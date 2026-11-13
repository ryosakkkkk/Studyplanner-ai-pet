package pe.studyplanner.aipet.domain.tutor

import kotlin.math.abs

/**
 * Teorema de Pitágoras.
 * Nivel 1: hipotenusa a partir de los catetos. Nivel 2: un cateto a partir de la hipotenusa y el otro cateto.
 * Nivel 3: problemas contextualizados (escalera, diagonal de un terreno, distancia en línea recta, diagonal de un cuadrado).
 */
class PythagorasGenerator : BaseGenerator(TOPIC_ID) {

    /** Terna pitagórica escalada: catetos [a], [b] e hipotenusa [c]. */
    private class Triple3(val a: Int, val b: Int, val c: Int)

    override fun draft(rng: SeededRandom, difficulty: Int): Draft = when (difficulty) {
        1 -> hypotenuse(rng)
        2 -> missingLeg(rng)
        else -> when (rng.below(4)) {
            0 -> ladder(rng)
            1 -> rectangleDiagonal(rng)
            2 -> straightLine(rng)
            else -> squareDiagonal(rng)
        }
    }

    private fun triple(rng: SeededRandom): Triple3 {
        val (a, b, c) = rng.pick(PRIMITIVES)
        val k = rng.search { int(1, 6).takeIf { it * maxOf(a, b, c) <= MAX_SIDE } }
        return if (rng.coin()) Triple3(a * k, b * k, c * k) else Triple3(b * k, a * k, c * k)
    }

    private fun lengthPool(unit: String) = WrongPool(
        format = { "${it.toIntExact()} $unit" },
        valid = { it.isInteger && it.num in 1..MAX_OPTION },
    )

    private fun sqrtText(radicand: Int, unit: String): String = "${Surd.of(rat(1), radicand.toLong())} $unit"

    private fun lengthFillers(correct: Int, unit: String): List<Wrong> =
        listOf(1, -1, 2, -2, 3, -3, 4, -4, 5, -5).map { correct + it }.filter { it > 0 }
            .map { Wrong("$it $unit", GENERIC_FEEDBACK) }

    // ---- nivel 1 ----

    private fun hypotenuse(rng: SeededRandom): Draft {
        val t = triple(rng)
        val unit = rng.pick(UNITS)
        val pool = lengthPool(unit)
        pool.add(t.a + t.b, "Sumaste los catetos. La hipotenusa NO es la suma: se obtiene con c² = a² + b².")
        pool.add(t.a * t.a + t.b * t.b, "Ese es c², te falta sacar la raíz cuadrada para hallar c.")
        pool.add(abs(t.a - t.b), "Restaste los catetos. La hipotenusa se calcula con c² = a² + b².")
        pool.addSecondaryText(sqrtText(t.a + t.b, unit), "Sacaste la raíz de a + b. Primero hay que elevar al cuadrado cada cateto: √(a² + b²).")
        pool.add(t.a * t.b / 2, "Calculaste el área del triángulo. Se pide la hipotenusa: c = √(a² + b²).")
        return Draft(
            statement = "Un triángulo rectángulo tiene catetos de ${t.a} $unit y ${t.b} $unit. ¿Cuánto mide la hipotenusa?",
            correct = "${t.c} $unit",
            wrong = pool.items,
            hints = listOf(
                "En todo triángulo rectángulo se cumple a² + b² = c², donde c es la hipotenusa (el lado opuesto al ángulo recto).",
                "Eleva al cuadrado cada cateto, súmalos y luego saca la raíz cuadrada del resultado.",
                "${t.a}² + ${t.b}² = ${t.a * t.a} + ${t.b * t.b} = ${t.a * t.a + t.b * t.b}. ¿Qué número elevado al cuadrado da ${t.a * t.a + t.b * t.b}?",
            ),
            steps = listOf(
                "Aplicamos el teorema de Pitágoras: c² = a² + b².",
                "Reemplazamos: c² = ${t.a}² + ${t.b}² = ${t.a * t.a} + ${t.b * t.b} = ${t.c * t.c}.",
                "Sacamos la raíz cuadrada: c = √${t.c * t.c} = ${t.c}.",
                "Comprobación: ${t.c}² = ${t.c * t.c} y ${t.a}² + ${t.b}² = ${t.a * t.a + t.b * t.b}. Son iguales.",
                "Respuesta: ${t.c} $unit.",
            ),
            fillers = pool.secondary + lengthFillers(t.c, unit),
        )
    }

