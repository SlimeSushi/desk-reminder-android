@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.deskreminder.mobile.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import cn.deskreminder.mobile.data.*
import cn.deskreminder.mobile.data.model.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun ActionIcon(icon:ImageVector,label:String,onClick:()->Unit,enabled:Boolean=true,tint:Color=LocalContentColor.current){IconButton(onClick,Modifier.size(48.dp).semantics{contentDescription=label},enabled=enabled){Icon(icon,null,tint=tint,modifier=Modifier.size(22.dp))}}
@Composable fun Primary(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,danger:Boolean=false){Button(onClick,modifier.heightIn(min=50.dp),enabled=enabled,shape=if(LocalAppStyle.current==AppStyle.CLASSIC)RoundedCornerShape(12.dp)else CircleShape,colors=if(danger)ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)else ButtonDefaults.buttonColors()){Text(text,fontWeight=FontWeight.SemiBold)}}
@Composable fun Secondary(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true){
    if(LocalAppStyle.current==AppStyle.CLASSIC){OutlinedButton(onClick,modifier.heightIn(min=50.dp),enabled=enabled,shape=RoundedCornerShape(12.dp)){Text(text)};return}
    FilledTonalButton(onClick,modifier.heightIn(min=50.dp),enabled=enabled,shape=CircleShape,colors=ButtonDefaults.filledTonalButtonColors(containerColor=MaterialTheme.colorScheme.surfaceVariant,contentColor=MaterialTheme.colorScheme.primary)){Text(text,fontWeight=FontWeight.Medium)}
}
@Composable fun Choice(text:String,selected:Boolean,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true){
    Surface(onClick,modifier.heightIn(min=48.dp).semantics{this.selected=selected},enabled=enabled,shape=appShape(10.dp,14.dp),border=if(LocalAppStyle.current==AppStyle.CLASSIC)BorderStroke(1.dp,if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)else null,color=if(selected)MaterialTheme.colorScheme.primaryContainer else if(LocalAppStyle.current==AppStyle.CLASSIC)MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.65f)){
        Box(Modifier.padding(horizontal=12.dp,vertical=10.dp),contentAlignment=Alignment.Center){Text(text,color=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal,fontSize=15.sp)}
    }
}
@Composable fun Section(title:String,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    Surface(modifier.fillMaxWidth(),shape=appShape(16.dp,22.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.padding(horizontal=18.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){if(title.isNotBlank())Text(title,style=MaterialTheme.typography.titleMedium);content()}}
}
@Composable fun SettingRow(title:String,value:String="",onClick:(()->Unit)?=null,trailing:(@Composable () -> Unit)?=null){
    Row(Modifier.fillMaxWidth().heightIn(min=52.dp).then(if(onClick!=null)Modifier.clickable(onClick=onClick)else Modifier),verticalAlignment=Alignment.CenterVertically){
        Text(title,Modifier.weight(1f).padding(end=8.dp));if(trailing!=null)trailing()else{Text(value,color=MaterialTheme.colorScheme.onSurfaceVariant);if(onClick!=null)Icon(Icons.Rounded.ChevronRight,null,Modifier.padding(start=4.dp).size(22.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
@Composable fun SwitchRow(title:String,value:Boolean,onChange:(Boolean)->Unit,enabled:Boolean=true){
    val interactions=remember{MutableInteractionSource()};val pressed by interactions.collectIsPressedAsState();val focused by interactions.collectIsFocusedAsState()
    // Keep the semantic/click target and thumb feedback; omit the inherited full-row ink flash.
    Row(Modifier.fillMaxWidth().heightIn(min=54.dp).then(if(focused)Modifier.border(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.55f),RoundedCornerShape(14.dp))else Modifier).toggleable(value,interactionSource=interactions,indication=null,enabled=enabled,role=Role.Switch,onValueChange=onChange),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f).padding(end=12.dp));AppleToggle(value,enabled,pressed)}
}
@Composable fun Page(title:String,onBack:()->Unit,actions:@Composable RowScope.()->Unit={},bottom: @Composable () -> Unit={},snackbarHost:@Composable ()->Unit={},content:@Composable (PaddingValues)->Unit){
    if(LocalAppStyle.current==AppStyle.CLASSIC){
        Scaffold(topBar={TopAppBar(title={Text(title,fontWeight=FontWeight.SemiBold)},navigationIcon={ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack,"返回",onBack)},actions=actions,colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.surface))},bottomBar=bottom,snackbarHost=snackbarHost,containerColor=MaterialTheme.colorScheme.background,contentWindowInsets=WindowInsets.safeDrawing,content=content)
        return
    }
    GlassHost{source->Scaffold(topBar={CenterAlignedTopAppBar(title={Text(title,fontWeight=FontWeight.SemiBold,fontSize=18.sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)},navigationIcon={FilledTonalIconButton(onBack,Modifier.padding(start=8.dp).size(44.dp),shape=CircleShape,colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=MaterialTheme.colorScheme.surface)){Icon(Icons.AutoMirrored.Rounded.ArrowBack,"返回",Modifier.size(21.dp))}},actions=actions,colors=TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor=MaterialTheme.colorScheme.background))},bottomBar=bottom,snackbarHost=snackbarHost,containerColor=MaterialTheme.colorScheme.background,contentWindowInsets=WindowInsets.safeDrawing){padding->Box(Modifier.fillMaxSize().then(source)){content(padding)}}}
}
@Composable fun BottomActionBar(content:@Composable RowScope.()->Unit){GlassSurface(Modifier.fillMaxWidth(),RoundedCornerShape(topStart=24.dp,topEnd=24.dp)){Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal=20.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically,content=content)}}
@Composable fun FormFooter(onCancel:()->Unit,onSave:()->Unit,saving:Boolean=false,label:String="保存"){
    BottomActionBar{Secondary("取消",onCancel,Modifier.weight(1f),!saving);Primary(if(saving)"保存中…"else label,onSave,Modifier.weight(1f),!saving)}
}
@Composable fun MessageDialog(title:String,message:String,confirm:String="确定",danger:Boolean=false,onDismiss:()->Unit,onConfirm:()->Unit){
    AppAlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){Text(message)}},dismissButton={TextButton(onDismiss){Text("取消")}},confirmButton={TextButton(onConfirm,colors=ButtonDefaults.textButtonColors(contentColor=if(danger)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)){Text(confirm)}},shape=RoundedCornerShape(24.dp))
}
@Composable fun ResumeDialog(title:String,warning:String,onDismiss:()->Unit,onConfirm:()->Unit){
    AppAlertDialog(onDismissRequest=onDismiss,title={Text("恢复待办？")},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){Text("“$title”已经中止。");Spacer(Modifier.height(10.dp));Text(warning)}},dismissButton={TextButton(onDismiss){Text("暂不恢复")}},confirmButton={TextButton(onConfirm){Text("仍然恢复")}},shape=RoundedCornerShape(24.dp))
}
@Composable fun EmptyState(title:String,action:String?=null,onAction:()->Unit={}){Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=48.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){Text(title,color=MaterialTheme.colorScheme.onSurfaceVariant);if(action!=null)Secondary(action,onAction)}}
@Composable fun StatusLabel(item:Occurrence){val (text,color)=when{item.completed->"已完成" to (if(MaterialTheme.colorScheme.surface.luminance()<.2f)Color(0xFF86EFAC)else Tone.Green);item.stopped->"已中止" to MaterialTheme.colorScheme.onSurfaceVariant;item.ended->"周期结束" to MaterialTheme.colorScheme.onSurfaceVariant;item.due?.isBefore(LocalDateTime.now())==true->"已超时" to MaterialTheme.colorScheme.error;else->"待完成" to MaterialTheme.colorScheme.primary};Surface(color=color.copy(alpha=.10f),shape=RoundedCornerShape(7.dp)){Text(text,Modifier.padding(horizontal=8.dp,vertical=4.dp),color=color,fontSize=12.sp)}}

