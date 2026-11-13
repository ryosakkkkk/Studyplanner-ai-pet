package pe.studyplanner.aipet.domain.tutor

import pe.studyplanner.aipet.domain.json.MiniJson

/** Fases de una sesión de tutoría, en orden. */
enum class Phase {
    /** Explicación breve y ejemplo resuelto. */
    LEARN,

    /** Práctica con pistas graduadas y retroalimentación. */
    GUIDED_PRACTICE,

    /** Comprobación sin pistas ni avisos hasta el final. */
    UNAIDED_CHECK,

    /** Resumen, estado de dominio y propuesta de repaso. */
    SUMMARY,
}

/**
 * Señal para la mascota. La interfaz decide cómo animarla; este es el mapeo documentado hacia los estados
 * de la mascota de la app (`petState`), con un estado alternativo opcional.
 *
 * | Señal | Cuándo ocurre | Estado de la mascota |
 * |---|---|---|
 * | [EXPLAIN] | explicación, ejemplo, pistas, solución | hablar |
 * | [THINK] | el estudiante piensa en un ejercicio | pensar |
 * | [CORRECT] | respuesta correcta en práctica guiada | feliz |
 * | [ENCOURAGE] | respuesta incorrecta o resultado parcial | alerta (alternativa: sorprendido) |
 * | [CELEBRATE] | comprobación sin ayuda perfecta | celebrar |
 * | [REST] | ninguna correcta sin ayuda: conviene descansar y volver mañana | descansar |
 */
enum class PetCue(val petState: String, val altPetState: String? = null) {
    EXPLAIN("hablar"),
    THINK("pensar"),
    CORRECT("feliz"),
    ENCOURAGE("alerta", "sorprendido"),
    CELEBRATE("celebrar"),
    REST("descansar"),
}

/** Acciones que la interfaz puede ofrecer; [TutorState.actions] lista las válidas en cada momento. */
enum class TutorAction {
    /** Avanzar (siguiente página, siguiente ejercicio, siguiente fase). */
    NEXT,

    /** Volver a la página anterior (solo en la fase de aprendizaje). */
    BACK,

    /** Saltar la explicación e ir directo a practicar. */
    SKIP_LEARN,

    /** Responder con una alternativa (A a E): [TutorSession.answer]. */
    ANSWER,

    /** Pedir la siguiente pista (solo en práctica guiada). */
    REQUEST_HINT,

    /** Ver la solución paso a paso (solo en práctica guiada, con las 3 pistas ya mostradas). */
    SHOW_SOLUTION,

    /** Intentar un ejercicio parecido tras ver la solución. */
    RETRY,

    /** Marcar «No sé» (solo en la comprobación sin ayuda): se registra como respuesta incorrecta. */
    DONT_KNOW,
}

/** Pantalla lógica dentro de una fase; la interfaz decide cómo dibujarla. */
enum class TutorScreen {
    EXPLANATION, WORKED_EXAMPLE, QUESTION, HINT, WRONG_ANSWER, SOLUTION, CORRECT, UNAIDED_INTRO, SUMMARY,
}

/** Posición dentro de la fase actual (por ejemplo, ejercicio 2 de 3). */
data class Progress(val phase: Phase, val current: Int, val total: Int)

/**
 * Vista de un ejercicio para la interfaz. Nunca contiene la respuesta correcta mientras el ejercicio está
 * abierto: [correctIndex] solo se rellena cuando ya se acertó o se mostró la solución.
 *
 * @property eliminated alternativas ya descartadas por errores (la interfaz puede atenuarlas).
 * @property hintsShown pistas ya reveladas, en orden.
 * @property hintsRemaining cuántas pistas faltan por revelar (0 en la comprobación sin ayuda).
 * @property solutionSteps pasos de la solución, o vacío si todavía no se revelaron.
 * @property sources fuentes de la respuesta (preguntas de banco), o vacío mientras la respuesta no se haya revelado.
 */
data class ExerciseView(
    val id: String,
    val statement: String,
    val options: List<String>,
    val eliminated: Set<Int>,
    val hintsShown: List<String>,
    val hintsRemaining: Int,
    val solutionSteps: List<String>,
    val correctIndex: Int?,
    val sources: List<Source> = emptyList(),
)

