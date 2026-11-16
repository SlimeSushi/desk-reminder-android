package cn.deskreminder.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.android.awaitFrame

@Composable fun HomeHeader(tab:Int,query:String,searching:Boolean,onSearch:(String)->Unit,onOpenSearch:()->Unit,onCloseSearch:()->Unit,actions:@Composable RowScope.()->Unit){
    val title=listOf("待办","日历","随手记","设置")[tab]
    val classic=LocalAppStyle.current==AppStyle.CLASSIC
    val large=LocalConfiguration.current.fontScale>=1.5f
    Surface(color=if(classic)MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background){
        Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min=if(classic&&tab!=3)64.dp else 76.dp).padding(horizontal=if(classic&&tab==3)16.dp else 20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Text(title,fontSize=if(large)20.sp else if(classic&&tab!=3)22.sp else 30.sp,lineHeight=if(large)26.sp else 38.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.4).sp,maxLines=1)
            if(tab==3)Spacer(Modifier.weight(1f))else Box(Modifier.weight(1f)){
                HeaderSearch(title,query,searching||tab==1,tab==1,onSearch,onOpenSearch,onCloseSearch)
            }
            actions()
        }
    }
}
@Composable private fun HeaderSearch(label:String,value:String,expanded:Boolean,always:Boolean,onChange:(String)->Unit,onOpen:()->Unit,onClose:()->Unit){
    val focus=remember{FocusRequester()};val manager=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    fun close(){manager.clearFocus();keyboard?.hide();onClose()}
    BackHandler(enabled=expanded&&!always){close()}
    LaunchedEffect(expanded){if(expanded&&!always){awaitFrame();focus.requestFocus();keyboard?.show()}}
    if(!expanded)Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.CenterEnd){ActionIcon(Icons.Rounded.Search,"搜索$label",onOpen)}
    else Surface(Modifier.fillMaxWidth().heightIn(min=44.dp),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(.6.dp,MaterialTheme.colorScheme.outlineVariant)){
        Row(Modifier.padding(start=12.dp),verticalAlignment=Alignment.CenterVertically){
            BasicTextField(value,onChange,Modifier.weight(1f).padding(vertical=10.dp).focusRequester(focus).testTag("header-search-$label").semantics{contentDescription="${label}搜索"},singleLine=true,
                textStyle=MaterialTheme.typography.bodyMedium.copy(color=MaterialTheme.colorScheme.onSurface),cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={manager.clearFocus();keyboard?.hide()}),
                decorationBox={inner->Box{if(value.isEmpty())Text(if(always)"搜索标题或内容"else"搜索$label",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis);inner()}})
            if(always&&value.isEmpty())Icon(Icons.Rounded.Search,null,Modifier.padding(horizontal=12.dp).size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
            else IconButton({if(value.isNotEmpty())onChange("")else close()},Modifier.size(44.dp).semantics{contentDescription=if(value.isNotEmpty())"清除${label}搜索"else"关闭${label}搜索"}){Icon(Icons.Rounded.Close,null,Modifier.size(20.dp))}
        }
    }
}
