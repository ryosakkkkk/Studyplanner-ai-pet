package pe.studyplanner.aipet.domain.tutor

import pe.studyplanner.aipet.domain.tutor.Fmt.combine
import pe.studyplanner.aipet.domain.tutor.Fmt.dot
import pe.studyplanner.aipet.domain.tutor.Fmt.p
import pe.studyplanner.aipet.domain.tutor.Fmt.signedTerm
import kotlin.math.abs

/** Acumulador de distractores para respuestas en forma de par "x = a, y = b" (enteros primero). */
internal class PairPool {
    val items = mutableListOf<Wrong>()
    private val secondary = mutableListOf<Wrong>()

    /** Agrega el par (x, y) si ambos valores son presentables; los pares fraccionarios quedan de respaldo. */
    fun add(x: Rational, y: Rational, feedback: String) {
        if (!x.isSane(MAX_ABS, MAX_DEN) || !y.isSane(MAX_ABS, MAX_DEN)) return
        val wrong = Wrong(text(x, y), feedback)
        if (x.isInteger && y.isInteger) items += wrong else secondary += wrong
    }

    fun add(x: Int, y: Int, feedback: String) = add(rat(x), rat(y), feedback)

    /** Respaldo ordenado: pares fraccionarios con error típico y luego pares vecinos de la solución. */
    fun fallbacks(x0: Int, y0: Int): List<Wrong> = secondary + listOf(1 to 0, 0 to 1, -1 to 0, 0 to -1, 1 to 1, -1 to -1, 2 to 0, 0 to 2)
        .map { (dx, dy) -> Wrong(text(rat(x0 + dx), rat(y0 + dy)), GENERIC_FEEDBACK) }

    companion object {
        private const val MAX_ABS = 100L
        private const val MAX_DEN = 12L

        /** Texto de una opción: "x = 3, y = -2". */
        fun text(x: Rational, y: Rational): String = "x = $x, y = $y"
    }
}

/**
 * Sistemas lineales 2×2 con solución entera.
 * Nivel 1: x + y = s, x - y = d (suma de ecuaciones).
 * Nivel 2: y = mx + n con ax + by = c (sustitución).
 * Nivel 3: ax + by = c con dx + ey = f (eliminación con multiplicadores).
 */
class LinearSystemGenerator : BaseGenerator(TOPIC_ID) {

    override fun draft(rng: SeededRandom, difficulty: Int): Draft = when (difficulty) {
        1 -> sumAndDifference(rng)
        2 -> substitution(rng)
        else -> elimination(rng)
    }

    private fun basicErrors(pool: PairPool, x0: Int, y0: Int) {
        pool.add(y0, x0, "Intercambiaste los valores: el primero es x y el segundo es y. Revisa cuál variable despejaste.")
        pool.add(x0, -y0, "El valor de x es correcto, pero el signo de y está al revés. Sustituye en las ecuaciones para comprobar.")
        pool.add(-x0, y0, "El valor de y es correcto, pero el signo de x está al revés. Sustituye en las ecuaciones para comprobar.")
    }

    private fun sumAndDifference(rng: SeededRandom): Draft {
        val x0 = rng.intExcept(-8, 8, 0)
        val y0 = rng.intExcept(-8, 8, 0, x0, -x0)
        val s = x0 + y0
        val d = x0 - y0
        val pool = PairPool()
        basicErrors(pool, x0, y0)
        pool.add(s + d, -d, "Al sumar las ecuaciones queda 2x = ${s + d}: falta dividir entre 2 para hallar x.")
        pool.add(d, s - d, "Al restar las ecuaciones queda 2y = ${s - d}: falta dividir entre 2 para hallar y.")
        pool.add(x0, s + x0, "Para hallar y en x + y = $s hay que RESTAR x: y = $s - ${p(x0)}, no sumarlo.")
        return Draft(
            statement = "Resuelve el sistema:\n${combine(1 to "x", 1 to "y")} = $s\n${combine(1 to "x", -1 to "y")} = $d\n" +
                "¿Cuál es la solución?",
            correct = PairPool.text(rat(x0), rat(y0)),
            wrong = pool.items,
            hints = listOf(
                "Un sistema 2×2 busca los valores de x e y que cumplen las dos ecuaciones a la vez. " +
                    "Aquí los coeficientes de y son opuestos (+1 y -1): conviene sumar las ecuaciones.",
                "Suma las ecuaciones miembro a miembro: la y se cancela y queda una ecuación solo con x. " +
                    "Resuélvela y luego sustituye en la primera ecuación para hallar y.",
                "Al sumar queda 2x = ${s + d}, de donde x = $x0. Ahora sustituye ese valor en ${combine(1 to "x", 1 to "y")} = $s.",
            ),
            steps = listOf(
                "Sumamos las dos ecuaciones miembro a miembro; la y se cancela: 2x = $s + ${p(d)} = ${s + d}.",
                "Dividimos entre 2: x = ${s + d} ÷ 2 = $x0.",
                "Sustituimos en la primera ecuación: ${p(x0)} + y = $s, entonces y = $s - ${p(x0)} = $y0.",
                "Comprobación en la segunda ecuación: ${p(x0)} - ${p(y0)} = $d. Es correcto.",
                "Respuesta: x = $x0, y = $y0.",
            ),
            fillers = pool.fallbacks(x0, y0),
        )
    }

