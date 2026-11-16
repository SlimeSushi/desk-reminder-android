package cn.deskreminder.mobile.data

import cn.deskreminder.mobile.data.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit

object Dates {
    val minuteFormat:DateTimeFormatter=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)
    val secondFormat:DateTimeFormatter=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)
    fun date(text:String?):LocalDate?=runCatching{LocalDate.parse(text)}.getOrNull()
    fun time(text:String?):LocalTime?=runCatching{LocalTime.parse(text)}.getOrNull()
    fun moment(text:String?):LocalDateTime?=text?.let{runCatching{LocalDateTime.parse(it,minuteFormat)}.getOrElse{runCatching{LocalDateTime.parse(text,secondFormat)}.getOrNull()}}
    fun minute(value:LocalDateTime):String=value.format(minuteFormat)
    fun stamp(value:LocalDateTime=LocalDateTime.now()):String=value.format(secondFormat)
    fun monday(date:LocalDate):LocalDate=date.minusDays((date.dayOfWeek.value-1).toLong())
    fun key(date:LocalDate):String=monday(date).toString()
    fun display(value:LocalDateTime?,reference:LocalDate=LocalDate.now()):String=if(value==null)"时间未记录" else value.format(DateTimeFormatter.ofPattern(if(value.year==reference.year)"M月d日 HH:mm" else "uuuu年M月d日 HH:mm"))
    fun relative(value:LocalDateTime,now:LocalDateTime=LocalDateTime.now()):String=when(value.toLocalDate()) {
        now.toLocalDate()->"今天 ${value.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}"
        now.toLocalDate().plusDays(1)->"明天 ${value.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}"
        else->display(value,now.toLocalDate())
    }
    fun plus(value:LocalDateTime,minutes:Long):LocalDateTime=runCatching{value.plusMinutes(minutes)}.getOrElse{if(minutes>0)LocalDateTime.of(9999,12,31,23,59)else LocalDateTime.of(1,1,1,0,0)}
}
data class Occurrence(val todo:Todo,val week:String?=null,val due:LocalDateTime?=null,val original:LocalDateTime?=due,val completed:Boolean=false,val actual:LocalDateTime?=null,val ended:Boolean=false,val stop:StopRecord?=null) {
    val key:String get()=todo.id+":"+(week?:"once")
    val signature:String get()=due?.let(Dates::minute).orEmpty()
    val stopped:Boolean get()=!completed&&!ended&&(stop!=null||todo.stopped)
}
data class TimeStep(val from:LocalDateTime?,val to:LocalDateTime?,val changed:LocalDateTime?,val all:Boolean=false,val edit:Boolean=false,val reason:String="")

