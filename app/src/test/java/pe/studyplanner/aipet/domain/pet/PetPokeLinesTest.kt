package pe.studyplanner.aipet.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetPokeLinesTest {
    @Test
    fun cadaParteTieneSuFrase() {
        assertEquals("¡Jaja, cosquillas!", PetPokeLines.forPart("cabeza") { 0 })
        assertEquals("¡Hola!", PetPokeLines.forPart("mano") { 0 })
        assertEquals("¡Salto!", PetPokeLines.forPart("pies") { 0 })
        assertEquals("¡A bailar!", PetPokeLines.forPart("baile") { 0 })
        // parte desconocida o índice fuera de rango: nunca falla
        assertTrue(PetPokeLines.forPart("cuerpo") { 99 }.isNotBlank())
    }
}
