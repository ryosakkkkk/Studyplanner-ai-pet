package pe.studyplanner.aipet.domain.tutor

/**
 * Máximo común divisor (MCD) y mínimo común múltiplo (MCM).
 * Nivel 1: MCD o MCM de dos números. Nivel 2: problema con contexto (cortar en partes iguales o hechos que
 * coinciden), de dos números. Nivel 3: MCD o MCM de tres números, o el otro número cuando se conocen el MCD, el MCM
 * y uno de los números.
 *
 * Todos los enunciados tienen una respuesta entera pequeña y los números se escogen para que ninguno divida al otro.
 */
class McdMcmGenerator : BaseGenerator(TOPIC_ID) {

    private enum class Kind(val acronym: String) { GCD("MCD"), LCM("MCM") }

    /** Contexto de un problema de dos números; el enunciado usa {a} y {b}. */
    private class Context(val kind: Kind, val template: String, val range: IntRange, val maxLcm: Int, val cue: String)

    override fun draft(rng: SeededRandom, difficulty: Int): Draft = when (difficulty) {
        1 -> plainPair(rng)
        2 -> contextPair(rng)
        else -> if (rng.coin()) threeNumbers(rng) else unknownNumber(rng)
    }

    // ---- utilidades ----

    private fun pool() = WrongPool(valid = { it.isInteger && it.num in 1..MAX_OPTION })

    private fun primeFactors(n: Int): Map<Int, Int> {
        val out = linkedMapOf<Int, Int>()
        var rest = n
        var p = 2
        while (p * p <= rest) {
            while (rest % p == 0) {
                out[p] = (out[p] ?: 0) + 1
                rest /= p
            }
            p++
        }
        if (rest > 1) out[rest] = (out[rest] ?: 0) + 1
        return out
    }

    private fun smallestPrime(n: Int): Int = primeFactors(n).keys.first()

    private fun productText(f: Map<Int, Int>): String =
        f.entries.joinToString(" · ") { (p, e) -> if (e == 1) "$p" else "$p^$e" }

    private fun valueOf(f: Map<Int, Int>): Int = f.entries.fold(1) { acc, (p, e) -> acc * pow(p, e) }

    private fun pow(base: Int, exp: Int): Int {
        var r = 1
        repeat(exp) { r *= base }
        return r
    }

    private fun describe(n: Int): String {
        val f = primeFactors(n)
        return if (f.size == 1 && f.values.single() == 1) "$n es primo" else "$n = ${productText(f)}"
    }

    private fun radical(n: Int): Int = primeFactors(n).keys.fold(1) { acc, p -> acc * p }

    /** Factores comunes con su menor exponente, o todos con su mayor exponente. */
    private fun combine(numbers: List<Int>, kind: Kind): Map<Int, Int> {
        val all = numbers.map { primeFactors(it) }
        val primes = all.flatMap { it.keys }.toSortedSet()
        val out = linkedMapOf<Int, Int>()
        for (p in primes) {
            val exps = all.map { it[p] ?: 0 }
            val e = if (kind == Kind.GCD) exps.min() else exps.max()
            if (e > 0) out[p] = e
        }
        return out
    }

    private fun gcd3(a: Int, b: Int, c: Int) = gcd(gcd(a, b), c)

    private fun lcm3(a: Int, b: Int, c: Int) = lcm(lcm(a, b), c)

    private fun answerOf(kind: Kind, numbers: List<Int>): Int =
        numbers.reduce { x, y -> if (kind == Kind.GCD) gcd(x, y) else lcm(x, y) }

    private fun joinNumbers(numbers: List<Int>): String =
        numbers.dropLast(1).joinToString(", ") + " y " + numbers.last()

    // ---- errores típicos ----

