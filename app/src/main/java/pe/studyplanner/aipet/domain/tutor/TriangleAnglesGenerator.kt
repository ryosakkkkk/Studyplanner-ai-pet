package pe.studyplanner.aipet.domain.tutor

import pe.studyplanner.aipet.domain.tutor.Fmt.combine
import pe.studyplanner.aipet.domain.tutor.Fmt.degrees
import kotlin.math.abs

/**
 * Ángulos en el triángulo: suma de ángulos interiores (180°) y ángulo exterior.
 * Nivel 1: tercer ángulo con dos datos numéricos. Nivel 2: ángulos con expresiones en x.
 * Nivel 3: ángulo exterior (numérico y con expresiones en x).
 */
class TriangleAnglesGenerator : BaseGenerator(TOPIC_ID) {

    override fun draft(rng: SeededRandom, difficulty: Int): Draft = when (difficulty) {
        1 -> thirdAngle(rng)
        2 -> algebraicAngle(rng)
        else -> when (rng.below(4)) {
            0 -> exteriorFromInteriors(rng)
            1 -> interiorFromExterior(rng)
            2 -> adjacentInterior(rng)
            else -> exteriorEquation(rng)
        }
    }

    private fun degreePool() = WrongPool(
        format = { degrees(it.toIntExact()) },
        valid = { it.isInteger && it.num in 1..MAX_DEGREES },
    )

    private fun degreeFillers(correct: Int): List<Wrong> =
        listOf(5, -5, 10, -10, 15, -15, 20, -20).map { correct + it }.filter { it in 1..MAX_DEGREES.toInt() }
            .map { Wrong(degrees(it), GENERIC_FEEDBACK) }

    // ---- nivel 1 ----

    private fun thirdAngle(rng: SeededRandom): Draft {
        val labels = listOf("A", "B", "C")
        val unknown = rng.pick(labels)
        val (a, b) = rng.search {
            val a = int(25, 95)
            val b = int(25, 95)
            if (180 - a - b < 10) null else a to b
        }
        val answer = 180 - a - b
        val known = labels.filter { it != unknown }
        val pool = degreePool()
        pool.add(a + b, "Esa es la suma de los dos ángulos dados. Falta restarla de 180°: el tercer ángulo completa los 180°.")
        pool.add(360 - a - b, "Los ángulos interiores de un triángulo suman 180°, no 360°.")
        pool.add(180 - a, "Solo restaste uno de los ángulos conocidos. Hay que restar los dos.")
        pool.add(180 - b, "Solo restaste uno de los ángulos conocidos. Hay que restar los dos.")
        pool.add(90 - a, "Usaste 90° como si el triángulo fuera rectángulo. La suma de los ángulos interiores es 180°.")
        pool.add(abs(a - b), "Restaste los ángulos dados entre sí. El tercer ángulo sale de 180° menos la suma de ambos.")
        pool.add(90 - b, "Usaste 90° como si el triángulo fuera rectángulo. La suma de los ángulos interiores es 180°.")
        pool.add(60, "Supusiste que los tres ángulos son iguales (60°). Eso solo pasa en un triángulo equilátero.")
        return Draft(
            statement = "En el triángulo ABC, el ángulo ${known[0]} mide ${degrees(a)} y el ángulo ${known[1]} mide ${degrees(b)}. " +
                "¿Cuánto mide el ángulo $unknown?",
            correct = degrees(answer),
            wrong = pool.items,
            hints = listOf(
                "La suma de los ángulos interiores de cualquier triángulo es 180°.",
                "Suma los dos ángulos que conoces y réstale ese total a 180°.",
                "$a + $b = ${a + b}. El tercer ángulo es 180° - ${a + b}°.",
            ),
            steps = listOf(
                "Los ángulos interiores de un triángulo suman 180°.",
                "Sumamos los ángulos conocidos: $a° + $b° = ${a + b}°.",
                "El ángulo $unknown mide 180° - ${a + b}° = $answer°.",
                "Comprobación: $a° + $b° + $answer° = ${a + b + answer}°.",
                "Respuesta: ${degrees(answer)}.",
            ),
            fillers = degreeFillers(answer),
        )
    }

    // ---- nivel 2 ----

    private class Lin(val k: Int, val c: Int) {
        fun at(x: Int) = k * x + c
        fun text(): String = combine(k to "x", c to "")
        fun degreeText(): String = if (k != 0 && c != 0) "(${text()})°" else "${text()}°"
    }

    private class Algebraic(val x0: Int, val angles: List<Lin>)

