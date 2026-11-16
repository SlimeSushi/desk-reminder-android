package cn.deskreminder.mobile

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import cn.deskreminder.mobile.ui.DeskApp
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity(){
    private val incoming=mutableStateOf<Intent?>(null)
    private val notificationPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){
        val app=application as DeskApplication;app.repository.scope.launch{runCatching{app.reminders.processDue()}}
    }
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge();incoming.value=intent;setContent{DeskApp(incoming.value,{incoming.value=null},{explicit->requestNotifications(explicit)})}}
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);incoming.value=intent}
    override fun onResume(){super.onResume();val app=application as DeskApplication;app.cloud.setForeground(true);app.repository.scope.launch{runCatching{app.reminders.processDue()}}}
    override fun onPause(){(application as DeskApplication).cloud.setForeground(false);super.onPause()}
    private fun requestNotifications(explicit:Boolean){
        val app=application as DeskApplication;if(app.reminders.notificationsAllowed())return
        if(Build.VERSION.SDK_INT>=33){
            val prefs=getSharedPreferences("permission-ui",MODE_PRIVATE);val asked=prefs.getBoolean("notifications-asked",false)
            if(asked&&!explicit)return
            if(asked&&!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)){if(explicit)startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName));return}
            prefs.edit().putBoolean("notifications-asked",true).apply();notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
    }
}
