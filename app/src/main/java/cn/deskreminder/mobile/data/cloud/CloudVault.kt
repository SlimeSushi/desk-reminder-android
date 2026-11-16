package cn.deskreminder.mobile.data.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CloudVault(context:Context){
    private val file=File(context.noBackupFilesDir,"nutstore-connection.dat")
    val exists:Boolean get()=file.exists()
    private fun key(create:Boolean):SecretKey{
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getEntry(ALIAS,null) as? KeyStore.SecretKeyEntry)?.secretKey?.let{return it}
        check(create){"本机密码保护信息不可用，请重新填写"}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load():CloudProfile{
        if(!file.exists())return CloudProfile()
        try{
            val all=file.readBytes();require(all.size in 16..32768&&all[0].toInt()==1);val size=all[1].toInt();require(size in 12..16&&all.size>2+size)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(false),GCMParameterSpec(128,all.copyOfRange(2,2+size)))
            val plain=cipher.doFinal(all.copyOfRange(2+size,all.size))
            try{return CloudData.gson.fromJson(plain.toString(Charsets.UTF_8),CloudProfile::class.java).also{NutstoreTransport.validate(it)}}finally{plain.fill(0)}
        }catch(_:Exception){throw CloudConnectionException("本机连接配置无法读取，请重新填写。原配置仍保留")}
    }
    fun save(profile:CloudProfile){
        NutstoreTransport.validate(profile);val plain=CloudData.gson.toJson(profile).toByteArray(Charsets.UTF_8)
        try{val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));val iv=cipher.iv;val payload=byteArrayOf(1,iv.size.toByte())+iv+cipher.doFinal(plain)
            file.parentFile?.mkdirs();val atomic=AtomicFile(file);val output=atomic.startWrite()
            try{output.write(payload);atomic.finishWrite(output)}catch(error:Throwable){atomic.failWrite(output);throw error}
        }finally{plain.fill(0)}
    }
    fun forget(){if(file.exists())check(file.delete()){"清除本机配置失败"}}
    companion object{private const val ALIAS="DeskReminder.Nutstore.v1"}
}