    private fun algebraicAngle(rng: SeededRandom): Draft {
        val d = rng.search {
            val x0 = int(8, 30)
            val ks = listOf(int(1, 3), int(1, 3), int(1, 3))
            val c1 = int(-4, 4) * 5
            val c2 = int(-4, 4) * 5
            val c3 = 180 - ks.sum() * x0 - c1 - c2
            val angles = listOf(Lin(ks[0], c1), Lin(ks[1], c2), Lin(ks[2], c3))
            val values = angles.map { it.at(x0) }
            if (abs(c3) > 40 || (c1 == 0 && c2 == 0 && c3 == 0) || values.any { it < 10 || it > 130 }) null else Algebraic(x0, angles)
        }
        val labels = listOf("A", "B", "C")
        val asked = rng.below(3)
        val x0 = d.x0
        val kSum = d.angles.sumOf { it.k }
        val cSum = d.angles.sumOf { it.c }
        val value = d.angles[asked].at(x0)
        val pool = degreePool()
        pool.add(x0, "Ese es el valor de x, pero la pregunta pide la medida del ángulo: reemplaza x en la expresión.")
        pool.add(d.angles[asked].k * x0, "Olvidaste sumar el término independiente (${d.angles[asked].c}) de la expresión del ángulo.")
        for (j in 0..2) {
            if (j != asked) pool.add(d.angles[j].at(x0), "Calculaste la medida del ángulo ${labels[j]}, pero se pedía la del ángulo ${labels[asked]}.")
        }
        fun withX(xr: Rational, feedback: String) = pool.add(xr * d.angles[asked].k + d.angles[asked].c, feedback)
        withX(rat(180 + cSum) / kSum, "Al pasar ${cSum} al otro lado debe cambiar de signo.")
        withX(rat(180) / kSum, "Te faltó restar los términos independientes ($cSum) antes de dividir entre $kSum.")
        withX(rat(360 - cSum) / kSum, "Los ángulos interiores de un triángulo suman 180°, no 360°.")
        val eq = "${d.angles.joinToString(" + ") { it.text().let { t -> if (' ' in t) "($t)" else t } }} = 180"
        return Draft(
            statement = "En el triángulo ABC, el ángulo A mide ${d.angles[0].degreeText()}, el ángulo B mide ${d.angles[1].degreeText()} " +
                "y el ángulo C mide ${d.angles[2].degreeText()}. ¿Cuánto mide el ángulo ${labels[asked]}?",
            correct = degrees(value),
            wrong = pool.items,
            hints = listOf(
                "Los tres ángulos interiores suman 180°. Escribe una ecuación sumando las tres expresiones.",
                "Reduce términos semejantes: queda ${combine(kSum to "x", cSum to "")} = 180. Despeja x y luego reemplázalo en la expresión del ángulo pedido.",
                "La ecuación es ${kSum}x = ${180 - cSum}, entonces x = $x0. Reemplaza x en ${d.angles[asked].text()}.",
            ),
            steps = listOf(
                "Los ángulos interiores suman 180°: $eq.",
                "Reducimos: ${combine(kSum to "x", cSum to "")} = 180, entonces ${kSum}x = ${180 - cSum}.",
                "Despejamos: x = ${180 - cSum} ÷ $kSum = $x0.",
                "El ángulo ${labels[asked]} mide ${d.angles[asked].text()} con x = $x0: ${combine(d.angles[asked].k * x0 to "", d.angles[asked].c to "")} = $value°.",
                "Comprobación: ${d.angles.joinToString(" + ") { "${it.at(x0)}°" }} = 180°.",
                "Respuesta: ${degrees(value)}.",
            ),
            fillers = degreeFillers(value),
        )
    }

    // ---- nivel 3 ----

