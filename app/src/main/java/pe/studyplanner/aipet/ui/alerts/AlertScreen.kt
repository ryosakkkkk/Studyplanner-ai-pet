package pe.studyplanner.aipet.ui.alerts

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import pe.studyplanner.aipet.data.academic.*
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.alerts.*
import pe.studyplanner.aipet.ui.academic.*
import pe.studyplanner.aipet.platform.alerts.AndroidAlertPlatform

class AlertViewModel(private val service:AlertService,val scope:DatasetScope):ViewModel() {
    var rows by mutableStateOf<List<AlertEntity>>(emptyList());private set
    var snapshot by mutableStateOf<AcademicSnapshot?>(null);private set
    var busy by mutableStateOf(false);private set
    var message by mutableStateOf("");private set
    var savedId by mutableStateOf<String?>(null);private set
    var history by mutableStateOf<List<AlertDeliveryEntity>>(emptyList());private set
    init {
        viewModelScope.launch { try { service.database.alertDao().observe(scope.name).collect { rows=it } } catch(_:Exception) { message="No pudimos leer los avisos. Reabre esta pantalla para reintentar." } }
        viewModelScope.launch { try { RoomAcademicRepository(service.database).observe(scope).collect { snapshot=it } } catch(_:Exception) { message="No pudimos leer las actividades vinculadas." } }
        refresh()
    }
    private fun run(action:suspend ()->Unit) {
        if(busy) return
        busy=true;message=""
        viewModelScope.launch {
            try { action() } catch(e:CancellationException) { throw e }
            catch(e:Exception) { message=e.message ?: "No se pudo completar. Los datos se conservan; vuelve a intentar." }
            finally { busy=false }
        }
    }
    fun refresh()=run { service.reconcile(true);history=service.database.alertDao().deliveries(scope.name) }
    fun save(d:AlertDefinition,generation:Long,revision:Long)=run {
        val row=service.save(scope,d,generation,revision);savedId=d.id
        message="Aviso guardado. ${stateLabel(row.state)}. ${row.reason}"
    }
    fun enable(row:AlertEntity)=run { service.setEnabled(scope,row.id,row.generation,!row.enabled);message=if(row.enabled) "Aviso desactivado; la actividad se conserva." else "Aviso reactivado; revisa su estado de programación." }
    fun snooze(row:AlertEntity)=run {
        val token=requireNotNull(row.deliveryToken) { "Todavía no hay una publicación que posponer." }
        message=if(service.action(scope.name,row.id,token,"SNOOZE")) "Posposición guardada; revisa su estado en la lista." else "Esta acción ya se usó o pertenece a un aviso antiguo."
        history=service.database.alertDao().deliveries(scope.name)
    }
    fun error(text:String) { message=text }
    fun edit() { savedId=null;message="" }
}

