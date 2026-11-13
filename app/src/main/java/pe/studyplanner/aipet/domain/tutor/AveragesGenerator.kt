package pe.studyplanner.aipet.domain.tutor

/**
 * Promedios aritmético y ponderado.
 * Nivel 1: promedio de una lista de datos. Nivel 2: dato que falta conociendo el promedio, o suma total a partir del
 * promedio. Nivel 3: promedio ponderado, promedio de dos grupos juntos, o dato que hace subir el promedio.
 *
 * Los datos se construyen a partir del promedio, así que todas las respuestas son enteras.
 */
class AveragesGenerator : BaseGenerator(TOPIC_ID) {

    override fun draft(rng: SeededRandom, difficulty: Int): Draft = when (difficulty) {
        1 -> plainMean(rng)
        2 -> if (rng.coin()) missingValue(rng) else totalFromMean(rng)
        else -> when (rng.below(3)) {
            0 -> weighted(rng)
            1 -> groups(rng)
            else -> addedValue(rng)
        }
    }

    // ---- utilidades ----

    private fun pool() = WrongPool(valid = { it.isInteger && it.num in 1..MAX_OPTION })

    private fun fillers(correct: Int): List<Wrong> =
        listOf(1, -1, 2, -2, 3, -3, 4, -4, 5, -5).map { correct + it }.filter { it in 1..MAX_OPTION }
            .map { Wrong(it.toString(), GENERIC_FEEDBACK) }

    private fun listText(values: List<Int>): String = values.dropLast(1).joinToString(", ") + " y " + values.last()

    private fun sumText(values: List<Int>): String = values.joinToString(" + ")

    /** [count] datos en [range] cuyo promedio es exactamente [mean]; el último se calcula para cuadrar la suma. */
    private fun dataWithMean(rng: SeededRandom, count: Int, mean: Int, range: IntRange, spread: Int): List<Int> =
        rng.search {
            val lo = maxOf(range.first, mean - spread)
            val hi = minOf(range.last, mean + spread)
            val values = MutableList(count - 1) { int(lo, hi) }
            val last = count * mean - values.sum()
            values += last
            if (last !in range || values.toSet().size < 3) null else values.toList()
        }

