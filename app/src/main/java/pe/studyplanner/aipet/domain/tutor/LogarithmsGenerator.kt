package pe.studyplanner.aipet.domain.tutor

/**
 * Logaritmos: definición y propiedades. Los logaritmos se escriben `log_b(N)` (base b, argumento N); la lectura en
 * voz los convierte en «logaritmo en base b de N» (ver [Speech]).
 * Nivel 1: logaritmo directo (N es una potencia exacta de la base). Nivel 2: suma o resta de dos logaritmos, o
 * ecuación `log_b(x) = n`. Nivel 3: propiedades de producto, cociente y potencia, ecuación con `x ± c` en el
 * argumento o ecuación con la incógnita en la base.
 *
 * Todos los argumentos son potencias exactas de la base y las respuestas son enteros pequeños.
 */
class LogarithmsGenerator : BaseGenerator(TOPIC_ID) {

    override fun draft(rng: SeededRandom, difficulty: Int): Draft = when (difficulty) {
        1 -> direct(rng)
        2 -> if (rng.coin()) sumOrDifference(rng) else simpleEquation(rng)
        else -> when (rng.below(5)) {
            0 -> product(rng)
            1 -> quotient(rng)
            2 -> power(rng)
            3 -> shiftedArgument(rng)
            else -> unknownBase(rng)
        }
    }

    // ---- utilidades ----

    private fun pow(base: Int, exp: Int): Int {
        var r = 1
        repeat(exp) { r *= base }
        return r
    }

    private fun pool() = WrongPool(valid = { it.isInteger && it.num in 1..MAX_OPTION })

    private fun fillers(correct: Int): List<Wrong> =
        listOf(1, -1, 2, -2, 3, -3, 4, -4, 5, -5).map { correct + it }.filter { it in 1..MAX_OPTION }
            .map { Wrong(it.toString(), GENERIC_FEEDBACK) }

    /** Mayor exponente n tal que base^n no pasa de [limit]. */
    private fun maxExponent(base: Int, limit: Int): Int {
        var n = 0
        while (pow(base, n + 1) <= limit) n++
        return n
    }

    private fun powersText(base: Int, upTo: Int): String = (1..upTo).joinToString(", ") { pow(base, it).toString() }

    private fun log(base: Any, arg: Any) = "log_$base($arg)"

    private fun because(base: Int, exp: Int) = "$base^$exp = ${pow(base, exp)}"

    // ---- nivel 1 ----

    private fun direct(rng: SeededRandom): Draft {
        val b = rng.pick(listOf(2, 3, 4, 5, 6, 7, 10))
        val n = rng.int(2, minOf(7, maxExponent(b, 1000)))
        val big = pow(b, n)
        val pool = pool()
        pool.add(b * n, "Multiplicaste la base por el exponente. El logaritmo es el exponente n que cumple $b^n = $big.")
        pool.add(big / b, "Dividiste $big entre la base. El logaritmo no es un cociente: es el exponente al que se eleva $b para obtener $big.")
        pool.add(n + 1, "Te pasaste un exponente: $b^${n + 1} = ${pow(b, n + 1)}, y no es $big.")
        pool.add(n - 1, "Te quedaste un exponente corto: $b^${n - 1} = ${pow(b, n - 1)}, y no es $big.")
        pool.add(b + n, "Sumaste la base y el exponente. Pregúntate a qué exponente hay que elevar $b para obtener $big.")
        if (big % n == 0) pool.add(big / n, "Dividiste $big entre el exponente. El logaritmo es el exponente mismo, no un cociente.")
        return Draft(
            statement = "Calcula el valor de:\n${log(b, big)}",
            correct = n.toString(),
            wrong = pool.items,
            hints = listOf(
                "El logaritmo de N en base b es el exponente al que se eleva b para obtener N: log_b(N) = n significa que b^n = N.",
                "Pregúntate: ¿a qué exponente hay que elevar $b para obtener $big? Escribe las potencias de $b: ${powersText(b, n - 1)} y las que siguen, hasta llegar a $big.",
                "Las potencias de $b son ${powersText(b, n)}. Cuenta en qué lugar de la lista aparece $big.",
            ),
            steps = listOf(
                "El logaritmo de $big en base $b es el exponente al que hay que elevar $b para obtener $big.",
                "Potencias de $b: ${powersText(b, n)}. El número $big ocupa el lugar $n.",
                "Entonces ${because(b, n)}, y por eso ${log(b, big)} = $n.",
                "Comprobación: elevamos $b a la $n y obtenemos ${because(b, n)}.",
                "Respuesta: $n.",
            ),
            fillers = fillers(n),
        )
    }

