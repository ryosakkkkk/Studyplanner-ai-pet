package pe.studyplanner.aipet.domain.tutor

/**
 * Convierte texto con notación matemática (como "S/ 120", "2^(-3)", "30°", "3/4", "√3") en texto que un
 * sintetizador de voz en español lee sin tropiezos. La pantalla sigue mostrando el texto original.
 */
object Speech {

    private const val SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    private const val SUBSCRIPTS = "₀₁₂₃₄₅₆₇₈₉"
    private val DIGIT_WORDS = listOf("cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve")
    private val MONTHS = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )
    private val ORDINALS_F = listOf("primera", "segunda", "tercera", "cuarta", "quinta", "sexta", "séptima", "octava", "novena", "décima")
    private val ORDINALS_M = listOf("primero", "segundo", "tercero", "cuarto", "quinto", "sexto", "séptimo", "octavo", "noveno", "décimo")
    private val ORBITALS = mapOf('s' to "ese", 'p' to "pe", 'd' to "de", 'f' to "efe")
    private val ELEMENTS = mapOf(
        "H" to "hidrógeno", "He" to "helio", "Li" to "litio", "Be" to "berilio", "B" to "boro", "C" to "carbono",
        "N" to "nitrógeno", "O" to "oxígeno", "F" to "flúor", "Ne" to "neón", "Na" to "sodio", "Mg" to "magnesio",
        "Al" to "aluminio", "Si" to "silicio", "P" to "fósforo", "S" to "azufre", "Cl" to "cloro", "Ar" to "argón",
        "K" to "potasio", "Ca" to "calcio", "Fe" to "hierro", "Cu" to "cobre", "Zn" to "zinc", "Ag" to "plata",
        "Au" to "oro", "Hg" to "mercurio", "Pb" to "plomo", "U" to "uranio", "I" to "yodo", "Br" to "bromo",
    )

    private val SUPER_RUN = "[$SUPERSCRIPTS]"

    /**
     * Primera pasada: símbolos que no son de matemática escolar (cargas «Al³⁺», configuraciones «2p⁶», isótopos «²³Na»,
     * subíndices «v₀», unidades «m/s²», «N.° 26834», fechas «23-07-1987», «½», «±», «−»). Las reglas generales de
     * [RULES] corren después.
     */
    private val PRE_RULES: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        Regex("""[\u2212]""") to { _ -> "-" },
        Regex("""\s*—\s*""") to { _ -> ", " },
        Regex("""(\d)\s?–\s?(\d)""") to { m -> "${m.groupValues[1]} a ${m.groupValues[2]}" },
        Regex("""\s*–\s*""") to { _ -> ", " },
        // Isótopos: «²³Na» -> «sodio 23».
        Regex("""($SUPER_RUN+)(\p{Lu}\p{Ll}?)(?!\p{L})""") to { m ->
            val symbol = m.groupValues[2]
            " ${ELEMENTS[symbol] ?: symbol} ${plainDigits(m.groupValues[1])}"
        },
        // Configuración electrónica: «2p⁶» -> «2 pe 6».
        Regex("""(\d)([spdf])($SUPER_RUN+)""") to { m ->
            "${m.groupValues[1]} ${ORBITALS.getValue(m.groupValues[2][0])} ${plainDigits(m.groupValues[3])}"
        },
        // Cargas de iones: «Al³⁺» -> «Al tres más», «Cl⁻» -> «Cl menos».
        Regex("""(\p{L}|\))($SUPER_RUN*)([⁺⁻])""") to { m ->
            val digits = m.groupValues[2]
            val magnitude = if (digits.isEmpty()) "" else " " + digits.map { DIGIT_WORDS[SUPERSCRIPTS.indexOf(it)] }.joinToString(" ")
            "${m.groupValues[1]}$magnitude ${if (m.groupValues[3] == "⁺") "más" else "menos"}"
        },
        Regex("""(?<![\p{L}])km/s²""") to { _ -> "kilómetros por segundo al cuadrado" },
        Regex("""(?<![\p{L}])m/s²""") to { _ -> "metros por segundo al cuadrado" },
        Regex("""(?<![\p{L}])km/s(?![\p{L}])""") to { _ -> "kilómetros por segundo" },
        Regex("""(?<![\p{L}])m/s(?![\p{L}])""") to { _ -> "metros por segundo" },
        // Exponentes sueltos: «t²» -> «t al cuadrado», «10⁵» -> «10 elevado a 5».
        Regex("""($SUPER_RUN+)""") to { m ->
            when (val run = m.groupValues[1]) {
                "²" -> " al cuadrado"
                "³" -> " al cubo"
                else -> " elevado a ${plainDigits(run)}"
            }
        },
        Regex("""([₀-₉ₐₑₒₓₔₕₖₗₘₙₚₛₜ])""") to { m ->
            val c = m.groupValues[1][0]
            val d = SUBSCRIPTS.indexOf(c)
            val word = if (d >= 0) DIGIT_WORDS[d] else when (c) {
                'ₗ' -> "ele"; 'ₛ' -> "ese"; 'ₙ' -> "ene"; 'ₓ' -> "equis"; 'ₐ' -> "a"; 'ₑ' -> "e"; 'ₒ' -> "o"
                'ₕ' -> "hache"; 'ₖ' -> "ka"; 'ₘ' -> "eme"; 'ₚ' -> "pe"; 'ₜ' -> "te"; else -> "ce"
            }
            " $word "
        },
        Regex("""½""") to { _ -> " un medio " },
        Regex("""¼""") to { _ -> " un cuarto " },
        Regex("""¾""") to { _ -> " tres cuartos " },
        Regex("""\s*±\s*""") to { _ -> " más menos " },
        Regex("""…""") to { _ -> "..." },
        // Ordinales abreviados: «2.ª» -> «segunda», «1.º» -> «primero».
        Regex("""(\d{1,2})\.?ª""") to { m -> ORDINALS_F.getOrNull(m.groupValues[1].toInt() - 1) ?: "${m.groupValues[1]} ª" },
        Regex("""(\d{1,2})\.?º""") to { m -> ORDINALS_M.getOrNull(m.groupValues[1].toInt() - 1) ?: "${m.groupValues[1]} º" },
        Regex("""(?<!\p{L})N\.?\s?[°º]\s?""") to { _ -> "número " },
        Regex("""(?<!\p{L})ed\.""") to { _ -> "edición" },
        // Fechas «23-07-1987» -> «23 de julio de 1987».
        Regex("""(?<![\d-])(\d{1,2})-(\d{2})-(\d{4})(?![\d-])""") to { m ->
            val month = m.groupValues[2].toInt()
            if (month in 1..12) "${m.groupValues[1].toInt()} de ${MONTHS[month - 1]} de ${m.groupValues[3]}" else m.value
        },
        // Códigos de normas después de «número»: «042-87-AG» -> «042 guion 87 guion AG».
        Regex("""(número\s+)([\p{L}\p{N}]+(?:-[\p{L}\p{N}]+)+)""") to { m ->
            m.groupValues[1] + m.groupValues[2].replace("-", " guion ")
        },
    )

    private fun plainDigits(run: String): String = run.map { SUPERSCRIPTS.indexOf(it).let { d -> if (d >= 0) d.toString() else it.toString() } }.joinToString("")

    private val RULES: List<Pair<Regex, String>> = listOf(
        // Logaritmos: "log_2(32)" -> "logaritmo en base 2 de 32"; la base puede ser una letra: "log_x(81)".
        Regex("""log_(\p{L}|\d+)\(([^()]*)\)""") to "logaritmo en base $1 de $2",
        // Espacio para completar en oraciones incompletas y conectores: «Estudió mucho; ______, aprobó».
        Regex("""_{3,}""") to "espacio",
        Regex("""km/h""") to "kilómetros por hora",
        Regex("""S/\s?(\d+(?:,\d+)?)""") to "$1 soles",
        Regex("""(\d+(?:,\d+)?)\s?%""") to "$1 por ciento",
        Regex("""(\d+(?:,\d+)?)\s?°""") to "$1 grados",
        Regex("""\^\(\s*-\s*(\d+)\s*\)""") to " elevado a menos $1",
        Regex("""\^\(\s*(\d+)\s*\)""") to " elevado a $1",
        Regex("""\^(\d+)""") to " elevado a $1",
        Regex("""\^\(([^)]*)\)""") to " elevado a ($1)",
        Regex("""²""") to " al cuadrado",
        Regex("""³""") to " al cubo",
        Regex("""(\d)√""") to "$1 por raíz de ",
        Regex("""√""") to "raíz de ",
        Regex("""(\d+)\s?/\s?(\d+)""") to "$1 sobre $2",
        Regex("""\s*÷\s*""") to " entre ",
        Regex("""\s*[·×]\s*""") to " por ",
        Regex("""\s*≈\s*""") to " aproximadamente ",
        Regex("""\s*=\s*""") to " igual a ",
        Regex("""\s\+\s""") to " más ",
        Regex("""\s-\s""") to " menos ",
        Regex("""(?<![\p{L}\p{N})])-(?=[\d(√\p{L}])""") to "menos ",
        Regex("""(?<![\p{L}\p{N})])\+(?=[\d(\p{L}])""") to "más ",
        Regex("""(\d)\s+s(?![\p{L}\p{N}/])""") to "$1 segundos",
        Regex("""\s:\s""") to " a ",
        Regex(""",?\s?\.\.\.""") to ", etcétera",
        Regex("""(\d)\s?km(?!\p{L})""") to "$1 kilómetros",
        Regex("""(\d)\s?cm(?!\p{L})""") to "$1 centímetros",
        Regex("""(\d)\s?m(?![\p{L}/])""") to "$1 metros",
        Regex("""(?<!\p{L})sen(?!\p{L})""") to "seno",
        Regex("""(?<!\p{L})cos(?!\p{L})""") to "coseno",
        Regex("""(?<!\p{L})tan(?!\p{L})""") to "tangente",
        Regex("""(?<!\p{L})cot(?!\p{L})""") to "cotangente",
        Regex("""(?<!\p{L})csc(?!\p{L})""") to "cosecante",
        Regex("""(?<!\p{L})sec(?!\p{L})""") to "secante",
        Regex("""θ""") to "theta",
        // Respaldos generales (después de las reglas específicas): "(3x - 20)°", "a^n", "(b/a)", "1/√2".
        Regex("""(?<=[\p{L})])\s?°""") to " grados",
        Regex("""\^([\p{L}\p{N}]+)""") to " elevado a $1",
        Regex("""\s?/\s?""") to " sobre ",
    )

    /** Texto listo para el sintetizador de voz. */
    fun forTts(text: String): String {
        var out = joinLines(text)
        for ((regex, replace) in PRE_RULES) out = regex.replace(out) { replace(it) }
        for ((regex, replacement) in RULES) out = regex.replace(out, replacement)
        return out.replace(Regex("""\s+"""), " ").replace(" ,", ",").replace(" .", ".").replace("( ", "(").replace(" )", ")").trim()
    }

    private fun joinLines(text: String): String {
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val sb = StringBuilder()
        for ((i, line) in lines.withIndex()) {
            sb.append(line)
            if (i < lines.lastIndex) sb.append(if (line.last() in ".!?:;,") " " else ". ")
        }
        return sb.toString()
    }
}
