package pe.studyplanner.aipet.domain.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenOptionTest {

    @Test fun marksTheOptionAfterAMarkerWord() {
        assertEquals(2, SpokenOption.parse("opción c"))
        assertEquals(3, SpokenOption.parse("alternativa de"))
        assertEquals(4, SpokenOption.parse("letra e"))
        assertEquals(0, SpokenOption.parse("respuesta a"))
        assertEquals(1, SpokenOption.parse("Opción B, por favor"))
    }

    @Test fun ignoresAccentsAndCase() {
        assertEquals(3, SpokenOption.parse("OPCIÓN D"))
        assertEquals(1, SpokenOption.parse("opcion be"))
    }

    @Test fun skipsFillerWordsBeforeTheLetter() {
        assertEquals(2, SpokenOption.parse("creo que es la c"))
        assertEquals(1, SpokenOption.parse("la opción es la b"))
        assertEquals(0, SpokenOption.parse("marco la a"))
    }

    @Test fun acceptsASingleLetterOrOrdinal() {
        assertEquals(0, SpokenOption.parse("a"))
        assertEquals(2, SpokenOption.parse("tercera"))
        assertEquals(4, SpokenOption.parse("quinta"))
    }

    @Test fun refusesToGuessWhenItIsNotASingleClearOption() {
        assertNull(SpokenOption.parse(""))
        assertNull(SpokenOption.parse("   "))
        assertNull(SpokenOption.parse("opción"))
        assertNull(SpokenOption.parse("opción x"))
        assertNull(SpokenOption.parse("b c"))
        assertNull(SpokenOption.parse("no sé"))
        assertNull(SpokenOption.parse("la respuesta es veinticuatro"))
    }
}
