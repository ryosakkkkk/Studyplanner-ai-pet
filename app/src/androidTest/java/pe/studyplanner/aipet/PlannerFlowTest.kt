package pe.studyplanner.aipet

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import pe.studyplanner.aipet.data.academic.*
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.planner.*
import pe.studyplanner.aipet.domain.profile.ThemeMode
import pe.studyplanner.aipet.ui.planner.PlannerScreen
import pe.studyplanner.aipet.ui.theme.StudyPlannerTheme

class PlannerFlowTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var database:AcademicDatabase
    private lateinit var planner:RoomPlannerRepository
    private lateinit var academic:RoomAcademicRepository
    private lateinit var context:Context
    private lateinit var name:String
    private val scope=DatasetScope.PERSONAL
    private lateinit var goal:AcademicActivity
    private val tomorrow get()=LocalDate.now(ZoneId.of("America/Lima")).plusDays(1)
    @Before fun prepare()=runBlocking {
        context=ApplicationProvider.getApplicationContext();name="planner-flow-${UUID.randomUUID()}.db"
        database=AcademicDatabase.open(context,name);planner=RoomPlannerRepository(database);academic=RoomAcademicRepository(database)
        database.academicDao().insertProfile(ProfileEntity(studentName="QA",petName="Kiro",avatar="FOX",zoneId="America/Lima",weekStartsOn="MONDAY",theme="LIGHT",onboardingCompleted=true,petVisible=true,preferencesRevision=0,legacyImportVersion=1))
        planner.saveSettings(scope,PlannerSettings(windows=DayOfWeek.entries.map { Availability(it,LocalTime.of(18,0),LocalTime.of(20,0)) }))
        goal=academic.saveActivity(scope,AcademicActivity(kind=ActivityKind.TASK,title="Objetivo QA",time=ActivityTime.DateOnly(tomorrow.plusDays(3)),estimateMinutes=60))
    }
    @After fun cleanup() { compose.runOnIdle { compose.activity.viewModelStore.clear() };database.close();context.deleteDatabase(name) }
    private fun show() {
        compose.setContent { StudyPlannerTheme(ThemeMode.LIGHT) { PlannerScreen(planner,scope,onBack={}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("planner_content").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun text(value:String):SemanticsNodeInteraction {
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithTag("planner_content").performScrollToNode(hasText(value));compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        return compose.onNodeWithText(value).performScrollTo().assertIsDisplayed()
    }
    private fun field(label:String,value:String) { text(label).performTextReplacement(value);Espresso.closeSoftKeyboard() }
    private fun persisted():PlanContext {
        val other=AcademicDatabase.open(context,name)
        return try { runBlocking { RoomPlannerRepository(other).load(scope) } } finally { other.close() }
    }
    @Test fun previewDiscardAndExplicitConfirmationWriteOnlyReviewedSessions() {
        show();text("Disponibilidad y límites").performClick();compose.onNodeWithText("Objetivos y replanificación").performClick()
        field("Inicio del horizonte (AAAA-MM-DD)",tomorrow.toString())
        text("Planificar Objetivo QA").performClick()
        text("Generar propuesta local").performClick();text("Propuesta sin aplicar").assertIsDisplayed()
        assertTrue(persisted().links.isEmpty());assertEquals(1,persisted().snapshot.activities.size)
        text("Descartar y ajustar").performClick();assertTrue(persisted().links.isEmpty())
        text("Generar propuesta local").performClick();text("Aplicar cambios a la agenda").performClick()
        compose.onNodeWithText("Seguir revisando").performClick();assertTrue(persisted().links.isEmpty())
        text("Aplicar cambios a la agenda").performClick();compose.onNodeWithText("Confirmar aplicación").performClick()
        compose.waitUntil(15_000) { persisted().links.isNotEmpty() }
        val saved=persisted();assertEquals(60,saved.snapshot.activities.filter { it.kind==ActivityKind.STUDY }.sumOf { it.estimateMinutes!! })
        assertTrue(saved.links.all { it.goalId==goal.id });assertEquals(goal,saved.snapshot.activities.first { it.id==goal.id })
        assertTrue(runBlocking { academic.snapshot(DatasetScope.DEMO).activities }.isEmpty())
    }
    @Test fun invalidAvailabilityCannotReplaceSavedPreferences() {
        show();val before=persisted()
        field("Fin franja 1","17:00")
        text("Guardar disponibilidad y límites").performClick()
        text("Cada franja debe terminar el mismo día después del inicio, con precisión de minutos.").assertIsDisplayed()
        assertEquals(before,persisted())
    }
}
