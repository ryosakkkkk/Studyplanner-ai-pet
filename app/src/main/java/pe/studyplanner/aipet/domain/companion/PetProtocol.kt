package pe.studyplanner.aipet.domain.companion

import java.util.UUID

/** Caras que entiende la mascota física (firmware 1.x). La palabra de [wire] es la que viaja por BLE. */
enum class PetExpression(val wire: String) {
    IDLE("idle"),
    FELIZ("feliz"),
    PENSAR("pensar"),
    HABLAR("hablar"),
    SORPRENDIDO("sorprendido"),
    CELEBRAR("celebrar"),
    DORMIR("dormir"),
    ALERTA("alerta"),
    ESCUCHAR("escuchar"),
}

/** Sonidos que la mascota sintetiza; [code] es el byte que se escribe en la característica CUE. */
enum class PetSound(val code: Int) {
    CORRECTO(1),
    ERROR(2),
    PISTA(3),
    RECORDATORIO(4),
    CELEBRACION(5),
}

/** Mensajes que la mascota envía por la característica EVENT. */
sealed interface PetEvent {
    /** Botón de alternativa: [index] 0 a 4 (A a E). */
    data class Option(val index: Int) : PetEvent

    /** Pulsación corta de TALK. */
    data object Talk : PetEvent

    /** TALK mantenido 700 ms: siguiente. */
    data object Ok : PetEvent

    /** Respuesta al diagnóstico de latencia `P:<n>`. */
    data class Pong(val token: String) : PetEvent
}

/** Contenido de la característica STATUS (`FW:1.0.0|BAT:NA`). [batteryPercent] es `null` si la mascota no lo mide. */
data class PetStatus(val firmware: String, val batteryPercent: Int?) {
    /** La app solo entiende el protocolo 1.x: el primer número de la versión debe ser 1. */
    val compatible: Boolean get() = firmware.substringBefore('.') == SUPPORTED_MAJOR

    private companion object {
        const val SUPPORTED_MAJOR = "1"
    }
}

/**
 * Contrato BLE con la mascota StudyPet (ver esp32/PROTOCOLO_BLE.md): UUID, codificación de FACE y CUE, y lectura de
 * EVENT y STATUS. Es Kotlin puro (sin Android) para poder probarlo en la JVM.
 */
object PetProtocol {
    const val DEVICE_NAME = "StudyPet"

    const val SERVICE_UUID = "45bcd4ae-6b58-4eb9-968a-3ed899731e9a"
    const val FACE_UUID = "d693d988-f7c3-41b4-b13a-fc22bf26a0e6"
    const val CUE_UUID = "b547f1e3-3abd-4393-b19a-6837b3f6c6b4"
    const val EVENT_UUID = "8b27bc7a-5906-4647-9730-2d61cbaec596"
    const val STATUS_UUID = "43f84ca4-e9df-4166-9a06-f04aebeb4908"

    /** Descriptor estándar (Client Characteristic Configuration) de EVENT y STATUS. */
    const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"

    val SERVICE: UUID = UUID.fromString(SERVICE_UUID)
    val FACE: UUID = UUID.fromString(FACE_UUID)
    val CUE: UUID = UUID.fromString(CUE_UUID)
    val EVENT: UUID = UUID.fromString(EVENT_UUID)
    val STATUS: UUID = UUID.fromString(STATUS_UUID)
    val CCCD: UUID = UUID.fromString(CCCD_UUID)

    /** MTU que se pide al conectar (la mascota ofrece 247). */
    const val PREFERRED_MTU = 247

    /** Bytes que el protocolo ATT gasta en la cabecera de cada escritura. */
    const val ATT_HEADER_BYTES = 3

    /** MTU por defecto de BLE: sin negociar, solo caben 20 bytes por escritura. */
    const val DEFAULT_MTU = 23

    /** Máximo de bytes de un mensaje FACE que acepta el firmware. */
    const val MAX_FACE_BYTES = 111

    /** Máximo de caracteres del texto: el firmware admite 40, pero recomienda 36 para no perder el final al partir. */
    const val MAX_TEXT_CHARS = 36

    private const val SOUND_MIN = 1
    private const val SOUND_MAX = 5
    private const val BUTTON_PREFIX = "BTN:"
    private const val PONG_PREFIX = "PONG:"
    private const val LETTERS = "ABCDE"

