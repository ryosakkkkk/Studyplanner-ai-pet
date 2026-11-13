package pe.studyplanner.aipet.domain.tutor

import java.math.BigInteger
import kotlin.math.abs

/*
 * Herramientas matemáticas de la auditoría (ver ContentAudit). Son INDEPENDIENTES de Numbers.kt y de los
 * generadores: otra clase de racionales (BigInteger, sin desbordamiento) y otro analizador de expresiones que
 * trabaja sobre el TEXTO que ve el estudiante. Así, un error en un generador no puede esconderse detrás del mismo
 * error en el verificador.
 *
 * Solo se usan APIs disponibles desde Android API 26 (por ejemplo, nada de BigInteger.sqrt ni intValueExact).
 */

/** El texto no tiene la forma esperada: el verificador no puede analizarlo. Cuenta como fallo de contenido. */
internal class AuditParseError(message: String) : Exception(message)

internal fun parseFail(message: String): Nothing = throw AuditParseError(message)

/** Racional exacto con BigInteger. */
internal class AQ private constructor(val n: BigInteger, val d: BigInteger) {
    val isInteger: Boolean get() = d == BigInteger.ONE
    val signum: Int get() = n.signum()

    operator fun plus(o: AQ) = of(n * o.d + o.n * d, d * o.d)
    operator fun minus(o: AQ) = of(n * o.d - o.n * d, d * o.d)
    operator fun times(o: AQ) = of(n * o.n, d * o.d)
    operator fun div(o: AQ): AQ {
        if (o.n.signum() == 0) parseFail("división entre cero")
        return of(n * o.d, d * o.n)
    }
    operator fun unaryMinus() = of(-n, d)
    operator fun plus(k: Int) = this + of(k.toLong())
    operator fun minus(k: Int) = this - of(k.toLong())
    operator fun times(k: Int) = this * of(k.toLong())
    operator fun div(k: Int) = this / of(k.toLong())

    fun pow(e: Int): AQ {
        if (abs(e) > MAX_EXPONENT) parseFail("exponente demasiado grande: $e")
        var r = ONE
        repeat(abs(e)) { r *= this }
        return if (e < 0) ONE / r else r
    }

    /** Valor entero pequeño, o `null` si no es entero o no cabe en un Int. */
    fun toIntOrNull(): Int? = if (isInteger && n.bitLength() < 31) n.toInt() else null

    fun toDouble(): Double = n.toDouble() / d.toDouble()

    override fun equals(other: Any?) = other is AQ && n == other.n && d == other.d
    override fun hashCode() = 31 * n.hashCode() + d.hashCode()
    override fun toString() = if (isInteger) n.toString() else "$n/$d"

    companion object {
        private const val MAX_EXPONENT = 400
        val ZERO = AQ(BigInteger.ZERO, BigInteger.ONE)
        val ONE = AQ(BigInteger.ONE, BigInteger.ONE)

        fun of(n: BigInteger, d: BigInteger = BigInteger.ONE): AQ {
            if (d.signum() == 0) parseFail("denominador cero")
            val g = n.gcd(d)
            val sign = if (d.signum() < 0) BigInteger.valueOf(-1) else BigInteger.ONE
            return AQ(sign * (n / g), sign * (d / g))
        }

        fun of(n: Long, d: Long = 1L): AQ = of(BigInteger.valueOf(n), BigInteger.valueOf(d))
    }
}

private val PLAIN_NUMBER = Regex("""^(-?\d+)(?:/(\d+))?$""")

/** Entero o fracción escrita "3", "-5", "3/4"; `null` si el texto no tiene esa forma. */
internal fun parseAQ(text: String): AQ? {
    val m = PLAIN_NUMBER.matchEntire(text.trim()) ?: return null
    val den = m.groupValues[2]
    if (den.isNotEmpty() && BigInteger(den).signum() == 0) return null
    return AQ.of(BigInteger(m.groupValues[1]), if (den.isEmpty()) BigInteger.ONE else BigInteger(den))
}

/** Raíz cuadrada entera exacta de [n], o `null` si no es cuadrado perfecto. */
internal fun exactSqrt(n: Long): Long? {
    if (n < 0) return null
    var r = Math.sqrt(n.toDouble()).toLong()
    while (r * r > n) r--
    while ((r + 1) * (r + 1) <= n) r++
    return if (r * r == n) r else null
}

/**
 * Analizador de expresiones aritméticas/algebraicas como las del enunciado:
 * números, `+ - · × ÷ /`, paréntesis, `^` (con exponente entero, opcionalmente entre paréntesis), `²`, `³`,
 * variables de una letra (las que traiga [vars]) y multiplicación implícita SIN espacios ("3x", "2(x + 1)").
 * Con [decimalComma] acepta "1,25". Cualquier otra cosa lanza [AuditParseError].
 */
