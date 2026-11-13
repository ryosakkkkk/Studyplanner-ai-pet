package pe.studyplanner.aipet.data.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pe.studyplanner.aipet.data.ai.AiConnectionStore
import pe.studyplanner.aipet.domain.account.AccountRules
import pe.studyplanner.aipet.domain.account.BackupArchive

/** Estado de la cuenta que ve la app. Sin cuenta, todo se guarda solo en el teléfono. */
data class AccountState(
    val email: String = "",
    val name: String = "",
    val signedIn: Boolean = false,
    /** Hora (ms) de la última copia subida o bajada; 0 si nunca. */
    val lastBackup: Long = 0L,
    val busy: Boolean = false,
    val message: String = "",
)

/** Resultado de entrar: si la nube tiene una copia, la app pregunta si la usa. */
data class SignInResult(val cloudBackupAt: Long?)

/**
 * Cuenta y copia en la nube. Usa el servidor de la app (el mismo de la IA): las rutas de cuenta y `/copia`. La sesión se
 * guarda cifrada con el almacén de claves del teléfono. La copia se sube al salir de la app si algo cambió.
 */
class CloudAccount(private val context: Context, private val server: AiConnectionStore, private val database: () -> androidx.room.RoomDatabase) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(AccountState(
        email = prefs.getString("email", "").orEmpty(), name = prefs.getString("name", "").orEmpty(),
        signedIn = prefs.contains("session"), lastBackup = prefs.getLong("lastBackup", 0L)))
    val state = mutable.asStateFlow()
    private val lock = Mutex()

    /** Hay servidor configurado (la app se compiló con su dirección y token). */
    val available: Boolean get() = server.state.value.endpoint.isNotBlank() && server.state.value.hasToken

    suspend fun register(name: String, email: String, password: String): Result<SignInResult> =
        access("cuenta/registro", JSONObject().put("nombre", name.trim()).put("correo", AccountRules.normalizeEmail(email))
            .put("clave", derive(email, password)))

    suspend fun signIn(email: String, password: String): Result<SignInResult> =
        access("cuenta/entrar", JSONObject().put("correo", AccountRules.normalizeEmail(email)).put("clave", derive(email, password)))

    private suspend fun derive(email: String, password: String) = withContext(Dispatchers.Default) { AccountRules.deriveKey(email, password) }

    private suspend fun access(path: String, body: JSONObject): Result<SignInResult> = lock.withLock {
        busy(true)
        runCatching {
            val json = call("POST", path, body, session = null)
            val email = json.optString("correo"); val name = json.optString("nombre")
            check(prefs.edit().putString("email", email).putString("name", name).putString("session", encrypt(json.getString("sesion")))
                .remove("lastHash").putLong("lastBackup", 0L).commit()) { "No se pudo guardar la sesión." }
            mutable.value = AccountState(email = email, name = name, signedIn = true)
            SignInResult(json.optJSONObject("copia")?.optLong("fecha"))
        }.also { done(it.exceptionOrNull()) }
    }

    /** Sube la copia si cambió desde la última vez (o siempre, con [force]). */
    suspend fun backup(force: Boolean = false): Result<Boolean> = lock.withLock {
        if (!state.value.signedIn) return@withLock Result.success(false)
        busy(force)
        runCatching {
            val zip = withContext(Dispatchers.IO) { BackupArchive.pack(collect()) }
            val hash = BackupArchive.sha256Hex(zip)
            if (!force && hash == prefs.getString("lastHash", null)) return@runCatching false
            val json = call("PUT", "copia", JSONObject().put("datos", Base64.encodeToString(zip, Base64.NO_WRAP)).put("hash", hash), session())
            val at = json.optLong("fecha", System.currentTimeMillis())
            prefs.edit().putString("lastHash", hash).putLong("lastBackup", at).apply()
            mutable.value = state.value.copy(lastBackup = at)
            true
        }.also { done(it.exceptionOrNull(), if (force && it.isSuccess) "Copia guardada en la nube." else "") }
    }

    /**
     * Baja la copia de la nube y la deja lista para aplicarse al reiniciar la app (la base de datos no se puede cambiar
     * mientras está abierta). Después hay que llamar a [restartApp].
     */
    suspend fun downloadForRestore(): Result<Unit> = lock.withLock {
        busy(true)
        runCatching {
            val json = call("GET", "copia", null, session())
            val zip = Base64.decode(json.getString("datos"), Base64.NO_WRAP)
            check(BackupArchive.sha256Hex(zip) == json.optString("hash")) { "La copia llegó incompleta. Intenta de nuevo." }
            val files = BackupArchive.unpack(zip)
            withContext(Dispatchers.IO) {
                val stage = File(context.filesDir, STAGE).apply { deleteRecursively(); mkdirs() }
                files.forEach { (name, data) -> File(stage, name).apply { parentFile?.mkdirs(); writeBytes(data) } }
                File(stage, READY).writeText(BackupArchive.sha256Hex(zip))
            }
            prefs.edit().putString("lastHash", BackupArchive.sha256Hex(zip)).putLong("lastBackup", json.optLong("fecha")).commit()
            Unit
        }.also { done(it.exceptionOrNull()) }
    }

    suspend fun signOut() = lock.withLock {
        runCatching { call("POST", "cuenta/salir", JSONObject(), session()) }
        prefs.edit().remove("session").remove("lastHash").putLong("lastBackup", 0L).commit()
        mutable.value = AccountState(email = state.value.email)
    }

    fun clearMessage() { mutable.value = state.value.copy(message = "") }

    private fun busy(on: Boolean) { mutable.value = state.value.copy(busy = on, message = "") }
    private fun done(error: Throwable?, ok: String = "") {
        mutable.value = state.value.copy(busy = false, message = error?.let { friendly(it) } ?: ok)
    }
    private fun friendly(error: Throwable): String = when (error) {
        is ServerMessage -> error.message ?: "No se pudo completar."
        is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.net.ConnectException -> "Sin conexión. Tus datos siguen en el teléfono."
        else -> error.message?.takeIf { it.length < 120 } ?: "No se pudo completar. Intenta de nuevo."
    }

    /** Archivos que van en la copia, leídos tal como están en el teléfono. */
    private fun collect(): Map<String, ByteArray> {
        // Pasa lo que está en el diario de SQLite al archivo principal antes de copiarlo.
        runCatching { database().openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() } }
        val root = context.filesDir.parentFile ?: return emptyMap()
        return BackupArchive.FILES.mapNotNull { name -> File(root, name).takeIf { it.isFile }?.let { name to it.readBytes() } }.toMap()
    }

    private fun session(): String = decrypt(prefs.getString("session", null) ?: throw ServerMessage("Vuelve a entrar a tu cuenta."))

    private suspend fun call(method: String, path: String, body: JSONObject?, session: String?): JSONObject = withContext(Dispatchers.IO) {
        val conn = server.state.value
        if (conn.endpoint.isBlank() || !conn.hasToken) throw ServerMessage("Esta versión no tiene servidor para cuentas.")
        val connection = URL("${conn.endpoint}/$path").openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000; connection.readTimeout = 60_000
            connection.setRequestProperty("Authorization", "Bearer ${server.token()}")
            if (session != null) connection.setRequestProperty("X-Sesion", session)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code == 401 && session != null) {
                prefs.edit().remove("session").commit()
                mutable.value = state.value.copy(signedIn = false)
            }
            if (code !in 200..299) throw ServerMessage(json.optString("message").ifBlank { "El servidor no respondió bien ($code)." })
            json
        } finally { connection.disconnect() }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String = try {
        val parts = value.split(":"); require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (_: Exception) { throw ServerMessage("Vuelve a entrar a tu cuenta.") }

    private class ServerMessage(message: String) : Exception(message)

    companion object {
        private const val PREFS = "cuenta_v1"
        private const val ALIAS = "studyplanner_cuenta_v1"
        private const val STAGE = "copia_pendiente"
        private const val READY = ".lista"

        /**
         * Al abrir la app, antes de abrir la base de datos: si hay una copia bajada esperando, la pone en su lugar.
         * Si algo falla, se deja todo como estaba.
         */
        fun applyPendingRestore(context: Context) {
            val stage = File(context.filesDir, STAGE)
            if (!File(stage, READY).isFile) { stage.deleteRecursively(); return }
            val root = context.filesDir.parentFile ?: return
            runCatching {
                BackupArchive.FILES.forEach { name ->
                    val source = File(stage, name)
                    if (!source.isFile) return@forEach
                    val target = File(root, name)
                    if (name.startsWith("databases/")) listOf("-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
                    target.parentFile?.mkdirs()
                    source.copyTo(target, overwrite = true)
                }
            }.onFailure { android.util.Log.e("StudyPlannerCuenta", "No se pudo aplicar la copia", it) }
            stage.deleteRecursively()
        }

        /** Reinicia la app para abrir los datos recién bajados. */
        fun restartApp(context: Context) {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
            context.startActivity(android.content.Intent.makeRestartActivityTask(intent.component))
            Runtime.getRuntime().exit(0)
        }
    }
}
