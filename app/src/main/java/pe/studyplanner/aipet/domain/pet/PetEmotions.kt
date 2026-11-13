package pe.studyplanner.aipet.domain.pet

import pe.studyplanner.aipet.domain.json.MiniJson
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/** Pose local de un hueso procedural dentro de una cara. Lo que el JSON no diga queda neutro (loc 0, rot 0, escala 1). */
class PetFaceBone(val loc: FloatArray, val rot: FloatArray, val scale: FloatArray)

/**
 * Efecto 2D de una emoción: sprite del atlas anclado a un hueso (`root` si no se indica o no existe; `world` = sin
 * transformación de hueso: [at] se usa tal cual en el espacio del modelo, para objetos de escena que no deben moverse
 * con el cuerpo). Ver `PET_V2_SPEC.md` §4. [rot] = giro inicial en el plano (grados). [back] = capa «back»: se dibuja
 * antes de la malla, por detrás de la mascota (puf, cojín, plataforma, caballete); si no, «front»: después de la malla.
 */
class PetFxSpec(val sprite: String, val bone: String, val at: FloatArray, val size: Float, val anim: String, val phase: Float, val count: Int,
                val rot: Float = 0f, val back: Boolean = false)

class PetEmotion(val id: String, val label: String, val clip: String, val face: String, val props: List<String>,
                 val blink: Boolean, val talk: Boolean, val fx: List<PetFxSpec>)

/** Índice del atlas `fx_atlas.png`: rectángulos [x, y, w, h] en píxeles (origen arriba a la izquierda, como el PNG). */
class PetAtlas(val width: Int, val height: Int, val sprites: Map<String, FloatArray>) {
    private val uvs: Map<String, FloatArray> = HashMap<String, FloatArray>().also { m ->
        for ((name, r) in sprites) {
            // Media celda hacia dentro para que el filtrado lineal no mezcle el sprite con el vecino.
            val ix = if (r[2] > 2f) 0.5f else 0f
            val iy = if (r[3] > 2f) 0.5f else 0f
            m[name] = floatArrayOf((r[0] + ix) / width, (r[1] + iy) / height, (r[0] + r[2] - ix) / width, (r[1] + r[3] - iy) / height)
        }
    }

    /** (u0, v0, u1, v1) normalizados, con v0 en el borde de arriba del sprite; null si el atlas no lo tiene. */
    fun uv(name: String): FloatArray? = uvs[name]
}

