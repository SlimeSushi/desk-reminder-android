package cn.deskreminder.mobile.reminder

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cn.deskreminder.mobile.*
import cn.deskreminder.mobile.data.*
import cn.deskreminder.mobile.data.model.*
import cn.deskreminder.mobile.widget.Widgets
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

class ReminderScheduler(private val context:Context,private val repo:DataRepository) {
    private val alarms=context.getSystemService(AlarmManager::class.java)
    private val notifications=context.getSystemService(NotificationManager::class.java)
    private val processing=Mutex()
    fun notificationsAllowed():Boolean=NotificationManagerCompat.from(context).areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
    fun exactAllowed():Boolean=Build.VERSION.SDK_INT<31||alarms.canScheduleExactAlarms()
    fun ensureChannels(){
        for(sound in listOf(false,true))for(vibrate in listOf(false,true)){
            val id=channel(sound,vibrate)
            val name=if(sound&&vibrate)"提醒：声音与振动" else if(sound)"提醒：声音" else if(vibrate)"提醒：振动" else "提醒：静音"
            val value=NotificationChannel(id,name,NotificationManager.IMPORTANCE_HIGH)
            value.description="待办计划与重复提醒";value.enableVibration(vibrate)
            if(!sound)value.setSound(null,null)
            notifications.createNotificationChannel(value)
        }
    }
    private fun channel(sound:Boolean,vibrate:Boolean)="reminders-${if(sound)1 else 0}-${if(vibrate)1 else 0}"
    private fun epoch(value:LocalDateTime):Long=value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun dueAt(item:Occurrence,data:AppData,now:Long):Long? {
        val due=item.due?:return null;val rule=Schedule.rule(item,data.settings)
        if(!rule.enabled||!rule.popup)return null
        val first=epoch(due.minusMinutes(rule.advanceMinutes.toLong()))
        if(first>now)return first
        if(!notificationsAllowed())return null
        val state=data.reminderState[item.key]
        if(state==null||state.dueSignature!=item.signature)return now
        if(state.snoozedUntil>0)return state.snoozedUntil
        return if(rule.repeatMinutes>0)state.lastAt+rule.repeatMinutes*60_000L else null
    }
    suspend fun reschedule(){
        val data=repo.current();val now=System.currentTimeMillis()
        val current=Schedule.pending(data)
        val valid=current.map{it.key}.toSet()
        notifications.activeNotifications.filter{it.tag!=null&&it.tag !in setOf("summary","test")&&it.tag !in valid}.forEach{notifications.cancel(it.tag,it.id)}
        if(!data.settings.reminder.enabled)notifications.cancelAll()
        val midnight=epoch(LocalDate.now().plusDays(1).atTime(0,5))
        // Status colors must also refresh at the advance/due boundary when notifications are muted.
        val colorBoundaries=if(data.settings.reminder.highlight)Schedule.displayPending(data).flatMap{item->item.due?.let{due->listOf(epoch(due.minusMinutes((item.todo.reminder?:data.settings.reminder).advanceMinutes.toLong())),epoch(due))}.orEmpty()}.filter{it>now}else emptyList()
        val next=(current.mapNotNull{dueAt(it,data,now)}+colorBoundaries).minOrNull()?.coerceAtMost(midnight)?:midnight
        val intent=PendingIntent.getBroadcast(context,73100,Intent(context,ReminderReceiver::class.java).setAction(ACTION_ALARM),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.cancel(intent)
        val at=next.coerceAtLeast(now+1000)
        try{if(exactAllowed())alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent)else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent)}
        catch(_:SecurityException){alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent)}
    }
    suspend fun processDue()=processing.withLock {
        ensureChannels();val now=System.currentTimeMillis();val ready=mutableListOf<Pair<Occurrence,ReminderRule>>()
        val data=repo.current()
        val eligible=Schedule.pending(data).filter{dueAt(it,data,now)?.let{at->at<=now}==true}
        if(eligible.isNotEmpty()&&notificationsAllowed()){
            repo.commit{next->
                Schedule.pending(next).forEach{item->
                    if(dueAt(item,next,now)?.let{it<=now}==true){next.reminderState[item.key]=ReminderStamp(item.signature,now,0);ready.add(item to Schedule.rule(item,next.settings))}
                }
            }
            ready.take(12).forEach{(item,rule)->notify(item,rule)}
            if(ready.size>1){
                val style=NotificationCompat.InboxStyle().setBigContentTitle("${ready.size}项待办提醒")
                ready.take(8).forEach{style.addLine(it.first.todo.title)}
                val open=PendingIntent.getActivity(context,73001,Intent(context,MainActivity::class.java).putExtra("tab",0),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val summary=NotificationCompat.Builder(context,channel(false,false)).setSmallIcon(R.drawable.ic_reminder).setContentTitle("${ready.size}项待办需要关注").setContentText("点击查看待完成事项").setStyle(style).setGroup("desk-reminders").setGroupSummary(true).setContentIntent(open).setAutoCancel(true).build()
                runCatching{notifications.notify("summary",73001,summary)}
            }
        }
        reschedule()
        repo.maintainBackups();Widgets.update(context,repo.current())
    }
    private fun action(item:Occurrence,action:String):PendingIntent {
        val intent=Intent(context,ReminderReceiver::class.java).setAction(action).setData(Uri.parse("deskreminder://$action/${Uri.encode(item.key)}/${Uri.encode(item.signature)}"))
            .putExtra("task",item.todo.id).putExtra("week",item.week).putExtra("due",item.signature)
        return PendingIntent.getBroadcast(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun notify(item:Occurrence,rule:ReminderRule){
        val open=Intent(context,MainActivity::class.java).setData(Uri.parse("deskreminder://detail/${Uri.encode(item.key)}")).putExtra("task",item.todo.id).putExtra("week",item.week)
        val pending=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text="计划完成 ${Dates.display(item.due)}"+(if(item.todo.content.isBlank())"" else "\n${item.todo.content.take(500)}")
        val value=NotificationCompat.Builder(context,channel(rule.sound,rule.vibrate)).setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(item.todo.title).setContentText("计划完成 ${Dates.display(item.due)}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setGroup("desk-reminders").setContentIntent(pending).setAutoCancel(true)
            .addAction(0,"完成",action(item,ACTION_COMPLETE)).addAction(0,"稍后10分钟",action(item,ACTION_SNOOZE)).build()
        runCatching{notifications.notify(item.key,1,value)}
    }
    suspend fun sendTest(){
        require(notificationsAllowed()){"请先开启通知权限"};ensureChannels();val rule=repo.current().settings.reminder
        val open=PendingIntent.getActivity(context,73002,Intent(context,MainActivity::class.java).putExtra("tab",3),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notifications.notify("test",73002,NotificationCompat.Builder(context,channel(rule.sound,rule.vibrate)).setSmallIcon(R.drawable.ic_reminder).setContentTitle("提醒测试").setContentText("这是一条本地测试提醒").setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build())
    }
    suspend fun handle(intent:Intent){
        val id=intent.getStringExtra("task")?:return
        val week=intent.getStringExtra("week");val expected=intent.getStringExtra("due").orEmpty()
        var changed=false
        repo.commit{next->
            val task=next.todos.firstOrNull{it.id==id}?:return@commit
            val item=Schedule.occurrence(task,week)?:return@commit
            if(item.completed||item.stopped||item.week!=week||item.signature!=expected)return@commit
            when(intent.action){
                ACTION_COMPLETE->{Schedule.complete(task,week,true);next.reminderState.remove(item.key);changed=true}
                ACTION_SNOOZE->{next.reminderState[item.key]=ReminderStamp(item.signature,System.currentTimeMillis(),System.currentTimeMillis()+600_000);changed=true}
            }
        }
        if(changed)notifications.cancel(id+":"+(week?:"once"),1)
        reschedule()
    }
    companion object {
        const val ACTION_ALARM="cn.deskreminder.ALARM"
        const val ACTION_COMPLETE="cn.deskreminder.COMPLETE"
        const val ACTION_SNOOZE="cn.deskreminder.SNOOZE"
    }
}
class ReminderReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val pending=goAsync();val app=context.applicationContext as DeskApplication
        app.repository.scope.launch{try{withTimeout(8000){if(intent.action==ReminderScheduler.ACTION_ALARM)app.reminders.processDue()else app.reminders.handle(intent)}}catch(_:Exception){}finally{pending.finish()}}
    }
}
class RecoveryReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))return
        val pending=goAsync();val app=context.applicationContext as DeskApplication
        app.repository.scope.launch{try{withTimeout(8000){app.reminders.processDue()}}catch(_:Exception){}finally{pending.finish()}}
    }
}