@Composable fun DateTimeField(label:String,value:LocalDateTime,onChange:(LocalDateTime)->Unit,enabled:Boolean=true){
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(label,style=MaterialTheme.typography.labelLarge)
        DateOnlyField("日期",value.toLocalDate(),{onChange(LocalDateTime.of(it,value.toLocalTime()))})
        TimeField(value.toLocalTime(),{onChange(LocalDateTime.of(value.toLocalDate(),it))},enabled)
    }
}
@Composable fun TimeField(value:LocalTime,onChange:(LocalTime)->Unit,enabled:Boolean=true){
    var open by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        Box(Modifier.semantics{contentDescription="选择时间";stateDescription=if(open)"已展开"else"已收起"}){
            SelectionRow("时间",value.format(DateTimeFormatter.ofPattern("HH:mm")),Icons.Rounded.Schedule,{if(enabled)open=!open})
        }
        if(open&&enabled){
            InlineTimePicker(value,onChange)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton({open=false}){Icon(Icons.Rounded.Check,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("收起时间选择")}}
        }
    }
}
@Composable fun DateOnlyField(label:String,value:LocalDate,onChange:(LocalDate)->Unit){var open by remember{mutableStateOf(false)};Box(Modifier.semantics{contentDescription="选择日期"}){SelectionRow(label,value.format(DateTimeFormatter.ofPattern("uuuu年M月d日")),Icons.Rounded.CalendarToday,{open=true})};if(open)DateChooser(value,{open=false}){open=false;onChange(it)}}

