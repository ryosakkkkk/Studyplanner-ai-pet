package pe.studyplanner.aipet.domain.tutor

import java.time.LocalDate

/** Nivel de dominio de un tema, calculado solo a partir de los intentos registrados. */
enum class MasteryStatus(val label: String) {
    /** Sin intentos registrados. */
    NO_INICIADO("No iniciado"),

    /** Solo hay práctica guiada, o todavía no hay evidencia suficiente sin ayuda. */
    EN_PRACTICA("En práctica"),

    /** Ya hubo comprobación sin ayuda y no se acertó todo: el estudiante avanza con pistas, no solo. */
    SOLO_CON_AYUDA("Solo con ayuda"),

    /** Los últimos [MasteryPolicy.WINDOW] intentos sin ayuda fueron correctos. */
    DOMINIO_SIN_AYUDA("Dominio sin ayuda"),
}

/**
 * Sugerencia de repaso. Es solo una propuesta: la app decide si la agenda y cuándo.
 *
 * @property dayOffset días desde hoy en que conviene repasar (1 = mañana).
 * @property reason motivo en español, listo para mostrar al estudiante.
 * @property relearn `true` si conviene volver a la explicación y a la práctica guiada (no solo repasar).
 */
data class ReviewProposal(
    val topicId: String,
    val dayOffset: Int,
    val reason: String,
    val relearn: Boolean = false,
) {
    init {
        require(dayOffset >= 1) { "dayOffset debe ser al menos 1" }
    }

    /** Fecha sugerida a partir de [today]. */
    fun dueOn(today: LocalDate): LocalDate = today.plusDays(dayOffset.toLong())
}

/**
 * Conteos por tema que alimentan el estado de dominio.
 *
 * @property recentUnaided resultados (más antiguo primero) de la ventana de intentos sin ayuda que decide el estado.
 */
data class TopicMastery(
    val topicId: String,
    val status: MasteryStatus,
    val guidedAttempts: Int,
    val guidedCorrect: Int,
    val unaidedAttempts: Int,
    val unaidedCorrect: Int,
    val recentUnaided: List<Boolean>,
)

/**
 * Política de dominio y repaso espaciado. Todo es determinista y solo depende de la lista de intentos.
 *
 * Regla de estado (por tema):
 * 1. Sin intentos: NO_INICIADO.
 * 2. Los últimos [WINDOW] intentos sin ayuda, todos correctos: DOMINIO_SIN_AYUDA.
 * 3. Hay algún intento sin ayuda y el resultado reciente no es perfecto: SOLO_CON_AYUDA.
 * 4. En cualquier otro caso (solo práctica guiada, o un único acierto sin ayuda): EN_PRACTICA.
 *
 * Regla de repaso tras una comprobación sin ayuda (la sesión usa 2 ejercicios):
 * - todas correctas: repasar en [DAYS_AFTER_MASTERY] días;
 * - algunas correctas: repasar mañana;
 * - ninguna correcta: volver a aprender mañana con práctica guiada.
 *
 * Límites conocidos: los intervalos son fijos (3 y 1 días), no una agenda que se expande con cada acierto, y la
 * ventana puede mezclar intentos de sesiones distintas porque el registro no guarda un identificador de sesión.
 */
object MasteryPolicy {
    /** Tamaño de la ventana de intentos sin ayuda que decide el dominio. */
    const val WINDOW = 2

    /** Días de espera tras acertar toda la comprobación sin ayuda. */
    const val DAYS_AFTER_MASTERY = 3

    /** Días de espera tras una comprobación con fallos. */
    const val DAYS_AFTER_MISS = 1

    /** Estado de dominio de [topicId] según [attempts] (se ignoran los intentos de otros temas). */
    fun status(topicId: String, attempts: List<Attempt>): MasteryStatus = summarize(topicId, attempts).status

    /** Estado y conteos de [topicId]. Los intentos se ordenan por fecha antes de decidir. */
    fun summarize(topicId: String, attempts: List<Attempt>): TopicMastery {
        val own = attempts.filter { it.topicId == topicId }.sortedBy { it.timestampMillis }
        val guided = own.filter { it.mode == AttemptMode.GUIDED }
        val unaided = own.filter { it.mode == AttemptMode.UNAIDED }
        val recent = unaided.takeLast(WINDOW).map { it.correct }
        return TopicMastery(
            topicId = topicId,
            status = decide(own.isEmpty(), recent),
            guidedAttempts = guided.size,
            guidedCorrect = guided.count { it.correct },
            unaidedAttempts = unaided.size,
            unaidedCorrect = unaided.count { it.correct },
            recentUnaided = recent,
        )
    }

    private fun decide(noAttempts: Boolean, recentUnaided: List<Boolean>): MasteryStatus = when {
        noAttempts -> MasteryStatus.NO_INICIADO
        recentUnaided.size >= WINDOW && recentUnaided.all { it } -> MasteryStatus.DOMINIO_SIN_AYUDA
        recentUnaided.isNotEmpty() && !recentUnaided.all { it } -> MasteryStatus.SOLO_CON_AYUDA
        else -> MasteryStatus.EN_PRACTICA
    }

    /**
     * Propuesta de repaso a partir del resultado de la comprobación sin ayuda.
     * Devuelve `null` si no hubo ejercicios sin ayuda ([unaidedTotal] = 0).
     */
    fun proposeReview(topicId: String, unaidedCorrect: Int, unaidedTotal: Int): ReviewProposal? {
        require(unaidedCorrect in 0..unaidedTotal) { "Aciertos fuera de rango: $unaidedCorrect de $unaidedTotal" }
        if (unaidedTotal == 0) return null
        return when (unaidedCorrect) {
            unaidedTotal -> ReviewProposal(
                topicId, DAYS_AFTER_MASTERY,
                "Acertaste todo sin ayuda. Repasar en $DAYS_AFTER_MASTERY días ayuda a que lo recuerdes en el examen.",
            )
            0 -> ReviewProposal(
                topicId, DAYS_AFTER_MISS,
                "Sin ayuda todavía cuesta. Mañana vuelve a ver la explicación y practica con pistas.",
                relearn = true,
            )
            else -> ReviewProposal(
                topicId, DAYS_AFTER_MISS,
                "Acertaste $unaidedCorrect de $unaidedTotal sin ayuda. Repasa mañana lo que falló para afirmarlo.",
            )
        }
    }

    /** Propuesta de repaso para una lista de intentos sin ayuda de una sola comprobación. */
    fun proposeReview(topicId: String, checkAttempts: List<Attempt>): ReviewProposal? {
        val unaided = checkAttempts.filter { it.mode == AttemptMode.UNAIDED && it.topicId == topicId }
        return proposeReview(topicId, unaided.count { it.correct }, unaided.size)
    }
}
