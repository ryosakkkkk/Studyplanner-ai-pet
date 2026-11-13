package pe.studyplanner.aipet.domain.tutor

import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los dos bancos reales (los mismos archivos que van en el APK), validados pregunta por pregunta. */
class QuestionBankTest {

    private val topics get() = BankFiles.bankTopics
    private val catalog get() = BankFiles.catalog

    private fun plain(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

    // ---- carga ----

    @Test
    fun cargaLosDosBancosReales() {
        assertEquals(listOf(8, 7, 23), BankFiles.banks.map { it.topics.size })
        assertEquals(38, topics.size)
        assertEquals(38, topics.map { it.id }.toSet().size)
        assertEquals(274, topics.sumOf { it.questions.size })
        // ids de pregunta únicos en todos los bancos
        assertEquals(274, topics.flatMap { t -> t.questions.map { it.id } }.toSet().size)
        assertEquals(
            setOf(
                Area.ECOLOGIA, Area.GEOGRAFIA, Area.HISTORIA, Area.BIOLOGIA, Area.FISICA, Area.QUIMICA,
                Area.ZOOLOGIA, Area.CIVICA, Area.ECONOMIA, Area.COMUNICACION, Area.RAZ_VERBAL,
            ),
            topics.map { it.area }.toSet(),
        )
    }

    @Test
    fun elCatalogoSumaMatematicaMasBancos() {
        assertEquals(TutorRegistry.topics.size + 38, catalog.topics.size)
        assertEquals(TutorRegistry.topics.size + 38, catalog.generators.size)
        assertEquals(TutorRegistry.topics.map { it.id }, catalog.topics.take(TutorRegistry.topics.size).map { it.id })
    }

    // ---- cada pregunta ----

    @Test
    fun cadaPreguntaDelBancoEsValida() {
        var checked = 0
        for (t in topics) for (q in t.questions) {
            val ctx = "${t.id}/${q.id}"
            assertEquals(ctx, 5, q.options.size)
            assertEquals(ctx, 5, q.options.map { it.trim().lowercase() }.toSet().size) // exacto: «2p⁶» y «2p⁴» son distintas
            assertTrue(ctx, q.correctIndex in 0..4)
            assertEquals(ctx, (0..4).toSet() - q.correctIndex, q.optionFeedback.keys)
            assertTrue(ctx, q.optionFeedback.values.all { it.isNotBlank() })
            assertEquals(ctx, 3, q.hints.size)
            assertEquals(ctx, 3, q.hints.map { it.trim().lowercase() }.toSet().size)
            assertTrue(ctx, q.statement.isNotBlank() && q.explanation.isNotBlank())
            assertTrue(ctx, q.difficulty in 1..3)
            assertTrue("$ctx: sin fuentes", q.sources.isNotEmpty())
            for (s in q.sources) {
                assertTrue("$ctx: título", s.title.isNotBlank())
                assertTrue("$ctx: url ${s.url}", s.url.startsWith("https://") || s.url.startsWith("http://"))
                assertTrue("$ctx: fecha ${s.accessed}", Regex("""\d{4}-\d{2}-\d{2}""").matches(s.accessed))
            }
            // Ninguna pista regala la alternativa correcta ni ninguna alternativa depende de su posición.
            val correct = plain(q.options[q.correctIndex])
            if (correct.length >= 2) {
                q.hints.forEachIndexed { i, h ->
                    assertFalse("$ctx: la pista ${i + 1} contiene la respuesta", " ${plain(h)} ".contains(" $correct "))
                }
            }
            for (o in q.options) {
                assertFalse("$ctx: '$o'", Regex("""(?i)todas las anteriores|ninguna de las anteriores|ambas son correctas|\b(opci[oó]n|alternativa|letra) [A-E]\b""").containsMatchIn(o))
            }
            checked++
        }
        assertEquals(274, checked)
    }

    @Test
    fun cadaTemaDeBancoTieneExplicacionObjetivoYTemario() {
        for (t in topics) {
            assertTrue(t.id, t.explanation.size in 3..5)
            assertTrue(t.id, t.objective.isNotBlank() && t.title.isNotBlank())
            assertTrue(t.id, t.questions.size in 6..12)
            val topic = t.toTopic()
            assertEquals(t.id, t.area, topic.area)
            assertEquals(t.id, null, topic.workedExample)
            assertEquals(t.id, 1, topic.temario.size)
            val ref = topic.temario.single()
            assertEquals(t.id, t.area, ref.area)
            assertTrue(t.id, ref.item >= 1 && ref.title.isNotBlank())
            assertTrue(t.id, topic.temarioLabel.startsWith("Temario UNAMBA: ${t.area.label} ${ref.item} · "))
        }
    }

    @Test
    fun elTemarioDeCadaTemaDeBancoCoincideConElArchivo() {
        val expected = mapOf(
            "ecologia-anp-santuario-ampay" to "Temario UNAMBA: Ecología y medio ambiente 13 · Conservación de los Recursos Naturales. Áreas Naturales Protegidas",
            "geografia-espacio-geografico-apurimac" to "Temario UNAMBA: Geografía del Perú y del mundo 9 · Espacio Geográfico de Apurímac",
            "historia-tupac-amaru-micaela-bastidas" to "Temario UNAMBA: Historia del Perú 19 · Europa, América y el Perú en el Siglo XVIII",
            "historia-tahuantinsuyo-apurimac" to "Temario UNAMBA: Historia del Perú 11 · El Horizonte Tardío: El imperio del Tahuantinsuyo",
            "quimica-estructura-atomica" to "Temario UNAMBA: Química 3 · Estructura Atómica",
            "civica-constitucion-estado-peruano" to "Temario UNAMBA: Educación cívica 15 · Constitución Política del Perú y el Estado Peruano",
            "raz-verbal-sinonimia-contextual" to "Temario UNAMBA: Razonamiento verbal 3 · Sinonimia",
        )
        for ((id, label) in expected) assertEquals(id, label, catalog.requireTopic(id).temarioLabel)
    }

    @Test
    fun lasAreasSeAgrupanEnElOrdenDelTemarioConMatematicaPrimero() {
        val areas = catalog.areas
        assertEquals(Area.entries.size, areas.size)
        val firstBank = areas.indexOfFirst { !it.math }
        assertEquals(5, firstBank)
        assertTrue(areas.take(5).all { it.math })
        assertTrue(areas.drop(5).none { it.math })
        assertEquals(
            listOf(Area.FISICA, Area.QUIMICA, Area.ECOLOGIA, Area.BIOLOGIA, Area.ZOOLOGIA, Area.CIVICA, Area.GEOGRAFIA, Area.HISTORIA, Area.ECONOMIA, Area.COMUNICACION, Area.RAZ_VERBAL),
            areas.drop(5),
        )
        for (a in areas) assertTrue(a.name, catalog.byArea(a).isNotEmpty() && a.label.isNotBlank())
        assertEquals(catalog.topics.size, areas.sumOf { catalog.byArea(it).size })
    }

    // ---- generador: determinismo, barajado y repetición ----

    @Test
    fun elBarajadoEsDeterministaYReasignaLaRespuestaYLaRetroalimentacion() {
        for (t in topics) {
            val gen = catalog.generator(t.id)!!
            for (seed in 1L..120L) for (d in 1..3) {
                val e = gen.generate(seed, d)
                assertEquals("${t.id} s$seed", e, gen.generate(seed, d))
                val q = t.questions.first { it.statement == e.statement }
                assertEquals(q.options.toSet(), e.options.toSet())
                assertEquals("${t.id} s$seed: correcta", q.options[q.correctIndex], e.correctOption)
                assertEquals((0..4).toSet() - e.correctIndex, e.optionFeedback.keys)
                for ((i, text) in e.optionFeedback) {
                    val original = q.options.indexOf(e.options[i])
                    assertEquals("${t.id} s$seed: retroalimentación de ${e.options[i]}", q.optionFeedback.getValue(original), text)
                }
                assertEquals(q.hints, e.hints)
                assertEquals(q.sources, e.sources)
                assertEquals(listOf(q.explanation), e.solutionSteps)
                assertEquals(q.difficulty, e.difficulty)
            }
        }
    }

    @Test
    fun laPosicionDeLaCorrectaVariaConLaSemilla() {
        for (t in topics) {
            val gen = catalog.generator(t.id)!!
            val positions = (1L..200L).map { gen.generate(it, 2).correctIndex }.toSet()
            assertEquals(t.id, setOf(0, 1, 2, 3, 4), positions)
        }
    }

    @Test
    fun todaPreguntaSePuedeObtenerYLasSemillasLasRecorrenTodas() {
        for (t in topics) {
            val gen = catalog.generator(t.id)!!
            val seen = (1L..60L).map { gen.generate(it, 1).statement }.toSet()
            assertEquals(t.id, t.questions.map { it.statement }.toSet(), seen)
        }
    }

    @Test
    fun elIdRegeneraExactamenteElMismoEjercicioTambienConAvoid() {
        for (t in topics) {
            val gen = catalog.generator(t.id)!!
            val avoid = mutableSetOf<String>()
            for (seed in 1L..40L) {
                val e = gen.generate(seed * 7_919, ((seed % 3).toInt()) + 1, avoid)
                assertEquals(t.id, e, catalog.regenerate(e.id))
                avoid += e.statement
                if (avoid.size == t.questions.size) avoid.clear()
            }
        }
    }

    @Test
    fun conAvoidNuncaRepiteMientrasQuedenPreguntasSinUsar() {
        for (t in topics) {
            val gen = catalog.generator(t.id)!!
            for (base in listOf(1L, 99L, 123_456L, 5_000_000_000L)) {
                val used = mutableSetOf<String>()
                for (i in 0 until t.questions.size) {
                    val e = gen.generate(base + i * 13, listOf(1, 2, 2, 3, 1, 2)[i % 6], used)
                    assertTrue("${t.id}: repitió '${e.statement}' en la pregunta ${i + 1}", used.add(e.statement))
                }
                assertEquals(t.questions.size, used.size)
            }
        }
    }

    @Test
    fun conAvoidPrefiereLaDificultadPedida() {
        for (t in topics) {
            val gen = catalog.generator(t.id)!!
            for (d in 1..3) {
                val available = t.questions.map { it.difficulty }
                val best = available.minOf { Math.abs(it - d) }
                for (seed in 1L..30L) assertEquals("${t.id} d$d", best, Math.abs(gen.generate(seed, d, emptySet()).difficulty - d))
            }
        }
    }

    // ---- sesiones completas ----

    @Test
    fun cadaTemaDeBancoCompletaUnaSesionSinRepetirPreguntasNiEjemplo() {
        for (t in topics) for (seed in listOf(1L, 2L, 17L, 99L, 123_456L, 20_261_029L)) {
            val s = bankSession(t.id, seed)
            val first = s.start()
            assertEquals(t.id, TutorScreen.EXPLANATION, first.screen)
            val learnPages = first.progress.total
            assertEquals(t.id, t.explanation.size + 1, learnPages) // objetivo + párrafos, sin ejemplo resuelto
            val seen = s.playBankPerfect()
            assertFalse(seen.any { it.screen == TutorScreen.WORKED_EXAMPLE })
            val statements = seen.mapNotNull { it.exercise?.statement }.distinct()
            assertEquals("$seed ${t.id}: 3 guiadas + 2 sin ayuda distintas", 5, statements.size)
            val summary = checkNotNull(seen.last().summary)
            assertEquals(2, summary.unaidedCorrect)
            assertEquals(3, summary.guidedSolved)
        }
    }

    @Test
    fun saltarLaExplicacionYReintentarFuncionaConUnTemaDeBanco() {
        val s = bankSession("quimica-estructura-atomica", 3L)
        var st = s.toGuidedBank()
        assertEquals(Phase.GUIDED_PRACTICE, st.phase)
        repeat(4) { st = s.answer(wrongOfBank(st)) } // 3 errores muestran las 3 pistas; el cuarto, la solución
        assertEquals(TutorScreen.SOLUTION, st.screen)
        assertTrue(st.text.contains("La respuesta correcta es"))
        assertTrue(st.text.contains("Fuente: "))
        st = s.retry()
        assertEquals(TutorScreen.QUESTION, st.screen)
    }

    @Test
    fun lasFuentesSoloAparecenCuandoLaRespuestaYaEsVisible() {
        for (t in topics) {
            val s = bankSession(t.id, 5L)
            val seen = s.playBankPerfect()
            for (st in seen) {
                val view = st.exercise ?: continue
                when (st.screen) {
                    TutorScreen.QUESTION, TutorScreen.HINT, TutorScreen.WRONG_ANSWER -> {
                        assertTrue("${t.id}: fuentes antes de tiempo en ${st.screen}", view.sources.isEmpty())
                        assertFalse(st.text.contains("Fuente:"))
                    }
                    TutorScreen.CORRECT -> {
                        assertTrue(view.sources.isNotEmpty())
                        for (src in view.sources) assertTrue(st.text.contains("Fuente: ${src.title}"))
                        assertTrue(st.text.contains(view.solutionSteps.single()))
                    }
                    else -> Unit
                }
            }
            val items = checkNotNull(seen.last().summary).unaidedReview
            assertTrue(items.isNotEmpty() && items.all { it.sources.isNotEmpty() })
        }
    }

    @Test
    fun enLaComprobacionSinAyudaNoSeAdelantaNadaDeLaRespuesta() {
        val s = bankSession("fisica-cinematica-mru-mruv", 9L)
        s.skipLearn()
        repeat(3) { s.answer(bankExerciseOf(s.state).correctIndex); s.next() }
        var st = s.state
        assertEquals(TutorScreen.UNAIDED_INTRO, st.screen)
        st = s.next()
        repeat(2) {
            assertEquals(TutorScreen.QUESTION, st.screen)
            val view = checkNotNull(st.exercise)
            assertEquals(null, view.correctIndex)
            assertTrue(view.sources.isEmpty() && view.solutionSteps.isEmpty() && view.hintsShown.isEmpty())
            assertFalse(st.text.contains("Fuente:"))
            st = s.answer(wrongOfBank(st))
        }
        val summary = checkNotNull(st.summary)
        assertEquals(0, summary.unaidedCorrect)
        assertTrue(summary.unaidedReview.all { it.sources.isNotEmpty() && it.feedback.isNotBlank() })
    }

    @Test
    fun conAvoidAgotadoLaSesionSigueFuncionandoAunqueRepita() {
        val gen = catalog.generator("civica-constitucion-estado-peruano")!!
        val all = (1L..6L).map { gen.generate(it, 2).statement }.toMutableSet()
        val e = gen.generate(77L, 2, all)
        assertTrue(e.statement in all) // se acabaron las preguntas sin usar: repite en vez de fallar
    }

    // ---- errores de formato ----

    @Test
    fun unBancoMalFormadoSeRechazaConUnMensajeClaro() {
        fun message(json: String): String {
            try {
                QuestionBankParser.parse(json)
            } catch (e: QuestionBankException) {
                return e.message.orEmpty()
            }
            throw AssertionError("se esperaba QuestionBankException")
        }
        assertTrue(message("{").startsWith("JSON inválido"))
        assertTrue(message("""{"version":2,"topics":[]}""").contains("Versión de banco 2"))
        assertTrue(message("""{"version":1}""").contains("topics"))
        val good = BankFiles.text(BankFiles.NAMES[0])
        assertTrue(message(good.replaceFirst("\"area\": \"ECOLOGIA\"", "\"area\": \"ALGEBRA\"")).contains("área desconocida 'ALGEBRA'"))
        assertTrue(message(good.replaceFirst("\"correctIndex\": 1", "\"correctIndex\": 9")).contains("correctIndex"))
        assertNotEquals(0, QuestionBankParser.parse(good).topics.size)
    }
}

/** Atajos para las sesiones de banco (el catálogo real, no el solo de matemática). */
internal fun TutorSession.toGuidedBank(): TutorState {
    skipLearn()
    return state
}

internal fun wrongOfBank(state: TutorState): Int {
    val exercise = bankExerciseOf(state)
    val eliminated = checkNotNull(state.exercise).eliminated
    return exercise.options.indices.first { it != exercise.correctIndex && it !in eliminated }
}
