package pe.studyplanner.aipet.domain.tutor

import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Auditoría de contenido: comprueba por máquina que los ejercicios generados son correctos.
 *
 * Por cada tema y dificultad (1..3) y cada semilla se genera el ejercicio y se verifica, con cálculos
 * INDEPENDIENTES de los generadores (ver [AuditVerifiers]): que exactamente una opción sea la respuesta calculada,
 * que sea la marcada como correcta, que haya 5 opciones distintas, 3 pistas, retroalimentación por cada opción
 * incorrecta, pasos coherentes, texto limpio y formato peruano. Se ejecuta con `ContentAuditKt`:
 *
 *     ContentAuditKt [--seeds N] [--first-seed S] [--report ruta/AUDIT_REPORT.md] [--topic prefijo]
 *
 * Termina con código 1 si hay algún fallo. No depende de Android; solo se ejecuta en la JVM de desarrollo.
 */
object ContentAudit {

    /** Semillas por tema y dificultad por defecto. */
    const val DEFAULT_SEEDS = 500

    private const val MIN_LETTER_TOLERANCE = 8.0
    private const val LETTER_SIGMAS = 5.0

    /** Un fallo concreto: reproducible con `TutorRegistry.generate(topicId, seed, difficulty)`. */
    data class Failure(val topicId: String, val difficulty: Int, val seed: Long, val check: String, val detail: String)

    /** Conteo de verificaciones y fallos, por identificador de verificación. */
    class Tally {
        val runs = linkedMapOf<String, Int>()
        val fails = linkedMapOf<String, Int>()
        val failures = mutableListOf<Failure>()
        val counters = linkedMapOf<String, Long>()

        val totalRuns: Int get() = runs.values.sum()
        val totalFails: Int get() = fails.values.sum()

        fun merge(o: Tally) {
            o.runs.forEach { (k, v) -> runs[k] = (runs[k] ?: 0) + v }
            o.fails.forEach { (k, v) -> fails[k] = (fails[k] ?: 0) + v }
            o.counters.forEach { (k, v) -> counters[k] = (counters[k] ?: 0L) + v }
            failures += o.failures
        }
    }

    /** Resultado de un tema en una dificultad. */
    class Cell(val topicId: String, val difficulty: Int, val exercises: Int, val tally: Tally) {
        /** Cuántas veces la respuesta correcta quedó en A, B, C, D y E. */
        val correctLetterCounts = IntArray(Exercise.OPTION_COUNT)
        var distinctStatements = 0
        var wrongOptions = 0
        var genericFeedback = 0
    }

    /** Resultado completo de una auditoría. */
    class Result(
        val date: LocalDate,
        val timestamp: String,
        val seeds: Int,
        val firstSeed: Long,
        val cells: List<Cell>,
        val registry: Tally,
        val selfTest: List<Pair<String, Boolean>>,
    ) {
        val total = Tally().also { t -> cells.forEach { t.merge(it.tally) }; t.merge(registry) }
        val exercises: Int get() = cells.sumOf { it.exercises }
        val failures: Int get() = total.totalFails + selfTest.count { !it.second }
        val passed: Boolean get() = failures == 0
    }

    // ------------------------------------------------------------------------------------------------
    // Verificación de un ejercicio
    // ------------------------------------------------------------------------------------------------

    private class Sink(val topicId: String, val difficulty: Int, val seed: Long, val tally: Tally) {
        fun check(id: String, ok: Boolean, detail: () -> String) {
            tally.runs[id] = (tally.runs[id] ?: 0) + 1
            if (!ok) {
                tally.fails[id] = (tally.fails[id] ?: 0) + 1
                tally.failures += Failure(topicId, difficulty, seed, id, detail())
            }
        }
    }

    /**
     * Audita un ejercicio ya generado y devuelve los fallos como "VERIFICACION: detalle" (lista vacía si todo está
     * bien). No exige un generador: sirve para probar el auditor con ejercicios alterados.
     */
    fun auditExercise(e: Exercise): List<String> {
        val tally = Tally()
        auditInto(Sink(e.topicId, e.difficulty, -1, tally), e, null)
        return tally.failures.map { "${it.check}: ${it.detail}" }
    }

    private fun normalize(text: String) = text.lowercase().filter { !it.isWhitespace() }

    private fun snippet(text: String) = "«" + text.replace('\n', '⏎').take(90) + "»"

    private val BAD_TOKENS: List<Pair<String, Regex>> = listOf(
        "llave o signo de plantilla sin resolver" to Regex("""[{}$]"""),
        "null" to Regex("""\bnull\b"""),
        "NaN o Infinity" to Regex("""NaN|Infinity"""),
        "cero negativo (-0)" to Regex("""(?<![\p{L}\p{N}])-0(?![\p{N},.])"""),
        "signos duplicados" to Regex("""\+ -\d|- -\d|--|\+\+|\+ \+|- \+"""),
        "puntos suspensivos mal formados" to Regex("""(?<!\.)\.\.(?!\.)|\.{4,}"""),
        "espacios dobles o al borde" to Regex("""  |^\s|\s$"""),
        "espacio antes de coma o punto" to Regex(""" ,|(?<!\.) \.(?!\.)"""),
        "espacio dentro de paréntesis" to Regex("""\(\s|\s\)"""),
        "signos de interrogación repetidos" to Regex("""¿¿|\?\?|¡¡|!!"""),
    )

    private val PERUVIAN_FORMAT: List<Pair<String, Regex>> = listOf(
        "punto decimal o de miles entre dígitos (se usa coma)" to Regex("""\d\.\d"""),
        "S/ sin espacio y cifra" to Regex("""S/(?! \d)"""),
        "espacio antes de % o °" to Regex("""\d\s[%°]"""),
        "fracción unicode (se escribe 3/4)" to Regex("""[½¼¾⅓⅔⅕⅖⅗⅘⅙⅚⅛]"""),
        "notación LaTeX" to Regex("""\\frac|\\sqrt|\\cdot"""),
        "signo de dólar como moneda" to Regex("""\$"""),
    )

