package pe.studyplanner.aipet.platform.overlay

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import pe.studyplanner.aipet.domain.pet.*

/** The user's textured FBX, plus procedural reaction cues. The mesh has no facial rig. */
internal class FoxScene(private val assets: AssetManager) : GLSurfaceView.Renderer {
    @Volatile var turn = 0f
    @Volatile var mood = "idle"
    @Volatile var reducedMotion = false
    @Volatile var greetUntil = 0L
    private var program=0; private var vbo=0; private var count=0; private var texture=0
    private var effectProgram=0
    private var positionAttribute=0; private var normalAttribute=0; private var uvAttribute=0
    private var mvpUniform=0; private var modelUniform=0; private var imageUniform=0
    private var effectPosition=0; private var effectMatrix=0; private var effectColor=0
    private var loadedPose=""
    private val meshes=mutableMapOf<String,Pair<Int,Int>>()
    private val projection=FloatArray(16); private val model=FloatArray(16); private val mvp=FloatArray(16)
    private val effectModel=FloatArray(16); private val effectMvp=FloatArray(16)
    private val blue=floatArrayOf(.22f,.62f,1f,1f)
    private val violet=floatArrayOf(.56f,.36f,1f,1f)
    private val gold=floatArrayOf(1f,.72f,.22f,1f)
    private val pink=floatArrayOf(1f,.42f,.64f,1f)
    private val mint=floatArrayOf(.13f,.78f,.68f,1f)
    private val white=floatArrayOf(.98f,.98f,1f,1f)
    private val shadow=floatArrayOf(.29f,.25f,.48f,.13f)
    private fun buffer(values:FloatArray)=ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(values);position(0)}
    private val square=buffer(floatArrayOf(-1f,-1f,1f,-1f,1f,1f,-1f,-1f,1f,1f,-1f,1f))
    private val disc=buffer(buildList<Float>{
        repeat(32){i->add(0f);add(0f);for(j in i..i+1){add(cos(j*2*PI/32).toFloat());add(sin(j*2*PI/32).toFloat())}}
    }.toFloatArray())

    private fun usePose(pose:String) {
        val cached=meshes[pose]
        if(cached!=null){vbo=cached.first;count=cached.second;loadedPose=pose;return}
        val bytes=assets.open("pet/fox-$pose.mesh").use { it.readBytes() }
        val data=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);count=data.int
        require(count>0 && bytes.size.toLong()==4L+count.toLong()*32L){"Invalid fox mesh"}
        val vertices=ByteBuffer.allocateDirect(bytes.size-4).order(ByteOrder.nativeOrder()).apply{put(bytes,4,bytes.size-4);position(0)}
        val ids=IntArray(1);glGenBuffers(1,ids,0);vbo=ids[0];glBindBuffer(GL_ARRAY_BUFFER,vbo)
        glBufferData(GL_ARRAY_BUFFER,bytes.size-4,vertices,GL_STATIC_DRAW)
        meshes[pose]=vbo to count;loadedPose=pose
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        fun shader(type:Int,source:String)=glCreateShader(type).also { glShaderSource(it,source);glCompileShader(it) }
        fun link(vertex:String,fragment:String):Int {
            val v=shader(GL_VERTEX_SHADER,vertex);val f=shader(GL_FRAGMENT_SHADER,fragment)
            return glCreateProgram().also{glAttachShader(it,v);glAttachShader(it,f);glLinkProgram(it);glDeleteShader(v);glDeleteShader(f)}
        }
        program=link(
            "attribute vec3 p;attribute vec3 normal;attribute vec2 uv;uniform mat4 mvp;uniform mat4 model;varying vec3 n;varying vec2 tex;void main(){gl_Position=mvp*vec4(p,1.0);n=mat3(model)*normal;tex=vec2(uv.x,1.0-uv.y);}",
            "precision mediump float;varying vec3 n;varying vec2 tex;uniform sampler2D image;void main(){vec4 c=texture2D(image,tex);vec3 N=normalize(n);float key=max(dot(N,normalize(vec3(-0.4,0.8,1.0))),0.0);float fill=max(dot(N,normalize(vec3(0.8,0.3,0.5))),0.0);float rim=pow(1.0-abs(N.z),3.0)*0.045;gl_FragColor=vec4(c.rgb*(0.79+0.18*key+0.07*fill)+vec3(rim),c.a);}")
        positionAttribute=glGetAttribLocation(program,"p");normalAttribute=glGetAttribLocation(program,"normal");uvAttribute=glGetAttribLocation(program,"uv")
        mvpUniform=glGetUniformLocation(program,"mvp");modelUniform=glGetUniformLocation(program,"model");imageUniform=glGetUniformLocation(program,"image")
        effectProgram=link("attribute vec2 p;uniform mat4 mvp;void main(){gl_Position=mvp*vec4(p,0.0,1.0);}","precision mediump float;uniform vec4 color;void main(){gl_FragColor=color;}")
        effectPosition=glGetAttribLocation(effectProgram,"p");effectMatrix=glGetUniformLocation(effectProgram,"mvp");effectColor=glGetUniformLocation(effectProgram,"color")
        meshes.clear();usePose("front")
        val ids=IntArray(1);glGenTextures(1,ids,0);texture=ids[0];glBindTexture(GL_TEXTURE_2D,texture)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
        val bitmap=assets.open("pet/fox-fbx-color.png").use { BitmapFactory.decodeStream(it) }
        GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0);glGenerateMipmap(GL_TEXTURE_2D);bitmap.recycle()
        glClearColor(0f,0f,0f,0f)
        glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA)
    }
    override fun onSurfaceChanged(gl: GL10?, width:Int,height:Int) {
        glViewport(0,0,width,height);val aspect=width.toFloat()/height.coerceAtLeast(1)
        val vertical=1.22f/max(aspect, .6f).coerceAtMost(1f)
        Matrix.orthoM(projection,0,-vertical*aspect,vertical*aspect,-vertical,vertical,-5f,5f)
    }
    override fun onDrawFrame(gl: GL10?) {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        val t=if(reducedMotion)0f else (SystemClock.uptimeMillis()%600_000L)/1000f
        val state=if(SystemClock.uptimeMillis()<greetUntil)PetState.GREET else PetAnimationController.fromMood(mood)
        val pose=if(state in setOf(PetState.GREET,PetState.HAPPY,PetState.CELEBRATE)) "wave" else "front"
        if(loadedPose!=pose)usePose(pose)
        effectsBegin();shape(0f,-1.04f,.55f,.055f,shadow,true)
        glEnable(GL_DEPTH_TEST);glUseProgram(program)
        Matrix.setIdentityM(model,0)
        val breathing=if(reducedMotion)0f else sin(t*1.8f)*.014f
        val bounce=if(reducedMotion)0f else when(state){
            PetState.GREET,PetState.HAPPY->abs(sin(t*5f))*.045f
            PetState.CELEBRATE->abs(sin(t*6f))*.12f
            PetState.FLOAT->.07f+sin(t*2f)*.07f
            PetState.SURPRISED->abs(sin(t*3f))*.025f
            else->breathing
        }
        val lean=when(state){
            PetState.LISTEN->-8f;PetState.THINK->7f
            PetState.STUDY->-3f;PetState.REST->12f;PetState.SLEEP->22f
            PetState.GREET->if(reducedMotion)0f else sin(t*7f)*5f
            PetState.CELEBRATE->if(reducedMotion)0f else sin(t*6f)*7f
            PetState.ALERT->if(reducedMotion)0f else sin(t*7f)*2f
            else->0f
        }
        Matrix.translateM(model,0,0f,bounce+if(state==PetState.SLEEP)-.08f else 0f,0f)
        Matrix.rotateM(model,0,lean,0f,0f,1f)
        val nod=when(state){PetState.STUDY->10f;PetState.SPEAK->if(reducedMotion)0f else sin(t*6f)*2.5f;PetState.SLEEP->8f;else->0f}
        Matrix.rotateM(model,0,nod,1f,0f,0f)
        Matrix.rotateM(model,0,turn+if(reducedMotion || state==PetState.SLEEP)0f else sin(t*.55f)*3f,0f,1f,0f)
        Matrix.multiplyMM(mvp,0,projection,0,model,0)
        glUniformMatrix4fv(mvpUniform,1,false,mvp,0);glUniformMatrix4fv(modelUniform,1,false,model,0)
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);glUniform1i(imageUniform,0)
        glBindBuffer(GL_ARRAY_BUFFER,vbo)
        glEnableVertexAttribArray(positionAttribute);glVertexAttribPointer(positionAttribute,3,GL_FLOAT,false,32,0)
        glEnableVertexAttribArray(normalAttribute);glVertexAttribPointer(normalAttribute,3,GL_FLOAT,false,32,12)
        glEnableVertexAttribArray(uvAttribute);glVertexAttribPointer(uvAttribute,2,GL_FLOAT,false,32,24)
        glDrawArrays(GL_TRIANGLES,0,count)
        glDisableVertexAttribArray(positionAttribute);glDisableVertexAttribArray(normalAttribute);glDisableVertexAttribArray(uvAttribute)
        effectsBegin();reaction(state,t)
        glDisableVertexAttribArray(effectPosition)
    }
    private fun effectsBegin(){glDisable(GL_DEPTH_TEST);glUseProgram(effectProgram);glBindBuffer(GL_ARRAY_BUFFER,0);glEnableVertexAttribArray(effectPosition)}
    private fun shape(x:Float,y:Float,sx:Float,sy:Float,color:FloatArray,round:Boolean=false,rotation:Float=0f){
        Matrix.setIdentityM(effectModel,0);Matrix.translateM(effectModel,0,x,y,0f);Matrix.rotateM(effectModel,0,rotation,0f,0f,1f);Matrix.scaleM(effectModel,0,sx,sy,1f)
        Matrix.multiplyMM(effectMvp,0,projection,0,effectModel,0);glUniformMatrix4fv(effectMatrix,1,false,effectMvp,0);glUniform4fv(effectColor,1,color,0)
        val data=if(round)disc else square;data.position(0);glVertexAttribPointer(effectPosition,2,GL_FLOAT,false,0,data);glDrawArrays(GL_TRIANGLES,0,if(round)96 else 6)
    }
    private fun star(x:Float,y:Float,size:Float,color:FloatArray){shape(x,y,size,.014f,color,rotation=45f);shape(x,y,size,.014f,color,rotation=-45f)}
    private fun reaction(state:PetState,t:Float){
        when(state){
            PetState.IDLE->Unit
            PetState.GREET->{for(i in 0..2)shape(.76f+i*.09f,.67f+i*.10f,.025f,.07f,gold,rotation=-30f+i*20f)}
            PetState.LISTEN->{for(side in listOf(-1f,1f))for(i in 0..2){val s=.018f+(if(reducedMotion) .5f else (sin(t*4-i)+1)/2)*.012f;shape(side*(.77f+i*.095f),.32f,s,.04f+i*.04f,blue,true)}}
            PetState.THINK->{for(i in 0..2)shape(.65f+i*.16f,.72f,.035f,.035f+(if(reducedMotion)0f else abs(sin(t*3-i))*.018f),violet,true)}
            PetState.SPEAK->{for(i in -2..2){val h=.025f+if(reducedMotion).03f else abs(sin(t*8+i))*.055f;shape(i*.11f,-1.02f,.025f,h,mint,true)}}
            PetState.STUDY->{
                shape(-.16f,-.53f,.19f,.17f,violet,rotation=6f);shape(.16f,-.53f,.19f,.17f,blue,rotation=-6f)
                shape(-.16f,-.51f,.16f,.14f,white,rotation=6f);shape(.16f,-.51f,.16f,.14f,white,rotation=-6f)
                repeat(3){i->shape(-.16f,-.44f-i*.05f,.12f,.008f,violet,rotation=6f);shape(.16f,-.44f-i*.05f,.12f,.008f,blue,rotation=-6f)}
            }
            PetState.HAPPY->{star(-.85f,.47f,.10f,gold);star(.86f,.7f,.08f,pink)}
            PetState.CELEBRATE->{repeat(6){i->val a=i*PI/3;val lift=if(reducedMotion)0f else sin(t*2+i)*.08f;star((cos(a)*.92).toFloat(),(sin(a)*.8).toFloat()+lift,.055f,if(i%2==0)gold else pink)}}
            PetState.SURPRISED->{shape(.80f,.76f,.025f,.10f,gold,rotation=-12f);shape(.83f,.58f,.026f,.026f,gold,true);star(-.79f,.7f,.065f,blue)}
            PetState.ALERT->{shape(.88f,.72f,.03f,.12f,pink);shape(.88f,.50f,.03f,.03f,pink,true)}
            PetState.REST->{star(.88f,.72f,.08f,violet);star(.71f,.94f,.04f,blue)}
            PetState.SLEEP->{repeat(2){i->val x=.62f+i*.25f;val y=.5f+i*.35f+(if(reducedMotion)0f else sin(t*1.6f)*.04f);val s=.06f+i*.02f;shape(x,y+s,s,.014f,violet);shape(x,y,s*1.4f,.014f,violet,rotation=45f);shape(x,y-s,s,.014f,violet)}}
            PetState.FLOAT->{repeat(3){i->star(-.8f+i*.8f,-.94f+(if(reducedMotion)0f else sin(t*2+i)*.04f),.04f,blue)}}
        }
    }
}
