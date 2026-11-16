@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.deskreminder.mobile.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import cn.deskreminder.mobile.MainViewModel
import cn.deskreminder.mobile.data.*
import cn.deskreminder.mobile.data.model.*
import kotlinx.coroutines.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

@Composable fun BackupPage(data:AppData,vm:MainViewModel,onBack:()->Unit,onImport:()->Unit,onExport:()->Unit,onRestore:(BackupInfo)->Unit){
    var files by remember{mutableStateOf<List<BackupInfo>>(emptyList())};var refresh by remember{mutableIntStateOf(0)}
    var retentionPicker by remember{mutableStateOf(false)}
    var retention by remember{mutableStateOf<Pair<Int,List<BackupInfo>>?>(null)};var animating by remember{mutableStateOf(false)}
    val busy by vm.busy.collectAsState()
    LaunchedEffect(data.revision,refresh){files=withContext(Dispatchers.IO){vm.repo.listBackups()}}
    LaunchedEffect(busy){if(!busy&&animating){animating=false;refresh++}}
    fun retain(days:Int){var preview=emptyList<BackupInfo>();vm.operation(onDone={if(preview.isEmpty())refresh++ else retention=days to preview}){preview=vm.repo.cleanupCandidates(days);if(preview.isEmpty())vm.repo.setRetention(days)}}
    Page("备份与恢复",onBack,bottom={
        Section("自动备份已开启",Modifier.testTag("backup-actions").navigationBarsPadding().padding(horizontal=20.dp,vertical=12.dp)){
            Primary(if(animating)"备份中…"else"立即备份",{if(!busy&&!animating){animating=true;vm.operation("已手动备份",{refresh++;animating=false}){val start=System.currentTimeMillis();vm.repo.manualBackup();delay((2000-(System.currentTimeMillis()-start)).coerceAtLeast(0))}}},Modifier.fillMaxWidth(),!busy&&!animating)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Secondary("分享当前备份",onExport,Modifier.weight(1f),!busy);Secondary("导入备份",onImport,Modifier.weight(1f),!busy)}
        }
    }){padding->LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("backup-history"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Section("备份策略"){SettingRow("自动备份保留",if(data.settings.backupRetentionDays==0)"永久"else"${data.settings.backupRetentionDays}天",{retentionPicker=true})}}
        item{Row(verticalAlignment=Alignment.CenterVertically){Text("历史备份 ${files.size}份",Modifier.weight(1f),fontWeight=FontWeight.SemiBold);Text(formatBytes(files.sumOf{it.bytes}),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        if(animating)item("animation"){QuillAnimation()}
        else if(files.isEmpty())item{EmptyState("还没有备份")}
        else items(files,key={it.file.name}){info->Surface(onClick={onRestore(info)},modifier=Modifier.testTag("backup-entry-${info.file.name}"),enabled=!busy,shape=appShape(14.dp,20.dp),color=MaterialTheme.colorScheme.surface){
            Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(info.at.format(DateTimeFormatter.ofPattern("M月d日 HH:mm:ss.SSS")),fontSize=14.sp,fontWeight=FontWeight.Medium)
                Text("${info.todos}项待办 · ${info.notes}张随手记${if(info.trash>0)" · 废纸篓${info.trash}张"else""}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            };Text(info.label,fontSize=12.sp,color=MaterialTheme.colorScheme.primary)}
        }}
    }}
    if(retentionPicker)DaysChooser("自动备份保留",data.settings.backupRetentionDays,"永久",{retentionPicker=false}){days->retentionPicker=false;retain(days)}
    retention?.let{(days,preview)->MessageDialog("清理过期自动备份？","应用保留${days}天将删除${preview.size}份自动备份，共${formatBytes(preview.sumOf{it.bytes})}。\n\n手动备份与恢复前保护备份保留，至少保留最新一份自动备份。规则生效后会继续自动清理超期自动备份。","确认清理",true,{retention=null}){vm.operation("保留规则已更新",{retention=null;refresh++}){vm.repo.setRetention(days,preview.map{it.file.name}.toSet())}}}
}
fun formatBytes(bytes:Long):String=if(bytes<1024*1024)"%.1f KB".format(bytes/1024.0)else"%.1f MB".format(bytes/1048576.0)

@Composable fun BackupRestoreDialog(current:AppData?,backup:ImportedData,label:String,busy:Boolean,onDismiss:()->Unit,syncAfter:Boolean=false,onRestore:()->Unit){
    AppAlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(if(label.isNotBlank())"恢复这份备份？"else"导入备份")},text={
        Column(Modifier.fillMaxWidth().heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)){
            if(label.isNotBlank())Text(label,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("将用这份备份整体替换当前待办、随手记和废纸篓，不会合并。",color=MaterialTheme.colorScheme.onSurface,fontSize=15.sp,lineHeight=22.sp)
            BackupComparison(current,backup.data)
            Text("恢复前会自动创建保护备份。本机外观和提醒配置保留。"+(if(syncAfter)"恢复后将同步到其他设备，离线时等待联网。"else"" ),fontSize=14.sp,lineHeight=20.sp)
        }
    },dismissButton={TextButton(onDismiss,enabled=!busy){Text("取消")}},confirmButton={TextButton(onRestore,enabled=!busy){Text(if(busy)"恢复中…"else if(syncAfter)"恢复并同步"else"恢复到本机")}})
}