/** Contenido de `assets/pet/emociones.json`, común a todas las mascotas. */
class PetEmotions(val faces: Map<String, Map<String, PetFaceBone>>, val emotions: Map<String, PetEmotion>,
                  val moods: Map<String, String>, val atlas: PetAtlas?) {

    private val fallbackIdle = PetEmotion("idle", "En reposo", "idle", "", emptyList(), true, false, emptyList())

    /**
     * Emoción para un texto de la app: id exacto, luego la tabla `moods`, luego los textos de siempre
     * ([PetAnimationController.fromMood], p. ej. "floating" → "float"). Lo desconocido es "idle".
     */
    fun resolve(name: String?): PetEmotion {
        val key = name?.trim()?.lowercase().orEmpty()
        emotions[key]?.let { return it }
        moods[key]?.let { id -> emotions[id]?.let { return it } }
        emotions[PetAnimationController.fromMood(key).name.lowercase()]?.let { return it }
        return emotions["idle"] ?: fallbackIdle
    }

    companion object {
        /** Lee el JSON. Lanza [IllegalArgumentException] (mensaje en español) si no es JSON o si algún campo tiene el tipo equivocado. */
        fun parse(text: String): PetEmotions {
            val root = asMap(MiniJson.parse(text.removePrefix("\uFEFF")), "el documento")
            val version = root["version"]
            require(version == null || (version is Number && version.toInt() == 1)) { "emociones.json: versión no compatible ($version)." }

            val faces = LinkedHashMap<String, Map<String, PetFaceBone>>()
            for ((face, fv) in asMap(root["faces"], "faces")) {
                val bones = LinkedHashMap<String, PetFaceBone>()
                for ((bone, bv) in asMap(fv, "faces.$face")) {
                    val m = asMap(bv, "faces.$face.$bone")
                    bones[bone] = PetFaceBone(vec3(m["loc"], "faces.$face.$bone.loc", 0f), vec3(m["rot"], "faces.$face.$bone.rot", 0f),
                        vec3(m["scale"], "faces.$face.$bone.scale", 1f))
                }
                faces[face] = bones
            }

            val emotions = LinkedHashMap<String, PetEmotion>()
            for ((id, ev) in asMap(root["emotions"], "emotions")) {
                val e = asMap(ev, "emotions.$id")
                val fx = ArrayList<PetFxSpec>()
                for ((i, fv) in asList(e["fx"], "emotions.$id.fx").withIndex()) {
                    val f = asMap(fv, "emotions.$id.fx[$i]")
                    val sprite = f["sprite"] as? String ?: throw IllegalArgumentException("emociones.json: falta «sprite» en emotions.$id.fx[$i].")
                    val count = ((f["count"] as? Number)?.toInt() ?: 1).coerceIn(1, 16)
                    val layer = f["layer"]
                    require(layer == null || layer is String) { "emociones.json: «layer» de emotions.$id.fx[$i] debe ser \"back\" o \"front\"." }
                    fx.add(PetFxSpec(sprite, f["bone"] as? String ?: "root", vec3(f["at"], "emotions.$id.fx[$i].at", 0f),
                        num(f["size"], "emotions.$id.fx[$i].size", 0.2f), f["anim"] as? String ?: "", num(f["phase"], "emotions.$id.fx[$i].phase", 0f), count,
                        num(f["rot"], "emotions.$id.fx[$i].rot", 0f), (layer as String?)?.trim()?.lowercase() == "back"))
                }
                val props = asList(e["props"], "emotions.$id.props").map { it as? String ?: throw IllegalArgumentException("emociones.json: «props» de $id debe ser una lista de nombres.") }
                emotions[id.lowercase()] = PetEmotion(id, e["label"] as? String ?: id, e["clip"] as? String ?: "idle", e["face"] as? String ?: "", props,
                    e["blink"] as? Boolean ?: true, e["talk"] as? Boolean ?: false, fx)
            }

            val moods = LinkedHashMap<String, String>()
            for ((mood, v) in asMap(root["moods"], "moods")) {
                moods[mood.lowercase()] = (v as? String ?: throw IllegalArgumentException("emociones.json: moods.$mood debe ser un texto.")).lowercase()
            }

            val atlasValue = root["atlas"]
            val atlas = if (atlasValue == null) null else {
                val a = asMap(atlasValue, "atlas")
                val size = asList(a["size"], "atlas.size")
                require(size.size == 2) { "emociones.json: atlas.size debe ser [ancho, alto]." }
                val w = num(size[0], "atlas.size", 0f).toInt(); val h = num(size[1], "atlas.size", 0f).toInt()
                require(w > 0 && h > 0) { "emociones.json: el tamaño del atlas debe ser positivo." }
                val sprites = LinkedHashMap<String, FloatArray>()
                for ((name, rv) in asMap(a["sprites"], "atlas.sprites")) {
                    val r = asList(rv, "atlas.sprites.$name")
                    require(r.size == 4) { "emociones.json: atlas.sprites.$name debe ser [x, y, ancho, alto]." }
                    sprites[name] = FloatArray(4) { num(r[it], "atlas.sprites.$name", 0f) }
                }
                PetAtlas(w, h, sprites)
            }
            return PetEmotions(faces, emotions, moods, atlas)
        }

        @Suppress("UNCHECKED_CAST")
        private fun asMap(v: Any?, what: String): Map<String, Any?> =
            if (v == null) emptyMap() else v as? Map<String, Any?> ?: throw IllegalArgumentException("emociones.json: «$what» debe ser un objeto.")

        @Suppress("UNCHECKED_CAST")
        private fun asList(v: Any?, what: String): List<Any?> =
            if (v == null) emptyList() else v as? List<Any?> ?: throw IllegalArgumentException("emociones.json: «$what» debe ser una lista.")

        private fun num(v: Any?, what: String, default: Float): Float =
            if (v == null) default else (v as? Number)?.toFloat() ?: throw IllegalArgumentException("emociones.json: «$what» debe ser un número.")

        private fun vec3(v: Any?, what: String, fill: Float): FloatArray {
            if (v == null) return floatArrayOf(fill, fill, fill)
            val l = asList(v, what)
            require(l.size == 3) { "emociones.json: «$what» debe tener 3 números." }
            return floatArrayOf(num(l[0], what, fill), num(l[1], what, fill), num(l[2], what, fill))
        }
    }
}

