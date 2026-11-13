package pe.studyplanner.aipet.domain.pet

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Hueso de un avatar `.pet` v2. [kind] 0 = cuerpo (sale de los clips); 1 = procedural (cara, efectos y accesorios). */
class PetBone(val name: String, val parent: Int, val kind: Int, val defaultVisible: Boolean, val head: FloatArray) {
    val procedural: Boolean get() = kind == 1
}

/** Clip de cuerpo. [data] = frames × [k] × 12 floats: matrices de skin 3x4 por filas, ya decodificadas desde half. */
class PetClip(val name: String, val loop: Boolean, val frames: Int, val fps: Float, val k: Int, val data: FloatArray) {
    val duration: Float get() = frames / fps
}

/**
 * Avatar `.pet` v2 ya leído y validado (Kotlin puro, sin Android). Ver `PET_V2_SPEC.md` §2.
 * [vertexBytes] queda listo para subirse tal cual a un VBO (28 bytes por vértice, little-endian).
 */
class PetRig private constructor(
    val bones: List<PetBone>,
    val vertexCount: Int,
    val vertexBytes: ByteArray,
    /** Índices de triángulos: u16 guardado en un Short (usar `toInt() and 0xFFFF`). */
    val indices: ShortArray,
    val clips: List<PetClip>,
) {
    val boneCount: Int get() = bones.size
    val indexCount: Int get() = indices.size

    /** Índices de hueso de los huesos de cuerpo (kind 0), en orden: es el orden de las K matrices de cada fotograma. */
    val bodyBones: IntArray = bones.indices.filter { !bones[it].procedural }.toIntArray()

    /** Por hueso: su posición entre las K matrices de un fotograma, o -1 si es procedural. */
    val bodySlot: IntArray = IntArray(bones.size) { -1 }.also { slots -> bodyBones.forEachIndexed { slot, bone -> slots[bone] = slot } }

    /** Pose local por defecto (loc 0, rot 0, escala 1 si es visible en reposo o 0 si no): 9 floats por hueso. */
    val defaultLocals: FloatArray = FloatArray(bones.size * 9).also { l ->
        for (i in bones.indices) {
            val s = if (bones[i].procedural && !bones[i].defaultVisible) 0f else 1f
            l[i * 9 + 6] = s; l[i * 9 + 7] = s; l[i * 9 + 8] = s
        }
    }

    private val boneByName: Map<String, Int> = HashMap<String, Int>().also { m -> bones.forEachIndexed { i, b -> m[b.name] = i } }
    private val clipByName: Map<String, PetClip> = HashMap<String, PetClip>().also { m -> clips.forEach { m[it.name] = it } }

    /** Índice del hueso con ese nombre, o -1 si este avatar no lo tiene. */
    fun boneIndex(name: String): Int = boneByName[name] ?: -1

    fun clip(name: String?): PetClip? = if (name == null) null else clipByName[name]

    companion object {
        const val STRIDE = 28
        const val MAX_BONES = 40
        private const val VERSION = 2L
        private const val MAX_CLIPS = 256

        /**
         * Lee un `.pet` v2. Lanza [IllegalArgumentException] (mensaje en español) si la marca, la versión, los tamaños
         * o la longitud total no cuadran. El relleno de los índices se alinea respecto al archivo; si así no cuadra se
         * prueba también respecto al bloque de índices (la especificación no distingue ambos casos).
         */
        fun parse(bytes: ByteArray): PetRig =
            try { parse(bytes, false) } catch (first: IllegalArgumentException) {
                try { parse(bytes, true) } catch (_: IllegalArgumentException) { throw first }
            }

        /** half IEEE 754 (1 signo, 5 exponente, 10 mantisa) → float, sin depender de `Half` (API 35). */
        fun halfToFloat(h: Int): Float {
            val e = (h shr 10) and 0x1F
            val m = h and 0x3FF
            val v = when (e) {
                0 -> m * (1f / 16777216f)
                31 -> if (m == 0) Float.POSITIVE_INFINITY else Float.NaN
                else -> Float.fromBits(((e + 112) shl 23) or (m shl 13))
            }
            return if (h and 0x8000 != 0) -v else v
        }

        private class Reader(val b: ByteArray) {
            var p = 0
            fun need(n: Long, what: String) { require(n >= 0 && p + n <= b.size) { "Archivo .pet dañado: termina antes de tiempo ($what)." } }
            fun u8(what: String): Int { need(1, what); return b[p++].toInt() and 0xFF }
            fun i8(what: String): Int { need(1, what); return b[p++].toInt() }
            fun u16(what: String): Int { need(2, what); val v = (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8); p += 2; return v }
            fun u32(what: String): Long {
                need(4, what)
                val v = (b[p].toLong() and 0xFF) or ((b[p + 1].toLong() and 0xFF) shl 8) or ((b[p + 2].toLong() and 0xFF) shl 16) or ((b[p + 3].toLong() and 0xFF) shl 24)
                p += 4
                return v
            }
            fun f32(what: String): Float = Float.fromBits(u32(what).toInt())
            fun ascii(n: Int, what: String): String { need(n.toLong(), what); val s = String(b, p, n, Charsets.US_ASCII); p += n; return s }
        }

        private fun parse(bytes: ByteArray, relativePad: Boolean): PetRig {
            val r = Reader(bytes)
            r.need(20, "cabecera")
            require(bytes[0] == 'S'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'E'.code.toByte() && bytes[3] == 'T'.code.toByte()) {
                "Archivo .pet inválido: no empieza con la marca SPET."
            }
            r.p = 4
            val version = r.u32("versión")
            require(version == VERSION) { "Archivo .pet no compatible: versión $version (se esperaba $VERSION)." }
            val boneCount = r.u32("cabecera")
            val vertexCount = r.u32("cabecera")
            val indexCount = r.u32("cabecera")
            require(boneCount in 1L..MAX_BONES.toLong()) { "Archivo .pet inválido: cantidad de huesos fuera de rango ($boneCount; máximo $MAX_BONES)." }
            require(vertexCount in 1L..65535L) { "Archivo .pet inválido: cantidad de vértices fuera de rango ($vertexCount)." }
            require(indexCount >= 3 && indexCount % 3 == 0L) { "Archivo .pet inválido: la cantidad de índices ($indexCount) no es múltiplo de 3." }
            val nb = boneCount.toInt(); val nv = vertexCount.toInt()

            val bones = ArrayList<PetBone>(nb)
            val names = HashSet<String>()
            for (i in 0 until nb) {
                val len = r.u8("hueso $i")
                require(len > 0) { "Archivo .pet inválido: el hueso $i no tiene nombre." }
                val name = r.ascii(len, "hueso $i")
                val parent = r.i8("hueso $i")
                val kind = r.u8("hueso $i")
                val visible = r.u8("hueso $i")
                val head = floatArrayOf(r.f32("hueso $i"), r.f32("hueso $i"), r.f32("hueso $i"))
                require(names.add(name)) { "Archivo .pet inválido: el hueso «$name» está repetido." }
                require(parent == -1 || parent in 0 until i) { "Archivo .pet inválido: el padre del hueso «$name» debe aparecer antes que él." }
                require(kind <= 1) { "Archivo .pet inválido: tipo de hueso desconocido ($kind) en «$name»." }
                require(head.all { it.isFinite() }) { "Archivo .pet inválido: posición de reposo no finita en «$name»." }
                bones.add(PetBone(name, parent, kind, visible != 0, head))
            }
            val bodyCount = bones.count { !it.procedural }
            require(bodyCount > 0) { "Archivo .pet inválido: no hay ningún hueso de cuerpo." }

            r.need(vertexCount * STRIDE, "vértices")
            val vertexBytes = bytes.copyOfRange(r.p, r.p + nv * STRIDE)
            r.p += nv * STRIDE
            for (v in 0 until nv) {
                val o = v * STRIDE
                for (j in 0 until 4) {
                    if ((vertexBytes[o + 20 + j].toInt() and 0xFF) >= nb) {
                        // Con peso 0 el hueso no influye: se redirige a 0 para que el shader no lea fuera del arreglo.
                        require(vertexBytes[o + 24 + j].toInt() == 0) { "Archivo .pet inválido: el vértice $v usa un hueso que no existe." }
                        vertexBytes[o + 20 + j] = 0
                    }
                }
            }

            val indexStart = r.p
            r.need(indexCount * 2, "índices")
            val ni = indexCount.toInt()
            val indices = ShortArray(ni)
            for (i in 0 until ni) {
                val v = r.u16("índices")
                require(v < nv) { "Archivo .pet inválido: el índice $i apunta al vértice $v y solo hay $nv." }
                indices[i] = v.toShort()
            }
            val base = if (relativePad) indexStart else 0
            r.p = base + (((r.p - base) + 3) and 3.inv())
            r.need(0, "relleno")

            val clipCount = r.u32("clips")
            require(clipCount in 1..MAX_CLIPS.toLong()) { "Archivo .pet inválido: cantidad de clips fuera de rango ($clipCount)." }
            val clips = ArrayList<PetClip>(clipCount.toInt())
            val clipNames = HashSet<String>()
            for (c in 0 until clipCount.toInt()) {
                val len = r.u8("clip $c")
                require(len > 0) { "Archivo .pet inválido: el clip $c no tiene nombre." }
                val name = r.ascii(len, "clip $c")
                val loop = r.u8("clip «$name»")
                val frames = r.u16("clip «$name»")
                val fps = r.f32("clip «$name»")
                val k = r.u8("clip «$name»")
                require(clipNames.add(name)) { "Archivo .pet inválido: el clip «$name» está repetido." }
                require(loop <= 1) { "Archivo .pet inválido: el clip «$name» tiene un valor de bucle desconocido ($loop)." }
                require(frames >= 1) { "Archivo .pet inválido: el clip «$name» no tiene fotogramas." }
                require(fps.isFinite() && fps > 0f) { "Archivo .pet inválido: el clip «$name» tiene fps no válidos." }
                require(k == bodyCount) { "Archivo .pet inválido: el clip «$name» trae $k huesos de cuerpo y el avatar tiene $bodyCount." }
                val count = frames * k * 12
                r.need(count.toLong() * 2, "clip «$name»")
                val data = FloatArray(count)
                var q = r.p
                for (i in 0 until count) {
                    data[i] = halfToFloat((bytes[q].toInt() and 0xFF) or ((bytes[q + 1].toInt() and 0xFF) shl 8))
                    q += 2
                }
                r.p = q
                clips.add(PetClip(name, loop == 1, frames, fps, k, data))
            }
            require(r.p == bytes.size) { "Archivo .pet inválido: sobran ${bytes.size - r.p} bytes después del último clip." }
            return PetRig(bones, nv, vertexBytes, indices, clips)
        }
    }
}

