package cn.deskreminder.mobile.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.input.key.*

@Composable fun ColorChooser(title:String,initial:String,onDismiss:()->Unit,onApply:(String)->Unit){
    var hex by remember{mutableStateOf(initial.uppercase())};var hue by remember{mutableFloatStateOf(210f)};var saturation by remember{mutableFloatStateOf(.7f)};var value by remember{mutableFloatStateOf(.8f)}
    var error by remember{mutableStateOf("")};var rgbError by remember{mutableStateOf(false)}
    fun setColor(color:Int){hex="#%06X".format(color and 0xffffff);val hsv=FloatArray(3);android.graphics.Color.colorToHSV(color,hsv);hue=hsv[0];saturation=hsv[1];value=hsv[2];error="";rgbError=false}
    fun hsv(){hex="#%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(hue,saturation,value)) and 0xffffff);error="";rgbError=false}
    LaunchedEffect(Unit){setColor(android.graphics.Color.parseColor(initial))}
    val color=runCatching{Color(android.graphics.Color.parseColor(hex))}.getOrDefault(Color.Gray)
    androidx.compose.ui.window.Dialog(onDismissRequest=onDismiss,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)){
    BoxWithConstraints(Modifier.fillMaxWidth().imePadding().padding(16.dp),contentAlignment=Alignment.Center){
    Surface(Modifier.widthIn(max=420.dp).fillMaxWidth().heightIn(max=maxHeight),shape=appShape(24.dp,30.dp)){
    Column{
    Text(title,Modifier.padding(start=20.dp,end=20.dp,top=20.dp,bottom=16.dp),style=MaterialTheme.typography.titleLarge)
    Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            val hueColor=Color(android.graphics.Color.HSVToColor(floatArrayOf(hue,1f,1f)))
            Canvas(Modifier.fillMaxWidth().height(160.dp).pointerInput(hue){detectDragGestures(onDragStart={point->saturation=(point.x/size.width).coerceIn(0f,1f);value=(1-point.y/size.height).coerceIn(0f,1f);hsv()}){change,_->change.consume();saturation=(change.position.x/size.width).coerceIn(0f,1f);value=(1-change.position.y/size.height).coerceIn(0f,1f);hsv()}}.pointerInput(hue){detectTapGestures{point->saturation=(point.x/size.width).coerceIn(0f,1f);value=(1-point.y/size.height).coerceIn(0f,1f);hsv()}}){
                drawRect(Brush.horizontalGradient(listOf(Color.White,hueColor)));drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black)));drawCircle(Color.White,7.dp.toPx(),Offset(saturation*size.width,(1-value)*size.height),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
            HueSlider(hue,{hue=it;hsv()})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Box(Modifier.weight(1f).height(40.dp).background(Color(android.graphics.Color.parseColor(initial)),RoundedCornerShape(10.dp)).clickable{setColor(android.graphics.Color.parseColor(initial))})
            Box(Modifier.weight(1f).height(40.dp).background(color,RoundedCornerShape(10.dp)))
        }
        AppTextField(hex,{raw->hex=raw.uppercase();if(raw.matches(Regex("#?[0-9a-fA-F]{6}")))setColor(android.graphics.Color.parseColor(if(raw.startsWith("#"))raw else "#$raw"))},label={Text("HEX")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            val components=listOf((color.red*255).toInt(),(color.green*255).toInt(),(color.blue*255).toInt())
            components.forEachIndexed{index,current->var text by remember(hex,index){mutableStateOf(current.toString())};AppTextField(text,{text=it;val number=it.toIntOrNull();rgbError=number==null||number !in 0..255;if(!rgbError){val all=components.toMutableList();all[index]=number!!;setColor(android.graphics.Color.rgb(all[0],all[1],all[2]))}},label={Text(listOf("R","G","B")[index])},singleLine=true,modifier=Modifier.weight(1f),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))}
        }
        if(rgbError)Text("RGB需为0–255的整数",color=MaterialTheme.colorScheme.error)
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
    }
    Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Secondary("取消",onDismiss,Modifier.weight(1f))
        Primary("应用颜色",{if(hex.matches(Regex("#[0-9A-F]{6}")))onApply(hex)else error="请输入有效的HEX色值，例如 #2563EB"},Modifier.weight(1.5f),!rgbError)
    }
    }
    }
    }
    }
}

/** Continuous hue track with the same value for touch, keyboard and accessibility. */
@Composable private fun HueSlider(hue:Float,onChange:(Float)->Unit){
    val stops=remember{(0..6).map{Color.hsv(it*60f,1f,1f)}}
    val latest by rememberUpdatedState(onChange)
    Canvas(Modifier.fillMaxWidth().height(48.dp).testTag("hue-slider").semantics{
        contentDescription="色相";progressBarRangeInfo=ProgressBarRangeInfo(hue,0f..360f)
        setProgress{latest(it.coerceIn(0f,360f));true}
    }.onKeyEvent{event->if(event.type==KeyEventType.KeyDown&&event.key in listOf(Key.DirectionLeft,Key.DirectionRight)){latest((hue+if(event.key==Key.DirectionRight)1f else -1f).coerceIn(0f,360f));true}else false}.focusable().pointerInput(Unit){
        awaitEachGesture{
            val down=awaitFirstDown();val inset=12.dp.toPx()
            fun change(x:Float){latest(((x-inset)/(size.width-2*inset).coerceAtLeast(1f)*360).coerceIn(0f,360f))}
            change(down.position.x);down.consume()
            do{val event=awaitPointerEvent();val pointer=event.changes.firstOrNull{it.id==down.id}?:break;change(pointer.position.x);pointer.consume()}while(pointer.pressed)
        }
    }){
        val radius=12.dp.toPx();val width=(size.width-2*radius).coerceAtLeast(1f);val y=size.height/2;val x=radius+width*hue/360
        drawRoundRect(Brush.horizontalGradient(stops,startX=radius,endX=size.width-radius),topLeft=Offset(radius,y-6.dp.toPx()),size=androidx.compose.ui.geometry.Size(width,12.dp.toPx()),cornerRadius=androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
        drawCircle(Color.Black.copy(alpha=.15f),radius+1.dp.toPx(),Offset(x,y+1.dp.toPx()));drawCircle(Color.White,radius,Offset(x,y));drawCircle(Color.hsv(hue,1f,1f),radius-3.dp.toPx(),Offset(x,y))
    }
}
