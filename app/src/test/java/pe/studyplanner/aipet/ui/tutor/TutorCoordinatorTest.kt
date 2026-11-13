package pe.studyplanner.aipet.ui.tutor

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.studyplanner.aipet.data.ai.AiTransport
import pe.studyplanner.aipet.data.ai.ProxyReply
import pe.studyplanner.aipet.data.tutor.TutorAiExplainer
import pe.studyplanner.aipet.data.tutor.TutorAttemptStore
import pe.studyplanner.aipet.data.tutor.TutorHistory
import pe.studyplanner.aipet.domain.academic.AcademicActivity
import pe.studyplanner.aipet.domain.academic.AcademicRepository
import pe.studyplanner.aipet.domain.academic.AcademicSnapshot
import pe.studyplanner.aipet.domain.academic.ActivityKind
import pe.studyplanner.aipet.domain.academic.Course
import pe.studyplanner.aipet.domain.academic.DatasetScope
import pe.studyplanner.aipet.domain.academic.RecurrenceException
import pe.studyplanner.aipet.domain.academic.StaleAcademicWriteException
import pe.studyplanner.aipet.domain.agent.AgentContext
import pe.studyplanner.aipet.domain.companion.PetEvent
import pe.studyplanner.aipet.domain.companion.PetExpression
import pe.studyplanner.aipet.domain.companion.PetLink
import pe.studyplanner.aipet.domain.companion.PetLinkState
import pe.studyplanner.aipet.domain.companion.PetLinkStatus
import pe.studyplanner.aipet.domain.companion.PetSound
import pe.studyplanner.aipet.domain.tutor.Area
import pe.studyplanner.aipet.domain.tutor.AttemptMode
import pe.studyplanner.aipet.domain.tutor.BankFiles
import pe.studyplanner.aipet.domain.tutor.bankExerciseOf
import pe.studyplanner.aipet.domain.tutor.FakeClock
import pe.studyplanner.aipet.domain.tutor.MasteryStatus
import pe.studyplanner.aipet.domain.tutor.Phase
import pe.studyplanner.aipet.domain.tutor.TutorReviewPlanner
import pe.studyplanner.aipet.domain.tutor.TutorScreen
import pe.studyplanner.aipet.domain.tutor.correctOf
import pe.studyplanner.aipet.domain.tutor.wrongOf

/** Mascota falsa: guarda lo que recibe y deja simular conexión y botones. */
private class FakePetLink(connected: Boolean = true) : PetLink {
    private val mutableState = MutableStateFlow(PetLinkState(if (connected) PetLinkStatus.CONNECTED else PetLinkStatus.IDLE))
    private val mutableEvents = MutableSharedFlow<PetEvent>(extraBufferCapacity = 8)
    override val state: StateFlow<PetLinkState> = mutableState.asStateFlow()
    override val events: SharedFlow<PetEvent> = mutableEvents.asSharedFlow()
    val faces = mutableListOf<Pair<PetExpression, String>>()
    val sounds = mutableListOf<PetSound>()

    override fun refresh() = Unit
    override fun connect() = setConnected(true)
    override fun disconnect() = setConnected(false)
    override fun showFace(expression: PetExpression, text: String) {
        faces += expression to text
    }

    override fun playSound(sound: PetSound) {
        sounds += sound
    }

    fun setConnected(value: Boolean) {
        mutableState.value = PetLinkState(if (value) PetLinkStatus.CONNECTED else PetLinkStatus.IDLE)
    }

    fun press(event: PetEvent) {
        mutableEvents.tryEmit(event)
    }
}

/** Agenda falsa: solo `saveActivity` hace algo; el resto no debería usarse. */
private class FakeAcademic(private val failWith: Exception? = null) : AcademicRepository {
    val saved = mutableListOf<AcademicActivity>()

