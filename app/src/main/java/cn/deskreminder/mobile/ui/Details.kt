package cn.deskreminder.mobile.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import cn.deskreminder.mobile.MainViewModel
import cn.deskreminder.mobile.data.*
import cn.deskreminder.mobile.data.model.*

@Composable fun TaskDetails(item:Occurrence,vm:MainViewModel,onBack:()->Unit,onEdit:()->Unit,onPostpone:()->Unit,onRemove:()->Unit,onToggle:()->Unit,onActual:()->Unit,onResume:()->Unit){
    val data by vm.data.collectAsState()
    Page("待办详情",onBack,actions={ActionIcon(Icons.Rounded.DeleteOutline,"中止或删除",onRemove)},bottom={BottomActionBar{
        if(item.stopped)Primary("恢复待办",onResume,Modifier.weight(1f))else if(!item.completed&&!item.ended)Secondary(if(item.due==null)"补设时间"else"延期",onPostpone,Modifier.weight(1f))
        Primary("编辑",onEdit,Modifier.weight(1f))
    }}){padding->Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
        Row(verticalAlignment=Alignment.Top){SelectionContainer(Modifier.weight(1f)){Text(item.todo.title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)};Spacer(Modifier.width(8.dp));StatusLabel(item)}
        if(item.todo.content.isNotBlank())Section("内容"){SelectionContainer{Text(item.todo.content,lineHeight=26.sp)}}
        if(item.stopped){Section("中止记录"){item.todo.stopRecords.forEach{entry->Text(Dates.display(Dates.moment(entry.stoppedAt)),fontWeight=FontWeight.Medium);if(entry.reason.isNotBlank())SelectionContainer{Text(entry.reason)};entry.resumedAt?.let{Text("恢复于 ${Dates.display(Dates.moment(it))}",fontSize=14.sp)}}}}
        if(item.due!=null||item.completed)Section("时间"){
            item.due?.let{Text("计划完成",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(Dates.display(it))}
            if(item.completed){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("实际完成",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(item.actual?.let{Dates.display(it)}?:"时间未记录")};ActionIcon(Icons.Rounded.Edit,"修改实际完成时间",onActual)}}
        }
        if(item.todo.type=="weekly")Section("每周计划"){
            Text("周${"一二三四五六日"[(item.todo.weekday-1).coerceIn(0,6)]} ${item.todo.weeklyTime}")
            if(item.todo.weeklyStartDate.isNotEmpty()||item.todo.weeklyEndDate.isNotEmpty())Text("${item.todo.weeklyStartDate.ifBlank{"未设开始"}} — ${item.todo.weeklyEndDate.ifBlank{"未设结束"}}",fontSize=14.sp)
        }
        val timeline=Schedule.timeline(item)
        if(timeline.isNotEmpty())Section(if(timeline.any{it.edit})"时间记录 · ${timeline.size}次"else"延期记录 · ${timeline.size}次"){
            timeline.forEachIndexed{index,step->
                if(index>0)HorizontalDivider()
                Text("${Dates.display(step.from)} →\n${Dates.display(step.to)}",lineHeight=24.sp)
                Text("${step.changed?.let{Dates.display(it)}?:"操作时间未记录"} · ${if(step.edit)"计划调整"else if(step.all)"全量延期"else"本次延期"}",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(step.reason.isNotBlank())Text("延期原因  ${step.reason}",fontSize=14.sp)
            }
        }
        if(item.due!=null&&!item.completed&&!item.stopped&&!item.ended){val rule=Schedule.rule(item,data?.settings?:MobileSettings());Section("提醒"){
            Text(if(rule.enabled)"已开启"else"已关闭");if(rule.enabled){Text("${durationLabel(rule.advanceMinutes,true)} · ${durationLabel(rule.repeatMinutes)}",fontSize=14.sp)}
        }}
        if(!item.stopped&&!item.ended)Secondary(if(item.completed)"取消完成"else"标记完成",onToggle,Modifier.fillMaxWidth())
    }}
}
@Composable fun NoteDetails(note:Note,vm:MainViewModel,onBack:()->Unit,onEdit:()->Unit,onTrash:()->Unit){
    val clipboard=LocalClipboardManager.current
    Page("随手记",onBack,actions={ActionIcon(Icons.Rounded.ContentCopy,"复制内容",{clipboard.setText(AnnotatedString(note.text));vm.events.tryEmit(cn.deskreminder.mobile.UiEvent.Message("已复制"))});ActionIcon(if(note.pinned)Icons.Rounded.PushPin else Icons.Rounded.PushPin,"置顶或取消置顶",{vm.pin(note)});ActionIcon(Icons.Rounded.DeleteOutline,"移入废纸篓",onTrash)},bottom={BottomActionBar{Primary("编辑",onEdit,Modifier.weight(1f))}}){padding->Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
        SelectionContainer{Text(note.text,lineHeight=28.sp)}
        if(note.createdAt.isNotEmpty())Text("创建于 ${Dates.display(Dates.moment(note.createdAt))}",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }}
}
@Composable fun TrashPage(data:AppData,vm:MainViewModel,onBack:()->Unit){
    var deleting by remember{mutableStateOf<TrashedNote?>(null)};var clearing by remember{mutableStateOf(false)}
    Page("废纸篓 ${data.noteTrash.size}",onBack,actions={TextButton({clearing=true},enabled=data.noteTrash.isNotEmpty()){Text("清空")}}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            if(data.noteTrash.isEmpty())item{EmptyState("废纸篓是空的")}
            items(data.noteTrash.sortedByDescending{it.deletedAt},key={it.note.id}){entry->Section(if(entry.note.pinned)"原置顶随手记"else"随手记"){
                Text(entry.note.text,maxLines=5,overflow=TextOverflow.Ellipsis)
                Text("删除于 ${Dates.display(Dates.moment(entry.deletedAt))}",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Secondary("恢复",{vm.restoreNote(entry.note.id)},Modifier.weight(1f));TextButton({deleting=entry},Modifier.weight(1f)){Text("彻底删除",color=MaterialTheme.colorScheme.error)}}
            }}
        }
    }
    deleting?.let{entry->MessageDialog("彻底删除随手记？",entry.note.text.take(180)+"\n\n删除后无法从废纸篓恢复。","彻底删除",true,{deleting=null}){vm.mutate("已彻底删除",{deleting=null}){it.noteTrash.removeAll{value->value.note.id==entry.note.id}}}}
    if(clearing)MessageDialog("清空废纸篓？","将彻底删除${data.noteTrash.size}条随手记。","确认清空",true,{clearing=false}){val ids=data.noteTrash.map{it.note.id}.toSet();vm.mutate("废纸篓已清空",{clearing=false}){it.noteTrash.removeAll{value->value.note.id in ids}}}
}