@Composable fun MonthGrid(month:YearMonth,selected:LocalDate,onSelect:(LocalDate)->Unit,signals:Map<LocalDate,Color> = emptyMap()){
    val first=month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value-1).toLong());val rows=(month.lengthOfMonth()+month.atDay(1).dayOfWeek.value-2)/7+1
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){Column(Modifier.requiredWidth(maxOf(maxWidth,308.dp))){
        Row{listOf("一","二","三","四","五","六","日").forEach{Box(Modifier.weight(1f).height(32.dp),Alignment.Center){Text(it,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
        repeat(rows){r->Row{repeat(7){c->val day=first.plusDays((r*7+c).toLong());val chosen=day==selected;val today=day==LocalDate.now()
            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(10.dp)).background(if(chosen)MaterialTheme.colorScheme.primaryContainer else Color.Transparent).clickable{onSelect(day)}.semantics{contentDescription="${day} ${if(chosen)"已选择"else""}"},Alignment.Center){
                Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(32.dp).then(if(chosen)Modifier.background(MaterialTheme.colorScheme.primary,CircleShape)else if(today)Modifier.border(1.dp,MaterialTheme.colorScheme.primary,CircleShape)else Modifier),Alignment.Center){Text(day.dayOfMonth.toString(),fontSize=14.sp,color=if(chosen)MaterialTheme.colorScheme.onPrimary else if(day.month!=month.month)MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.45f)else MaterialTheme.colorScheme.onSurface)}
                    Box(Modifier.size(4.dp).background(signals[day]?:Color.Transparent,CircleShape))}
            }
        }}}
    }}
}
@Composable fun DateChooser(initial:LocalDate,onDismiss:()->Unit,onConfirm:(LocalDate)->Unit){
    var selected by remember{mutableStateOf(initial)};var month by remember{mutableStateOf(YearMonth.from(initial))};var years by remember{mutableStateOf(false)}
    androidx.compose.ui.window.Dialog(onDismissRequest=onDismiss,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.padding(4.dp).widthIn(max=420.dp).fillMaxWidth(),shape=RoundedCornerShape(30.dp)){
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical=12.dp)){
                Text("选择日期",Modifier.padding(horizontal=20.dp,vertical=8.dp),style=MaterialTheme.typography.titleLarge)
                Row(Modifier.padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){ActionIcon(Icons.Rounded.ChevronLeft,"上个月",{month=month.minusMonths(1)});TextButton({years=true},Modifier.weight(1f)){Text("${month.year}年${month.monthValue}月")};ActionIcon(Icons.Rounded.ChevronRight,"下个月",{month=month.plusMonths(1)})}
                Box(Modifier.padding(horizontal=2.dp)){MonthGrid(month,selected,{selected=it;month=YearMonth.from(it)})}
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){TextButton({selected=LocalDate.now();month=YearMonth.now()}){Text("今天")};Spacer(Modifier.weight(1f));TextButton(onDismiss){Text("取消")};TextButton({onConfirm(selected)}){Text("确定")}}
            }
        }
    }
    if(years)AppAlertDialog(onDismissRequest={years=false},confirmButton={TextButton({years=false}){Text("关闭")}},title={Text("选择年份")},text={val state=rememberLazyListState((month.year-1900).coerceIn(0,200));LazyColumn(Modifier.heightIn(max=360.dp),state){items((1900..2100).toList()){year->TextButton({month=month.withYear(year);years=false},Modifier.fillMaxWidth()){Text("${year}年")}}}})
}
fun durationLabel(minutes:Int,lead:Boolean=false):String=when{minutes==0->if(lead)"到点提醒"else"只提醒一次";minutes%1440==0->"${minutes/1440}天";minutes%60==0->"${minutes/60}小时";else->"${minutes}分钟"}
@Composable fun DurationChooser(title:String,initial:Int,lead:Boolean=false,onDismiss:()->Unit,onConfirm:(Int)->Unit){
    val options=if(lead)listOf(0,5,10,15,30,60,120,1440)else listOf(0,10,30,60,120,360,1440)
    var selected by remember{mutableIntStateOf(if(initial in options)initial else -1)}
    var unit by remember{mutableIntStateOf(if(initial>0&&initial%1440==0)1440 else if(initial>0&&initial%60==0)60 else 1)}
    var quantity by remember{mutableStateOf(if(initial>0)(initial/unit).toString()else"30")};var error by remember{mutableStateOf("")};var units by remember{mutableStateOf(false)}
    FormSheet(title,onDismiss,{val count=quantity.toLongOrNull();val value=if(selected>=0)selected.toLong()else count?.takeIf{it>0}?.times(unit);if(value==null||value>Int.MAX_VALUE||lead&&value>525600)error="请输入有效的提醒时间"else onConfirm(value.toInt())}){
        options.forEach{value->RadioOption(durationLabel(value,lead),selected==value,{selected=value;error=""})}
        HorizontalDivider(Modifier.padding(vertical=12.dp));Text("自定义",Modifier.padding(bottom=8.dp),fontWeight=FontWeight.Medium)
        NumberControl(quantity,{quantity=it;selected=-1;error=""},when(unit){1440->"天";60->"小时";else->"分钟"})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton({units=true}){Text("单位："+when(unit){1440->"天";60->"小时";else->"分钟"});Icon(Icons.Rounded.ExpandMore,null)};DropdownMenu(units,{units=false}){listOf(1 to "分钟",60 to "小时",1440 to "天").forEach{(value,label)->DropdownMenuItem(text={Text(label)},onClick={unit=value;selected=-1;units=false})}}}
        if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
    }
}

