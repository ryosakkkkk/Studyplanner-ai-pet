package pe.studyplanner.aipet.platform.overlay

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import pe.studyplanner.aipet.domain.pet.*
import pe.studyplanner.aipet.domain.profile.AvatarStyle

/**
 * Mascota animada con esqueleto (`assets/pet/<estilo>.pet`, formato v2 de `PET_V2_SPEC.md`): clips de cuerpo, cara y accesorios
 * procedurales según `emociones.json`, sombra suave y efectos 2D del atlas `fx_atlas.png`. Se crea con [create]; si falta
 * algún archivo o está dañado devuelve null y la vista usa el renderizador anterior. Todo el estado de dibujo vive en el hilo de GL.
 */
internal class PetSkinnedScene private constructor(
    private val assets: AssetManager, private val rig: PetRig, private val emotions: PetEmotions,
) : GLSurfaceView.Renderer {
    @Volatile var turn = 0f
    /** Texto de la app ("idle", "listening"…) o id de emoción. Una señal activa de [PetEmotionBus] manda sobre él. */
    @Volatile var mood = "idle"
    @Volatile var reducedMotion = false
    /** Hasta este `uptimeMillis` se muestra el saludo (toque). */
    @Volatile var greetUntil = 0L
    /** Nivel de voz 0..1 para la boca; 0 = sin nivel externo. */
    @Volatile var talkLevel = 0f

    private val poser = PetPoser(rig)
    private val player = PetClipPlayer(poser)
    private val face = PetFaceDriver(rig, emotions)
    private val fx = PetFx(rig, emotions.atlas)
    private val skin = FloatArray(rig.boneCount * 12)
    private var wantedName = ""
    private var emotion: PetEmotion? = null
    private var emotionStart = 0.0

    private var ready = false
    private var meshProgram = 0; private var spriteProgram = 0; private var shadowProgram = 0
    private var vbo = 0; private var ibo = 0; private var quad = 0; private var atlasTexture = 0
    private var aPos = -1; private var aNor = -1; private var aCol = -1; private var aIdx = -1; private var aW = -1
    private var uMvp = -1; private var uModel = -1; private var uBones = -1
    private var sC = -1; private var sProj = -1; private var sPos = -1; private var sRot = -1; private var sRect = -1; private var sAlpha = -1; private var sTex = -1
    private var hC = -1; private var hProj = -1; private var hRect = -1; private var hColor = -1
    private val projection = FloatArray(16); private val model = FloatArray(16); private val mvp = FloatArray(16)

    // ------------------------------------------------------------------ OpenGL

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Puede llamarse otra vez (contexto nuevo): los objetos de GL anteriores ya no valen y se crean de nuevo.
        ready = false
        glClearColor(0f, 0f, 0f, 0f)
        glEnable(GL_BLEND); glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA) // todo sale con alfa premultiplicado
        try {
            meshProgram = link(meshVertexShader(rig.boneCount * 3), MESH_FRAGMENT)
            aPos = glGetAttribLocation(meshProgram, "aPos"); aNor = glGetAttribLocation(meshProgram, "aNor"); aCol = glGetAttribLocation(meshProgram, "aCol")
            aIdx = glGetAttribLocation(meshProgram, "aIdx"); aW = glGetAttribLocation(meshProgram, "aW")
            uMvp = glGetUniformLocation(meshProgram, "uMvp"); uModel = glGetUniformLocation(meshProgram, "uModel")
            uBones = glGetUniformLocation(meshProgram, "uBones").let { if (it >= 0) it else glGetUniformLocation(meshProgram, "uBones[0]") }
            spriteProgram = link(SPRITE_VERTEX, SPRITE_FRAGMENT)
            sC = glGetAttribLocation(spriteProgram, "aC"); sProj = glGetUniformLocation(spriteProgram, "uProj"); sPos = glGetUniformLocation(spriteProgram, "uPos")
            sRot = glGetUniformLocation(spriteProgram, "uRot"); sRect = glGetUniformLocation(spriteProgram, "uRect")
            sAlpha = glGetUniformLocation(spriteProgram, "uAlpha"); sTex = glGetUniformLocation(spriteProgram, "uTex")
            shadowProgram = link(SHADOW_VERTEX, SHADOW_FRAGMENT)
            hC = glGetAttribLocation(shadowProgram, "aC"); hProj = glGetUniformLocation(shadowProgram, "uProj")
            hRect = glGetUniformLocation(shadowProgram, "uRect"); hColor = glGetUniformLocation(shadowProgram, "uColor")
            uploadGeometry()
            atlasTexture = uploadAtlas()
            ready = true
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo preparar el avatar 3D animado.", e)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1)
        val vertical = 1.22f / max(aspect, .6f).coerceAtMost(1f)
        Matrix.orthoM(projection, 0, -vertical * aspect, vertical * aspect, -vertical, vertical, -5f, 5f)
    }

    override fun onDrawFrame(gl: GL10?) {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        if (!ready) return
        try {
            val nowMs = SystemClock.uptimeMillis()
            advance(nowMs, nowMs / 1000.0, reducedMotion)
            Matrix.setIdentityM(model, 0)
            Matrix.rotateM(model, 0, turn, 0f, 1f, 0f)
            Matrix.multiplyMM(mvp, 0, projection, 0, model, 0)
            drawShadow()
            drawSprites(0, fx.backCount) // capa «back»: detrás de la mascota
            drawMesh()
            drawSprites(fx.backCount, fx.count)
        } catch (e: RuntimeException) {
            ready = false // un fallo inesperado no debe tumbar la app: se deja de dibujar y queda el registro
            Log.e(TAG, "Fallo al dibujar el avatar 3D animado; se detiene el dibujo.", e)
        }
    }

    // «Vida propia»: si la mascota lleva un rato en reposo hace un gesto corto (saluda, piensa, guiña...) y vuelve al reposo.
    private val fidgetRandom = kotlin.random.Random(20261105)
    private var fidget: String? = null
    private var fidgetUntil = 0.0
    private var nextFidget = 0.0

    private fun withFidget(wanted: String, now: Double, reduced: Boolean): String {
        if (reduced || wanted != "idle") { fidget = null; nextFidget = 0.0; return wanted }
        if (nextFidget == 0.0) nextFidget = now + 9.0 + fidgetRandom.nextDouble() * 6.0
        if (fidget != null && now >= fidgetUntil) { fidget = null; nextFidget = now + 11.0 + fidgetRandom.nextDouble() * 9.0 }
        if (fidget == null && now >= nextFidget) { fidget = FIDGETS[fidgetRandom.nextInt(FIDGETS.size)]; fidgetUntil = now + 3.2 }
        return fidget ?: wanted
    }

    /** Elige la emoción (saludo > bus > mood, con gestos en reposo) y deja lista la pose y los efectos de este fotograma. */
    private fun advance(nowMs: Long, now: Double, reduced: Boolean) {
        val wanted = withFidget(PetEmotionBus.effective(mood, nowMs, greetUntil), now, reduced)
        if (emotion == null || wanted != wantedName) {
            wantedName = wanted
            val next = emotions.resolve(wanted)
            if (next !== emotion) {
                val instant = emotion == null || reduced
                emotion = next; emotionStart = now
                face.setEmotion(next, now, instant)
                player.play(clipFor(next), now, instant)
                fx.bind(next)
            }
        }
        face.reducedMotion = reduced; face.talkLevel = talkLevel
        face.update(now)
        player.evaluate(now, face.locals, skin, reduced)
        fx.update((now - emotionStart).toFloat(), skin, reduced)
    }

    private fun clipFor(e: PetEmotion): String = when {
        rig.clip(e.clip) != null -> e.clip
        rig.clip("idle") != null -> "idle"
        else -> rig.clips[0].name
    }

    private fun drawShadow() {
        glDisable(GL_DEPTH_TEST)
        glUseProgram(shadowProgram)
        glUniformMatrix4fv(hProj, 1, false, projection, 0)
        glUniform4f(hRect, 0f, -1.02f, .55f, .07f)
        glUniform4f(hColor, .29f, .25f, .48f, .2f)
        glBindBuffer(GL_ARRAY_BUFFER, quad)
        glEnableVertexAttribArray(hC); glVertexAttribPointer(hC, 2, GL_FLOAT, false, 0, 0)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glDisableVertexAttribArray(hC)
    }

    private fun drawMesh() {
        glEnable(GL_DEPTH_TEST)
        glUseProgram(meshProgram)
        glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        glUniformMatrix4fv(uModel, 1, false, model, 0)
        glUniform4fv(uBones, rig.boneCount * 3, skin, 0)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        attribute(aPos, 3, GL_FLOAT, false, 0)
        attribute(aNor, 4, GL_BYTE, true, 12)
        attribute(aCol, 4, GL_UNSIGNED_BYTE, true, 16)
        attribute(aIdx, 4, GL_UNSIGNED_BYTE, false, 20)
        attribute(aW, 4, GL_UNSIGNED_BYTE, true, 24)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo)
        glDrawElements(GL_TRIANGLES, rig.indexCount, GL_UNSIGNED_SHORT, 0)
        release(aPos); release(aNor); release(aCol); release(aIdx); release(aW)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
    }

    private fun attribute(location: Int, size: Int, type: Int, normalized: Boolean, offset: Int) {
        if (location < 0) return
        glEnableVertexAttribArray(location)
        glVertexAttribPointer(location, size, type, normalized, PetRig.STRIDE, offset)
    }

    private fun release(location: Int) { if (location >= 0) glDisableVertexAttribArray(location) }

    /** Efectos [from, to) como cuadrados que miran a cámara: sin profundidad y con alfa premultiplicado. */
    private fun drawSprites(from: Int, to: Int) {
        if (from >= to || atlasTexture == 0) return
        glDisable(GL_DEPTH_TEST)
        glUseProgram(spriteProgram)
        glUniformMatrix4fv(sProj, 1, false, projection, 0)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, atlasTexture); glUniform1i(sTex, 0)
        glBindBuffer(GL_ARRAY_BUFFER, quad)
        glEnableVertexAttribArray(sC); glVertexAttribPointer(sC, 2, GL_FLOAT, false, 0, 0)
        for (i in from until to) {
            val s = fx.sprites[i]
            val a = s.rot * DEG
            glUniform4f(sPos, model[0] * s.x + model[4] * s.y + model[8] * s.z + model[12],
                model[1] * s.x + model[5] * s.y + model[9] * s.z + model[13], model[2] * s.x + model[6] * s.y + model[10] * s.z + model[14], s.size * .5f)
            glUniform2f(sRot, cos(a), sin(a))
            glUniform4f(sRect, s.u0, s.v0, s.u1, s.v1)
            glUniform1f(sAlpha, s.alpha)
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        }
        glDisableVertexAttribArray(sC)
    }

    private fun shader(type: Int, source: String): Int {
        val id = glCreateShader(type)
        glShaderSource(id, source); glCompileShader(id)
        val status = IntArray(1); glGetShaderiv(id, GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) { val log = glGetShaderInfoLog(id); glDeleteShader(id); throw IllegalStateException("Shader sin compilar: $log") }
        return id
    }

    private fun link(vertex: String, fragment: String): Int {
        val v = shader(GL_VERTEX_SHADER, vertex); val f = shader(GL_FRAGMENT_SHADER, fragment)
        val program = glCreateProgram()
        glAttachShader(program, v); glAttachShader(program, f); glLinkProgram(program)
        glDeleteShader(v); glDeleteShader(f)
        val status = IntArray(1); glGetProgramiv(program, GL_LINK_STATUS, status, 0)
        if (status[0] == 0) { val log = glGetProgramInfoLog(program); glDeleteProgram(program); throw IllegalStateException("Programa sin enlazar: $log") }
        return program
    }

    private fun uploadGeometry() {
        val ids = IntArray(3); glGenBuffers(3, ids, 0)
        vbo = ids[0]; ibo = ids[1]; quad = ids[2]
        val vertices = ByteBuffer.allocateDirect(rig.vertexBytes.size).order(ByteOrder.nativeOrder()).apply { put(rig.vertexBytes); position(0) }
        glBindBuffer(GL_ARRAY_BUFFER, vbo); glBufferData(GL_ARRAY_BUFFER, rig.vertexBytes.size, vertices, GL_STATIC_DRAW)
        val indices = ByteBuffer.allocateDirect(rig.indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(rig.indices); position(0) }
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo); glBufferData(GL_ELEMENT_ARRAY_BUFFER, rig.indices.size * 2, indices, GL_STATIC_DRAW)
        val corners = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }
        glBindBuffer(GL_ARRAY_BUFFER, quad); glBufferData(GL_ARRAY_BUFFER, 32, corners, GL_STATIC_DRAW)
        glBindBuffer(GL_ARRAY_BUFFER, 0); glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    /** Sube `fx_atlas.png`; si falta o no se puede leer, la mascota se dibuja igual, sin efectos 2D (devuelve 0). */
    private fun uploadAtlas(): Int {
        if (emotions.atlas == null) return 0
        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap: Bitmap? = try {
            assets.open("pet/fx_atlas.png").use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Throwable) { // también OutOfMemoryError: el atlas es opcional
            Log.w(TAG, "No se pudo leer pet/fx_atlas.png; sin efectos 2D.", e); null
        }
        if (bitmap == null) return 0
        val ids = IntArray(1); glGenTextures(1, ids, 0)
        glBindTexture(GL_TEXTURE_2D, ids[0])
        // GLES2 solo admite mipmaps en texturas de lados potencia de 2 (o con GL_OES_texture_npot); sin ellos el sprite se ve
        // algo más «granulado» al achicarse, pero es correcto (con mipmaps no permitidos la textura saldría negra).
        val mips = (isPowerOfTwo(bitmap.width) && isPowerOfTwo(bitmap.height)) || glGetString(GL_EXTENSIONS)?.contains("GL_OES_texture_npot") == true
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, if (mips) GL_LINEAR_MIPMAP_LINEAR else GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GL_TEXTURE_2D, 0, bitmap, 0) // BitmapFactory entrega alfa premultiplicado
        if (mips) glGenerateMipmap(GL_TEXTURE_2D)
        bitmap.recycle()
        return ids[0]
    }

    private fun isPowerOfTwo(n: Int) = n > 0 && (n and (n - 1)) == 0

    companion object {
        private const val TAG = "PetSkinnedScene"
        private const val DEG = 0.017453292f
        private const val MAX_CACHED_RIGS = 3
        /** Gestos cortos que la mascota hace sola cuando está en reposo. */
        private val FIDGETS = listOf("wave", "wink", "happy", "think", "idea", "music", "walk")
        private val rigs = LinkedHashMap<String, PetRig?>()
        private var sharedEmotions: PetEmotions? = null
        private var emotionsFailed = false

        /**
         * Escena para [style] si `pet/<estilo>.pet`, `pet/emociones.json` y su lectura son válidos; si no, null (y queda en el
         * registro por qué). Los archivos ya leídos se reutilizan entre vistas; los que no cargan no se vuelven a intentar.
         */
        fun create(assets: AssetManager, style: AvatarStyle): PetSkinnedScene? = synchronized(rigs) {
            val key = style.name.lowercase()
            val emotions = emotionsOf(assets)
            val rig = if (rigs.containsKey(key)) rigs[key] else loadRig(assets, key).also { remember(key, it) }
            if (emotions == null || rig == null) null else PetSkinnedScene(assets, rig, emotions)
        }

        private fun remember(key: String, rig: PetRig?) {
            if (rigs.size >= MAX_CACHED_RIGS) { val eldest = rigs.keys.iterator(); eldest.next(); eldest.remove() }
            rigs[key] = rig
        }

        private fun loadRig(assets: AssetManager, key: String): PetRig? = try {
            PetRig.parse(assets.open("pet/$key.pet").use { it.readBytes() })
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "pet/$key.pet no está en los assets; se usa el renderizador anterior."); null
        } catch (e: Exception) {
            Log.w(TAG, "pet/$key.pet no se pudo cargar; se usa el renderizador anterior.", e); null
        }

        private fun emotionsOf(assets: AssetManager): PetEmotions? {
            sharedEmotions?.let { return it }
            if (emotionsFailed) return null
            try {
                sharedEmotions = PetEmotions.parse(assets.open("pet/emociones.json").use { String(it.readBytes(), Charsets.UTF_8) })
            } catch (e: Exception) {
                emotionsFailed = true
                Log.w(TAG, "pet/emociones.json no se pudo cargar; se usa el renderizador anterior.", e)
            }
            return sharedEmotions
        }

        // Sombreado «juguete de vinilo» (0.15): luz principal envolvente, cielo/suelo, borde lavanda y brillo doble.
        // Es el mismo GLSL ES 1.0 que el simulador web (simulador/motor.js), donde se comprobó que compila.
        // uBones se dimensiona con los huesos reales (B ≤ 40) y la normal usa las tres columnas de uModel (equivale a mat3(uModel)).
        private fun meshVertexShader(vectors: Int) = """
            attribute vec3 aPos; attribute vec4 aNor; attribute vec4 aCol; attribute vec4 aIdx; attribute vec4 aW;
            uniform mat4 uMvp; uniform mat4 uModel; uniform vec4 uBones[$vectors];
            varying vec3 vN; varying vec4 vC; varying float vY;
            vec3 xf(int b, vec4 p){ return vec3(dot(uBones[b*3],p), dot(uBones[b*3+1],p), dot(uBones[b*3+2],p)); }
            void main(){
              vec4 w = aW / max(aW.x+aW.y+aW.z+aW.w, 1e-4);
              vec4 p = vec4(aPos,1.0); vec4 n = vec4(aNor.xyz,0.0);
              vec3 sp = xf(int(aIdx.x),p)*w.x + xf(int(aIdx.y),p)*w.y + xf(int(aIdx.z),p)*w.z + xf(int(aIdx.w),p)*w.w;
              vec3 sn = xf(int(aIdx.x),n)*w.x + xf(int(aIdx.y),n)*w.y + xf(int(aIdx.z),n)*w.z + xf(int(aIdx.w),n)*w.w;
              gl_Position = uMvp*vec4(sp,1.0); vN = mat3(uModel[0].xyz, uModel[1].xyz, uModel[2].xyz)*sn; vC = aCol; vY = sp.y;
            }"""

        private const val MESH_FRAGMENT = """
            precision mediump float; varying vec3 vN; varying vec4 vC; varying float vY;
            void main(){
              vec3 N = normalize(vN);
              float g = vC.a;
              if (g >= 0.95) { gl_FragColor = vec4(vC.rgb, 1.0); return; }
              vec3 K = normalize(vec3(-0.45,0.85,1.0)); vec3 F = normalize(vec3(0.85,0.25,0.55)); vec3 H = normalize(K+vec3(0.0,0.0,1.0));
              float key = clamp((dot(N,K)+0.3)/1.3, 0.0, 1.0); key = key*key*(3.0-2.0*key);
              float fill = max(dot(N,F),0.0);
              vec3 sky = mix(vec3(0.86,0.84,0.95), vec3(1.0,0.98,0.96), N.y*0.5+0.5);
              float ground = clamp(-N.y,0.0,1.0)*(1.0-smoothstep(-0.9,0.2,vY))*0.10;
              vec3 c = vC.rgb*(sky*0.50 + vec3(1.0,0.97,0.93)*0.44*key + 0.08*fill) - vec3(ground);
              float rim = pow(1.0-abs(N.z), 2.6);
              c += vec3(0.62,0.58,0.95)*rim*0.16*(0.4+0.6*key);
              float nh = max(dot(N,H),0.0);
              c += vec3(pow(nh,48.0)*min(g,0.9)*0.95 + pow(nh,10.0)*g*0.10);
              gl_FragColor = vec4(c, 1.0);
            }"""

        private const val SPRITE_VERTEX = """
            attribute vec2 aC; uniform mat4 uProj; uniform vec4 uPos; uniform vec2 uRot; uniform vec4 uRect; varying vec2 vUv;
            void main(){
              vec2 c = vec2(aC.x*uRot.x - aC.y*uRot.y, aC.x*uRot.y + aC.y*uRot.x) * uPos.w;
              gl_Position = uProj * vec4(uPos.xy + c, uPos.z, 1.0);
              vUv = vec2(mix(uRect.x, uRect.z, aC.x*0.5+0.5), mix(uRect.w, uRect.y, aC.y*0.5+0.5));
            }"""

        // Las coordenadas del atlas (1536 px de ancho) piden precisión alta; si el fragmento no la tiene, mediump.
        private const val SPRITE_FRAGMENT = "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n" +
            "varying vec2 vUv; uniform sampler2D uTex; uniform float uAlpha;\n" +
            "void main(){ gl_FragColor = texture2D(uTex, vUv) * uAlpha; }"

        private const val SHADOW_VERTEX = """
            attribute vec2 aC; uniform mat4 uProj; uniform vec4 uRect; varying vec2 vP;
            void main(){ vP = aC; gl_Position = uProj * vec4(uRect.xy + aC*uRect.zw, 0.0, 1.0); }"""

        private const val SHADOW_FRAGMENT = """
            precision mediump float; varying vec2 vP; uniform vec4 uColor;
            void main(){ float a = uColor.a * (1.0 - smoothstep(0.25, 1.0, length(vP))); gl_FragColor = vec4(uColor.rgb * a, a); }"""
    }
}