/**
 * Calcula las B matrices de skin 3x4 (por filas) de un fotograma: huesos de cuerpo desde los clips y huesos
 * procedurales como `S_p = S_padre · T(h) · M · T(-h)` con `M = T(loc) · Rz · Ry · Rx · Escala`. No reserva memoria al evaluar.
 */
class PetPoser(private val rig: PetRig) {
    private val b = rig.boneCount
    private val k = rig.bodyBones.size
    private val heads = FloatArray(b * 3).also { h -> for (i in 0 until b) System.arraycopy(rig.bones[i].head, 0, h, i * 3, 3) }
    private val parents = IntArray(b) { rig.bones[it].parent }
    private val scratch = FloatArray(k * 12)

    /** Última pose de cuerpo evaluada (K × 12, ya mezclada): sirve de instantánea para fundir hacia otro clip. */
    val body = FloatArray(k * 12)

    /** Restablece la pose local por defecto ([PetRig.defaultLocals]) en [locals] (B × 9). */
    fun resetLocals(locals: FloatArray) { System.arraycopy(rig.defaultLocals, 0, locals, 0, rig.defaultLocals.size) }

    /** Muestrea [clip] en [timeSec] (interpolación lineal entre fotogramas; en bucle se envuelve, si no se queda en el último). */
    fun sample(clip: PetClip, timeSec: Float, dst: FloatArray) {
        val n = k * 12
        val frames = clip.frames
        var i0 = 0; var i1 = 0; var a = 0f
        if (frames > 1) {
            var f = timeSec.toDouble() * clip.fps
            if (clip.loop) {
                f -= floor(f / frames) * frames
                val fl = floor(f)
                i0 = fl.toInt(); a = (f - fl).toFloat()
                if (i0 >= frames) i0 = 0
                i1 = if (i0 + 1 >= frames) 0 else i0 + 1
            } else if (f >= frames - 1) {
                i0 = frames - 1; i1 = i0
            } else if (f > 0.0) {
                val fl = floor(f)
                i0 = fl.toInt(); i1 = i0 + 1; a = (f - fl).toFloat()
            }
        }
        val d = clip.data
        val o0 = i0 * n; val o1 = i1 * n
        if (a == 0f) System.arraycopy(d, o0, dst, 0, n)
        else for (i in 0 until n) dst[i] = d[o0 + i] + (d[o1 + i] - d[o0 + i]) * a
    }

