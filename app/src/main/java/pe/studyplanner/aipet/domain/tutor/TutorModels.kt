package pe.studyplanner.aipet.domain.tutor

/**
 * Áreas del temario del examen de admisión, en el orden de estudio de la lista de temas: primero las de matemática
 * (cuyos ejercicios se generan y calculan, [math] = `true`) y después las de los bancos de preguntas verificadas,
 * en el orden del temario oficial.
 */
enum class Area(val label: String, val math: Boolean = true) {
    ARITMETICA("Aritmética"),
    ALGEBRA("Álgebra"),
    GEOMETRIA("Geometría"),
    TRIGONOMETRIA("Trigonometría"),
    RAZONAMIENTO("Razonamiento matemático"),
    FISICA("Física", math = false),
    QUIMICA("Química", math = false),
    ECOLOGIA("Ecología y medio ambiente", math = false),
    BIOLOGIA("Biología", math = false),
    ZOOLOGIA("Zoología", math = false),
    CIVICA("Educación cívica", math = false),
    GEOGRAFIA("Geografía del Perú y del mundo", math = false),
    HISTORIA("Historia del Perú", math = false),
    ECONOMIA("Economía", math = false),
    COMUNICACION("Comunicación", math = false),
    RAZ_VERBAL("Razonamiento verbal", math = false),
}

/**
 * Fuente que respalda un dato de una pregunta del banco. Se muestra como «Fuente: título».
 *
 * @property accessed fecha (AAAA-MM-DD) en que se consultó la fuente.
 */
data class Source(
    val title: String,
    val publisher: String = "",
    val url: String = "",
    val accessed: String = "",
) {
    init {
        require(title.isNotBlank()) { "Una fuente necesita título" }
    }
}

/**
 * Ejemplo resuelto que se muestra antes de practicar (efecto del ejemplo resuelto).
 * Se produce con el mismo generador que los ejercicios, por lo que siempre es coherente con ellos.
 *
 * @property statement enunciado.
 * @property steps pasos de la resolución, cada uno apto para ser leído en voz alta.
 * @property answer respuesta final (texto tal como aparece en las opciones).
 */
data class WorkedExample(
    val statement: String,
    val steps: List<String>,
    val answer: String,
) {
    companion object {
        /** Convierte un ejercicio generado en ejemplo resuelto. */
        fun from(exercise: Exercise): WorkedExample =
            WorkedExample(exercise.statement, exercise.solutionSteps, exercise.correctOption)
    }
}

/**
 * Un tema del temario.
 *
 * @property id identificador estable (sin espacios); se usa en el registro, el CSV y la agenda.
 * @property temario ítems del temario oficial de la UNAMBA que cubre el tema (al menos uno); ver [Temario].
 * @property explanation de 3 a 5 párrafos cortos; cada uno se puede leer con TTS por separado.
 * @property workedExample ejemplo resuelto antes de practicar; `null` en los temas de banco de preguntas, que no lo
 *   tienen (su explicación ya trae los datos y las preguntas se reservan para practicar).
 */
data class Topic(
    val id: String,
    val area: Area,
    val title: String,
    val temario: List<TemarioRef>,
    val objective: String,
    val explanation: List<String>,
    val workedExample: WorkedExample?,
) {
    init {
        require(id.isNotBlank() && id.none { it.isWhitespace() }) { "id de tema inválido: '$id'" }
        require(temario.isNotEmpty()) { "El tema $id debe citar al menos un ítem del temario oficial" }
        require(explanation.size in 3..5) { "El tema $id debe tener 3 a 5 párrafos, tiene ${explanation.size}" }
    }

    /** Línea para la interfaz: «Temario UNAMBA: Álgebra 7 · Ecuaciones lineales y cuadráticas». */
    val temarioLabel: String get() = "Temario UNAMBA: " + temario.joinToString("; ") { it.label }
}

