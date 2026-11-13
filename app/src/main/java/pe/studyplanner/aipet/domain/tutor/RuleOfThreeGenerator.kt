package pe.studyplanner.aipet.domain.tutor

/**
 * Regla de tres simple, directa e inversa.
 * Nivel 1: directa con valor unitario entero. Nivel 2: inversa. Nivel 3: mezcla de ambas con números mayores,
 * donde el reto es reconocer el tipo de relación.
 */
class RuleOfThreeGenerator : BaseGenerator(TOPIC_ID) {

    /** Tipo de relación entre las magnitudes. */
    private enum class Kind { DIRECT, INVERSE }

    /**
     * Contexto de un problema. El enunciado usa [a], [b] y [c] en ese orden: "a" corresponde a "b" y se pide
     * el valor que corresponde a "c". [qa] y [qb] son las magnitudes en lenguaje natural para las pistas.
     */
    private class Context(
        val kind: Kind,
        val template: String,
        val qa: String,
        val qb: String,
        val unit: (Int) -> String,
        val bRange: IntRange,
    )

    private class Problem(val ctx: Context, val a: Int, val b: Int, val c: Int, val x: Int)

    override fun draft(rng: SeededRandom, difficulty: Int): Draft {
        val problem = when (difficulty) {
            1 -> directProblem(rng, simple = true)
            2 -> inverseProblem(rng, big = false)
            else -> if (rng.coin()) directProblem(rng, simple = false) else inverseProblem(rng, big = true)
        }
        return build(problem)
    }

    private fun directProblem(rng: SeededRandom, simple: Boolean): Problem {
        val ctx = rng.pick(DIRECT)
        val (a, b, c, x) = rng.search {
            if (simple) {
                val a = int(2, 9)
                val u = int(ctx.bRange.first, ctx.bRange.last)
                val c = intExcept(2, 12, a)
                listOf(a, a * u, c, c * u)
            } else {
                val m = int(2, 4)
                val a1 = int(2, 7)
                val c1 = int(2, 7)
                val t = int(ctx.bRange.first, ctx.bRange.last)
                if (gcd(a1, c1) != 1 || a1 == c1) null else listOf(m * a1, a1 * t, m * c1, c1 * t)
            }
        }
        return Problem(ctx, a, b, c, x)
    }

    private fun inverseProblem(rng: SeededRandom, big: Boolean): Problem {
        val ctx = rng.pick(INVERSE)
        val (a, b, c, x) = rng.search {
            val a = int(ctx.bRange.first, ctx.bRange.last)
            val b = if (big) int(6, 24) else int(2, 12)
            val total = a * b
            val options = (2..total.coerceAtMost(MAX_C))
                .filter { total % it == 0 && it != a && total / it >= 2 && it * 3 >= a && it <= a * 3 }
            if (options.isEmpty()) null else {
                val c = pick(options)
                listOf(a, b, c, total / c)
            }
        }
        return Problem(ctx, a, b, c, x)
    }

