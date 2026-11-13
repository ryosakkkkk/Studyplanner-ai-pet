package pe.studyplanner.aipet.domain.tutor

import java.time.Instant

/**
 * Códec CSV (RFC 4180) del historial de intentos. No guarda datos personales: solo fecha, ids de tema y de
 * ejercicio, opción elegida y contadores. El ejercicio se puede regenerar a partir de su id.
 *
 * Columnas: `fecha_iso,tema,modo,ejercicio,opcion,correcta,pistas,tiempo_ms`.
 * - `fecha_iso`: instante en UTC, por ejemplo `2026-10-28T20:15:03.250Z`.
 * - `opcion`: letra A–E, o `-` si el estudiante marcó «No sé».
 * - `correcta`: `1` o `0`.
 */
object AttemptLog {

    /** Encabezado del archivo. */
    const val HEADER = "fecha_iso,tema,modo,ejercicio,opcion,correcta,pistas,tiempo_ms"

    private const val COLUMNS = 8
    private const val LETTERS = "ABCDE"
    private const val NO_ANSWER_TEXT = "-"
    private const val BOM = "\uFEFF"

    /** Resultado de leer un CSV: filas válidas y un mensaje por cada fila rechazada. */
    data class DecodeResult(val attempts: List<Attempt>, val errors: List<String>)

    /** CSV completo (encabezado y una fila por intento). */
    fun encode(attempts: List<Attempt>): String =
        (listOf(HEADER) + attempts.map(::encodeRow)).joinToString("\n", postfix = "\n")

    /** Una fila CSV, sin salto de línea; útil para añadir al final de un archivo existente. */
    fun encodeRow(a: Attempt): String = listOf(
        Instant.ofEpochMilli(a.timestampMillis).toString(),
        a.topicId,
        a.mode.name,
        a.exerciseId,
        if (a.selectedIndex in LETTERS.indices) LETTERS[a.selectedIndex].toString() else NO_ANSWER_TEXT,
        if (a.correct) "1" else "0",
        a.hintsShownBefore.toString(),
        a.elapsedMs.toString(),
    ).joinToString(",") { quote(it) }

    /**
     * Lee un CSV. Acepta fin de línea `\n` o `\r\n`, campos entre comillas con comas, comillas dobles y saltos de
     * línea, y omite líneas vacías. Las filas inválidas no detienen la lectura: se reportan en `errors`.
     */
    fun decode(csv: String): DecodeResult {
        val rows = parseRows(csv.removePrefix(BOM))
        val attempts = mutableListOf<Attempt>()
        val errors = mutableListOf<String>()
        var headerSeen = false
        for ((index, row) in rows.withIndex()) {
            if (!headerSeen) {
                headerSeen = true
                if (row.joinToString(",") == HEADER) continue
                // Sin encabezado, la primera fila puede ser un intento (archivo creado a mano o recortado).
                val first = toAttempt(row)
                if (first != null) attempts += first else errors += "Fila 1: encabezado inesperado"
                continue
            }
            toAttempt(row)?.let { attempts += it } ?: run { errors += "Fila ${index + 1}: formato inválido" }
        }
        return DecodeResult(attempts, errors)
    }

    private fun toAttempt(f: List<String>): Attempt? {
        if (f.size != COLUMNS) return null
        val time = runCatching { Instant.parse(f[0]).toEpochMilli() }.getOrNull() ?: return null
        val mode = AttemptMode.values().firstOrNull { it.name == f[2] } ?: return null
        val selected = when {
            f[4] == NO_ANSWER_TEXT -> Attempt.NO_ANSWER
            f[4].length == 1 && f[4][0] in LETTERS -> LETTERS.indexOf(f[4][0])
            else -> return null
        }
        val correct = when (f[5]) { "1" -> true; "0" -> false; else -> return null }
        val hints = f[6].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val elapsed = f[7].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        return Attempt(time, f[1], f[3], mode, selected, correct, hints, elapsed).takeIf { f[1].isNotEmpty() }
    }

    /** Entrecomilla un campo cuando contiene coma, comillas, saltos de línea o espacios al borde. */
    private fun quote(field: String): String {
        val needs = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            field.startsWith(' ') || field.endsWith(' ')
        return if (needs) "\"" + field.replace("\"", "\"\"") + "\"" else field
    }

    /** Divide el texto en filas de campos respetando las comillas. Ignora las filas completamente vacías. */
    private fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var wasQuoted = false
        var i = 0
        fun endField() {
            fields += field.toString()
            field.clear()
            wasQuoted = false
        }
        fun endRow() {
            val quoted = wasQuoted
            endField()
            if (!(fields.size == 1 && fields[0].isEmpty() && !quoted)) rows += fields
            fields = mutableListOf()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                inQuotes && c == '"' -> inQuotes = false
                inQuotes -> field.append(c)
                c == '"' -> { inQuotes = true; wasQuoted = true }
                c == ',' -> endField()
                c == '\r' -> { if (text.getOrNull(i + 1) == '\n') i++; endRow() }
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || fields.isNotEmpty() || wasQuoted) endRow()
        return rows
    }
}
