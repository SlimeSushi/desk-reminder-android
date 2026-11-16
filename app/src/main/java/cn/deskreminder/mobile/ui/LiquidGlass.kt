package cn.deskreminder.mobile.ui

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*

private class GlassBackdrop(val layer:GraphicsLayer){var origin by mutableStateOf(Offset.Zero);var screen by mutableStateOf(IntSize(1,1))}
private val LocalGlassBackdrop=staticCompositionLocalOf<GlassBackdrop?>{null}
val LocalChromeBottomInset=staticCompositionLocalOf{0.dp}

/** Record only the app's content. Functional chrome must remain outside the source. */
@Composable fun GlassHost(content:@Composable (Modifier)->Unit){
    if(LocalAppStyle.current==AppStyle.CLASSIC){content(Modifier);return}
    val layer=rememberGraphicsLayer();val backdrop=remember(layer){GlassBackdrop(layer)}
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop){
        content(Modifier.onGloballyPositioned{backdrop.origin=it.positionInRoot();backdrop.screen=it.findRootCoordinates().size}.drawWithContent{
            layer.record{this@drawWithContent.drawContent()};drawLayer(layer)
        })
    }
}

/** Regular functional material; all foreground labels remain in a separate, sharp layer. */
@Composable fun GlassSurface(modifier:Modifier=Modifier,shape:Shape=RoundedCornerShape(28.dp),press:Float=0f,foregroundSlots:Int=0,foregroundAreas:List<androidx.compose.ui.geometry.Rect> = emptyList(),content:@Composable ()->Unit){
    if(LocalAppStyle.current==AppStyle.CLASSIC){Surface(modifier.testTag("classic-functional-surface"),color=MaterialTheme.colorScheme.surface,shadowElevation=2.dp,tonalElevation=0.dp,content=content);return}
    val dark=MaterialTheme.colorScheme.background.luminance()<.2f
    val backdrop=LocalGlassBackdrop.current;val view=LocalView.current
    val inspection=LocalInspectionMode.current;val density=LocalDensity.current
    val direction=LocalLayoutDirection.current
    val supported=Build.VERSION.SDK_INT>=31&&backdrop!=null&&view.isHardwareAccelerated&&!inspection
    val optics=remember(supported){if(supported&&Build.VERSION.SDK_INT>=33)runCatching{LiquidGlassOptics()}.getOrNull()else null}
    var origin by remember{mutableStateOf(Offset.Zero)}
    var touching by remember{mutableStateOf(false)}
    val touchPressure by animateFloatAsState(if(touching)1f else 0f,spring(dampingRatio=.9f,stiffness=1800f),label="玻璃按压收缩")
    val pressure=maxOf(press,touchPressure).coerceIn(0f,1f)
    val surface=if(dark)Color(0xFF242429)else Color(0xFFF8FAFF)
    Surface(modifier.onGloballyPositioned{origin=it.positionInRoot()}.graphicsLayer{scaleX=1f-pressure*.015f;scaleY=1f-pressure*.015f}.testTag("liquid-glass-surface").pointerInput(Unit){
        awaitEachGesture{
            awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial);touching=true
            try{do{val event=awaitPointerEvent(PointerEventPass.Initial)}while(event.changes.any{it.pressed})}finally{touching=false}
        }
    }.shadow(9.dp,shape,clip=false,ambientColor=Color.Black.copy(alpha=.12f),spotColor=Color.Black.copy(alpha=.13f)),shape=shape,color=Color.Transparent,tonalElevation=0.dp){
        Box{
            if(supported)Box(Modifier.matchParentSize().graphicsLayer{
                val radius=GlassMaterial.BLUR_DP.dp.toPx()
                renderEffect=if(Build.VERSION.SDK_INT>=33&&optics!=null){
                    val outline=shape.createOutline(size,direction,density)
                    val radii=if(outline is Outline.Rounded)outline.roundRect.let{floatArrayOf(it.topLeftCornerRadius.x,it.topRightCornerRadius.x,it.bottomRightCornerRadius.x,it.bottomLeftCornerRadius.x)}else floatArrayOf(0f,0f,0f,0f)
                    val regions=foregroundAreas.filter{it.width>0&&it.height>0}.take(8).flatMap{listOf(it.left-origin.x,it.top-origin.y,it.right-origin.x,it.bottom-origin.y)}.toFloatArray()
                    optics.configure(size.width,size.height,radii,this.density,dark,pressure,origin.x,origin.y,backdrop!!.screen.width.toFloat(),backdrop.screen.height.toFloat(),foregroundSlots.toFloat(),regions)
                    optics.effect(radius).asComposeRenderEffect()
                }else BlurEffect(radius,radius,TileMode.Clamp)
            }.drawBehind{val offset=backdrop!!.origin-origin;translate(offset.x,offset.y){drawLayer(backdrop.layer)}})
            if(!supported||optics==null){
                // Legacy/inspection windows use a diffused material, never unblurred translucent text.
                Box(Modifier.matchParentSize().drawWithCache{
                    val outline=shape.createOutline(size,direction,density)
                    val radii=if(outline is Outline.Rounded)outline.roundRect.let{floatArrayOf(it.topLeftCornerRadius.x,it.topRightCornerRadius.x,it.bottomRightCornerRadius.x,it.bottomLeftCornerRadius.x)}else floatArrayOf(0f,0f,0f,0f)
                    val image=StaticGlassFrame.bitmap(size.width.toInt(),size.height.toInt(),radii,surface.toArgb(),this.density,if(supported).62f else .88f).asImageBitmap()
                    onDrawBehind{drawImage(image,dstSize=IntSize(size.width.toInt().coerceAtLeast(1),size.height.toInt().coerceAtLeast(1)))}
                })
            }
            content()
        }
    }
}
