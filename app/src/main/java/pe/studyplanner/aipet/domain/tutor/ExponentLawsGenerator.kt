package pe.studyplanner.aipet.domain.tutor

import pe.studyplanner.aipet.domain.tutor.Fmt.power

/**
 * Leyes de exponentes con valor numérico exacto.
 * Nivel 1: producto y cociente de potencias de igual base.
 * Nivel 2: potencia de una potencia y potencia de un producto.
 * Nivel 3: exponentes negativos y exponente cero.
 */
class ExponentLawsGenerator : BaseGenerator(TOPIC_ID) {

    override fun draft(rng: SeededRandom, difficulty: Int): Draft = when (difficulty) {
        1 -> if (rng.coin()) productQuotient(rng) else quotientProduct(rng)
        2 -> when (rng.below(3)) {
            0 -> powerOfPower(rng)
            1 -> powerOfProduct(rng)
            else -> powerOfPowerBoth(rng)
        }
        else -> when (rng.below(4)) {
            0 -> negativeExponents(rng)
            1 -> negativeBaseInverse(rng)
            2 -> negativeFractionBase(rng)
            else -> zeroExponent(rng)
        }
    }

    // ---- utilidades ----

    /** Potencia exacta; admite exponentes negativos. */
    private fun pw(base: Int, exp: Int): Rational {
        var r = Rational.ONE
        repeat(kotlin.math.abs(exp)) { r *= rat(base) }
        return if (exp < 0) Rational.ONE / r else r
    }

    private fun pw(base: Rational, exp: Int): Rational {
        var r = Rational.ONE
        repeat(kotlin.math.abs(exp)) { r *= base }
        return if (exp < 0) Rational.ONE / r else r
    }

    /** Verdadero si |base|^exp no supera [MAX_ABS]; evita desbordar Long al calcular potencias grandes. */
    private fun powerFits(base: Int, exp: Int): Boolean {
        val b = kotlin.math.abs(base)
        if (b <= 1) return true
        var v = 1L
        repeat(exp) {
            v *= b
            if (v > MAX_ABS) return false
        }
        return true
    }

    /** Mayor exponente e con base^e <= [limit]. */
    private fun maxExp(base: Int, limit: Int = VALUE_LIMIT): Int {
        var e = 0
        var v = 1
        while (v * base <= limit) { v *= base; e++ }
        return e
    }

    /** Entre paréntesis si es fracción: "÷ (1/16)" y no "÷ 1/16", que se leería (a ÷ 1) / 16. */
    private fun grouped(r: Rational): String = if (r.isInteger) r.toString() else "($r)"

    /** Decimal exacto con "=" o aproximado con "≈", según el redondeo a 4 cifras sea exacto o no. */
    private fun decimalText(r: Rational): String {
        val exact = (r.num * 10000) % r.den == 0L
        return (if (exact) "= " else "≈ ") + Fmt.decimal(r.toDouble(), 4)
    }

    private fun newPool(preferIntegers: Boolean) = WrongPool(
        valid = { it.isSane(MAX_ABS, MAX_DEN) },
        preferIntegers = preferIntegers,
    )

    private fun exponentOnly(pool: WrongPool, e: Int) =
        pool.add(e, "Hallaste bien el exponente, pero falta calcular la potencia: el resultado es la base elevada a ese exponente.")

    private fun baseTimesExponent(pool: WrongPool, base: Int, e: Int) =
        pool.add(base * e, "Multiplicaste la base por el exponente. Elevar a una potencia es multiplicar la base por sí misma varias veces.")

    private fun build(pool: WrongPool, statement: String, correct: Rational, hints: List<String>, steps: List<String>) =
        Draft(
            statement = "Calcula el valor de:\n$statement",
            correct = Fmt.rational(correct),
            wrong = pool.items,
            hints = hints,
            steps = steps,
            fillers = pool.fallbacks(correct),
        )

    // ---- nivel 1 ----

