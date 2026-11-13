package pe.studyplanner.aipet.domain.pet

import java.io.ByteArrayOutputStream

internal class TestBone(val name: String, val parent: Int, val kind: Int, val visible: Boolean = true, val head: FloatArray = floatArrayOf(0f, 0f, 0f))

/** [frames]: por fotograma, las K matrices 3x4 (por filas) de los huesos de cuerpo, una detrás de otra. */
internal class TestClip(val name: String, val loop: Boolean, val fps: Float, val frames: List<FloatArray>)

/** Construye archivos `.pet` v2 diminutos (little-endian, matrices en half) para probar el lector y la pose. */
internal object SyntheticPet {
    val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)
    /** Giro de 90° sobre Z más una traslación (2, 0, 0): todos sus valores caben exactos en half. */
    val ROT_Z_90 = floatArrayOf(0f, -1f, 0f, 2f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)

    fun translation(x: Float, y: Float, z: Float) = floatArrayOf(1f, 0f, 0f, x, 0f, 1f, 0f, y, 0f, 0f, 1f, z)

    fun frame(vararg matrices: FloatArray): FloatArray = matrices.flatMap { it.toList() }.toFloatArray()

    /** float → bits de un half IEEE 754 (redondeo al par más cercano). */
    fun half(f: Float): Int {
        val bits = f.toRawBits()
        val sign = (bits ushr 16) and 0x8000
        val e32 = (bits ushr 23) and 0xFF
        var mant = bits and 0x7FFFFF
        if (e32 == 0xFF) return sign or 0x7C00 or (if (mant != 0) 0x200 else 0)
        val exp = e32 - 127 + 15
        if (exp >= 31) return sign or 0x7C00
        if (exp <= 0) {
            if (exp < -10) return sign
            mant = mant or 0x800000
            val shift = 14 - exp
            var h = mant shr shift
            val rem = mant and ((1 shl shift) - 1)
            val mid = 1 shl (shift - 1)
            if (rem > mid || (rem == mid && (h and 1) == 1)) h++
            return sign or h
        }
        var h = (exp shl 10) or (mant shr 13)
        val rem = mant and 0x1FFF
        if (rem > 0x1000 || (rem == 0x1000 && (h and 1) == 1)) h++
        return sign or h
    }

    /**
     * Vértice v: posición (v, v/2, 0.25), normal (0, 0, 127), color (200-v, 100, 50, 10+v), hueso v % B con peso 255.
     * [relativePad] alinea el relleno de los índices respecto al bloque en vez de respecto al archivo.
     */
    fun build(bones: List<TestBone>, clips: List<TestClip>, vertexCount: Int = 3, indices: IntArray = intArrayOf(0, 1, 2),
              relativePad: Boolean = false, version: Int = 2, vertexBone: (Int) -> Int = { it % bones.size }): ByteArray {
        val o = ByteArrayOutputStream()
        fun u8(v: Int) = o.write(v and 0xFF)
        fun u16(v: Int) { u8(v); u8(v shr 8) }
        fun u32(v: Long) { u16((v and 0xFFFF).toInt()); u16(((v shr 16) and 0xFFFF).toInt()) }
        fun f32(v: Float) = u32(v.toRawBits().toLong() and 0xFFFFFFFFL)
        fun ascii(s: String) { u8(s.length); o.write(s.toByteArray(Charsets.US_ASCII)) }
        "SPET".forEach { u8(it.code) }
        u32(version.toLong()); u32(bones.size.toLong()); u32(vertexCount.toLong()); u32(indices.size.toLong())
        for (b in bones) { ascii(b.name); u8(b.parent); u8(b.kind); u8(if (b.visible) 1 else 0); b.head.forEach { f32(it) } }
        for (v in 0 until vertexCount) {
            f32(v.toFloat()); f32(v / 2f); f32(0.25f)
            u8(0); u8(0); u8(127); u8(0)
            u8(200 - v); u8(100); u8(50); u8(10 + v)
            u8(vertexBone(v)); u8(0); u8(0); u8(0)
            u8(255); u8(0); u8(0); u8(0)
        }
        val indexStart = o.size()
        for (i in indices) u16(i)
        while ((o.size() - (if (relativePad) indexStart else 0)) % 4 != 0) u8(0)
        u32(clips.size.toLong())
        val k = bones.count { it.kind == 0 }
        for (c in clips) {
            ascii(c.name); u8(if (c.loop) 1 else 0); u16(c.frames.size); f32(c.fps); u8(k)
            for (fr in c.frames) { require(fr.size == k * 12) { "fotograma de ${c.name} con ${fr.size} valores, se esperaban ${k * 12}" }; fr.forEach { u16(half(it)) } }
        }
        return o.toByteArray()
    }

    /**
     * root (cuerpo) → head (cuerpo, reposo en y=1) → book (procedural, oculto en reposo, reposo en (0.5, 1, 0.25)).
     * "idle": bucle de 2 fotogramas a 2 fps. "wave": 3 fotogramas a 4 fps sin bucle, root sube 0, 2, 4.
     */
    fun tiny(rootName: String = "root", relativePad: Boolean = false, bookName: String = "book"): ByteArray = build(
        listOf(TestBone(rootName, -1, 0), TestBone("head", 0, 0, head = floatArrayOf(0f, 1f, 0f)),
            TestBone(bookName, 1, 1, visible = false, head = floatArrayOf(0.5f, 1f, 0.25f))),
        listOf(
            TestClip("idle", true, 2f, listOf(frame(IDENTITY, IDENTITY), frame(translation(0f, 1f, 0f), ROT_Z_90))),
            TestClip("wave", false, 4f, listOf(frame(translation(0f, 0f, 0f), IDENTITY), frame(translation(0f, 2f, 0f), IDENTITY), frame(translation(0f, 4f, 0f), IDENTITY))),
        ), relativePad = relativePad)

    /** Cuerpo (root, head) más huesos de cara y accesorios, para probar caras, parpadeo, boca y efectos. */
    fun face(): ByteArray = build(
        listOf(TestBone("root", -1, 0), TestBone("head", 0, 0, head = floatArrayOf(0f, 1f, 0f)),
            TestBone("eye_L", 1, 1, head = floatArrayOf(-0.2f, 1.1f, 0.5f)), TestBone("eye_R", 1, 1, head = floatArrayOf(0.2f, 1.1f, 0.5f)),
            TestBone("mouth", 1, 1, head = floatArrayOf(0f, 0.9f, 0.5f)), TestBone("smile", 1, 1, visible = false, head = floatArrayOf(0f, 0.9f, 0.5f)),
            TestBone("tear", 1, 1, visible = false, head = floatArrayOf(0.2f, 1f, 0.5f)), TestBone("book", 0, 1, visible = false, head = floatArrayOf(0.3f, 0.2f, 0.5f))),
        listOf(TestClip("idle", true, 2f, listOf(frame(IDENTITY, IDENTITY), frame(IDENTITY, IDENTITY)))))

    /** Un `emociones.json` pequeño que usa los huesos de [face]. */
    val EMOTIONS_JSON = """
    {
      "version": 1,
      "faces": {
        "neutral": {},
        "happy": {
          "eye_L": {"scale": [1, 0.6, 1]}, "eye_R": {"scale": [1, 0.6, 1]},
          "smile": {"loc": [0, 0.01, 0], "scale": [1, 1, 1]},
          "ghost": {"scale": [3, 3, 3]}
        },
        "sad": {"tear": {"loc": [0.1, -0.05, 0.02], "rot": [0, 0, 15], "scale": [1, 1, 1]}, "mouth": {"scale": [1, 0.5, 1]}}
      },
      "emotions": {
        "idle": {"label": "En reposo", "clip": "idle", "face": "neutral", "props": [], "blink": true, "talk": false, "fx": []},
        "happy": {"label": "Feliz", "clip": "happy", "face": "happy", "blink": true,
          "fx": [{"sprite": "sparkle", "bone": "head", "at": [0.75, 0.95, 0.2], "size": 0.22, "anim": "twinkle", "phase": 0.0, "count": 1}]},
        "study": {"label": "Estudiar", "clip": "read", "face": "neutral", "props": ["book", "laptop"], "blink": true, "talk": false,
          "fx": [{"sprite": "note", "bone": "nariz", "at": [0, 1.2, 0.3], "size": 0.2, "anim": "float_up", "count": 3},
                 {"sprite": "glow", "bone": "world", "at": [0, -1, 0], "size": 1.1, "layer": "back", "rot": 30},
                 {"sprite": "sparkle", "at": [0, 0, 0], "size": 0.1, "layer": "front"},
                 {"sprite": "missing", "at": [0, 0, 0]}]},
        "speak": {"label": "Hablar", "clip": "speak", "face": "neutral", "props": [], "blink": true, "talk": true, "fx": []},
        "listen": {"label": "Escuchar", "clip": "listen", "face": "neutral", "blink": true},
        "float": {"label": "Flotar", "clip": "float", "face": "neutral", "blink": true},
        "sleep": {"label": "Dormir", "clip": "sleep", "face": "sad", "blink": false},
        "celebrate": {"label": "Celebrar", "clip": "celebrate", "face": "happy"}
      },
      "moods": {"speaking": "speak", "Listening": "listen"},
      "atlas": {"size": [256, 128], "sprites": {"sparkle": [0, 0, 64, 64], "note": [64, 0, 64, 64], "glow": [128, 0, 128, 128]}}
    }
    """.trimIndent()
}