    private val TTS_LEFTOVER = Regex("""[\^√²³°÷·×=%≈θ/]""")
    private val FRACTION_TOKEN = Regex("""(?<![\d√/])(\d*)(√\d+)?/(\d+)""")
    private val RADICAND = Regex("""√(\d+)""")
    private val NUMBER_TOKEN = Regex("""\d+""")
    private const val MAX_OPTION_NUMBER = 20000L
    private const val MAX_DENOMINATOR = 64L
    private const val MAX_STATEMENT = 330
    private const val MAX_OPTION = 40
    private const val MAX_TEXT = 450

    private fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

    private fun isGenericFeedback(text: String) =
        text == GENERIC_FEEDBACK || text.startsWith("Ese valor no cumple la proporción")

    private fun auditInto(sink: Sink, e: Exercise, generator: ExerciseGenerator?) {
        val c = sink::check

        // --- estructura ---
        c("OPCIONES_5", e.options.size == Exercise.OPTION_COUNT) { "hay ${e.options.size} opciones" }
        c("OPCIONES_NO_VACIAS", e.options.all { it.isNotBlank() }) { "opciones: ${e.options}" }
        c("OPCIONES_DISTINTAS", e.options.map(::normalize).toSet().size == e.options.size) { "opciones repetidas: ${e.options}" }
        c("INDICE_EN_RANGO", e.correctIndex in e.options.indices) { "correctIndex=${e.correctIndex}" }
        c("DIFICULTAD", e.difficulty in 1..3 && (generator == null || e.id.contains("-d${e.difficulty}-"))) { "dificultad ${e.difficulty} en ${e.id}" }
        c("PISTAS_3", e.hints.size == Exercise.HINT_COUNT && e.hints.all { it.isNotBlank() } && e.hints.toSet().size == e.hints.size) { "pistas: ${e.hints.map(::snippet)}" }
        val wrongIndices = e.options.indices.filter { it != e.correctIndex }
        c("FEEDBACK_POR_OPCION", wrongIndices.all { !e.optionFeedback[it].isNullOrBlank() } && e.optionFeedback[e.correctIndex] == null) {
            "retroalimentación incompleta: ${e.optionFeedback.keys} (correcta=${e.correctIndex})"
        }
        c("PASOS", e.solutionSteps.size >= 3 && e.solutionSteps.all { it.isNotBlank() }) { "pasos: ${e.solutionSteps.size}" }

        // --- texto ---
        val feedbackTexts = e.optionFeedback.values.toList()
        val allTexts = listOf(e.statement) + e.options + e.hints + feedbackTexts + e.solutionSteps
        val dirty = allTexts.firstNotNullOfOrNull { t ->
            BAD_TOKENS.firstOrNull { it.second.containsMatchIn(t) }?.let { "${it.first} en ${snippet(t)}" }
                ?: if (t.count { it == '(' } != t.count { it == ')' }) "paréntesis desbalanceados en ${snippet(t)}" else null
        }
        c("TEXTO_LIMPIO", dirty == null) { dirty ?: "" }
        val badFormat = allTexts.firstNotNullOfOrNull { t ->
            PERUVIAN_FORMAT.firstOrNull { it.second.containsMatchIn(t) }?.let { "${it.first} en ${snippet(t)}" }
        }
        c("FORMATO_PERUANO", badFormat == null) { badFormat ?: "" }
        c(
            "COTAS",
            e.statement.length <= MAX_STATEMENT && e.options.all { it.length <= MAX_OPTION } &&
                (e.hints + feedbackTexts + e.solutionSteps).all { it.length <= MAX_TEXT } &&
                e.options.all { o -> NUMBER_TOKEN.findAll(o).all { it.value.length <= 5 && it.value.toLong() <= MAX_OPTION_NUMBER } } &&
                e.options.all { o -> FRACTION_TOKEN.findAll(o).all { it.groupValues[3].toLong() <= MAX_DENOMINATOR } },
        ) { "texto o números fuera de cotas: enunciado ${e.statement.length} car., opciones ${e.options}" }
        c(
            "FRACCIONES_REDUCIDAS",
            e.options.all { o ->
                FRACTION_TOKEN.findAll(o).all {
                    val coef = it.groupValues[1].ifEmpty { "1" }.toLong()
                    gcd(coef, it.groupValues[3].toLong()) == 1L && it.groupValues[3].toLong() > 1L
                }
            },
        ) { "fracción sin reducir en ${e.options}" }
        c(
            "RADICALES_SIMPLIFICADOS",
            e.options.all { o ->
                RADICAND.findAll(o).all { m ->
                    val r = m.groupValues[1].toLong()
                    r >= 2 && (2L..1000L).none { k -> k * k <= r && r % (k * k) == 0L }
                }
            },
        ) { "radical sin simplificar en ${e.options}" }

        // Las cadenas aritméticas "a = b = c" del texto deben ser verdaderas.
        val chainTexts = e.hints + e.solutionSteps + feedbackTexts + e.statement
        var chainsFound = 0L
        val badChain = chainTexts.firstNotNullOfOrNull { t ->
            val chains = ArithmeticChains.find(t)
            chainsFound += chains.size
            chains.firstOrNull { !it.consistent }?.let { "${it.text} → ${it.sides}" }
        }
        sink.tally.counters["cadenas_aritmeticas_verificadas"] = (sink.tally.counters["cadenas_aritmeticas_verificadas"] ?: 0L) + chainsFound
        c("ARITMETICA_EN_TEXTO", badChain == null) { "cadena falsa: $badChain" }

        // Texto apto para voz: tras la conversión no quedan símbolos matemáticos.
        val speechProblem = (listOf(e.statement) + e.options + e.hints + e.solutionSteps + feedbackTexts)
            .firstNotNullOfOrNull { t -> Speech.forTts(t).let { s -> TTS_LEFTOVER.find(s)?.let { "queda '${it.value}' en ${snippet(s)}" } } }
        c("TTS_SIN_SIMBOLOS", speechProblem == null) { speechProblem ?: "" }

        // --- respuesta: verificación independiente ---
        val verifier = AuditVerifiers.forTopic(e.topicId)
        c("VERIFICADOR_EXISTE", verifier != null) { "no hay verificador para el tema ${e.topicId}" }
        if (verifier != null) {
            val verdict = try {
                verifier(e)
            } catch (ex: AuditParseError) {
                c("ENUNCIADO_ANALIZABLE", false) { ex.message ?: "enunciado no analizable" }
                null
            } catch (ex: RuntimeException) {
                c("ENUNCIADO_ANALIZABLE", false) { "${ex.javaClass.simpleName}: ${ex.message}" }
                null
            }
            if (verdict != null) {
                c("ENUNCIADO_ANALIZABLE", true) { "" }
                val hits = verdict.matches.indices.filter { verdict.matches[it] }
                c("UNA_SOLA_CORRECTA", hits.size == 1) { "las opciones ${hits.map { e.label(it) }} coinciden con la respuesta calculada; opciones=${e.options}" }
                c("INDICE_COINCIDE", hits.size == 1 && hits[0] == e.correctIndex) {
                    "la respuesta calculada está en ${hits.map { e.label(it) }} y correctIndex=${e.label(e.correctIndex.coerceIn(0, 4))}"
                }
                verdict.extra.forEach { (id, ok, detail) -> c(id, ok) { detail } }
            }
        }

        // --- coherencia de la solución con la respuesta ---
        val correctText = e.options.getOrNull(e.correctIndex).orEmpty()
        val last = e.solutionSteps.lastOrNull().orEmpty()
        val token = Regex("""(?<![\p{L}\p{N}√/,-])""" + Regex.escape(correctText) + """(?![\p{L}\p{N}√/])""")
        c("PASO_FINAL_RESPUESTA", correctText.isNotEmpty() && last.startsWith("Respuesta:") && token.containsMatchIn(last)) {
            "el último paso ${snippet(last)} no declara la respuesta «$correctText»"
        }

        // --- reproducibilidad ---
        if (generator != null) {
            val again = generator.generate(sink.seed, sink.difficulty)
            c("DETERMINISTA", again == e) { "dos generaciones con la misma semilla difieren" }
            c("ID_REGENERABLE", TutorRegistry.regenerate(e.id) == e) { "regenerate(${e.id}) no reproduce el ejercicio" }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Ejecución
    // ------------------------------------------------------------------------------------------------

    /** Audita cada tema de [generators] en las dificultades 1..3 con las semillas 1..[seeds]. */
    fun run(
        seeds: Int = DEFAULT_SEEDS,
        generators: List<ExerciseGenerator> = TutorRegistry.generators,
        withSelfTest: Boolean = true,
        firstSeed: Long = 1L,
    ): Result {
        val cells = mutableListOf<Cell>()
        for (g in generators) {
            for (difficulty in 1..3) {
                val tally = Tally()
                val cell = Cell(g.topicId, difficulty, seeds, tally)
                val statements = HashSet<String>()
                for (offset in 0 until seeds) {
                    val seed = firstSeed + offset
                    val sink = Sink(g.topicId, difficulty, seed, tally)
                    val exercise = try {
                        g.generate(seed, difficulty)
                    } catch (ex: RuntimeException) {
                        sink.check("GENERACION_SIN_EXCEPCION", false) { "${ex.javaClass.simpleName}: ${ex.message}" }
                        continue
                    }
                    sink.check("GENERACION_SIN_EXCEPCION", true) { "" }
                    auditInto(sink, exercise, g)
                    cell.correctLetterCounts[exercise.correctIndex]++
                    statements += exercise.statement
                    cell.wrongOptions += exercise.optionFeedback.size
                    cell.genericFeedback += exercise.optionFeedback.values.count(::isGenericFeedback)
                }
                cell.distinctStatements = statements.size
                cells += cell
            }
        }
        val registry = auditRegistry(generators)
        val letters = checkLetterBalance(cells)
        registry.merge(letters)
        val now = LocalDateTime.now()
        return Result(
            date = LocalDate.now(),
            timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
            seeds = seeds,
            firstSeed = firstSeed,
            cells = cells,
            registry = registry,
            selfTest = if (withSelfTest) selfTest() else emptyList(),
        )
    }

    /**
     * La letra de la respuesta correcta no debe estar sesgada. Lo esperado es 20% por letra; se tolera 8 puntos
     * (12% a 28%, el caso de la auditoría completa) o 5 desviaciones estándar si la muestra es pequeña, para que
     * una auditoría reducida no falle por simple azar.
     */
    private fun checkLetterBalance(cells: List<Cell>): Tally {
        val tally = Tally()
        for ((topic, group) in cells.groupBy { it.topicId }) {
            val total = group.sumOf { it.exercises }
            val counts = IntArray(Exercise.OPTION_COUNT) { i -> group.sumOf { it.correctLetterCounts[i] } }
            val tolerance = if (total == 0) 0.0 else maxOf(MIN_LETTER_TOLERANCE, LETTER_SIGMAS * Math.sqrt(20.0 * 80.0 / total))
            val ok = total == 0 || counts.all { Math.abs(it * 100.0 / total - 20.0) <= tolerance }
            val sink = Sink(topic, 0, 0, tally)
            sink.check("LETRA_CORRECTA_EQUILIBRADA", ok) { "distribución A-E: ${counts.toList()} de $total" }
        }
        return tally
    }

    /** Registro de temas: estructura, textos aptos para voz y ejemplo resuelto auditado. */
    fun auditRegistry(generators: List<ExerciseGenerator> = TutorRegistry.generators): Tally {
        val tally = Tally()
        val topics = TutorRegistry.topics
        val sink0 = Sink("(registro)", 0, 0, tally)
        sink0.check("REGISTRO_10_TEMAS", topics.size >= 10) { "hay ${topics.size} temas" }
        sink0.check("REGISTRO_IDS_UNICOS", topics.map { it.id }.toSet().size == topics.size) { "ids repetidos" }
        sink0.check("REGISTRO_UN_GENERADOR_POR_TEMA", topics.all { t -> generators.count { it.topicId == t.id } == 1 }) { "algún tema no tiene exactamente un generador" }
        for (t in topics) {
            val sink = Sink(t.id, 1, TutorRegistry.WORKED_EXAMPLE_SEED, tally)
            sink.check("TEMA_TEXTOS_BASICOS", t.title.isNotBlank() && t.objective.isNotBlank()) { "título u objetivo vacío" }
            sink.check("TEMA_TEMARIO", t.temario.isNotEmpty() && t.area in t.temario.map { it.area } && t.temario.all { it.item in 1..Temario.itemCount(it.area) }) {
                "referencias al temario inválidas: ${t.temario.map { it.label }}"
            }
            sink.check("TEMA_EXPLICACION_3_A_5", t.explanation.size in 3..5) { "${t.explanation.size} párrafos" }
            sink.check("TEMA_PARRAFOS_CORTOS", t.explanation.all { it.length in 40..420 }) {
                "longitudes: ${t.explanation.map { it.length }}"
            }
            val spoken = (t.explanation + t.objective).map { Speech.forTts(it) }
            sink.check("TEMA_EXPLICACION_APTA_PARA_VOZ", spoken.none { TTS_LEFTOVER.containsMatchIn(it) }) {
                spoken.firstNotNullOfOrNull { s -> TTS_LEFTOVER.find(s)?.let { "queda '${it.value}' en ${snippet(s)}" } } ?: ""
            }
            val ex = checkNotNull(t.workedExample) { "${t.id}: los temas de matemática traen ejemplo resuelto" }
            sink.check("EJEMPLO_RESUELTO_COMPLETO", ex.statement.isNotBlank() && ex.steps.size >= 3 && ex.answer.isNotBlank()) { "ejemplo incompleto" }
            val generator = generators.firstOrNull { it.topicId == t.id }
            if (generator != null) {
                val exercise = generator.generate(TutorRegistry.WORKED_EXAMPLE_SEED, TutorRegistry.WORKED_EXAMPLE_DIFFICULTY)
                sink.check("EJEMPLO_RESUELTO_ES_DEL_GENERADOR", WorkedExample.from(exercise) == ex) { "el ejemplo no coincide con el generador (semilla 0)" }
                auditInto(sink, exercise, generator)
            }
        }
        return tally
    }

    // ------------------------------------------------------------------------------------------------
    // Autoprueba del auditor: ejercicios alterados a propósito que DEBEN ser detectados
    // ------------------------------------------------------------------------------------------------

    /** Cada alteración se aplica a un ejercicio real y el auditor debe reportar al menos un fallo. */
    fun selfTest(): List<Pair<String, Boolean>> {
        val out = mutableListOf<Pair<String, Boolean>>()
        for (g in TutorRegistry.generators) {
            val base = g.generate(7, 2)
            if (auditExercise(base).isNotEmpty()) out += "${g.topicId}: el ejercicio base sin alterar ya falla" to false
            for ((name, mutated) in mutations(base)) {
                out += "${g.topicId}: $name" to auditExercise(mutated).isNotEmpty()
            }
        }
        return out
    }

    /** Variantes defectuosas de [e] (la estructura sigue siendo válida para el constructor de [Exercise]). */
    internal fun mutations(e: Exercise): List<Pair<String, Exercise>> {
        val result = mutableListOf<Pair<String, Exercise>>()
        val otherIndex = (e.correctIndex + 1) % Exercise.OPTION_COUNT
        result += "marca una opción incorrecta como correcta" to e.copy(
            correctIndex = otherIndex,
            optionFeedback = e.optionFeedback.filterKeys { it != otherIndex } + (e.correctIndex to "Mensaje de prueba para la antigua correcta."),
        )
        result += "una pista con llave sin resolver" to e.copy(hints = listOf(e.hints[0], e.hints[1] + " {x}", e.hints[2]))
        result += "retroalimentación vacía" to e.copy(optionFeedback = e.optionFeedback.mapValues { (k, v) -> if (k == e.optionFeedback.keys.first()) " " else v })
        result += "el enunciado cambia y la respuesta ya no corresponde" to e.copy(statement = bumpFirstNumber(e.statement))
        result += "la respuesta final del paso a paso es otra" to e.copy(solutionSteps = e.solutionSteps.dropLast(1) + "Respuesta: 999999.")
        result += "decimal con punto en una opción" to withOption(e, otherIndex, "3.5")
        result += "texto con null" to e.copy(solutionSteps = listOf("null") + e.solutionSteps.drop(1))
        result += "dos pistas iguales" to e.copy(hints = listOf(e.hints[0], e.hints[0], e.hints[2]))
        parseAQ(e.options[e.correctIndex])?.let { q ->
            // Una opción incorrecta equivalente a la correcta (por ejemplo "6/2" y "3"): dos respuestas válidas.
            val twin = if (q.isInteger) "${q.n.multiply(java.math.BigInteger.valueOf(2))}/2" else null
            if (twin != null) result += "dos opciones equivalentes a la respuesta" to withOption(e, otherIndex, twin)
        }
        return result
    }

    private fun withOption(e: Exercise, index: Int, text: String): Exercise {
        val options = e.options.toMutableList()
        options[index] = text
        return e.copy(options = options)
    }

    private fun bumpFirstNumber(text: String): String {
        val m = Regex("""\d+""").find(text) ?: return text + " 1"
        return text.replaceRange(m.range, (m.value.toLong() + 1).toString())
    }

    // ------------------------------------------------------------------------------------------------
    // Informe
    // ------------------------------------------------------------------------------------------------

    private val CHECK_DESCRIPTIONS: Map<String, String> = linkedMapOf(
        "GENERACION_SIN_EXCEPCION" to "El generador devuelve un ejercicio sin lanzar excepciones.",
        "OPCIONES_5" to "Hay exactamente 5 opciones.",
        "OPCIONES_NO_VACIAS" to "Ninguna opción está vacía.",
        "OPCIONES_DISTINTAS" to "Las 5 opciones son distintas (sin importar mayúsculas ni espacios).",
        "INDICE_EN_RANGO" to "correctIndex está entre 0 y 4.",
        "DIFICULTAD" to "La dificultad está entre 1 y 3 y coincide con la pedida.",
        "PISTAS_3" to "Hay 3 pistas no vacías y distintas entre sí.",
        "FEEDBACK_POR_OPCION" to "Cada opción incorrecta tiene retroalimentación y la correcta no.",
        "PASOS" to "La solución tiene al menos 3 pasos no vacíos.",
        "TEXTO_LIMPIO" to "Sin '{', '}', '$', 'null', 'NaN', '-0', signos o espacios mal formados ni paréntesis desbalanceados.",
        "FORMATO_PERUANO" to "Coma decimal (no punto), 'S/ 120', fracciones '3/4', sin espacio antes de % o °.",
        "COTAS" to "Longitudes razonables, números de las opciones hasta 20000 y denominadores hasta 64.",
        "FRACCIONES_REDUCIDAS" to "Las fracciones de las opciones están reducidas.",
        "RADICALES_SIMPLIFICADOS" to "Los radicales de las opciones están simplificados.",
        "ARITMETICA_EN_TEXTO" to "Toda cadena numérica 'a = b = c' escrita en enunciado, pistas, pasos y avisos es verdadera.",
        "TTS_SIN_SIMBOLOS" to "Tras la conversión a voz no quedan símbolos como ^ √ ² ° ÷ · = % /.",
        "VERIFICADOR_EXISTE" to "El tema tiene verificador independiente.",
        "ENUNCIADO_ANALIZABLE" to "El enunciado impreso se puede analizar con el verificador independiente.",
        "UNA_SOLA_CORRECTA" to "Exactamente UNA opción coincide con la respuesta calculada de forma independiente.",
        "INDICE_COINCIDE" to "Esa opción es la que marca correctIndex.",
        "PASO_FINAL_RESPUESTA" to "El último paso empieza con 'Respuesta:' y declara la opción correcta.",
        "DETERMINISTA" to "La misma semilla y dificultad producen exactamente el mismo ejercicio.",
        "ID_REGENERABLE" to "El id del ejercicio permite regenerarlo con TutorRegistry.regenerate.",
        "LETRA_CORRECTA_EQUILIBRADA" to "La letra de la correcta no está sesgada (cada letra a 8 puntos de 20% por tema, o 5 desviaciones si hay pocas semillas).",
        "REGISTRO_10_TEMAS" to "El registro tiene al menos 10 temas.",
        "REGISTRO_IDS_UNICOS" to "Los ids de tema son únicos.",
        "REGISTRO_UN_GENERADOR_POR_TEMA" to "Cada tema tiene exactamente un generador.",
        "TEMA_TEXTOS_BASICOS" to "Título y objetivo no vacíos.",
        "TEMA_TEMARIO" to "El tema cita al menos un ítem válido del temario oficial de la UNAMBA y su área figura entre las citadas.",
        "TEMA_EXPLICACION_3_A_5" to "La explicación tiene de 3 a 5 párrafos.",
        "TEMA_PARRAFOS_CORTOS" to "Cada párrafo mide entre 40 y 420 caracteres (apto para leerlo en voz alta).",
        "TEMA_EXPLICACION_APTA_PARA_VOZ" to "La explicación convertida a voz no deja símbolos matemáticos.",
        "EJEMPLO_RESUELTO_COMPLETO" to "El ejemplo resuelto tiene enunciado, 3 o más pasos y respuesta.",
        "EJEMPLO_RESUELTO_ES_DEL_GENERADOR" to "El ejemplo resuelto sale del generador con la semilla 0.",
        "OPCIONES_ANALIZABLES" to "Las 5 opciones tienen el formato esperado para el verificador.",
        "ECUACION_LINEAL" to "La ecuación impresa es de primer grado (comprobado con 3 puntos).",
        "RAIZ_LIMPIA" to "La solución es un entero pequeño.",
        "SUSTITUCION_COINCIDE_CON_INTERPOLACION" to "La opción marcada coincide con la raíz hallada por interpolación lineal.",
        "SISTEMA_LINEAL" to "Las dos ecuaciones impresas son lineales.",
        "SOLUCION_ENTERA" to "La solución (x, y) es de enteros pequeños.",
        "SUSTITUCION_COINCIDE_CON_CRAMER" to "La opción marcada coincide con la solución por regla de Cramer.",
        "RAICES_ENTERAS" to "Las raíces (por la fórmula con el discriminante) son enteras.",
        "SUSTITUCION_EN_RAICES" to "Cada raíz anula la ecuación impresa.",
        "RAICES_EN_ORDEN_ASCENDENTE" to "Las opciones con dos raíces las listan de menor a mayor.",
        "TIPO_POR_DIFICULTAD" to "El tipo de problema corresponde a la dificultad.",
        "CONCORDANCIA_VERBO" to "'cuesta' o 'cuestan' concuerda con el objeto.",
        "PREGUNTA_SEGUN_CAMBIO" to "La pregunta final corresponde a un aumento o a un descuento.",
        "GRAMATICA_OTRO_UN" to "'otro' o 'un' concuerda con el tipo de los dos cambios.",
        "VARIACION_ENTERA_NO_NULA" to "La variación total es un entero distinto de cero.",
        "PRECIO_ENTERO" to "El precio final es un entero positivo.",
        "RESPUESTA_ENTERA_POSITIVA" to "La respuesta (regla de tres o promedios) es un entero positivo.",
        "UNIDAD_Y_PLURAL_EN_OPCIONES" to "Todas las opciones usan la unidad correcta y el singular/plural correcto.",
        "CANTIDADES_EN_PLURAL" to "Las cantidades del enunciado son 2 o más (la plantilla va en plural).",
        "DATOS_DISTINTOS" to "La cantidad pedida no repite el dato.",
        "RESPUESTA_LIMPIA" to "El valor es un entero o una fracción sencilla.",
        "ETIQUETAS_DISTINTAS" to "Los ángulos A, B y C del enunciado son distintos.",
        "TRIANGULO_VALIDO" to "Los datos forman un triángulo válido.",
        "X_ENTERO" to "El valor de x es entero.",
        "ANGULOS_VALIDOS" to "Los tres ángulos resultantes son enteros entre 1° y 179°.",
        "SUMA_180" to "Los tres ángulos resultantes suman 180°.",
        "EXTERIOR_IGUAL_A_SUMA" to "El ángulo exterior resultante es la suma de los dos no adyacentes.",
        "ANGULO_ENTRE_1_Y_179" to "La respuesta está entre 1° y 179°.",
        "UNIDADES_IGUALES" to "Las unidades del enunciado son las mismas.",
        "HIPOTENUSA_MAYOR" to "La hipotenusa dada es mayor que el cateto dado.",
        "TERNA_PITAGORICA_ENTERA" to "El cuadrado esperado es un cuadrado perfecto (lados enteros).",
        "CUADRADO_DE_LA_OPCION_CORRECTA" to "El cuadrado de la longitud de la opción marcada cumple a² + b² = c².",
        "TERNA_PITAGORICA" to "Los lados del enunciado cumplen a² + b² = c².",
        "OPUESTO_ES_UN_CATETO" to "El cateto opuesto nombrado es uno de los catetos.",
        "NOTA_37_53" to "La aclaración del triángulo 37°-53° aparece si y solo si el ángulo es 37° o 53°.",
        "MISMA_FAMILIA" to "Los dos ángulos de la expresión son del mismo triángulo notable.",
        "TERMINOS_DISTINTOS" to "Los dos términos de la expresión no son idénticos.",
        "RESULTADO_POSITIVO" to "El valor de la expresión es positivo.",
        "PITAGORAS_EN_LOS_TRES_LADOS" to "Los tres lados calculados con la tabla trigonométrica cumplen Pitágoras.",
        "LADO_CONOCIDO_DISTINTO_DEL_PEDIDO" to "Se pide un lado distinto del que se da.",
        "ANGULO_NOTABLE" to "El ángulo es 30°, 37°, 45°, 53° o 60°.",
        "LUGAR_MAYOR_QUE_LOS_DADOS" to "El lugar pedido no está ya escrito en el enunciado.",
        "RAZON_ENTERA" to "La razón calculada con dos términos es entera.",
        "LUGARES_ORDENADOS" to "Los lugares del enunciado están en orden creciente.",
        "EL_ULTIMO_TERMINO_PERTENECE" to "El último término del enunciado pertenece a la sucesión.",
        "RESPUESTA_ACOTADA" to "La respuesta no supera 20000 en valor absoluto.",
        "NINGUNO_DIVIDE_AL_OTRO" to "En el MCD/MCM de dos números, ninguno divide al otro y son distintos.",
        "MCD_NO_TRIVIAL" to "Los dos números tienen un divisor común mayor que 1.",
        "NUMEROS_DISTINTOS" to "Los tres números del enunciado son distintos.",
        "TERCER_NUMERO_CUENTA" to "El tercer número cambia el resultado respecto de los dos primeros.",
        "OTRO_NUMERO_UNICO" to "Hay exactamente un número que cumple el MCD y el MCM dados con el número conocido.",
        "MCD_DIVIDE_AL_MCM" to "El MCD divide al MCM y al número dado, y el número dado divide al MCM.",
        "RESPUESTA_ENTERA" to "La respuesta de interés simple es un entero positivo.",
        "PLURAL_ANIOS" to "'año' o 'años' concuerda con la cantidad del enunciado.",
        "CAPITAL_MULTIPLO_DE_100" to "El capital es múltiplo de 100 (así todas las respuestas son enteras).",
        "MESES_MULTIPLO_DE_3" to "El tiempo en meses es múltiplo de 3.",
        "TIEMPO_MAYOR_QUE_1" to "El tiempo pedido es de 2 años o más (la opción va en plural).",
        "LOGARITMO_MAYOR_QUE_1" to "El logaritmo directo vale al menos 2.",
        "FACTORES_POTENCIAS" to "Los factores dentro del logaritmo son potencias exactas de la base.",
        "DIVISION_EXACTA" to "El cociente dentro del logaritmo es una división exacta mayor que 1.",
        "X_POSITIVA" to "La solución x de la ecuación logarítmica es positiva (el argumento es válido).",
        "DATOS_SUFICIENTES" to "El promedio se pide sobre 4 a 6 datos.",
        "CANTIDAD_COINCIDE" to "La cantidad de datos que dice el enunciado coincide con la lista.",
        "PONDERADO_DISTINTO_DEL_SIMPLE" to "El promedio ponderado no coincide con el promedio simple.",
        "TOTAL_COINCIDE" to "El total de alumnos del enunciado es la suma de los dos grupos.",
    )

    private val TOPIC_METHODS: List<Pair<String, String>> = listOf(
        LinearEquationGenerator.TOPIC_ID to "Se analiza la ecuación impresa y se SUSTITUYE cada opción; además se resuelve por interpolación lineal.",
        LinearSystemGenerator.TOPIC_ID to "Se sustituye cada par en las dos ecuaciones impresas; además se resuelve con la regla de Cramer.",
        PercentageGenerator.TOPIC_ID to "Se recalcula con factores multiplicativos exactos (el generador suma y resta montos); la variación total con (1+a)(1+b)-1.",
        RuleOfThreeGenerator.TOPIC_ID to "Se reconoce la plantilla del enunciado, se decide directa/inversa y se recalcula con productos cruzados; se revisan unidades y plural.",
        ExponentLawsGenerator.TOPIC_ID to "Se evalúa la expresión impresa con racionales exactos (BigInteger), sin usar las leyes.",
        QuadraticGenerator.TOPIC_ID to "Se extraen a, b y c evaluando la ecuación impresa, se usa el discriminante y se sustituyen las raíces.",
        TriangleAnglesGenerator.TOPIC_ID to "Se resuelve la ecuación de ángulos impresa y se comprueba que los tres suman 180° (o que el exterior es la suma de los no adyacentes).",
        PythagorasGenerator.TOPIC_ID to "Se comparan CUADRADOS de longitudes (a² + b² = c²) con racionales exactos, sin sacar raíces.",
        TrigRatiosGenerator.TOPIC_ID to "Tabla independiente: Math.sin/cos/tan para 30°, 45° y 60°; 3-4-5 para 37° y 53° (convención del examen); comparación numérica.",
        ArithmeticSequenceGenerator.TOPIC_ID to "Se recorre la sucesión por recurrencia, término a término, sin la fórmula cerrada.",
        McdMcmGenerator.TOPIC_ID to "MCD y MCM por enumeración de divisores y de múltiplos (sin factorizar); el «otro número» se busca probando todos los candidatos que dividen al MCM.",
        SimpleInterestGenerator.TOPIC_ID to "Cada opción se SUSTITUYE en el interés dicho (capital, tasa o tiempo) con racionales exactos y se compara con el dato del enunciado.",
        LogarithmsGenerator.TOPIC_ID to "Logaritmo exacto por divisiones sucesivas entre la base (sin propiedades); las ecuaciones se verifican sustituyendo cada opción en el enunciado.",
        AveragesGenerator.TOPIC_ID to "Suma y cociente exactos con racionales; el dato que falta o agregado se comprueba sustituyéndolo en el promedio.",
    )

    /** Informe en Markdown (español) con los conteos reales de la corrida. */
    fun renderMarkdown(r: Result): String {
        val sb = StringBuilder()
        val verdict = if (r.passed) "APROBADO: 0 fallos" else "CON FALLOS: ${r.failures}"
        sb.appendLine("# Informe de auditoría de contenido")
        sb.appendLine()
        sb.appendLine("- Fecha: **${r.date}** (corrida a las ${r.timestamp.takeLast(5)})")
        sb.appendLine("- Generado por: `ContentAuditKt` (`tools/build.sh audit`)")
        sb.appendLine("- Alcance: ${r.cells.map { it.topicId }.distinct().size} temas × 3 dificultades × ${r.seeds} semillas (${r.firstSeed}..${r.firstSeed + r.seeds - 1}) = **${r.exercises} ejercicios**")
        sb.appendLine("- Verificaciones ejecutadas: **${r.total.totalRuns}** (incluye el registro de temas y los ${r.cells.map { it.topicId }.distinct().size} ejemplos resueltos)")
        sb.appendLine("- Fallos: **${r.total.totalFails}**")
        sb.appendLine("- Cadenas aritméticas del texto comprobadas una por una: **${r.total.counters["cadenas_aritmeticas_verificadas"] ?: 0}**")
        sb.appendLine("- Autoprueba del auditor (ejercicios alterados a propósito): **${r.selfTest.count { it.second }} de ${r.selfTest.size}** detectados")
        sb.appendLine("- Resultado: **$verdict**")
        sb.appendLine()
        sb.appendLine("## Cómo se verifica (independiente de los generadores)")
        sb.appendLine()
        sb.appendLine("El auditor NO llama a la lógica interna de los generadores. Lee el **enunciado impreso** (lo que ve el estudiante), recalcula la respuesta con matemática propia (`AuditMath.kt`: racionales con BigInteger y un analizador de expresiones) y pide que **exactamente una** de las 5 opciones coincida y que sea la marcada como correcta. Método por tema:")
        sb.appendLine()
        sb.appendLine("| Tema | Método de verificación |")
        sb.appendLine("|---|---|")
        TOPIC_METHODS.forEach { (id, text) -> sb.appendLine("| `$id` | $text |") }
        sb.appendLine()
        sb.appendLine("Verificaciones comunes a todos los temas: 5 opciones distintas, `correctIndex` válido, 3 pistas no vacías y distintas, retroalimentación en cada opción incorrecta, pasos con `Respuesta:` final que declara la opción correcta, texto sin `{`, `null`, `-0` ni signos mal formados, formato peruano (coma decimal, `S/ 120`, fracciones `3/4`), cotas razonables, fracciones reducidas, radicales simplificados, **cada cadena `a = b = c` del texto es aritméticamente cierta**, texto apto para voz, determinismo por semilla e id regenerable.")
        sb.appendLine()
        sb.appendLine("## Resultados por tema y dificultad")
        sb.appendLine()
        sb.appendLine("| Tema | Dif. | Ejercicios | Verificaciones | Fallos | Correcta A/B/C/D/E | Enunciados distintos | Feedback genérico |")
        sb.appendLine("|---|---|---|---|---|---|---|---|")
        for (c in r.cells) {
            val generic = if (c.wrongOptions == 0) "0%" else "%.1f%%".format(java.util.Locale.ROOT, 100.0 * c.genericFeedback / c.wrongOptions).replace('.', ',')
            sb.appendLine(
                "| `${c.topicId}` | ${c.difficulty} | ${c.exercises} | ${c.tally.totalRuns} | ${c.tally.totalFails} | " +
                    "${c.correctLetterCounts.joinToString("/")} | ${c.distinctStatements} | $generic |",
            )
        }
        sb.appendLine()
        sb.appendLine("`Enunciados distintos` es la variedad real entre las ${r.seeds} semillas; `Feedback genérico` es el porcentaje de opciones incorrectas que no vienen de un error típico concreto y usan un mensaje general de relleno.")
        sb.appendLine()
        sb.appendLine("## Verificaciones por tipo")
        sb.appendLine()
        sb.appendLine("| Verificación | Qué comprueba | Ejecuciones | Fallos |")
        sb.appendLine("|---|---|---|---|")
        val ids = r.total.runs.keys.sortedWith(compareBy({ CHECK_DESCRIPTIONS.keys.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it }))
        for (id in ids) {
            sb.appendLine("| `$id` | ${CHECK_DESCRIPTIONS[id] ?: "—"} | ${r.total.runs[id]} | ${r.total.fails[id] ?: 0} |")
        }
        sb.appendLine()
        sb.appendLine("## Autoprueba del auditor")
        sb.appendLine()
        sb.appendLine("Para no confiar en un auditor que nunca falla, se alteran ejercicios reales (marcar otra opción como correcta, pista con `{`, retroalimentación vacía, enunciado cambiado, opción con punto decimal, dos opciones equivalentes, etc.). El auditor debe detectar **todas** las alteraciones: **${r.selfTest.count { it.second }} de ${r.selfTest.size}**.")
        sb.appendLine()
        val missed = r.selfTest.filter { !it.second }
        if (missed.isNotEmpty()) {
            sb.appendLine("No detectadas:")
            missed.forEach { sb.appendLine("- ${it.first}") }
            sb.appendLine()
        }
        sb.appendLine("## Fallos")
        sb.appendLine()
        if (r.total.failures.isEmpty()) {
            sb.appendLine("Ninguno: 0 fallos en ${r.exercises} ejercicios y ${r.total.totalRuns} verificaciones.")
        } else {
            sb.appendLine("Primeros fallos (reproducir con `TutorRegistry.generate(tema, semilla, dificultad)`):")
            sb.appendLine()
            r.total.failures.take(40).forEach {
                sb.appendLine("- `${it.topicId}` dif. ${it.difficulty} semilla ${it.seed} — **${it.check}**: ${it.detail.replace('\n', ' ')}")
            }
        }
        sb.appendLine()
        sb.appendLine("## Limitaciones (qué NO demuestra esta auditoría)")
        sb.appendLine()
        sb.appendLine("- Verifica que la **respuesta** y la **aritmética escrita** son correctas y únicas para el enunciado impreso. No puede juzgar si un mensaje de retroalimentación explica bien el error de un distractor ni si la redacción en español es la mejor; eso se revisó leyendo el código de cada generador y muestras de cada tema, no es una prueba automática.")
        sb.appendLine("- Las razones de 37° y 53° siguen la convención del examen (triángulo 3-4-5, sen 37° = 3/5); son aproximaciones, no valores exactos, y los enunciados lo aclaran.")
        sb.appendLine("- Las semillas auditadas son ${r.firstSeed}..${r.firstSeed + r.seeds - 1}. Una sesión real usa semillas derivadas, mucho más grandes; el generador es el mismo, pero esas semillas concretas no están cubiertas por esta corrida (ver `--first-seed` y `--seeds` para auditar otros rangos).")
        sb.appendLine("- Los generadores de dificultad baja tienen poca variedad (ver 'Enunciados distintos'); la sesión evita repetir enunciados dentro de una misma sesión, pero un estudiante que repite el tema verá enunciados parecidos.")
        sb.appendLine("- La conversión a voz se audita buscando símbolos sin traducir; no se escucha con un sintetizador real.")
        sb.appendLine("- No hay una verificación automática de que una pista no regale la respuesta: la pista 3 es 'casi la solución' por diseño y, cuando el cálculo que falta es trivial (por ejemplo, un lado de 1 parte en el triángulo de 45°), la deja a la vista. Se buscó a mano el patrón '= respuesta' en las pistas de las 500 semillas y se corrigieron los casos donde la pista escribía el resultado (razones del triángulo con lados dados, ecuaciones con coeficiente 1); los que quedan son coincidencias de datos (otra cantidad del ejercicio vale lo mismo que la respuesta).")
        return sb.toString()
    }
}

/** Punto de entrada: `ContentAuditKt [--seeds N] [--first-seed S] [--report ruta] [--topic prefijo] [--no-selftest] [--show VERIFICACION]`. */
fun main(args: Array<String>) {
    var seeds = ContentAudit.DEFAULT_SEEDS
    var firstSeed = 1L
    var report: String? = null
    var topicPrefix = ""
    var selfTest = true
    var show: String? = null
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--seeds" -> seeds = args[++i].toInt()
            "--first-seed" -> firstSeed = args[++i].toLong()
            "--report" -> report = args[++i]
            "--topic" -> topicPrefix = args[++i]
            "--no-selftest" -> selfTest = false
            "--show" -> show = args[++i]
            else -> { println("Argumento desconocido: ${args[i]}"); System.exit(2) }
        }
        i++
    }
    val generators = TutorRegistry.generators.filter { it.topicId.startsWith(topicPrefix) }
    val result = ContentAudit.run(seeds, generators, selfTest, firstSeed)
    println("Auditoría ${result.date}: ${result.exercises} ejercicios, ${result.total.totalRuns} verificaciones, ${result.total.totalFails} fallos.")
    println("Autoprueba del auditor: ${result.selfTest.count { it.second }} de ${result.selfTest.size} alteraciones detectadas.")
    for (cell in result.cells) {
        println("  %-26s d%d  verificaciones=%-6d fallos=%d".format(cell.topicId, cell.difficulty, cell.tally.totalRuns, cell.tally.totalFails))
    }
    result.total.fails.forEach { (k, v) -> println("  FALLO $k: $v") }
    val listed = if (show == null) result.total.failures.take(15) else result.total.failures.filter { it.check == show }.distinctBy { it.topicId + it.difficulty + it.detail.take(40) }.take(40)
    listed.forEach { println("   - ${it.topicId} d${it.difficulty} s${it.seed} ${it.check}: ${it.detail}") }
    result.selfTest.filter { !it.second }.forEach { println("   - NO DETECTADA: ${it.first}") }
    if (report != null) {
        File(report).writeText(ContentAudit.renderMarkdown(result), Charsets.UTF_8)
        println("Informe escrito en $report")
    }
    if (!result.passed) System.exit(1)
}