    /**
     * Evalúa la pose completa en [out] (B × 12). [timeSec] es el tiempo dentro de [clipName] (desconocido → primer clip).
     * Si [blend01] < 1 se mezcla desde [from] (instantánea K × 12) o, si no hay, desde [prevClipName] muestreado en
     * [prevTimeSec]: 0 = solo lo anterior, 1 = solo el clip nuevo. [locals] (B × 9) trae loc/rot/escala de los procedurales.
     */
    fun evaluate(timeSec: Float, clipName: String, prevClipName: String?, blend01: Float, locals: FloatArray, out: FloatArray,
                 prevTimeSec: Float = timeSec, from: FloatArray? = null) {
        sample(rig.clip(clipName) ?: rig.clips[0], timeSec, body)
        if (blend01 < 1f) {
            val w = if (blend01 > 0f) blend01 else 0f
            var prev = from
            if (prev == null && prevClipName != null) {
                val pc = rig.clip(prevClipName)
                if (pc != null) { sample(pc, prevTimeSec, scratch); prev = scratch }
            }
            if (prev != null) for (i in body.indices) body[i] = prev[i] + (body[i] - prev[i]) * w
        }
        compose(locals, out)
    }

    private fun compose(locals: FloatArray, out: FloatArray) {
        for (i in 0 until b) {
            val o = i * 12
            val slot = rig.bodySlot[i]
            if (slot >= 0) { System.arraycopy(body, slot * 12, out, o, 12); continue }
            val l = i * 9
            val rx = locals[l + 3]; val ry = locals[l + 4]; val rz = locals[l + 5]
            var r00 = 1f; var r01 = 0f; var r02 = 0f
            var r10 = 0f; var r11 = 1f; var r12 = 0f
            var r20 = 0f; var r21 = 0f; var r22 = 1f
            if (rx != 0f || ry != 0f || rz != 0f) {
                // R = Rz · Ry · Rx (primero X, luego Y, luego Z), ángulos en grados.
                val cX = cos(rx * DEG); val sX = sin(rx * DEG)
                val cY = cos(ry * DEG); val sY = sin(ry * DEG)
                val cZ = cos(rz * DEG); val sZ = sin(rz * DEG)
                r00 = cZ * cY; r01 = cZ * sY * sX - sZ * cX; r02 = cZ * sY * cX + sZ * sX
                r10 = sZ * cY; r11 = sZ * sY * sX + cZ * cX; r12 = sZ * sY * cX - cZ * sX
                r20 = -sY;     r21 = cY * sX;                r22 = cY * cX
            }
            val sx = locals[l + 6]; val sy = locals[l + 7]; val sz = locals[l + 8]
            // A = R · diag(escala); la traslación de T(h) · T(loc) · A · T(-h) es h + loc - A·h.
            val a00 = r00 * sx; val a01 = r01 * sy; val a02 = r02 * sz
            val a10 = r10 * sx; val a11 = r11 * sy; val a12 = r12 * sz
            val a20 = r20 * sx; val a21 = r21 * sy; val a22 = r22 * sz
            val hx = heads[i * 3]; val hy = heads[i * 3 + 1]; val hz = heads[i * 3 + 2]
            val tx = hx + locals[l] - (a00 * hx + a01 * hy + a02 * hz)
            val ty = hy + locals[l + 1] - (a10 * hx + a11 * hy + a12 * hz)
            val tz = hz + locals[l + 2] - (a20 * hx + a21 * hy + a22 * hz)
            val p = parents[i]
            if (p < 0) {
                out[o] = a00; out[o + 1] = a01; out[o + 2] = a02; out[o + 3] = tx
                out[o + 4] = a10; out[o + 5] = a11; out[o + 6] = a12; out[o + 7] = ty
                out[o + 8] = a20; out[o + 9] = a21; out[o + 10] = a22; out[o + 11] = tz
            } else {
                val q = p * 12
                val p00 = out[q]; val p01 = out[q + 1]; val p02 = out[q + 2]; val ptx = out[q + 3]
                val p10 = out[q + 4]; val p11 = out[q + 5]; val p12 = out[q + 6]; val pty = out[q + 7]
                val p20 = out[q + 8]; val p21 = out[q + 9]; val p22 = out[q + 10]; val ptz = out[q + 11]
                out[o] = p00 * a00 + p01 * a10 + p02 * a20
                out[o + 1] = p00 * a01 + p01 * a11 + p02 * a21
                out[o + 2] = p00 * a02 + p01 * a12 + p02 * a22
                out[o + 3] = p00 * tx + p01 * ty + p02 * tz + ptx
                out[o + 4] = p10 * a00 + p11 * a10 + p12 * a20
                out[o + 5] = p10 * a01 + p11 * a11 + p12 * a21
                out[o + 6] = p10 * a02 + p11 * a12 + p12 * a22
                out[o + 7] = p10 * tx + p11 * ty + p12 * tz + pty
                out[o + 8] = p20 * a00 + p21 * a10 + p22 * a20
                out[o + 9] = p20 * a01 + p21 * a11 + p22 * a21
                out[o + 10] = p20 * a02 + p21 * a12 + p22 * a22
                out[o + 11] = p20 * tx + p21 * ty + p22 * tz + ptz
            }
        }
    }

