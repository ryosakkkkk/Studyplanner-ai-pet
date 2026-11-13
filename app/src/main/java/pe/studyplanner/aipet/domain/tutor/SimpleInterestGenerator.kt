package pe.studyplanner.aipet.domain.tutor

/**
 * Interés simple: I = C · r · t ÷ 100, con la tasa r en porcentaje anual y el tiempo t en años.
 * Nivel 1: interés ganado. Nivel 2: monto final, o interés cuando el tiempo está en meses.
 * Nivel 3: hallar el capital, la tasa o el tiempo a partir del interés.
 *
 * Los capitales son múltiplos de 100 y los datos se escogen para que todas las respuestas sean enteras.
 */
class SimpleInterestGenerator : BaseGenerator(TOPIC_ID) {

    override fun draft(rng: SeededRandom, difficulty: Int): Draft = when (difficulty) {
        1 -> interest(rng)
        2 -> if (rng.coin()) amount(rng) else interestInMonths(rng)
        else -> when (rng.below(3)) {
            0 -> findCapital(rng)
            1 -> findRate(rng)
            else -> findTime(rng)
        }
    }

    // ---- utilidades ----

    private fun soles(n: Int) = Fmt.soles(n)

    private fun years(n: Int) = if (n == 1) "1 año" else "$n años"

    private fun moneyPool() = WrongPool(
        format = { Fmt.soles(it.toIntExact()) },
        valid = { it.isInteger && it.num in 1..MAX_MONEY },
    )

    private fun fillers(correct: Int, format: (Int) -> String, min: Int = 1): List<Wrong> =
        listOf(1, -1, 2, -2, 3, -3, 5, -5, 10, -10).map { correct + it }.filter { it >= min }
            .map { Wrong(format(it), GENERIC_FEEDBACK) }

    private val formulaHint =
        "En el interés simple, el interés es el capital por la tasa por el tiempo en años, dividido entre 100."

    private fun capital(rng: SeededRandom) = 100 * rng.int(5, 60)

    // ---- nivel 1 ----

    private fun interest(rng: SeededRandom): Draft {
        val c = capital(rng)
        val r = rng.int(2, 12)
        val t = rng.int(1, 5)
        val i = c * r * t / 100
        val pool = moneyPool()
        pool.add(c + i, "Calculaste el monto (capital más interés). La pregunta pide solo el interés.")
        pool.add(c * r * t, "Te faltó dividir entre 100: la tasa $r% significa $r por cada 100.")
        pool.add(c * r * t / 10, "Dividiste entre 10 en lugar de entre 100: el porcentaje se divide entre 100.")
        if (t > 1) pool.add(c * r / 100, "Calculaste el interés de UN solo año. Hay que multiplicar por el tiempo: $t años.")
        pool.add(c * t / 100, "Olvidaste la tasa: el interés es capital × tasa × tiempo ÷ 100.")
        pool.add(c * (r + t) / 100, "Sumaste la tasa y el tiempo. Se multiplican: capital × tasa × tiempo ÷ 100.")
        compound(c, r, t)?.let { pool.add(it, "Calculaste un interés compuesto (el interés de cada año se suma al capital). En el interés simple siempre se usa el capital inicial.") }
        return Draft(
            statement = "Se depositan ${soles(c)} a una tasa de interés simple de $r% anual durante ${years(t)}. ¿Cuánto interés se gana?",
            correct = soles(i),
            wrong = pool.items,
            hints = listOf(
                formulaHint,
                "Usa I = C × r × t ÷ 100, con C = $c, r = $r y t = $t.",
                "Multiplica $c × $r × $t y divide el resultado entre 100.",
            ),
            steps = listOf(
                "En el interés simple: interés = capital × tasa × tiempo ÷ 100, con el tiempo en años.",
                "Datos: capital ${soles(c)}, tasa $r% anual, tiempo ${years(t)}.",
                "Interés = $c × $r × $t ÷ 100 = ${c * r * t} ÷ 100 = $i soles.",
                "Comprobación: cada año se gana $c × $r ÷ 100 = ${c * r / 100} soles, y en $t ${if (t == 1) "año" else "años"} son ${c * r / 100} × $t = $i soles.",
                "Respuesta: ${soles(i)}.",
            ),
            fillers = fillers(i, { soles(it) }),
        )
    }

