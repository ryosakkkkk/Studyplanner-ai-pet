package pe.studyplanner.aipet.domain.voice

import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Controlled engine callbacks verify state and lifetime; they do not claim native STT accuracy. */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSessionTest {
    @Test fun constructionOnlyQueriesCapabilitiesWithoutOpeningEitherEngine() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        assertEquals(VoicePhase.IDLE, session.state.value.phase)
        assertTrue(session.state.value.capabilities.speechAvailable)
        assertFalse(session.state.value.readAloudEnabled)
        assertEquals(0, platform.recognitionRequests.size)
        assertEquals(0, platform.spoken.size)
    }

    @Test fun deniedPermissionNeverCallsRecognizerAndTextFallbackIsExplicit() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(false)
        assertEquals(0, platform.recognitionRequests.size)
        assertEquals(VoicePhase.ERROR, session.state.value.phase)
        assertTrue(session.state.value.message.contains("escribiendo"))
    }

    @Test fun absentServiceDoesNotTryToCreateARecognizer() = runTest {
        val platform = ControlledPlatform().apply { available = VoiceCapabilities(ttsEngineAvailable = true) }
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true)
        assertTrue(platform.recognitionRequests.isEmpty())
        assertTrue(session.state.value.message.contains("servicio"))
    }

    @Test fun explicitOnDeviceRequestIsNotSilentlyReplacedWithRemoteRecognition() = runTest {
        val platform = ControlledPlatform().apply { available = VoiceCapabilities(speechAvailable = true, onDeviceAvailable = false) }
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true, onDevice = true)
        assertTrue(platform.recognitionRequests.isEmpty())
        assertEquals(VoicePhase.ERROR, session.state.value.phase)
        assertTrue(session.state.value.message.contains("en el dispositivo"))
    }

    @Test fun onDeviceOnlyServiceCanBeSelectedEvenWithoutDefaultRecognizer() = runTest {
        val platform = ControlledPlatform().apply { available = VoiceCapabilities(onDeviceAvailable = true) }
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true, onDevice = true)
        assertEquals(listOf(true), platform.recognitionRequests.map { it.first })
        session.cancel()
    }

    @Test fun requestedMicrophoneDoesNotBecomeListeningUntilNativeReady() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true)
        assertEquals(VoicePhase.REQUESTING, session.state.value.phase)
        platform.recognition.onPartial("resultado fuera de orden")
        assertEquals("", session.state.value.partial)
        platform.recognition.onReady()
        assertEquals(VoicePhase.LISTENING, session.state.value.phase)
        platform.recognition.onPartial("crea una tarea")
        assertEquals("crea una tarea", session.state.value.partial)
        assertNull(session.takeTranscript())
        platform.recognition.onEndOfSpeech()
        assertEquals(VoicePhase.TRANSCRIBING, session.state.value.phase)
        session.cancel()
    }

    @Test fun finalTextRequiresExplicitOneTimeTransferAndReleasesMicrophone() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true)
        val listener = platform.recognition
        listener.onReady()
        listener.onResult("  crea\n tarea \"Cafe\u0301\"  ")
        assertEquals(VoicePhase.REVIEW, session.state.value.phase)
        assertEquals("crea tarea \"Café\"", session.state.value.transcript)
        assertEquals("", session.state.value.partial)
        assertTrue(platform.recognitionStops >= 2)
        assertEquals("crea tarea \"Café\"", session.takeTranscript())
        assertNull(session.takeTranscript())
        assertEquals(VoicePhase.IDLE, session.state.value.phase)
        listener.onResult("resultado tardío")
        assertEquals("", session.state.value.transcript)
    }

    @Test fun repeatedStartWhilePreparingOrListeningDoesNotCreateAnotherSession() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true)
        session.startDictation(true)
        platform.recognition.onReady()
        session.startDictation(true)
        platform.recognition.onEndOfSpeech()
        session.startDictation(true)
        assertEquals(1, platform.recognitionRequests.size)
        session.cancel()
    }

    @Test fun cancelDiscardsPartialsAndIgnoresEveryLateCallback() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true)
        val previous = platform.recognition
        previous.onReady()
        previous.onPartial("parcial privado")
        session.cancel()
        val stopped = session.state.value
        previous.onReady(); previous.onPartial("tardío"); previous.onEndOfSpeech()
        previous.onResult("crea tarea \"No crear\""); previous.onError("tardío")
        assertEquals(stopped, session.state.value)
        assertEquals("", stopped.partial)
        assertEquals("", stopped.transcript)
        assertTrue(platform.releases > 0)
    }

    @Test fun oldGenerationCannotOverwriteANewerReview() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true)
        val old = platform.recognition
        session.cancel()
        session.startDictation(true)
        val current = platform.recognition
        old.onResult("No usar")
        assertEquals(VoicePhase.REQUESTING, session.state.value.phase)
        current.onResult("Texto actual")
        old.onError("Error anterior")
        assertEquals("Texto actual", session.takeTranscript())
    }

    @Test fun fifteenSecondDeadlineAlsoBoundsPreparation() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true)
        runCurrent()
        advanceTimeBy(14_999)
        assertEquals(VoicePhase.REQUESTING, session.state.value.phase)
        advanceTimeBy(1); runCurrent()
        assertEquals(VoicePhase.ERROR, session.state.value.phase)
        assertTrue(session.state.value.message.contains("15 segundos"))
        assertTrue(platform.releases > 0)
        platform.recognition.onReady()
        assertEquals(VoicePhase.ERROR, session.state.value.phase)
    }

    @Test fun readyAndTranscribingDoNotExtendTheSingleFifteenSecondWindow() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true)
        runCurrent()
        advanceTimeBy(7_000)
        platform.recognition.onReady()
        advanceTimeBy(7_000)
        platform.recognition.onEndOfSpeech()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(VoicePhase.ERROR, session.state.value.phase)
        platform.recognition.onResult("demasiado tarde")
        assertEquals("", session.state.value.transcript)
    }

    @Test fun suspensionReleasesBothEnginesAndDoesNotRestartOnRefresh() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true)
        val previous = platform.recognition
        previous.onReady()
        session.suspendSession()
        session.refresh()
        previous.onResult("No transferir")
        assertEquals(VoicePhase.IDLE, session.state.value.phase)
        assertTrue(platform.releases > 0)
        assertEquals(1, platform.recognitionRequests.size)
        assertNull(session.takeTranscript())
    }

    @Test fun discardedFinalTranscriptCannotBeReusedOrResurrected() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true)
        val previous = platform.recognition
        previous.onResult("Texto descartado")
        session.discardTranscript()
        previous.onResult("Texto descartado")
        assertEquals("", session.state.value.transcript)
        assertNull(session.takeTranscript())
    }

    @Test fun closeIsTerminalIdempotentAndLateCallbacksCannotResurrectEngines() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true)
        val previous = platform.recognition
        session.close()
        val releases = platform.releases
        val closedState = session.state.value
        session.close(); session.cancel(); session.refresh(); session.startDictation(true)
        session.setReadAloudEnabled(true); session.speak("Texto"); session.suspendSession()
        previous.onReady(); previous.onResult("tardío")
        assertEquals(releases, platform.releases)
        assertEquals(closedState, session.state.value)
        assertEquals(1, platform.recognitionRequests.size)
        assertTrue(platform.spoken.isEmpty())
    }

    @Test fun wakeNameMatchesNfcAndRootCaseAtTheBeginningWithoutSendingAnything() = runTest {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            val platform = ControlledPlatform()
            val session = VoiceSession(platform, backgroundScope)
            session.startDictation(true, wakeName = "KIRO Ágil")
            platform.recognition.onResult("kiro a\u0301gil, crea tarea \"Lectura\"")
            assertEquals("crea tarea \"Lectura\"", session.state.value.transcript)
            assertEquals(VoicePhase.REVIEW, session.state.value.phase)
            assertEquals(1, platform.recognitionRequests.size)
        } finally { Locale.setDefault(previousLocale) }
    }

    @Test fun wakeNameRejectsSubstringNoiseAndNameInsideAWord() = runTest {
        listOf("Oye Kiro crea tarea", "Kiroga crea tarea", "Kiro_otra crea tarea", "Kiro\u0301 crea tarea").forEach { noise ->
            val platform = ControlledPlatform()
            val session = VoiceSession(platform, backgroundScope)
            session.startDictation(true, wakeName = "Kiro")
            platform.recognition.onResult(noise)
            assertEquals(noise, VoicePhase.ERROR, session.state.value.phase)
            assertNull(session.takeTranscript())
            assertEquals(1, platform.recognitionRequests.size)
            session.close()
        }
    }

    @Test fun unicodeCaseExpansionKeepsTheBoundaryAndOriginalRequestText() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true, wakeName = "İ")
        platform.recognition.onResult("i\u0307, CREA tarea \"Mi Título\"")
        assertEquals("CREA tarea \"Mi Título\"", session.takeTranscript())
        session.startDictation(true, wakeName = "i")
        platform.recognition.onResult("İ crea tarea")
        assertNull(session.takeTranscript())
        assertEquals(VoicePhase.ERROR, session.state.value.phase)
    }

    @Test fun wakeNameAloneAndInvalidNameDoNotProduceACommand() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true, wakeName = "Kiro")
        platform.recognition.onResult("Kiro")
        assertNull(session.takeTranscript())
        assertEquals(VoicePhase.ERROR, session.state.value.phase)
        session.startDictation(true, wakeName = "Kiro\n")
        assertEquals(1, platform.recognitionRequests.size)
        session.startDictation(true, wakeName = "x".repeat(25))
        assertEquals(1, platform.recognitionRequests.size)
    }

    @Test fun oversizedFinalIsRejectedRatherThanSilentlyTurningIntoATruncatedCommand() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true)
        platform.recognition.onResult("x".repeat(4001))
        assertEquals(VoicePhase.ERROR, session.state.value.phase)
        assertTrue(session.state.value.message.contains("4000"))
        assertNull(session.takeTranscript())
    }

    @Test fun recognitionErrorsAndThrownPermissionFailuresLeaveRecoverableTextFallback() = runTest {
        val platform = ControlledPlatform().apply { recognitionFailure = SecurityException() }
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true)
        assertEquals(VoicePhase.ERROR, session.state.value.phase)
        assertTrue(session.state.value.message.contains("permiso"))
        assertTrue(platform.releases > 0)
        platform.recognitionFailure = null
        session.startDictation(true)
        platform.recognition.onError("El servicio perdió la red. Escribe tu solicitud.")
        assertTrue(session.state.value.message.contains("perdió la red"))
        assertNull(session.takeTranscript())
    }

    @Test fun speechIsSilentByDefaultAndOptInDoesNotAutomaticallyReadAnything() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.speak("Respuesta")
        assertTrue(platform.spoken.isEmpty())
        session.setReadAloudEnabled(true)
        assertTrue(platform.spoken.isEmpty())
        session.speak("Respuesta")
        assertEquals(listOf("Respuesta"), platform.spoken.map { it.first })
        assertEquals(VoicePhase.PREPARING_SPEECH, session.state.value.phase)
        session.close()
    }

    @Test fun speakingStateRequiresActualStartAndDoneLeavesMicrophoneOff() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.setReadAloudEnabled(true)
        session.speak("Texto")
        assertEquals(VoicePhase.PREPARING_SPEECH, session.state.value.phase)
        platform.speech.onStart()
        assertEquals(VoicePhase.SPEAKING, session.state.value.phase)
        platform.speech.onDone()
        assertEquals(VoicePhase.IDLE, session.state.value.phase)
        assertTrue(platform.recognitionRequests.isEmpty())
        assertTrue(platform.speechStops >= 2)
    }

    @Test fun mutingStopsSpeechAndIgnoresLateStartDoneAndErrorWithoutRemovingReview() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true)
        platform.recognition.onResult("Borrador conservado")
        session.setReadAloudEnabled(true)
        session.speak("Respuesta")
        val previous = platform.speech
        previous.onStart()
        session.setReadAloudEnabled(false)
        val muted = session.state.value
        previous.onStart(); previous.onDone(); previous.onError("tardío")
        assertEquals(muted, session.state.value)
        assertEquals(VoicePhase.REVIEW, muted.phase)
        assertEquals("Borrador conservado", session.takeTranscript())
    }

    @Test fun startingDictationStopsSpeechBeforeOpeningMicrophone() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.setReadAloudEnabled(true)
        session.speak("Respuesta")
        val previous = platform.speech
        previous.onStart()
        platform.operations.clear()
        session.startDictation(true)
        assertTrue(platform.operations.indexOf("stopSpeech") < platform.operations.indexOf("startRecognition"))
        previous.onDone()
        assertEquals(VoicePhase.REQUESTING, session.state.value.phase)
        session.cancel()
    }

    @Test fun speechCannotInterruptAnActiveDictationOrStartAutoListening() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.setReadAloudEnabled(true)
        session.startDictation(true)
        platform.recognition.onReady()
        session.speak("Respuesta")
        assertTrue(platform.spoken.isEmpty())
        assertEquals(VoicePhase.LISTENING, session.state.value.phase)
        session.cancel()
    }

    @Test fun unavailableTtsAndTtsFailurePreservePreviouslyRecognizedReview() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.startDictation(true)
        platform.recognition.onResult("Borrador")
        platform.available = platform.available.copy(ttsEngineAvailable = false)
        session.setReadAloudEnabled(true)
        session.speak("Respuesta")
        assertTrue(platform.spoken.isEmpty())
        assertEquals("Borrador", session.state.value.transcript)
        platform.available = platform.available.copy(ttsEngineAvailable = true)
        session.speak("Respuesta")
        platform.speech.onError("No hay una voz española instalada.")
        assertEquals(VoicePhase.ERROR, session.state.value.phase)
        assertEquals("Borrador", session.takeTranscript())
    }

    @Test fun ttsPreparationHasFifteenSecondTimeoutAndLateInitCannotSpeak() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.setReadAloudEnabled(true)
        session.speak("Respuesta")
        val previous = platform.speech
        runCurrent(); advanceTimeBy(15_000); runCurrent()
        assertEquals(VoicePhase.ERROR, session.state.value.phase)
        assertTrue(platform.releases > 0)
        previous.onStart()
        assertEquals(VoicePhase.ERROR, session.state.value.phase)
    }

    @Test fun speechLengthAndDurationHaveExplicitBoundsRatherThanSilentTruncation() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.setReadAloudEnabled(true)
        session.speak("x".repeat(3901))
        assertTrue(platform.spoken.isEmpty())
        assertTrue(session.state.value.message.contains("3900"))
        session.speak("Respuesta breve")
        platform.speech.onStart()
        runCurrent(); advanceTimeBy(120_000); runCurrent()
        assertEquals(VoicePhase.ERROR, session.state.value.phase)
        assertTrue(session.state.value.message.contains("dos minutos"))
    }

    @Test fun rateAndVolumeAreBoundedIgnoreNanAndDoNotCreateAnEngine() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.setSpeechRate(99f)
        session.setSpeechVolume(-1f)
        assertEquals(1.5f, session.state.value.speechRate, 0f)
        assertEquals(0f, session.state.value.speechVolume, 0f)
        session.setSpeechRate(Float.NaN)
        session.setSpeechVolume(Float.POSITIVE_INFINITY)
        assertEquals(1.5f to 0f, platform.options.last())
        assertTrue(platform.spoken.isEmpty())
        assertTrue(platform.recognitionRequests.isEmpty())
    }

    @Test fun changingSpeechOptionsStopsCurrentUtteranceAndNeedsAnotherExplicitRead() = runTest {
        val platform = ControlledPlatform()
        val session = VoiceSession(platform, backgroundScope)
        session.setReadAloudEnabled(true)
        session.speak("Respuesta")
        val previous = platform.speech
        previous.onStart()
        session.setSpeechRate(0.8f)
        previous.onStart()
        assertEquals(VoicePhase.IDLE, session.state.value.phase)
        assertEquals(1, platform.spoken.size)
        session.setSpeechVolume(0.5f)
        session.speak("Respuesta")
        assertEquals(0.8f to 0.5f, platform.options.last())
        assertEquals(2, platform.spoken.size)
        session.close()
    }

    private class ControlledPlatform : VoicePlatform {
        var available = VoiceCapabilities(true, true, true)
        val recognitionRequests = mutableListOf<Pair<Boolean, RecognitionEvents>>()
        val spoken = mutableListOf<Pair<String, SpeechEvents>>()
        val options = mutableListOf<Pair<Float, Float>>()
        val operations = mutableListOf<String>()
        var recognitionStops = 0
        var speechStops = 0
        var releases = 0
        var recognitionFailure: Exception? = null
        val recognition get() = recognitionRequests.last().second
        val speech get() = spoken.last().second
        override fun capabilities() = available
        override fun startRecognition(onDevice: Boolean, listener: RecognitionEvents) {
            recognitionFailure?.let { throw it }
            operations += "startRecognition"
            recognitionRequests += onDevice to listener
        }
        override fun stopRecognition() { recognitionStops++; operations += "stopRecognition" }
        override fun speak(text: String, listener: SpeechEvents) { spoken += text to listener }
        override fun stopSpeech() { speechStops++; operations += "stopSpeech" }
        override fun release() { releases++; stopRecognition(); stopSpeech() }
        override fun configureSpeech(rate: Float, volume: Float) { options += rate to volume }
    }
}
