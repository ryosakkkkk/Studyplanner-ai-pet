package pe.studyplanner.aipet.data.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AiConnection(val endpoint:String="",val enabled:Boolean=false,val hasToken:Boolean=false,val autoSpeak:Boolean=false,val error:String="",
    /** La IA viene incluida en la app (servidor y código puestos al compilar): siempre encendida, sin ajustes para el estudiante. */
    val builtIn:Boolean=false)

/** Only a revocable personal proxy token is stored, encrypted by the device keystore. */
class AiConnectionStore(context:Context,private val preferencesName:String="ai_connection_v1") {
    private val prefs=context.applicationContext.getSharedPreferences(preferencesName,Context.MODE_PRIVATE)
    private val mutable=MutableStateFlow(AiConnection(prefs.getString("endpoint","").orEmpty(),prefs.getBoolean("enabled",false),prefs.contains("token"),prefs.getBoolean("autoSpeak",false),builtIn=prefs.getBoolean("builtIn",false)))
    val state=mutable.asStateFlow()
    @Synchronized fun save(endpoint:String,newToken:String,enabled:Boolean) {
        try {
            val normalized=validateEndpoint(endpoint)
            require(!enabled || newToken.isNotBlank() || state.value.hasToken){"Introduce el código de acceso de tu servidor."}
            require(newToken.isBlank() || newToken.matches(Regex("[A-Za-z0-9_-]{43,128}"))){"Usa el código personal de 43 a 128 caracteres; no la clave de un proveedor."}
            require(normalized==state.value.endpoint || newToken.isNotBlank()){"Al cambiar el servidor debes introducir su propio código de acceso."}
            val editor=prefs.edit().putString("endpoint",normalized).putBoolean("enabled",enabled)
            if(newToken.isNotBlank())editor.putString("token",encrypt(newToken))
            check(editor.commit()){"No se pudo guardar la conexión."}
            mutable.value=state.value.copy(endpoint=normalized,enabled=enabled,hasToken=prefs.contains("token"),error="")
        } catch(_:Exception){mutable.value=state.value.copy(error="No se pudo guardar. Revisa la URL HTTPS y el código personal (43 a 128 caracteres).")}
    }
    /** Deja lista la conexión incluida al compilar (ACTIVAR_IA.bat), sin activarla: el estudiante decide con un toque. */
    @Synchronized fun preconfigure(endpoint:String,token:String) {
        if(endpoint.isBlank() || token.isBlank() || state.value.endpoint.isNotBlank() || state.value.hasToken) return
        try {
            val normalized=validateEndpoint(endpoint)
            require(token.matches(Regex("[A-Za-z0-9_-]{43,128}")))
            if(prefs.edit().putString("endpoint",normalized).putBoolean("enabled",false).putString("token",encrypt(token)).commit())
                mutable.value=state.value.copy(endpoint=normalized,enabled=false,hasToken=true,error="")
        } catch(_:Exception) { /* Una configuración incluida inválida se ignora: la app sigue en modo local. */ }
    }
    /**
     * IA incluida al compilar (ACTIVAR_IA.bat): queda encendida siempre y se actualiza si la app trae otro servidor o código.
     * El estudiante no configura nada. Sin datos incluidos no hace nada y la app sigue en modo local.
     */
    @Synchronized fun builtIn(endpoint:String,token:String) {
        if(endpoint.isBlank() || token.isBlank()) return
        try {
            val normalized=validateEndpoint(endpoint)
            require(token.matches(Regex("[A-Za-z0-9_-]{43,128}")))
            val same=state.value.endpoint==normalized && state.value.hasToken && runCatching{token()}.getOrNull()==token
            val editor=prefs.edit().putString("endpoint",normalized).putBoolean("enabled",true).putBoolean("builtIn",true)
            if(!same)editor.putString("token",encrypt(token))
            if(editor.commit())mutable.value=state.value.copy(endpoint=normalized,enabled=true,hasToken=true,builtIn=true,error="")
        } catch(_:Exception) { /* Una configuración incluida inválida se ignora: la app sigue en modo local. */ }
    }
    @Synchronized fun disable() { if(prefs.edit().putBoolean("enabled",false).commit())mutable.value=state.value.copy(enabled=false,error="") }
    @Synchronized fun forget() {if(prefs.edit().remove("token").remove("endpoint").putBoolean("enabled",false).commit())mutable.value=state.value.copy(endpoint="",enabled=false,hasToken=false,error="")}
    @Synchronized fun setAutoSpeak(value:Boolean){if(prefs.edit().putBoolean("autoSpeak",value).commit())mutable.value=state.value.copy(autoSpeak=value)}
    fun token():String {
        val encrypted=prefs.getString("token",null) ?: error("Configura el código de acceso de tu servidor.")
        return try {
            val parts=encrypted.split(":");require(parts.size==2)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),Charsets.UTF_8)
        } catch(_:Exception){error("No se pudo abrir el código guardado. Introdúcelo de nuevo en Conectar IA.")}
    }
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(ALIAS,null) as? SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(value:String):String {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key())}
        val encrypted=cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP)
    }
    companion object {
        private const val ALIAS="studyplanner_proxy_access_v1"
        fun validateEndpoint(value:String):String {
            val uri=URI(value.trim());require(uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.userInfo==null && uri.query==null && uri.fragment==null && uri.port in listOf(-1,443))
            require(uri.path.isNullOrEmpty() || uri.path=="/"){"Usa solo el dominio HTTPS del servidor."}
            require(!uri.host.equals("generativelanguage.googleapis.com",true) && !uri.host.equals("api.cloudflare.com",true) && !uri.host.equals("api.openai.com",true)){"Usa tu servidor, no el proveedor directamente."}
            return "https://${uri.host.lowercase()}"
        }
    }
}
