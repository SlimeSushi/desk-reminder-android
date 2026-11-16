package cn.deskreminder.mobile.data.cloud

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.StringReader
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.File

internal object NutstoreRequestBudget {
    private var location:File?=null
    private var recent:MutableList<Long>?=null
    @Synchronized fun configure(file:File){if(location!=file){location=file;recent=null}}
    @Synchronized fun take(){
        val file=location?:throw CloudConnectionException("访问频率保护尚未初始化")
        val items=recent?:runCatching{if(file.exists())file.readText().split(',').filter{it.isNotEmpty()}.map{it.toLong()}.toMutableList()else mutableListOf()}.getOrElse{throw CloudConnectionException("本机访问频率记录无法读取")}.also{recent=it}
        val now=System.currentTimeMillis();items.removeAll{it<now-30*60_000||it>now+60_000}
        if(items.size>=180)throw CloudConnectionException("本机访问次数保护已启用，请30分钟后重试")
        items.add(now);val atomic=android.util.AtomicFile(file);val output=atomic.startWrite()
        try{output.write(items.joinToString(",").toByteArray());atomic.finishWrite(output)}catch(error:Exception){atomic.failWrite(output);throw error}
    }
}

data class CloudProfile(var protocolVersion:Int=0,var backupToCloud:Boolean=true,var version:Int=1,var account:String="",var password:String="",var folder:String="DeskReminderSync",var deviceId:String=CloudData.deviceId(),var automatic:Boolean=false,var joined:Boolean=false,var checkedUtc:String="",var lastSuccessUtc:String="",var conditionalWrites:Boolean=false){override fun toString()="CloudProfile(redacted)"}
data class CloudEntry(val name:String,val directory:Boolean,val tag:String?=null)
data class CloudCheck(val at:String,val conditionalWrites:Boolean,val removed:Boolean,val remainingName:String="")
data class CloudBatch(val envelopes:MutableList<SyncEnvelope> = mutableListOf(),val remoteFiles:MutableMap<String,String> = mutableMapOf(),var ownSequence:Long=0)
class CloudConnectionException(message:String):IOException(message)

