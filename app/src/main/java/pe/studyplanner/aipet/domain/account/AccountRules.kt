package pe.studyplanner.aipet.domain.account

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Reglas de la cuenta. La contraseña nunca sale del teléfono: se convierte con PBKDF2-SHA256 (100 000 vueltas, sal =
 * «studyplanner:<correo>») y solo esa clave derivada viaja al servidor, que guarda un hash con su propia sal.
 */
object AccountRules {
    const val MIN_PASSWORD = 8
    private val EMAIL = Regex("^[^\\s@]{1,64}@[^\\s@]{1,63}\\.[^\\s@]{2,24}$")

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    /** Mensaje corto si algo falta; null si está bien. */
    fun emailProblem(raw: String): String? = if (EMAIL.matches(normalizeEmail(raw)) && normalizeEmail(raw).length <= 120) null else "Escribe un correo válido."
    fun passwordProblem(raw: String): String? = when {
        raw.length < MIN_PASSWORD -> "Usa al menos $MIN_PASSWORD caracteres."
        raw.length > 128 -> "Usa hasta 128 caracteres."
        else -> null
    }
    fun nameProblem(raw: String): String? = raw.trim().let { if (it.isEmpty() || it.length > 40) "Escribe tu nombre (hasta 40 letras)." else null }

    fun deriveKey(email: String, password: String): String {
        val spec = PBEKeySpec(password.toCharArray(), "studyplanner:${normalizeEmail(email)}".toByteArray(Charsets.UTF_8), 100_000, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        } finally { spec.clearPassword() }
    }
}

/**
 * Copia de la app: un .zip con solo los archivos permitidos (agenda, perfil, práctica, colores de la mascota y voz).
 * No incluye la conexión con la IA ni la sesión de la cuenta.
 */
object BackupArchive {
    val FILES = listOf(
        "databases/academic_v1.db",
        "files/datastore/profile_v1.preferences_pb",
        "files/tutor/intentos.csv",
        "shared_prefs/pet_style_v1.xml",
        "shared_prefs/talk_v1.xml",
    )
    const val MAX_BYTES = 20 * 1024 * 1024

    /** Siempre el mismo .zip para los mismos datos (orden fijo y fecha fija), así el resumen dice si algo cambió. */
    fun pack(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            FILES.forEach { name ->
                val data = entries[name] ?: return@forEach
                zip.putNextEntry(ZipEntry(name).apply { time = 315532800000L })
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Lee una copia; ignora lo que no esté en la lista y rechaza copias dañadas o demasiado grandes. */
    fun unpack(bytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || entry.name !in FILES) continue
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                while (true) {
                    val n = zip.read(chunk)
                    if (n < 0) break
                    total += n
                    require(total <= MAX_BYTES) { "La copia es demasiado grande." }
                    buffer.write(chunk, 0, n)
                }
                result[entry.name] = buffer.toByteArray()
            }
        }
        require(result.isNotEmpty()) { "La copia está vacía." }
        return result
    }

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
