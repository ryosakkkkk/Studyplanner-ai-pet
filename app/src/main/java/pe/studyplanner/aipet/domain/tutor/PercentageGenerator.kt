package pe.studyplanner.aipet.domain.tutor

import kotlin.math.abs

/**
 * Porcentajes: aumentos y descuentos, incluidos los sucesivos.
 * Nivel 1: un solo cambio sobre un precio. Nivel 2: dos cambios sucesivos sobre un precio.
 * Nivel 3: variación porcentual total (aumento o descuento único equivalente) de dos cambios sucesivos.
 *
 * Los precios y porcentajes se escogen para que todos los resultados intermedios sean enteros.
 */
class PercentageGenerator : BaseGenerator(TOPIC_ID) {

    /** Un cambio porcentual: [sign] = +1 (aumento) o -1 (descuento); [pct] > 0. */
    private class Change(val sign: Int, val pct: Int) {
        val signed: Int get() = sign * pct
        val isIncrease: Boolean get() = sign > 0
        val noun: String get() = if (isIncrease) "aumento" else "descuento"
        val factorText: String get() = Fmt.decimal((100 + signed) / 100.0)
    }

    private class Item(val phrase: String, val verb: String)

    override fun draft(rng: SeededRandom, difficulty: Int): Draft = when (difficulty) {
        1 -> single(rng)
        2 -> successive(rng)
        else -> equivalentVariation(rng)
    }

    private fun priceFormat(): WrongPool = WrongPool(
        format = { Fmt.soles(it.toIntExact()) },
        valid = { it.isInteger && it.signum > 0 && it.num <= MAX_PRICE },
    )

    private fun randomChange(rng: SeededRandom, magnitudes: List<Int>): Change =
        Change(if (rng.coin()) 1 else -1, rng.pick(magnitudes))

    private fun single(rng: SeededRandom): Draft {
        val item = rng.pick(ITEMS)
        val price = 20 * rng.int(4, 60)
        val change = Change(if (rng.coin()) 1 else -1, 5 * rng.int(1, 12))
        val delta = price * change.pct / 100
        val result = price + change.sign * delta
        val pool = priceFormat()
        pool.add(delta, "Esos S/ $delta son solo el ${change.noun} (el ${change.pct}% de $price). " +
            "Falta ${if (change.isIncrease) "sumarlo al" else "restarlo del"} precio inicial.")
        pool.add(price - change.sign * delta, "Hiciste la operación contraria: un descuento se RESTA del precio y un aumento se SUMA.")
        pool.add(price + change.sign * change.pct, "El ${change.pct}% no son ${change.pct} soles: hay que calcular ${change.pct}/100 del precio.")
        if ((price * 100) % (100 + change.sign * change.pct) == 0) {
            pool.add(price * 100 / (100 + change.sign * change.pct), "Dividiste entre el factor en lugar de multiplicar: el porcentaje se aplica sobre el precio inicial.")
        }
        if (price % change.pct == 0) {
            pool.add(price + change.sign * (price / change.pct), "El ${change.pct}% no es 1/${change.pct} del precio: es ${change.pct}/100 del precio.")
        }
        if (delta % 10 == 0) {
            pool.add(price + change.sign * (delta / 10), "Parece un error con la coma decimal: el ${change.pct}% es ${change.pct}/100, es decir, ${Fmt.decimal(change.pct / 100.0)}.")
        }
        pool.add(price - change.sign * change.pct, "El ${change.pct}% no son ${change.pct} soles, y además invertiste la operación: " +
            "un ${change.noun} se ${if (change.isIncrease) "SUMA" else "RESTA"}.")
        pool.add(price + change.sign * delta * 10, "Parece un error con la coma decimal: el ${change.pct}% es ${Fmt.decimal(change.pct / 100.0)}, " +
            "no ${Fmt.decimal(change.pct / 10.0)}.")
        if ((result * change.pct) % 100 == 0) {
            pool.add(result + change.sign * (result * change.pct / 100), "Aplicaste el ${change.pct}% dos veces. El porcentaje se aplica una sola vez, sobre el precio inicial.")
        }
        val question = if (change.isIncrease) "¿cuál es el nuevo precio?" else "¿cuánto se paga ahora?"
        return Draft(
            statement = "${item.phrase} ${item.verb} ${Fmt.soles(price)}. Si se aplica un ${change.noun} de ${change.pct}%, $question",
            correct = Fmt.soles(result),
            wrong = pool.items,
            hints = listOf(
                "El ${change.pct}% de una cantidad es ${change.pct}/100 de ella. Un descuento se RESTA del precio y un aumento se SUMA.",
                "Primero calcula cuánto es el ${change.pct}% del precio inicial y luego ${if (change.isIncrease) "súmalo" else "réstalo"} al precio.",
                "El ${change.pct}% de $price es $price × ${change.pct} ÷ 100 = $delta. Ahora ${if (change.isIncrease) "suma" else "resta"} $delta a $price.",
            ),
            steps = listOf(
                "El precio inicial es ${Fmt.soles(price)} y el ${change.noun} es de ${change.pct}%.",
                "Calculamos el ${change.pct}% del precio: $price × ${change.pct} ÷ 100 = $delta.",
                "Como es un ${change.noun}, ${if (change.isIncrease) "sumamos" else "restamos"}: " +
                    "$price ${Fmt.op(change.sign)} $delta = $result.",
                "Comprobación con el factor ${change.factorText}: $price × ${change.factorText} = $result.",
                "Respuesta: ${Fmt.soles(result)}.",
            ),
            fillers = priceFillers(result),
        )
    }

