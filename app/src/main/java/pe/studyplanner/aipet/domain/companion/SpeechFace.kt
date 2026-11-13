package pe.studyplanner.aipet.domain.companion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pe.studyplanner.aipet.domain.tutor.TutorState
import pe.studyplanner.aipet.domain.voice.SpeechActivityListener

/**
 * Reglas puras de la cara «hablando» de la mascota física: qué texto se muestra mientras suena la voz y a qué cara
 * se vuelve cuando termina. La cara `hablar` (boca que se mueve) solo sale de aquí, mientras la voz suena; ninguna
 * señal del tutor ([PetFeedback]) la usa. Kotlin puro (sin Android) para poder probarlo en la JVM.
 */
object SpeechFace {
    /** Los avisos de voz que llegan con menos de esto entre sí se funden en un solo cambio de cara. */
    const val DEBOUNCE_MS = 150L

    /** Texto máximo en la OLED: el límite recomendado del protocolo, para no perder el final al partir en 2 líneas. */
    const val MAX_TEXT_CHARS = PetProtocol.MAX_TEXT_CHARS

    /** Cara neutra, sin texto: a la que se vuelve cuando no hay un estado del tutor que restaurar (por ejemplo, en el chat). */
    val IDLE = PetSignal(PetExpression.IDLE, "")

    /** Cara «hablando» con el principio de lo que se lee. Nunca lleva sonido. */
    fun speakingSignal(utterance: String, maxChars: Int = MAX_TEXT_CHARS): PetSignal =
        PetSignal(PetExpression.HABLAR, excerpt(utterance, maxChars))

    /**
     * Cara a la que se vuelve al terminar la voz: la que corresponde al estado actual del tutor, o la neutra si no hay
     * tutor. Sin sonido: el aviso de acierto, error o pista ya sonó cuando cambió el estado y no debe repetirse.
     */
    fun restoreSignal(tutor: TutorState?): PetSignal =
        if (tutor == null) IDLE else PetFeedback.signalFor(tutor).copy(sound = null)

    /**
     * El principio de [text] en a lo sumo [maxChars] caracteres, cortado en un límite de palabra. Los espacios, saltos y
     * `|` pasan a un solo espacio. Si una sola palabra es más larga que el límite se corta en seco, sin partir un
     * carácter de dos unidades UTF-16. Si hubo corte, no termina en espacio ni en `, ; : -`.
     */
    fun excerpt(text: String, maxChars: Int = MAX_TEXT_CHARS): String {
        val limit = maxChars.coerceAtLeast(1)
        val clean = flatten(text)
        if (clean.codePointCount(0, clean.length) <= limit) return clean
        val cut = clean.offsetByCodePoints(0, limit)
        val head = clean.substring(0, cut)
        // Si el primer carácter que ya no cabe es un espacio, la última palabra entró completa.
        val boundary = if (clean[cut] == ' ') cut else head.lastIndexOf(' ')
        val body = if (boundary > 0) clean.substring(0, boundary) else head
        return body.trimEnd(' ', ',', ';', ':', '-')
    }

    private fun flatten(text: String): String {
        val flattened = buildString(text.length) {
            for (ch in text) append(if (ch == '|' || Character.isISOControl(ch) || ch.isWhitespace()) ' ' else ch)
        }
        return flattened.trim().replace(Regex(" +"), " ")
    }
}

/**
 * Sigue a la voz con la cara de la mascota física: al empezar a leer muestra «hablando» con el principio del texto y,
 * al terminar (fin, error, detener, cancelar, cerrar), vuelve a la cara que da [restore].
 *
 * Reglas que cumple:
 *  - Hilo principal: [VoiceSession] ya entrega sus avisos en el hilo principal (`AndroidVoicePlatform` los pasa por un
 *    `Handler` antes de llegar), y [PetLink] exige el hilo principal. [scope] debe ser un alcance del hilo principal.
 *  - Los avisos que llegan con menos de [debounceMillis] entre sí se funden: solo se escribe la última intención.
 *    Una lectura que empieza y termina dentro de esa ventana no manda nada; dos lecturas seguidas no pasan por la cara
 *    de reposo. Si los avisos no pararan nunca, la escritura se aplazaría mientras tanto (no ocurre con una voz real).
 *  - Sin mascota conectada no hace nada: ni siquiera programa la espera.
 *  - Solo se restaura la cara si de verdad se mostró «hablando»; nunca se manda un sonido.
 *
 * @param restore cara a la que volver; se evalúa al momento de restaurar, así refleja el estado de ese momento.
 * @param wait espera antes de escribir (se cambia en las pruebas para no depender del reloj real).
 */
class SpeechFaceSync(
    private val petLink: PetLink,
    private val scope: CoroutineScope,
    private val restore: () -> PetSignal = { SpeechFace.IDLE },
    private val debounceMillis: Long = SpeechFace.DEBOUNCE_MS,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) : SpeechActivityListener {
    private var wanted: PetSignal? = null
    private var shown = false
    private var pending: Job? = null
    private var released = false

    override fun onSpeechStarted(text: String) = request(SpeechFace.speakingSignal(text))

    override fun onSpeechEnded() = request(null)

    /**
     * Se cierra la pantalla que usaba la voz. Cancela lo pendiente y, si la cara «hablando» quedó puesta, la quita al
     * instante ([restoreFace] = false cuando quien llama manda enseguida su propia cara). Después ignora todo aviso.
     */
    fun release(restoreFace: Boolean = true) {
        if (released) return
        released = true
        pending?.cancel()
        pending = null
        wanted = null
        if (shown && restoreFace && petLink.state.value.connected) show(restore())
        shown = false
    }

    private fun request(signal: PetSignal?) {
        if (released) return
        wanted = signal
        pending?.cancel()
        pending = null
        // Nada que hacer: sin mascota no hay a quién mostrar «hablando», y sin «hablando» puesto no hay qué restaurar.
        val needed = if (signal != null) petLink.state.value.connected else shown
        if (!needed) return
        pending = scope.launch {
            wait(debounceMillis)
            flush()
        }
    }

    private fun flush() {
        pending = null
        val target = wanted
        val connected = petLink.state.value.connected
        if (target != null) {
            if (!connected) return
            show(target)
            shown = true
        } else if (shown) {
            shown = false
            if (connected) show(restore())
        }
    }

    private fun show(signal: PetSignal) = petLink.showFace(signal.expression, signal.text)
}
