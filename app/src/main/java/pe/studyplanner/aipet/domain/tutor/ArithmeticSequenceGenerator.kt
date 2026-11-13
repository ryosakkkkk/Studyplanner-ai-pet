package pe.studyplanner.aipet.domain.tutor

import pe.studyplanner.aipet.domain.tutor.Fmt.p

/**
 * Sucesiones aritméticas.
 * Nivel 1: siguiente término. Nivel 2: término de un lugar dado. Nivel 3: término a partir de dos términos
 * conocidos, o número de términos de una sucesión.
 */
class ArithmeticSequenceGenerator : BaseGenerator(TOPIC_ID) {

    override fun draft(rng: SeededRandom, difficulty: Int): Draft = when (difficulty) {
        1 -> nextTerm(rng)
        2 -> nthTerm(rng)
        else -> if (rng.coin()) fromTwoTerms(rng) else countTerms(rng)
    }

    private fun pool(min: Long = -MAX_ABS) = WrongPool(valid = { it.isInteger && it.num in min..MAX_ABS })

    private fun fillers(correct: Int): List<Wrong> =
        listOf(1, -1, 2, -2, 3, -3, 4, -4, 5, -5).map { correct + it }
            .map { Wrong(it.toString(), GENERIC_FEEDBACK) }

    private fun terms(first: Int, step: Int, count: Int): String =
        (0 until count).joinToString(", ") { (first + it * step).toString() }

    private fun rate(rng: SeededRandom, max: Int = 9): Int = rng.intExcept(-max, max, 0)

    // ---- nivel 1 ----

    private fun nextTerm(rng: SeededRandom): Draft {
        val a1 = rng.int(-10, 30)
        val r = rate(rng)
        val last = a1 + 3 * r
        val answer = last + r
        val pool = pool()
        pool.add(last - r, "Restaste la razón $r. Para el término siguiente se SUMA la razón (con su signo) al último término.")
        pool.add(last + 2 * r, "Saltaste un término: el siguiente se obtiene sumando la razón UNA sola vez.")
        pool.add(2 * last, "Duplicaste el último término. En una sucesión aritmética se suma siempre la misma cantidad.")
        pool.add(last * r, "Multiplicaste por la razón. En una sucesión aritmética se SUMA la razón, no se multiplica.")
        pool.add(last + Math.abs(r), "Revisa el signo de la razón: es $r (la sucesión ${if (r > 0) "crece" else "decrece"}).")
        pool.add(answer + r, "Pasaste de largo: ese es el término después del que se pide.")
        return Draft(
            statement = "En la sucesión aritmética ${terms(a1, r, 4)}, ... ¿qué número sigue?",
            correct = answer.toString(),
            wrong = pool.items,
            hints = listOf(
                "En una sucesión aritmética se pasa de un término al siguiente sumando siempre la misma cantidad, llamada razón (r).",
                "Halla la razón restando dos términos consecutivos (el segundo menos el primero) y súmala al último término.",
                "La razón es ${a1 + r} - ${p(a1)} = $r. El último término es $last: calcula $last + ${p(r)}.",
            ),
            steps = listOf(
                "En una sucesión aritmética la diferencia entre términos consecutivos es constante (la razón r).",
                "Calculamos la razón: r = ${a1 + r} - ${p(a1)} = $r. Comprobamos con otro par: $last - ${p(last - r)} = $r.",
                "El siguiente término es el último más la razón: $last + ${p(r)} = $answer.",
                "Respuesta: $answer.",
            ),
            fillers = fillers(answer),
        )
    }

    // ---- nivel 2 ----

    private fun nthTerm(rng: SeededRandom): Draft {
        val a1 = rng.int(-10, 30)
        val r = rate(rng, 8)
        val n = rng.int(10, 40)
        val answer = a1 + (n - 1) * r
        val pool = pool()
        pool.add(a1 + n * r, "Hay n - 1 saltos entre el primer término y el de lugar n, no n saltos: an = a1 + (n - 1)·r.")
        pool.add(n * r, "Olvidaste sumar el primer término a1 = $a1 y usaste n saltos en lugar de n - 1.")
        pool.add((n - 1) * r, "Te faltó sumar el primer término a1 = $a1: an = a1 + (n - 1)·r.")
        pool.add(a1 + (n - 2) * r, "Contaste un salto de menos: del primer término al de lugar $n hay ${n - 1} saltos.")
        pool.add(a1 + (n + 1) * r, "Contaste dos saltos de más: del primer término al de lugar $n hay ${n - 1} saltos.")
        pool.add(a1 * n + r, "En una sucesión aritmética se suman saltos iguales a la razón; no se multiplica el primer término por n.")
        return Draft(
            statement = "En la sucesión aritmética ${terms(a1, r, 4)}, ... ¿cuánto vale el término de lugar $n?",
            correct = answer.toString(),
            wrong = pool.items,
            hints = listOf(
                "Para llegar al término de lugar n desde el primero hay que dar n - 1 saltos, y cada salto vale la razón r.",
                "Usa la fórmula an = a1 + (n - 1)·r. Halla la razón restando dos términos consecutivos.",
                "Aquí a1 = $a1, r = $r y n = $n. Calcula $a1 + (${n} - 1)·${p(r)}.",
            ),
            steps = listOf(
                "Identificamos el primer término a1 = $a1 y la razón r = ${a1 + r} - ${p(a1)} = $r.",
                "Fórmula del término general: an = a1 + (n - 1)·r.",
                "Para n = $n: a$n = $a1 + (${n} - 1)·${p(r)} = $a1 + ${n - 1}·${p(r)} = $a1 + ${p((n - 1) * r)} = $answer.",
                "Comprobación por recurrencia: partiendo de $a1 y sumando $r repetidamente ${n - 1} veces se llega a $answer.",
                "Respuesta: $answer.",
            ),
            fillers = fillers(answer),
        )
    }

