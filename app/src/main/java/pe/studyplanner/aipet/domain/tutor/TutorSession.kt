package pe.studyplanner.aipet.domain.tutor

/**
 * Sesión de tutoría de un tema: máquina de estados determinista, sin Android ni reloj propio.
 *
 * Flujo: LEARN -> GUIDED_PRACTICE -> UNAIDED_CHECK -> SUMMARY. La interfaz lee [state], dibuja `text`/`exercise`,
 * habla `speech`, anima a la mascota con `cue` y llama al método que corresponde a una de las `actions`:
 * [start], [next], [back], [skipLearn], [answer], [requestHint], [showSolution], [retry] y [dontKnow].
 * Una acción no válida en ese momento se ignora y devuelve el estado actual sin cambios.
 *
 * Reglas de la práctica (práctica de recuperación con respuesta inmediata):
 * - El estudiante responde primero. No se muestra ninguna pista salvo que la pida ([requestHint], hasta 3).
 * - Una respuesta incorrecta muestra enseguida por qué esa alternativa no es (retroalimentación de ESA alternativa)
 *   y la solución paso a paso, como ejemplo resuelto: el ejercicio queda como no resuelto y se ofrece [retry] con
 *   uno parecido (misma dificultad, otros datos) para aplicar lo que acaba de ver.
 * - La solución se puede pedir en cualquier momento sin responder ([showSolution]).
 *
 * Reglas de la comprobación sin ayuda: sin pistas, sin retroalimentación y sin saber si acertó hasta el
 * resumen. «No sé» ([dontKnow]) cuenta como incorrecta.
 *
 * Cada respuesta se anota en [attempts]. Las semillas de los ejercicios salen de [SessionConfig.seed], son
 * siempre mayores que 0 (la 0 es del ejemplo resuelto) y no repiten enunciados dentro de la sesión.
 *
 * Esta clase no es segura para varios hilos y no se guarda en disco: la app persiste [attempts] (ver [AttemptLog]).
 */
