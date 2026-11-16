package cn.deskreminder.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.*
import cn.deskreminder.mobile.DeskApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class PinResult{REQUESTED,UNSUPPORTED,FAILED}
object WidgetPinning {
    val confirmations=MutableStateFlow(0L)
    fun request(context:Context,notes:Boolean):PinResult=try{
        val manager=AppWidgetManager.getInstance(context)
        if(!manager.isRequestPinAppWidgetSupported)PinResult.UNSUPPORTED else{
            val provider=ComponentName(context,if(notes)NoteWidgetProvider::class.java else TodoWidgetProvider::class.java)
            val callback=PendingIntent.getBroadcast(context,if(notes)84002 else 84001,Intent(context,WidgetPinReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            // No custom preview is necessary; use the launcher's supported preview path.
            if(manager.requestPinAppWidget(provider,null,callback))PinResult.REQUESTED else PinResult.FAILED
        }
    }catch(_:RuntimeException){PinResult.FAILED}
}
class WidgetPinReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        WidgetPinning.confirmations.value=System.nanoTime()
        val pending=goAsync();val app=context.applicationContext as DeskApplication
        app.repository.scope.launch{try{withTimeout(8000){Widgets.update(context,app.repository.current())}}finally{pending.finish()}}
    }
}
