package pe.studyplanner.aipet

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.studyplanner.aipet.data.academic.AcademicDatabase
import pe.studyplanner.aipet.data.academic.RoomAcademicRepository
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.profile.ThemeMode
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.ui.academic.AcademicScreen
import pe.studyplanner.aipet.ui.academic.AcademicTab
import pe.studyplanner.aipet.ui.theme.StudyPlannerTheme

/** Real forms and transactions in a unique file-backed Room fixture, never the personal database. */
@RunWith(AndroidJUnit4::class)
class AcademicFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context
    private lateinit var database: AcademicDatabase
    private lateinit var repository: RoomAcademicRepository
    private lateinit var databaseName: String
    private val personal = DatasetScope.PERSONAL
    private val demo = DatasetScope.DEMO

    @Before fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "academic-ui-${UUID.randomUUID()}.db"
        database = AcademicDatabase.open(context, databaseName)
        repository = RoomAcademicRepository(database)
    }

    @After fun cleanup() {
        compose.runOnIdle { compose.activity.viewModelStore.clear() }
        compose.waitForIdle()
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun show(tab: AcademicTab) {
        compose.setContent {
            StudyPlannerTheme(ThemeMode.LIGHT) {
                AcademicScreen(repository, UserProfile(onboardingCompleted = true), initialTab = tab, onBack = {})
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("academic_content").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Lazy items may not exist yet, especially with the runner's real 200% system font. */
    private fun listText(text: String): SemanticsNodeInteraction {
        compose.waitUntil(15_000) {
            runCatching {
                compose.onNodeWithTag("academic_content").performScrollToNode(hasText(text))
                compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        return compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private fun editorClick(text: String) {
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed().performClick()
    }

    private fun field(label: String, text: String) {
        compose.onNodeWithText(label).performScrollTo().performTextReplacement(text)
        Espresso.closeSoftKeyboard()
    }

    private fun awaitSnapshot(scope: DatasetScope = personal, predicate: (AcademicSnapshot) -> Boolean): AcademicSnapshot =
        runBlocking { withTimeout(15_000) { repository.observe(scope).first(predicate) } }

    /** A second Room connection proves the assertion reads persisted rows rather than UI state. */
    private fun persisted(scope: DatasetScope = personal): AcademicSnapshot {
        val other = AcademicDatabase.open(context, databaseName)
        return try { runBlocking { RoomAcademicRepository(other).snapshot(scope) } } finally { other.close() }
    }

    @Test fun courseRejectsBlankThenPersistsReviewedNameAndCode() {
        show(AcademicTab.COURSES)
        listText("Nuevo curso").performClick()
        waitText("Nombre del curso")
        editorClick("Guardar curso")
        compose.onNodeWithText("Escribe el nombre del curso.").performScrollTo().assertIsDisplayed()
        assertTrue(persisted().courses.isEmpty())
        assertEquals(0L, persisted().revision)

        field("Nombre del curso", "Curso QA real")
        field("Código o alias (opcional)", "QA-UI")
        editorClick("Guardar curso")
        awaitSnapshot { it.courses.size == 1 }
        listText("Editar Curso QA real").assertIsDisplayed()
        val saved = persisted().courses.single()
        assertEquals("Curso QA real", saved.name)
        assertEquals("QA-UI", saved.code)
        assertEquals(1L, saved.version)
        assertTrue(persisted(demo).courses.isEmpty())
    }

    @Test fun undatedTaskCanBeCreatedEditedCompletedAndDeletedOnlyAfterConfirmation() {
        show(AcademicTab.TASKS)
        listText("Crear tarea").performClick()
        waitText("Título")
        field("Título", "Lectura QA sin fecha")
        editorClick("Continuar con fecha y horario")
        compose.onNodeWithText("Sin fecha: no aparecerá como vencida ni ocupará una franja del calendario.")
            .performScrollTo().assertIsDisplayed()
        editorClick("Guardar actividad")
        val created = awaitSnapshot { it.activities.size == 1 }.activities.single()
        assertEquals(ActivityTime.Unscheduled, created.time)
        assertEquals(ActivityStatus.TODO, created.status)
        assertEquals(1L, persisted().revision)

        listText("Editar Lectura QA sin fecha").performClick()
        waitText("Título")
        field("Título", "Lectura QA editada")
        editorClick("Continuar con fecha y horario")
        editorClick("Guardar actividad")
        awaitSnapshot { it.activities.singleOrNull()?.title == "Lectura QA editada" }
        listText("Completar Lectura QA editada").performClick()
        awaitSnapshot { it.activities.singleOrNull()?.status == ActivityStatus.COMPLETED }
        val completed = persisted().activities.single()
        assertEquals(created.id, completed.id)
        assertEquals(3L, completed.version)
        assertEquals(ActivityTime.Unscheduled, completed.time)

        listText("Pendientes").performClick()
        compose.onNode(hasText("Todas") and hasAnyAncestor(isPopup())).performScrollTo().performClick()
        listText("Eliminar Lectura QA editada").performClick()
        waitText("¿Eliminar actividad?")
        assertEquals(1, persisted().activities.size)
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(1, persisted().activities.size)
        listText("Eliminar Lectura QA editada").performClick()
        compose.onNodeWithText("Eliminar actividad").performClick()
        awaitSnapshot { it.activities.isEmpty() && it.revision == 4L }
        assertTrue(persisted().activities.isEmpty())
        assertTrue(persisted(demo).activities.isEmpty())
    }

    @Test fun demoEntrySeedAndResetThroughUiPreservePersonalRows() {
        runBlocking {
            val course = repository.saveCourse(personal, Course(name = "Personal QA conservado"))
            repository.saveActivity(personal, AcademicActivity(kind = ActivityKind.TASK, title = "Tarea personal QA", courseId = course.id))
        }
        val before = persisted()
        show(AcademicTab.COURSES)
        listText("Abrir demostración aislada").performClick()
        waitText("Entrar en demostración")
        assertTrue(persisted(demo).activities.isEmpty())
        compose.onNodeWithText("Entrar en demo").performClick()
        listText("Crear datos ficticios").performClick()
        compose.onNodeWithText("Crear demo").performClick()
        val seeded = awaitSnapshot(demo) { it.activities.isNotEmpty() }
        runBlocking { repository.saveActivity(demo, AcademicActivity(kind = ActivityKind.TASK, title = "Marcador QA para restablecer")) }
        listText("Restablecer solo demostración").performClick()
        waitText("¿Restablecer demostración?")
        assertEquals(seeded.activities.size + 1, persisted(demo).activities.size)
        compose.onNodeWithText("Restablecer demo").performClick()
        awaitSnapshot(demo) { it.activities.size == seeded.activities.size && it.activities.none { row -> row.title == "Marcador QA para restablecer" } }
        assertEquals(before, persisted())
        listText("Volver a mis datos personales").performClick()
        compose.onNodeWithText("Ver mis datos").performClick()
        listText("Editar Personal QA conservado").assertIsDisplayed()
        assertEquals(before, persisted())
    }
}
