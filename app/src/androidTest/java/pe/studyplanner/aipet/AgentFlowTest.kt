package pe.studyplanner.aipet

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import pe.studyplanner.aipet.data.academic.*
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.profile.ThemeMode
import pe.studyplanner.aipet.ui.agent.AgentScreen
import pe.studyplanner.aipet.ui.theme.StudyPlannerTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** Real Compose + file-backed Room. No gateway stub can claim an action was executed. */
class AgentFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context
    private lateinit var db: AcademicDatabase
    private lateinit var name: String
    private lateinit var service: AgentService
    private lateinit var academic: RoomAcademicRepository
    private val clock = Clock.fixed(Instant.parse("2026-10-21T15:20:30Z"), ZoneOffset.UTC)
    private val platform = AlertTestPlatform()

    @Before fun prepare() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        name = "agent-flow-${UUID.randomUUID()}.db"
        db = AcademicDatabase.open(context, name)
        db.academicDao().insertProfile(ProfileEntity(studentName = "Prueba", petName = "KiroQA",
            avatar = "FOX", zoneId = "America/Lima", weekStartsOn = "MONDAY", theme = "LIGHT",
            onboardingCompleted = true, petVisible = true, preferencesRevision = 0, legacyImportVersion = 1))
        academic = RoomAcademicRepository(db, clock)
        service = AgentService(db, AlertService(db, platform, clock), clock = clock)
    }

    @After fun clean() {
        compose.runOnIdle { compose.activity.viewModelStore.clear() }
        db.close()
        context.deleteDatabase(name)
    }

    // A separate connection observes committed data, rather than the screen's in-memory state.
    private fun <T> read(block: suspend (AcademicDatabase) -> T): T {
        val reader = AcademicDatabase.open(context, name)
        return try { runBlocking { block(reader) } } finally { reader.close() }
    }
    private fun snapshot(scope: DatasetScope = DatasetScope.DEMO) =
        read { RoomAcademicRepository(it, clock).snapshot(scope) }
    private data class StoredReceipt(val scope: String, val tool: String, val ids: List<String>)
    private fun receipts(): List<StoredReceipt> = read { reader ->
        reader.openHelper.readableDatabase.query("SELECT scope,tool,entityIds FROM agent_receipt ORDER BY rowid").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val ids = JSONArray(cursor.getString(2))
                    add(StoredReceipt(cursor.getString(0), cursor.getString(1), (0 until ids.length()).map(ids::getString)))
                }
            }
        }
    }
    private fun messages() = read { it.agentDao().messages(DatasetScope.DEMO.name) }
    private fun seedTask(title: String) = runBlocking {
        academic.saveActivity(DatasetScope.DEMO, AcademicActivity(kind = ActivityKind.TASK, title = title))
    }

    private fun showDemo() {
        compose.setContent {
            StudyPlannerTheme(ThemeMode.LIGHT) {
                AgentScreen(service, onBack = {}, onOpenAgenda = {}, onOpenPlanner = {}, onOpenAlerts = {})
            }
        }
        enabledText("Demostración").performClick()
        readyInput()
        compose.onNodeWithText("Demostración").assertIsSelected()
    }
    private fun text(value: String, substring: Boolean = false): SemanticsNodeInteraction {
        compose.waitUntil(15000) {
            runCatching {
                compose.onNodeWithTag("agent_content").performScrollToNode(hasText(value, substring = substring))
                compose.onAllNodesWithText(value, substring = substring).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        return compose.onNodeWithText(value, substring = substring).performScrollTo().assertIsDisplayed()
    }
    private fun enabledText(value: String): SemanticsNodeInteraction {
        val node = text(value)
        compose.waitUntil(15000) { runCatching { node.assertIsEnabled() }.isSuccess }
        return node
    }
    private fun readyInput(): SemanticsNodeInteraction {
        compose.waitUntil(15000) {
            runCatching {
                compose.onNodeWithTag("agent_content").performScrollToNode(hasTestTag("agent_input"))
                compose.onNodeWithTag("agent_input").assertIsEnabled()
            }.isSuccess
        }
        return compose.onNodeWithTag("agent_input").performScrollTo().assertIsDisplayed()
    }
    private fun send(request: String) {
        readyInput().performTextReplacement(request)
        Espresso.closeSoftKeyboard()
        enabledText("Revisar solicitud").performClick()
    }
    private fun confirmTask(title: String) {
        send("crea una tarea \"$title\" para mañana")
        enabledText("Confirmar y ejecutar").performClick()
        compose.waitUntil(15000) { snapshot().activities.any { it.title == title } && receipts().size == 1 }
        // Wait for the assistant's post-commit append, too, before another UI action.
        text("Tarea guardada y verificada en la agenda local.")
        readyInput()
    }
    private fun assertNoWrites() {
        assertTrue(snapshot().activities.isEmpty())
        assertTrue(snapshot(DatasetScope.PERSONAL).activities.isEmpty())
        assertTrue(receipts().isEmpty())
        assertTrue(read { it.alertDao().all() }.isEmpty())
        assertTrue(platform.scheduled.isEmpty())
    }

    @Test fun reviewAndDiscardDoNotWriteAnAcademicRecordOrReceipt() {
        showDemo()
        send("crea una tarea \"Solo propuesta\" para mañana")
        text("Revisa antes de confirmar")
        enabledText("Confirmar y ejecutar").assertIsEnabled()
        assertNoWrites()
        enabledText("Descartar propuesta").performClick()
        readyInput()
        text("Propuesta descartada. No se aplicaron sus cambios.")
        assertNoWrites()
    }

    @Test fun confirmedTaskHasDurableIdReceiptAndDateOnlyInDemo() {
        showDemo()
        confirmTask("Lectura UI real")
        val task = snapshot().activities.single()
        assertEquals("Lectura UI real", task.title)
        assertEquals(ActivityKind.TASK, task.kind)
        assertEquals(ActivityTime.DateOnly(LocalDate.of(2026, 10, 22)), task.time)
        UUID.fromString(task.id)
        assertEquals(listOf(StoredReceipt("DEMO", "create_task", listOf(task.id))), receipts())
        assertTrue(snapshot(DatasetScope.PERSONAL).activities.isEmpty())
        assertTrue(messages().isEmpty()) // Conversation persistence starts disabled.
        enabledText("Ver comprobante").performClick()
        text(task.id, substring = true)
        assertTrue(read { it.alertDao().all() }.isEmpty())
    }

    @Test fun duplicateTaskTitlesAskForClarificationWithoutChoosingOrDeletingEither() {
        seedTask("Lectura duplicada")
        seedTask("Lectura duplicada")
        val before = snapshot()
        showDemo()
        send("elimina tarea \"Lectura duplicada\"")
        text("Hay varias tareas con ese título exacto. Selecciona una en Pendientes.")
        readyInput()
        assertEquals(before, snapshot())
        assertEquals(2, snapshot().activities.size)
        assertTrue(receipts().isEmpty())
        assertTrue(snapshot(DatasetScope.PERSONAL).activities.isEmpty())
    }

    @Test fun deleteRequiresSecondDialogAndDismissalPreservesTheTask() {
        val target = seedTask("Borrado con revisión")
        val preserved = seedTask("Conservar esta tarea")
        val before = snapshot()
        showDemo()
        send("elimina tarea \"Borrado con revisión\"")
        enabledText("Revisar eliminación").performClick()
        compose.onNodeWithText("Eliminar esta tarea").assertIsDisplayed()
        assertEquals(before, snapshot())
        assertTrue(receipts().isEmpty())
        compose.onNodeWithText("Seguir revisando").performClick()
        enabledText("Revisar eliminación").assertIsEnabled()
        assertEquals(before, snapshot())
        enabledText("Revisar eliminación").performClick()
        compose.onNodeWithText("Eliminar tarea confirmada").performClick()
        compose.waitUntil(15000) { snapshot().activities.none { it.id == target.id } && receipts().size == 1 }
        text("Tarea eliminada de la agenda local.")
        assertEquals(listOf(preserved), snapshot().activities)
        assertEquals(listOf(StoredReceipt("DEMO", "delete_task", listOf(target.id))), receipts())
        assertTrue(snapshot(DatasetScope.PERSONAL).activities.isEmpty())
    }

    @Test fun optInHistoryCanBeErasedWithoutErasingTheTaskOrItsReceipt() {
        showDemo()
        send("mis pendientes")
        text("No hay tareas pendientes en DEMO.")
        readyInput()
        assertTrue(messages().isEmpty())
        text("Guardar historial en este dispositivo")
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.waitUntil(15000) { read { it.agentDao().preferences("DEMO")?.historyEnabled } == true }
        confirmTask("Conservar al borrar historial")
        compose.waitUntil(15000) { messages().size == 2 }
        val before = snapshot()
        val receiptBefore = receipts()
        enabledText("Borrar conversación de este espacio").performClick()
        compose.onNodeWithText("Borrar conversación").assertIsDisplayed()
        assertEquals(2, messages().size)
        compose.onNodeWithText("Borrar mensajes").performClick()
        compose.waitUntil(15000) { messages().isEmpty() }
        readyInput()
        assertEquals(before, snapshot())
        assertEquals(receiptBefore, receipts())
        assertTrue(read { it.agentDao().preferences("DEMO")?.historyEnabled } == true)
        assertTrue(snapshot(DatasetScope.PERSONAL).activities.isEmpty())
        compose.onAllNodesWithText("Tarea guardada y verificada en la agenda local.").assertCountEquals(0)
    }

    @Test fun planningWithoutPendingObjectivesReportsRejectionAndCreatesNoSessions() {
        showDemo()
        send("planifica mi semana")
        text("No hay tareas o exámenes pendientes para planificar.")
        readyInput()
        assertNoWrites()
        compose.onAllNodesWithTag("agent_review").assertCountEquals(0)
    }
}
