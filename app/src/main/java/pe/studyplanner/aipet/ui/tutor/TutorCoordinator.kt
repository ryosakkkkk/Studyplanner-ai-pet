package pe.studyplanner.aipet.ui.tutor

import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.studyplanner.aipet.data.tutor.TutorHistory
import pe.studyplanner.aipet.domain.academic.AcademicRepository
import pe.studyplanner.aipet.domain.academic.DatasetScope
import pe.studyplanner.aipet.domain.academic.StaleAcademicWriteException
import pe.studyplanner.aipet.domain.companion.PetEvent
import pe.studyplanner.aipet.domain.companion.PetExpression
import pe.studyplanner.aipet.domain.companion.PetFeedback
import pe.studyplanner.aipet.domain.companion.PetLink
import pe.studyplanner.aipet.domain.companion.PetSignal
import pe.studyplanner.aipet.domain.companion.SpeechFace
import pe.studyplanner.aipet.domain.companion.SpeechFaceSync
import pe.studyplanner.aipet.domain.tutor.AttemptMode
import pe.studyplanner.aipet.domain.tutor.Phase
import pe.studyplanner.aipet.domain.tutor.ReviewProposal
import pe.studyplanner.aipet.domain.tutor.SessionConfig
import pe.studyplanner.aipet.domain.tutor.Topic
import pe.studyplanner.aipet.domain.tutor.TutorAction
import pe.studyplanner.aipet.domain.tutor.TutorClock
import pe.studyplanner.aipet.domain.tutor.TutorCatalog
import pe.studyplanner.aipet.domain.tutor.TutorContent
import pe.studyplanner.aipet.domain.tutor.TutorReviewPlanner
import pe.studyplanner.aipet.domain.tutor.TutorSession
import pe.studyplanner.aipet.domain.tutor.TutorState

/** Propuesta de repaso al final de la sesión. Nada se guarda en la agenda hasta que el estudiante confirma. */
sealed interface ReviewUi {
    /** Sin propuesta (no hubo comprobación) o ya no hay nada que mostrar. */
    data object None : ReviewUi

    /**
     * @property dueOn día sugerido.
     * @property canSchedule `false` si la app no tiene agenda disponible: solo se informa la sugerencia.
     */
    data class Offer(
        val proposal: ReviewProposal,
        val dueOn: LocalDate,
        val startHour: Int,
        val minutes: Int,
        val canSchedule: Boolean,
        val saving: Boolean = false,
        val error: String = "",
    ) : ReviewUi

    data class Saved(val dueOn: LocalDate, val startHour: Int, val alreadyScheduled: Boolean) : ReviewUi

    /** El estudiante eligió no programarlo. */
    data object Declined : ReviewUi
}

/** Pedidos de la mascota física que solo la pantalla puede atender. */
enum class TutorEffect {
    /** `BTN:TALK`: empezar a escuchar una respuesta dictada (la pantalla pide el aviso y el permiso del micrófono). */
    START_VOICE,
}

/**
 * Todo lo que la pantalla dibuja.
 *
 * @property hintsUsed pistas reveladas en la práctica guiada de esta sesión.
 */
data class TutorUiState(
    val topic: Topic,
    val tutor: TutorState,
    val review: ReviewUi = ReviewUi.None,
    val hintsUsed: Int = 0,
)

/**
 * Orquesta una sesión de práctica: la máquina de estados del motor ([TutorSession]), el historial en disco, la
 * mascota física y el repaso en la agenda. Funciona sin Internet. No depende de Android ni de Compose: la
 * pantalla solo dibuja [ui] y llama a estos métodos, y las pruebas de la JVM lo ejercitan completo.
 *
 * Hay que usarla desde un solo hilo (el principal): [TutorSession] no es segura entre hilos.
 *
 * Reglas que cumple:
 *  - Los intentos guiados se guardan al responder; los de la comprobación sin ayuda solo al llegar al resumen, para
 *    que ni el historial ni la lista de temas adelanten si se acertó.
 *  - Durante la comprobación sin ayuda la mascota física no anuncia aciertos ni errores (ver [PetFeedback]).
 *  - El repaso solo se crea en la agenda tras [scheduleReview], es decir, después de que el estudiante confirmó.
 */
