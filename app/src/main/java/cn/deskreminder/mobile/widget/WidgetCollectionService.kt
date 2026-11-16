package cn.deskreminder.mobile.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import cn.deskreminder.mobile.*
import cn.deskreminder.mobile.data.*
import cn.deskreminder.mobile.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

class WidgetCollectionService:RemoteViewsService(){
    override fun onGetViewFactory(intent:Intent):RemoteViewsFactory=WidgetFactory(applicationContext,intent.getBooleanExtra("notes",false))
}
internal class WidgetFactory(private val context:Context,private val notes:Boolean):RemoteViewsService.RemoteViewsFactory{
    private var data=AppData();private var now=LocalDateTime.now();private var entries=emptyList<WidgetEntry>()
    override fun onCreate(){}
    override fun onDataSetChanged(){data=runBlocking(Dispatchers.IO){(context.applicationContext as DeskApplication).repository.current()};now=LocalDateTime.now();entries=WidgetRows.entries(data,notes,now)}
    override fun onDestroy(){entries=emptyList()}
    override fun getCount()=entries.size
    override fun getViewAt(position:Int):RemoteViews?=entries.getOrNull(position)?.let{WidgetRows.row(context,it,position,data.settings,notes,now)}
    override fun getLoadingView():RemoteViews?=null
    override fun getViewTypeCount()=1
    override fun getItemId(position:Int)=entries.getOrNull(position)?.key?.fold(1125899906842597L){hash,char->hash*31+char.code}?:position.toLong()
    override fun hasStableIds()=true
}
internal data class WidgetEntry(val key:String,val item:Occurrence?=null,val note:Note?=null)
internal object WidgetRows{
    fun entries(data:AppData,notes:Boolean,now:LocalDateTime)=if(notes)data.notes.sortedByDescending{it.pinned}.map{WidgetEntry(it.id,note=it)}else Schedule.displayPending(data,now).map{WidgetEntry(it.key,item=it)}
    fun tint(hex:String,opacity:Int):Int=(Color.parseColor(hex) and 0x00ffffff) or ((opacity*255/100) shl 24)
    fun showTime(due:LocalDateTime?,settings:MobileSettings,now:LocalDateTime):Boolean=due!=null&&when(settings.widgetTimeMode){"none"->false;"through_today","today"->due.toLocalDate()<=now.toLocalDate();else->true}
    fun row(context:Context,entry:WidgetEntry,index:Int,settings:MobileSettings,notes:Boolean,now:LocalDateTime):RemoteViews{
        val row=RemoteViews(context.packageName,R.layout.widget_row);val density=context.resources.displayMetrics.density
        row.setViewPadding(R.id.widget_row,0,(2*density).toInt(),0,(2*density).toInt())
        var color=tint(settings.widgetText,settings.widgetTextOpacity)
        val item=entry.item
        if(item!=null){
            val state=Schedule.dueState(item,settings,now);val bg=Color.parseColor(settings.widgetBackground)
            val light=settings.widgetBackgroundOpacity>25&&(.299*Color.red(bg)+.587*Color.green(bg)+.114*Color.blue(bg)>=158)
            if(state!=DueState.NORMAL)color=tint(when(state){DueState.SOON->if(light)"#A04D06"else"#FFD160";else->if(light)"#B52539"else"#FF7076"},settings.widgetTextOpacity)
        }
        row.setTextViewText(R.id.widget_row_title,(entry.note?.text?:item!!.todo.title).take(500))
        row.setInt(R.id.widget_row_title,"setMaxLines",if(notes)2 else 1);row.setTextColor(R.id.widget_row_title,color)
        row.setViewVisibility(R.id.widget_check,if(notes)View.GONE else View.VISIBLE)
        row.setInt(R.id.widget_check,"setColorFilter",color);row.setInt(R.id.widget_check,"setImageAlpha",settings.widgetTextOpacity*255/100)
        row.setTextViewText(R.id.widget_row_time,item?.due?.let{Dates.relative(it,now)}.orEmpty());row.setTextColor(R.id.widget_row_time,color)
        row.setViewVisibility(R.id.widget_row_time,if(item!=null&&showTime(item.due,settings,now))View.VISIBLE else View.GONE)
        val open=Intent().putExtra("kind",if(notes)"note"else"task").putExtra("id",entry.note?.id?:item!!.todo.id).putExtra("week",item?.week)
        row.setOnClickFillInIntent(R.id.widget_content,open)
        if(item!=null)row.setOnClickFillInIntent(R.id.widget_check,Intent().putExtra("kind","complete").putExtra("task",item.todo.id).putExtra("week",item.week).putExtra("due",item.signature))
        return row
    }
}
