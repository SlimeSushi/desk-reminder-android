@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.deskreminder.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import cn.deskreminder.mobile.MainViewModel
import cn.deskreminder.mobile.data.*
import cn.deskreminder.mobile.data.model.*
import kotlinx.coroutines.android.awaitFrame
import java.time.*

@Composable fun UnsavedDialog(onContinue:()->Unit,onKeep:()->Unit,onDiscard:()->Unit){AppAlertDialog(onDismissRequest=onContinue,title={Text("修改尚未保存")},text={Text("可以保留本机草稿，下次继续编辑。")},confirmButton={Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){Primary("继续编辑",onContinue,Modifier.fillMaxWidth());Secondary("保留草稿并返回",onKeep,Modifier.fillMaxWidth());TextButton(onDiscard,Modifier.fillMaxWidth(),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("放弃修改")}}})}

@Composable fun TodoEditor(source:Todo?,week:String?,settings:MobileSettings,vm:MainViewModel,onBack:()->Unit,forceTimed:Boolean=false){
    val key="todo:${source?.id?:"new"}"
    val baseline=remember(key){source?.let(BackupCodec::copy)?:Todo(weeklyStartDate=LocalDate.now().toString(),weeklyEndDate=LocalDate.now().plusMonths(1).toString())}
    val initial=remember(key){runCatching{vm.repo.draft(key).takeIf{it.isNotBlank()}?.let{BackupCodec.gson.fromJson(it,TodoDraft::class.java)}}.getOrNull()?:TodoDraft(source?.let{BackupCodec.gson.toJson(it)},BackupCodec.copy(baseline))}
    var draft by remember(key){mutableStateOf(initial)}
    val task=draft.todo;val originalItem=source?.let{Schedule.occurrence(it,week)};val completed=originalItem?.completed==true
    var error by remember{mutableStateOf("")};var leaving by remember{mutableStateOf(false)}
    val focus=remember{FocusRequester()};val keyboard=LocalSoftwareKeyboardController.current
    val busy by vm.busy.collectAsState()
    fun update(value:Todo,actual:String?=draft.actualTime){draft=draft.copy(todo=value,actualTime=actual);vm.repo.saveDraft(key,BackupCodec.gson.toJson(draft));error=""}
    LaunchedEffect(key){if(forceTimed&&task.type=="open")update(task.copy(type="once",dueAt=Dates.minute(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0))));if(source==null){awaitFrame();focus.requestFocus();keyboard?.show()}}
    fun exit(){if(BackupCodec.gson.toJson(task)!=BackupCodec.gson.toJson(baseline)||draft.actualTime!=null)leaving=true else{vm.repo.saveDraft(key,"");onBack()}}
    fun save(){
        if(task.title.isBlank()){error="请输入标题";focus.requestFocus();return}
        val candidate=BackupCodec.copy(task).apply{title=title.trim();if(type=="open")reminder=null}
        try{
            BackupCodec.validate(AppData(todos=mutableListOf(candidate),settings=settings))
            if(draft.actualTime!=null)require(Dates.moment(draft.actualTime)!=null){"实际完成时间无效"}
            vm.saveTask(candidate,draft.original,draft.actualTime?.let(Dates::moment),week,onBack)
        }catch(value:Exception){error=value.localizedMessage?:"请检查填写内容"}
    }
    BackHandler{exit()}
    Page(if(source==null)"新增待办"else"编辑待办",{exit()},bottom={FormFooter({exit()},{save()},busy)}){padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
            AppTextField(task.title,{update(task.copy(title=it))},Modifier.fillMaxWidth().focusRequester(focus),label={Text("标题 · 必填")},minLines=1,maxLines=3,shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),isError=error=="请输入标题",keyboardOptions=KeyboardOptions(imeAction=ImeAction.Next))
            AppTextField(task.content,{update(task.copy(content=it))},Modifier.fillMaxWidth(),label={Text("内容 · 选填")},minLines=1,maxLines=8,shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            if(!completed){
                var typePicker by remember{mutableStateOf(false)}
                SelectionRow("计划类型",when(task.type){"once"->"指定时间";"weekly"->"每周重复";else->"无期限"},onClick={typePicker=true})
                if(typePicker)SingleChoiceSheet("计划类型",task.type,listOf("open" to "无期限","once" to "指定时间","weekly" to "每周重复"),{typePicker=false}){value->
                    val next=task.copy(type=value)
                    if(value=="once"&&Dates.moment(next.dueAt)==null)next.dueAt=Dates.minute(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0))
                    if(value=="weekly"){if(Dates.date(next.weeklyStartDate)==null)next.weeklyStartDate=LocalDate.now().toString();if(Dates.date(next.weeklyEndDate)==null)next.weeklyEndDate=LocalDate.now().plusMonths(1).toString();if(Dates.time(next.weeklyTime)==null)next.weeklyTime="17:00"}
                    update(next);typePicker=false
                }
                if(task.type=="once")DateTimeField("计划完成",Dates.moment(task.dueAt)?:LocalDateTime.now(),{update(task.copy(dueAt=Dates.minute(it)))})
                if(task.type=="weekly"){
                    DateOnlyField("周期开始",Dates.date(task.weeklyStartDate)?:LocalDate.now(),{update(task.copy(weeklyStartDate=it.toString()))})
                    DateOnlyField("周期结束",Dates.date(task.weeklyEndDate)?:LocalDate.now().plusMonths(1),{update(task.copy(weeklyEndDate=it.toString()))})
                    WeekdayButtons(task.weekday){day->update(task.copy(weekday=day))}
                    TimeField(Dates.time(task.weeklyTime)?:LocalTime.of(17,0),{time->update(task.copy(weeklyTime=time.toString()))})
                }
                if(task.type!="open"&&!task.stopped){HorizontalDivider();RuleEditor(task.reminder?:settings.reminder,{update(task.copy(reminder=it))},{update(task.copy(reminder=null))})}
            }else{
                Section("计划安排"){originalItem?.due?.let{Text(Dates.display(it))}?:Text("无期限")}
                val actual=Dates.moment(draft.actualTime)?:originalItem?.actual
                if(actual!=null)DateTimeField("实际完成",actual,{update(task,Dates.stamp(it))})else TextButton({update(task,Dates.stamp())}){Text("补记实际完成时间")}
            }
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }
    }
    if(leaving)UnsavedDialog({leaving=false},{leaving=false;onBack()},{vm.repo.saveDraft(key,"");leaving=false;onBack()})
}