    // ---- nivel 2 ----

    private fun missingLeg(rng: SeededRandom): Draft {
        val t = triple(rng)
        val unit = rng.pick(UNITS)
        val pool = lengthPool(unit)
        pool.add(t.c - t.a, "Restaste las longitudes. Hay que restar los CUADRADOS: b² = c² - a².")
        pool.add(t.c + t.a, "Sumaste la hipotenusa y el cateto. Se despeja b² = c² - a².")
        pool.add(t.c * t.c - t.a * t.a, "Ese es b², te falta sacar la raíz cuadrada para hallar b.")
        pool.addSecondaryText(sqrtText(t.c * t.c + t.a * t.a, unit), "Sumaste los cuadrados, pero la hipotenusa es el lado mayor: b² = c² - a².")
        pool.add(t.c, "Esa es la hipotenusa dada. Se pide el otro cateto, que es menor.")
        return Draft(
            statement = "Un triángulo rectángulo tiene hipotenusa de ${t.c} $unit y un cateto de ${t.a} $unit. ¿Cuánto mide el otro cateto?",
            correct = "${t.b} $unit",
            wrong = pool.items,
            hints = listOf(
                "En todo triángulo rectángulo se cumple a² + b² = c². La hipotenusa c es el lado más largo.",
                "Despeja el cateto que falta: b² = c² - a². Luego saca la raíz cuadrada.",
                "${t.c}² - ${t.a}² = ${t.c * t.c} - ${t.a * t.a} = ${t.b * t.b}. ¿Qué número elevado al cuadrado da ${t.b * t.b}?",
            ),
            steps = listOf(
                "Aplicamos el teorema de Pitágoras: a² + b² = c², entonces b² = c² - a².",
                "Reemplazamos: b² = ${t.c}² - ${t.a}² = ${t.c * t.c} - ${t.a * t.a} = ${t.b * t.b}.",
                "Sacamos la raíz cuadrada: b = √${t.b * t.b} = ${t.b}.",
                "Comprobación: ${t.a}² + ${t.b}² = ${t.a * t.a} + ${t.b * t.b} = ${t.c * t.c} = ${t.c}².",
                "Respuesta: ${t.b} $unit.",
            ),
            fillers = pool.secondary + lengthFillers(t.b, unit),
        )
    }

    // ---- nivel 3 ----

    private fun ladder(rng: SeededRandom): Draft {
        val t = triple(rng)
        val pool = lengthPool("m")
        pool.add(t.c - t.a, "Restaste las longitudes. Hay que usar los cuadrados: altura² = escalera² - base².")
        pool.add(t.c + t.a, "Sumaste la escalera y la base. La altura sale de altura² = escalera² - base².")
        pool.add(t.c * t.c - t.a * t.a, "Ese es la altura al cuadrado: falta la raíz cuadrada.")
        pool.addSecondaryText(sqrtText(t.c * t.c + t.a * t.a, "m"), "La escalera es la hipotenusa (el lado más largo): altura² = escalera² - base², no una suma.")
        pool.add(t.c, "Esa es la longitud de la escalera. La altura es menor, porque la escalera es la hipotenusa.")
        return Draft(
            statement = "Una escalera de ${t.c} m se apoya en una pared. Su base está a ${t.a} m de la pared. " +
                "¿A qué altura de la pared llega la escalera?",
            correct = "${t.b} m",
            wrong = pool.items,
            hints = listOf(
                "Dibuja un triángulo rectángulo: la pared y el suelo forman el ángulo recto, y la escalera es la hipotenusa.",
                "Despeja el cateto que falta: altura² = escalera² - base². Luego saca la raíz cuadrada.",
                "${t.c}² - ${t.a}² = ${t.c * t.c} - ${t.a * t.a} = ${t.b * t.b}. ¿Qué número elevado al cuadrado da ${t.b * t.b}?",
            ),
            steps = listOf(
                "La escalera es la hipotenusa (${t.c} m), la base es un cateto (${t.a} m) y la altura es el otro cateto.",
                "Pitágoras: altura² = ${t.c}² - ${t.a}² = ${t.c * t.c} - ${t.a * t.a} = ${t.b * t.b}.",
                "Altura = √${t.b * t.b} = ${t.b} m.",
                "Comprobación: ${t.a}² + ${t.b}² = ${t.a * t.a + t.b * t.b} = ${t.c}².",
                "Respuesta: ${t.b} m.",
            ),
            fillers = pool.secondary + lengthFillers(t.b, "m"),
        )
    }

