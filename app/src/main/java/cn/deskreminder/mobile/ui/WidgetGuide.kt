package cn.deskreminder.mobile.ui

import android.content.*
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.deskreminder.mobile.widget.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable fun WidgetGuide(onBack:()->Unit){
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current
    var refresh by remember{mutableIntStateOf(0)};var error by remember{mutableStateOf("")}
    val feedback=remember{SnackbarHostState()};val scope=rememberCoroutineScope()
    val status by produceState<WidgetStatus?>(null,refresh){value=withContext(Dispatchers.IO){WidgetDiagnostics.read(context)}}
    DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)refresh++};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer)}}
    fun open(intent:Intent){runCatching{context.startActivity(intent)}.onFailure{error="系统未提供此入口，请手动打开系统设置。"}}
    Page("组件添加指南",onBack,snackbarHost={OperationMessageHost(feedback)}){padding->Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Section("小米15 · 澎湃OS · 系统桌面"){
            Text("1  长按桌面空白处，或双指捏合桌面。\n\n2  选择“添加小部件”，点击搜索入口。\n\n3  进入“安卓小部件”，找到“桌面清单”。\n\n4  选择待办或随手记，长按拖到桌面空白处。\n\n5  长按已添加的组件，拖动边框调整宽度和高度。",lineHeight=24.sp)
            Primary("去手机桌面",{open(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))},Modifier.fillMaxWidth())
        }
        Section("添加前后检查"){
            Text("添加列表不需要通知、闹钟、悬浮窗或存储权限。系统没有弹出“添加”确认时，可直接使用上面的安卓小部件入口。",lineHeight=24.sp)
            Text("桌面需留足空白位置；关闭桌面布局锁定后再拖放。确保应用和桌面在同一用户空间，安装更新后至少打开应用一次。",lineHeight=24.sp)
            Text("通知权限用于显示提醒；闹钟与提醒权限用于准时调度。若后台刷新被系统限制，可在应用详情中允许自启动，并检查省电策略。这些设置影响更新及时性，不决定组件是否出现在列表。",lineHeight=24.sp)
            Secondary("打开应用系统设置",{open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))},Modifier.fillMaxWidth())
        }
        Section("OPPO / 其他安卓桌面"){
            Text("长按桌面空白处 → 小部件／插件 → 桌面清单 → 拖到桌面。部分桌面需要横向浏览同一应用的组件。具体入口名称以手机为准。",lineHeight=24.sp)
            Text("OPPO Find X7 Ultra／ColorOS16 尚未真机验证。",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Section("系统识别检查"){
            Text(status?.report()?:"正在检查…",lineHeight=24.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Secondary("重新检查",{refresh++},Modifier.weight(1f))
                Secondary("复制结果",{status?.let{(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("桌面组件检查",it.report()));scope.launch{feedback.showSnackbar("已复制检查结果",duration=SnackbarDuration.Indefinite)}}},Modifier.weight(1f),status!=null)
            }
            Text("系统可见数量反映当前查询结果；小米请以“安卓小部件”列表实际显示为准。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Section("官方说明"){
            TextButton({open(Intent(Intent.ACTION_VIEW,Uri.parse("https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1664")))}){Text("小米：安卓小部件入口")}
            TextButton({open(Intent(Intent.ACTION_VIEW,Uri.parse("https://developer.android.com/develop/ui/views/appwidgets/overview")))}){Text("Android：桌面小组件")}
        }
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
    }}
}
