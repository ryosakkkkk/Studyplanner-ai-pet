package pe.studyplanner.aipet.domain.tutor

import pe.studyplanner.aipet.domain.json.JsonException
import pe.studyplanner.aipet.domain.json.MiniJson

/** El banco de preguntas no se pudo leer o no cumple el esquema. El mensaje (en español) dice dónde está el problema. */
class QuestionBankException(message: String) : IllegalArgumentException(message)

/**
 * Una pregunta verificada del banco (ver `apk/banco/VERIFICACION_BANCO*.md`): enunciado, 5 alternativas en el orden
 * del archivo, pistas graduadas, retroalimentación de cada alternativa incorrecta, explicación y fuentes.
 */
data class BankQuestion(
    val id: String,
    val statement: String,
    val options: List<String>,
    val correctIndex: Int,
    val hints: List<String>,
    val optionFeedback: Map<Int, String>,
    val explanation: String,
    val difficulty: Int,
    val sources: List<Source>,
) {
    init {
        require(id.isNotBlank()) { "pregunta sin id" }
        require(statement.isNotBlank()) { "enunciado vacío" }
        require(options.size == Exercise.OPTION_COUNT && options.all { it.isNotBlank() }) { "debe tener ${Exercise.OPTION_COUNT} alternativas no vacías" }
        require(options.map { it.trim().lowercase() }.toSet().size == Exercise.OPTION_COUNT) { "alternativas repetidas" }
        require(correctIndex in options.indices) { "correctIndex fuera de rango" }
        require(hints.size == Exercise.HINT_COUNT && hints.all { it.isNotBlank() }) { "debe tener ${Exercise.HINT_COUNT} pistas no vacías" }
        require(optionFeedback.keys == options.indices.toSet() - correctIndex && optionFeedback.values.all { it.isNotBlank() }) {
            "optionFeedback debe cubrir exactamente las alternativas incorrectas"
        }
        require(explanation.isNotBlank()) { "explicación vacía" }
        require(difficulty in 1..3) { "dificultad fuera de 1..3" }
        require(sources.isNotEmpty()) { "toda pregunta lleva al menos una fuente" }
    }
}

/** Un tema del banco: explicación para aprender y sus preguntas. */
data class BankTopic(
    val id: String,
    val area: Area,
    val title: String,
    val temario: TemarioRef,
    val objective: String,
    val explanation: List<String>,
    val questions: List<BankQuestion>,
) {
    init {
        require(!area.math) { "$id: el banco solo trae temas fuera de matemática" }
        require(questions.map { it.id }.toSet().size == questions.size) { "$id: ids de pregunta repetidos" }
        require(questions.map { it.statement }.toSet().size == questions.size) { "$id: enunciados repetidos" }
        require(questions.size >= MIN_QUESTIONS) { "$id: necesita al menos $MIN_QUESTIONS preguntas para una sesión sin repetir" }
    }

    /** El tema tal como lo ve la lista de temas y la fase «Aprender» (sin ejemplo resuelto). */
    fun toTopic(): Topic = Topic(id, area, title, listOf(temario), objective, explanation, workedExample = null)

    companion object {
        /** Una sesión usa 3 ejercicios guiados y 2 sin ayuda: hacen falta al menos 5 preguntas distintas. */
        const val MIN_QUESTIONS = 5
    }
}

/** Banco de preguntas ya leído y validado. */
data class QuestionBank(val version: Int, val topics: List<BankTopic>) {
    init {
        require(topics.map { it.id }.toSet().size == topics.size) { "ids de tema repetidos en el banco" }
    }
}

/** Lee el JSON del banco (`assets/tutor/banco_preguntas_*.json`) con el lector mínimo de [MiniJson]. */
object QuestionBankParser {

    /** Versión del esquema que entiende esta app. */
    const val SCHEMA_VERSION = 1

    fun parse(json: String): QuestionBank {
        val root = try {
            MiniJson.parse(json)
        } catch (e: JsonException) {
            throw QuestionBankException(e.message ?: "JSON inválido")
        }
        val doc = obj(root, "raíz")
        val version = int(doc, "version", "raíz")
        if (version != SCHEMA_VERSION) throw QuestionBankException("Versión de banco $version no admitida (se esperaba $SCHEMA_VERSION)")
        val topics = list(doc, "topics", "raíz").mapIndexed { i, t -> topic(obj(t, "topics[$i]")) }
        return try {
            QuestionBank(version, topics)
        } catch (e: IllegalArgumentException) {
            throw QuestionBankException(e.message ?: "banco inválido")
        }
    }