    // ---- nivel 2 ----

    private fun sumOrDifference(rng: SeededRandom): Draft {
        val bases = listOf(2, 3, 4, 5, 10)
        val plus = rng.coin()
        val (b1, n1, b2, n2) = rng.search {
            val b1 = pick(bases)
            val b2 = pick(bases)
            val n1 = int(2, minOf(6, maxExponent(b1, 1000)))
            val n2 = int(2, minOf(6, maxExponent(b2, 1000)))
            val ok = !(b1 == b2 && n1 == n2) && (plus || n1 > n2)
            if (ok) listOf(b1, n1, b2, n2) else null
        }
        val big1 = pow(b1, n1)
        val big2 = pow(b2, n2)
        val answer = if (plus) n1 + n2 else n1 - n2
        val op = if (plus) "+" else "-"
        val word = if (plus) "sumar" else "restar"
        val otherWord = if (plus) "restar" else "sumar"
        val pool = pool()
        pool.add(if (plus) n1 - n2 else n1 + n2, "La expresión pide $word los logaritmos, no $otherWord.")
        pool.add(n1 * n2, "Multiplicaste los dos logaritmos. La expresión pide $word los valores, no multiplicarlos.")
        pool.add(if (plus) big1 + big2 else big1 - big2, "Operaste los números de adentro ($big1 y $big2) sin calcular los logaritmos. Primero hay que hallar cada logaritmo.")
        pool.add(n1, "Calculaste solo el primer logaritmo. Falta calcular el segundo y $word.")
        pool.add(n2, "Calculaste solo el segundo logaritmo. Falta calcular el primero y $word.")
        pool.add(answer + 1, "Revisa los exponentes: ${because(b1, n1)} y ${because(b2, n2)}.")
        return Draft(
            statement = "Calcula el valor de:\nE = ${log(b1, big1)} $op ${log(b2, big2)}",
            correct = answer.toString(),
            wrong = pool.items,
            hints = listOf(
                "Calcula cada logaritmo por separado con la definición: log_b(N) es el exponente al que se eleva b para obtener N.",
                "Halla ${log(b1, big1)} y ${log(b2, big2)}, y luego ${if (plus) "suma" else "resta"} los resultados en ese orden.",
                "Pregúntate: ¿a qué exponente elevas $b1 para obtener $big1? ¿Y $b2 para obtener $big2?",
            ),
            steps = listOf(
                "Calculamos cada logaritmo con la definición.",
                "${log(b1, big1)} = $n1, porque ${because(b1, n1)}.",
                "${log(b2, big2)} = $n2, porque ${because(b2, n2)}.",
                "E = $n1 $op $n2 = $answer.",
                "Respuesta: $answer.",
            ),
            fillers = fillers(answer),
        )
    }

