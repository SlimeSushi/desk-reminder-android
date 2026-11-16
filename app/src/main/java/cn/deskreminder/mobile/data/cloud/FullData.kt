package cn.deskreminder.mobile.data.cloud

import cn.deskreminder.mobile.data.model.AppData
import com.google.gson.JsonParser
import java.time.Instant

data class FullEnvelope(var format:String="DeskReminderFullSync",var version:Int=3,var deviceId:String="",var modifiedMs:Long=0,var counter:Long=1,var payloadHash:String="",var data:MutableMap<String,String> = mutableMapOf())

object FullData{
    fun hashMap(map:Map<String,String>)=CloudData.hash(CloudData.canonical(CloudData.gson.toJsonTree(map)))
    fun hasChanges(value:AppData)=value.syncTracking?.version!=3||value.syncTracking?.full?.payloadHash!=hashMap(CloudData.captureMap(value))
    fun capture(value:AppData,device:String,target:String):SyncTracking{
        val state=value.syncTracking?.takeIf{it.version==3&&it.targetKey==target}?.let{CloudData.copy(it)}?:SyncTracking(version=3,targetKey=target)
        if(value.syncTracking?.version==2)state.legacyV2=CloudData.copy(value.syncTracking!!)
        val map=CloudData.captureMap(value).toMutableMap();val hash=hashMap(map);val previous=state.full
        if(previous==null||previous.payloadHash!=hash){
            require(previous==null||previous.modifiedMs<=System.currentTimeMillis()+300000){"系统时间与数据版本相差过大，请校准时间后同步"}
            val count=(previous?.counter?:0)+1;require(count<=CloudData.MAX_COUNTER){"数据修改序号已超出范围"}
            state.full=FullEnvelope(deviceId=device,modifiedMs=maxOf(System.currentTimeMillis(),previous?.modifiedMs?:0),counter=count,payloadHash=hash,data=map)
        };return state
    }
    fun compare(a:FullEnvelope,b:FullEnvelope):Int=compareValuesBy(a,b,{it.modifiedMs},{it.counter},{it.deviceId},{it.payloadHash})
    private fun records(e:FullEnvelope)=e.data.map{(key,value)->SyncRecord(key,mutableListOf(SyncValue(mutableMapOf(e.deviceId to e.counter),value,false,e.deviceId,e.counter,Instant.ofEpochMilli(e.modifiedMs).toString())))}.toMutableList()
    fun validate(e:FullEnvelope){
        require(e.format=="DeskReminderFullSync"&&e.version==3&&e.deviceId.matches(Regex("[a-f0-9]{32}"))&&e.counter in 1..CloudData.MAX_COUNTER&&e.modifiedMs in 0..System.currentTimeMillis()+300000&&e.data.size<=20002&&e.payloadHash==hashMap(e.data)&&e.data.keys.containsAll(listOf("order:todos","order:notes"))){"云端版本、时间或内容校验失败，本机内容已保留"}
        CloudData.validate(SyncEnvelope(deviceId=e.deviceId,sequence=e.counter,createdUtc=Instant.ofEpochMilli(e.modifiedMs).toString(),records=records(e)))
        fun order(key:String)=JsonParser.parseString(e.data.getValue(key)).asJsonArray.map{it.asString}
        val todos=e.data.keys.filter{it.startsWith("todo:")}.map{it.removePrefix("todo:")}.toSet();val notes=e.data.filter{it.key.startsWith("note:")&&!JsonParser.parseString(it.value).asJsonObject["InTrash"].asBoolean}.keys.map{it.removePrefix("note:")}.toSet()
        require(order("order:todos").let{it.size==it.toSet().size&&it.toSet()==todos}&&order("order:notes").let{it.size==it.toSet().size&&it.toSet()==notes}){"云端顺序与内容不一致"}
    }
    fun parse(json:String)=CloudData.gson.fromJson(json,FullEnvelope::class.java).also{validate(it)}
    fun apply(current:AppData,state:SyncTracking,winner:FullEnvelope):AppData{
        validate(winner);val records=records(winner)
        CloudData.captureMap(current).keys.filter{!it.startsWith("order:")&&it !in winner.data}.forEach{records.add(SyncRecord(it,mutableListOf(SyncValue(deleted=true))))}
        val output=CloudData.apply(current,SyncTracking(records=records));state.full=CloudData.gson.fromJson(CloudData.gson.toJson(winner),FullEnvelope::class.java);output.syncTracking=state;return output
    }
}
