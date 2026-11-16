package cn.deskreminder.mobile.data.model

import java.util.UUID
import cn.deskreminder.mobile.data.cloud.SyncTracking

data class ReminderRule(var enabled:Boolean=true,var advanceMinutes:Int=30,var repeatMinutes:Int=30,var popup:Boolean=true,var highlight:Boolean=true,var sound:Boolean=true,var vibrate:Boolean=true)
data class StopRecord(var stoppedAt:String="",var reason:String="",var resumedAt:String?=null)
data class Postponement(var fromDueAt:String="",var toDueAt:String="",var changedAt:String="",var kind:String="postpone",var reason:String="")
data class WeeklyCompletion(var weekKey:String="",var completedAt:String="")
data class WeeklySnapshot(var original:String="",var due:String="",var changeCount:Int=0)
data class WeeklyPostponement(var fromWeek:String="",var offsetMinutes:Long=0,var allFuture:Boolean=false,var operationId:String="",var changedAt:String="",var excludedWeeks:MutableList<String> = mutableListOf(),var reason:String="")
data class Todo(
    var id:String=UUID.randomUUID().toString(),var title:String="",var content:String="",var type:String="open",
    var dueAt:String="",var originalDueAt:String?=null,var completedAt:String?=null,var done:Boolean=false,
    var stopped:Boolean=false,var stopRecords:MutableList<StopRecord> = mutableListOf(),
    var weekday:Int=5,var weeklyTime:String="17:00",var weeklyStartDate:String="",var weeklyEndDate:String="",
    var doneWeek:String?=null,var completedWeeks:MutableList<String> = mutableListOf(),
    var weeklyCompletions:MutableList<WeeklyCompletion> = mutableListOf(),
    var postponements:MutableList<Postponement> = mutableListOf(),
    var weeklyPostponements:MutableList<WeeklyPostponement> = mutableListOf(),
    var reminder:ReminderRule?=null,var createdAt:String="",var weeklySnapshots:MutableMap<String,WeeklySnapshot> = mutableMapOf(),var archived:Boolean=false
)
data class Note(var id:String=UUID.randomUUID().toString(),var text:String="",var createdAt:String="",var pinned:Boolean=false)
data class TrashedNote(var note:Note=Note(),var deletedAt:String="",var originalIndex:Int=0)
data class ReminderStamp(var dueSignature:String="",var lastAt:Long=0,var snoozedUntil:Long=0)
data class MobileSettings(
    var theme:String="light",var reminder:ReminderRule=ReminderRule(),var historyDays:Int=0,var backupRetentionDays:Int=0,
    var widgetBackground:String="#FFFFFF",var widgetText:String="#202938",var widgetBackgroundOpacity:Int=88,var widgetTextOpacity:Int=100,
    var widgetTodoTitle:Boolean=false,var widgetNoteTitle:Boolean=false,var widgetTimeMode:String="all",
    var appStyle:String="fruit"
)
data class AppData(
    var todos:MutableList<Todo> = mutableListOf(),var notes:MutableList<Note> = mutableListOf(),
    var noteTrash:MutableList<TrashedNote> = mutableListOf(),var settings:MobileSettings=MobileSettings(),
    var reminderState:MutableMap<String,ReminderStamp> = mutableMapOf(),var revision:Long=0,
    var syncTracking:SyncTracking?=null
)
data class TodoDraft(var original:String?=null,var todo:Todo=Todo(),var actualTime:String?=null)
data class NoteDraft(var original:String?=null,var note:Note=Note())