    private fun simpleEquation(rng: SeededRandom): Draft {
        val b = rng.pick(listOf(2, 3, 4, 5, 10))
        val n = rng.int(2, minOf(6, maxExponent(b, 1000)))
        val x = pow(b, n)
        val pool = pool()
        pool.add(n, "Diste el valor del logaritmo. Se pide x, el número cuyo logaritmo en base $b es $n: x = $b^$n.")
        pool.add(b * n, "Multiplicaste la base por el logaritmo. Por definición, x es $b elevado a $n.")
        pool.add(pow(b, n + 1), "Elevaste $b a un exponente de más. Por definición, x = $b^$n.")
        pool.add(pow(b, n - 1), "Elevaste $b a un exponente de menos. Por definición, x = $b^$n.")
        pool.add(b + n, "Sumaste la base y el logaritmo. Por definición, x = $b^$n.")
        return Draft(
            statement = "Resuelve la ecuación:\n${log(b, "x")} = $n\n¿Cuál es el valor de x?",
            correct = x.toString(),
            wrong = pool.items,
            hints = listOf(
                "Usa la definición: log_b(x) = n significa que b^n = x.",
                "Aquí la base es $b y el logaritmo vale $n. Entonces x es $b elevado a la potencia $n.",
                "Calcula la potencia ${b}^$n.",
            ),
            steps = listOf(
                "Por definición, el número x es la base elevada al valor del logaritmo.",
                "Entonces x = $b^$n.",
                "Calculamos la potencia: x = $b^$n = $x.",
                "Comprobación: ${log(b, x)} = $n, porque ${because(b, n)}.",
                "Respuesta: $x.",
            ),
            fillers = fillers(x),
        )
    }

    // ---- nivel 3 ----

    private fun product(rng: SeededRandom): Draft {
        val b = rng.pick(listOf(2, 3, 5))
        val (p, q) = rng.search {
            val p = int(1, 4)
            val q = int(1, 4)
            if (p == q || p + q < 3 || pow(b, p + q) > 2000) null else p to q
        }
        val x = pow(b, p)
        val y = pow(b, q)
        val answer = p + q
        val pool = pool()
        pool.add(p * q, "Multiplicaste los logaritmos. El logaritmo de un producto es la SUMA de los logaritmos.")
        pool.add(x * y, "Multiplicaste los números de adentro ($x × $y) y te quedaste ahí. Falta calcular el logaritmo, o mejor, sumar log_$b($x) y log_$b($y).")
        pool.add(Math.abs(p - q), "Restaste los logaritmos. La resta es para el logaritmo de un COCIENTE; para un producto se suman.")
        pool.add(x + y, "Sumaste los números de adentro. Hay que sumar sus logaritmos, no los números.")
        pool.add(answer + 1, "Revisa los exponentes: ${because(b, p)} y ${because(b, q)}.")
        return Draft(
            statement = "Calcula el valor de:\n${log(b, "$x · $y")}",
            correct = answer.toString(),
            wrong = pool.items,
            hints = listOf(
                "El logaritmo de un producto es la suma de los logaritmos: log_b(x · y) = log_b(x) + log_b(y).",
                "Calcula por separado ${log(b, x)} y ${log(b, y)}, y súmalos.",
                "Pregúntate: ¿a qué exponente elevas $b para obtener $x? ¿Y para obtener $y?",
            ),
            steps = listOf(
                "Propiedad del producto: el logaritmo de un producto es la suma de los logaritmos de los factores.",
                "${log(b, x)} = $p, porque ${because(b, p)}. ${log(b, y)} = $q, porque ${because(b, q)}.",
                "La suma es $p + $q = $answer.",
                "Comprobación: $x · $y = ${x * y} y ${because(b, answer)}.",
                "Respuesta: $answer.",
            ),
            fillers = fillers(answer),
        )
    }

