package pe.studyplanner.aipet.data.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.studyplanner.aipet.domain.tutor.BankFiles
import pe.studyplanner.aipet.domain.tutor.TutorRegistry

class TutorBankLoaderTest {

    private fun reader(name: String) = BankFiles.text(name.substringAfterLast('/'))

    @Test
    fun cargaLosDosArchivosEmpaquetadosYSumaLosTemas() {
        val loaded = TutorBankLoader.load(readAsset = ::reader)
        assertEquals(TutorBankLoader.ASSET_FILES, loaded.files)
        assertTrue(loaded.errors.toString(), loaded.errors.isEmpty())
        assertEquals(TutorRegistry.topics.size + 38, loaded.catalog.topics.size)
    }

    @Test
    fun losNombresDeAssetsSonLosDeLosArchivosReales() {
        assertEquals(BankFiles.NAMES, TutorBankLoader.ASSET_FILES.map { it.substringAfterLast('/') })
        assertTrue(TutorBankLoader.ASSET_FILES.all { it.startsWith("tutor/") })
    }

    @Test
    fun unArchivoRotoSeOmiteSoloEseArchivoYLaMatematicaSigue() {
        val loaded = TutorBankLoader.load { name -> if (name.endsWith("v1.json")) "{ esto no es json" else reader(name) }
        assertEquals(listOf("tutor/banco_preguntas_v1b.json", "tutor/banco_preguntas_v2.json"), loaded.files)
        assertEquals(1, loaded.errors.size)
        assertTrue(loaded.errors.single().startsWith("tutor/banco_preguntas_v1.json: JSON inválido"))
        assertEquals(TutorRegistry.topics.size + 7 + 23, loaded.catalog.topics.size)
    }

    @Test
    fun siTodoFallaQuedaSoloLaMatematica() {
        val loaded = TutorBankLoader.load { throw java.io.FileNotFoundException("no está") }
        assertTrue(loaded.files.isEmpty())
        assertEquals(3, loaded.errors.size)
        assertEquals(TutorRegistry.topics.map { it.id }, loaded.catalog.topics.map { it.id })
    }

    @Test
    fun cargarElMismoBancoDosVecesNoDuplicaTemas() {
        val loaded = TutorBankLoader.load(listOf("tutor/banco_preguntas_v1.json", "tutor/banco_preguntas_v1.json"), ::reader)
        assertEquals(1, loaded.files.size)
        assertEquals(1, loaded.errors.size)
    }
}