    private fun productQuotient(rng: SeededRandom): Draft {
        val base = rng.pick(BASES)
        val cap = maxExp(base)
        val maxTerm = maxExp(base, TERM_LIMIT)
        val (m, n, p) = rng.search {
            val m = int(2, maxTerm)
            val n = int(2, maxTerm)
            val p = int(2, maxTerm)
            if (m + n - p < 2 || m + n - p > cap || pw(base, m + n).toDouble() > PRODUCT_LIMIT) null else listOf(m, n, p)
        }
        val e = m + n - p
        val value = pw(base, e)
        val pool = newPool(preferIntegers = true)
        pool.add(pw(base, m + n + p), "En el cociente los exponentes se RESTAN, no se suman.")
        pool.add(pw(base, m * n - p), "Al multiplicar potencias de igual base los exponentes se SUMAN (no se multiplican).")
        pool.add(pw(base, m + n), "Te faltó dividir entre $base^$p: al dividir potencias de igual base se restan los exponentes.")
        exponentOnly(pool, e)
        baseTimesExponent(pool, base, e)
        pool.add(pw(base, m - n - p), "Al multiplicar $base^$m por $base^$n los exponentes se suman: $m + $n, no $m - $n.")
        return build(
            pool,
            "(${power(base, m)} · ${power(base, n)}) ÷ ${power(base, p)}",
            value,
            hints = listOf(
                "Con igual base: al multiplicar potencias se suman los exponentes y al dividirlas se restan.",
                "Primero resuelve el paréntesis (suma los exponentes) y luego divide (resta el exponente del denominador).",
                "El paréntesis da $base^($m + $n). Al dividir entre $base^$p queda $base^($m + $n - $p).",
            ),
            steps = listOf(
                "Multiplicamos potencias de igual base: se suman los exponentes. ${power(base, m)} · ${power(base, n)} = $base^($m + $n) = ${power(base, m + n)}.",
                "Dividimos potencias de igual base: se restan los exponentes. ${power(base, m + n)} ÷ ${power(base, p)} = $base^(${m + n} - $p) = ${power(base, e)}.",
                "Calculamos la potencia: ${power(base, e)} = $value.",
                "Comprobación sin usar las leyes: ${power(base, m)} = ${pw(base, m)}, ${power(base, n)} = ${pw(base, n)}, " +
                    "${power(base, p)} = ${pw(base, p)}; (${pw(base, m)} · ${pw(base, n)}) ÷ ${pw(base, p)} = " +
                    "${pw(base, m) * pw(base, n)} ÷ ${pw(base, p)} = $value.",
                "Respuesta: $value.",
            ),
        )
    }

    private fun quotientProduct(rng: SeededRandom): Draft {
        val base = rng.pick(BASES)
        val cap = maxExp(base)
        val maxTerm = maxExp(base, TERM_LIMIT).coerceAtLeast(3)
        val (m, n, p) = rng.search {
            val m = int(3, maxTerm)
            val n = int(2, m - 1)
            val p = int(2, maxTerm)
            if (m - n + p > cap) null else listOf(m, n, p)
        }
        val e = m - n + p
        val value = pw(base, e)
        val pool = newPool(preferIntegers = true)
        pool.add(pw(base, m - n - p), "Después de dividir, el resultado se MULTIPLICA por $base^$p: los exponentes se suman, no se restan.")
        pool.add(pw(base, m + n + p), "En el cociente los exponentes se RESTAN, no se suman.")
        pool.add(pw(base, m - n), "Te faltó multiplicar por $base^$p: al multiplicar potencias de igual base se suman los exponentes.")
        pool.add(pw(base, m * p - n), "Al multiplicar potencias de igual base los exponentes se SUMAN, no se multiplican.")
        exponentOnly(pool, e)
        baseTimesExponent(pool, base, e)
        return build(
            pool,
            "(${power(base, m)} ÷ ${power(base, n)}) · ${power(base, p)}",
            value,
            hints = listOf(
                "Con igual base: al dividir potencias se restan los exponentes y al multiplicarlas se suman.",
                "Resuelve primero el paréntesis (resta de exponentes) y luego multiplica por la última potencia (suma de exponentes).",
                "El paréntesis da $base^($m - $n). Al multiplicar por $base^$p queda $base^($m - $n + $p).",
            ),
            steps = listOf(
                "Dividimos potencias de igual base: se restan los exponentes. ${power(base, m)} ÷ ${power(base, n)} = $base^($m - $n) = ${power(base, m - n)}.",
                "Multiplicamos potencias de igual base: se suman los exponentes. ${power(base, m - n)} · ${power(base, p)} = $base^(${m - n} + $p) = ${power(base, e)}.",
                "Calculamos la potencia: ${power(base, e)} = $value.",
                "Comprobación sin usar las leyes: ${power(base, m)} = ${pw(base, m)}, ${power(base, n)} = ${pw(base, n)}, " +
                    "${power(base, p)} = ${pw(base, p)}; (${pw(base, m)} ÷ ${pw(base, n)}) · ${pw(base, p)} = " +
                    "${pw(base, m) / pw(base, n)} · ${pw(base, p)} = $value.",
                "Respuesta: $value.",
            ),
        )
    }

