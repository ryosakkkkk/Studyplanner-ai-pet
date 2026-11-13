package pe.studyplanner.aipet.ui.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkRepliesTest {
    @Test fun spokenYesAndNoAreRecognizedWithAccentsAndPunctuation() {
        listOf("Sí", "sí.", "Si, guárdalo", "Claro", "dale", "OK", "De acuerdo").forEach { assertTrue(it, TalkReplies.isYes(it)) }
        listOf("No", "no gracias", "Cancela", "Mejor no", "ahora no").forEach { assertTrue(it, TalkReplies.isNo(it)) }
    }

    @Test fun ambiguousAnswersAreNeitherYesNorNo() {
        listOf("sí pero mañana", "no sé", "tal vez", "crea otra tarea", "").forEach {
            assertFalse(it, TalkReplies.isYes(it))
            assertFalse(it, TalkReplies.isNo(it))
        }
    }

    @Test fun speechTextDropsMarkupAndAiPrefix() {
        assertEquals("La fotosíntesis produce glucosa.", TalkReplies.forSpeech("IA: La **fotosíntesis** produce glucosa."))
        assertEquals("La fotosíntesis produce glucosa.", TalkReplies.friendly("IA: La fotosíntesis produce glucosa."))
        // Así llega del servicio: la mascota dice solo la respuesta.
        assertEquals("Una ecuación de primer grado tiene una variable.", TalkReplies.friendly("No se ejecutó ninguna acción. IA: Una ecuación de primer grado tiene una variable."))
        assertEquals("Una ecuación de primer grado tiene una variable.", TalkReplies.forSpeech("No se ejecutó ninguna acción. IA: Una ecuación de primer grado tiene una variable."))
        assertTrue(TalkReplies.isAi("No se ejecutó ninguna acción. IA: Hola"))
        assertTrue(TalkReplies.friendly("Modo DEMO_SIN_IA: uso reglas locales").contains("¿qué tengo hoy?"))
    }
}
