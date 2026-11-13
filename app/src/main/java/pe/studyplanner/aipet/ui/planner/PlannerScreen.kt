package pe.studyplanner.aipet.ui.planner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.planner.*
import pe.studyplanner.aipet.ui.academic.ChoiceField
import pe.studyplanner.aipet.ui.academic.ToggleField

class PlannerViewModel(private val repository: PlannerRepository, private val scope: DatasetScope) : ViewModel() {
    var context by mutableStateOf<PlanContext?>(null); private set
    var draft by mutableStateOf<PlanDraft?>(null); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    init { reload() }
    private fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; message = ""
        viewModelScope.launch {
            try { action() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = if (e is IllegalArgumentException || e is StaleAcademicWriteException)
                e.message ?: "Revisa los datos antes de continuar." else "No se pudo completar la operación. Tus datos se conservan; vuelve a intentar." }
            finally { busy = false }
        }
    }
    fun reload() = run { context = repository.load(scope) }
    fun save(settings: PlannerSettings) = run {
        repository.saveSettings(scope, settings); message = "Disponibilidad y límites guardados."
        try { context = repository.load(scope) } catch (e: CancellationException) { throw e }
        catch (_: Exception) { message += " No se pudo recargar la vista; vuelve a abrir el planificador." }
    }
    fun propose(request: PlanRequest) = run {
        val baseline = requireNotNull(context) { "Primero carga los datos." }
        val proposed = repository.propose(scope, request)
        if (proposed.revision != baseline.snapshot.revision || proposed.settingsVersion != baseline.settings.version || proposed.profileRevision != baseline.profileRevision || proposed.zoneId != baseline.zoneId)
            throw StaleAcademicWriteException()
        draft = proposed
    }
    fun discard() { if (!busy) { draft = null; message = "Propuesta descartada. La agenda no cambió." } }
    fun apply() { val reviewed = draft ?: return; run {
        val receipt = repository.apply(reviewed)
        draft = null; message = if (receipt.replay) "Esta propuesta ya estaba aplicada; no se duplicó." else "Plan guardado en la agenda. No se crearon avisos adicionales. Revisa los avisos vinculados en Alarmas y recordatorios."
        try { context = repository.load(scope) } catch (e: CancellationException) { throw e }
        catch (_: Exception) { message += " No se pudo recargar la vista; vuelve a la agenda para consultarlo." }
    } }
    fun error(text: String) { message = text }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(repository: PlannerRepository, scope: DatasetScope, onBack: () -> Unit) {
    val model: PlannerViewModel = viewModel(key = "planner-${scope.name}", factory = remember(repository, scope) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = PlannerViewModel(repository, scope) as T
        }
    })
    val context = model.context
    var page by rememberSaveable { mutableStateOf("SETTINGS") }
    var windows by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var daily by rememberSaveable { mutableStateOf("120") }; var minimum by rememberSaveable { mutableStateOf("25") }
    var maximum by rememberSaveable { mutableStateOf("50") }; var pause by rememberSaveable { mutableStateOf("10") }
    var sleepStart by rememberSaveable { mutableStateOf("23:00") }; var sleepEnd by rememberSaveable { mutableStateOf("07:00") }
    var preferredStart by rememberSaveable { mutableStateOf("18:00") }; var preferredEnd by rememberSaveable { mutableStateOf("20:00") }
    var loadedVersion by rememberSaveable { mutableLongStateOf(-1) }
    var start by rememberSaveable { mutableStateOf("") }; var days by rememberSaveable { mutableStateOf("7") }
    var selected by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var amounts by rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }
    var indivisible by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var moving by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(context?.settings?.version) {
        val c = context ?: return@LaunchedEffect
        if (loadedVersion != c.settings.version) {
            val s = c.settings
            windows = ArrayList(s.windows.map { "${it.day.name}|${it.start}|${it.end}" }.ifEmpty { listOf("MONDAY|18:00|20:00") })
            daily = s.dailyMinutes.toString(); minimum = s.minBlock.toString(); maximum = s.maxBlock.toString(); pause = s.breakMinutes.toString()
            sleepStart = s.sleepStart.toString(); sleepEnd = s.sleepEnd.toString(); preferredStart = s.preferredStart.toString(); preferredEnd = s.preferredEnd.toString()
            loadedVersion = s.version
        }
        if (start.isEmpty()) start = LocalDate.now(ZoneId.of(c.zoneId)).toString()
    }
    val enabled = !model.busy
    val draft = model.draft
    val listState = rememberLazyListState()
    LaunchedEffect(page, draft?.id, model.message) { listState.scrollToItem(0) }
    LaunchedEffect(context?.snapshot?.revision) {
        val c = context ?: return@LaunchedEffect
        val goals = c.snapshot.activities.filter { it.kind in listOf(ActivityKind.TASK, ActivityKind.EXAM) && it.status in listOf(ActivityStatus.TODO, ActivityStatus.IN_PROGRESS) }.map { it.id }.toSet()
        val sessions = c.links.mapNotNull { link -> c.snapshot.activities.firstOrNull { it.id == link.sessionId && !it.fixed && it.status == ActivityStatus.TODO && (it.time as? ActivityTime.Timed)?.start?.let { time -> time >= Instant.now() } == true }?.id }.toSet()
        selected = selected.filter { it in goals }; moving = moving.filter { it in sessions }
    }
    fun enteredSettings(version: Long) = PlannerSettings(version, windows.map { val p = it.split('|'); Availability(DayOfWeek.valueOf(p[0]), LocalTime.parse(p[1]), LocalTime.parse(p[2])) },
        daily.toInt(), minimum.toInt(), maximum.toInt(), pause.toInt(), LocalTime.parse(sleepStart), LocalTime.parse(sleepEnd), LocalTime.parse(preferredStart), LocalTime.parse(preferredEnd))
    val unsavedSettings = context?.let { runCatching { enteredSettings(it.settings.version) }.getOrNull() != it.settings } ?: true
    fun validate(action: () -> Unit) { try { action() } catch (_: NumberFormatException) { model.error("Introduce minutos y días como números enteros.") }
        catch (_: DateTimeException) { model.error("Revisa fechas (AAAA-MM-DD) y horas (HH:mm, 24 horas).") }
        catch (e: Exception) { model.error(e.message ?: "Revisa los datos.") } }
    Scaffold(topBar = { TopAppBar(title = { Text("Planificar estudio") }, navigationIcon = {
        TextButton(onClick = onBack, enabled = enabled) { Text("Volver") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().testTag("planner_content"), state = listState, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(if (scope == DatasetScope.DEMO) "DEMO · Solo datos ficticios" else "PERSONAL · Tu agenda", style = MaterialTheme.typography.titleMedium)
                Text("Motor local. Primero revisas la propuesta; solo al confirmar cambia la agenda. No usa IA remota ni crea avisos.") }
            if (model.busy) item { CircularProgressIndicator(); Text("Comprobando y guardando…") }
            if (model.message.isNotEmpty()) item { Text(model.message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (context == null) item { Button(onClick = model::reload, enabled = enabled) { Text("Reintentar lectura") } }
            else if (draft != null) {
                item { Text("Propuesta sin aplicar", style = MaterialTheme.typography.headlineSmall)
                    Text("Zona: ${draft.zoneId}. Del ${draft.request.startDate} al ${draft.request.startDate.plusDays(draft.request.days.toLong() - 1)}.")
                    Text("${draft.changes.count { it.before == null }} altas · ${draft.changes.count { it.before != null && it.after != null }} cambios · ${draft.changes.count { it.after == null }} eliminaciones de sesiones autorizadas.") }
                draft.goals.forEach { goal -> item {
                    Text(goal.title, style = MaterialTheme.typography.titleMedium)
                    Text(if (goal.required == null) "Sin estimación: no planificado." else "Total ${goal.required} min · vinculados ${goal.credited} · propuestos ${goal.assigned} · pendientes ${goal.pending}.")
                    Text(goal.reason)
                } }
                draft.changes.forEach { change -> item {
                    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(when { change.before == null -> "Agregar sesión"; change.after == null -> "Eliminar sesión autorizada"; else -> "Mover sesión autorizada" }, style = MaterialTheme.typography.titleMedium)
                        Text((change.after ?: change.before)!!.title)
                        change.before?.let { Text("Antes: ${sessionTime(it, draft.zoneId)}") }
                        change.after?.let { Text("Después: ${sessionTime(it, draft.zoneId)} · ${it.estimateMinutes} min") }
                    } }
                } }
                item { Text("Los minutos pendientes no se ocultarán ni se compensarán moviendo clases o invadiendo tu descanso.")
                    Button(onClick = { confirm = true }, enabled = enabled && draft.changes.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Aplicar cambios a la agenda") }
                    OutlinedButton(onClick = model::discard, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Descartar y ajustar") }
                    Text("La propuesta caduca en 30 minutos o si cambian datos, perfil o disponibilidad. Al salir del proceso se pierde la propuesta sin aplicar, no la agenda.") }
            } else {
                item {
                    ChoiceField("Paso", page, listOf("SETTINGS" to "Disponibilidad y límites", "GOALS" to "Objetivos y replanificación"), enabled) { page = it }
                    Text("Zona: ${context.zoneId}. Guardar límites no mueve ningún evento.")
                }
                if (page == "SETTINGS") {
                    item { Text("Disponibilidad semanal", style = MaterialTheme.typography.headlineSmall)
                        Text("Cada franja comienza y termina el mismo día. Añade otra para horarios partidos. Las franjas superpuestas se unen; no duplican tiempo.") }
                    windows.forEachIndexed { index, value -> item {
                        val parts = value.split('|')
                        fun update(part: Int, text: String) { val copy = ArrayList(windows); val fields = parts.toMutableList(); fields[part] = text; copy[index] = fields.joinToString("|"); windows = copy }
                        OutlinedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceField("Día de franja ${index + 1}", parts[0], DayOfWeek.entries.map { it.name to dayName(it) }, enabled) { update(0, it) }
                            PlannerField("Inicio franja ${index + 1}", parts[1], enabled) { update(1, it) }
                            PlannerField("Fin franja ${index + 1}", parts[2], enabled) { update(2, it) }
                            TextButton(onClick = { windows = ArrayList(windows.filterIndexed { i, _ -> i != index }) }, enabled = enabled) { Text("Quitar franja ${index + 1}") }
                        } }
                    } }
                    item {
                        OutlinedButton(onClick = { windows = ArrayList(windows + "MONDAY|18:00|20:00") }, enabled = enabled && windows.size < 28) { Text("Añadir franja") }
                        PlannerField("Máximo diario de estudio (min)", daily, enabled) { daily = it }
                        PlannerField("Bloque mínimo (min)", minimum, enabled) { minimum = it }
                        PlannerField("Bloque máximo (min)", maximum, enabled) { maximum = it }
                        PlannerField("Pausa entre sesiones (min)", pause, enabled) { pause = it }
                        PlannerField("Inicio descanso protegido", sleepStart, enabled) { sleepStart = it }
                        PlannerField("Fin descanso protegido", sleepEnd, enabled) { sleepEnd = it }
                        PlannerField("Inicio franja preferida", preferredStart, enabled) { preferredStart = it }
                        PlannerField("Fin franja preferida", preferredEnd, enabled) { preferredEnd = it }
                        Text("Las horas usan HH:mm, formato de 24 horas. El descanso puede cruzar medianoche. La preferencia nunca elimina restricciones.")
                        Button(onClick = { validate {
                            model.save(enteredSettings(context.settings.version))
                        } }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Guardar disponibilidad y límites") }
                    }
                } else {
                    item {
                        PlannerField("Inicio del horizonte (AAAA-MM-DD)", start, enabled) { start = it }
                        PlannerField("Días a planificar (1–31)", days, enabled) { days = it }
                        Text("Esfuerzo total incluye sesiones ya vinculadas válidas. Una tarea con fecha admite estudio hasta terminar ese día. Para un examen sin hora solo se planifica antes de que comience su fecha; no se inventa la hora del examen.")
                    }
                    val goals = context.snapshot.activities.filter { it.kind in listOf(ActivityKind.TASK, ActivityKind.EXAM) && it.status in listOf(ActivityStatus.TODO, ActivityStatus.IN_PROGRESS) }
                    if (goals.isEmpty()) item { Text("No hay tareas o exámenes pendientes. Créalos en la agenda antes de planificar.") }
                    goals.forEach { goal -> item {
                        ToggleField("Planificar ${goal.title}", goal.id in selected, enabled) { checked ->
                            selected = ArrayList(if (checked) selected + goal.id else selected - goal.id)
                            if (!amounts.containsKey(goal.id)) {
                                val estimate = if (context.goalDefaults.containsKey(goal.id)) context.goalDefaults[goal.id]?.totalMinutes else goal.estimateMinutes
                                amounts = HashMap(amounts + (goal.id to estimate?.toString().orEmpty()))
                                if (context.goalDefaults[goal.id]?.splittable == false && goal.id !in indivisible) indivisible = ArrayList(indivisible + goal.id)
                            }
                        }
                        if (goal.id in selected) {
                            PlannerField("Esfuerzo total: ${goal.title}", amounts[goal.id].orEmpty(), enabled) { amounts = HashMap(amounts + (goal.id to it)) }
                            Text("Minutos; vacío = sin estimar y no se crearán sesiones para este objetivo.")
                            ToggleField("Permitir dividir ${goal.title}", goal.id !in indivisible, enabled) { checked -> indivisible = ArrayList(if (checked) indivisible - goal.id else indivisible + goal.id) }
                        }
                    } }
                    item { Text("Replanificación explícita", style = MaterialTheme.typography.titleLarge)
                        Text("Las sesiones existentes se conservan. Marca únicamente las que autorizas mover o retirar; el resto seguirá bloqueando su horario.") }
                    context.links.forEach { link ->
                        val session = context.snapshot.activities.firstOrNull { it.id == link.sessionId }
                        if (session != null && session.kind == ActivityKind.STUDY && !session.fixed && session.status == ActivityStatus.TODO && (session.time as? ActivityTime.Timed)?.start?.let { it >= Instant.now() } == true) item {
                            ToggleField("Autorizar replanificación: ${session.title}, ${sessionTime(session, context.zoneId)}", session.id in moving, enabled) { checked -> moving = ArrayList(if (checked) moving + session.id else moving - session.id) }
                        }
                    }
                    item {
                        Button(onClick = { validate {
                            model.propose(PlanRequest(LocalDate.parse(start), days.toInt(), selected.map { PlanGoal(it, amounts[it]?.takeIf(String::isNotBlank)?.toInt(), it !in indivisible) }, moving.toSet()))
                        } }, enabled = enabled && context.settings.version > 0 && !unsavedSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Generar propuesta local") }
                        if (context.settings.version == 0L) Text("Primero guarda disponibilidad y límites en el paso anterior.")
                        else if (unsavedSettings) Text("Tienes cambios de disponibilidad sin guardar. Vuelve al paso anterior y guárdalos antes de generar.")
                    }
                }
                item { OutlinedButton(onClick = model::reload, enabled = enabled) { Text("Recargar datos actuales") } }
            }
            item { OutlinedButton(onClick = onBack, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Volver a la agenda") } }
        }
    }
    if (confirm && draft != null) AlertDialog(onDismissRequest = { if (enabled) confirm = false }, title = { Text("Confirmar plan revisado") },
        text = { Text("Se aplicarán solo los cambios mostrados en ${scope.name}. Los déficits seguirán pendientes. No se crean recordatorios.") },
        confirmButton = { TextButton(onClick = { confirm = false; model.apply() }, enabled = enabled) { Text("Confirmar aplicación") } },
        dismissButton = { TextButton(onClick = { confirm = false }, enabled = enabled) { Text("Seguir revisando") } })
}

@Composable private fun PlannerField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), singleLine = true, enabled = enabled)
}
private fun dayName(day: DayOfWeek): String = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")[day.value - 1]
private fun sessionTime(activity: AcademicActivity, zone: String): String {
    val t = activity.time as? ActivityTime.Timed ?: return "Horario no disponible"
    val format = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    return "${t.start.atZone(ZoneId.of(zone)).format(format)} – ${t.end?.atZone(ZoneId.of(zone))?.format(format)}"
}
