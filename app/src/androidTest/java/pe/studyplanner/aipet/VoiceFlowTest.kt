package pe.studyplanner.aipet

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import pe.studyplanner.aipet.domain.profile.ThemeMode
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.domain.voice.*
import pe.studyplanner.aipet.ui.theme.StudyPlannerTheme
import pe.studyplanner.aipet.ui.voice.VoicePanel

/**
 * The production Compose panel and state machine use a controlled voice platform.
 * These are interaction and cancellation tests, not evidence of audible TTS or real STT.
 * The runner grants RECORD_AUDIO temporarily and restores its original state afterwards.
 */
class VoiceFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var session: VoiceSession
    private lateinit var scope: CoroutineScope
    private lateinit var platform: ControlledVoicePlatform
    private val profile = mutableStateOf(UserProfile(studentName = "Prueba", petName = "KiroQA", petVisible = true))
    private val panelVisible = mutableStateOf(true)
    private val usedTranscripts = mutableListOf<String>()
    private val readText = "Tienes dos tareas para revisar."

    @After fun clean() {
        if (::session.isInitialized) compose.runOnIdle {
            session.close()
            scope.cancel()
        }
    }

    private fun show(capabilities: VoiceCapabilities = VoiceCapabilities(true, true, true)) {
        platform = ControlledVoicePlatform(capabilities)
        compose.runOnIdle {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            session = VoiceSession(platform, scope)
        }
        compose.setContent {
            StudyPlannerTheme(ThemeMode.LIGHT) {
                Column(Modifier.testTag("voice_test_scroll").verticalScroll(rememberScrollState())) {
                    if (panelVisible.value) VoicePanel(
                        session = session,
                        profile = profile.value,
                        onTogglePet = { profile.value = profile.value.copy(petVisible = !profile.value.petVisible) },
                        onUseTranscript = { usedTranscripts += it },
                        readText = readText,
                        enabled = true,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun text(value: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("voice_test_scroll").performScrollToNode(hasText(value))
        return compose.onNodeWithText(value).performScrollTo().assertIsDisplayed()
    }

    private fun startFromPanel() {
        assertEquals("The test runner must grant microphone permission temporarily.",
            PackageManager.PERMISSION_GRANTED, compose.activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        text("Dictar solicitud").performClick()
        compose.onNodeWithText("Antes de usar el micrófono").assertIsDisplayed()
        assertEquals(0, platform.recognitionStarts)
        compose.onNodeWithText("Continuar con el dictado").performScrollTo().performClick()
        compose.waitUntil(10000) { platform.recognitionStarts == 1 }
    }

    private fun finalTranscript(value: String) {
        compose.runOnIdle {
            platform.recognitionEvents!!.onReady()
            platform.recognitionEvents!!.onResult(value)
        }
        compose.waitUntil(10000) { session.state.value.phase == VoicePhase.REVIEW }
        assertTrue(usedTranscripts.isEmpty())
        assertEquals(0, platform.speechStarts)
    }

    @Test fun cancellingPrivacyDialogNeverStartsRecognition() {
        show()
        text("Dictar solicitud").performClick()
        compose.onNodeWithText("Antes de usar el micrófono").assertIsDisplayed()
        assertEquals(0, platform.recognitionStarts)
        compose.onNodeWithText("Ahora no").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(0, platform.recognitionStarts)
        assertEquals(VoicePhase.IDLE, session.state.value.phase)
        assertTrue(usedTranscripts.isEmpty())
    }

    @Test fun finalTranscriptMustBeEditedAndExplicitlyUsedAsText() {
        show()
        startFromPanel()
        finalTranscript("crea tarea lectura para mañana")
        compose.onNodeWithTag("voice_transcript").performScrollTo()
            .performTextReplacement("crea tarea \"Lectura corregida\" para mañana")
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        assertTrue(usedTranscripts.isEmpty())
        text("Usar transcripción").performClick()
        compose.waitUntil(10000) { usedTranscripts.size == 1 }
        assertEquals(listOf("crea tarea \"Lectura corregida\" para mañana"), usedTranscripts)
        assertEquals(VoicePhase.IDLE, session.state.value.phase)
        assertEquals(0, platform.speechStarts)
    }

    @Test fun discardLeavesNoTextToSendAndNoSpeech() {
        show()
        startFromPanel()
        finalTranscript("elimina una tarea que no quiero enviar")
        text("Descartar transcripción").performClick()
        compose.waitForIdle()
        assertEquals(VoicePhase.IDLE, session.state.value.phase)
        assertTrue(session.state.value.transcript.isBlank())
        assertTrue(usedTranscripts.isEmpty())
        assertEquals(0, platform.speechStarts)
    }

    @Test fun cancelDictationIgnoresAResultDeliveredAfterCancellation() {
        show()
        startFromPanel()
        val stale = platform.recognitionEvents!!
        compose.runOnIdle { stale.onReady() }
        val stopsBeforeCancel = platform.recognitionStops
        text("Cancelar dictado").performClick()
        compose.runOnIdle { stale.onResult("crea tarea \"Resultado tardío\" para mañana") }
        compose.waitForIdle()
        assertEquals(VoicePhase.IDLE, session.state.value.phase)
        assertTrue(session.state.value.transcript.isBlank())
        assertTrue(usedTranscripts.isEmpty())
        assertTrue(platform.recognitionStops > stopsBeforeCancel)
        compose.onAllNodesWithText("Usar transcripción").assertCountEquals(0)
    }

    @Test fun missingRecognitionServiceOffersTextWithoutStartingAnything() {
        show(VoiceCapabilities(speechAvailable = false, onDeviceAvailable = false, ttsEngineAvailable = true))
        text("No hay un servicio de dictado disponible. Puedes escribir tu solicitud.")
        text("Dictar solicitud").assertIsNotEnabled()
        assertEquals(0, platform.recognitionStarts)
        assertEquals(0, platform.speechStarts)
        assertTrue(usedTranscripts.isEmpty())
    }

    @Test fun readAloudRequiresOptInAndMutingStopsCurrentSpeech() {
        show()
        startFromPanel()
        finalTranscript("Una transcripción que debe conservarse")
        text("Leer texto mostrado").assertIsNotEnabled()
        assertFalse(session.state.value.readAloudEnabled)
        compose.onNodeWithTag("voice_read_aloud").performScrollTo().performClick()
        text("Leer texto mostrado").performClick()
        compose.waitUntil(10000) { platform.speechStarts == 1 }
        assertEquals(readText, platform.spokenText)
        val stale = platform.speechEvents!!
        compose.runOnIdle { stale.onStart() }
        compose.waitUntil(10000) { session.state.value.phase == VoicePhase.SPEAKING }
        val stopsBeforeMute = platform.speechStops
        compose.onNodeWithTag("voice_read_aloud").performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(session.state.value.readAloudEnabled)
        assertEquals(VoicePhase.REVIEW, session.state.value.phase)
        assertTrue(platform.speechStops > stopsBeforeMute)
        compose.runOnIdle { stale.onStart() }
        compose.waitForIdle()
        assertEquals(VoicePhase.REVIEW, session.state.value.phase)
        assertEquals("Una transcripción que debe conservarse", session.state.value.transcript)
        compose.onNodeWithTag("voice_transcript").performScrollTo()
            .assertTextContains("Una transcripción que debe conservarse")
        assertTrue(usedTranscripts.isEmpty())
        text("Leer texto mostrado").assertIsNotEnabled()
    }

    @Test fun hidingThePetStopsListeningAndShowingItNeverRestartsTheMicrophone() {
        show()
        startFromPanel()
        val stale = platform.recognitionEvents!!
        compose.runOnIdle {
            stale.onReady()
            stale.onPartial("palabras todavía sin confirmar")
        }
        val stopsBeforeHide = platform.recognitionStops
        text("Ocultar mascota").performClick()
        compose.waitUntil(10000) { !profile.value.petVisible && session.state.value.phase == VoicePhase.IDLE }
        assertTrue(platform.recognitionStops > stopsBeforeHide)
        compose.runOnIdle { stale.onResult("crea tarea \"No debe aparecer\" para mañana") }
        text("Mostrar mascota").performClick()
        compose.waitForIdle()
        assertTrue(profile.value.petVisible)
        assertEquals(1, platform.recognitionStarts)
        assertEquals(VoicePhase.IDLE, session.state.value.phase)
        assertTrue(session.state.value.transcript.isBlank())
        assertTrue(usedTranscripts.isEmpty())
    }

    @Test fun leavingThePanelCancelsCaptureAndRejectsLateRecognition() {
        show()
        startFromPanel()
        val stale = platform.recognitionEvents!!
        compose.runOnIdle { stale.onReady() }
        val stopsBeforeLeaving = platform.recognitionStops
        compose.runOnIdle { panelVisible.value = false }
        compose.waitUntil(10000) { session.state.value.phase == VoicePhase.IDLE }
        assertTrue(platform.recognitionStops > stopsBeforeLeaving)
        compose.runOnIdle {
            stale.onPartial("parcial después de salir")
            stale.onResult("resultado después de salir")
        }
        compose.waitForIdle()
        assertEquals(VoicePhase.IDLE, session.state.value.phase)
        assertTrue(session.state.value.transcript.isBlank())
        assertTrue(usedTranscripts.isEmpty())
        assertEquals(0, platform.speechStarts)
    }

    private class ControlledVoicePlatform(private val available: VoiceCapabilities) : VoicePlatform {
        var recognitionStarts = 0
        var recognitionStops = 0
        var speechStarts = 0
        var speechStops = 0
        var spokenText: String? = null
        var recognitionEvents: RecognitionEvents? = null
        var speechEvents: SpeechEvents? = null
        override fun capabilities() = available
        override fun startRecognition(onDevice: Boolean, listener: RecognitionEvents) {
            recognitionStarts++
            recognitionEvents = listener
        }
        override fun stopRecognition() { recognitionStops++ }
        override fun speak(text: String, listener: SpeechEvents) {
            speechStarts++
            spokenText = text
            speechEvents = listener
        }
        override fun stopSpeech() { speechStops++ }
        override fun release() { stopRecognition(); stopSpeech() }
    }
}
