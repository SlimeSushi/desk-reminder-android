package cn.deskreminder.mobile.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

data class WidgetStatus(val registered:Int,val visible:Int,val todos:Int,val notes:Int,val canPin:Boolean){
    fun report()="${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}\n应用注册 $registered/2\n系统可见 $visible/2\n桌面已添加：待办 $todos，随手记 $notes\n系统直接添加请求：${if(canPin)"支持"else"不支持，请从桌面添加"}"
}
object WidgetDiagnostics{
    fun read(context:Context):WidgetStatus{
        val components=listOf(ComponentName(context,TodoWidgetProvider::class.java),ComponentName(context,NoteWidgetProvider::class.java))
        val manager=AppWidgetManager.getInstance(context)
        val registered=components.count{component->runCatching{
            @Suppress("DEPRECATION") val info=context.packageManager.getReceiverInfo(component,PackageManager.GET_META_DATA)
            val enabled=context.packageManager.getComponentEnabledSetting(component)
            info.enabled&&info.applicationInfo.enabled&&enabled!=PackageManager.COMPONENT_ENABLED_STATE_DISABLED&&info.metaData.getInt("android.appwidget.provider")!=0
        }.getOrDefault(false)}
        val visible=runCatching{manager.installedProviders.map{it.provider}.toSet().intersect(components.toSet()).size}.getOrDefault(0)
        return WidgetStatus(registered,visible,manager.getAppWidgetIds(components[0]).size,manager.getAppWidgetIds(components[1]).size,manager.isRequestPinAppWidgetSupported)
    }
}
