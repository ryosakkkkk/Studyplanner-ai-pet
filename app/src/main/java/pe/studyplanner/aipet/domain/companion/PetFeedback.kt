package pe.studyplanner.aipet.domain.companion

import pe.studyplanner.aipet.domain.pet.PetAnimationController
import pe.studyplanner.aipet.domain.pet.PetState
import pe.studyplanner.aipet.domain.tutor.Exercise
import pe.studyplanner.aipet.domain.tutor.Phase
import pe.studyplanner.aipet.domain.tutor.PetCue
import pe.studyplanner.aipet.domain.tutor.TutorScreen
import pe.studyplanner.aipet.domain.tutor.TutorState

/** Lo que se le manda a la mascota física: una cara, un texto corto y, a veces, un sonido. */
data class PetSignal(val expression: PetExpression, val text: String, val sound: PetSound? = null)

/**
 * Traduce lo que ocurre en el tutor a la mascota. Dos destinos distintos:
 *  - el zorro del teléfono ([moodFor]), con el mapeo documentado de [PetCue];
 *  - la mascota física ([signalFor]), que solo conoce las 9 caras de su firmware.
 *
 * La boca que se mueve (`hablar` en la mascota física, `speaking` en el zorro) es solo para cuando la voz realmente
 * suena ([SpeechFace] y el estado de voz de la pantalla): ninguna señal del tutor la usa por sí sola.
 *
 * Regla pedagógica: durante la comprobación sin ayuda la mascota nunca anuncia si se acertó, ni con la cara ni con
 * el sonido. El resultado solo aparece en el resumen.
 */
object PetFeedback {

    /** Estado de ánimo del zorro de la app (el parámetro `mood` de `PetAvatar`) para una señal del tutor. */
    fun moodFor(cue: PetCue): String = PetAnimationController.fromMood(cue.petState).mood

    /**
     * Estado de ánimo del zorro mientras NO suena la voz: como [moodFor], pero sin la boca que se mueve (`speaking`),
     * que la pantalla pone solo mientras la lectura está sonando.
     */
    fun silentMoodFor(cue: PetCue): String = moodFor(cue).let { if (it == PetState.SPEAK.mood) PetState.IDLE.mood else it }

    /** Cara de la mascota física para una señal del tutor. Nunca devuelve `hablar`: esa cara es solo para la voz. */
    fun expressionFor(cue: PetCue, useAlternative: Boolean = false): PetExpression = when (cue) {
        PetCue.EXPLAIN -> PetExpression.IDLE
        PetCue.THINK -> PetExpression.PENSAR
        PetCue.CORRECT -> PetExpression.FELIZ
        PetCue.ENCOURAGE -> if (useAlternative) PetExpression.SORPRENDIDO else PetExpression.ALERTA
        PetCue.CELEBRATE -> PetExpression.CELEBRAR
        PetCue.REST -> PetExpression.DORMIR
    }

    /** Cara, texto y sonido que corresponden al estado actual del tutor. */
    fun signalFor(state: TutorState): PetSignal {
        val progress = state.progress
        return when (state.screen) {
            TutorScreen.EXPLANATION, TutorScreen.WORKED_EXAMPLE ->
                PetSignal(expressionFor(state.cue), "Vamos a aprender")
            TutorScreen.QUESTION -> {
                val label = if (state.phase == Phase.UNAIDED_CHECK) "Sin ayuda" else "Ejercicio"
                PetSignal(expressionFor(state.cue), "$label ${progress.current} de ${progress.total}")
            }
            TutorScreen.HINT -> {
                val shown = state.exercise?.hintsShown?.size ?: 1
                PetSignal(PetExpression.PENSAR, "Pista $shown de ${Exercise.HINT_COUNT}", PetSound.PISTA)
            }
            TutorScreen.WRONG_ANSWER -> PetSignal(expressionFor(state.cue), "Intenta otra vez", PetSound.ERROR)
            // Tras un error (la mascota anima) suena el aviso de error; si la pidió, solo cambia la cara.
            TutorScreen.SOLUTION -> PetSignal(expressionFor(state.cue), "Mira la solución",
                if (state.cue == PetCue.ENCOURAGE) PetSound.ERROR else null)
            TutorScreen.CORRECT -> PetSignal(expressionFor(state.cue), "¡Correcto!", PetSound.CORRECTO)
            TutorScreen.UNAIDED_INTRO -> PetSignal(expressionFor(state.cue), "Ahora, sin ayuda")
            TutorScreen.SUMMARY -> summarySignal(state.cue)
        }
    }

    private fun summarySignal(cue: PetCue): PetSignal = when (cue) {
        PetCue.CELEBRATE -> PetSignal(PetExpression.CELEBRAR, "¡Lo lograste!", PetSound.CELEBRACION)
        PetCue.REST -> PetSignal(PetExpression.DORMIR, "")
        else -> PetSignal(expressionFor(cue, useAlternative = true), "¡Buen avance!")
    }
}
