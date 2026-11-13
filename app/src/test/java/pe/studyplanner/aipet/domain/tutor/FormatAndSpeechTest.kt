package pe.studyplanner.aipet.domain.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatAndSpeechTest {

    // ---- formato peruano ----

    @Test
    fun fraccionesYEnterosSeEscribenComoEnElAula() {
        assertEquals("3/4", Fmt.rational(rat(3) / rat(4)))
        assertEquals("-3/4", Fmt.rational(rat(-3) / rat(4)))
        assertEquals("-3/4", Fmt.rational(rat(3) / rat(-4)))
        assertEquals("5", Fmt.rational(rat(10) / rat(2)))
        assertEquals("0", Fmt.rational(rat(0) / rat(7)))
        assertEquals("1/2", Fmt.rational(rat(2) / rat(4)))
    }

    @Test
    fun losDecimalesUsanComaYNuncaSalenConMenosCero() {
        assertEquals("1,5", Fmt.decimal(1.5))
        assertEquals("2", Fmt.decimal(2.0))
        assertEquals("0", Fmt.decimal(0.0))
        assertEquals("0", Fmt.decimal(-0.001))
        assertEquals("-0,5", Fmt.decimal(-0.5))
        assertEquals("0,125", Fmt.decimal(0.125, 4))
        assertEquals("0,33", Fmt.decimal(1.0 / 3.0))
        assertEquals("1.000".replace(".", ""), Fmt.decimal(1000.0))
    }

    @Test
    fun solesPorcentajesYGrados() {
        assertEquals("S/ 120", Fmt.soles(120))
        assertEquals("20%", Fmt.percent(20))
        assertEquals("37°", Fmt.degrees(37))
        assertEquals("2^5", Fmt.power(2, 5))
        assertEquals("2^(-3)", Fmt.power(2, -3))
    }

    @Test
    fun lasSumasAlgebraicasLlevanBienLosSignos() {
        assertEquals("3x + 5", Fmt.combine(3 to "x", 5 to ""))
        assertEquals("-x + 2", Fmt.combine(-1 to "x", 2 to ""))
        assertEquals("2x - 3y", Fmt.combine(2 to "x", -3 to "y"))
        assertEquals("x - 4", Fmt.combine(1 to "x", -4 to ""))
        assertEquals("0", Fmt.combine(0 to "x", 0 to ""))
        assertEquals("3x + 4", Fmt.combine(3 to "x", 0 to "y", 4 to ""))
    }

    @Test
    fun noQuedanUnosNiParentesisSobrantes() {
        assertEquals("x", Fmt.xTerm(1))
        assertEquals("5x", Fmt.xTerm(5))
        assertEquals("5", Fmt.mul(1, "5"))
        assertEquals("-5", Fmt.mul(-1, "5"))
        assertEquals("3·x", Fmt.mul(3, "x"))
        assertEquals("(x - 3)", Fmt.scaled(1, "x - 3"))
        assertEquals("2(x - 3)", Fmt.scaled(2, "x - 3"))
        assertEquals("(-5)", Fmt.p(-5))
        assertEquals("5", Fmt.p(5))
        assertEquals("(-1/2)", Fmt.p(rat(-1) / rat(2)))
    }

    @Test
    fun losRadicalesSeSimplificanYSeEscriben() {
        assertEquals("√3", Fmt.surd(Surd.of(1, 3)))
        assertEquals("2√3", Fmt.surd(Surd.of(2, 3)))
        assertEquals("2√3", Fmt.surd(Surd.of(1, 12)))
        assertEquals("-6√3", Fmt.surd(Surd.of(-3, 12)))
        assertEquals("√3/2", Fmt.surd(Surd.of(rat(1) / rat(2), 3)))
        assertEquals("5", Fmt.surd(Surd.of(1, 25)))
        assertEquals("0", Fmt.surd(Surd.of(0, 7)))
    }

    @Test
    fun laAritmeticaRacionalEsExactaYSimplificada() {
        assertEquals(rat(1) / rat(2), rat(1) / rat(3) + rat(1) / rat(6))
        assertEquals(rat(-1) / rat(6), rat(1) / rat(3) - rat(1) / rat(2))
        assertEquals(rat(2) / rat(3), (rat(1) / rat(2)) * (rat(4) / rat(3)))
        assertTrue((rat(6) / rat(3)).isInteger)
        assertFalse((rat(1) / rat(3)).isInteger)
        assertTrue(rat(-1) / rat(3) < rat(0))
        assertTrue(rat(1) / rat(3) < rat(1) / rat(2))
        assertEquals(3, gcd(-12, 9))
        assertEquals(12, lcm(4, 6))
    }

    // ---- voz ----

    @Test
    fun laVozLeeMonedaPorcentajesYGrados() {
        assertEquals("120 soles", Speech.forTts("S/ 120"))
        assertEquals("12,5 soles", Speech.forTts("S/ 12,5"))
        assertEquals("20 por ciento", Speech.forTts("20%"))
        assertEquals("37 grados", Speech.forTts("37°"))
        assertEquals("(3x menos 20) grados", Speech.forTts("(3x - 20)°"))
        assertEquals("60 kilómetros por hora", Speech.forTts("60 km/h"))
    }

    @Test
    fun laVozLeeFraccionesPotenciasYRaices() {
        assertEquals("3 sobre 4", Speech.forTts("3/4"))
        assertTrue(Speech.forTts("2^(-3)").contains("elevado a menos 3"))
        assertTrue(Speech.forTts("2^5").contains("elevado a 5"))
        assertEquals("x al cuadrado menos 81 igual a 0", Speech.forTts("x² - 81 = 0"))
        assertEquals("raíz de 3 sobre 2", Speech.forTts("√3/2"))
        assertEquals("2 por raíz de 3", Speech.forTts("2√3"))
        assertEquals("seno 30 grados", Speech.forTts("sen 30°"))
        assertEquals("6 entre 2", Speech.forTts("6 ÷ 2"))
        assertEquals("2 por 3", Speech.forTts("2 · 3"))
        assertEquals("logaritmo en base 5 de 125", Speech.forTts("log_5(125)"))
        assertEquals("logaritmo en base x de 16 igual a 4", Speech.forTts("log_x(16) = 4"))
        assertEquals("logaritmo en base 3 de 729 entre 81", Speech.forTts("log_3(729 ÷ 81)"))
    }

    @Test
    fun laVozUneLasLineasYConservaLosNumerosNegativos() {
        assertEquals("Resuelve la ecuación. 2x más 1 igual a 5. ¿Cuánto vale x?", Speech.forTts("Resuelve la ecuación\n2x + 1 = 5\n¿Cuánto vale x?"))
        assertTrue(Speech.forTts("x = -3").contains("menos 3"))
        assertEquals("1,5", Speech.forTts("1,5"))
    }

    @Test
    fun laVozNuncaDejaSimbolosEnLosTextosDeLosTemas() {
        val leftovers = Regex("""[\^²³√÷·×°≈=/%]""")
        for (t in TutorRegistry.topics) {
            val example = checkNotNull(t.workedExample)
            val texts = t.explanation + t.objective + example.steps + example.statement
            for (text in texts) {
                val spoken = Speech.forTts(text)
                assertFalse("${t.id}: '$spoken'", leftovers.containsMatchIn(spoken))
            }
        }
    }
}
