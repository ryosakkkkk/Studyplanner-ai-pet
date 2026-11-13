package pe.studyplanner.aipet

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pe.studyplanner.aipet.platform.overlay.OverlayStatus
import pe.studyplanner.aipet.ui.overlay.OverlayControls
import pe.studyplanner.aipet.ui.theme.StudyPlannerTheme
import pe.studyplanner.aipet.domain.profile.ThemeMode

/** UI contract only. Callbacks are fixtures; real WindowManager/FGS are checked separately. */
class F8OverlayControlsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun permissionReturnNeverStartsTheSession() {
        val allowed = mutableStateOf(false); val starts = AtomicInteger()
        compose.setContent { StudyPlannerTheme(ThemeMode.LIGHT) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            OverlayControls(OverlayStatus(), allowed.value, true, true, {}, {}, {starts.incrementAndGet()}, {})
        } } }
        compose.onNodeWithTag("overlay_show").assertIsNotEnabled()
        compose.runOnIdle { allowed.value=true }
        compose.onNodeWithTag("overlay_show").assertIsEnabled()
        compose.runOnIdle { assertEquals(0, starts.get()) }
    }
    @Test fun hiddenPetAndDisabledNotificationsKeepStartBlocked() {
        val pet = mutableStateOf(false); val notifications = mutableStateOf(true)
        compose.setContent { StudyPlannerTheme(ThemeMode.LIGHT) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            OverlayControls(OverlayStatus(), true, notifications.value, pet.value, {}, {}, {}, {})
        } } }
        compose.onNodeWithTag("overlay_show").assertIsNotEnabled()
        compose.runOnIdle { pet.value=true; notifications.value=false }
        compose.onNodeWithTag("overlay_show").assertIsNotEnabled()
        compose.onNodeWithTag("overlay_notifications").assertExists()
    }
    @Test fun stopIsAvailableDuringStartupAndWhileRunning() {
        val state = mutableStateOf(OverlayStatus(starting=true)); val stops = AtomicInteger()
        compose.setContent { StudyPlannerTheme(ThemeMode.LIGHT) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            OverlayControls(state.value, true, true, true, {}, {}, {}, {stops.incrementAndGet()})
        } } }
        compose.onNodeWithTag("overlay_stop").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { state.value=OverlayStatus(active=true) }
        compose.onNodeWithTag("overlay_stop").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, stops.get()) }
    }
    @Test fun activeSessionDisablesDuplicateStart() {
        compose.setContent { StudyPlannerTheme(ThemeMode.LIGHT) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            OverlayControls(OverlayStatus(active=true), true, true, true, {}, {}, {}, {})
        } } }
        compose.onNodeWithTag("overlay_show").assertIsNotEnabled()
        compose.onNodeWithTag("overlay_stop").assertIsEnabled()
    }
}
