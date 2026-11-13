package pe.studyplanner.aipet.domain.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.studyplanner.aipet.domain.tutor.Phase
import pe.studyplanner.aipet.domain.tutor.PetCue
import pe.studyplanner.aipet.domain.tutor.TutorScreen
import pe.studyplanner.aipet.domain.tutor.TutorState
import pe.studyplanner.aipet.domain.tutor.correctOf
import pe.studyplanner.aipet.domain.tutor.finishGuidedCorrectly
import pe.studyplanner.aipet.domain.tutor.newSession
import pe.studyplanner.aipet.domain.tutor.playPerfect
import pe.studyplanner.aipet.domain.tutor.runUnaided
import pe.studyplanner.aipet.domain.tutor.toGuided
import pe.studyplanner.aipet.domain.tutor.wrongOf

class PetFeedbackTest {

    @Test fun phoneFoxFollowsTheDocumentedCueMapping() {
        assertEquals("speaking", PetFeedback.moodFor(PetCue.EXPLAIN))
        assertEquals("thinking", PetFeedback.moodFor(PetCue.THINK))
        assertEquals("happy", PetFeedback.moodFor(PetCue.CORRECT))
        assertEquals("alert", PetFeedback.moodFor(PetCue.ENCOURAGE))
        assertEquals("celebrate", PetFeedback.moodFor(PetCue.CELEBRATE))
        assertEquals("rest", PetFeedback.moodFor(PetCue.REST))
    }

    @Test fun physicalPetFaceFollowsTheCueMapping() {
        // Explicar no mueve la boca: `hablar` es solo para cuando la voz suena (SpeechFace).
        assertEquals(PetExpression.IDLE, PetFeedback.expressionFor(PetCue.EXPLAIN))
        assertEquals(PetExpression.PENSAR, PetFeedback.expressionFor(PetCue.THINK))
        assertEquals(PetExpression.FELIZ, PetFeedback.expressionFor(PetCue.CORRECT))
        assertEquals(PetExpression.ALERTA, PetFeedback.expressionFor(PetCue.ENCOURAGE))
        assertEquals(PetExpression.SORPRENDIDO, PetFeedback.expressionFor(PetCue.ENCOURAGE, useAlternative = true))
        assertEquals(PetExpression.CELEBRAR, PetFeedback.expressionFor(PetCue.CELEBRATE))
        assertEquals(PetExpression.DORMIR, PetFeedback.expressionFor(PetCue.REST))
    }

    @Test fun theFoxMouthMovesOnlyWhileTheVoiceIsPlaying() {
        assertEquals("speaking", PetFeedback.moodFor(PetCue.EXPLAIN))
        assertEquals("idle", PetFeedback.silentMoodFor(PetCue.EXPLAIN))
        for (cue in PetCue.entries) {
            assertNotEquals("speaking", PetFeedback.silentMoodFor(cue))
            if (cue != PetCue.EXPLAIN) assertEquals(PetFeedback.moodFor(cue), PetFeedback.silentMoodFor(cue))
        }
    }

    @Test fun learnHintAndSolutionScreensKeepTheMouthStill() {
        val learn = newSession()
        assertEquals(PetSignal(PetExpression.IDLE, "Vamos a aprender"), PetFeedback.signalFor(learn.state))
        var example: TutorState = learn.state
        repeat(10) { if (example.screen != TutorScreen.WORKED_EXAMPLE) example = learn.next() }
        assertEquals(TutorScreen.WORKED_EXAMPLE, example.screen)
        assertEquals(PetSignal(PetExpression.IDLE, "Vamos a aprender"), PetFeedback.signalFor(example))

        val hinted = newSession()
        hinted.toGuided()
        val hint = hinted.requestHint()
        assertEquals(TutorScreen.HINT, hint.screen)
        assertEquals(PetSignal(PetExpression.PENSAR, "Pista 1 de 3", PetSound.PISTA), PetFeedback.signalFor(hint))

        // Pedir la solución: cara tranquila y sin sonido.
        val asked = newSession()
        asked.toGuided()
        val solution = asked.showSolution()
        assertEquals(TutorScreen.SOLUTION, solution.screen)
        assertEquals(PetSignal(PetExpression.IDLE, "Mira la solución"), PetFeedback.signalFor(solution))
    }

