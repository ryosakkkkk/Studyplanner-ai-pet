package pe.studyplanner.aipet.ui.tutor

import pe.studyplanner.aipet.domain.tutor.Phase
import pe.studyplanner.aipet.domain.tutor.TutorAction
import pe.studyplanner.aipet.domain.tutor.TutorScreen
import pe.studyplanner.aipet.domain.tutor.TutorState
import pe.studyplanner.aipet.domain.tutor.TutorTexts

/**
 * Decisiones de presentación que no dependen de Compose: qué texto, qué etiqueta y qué controles se muestran. Viven
 * aparte para poder probarlas en la JVM, sobre todo la regla de que la comprobación sin ayuda no muestra pistas ni
 * avisos de acierto.
 */
internal object TutorPresentation {

    /** Etiqueta de la fase para el encabezado y para los lectores de pantalla. */
    fun phaseLabel(state: TutorState): String {
        val p = state.progress
        return when (state.phase) {
            Phase.LEARN -> "Aprender · paso ${p.current} de ${p.total}"
            Phase.GUIDED_PRACTICE -> "Práctica guiada · ejercicio ${p.current} de ${p.total}"
            Phase.UNAIDED_CHECK ->
                if (p.current <= 0) "Comprobación sin ayuda" else "Comprobación sin ayuda · ${p.current} de ${p.total}"
            Phase.SUMMARY -> "Resumen"
        }
    }

    /** Avance de la fase actual, de 0 a 1. */
    fun phaseFraction(state: TutorState): Float {
        val p = state.progress
        if (p.total <= 0) return 0f
        val done = when (state.phase) {
            Phase.LEARN -> p.current
            Phase.GUIDED_PRACTICE ->
                p.current - 1 + if (state.screen == TutorScreen.CORRECT || state.screen == TutorScreen.SOLUTION) 1 else 0
            Phase.UNAIDED_CHECK -> (p.current - 1).coerceAtLeast(0)
            Phase.SUMMARY -> p.total
        }
        return (done.toFloat() / p.total).coerceIn(0f, 1f)
    }

    /**
     * Mensaje del tutor para la tarjeta principal. En una respuesta incorrecta el motor agrega la pista al final del
     * mensaje; como la pantalla ya la dibuja en su propia tarjeta, aquí se quita para no repetirla. En una pista
     * pedida el mensaje es la pista misma, así que no queda texto.
     */
    fun message(state: TutorState): String {
        val exercise = state.exercise
        return when (state.screen) {
            TutorScreen.HINT -> ""
            TutorScreen.WRONG_ANSWER -> {
                val hints = exercise?.hintsShown.orEmpty()
                val last = hints.lastOrNull()
                if (last == null) state.text
                else state.text.removeSuffix(TutorTexts.hint(hints.size, last)).trim()
            }
            else -> state.text
        }
    }

    /** Pistas que se dibujan como tarjetas: solo mientras el ejercicio sigue abierto en la práctica guiada. */
    fun visibleHints(state: TutorState): List<String> {
        if (state.phase != Phase.GUIDED_PRACTICE) return emptyList()
        if (state.screen != TutorScreen.HINT && state.screen != TutorScreen.WRONG_ANSWER) return emptyList()
        return state.exercise?.hintsShown.orEmpty()
    }

    /** Las alternativas se pueden tocar solo cuando el motor espera una respuesta. */
    fun canAnswer(state: TutorState): Boolean = TutorAction.ANSWER in state.actions

    /** Cuántas pistas faltan por pedir (0 fuera de la práctica guiada). */
    fun hintsRemaining(state: TutorState): Int =
        if (state.phase == Phase.GUIDED_PRACTICE) state.exercise?.hintsRemaining ?: 0 else 0

    /** Texto del botón para avanzar según la pantalla. */
    fun nextLabel(state: TutorState): String = when (state.screen) {
        TutorScreen.WORKED_EXAMPLE -> "Empezar a practicar"
        TutorScreen.UNAIDED_INTRO -> "Empezar la comprobación"
        TutorScreen.CORRECT ->
            if (state.progress.current >= state.progress.total) "Ir a la comprobación sin ayuda" else "Siguiente ejercicio"
        TutorScreen.SOLUTION -> "Seguir sin repetir"
        else -> "Continuar"
    }
}
