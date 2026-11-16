package cn.deskreminder.mobile.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat

object Tone {val Blue=Color(0xFF0064D9);val Green=Color(0xFF1E7B34);val Red=Color(0xFFBA2532)}
private val Light=lightColorScheme(primary=Tone.Blue,onPrimary=Color.White,primaryContainer=Color(0xFFE6EFFF),onPrimaryContainer=Tone.Blue,background=Color(0xFFF2F2F7),onBackground=Color(0xFF1C1C1E),surface=Color.White,onSurface=Color(0xFF1C1C1E),surfaceVariant=Color(0xFFEBEBF0),onSurfaceVariant=Color(0xFF696972),outline=Color(0xFFBDBDC5),outlineVariant=Color(0xFFE2E2E8),error=Tone.Red)
private val Dark=darkColorScheme(primary=Color(0xFF91BDFF),onPrimary=Color(0xFF001A3D),primaryContainer=Color(0xFF1E3454),onPrimaryContainer=Color(0xFFD6E6FF),background=Color(0xFF000000),onBackground=Color(0xFFF5F5F7),surface=Color(0xFF1C1C1E),onSurface=Color(0xFFF5F5F7),surfaceVariant=Color(0xFF2C2C2E),onSurfaceVariant=Color(0xFFAEAEB5),outline=Color(0xFF63636B),outlineVariant=Color(0xFF3A3A3F),error=Color(0xFFFFA4AB))
private val ClassicLight=lightColorScheme(primary=Color(0xFF2563EB),onPrimary=Color.White,primaryContainer=Color(0xFFEFF6FF),onPrimaryContainer=Color(0xFF1D4ED8),background=Color(0xFFF8FAFC),onBackground=Color(0xFF202938),surface=Color.White,onSurface=Color(0xFF202938),surfaceVariant=Color(0xFFF0F3F7),onSurfaceVariant=Color(0xFF667085),outline=Color(0xFFDDE3EB),outlineVariant=Color(0xFFE7EBF0),error=Color(0xFFB42318))
private val ClassicDark=darkColorScheme(primary=Color(0xFF9BBEFF),onPrimary=Color(0xFF12254D),primaryContainer=Color(0xFF223754),onPrimaryContainer=Color(0xFFD8E7FF),background=Color(0xFF11151C),onBackground=Color(0xFFE8EDF5),surface=Color(0xFF1B222D),onSurface=Color(0xFFE8EDF5),surfaceVariant=Color(0xFF252F3D),onSurfaceVariant=Color(0xFFB1BCCB),outline=Color(0xFF465265),outlineVariant=Color(0xFF303B4B),error=Color(0xFFFFACA4))
enum class AppStyle { CLASSIC, FRUIT }
val LocalAppStyle=staticCompositionLocalOf{AppStyle.FRUIT}
@Composable fun appShape(classic: Dp,fruit: Dp)=RoundedCornerShape(if(LocalAppStyle.current==AppStyle.CLASSIC)classic else fruit)

@Composable fun DeskTheme(theme:String,style:String="fruit",content: @Composable () -> Unit){
    val classic=style=="classic"
    val dark=theme=="dark"||(theme=="system"&&isSystemInDarkTheme());val view=LocalView.current
    SideEffect{(view.context as? Activity)?.window?.let{WindowCompat.getInsetsController(it,view).apply{isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark}}}
    val base=Typography()
    val palette=if(classic){val colors=if(dark)ClassicDark else ClassicLight;colors.copy(secondary=colors.primary,secondaryContainer=colors.primaryContainer,onSecondaryContainer=colors.onPrimaryContainer,surfaceContainer=colors.surface,surfaceContainerLow=colors.background,surfaceContainerHigh=colors.surfaceVariant,surfaceContainerHighest=colors.surfaceVariant)}else if(dark)Dark.copy(secondary=Dark.primary,secondaryContainer=Dark.primaryContainer,onSecondaryContainer=Dark.onPrimaryContainer,surfaceContainer=Dark.surface,surfaceContainerLow=Dark.background,surfaceContainerHigh=Dark.surface,surfaceContainerHighest=Dark.surfaceVariant)else Light.copy(secondary=Tone.Blue,secondaryContainer=Light.primaryContainer,onSecondaryContainer=Tone.Blue,surfaceContainer=Light.surface,surfaceContainerLow=Light.background,surfaceContainerHigh=Light.surface,surfaceContainerHighest=Light.surfaceVariant)
    val type=base.copy(
        headlineLarge=base.headlineLarge.copy(fontFamily=FontFamily.SansSerif,fontSize=34.sp,lineHeight=41.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.6).sp),
        headlineSmall=base.headlineSmall.copy(fontSize=25.sp,lineHeight=32.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.3).sp),
        titleLarge=base.titleLarge.copy(fontSize=23.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.3).sp),
        titleMedium=base.titleMedium.copy(fontSize=17.sp,lineHeight=23.sp,fontWeight=FontWeight.SemiBold,letterSpacing=0.sp),
        bodyLarge=base.bodyLarge.copy(fontFamily=FontFamily.SansSerif,fontSize=17.sp,lineHeight=25.sp,letterSpacing=0.sp),
        bodyMedium=base.bodyMedium.copy(fontSize=15.sp,lineHeight=22.sp,letterSpacing=0.sp),
        bodySmall=base.bodySmall.copy(fontSize=13.sp,lineHeight=19.sp,letterSpacing=0.sp),
        labelLarge=base.labelLarge.copy(fontSize=15.sp,lineHeight=21.sp,fontWeight=FontWeight.Medium,letterSpacing=0.sp),
        labelMedium=base.labelMedium.copy(fontSize=13.sp,lineHeight=18.sp,fontWeight=FontWeight.Medium,letterSpacing=0.sp))
    val typography=if(classic)base.copy(bodyLarge=base.bodyLarge.copy(fontFamily=FontFamily.SansSerif,fontSize=16.sp),bodyMedium=base.bodyMedium.copy(fontSize=14.sp),titleLarge=base.titleLarge.copy(fontSize=24.sp))else type
    CompositionLocalProvider(LocalAppStyle provides if(classic)AppStyle.CLASSIC else AppStyle.FRUIT){
    MaterialTheme(colorScheme=palette,typography=typography,shapes=if(classic)Shapes()else Shapes(extraSmall=RoundedCornerShape(12.dp),small=RoundedCornerShape(16.dp),medium=RoundedCornerShape(22.dp),large=RoundedCornerShape(28.dp),extraLarge=RoundedCornerShape(32.dp)),content=content)
    }
}
