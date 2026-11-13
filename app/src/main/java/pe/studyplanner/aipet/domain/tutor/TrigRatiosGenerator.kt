package pe.studyplanner.aipet.domain.tutor

/**
 * Razones trigonométricas en triángulos notables (30°-60°, 45° y 37°-53°).
 * Nivel 1: definición con los lados de un triángulo, o valor de una razón notable.
 * Nivel 2: expresiones con dos razones notables de la misma familia.
 * Nivel 3: cálculo de un lado de un triángulo notable.
 *
 * Nota: los ángulos 37° y 53° son aproximaciones de los ángulos del triángulo 3-4-5 (36,87° y 53,13°); en el
 * examen de admisión se trabajan como notables, y los enunciados lo indican.
 */
class TrigRatiosGenerator : BaseGenerator(TOPIC_ID) {

    private enum class Fn(val label: String) { SEN("sen"), COS("cos"), TAN("tan") }

    private enum class Role(
        val phrase: String,
        val short: String,
        val article: String,
        val ofPhrase: String,
        val toPhrase: String,
    ) {
        OPP("el cateto opuesto a ese ángulo", "cateto opuesto", "el cateto opuesto", "del cateto opuesto", "al cateto opuesto"),
        ADJ("el cateto adyacente a ese ángulo", "cateto adyacente", "el cateto adyacente", "del cateto adyacente", "al cateto adyacente"),
        HYP("la hipotenusa", "hipotenusa", "la hipotenusa", "de la hipotenusa", "a la hipotenusa");

        /** Con artículo y mayúscula inicial, para empezar una oración. */
        val cap: String get() = article.replaceFirstChar { it.uppercase() }
    }

    /** Triángulo notable: lados (opuesto, adyacente, hipotenusa) respecto del ángulo agudo [angle]. */
    private class Notable(val angle: Int, val opp: Surd, val adj: Surd, val hyp: Surd) {
        fun side(role: Role): Surd = when (role) {
            Role.OPP -> opp
            Role.ADJ -> adj
            Role.HYP -> hyp
        }

        fun value(fn: Fn): Surd = when (fn) {
            Fn.SEN -> opp / hyp
            Fn.COS -> adj / hyp
            Fn.TAN -> opp / adj
        }

        val complement: Notable get() = TABLE.getValue(90 - angle)

        /** Familia del ángulo: 30/60, 45 o 37/53. */
        val family: List<Int> get() = listOf(angle, 90 - angle).distinct().sorted()

        val ratioText: String
            get() = when (angle) {
                30, 60 -> "1 : √3 : 2 (opuesto a 30°, opuesto a 60° e hipotenusa)"
                45 -> "1 : 1 : √2 (cateto, cateto e hipotenusa)"
                else -> "3 : 4 : 5 (opuesto a 37°, opuesto a 53° e hipotenusa)"
            }
    }

    private class Term(val fn: Fn, val angle: Int) {
        val text: String get() = "${fn.label} $angle°"
        val value: Surd get() = TABLE.getValue(angle).value(fn)
    }

    override fun draft(rng: SeededRandom, difficulty: Int): Draft = when (difficulty) {
        1 -> if (rng.coin()) fromSides(rng) else directValue(rng)
        2 -> expression(rng)
        else -> sideLength(rng)
    }

    /** Verdadero si el valor es presentable al estudiante. */
    private fun sane(s: Surd, maxAbs: Long = 400, maxDen: Long = 40): Boolean =
        !s.isZero && s.coef.isSane(maxAbs, maxDen) && s.coef.signum > 0

    private fun withUnit(s: Surd, unit: String) = if (unit.isEmpty()) s.toString() else "$s $unit"

    private fun noteFor(angle: Int): String =
        if (angle == 37 || angle == 53) "Usando el triángulo notable de 37° y 53°, " else ""

    /** Aclaración al final de un enunciado de lados: en el examen 37° y 53° se tratan como notables. */
    private fun sideNote(angle: Int): String =
        if (angle == 37 || angle == 53) " (Usa el triángulo notable de 37° y 53°.)" else ""

    // ---- nivel 1: definición con los lados ----

