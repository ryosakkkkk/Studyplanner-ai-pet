package pe.studyplanner.aipet.ui.tutor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pe.studyplanner.aipet.data.tutor.TutorAiExplainer
import pe.studyplanner.aipet.data.tutor.TutorHistory
import pe.studyplanner.aipet.domain.academic.AcademicRepository
import pe.studyplanner.aipet.domain.companion.PetFeedback
import pe.studyplanner.aipet.domain.companion.PetLink
import pe.studyplanner.aipet.domain.profile.AvatarStyle
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.domain.tutor.Exercise
import pe.studyplanner.aipet.domain.tutor.Phase
import pe.studyplanner.aipet.domain.tutor.SpokenOption
import pe.studyplanner.aipet.domain.tutor.TutorAction
import pe.studyplanner.aipet.domain.tutor.TutorContent
import pe.studyplanner.aipet.domain.tutor.TutorScreen as TutorStep
import pe.studyplanner.aipet.domain.tutor.TutorState
import pe.studyplanner.aipet.domain.voice.VoicePhase
import pe.studyplanner.aipet.domain.voice.VoiceSession
import pe.studyplanner.aipet.platform.voice.AndroidVoicePlatform
import pe.studyplanner.aipet.ui.components.PetAvatar

private const val OPTION_LETTERS = "ABCDE"
private const val MAX_SPOKEN_CHARS = 3800

/**
 * Sesión de práctica de un tema: aprender, practicar con pistas, comprobar sin ayuda y ver el resumen.
 *
 * Toda la lógica vive en [TutorCoordinator]; esta pantalla solo dibuja su estado y le pasa las acciones. La
 * lectura en voz y el dictado de la respuesta usan la misma [VoiceSession] que el chat, con el mismo aviso previo
 * al micrófono y la misma regla: lo dictado nunca responde solo, el estudiante lo confirma.
 */