@Composable fun RuleEditor(rule:ReminderRule,onChange:(ReminderRule)->Unit,onReset:(()->Unit)?=null,showTitle:Boolean=true){
    var field by remember{mutableStateOf("")}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(showTitle)Row(verticalAlignment=Alignment.CenterVertically){Text("提醒设置",Modifier.weight(1f),fontWeight=FontWeight.SemiBold);if(onReset!=null)TextButton(onReset){Text("还原默认")}}
        SwitchRow("启用提醒",rule.enabled,{onChange(rule.copy(enabled=it))})
        SettingRow("提前提醒",durationLabel(rule.advanceMinutes,true),{field="lead"})
        SettingRow("重复间隔",durationLabel(rule.repeatMinutes),{field="repeat"})
        HorizontalDivider(Modifier.padding(vertical=4.dp))
        SwitchRow("通知提醒",rule.popup,{onChange(rule.copy(popup=it))})
        SwitchRow("提示音",rule.sound,{onChange(rule.copy(sound=it))})
        SwitchRow("振动",rule.vibrate,{onChange(rule.copy(vibrate=it))})
    }
    if(field.isNotEmpty())DurationChooser(if(field=="lead")"提前提醒"else"重复间隔",if(field=="lead")rule.advanceMinutes else rule.repeatMinutes,field=="lead",{field=""}){value->onChange(if(field=="lead")rule.copy(advanceMinutes=value)else rule.copy(repeatMinutes=value));field=""}
}