    override fun observe(scope: DatasetScope): Flow<AcademicSnapshot> = emptyFlow()
    override suspend fun snapshot(scope: DatasetScope): AcademicSnapshot = AcademicSnapshot(scope)
    override suspend fun saveCourse(scope: DatasetScope, course: Course): Course = error("no se usa")
    override suspend fun archiveCourse(scope: DatasetScope, id: String, expectedVersion: Long): Course = error("no se usa")
    override suspend fun deleteCourse(scope: DatasetScope, id: String, expectedVersion: Long) = error("no se usa")
    override suspend fun saveActivity(scope: DatasetScope, activity: AcademicActivity): AcademicActivity {
        failWith?.let { throw it }
        check(scope == DatasetScope.PERSONAL) { "el repaso va a la agenda personal" }
        saved += activity
        return activity
    }

    override suspend fun deleteActivity(scope: DatasetScope, id: String, expectedVersion: Long) = error("no se usa")
    override suspend fun putException(scope: DatasetScope, exception: RecurrenceException, expectedActivityVersion: Long) = error("no se usa")
    override suspend fun removeException(scope: DatasetScope, activityId: String, occurrenceDate: LocalDate, expectedActivityVersion: Long) = error("no se usa")
    override suspend fun seedDemo(referenceDate: LocalDate) = error("no se usa")
    override suspend fun resetDemo(referenceDate: LocalDate) = error("no se usa")
}

class TutorCoordinatorTest {
    private val topicId = "ecuaciones-lineales"
    private val today = LocalDate.of(2026, 10, 28)
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var history: TutorHistory
    private lateinit var store: TutorAttemptStore

    @Before fun setUp() {
        dir = Files.createTempDirectory("tutor-coordinator").toFile()
        store = TutorAttemptStore(File(dir, "tutor/intentos.csv"))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        history = TutorHistory(store, scope, Dispatchers.Unconfined)
        history.start()
    }

    @After fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun coordinator(
        link: PetLink = FakePetLink(),
        academic: AcademicRepository? = FakeAcademic(),
        ai: TutorAiExplainer? = null,
        speechDebounceMillis: Long = 0L,
    ) = TutorCoordinator(
        topicId = topicId,
        scope = scope,
        history = history,
        petLink = link,
        academic = academic,
        ai = ai,
        zoneId = "America/Lima",
        clock = FakeClock(),
        today = { today },
        seedBase = 11L,
        speechDebounceMillis = speechDebounceMillis,
    )

    private fun TutorCoordinator.tutor() = ui.value.tutor

    private fun TutorCoordinator.finishGuidedCorrectly() {
        skipLearn()
        repeat(3) {
            answer(correctOf(tutor()))
            next()
        }
    }

    /** Sesión completa hasta el resumen; [picks] true = correcta, false = incorrecta. */
    private fun TutorCoordinator.playToSummary(picks: List<Boolean>) {
        finishGuidedCorrectly()
        next()
        for (pick in picks) answer(if (pick) correctOf(tutor()) else wrongOf(tutor()))
    }

    @Test fun startsInTheLearnPhaseWithAQuietFace() {
        val link = FakePetLink()
        val c = coordinator(link)
        val state = c.tutor()
        assertEquals(Phase.LEARN, state.phase)
        assertEquals(TutorScreen.EXPLANATION, state.screen)
        assertEquals(listOf(PetExpression.IDLE to "Vamos a aprender"), link.faces)
        assertTrue(link.sounds.isEmpty())
        assertEquals(AiUi.Unavailable, c.ui.value.ai)
        assertEquals(ReviewUi.None, c.ui.value.review)
    }

    @Test fun guidedAnswersAreSavedRightAway() {
        val c = coordinator()
        c.skipLearn()
        c.answer(wrongOf(c.tutor()))
        assertEquals(TutorScreen.WRONG_ANSWER, c.tutor().screen)
        assertEquals(1, history.state.value.attempts.size)
        assertEquals(AttemptMode.GUIDED, history.state.value.attempts.single().mode)
        c.answer(correctOf(c.tutor()))
        assertEquals(TutorScreen.CORRECT, c.tutor().screen)
        assertEquals(2, history.state.value.attempts.size)
    }

    @Test fun guidedFeedbackReachesThePhysicalPet() {
        val link = FakePetLink()
        val c = coordinator(link)
        c.skipLearn()
        c.requestHint()
        c.answer(wrongOf(c.tutor()))
        c.answer(correctOf(c.tutor()))
        assertEquals(listOf(PetSound.PISTA, PetSound.ERROR, PetSound.CORRECTO), link.sounds)
        assertEquals(PetExpression.FELIZ to "¡Correcto!", link.faces.last())
    }