    private fun pairErrors(pool: WrongPool, kind: Kind, a: Int, b: Int) {
        val g = gcd(a, b)
        val l = lcm(a, b)
        val lo = minOf(a, b)
        val hi = maxOf(a, b)
        if (kind == Kind.GCD) {
            pool.add(l, "Calculaste el MCM. El MCD es el MAYOR número que divide a $a y a $b; el MCM es el menor múltiplo que comparten.")
            pool.add(a * b, "Multiplicaste los dos números. El producto es igual a MCD × MCM, no al MCD.")
            pool.add(hi - lo, "Restaste los números. La diferencia no es el MCD: el MCD es el mayor número que divide a los dos.")
            if (radical(g) != g) {
                pool.add(radical(g), "Multiplicaste los factores comunes sin sus exponentes. Cada factor común se toma con su MENOR exponente.")
            }
            pool.add(if (g % smallestPrime(g) == 0 && g != smallestPrime(g)) g / smallestPrime(g) else 1,
                "Ese número divide a $a y a $b, pero no es el mayor que lo hace. El MCD es el MAYOR divisor común.")
            pool.add(lo, "Tomaste el menor de los números. El MCD debe dividir a los dos, y $lo no divide a $hi.")
            val up = g * smallestPrime(g)
            val failing = if (a % up != 0) a else b
            pool.add(up, "Ese número no divide a ambos: $failing no es divisible entre $up. El MCD debe dividir exactamente a los dos.")
        } else {
            pool.add(g, "Calculaste el MCD. El MCM es el MENOR múltiplo común: debe ser múltiplo de $a y de $b.")
            pool.add(a * b, "Multiplicaste los dos números. El producto es un múltiplo común, pero no el menor: hay que dividirlo entre el MCD ($g).")
            pool.add(a + b, "Sumaste los números. El MCM no es la suma: es el menor número que es múltiplo de ambos.")
            pool.add(2 * l, "Ese número es múltiplo de $a y de $b, pero no es el menor. Busca el MENOR múltiplo común.")
            pool.add(hi, "Tomaste el mayor de los números. El MCM debe ser múltiplo de los dos, y $hi no es múltiplo de $lo.")
            if (radical(l) != l) {
                pool.add(radical(l), "Multiplicaste los factores sin sus exponentes. Para el MCM se toman todos los factores con su MAYOR exponente.")
            }
            val down = l / smallestPrime(l)
            val failing = if (down % a != 0) a else b
            pool.add(down, "Ese número no es múltiplo de ambos: $down no es divisible entre $failing. El MCM debe ser múltiplo de los dos.")
        }
    }

    // ---- constructor de borradores para dos números ----

    private fun pairDraft(statement: String, kind: Kind, a: Int, b: Int, cue: String?): Draft {
        val g = gcd(a, b)
        val l = lcm(a, b)
        val answer = if (kind == Kind.GCD) g else l
        val pool = pool()
        pairErrors(pool, kind, a, b)
        val fa = primeFactors(a)
        val fb = primeFactors(b)
        val combined = combine(listOf(a, b), kind)
        val combinedText = productText(combined)
        val equal = if (combinedText == "$answer") "" else " = $answer"
        val hints = if (kind == Kind.GCD) {
            listOf(
                "El MCD es el MAYOR número que divide exactamente a todos los números dados." + (cue?.let { " $it" } ?: ""),
                "Descompón cada número en factores primos y multiplica solo los factores comunes, cada uno con su menor exponente.",
                "Descomposición: ${describe(a)} y ${describe(b)}. Toma los factores comunes con su menor exponente y multiplícalos.",
            )
        } else {
            listOf(
                "El MCM es el MENOR número que es múltiplo de todos los números dados." + (cue?.let { " $it" } ?: ""),
                "Descompón cada número en factores primos y multiplica todos los factores, comunes y no comunes, cada uno con su mayor exponente.",
                "Descomposición: ${describe(a)} y ${describe(b)}. Toma todos los factores con su mayor exponente y multiplícalos.",
            )
        }
        val steps = mutableListOf<String>()
        if (cue != null) steps += cue
        steps += "Descomponemos en factores primos: ${describe(a)} y ${describe(b)}."
        if (kind == Kind.GCD) {
            steps += "El MCD es el producto de los factores comunes, cada uno con su MENOR exponente: $combinedText$equal."
            steps += "Comprobación: $a ÷ $g = ${a / g} y $b ÷ $g = ${b / g}. Las dos divisiones son exactas y los cocientes no tienen otro divisor común aparte de 1."
        } else {
            steps += "El MCM es el producto de todos los factores, comunes y no comunes, cada uno con su MAYOR exponente: $combinedText$equal."
            steps += "Comprobación: $l ÷ $a = ${l / a} y $l ÷ $b = ${l / b}. Las dos divisiones son exactas, así que $l es múltiplo de ambos."
        }
        steps += "Respuesta: $answer."
        return Draft(
            statement = statement,
            correct = answer.toString(),
            wrong = pool.items,
            hints = hints,
            steps = steps,
            fillers = fillers(answer),
        ).also { check(fa.isNotEmpty() && fb.isNotEmpty() && valueOf(fa) == a && valueOf(fb) == b) }
    }

    private fun fillers(correct: Int): List<Wrong> =
        listOf(1, -1, 2, -2, 3, -3, 4, -4, 6, -6).map { correct + it }.filter { it in 1..MAX_OPTION }
            .map { Wrong(it.toString(), GENERIC_FEEDBACK) }

    /** Dos números sin que uno divida al otro y con MCD de al menos 2; el MCM no pasa de [maxLcm]. */
    private fun pair(rng: SeededRandom, range: IntRange, maxLcm: Int): Pair<Int, Int> = rng.search {
        val x = int(range.first, range.last)
        val y = int(range.first, range.last)
        val lo = minOf(x, y)
        val hi = maxOf(x, y)
        if (lo == hi || hi % lo == 0 || gcd(lo, hi) < 2 || lcm(lo, hi) > maxLcm) null else lo to hi
    }

