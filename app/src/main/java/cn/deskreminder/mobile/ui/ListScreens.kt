@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.deskreminder.mobile.ui

import androidx.compose.foundation.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import cn.deskreminder.mobile.MainViewModel
import cn.deskreminder.mobile.data.*
import cn.deskreminder.mobile.data.model.*
import kotlinx.coroutines.delay
import java.time.*

@Stable class TodoSwipeState {
    var openKey by mutableStateOf<String?>(null)
    fun close(){openKey=null}
}

enum class TodoListPage(val label:String){PENDING("待完成"),COMPLETED("已完成"),STOPPED("已中止")}

@Composable fun TodoList(data:AppData,query:String,onOpen:(Occurrence)->Unit,onEdit:(Occurrence?)->Unit,onPostpone:(Occurrence)->Unit,onRemove:(Occurrence)->Unit,onToggle:(Occurrence)->Unit,onResume:(Occurrence)->Unit,now:LocalDateTime){
    var pageName by rememberSaveable{mutableStateOf(TodoListPage.PENDING.name)}
    val page=runCatching{TodoListPage.valueOf(pageName)}.getOrDefault(TodoListPage.PENDING)
    val counts=remember(data.revision,now.minute){listOf(Schedule.displayPending(data,now).size,Schedule.history(data,now).size,Schedule.stopped(data,now).size)}
    val entries=remember(data.revision,page,query,now.minute){when(page){
        TodoListPage.PENDING->Schedule.displayPending(data,now)
        TodoListPage.COMPLETED->Schedule.history(data,now)
        TodoListPage.STOPPED->Schedule.stopped(data,now)
    }.filter{query.isBlank()||it.todo.title.contains(query,true)||it.todo.content.contains(query,true)}}
    val state=rememberLazyListState()
    val swipe=remember{TodoSwipeState()}
    LaunchedEffect(page,query){swipe.close()}
    LaunchedEffect(state.isScrollInProgress){if(state.isScrollInProgress)swipe.close()}
    LaunchedEffect(entries){if(entries.none{it.key==swipe.openKey})swipe.close()}
    BackHandler(swipe.openKey!=null){swipe.close()}
    LazyColumn(Modifier.fillMaxSize().onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.Escape&&swipe.openKey!=null){swipe.close();true}else false}.pointerInput(swipe){detectTapGestures{swipe.close()}},state,contentPadding=PaddingValues(start=20.dp,end=20.dp,top=4.dp,bottom=LocalChromeBottomInset.current+24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item("switch"){SegmentedControl(listOf(
            TodoListPage.PENDING to "待完成 ${counts[0]}",
            TodoListPage.COMPLETED to "已完成 ${counts[1]}",
            TodoListPage.STOPPED to "已中止 ${counts[2]}"
        ),page,{pageName=it.name},Modifier.padding(bottom=10.dp))}
        if(entries.isEmpty())item("empty"){EmptyState(if(query.isNotBlank())"没有找到匹配事项"else when(page){
            TodoListPage.COMPLETED->"暂无已完成记录"
            TodoListPage.STOPPED->"没有已中止的事项"
            else->"还没有待办"
        },if(page==TodoListPage.PENDING&&query.isBlank())"新增待办"else null,{onEdit(null)})}
        items(entries,key={it.key+page.name}){item->TodoCard(item,data.settings,onOpen={onOpen(item)},onToggle={onToggle(item)},onEdit={onEdit(item)},onPostpone={onPostpone(item)},onRemove={onRemove(item)},onResume={onResume(item)},now=now,swipe=swipe)}
    }
}