    private fun fromSides(rng: SeededRandom): Draft {
        val (p, q, h) = rng.pick(TRIPLES).let { t -> val k = rng.int(1, 4); listOf(t[0] * k, t[1] * k, t[2] * k) }
        val swap = rng.coin()
        val opp = if (swap) q else p
        val adj = if (swap) p else q
        val fn = rng.pick(Fn.values().toList())
        val correct = ratio(fn, opp, adj, h)
        val raw = definitionNumbers(fn, opp, adj, h)
        val reduced = if (raw == correct.toString()) "" else " = $correct"
        val pool = WrongPool(valid = { it.signum > 0 }, preferIntegers = false)
        val all = listOf(
            Triple(rat(opp, h), "sen θ", "cateto opuesto ÷ hipotenusa"),
            Triple(rat(adj, h), "cos θ", "cateto adyacente ÷ hipotenusa"),
            Triple(rat(opp, adj), "tan θ", "cateto opuesto ÷ cateto adyacente"),
            Triple(rat(adj, opp), "cot θ (la inversa de tan θ)", "cateto adyacente ÷ cateto opuesto"),
            Triple(rat(h, opp), "csc θ (la inversa de sen θ)", "hipotenusa ÷ cateto opuesto"),
            Triple(rat(h, adj), "sec θ (la inversa de cos θ)", "hipotenusa ÷ cateto adyacente"),
        )
        all.forEach { (v, name, formula) ->
            pool.add(v, "Ese valor corresponde a $name = $formula. Para ${fn.label} θ usa ${definition(fn)}.")
        }
        return Draft(
            statement = "En un triángulo rectángulo, los catetos miden $p y $q y la hipotenusa mide $h. " +
                "Si θ es el ángulo opuesto al cateto de $opp, ¿cuánto vale ${fn.label} θ?",
            correct = correct.toString(),
            wrong = pool.items,
            hints = listOf(
                "Ubica los lados respecto de θ: el cateto opuesto está frente a θ, el adyacente está junto a θ y la hipotenusa es el lado más largo.",
                "Recuerda: sen θ = opuesto ÷ hipotenusa, cos θ = adyacente ÷ hipotenusa y tan θ = opuesto ÷ adyacente. Aquí necesitas ${fn.label} θ.",
                "El cateto opuesto a θ mide $opp, el adyacente mide $adj y la hipotenusa mide $h. Forma la razón ${fn.label} θ con ${definitionWords(fn, opp, adj, h)} y simplifica si se puede.",
            ),
            steps = listOf(
                "θ es el ángulo opuesto al cateto de $opp: el cateto opuesto mide $opp, el adyacente mide $adj y la hipotenusa mide $h.",
                "Definición: ${fn.label} θ = ${definition(fn)}.",
                "${fn.label} θ = $raw$reduced.",
                "Comprobación: $opp² + $adj² = ${opp * opp + adj * adj} = $h², así que los lados forman un triángulo rectángulo.",
                "Respuesta: ${fn.label} θ = $correct.",
            ),
            fillers = pool.fallbacks(correct),
        )
    }

    private fun ratio(fn: Fn, opp: Int, adj: Int, hyp: Int): Rational = when (fn) {
        Fn.SEN -> rat(opp, hyp)
        Fn.COS -> rat(adj, hyp)
        Fn.TAN -> rat(opp, adj)
    }

    private fun definition(fn: Fn): String = when (fn) {
        Fn.SEN -> "cateto opuesto ÷ hipotenusa"
        Fn.COS -> "cateto adyacente ÷ hipotenusa"
        Fn.TAN -> "cateto opuesto ÷ cateto adyacente"
    }

    private fun definitionNumbers(fn: Fn, opp: Int, adj: Int, hyp: Int): String = when (fn) {
        Fn.SEN -> "$opp/$hyp"
        Fn.COS -> "$adj/$hyp"
        Fn.TAN -> "$opp/$adj"
    }

    /** Qué lados dividir, con sus medidas, pero sin escribir el resultado (la pista no debe regalar la respuesta). */
    private fun definitionWords(fn: Fn, opp: Int, adj: Int, hyp: Int): String = when (fn) {
        Fn.SEN -> "el cateto opuesto ($opp) entre la hipotenusa ($hyp)"
        Fn.COS -> "el cateto adyacente ($adj) entre la hipotenusa ($hyp)"
        Fn.TAN -> "el cateto opuesto ($opp) entre el cateto adyacente ($adj)"
    }