class NutstoreTransport(private val root:HttpUrl=ENDPOINT.toHttpUrl()) {
    init{require(root.toString()==ENDPOINT||root.scheme=="http"&&root.host in listOf("127.0.0.1","localhost","::1")){"Only loopback test endpoints may override Nutstore"}}
    private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(15,TimeUnit.SECONDS).build()
    private data class Reply(val status:Int,val text:String,val tag:String?)
    private fun path(profile:CloudProfile,relative:String=""):HttpUrl{
        val builder=root.newBuilder();profile.folder.split('/').forEach{builder.addPathSegment(it)}
        if(relative.isEmpty())builder.addPathSegment("")else{
            relative.trimEnd('/').split('/').forEach{require(it.matches(Regex("[a-zA-Z0-9_.-]+"))&&it !in listOf(".","..")){"同步路径无效"};builder.addPathSegment(it)}
            if(relative.endsWith('/'))builder.addPathSegment("")
        };return builder.build()
    }
    private suspend fun send(method:String,url:HttpUrl,profile:CloudProfile,body:String?=null,condition:String?=null,children:Boolean=false,limit:Int=131072):Reply=suspendCancellableCoroutine{continuation->
        try{
            if(root.toString()==ENDPOINT)NutstoreRequestBudget.take()
            val builder=Request.Builder().url(url).header("Authorization",Credentials.basic(profile.account,profile.password,Charsets.UTF_8))
            if(method=="PROPFIND")builder.header("Depth",if(children)"1"else"0")
            if(condition=="new")builder.header("If-None-Match","*")else if(condition!=null)builder.header("If-Match",condition)
            val payload=body?.toRequestBody((if(method=="PROPFIND")"application/xml; charset=utf-8"else"text/plain; charset=utf-8").toMediaType())
            builder.method(method,payload);val call=client.newCall(builder.build());continuation.invokeOnCancellation{call.cancel()}
            call.enqueue(object:Callback{
                override fun onFailure(call:Call,e:IOException){if(continuation.isActive)continuation.resumeWithException(CloudConnectionException("连接中断或超时，请检查网络后重试"))}
                override fun onResponse(call:Call,response:Response){
                    try{response.use{value->
                        if(value.code in 300..399)throw CloudConnectionException("服务返回跳转地址，已停止发送账号信息")
                        val output=ByteArrayOutputStream();value.body?.byteStream()?.use{input->val buffer=ByteArray(8192);while(true){val count=input.read(buffer);if(count<0)break;require(output.size()+count<=limit){"服务返回内容过大，未继续处理"};output.write(buffer,0,count)}}
                        val result=Reply(value.code,output.toString("UTF-8"),value.header("ETag"));if(continuation.isActive)continuation.resume(result)
                    }}catch(error:Exception){if(continuation.isActive)continuation.resumeWithException(if(error is CloudConnectionException||error is IllegalArgumentException)error else CloudConnectionException("连接读取未完成，请重试"))}
                }
            })
        }catch(error:Exception){if(continuation.isActive)continuation.resumeWithException(error)}
    }
    private fun requireStatus(reply:Reply,vararg codes:Int){if(reply.status in codes)return;throw CloudConnectionException(when(reply.status){401,403->"账号、应用密码或目录权限不正确";404->"找不到同步文件夹，请确认名称和创建位置";429->"坚果云访问次数已达限制，请稍后重试";507->"坚果云存储空间不足";else->"检查未完成（服务状态 ${reply.status}），请稍后重试"})}
    private fun parseDirectory(xml:String,requested:HttpUrl):List<CloudEntry>{
        require(!xml.contains("<!DOCTYPE",true)&&!xml.contains("<!ENTITY",true)){"云端目录XML无效"}
        val factory=DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true;isExpandEntityReferences=false}
        val parser=factory.newDocumentBuilder().apply{setEntityResolver{_,_->InputSource(StringReader(""))}}
        val doc=parser.parse(InputSource(StringReader(xml)));val rows=doc.getElementsByTagNameNS("DAV:","response")
        require(rows.length<750){"云端单层目录达到返回上限，已停止合并以防漏读"}
        fun Element.nodes(name:String)=getElementsByTagNameNS("DAV:",name)
        val base=requested.encodedPath.trimEnd('/');val result=mutableListOf<CloudEntry>();var foundCollection=false
        for(index in 0 until rows.length){
            val row=rows.item(index) as Element;val href=row.nodes("href").item(0)?.textContent?:error("目录地址无效")
            val target=requested.resolve(href)?:error("目录地址无效")
            require(target.scheme==requested.scheme&&target.host==requested.host&&target.port==requested.port){"已阻止目录中的外部地址"}
            val actual=target.encodedPath.trimEnd('/')
            var good:Element?=null;val props=row.nodes("propstat")
            for(j in 0 until props.length){val prop=props.item(j) as Element;if(prop.nodes("status").item(0)?.textContent?.contains(" 200 ")==true){good=prop;break}}
            require(good!=null){"目录含无法读取的条目，未合并内容"}
            if(actual==base){require(good.nodes("collection").length>0){"请求地址不是可用文件夹"};foundCollection=true;continue}
            require(actual.startsWith("$base/")){"目录返回了范围以外的地址"}
            val tail=actual.removePrefix("$base/");require(!tail.contains('/')){"目录返回了不完整层级"}
            val name=target.pathSegments.filter{it.isNotEmpty()}.last();require(!name.contains('/')&&name !in listOf(".","..")){"目录名称无效"}
            result.add(CloudEntry(name,good.nodes("collection").length>0,good.nodes("getetag").item(0)?.textContent))
        };require(foundCollection){"云端目录未返回有效文件夹，未合并记录"};return result
    }
    suspend fun check(profile:CloudProfile):CloudCheck {
        validate(profile);val folder=path(profile);val reply=send("PROPFIND",folder,profile,PROPS)
        requireStatus(reply,207)
        // A depth-zero response must describe the requested resource as a collection.
        require(reply.text.contains("collection")){"这个地址没有返回可用文件夹"}
        parseDirectory(reply.text,folder)
        val name="desk-connection-test-${CloudData.deviceId()}.txt";val file=path(profile,name);val original="DeskReminder connection verification ${CloudData.deviceId()}"
        var owned=true;var supports=false;var removed=true
        try{
            val put=send("PUT",file,profile,original,"new");if(put.status==412)owned=false;requireStatus(put,200,201,204)
            val read=send("GET",file,profile);requireStatus(read,200);require(read.text==original){"测试文件回读内容不一致"}
            if(read.tag?.startsWith('"')==true&&read.tag.endsWith('"')){
                val duplicate=send("PUT",file,profile,"must not replace existing test file","new")
                if(duplicate.status==412){val wrong=send("PUT",file,profile,"must not replace wrong version","\"desk-mismatch-${CloudData.deviceId()}\"");val still=send("GET",file,profile);requireStatus(still,200)
                    if(wrong.status==412&&still.text==original){val changed="$original checked";val correct=send("PUT",file,profile,changed,read.tag);if(correct.status in listOf(200,204)){val final=send("GET",file,profile);requireStatus(final,200);supports=final.text==changed}}
                }
            }
        }finally{if(owned)kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable){removed=runCatching{send("DELETE",file,profile).status in listOf(200,204,404)}.getOrDefault(false)}}
        return CloudCheck(Instant.now().toString(),supports,removed,if(removed)""else name)
    }
    suspend fun list(profile:CloudProfile,relative:String,allowMissing:Boolean=false):List<CloudEntry>{val requested=path(profile,relative.trimEnd('/')+"/");val reply=send("PROPFIND",requested,profile,PROPS,children=true,limit=4*1024*1024);if(allowMissing&&reply.status==404)return emptyList();requireStatus(reply,207);return parseDirectory(reply.text,requested)}
    private suspend fun ensure(profile:CloudProfile,relative:String){requireStatus(send("MKCOL",path(profile,relative.trimEnd('/')+"/"),profile),201,405)}
    suspend fun read(profile:CloudProfile,relative:String):String=send("GET",path(profile,relative),profile,limit=50*1024*1024).also{requireStatus(it,200)}.text
    suspend fun writeVerified(profile:CloudProfile,relative:String,json:String){
        if(relative.startsWith("protocol-v3/current-"))require(relative=="protocol-v3/current-${profile.deviceId}.json"){"禁止写入其他设备的当前数据"}
        val target=path(profile,relative);val old=send("GET",target,profile,limit=50*1024*1024)
        if(old.status==200&&old.text==json)return
        requireStatus(old,200,404)
        if(old.status==404){ensure(profile,"protocol-v3");val parent=relative.substringBeforeLast('/');if(parent!="protocol-v3")ensure(profile,parent)}
        if(old.status==200&&relative.startsWith("protocol-v3/current-"))require(FullData.compare(FullData.parse(old.text),FullData.parse(json))<=0){"云端存在此设备的更新版本，请重新检查后同步"}
        requireStatus(send("PUT",target,profile,json,if(old.status==404)"new"else old.tag),200,201,204)
        require(read(profile,relative)==json){"云端回读校验失败，待上传内容保留"}
    }
    suspend fun write(profile:CloudProfile,envelope:SyncEnvelope){
        CloudData.validate(envelope);require(envelope.deviceId==profile.deviceId){"禁止写入其他设备文件"}
        val json=CloudData.gson.toJson(envelope);val name=fileName(envelope,json);val parent="protocol-v2/${envelope.deviceId}/b${(envelope.sequence/128).toString().padStart(16,'0')}/"
        ensure(profile,"protocol-v2");ensure(profile,"protocol-v2/${envelope.deviceId}");ensure(profile,parent)
        val target=path(profile,parent+name);val previous=send("GET",target,profile,limit=50*1024*1024)
        if(previous.status==200&&previous.text==json)return
        if(previous.status!=200)requireStatus(previous,404)
        val written=send("PUT",target,profile,json,if(previous.status==404)"new"else null);if(written.status!=412)requireStatus(written,200,201,204)
        require(read(profile,parent+name)==json){"云端回读校验失败，待上传内容仍保留"}
    }
    suspend fun pull(profile:CloudProfile,state:SyncTracking?):CloudBatch {
        val batch=CloudBatch();val devices=list(profile,"protocol-v2",true)
        devices.filter{it.directory&&it.name.matches(Regex("[a-f0-9]{32}"))}.sortedBy{it.name}.forEach{device->
            val parent="protocol-v2/${device.name}";val buckets=list(profile,parent).filter{it.directory&&it.name.matches(Regex("b[0-9]{16}"))}.sortedByDescending{it.name}
            for(bucket in buckets){val files=list(profile,"$parent/${bucket.name}").filter{!it.directory&&FILE.matches(it.name)};if(files.isEmpty())continue
                val sequence=files.maxOf{it.name.substring(9,29)};val heads=files.filter{it.name.substring(9,29)==sequence}.sortedBy{it.name};val identity=bucket.name+"/"+heads.joinToString("|"){it.name}
                batch.remoteFiles[device.name]=identity;val number=sequence.toLong();require(number in 1..CloudData.MAX_COUNTER);if(device.name==profile.deviceId)batch.ownSequence=number
                if(state?.remoteFiles?.get(device.name)==identity)break
                heads.forEach{file->val json=read(profile,"$parent/${bucket.name}/${file.name}");require(CloudData.hash(json)==FILE.matchEntire(file.name)!!.groupValues[2]){"云端文件校验失败，未合并内容"};val envelope=CloudData.parse(json);require(envelope.deviceId==device.name&&envelope.sequence==number&&bucket.name=="b${(number/128).toString().padStart(16,'0')}"){"云端文件身份不一致"};batch.envelopes.add(envelope)}
                break
            }
        };return batch
    }
    companion object {
        const val ENDPOINT="https://dav.jianguoyun.com/dav/"
        const val HELP="https://help.jianguoyun.com/?p=2064"
        private const val PROPS="<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/><d:getetag/></d:prop></d:propfind>"
        private val FILE=Regex("snapshot-(\\d{20})-([a-f0-9]{64})\\.json")
        fun target(profile:CloudProfile)=CloudData.hash(profile.account.trim().lowercase()+"\n"+profile.folder)
        fun fileName(envelope:SyncEnvelope,json:String)="snapshot-${envelope.sequence.toString().padStart(20,'0')}-${CloudData.hash(json)}.json"
        fun validate(profile:CloudProfile){require(profile.version==1);require(profile.account.isNotBlank()&&profile.account.length<=320&&profile.account.none{it in "\r\n:"}){"请填写有效的坚果云账号"};require(profile.password.isNotBlank()&&profile.password.length<=2048){"请填写第三方应用密码"};require(profile.folder.isNotBlank()&&profile.folder.length<=256&&profile.folder==profile.folder.trim()){ "请填写同步目录名称" };require(profile.folder.split('/').all{it.isNotEmpty()&&it !in listOf(".","..")&&it.none{c->c in "\\%?#:\u0000\r\n"}}){"同步目录不能包含跳转路径或特殊网址字符"}}
    }
}
