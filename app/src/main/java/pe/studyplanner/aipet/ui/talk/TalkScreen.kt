package pe.studyplanner.aipet.ui.talk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import pe.studyplanner.aipet.R
import pe.studyplanner.aipet.domain.academic.ActivityTime
import pe.studyplanner.aipet.domain.agent.AgentAction
import pe.studyplanner.aipet.domain.agent.AgentGateway
import pe.studyplanner.aipet.domain.agent.AgentProposal
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.domain.voice.VoicePhase
import pe.studyplanner.aipet.domain.voice.VoiceSession
import pe.studyplanner.aipet.platform.voice.AndroidVoicePlatform
import pe.studyplanner.aipet.ui.agent.AgentChatViewModel
import pe.studyplanner.aipet.ui.components.AppSection
import pe.studyplanner.aipet.ui.components.PetAvatar
import pe.studyplanner.aipet.ui.components.PetStill
import pe.studyplanner.aipet.ui.components.ProfileAvatarButton
import pe.studyplanner.aipet.ui.components.SectionNavigation

private val spanish: Locale = Locale.forLanguageTag("es-PE")

/** Respuestas cortas para confirmar por voz una propuesta. El borrado siempre exige el botón. */
internal object TalkReplies {
    private fun plain(text: String) = java.text.Normalizer.normalize(text.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    fun isYes(text: String): Boolean = plain(text).let {
        it in setOf("si", "si por favor", "claro", "dale", "ok", "okay", "vale", "confirmo", "confirmar", "guardalo", "guarda", "guardar", "hazlo", "si guardalo", "si hazlo", "de acuerdo", "esta bien", "correcto", "si claro")
    }

    fun isNo(text: String): Boolean = plain(text).let {
        it in setOf("no", "no gracias", "cancela", "cancelar", "descarta", "descartar", "mejor no", "todavia no", "ahora no", "no lo guardes")
    }

    /** Respuesta de la IA sin las marcas internas («No se ejecutó ninguna acción.», «IA:»). */
    fun answerOnly(text: String): String = text.removePrefix(NO_ACTION).removePrefix("IA: ")

    /** Si el texto es una respuesta de la IA en la nube. */
    fun isAi(text: String?): Boolean = text != null && text.removePrefix(NO_ACTION).startsWith("IA: ")

    private const val NO_ACTION = "No se ejecutó ninguna acción. "

    /** Quita marcas que no deben leerse en voz alta. */
    fun forSpeech(text: String): String = answerOnly(text).replace(Regex("[*#_`>|]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private val zonedIso = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?(Z|[+-]\\d{2}:\\d{2})(\\[[^\\]]+])?")
    private val dateIso = Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b")
    private val dayTime = DateTimeFormatter.ofPattern("EEE d 'de' MMM, HH:mm", spanish)
    private val dayOnly = DateTimeFormatter.ofPattern("EEE d 'de' MMM", spanish)

    /** Fechas ISO del servicio local en palabras: «vie 6 de nov, 18:30». */
    fun humanDates(text: String): String = text
        .replace(zonedIso) { m -> runCatching { ZonedDateTime.parse(m.value, DateTimeFormatter.ISO_DATE_TIME).format(dayTime) }.getOrDefault(m.value) }
        .replace(dateIso) { m -> runCatching { LocalDate.parse(m.value).format(dayOnly) }.getOrDefault(m.value) }

    /**
     * El servicio local responde con datos técnicos (ID, versión, espacio de datos, estados en inglés). En la conversación
     * se dice simple; el historial completo conserva el texto original.
     */
    private val alertReceipt = Regex("^Aviso [0-9a-fA-F-]{8,}: .*Estado actual: ([A-Z_]+)\\.?$", RegexOption.DOT_MATCHES_ALL)

    fun friendly(text: String): String = when {
        text.startsWith("Modo DEMO_SIN_IA") -> "Sin Internet entiendo órdenes cortas y los temas que tengo guardados. Prueba: «¿qué tengo hoy?», «¿qué es la mitosis?» o «recuérdame estudiar en 30 minutos»."
        // Comprobante de un aviso: se dice si quedó programado y qué falta, sin ID ni estados internos.
        alertReceipt.matches(text.trim()) -> when (alertReceipt.find(text.trim())?.groupValues?.get(1)) {
            "EXACT", "FLEXIBLE", "PENDING" -> "Recordatorio guardado. Te avisaré a esa hora."
            "NEEDS_EXACT" -> "Recordatorio guardado. Para que suene a la hora exacta, permite las alarmas exactas en Agenda › Recordatorios."
            "NEEDS_NOTIFICATIONS" -> "Recordatorio guardado. Activa las notificaciones en Agenda › Recordatorios para recibir el aviso."
            else -> "Recordatorio guardado. Revisa su estado en Agenda › Recordatorios."
        }
        else -> humanDates(answerOnly(text))
            .replace(Regex(" · ID [^\\n·]+"), "")
            .replace(Regex("\\s*\\((PERSONAL|DEMO)\\)"), "")
            .replace(Regex(" en (PERSONAL|DEMO)\\."), ".")
            .replace(" · TODO", " · pendiente").replace(" · IN_PROGRESS", " · en curso")
            .replace("No hay tareas pendientes.", "No tienes tareas pendientes.")
            .replace("Aviso guardado; consulta el estado real de programación.", "Recordatorio guardado. En Agenda › Recordatorios ves si el aviso quedó activo.")
            .replace("Tarea guardada y verificada en la agenda local.", "Tarea guardada en tu agenda.")
            .replace("Examen guardado y verificado en la agenda local.", "Examen guardado en tu agenda.")
            .replace("Propuesta descartada. No se aplicaron sus cambios.", "Cancelado. No se guardó nada.")
            .replace("No hay actividades en ese intervalo.", "No tienes actividades programadas para ese periodo.")
            .trim()
    }

    /** Líneas útiles de la vista previa de una propuesta, sin identificadores internos ni avisos técnicos. */
    fun previewLines(preview: String): List<String> = preview.lines().map { it.trim() }.filter { line ->
        line.isNotBlank() && !line.startsWith("Espacio:") && !line.startsWith("Nada se guarda") && !line.startsWith("ID:") &&
            !line.contains("Versión:") && !line.startsWith("Precisión elegida")
    }.map { humanDates(it).replace(Regex("\\s*\\([0-9a-f-]{8,}\\)"), "") }
}

/** Lo que el estudiante necesita revisar antes de guardar: qué es, título, cuándo y aviso. */
internal data class ProposalSummary(val kind: String, val title: String, val time: String?, val note: String?, val confirmLabel: String)

internal fun summarize(proposal: AgentProposal, zoneId: String): ProposalSummary {
    val lines = TalkReplies.previewLines(proposal.preview)
    fun fromPreview(prefix: String) = lines.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim().orEmpty()
    fun timeOf(t: ActivityTime): String = when (t) {
        ActivityTime.Unscheduled -> "Sin fecha"
        is ActivityTime.DateOnly -> t.date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", spanish))
        is ActivityTime.Timed -> t.start.atZone(ZoneId.of(zoneId)).format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, HH:mm", spanish))
        is ActivityTime.Weekly -> "Cada semana, ${t.rule.startTime}"
    }
    fun at(i: Instant, zone: String) = i.atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, HH:mm", spanish))
    return when (val a = proposal.action) {
        is AgentAction.CreateTask -> ProposalSummary("Nueva tarea", a.title, timeOf(a.time), null, "Guardar")
        is AgentAction.CreateExam -> ProposalSummary("Nuevo examen", a.title, timeOf(a.time), null, "Guardar")
        is AgentAction.SetAlarm -> ProposalSummary(if (a.reminder) "Nuevo recordatorio" else "Nueva alarma", a.title, at(a.at, a.zone),
            "Aviso a esa hora", "Guardar")
        is AgentAction.RenameTask -> ProposalSummary("Cambiar nombre", a.title, null, fromPreview("Renombrar tarea:").ifBlank { null }, "Guardar")
        is AgentAction.CompleteTask -> ProposalSummary("Completar tarea", fromPreview("Completar tarea:"), null, "Sus avisos se desactivarán", "Completar")
        is AgentAction.DeleteTask -> ProposalSummary("Eliminar tarea", fromPreview("Eliminar solo esta tarea:"), null, "No se puede deshacer", "Eliminar")
        is AgentAction.CancelAlarm -> ProposalSummary("Desactivar aviso", fromPreview("Desactivar aviso:"), null, null, "Desactivar")
        is AgentAction.PlanWeek -> ProposalSummary("Organizar semana", "${a.days} días desde ${a.start.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", spanish))}",
            null, lines.drop(1).take(4).joinToString("\n").ifBlank { null }, "Guardar plan")
        is AgentAction.ChangePetName -> ProposalSummary("Cambiar nombre de la mascota", a.name, null, null, "Guardar")
        AgentAction.HidePet -> ProposalSummary("Ocultar la mascota", "Puedes mostrarla después desde Perfil", null, null, "Ocultar")
    }
}