@Composable fun TodoCard(item:Occurrence,settings:MobileSettings,onOpen:()->Unit,onToggle:()->Unit,onEdit:()->Unit,onPostpone:()->Unit,onRemove:()->Unit,onResume:()->Unit={},now:LocalDateTime=LocalDateTime.now(),swipe:TodoSwipeState=remember{TodoSwipeState()}){
    var drag by remember(item.key){mutableFloatStateOf(0f)};var dragging by remember(item.key){mutableStateOf(false)}
    val canPostpone=!item.completed&&!item.stopped&&!item.ended
    val canResume=item.stopped
    val density=LocalDensity.current
    val width=with(density){(if(canPostpone||canResume)112.dp else 56.dp).toPx()}
    val opened=swipe.openKey==item.key
    val offset by animateFloatAsState(if(dragging)drag else if(opened)width else 0f,if(dragging)snap()else tween(220),label="待办侧滑")
    val interactions=remember{MutableInteractionSource()};val pressed by interactions.collectIsPressedAsState();val focused by interactions.collectIsFocusedAsState()
    val scale by animateFloatAsState(if(pressed).985f else 1f,tween(120),label="待办按压")
    val dueState=Schedule.dueState(item,settings,now);val highlight=dueState!=DueState.NORMAL
    val green=if(MaterialTheme.colorScheme.surface.luminance()<.25f)Color(0xFF86EFAC)else Tone.Green
    val overdue=!item.completed&&!item.stopped&&!item.ended&&item.due?.isBefore(now)==true
    val titleColor=when{item.completed->green;item.stopped||item.ended->MaterialTheme.colorScheme.onSurfaceVariant;highlight->dueStateColor(dueState);else->MaterialTheme.colorScheme.onSurface}
    Box(Modifier.fillMaxWidth().testTag("todo-row-${item.key}").clip(appShape(14.dp,20.dp)).semantics{customActions=buildList{if(canResume)add(CustomAccessibilityAction("恢复"){swipe.close();onResume();true});if(canPostpone)add(CustomAccessibilityAction(if(item.due==null)"补设时间"else"延期"){swipe.close();onPostpone();true});add(CustomAccessibilityAction("中止或删除"){swipe.close();onRemove();true})}}){
        val titleLines=if(opened||dragging||offset>1f)1 else 2
        if(offset>1f)Row(Modifier.matchParentSize(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.End),verticalAlignment=Alignment.CenterVertically){
            if(canResume)SwipeAction("恢复",Icons.Rounded.Restore,Tone.Green,Modifier.testTag("todo-resume-${item.key}")){swipe.close();drag=0f;onResume()}
            if(canPostpone)SwipeAction(if(item.due==null)"补时"else"延期",if(item.due==null)Icons.Rounded.Timer else Icons.Rounded.Update,MaterialTheme.colorScheme.primary,Modifier.testTag("todo-delay-${item.key}")){swipe.close();drag=0f;onPostpone()}
            SwipeAction("删除",Icons.Rounded.DeleteOutline,MaterialTheme.colorScheme.error,Modifier.testTag("todo-delete-${item.key}")){swipe.close();drag=0f;onRemove()}
        }
        Surface(shape=appShape(14.dp,20.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().padding(end=with(density){offset.toDp()}).testTag("todo-content-${item.key}").graphicsLayer{scaleX=scale;scaleY=scale}.then(if(focused)Modifier.border(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.55f),appShape(14.dp,20.dp))else Modifier).heightIn(min=if(item.due!=null||item.completed||item.stopped)with(density){(if(item.completed||item.stopped)80.sp else 64.sp).toDp()}+16.dp else maxOf(64.dp,with(density){44.sp.toDp()}+16.dp)).draggable(rememberDraggableState{drag=(drag-it).coerceIn(0f,width)},Orientation.Horizontal,onDragStarted={drag=offset;dragging=true;swipe.openKey=item.key},onDragStopped={velocity->val keep=when{velocity< -700->true;velocity>700->false;else->drag>width*.4f};if(keep)swipe.openKey=item.key else if(swipe.openKey==item.key)swipe.close();dragging=false}).clickable(interactionSource=interactions,indication=null){val close=opened||offset>1f;swipe.close();drag=0f;if(!close)onOpen()}){
            Row(Modifier.padding(start=if(item.stopped)16.dp else 2.dp,end=12.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically){
                if(!item.stopped)ActionIcon(when{item.completed->Icons.Rounded.CheckCircle;else->Icons.Rounded.RadioButtonUnchecked},if(item.completed)"取消完成"else if(item.ended)"查看详情"else"标记完成",{swipe.close();if(item.ended)onOpen()else onToggle()},tint=titleColor)
                Column(Modifier.weight(1f).padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text(item.todo.title,Modifier.testTag("todo-title-${item.key}"),color=titleColor,fontSize=16.sp,fontWeight=FontWeight.Medium,maxLines=titleLines,overflow=TextOverflow.Ellipsis,lineHeight=23.sp)
                    item.due?.let{due->
                        val status=if(!item.completed&&!item.stopped&&!item.ended)TaskTime.status(due,now)else null
                        val accent=if(highlight)titleColor else MaterialTheme.colorScheme.onSurfaceVariant
                        if(overdue&&status!=null)Text(status,fontSize=13.sp,lineHeight=16.sp,color=accent)
                        TodoPlanTime(due,now,accent,item.key)
                        if(!overdue&&status!=null)Text(status,fontSize=13.sp,lineHeight=16.sp,color=accent)
                    }
                    if(item.completed)Text("完成 ${item.actual?.let{Dates.display(it)}?:"时间未记录"}",fontSize=13.sp,color=green)
                    if(item.stopped){
                        val lastStop=item.todo.stopRecords.lastOrNull()
                        Text("已中止 ${lastStop?.let{Dates.display(Dates.moment(it.stoppedAt))}?:"时间未记录"}",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        lastStop?.reason?.trim()?.takeIf{it.isNotEmpty()}?.let{Text("原因 $it",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)}
                    }
                    if(item.ended)Text("周期结束",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
@Composable private fun TodoPlanTime(due:LocalDateTime,now:LocalDateTime,color:Color,key:String){
    val relative=Dates.relative(due,now)
    val date=relative.substringBeforeLast(' ');val time=relative.substringAfterLast(' ')
    // Keep date/time tokens intact where possible, but allow a new line instead of ellipsis.
    val parts=if('年' in date)listOf(date.substringBefore('年')+"年",date.substringAfter('年'),time)else listOf(date,time)
    FlowRow(Modifier.fillMaxWidth().testTag("todo-plan-$key").semantics(mergeDescendants=true){contentDescription="计划完成 $relative"},horizontalArrangement=Arrangement.spacedBy(3.dp),verticalArrangement=Arrangement.spacedBy(0.dp)){
        parts.forEach{Text(it,fontSize=12.sp,lineHeight=16.sp,color=color)}
    }
}
@Composable private fun SwipeAction(title:String,icon:androidx.compose.ui.graphics.vector.ImageVector,color:Color,modifier:Modifier=Modifier,onClick:()->Unit){
    val interactions=remember{MutableInteractionSource()};val pressed by interactions.collectIsPressedAsState();val focused by interactions.collectIsFocusedAsState()
    val scale by animateFloatAsState(if(pressed).97f else 1f,tween(120),label="侧滑按钮按压")
    Surface(modifier=modifier.width(48.dp).heightIn(min=64.dp).graphicsLayer{scaleX=scale;scaleY=scale}.clip(RoundedCornerShape(14.dp)).then(if(focused)Modifier.border(1.dp,color,RoundedCornerShape(14.dp))else Modifier).clickable(interactionSource=interactions,indication=null,role=Role.Button,onClick=onClick),shape=RoundedCornerShape(14.dp),color=color.copy(alpha=if(pressed).18f else .1f),contentColor=color){
        Column(Modifier.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp,Alignment.CenterVertically)){Icon(icon,null,Modifier.size(21.dp));Text(title,fontSize=12.sp,lineHeight=16.sp)}
    }
}

@Composable fun CalendarPage(data:AppData,query:String,onOpen:(Occurrence)->Unit,onActual:(Occurrence)->Unit){
    var monthText by rememberSaveable{mutableStateOf(YearMonth.now().toString())};var selectedText by rememberSaveable{mutableStateOf(LocalDate.now().toString())}
    var previousQuery by rememberSaveable{mutableStateOf(query)};var locate by rememberSaveable{mutableStateOf(false)};var weekMode by rememberSaveable{mutableStateOf(false)}
    var originMonth by rememberSaveable{mutableStateOf(monthText)};var originDay by rememberSaveable{mutableStateOf(selectedText)}
    val month=YearMonth.parse(monthText);val selected=LocalDate.parse(selectedText)
    val start=if(weekMode)Dates.monday(selected)else month.atDay(1);val end=if(weekMode)start.plusDays(6)else month.atEndOfMonth()
    val range=remember(data.revision,start,end,query){data.todos.filter{query.isBlank()||it.title.contains(query,true)||it.content.contains(query,true)}.flatMap{Schedule.inRange(it,start.atStartOfDay(),end.atTime(23,59,59)).filter{value->!it.archived||value.completed}}}
    val signals=range.groupBy{it.due!!.toLocalDate()}.mapValues{(_,items)->when{items.all{it.completed}->Tone.Green;items.any{!it.completed&&!it.stopped&&it.due!!<LocalDateTime.now()}->Tone.Red;items.all{it.stopped}->MaterialTheme.colorScheme.outline;else->Tone.Blue}}
    val results=remember(data.revision,query){if(query.isBlank())emptyList()else data.todos.filter{it.title.contains(query,true)||it.content.contains(query,true)}.flatMap{task->
        if(task.type=="weekly"){val a=Dates.date(task.weeklyStartDate)?:LocalDate.now().minusYears(1);val b=Dates.date(task.weeklyEndDate)?:LocalDate.now().plusYears(1);Schedule.inRange(task,a.atStartOfDay(),b.plusYears(2).atTime(23,59)).filter{!task.archived||it.completed}.take(500)}else listOfNotNull(Schedule.occurrence(task))
    }.sortedByDescending{it.due}}
    LaunchedEffect(query){if(previousQuery.isEmpty()&&query.isNotEmpty()){originMonth=monthText;originDay=selectedText};if(query.isEmpty()&&previousQuery.isNotEmpty()){monthText=originMonth;selectedText=originDay};locate=false;previousQuery=query}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=16.dp,bottom=LocalChromeBottomInset.current+16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        if(query.isNotBlank()&&!locate){
            item{Text("搜索结果 ${results.size}项",fontWeight=FontWeight.Medium)}
            if(results.isEmpty())item{EmptyState("没有找到匹配事项")}
            items(results,key={it.key}){item->AgendaItem(item,onOpen={onOpen(item)},onActual={onActual(item)},onLocate=if(item.due!=null){{selectedText=item.due.toLocalDate().toString();monthText=YearMonth.from(item.due).toString();locate=true}}else null)}
        }else{
            item{FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text("${month.year}年${month.monthValue}月",Modifier.padding(end=8.dp,top=12.dp),fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                ActionIcon(Icons.Rounded.ChevronLeft,"上一${if(weekMode)"周"else"个月"}",{if(weekMode){selectedText=selected.minusWeeks(1).toString();monthText=YearMonth.from(LocalDate.parse(selectedText)).toString()}else{monthText=month.minusMonths(1).toString();selectedText=selected.minusMonths(1).toString()}})
                ActionIcon(Icons.Rounded.ChevronRight,"下一${if(weekMode)"周"else"个月"}",{if(weekMode){selectedText=selected.plusWeeks(1).toString();monthText=YearMonth.from(LocalDate.parse(selectedText)).toString()}else{monthText=month.plusMonths(1).toString();selectedText=selected.plusMonths(1).toString()}})
                TextButton({monthText=YearMonth.now().toString();selectedText=LocalDate.now().toString()}){Text("今天")}
                Choice(if(weekMode)"周"else"月",true,{weekMode=!weekMode})
            }}
            item{if(!weekMode)MonthGrid(month,selected,{selectedText=it.toString();monthText=YearMonth.from(it).toString()},signals)else Row{repeat(7){index->val day=Dates.monday(selected).plusDays(index.toLong());Surface(onClick={selectedText=day.toString();monthText=YearMonth.from(day).toString()},modifier=Modifier.weight(1f).heightIn(min=72.dp),shape=RoundedCornerShape(10.dp),color=if(day==selected)MaterialTheme.colorScheme.primaryContainer else Color.Transparent){Column(Modifier.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){Text("一二三四五六日"[index].toString(),fontSize=12.sp);Text(day.dayOfMonth.toString(),fontWeight=FontWeight.Medium)}}}}}
            val dayItems=range.filter{it.due?.toLocalDate()==selected}.sortedBy{it.due}
            item{HorizontalDivider();Row(Modifier.padding(top=16.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically){Text("${selected.monthValue}月${selected.dayOfMonth}日 · 周${"一二三四五六日"[selected.dayOfWeek.value-1]}",Modifier.weight(1f),fontWeight=FontWeight.SemiBold,fontSize=18.sp);Text("${dayItems.size}项",color=MaterialTheme.colorScheme.onSurfaceVariant)}}
            if(dayItems.isEmpty())item{EmptyState("这一天还没有安排")}
            items(dayItems,key={it.key}){item->AgendaItem(item,{onOpen(item)},{onActual(item)})}
        }
    }
}
@Composable fun AgendaItem(item:Occurrence,onOpen:()->Unit,onActual:()->Unit,onLocate:(()->Unit)?=null){
    var expanded by remember(item.key){mutableStateOf(false)};val timeline=Schedule.timeline(item)
    Surface(shape=appShape(14.dp,22.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Text(item.todo.title,Modifier.weight(1f).clickable(onClick=onOpen).padding(vertical=8.dp),fontWeight=FontWeight.Medium);StatusLabel(item)}
        item.due?.let{Text("计划完成  ${Dates.display(it)}",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item.stop?.let{record->
            Text("中止时间  ${Dates.display(Dates.moment(record.stoppedAt))}",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            record.resumedAt?.let{Text("恢复时间  ${Dates.display(Dates.moment(it))}",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(record.reason.isNotBlank())Text("中止原因  ${record.reason}",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(item.completed)Row(verticalAlignment=Alignment.CenterVertically){Text("实际完成  ${item.actual?.let{Dates.display(it)}?:"时间未记录"}",Modifier.weight(1f),fontSize=14.sp);ActionIcon(Icons.Rounded.Edit,"修改实际完成时间",onActual)}
        if(item.todo.content.isNotBlank())Text(item.todo.content,maxLines=3,overflow=TextOverflow.Ellipsis,fontSize=14.sp)
        if(timeline.isNotEmpty()){
            TextButton({expanded=!expanded}){Text("${if(timeline.any{it.edit})"时间记录"else"延期记录"} · ${timeline.size}次");Icon(if(expanded)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null)}
            if(!expanded)timeline.lastOrNull()?.reason?.takeIf{it.isNotBlank()}?.let{Text("延期原因  $it",fontSize=14.sp)}
            if(expanded)timeline.forEach{step->Text("${Dates.display(step.from)} → ${Dates.display(step.to)}",fontSize=13.sp);step.changed?.let{Text("${Dates.display(it)} · ${if(step.all)"全量"else"本次"}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};if(step.reason.isNotBlank())Text("延期原因  ${step.reason}",fontSize=14.sp)}
        }
        if(onLocate!=null)TextButton(onLocate){Text("查看日期")}
    }}
}

@Composable fun NotesPage(data:AppData,vm:MainViewModel,query:String,onOpen:(Note)->Unit,onEdit:(Note)->Unit,onTrash:(Note)->Unit){
    val configuration=LocalConfiguration.current
    val columns=if(configuration.screenWidthDp<360||configuration.fontScale>=1.5f)1 else 2
    val notes=data.notes.filter{query.isBlank()||it.text.contains(query,true)}
    val bounds=remember{mutableStateMapOf<String,Rect>()};val haptic=LocalHapticFeedback.current
    LazyVerticalStaggeredGrid(columns=StaggeredGridCells.Fixed(columns),modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=8.dp,bottom=LocalChromeBottomInset.current+96.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalItemSpacing=10.dp){
        if(notes.isEmpty())item(span=StaggeredGridItemSpan.FullLine){EmptyState(if(query.isBlank())"还没有随手记"else"没有找到匹配随手记")}
        listOf(true,false).forEach{pinned->
            val group=notes.filter{it.pinned==pinned};if(group.isNotEmpty()){
                item(key="heading-$pinned",span=StaggeredGridItemSpan.FullLine){Text(if(pinned)"置顶"else"全部随手记",Modifier.padding(top=8.dp,bottom=4.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                items(group,key={it.id}){note->
                    var drag by remember{mutableStateOf(Offset.Zero)};var dragging by remember{mutableStateOf(false)}
                    val modifier=Modifier.onGloballyPositioned{if(!dragging)bounds[note.id]=it.boundsInWindow()}.zIndex(if(dragging)2f else 0f).graphicsLayer{translationX=drag.x;translationY=drag.y;alpha=if(dragging).8f else 1f}.pointerInput(note.id){detectDragGesturesAfterLongPress(
                        onDragStart={dragging=true;drag=Offset.Zero;haptic.performHapticFeedback(HapticFeedbackType.LongPress)},onDragCancel={dragging=false;drag=Offset.Zero},
                        onDragEnd={val point=bounds[note.id]?.center?.plus(drag);val target=if(point==null)null else group.firstOrNull{it.id!=note.id&&bounds[it.id]?.contains(point)==true};dragging=false;drag=Offset.Zero;if(target!=null)vm.reorderNote(note,target.id)},
                        onDrag={change,amount->change.consume();drag+=amount})}
                    NoteCard(note,modifier,{onOpen(note)},{onEdit(note)},{vm.pin(note)},{vm.moveNote(note,-1)},{vm.moveNote(note,1)},{onTrash(note)})
                }
            }
        }
    }
}
@Composable fun NoteCard(note:Note,modifier:Modifier=Modifier,onOpen:()->Unit,onEdit:()->Unit,onPin:()->Unit,onUp:()->Unit,onDown:()->Unit,onTrash:()->Unit){
    var menu by remember{mutableStateOf(false)}
    val cardHeight=maxOf(208.dp,with(LocalDensity.current){(22.sp.toDp()*7)+54.dp})
    val dark=MaterialTheme.colorScheme.background.luminance()<.2f
    Surface(onClick=onOpen,modifier=modifier.fillMaxWidth().height(cardHeight).testTag("note-card-${note.id}").semantics{customActions=listOf(CustomAccessibilityAction("上移"){onUp();true},CustomAccessibilityAction("下移"){onDown();true})},shape=appShape(14.dp,22.dp),color=if(note.pinned&&LocalAppStyle.current==AppStyle.CLASSIC)MaterialTheme.colorScheme.primaryContainer.copy(alpha=.55f)else if(note.pinned){if(dark)Color(0xFF29261F)else Color(0xFFFFFBF0)}else MaterialTheme.colorScheme.surface,border=BorderStroke(.6.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.65f))){
        Box{
            Column(Modifier.padding(start=14.dp,end=40.dp,top=14.dp,bottom=14.dp)){
                if(note.pinned)Icon(Icons.Rounded.PushPin,"已置顶",Modifier.padding(bottom=6.dp).size(14.dp),tint=MaterialTheme.colorScheme.primary)
                Text(note.text,maxLines=7,overflow=TextOverflow.Ellipsis,lineHeight=22.sp,fontSize=15.sp)
            }
            Box(Modifier.align(Alignment.TopEnd)){IconButton({menu=true},Modifier.size(48.dp).semantics{contentDescription="随手记操作"}){Icon(Icons.Rounded.MoreHoriz,null,Modifier.size(20.dp))};DropdownMenu(menu,{menu=false}){
                listOf("编辑" to onEdit,(if(note.pinned)"取消置顶"else"置顶") to onPin,"上移" to onUp,"下移" to onDown,"移入废纸篓" to onTrash).forEach{(text,action)->DropdownMenuItem(text={Text(text)},onClick={menu=false;action()})}
            }}
        }
    }
}