    // ---- nivel 2 ----

    private fun powerOfPower(rng: SeededRandom): Draft {
        val base = rng.pick(BASES)
        val cap = maxExp(base)
        val capBig = maxExp(base, PRODUCT_LIMIT.toInt())
        val maxTerm = maxExp(base, TERM_LIMIT)
        val (m, n, p) = rng.search {
            val m = int(2, 4)
            val n = int(2, 3)
            val total = m * n
            val p = int(2, maxTerm.coerceAtMost(total - 2).coerceAtLeast(2))
            if (total > capBig || total - p < 2 || total - p > cap) null else listOf(m, n, p)
        }
        val e = m * n - p
        val value = pw(base, e)
        val pool = newPool(preferIntegers = true)
        pool.add(pw(base, m + n - p), "En una potencia de potencia los exponentes se MULTIPLICAN: ($base^$m)^$n = $base^${m * n}.")
        pool.add(pw(base, m * n + p), "Al dividir potencias de igual base los exponentes se RESTAN, no se suman.")
        pool.add(pw(base, m * n), "Te faltó dividir entre $base^$p después de resolver la potencia de potencia.")
        exponentOnly(pool, e)
        baseTimesExponent(pool, base, e)
        if ((m * n) % p == 0) {
            pool.add(pw(base, m * n / p), "Dividiste los exponentes. Al dividir potencias de igual base se RESTAN los exponentes.")
        }
        if (powerFits(base, m * n * p)) {
            pool.add(pw(base, m * n * p), "Multiplicaste también el exponente del divisor. Al dividir potencias de igual base los exponentes se RESTAN.")
        }
        return build(
            pool,
            "(${power(base, m)})^$n ÷ ${power(base, p)}",
            value,
            hints = listOf(
                "Potencia de potencia: se multiplican los exponentes. Cociente de igual base: se restan los exponentes.",
                "Primero convierte (b^m)^n en una sola potencia multiplicando m por n. Luego divide restando exponentes.",
                "(${power(base, m)})^$n = $base^($m · $n) = $base^${m * n}. Ahora divide entre ${power(base, p)}.",
            ),
            steps = listOf(
                "Potencia de potencia: se multiplican los exponentes. (${power(base, m)})^$n = $base^($m · $n) = ${power(base, m * n)}.",
                "Cociente de igual base: se restan los exponentes. ${power(base, m * n)} ÷ ${power(base, p)} = $base^(${m * n} - $p) = ${power(base, e)}.",
                "Calculamos la potencia: ${power(base, e)} = $value.",
                "Comprobación sin usar las leyes: ${power(base, m)} = ${pw(base, m)}; ${pw(base, m)}^$n = ${pw(base, m * n)}; " +
                    "${power(base, p)} = ${pw(base, p)}; ${pw(base, m * n)} ÷ ${pw(base, p)} = $value.",
                "Respuesta: $value.",
            ),
        )
    }