private enum class TalkUi { READY, LISTENING, PROCESSING, SPEAKING, ERROR }

private const val PREFS = "talk_v1"

/**
 * Pestaña «Asistente»: mascota, última respuesta y entrada por texto o voz. Abrir esta pantalla nunca enciende el
 * micrófono; cada estado de voz tiene su propio botón (Hablar, Detener escucha, Cancelar, Detener audio, Reintentar).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TalkScreen(
    gateway: AgentGateway,
    profile: UserProfile,
    voiceEnabled: Boolean,
    onNavigate: (AppSection) -> Unit,
    onOpenChat: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenReview: () -> Unit = onOpenPractice,
    onProfile: () -> Unit = { onNavigate(AppSection.SETTINGS) },
    onPersonalize: (() -> Unit)? = null,
    onDevice: (() -> Unit)? = null,
    onTogglePet: (() -> Unit)? = null,
    listenOnOpen: Boolean = false,
    /** «Agéndame…»: lo nuevo ya se guardó; se abre la Agenda con eso marcado. */
    onAgendaSaved: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val app = appContext as? pe.studyplanner.aipet.StudyPlannerApplication
    val model: AgentChatViewModel = viewModel(factory = remember(gateway) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AgentChatViewModel(gateway) as T
        }
    })
    val state by model.state.collectAsStateWithLifecycle()
    var autoTried by remember { mutableStateOf<String?>(null) }
    fun autoSaves(p: pe.studyplanner.aipet.domain.agent.AgentProposal?): Boolean = p != null && onAgendaSaved != null &&
        p.scope == pe.studyplanner.aipet.domain.academic.DatasetScope.PERSONAL &&
        (p.action is AgentAction.CreateTask || p.action is AgentAction.CreateExam)
    val scope = rememberCoroutineScope()
    val faceSync = remember(voiceEnabled) {
        if (voiceEnabled) app?.petLink?.let { pe.studyplanner.aipet.domain.companion.SpeechFaceSync(it, scope) } else null
    }
    val voice = remember(voiceEnabled) { if (voiceEnabled) VoiceSession(AndroidVoicePlatform(appContext), scope, faceSync) else null }
    DisposableEffect(voice) { onDispose { voice?.close() } }
    DisposableEffect(faceSync) { onDispose { faceSync?.release() } }
    val voiceState = if (voice != null) voice.state.collectAsStateWithLifecycle().value else null
    val phase = voiceState?.phase ?: VoicePhase.IDLE
    val listening = phase in setOf(VoicePhase.REQUESTING, VoicePhase.LISTENING, VoicePhase.TRANSCRIBING)
    val speaking = phase in setOf(VoicePhase.PREPARING_SPEECH, VoicePhase.SPEAKING)

    val prefs = remember { appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var noticeAccepted by remember { mutableStateOf(prefs.getBoolean("notice", false)) }
    var muted by remember { mutableStateOf(prefs.getBoolean("muted", false)) }
    var showNotice by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("") }
    var draft by rememberSaveable { mutableStateOf("") }
    var celebrating by remember { mutableStateOf(false) }
    var greeting by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    // Qué pidió la última acción de voz: así un fallo de lectura no se confunde con uno de escucha.
    var lastVoiceAction by remember { mutableStateOf("") }
    // Si la lectura en voz falla una vez, no se vuelve a intentar en esta sesión ni se repite el aviso.
    var speechBroken by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { delay(2500); greeting = false }
    LaunchedEffect(voice, muted) { voice?.setReadAloudEnabled(!muted) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(voice, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) voice?.suspendSession()
            if (event == Lifecycle.Event.ON_RESUME) voice?.refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun micGranted() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { lastVoiceAction = "listen"; voice?.startDictation(true) }
        else hint = "Sin permiso de micrófono. Puedes escribir."
    }
    val writeInstead: () -> Unit = {
        if (phase == VoicePhase.ERROR) voice?.discardTranscript()
        model.clearError(); hint = ""
        focus.requestFocus(); keyboard?.show()
    }
    val listen: () -> Unit = {
        hint = ""
        model.clearError()
        when {
            voice == null -> { hint = "La voz no está disponible en este teléfono. Puedes escribir."; writeInstead() }
            !profile.petVisible -> hint = "Muestra a ${profile.petName} para hablar con voz."
            !noticeAccepted -> showNotice = true
            micGranted() -> { lastVoiceAction = "listen"; voice.startDictation(true) }
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val send: (String) -> Unit = { text ->
        val clean = text.trim()
        if (clean.isNotEmpty()) { voice?.stopSpeaking(); hint = ""; model.send(clean) }
    }

    // «Hablar» desde Hoy: el estudiante ya pidió hablar, así que se escucha una vez al abrir (nunca al volver).
    var listenedOnOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(listenOnOpen) {
        if (listenOnOpen && !listenedOnOpen) { listenedOnOpen = true; delay(450); listen() }
    }

    // Lo dictado se envía solo; los cambios en la agenda siguen esperando «sí» o el botón.
    LaunchedEffect(phase) {
        if (phase == VoicePhase.REVIEW) {
            val text = voice?.takeTranscript().orEmpty()
            val proposal = state.proposal
            when {
                text.isBlank() -> Unit
                proposal != null && proposal.action !is AgentAction.DeleteTask && TalkReplies.isYes(text) -> model.confirm()
                proposal != null && TalkReplies.isNo(text) -> model.discard()
                proposal != null -> hint = "Responde «sí» o «no», o usa los botones."
                else -> send(text)
            }
        }
    }
    LaunchedEffect(state.receipt?.requestId) {
        if (state.receipt?.status == "COMMITTED") { celebrating = true; delay(3500); celebrating = false }
    }
    // «Agéndame…» por voz o texto: una tarea o un examen nuevos se guardan sin preguntar (se pueden deshacer en la
    // Agenda), la mascota lo dice y se abre la Agenda con lo nuevo marcado. Cambiar o borrar sigue pidiendo «sí».
    var autoAction by remember { mutableStateOf<AgentAction?>(null) }
    // Espera a que termine de prepararse la propuesta (busy en falso) y la confirma una sola vez; si falla, queda la
    // tarjeta normal con «Guardar» para reintentar.
    LaunchedEffect(state.proposal?.id, state.busy) {
        val p = state.proposal ?: return@LaunchedEffect
        if (!state.busy && autoSaves(p) && autoTried != p.id) { autoTried = p.id; autoAction = p.action; model.confirm() }
    }
    LaunchedEffect(state.receipt?.requestId) {
        val r = state.receipt ?: return@LaunchedEffect
        val a = autoAction ?: return@LaunchedEffect
        autoAction = null
        val id = r.entityIds.firstOrNull()
        if (r.status != "COMMITTED" || id == null || onAgendaSaved == null) return@LaunchedEffect
        val (title, time) = when (a) {
            is AgentAction.CreateTask -> a.title to a.time
            is AgentAction.CreateExam -> a.title to a.time
            else -> return@LaunchedEffect
        }
        pe.studyplanner.aipet.ui.academic.AgendaFocusBus.show(
            pe.studyplanner.aipet.ui.academic.AgendaFocus(id, pe.studyplanner.aipet.ui.academic.AgendaFocusBus.dateOf(time), title))
        // Deja que diga «Tarea guardada en tu agenda» antes de cambiar de pantalla.
        delay(600)
        voice?.let { v -> kotlinx.coroutines.withTimeoutOrNull(3500) {
            v.state.first { it.phase != VoicePhase.PREPARING_SPEECH && it.phase != VoicePhase.SPEAKING }
        } }
        onAgendaSaved()
    }

    val lastAssistant = state.messages.lastOrNull { it.role == "assistant" }
    val proposal = state.proposal
    val autoSaving = autoSaves(proposal) && (autoTried != proposal?.id || state.busy)
    val summary = proposal?.takeIf { !autoSaving }?.let { runCatching { summarize(it, profile.zoneId) }.getOrNull() }
    val speakKey = proposal?.id ?: lastAssistant?.id ?: ""
    var spokenKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.ready) { if (state.ready && spokenKey == null) spokenKey = speakKey }
    LaunchedEffect(speakKey, state.ready) {
        val baseline = spokenKey
        if (!state.ready || baseline == null || speakKey.isBlank() || speakKey == baseline) return@LaunchedEffect
        spokenKey = speakKey
        // Lo que se guarda solo no pregunta «¿Lo guardo?»: se dice después que quedó guardado.
        if (autoSaves(state.proposal)) return@LaunchedEffect
        greeting = false
        // Sin motor de lectura en el teléfono no se intenta leer: la respuesta ya está en pantalla.
        if (!muted && !speechBroken && voice != null && !listening && voiceState?.capabilities?.ttsEngineAvailable != false) {
            val text = summary?.let { "${it.kind}: ${it.title}${it.time?.let { t -> ", $t" }.orEmpty()}. ¿Lo guardo? Di sí o no." }
                ?: TalkReplies.friendly(lastAssistant?.text.orEmpty())
            lastVoiceAction = "speak"
            voice.speak(TalkReplies.forSpeech(text).take(3800))
        }
    }
    // Si la lectura en voz falla (por ejemplo, el teléfono no tiene motor de voz), la respuesta sigue en pantalla:
    // no es un error de escucha ni pide reintentar.
    LaunchedEffect(phase, lastVoiceAction) {
        if (phase == VoicePhase.ERROR && lastVoiceAction == "speak") {
            voice?.discardTranscript()
            lastVoiceAction = ""
            if (!speechBroken) {
                speechBroken = true
                val note = "La lectura en voz no está disponible; las respuestas se muestran en pantalla."
                hint = note
                delay(5000)
                if (hint == note) hint = ""
            }
        }
    }

    val ui = when {
        listening -> TalkUi.LISTENING
        state.busy && state.ready -> TalkUi.PROCESSING
        speaking -> TalkUi.SPEAKING
        (phase == VoicePhase.ERROR && lastVoiceAction != "speak") || state.error.isNotBlank() -> TalkUi.ERROR
        else -> TalkUi.READY
    }
    val errorText = when {
        state.error.isNotBlank() -> state.error.substringBefore(". ").removeSuffix(".") + "."
        phase == VoicePhase.ERROR -> voiceState?.message?.substringBefore(". ")?.removeSuffix(".")?.takeIf { it.isNotBlank() && it.length <= 90 }?.let { "$it." } ?: "No te escuché bien."
        else -> ""
    }
    val welcome = (if (profile.studentName.isBlank()) "¡Hola!" else "¡Hola, ${profile.studentName}!") +
        " Soy ${profile.petName}. Pregúntame un tema del examen. Funciono sin Internet."
    // Dos temas al azar de lo que sabe la mascota: un toque y lo explica (sin Internet).
    val suggestions = remember {
        app?.studyKnowledge?.entries?.filter { it.id.startsWith("g-") && it.title.length <= 26 }?.shuffled()?.take(2)?.map { it.title }.orEmpty()
    }
    val bubble = when {
        listening -> voiceState?.partial?.takeIf { it.isNotBlank() }?.let { "«$it…»" } ?: "Te escucho…"
        state.busy && state.ready -> "Déjame pensar…"
        autoSaving -> "Lo pongo en tu agenda…"
        summary != null -> "Revisa antes de guardar."
        proposal != null -> TalkReplies.previewLines(proposal.preview).joinToString("\n")
        lastAssistant != null -> TalkReplies.friendly(lastAssistant.text)
        else -> welcome
    }
    val fromAi = proposal == null && !listening && !state.busy && TalkReplies.isAi(lastAssistant?.text)
    val mood = when {
        listening -> "listen"
        state.busy -> "think"
        speaking -> "speak"
        celebrating -> "celebrate"
        ui == TalkUi.ERROR -> "confused"
        proposal != null -> when (proposal.action) {
            is AgentAction.CreateTask -> "tasks"
            is AgentAction.SetAlarm, is AgentAction.CancelAlarm -> "reminder"
            is AgentAction.CreateExam, is AgentAction.PlanWeek -> "plan"
            is AgentAction.CompleteTask -> "happy"
            is AgentAction.DeleteTask -> "worried"
            else -> "attentive"
        }
        greeting -> "wave"
        else -> "idle"
    }
    val imeVisible = WindowInsets.isImeVisible
    val canSend = state.ready && !state.busy && proposal == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Asistente", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = { voice?.stopSpeaking(); onNavigate(AppSection.TODAY) }, modifier = Modifier.testTag("talk_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver a Hoy")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("talk_menu")) { Icon(Icons.Default.MoreVert, "Más opciones del asistente") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Historial de la conversación") }, onClick = { menu = false; onOpenChat() })
                            DropdownMenuItem(text = { Text(if (muted) "Activar voz de ${profile.petName}" else "Silenciar voz de ${profile.petName}") }, onClick = {
                                menu = false
                                muted = !muted
                                prefs.edit().putBoolean("muted", muted).apply()
                                if (muted) voice?.stopSpeaking()
                            })
                            if (onPersonalize != null) DropdownMenuItem(text = { Text("Personalizar mascota") }, onClick = { menu = false; onPersonalize() })
                            if (onDevice != null) DropdownMenuItem(text = { Text("Mascota física") }, onClick = { menu = false; onDevice() })
                        }
                    }
                    ProfileAvatarButton(profile, onProfile)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = { SectionNavigation(AppSection.CHAT, onNavigate) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (imeVisible) {
                // Con el teclado abierto la mascota se reduce: campo y Enviar siempre quedan a la vista.
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        PetStill(profile.avatar, Modifier.size(52.dp), contentDescription = profile.petName)
                    }
                    Bubble(bubble, fromAi, Modifier.weight(1f), maxHeight = 88)
                }
                Spacer(Modifier.weight(1f))
            } else {
                Box(Modifier.fillMaxWidth().weight(1f).heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                    if (profile.petVisible) {
                        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val side = minOf(maxWidth, maxHeight, 300.dp)
                            PetAvatar(profile.avatar, Modifier.size(side).testTag("talk_pet"), mood = mood)
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.align(Alignment.TopStart).padding(top = 6.dp)) {
                            Text(profile.petName, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PetStill(profile.avatar, Modifier.size(96.dp), contentDescription = null)
                            Text("${profile.petName} está oculto.", style = MaterialTheme.typography.bodyMedium)
                            if (onTogglePet != null) OutlinedButton(onClick = onTogglePet, modifier = Modifier.heightIn(min = 48.dp)) { Text("Mostrar a ${profile.petName}") }
                        }
                    }
                }
                Bubble(bubble, fromAi, Modifier.fillMaxWidth(), maxHeight = 168)
            }

            if (proposal != null && !autoSaving) ProposalCard(summary, state.busy,
                onSave = { model.confirm() },
                onEdit = if (proposal.action is AgentAction.DeleteTask) null else ({
                    val asked = state.messages.lastOrNull { it.role == "user" }?.text.orEmpty()
                    model.discard(); draft = asked; focus.requestFocus(); keyboard?.show()
                }),
                onCancel = { model.discard() })

            if (!imeVisible && proposal == null) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { title ->
                        SuggestionChip(onClick = { send(title) }, enabled = canSend, label = { Text(title, maxLines = 1) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                    SuggestionChip(onClick = { send("muestra mi agenda hoy") }, enabled = canSend, label = { Text("Mi día") }, modifier = Modifier.heightIn(min = 48.dp))
                    SuggestionChip(onClick = onOpenReview, label = { Text("Practicar") }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }

            if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })

            OutlinedTextField(
                draft, { if (it.length <= 4000) draft = it },
                Modifier.fillMaxWidth().focusRequester(focus).testTag("talk_input")
                    // Enter envía (también con teclado físico); Mayús+Enter conserva el salto de línea.
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter && !event.isShiftPressed) {
                            if (event.type == KeyEventType.KeyDown && draft.isNotBlank() && canSend) { send(draft); draft = "" }
                            true
                        } else false
                    },
                placeholder = { Text("Escribe o toca el micrófono", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                enabled = canSend, maxLines = 3, shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (draft.isNotBlank()) { send(draft); draft = "" } }),
                trailingIcon = {
                    if (draft.isNotBlank()) IconButton(onClick = { send(draft); draft = "" }, enabled = canSend,
                        modifier = Modifier.testTag("talk_send")) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = MaterialTheme.colorScheme.primary) }
                },
            )

            if (!imeVisible) VoiceControls(ui, profile.petName, voice != null, state.ready, state.cancellable, errorText,
                onTalk = listen,
                onStopListening = { voice?.cancel(); hint = "Escucha detenida. No se envió nada." },
                onCancel = { model.cancel() },
                onStopAudio = { voice?.stopSpeaking() },
                onRetry = {
                    when {
                        !state.ready -> model.retryLoad()
                        phase == VoicePhase.ERROR -> listen()
                        else -> { model.clearError(); model.retryLast() }
                    }
                },
                onWrite = writeInstead)
            Spacer(Modifier.height(2.dp))
        }
    }

    if (showNotice) AlertDialog(
        onDismissRequest = { showNotice = false },
        title = { Text("Antes de hablar") },
        text = {
            Text("El micrófono se enciende solo cuando tocas «Hablar» y se apaga al terminar (máximo 15 segundos). El reconocimiento de voz de Android puede usar Internet. StudyPlanner no guarda audio. Antes de guardar algo en tu agenda siempre te preguntaré.",
                Modifier.verticalScroll(rememberScrollState()))
        },
        confirmButton = {
            Button(onClick = {
                showNotice = false
                noticeAccepted = true
                prefs.edit().putBoolean("notice", true).apply()
                if (micGranted()) { lastVoiceAction = "listen"; voice?.startDictation(true) } else permission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text("Hablar") }
        },
        dismissButton = { TextButton(onClick = { showNotice = false; writeInstead() }) { Text("Escribir") } },
    )
}

