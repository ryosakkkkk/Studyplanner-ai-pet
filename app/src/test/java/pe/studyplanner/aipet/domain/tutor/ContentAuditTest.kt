package pe.studyplanner.aipet.domain.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El auditor completo (500 semillas por tema y dificultad) se ejecuta con `tools/build.sh audit` y escribe
 * AUDIT_REPORT.md; aquí se corre una versión reducida y se prueba que el auditor de verdad detecta errores.
 */
class ContentAuditTest {

    @Test
    fun laAuditoriaReducidaNoTieneFallos() {
        val result = ContentAudit.run(seeds = 40, withSelfTest = false)
        val first = result.total.failures.take(5).joinToString("; ") {
            "${it.topicId} d${it.difficulty} s${it.seed} ${it.check}: ${it.detail}"
        }
        assertEquals("fallos: $first", 0, result.total.totalFails)
        assertEquals(TutorRegistry.topics.size * 3 * 40, result.exercises)
        assertTrue(result.total.totalRuns > 10_000)
    }

    @Test
    fun laAuditoriaDeSemillasLejanasTampocoFalla() {
        val result = ContentAudit.run(seeds = 25, withSelfTest = false, firstSeed = 7_000_001L)
        assertEquals(result.total.failures.take(3).toString(), 0, result.total.totalFails)
    }

    @Test
    fun losEjerciciosQueRealmenteSirveUnaSesionPasanLaAuditoria() {
        // Las sesiones derivan semillas grandes; se audita exactamente lo que vería el estudiante.
        var audited = 0
        for (t in TutorRegistry.topics) for (sessionSeed in 1L..25L) {
            val s = newSession(t.id, SessionConfig(seed = sessionSeed, guidedDifficulties = listOf(1, 2, 3), unaidedDifficulties = listOf(3, 2)))
            val ids = s.playPerfect().mapNotNull { it.exercise?.id }.toSet()
            assertEquals("${t.id} seed $sessionSeed", 5, ids.size)
            for (id in ids) {
                val e = checkNotNull(TutorRegistry.regenerate(id))
                assertEquals(id, emptyList<String>(), ContentAudit.auditExercise(e))
                audited++
            }
        }
        assertEquals(TutorRegistry.topics.size * 25 * 5, audited)
    }

    @Test
    fun elRegistroDeTemasPasaSuAuditoria() {
        val tally = ContentAudit.auditRegistry()
        assertEquals(tally.failures.toString(), 0, tally.totalFails)
        assertTrue(tally.runs.getValue("REGISTRO_10_TEMAS") >= 1)
    }

    @Test
    fun laAutopruebaDetectaTodasLasAlteraciones() {
        val results = ContentAudit.selfTest()
        assertTrue("solo ${results.size} alteraciones", results.size >= 50)
        val missed = results.filter { !it.second }.map { it.first }
        assertTrue("alteraciones no detectadas: $missed", missed.isEmpty())
    }

    @Test
    fun unEjercicioValidPasaYUnoConLaRespuestaMovidaFalla() {
        for (g in TutorRegistry.generators) {
            val e = g.generate(3, 2)
            assertEquals(g.topicId, emptyList<String>(), ContentAudit.auditExercise(e))

            val other = (e.correctIndex + 1) % Exercise.OPTION_COUNT
            val feedback = e.optionFeedback.toMutableMap()
            val moved = checkNotNull(feedback.remove(other))
            feedback[e.correctIndex] = moved
            val broken = e.copy(correctIndex = other, optionFeedback = feedback)
            assertFalse("${g.topicId}: el auditor no detectó la respuesta movida", ContentAudit.auditExercise(broken).isEmpty())
        }
    }

    @Test
    fun unEnunciadoAlteradoNoCoincideConLaRespuestaYElAuditorLoDetecta() {
        val e = TutorRegistry.generate("ecuaciones-lineales", 5, 2)
        val altered = e.copy(statement = e.statement.replace(Regex("""\d+"""), "999"))
        assertFalse(ContentAudit.auditExercise(altered).isEmpty())
    }
}
