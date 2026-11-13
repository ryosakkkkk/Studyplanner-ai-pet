package pe.studyplanner.aipet.domain.tutor

import java.io.File

/** Dónde están los bancos reales: `app/src/main/assets/tutor/` (la prueba los lee tal como se empaquetan en el APK). */
internal object BankFiles {
    val NAMES = listOf("banco_preguntas_v1.json", "banco_preguntas_v1b.json", "banco_preguntas_v2.json")

    private val CANDIDATES: List<String> = listOfNotNull(
        System.getProperty("bank.dir"),
        "src/main/assets/tutor",
        "app/src/main/assets/tutor",
        "../app/src/main/assets/tutor",
    )

    fun text(name: String): String {
        val file = CANDIDATES.map { File(it, name) }.firstOrNull { it.isFile }
            ?: error("No encontré $name en ${CANDIDATES.map { File(it).absolutePath }}")
        return file.readText(Charsets.UTF_8)
    }

    val banks: List<QuestionBank> by lazy { NAMES.map { QuestionBankParser.parse(text(it)) } }

    /** Matemática más los bancos reales (la misma composición que instala la app al arrancar). */
    val catalog: TutorCatalog by lazy { banks.fold(TutorCatalog.MATH_ONLY) { c, b -> c.plus(b) } }

    val bankTopics: List<BankTopic> get() = banks.flatMap { it.topics }
}

internal fun bankSession(topicId: String, seed: Long = 11L, config: SessionConfig = SessionConfig(seed = seed)): TutorSession =
    TutorSession.forTopic(topicId, config, FakeClock(), BankFiles.catalog)

internal fun bankExerciseOf(state: TutorState): Exercise {
    val view = checkNotNull(state.exercise) { "no hay ejercicio en pantalla" }
    return checkNotNull(BankFiles.catalog.regenerate(view.id)) { "id no regenerable: ${view.id}" }
}

/** Una sesión completa de banco con todo correcto; devuelve todos los estados que vio la interfaz. */
internal fun TutorSession.playBankPerfect(): List<TutorState> {
    val seen = mutableListOf(state)
    repeat(60) {
        val st = state
        if (st.phase == Phase.SUMMARY) return seen
        seen += when {
            TutorAction.ANSWER in st.actions -> answer(bankExerciseOf(st).correctIndex)
            TutorAction.NEXT in st.actions -> next()
            else -> error("sesión atascada en ${st.screen}")
        }
    }
    error("la sesión no terminó")
}
