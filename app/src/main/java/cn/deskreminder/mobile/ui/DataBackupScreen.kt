@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.deskreminder.mobile.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import cn.deskreminder.mobile.MainViewModel
import cn.deskreminder.mobile.data.*
import cn.deskreminder.mobile.data.cloud.*
import cn.deskreminder.mobile.data.model.*
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter

enum class BackupUploadState(val label:String){
 WAITING("本机已备份 · 等待上传"),
 UPLOADING("本机已备份 · 正在上传"),
 FAILED("上传失败 · 本机备份已保留"),
 SAVED("已保存到云端")
}

data class DataBackupRow(val id:String,val at:String,val kind:String,val location:String,val todos:Int,val notes:Int,val trash:Int=0,val device:String="本机",val bytes:Long=0,val local:BackupInfo?=null,val cloud:CloudBackup?=null,val createdMs:Long=0,val uploadState:BackupUploadState?=null)

@Composable private fun BackupUploadFeedback(row:DataBackupRow,retryEnabled:Boolean,onRetry:(BackupInfo)->Unit){
 val status=row.uploadState?:return
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp)){
  Text(status.label,style=MaterialTheme.typography.bodySmall,
   color=if(status==BackupUploadState.FAILED)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
  if(status==BackupUploadState.FAILED&&row.local!=null){
   TextButton(onClick={onRetry(row.local)},enabled=retryEnabled,modifier=Modifier.heightIn(min=48.dp)){
    Text("重试上传")
   }
  }
 }
}

@Composable fun DataBackupScreen(data:AppData,vm:MainViewModel,onBack:()->Unit,onImport:()->Unit,onExport:()->Unit,onRestoreText:(String,String)->Unit){
 val context=LocalContext.current;val cloud=vm.app.cloud
 val state by cloud.state.collectAsState();val remote by cloud.history.collectAsState();val backupStatus by cloud.backupStatus.collectAsState();val busy by vm.busy.collectAsState()
 val uploadingBackup by cloud.uploadingBackup.collectAsState()
 var view by rememberSaveable{mutableStateOf("hub")};var tab by rememberSaveable{mutableIntStateOf(0)};var refresh by remember{mutableIntStateOf(0)}
 var rows by remember{mutableStateOf(emptyList<DataBackupRow>())};var selected by rememberSaveable{mutableStateOf<String?>(null)};var animating by remember{mutableStateOf(false)}
 LaunchedEffect(backupStatus){if(backupStatus.isNotEmpty())vm.events.tryEmit(cn.deskreminder.mobile.UiEvent.Message(backupStatus))}
 var retention by remember{mutableStateOf<Pair<Int,List<BackupInfo>>?>(null)}
 BackHandler(view!="hub"){view="hub"}
 LaunchedEffect(data.revision,remote,refresh,busy){if(!busy)rows=withContext(Dispatchers.IO){
  val local=vm.repo.listBackups().map{info->DataBackupRow(CloudData.hash(vm.repo.readSaved(info)),info.at.format(DateTimeFormatter.ofPattern("M月d日 HH:mm:ss")),if(info.reason=="before-sync")"替换前"else info.label,"本机",info.todos,info.notes,info.trash,bytes=info.bytes,local=info,createdMs=info.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())}.toMutableList()
  remote.forEach{entry->val index=local.indexOfFirst{it.id==entry.id};if(index>=0)local[index]=local[index].copy(location="本机 + 云端",cloud=entry)else local.add(DataBackupRow(entry.id,Instant.ofEpochMilli(entry.createdMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm:ss")),"手动","云端",entry.todos,entry.notes,entry.trash,if(entry.device==cloud.profile.deviceId)"当前设备"else"其他设备",cloud=entry,createdMs=entry.createdMs))};local.sortedByDescending{it.createdMs}
 }}
 LaunchedEffect(busy){if(!busy&&animating){animating=false;refresh++}}
 fun read(row:DataBackupRow,action:(String)->Unit){var text="";vm.operation(onDone={action(text)}){text=if(row.local!=null)vm.repo.readSaved(row.local)else cloud.readBackup(row.cloud!!)}}
 fun share(row:DataBackupRow){read(row){text->runCatching{context.startActivity(BackupSharing.prepare(context,text))}.onFailure{vm.events.tryEmit(cn.deskreminder.mobile.UiEvent.Error("无法打开文件分享"))}}}
 fun retain(days:Int){var candidates=emptyList<BackupInfo>();vm.operation(onDone={if(candidates.isNotEmpty())retention=days to candidates else refresh++}){candidates=vm.repo.cleanupCandidates(days);if(candidates.isEmpty())vm.repo.setRetention(days)}}
 val tracking=cloud.currentTracking(data)
 val displayRows=rows.map{row->
  val name=row.local?.file?.name
  if(name==null)row else row.copy(uploadState=when{
   row.cloud!=null->BackupUploadState.SAVED
   name==uploadingBackup->BackupUploadState.UPLOADING
   name !in tracking?.backupQueue.orEmpty()->null
   tracking?.backupUploadFailures?.containsKey(name)==true->BackupUploadState.FAILED
   else->BackupUploadState.WAITING
  })
 }
 val entry=displayRows.firstOrNull{it.id==selected}
 when(view){
  "config"->CloudConnectionContent(state,cloud.profile,emptyList(),{view="hub"},{cloud.checkAndSave(it)},{cloud.request()},{cloud.setAutomatic(it)},{cloud.cancel()},{cloud.confirmMerge(it)},{_,_->},{runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(NutstoreTransport.HELP))) }},configurationOnly=true)
  "policy"->DataBackupPolicy(data.settings.backupRetentionDays,state.connected,cloud.profile.backupToCloud,busy||state.busy,{view="hub"},{retain(it)},{cloud.setBackupCloud(it)})
  "detail"->Page("备份详情",{view="hub"}){padding->Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
   entry?.let{row->Section("备份信息"){
    listOf("创建时间" to row.at,"类型" to row.kind,"保存位置" to row.location,"来源设备" to row.device,"内容" to "${row.todos}项待办 · ${row.notes}张随手记","大小" to if(row.bytes>0)formatBytes(row.bytes)else"获取后显示").forEach{(label,value)->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(label,Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,Modifier.weight(2f))}}
    BackupUploadFeedback(row,!busy&&!state.busy,cloud::retryBackup)
    Secondary("分享备份",{share(row)},Modifier.fillMaxWidth(),!busy&&!state.busy);Primary("恢复备份",{read(row){onRestoreText(it,row.at+" · "+row.kind)}},Modifier.fillMaxWidth(),!busy&&!state.busy)
   }}
  }}
  else->DataBackupHub(state,displayRows,busy,animating,tab,selected,onBack,{tab=it;if(it==1&&state.connected)cloud.refreshHistory()},{selected=it},{cloud.request()},{cloud.setAutomatic(it)},{view="config"},{view="policy"},{cloud.refreshHistory()},onExport,onImport,
   {if(!busy&&!animating){tab=1;animating=true;var id:String?=null;vm.operation("本机备份已完成",{selected=id;refresh++;animating=false}){val start=System.currentTimeMillis();try{val backup=vm.repo.manualBackup();id=CloudData.hash(vm.repo.readSaved(backup));cloud.queueBackup(backup)}finally{delay((2000-(System.currentTimeMillis()-start)).coerceAtLeast(0))}}}},
   {view="detail"},{entry?.let{share(it)}},{entry?.let{row->read(row){onRestoreText(it,row.at+" · "+row.kind)}}},
   localChangesPending=cloud.hasPendingLocalChanges(data),onRetryBackup=cloud::retryBackup)
 }
 retention?.let{(days,items)->MessageDialog("清理过期自动备份？","应用保留${days}天，将清理本机${items.size}份超期自动备份。手动、保护及云端副本保留。","应用并清理",true,{retention=null}){vm.operation("保留规则已更新",{retention=null;refresh++}){vm.repo.setRetention(days,items.map{it.file.name}.toSet())}}}
 state.preview?.let{preview->AppAlertDialog(onDismissRequest={cloud.confirmMerge(false)},title={Text("获取云端完整数据？")},text={Text("待办 ${preview.localTasks} → ${preview.tasks}\n随手记 ${preview.localNotes} → ${preview.notes}\n\n将替换本机业务内容，先保存保护备份。本机外观和提醒保留。")},confirmButton={TextButton({cloud.confirmMerge(true)}){Text("获取并同步")}},dismissButton={TextButton({cloud.confirmMerge(false)}){Text("取消")}})}
}