    // ---- nivel 3 ----

    private fun fromTwoTerms(rng: SeededRandom): Draft {
        val a1 = rng.int(-10, 25)
        val r = rate(rng, 7)
        val (pp, qq, n) = rng.search {
            val pp = int(2, 8)
            val qq = int(pp + 2, pp + 8)
            val n = int(qq + 4, 30)
            listOf(pp, qq, n)
        }
        fun term(k: Int) = a1 + (k - 1) * r
        val u = term(pp)
        val v = term(qq)
        val answer = term(n)
        val pool = pool()
        pool.add(v + (n - pp) * r, "Mezclaste los lugares: desde el lugar $qq hay ${n - qq} saltos hasta el lugar $n, no ${n - pp}.")
        pool.add(u + (n - qq) * r, "Desde el lugar $pp hasta el lugar $n hay ${n - pp} saltos, no ${n - qq}.")
        pool.add(u + n * r, "Del lugar $pp al lugar $n hay ${n - pp} saltos, no $n.")
        pool.add(u - (n - pp) * r, "Revisa el signo de la razón: es $r (${if (r > 0) "la sucesión crece" else "la sucesión decrece"}).")
        pool.add(u + (n - pp + 1) * r, "Contaste un salto de más: del lugar $pp al lugar $n hay ${n - pp} saltos.")
        pool.add(u + (n - pp - 1) * r, "Contaste un salto de menos: del lugar $pp al lugar $n hay ${n - pp} saltos.")
        return Draft(
            statement = "En una sucesión aritmética, el término de lugar $pp vale $u y el término de lugar $qq vale $v. " +
                "¿Cuánto vale el término de lugar $n?",
            correct = answer.toString(),
            wrong = pool.items,
            hints = listOf(
                "Entre dos términos de una sucesión aritmética, la diferencia de valores es igual a la razón por el número de saltos que los separa.",
                "Entre el lugar $pp y el lugar $qq hay ${qq - pp} saltos: r = (${p(v)} - ${p(u)}) ÷ ${qq - pp}. Luego avanza desde el lugar $pp hasta el lugar $n.",
                "La razón es $r. Del lugar $pp al lugar $n hay ${n - pp} saltos: calcula $u + ${n - pp}·${p(r)}.",
            ),
            steps = listOf(
                "Entre el lugar $pp y el lugar $qq hay ${qq - pp} saltos iguales a la razón.",
                "Razón: r = (${p(v)} - ${p(u)}) ÷ ${qq - pp} = ${v - u} ÷ ${qq - pp} = $r.",
                "Del lugar $pp al lugar $n hay ${n - pp} saltos: a$n = $u + ${n - pp}·${p(r)} = $u + ${p((n - pp) * r)} = $answer.",
                "Comprobación con el primer término: a1 = $u - ${pp - 1}·${p(r)} = $a1 y a$n = $a1 + ${n - 1}·${p(r)} = $answer.",
                "Respuesta: $answer.",
            ),
            fillers = fillers(answer),
        )
    }

    private fun countTerms(rng: SeededRandom): Draft {
        val a1 = rng.int(-10, 30)
        val r = rate(rng, 8)
        val n = rng.int(12, 45)
        val last = a1 + (n - 1) * r
        val pool = pool(min = 1)
        pool.add(n - 1, "Ese es el número de saltos. Los términos son uno más que los saltos: n = (último - primero) ÷ r + 1.")
        pool.add(n + 1, "Sumaste un término de más: n = (último - primero) ÷ r + 1.")
        pool.add(n - 2, "Restaste uno de más al contar los términos.")
        if (last % r == 0) pool.add(last / r, "Dividiste el último término entre la razón. Debes dividir la DIFERENCIA (último - primero).")
        pool.add(last - a1, "Hallaste la diferencia entre el último y el primero, pero falta dividirla entre la razón $r y sumar 1.")
        return Draft(
            statement = "¿Cuántos términos tiene la sucesión aritmética ${terms(a1, r, 3)}, ..., $last?",
            correct = n.toString(),
            wrong = pool.items,
            hints = listOf(
                "El último término es a1 + (n - 1)·r. Despejando, el número de saltos es (último - primero) ÷ r.",
                "Calcula la razón, luego (último - primero) ÷ r para saber cuántos saltos hay. Los términos son los saltos más 1.",
                "La razón es $r y (${p(last)} - ${p(a1)}) ÷ ${p(r)} = ${(last - a1) / r}. Suma 1 para contar el primer término.",
            ),
            steps = listOf(
                "El primer término es $a1, el último es $last y la razón es r = ${a1 + r} - ${p(a1)} = $r.",
                "Número de saltos: (${p(last)} - ${p(a1)}) ÷ ${p(r)} = ${last - a1} ÷ ${p(r)} = ${n - 1}.",
                "Hay un término más que saltos: n = ${n - 1} + 1 = $n.",
                "Comprobación: a$n = $a1 + ${n - 1}·${p(r)} = $last.",
                "Respuesta: $n términos.",
            ),
            fillers = fillers(n).filter { it.text.toInt() > 0 },
        )
    }

    companion object {
        const val TOPIC_ID = "sucesiones-aritmeticas"
        private const val MAX_ABS = 600L
    }
}
