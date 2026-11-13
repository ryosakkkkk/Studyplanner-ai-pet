package pe.studyplanner.aipet.ui.academic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import java.io.IOException
import java.time.LocalDate
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import pe.studyplanner.aipet.domain.academic.*

@OptIn(ExperimentalCoroutinesApi::class)
class AcademicViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: ViewModelStore

    @Before fun before() { Dispatchers.setMain(dispatcher); store = ViewModelStore() }
    @After fun after() { store.clear(); Dispatchers.resetMain() }

    @Test fun `guardar no anuncia exito antes del commit y bloquea pulsaciones repetidas`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = ControlledAcademicRepository().apply { writeGate = gate }
        val model = createModel(repository)
        runCurrent()
        model.saveCourse(Course(name = "Matemática de prueba"))
        model.saveCourse(Course(name = "Segundo toque"))
        runCurrent()
        assertEquals(AcademicMutation.Saving, model.mutation.value)
        assertEquals(1, repository.writes.size)
        assertTrue(repository.snapshot(DatasetScope.PERSONAL).courses.isEmpty())
        model.changeScope(DatasetScope.DEMO)
        assertEquals(DatasetScope.PERSONAL, model.scope.value)
        gate.complete(Unit)
        runCurrent()
        assertTrue(model.mutation.value is AcademicMutation.Saved)
        assertEquals("Matemática de prueba", repository.snapshot(DatasetScope.PERSONAL).courses.single().name)
    }

    @Test fun `un error de disco conserva datos y nunca emite guardado`() = runTest {
        val repository = ControlledAcademicRepository().apply { failure = IOException("Fallo de disco controlado") }
        val model = createModel(repository)
        val observed = mutableListOf<AcademicMutation>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.mutation.toList(observed) }
        runCurrent()
        model.saveCourse(Course(name = "No persistido"))
        runCurrent()
        assertTrue(model.mutation.value is AcademicMutation.Failed)
        assertFalse(observed.any { it is AcademicMutation.Saved })
        assertTrue(repository.snapshot(DatasetScope.PERSONAL).courses.isEmpty())
        assertTrue(repository.snapshot(DatasetScope.DEMO).courses.isEmpty())
    }

    @Test fun `cambiar a demo no puede escribir usando el snapshot personal anterior`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = ControlledAcademicRepository().apply { demoReadGate = gate }
        val model = createModel(repository)
        runCurrent()
        assertEquals(DatasetScope.PERSONAL, (model.state.value as AcademicLoadState.Ready).snapshot.scope)
        model.changeScope(DatasetScope.DEMO)
        model.saveCourse(Course(name = "No guardar antes de leer"))
        runCurrent()
        assertEquals(AcademicLoadState.Loading, model.state.value)
        model.saveCourse(Course(name = "No guardar mientras carga"))
        runCurrent()
        assertTrue(repository.writes.isEmpty())
        gate.complete(Unit)
        runCurrent()
        model.saveCourse(Course(name = "Curso ficticio"))
        runCurrent()
        assertEquals(listOf(DatasetScope.DEMO), repository.writes)
        assertTrue(repository.snapshot(DatasetScope.PERSONAL).courses.isEmpty())
        assertEquals("Curso ficticio", repository.snapshot(DatasetScope.DEMO).courses.single().name)
    }

    private fun createModel(repository: AcademicRepository): AcademicViewModel = ViewModelProvider(store,
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AcademicViewModel(repository) as T
        })[AcademicViewModel::class.java]

    /** Explicit test double for controllable I/O timing; no Android data or production DB. */
    private class ControlledAcademicRepository : AcademicRepository {
        private val values = DatasetScope.entries.associateWith { MutableStateFlow(AcademicSnapshot(it)) }
        val writes = mutableListOf<DatasetScope>()
        var writeGate: CompletableDeferred<Unit>? = null
        var demoReadGate: CompletableDeferred<Unit>? = null
        var failure: Exception? = null
        override fun observe(scope: DatasetScope): Flow<AcademicSnapshot> = flow {
            if (scope == DatasetScope.DEMO) demoReadGate?.await()
            emitAll(values.getValue(scope))
        }
        override suspend fun snapshot(scope: DatasetScope) = values.getValue(scope).value
        override suspend fun saveCourse(scope: DatasetScope, course: Course): Course {
            writes += scope
            writeGate?.await()
            failure?.let { throw it }
            val saved = course.copy(version = course.version + 1)
            values.getValue(scope).value = snapshot(scope).copy(courses = snapshot(scope).courses + saved)
            return saved
        }
        override suspend fun archiveCourse(scope: DatasetScope, id: String, expectedVersion: Long): Course = error("No usado por este ensayo")
        override suspend fun deleteCourse(scope: DatasetScope, id: String, expectedVersion: Long) = error("No usado por este ensayo")
        override suspend fun saveActivity(scope: DatasetScope, activity: AcademicActivity): AcademicActivity = error("No usado por este ensayo")
        override suspend fun deleteActivity(scope: DatasetScope, id: String, expectedVersion: Long) = error("No usado por este ensayo")
        override suspend fun putException(scope: DatasetScope, exception: RecurrenceException, expectedActivityVersion: Long) = error("No usado por este ensayo")
        override suspend fun removeException(scope: DatasetScope, activityId: String, occurrenceDate: LocalDate, expectedActivityVersion: Long) = error("No usado por este ensayo")
        override suspend fun seedDemo(referenceDate: LocalDate) = error("No usado por este ensayo")
        override suspend fun resetDemo(referenceDate: LocalDate) = error("No usado por este ensayo")
    }
}
