package pe.studyplanner.aipet.domain.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La normalización para la voz (`Speech.forTts`) convierte símbolos como «Al³⁺» o «m/s²» en palabras, pero la pantalla
 * sigue mostrando el texto original: la función no toca el texto de los ejercicios.
 */
class BankSpeechTest {

    /** Símbolos que un sintetizador de voz no sabe leer y que el banco sí usa. */
    private val leftovers = Regex("""[⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻₀-₉ₐ-ₜ½¼¾±−–—…ªº°·×÷^√]|N\.°""")

    private fun say(text: String) = Speech.forTts(text)

    @Test
    fun cargasDeIones() {
        assertEquals("Al tres más", say("Al³⁺"))
        assertEquals("Fe tres más y O dos menos", say("Fe³⁺ y O²⁻"))
        assertEquals("Cl menos.", say("Cl⁻."))
        assertEquals("K más", say("K⁺"))
        assertEquals("El ion Al tres más: parte", say("El ion Al³⁺: parte"))
    }

    @Test
    fun unidadesDeVelocidadYAceleracion() {
        assertEquals("metros por segundo al cuadrado", say("m/s²"))
        assertEquals("20 metros por segundo", say("20 m/s"))
        assertEquals("a igual a menos 5 metros por segundo al cuadrado", say("a = −5 m/s²"))
        assertEquals("72 kilómetros por hora", say("72 km/h"))
        assertEquals("25 segundos", say("25 s"))
        assertEquals("300 metros", say("300 m"))
    }

    @Test
    fun configuracionElectronicaEIsotopos() {
        assertEquals("1 ese 2 2 ese 2 2 pe 6", say("1s² 2s² 2p⁶"))
        assertEquals("3 de 5", say("3d⁵"))
        assertEquals("sodio 23 y magnesio 24", say("²³Na y ²⁴Mg"))
        assertEquals("carbono 14", say("¹⁴C"))
        assertEquals("(n, l, m ele, m ese)", say("(n, l, mₗ, mₛ)"))
    }

    @Test
    fun subindicesExponentesYFracciones() {
        assertEquals("v cero al cuadrado sobre (2g)", say("v₀²/(2g)"))
        assertEquals("x igual a v cero t más un medio at al cuadrado", say("x = v₀t + ½at²"))
        assertEquals("un medio por 3 por 36", say("½ · 3 · 36"))
        assertEquals("más menos un medio", say("±½"))
        assertEquals("n menos 1", say("n − 1"))
        assertEquals("0, 1, 2, etcétera, n menos 1", say("0, 1, 2, …, n − 1"))
        assertEquals("10 elevado a 5", say("10⁵"))
    }

    @Test
    fun normasFechasYOrdinales() {
        assertEquals("número 042 guion 87 guion AG", say("N.° 042-87-AG"))
        assertEquals("Ley número 26834", say("Ley N.° 26834"))
        assertEquals("(23 de julio de 1987)", say("(23-07-1987)"))
        assertEquals("segunda edición, versión provisional", say("2.ª ed., versión provisional"))
    }

    @Test
    fun noRompeLaVozDeMatematica() {
        assertEquals("3 sobre 4 más 2 por ciento", say("3/4 + 2 %"))
        assertEquals("120 soles", say("S/ 120"))
        assertEquals("30 grados", say("30°"))
        assertEquals("x elevado a 2", say("x^2"))
        assertEquals("logaritmo en base 2 de 32", say("log_2(32)"))
    }

    @Test
    fun ningunTextoDelBancoDejaSimbolosParaElSintetizador() {
        var spoken = 0
        for (t in BankFiles.bankTopics) {
            val texts = mutableListOf<String>()
            texts += t.explanation; texts += t.objective; texts += t.title
            for (q in t.questions) {
                texts += q.statement; texts += q.options; texts += q.hints; texts += q.optionFeedback.values; texts += q.explanation
                texts += q.sources.map { "Fuente: ${it.title}" }
            }
            for (x in texts) {
                val out = say(x)
                assertFalse("${t.id}: '$out' (de '$x')", leftovers.containsMatchIn(out))
                assertTrue(out.isNotBlank())
                spoken++
            }
        }
        assertTrue(spoken > 1000)
    }

    @Test
    fun laPantallaMuestraElTextoOriginalYLaVozLoNormalizado() {
        var withSymbols = 0
        for (id in listOf("fisica-cinematica-mru-mruv", "quimica-estructura-atomica")) for (seed in 1L..30L) {
            val s = bankSession(id, seed)
            for (st in s.playBankPerfect()) {
                val view = st.exercise ?: continue
                assertFalse("voz con símbolos: ${st.speech}", leftovers.containsMatchIn(st.speech))
                val shown = view.statement + view.options.joinToString(" ")
                if (leftovers.containsMatchIn(shown)) {
                    withSymbols++
                    // Lo que se ve conserva «m/s²», «Al³⁺»…; solo `speech` cambia.
                    assertTrue(st.text.isNotBlank())
                    val original = bankExerciseOf(st)
                    assertEquals(original.options, view.options)
                    assertEquals(original.statement, view.statement)
                }
            }
        }
        assertTrue("las sesiones de prueba debían mostrar símbolos", withSymbols > 10)
    }

    @Test
    fun normalizarNoCambiaElTextoDeOrigen() {
        for (t in BankFiles.bankTopics) for (q in t.questions) {
            val before = q.statement + q.options.joinToString("|")
            say(q.statement); q.options.forEach { say(it) }
            assertEquals(before, q.statement + q.options.joinToString("|"))
        }
        val exercise = BankFiles.catalog.generate("fisica-cinematica-mru-mruv", 3, 1)
        assertEquals(exercise, BankFiles.catalog.generate("fisica-cinematica-mru-mruv", 3, 1))
    }
}