    private fun powerOfProduct(rng: SeededRandom): Draft {
        val b = rng.pick(listOf(2, 3, 5))
        val a = rng.intExcept(2, 5, b)
        val n = rng.int(2, 4)
        val value = pw(a, n)
        val pool = newPool(preferIntegers = true)
        pool.add(a, "Olvidaste el exponente: (a · b)^n ÷ b^n = a^n.")
        pool.add(a * n, "Multiplicaste la base por el exponente. Elevar a una potencia es multiplicar la base por sí misma $n veces.")
        pool.add(pw(a, n) * b, "Al elevar el producto, el exponente $n afecta a AMBOS factores: (a · b)^$n = a^$n · b^$n.")
        pool.add(pw(a * b, n), "Te faltó dividir entre ${power(b, n)}.")
        pool.add(a + n, "Sumaste la base y el exponente. Elevar a una potencia es multiplicar la base por sí misma varias veces.")
        pool.add(pw(n, a), "Intercambiaste la base y el exponente.")
        return build(
            pool,
            "($a · $b)^$n ÷ ${power(b, n)}",
            value,
            hints = listOf(
                "Potencia de un producto: (a · b)^n = a^n · b^n. Después puedes simplificar potencias iguales.",
                "Reparte el exponente en el paréntesis: ($a · $b)^$n = ${power(a, n)} · ${power(b, n)}. Luego simplifica.",
                "Al repartir queda ${power(a, n)} · ${power(b, n)} ÷ ${power(b, n)}. Las potencias de $b se cancelan.",
            ),
            steps = listOf(
                "Potencia de un producto: el exponente afecta a cada factor. ($a · $b)^$n = ${power(a, n)} · ${power(b, n)}.",
                "Simplificamos: ${power(a, n)} · ${power(b, n)} ÷ ${power(b, n)} = ${power(a, n)}.",
                "Calculamos la potencia: ${power(a, n)} = $value.",
                "Comprobación sin usar las leyes: $a · $b = ${a * b}; ${a * b}^$n = ${pw(a * b, n)}; " +
                    "${power(b, n)} = ${pw(b, n)}; ${pw(a * b, n)} ÷ ${pw(b, n)} = $value.",
                "Respuesta: $value.",
            ),
        )
    }

    private fun powerOfPowerBoth(rng: SeededRandom): Draft {
        val base = rng.pick(BASES)
        val cap = maxExp(base)
        val capBig = maxExp(base, PRODUCT_LIMIT.toInt())
        val v = rng.search {
            val m = int(2, 4)
            val n = int(2, 3)
            val p = int(2, 3)
            val q = int(2, 3)
            val e = m * n - p * q
            if (e < 2 || e > cap || m * n > capBig) null else listOf(m, n, p, q)
        }
        val (m, n, p, q) = v
        val e = m * n - p * q
        val value = pw(base, e)
        val pool = newPool(preferIntegers = true)
        pool.add(pw(base, m + n - p - q), "En una potencia de potencia los exponentes se MULTIPLICAN, no se suman.")
        pool.add(pw(base, m * n - p - q), "En el denominador también se multiplican los exponentes: (${power(base, p)})^$q = $base^${p * q}.")
        pool.add(pw(base, m * n + p * q), "Al dividir potencias de igual base los exponentes se RESTAN.")
        pool.add(pw(base, m * n), "Te faltó dividir entre el denominador.")
        exponentOnly(pool, e)
        baseTimesExponent(pool, base, e)
        return build(
            pool,
            "(${power(base, m)})^$n ÷ (${power(base, p)})^$q",
            value,
            hints = listOf(
                "Potencia de potencia: se multiplican los exponentes. Cociente de igual base: se restan los exponentes.",
                "Convierte el numerador y el denominador en potencias simples (multiplica sus exponentes) y luego divide.",
                "El numerador es $base^${m * n} y el denominador es $base^${p * q}. Resta los exponentes.",
            ),
            steps = listOf(
                "Numerador: (${power(base, m)})^$n = $base^($m · $n) = ${power(base, m * n)}.",
                "Denominador: (${power(base, p)})^$q = $base^($p · $q) = ${power(base, p * q)}.",
                "Dividimos restando exponentes: ${power(base, m * n)} ÷ ${power(base, p * q)} = $base^(${m * n} - ${p * q}) = ${power(base, e)} = $value.",
                "Comprobación sin usar las leyes: ${pw(base, m * n)} ÷ ${pw(base, p * q)} = $value.",
                "Respuesta: $value.",
            ),
        )
    }

    // ---- nivel 3 ----