    private fun build(pr: Problem): Draft {
        val ctx = pr.ctx
        val direct = ctx.kind == Kind.DIRECT
        val pool = WrongPool(
            format = { ctx.unit(it.toIntExact()) },
            valid = { it.isInteger && it.signum > 0 && it.num <= MAX_VALUE && it.num >= 1 },
        )
        val (a, b, c) = Triple(pr.a, pr.b, pr.c)
        if (direct) addDirectErrors(pool, ctx, a, b, c) else addInverseErrors(pool, ctx, a, b, c)
        val statement = ctx.template.replace("{a}", "$a").replace("{b}", "$b").replace("{c}", "$c")
        val hints = listOf(
            "Pregúntate: si ${ctx.qa} aumenta, ¿${ctx.qb} aumenta o disminuye? " +
                "Eso te dice si la relación es directa o inversa.",
            if (direct) {
                "Aquí la relación es directa: si ${ctx.qa} aumenta, ${ctx.qb} también aumenta. " +
                    "Se multiplica en cruz: $a corresponde a $b, y $c corresponde a x."
            } else {
                "Aquí la relación es inversa: si ${ctx.qa} aumenta, ${ctx.qb} disminuye. " +
                    "Los productos se mantienen iguales: $a × $b = $c × x."
            },
            if (direct) "Plantea x = ($b × $c) ÷ $a y calcula."
            else "Plantea x = ($a × $b) ÷ $c y calcula.",
        )
        val steps = if (direct) directSteps(pr) else inverseSteps(pr)
        return Draft(
            statement = statement,
            correct = ctx.unit(pr.x),
            wrong = pool.items,
            hints = hints,
            steps = steps,
            fillers = listOf(1, -1, 2, -2, 3, -3, 4, -4).map { pr.x + it }.filter { it >= 1 }
                .map { Wrong(ctx.unit(it), REVIEW_FEEDBACK) },
        )
    }

    private fun addDirectErrors(pool: WrongPool, ctx: Context, a: Int, b: Int, c: Int) {
        if ((a * b) % c == 0) {
            pool.add(a * b / c, "Trataste la relación como inversa. Aquí, si ${ctx.qa} aumenta, ${ctx.qb} también aumenta: es directa.")
        }
        pool.add(b + (c - a), "Sumaste la diferencia ($c - $a) en lugar de usar proporcionalidad. En una regla de tres se multiplica y se divide.")
        pool.add(b - (c - a), "Restaste la diferencia ($c - $a): la proporcionalidad no se resuelve sumando ni restando.")
        pool.add(b * c, "Te faltó dividir entre $a: x = ($b × $c) ÷ $a.")
        if (b % a == 0) {
            pool.add(b / a, "Hallaste el valor por unidad ($b ÷ $a), pero falta multiplicarlo por $c.")
        }
        if ((a * c) % b == 0) {
            pool.add(a * c / b, "Invertiste la razón: debes multiplicar $b por $c y dividir entre $a, no al revés.")
        }
        pool.add(a * c, "Multiplicaste $a × $c, las dos cantidades de la misma magnitud. Se multiplican $b y $c, y el resultado se divide entre $a.")
    }

    private fun addInverseErrors(pool: WrongPool, ctx: Context, a: Int, b: Int, c: Int) {
        if ((b * c) % a == 0) {
            pool.add(b * c / a, "Trataste la relación como directa. Aquí, si ${ctx.qa} aumenta, ${ctx.qb} disminuye: es inversa.")
        }
        pool.add(b - (c - a), "Restaste la diferencia ($c - $a): en una relación inversa los PRODUCTOS se mantienen iguales.")
        pool.add(b + (c - a), "Sumaste la diferencia ($c - $a): en una relación inversa se igualan los productos $a × $b = $c × x.")
        pool.add(a * b, "Hallaste el producto $a × $b, pero falta dividirlo entre $c.")
        if ((a * c) % b == 0) {
            pool.add(a * c / b, "Mezclaste los datos: en una relación inversa se igualan los productos $a × $b = $c × x, y de ahí x = ($a × $b) ÷ $c.")
        }
        pool.add(b * c, "Multiplicaste $b por $c; en una relación inversa se iguala $a × $b con $c × x.")
        pool.add(a * c, "Multiplicaste $a × $c, las dos cantidades de la misma magnitud. En una relación inversa se iguala $a × $b = $c × x.")
    }

    private fun directSteps(pr: Problem): List<String> {
        val ctx = pr.ctx
        return listOf(
            "Si ${ctx.qa} aumenta, ${ctx.qb} también aumenta: la relación es directa.",
            "Ordenamos los datos: ${pr.a} corresponde a ${pr.b}, y ${pr.c} corresponde a x.",
            "Multiplicamos en cruz: x = (${pr.b} × ${pr.c}) ÷ ${pr.a} = ${pr.b * pr.c} ÷ ${pr.a} = ${pr.x}.",
            "Comprobación: ${pr.b} × ${pr.c} = ${pr.b * pr.c} y ${pr.a} × ${pr.x} = ${pr.a * pr.x}. Son iguales.",
            "Respuesta: ${ctx.unit(pr.x)}.",
        )
    }

