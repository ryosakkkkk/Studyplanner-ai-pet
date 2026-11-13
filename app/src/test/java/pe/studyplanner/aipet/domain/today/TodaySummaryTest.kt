package pe.studyplanner.aipet.domain.today

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import pe.studyplanner.aipet.domain.tutor.Attempt
import pe.studyplanner.aipet.domain.tutor.AttemptMode
import pe.studyplanner.aipet.domain.tutor.TutorReviewPlanner

class TodaySummaryTest {
    private val zone = ZoneId.of("America/Lima")
    private val today = LocalDate.of(2026, 11, 11)

    private fun at(day: LocalDate, hour: Int = 10) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun attempt(day: LocalDate, mode: AttemptMode = AttemptMode.GUIDED, ok: Boolean = true, topic: String = "porcentajes", hour: Int = 10) =
        Attempt(at(day, hour), topic, "$topic-$day-$hour", mode, if (ok) 0 else 1, ok, 0, 1000)

    @Test
    fun rachaCuentaDiasSeguidosHastaHoy() {
        val list = listOf(attempt(today), attempt(today.minusDays(1)), attempt(today.minusDays(2)), attempt(today.minusDays(4)))
        assertEquals(3, TodaySummary.streakDays(list, zone, today))
    }

    @Test
    fun siHoyNoPracticoLaRachaLlegaHastaAyer() {
        val list = listOf(attempt(today.minusDays(1)), attempt(today.minusDays(2)))
        assertEquals(2, TodaySummary.streakDays(list, zone, today))
        assertEquals(0, TodaySummary.streakDays(listOf(attempt(today.minusDays(3))), zone, today))
    }

    @Test
    fun metaDelDiaSeTopaEnCien() {
        val three = List(3) { attempt(today, hour = 9 + it) }
        assertEquals(0.6f, TodaySummary.goalFraction(three, zone, today), 0.001f)
        assertEquals(1f, TodaySummary.goalFraction(List(9) { attempt(today, hour = 8 + it) }, zone, today), 0.001f)
        assertEquals(0f, TodaySummary.goalFraction(emptyList(), zone, today), 0.001f)
    }

    @Test
    fun comprobarSoloOfreceTemasYaPracticados() {
        assertNull(TodaySummary.topicToCheck(emptyList()))
        assertEquals("porcentajes", TodaySummary.topicToCheck(listOf(attempt(today))))
        assertEquals("porcentajes", TodaySummary.topicToPractice(listOf(attempt(today))))
    }

    @Test
    fun repasoPendienteTrasComprobarYSinAgenda() {
        val check = List(3) { attempt(today, AttemptMode.UNAIDED, ok = true, hour = 10 + it) }
        val pending = TodaySummary.pendingReview(check, zone, today, emptySet())
        assertNotNull(pending)
        assertEquals(today.plusDays(3), pending!!.due)
        // Ya guardado en la agenda: no se vuelve a proponer.
        val id = TutorReviewPlanner.idFor("porcentajes", pending.due, TutorReviewPlanner.DEFAULT_START_HOUR)
        assertNull(TodaySummary.pendingReview(check, zone, today, setOf(id)))
    }

    @Test
    fun comprobacionViejaNoGeneraPropuesta() {
        val old = List(3) { attempt(today.minusDays(5), AttemptMode.UNAIDED, ok = false, hour = 10 + it) }
        assertNull(TodaySummary.pendingReview(old, zone, today, emptySet()))
        assertNull(TodaySummary.pendingReview(listOf(attempt(today)), zone, today, emptySet()))
    }

    @Test
    fun etiquetasDeDia() {
        assertEquals("hoy", TodaySummary.dayLabel(today, today))
        assertEquals("mañana", TodaySummary.dayLabel(today.plusDays(1), today))
        assertEquals("el viernes 13", TodaySummary.dayLabel(today.plusDays(2), today))
    }

    @Test
    fun misionesDeHoy() {
        val list = listOf(attempt(today), attempt(today, hour = 11), attempt(today.minusDays(1)))
        assertEquals(2, TodaySummary.answeredToday(list, zone, today))
        assertEquals(false, TodaySummary.checkedToday(list, zone, today))
        assertEquals(true, TodaySummary.checkedToday(list + attempt(today, AttemptMode.UNAIDED, hour = 12), zone, today))
    }
}