    private fun median(values: List<Int>): Rational {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) rat(sorted[n / 2]) else Rational.of(sorted[n / 2 - 1] + sorted[n / 2], 2)
    }

    // ---- nivel 1 ----

    private fun plainMean(rng: SeededRandom): Draft {
        val count = rng.int(4, 6)
        val kind = rng.below(3)
        val (mean, range, spread) = when (kind) {
            0 -> Triple(rng.int(9, 17), 5..20, 5)
            1 -> Triple(rng.int(12, 30), 5..45, 8)
            else -> Triple(rng.int(10, 35), 3..50, 9)
        }
        val values = dataWithMean(rng, count, mean, range, spread)
        val sum = mean * count
        val statement = when (kind) {
            0 -> "Las notas de ${rng.pick(NAMES)} en sus prácticas fueron ${listText(values)}. ¿Cuál es el promedio de sus notas?"
            1 -> "En $count días seguidos, una tienda vendió ${listText(values)} cuadernos, en ese orden. ¿Cuántos cuadernos vendió en promedio por día?"
            else -> "Halla el promedio aritmético de ${listText(values)}."
        }
        val pool = pool()
        pool.add(sum, "Sumaste los $count datos pero no dividiste. El promedio es la suma entre la cantidad de datos.")
        pool.add(Rational.of(sum, count - 1), "Dividiste entre ${count - 1} en lugar de $count. Se divide entre la cantidad de datos, que es $count.")
        pool.add(Rational.of(sum, count + 1), "Dividiste entre ${count + 1} en lugar de $count. Se divide entre la cantidad de datos, que es $count.")
        pool.add(median(values), "Tomaste el valor central. Eso es la mediana; el promedio se calcula con la suma de todos los datos.")
        pool.add(Rational.of(values.max() + values.min(), 2), "Promediaste solo el mayor y el menor. Hay que usar todos los datos.")
        pool.add(values.max() - values.min(), "Restaste el mayor y el menor. Eso es el rango; el promedio es la suma entre la cantidad de datos.")
        return Draft(
            statement = statement,
            correct = mean.toString(),
            wrong = pool.items,
            hints = listOf(
                "El promedio aritmético es lo que le tocaría a cada dato si el total se repartiera en partes iguales.",
                "Suma todos los datos y divide el resultado entre la cantidad de datos.",
                "Hay $count datos. Calcula ${sumText(values)} y divide entre $count.",
            ),
            steps = listOf(
                "Promedio = suma de los datos ÷ cantidad de datos.",
                "Sumamos los datos: ${sumText(values)} = $sum.",
                "Hay $count datos, así que dividimos: $sum ÷ $count = $mean.",
                "Comprobación: $mean × $count = $sum, la suma de los datos.",
                "Respuesta: $mean.",
            ),
            fillers = fillers(mean),
        )
    }

    // ---- nivel 2 ----

    private fun missingValue(rng: SeededRandom): Draft {
        val count = rng.int(4, 6)
        val mean = rng.int(10, 30)
        val values = dataWithMean(rng, count, mean, 3..60, 9)
        val known = values.dropLast(1)
        val answer = values.last()
        val total = mean * count
        val knownSum = known.sum()
        val pool = pool()
        pool.add(mean, "Diste el promedio. El número que falta se halla con la suma total (promedio × cantidad) menos la suma de los conocidos.")
        pool.add(total, "Hallaste la suma total de los $count números, pero falta restarle la suma de los conocidos ($knownSum).")
        pool.add((count - 1) * mean - knownSum, "Usaste ${count - 1} números en lugar de $count para la suma total. El promedio es de $count números.")
        pool.add((count + 1) * mean - knownSum, "Usaste ${count + 1} números en lugar de $count para la suma total. El promedio es de $count números.")
        pool.add(knownSum, "Sumaste los números conocidos y diste ese resultado. Falta compararlo con la suma total ($total).")
        return Draft(
            statement = "El promedio de $count números es $mean. Si ${count - 1} de ellos son ${listText(known)}, ¿cuál es el número que falta?",
            correct = answer.toString(),
            wrong = pool.items,
            hints = listOf(
                "Si conoces el promedio y la cantidad de números, conoces la suma total: promedio × cantidad.",
                "Calcula la suma total y réstale la suma de los números conocidos.",
                "La suma total es $mean × $count. La suma de los conocidos es ${sumText(known)}. Resta ambas.",
            ),
            steps = listOf(
                "Suma total = promedio × cantidad de números = $mean × $count = $total.",
                "Suma de los ${count - 1} números conocidos: ${sumText(known)} = $knownSum.",
                "El número que falta es la diferencia: $total - $knownSum = $answer.",
                "Comprobación: ($knownSum + $answer) ÷ $count = $mean.",
                "Respuesta: $answer.",
            ),
            fillers = fillers(answer),
        )
    }

    private fun totalFromMean(rng: SeededRandom): Draft {
        val count = rng.int(4, 9)
        val mean = rng.int(12, 40)
        val total = count * mean
        val pool = pool()
        pool.add(mean + count, "Sumaste el promedio y la cantidad. La suma total es el promedio multiplicado por la cantidad.")
        pool.add((count - 1) * mean, "Multiplicaste por ${count - 1} en lugar de $count. Hay $count amigos.")
        pool.add((count + 1) * mean, "Multiplicaste por ${count + 1} en lugar de $count. Hay $count amigos.")
        pool.add(count * mean + mean, "Contaste el promedio una vez de más.")
        pool.add(Rational.of(mean, count), "Dividiste el promedio entre la cantidad. Para hallar la suma hay que multiplicar.")
        pool.add(mean, "Diste el promedio. La suma total es el promedio multiplicado por la cantidad de datos.")
        return Draft(
            statement = "El promedio de las edades de $count amigos es $mean años. ¿Cuánto suman las edades de todos?",
            correct = total.toString(),
            wrong = pool.items,
            hints = listOf(
                "El promedio es la suma entre la cantidad de datos. Entonces la suma es el promedio por la cantidad.",
                "Multiplica el promedio por el número de amigos.",
                "Calcula $mean × $count.",
            ),
            steps = listOf(
                "Promedio = suma ÷ cantidad, así que suma = promedio × cantidad.",
                "Hay $count amigos y el promedio es $mean años.",
                "Suma de las edades = $mean × $count = $total.",
                "Comprobación: $total ÷ $count = $mean, el promedio dado.",
                "Respuesta: $total.",
            ),
            fillers = fillers(total),
        )
    }

    // ---- nivel 3 ----

    private fun weighted(rng: SeededRandom): Draft {
        val (weights, notes) = rng.search {
            val w = pick(WEIGHTS)
            val n = List(3) { int(6, 20) }
            val total = n[0] * w[0] + n[1] * w[1] + n[2] * w[2]
            val weightSum = w.sum()
            val simple = Rational.of(n.sum(), 3)
            if (total % weightSum != 0 || simple == rat(total / weightSum) || n.toSet().size < 3) null else w to n
        }
        val weightSum = weights.sum()
        val products = notes.indices.map { notes[it] * weights[it] }
        val total = products.sum()
        val answer = total / weightSum
        val pool = pool()
        pool.add(Rational.of(notes.sum(), 3), "Promediaste las tres notas sin tomar en cuenta los pesos. En un promedio ponderado cada nota cuenta según su peso.")
        pool.add(total, "Hallaste la suma de los productos nota × peso, pero falta dividir entre la suma de los pesos ($weightSum).")
        pool.add(Rational.of(total, 3), "Dividiste entre 3, la cantidad de notas. En un promedio ponderado se divide entre la suma de los pesos ($weightSum).")
        val reversed = notes[0] * weights[2] + notes[1] * weights[1] + notes[2] * weights[0]
        pool.add(Rational.of(reversed, weightSum), "Asignaste los pesos en orden inverso. La práctica pesa ${weights[0]}, el parcial ${weights[1]} y el final ${weights[2]}.")
        pool.add(notes.sum(), "Sumaste las notas sin pesos ni división.")
        pool.add(Rational.of(total, weightSum + 1), "Dividiste entre ${weightSum + 1}. Se divide entre la suma de los pesos: ${weights.joinToString(" + ")} = $weightSum.")
        return Draft(
            statement = "En un curso, la nota final se calcula con pesos: la práctica tiene peso ${weights[0]}, el parcial tiene peso ${weights[1]} " +
                "y el examen final tiene peso ${weights[2]}. Un alumno sacó ${notes[0]} en la práctica, ${notes[1]} en el parcial y " +
                "${notes[2]} en el final. ¿Cuál es su nota final?",
            correct = answer.toString(),
            wrong = pool.items,
            hints = listOf(
                "En el promedio ponderado cada dato se multiplica por su peso. Después se divide entre la suma de los pesos, no entre la cantidad de datos.",
                "Calcula nota × peso para cada una, suma los productos y divide entre la suma de los pesos.",
                "La suma de los pesos es ${weights.joinToString(" + ")} = $weightSum. Los productos son ${notes[0]} × ${weights[0]}, ${notes[1]} × ${weights[1]} y ${notes[2]} × ${weights[2]}.",
            ),
            steps = listOf(
                "Promedio ponderado = suma de (nota × peso) ÷ suma de los pesos.",
                "Productos: ${notes.indices.joinToString(" + ") { "${notes[it]} × ${weights[it]}" }} = ${products.joinToString(" + ")} = $total.",
                "Suma de los pesos: ${weights.joinToString(" + ")} = $weightSum.",
                "Nota final = $total ÷ $weightSum = $answer.",
                "Respuesta: $answer.",
            ),
            fillers = fillers(answer),
        )
    }

    private fun groups(rng: SeededRandom): Draft {
        val (n1, n2, m1, m2) = rng.search {
            val n1 = int(5, 30)
            val n2 = int(5, 30)
            val m1 = int(8, 18)
            val m2 = int(8, 18)
            val total = n1 * m1 + n2 * m2
            if (n1 == n2 || m1 == m2 || total % (n1 + n2) != 0) null else listOf(n1, n2, m1, m2)
        }
        val s1 = n1 * m1
        val s2 = n2 * m2
        val total = s1 + s2
        val count = n1 + n2
        val answer = total / count
        val pool = pool()
        pool.add(Rational.of(m1 + m2, 2), "Promediaste los dos promedios. Los grupos tienen distinto número de alumnos, así que cada promedio pesa distinto.")
        pool.add(total, "Hallaste la suma total de las notas ($s1 + $s2), pero falta dividir entre los $count alumnos.")
        pool.add(Rational.of(total, 2), "Dividiste entre 2, la cantidad de grupos. Hay que dividir entre el total de alumnos ($count).")
        pool.add(m1, "Diste el promedio del primer grupo. Falta incluir al segundo grupo.")
        pool.add(m2, "Diste el promedio del segundo grupo. Falta incluir al primer grupo.")
        pool.add(Rational.of(total, count + 1), "Dividiste entre ${count + 1}. El total de alumnos es $n1 + $n2 = $count.")
        return Draft(
            statement = "El promedio de las notas de $n1 alumnos es $m1, y el de otros $n2 alumnos es $m2. " +
                "¿Cuál es el promedio de los $count alumnos juntos?",
            correct = answer.toString(),
            wrong = pool.items,
            hints = listOf(
                "No se pueden promediar los promedios sin más, porque los grupos tienen distinto tamaño. Hay que trabajar con las sumas.",
                "Halla la suma de las notas de cada grupo (promedio × alumnos), súmalas y divide entre el total de alumnos.",
                "Calcula $m1 × $n1 y $m2 × $n2. Suma ambos resultados y divide entre $n1 + $n2.",
            ),
            steps = listOf(
                "La suma de cada grupo es su promedio por su cantidad de alumnos.",
                "Primer grupo: $m1 × $n1 = $s1. Segundo grupo: $m2 × $n2 = $s2.",
                "Suma total de las notas: $s1 + $s2 = $total. Total de alumnos: $n1 + $n2 = $count.",
                "Promedio de los dos grupos: $total ÷ $count = $answer.",
                "Respuesta: $answer.",
            ),
            fillers = fillers(answer),
        )
    }

    private fun addedValue(rng: SeededRandom): Draft {
        val count = rng.int(4, 9)
        val m1 = rng.int(10, 30)
        val d = rng.int(1, 4)
        val m2 = m1 + d
        val s1 = count * m1
        val s2 = (count + 1) * m2
        val answer = s2 - s1
        val pool = pool()
        pool.add(m2, "Diste el nuevo promedio. El número agregado es la diferencia entre la suma nueva y la suma anterior.")
        pool.add(s2, "Hallaste la suma nueva de los ${count + 1} números, pero falta restarle la suma anterior ($s1).")
        pool.add(s2 - m1, "Restaste el promedio anterior en lugar de la suma anterior. La suma de los $count números era $count × $m1 = $s1.")
        pool.add(count * d, "Calculaste solo lo que sube la suma de los $count números anteriores ($count × $d). El número agregado también debe aportar el promedio anterior y su propio aumento.")
        pool.add(d * (count + 1), "Calculaste solo lo que sube la suma con el nuevo promedio (${count + 1} × $d). Falta sumar el promedio anterior ($m1).")
        pool.add(m1 + d, "Sumaste el aumento al promedio anterior, pero ese es el nuevo promedio, no el número agregado.")
        return Draft(
            statement = "El promedio de $count números es $m1. Al agregar un número más, el promedio sube a $m2. ¿Cuál es el número que se agregó?",
            correct = answer.toString(),
            wrong = pool.items,
            hints = listOf(
                "Con el promedio y la cantidad de números puedes hallar la suma antes y después de agregar el número.",
                "El número agregado es la suma nueva menos la suma anterior.",
                "Suma anterior: $m1 × $count. Suma nueva: $m2 × ${count + 1}. Resta la anterior de la nueva.",
            ),
            steps = listOf(
                "Suma anterior de los $count números: $m1 × $count = $s1.",
                "Ahora hay ${count + 1} números con promedio $m2, así que la suma nueva es $m2 × ${count + 1} = $s2.",
                "El número agregado es la diferencia entre las dos sumas: $s2 - $s1 = $answer.",
                "Comprobación: ($s1 + $answer) ÷ ${count + 1} = $s2 ÷ ${count + 1} = $m2.",
                "Respuesta: $answer.",
            ),
            fillers = fillers(answer),
        )
    }

    companion object {
        const val TOPIC_ID = "promedios"
        private const val MAX_OPTION = 5000
        private val NAMES = listOf("Rosa", "Luis", "Marta", "Diego", "Carla", "Mateo", "Elena", "Pablo")
        private val WEIGHTS = listOf(
            listOf(1, 2, 3), listOf(1, 1, 2), listOf(2, 3, 5), listOf(1, 2, 2), listOf(3, 3, 4), listOf(1, 3, 6), listOf(2, 2, 1),
        )
    }
}