    private fun quotient(rng: SeededRandom): Draft {
        val b = rng.pick(listOf(2, 3, 5))
        val (p, q) = rng.search {
            val p = int(3, 6)
            val q = int(1, 4)
            if (p - q < 1 || pow(b, p) > 2000) null else p to q
        }
        val x = pow(b, p)
        val y = pow(b, q)
        val answer = p - q
        val pool = pool()
        pool.add(p + q, "Sumaste los logaritmos. La suma es para el logaritmo de un producto; para un COCIENTE se restan.")
        pool.add(p * q, "Multiplicaste los logaritmos. El logaritmo de un cociente es la RESTA de los logaritmos.")
        pool.add(x / y, "Dividiste los números de adentro ($x ÷ $y) y no calculaste el logaritmo. Se restan los logaritmos: log_$b($x) - log_$b($y).")
        if (p % q == 0) pool.add(p / q, "Dividiste los logaritmos. El logaritmo de un cociente es la RESTA de los logaritmos, no su cociente.")
        pool.add(p, "Calculaste solo el logaritmo del numerador. Falta restar el logaritmo de $y.")
        pool.add(answer + 1, "Revisa los exponentes: ${because(b, p)} y ${because(b, q)}.")
        return Draft(
            statement = "Calcula el valor de:\n${log(b, "$x ÷ $y")}",
            correct = answer.toString(),
            wrong = pool.items,
            hints = listOf(
                "El logaritmo de un cociente es la resta de los logaritmos: log_b(x ÷ y) = log_b(x) - log_b(y).",
                "Calcula por separado ${log(b, x)} y ${log(b, y)}, y réstalos en ese orden.",
                "Pregúntate: ¿a qué exponente elevas $b para obtener $x? ¿Y para obtener $y?",
            ),
            steps = listOf(
                "Propiedad del cociente: el logaritmo de un cociente es la resta de los logaritmos.",
                "${log(b, x)} = $p, porque ${because(b, p)}. ${log(b, y)} = $q, porque ${because(b, q)}.",
                "La resta es $p - $q = $answer.",
                "Comprobación: $x ÷ $y = ${x / y} y ${because(b, answer)}.",
                "Respuesta: $answer.",
            ),
            fillers = fillers(answer),
        )
    }

    private fun power(rng: SeededRandom): Draft {
        val b = rng.pick(listOf(2, 3, 5))
        val p = rng.int(2, 3)
        val k = rng.int(2, 4)
        val x = pow(b, p)
        val answer = p * k
        val pool = pool()
        pool.add(p + k, "Sumaste el logaritmo y el exponente. En el logaritmo de una potencia el exponente sale MULTIPLICANDO.")
        pool.add(pow(p, k), "Elevaste el logaritmo al exponente. El exponente de la potencia sale multiplicando: k · log.")
        pool.add(k, "Diste solo el exponente $k. Falta multiplicarlo por log_$b($x) = $p.")
        pool.add(p, "Calculaste solo log_$b($x) = $p. Falta multiplicarlo por el exponente $k.")
        pool.add(pow(x, k), "Calculaste la potencia $x^$k y no el logaritmo.")
        pool.add(answer + 1, "Revisa la multiplicación $p × $k.")
        return Draft(
            statement = "Calcula el valor de:\n${log(b, "$x^$k")}",
            correct = answer.toString(),
            wrong = pool.items,
            hints = listOf(
                "El logaritmo de una potencia es el exponente por el logaritmo de la base de la potencia: log_b(x^k) = k · log_b(x).",
                "Calcula primero ${log(b, x)} y multiplica el resultado por $k.",
                "Pregúntate: ¿a qué exponente elevas $b para obtener $x? Luego multiplica por $k.",
            ),
            steps = listOf(
                "Propiedad de la potencia: el exponente sale multiplicando, log_b(x^k) = k · log_b(x).",
                "${log(b, x)} = $p, porque ${because(b, p)}.",
                "Multiplicamos por el exponente: $k × $p = $answer.",
                "Comprobación: $x es $b^$p, así que $x^$k = ($b^$p)^$k = $b^$answer.",
                "Respuesta: $answer.",
            ),
            fillers = fillers(answer),
        )
    }

