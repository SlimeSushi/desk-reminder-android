package cn.deskreminder.mobile.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

object BackupSharing {
    internal fun prepare(context:Context,json:String,uriForFile:(File)->android.net.Uri={file->FileProvider.getUriForFile(context,context.packageName+".backup-files",file)}):Intent {
        val folder=File(context.cacheDir,"shared-backups").apply{check(isDirectory||mkdirs()){"分享文件无法写入"}}
        val oldest=System.currentTimeMillis()-7*86400000L
        folder.listFiles().orEmpty().filter{it.isFile&&it.name.endsWith(".json")&&it.lastModified()<oldest}.forEach{it.delete()}
        val stamp=LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss"))
        val file=File(folder,"桌面清单备份-$stamp-${UUID.randomUUID().toString().take(6)}.json")
        DataRepository.writeAtomic(file,json)
        val uri=uriForFile(file)
        val send=Intent(Intent.ACTION_SEND).apply{
            type="application/octet-stream"
            putExtra(Intent.EXTRA_STREAM,uri);putExtra(Intent.EXTRA_TITLE,file.name)
            clipData=ClipData.newRawUri(file.name,uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send,"分享备份文件").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
class BackupFileProvider:FileProvider()
