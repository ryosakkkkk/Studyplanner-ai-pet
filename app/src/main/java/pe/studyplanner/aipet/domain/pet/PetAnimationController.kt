package pe.studyplanner.aipet.domain.pet

import java.util.Locale

/** States describe intent; an unrigged model only receives whole-body motion. */
enum class PetState(val label: String, val mood: String) {
    IDLE("En reposo", "idle"), GREET("Saludar", "greet"), LISTEN("Escuchar", "listening"),
    THINK("Pensar", "thinking"), SPEAK("Hablar", "speaking"), STUDY("Estudiar", "studying"),
    HAPPY("Feliz", "happy"), CELEBRATE("Celebrar", "celebrate"), SURPRISED("Sorpresa", "surprised"),
    ALERT("Alerta", "alert"), REST("Descansar", "rest"), SLEEP("Dormir", "sleeping"), FLOAT("Flotar", "floating")
}
object PetAnimationController {
    fun fromVoice(phase: String, busy: Boolean = false): PetState = when {
        phase == "LISTENING" -> PetState.LISTEN
        phase == "SPEAKING" -> PetState.SPEAK
        busy || phase == "TRANSCRIBING" -> PetState.THINK
        phase == "ERROR" -> PetState.ALERT
        else -> PetState.IDLE
    }
    fun fromMood(mood: String): PetState = when(mood.lowercase(Locale.ROOT)) {
        "listen", "listening", "escuchar" -> PetState.LISTEN
        "speak", "speaking", "hablar" -> PetState.SPEAK
        "think", "thinking", "pensar" -> PetState.THINK
        "study", "studying", "estudiar" -> PetState.STUDY
        "happy", "feliz" -> PetState.HAPPY
        "sleep", "sleeping", "dormir" -> PetState.SLEEP
        "alert", "alerta" -> PetState.ALERT
        "float", "floating", "flotar" -> PetState.FLOAT
        "greet", "saludar" -> PetState.GREET
        "celebrate", "celebrar" -> PetState.CELEBRATE
        "surprised", "sorprendido" -> PetState.SURPRISED
        "rest", "descansar" -> PetState.REST
        else -> PetState.IDLE
    }
}
