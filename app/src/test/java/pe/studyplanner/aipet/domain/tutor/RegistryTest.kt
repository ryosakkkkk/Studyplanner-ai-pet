package pe.studyplanner.aipet.domain.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RegistryTest {

    @Test
    fun hayCatorceTemasConIdsUnicosYEstables() {
        val ids = TutorRegistry.topics.map { it.id }
        assertEquals(
            listOf(
                "ecuaciones-lineales", "sistemas-2x2", "porcentajes", "regla-de-tres", "mcd-mcm", "interes-simple",
                "leyes-de-exponentes", "ecuaciones-cuadraticas", "logaritmos", "angulos-triangulo", "pitagoras",
                "razones-trigonometricas", "sucesiones-aritmeticas", "promedios",
            ),
            ids,
        )
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(ids, TutorRegistry.generators.map { it.topicId })
    }

    @Test
    fun cadaTemaTieneObjetivoYDeTresACincoParrafos() {
        for (t in TutorRegistry.topics) {
            assertTrue(t.id, t.title.isNotBlank())
            assertTrue(t.id, t.objective.isNotBlank())
            assertTrue("${t.id}: ${t.explanation.size} párrafos", t.explanation.size in 3..5)
            assertTrue(t.id, t.explanation.all { it.isNotBlank() })
        }
    }

    @Test
    fun cadaTemaCitaElTemarioOficialDeLaUnamba() {
        for (t in TutorRegistry.topics) {
            assertTrue("${t.id} sin referencia al temario", t.temario.isNotEmpty())
            assertTrue("${t.id}: el área del tema debe figurar entre sus referencias", t.area in t.temario.map { it.area })
            assertEquals("${t.id}: referencias repetidas", t.temario.size, t.temario.map { it.area to it.item }.toSet().size)
            for (ref in t.temario) {
                assertTrue("${t.id}: ítem ${ref.item} fuera del temario", ref.item in 1..Temario.itemCount(ref.area))
                assertTrue("${t.id}: ${ref.label}", ref.title.isNotBlank() && ref.label.startsWith("${ref.area.label} ${ref.item} · "))
            }
            assertTrue(t.id, t.temarioLabel.startsWith("Temario UNAMBA: "))
            assertTrue(t.id, t.temario.all { t.temarioLabel.contains(it.label) })
        }
    }

    @Test
    fun elTemarioDeCadaTemaCoincideConLaTablaAcordada() {
        val expected = mapOf(
            "ecuaciones-lineales" to listOf("Álgebra 7"),
            "sistemas-2x2" to listOf("Álgebra 8"),
            "porcentajes" to listOf("Aritmética 11"),
            "regla-de-tres" to listOf("Aritmética 10", "Aritmética 11"),
            "mcd-mcm" to listOf("Aritmética 8"),
            "interes-simple" to listOf("Aritmética 12"),
            "leyes-de-exponentes" to listOf("Álgebra 1"),
            "ecuaciones-cuadraticas" to listOf("Álgebra 7"),
            "logaritmos" to listOf("Álgebra 11"),
            "angulos-triangulo" to listOf("Geometría 2", "Geometría 3"),
            "pitagoras" to listOf("Geometría 3", "Geometría 10"),
            "razones-trigonometricas" to listOf("Trigonometría 2", "Geometría 3"),
            "sucesiones-aritmeticas" to listOf("Razonamiento matemático 5", "Álgebra 12"),
            "promedios" to listOf("Razonamiento matemático 7"),
        )
        assertEquals("la tabla debe cubrir todos los temas", TutorRegistry.topics.map { it.id }.toSet(), expected.keys)
        for ((id, refs) in expected) {
            val topic = TutorRegistry.requireTopic(id)
            assertEquals(id, refs, topic.temario.map { "${it.area.label} ${it.item}" })
        }
        assertEquals(
            "Temario UNAMBA: Álgebra 7 · Ecuaciones lineales y cuadráticas",
            TutorRegistry.requireTopic("ecuaciones-lineales").temarioLabel,
        )
        assertEquals(
            "Temario UNAMBA: Aritmética 11 · Reglas de tres (tanto por ciento, aumentos y descuentos sucesivos)",
            TutorRegistry.requireTopic("porcentajes").temarioLabel,
        )
    }

    @Test
    fun elEjemploResueltoSaleDelGeneradorConLaSemillaReservada() {
        for (t in TutorRegistry.topics) {
            val e = TutorRegistry.generate(t.id, TutorRegistry.WORKED_EXAMPLE_SEED, TutorRegistry.WORKED_EXAMPLE_DIFFICULTY)
            val example = checkNotNull(t.workedExample)
            assertEquals(t.id, WorkedExample.from(e), example)
            assertTrue(t.id, example.steps.size >= 3)
            assertTrue(t.id, example.steps.last().contains(example.answer))
        }
    }

    @Test
    fun losTemasNuevosSeAgrupanEnSuAreaDelTemario() {
        assertEquals(Area.ARITMETICA, TutorRegistry.requireTopic("mcd-mcm").area)
        assertEquals(Area.ARITMETICA, TutorRegistry.requireTopic("interes-simple").area)
        assertEquals(Area.ALGEBRA, TutorRegistry.requireTopic("logaritmos").area)
        assertEquals(Area.RAZONAMIENTO, TutorRegistry.requireTopic("promedios").area)
        assertTrue(TutorRegistry.byArea(Area.ARITMETICA).map { it.id }.containsAll(listOf("mcd-mcm", "interes-simple")))
        assertTrue(TutorRegistry.byArea(Area.RAZONAMIENTO).map { it.id }.containsAll(listOf("sucesiones-aritmeticas", "promedios")))
    }

    @Test
    fun busquedasPorIdYPorArea() {
        assertNotNull(TutorRegistry.topic("pitagoras"))
        assertNull(TutorRegistry.topic("no-existe"))
        assertNull(TutorRegistry.generator("no-existe"))
        assertEquals(Area.GEOMETRIA, TutorRegistry.requireTopic("pitagoras").area)
        assertTrue(TutorRegistry.byArea(Area.ALGEBRA).size >= 3)
        assertEquals(TutorRegistry.topics.size, Area.values().sumOf { TutorRegistry.byArea(it).size })
    }

    @Test
    fun untemaDesconocidoSeRechazaConUnErrorClaro() {
        try {
            TutorRegistry.requireTopic("no-existe")
            fail("debía fallar")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("no-existe"))
        }
        try {
            TutorRegistry.generate("no-existe", 1L, 1)
            fail("debía fallar")
        } catch (expected: IllegalArgumentException) {
            // correcto
        }
        try {
            TutorSession.forTopic("no-existe")
            fail("debía fallar")
        } catch (expected: IllegalArgumentException) {
            // correcto
        }
    }
}
