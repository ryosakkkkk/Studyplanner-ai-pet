package pe.studyplanner.aipet.domain.tutor

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.sqrt

/** Fracción exacta y normalizada (denominador > 0, sin factores comunes). */
class Rational private constructor(val num: Long, val den: Long) : Comparable<Rational> {

    val isInteger: Boolean get() = den == 1L
    val signum: Int get() = if (num > 0) 1 else if (num < 0) -1 else 0

    operator fun plus(o: Rational) = of(num * o.den + o.num * den, den * o.den)
    operator fun minus(o: Rational) = of(num * o.den - o.num * den, den * o.den)
    operator fun times(o: Rational) = of(num * o.num, den * o.den)
    operator fun div(o: Rational): Rational {
        require(o.num != 0L) { "División entre cero" }
        return of(num * o.den, den * o.num)
    }
    operator fun unaryMinus() = of(-num, den)
    operator fun plus(n: Int) = this + of(n)
    operator fun minus(n: Int) = this - of(n)
    operator fun times(n: Int) = this * of(n)
    operator fun div(n: Int) = this / of(n)

    /** Valor entero; falla si no lo es. */
    fun toIntExact(): Int {
        check(isInteger) { "$this no es entero" }
        return Math.toIntExact(num)
    }

    fun toDouble(): Double = num.toDouble() / den.toDouble()

    /** Verdadero si es un valor "razonable" para mostrar a un estudiante. */
    fun isSane(maxAbs: Long = 5000, maxDen: Long = 12): Boolean = den <= maxDen && abs(num) <= maxAbs * den

    override fun compareTo(other: Rational): Int = (num * other.den).compareTo(other.num * den)
    override fun equals(other: Any?) = other is Rational && num == other.num && den == other.den
    override fun hashCode() = 31 * num.hashCode() + den.hashCode()
    override fun toString() = Fmt.rational(this)

    companion object {
        val ZERO = Rational(0, 1)
        val ONE = Rational(1, 1)

        fun of(num: Long, den: Long = 1): Rational {
            require(den != 0L) { "Denominador cero" }
            val g = gcd(abs(num), abs(den))
            val sign = if (den < 0) -1 else 1
            return Rational(sign * num / g, sign * den / g)
        }

        fun of(num: Int, den: Int = 1): Rational = of(num.toLong(), den.toLong())

        private fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
    }
}

/** Atajo interno para construir fracciones. */
internal fun rat(n: Int, d: Int = 1): Rational = Rational.of(n, d)

/**
 * Número de la forma coef·√rad con rad libre de cuadrados (por ejemplo 5√3 o √2/2).
 * Sirve para los triángulos notables (30°-60°, 45°).
 */
class Surd private constructor(val coef: Rational, val rad: Int) {

    val isRational: Boolean get() = rad == 1

    operator fun times(o: Surd) = of(coef * o.coef, rad.toLong() * o.rad)

    /** División racionalizando: (a√m)/(b√n) = (a/b)·√(mn)/n. */
    operator fun div(o: Surd): Surd = of(coef / o.coef / rat(o.rad), rad.toLong() * o.rad)

    operator fun times(r: Rational) = of(coef * r, rad.toLong())

    operator fun unaryMinus() = Surd(-coef, rad)

    /** Verdadero si la suma con [o] se puede expresar con un solo radical. */
    fun canAdd(o: Surd): Boolean = coef.signum == 0 || o.coef.signum == 0 || rad == o.rad

    /** Suma de radicales semejantes (mismo radicando) o con cero; falla si no son semejantes. */
    operator fun plus(o: Surd): Surd {
        require(canAdd(o)) { "Radicales no semejantes: $this + $o" }
        if (coef.signum == 0) return o
        if (o.coef.signum == 0) return this
        val sum = coef + o.coef
        return if (sum.signum == 0) Surd(Rational.ZERO, 1) else Surd(sum, rad)
    }

    operator fun minus(o: Surd): Surd = this + (-o)

    /** Valor exacto si es racional (radicando 1); falla en caso contrario. */
    fun toRational(): Rational {
        check(isRational) { "$this no es racional" }
        return coef
    }

    /** Verdadero si el valor es cero. */
    val isZero: Boolean get() = coef.signum == 0

    fun toDouble(): Double = coef.toDouble() * sqrt(rad.toDouble())

    override fun equals(other: Any?) = other is Surd && coef == other.coef && rad == other.rad
    override fun hashCode() = 31 * coef.hashCode() + rad
    override fun toString() = Fmt.surd(this)

    companion object {
        /** Construye coef·√radicand simplificando los cuadrados perfectos. */
        fun of(coef: Rational, radicand: Long): Surd {
            require(radicand >= 1) { "Radicando inválido" }
            if (coef.signum == 0) return Surd(Rational.ZERO, 1)
            var rad = radicand
            var c = coef
            var k = 2L
            while (k * k <= rad) {
                while (rad % (k * k) == 0L) {
                    rad /= k * k
                    c *= rat(k.toInt())
                }
                k++
            }
            return Surd(c, Math.toIntExact(rad))
        }

        fun of(coef: Int, rad: Int = 1): Surd = of(rat(coef), rad.toLong())

        /** Igual que [of] con radicando entero. */
        fun of(coef: Rational, radicand: Int): Surd = of(coef, radicand.toLong())

        /** Número racional como radical. */
        fun of(value: Rational): Surd = Surd(value, 1)
    }
}

