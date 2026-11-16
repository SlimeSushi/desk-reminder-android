package cn.deskreminder.mobile.data.cloud

import cn.deskreminder.mobile.data.*
import cn.deskreminder.mobile.data.model.*
import com.google.gson.*
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class SyncValue(var clock:MutableMap<String,Long> = mutableMapOf(),var json:String?=null,var deleted:Boolean=false,var actor:String="",var counter:Long=0,var changedUtc:String="")
data class SyncRecord(var key:String="",var values:MutableList<SyncValue> = mutableListOf())
data class SyncTracking(var legacyV2:SyncTracking?=null,var full:FullEnvelope?=null,var fullHeads:MutableMap<String,FullEnvelope> = mutableMapOf(),var fullTags:MutableMap<String,String> = mutableMapOf(),var backupQueue:MutableList<String> = mutableListOf(),var version:Int=2,var targetKey:String="",var uploadSequence:Long=0,var lastUploadedHash:String?=null,var pendingJson:String?=null,var pendingRecordsHash:String?=null,var remoteFiles:MutableMap<String,String> = mutableMapOf(),var counter:Long=0,var records:MutableList<SyncRecord> = mutableListOf(),var lastLocal:MutableMap<String,String> = mutableMapOf(),
    // Local queue metadata. A zero deadline permits manual retry immediately.
    var backupUploadFailures:MutableMap<String,Long>?=null
)
data class SyncEnvelope(var format:String="DeskReminderSync",var version:Int=2,var deviceId:String="",var sequence:Long=0,var createdUtc:String="",var businessTimeSemantics:String="local-wall-clock",var records:MutableList<SyncRecord> = mutableListOf())