/**
 * Cara animada: mezcla las caras (y accesorios) de la emoción actual durante [FACE_BLEND_SEC], añade el parpadeo
 * automático y la boca al hablar, y deja en [locals] la pose local (loc, rot, escala) de cada hueso para [PetPoser].
 * El tiempo lo pone quien llama (segundos, siempre creciente). No reserva memoria al actualizar, salvo la primera vez
 * que una emoción se vuelve actual.
 */
class PetFaceDriver(private val rig: PetRig, private val data: PetEmotions, seed: Long = 20260501L) {
    private val n = rig.boneCount * 9
    private val random = kotlin.random.Random(seed)
    private val targets = HashMap<String, FloatArray>()
    private val from = FloatArray(n)
    private val base = FloatArray(n)
    private val eyeL = rig.boneIndex("eye_L")
    private val eyeR = rig.boneIndex("eye_R")
    private val mouth = rig.boneIndex("mouth")
    private var faceStart = Double.NEGATIVE_INFINITY
    private var last = Double.NaN
    private var blinkAt = -1.0
    private var blinkStart = -1.0
    private var mouthOpen = 0f

    /** Pose local de salida: 9 floats por hueso (loc3, rot3, escala3); solo cuentan los procedurales. */
    val locals = FloatArray(n)

    /** Con movimiento reducido no hay mezcla de caras, ni parpadeo, ni boca animada. */
    var reducedMotion = false

    /** Nivel externo de voz 0..1 (p. ej. de la voz sintética); si es > 0 manda sobre las sílabas procedurales. */
    var talkLevel = 0f

    var emotion: PetEmotion = data.resolve("idle")
        private set
    private var to: FloatArray = targetFor(emotion)

    init {
        for (e in data.emotions.values) targetFor(e)
        System.arraycopy(to, 0, from, 0, n)
        System.arraycopy(to, 0, base, 0, n)
        System.arraycopy(to, 0, locals, 0, n)
    }

    private fun targetFor(e: PetEmotion): FloatArray = targets.getOrPut(e.id) {
        val t = rig.defaultLocals.copyOf()
        val face = data.faces[e.face]
        if (face != null) for ((name, f) in face) {
            val i = rig.boneIndex(name)
            if (i < 0 || !rig.bones[i].procedural) continue
            System.arraycopy(f.loc, 0, t, i * 9, 3); System.arraycopy(f.rot, 0, t, i * 9 + 3, 3); System.arraycopy(f.scale, 0, t, i * 9 + 6, 3)
        }
        for (p in e.props) {
            val i = rig.boneIndex(p)
            if (i < 0 || !rig.bones[i].procedural) continue
            val o = i * 9 + 6
            if (t[o] == 0f && t[o + 1] == 0f && t[o + 2] == 0f) { t[o] = 1f; t[o + 1] = 1f; t[o + 2] = 1f }
        }
        t
    }

    fun setEmotion(name: String, now: Double, instant: Boolean = false) { setEmotion(data.resolve(name), now, instant) }

    /** Cambia de emoción; la cara se mezcla desde lo que se estaba mostrando (o salta si [instant]). */
    fun setEmotion(e: PetEmotion, now: Double, instant: Boolean = false) {
        if (e === emotion) return
        emotion = e
        to = targetFor(e)
        System.arraycopy(if (instant) to else base, 0, from, 0, n)
        faceStart = now
    }

    fun update(now: Double) {
        val dt = if (last.isNaN()) 0f else (now - last).coerceIn(0.0, 0.1).toFloat()
        last = now
        val w = if (reducedMotion) 1f else ((now - faceStart) / FACE_BLEND_SEC).toFloat().coerceIn(0f, 1f)
        val t = to
        for (i in 0 until n) base[i] = from[i] + (t[i] - from[i]) * w
        System.arraycopy(base, 0, locals, 0, n)

        // Parpadeo: multiplica la escala Y de los ojos (sobre lo que diga la cara).
        var blink = 1f
        if (emotion.blink && !reducedMotion) {
            if (blinkAt < 0.0) blinkAt = now + nextBlinkGap()
            if (blinkStart < 0.0 && now >= blinkAt) blinkStart = now
            if (blinkStart >= 0.0) {
                val u = ((now - blinkStart) / BLINK_SEC).toFloat()
                if (u >= 1f) { blinkStart = -1.0; blinkAt = now + nextBlinkGap() } else blink = 1f - 0.92f * sin(PI.toFloat() * u)
            }
        } else { blinkStart = -1.0; blinkAt = -1.0 }
        if (blink != 1f) {
            if (eyeL >= 0) locals[eyeL * 9 + 7] *= blink
            if (eyeR >= 0) locals[eyeR * 9 + 7] *= blink
        }

        // Boca: nivel externo, o sílabas procedurales si la emoción habla. Con movimiento reducido queda entreabierta y quieta.
        val ext = talkLevel
        val target = when {
            reducedMotion -> if (ext > 0.001f || emotion.talk) 0.35f else 0f
            ext > 0.001f -> ext.coerceAtMost(1f)
            emotion.talk -> syllable(now)
            else -> 0f
        }
        mouthOpen = if (reducedMotion) target else mouthOpen + (target - mouthOpen) * (1f - exp(-dt * 24f))
        if (mouthOpen < 0.002f) mouthOpen = 0f
        if (mouthOpen > 0f && mouth >= 0) {
            val a = mouthOpen; val o = mouth * 9
            locals[o + 1] -= 0.012f * a; locals[o + 2] += 0.006f * a
            locals[o + 6] *= 1f - 0.1f * a; locals[o + 7] *= 1f + 2.4f * a; locals[o + 8] *= 1f + 0.35f * a
        }
    }