@Composable
fun TutorScreen(
    topicId: String,
    history: TutorHistory,
    petLink: PetLink,
    academic: AcademicRepository?,
    ai: TutorAiExplainer?,
    profile: UserProfile,
    voiceEnabled: Boolean,
    onBack: () -> Unit,
    onOpenAgenda: () -> Unit,
    startAtCheck: Boolean = false,
    onAskTutor: ((String) -> Unit)? = null,
) {
    LaunchedEffect(history) { history.start() }
    val data by history.state.collectAsStateWithLifecycle()
    when {
        TutorContent.catalog.topic(topicId) == null -> TutorNoticeScreen(
            title = "Tema no disponible",
            message = "No encontramos ese tema de práctica. Vuelve a la lista y elige otro.",
            busy = false,
            actionLabel = "Volver a los temas",
            onAction = onBack,
        )
        !data.loaded -> TutorNoticeScreen(
            title = "Preparando tu práctica",
            message = "Cargando tu registro de práctica…",
            busy = true,
            actionLabel = "Volver",
            onAction = onBack,
        )
        else -> TutorSessionScreen(topicId, history, petLink, academic, ai, profile, voiceEnabled, onBack, onOpenAgenda, startAtCheck, onAskTutor)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TutorNoticeScreen(title: String, message: String, busy: Boolean, actionLabel: String, onAction: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Practicar", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onAction) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (busy) CircularProgressIndicator()
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Button(onClick = onAction, modifier = Modifier.heightIn(min = 52.dp)) { Text(actionLabel) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TutorSessionScreen(
    topicId: String,
    history: TutorHistory,
    petLink: PetLink,
    academic: AcademicRepository?,
    ai: TutorAiExplainer?,
    profile: UserProfile,
    voiceEnabled: Boolean,
    onBack: () -> Unit,
    onOpenAgenda: () -> Unit,
    startAtCheck: Boolean,
    onAskTutor: ((String) -> Unit)?,
) {
    val model: TutorViewModel = viewModel(
        key = "tutor-$topicId",
        factory = remember(topicId, history, petLink, academic, ai, profile.zoneId) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TutorViewModel(topicId, history, petLink, academic, ai, profile.zoneId) as T
            }
        },
    )
    val coordinator = model.coordinator
    // «Preguntarle al tutor»: abre la conversación con la IA sobre el ejercicio abierto (solo en la práctica guiada).
    val askTutor: (() -> Unit)? = if (onAskTutor == null) null else {
        {
            val brief = coordinator.tutorBrief()
            if (brief != null) onAskTutor(brief.toJson())
        }
    }
    // «Comprobar» desde Hoy: una sola vez por sesión, aunque la pantalla se recree.
    var checkRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(coordinator, startAtCheck) {
        if (startAtCheck && !checkRequested) { checkRequested = true; coordinator.goToCheck() }
    }
    val ui by coordinator.ui.collectAsStateWithLifecycle()
    val link by petLink.state.collectAsStateWithLifecycle()
    val tutor = ui.tutor
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val reducedMotion = remember(context) { animationsDisabled(context) }

    // ---- voz: lectura en voz alta y dictado de la respuesta ----
    val voiceAllowed = voiceEnabled && profile.petVisible
    val voiceScope = rememberCoroutineScope()
    // La mascota física muestra la cara hablando mientras suena la lectura (coordinator.speechSync).
    val voice: VoiceSession? = remember(voiceAllowed, context, coordinator) {
        if (voiceAllowed) VoiceSession(AndroidVoicePlatform(context.applicationContext), voiceScope, coordinator.speechSync) else null
    }
    DisposableEffect(voice) { onDispose { voice?.close() } }
    val voiceUi = if (voice != null) voice.state.collectAsStateWithLifecycle().value else null
    val caps = voiceUi?.capabilities
    val onDeviceMode = caps != null && !caps.speechAvailable && caps.onDeviceAvailable
    val listening = voiceUi?.phase in setOf(VoicePhase.REQUESTING, VoicePhase.LISTENING, VoicePhase.TRANSCRIBING)
    val reading = voiceUi?.phase in setOf(VoicePhase.PREPARING_SPEECH, VoicePhase.SPEAKING)

    var foreground by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var consent by remember { mutableStateOf(false) }
    var permissionPending by remember { mutableStateOf(false) }
    var micMessage by remember { mutableStateOf("") }
    var voiceRequest by remember { mutableIntStateOf(0) }
    var confirmExit by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(voice, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                foreground = false
                consent = false
                permissionPending = false
                voice?.suspendSession()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                foreground = true
                voice?.refresh()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val canAnswer = TutorPresentation.canAnswer(tutor)
    val canDictate = voice != null && caps != null && foreground &&
        (caps.speechAvailable || caps.onDeviceAvailable) && !listening && voiceUi?.phase != VoicePhase.REVIEW

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val authorized = permissionPending && foreground
        permissionPending = false
        if (!granted) {
            micMessage = "Permiso de micrófono denegado. Puedes responder tocando una alternativa."
        } else if (authorized) {
            voice?.startDictation(true, onDeviceMode)
        } else {
            micMessage = "Permiso actualizado. Pulsa «Responder con la voz» otra vez."
        }
    }

    fun beginVoiceAnswer() {
        val v = voice
        if (v == null) {
            micMessage = "La voz no está disponible. Toca una alternativa para responder."
            return
        }
        if (!canAnswer) {
            micMessage = "Ahora no hay una pregunta que responder."
            return
        }
        if (!canDictate) {
            micMessage = "El dictado no está disponible ahora. Toca una alternativa para responder."
            return
        }
        v.cancel()
        micMessage = ""
        consent = true
    }

    fun startAfterConsent() {
        val v = voice ?: return
        if (!foreground) return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            v.startDictation(true, onDeviceMode)
        } else {
            permissionPending = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // BTN:TALK de la mascota física: pide el mismo aviso previo y permiso que el botón de la pantalla.
    LaunchedEffect(coordinator) {
        coordinator.effects.collect { effect -> if (effect == TutorEffect.START_VOICE) voiceRequest++ }
    }
    LaunchedEffect(voiceRequest) { if (voiceRequest > 0) beginVoiceAnswer() }

    // Al cambiar de pantalla se corta la lectura y se descarta cualquier dictado pendiente de otra pregunta.
    LaunchedEffect(tutor.screen, tutor.exercise?.id) {
        micMessage = ""
        val v = voice ?: return@LaunchedEffect
        v.stopSpeaking()
        val phase = v.state.value.phase
        if (phase == VoicePhase.REQUESTING || phase == VoicePhase.LISTENING || phase == VoicePhase.TRANSCRIBING) v.cancel()
        else if (phase == VoicePhase.REVIEW || phase == VoicePhase.ERROR) v.discardTranscript()
    }

    val scroll = rememberScrollState()
    LaunchedEffect(tutor) { scroll.scrollTo(0) }

    val inProgress = tutor.phase == Phase.GUIDED_PRACTICE || tutor.phase == Phase.UNAIDED_CHECK
    BackHandler(enabled = inProgress) { confirmExit = true }
    val exit: () -> Unit = { if (inProgress) confirmExit = true else onBack() }

    val mood = when (voiceUi?.phase) {
        VoicePhase.LISTENING -> "listen"
        VoicePhase.SPEAKING -> "speak"
        else -> tutorEmotion(tutor)
    }
    // Se elige una alternativa y se confirma con «Responder», en la zona del pulgar.
    var selected by remember(tutor.exercise?.id) { mutableStateOf<Int?>(null) }
    val answerBar = tutor.exercise != null && canAnswer && voiceUi?.phase != VoicePhase.REVIEW
    val hintsUsed = tutor.exercise?.hintsShown?.size ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.topic.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = exit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Salir de la práctica") } },
                actions = {
                    if (tutor.phase == Phase.GUIDED_PRACTICE && tutor.exercise != null) HintCounter(hintsUsed, Exercise.HINT_COUNT)
                },
            )
        },
        bottomBar = {
            if (answerBar) AnswerBar(
                canHint = TutorAction.REQUEST_HINT in tutor.actions,
                firstHint = hintsUsed == 0,
                canAnswer = selected != null && selected !in tutor.exercise?.eliminated.orEmpty(),
                onHint = { coordinator.requestHint() },
                onAnswer = { selected?.let { coordinator.answer(it) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SessionHeader(
                label = TutorPresentation.phaseLabel(tutor),
                fraction = TutorPresentation.phaseFraction(tutor),
                mood = mood,
                showPet = profile.petVisible,
                style = profile.avatar,
                reducedMotion = reducedMotion,
                petLinked = link.connected,
            )
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("tutor_session"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tutor.phase == Phase.UNAIDED_CHECK) UnaidedBanner()
                if (voiceUi != null && voice != null) {
                    VoiceAnswerCard(
                        phase = voiceUi.phase,
                        message = voiceUi.message,
                        partial = voiceUi.partial,
                        transcript = voiceUi.transcript,
                        option = if (voiceUi.phase == VoicePhase.REVIEW) SpokenOption.parse(voiceUi.transcript) else null,
                        canAnswer = canAnswer,
                        onConfirm = { index ->
                            if (voice.takeTranscript() != null) coordinator.answer(index)
                        },
                        onRetry = { beginVoiceAnswer() },
                        onDiscard = { voice.discardTranscript() },
                        onCancel = { voice.cancel() },
                    )
                }
                if (micMessage.isNotBlank()) {
                    Text(micMessage, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
                }
                when (tutor.screen) {
                    TutorStep.EXPLANATION, TutorStep.WORKED_EXAMPLE -> LearnContent(
                        tutor = tutor,
                        ui = ui,
                        listenButton = { ListenButton(voice, caps?.ttsEngineAvailable == true, reading, tutor.speech) },
                        onRequestAi = { coordinator.requestAiExplanation() },
                        onBack = { coordinator.back() },
                        onNext = { coordinator.next() },
                        onSkip = { coordinator.skipLearn() },
                    )
                    TutorStep.SUMMARY -> SummaryContent(
                        tutor = tutor,
                        ui = ui,
                        listenButton = { ListenButton(voice, caps?.ttsEngineAvailable == true, reading, tutor.speech) },
                        onHour = { coordinator.setReviewHour(it) },
                        onSchedule = { coordinator.scheduleReview() },
                        onDecline = { coordinator.declineReview() },
                        onOpenAgenda = onOpenAgenda,
                        onRestart = { coordinator.restart() },
                        onBackToTopics = onBack,
                    )
                    TutorStep.UNAIDED_INTRO -> {
                        TutorMessageCard(tutor.title, tutor.text)
                        ListenButton(voice, caps?.ttsEngineAvailable == true, reading, tutor.speech)
                        Button(
                            onClick = { coordinator.next() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("tutor_next"),
                        ) { Text(TutorPresentation.nextLabel(tutor)) }
                    }
                    else -> ExerciseContent(
                        tutor = tutor,
                        canAnswer = canAnswer,
                        voiceAvailable = voice != null,
                        listenButton = { ListenButton(voice, caps?.ttsEngineAvailable == true, reading, tutor.speech) },
                        selected = selected,
                        onAnswer = { selected = it },
                        onSolution = { coordinator.showSolution() },
                        onDontKnow = { coordinator.dontKnow() },
                        onRetry = { coordinator.retry() },
                        onNext = { coordinator.next() },
                        onVoice = { beginVoiceAnswer() },
                        onAskTutor = askTutor,
                    )
                }
            }
        }
    }

    if (consent) {
        AlertDialog(
            onDismissRequest = { consent = false },
            title = { Text("Antes de usar el micrófono") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        if (onDeviceMode) {
                            "Usaremos el servicio de reconocimiento del dispositivo, si tiene el idioma disponible. No guardamos audio. " +
                                "La captura dura hasta 15 segundos. Lo que digas solo propone una alternativa: tú la confirmas antes de responder."
                        } else {
                            "El servicio de reconocimiento de Android puede enviar tu audio a sus servidores y necesitar Internet. " +
                                "StudyPlanner no guarda audio. La captura dura hasta 15 segundos. Lo que digas solo propone una " +
                                "alternativa: tú la confirmas antes de responder. Continúa solo si aceptas usar ese servicio; " +
                                "también puedes tocar la alternativa."
                        },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { consent = false; startAfterConsent() },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Continuar con el dictado") }
            },
            dismissButton = {
                OutlinedButton(onClick = { consent = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ahora no") }
            },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("¿Salir de la práctica?") },
            text = {
                Text(
                    "Se pierde el avance de esta sesión. Lo que respondiste con pistas ya quedó guardado; " +
                        "la comprobación sin ayuda se guarda al llegar al resumen.",
                )
            },
            confirmButton = {
                Button(onClick = { confirmExit = false; onBack() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Salir") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmExit = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Seguir practicando") }
            },
        )
    }
}

@Composable
private fun SessionHeader(
    label: String,
    fraction: Float,
    mood: String,
    showPet: Boolean,
    style: AvatarStyle,
    reducedMotion: Boolean,
    petLinked: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (showPet) PetAvatar(style, Modifier.size(92.dp), mood = mood, reducedMotion = reducedMotion, closeUp = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag("tutor_phase").semantics { heading() },
            )
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            if (petLinked) {
                Text(
                    "Mascota física conectada",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Botón «Escuchar»: lee en voz alta el texto de la pantalla con el motor de lectura de Android. */
@Composable
private fun ListenButton(voice: VoiceSession?, ttsAvailable: Boolean, reading: Boolean, speech: String) {
    if (voice == null || !ttsAvailable) return
    OutlinedButton(
        onClick = {
            if (reading) {
                voice.stopSpeaking()
            } else {
                voice.setReadAloudEnabled(true)
                voice.speak(speech.take(MAX_SPOKEN_CHARS))
            }
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("tutor_listen").semantics {
            contentDescription = if (reading) "Detener el audio" else "Escuchar este contenido en voz alta"
        },
    ) { Text(if (reading) "Detener audio" else "Escuchar") }
}

@Composable
private fun LearnContent(
    tutor: TutorState,
    ui: TutorUiState,
    listenButton: @Composable () -> Unit,
    onRequestAi: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    TutorMessageCard(tutor.title, tutor.text)
    listenButton()
    if (tutor.screen == TutorStep.EXPLANATION) AiExplanationBlock(ui.ai, onRequestAi)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (TutorAction.BACK in tutor.actions) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("tutor_back"),
            ) { Text("Atrás") }
        }
        if (TutorAction.NEXT in tutor.actions) {
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("tutor_next"),
            ) { Text(TutorPresentation.nextLabel(tutor)) }
        }
    }
    if (TutorAction.SKIP_LEARN in tutor.actions) {
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("tutor_skip")) {
            Text("Ir a practicar")
        }
    }
    // El temario y las referencias no van en el flujo principal: se abren al pedirlos.
    var sources by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { sources = !sources }, modifier = Modifier.heightIn(min = 48.dp).testTag("tutor_sources_toggle")) {
        Text(if (sources) "Ocultar detalles y fuentes" else "Detalles y fuentes")
    }
    if (sources) Text(
        ui.topic.temarioLabel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("tutor_temario"),
    )
}

@Composable
private fun ExerciseContent(
    tutor: TutorState,
    canAnswer: Boolean,
    voiceAvailable: Boolean,
    listenButton: @Composable () -> Unit,
    selected: Int?,
    onAnswer: (Int) -> Unit,
    onSolution: () -> Unit,
    onDontKnow: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onVoice: () -> Unit,
    onAskTutor: (() -> Unit)?,
) {
    val exercise = tutor.exercise ?: return
    if (tutor.screen == TutorStep.QUESTION) {
        Text(tutor.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    StatementCard(exercise.statement)
    val message = TutorPresentation.message(tutor)
    if (tutor.screen != TutorStep.QUESTION && message.isNotBlank()) {
        TutorMessageCard(feedbackTitle(tutor.screen), message)
    }
    TutorPresentation.visibleHints(tutor).forEachIndexed { i, hint -> HintCard(i + 1, Exercise.HINT_COUNT, hint) }
    exercise.options.forEachIndexed { i, option ->
        val eliminated = i in exercise.eliminated
        OptionButton(
            index = i,
            text = option,
            enabled = canAnswer && !eliminated,
            eliminated = eliminated,
            correct = exercise.correctIndex == i,
            selected = canAnswer && selected == i,
            onClick = { onAnswer(i) },
        )
    }
    if (canAnswer && voiceAvailable) {
        OutlinedButton(
            onClick = onVoice,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("tutor_voice"),
        ) { Text("Responder con la voz") }
    }
    listenButton()
    // El tutor no da la respuesta: conversa con preguntas. En la comprobación sin ayuda no se ofrece.
    if (onAskTutor != null && tutor.phase == Phase.GUIDED_PRACTICE) {
        TextButton(
            onClick = onAskTutor,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("tutor_ask"),
        ) { Text("Preguntarle al tutor") }
    }
    if (TutorAction.SHOW_SOLUTION in tutor.actions) {
        OutlinedButton(
            onClick = onSolution,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("tutor_solution"),
        ) { Text("Ver la solución paso a paso") }
    }
    if (TutorAction.DONT_KNOW in tutor.actions) {
        OutlinedButton(
            onClick = onDontKnow,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("tutor_dont_know"),
        ) { Text("No sé") }
    }
    if (TutorAction.RETRY in tutor.actions) {
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("tutor_retry"),
        ) { Text("Probar uno parecido") }
    }
    if (TutorAction.NEXT in tutor.actions) {
        val primary = TutorAction.RETRY !in tutor.actions
        val nextModifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("tutor_next")
        if (primary) {
            Button(onClick = onNext, modifier = nextModifier) { Text(TutorPresentation.nextLabel(tutor)) }
        } else {
            OutlinedButton(onClick = onNext, modifier = nextModifier) { Text(TutorPresentation.nextLabel(tutor)) }
        }
    }
}

@Composable
private fun SummaryContent(
    tutor: TutorState,
    ui: TutorUiState,
    listenButton: @Composable () -> Unit,
    onHour: (Int) -> Unit,
    onSchedule: () -> Unit,
    onDecline: () -> Unit,
    onOpenAgenda: () -> Unit,
    onRestart: () -> Unit,
    onBackToTopics: () -> Unit,
) {
    TutorMessageCard(tutor.title, tutor.text)
    listenButton()
    val summary = tutor.summary
    if (summary != null) {
        SummaryCard(summary, ui.hintsUsed)
        ReviewProposalCard(ui.review, onHour, onSchedule, onDecline, onOpenAgenda)
        if (summary.unaidedReview.isNotEmpty()) {
            Text(
                "Revisa la comprobación sin ayuda",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
            )
            summary.unaidedReview.forEachIndexed { i, item -> UnaidedReviewCard(i + 1, item) }
        }
    }
    Button(
        onClick = onRestart,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("tutor_restart"),
    ) { Text("Practicar este tema otra vez") }
    OutlinedButton(
        onClick = onBackToTopics,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("tutor_topics_back"),
    ) { Text("Volver a los temas") }
}

/**
 * Estado del dictado de la respuesta. Lo dictado solo propone una alternativa: el estudiante la confirma. Nada se
 * responde mientras no pulse «Confirmar».
 */
@Composable
private fun VoiceAnswerCard(
    phase: VoicePhase,
    message: String,
    partial: String,
    transcript: String,
    option: Int?,
    canAnswer: Boolean,
    onConfirm: (Int) -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    val active = phase == VoicePhase.REQUESTING || phase == VoicePhase.LISTENING || phase == VoicePhase.TRANSCRIBING
    if (!active && phase != VoicePhase.REVIEW && phase != VoicePhase.ERROR) return
    Card(
        modifier = Modifier.fillMaxWidth().testTag("tutor_voice_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                active -> {
                    Text(
                        message.ifBlank { "Escuchando…" },
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (partial.isNotBlank()) {
                        Text("Parcial, no enviado: $partial", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Cancelar dictado")
                    }
                }
                phase == VoicePhase.REVIEW && option != null -> {
                    val letter = OPTION_LETTERS[option]
                    Text(
                        "Escuché: «$transcript». ¿Respondes la opción $letter?",
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = { onConfirm(option) },
                        enabled = canAnswer,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("tutor_voice_confirm"),
                    ) { Text("Confirmar opción $letter") }
                    OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Descartar")
                    }
                }
                phase == VoicePhase.REVIEW -> {
                    Text(
                        "No entendí qué alternativa quieres. Escuché: «$transcript». Di por ejemplo «opción B».",
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Dictar otra vez") }
                    OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Descartar")
                    }
                }
                else -> {
                    Text(
                        message.ifBlank { "La voz no está disponible. Puedes tocar una alternativa." },
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Entendido")
                    }
                }
            }
        }
    }
}

private fun feedbackTitle(screen: TutorStep): String = when (screen) {
    TutorStep.WRONG_ANSWER -> "Revisemos"
    TutorStep.CORRECT -> "¡Muy bien!"
    TutorStep.SOLUTION -> "Solución paso a paso"
    else -> "Tutor"
}

/** `true` si el estudiante quitó las animaciones del sistema (escala de animación en 0). */
private fun animationsDisabled(context: Context): Boolean = try {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (_: Exception) {
    false
}

/**
 * Emoción de la mascota animada para cada paso del tutor. Durante la comprobación sin ayuda nunca muestra si se
 * acertó: solo escribe concentrada hasta el resumen.
 */
internal fun tutorEmotion(tutor: TutorState): String = when {
    tutor.phase == Phase.UNAIDED_CHECK && tutor.screen != TutorStep.SUMMARY -> "write"
    else -> when (tutor.screen) {
        TutorStep.EXPLANATION, TutorStep.WORKED_EXAMPLE -> "read"
        TutorStep.QUESTION -> "think"
        TutorStep.HINT -> "idea"
        TutorStep.WRONG_ANSWER -> "confused"
        TutorStep.SOLUTION -> "present"
        TutorStep.CORRECT -> "happy"
        TutorStep.UNAIDED_INTRO -> "alert"
        TutorStep.SUMMARY -> when (tutor.cue) {
            pe.studyplanner.aipet.domain.tutor.PetCue.CELEBRATE -> "trophy"
            pe.studyplanner.aipet.domain.tutor.PetCue.REST -> "rest"
            else -> "progress"
        }
    }
}

/** «Pistas 1/3», siempre a la vista durante la práctica guiada. */
@Composable
private fun HintCounter(used: Int, total: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.padding(end = 12.dp).semantics { contentDescription = "Pistas usadas: $used de $total" }.testTag("tutor_hint_counter"),
    ) {
        Text("Pistas $used/$total", Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

/** Barra inferior de la pregunta: «Otra pista» y «Responder». */
@Composable
private fun AnswerBar(canHint: Boolean, firstHint: Boolean, canAnswer: Boolean, onHint: () -> Unit, onAnswer: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canHint) OutlinedButton(
                onClick = onHint,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("tutor_hint"),
            ) { Text(if (firstHint) "Pedir pista" else "Otra pista", maxLines = 1) }
            Button(
                onClick = onAnswer,
                enabled = canAnswer,
                modifier = Modifier.weight(if (canHint) 1.4f else 1f).heightIn(min = 56.dp).testTag("tutor_answer"),
            ) { Text("Responder", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