    @Test fun hintsAreCountedOncePerExercise() {
        val c = coordinator()
        c.skipLearn()
        c.requestHint()
        c.requestHint()
        assertEquals(2, c.ui.value.hintsUsed)
        c.answer(wrongOf(c.tutor()))
        assertEquals(3, c.ui.value.hintsUsed)
        c.answer(correctOf(c.tutor()))
        c.next()
        c.answer(wrongOf(c.tutor()))
        assertEquals(4, c.ui.value.hintsUsed)
    }

    @Test fun unaidedAnswersAreOnlySavedWhenTheSummaryAppears() {
        val c = coordinator()
        c.finishGuidedCorrectly()
        assertEquals(TutorScreen.UNAIDED_INTRO, c.tutor().screen)
        c.next()
        c.answer(correctOf(c.tutor()))
        assertEquals(Phase.UNAIDED_CHECK, c.tutor().phase)
        assertTrue(history.state.value.attempts.all { it.mode == AttemptMode.GUIDED })
        assertEquals(3, history.state.value.attempts.size)
        c.answer(wrongOf(c.tutor()))
        assertEquals(Phase.SUMMARY, c.tutor().phase)
        assertEquals(2, history.state.value.attempts.count { it.mode == AttemptMode.UNAIDED })
    }

    @Test fun theMasteryBadgeDoesNotMoveBeforeTheSummary() {
        val c = coordinator()
        c.finishGuidedCorrectly()
        c.next()
        c.answer(correctOf(c.tutor()))
        assertEquals(MasteryStatus.EN_PRACTICA, history.state.value.masteryOf(topicId))
        c.answer(correctOf(c.tutor()))
        assertEquals(MasteryStatus.DOMINIO_SIN_AYUDA, history.state.value.masteryOf(topicId))
    }

    @Test fun thePhysicalPetStaysQuietDuringTheUnaidedCheck() {
        val link = FakePetLink()
        val c = coordinator(link)
        c.finishGuidedCorrectly()
        val soundsBefore = link.sounds.size
        c.next()
        c.answer(wrongOf(c.tutor()))
        assertEquals(Phase.UNAIDED_CHECK, c.tutor().phase)
        assertEquals(soundsBefore, link.sounds.size)
        assertTrue(link.faces.takeLast(2).none { it.first == PetExpression.FELIZ || it.first == PetExpression.ALERTA })
        c.answer(correctOf(c.tutor()))
        assertEquals(Phase.SUMMARY, c.tutor().phase)
    }

    @Test fun aPerfectCheckCelebratesAndOffersAReview() {
        val link = FakePetLink()
        val c = coordinator(link)
        c.playToSummary(listOf(true, true))
        assertEquals(PetSound.CELEBRACION, link.sounds.last())
        assertEquals(PetExpression.CELEBRAR to "¡Lo lograste!", link.faces.last())
        val offer = c.ui.value.review as ReviewUi.Offer
        assertEquals(today.plusDays(3), offer.dueOn)
        assertTrue(offer.canSchedule)
        assertFalse(offer.saving)
        assertEquals(TutorReviewPlanner.DEFAULT_START_HOUR, offer.startHour)
    }

    @Test fun nothingReachesTheAgendaUntilTheStudentConfirms() {
        val academic = FakeAcademic()
        val c = coordinator(academic = academic)
        c.playToSummary(listOf(true, false))
        assertTrue(c.ui.value.review is ReviewUi.Offer)
        assertTrue(academic.saved.isEmpty())
        c.setReviewHour(20)
        assertTrue(academic.saved.isEmpty())
        c.declineReview()
        assertEquals(ReviewUi.Declined, c.ui.value.review)
        c.scheduleReview()
        assertTrue(academic.saved.isEmpty())
    }