    private fun nextBlinkGap(): Double = 2.5 + random.nextDouble() * 2.5

    /** Sílabas de ~0,17 s con fuerza variable y alguna pausa corta: apertura 0..1 determinista según el tiempo. */
    private fun syllable(t: Double): Float {
        val p = t / SYLLABLE_SEC
        val i = floor(p).toLong()
        val u = (p - i).toFloat()
        val pause = (i and 3L) == 3L && hash01(i shr 2) < 0.45f
        val amp = if (pause) 0.06f else 0.35f + 0.65f * hash01(i)
        return amp * sin(PI.toFloat() * u)
    }

    private fun hash01(x: Long): Float {
        var z = x * -7046029254386353131L + 1442695040888963407L
        z = z xor (z ushr 29); z *= -4658895280553007687L; z = z xor (z ushr 32)
        return ((z ushr 40).toInt() and 0xFFFFFF) / 16777216f
    }

    companion object {
        const val FACE_BLEND_SEC = 0.15
        const val BLINK_SEC = 0.12
        private const val SYLLABLE_SEC = 0.17
    }
}

/**
 * Efectos 2D (sprites) de la emoción actual. [bind] resuelve huesos y atlas una vez; [update] deja en [sprites] las
 * primeras [count] instancias listas para dibujar (centro en espacio del modelo, tamaño, giro, alfa y rectángulo del atlas).
 * Las de la capa «back» van primero: `[0, backCount)` se dibuja antes de la malla y `[backCount, count)` después.
 */
class PetFx(private val rig: PetRig, private val atlas: PetAtlas?) {
    class Sprite {
        @JvmField var x = 0f
        @JvmField var y = 0f
        @JvmField var z = 0f
        /** Lado del cuadrado, en unidades del modelo (ya con la escala de la animación). */
        @JvmField var size = 0f
        /** Grados, sentido antihorario visto desde la cámara. */
        @JvmField var rot = 0f
        @JvmField var alpha = 0f
        @JvmField var u0 = 0f
        @JvmField var v0 = 0f
        @JvmField var u1 = 0f
        @JvmField var v1 = 0f
    }

    val sprites = Array(MAX_SPRITES) { Sprite() }
    var count = 0
        private set
    var backCount = 0
        private set
    private var backSpecs = 0
    private var specs: List<PetFxSpec> = emptyList()
    private var bones = IntArray(0)
    private var rects = FloatArray(0)
    private val rootIndex = rig.boneIndex("root").coerceAtLeast(0)
    private var dx = 0f; private var dy = 0f; private var sc = 1f; private var rot = 0f; private var al = 1f

    /** Elige los efectos de [e] (los que usan un sprite que el atlas no tiene se omiten) y pone primero los de la capa «back». */
    fun bind(e: PetEmotion?) {
        val keep = ArrayList<PetFxSpec>()
        val r = ArrayList<FloatArray>()
        backSpecs = 0
        if (e != null && atlas != null) for (layer in 0..1) for (s in e.fx) {
            if (s.back != (layer == 0)) continue
            val uv = atlas.uv(s.sprite) ?: continue
            keep.add(s); r.add(uv)
            if (layer == 0) backSpecs++
        }
        specs = keep
        bones = IntArray(keep.size) {
            val name = keep[it].bone
            if (name == WORLD) -1 else rig.boneIndex(name).let { i -> if (i >= 0) i else rootIndex }
        }
        rects = FloatArray(keep.size * 4).also { a -> r.forEachIndexed { i, uv -> System.arraycopy(uv, 0, a, i * 4, 4) } }
        count = 0; backCount = 0
    }

