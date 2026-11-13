package pe.studyplanner.aipet.domain.tutor

import pe.studyplanner.aipet.domain.tutor.Fmt.combine
import pe.studyplanner.aipet.domain.tutor.Fmt.p
import kotlin.math.abs

/**
 * Ecuaciones cuadráticas con raíces enteras (resolución por factorización).
 * Nivel 1: raíces positivas. Nivel 2: raíces de cualquier signo. Nivel 3: coeficiente principal distinto de 1,
 * términos en ambos lados o raíz doble.
 */
class QuadraticGenerator : BaseGenerator(TOPIC_ID) {

    /** Forma en que se presenta la ecuación. */
    private enum class Form { MONIC, SCALED, BOTH_SIDES, DOUBLE_ROOT }

    /** Ecuación a x² + b x + c = 0 con raíces enteras [r1] <= [r2]. */
    private class Quad(val form: Form, val r1: Int, val r2: Int, val k: Int = 1, val left: Int = 0, val right: Int = 0)

    override fun draft(rng: SeededRandom, difficulty: Int): Draft = build(
        when (difficulty) {
            1 -> positiveRoots(rng)
            2 -> anyRoots(rng)
            else -> advanced(rng)
        },
    )

    private fun ordered(a: Int, b: Int) = Pair(minOf(a, b), maxOf(a, b))

    private fun positiveRoots(rng: SeededRandom): Quad {
        val r1 = rng.int(1, 9)
        val r2 = rng.intExcept(1, 9, r1)
        val (lo, hi) = ordered(r1, r2)
        return Quad(Form.MONIC, lo, hi)
    }

    private fun anyRoots(rng: SeededRandom): Quad {
        val r1 = rng.intExcept(-9, 9, 0)
        val r2 = rng.intExcept(-9, 9, 0, r1)
        val (lo, hi) = ordered(r1, r2)
        return Quad(Form.MONIC, lo, hi)
    }

    private fun advanced(rng: SeededRandom): Quad = when (rng.below(3)) {
        0 -> {
            val r1 = rng.intExcept(-6, 6, 0)
            val r2 = rng.intExcept(-6, 6, 0, r1)
            val (lo, hi) = ordered(r1, r2)
            Quad(Form.SCALED, lo, hi, k = rng.int(2, 4))
        }
        1 -> {
            val r1 = rng.intExcept(-8, 8, 0)
            val r2 = rng.intExcept(-8, 8, 0, r1)
            val (lo, hi) = ordered(r1, r2)
            val b = -(lo + hi)
            val l = rng.search { intExcept(-6, 6, 0).takeIf { it - b != 0 } }
            Quad(Form.BOTH_SIDES, lo, hi, left = l, right = l - b)
        }
        else -> {
            val r = rng.intExcept(-9, 9, 0)
            Quad(Form.DOUBLE_ROOT, r, r)
        }
    }

    // ---- coeficientes y texto ----

    /** Coeficiente de x de la ecuación simplificada (mónica): -(r1 + r2). */
    private fun monicB(q: Quad): Int = -(q.r1 + q.r2)

    /** Término independiente de la ecuación simplificada (mónica): r1·r2. */
    private fun monicC(q: Quad): Int = q.r1 * q.r2

    private fun equationText(q: Quad): String = when (q.form) {
        Form.BOTH_SIDES -> "${combine(1 to "x²", q.left to "x")} = ${combine(q.right to "x", -monicC(q) to "")}"
        else -> "${combine(q.k to "x²", q.k * monicB(q) to "x", q.k * monicC(q) to "")} = 0"
    }

    private fun rootsText(roots: List<Int>): String =
        roots.distinct().sorted().joinToString(" o ") { "x = $it" }

    /** Raíces enteras de a x² + b x + c = 0 en [-100, 100], o null si no hay entre 1 y 2. */
    private fun integerRoots(a: Int, b: Int, c: Int): List<Int>? {
        val roots = (-SEARCH_LIMIT..SEARCH_LIMIT).filter { a * it * it + b * it + c == 0 }
        return roots.takeIf { it.size in 1..2 }
    }

