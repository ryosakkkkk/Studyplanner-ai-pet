package pe.studyplanner.aipet.domain.tutor

/** Reloj de pruebas: solo avanza cuando la prueba lo ordena. */
class FakeClock(var now: Long = START) : TutorClock {
    override fun nowMillis(): Long = now

    fun advance(ms: Long) {
        now += ms
    }

    companion object {
        /** 2025-10-27T21:20:00Z; el valor exacto no importa, solo que sea fijo. */
        const val START = 1_761_600_000_000L
    }
}

/** Ejercicio que muestra el estado, regenerado desde su id (la interfaz no recibe la respuesta correcta). */
internal fun exerciseOf(state: TutorState): Exercise {
    val view = checkNotNull(state.exercise) { "no hay ejercicio en pantalla" }
    return checkNotNull(TutorRegistry.regenerate(view.id)) { "id no regenerable: ${view.id}" }
}

/** Índice de la alternativa correcta del ejercicio en pantalla. */
internal fun correctOf(state: TutorState): Int = exerciseOf(state).correctIndex

/** Primera alternativa incorrecta que todavía no fue descartada. */
internal fun wrongOf(state: TutorState): Int {
    val exercise = exerciseOf(state)
    val eliminated = checkNotNull(state.exercise).eliminated
    return exercise.options.indices.first { it != exercise.correctIndex && it !in eliminated }
}

internal fun newSession(
    topicId: String = "ecuaciones-lineales",
    config: SessionConfig = SessionConfig(seed = 11L),
    clock: TutorClock = FakeClock(),
): TutorSession = TutorSession.forTopic(topicId, config, clock)

/** Salta la explicación y deja la sesión en el primer ejercicio guiado. */
internal fun TutorSession.toGuided(): TutorState {
    skipLearn()
    return state
}

/** Resuelve bien, a la primera, todos los ejercicios guiados; termina en la introducción de la comprobación. */
internal fun TutorSession.finishGuidedCorrectly(): TutorState {
    skipLearn()
    repeat(config.guidedCount) {
        answer(correctOf(state))
        next()
    }
    return state
}

/** Responde la comprobación sin ayuda: true = correcta, false = una incorrecta, null = «No sé». */
internal fun TutorSession.runUnaided(picks: List<Boolean?>): TutorState {
    next()
    for (pick in picks) {
        when (pick) {
            true -> answer(correctOf(state))
            false -> answer(wrongOf(state))
            null -> dontKnow()
        }
    }
    return state
}

/** Una sesión completa con todo correcto; devuelve todos los estados que vio la interfaz. */
internal fun TutorSession.playPerfect(): List<TutorState> {
    val seen = mutableListOf(state)
    repeat(60) {
        val st = state
        if (st.phase == Phase.SUMMARY) return seen
        val next = when {
            TutorAction.ANSWER in st.actions -> answer(correctOf(st))
            TutorAction.NEXT in st.actions -> next()
            else -> error("sesión atascada en ${st.screen}")
        }
        seen += next
    }
    error("la sesión no terminó")
}
