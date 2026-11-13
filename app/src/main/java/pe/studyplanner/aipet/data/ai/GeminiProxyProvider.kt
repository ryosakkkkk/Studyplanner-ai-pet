package pe.studyplanner.aipet.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pe.studyplanner.aipet.domain.academic.DatasetScope
import pe.studyplanner.aipet.domain.agent.*
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

data class ProxyReply(val kind:String,val text:String)
interface AiTransport {
    suspend fun check():String
    suspend fun chat(text:String,context:AgentContext):ProxyReply
}

class GeminiProxyClient(private val store:AiConnectionStore):AiTransport {
    // El servidor prueba un proveedor de IA y, si no responde, pasa al de respaldo: en ese caso la respuesta llega
    // después de unos 23 s (medido el 11 nov). Con 25 s de espera esas respuestas quedaban al límite.
    private companion object { const val READ_TIMEOUT_MS=45_000 }
    override suspend fun check():String {check(request("check",JSONObject()).optBoolean("configured",false)){"El servidor no está configurado."};return "Servidor disponible y acceso válido. La respuesta de la IA se comprueba al enviar una petición."}
    override suspend fun chat(text:String,context:AgentContext):ProxyReply {
        val json=request("chat",JSONObject().put("message",text).put("zoneId",context.zoneId).put("now",context.now.toString()))
        val kind=json.optString("kind");val body=json.optString("text").trim()
        check(kind in setOf("answer","command","clarify") && body.isNotBlank() && body.length<=4000){"El servidor devolvió una respuesta incompatible. No se aplicó ningún cambio."}
        return ProxyReply(kind,body)
    }
    private suspend fun request(path:String,payload:JSONObject):JSONObject=withContext(Dispatchers.IO) {
        val config=store.state.value
        val endpoint=AiConnectionStore.validateEndpoint(config.endpoint)
        val connection=URL("$endpoint/$path").openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects=false
            connection.requestMethod="POST";connection.connectTimeout=10_000;connection.readTimeout=READ_TIMEOUT_MS;connection.doOutput=true
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
            connection.setRequestProperty("Authorization","Bearer ${store.token()}")
            connection.outputStream.use{it.write(payload.toString().toByteArray(Charsets.UTF_8))}
            coroutineContext.ensureActive()
            val code=connection.responseCode
            check(code==200){when(code){401,403->"El código de acceso no es válido. Revisa Conectar IA.";429->"Se alcanzó el límite de uso. Puedes continuar con los comandos locales.";503->"La IA aún no está configurada en tu servidor.";else->"La IA no respondió (HTTP $code). No se aplicó ningún cambio."}}
            val buffer=java.io.ByteArrayOutputStream()
            connection.inputStream.use {input->val chunk=ByteArray(2048);while(true){coroutineContext.ensureActive();val n=input.read(chunk);if(n<0)break;check(buffer.size()+n<=24_000){"Respuesta demasiado grande."};buffer.write(chunk,0,n)}}
            JSONObject(buffer.toString("UTF-8"))
        } finally {connection.disconnect()}
    }
}

/** The provider cannot save data: commands return through the existing local validation and confirmation. */
class GeminiProxyProvider(private val store:AiConnectionStore,private val client:AiTransport=GeminiProxyClient(store)):AgentProvider {
    private val local=LocalRulesFallback()
    override val mode:String get()=if(store.state.value.enabled)"IA_OPCIONAL" else "DEMO_SIN_IA"
    override suspend fun interpret(text:String,context:AgentContext):AgentInterpretation {
        val initial=local.interpret(text,context)
        if(initial !is AgentInterpretation.Clarification || !store.state.value.enabled || context.snapshot.scope==DatasetScope.DEMO || text.isBlank() || text.length>4000 || text.any{Character.isISOControl(it)})return initial
        return try {
            val reply=client.chat(text,context)
            when(reply.kind) {
                "command"->when(val parsed=local.interpret(reply.text,context)) {
                    is AgentInterpretation.Candidate,is AgentInterpretation.Query->parsed
                    else->AgentInterpretation.Clarification((parsed as? AgentInterpretation.Clarification)?.message ?: "Necesito datos más precisos. Puedes usar el formulario.")
                }
                "clarify"->AgentInterpretation.Clarification(reply.text)
                else->AgentInterpretation.Answer("IA: ${reply.text}")
            }
        } catch(e:CancellationException){throw e}
        catch(e:Exception){AgentInterpretation.Clarification("${if(e is IllegalStateException)e.message else "No se pudo conectar con la IA. Revisa tu conexión y el servidor."} Los comandos locales siguen disponibles; por ejemplo: mis pendientes.")}
    }
}
