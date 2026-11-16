package cn.deskreminder.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.deskreminder.mobile.data.*
import cn.deskreminder.mobile.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*
import java.util.UUID

sealed interface UiEvent { data class Message(val value:String):UiEvent;data class Error(val value:String):UiEvent;data object AskNotifications:UiEvent }
class MainViewModel(application:Application):AndroidViewModel(application){
    val app=application as DeskApplication
    val repo=app.repository
    val data=repo.data
    val busy=MutableStateFlow(false)
    val events=MutableSharedFlow<UiEvent>(extraBufferCapacity=16)
    val importPreview=androidx.compose.runtime.mutableStateOf<ImportedData?>(null)
    fun operation(message:String="",onDone:()->Unit={},work:suspend()->Unit){
        if(busy.value)return;busy.value=true
        viewModelScope.launch{try{withContext(Dispatchers.IO){work()};busy.value=false;onDone();if(message.isNotEmpty())events.emit(UiEvent.Message(message))}catch(error:Exception){busy.value=false;events.emit(UiEvent.Error(error.localizedMessage?:"操作未完成，请重试"))}}
    }
    fun mutate(message:String="",onDone:()->Unit={},change:(AppData)->Unit)=operation(message,onDone){repo.commit(change=change)}
    fun saveTask(edited:Todo,original:String?,actual:LocalDateTime?,week:String?,onDone:()->Unit){
        mutate("已保存",{repo.saveDraft("todo:${if(original==null)"new"else edited.id}","");onDone();if(edited.type!="open"&&!edited.done&&!edited.stopped)events.tryEmit(UiEvent.AskNotifications)}){state->
            val index=state.todos.indexOfFirst{it.id==edited.id};val old=state.todos.getOrNull(index)
            require(if(original==null)old==null else old!=null&&BackupCodec.gson.toJson(old)==original){"事项已被通知或小组件更新。当前输入已保留，请返回后重新打开。"}
            val task=BackupCodec.copy(edited)
            if(old==null){task.createdAt=Dates.stamp();state.todos.add(task)}else{
                if(old.type=="weekly"&&task.type!="weekly"&&(old.completedWeeks.isNotEmpty()||old.doneWeek!=null)){
                    val preserved=BackupCodec.copy(old);Schedule.freezeCompletedPlans(preserved);preserved.archived=true;preserved.id=UUID.randomUUID().toString()
                    state.todos[index]=preserved;task.completedWeeks.clear();task.weeklyCompletions.clear();task.weeklySnapshots.clear();task.weeklyPostponements.clear();task.doneWeek=null;task.done=false;task.completedAt=null;state.todos.add(task)
                }else{
                    if(old.type=="weekly"&&task.type=="weekly"&&(old.weekday!=task.weekday||old.weeklyTime!=task.weeklyTime||old.weeklyStartDate!=task.weeklyStartDate||old.weeklyEndDate!=task.weeklyEndDate)){
                        val preserved=BackupCodec.copy(old);Schedule.freezeCompletedPlans(preserved);task.weeklySnapshots=preserved.weeklySnapshots
                    }
                    if(old.type=="once"&&task.type=="once"&&old.dueAt!=task.dueAt)task.postponements.add(Postponement(old.dueAt,task.dueAt,Dates.stamp(),"edit"))
                    state.todos[index]=task
                }
            }
            if(actual!=null)Schedule.changeActual(task,week,actual)
        }
    }
    fun complete(item:Occurrence,value:Boolean,onDone:()->Unit={})=mutate(if(value)"已完成"else"已恢复待办",onDone){state->
        val task=state.todos.firstOrNull{it.id==item.todo.id}?:error("事项已不存在")
        val current=Schedule.occurrence(task,item.week)?:error("计划已变化，请重新查看")
        require(current.week==item.week&&current.signature==item.signature&&current.completed!=value){"事项状态已更新，请重新查看"};Schedule.complete(task,item.week,value);state.reminderState.remove(item.key)
    }
    fun postpone(item:Occurrence,newDue:LocalDateTime,all:Boolean,reason:String,onDone:()->Unit)=mutate("已延期",onDone){state->val task=state.todos.firstOrNull{it.id==item.todo.id}?:error("事项已不存在");require(Schedule.occurrence(task,item.week)?.due==item.due){"计划时间已变化，请重新打开"};Schedule.postpone(task,item.week,newDue,all,reason=reason);state.reminderState.remove(item.key)}
    fun changeActual(item:Occurrence,time:LocalDateTime,onDone:()->Unit)=mutate("完成时间已更新",onDone){state->val task=state.todos.firstOrNull{it.id==item.todo.id}?:error("事项已不存在");Schedule.changeActual(task,item.week,time)}
    fun remove(item:Occurrence,stop:Boolean,reason:String,onDone:()->Unit)=mutate(if(stop)"任务已中止"else"任务已删除",onDone){state->val task=state.todos.firstOrNull{it.id==item.todo.id}?:error("事项已不存在");if(stop)Schedule.stop(task,reason)else state.todos.remove(task);state.reminderState.keys.filter{it.startsWith(task.id+":")}.forEach{state.reminderState.remove(it)}}
    fun resume(item:Occurrence,onDone:()->Unit={})=mutate("已恢复待办",onDone){state->val task=state.todos.firstOrNull{it.id==item.todo.id}?:error("事项已不存在");require(task.stopped){"事项已不在中止状态，请重新查看"};Schedule.resume(task);state.reminderState.keys.filter{it.startsWith(task.id+":")}.forEach{state.reminderState.remove(it)}}
    fun saveNote(note:Note,original:String?,onDone:()->Unit)=mutate("已保存",{repo.saveDraft("note:${if(original==null)"new"else note.id}","");onDone()}){state->
        val index=state.notes.indexOfFirst{it.id==note.id};val old=state.notes.getOrNull(index)
        require(if(original==null)old==null else old!=null&&BackupCodec.gson.toJson(old)==original){"随手记内容已变化，请保留草稿后重新打开"}
        val saved=BackupCodec.copy(note).apply{text=text.trim();if(createdAt.isBlank())createdAt=Dates.stamp()}
        if(old==null)state.notes.add(0,saved)else state.notes[index]=saved
    }
    fun pin(note:Note)=mutate(if(note.pinned)"已取消置顶"else"已置顶"){state->state.notes.firstOrNull{it.id==note.id}?.let{it.pinned=!it.pinned}}
    fun moveNote(note:Note,delta:Int)=mutate{state->val group=state.notes.filter{it.pinned==note.pinned};val index=group.indexOfFirst{it.id==note.id};val target=group.getOrNull(index+delta)?:return@mutate;val first=state.notes.indexOfFirst{it.id==note.id};val second=state.notes.indexOfFirst{it.id==target.id};java.util.Collections.swap(state.notes,first,second)}
    fun reorderNote(note:Note,targetId:String)=mutate{state->val first=state.notes.indexOfFirst{it.id==note.id};val target=state.notes.indexOfFirst{it.id==targetId};if(first<0||target<0||state.notes[first].pinned!=state.notes[target].pinned)return@mutate;val value=state.notes.removeAt(first);state.notes.add(target.coerceAtMost(state.notes.size),value)}
    fun trash(note:Note,onDone:()->Unit)=mutate("已移入废纸篓",onDone){state->val index=state.notes.indexOfFirst{it.id==note.id};require(index>=0){"随手记已不存在"};state.noteTrash.add(TrashedNote(state.notes.removeAt(index),Dates.stamp(),index))}
    fun restoreNote(id:String)=mutate("已恢复随手记"){state->val trash=state.noteTrash.firstOrNull{it.note.id==id}?:error("记录已不存在");state.notes.add(trash.originalIndex.coerceIn(0,state.notes.size),trash.note);state.noteTrash.remove(trash)}
}