    // ---- nivel 1: valor de una razón notable ----

    private fun directValue(rng: SeededRandom): Draft {
        val angle = rng.pick(ANGLES)
        val fn = rng.pick(Fn.values().toList())
        val term = Term(fn, angle)
        val correct = term.value
        val tri = TABLE.getValue(angle)
        val others = ANGLES.flatMap { a -> Fn.values().map { f -> Term(f, a) } }.filter { !(it.angle == angle && it.fn == fn) }
        fun wrongOf(t: Term) = Wrong(
            t.value.toString(),
            "Ese es el valor de ${t.text}, no de ${term.text}. Revisa qué lado es opuesto, adyacente e hipotenusa respecto de $angle°.",
        )
        val extras = if (angle == 45) {
            listOf(
                Wrong("1/2", "1/2 es el valor de sen 30° y cos 60°, no de las razones de 45°."),
                Wrong("√2", "√2 es la razón hipotenusa ÷ cateto (la inversa de sen 45°), no ${term.text}."),
            )
        } else {
            emptyList()
        }
        val sameFamily = others.filter { it.angle in tri.family }.map(::wrongOf) + extras
        // Los respaldos más creíbles primero: la familia 45° con la 30°-60° y viceversa; los de 37°-53° al final.
        val rest = rng.shuffled(others.filter { it.angle !in tri.family })
            .sortedBy { if (it.angle == 45 || (angle == 45 && it.angle in listOf(30, 60))) 0 else 1 }
            .map(::wrongOf)
        return Draft(
            statement = "${noteFor(angle)}halla el valor de ${term.text}.".replaceFirstChar { it.uppercase() },
            correct = correct.toString(),
            wrong = sameFamily,
            hints = listOf(
                "Dibuja el triángulo notable que contiene un ángulo de $angle°: sus lados son proporcionales a ${tri.ratioText}.",
                "Ubica los lados respecto de $angle°: sen = opuesto ÷ hipotenusa, cos = adyacente ÷ hipotenusa, tan = opuesto ÷ adyacente.",
                "Respecto de $angle°, el lado opuesto es ${tri.opp}, el adyacente es ${tri.adj} y la hipotenusa es ${tri.hyp}. " +
                    "Escribe ${fn.label} $angle° como cociente de los lados que corresponden.",
            ),
            steps = listOf(
                "En el triángulo notable de $angle° los lados son proporcionales a ${tri.ratioText}.",
                "Respecto de $angle°: lado opuesto = ${tri.opp}, lado adyacente = ${tri.adj}, hipotenusa = ${tri.hyp}.",
                "${term.text} = ${sideFormula(fn, tri)} = $correct.",
                "Comprobación numérica: ${term.text} ≈ ${Fmt.decimal(correct.toDouble(), 3)}.",
                "Respuesta: ${term.text} = $correct.",
            ),
            fillers = rest,
        )
    }

    private fun sideFormula(fn: Fn, tri: Notable): String = when (fn) {
        Fn.SEN -> "${tri.opp}/${tri.hyp}"
        Fn.COS -> "${tri.adj}/${tri.hyp}"
        Fn.TAN -> "${tri.opp}/${tri.adj}"
    }

    // ---- nivel 2: expresiones ----

    private enum class Op(val symbol: String) { ADD("+"), SUB("-"), MUL("·"), DIV("÷") }

    private class Expr(val a: Term, val op: Op, val b: Term, val value: Surd)

    private fun apply(op: Op, x: Surd, y: Surd): Surd? = when (op) {
        Op.ADD -> if (x.canAdd(y)) x + y else null
        Op.SUB -> if (x.canAdd(y)) x - y else null
        Op.MUL -> x * y
        Op.DIV -> if (y.isZero) null else x / y
    }

