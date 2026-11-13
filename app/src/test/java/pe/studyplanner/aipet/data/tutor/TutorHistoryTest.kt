package pe.studyplanner.aipet.data.tutor

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.studyplanner.aipet.domain.tutor.Attempt
import pe.studyplanner.aipet.domain.tutor.AttemptMode
import pe.studyplanner.aipet.domain.tutor.MasteryStatus
import pe.studyplanner.aipet.domain.tutor.TutorRegistry

class TutorHistoryTest {
    private lateinit var dir: File
    private lateinit var store: TutorAttemptStore
    private lateinit var scope: CoroutineScope

    @Before fun setUp() {
        dir = Files.createTempDirectory("tutor-history").toFile()
        store = TutorAttemptStore(File(dir, "tutor/intentos.csv"))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun attempt(n: Int, mode: AttemptMode, correct: Boolean): Attempt {
        val exercise = TutorRegistry.generate("ecuaciones-lineales", 200L + n, 1)
        return Attempt(1_761_600_000_000L + n * 1000L, exercise.topicId, exercise.id, mode, 0, correct, 0, 900L)
    }

    private fun history() = TutorHistory(store, scope, Dispatchers.IO)

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Tiempo agotado esperando: $what" }
            Thread.sleep(10)
        }
    }

    @Test fun startsEmptyAndNotLoaded() {
        val history = history()
        assertFalse(history.state.value.loaded)
        assertTrue(history.state.value.attempts.isEmpty())
        assertEquals(MasteryStatus.NO_INICIADO, history.state.value.masteryOf("ecuaciones-lineales"))
    }

    @Test fun startLoadsWhatWasSavedBefore() {
        store.append(listOf(attempt(1, AttemptMode.UNAIDED, true), attempt(2, AttemptMode.UNAIDED, true)))
        val history = history()
        history.start()
        waitUntil("carga") { history.state.value.loaded }
        assertEquals(2, history.state.value.attempts.size)
        assertEquals(MasteryStatus.DOMINIO_SIN_AYUDA, history.state.value.masteryOf("ecuaciones-lineales"))
        assertTrue(history.state.value.error.isEmpty())
    }

    @Test fun appendedAttemptsShowUpAtOnceAndReachTheFileInOrder() {
        val history = history()
        history.start()
        waitUntil("carga") { history.state.value.loaded }
        history.append(listOf(attempt(1, AttemptMode.GUIDED, false)))
        history.append(listOf(attempt(2, AttemptMode.GUIDED, true), attempt(3, AttemptMode.UNAIDED, true)))
        assertEquals(3, history.state.value.attempts.size)
        waitUntil("escritura") { store.readAll().attempts.size == 3 }
        assertEquals(history.state.value.attempts, store.readAll().attempts)
    }

    @Test fun startingTwiceDoesNotDuplicateAnything() {
        store.append(listOf(attempt(1, AttemptMode.GUIDED, true)))
        val history = history()
        history.start()
        history.start()
        waitUntil("carga") { history.state.value.loaded }
        history.append(listOf(attempt(2, AttemptMode.GUIDED, true)))
        waitUntil("escritura") { store.readAll().attempts.size == 2 }
        assertEquals(2, history.state.value.attempts.size)
    }

    @Test fun attemptsAppendedBeforeTheFileIsReadAreKept() {
        store.append(listOf(attempt(1, AttemptMode.GUIDED, true)))
        val history = history()
        history.append(listOf(attempt(2, AttemptMode.GUIDED, false)))
        history.start()
        waitUntil("carga") { history.state.value.loaded }
        waitUntil("escritura") { store.readAll().attempts.size == 2 }
        assertEquals(2, history.state.value.attempts.size)
    }

    @Test fun exportReturnsTheHeaderAndRows() = runBlocking {
        val history = history()
        history.start()
        waitUntil("carga") { history.state.value.loaded }
        history.append(listOf(attempt(1, AttemptMode.GUIDED, true)))
        waitUntil("escritura") { store.readAll().attempts.size == 1 }
        val csv = history.exportCsv()
        assertEquals(2, csv.lines().count { it.isNotBlank() })
    }
}
