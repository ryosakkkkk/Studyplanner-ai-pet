package pe.studyplanner.aipet.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.domain.voice.*
import pe.studyplanner.aipet.ui.components.PetAvatar

/** Voice produces editable text only. This panel has no agent or database handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicePanel(
    session: VoiceSession,
    profile: UserProfile,
    onTogglePet: () -> Unit,
    onUseTranscript: (String) -> Unit,
    readText: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    operationState: String = "En reposo",
    autoReply: Boolean = false,
    onAutoReply: (Boolean) -> Unit = {},
    compact: Boolean = false,
    requestDictation: Int = 0,
    onRequestHandled: () -> Unit = {},
    operationMood: String = "idle",
) {
    val state by session.state.collectAsStateWithLifecycle()
    val petMood=when(state.phase) {
        VoicePhase.LISTENING->"listening"
        VoicePhase.SPEAKING->"speaking"
        VoicePhase.REQUESTING,VoicePhase.TRANSCRIBING,VoicePhase.PREPARING_SPEECH->"thinking"
        VoicePhase.ERROR->"alert"
        else->if(operationState=="Revisando la solicitud")"thinking" else operationMood
    }
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var foreground by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var privacy by remember { mutableStateOf(false) }
    var nameMode by remember { mutableStateOf(false) }
    var permissionPending by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf("") }
    var offline by rememberSaveable { mutableStateOf(false) }
    val onDeviceMode = offline || (!state.capabilities.speechAvailable && state.capabilities.onDeviceAvailable)
    var reducedMotion by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var edited by rememberSaveable(state.transcript) { mutableStateOf(state.transcript) }
    var lastPetName by remember { mutableStateOf(profile.petName) }
    val currentProfile by rememberUpdatedState(profile)
    val currentlyEnabled by rememberUpdatedState(enabled)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val authorized = permissionPending && foreground && currentlyEnabled && currentProfile.petVisible
        permissionPending = false
        if (!granted) permissionMessage = "Permiso de micrófono denegado. Puedes seguir escribiendo y revisarlo en los ajustes de Android."
        else if (authorized) session.startDictation(true, onDeviceMode, if (nameMode) currentProfile.petName else null)
        else permissionMessage = "Permiso actualizado. Pulsa Dictar de nuevo cuando estés en esta pantalla; no se inició una escucha automáticamente."
    }
    DisposableEffect(session, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_PAUSE) {
                foreground = false
                privacy = false
                permissionPending = false
                session.suspendSession()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                foreground = true
                session.refresh()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            foreground = false
            permissionPending = false
            owner.lifecycle.removeObserver(observer)
            if(!compact) session.suspendSession()
        }
    }
    LaunchedEffect(profile.petVisible, profile.petName, enabled) {
        if (!profile.petVisible || lastPetName != profile.petName) {
            privacy = false;permissionPending = false;session.suspendSession()
        } else if(!enabled) {
            privacy = false;permissionPending = false
            if(session.state.value.phase in setOf(VoicePhase.REQUESTING,VoicePhase.LISTENING,VoicePhase.TRANSCRIBING))session.cancel()
        }
        lastPetName = profile.petName
    }
    val listening = state.phase in setOf(VoicePhase.REQUESTING, VoicePhase.LISTENING, VoicePhase.TRANSCRIBING)
    val reading = state.phase in setOf(VoicePhase.PREPARING_SPEECH, VoicePhase.SPEAKING)
    val canDictate = enabled && foreground && profile.petVisible && (state.capabilities.speechAvailable || state.capabilities.onDeviceAvailable) && !listening && state.phase != VoicePhase.REVIEW
    LaunchedEffect(requestDictation) {
        if(requestDictation>0) {
            if(canDictate) {session.cancel();permissionMessage="";nameMode=false;privacy=true}
            else permissionMessage="El dictado no está disponible ahora. Puedes escribir o revisar las opciones de voz."
            onRequestHandled()
        }
    }
    val status = if (!profile.petVisible) "Mascota oculta · audio detenido" else when (state.phase) {
        VoicePhase.IDLE -> operationState
        VoicePhase.REQUESTING -> "Preparando micrófono"
        VoicePhase.LISTENING -> "Escuchando · sesión de hasta 15 segundos"
        VoicePhase.TRANSCRIBING -> "Esperando transcripción final"
        VoicePhase.REVIEW -> "Transcripción pendiente de tu revisión"
        VoicePhase.PREPARING_SPEECH -> "Preparando lectura"
        VoicePhase.SPEAKING -> "Leyendo en voz alta"
        VoicePhase.ERROR -> "Voz no disponible · puedes escribir"
    }
    val targetColor = if (listening) MaterialTheme.colorScheme.primary else if (reading) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
    val borderColor by animateColorAsState(targetColor, if (reducedMotion) snap() else tween(180), label = "Estado de voz")
    OutlinedCard(modifier.fillMaxWidth().testTag("voice_panel"), border = BorderStroke(1.dp, borderColor.copy(alpha=.35f)), colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.25f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if(compact) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    if(profile.petVisible) PetAvatar(profile.avatar,Modifier.size(88.dp),
                        mood=petMood,reducedMotion=reducedMotion)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(profile.petName,style=MaterialTheme.typography.titleMedium)
                        Text(status,Modifier.testTag("voice_state").semantics{liveRegion=LiveRegionMode.Polite},style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={session.cancel();permissionMessage="";nameMode=false;privacy=true},enabled=canDictate,contentPadding=PaddingValues(0.dp)) {Text("Tocar para hablar")}
                    }
                }
            } else {
                Text(profile.petName, style = MaterialTheme.typography.titleLarge)
                Button(onClick = { session.cancel(); permissionMessage = ""; nameMode = false; privacy = true }, enabled = canDictate, modifier = Modifier.fillMaxWidth()) { Text("Tocar para hablar") }
                if (profile.petVisible) {
                    PetAvatar(profile.avatar, Modifier.size(128.dp).align(androidx.compose.ui.Alignment.CenterHorizontally),
                        mood = petMood,
                        reducedMotion = reducedMotion)
                }
                Text(status, Modifier.testTag("voice_state").semantics { liveRegion = LiveRegionMode.Polite })
            }
            if(advanced) OutlinedButton(onClick = { permissionPending = false; privacy = false; session.suspendSession(); onTogglePet() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (profile.petVisible) "Ocultar mascota" else "Mostrar mascota")
            }
            if(!compact) Text("Habla, revisa y confirma. Tú decides qué se guarda.", style = MaterialTheme.typography.bodySmall)
            if (!state.capabilities.speechAvailable && !state.capabilities.onDeviceAvailable) Text("No hay un servicio de dictado disponible. Puedes escribir tu solicitud.")

            if(advanced) {
            OutlinedButton(onClick = { session.cancel(); permissionMessage = ""; nameMode = true; privacy = true }, enabled = canDictate && profile.petName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Escuchar mi nombre y dictar") }
            Text("Por nombre: di «${profile.petName}» al comienzo y luego la solicitud. Solo en una sesión visible de 15 segundos; no hay escucha permanente.")
            if (state.capabilities.onDeviceAvailable) {
                VoiceSwitch("Usar reconocimiento en el dispositivo", onDeviceMode, { offline = it }, state.capabilities.speechAvailable && !listening && state.phase != VoicePhase.REVIEW)
                Text("La disponibilidad del servicio no garantiza que tenga español instalado. Si falta el modelo, se informa el error; no se descarga ni cambia a remoto automáticamente.")
            } else Text("Este Android no ofrece un reconocedor local verificable. El servicio de dictado instalado puede necesitar red.")
            }
            if (listening) {
                Text("Micrófono solicitado por esta sesión. Cancelar descarta resultados parciales.")
                if (state.partial.isNotBlank()) Text("Parcial, no enviado: ${state.partial}")
                OutlinedButton(onClick = { session.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar dictado") }
            }
            if (permissionMessage.isNotBlank()) Text(permissionMessage, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (state.message.isNotBlank() && (!compact || advanced || state.phase==VoicePhase.ERROR || listening)) Text(state.message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (state.transcript.isNotBlank() && state.phase in setOf(VoicePhase.REVIEW, VoicePhase.ERROR)) {
                OutlinedTextField(edited, { if (it.length <= 4000) edited = it }, label = { Text("Transcripción para revisar") }, modifier = Modifier.fillMaxWidth().testTag("voice_transcript"), minLines = 2, maxLines = 6)
                Button(onClick = { if (session.takeTranscript() != null) onUseTranscript(edited.trim()) }, enabled = enabled && edited.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Revisar esta petición") }
                OutlinedButton(onClick = { session.discardTranscript() }, modifier = Modifier.fillMaxWidth()) { Text("Descartar transcripción") }
            }
            if(!compact || advanced) HorizontalDivider()
            if(advanced) VoiceSwitch("Activar lectura en voz alta", state.readAloudEnabled, session::setReadAloudEnabled, profile.petVisible)
            VoiceSwitch("Responder en voz alta", autoReply, { onAutoReply(it); session.setReadAloudEnabled(it); if (!it) session.stopSpeaking() }, profile.petVisible)
            if(advanced) Text("La lectura usa el motor de voz de Android. Puedes detenerla cuando quieras.")
            if (!state.capabilities.ttsEngineAvailable) Text("No hay un motor de lectura disponible. El texto permanece visible.")
            if(advanced) Button(onClick = { session.speak(readText) }, enabled = foreground && profile.petVisible && state.readAloudEnabled && state.capabilities.ttsEngineAvailable && readText.isNotBlank() && !listening, modifier = Modifier.fillMaxWidth()) { Text("Leer texto mostrado") }
            if (reading) OutlinedButton(onClick = session::stopSpeaking, modifier = Modifier.fillMaxWidth()) { Text("Detener audio") }
            TextButton(onClick = { advanced = !advanced }) { Text(if(advanced) "Menos opciones" else "Voz y opciones") }
            if(advanced) {
            Text("Velocidad de lectura")
            listOf(0.8f to "Lenta · 0,8×", 1f to "Normal · 1×", 1.2f to "Rápida · 1,2×").forEach { (value, label) ->
                FilterChip(selected = state.speechRate == value, onClick = { session.setSpeechRate(value) }, label = { Text(label) })
            }
            Text("Volumen de lectura; también depende del volumen de Android")
            listOf(0.5f to "Volumen bajo", 1f to "Volumen normal").forEach { (value, label) ->
                FilterChip(selected = state.speechVolume == value, onClick = { session.setSpeechVolume(value) }, label = { Text(label) })
            }
            VoiceSwitch("Reducir movimiento", reducedMotion, { reducedMotion = it }, true)
            Text("Estas opciones de voz se conservan solo mientras el chat está abierto. Ocultar la mascota o salir detiene el audio; la agenda y sus avisos se conservan.")
        }
    }
    }
    if (privacy) {
        // At large font sizes or in landscape, fixed AlertDialog slots can
        // consume all available height before laying out the consent text.
        // Scroll the complete consent, including both decisions, as one unit.
        val dialogMaxHeight = (LocalConfiguration.current.screenHeightDp - 32).coerceAtLeast(1).dp
        BasicAlertDialog(onDismissRequest = { privacy = false }) {
            Surface(modifier = Modifier.fillMaxWidth().heightIn(max = dialogMaxHeight),
                shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .testTag("voice_privacy_scroll").padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Antes de usar el micrófono", style = MaterialTheme.typography.headlineSmall)
                    Text(if (onDeviceMode) "Usaremos el servicio de reconocimiento del dispositivo, si tiene el idioma disponible. No guardamos audio. La captura dura hasta 15 segundos y no envía ni confirma cambios. Puedes cancelar y escribir."
                    else "El servicio de reconocimiento de Android puede enviar tu audio a sus servidores y necesitar Internet. StudyPlanner no guarda audio. La captura dura hasta 15 segundos y no envía ni confirma cambios. Continúa solo si aceptas usar ese servicio; también puedes escribir.")
                    Button(onClick = {
                        privacy = false
                        if (foreground && enabled && profile.petVisible) {
                            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) session.startDictation(true, onDeviceMode, if (nameMode) profile.petName else null)
                            else { permissionPending = true; permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Continuar con el dictado") }
                    OutlinedButton(onClick = { privacy = false }, modifier = Modifier.fillMaxWidth()) { Text("Ahora no") }
                }
            }
        }
    }
}

@Composable
private fun VoiceSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange, enabled = enabled, modifier = Modifier
            .then(if (label == "Activar lectura en voz alta") Modifier.testTag("voice_read_aloud") else Modifier)
            .semantics { contentDescription = label })
    }
}
