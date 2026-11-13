package pe.studyplanner.aipet

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.studyplanner.aipet.domain.academic.DatasetScope
import pe.studyplanner.aipet.domain.profile.UserProfile

/** Production Application/Room/navigation; no sample data is inserted into PERSONAL. */
@RunWith(AndroidJUnit4::class)
class AcademicNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun productionRoutesOpenRealAgendaWithoutMutatingPersonalData() {
        val app = ApplicationProvider.getApplicationContext<StudyPlannerApplication>()
        val original = runBlocking { withTimeout(10_000) { app.profileRepository.profile.first() } }
        val before = runBlocking { app.academicRepository.snapshot(DatasetScope.PERSONAL) }
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            runBlocking { app.profileRepository.save(UserProfile(studentName = "Fase2QA", onboardingCompleted = true)) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Agenda académica").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Agenda académica").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("academic_content").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Volver a inicio").performClick()
            compose.onNodeWithContentDescription("Abrir secciones").performClick()
            compose.onNodeWithTag("section_menu_calendar").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("academic_content").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Volver a inicio").performClick()
            compose.onNodeWithContentDescription("Abrir secciones").performClick()
            compose.onNodeWithTag("section_menu_tasks").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("academic_content").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Aún no disponible").assertDoesNotExist()
            assertEquals(before, runBlocking { app.academicRepository.snapshot(DatasetScope.PERSONAL) })
        } finally {
            try { scenario?.close() } finally { runBlocking { app.profileRepository.save(original) } }
        }
    }
}