    private fun exteriorFromInteriors(rng: SeededRandom): Draft {
        val (a, b) = rng.search {
            val a = int(25, 85)
            val b = int(25, 85)
            if (a + b > 160) null else a to b
        }
        val c = 180 - a - b
        val ext = a + b
        val pool = degreePool()
        pool.add(c, "Ese es el ángulo interior C. El ángulo exterior en C es su suplemento: 180° - C, que también es A + B.")
        pool.add(abs(a - b), "El ángulo exterior es la SUMA de los dos ángulos interiores no adyacentes, no su diferencia.")
        pool.add(180 - a, "Solo usaste el ángulo A. El exterior en C es igual a A + B.")
        pool.add(180 - b, "Solo usaste el ángulo B. El exterior en C es igual a A + B.")
        pool.add(360 - ext, "Revisa: el ángulo exterior y el interior en C suman 180°, no 360°.")
        pool.add(a + b + c, "180° es la suma de los tres ángulos interiores, no el ángulo exterior.")
        return Draft(
            statement = "En el triángulo ABC, el ángulo A mide ${degrees(a)} y el ángulo B mide ${degrees(b)}. " +
                "Se prolonga el lado AC hasta un punto D. ¿Cuánto mide el ángulo exterior BCD?",
            correct = degrees(ext),
            wrong = pool.items,
            hints = listOf(
                "El ángulo exterior de un triángulo es igual a la suma de los dos ángulos interiores que no están a su lado.",
                "El ángulo exterior en C no es adyacente a A ni a B: súmalos.",
                "$a + $b = ?. Ese es el valor del ángulo exterior BCD.",
            ),
            steps = listOf(
                "El ángulo exterior en C es igual a la suma de los dos ángulos interiores no adyacentes: A y B.",
                "BCD = $a° + $b° = $ext°.",
                "Comprobación: el ángulo interior C mide 180° - $a° - $b° = $c° y $c° + $ext° = ${c + ext}°, que es un ángulo llano.",
                "Respuesta: ${degrees(ext)}.",
            ),
            fillers = degreeFillers(ext),
        )
    }

    private fun interiorFromExterior(rng: SeededRandom): Draft {
        val (a, b) = rng.search {
            val a = int(25, 85)
            val b = int(25, 85)
            if (a + b > 160) null else a to b
        }
        val ext = a + b
        val pool = degreePool()
        pool.add(ext + a, "Debes RESTAR: ángulo exterior = A + B, entonces B = exterior - A.")
        pool.add(180 - ext, "Ese es el ángulo interior C. Para hallar B usa: ángulo exterior = A + B.")
        pool.add(180 - a, "El ángulo exterior en C no es 180° menos A. Usa: exterior = A + B.")
        pool.add(ext, "Ese es el ángulo exterior dado. Para hallar B resta A: B = exterior - A.")
        pool.add(180 - ext - a, "Restaste el ángulo A del interior C. Mejor: B = exterior - A.")
        return Draft(
            statement = "En el triángulo ABC se prolonga el lado AC hasta un punto D. Si el ángulo exterior BCD mide ${degrees(ext)} " +
                "y el ángulo A mide ${degrees(a)}, ¿cuánto mide el ángulo B?",
            correct = degrees(b),
            wrong = pool.items,
            hints = listOf(
                "El ángulo exterior de un triángulo es igual a la suma de los dos ángulos interiores que no están a su lado.",
                "Aquí: ángulo exterior BCD = ángulo A + ángulo B. Despeja el ángulo B.",
                "B = $ext° - $a°.",
            ),
            steps = listOf(
                "El ángulo exterior en C es igual a la suma de los interiores no adyacentes: BCD = A + B.",
                "Despejamos B: B = BCD - A = $ext° - $a° = $b°.",
                "Comprobación: el interior C mide 180° - $ext° = ${180 - ext}° y $a° + $b° + ${180 - ext}° = 180°.",
                "Respuesta: ${degrees(b)}.",
            ),
            fillers = degreeFillers(b),
        )
    }

    private fun adjacentInterior(rng: SeededRandom): Draft {
        val c = rng.int(30, 130)
        val ext = 180 - c
        val pool = degreePool()
        pool.add(ext, "Ese es el ángulo exterior dado. El interior en C es su suplemento: 180° - exterior.")
        pool.add(360 - ext, "Un ángulo interior y su exterior suman 180° (ángulo llano), no 360°.")
        pool.add(90 - ext, "Usaste 90° como si fueran complementarios. Interior y exterior son suplementarios: suman 180°.")
        pool.add(ext + 90, "Interior y exterior suman 180°: C = 180° - exterior.")
        pool.add(ext - 90, "Restaste 90° en lugar de usar 180°: interior y exterior son suplementarios.")
        pool.add(abs(ext - c), "No hay que restar entre ellos: C = 180° - exterior.")
        if (ext % 2 == 0) pool.add(ext / 2, "Dividiste el ángulo exterior entre 2, pero aquí no hay ninguna bisectriz: C = 180° - exterior.")
        return Draft(
            statement = "En el triángulo ABC se prolonga el lado AC hasta un punto D. Si el ángulo exterior BCD mide ${degrees(ext)}, " +
                "¿cuánto mide el ángulo interior C?",
            correct = degrees(c),
            wrong = pool.items,
            hints = listOf(
                "El ángulo interior C y el ángulo exterior BCD están uno al lado del otro sobre una recta.",
                "Sobre una recta los ángulos adyacentes suman 180°. Entonces C + BCD = 180°.",
                "C = 180° - $ext°.",
            ),
            steps = listOf(
                "El ángulo interior C y el exterior BCD forman un ángulo llano: suman 180°.",
                "C = 180° - $ext° = $c°.",
                "Comprobación: $c° + $ext° = ${c + ext}°.",
                "Respuesta: ${degrees(c)}.",
            ),
            fillers = degreeFillers(c),
        )
    }