    private fun negativeExponents(rng: SeededRandom): Draft {
        val base = rng.pick(listOf(2, 3, 5))
        val (m, n, p) = rng.search {
            val m = int(1, 4)
            val n = int(1, 6)
            val p = int(1, 4)
            val e = n - m + p
            if (e < -2 || e > maxExp(base, 700)) null else listOf(m, n, p)
        }
        val e = n - m + p
        val value = pw(base, e)
        val pool = newPool(preferIntegers = false)
        pool.add(pw(base, -m + n - p), "Dividir entre ${power(base, -p)} equivale a MULTIPLICAR por $base^$p: el exponente $p cambia de signo.")
        pool.add(pw(base, m + n + p), "El signo del exponente negativo $m cuenta: ${power(base, -m)} no es lo mismo que $base^$m.")
        pool.add(pw(base, -e), "El exponente final tiene el signo contrario: suma con cuidado $n - $m + $p.")
        pool.add(-pw(base, e), "Un exponente negativo NO vuelve negativo al número: solo indica el inverso.")
        exponentOnly(pool, e)
        baseTimesExponent(pool, base, e)
        return build(
            pool,
            "(${power(base, -m)} · ${power(base, n)}) ÷ ${power(base, -p)}",
            value,
            hints = listOf(
                "Las leyes de igual base valen también con exponentes negativos. Dividir entre una potencia equivale a restar su exponente (con su signo).",
                "Suma los exponentes del producto y luego réstale el exponente del denominador, cuidando los signos.",
                "El producto da $base^(-$m + $n). Al dividir entre $base^(-$p) queda $base^(-$m + $n - (-$p)).",
            ),
            steps = listOf(
                "Producto de igual base: se suman los exponentes. ${power(base, -m)} · ${power(base, n)} = $base^(-$m + $n) = ${power(base, n - m)}.",
                "Cociente de igual base: se restan los exponentes. ${power(base, n - m)} ÷ ${power(base, -p)} = $base^(${n - m} - (-$p)) = ${power(base, e)}.",
                "Calculamos la potencia: ${power(base, e)} = $value.",
                "Comprobación sin usar las leyes: ${power(base, -m)} = ${pw(base, -m)}, ${power(base, n)} = ${pw(base, n)}, " +
                    "${power(base, -p)} = ${pw(base, -p)}; (${pw(base, -m)} · ${pw(base, n)}) ÷ ${grouped(pw(base, -p))} = $value.",
                "Respuesta: $value.",
            ),
        )
    }

    private fun negativeBaseInverse(rng: SeededRandom): Draft {
        val base = rng.pick(listOf(2, 3, 4, 5))
        val n = rng.int(2, if (base <= 3) 4 else 3)
        val value = pw(base, n)
        val pool = newPool(preferIntegers = false)
        pool.add(Rational.ONE / value, "Te faltó invertir: el exponente negativo da el inverso, y el inverso de 1/$base es $base.")
        pool.add(-value, "Un exponente negativo NO vuelve negativo al número: solo invierte la fracción.")
        pool.add(base * n, "Multiplicaste la base por el exponente. Elevar a una potencia es multiplicar la base por sí misma varias veces.")
        pool.add(-(base * n), "Multiplicaste la base por el exponente y además le pusiste signo negativo.")
        pool.add(pw(n, base), "Intercambiaste la base y el exponente.")
        pool.add(Rational.of(1, base * n), "Calculaste 1/($base · $n); un exponente no se multiplica por la base.")
        return build(
            pool,
            "(1/$base)^(-$n)",
            value,
            hints = listOf(
                "Un exponente negativo invierte la base: a^(-n) = (1/a)^n. En una fracción, se intercambian numerador y denominador.",
                "Invierte la fracción 1/$base para obtener $base/1 y cambia el exponente a positivo.",
                "(1/$base)^(-$n) = ($base)^$n. Calcula esa potencia.",
            ),
            steps = listOf(
                "Un exponente negativo invierte la base: (1/$base)^(-$n) = ($base/1)^$n = $base^$n.",
                "Calculamos la potencia: $base^$n = $value.",
                "Comprobación sin usar la ley: (1/$base)^$n = ${pw(Rational.of(1, base), n)} y el inverso de ${pw(Rational.of(1, base), n)} es $value.",
                "Respuesta: $value.",
            ),
        )
    }

