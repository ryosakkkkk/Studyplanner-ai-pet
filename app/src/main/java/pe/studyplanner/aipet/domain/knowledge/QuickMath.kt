package pe.studyplanner.aipet.domain.knowledge

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Cuentas rápidas sin Internet para «Hablar»: «¿cuánto es 3 + 4 × 5?», «el 20 % de 150», «raíz de 144»,
 * «2 elevado a 10». Solo responde si toda la pregunta es una cuenta; si no, devuelve `null` y la busca la base.
 */
object QuickMath {

    /** Respuesta en una frase, o `null` si el texto no es una cuenta que sepa hacer. */
    fun answer(text: String): String? {
        val t = normalize(text)
        PERCENT.matchEntire(t)?.let { m ->
            val p = number(m.groupValues[1]) ?: return null
            val base = number(m.groupValues[2]) ?: return null
            return "El ${show(p)} % de ${show(base)} es ${show(p * base / 100)}, porque ${show(p)} × ${show(base)} ÷ 100 = ${show(p * base / 100)}."
        }
        val body = PREFIX.replace(t, "").trim().trimEnd('?', '.', '=', ' ')
        if (body.isEmpty() || body.none { it.isDigit() }) return null
        val expression = toSymbols(body)
        if (!expression.all { it.isDigit() || it in "+-*/^().,r " }) return null
        if (expression.count { it in "+-*/^r" } == 0) return null
        val value = runCatching { Parser(expression.replace(" ", "")).parse() }.getOrNull() ?: return null
        if (value.isNaN() || value.isInfinite() || abs(value) > 1e15) return null
        return "${pretty(expression)} = ${show(value)}."
    }

    private val PERCENT = Regex("""^(?:(?:cuanto|calcula|halla|dime)\s+)?(?:es\s+)?(?:el\s+)?(\d+(?:[.,]\d+)?)\s*(?:%|por ciento|porciento)\s+de\s+(\d+(?:[.,]\d+)?)$""")
    private val PREFIX = Regex("""^(?:cuanto\s+(?:es|da|sale|seria)|calcula(?:me)?|resuelve|halla|dime\s+cuanto\s+es|cuanto)\s*""")

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .replace("¿", "").replace("¡", "").replace(Regex("\\s+"), " ").trim().trimEnd('?', '!', '.', ' ')

    private fun toSymbols(s: String): String = s
        .replace(Regex("""raiz\s+cuadrada\s+de\s+|raiz\s+de\s+|√"""), " r ")
        .replace(Regex("""\s*al\s+cuadrado"""), "^2").replace(Regex("""\s*al\s+cubo"""), "^3")
        .replace(Regex("""\s*elevado\s+a(?:\s+la)?\s*"""), "^")
        .replace(Regex("""\s+(?:dividido\s+(?:entre|por|para)|entre)\s+"""), " / ")
        .replace(Regex("""\s+(?:multiplicado\s+por|por|x)\s+"""), " * ")
        .replace(Regex("""(\d)\s*x\s*(\d)"""), "$1*$2")
        .replace("×", "*").replace("÷", "/").replace("·", "*")
        .replace(Regex("""\s+mas\s+"""), " + ").replace(Regex("""\s+menos\s+"""), " - ")

    private fun pretty(e: String): String = e.replace(Regex("\\s+"), "").replace("*", " × ").replace("/", " ÷ ")
        .replace("+", " + ").replace(Regex("(?<=[\\d)])-"), " − ").replace("r", "√").replace(".", ",").replace(Regex("\\s+"), " ").trim()

    private fun number(s: String): Double? = s.replace(',', '.').toDoubleOrNull()

    /** Entero sin decimales; si no, hasta 4 decimales con coma. */
    fun show(v: Double): String {
        val r = (v * 10_000).roundToLong() / 10_000.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString().trimEnd('0').replace('.', ',')
    }

    /** Descenso recursivo: suma y resta < producto y división < potencia < signo y raíz < número o paréntesis. */
    private class Parser(private val s: String) {
        private var i = 0
        fun parse(): Double { val v = sum(); require(i == s.length); return v }
        private fun sum(): Double { var v = product(); while (i < s.length && s[i] in "+-") { val op = s[i++]; val r = product(); v = if (op == '+') v + r else v - r }; return v }
        private fun product(): Double { var v = power(); while (i < s.length && s[i] in "*/") { val op = s[i++]; val r = power(); require(op != '/' || r != 0.0); v = if (op == '*') v * r else v / r }; return v }
        private fun power(): Double { val b = unary(); if (i < s.length && s[i] == '^') { i++; return b.pow(power()) }; return b }
        private fun unary(): Double = when {
            i < s.length && s[i] == '-' -> { i++; -unary() }
            i < s.length && s[i] == 'r' -> { i++; val v = unary(); require(v >= 0); sqrt(v) }
            else -> atom()
        }
        private fun atom(): Double {
            require(i < s.length)
            if (s[i] == '(') { i++; val v = sum(); require(i < s.length && s[i] == ')'); i++; return v }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == ',')) i++
            require(i > start)
            return s.substring(start, i).replace(',', '.').toDouble()
        }
    }
}
