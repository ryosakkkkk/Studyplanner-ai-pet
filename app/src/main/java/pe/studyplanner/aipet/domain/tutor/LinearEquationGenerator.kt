package pe.studyplanner.aipet.domain.tutor

import pe.studyplanner.aipet.domain.tutor.Fmt.combine
import pe.studyplanner.aipet.domain.tutor.Fmt.p

/**
 * Ecuaciones de primer grado con solución entera.
 * Nivel 1: ax + b = c. Nivel 2: ax + b = cx + d. Nivel 3: a(x + b) - c(x + e) = f.
 */
class LinearEquationGenerator : BaseGenerator(TOPIC_ID) {

    override fun draft(rng: SeededRandom, difficulty: Int): Draft = when (difficulty) {
        1 -> simple(rng)
        2 -> bothSides(rng)
        else -> parentheses(rng)
    }

    private fun simple(rng: SeededRandom): Draft {
        val x0 = rng.intExcept(-9, 9, 0)
        val a = rng.int(2, 9)
        val b = rng.intExcept(-12, 12, 0)
        val c = a * x0 + b
        val pool = WrongPool()
        pool.add(rat(c + b, a), "Al pasar $b al otro lado, cambia de signo: debías hacer la operación contraria, no repetirla.")
        pool.add((c - b) * a, "Para despejar x hay que DIVIDIR entre $a (operación contraria de multiplicar), no multiplicar.")
        pool.add(c - b, "Te faltó el último paso: dividir entre $a, el coeficiente de x.")
        pool.add(rat(c, a) - b, "Al dividir entre $a hay que dividir TODO el lado derecho, no solo $c.")
        pool.add(-x0, "El valor es correcto pero con el signo cambiado. Comprueba sustituyendo en la ecuación original.")
        val moved = c - b
        return Draft(
            statement = "Resuelve la ecuación:\n${combine(a to "x", b to "")} = $c\n¿Cuál es el valor de x?",
            correct = x0.toString(),
            wrong = pool.items,
            hints = listOf(
                "Despejar x es dejarla sola en un lado. Lo que hagas a un lado de la ecuación, hazlo igual al otro.",
                "Primero pasa al otro lado el número que acompaña a ${a}x, con la operación contraria. Luego divide entre el coeficiente de x.",
                "Al pasar $b queda ${a}x = $moved. Ahora divide ambos lados entre $a.",
            ),
            steps = listOf(
                "La ecuación es ${combine(a to "x", b to "")} = $c.",
                "Pasamos $b al otro lado con la operación contraria (${if (b > 0) "restar $b" else "sumar ${-b}"}): ${a}x = $c ${if (b > 0) "- $b" else "+ ${-b}"} = $moved.",
                "Dividimos ambos lados entre $a: x = $moved ÷ $a = $x0.",
                "Comprobación: $a·${p(x0)} ${Fmt.signedTerm(b)} = $c. Es correcto.",
                "Respuesta: x = $x0.",
            ),
            fillers = pool.fallbacks(rat(x0)),
        )
    }

    private fun bothSides(rng: SeededRandom): Draft {
        val x0 = rng.intExcept(-9, 9, 0)
        val a = rng.int(2, 9)
        val c = rng.intExcept(1, 8, a - 1, a, a + 1) // |a - c| >= 2: el coeficiente tras agrupar no es 1 ni -1
        val b = rng.intExcept(-12, 12, 0, -(a - c) * x0)
        val d = (a - c) * x0 + b
        val k = a - c
        val pool = WrongPool()
        pool.add(rat(d + b, k), "Al pasar $b al otro lado debe cambiar de signo.")
        pool.add(rat(d - b, a + c), "Al pasar ${Fmt.xTerm(c)} al lado izquierdo cambia de signo: ${Fmt.xTerm(a)} - ${Fmt.xTerm(c)} = ${combine(k to "x")}, no ${Fmt.xTerm(a + c)}.")
        pool.add(-x0, "El valor es correcto pero con el signo cambiado. Comprueba sustituyendo en la ecuación original.")
        pool.add(rat(d - b, a), "Olvidaste pasar ${Fmt.xTerm(c)} al otro lado: los términos con x se agrupan antes de despejar.")
        pool.add(d - b, "Te faltó dividir entre el coeficiente de x que queda al agrupar ($k).")
        val lhs = a * x0 + b
        return Draft(
            statement = "Resuelve la ecuación:\n${combine(a to "x", b to "")} = ${combine(c to "x", d to "")}\n¿Cuál es el valor de x?",
            correct = x0.toString(),
            wrong = pool.items,
            hints = listOf(
                "Reúne en un lado todos los términos con x y en el otro los números. Al cruzar el signo igual, un término cambia de signo.",
                "Pasa los términos con x a la izquierda y los números a la derecha. Reduce y luego divide entre el coeficiente de x.",
                "Al agrupar queda ${combine(k to "x")} = ${d - b}. Divide ambos lados entre $k.",
            ),
            steps = listOf(
                "La ecuación es ${combine(a to "x", b to "")} = ${combine(c to "x", d to "")}.",
                "Pasamos ${Fmt.xTerm(c)} a la izquierda y $b a la derecha, cambiando de signo: ${Fmt.xTerm(a)} - ${Fmt.xTerm(c)} = $d - ${p(b)}.",
                "Reducimos: ${combine(k to "x")} = ${d - b}.",
                "Dividimos entre $k: x = ${d - b} ÷ ${p(k)} = $x0.",
                "Comprobación: izquierda $a·${p(x0)} ${Fmt.signedTerm(b)} = $lhs; derecha $c·${p(x0)} ${Fmt.signedTerm(d)} = $lhs. Coinciden.",
                "Respuesta: x = $x0.",
            ),
            fillers = pool.fallbacks(rat(x0)),
        )
    }

