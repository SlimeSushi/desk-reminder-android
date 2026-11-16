package cn.deskreminder.mobile.data.cloud

import android.app.job.*
import android.content.ComponentName
import android.content.Context
import cn.deskreminder.mobile.DeskApplication
import cn.deskreminder.mobile.data.*
import cn.deskreminder.mobile.data.model.AppData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import java.time.Instant

data class MergePreview(val localTasks:Int,val localNotes:Int,val tasks:Int,val notes:Int,val conflicts:Int=0)
data class CloudUiState(val status:String="尚未连接坚果云",val connected:Boolean=false,val busy:Boolean=false,val error:Boolean=false,val automatic:Boolean=false,val lastSuccess:String="",val conflictCount:Int=0,val preview:MergePreview?=null)

class CloudSync(private val context:Context,private val repo:DataRepository){
 private val vault=CloudVault(context)
 private val mutable=MutableStateFlow(CloudUiState());val state=mutable.asStateFlow()
 val backupStatus=MutableStateFlow("")
 private val mutableUploadingBackup=MutableStateFlow<String?>(null)
 val uploadingBackup=mutableUploadingBackup.asStateFlow()
 private val mutableHistory=MutableStateFlow<List<CloudBackup>>(emptyList());val history=mutableHistory.asStateFlow()
 @Volatile var profile:CloudProfile=CloudProfile();private set
 @Volatile private var connected=false
 @Volatile private var editing=false
 private val gate=Mutex();private var active:Job?=null;private var foreground:Job?=null
 private var pending:SyncTracking?=null;private var previewHash:String?=null
 internal var persistProfile:(CloudProfile)->Unit={vault.save(it)}
 internal var transport:()->NutstoreTransport={NutstoreTransport()}
 private var changedAt=0L;private var lastAttempt=0L;private var nextAttempt=0L
 private var rateLimitedUntil=0L
 init{
  NutstoreRequestBudget.configure(java.io.File(context.noBackupFilesDir,"nutstore-request-budget.txt"))
  runCatching{profile=vault.load();connected=vault.exists;if(connected)NutstoreTransport.validate(profile);if(profile.protocolVersion!=3)profile.joined=false;message(when{!connected->"尚未连接坚果云";profile.joined->"已连接坚果云";else->"已连接，待首次同步"});if(connected)mutableHistory.value=CloudBackups.cached(context,NutstoreTransport.target(profile))}.onFailure{connected=false;message("本机连接配置无法读取，请重新填写",true)}
  repo.cloudCapture={data->if(connected)runCatching{data.syncTracking=capture(data)}.onFailure{message("同步状态暂未更新，本机修改仍会保存，请校准时间或检查云端数据",true)}}
  repo.onBusinessChanged={if(connected){changedAt=System.currentTimeMillis();if(!state.value.busy)message("本机修改待同步");schedule(true)}}
  schedule();repo.scope.launch{repo.current();schedule()}
 }
 private fun message(text:String,error:Boolean=false){mutable.update{it.copy(status=text,error=error,connected=connected,automatic=profile.automatic,lastSuccess=profile.lastSuccessUtc,conflictCount=0)}}
 private fun backOff(error:Exception):Long{
  val limited=error.message.orEmpty().let{it.contains("次数")||it.contains("额度")}
  val deadline=System.currentTimeMillis()+if(limited)30*60_000 else 2*60_000
  nextAttempt=maxOf(nextAttempt,deadline)
  if(limited)rateLimitedUntil=deadline
  return if(limited)deadline else 0L
 }
 private fun fail(e:Exception){backOff(e);message(if(e is CloudConnectionException||e is IllegalArgumentException||e is IllegalStateException)e.message?:"同步未完成"else"操作未完成，本机内容已保留，请重试",true)}
 fun capture(data:AppData)=FullData.capture(data,profile.deviceId,NutstoreTransport.target(profile))
 internal fun currentTracking(data:AppData)=data.syncTracking?.takeIf{connected&&it.version==3&&it.targetKey==NutstoreTransport.target(profile)}
 internal fun hasPendingLocalChanges(data:AppData):Boolean{
  val tracking=currentTracking(data)?:return false
  val local=tracking.full?.takeIf{it.deviceId==profile.deviceId}?:return false
  val uploaded=tracking.fullHeads[profile.deviceId]
  return uploaded==null||FullData.compare(local,uploaded)!=0
 }
 private fun launch(work:suspend()->Unit){
  if(!gate.tryLock())return
  mutable.update{it.copy(busy=true)}
  val job=repo.scope.launch(start=CoroutineStart.LAZY){try{work()}catch(_:CancellationException){message("操作已取消，本机内容保留")}catch(e:Exception){fail(e)}}
  active=job
  job.invokeOnCompletion{mutable.update{it.copy(busy=false)};gate.unlock()}
  job.start()
 }
 fun checkAndSave(edited:CloudProfile)=launch{
  NutstoreTransport.validate(edited);message("正在检查坚果云读写…");val result=transport().check(edited);require(result.removed){"读写正常，但测试文件未清理：${result.remainingName}"}
  val same=connected&&NutstoreTransport.target(edited)==NutstoreTransport.target(profile)
  val next=edited.copy(checkedUtc=result.at,conditionalWrites=result.conditionalWrites)
  if(same){next.deviceId=profile.deviceId;next.joined=profile.joined;next.protocolVersion=profile.protocolVersion;next.automatic=profile.automatic;next.backupToCloud=profile.backupToCloud;next.lastSuccessUtc=profile.lastSuccessUtc}else{next.joined=false;next.automatic=false;mutableHistory.value=emptyList()}
  persistProfile(next);profile=next;connected=true;nextAttempt=0;schedule();message(if(profile.joined)"连接检查成功"else"已连接，待首次同步")
 }
 fun setAutomatic(value:Boolean)=launch{check(connected){"请先连接坚果云"};val next=profile.copy(automatic=value);persistProfile(next);profile=next;schedule();lastAttempt=0;message(if(value)if(profile.joined)"自动同步已开启"else"自动同步已开启，请先完成首次同步"else"自动同步已关闭")}
 fun setBackupCloud(value:Boolean)=launch{check(connected){"请先连接坚果云"};val next=profile.copy(backupToCloud=value);persistProfile(next);profile=next;message("手动备份位置已更新")}
 fun cancel(){active?.cancel();pending=null;mutable.update{it.copy(preview=null)}}
 fun request(manual:Boolean=true)=launch{perform(manual)}
 private suspend fun perform(manual:Boolean){
  check(connected){"请先保存并检查坚果云连接"};if(editing){message("请先结束业务编辑，再同步");return}
  if(System.currentTimeMillis()<nextAttempt){if(manual)message("暂缓同步，请稍后重试",true);return}
  if(!manual&&(!profile.automatic||!profile.joined))return
  lastAttempt=System.currentTimeMillis();message("正在检查云端更新…")
  val state=repo.commit(syncing=true){it.syncTracking=capture(it)}.syncTracking!!
  val pulled=pull(transport(),profile,state)
  if(pulled.fullHeads.isEmpty()&&!profile.joined)throw IllegalStateException("请先在主电脑开始新版同步，再连接手机")
  if(editing){pending=pulled;message("更新已获取，等待编辑结束");return};apply(pulled,manual,false)
 }
 internal suspend fun pull(web:NutstoreTransport,p:CloudProfile,state:SyncTracking):SyncTracking{
  val next=CloudData.copy(state)
  web.list(p,"protocol-v3",true).filter{!it.directory&&it.name.matches(Regex("current-[a-f0-9]{32}\\.json"))}.forEach{file->
   val id=file.name.removePrefix("current-").removeSuffix(".json")
   if(!file.tag.isNullOrEmpty()&&next.fullTags[id]==file.tag&&next.fullHeads.containsKey(id))return@forEach
   val envelope=FullData.parse(web.read(p,"protocol-v3/${file.name}"));require(envelope.deviceId==id){"云端设备标识不一致"}
   next.fullHeads[id]?.let{require(FullData.compare(envelope,it)>=0){"云端版本发生回退，本机内容保留"}}
   next.fullHeads[id]=envelope;next.fullTags[id]=file.tag.orEmpty()
  };return next
 }
 private suspend fun apply(pulled:SyncTracking,manual:Boolean,confirmed:Boolean){
  val local=repo.current();val remote=pulled.fullHeads.values.maxWithOrNull(Comparator{a,b->FullData.compare(a,b)})
  if(!profile.joined&&!confirmed){if(!manual){message("请先手动完成首次同步");return};check(remote!=null){"请先由主电脑开始同步"};pending=pulled;previewHash=capture(local).full!!.payloadHash
   val preview=FullData.apply(local,CloudData.copy(pulled),remote);mutable.update{it.copy(status="请确认获取云端完整数据",preview=MergePreview(local.todos.size,local.notes.size,preview.todos.size,preview.notes.size))};return}
  val result=repo.commit(syncing=true){current->
   val state=capture(current);state.fullHeads=pulled.fullHeads;state.fullTags=pulled.fullTags
   if(confirmed)check(state.full!!.payloadHash==previewHash){"本机内容已变化，请重新同步"}
   val winner=if(!profile.joined&&remote!=null)remote else if(remote!=null&&FullData.compare(remote,state.full!!)>0)remote else state.full!!
   val next=FullData.apply(current,state,winner);current.todos=next.todos;current.notes=next.notes;current.noteTrash=next.noteTrash;current.reminderState=next.reminderState;current.syncTracking=next.syncTracking
  }
  val value=result.syncTracking!!.full!!;val known=result.syncTracking!!.fullHeads[profile.deviceId]
  if(value.deviceId==profile.deviceId&&(known==null||FullData.compare(value,known)!=0)){
   message("正在上传完整数据…");transport().writeVerified(profile,"protocol-v3/current-${profile.deviceId}.json",CloudData.gson.toJson(value))
   repo.commit(syncing=true){it.syncTracking=capture(it).apply{fullHeads[profile.deviceId]=value;fullTags.remove(profile.deviceId)}}
  }
  profile=profile.copy(joined=true,protocolVersion=3,lastSuccessUtc=Instant.now().toString());persistProfile(profile);schedule();nextAttempt=0
  changedAt=if(hasPendingLocalChanges(repo.current()))System.currentTimeMillis()else 0
  message(if(changedAt>0)"本机有后续修改待同步"else"已同步");pumpBackups()
 }
 fun confirmMerge(accept:Boolean){val batch=pending;pending=null;mutable.update{it.copy(preview=null)};if(!accept){message("首次同步已取消，本机内容保持");return};if(batch!=null)launch{apply(batch,true,true)}}
 fun setEditing(value:Boolean){editing=value;if(!value&&pending!=null&&state.value.preview==null&&!state.value.busy){val batch=pending!!;pending=null;launch{apply(batch,true,false)}}}
 fun resolve(key:String,index:Int){message("新版使用完整数据更新，无需逐条选择")}
 suspend fun queueBackup(info:BackupInfo){
  if(!connected||!profile.backupToCloud)return
  repo.commit(syncing=true){it.syncTracking=capture(it).apply{if(info.file.name !in backupQueue)backupQueue.add(info.file.name)}}
  backupStatus.value="本机已备份，等待上传";if(!state.value.busy)launch{pumpBackups()};schedule(true)
 }
 fun retryBackup(info:BackupInfo)=launch{pumpBackups(info.file.name)}