    /** Interés compuesto entero (error típico), o `null` si no es entero o no aplica. */
    private fun compound(c: Int, r: Int, t: Int): Int? {
        if (t < 2) return null
        var amount = Rational.of(c)
        repeat(t) { amount *= Rational.of(100 + r, 100) }
        val interest = amount - rat(c)
        return if (interest.isInteger) interest.toIntExact() else null
    }

    // ---- nivel 2 ----

    private fun amount(rng: SeededRandom): Draft {
        val c = capital(rng)
        val r = rng.int(2, 12)
        val t = rng.int(2, 6)
        val i = c * r * t / 100
        val total = c + i
        val pool = moneyPool()
        pool.add(i, "Hallaste solo el interés. El monto es el capital MÁS el interés.")
        pool.add(c + c * r / 100, "Sumaste el interés de UN solo año. Hay que multiplicar por el tiempo: $t años.")
        pool.add(c + c * r * t / 10, "Dividiste entre 10 en lugar de entre 100 al calcular el interés.")
        pool.add(c + r * t, "Sumaste el porcentaje total ($r × $t) directamente al capital. Primero hay que calcular ese porcentaje DEL capital.")
        pool.add(c - i, "Restaste el interés. En un depósito el interés se SUMA al capital.")
        pool.add(2 * c + i, "Sumaste el capital dos veces: el monto es capital + interés.")
        compound(c, r, t)?.let { pool.add(c + it, "Calculaste el monto con interés compuesto. En el interés simple el interés de cada año es el mismo.") }
        return Draft(
            statement = "Se depositan ${soles(c)} a una tasa de interés simple de $r% anual durante ${years(t)}. ¿Cuál es el monto al final del plazo?",
            correct = soles(total),
            wrong = pool.items,
            hints = listOf(
                "El monto es todo el dinero al final del plazo: el capital más el interés ganado.",
                "Primero halla el interés con I = C × r × t ÷ 100 y luego súmalo al capital.",
                "El interés es $c × $r × $t ÷ 100. Al resultado súmale el capital $c.",
            ),
            steps = listOf(
                "El monto es el capital más el interés. Primero calculamos el interés.",
                "Interés = $c × $r × $t ÷ 100 = ${c * r * t} ÷ 100 = $i soles.",
                "Monto = capital + interés = $c + $i = $total soles.",
                "Comprobación: cada año se gana ${c * r / 100} soles; en $t años son $i soles y $c + $i = $total.",
                "Respuesta: ${soles(total)}.",
            ),
            fillers = fillers(total, { soles(it) }),
        )
    }

    private fun interestInMonths(rng: SeededRandom): Draft {
        val (c, r, m) = rng.search {
            val k = int(5, 60)
            val r = int(2, 12)
            val m = pick(MONTHS)
            if ((k * r * m) % 12 != 0) null else listOf(100 * k, r, m)
        }
        val i = c * r * m / 1200
        val yearsText = Fmt.decimal(m / 12.0)
        val pool = moneyPool()
        pool.add(c * r * m / 100, "Usaste los $m meses como si fueran años. Hay que pasar los meses a años: $m ÷ 12.")
        pool.add(c * r / 100, "Calculaste el interés de UN año. El tiempo es de $m meses, es decir, $yearsText años.")
        pool.add(c * r * m / 12, "Dividiste entre 12 pero olvidaste dividir entre 100 (la tasa es un porcentaje).")
        pool.add(c * r * m / 120, "Dividiste entre 120. Pasar meses a años es dividir entre 12 y el porcentaje se divide entre 100: en total, entre 1200.")
        pool.add(c + i, "Calculaste el monto (capital más interés). La pregunta pide solo el interés.")
        pool.add(c * r * m / 1000, "Dividiste entre 1000. Debes dividir entre 100 (por el porcentaje) y entre 12 (por los meses): 1200.")
        return Draft(
            statement = "Se prestan ${soles(c)} a una tasa de interés simple de $r% anual durante $m meses. ¿Cuánto interés se paga?",
            correct = soles(i),
            wrong = pool.items,
            hints = listOf(
                "La tasa es anual, así que el tiempo debe estar en años. Para pasar meses a años se divide entre 12.",
                "Convierte $m meses a años y luego usa I = C × r × t ÷ 100.",
                "El tiempo es $m ÷ 12 = $yearsText años. Calcula $c × $r × $yearsText ÷ 100.",
            ),
            steps = listOf(
                "La tasa es anual, así que pasamos el tiempo a años: $m ÷ 12 = $yearsText años.",
                "Interés = capital × tasa × tiempo ÷ 100.",
                "Interés = $c × $r × $yearsText ÷ 100 = ${Fmt.decimal(c * r * m / 12.0)} ÷ 100 = $i soles.",
                "Comprobación con los meses: $c × $r × $m ÷ 1200 = ${c * r * m} ÷ 1200 = $i soles.",
                "Respuesta: ${soles(i)}.",
            ),
            fillers = fillers(i, { soles(it) }),
        )
    }