    // ---- nivel 1 ----

    private fun plainPair(rng: SeededRandom): Draft {
        val kind = if (rng.coin()) Kind.GCD else Kind.LCM
        val (a, b) = pair(rng, 8..48, 360)
        return pairDraft("Halla el ${kind.acronym} de $a y $b.", kind, a, b, null)
    }

    // ---- nivel 2 ----

    private fun contextPair(rng: SeededRandom): Draft {
        val ctx = rng.pick(CONTEXTS)
        val (a, b) = pair(rng, ctx.range, ctx.maxLcm)
        return pairDraft(ctx.template.replace("{a}", "$a").replace("{b}", "$b"), ctx.kind, a, b, ctx.cue)
    }

    // ---- nivel 3 ----

    private fun threeNumbers(rng: SeededRandom): Draft {
        val kind = if (rng.coin()) Kind.GCD else Kind.LCM
        val (a, b, c) = rng.search {
            val a = int(6, 48)
            val b = int(6, 48)
            val c = int(6, 48)
            val ok = a != b && b != c && a != c &&
                if (kind == Kind.GCD) gcd3(a, b, c) >= 2 && gcd(a, b) != gcd3(a, b, c)
                else lcm3(a, b, c) <= 720 && lcm(a, b) != lcm3(a, b, c) && gcd(a, b) >= 2
            if (ok) listOf(a, b, c) else null
        }
        val numbers = listOf(a, b, c)
        val answer = answerOf(kind, numbers)
        val pool = pool()
        if (kind == Kind.GCD) {
            val note = "Calculaste el MCD de solo dos de los números. El MCD debe dividir a los TRES números."
            pool.add(gcd(a, b), note)
            pool.add(gcd(b, c), note)
            pool.add(gcd(a, c), note)
            pool.add(lcm3(a, b, c), "Calculaste el MCM. El MCD es el MAYOR número que divide a $a, a $b y a $c.")
            if (radical(answer) != answer) {
                pool.add(radical(answer), "Multiplicaste los factores comunes sin sus exponentes. Cada factor común se toma con su MENOR exponente.")
            }
            pool.add(1, "El 1 divide a todos los números, pero no es el mayor divisor común de $a, $b y $c.")
            pool.add(minOf(a, b, c), "Tomaste el menor de los números. El MCD debe dividir a los tres, y ese número no divide a todos.")
        } else {
            val note = "Calculaste el MCM de solo dos de los números. El MCM debe ser múltiplo de los TRES números."
            pool.add(lcm(a, b), note)
            pool.add(lcm(b, c), note)
            pool.add(lcm(a, c), note)
            pool.add(a * b * c, "Multiplicaste los tres números. El producto es un múltiplo común, pero casi nunca el menor.")
            pool.add(gcd3(a, b, c), "Calculaste el MCD. El MCM es el MENOR número que es múltiplo de $a, de $b y de $c.")
            pool.add(2 * answer, "Ese número es múltiplo de los tres, pero no es el menor. Busca el MENOR múltiplo común.")
            pool.add(answer / smallestPrime(answer), "Ese número no es múltiplo de los tres. El MCM debe ser múltiplo de $a, de $b y de $c.")
        }
        val combined = combine(numbers, kind)
        val combinedText = productText(combined)
        val equal = if (combinedText == "$answer") "" else " = $answer"
        val descriptions = numbers.joinToString("; ") { describe(it) }
        val hints = if (kind == Kind.GCD) {
            listOf(
                "El MCD de tres números es el MAYOR número que divide a los tres. Los factores comunes son los que aparecen en los tres números.",
                "Descompón cada número en factores primos y multiplica solo los factores que están en los tres, cada uno con su menor exponente.",
                "Descomposición: $descriptions. Busca los factores que se repiten en los tres y toma el menor exponente.",
            )
        } else {
            listOf(
                "El MCM de tres números es el MENOR número que es múltiplo de los tres.",
                "Descompón cada número en factores primos y multiplica todos los factores que aparezcan, cada uno con su mayor exponente.",
                "Descomposición: $descriptions. Toma todos los factores distintos, cada uno con su mayor exponente, y multiplícalos.",
            )
        }
        val steps = if (kind == Kind.GCD) {
            listOf(
                "Descomponemos en factores primos: $descriptions.",
                "El MCD es el producto de los factores que están en los tres números, cada uno con su MENOR exponente: $combinedText$equal.",
                "Comprobación: $a ÷ $answer = ${a / answer}, $b ÷ $answer = ${b / answer} y $c ÷ $answer = ${c / answer}. Las tres divisiones son exactas.",
                "Respuesta: $answer.",
            )
        } else {
            listOf(
                "Descomponemos en factores primos: $descriptions.",
                "El MCM es el producto de todos los factores, cada uno con su MAYOR exponente: $combinedText$equal.",
                "Comprobación: $answer ÷ $a = ${answer / a}, $answer ÷ $b = ${answer / b} y $answer ÷ $c = ${answer / c}. Las tres divisiones son exactas.",
                "Respuesta: $answer.",
            )
        }
        return Draft(
            statement = "Halla el ${kind.acronym} de ${joinNumbers(numbers)}.",
            correct = answer.toString(),
            wrong = pool.items,
            hints = hints,
            steps = steps,
            fillers = fillers(answer),
        )
    }

