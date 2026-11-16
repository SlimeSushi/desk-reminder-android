package cn.deskreminder.mobile.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import cn.deskreminder.mobile.*
import cn.deskreminder.mobile.reminder.ReminderScheduler
import cn.deskreminder.mobile.ui.*
import kotlinx.coroutines.*

/** Explicit mutable collection template dispatches only these validated local actions. */
class WidgetActionActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        val kind=intent.getStringExtra("kind")
        if(kind=="complete"){
            lifecycleScope.launch{
                try{withContext(Dispatchers.IO){(application as DeskApplication).reminders.handle(Intent(intent).setAction(ReminderScheduler.ACTION_COMPLETE))};finish()}
                catch(_:Exception){val settings=(application as DeskApplication).repository.data.value?.settings;setContent{DeskTheme(settings?.theme?:"light",settings?.appStyle?:"fruit"){DesktopOperationFeedback("操作未完成，请重试",{finish()},error=true)}}}
            }
        }else{
            val id=intent.getStringExtra("id")
            if(id!=null&&(kind=="task"||kind=="note"))startActivity(Intent(this,MainActivity::class.java).putExtra(if(kind=="task")"task"else"note",id).putExtra("week",intent.getStringExtra("week")))
            finish()
        }
    }
}
