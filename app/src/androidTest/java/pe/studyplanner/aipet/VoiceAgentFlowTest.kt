package pe.studyplanner.aipet

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pe.studyplanner.aipet.data.academic.*
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.profile.ThemeMode
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.domain.voice.*
import pe.studyplanner.aipet.ui.agent.AgentScreen
import pe.studyplanner.aipet.ui.theme.StudyPlannerTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** Controlled recognition events feed the real agent screen, service and file-backed Room. */
class VoiceAgentFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context
    private lateinit var db: AcademicDatabase
    private lateinit var databaseName: String
    private lateinit var service: AgentService
    private lateinit var voice: VoiceSession
    private lateinit var voiceScope: CoroutineScope
    private val voicePlatform = DraftVoicePlatform()
    private val alerts = AlertTestPlatform()
    private val clock = Clock.fixed(Instant.parse("2026-10-21T15:20:30Z"), ZoneOffset.UTC)

    @Before fun prepare() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "voice-agent-flow-${UUID.randomUUID()}.db"
        db = AcademicDatabase.open(context, databaseName)
        db.academicDao().insertProfile(ProfileEntity(studentName = "Prueba", petName = "KiroQA",
            avatar = "FOX", zoneId = "America/Lima", weekStartsOn = "MONDAY", theme = "LIGHT",
            onboardingCompleted = true, petVisible = true, preferencesRevision = 0, legacyImportVersion = 1))
        service = AgentService(db, AlertService(db, alerts, clock), clock = clock)
        compose.runOnIdle {
            voiceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            voice = VoiceSession(voicePlatform, voiceScope)
        }
    }

    @After fun clean() {
        compose.runOnIdle {
            compose.activity.viewModelStore.clear()
            if (::voice.isInitialized) voice.close()
            if (::voiceScope.isInitialized) voiceScope.cancel()
        }
        if (::db.isInitialized) db.close()
        if (::context.isInitialized && ::databaseName.isInitialized) context.deleteDatabase(databaseName)
    }

    private fun <T> read(block: suspend (AcademicDatabase) -> T): T {
        val reader = AcademicDatabase.open(context, databaseName)
        return try { runBlocking { block(reader) } } finally { reader.close() }
    }

    private fun snapshot(scope: DatasetScope = DatasetScope.DEMO) =
        read { RoomAcademicRepository(it, clock).snapshot(scope) }

    private data class StoredReceipt(val scope: String, val tool: String, val ids: List<String>, val status: String)

    private fun receipts(): List<StoredReceipt> = read { reader ->
        reader.openHelper.readableDatabase.query("SELECT scope,tool,entityIds,result FROM agent_receipt ORDER BY rowid").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val ids = JSONArray(cursor.getString(2))
                    add(StoredReceipt(cursor.getString(0), cursor.getString(1),
                        (0 until ids.length()).map(ids::getString), JSONObject(cursor.getString(3)).getString("status")))
                }
            }
        }
    }

    private fun assertNoWrites() {
        assertTrue(snapshot().activities.isEmpty())
        assertTrue(snapshot(DatasetScope.PERSONAL).activities.isEmpty())
        assertTrue(receipts().isEmpty())
        assertTrue(read { it.alertDao().all() }.isEmpty())
        assertTrue(alerts.scheduled.isEmpty())
    }

    private fun text(value: String): SemanticsNodeInteraction {
        compose.waitUntil(15000) {
            runCatching {
                compose.onNodeWithTag("agent_content").performScrollToNode(hasText(value))
                compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        return compose.onNodeWithText(value).performScrollTo().assertIsDisplayed()
    }

    private fun enabledText(value: String): SemanticsNodeInteraction {
        val node = text(value)
        compose.waitUntil(15000) { runCatching { node.assertIsEnabled() }.isSuccess }
        return node
    }

    private fun input(): SemanticsNodeInteraction {
        compose.onNodeWithTag("agent_content").performScrollToNode(hasTestTag("agent_input"))
        val node = compose.onNodeWithTag("agent_input").performScrollTo()
        compose.waitUntil(15000) { runCatching { node.assertIsEnabled() }.isSuccess }
        return node
    }

    private fun showDemoAgent() {
        compose.setContent {
            StudyPlannerTheme(ThemeMode.LIGHT) {
                AgentScreen(service, onBack = {}, onOpenAgenda = {}, onOpenPlanner = {}, onOpenAlerts = {},
                    voiceEnabled = true, profile = UserProfile(studentName = "Prueba", petName = "KiroQA",
                        theme = ThemeMode.LIGHT, onboardingCompleted = true), voiceSession = voice)
            }
        }
        enabledText("Demostración").performClick()
        input()
    }

    private fun dictateFinal(command: String) {
        enabledText("Abrir controles de voz").performClick()
        enabledText("Tocar para hablar").performClick()
        compose.onNodeWithText("Antes de usar el micrófono").assertIsDisplayed()
        assertEquals(0, voicePlatform.recognitionStarts)
        compose.onNodeWithText("Continuar con el dictado").performScrollTo().performClick()
        compose.waitUntil(15000) { voicePlatform.recognitionStarts == 1 }
        compose.runOnIdle {
            voicePlatform.events!!.onReady()
            voicePlatform.events!!.onResult(command)
        }
        compose.waitUntil(15000) { voice.state.value.phase == VoicePhase.REVIEW }
    }

    @Test fun voiceDraftRequiresReviewAndConfirmationBeforeAnyRoomWrite() {
        assertEquals("The runner grants RECORD_AUDIO temporarily outside the instrumentation process.",
            PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        showDemoAgent()
        dictateFinal("crea tarea \"Borrador original F6\" para mañana")
        assertNoWrites()
        compose.onNodeWithTag("agent_content").performScrollToNode(hasTestTag("voice_transcript"))
        val title = "Lectura corregida por voz F6"
        val command = "crea tarea \"$title\" para mañana"
        compose.onNodeWithTag("voice_transcript").performScrollTo().performTextReplacement(command)
        Espresso.closeSoftKeyboard()
        assertNoWrites()
        enabledText("Revisar esta petición").performClick()
        enabledText("Confirmar y ejecutar").assertIsEnabled()
        assertNoWrites()
        enabledText("Confirmar y ejecutar").performClick()
        compose.waitUntil(15000) { snapshot().activities.size == 1 && receipts().size == 1 }
        text("Tarea guardada y verificada en la agenda local.")
        val task = snapshot().activities.single()
        UUID.fromString(task.id)
        assertEquals(title, task.title)
        assertEquals(ActivityKind.TASK, task.kind)
        assertEquals(ActivityTime.DateOnly(LocalDate.of(2026, 10, 22)), task.time)
        assertEquals(listOf(StoredReceipt("DEMO", "create_task", listOf(task.id), "COMMITTED")), receipts())
        assertTrue(snapshot(DatasetScope.PERSONAL).activities.isEmpty())
        assertTrue(read { it.agentDao().messages("DEMO") }.isEmpty())
        assertTrue(read { it.alertDao().all() }.isEmpty())
        assertTrue(alerts.scheduled.isEmpty())
        assertEquals(1, voicePlatform.recognitionStarts)
        assertEquals(0, voicePlatform.speechStarts)
    }

    @Test fun changingFromDemoToPersonalDiscardsAnUnsentVoiceDraft() {
        assertEquals("The runner grants RECORD_AUDIO temporarily outside the instrumentation process.",
            PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        showDemoAgent()
        dictateFinal("crea tarea \"Solo debe permanecer en DEMO\" para mañana")
        assertTrue(voice.state.value.transcript.contains("Solo debe permanecer en DEMO"))
        assertNoWrites()
        enabledText("Mi espacio").performClick()
        input().let { node ->
            assertEquals("", node.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        }
        compose.waitUntil(15000) { voice.state.value.phase == VoicePhase.IDLE }
        assertTrue(voice.state.value.transcript.isBlank())
        text("Mi espacio").assertIsSelected()
        enabledText("Abrir controles de voz").performClick()
        enabledText("Tocar para hablar").assertIsEnabled()
        compose.onAllNodesWithTag("voice_transcript").assertCountEquals(0)
        compose.onAllNodesWithText("Usar transcripción").assertCountEquals(0)
        assertNoWrites()
        assertEquals(1, voicePlatform.recognitionStarts)
        assertEquals(0, voicePlatform.speechStarts)
    }

    private class DraftVoicePlatform : VoicePlatform {
        var recognitionStarts = 0
        var speechStarts = 0
        var events: RecognitionEvents? = null
        override fun capabilities() = VoiceCapabilities(true, false, false)
        override fun startRecognition(onDevice: Boolean, listener: RecognitionEvents) {
            recognitionStarts++
            events = listener
        }
        override fun stopRecognition() = Unit
        override fun speak(text: String, listener: SpeechEvents) { speechStarts++ }
        override fun stopSpeech() = Unit
        override fun release() = Unit
    }
}