    private companion object { const val DEG = 0.017453292f }
}

/**
 * Lleva el clip de cuerpo actual de un avatar y mezcla [fadeSec] (0,25 s) al cambiar de clip, partiendo siempre de la
 * última pose mostrada, así un cambio a mitad de una mezcla no da saltos. Con [evaluate] en modo `still` (movimiento
 * reducido) congela el primer fotograma y cambia de clip sin mezcla.
 */
class PetClipPlayer(private val poser: PetPoser, private val fadeSec: Float = 0.25f) {
    var clip: String = ""
        private set
    private var start = 0.0
    private var blendStart = 0.0
    private var blending = false
    private var shown = false
    private val snapshot = FloatArray(poser.body.size)

    fun play(name: String, now: Double, instant: Boolean = false) {
        if (name == clip) return
        if (shown && !instant) {
            System.arraycopy(poser.body, 0, snapshot, 0, snapshot.size)
            blending = true; blendStart = now
        } else blending = false
        clip = name; start = now
    }

    fun evaluate(now: Double, locals: FloatArray, out: FloatArray, still: Boolean = false) {
        val t = if (still) 0f else (now - start).toFloat()
        var w = 1f
        if (blending) {
            w = if (still) 1f else ((now - blendStart) / fadeSec).toFloat().coerceIn(0f, 1f)
            if (w >= 1f) blending = false
        }
        poser.evaluate(t, clip, null, w, locals, out, from = if (w < 1f) snapshot else null)
        shown = true
    }
}