@Composable fun NoteEditor(source:Note?,vm:MainViewModel,onBack:()->Unit){
    val key="note:${source?.id?:"new"}";val baseline=remember(key){source?.let(BackupCodec::copy)?:Note()}
    val initial=remember(key){runCatching{vm.repo.draft(key).takeIf{it.isNotBlank()}?.let{BackupCodec.gson.fromJson(it,NoteDraft::class.java)}}.getOrNull()?:NoteDraft(source?.let{BackupCodec.gson.toJson(it)},BackupCodec.copy(baseline))}
    var draft by remember(key){mutableStateOf(initial)};var leaving by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
    val focus=remember{FocusRequester()};val keyboard=LocalSoftwareKeyboardController.current;val busy by vm.busy.collectAsState()
    fun update(value:Note){draft=draft.copy(note=value);vm.repo.saveDraft(key,BackupCodec.gson.toJson(draft));error=""}
    fun exit(){if(BackupCodec.gson.toJson(draft.note)!=BackupCodec.gson.toJson(baseline))leaving=true else onBack()}
    BackHandler{exit()}
    LaunchedEffect(key){awaitFrame();focus.requestFocus();keyboard?.show()}
    Page(if(source==null)"新增随手记"else"编辑随手记",{exit()},bottom={FormFooter({exit()},{if(draft.note.text.isBlank())error="请填写随手记内容"else vm.saveNote(draft.note,draft.original,onBack)},busy)}){padding->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            SwitchRow("置顶",draft.note.pinned,{update(draft.note.copy(pinned=it))})
            AppTextField(draft.note.text,{update(draft.note.copy(text=it))},Modifier.fillMaxWidth().weight(1f).focusRequester(focus),label={Text("随手记内容")},shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        }
    }
    if(leaving)UnsavedDialog({leaving=false},{leaving=false;onBack()},{vm.repo.saveDraft(key,"");leaving=false;onBack()})
}

