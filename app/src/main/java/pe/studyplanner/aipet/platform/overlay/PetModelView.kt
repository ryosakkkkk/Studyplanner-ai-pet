package pe.studyplanner.aipet.platform.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import pe.studyplanner.aipet.domain.pet.PetEmotionBus
import pe.studyplanner.aipet.domain.profile.AvatarStyle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/**
 * Mascota 3D de la app y de la vista flotante. Si `pet/<estilo>.pet` y `pet/emociones.json` existen y cargan bien se dibuja el
 * avatar animado con esqueleto ([PetSkinnedScene]); si no, el zorro texturizado del FBX o la geometría procedural de los demás.
 */
open class PetModelView(context: Context, style: AvatarStyle) : GLSurfaceView(context) {
    private val skinned = PetSkinnedScene.create(context.assets, style)
    private val renderer: GLSurfaceView.Renderer = skinned ?: if (style == AvatarStyle.FOX) FoxScene(context.assets) else PetScene(style)
    private val fox = renderer as? FoxScene
    private val scene = renderer as? PetScene
    private var turn = 0f
    private var greetUntil = 0L
    private var shownMood = "idle"
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var moved = false
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { startX=event.x; startY=event.y; lastX=event.x; moved=false; return true }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.x-startX) > ViewConfiguration.get(context).scaledTouchSlop) {
                    moved=true; parent?.requestDisallowInterceptTouchEvent(true)
                    turn += (event.x-lastX)*0.6f; skinned?.turn=turn; fox?.turn=turn; scene?.turn=turn
                }
                lastX=event.x; return true
            }
            MotionEvent.ACTION_UP -> { parent?.requestDisallowInterceptTouchEvent(false); if(!moved) performClick(); return true }
            MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false); return true }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean {
        super.performClick(); greetUntil=SystemClock.uptimeMillis()+1500
        skinned?.greetUntil=greetUntil; fox?.greetUntil=greetUntil; scene?.greetUntil=greetUntil
        return true
    }
    var mood = "idle"
        set(value) { field = value; skinned?.mood = value; legacyMood() }
    var reducedMotion = false
        set(value) { field = value; skinned?.reducedMotion = value; fox?.reducedMotion = value; scene?.reducedMotion = value }
    /** Nivel de voz 0..1 que abre la boca de la mascota animada; 0 = sin nivel externo (boca procedural si la emoción habla). */
    var talkLevel = 0f
        set(value) { field = if (value.isNaN()) 0f else value.coerceIn(0f, 1f); skinned?.talkLevel = field }
    /** Los renderizadores anteriores no leen el bus de emociones: mientras haya señal reciben su emoción como `mood`. */
    private fun legacyMood() {
        if (skinned != null) return
        val wanted = PetEmotionBus.active(SystemClock.uptimeMillis()) ?: mood
        if (wanted != shownMood) { shownMood = wanted; fox?.mood = wanted; scene?.mood = wanted }
    }
    // 16 ms = 60 fotogramas por segundo: movimiento fluido (antes 33 ms, unos 30 por segundo).
    private val frame = object : Runnable {
        override fun run() { legacyMood(); requestRender(); postDelayed(this, if (reducedMotion) 150 else 16) }
    }
    /** Se llama una vez, en el hilo principal, cuando la escena dibujó su primer cuadro. La UI quita entonces el respaldo. */
    @Volatile var onFirstFrame: (() -> Unit)? = null
    @Volatile private var firstFrameSent = false
    private val counting = object : GLSurfaceView.Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) = renderer.onSurfaceCreated(gl, config)
        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) = renderer.onSurfaceChanged(gl, width, height)
        override fun onDrawFrame(gl: GL10?) {
            renderer.onDrawFrame(gl)
            if (!firstFrameSent) { firstFrameSent = true; post { onFirstFrame?.invoke() } }
        }
    }
    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        setRenderer(counting)
        renderMode = RENDERMODE_WHEN_DIRTY
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); post(frame) }
    override fun onDetachedFromWindow() { removeCallbacks(frame); super.onDetachedFromWindow() }
    override fun onPause() { removeCallbacks(frame); super.onPause() }
    override fun onResume() { super.onResume(); removeCallbacks(frame); post(frame) }
}