    private fun expression(rng: SeededRandom): Draft {
        val e = rng.search {
            val family = pick(FAMILIES)
            val t1 = Term(pick(Fn.values().toList()), pick(family))
            val t2 = Term(pick(Fn.values().toList()), pick(family))
            val op = pick(Op.values().toList())
            val value = apply(op, t1.value, t2.value)
            if ((t1.fn == t2.fn && t1.angle == t2.angle) || value == null || !sane(value)) null else Expr(t1, op, t2, value)
        }
        val wrong = mutableListOf<Wrong>()
        fun addWrong(v: Surd?, feedback: String) {
            if (v != null && v.coef.signum != 0 && v.coef.isSane(400, 60)) wrong += Wrong(v.toString(), feedback)
        }
        for ((idx, term) in listOf(e.a, e.b).withIndex()) {
            for ((alt, why) in alternatives(term)) {
                val left = if (idx == 0) alt else e.a.value
                val right = if (idx == 1) alt else e.b.value
                addWrong(apply(e.op, left, right), "Revisa el valor de ${term.text}: es ${term.value}, no $alt. $why")
            }
        }
        if (e.op == Op.SUB || e.op == Op.DIV) {
            addWrong(apply(e.op, e.b.value, e.a.value), "Invertiste el orden: la operación es ${e.a.text} ${e.op.symbol} ${e.b.text}, no al revés.")
        }
        val otherOps = Op.values().filter { it != e.op }.map { op ->
            Wrong("", "").let { op to apply(op, e.a.value, e.b.value) }
        }
        val opFill = otherOps.mapNotNull { (op, v) ->
            if (v == null || v.isZero || !v.coef.isSane(400, 60)) null
            else Wrong(v.toString(), "Cambiaste la operación: es ${e.a.text} ${e.op.symbol} ${e.b.text}, no ${e.a.text} ${op.symbol} ${e.b.text}.")
        }
        val tableFill = rng.shuffled(ANGLES.flatMap { a -> Fn.values().map { f -> Term(f, a) } }).map {
            Wrong(it.value.toString(), "Ese número es el valor de ${it.text}, que no es el resultado de la expresión. Calcula cada razón y opera.")
        }
        val tri = TABLE.getValue(e.a.angle)
        val values = listOf(e.a, e.b).distinctBy { it.text }
        return Draft(
            statement = "${noteFor(e.a.angle)}calcula el valor de E = ${e.a.text} ${e.op.symbol} ${e.b.text}.".replaceFirstChar { it.uppercase() },
            correct = e.value.toString(),
            wrong = wrong,
            hints = listOf(
                "Dibuja el triángulo notable de ${familyName(tri)}: sus lados son proporcionales a ${tri.ratioText}.",
                "Escribe el valor de cada razón (sen = opuesto ÷ hipotenusa, cos = adyacente ÷ hipotenusa, tan = opuesto ÷ adyacente) y luego opera.",
                values.joinToString(", ") { "${it.text} = ${it.value}" } + ". Ahora ${operationVerb(e.op)}.",
            ),
            steps = listOf(
                "Usamos el triángulo notable de ${familyName(tri)}, de lados proporcionales a ${tri.ratioText}.",
                values.joinToString(", ") { "${it.text} = ${it.value}" } + ".",
                "Reemplazamos: E = ${e.a.value} ${e.op.symbol} ${e.b.value}.",
                "Operamos: E = ${e.value}.",
                "Comprobación numérica: ${Fmt.decimal(e.a.value.toDouble(), 3)} ${e.op.symbol} ${Fmt.decimal(e.b.value.toDouble(), 3)} ≈ ${Fmt.decimal(e.value.toDouble(), 3)}.",
                "Respuesta: E = ${e.value}.",
            ),
            fillers = opFill + tableFill,
        )
    }

    /** "1 parte" o "√3 partes". */
    private fun partsOf(s: Surd): String = "$s ${if (s == Surd.of(1)) "parte" else "partes"}"

    private fun familyName(tri: Notable): String = tri.family.joinToString("° y ") { it.toString() } + "°"

    private fun operationVerb(op: Op): String = when (op) {
        Op.ADD -> "suma"
        Op.SUB -> "resta"
        Op.MUL -> "multiplica"
        Op.DIV -> "divide"
    }