    private fun rectangleDiagonal(rng: SeededRandom): Draft {
        val t = triple(rng)
        val pool = lengthPool("m")
        pool.add(t.a + t.b, "Sumaste el largo y el ancho. La diagonal es la hipotenusa: c = √(a² + b²).")
        pool.add(t.a * t.a + t.b * t.b, "Ese es c², te falta sacar la raíz cuadrada.")
        pool.add(abs(t.a - t.b), "Restaste el largo y el ancho. La diagonal se calcula con c² = a² + b².")
        pool.addSecondaryText(sqrtText(t.a + t.b, "m"), "Sacaste la raíz de la suma de los lados. Primero hay que elevar al cuadrado cada lado.")
        pool.add(2 * (t.a + t.b), "Calculaste el perímetro. Se pide la diagonal.")
        return Draft(
            statement = "Un terreno rectangular mide ${t.a} m de largo y ${t.b} m de ancho. ¿Cuánto mide su diagonal?",
            correct = "${t.c} m",
            wrong = pool.items,
            hints = listOf(
                "La diagonal divide al rectángulo en dos triángulos rectángulos: el largo y el ancho son los catetos.",
                "La diagonal es la hipotenusa: eleva al cuadrado el largo y el ancho, súmalos y saca la raíz.",
                "${t.a}² + ${t.b}² = ${t.a * t.a} + ${t.b * t.b} = ${t.c * t.c}. ¿Qué número elevado al cuadrado da ${t.c * t.c}?",
            ),
            steps = listOf(
                "La diagonal es la hipotenusa de un triángulo rectángulo cuyos catetos son ${t.a} m y ${t.b} m.",
                "Pitágoras: d² = ${t.a}² + ${t.b}² = ${t.a * t.a} + ${t.b * t.b} = ${t.c * t.c}.",
                "d = √${t.c * t.c} = ${t.c} m.",
                "Comprobación: ${t.c}² = ${t.c * t.c}.",
                "Respuesta: ${t.c} m.",
            ),
            fillers = pool.secondary + lengthFillers(t.c, "m"),
        )
    }

