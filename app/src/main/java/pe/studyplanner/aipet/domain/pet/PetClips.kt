package pe.studyplanner.aipet.domain.pet

import pe.studyplanner.aipet.domain.profile.AvatarStyle

/**
 * Traduce el estado de ánimo que pide la interfaz («think», «speak», «celebrate»…) a uno de los 17 clips de los
 * modelos GLB de la versión 0.17. Los clips que no son en bucle se reproducen una vez y la mascota vuelve a «idle».
 */
object PetClips {
    /** Clips incluidos en cada GLB. */
    val ALL: List<String> = listOf(
        "idle", "talk", "talk_body", "happy", "sad", "surprised", "angry", "think", "sleep",
        "run", "walk", "jump", "celebrate", "wave", "yes", "no", "dance",
    )

    /**
     * @property clip nombre del clip en el GLB.
     * @property loop si se repite mientras dure el estado.
     * @property talking si la boca debe moverse (voz sonando).
     */
    data class Choice(val clip: String, val loop: Boolean, val talking: Boolean = false)

    private val IDLE = Choice("idle", loop = true)

    fun forMood(mood: String): Choice = when (mood.trim().lowercase()) {
        "", "idle", "attentive", "progress", "read", "studying", "floating" -> IDLE
        "wave", "greet", "hello" -> Choice("wave", loop = false)
        "listen", "listening" -> Choice("yes", loop = false)
        "think", "thinking", "write", "plan" -> Choice("think", loop = true)
        "speak", "speaking", "talk", "present" -> Choice("talk_body", loop = true, talking = true)
        "celebrate", "trophy" -> Choice("celebrate", loop = false)
        "dance" -> Choice("dance", loop = false)
        "happy", "tasks", "correct" -> Choice("happy", loop = false)
        "confused", "sad", "wrong" -> Choice("sad", loop = false)
        "worried", "alert", "surprised" -> Choice("surprised", loop = false)
        "idea", "yes" -> Choice("yes", loop = false)
        "no" -> Choice("no", loop = false)
        "angry" -> Choice("angry", loop = false)
        "reminder", "notify", "jump" -> Choice("jump", loop = false)
        "rest", "sleep" -> Choice("sleep", loop = true)
        "run" -> Choice("run", loop = true)
        "walk" -> Choice("walk", loop = true)
        else -> IDLE
    }

    /** Nombre del modelo GLB de cada mascota, o null si aún no tiene modelo nuevo (el Conejo usa el motor anterior). */
    fun modelName(style: AvatarStyle): String? = when (style) {
        AvatarStyle.FOX -> "zorro"
        AvatarStyle.CAT -> "gato"
        AvatarStyle.PANDA -> "panda"
        AvatarStyle.OWL -> "buho"
        AvatarStyle.PENGUIN -> "pinguino"
        AvatarStyle.RABBIT -> "conejo"
    }
}