fun stateLabel(value:String)=when(AlertState.valueOf(value)) {
    AlertState.PENDING -> "Programación pendiente"
    AlertState.EXACT -> "Programado exacto"
    AlertState.FLEXIBLE -> "Programado flexible"
    AlertState.NEEDS_NOTIFICATIONS -> "Notificaciones bloqueadas"
    AlertState.NEEDS_EXACT -> "Falta acceso exacto"
    AlertState.FAILED -> "Programación fallida"
    AlertState.DISABLED -> "Desactivado"
    AlertState.MISSED -> "Horario vencido / sin próxima ocurrencia"
    AlertState.POSTED -> "Publicado por Android"
    AlertState.UNCERTAIN -> "Publicación no confirmada"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertScreen(service:AlertService,scope:DatasetScope,zoneId:String,onBack:()->Unit) {
    val model:AlertViewModel=viewModel(key="alerts-${scope.name}",factory=remember(service,scope) { object:ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=AlertViewModel(service,scope) as T
    } })
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,model) {
        val observer=LifecycleEventObserver { _,event->if(event==Lifecycle.Event.ON_RESUME) model.refresh() }
        lifecycle.addObserver(observer);onDispose { lifecycle.removeObserver(observer) }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted->
        model.error(if(granted) "Permiso concedido; actualizando programación…" else "No se concedió permiso. La agenda sigue disponible; puedes revisar ajustes.")
        model.refresh()
    }
    var tab by rememberSaveable { mutableStateOf(AlertKind.ALARM.name) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var newId by rememberSaveable { mutableStateOf("") }
    val listState=rememberLazyListState()
    LaunchedEffect(model.savedId) { if(model.savedId!=null) { editing=null;listState.scrollToItem(0) } }
    if(editing!=null && model.snapshot!=null) {
        val row=remember(editing) { model.rows.firstOrNull { it.id==editing } }
        key(editing) { AlertEditor(service,scope,zoneId,model.snapshot!!,row,
            if(row==null) newId else row.id,AlertKind.valueOf(tab),model.busy,model.message,
            onSave=model::save,onError=model::error,onBack={editing=null}) }
        return
    }
    Scaffold(topBar={ TopAppBar(title={Text("Alarmas y recordatorios")},navigationIcon={TextButton(onClick=onBack,enabled=!model.busy){Text("Volver")}}) }) { padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("alerts_content"),state=listState,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {
                Text(if(scope==DatasetScope.DEMO) "DEMOSTRACIÓN · datos ficticios y avisos reales identificados DEMO." else "Tus avisos personales",style=MaterialTheme.typography.titleMedium)
                Text("Funcionan sin mostrar la mascota y sin Internet. No molestar, canales, ahorro, teléfono apagado y Forzar detención pueden impedir la entrega. Una alarma es un aviso del sistema; no garantiza un despertador continuo.")
                ChoiceField("Ver avisos",tab,listOf("ALARM" to "Alarmas","REMINDER" to "Recordatorios"),!model.busy){tab=it}
                Button(enabled=!model.busy && model.snapshot!=null,onClick={model.edit();newId=java.util.UUID.randomUUID().toString();editing=newId},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(if(tab=="ALARM") "Crear alarma" else "Crear recordatorio")}
            }
            item {
                Text("Permisos y canales",style=MaterialTheme.typography.titleMedium)
                val platform=service.platform as? AndroidAlertPlatform
                Text("Notificaciones de la app: ${if(platform?.notificationsEnabled()==true) "permitidas" else "revisar ajustes"}. Acceso exacto: ${if(platform?.exactAllowed()==true) "disponible" else "no disponible"}. Cada canal puede estar bloqueado por separado.")
                if(Build.VERSION.SDK_INT>=33) OutlinedButton(onClick={permission.launch(Manifest.permission.POST_NOTIFICATIONS)},enabled=!model.busy,modifier=Modifier.fillMaxWidth()){Text("Activar notificaciones")}
                OutlinedButton(onClick={runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)) }.onFailure { model.error("No se pudieron abrir los ajustes. Busca StudyPlanner en Ajustes del sistema.") }},modifier=Modifier.fillMaxWidth()){Text("Abrir ajustes de notificaciones")}
                if(Build.VERSION.SDK_INT>=31) OutlinedButton(onClick={runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}"))) }.onFailure { model.error("No se pudo abrir el acceso exacto. Puedes editar el aviso y elegir flexible.") }},modifier=Modifier.fillMaxWidth()){Text("Revisar acceso a alarmas exactas")}
                OutlinedButton(onClick=model::refresh,enabled=!model.busy,modifier=Modifier.fillMaxWidth()){Text("Actualizar permisos y programación")}
            }
            item {
                if(model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(model.message.isNotBlank()) Text(model.message,Modifier.semantics{liveRegion=LiveRegionMode.Polite}.testTag("alerts_message"))
            }
            val rows=model.rows.filter { runCatching { AlertCodec.decode(it.definition).kind.name==tab }.getOrDefault(true) }
            if(rows.isEmpty()) item {Text("Aún no hay avisos en este espacio. Crea uno y revisa la hora antes de confirmar.")}
            items(rows,key={it.id}) { row->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    val definition=runCatching { AlertCodec.decode(row.definition) }.getOrNull()
                    Text(definition?.title ?: "Aviso no legible",style=MaterialTheme.typography.titleMedium)
                    Text(stateLabel(row.state),style=MaterialTheme.typography.labelLarge)
                    row.nextAt?.let { Text("Próximo: "+formatAt(Instant.ofEpochMilli(it),definition?.let(AlertService::zone) ?: zoneId)) }
                    Text(row.reason)
                    row.sourceId?.let { source->Text("Vinculado: "+(model.snapshot?.activities?.firstOrNull { it.id==source }?.title ?: "actividad retirada")) }
                    OutlinedButton(onClick={model.edit();editing=row.id;newId=row.id},enabled=!model.busy && definition!=null,modifier=Modifier.fillMaxWidth()){Text("Editar aviso")}
                    OutlinedButton(onClick={model.enable(row)},enabled=!model.busy,modifier=Modifier.fillMaxWidth()){Text(if(row.enabled) "Desactivar aviso" else "Reactivar aviso")}
                    if(row.deliveryToken!=null) OutlinedButton(onClick={model.snooze(row)},enabled=!model.busy,modifier=Modifier.fillMaxWidth()){Text("Posponer publicación actual")}
                } }
            }
            item {
                Text("Registro local de publicaciones",style=MaterialTheme.typography.titleMedium)
                Text("Publicado significa que Android aceptó la notificación. La recepción visual o auditiva necesita una comprobación aparte.")
                model.history.take(10).forEach { h->Text("${formatAt(Instant.ofEpochMilli(h.receiverAt),zoneId)} · "+when(h.result){"POSTED"->"Publicado por Android";"CLAIMED"->"Publicación iniciada";"UNCERTAIN"->"Publicación no confirmada";"MISSED"->"Vencido sin publicar";else->"Cancelado"}+" · acción: "+when(h.action){"SNOOZE"->"posponer";"COMPLETE"->"completar";else->"ninguna"}) }
            }
        }
    }
}