    private fun straightLine(rng: SeededRandom): Draft {
        val t = triple(rng)
        val pool = lengthPool("km")
        pool.add(t.a + t.b, "Sumaste los dos tramos. La distancia en línea recta es la hipotenusa: √(a² + b²).")
        pool.add(t.a * t.a + t.b * t.b, "Ese es el cuadrado de la distancia: falta la raíz cuadrada.")
        pool.add(abs(t.a - t.b), "Restaste los tramos. Los tramos norte y este forman un ángulo recto: usa Pitágoras.")
        pool.addSecondaryText(sqrtText(t.a + t.b, "km"), "Sacaste la raíz de la suma de los tramos. Primero hay que elevar al cuadrado cada tramo.")
        return Draft(
            statement = "Un ciclista parte de un punto, recorre ${t.a} km hacia el norte y luego ${t.b} km hacia el este. " +
                "¿A qué distancia en línea recta está del punto de partida?",
            correct = "${t.c} km",
            wrong = pool.items,
            hints = listOf(
                "El norte y el este forman un ángulo recto: los dos tramos son los catetos y la distancia en línea recta es la hipotenusa.",
                "Eleva al cuadrado cada tramo, súmalos y saca la raíz cuadrada.",
                "${t.a}² + ${t.b}² = ${t.a * t.a} + ${t.b * t.b} = ${t.c * t.c}. ¿Qué número elevado al cuadrado da ${t.c * t.c}?",
            ),
            steps = listOf(
                "Los tramos al norte (${t.a} km) y al este (${t.b} km) forman un ángulo recto: son los catetos.",
                "Pitágoras: d² = ${t.a}² + ${t.b}² = ${t.a * t.a} + ${t.b * t.b} = ${t.c * t.c}.",
                "d = √${t.c * t.c} = ${t.c} km.",
                "Comprobación: ${t.c}² = ${t.c * t.c}.",
                "Respuesta: ${t.c} km.",
            ),
            fillers = pool.secondary + lengthFillers(t.c, "km"),
        )
    }

    private fun squareDiagonal(rng: SeededRandom): Draft {
        val a = rng.int(2, 12)
        val answer = Surd.of(rat(a), 2)
        val wrongs = listOf(
            "${2 * a} cm" to "La diagonal no es el doble del lado: d² = a² + a² = 2a², entonces d = a√2.",
            "${a * a} cm" to "Ese número es el área del cuadrado. La diagonal sale de d² = a² + a².",
            "${Surd.of(rat(a), 3)} cm" to "El radicando es 2, no 3: d² = a² + a² = 2a².",
            "${Surd.of(Rational.of(a, 2), 2)} cm" to "Esa sería la mitad de la diagonal. La diagonal completa es a√2.",
            "${Surd.of(rat(2 * a), 2)} cm" to "Multiplicaste por 2 y además pusiste √2: d = a√2, sin el factor 2.",
        )
        val items = wrongs.filter { it.first != "$answer cm" }.map { Wrong(it.first, it.second) }
        val d2 = 2 * a * a
        return Draft(
            statement = "Un cuadrado tiene lados de $a cm. ¿Cuánto mide su diagonal?",
            correct = "$answer cm",
            wrong = items,
            hints = listOf(
                "La diagonal divide al cuadrado en dos triángulos rectángulos. Los lados del cuadrado son los catetos y la diagonal es la hipotenusa.",
                "Aplica Pitágoras: d² = lado² + lado². Luego saca la raíz y simplifica el radical.",
                "d² = $a² + $a² = $d2. Simplifica √$d2 extrayendo el factor que es cuadrado perfecto.",
            ),
            steps = listOf(
                "La diagonal es la hipotenusa de un triángulo rectángulo de catetos $a cm y $a cm.",
                "Pitágoras: d² = $a² + $a² = ${a * a} + ${a * a} = $d2.",
                "d = √$d2 = √(${a * a} · 2) = $a√2.",
                "Comprobación: ($a√2)² = ${a * a} · 2 = $d2.",
                "Respuesta: $answer cm.",
            ),
            fillers = listOf(2, 3, 5, 7, 11).map { Wrong("${Surd.of(rat(a), it)} cm", GENERIC_FEEDBACK) }
                .filter { it.text != "$answer cm" },
        )
    }

    companion object {
        const val TOPIC_ID = "pitagoras"
        private const val MAX_SIDE = 50
        private const val MAX_OPTION = 20000L
        private val UNITS = listOf("cm", "m")
        private val PRIMITIVES = listOf(
            listOf(3, 4, 5), listOf(5, 12, 13), listOf(8, 15, 17), listOf(7, 24, 25),
            listOf(20, 21, 29), listOf(9, 40, 41), listOf(12, 35, 37),
        )
    }
}
