package pe.studyplanner.aipet.domain.tutor

/**
 * Títulos de los ítems del temario oficial de matemática del examen de admisión de la UNAMBA
 * («Temario de conocimientos para examen de admisión 2022», PDF actualizado en 2023). Solo se usan como lista de
 * temas: todo el contenido del tutor (explicaciones, ejercicios) es propio.
 *
 * Los ítems se numeran dentro de cada área, tal como figuran en el documento oficial.
 */
object Temario {

    /** Documento oficial al que se refieren los números de ítem. */
    const val FUENTE = "Temario de conocimientos para examen de admisión 2022, UNAMBA"

    private val ITEMS: Map<Area, List<String>> = mapOf(
        Area.ARITMETICA to listOf(
            "Lógica proposicional",
            "Teoría de conjuntos",
            "Sistema de números enteros",
            "Sistema de números racionales",
            "Sistemas de numeración",
            "Divisibilidad",
            "Números primos",
            "Máximo común divisor y mínimo común múltiplo",
            "Razones y proporciones",
            "Magnitudes",
            "Reglas de tres",
            "Regla de interés",
        ),
        Area.ALGEBRA to listOf(
            "Expresiones algebraicas",
            "Monomios y polinomios",
            "Operaciones con polinomios",
            "Factorización y fracciones algebraicas",
            "Radicación",
            "Binomio de Newton",
            "Ecuaciones lineales y cuadráticas",
            "Sistema de ecuaciones y matrices",
            "Inecuaciones lineales, cuadráticas",
            "Relaciones y funciones",
            "Logaritmos",
            "Progresiones aritméticas y geométricas",
            "Introducción al cálculo",
        ),
        Area.GEOMETRIA to listOf(
            "Elementos fundamentales de la geometría",
            "Ángulos",
            "Triángulos y líneas notables",
            "Congruencia de triángulos",
            "Polígono",
            "Cuadriláteros",
            "Circunferencia",
            "Puntos notables",
            "Proporcionalidad y semejanza de triángulos",
            "Relaciones métricas en triángulos y circunferencias",
            "Polígonos regulares",
            "Área de regiones poligonales, poligonales regulares y circulares",
            "Rectas y planos en el espacio, ángulos diedros, triedros y poliedros",
            "Prisma y cilindro",
            "Pirámide y cono",
            "Superficie y volumen de la esfera",
        ),
        Area.TRIGONOMETRIA to listOf(
            "Ángulo trigonométrico y medidas angulares",
            "Razones trigonométricas de un ángulo agudo",
            "Razones trigonométricas de ángulos en posición normal",
            "Reducción al primer cuadrante",
            "Identidades trigonométricas",
            "Funciones trigonométricas del ángulo doble",
            "Funciones trigonométricas del ángulo mitad",
            "Transformaciones trigonométricas",
            "Ecuaciones trigonométricas",
            "Resolución de triángulos oblicuángulos, ángulos verticales y horizontales",
        ),
        Area.RAZONAMIENTO to listOf(
            "Razonamiento lógico",
            "Suficiencia de datos",
            "Planteo de ecuaciones",
            "Operadores matemáticos",
            "Sucesiones, series y sumas notables",
            "Conteo de figuras plano convexas, cubos y superficies",
            "Promedios",
            "Criptograma",
            "Análisis combinatorios",
        ),
    )

    /**
     * Cuántos ítems tiene el área de matemática en el temario oficial. Solo las áreas de matemática tienen tabla
     * aquí; el ítem de un tema de banco de preguntas lleva su propio título oficial (ver [TemarioRef.officialTitle]).
     */
    fun itemCount(area: Area): Int = ITEMS.getValue(area).size

    /** Título oficial del ítem [item] (desde 1) del [area] de matemática; falla si el ítem no existe. */
    fun title(area: Area, item: Int): String {
        val titles = ITEMS.getValue(area)
        require(item in 1..titles.size) { "${area.label} no tiene el ítem $item (hay ${titles.size})" }
        return titles[item - 1]
    }

    private val LOWER_WORDS = setOf("de", "del", "la", "las", "el", "los", "y", "e", "en", "o", "u", "a", "al", "con", "por", "para", "un", "una")

    /**
     * Pasa a mayúsculas y minúsculas legibles un título oficial escrito todo en mayúsculas («ESTRUCTURA ATÓMICA» →
     * «Estructura Atómica»). Las palabras que ya traen minúsculas no se tocan, las cifras romanas se conservan
     * («SIGLO XVIII») y las palabras de enlace van en minúscula salvo al empezar.
     */
    fun displayTitle(raw: String): String {
        val words = raw.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        return words.mapIndexed { i, word ->
            val letters = word.filter { it.isLetter() }
            when {
                letters.isEmpty() || letters.any { it.isLowerCase() } -> word
                ROMAN.matches(letters) && letters.length >= 2 -> word
                i > 0 && letters.lowercase() in LOWER_WORDS -> word.lowercase()
                else -> word.lowercase().replaceFirstChar { it.uppercase() }
            }
        }.joinToString(" ")
    }

    private val ROMAN = Regex("""^M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$""")
}

/**
 * Referencia de un tema del tutor a un ítem del temario oficial.
 *
 * @property item número del ítem dentro del área (desde 1); en las áreas de matemática se valida contra [Temario].
 * @property focus parte concreta del ítem que cubre el tema, cuando el título del ítem no basta para entenderlo
 *   (por ejemplo, «tanto por ciento» dentro de «Reglas de tres»). Puede estar vacío.
 * @property officialTitle título oficial del ítem tal como figura en el temario (solo para las áreas de banco de
 *   preguntas, cuya tabla completa no se guarda en la app; obligatorio ahí y vacío en matemática).
 */
data class TemarioRef(val area: Area, val item: Int, val focus: String = "", val officialTitle: String = "") {

    init {
        if (area.math) {
            require(item in 1..Temario.itemCount(area)) { "${area.label} no tiene el ítem $item" }
        } else {
            require(item >= 1) { "El ítem del temario empieza en 1" }
            require(officialTitle.isNotBlank()) { "${area.label} $item: falta el título oficial del ítem" }
        }
    }

    /** Título oficial del ítem. */
    val title: String get() = if (area.math) Temario.title(area, item) else Temario.displayTitle(officialTitle)

    /** Texto corto para mostrar: «Álgebra 7 · Ecuaciones lineales y cuadráticas» (con el foco entre paréntesis si lo hay). */
    val label: String
        get() = "${area.label} $item · $title" + if (focus.isBlank()) "" else " ($focus)"
}
