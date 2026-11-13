package pe.studyplanner.aipet.domain.companion

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.studyplanner.aipet.domain.tutor.Phase
import pe.studyplanner.aipet.domain.tutor.TutorScreen
import pe.studyplanner.aipet.domain.tutor.correctOf
import pe.studyplanner.aipet.domain.tutor.finishGuidedCorrectly
import pe.studyplanner.aipet.domain.tutor.newSession
import pe.studyplanner.aipet.domain.tutor.playPerfect
import pe.studyplanner.aipet.domain.tutor.runUnaided
import pe.studyplanner.aipet.domain.tutor.toGuided
import pe.studyplanner.aipet.domain.tutor.wrongOf

/** Mascota falsa: guarda caras y sonidos, y deja simular la conexión. */
private class RecordingPetLink(connected: Boolean = true) : PetLink {
    private val mutableState = MutableStateFlow(PetLinkState(if (connected) PetLinkStatus.CONNECTED else PetLinkStatus.IDLE))
    private val mutableEvents = MutableSharedFlow<PetEvent>(extraBufferCapacity = 1)
    override val state: StateFlow<PetLinkState> = mutableState.asStateFlow()
    override val events: SharedFlow<PetEvent> = mutableEvents.asSharedFlow()
    val faces = mutableListOf<Pair<PetExpression, String>>()
    val sounds = mutableListOf<PetSound>()

    override fun refresh() = Unit
    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun showFace(expression: PetExpression, text: String) {
        faces += expression to text
    }

    override fun playSound(sound: PetSound) {
        sounds += sound
    }

    fun setConnected(value: Boolean) {
        mutableState.value = PetLinkState(if (value) PetLinkStatus.CONNECTED else PetLinkStatus.IDLE)
    }
}

/**
 * Espera controlada: cada [wait] queda en pausa hasta que la prueba llama a [elapse]. Así «pasaron 150 ms» es un paso
 * explícito de la prueba y no depende del reloj real ni de la biblioteca de pruebas de corrutinas.
 */
private class ManualWait {
    val requested = mutableListOf<Long>()
    private val gates = mutableListOf<CompletableDeferred<Unit>>()

    /** Cuántas esperas siguen pendientes (ni canceladas ni cumplidas). */
    var open = 0
        private set

    suspend fun wait(millis: Long) {
        requested += millis
        val gate = CompletableDeferred<Unit>()
        gates += gate
        open++
        try {
            gate.await()
        } finally {
            open--
        }
    }

    fun elapse() {
        gates.toList().forEach { it.complete(Unit) }
    }
}

class SpeechFaceTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After fun tearDown() {
        scope.cancel()
    }

    // ---- recorte del texto ----

    @Test fun shortTextIsKeptAsIs() {
        assertEquals("Resuelve la ecuación 2x + 3 = 11", SpeechFace.excerpt("Resuelve la ecuación 2x + 3 = 11"))
        assertEquals("", SpeechFace.excerpt(""))
        assertEquals("", SpeechFace.excerpt("   \n\t "))
    }

    @Test fun whitespaceNewlinesAndBarsBecomeSingleSpaces() {
        assertEquals("Hola mundo hoy", SpeechFace.excerpt("  Hola\n  mundo | hoy\t"))
    }

    @Test fun textOfExactlyTheLimitIsNotCut() {
        val text = "a".repeat(SpeechFace.MAX_TEXT_CHARS)
        assertEquals(text, SpeechFace.excerpt(text))
        val words = "uno dos tres cuatro cinco seis siete"
        assertEquals(SpeechFace.MAX_TEXT_CHARS, words.length)
        assertEquals(words, SpeechFace.excerpt(words))
    }

    @Test fun longTextIsCutAtTheLastWholeWord() {
        val text = "Escucha con atención la explicación de la suma de fracciones"
        assertEquals("Escucha con atención la explicación", SpeechFace.excerpt(text))
    }

    @Test fun aWordThatEndsExactlyAtTheLimitIsKept() {
        // El carácter 37 es un espacio: «siete» entra completa.
        val text = "uno dos tres cuatro cinco seis siete ocho nueve"
        assertEquals("uno dos tres cuatro cinco seis siete", SpeechFace.excerpt(text))
    }

    @Test fun trailingCommaOrDashIsDroppedAfterACut() {
        assertEquals(
            "Suma 3 a ambos lados de la igualdad",
            SpeechFace.excerpt("Suma 3 a ambos lados de la igualdad, y luego divide"),
        )
        assertEquals("Resta 4 y después", SpeechFace.excerpt("Resta 4 y después - luego", 19))
    }

    @Test fun aSingleWordLongerThanTheLimitIsCutHard() {
        assertEquals("x".repeat(SpeechFace.MAX_TEXT_CHARS), SpeechFace.excerpt("x".repeat(60)))
    }

    @Test fun aSurrogatePairIsNeverSplit() {
        val emoji = "😀"
        val result = SpeechFace.excerpt(emoji.repeat(40))
        assertEquals(emoji.repeat(SpeechFace.MAX_TEXT_CHARS), result)
        assertEquals(SpeechFace.MAX_TEXT_CHARS, result.codePointCount(0, result.length))
    }

    @Test fun charactersAreCountedNotBytes() {
        assertEquals("á".repeat(SpeechFace.MAX_TEXT_CHARS), SpeechFace.excerpt("á".repeat(50)))
    }

    @Test fun aCustomLimitIsHonoured() {
        assertEquals("Hola mundo", SpeechFace.excerpt("Hola mundo cruel", 12))
        assertEquals("H", SpeechFace.excerpt("Hola", 0))
    }

    @Test fun neverCutsInsideAWordAndNeverExceedsTheLimit() {
        val samples = listOf(
            "Para resolver la ecuación, primero agrupa los términos semejantes en cada lado de la igualdad.",
            "El teorema de Pitágoras relaciona los tres lados de un triángulo rectángulo.",
            "Calcula el 15 por ciento de 240 soles y luego súmalo al precio original del producto.",
            "Elige la alternativa correcta: A, B, C, D o E. Puedes pedir una pista cuando quieras.",
            "Tienes dos ecuaciones con dos incógnitas; despeja una y sustitúyela en la otra ecuación.",
        )
        for (text in samples) {
            val result = SpeechFace.excerpt(text)
            assertTrue(result, result.codePointCount(0, result.length) <= SpeechFace.MAX_TEXT_CHARS)
            assertTrue(result, text.startsWith(result))
            assertFalse(result, result.endsWith(" "))
            assertTrue(result, result.isNotEmpty())
            // Lo que sigue al recorte no continúa una palabra.
            assertTrue(result, text.length == result.length || !text[result.length].isLetterOrDigit())
        }
    }

    @Test fun theExcerptTravelsWholeInTheFaceMessage() {
        // El mensaje FACE no vuelve a recortar el texto: lo que se calcula es lo que llega a la pantalla.
        for (text in listOf("á".repeat(80), "Escucha con atención la explicación de la suma de fracciones", "Pérez, ¿cuántos años tenías ayer? Ñandú")) {
            val signal = SpeechFace.speakingSignal(text)
            val bytes = PetProtocol.encodeFace(signal.expression, signal.text)
            assertEquals("E:hablar|T:${signal.text}", String(bytes, Charsets.UTF_8))
            assertTrue(bytes.size <= PetProtocol.MAX_FACE_BYTES)
        }
    }

    @Test fun speakingSignalIsTheTalkingFaceWithoutSound() {
        val signal = SpeechFace.speakingSignal("Vamos a aprender las ecuaciones lineales paso a paso")
        assertEquals(PetExpression.HABLAR, signal.expression)
        assertNull(signal.sound)
        assertTrue(signal.text.length <= SpeechFace.MAX_TEXT_CHARS)
        assertTrue(signal.text.startsWith("Vamos a aprender las ecuaciones"))
    }

    // ---- cara a la que se vuelve ----

    @Test fun withoutATutorTheFaceReturnsToIdle() {
        assertEquals(PetSignal(PetExpression.IDLE, ""), SpeechFace.restoreSignal(null))
        assertEquals(SpeechFace.IDLE, SpeechFace.restoreSignal(null))
    }

    @Test fun restoreIsTheFaceTheTutorStateImpliesWithoutItsSound() {
        val session = newSession()
        val states = mutableListOf(session.state)
        states += session.toGuided()
        states += session.requestHint()
        states += session.answer(wrongOf(session.state))
        session.retry()
        states += session.answer(correctOf(session.state))
        states += session.playPerfect().last()
        assertEquals(setOf(TutorScreen.EXPLANATION, TutorScreen.QUESTION, TutorScreen.HINT, TutorScreen.SOLUTION, TutorScreen.CORRECT, TutorScreen.SUMMARY), states.map { it.screen }.toSet())
        for (state in states) {
            val expected = PetFeedback.signalFor(state)
            val restored = SpeechFace.restoreSignal(state)
            assertEquals(expected.expression, restored.expression)
            assertEquals(expected.text, restored.text)
            assertNull("no se repite el sonido de ${state.screen}", restored.sound)
        }
    }

    @Test fun restoreMapsEachKindOfScreenToItsFace() {
        val session = newSession()
        assertEquals(PetSignal(PetExpression.IDLE, "Vamos a aprender"), SpeechFace.restoreSignal(session.state))
        val question = SpeechFace.restoreSignal(session.toGuided())
        assertEquals(PetExpression.PENSAR, question.expression)
        assertTrue(question.text.startsWith("Ejercicio 1 de "))
        assertEquals(PetExpression.PENSAR, SpeechFace.restoreSignal(session.requestHint()).expression)
        assertEquals(PetExpression.ALERTA, SpeechFace.restoreSignal(session.answer(wrongOf(session.state))).expression)
        session.retry()
        assertEquals(PetSignal(PetExpression.FELIZ, "¡Correcto!"), SpeechFace.restoreSignal(session.answer(correctOf(session.state))))
        assertEquals(PetSignal(PetExpression.CELEBRAR, "¡Lo lograste!"), SpeechFace.restoreSignal(newSession().playPerfect().last()))
    }

    @Test fun afterTheVoiceStopsTheMouthStopsOnEveryScreen() {
        val session = newSession()
        val states = mutableListOf(session.state)
        states += session.toGuided()
        states += session.requestHint()
        states += session.answer(wrongOf(session.state))
        states += session.answer(correctOf(session.state))
        states += newSession().playPerfect()
        val asked = newSession()
        asked.toGuided()
        states += asked.showSolution()
        assertTrue(states.any { it.screen == TutorScreen.SOLUTION })
        for (state in states) {
            assertTrue("${state.screen} deja la boca en movimiento", SpeechFace.restoreSignal(state).expression != PetExpression.HABLAR)
        }
    }

    @Test fun restoringDuringTheUnaidedCheckNeverRevealsTheResult() {
        val right = newSession()
        val wrong = newSession()
        right.finishGuidedCorrectly()
        wrong.finishGuidedCorrectly()
        val afterRight = right.runUnaided(listOf(true))
        val afterWrong = wrong.runUnaided(listOf(false))
        assertEquals(Phase.UNAIDED_CHECK, afterRight.phase)
        assertEquals(Phase.UNAIDED_CHECK, afterWrong.phase)
        assertEquals(SpeechFace.restoreSignal(afterRight), SpeechFace.restoreSignal(afterWrong))
        assertEquals(PetExpression.PENSAR, SpeechFace.restoreSignal(afterRight).expression)
    }

    // ---- coalescencia (debounce) ----

    private fun sync(
        link: PetLink,
        timer: ManualWait,
        restore: () -> PetSignal = { SpeechFace.IDLE },
    ) = SpeechFaceSync(link, scope, restore, wait = { timer.wait(it) })

    @Test fun nothingIsWrittenUntilTheWindowPasses() {
        val link = RecordingPetLink()
        val timer = ManualWait()
        val sync = sync(link, timer)
        sync.onSpeechStarted("Hola mundo")
        assertTrue(link.faces.isEmpty())
        assertEquals(listOf(SpeechFace.DEBOUNCE_MS), timer.requested)
        timer.elapse()
        assertEquals(listOf(PetExpression.HABLAR to "Hola mundo"), link.faces)
    }

    @Test fun theDebounceWindowIs150Milliseconds() {
        assertEquals(150L, SpeechFace.DEBOUNCE_MS)
    }

    @Test fun whenTheVoiceEndsTheRestoredFaceReplacesTheTalkingOne() {
        val link = RecordingPetLink()
        val timer = ManualWait()
        val sync = sync(link, timer)
        sync.onSpeechStarted("Hola mundo")
        timer.elapse()
        sync.onSpeechEnded()
        assertEquals(1, link.faces.size)
        timer.elapse()
        assertEquals(listOf(PetExpression.HABLAR to "Hola mundo", PetExpression.IDLE to ""), link.faces)
        assertTrue(link.sounds.isEmpty())
    }

    @Test fun restoreIsEvaluatedWhenItIsWrittenNotWhenTheVoiceStarted() {
        val link = RecordingPetLink()
        val timer = ManualWait()
        var current = PetSignal(PetExpression.PENSAR, "Ejercicio 1 de 3")
        val sync = sync(link, timer, restore = { current })
        sync.onSpeechStarted("Lee esto")
        timer.elapse()
        current = PetSignal(PetExpression.FELIZ, "¡Correcto!")
        sync.onSpeechEnded()
        timer.elapse()
        assertEquals(PetExpression.FELIZ to "¡Correcto!", link.faces.last())
    }

    @Test fun anUtteranceThatStartsAndEndsInsideTheWindowWritesNothing() {
        val link = RecordingPetLink()
        val timer = ManualWait()
        val sync = sync(link, timer)
        sync.onSpeechStarted("Muy corto")
        sync.onSpeechEnded()
        timer.elapse()
        assertTrue(link.faces.isEmpty())
        assertEquals(0, timer.open)
    }

    @Test fun backToBackUtterancesDoNotPassThroughTheRestingFace() {
        val link = RecordingPetLink()
        val timer = ManualWait()
        val sync = sync(link, timer)
        sync.onSpeechStarted("Primera frase")
        timer.elapse()
        sync.onSpeechEnded()
        sync.onSpeechStarted("Segunda frase")
        timer.elapse()
        assertEquals(listOf(PetExpression.HABLAR to "Primera frase", PetExpression.HABLAR to "Segunda frase"), link.faces)
    }

    @Test fun aBurstOfShortUtterancesWritesOnlyTheLastIntention() {
        val link = RecordingPetLink()
        val timer = ManualWait()
        val sync = sync(link, timer)
        repeat(5) { i ->
            sync.onSpeechStarted("Frase $i")
            sync.onSpeechEnded()
        }
        sync.onSpeechStarted("Frase final")
        assertEquals(1, timer.open)
        timer.elapse()
        assertEquals(listOf(PetExpression.HABLAR to "Frase final"), link.faces)
    }

    @Test fun aBurstThatEndsSilentlyRestoresOnlyWhatWasShown() {
        val link = RecordingPetLink()
        val timer = ManualWait()
        val sync = sync(link, timer)
        sync.onSpeechStarted("Frase")
        timer.elapse()
        repeat(4) {
            sync.onSpeechEnded()
            sync.onSpeechStarted("Otra")
        }
        sync.onSpeechEnded()
        timer.elapse()
        assertEquals(listOf(PetExpression.HABLAR to "Frase", PetExpression.IDLE to ""), link.faces)
    }

    @Test fun nothingIsRestoredWhenTheTalkingFaceWasNeverShown() {
        val link = RecordingPetLink()
        val timer = ManualWait()
        val sync = sync(link, timer)
        sync.onSpeechEnded()
        assertEquals(0, timer.open)
        assertTrue(timer.requested.isEmpty())
        assertTrue(link.faces.isEmpty())
    }

    // ---- sin mascota conectada ----

    @Test fun withoutAConnectedPetNothingIsScheduledOrWritten() {
        val link = RecordingPetLink(connected = false)
        val timer = ManualWait()
        val sync = sync(link, timer)
        sync.onSpeechStarted("Hola")
        sync.onSpeechEnded()
        timer.elapse()
        assertTrue(timer.requested.isEmpty())
        assertTrue(link.faces.isEmpty())
        assertTrue(link.sounds.isEmpty())
    }

    @Test fun aPetThatDisconnectsInsideTheWindowReceivesNothing() {
        val link = RecordingPetLink()
        val timer = ManualWait()
        val sync = sync(link, timer)
        sync.onSpeechStarted("Hola")
        link.setConnected(false)
        timer.elapse()
        assertTrue(link.faces.isEmpty())
    }

    @Test fun aPetThatDisconnectsWhileTalkingIsNotWrittenToAtTheEnd() {
        val link = RecordingPetLink()
        val timer = ManualWait()
        val sync = sync(link, timer)
        sync.onSpeechStarted("Hola")
        timer.elapse()
        link.setConnected(false)
        sync.onSpeechEnded()
        timer.elapse()
        assertEquals(listOf(PetExpression.HABLAR to "Hola"), link.faces)
        // Al reconectar y volver a hablar todo funciona igual.
        link.setConnected(true)
        sync.onSpeechStarted("Otra vez")
        timer.elapse()
        assertEquals(PetExpression.HABLAR to "Otra vez", link.faces.last())
    }

    // ---- cierre de la pantalla ----

    @Test fun releaseRemovesTheTalkingFaceAtOnceAndIgnoresLaterNotices() {
        val link = RecordingPetLink()
        val timer = ManualWait()
        val sync = sync(link, timer)
        sync.onSpeechStarted("Hola")
        timer.elapse()
        sync.onSpeechEnded()
        sync.release()
        assertEquals(listOf(PetExpression.HABLAR to "Hola", PetExpression.IDLE to ""), link.faces)
        assertEquals(0, timer.open)
        sync.onSpeechStarted("Tarde")
        timer.elapse()
        sync.release()
        assertEquals(2, link.faces.size)
    }

    @Test fun releaseWithoutRestoreLeavesTheLastWordToTheCaller() {
        val link = RecordingPetLink()
        val timer = ManualWait()
        val sync = sync(link, timer)
        sync.onSpeechStarted("Hola")
        timer.elapse()
        sync.release(restoreFace = false)
        assertEquals(listOf(PetExpression.HABLAR to "Hola"), link.faces)
    }

    @Test fun releaseCancelsAPendingTalkingFace() {
        val link = RecordingPetLink()
        val timer = ManualWait()
        val sync = sync(link, timer)
        sync.onSpeechStarted("Hola")
        sync.release()
        timer.elapse()
        assertTrue(link.faces.isEmpty())
        assertEquals(0, timer.open)
    }

    @Test fun releaseWithNothingShownWritesNothing() {
        val link = RecordingPetLink()
        val timer = ManualWait()
        val sync = sync(link, timer)
        sync.release()
        assertTrue(link.faces.isEmpty())
    }
}