    @Test fun confirmingCreatesOneStudySessionInThePersonalAgenda() {
        val academic = FakeAcademic()
        val c = coordinator(academic = academic)
        c.playToSummary(listOf(true, true))
        c.setReviewHour(16)
        c.scheduleReview()
        val saved = academic.saved.single()
        assertEquals(ActivityKind.STUDY, saved.kind)
        assertEquals("Repaso: ${c.topic.title}", saved.title)
        val saving = c.ui.value.review as ReviewUi.Saved
        assertEquals(today.plusDays(3), saving.dueOn)
        assertEquals(16, saving.startHour)
        assertFalse(saving.alreadyScheduled)
        c.scheduleReview()
        assertEquals(1, academic.saved.size)
    }

    @Test fun anExistingReviewIsReportedNotDuplicated() {
        val c = coordinator(academic = FakeAcademic(failWith = StaleAcademicWriteException()))
        c.playToSummary(listOf(true, true))
        c.scheduleReview()
        val saved = c.ui.value.review as ReviewUi.Saved
        assertTrue(saved.alreadyScheduled)
    }

    @Test fun aFailureKeepsTheOfferSoTheStudentCanRetry() {
        val c = coordinator(academic = FakeAcademic(failWith = RuntimeException("disco lleno")))
        c.playToSummary(listOf(true, true))
        c.scheduleReview()
        val offer = c.ui.value.review as ReviewUi.Offer
        assertFalse(offer.saving)
        assertEquals(TutorCoordinator.SCHEDULE_ERROR, offer.error)
    }

    @Test fun withoutAnAgendaTheReviewIsOnlyASuggestion() {
        val c = coordinator(academic = null)
        c.playToSummary(listOf(true, true))
        val offer = c.ui.value.review as ReviewUi.Offer
        assertFalse(offer.canSchedule)
        c.scheduleReview()
        assertTrue(c.ui.value.review is ReviewUi.Offer)
    }

    @Test fun onlyTheOfferedHoursCanBePicked() {
        val c = coordinator()
        c.playToSummary(listOf(true, true))
        c.setReviewHour(5)
        assertEquals(TutorReviewPlanner.DEFAULT_START_HOUR, (c.ui.value.review as ReviewUi.Offer).startHour)
        c.setReviewHour(20)
        assertEquals(20, (c.ui.value.review as ReviewUi.Offer).startHour)
    }

    @Test fun petButtonsAnswerAndAdvanceOnlyWhenTheActionIsAllowed() {
        val link = FakePetLink()
        val c = coordinator(link)
        link.press(PetEvent.Option(2))
        assertEquals(TutorScreen.EXPLANATION, c.tutor().screen)
        c.skipLearn()
        val correct = correctOf(c.tutor())
        link.press(PetEvent.Option(correct))
        assertEquals(TutorScreen.CORRECT, c.tutor().screen)
        link.press(PetEvent.Option(0))
        assertEquals(TutorScreen.CORRECT, c.tutor().screen)
        link.press(PetEvent.Ok)
        assertEquals(TutorScreen.QUESTION, c.tutor().screen)
        assertEquals(2, c.tutor().progress.current)
    }

    @Test fun theTalkButtonAsksTheScreenToStartListening() {
        val link = FakePetLink()
        val c = coordinator(link)
        val effects = mutableListOf<TutorEffect>()
        val job = scope.launch { c.effects.collect { effects += it } }
        link.press(PetEvent.Talk)
        assertEquals(listOf(TutorEffect.START_VOICE), effects)
        job.cancel()
    }

    @Test fun reconnectingResendsTheCurrentFaceWithoutRepeatingTheSound() {
        val link = FakePetLink(connected = false)
        val c = coordinator(link)
        c.skipLearn()
        c.requestHint()
        val sounds = link.sounds.toList()
        val faces = link.faces.size
        link.setConnected(true)
        assertEquals(faces + 1, link.faces.size)
        assertEquals(PetExpression.PENSAR to "Pista 1 de 3", link.faces.last())
        assertEquals(sounds, link.sounds)
    }

    @Test fun anIdenticalFaceWithoutSoundIsNotSentTwice() {
        val link = FakePetLink()
        val c = coordinator(link)
        c.skipLearn()
        val faces = link.faces.size
        c.requestHint()
        c.next()
        assertEquals(faces + 1, link.faces.size)
    }

