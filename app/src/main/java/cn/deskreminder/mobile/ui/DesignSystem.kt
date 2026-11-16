@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.deskreminder.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.testTag

@Composable fun FloatingTabs(selected:Int,onSelect:(Int)->Unit){
    val labels=listOf("待办","日历","随手记","设置")
    val icons=listOf(Icons.Rounded.CheckCircleOutline,Icons.Rounded.CalendarMonth,Icons.AutoMirrored.Rounded.StickyNote2,Icons.Rounded.Tune)
    if(LocalAppStyle.current==AppStyle.CLASSIC){
        NavigationBar(Modifier.testTag("classic-tab-bar"),containerColor=MaterialTheme.colorScheme.surface){
            labels.forEachIndexed{index,label->NavigationBarItem(selected=selected==index,onClick={onSelect(index)},icon={Icon(icons[index],null)},label={Text(label)},colors=NavigationBarItemDefaults.colors(selectedIconColor=MaterialTheme.colorScheme.primary,selectedTextColor=MaterialTheme.colorScheme.primary,indicatorColor=MaterialTheme.colorScheme.primaryContainer,unselectedIconColor=MaterialTheme.colorScheme.onSurfaceVariant,unselectedTextColor=MaterialTheme.colorScheme.onSurfaceVariant))}
        }
        return
    }
    val dark=MaterialTheme.colorScheme.background.luminance()<.2f
    val chromeText=if(dark)Color(0xFFD2D2D7)else Color(0xFF54545B)
    val interactions=remember{List(4){MutableInteractionSource()}}
    val foreground=remember{mutableStateListOf<Rect>().apply{repeat(8){add(Rect.Zero)}}}
    val presses=interactions.map{it.collectIsPressedAsState().value}
    val pressure by animateFloatAsState(if(presses.any{it})1f else 0f,spring(dampingRatio=.9f,stiffness=1800f),label="玻璃触摸深度")
    val textDensity=androidx.compose.ui.platform.LocalDensity.current
    val height=if(textDensity.fontScale>=1.5f)maxOf(60.dp,with(textDensity){16.sp.toDp()*2+40.dp})else 60.dp
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=16.dp,vertical=10.dp),contentAlignment=Alignment.Center){
        GlassSurface(Modifier.widthIn(max=520.dp).fillMaxWidth(),CircleShape,press=pressure,foregroundSlots=4,foregroundAreas=foreground){
            BoxWithConstraints(Modifier.fillMaxWidth().padding(4.dp)){
                val gap=4.dp;val itemWidth=(maxWidth-gap*3)/4
                val offset by animateDpAsState((itemWidth+gap)*selected,spring(dampingRatio=.86f,stiffness=430f),label="滑动玻璃选中态")
                val lens=if(dark)listOf(Color(0xFF80B5FF).copy(alpha=.18f),Color(0xFF3A73BA).copy(alpha=.16f))else listOf(Color(0xFFE9F4FF).copy(alpha=.84f),Color(0xFFC8E1FF).copy(alpha=.72f))
                Box(Modifier.offset{IntOffset(offset.roundToPx(),0)}.width(itemWidth).height(height).testTag("liquid-tab-lens").shadow(3.dp,CircleShape,clip=false,ambientColor=Tone.Blue.copy(alpha=.09f),spotColor=Tone.Blue.copy(alpha=.12f)).background(Brush.verticalGradient(lens),CircleShape))
                Row(Modifier.fillMaxWidth().selectableGroup(),horizontalArrangement=Arrangement.spacedBy(gap)){
                    labels.forEachIndexed{index,label->
                        val chosen=selected==index
                        Column(Modifier.weight(1f).height(height).clip(CircleShape).selectable(chosen,role=Role.Tab,interactionSource=interactions[index],indication=LocalIndication.current,onClick={onSelect(index)}).padding(horizontal=2.dp,vertical=5.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                            Icon(icons[index],null,Modifier.size(23.dp).onGloballyPositioned{foreground[index*2]=it.boundsInRoot()},tint=if(chosen)MaterialTheme.colorScheme.primary else chromeText)
                            Spacer(Modifier.height(3.dp));Text(label,modifier=Modifier.onGloballyPositioned{foreground[index*2+1]=it.boundsInRoot()},fontSize=12.sp,lineHeight=16.sp,maxLines=2,textAlign=TextAlign.Center,fontWeight=if(chosen)FontWeight.SemiBold else FontWeight.Medium,color=if(chosen)MaterialTheme.colorScheme.primary else chromeText)
                        }
                    }
                }
            }
        }
    }
}
@Composable fun <T> SegmentedControl(options:List<Pair<T,String>>,selected:T,onSelect:(T)->Unit,modifier:Modifier=Modifier){
    if(LocalAppStyle.current==AppStyle.CLASSIC){
        Row(modifier.fillMaxWidth().selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            options.forEach{(value,label)->Choice(label,selected==value,{onSelect(value)},Modifier.weight(1f))}
        }
        return
    }
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(3.dp)){
        options.forEach{(value,label)->val chosen=selected==value
            Surface(Modifier.weight(1f).heightIn(min=44.dp).selectable(chosen,role=Role.Tab,onClick={onSelect(value)}),shape=RoundedCornerShape(12.dp),shadowElevation=if(chosen)1.dp else 0.dp,color=if(chosen)MaterialTheme.colorScheme.surface else Color.Transparent){Box(Modifier.padding(horizontal=6.dp,vertical=9.dp),contentAlignment=Alignment.Center){Text(label,style=MaterialTheme.typography.labelLarge,fontWeight=if(chosen)FontWeight.SemiBold else FontWeight.Medium,color=if(chosen)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)}}
        }
    }
}
@Composable fun AppleToggle(value:Boolean,enabled:Boolean=true,pressed:Boolean=false){
    if(LocalAppStyle.current==AppStyle.CLASSIC){
        Switch(value,null,modifier=Modifier.testTag("classic-switch"),enabled=enabled,colors=SwitchDefaults.colors(checkedTrackColor=MaterialTheme.colorScheme.primary,checkedThumbColor=MaterialTheme.colorScheme.onPrimary))
        return
    }
    val thumb by animateDpAsState(if(value)22.dp else 2.dp,label="开关位置")
    val track by animateColorAsState(if(value)Tone.Blue else MaterialTheme.colorScheme.outlineVariant,label="开关轨道")
    val pressure by animateFloatAsState(if(pressed)1f else 0f,spring(dampingRatio=.9f,stiffness=500f),label="开关触摸反馈")
    Box(Modifier.size(52.dp,32.dp).clip(CircleShape).background(track.copy(alpha=if(enabled)1f else .4f))){Box(Modifier.offset{IntOffset(thumb.roundToPx(),2.dp.roundToPx())}.size(28.dp).graphicsLayer{scaleX=1f+pressure*.05f;scaleY=1f-pressure*.03f}.shadow(2.dp,CircleShape).background(Brush.verticalGradient(listOf(Color.White,Color(0xFFEAF2FF))),CircleShape))}
}
@Composable fun DeskSlider(value:Float,onValueChange:(Float)->Unit,valueRange:ClosedFloatingPointRange<Float> = 0f..100f,onValueChangeFinished:(()->Unit)?=null){
    if(LocalAppStyle.current==AppStyle.CLASSIC){Slider(value,onValueChange,valueRange=valueRange,onValueChangeFinished=onValueChangeFinished,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp));return}
    val fraction=((value-valueRange.start)/(valueRange.endInclusive-valueRange.start)).coerceIn(0f,1f)
    val active=MaterialTheme.colorScheme.primary;val inactive=MaterialTheme.colorScheme.outlineVariant
    Slider(value,onValueChange,valueRange=valueRange,onValueChangeFinished=onValueChangeFinished,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
        thumb={Box(Modifier.size(24.dp).shadow(2.dp,CircleShape).background(Color.White,CircleShape).border(.6.dp,Color.Black.copy(alpha=.10f),CircleShape))},
        track={Canvas(Modifier.fillMaxWidth().height(4.dp)){drawRoundRect(inactive,cornerRadius=androidx.compose.ui.geometry.CornerRadius(size.height/2));drawRoundRect(active,size=androidx.compose.ui.geometry.Size(size.width*fraction,size.height),cornerRadius=androidx.compose.ui.geometry.CornerRadius(size.height/2))}})
}
@Composable fun AppTextField(value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,readOnly:Boolean=false,label:(@Composable ()->Unit)?=null,placeholder:(@Composable ()->Unit)?=null,leadingIcon:(@Composable ()->Unit)?=null,trailingIcon:(@Composable ()->Unit)?=null,supportingText:(@Composable ()->Unit)?=null,isError:Boolean=false,singleLine:Boolean=false,minLines:Int=1,maxLines:Int=if(singleLine)1 else Int.MAX_VALUE,shape:Shape=RoundedCornerShape(16.dp),keyboardOptions:KeyboardOptions=KeyboardOptions.Default,keyboardActions:KeyboardActions=KeyboardActions.Default,textStyle:TextStyle=MaterialTheme.typography.bodyLarge,visualTransformation:androidx.compose.ui.text.input.VisualTransformation=androidx.compose.ui.text.input.VisualTransformation.None){
    OutlinedTextField(value,onValueChange,modifier,enabled,readOnly,textStyle,label,placeholder,leadingIcon,trailingIcon,supportingText=supportingText,visualTransformation=visualTransformation,isError=isError,singleLine=singleLine,minLines=minLines,maxLines=maxLines,shape=if(LocalAppStyle.current==AppStyle.CLASSIC)shape else RoundedCornerShape(16.dp),keyboardOptions=keyboardOptions,keyboardActions=keyboardActions,
        colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=MaterialTheme.colorScheme.surface,focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedBorderColor=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.7f),focusedBorderColor=MaterialTheme.colorScheme.primary,unfocusedLabelColor=MaterialTheme.colorScheme.onSurfaceVariant))
}
@Composable fun AppAlertDialog(onDismissRequest:()->Unit,confirmButton:@Composable ()->Unit,modifier:Modifier=Modifier,dismissButton:(@Composable ()->Unit)?=null,icon:(@Composable ()->Unit)?=null,title:(@Composable ()->Unit)?=null,text:(@Composable ()->Unit)?=null,shape:Shape=RoundedCornerShape(28.dp),properties:DialogProperties=DialogProperties()){
    androidx.compose.material3.AlertDialog(onDismissRequest,confirmButton,modifier.widthIn(max=440.dp),dismissButton,icon,title,text,shape=appShape(24.dp,28.dp),containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp,titleContentColor=MaterialTheme.colorScheme.onSurface,textContentColor=MaterialTheme.colorScheme.onSurfaceVariant,properties=properties)
}
