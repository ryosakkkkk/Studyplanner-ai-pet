package pe.studyplanner.aipet.ui.agent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pe.studyplanner.aipet.domain.academic.DatasetScope
import pe.studyplanner.aipet.domain.agent.*
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.domain.voice.VoiceSession
import pe.studyplanner.aipet.platform.voice.AndroidVoicePlatform
import pe.studyplanner.aipet.ui.voice.VoicePanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(gateway:AgentGateway,onBack:()->Unit,onOpenAgenda:(DatasetScope)->Unit,
    onOpenPlanner:(DatasetScope)->Unit,onOpenAlerts:(DatasetScope)->Unit,
    voiceEnabled:Boolean=false,profile:UserProfile=UserProfile(),onTogglePet:()->Unit={},
    voiceSession:VoiceSession?=null, onNavigate:((pe.studyplanner.aipet.ui.components.AppSection)->Unit)?=null,
    onAiSettings:(()->Unit)?=null) {
    val model:AgentChatViewModel=viewModel(factory=remember(gateway){object:ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=AgentChatViewModel(gateway) as T
    }})
    val state by model.state.collectAsStateWithLifecycle()
    val context=LocalContext.current.applicationContext
    val aiStore=(context as? pe.studyplanner.aipet.StudyPlannerApplication)?.aiConnection
    val aiConnection=aiStore?.state?.collectAsStateWithLifecycle()?.value
    val voiceScope=rememberCoroutineScope()
    // Mascota física (si hay una conectada): cara hablando mientras suena la lectura; al terminar vuelve a reposo.
    val faceSync=remember(voiceEnabled,context,voiceSession) {
        if(voiceSession==null && voiceEnabled) (context as? pe.studyplanner.aipet.StudyPlannerApplication)?.petLink?.let {pe.studyplanner.aipet.domain.companion.SpeechFaceSync(it,voiceScope)} else null
    }
    val voice=remember(voiceEnabled,context,voiceSession) {voiceSession ?: if(voiceEnabled) VoiceSession(AndroidVoicePlatform(context),voiceScope,faceSync) else null}
    DisposableEffect(voice) {onDispose {voice?.close()}}
    DisposableEffect(faceSync) {onDispose {faceSync?.release()}}
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    val voiceUi=if(voice!=null) voice.state.collectAsStateWithLifecycle().value else null
    val voiceListening=voiceUi?.phase in setOf(pe.studyplanner.aipet.domain.voice.VoicePhase.REQUESTING,pe.studyplanner.aipet.domain.voice.VoicePhase.LISTENING,pe.studyplanner.aipet.domain.voice.VoicePhase.TRANSCRIBING)
    val voiceReading=voiceUi?.phase in setOf(pe.studyplanner.aipet.domain.voice.VoicePhase.PREPARING_SPEECH,pe.studyplanner.aipet.domain.voice.VoicePhase.SPEAKING)
    DisposableEffect(voice,owner) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event ->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event==androidx.lifecycle.Lifecycle.Event.ON_STOP) voice?.suspendSession()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(profile.petVisible) {if(!profile.petVisible)voice?.suspendSession()}

    var text by rememberSaveable {mutableStateOf("")}
    var help by rememberSaveable {mutableStateOf(false)}
    var deleteReview by remember {mutableStateOf(false)}
    var eraseHistory by remember {mutableStateOf(false)}
    var leaveReview by remember {mutableStateOf(false)}
    var details by rememberSaveable {mutableStateOf(false)}
    var showVoice by rememberSaveable {mutableStateOf(true)}
    var chatSettings by rememberSaveable { mutableStateOf(false) }
    var autoReply by remember { mutableStateOf(aiStore?.state?.value?.autoSpeak ?: false) }
    LaunchedEffect(voice,autoReply){voice?.setReadAloudEnabled(autoReply)}
    var dictationRequest by remember { mutableIntStateOf(0) }
    val listState=rememberLazyListState()
    val uiScope=rememberCoroutineScope()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val reply = "${profile.petName}. " + (state.proposal?.preview ?: state.messages.lastOrNull { it.role=="assistant" }?.text ?: "Revisa tu agenda o escribe una solicitud.")
    var previousReply by remember { mutableStateOf(reply) }
    LaunchedEffect(reply) {
        val changed = reply != previousReply
        previousReply = reply
        if (changed && autoReply && profile.petVisible && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            val phase = voice?.state?.value?.phase
            if (phase !in setOf(pe.studyplanner.aipet.domain.voice.VoicePhase.REQUESTING, pe.studyplanner.aipet.domain.voice.VoicePhase.LISTENING, pe.studyplanner.aipet.domain.voice.VoicePhase.TRANSCRIBING)) voice?.speak(reply.take(3800))
        }
    }
    val exit:()->Unit={if(!state.busy){if(state.proposal!=null)leaveReview=true else onBack()}}
    BackHandler(state.busy || state.proposal!=null){exit()}
    val canSend=!state.busy && state.ready && state.proposal==null
    LaunchedEffect(state.messages.size,state.proposal?.id,state.error) {
        if(state.messages.isNotEmpty() || state.proposal!=null || state.error.isNotBlank()) {
            val firstMessage=if(voice!=null)3 else 2
            val target=when {
                state.error.isNotBlank()->firstMessage+state.messages.size+(if(state.proposal!=null)1 else 0)
                state.proposal!=null->firstMessage+state.messages.size
                else->firstMessage+state.messages.size-1
            }
            listState.animateScrollToItem(target.coerceAtLeast(0))
        }
    }
    Scaffold(topBar={AdaptiveAgentTopBar(busy=state.busy,onBack=exit)},bottomBar={
        Column(Modifier.imePadding()) {
            Surface(color=MaterialTheme.colorScheme.surface,tonalElevation=2.dp) {
                Column {
                if(voiceListening || voiceReading) Text(if(voiceListening) "Escuchando · toca el micrófono para cancelar" else "Leyendo · toca el micrófono para detener",Modifier.padding(horizontal=20.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(voice!=null) FilledTonalIconButton(onClick={
                        if(voiceListening)voice.cancel() else if(voiceReading)voice.stopSpeaking() else {
                            showVoice=true;dictationRequest++
                            uiScope.launch {listState.animateScrollToItem(1)}
                        }
                    },enabled=(canSend || voiceListening || voiceReading) && profile.petVisible,modifier=Modifier.size(48.dp),
                        colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=if(voiceListening)MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)) {
                        Icon(painterResource(pe.studyplanner.aipet.R.drawable.ic_mic),if(voiceListening)"Cancelar dictado" else if(voiceReading)"Detener audio" else "Dictar una petición")
                    }
                    OutlinedTextField(value=text,onValueChange={if(it.length<=4000)text=it},placeholder={Text("Escribe un mensaje…")},
                        enabled=canSend,minLines=1,maxLines=3,shape=MaterialTheme.shapes.large,
                        modifier=Modifier.weight(1f).testTag("agent_input"))
                    FilledIconButton(onClick={voice?.suspendSession();val request=text;text="";model.send(request)},
                        enabled=canSend && text.isNotBlank(),modifier=Modifier.size(48.dp).testTag("agent_send")) {
                        Icon(Icons.Default.Send,"Revisar solicitud")
                    }
                }
                }
            }
            if(onNavigate!=null && !state.busy && state.proposal==null) pe.studyplanner.aipet.ui.components.SectionNavigation(pe.studyplanner.aipet.ui.components.AppSection.CHAT,onNavigate)
            else Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }) { padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("agent_content"),state=listState,
            contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item {
                Text(if(aiConnection?.enabled==true && state.scope==DatasetScope.PERSONAL) "IA en la nube activada · las órdenes conocidas se resuelven en el teléfono" else "Comandos locales · funcionan sin conectar IA", style=MaterialTheme.typography.bodySmall)
                if(onAiSettings!=null && aiConnection?.builtIn!=true)TextButton(onClick=onAiSettings,enabled=canSend){Text(if(aiConnection?.enabled==true)"Conexión IA" else "Conectar IA")}
                if(chatSettings) {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    FilterChip(selected=state.scope==DatasetScope.PERSONAL,onClick={if(state.scope!=DatasetScope.PERSONAL){voice?.discardTranscript();showVoice=false;text="";model.changeScope(DatasetScope.PERSONAL)}},
                        enabled=!state.busy && state.proposal==null,label={Text("Mi espacio")})
                    FilterChip(selected=state.scope==DatasetScope.DEMO,onClick={if(state.scope!=DatasetScope.DEMO){voice?.discardTranscript();showVoice=false;text="";model.changeScope(DatasetScope.DEMO)}},
                        enabled=!state.busy && state.proposal==null,label={Text("Demostración")})
                }
                }
                if(state.scope==DatasetScope.DEMO) Text("DEMO · Datos ficticios; avisos reales.")
            }
            if(voice!=null) item {
                if(!showVoice) TextButton(onClick={showVoice=!showVoice;if(!showVoice)voice.suspendSession()},modifier=Modifier.fillMaxWidth()) {
                    Text(if(showVoice) "Cerrar controles de voz" else "Abrir controles de voz")
                }
                if(showVoice) VoicePanel(voice,profile,onTogglePet,
                    onUseTranscript={value->text="";model.send(value)},
                    readText=reply, autoReply=autoReply, onAutoReply={autoReply=it;aiStore?.setAutoSpeak(it)},
                    compact=true, requestDictation=dictationRequest, onRequestHandled={dictationRequest=0},
                    operationMood=when {state.error.isNotBlank()->"alert";state.busy->"thinking";state.proposal!=null->"think";state.receipt?.status=="COMMITTED"->"celebrate";state.receipt!=null->"alert";else->"idle"},
                    enabled=state.ready && !state.busy && state.proposal==null,
                    operationState=when {
                        state.busy && state.proposal!=null->"Guardando el cambio confirmado"
                        state.busy->"Revisando la solicitud"
                        state.error.isNotBlank()->"Revisa el mensaje de error"
                        state.proposal!=null->"Propuesta pendiente de confirmación"
                        state.receipt!=null->"Comprobante disponible · revisa su resultado"
                        else->"En reposo"
                    })
            }
            item {
                if(state.messages.isEmpty() && state.proposal==null) {
                    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("¿Qué organizamos hoy?",style=MaterialTheme.typography.titleMedium)
                            Text("Consulta tus pendientes, crea una tarea o prepara tu semana. Revisaremos los cambios antes de guardarlos.",style=MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        SuggestionChip(onClick={text="mis pendientes"},label={Text("Mis tareas")})
                        SuggestionChip(onClick={text="planifica mi semana"},label={Text("Mi semana")})
                    }
                }
                TextButton(onClick={help=!help}){Text(if(help) "Ocultar ejemplos" else "Ver ejemplos de peticiones")}
                if(help) {
                    Text("Puedes crear tareas y avisos sencillos sin comillas; para editar usa el título exacto entre comillas. Usa horas de 24 horas (18:00). Si falta un dato o hay varias coincidencias, se pide aclaración. Borrado masivo, deshacer e integración universitaria no están disponibles en este modo. El dictado, cuando está disponible, solo prepara texto revisable.")
                    listOf("mis pendientes","crea tarea \"Repasar álgebra\" para mañana",
                        "crea examen \"Redes\" para mañana","recuérdame \"Pausa\" en 2 minutos",
                        "planifica mi semana").forEach { example->
                        TextButton(onClick={text=example},enabled=!state.busy && state.proposal==null,modifier=Modifier.fillMaxWidth()){Text(example)}
                    }
                    Text("Para renombrar, completar o eliminar una tarea, indica su título exacto entre comillas. El plan necesita disponibilidad guardada y conserva los esfuerzos desconocidos sin inventarlos. Usa los formularios para operaciones fuera de la gramática.")
                }
            }
            items(state.messages,key={it.id}) {message->
                Row(Modifier.fillMaxWidth(), horizontalArrangement=if(message.role=="user") Arrangement.End else Arrangement.Start) {
                Card(Modifier.fillMaxWidth(0.92f),colors=CardDefaults.cardColors(containerColor=if(message.role=="user")MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {Column(Modifier.padding(16.dp)) {
                    Text(if(message.role=="user") "Tú" else profile.petName,style=MaterialTheme.typography.labelLarge)
                    Text(if(message.role=="assistant") pe.studyplanner.aipet.ui.talk.TalkReplies.friendly(message.text) else message.text,Modifier.testTag("agent_message_${message.id}"))
                }}}
            }
            state.proposal?.let { p->item {
                Card(Modifier.fillMaxWidth().testTag("agent_review")) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Revisa antes de confirmar",style=MaterialTheme.typography.titleLarge)
                    Text(p.preview)
                    Text("Esta propuesta no modifica la agenda hasta confirmar. Caduca a los 10 minutos; si cambian los datos, habrá que revisarla de nuevo.")
                    Button(onClick={if(p.action is AgentAction.DeleteTask)deleteReview=true else model.confirm()},enabled=!state.busy,
                        modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text(if(p.action is AgentAction.DeleteTask) "Revisar eliminación" else "Confirmar y ejecutar")}
                    TextButton(onClick={text=state.messages.lastOrNull{it.role=="user"}?.text.orEmpty();model.discard()},enabled=!state.busy) {Text("Editar petición")}
                    OutlinedButton(onClick=model::discard,enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Descartar propuesta")}
                }}
            }}
            item {
                if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(state.error.isNotBlank()) Text(state.error,Modifier.semantics{liveRegion=LiveRegionMode.Polite}.testTag("agent_error"))
            }
            state.receipt?.let { r->item {
                Text(if(r.status in setOf("PARTIAL_SUCCESS","SAVED_PENDING","SAVED_BLOCKED")) "Cambio guardado; revisa el aviso pendiente." else "Operación registrada.")
                TextButton(onClick={details=!details}){Text(if(details) "Ocultar comprobante" else "Ver comprobante")}
                if(details) Text("${r.scope.name} · ${r.tool}\nSolicitud: ${r.requestId}\nRegistros: ${r.entityIds.joinToString()}\n${r.schedulerState.orEmpty()}${if(r.replay) " · Comprobante recuperado; operación no repetida." else ""}")
            }}
            if(state.proposal!=null) item {Text("Confirma, edita o descarta la propuesta para continuar.",style=MaterialTheme.typography.bodySmall)}
            item {
                TextButton(onClick={chatSettings=!chatSettings}) {Text(if(chatSettings) "Cerrar opciones" else "Opciones e historial")}
                if(chatSettings) {
                Text("Conservación de conversaciones",style=MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Guardar historial en este dispositivo",Modifier.weight(1f))
                    Switch(checked=state.historyEnabled,onCheckedChange=model::historyEnabled,enabled=!state.busy && state.ready,modifier=Modifier.semantics{contentDescription="Guardar historial en este dispositivo"})
                }
                Text(if(!state.ready) "No se ha podido comprobar la conservación del historial. Las solicitudes están bloqueadas hasta recuperar la lectura." else if(state.historyEnabled) "Se guardan hasta 100 mensajes de este espacio. Desactivar detiene los nuevos guardados; puedes borrar los anteriores." else "Solo sesión: los mensajes nuevos no se conservan al cerrar el chat. Los comprobantes de operaciones se guardan para evitar duplicados.")
                if(!state.ready && !state.busy) OutlinedButton(onClick=model::retryLoad,modifier=Modifier.fillMaxWidth()){Text("Reintentar lectura del chat")}
                OutlinedButton(onClick={eraseHistory=true},enabled=!state.busy && state.proposal==null,modifier=Modifier.fillMaxWidth()){Text("Borrar conversación de este espacio")}
                OutlinedButton(onClick={onOpenAgenda(state.scope)},enabled=!state.busy && state.proposal==null,modifier=Modifier.fillMaxWidth()){Text("Abrir agenda y formularios")}
                OutlinedButton(onClick={onOpenPlanner(state.scope)},enabled=!state.busy && state.proposal==null,modifier=Modifier.fillMaxWidth()){Text("Abrir planificador")}
                OutlinedButton(onClick={onOpenAlerts(state.scope)},enabled=!state.busy && state.proposal==null,modifier=Modifier.fillMaxWidth()){Text("Revisar alarmas y recordatorios")}
                }
            }
        }
    }
    if(deleteReview) AlertDialog(onDismissRequest={deleteReview=false},title={Text("Eliminar esta tarea")},
        text={Text("Se eliminará únicamente la tarea de la propuesta y se cancelarán sus avisos vinculados. No hay deshacer para esta operación. Revisa el título y el espacio antes de continuar.")},
        confirmButton={TextButton(onClick={deleteReview=false;model.confirm()}){Text("Eliminar tarea confirmada")}},
        dismissButton={TextButton(onClick={deleteReview=false}){Text("Seguir revisando")}})
    if(eraseHistory) AlertDialog(onDismissRequest={eraseHistory=false},title={Text("Borrar conversación")},
        text={Text("Se borrarán los mensajes guardados y los de esta sesión en ${state.scope.name}. La agenda y los comprobantes de operaciones se conservan.")},
        confirmButton={TextButton(onClick={eraseHistory=false;model.clearHistory()}){Text("Borrar mensajes")}},
        dismissButton={TextButton(onClick={eraseHistory=false}){Text("Cancelar")}})
    if(leaveReview) AlertDialog(onDismissRequest={leaveReview=false},title={Text("Descartar la propuesta y salir")},
        text={Text("Esta propuesta no se aplicará. Puedes volver a solicitarla después.")},
        confirmButton={TextButton(onClick={leaveReview=false;model.discard();onBack()}){Text("Descartar y salir")}},
        dismissButton={TextButton(onClick={leaveReview=false}){Text("Seguir revisando")}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdaptiveAgentTopBar(busy:Boolean,onBack:()->Unit) {
    Surface(
        modifier=Modifier.fillMaxWidth().testTag("agent_top_bar").semantics{isTraversalGroup=true},
        color=MaterialTheme.colorScheme.surface,
        contentColor=MaterialTheme.colorScheme.onSurface,
    ) {
        // The title determines the height when system text needs more than one line.
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(TopAppBarDefaults.windowInsets)
                .heightIn(min=64.dp).padding(horizontal=4.dp,vertical=8.dp),
            verticalAlignment=Alignment.CenterVertically,
        ) {
            IconButton(onClick=onBack,enabled=!busy,modifier=Modifier.size(48.dp).testTag("agent_back")) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"Volver")}
            Text("Chat con tu compañero",
                modifier=Modifier.weight(1f).padding(start=4.dp,end=16.dp)
                    .semantics{heading()}.testTag("agent_title"),
                style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        }
    }
}
