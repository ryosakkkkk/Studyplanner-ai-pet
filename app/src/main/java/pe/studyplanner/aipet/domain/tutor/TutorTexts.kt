package pe.studyplanner.aipet.domain.tutor

/**
 * Mensajes del tutor en español (Perú), tuteo cercano y claro para estudiantes de 16 a 19 años.
 * Todo texto que ve o escucha el estudiante sale de aquí, para poder revisarlo en un solo lugar.
 */
internal object TutorTexts {

    private val WRONG_OPENERS = listOf("Todavía no.", "Sigamos pensando.", "Otra vez, con calma.")

    fun workedExample(example: WorkedExample): String =
        "Mira cómo se resuelve un ejemplo:\n${example.statement}\n\n" + numbered(example.steps)

    fun workedExampleSpeech(example: WorkedExample): String {
        val steps = example.steps.mapIndexed { i, s -> "Paso ${i + 1}. $s" }
        return Speech.forTts("Mira cómo se resuelve un ejemplo. ${example.statement}\n" + steps.joinToString("\n"))
    }

    fun guidedQuestion(number: Int, total: Int): String =
        "Ejercicio $number de $total. Intenta resolverlo tú. Si te atascas, puedes pedir una pista."

    fun retryQuestion(number: Int, total: Int): String =
        "Probemos con uno parecido (ejercicio $number de $total). Ahora aplica lo que viste en la solución."

    fun unaidedQuestion(number: Int, total: Int): String =
        "Comprobación $number de $total, sin pistas. Elige la alternativa que creas correcta."

    fun unaidedIntro(total: Int): String =
        "Ahora viene la comprobación: $total ejercicios sin pistas ni avisos. Responde lo mejor que puedas. " +
            "Al final verás cómo te fue y revisaremos cada uno. Si no sabes uno, marca «No sé»."

    fun hint(number: Int, hint: String): String = "Pista $number de ${Exercise.HINT_COUNT}: $hint"

    fun wrongWithHint(wrongCount: Int, feedback: String, hintNumber: Int, hint: String): String =
        "${opener(wrongCount)} $feedback\n\n" + hint(hintNumber, hint)

    fun wrongThenSolution(wrongCount: Int, feedback: String, steps: List<String>): String =
        "${opener(wrongCount)} $feedback\n\nNo pasa nada. Veamos la solución paso a paso:\n" + numbered(steps)

    fun solutionOnRequest(steps: List<String>): String =
        "Veamos la solución paso a paso:\n" + numbered(steps)

    /** Solución de un ejercicio: paso a paso (matemática) o la respuesta con su explicación y fuentes (banco). */
    fun solutionOnRequest(exercise: Exercise): String =
        if (exercise.sources.isEmpty()) solutionOnRequest(exercise.solutionSteps)
        else "Veamos la respuesta y por qué:\n" + bankAnswer(exercise)

    fun wrongThenSolution(wrongCount: Int, feedback: String, exercise: Exercise): String =
        if (exercise.sources.isEmpty()) wrongThenSolution(wrongCount, feedback, exercise.solutionSteps)
        else "${opener(wrongCount)} $feedback\n\nNo pasa nada. Veamos la respuesta y por qué:\n" + bankAnswer(exercise)

    /** «Fuente: título», una línea por fuente. */
    fun sourcesLine(sources: List<Source>): String = sources.joinToString("\n") { "Fuente: ${it.title}" }

    private fun bankAnswer(exercise: Exercise): String =
        "La respuesta correcta es ${exercise.label(exercise.correctIndex)}) ${exercise.correctOption}.\n" +
            numbered(exercise.solutionSteps) + "\n\n" + sourcesLine(exercise.sources)

    fun correct(exercise: Exercise, hintsShown: Int, wrongCount: Int): String {
        val answer = "La respuesta es ${exercise.label(exercise.correctIndex)}) ${exercise.correctOption}."
        val praise = when {
            hintsShown == 0 && wrongCount == 0 -> "¡Correcto, a la primera!"
            else -> "¡Correcto! Lo lograste con ayuda."
        }
        val explanation = if (exercise.sources.isEmpty()) "" else "\n\n" + numbered(exercise.solutionSteps) + "\n\n" + sourcesLine(exercise.sources)
        return "$praise $answer$explanation"
    }

    fun summary(correct: Int, total: Int, mastery: MasteryStatus, review: ReviewProposal?): String {
        val result = when (correct) {
            total -> "¡Excelente! Resolviste $correct de $total ejercicios sin ayuda."
            0 -> "Esta vez no salió ningún ejercicio sin ayuda, y está bien: así se aprende."
            else -> "Resolviste $correct de $total ejercicios sin ayuda. Vas por buen camino."
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