private class PetScene(private val style: AvatarStyle) : GLSurfaceView.Renderer {
    @Volatile var turn = 0f
    @Volatile var greetUntil = 0L
    @Volatile var mood = "idle"
    @Volatile var reducedMotion = false
    private var program = 0
    private val projection = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val inverse = FloatArray(16)
    private val normal = FloatArray(16)
    private val world = FloatArray(16)
    private val mesh = buildList<Float> {
        fun point(a: Double, b: Double) { add((cos(a)*cos(b)).toFloat()); add(sin(a).toFloat()); add((cos(a)*sin(b)).toFloat()) }
        for (i in 0 until 24) for (j in 0 until 36) {
            val a = -PI/2+i*PI/24; val b = j*2*PI/36; val da=PI/24; val db=2*PI/36
            point(a,b); point(a+da,b); point(a+da,b+db)
            point(a,b); point(a+da,b+db); point(a,b+db)
        }
    }.toFloatArray()
    private val buffer = ByteBuffer.allocateDirect(mesh.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(mesh); position(0) }
    private val boxMesh = buildList<Float> {
        val corners=arrayOf(floatArrayOf(-1f,-1f,-1f),floatArrayOf(1f,-1f,-1f),floatArrayOf(1f,1f,-1f),floatArrayOf(-1f,1f,-1f),floatArrayOf(-1f,-1f,1f),floatArrayOf(1f,-1f,1f),floatArrayOf(1f,1f,1f),floatArrayOf(-1f,1f,1f))
        val faces=arrayOf(intArrayOf(0,1,2,3),intArrayOf(4,5,6,7),intArrayOf(0,4,7,3),intArrayOf(1,5,6,2),intArrayOf(3,2,6,7),intArrayOf(0,1,5,4))
        for(face in faces) for(index in intArrayOf(0,1,2,0,2,3)) for(value in corners[face[index]]) add(value)
    }.toFloatArray()
    private val boxBuffer=ByteBuffer.allocateDirect(boxMesh.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(boxMesh); position(0) }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        fun shader(type:Int, source:String):Int = glCreateShader(type).also { glShaderSource(it,source); glCompileShader(it) }
        program=glCreateProgram()
        glAttachShader(program, shader(GL_VERTEX_SHADER,"attribute vec3 p; uniform mat4 mvp; uniform mat4 normal; varying vec3 n; void main(){gl_Position=mvp*vec4(p,1.0); n=normalize(mat3(normal)*p);}"))
        glAttachShader(program, shader(GL_FRAGMENT_SHADER,"precision mediump float; varying vec3 n; uniform vec3 color; void main(){vec3 N=normalize(n); vec3 L=normalize(vec3(-0.5,0.8,1.0)); float light=0.50+0.50*max(dot(N,L),0.0); float spec=pow(max(dot(N,normalize(L+vec3(0.0,0.0,1.0))),0.0),38.0)*0.22; float rim=pow(1.0-max(N.z,0.0),3.0)*0.09; gl_FragColor=vec4(color*light+vec3(spec+rim),1.0);}"))
        glLinkProgram(program)
        glEnable(GL_DEPTH_TEST)
        glClearColor(0f,0f,0f,0f)
    }
    override fun onSurfaceChanged(gl: GL10?, width:Int, height:Int) {
        glViewport(0,0,width,height)
        val aspect=width.toFloat()/height.coerceAtLeast(1)
        Matrix.orthoM(projection,0,-1.3f*aspect,1.3f*aspect,-1.3f,1.3f,-5f,5f)
    }
    override fun onDrawFrame(gl: GL10?) {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glUseProgram(program)
        val p=glGetAttribLocation(program,"p")
        buffer.position(0); glVertexAttribPointer(p,3,GL_FLOAT,false,0,buffer); glEnableVertexAttribArray(p)
        val t=android.os.SystemClock.uptimeMillis()/1000f
        val greeting=!reducedMotion && android.os.SystemClock.uptimeMillis()<greetUntil
        val yaw=if(reducedMotion) 0f else sin(t*0.65f)*9f
        Matrix.setIdentityM(world,0)
        Matrix.rotateM(world,0,turn,0f,1f,0f)
        if (greeting) Matrix.translateM(world,0,0f,abs(sin(t*9))*0.08f,0f)
        val bob=if(reducedMotion) 0f else sin(t*1.5f)*0.025f
        val fur=when(style){AvatarStyle.FOX->floatArrayOf(0.96f,0.53f,0.23f);AvatarStyle.CAT->floatArrayOf(0.58f,0.72f,0.9f);AvatarStyle.OWL->floatArrayOf(0.58f,0.46f,0.76f);AvatarStyle.PANDA->floatArrayOf(0.94f,0.94f,0.9f);AvatarStyle.RABBIT->floatArrayOf(0.94f,0.76f,0.79f);AvatarStyle.PENGUIN->floatArrayOf(0.16f,0.24f,0.34f)}
        val cream=floatArrayOf(1f,0.91f,0.76f); val ink=floatArrayOf(0.12f,0.14f,0.23f)
        fun ball(x:Float,y:Float,z:Float,sx:Float,sy:Float,sz:Float,c:FloatArray, head:Boolean=false, box:Boolean=false) {
            System.arraycopy(world,0,model,0,16)
            if(head) { Matrix.translateM(model,0,0f,0.25f+bob,0f); Matrix.rotateM(model,0,yaw,0f,1f,0f) }
            Matrix.translateM(model,0,x,y,z); Matrix.scaleM(model,0,sx,sy,sz)
            Matrix.multiplyMM(mvp,0,projection,0,model,0)
            glUniformMatrix4fv(glGetUniformLocation(program,"mvp"),1,false,mvp,0)
            Matrix.invertM(inverse,0,model,0); Matrix.transposeM(normal,0,inverse,0)
            glUniformMatrix4fv(glGetUniformLocation(program,"normal"),1,false,normal,0)
            glUniform3fv(glGetUniformLocation(program,"color"),1,c,0)
            val vertices=if(box)boxBuffer else buffer
            vertices.position(0); glVertexAttribPointer(p,3,GL_FLOAT,false,0,vertices)
            glDrawArrays(GL_TRIANGLES,0,if(box)boxMesh.size/3 else mesh.size/3)
        }
        val dark=floatArrayOf(0.13f,0.17f,0.23f)
        val white=floatArrayOf(0.99f,0.97f,0.93f)
        val pink=floatArrayOf(0.98f,0.59f,0.65f)
        val gold=floatArrayOf(1f,0.66f,0.18f)
        ball(0f,-0.52f,-0.02f,0.39f,0.46f,0.32f,fur)
        ball(0f,-0.48f,0.27f,0.27f,0.31f,0.1f,white)
        for(side in listOf(-1f,1f)) {
            val limb=if(style==AvatarStyle.PANDA)dark else if(style==AvatarStyle.PENGUIN)gold else fur
            ball(side*0.23f,-0.94f,0.12f,0.21f,0.12f,0.24f,limb)
            val wave=if(greeting && side>0) 0.32f+sin(t*14)*0.06f else 0f
            ball(side*(0.38f+wave*0.3f),-0.46f+wave,0.04f,0.14f,0.27f,0.16f,if(style==AvatarStyle.PANDA)dark else fur)
            when(style) {
                AvatarStyle.PENGUIN -> Unit
                AvatarStyle.OWL -> ball(side*0.43f,0.42f,0f,0.19f,0.22f,0.17f,fur,true)
                AvatarStyle.PANDA -> ball(side*0.43f,0.43f,0f,0.23f,0.23f,0.17f,dark,true)
                AvatarStyle.RABBIT -> {
                    ball(side*0.29f,0.57f,0f,0.15f,0.43f,0.14f,fur,true)
                    ball(side*0.29f,0.61f,0.12f,0.075f,0.29f,0.035f,pink,true)
                }
                else -> {
                    ball(side*0.43f,0.41f,0f,0.19f,0.30f,0.18f,fur,true)
                    ball(side*0.44f,0.46f,0.14f,0.10f,0.18f,0.035f,pink,true)
                }
            }
        }
        ball(0f,0f,0f,0.62f,0.54f,0.46f,fur,true)
        for(side in listOf(-1f,1f)) {
            if(style==AvatarStyle.PANDA) ball(side*0.24f,0.02f,0.4f,0.19f,0.22f,0.11f,dark,true)
            else if(style==AvatarStyle.OWL || style==AvatarStyle.PENGUIN)
                ball(side*0.23f,-0.04f,0.38f,0.27f,0.33f,0.12f,white,true)
            else ball(side*0.22f,-0.20f,0.37f,0.27f,0.22f,0.14f,cream,true)
            val blink=if(!reducedMotion && (t%4.4f<0.13f || greeting))0.023f else 0.11f
            val eye=if(style==AvatarStyle.OWL)0.10f else 0.078f
            ball(side*0.24f,0.07f,0.49f,eye,blink,0.05f,ink,true)
            if(blink>0.03f)ball(side*0.24f-0.022f,0.11f,0.534f,0.026f,0.032f,0.012f,white,true)
            ball(side*0.37f,-0.16f,0.43f,0.08f,0.035f,0.018f,pink,true)
        }
        val beak=style==AvatarStyle.OWL || style==AvatarStyle.PENGUIN
        ball(0f,-0.13f,0.54f,if(beak)0.115f else 0.065f,if(beak)0.085f else 0.047f,0.07f,if(beak)gold else if(style==AvatarStyle.RABBIT)pink else ink,true)
        val mouth=if(mood=="speaking" && !reducedMotion)0.04f+abs(sin(t*12))*0.055f else 0.018f
        ball(0f,-0.27f,0.49f,0.07f,mouth,0.025f,ink,true)
        // The study companion carries an original two-page book.
        for(side in listOf(-1f,1f)) {
            ball(side*0.14f,-0.68f,0.37f,0.145f,0.16f,0.035f,floatArrayOf(0.30f,0.45f,0.84f),box=true)
            ball(side*0.14f,-0.67f,0.411f,0.125f,0.135f,0.012f,white,box=true)
        }
        if(mood=="listening") for(i in -2..2) {
            val h=if(reducedMotion)0.04f else 0.03f+abs(sin(t*4+i))*0.035f
            ball(i*0.085f,-1.12f,0f,0.025f,h,0.02f,floatArrayOf(0.35f,0.76f,0.8f))
        }
    }
}
