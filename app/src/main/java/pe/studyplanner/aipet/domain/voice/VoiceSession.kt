package pe.studyplanner.aipet.domain.voice

import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.studyplanner.aipet.domain.profile.ExtendedGraphemes

enum class VoicePhase { IDLE, REQUESTING, LISTENING, TRANSCRIBING, REVIEW, PREPARING_SPEECH, SPEAKING, ERROR }

data class VoiceCapabilities(
    val speechAvailable: Boolean = false,
    val onDeviceAvailable: Boolean = false,
    val ttsEngineAvailable: Boolean = false,
)

data class VoiceState(
    val capabilities: VoiceCapabilities,
    val phase: VoicePhase = VoicePhase.IDLE,
    val transcript: String = "",
    val partial: String = "",
    val message: String = "",
    val readAloudEnabled: Boolean = false,
    val speechRate: Float = 1f,
    val speechVolume: Float = 1f,
)

/** Main-thread callbacks. An adapter must never persist audio or transcripts. */
interface RecognitionEvents {
    fun onReady()
    fun onEndOfSpeech()
    fun onPartial(text: String)
    fun onResult(text: String)
    fun onError(message: String)
}

interface SpeechEvents {
    fun onStart()
    fun onDone()
    fun onError(message: String)
}

/**
 * Aviso opcional, en el hilo principal, de que la lectura en voz empezó o terminó. Sirve para que otra parte de la
 * app (la cara de la mascota física) siga a la voz sin conocer el motor. [onSpeechEnded] llega una vez por cada
 * [onSpeechStarted], sea cual sea la salida: fin normal, error, detener, cancelar, cerrar o límite de tiempo.
 */
interface SpeechActivityListener {
    /** Ya suena la voz. [text] es el texto que se lee (normalizado). */
    fun onSpeechStarted(text: String)

    /** Dejó de sonar o nunca llegó a sonar después de un aviso de inicio. */
    fun onSpeechEnded()
}

interface VoicePlatform {
    fun capabilities(): VoiceCapabilities
    fun startRecognition(onDevice: Boolean, listener: RecognitionEvents)
    fun stopRecognition()
    fun speak(text: String, listener: SpeechEvents)
    fun configureSpeech(rate: Float, volume: Float) = Unit
    fun stopSpeech()
    /** Release both engines; subsequent explicit requests may create new instances. */
    fun release()
}

/**
 * Foreground, single-shot voice boundary. It has no agent, database or scheduler reference:
 * a recognized phrase can only become a reviewable draft, never an executed instruction.
 * Public methods and platform callbacks are called on the main thread in production.
 */