    private fun factorText(root: Int): String = "(${combine(1 to "x", -root to "")})"

    private fun checkText(root: Int, b: Int, c: Int): String {
        val middle = if (b == 0) "" else " ${Fmt.op(b)} ${abs(b)}·${p(root)}"
        val last = if (c == 0) "" else " ${Fmt.op(c)} ${abs(c)}"
        val values = combine(root * root to "", b * root to "", c to "")
        return "${p(root)}²$middle$last = $values = 0"
    }

    // ---- distractores ----

    private fun addRoots(list: MutableList<Wrong>, roots: List<Int>, feedback: String, twoRoots: Boolean = false) {
        if (roots.isEmpty()) return
        val message = if (twoRoots && roots.distinct().size == 1) MISSING_ROOT else feedback
        list += Wrong(rootsText(roots), message)
    }

    private fun buildWrong(q: Quad): List<Wrong> {
        val b = monicB(q)
        val c = monicC(q)
        val r1 = q.r1
        val r2 = q.r2
        val out = mutableListOf<Wrong>()
        val two = r1 != r2
        addRoots(out, listOf(-r1, -r2), "Los signos de las raíces están al revés: si un factor es (x - 3), la raíz es x = 3, no x = -3.")
        if (r1 != r2) {
            addRoots(out, listOf(r1, -r2), SIGN_SLIP, two)
            addRoots(out, listOf(-r1, r2), SIGN_SLIP, two)
            addRoots(out, listOf(r1), MISSING_ROOT)
            addRoots(out, listOf(r2), MISSING_ROOT)
        } else {
            addRoots(out, listOf(r1, -r1), "Esta ecuación tiene una sola solución (raíz doble): ya no hay una segunda raíz de signo opuesto.")
        }
        if (b != 0) addRoots(out, listOf(0, -b), "Ignoraste el término independiente $c: no se puede quitar y resolver solo x(${combine(1 to "x", b to "")}) = 0.")
        factorPairs(c, r1 + r2).forEach {
            addRoots(out, listOf(it.first, it.second), "Esos números multiplican $c, pero no cumplen la condición de la suma: " +
                "busca dos números que multiplicados den $c Y sumados den $b.")
        }
        mutatedRoots(q, b, c).forEach { addRoots(out, it.first, it.second) }
        return out
    }

