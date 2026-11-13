package pe.studyplanner.aipet.domain.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El aviso «empezó / terminó la lectura» (que mueve la cara de la mascota física) debe llegar una sola vez por lectura
 * y por cualquier salida. Motor controlado a mano; no se prueba la calidad de ninguna voz.
 */
class VoiceSessionSpeechActivityTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After fun tearDown() {
        scope.cancel()
    }

    private class Recorder : SpeechActivityListener {
        val events = mutableListOf<String>()
        var failing = false
        override fun onSpeechStarted(text: String) {
            events += "start:$text"
            if (failing) error("fallo del oyente")
        }

        override fun onSpeechEnded() {
            events += "end"
            if (failing) error("fallo del oyente")
        }
    }

    private class StubPlatform : VoicePlatform {
        val spoken = mutableListOf<Pair<String, SpeechEvents>>()
        val recognitions = mutableListOf<RecognitionEvents>()
        val speech get() = spoken.last().second
        override fun capabilities() = VoiceCapabilities(speechAvailable = true, onDeviceAvailable = true, ttsEngineAvailable = true)
        override fun startRecognition(onDevice: Boolean, listener: RecognitionEvents) {
            recognitions += listener
        }

        override fun stopRecognition() = Unit
        override fun speak(text: String, listener: SpeechEvents) {
            spoken += text to listener
        }

        override fun stopSpeech() = Unit
        override fun release() = Unit
    }

    private val platform = StubPlatform()
    private val recorder = Recorder()
    private val session = VoiceSession(platform, scope, recorder).also { it.setReadAloudEnabled(true) }

    private fun startSpeaking(text: String = "Hola mundo") {
        session.speak(text)
        platform.speech.onStart()
    }

    @Test fun nothingIsAnnouncedUntilTheEngineReallyStarts() {
        session.speak("Hola  mundo\n")
        assertEquals(VoicePhase.PREPARING_SPEECH, session.state.value.phase)
        assertTrue(recorder.events.isEmpty())
        platform.speech.onStart()
        assertEquals(listOf("start:Hola mundo"), recorder.events)
        assertEquals(VoicePhase.SPEAKING, session.state.value.phase)
    }

    @Test fun finishingNormallyAnnouncesTheEndOnce() {
        startSpeaking()
        platform.speech.onDone()
        assertEquals(listOf("start:Hola mundo", "end"), recorder.events)
        assertEquals(VoicePhase.IDLE, session.state.value.phase)
    }

    @Test fun anEngineErrorAfterTheStartAnnouncesTheEnd() {
        startSpeaking()
        platform.speech.onError("falló")
        assertEquals(listOf("start:Hola mundo", "end"), recorder.events)
        assertEquals(VoicePhase.ERROR, session.state.value.phase)
    }

    @Test fun anErrorBeforeTheStartAnnouncesNothing() {
        session.speak("Hola mundo")
        platform.speech.onError("sin voz instalada")
        assertEquals(VoicePhase.ERROR, session.state.value.phase)
        assertTrue(recorder.events.isEmpty())
    }

    @Test fun stoppingAnnouncesTheEnd() {
        startSpeaking()
        session.stopSpeaking()
        assertEquals(listOf("start:Hola mundo", "end"), recorder.events)
    }

    @Test fun stoppingWhenNothingIsSpeakingAnnouncesNothing() {
        session.stopSpeaking()
        session.speak("Hola")
        session.stopSpeaking()
        assertTrue(recorder.events.isEmpty())
    }

    @Test fun cancelSuspendAndCloseAnnounceTheEnd() {
        startSpeaking()
        session.cancel()
        startSpeaking()
        session.suspendSession()
        startSpeaking()
        session.close()
        assertEquals(List(3) { listOf("start:Hola mundo", "end") }.flatten(), recorder.events)
    }

    @Test fun turningOffReadAloudAnnouncesTheEnd() {
        startSpeaking()
        session.setReadAloudEnabled(false)
        assertEquals(listOf("start:Hola mundo", "end"), recorder.events)
    }

    @Test fun startingDictationWhileSpeakingAnnouncesTheEnd() {
        startSpeaking()
        session.startDictation(hasPermission = true, onDevice = true)
        assertEquals(VoicePhase.REQUESTING, session.state.value.phase)
        assertEquals(listOf("start:Hola mundo", "end"), recorder.events)
    }

    @Test fun readingAnotherTextEndsTheFirstAndStartsTheSecond() {
        startSpeaking("Primera")
        session.speak("Segunda")
        assertEquals(listOf("start:Primera", "end"), recorder.events)
        platform.speech.onStart()
        assertEquals(listOf("start:Primera", "end", "start:Segunda"), recorder.events)
        platform.speech.onDone()
        assertEquals(listOf("start:Primera", "end", "start:Segunda", "end"), recorder.events)
    }

    @Test fun aLateCallbackOfAnInterruptedReadingIsIgnored() {
        startSpeaking()
        val old = platform.speech
        session.stopSpeaking()
        old.onDone()
        old.onError("tarde")
        assertEquals(listOf("start:Hola mundo", "end"), recorder.events)
    }

    @Test fun startsAndEndsAlwaysComeInPairs() {
        repeat(4) { i ->
            startSpeaking("Frase $i")
            if (i % 2 == 0) platform.speech.onDone() else session.stopSpeaking()
        }
        assertEquals(4, recorder.events.count { it.startsWith("start:") })
        assertEquals(4, recorder.events.count { it == "end" })
        assertEquals(listOf("start:Frase 0", "end", "start:Frase 1", "end", "start:Frase 2", "end", "start:Frase 3", "end"), recorder.events)
    }

    @Test fun aFailingListenerNeverBreaksTheVoice() {
        recorder.failing = true
        startSpeaking()
        assertEquals(VoicePhase.SPEAKING, session.state.value.phase)
        platform.speech.onDone()
        assertEquals(VoicePhase.IDLE, session.state.value.phase)
        assertEquals(listOf("start:Hola mundo", "end"), recorder.events)
    }
}