@Composable fun PostponeDialog(item:Occurrence,vm:MainViewModel,onDismiss:()->Unit){
    var reason by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf("")}
    var mode by remember{mutableIntStateOf(0)};var days by remember{mutableStateOf("1")};var target by remember{mutableStateOf(item.due!!.plusDays(1))};var all by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};val busy by vm.busy.collectAsState()
    FormSheet("延期",{if(!busy)onDismiss()},{
        val number=days.toLongOrNull();val result=if(mode==0&&number!=null&&number in 1..365000)item.due!!.plusDays(number)else if(mode==1)target else null
        if(result==null||result<=item.due)error="请选择晚于当前计划的时间"else vm.postpone(item,result,all,reason,onDismiss)
    },confirm=if(busy)"保存中…"else"确认延期",enabled=!busy){
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
            Text(item.todo.title,style=MaterialTheme.typography.titleMedium)
            Text("当前计划 ${Dates.display(item.due)}",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(item.todo.type=="weekly")SegmentedControl(listOf(false to "仅这一次",true to "全量延期"),all,{all=it})
            SegmentedControl(listOf(0 to "延几天",1 to "延到指定时间"),mode,{mode=it})
            if(mode==0)AppTextField(days,{days=it;error=""},label={Text("延期天数")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth())else DateTimeField("延期至",target,{target=it;error=""})
            AppTextField(reason,{reason=it},label={Text("延期原因 · 选填")},minLines=2,maxLines=4,modifier=Modifier.fillMaxWidth())
            if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
        }
    }
}

@Composable fun RemovalDialog(item:Occurrence,vm:MainViewModel,onDismiss:()->Unit,onDone:()->Unit){
    val canStop=!item.todo.stopped&&(!item.completed||item.todo.type=="weekly")&&!item.ended
    var stop by remember{mutableStateOf(canStop)};var reason by remember{mutableStateOf("")};val busy by vm.busy.collectAsState()
    FormSheet("处理这项待办",{if(!busy)onDismiss()},{if(!busy)vm.remove(item,stop,reason){onDismiss();onDone()}},confirm=if(busy)"处理中…"else if(stop)"确认中止"else"确认删除",danger=!stop,enabled=!busy){
        Text(item.todo.title,Modifier.padding(bottom=12.dp),style=MaterialTheme.typography.titleMedium)
        if(canStop)RadioOption("任务中止",stop,{stop=true})
        RadioOption("删除任务",!stop,{stop=false})
        if(stop)AppTextField(reason,{reason=it},label={Text("中止原因 · 选填")},minLines=2,maxLines=4,shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().padding(top=12.dp))
        Text(if(stop){if(item.todo.type=="weekly")"中止周期内后续事项，保留已完成和延期历史，记录移入「已中止」。"else"移入「已中止」并停止后续提醒，之后可随时恢复。"}else if(item.todo.type=="weekly")"删除整个周期及其完成、延期记录。"else"删除此任务及相关记录。",Modifier.padding(top=12.dp),fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun ActualTimeDialog(item:Occurrence,vm:MainViewModel,onDismiss:()->Unit){
    var value by remember{mutableStateOf(item.actual?:LocalDateTime.now().withSecond(0).withNano(0))};var error by remember{mutableStateOf("")};val busy by vm.busy.collectAsState()
    FormSheet("实际完成时间",{if(!busy)onDismiss()},{vm.changeActual(item,value,onDismiss)},confirm="保存",enabled=!busy){
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)){Text(item.todo.title,style=MaterialTheme.typography.titleMedium);DateTimeField("实际完成",value,{value=it;error=""});if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)}
    }
}
