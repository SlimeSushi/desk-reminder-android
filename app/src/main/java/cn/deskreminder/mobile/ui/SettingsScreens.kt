@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.deskreminder.mobile.ui

import android.content.*
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.deskreminder.mobile.*
import cn.deskreminder.mobile.data.model.*
import cn.deskreminder.mobile.widget.*

@Composable fun SettingsPage(data:AppData,vm:MainViewModel,onNavigate:(String)->Unit){
    val cloud by vm.app.cloud.state.collectAsState()
    val classic=LocalAppStyle.current==AppStyle.CLASSIC
    val edge=if(classic)16.dp else 20.dp
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=edge,end=edge,top=8.dp,bottom=LocalChromeBottomInset.current+8.dp),verticalArrangement=Arrangement.spacedBy(if(classic)16.dp else 20.dp)){
        item{SettingsGroup{
            SettingsLink("通用",if(data.settings.historyDays==0)"显示全部已完成事项"else"显示近${data.settings.historyDays}天已完成事项",Icons.Rounded.Tune,{onNavigate("general")})
            SettingsDivider()
            SettingsLink("外观",when(data.settings.theme){"dark"->"深色";"system"->"跟随系统";else->"浅色"},Icons.Rounded.Palette,{onNavigate("appearance")})
            SettingsDivider()
            SettingsLink("提醒",if(data.settings.reminder.enabled)"提前${durationLabel(data.settings.reminder.advanceMinutes)} · ${durationLabel(data.settings.reminder.repeatMinutes)}重复"else"已关闭",Icons.Rounded.NotificationsNone,{onNavigate("reminder")})
            SettingsDivider()
            SettingsLink("桌面组件","标题、计划时间与桌面添加",Icons.Rounded.Widgets,{onNavigate("widget")})
        }}
        item{SettingsGroup{
            SettingsLink("数据备份",cloud.status,Icons.Rounded.CloudQueue,{onNavigate("backup")})
            SettingsDivider()
            SettingsLink("通知与后台",if(vm.app.reminders.notificationsAllowed())"通知已允许"else"检查系统权限",Icons.Rounded.VerifiedUser,{onNavigate("permissions")})
        }}
        item{Text("桌面清单 ${BuildConfig.VERSION_NAME}",Modifier.fillMaxWidth().padding(8.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
@Composable private fun SettingsDivider(){HorizontalDivider(Modifier.padding(start=if(LocalAppStyle.current==AppStyle.CLASSIC)54.dp else 52.dp))}
@Composable private fun SettingsGroup(content:@Composable ColumnScope.()->Unit){Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.padding(horizontal=16.dp),content=content)}}
@Composable private fun SettingsLink(title:String,summary:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){
    val classic=LocalAppStyle.current==AppStyle.CLASSIC
    val tile=when(title){"通用"->Color(0xFF6B6B76);"提醒"->Color(0xFFC64B36);"外观"->Color(0xFF8050BD);"桌面组件"->Color(0xFF147D76);"备份与恢复"->Color(0xFF1567D6);else->Color(0xFF278445)}
    val large=androidx.compose.ui.platform.LocalConfiguration.current.fontScale>=1.5f
    Row(Modifier.fillMaxWidth().testTag("settings-link-$title").heightIn(min=if(classic)84.dp else 64.dp).clickable(onClick=onClick).padding(vertical=if(classic)16.dp else 12.dp),verticalAlignment=Alignment.CenterVertically){
        if(classic)Surface(Modifier.size(42.dp),shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.primaryContainer){Box(contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)}}
        else Surface(shape=RoundedCornerShape(8.dp),color=tile){Icon(icon,null,Modifier.padding(6.dp).size(19.dp),tint=Color.White)}
        Column(Modifier.weight(1f).padding(start=12.dp,end=8.dp),verticalArrangement=Arrangement.spacedBy(if(classic)6.dp else 4.dp)){
            Text(title,fontSize=17.sp)
            if(classic||large)Text(summary,fontSize=if(classic)14.sp else 13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(!classic&&!large)Text(summary,Modifier.widthIn(max=132.dp),fontSize=13.sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Rounded.ChevronRight,null,Modifier.padding(start=6.dp).size(if(classic)22.dp else 17.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=if(classic)1f else .6f))
    }
}
@Composable fun GeneralSettings(data:AppData,vm:MainViewModel,onBack:()->Unit){
    var custom by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf((data.settings.historyDays.takeIf{it>0&&it !in listOf(7,30,90)}?:30).toString())}
    var error by remember{mutableStateOf("")}
    Page("通用",onBack){padding->Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)){
        Section("已完成显示范围"){
            listOf(listOf(0 to "全部",7 to "近7天"),listOf(30 to "近30天",90 to "近90天")).forEach{options->
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){options.forEach{(days,label)->Choice(label,data.settings.historyDays==days,{error="";vm.mutate{it.settings.historyDays=days}},Modifier.weight(1f))}}
            }
            HorizontalDivider(Modifier.padding(vertical=4.dp))
            Row(verticalAlignment=Alignment.CenterVertically){Text("自定义",Modifier.weight(1f));if(data.settings.historyDays>0&&data.settings.historyDays !in listOf(7,30,90))Text("近${data.settings.historyDays}天",color=MaterialTheme.colorScheme.primary)}
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.weight(1f)){NumberControl(custom,{custom=it;error=""},"天")}
                Primary("应用",{val days=custom.toIntOrNull();if(days==null||days<1)error="请输入有效的正整数天数"else vm.mutate{it.settings.historyDays=days}})
            }
            if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
        }
    }}
}
@Composable fun ReminderSettings(data:AppData,vm:MainViewModel,onBack:()->Unit,onPermissions:()->Unit){
    Page("提醒",onBack){padding->Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Section("提醒规则"){RuleEditor(data.settings.reminder,{rule->vm.mutate{it.settings.reminder=rule}},showTitle=false)}
        Section("系统权限"){SettingRow("通知与后台检查",onClick=onPermissions)}
    }}
}
@Composable fun AppearanceSettings(data:AppData,vm:MainViewModel,onBack:()->Unit){
    val busy by vm.busy.collectAsState()
    Page("外观",onBack){padding->Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Section("应用外观"){
            Text("风格",style=MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Choice("经典",data.settings.appStyle=="classic",{vm.mutate{it.settings.appStyle="classic"}},Modifier.weight(1f))
                Choice("果味",data.settings.appStyle=="fruit",{vm.mutate{it.settings.appStyle="fruit"}},Modifier.weight(1f))
            }
            Text("主题",style=MaterialTheme.typography.titleSmall)
            ThemeOptions(data.settings.theme,!busy){value->vm.mutate{it.settings.theme=value}}
        }
        Section("到期状态颜色"){
            SwitchRow("待办文字变色",data.settings.reminder.highlight,{value->vm.mutate{it.settings.reminder.highlight=value}})
            listOf(cn.deskreminder.mobile.data.DueState.NORMAL to "正常",cn.deskreminder.mobile.data.DueState.SOON to "临近截止",cn.deskreminder.mobile.data.DueState.OVERDUE to "已逾期").forEach{(state,label)->
                Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("确认项目资料",color=dueStateColor(if(data.settings.reminder.highlight)state else cn.deskreminder.mobile.data.DueState.NORMAL))}
            }
        }
    }}
}
@Composable private fun ThemeOptions(selected:String,enabled:Boolean,onSelect:(String)->Unit){
    val choices=listOf(Triple("light","浅色",Icons.Rounded.LightMode),Triple("dark","深色",Icons.Rounded.DarkMode),Triple("system","跟随系统",Icons.Rounded.DesktopWindows))
    val large=LocalDensity.current.fontScale>=1.5f
    BoxWithConstraints(Modifier.fillMaxWidth()){
        val verticalIcon=maxWidth<300.dp
        if(large)Column(Modifier.fillMaxWidth().selectableGroup(),verticalArrangement=Arrangement.spacedBy(8.dp)){
            choices.forEach{(key,label,icon)->ThemeOption(key,label,icon,selected==key,enabled,false,{onSelect(key)},Modifier.fillMaxWidth())}
        }else Row(Modifier.fillMaxWidth().selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            choices.forEach{(key,label,icon)->ThemeOption(key,label,icon,selected==key,enabled,verticalIcon,{onSelect(key)},Modifier.weight(1f))}
        }
    }
}
@Composable private fun ThemeOption(key:String,label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,selected:Boolean,enabled:Boolean,vertical:Boolean,onClick:()->Unit,modifier:Modifier){
    val interactions=remember{MutableInteractionSource()};val pressed by interactions.collectIsPressedAsState();val focused by interactions.collectIsFocusedAsState()
    val accent=MaterialTheme.colorScheme.primary
    val shape=RoundedCornerShape(10.dp)
    Surface(modifier.testTag("theme-option-$key").heightIn(min=48.dp).clip(shape).selectable(selected,enabled=enabled,role=Role.RadioButton,interactionSource=interactions,indication=null,onClick=onClick),shape=shape,
        color=if(selected||pressed)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border=BorderStroke(if(selected||focused)1.dp else .7.dp,if(selected||focused)accent else MaterialTheme.colorScheme.outlineVariant),
        contentColor=if(selected)accent else MaterialTheme.colorScheme.onSurfaceVariant){
        if(vertical)Column(Modifier.padding(horizontal=6.dp,vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){
            Icon(icon,null,Modifier.size(18.dp));Text(label,fontSize=14.sp,lineHeight=20.sp,fontWeight=if(selected)FontWeight.Medium else FontWeight.Normal)
        }else Row(Modifier.padding(horizontal=6.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
            Icon(icon,null,Modifier.size(18.dp));Spacer(Modifier.width(4.dp));Text(label,fontSize=14.sp,lineHeight=20.sp,fontWeight=if(selected)FontWeight.Medium else FontWeight.Normal)
        }
    }
}
@Composable fun WidgetSettings(data:AppData,vm:MainViewModel,onBack:()->Unit,onGuide:()->Unit){
    Page("桌面组件",onBack){padding->Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)){
        Section("手机桌面"){WidgetControls(data,vm,onGuide)}
    }}
}
@Composable private fun WidgetControls(data:AppData,vm:MainViewModel,onGuide:()->Unit){
    val context=LocalContext.current;var colorTarget by remember{mutableStateOf("")};var background by remember{mutableFloatStateOf(data.settings.widgetBackgroundOpacity.toFloat())};var text by remember{mutableFloatStateOf(data.settings.widgetTextOpacity.toFloat())}
    LaunchedEffect(data.settings.widgetBackgroundOpacity,data.settings.widgetTextOpacity){background=data.settings.widgetBackgroundOpacity.toFloat();text=data.settings.widgetTextOpacity.toFloat()}
    var pinResult by remember{mutableStateOf<PinResult?>(null)}
    LaunchedEffect(Unit){val initial=WidgetPinning.confirmations.value;WidgetPinning.confirmations.collect{value->if(value!=initial){pinResult=null;vm.events.tryEmit(UiEvent.Message("桌面组件已添加"))}}}
    fun pin(notes:Boolean){pinResult=WidgetPinning.request(context,notes)}
    SwitchRow("显示待办标题",data.settings.widgetTodoTitle,{value->vm.mutate{it.settings.widgetTodoTitle=value}})
    SwitchRow("显示随手记标题",data.settings.widgetNoteTitle,{value->vm.mutate{it.settings.widgetNoteTitle=value}})
    Text("待办计划时间",style=MaterialTheme.typography.labelLarge)
    SegmentedControl(listOf("none" to "不显示","through_today" to "截至今天","all" to "全部"),data.settings.widgetTimeMode,{value->vm.mutate{it.settings.widgetTimeMode=value}})
    var notes by remember{mutableStateOf(false)}
    SegmentedControl(listOf(false to "待办预览",true to "随手记预览"),notes,{notes=it})
    Box(Modifier.fillMaxWidth().height(220.dp).background(Color(0xFFBDD9EA),RoundedCornerShape(18.dp)).padding(12.dp)){
        val previewData=data.copy(settings=data.settings.copy(widgetBackgroundOpacity=background.toInt(),widgetTextOpacity=text.toInt()))
        androidx.compose.ui.viewinterop.AndroidView(factory={android.widget.FrameLayout(it)},update={host->host.removeAllViews();host.addView(Widgets.preview(context,notes,previewData).apply(context,host))},modifier=Modifier.fillMaxSize())
    }
            SettingRow("背景颜色",data.settings.widgetBackground,{colorTarget="background"})
            SettingRow("背景不透明度","${background.toInt()}%")
            DeskSlider(background,{background=it},valueRange=0f..100f,onValueChangeFinished={vm.mutate{it.settings.widgetBackgroundOpacity=background.toInt()}})
            HorizontalDivider()
            SettingRow("文字颜色",data.settings.widgetText,{colorTarget="text"})
            SettingRow("文字不透明度","${text.toInt()}%")
            DeskSlider(text,{text=it},valueRange=0f..100f,onValueChangeFinished={vm.mutate{it.settings.widgetTextOpacity=text.toInt()}})
        if(background==0f&&text==0f)Secondary("恢复组件可见",{vm.mutate{it.settings.widgetTextOpacity=100;it.settings.widgetBackgroundOpacity=86}})
        Primary("添加待办组件",{pin(false)},Modifier.fillMaxWidth());Secondary("添加随手记组件",{pin(true)},Modifier.fillMaxWidth())
        SettingRow("添加教程与系统检查",onClick=onGuide)
    if(colorTarget.isNotEmpty())ColorChooser(if(colorTarget=="background")"背景颜色"else"文字颜色",if(colorTarget=="background")data.settings.widgetBackground else data.settings.widgetText,{colorTarget=""}){hex->val target=colorTarget;vm.mutate("颜色已更新",{colorTarget=""}){if(target=="background")it.settings.widgetBackground=hex else it.settings.widgetText=hex}}
    if(pinResult==PinResult.REQUESTED){
        LaunchedEffect(pinResult){kotlinx.coroutines.delay(15000);pinResult=null}
        Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primaryContainer){Column(Modifier.padding(12.dp)){
            Text("请在系统桌面中确认添加",style=MaterialTheme.typography.bodyMedium)
            TextButton(onGuide){Text("没有出现添加窗口？")}
        }}
    }
    pinResult?.takeIf{it!=PinResult.REQUESTED}?.let{result->AppAlertDialog(onDismissRequest={pinResult=null},title={Text(if(result==PinResult.UNSUPPORTED)"桌面不支持直接添加"else"未能打开添加窗口")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("小米系统桌面：\n长按桌面空白处 → 添加小部件 → 搜索 → 安卓小部件 → 桌面清单 → 拖到桌面。\n\n添加后可长按组件，拖动边框调整尺寸。")
        TextButton({pinResult=null;onGuide()}){Text("查看完整教程与权限说明")}
    }},dismissButton={TextButton({pinResult=null}){Text("关闭")}},confirmButton={TextButton({pinResult=null;runCatching{context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))}}){Text("去桌面添加")}})}
}
@Composable fun PermissionPage(vm:MainViewModel,onBack:()->Unit,onNotifications:()->Unit){
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current;var refresh by remember{mutableIntStateOf(0)}
    DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)refresh++};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer)}}
    val notifications=remember(refresh){vm.app.reminders.notificationsAllowed()};val exact=remember(refresh){vm.app.reminders.exactAllowed()}
    fun open(intent:Intent){runCatching{context.startActivity(intent)}.onFailure{runCatching{context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}}}
    val manufacturer=Build.MANUFACTURER.lowercase();val vendor=when{manufacturer.contains("xiaomi")->"小米";manufacturer.contains("oppo")||manufacturer.contains("oneplus")->"ColorOS";else->"系统"}
    val power=context.getSystemService(PowerManager::class.java)
    Page("通知与后台检查",onBack){padding->Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Section("提醒权限"){
            SettingRow("通知权限",if(notifications)"已开启"else"未开启",{if(!notifications)onNotifications()else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))})
            SettingRow("准时提醒",if(exact)"已允许"else"未允许",{if(Build.VERSION.SDK_INT>=31)open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}")))})
        }
        Section("后台运行"){
            SettingRow("电池优化",if(power.isIgnoringBatteryOptimizations(context.packageName))"已豁免"else"系统默认",{open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))})
            SettingRow("${vendor}自启动与后台管理","去核对",{open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))})
        }
        Primary("发送测试提醒",{vm.operation("已发送测试提醒"){vm.app.reminders.sendTest()}},Modifier.fillMaxWidth())
        Text("机型 ${Build.MODEL}",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!notifications||!exact)Text("提醒规则可以保存。授予对应权限后，系统才能正常展示和调度提醒。",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }}
}