    private fun topic(t: Map<String, Any?>): BankTopic {
        val id = str(t, "id", "tema")
        val ctx = "tema $id"
        val code = str(t, "area", ctx)
        val area = Area.entries.firstOrNull { it.name == code && !it.math } ?: throw QuestionBankException("$ctx: área desconocida '$code'")
        val ref = obj(t["temario"], "$ctx/temario")
        val temario = try {
            TemarioRef(area, int(ref, "item", "$ctx/temario"), officialTitle = str(ref, "title", "$ctx/temario"))
        } catch (e: IllegalArgumentException) {
            throw QuestionBankException("$ctx/temario: ${e.message}")
        }
        val questions = list(t, "questions", ctx).mapIndexed { i, q -> question(obj(q, "$ctx/questions[$i]"), ctx) }
        return try {
            BankTopic(
                id = id,
                area = area,
                title = str(t, "title", ctx),
                temario = temario,
                objective = str(t, "objective", ctx),
                explanation = strings(t, "explanation", ctx),
                questions = questions,
            ).also { it.toTopic() } // valida de una vez los 3 a 5 párrafos de la explicación
        } catch (e: IllegalArgumentException) {
            if (e is QuestionBankException) throw e
            throw QuestionBankException("$ctx: ${e.message}")
        }
    }

    private fun question(q: Map<String, Any?>, topicCtx: String): BankQuestion {
        val id = str(q, "id", topicCtx)
        val ctx = "$topicCtx/$id"
        val feedback = obj(q["optionFeedback"], "$ctx/optionFeedback").entries.associate { (k, v) ->
            (k.toIntOrNull() ?: throw QuestionBankException("$ctx: clave de optionFeedback '$k' no es un índice")) to
                (v as? String ?: throw QuestionBankException("$ctx: optionFeedback[$k] no es texto"))
        }
        val sources = list(q, "sources", ctx).mapIndexed { i, s ->
            val m = obj(s, "$ctx/sources[$i]")
            try {
                Source(str(m, "title", "$ctx/sources[$i]"), opt(m, "publisher"), opt(m, "url"), opt(m, "accessed"))
            } catch (e: IllegalArgumentException) {
                if (e is QuestionBankException) throw e
                throw QuestionBankException("$ctx/sources[$i]: ${e.message}")
            }
        }
        return try {
            BankQuestion(
                id = id,
                statement = str(q, "statement", ctx),
                options = strings(q, "options", ctx),
                correctIndex = int(q, "correctIndex", ctx),
                hints = strings(q, "hints", ctx),
                optionFeedback = feedback,
                explanation = str(q, "explanation", ctx),
                difficulty = int(q, "difficulty", ctx),
                sources = sources,
            )
        } catch (e: IllegalArgumentException) {
            if (e is QuestionBankException) throw e
            throw QuestionBankException("$ctx: ${e.message}")
        }
    }

    // ---- acceso tipado al árbol JSON ----

    @Suppress("UNCHECKED_CAST")
    private fun obj(value: Any?, ctx: String): Map<String, Any?> =
        value as? Map<String, Any?> ?: throw QuestionBankException("$ctx: se esperaba un objeto")

    private fun list(m: Map<String, Any?>, key: String, ctx: String): List<Any?> =
        m[key] as? List<*> ?: throw QuestionBankException("$ctx: falta la lista '$key'")

    private fun str(m: Map<String, Any?>, key: String, ctx: String): String =
        (m[key] as? String)?.takeIf { it.isNotBlank() } ?: throw QuestionBankException("$ctx: falta el texto '$key'")

    private fun opt(m: Map<String, Any?>, key: String): String = m[key] as? String ?: ""

    private fun int(m: Map<String, Any?>, key: String, ctx: String): Int {
        val v = m[key]
        return if (v is Long && v in Int.MIN_VALUE..Int.MAX_VALUE) v.toInt() else throw QuestionBankException("$ctx: falta el entero '$key'")
    }

    private fun strings(m: Map<String, Any?>, key: String, ctx: String): List<String> =
        list(m, key, ctx).mapIndexed { i, v -> v as? String ?: throw QuestionBankException("$ctx: '$key[$i]' no es texto") }
}

/**
 * Generador de un tema de banco. No inventa nada: elige una pregunta verificada según la semilla y baraja sus
 * alternativas (también según la semilla), reasignando `correctIndex` y las claves de `optionFeedback`.
 *
 * Determinismo y repetición: el id del ejercicio es `<tema>-d<dificultad>-s<semilla>` y la pregunta es siempre
 * `semilla mod n` (n = cantidad de preguntas). [generate] con `avoid` busca, entre las preguntas sin usar, la de
 * dificultad más cercana a la pedida y devuelve un ejercicio cuya semilla efectiva apunta a ella, de modo que el id
 * basta para volver a generar exactamente el mismo ejercicio ([generate] de dos argumentos).
 */
class BankExerciseGenerator(private val topic: BankTopic) : ExerciseGenerator {

    override val topicId: String get() = topic.id

    private val questions: List<BankQuestion> get() = topic.questions
    private val salt: Long = topic.id.fold(7L) { h, c -> h * 31 + c.code }