@Composable fun BackupRestoreDialog(current:AppData?,backup:ImportedData,label:String,busy:Boolean,onDismiss:()->Unit,onRestore:()->Unit){
    BackupRestoreDialog(current,backup,label,busy,onDismiss,syncAfter=false,onRestore=onRestore)
}

@Composable private fun BackupComparison(current:AppData?,backup:AppData){
    val large=LocalDensity.current.fontScale>=1.5f
    val rows=listOf(Triple("待办记录",current?.todos?.size,backup.todos.size),Triple("随手记",current?.notes?.size,backup.notes.size),Triple("废纸篓",current?.noteTrash?.size,backup.noteTrash.size))
    Surface(Modifier.fillMaxWidth().testTag("restore-comparison"),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.65f)){
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            if(!large)Row(Modifier.fillMaxWidth()){
                Text("记录",Modifier.weight(1.2f),fontSize=13.sp)
                Text("当前",Modifier.weight(1f),fontSize=13.sp,textAlign=androidx.compose.ui.text.style.TextAlign.End)
                Text("备份",Modifier.weight(1f),fontSize=13.sp,textAlign=androidx.compose.ui.text.style.TextAlign.End,color=MaterialTheme.colorScheme.primary)
            }
            rows.forEachIndexed{index,(title,before,after)->
                if(index>0)HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.6f))
                val beforeText=before?.toString()?:"不可读"
                val values:@Composable RowScope.()->Unit={
                    Text(if(large)"当前 $beforeText"else beforeText,Modifier.weight(1f).testTag("restore-current-$index"),fontSize=15.sp,fontWeight=FontWeight.Medium,textAlign=androidx.compose.ui.text.style.TextAlign.End)
                    Text(if(large)"备份 $after"else after.toString(),Modifier.weight(1f).testTag("restore-backup-$index"),fontSize=15.sp,fontWeight=FontWeight.SemiBold,textAlign=androidx.compose.ui.text.style.TextAlign.End,color=MaterialTheme.colorScheme.primary)
                }
                if(large)Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(title,fontSize=15.sp,fontWeight=FontWeight.Medium)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),content=values)
                }else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text(title,Modifier.weight(1.2f),fontSize=14.sp,fontWeight=FontWeight.Medium)
                    values()
                }
            }
        }
    }
}

@Composable fun QuillAnimation(){
    val progress=remember{Animatable(0f)};LaunchedEffect(Unit){progress.animateTo(1f,tween(2000,easing=LinearEasing))}
    Column(Modifier.fillMaxWidth().height(240.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        Canvas(Modifier.size(190.dp,170.dp)){
            val left=size.width*.18f;val top=size.height*.12f;val paperW=size.width*.66f;val paperH=size.height*.76f
            drawRoundRect(Color.White,Offset(left,top),Size(paperW,paperH),CornerRadius(12.dp.toPx()))
            drawRoundRect(Color(0xFFDDE3EB),Offset(left,top),Size(paperW,paperH),CornerRadius(12.dp.toPx()),style=Stroke(1.dp.toPx()))
            val t=(progress.value*3).coerceAtMost(2.999f);val row=t.toInt();val fraction=t-row
            repeat(3){line->val amount=if(line<row)1f else if(line==row)fraction else 0f;val y=top+paperH*(.35f+line*.2f);val path=Path();val x0=left+paperW*.14f
                path.moveTo(x0,y);repeat((amount*80).toInt()){step->val x=x0+paperW*.68f*step/80;path.lineTo(x,y+sin(step*.5f)*2.dp.toPx())};drawPath(path,Tone.Blue,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
            }
            val point=Offset(left+paperW*.14f+paperW*.68f*fraction,top+paperH*(.35f+row*.2f))
            rotate(28f,point){
                val feather=Path().apply{moveTo(point.x,point.y);cubicTo(point.x-22.dp.toPx(),point.y-34.dp.toPx(),point.x-15.dp.toPx(),point.y-83.dp.toPx(),point.x+17.dp.toPx(),point.y-96.dp.toPx());cubicTo(point.x+32.dp.toPx(),point.y-62.dp.toPx(),point.x+27.dp.toPx(),point.y-29.dp.toPx(),point.x,point.y);close()}
                drawPath(feather,Color(0xFF8FB2D1));drawLine(Color(0xFF4B7599),point,Offset(point.x+12.dp.toPx(),point.y-82.dp.toPx()),2.dp.toPx(),StrokeCap.Round)
                drawLine(Color(0xFF8E6E4A),point,Offset(point.x-2.dp.toPx(),point.y+7.dp.toPx()),3.dp.toPx(),StrokeCap.Round)
            }
        }
        Text("正在生成备份",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp)
    }
}