internal class ExprParser(
    private val text: String,
    private val vars: Map<Char, AQ> = emptyMap(),
    private val decimalComma: Boolean = false,
) {
    private var i = 0

    fun parse(): AQ {
        val v = expr()
        skipSpaces()
        if (i != text.length) parseFail("sobran caracteres '${text.substring(i)}' en \"$text\"")
        return v
    }

    private fun peek(): Char = if (i < text.length) text[i] else END
    private fun skipSpaces() { while (i < text.length && text[i] == ' ') i++ }

    private fun expr(): AQ {
        var v = term()
        while (true) {
            skipSpaces()
            when (peek()) {
                '+' -> { i++; v += term() }
                '-' -> { i++; v -= term() }
                else -> return v
            }
        }
    }

    private fun term(): AQ {
        var v = unary()
        while (true) {
            val before = i
            skipSpaces()
            val c = peek()
            when {
                c == '·' || c == '×' -> { i++; v *= unary() }
                c == '÷' || c == '/' -> { i++; v /= unary() }
                i == before && (c.isDigit() || c.isLetter() || c == '(') -> v *= unary() // implícita: sin espacios
                else -> { i = before; return v }
            }
        }
    }

    private fun unary(): AQ {
        skipSpaces()
        if (peek() == '-') { i++; return -unary() }
        return power()
    }

    private fun power(): AQ {
        var base = atom()
        while (true) {
            when (peek()) {
                '^' -> { i++; base = base.pow(exponent()) }
                '²' -> { i++; base = base.pow(2) }
                '³' -> { i++; base = base.pow(3) }
                else -> return base
            }
        }
    }

    private fun exponent(): Int {
        if (peek() == '(') {
            i++
            val v = expr()
            skipSpaces()
            if (peek() != ')') parseFail("falta ')' en \"$text\"")
            i++
            return v.toIntOrNull() ?: parseFail("exponente no entero en \"$text\"")
        }
        val negative = peek() == '-'
        if (negative) i++
        val start = i
        while (peek().isDigit()) i++
        if (start == i) parseFail("exponente vacío en \"$text\"")
        val magnitude = text.substring(start, i).toIntOrNull() ?: parseFail("exponente enorme en \"$text\"")
        return if (negative) -magnitude else magnitude
    }

    private fun atom(): AQ {
        skipSpaces()
        val c = peek()
        return when {
            c.isDigit() -> number()
            c == '(' -> {
                i++
                val v = expr()
                skipSpaces()
                if (peek() != ')') parseFail("falta ')' en \"$text\"")
                i++
                v
            }
            c.isLetter() -> {
                i++
                vars[c] ?: parseFail("variable '$c' desconocida en \"$text\"")
            }
            else -> parseFail("carácter inesperado '${if (c == END) "fin" else c.toString()}' en \"$text\"")
        }
    }

    private fun number(): AQ {
        val start = i
        while (peek().isDigit()) i++
        var value = AQ.of(BigInteger(text.substring(start, i)))
        if (decimalComma && peek() == ',' && i + 1 < text.length && text[i + 1].isDigit()) {
            i++
            val fracStart = i
            while (peek().isDigit()) i++
            val digits = text.substring(fracStart, i)
            value += AQ.of(BigInteger(digits), BigInteger.TEN.pow(digits.length))
        }
        return value
    }

    private companion object {
        const val END = '\u0000'
    }
}

/** Evalúa [text] con las variables dadas. */
internal fun evalExpr(text: String, vars: Map<Char, AQ> = emptyMap(), decimalComma: Boolean = false): AQ =
    ExprParser(text, vars, decimalComma).parse()

/** Separa "izquierda = derecha" (exactamente un signo igual). */
internal fun splitEquation(line: String): Pair<String, String> {
    val parts = line.split(" = ")
    if (parts.size != 2) parseFail("se esperaba una ecuación con un solo '=': \"$line\"")
    return parts[0] to parts[1]
}

/**
 * Cadenas aritméticas "a = b = c" dentro de un texto (pistas, pasos, retroalimentación), con solo números.
 * Devuelve las cadenas que SÍ se pueden evaluar por completo y el resultado de cada lado. Es conservador: omite
 * toda cadena pegada a letras, con un signo binario a la izquierda o que no se pueda analizar, para no dar falsos
 * positivos. No pretende encontrar todas las operaciones del texto, solo comprobar las que encuentra.
 */
internal object ArithmeticChains {

    class Chain(val text: String, val sides: List<AQ>) {
        val consistent: Boolean get() = sides.all { it == sides.first() }
    }

    private val SEGMENT = Regex("""[\d,+\-·×÷/^()²³= ]+""")

    fun find(text: String): List<Chain> {
        val out = mutableListOf<Chain>()
        for (m in SEGMENT.findAll(text)) {
            val raw = m.value
            if ('=' !in raw) continue
            val start = m.range.first
            val end = m.range.last + 1
            val lead = raw.length - raw.trimStart().length
            val firstIndex = start + lead
            val firstChar = text.getOrNull(firstIndex) ?: continue
            // Letra o dígito pegados a la izquierda ("a16", "x2"): el primer lado es un nombre, no un número.
            val before = text.getOrNull(firstIndex - 1)
            val glued = before != null && (before.isLetterOrDigit() || before == '_')
            if (before == '√') continue // "√1681 = 41": el primer lado es el radicando, no un valor igual al segundo
            // Un signo al inicio solo es unario si lo que hay antes no es un operando.
            if (firstChar in "+-·×÷/^") {
                val prev = text.substring(0, firstIndex).trimEnd().lastOrNull()
                if (firstChar != '-' || (prev != null && (prev.isLetterOrDigit() || prev == ')' || prev == '²'))) continue
            }
            // A la derecha no puede haber una letra pegada ("6x").
            val trimmedEnd = raw.trimEnd().trimEnd(',')
            val lastIndex = start + trimmedEnd.length
            if (text.getOrNull(lastIndex)?.let { it.isLetter() || it == '√' } == true) continue
            val sides = trimmedEnd.split('=').map { it.trim() }
            // El primer lado se descarta si es vacío ("x = ...") o si es el final de un nombre ("a16 = ...").
            val dropFirst = sides.first().isEmpty() || (glued && sides.first().all { it.isDigit() })
            if (glued && !dropFirst) continue
            val usable = if (dropFirst) sides.drop(1) else sides
            if (usable.size < 2 || usable.any { it.isEmpty() }) continue
            val values = try {
                usable.map { evalExpr(it, decimalComma = true) }
            } catch (e: AuditParseError) {
                continue
            }
            out += Chain(usable.joinToString(" = "), values)
        }
        return out
    }
}