    override fun generate(seed: Long, difficulty: Int): Exercise {
        require(difficulty in 1..3) { "difficulty debe estar en 1..3" }
        return build(Math.floorMod(seed, questions.size.toLong()).toInt(), seed)
    }

    override fun generate(seed: Long, difficulty: Int, avoid: Set<String>): Exercise {
        require(difficulty in 1..3) { "difficulty debe estar en 1..3" }
        val unused = questions.indices.filter { questions[it].statement !in avoid }
        val pool = unused.ifEmpty { questions.indices.toList() }
        val best = pool.minOf { Math.abs(questions[it].difficulty - difficulty) }
        val group = pool.filter { Math.abs(questions[it].difficulty - difficulty) == best }
        val chosen = group[SeededRandom(seed * PICK_MIX + salt).below(group.size)]
        val n = questions.size.toLong()
        return build(chosen, seed - Math.floorMod(seed, n) + chosen)
    }

    private fun build(index: Int, seed: Long): Exercise {
        val q = questions[index]
        val order = SeededRandom(seed * SHUFFLE_MIX + salt).shuffled((0 until Exercise.OPTION_COUNT).toList())
        val newCorrect = order.indexOf(q.correctIndex)
        return Exercise(
            id = "${topic.id}-d${q.difficulty}-s$seed",
            topicId = topic.id,
            statement = q.statement,
            options = order.map { q.options[it] },
            correctIndex = newCorrect,
            hints = q.hints,
            optionFeedback = order.withIndex().filter { it.index != newCorrect }.associate { it.index to q.optionFeedback.getValue(it.value) },
            solutionSteps = listOf(q.explanation),
            difficulty = q.difficulty,
            sources = q.sources,
        )
    }

    private companion object {
        const val PICK_MIX = 1_000_003L
        const val SHUFFLE_MIX = 7_919L
    }
}

/**
 * Los temas disponibles para estudiar: los de matemática de [TutorRegistry] (ejercicios calculados) y los de los
 * bancos de preguntas. Una instancia es inmutable; la app usa la de [TutorContent].
 */
class TutorCatalog(val topics: List<Topic>, val generators: List<ExerciseGenerator>) {

    init {
        require(topics.map { it.id }.toSet().size == topics.size) { "Hay ids de tema repetidos" }
        require(topics.all { t -> generators.count { it.topicId == t.id } == 1 }) { "Cada tema necesita exactamente un generador" }
        require(generators.size == topics.size) { "Sobran generadores" }
    }

    fun topic(id: String): Topic? = topics.firstOrNull { it.id == id }

    fun requireTopic(id: String): Topic = requireNotNull(topic(id)) { "Tema desconocido: '$id'" }

    fun generator(id: String): ExerciseGenerator? = generators.firstOrNull { it.topicId == id }

    fun byArea(area: Area): List<Topic> = topics.filter { it.area == area }

    /** Áreas que tienen al menos un tema, en el orden de [Area]: primero matemática, luego el resto del temario. */
    val areas: List<Area> get() = Area.entries.filter { a -> topics.any { it.area == a } }

    fun generate(topicId: String, seed: Long, difficulty: Int): Exercise =
        requireNotNull(generator(topicId)) { "Tema desconocido: '$topicId'" }.generate(seed, difficulty)

    /** Vuelve a generar un ejercicio desde su id (el del CSV de intentos); `null` si el id no es de ningún tema. */
    fun regenerate(exerciseId: String): Exercise? {
        val m = ID_PATTERN.matchEntire(exerciseId) ?: return null
        val seed = m.groupValues[3].toLongOrNull() ?: return null
        val g = generator(m.groupValues[1]) ?: return null
        return g.generate(seed, m.groupValues[2].toInt())
    }

    /** Este catálogo más los temas de [bank]. Falla si un id de tema ya existe. */
    fun plus(bank: QuestionBank): TutorCatalog = TutorCatalog(
        topics + bank.topics.map { it.toTopic() },
        generators + bank.topics.map { BankExerciseGenerator(it) },
    )

    companion object {
        private val ID_PATTERN = Regex("""^(.+)-d([1-3])-s(-?\d+)$""")

        /** Solo los temas de matemática (el comportamiento de la versión 0.12). */
        val MATH_ONLY: TutorCatalog by lazy { TutorCatalog(TutorRegistry.topics, TutorRegistry.generators) }
    }
}

/**
 * El catálogo que usa la app. Empieza solo con matemática; `StudyPlannerApplication` instala los bancos de preguntas al
 * arrancar. Las pruebas de matemática no dependen de esto (usan [TutorRegistry] directamente).
 */
object TutorContent {
    @Volatile
    var catalog: TutorCatalog = TutorCatalog.MATH_ONLY
        private set

    fun install(catalog: TutorCatalog) {
        this.catalog = catalog
    }
}