    private fun substitution(rng: SeededRandom): Draft {
        val v = rng.search {
            val x0 = intExcept(-6, 6, 0)
            val m = intExcept(-3, 3, 0)
            val n = intExcept(-9, 9, 0)
            val y0 = m * x0 + n
            val a = intExcept(-4, 4, 0)
            val b = intExcept(-4, 4, 0)
            if (y0 == 0 || abs(y0) > 12 || a + b * m == 0) null else SubstitutionData(x0, y0, m, n, a, b)
        }
        val k = v.a + v.b * v.m
        val c = v.a * v.x0 + v.b * v.y0
        val pool = PairPool()
        basicErrors(pool, v.x0, v.y0)
        fun yFrom(x: Rational) = x * v.m + v.n
        val noDistribN = rat(c - v.n) / k
        pool.add(noDistribN, yFrom(noDistribN), "Al sustituir, ${v.b} debe multiplicar a TODO el paréntesis, también a ${v.n}.")
        if (v.a + v.m != 0) {
            val noDistribX = rat(c - v.b * v.n) / (v.a + v.m)
            pool.add(noDistribX, yFrom(noDistribX), "Al sustituir, ${v.b} debe multiplicar también a ${combine(v.m to "x")}.")
        }
        val signSlip = rat(c + v.b * v.n) / k
        pool.add(signSlip, yFrom(signSlip), "Al pasar ${v.b * v.n} al otro lado debe cambiar de signo.")
        pool.add(v.x0, v.m * v.x0 - v.n, "Hallaste bien x, pero al calcular y te equivocaste con el signo de ${v.n}.")
        val eq1 = "y = ${combine(v.m to "x", v.n to "")}"
        val eq2 = combine(v.a to "x", v.b to "y")
        val inner = combine(v.m to "x", v.n to "")
        return Draft(
            statement = "Resuelve el sistema:\n$eq1\n$eq2 = $c\n¿Cuál es la solución?",
            correct = PairPool.text(rat(v.x0), rat(v.y0)),
            wrong = pool.items,
            hints = listOf(
                "Como la primera ecuación ya tiene la y despejada, usa sustitución: " +
                    "reemplaza esa expresión de y en la otra ecuación.",
                "Al sustituir queda una ecuación solo con x. Distribuye, reduce, resuelve x " +
                    "y luego calcula y con la primera ecuación.",
                "Al sustituir y reducir queda ${combine(k to "x")} ${signedTerm(v.b * v.n)} = $c. " +
                    "Despeja x y luego halla y.",
            ),
            steps = listOf(
                "Sustituimos y = $inner en la segunda ecuación: ${Fmt.addScaled(combine(v.a to "x"), v.b, inner)} = $c.",
                "Distribuimos y reducimos: ${combine(k to "x", v.b * v.n to "")} = $c.",
                "Despejamos x: ${combine(k to "x")} = $c - ${p(v.b * v.n)} = ${c - v.b * v.n}, entonces x = ${c - v.b * v.n} ÷ ${p(k)} = ${v.x0}.",
                "Hallamos y con la primera ecuación: y = ${Fmt.mul(v.m, p(v.x0))} ${signedTerm(v.n)} = ${v.y0}.",
                "Comprobación en la segunda ecuación: ${dot(v.a, v.x0, v.b, v.y0)} = $c. Es correcto.",
                "Respuesta: x = ${v.x0}, y = ${v.y0}.",
            ),
            fillers = pool.fallbacks(v.x0, v.y0),
        )
    }

    private class SubstitutionData(val x0: Int, val y0: Int, val m: Int, val n: Int, val a: Int, val b: Int)

    private class EliminationData(
        val x0: Int, val y0: Int,
        val a1: Int, val b1: Int, val a2: Int, val b2: Int,
    )