    /** Bytes que caben en una escritura con el [mtu] negociado, sin pasar del límite del firmware. */
    fun payloadLimit(mtu: Int): Int = (mtu - ATT_HEADER_BYTES).coerceIn(DEFAULT_MTU - ATT_HEADER_BYTES, MAX_FACE_BYTES)

    /**
     * Mensaje FACE: `E:<expresion>|T:<texto>`, o `E:<expresion>` si no hay texto (la mascota borra el texto).
     * El texto se limpia (saltos y tabuladores pasan a espacio, `|` a espacio), se corta a [MAX_TEXT_CHARS] caracteres
     * y, si aun así el mensaje no cabe en [maxBytes], se recorta por el final sin partir un carácter.
     */
    fun encodeFace(expression: PetExpression, text: String = "", maxBytes: Int = MAX_FACE_BYTES): ByteArray {
        val limit = maxBytes.coerceIn(DEFAULT_MTU - ATT_HEADER_BYTES, MAX_FACE_BYTES)
        val head = "E:${expression.wire}"
        var body = cleanText(text)
        while (body.isNotEmpty() && utf8Size("$head|T:$body") > limit) {
            body = body.substring(0, body.offsetByCodePoints(body.length, -1)).trimEnd()
        }
        val message = if (body.isEmpty()) head else "$head|T:$body"
        return message.toByteArray(Charsets.UTF_8)
    }

    /** Un solo byte con el código del sonido. */
    fun encodeCue(sound: PetSound): ByteArray = byteArrayOf(sound.code.toByte())

    /** El sonido para un código, o `null` si está fuera de 1 a 5. */
    fun soundOf(code: Int): PetSound? = if (code in SOUND_MIN..SOUND_MAX) PetSound.entries.firstOrNull { it.code == code } else null

    /** Lee un mensaje de EVENT. Devuelve `null` si el texto no es ninguno de los conocidos. */
    fun parseEvent(bytes: ByteArray): PetEvent? = parseEvent(String(bytes, Charsets.UTF_8))

    fun parseEvent(raw: String): PetEvent? {
        val text = raw.trim().trim('\u0000')
        return when {
            text == "BTN:TALK" -> PetEvent.Talk
            text == "BTN:OK" -> PetEvent.Ok
            text.startsWith(BUTTON_PREFIX) -> {
                val letter = text.removePrefix(BUTTON_PREFIX)
                val index = if (letter.length == 1) LETTERS.indexOf(letter[0]) else -1
                if (index >= 0) PetEvent.Option(index) else null
            }
            text.startsWith(PONG_PREFIX) -> PetEvent.Pong(text.removePrefix(PONG_PREFIX))
            else -> null
        }
    }

    /** Nombre corto del botón de un evento, para la pantalla de prueba ("A", "TALK", "OK"). */
    fun buttonLabel(event: PetEvent): String = when (event) {
        is PetEvent.Option -> LETTERS.getOrNull(event.index)?.toString() ?: "?"
        PetEvent.Talk -> "TALK"
        PetEvent.Ok -> "OK"
        is PetEvent.Pong -> "PONG"
    }

    /** Lee STATUS (`FW:1.0.0|BAT:NA`). Devuelve `null` si falta el campo `FW`. */
    fun parseStatus(bytes: ByteArray): PetStatus? = parseStatus(String(bytes, Charsets.UTF_8))

    fun parseStatus(raw: String): PetStatus? {
        val fields = raw.trim().trim('\u0000').split('|').mapNotNull { part ->
            val at = part.indexOf(':')
            if (at <= 0) null else part.substring(0, at).trim() to part.substring(at + 1).trim()
        }.toMap()
        val firmware = fields["FW"]?.takeIf { it.isNotEmpty() } ?: return null
        val battery = fields["BAT"]?.toIntOrNull()?.takeIf { it in 0..100 }
        return PetStatus(firmware, battery)
    }

    private fun cleanText(text: String): String {
        val flattened = buildString(text.length) {
            for (ch in text) append(if (ch == '|' || Character.isISOControl(ch) || ch.isWhitespace()) ' ' else ch)
        }
        val collapsed = flattened.trim().replace(Regex(" +"), " ")
        if (collapsed.codePointCount(0, collapsed.length) <= MAX_TEXT_CHARS) return collapsed
        return collapsed.substring(0, collapsed.offsetByCodePoints(0, MAX_TEXT_CHARS)).trimEnd()
    }

    private fun utf8Size(text: String): Int = text.toByteArray(Charsets.UTF_8).size
}
