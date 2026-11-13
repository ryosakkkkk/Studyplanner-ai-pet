package pe.studyplanner.aipet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.studyplanner.aipet.domain.profile.ProfileRepository
import pe.studyplanner.aipet.domain.profile.ProfileValidator
import pe.studyplanner.aipet.domain.profile.UserProfile

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: ViewModelStore

    @Before
    fun prepareMainDispatcher() {
        Dispatchers.setMain(dispatcher)
        store = ViewModelStore()
    }

    @After
    fun clearViewModelsAndMainDispatcher() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `un fallo de lectura es visible y reintentar recupera el perfil existente`() = runTest {
        val expected = UserProfile(petName = "Inti", onboardingCompleted = true)
        val repository = ControlledRepository(expected).apply {
            readFailure = IOException("Lectura temporalmente no disponible")
        }
        val model = createModel(repository)
        runCurrent()

        assertEquals(ProfileState.ReadError, model.state.value)
        assertEquals(1, repository.readCalls)
        repository.readFailure = null
        model.retryRead()
        runCurrent()

        assertEquals(ProfileState.Ready(expected), model.state.value)
        assertEquals(2, repository.readCalls)
        assertEquals(0, repository.saveCalls)
    }

    @Test
    fun `un fallo de escritura conserva el perfil y nunca anuncia guardado`() = runTest {
        val original = UserProfile(petName = "Inti", onboardingCompleted = true)
        val repository = ControlledRepository(original).apply {
            writeFailure = IOException("Almacenamiento sin espacio")
        }
        val model = createModel(repository)
        val observed = mutableListOf<SaveState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.saveState.toList(observed)
        }
        runCurrent()

        model.save(original.copy(petName = "Luna"))
        runCurrent()

        assertTrue(model.saveState.value is SaveState.Failed)
        assertTrue(observed.any { it is SaveState.Saving })
        assertFalse(observed.any { it is SaveState.Saved })
        assertEquals(original, repository.persisted)
        assertEquals(ProfileState.Ready(original), model.state.value)
        assertEquals(1, repository.saveCalls)
    }

    @Test
    fun `pulsar guardar repetidamente durante escritura produce una sola operacion`() = runTest {
        val original = UserProfile(onboardingCompleted = true)
        val pendingWrite = CompletableDeferred<Unit>()
        val repository = ControlledRepository(original).apply { writeGate = pendingWrite }
        val model = createModel(repository)
        runCurrent()
        val expected = original.copy(petName = "Inti")

        model.save(expected)
        model.save(original.copy(petName = "Luna"))
        runCurrent()
        model.save(original.copy(petName = "Sol"))
        runCurrent()

        assertEquals(SaveState.Saving, model.saveState.value)
        assertEquals(1, repository.saveCalls)
        assertEquals(original, repository.persisted)

        pendingWrite.complete(Unit)
        runCurrent()

        assertEquals(1, repository.saveCalls)
        assertEquals(expected, repository.persisted)
        assertEquals(ProfileState.Ready(expected), model.state.value)
        assertEquals(SaveState.Saved, model.saveState.value)
    }

    private fun createModel(repository: ProfileRepository): ProfileViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(ProfileViewModel::class.java))
                return ProfileViewModel(repository) as T
            }
        }
        return ViewModelProvider(store, factory)[ProfileViewModel::class.java]
    }

    private class ControlledRepository(initial: UserProfile) : ProfileRepository {
        private val values = MutableStateFlow(initial)
        var readFailure: IOException? = null
        var writeFailure: IOException? = null
        var writeGate: CompletableDeferred<Unit>? = null
        var readCalls = 0
            private set
        var saveCalls = 0
            private set
        val persisted: UserProfile get() = values.value

        override val profile: Flow<UserProfile> = flow {
            readCalls++
            readFailure?.let { throw it }
            emitAll(values)
        }

        override suspend fun save(profile: UserProfile) {
            saveCalls++
            writeGate?.await()
            writeFailure?.let { throw it }
            values.value = ProfileValidator.normalize(profile)
        }
    }
}
