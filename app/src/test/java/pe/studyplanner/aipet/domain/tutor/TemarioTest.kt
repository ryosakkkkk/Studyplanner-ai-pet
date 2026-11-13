package pe.studyplanner.aipet.domain.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** La tabla del temario oficial y la referencia que cada tema del tutor hace a ella. */
class TemarioTest {

    @Test
    fun laTablaTieneLosItemsDelTemarioOficial() {
        assertEquals(12, Temario.itemCount(Area.ARITMETICA))
        assertEquals(13, Temario.itemCount(Area.ALGEBRA))
        assertEquals(16, Temario.itemCount(Area.GEOMETRIA))
        assertEquals(10, Temario.itemCount(Area.TRIGONOMETRIA))
        assertEquals(9, Temario.itemCount(Area.RAZONAMIENTO))
        for (area in Area.values().filter { it.math }) {
            for (item in 1..Temario.itemCount(area)) assertTrue("$area $item", Temario.title(area, item).isNotBlank())
        }
    }

    @Test
    fun algunosTitulosOficialesCoincidenConElDocumento() {
        assertEquals("Ecuaciones lineales y cuadráticas", Temario.title(Area.ALGEBRA, 7))
        assertEquals("Sistema de ecuaciones y matrices", Temario.title(Area.ALGEBRA, 8))
        assertEquals("Reglas de tres", Temario.title(Area.ARITMETICA, 11))
        assertEquals("Máximo común divisor y mínimo común múltiplo", Temario.title(Area.ARITMETICA, 8))
        assertEquals("Regla de interés", Temario.title(Area.ARITMETICA, 12))
        assertEquals("Logaritmos", Temario.title(Area.ALGEBRA, 11))
        assertEquals("Triángulos y líneas notables", Temario.title(Area.GEOMETRIA, 3))
        assertEquals("Relaciones métricas en triángulos y circunferencias", Temario.title(Area.GEOMETRIA, 10))
        assertEquals("Razones trigonométricas de un ángulo agudo", Temario.title(Area.TRIGONOMETRIA, 2))
        assertEquals("Sucesiones, series y sumas notables", Temario.title(Area.RAZONAMIENTO, 5))
        assertEquals("Promedios", Temario.title(Area.RAZONAMIENTO, 7))
    }

    @Test
    fun laEtiquetaSeEscribeConAreaItemYTitulo() {
        assertEquals("Álgebra 7 · Ecuaciones lineales y cuadráticas", TemarioRef(Area.ALGEBRA, 7).label)
        assertEquals(
            "Aritmética 11 · Reglas de tres (tanto por ciento)",
            TemarioRef(Area.ARITMETICA, 11, "tanto por ciento").label,
        )
        assertEquals("Razonamiento matemático 7 · Promedios", TemarioRef(Area.RAZONAMIENTO, 7).label)
    }

    @Test
    fun unItemInexistenteSeRechaza() {
        for ((area, item) in listOf(Area.ALGEBRA to 0, Area.ALGEBRA to 14, Area.ARITMETICA to 13, Area.RAZONAMIENTO to 10, Area.TRIGONOMETRIA to -1)) {
            try {
                TemarioRef(area, item)
                fail("debía rechazar $area $item")
            } catch (expected: IllegalArgumentException) {
                // correcto
            }
        }
    }

    @Test
    fun unTemaSinReferenciaAlTemarioNoSePuedeCrear() {
        val example = checkNotNull(TutorRegistry.topics.first().workedExample)
        try {
            Topic("sin-temario", Area.ALGEBRA, "Título", emptyList(), "Objetivo", listOf("a", "b", "c"), example)
            fail("un tema sin temario debía rechazarse")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("sin-temario"))
        }
    }
}
