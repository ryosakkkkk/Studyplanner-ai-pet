package pe.studyplanner.aipet.domain.pet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class PetRigTest {
    private fun fails(bytes: ByteArray): String {
        try { PetRig.parse(bytes) } catch (e: IllegalArgumentException) { return e.message.orEmpty() }
        throw AssertionError("se esperaba IllegalArgumentException")
    }

    // ---- Matrices 4x4 de referencia (implementación independiente de PetPoser) ----
    private fun m4(a: FloatArray, o: Int) = DoubleArray(16).also { for (i in 0 until 12) it[i] = a[o + i].toDouble(); it[15] = 1.0 }
    private fun mul(a: DoubleArray, b: DoubleArray) = DoubleArray(16).also { r ->
        for (i in 0..3) for (j in 0..3) { var s = 0.0; for (k in 0..3) s += a[i * 4 + k] * b[k * 4 + j]; r[i * 4 + j] = s }
    }
    private fun tr(x: Double, y: Double, z: Double) = doubleArrayOf(1.0, 0.0, 0.0, x, 0.0, 1.0, 0.0, y, 0.0, 0.0, 1.0, z, 0.0, 0.0, 0.0, 1.0)
    private fun sc(x: Double, y: Double, z: Double) = doubleArrayOf(x, 0.0, 0.0, 0.0, 0.0, y, 0.0, 0.0, 0.0, 0.0, z, 0.0, 0.0, 0.0, 0.0, 1.0)
    private fun rx(d: Double): DoubleArray { val a = Math.toRadians(d); val c = cos(a); val s = sin(a); return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, c, -s, 0.0, 0.0, s, c, 0.0, 0.0, 0.0, 0.0, 1.0) }
    private fun ry(d: Double): DoubleArray { val a = Math.toRadians(d); val c = cos(a); val s = sin(a); return doubleArrayOf(c, 0.0, s, 0.0, 0.0, 1.0, 0.0, 0.0, -s, 0.0, c, 0.0, 0.0, 0.0, 0.0, 1.0) }
    private fun rz(d: Double): DoubleArray { val a = Math.toRadians(d); val c = cos(a); val s = sin(a); return doubleArrayOf(c, -s, 0.0, 0.0, s, c, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0) }

    /** S_parent · T(h) · T(loc) · Rz · Ry · Rx · Escala · T(-h) */
    private fun expected(parent: DoubleArray, h: FloatArray, loc: FloatArray, rot: FloatArray, scale: FloatArray): DoubleArray {
        val r = mul(rz(rot[2].toDouble()), mul(ry(rot[1].toDouble()), rx(rot[0].toDouble())))
        var m = mul(parent, tr(h[0].toDouble(), h[1].toDouble(), h[2].toDouble()))
        m = mul(m, tr(loc[0].toDouble(), loc[1].toDouble(), loc[2].toDouble()))
        m = mul(m, r)
        m = mul(m, sc(scale[0].toDouble(), scale[1].toDouble(), scale[2].toDouble()))
        return mul(m, tr(-h[0].toDouble(), -h[1].toDouble(), -h[2].toDouble()))
    }

    private fun assertMatrix(expected: DoubleArray, actual: FloatArray, off: Int, tol: Double = 1e-5) {
        for (i in 0 until 12) assertEquals("m[$i]", expected[i], actual[off + i].toDouble(), tol)
    }

    @Test
    fun parsesBonesVerticesIndicesAndClips() {
        val rig = PetRig.parse(SyntheticPet.tiny())
        assertEquals(3, rig.boneCount)
        assertEquals(listOf("root", "head", "book"), rig.bones.map { it.name })
        assertEquals(listOf(-1, 0, 1), rig.bones.map { it.parent })
        assertEquals(listOf(0, 0, 1), rig.bones.map { it.kind })
        assertEquals(listOf(true, true, false), rig.bones.map { it.defaultVisible })
        assertArrayEquals(floatArrayOf(0.5f, 1f, 0.25f), rig.bones[2].head, 0f)
        assertTrue(rig.bones[2].procedural)
        assertFalse(rig.bones[0].procedural)
        assertArrayEquals(intArrayOf(0, 1), rig.bodyBones)
        assertArrayEquals(intArrayOf(0, 1, -1), rig.bodySlot)
        assertEquals(1, rig.boneIndex("head"))
        assertEquals(-1, rig.boneIndex("nope"))
        // por defecto: visibles con escala 1, el oculto con escala 0
        assertEquals(1f, rig.defaultLocals[6], 0f)
        assertEquals(0f, rig.defaultLocals[2 * 9 + 6], 0f)
        assertEquals(0f, rig.defaultLocals[2 * 9 + 8], 0f)

        assertEquals(3, rig.vertexCount)
        assertEquals(3 * PetRig.STRIDE, rig.vertexBytes.size)
        val vb = ByteBuffer.wrap(rig.vertexBytes).order(ByteOrder.LITTLE_ENDIAN)
        val v2 = 2 * PetRig.STRIDE
        assertEquals(2f, vb.getFloat(v2), 0f)
        assertEquals(1f, vb.getFloat(v2 + 4), 0f)
        assertEquals(0.25f, vb.getFloat(v2 + 8), 0f)
        assertEquals(127, vb.get(v2 + 14).toInt())
        assertEquals(198, vb.get(v2 + 16).toInt() and 0xFF)
        assertEquals(12, vb.get(v2 + 19).toInt() and 0xFF)
        assertEquals(2, vb.get(v2 + 20).toInt())
        assertEquals(255, vb.get(v2 + 24).toInt() and 0xFF)
        assertEquals(3, rig.indexCount)
        assertArrayEquals(shortArrayOf(0, 1, 2), rig.indices)

        assertEquals(listOf("idle", "wave"), rig.clips.map { it.name })
        val idle = rig.clip("idle")!!
        assertTrue(idle.loop)
        assertEquals(2, idle.frames)
        assertEquals(2f, idle.fps, 0f)
        assertEquals(2, idle.k)
        assertEquals(1f, idle.duration, 1e-6f)
        assertEquals(2 * 2 * 12, idle.data.size)
        assertEquals(1f, idle.data[24 + 7], 0f)
        assertArrayEquals(SyntheticPet.ROT_Z_90, idle.data.copyOfRange(36, 48), 0f)
        assertFalse(rig.clip("wave")!!.loop)
        assertNull(rig.clip("zzz"))
        assertNull(rig.clip(null))
    }

    @Test
    fun decodesHalfFloatsLikeIeee754() {
        val known = listOf(0x0000 to 0f, 0x3C00 to 1f, 0xBC00 to -1f, 0xC000 to -2f, 0x3800 to 0.5f, 0x7BFF to 65504f, 0x4900 to 10f,
            0x0400 to Math.pow(2.0, -14.0).toFloat(), 0x0001 to Math.pow(2.0, -24.0).toFloat(), 0x3555 to 0.333251953125f)
        for ((bits, value) in known) assertEquals("0x" + bits.toString(16), value, PetRig.halfToFloat(bits), 0f)
        assertEquals(-0f, PetRig.halfToFloat(0x8000), 0f)
        assertEquals(0x8000_0000.toInt(), PetRig.halfToFloat(0x8000).toRawBits())
        assertEquals(Float.POSITIVE_INFINITY, PetRig.halfToFloat(0x7C00), 0f)
        assertEquals(Float.NEGATIVE_INFINITY, PetRig.halfToFloat(0xFC00), 0f)
        assertTrue(PetRig.halfToFloat(0x7E00).isNaN())
        // Los 65536 patrones contra la fórmula directa (sin NaN/infinito) y de ida y vuelta con el codificador de la prueba.
        for (h in 0..0xFFFF) {
            val e = (h shr 10) and 0x1F; val m = h and 0x3FF
            if (e == 31) continue
            val mag = if (e == 0) m * Math.pow(2.0, -24.0) else (1.0 + m / 1024.0) * Math.pow(2.0, (e - 15).toDouble())
            val ref = (if (h and 0x8000 != 0) -mag else mag).toFloat()
            assertEquals("h=$h", ref.toRawBits(), PetRig.halfToFloat(h).toRawBits())
            assertEquals("h=$h", h, SyntheticPet.half(PetRig.halfToFloat(h)))
        }
        for (i in 0 until 2000) {
            val f = (i - 1000) * 0.731f
            assertTrue("f=$f", abs(PetRig.halfToFloat(SyntheticPet.half(f)) - f) <= abs(f) / 1024f + 1e-6f)
        }
    }

    @Test
    fun acceptsPaddingAlignedToTheFileOrToTheIndexBlock() {
        // Con "rootx" el bloque de índices no empieza en múltiplo de 4: los dos criterios de relleno dan archivos distintos.
        val absolute = SyntheticPet.tiny(rootName = "rootx", relativePad = false)
        val relative = SyntheticPet.tiny(rootName = "rootx", relativePad = true)
        assertEquals(absolute.size + 1, relative.size)
        val a = PetRig.parse(absolute); val b = PetRig.parse(relative)
        assertEquals("rootx", a.bones[0].name)
        assertEquals("rootx", b.bones[0].name)
        assertArrayEquals(a.clips[1].data, b.clips[1].data, 0f)
        assertArrayEquals(a.vertexBytes, b.vertexBytes)
        assertEquals(PetRig.parse(SyntheticPet.tiny()).clips[0].frames, a.clips[0].frames)
    }

    @Test
    fun rejectsCorruptFilesWithSpanishMessages() {
        val good = SyntheticPet.tiny()
        PetRig.parse(good)
        assertTrue(fails(ByteArray(5)).contains("termina antes de tiempo"))
        assertTrue(fails(good.copyOf().also { it[0] = 'X'.code.toByte() }).contains("SPET"))
        assertTrue(fails(good.copyOf().also { it[4] = 1 }).contains("versión 1"))
        assertTrue(fails(good.copyOf(good.size - 1)).contains("termina antes de tiempo"))
        assertTrue(fails(good + byteArrayOf(0)).contains("sobran"))
        assertTrue(fails(good.copyOf().also { it[8] = 41 }).contains("huesos"))
        assertTrue(fails(good.copyOf().also { it[8] = 0 }).contains("huesos"))
        assertTrue(fails(good.copyOf().also { it[12] = 0 }).contains("vértices"))
        val body = listOf(TestBone("a", -1, 0), TestBone("b", 0, 0))
        val clip = listOf(TestClip("idle", true, 1f, listOf(SyntheticPet.frame(SyntheticPet.IDENTITY, SyntheticPet.IDENTITY))))
        PetRig.parse(SyntheticPet.build(body, clip))
        assertTrue(fails(SyntheticPet.build(listOf(TestBone("a", 1, 0), TestBone("b", -1, 0)), clip)).contains("padre"))
        assertTrue(fails(SyntheticPet.build(listOf(TestBone("a", -1, 0), TestBone("b", 0, 2)),
            listOf(TestClip("idle", true, 1f, listOf(SyntheticPet.frame(SyntheticPet.IDENTITY)))))).contains("tipo de hueso"))
        assertTrue(fails(SyntheticPet.build(listOf(TestBone("a", -1, 0), TestBone("a", 0, 0)), clip)).contains("repetido"))
        assertTrue(fails(SyntheticPet.build(body, clip, vertexBone = { 7 })).contains("no existe"))
        assertTrue(fails(SyntheticPet.build(body, clip, indices = intArrayOf(0, 1, 5))).contains("índice"))
        assertTrue(fails(SyntheticPet.build(body, clip, indices = intArrayOf(0, 1))).contains("múltiplo de 3"))
        assertTrue(fails(SyntheticPet.build(body, emptyList())).contains("clips"))
        assertTrue(fails(SyntheticPet.build(body, listOf(TestClip("idle", true, 1f, emptyList())))).contains("fotogramas"))
        assertTrue(fails(SyntheticPet.build(body, listOf(TestClip("idle", true, 0f, clip[0].frames)))).contains("fps"))
        assertTrue(fails(SyntheticPet.build(body, clip + clip)).contains("repetido"))
        assertTrue(fails(SyntheticPet.build(listOf(TestBone("a", -1, 1)), listOf(TestClip("idle", true, 1f, listOf(FloatArray(0)))), vertexBone = { 0 })).contains("cuerpo"))
        // Un clip con K distinto del número de huesos de cuerpo (se cambia el byte K del único clip).
        val badK = SyntheticPet.build(body, clip)
        badK[badK.size - 2 * 12 * 2 - 1] = 1
        assertTrue(fails(badK).contains("huesos de cuerpo"))
    }

    @Test
    fun samplesClipsWithInterpolationLoopAndHold() {
        val rig = PetRig.parse(SyntheticPet.tiny())
        val poser = PetPoser(rig)
        val dst = FloatArray(24)
        val idle = rig.clip("idle")!!
        poser.sample(idle, 0f, dst)
        assertArrayEquals(SyntheticPet.frame(SyntheticPet.IDENTITY, SyntheticPet.IDENTITY), dst, 0f)
        poser.sample(idle, 0.25f, dst) // fotograma 0,5: mitad y mitad
        assertEquals(0.5f, dst[7], 1e-6f)
        assertArrayEquals(floatArrayOf(0.5f, -0.5f, 0f, 1f, 0.5f, 0.5f, 0f, 0f, 0f, 0f, 1f, 0f), dst.copyOfRange(12, 24), 1e-6f)
        poser.sample(idle, 0.5f, dst) // fotograma 1 exacto
        assertEquals(1f, dst[7], 0f)
        assertArrayEquals(SyntheticPet.ROT_Z_90, dst.copyOfRange(12, 24), 0f)
        poser.sample(idle, 0.75f, dst) // 1,5: del último al primero (envuelve)
        assertEquals(0.5f, dst[7], 1e-6f)
        poser.sample(idle, 1f, dst) // 2,0 = fotograma 0
        assertEquals(0f, dst[7], 0f)
        poser.sample(idle, 2.25f, dst)
        assertEquals(0.5f, dst[7], 1e-6f)
        poser.sample(idle, -0.25f, dst) // tiempo negativo también envuelve
        assertEquals(0.5f, dst[7], 1e-6f)

        val wave = rig.clip("wave")!!
        poser.sample(wave, 0.125f, dst) // 0,5 entre 0 y 2
        assertEquals(1f, dst[7], 1e-6f)
        poser.sample(wave, 0.375f, dst) // 1,5 entre 2 y 4
        assertEquals(3f, dst[7], 1e-6f)
        poser.sample(wave, 0.5f, dst)
        assertEquals(4f, dst[7], 0f)
        poser.sample(wave, 10f, dst) // sin bucle se queda en el último
        assertEquals(4f, dst[7], 0f)
        poser.sample(wave, -3f, dst) // y antes de empezar en el primero
        assertEquals(0f, dst[7], 0f)
    }

    @Test
    fun crossfadeMixesPreviousAndNewClipOrASnapshot() {
        val rig = PetRig.parse(SyntheticPet.tiny())
        val poser = PetPoser(rig)
        val locals = FloatArray(9 * 3); poser.resetLocals(locals)
        val out = FloatArray(36)
        // idle en t=0,5 s → root sube 1; wave en t=0,5 s (fotograma 2) → root sube 4
        poser.evaluate(0.5f, "idle", null, 1f, locals, out)
        assertEquals(1f, out[7], 0f)
        poser.evaluate(0.5f, "idle", "wave", 0f, locals, out) // 0 = solo lo anterior
        assertEquals(4f, out[7], 1e-6f)
        poser.evaluate(0.5f, "idle", "wave", 0.25f, locals, out)
        assertEquals(4f + (1f - 4f) * 0.25f, out[7], 1e-6f)
        poser.evaluate(0.5f, "idle", "wave", 1f, locals, out)
        assertEquals(1f, out[7], 0f)
        poser.evaluate(0.5f, "idle", "wave", 0.5f, locals, out, prevTimeSec = 0f) // wave en 0 → root en 0
        assertEquals(0.5f, out[7], 1e-6f)
        // el hueso de cuerpo head también se mezcla: giro de 90° contra identidad
        assertEquals(0.5f, out[12], 1e-6f)
        // desde una instantánea (K × 12)
        val snap = SyntheticPet.frame(SyntheticPet.translation(0f, 10f, 0f), SyntheticPet.IDENTITY)
        poser.evaluate(0.5f, "idle", "wave", 0.5f, locals, out, from = snap)
        assertEquals(5.5f, out[7], 1e-6f)
        // clip desconocido → primer clip, sin fallar
        poser.evaluate(0.5f, "no-existe", null, 1f, locals, out)
        assertEquals(1f, out[7], 0f)
        assertEquals(1f, poser.body[7], 0f)
    }

    @Test
    fun proceduralBoneHiddenByDefaultCollapsesAndShowsAsItsParent() {
        val rig = PetRig.parse(SyntheticPet.tiny())
        val poser = PetPoser(rig)
        val locals = FloatArray(27); poser.resetLocals(locals)
        val out = FloatArray(36)
        poser.evaluate(0.5f, "idle", null, 1f, locals, out) // head = giro 90° + (2,0,0)
        assertArrayEquals(SyntheticPet.ROT_Z_90, out.copyOfRange(12, 24), 0f)
        // Oculto (escala 0): sin parte lineal, queda en el punto de reposo (0.5, 1, 0.25) llevado por el padre → (1, 0.5, 0.25)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 0.5f, 0f, 0f, 0f, 0.25f), out.copyOfRange(24, 36), 1e-6f)
        // Visible (escala 1, sin loc ni rot): idéntico al padre
        for (i in 6..8) locals[2 * 9 + i] = 1f
        poser.evaluate(0.5f, "idle", null, 1f, locals, out)
        assertArrayEquals(SyntheticPet.ROT_Z_90, out.copyOfRange(24, 36), 1e-6f)
    }

    @Test
    fun proceduralBoneFollowsTheSpecFormula() {
        val rig = PetRig.parse(SyntheticPet.tiny())
        val poser = PetPoser(rig)
        val locals = FloatArray(27); poser.resetLocals(locals)
        val loc = floatArrayOf(0.1f, 0.2f, 0.3f); val rot = floatArrayOf(30f, 45f, 60f); val scale = floatArrayOf(1.5f, 0.5f, 2f)
        System.arraycopy(loc, 0, locals, 18, 3); System.arraycopy(rot, 0, locals, 21, 3); System.arraycopy(scale, 0, locals, 24, 3)
        val out = FloatArray(36)
        poser.evaluate(0.5f, "idle", null, 1f, locals, out)
        assertMatrix(expected(m4(out, 12), rig.bones[2].head, loc, rot, scale), out, 24)
        // cada eje por separado (orden Rz · Ry · Rx)
        for (axis in 0..2) {
            val r = FloatArray(3); r[axis] = 35f
            System.arraycopy(r, 0, locals, 21, 3)
            poser.evaluate(0.5f, "idle", null, 1f, locals, out)
            assertMatrix(expected(m4(out, 12), rig.bones[2].head, loc, r, scale), out, 24)
        }
    }

    @Test
    fun proceduralBonesChainThroughTheirProceduralParent() {
        val bones = listOf(TestBone("root", -1, 0), TestBone("a", 0, 1, head = floatArrayOf(0f, 1f, 0f)), TestBone("b", 1, 1, head = floatArrayOf(1f, 2f, 0.5f)))
        val clip = listOf(TestClip("idle", true, 1f, listOf(SyntheticPet.frame(SyntheticPet.translation(1f, 0f, 0f)))))
        val rig = PetRig.parse(SyntheticPet.build(bones, clip, vertexBone = { 0 }))
        val poser = PetPoser(rig)
        val locals = FloatArray(27); poser.resetLocals(locals)
        val aLoc = floatArrayOf(0f, 0.5f, 0f); val aRot = floatArrayOf(0f, 0f, 90f); val one = floatArrayOf(1f, 1f, 1f)
        val bLoc = floatArrayOf(0.25f, 0f, 0f); val bRot = floatArrayOf(10f, 20f, 30f); val bScale = floatArrayOf(2f, 2f, 2f)
        System.arraycopy(aLoc, 0, locals, 9, 3); System.arraycopy(aRot, 0, locals, 12, 3)
        System.arraycopy(bLoc, 0, locals, 18, 3); System.arraycopy(bRot, 0, locals, 21, 3); System.arraycopy(bScale, 0, locals, 24, 3)
        val out = FloatArray(36)
        poser.evaluate(0f, "idle", null, 1f, locals, out)
        val sa = expected(m4(out, 0), rig.bones[1].head, aLoc, aRot, one)
        assertMatrix(sa, out, 12)
        assertMatrix(expected(sa, rig.bones[2].head, bLoc, bRot, bScale), out, 24)
    }

    @Test
    fun clipPlayerFadesFromTheLastShownPoseOverAQuarterSecond() {
        val rig = PetRig.parse(SyntheticPet.tiny())
        val poser = PetPoser(rig)
        val player = PetClipPlayer(poser)
        val locals = FloatArray(27); poser.resetLocals(locals)
        val out = FloatArray(36)
        player.play("idle", 0.0)
        player.evaluate(0.25, locals, out) // idle a 0,25 s → root sube 0,5
        assertEquals(0.5f, out[7], 1e-6f)
        player.play("wave", 1.0)
        player.evaluate(1.0, locals, out) // w = 0: sigue la pose que se veía (0,5), sin salto
        assertEquals(0.5f, out[7], 1e-6f)
        player.evaluate(1.125, locals, out) // w = 0,5: mitad de la pose congelada y mitad de wave (t=0,125 → 1)
        assertEquals(0.75f, out[7], 1e-6f)
        // Un cambio a mitad de la mezcla parte de lo último mostrado (0,75), no de un clip.
        player.play("idle", 1.125)
        player.evaluate(1.125, locals, out)
        assertEquals(0.75f, out[7], 1e-6f)
        player.evaluate(1.6, locals, out) // mezcla terminada: idle puro a 0,475 s → fotograma 0,95 → root 0,95
        assertEquals(0.95f, out[7], 1e-5f)
        assertEquals("idle", player.clip)
        // Movimiento reducido: primer fotograma del clip y cambio sin mezcla.
        player.play("wave", 2.0)
        player.evaluate(2.05, locals, out, still = true)
        assertEquals(0f, out[7], 0f)
        player.evaluate(5.0, locals, out, still = true)
        assertEquals(0f, out[7], 0f)
    }
}
