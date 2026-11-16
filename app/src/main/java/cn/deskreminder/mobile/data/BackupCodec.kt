package cn.deskreminder.mobile.data

import cn.deskreminder.mobile.data.model.*
import com.google.gson.*
import java.time.*
import java.util.UUID

data class ImportedData(val data:AppData,val mobile:Boolean,val createdAt:String)
object BackupCodec {
    val gson:Gson=GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).serializeNulls().disableHtmlEscaping().create()
    fun copy(value:AppData):AppData=decodeState(gson.toJson(value))
    fun copy(task:Todo):Todo=gson.fromJson(gson.toJson(task),Todo::class.java)
    fun copy(note:Note):Note=gson.fromJson(gson.toJson(note),Note::class.java)
    fun decodeState(json:String):AppData { require(json.length<=50*1024*1024){"文件过大，最多支持50MB"};return normalize(gson.fromJson(json,AppData::class.java)?:error("数据为空")) }
    fun business(data:AppData):JsonObject {
        val root=JsonObject()
        val tasks=JsonArray();data.todos.forEach{task->val value=gson.toJsonTree(task).asJsonObject;value.remove("Reminder");tasks.add(value)}
        root.add("Todos",tasks)
        // The desktop stores notes from low to high priority; mobile renders them in display order.
        root.add("Notes",gson.toJsonTree(data.notes.reversed()))
        val trash=gson.toJsonTree(data.noteTrash).asJsonArray
        trash.forEach{it.asJsonObject.addProperty("OriginalIndex",(data.notes.size-it.asJsonObject.get("OriginalIndex").asInt).coerceIn(0,data.notes.size))}
        root.add("NoteTrash",trash);return root
    }
    fun document(data:AppData,reason:String):String {
        return gson.toJson(JsonObject().apply{addProperty("Format","DeskReminderBackup");addProperty("Version",2);addProperty("Scope","business");addProperty("Source","android");addProperty("CreatedAt",OffsetDateTime.now(ZoneOffset.UTC).toString());addProperty("Reason",reason);add("Data",business(data))})
    }
    fun read(json:String,current:MobileSettings):ImportedData {
        require(json.length<=50*1024*1024){"备份文件超过50MB"}
        val root=JsonParser.parseString(json).asJsonObject
        val format=root.get("Format")?.takeUnless{it.isJsonNull}?.asString.orEmpty()
        val legacyMobile=format=="DeskReminderMobileBackup"
        val mobile=legacyMobile||root.get("Source")?.asString=="android"
        require(format.isEmpty()||format=="DeskReminderBackup"||legacyMobile){"不支持的备份格式"}
        if(format.isNotEmpty()){
            val version=root.get("Version")?.asInt
            require(version==1||format=="DeskReminderBackup"&&version==2&&root.get("Scope")?.asString=="business"){"备份版本或范围暂不支持"}
        }
        val body=if(format.isEmpty())root else root.getAsJsonObject("Data")?:error("备份缺少数据")
        require(body.has("Todos")&&body.get("Todos").isJsonArray&&body.has("Notes")&&body.get("Notes").isJsonArray){"文件缺少待办或随手记"}
        val data:AppData
        run {
            // Desktop styles, global/per-task reminders and runtime state stay on their original device.
            val business=JsonObject();business.add("Todos",body.get("Todos").deepCopy());business.add("Notes",body.get("Notes").deepCopy())
            body.get("NoteTrash")?.let{business.add("NoteTrash",it.deepCopy())};business.add("Settings",gson.toJsonTree(current))
            business.getAsJsonArray("Todos").forEach{it.asJsonObject.remove("Reminder")}
            data=decodeState(business.toString())
            if(!legacyMobile){data.notes.reverse();data.noteTrash.forEach{it.originalIndex=(data.notes.size-it.originalIndex).coerceIn(0,data.notes.size)}}
        }
        data.reminderState.clear();validate(data)
        return ImportedData(data,mobile,root.get("CreatedAt")?.takeUnless{it.isJsonNull}?.asString.orEmpty())
    }
    private fun normalize(data:AppData):AppData {
        data.todos=data.todos.orEmpty().filterNotNull().toMutableList();data.notes=data.notes.orEmpty().filterNotNull().toMutableList();data.noteTrash=data.noteTrash.orEmpty().filterNotNull().toMutableList()
        data.reminderState=data.reminderState.orEmpty().toMutableMap();data.settings=data.settings?:MobileSettings()
        if(data.settings.appStyle !in listOf("classic","fruit"))data.settings.appStyle="fruit"
        if(data.settings.widgetTimeMode=="today")data.settings.widgetTimeMode="through_today"
        val ids=mutableSetOf<String>()
        data.todos.forEach{t->
            t.id=t.id.orEmpty();if(t.id.isBlank()||!ids.add(t.id)){t.id=UUID.randomUUID().toString();ids.add(t.id)}
            t.title=t.title.orEmpty();t.content=t.content.orEmpty();t.type=t.type.orEmpty();t.dueAt=t.dueAt.orEmpty();t.weeklyTime=t.weeklyTime.orEmpty();t.weeklyStartDate=t.weeklyStartDate.orEmpty();t.weeklyEndDate=t.weeklyEndDate.orEmpty();t.createdAt=t.createdAt.orEmpty()
            t.stopRecords=t.stopRecords.orEmpty().filterNotNull().toMutableList();t.completedWeeks=t.completedWeeks.orEmpty().filterNotNull().distinct().toMutableList()
            t.weeklyCompletions=t.weeklyCompletions.orEmpty().filterNotNull().toMutableList();t.postponements=t.postponements.orEmpty().filterNotNull().toMutableList();t.weeklyPostponements=t.weeklyPostponements.orEmpty().filterNotNull().toMutableList();t.weeklySnapshots=t.weeklySnapshots.orEmpty().toMutableMap()
            t.postponements.forEach{it.reason=it.reason.orEmpty()}
            t.weeklyPostponements.forEach{it.excludedWeeks=it.excludedWeeks.orEmpty().filterNotNull().toMutableList();it.operationId=it.operationId.orEmpty();it.changedAt=it.changedAt.orEmpty();it.reason=it.reason.orEmpty()}
            t.doneWeek?.takeIf{it.isNotBlank()}?.let{if(!t.completedWeeks.contains(it))t.completedWeeks.add(it)}
        }
        ids.clear();(data.notes+data.noteTrash.map{it.note}).forEach{note->
            note.id=note.id.orEmpty();if(note.id.isBlank()||!ids.add(note.id)){note.id=UUID.randomUUID().toString();ids.add(note.id)}
            note.text=note.text.orEmpty();note.createdAt=note.createdAt.orEmpty()
        }
        return data
    }
    fun validate(data:AppData) {
        require(data.todos.size+data.notes.size+data.noteTrash.size<=20000){"记录数量过多"}
        fun rule(value:ReminderRule){require(value.advanceMinutes in 0..525600&&value.repeatMinutes>=0){"提醒时间或间隔无效"}}
        rule(data.settings.reminder)
        require(data.settings.historyDays>=0&&data.settings.backupRetentionDays>=0){"保留天数不能为负数"}
        require(data.settings.appStyle in listOf("classic","fruit")){"界面风格无效"}
        require(data.settings.theme in listOf("light","dark","system")){"外观设置无效"}
        require(data.settings.widgetBackground.matches(Regex("#[A-Fa-f0-9]{6}"))&&data.settings.widgetText.matches(Regex("#[A-Fa-f0-9]{6}"))){"颜色格式无效"}
        require(data.settings.widgetBackgroundOpacity in 0..100&&data.settings.widgetTextOpacity in 0..100){"透明度无效"}
        require(data.settings.widgetTimeMode in listOf("none","through_today","all")){"组件时间显示设置无效"}
        data.todos.forEach{task->
            require(task.title.isNotBlank()&&task.title.length<=20000&&task.content.length<=2*1024*1024){"备份中有无效的事项标题或内容"}
            require(task.type in listOf("open","once","weekly")){"未知的计划类型"}
            if(task.type=="once")require(Dates.moment(task.dueAt)!=null){"计划完成时间无效"}
            if(task.type=="weekly"){
                require(task.weekday in 1..7&&Dates.time(task.weeklyTime)!=null){"每周时间无效"}
                val start=Dates.date(task.weeklyStartDate);val end=Dates.date(task.weeklyEndDate)
                require((task.weeklyStartDate.isEmpty()||start!=null)&&(task.weeklyEndDate.isEmpty()||end!=null)){"每周周期日期无效"}
                require(start==null||end==null||end>=start){"结束日期不能早于开始日期"}
                if(start!=null&&end!=null)require(start.plusDays(((task.weekday-start.dayOfWeek.value+7)%7).toLong())<=end){"周期内没有所选星期"}
            }
            task.weeklySnapshots.forEach{(key,value)->require(Dates.date(key)?.let{Dates.key(it)==key}==true&&Dates.moment(value.original)!=null&&Dates.moment(value.due)!=null&&value.changeCount in 0..task.weeklyPostponements.size){"每周历史计划快照无效"}}
            task.completedAt?.takeIf{it.isNotEmpty()}?.let{require(Dates.moment(it)!=null){"实际完成时间无效"}}
            task.completedWeeks.forEach{require(Dates.date(it)?.let{d->Dates.key(d)==it}==true){"每周历史日期无效"}}
            task.weeklyCompletions.forEach{require(Dates.date(it.weekKey)!=null&&Dates.moment(it.completedAt)!=null){"每周完成记录无效"}}
            task.postponements.forEach{require(Dates.moment(it.fromDueAt)!=null&&Dates.moment(it.toDueAt)!=null){"延期历史无效"}}
            task.weeklyPostponements.forEach{require(Dates.date(it.fromWeek)!=null&&it.offsetMinutes in -525600000L..525600000L){"每周延期记录无效"}}
            task.stopRecords.forEach{require(Dates.moment(it.stoppedAt)!=null&&(it.resumedAt.isNullOrEmpty()||Dates.moment(it.resumedAt)!=null)){"中止记录无效"}}
            task.reminder?.let(::rule)
        }
        data.notes.forEach{require(it.text.isNotBlank()&&it.text.length<=2*1024*1024){"随手记内容无效"}}
        data.noteTrash.forEach{require(it.note.text.isNotBlank()&&it.originalIndex>=0&&Dates.moment(it.deletedAt)!=null){"废纸篓记录无效"}}
    }
}
