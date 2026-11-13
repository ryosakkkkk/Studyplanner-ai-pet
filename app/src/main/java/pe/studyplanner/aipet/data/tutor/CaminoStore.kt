package pe.studyplanner.aipet.data.tutor

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import pe.studyplanner.aipet.domain.json.JsonException
import pe.studyplanner.aipet.domain.json.MiniJson
import pe.studyplanner.aipet.domain.tutor.Attempt
import pe.studyplanner.aipet.domain.tutor.AttemptMode

/**
 * Lo que la tarjeta «Camino UNAMBA» de Estudiar muestra sin abrir el camino: grupo elegido, avance y la etapa que sigue.
 * Lo escribe la página del camino cada vez que guarda.
 */
data class CaminoSummary(
    val group: String,
    val groupName: String,
    val done: Int,
    val total: Int,
    val next: String,
    val nextCourse: String,
    val diagnosed: Boolean,
) {
    companion object {
        /** Lee el resumen que envía la página (`{"g":"A","n":"Ingenierías","h":3,"t":73,"s":"…","c":"…","d":true}`); `null` si no hay grupo. */
        fun parse(json: String): CaminoSummary? {
            if (json.isBlank()) return null
            val parsed = try { MiniJson.parse(json) } catch (_: JsonException) { null }
            val map = parsed as? Map<*, *> ?: return null
            val group = (map["g"] as? String).orEmpty()
            if (group.isBlank()) return null
            fun int(key: String) = (map[key] as? Long)?.coerceIn(0, 10_000)?.toInt() ?: 0
            return CaminoSummary(
                group = group.take(2),
                groupName = (map["n"] as? String).orEmpty().take(40),
                done = int("h"),
                total = int("t"),
                next = (map["s"] as? String).orEmpty().take(120),
                nextCourse = (map["c"] as? String).orEmpty().take(60),
                diagnosed = map["d"] == true,
            )
        }
    }
}

/**
 * Respuestas dadas en el camino, contadas por día. Sirven para que la racha y la meta de «Hoy» también cuenten la
 * práctica del camino. Es de Kotlin puro para probarlo en la JVM. Formato guardado: `2026-11-11=7;2026-11-10=3`.
 */
object CaminoDays {
    /** Días que se conservan (los más recientes). */
    const val KEEP_DAYS = 400

    fun parse(text: String): Map<LocalDate, Int> = buildMap {
        for (part in text.split(';')) {
            val day = part.substringBefore('=', "").trim()
            val count = part.substringAfter('=', "").trim().toIntOrNull() ?: continue
            val date = try { LocalDate.parse(day) } catch (_: RuntimeException) { continue }
            if (count > 0) put(date, count)
        }
    }

    fun format(days: Map<LocalDate, Int>): String =
        days.entries.sortedByDescending { it.key }.take(KEEP_DAYS).joinToString(";") { "${it.key}=${it.value}" }

    /** [days] con una respuesta más en [day]. */
    fun add(days: Map<LocalDate, Int>, day: LocalDate): Map<LocalDate, Int> = days + (day to ((days[day] ?: 0) + 1))

    /**
     * Las respuestas del camino como intentos «de conteo»: solo sirven para la racha y la meta del día (su tema no
     * existe en el catálogo, así que no deben mezclarse con el historial del tutor ni guardarse).
     */
    fun asAttempts(days: Map<LocalDate, Int>, zone: ZoneId): List<Attempt> = days.flatMap { (day, count) ->
        val noon = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        (0 until count.coerceAtMost(MAX_PER_DAY)).map { i ->
            Attempt(noon + i, TOPIC_ID, TOPIC_ID, AttemptMode.GUIDED, selectedIndex = 0, correct = true, hintsShownBefore = 0, elapsedMs = 0)
        }
    }

    const val TOPIC_ID = "camino"
    private const val MAX_PER_DAY = 50
}

/** Estado del camino en las preferencias de la app (`camino.xml`): lo guarda y lo lee la página del camino. */
object CaminoStore {
    private const val PREFS = "camino"
    private const val KEY_STATE = "estado"
    private const val KEY_SUMMARY = "resumen"
    private const val KEY_DAYS = "dias"
    private const val MAX_STATE_CHARS = 400_000

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun state(context: Context): String = prefs(context).getString(KEY_STATE, "").orEmpty()

    fun save(context: Context, state: String, summary: String) {
        if (state.length > MAX_STATE_CHARS) return
        prefs(context).edit().putString(KEY_STATE, state).putString(KEY_SUMMARY, summary.take(2_000)).apply()
    }

    fun summary(context: Context): CaminoSummary? = CaminoSummary.parse(prefs(context).getString(KEY_SUMMARY, "").orEmpty())

    fun days(context: Context): Map<LocalDate, Int> = CaminoDays.parse(prefs(context).getString(KEY_DAYS, "").orEmpty())

    /** Anota una respuesta del camino en el día [day]. */
    @Synchronized
    fun recordAnswer(context: Context, day: LocalDate) {
        val p = prefs(context)
        val next = CaminoDays.add(CaminoDays.parse(p.getString(KEY_DAYS, "").orEmpty()), day)
        p.edit().putString(KEY_DAYS, CaminoDays.format(next)).apply()
    }
}

/**
 * Paso de las peticiones del tutor del camino hacia la IA. La página arma la petición completa (la pregunta, la
 * conversación y las reglas del método socrático) y la app solo la reenvía. Kotlin puro.
 */
object CaminoAiPrompt {
    /** El servidor acepta hasta 4000 caracteres por mensaje. */
    const val MAX_REQUEST = 3_800

    /** La petición en una sola línea, sin caracteres de control y con tope de tamaño (lo que exige el servidor). */
    fun relay(request: String): String =
        request.replace(Regex("\\s+"), " ").filter { !Character.isISOControl(it) }.trim().take(MAX_REQUEST)

    /**
     * Qué texto de la respuesta se le muestra al estudiante. Una pregunta del tutor puede llegar como «answer» o como
     * «clarify»; una orden para la agenda («command») no es una respuesta del tutor.
     */
    fun replyText(kind: String, text: String): String? = when (kind) {
        "answer", "clarify" -> TutorAiPrompt.cleanReply(text).takeIf { it.isNotBlank() }
        "command" -> ONLY_STUDY
        else -> null
    }

    /** Aviso para el estudiante según el fallo del servidor; vacío si fue un problema de conexión cualquiera. */
    fun failureText(message: String?): String {
        val text = message.orEmpty()
        return when {
            text.contains("límite", ignoreCase = true) -> "El tutor llegó a su límite de uso por ahora. Sigue con la guía paso a paso e intenta de nuevo en un rato."
            text.contains("código de acceso", ignoreCase = true) -> "El acceso a la IA no es válido. Revísalo en Conectar IA."
            text.contains("no está configurad", ignoreCase = true) -> "La IA todavía no está configurada en el servidor."
            else -> ""
        }
    }

    const val ONLY_STUDY = "Aquí solo te ayudo a estudiar. Escríbeme tu duda sobre el tema o lo que estás pensando."
}

/**
 * Pregunta que pasa de la práctica de la app a la página del tutor. Se guarda solo en memoria y se entrega una vez:
 * la página la pide al abrirse (`AndroidCamino.pendiente()`) y, si la hay, muestra directo la conversación.
 */
object CaminoHandoff {
    private val pending = java.util.concurrent.atomic.AtomicReference("")

    fun put(question: String) = pending.set(question.take(MAX_CHARS))

    /** La pregunta pendiente (o vacío); al leerla se borra. */
    fun take(): String = pending.getAndSet("")

    private const val MAX_CHARS = 20_000
}