    /** Pares enteros (u, v) con u·v = [product] y u + v distinto de [sum] y de -[sum]. */
    private fun factorPairs(product: Int, sum: Int): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        for (u in 1..abs(product)) {
            if (product % u != 0) continue
            val v = product / u
            if (u > abs(v)) continue
            listOf(u to v, -u to -v).filter { it.first + it.second != sum && it.first + it.second != -sum }
                .forEach { result += it }
        }
        return result.take(MAX_FACTOR_PAIRS)
    }

    /** Raíces de ecuaciones con un error típico de transformación, resueltas por búsqueda exhaustiva. */
    private fun mutatedRoots(q: Quad, b: Int, c: Int): List<Pair<List<Int>, String>> {
        val result = mutableListOf<Pair<List<Int>, String>>()
        integerRoots(1, b, -c)?.let {
            result += it to "Parece que cambiaste el signo del término independiente al pasarlo al otro lado."
        }
        if (q.form == Form.SCALED) {
            integerRoots(1, b, q.k * c)?.let {
                result += it to "Al dividir entre ${q.k} hay que dividir TODOS los términos, también el último."
            }
            integerRoots(1, q.k * b, c)?.let {
                result += it to "Al dividir entre ${q.k} hay que dividir TODOS los términos, también el de x."
            }
        }
        if (q.form == Form.BOTH_SIDES) {
            integerRoots(1, q.left + q.right, c)?.let {
                result += it to "Al pasar ${combine(q.right to "x")} al lado izquierdo debe cambiar de signo."
            }
        }
        return result
    }

    // ---- ejercicio ----

    private fun build(q: Quad): Draft {
        val b = monicB(q)
        val c = monicC(q)
        val u = -q.r1
        val v = -q.r2
        val correct = rootsText(listOf(q.r1, q.r2))
        val fill = fillers(q)
        return Draft(
            statement = "Resuelve la ecuación:\n${equationText(q)}\n¿Cuál es el conjunto solución?",
            correct = correct,
            wrong = buildWrong(q),
            hints = listOf(
                "Estas ecuaciones se resuelven factorizando: (x + u)(x + v) = x² + (u + v)x + u·v. " +
                    "Si el producto de dos factores es cero, alguno de ellos es cero.",
                secondHint(q, b, c),
                "Los números buscados son $u y $v: la ecuación queda ${factorText(q.r1)}${factorText(q.r2)} = 0. " +
                    "Iguala cada factor a cero.",
            ),
            steps = steps(q, b, c, u, v),
            fillers = fill,
        )
    }

    private fun secondHint(q: Quad, b: Int, c: Int): String {
        val search = "Busca dos números que multiplicados den $c y sumados den $b."
        return when (q.form) {
            Form.MONIC, Form.DOUBLE_ROOT -> search
            Form.SCALED -> "Primero divide toda la ecuación entre ${q.k}. Después: " + search.replaceFirstChar { it.lowercase() }
            Form.BOTH_SIDES -> "Primero pasa todos los términos a un solo lado (cambian de signo), reduce y deja la ecuación igualada a cero. " +
                "Después: " + search.replaceFirstChar { it.lowercase() }
        }
    }

    private fun fillers(q: Quad): List<Wrong> =
        listOf(1 to 0, 0 to 1, -1 to 0, 0 to -1, 1 to 1, -1 to -1).mapNotNull { (d1, d2) ->
            val roots = listOf(q.r1 + d1, q.r2 + d2).filter { it != 0 }
            if (roots.isEmpty()) null else Wrong(rootsText(roots), GENERIC_FEEDBACK)
        }

    private fun steps(q: Quad, b: Int, c: Int, u: Int, v: Int): List<String> {
        val monic = "${combine(1 to "x²", b to "x", c to "")} = 0"
        val intro = when (q.form) {
            Form.MONIC, Form.DOUBLE_ROOT -> "La ecuación ya está igualada a cero: ${equationText(q)}."
            Form.SCALED -> "Dividimos toda la ecuación entre ${q.k} para simplificar: $monic."
            Form.BOTH_SIDES -> "Pasamos todos los términos al lado izquierdo (cambian de signo) y reducimos: $monic."
        }
        val factoring = "Buscamos dos números que multiplicados den $c y sumados den $b: son $u y $v."
        val factors = "Factorizamos: ${factorText(q.r1)}${factorText(q.r2)} = 0."
        val zero = if (q.r1 == q.r2) {
            "Es un cuadrado perfecto: ${factorText(q.r1)}² = 0, así que x ${Fmt.op(u)} ${abs(u)} = 0 y x = ${q.r1}. Es una raíz doble."
        } else {
            "Igualamos cada factor a cero: x ${Fmt.op(u)} ${abs(u)} = 0 da x = ${q.r1}; x ${Fmt.op(v)} ${abs(v)} = 0 da x = ${q.r2}."
        }
        val check = if (q.r1 == q.r2) {
            "Comprobación: ${checkText(q.r1, b, c)}."
        } else {
            "Comprobación: ${checkText(q.r1, b, c)} y ${checkText(q.r2, b, c)}."
        }
        return listOf(intro, factoring, factors, zero, check, "Respuesta: ${rootsText(listOf(q.r1, q.r2))}.")
    }

    companion object {
        const val TOPIC_ID = "ecuaciones-cuadraticas"
        private const val SEARCH_LIMIT = 100
        private const val MISSING_ROOT = "Falta una raíz: esta ecuación tiene dos soluciones distintas."
        private const val SIGN_SLIP = "Una de las raíces tiene el signo cambiado. Iguala cada factor a cero y despeja con cuidado."
        private const val MAX_FACTOR_PAIRS = 3
    }
}