    // ---- nivel 3 ----

    private fun findCapital(rng: SeededRandom): Draft {
        val c = capital(rng)
        val r = rng.int(2, 12)
        val t = rng.int(2, 6)
        val i = c * r * t / 100
        val pool = moneyPool()
        pool.add(i * r * t / 100, "Aplicaste la fórmula del interés al revés. Aquí se conoce el interés y se pide el capital: C = I × 100 ÷ (r × t).")
        pool.add(i * 100 / r, "Olvidaste el tiempo: el interés depende de $t años, así que se divide entre r × t = ${r * t}, no solo entre $r.")
        pool.add(i / (r * t), "Te faltó multiplicar por 100: C = I × 100 ÷ (r × t).")
        pool.add(i * 100 / t, "Olvidaste la tasa: se divide entre r × t = ${r * t}, no solo entre $t.")
        pool.add(i * 100 / (r + t), "Sumaste la tasa y el tiempo. Se multiplican: C = I × 100 ÷ (r × t).")
        pool.add(i - r * t, "Restaste el porcentaje del interés. El capital se halla despejando: C = I × 100 ÷ (r × t).")
        return Draft(
            statement = "¿Qué capital, colocado a una tasa de interés simple de $r% anual durante $t años, produce un interés de ${soles(i)}?",
            correct = soles(c),
            wrong = pool.items,
            hints = listOf(
                formulaHint,
                "Despeja el capital: C = I × 100 ÷ (r × t).",
                "Calcula r × t = $r × $t y luego $i × 100 ÷ (r × t).",
            ),
            steps = listOf(
                "Partimos de I = C × r × t ÷ 100 y despejamos: C = I × 100 ÷ (r × t).",
                "Datos: interés ${soles(i)}, tasa $r% y tiempo $t años.",
                "Capital = $i × 100 ÷ ($r × $t) = ${i * 100} ÷ ${r * t} = $c soles.",
                "Comprobación: $c × $r × $t ÷ 100 = ${c * r * t} ÷ 100 = $i soles.",
                "Respuesta: ${soles(c)}.",
            ),
            fillers = fillers(c, { soles(it) }),
        )
    }

