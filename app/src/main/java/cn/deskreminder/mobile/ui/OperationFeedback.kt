package cn.deskreminder.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay

const val OPERATION_FEEDBACK_MILLIS=1000L
@Composable fun OperationMessage(text:String,modifier:Modifier=Modifier,error:Boolean=false){
    Surface(modifier.widthIn(max=480.dp).fillMaxWidth().testTag("operation-feedback").semantics{liveRegion=LiveRegionMode.Polite},shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=6.dp,border=BorderStroke(.6.dp,MaterialTheme.colorScheme.outlineVariant)){
        Row(Modifier.padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Icon(if(error)Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,null,Modifier.size(22.dp),tint=if(error)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(text,color=MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable fun OperationMessageHost(host:SnackbarHostState,modifier:Modifier=Modifier){
    val data=host.currentSnackbarData
    Box(modifier,contentAlignment=Alignment.Center){if(data!=null){
        LaunchedEffect(data){delay(OPERATION_FEEDBACK_MILLIS);data.dismiss()}
        OperationMessage(data.visuals.message,Modifier.padding(horizontal=16.dp,vertical=8.dp))
    }
    }
}
@Composable fun DesktopOperationFeedback(text:String,onDismiss:()->Unit,error:Boolean=false){
    LaunchedEffect(text){delay(OPERATION_FEEDBACK_MILLIS);onDismiss()}
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal=20.dp,vertical=24.dp),contentAlignment=Alignment.BottomCenter){OperationMessage(text,error=error)}
}
