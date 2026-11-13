package pe.studyplanner.aipet

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pe.studyplanner.aipet.data.profile.DataStoreProfileRepository
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.ui.ProfileViewModel
import pe.studyplanner.aipet.ui.StudyPlannerApp

class ProfileFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var file: File
    private lateinit var dataScope: CoroutineScope
    private lateinit var repository: DataStoreProfileRepository
    private val viewModels = ViewModelStore()

    @Before fun prepare() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        file = File.createTempFile("profile-test-", ".preferences_pb", context.cacheDir)
        check(file.delete())
        dataScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = DataStoreProfileRepository(
            PreferenceDataStoreFactory.create(scope = dataScope, produceFile = { file })
        )
    }

    private fun show(initial: UserProfile? = null) {
        if (initial != null) runBlocking { repository.save(initial) }
        val model = ViewModelProvider(viewModels, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ProfileViewModel(repository) as T
        })[ProfileViewModel::class.java]
        compose.setContent { StudyPlannerApp(model) }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun cleanup() {
        viewModels.clear()
        runBlocking { dataScope.coroutineContext[Job]?.cancelAndJoin() }
        file.delete()
    }

    @Test fun onboardingPersistsReviewedNameAndCanHidePet() {
        show()
        waitFor("Empezar")
        compose.onNodeWithText("Empezar").performScrollTo().performClick()
        compose.onNodeWithText("Nombre de tu mascota").performTextReplacement("Luci\u0301a")
        compose.onNodeWithText("Crear mi espacio").performScrollTo().performClick()
        waitFor("Mi espacio")
        compose.onNodeWithText("Lucía").assertIsDisplayed()
        val saved = runBlocking { repository.profile.first() }
        assertEquals("Lucía", saved.petName)
        assertEquals(true, saved.onboardingCompleted)
        compose.onNodeWithText("Ocultar mascota").performScrollTo().performClick()
        waitFor("Mostrar mascota")
        compose.onNodeWithTag("home_pet").assertDoesNotExist()
        assertEquals(false, runBlocking { repository.profile.first { !it.petVisible } }.petVisible)
    }

    @Test fun blankPetNameDoesNotCompleteOnboarding() {
        show()
        waitFor("Empezar")
        compose.onNodeWithText("Empezar").performScrollTo().performClick()
        compose.onNodeWithText("Nombre de tu mascota").performTextReplacement("   ")
        compose.onNodeWithText("Crear mi espacio").performScrollTo().performClick()
        compose.onNodeWithText("Nombre de tu mascota").performScrollTo()
        compose.onNodeWithText("Escribe un nombre de mascota de 1 a 24 caracteres visibles.").assertExists()
        assertEquals(false, runBlocking { repository.profile.first() }.onboardingCompleted)
    }

    @Test fun backRequiresDiscardAndDoesNotSaveDraft() {
        show(UserProfile(onboardingCompleted = true))
        waitFor("Mi espacio")
        compose.onNodeWithContentDescription("Abrir ajustes").performClick()
        compose.onNodeWithText("Nombre de tu mascota").performTextReplacement("Borrador")
        // Atrás con el IME abierto lo cierra; esta aserción comprueba salir del editor.
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        compose.onNodeWithText("¿Descartar tus cambios?").assertIsDisplayed()
        compose.onNodeWithText("Seguir editando").performClick()
        compose.onNodeWithText("Nombre de tu mascota").assertTextContains("Borrador")
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        compose.onNodeWithText("Descartar").performClick()
        waitFor("Mi espacio")
        compose.onNodeWithText("Kiro").assertIsDisplayed()
        assertEquals("Kiro", runBlocking { repository.profile.first() }.petName)
    }

    @Test fun sectionNavigationShowsHonestStatesAndReturnsWithoutDuplicates() {
        show(UserProfile(onboardingCompleted = true))
        waitFor("Mi espacio")
        compose.onNodeWithContentDescription("Abrir secciones").performClick()
        compose.onNodeWithTag("section_menu_calendar").performClick()
        compose.onNodeWithTag("section_screen_calendar").assertIsDisplayed()
        compose.onNodeWithText("Aún no disponible").assertIsDisplayed()

        compose.onNodeWithContentDescription("Abrir secciones").performClick()
        compose.onNodeWithTag("section_menu_calendar").assertIsSelected().performClick()
        compose.onNodeWithTag("section_screen_calendar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Abrir secciones").performClick()
        compose.onNodeWithTag("section_menu_settings").performClick()
        compose.onNodeWithText("Nombre de tu mascota").assertExists()
        Espresso.pressBack()
        compose.onNodeWithTag("section_screen_calendar").assertIsDisplayed()
        Espresso.pressBack()
        waitFor("Mi espacio")

        listOf("tasks", "chat").forEach { route ->
            compose.onNodeWithContentDescription("Abrir secciones").performClick()
            compose.onNodeWithTag("section_menu_$route").performClick()
            compose.onNodeWithTag("section_screen_$route").assertIsDisplayed()
            compose.onNodeWithText("Aún no disponible").assertIsDisplayed()
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        }
        Espresso.pressBack()
        waitFor("Mi espacio")
        assertEquals(UserProfile(onboardingCompleted = true), runBlocking { repository.profile.first() })
    }
}
