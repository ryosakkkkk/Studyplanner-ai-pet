package pe.studyplanner.aipet.domain.profile

import java.text.Normalizer
import java.time.ZoneId

object ProfileValidator {
    private val availableZoneIds = ZoneId.getAvailableZoneIds()

    fun normalize(profile: UserProfile): UserProfile = profile.copy(
        studentName = normalizeName(profile.studentName),
        petName = normalizeName(profile.petName),
        zoneId = profile.zoneId.trim(),
    )

    fun validate(profile: UserProfile): Map<String, String> {
        val normalized = normalize(profile)
        return buildMap {
            if (hasControlCharacters(profile.studentName)) {
                put("studentName", "El nombre no puede contener caracteres de control.")
            } else if (normalized.studentName.isNotEmpty() && !hasNameBase(normalized.studentName)) {
                put("studentName", "Incluye al menos un carácter visible en tu nombre, o déjalo vacío.")
            } else if (ExtendedGraphemes.count(normalized.studentName) > 80) {
                put("studentName", "Usa como máximo 80 caracteres visibles para tu nombre.")
            }

            if (hasControlCharacters(profile.petName)) {
                put("petName", "El nombre de la mascota no puede contener caracteres de control.")
            } else if (ExtendedGraphemes.count(normalized.petName) !in 1..24) {
                put("petName", "Escribe un nombre de mascota de 1 a 24 caracteres visibles.")
            } else if (!hasNameBase(normalized.petName)) {
                put("petName", "Incluye al menos un carácter visible en el nombre de la mascota.")
            }

            if (normalized.zoneId !in availableZoneIds) {
                put("zoneId", "Selecciona una zona horaria válida, por ejemplo America/Lima.")
            }
        }
    }

    private fun normalizeName(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFC)

    private fun hasControlCharacters(value: String): Boolean =
        value.any { Character.isISOControl(it) }

    // Las marcas, uniones y selectores pueden acompañar a una base; no se borran ni se
    // prohíben dentro del nombre, para conservar acentos, escrituras no latinas y emoji ZWJ.
    private fun hasNameBase(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val codePoint = Character.codePointAt(value, index)
            val type = Character.getType(codePoint)
            val isDependentOrSeparator = when (type) {
                Character.CONTROL.toInt(), Character.FORMAT.toInt(), Character.SURROGATE.toInt(),
                Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt(), Character.SPACE_SEPARATOR.toInt(),
                Character.LINE_SEPARATOR.toInt(), Character.PARAGRAPH_SEPARATOR.toInt() -> true
                else -> false
            }
            if (!isDependentOrSeparator && !isDefaultIgnorable(codePoint) && codePoint != 0x2800) {
                // U+2800 es BRAILLE PATTERN BLANK. Se conservan puntos aún no asignados por
                // el runtime, para no rechazar emoji recientes solo por su versión Unicode.
                return true
            }
            index += Character.charCount(codePoint)
        }
        return false
    }

    // Default_Ignorable_Code_Point, Unicode 17.0. La lista explícita cubre también
    // fillers de categoría Letter y caracteres que un Android antiguo aún no clasifica.
    // https://www.unicode.org/Public/17.0.0/ucd/DerivedCoreProperties.txt
    private fun isDefaultIgnorable(codePoint: Int): Boolean = when (codePoint) {
        0x00AD, 0x034F, 0x061C,
        in 0x115F..0x1160,
        in 0x17B4..0x17B5,
        in 0x180B..0x180F,
        in 0x200B..0x200F,
        in 0x202A..0x202E,
        in 0x2060..0x206F,
        0x3164,
        in 0xFE00..0xFE0F,
        0xFEFF, 0xFFA0,
        in 0xFFF0..0xFFF8,
        in 0x1BCA0..0x1BCA3,
        in 0x1D173..0x1D17A,
        in 0xE0000..0xE0FFF -> true
        else -> false
    }

}