    private class ExteriorEq(val x0: Int, val a: Lin, val b: Lin, val ext: Lin)

    private fun exteriorEquation(rng: SeededRandom): Draft {
        val d = rng.search {
            val x0 = int(8, 30)
            val a = Lin(int(1, 3), int(-3, 3) * 5)
            val b = Lin(int(1, 3), int(-3, 3) * 5)
            val k3 = int(3, 6)
            val c3 = a.at(x0) + b.at(x0) - k3 * x0
            val ext = Lin(k3, c3)
            val sameSlope = k3 == a.k + b.k
            val ok = !sameSlope && abs(c3) <= 40 && ext.at(x0) in 40..170 && a.at(x0) in 10..120 && b.at(x0) in 10..120
            if (ok) ExteriorEq(x0, a, b, ext) else null
        }
        val x0 = d.x0
        val denom = d.ext.k - d.a.k - d.b.k
        val numer = d.a.c + d.b.c - d.ext.c
        check(denom * x0 == numer) { "Inconsistencia en el ángulo exterior" }
        val pool = WrongPool(valid = { it.isInteger && it.num in 1..MAX_DEGREES })
        pool.add(d.a.at(x0), "Esa es la medida del ángulo A; la pregunta pide el valor de x.")
        pool.add(d.ext.at(x0), "Esa es la medida del ángulo exterior; la pregunta pide el valor de x.")
        val k123 = d.ext.k + d.a.k + d.b.k
        pool.add(rat(180 - d.a.c - d.b.c - d.ext.c) / k123, "Sumaste los tres ángulos igual a 180°, pero el exterior NO es interior: vale lo mismo que A + B.")
        if (d.ext.k != d.a.k) pool.add(rat(d.a.c - d.ext.c) / (d.ext.k - d.a.k), "Igualaste el exterior solo con A. El exterior es igual a A + B.")
        if (d.ext.k != d.b.k) pool.add(rat(d.b.c - d.ext.c) / (d.ext.k - d.b.k), "Igualaste el exterior solo con B. El exterior es igual a A + B.")
        pool.add(rat(-numer) / denom, "Al pasar términos al otro lado, los signos cambian: revisa tu despeje.")
        val subtractDenominator = d.ext.k - (d.a.k - d.b.k)
        if (subtractDenominator != 0) {
            pool.add(rat(d.a.c - d.b.c - d.ext.c) / subtractDenominator, "El ángulo exterior es la SUMA de A y B, no su diferencia.")
        }
        return Draft(
            statement = "En el triángulo ABC, el ángulo exterior en C mide ${d.ext.degreeText()}, el ángulo A mide ${d.a.degreeText()} " +
                "y el ángulo B mide ${d.b.degreeText()}. ¿Cuál es el valor de x?",
            correct = x0.toString(),
            wrong = pool.items,
            hints = listOf(
                "El ángulo exterior de un triángulo es igual a la suma de los dos ángulos interiores no adyacentes (A y B).",
                "Plantea la ecuación: exterior = A + B. Reduce términos semejantes y despeja x.",
                "La ecuación es ${d.ext.text()} = ${combine(d.a.k + d.b.k to "x", d.a.c + d.b.c to "")}. Pasa los términos con x a un lado y los números al otro.",
            ),
            steps = listOf(
                "El ángulo exterior en C es igual a A + B: ${d.ext.text()} = ${d.a.text()} + ${d.b.text()}.",
                "Reducimos el lado derecho: ${d.ext.text()} = ${combine(d.a.k + d.b.k to "x", d.a.c + d.b.c to "")}.",
                "Agrupamos: ${combine(denom to "x")} = ${numer}.",
                "Despejamos: x = $numer ÷ ${Fmt.p(denom)} = $x0.",
                "Comprobación: exterior = ${d.ext.at(x0)}°, A = ${d.a.at(x0)}°, B = ${d.b.at(x0)}° y ${d.a.at(x0)}° + ${d.b.at(x0)}° = ${d.ext.at(x0)}°.",
                "Respuesta: x = $x0.",
            ),
            fillers = (1..5).flatMap { listOf(x0 + it, x0 - it) }.filter { it > 0 }.map { Wrong(it.toString(), GENERIC_FEEDBACK) },
        )
    }

    companion object {
        const val TOPIC_ID = "angulos-triangulo"
        private const val MAX_DEGREES = 179L
    }
}
