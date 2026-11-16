package cn.deskreminder.mobile.ui

import android.graphics.*
import android.util.LruCache
import java.util.Random
import kotlin.math.*

internal object GlassMaterial {
    const val BLUR_DP=6f
    const val TINT=.30f
    const val DISPLACEMENT_DP=1.6f
    const val SPECULAR_WIDTH_DP=4f
    const val SPECULAR_STRENGTH=.50f
    fun bandPx(width:Float,height:Float,density:Float)=(min(width,height)/density*.12f).coerceIn(4f,10f)*density
    fun localLight(originX:Float,originY:Float,screenWidth:Float,screenHeight:Float)=floatArrayOf(screenWidth*.16f-originX,screenHeight*.08f-originY)
}

/** One fixed tile; no time uniform, per-frame random generation, or flashing grain. */
internal object GlassGrain {
    val bitmap:Bitmap by lazy {
        val random=Random(0x474C415353L)
        Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply{
            val pixels=IntArray(64*64){val v=random.nextInt(256);Color.rgb(v,v,v)}
            setPixels(pixels,0,64,0,0,64,64)
        }
    }
}

/** Shared static material for legacy/preview windows and RemoteViews; no wallpaper capture. */
internal object StaticGlassFrame {
    private data class Key(val width:Int,val height:Int,val corners:List<Float>,val color:Int,val density:Float,val alpha:Float)
    private val cache=object:LruCache<Key,Bitmap>(4*1024*1024){override fun sizeOf(key:Key,value:Bitmap)=value.allocationByteCount}
    @Synchronized fun bitmap(width:Int,height:Int,corners:FloatArray,color:Int,density:Float,alpha:Float=1f):Bitmap {
        val w=width.coerceAtLeast(1);val h=height.coerceAtLeast(1)
        val key=Key(w,h,corners.toList(),color,density,alpha)
        cache.get(key)?.let{return it}
        // Bound IPC/update memory while keeping the authored physical corner geometry.
        val scale=min(1f,800f/max(w,h));val bw=max(1,(w*scale).roundToInt());val bh=max(1,(h*scale).roundToInt())
        val image=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);val canvas=Canvas(image)
        val radii=FloatArray(8){corners[it/2]*scale}
        val path=Path().apply{addRoundRect(RectF(0f,0f,bw.toFloat(),bh.toFloat()),radii,Path.Direction.CW)}
        canvas.save();canvas.clipPath(path)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color=(color and 0x00FFFFFF) or ((alpha.coerceIn(0f,1f)*255).roundToInt() shl 24);canvas.drawPaint(paint)
        paint.shader=LinearGradient(0f,0f,0f,bh.toFloat(),intArrayOf(Color.argb(32,255,255,255),Color.TRANSPARENT,Color.argb(9,0,0,0)),floatArrayOf(0f,.3f,1f),Shader.TileMode.CLAMP)
        canvas.drawPaint(paint)
        paint.shader=BitmapShader(GlassGrain.bitmap,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT);paint.alpha=2;canvas.drawPaint(paint)
        paint.shader=LinearGradient(0f,0f,bw.toFloat(),bh.toFloat(),intArrayOf(Color.argb(104,255,255,255),Color.argb(18,255,255,255),Color.argb(58,255,255,255)),null,Shader.TileMode.CLAMP)
        paint.alpha=255;paint.style=Paint.Style.STROKE;paint.strokeWidth=max(.55f,scale)
        canvas.drawPath(path,paint);canvas.restore()
        cache.put(key,image);return image
    }
}
