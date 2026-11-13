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
            check(code==200){when(code){401,403->"El servidor de la IA no aceptó el acceso.";429->"La IA en línea llegó a su límite por hoy.";503->"La IA aún no está configurada en el servidor.";else->"La IA no respondió (HTTP $code)."}}
            val buffer=java.io.ByteArrayOutputStream()
            connection.inputStream.use {input->val chunk=ByteArray(2048);while(true){coroutineContext.ensureActive();val n=input.read(chunk);if(n<0)break;check(buffer.size()+n<=24_000){"Respuesta demasiado grande."};buffer.write(chunk,0,n)}}
            JSONObject(buffer.toString("UTF-8"))
        } finally {connection.disconnect()}
    }
}

/**
 * Quién responde en «Hablar», en este orden: 1) las órdenes de la agenda (reglas locales); 2) saludos y cuentas
 * rápidas; 3) lo que la mascota sabe sin Internet ([StudyKnowledge]); 4) solo si nada de eso alcanza y hay conexión,
 * la IA del servidor (Gemini). Sin Internet la app sigue respondiendo con lo que tiene guardado.
 * The provider cannot save data: commands return through the existing local validation and confirmation.
 */
class GeminiProxyProvider(private val store:AiConnectionStore,private val client:AiTransport=GeminiProxyClient(store),
    private val knowledge:()->pe.studyplanner.aipet.domain.knowledge.StudyKnowledge? = {null}):AgentProvider {
    private val local=LocalRulesFallback()
    override val mode:String get()=if(store.state.value.enabled)"IA_OPCIONAL" else "DEMO_SIN_IA"
    override suspend fun interpret(text:String,context:AgentContext):AgentInterpretation {
        val initial=local.interpret(text,context)
        if(initial !is AgentInterpretation.Clarification || text.isBlank() || text.length>4000 || text.any{Character.isISOControl(it)})return initial
        // Una orden de agenda reconocida pero incompleta conserva su aclaración; el texto libre va a lo que sabe la mascota.
        val freeText=initial.message.startsWith(OfflineAnswers.NOT_A_COMMAND)
        var offline:AgentInterpretation=initial
        if(freeText) {
            OfflineAnswers.smallTalk(text)?.let{return AgentInterpretation.Answer(it)}
            pe.studyplanner.aipet.domain.knowledge.QuickMath.answer(text)?.let{return AgentInterpretation.Answer(it)}
            val base=runCatching{knowledge()}.getOrNull()
            val hit=base?.best(text)
            if(base!=null && hit!=null && hit.strong)return AgentInterpretation.Answer(base.reply(hit))
            offline=if(base!=null && hit!=null)AgentInterpretation.Answer(OfflineAnswers.CLOSEST+base.reply(hit)) else AgentInterpretation.Clarification(OfflineAnswers.UNKNOWN)
        }
        if(!store.state.value.enabled || context.snapshot.scope==DatasetScope.DEMO)return offline
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
        // Sin conexión (o el servidor falló): se responde con lo guardado, sin errores técnicos.
        catch(_:Exception){offline}
    }
}

/** Respuestas que no necesitan Internet ni la base: saludos, gracias y el aviso cuando no hay ficha. */
object OfflineAnswers {
    /** Inicio de la aclaración de [LocalRulesFallback] cuando el texto no es ninguna orden de la agenda. */
    const val NOT_A_COMMAND="Modo DEMO_SIN_IA"
    const val UNKNOWN="Todavía no tengo ese tema guardado. Prueba con otras palabras, por ejemplo: «¿qué es la fotosíntesis?» o «regla de tres»."
    const val CLOSEST="No lo tengo exacto; lo más cercano que sé: "
    private fun plain(text:String)=java.text.Normalizer.normalize(text.lowercase(java.util.Locale.ROOT),java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"),"").replace(Regex("[^a-z ]")," ").replace(Regex("\\s+")," ").trim()
    fun smallTalk(text:String):String? {
        val t=plain(text)
        return when {
            t.matches(Regex("(hola|holi|buenas|buenos dias|buenas tardes|buenas noches|hey|que tal|como estas|hola como estas)( kiro| amigo| amiga)?")) ->
                "¡Hola! Pregúntame un tema del examen, por ejemplo «¿qué es la mitosis?», o dime «agéndame estudiar mañana»."
            t.matches(Regex("(gracias|muchas gracias|gracias kiro|ok gracias|chevere|genial)")) -> "¡De nada! Cuando quieras seguimos estudiando."
            t.matches(Regex("(que sabes|que puedes hacer|ayuda|que temas sabes|en que me ayudas)")) ->
                "Sé los temas del examen de admisión: pregúntame «qué es…» o «explícame…». También hago cuentas («cuánto es 15 % de 80») y organizo tu agenda («agéndame…»)."
            else -> null
        }
    }
}