class TutorSession(
    val topic: Topic,
    private val generator: ExerciseGenerator,
    val config: SessionConfig = SessionConfig(),
    private val clock: TutorClock = TutorClock.SYSTEM,
) {
    init {
        require(generator.topicId == topic.id) { "El generador ${generator.topicId} no es del tema ${topic.id}" }
    }

    private class Open(val exercise: Exercise, val shownAt: Long) {
        var hintsShown = 0
        var wrongCount = 0
        var solutionRevealed = false
        var solved = false
        val eliminated = mutableSetOf<Int>()
    }

    private class UnaidedResult(val exercise: Exercise, val selected: Int)

    private val log = mutableListOf<Attempt>()
    private val unaided = mutableListOf<UnaidedResult>()
    private val usedStatements = mutableSetOf<String>()
    // Objetivo + párrafos de la explicación + (si el tema lo tiene) el ejemplo resuelto.
    private val learnPages = topic.explanation.size + 1 + if (topic.workedExample != null) 1 else 0

    private var phase = Phase.LEARN
    private var screen = TutorScreen.EXPLANATION
    private var text = ""
    private var speechText: String? = null
    private var cue = PetCue.EXPLAIN
    private var learnPage = 0
    private var guidedIndex = 0
    private var guidedSolved = 0
    private var unaidedIndex = 0
    private var variant = 0
    private var open: Open? = null
    private var summary: SessionSummary? = null

    init {
        topic.workedExample?.let { usedStatements += it.statement }
        showLearnPage()
    }

    /**
     * Intentos registrados hasta ahora, en orden. Es una copia: no cambia después. Para guardarlos en disco use
     * [AttemptLog.encode]. Ojo: durante la comprobación sin ayuda cada intento ya trae `correct`; la interfaz no debe
     * mostrarlo hasta la fase SUMMARY (para eso sirve [TutorState.summary]).
     */
    val attempts: List<Attempt> get() = log.toList()

    /** Estado actual para dibujar. Es una función pura de lo ocurrido hasta ahora. */
    val state: TutorState
        get() = TutorState(
            phase = phase,
            screen = screen,
            title = title(),
            text = text,
            speech = speechText ?: toSpeech(text),
            cue = cue,
            actions = actions(),
            exercise = exerciseView(),
            progress = progress(),
            summary = summary,
        )

    // ---- acciones públicas ----

    /**
     * Estado inicial de la sesión (primera página de la explicación). Es idempotente: llamarla otra vez, o después
     * de otras acciones, solo devuelve el estado actual. Existe para que la interfaz tenga un punto de entrada
     * explícito; la sesión ya nace lista en la fase LEARN.
     */
    fun start(): TutorState = state

    /** Avanza: página siguiente, ejercicio siguiente o fase siguiente. */
    fun next(): TutorState {
        when (screen) {
            TutorScreen.EXPLANATION, TutorScreen.WORKED_EXAMPLE -> nextLearnPage()
            TutorScreen.CORRECT, TutorScreen.SOLUTION -> advanceGuided()
            TutorScreen.UNAIDED_INTRO -> openUnaided()
            else -> Unit
        }
        return state
    }

    /** Vuelve a la página anterior de la explicación. */
    fun back(): TutorState {
        if (phase == Phase.LEARN && learnPage > 0) {
            learnPage--
            showLearnPage()
        }
        return state
    }

    /** Salta la explicación (si [SessionConfig.allowSkipLearn]) y va directo a practicar. */
    fun skipLearn(): TutorState {
        if (phase == Phase.LEARN && config.allowSkipLearn) startGuided()
        return state
    }

    /**
     * Va directo a la comprobación sin ayuda (botón «Comprobar» de la pantalla Hoy). Solo antes de que empiece: si
     * la comprobación ya está en curso o terminó, no cambia nada.
     */
    fun goToCheck(): TutorState {
        if (phase == Phase.LEARN || phase == Phase.GUIDED_PRACTICE) startUnaided()
        return state
    }

    /** Responde con la alternativa [index] (0 a 4 = A a E). Se ignora si no hay un ejercicio esperando respuesta. */
    fun answer(index: Int): TutorState {
        val current = open
        if (current != null && index in 0 until Exercise.OPTION_COUNT && acceptsAnswer()) {
            if (phase == Phase.GUIDED_PRACTICE) answerGuided(current, index) else answerUnaided(current, index)
        }
        return state
    }

    /** Revela la siguiente pista. Solo en práctica guiada y mientras queden pistas. */
    fun requestHint(): TutorState {
        val current = open
        if (current != null && canAskForHelp(current) && current.hintsShown < Exercise.HINT_COUNT) {
            val hint = current.exercise.hints[current.hintsShown]
            current.hintsShown++
            show(TutorScreen.HINT, TutorTexts.hint(current.hintsShown, hint), PetCue.EXPLAIN)
        }
        return state
    }

    /** Muestra la solución paso a paso. En la práctica guiada, en cualquier momento antes de resolver el ejercicio. */
    fun showSolution(): TutorState {
        val current = open
        if (current != null && canAskForHelp(current)) {
            reveal(current, TutorTexts.solutionOnRequest(current.exercise))
        }
        return state
    }

    /** Tras ver la solución, ofrece un ejercicio parecido (misma dificultad, otra semilla). */
    fun retry(): TutorState {
        if (screen == TutorScreen.SOLUTION && phase == Phase.GUIDED_PRACTICE) {
            variant++
            openGuided(isRetry = true)
        }
        return state
    }

    /** Marca «No sé» en la comprobación sin ayuda: se registra como incorrecta. */
    fun dontKnow(): TutorState {
        val current = open
        if (current != null && phase == Phase.UNAIDED_CHECK && screen == TutorScreen.QUESTION) {
            record(current, Attempt.NO_ANSWER, correct = false)
            unaided += UnaidedResult(current.exercise, Attempt.NO_ANSWER)
            advanceUnaided()
        }
        return state
    }

    // ---- fase LEARN ----

    private fun nextLearnPage() {
        if (learnPage < learnPages - 1) {
            learnPage++
            showLearnPage()
        } else {
            startGuided()
        }
    }

    private fun showLearnPage() {
        val paragraphs = topic.explanation
        when {
            learnPage == 0 -> show(TutorScreen.EXPLANATION, "Hoy vas a lograr esto: ${topic.objective}", PetCue.EXPLAIN)
            learnPage <= paragraphs.size -> show(TutorScreen.EXPLANATION, paragraphs[learnPage - 1], PetCue.EXPLAIN)
            else -> showWorkedExample()
        }
    }

    private fun showWorkedExample() {
        val example = topic.workedExample ?: return
        show(TutorScreen.WORKED_EXAMPLE, TutorTexts.workedExample(example), PetCue.EXPLAIN, TutorTexts.workedExampleSpeech(example))
    }

    // ---- fase GUIDED_PRACTICE ----

    private fun startGuided() {
        phase = Phase.GUIDED_PRACTICE
        guidedIndex = 0
        variant = 0
        openGuided(isRetry = false)
    }

    private fun openGuided(isRetry: Boolean) {
        val exercise = pickExercise(TAG_GUIDED, guidedIndex, variant, config.guidedDifficulties[guidedIndex])
        open = Open(exercise, clock.nowMillis())
        val number = guidedIndex + 1
        val prompt = if (isRetry) TutorTexts.retryQuestion(number, config.guidedCount) else TutorTexts.guidedQuestion(number, config.guidedCount)
        show(TutorScreen.QUESTION, prompt, PetCue.THINK, TutorTexts.questionSpeech(prompt, exercise))
    }

    private fun advanceGuided() {
        guidedIndex++
        variant = 0
        if (guidedIndex >= config.guidedCount) startUnaided() else openGuided(isRetry = false)
    }

    private fun acceptsAnswer() = screen == TutorScreen.QUESTION || isGuidedFeedbackScreen()


    private fun isGuidedFeedbackScreen() =
        phase == Phase.GUIDED_PRACTICE && (screen == TutorScreen.HINT || screen == TutorScreen.WRONG_ANSWER)

    private fun canAskForHelp(current: Open): Boolean =
        phase == Phase.GUIDED_PRACTICE && !current.solved && !current.solutionRevealed &&
            (screen == TutorScreen.QUESTION || isGuidedFeedbackScreen())

    private fun answerGuided(current: Open, index: Int) {
        if (index in current.eliminated || current.solved || current.solutionRevealed) return
        val correct = index == current.exercise.correctIndex
        record(current, index, correct)
        if (correct) {
            current.solved = true
            guidedSolved++
            show(TutorScreen.CORRECT, TutorTexts.correct(current.exercise, current.hintsShown, current.wrongCount), PetCue.CORRECT)
        } else {
            current.eliminated += index
            current.wrongCount++
            handleWrongGuided(current, index)
        }
    }

    /** Error: se explica esa alternativa y se muestra la solución completa en el acto (ejemplo resuelto). */
    private fun handleWrongGuided(current: Open, index: Int) {
        val feedback = current.exercise.optionFeedback.getValue(index)
        reveal(current, TutorTexts.wrongThenSolution(current.wrongCount, feedback, current.exercise), PetCue.ENCOURAGE)
    }

    /** Solución a la vista. Tras un error la mascota anima ([PetCue.ENCOURAGE]); pedida, explica ([PetCue.EXPLAIN]). */
    private fun reveal(current: Open, message: String, cue: PetCue = PetCue.EXPLAIN) {
        current.solutionRevealed = true
        show(TutorScreen.SOLUTION, message, cue)
    }

    // ---- fase UNAIDED_CHECK ----

    private fun startUnaided() {
        phase = Phase.UNAIDED_CHECK
        unaidedIndex = 0
        open = null
        show(TutorScreen.UNAIDED_INTRO, TutorTexts.unaidedIntro(config.unaidedCount), PetCue.EXPLAIN)
    }

    private fun openUnaided() {
        val exercise = pickExercise(TAG_UNAIDED, unaidedIndex, 0, config.unaidedDifficulties[unaidedIndex])
        open = Open(exercise, clock.nowMillis())
        val prompt = TutorTexts.unaidedQuestion(unaidedIndex + 1, config.unaidedCount)
        show(TutorScreen.QUESTION, prompt, PetCue.THINK, TutorTexts.questionSpeech(prompt, exercise))
    }

    private fun answerUnaided(current: Open, index: Int) {
        record(current, index, index == current.exercise.correctIndex)
        unaided += UnaidedResult(current.exercise, index)
        advanceUnaided()
    }

    private fun advanceUnaided() {
        unaidedIndex++
        if (unaidedIndex >= config.unaidedCount) finish() else openUnaided()
    }

    // ---- fase SUMMARY ----

    private fun finish() {
        phase = Phase.SUMMARY
        open = null
        val items = unaided.map(::reviewItem)
        val correct = items.count { it.correct }
        val mastery = MasteryPolicy.status(topic.id, config.history + log)
        val review = MasteryPolicy.proposeReview(topic.id, correct, items.size)
        summary = SessionSummary(topic.id, guidedSolved, config.guidedCount, correct, items.size, mastery, review, items)
        val pet = when (correct) {
            items.size -> PetCue.CELEBRATE
            0 -> PetCue.REST
            else -> PetCue.ENCOURAGE
        }
        show(TutorScreen.SUMMARY, TutorTexts.summary(correct, items.size, mastery, review), pet)
    }

    private fun reviewItem(r: UnaidedResult): UnaidedReviewItem {
        val e = r.exercise
        return UnaidedReviewItem(
            exerciseId = e.id,
            statement = e.statement,
            options = e.options,
            selectedIndex = r.selected,
            correctIndex = e.correctIndex,
            correct = r.selected == e.correctIndex,
            feedback = TutorTexts.optionFeedbackFor(e, r.selected),
            solutionSteps = e.solutionSteps,
            sources = e.sources,
        )
    }

    // ---- utilidades ----

    private fun record(current: Open, selected: Int, correct: Boolean) {
        val now = clock.nowMillis()
        val mode = if (phase == Phase.GUIDED_PRACTICE) AttemptMode.GUIDED else AttemptMode.UNAIDED
        val hints = if (mode == AttemptMode.GUIDED) current.hintsShown else 0
        log += Attempt(now, topic.id, current.exercise.id, mode, selected, correct, hints, (now - current.shownAt).coerceAtLeast(0))
    }

    private fun show(newScreen: TutorScreen, message: String, newCue: PetCue, speech: String? = null) {
        screen = newScreen
        text = message
        cue = newCue
        speechText = speech
    }

    /** Elige un ejercicio determinista que no repita enunciado dentro de la sesión. */
    private fun pickExercise(tag: Long, slot: Int, variant: Int, difficulty: Int): Exercise {
        var salt = 0L
        while (true) {
            val exercise = generator.generate(seedFor(tag, slot.toLong(), variant.toLong(), salt), difficulty, usedStatements)
            if (usedStatements.add(exercise.statement) || salt >= MAX_SALT) return exercise
            salt++
        }
    }

    private fun seedFor(vararg parts: Long): Long {
        var h = config.seed
        for (p in parts) h = SeededRandom(h * MIX_FACTOR + p).long()
        return (h ushr SEED_SHIFT) + 1
    }

    private fun actions(): Set<TutorAction> {
        val result = mutableSetOf<TutorAction>()
        val current = open
        when (screen) {
            TutorScreen.EXPLANATION, TutorScreen.WORKED_EXAMPLE -> {
                result += TutorAction.NEXT
                if (learnPage > 0) result += TutorAction.BACK
                if (config.allowSkipLearn) result += TutorAction.SKIP_LEARN
            }
            TutorScreen.QUESTION, TutorScreen.HINT, TutorScreen.WRONG_ANSWER -> questionActions(current, result)
            TutorScreen.SOLUTION -> result += setOf(TutorAction.RETRY, TutorAction.NEXT)
            TutorScreen.CORRECT, TutorScreen.UNAIDED_INTRO -> result += TutorAction.NEXT
            TutorScreen.SUMMARY -> Unit
        }
        return result
    }

    private fun questionActions(current: Open?, result: MutableSet<TutorAction>) {
        result += TutorAction.ANSWER
        if (phase == Phase.UNAIDED_CHECK) {
            result += TutorAction.DONT_KNOW
        } else if (current != null) {
            if (current.hintsShown < Exercise.HINT_COUNT) result += TutorAction.REQUEST_HINT
            result += TutorAction.SHOW_SOLUTION
        }
    }

    private fun exerciseView(): ExerciseView? {
        val current = open ?: return null
        val e = current.exercise
        val guided = phase == Phase.GUIDED_PRACTICE
        val revealed = screen == TutorScreen.CORRECT || screen == TutorScreen.SOLUTION
        return ExerciseView(
            id = e.id,
            statement = e.statement,
            options = e.options,
            eliminated = current.eliminated.toSet(),
            hintsShown = if (guided) e.hints.take(current.hintsShown) else emptyList(),
            hintsRemaining = if (guided) Exercise.HINT_COUNT - current.hintsShown else 0,
            solutionSteps = if (revealed) e.solutionSteps else emptyList(),
            correctIndex = if (revealed) e.correctIndex else null,
            sources = if (revealed) e.sources else emptyList(),
        )
    }

    private fun progress(): Progress = when (phase) {
        Phase.LEARN -> Progress(phase, learnPage + 1, learnPages)
        Phase.GUIDED_PRACTICE -> Progress(phase, guidedIndex + 1, config.guidedCount)
        Phase.UNAIDED_CHECK ->
            Progress(phase, if (screen == TutorScreen.UNAIDED_INTRO) 0 else unaidedIndex + 1, config.unaidedCount)
        Phase.SUMMARY -> Progress(phase, config.unaidedCount, config.unaidedCount)
    }

    private fun title(): String = when (screen) {
        TutorScreen.EXPLANATION -> topic.title
        TutorScreen.WORKED_EXAMPLE -> "Ejemplo resuelto"
        TutorScreen.UNAIDED_INTRO -> "Comprobación sin ayuda"
        TutorScreen.SUMMARY -> "Resumen: ${topic.title}"
        else -> if (phase == Phase.GUIDED_PRACTICE) "Práctica guiada" else "Comprobación sin ayuda"
    }

    private fun toSpeech(message: String): String = Speech.forTts(message.replace(NUMBERING, "Paso $1. "))

    companion object {
        private const val TAG_GUIDED = 1L
        private const val TAG_UNAIDED = 2L
        private const val MAX_SALT = 40L
        private const val MIX_FACTOR = 1_000_003L
        private const val SEED_SHIFT = 34
        private val NUMBERING = Regex("""(?m)^(\d+)\) """)

        /**
         * Crea una sesión para un tema del [catalog] (por defecto el de la app: matemática más los bancos de preguntas);
         * lanza [IllegalArgumentException] si no existe.
         */
        fun forTopic(
            topicId: String,
            config: SessionConfig = SessionConfig(),
            clock: TutorClock = TutorClock.SYSTEM,
            catalog: TutorCatalog = TutorContent.catalog,
        ): TutorSession {
            val generator = requireNotNull(catalog.generator(topicId)) { "Tema desconocido: '$topicId'" }
            return TutorSession(catalog.requireTopic(topicId), generator, config, clock)
        }
    }
}
