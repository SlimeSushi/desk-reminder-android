package cn.deskreminder.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.*

@Composable fun InlineTimePicker(value:LocalTime,onChange:(LocalTime)->Unit){
    var hour by remember{mutableIntStateOf(value.hour)};var minute by remember{mutableIntStateOf(value.minute)}
    LaunchedEffect(value){hour=value.hour;minute=value.minute}
    val rowHeight=maxOf(40.dp,with(LocalDensity.current){28.sp.toDp()}+8.dp)
    Column(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Row(Modifier.widthIn(max=300.dp).fillMaxWidth().padding(bottom=4.dp)){
            Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Text("时",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Spacer(Modifier.width(24.dp))
            Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Text("分",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        Box(Modifier.widthIn(max=300.dp).fillMaxWidth().height(rowHeight*5),contentAlignment=Alignment.Center){
            Box(Modifier.fillMaxWidth().height(rowHeight).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.65f),RoundedCornerShape(10.dp)))
            Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically){
                TimeWheel(24,hour,rowHeight,"小时滚轮",Modifier.weight(1f)){hour=it;onChange(LocalTime.of(hour,minute))}
                Text(":",Modifier.width(24.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontSize=24.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                TimeWheel(60,minute,rowHeight,"分钟滚轮",Modifier.weight(1f)){minute=it;onChange(LocalTime.of(hour,minute))}
            }
        }
    }
}
@Composable internal fun TimeWheel(count:Int,value:Int,rowHeight:Dp,label:String,modifier:Modifier,display:(Int)->String={"%02d".format(it)},onChange:(Int)->Unit){
    val state=rememberLazyListState(value);val scope=rememberCoroutineScope();val latest by rememberUpdatedState(onChange)
    val centered by remember(state){derivedStateOf{
        val layout=state.layoutInfo;val middle=(layout.viewportStartOffset+layout.viewportEndOffset)/2
        layout.visibleItemsInfo.minByOrNull{abs(it.offset+it.size/2-middle)}?.index?:state.firstVisibleItemIndex
    }}
    LaunchedEffect(state){snapshotFlow{state.isScrollInProgress to centered}.filter{!it.first}.map{it.second}.distinctUntilChanged().collect{latest(it.coerceIn(0,count-1))}}
    LaunchedEffect(value){if(!state.isScrollInProgress&&centered!=value)state.scrollToItem(value)}
    LazyColumn(state=state,flingBehavior=rememberSnapFlingBehavior(state),contentPadding=PaddingValues(vertical=rowHeight*2),modifier=modifier.fillMaxHeight().testTag(when(count){24->"time-hour-wheel";60->"time-minute-wheel";else->"weekday-wheel"}).semantics{
        contentDescription=label;stateDescription=display(value)
        customActions=listOf(CustomAccessibilityAction("增加"){scope.launch{state.animateScrollToItem((value+1).coerceAtMost(count-1))};true},CustomAccessibilityAction("减少"){scope.launch{state.animateScrollToItem((value-1).coerceAtLeast(0))};true})
    }){
        items(count){number->val distance=abs(centered-number)
            Box(Modifier.fillMaxWidth().height(rowHeight),contentAlignment=Alignment.Center){
                Text(display(number),fontSize=if(count==7){if(distance==0)20.sp else 18.sp}else if(distance==0)26.sp else 22.sp,fontWeight=if(distance==0)FontWeight.SemiBold else FontWeight.Normal,color=if(distance==0)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=if(distance==1).55f else .24f))
            }
        }
    }
}
