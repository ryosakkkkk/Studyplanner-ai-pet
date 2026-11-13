package pe.studyplanner.aipet.domain.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GeneratorDeterminismTest {

    private val seeds = 1L..40L

    private fun freshGenerators(): List<ExerciseGenerator> = listOf(
        LinearEquationGenerator(), LinearSystemGenerator(), PercentageGenerator(), RuleOfThreeGenerator(),
        McdMcmGenerator(), SimpleInterestGenerator(), ExponentLawsGenerator(), QuadraticGenerator(),
        LogarithmsGenerator(), TriangleAnglesGenerator(), PythagorasGenerator(), TrigRatiosGenerator(),
        ArithmeticSequenceGenerator(), AveragesGenerator(),
    )

    @Test
    fun mismaSemillaYDificultadDanElMismoEjercicio() {
        for (g in TutorRegistry.generators) for (d in 1..3) for (s in seeds) {
            assertEquals("${g.topicId} d$d s$s", g.generate(s, d), g.generate(s, d))
        }
    }

    @Test
    fun otraInstanciaDelGeneradorDaElMismoResultado() {
        val first = TutorRegistry.generators
        val second = freshGenerators()
        assertEquals("la lista de generadores nuevos debe cubrir todo el registro", first.map { it.topicId }, second.map { it.topicId })
        for (i in first.indices) for (d in 1..3) for (s in seeds) {
            assertEquals("${first[i].topicId} d$d s$s", first[i].generate(s, d), second[i].generate(s, d))
        }
    }

    @Test
    fun elResultadoNoDependeDelOrdenDeGeneracion() {
        for (g in TutorRegistry.generators) {
            val forward = seeds.map { g.generate(it, 2) }
            val backward = seeds.reversed().map { g.generate(it, 2) }.reversed()
            assertEquals(g.topicId, forward, backward)
        }
    }

    @Test
    fun semillasDistintasDanVariedad() {
        for (g in TutorRegistry.generators) for (d in 1..3) {
            val statements = (1L..100L).map { g.generate(it, d).statement }.toSet()
            assertTrue("${g.topicId} d$d solo tiene ${statements.size} enunciados distintos en 100 semillas", statements.size >= 10)
        }
    }

    @Test
    fun cadaEjercicioTieneLaEstructuraPedida() {
        for (g in TutorRegistry.generators) for (d in 1..3) for (s in seeds) {
            val e = g.generate(s, d)
            val where = e.id
            assertEquals(where, Exercise.OPTION_COUNT, e.options.size)
            assertEquals(where, Exercise.OPTION_COUNT, e.options.toSet().size)
            assertTrue(where, e.correctIndex in e.options.indices)
            assertEquals(where, Exercise.HINT_COUNT, e.hints.size)
            assertTrue(where, e.hints.all { it.isNotBlank() })
            assertEquals(where, d, e.difficulty)
            assertEquals(where, g.topicId, e.topicId)
            assertEquals(where, e.options.indices.toSet() - e.correctIndex, e.optionFeedback.keys)
            assertTrue(where, e.optionFeedback.values.all { it.isNotBlank() })
            assertTrue(where, e.solutionSteps.size >= 3)
            assertTrue("$where: el último paso debe nombrar la respuesta", e.solutionSteps.last().contains(e.correctOption))
        }
    }

    @Test
    fun ningunTextoTrazasDePlantillaNiCerosNegativos() {
        for (g in TutorRegistry.generators) for (d in 1..3) for (s in seeds) {
            val e = g.generate(s, d)
            val all = listOf(e.statement) + e.options + e.hints + e.solutionSteps + e.optionFeedback.values
            for (text in all) {
                assertTrue("${e.id}: '{' en \"$text\"", '{' !in text)
                assertTrue("${e.id}: 'null' en \"$text\"", !text.contains("null"))
                assertTrue("${e.id}: '-0' suelto en \"$text\"", !Regex("""(?<![\d,.])-0(?![\d,.])""").containsMatchIn(text))
            }
        }
    }

    @Test
    fun laRespuestaCorrectaCaeEnLasCincoPosiciones() {
        for (g in TutorRegistry.generators) {
            val positions = (1L..100L).map { g.generate(it, 2).correctIndex }.toSet()
            assertEquals(g.topicId, setOf(0, 1, 2, 3, 4), positions)
        }
    }

    @Test
    fun elIdPermiteRegenerarElMismoEjercicio() {
        for (g in TutorRegistry.generators) for (d in 1..3) for (s in seeds) {
            val e = g.generate(s, d)
            assertEquals(e.id, e, TutorRegistry.regenerate(e.id))
        }
    }

    @Test
    fun regenerarRechazaIdsInvalidos() {
        assertNull(TutorRegistry.regenerate(""))
        assertNull(TutorRegistry.regenerate("basura"))
        assertNull(TutorRegistry.regenerate("ecuaciones-lineales-d4-s1"))
        assertNull(TutorRegistry.regenerate("ecuaciones-lineales-d2-sabc"))
        assertNull(TutorRegistry.regenerate("tema-que-no-existe-d2-s1"))
        assertNotNull(TutorRegistry.regenerate("ecuaciones-lineales-d2-s1"))
    }

    @Test
    fun laDificultadFueraDeRangoSeRechaza() {
        val g = TutorRegistry.generators.first()
        for (bad in listOf(0, 4, -1)) {
            try {
                g.generate(1L, bad)
                fail("debía rechazar la dificultad $bad")
            } catch (expected: IllegalArgumentException) {
                // correcto
            }
        }
    }

    @Test
    fun laDificultadCambiaElEjercicio() {
        for (g in TutorRegistry.generators) {
            val ids = (1..3).map { g.generate(5L, it).id }.toSet()
            assertEquals(g.topicId, 3, ids.size)
        }
    }
}
