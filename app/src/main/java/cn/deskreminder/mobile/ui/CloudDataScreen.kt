package cn.deskreminder.mobile.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import cn.deskreminder.mobile.MainViewModel
import cn.deskreminder.mobile.data.cloud.*
import com.google.gson.JsonParser
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun CloudDataScreen(vm:MainViewModel,onBack:()->Unit){
    val state by vm.app.cloud.state.collectAsState()
    val data by vm.data.collectAsState()
    val context=LocalContext.current
    CloudConnectionContent(state,vm.app.cloud.profile,CloudData.conflicts(data?.syncTracking?.takeIf{it.targetKey==NutstoreTransport.target(vm.app.cloud.profile)}),onBack,
        {vm.app.cloud.checkAndSave(it)},{vm.app.cloud.request()},{vm.app.cloud.setAutomatic(it)},
        {vm.app.cloud.cancel()},{vm.app.cloud.confirmMerge(it)}, {key,index->vm.app.cloud.resolve(key,index)},
        {runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(NutstoreTransport.HELP)))}})
}

// Password drafts are deliberately memory-only; credentials never enter saved screen state.
@Composable fun CloudConnectionContent(state:CloudUiState,profile:CloudProfile,conflicts:List<SyncRecord>,onBack:()->Unit,
    onCheck:(CloudProfile)->Unit,onSync:()->Unit,onAutomatic:(Boolean)->Unit,onCancel:()->Unit,onMerge:(Boolean)->Unit,
    onResolve:(String,Int)->Unit,onHelp:()->Unit,configurationOnly:Boolean=false){
    var account by remember(profile.account){mutableStateOf(profile.account)}
    var password by remember(profile.password){mutableStateOf(profile.password)}
    var folder by remember(profile.folder){mutableStateOf(profile.folder)}
    var showPassword by remember{mutableStateOf(false)}
    var validation by remember{mutableStateOf("")}
    var checking by remember{mutableStateOf(false)}
    LaunchedEffect(state.busy,state.status){if(checking&&!state.busy){checking=false;if(!state.error)onBack()}}
    var selected by remember{mutableStateOf<Pair<String,Int>?>(null)}
    val dirty=account.trim()!=profile.account||password!=profile.password||folder.trim()!=profile.folder
    Page(if(configurationOnly)"坚果云配置"else"云端数据",onBack){padding->Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Section("坚果云"){
            AppTextField(account,{account=it;validation=""},Modifier.fillMaxWidth(),enabled=!state.busy,label={Text("账号")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
            AppTextField(password,{password=it;validation=""},Modifier.fillMaxWidth(),enabled=!state.busy,label={Text("第三方应用密码")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),
                visualTransformation=if(showPassword)VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon={IconButton({showPassword=!showPassword}){Icon(if(showPassword)Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,if(showPassword)"隐藏密码"else"显示密码")}})
            AppTextField(folder,{folder=it;validation=""},Modifier.fillMaxWidth(),enabled=!state.busy,label={Text("同步文件夹")},singleLine=true)
            if(validation.isNotEmpty())Text(validation,color=MaterialTheme.colorScheme.error)
            Primary(if(state.busy)"正在处理…"else"保存并检查连接",{
                val edited=CloudProfile(account=account.trim(),password=password,folder=folder.trim())
                runCatching{NutstoreTransport.validate(edited)}.onSuccess{checking=true;onCheck(edited)}.onFailure{validation=it.message?:"请检查填写内容"}
            },Modifier.fillMaxWidth(),enabled=!state.busy)
            TextButton(onHelp,Modifier.fillMaxWidth()){Text("坚果云如何配置");Icon(Icons.Rounded.OpenInNew,null,Modifier.padding(start=6.dp).size(16.dp))}
        }
        if(!configurationOnly)Section("同步"){
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(state.status,color=if(state.error)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if(state.lastSuccess.isNotEmpty())Text("上次同步  "+displayCloudTime(state.lastSuccess),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            SwitchRow("自动同步",state.automatic,onAutomatic,enabled=state.connected&&!state.busy&&!dirty)
            if(state.busy)Secondary("取消",onCancel,Modifier.fillMaxWidth())else Primary("立即同步",onSync,Modifier.fillMaxWidth(),enabled=state.connected&&!dirty)
        }
        if(!configurationOnly&&conflicts.isNotEmpty())Section("待处理冲突 · ${conflicts.size}"){
            conflicts.forEach{record->
                Text("同一条内容在不同设备发生了修改",style=MaterialTheme.typography.titleSmall)
                record.values.forEachIndexed{index,value->
                    Text(cloudVersionSummary(value))
                    Text(displayCloudTime(value.changedUtc),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Secondary("采用此版本",{selected=record.key to index},Modifier.fillMaxWidth(),enabled=!state.busy&&!dirty)
                    HorizontalDivider()
                }
            }
        }
    }}
    if(!configurationOnly)state.preview?.let{preview->AppAlertDialog(onDismissRequest={onMerge(false)},title={Text("首次合并云端数据")},text={Text("本机 ${preview.localTasks} 项待办 · ${preview.localNotes} 张随手记\n合并后 ${preview.tasks} 项待办 · ${preview.notes} 张随手记\n冲突 ${preview.conflicts} 项\n\n先保存同步前备份。冲突保留双方版本，待你选择。")},confirmButton={TextButton({onMerge(true)}){Text("合并并同步")}},dismissButton={TextButton({onMerge(false)}){Text("取消")}})}
    selected?.let{(key,index)->AppAlertDialog(onDismissRequest={selected=null},title={Text("采用这个版本？")},text={Text("此选择将同步到其他设备。当前内容会先保存到同步前备份。")},confirmButton={TextButton({selected=null;onResolve(key,index)}){Text("采用")}},dismissButton={TextButton({selected=null}){Text("取消")}})}
}
private fun displayCloudTime(value:String)=runCatching{Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))}.getOrDefault(value)
private fun cloudVersionSummary(value:SyncValue):String{
    if(value.deleted)return "已删除"
    return runCatching{
        val json=JsonParser.parseString(value.json).asJsonObject
        if(json.has("Note")){val note=json["Note"].asJsonObject;(if(json["InTrash"].asBoolean)"废纸篓 · "else "")+note["Text"].asString}
        else listOf(json["Title"]?.asString.orEmpty(),json["Content"]?.asString.orEmpty(),json["DueAt"]?.asString.orEmpty(),if(json["Type"]?.asString=="weekly")"每周 ${json["Weekday"].asInt} · ${json["WeeklyTime"].asString}\n${json["WeeklyStartDate"].asString} 至 ${json["WeeklyEndDate"].asString}"else "",if(json["Done"]?.asBoolean==true)"已完成  "+json["CompletedAt"]?.asString.orEmpty()else if(json["Stopped"]?.asBoolean==true)"已中止"else "待完成","延期 ${(json["Postponements"]?.asJsonArray?.size()?:0)+(json["WeeklyPostponements"]?.asJsonArray?.size()?:0)} 次").filter{it.isNotEmpty()}.joinToString("\n")
    }.getOrDefault("内容版本").take(1500)
}