@Composable
private fun Bubble(text: String, fromAi: Boolean, modifier: Modifier, maxHeight: Int) {
    Surface(shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.testTag("talk_bubble")) {
        Column(Modifier.heightIn(max = maxHeight.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (fromAi) Text("Respuesta generada por IA; puede tener errores.", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProposalCard(summary: ProposalSummary?, busy: Boolean, onSave: () -> Unit, onEdit: (() -> Unit)?, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("talk_proposal"), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (summary != null) {
                Text(summary.kind, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (summary.title.isNotBlank()) Text(summary.title.replaceFirstChar { c -> c.titlecase(spanish) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                summary.time?.let { Text(it.replaceFirstChar { c -> c.titlecase(spanish) }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                summary.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSave, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("talk_yes")) {
                    Text(summary?.confirmLabel ?: "Guardar")
                }
                if (onEdit != null) OutlinedButton(onClick = onEdit, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("talk_edit")) { Text("Editar") }
                TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("talk_no")) { Text("Cancelar") }
            }
        }
    }
}

@Composable
private fun VoiceControls(ui: TalkUi, petName: String, voiceAvailable: Boolean, ready: Boolean, cancellable: Boolean, error: String,
    onTalk: () -> Unit, onStopListening: () -> Unit, onCancel: () -> Unit, onStopAudio: () -> Unit, onRetry: () -> Unit, onWrite: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().testTag("talk_voice_${ui.name.lowercase()}")) {
        when (ui) {
            TalkUi.ERROR -> Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetry, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("talk_retry")) { Text("Reintentar") }
                    OutlinedButton(onClick = onWrite, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("talk_write")) { Text("Escribir") }
                }
            }
            else -> Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (ui) {
                    TalkUi.LISTENING -> { ListeningBars(); Text("Escuchando", Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, fontWeight = FontWeight.SemiBold) }
                    TalkUi.PROCESSING -> { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp); Text("Preparando respuesta", Modifier.weight(1f)) }
                    TalkUi.SPEAKING -> { Icon(painterResource(R.drawable.ic_volume_on), null, tint = MaterialTheme.colorScheme.primary); Text("Hablando", Modifier.weight(1f), maxLines = 1) }
                    else -> {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
                        Text(if (voiceAvailable) "Listo" else "Voz no disponible; puedes escribir", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                when (ui) {
                    TalkUi.LISTENING -> OutlinedButton(onClick = onStopListening, modifier = Modifier.heightIn(min = 48.dp).testTag("talk_stop_listening")) { Text("Detener escucha") }
                    TalkUi.PROCESSING -> if (cancellable) OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp).testTag("talk_cancel")) { Text("Cancelar") }
                    TalkUi.SPEAKING -> OutlinedButton(onClick = onStopAudio, modifier = Modifier.heightIn(min = 48.dp).testTag("talk_stop_audio")) {
                        Icon(painterResource(R.drawable.ic_stop), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Detener audio")
                    }
                    else -> if (voiceAvailable) Button(onClick = onTalk, enabled = ready, modifier = Modifier.heightIn(min = 52.dp).testTag("talk_mic")
                        .semantics { contentDescription = "Hablar" }) {
                        Icon(painterResource(R.drawable.ic_mic), null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Hablar", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningBars() {
    val pulse = rememberInfiniteTransition(label = "escucha")
    val level by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "nivel")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(22.dp)) {
        listOf(.5f, .9f, 1f, .7f, .45f).forEachIndexed { i, base ->
            val h = (22 * (if (i % 2 == 0) base * level else base * (1.35f - level))).coerceIn(5f, 22f)
            Box(Modifier.width(4.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
        }
    }
}