    /** Valores erróneos típicos de un término, con la explicación del error. */
    private fun alternatives(t: Term): List<Pair<Surd, String>> {
        val tri = TABLE.getValue(t.angle)
        val out = mutableListOf<Pair<Surd, String>>()
        Fn.values().filter { it != t.fn }.forEach {
            out += tri.value(it) to "Confundiste ${t.fn.label} con ${it.label}."
        }
        if (tri.complement.angle != t.angle) {
            out += tri.complement.value(t.fn) to "Confundiste los ángulos ${t.angle}° y ${90 - t.angle}°."
        }
        if (!t.value.isZero) out += (Surd.of(1) / t.value) to "Usaste la razón inversa."
        return out.filter { it.first != t.value }
    }

    // ---- nivel 3: lados de un triángulo notable ----

    private class SideProblem(val tri: Notable, val known: Role, val asked: Role, val given: Surd, val unitPart: Int)

    private fun sideLength(rng: SeededRandom): Draft {
        val pr = rng.search {
            val tri = TABLE.getValue(pick(ANGLES))
            val known = pick(Role.values().toList())
            val asked = pick(Role.values().toList().filter { it != known })
            val k = int(2, 12)
            val given = tri.side(known) * rat(k)
            // El dato debe ser un número entero o k√2; los demás casos darían un dato irracional.
            if (!(given.isRational || (tri.angle == 45 && known == Role.HYP))) null else SideProblem(tri, known, asked, given, k)
        }
        val tri = pr.tri
        val answer = pr.given * tri.side(pr.asked) / tri.side(pr.known)
        val givenText = withUnit(pr.given, "cm")
        val parts = Surd.of(rat(pr.unitPart)).toString()
        val (main, backup) = sideDistractors(pr)
        val answerShifts = listOf(1, -1, 2, -2, 3, -3).mapNotNull { d ->
            val coef = answer.coef + d
            if (coef.signum <= 0) null else Surd.of(coef, answer.rad.toLong())
        }.map { Wrong(withUnit(it, "cm"), GENERIC_FEEDBACK) }
        return Draft(
            statement = "En un triángulo rectángulo, un ángulo agudo mide ${tri.angle}° y ${pr.known.phrase} mide $givenText. " +
                "¿Cuánto mide ${pr.asked.phrase}?" + sideNote(tri.angle),
            correct = withUnit(answer, "cm"),
            wrong = main,
            hints = listOf(
                "Usa el triángulo notable de ${familyName(tri)}: sus lados son proporcionales a ${tri.ratioText}.",
                "Respecto del ángulo de ${tri.angle}°: opuesto = ${tri.opp}, adyacente = ${tri.adj}, hipotenusa = ${tri.hyp} (en partes). " +
                    "El lado conocido (${pr.known.article}) corresponde a ${partsOf(tri.side(pr.known))} y mide $givenText: halla cuánto vale cada parte.",
                "Cada parte mide $parts cm. El lado pedido (${pr.asked.article}) tiene ${partsOf(tri.side(pr.asked))}: multiplica.",
            ),
            steps = listOf(
                "El triángulo notable de ${familyName(tri)} tiene lados proporcionales a ${tri.ratioText}.",
                "Respecto de ${tri.angle}°: opuesto = ${partsOf(tri.opp)}, adyacente = ${partsOf(tri.adj)}, hipotenusa = ${partsOf(tri.hyp)}.",
                "${pr.known.cap} mide $givenText y corresponde a ${partsOf(tri.side(pr.known))}, así que cada parte mide $parts cm.",
                "${pr.asked.cap} tiene ${partsOf(tri.side(pr.asked))}: ${tri.side(pr.asked)} × $parts = $answer.",
                pythagorasCheck(pr),
                "Respuesta: ${withUnit(answer, "cm")}.",
            ),
            fillers = backup + answerShifts,
        )
    }