    /** Precios vecinos del correcto, para completar si faltan distractores. */
    private fun priceFillers(correct: Int): List<Wrong> =
        listOf(10, -10, 20, -20, 5, -5, 15, -15).map { correct + it }.filter { it > 0 }
            .map { Wrong(Fmt.soles(it), GENERIC_FEEDBACK) }

    private class Successive(val price: Int, val first: Change, val second: Change, val afterFirst: Int, val final: Int)

    private fun successive(rng: SeededRandom): Draft {
        val d = rng.search {
            val price = 20 * int(5, 75)
            val first = randomChange(this, MAGNITUDES)
            val second = randomChange(this, MAGNITUDES)
            val p1x = price * (100 + first.signed)
            val p1 = p1x / 100
            val p2x = p1 * (100 + second.signed)
            if (p1x % 100 != 0 || p2x % 100 != 0) null else Successive(price, first, second, p1, p2x / 100)
        }
        val item = rng.pick(ITEMS)
        val price = d.price
        val pool = priceFormat()
        val sumPct = d.first.signed + d.second.signed
        pool.add(price * (100 + sumPct) / 100, "No se pueden sumar los porcentajes: el segundo se aplica sobre el precio que quedó después del primero.")
        pool.add(d.afterFirst, "Te detuviste después del primer cambio: falta aplicar el segundo porcentaje.")
        pool.add(price * (100 + d.second.signed) / 100, "Aplicaste solo el segundo cambio al precio inicial; el primero también cuenta.")
        if ((d.afterFirst * (100 - d.second.signed)) % 100 == 0) {
            pool.add(d.afterFirst * (100 - d.second.signed) / 100, "El segundo cambio es ${if (d.second.isIncrease) "un aumento" else "un descuento"}: " +
                "${if (d.second.isIncrease) "se suma" else "se resta"}, no al revés.")
        }
        val bothDown = price * (100 - d.first.pct) * (100 - d.second.pct)
        if (bothDown % 10000 == 0) {
            pool.add(bothDown / 10000, "Tomaste ambos cambios como descuentos; revisa cuál es aumento y cuál es descuento.")
        }
        val bothUp = price * (100 + d.first.pct) * (100 + d.second.pct)
        if (bothUp % 10000 == 0) {
            pool.add(bothUp / 10000, "Tomaste ambos cambios como aumentos; revisa cuál es aumento y cuál es descuento.")
        }
        val secondPhrase = if (d.second.sign == d.first.sign) "otro ${d.second.noun}" else "un ${d.second.noun}"
        val change2Amount = abs(d.afterFirst * d.second.pct / 100)
        val change1Amount = abs(price * d.first.pct / 100)
        return Draft(
            statement = "${item.phrase} ${item.verb} ${Fmt.soles(price)}. " +
                "Primero se aplica un ${d.first.noun} de ${d.first.pct}% y luego $secondPhrase de ${d.second.pct}%. " +
                "¿Cuál es el precio final?",
            correct = Fmt.soles(d.final),
            wrong = pool.items,
            hints = listOf(
                "Los cambios sucesivos no se suman: el segundo porcentaje se aplica sobre el precio que resultó del primero.",
                "Calcula el precio después del primer cambio y úsalo como nuevo precio inicial para aplicar el segundo.",
                "Después del primer cambio el precio es ${Fmt.soles(d.afterFirst)}. Aplica el ${d.second.noun} de ${d.second.pct}% a esa cantidad.",
            ),
            steps = listOf(
                "Primer cambio, ${d.first.noun} de ${d.first.pct}% sobre ${Fmt.soles(price)}: " +
                    "$price ${Fmt.op(d.first.sign)} $change1Amount = ${d.afterFirst}.",
                "Segundo cambio, ${d.second.noun} de ${d.second.pct}% sobre ${Fmt.soles(d.afterFirst)} (no sobre $price): " +
                    "${d.afterFirst} ${Fmt.op(d.second.sign)} $change2Amount = ${d.final}.",
                "Comprobación con factores: $price × ${d.first.factorText} × ${d.second.factorText} = ${d.final}.",
                "Respuesta: ${Fmt.soles(d.final)}.",
            ),
            fillers = priceFillers(d.final),
        )
    }