 private suspend fun pumpBackups(onlyBackup:String?=null){
  val tracking=currentTracking(repo.current())?:return
  val now=System.currentTimeMillis()
  if(onlyBackup==null&&now<nextAttempt)return
  val retryAfter=readyBackupAt(tracking)?:return
  if(now<retryAfter){
   if(onlyBackup!=null)backupStatus.value="坚果云访问受限，请稍后重试上传"
   return
  }
  val names=if(onlyBackup==null)tracking.backupQueue.toList()else listOf(onlyBackup).filter{it in tracking.backupQueue}
  var saved=false
  for(name in names){
   mutableUploadingBackup.value=name
   var verifiedUpload=false
   try{
    val info=repo.listBackups().firstOrNull{it.file.name==name}
     ?:throw IllegalStateException("待上传的本机手动备份不存在")
    val uploaded=CloudBackups.upload(transport(),profile,repo,info)
    verifiedUpload=true
    val history=listOf(uploaded)+mutableHistory.value.filter{it.id!=uploaded.id}
    mutableHistory.value=history
    CloudBackups.store(context,NutstoreTransport.target(profile),history)
    repo.commit(syncing=true){data->currentTracking(data)?.apply{
     backupQueue.remove(name)
     backupUploadFailures?.remove(name)
     if(backupUploadFailures.isNullOrEmpty())backupUploadFailures=null
    }}
    saved=true
   }catch(cancelled:CancellationException){
    throw cancelled
   }catch(error:Exception){
    val deadline=backOff(error)
    if(!verifiedUpload){
     repo.commit(syncing=true){data->currentTracking(data)?.apply{
      if(name in backupQueue){
       val failures=backupUploadFailures?:mutableMapOf<String,Long>().also{backupUploadFailures=it}
       failures[name]=deadline
      }
     }}
    }
    backupStatus.value=if(verifiedUpload)"云端备份已保存，本机状态待更新"else"上传失败，本机备份已保留"
    return
   }finally{
    mutableUploadingBackup.value=null
   }
  }
  if(saved)backupStatus.value="历史备份已保存到本机及云端"
 }
 fun refreshHistory()=launch{check(connected){"请先连接坚果云"};mutableHistory.value=CloudBackups.pull(context,transport(),profile)}
 suspend fun readBackup(entry:CloudBackup):String=CloudBackups.read(transport(),profile,entry)
 private fun readyBackupAt(tracking:SyncTracking):Long?=tracking.backupQueue.maxOfOrNull { name ->
  maxOf(rateLimitedUntil,tracking.backupUploadFailures?.get(name)?:0L)
 }
 fun setForeground(value:Boolean){foreground?.cancel();foreground=null;if(value){foreground=repo.scope.launch{if(profile.automatic&&profile.joined)request(false);while(isActive){delay(1000);val now=System.currentTimeMillis();if(!state.value.busy&&connected&&now>=nextAttempt){val ready=currentTracking(repo.current())?.let(::readyBackupAt);if(ready!=null&&now>=ready)this@CloudSync.launch{pumpBackups()}else if(profile.automatic&&profile.joined&&(now-lastAttempt>=60_000||changedAt>0&&now-changedAt>=1200))request(false)}}}}}
 val hasPendingBackups:Boolean get()=repo.data.value?.let(::currentTracking)?.backupQueue?.isNotEmpty()==true
 fun schedule(immediate:Boolean=false){
  val scheduler=context.getSystemService(JobScheduler::class.java)?:return
  if(!connected){scheduler.cancel(JOB);scheduler.cancel(JOB+1);return}
  val automatic=profile.automatic&&profile.joined
  if(!automatic){scheduler.cancel(JOB);if(!hasPendingBackups){scheduler.cancel(JOB+1);return}}
  val oneOff=immediate||!automatic
  val builder=JobInfo.Builder(if(oneOff)JOB+1 else JOB,ComponentName(context,NutstoreSyncJobService::class.java)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
  if(oneOff){
   val ready=repo.data.value?.let(::currentTracking)?.let(::readyBackupAt)?:0L
   builder.setMinimumLatency(maxOf(1200,maxOf(nextAttempt,ready)-System.currentTimeMillis()))
  }else builder.setPeriodic(15*60_000L)
  scheduler.schedule(builder.build())
 }
 suspend fun background(){if(!state.value.busy&&connected&&gate.tryLock()){active=currentCoroutineContext()[Job];mutable.update{it.copy(busy=true)};try{pumpBackups();if(profile.automatic&&profile.joined)perform(false)}catch(e:Exception){if(e is CancellationException)message("操作已取消，本机记录保留")else fail(e)}finally{mutable.update{it.copy(busy=false)};gate.unlock()}}}
 companion object{const val JOB=42017}
}
class NutstoreSyncJobService:JobService(){private var running:Job?=null;override fun onStartJob(params:JobParameters):Boolean{val app=application as DeskApplication;running=app.repository.scope.launch{try{app.cloud.background()}finally{jobFinished(params,app.cloud.hasPendingBackups)}};return true};override fun onStopJob(params:JobParameters):Boolean{running?.cancel();return true}}