    @Test fun restartingGivesNewExercisesAndClearsTheSessionState() {
        val c = coordinator()
        c.skipLearn()
        val firstId = checkNotNull(c.tutor().exercise).id
        c.requestHint()
        c.restart()
        assertEquals(Phase.LEARN, c.tutor().phase)
        assertEquals(0, c.ui.value.hintsUsed)
        assertEquals(ReviewUi.None, c.ui.value.review)
        c.skipLearn()
        assertNotEquals(firstId, checkNotNull(c.tutor().exercise).id)
    }

    @Test fun closingPutsThePhysicalPetBackToIdle() {
        val link = FakePetLink()
        val c = coordinator(link)
        c.close()
        assertEquals(PetExpression.IDLE to "", link.faces.last())
    }

    @Test fun spokenReadingShowsTheTalkingFaceAndThenTheQuestionFaceAgain() {
        val link = FakePetLink()
        val c = coordinator(link)
        c.skipLearn()
        val question = link.faces.last()
        assertEquals(PetExpression.PENSAR, question.first)
        link.faces.clear()
        c.speechSync.onSpeechStarted("Despeja x en 2x + 3 = 11")
        assertEquals(listOf(PetExpression.HABLAR to "Despeja x en 2x + 3 = 11"), link.faces)
        c.speechSync.onSpeechEnded()
        assertEquals(listOf(PetExpression.HABLAR to "Despeja x en 2x + 3 = 11", question), link.faces)
        assertTrue(link.sounds.isEmpty())
    }

    @Test fun theMouthMovesOnlyWhileTheExplanationIsRead() {
        val link = FakePetLink()
        val c = coordinator(link)
        assertEquals(PetExpression.IDLE to "Vamos a aprender", link.faces.last())
        c.speechSync.onSpeechStarted(c.tutor().speech)
        assertEquals(PetExpression.HABLAR, link.faces.last().first)
        c.speechSync.onSpeechEnded()
        assertEquals(PetExpression.IDLE to "Vamos a aprender", link.faces.last())
        assertEquals(3, link.faces.size)
    }

    @Test fun theTalkingFaceShowsOnlyTheStartOfALongReading() {
        val link = FakePetLink()
        val c = coordinator(link)
        link.faces.clear()
        c.speechSync.onSpeechStarted("Una ecuación lineal es una igualdad con una incógnita de primer grado que se despeja paso a paso")
        val (expression, text) = link.faces.single()
        assertEquals(PetExpression.HABLAR, expression)
        assertTrue(text.length <= 36)
        assertTrue(text.startsWith("Una ecuación lineal es una"))
    }

    @Test fun afterReadingAHintTheFaceIsTheHintFaceWithoutRepeatingItsSound() {
        val link = FakePetLink()
        val c = coordinator(link)
        c.skipLearn()
        c.requestHint()
        val hint = link.faces.last()
        assertEquals(listOf(PetSound.PISTA), link.sounds)
        c.speechSync.onSpeechStarted("Primero mira el término con la incógnita")
        c.speechSync.onSpeechEnded()
        assertEquals(hint, link.faces.last())
        assertEquals(listOf(PetSound.PISTA), link.sounds)
    }

    @Test fun theRestoredFaceFollowsTheTutorStateAtTheEndOfTheReading() {
        val link = FakePetLink()
        val c = coordinator(link)
        c.skipLearn()
        c.speechSync.onSpeechStarted("Lee la pregunta")
        c.answer(correctOf(c.tutor()))
        c.speechSync.onSpeechEnded()
        assertEquals(PetExpression.FELIZ to "¡Correcto!", link.faces.last())
        assertEquals(listOf(PetSound.CORRECTO), link.sounds)
    }

    @Test fun duringTheUnaidedCheckTheRestoredFaceRevealsNothing() {
        val link = FakePetLink()
        val c = coordinator(link)
        c.finishGuidedCorrectly()
        c.next()
        c.speechSync.onSpeechStarted("Lee la pregunta")
        c.answer(wrongOf(c.tutor()))
        c.speechSync.onSpeechEnded()
        assertEquals(PetExpression.PENSAR, link.faces.last().first)
        assertTrue(link.faces.last().second.startsWith("Sin ayuda"))
        assertTrue(PetSound.ERROR !in link.sounds)
    }

