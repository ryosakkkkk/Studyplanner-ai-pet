package pe.studyplanner.aipet.ui.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.studyplanner.aipet.domain.tutor.Phase
import pe.studyplanner.aipet.domain.tutor.TutorAction
import pe.studyplanner.aipet.domain.tutor.TutorScreen
import pe.studyplanner.aipet.domain.tutor.correctOf
import pe.studyplanner.aipet.domain.tutor.finishGuidedCorrectly
import pe.studyplanner.aipet.domain.tutor.newSession
import pe.studyplanner.aipet.domain.tutor.playPerfect
import pe.studyplanner.aipet.domain.tutor.runUnaided
import pe.studyplanner.aipet.domain.tutor.toGuided
import pe.studyplanner.aipet.domain.tutor.wrongOf

class TutorPresentationTest {

    @Test fun learnPhaseLabelCountsPages() {
        val state = newSession().state
        assertEquals("Aprender · paso 1 de ${state.progress.total}", TutorPresentation.phaseLabel(state))
        assertEquals(1f / state.progress.total, TutorPresentation.phaseFraction(state), 0.0001f)
    }

    @Test fun guidedLabelAndFractionAdvanceWithTheExercises() {
        val session = newSession()
        var state = session.toGuided()
        assertEquals("Práctica · 1 de 3", TutorPresentation.phaseLabel(state))
        assertEquals(0f, TutorPresentation.phaseFraction(state), 0.0001f)
        state = session.answer(correctOf(state))
        assertEquals(1f / 3f, TutorPresentation.phaseFraction(state), 0.0001f)
        state = session.next()
        assertEquals("Práctica · 2 de 3", TutorPresentation.phaseLabel(state))
    }

    @Test fun wrongAnswerShowsTheFeedbackAndTheSolutionAtOnce() {
        val session = newSession()
        val first = session.toGuided()
        val state = session.answer(wrongOf(first))
        assertEquals(TutorScreen.SOLUTION, state.screen)
        assertTrue(TutorPresentation.visibleHints(state).isEmpty())
        val message = TutorPresentation.message(state)
        assertTrue(message.isNotBlank())
        assertTrue(message.contains("Así se resuelve"))
        assertFalse(TutorPresentation.canAnswer(state))
        assertEquals("Siguiente ejercicio", TutorPresentation.nextLabel(state))
        assertTrue(TutorAction.RETRY in state.actions)
    }

    @Test fun requestedHintsAreCardsAndLeaveNoDuplicatedMessage() {
        val session = newSession()
        session.toGuided()
        val state = session.requestHint()
        assertEquals(TutorScreen.HINT, state.screen)
        assertEquals("", TutorPresentation.message(state))
        assertEquals(1, TutorPresentation.visibleHints(state).size)
        assertEquals(2, TutorPresentation.hintsRemaining(state))
        val second = session.requestHint()
        assertEquals(listOf(true, true), TutorPresentation.visibleHints(second).map { it.isNotBlank() })
        assertEquals(1, TutorPresentation.hintsRemaining(second))
    }

    @Test fun solvedAndRevealedExercisesHideTheHintCards() {
        val session = newSession()
        var state = session.toGuided()
        state = session.requestHint()
        state = session.answer(correctOf(state))
        assertEquals(TutorScreen.CORRECT, state.screen)
        assertTrue(TutorPresentation.visibleHints(state).isEmpty())
        assertFalse(TutorPresentation.canAnswer(state))
    }

    @Test fun theUnaidedCheckOffersNoHintsAndLeaksNothing() {
        val session = newSession()
        session.finishGuidedCorrectly()
        var state = session.state
        assertEquals(TutorScreen.UNAIDED_INTRO, state.screen)
        assertEquals("Empezar la comprobación", TutorPresentation.nextLabel(state))
        state = session.next()
        for (step in 0 until 2) {
            assertEquals(Phase.UNAIDED_CHECK, state.phase)
            assertTrue(TutorPresentation.canAnswer(state))
            assertTrue(TutorPresentation.visibleHints(state).isEmpty())
            assertEquals(0, TutorPresentation.hintsRemaining(state))
            assertTrue(TutorAction.REQUEST_HINT !in state.actions)
            assertTrue(TutorAction.DONT_KNOW in state.actions)
            val exercise = checkNotNull(state.exercise)
            assertEquals(null, exercise.correctIndex)
            assertTrue(exercise.eliminated.isEmpty())
            assertTrue(exercise.hintsShown.isEmpty())
            assertTrue(exercise.solutionSteps.isEmpty())
            assertEquals("Comprobación sin ayuda · ${step + 1} de 2", TutorPresentation.phaseLabel(state))
            state = session.answer(wrongOf(state))
        }
        assertEquals(Phase.SUMMARY, state.phase)
    }

    @Test fun summaryIsFullAndLabelled() {
        val state = newSession().also { it.finishGuidedCorrectly() }.runUnaided(listOf(true, false))
        assertEquals("Resumen", TutorPresentation.phaseLabel(state))
        assertEquals(1f, TutorPresentation.phaseFraction(state), 0.0001f)
        assertTrue(state.summary != null)
    }

    @Test fun nextButtonsSayWhereTheyGo() {
        val session = newSession()
        var state = session.state
        assertEquals("Continuar", TutorPresentation.nextLabel(state))
        repeat(state.progress.total - 1) { state = session.next() }
        assertEquals(TutorScreen.WORKED_EXAMPLE, state.screen)
        assertEquals("Empezar a practicar", TutorPresentation.nextLabel(state))
    }

    @Test fun lastGuidedExercisePointsToTheUnaidedCheck() {
        val session = newSession()
        var state = session.toGuided()
        repeat(2) {
            state = session.answer(correctOf(state))
            assertEquals("Siguiente ejercicio", TutorPresentation.nextLabel(state))
            state = session.next()
        }
        state = session.answer(correctOf(state))
        assertEquals("Ir a la comprobación sin ayuda", TutorPresentation.nextLabel(state))
    }

    @Test fun everyStateOfAFullSessionHasAFractionInRange() {
        for (state in newSession().playPerfect()) {
            val fraction = TutorPresentation.phaseFraction(state)
            assertTrue("fracción $fraction", fraction in 0f..1f)
            assertTrue(TutorPresentation.phaseLabel(state).isNotBlank())
        }
    }
}
