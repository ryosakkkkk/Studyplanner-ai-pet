package pe.studyplanner.aipet.domain.tutor

import kotlin.math.abs
import kotlin.math.sqrt

/*
 * Verificadores independientes de la auditoría: uno por tema. Cada uno lee el ENUNCIADO tal como lo ve el
 * estudiante, recalcula la respuesta con un método distinto al del generador y dice qué opciones coinciden.
 *
 *  - ecuaciones-lineales / sistemas-2x2 / ecuaciones-cuadraticas: SUSTITUIR cada opción en el enunciado
 *    (y, además, resolver por interpolación, Cramer y discriminante).
 *  - porcentajes: recalcular con factores multiplicativos exactos (el generador suma y resta montos).
 *  - regla-de-tres: identificar la plantilla, decidir directa/inversa y recalcular con productos cruzados.
 *  - leyes-de-exponentes: evaluar la expresión impresa con racionales exactos.
 *  - angulos-triangulo: resolver la ecuación impresa y comprobar que los tres ángulos suman 180°.
 *  - pitagoras: comparar CUADRADOS de longitudes (a² + b² = c²) sin sacar raíces.
 *  - razones-trigonometricas: tabla exacta independiente (seno, coseno y tangente por Math.sin/cos para 30°, 45°
 *    y 60°; 3-4-5 para 37° y 53°, como se usa en el examen) con comparación numérica.
 *  - sucesiones-aritmeticas: recurrencia término a término (sin la fórmula an = a1 + (n-1)r).
 *  - mcd-mcm: enumeración de divisores y de múltiplos (sin factorizar ni usar el algoritmo de Euclides); el "otro
 *    número" se busca probando todos los candidatos que dividen al MCM.
 *  - interes-simple: cada opción se SUSTITUYE en el interés dicho (capital, tasa o tiempo) y se compara con el dato.
 *  - logaritmos: logaritmo exacto por divisiones sucesivas entre la base (sin propiedades); las ecuaciones se
 *    resuelven sustituyendo cada opción en el enunciado.
 *  - promedios: suma y cociente exactos con racionales; el dato que falta o agregado se comprueba sustituyéndolo.
 */

/**
 * Resultado de un verificador.
 * @property matches para cada opción, si coincide con la respuesta calculada de forma independiente.
 * @property extra verificaciones adicionales: (identificador, cumplida, detalle si falla).
 */
internal class Verdict(
    val matches: List<Boolean>,
    val extra: List<Triple<String, Boolean, String>> = emptyList(),
)

internal object AuditVerifiers {

    private const val EPS = 1e-9
    private const val MAX_ANSWER = 20000

    /** Verificador del tema, o `null` si el tema no tiene (eso es un fallo de la auditoría). */
    fun forTopic(topicId: String): ((Exercise) -> Verdict)? = when (topicId) {
        LinearEquationGenerator.TOPIC_ID -> ::linearEquation
        LinearSystemGenerator.TOPIC_ID -> ::linearSystem
        PercentageGenerator.TOPIC_ID -> ::percentage
        RuleOfThreeGenerator.TOPIC_ID -> ::ruleOfThree
        ExponentLawsGenerator.TOPIC_ID -> ::exponents
        QuadraticGenerator.TOPIC_ID -> ::quadratic
        TriangleAnglesGenerator.TOPIC_ID -> ::triangleAngles
        PythagorasGenerator.TOPIC_ID -> ::pythagoras
        TrigRatiosGenerator.TOPIC_ID -> ::trigRatios
        ArithmeticSequenceGenerator.TOPIC_ID -> ::arithmeticSequence
        McdMcmGenerator.TOPIC_ID -> ::gcdLcm
        SimpleInterestGenerator.TOPIC_ID -> ::simpleInterest
        LogarithmsGenerator.TOPIC_ID -> ::logarithms
        AveragesGenerator.TOPIC_ID -> ::averages
        else -> null
    }

    // ---------- utilidades ----------

    private fun Regex.mustMatch(text: String): MatchResult =
        matchEntire(text) ?: parseFail("enunciado no reconocido: \"${text.take(110).replace('\n', '⏎')}\"")

    private fun lines(e: Exercise, expected: Int, first: String, last: String): List<String> {
        val l = e.statement.split("\n")
        if (l.size != expected || l.first() != first || l.last() != last) {
            parseFail("estructura inesperada del enunciado: \"${e.statement.take(110).replace('\n', '⏎')}\"")
        }
        return l
    }

    private fun clean(q: AQ, maxDen: Long = 64, maxAbs: Int = MAX_ANSWER): Boolean =
        q.d <= java.math.BigInteger.valueOf(maxDen) && q.n.abs() <= java.math.BigInteger.valueOf(maxAbs.toLong())

    private fun allParsed(items: List<Any?>) = Triple("OPCIONES_ANALIZABLES", items.all { it != null }, "alguna opción no tiene el formato esperado")

    // ---------- ecuaciones lineales ----------

    private fun linearEquation(e: Exercise): Verdict {
        val l = lines(e, 3, "Resuelve la ecuación:", "¿Cuál es el valor de x?")
        val (lhs, rhs) = splitEquation(l[1])
        fun f(x: AQ) = evalExpr(lhs, mapOf('x' to x)) - evalExpr(rhs, mapOf('x' to x))
        val f0 = f(AQ.ZERO)
        val slope = f(AQ.ONE) - f0
        if (slope.signum == 0) parseFail("la ecuación no determina x")
        val isLinear = f(AQ.of(2)) - f0 == slope * 2 && f(AQ.of(-3)) - f0 == slope * -3
        val root = -f0 / slope // resolución por interpolación lineal
        val parsed = e.options.map { parseAQ(it) }
        val matches = parsed.map { it != null && f(it).signum == 0 } // sustituir la opción en la ecuación
        val correct = parsed.getOrNull(e.correctIndex)
        return Verdict(
            matches,
            listOf(
                allParsed(parsed),
                Triple("ECUACION_LINEAL", isLinear, "la ecuación impresa no es de primer grado"),
                Triple("RAIZ_LIMPIA", root.isInteger && clean(root, 1, 100), "la solución $root no es un entero pequeño"),
                Triple("SUSTITUCION_COINCIDE_CON_INTERPOLACION", correct == root, "la opción marcada vale $correct y la raíz es $root"),
            ),
        )
    }

    // ---------- sistemas 2x2 ----------

    private val PAIR_OPTION = Regex("""^x = (-?\d+(?:/\d+)?), y = (-?\d+(?:/\d+)?)$""")

    private fun linearSystem(e: Exercise): Verdict {
        val l = lines(e, 4, "Resuelve el sistema:", "¿Cuál es la solución?")
        fun fn(line: String): (AQ, AQ) -> AQ {
            val (a, b) = splitEquation(line)
            return { x, y ->
                val v = mapOf('x' to x, 'y' to y)
                evalExpr(a, v) - evalExpr(b, v)
            }
        }
        val f1 = fn(l[1])
        val f2 = fn(l[2])
        val z = AQ.ZERO
        val a1 = f1(AQ.ONE, z) - f1(z, z)
        val b1 = f1(z, AQ.ONE) - f1(z, z)
        val c1 = -f1(z, z)
        val a2 = f2(AQ.ONE, z) - f2(z, z)
        val b2 = f2(z, AQ.ONE) - f2(z, z)
        val c2 = -f2(z, z)
        val linear = f1(AQ.of(2), AQ.of(3)) == a1 * 2 + b1 * 3 - c1 && f2(AQ.of(-2), AQ.of(5)) == a2 * -2 + b2 * 5 - c2
        val det = a1 * b2 - a2 * b1
        if (det.signum == 0) parseFail("sistema sin solución única")
        val x = (c1 * b2 - c2 * b1) / det // regla de Cramer
        val y = (a1 * c2 - a2 * c1) / det
        val parsed = e.options.map { text ->
            PAIR_OPTION.matchEntire(text)?.let { m -> parseAQ(m.groupValues[1])!! to parseAQ(m.groupValues[2])!! }
        }
        val matches = parsed.map { it != null && f1(it.first, it.second).signum == 0 && f2(it.first, it.second).signum == 0 }
        val correct = parsed.getOrNull(e.correctIndex)
        return Verdict(
            matches,
            listOf(
                allParsed(parsed),
                Triple("SISTEMA_LINEAL", linear, "una ecuación no es lineal"),
                Triple("SOLUCION_ENTERA", x.isInteger && y.isInteger && clean(x, 1, 100) && clean(y, 1, 100), "la solución ($x, $y) no es entera"),
                Triple("SUSTITUCION_COINCIDE_CON_CRAMER", correct != null && correct.first == x && correct.second == y, "la opción marcada es $correct y Cramer da ($x, $y)"),
            ),
        )
    }

