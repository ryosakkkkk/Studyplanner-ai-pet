package pe.studyplanner.aipet.domain.pet

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.studyplanner.aipet.domain.profile.AvatarStyle

class PetClipsTest {
    private val moods = listOf(
        "idle", "wave", "greet", "listen", "listening", "think", "thinking", "speak", "speaking", "celebrate", "trophy",
        "happy", "tasks", "confused", "worried", "idea", "present", "alert", "rest", "reminder", "notify", "plan",
        "write", "read", "progress", "attentive", "studying", "floating", "algo-desconocido", "",
    )

    @Test
    fun cadaEstadoUsaUnClipQueExiste() {
        for (mood in moods) assertTrue("$mood -> ${PetClips.forMood(mood).clip}", PetClips.forMood(mood).clip in PetClips.ALL)
    }

    @Test
    fun hablarMueveLaBocaYLoDemasNo() {
        assertTrue(PetClips.forMood("speak").talking)
        assertTrue(PetClips.forMood("speaking").talking)
        assertEquals("talk_body", PetClips.forMood("speak").clip)
        for (mood in moods - setOf("speak", "speaking", "present")) assertFalse(mood, PetClips.forMood(mood).talking)
    }

    @Test
    fun eventosDelTutorTienenSuReaccion() {
        assertEquals("happy", PetClips.forMood("happy").clip)
        assertEquals("sad", PetClips.forMood("confused").clip)
        assertEquals("celebrate", PetClips.forMood("trophy").clip)
        assertEquals("think", PetClips.forMood("think").clip)
        assertEquals("sleep", PetClips.forMood("rest").clip)
        assertEquals("idle", PetClips.forMood("desconocido").clip)
        assertTrue(PetClips.forMood("think").loop)
        assertFalse(PetClips.forMood("celebrate").loop)
    }

    @Test
    fun lasSeisMascotasTienenModelo3D() {
        val names = AvatarStyle.entries.mapNotNull(PetClips::modelName)
        assertEquals(listOf("buho", "conejo", "gato", "panda", "pinguino", "zorro"), names.sorted())
    }

    /** Lee el bloque JSON de cada GLB empaquetado y comprueba que trae los 17 clips y la boca «MouthOpen». */
    @Test
    fun losModelosEmpaquetadosTraenTodosLosClipsYLaBoca() {
        val dir = listOf(File("src/main/assets/web/pets"), File("app/src/main/assets/web/pets")).firstOrNull { it.isDirectory }
            ?: return // fuera del módulo (p. ej. otra raíz de trabajo) no hay assets que revisar
        for (style in AvatarStyle.entries) {
            val name = PetClips.modelName(style) ?: continue
            val file = File(dir, "$name.glb")
            assertTrue("falta $file", file.isFile)
            val json = glbJson(file.readBytes())
            for (clip in PetClips.ALL) assertTrue("$name sin clip $clip", json.contains("\"name\":\"$clip\""))
            assertTrue("$name sin MouthOpen", json.contains("MouthOpen"))
        }
    }

    /** Cada mascota 3D trae su cara hecha en Blender: párpados con parpadeo y ojos feliz, triste, enojado y medio cerrados. */
    @Test
    fun cadaMascotaTraeSuCaraConCincoGestos() {
        val dir = listOf(File("src/main/assets/web/pets"), File("app/src/main/assets/web/pets")).firstOrNull { it.isDirectory } ?: return
        for (style in AvatarStyle.entries) {
            val name = PetClips.modelName(style) ?: continue
            val json = glbJson(File(dir, "${name}_cara.glb").readBytes())
            for (k in listOf("Blink", "Happy", "Sad", "Angry", "Half")) assertTrue("$name sin $k", json.contains("\"$k\""))
        }
    }

    private fun glbJson(bytes: ByteArray): String {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, buf.getInt(0)) // «glTF»
        val length = buf.getInt(12)
        assertEquals(0x4E4F534A, buf.getInt(16)) // «JSON»
        return String(bytes, 20, length, Charsets.UTF_8)
    }
}
