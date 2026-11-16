package cn.deskreminder.mobile.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModelProvider
import cn.deskreminder.mobile.*
import cn.deskreminder.mobile.data.*
import cn.deskreminder.mobile.data.model.*
import cn.deskreminder.mobile.ui.*
import kotlinx.coroutines.android.awaitFrame
import java.time.LocalDateTime

/** Translucent, short-lived editor over the launcher. It never navigates to MainActivity. */
class WidgetQuickAddActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        val vm=ViewModelProvider(this,ViewModelProvider.AndroidViewModelFactory(application))[MainViewModel::class.java]
        val notes=intent.getBooleanExtra("notes",false)
        setContent{val data by vm.data.collectAsState();DeskTheme(data?.settings?.theme?:"light",data?.settings?.appStyle?:"fruit"){WidgetQuickAdd(notes,vm,{finish()})}}
    }
}

@Composable internal fun WidgetQuickAdd(notes:Boolean,vm:MainViewModel,onClose:()->Unit){
    val key=if(notes)"widget-quick-note"else"widget-quick-todo"
    var text by rememberSaveable{mutableStateOf(vm.repo.draft(key))}
    var content by rememberSaveable{mutableStateOf(vm.repo.draft("$key-content"))}
    var dueText by rememberSaveable{mutableStateOf(vm.repo.draft("$key-due"))}
    var error by remember{mutableStateOf("")};var saved by rememberSaveable{mutableStateOf(false)};val busy by vm.busy.collectAsState();val data by vm.data.collectAsState()
    val focus=remember{FocusRequester()};val keyboard=LocalSoftwareKeyboardController.current
    val focusManager=LocalFocusManager.current
    fun close(){if(!busy)onClose()}
    BackHandler{close()}
    LaunchedEffect(saved){if(!saved){awaitFrame();focus.requestFocus();keyboard?.show()}}
    LaunchedEffect(vm){vm.events.collect{event->if(event is UiEvent.Error)error=event.value}}
    fun save(){
        if(text.isBlank()){error=if(notes)"请填写随手记内容"else"请输入标题";return}
        val due=Dates.moment(dueText)
        vm.mutate(onDone={listOf(key,"$key-content","$key-due").forEach{vm.repo.saveDraft(it,"")};keyboard?.hide();focusManager.clearFocus();saved=true}){state->
            if(notes)state.notes.add(0,Note(text=text.trim(),createdAt=Dates.stamp()))
            else state.todos.add(Todo(title=text.trim(),content=content.trim(),type=if(due==null)"open"else"once",dueAt=due?.let(Dates::minute).orEmpty(),createdAt=Dates.stamp()))
        }
    }
    if(saved){DesktopOperationFeedback("已新增",onClose);return}
    Box(Modifier.fillMaxSize().clickable(interactionSource=remember{androidx.compose.foundation.interaction.MutableInteractionSource()},indication=null){close()}.windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(12.dp),contentAlignment=Alignment.BottomCenter){
        Surface(onClick={},modifier=Modifier.widthIn(max=480.dp).fillMaxWidth().heightIn(max=600.dp),shape=appShape(20.dp,30.dp),color=MaterialTheme.colorScheme.surface){
            Column{
                if(notes)Text("新增随手记",Modifier.padding(start=20.dp,end=20.dp,top=20.dp,bottom=12.dp),style=MaterialTheme.typography.titleLarge)else Spacer(Modifier.height(20.dp))
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    AppTextField(text,{text=it;error="";vm.repo.saveDraft(key,it)},label={Text(if(notes)"随手记内容"else"标题 · 必填")},modifier=Modifier.fillMaxWidth().focusRequester(focus),minLines=if(notes)3 else 1,maxLines=if(notes)8 else 3,shape=RoundedCornerShape(12.dp))
                    if(!notes){
                        AppTextField(content,{content=it;vm.repo.saveDraft("$key-content",it)},label={Text("内容 · 选填")},modifier=Modifier.fillMaxWidth(),maxLines=4,shape=RoundedCornerShape(12.dp))
                        if(dueText.isBlank())SelectionRow("计划完成","不设置",Icons.Rounded.Schedule,{dueText=Dates.minute(LocalDateTime.now().plusHours(1));vm.repo.saveDraft("$key-due",dueText)})
                        else{DateTimeField("计划完成",Dates.moment(dueText)?:LocalDateTime.now(),{dueText=Dates.minute(it);vm.repo.saveDraft("$key-due",dueText)});TextButton({dueText="";vm.repo.saveDraft("$key-due","")}){Text("移除时间")}}
                        if(dueText.isNotBlank()&&!vm.app.reminders.notificationsAllowed())Text("尚未允许通知，此事项会保存；可在应用的通知与后台设置中开启提醒。",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                    }
                    if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
                }
                Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){Secondary("取消",{close()},Modifier.weight(1f),!busy);Primary(if(busy)"保存中…"else"保存",{save()},Modifier.weight(1.5f),!busy&&data!=null)}
            }
        }
    }
}