    @Test fun withoutAConnectedPetTheSpeechFaceDoesNothing() {
        val link = FakePetLink(connected = false)
        val c = coordinator(link)
        link.faces.clear()
        c.speechSync.onSpeechStarted("Hola")
        c.speechSync.onSpeechEnded()
        assertTrue(link.faces.isEmpty())
        assertTrue(link.sounds.isEmpty())
    }

    @Test fun closingWhileTheTalkingFaceIsShownLeavesTheIdleFaceLast() {
        val link = FakePetLink()
        val c = coordinator(link)
        c.speechSync.onSpeechStarted("Hola")
        c.close()
        assertEquals(PetExpression.IDLE to "", link.faces.last())
        val count = link.faces.size
        c.speechSync.onSpeechEnded()
        c.speechSync.onSpeechStarted("Tarde")
        assertEquals(count, link.faces.size)
    }

    @Test fun aiExplanationIsOptionalAndLabelledByTheScreen() {
        assertEquals(AiUi.Unavailable, coordinator(ai = null).ui.value.ai)
        val c = coordinator()
        c.requestAiExplanation()
        assertEquals(AiUi.Unavailable, c.ui.value.ai)

        val transport = object : AiTransport {
            override suspend fun check() = "ok"
            override suspend fun chat(text: String, context: AgentContext) = ProxyReply("answer", "Imagina una balanza en equilibrio.")
        }
        val withAi = coordinator(ai = TutorAiExplainer(transport))
        assertEquals(AiUi.Idle, withAi.ui.value.ai)
        withAi.requestAiExplanation()
        assertEquals(AiUi.Ready("Imagina una balanza en equilibrio."), withAi.ui.value.ai)
    }

    @Test fun aFailingAiLeavesTheLocalPracticeUntouched() {
        val transport = object : AiTransport {
            override suspend fun check() = "ok"
            override suspend fun chat(text: String, context: AgentContext): ProxyReply = throw RuntimeException("sin red")
        }
        val c = coordinator(ai = TutorAiExplainer(transport))
        c.requestAiExplanation()
        assertEquals(AiUi.Failed(TutorAiExplainer.GENERIC), c.ui.value.ai)
        c.skipLearn()
        assertEquals(Phase.GUIDED_PRACTICE, c.tutor().phase)
    }

    @Test fun anUnknownTimeZoneFallsBackToThePhoneDateInsteadOfFailing() {
        // Si la zona del perfil no se reconociera, el resumen no debe cerrar la app.
        val today = TutorCoordinator.todayIn("Zona/Inexistente")
        assertTrue(kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(today, LocalDate.now())) <= 1)
        val lima = TutorCoordinator.todayIn("America/Lima")
        assertTrue(kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(lima, LocalDate.now())) <= 1)
    }

    @Test fun aBankTopicRunsThroughTheCoordinatorAndKeepsUnaidedAnswersUntilTheSummary() {
        val c = TutorCoordinator(
            topicId = "quimica-estructura-atomica",
            scope = scope,
            history = history,
            petLink = FakePetLink(),
            academic = FakeAcademic(),
            ai = null,
            zoneId = "America/Lima",
            clock = FakeClock(),
            today = { today },
            seedBase = 11L,
            speechDebounceMillis = 0L,
            catalog = BankFiles.catalog,
        )
        assertEquals(Area.QUIMICA, c.topic.area)
        assertEquals(Phase.LEARN, c.tutor().phase)
        c.skipLearn()
        repeat(3) {
            c.answer(bankExerciseOf(c.tutor()).correctIndex)
            assertEquals(TutorScreen.CORRECT, c.tutor().screen)
            assertTrue(c.tutor().text.contains("Fuente: "))
            c.next()
        }
        assertEquals(3, history.state.value.attempts.size)
        c.next()
        repeat(2) { c.answer(bankExerciseOf(c.tutor()).correctIndex) }
        assertEquals(Phase.SUMMARY, c.tutor().phase)
        assertEquals(5, history.state.value.attempts.size)
        assertTrue(history.state.value.attempts.all { it.topicId == "quimica-estructura-atomica" })
    }
}
