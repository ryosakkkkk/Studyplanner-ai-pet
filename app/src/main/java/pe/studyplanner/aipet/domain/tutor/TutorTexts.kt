package pe.studyplanner.aipet.domain.tutor

/**
 * Mensajes del tutor en español (Perú), tuteo cercano y claro para estudiantes de 16 a 19 años.
 * Todo texto que ve o escucha el estudiante sale de aquí, para poder revisarlo en un solo lugar.
 */
internal object TutorTexts {

    private val WRONG_OPENERS = listOf("No es esa.", "Todavía no.", "Casi.")

    fun workedExample(example: WorkedExample): String =
        "Así se resuelve:\n${example.statement}\n\n" + numbered(example.steps)

    fun workedExampleSpeech(example: WorkedExample): String {
        val steps = example.steps.mapIndexed { i, s -> "Paso ${i + 1}. $s" }
        return Speech.forTts("Así se resuelve. ${example.statement}\n" + steps.joinToString("\n"))
    }

    fun guidedQuestion(number: Int, total: Int): String = "Ejercicio $number de $total."

    fun retryQuestion(number: Int, total: Int): String = "Ejercicio $number de $total: uno parecido. Aplica lo que viste."

    fun unaidedQuestion(number: Int, total: Int): String = "Comprobación $number de $total, sin ayuda."

    fun unaidedIntro(total: Int): String =
        "Ahora $total ejercicios sin ayuda, como en el examen. Al final ves cómo te fue. Si no sabes uno, marca «No sé»."

    fun hint(number: Int, hint: String): String = "Pista $number de ${Exercise.HINT_COUNT}: $hint"

    fun wrongThenSolution(wrongCount: Int, feedback: String, steps: List<String>): String =
        "${opener(wrongCount)} $feedback\n\nAsí se resuelve:\n" + numbered(steps)

    fun solutionOnRequest(steps: List<String>): String =
        "Así se resuelve:\n" + numbered(steps)

    /** Solución de un ejercicio: paso a paso (matemática) o la respuesta con su explicación y fuentes (banco). */
    fun solutionOnRequest(exercise: Exercise): String =
        if (exercise.sources.isEmpty()) solutionOnRequest(exercise.solutionSteps)
        else "La respuesta y por qué:\n" + bankAnswer(exercise)

    /** Error en la práctica: por qué esa alternativa no es y, enseguida, la solución completa. */
    fun wrongThenSolution(wrongCount: Int, feedback: String, exercise: Exercise): String =
        if (exercise.sources.isEmpty()) wrongThenSolution(wrongCount, feedback, exercise.solutionSteps)
        else "${opener(wrongCount)} $feedback\n\n" + bankAnswer(exercise)

    /** «Fuente: título», una línea por fuente. */
    fun sourcesLine(sources: List<Source>): String = sources.joinToString("\n") { "Fuente: ${it.title}" }

    private fun bankAnswer(exercise: Exercise): String =
        "La respuesta correcta es ${exercise.label(exercise.correctIndex)}) ${exercise.correctOption}.\n" +
            numbered(exercise.solutionSteps) + "\n\n" + sourcesLine(exercise.sources)

    fun correct(exercise: Exercise, hintsShown: Int, wrongCount: Int): String {
        val answer = "La respuesta es ${exercise.label(exercise.correctIndex)}) ${exercise.correctOption}."
        val praise = when {
            hintsShown == 0 && wrongCount == 0 -> "¡Correcto, a la primera!"
            else -> "¡Correcto! Con ayuda."
        }
        val explanation = if (exercise.sources.isEmpty()) "" else "\n\n" + numbered(exercise.solutionSteps) + "\n\n" + sourcesLine(exercise.sources)
        return "$praise $answer$explanation"
    }

    fun summary(correct: Int, total: Int, mastery: MasteryStatus, review: ReviewProposal?): String {
        val result = when (correct) {
            total -> "¡Excelente! $correct de $total sin ayuda."
            0 -> "Esta vez 0 de $total sin ayuda. Así se aprende: repasa la solución."
            else -> "$correct de $total sin ayuda. Vas bien."
        }
        val advice = review?.reason ?: ""
        return "$result Estado del tema: ${mastery.label}. $advice".trim()
    }

    fun optionFeedbackFor(exercise: Exercise, selected: Int): String = when {
        selected == Attempt.NO_ANSWER -> "Marcaste «No sé». Mira la solución y vuelve a intentarlo después."
        selected == exercise.correctIndex -> "Correcta."
        else -> exercise.optionFeedback.getValue(selected)
    }

    fun questionSpeech(prompt: String, exercise: Exercise): String {
        val options = exercise.options.mapIndexed { i, o -> "Opción ${exercise.label(i)}. $o." }
        return Speech.forTts("$prompt\n${exercise.statement}\n" + options.joinToString("\n"))
    }

    private fun opener(wrongCount: Int): String = WRONG_OPENERS[(wrongCount - 1).coerceAtLeast(0) % WRONG_OPENERS.size]

    /** Pasos numerados «1) …»; un solo paso (la explicación de una pregunta de banco) va sin número. */
    private fun numbered(steps: List<String>): String =
        if (steps.size == 1) steps[0] else steps.mapIndexed { i, s -> "${i + 1}) $s" }.joinToString("\n")
}
