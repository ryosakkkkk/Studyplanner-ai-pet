package pe.studyplanner.aipet.domain.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class MasteryPolicyTest {

    private val topic = "porcentajes"

    private fun attempt(t: Long, mode: AttemptMode, ok: Boolean, topicId: String = topic) =
        Attempt(t, topicId, "$topicId-d2-s$t", mode, if (ok) 0 else 1, ok, 0, 1_000)

    private fun guided(t: Long, ok: Boolean = true, topicId: String = topic) = attempt(t, AttemptMode.GUIDED, ok, topicId)
    private fun unaided(t: Long, ok: Boolean, topicId: String = topic) = attempt(t, AttemptMode.UNAIDED, ok, topicId)

    private fun status(vararg attempts: Attempt) = MasteryPolicy.status(topic, attempts.toList())

    // ---- estado de dominio ----

    @Test
    fun sinIntentosNoEstaIniciado() {
        assertEquals(MasteryStatus.NO_INICIADO, status())
    }

    @Test
    fun soloPracticaGuiadaEstaEnPractica() {
        assertEquals(MasteryStatus.EN_PRACTICA, status(guided(1), guided(2, ok = false), guided(3)))
    }

    @Test
    fun unSoloAciertoSinAyudaTodaviaEstaEnPractica() {
        assertEquals(MasteryStatus.EN_PRACTICA, status(guided(1), unaided(2, true)))
    }

    @Test
    fun dosAciertosSeguidosSinAyudaSonDominio() {
        assertEquals(MasteryStatus.DOMINIO_SIN_AYUDA, status(guided(1), unaided(2, true), unaided(3, true)))
    }

    @Test
    fun unFalloSinAyudaDejaSoloConAyuda() {
        assertEquals(MasteryStatus.SOLO_CON_AYUDA, status(guided(1), unaided(2, false)))
        assertEquals(MasteryStatus.SOLO_CON_AYUDA, status(unaided(2, true), unaided(3, false)))
        assertEquals(MasteryStatus.SOLO_CON_AYUDA, status(unaided(2, false), unaided(3, true)))
        assertEquals(MasteryStatus.SOLO_CON_AYUDA, status(unaided(2, false), unaided(3, false)))
    }

    @Test
    fun soloCuentaLaVentanaDeLosUltimosDosIntentosSinAyuda() {
        assertEquals(MasteryStatus.DOMINIO_SIN_AYUDA, status(unaided(1, false), unaided(2, true), unaided(3, true)))
        assertEquals(MasteryStatus.SOLO_CON_AYUDA, status(unaided(1, true), unaided(2, true), unaided(3, false)))
        assertEquals(MasteryPolicy.WINDOW, 2)
    }

    @Test
    fun losIntentosGuiadosNoCambianElDominioSinAyuda() {
        val base = status(unaided(1, true), unaided(2, true))
        assertEquals(base, status(unaided(1, true), unaided(2, true), guided(3, ok = false), guided(4, ok = false)))
    }

    @Test
    fun seIgnoranLosIntentosDeOtrosTemas() {
        val other = unaided(5, true, "pitagoras")
        assertEquals(MasteryStatus.NO_INICIADO, status(other, unaided(6, true, "pitagoras")))
        assertEquals(MasteryStatus.EN_PRACTICA, status(guided(1), other, unaided(6, true, "pitagoras")))
    }

    @Test
    fun losIntentosSeOrdenanPorFechaAntesDeDecidir() {
        val chronological = listOf(unaided(10, false), unaided(20, true), unaided(30, true))
        assertEquals(MasteryStatus.DOMINIO_SIN_AYUDA, MasteryPolicy.status(topic, chronological))
        assertEquals(MasteryStatus.DOMINIO_SIN_AYUDA, MasteryPolicy.status(topic, chronological.reversed()))
        assertEquals(
            MasteryStatus.SOLO_CON_AYUDA,
            MasteryPolicy.status(topic, listOf(unaided(30, false), unaided(10, true), unaided(20, true))),
        )
    }

    @Test
    fun elResumenDeConteosEsCorrecto() {
        val m = MasteryPolicy.summarize(
            topic,
            listOf(guided(1), guided(2, false), unaided(3, false), unaided(4, true), unaided(5, true), guided(6, true, "otro")),
        )
        assertEquals(2, m.guidedAttempts)
        assertEquals(1, m.guidedCorrect)
        assertEquals(3, m.unaidedAttempts)
        assertEquals(2, m.unaidedCorrect)
        assertEquals(listOf(true, true), m.recentUnaided)
        assertEquals(MasteryStatus.DOMINIO_SIN_AYUDA, m.status)
    }

    // ---- propuesta de repaso ----

    @Test
    fun dosDeDosProponeRepasoEnTresDias() {
        val r = checkNotNull(MasteryPolicy.proposeReview(topic, 2, 2))
        assertEquals(3, r.dayOffset)
        assertEquals(MasteryPolicy.DAYS_AFTER_MASTERY, r.dayOffset)
        assertFalse(r.relearn)
        assertEquals(topic, r.topicId)
        assertTrue(r.reason.isNotBlank())
    }

    @Test
    fun unaDeDosProponeRepasoMañana() {
        val r = checkNotNull(MasteryPolicy.proposeReview(topic, 1, 2))
        assertEquals(1, r.dayOffset)
        assertEquals(MasteryPolicy.DAYS_AFTER_MISS, r.dayOffset)
        assertFalse(r.relearn)
        assertTrue(r.reason.contains("1 de 2"))
    }

    @Test
    fun ceroDeDosProponeVolverAAprenderMañana() {
        val r = checkNotNull(MasteryPolicy.proposeReview(topic, 0, 2))
        assertEquals(1, r.dayOffset)
        assertTrue(r.relearn)
    }

    @Test
    fun sinEjerciciosSinAyudaNoHayPropuesta() {
        assertNull(MasteryPolicy.proposeReview(topic, 0, 0))
        assertNull(MasteryPolicy.proposeReview(topic, emptyList()))
        assertNull(MasteryPolicy.proposeReview(topic, listOf(guided(1))))
    }

    @Test
    fun laPropuestaTambienSeCalculaConLosIntentos() {
        val check = listOf(unaided(1, true), unaided(2, false), guided(3), unaided(4, true, "pitagoras"))
        val r = checkNotNull(MasteryPolicy.proposeReview(topic, check))
        assertEquals(1, r.dayOffset)
        assertFalse(r.relearn)
    }

    @Test
    fun losAciertosFueraDeRangoSeRechazan() {
        for ((correct, total) in listOf(3 to 2, -1 to 2, 1 to 0)) {
            try {
                MasteryPolicy.proposeReview(topic, correct, total)
                fail("debía rechazar $correct de $total")
            } catch (expected: IllegalArgumentException) {
                // correcto
            }
        }
    }

    @Test
    fun laFechaSugeridaSumaLosDiasAHoy() {
        val today = LocalDate.of(2026, 10, 28)
        assertEquals(LocalDate.of(2026, 10, 31), checkNotNull(MasteryPolicy.proposeReview(topic, 2, 2)).dueOn(today))
        assertEquals(LocalDate.of(2026, 10, 29), checkNotNull(MasteryPolicy.proposeReview(topic, 1, 2)).dueOn(today))
        assertEquals(LocalDate.of(2026, 11, 1), ReviewProposal(topic, 4, "x").dueOn(today))
    }

    @Test
    fun laPropuestaNoPuedeSerParaHoy() {
        try {
            ReviewProposal(topic, 0, "hoy")
            fail("dayOffset 0 debía rechazarse")
        } catch (expected: IllegalArgumentException) {
            // correcto
        }
    }
}