    private fun parentheses(rng: SeededRandom): Draft {
        val x0 = rng.intExcept(-8, 8, 0)
        val a = rng.int(2, 6)
        val c = rng.intExcept(1, 5, a)
        val b = rng.intExcept(-6, 6, 0)
        val zeroConsts = if ((a * b) % c == 0) a * b / c else 0 // evita que los términos independientes se anulen
        val e = rng.intExcept(-6, 6, 0, zeroConsts)
        val f = a * (x0 + b) - c * (x0 + e)
        val k = a - c
        val consts = a * b - c * e
        val pool = WrongPool()
        pool.add(rat(f - a * b - c * e, k), "Al multiplicar el signo menos por el paréntesis, TODOS sus términos cambian de signo: -${Fmt.scaled(c, combine(1 to "x", e to ""))} = ${combine(-c to "x", -c * e to "")}.")
        pool.add(rat(f - b + c * e, k), "El número $a que está fuera del paréntesis multiplica a CADA término de adentro, también a $b.")
        pool.add(rat(f - a * b + e, k), "El número $c que está fuera del segundo paréntesis multiplica también a $e.")
        pool.add(rat(f - consts, a + c), "Al agrupar los términos con x: ${Fmt.xTerm(a)} - ${Fmt.xTerm(c)} = ${combine(k to "x")}, no ${Fmt.xTerm(a + c)}.")
        pool.add(rat(f + consts, k), "Al pasar ${consts} al otro lado debe cambiar de signo.")
        pool.add(-x0, "El valor es correcto pero con el signo cambiado. Comprueba sustituyendo en la ecuación original.")
        val expanded = combine(a to "x", a * b to "", -c to "x", -c * e to "")
        return Draft(
            statement = "Resuelve la ecuación:\n${Fmt.scaled(a, combine(1 to "x", b to ""))} - ${Fmt.scaled(c, combine(1 to "x", e to ""))} = $f\n¿Cuál es el valor de x?",
            correct = x0.toString(),
            wrong = pool.items,
            hints = listOf(
                "Primero elimina los paréntesis con la propiedad distributiva: el número de afuera multiplica a cada término de adentro, y el signo menos también.",
                "Después de distribuir, reduce los términos semejantes, pasa los números al otro lado cambiando de signo y divide entre el coeficiente de x.",
                "Al distribuir y reducir queda ${combine(k to "x", consts to "")} = $f. Pasa $consts al otro lado y divide.",
            ),
            steps = listOf(
                "Distribuimos los paréntesis: $expanded = $f.",
                "Reducimos términos semejantes: ${combine(k to "x", consts to "")} = $f.",
                "Pasamos $consts al otro lado: ${combine(k to "x")} = $f - ${p(consts)} = ${f - consts}.",
                "Dividimos entre $k: x = ${f - consts} ÷ ${p(k)} = $x0.",
                "Comprobación: ${Fmt.mul(a, p(x0 + b))} - ${Fmt.mul(c, p(x0 + e))} = $f. Es correcto.",
                "Respuesta: x = $x0.",
            ),
            fillers = pool.fallbacks(rat(x0)),
        )
    }

    companion object {
        const val TOPIC_ID = "ecuaciones-lineales"
    }
}
