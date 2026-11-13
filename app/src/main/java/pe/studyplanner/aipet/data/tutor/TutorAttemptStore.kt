package pe.studyplanner.aipet.data.tutor

import java.io.File
import java.io.RandomAccessFile
import pe.studyplanner.aipet.domain.tutor.Attempt
import pe.studyplanner.aipet.domain.tutor.AttemptLog

/**
 * Registro de intentos en un archivo CSV del almacenamiento interno de la app (`filesDir/tutor/intentos.csv`).
 * Usa [AttemptLog] para el formato, que no guarda datos personales: solo ids de tema y de ejercicio.
 *
 * Es de Kotlin/Java puro (recibe el [file]) para poder probarlo en la JVM. Todas las operaciones bloquean el hilo
 * que las llama: la app las ejecuta fuera del hilo principal (ver [TutorHistory]).
 */
class TutorAttemptStore(private val file: File) {

    /** Lee todos los intentos válidos. Un archivo ausente es un historial vacío; las filas dañadas se informan en `errors`. */
    @Synchronized
    fun readAll(): AttemptLog.DecodeResult {
        if (!file.isFile) return AttemptLog.DecodeResult(emptyList(), emptyList())
        return AttemptLog.decode(file.readText(Charsets.UTF_8))
    }

    /**
     * Añade [attempts] al final del archivo. Escribe el encabezado si el archivo es nuevo o está vacío, y repara un
     * final de línea faltante (por ejemplo, tras un corte durante una escritura anterior).
     */
    @Synchronized
    fun append(attempts: List<Attempt>) {
        if (attempts.isEmpty()) return
        file.parentFile?.mkdirs()
        val out = StringBuilder()
        if (!file.isFile || file.length() == 0L) {
            out.append(AttemptLog.HEADER).append('\n')
        } else if (!endsWithNewline()) {
            out.append('\n')
        }
        for (attempt in attempts) out.append(AttemptLog.encodeRow(attempt)).append('\n')
        file.appendText(out.toString(), Charsets.UTF_8)
    }

    /**
     * CSV normalizado para compartir: encabezado y las últimas [maxRows] filas válidas. El límite evita pasar de unos
     * cientos de KB por un `Intent`.
     */
    @Synchronized
    fun exportCsv(maxRows: Int = DEFAULT_EXPORT_ROWS): String =
        AttemptLog.encode(readAll().attempts.takeLast(maxRows.coerceAtLeast(1)))

    /** Borra el archivo. Devuelve `true` si ya no existe. */
    @Synchronized
    fun clear(): Boolean = !file.exists() || file.delete()

    private fun endsWithNewline(): Boolean = RandomAccessFile(file, "r").use { raf ->
        if (raf.length() == 0L) {
            true
        } else {
            raf.seek(raf.length() - 1)
            raf.read() == '\n'.code
        }
    }

    companion object {
        const val DEFAULT_EXPORT_ROWS = 2000
    }
}