    private fun negativeFractionBase(rng: SeededRandom): Draft {
        val (p, q, n) = rng.search {
            val p = int(2, 5)
            val q = int(2, 5)
            val n = int(1, 3)
            // El resultado (q/p)^n debe quedar con numerador y denominador de a lo más 64 (fracción sencilla).
            if (p == q || gcd(p, q) != 1 || pw(p, n).toDouble() > 64 || pw(q, n).toDouble() > 64) null else listOf(p, q, n)
        }
        val base = Rational.of(p, q)
        val value = pw(base, -n)
        val pool = newPool(preferIntegers = false)
        pool.add(pw(base, n), "Te faltó invertir la fracción: el exponente negativo intercambia numerador y denominador.")
        pool.add(-value, "Un exponente negativo NO vuelve negativo al número: solo invierte la fracción.")
        pool.add(Rational.of(q * n, p), "Multiplicaste en lugar de elevar: (${q}/${p})^$n no es $n · $q/$p.")
        pool.add(Rational.of(q, p), "Invertiste la fracción pero olvidaste elevar al exponente $n.")
        pool.add(Rational.of(q, p * n), "Multiplicaste el denominador por el exponente en vez de elevar ambos términos de la fracción.")
        pool.add(-pw(base, n), "Un exponente negativo no vuelve negativo el resultado, y además falta invertir la fracción.")
        val flipped = Rational.of(q, p)
        return build(
            pool,
            "($p/$q)^(-$n)",
            value,
            hints = listOf(
                "Un exponente negativo invierte la base: (a/b)^(-n) = (b/a)^n.",
                "Invierte la fracción $p/$q y pon el exponente positivo $n. Después eleva numerador y denominador.",
                "($p/$q)^(-$n) = ($q/$p)^$n. Eleva $q y $p a la $n.",
            ),
            steps = listOf(
                "Un exponente negativo invierte la fracción: ($p/$q)^(-$n) = ($q/$p)^$n.",
                "Elevamos numerador y denominador: $q^$n / $p^$n = ${pw(q, n)}/${pw(p, n)}${if ("${pw(q, n)}/${pw(p, n)}" == value.toString()) "" else " = $value"}.",
                "Comprobación sin usar la ley: ($p/$q)^$n = ${pw(base, n)} y su inverso es $value; además (${flipped})^$n = $value.",
                "Respuesta: $value.",
            ),
        )
    }

    private fun zeroExponent(rng: SeededRandom): Draft {
        val a = rng.int(2, 9)
        val b = rng.pick(listOf(2, 3, 5))
        val n = rng.int(1, if (b == 5) 2 else 3)
        val value = Rational.ONE + pw(b, -n)
        val pool = newPool(preferIntegers = false)
        pool.add(pw(b, -n), "Cualquier número distinto de cero elevado a 0 es 1, no 0: $a^0 = 1.")
        pool.add(rat(a) + pw(b, -n), "$a^0 = 1 (no $a): todo número distinto de cero elevado a 0 vale 1.")
        pool.add(Rational.ONE - pw(b, n), "${power(b, -n)} NO es negativo: es el inverso de $b^$n, o sea 1/${pw(b, n)}.")
        pool.add(Rational.ONE + pw(b, n), "Un exponente negativo invierte la base: ${power(b, -n)} = 1/${pw(b, n)}, no ${pw(b, n)}.")
        pool.add(-pw(b, n), "Dos errores: $a^0 vale 1 (no 0) y ${power(b, -n)} es 1/${pw(b, n)}.")
        pool.add(rat(a) + pw(b, n), "Dos errores: $a^0 vale 1 (no $a) y ${power(b, -n)} es 1/${pw(b, n)}, no ${pw(b, n)}.")
        return build(
            pool,
            "$a^0 + ${power(b, -n)}",
            value,
            hints = listOf(
                "Dos ideas clave: todo número distinto de cero elevado a 0 vale 1, y un exponente negativo indica el inverso: a^(-n) = 1/a^n.",
                "Reemplaza $a^0 por 1 y ${power(b, -n)} por 1/$b^$n. Luego suma las dos cantidades.",
                "La expresión queda 1 + 1/${pw(b, n)}. Suma usando el denominador ${pw(b, n)}.",
            ),
            steps = listOf(
                "Todo número distinto de cero elevado a 0 vale 1: $a^0 = 1.",
                "Un exponente negativo indica el inverso: ${power(b, -n)} = 1/$b^$n = 1/${pw(b, n)}.",
                "Sumamos: 1 + 1/${pw(b, n)} = ${pw(b, n)}/${pw(b, n)} + 1/${pw(b, n)} = $value.",
                "Comprobación decimal: $a^0 = 1 y ${power(b, -n)} ${decimalText(pw(b, -n))}; la suma ${decimalText(value)}.",
                "Respuesta: $value.",
            ),
        )
    }

    companion object {
        const val TOPIC_ID = "leyes-de-exponentes"
        private const val VALUE_LIMIT = 1000
        private const val TERM_LIMIT = 300
        private const val PRODUCT_LIMIT = 30000.0
        private const val MAX_ABS = 5000L
        private const val MAX_DEN = 64L
        private val BASES = listOf(2, 3, 5)
    }
}
