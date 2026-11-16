package cn.deskreminder.mobile.data

import android.content.Context
import android.util.AtomicFile
import androidx.room.Room
import cn.deskreminder.mobile.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

data class BackupInfo(val file:File,val at:LocalDateTime,val reason:String,val todos:Int,val notes:Int,val trash:Int) {
    val label:String get()=when(reason){"manual"->"手动";"before-import"->"恢复前";"before-change"->"修改前";"before-sync"->"同步前";else->"自动"}
    val bytes:Long get()=file.length()
}
class DataRepository(context:Context) {
    private val app=context.applicationContext
    private val db=Room.databaseBuilder(app,LocalDatabase::class.java,"deskreminder.db").build()
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutex=Mutex()
    private val _data=MutableStateFlow<AppData?>(null)
    val data:StateFlow<AppData?> = _data.asStateFlow()
    val fatal=MutableStateFlow<String?>(null)
    val backupWarning=MutableStateFlow<String?>(null)
    private val folder=File(app.filesDir,"backups")
    private var maintainedDay:LocalDate?=null
    var onChanged:(()->Unit)?=null
    var onBusinessChanged:(()->Unit)?=null
    var cloudCapture:((AppData)->Unit)?=null
    init { load() }
    private fun load(){scope.launch{runCatching{val row=db.state().load();val state=if(row==null)AppData()else BackupCodec.decodeState(row.payload);BackupCodec.validate(state);_data.value=state;cleanup(state.settings.backupRetentionDays)}.onFailure{fatal.value="记录无法读取，原数据已保留。可导入一份可用备份。"}}}
    suspend fun current():AppData=data.filterNotNull().first()
    private fun signature(value:AppData):String = BackupCodec.gson.toJson(BackupCodec.business(value))
    suspend fun commit(syncing:Boolean=false,change:(AppData)->Unit):AppData=withContext(Dispatchers.IO){mutex.withLock {
        check(fatal.value==null){"请先恢复可用备份"}
        val before=current();val next=BackupCodec.copy(before);change(next)
        if(!syncing)cloudCapture?.invoke(next)
        BackupCodec.validate(next)
        if(BackupCodec.gson.toJson(before)==BackupCodec.gson.toJson(next))return@withLock before
        val businessChanged=signature(before)!=signature(next)
        if(syncing&&businessChanged)writeBackup(before,"before-sync")
        next.revision=before.revision+1
        if(businessChanged&&listBackups().isEmpty()&&(before.todos.isNotEmpty()||before.notes.isNotEmpty()||before.noteTrash.isNotEmpty()))runCatching{writeBackup(before,"before-change")}.onFailure{backupWarning.value="原内容保护备份未写入，请检查手机存储空间。"}
        db.runInTransaction{db.state().save(StateRow().apply{payload=BackupCodec.gson.toJson(next)})}
        _data.value=next
        if(businessChanged){
            runCatching{writeBackup(next,"auto")}.onFailure{backupWarning.value="修改已保存，但自动备份未完成：${it.localizedMessage.orEmpty()}"}
        }
        runCatching{cleanup(next.settings.backupRetentionDays)}.onFailure{backupWarning.value="旧备份清理未完成，文件已保留。"}
        onChanged?.invoke();if(businessChanged&&!syncing)onBusinessChanged?.invoke();next
    }}
    private fun writeBackup(value:AppData,reason:String):BackupInfo {
        folder.mkdirs();val now=LocalDateTime.now()
        val name="backup-${now.format(DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS"))}-$reason-t${value.todos.size}-n${value.notes.size}-r${value.noteTrash.size}-${UUID.randomUUID().toString().take(8)}.json"
        val file=File(folder,name);writeAtomic(file,BackupCodec.document(value,reason));return BackupInfo(file,now,reason,value.todos.size,value.notes.size,value.noteTrash.size)
    }
    suspend fun manualBackup():BackupInfo=withContext(Dispatchers.IO){mutex.withLock{writeBackup(current(),"manual")}}
    fun listBackups():List<BackupInfo> {
        val pattern=Regex("backup-(\\d{8}-\\d{6}-\\d{3})-(auto|manual|before-import|before-change|before-sync)-t(\\d+)-n(\\d+)-r(\\d+)-[a-f0-9-]+\\.json")
        return folder.listFiles().orEmpty().filter{it.isFile}.mapNotNull{file->
            val match=pattern.matchEntire(file.name)?:return@mapNotNull null
            runCatching{BackupInfo(file,LocalDateTime.parse(match.groupValues[1],DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS")),match.groupValues[2],match.groupValues[3].toInt(),match.groupValues[4].toInt(),match.groupValues[5].toInt())}.getOrNull()
        }.sortedByDescending{it.at}
    }
    fun cleanupCandidates(days:Int):List<BackupInfo> {
        if(days<=0)return emptyList();val autos=listBackups().filter{it.reason=="auto"};val keep=autos.firstOrNull()?.file?.name
        val oldest=LocalDate.now().minusDays((days-1).toLong())
        return autos.filter{it.file.name!=keep&&it.at.toLocalDate()<oldest}
    }
    private fun cleanup(days:Int){cleanupCandidates(days).forEach{info->require(info.file.canonicalFile.parentFile==folder.canonicalFile){"备份路径异常"};check(info.file.delete()){ "旧备份清理未完成" }}}
    suspend fun maintainBackups()=withContext(Dispatchers.IO){mutex.withLock{val today=LocalDate.now();if(maintainedDay!=today){runCatching{cleanup(current().settings.backupRetentionDays)}.onFailure{backupWarning.value="旧备份清理未完成，文件已保留。"};maintainedDay=today}}}
    suspend fun setRetention(days:Int,approvedNames:Set<String>?=null){
        require(days>=0){"请输入有效天数"}
        // The UI confirms the current candidate set before applying a shorter retention policy.
        val candidates=cleanupCandidates(days)
        require(candidates.isEmpty()||approvedNames!=null&&candidates.all{it.file.name in approvedNames}){"清理范围已变化，请重新确认"}
        commit{it.settings.backupRetentionDays=days}
    }
    suspend fun restore(imported:ImportedData):AppData=withContext(Dispatchers.IO){mutex.withLock {
        val before=_data.value
        if(before!=null)writeBackup(before,"before-import")else{
            db.state().load()?.payload?.let{raw->File(app.filesDir,"recovery").mkdirs();writeAtomic(File(app.filesDir,"recovery/unreadable-${System.currentTimeMillis()}.json"),raw)}
        }
        val next=BackupCodec.copy(imported.data)
        if(before!=null){
            next.settings=before.settings
            next.syncTracking=before.syncTracking
            val local=before.todos.associateBy{it.id}
            next.todos.forEach{task->task.reminder=local[task.id]?.reminder?.let{BackupCodec.gson.fromJson(BackupCodec.gson.toJson(it),ReminderRule::class.java)}}
        }
        next.reminderState.clear();cloudCapture?.invoke(next);BackupCodec.validate(next);next.revision=(before?.revision?:0)+1
        db.runInTransaction{db.state().save(StateRow().apply{payload=BackupCodec.gson.toJson(next)})}
        fatal.value=null;_data.value=next;onChanged?.invoke();onBusinessChanged?.invoke();next
    }}
    suspend fun exportCurrent():String=BackupCodec.document(current(),"manual")
    fun readSaved(info:BackupInfo):String {require(info.file.canonicalFile.parentFile==folder.canonicalFile){"备份路径异常"};require(info.file.length()<=50*1024*1024){"备份过大"};return info.file.readText()}
    fun saveDraft(key:String,value:String){app.getSharedPreferences("drafts",Context.MODE_PRIVATE).edit().putString(key,value).apply()}
    fun draft(key:String):String=app.getSharedPreferences("drafts",Context.MODE_PRIVATE).getString(key,"").orEmpty()
    companion object {
        fun writeAtomic(file:File,text:String){
            file.parentFile?.mkdirs();val atomic=AtomicFile(file);val stream=atomic.startWrite()
            try{stream.write(text.toByteArray(Charsets.UTF_8));atomic.finishWrite(stream)}catch(error:Throwable){atomic.failWrite(stream);throw error}
        }
    }
}
