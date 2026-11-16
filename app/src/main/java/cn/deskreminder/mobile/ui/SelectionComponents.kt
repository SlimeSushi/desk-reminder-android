@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.deskreminder.mobile.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*

@Composable fun WeekdayButtons(weekday:Int,onChange:(Int)->Unit){
    Column(Modifier.fillMaxWidth().selectableGroup(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("每周重复日",style=MaterialTheme.typography.labelLarge)
        listOf((1..4).toList(),(5..7).toList()).forEachIndexed{row,days->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                if(row==1)Spacer(Modifier.weight(.5f))
                days.forEach{day->Choice("周${"一二三四五六日"[day-1]}",weekday==day,{onChange(day)},Modifier.weight(1f).semantics{selected=weekday==day;role=Role.RadioButton;contentDescription="星期${"一二三四五六日"[day-1]}"})}
                if(row==1)Spacer(Modifier.weight(.5f))
            }
        }
    }
}

@Composable fun FormSheet(title:String,onDismiss:()->Unit,onConfirm:()->Unit,confirm:String="完成",danger:Boolean=false,enabled:Boolean=true,content:@Composable ColumnScope.()->Unit){
    val state=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=state,sheetMaxWidth=560.dp,containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp,shape=if(LocalAppStyle.current==AppStyle.CLASSIC)RoundedCornerShape(topStart=24.dp,topEnd=24.dp)else RoundedCornerShape(topStart=32.dp,topEnd=32.dp),dragHandle={Box(Modifier.padding(top=10.dp,bottom=14.dp).size(36.dp,4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.35f),RoundedCornerShape(2.dp)))}){
        Column(Modifier.fillMaxWidth().imePadding()){
            Text(title,Modifier.padding(start=24.dp,end=24.dp,bottom=16.dp),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(horizontal=20.dp),content=content)
            Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Secondary("取消",onDismiss,Modifier.weight(1f),enabled);Primary(confirm,onConfirm,Modifier.weight(1f),enabled,danger=danger)
            }
        }
    }
}
@Composable fun RadioOption(label:String,selected:Boolean,onSelect:()->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=52.dp).selectable(selected=selected,role=Role.RadioButton,onClick=onSelect).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically){
        Text(label,Modifier.weight(1f),fontWeight=if(selected)FontWeight.Medium else FontWeight.Normal)
        if(selected)Icon(Icons.Rounded.Check,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))else Spacer(Modifier.size(22.dp))
    }
}
@Composable fun <T> SingleChoiceSheet(title:String,initial:T,options:List<Pair<T,String>>,onDismiss:()->Unit,onConfirm:(T)->Unit){
    var selected by remember{mutableStateOf(initial)}
    FormSheet(title,onDismiss,{onConfirm(selected)}){options.forEach{(value,label)->RadioOption(label,selected==value,{selected=value})}}
}
@Composable fun SelectionRow(title:String,value:String,icon:ImageVector?=null,onClick:()->Unit){
    val focus=androidx.compose.ui.platform.LocalFocusManager.current;val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Surface(onClick={focus.clearFocus();keyboard?.hide();onClick()},shape=appShape(12.dp,16.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(horizontal=14.dp,vertical=12.dp).heightIn(min=32.dp),verticalAlignment=Alignment.CenterVertically){
            if(icon!=null){Icon(icon,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.width(10.dp))}
            Text(title,Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=15.sp)
            Text(value,fontWeight=FontWeight.Medium);Icon(Icons.Rounded.ExpandMore,null,Modifier.padding(start=6.dp).size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun NumberControl(value:String,onChange:(String)->Unit,unit:String,enabled:Boolean=true){
    Surface(color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.65f),shape=appShape(12.dp,16.dp)){
        Row(Modifier.heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton({onChange(((value.toLongOrNull()?:1)-1).coerceAtLeast(1).toString())},enabled=enabled){Icon(Icons.Rounded.Remove,"减少")}
            BasicTextField(value,{if(it.all(Char::isDigit)&&it.length<=9)onChange(it)},Modifier.weight(1f).semantics{contentDescription="自定义数量"},enabled=enabled,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),textStyle=TextStyle(fontSize=20.sp,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurface),cursorBrush=SolidColor(MaterialTheme.colorScheme.primary))
            Text(unit,Modifier.padding(start=8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton({onChange(((value.toLongOrNull()?:0)+1).coerceAtMost(999999999).toString())},enabled=enabled){Icon(Icons.Rounded.Add,"增加")}
        }
    }
}
@Composable fun DaysChooser(title:String,initial:Int,zero:String,onDismiss:()->Unit,onConfirm:(Int)->Unit){
    var selected by remember{mutableIntStateOf(if(initial in listOf(0,7,30,90))initial else -1)}
    var custom by remember{mutableStateOf((initial.takeIf{it>0&&it !in listOf(7,30,90)}?:30).toString())};var error by remember{mutableStateOf("")}
    FormSheet(title,onDismiss,{val days=if(selected>=0)selected else custom.toIntOrNull();if(days==null||days<0||selected<0&&days==0)error="请输入有效的正整数天数"else onConfirm(days)}){
        listOf(0 to zero,7 to "近7天",30 to "近30天",90 to "近90天").forEach{(value,label)->RadioOption(label,selected==value,{selected=value;error=""})}
        HorizontalDivider(Modifier.padding(vertical=12.dp))
        Row(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalAlignment=Alignment.CenterVertically){Text("自定义",Modifier.weight(1f),fontWeight=FontWeight.Medium);if(selected==-1)Icon(Icons.Rounded.Check,null,tint=MaterialTheme.colorScheme.primary)}
        NumberControl(custom,{custom=it;selected=-1;error=""},"天")
        if(error.isNotEmpty())Text(error,Modifier.padding(top=8.dp),color=MaterialTheme.colorScheme.error,fontSize=13.sp)
    }
}