@Composable fun DataBackupHub(state:CloudUiState,rows:List<DataBackupRow>,busy:Boolean,animating:Boolean,tab:Int,selected:String?,onBack:()->Unit,onTab:(Int)->Unit,onSelect:(String)->Unit,onSync:()->Unit,onAutomatic:(Boolean)->Unit,onConfig:()->Unit,onPolicy:()->Unit,onRefresh:()->Unit,onExport:()->Unit,onImport:()->Unit,onBackup:()->Unit,onDetail:()->Unit,onShare:()->Unit,onRestore:()->Unit,localChangesPending:Boolean=false,onRetryBackup:(BackupInfo)->Unit={}){
 var filter by rememberSaveable{mutableStateOf("all")};var more by remember{mutableStateOf(false)}
 val combinedBusy=busy||state.busy
 Page("数据备份",onBack,bottom={if(tab==1)Surface{FlowRow(Modifier.fillMaxWidth().testTag("history-actions").navigationBarsPadding().padding(horizontal=16.dp,vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Secondary("查看",onDetail,Modifier.weight(1f).widthIn(min=72.dp),selected!=null&&!combinedBusy&&!animating);Secondary("分享",onShare,Modifier.weight(1f).widthIn(min=72.dp),selected!=null&&!combinedBusy&&!animating);Secondary("恢复",onRestore,Modifier.weight(1f).widthIn(min=72.dp),selected!=null&&!combinedBusy&&!animating)}}}){padding->LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("data-backup-hub"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  item{Section(""){
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Icon(if(state.error)Icons.Rounded.CloudOff else if(state.status=="已同步")Icons.Rounded.CheckCircleOutline else Icons.Rounded.CloudQueue,null,Modifier.size(28.dp),tint=if(state.error)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary);Column(Modifier.weight(1f).padding(horizontal=12.dp)){
    val pendingTitle=localChangesPending&&state.status in listOf("已同步","本机修改待同步","本机有后续修改待同步")
    Text(if(pendingTitle)"待同步"else state.status,fontWeight=FontWeight.SemiBold)
    if(localChangesPending)Text("本机已保存 · 待同步",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(state.lastSuccess.isNotEmpty())Text("上次同步成功 · "+runCatching{Instant.parse(state.lastSuccess).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))}.getOrDefault(state.lastSuccess),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   };Box{IconButton({more=true}){Icon(Icons.Rounded.MoreHoriz,"更多数据操作")};DropdownMenu(more,{more=false}){DropdownMenuItem({Text("导出当前数据")},{more=false;onExport()});DropdownMenuItem({Text("导入备份")},{more=false;onImport()})}}}
   FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    Primary(if(state.connected)if(state.busy)"处理中…"else"立即同步"else"连接坚果云",if(state.connected)onSync else onConfig,Modifier.weight(1f).widthIn(min=120.dp),!combinedBusy&&!animating)
    Secondary(if(animating)"备份中…"else"立即备份",onBackup,Modifier.weight(1f).widthIn(min=120.dp),!busy&&!animating)
   }
  }}
  item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("同步","备份记录").forEachIndexed{i,label->FilterChip(selected=tab==i,onClick={onTab(i)},label={Text(label)},modifier=Modifier.weight(1f).heightIn(min=44.dp))}}}
  if(tab==0){
   item{Section(""){SwitchRow("自动同步",state.automatic,onAutomatic,enabled=state.connected&&!combinedBusy);HorizontalDivider();Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("同步方式");Text("最新完整数据",color=MaterialTheme.colorScheme.onSurfaceVariant)};HorizontalDivider();SettingRow("坚果云配置",if(state.connected)"已连接"else"未配置",onConfig)}}
   item{Text("本机自动备份已开启",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
  }else{
   item{Row(verticalAlignment=Alignment.CenterVertically){Text("历史备份 ${rows.size}份",Modifier.weight(1f),fontWeight=FontWeight.SemiBold);TextButton(onPolicy){Text("备份设置")}}}
   item{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("all" to "全部","local" to "本机","cloud" to "云端").forEach{(key,label)->FilterChip(filter==key,{filter=key},{Text(label)})};Spacer(Modifier.weight(1f));IconButton(onRefresh,enabled=state.connected&&!state.busy){Icon(Icons.Rounded.Refresh,"刷新备份记录")}}}
   if(animating)item("animation"){Box(Modifier.height(170.dp)){QuillAnimation()}}
   val filtered=rows.filter{filter=="all"||filter=="local"&&it.local!=null||filter=="cloud"&&it.cloud!=null}
   if(filtered.isEmpty())item{EmptyState(if(filter=="cloud"&&!state.connected)"连接坚果云后查看云端备份"else"还没有这类备份")}
   items(filtered,key={it.id}){entry->Surface(onClick={onSelect(entry.id)},enabled=!busy&&!animating,shape=appShape(14.dp,20.dp),color=if(selected==entry.id)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().testTag("history-${entry.id}")){
    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Text(entry.at,Modifier.weight(1f),fontWeight=FontWeight.Medium);Text(entry.kind,style=MaterialTheme.typography.labelSmall)}
     FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text("${entry.todos}项待办 · ${entry.notes}张随手记",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(entry.location,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
     BackupUploadFeedback(entry,!combinedBusy&&!animating,onRetryBackup)
    }
   }}

  }
 }}
}