    private fun shiftedArgument(rng: SeededRandom): Draft {
        val b = rng.pick(listOf(2, 3, 4, 5))
        val n = rng.int(2, minOf(4, maxExponent(b, 700)))
        val big = pow(b, n)
        val negative = rng.coin()
        val c = rng.search { int(1, 9).takeIf { negative || it < big } }
        val signed = if (negative) -c else c
        val x = big - signed
        val argument = Fmt.combine(1 to "x", signed to "")
        val pool = pool()
        pool.add(big + signed, "Pasaste el $c al otro lado con el signo equivocado. Si x ${Fmt.op(signed)} $c = $big, entonces x = $big ${Fmt.op(-signed)} $c.")
        pool.add(big, "Olvidaste el $c: x ${Fmt.op(signed)} $c es igual a $big, no x.")
        pool.add(b * n - signed, "Multiplicaste la base por el logaritmo. Por definición, lo de adentro es $b^$n = $big.")
        pool.add(n - signed, "Usaste $n en lugar de la potencia $b^$n. Por definición, lo de adentro vale $big.")
        pool.add(pow(b, n + 1) - signed, "Elevaste $b a un exponente de más: $b^$n = $big.")
        pool.add(pow(b, n - 1) - signed, "Elevaste $b a un exponente de menos: $b^$n = $big.")
        return Draft(
            statement = "Resuelve la ecuación:\n${log(b, argument)} = $n\n¿Cuál es el valor de x?",
            correct = x.toString(),
            wrong = pool.items,
            hints = listOf(
                "Usa la definición: log_b(A) = n significa que A = b^n, donde A es lo que está dentro del logaritmo.",
                "Entonces lo de adentro, $argument, es igual a $b elevado a la potencia $n. Calcula la potencia y despeja x.",
                "Calcula ${b}^$n y luego ${if (negative) "suma" else "resta"} $c para despejar x.",
            ),
            steps = listOf(
                "Por definición, lo de adentro del logaritmo es la base elevada al resultado: $argument = $b^$n.",
                "Calculamos la potencia: ${because(b, n)}.",
                "Entonces $argument = $big y x = $big ${Fmt.op(-signed)} $c = $x.",
                "Comprobación: con x = $x el argumento es $x ${Fmt.op(signed)} $c = $big, y ${because(b, n)}.",
                "Respuesta: $x.",
            ),
            fillers = fillers(x),
        )
    }

    private fun unknownBase(rng: SeededRandom): Draft {
        val x = rng.int(2, 6)
        val n = rng.int(2, 4)
        val big = pow(x, n)
        val pool = pool()
        pool.add(n, "Diste el valor del logaritmo. La incógnita x es la BASE: el número que elevado a $n da $big.")
        pool.add(big, "Diste el número del logaritmo. Se pide la base x, que elevada a $n da $big.")
        if (big % n == 0) pool.add(big / n, "Dividiste $big entre $n. La base es el número que elevado a $n da $big, no el cociente.")
        pool.add(big - n, "Restaste $n de $big. La base es el número que elevado a $n da $big.")
        pool.add(x + 1, "Comprueba: ${x + 1}^$n = ${pow(x + 1, n)} y no es $big.")
        if (x > 2) pool.add(x - 1, "Comprueba: ${x - 1}^$n = ${pow(x - 1, n)} y no es $big.")
        pool.add(x * n, "Multiplicaste la base por el logaritmo. Debes hallar x tal que x^$n = $big.")
        return Draft(
            statement = "Resuelve la ecuación:\n${log("x", big)} = $n\n¿Cuál es el valor de x?",
            correct = x.toString(),
            wrong = pool.items,
            hints = listOf(
                "Usa la definición: log_x(N) = n significa que x^n = N. Aquí x es la base.",
                "Busca el número que elevado a $n da $big. Prueba con 2, 3, 4, 5...",
                "Las potencias de exponente $n son ${(2..x + 1).joinToString(", ") { pow(it, n).toString() }}. Busca $big en la lista.",
            ),
            steps = listOf(
                "Por definición, la base x elevada al valor del logaritmo da el número de adentro: x^$n = $big.",
                "Probamos con los enteros desde 2 hasta dar con $big: ${(2..x).joinToString("; ") { because(it, n) }}.",
                "Con $x se cumple ${because(x, n)}: la base es $x.",
                "Comprobación: ${log(x, big)} = $n, porque ${because(x, n)}.",
                "Respuesta: $x.",
            ),
            fillers = fillers(x),
        )
    }

    companion object {
        const val TOPIC_ID = "logaritmos"
        private const val MAX_OPTION = 20000
    }
}