    private fun elimination(rng: SeededRandom): Draft {
        val v = rng.search {
            val d = EliminationData(
                intExcept(-6, 6, 0), intExcept(-6, 6, 0),
                intExcept(-5, 5, 0), intExcept(-5, 5, 0), intExcept(-5, 5, 0), intExcept(-5, 5, 0),
            )
            val det = d.a1 * d.b2 - d.a2 * d.b1
            val big = lcm(d.b1, d.b2)
            val bothScaled = abs(big / d.b1) >= 2 && abs(big / d.b2) >= 2 // ninguna ecuación queda igual
            if (det == 0 || !bothScaled) null else d
        }
        val c1 = v.a1 * v.x0 + v.b1 * v.y0
        val c2 = v.a2 * v.x0 + v.b2 * v.y0
        val big = lcm(v.b1, v.b2)
        val p1 = big / v.b1
        val q2 = -big / v.b2
        val coefX = p1 * v.a1 + q2 * v.a2
        val rhs = p1 * c1 + q2 * c2
        check(rhs == coefX * v.x0) { "Inconsistencia en la eliminación" }
        val pool = PairPool()
        basicErrors(pool, v.x0, v.y0)
        pool.add(rat(v.x0), (rat(c1) + v.a1 * v.x0) / v.b1, "Al sustituir x en la primera ecuación, ${v.a1 * v.x0} pasa restando: cambia de signo.")
        pool.add((rat(c1) + v.b1 * v.y0) / v.a1, rat(v.y0), "Al sustituir y en la primera ecuación, ${v.b1 * v.y0} pasa restando: cambia de signo.")
        val wrongX = wrongElimination(p1, q2, v, c1, c2)
        if (wrongX != null) {
            pool.add(wrongX, (rat(c1) - wrongX * v.a1) / v.b1, "Tras multiplicar, las ecuaciones se SUMAN porque los coeficientes de y son opuestos; no se restan.")
        }
        if (coefX != 0) {
            val noScaleRhs = rat(c1 + q2 * c2) / coefX
            pool.add(noScaleRhs, (rat(c1) - noScaleRhs * v.a1) / v.b1, "Al multiplicar una ecuación por $p1, TODOS sus términos se multiplican, también el $c1 de la derecha.")
        }
        val e1 = "${combine(v.a1 to "x", v.b1 to "y")} = $c1"
        val e2 = "${combine(v.a2 to "x", v.b2 to "y")} = $c2"
        return Draft(
            statement = "Resuelve el sistema:\n$e1\n$e2\n¿Cuál es la solución?",
            correct = PairPool.text(rat(v.x0), rat(v.y0)),
            wrong = pool.items,
            hints = listOf(
                "Para eliminar una variable, multiplica las ecuaciones por números que dejen opuestos los coeficientes " +
                    "de esa variable; después súmalas.",
                "Para eliminar la y, multiplica la primera ecuación por $p1 y la segunda por $q2. " +
                    "Suma, resuelve x y sustituye en una ecuación para hallar y.",
                "Al multiplicar y sumar queda ${combine(coefX to "x")} = $rhs. Despeja x y luego halla y.",
            ),
            steps = listOf(
                "Para eliminar la y, buscamos el mínimo común múltiplo de ${abs(v.b1)} y ${abs(v.b2)}, que es $big. " +
                    "Multiplicamos la ecuación (1) por $p1 y la (2) por $q2.",
                "Quedan: (1) ${combine(p1 * v.a1 to "x", big to "y")} = ${p1 * c1}; (2) ${combine(q2 * v.a2 to "x", -big to "y")} = ${q2 * c2}.",
                "Sumamos: ${combine(coefX to "x")} = $rhs.",
                "Despejamos x: x = $rhs ÷ ${p(coefX)} = ${v.x0}.",
                "Sustituimos x = ${v.x0} en (1): ${combine(v.b1 to "y")} = $c1 - ${p(v.a1 * v.x0)} = ${c1 - v.a1 * v.x0}, " +
                    "entonces y = ${c1 - v.a1 * v.x0} ÷ ${p(v.b1)} = ${v.y0}.",
                "Comprobación en (2): ${dot(v.a2, v.x0, v.b2, v.y0)} = $c2. Es correcto.",
                "Respuesta: x = ${v.x0}, y = ${v.y0}.",
            ),
            fillers = pool.fallbacks(v.x0, v.y0),
        )
    }

    /** Valor de x si, tras multiplicar, se restan las ecuaciones en lugar de sumarlas. */
    private fun wrongElimination(p1: Int, q2: Int, v: EliminationData, c1: Int, c2: Int): Rational? {
        val den = p1 * v.a1 - q2 * v.a2
        return if (den == 0) null else rat(p1 * c1 - q2 * c2) / den
    }

    companion object {
        const val TOPIC_ID = "sistemas-2x2"
    }
}
