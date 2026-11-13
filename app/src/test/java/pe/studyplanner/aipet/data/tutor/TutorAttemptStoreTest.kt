package pe.studyplanner.aipet.data.tutor

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.studyplanner.aipet.domain.tutor.Attempt
import pe.studyplanner.aipet.domain.tutor.AttemptLog
import pe.studyplanner.aipet.domain.tutor.AttemptMode
import pe.studyplanner.aipet.domain.tutor.TutorRegistry

class TutorAttemptStoreTest {
    private lateinit var dir: File
    private lateinit var file: File
    private lateinit var store: TutorAttemptStore

    @Before fun setUp() {
        dir = Files.createTempDirectory("tutor-store").toFile()
        file = File(dir, "tutor/intentos.csv")
        store = TutorAttemptStore(file)
    }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    private fun attempt(n: Int, correct: Boolean = n % 2 == 0, mode: AttemptMode = AttemptMode.GUIDED): Attempt {
        val exercise = TutorRegistry.generate("ecuaciones-lineales", 100L + n, 1)
        return Attempt(1_761_600_000_000L + n * 1000L, exercise.topicId, exercise.id, mode, n % 5, correct, n % 4, 1500L + n)
    }

    @Test fun aMissingFileIsAnEmptyHistory() {
        val result = store.readAll()
        assertTrue(result.attempts.isEmpty())
        assertTrue(result.errors.isEmpty())
        assertFalse(file.exists())
    }

    @Test fun appendCreatesTheFolderAndTheHeader() {
        store.append(listOf(attempt(1), attempt(2)))
        val lines = file.readText().lines().filter { it.isNotBlank() }
        assertEquals(AttemptLog.HEADER, lines.first())
        assertEquals(3, lines.size)
        assertEquals(listOf(attempt(1), attempt(2)), store.readAll().attempts)
    }

    @Test fun laterAppendsKeepASingleHeader() {
        store.append(listOf(attempt(1)))
        store.append(listOf(attempt(2), attempt(3, mode = AttemptMode.UNAIDED)))
        val text = file.readText()
        assertEquals(1, text.lines().count { it == AttemptLog.HEADER })
        assertEquals(listOf(attempt(1), attempt(2), attempt(3, mode = AttemptMode.UNAIDED)), store.readAll().attempts)
    }

    @Test fun appendRepairsAFileThatEndsWithoutNewline() {
        store.append(listOf(attempt(1)))
        file.writeText(file.readText().trimEnd('\n'))
        store.append(listOf(attempt(2)))
        assertEquals(listOf(attempt(1), attempt(2)), store.readAll().attempts)
        assertTrue(store.readAll().errors.isEmpty())
    }

    @Test fun appendingNothingDoesNotCreateAFile() {
        store.append(emptyList())
        assertFalse(file.exists())
    }

    @Test fun exportKeepsTheHeaderAndTheLastRows() {
        store.append((1..5).map { attempt(it) })
        val csv = store.exportCsv(maxRows = 2)
        val lines = csv.lines().filter { it.isNotBlank() }
        assertEquals(3, lines.size)
        assertEquals(AttemptLog.HEADER, lines.first())
        assertEquals(listOf(attempt(4), attempt(5)), AttemptLog.decode(csv).attempts)
    }

    @Test fun damagedRowsAreReportedButDoNotStopTheRead() {
        store.append(listOf(attempt(1)))
        file.appendText("esto,no,es,una,fila\n")
        store.append(listOf(attempt(2)))
        val result = store.readAll()
        assertEquals(listOf(attempt(1), attempt(2)), result.attempts)
        assertEquals(1, result.errors.size)
    }

    @Test fun theExportContainsNoPersonalFields() {
        store.append(listOf(attempt(1)))
        val header = store.exportCsv().lines().first()
        assertEquals("fecha_iso,tema,modo,ejercicio,opcion,correcta,pistas,tiempo_ms", header)
    }

    @Test fun clearRemovesTheFile() {
        store.append(listOf(attempt(1)))
        assertTrue(store.clear())
        assertFalse(file.exists())
        assertTrue(store.clear())
    }
}