    @Test fun noTutorStateEverShowsTheTalkingFace() {
        val sessions = listOf(newSession().playPerfect(), newSession().also { it.finishGuidedCorrectly() }.let { s ->
            listOf(s.state, s.runUnaided(listOf(true, false)))
        })
        var checked = 0
        for (state in sessions.flatten()) {
            assertNotEquals(PetExpression.HABLAR, PetFeedback.signalFor(state).expression)
            checked++
        }
        assertTrue(checked > 10)
        for (cue in PetCue.entries) {
            assertNotEquals(PetExpression.HABLAR, PetFeedback.expressionFor(cue))
            assertNotEquals(PetExpression.HABLAR, PetFeedback.expressionFor(cue, useAlternative = true))
        }
    }

    @Test fun guidedPracticeAnnouncesHintsMistakesAndSuccess() {
        val session = newSession()
        var state = session.toGuided()
        val question = PetFeedback.signalFor(state)
        assertEquals(PetExpression.PENSAR, question.expression)
        assertEquals("Ejercicio 1 de 3", question.text)
        assertNull(question.sound)

        state = session.requestHint()
        val hint = PetFeedback.signalFor(state)
        assertEquals("Pista 1 de 3", hint.text)
        assertEquals(PetSound.PISTA, hint.sound)

        // Un error muestra la solución en el acto, con la cara de ánimo y el sonido de error.
        state = session.answer(wrongOf(state))
        assertEquals(TutorScreen.SOLUTION, state.screen)
        val wrong = PetFeedback.signalFor(state)
        assertEquals(PetExpression.ALERTA, wrong.expression)
        assertEquals("Mira la solución", wrong.text)
        assertEquals(PetSound.ERROR, wrong.sound)

        state = session.retry()
        state = session.answer(correctOf(state))
        val right = PetFeedback.signalFor(state)
        assertEquals(PetExpression.FELIZ, right.expression)
        assertEquals("¡Correcto!", right.text)
        assertEquals(PetSound.CORRECTO, right.sound)
    }

    @Test fun theUnaidedCheckNeverRevealsWhetherTheAnswerWasRight() {
        val right = newSession()
        val wrong = newSession()
        right.finishGuidedCorrectly()
        wrong.finishGuidedCorrectly()
        right.next()
        wrong.next()
        // Misma pregunta en ambas sesiones (misma semilla): una se responde bien y la otra mal.
        val afterRight = right.answer(correctOf(right.state))
        val afterWrong = wrong.answer(wrongOf(wrong.state))
        assertEquals(Phase.UNAIDED_CHECK, afterRight.phase)
        assertEquals(Phase.UNAIDED_CHECK, afterWrong.phase)
        assertEquals(PetFeedback.signalFor(afterRight), PetFeedback.signalFor(afterWrong))
        val signal = PetFeedback.signalFor(afterRight)
        assertNull(signal.sound)
        assertEquals(PetExpression.PENSAR, signal.expression)
        assertTrue(signal.text.startsWith("Sin ayuda"))
    }

    @Test fun noStateBeforeTheSummaryMakesASoundDuringTheUnaidedPhase() {
        val session = newSession()
        session.finishGuidedCorrectly()
        val states = mutableListOf(session.state)
        states += session.next()
        states += session.answer(wrongOf(session.state))
        for (state in states) {
            assertEquals(Phase.UNAIDED_CHECK, state.phase)
            assertNull(PetFeedback.signalFor(state).sound)
        }
    }

    @Test fun summaryReactsToTheUnaidedResult() {
        val perfect = newSession().also { it.finishGuidedCorrectly() }.runUnaided(listOf(true, true))
        assertEquals(PetSignal(PetExpression.CELEBRAR, "¡Lo lograste!", PetSound.CELEBRACION), PetFeedback.signalFor(perfect))

        val none = newSession().also { it.finishGuidedCorrectly() }.runUnaided(listOf(false, null))
        assertEquals(PetSignal(PetExpression.DORMIR, "", null), PetFeedback.signalFor(none))

        val partial = newSession().also { it.finishGuidedCorrectly() }.runUnaided(listOf(true, false))
        assertEquals(PetSignal(PetExpression.SORPRENDIDO, "¡Buen avance!", null), PetFeedback.signalFor(partial))
    }

    @Test fun everyTextFitsTheSmallScreenAndEveryFaceIsKnown() {
        val states = newSession().playPerfect()
        assertTrue(states.isNotEmpty())
        for (state in states) {
            val signal = PetFeedback.signalFor(state)
            assertTrue("«${signal.text}» es largo", signal.text.length <= PetProtocol.MAX_TEXT_CHARS)
            assertTrue(signal.expression in PetExpression.entries)
        }
    }
}