fun overdueLabel(due:LocalDateTime,now:LocalDateTime=LocalDateTime.now()):String=TaskTime.status(due,now).orEmpty()
object Schedule {
    fun isDone(task:Todo,week:String?):Boolean=if(task.type!="weekly")task.done else week!=null&&(task.completedWeeks.contains(week)||task.doneWeek==week)
    fun completedAt(task:Todo,week:String?):LocalDateTime?=if(task.type!="weekly")Dates.moment(task.completedAt) else task.weeklyCompletions.firstOrNull{it.weekKey==week}?.let{Dates.moment(it.completedAt)}
    fun original(task:Todo,week:String):LocalDateTime? {
        val monday=Dates.date(week)?:return null
        if(Dates.monday(monday)!=monday||task.weekday !in 1..7)return null
        val date=monday.plusDays((task.weekday-1).toLong())
        if(Dates.date(task.weeklyStartDate)?.let{date<it}==true||Dates.date(task.weeklyEndDate)?.let{date>it}==true)return null
        return date.atTime(Dates.time(task.weeklyTime)?:return null)
    }
    fun applies(change:WeeklyPostponement,week:String):Boolean=if(change.allFuture)week>=change.fromWeek&&!change.excludedWeeks.contains(week)else week==change.fromWeek
    fun occurrence(task:Todo,week:String?=null):Occurrence? {
        if(task.type!="weekly")return Occurrence(task,null,Dates.moment(task.dueAt),Dates.moment(task.originalDueAt)?:Dates.moment(task.dueAt),task.done,Dates.moment(task.completedAt))
        if(week==null)return null
        val snapshot=task.weeklySnapshots[week]
        val original=Dates.moment(snapshot?.original)?:original(task,week)?:return null
        var effective=Dates.moment(snapshot?.due)?:original
        task.weeklyPostponements.drop(snapshot?.changeCount?:0).filter{applies(it,week)}.forEach{effective=Dates.plus(effective,it.offsetMinutes)}
        return Occurrence(task,week,effective,original,isDone(task,week),completedAt(task,week))
    }
    private fun bounds(task:Todo):Pair<Long,Long> {
        var positive=0L;var negative=0L
        task.weeklyPostponements.forEach{if(it.offsetMinutes>0)positive=Math.addExact(positive,it.offsetMinutes)else negative=Math.addExact(negative,it.offsetMinutes)}
        return positive to negative
    }
    fun inRange(task:Todo,start:LocalDateTime,end:LocalDateTime):List<Occurrence> {
        if(end<start)return emptyList()
        if(task.type!="weekly")return listOfNotNull(occurrence(task)).filter{it.due!=null&&it.due>=start&&it.due<=end}.map{it.copy(stop=stopWindowFor(task,it.due))}
        val (positive,negative)=bounds(task)
        var first=Dates.monday(Dates.plus(start,-positive).toLocalDate())
        var last=Dates.monday(Dates.plus(end,-negative).toLocalDate())
        Dates.date(task.weeklyStartDate)?.let{if(Dates.monday(it)>first)first=Dates.monday(it)}
        Dates.date(task.weeklyEndDate)?.let{if(Dates.monday(it)<last)last=Dates.monday(it)}
        require(ChronoUnit.WEEKS.between(first,last)<20000){"周期跨度过大，请缩短查询范围"}
        val results=mutableListOf<Occurrence>();var week=first
        while(week<=last){occurrence(task,week.toString())?.let{value->if(value.due!!>=start&&value.due<=end)results.add(value.copy(stop=stopWindowFor(task,value.due)))};week=week.plusWeeks(1)}
        return results.sortedWith(compareBy<Occurrence>{it.due}.thenBy{it.week})
    }
    fun stopWindowFor(task:Todo,due:LocalDateTime?):StopRecord? {
        if(due==null)return null
        task.stopRecords.asReversed().forEach{record->
            val from=Dates.moment(record.stoppedAt)?:return@forEach
            val to=Dates.moment(record.resumedAt)
            if(!due.isBefore(from)&&(to==null||due.isBefore(to)))return record
            if(to==null&&task.type=="once")return record
        }
        return null
    }
    fun stopped(data:AppData,now:LocalDateTime=LocalDateTime.now()):List<Occurrence> = data.todos
        .filter{it.stopped&&!it.archived}
        .map{Occurrence(it,due=pending(it.copy(stopped=false),now)?.due)}
        .sortedByDescending{it.todo.stopRecords.lastOrNull()?.let{record->Dates.moment(record.stoppedAt)}?:LocalDateTime.MIN}
    fun resumeWarning(task:Todo,now:LocalDateTime=LocalDateTime.now()):String? {
        if(task.type=="once"){
            val due=Dates.moment(task.dueAt)
            if(due!=null&&!due.isAfter(now))return "原计划 ${Dates.display(due)} 已过。恢复后会立即按当前提醒设置提醒。"
        }
        if(task.type=="weekly"){
            val next=pending(task.copy(stopped=false),now)
            if(next==null)return "本周期已没有剩余的可执行周，恢复后不会出现在待完成。请先在详情里编辑周期结束日期。"
            if(!next.due!!.isAfter(now))return "下一次计划 ${Dates.display(next.due)} 已过。恢复后会立即按当前提醒设置提醒。"
        }
        return null
    }
    fun pending(task:Todo,now:LocalDateTime=LocalDateTime.now(),includeCompletedCurrent:Boolean=false):Occurrence? {
        if(task.stopped||task.archived)return null
        if(task.type!="weekly")return if(task.done)null else occurrence(task)
        val monday=Dates.monday(now.toLocalDate());val (positive,negative)=bounds(task)
        val end=Dates.date(task.weeklyEndDate);val ended=end!=null&&now.toLocalDate()>end
        var lower=Dates.plus(monday.atStartOfDay(),-positive).toLocalDate()
        if(ended&&positive>0){val shifted=Dates.plus(end!!.plusDays(1).atStartOfDay(),-positive).toLocalDate();if(shifted<lower)lower=shifted}
        Dates.date(task.weeklyStartDate)?.let{if(it>lower)lower=it}
        var week=Dates.monday(lower);var best:Occurrence?=null;var count=0
        while(week<=monday&&count++<20000){
            val value=occurrence(task,week.toString())
            if(value!=null&&!value.completed){
                val shiftedAfterEnd=ended&&value.due!!.toLocalDate()>end&&value.due!=value.original
                if(week==monday||value.due!!>=monday.atStartOfDay()||shiftedAfterEnd){if(best==null||value.due!!<best.due)best=value}
            };week=week.plusWeeks(1)
        }
        if(includeCompletedCurrent&&best==null)occurrence(task,monday.toString())?.takeIf{it.completed}?.let{return it}
        week=monday.plusWeeks(1)
        Dates.date(task.weeklyStartDate)?.let{if(Dates.monday(it)>week)week=Dates.monday(it)}
        count=0
        while(count++<20000){
            if(end!=null&&week>end)break
            val value=occurrence(task,week.toString())
            if(value!=null&&!value.completed&&(best==null||value.due!!<best.due))best=value
            if(best!=null&&Dates.plus(week.plusDays((task.weekday-1).toLong()).atTime(Dates.time(task.weeklyTime)?:LocalTime.NOON),negative)>best.due)break
            week=week.plusWeeks(1)
        }
        return best
    }
    fun pending(data:AppData,now:LocalDateTime=LocalDateTime.now()):List<Occurrence> = data.todos.mapNotNull{pending(it,now)}.sortedWith(compareBy<Occurrence>{it.due==null}.thenBy{it.due})
    private fun hasPendingShiftAfterEnd(task:Todo):Boolean{
        val end=Dates.date(task.weeklyEndDate)?:return false;val positive=bounds(task).first
        if(positive<=0)return false
        var week=Dates.monday(Dates.plus(end.plusDays(1).atStartOfDay(),-positive).toLocalDate());val last=Dates.monday(end);var count=0
        while(week<=last&&count++<20000){val item=occurrence(task,week.toString());if(item!=null&&!item.completed&&item.due!!.toLocalDate()>end)return true;week=week.plusWeeks(1)}
        return false
    }
    fun endedForDisplay(task:Todo,now:LocalDateTime=LocalDateTime.now()):Boolean=task.type=="weekly"&&Dates.date(task.weeklyEndDate)?.let{now.toLocalDate()>it}==true&&!hasPendingShiftAfterEnd(task)
    fun displayPending(data:AppData,now:LocalDateTime=LocalDateTime.now()):List<Occurrence> = data.todos.mapNotNull{task->
        if(endedForDisplay(task,now))null else pending(task,now,includeCompletedCurrent=true)?.takeUnless{it.completed}
    }.sortedWith(compareBy<Occurrence>{it.due==null}.thenBy{it.due})
    fun history(data:AppData,now:LocalDateTime=LocalDateTime.now(),applyRange:Boolean=true):List<Occurrence> {
        val result=mutableListOf<Occurrence>()
        data.todos.forEach{task->
            if(task.type=="weekly"){
                (task.completedWeeks+listOfNotNull(task.doneWeek)).distinct().forEach{week->occurrence(task,week)?.let(result::add)}
                if(!task.stopped&&!task.archived&&endedForDisplay(task,now)){
                    val end=Dates.date(task.weeklyEndDate)!!;var last=Dates.monday(end).plusDays((task.weekday-1).toLong());if(last>end)last=last.minusWeeks(1)
                    if(!isDone(task,Dates.key(last)))result.add(Occurrence(task,due=end.atTime(23,59),ended=true))
                }
            }else if(task.done&&!task.stopped)occurrence(task)?.let(result::add)
        }
        val first=if(applyRange&&data.settings.historyDays>0)now.toLocalDate().minusDays((data.settings.historyDays-1).toLong())else null
        fun date(item:Occurrence)=item.actual?:if(item.stopped)Dates.moment(item.todo.stopRecords.lastOrNull()?.stoppedAt)else if(item.ended)item.due else null
        return result.filter{first==null||date(it)==null||date(it)!!.toLocalDate()>=first}.sortedByDescending{date(it)?:LocalDateTime.MIN}
    }
    fun complete(task:Todo,week:String?,value:Boolean,whenAt:LocalDateTime=LocalDateTime.now()) {
        require(!task.stopped||!value){"请先恢复中止的任务"}
        if(task.type!="weekly"){task.done=value;task.completedAt=if(value)Dates.stamp(whenAt)else null;return}
        require(week!=null&&original(task,week)!=null){"该周事项已变化，请重新查看"}
        task.weeklyCompletions.removeAll{it.weekKey==week};task.completedWeeks.removeAll{it==week}
        if(value){task.completedWeeks.add(week);task.doneWeek=week;task.weeklyCompletions.add(WeeklyCompletion(week,Dates.stamp(whenAt)))}else if(task.doneWeek==week)task.doneWeek=null
    }
    fun changeActual(task:Todo,week:String?,whenAt:LocalDateTime,now:LocalDateTime=LocalDateTime.now()) {
        require(isDone(task,week)){"只能修改已完成事项的实际完成时间"}
        if(task.type!="weekly")task.completedAt=Dates.stamp(whenAt)else{task.weeklyCompletions.removeAll{it.weekKey==week};task.weeklyCompletions.add(WeeklyCompletion(week!!,Dates.stamp(whenAt)))}
    }
    fun postpone(task:Todo,week:String?,newDue:LocalDateTime,all:Boolean=false,whenAt:LocalDateTime=LocalDateTime.now(),reason:String="") {
        val current=occurrence(task,week)?:error("事项计划已变化")
        require(!current.completed&&!task.stopped){"已完成或中止事项不能延期"};require(current.due!=null&&newDue>current.due){"延期时间需晚于当前计划时间"}
        if(task.type=="once"){task.postponements.add(Postponement(Dates.minute(current.due),Dates.minute(newDue),Dates.stamp(whenAt),reason=reason.trim()));task.dueAt=Dates.minute(newDue)}
        else if(task.type=="weekly"){
            val excluded=(task.completedWeeks+listOfNotNull(task.doneWeek)).distinct().toMutableList()
            task.weeklyPostponements.add(WeeklyPostponement(week!!,ChronoUnit.MINUTES.between(current.due,newDue),all,java.util.UUID.randomUUID().toString(),Dates.stamp(whenAt),if(all)excluded else mutableListOf(),reason.trim()))
        }else error("无期限事项请先补设时间")
    }
    fun stop(task:Todo,reason:String,now:LocalDateTime=LocalDateTime.now()){require(!task.stopped){"事项已中止"};task.stopped=true;task.stopRecords.add(StopRecord(Dates.stamp(now),reason.trim()))}
    fun freezeCompletedPlans(task:Todo){(task.completedWeeks+listOfNotNull(task.doneWeek)).distinct().forEach{week->if(!task.weeklySnapshots.containsKey(week))occurrence(task,week)?.let{task.weeklySnapshots[week]=WeeklySnapshot(Dates.minute(it.original!!),Dates.minute(it.due!!),task.weeklyPostponements.size)}}}
    fun resume(task:Todo,now:LocalDateTime=LocalDateTime.now()){task.stopped=false;task.stopRecords.lastOrNull{it.resumedAt.isNullOrEmpty()}?.resumedAt=Dates.stamp(now)}
    fun timeline(item:Occurrence):List<TimeStep> {
        if(item.todo.type!="weekly")return item.todo.postponements.map{TimeStep(Dates.moment(it.fromDueAt),Dates.moment(it.toDueAt),Dates.moment(it.changedAt),edit=it.kind=="edit",reason=it.reason)}
        var due=item.original?:return emptyList();val result=mutableListOf<TimeStep>()
        item.todo.weeklyPostponements.filter{applies(it,item.week!!)}.groupBy{it.operationId.ifBlank{it.changedAt+it.fromWeek}}.values.forEach{group->val old=due;group.forEach{due=Dates.plus(due,it.offsetMinutes)};result.add(TimeStep(old,due,Dates.moment(group.first().changedAt),group.any{it.allFuture},reason=group.map{it.reason}.filter{it.isNotBlank()}.distinct().joinToString("；")))}
        return result
    }
    fun rule(item:Occurrence,settings:MobileSettings):ReminderRule=(item.todo.reminder?:settings.reminder).copy().apply{enabled=enabled&&settings.reminder.enabled&&!item.stopped&&!item.completed}
    /** Appearance owns status color; muting notifications and legacy per-item Highlight do not suppress it. */
    fun dueState(item:Occurrence,settings:MobileSettings,now:LocalDateTime=LocalDateTime.now()):DueState{
        val due=item.due?:return DueState.NORMAL
        if(!settings.reminder.highlight||item.todo.type=="open"||item.completed||item.stopped||item.ended)return DueState.NORMAL
        val advance=(item.todo.reminder?:settings.reminder).advanceMinutes.toLong()
        return when{now>=due->DueState.OVERDUE;now>=due.minusMinutes(advance)->DueState.SOON;else->DueState.NORMAL}
    }
}
enum class DueState{NORMAL,SOON,OVERDUE}