/**
 * Ejercicio de opción múltiple (5 alternativas A–E) con apoyo pedagógico.
 *
 * @property options exactamente 5 textos distintos, sin la letra (la interfaz agrega A–E).
 * @property hints 3 pistas graduadas: conceptual, procedimental y casi-solución.
 * @property optionFeedback mensaje específico del error que revela cada alternativa incorrecta
 *   (clave = índice de la opción; la opción correcta no tiene entrada).
 * @property solutionSteps resolución completa paso a paso; el último paso nombra la respuesta.
 * @property difficulty 1 (básico), 2 (intermedio) o 3 (difícil).
 * @property sources fuentes que respaldan la respuesta (solo las preguntas de banco las traen); la interfaz las
 *   muestra únicamente cuando la respuesta ya es visible.
 */
data class Exercise(
    val id: String,
    val topicId: String,
    val statement: String,
    val options: List<String>,
    val correctIndex: Int,
    val hints: List<String>,
    val optionFeedback: Map<Int, String>,
    val solutionSteps: List<String>,
    val difficulty: Int,
    val sources: List<Source> = emptyList(),
) {
    init {
        require(options.size == OPTION_COUNT) { "Se requieren $OPTION_COUNT opciones, hay ${options.size}" }
        require(options.toSet().size == OPTION_COUNT) { "Opciones repetidas en $id: $options" }
        require(correctIndex in options.indices) { "correctIndex fuera de rango en $id" }
        require(hints.size == HINT_COUNT) { "Se requieren $HINT_COUNT pistas en $id" }
        require(difficulty in 1..3) { "Dificultad fuera de 1..3 en $id" }
        require(optionFeedback.keys == options.indices.toSet() - correctIndex) {
            "Falta o sobra retroalimentación en $id"
        }
    }

    /** Texto de la alternativa correcta. */
    val correctOption: String get() = options[correctIndex]

    /** Letra (A–E) de la alternativa [index]. */
    fun label(index: Int): String = OPTION_LABELS[index].toString()

    companion object {
        const val OPTION_COUNT = 5
        const val HINT_COUNT = 3
        private const val OPTION_LABELS = "ABCDE"
    }
}

/**
 * Generador parametrizado de ejercicios de un tema.
 * La respuesta correcta se CALCULA; los distractores salen de errores típicos.
 * Debe ser puro: el mismo (seed, difficulty) produce siempre el mismo [Exercise].
 */
interface ExerciseGenerator {
    /** Tema al que pertenecen los ejercicios. */
    val topicId: String

    /** Genera un ejercicio determinista. [difficulty] debe estar en 1..3. */
    fun generate(seed: Long, difficulty: Int): Exercise

    /**
     * Como [generate], pero un generador con pocos ejercicios posibles (el banco de preguntas) evita los enunciados
     * de [avoid] mientras quede alguno sin usar. El ejercicio devuelto se puede volver a generar con su propio id
     * ([TutorRegistry.regenerate]). Los generadores matemáticos lo ignoran: la sesión ya reintenta con otra semilla.
     */
    fun generate(seed: Long, difficulty: Int, avoid: Set<String>): Exercise = generate(seed, difficulty)
}

/** Modo en que se respondió un ejercicio: con ayuda (pistas) o sin ayuda (comprobación). */
enum class AttemptMode { GUIDED, UNAIDED }

/**
 * Registro de una respuesta del estudiante. No contiene datos personales: solo ids de tema y ejercicio.
 *
 * @property timestampMillis instante de la respuesta (reloj inyectado, milisegundos desde 1970 UTC).
 * @property exerciseId id del ejercicio; permite regenerarlo con [TutorRegistry.regenerate].
 * @property selectedIndex opción elegida (0 a 4), o [NO_ANSWER] si el estudiante marcó «No sé».
 * @property correct si la opción elegida era la correcta.
 * @property hintsShownBefore pistas ya mostradas cuando respondió (0 en modo UNAIDED).
 * @property elapsedMs milisegundos desde que se mostró el ejercicio hasta esta respuesta.
 */
data class Attempt(
    val timestampMillis: Long,
    val topicId: String,
    val exerciseId: String,
    val mode: AttemptMode,
    val selectedIndex: Int,
    val correct: Boolean,
    val hintsShownBefore: Int,
    val elapsedMs: Long,
) {
    companion object {
        /** Valor de [selectedIndex] cuando no se eligió ninguna opción. */
        const val NO_ANSWER = -1
    }
}
