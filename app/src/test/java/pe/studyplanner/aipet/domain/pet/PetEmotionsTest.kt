package pe.studyplanner.aipet.domain.pet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class PetEmotionsTest {
    private val data = PetEmotions.parse(SyntheticPet.EMOTIONS_JSON)
    private val rig = PetRig.parse(SyntheticPet.face())

    private fun fails(json: String): String {
        try { PetEmotions.parse(json) } catch (e: IllegalArgumentException) { return e.message.orEmpty() }
        throw AssertionError("se esperaba IllegalArgumentException")
    }

    // ---------------------------------------------------------------- emociones.json

    @Test
    fun parsesFacesEmotionsMoodsAndAtlas() {
        assertEquals(setOf("neutral", "happy", "sad"), data.faces.keys)
        val eye = data.faces["happy"]!!["eye_L"]!!
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), eye.loc, 0f) // lo que falta queda neutro
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), eye.rot, 0f)
        assertArrayEquals(floatArrayOf(1f, 0.6f, 1f), eye.scale, 0f)
        val tear = data.faces["sad"]!!["tear"]!!
        assertArrayEquals(floatArrayOf(0.1f, -0.05f, 0.02f), tear.loc, 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 15f), tear.rot, 0f)

        assertEquals(listOf("idle", "happy", "study", "speak", "listen", "float", "sleep", "celebrate"), data.emotions.keys.toList())
        val study = data.emotions["study"]!!
        assertEquals("Estudiar", study.label)
        assertEquals("read", study.clip)
        assertEquals("neutral", study.face)
        assertEquals(listOf("book", "laptop"), study.props)
        assertTrue(study.blink)
        assertFalse(study.talk)
        assertTrue(data.emotions["speak"]!!.talk)
        assertFalse(data.emotions["sleep"]!!.blink)
        assertEquals(emptyList<String>(), data.emotions["listen"]!!.props) // sin props ni fx: vacío
        assertEquals(0, data.emotions["listen"]!!.fx.size)

        val happyFx = data.emotions["happy"]!!.fx[0]
        assertEquals("sparkle", happyFx.sprite)
        assertEquals("head", happyFx.bone)
        assertArrayEquals(floatArrayOf(0.75f, 0.95f, 0.2f), happyFx.at, 0f)
        assertEquals(0.22f, happyFx.size, 0f)
        assertEquals("twinkle", happyFx.anim)
        assertEquals(1, happyFx.count)
        assertEquals(0f, happyFx.rot, 0f)
        assertFalse(happyFx.back)
        val notes = study.fx[0]
        assertEquals("nariz", notes.bone)
        assertEquals(3, notes.count)
        assertFalse(notes.back) // sin "layer" = front
        assertTrue(study.fx[1].back)
        assertEquals("world", study.fx[1].bone)
        assertEquals(30f, study.fx[1].rot, 0f)
        assertFalse(study.fx[2].back) // "layer": "front" explícito
        assertEquals("root", study.fx[3].bone) // sin "bone" = root
        assertEquals(0.2f, study.fx[3].size, 0f)

        assertEquals("speak", data.moods["speaking"])
        assertEquals("listen", data.moods["listening"]) // las claves se normalizan a minúsculas

        val atlas = data.atlas!!
        assertEquals(256, atlas.width)
        assertEquals(128, atlas.height)
        assertArrayEquals(floatArrayOf(0.5f / 256, 0.5f / 128, 63.5f / 256, 63.5f / 128), atlas.uv("sparkle")!!, 1e-7f)
        assertNull(atlas.uv("nope"))
    }

    @Test
    fun rejectsMalformedDocumentsAndToleratesMissingSections() {
        assertTrue(fails("{").isNotEmpty())
        assertTrue(fails("[]").contains("objeto"))
        assertTrue(fails("""{"version": 2}""").contains("versión"))
        assertTrue(fails("""{"emotions": {"a": 3}}""").contains("emotions.a"))
        assertTrue(fails("""{"emotions": {"a": {"fx": [{"size": 1}]}}}""").contains("sprite"))
        assertTrue(fails("""{"emotions": {"a": {"fx": [{"sprite": "x", "layer": 3}]}}}""").contains("layer"))
        assertTrue(fails("""{"emotions": {"a": {"props": [1]}}}""").contains("props"))
        assertTrue(fails("""{"atlas": {"size": [0, 10], "sprites": {}}}""").contains("atlas"))
        assertTrue(fails("""{"atlas": {"size": [10, 10], "sprites": {"s": [1, 2, 3]}}}""").contains("atlas.sprites.s"))
        assertTrue(fails("""{"faces": {"f": {"b": {"loc": [1, 2]}}}}""").contains("3 números"))
        val minimal = PetEmotions.parse("﻿{}") // con marca BOM y sin secciones
        assertEquals("idle", minimal.resolve("lo-que-sea").id)
        assertNull(minimal.atlas)
        assertEquals("Celebrate", PetEmotions.parse("""{"version": 1, "emotions": {"Celebrate": {}}}""").resolve("celebrate").id)
        // un layer desconocido no rompe el avatar: se dibuja delante
        assertFalse(PetEmotions.parse("""{"emotions": {"a": {"fx": [{"sprite": "x", "layer": "otro"}]}}}""").emotions["a"]!!.fx[0].back)
    }

    @Test
    fun resolvesMoodsLegacyTextsAndUnknownToIdle() {
        assertEquals("happy", data.resolve("happy").id)
        assertEquals("happy", data.resolve("  HAPPY ").id)
        assertEquals("speak", data.resolve("speaking").id) // tabla moods
        assertEquals("listen", data.resolve("Listening").id)
        assertEquals("float", data.resolve("floating").id) // textos de siempre vía PetAnimationController
        assertEquals("study", data.resolve("studying").id)
        assertEquals("sleep", data.resolve("sleeping").id)
        assertEquals("celebrate", data.resolve("celebrar").id)
        assertEquals("idle", data.resolve("zzz").id)
        assertEquals("idle", data.resolve("").id)
        assertEquals("idle", data.resolve(null).id)
        assertEquals("idle", data.resolve("greet").id) // existe como estado pero este JSON no la define
    }

    // ---------------------------------------------------------------- cara

    private fun driver() = PetFaceDriver(rig, data, seed = 7L)
    private fun scaleOf(d: PetFaceDriver, bone: String) = d.locals.copyOfRange(rig.boneIndex(bone) * 9 + 6, rig.boneIndex(bone) * 9 + 9)

    @Test
    fun blendsFacesOver150msAndIgnoresUnknownBones() {
        val d = driver()
        d.update(0.0)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), scaleOf(d, "eye_L"), 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), scaleOf(d, "smile"), 0f) // oculta en reposo
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), scaleOf(d, "book"), 0f)
        d.setEmotion("happy", 1.0)
        d.update(1.0)
        assertEquals(1f, scaleOf(d, "eye_L")[1], 1e-6f)
        d.update(1.075) // a mitad de camino
        assertEquals(0.8f, scaleOf(d, "eye_L")[1], 1e-5f)
        assertEquals(0.5f, scaleOf(d, "smile")[1], 1e-5f)
        d.update(1.15)
        assertEquals(0.6f, scaleOf(d, "eye_L")[1], 1e-5f)
        assertEquals(1f, scaleOf(d, "smile")[1], 1e-5f)
        assertEquals(0.01f, d.locals[rig.boneIndex("smile") * 9 + 1], 1e-6f)
        d.update(1.4)
        assertEquals(0.6f, scaleOf(d, "eye_R")[1], 1e-5f)
        // cambiar otra vez a mitad de la mezcla parte de lo que se veía: ojos a medio camino entre «happy» (0,6) y «sad» (1)
        d.setEmotion("sad", 2.0)
        d.update(2.075)
        assertEquals(0.5f, scaleOf(d, "smile")[1], 1e-5f) // la sonrisa sigue en 1 → 0 a mitad de camino
        d.setEmotion("idle", 2.075)
        d.update(2.075)
        assertEquals(0.8f, scaleOf(d, "eye_L")[1], 1e-5f)
        assertEquals("idle", d.emotion.id) // y el hueso «ghost» de la cara «happy» no existe en este avatar: se ignora sin fallar
    }

    @Test
    fun propsBecomeVisibleOnlyWhileTheEmotionAsksForThem() {
        val d = driver()
        d.setEmotion("study", 0.0, instant = true)
        d.update(0.0)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), scaleOf(d, "book"), 0f) // «laptop» no existe aquí y se ignora
        d.setEmotion("idle", 5.0)
        d.update(5.2)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), scaleOf(d, "book"), 0f)
    }

    @Test
    fun reducedMotionSwitchesFacesAtOnce() {
        val d = driver()
        d.reducedMotion = true
        d.update(0.0)
        d.setEmotion("happy", 1.0)
        d.update(1.0)
        assertEquals(0.6f, scaleOf(d, "eye_L")[1], 1e-6f)
    }

    @Test
    fun blinksEveryTwoAndAHalfToFiveSecondsAndNeverWithReducedMotion() {
        val eyeL = rig.boneIndex("eye_L") * 9 + 7
        val eyeR = rig.boneIndex("eye_R") * 9 + 7
        val d = driver()
        val starts = ArrayList<Double>()
        var closed = false; var low = 1f
        var i = 0
        while (i <= 40 * 30) {
            val t = i / 30.0
            d.update(t)
            val s = d.locals[eyeL]
            assertEquals(s, d.locals[eyeR], 0f) // los dos ojos a la vez
            if (s < 1f && !closed) { starts.add(t); closed = true } else if (s >= 1f) closed = false
            low = min(low, s)
            i++
        }
        assertTrue("parpadeos: ${starts.size}", starts.size >= 7)
        assertTrue(low < 0.5f && low >= 0.079f)
        assertTrue("primero: ${starts[0]}", starts[0] in 2.5..5.1)
        for (k in 1 until starts.size) assertTrue("intervalo ${starts[k] - starts[k - 1]}", starts[k] - starts[k - 1] in 2.5..5.3)

        val r = driver(); r.reducedMotion = true
        var seen = 1f
        i = 0
        while (i <= 40 * 30) { r.update(i / 30.0); seen = min(seen, r.locals[eyeL]); i++ }
        assertEquals(1f, seen, 0f)

        val s = driver(); s.setEmotion("sleep", 0.0, instant = true) // blink = false
        seen = 1f; i = 0
        while (i <= 40 * 30) { s.update(i / 30.0); seen = min(seen, s.locals[eyeL]); i++ }
        assertEquals(1f, seen, 0f)
    }

    @Test
    fun blinkMultipliesWhateverTheFaceSays() {
        val d = driver()
        d.setEmotion("happy", 0.0, instant = true) // ojos a 0.6 en Y
        val eyeL = rig.boneIndex("eye_L") * 9 + 7
        var low = 1f
        var i = 0
        while (i <= 10 * 30) { d.update(i / 30.0); low = min(low, d.locals[eyeL]); i++ }
        assertTrue(low < 0.6f * 0.5f && low >= 0.6f * 0.079f)
    }

    @Test
    fun talkingMouthFollowsTheSpecFormula() {
        val m = rig.boneIndex("mouth") * 9
        val d = driver(); d.setEmotion("speak", 0.0, instant = true)
        var top = 0f; var i = 0
        while (i < 3 * 30) {
            d.update(i / 30.0)
            val a = (d.locals[m + 7] - 1f) / 2.4f
            assertTrue("a=$a", a >= 0f && a <= 1.0001f)
            assertEquals(1f - 0.1f * a, d.locals[m + 6], 1e-4f)
            assertEquals(1f + 0.35f * a, d.locals[m + 8], 1e-4f)
            assertEquals(-0.012f * a, d.locals[m + 1], 1e-5f)
            assertEquals(0.006f * a, d.locals[m + 2], 1e-5f)
            top = max(top, a); i++
        }
        assertTrue("a máxima $top", top > 0.3f)

        // nivel externo = 1: boca abierta del todo (con un poco de suavizado)
        val e = driver(); e.talkLevel = 1f
        i = 0; while (i < 30) { e.update(i / 30.0); i++ }
        assertArrayEquals(floatArrayOf(0.9f, 3.4f, 1.35f), e.locals.copyOfRange(m + 6, m + 9), 1e-3f)
        assertArrayEquals(floatArrayOf(0f, -0.012f, 0.006f), e.locals.copyOfRange(m, m + 3), 1e-4f)
        // y vuelve a cerrarse
        e.talkLevel = 0f
        i = 30; while (i < 90) { e.update(i / 30.0); i++ }
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), e.locals.copyOfRange(m + 6, m + 9), 1e-6f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), e.locals.copyOfRange(m, m + 3), 1e-6f)

        // sin hablar y sin nivel externo, la boca queda como la cara
        val q = driver()
        i = 0; while (i < 30) { q.update(i / 30.0); i++ }
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), q.locals.copyOfRange(m + 6, m + 9), 0f)

        // con movimiento reducido: entreabierta y quieta
        val r = driver(); r.reducedMotion = true; r.talkLevel = 0.9f
        r.update(0.0); val first = r.locals.copyOfRange(m, m + 9)
        r.talkLevel = 0.2f; r.update(0.5)
        assertArrayEquals(first, r.locals.copyOfRange(m, m + 9), 0f)
        assertEquals(1f + 2.4f * 0.35f, first[7], 1e-5f)
    }

    // ---------------------------------------------------------------- efectos 2D

    private val atlas = PetAtlas(256, 128, mapOf("sparkle" to floatArrayOf(0f, 0f, 64f, 64f), "note" to floatArrayOf(64f, 0f, 64f, 64f)))

    /** root y los demás huesos a la identidad; head desplazado (1, 2, 3). */
    private fun skin() = FloatArray(rig.boneCount * 12).also { s ->
        for (i in 0 until rig.boneCount) System.arraycopy(SyntheticPet.IDENTITY, 0, s, i * 12, 12)
        val h = rig.boneIndex("head") * 12
        s[h + 3] = 1f; s[h + 7] = 2f; s[h + 11] = 3f
    }

    private fun spec(anim: String, phase: Float = 0f, count: Int = 1, bone: String = "head", back: Boolean = false, rot: Float = 0f, size: Float = 0.5f, sprite: String = "sparkle") =
        PetFxSpec(sprite, bone, floatArrayOf(0.5f, 0.25f, 0.125f), size, anim, phase, count, rot, back)

    private fun fxOf(vararg specs: PetFxSpec, atlas: PetAtlas? = this.atlas) =
        PetFx(rig, atlas).also { it.bind(PetEmotion("t", "t", "idle", "", emptyList(), true, false, specs.toList())) }

    private fun one(s: PetFxSpec, age: Float, reduced: Boolean = false): PetFx.Sprite {
        val fx = fxOf(s); fx.update(age, skin(), reduced)
        assertEquals(1, fx.count)
        return fx.sprites[0]
    }

    private fun assertSprite(sp: PetFx.Sprite, x: Float, y: Float, size: Float, rot: Float, alpha: Float, z: Float = 3.125f) {
        assertEquals("x", x, sp.x, 1e-5f); assertEquals("y", y, sp.y, 1e-5f); assertEquals("z", z, sp.z, 1e-5f)
        assertEquals("size", size, sp.size, 1e-5f); assertEquals("rot", rot, sp.rot, 1e-3f); assertEquals("alpha", alpha, sp.alpha, 1e-5f)
    }

    @Test
    fun spriteIsCenteredOnTheBoneTransformedRestPointWithItsAtlasRect() {
        val sp = one(spec("", bone = "head"), 1f)
        assertSprite(sp, 1.5f, 2.25f, 0.5f, 0f, 1f)
        assertEquals(0.5f / 256, sp.u0, 1e-7f); assertEquals(0.5f / 128, sp.v0, 1e-7f)
        assertEquals(63.5f / 256, sp.u1, 1e-7f); assertEquals(63.5f / 128, sp.v1, 1e-7f)
        val note = one(spec("", sprite = "note"), 1f)
        assertEquals(64.5f / 256, note.u0, 1e-7f)
        // «root» y un hueso que no existe usan root (identidad): el punto de reposo tal cual
        assertSprite(one(spec("", bone = "root"), 1f), 0.5f, 0.25f, 0.5f, 0f, 1f, z = 0.125f)
        assertSprite(one(spec("", bone = "nariz"), 1f), 0.5f, 0.25f, 0.5f, 0f, 1f, z = 0.125f)
        // sprites que el atlas no tiene, o sin atlas, no se dibujan
        val none = fxOf(spec("", sprite = "otro")); none.update(1f, skin(), false)
        assertEquals(0, none.count)
        val noAtlas = fxOf(spec(""), atlas = null); noAtlas.update(1f, skin(), false)
        assertEquals(0, noAtlas.count)
    }

    @Test
    fun fadesInOverTwoTenthsOfASecond() {
        assertEquals(0f, one(spec(""), 0f).alpha, 1e-6f)
        assertEquals(0.5f, one(spec(""), 0.1f).alpha, 1e-6f)
        assertEquals(1f, one(spec(""), 0.2f).alpha, 1e-6f)
        assertEquals(1f, one(spec(""), 7f).alpha, 1e-6f)
    }

    @Test
    fun twinkleScalesBetweenQuarterAndFullAndPulsesAlpha() {
        // 1.4·t + fase = 1.25 → seno 1; = 1.75 → seno -1
        assertSprite(one(spec("twinkle", phase = 0.69f), 0.4f), 1.5f, 2.25f, 0.5f * 1.0f, 0f, 1f)
        assertSprite(one(spec("twinkle", phase = 0.19f), 0.4f), 1.5f, 2.25f, 0.5f * 0.5f, 0f, 0.85f)
    }

    @Test
    fun floatUpRisesFadesInTheLastFortyPercentAndSwaysSideways() {
        assertSprite(one(spec("float_up"), 0.8f), 1.5f, 2.25f + 0.175f, 0.5f, 0f, 1f) // u = 0,5
        val late = one(spec("float_up"), 1.44f) // u = 0,9
        assertEquals(2.25f + 0.315f, late.y, 1e-5f)
        assertEquals(0.25f, late.alpha, 1e-5f)
        assertEquals(1.5f + 0.05f * Math.sin(2 * Math.PI * 0.9).toFloat(), late.x, 1e-5f)
        assertEquals(1.5f + 0.05f, one(spec("float_up"), 0.4f).x, 1e-5f) // u = 0,25 → seno 1
        assertEquals(2.25f, one(spec("float_up"), 1.6f).y, 1e-5f) // el ciclo vuelve a empezar
        val four = fxOf(spec("float_up", count = 4)); four.update(0.8f, skin(), false)
        assertEquals(4, four.count)
        val dy = FloatArray(4) { four.sprites[it].y - 2.25f }
        assertArrayEquals(floatArrayOf(0.175f, 0.2625f, 0f, 0.0875f), dy, 1e-5f) // fases repartidas 0, ¼, ½, ¾
        assertEquals(0.625f, four.sprites[1].alpha, 1e-5f)
    }

    @Test
    fun bobPopShakeAndPulse() {
        assertEquals(2.25f + 0.04f, one(spec("bob", phase = 0.8f), 0.5f).y, 1e-5f) // 0,9·0,5 + 0,8 = 1,25
        // pop: 0 → 1 con rebote en 0,25 s, luego bob suave
        assertEquals(0f, one(spec("pop"), 0f).size, 1e-6f)
        assertEquals(0.5f * 1.0876975f, one(spec("pop"), 0.125f).size, 1e-4f)
        assertTrue(one(spec("pop"), 0.18f).size > 0.5f) // rebasa 1 antes de asentarse
        assertEquals(0.5f, one(spec("pop"), 0.25f).size, 1e-6f)
        val settled = one(spec("pop"), 1f)
        assertEquals(0.5f, settled.size, 1e-6f)
        assertEquals(2.25f + 0.02f * Math.sin(2 * Math.PI * 0.9).toFloat(), settled.y, 1e-5f)
        // shake: ±12° a 6 Hz (más el giro inicial del fx)
        assertEquals(12f, one(spec("shake"), 1f / 24f).rot, 1e-3f)
        assertEquals(30f + 12f, one(spec("shake", rot = 30f), 1f / 24f).rot, 1e-3f)
        assertEquals(-12f, one(spec("shake"), 3f / 24f).rot, 1e-3f)
        // pulse: 1 ± 0,12 a 2 Hz
        assertEquals(0.5f * 1.12f, one(spec("pulse"), 0.125f).size, 1e-5f)
        assertEquals(0.5f * 0.88f, one(spec("pulse"), 0.375f).size, 1e-5f)
        // spin: vuelta completa en el plano cada 2 s
        assertEquals(180f, one(spec("spin"), 1f).rot, 1e-3f)
        assertEquals(190f, one(spec("spin", rot = 10f), 1f).rot, 1e-3f)
        assertEquals(270f, one(spec("spin"), 1.5f).rot, 1e-3f)
        assertEquals(270f, one(spec("spin", phase = 0.25f), 1f).rot, 1e-3f) // la fase va en vueltas: 0,5 + 0,25 = 0,75
        // bounce: salto |seno| de 0,12 que nunca baja del suelo
        assertEquals(2.25f + 0.12f, one(spec("bounce"), 0.25f).y, 1e-5f)
        assertEquals(2.25f + 0.12f, one(spec("bounce"), 0.75f).y, 1e-5f)
        assertEquals(2.25f + 0.12f * 0.70710678f, one(spec("bounce"), 0.125f).y, 1e-5f)
        assertEquals(2.25f, one(spec("bounce"), 0.5f).y, 1e-5f)
        var t = 0.2f
        while (t < 3f) { val y = one(spec("bounce", phase = 0.3f), t).y; assertTrue("t=$t y=$y", y >= 2.25f - 1e-6f && y <= 2.25f + 0.12f + 1e-6f); t += 0.037f }
        // none (y cualquier nombre desconocido): quieto, solo con el fundido de entrada
        assertSprite(one(spec("none", phase = 0.3f, rot = 15f), 3f), 1.5f, 2.25f, 0.5f, 15f, 1f)
        assertSprite(one(spec("no-existe"), 3f), 1.5f, 2.25f, 0.5f, 0f, 1f)
    }

    @Test
    fun worldBoneKeepsTheAtPointInModelSpaceAndUnknownBonesFallBackToRoot() {
        val s = skin()
        s[3] = 10f; s[7] = 20f; s[11] = 30f // root (hueso 0) se movió
        fun center(bone: String, anim: String = "", age: Float = 1f): FloatArray {
            val fx = fxOf(spec(anim, bone = bone)); fx.update(age, s, false)
            return floatArrayOf(fx.sprites[0].x, fx.sprites[0].y, fx.sprites[0].z)
        }
        assertArrayEquals(floatArrayOf(0.5f, 0.25f, 0.125f), center("world"), 1e-6f)
        assertArrayEquals(floatArrayOf(10.5f, 20.25f, 30.125f), center("root"), 1e-6f)
        assertArrayEquals(floatArrayOf(10.5f, 20.25f, 30.125f), center("hand_L"), 1e-6f) // no existe: root
        assertArrayEquals(floatArrayOf(1.5f, 2.25f, 3.125f), center("head"), 1e-6f)
        // la animación se suma sobre el ancla «world»
        assertArrayEquals(floatArrayOf(0.5f, 0.25f + 0.12f, 0.125f), center("world", "bounce", 0.25f), 1e-5f)
    }

    @Test
    fun fallDropsSixTenthsWithSpinAndSpreadsHorizontallyByPhase() {
        val mid = one(spec("fall"), 0.9f) // u = 0,5
        assertEquals(2.25f, mid.y, 1e-5f)
        assertEquals(180f, mid.rot, 1e-3f)
        assertEquals(1f, mid.alpha, 1e-6f)
        assertEquals(1.5f + (0.17f - 0.5f) * 0.6f, mid.x, 1e-5f)
        assertEquals(2.25f + 0.3f - 0.6f * (0.2f / 1.8f), one(spec("fall"), 0.2f).y, 1e-5f)
        assertEquals(0.4f, one(spec("fall"), 1.62f).alpha, 1e-4f) // se apaga al final del recorrido
        val six = fxOf(spec("fall", count = 6)); six.update(1f, skin(), false)
        assertEquals(6, six.count)
        val xs = HashSet<Float>(); for (i in 0 until 6) xs.add(six.sprites[i].x)
        assertEquals(6, xs.size)
    }

    @Test
    fun reducedMotionLeavesSpritesStill() {
        for (anim in listOf("twinkle", "bob", "pop", "shake", "pulse", "spin", "bounce", "none", "")) {
            assertSprite(one(spec(anim, phase = 0.37f), 4.3f, reduced = true), 1.5f, 2.25f, 0.5f, 0f, 1f)
        }
        assertSprite(one(spec("shake", rot = 20f), 4.3f, reduced = true), 1.5f, 2.25f, 0.5f, 20f, 1f)
        // el tiempo no cambia nada, y los repetidos conservan su reparto
        val a = fxOf(spec("float_up", count = 3)); a.update(0f, skin(), true)
        val b = fxOf(spec("float_up", count = 3)); b.update(9f, skin(), true)
        for (i in 0 until 3) { assertEquals(a.sprites[i].y, b.sprites[i].y, 0f); assertEquals(a.sprites[i].alpha, b.sprites[i].alpha, 0f) }
        assertTrue(a.sprites[0].y != a.sprites[1].y)
        // sin fundido de entrada
        assertEquals(1f, one(spec("", phase = 0f), 0f, reduced = true).alpha, 0f)
    }

    @Test
    fun backLayerSpritesComeFirstAndKeepTheirOrder() {
        val fx = fxOf(spec("", size = 0.1f), spec("", size = 0.2f, back = true), spec("", size = 0.3f), spec("", size = 0.4f, back = true, count = 2),
            spec("", size = 0.6f, sprite = "otro"))
        fx.update(1f, skin(), false)
        assertEquals(5, fx.count)
        assertEquals(3, fx.backCount)
        assertArrayEquals(floatArrayOf(0.2f, 0.4f, 0.4f, 0.1f, 0.3f), FloatArray(5) { fx.sprites[it].size }, 1e-6f)
        // solo front / solo back / nada
        val front = fxOf(spec("")); front.update(1f, skin(), false)
        assertEquals(0, front.backCount); assertEquals(1, front.count)
        val back = fxOf(spec("", back = true), spec("", back = true)); back.update(1f, skin(), false)
        assertEquals(2, back.backCount); assertEquals(2, back.count)
        val empty = fxOf(); empty.update(1f, skin(), false)
        assertEquals(0, empty.backCount); assertEquals(0, empty.count)
        // al cambiar de emoción se vuelve a repartir
        back.bind(PetEmotion("x", "x", "idle", "", emptyList(), true, false, listOf(spec(""))))
        back.update(1f, skin(), false)
        assertEquals(0, back.backCount); assertEquals(1, back.count)
        // con la emoción del JSON: glow detrás (rot 30), notas ×3 y destello delante; el sprite «missing» no está en el atlas
        val study = PetFx(rig, data.atlas); study.bind(data.emotions["study"]); study.update(1f, skin(), false)
        assertEquals(5, study.count); assertEquals(1, study.backCount)
        assertEquals(1.1f, study.sprites[0].size, 1e-6f); assertEquals(30f, study.sprites[0].rot, 1e-6f)
        assertEquals(-1f, study.sprites[0].y, 1e-6f) // «world»: el punto «at» sin transformar
        assertEquals(0.1f, study.sprites[4].size, 1e-6f)
    }

    @Test
    fun neverExceedsTheSpritePool() {
        val fx = fxOf(*Array(10) { spec("", count = 16) })
        fx.update(1f, skin(), false)
        assertEquals(PetFx.MAX_SPRITES, fx.count)
    }
}
