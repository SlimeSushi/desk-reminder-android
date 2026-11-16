package cn.deskreminder.mobile.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.BitmapShader
import androidx.annotation.RequiresApi

/** Android optical implementation, guided by Apple's Regular Liquid Glass principles. */
@RequiresApi(33)
internal class LiquidGlassOptics {
    val shader=RuntimeShader(PROGRAM)
    init { shader.setInputShader("grain",BitmapShader(GlassGrain.bitmap,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT)) }

    fun configure(width:Float,height:Float,radii:FloatArray,density:Float,dark:Boolean,press:Float,originX:Float=0f,originY:Float=0f,screenWidth:Float=width,screenHeight:Float=height,foregroundSlots:Float=0f,foregroundRects:FloatArray=floatArrayOf()){
        shader.setFloatUniform("bounds",width.coerceAtLeast(1f),height.coerceAtLeast(1f))
        shader.setFloatUniform("corners",radii)
        shader.setFloatUniform("pixelDensity",density)
        shader.setFloatUniform("darkAppearance",if(dark)1f else 0f)
        val light=GlassMaterial.localLight(originX,originY,screenWidth,screenHeight)
        shader.setFloatUniform("lightPosition",light)
        shader.setFloatUniform("surfaceOrigin",originX,originY)
        shader.setFloatUniform("bandWidth",GlassMaterial.bandPx(width,height,density))
        shader.setFloatUniform("foregroundSlots",foregroundSlots)
        shader.setFloatUniform("regionCount",(foregroundRects.size/4).coerceAtMost(8).toFloat())
        shader.setFloatUniform("foregroundBounds",foregroundRects.copyOf(32))
        shader.setFloatUniform("materialConfig",GlassMaterial.TINT,GlassMaterial.DISPLACEMENT_DP,GlassMaterial.SPECULAR_WIDTH_DP,GlassMaterial.SPECULAR_STRENGTH)
        shader.setFloatUniform("interaction",press.coerceIn(0f,1f))
    }

    fun effect(blurRadius:Float):RenderEffect=RenderEffect.createChainEffect(
        RenderEffect.createRuntimeShaderEffect(shader,"source"),
        RenderEffect.createBlurEffect(blurRadius,blurRadius,Shader.TileMode.CLAMP)
    )

    companion object {
        // Rounded-surface distance gives a narrow lens at the rim; the middle stays undistorted.
        // Alpha-composite captured content onto the canvas before adapting its luminosity.
        private const val PROGRAM="""
            uniform shader source;
            uniform shader grain;
            uniform float2 bounds;
            uniform float4 corners;
            uniform float pixelDensity;
            uniform float darkAppearance;
            uniform float2 lightPosition;
            uniform float2 surfaceOrigin;
            uniform float bandWidth;
            uniform float foregroundSlots;
            uniform float regionCount;
            uniform float4 foregroundBounds[8];
            uniform float4 materialConfig;
            uniform float interaction;

            half4 main(float2 p) {
                float2 center=bounds*.5;
                float2 v=p-center;
                float radius=p.x<center.x ? (p.y<center.y ? corners.x : corners.w)
                                          : (p.y<center.y ? corners.y : corners.z);
                float2 q=abs(v)-(center-radius);
                float2 outer=max(q,float2(0.0));
                float distance=length(outer)+min(max(q.x,q.y),0.0)-radius;
                float depth=max(-distance,0.0);
                float2 direction;
                if (length(outer)>.001) direction=normalize(outer)*sign(v);
                else direction=q.x>q.y ? float2(sign(v.x),0.0) : float2(0.0,sign(v.y));
                float phase=clamp(depth/bandWidth,0.0,1.0);
                float lens=sin(phase*3.14159265)*(1.0-smoothstep(.65,1.0,phase));
                float2 offset=direction*lens*(materialConfig.y+interaction*.15)*pixelDensity;
                float2 uv=clamp(p+offset,float2(.5),bounds-.5);
                half4 captured=source.eval(uv);
                half3 base=mix(half3(.949,.949,.969),half3(0.0),darkAppearance);
                half3 color=captured.rgb+base*(1.0-captured.a);
                float luminance=dot(color,float3(.2126,.7152,.0722));
                color=mix(half3(luminance),color,.97);
                half3 material=mix(half3(.99,.993,1.0),half3(.13,.135,.15),darkAppearance);
                color=mix(color,material,materialConfig.x);
                // Sample the already blurred source locally, without flooring the entire sheet.
                float localLum=0.0;
                for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++) {
                    half4 sample=source.eval(clamp(uv+float2(float(x),float(y))*2.0*pixelDensity,float2(.5),bounds-.5));
                    localLum+=dot(sample.rgb+base*(1.0-sample.a),float3(.2126,.7152,.0722))/9.0;
                }
                float guard=0.0;
                if(regionCount>0.0) {
                    for(int i=0;i<8;i++) if(float(i)<regionCount) {
                        float4 rect=foregroundBounds[i]+float4(-2.0,-2.0,2.0,2.0)*pixelDensity;
                        float2 outside=max(max(rect.xy-p,p-rect.zw),float2(0.0));
                        guard=max(guard,1.0-smoothstep(0.0,6.0*pixelDensity,length(outside)));
                    }
                } else if(foregroundSlots>0.0) {
                    float cell=bounds.x/foregroundSlots;
                    float dx=(mod(p.x,cell)-cell*.5)/(cell*.38);
                    float iconY=(p.y-bounds.y*.32)/(bounds.y*.22);
                    float textY=(p.y-bounds.y*.73)/(bounds.y*.24);
                    guard=max(exp(-pow(dx,4.0)-pow(iconY,4.0)),exp(-pow(dx,4.0)-pow(textY,4.0)));
                }
                float adjusted=dot(color,float3(.2126,.7152,.0722));
                if(darkAppearance>.5) {
                    float observed=max(adjusted,mix(localLum,.135,materialConfig.x));
                    color*=mix(1.0,min(1.0,.18/max(observed,.001)),guard);
                } else {
                    float observed=min(adjusted,mix(localLum,.99,materialConfig.x));
                    color=mix(color,half3(1.0),guard*max(0.0,(.82-observed)/max(1.0-observed,.001)));
                }
                float2 towardLight=lightPosition-p;
                towardLight=towardLight/max(length(towardLight),.001);
                // A small surface tilt relaxes along the same global light direction on release.
                float2 tilted=direction+towardLight*interaction*.04;
                float2 normal=tilted/max(length(tilted),.001);
                float reflection=pow(max(dot(normal,towardLight),0.0),3.0);
                float fresnel=exp(-depth/max((materialConfig.z+interaction*.5)*pixelDensity,.1));
                color+=half3(reflection*fresnel*(materialConfig.w+interaction*.04));
                color-=half3((1.0-reflection)*fresnel*.02);
                color+=half3((grain.eval((p+surfaceOrigin)/pixelDensity).r-.5)*.015);
                float alpha=1.0-smoothstep(-.65,.65,distance);
                return half4(clamp(color,0.0,1.0)*alpha,alpha);
            }
        """
    }
}
