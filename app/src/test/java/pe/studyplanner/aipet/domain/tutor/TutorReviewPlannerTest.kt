package pe.studyplanner.aipet.domain.tutor

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import pe.studyplanner.aipet.domain.academic.ActivityKind
import pe.studyplanner.aipet.domain.academic.ActivityTime

class TutorReviewPlannerTest {
    private val topic = TutorRegistry.requireTopic("ecuaciones-lineales")
    private val today = LocalDate.of(2026, 10, 28)
    private val proposal = ReviewProposal(topic.id, dayOffset = 2, reason = "Repasa pronto.")

    @Test fun buildsAFixedStudySessionOnTheProposedDay() {
        val activity = TutorReviewPlanner.activityFor(topic, proposal, today, "America/Lima", startHour = 18, minutes = 30)
        assertEquals(ActivityKind.STUDY, activity.kind)
        assertEquals("Repaso: ${topic.title}", activity.title)
        assertEquals(30, activity.estimateMinutes)
        assertTrue(activity.fixed)
        val time = activity.time as ActivityTime.Timed
        // 2026-10-30 18:00 en Lima (UTC-5) son las 23:00 UTC.
        assertEquals(Instant.parse("2026-10-30T23:00:00Z"), time.start)
        assertEquals(Instant.parse("2026-10-30T23:30:00Z"), time.end)
        assertEquals("America/Lima", time.zoneId)
        assertTrue(activity.notes.contains("Repasa pronto."))
    }

    @Test fun relearningProposalsGetADifferentTitle() {
        val relearn = proposal.copy(relearn = true)
        val activity = TutorReviewPlanner.activityFor(topic, relearn, today, "America/Lima")
        assertEquals("Volver a aprender: ${topic.title}", activity.title)
    }

    @Test fun theSameReviewAlwaysGetsTheSameId() {
        val a = TutorReviewPlanner.activityFor(topic, proposal, today, "America/Lima", 18, 30)
        val b = TutorReviewPlanner.activityFor(topic, proposal, today, "America/Lima", 18, 45)
        assertEquals(a.id, b.id)
        assertEquals(a.id, TutorReviewPlanner.idFor(topic.id, LocalDate.of(2026, 10, 30), 18))
    }

    @Test fun anotherHourDayOrTopicChangesTheId() {
        val base = TutorReviewPlanner.idFor(topic.id, LocalDate.of(2026, 10, 30), 18)
        assertNotEquals(base, TutorReviewPlanner.idFor(topic.id, LocalDate.of(2026, 10, 30), 16))
        assertNotEquals(base, TutorReviewPlanner.idFor(topic.id, LocalDate.of(2026, 10, 31), 18))
        assertNotEquals(base, TutorReviewPlanner.idFor("otro-tema", LocalDate.of(2026, 10, 30), 18))
    }

    @Test fun offeredHoursAreAllValid() {
        for (hour in TutorReviewPlanner.START_HOURS) {
            TutorReviewPlanner.activityFor(topic, proposal, today, "America/Lima", hour, TutorReviewPlanner.DEFAULT_MINUTES)
        }
        assertTrue(TutorReviewPlanner.DEFAULT_START_HOUR in TutorReviewPlanner.START_HOURS)
    }

    @Test fun rejectsAnImpossibleHourOrDuration() {
        try {
            TutorReviewPlanner.activityFor(topic, proposal, today, "America/Lima", startHour = 24)
            fail("hora 24 no es válida")
        } catch (_: IllegalArgumentException) {
        }
        try {
            TutorReviewPlanner.activityFor(topic, proposal, today, "America/Lima", minutes = 0)
            fail("0 minutos no es válido")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun refusesAnHourThatDoesNotExistBecauseOfDaylightSaving() {
        // 2026-03-08 02:00 no existe en Nueva York (salto de horario de verano).
        try {
            TutorReviewPlanner.activityFor(
                topic, proposal.copy(dayOffset = 1), LocalDate.of(2026, 3, 7), "America/New_York", startHour = 2,
            )
            fail("la hora no existe")
        } catch (_: DateTimeException) {
        }
    }
}