    // ---------- ecuaciones cuadráticas ----------

    private val ROOTS_OPTION = Regex("""^x = (-?\d+)(?: o x = (-?\d+))?$""")

    private fun quadratic(e: Exercise): Verdict {
        val l = lines(e, 3, "Resuelve la ecuación:", "¿Cuál es el conjunto solución?")
        val (lhs, rhs) = splitEquation(l[1])
        fun f(x: AQ) = evalExpr(lhs, mapOf('x' to x)) - evalExpr(rhs, mapOf('x' to x))
        val c = f(AQ.ZERO)
        val plus = f(AQ.ONE)
        val minus = f(AQ.of(-1))
        val a = (plus + minus) / 2 - c
        val b = (plus - minus) / 2
        val isQuadratic = a.signum != 0 && f(AQ.of(2)) == a * 4 + b * 2 + c && f(AQ.of(-2)) == a * 4 - b * 2 + c &&
            f(AQ.of(3)) == a * 9 + b * 3 + c
        if (!isQuadratic) parseFail("la ecuación impresa no es cuadrática")
        val disc = b * b - a * c * 4
        val discInt = disc.toIntOrNull() ?: parseFail("discriminante no entero: $disc")
        if (discInt < 0) parseFail("sin raíces reales")
        val s = exactSqrt(discInt.toLong()) ?: parseFail("discriminante $discInt no es cuadrado perfecto")
        val twoA = a * 2
        val expected = setOf((-b + AQ.of(s)) / twoA, (-b - AQ.of(s)) / twoA)
        val parsed = e.options.map { text ->
            ROOTS_OPTION.matchEntire(text)?.let { m ->
                val r1 = AQ.of(m.groupValues[1].toLong())
                val r2 = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { AQ.of(it.toLong()) }
                setOf(r1) + listOfNotNull(r2) to (r2 == null || r1.toIntOrNull()!! < r2.toIntOrNull()!!)
            }
        }
        val matches = parsed.map { it != null && it.first == expected }
        return Verdict(
            matches,
            listOf(
                allParsed(parsed),
                Triple("RAICES_ENTERAS", expected.all { it.isInteger }, "las raíces $expected no son enteras"),
                Triple("SUSTITUCION_EN_RAICES", expected.all { f(it).signum == 0 }, "alguna raíz no anula la ecuación"),
                Triple("RAICES_EN_ORDEN_ASCENDENTE", parsed.all { it == null || it.second }, "alguna opción lista las raíces en desorden o repetidas"),
            ),
        )
    }

    // ---------- porcentajes ----------

    private val PCT_SINGLE = Regex("""^(.+?) (cuesta|cuestan) S/ (\d+)\. Si se aplica un (aumento|descuento) de (\d+)%, (¿cuál es el nuevo precio\?|¿cuánto se paga ahora\?)$""")
    private val PCT_SUCCESSIVE = Regex("""^(.+?) (cuesta|cuestan) S/ (\d+)\. Primero se aplica un (aumento|descuento) de (\d+)% y luego (otro|un) (aumento|descuento) de (\d+)%\. ¿Cuál es el precio final\?$""")
    private val PCT_VARIATION = Regex("""^Un artículo recibe un (aumento|descuento) de (\d+)% y luego (otro|un) (aumento|descuento) de (\d+)%\. ¿Cuál es la variación porcentual total respecto del precio inicial\?$""")
    private val SOLES_OPTION = Regex("""^S/ (\d+)$""")
    private val VARIATION_OPTION = Regex("""^(?:(Aumento) de (\d+)%|(Descuento) de (\d+)%|No varía \(0%\))$""")

    private fun factor(noun: String, pct: Int): AQ = AQ.of((if (noun == "aumento") 100 + pct else 100 - pct).toLong(), 100L)

