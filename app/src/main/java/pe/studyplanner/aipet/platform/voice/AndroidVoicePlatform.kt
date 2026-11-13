package pe.studyplanner.aipet.platform.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import pe.studyplanner.aipet.domain.voice.RecognitionEvents
import pe.studyplanner.aipet.domain.voice.SpeechEvents
import pe.studyplanner.aipet.domain.voice.VoiceCapabilities
import pe.studyplanner.aipet.domain.voice.VoicePlatform

/** Native engines only. No engine installation, audio file, network client or transcript log. */
class AndroidVoicePlatform(context: Context) : VoicePlatform {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var speech: TextToSpeech? = null
    private var recognitionGeneration = 0L
    private var speechGeneration = 0L
    private var speechRate = 1f
    private var speechVolume = 1f
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var recognitionFocus: AudioFocusRequest? = null
    private var speechFocus: AudioFocusRequest? = null

    override fun capabilities(): VoiceCapabilities {
        assertMain()
        return VoiceCapabilities(
            speechAvailable = runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false),
            onDeviceAvailable = Build.VERSION.SDK_INT >= 31 && runCatching {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
            }.getOrDefault(false),
            // Visibility is declared with <queries>; availability does not promise installed Spanish data.
            ttsEngineAvailable = runCatching {
                appContext.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0).isNotEmpty()
            }.getOrDefault(false),
        )
    }

    override fun startRecognition(onDevice: Boolean, listener: RecognitionEvents) {
        assertMain()
        stopSpeech()
        stopRecognition()
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.onError("El permiso de micrófono no está concedido. Puedes continuar escribiendo.")
            return
        }
        if (callInProgress()) {
            listener.onError("El audio está ocupado por una llamada o comunicación. Continúa escribiendo y vuelve a dictar después.")
            return
        }
        val token = recognitionGeneration
        try {
            val engine = if (onDevice) {
                if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
                    listener.onError("El reconocimiento en el dispositivo no está disponible. Puedes continuar escribiendo.")
                    return
                }
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                    listener.onError("No hay un servicio de reconocimiento instalado. Puedes continuar escribiendo.")
                    return
                }
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
            recognizer = engine
            engine.setRecognitionListener(object : RecognitionListener {
                private fun live() = token == recognitionGeneration && recognizer === engine
                override fun onReadyForSpeech(params: Bundle?) { if (live()) listener.onReady() }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { if (live()) listener.onEndOfSpeech() }
                override fun onError(error: Int) { if (live()) listener.onError(recognitionError(error)) }
                override fun onResults(results: Bundle?) {
                    if (live()) listener.onResult(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (live()) listener.onPartial(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            val focus = acquireFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE) {
                if (token == recognitionGeneration && recognizer === engine) {
                    stopRecognition()
                    listener.onError("Otra aplicación o una llamada interrumpió el dictado. El micrófono se detuvo; puedes continuar escribiendo.")
                }
            }
            if (focus == null) {
                listener.onError("Android no concedió el uso exclusivo del audio. Puedes continuar escribiendo o intentarlo después.")
                return
            }
            recognitionFocus = focus
            engine.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                if (onDevice) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
        } catch (_: SecurityException) {
            if (token == recognitionGeneration) listener.onError("Android bloqueó el micrófono. Revisa el permiso o continúa escribiendo.")
        } catch (_: Exception) {
            if (token == recognitionGeneration) listener.onError("No se pudo iniciar el servicio de reconocimiento. Puedes continuar escribiendo.")
        }
    }

    override fun stopRecognition() {
        assertMain()
        recognitionGeneration++
        val previous = recognizer
        recognizer = null
        val previousFocus = recognitionFocus
        recognitionFocus = null
        // Destroy rather than stopListening: cancellation must never request a final result.
        if (previous != null) {
            runCatching { previous.cancel() }
            runCatching { previous.destroy() }
        }
        if (previousFocus != null) runCatching { audioManager?.abandonAudioFocusRequest(previousFocus) }
    }

    override fun speak(text: String, listener: SpeechEvents) {
        assertMain()
        stopRecognition()
        stopSpeech()
        if (callInProgress()) {
            listener.onError("El audio está ocupado por una llamada o comunicación. El texto permanece en pantalla.")
            return
        }
        if (text.length > TextToSpeech.getMaxSpeechInputLength()) {
            listener.onError("El motor no admite un texto tan largo. El texto completo permanece en pantalla.")
            return
        }
        val token = speechGeneration
        val utterance = UUID.randomUUID().toString()
        try {
            val focus = acquireFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) {
                if (token == speechGeneration) {
                    stopSpeech()
                    listener.onError("Otra aplicación o una llamada interrumpió la lectura. El texto permanece en pantalla.")
                }
            }
            if (focus == null) {
                listener.onError("Android no concedió el audio para leer. El texto permanece en pantalla.")
                return
            }
            speechFocus = focus
            // Posting also handles a hypothetical synchronous constructor callback safely.
            val engine = TextToSpeech(appContext) { status ->
                main.post {
                    if (token == speechGeneration) {
                        val current = speech
                        if (status != TextToSpeech.SUCCESS || current == null) {
                            listener.onError("El motor de lectura no pudo iniciarse. El texto permanece en pantalla.")
                        } else configureAndSpeak(current, token, utterance, text, listener)
                    }
                }
            }
            if (token == speechGeneration) speech = engine else engine.shutdown()
        } catch (_: Exception) {
            if (token == speechGeneration) listener.onError("No se pudo abrir el motor de lectura. El texto permanece en pantalla.")
        }
    }

    private fun configureAndSpeak(engine: TextToSpeech, token: Long, utterance: String, text: String, listener: SpeechEvents) {
        if (token != speechGeneration || speech !== engine) return
        try {
            if (engine.setAudioAttributes(speechAttributes()) != TextToSpeech.SUCCESS) {
                listener.onError("El motor no pudo preparar el audio. El texto permanece en pantalla.")
                return
            }
            // Select only already installed, non-network Spanish voices. Do not launch any download UI.
            val localSpanish = engine.voices.orEmpty().filter { voice ->
                voice.locale.language.equals("es", ignoreCase = true) && !voice.isNetworkConnectionRequired &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features.orEmpty()
            }.sortedWith(compareBy<android.speech.tts.Voice> { voiceRegionRank(it.locale.country) }
                .thenByDescending { it.quality }.thenBy { it.latency }.thenBy { it.name }).firstOrNull()
            val voiceSelected = if (localSpanish != null) engine.setVoice(localSpanish) == TextToSpeech.SUCCESS else {
                // Some older engines enumerate voices only after selecting an installed language.
                listOf(Locale("es", "PE"), Locale("es", "ES"), Locale("es", "US")).any { locale ->
                    val language = engine.setLanguage(locale)
                    val selected = engine.voice
                    language >= TextToSpeech.LANG_AVAILABLE && selected != null &&
                        selected.locale.language.equals("es", ignoreCase = true) && !selected.isNetworkConnectionRequired &&
                        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in selected.features.orEmpty()
                }
            }
            if (!voiceSelected || engine.setSpeechRate(speechRate) != TextToSpeech.SUCCESS) {
                listener.onError("No hay una voz española local instalada. No se descargaron voces; el texto permanece en pantalla.")
                return
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                private fun deliver(id: String?, event: () -> Unit) {
                    main.post { if (id == utterance && token == speechGeneration && speech === engine) event() }
                }
                override fun onStart(utteranceId: String?) = deliver(utteranceId, listener::onStart)
                override fun onDone(utteranceId: String?) = deliver(utteranceId, listener::onDone)
                @Deprecated("Android callback retained for older engines")
                override fun onError(utteranceId: String?) = deliver(utteranceId) {
                    listener.onError("Falló la lectura del motor de voz. El texto permanece en pantalla.")
                }
                override fun onError(utteranceId: String?, errorCode: Int) = deliver(utteranceId) {
                    listener.onError("Falló la lectura del motor de voz. El texto permanece en pantalla.")
                }
                override fun onStop(utteranceId: String?, interrupted: Boolean) = deliver(utteranceId) {
                    listener.onError("El motor interrumpió la lectura. El texto permanece en pantalla.")
                }
            })
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, speechVolume)
                }, utterance) == TextToSpeech.ERROR) {
                listener.onError("El motor rechazó la lectura. El texto permanece en pantalla.")
            }
        } catch (_: Exception) {
            if (token == speechGeneration) listener.onError("No se pudo preparar una voz española local. El texto permanece en pantalla.")
        }
    }

    override fun stopSpeech() {
        assertMain()
        speechGeneration++
        val previous = speech
        speech = null
        val previousFocus = speechFocus
        speechFocus = null
        if (previous != null) {
            runCatching { previous.stop() }
            runCatching { previous.shutdown() }
        }
        if (previousFocus != null) runCatching { audioManager?.abandonAudioFocusRequest(previousFocus) }
    }

    override fun configureSpeech(rate: Float, volume: Float) {
        assertMain()
        if (rate.isFinite()) speechRate = rate.coerceIn(0.5f, 1.5f)
        if (volume.isFinite()) speechVolume = volume.coerceIn(0f, 1f)
    }

    override fun release() { assertMain(); stopRecognition(); stopSpeech() }
    private fun assertMain() = check(Looper.myLooper() == Looper.getMainLooper()) { "Los motores de voz requieren el hilo principal." }

    private fun callInProgress(): Boolean = runCatching {
        audioManager?.mode in setOf(AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE)
    }.getOrDefault(true)

    private fun speechAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

    private fun acquireFocus(gain: Int, onLost: () -> Unit): AudioFocusRequest? {
        val manager = audioManager ?: return null
        val request = AudioFocusRequest.Builder(gain).setAudioAttributes(speechAttributes())
            .setAcceptsDelayedFocusGain(false).setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) onLost()
            }, main).build()
        return if (manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) request else null
    }

    private fun recognitionError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "No se pudo acceder al micrófono. Cierra otras grabaciones o continúa escribiendo."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "El permiso de micrófono no está disponible. Puedes continuar escribiendo."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "El servicio de reconocimiento no pudo conectarse. Puedes escribir o usar un servicio en el dispositivo si está disponible."
        SpeechRecognizer.ERROR_NO_MATCH -> "El servicio no reconoció una frase. Puedes intentarlo otra vez o escribir."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz. El micrófono se ha detenido; puedes volver a intentarlo."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El servicio de voz está ocupado. Espera un momento o continúa escribiendo."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "El servicio no dispone de español de Perú. Puedes continuar escribiendo; no se descargaron idiomas."
        SpeechRecognizer.ERROR_CLIENT -> "La sesión de reconocimiento se interrumpió. Puedes volver a intentarlo o escribir."
        else -> "El servicio de reconocimiento falló. Puedes volver a intentarlo o continuar escribiendo."
    }
}

/** Acento latinoamericano primero (Perú, luego otros), España después. */
internal fun voiceRegionRank(country: String): Int = when (country.uppercase(Locale.ROOT)) {
    "PE" -> 0
    "US", "MX", "419", "CO", "CL", "AR", "EC", "BO" -> 1
    "ES" -> 3
    else -> 2
}
