package cn.deskreminder.mobile.data.cloud

import android.content.Context
import cn.deskreminder.mobile.data.*
import cn.deskreminder.mobile.data.model.MobileSettings
import java.io.File
import java.time.*

data class CloudBackup(val id:String,val path:String,val createdMs:Long,val device:String,val todos:Int,val notes:Int,val trash:Int)
private data class HistoryCache(val target:String,val items:List<CloudBackup>)
object CloudBackups{
 private val pattern=Regex("history-(\\d{13})-manual-(\\d+)-(\\d+)-(\\d+)-([a-f0-9]{32})-([a-f0-9]{64})\\.json")
 fun cached(context:Context,target:String):List<CloudBackup> = runCatching{CloudData.gson.fromJson(File(context.filesDir,"cloud-history-index.json").readText(),HistoryCache::class.java).takeIf{it.target==target}?.items.orEmpty()}.getOrDefault(emptyList())
 suspend fun pull(context:Context,web:NutstoreTransport,p:CloudProfile):List<CloudBackup>{
  val items=web.list(p,"protocol-v3/history",true).filter{!it.directory}.mapNotNull{file->val match=pattern.matchEntire(file.name)?:return@mapNotNull null;val (time,t,n,r,device,hash)=match.destructured
   val row=CloudBackup(hash,"protocol-v3/history/${file.name}",time.toLong(),device,t.toInt(),n.toInt(),r.toInt());require(row.createdMs<=System.currentTimeMillis()+300000&&row.todos.toLong()+row.notes+row.trash<=20000){"云端备份索引无效"};row}
  DataRepository.writeAtomic(File(context.filesDir,"cloud-history-index.json"),CloudData.gson.toJson(HistoryCache(NutstoreTransport.target(p),items)));return items.distinctBy{it.id}.sortedByDescending{it.createdMs}
 }
 suspend fun upload(web:NutstoreTransport,p:CloudProfile,repo:DataRepository,info:BackupInfo):CloudBackup{
  require(info.reason=="manual"){"只上传手动历史备份"};val json=repo.readSaved(info);val hash=CloudData.hash(json);val time=info.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
  val name="history-$time-manual-${info.todos}-${info.notes}-${info.trash}-${p.deviceId}-$hash.json";web.writeVerified(p,"protocol-v3/history/$name",json);return CloudBackup(hash,"protocol-v3/history/$name",time,p.deviceId,info.todos,info.notes,info.trash)
 }
 fun store(context:Context,target:String,items:List<CloudBackup>){DataRepository.writeAtomic(File(context.filesDir,"cloud-history-index.json"),CloudData.gson.toJson(HistoryCache(target,items)))}
 suspend fun read(web:NutstoreTransport,p:CloudProfile,entry:CloudBackup):String{
  require(pattern.matches(entry.path.substringAfterLast('/'))&&entry.path.startsWith("protocol-v3/history/")){"备份地址无效"};val json=web.read(p,entry.path);require(CloudData.hash(json)==entry.id){"云端备份校验失败"}
  val value=BackupCodec.read(json,MobileSettings()).data;require(value.todos.size==entry.todos&&value.notes.size==entry.notes&&value.noteTrash.size==entry.trash){"云端备份数量不一致"};return json
 }
}