    private class Variation(val first: Change, val second: Change, val total: Int)

    private fun equivalentVariation(rng: SeededRandom): Draft {
        val d = rng.search {
            val first = randomChange(this, MAGNITUDES_L3)
            val second = randomChange(this, MAGNITUDES_L3)
            val product = first.signed * second.signed
            val total = first.signed + second.signed + product / 100
            if (product % 100 != 0 || total == 0) null else Variation(first, second, total)
        }
        val c1 = d.first.signed
        val c2 = d.second.signed
        val afterFirst = 100 + c1
        val finalValue = afterFirst * (100 + c2) / 100
        val second2 = abs(afterFirst * c2 / 100)
        val pool = WrongPool(
            format = { Fmt.variation(it.toIntExact()) },
            valid = { it.isInteger && abs(it.num) <= MAX_VARIATION },
        )
        pool.add(c1 + c2, "No se pueden sumar porcentajes sucesivos: el segundo se calcula sobre el valor ya modificado.")
        pool.add(-d.total, "La magnitud es correcta, pero el sentido está al revés: revisa si el valor final quedó por encima o por debajo de 100.")
        pool.add(c1 - c2, "No se restan los porcentajes: hay que aplicar los cambios uno tras otro sobre el valor inicial.")
        if ((c1 + c2) % 2 == 0) pool.add((c1 + c2) / 2, "El promedio de los porcentajes no da la variación total: los cambios son sucesivos.")
        pool.add(-(c1 + c2), "Sumaste los porcentajes y además invertiste el sentido. Aplica los cambios uno tras otro.")
        val offsets = listOf(1, -1, 2, -2, 5, -5)
        val fillers = offsets.map { d.total + it }.filter { abs(it) <= MAX_VARIATION }
            .map { Wrong(Fmt.variation(it), GENERIC_FEEDBACK) }
        val secondPhrase = if (d.second.sign == d.first.sign) "otro ${d.second.noun}" else "un ${d.second.noun}"
        return Draft(
            statement = "Un artículo recibe un ${d.first.noun} de ${d.first.pct}% y luego $secondPhrase de ${d.second.pct}%. " +
                "¿Cuál es la variación porcentual total respecto del precio inicial?",
            correct = Fmt.variation(d.total),
            wrong = pool.items,
            hints = listOf(
                "Imagina que el precio inicial es 100: así cada porcentaje se convierte en una cantidad fácil de seguir.",
                "Aplica el primer cambio a 100 y luego el segundo al resultado (no a 100). Al final compara con 100.",
                "Con precio inicial 100: tras el primer cambio queda $afterFirst y tras el segundo queda $finalValue. " +
                    "Compara $finalValue con 100.",
            ),
            steps = listOf(
                "Suponemos un precio inicial de 100.",
                "Primer cambio, ${d.first.noun} de ${d.first.pct}%: 100 ${Fmt.op(d.first.sign)} ${d.first.pct} = $afterFirst.",
                "Segundo cambio, ${d.second.noun} de ${d.second.pct}% sobre $afterFirst (no sobre 100): " +
                    "$afterFirst ${Fmt.op(d.second.sign)} $second2 = $finalValue.",
                "Variación total: $finalValue - 100 = ${d.total}, es decir, ${Fmt.variation(d.total).replaceFirstChar { it.lowercase() }}.",
                "Comprobación con la fórmula c1 + c2 + c1·c2/100: $c1 + ${Fmt.p(c2)} + ${Fmt.p(c1)}·${Fmt.p(c2)}/100 = ${d.total}.",
                "Respuesta: ${Fmt.variation(d.total)}.",
            ),
            fillers = fillers,
        )
    }

    companion object {
        const val TOPIC_ID = "porcentajes"
        private const val MAX_PRICE = 20000L
        private const val MAX_VARIATION = 150L
        private val MAGNITUDES = listOf(10, 15, 20, 25, 30, 40, 50)
        private val MAGNITUDES_L3 = listOf(10, 20, 25, 30, 40, 50)
        private val ITEMS = listOf(
            Item("Un celular", "cuesta"),
            Item("Una mochila", "cuesta"),
            Item("Unas zapatillas", "cuestan"),
            Item("Un libro", "cuesta"),
            Item("Una laptop", "cuesta"),
            Item("Una bicicleta", "cuesta"),
        )
    }
}
