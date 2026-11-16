@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.deskreminder.mobile.ui

import android.content.Intent
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.deskreminder.mobile.*
import cn.deskreminder.mobile.data.*
import cn.deskreminder.mobile.data.model.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.time.*

@Composable fun DeskApp(incoming:Intent?,consumeIntent:()->Unit,requestNotifications:(Boolean)->Unit){
    val application=LocalContext.current.applicationContext as DeskApplication
    val factory=remember(application){object:androidx.lifecycle.ViewModelProvider.Factory{
        @Suppress("UNCHECKED_CAST") override fun <T:androidx.lifecycle.ViewModel> create(modelClass:Class<T>):T=MainViewModel(application) as T
    }}
    val vm:MainViewModel=viewModel(factory=factory)
    val state by vm.data.collectAsState();val fatal by vm.repo.fatal.collectAsState();val busy by vm.busy.collectAsState();val context=LocalContext.current
    var tab by rememberSaveable{mutableIntStateOf(0)};var route by rememberSaveable{mutableStateOf("home")};var returnRoute by rememberSaveable{mutableStateOf("home")}
    var taskId by rememberSaveable{mutableStateOf<String?>(null)};var week by rememberSaveable{mutableStateOf<String?>(null)};var noteId by rememberSaveable{mutableStateOf<String?>(null)}
    var timed by rememberSaveable{mutableStateOf(false)};var todoSearch by rememberSaveable{mutableStateOf(false)};var noteSearch by rememberSaveable{mutableStateOf(false)};var todoQuery by rememberSaveable{mutableStateOf("")};var noteQuery by rememberSaveable{mutableStateOf("")};var calendarQuery by rememberSaveable{mutableStateOf("")}
    var error by remember{mutableStateOf("")};val snackbar=remember{SnackbarHostState()};val scope=rememberCoroutineScope();val holder=rememberSaveableStateHolder()
    var postponed by remember{mutableStateOf<Occurrence?>(null)};var removing by remember{mutableStateOf<Occurrence?>(null)};var actual by remember{mutableStateOf<Occurrence?>(null)};var uncomplete by remember{mutableStateOf<Occurrence?>(null)};var trashing by remember{mutableStateOf<Note?>(null)};var resuming by remember{mutableStateOf<Occurrence?>(null)}
    var imported by vm.importPreview
    var restoreLabel by rememberSaveable{mutableStateOf("")}
    val now by produceState(LocalDateTime.now()){while(true){value=LocalDateTime.now();delay(30_000)}}
    fun back(){route=if(returnRoute!="home"&&route in listOf("taskEdit","noteEdit","widget","widgetGuide","permissions"))returnRoute else "home";returnRoute="home"}
    fun openTask(item:Occurrence){taskId=item.todo.id;week=item.week;route="taskDetail";returnRoute="home"}
    fun editTask(item:Occurrence?,force:Boolean=false){taskId=item?.todo?.id;week=item?.week;timed=force;returnRoute=if(route=="taskDetail")"taskDetail"else"home";route="taskEdit"}
    fun delayTask(item:Occurrence){if(item.due==null)editTask(item,true)else postponed=item}
    fun toggle(item:Occurrence){if(item.completed)uncomplete=item else vm.complete(item,true)}
    fun resumeTask(item:Occurrence){if(Schedule.resumeWarning(item.todo)==null)vm.resume(item)else resuming=item}
    fun export(){
        var share:Intent?=null
        vm.operation(onDone={runCatching{context.startActivity(share!!)}.onFailure{error="无法打开分享面板，请检查手机上是否有可接收文件的应用。"}}){
            val text=vm.repo.exportCurrent()
            share=BackupSharing.prepare(context,text)
        }
    }
    fun preview(json:String,label:String=""){
        val current=state?.settings?:MobileSettings();var parsed:ImportedData?=null
        vm.operation(onDone={restoreLabel=label;vm.importPreview.value=parsed}){parsed=BackupCodec.read(json,current)}
    }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){var json="";vm.operation(onDone={preview(json)}){
        context.contentResolver.openInputStream(uri)?.use{input->val output=ByteArrayOutputStream();val buffer=ByteArray(16384);while(true){val count=input.read(buffer);if(count<0)break;require(output.size()+count<=50*1024*1024){"备份超过50MB"};output.write(buffer,0,count)};json=output.toString("UTF-8")}?:error("备份文件无法读取")
    }}}
    LaunchedEffect(vm){vm.events.collect{event->when(event){is UiEvent.Message->scope.launch{snackbar.showSnackbar(event.value,duration=SnackbarDuration.Indefinite)};is UiEvent.Error->error=event.value;UiEvent.AskNotifications->if(!vm.app.reminders.notificationsAllowed())requestNotifications(false)}}}
    LaunchedEffect(Unit){vm.repo.backupWarning.collect{warning->if(warning!=null){error=warning;vm.repo.backupWarning.value=null}}}
    LaunchedEffect(incoming,state!=null){if(incoming!=null&&state!=null){
        when{incoming.hasExtra("task")->{taskId=incoming.getStringExtra("task");week=incoming.getStringExtra("week");route="taskDetail"}
            incoming.hasExtra("note")->{noteId=incoming.getStringExtra("note");route="noteDetail";tab=2}
            incoming.getStringExtra("new")=="todo"->{taskId=null;week=null;route="taskEdit";returnRoute="home";timed=false}
            incoming.getStringExtra("new")=="note"->{noteId=null;route="noteEdit";returnRoute="home";tab=2}
            incoming.hasExtra("tab")->{tab=incoming.getIntExtra("tab",0).coerceIn(0,3);route=if(incoming.getBooleanExtra("widget",false))"widget"else"home"}}
        consumeIntent()
    }}
    LaunchedEffect(route,postponed,removing,actual,uncomplete,trashing,resuming,imported){application.cloud.setEditing(route in listOf("taskEdit","noteEdit")||postponed!=null||removing!=null||actual!=null||uncomplete!=null||trashing!=null||resuming!=null||imported!=null)}
    DisposableEffect(application){onDispose{application.cloud.setEditing(false)}}
    BackHandler(enabled=route!="home"&&route!="taskEdit"&&route!="noteEdit"){back()}
    DeskTheme(state?.settings?.theme?:"light",state?.settings?.appStyle?:"fruit"){
        Box(Modifier.fillMaxSize()){
        if(state==null){Scaffold{padding->Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),verticalArrangement=Arrangement.Center){if(fatal==null)CircularProgressIndicator()else{Text(fatal.orEmpty());Spacer(Modifier.height(20.dp));Primary("导入备份",{importer.launch(arrayOf("application/json","text/plain","*/*"))})}}}}
        else{
            val data=state!!;val task=data.todos.firstOrNull{it.id==taskId};val note=data.notes.firstOrNull{it.id==noteId}
            val item=task?.let{value->if(value.type=="weekly"&&week==null)Schedule.pending(value)?:Schedule.history(data,applyRange=false).firstOrNull{it.todo.id==value.id}?:Schedule.stopped(data).firstOrNull{it.todo.id==value.id}?:Occurrence(value)else Schedule.occurrence(value,week)}
            when(route){
                "taskEdit"->TodoEditor(task,week,data.settings,vm,{back()},timed)
                "noteEdit"->NoteEditor(note,vm,{back()})
                "taskDetail"->if(item==null)Page("待办详情",{back()}){p->Box(Modifier.padding(p)){EmptyState("事项已变化或被删除","返回",{back()})}}else TaskDetails(item,vm,{back()},{editTask(item)},{delayTask(item)},{removing=item},{toggle(item)},{actual=item},{resumeTask(item)})
                "noteDetail"->if(note==null)Page("随手记",{back()}){p->Box(Modifier.padding(p)){EmptyState("随手记已移入废纸篓","返回",{back()})}}else NoteDetails(note,vm,{back()},{returnRoute="noteDetail";route="noteEdit"},{trashing=note})
                "trash"->TrashPage(data,vm,{back()})
                "general"->GeneralSettings(data,vm,{back()})
                "reminder"->ReminderSettings(data,vm,{back()},{returnRoute="reminder";route="permissions"})
                "appearance"->AppearanceSettings(data,vm,{back()})
                "widget"->WidgetSettings(data,vm,{back()},{returnRoute="widget";route="widgetGuide"})
                "widgetGuide"->WidgetGuide({back()})
                "permissions"->PermissionPage(vm,{back()},{requestNotifications(true)})
                "backup","cloud"->DataBackupScreen(data,vm,{back()},{importer.launch(arrayOf("application/json","text/plain","*/*"))},{export()},{text,label->preview(text,label)})
                else->{
                    GlassHost{backdropModifier->
                    Scaffold(modifier=Modifier.imePadding(),topBar={HomeHeader(tab,when(tab){0->todoQuery;1->calendarQuery;else->noteQuery},if(tab==0)todoSearch else noteSearch,
                        {value->when(tab){0->todoQuery=value;1->calendarQuery=value;2->noteQuery=value}},
                        {if(tab==0)todoSearch=true else noteSearch=true},
                        {if(tab==0){todoQuery="";todoSearch=false}else{noteQuery="";noteSearch=false}},actions={
                        if(tab==0)FilledIconButton({editTask(null)},Modifier.size(44.dp),shape=if(LocalAppStyle.current==AppStyle.CLASSIC)RoundedCornerShape(12.dp)else androidx.compose.foundation.shape.CircleShape,enabled=!busy){Icon(Icons.Rounded.Add,"新增待办",Modifier.size(25.dp))}
                        if(tab==2)ActionIcon(if(data.noteTrash.isEmpty())Icons.Rounded.DeleteOutline else Icons.Rounded.Delete,"废纸篓 ${data.noteTrash.size}条",{route="trash"})
                    })},bottomBar={if(!WindowInsets.isImeVisible)FloatingTabs(tab,{tab=it})},floatingActionButton={if(tab==2)FloatingActionButton({noteId=null;route="noteEdit";returnRoute="home"},Modifier.size(52.dp),shape=if(LocalAppStyle.current==AppStyle.CLASSIC)RoundedCornerShape(16.dp)else androidx.compose.foundation.shape.CircleShape,containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary){Icon(Icons.Rounded.Add,"新增随手记",Modifier.size(27.dp))}},snackbarHost={OperationMessageHost(snackbar)},containerColor=MaterialTheme.colorScheme.background,contentWindowInsets=WindowInsets.safeDrawing){padding->
                        CompositionLocalProvider(LocalChromeBottomInset provides padding.calculateBottomPadding()){
                        Column(Modifier.fillMaxSize().padding(top=padding.calculateTopPadding()).then(backdropModifier)){
                            holder.SaveableStateProvider(tab){when(tab){
                                0->TodoList(data,todoQuery,::openTask,{editTask(it)},::delayTask,{removing=it},::toggle,::resumeTask,now)
                                1->CalendarPage(data,calendarQuery,::openTask,{actual=it})
                                2->NotesPage(data,vm,noteQuery,{noteId=it.id;route="noteDetail"},{noteId=it.id;returnRoute="home";route="noteEdit"},{trashing=it})
                                3->SettingsPage(data,vm,{destination->returnRoute="home";route=destination})
                            }}
                        }
                        }
                    }
                    }
                }
            }
        }
        if(route!="home")OperationMessageHost(snackbar,Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom=80.dp))
        }
        postponed?.let{PostponeDialog(it,vm,{postponed=null})}
        removing?.let{RemovalDialog(it,vm,{removing=null},{if(route=="taskDetail")route="home"})}
        actual?.let{ActualTimeDialog(it,vm,{actual=null})}
        uncomplete?.let{value->MessageDialog("取消完成？",if(value.todo.stopped)"取消“${value.todo.title}”本次完成。该周期已中止，提醒继续保持关闭。"else"“${value.todo.title}”将返回待完成，并按当前规则重新计算提醒。","取消完成",false,{uncomplete=null}){vm.complete(value,false){uncomplete=null}}}
        resuming?.let{value->Schedule.resumeWarning(value.todo)?.let{warning->ResumeDialog(value.todo.title,warning,{resuming=null}){vm.resume(value){resuming=null}}}}
        trashing?.let{value->MessageDialog("移入废纸篓？",value.text.take(160)+"\n\n之后可从废纸篓恢复。","移入废纸篓",false,{trashing=null}){vm.trash(value){trashing=null;if(route=="noteDetail")route="home"}}}
        imported?.let{value->BackupRestoreDialog(state,value,restoreLabel,busy,{imported=null;restoreLabel=""},syncAfter=application.cloud.state.value.connected&&application.cloud.state.value.automatic){vm.operation("备份已恢复",{imported=null;restoreLabel="";route="home"}){vm.repo.restore(value)}}}
        if(error.isNotBlank())AppAlertDialog(onDismissRequest={error=""},title={Text("操作未完成")},text={Text(error)},confirmButton={TextButton({error=""}){Text("知道了")}})
    }
}