class TutorCoordinator(
    topicId: String,
    private val scope: CoroutineScope,
    private val history: TutorHistory,
    private val petLink: PetLink,
    private val academic: AcademicRepository?,
    private val zoneId: String,
    private val clock: TutorClock = TutorClock.SYSTEM,
    private val today: () -> LocalDate = { todayIn(zoneId) },
    private val seedBase: Long = clock.nowMillis(),
    speechDebounceMillis: Long = SpeechFace.DEBOUNCE_MS,
    private val catalog: TutorCatalog = TutorContent.catalog,
) {
    val topic: Topic = catalog.requireTopic(topicId)

    private val hintsByExercise = mutableMapOf<String, Int>()
    private var persisted = 0
    private var restarts = 0L
    private var lastSignal: PetSignal? = null
    private var session: TutorSession = newSession()

    private val mutableUi = MutableStateFlow(initialUi())
    private val mutableEffects = MutableSharedFlow<TutorEffect>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val ui: StateFlow<TutorUiState> = mutableUi.asStateFlow()
    val effects: SharedFlow<TutorEffect> = mutableEffects.asSharedFlow()

    /**
     * Cara de la mascota física mientras suena la lectura en voz: «hablando» con el principio del texto y, al terminar,
     * la cara que corresponde al estado del tutor en ese momento. La pantalla se lo pasa a su `VoiceSession`.
     */
    val speechSync = SpeechFaceSync(
        petLink = petLink,
        scope = scope,
        restore = { SpeechFace.restoreSignal(mutableUi.value.tutor) },
        debounceMillis = speechDebounceMillis,
    )

    init {
        scope.launch { petLink.events.collect { handlePetEvent(it) } }
        scope.launch {
            petLink.state.map { it.connected }.distinctUntilChanged().collect { connected ->
                // Al conectar a mitad de sesión, la mascota recibe la cara actual (sin repetir el sonido).
                if (connected) lastSignal?.let { deliver(it, withSound = false) }
            }
        }
        sendPetSignal(mutableUi.value.tutor)
    }

    // ---- acciones de la sesión ----

    fun next() = advance { it.next() }
    fun back() = advance { it.back() }
    fun skipLearn() = advance { it.skipLearn() }
    fun goToCheck() = advance { it.goToCheck() }
    fun answer(index: Int) = advance { it.answer(index) }
    fun requestHint() = advance { it.requestHint() }
    fun showSolution() = advance { it.showSolution() }
    fun retry() = advance { it.retry() }
    fun dontKnow() = advance { it.dontKnow() }

    /** Empieza una sesión nueva del mismo tema, con ejercicios distintos. */
    fun restart() {
        restarts++
        hintsByExercise.clear()
        persisted = 0
        lastSignal = null
        session = newSession()
        mutableUi.value = initialUi()
        sendPetSignal(mutableUi.value.tutor)
    }

    // ---- repaso en la agenda ----

    fun setReviewHour(hour: Int) {
        mutableUi.update { state ->
            val offer = state.review as? ReviewUi.Offer
            if (offer == null || offer.saving || hour !in TutorReviewPlanner.START_HOURS) state
            else state.copy(review = offer.copy(startHour = hour, error = ""))
        }
    }

    fun declineReview() {
        mutableUi.update { state ->
            val offer = state.review as? ReviewUi.Offer
            if (offer == null || offer.saving) state else state.copy(review = ReviewUi.Declined)
        }
    }

    /** Crea la sesión de estudio en la agenda personal. Solo se llama cuando el estudiante pulsó «Programar». */
    fun scheduleReview() {
        val offer = mutableUi.value.review as? ReviewUi.Offer ?: return
        val repository = academic ?: return
        if (offer.saving || !offer.canSchedule) return
        mutableUi.update { it.copy(review = offer.copy(saving = true, error = "")) }
        scope.launch {
            val result: ReviewUi = try {
                val baseDay = offer.dueOn.minusDays(offer.proposal.dayOffset.toLong())
                val activity = TutorReviewPlanner.activityFor(topic, offer.proposal, baseDay, zoneId, offer.startHour, offer.minutes)
                repository.saveActivity(DatasetScope.PERSONAL, activity)
                ReviewUi.Saved(offer.dueOn, offer.startHour, alreadyScheduled = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: StaleAcademicWriteException) {
                // El identificador del repaso es estable: si ya existe, se programó antes.
                ReviewUi.Saved(offer.dueOn, offer.startHour, alreadyScheduled = true)
            } catch (e: DateTimeException) {
                offer.copy(saving = false, error = e.message ?: SCHEDULE_ERROR)
            } catch (e: IllegalArgumentException) {
                offer.copy(saving = false, error = e.message ?: SCHEDULE_ERROR)
            } catch (_: Exception) {
                offer.copy(saving = false, error = SCHEDULE_ERROR)
            }
            mutableUi.update { it.copy(review = result) }
        }
    }

    /** Se llama al salir de la pantalla: la mascota vuelve a reposo. */
    fun close() {
        speechSync.release(restoreFace = false)
        petLink.showFace(PetExpression.IDLE, "")
    }

    // ---- internos ----

    private fun newSession(): TutorSession = TutorSession.forTopic(
        topic.id,
        SessionConfig(seed = seedBase + restarts, history = history.state.value.attempts),
        clock,
        catalog,
    )

    private fun initialUi(): TutorUiState = TutorUiState(
        topic = topic,
        tutor = session.start(),
    )

    private fun advance(action: (TutorSession) -> TutorState) {
        val before = mutableUi.value.tutor
        val after = action(session)
        if (after == before) return
        val hints = trackHints(after)
        persist(after)
        mutableUi.update { it.copy(tutor = after, hintsUsed = hints) }
        if (after.phase == Phase.SUMMARY && before.phase != Phase.SUMMARY) offerReview(after)
        sendPetSignal(after)
    }

    private fun trackHints(state: TutorState): Int {
        val exercise = state.exercise
        if (exercise != null && state.phase == Phase.GUIDED_PRACTICE) {
            val shown = exercise.hintsShown.size
            if (shown > (hintsByExercise[exercise.id] ?: 0)) hintsByExercise[exercise.id] = shown
        }
        return hintsByExercise.values.sum()
    }

    /** Guarda los intentos nuevos. Los de la comprobación sin ayuda esperan al resumen. */
    private fun persist(state: TutorState) {
        val all = session.attempts
        val ready = if (state.phase == Phase.SUMMARY) all else all.takeWhile { it.mode == AttemptMode.GUIDED }
        if (ready.size > persisted) {
            history.append(ready.drop(persisted))
            persisted = ready.size
        }
    }

    private fun offerReview(state: TutorState) {
        val proposal = state.summary?.review
        val review: ReviewUi = if (proposal == null) {
            ReviewUi.None
        } else {
            ReviewUi.Offer(
                proposal = proposal,
                dueOn = proposal.dueOn(today()),
                startHour = TutorReviewPlanner.DEFAULT_START_HOUR,
                minutes = TutorReviewPlanner.DEFAULT_MINUTES,
                canSchedule = academic != null,
            )
        }
        mutableUi.update { it.copy(review = review) }
    }

    private fun handlePetEvent(event: PetEvent) {
        val actions = mutableUi.value.tutor.actions
        when (event) {
            is PetEvent.Option -> if (TutorAction.ANSWER in actions) answer(event.index)
            is PetEvent.Ok -> if (TutorAction.NEXT in actions) next()
            is PetEvent.Talk -> mutableEffects.tryEmit(TutorEffect.START_VOICE)
            is PetEvent.Pong -> Unit
        }
    }

    private fun sendPetSignal(state: TutorState) {
        val signal = PetFeedback.signalFor(state)
        val repeat = signal.sound == null && signal == lastSignal
        lastSignal = signal
        if (!repeat) deliver(signal, withSound = true)
    }

    private fun deliver(signal: PetSignal, withSound: Boolean) {
        petLink.showFace(signal.expression, signal.text)
        if (withSound) signal.sound?.let { petLink.playSound(it) }
    }

    companion object {
        /** La fecha de hoy en la zona del perfil; si la zona no se reconoce, la del teléfono (nunca falla). */
        internal fun todayIn(zoneId: String): LocalDate = try {
            LocalDate.now(ZoneId.of(zoneId))
        } catch (_: DateTimeException) {
            LocalDate.now()
        }

        const val SCHEDULE_ERROR = "No pudimos programar el repaso. Tu agenda no cambió; puedes intentarlo otra vez."
    }
}