private fun formatAt(at:Instant,zone:String)=at.atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss VV"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertEditor(service:AlertService,scope:DatasetScope,profileZone:String,snapshot:AcademicSnapshot,row:AlertEntity?,id:String,kind:AlertKind,
    busy:Boolean,message:String,onSave:(AlertDefinition,Long,Long)->Unit,onError:(String)->Unit,onBack:()->Unit) {
    val old=remember { row?.let { AlertCodec.decode(it.definition) } }
    val now=ZonedDateTime.now(ZoneId.of(profileZone))
    var title by rememberSaveable { mutableStateOf(old?.title ?: "") }
    var mode by rememberSaveable { mutableStateOf(when(old?.schedule){is AlertSchedule.Linked->"LINKED";is AlertSchedule.Relative->"RELATIVE";else->"LOCAL"}) }
    var date by rememberSaveable { mutableStateOf((old?.schedule as? AlertSchedule.Local)?.date?.toString() ?: now.toLocalDate().toString()) }
    var hour by rememberSaveable { mutableStateOf((old?.schedule as? AlertSchedule.Local)?.time?.toString() ?: now.plusMinutes(5).toLocalTime().withSecond(0).withNano(0).toString()) }
    var zone by rememberSaveable { mutableStateOf(old?.let(AlertService::zone) ?: profileZone) }
    var minutes by rememberSaveable { mutableStateOf("2") }
    var source by rememberSaveable { mutableStateOf((old?.schedule as? AlertSchedule.Linked)?.activityId ?: "") }
    var offset by rememberSaveable { mutableStateOf(((old?.schedule as? AlertSchedule.Linked)?.offsetMinutes ?: 0).toString()) }
    var dateHour by rememberSaveable { mutableStateOf((old?.schedule as? AlertSchedule.Linked)?.dateOnlyTime?.toString() ?: "") }
    var repeatDays by rememberSaveable { mutableStateOf<List<Int>>((old?.schedule as? AlertSchedule.Local)?.weekdays?.map { it.value } ?: emptyList()) }
    var until by rememberSaveable { mutableStateOf((old?.schedule as? AlertSchedule.Local)?.until?.toString() ?: "") }
    var precision by rememberSaveable { mutableStateOf(old?.precision?.name ?: if(kind==AlertKind.ALARM) "EXACT" else "FLEXIBLE") }
    var sound by rememberSaveable { mutableStateOf(old?.sound?.name ?: "SOUND") }
    var snooze by rememberSaveable { mutableStateOf((old?.snoozeMinutes ?: 10).toString()) }
    var quiet by rememberSaveable { mutableStateOf(old?.quietEnabled ?: true) }
    var quietStart by rememberSaveable { mutableStateOf(old?.quietStart?.toString() ?: "22:00") }
    var quietEnd by rememberSaveable { mutableStateOf(old?.quietEnd?.toString() ?: "07:00") }
    var candidate by remember { mutableStateOf<AlertDefinition?>(null) }
    var candidateGeneration by remember { mutableLongStateOf(0) }
    var candidateRevision by remember { mutableLongStateOf(0) }
    var preview by remember { mutableStateOf("") }
    val actualKind=old?.kind ?: kind
    val scroll=rememberLazyListState()
    LaunchedEffect(message) { if(message.isNotBlank()) scroll.scrollToItem(0) }
    Scaffold(topBar={TopAppBar(title={Text(if(row==null) "Crear aviso" else "Editar aviso")},navigationIcon={TextButton(onClick=onBack,enabled=!busy){Text("Cancelar")}})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().testTag("alert_editor"),state=scroll,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item { Text("${scope.name} · ${if(actualKind==AlertKind.ALARM) "Alarma" else "Recordatorio"}");if(message.isNotBlank()) Text(message,Modifier.semantics{liveRegion=LiveRegionMode.Polite});if(busy) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { OutlinedTextField(title,{title=it},label={Text("Título del aviso")},enabled=!busy,modifier=Modifier.fillMaxWidth()) }
            item {
                ChoiceField("Horario",mode,buildList { add("LOCAL" to "Fecha y hora");add("RELATIVE" to "En unos minutos (prueba real)");if(actualKind==AlertKind.REMINDER) add("LINKED" to "Vinculado a una actividad") },!busy){mode=it}
                if(mode=="LOCAL") {
                    DateInput("Fecha del aviso",date,null,!busy){date=it};TimeInput("Hora del aviso",hour,null,!busy){hour=it}
                    Text("Repetir los días elegidos (sin selección: una sola vez). La fecha indica el primer día admisible.")
                    DayOfWeek.entries.forEach { day->ToggleField(day.getDisplayName(java.time.format.TextStyle.FULL,java.util.Locale.forLanguageTag("es-PE")),day.value in repeatDays,!busy){ checked->repeatDays=if(checked) repeatDays+day.value else repeatDays-day.value} }
                    OutlinedTextField(until,{until=it},label={Text("Repetir hasta (AAAA-MM-DD, opcional)")},enabled=!busy,modifier=Modifier.fillMaxWidth())
                }
                if(mode=="RELATIVE") { OutlinedTextField(minutes,{minutes=it},label={Text("Avisar en minutos (1–10080)")},enabled=!busy,modifier=Modifier.fillMaxWidth());Text("Usa reloj monótono en este arranque. Tras reiniciar recupera la hora absoluta; si ya venció, no dispara de golpe.") }
                if(mode=="LINKED") {
                    val activities=snapshot.activities.filter { it.status !in setOf(ActivityStatus.COMPLETED,ActivityStatus.CANCELLED) }
                    ChoiceField("Actividad vinculada",source,listOf("" to "Selecciona una actividad")+activities.map { it.id to it.title },!busy){source=it}
                    OutlinedTextField(offset,{offset=it},label={Text("Minutos antes (0–10080)")},enabled=!busy,modifier=Modifier.fillMaxWidth())
                    if(activities.firstOrNull{it.id==source}?.time is ActivityTime.DateOnly) { Text("Solo hay fecha: elige una hora de referencia y se restará la anticipación indicada. No cambia la hora desconocida del examen o tarea.");TimeInput("Hora de referencia para el recordatorio",dateHour,null,!busy){dateHour=it} }
                    Text("Si cambias, completas o eliminas la actividad, sus avisos se actualizan o cancelan. Una serie usa la próxima ocurrencia válida, incluidas sus excepciones.")
                }
                OutlinedTextField(zone,{zone=it},label={Text("Zona horaria IANA")},enabled=!busy,modifier=Modifier.fillMaxWidth())
            }
            item {
                ChoiceField("Precisión solicitada",precision,listOf("EXACT" to "Hora exacta (requiere acceso)","FLEXIBLE" to "Flexible (puede retrasarse)"),!busy){precision=it}
                ChoiceField("Sonido solicitado",sound,listOf("SOUND" to "Sonido y vibración","VIBRATE" to "Solo vibración","SILENT" to "Silencioso"),!busy){sound=it}
                Text("El canal y No molestar controlan el resultado. Los cambios del sistema se respetan.")
                OutlinedTextField(snooze,{snooze=it},label={Text("Posponer durante (1–120 minutos)")},enabled=!busy,modifier=Modifier.fillMaxWidth())
                if(actualKind==AlertKind.REMINDER) {
                    ToggleField("Silencio nocturno (sin sonido ni vibración)",quiet,!busy){quiet=it}
                    if(quiet) { TimeInput("Inicio del silencio",quietStart,null,!busy){quietStart=it};TimeInput("Fin del silencio",quietEnd,null,!busy){quietEnd=it} }
                }
            }
            item {
                Button(enabled=!busy,onClick={
                    try {
                        val schedule=when(mode) {
                            "LOCAL" -> AlertSchedule.Local(LocalDate.parse(date),LocalTime.parse(hour),zone,repeatDays.map(DayOfWeek::of).toSet(),until.takeIf { it.isNotBlank() }?.let(LocalDate::parse))
                            "RELATIVE" -> { val m=minutes.toIntOrNull();require(m!=null && m in 1..10080){"Indica de 1 a 10080 minutos."};AlertSchedule.Relative(Instant.now().plusSeconds(m*60L),service.platform.elapsed()+m*60_000L,service.platform.boot(),zone) }
                            else -> AlertSchedule.Linked(source,offset.toIntOrNull() ?: error("Indica la anticipación en minutos."),zone,dateHour.takeIf{it.isNotBlank()}?.let(LocalTime::parse))
                        }
                        val d=AlertDefinition(id,actualKind,title,schedule,AlertPrecision.valueOf(precision),AlertSound.valueOf(sound),snooze.toIntOrNull() ?: error("Indica minutos válidos para posponer."),quiet,LocalTime.parse(quietStart),LocalTime.parse(quietEnd))
                        val next=requireNotNull(AlertRules.next(d,snapshot,Instant.now(),service.platform.boot(),service.platform.elapsed())){"No hay una próxima ocurrencia."}
                        require(next.at>Instant.now()){"El aviso debe ser futuro."}
                        candidateGeneration=row?.generation ?: 0;candidateRevision=snapshot.revision
                        val c=service.platform.capability(d,next)
                        preview="${scope.name} · ${d.title}\n${formatAt(next.at,zone)}\n${if(d.precision==AlertPrecision.EXACT) "Hora exacta solicitada" else "Flexible: Android puede retrasarlo"}\n"+
                            (if(!c.notifications) "Notificaciones bloqueadas: se guardará pendiente de permiso.\n" else "")+
                            (if(d.precision==AlertPrecision.EXACT && !c.exact) "Falta acceso exacto: se guardará sin programar.\n" else "")+
                            (if(next.silentNight) "Silencio nocturno activo.\n" else "")+"Al confirmar se guarda y se intenta programar en Android."
                        candidate=d
                    } catch(e:DateTimeException) { onError(e.message?.takeIf { it.contains("ambigua") || it.contains("existe") } ?: "Revisa fecha, hora (HH:mm) y zona horaria.") }
                    catch(e:Exception) { onError(e.message ?: "Revisa los datos del aviso.") }
                },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Revisar aviso")}
            }
        }
    }
    candidate?.let { d->AlertDialog(onDismissRequest={candidate=null},title={Text("Confirmar aviso real")},text={Text(preview,Modifier.verticalScroll(rememberScrollState()))},confirmButton={TextButton(enabled=!busy,onClick={candidate=null;onSave(d,candidateGeneration,candidateRevision)}){Text("Guardar y programar")}},dismissButton={TextButton(onClick={candidate=null}){Text("Seguir editando")}}) }
}