    /** Se conocen el MCD, el MCM y uno de los números: se pide el otro con a × b = MCD × MCM. */
    private fun unknownNumber(rng: SeededRandom): Draft {
        val (g, m, n) = rng.search {
            val g = int(2, 9)
            val m = int(2, 9)
            val n = int(2, 9)
            if (m == n || gcd(m, n) != 1 || g * m * n > 600) null else listOf(g, m, n)
        }
        val a = g * m
        val b = g * n
        val l = g * m * n
        val product = g * l
        val pool = pool()
        pool.add(l / a, "Dividiste el MCM entre el número dado. Falta el MCD: el producto de los dos números es MCD × MCM, o sea $a × x = $g × $l.")
        pool.add(l - a, "Restaste el número dado del MCM. No hay una relación de resta: se cumple que el producto de los dos números es MCD × MCM.")
        pool.add(l / g, "Dividiste el MCM entre el MCD. Eso no da el otro número: usa $a × x = $g × $l.")
        pool.add(a * g, "Multiplicaste el número dado por el MCD. Debes usar $a × x = MCD × MCM = $g × $l.")
        pool.add(product, "Multiplicaste el MCD por el MCM, pero falta dividir entre el número dado $a.")
        pool.add(l + g - a, "Los números no se suman ni se restan con el MCD y el MCM: se usa que el producto de los dos números es MCD × MCM.")
        pool.add(b + g, "Casi: revisa la división $product ÷ $a. El otro número debe tener MCD $g y MCM $l con el número dado.")
        return Draft(
            statement = "El MCD de dos números es $g y su MCM es $l. Si uno de los números es $a, ¿cuál es el otro?",
            correct = b.toString(),
            wrong = pool.items,
            hints = listOf(
                "Para dos números cualesquiera se cumple: el producto de los dos números es igual al producto de su MCD por su MCM.",
                "Escribe $a × x = $g × $l, donde x es el número que falta, y despeja x.",
                "Calcula el producto $g × $l y divídelo entre $a.",
            ),
            steps = listOf(
                "Para dos números se cumple: producto de los números = MCD × MCM.",
                "Con los datos: $a × x = $g × $l = $product.",
                "Despejamos x: x = $product ÷ $a = $b.",
                "Comprobación: ${describe(a)} y ${describe(b)}. Su MCD es $g y su MCM es $l.",
                "Respuesta: $b.",
            ),
            fillers = fillers(b),
        )
    }

    companion object {
        const val TOPIC_ID = "mcd-mcm"
        private const val MAX_OPTION = 5000

        private val CONTEXTS = listOf(
            Context(
                Kind.GCD,
                "Una cinta de {a} cm y otra de {b} cm se cortan en trozos de igual longitud, lo más largos posible y sin que sobre nada. ¿Cuántos centímetros mide cada trozo?",
                24..96, 10000,
                "Cortar en trozos iguales, lo más largos posible y sin que sobre nada, es dividir las dos medidas con el mayor divisor común: se pide el MCD.",
            ),
            Context(
                Kind.GCD,
                "Se reparten {a} manzanas y {b} naranjas en bolsas iguales, con la mayor cantidad posible de bolsas y sin que sobre fruta. ¿Cuántas bolsas se arman?",
                24..96, 10000,
                "Repartir en grupos iguales, con la mayor cantidad de grupos y sin que sobre nada, es buscar el mayor divisor común: se pide el MCD.",
            ),
            Context(
                Kind.LCM,
                "Dos faros emiten un destello al mismo tiempo. El primero destella cada {a} segundos y el segundo cada {b} segundos. ¿Cada cuántos segundos destellan juntos?",
                6..30, 360,
                "Dos hechos que se repiten cada cierto tiempo coinciden en el menor tiempo que es múltiplo de los dos períodos: se pide el MCM.",
            ),
            Context(
                Kind.LCM,
                "Dos buses parten juntos de un terminal. Uno sale cada {a} minutos y el otro cada {b} minutos. ¿Dentro de cuántos minutos volverán a partir juntos?",
                6..30, 360,
                "Volver a coincidir es llegar al menor tiempo que es múltiplo de los dos períodos: se pide el MCM.",
            ),
        )
    }
}
