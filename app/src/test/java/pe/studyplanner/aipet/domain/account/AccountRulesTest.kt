package pe.studyplanner.aipet.domain.account

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AccountRulesTest {
    @Test
    fun correoNombreYContrasena() {
        assertEquals("ana@ejemplo.pe", AccountRules.normalizeEmail("  Ana@Ejemplo.PE "))
        assertNull(AccountRules.emailProblem("ana@ejemplo.pe"))
        assertNotNull(AccountRules.emailProblem("ana@ejemplo"))
        assertNotNull(AccountRules.emailProblem("ana ejemplo.pe"))
        assertNotNull(AccountRules.passwordProblem("corta"))
        assertNull(AccountRules.passwordProblem("estudio-2026"))
        assertNotNull(AccountRules.nameProblem("   "))
        assertNull(AccountRules.nameProblem("Ana"))
    }

    @Test
    fun laClaveDerivadaCoincideConLaDelServidor() {
        // Mismo valor que calcula Node con pbkdf2Sync en las pruebas del servidor (test/cuentas.test.mjs).
        assertEquals("Eg0pGqzex78a3S8BiqvUCZ3Mf3Al-dB1Z5cCxXuha8M", AccountRules.deriveKey("Ana@Ejemplo.pe ", "estudio-2026"))
        // La contraseña no aparece en lo que se envía.
        assertTrue(!AccountRules.deriveKey("ana@ejemplo.pe", "estudio-2026").contains("estudio"))
    }

    @Test
    fun copiaIdaYVueltaConLosMismosBytes() {
        val files = mapOf(
            "databases/academic_v1.db" to ByteArray(5000) { (it % 251).toByte() },
            "files/tutor/intentos.csv" to "tema,ok\nfracciones,1\n".toByteArray(),
            "shared_prefs/pet_style_v1.xml" to "<map/>".toByteArray(),
        )
        val zip = BackupArchive.pack(files)
        // Los mismos datos dan el mismo .zip (así se sabe si hay que subir otra copia).
        assertEquals(BackupArchive.sha256Hex(zip), BackupArchive.sha256Hex(BackupArchive.pack(files)))
        val back = BackupArchive.unpack(zip)
        assertEquals(files.keys, back.keys)
        files.forEach { (k, v) -> assertArrayEquals(v, back[k]) }
    }

    @Test
    fun laCopiaSoloRestauraArchivosPermitidos() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            listOf("../../shared_prefs/ai_connection_v1.xml", "shared_prefs/cuenta_v1.xml", "files/tutor/intentos.csv").forEach {
                zip.putNextEntry(ZipEntry(it)); zip.write("x".toByteArray()); zip.closeEntry()
            }
        }
        assertEquals(setOf("files/tutor/intentos.csv"), BackupArchive.unpack(out.toByteArray()).keys)
        // Ni la sesión ni la conexión de IA viajan en la copia.
        assertTrue(BackupArchive.FILES.none { it.contains("cuenta") || it.contains("ai_connection") })
        try { BackupArchive.unpack(BackupArchive.pack(emptyMap())); fail("Una copia vacía no se aplica") } catch (_: IllegalArgumentException) {}
    }
}