class VoiceSession(
    private val platform: VoicePlatform,
    private val scope: CoroutineScope,
    private val activity: SpeechActivityListener? = null,
) {
    /** Estado observable. Toda escritura pasa por [trackSpeech]: ninguna salida de SPEAKING se queda sin avisar. */
    private inner class Tracked(initial: VoiceState) {
        private val flow = MutableStateFlow(initial)
        var value: VoiceState
            get() = flow.value
            set(next) {
                val previous = flow.value
                flow.value = next
                trackSpeech(previous, next)
            }
        fun asStateFlow(): StateFlow<VoiceState> = flow.asStateFlow()
    }

    private val mutable = Tracked(VoiceState(VoiceCapabilities()))
    val state: StateFlow<VoiceState> = mutable.asStateFlow()
    private var generation = 0L
    private var deadline: Job? = null
    private var closed = false
    private var utterance = ""

    init { refresh() }

    fun refresh() {
        if (closed) return
        mutable.value = mutable.value.copy(capabilities = runCatching { platform.capabilities() }.getOrDefault(VoiceCapabilities()))
    }

    fun startDictation(hasPermission: Boolean, onDevice: Boolean = false, wakeName: String? = null) {
        if (closed || mutable.value.phase in recognitionPhases) return
        invalidate()
        platform.stopSpeech()
        platform.stopRecognition()
        refresh()
        mutable.value = mutable.value.copy(transcript = "", partial = "", message = "")
        if (!hasPermission) return fail("Permite el micrófono para dictar, o continúa escribiendo.")
        if (onDevice && !mutable.value.capabilities.onDeviceAvailable) {
            return fail("No hay reconocimiento en el dispositivo disponible. Puedes escribir tu solicitud.")
        }
        if (!onDevice && !mutable.value.capabilities.speechAvailable) {
            return fail("No hay un servicio de reconocimiento instalado. Puedes escribir tu solicitud.")
        }
        val wake = wakeName?.let(::normalizeText)
        if (wake != null && (wake.isBlank() || ExtendedGraphemes.count(wake) > 24 || wakeName.orEmpty().any(Character::isISOControl))) {
            return fail("El nombre para esta escucha debe tener de 1 a 24 caracteres visibles, sin controles.")
        }
        val token = generation
        mutable.value = mutable.value.copy(phase = VoicePhase.REQUESTING,
            message = "Preparando el micrófono. La sesión dura como máximo 15 segundos.")
        watchdog(token, DICTATION_TIMEOUT_MS, "La sesión de voz terminó al alcanzar 15 segundos. Puedes intentarlo otra vez o escribir.")
        try {
            platform.startRecognition(onDevice, object : RecognitionEvents {
                override fun onReady() {
                    if (!live(token) || mutable.value.phase != VoicePhase.REQUESTING) return
                    mutable.value = mutable.value.copy(phase = VoicePhase.LISTENING,
                        message = if (wake == null) "Escuchando. Después podrás revisar el texto."
                        else "Escuchando una vez: di «$wake» al principio y luego tu solicitud.")
                }
                override fun onEndOfSpeech() {
                    if (!live(token) || mutable.value.phase != VoicePhase.LISTENING) return
                    mutable.value = mutable.value.copy(phase = VoicePhase.TRANSCRIBING, message = "Esperando la transcripción del servicio.")
                }
                override fun onPartial(text: String) {
                    if (!live(token) || mutable.value.phase !in setOf(VoicePhase.LISTENING, VoicePhase.TRANSCRIBING)) return
                    mutable.value = mutable.value.copy(partial = normalizeText(text).take(MAX_TRANSCRIPT_CHARS))
                }
                override fun onResult(text: String) {
                    if (!live(token)) return
                    val normalized = normalizeText(text)
                    if (normalized.length > MAX_TRANSCRIPT_CHARS) return finishError("La transcripción supera 4000 caracteres. Dicta una solicitud más breve.")
                    val result = if (wake == null) normalized else stripWakePrefix(normalized, wake)
                        ?: return finishError("No se reconoció el nombre al principio. No se envió ninguna solicitud; vuelve a intentarlo o escribe.")
                    if (result.isBlank()) return finishError("No se reconoció una solicitud. Puedes intentarlo otra vez o escribir.")
                    invalidate()
                    platform.stopRecognition()
                    mutable.value = mutable.value.copy(phase = VoicePhase.REVIEW, transcript = result, partial = "",
                        message = "Revisa la transcripción. No se ha enviado al chat ni ejecutado ninguna acción.")
                }
                override fun onError(message: String) {
                    if (live(token)) finishError(message.ifBlank { "No se pudo reconocer la voz. Puedes continuar escribiendo." })
                }
            })
        } catch (_: SecurityException) {
            if (live(token)) finishError("El permiso de micrófono no está disponible. Puedes continuar escribiendo.")
        } catch (_: Exception) {
            if (live(token)) finishError("No se pudo iniciar el reconocimiento. Puedes continuar escribiendo.")
        }
    }

    fun cancel() {
        if (closed) return
        invalidate()
        platform.release()
        resting("Sesión de voz cancelada. No se envió ninguna solicitud.")
    }

    fun discardTranscript() {
        if (closed) return
        invalidate()
        platform.release()
        mutable.value = mutable.value.copy(phase = VoicePhase.IDLE, transcript = "", partial = "", message = "Transcripción descartada.")
    }

    /** Explicit UI transfer only. A second call cannot reuse the consumed transcript. */
    fun takeTranscript(): String? {
        if (closed || mutable.value.phase !in setOf(VoicePhase.REVIEW, VoicePhase.ERROR) || mutable.value.transcript.isBlank()) return null
        val result = mutable.value.transcript
        invalidate()
        mutable.value = mutable.value.copy(phase = VoicePhase.IDLE, transcript = "", partial = "", message = "Texto transferido al borrador. Revísalo antes de enviar.")
        return result
    }

    fun setReadAloudEnabled(enabled: Boolean) {
        if (closed) return
        mutable.value = mutable.value.copy(readAloudEnabled = enabled)
        if (!enabled) stopSpeaking()
    }

    fun setSpeechRate(rate: Float) {
        if (closed || !rate.isFinite()) return
        val bounded = rate.coerceIn(0.5f, 1.5f)
        if (bounded == mutable.value.speechRate) return
        stopSpeaking()
        mutable.value = mutable.value.copy(speechRate = bounded, message = "Velocidad actualizada. Pulsa Leer para aplicarla.")
        platform.configureSpeech(bounded, mutable.value.speechVolume)
    }

    fun setSpeechVolume(volume: Float) {
        if (closed || !volume.isFinite()) return
        val bounded = volume.coerceIn(0f, 1f)
        if (bounded == mutable.value.speechVolume) return
        stopSpeaking()
        mutable.value = mutable.value.copy(speechVolume = bounded, message = "Volumen actualizado. Pulsa Leer para aplicarlo.")
        platform.configureSpeech(mutable.value.speechRate, bounded)
    }

    fun speak(text: String) {
        if (closed) return
        if (!mutable.value.readAloudEnabled) {
            mutable.value = mutable.value.copy(message = "Activa la lectura en voz y pulsa Leer para escuchar el texto.")
            return
        }
        if (mutable.value.phase in recognitionPhases) {
            mutable.value = mutable.value.copy(message = "Termina o cancela el dictado antes de leer en voz.")
            return
        }
        val readable = normalizeText(text)
        if (readable.isBlank()) return fail("No hay texto para leer.")
        if (readable.length > MAX_SPEECH_CHARS) return fail("La lectura admite hasta 3900 caracteres por solicitud. El texto completo se conserva en pantalla.")
        invalidate()
        platform.stopSpeech()
        platform.stopRecognition()
        refresh()
        if (!mutable.value.capabilities.ttsEngineAvailable) return fail("No hay un motor de lectura disponible. El texto permanece en pantalla.")
        val token = generation
        utterance = readable
        mutable.value = mutable.value.copy(phase = VoicePhase.PREPARING_SPEECH, partial = "", message = "Preparando la lectura. El micrófono está apagado.")
        watchdog(token, DICTATION_TIMEOUT_MS, "El motor de lectura no respondió. El texto permanece en pantalla.")
        try {
            platform.configureSpeech(mutable.value.speechRate, mutable.value.speechVolume)
            platform.speak(readable, object : SpeechEvents {
                override fun onStart() {
                    if (!live(token) || mutable.value.phase != VoicePhase.PREPARING_SPEECH) return
                    mutable.value = mutable.value.copy(phase = VoicePhase.SPEAKING, message = "Leyendo en voz. Puedes silenciar en cualquier momento.")
                    watchdog(token, SPEECH_TIMEOUT_MS, "La lectura alcanzó su límite de dos minutos. El texto permanece en pantalla.")
                }
                override fun onDone() {
                    if (!live(token)) return
                    invalidate()
                    platform.stopSpeech()
                    resting("Lectura terminada. El micrófono sigue apagado.")
                }
                override fun onError(message: String) {
                    if (live(token)) finishError(message.ifBlank { "No se pudo leer el texto. Puedes seguir leyéndolo en pantalla." })
                }
            })
        } catch (_: Exception) {
            if (live(token)) finishError("No se pudo iniciar la lectura. El texto permanece en pantalla.")
        }
    }

    fun stopSpeaking() {
        if (closed) return
        if (mutable.value.phase in speechPhases) {
            invalidate()
            platform.stopSpeech()
            resting("Lectura detenida. El micrófono sigue apagado.")
        } else platform.stopSpeech()
    }

    fun suspendSession() {
        if (closed) return
        invalidate()
        platform.release()
        resting("Voz detenida al ocultar la aplicación o la mascota. Pulsa el botón para iniciar otra sesión.")
    }

    fun close() {
        if (closed) return
        closed = true
        invalidate()
        platform.release()
        mutable.value = mutable.value.copy(phase = VoicePhase.IDLE, transcript = "", partial = "", message = "Sesión cerrada.")
    }

    private fun live(token: Long) = !closed && token == generation

    private fun trackSpeech(previous: VoiceState, next: VoiceState) {
        val listener = activity ?: return
        val wasSpeaking = previous.phase == VoicePhase.SPEAKING
        val isSpeaking = next.phase == VoicePhase.SPEAKING
        if (wasSpeaking == isSpeaking) return
        // El aviso es accesorio (mascota física): un fallo suyo nunca debe afectar a la voz.
        runCatching { if (isSpeaking) listener.onSpeechStarted(utterance) else listener.onSpeechEnded() }
    }

    private fun invalidate() { generation++; deadline?.cancel(); deadline = null }
    private fun resting(message: String) {
        mutable.value = mutable.value.copy(phase = if (mutable.value.transcript.isBlank()) VoicePhase.IDLE else VoicePhase.REVIEW,
            partial = "", message = message)
    }
    private fun fail(message: String) { mutable.value = mutable.value.copy(phase = VoicePhase.ERROR, partial = "", message = message) }
    private fun finishError(message: String) { invalidate(); platform.release(); fail(message) }
    private fun watchdog(token: Long, millis: Long, message: String) {
        deadline?.cancel()
        deadline = scope.launch { delay(millis); if (live(token)) finishError(message) }
    }

    companion object {
        const val DICTATION_TIMEOUT_MS = 15_000L
        const val SPEECH_TIMEOUT_MS = 120_000L
        const val MAX_TRANSCRIPT_CHARS = 4000
        const val MAX_SPEECH_CHARS = 3900
        private val recognitionPhases = setOf(VoicePhase.REQUESTING, VoicePhase.LISTENING, VoicePhase.TRANSCRIBING)
        private val speechPhases = setOf(VoicePhase.PREPARING_SPEECH, VoicePhase.SPEAKING)
        private fun normalizeText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
            .map { if (it.isWhitespace()) ' ' else it }.filterNot(Character::isISOControl).joinToString("").trim().replace(Regex(" +"), " ")
        private fun stripWakePrefix(text: String, name: String): String? {
            val normalized = text.lowercase(Locale.ROOT)
            val prefix = name.lowercase(Locale.ROOT)
            if (!normalized.startsWith(prefix)) return null
            if (normalized.length == prefix.length) return ""
            val next = normalized.codePointAt(prefix.length)
            if (Character.isLetterOrDigit(next) || Character.getType(next) in setOf(Character.NON_SPACING_MARK.toInt(),
                    Character.COMBINING_SPACING_MARK.toInt(), Character.CONNECTOR_PUNCTUATION.toInt(), Character.FORMAT.toInt())) return null
            // ROOT lowercasing can expand a code point (for example İ → i + dot).
            // Locate the matched original prefix without lowercasing the user's request.
            var originalEnd = 0
            while (originalEnd < text.length && text.substring(0, originalEnd).lowercase(Locale.ROOT).length < prefix.length) {
                originalEnd += Character.charCount(text.codePointAt(originalEnd))
            }
            if (text.substring(0, originalEnd).lowercase(Locale.ROOT) != prefix) return null
            return text.substring(originalEnd).trimStart { it.isWhitespace() || it in ",:;.!?¡¿—–-" }
        }
    }
}
