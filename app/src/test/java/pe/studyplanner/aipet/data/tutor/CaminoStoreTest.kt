package pe.studyplanner.aipet.data.tutor

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.studyplanner.aipet.domain.today.TodaySummary
import pe.studyplanner.aipet.domain.tutor.Attempt
import pe.studyplanner.aipet.domain.tutor.AttemptMode

class CaminoStoreTest {
    private val zone = ZoneId.of("America/Lima")
    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun losDiasSeGuardanYSeLeenIgual() {
        val days = mapOf(today to 7, today.minusDays(1) to 3)
        val text = CaminoDays.format(days)
        assertEquals("2026-11-11=7;2026-11-10=3", text)
        assertEquals(days, CaminoDays.parse(text))
    }

    @Test
    fun unTextoDanadoNoRompeLaLectura() {
        assertEquals(mapOf(today to 2), CaminoDays.parse("basura;2026-11-11=2;=4;2026-13-40=1;2026-11-10=cero;2026-11-09=0"))
        assertTrue(CaminoDays.parse("").isEmpty())
    }

    @Test
    fun cadaRespuestaSumaUnoEnSuDia() {
        var days = emptyMap<LocalDate, Int>()
        repeat(3) { days = CaminoDays.add(days, today) }
        days = CaminoDays.add(days, today.minusDays(1))
        assertEquals(3, days[today])
        assertEquals(1, days[today.minusDays(1)])
    }

    @Test
    fun soloSeConservanLosDiasMasRecientes() {
        val days = (0 until CaminoDays.KEEP_DAYS + 30).associate { today.minusDays(it.toLong()) to 1 }
        val kept = CaminoDays.parse(CaminoDays.format(days))
        assertEquals(CaminoDays.KEEP_DAYS, kept.size)
        assertTrue(today in kept)
        assertFalse(today.minusDays(CaminoDays.KEEP_DAYS + 5L) in kept)
    }

    @Test
    fun laPracticaDelCaminoCuentaParaLaRachaYLaMetaDeHoy() {
        val days = mapOf(today to 3, today.minusDays(1) to 6, today.minusDays(2) to 1)
        val counted = CaminoDays.asAttempts(days, zone)
        assertEquals(10, counted.size)
        assertEquals(3, TodaySummary.streakDays(counted, zone, today))
        assertEquals(3, TodaySummary.answeredToday(counted, zone, today))
        assertEquals(0.6f, TodaySummary.goalFraction(counted, zone, today), 0.001f)
    }

    @Test
    fun laPracticaDelCaminoSeSumaALaDelTutor() {
        val tutor = listOf(Attempt(today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), "porcentajes", "porcentajes-d1-s1", AttemptMode.GUIDED, 0, true, 0, 900))
        val counted = tutor + CaminoDays.asAttempts(mapOf(today to 4, today.minusDays(1) to 1), zone)
        assertEquals(5, TodaySummary.answeredToday(counted, zone, today))
        assertEquals(1f, TodaySummary.goalFraction(counted, zone, today), 0.001f)
        assertEquals(2, TodaySummary.streakDays(counted, zone, today))
        // Los intentos de conteo no son temas del catálogo: el tema para practicar sale solo del historial del tutor.
        assertEquals("porcentajes", TodaySummary.topicToPractice(tutor))
    }

    @Test
    fun elResumenSeLeeDelJsonDeLaPagina() {
        val s = CaminoSummary.parse("""{"g":"A","n":"Ingenierías","h":3,"t":73,"s":"Planteo de ecuaciones","c":"Razonamiento matemático","d":true}""")!!
        assertEquals("A", s.group)
        assertEquals("Ingenierías", s.groupName)
        assertEquals(3, s.done)
        assertEquals(73, s.total)
        assertEquals("Planteo de ecuaciones", s.next)
        assertEquals("Razonamiento matemático", s.nextCourse)
        assertTrue(s.diagnosed)
    }

    @Test
    fun sinGrupoNoHayResumen() {
        assertNull(CaminoSummary.parse(""))
        assertNull(CaminoSummary.parse("""{"g":""}"""))
        assertNull(CaminoSummary.parse("no es json"))
        assertNull(CaminoSummary.parse("[1,2]"))
    }
}