    private fun findRate(rng: SeededRandom): Draft {
        val c = capital(rng)
        val r = rng.int(2, 15)
        val t = rng.int(2, 6)
        val i = c * r * t / 100
        val pool = WrongPool(format = { "${it.toIntExact()}%" }, valid = { it.isInteger && it.num in 1..MAX_RATE })
        pool.add(r * t, "Calculaste el porcentaje de todo el plazo ($t años). La tasa es anual: hay que dividir entre $t.")
        pool.add(r * t * t, "Multiplicaste por el tiempo en lugar de dividir. La tasa anual es r = I × 100 ÷ (C × t).")
        pool.add(r + t, "Sumaste valores sin un fundamento: la tasa se obtiene con r = I × 100 ÷ (C × t).")
        pool.add(r * 2, "Revisa las operaciones: r = I × 100 ÷ (C × t), con C = $c, I = $i y t = $t.")
        pool.add(r + 1, "Estás cerca, pero el cálculo no es exacto. Comprueba: $c × tasa × $t ÷ 100 debe ser $i.")
        pool.add(r - 1, "Estás cerca, pero el cálculo no es exacto. Comprueba: $c × tasa × $t ÷ 100 debe ser $i.")
        return Draft(
            statement = "Un capital de ${soles(c)} produce ${soles(i)} de interés simple en $t años. ¿Cuál es la tasa de interés anual?",
            correct = "$r%",
            wrong = pool.items,
            hints = listOf(
                formulaHint,
                "Despeja la tasa: r = I × 100 ÷ (C × t).",
                "Calcula C × t = $c × $t y luego $i × 100 ÷ (C × t).",
            ),
            steps = listOf(
                "Partimos de I = C × r × t ÷ 100 y despejamos la tasa: r = I × 100 ÷ (C × t).",
                "Datos: capital $c soles, interés $i soles y tiempo $t años.",
                "Tasa = $i × 100 ÷ ($c × $t) = ${i * 100} ÷ ${c * t} = $r, es decir, $r% anual.",
                "Comprobación: $c × $r × $t ÷ 100 = ${c * r * t} ÷ 100 = $i soles.",
                "Respuesta: $r%.",
            ),
            fillers = fillers(r, { "$it%" }),
        )
    }

    private fun findTime(rng: SeededRandom): Draft {
        val c = capital(rng)
        val r = rng.int(2, 12)
        val t = rng.int(2, 8)
        val i = c * r * t / 100
        val pool = WrongPool(format = { "${it.toIntExact()} años" }, valid = { it.isInteger && it.num in 2..MAX_YEARS })
        pool.add(r * t, "Olvidaste dividir entre la tasa. Primero I × 100 ÷ C da r × t = ${r * t}; luego se divide entre $r.")
        pool.add(t * 12, "Pasaste el tiempo a meses sin que se pidiera. La pregunta pide el tiempo en años.")
        pool.add(t + 1, "Estás cerca, pero el cálculo no es exacto. Comprueba: $c × $r × tiempo ÷ 100 debe ser $i.")
        pool.add(t - 1, "Estás cerca, pero el cálculo no es exacto. Comprueba: $c × $r × tiempo ÷ 100 debe ser $i.")
        pool.add(i / c, "Dividiste el interés entre el capital sin multiplicar por 100 ni dividir entre la tasa.")
        pool.add(t * r, "Multiplicaste el tiempo por la tasa. El tiempo se despeja: t = I × 100 ÷ (C × r).")
        pool.add(t * 2, "Duplicaste el tiempo: revisa el cálculo t = I × 100 ÷ (C × r).")
        return Draft(
            statement = "Un capital de ${soles(c)} colocado a una tasa de interés simple de $r% anual produce ${soles(i)} de interés. ¿En cuántos años lo produce?",
            correct = "$t años",
            wrong = pool.items,
            hints = listOf(
                formulaHint,
                "Despeja el tiempo: t = I × 100 ÷ (C × r).",
                "Calcula C × r = $c × $r y luego $i × 100 ÷ (C × r).",
            ),
            steps = listOf(
                "Partimos de I = C × r × t ÷ 100 y despejamos el tiempo: t = I × 100 ÷ (C × r).",
                "Datos: capital $c soles, tasa $r% e interés $i soles.",
                "Tiempo = $i × 100 ÷ ($c × $r) = ${i * 100} ÷ ${c * r} = $t años.",
                "Comprobación: $c × $r × $t ÷ 100 = ${c * r * t} ÷ 100 = $i soles.",
                "Respuesta: $t años.",
            ),
            fillers = fillers(t, { "$it años" }, min = 2),
        )
    }

    companion object {
        const val TOPIC_ID = "interes-simple"
        private const val MAX_MONEY = 20000L
        private const val MAX_RATE = 100L
        private const val MAX_YEARS = 100L
        private val MONTHS = listOf(3, 6, 9, 15, 18, 24, 30)
    }
}
