package pe.studyplanner.aipet.domain.pet

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.studyplanner.aipet.domain.profile.AvatarStyle

class PetOutfitsTest {
    @Test
    fun soloAceptaColoresDeLaPaleta() {
        assertEquals("#FF3B30", PetOutfits.sanitize("#ff3b30"))
        assertNull(PetOutfits.sanitize("#123456"))
        assertNull(PetOutfits.sanitize(null))
        assertNull(PetOutfits.sanitize("javascript:alert(1)"))
    }

    @Test
    fun coloresPorMascotaYJson() {
        val saved = mapOf("zorro.ropa" to "#34C759", "zorro.mochila" to "#000000", "gato.ropa" to "#FF3B30")
        val fox = PetOutfits.colorsFor(AvatarStyle.FOX, saved)
        assertEquals(mapOf("ropa" to "#34C759"), fox)
        assertEquals("{\"ropa\":\"#34C759\",\"audifonos\":null,\"mochila\":null,\"botas\":null,\"_hide\":[],\"_acc\":{}}",
            PetOutfits.toJson(AvatarStyle.FOX, fox))
        // la coneja ya es 3D: polera, mochila y zapatillas
        assertEquals(listOf("ropa", "mochila", "zapatillas"), PetOutfits.zones(AvatarStyle.RABBIT).map { it.id })
        assertEquals("conejo.ropa", PetOutfits.key(AvatarStyle.RABBIT, "ropa"))
        assertEquals("Chaleco", PetOutfits.zones(AvatarStyle.OWL).first { it.id == "ropa" }.label)
    }

    @Test
    fun piezasYAccesorios() {
        val saved = mapOf("zorro.oculta.mochila" to "1", "zorro.acc.lentes" to "on", "zorro.acc.corona" to "#FFCC00",
            "zorro.acc.sombrero" to "on", "buho.acc.lentes" to "on")
        assertEquals(setOf("mochila"), PetOutfits.hiddenFor(AvatarStyle.FOX, saved))
        assertEquals(mapOf("lentes" to null, "corona" to "#FFCC00"), PetOutfits.accessoriesFor(AvatarStyle.FOX, saved))
        // el búho ya lleva lentes: no se le ponen otros
        assertTrue(PetOutfits.accessoriesFor(AvatarStyle.OWL, saved).isEmpty())
        val json = PetOutfits.jsonFor(AvatarStyle.FOX, saved)
        assertTrue(json, json.contains("\"_hide\":[\"mochila\"]"))
        assertTrue(json, json.contains("\"_acc\":{\"corona\":\"#FFCC00\",\"lentes\":null}"))
        assertTrue(PetOutfits.PALETTE.size >= 24)
    }

    /** Las zonas de la app deben existir en el visor 3D con el mismo identificador. */
    @Test
    fun zonasCoincidenConElVisor() {
        val html = listOf(File("src/main/assets/web/pet-viewer.html"), File("app/src/main/assets/web/pet-viewer.html"))
            .firstOrNull { it.isFile }?.readText() ?: return
        val zonesJs = Regex("const ZONES = (?:/\\*ZONES\\*/)?(\\{.*?\\});", RegexOption.DOT_MATCHES_ALL).find(html)!!.groupValues[1]
        for (style in AvatarStyle.entries) {
            val model = PetClips.modelName(style) ?: continue
            val block = Regex("\"$model\":\\[(.*?)\\]\\]").find(zonesJs)!!.groupValues[1]
            for (z in PetOutfits.zones(style)) assertTrue("$model sin zona ${z.id}", block.contains("\"${z.id}\""))
        }
    }
}
