package cn.deskreminder.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cn.deskreminder.mobile.data.DueState

// Same warning/overdue palette as Windows 1.0.4.6, adjusted for the local surface.
@Composable fun dueStateColor(state:DueState):Color{
    val light=MaterialTheme.colorScheme.surface.luminance()>.25f
    return when(state){
        DueState.SOON->if(light)Color(0xFFA04D06)else Color(0xFFFFD160)
        DueState.OVERDUE->if(light)Color(0xFFB52539)else Color(0xFFFF7076)
        DueState.NORMAL->MaterialTheme.colorScheme.onSurface
    }
}