    private fun inverseSteps(pr: Problem): List<String> {
        val ctx = pr.ctx
        return listOf(
            "Si ${ctx.qa} aumenta, ${ctx.qb} disminuye: la relación es inversa.",
            "En una relación inversa el producto se mantiene: ${pr.a} × ${pr.b} = ${pr.c} × x.",
            "Despejamos: x = (${pr.a} × ${pr.b}) ÷ ${pr.c} = ${pr.a * pr.b} ÷ ${pr.c} = ${pr.x}.",
            "Comprobación: ${pr.a} × ${pr.b} = ${pr.a * pr.b} y ${pr.c} × ${pr.x} = ${pr.c * pr.x}. Son iguales.",
            "Respuesta: ${ctx.unit(pr.x)}.",
        )
    }

    companion object {
        const val TOPIC_ID = "regla-de-tres"
        private const val MAX_C = 60
        private const val MAX_VALUE = 3000L

        /** "1 día" en singular y "n días" en plural. */
        private fun count(n: Int, one: String, many: String): String = if (n == 1) "1 $one" else "$n $many"

        private const val REVIEW_FEEDBACK =
            "Ese valor no cumple la proporción. Decide si la relación es directa (se mantiene el cociente) " +
                "o inversa (se mantiene el producto) y comprueba tu resultado."

        private val DIRECT = listOf(
            Context(
                Kind.DIRECT, "Si {a} cuadernos cuestan S/ {b}, ¿cuánto costarán {c} cuadernos?",
                "el número de cuadernos", "el costo", { "S/ $it" }, 2..8,
            ),
            Context(
                Kind.DIRECT, "Un auto recorre {b} km en {a} horas a velocidad constante. ¿Cuántos kilómetros recorre en {c} horas?",
                "el tiempo de viaje", "la distancia recorrida", { "$it km" }, 30..80,
            ),
            Context(
                Kind.DIRECT, "Una máquina produce {b} botellas en {a} minutos. ¿Cuántas botellas produce en {c} minutos?",
                "el tiempo de trabajo", "el número de botellas", { count(it, "botella", "botellas") }, 5..25,
            ),
            Context(
                Kind.DIRECT, "Con {a} kg de harina se preparan {b} panes. ¿Cuántos panes se preparan con {c} kg de harina?",
                "la cantidad de harina", "el número de panes", { count(it, "pan", "panes") }, 8..20,
            ),
        )

        private val INVERSE = listOf(
            Context(
                Kind.INVERSE, "{a} obreros terminan una obra en {b} días. ¿En cuántos días la terminarán {c} obreros, trabajando al mismo ritmo?",
                "el número de obreros", "el número de días necesarios", { count(it, "día", "días") }, 3..12,
            ),
            Context(
                Kind.INVERSE, "Un ciclista que va a {a} km/h tarda {b} horas en llegar a su destino. ¿Cuántas horas tardará si va a {c} km/h?",
                "la velocidad", "el tiempo de viaje", { count(it, "hora", "horas") }, 6..24,
            ),
            Context(
                Kind.INVERSE, "{a} grifos iguales llenan un tanque en {b} horas. ¿En cuántas horas lo llenarán {c} grifos?",
                "el número de grifos", "el tiempo de llenado", { count(it, "hora", "horas") }, 2..10,
            ),
            Context(
                Kind.INVERSE, "Una ración de arroz alcanza para {a} personas durante {b} días. ¿Durante cuántos días alcanzará para {c} personas?",
                "el número de personas", "la duración del arroz", { count(it, "día", "días") }, 4..18,
            ),
        )
    }
}
