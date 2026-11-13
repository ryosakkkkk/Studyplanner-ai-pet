package pe.studyplanner.aipet.domain.knowledge

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.studyplanner.aipet.data.ai.OfflineAnswers
import pe.studyplanner.aipet.domain.tutor.TutorRegistry

/** Lo que sabe la mascota sin Internet: se prueba con la base real que va en el APK. */
class StudyKnowledgeTest {

    private val baseFile = listOf(File("src/main/assets/conocimiento/base.json"), File("app/src/main/assets/conocimiento/base.json"))
        .first { it.exists() }
    private val parsed = StudyKnowledge.parse(baseFile.readText(Charsets.UTF_8))
    private val knowledge = StudyKnowledge(parsed.first + StudyKnowledge.fromTopics(TutorRegistry.topics, parsed.first.mapTo(HashSet()) { it.topicId }), parsed.second)

    private fun bestTitle(q: String): String? = knowledge.best(q)?.takeIf { it.strong }?.entry?.title

    @Test fun laBaseTraeDefinicionesYPreguntasDeTodosLosCursosDelExamen() {
        val (entries, courses) = parsed
        assertTrue("pocas fichas: ${entries.size}", entries.size >= 700)
        assertTrue(entries.count { it.kind == StudyKnowledge.KIND_DEF } >= 250)
        assertTrue(entries.count { it.kind == StudyKnowledge.KIND_PREG } >= 450)
        for (c in listOf("RM", "RV", "LEN", "ARI", "ALG", "GEO", "TRI", "FIS", "QUI", "BIO", "ZOO", "ECO", "HIS", "GEG", "CIV", "ECN")) {
            assertTrue("sin fichas de $c", entries.any { it.course == c })
            assertTrue("sin nombre del curso $c", courses[c].orEmpty().isNotBlank())
        }
        assertEquals("ids repetidos", entries.size, entries.map { it.id }.toSet().size)
        // Texto simple: la burbuja y la voz no reciben LaTeX ni etiquetas.
        assertTrue(entries.none { Regex("""\\[a-zA-Z]|<[a-z/]""").containsMatchIn(it.text + it.question) })
    }

    /** La mejor ficha responde con seguridad y su título trata de [tema] (puede haber varias fichas buenas). */
    private fun responde(pregunta: String, tema: String) {
        val title = bestTitle(pregunta)
        assertTrue("«$pregunta» respondió «$title»", title.orEmpty().contains(tema, ignoreCase = true))
    }

    @Test fun respondeConceptosAunqueSePreguntenDeOtraForma() {
        responde("¿Qué es la fotosíntesis?", "fotosíntesis")
        responde("explícame la fotosintesis", "fotosíntesis")
        responde("qué es la mitosis", "mitosis")
        responde("teorema de pitagoras", "pitágoras")
        responde("ley de ohm", "ohm")
        responde("cómo se hace una regla de tres inversa", "regla de tres")
        responde("¿Quién proclamó la independencia del Perú?", "independencia")
        responde("las ocho regiones naturales de pulgar vidal", "regiones")
        responde("qué es el precio de equilibrio", "equilibrio")
        responde("cuando llevan tilde las palabras graves", "tilde")
    }

    @Test fun losTemasDeMatematicaDeLaAppTambienSeConocen() {
        val hit = knowledge.best("ecuaciones de primer grado")
        assertNotNull(hit)
        assertTrue(hit!!.strong)
    }

    @Test fun loQueNoEstaNoSeInventa() {
        assertNull(bestTitle("cuál es el mejor equipo de fútbol"))
        assertNull(bestTitle("receta de ceviche"))
        assertTrue(knowledge.search("").isEmpty())
        assertTrue(knowledge.search("¿qué es?").isEmpty())
    }

    @Test fun laRespuestaEsCortaSimpleYDiceElCurso() {
        val hit = checkNotNull(knowledge.best("qué es la mitosis"))
        val reply = knowledge.reply(hit)
        assertTrue(reply.startsWith("Mitosis (Biología"))
        assertTrue(reply.length <= StudyKnowledge.MAX_REPLY)
        val question = knowledge.entries.first { it.kind == StudyKnowledge.KIND_PREG }
        val r = knowledge.reply(KnowledgeHit(question, 1.0, 1.0, 1, 1))
        assertTrue(r.startsWith("Una pregunta parecida: «"))
        assertFalse(r.contains("Respuesta: Respuesta:"))
    }

    @Test fun sinTildesNiPlurales() {
        assertEquals("raiz", StudyKnowledge.stem("raices"))
        assertEquals("funcion", StudyKnowledge.stem("funciones"))
        assertEquals("celula", StudyKnowledge.stem("celulas"))
        assertEquals(listOf("celula", "eucariota"), StudyKnowledge.terms("¿Qué son las células eucariotas?"))
    }

    @Test fun unArchivoDanadoNoRompeNada() {
        assertTrue(StudyKnowledge.parse("no es json").first.isEmpty())
        assertTrue(StudyKnowledge.parse("""{"entradas":[{"id":"a"},{"id":"b","t":"T","x":"Texto"}]}""").first.size == 1)
        assertTrue(StudyKnowledge(emptyList()).search("mitosis").isEmpty())
    }

    @Test fun cuentasRapidasSinInternet() {
        assertEquals("3 + 4 × 5 = 23.", QuickMath.answer("¿Cuánto es 3 + 4 × 5?"))
        assertEquals("(2 + 3) × 4 = 20.", QuickMath.answer("cuanto es (2+3)*4"))
        assertEquals("El 20 % de 150 es 30, porque 20 × 150 ÷ 100 = 30.", QuickMath.answer("cuánto es el 20% de 150"))
        assertEquals("√144 = 12.", QuickMath.answer("raíz de 144"))
        assertEquals("2^10 = 1024.", QuickMath.answer("2 elevado a 10"))
        assertEquals("7 ÷ 2 = 3,5.", QuickMath.answer("7 entre 2"))
        assertEquals("12 × 3 = 36.", QuickMath.answer("cuánto es 12 por 3"))
        assertNull(QuickMath.answer("qué es la mitosis"))
        assertNull(QuickMath.answer("5 entre 0"))
        assertNull(QuickMath.answer("2025"))
        assertNull(QuickMath.answer("agéndame estudiar 2 horas"))
    }

    @Test fun saludosYAgradecimientosSinInternet() {
        assertNotNull(OfflineAnswers.smallTalk("Hola"))
        assertNotNull(OfflineAnswers.smallTalk("¡Buenos días!"))
        assertNotNull(OfflineAnswers.smallTalk("gracias"))
        assertNull(OfflineAnswers.smallTalk("hola, qué es la mitosis"))
    }
}