/**
 * Formato numérico al estilo peruano: enteros sin separador de miles, fracciones "3/4",
 * decimales con coma ("0,5"), moneda "S/ 120", porcentajes "20%" y grados "37°".
 */
object Fmt {

    fun rational(r: Rational): String = if (r.isInteger) r.num.toString() else "${r.num}/${r.den}"

    /** Decimal con coma y sin ceros sobrantes; nunca produce "-0". */
    fun decimal(value: Double, maxDecimals: Int = 2): String {
        val bd = BigDecimal.valueOf(value).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros()
        if (bd.signum() == 0) return "0"
        return bd.toPlainString().replace('.', ',')
    }

    fun soles(n: Int): String = "S/ $n"
    fun percent(n: Int): String = "$n%"
    fun degrees(n: Int): String = "$n°"

    /** Potencia en texto plano: "2^5", y "2^(-3)" para exponentes negativos. */
    fun power(base: Int, exp: Int): String = if (exp < 0) "$base^($exp)" else "$base^$exp"

    fun surd(s: Surd): String {
        val c = s.coef
        if (s.rad == 1) return rational(c)
        val body = when (abs(c.num)) {
            1L -> "√${s.rad}"
            else -> "${abs(c.num)}√${s.rad}"
        }
        val sign = if (c.num < 0) "-" else ""
        return if (c.den == 1L) "$sign$body" else "$sign$body/${c.den}"
    }

    /**
     * Suma algebraica con signos bien escritos. Cada término es (coeficiente, variable);
     * la variable vacía representa el término independiente.
     * Ejemplos: "3x + 5", "-x + 2", "2x - 3y", "x - 4".
     */
    fun combine(vararg terms: Pair<Int, String>): String {
        val sb = StringBuilder()
        for ((c, v) in terms) {
            if (c == 0) continue
            val mag = abs(c)
            val body = if (v.isEmpty()) "$mag" else if (mag == 1) v else "$mag$v"
            if (sb.isEmpty()) {
                if (c < 0) sb.append('-')
                sb.append(body)
            } else {
                sb.append(if (c < 0) " - " else " + ").append(body)
            }
        }
        return if (sb.isEmpty()) "0" else sb.toString()
    }

    /** Número con signo explícito en una suma: "+ 5" o "- 5". */
    fun signedTerm(n: Int): String = if (n < 0) "- ${-n}" else "+ $n"

    /** Encierra entre paréntesis los negativos al sustituir: 5 -> "5", -5 -> "(-5)". */
    fun p(n: Int): String = if (n < 0) "($n)" else n.toString()

    /** Igual que [p] para fracciones. */
    fun p(r: Rational): String = if (r.signum < 0) "(${rational(r)})" else rational(r)

    /** k(inner) con k = 1 omitido: "2(x - 3)" o "(x - 3)". */
    fun scaled(k: Int, inner: String): String = if (k == 1) "($inner)" else "$k($inner)"

    /** Suma "[first] ± k(inner)" con el signo bien escrito: "3x - 2(x + 1)". */
    fun addScaled(first: String, k: Int, inner: String): String =
        first + (if (k < 0) " - " else " + ") + scaled(abs(k), inner)

    /** Producto escrito sin ceros ni unos sobrantes: mul(3, "x") = "3·x", mul(1, "5") = "5", mul(-1, "5") = "-5". */
    fun mul(k: Int, value: String): String = when (k) {
        1 -> value
        -1 -> "-$value"
        else -> "$k·$value"
    }

    /** Producto punto de dos pares, para sustituir: a·(u) + b·(v) con los signos bien escritos. */
    fun dot(a: Int, u: Int, b: Int, v: Int): String =
        "${mul(a, p(u))} ${if (b < 0) "-" else "+"} ${mul(abs(b), p(v))}"

    /** Término "kx" sin el 1 sobrante: xTerm(1) = "x", xTerm(5) = "5x". */
    fun xTerm(k: Int, variable: String = "x"): String = if (k == 1) variable else "$k$variable"

    /** Signo operador para armar sumas: "+" o "-". */
    fun op(n: Int): String = if (n < 0) "-" else "+"

    /** Texto de un porcentaje con signo para una variación: "Aumento de 8%", "Descuento de 4%". */
    fun variation(percent: Int): String = when {
        percent > 0 -> "Aumento de $percent%"
        percent < 0 -> "Descuento de ${-percent}%"
        else -> "No varía (0%)"
    }
}

/** Máximo común divisor (siempre no negativo). */
internal fun gcd(a: Int, b: Int): Int = if (b == 0) abs(a) else gcd(b, a % b)

/** Mínimo común múltiplo de enteros no nulos. */
internal fun lcm(a: Int, b: Int): Int = abs(a / gcd(a, b) * b)