/** Repaso de un ejercicio de la comprobación sin ayuda, mostrado solo en el resumen. */
data class UnaidedReviewItem(
    val exerciseId: String,
    val statement: String,
    val options: List<String>,
    val selectedIndex: Int,
    val correctIndex: Int,
    val correct: Boolean,
    val feedback: String,
    val solutionSteps: List<String>,
    val sources: List<Source> = emptyList(),
)

/**
 * Resultado de la sesión.
 *
 * @property guidedSolved ejercicios guiados resueltos (el resto terminó con la solución mostrada).
 * @property mastery estado de dominio del tema, contando el historial previo ([SessionConfig.history]).
 * @property review propuesta de repaso (solo una sugerencia), o `null` si no hubo comprobación.
 */
data class SessionSummary(
    val topicId: String,
    val guidedSolved: Int,
    val guidedTotal: Int,
    val unaidedCorrect: Int,
    val unaidedTotal: Int,
    val mastery: MasteryStatus,
    val review: ReviewProposal?,
    val unaidedReview: List<UnaidedReviewItem>,
)

/**
 * Estado completo que la interfaz dibuja. No depende de Android ni de ninguna librería de UI.
 *
 * @property text mensaje del tutor para mostrar.
 * @property speech texto equivalente listo para el sintetizador de voz (sin símbolos matemáticos).
 * @property cue señal para la mascota.
 * @property actions acciones disponibles ahora; cualquier otra se ignora.
 * @property exercise ejercicio en pantalla, o `null` fuera de los ejercicios.
 */
data class TutorState(
    val phase: Phase,
    val screen: TutorScreen,
    val title: String,
    val text: String,
    val speech: String,
    val cue: PetCue,
    val actions: Set<TutorAction>,
    val exercise: ExerciseView?,
    val progress: Progress,
    val summary: SessionSummary?,
)

/**
 * Configuración de una sesión.
 *
 * @property seed semilla de la sesión: la misma semilla produce los mismos ejercicios.
 * @property guidedDifficulties dificultad de cada ejercicio guiado (su tamaño es la cantidad, por defecto 3).
 * @property unaidedDifficulties dificultad de cada ejercicio sin ayuda (por defecto 2).
 * @property allowSkipLearn si se ofrece saltar la explicación.
 * @property history intentos anteriores del estudiante, solo para calcular el estado de dominio en el resumen.
 */
data class SessionConfig(
    val seed: Long = 1L,
    val guidedDifficulties: List<Int> = listOf(1, 2, 2),
    val unaidedDifficulties: List<Int> = listOf(2, 2),
    val allowSkipLearn: Boolean = true,
    val history: List<Attempt> = emptyList(),
) {
    init {
        require(guidedDifficulties.isNotEmpty() && unaidedDifficulties.isNotEmpty()) { "Se necesita al menos un ejercicio por fase" }
        require((guidedDifficulties + unaidedDifficulties).all { it in 1..3 }) { "Dificultad fuera de 1..3" }
    }

    /** Cantidad de ejercicios guiados. */
    val guidedCount: Int get() = guidedDifficulties.size

    /** Cantidad de ejercicios sin ayuda. */
    val unaidedCount: Int get() = unaidedDifficulties.size
}

/** Reloj inyectable: permite probar el tiempo sin esperar y no depende de ninguna API de Android. */
fun interface TutorClock {
    /** Milisegundos desde 1970 (UTC). */
    fun nowMillis(): Long

    companion object {
        /** Reloj del sistema. */
        val SYSTEM: TutorClock = TutorClock { System.currentTimeMillis() }
    }
}

/**
 * Lo que el tutor con IA necesita para conversar sobre el ejercicio abierto de la práctica guiada. Lleva la respuesta
 * y la solución aunque el estudiante todavía no las vea: es material para el tutor (para que guíe sin equivocarse) y
 * la interfaz no debe dibujarlo.
 *
 * @property answerVisible `true` si el estudiante ya acertó o ya vio la solución; entonces el tutor puede explicarla.
 */
data class TutorBrief(
    val course: String,
    val topic: String,
    val statement: String,
    val options: List<String>,
    val correctIndex: Int,
    val solutionSteps: List<String>,
    val answerVisible: Boolean,
) {
    /** JSON que lee la página del tutor (`assets/web/camino`): curso, tema, e, o, c, sol y visto. */
    fun toJson(): String = MiniJson.write(
        linkedMapOf(
            "curso" to course, "tema" to topic, "e" to statement, "o" to options, "c" to correctIndex,
            "sol" to solutionSteps, "visto" to answerVisible,
        ),
    )
}