    /** Distractores de un problema de lados: (preferidos con coeficiente entero, respaldo con fracciones). */
    private fun sideDistractors(pr: SideProblem): Pair<List<Wrong>, List<Wrong>> {
        val tri = pr.tri
        val k = rat(pr.unitPart)
        val main = mutableListOf<Wrong>()
        val backup = mutableListOf<Wrong>()
        fun add(v: Surd, feedback: String) {
            if (!sane(v, 800, 40)) return
            val w = Wrong(withUnit(v, "cm"), feedback)
            if (v.coef.isInteger) main += w else backup += w
        }
        for (r in Role.values()) {
            if (r == pr.asked) continue
            val msg = if (r == pr.known) {
                "Ese es el dato del problema (la medida ${r.ofPhrase}), no el lado que se pide."
            } else {
                "Ese es el valor ${r.ofPhrase}, pero el problema pide ${pr.asked.article}. Revisa cuál lado es opuesto o adyacente al ángulo de ${tri.angle}°."
            }
            add(tri.side(r) * k, msg)
        }
        val comp = tri.complement
        if (comp.angle != tri.angle) {
            add(
                comp.side(pr.asked) * k,
                "Intercambiaste los ángulos ${tri.angle}° y ${comp.angle}°: respecto de ${tri.angle}°, el lado opuesto tiene ${tri.opp} partes, no ${comp.opp}.",
            )
        }
        add(pr.given * tri.side(pr.asked), "Multiplicaste el dato por las partes del lado pedido. Primero halla cuánto vale UNA parte (dato ÷ partes del lado conocido).")
        if (tri.side(pr.asked).isRational && tri.side(pr.known).isRational) {
            val shift = tri.side(pr.asked).toRational() - tri.side(pr.known).toRational()
            add(pr.given + Surd.of(shift), "Sumaste la diferencia de partes. Los lados de un triángulo notable son PROPORCIONALES: se multiplica, no se suma.")
        }
        if (tri.angle == 45) {
            add(pr.given * Surd.of(2), "En el triángulo de 45° la hipotenusa NO es el doble del cateto: la hipotenusa es el cateto × √2.")
            add(pr.given / Surd.of(2), "En el triángulo de 45° la hipotenusa NO es el doble del cateto: la hipotenusa es el cateto × √2.")
        }
        for (p in Role.values()) for (q in Role.values()) {
            if (p == q || (p == pr.asked && q == pr.known)) continue
            add(
                pr.given * tri.side(p) / tri.side(q),
                "Multiplicaste por la razón «${p.short} ÷ ${q.short}», pero para pasar ${pr.known.ofPhrase} ${pr.asked.toPhrase} " +
                    "necesitas «${pr.asked.short} ÷ ${pr.known.short}».",
            )
        }
        return main to backup
    }

    /** Comprobación independiente de las leyes trigonométricas: los tres lados cumplen Pitágoras. */
    private fun pythagorasCheck(pr: SideProblem): String {
        fun length(role: Role) = pr.given * pr.tri.side(role) / pr.tri.side(pr.known)
        val o = length(Role.OPP).let { (it * it).toRational() }
        val a = length(Role.ADJ).let { (it * it).toRational() }
        val h = length(Role.HYP).let { (it * it).toRational() }
        check(o + a == h) { "Los lados no cumplen Pitágoras" }
        fun sq(r: Role) = length(r).toString().let { if ('√' in it) "($it)²" else "$it²" }
        return "Comprobación con Pitágoras: ${sq(Role.OPP)} + ${sq(Role.ADJ)} = $o + $a = $h = ${sq(Role.HYP)}."
    }

    companion object {
        const val TOPIC_ID = "razones-trigonometricas"

        private val TABLE: Map<Int, Notable> = listOf(
            Notable(30, Surd.of(1), Surd.of(1, 3), Surd.of(2)),
            Notable(60, Surd.of(1, 3), Surd.of(1), Surd.of(2)),
            Notable(45, Surd.of(1), Surd.of(1), Surd.of(1, 2)),
            Notable(37, Surd.of(3), Surd.of(4), Surd.of(5)),
            Notable(53, Surd.of(4), Surd.of(3), Surd.of(5)),
        ).associateBy { it.angle }

        private val ANGLES = listOf(30, 60, 45, 37, 53)
        private val FAMILIES = listOf(listOf(30, 60), listOf(45), listOf(37, 53))
        private val TRIPLES = listOf(listOf(3, 4, 5), listOf(5, 12, 13), listOf(8, 15, 17), listOf(7, 24, 25))
    }
}
