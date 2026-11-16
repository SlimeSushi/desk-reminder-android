package cn.deskreminder.mobile.widget

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import cn.deskreminder.mobile.*
import cn.deskreminder.mobile.data.*
import cn.deskreminder.mobile.data.model.*
import cn.deskreminder.mobile.reminder.*
import cn.deskreminder.mobile.ui.StaticGlassFrame
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

object Widgets {
    private fun activity(context:Context,route:String,id:String="",week:String?=null):PendingIntent {
        val quick=route=="newTodo"||route=="newNote"
        val intent=Intent(context,if(quick)WidgetQuickAddActivity::class.java else MainActivity::class.java).setData(Uri.parse("deskreminder://widget/$route/${Uri.encode(id)}/${Uri.encode(week.orEmpty())}"))
        if(quick)intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("notes",route=="newNote")
        when(route){"newTodo"->intent.putExtra("new","todo");"newNote"->intent.putExtra("new","note");"settings"->intent.putExtra("tab",3).putExtra("widget",true);"todos"->intent.putExtra("tab",0);"notes"->intent.putExtra("tab",2);"note"->intent.putExtra("note",id);"task"->intent.putExtra("task",id).putExtra("week",week)}
        return PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    @Synchronized fun update(context:Context,incoming:AppData){
        val data=(context.applicationContext as? DeskApplication)?.repository?.data?.value?:incoming
        val manager=AppWidgetManager.getInstance(context)
        listOf(TodoWidgetProvider::class.java to false,NoteWidgetProvider::class.java to true).forEach{(provider,notes)->manager.getAppWidgetIds(ComponentName(context,provider)).forEach{id->render(context,manager,id,notes,data)}}
    }
    private fun widgetBackground(settings:MobileSettings,width:Int,height:Int,density:Float):android.graphics.Bitmap {
        if(settings.appStyle!="classic")return StaticGlassFrame.bitmap(width,height,FloatArray(4){24*density},Color.parseColor(settings.widgetBackground),density)
        return android.graphics.Bitmap.createBitmap(width.coerceAtLeast(1),height.coerceAtLeast(1),android.graphics.Bitmap.Config.ARGB_8888).also{image->
            val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=Color.parseColor(settings.widgetBackground)}
            android.graphics.Canvas(image).drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),20*density,20*density,paint)
        }
    }
    private fun render(context:Context,manager:AppWidgetManager,id:Int,notes:Boolean,data:AppData){
        manager.updateAppWidget(id,root(context,notes,data,false,id))
        @Suppress("DEPRECATION") manager.notifyAppWidgetViewDataChanged(id,R.id.widget_list)
    }
    fun preview(context:Context,notes:Boolean,data:AppData,height:Int=220):RemoteViews=root(context,notes,data,true,0,height)
    private fun root(context:Context,notes:Boolean,data:AppData,preview:Boolean,id:Int,height:Int=220):RemoteViews{
        val settings=data.settings;val text=WidgetRows.tint(settings.widgetText,settings.widgetTextOpacity)
        val view=RemoteViews(context.packageName,if(preview)R.layout.widget_preview else R.layout.widget_main)
        val density=context.resources.displayMetrics.density
        val options=if(preview)null else AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
        val landscape=context.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val widthDp=options?.getInt(if(landscape)AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,350)?.coerceAtLeast(80)?:350
        val heightDp=if(preview)height else options?.getInt(if(landscape)AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,220)?.coerceAtLeast(80)?:220
        view.setImageViewBitmap(R.id.widget_background,widgetBackground(settings,(widthDp*density).toInt(),(heightDp*density).toInt(),density))
        view.setInt(R.id.widget_background,"setImageAlpha",settings.widgetBackgroundOpacity*255/100)
        view.setTextViewText(R.id.widget_title,if(notes)"随手记"else"${Schedule.displayPending(data).size}项待办");view.setTextColor(R.id.widget_title,text)
        val showTitle=if(notes)settings.widgetNoteTitle else settings.widgetTodoTitle
        view.setViewVisibility(R.id.widget_title,if(showTitle)View.VISIBLE else View.GONE)
        val reservedEnd=if(showTitle)0 else (48*context.resources.displayMetrics.density).toInt()
        view.setViewPadding(if(preview)R.id.widget_rows else R.id.widget_list,0,0,reservedEnd,0)
        view.setInt(R.id.widget_add,"setColorFilter",Color.parseColor(settings.widgetText))
        view.setInt(R.id.widget_add,"setImageAlpha",settings.widgetTextOpacity*255/100)
        view.setOnClickPendingIntent(R.id.widget_add,activity(context,if(notes)"newNote"else"newTodo"))
        if(preview){
            val now=java.time.LocalDateTime.now();val entries=WidgetRows.entries(data,notes,now)
            val scale=context.resources.configuration.fontScale.coerceAtLeast(1f)
            val count=((height-16-(if(showTitle)48 else 0))/(48*scale)).toInt().coerceAtLeast(1)
            entries.take(count).forEachIndexed{index,entry->view.addView(R.id.widget_rows,WidgetRows.row(context,entry,index,settings,notes,now))}
        }else{
            val adapter=Intent(context,WidgetCollectionService::class.java).putExtra("notes",notes).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).setData(Uri.parse("deskreminder://widget-list/$id/$notes"))
            @Suppress("DEPRECATION") view.setRemoteAdapter(R.id.widget_list,adapter)
            val click=Intent(context,WidgetActionActivity::class.java).setData(Uri.parse("deskreminder://widget-actions/$id"))
            val template=PendingIntent.getActivity(context,id,click,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            view.setPendingIntentTemplate(R.id.widget_list,template)
            view.setEmptyView(R.id.widget_list,R.id.widget_empty)
            view.setTextViewText(R.id.widget_empty,if(notes)"点击记录随手记"else"点击新增待办");view.setTextColor(R.id.widget_empty,text)
            view.setOnClickPendingIntent(R.id.widget_empty,activity(context,if(notes)"newNote"else"newTodo"))
        }
        return view
    }
}
open class BaseWidgetProvider:AppWidgetProvider(){
    private fun refresh(context:Context){val pending=goAsync();val app=context.applicationContext as DeskApplication;app.repository.scope.launch{try{withTimeout(8000){Widgets.update(context,app.repository.current());app.reminders.reschedule()}}finally{pending.finish()}}}
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){refresh(context)}
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:Bundle){refresh(context)}
}
class TodoWidgetProvider:BaseWidgetProvider()
class NoteWidgetProvider:BaseWidgetProvider()