    private fun percentage(e: Exercise): Verdict {
        val extra = mutableListOf<Triple<String, Boolean, String>>()
        val s = e.statement
        PCT_SINGLE.matchEntire(s)?.let { m ->
            val price = m.groupValues[3].toLong()
            val noun = m.groupValues[4]
            val value = AQ.of(price) * factor(noun, m.groupValues[5].toInt())
            val question = m.groupValues[6]
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 1, "un solo cambio debe ser dificultad 1")
            extra += Triple("CONCORDANCIA_VERBO", agreesWithVerb(m.groupValues[1], m.groupValues[2]), "\"${m.groupValues[1]} ${m.groupValues[2]}\"")
            extra += Triple("PREGUNTA_SEGUN_CAMBIO", (noun == "aumento") == question.contains("nuevo precio"), "la pregunta no corresponde al cambio")
            return pricesVerdict(e, value, extra)
        }
        PCT_SUCCESSIVE.matchEntire(s)?.let { m ->
            val value = AQ.of(m.groupValues[3].toLong()) * factor(m.groupValues[4], m.groupValues[5].toInt()) *
                factor(m.groupValues[7], m.groupValues[8].toInt())
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 2, "dos cambios sucesivos deben ser dificultad 2")
            extra += Triple("CONCORDANCIA_VERBO", agreesWithVerb(m.groupValues[1], m.groupValues[2]), "\"${m.groupValues[1]} ${m.groupValues[2]}\"")
            extra += Triple("GRAMATICA_OTRO_UN", (m.groupValues[6] == "otro") == (m.groupValues[4] == m.groupValues[7]), "\"${m.groupValues[6]}\" no concuerda con los cambios")
            return pricesVerdict(e, value, extra)
        }
        val m = PCT_VARIATION.mustMatch(s)
        val total = (factor(m.groupValues[1], m.groupValues[2].toInt()) * factor(m.groupValues[4], m.groupValues[5].toInt()) - 1) * 100
        extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 3, "la variación total debe ser dificultad 3")
        extra += Triple("GRAMATICA_OTRO_UN", (m.groupValues[3] == "otro") == (m.groupValues[1] == m.groupValues[4]), "\"${m.groupValues[3]}\" no concuerda con los cambios")
        extra += Triple("VARIACION_ENTERA_NO_NULA", total.isInteger && total.signum != 0, "variación total $total")
        val parsed = e.options.map { text ->
            VARIATION_OPTION.matchEntire(text)?.let { v ->
                when {
                    v.groupValues[1].isNotEmpty() -> AQ.of(v.groupValues[2].toLong())
                    v.groupValues[3].isNotEmpty() -> AQ.of(-v.groupValues[4].toLong())
                    else -> AQ.ZERO
                }
            }
        }
        return Verdict(parsed.map { it == total }, extra + allParsed(parsed))
    }

    private fun agreesWithVerb(phrase: String, verb: String): Boolean =
        (phrase.startsWith("Unas ") || phrase.startsWith("Unos ")) == (verb == "cuestan")

    private fun pricesVerdict(e: Exercise, value: AQ, extra: MutableList<Triple<String, Boolean, String>>): Verdict {
        val parsed = e.options.map { text -> SOLES_OPTION.matchEntire(text)?.let { AQ.of(it.groupValues[1].toLong()) } }
        extra += Triple("PRECIO_ENTERO", value.isInteger && value.signum > 0, "el precio final $value no es un entero positivo")
        extra += allParsed(parsed)
        return Verdict(parsed.map { it == value }, extra)
    }

    // ---------- regla de tres ----------

    /** Plantilla del enunciado: regex, orden de (a, b, c) en los grupos, si es directa y cómo se escribe la unidad. */
    private class RuleTemplate(
        val regex: Regex,
        val order: IntArray,
        val direct: Boolean,
        val unit: (Int) -> String,
    )

    private fun plural(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"

    private val RULE_TEMPLATES = listOf(
        RuleTemplate(Regex("""^Si (\d+) cuadernos cuestan S/ (\d+), ¿cuánto costarán (\d+) cuadernos\?$"""), intArrayOf(1, 2, 3), true) { "S/ $it" },
        RuleTemplate(Regex("""^Un auto recorre (\d+) km en (\d+) horas a velocidad constante\. ¿Cuántos kilómetros recorre en (\d+) horas\?$"""), intArrayOf(2, 1, 3), true) { "$it km" },
        RuleTemplate(Regex("""^Una máquina produce (\d+) botellas en (\d+) minutos\. ¿Cuántas botellas produce en (\d+) minutos\?$"""), intArrayOf(2, 1, 3), true) { plural(it, "botella", "botellas") },
        RuleTemplate(Regex("""^Con (\d+) kg de harina se preparan (\d+) panes\. ¿Cuántos panes se preparan con (\d+) kg de harina\?$"""), intArrayOf(1, 2, 3), true) { plural(it, "pan", "panes") },
        RuleTemplate(Regex("""^(\d+) obreros terminan una obra en (\d+) días\. ¿En cuántos días la terminarán (\d+) obreros, trabajando al mismo ritmo\?$"""), intArrayOf(1, 2, 3), false) { plural(it, "día", "días") },
        RuleTemplate(Regex("""^Un ciclista que va a (\d+) km/h tarda (\d+) horas en llegar a su destino\. ¿Cuántas horas tardará si va a (\d+) km/h\?$"""), intArrayOf(1, 2, 3), false) { plural(it, "hora", "horas") },
        RuleTemplate(Regex("""^(\d+) grifos iguales llenan un tanque en (\d+) horas\. ¿En cuántas horas lo llenarán (\d+) grifos\?$"""), intArrayOf(1, 2, 3), false) { plural(it, "hora", "horas") },
        RuleTemplate(Regex("""^Una ración de arroz alcanza para (\d+) personas durante (\d+) días\. ¿Durante cuántos días alcanzará para (\d+) personas\?$"""), intArrayOf(1, 2, 3), false) { plural(it, "día", "días") },
    )

    private fun ruleOfThree(e: Exercise): Verdict {
        val (template, m) = RULE_TEMPLATES.firstNotNullOfOrNull { t -> t.regex.matchEntire(e.statement)?.let { t to it } }
            ?: parseFail("enunciado no reconocido: \"${e.statement.take(110)}\"")
        // a se corresponde con b; se pide el valor que se corresponde con c.
        val a = m.groupValues[template.order[0]].toLong()
        val b = m.groupValues[template.order[1]].toLong()
        val c = m.groupValues[template.order[2]].toLong()
        // directa: a/b = c/x (productos cruzados a·x = b·c); inversa: a·b = c·x
        val x = if (template.direct) AQ.of(b * c, a) else AQ.of(a * b, c)
        val xInt = x.toIntOrNull()
        val expected = xInt?.let { template.unit(it) }
        val matches = e.options.map { it == expected }
        val unitsOk = e.options.all { text ->
            val n = Regex("""\d+""").find(text.removePrefix("S/ "))?.value?.toIntOrNull()
            n != null && template.unit(n) == text
        }
        return Verdict(
            matches,
            listOf(
                Triple("RESPUESTA_ENTERA_POSITIVA", xInt != null && xInt >= 1 && xInt <= 3000, "x = $x"),
                Triple("UNIDAD_Y_PLURAL_EN_OPCIONES", unitsOk, "alguna opción tiene unidad o plural incorrectos"),
                Triple("CANTIDADES_EN_PLURAL", a >= 2 && b >= 2 && c >= 2, "una cantidad del enunciado vale 1 y la plantilla está en plural"),
                Triple("DATOS_DISTINTOS", a != c, "la cantidad pedida repite el dato"),
            ),
        )
    }

    // ---------- leyes de exponentes ----------

    private fun exponents(e: Exercise): Verdict {
        val l = e.statement.split("\n")
        if (l.size != 2 || l[0] != "Calcula el valor de:") parseFail("estructura inesperada del enunciado: \"${e.statement.take(110)}\"")
        val value = evalExpr(l[1])
        val parsed = e.options.map { parseAQ(it) }
        return Verdict(
            parsed.map { it == value },
            listOf(
                allParsed(parsed),
                Triple("RESPUESTA_LIMPIA", clean(value), "el valor $value no es un entero ni una fracción sencilla"),
            ),
        )
    }

    // ---------- ángulos en el triángulo ----------

    private val ANGLE_NUMERIC = Regex("""^En el triángulo ABC, el ángulo ([ABC]) mide (\d+)° y el ángulo ([ABC]) mide (\d+)°\. ¿Cuánto mide el ángulo ([ABC])\?$""")
    private val ANGLE_ALGEBRAIC = Regex("""^En el triángulo ABC, el ángulo A mide (.+?)°, el ángulo B mide (.+?)° y el ángulo C mide (.+?)°\. ¿Cuánto mide el ángulo ([ABC])\?$""")
    private val ANGLE_EXTERIOR_SUM = Regex("""^En el triángulo ABC, el ángulo A mide (\d+)° y el ángulo B mide (\d+)°\. Se prolonga el lado AC hasta un punto D\. ¿Cuánto mide el ángulo exterior BCD\?$""")
    private val ANGLE_EXTERIOR_B = Regex("""^En el triángulo ABC se prolonga el lado AC hasta un punto D\. Si el ángulo exterior BCD mide (\d+)° y el ángulo A mide (\d+)°, ¿cuánto mide el ángulo B\?$""")
    private val ANGLE_ADJACENT = Regex("""^En el triángulo ABC se prolonga el lado AC hasta un punto D\. Si el ángulo exterior BCD mide (\d+)°, ¿cuánto mide el ángulo interior C\?$""")
    private val ANGLE_EXTERIOR_X = Regex("""^En el triángulo ABC, el ángulo exterior en C mide (.+?)°, el ángulo A mide (.+?)° y el ángulo B mide (.+?)°\. ¿Cuál es el valor de x\?$""")
    private val DEGREE_OPTION = Regex("""^(\d+)°$""")

    private fun solveLinear(expression: String): AQ {
        // expression se evalúa como f(x); se busca f(x) = 0
        val f0 = evalExpr(expression, mapOf('x' to AQ.ZERO))
        val slope = evalExpr(expression, mapOf('x' to AQ.ONE)) - f0
        if (slope.signum == 0) parseFail("la ecuación no determina x: $expression")
        return -f0 / slope
    }

    private fun angleAt(expr: String, x: AQ): AQ = evalExpr(expr, mapOf('x' to x))

    private fun triangleAngles(e: Exercise): Verdict {
        val s = e.statement
        val extra = mutableListOf<Triple<String, Boolean, String>>()
        val optionsDegrees = e.options.map { text -> DEGREE_OPTION.matchEntire(text)?.let { AQ.of(it.groupValues[1].toLong()) } }

        ANGLE_NUMERIC.matchEntire(s)?.let { m ->
            val labels = listOf(m.groupValues[1], m.groupValues[3], m.groupValues[5])
            val a = m.groupValues[2].toLong()
            val b = m.groupValues[4].toLong()
            extra += Triple("ETIQUETAS_DISTINTAS", labels.toSet().size == 3, "ángulos repetidos $labels")
            extra += Triple("TRIANGULO_VALIDO", a + b < 180, "los ángulos dados suman ${a + b}")
            return finishAngles(e, optionsDegrees, AQ.of(180 - a - b), extra)
        }
        ANGLE_ALGEBRAIC.matchEntire(s)?.let { m ->
            val angles = listOf(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            val x = solveLinear("${angles.joinToString(" + ") { "($it)" }} - 180")
            val values = angles.map { angleAt(it, x) }
            extra += Triple("X_ENTERO", x.isInteger, "x = $x")
            extra += Triple("ANGULOS_VALIDOS", values.all { it.signum > 0 && it.isInteger && it.toIntOrNull()!! < 180 }, "ángulos $values")
            extra += Triple("SUMA_180", values.reduce { p, q -> p + q } == AQ.of(180), "los ángulos suman ${values.reduce { p, q -> p + q }}")
            return finishAngles(e, optionsDegrees, values["ABC".indexOf(m.groupValues[4])], extra)
        }
        ANGLE_EXTERIOR_SUM.matchEntire(s)?.let { m ->
            val a = m.groupValues[1].toLong()
            val b = m.groupValues[2].toLong()
            extra += Triple("TRIANGULO_VALIDO", a + b < 180, "los ángulos dados suman ${a + b}")
            return finishAngles(e, optionsDegrees, AQ.of(a + b), extra) // propiedad del ángulo exterior
        }
        ANGLE_EXTERIOR_B.matchEntire(s)?.let { m ->
            val ext = m.groupValues[1].toLong()
            val a = m.groupValues[2].toLong()
            extra += Triple("TRIANGULO_VALIDO", ext > a && ext < 180, "exterior $ext y A $a")
            return finishAngles(e, optionsDegrees, AQ.of(ext - a), extra)
        }
        ANGLE_ADJACENT.matchEntire(s)?.let { m ->
            val ext = m.groupValues[1].toLong()
            extra += Triple("TRIANGULO_VALIDO", ext in 1..179, "exterior $ext")
            return finishAngles(e, optionsDegrees, AQ.of(180 - ext), extra)
        }
        val m = ANGLE_EXTERIOR_X.mustMatch(s)
        val (ext, a, b) = listOf(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        val x = solveLinear("($ext) - ($a) - ($b)")
        val values = listOf(ext, a, b).map { angleAt(it, x) }
        extra += Triple("X_ENTERO", x.isInteger && x.signum > 0, "x = $x")
        extra += Triple("ANGULOS_VALIDOS", values.all { it.signum > 0 && it.isInteger && it.toIntOrNull()!! < 180 }, "ángulos $values")
        extra += Triple("EXTERIOR_IGUAL_A_SUMA", values[0] == values[1] + values[2], "el exterior ${values[0]} no es ${values[1]} + ${values[2]}")
        val options = e.options.map { parseAQ(it) }
        return Verdict(options.map { it == x }, extra + allParsed(options))
    }

    private fun finishAngles(e: Exercise, options: List<AQ?>, answer: AQ, extra: MutableList<Triple<String, Boolean, String>>): Verdict {
        extra += Triple("ANGULO_ENTRE_1_Y_179", answer.signum > 0 && answer.toIntOrNull()?.let { it < 180 } == true, "respuesta $answer")
        extra += allParsed(options)
        return Verdict(options.map { it == answer }, extra)
    }

    // ---------- Pitágoras ----------

    private val PYTHAGORAS_HYPOTENUSE = Regex("""^Un triángulo rectángulo tiene catetos de (\d+) (cm|m) y (\d+) (cm|m)\. ¿Cuánto mide la hipotenusa\?$""")
    private val PYTHAGORAS_LEG = Regex("""^Un triángulo rectángulo tiene hipotenusa de (\d+) (cm|m) y un cateto de (\d+) (cm|m)\. ¿Cuánto mide el otro cateto\?$""")
    private val PYTHAGORAS_LADDER = Regex("""^Una escalera de (\d+) m se apoya en una pared\. Su base está a (\d+) m de la pared\. ¿A qué altura de la pared llega la escalera\?$""")
    private val PYTHAGORAS_RECTANGLE = Regex("""^Un terreno rectangular mide (\d+) m de largo y (\d+) m de ancho\. ¿Cuánto mide su diagonal\?$""")
    private val PYTHAGORAS_PATH = Regex("""^Un ciclista parte de un punto, recorre (\d+) km hacia el norte y luego (\d+) km hacia el este\. ¿A qué distancia en línea recta está del punto de partida\?$""")
    private val PYTHAGORAS_SQUARE = Regex("""^Un cuadrado tiene lados de (\d+) cm\. ¿Cuánto mide su diagonal\?$""")
    private val LENGTH_OPTION = Regex("""^(\d*)(?:√(\d+))?(?:/(\d+))? (cm|m|km)$""")

    /** Longitud de una opción como (cuadrado exacto, unidad). */
    private fun parseLength(text: String): Pair<AQ, String>? {
        val m = LENGTH_OPTION.matchEntire(text) ?: return null
        val coefText = m.groupValues[1]
        val radText = m.groupValues[2]
        if (coefText.isEmpty() && radText.isEmpty()) return null
        val coef = if (coefText.isEmpty()) AQ.ONE else AQ.of(coefText.toLong())
        val rad = if (radText.isEmpty()) AQ.ONE else AQ.of(radText.toLong())
        val den = if (m.groupValues[3].isEmpty()) AQ.ONE else AQ.of(m.groupValues[3].toLong())
        return (coef * coef * rad) / (den * den) to m.groupValues[4]
    }

    private fun pythagoras(e: Exercise): Verdict {
        val s = e.statement
        val extra = mutableListOf<Triple<String, Boolean, String>>()

        fun sq(n: String) = AQ.of(n.toLong()) * AQ.of(n.toLong())
        PYTHAGORAS_HYPOTENUSE.matchEntire(s)?.let { m ->
            extra += Triple("UNIDADES_IGUALES", m.groupValues[2] == m.groupValues[4], "unidades distintas en los catetos")
            return pythagorasVerdict(e, sq(m.groupValues[1]) + sq(m.groupValues[3]), m.groupValues[2], true, extra)
        }
        PYTHAGORAS_LEG.matchEntire(s)?.let { m ->
            extra += Triple("UNIDADES_IGUALES", m.groupValues[2] == m.groupValues[4], "unidades distintas")
            extra += Triple("HIPOTENUSA_MAYOR", m.groupValues[1].toInt() > m.groupValues[3].toInt(), "la hipotenusa no supera al cateto")
            return pythagorasVerdict(e, sq(m.groupValues[1]) - sq(m.groupValues[3]), m.groupValues[2], true, extra)
        }
        PYTHAGORAS_LADDER.matchEntire(s)?.let { m ->
            extra += Triple("HIPOTENUSA_MAYOR", m.groupValues[1].toInt() > m.groupValues[2].toInt(), "la escalera no supera a la base")
            return pythagorasVerdict(e, sq(m.groupValues[1]) - sq(m.groupValues[2]), "m", true, extra)
        }
        PYTHAGORAS_RECTANGLE.matchEntire(s)?.let { m ->
            return pythagorasVerdict(e, sq(m.groupValues[1]) + sq(m.groupValues[2]), "m", true, extra)
        }
        PYTHAGORAS_PATH.matchEntire(s)?.let { m ->
            return pythagorasVerdict(e, sq(m.groupValues[1]) + sq(m.groupValues[2]), "km", true, extra)
        }
        val m = PYTHAGORAS_SQUARE.mustMatch(s)
        extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 3, "problema contextualizado debe ser dificultad 3")
        // la diagonal del cuadrado es a√2: su cuadrado es 2a², sin raíz exacta
        return pythagorasVerdict(e, sq(m.groupValues[1]) * 2, "cm", false, extra)
    }

    private fun pythagorasVerdict(
        e: Exercise,
        expectedSquare: AQ,
        unit: String,
        mustBeInteger: Boolean,
        extra: MutableList<Triple<String, Boolean, String>>,
    ): Verdict {
        val parsed = e.options.map { parseLength(it) }
        val matches = parsed.map { it != null && it.first == expectedSquare && it.second == unit }
        val side = exactSqrt(expectedSquare.toIntOrNull()?.toLong() ?: -1)
        if (mustBeInteger) extra += Triple("TERNA_PITAGORICA_ENTERA", side != null, "el cuadrado $expectedSquare no es cuadrado perfecto")
        val correct = parsed.getOrNull(e.correctIndex)
        extra += Triple("CUADRADO_DE_LA_OPCION_CORRECTA", correct != null && correct.first == expectedSquare, "el cuadrado de la opción marcada es ${correct?.first} y debe ser $expectedSquare")
        extra += allParsed(parsed)
        return Verdict(matches, extra)
    }

    // ---------- razones trigonométricas ----------

    // Tabla independiente. 37° y 53° siguen la convención del examen (triángulo 3-4-5).
    private fun sinT(a: Int): Double = when (a) { 37 -> 0.6; 53 -> 0.8; else -> Math.sin(Math.toRadians(a.toDouble())) }
    private fun cosT(a: Int): Double = when (a) { 37 -> 0.8; 53 -> 0.6; else -> Math.cos(Math.toRadians(a.toDouble())) }
    private fun tanT(a: Int): Double = sinT(a) / cosT(a)

    private fun ratioT(fn: String, a: Int): Double = when (fn) {
        "sen" -> sinT(a)
        "cos" -> cosT(a)
        "tan" -> tanT(a)
        else -> parseFail("razón desconocida $fn")
    }

    private val SURD_OPTION = Regex("""^(-?)(\d*)(?:√(\d+))?(?:/(\d+))?$""")

    /** Valor numérico de "3/5", "√3/2", "5√3/6", "√2", "7"; `null` si no tiene esa forma. */
    private fun parseSurd(text: String): Double? {
        val m = SURD_OPTION.matchEntire(text) ?: return null
        val coefText = m.groupValues[2]
        val radText = m.groupValues[3]
        if (coefText.isEmpty() && radText.isEmpty()) return null
        val coef = if (coefText.isEmpty()) 1.0 else coefText.toDouble()
        val rad = if (radText.isEmpty()) 1.0 else sqrt(radText.toDouble())
        val den = if (m.groupValues[4].isEmpty()) 1.0 else m.groupValues[4].toDouble()
        val sign = if (m.groupValues[1] == "-") -1.0 else 1.0
        if (den == 0.0) return null
        return sign * coef * rad / den
    }

    private fun near(a: Double, b: Double) = abs(a - b) <= EPS * maxOf(1.0, abs(b))

    private val TRIG_SIDES = Regex("""^En un triángulo rectángulo, los catetos miden (\d+) y (\d+) y la hipotenusa mide (\d+)\. Si θ es el ángulo opuesto al cateto de (\d+), ¿cuánto vale (sen|cos|tan) θ\?$""")
    private val TRIG_VALUE = Regex("""^(Usando el triángulo notable de 37° y 53°, halla|Halla) el valor de (sen|cos|tan) (\d+)°\.$""")
    private val TRIG_EXPRESSION = Regex("""^(Usando el triángulo notable de 37° y 53°, calcula|Calcula) el valor de E = (sen|cos|tan) (\d+)° ([+\-·÷]) (sen|cos|tan) (\d+)°\.$""")
    private val TRIG_SIDE_LENGTH = Regex("""^En un triángulo rectángulo, un ángulo agudo mide (\d+)° y (el cateto opuesto a ese ángulo|el cateto adyacente a ese ángulo|la hipotenusa) mide (.+?) cm\. ¿Cuánto mide (el cateto opuesto a ese ángulo|el cateto adyacente a ese ángulo|la hipotenusa)\?( \(Usa el triángulo notable de 37° y 53°\.\))?$""")
    private const val NOTABLE_NOTE = "Usando el triángulo notable de 37° y 53°"

    private fun trigRatios(e: Exercise): Verdict {
        val s = e.statement
        val extra = mutableListOf<Triple<String, Boolean, String>>()

        TRIG_SIDES.matchEntire(s)?.let { m ->
            val p = m.groupValues[1].toLong()
            val q = m.groupValues[2].toLong()
            val h = m.groupValues[3].toLong()
            val opp = m.groupValues[4].toLong()
            val adj = if (opp == p) q else p
            extra += Triple("TERNA_PITAGORICA", p * p + q * q == h * h, "$p² + $q² ≠ $h²")
            extra += Triple("OPUESTO_ES_UN_CATETO", opp == p || opp == q, "el opuesto $opp no es un cateto")
            val expected = when (m.groupValues[5]) {
                "sen" -> AQ.of(opp, h)
                "cos" -> AQ.of(adj, h)
                else -> AQ.of(opp, adj)
            }
            val parsed = e.options.map { parseAQ(it) }
            extra += allParsed(parsed)
            return Verdict(parsed.map { it == expected }, extra)
        }
        TRIG_VALUE.matchEntire(s)?.let { m ->
            val angle = m.groupValues[3].toInt()
            val expected = ratioT(m.groupValues[2], angle)
            extra += Triple("NOTA_37_53", m.groupValues[1].startsWith("Usando") == (angle == 37 || angle == 53), "la aclaración del triángulo 37°-53° no coincide con el ángulo")
            return surdVerdict(e, expected, extra)
        }
        TRIG_EXPRESSION.matchEntire(s)?.let { m ->
            val a1 = m.groupValues[3].toInt()
            val a2 = m.groupValues[6].toInt()
            val x = ratioT(m.groupValues[2], a1)
            val y = ratioT(m.groupValues[5], a2)
            val expected = when (m.groupValues[4]) {
                "+" -> x + y
                "-" -> x - y
                "·" -> x * y
                else -> x / y
            }
            val family = listOf(listOf(30, 60), listOf(45), listOf(37, 53))
            extra += Triple("MISMA_FAMILIA", family.any { a1 in it && a2 in it }, "los ángulos $a1° y $a2° no son de la misma familia")
            extra += Triple("TERMINOS_DISTINTOS", !(m.groupValues[2] == m.groupValues[5] && a1 == a2), "los dos términos son iguales")
            extra += Triple("NOTA_37_53", m.groupValues[1].startsWith("Usando") == (a1 == 37 || a1 == 53), "la aclaración del triángulo 37°-53° no coincide con el ángulo")
            extra += Triple("RESULTADO_POSITIVO", expected > EPS, "el resultado $expected no es positivo")
            return surdVerdict(e, expected, extra)
        }
        val m = TRIG_SIDE_LENGTH.mustMatch(s)
        val angle = m.groupValues[1].toInt()
        val roles = listOf("el cateto opuesto a ese ángulo", "el cateto adyacente a ese ángulo", "la hipotenusa")
        val known = roles.indexOf(m.groupValues[2])
        val asked = roles.indexOf(m.groupValues[4])
        val given = parseSurd(m.groupValues[3]) ?: parseFail("dato no analizable: ${m.groupValues[3]}")
        val hyp = when (known) {
            0 -> given / sinT(angle)
            1 -> given / cosT(angle)
            else -> given
        }
        val sides = listOf(hyp * sinT(angle), hyp * cosT(angle), hyp)
        extra += Triple("PITAGORAS_EN_LOS_TRES_LADOS", near(sides[0] * sides[0] + sides[1] * sides[1], sides[2] * sides[2]), "los lados calculados no cumplen a² + b² = c²")
        extra += Triple("LADO_CONOCIDO_DISTINTO_DEL_PEDIDO", known != asked, "se pide el mismo lado que se da")
        extra += Triple("NOTA_37_53", m.groupValues[5].isNotEmpty() == (angle == 37 || angle == 53), "la aclaración del triángulo 37°-53° no coincide con el ángulo")
        extra += Triple("ANGULO_NOTABLE", angle in listOf(30, 37, 45, 53, 60), "ángulo $angle° no notable")
        val parsed = e.options.map { text -> if (text.endsWith(" cm")) parseSurd(text.removeSuffix(" cm")) else null }
        extra += allParsed(parsed)
        return Verdict(parsed.map { it != null && near(it, sides[asked]) }, extra)
    }

    private fun surdVerdict(e: Exercise, expected: Double, extra: MutableList<Triple<String, Boolean, String>>): Verdict {
        val parsed = e.options.map { parseSurd(it) }
        extra += allParsed(parsed)
        return Verdict(parsed.map { it != null && near(it, expected) }, extra)
    }

    // ---------- sucesiones aritméticas ----------

    private val SEQ_NEXT = Regex("""^En la sucesión aritmética ((?:-?\d+, )+)\.\.\. ¿qué número sigue\?$""")
    private val SEQ_NTH = Regex("""^En la sucesión aritmética ((?:-?\d+, )+)\.\.\. ¿cuánto vale el término de lugar (\d+)\?$""")
    private val SEQ_TWO = Regex("""^En una sucesión aritmética, el término de lugar (\d+) vale (-?\d+) y el término de lugar (\d+) vale (-?\d+)\. ¿Cuánto vale el término de lugar (\d+)\?$""")
    private val SEQ_COUNT = Regex("""^¿Cuántos términos tiene la sucesión aritmética ((?:-?\d+, )+)\.\.\., (-?\d+)\?$""")

    private fun terms(text: String): List<Int> = text.trim().trimEnd(',').split(", ").map { it.trim().toInt() }

    private fun commonDifference(terms: List<Int>): Int? {
        if (terms.size < 2) return null
        val r = terms[1] - terms[0]
        return if (terms.zipWithNext().all { (p, q) -> q - p == r }) r else null
    }

    private fun arithmeticSequence(e: Exercise): Verdict {
        val s = e.statement
        val extra = mutableListOf<Triple<String, Boolean, String>>()
        SEQ_NEXT.matchEntire(s)?.let { m ->
            val t = terms(m.groupValues[1])
            val r = commonDifference(t) ?: parseFail("los términos no son aritméticos: $t")
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 1, "debe ser dificultad 1")
            return seqVerdict(e, t.last() + r, extra) // recurrencia: un paso
        }
        SEQ_NTH.matchEntire(s)?.let { m ->
            val t = terms(m.groupValues[1])
            val r = commonDifference(t) ?: parseFail("los términos no son aritméticos: $t")
            val n = m.groupValues[2].toInt()
            var v = t.first()
            repeat(n - 1) { v += r } // recurrencia, sin la fórmula cerrada
            extra += Triple("LUGAR_MAYOR_QUE_LOS_DADOS", n > t.size, "el lugar $n ya está escrito en el enunciado")
            return seqVerdict(e, v, extra)
        }
        SEQ_TWO.matchEntire(s)?.let { m ->
            val p = m.groupValues[1].toInt()
            val u = m.groupValues[2].toInt()
            val q = m.groupValues[3].toInt()
            val v = m.groupValues[4].toInt()
            val n = m.groupValues[5].toInt()
            extra += Triple("RAZON_ENTERA", q > p && (v - u) % (q - p) == 0, "(v - u) no es múltiplo de la cantidad de saltos")
            val r = (v - u) / (q - p)
            var current = u
            repeat(n - p) { current += r }
            extra += Triple("LUGARES_ORDENADOS", p < q && q < n, "lugares $p, $q, $n")
            return seqVerdict(e, current, extra)
        }
        val m = SEQ_COUNT.mustMatch(s)
        val t = terms(m.groupValues[1])
        val last = m.groupValues[2].toInt()
        val r = commonDifference(t) ?: parseFail("los términos no son aritméticos: $t")
        var value = t.first()
        var count = 1
        while (value != last && count < 5000 && (r > 0 && value < last || r < 0 && value > last)) {
            value += r
            count++
        }
        extra += Triple("EL_ULTIMO_TERMINO_PERTENECE", value == last, "la sucesión no llega a $last")
        return seqVerdict(e, count, extra)
    }

    private fun seqVerdict(e: Exercise, answer: Int, extra: MutableList<Triple<String, Boolean, String>>): Verdict {
        val parsed = e.options.map { text -> Regex("""^-?\d+$""").matchEntire(text)?.value?.toIntOrNull() }
        extra += allParsed(parsed)
        extra += Triple("RESPUESTA_ACOTADA", abs(answer) <= MAX_ANSWER, "respuesta $answer")
        return Verdict(parsed.map { it == answer }, extra)
    }

    // ---------- MCD y MCM ----------

    private val GL_PLAIN = Regex("""^Halla el (MCD|MCM) de (\d+) y (\d+)\.$""")
    private val GL_THREE = Regex("""^Halla el (MCD|MCM) de (\d+), (\d+) y (\d+)\.$""")
    private val GL_UNKNOWN = Regex("""^El MCD de dos números es (\d+) y su MCM es (\d+)\. Si uno de los números es (\d+), ¿cuál es el otro\?$""")
    private val GL_CONTEXTS: List<Pair<Regex, Boolean>> = listOf(
        Regex("""^Una cinta de (\d+) cm y otra de (\d+) cm se cortan en trozos de igual longitud, lo más largos posible y sin que sobre nada\. ¿Cuántos centímetros mide cada trozo\?$""") to true,
        Regex("""^Se reparten (\d+) manzanas y (\d+) naranjas en bolsas iguales, con la mayor cantidad posible de bolsas y sin que sobre fruta\. ¿Cuántas bolsas se arman\?$""") to true,
        Regex("""^Dos faros emiten un destello al mismo tiempo\. El primero destella cada (\d+) segundos y el segundo cada (\d+) segundos\. ¿Cada cuántos segundos destellan juntos\?$""") to false,
        Regex("""^Dos buses parten juntos de un terminal\. Uno sale cada (\d+) minutos y el otro cada (\d+) minutos\. ¿Dentro de cuántos minutos volverán a partir juntos\?$""") to false,
    )

    /** MCD por enumeración: el mayor divisor común, probando desde el menor de los números hacia abajo. */
    private fun bruteGcd(numbers: List<Int>): Int = (numbers.min() downTo 1).first { d -> numbers.all { it % d == 0 } }

    /** MCM por enumeración: el primer múltiplo del mayor que es múltiplo de todos. */
    private fun bruteLcm(numbers: List<Int>): Int {
        val hi = numbers.max()
        var k = hi
        while (!numbers.all { k % it == 0 }) k += hi
        return k
    }

    private fun gcdLcm(e: Exercise): Verdict {
        val s = e.statement
        val extra = mutableListOf<Triple<String, Boolean, String>>()
        fun expectedFor(kind: String, numbers: List<Int>) = if (kind == "MCD") bruteGcd(numbers) else bruteLcm(numbers)
        fun finish(expected: Int): Verdict {
            val parsed = e.options.map { text -> Regex("""^\d+$""").matchEntire(text)?.value?.toIntOrNull() }
            extra += allParsed(parsed)
            extra += Triple("RESPUESTA_ACOTADA", expected <= MAX_ANSWER, "respuesta $expected")
            return Verdict(parsed.map { it == expected }, extra)
        }
        GL_PLAIN.matchEntire(s)?.let { m ->
            val a = m.groupValues[2].toInt()
            val b = m.groupValues[3].toInt()
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 1, "dos números sin contexto deben ser dificultad 1")
            extra += Triple("NINGUNO_DIVIDE_AL_OTRO", a % b != 0 && b % a != 0 && a != b, "alguno de $a y $b divide al otro")
            extra += Triple("MCD_NO_TRIVIAL", bruteGcd(listOf(a, b)) >= 2, "$a y $b no tienen divisor común mayor que 1")
            return finish(expectedFor(m.groupValues[1], listOf(a, b)))
        }
        GL_THREE.matchEntire(s)?.let { m ->
            val numbers = listOf(m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4].toInt())
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 3, "tres números deben ser dificultad 3")
            extra += Triple("NUMEROS_DISTINTOS", numbers.toSet().size == 3, "números repetidos $numbers")
            extra += Triple("TERCER_NUMERO_CUENTA", expectedFor(m.groupValues[1], numbers) != expectedFor(m.groupValues[1], numbers.take(2)),
                "el tercer número no cambia el resultado de los dos primeros")
            return finish(expectedFor(m.groupValues[1], numbers))
        }
        GL_UNKNOWN.matchEntire(s)?.let { m ->
            val g = m.groupValues[1].toInt()
            val l = m.groupValues[2].toInt()
            val a = m.groupValues[3].toInt()
            // el otro número divide al MCM: se prueban todos sus candidatos
            val candidates = (1..l).filter { b -> bruteGcd(listOf(a, b)) == g && bruteLcm(listOf(a, b)) == l }
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 3, "debe ser dificultad 3")
            extra += Triple("OTRO_NUMERO_UNICO", candidates.size == 1, "candidatos para el otro número: $candidates")
            extra += Triple("MCD_DIVIDE_AL_MCM", l % g == 0 && a % g == 0 && l % a == 0, "datos incoherentes: MCD $g, MCM $l, número $a")
            return finish(candidates.singleOrNull() ?: parseFail("el otro número no es único: $candidates"))
        }
        for ((regex, isGcd) in GL_CONTEXTS) {
            val m = regex.matchEntire(s) ?: continue
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 2, "un problema con contexto debe ser dificultad 2")
            extra += Triple("NINGUNO_DIVIDE_AL_OTRO", a % b != 0 && b % a != 0 && a != b, "alguno de $a y $b divide al otro")
            return finish(expectedFor(if (isGcd) "MCD" else "MCM", listOf(a, b)))
        }
        parseFail("enunciado no reconocido: \"${s.take(110)}\"")
    }

    // ---------- interés simple ----------

    private val SI_INTEREST = Regex("""^Se depositan S/ (\d+) a una tasa de interés simple de (\d+)% anual durante (\d+) (año|años)\. ¿Cuánto interés se gana\?$""")
    private val SI_AMOUNT = Regex("""^Se depositan S/ (\d+) a una tasa de interés simple de (\d+)% anual durante (\d+) (año|años)\. ¿Cuál es el monto al final del plazo\?$""")
    private val SI_MONTHS = Regex("""^Se prestan S/ (\d+) a una tasa de interés simple de (\d+)% anual durante (\d+) meses\. ¿Cuánto interés se paga\?$""")
    private val SI_CAPITAL = Regex("""^¿Qué capital, colocado a una tasa de interés simple de (\d+)% anual durante (\d+) años, produce un interés de S/ (\d+)\?$""")
    private val SI_RATE = Regex("""^Un capital de S/ (\d+) produce S/ (\d+) de interés simple en (\d+) años\. ¿Cuál es la tasa de interés anual\?$""")
    private val SI_TIME = Regex("""^Un capital de S/ (\d+) colocado a una tasa de interés simple de (\d+)% anual produce S/ (\d+) de interés\. ¿En cuántos años lo produce\?$""")
    private val SI_MONEY = Regex("""^S/ (\d+)$""")
    private val SI_PERCENT = Regex("""^(\d+)%$""")
    private val SI_YEARS = Regex("""^(\d+) años$""")

    /** Interés simple con capital, tasa (%) y tiempo (años) racionales. */
    private fun simple(c: AQ, r: AQ, t: AQ): AQ = c * r * t / AQ.of(100)

    private fun simpleInterest(e: Exercise): Verdict {
        val s = e.statement
        val extra = mutableListOf<Triple<String, Boolean, String>>()
        fun q(text: String) = AQ.of(text.toLong())
        fun money(): List<AQ?> = e.options.map { t -> SI_MONEY.matchEntire(t)?.let { q(it.groupValues[1]) } }
        fun finishMoney(expected: AQ): Verdict {
            val parsed = money()
            extra += allParsed(parsed)
            extra += Triple("RESPUESTA_ENTERA", expected.isInteger && expected.signum > 0 && clean(expected, 1), "respuesta $expected")
            return Verdict(parsed.map { it == expected }, extra)
        }
        SI_INTEREST.matchEntire(s)?.let { m ->
            val t = q(m.groupValues[3])
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 1, "el interés directo debe ser dificultad 1")
            extra += Triple("PLURAL_ANIOS", (m.groupValues[3] == "1") == (m.groupValues[4] == "año"), "año/años no concuerda con ${m.groupValues[3]}")
            extra += Triple("CAPITAL_MULTIPLO_DE_100", m.groupValues[1].toInt() % 100 == 0, "capital ${m.groupValues[1]}")
            return finishMoney(simple(q(m.groupValues[1]), q(m.groupValues[2]), t))
        }
        SI_AMOUNT.matchEntire(s)?.let { m ->
            val c = q(m.groupValues[1])
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 2, "el monto debe ser dificultad 2")
            extra += Triple("PLURAL_ANIOS", (m.groupValues[3] == "1") == (m.groupValues[4] == "año"), "año/años no concuerda con ${m.groupValues[3]}")
            return finishMoney(c + simple(c, q(m.groupValues[2]), q(m.groupValues[3])))
        }
        SI_MONTHS.matchEntire(s)?.let { m ->
            val months = m.groupValues[3].toLong()
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 2, "el interés en meses debe ser dificultad 2")
            extra += Triple("MESES_MULTIPLO_DE_3", months % 3 == 0L, "meses $months")
            return finishMoney(simple(q(m.groupValues[1]), q(m.groupValues[2]), AQ.of(months, 12)))
        }
        SI_CAPITAL.matchEntire(s)?.let { m ->
            val r = q(m.groupValues[1])
            val t = q(m.groupValues[2])
            val interest = q(m.groupValues[3])
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 3, "hallar el capital debe ser dificultad 3")
            // cada opción se sustituye: el capital correcto debe producir exactamente ese interés
            val parsed = money()
            extra += allParsed(parsed)
            val matches = parsed.map { it != null && simple(it, r, t) == interest }
            extra += Triple("CAPITAL_MULTIPLO_DE_100", parsed.getOrNull(e.correctIndex)?.let { it.toIntOrNull()?.rem(100) == 0 } == true, "el capital correcto no es múltiplo de 100")
            return Verdict(matches, extra)
        }
        SI_RATE.matchEntire(s)?.let { m ->
            val c = q(m.groupValues[1])
            val interest = q(m.groupValues[2])
            val t = q(m.groupValues[3])
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 3, "hallar la tasa debe ser dificultad 3")
            val parsed = e.options.map { text -> SI_PERCENT.matchEntire(text)?.let { q(it.groupValues[1]) } }
            extra += allParsed(parsed)
            return Verdict(parsed.map { it != null && simple(c, it, t) == interest }, extra)
        }
        val m = SI_TIME.mustMatch(s)
        val c = q(m.groupValues[1])
        val r = q(m.groupValues[2])
        val interest = q(m.groupValues[3])
        extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 3, "hallar el tiempo debe ser dificultad 3")
        val parsed = e.options.map { text -> SI_YEARS.matchEntire(text)?.let { q(it.groupValues[1]) } }
        extra += allParsed(parsed)
        extra += Triple("TIEMPO_MAYOR_QUE_1", parsed.getOrNull(e.correctIndex)?.let { it.signum > 0 && it != AQ.ONE } == true, "el tiempo correcto no es de 2 años o más")
        return Verdict(parsed.map { it != null && simple(c, r, it) == interest }, extra)
    }

    // ---------- logaritmos ----------

    private val LG_DIRECT = Regex("""^Calcula el valor de:\nlog_(\d+)\((\d+)\)$""")
    private val LG_EXPR = Regex("""^Calcula el valor de:\nE = log_(\d+)\((\d+)\) ([+-]) log_(\d+)\((\d+)\)$""")
    private val LG_PRODUCT = Regex("""^Calcula el valor de:\nlog_(\d+)\((\d+) · (\d+)\)$""")
    private val LG_QUOTIENT = Regex("""^Calcula el valor de:\nlog_(\d+)\((\d+) ÷ (\d+)\)$""")
    private val LG_POWER = Regex("""^Calcula el valor de:\nlog_(\d+)\((\d+)\^(\d+)\)$""")
    private val LG_EQ_X = Regex("""^Resuelve la ecuación:\nlog_(\d+)\(x\) = (\d+)\n¿Cuál es el valor de x\?$""")
    private val LG_EQ_SHIFT = Regex("""^Resuelve la ecuación:\nlog_(\d+)\(x ([+-]) (\d+)\) = (\d+)\n¿Cuál es el valor de x\?$""")
    private val LG_EQ_BASE = Regex("""^Resuelve la ecuación:\nlog_x\((\d+)\) = (\d+)\n¿Cuál es el valor de x\?$""")

    /**
     * Logaritmo entero exacto de [arg] en base [base] por divisiones sucesivas entre la base (sin propiedades de
     * logaritmos ni potencias); `null` si [arg] no es una potencia exacta de [base].
     */
    private fun exactLog(base: Long, arg: java.math.BigInteger): Int? {
        if (base < 2 || arg.signum() <= 0) return null
        val b = java.math.BigInteger.valueOf(base)
        var rest = arg
        var count = 0
        while (rest != java.math.BigInteger.ONE) {
            if (rest.mod(b).signum() != 0) return null
            rest = rest.divide(b)
            count++
            if (count > 64) return null
        }
        return count
    }

    private fun exactLog(base: Long, arg: Long) = exactLog(base, java.math.BigInteger.valueOf(arg))

    private fun logarithms(e: Exercise): Verdict {
        val s = e.statement
        val extra = mutableListOf<Triple<String, Boolean, String>>()
        fun need(value: Int?, what: String): Int = value ?: parseFail("$what no es una potencia exacta de la base")
        fun finish(answer: Int): Verdict {
            val parsed = e.options.map { text -> Regex("""^\d+$""").matchEntire(text)?.value?.toIntOrNull() }
            extra += allParsed(parsed)
            extra += Triple("RESPUESTA_ACOTADA", answer in 1..MAX_ANSWER, "respuesta $answer")
            return Verdict(parsed.map { it == answer }, extra)
        }
        LG_DIRECT.matchEntire(s)?.let { m ->
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 1, "el logaritmo directo debe ser dificultad 1")
            val n = need(exactLog(m.groupValues[1].toLong(), m.groupValues[2].toLong()), m.groupValues[2])
            extra += Triple("LOGARITMO_MAYOR_QUE_1", n >= 2, "log = $n")
            return finish(n)
        }
        LG_EXPR.matchEntire(s)?.let { m ->
            val n1 = need(exactLog(m.groupValues[1].toLong(), m.groupValues[2].toLong()), m.groupValues[2])
            val n2 = need(exactLog(m.groupValues[4].toLong(), m.groupValues[5].toLong()), m.groupValues[5])
            val answer = if (m.groupValues[3] == "+") n1 + n2 else n1 - n2
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 2, "la suma o resta de logaritmos debe ser dificultad 2")
            extra += Triple("TERMINOS_DISTINTOS", !(m.groupValues[1] == m.groupValues[4] && n1 == n2), "los dos logaritmos son iguales")
            return finish(answer)
        }
        LG_PRODUCT.matchEntire(s)?.let { m ->
            val product = java.math.BigInteger(m.groupValues[2]).multiply(java.math.BigInteger(m.groupValues[3]))
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 3, "debe ser dificultad 3")
            extra += Triple("FACTORES_POTENCIAS", exactLog(m.groupValues[1].toLong(), m.groupValues[2].toLong()) != null &&
                exactLog(m.groupValues[1].toLong(), m.groupValues[3].toLong()) != null, "los factores no son potencias de la base")
            return finish(need(exactLog(m.groupValues[1].toLong(), product), "el producto"))
        }
        LG_QUOTIENT.matchEntire(s)?.let { m ->
            val x = java.math.BigInteger(m.groupValues[2])
            val y = java.math.BigInteger(m.groupValues[3])
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 3, "debe ser dificultad 3")
            extra += Triple("DIVISION_EXACTA", x.mod(y).signum() == 0 && x > y, "$x ÷ $y no es una división exacta mayor que 1")
            return finish(need(exactLog(m.groupValues[1].toLong(), x.divide(y)), "el cociente"))
        }
        LG_POWER.matchEntire(s)?.let { m ->
            val value = java.math.BigInteger(m.groupValues[2]).pow(m.groupValues[3].toInt())
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 3, "debe ser dificultad 3")
            return finish(need(exactLog(m.groupValues[1].toLong(), value), "la potencia"))
        }
        LG_EQ_X.matchEntire(s)?.let { m ->
            val base = m.groupValues[1].toLong()
            val n = m.groupValues[2].toInt()
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 2, "la ecuación log_b(x) = n debe ser dificultad 2")
            val parsed = e.options.map { parseAQ(it)?.toIntOrNull() }
            extra += allParsed(parsed)
            // sustitución: el logaritmo de la opción en esa base debe ser n
            return Verdict(parsed.map { it != null && it > 0 && exactLog(base, it.toLong()) == n }, extra)
        }
        LG_EQ_SHIFT.matchEntire(s)?.let { m ->
            val base = m.groupValues[1].toLong()
            val shift = if (m.groupValues[2] == "+") m.groupValues[3].toLong() else -m.groupValues[3].toLong()
            val n = m.groupValues[4].toInt()
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 3, "debe ser dificultad 3")
            val parsed = e.options.map { parseAQ(it)?.toIntOrNull() }
            extra += allParsed(parsed)
            val correct = parsed.getOrNull(e.correctIndex)
            extra += Triple("X_POSITIVA", correct != null && correct > 0, "x = $correct")
            return Verdict(parsed.map { it != null && it > 0 && exactLog(base, it.toLong() + shift) == n }, extra)
        }
        val m = LG_EQ_BASE.mustMatch(s)
        val arg = m.groupValues[1].toLong()
        val n = m.groupValues[2].toInt()
        extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 3, "debe ser dificultad 3")
        val parsed = e.options.map { parseAQ(it)?.toIntOrNull() }
        extra += allParsed(parsed)
        // sustitución: la base de la opción, con el argumento dado, debe tener logaritmo n
        return Verdict(parsed.map { it != null && it >= 2 && exactLog(it.toLong(), arg) == n }, extra)
    }

    // ---------- promedios ----------

    private val AV_NOTES = Regex("""^Las notas de (\p{L}+) en sus prácticas fueron (.+)\. ¿Cuál es el promedio de sus notas\?$""")
    private val AV_SALES = Regex("""^En (\d+) días seguidos, una tienda vendió (.+) cuadernos, en ese orden\. ¿Cuántos cuadernos vendió en promedio por día\?$""")
    private val AV_PLAIN = Regex("""^Halla el promedio aritmético de (.+)\.$""")
    private val AV_MISSING = Regex("""^El promedio de (\d+) números es (\d+)\. Si (\d+) de ellos son (.+), ¿cuál es el número que falta\?$""")
    private val AV_TOTAL = Regex("""^El promedio de las edades de (\d+) amigos es (\d+) años\. ¿Cuánto suman las edades de todos\?$""")
    private val AV_WEIGHTED = Regex("""^En un curso, la nota final se calcula con pesos: la práctica tiene peso (\d+), el parcial tiene peso (\d+) y el examen final tiene peso (\d+)\. Un alumno sacó (\d+) en la práctica, (\d+) en el parcial y (\d+) en el final\. ¿Cuál es su nota final\?$""")
    private val AV_GROUPS = Regex("""^El promedio de las notas de (\d+) alumnos es (\d+), y el de otros (\d+) alumnos es (\d+)\. ¿Cuál es el promedio de los (\d+) alumnos juntos\?$""")
    private val AV_ADDED = Regex("""^El promedio de (\d+) números es (\d+)\. Al agregar un número más, el promedio sube a (\d+)\. ¿Cuál es el número que se agregó\?$""")

    private fun listOfInts(text: String): List<Int> = text.replace(" y ", ", ").split(", ").map { it.trim().toInt() }

    private fun averages(e: Exercise): Verdict {
        val s = e.statement
        val extra = mutableListOf<Triple<String, Boolean, String>>()
        val parsed = e.options.map { text -> Regex("""^\d+$""").matchEntire(text)?.value?.toLongOrNull()?.let { AQ.of(it) } }
        extra += allParsed(parsed)
        fun result(matches: List<Boolean>, answer: AQ?): Verdict {
            extra += Triple("RESPUESTA_ENTERA_POSITIVA", answer != null && answer.isInteger && answer.signum > 0 && clean(answer, 1), "respuesta $answer")
            return Verdict(matches, extra)
        }
        fun plain(values: List<Int>): Verdict {
            val mean = AQ.of(values.sum().toLong(), values.size.toLong())
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 1, "el promedio directo debe ser dificultad 1")
            extra += Triple("DATOS_SUFICIENTES", values.size in 4..6, "hay ${values.size} datos")
            return result(parsed.map { it == mean }, mean)
        }
        AV_NOTES.matchEntire(s)?.let { return plain(listOfInts(it.groupValues[2])) }
        AV_PLAIN.matchEntire(s)?.let { return plain(listOfInts(it.groupValues[1])) }
        AV_SALES.matchEntire(s)?.let { m ->
            val values = listOfInts(m.groupValues[2])
            extra += Triple("CANTIDAD_COINCIDE", values.size == m.groupValues[1].toInt(), "dice ${m.groupValues[1]} días y lista ${values.size} datos")
            return plain(values)
        }
        AV_MISSING.matchEntire(s)?.let { m ->
            val count = m.groupValues[1].toLong()
            val mean = AQ.of(m.groupValues[2].toLong())
            val known = listOfInts(m.groupValues[4])
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 2, "el dato que falta debe ser dificultad 2")
            extra += Triple("CANTIDAD_COINCIDE", known.size.toLong() == count - 1 && m.groupValues[3].toLong() == count - 1, "los datos conocidos no son ${count - 1}")
            // sustitución: con el dato propuesto, el promedio de los $count números debe ser el dado
            val matches = parsed.map { it != null && (AQ.of(known.sum().toLong()) + it) / AQ.of(count) == mean }
            return result(matches, parsed.getOrNull(e.correctIndex))
        }
        AV_TOTAL.matchEntire(s)?.let { m ->
            val count = AQ.of(m.groupValues[1].toLong())
            val mean = AQ.of(m.groupValues[2].toLong())
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 2, "la suma total debe ser dificultad 2")
            // sustitución: una suma dividida entre la cantidad debe dar el promedio
            return result(parsed.map { it != null && it / count == mean }, parsed.getOrNull(e.correctIndex))
        }
        AV_WEIGHTED.matchEntire(s)?.let { m ->
            val v = m.groupValues.drop(1).map { it.toLong() }
            val weighted = AQ.of(v[0] * v[3] + v[1] * v[4] + v[2] * v[5], v[0] + v[1] + v[2])
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 3, "el promedio ponderado debe ser dificultad 3")
            extra += Triple("PONDERADO_DISTINTO_DEL_SIMPLE", weighted != AQ.of(v[3] + v[4] + v[5], 3), "el promedio ponderado coincide con el simple")
            return result(parsed.map { it == weighted }, weighted)
        }
        AV_GROUPS.matchEntire(s)?.let { m ->
            val v = m.groupValues.drop(1).map { it.toLong() }
            val combined = AQ.of(v[0] * v[1] + v[2] * v[3], v[0] + v[2])
            extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 3, "el promedio de dos grupos debe ser dificultad 3")
            extra += Triple("TOTAL_COINCIDE", v[4] == v[0] + v[2], "dice ${v[4]} alumnos y son ${v[0] + v[2]}")
            return result(parsed.map { it == combined }, combined)
        }
        val m = AV_ADDED.mustMatch(s)
        val count = m.groupValues[1].toLong()
        val before = AQ.of(m.groupValues[2].toLong())
        val after = AQ.of(m.groupValues[3].toLong())
        extra += Triple("TIPO_POR_DIFICULTAD", e.difficulty == 3, "el dato agregado debe ser dificultad 3")
        // sustitución: con el dato agregado, el promedio de los count + 1 números debe ser el nuevo promedio
        val matches = parsed.map { it != null && (before * AQ.of(count) + it) / AQ.of(count + 1) == after }
        return result(matches, parsed.getOrNull(e.correctIndex))
    }
}
