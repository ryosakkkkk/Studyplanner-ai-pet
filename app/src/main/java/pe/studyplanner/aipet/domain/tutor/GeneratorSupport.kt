package pe.studyplanner.aipet.domain.tutor

/**
 * Generador pseudoaleatorio determinista (SplitMix64). Se implementa a mano para que la misma
 * semilla produzca los mismos ejercicios en cualquier versión de Kotlin/Android.
 */
class SeededRandom(seed: Long) {
    private var state = seed

    /** Siguiente valor de 64 bits de la secuencia. */
    fun long(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    /** Entero en [0, bound). */
    fun below(bound: Int): Int {
        require(bound > 0) { "bound debe ser positivo" }
        return ((long() ushr 1) % bound).toInt()
    }

    /** Entero en el rango cerrado [from, to]. */
    fun int(from: Int, to: Int): Int {
        require(from <= to) { "Rango vacío $from..$to" }
        return from + below(to - from + 1)
    }

    /** Entero del rango cerrado distinto de [excluded]. */
    fun intExcept(from: Int, to: Int, vararg excluded: Int): Int {
        check((from..to).any { it !in excluded }) { "Todo el rango está excluido" }
        while (true) {
            val v = int(from, to)
            if (v !in excluded) return v
        }
    }

    /** Elemento al azar de una lista no vacía. */
    fun <T> pick(items: List<T>): T = items[below(items.size)]

    /** Copia mezclada (Fisher-Yates). */
    fun <T> shuffled(items: List<T>): List<T> {
        val out = items.toMutableList()
        for (i in out.lastIndex downTo 1) {
            val j = below(i + 1)
            val tmp = out[i]
            out[i] = out[j]
            out[j] = tmp
        }
        return out
    }

    /** Verdadero con probabilidad 1/2. */
    fun coin(): Boolean = below(2) == 0

    /** Entero del rango cerrado [from, to] que no está en [excluded] (versión con colección). */
    fun intNotIn(from: Int, to: Int, excluded: Collection<Int>): Int = intExcept(from, to, *excluded.toIntArray())

    /**
     * Muestreo por rechazo: repite [attempt] hasta que devuelva un valor no nulo.
     * Falla (en vez de colgarse) si tras [tries] intentos no hay candidato válido.
     */
    fun <T : Any> search(tries: Int = SEARCH_TRIES, attempt: SeededRandom.() -> T?): T {
        repeat(tries) { attempt()?.let { return it } }
        error("Sin candidato válido tras $tries intentos")
    }

    private companion object {
        const val SEARCH_TRIES = 2000
        const val GOLDEN_GAMMA = -7046029254386353131L // 0x9E3779B97F4A7C15
        const val MIX_1 = -4658895280553007687L // 0xBF58476D1CE4E5B9
        const val MIX_2 = -7723592293110705685L // 0x94D049BB133111EB
    }
}

/** Retroalimentación que se usa cuando no hay un error típico disponible para completar 4 distractores. */
const val GENERIC_FEEDBACK =
    "Ese resultado sale de un error de cálculo. Rehaz las operaciones paso a paso y comprueba tu respuesta."

/** Alternativa incorrecta con el mensaje del error que la produce. */
class Wrong(val text: String, val feedback: String)

/** Borrador de un ejercicio antes de ordenar y mezclar las opciones. */
class Draft(
    val statement: String,
    val correct: String,
    val wrong: List<Wrong>,
    val hints: List<String>,
    val steps: List<String>,
    val fillers: List<Wrong> = emptyList(),
)

/**
 * Acumulador de distractores numéricos que descarta valores absurdos.
 * Si [preferIntegers] es verdadero, los distractores fraccionarios se guardan aparte ([secondary]) y solo se
 * usan cuando faltan distractores enteros: cuando la respuesta es entera, una opción fraccionaria delata
 * que es incorrecta y no discrimina.
 */
class WrongPool(
    private val format: (Rational) -> String = Fmt::rational,
    private val valid: (Rational) -> Boolean = { it.isSane() },
    private val preferIntegers: Boolean = true,
) {
    /** Distractores preferidos. */
    val items = mutableListOf<Wrong>()

    /** Distractores válidos pero menos deseables (fraccionarios). */
    val secondary = mutableListOf<Wrong>()

    /** Agrega el distractor [value] si es un valor presentable. */
    fun add(value: Rational, feedback: String) {
        if (!valid(value)) return
        val wrong = Wrong(format(value), feedback)
        if (preferIntegers && !value.isInteger) secondary += wrong else items += wrong
    }

    fun add(value: Int, feedback: String) = add(rat(value), feedback)

    /** Distractor ya formateado. */
    fun addText(text: String, feedback: String) {
        items += Wrong(text, feedback)
    }

    /** Distractor ya formateado de menor preferencia: solo se usa si faltan distractores principales. */
    fun addSecondaryText(text: String, feedback: String) {
        secondary += Wrong(text, feedback)
    }

    /** Valores cercanos al correcto, para completar cuando faltan distractores. */
    fun fillersAround(correct: Rational): List<Wrong> =
        listOf(1, -1, 2, -2, 3, -3, 5, -5, 10, -10)
            .map { correct + it }
            .filter(valid)
            .map { Wrong(format(it), GENERIC_FEEDBACK) }

    /** Respaldo ordenado: primero los distractores con error típico poco deseables, luego los genéricos. */
    fun fallbacks(correct: Rational): List<Wrong> = secondary + fillersAround(correct)
}

/** Base común: valida la dificultad, siembra el azar y ensambla las opciones. */
abstract class BaseGenerator(final override val topicId: String) : ExerciseGenerator {

    /** Construye el borrador con los datos y errores típicos de este tema. */
    protected abstract fun draft(rng: SeededRandom, difficulty: Int): Draft

    final override fun generate(seed: Long, difficulty: Int): Exercise {
        require(difficulty in 1..3) { "difficulty debe estar en 1..3" }
        val rng = SeededRandom(seed * 4 + difficulty)
        return assemble(rng, draft(rng, difficulty), seed, difficulty)
    }

    private fun assemble(rng: SeededRandom, d: Draft, seed: Long, difficulty: Int): Exercise {
        val chosen = pickDistractors(rng, d)
        val entries = rng.shuffled(chosen.map { it.text to it.feedback } + (d.correct to null))
        val correctIndex = entries.indexOfFirst { it.second == null }
        val feedback = entries.withIndex()
            .filter { it.index != correctIndex }
            .associate { it.index to checkNotNull(it.value.second) }
        return Exercise(
            id = "$topicId-d$difficulty-s$seed",
            topicId = topicId,
            statement = d.statement,
            options = entries.map { it.first },
            correctIndex = correctIndex,
            hints = d.hints,
            optionFeedback = feedback,
            solutionSteps = d.steps,
            difficulty = difficulty,
        )
    }

    private fun pickDistractors(rng: SeededRandom, d: Draft): List<Wrong> {
        val seen = mutableSetOf(d.correct)
        fun fresh(list: List<Wrong>) = list.filter { seen.add(it.text) }
        val main = rng.shuffled(fresh(d.wrong)).take(WRONG_COUNT)
        val missing = WRONG_COUNT - main.size
        val extra = if (missing > 0) fresh(d.fillers).take(missing) else emptyList()
        val all = main + extra
        check(all.size == WRONG_COUNT) { "Faltan distractores en $topicId (${all.size}) para '${d.statement}'" }
        return all
    }

    private companion object {
        const val WRONG_COUNT = Exercise.OPTION_COUNT - 1
    }
}