@Composable fun DataBackupPolicy(days:Int,connected:Boolean,toCloud:Boolean,busy:Boolean,onBack:()->Unit,onRetention:(Int)->Unit,onLocation:(Boolean)->Unit){
 var custom by rememberSaveable(days){mutableStateOf((if(days>0)days else 30).toString())};var error by remember{mutableStateOf("")}
 Page("备份设置",onBack){padding->Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
  Section("本机自动备份保留"){
   FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(0 to "永久",7 to "7天",30 to "30天",90 to "90天").forEach{(value,label)->FilterChip(days==value,{onRetention(value)},{Text(label)},enabled=!busy)}}
   FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp),itemVerticalAlignment=Alignment.CenterVertically){Text("自定义");AppTextField(custom,{custom=it;error=""},Modifier.width(90.dp),singleLine=true,enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number));Text("天");Primary("应用",{val count=custom.toIntOrNull();if(count==null||count<1)error="请输入大于0的整数天数"else onRetention(count)},enabled=!busy)}
   if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
  }
  Section("手动备份位置"){FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(!connected||!toCloud,{onLocation(false)},{Text("本机")},enabled=connected&&!busy);FilterChip(connected&&toCloud,{onLocation(true)},{Text("本机及云端")},enabled=connected&&!busy)}}
 }
 }
}