    /**
     * [age] = segundos desde que empezó la emoción; [skin] = matrices B × 12 de la pose actual. Con [reduced] los
     * sprites quedan quietos (escala 1, sin giro; los que se repiten conservan su reparto de fases).
     */
    fun update(age: Float, skin: FloatArray, reduced: Boolean) {
        val fade = if (reduced) 1f else (age / FADE_IN_SEC).coerceIn(0f, 1f)
        val t = if (reduced || age < 0f) 0f else age
        var n = 0
        var nBack = 0
        for (i in specs.indices) {
            if (i == backSpecs) nBack = n
            val s = specs[i]
            val ax = s.at[0]; val ay = s.at[1]; val az = s.at[2]
            val cx: Float; val cy: Float; val cz: Float
            if (bones[i] < 0) { cx = ax; cy = ay; cz = az } else {
                val o = bones[i] * 12
                cx = skin[o] * ax + skin[o + 1] * ay + skin[o + 2] * az + skin[o + 3]
                cy = skin[o + 4] * ax + skin[o + 5] * ay + skin[o + 6] * az + skin[o + 7]
                cz = skin[o + 8] * ax + skin[o + 9] * ay + skin[o + 10] * az + skin[o + 11]
            }
            for (r in 0 until s.count) {
                if (n >= MAX_SPRITES) break
                animate(s.anim, t, s.phase + r.toFloat() / s.count, reduced)
                val sp = sprites[n++]
                sp.x = cx + dx; sp.y = cy + dy; sp.z = cz
                sp.size = s.size * sc; sp.rot = s.rot + rot; sp.alpha = al * fade
                sp.u0 = rects[i * 4]; sp.v0 = rects[i * 4 + 1]; sp.u1 = rects[i * 4 + 2]; sp.v1 = rects[i * 4 + 3]
            }
        }
        if (backSpecs >= specs.size) nBack = n
        backCount = nBack
        count = n
    }

    private fun frac(x: Float): Float = x - floor(x)

    /** Deja en dx, dy, sc, rot, al el desplazamiento, escala, giro y alfa de una instancia (ver `PET_V2_SPEC.md` §4). */
    private fun animate(anim: String, t: Float, ph: Float, reduced: Boolean) {
        dx = 0f; dy = 0f; sc = 1f; rot = 0f; al = 1f
        when (anim) {
            "twinkle" -> if (!reduced) { val s = sin(TWO_PI * (1.4f * t + ph)); sc = 0.75f + 0.25f * s; al = 0.925f + 0.075f * s }
            "float_up" -> {
                val u = frac(t / 1.6f + ph)
                dy = 0.35f * u; dx = 0.05f * sin(TWO_PI * (u + ph))
                al = if (u < 0.6f) 1f else (1f - u) / 0.4f
            }
            "bob" -> if (!reduced) dy = 0.04f * sin(TWO_PI * (0.9f * t + ph))
            "pop" -> if (!reduced) {
                val p = t / 0.25f
                if (p < 1f) { val q = p - 1f; sc = 1f + 2.70158f * q * q * q + 1.70158f * q * q }
                else dy = 0.02f * (p - 1f).coerceAtMost(1f) * sin(TWO_PI * (0.9f * t + ph))
            }
            "fall" -> {
                val u = frac(t / 1.8f + ph)
                dy = 0.3f - 0.6f * u; dx = (frac(ph * 2.3f + 0.17f) - 0.5f) * 0.6f
                rot = 360f * u
                al = minOf(1f, u / 0.1f, (1f - u) / 0.25f)
            }
            "shake" -> if (!reduced) rot = 12f * sin(TWO_PI * (6f * t + ph))
            "pulse" -> if (!reduced) sc = 1f + 0.12f * sin(TWO_PI * (2f * t + ph))
            "spin" -> if (!reduced) rot = 360f * frac(t / 2f + ph)
            "bounce" -> if (!reduced) dy = 0.12f * abs(sin(TWO_PI * (t + ph)))
            // "none" y cualquier otro nombre: quieto
        }
    }

    companion object {
        const val MAX_SPRITES = 64
        const val FADE_IN_SEC = 0.2f
        /** Valor de `bone` que deja el punto `at` sin transformar (queda en el espacio del modelo). */
        const val WORLD = "world"
        private const val TWO_PI = 6.2831855f
    }
}
