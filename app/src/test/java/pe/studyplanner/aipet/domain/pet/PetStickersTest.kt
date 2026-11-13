package pe.studyplanner.aipet.domain.pet

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.studyplanner.aipet.domain.profile.AvatarStyle

class PetStickersTest {
    private fun assets(): File? = listOf(File("src/main/assets"), File("app/src/main/assets")).firstOrNull { it.isDirectory }

    @Test
    fun clavesUnicasYCuarentaYSiete() {
        val keys = PetStickers.ALL.map { it.key }
        assertEquals(47, keys.size)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun cadaMascotaTieneTodasSusLaminas() {
        val root = assets() ?: return
        for (style in AvatarStyle.entries) for (s in PetStickers.ALL) {
            val f = File(root, PetStickers.path(style, s.key))
            assertTrue("falta $f", f.isFile && f.length() > 500)
        }
    }

    @Test
    fun reaccionesUsanLaminasExistentes() {
        val keys = PetStickers.ALL.map { it.key }.toSet()
        for (mood in listOf("happy", "celebrate", "trophy", "confused", "idea", "think", "wave", "reminder", "notify", "plan", "rest", "studying", "progress")) {
            val k = PetStickers.forMood(mood)
            assertTrue("$mood -> $k", k != null && k in keys)
        }
        assertNull(PetStickers.forMood("idle"))
        assertNull(PetStickers.forMood("speak"))
        assertNull(PetStickers.forMood("listen"))
    }

    @Test
    fun cadaLaminaMueveLaMascota3D() {
        for (s in PetStickers.ALL) assertTrue(s.key, PetClips.forMood(PetStickers.moodFor(s.key)).clip in PetClips.ALL)
        assertEquals("angry", PetClips.forMood(PetStickers.moodFor("enojado")).clip)
        assertEquals("celebrate", PetClips.forMood(PetStickers.moodFor("trofeo")).clip)
    }
}