object CloudData {
    const val MAX_COUNTER=9007199254740991L
    val gson:Gson get()=BackupCodec.gson
    val todoFields=setOf("Id","Title","Content","Stopped","StopRecords","Type","DueAt","OriginalDueAt","CompletedAt","Postponements","Weekday","WeeklyTime","WeeklyStartDate","WeeklyEndDate","Done","DoneWeek","CompletedWeeks","WeeklyCompletions","WeeklyPostponements","CreatedAt","Archived","WeeklySnapshots")
    private val actorPattern=Regex("[a-f0-9]{32}")
    fun deviceId():String=UUID.randomUUID().toString().replace("-","")
    fun hash(text:String):String=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun quote(value:String):String=buildString{
        append('"');value.forEach{c->when(c){'"'->append("\\\"");'\\'->append("\\\\");'\b'->append("\\b");'\u000c'->append("\\f");'\n'->append("\\n");'\r'->append("\\r");'\t'->append("\\t");else->if(c.code<32)append("\\u%04x".format(c.code))else append(c)}};append('"')
    }
    fun canonical(value:JsonElement?):String=when{
        value==null||value.isJsonNull->"\"\""
        value.isJsonObject->value.asJsonObject.keySet().sorted().joinToString(",","{","}"){quote(it)+":"+canonical(value.asJsonObject[it])}
        value.isJsonArray->value.asJsonArray.joinToString(",","[","]"){canonical(it)}
        value.asJsonPrimitive.isString->quote(value.asString)
        else->value.toString()
    }
    private inline fun <reified T> clone(value:T):T=gson.fromJson(gson.toJson(value),T::class.java)
    fun copy(state:SyncTracking):SyncTracking=clone(state)
    private fun id(value:String){require(value.isNotBlank()&&value.length<=128&&!value.contains('\u0000')){"记录标识无效"}}
    private fun actor(value:String){require(actorPattern.matches(value)){"同步设备标识无效"}}
    private fun todo(task:Todo):JsonObject=gson.toJsonTree(task).asJsonObject.apply{keySet().toList().filter{it !in todoFields}.forEach{remove(it)}}
    fun captureMap(data:AppData):Map<String,String> {
        val result=linkedMapOf<String,String>()
        fun add(key:String,value:JsonElement){require(!result.containsKey(key)){"业务记录标识重复，未同步"};result[key]=canonical(value)}
        data.todos.forEach{id(it.id);add("todo:${it.id}",todo(it))}
        fun note(value:Note,trash:Boolean=false,at:String="",index:Int=0){id(value.id);add("note:${value.id}",JsonObject().apply{this.add("Note",gson.toJsonTree(value));addProperty("InTrash",trash);addProperty("DeletedAt",at);addProperty("OriginalIndex",index)})}
        data.notes.forEach{note(it)}
        data.noteTrash.forEach{note(it.note,true,it.deletedAt,(data.notes.size-it.originalIndex).coerceIn(0,data.notes.size))}
        result["order:todos"]=canonical(gson.toJsonTree(data.todos.map{it.id}))
        result["order:notes"]=canonical(gson.toJsonTree(data.notes.asReversed().map{it.id}))
        return result
    }
    fun hasChanges(data:AppData):Boolean=data.syncTracking?.lastLocal!=captureMap(data).mapValues{hash(it.value)}
    private fun joined(clocks:List<Map<String,Long>>):MutableMap<String,Long> {val result=mutableMapOf<String,Long>();clocks.forEach{clock->clock.forEach{(key,value)->result[key]=maxOf(result[key]?:0,value)}};return result}
    private fun dominates(a:Map<String,Long>,b:Map<String,Long>):Boolean=(a.keys+b.keys).all{(a[it]?:0)>=(b[it]?:0)}&&(a.keys+b.keys).any{(a[it]?:0)>(b[it]?:0)}
    private fun identity(value:SyncValue):String=if(value.deleted)"deleted"else value.json.orEmpty()
    private fun tie(value:SyncValue):String=value.changedUtc+"|"+value.actor+"|"+value.counter.toString().padStart(20,'0')+"|"+hash(identity(value))
    fun capture(data:AppData,device:String):SyncTracking {
        actor(device);val state=data.syncTracking?.let{copy(it)}?:SyncTracking()
        require(state.version==2&&state.counter in 0..MAX_COUNTER){"本机同步状态版本无效"};validateRecords(state.records)
        state.records.flatMap{it.values}.forEach{state.counter=maxOf(state.counter,it.clock[device]?:0)}
        val current=captureMap(data);val records=state.records.associateBy{it.key}.toMutableMap()
        (current.keys+state.lastLocal.keys).sorted().forEach{key->
            val json=current[key];val old=state.lastLocal[key]
            if(json!=null&&old==hash(json)||json==null&&old==null)return@forEach
            val record=records.getOrPut(key){SyncRecord(key)}
            val branch=if(record.values.size>1)record.values.filter{!it.deleted&&old!=null&&hash(it.json.orEmpty())==old}else record.values.toList()
            val remaining=if(record.values.size>1)record.values.filter{it !in branch}.toMutableList()else mutableListOf()
            val clock=joined(branch.map{it.clock});state.counter=maxOf(state.counter,clock[device]?:0)
            require(state.counter<MAX_COUNTER){"同步版本已超出范围"};clock[device]=++state.counter
            remaining.add(SyncValue(clock,json,json==null,device,state.counter,Instant.now().toString()))
            require(remaining.size<=16){"请先处理同步冲突"};record.values=remaining
        }
        state.records=records.values.sortedBy{it.key}.toMutableList();state.lastLocal=current.mapValues{hash(it.value)}.toMutableMap();return state
    }
    fun merge(local:SyncTracking,envelopes:List<SyncEnvelope>):SyncTracking {
        val result=copy(local);validateRecords(result.records);val map=result.records.associateBy{it.key}.toMutableMap()
        envelopes.forEach{envelope->validate(envelope);envelope.records.forEach incoming@{incoming->
            val record=map[incoming.key]
            if(record==null){map[incoming.key]=clone(incoming);return@incoming}
            var all=(record.values+incoming.values).map{clone(it)}
            all.forEachIndexed{i,value->all.drop(i+1).forEach{other->require(value.clock!=other.clock||identity(value)==identity(other)){"云端相同版本内容不同，已停止同步"}}}
            all=all.distinctBy{canonical(gson.toJsonTree(it.clock))+"|"+identity(it)}
            all=all.filter{value->all.none{other->other!==value&&dominates(other.clock,value.clock)}}
            var normal=all.groupBy{identity(it)}.values.map{group->clone(group.maxBy{tie(it)}).apply{clock=joined(group.map{it.clock})}}
            if(incoming.key.startsWith("order:")&&normal.size>1)normal=listOf(clone(normal.maxBy{tie(it)}).apply{clock=joined(normal.map{it.clock})})
            require(normal.size<=16){"同一记录冲突过多，请先处理"};record.values=normal.sortedBy{tie(it)}.toMutableList()
        }}
        result.records=map.values.sortedBy{it.key}.toMutableList();return result
    }
    fun conflicts(state:SyncTracking?):List<SyncRecord> = state?.records.orEmpty().filter{it.values.size>1}
    private fun reminderIdentity(task:Todo):String {val source=todo(task);val keys=listOf("Type","DueAt","Weekday","WeeklyTime","WeeklyStartDate","WeeklyEndDate","WeeklyPostponements","Done","Stopped","DoneWeek","CompletedWeeks");return canonical(JsonObject().apply{keys.forEach{add(it,source[it])}})}
    fun apply(current:AppData,state:SyncTracking):AppData {
        val output=BackupCodec.copy(current);val tasks=output.todos.associateBy{it.id}.toMutableMap()
        val notes=linkedMapOf<String,JsonObject>()
        captureMap(current).filterKeys{it.startsWith("note:")}.forEach{(key,value)->notes[key.substringAfter(':')]=JsonParser.parseString(value).asJsonObject}
        state.records.filter{it.values.size==1&&!it.key.startsWith("order:")}.forEach{record->
            val value=record.values.single();val key=record.key.substringAfter(':')
            if(record.key.startsWith("todo:")){
                if(value.deleted)tasks.remove(key)else{val task=gson.fromJson(value.json,Todo::class.java);task.reminder=current.todos.firstOrNull{it.id==key}?.reminder;tasks[key]=task}
            }else{if(value.deleted)notes.remove(key)else notes[key]=JsonParser.parseString(value.json).asJsonObject}
        }
        fun order(key:String,ids:Set<String>):List<String>{val raw=state.records.firstOrNull{it.key==key&&it.values.size==1&&!it.values[0].deleted}?.values?.get(0)?.json;val desired=raw?.let{JsonParser.parseString(it).asJsonArray.map{entry->entry.asString}}.orEmpty();return (desired.filter{it in ids}+ids.sorted()).distinct()}
        output.todos=order("order:todos",tasks.keys).map{tasks.getValue(it)}.toMutableList()
        val active=notes.filterValues{!it["InTrash"].asBoolean}
        output.notes=order("order:notes",active.keys).asReversed().map{gson.fromJson(active.getValue(it)["Note"],Note::class.java)}.toMutableList()
        output.noteTrash=notes.values.filter{it["InTrash"].asBoolean}.sortedBy{it["DeletedAt"].asString}.map{TrashedNote(gson.fromJson(it["Note"],Note::class.java),it["DeletedAt"].asString,(output.notes.size-it["OriginalIndex"].asInt).coerceIn(0,output.notes.size))}.toMutableList()
        val before=current.todos.associateBy{it.id};val changed=output.todos.filter{before[it.id]?.let{old->reminderIdentity(old)!=reminderIdentity(it)}?:true}.map{it.id}.toSet()+(before.keys-tasks.keys)
        output.reminderState.keys.filter{it.substringBefore(':') in changed}.forEach{output.reminderState.remove(it)}
        output.settings=current.settings;output.syncTracking=copy(state);output.syncTracking!!.lastLocal=captureMap(output).mapValues{hash(it.value)}.toMutableMap()
        BackupCodec.validate(output);return output
    }
    fun resolve(state:SyncTracking,key:String,index:Int,device:String):SyncTracking {
        actor(device);validateRecords(state.records);val result=copy(state);val record=result.records.first{it.key==key};require(index in record.values.indices)
        val value=clone(record.values[index]);val clock=joined(record.values.map{it.clock});result.counter=maxOf(result.counter,clock[device]?:0)
        require(result.counter<MAX_COUNTER);clock[device]=++result.counter;value.clock=clock;value.actor=device;value.counter=result.counter;value.changedUtc=Instant.now().toString();record.values=mutableListOf(value);return result
    }
    fun envelope(state:SyncTracking,device:String,sequence:Long)=SyncEnvelope(deviceId=device,sequence=sequence,createdUtc=Instant.now().toString(),records=clone(state).records)
    fun recordsHash(state:SyncTracking)=hash(canonical(gson.toJsonTree(state.records)))
    fun parse(json:String):SyncEnvelope {require(json.toByteArray().size<=50*1024*1024){"云端文件过大"};return gson.fromJson(json,SyncEnvelope::class.java).also{validate(it)}}
    fun validate(envelope:SyncEnvelope){require(envelope.format=="DeskReminderSync"&&envelope.version==2&&envelope.sequence in 1..MAX_COUNTER&&envelope.businessTimeSemantics=="local-wall-clock"){"无法识别云端同步版本"};actor(envelope.deviceId);Instant.parse(envelope.createdUtc);validateRecords(envelope.records)}
    fun validateRecords(records:List<SyncRecord>){
        require(records.size<=20000&&records.map{it.key}.distinct().size==records.size){"云端记录数量无效"}
        records.forEach{record->
            val task=record.key.startsWith("todo:");val note=record.key.startsWith("note:");val order=record.key in listOf("order:todos","order:notes")
            require((task||note||order)&&record.values.size in 1..16){"云端记录版本无效"}
            record.values.forEach{value->
                require(value.clock.size in 1..128&&value.counter in 1..MAX_COUNTER){"云端记录时钟无效"};actor(value.actor);Instant.parse(value.changedUtc)
                value.clock.forEach{(key,number)->actor(key);require(number in 1..MAX_COUNTER)};require((value.clock[value.actor]?:0)>=value.counter)
                if(value.deleted){require(value.json==null&&!order){"删除标记无效"};return@forEach}
                require(!value.json.isNullOrEmpty()&&value.json!!.toByteArray().size<=2*1024*1024){"云端记录内容无效"}
                val json=JsonParser.parseString(value.json)
                if(order){val ids=json.asJsonArray.map{it.asString};require(ids.size<=20000&&ids.distinct().size==ids.size);ids.forEach{ id(it) }}
                else if(task){require(json.asJsonObject.keySet().all{it in todoFields}){"云端包含设备配置或未知业务字段"};val item=gson.fromJson(json,Todo::class.java);id(item.id);require(record.key=="todo:${item.id}");BackupCodec.validate(AppData(todos=mutableListOf(item)))}
                else{val body=json.asJsonObject;require(body.keySet()==setOf("Note","InTrash","DeletedAt","OriginalIndex"));require(body["Note"].asJsonObject.keySet().all{it in setOf("Id","Text","CreatedAt","Pinned")});val item=gson.fromJson(body["Note"],Note::class.java);id(item.id);require(record.key=="note:${item.id}");val check=AppData();if(body["InTrash"].asBoolean)check.noteTrash.add(TrashedNote(item,body["DeletedAt"].asString,body["OriginalIndex"].asInt))else check.notes.add(item);BackupCodec.validate(check)}
            }
        }
    }
}
