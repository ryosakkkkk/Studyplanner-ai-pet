package pe.studyplanner.aipet.domain.tutor

import java.text.Normalizer
import java.util.Locale

/**
 * Interpreta una respuesta dictada ("opción c", "la b", "alternativa de") como el índice de una alternativa (0 a 4).
 * Solo propone una lectura: la interfaz siempre pide confirmación antes de responder.
 */
object SpokenOption {

    private val LETTER_WORDS: Map<String, Int> = mapOf(
        "a" to 0,
        "b" to 1, "be" to 1, "ve" to 1,
        "c" to 2, "ce" to 2, "se" to 2,
        "d" to 3, "de" to 3,
        "e" to 4,
        "primera" to 0, "segunda" to 1, "tercera" to 2, "cuarta" to 3, "quinta" to 4,
    )

    private val MARKERS = setOf("opcion", "alternativa", "letra", "respuesta", "inciso")

    private val FILLERS = setOf(
        "la", "el", "es", "son", "sera", "creo", "que", "pues", "marco", "elijo", "quiero", "pongo", "voy", "con",
        "seria", "mi", "por", "favor", "eh", "mmm", "este", "bueno", "entonces",
    )

    /** El índice de la alternativa dictada, o `null` si no se entiende una sola opción. */
    fun parse(text: String): Int? {
        val tokens = normalize(text).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        for (i in tokens.indices) {
            if (tokens[i] !in MARKERS) continue
            var j = i + 1
            while (j < tokens.size && tokens[j] in FILLERS) j++
            val letter = tokens.getOrNull(j)?.let { LETTER_WORDS[it] }
            if (letter != null) return letter
        }
        val rest = tokens.filter { it !in FILLERS && it !in MARKERS }
        return if (rest.size == 1) LETTER_WORDS[rest[0]] else null
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9ñ]+"), " ").trim()
    }
}
