package pe.studyplanner.aipet

import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.studyplanner.aipet.domain.profile.AvatarStyle
import pe.studyplanner.aipet.domain.profile.ThemeMode
import pe.studyplanner.aipet.domain.profile.UserProfile

/** Integra Application, el perfil persistente real y MainActivity. No simula muerte del proceso. */
@RunWith(AndroidJUnit4::class)
class MainActivityPersistenceTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun completeProfileSurvivesActivityCloseRelaunchAndRecreation() {
        val application = ApplicationProvider.getApplicationContext<StudyPlannerApplication>()
        assertTrue(
            "Esta prueba solo debe ejecutarse en el APK debug de prueba.",
            application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply {
                putString(
                    "stream",
                    "\nMainActivityPersistenceTest: API ${Build.VERSION.SDK_INT}, Android ${Build.VERSION.RELEASE}, ${Build.MODEL}\n",
                )
            },
        )

        val repository = application.profileRepository
        val original = runBlocking { withTimeout(10_000) { repository.profile.first() } }
        val expected = UserProfile(
            studentName = "María",
            petName = "Lucía",
            avatar = AvatarStyle.OWL,
            zoneId = "Europe/Madrid",
            weekStartsOn = DayOfWeek.SUNDAY,
            theme = ThemeMode.DARK,
            onboardingCompleted = true,
            petVisible = false,
        )
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            runBlocking { withTimeout(10_000) { repository.save(expected) } }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            assertHiddenPetHome()
            assertEquals(expected, runBlocking { withTimeout(10_000) { repository.profile.first() } })

            scenario.close()
            scenario = null
            scenario = ActivityScenario.launch(MainActivity::class.java)
            assertHiddenPetHome()
            assertEquals(expected, runBlocking { withTimeout(10_000) { repository.profile.first() } })
            compose.onNodeWithContentDescription("Abrir ajustes").performClick()
            assertCompleteProfileEditor()

            scenario.recreate()
            assertCompleteProfileEditor()
            assertEquals(expected, runBlocking { withTimeout(10_000) { repository.profile.first() } })
        } finally {
            // Se restaura el perfil previo incluso si falla una aserción o el cierre de la Activity.
            try {
                scenario?.close()
            } finally {
                runBlocking { withTimeout(10_000) { repository.save(original) } }
            }
        }
    }

    private fun assertHiddenPetHome() {
        waitFor("Mi espacio")
        compose.onNodeWithText("Hola, María.").assertIsDisplayed()
        compose.onNodeWithText("Mostrar mascota").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("home_pet").assertDoesNotExist()
        compose.onNodeWithText("Empezar").assertDoesNotExist()
    }

    private fun assertCompleteProfileEditor() {
        waitFor("Tu perfil")
        compose.onNodeWithText("Nombre de tu mascota").performScrollTo().assertTextContains("Lucía")
        compose.onNodeWithText("Búho").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Tu nombre (opcional)").performScrollTo().assertTextContains("María")
        compose.onNodeWithText("Zona horaria").performScrollTo().assertTextContains("Europe/Madrid")
        compose.onNodeWithText("domingo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Oscura").performScrollTo().assertIsSelected()
    }

    private fun waitFor(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
