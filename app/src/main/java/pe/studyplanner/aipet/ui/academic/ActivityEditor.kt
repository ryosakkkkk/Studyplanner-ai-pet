package pe.studyplanner.aipet.ui.academic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.TextStyle
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.calendar.StrictLocalTime

internal fun ActivityKind.spanish(): String = when (this) {
    ActivityKind.TASK -> "Tarea"
    ActivityKind.EXAM -> "Examen"
    ActivityKind.CLASS -> "Clase"
    ActivityKind.SPORT -> "Deporte"
    ActivityKind.PERSONAL -> "Personal"
    ActivityKind.STUDY -> "Sesión de estudio manual"
}

internal fun Priority.spanish(): String = when (this) { Priority.LOW -> "Baja"; Priority.MEDIUM -> "Media"; Priority.HIGH -> "Alta" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityEditor(initial: AcademicActivity, courses: List<Course>, activities: List<AcademicActivity>, profileZone: String,
    mutation: AcademicMutation, onSave: (AcademicActivity) -> Unit, onEdit: () -> Unit, onBack: () -> Unit, scope: DatasetScope,
    readAvailable: Boolean = true, onRetryRead: () -> Unit = {}) {
    val initialTime = initial.time
    val timed = initialTime as? ActivityTime.Timed
    val weekly = (initialTime as? ActivityTime.Weekly)?.rule
    val initialZone = timed?.zoneId ?: weekly?.zoneId ?: profileZone
    val localStart = timed?.start?.atZone(ZoneId.of(initialZone))
    val localEnd = timed?.end?.atZone(ZoneId.of(initialZone))
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var kindName by rememberSaveable(initial.id) { mutableStateOf(initial.kind.name) }
    var courseId by rememberSaveable(initial.id) { mutableStateOf(initial.courseId ?: "") }
    var notes by rememberSaveable(initial.id) { mutableStateOf(initial.notes) }
    var priorityName by rememberSaveable(initial.id) { mutableStateOf(initial.priority.name) }
    var estimate by rememberSaveable(initial.id) { mutableStateOf(initial.estimateMinutes?.toString() ?: "") }
    var difficulty by rememberSaveable(initial.id) { mutableStateOf(initial.difficulty?.toString() ?: "") }
    var topics by rememberSaveable(initial.id) { mutableStateOf(initial.topics) }
    var fixed by rememberSaveable(initial.id) { mutableStateOf(initial.fixed) }
    var temporal by rememberSaveable(initial.id) { mutableStateOf(when (initialTime) {
        ActivityTime.Unscheduled -> if (initial.kind == ActivityKind.TASK) "NONE" else if (initial.kind == ActivityKind.EXAM) "DATE" else "TIMED"
        is ActivityTime.DateOnly -> "DATE"
        is ActivityTime.Timed -> "TIMED"
        is ActivityTime.Weekly -> "WEEKLY"
    }) }
    var date by rememberSaveable(initial.id) { mutableStateOf((initialTime as? ActivityTime.DateOnly)?.date?.toString()
        ?: localStart?.toLocalDate()?.toString() ?: weekly?.startDate?.toString() ?: "") }
    var startTime by rememberSaveable(initial.id) { mutableStateOf(localStart?.toLocalTime()?.toString() ?: weekly?.startTime?.toString() ?: "") }
    var endDate by rememberSaveable(initial.id) { mutableStateOf(localEnd?.toLocalDate()?.toString() ?: "") }
    var endTime by rememberSaveable(initial.id) { mutableStateOf(localEnd?.toLocalTime()?.toString() ?: weekly?.endTime?.toString() ?: "") }
    var zone by rememberSaveable(initial.id) { mutableStateOf(initialZone) }
    var startOffsetName by rememberSaveable(initial.id) { mutableStateOf(localStart?.offset?.id ?: "") }
    var endOffsetName by rememberSaveable(initial.id) { mutableStateOf(localEnd?.offset?.id ?: "") }
    var weekdayMask by rememberSaveable(initial.id) { mutableIntStateOf(weekly?.weekdays?.sumOf { 1 shl (it.value - 1) } ?: 0) }
    var until by rememberSaveable(initial.id) { mutableStateOf(weekly?.until?.toString() ?: "") }
    var interval by rememberSaveable(initial.id) { mutableStateOf(weekly?.intervalWeeks?.toString() ?: "1") }
    var nextDay by rememberSaveable(initial.id) { mutableStateOf(weekly?.endDayOffset == 1) }
    val originalVersion by rememberSaveable(initial.id) { mutableLongStateOf(initial.version) }
    var step by rememberSaveable(initial.id) { mutableIntStateOf(0) }
    var moreDetails by rememberSaveable { mutableStateOf(false) }
    var zoneOpen by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable(initial.id) { mutableStateOf(false) }
    var discard by rememberSaveable(initial.id) { mutableStateOf(false) }
    var duplicate by rememberSaveable(initial.id) { mutableStateOf(false) }
    var confirmNoCourse by rememberSaveable(initial.id) { mutableStateOf(false) }
    var confirmSeries by rememberSaveable(initial.id) { mutableStateOf(false) }
    var touched by rememberSaveable(initial.id) { mutableStateOf(false) }
    val busy = mutation is AcademicMutation.Saving
    val kind = ActivityKind.valueOf(kindName)
    fun offsets(dateValue: String, timeValue: String): List<ZoneOffset> = runCatching {
        StrictLocalTime.zone(zone).rules.getValidOffsets(LocalDate.parse(dateValue).atTime(LocalTime.parse(timeValue)))
    }.getOrDefault(emptyList())
    val startOffsets = if (temporal == "TIMED") offsets(date, startTime) else emptyList()
    val endOffsets = if (temporal == "TIMED" && kind != ActivityKind.TASK) offsets(endDate, endTime) else emptyList()
    fun edit() { touched = true; onEdit() }
    val back = { if (!busy) { if (step == 1) step = 0 else if (touched) discard = true else onBack() } }
    BackHandler { back() }

    val errors = linkedMapOf<String, String>()
    if (title.isBlank()) errors["title"] = "Escribe un título."
    else if (title.trim().length > 160) errors["title"] = "Usa hasta 160 caracteres."
    if (notes.length > 2000) errors["notes"] = "Usa hasta 2000 caracteres."
    if (topics.length > 2000) errors["topics"] = "Usa hasta 2000 caracteres."
    val estimateValue = estimate.toIntOrNull()
    if (estimate.isNotBlank() && (estimateValue == null || estimateValue !in 1..10080)) errors["estimate"] = "Escribe entre 1 y 10080 minutos."
    if (kind == ActivityKind.STUDY && estimateValue == null) errors["estimate"] = "Indica la duración estimada de esta sesión manual."
    val difficultyValue = difficulty.toIntOrNull()
    if (kind == ActivityKind.EXAM && difficulty.isNotBlank() && (difficultyValue == null || difficultyValue !in 1..5)) errors["difficulty"] = "Usa un número entre 1 y 5 o déjalo vacío."
    if (kind == ActivityKind.CLASS && courseId.isBlank()) errors["course"] = "Selecciona un curso para la clase."
    fun parseDate(value: String, key: String): LocalDate? = try {
        require(value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        LocalDate.parse(value)
    } catch (_: Exception) { errors[key] = "Introduce una fecha válida, AAAA-MM-DD."; null }
    fun parseTime(value: String, key: String): LocalTime? = try {
        require(value.matches(Regex("\\d{2}:\\d{2}")))
        LocalTime.parse(value)
    } catch (_: Exception) { errors[key] = "Introduce una hora válida, HH:mm (00:00–23:59)."; null }
    var resultTime: ActivityTime? = null
    if (temporal == "NONE") {
        if (kind != ActivityKind.TASK) errors["time"] = "Esta actividad necesita una fecha."
        else resultTime = ActivityTime.Unscheduled
    } else if (temporal == "DATE") {
        if (kind != ActivityKind.TASK && kind != ActivityKind.EXAM) errors["time"] = "Indica un intervalo con inicio y final."
        parseDate(date, "date")?.let { resultTime = ActivityTime.DateOnly(it) }
    } else {
        val startDateValue = parseDate(date, "date")
        val startTimeValue = parseTime(startTime, "startTime")
        var validZone = true
        try { StrictLocalTime.zone(zone) } catch (_: Exception) { errors["zone"] = "Usa una zona IANA válida, por ejemplo America/Lima."; validZone = false }
        if (temporal == "TIMED") {
            var endDateValue: LocalDate? = null
            var endTimeValue: LocalTime? = null
            if (kind != ActivityKind.TASK) {
                endDateValue = parseDate(endDate, "endDate")
                endTimeValue = parseTime(endTime, "endTime")
            }
            if (startDateValue != null && startTimeValue != null && validZone) {
                try {
                    val start = StrictLocalTime.resolve(startDateValue.atTime(startTimeValue), zone,
                        if (startOffsets.size > 1 && startOffsetName.isNotBlank()) ZoneOffset.of(startOffsetName) else null)
                    val end = if (endDateValue != null && endTimeValue != null) StrictLocalTime.resolve(endDateValue.atTime(endTimeValue), zone,
                        if (endOffsets.size > 1 && endOffsetName.isNotBlank()) ZoneOffset.of(endOffsetName) else null) else null
                    if (end != null && !end.isAfter(start)) errors["endTime"] = "El final debe ser posterior al inicio. Para cruzar medianoche elige la fecha final."
                    else if (kind == ActivityKind.TASK || end != null) resultTime = ActivityTime.Timed(start, end, zone)
                } catch (error: DateTimeException) { errors["time"] = error.message ?: "Revisa la hora y la zona horaria." }
            }
        } else if (temporal == "WEEKLY") {
            val endTimeValue = parseTime(endTime, "endTime")
            val untilValue = if (until.isBlank()) null else parseDate(until, "until")
            val intervalValue = interval.toIntOrNull()
            if (intervalValue == null || intervalValue !in 1..52) errors["interval"] = "Usa un intervalo de 1 a 52 semanas."
            if (weekdayMask == 0) errors["weekdays"] = "Selecciona al menos un día."
            if (startDateValue != null && untilValue != null && untilValue < startDateValue) errors["until"] = "La vigencia final no puede ser anterior al inicio."
            if (kind == ActivityKind.TASK || kind == ActivityKind.EXAM || kind == ActivityKind.STUDY) errors["time"] = "Crea un registro por tarea, examen o sesión de estudio. La repetición semanal corresponde a clases, deporte o actividades personales."
            if (startDateValue != null && startTimeValue != null && endTimeValue != null && validZone && intervalValue != null) {
                if (!nextDay && endTimeValue <= startTimeValue) errors["endTime"] = "El final debe ser posterior al inicio; indica expresamente si termina al día siguiente."
                else resultTime = ActivityTime.Weekly(WeeklyRule(DayOfWeek.entries.filter { weekdayMask and (1 shl (it.value - 1)) != 0 }.toSet(),
                    startTimeValue, endTimeValue, zone, startDateValue, untilValue, intervalValue, if (nextDay) 1 else 0))
            }
        }
    }
    val draft = resultTime?.let { time -> initial.copy(kind = kind, title = title.trim(), courseId = courseId.ifBlank { null }, time = time,
        priority = Priority.valueOf(priorityName), estimateMinutes = estimateValue,
        difficulty = if (kind == ActivityKind.EXAM) difficultyValue else null, topics = if (kind == ActivityKind.EXAM) topics.trim() else "",
        notes = notes.trim(), fixed = if (kind == ActivityKind.EXAM || kind == ActivityKind.CLASS) true else fixed, version = originalVersion) }
    val matches = if (draft == null) emptyList() else activities.filter { it.id != initial.id && it.kind == draft.kind &&
        normalizeAcademicName(it.title) == normalizeAcademicName(draft.title) && it.courseId == draft.courseId && it.time == draft.time }
    fun saveValidated() {
        attempted = true
        if (errors.isNotEmpty() || draft == null) return
        if (matches.isNotEmpty()) duplicate = true
        else if (kind == ActivityKind.EXAM && courseId.isBlank()) confirmNoCourse = true
        else if (initial.time is ActivityTime.Weekly) confirmSeries = true
        else onSave(draft)
    }

    Scaffold(topBar = { TopAppBar(title = { Text(if (initial.version != 0L) "Editar" else when (kind) { ActivityKind.TASK -> "Nueva tarea"; ActivityKind.EXAM -> "Nuevo examen"; else -> "Nueva actividad" }) },
        actions = { if (scope == DatasetScope.DEMO) Text("Demostración", Modifier.padding(end = 12.dp), style = MaterialTheme.typography.labelMedium) },
        navigationIcon = { IconButton(onClick = back, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (scope == DatasetScope.DEMO) DatasetBanner(scope)
            if (!readAvailable) {
                Text("No se puede guardar hasta recuperar la lectura de la agenda. El borrador de ambos pasos se conserva.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetryRead, enabled = !busy) { Text("Reintentar lectura") }
            }
            Text(if (step == 0) "Paso 1 de 2 · Qué" else "Paso 2 de 2 · Cuándo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (initial.time is ActivityTime.Weekly) Text("Estás editando la serie completa. Sus otras fechas siguen el mismo patrón. Si el cambio de horario invalidara excepciones existentes, primero restaura esas ocurrencias desde el calendario; no se borran silenciosamente.")
            if (step == 0) {
                OutlinedTextField(title, { title = it; edit() }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth(), enabled = !busy,
                    isError = attempted && "title" in errors, supportingText = { Text(if (attempted) errors["title"] ?: "Obligatorio." else "Obligatorio.") })
                ChoiceField("Tipo", kindName, ActivityKind.entries.map { it.name to it.spanish() }, !busy) {
                    kindName = it
                    temporal = when (ActivityKind.valueOf(it)) { ActivityKind.TASK -> "NONE"; ActivityKind.EXAM -> "DATE"; else -> "TIMED" }
                    edit()
                }
                // Curso, prioridad, esfuerzo y notas son opcionales: se despliegan al pedirlos (o si tienen un error).
                val detailErrors = attempted && errors.keys.any { it in setOf("course", "notes", "topics", "estimate", "difficulty") }
                TextButton(onClick = { moreDetails = !moreDetails }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (moreDetails || detailErrors) "Ocultar detalles" else "Más detalles (curso, prioridad, notas)")
                }
                if (moreDetails || detailErrors || kind == ActivityKind.CLASS) {
                ChoiceField("Curso", courseId, listOf("" to "Sin curso") + courses.filter { !it.archived || it.id == courseId }.map { it.id to "${it.name}${if (it.archived) " (archivado)" else ""}" }, !busy) { courseId = it; edit() }
                if (attempted && "course" in errors) Text(errors.getValue("course"), color = MaterialTheme.colorScheme.error)
                if (kind == ActivityKind.TASK) {
                    ChoiceField("Prioridad", priorityName, Priority.entries.map { it.name to it.spanish() }, !busy) { priorityName = it; edit() }
                }
                if (kind == ActivityKind.TASK || kind == ActivityKind.STUDY) {
                    OutlinedTextField(estimate, { estimate = it; edit() }, label = { Text(if (kind == ActivityKind.STUDY) "Duración estimada (minutos)" else "Esfuerzo estimado (minutos, opcional)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy, isError = attempted && "estimate" in errors,
                        supportingText = { Text(if (attempted) errors["estimate"] ?: "Entre 1 y 10080 minutos." else "Entre 1 y 10080 minutos.") })
                }
                if (kind == ActivityKind.EXAM) {
                    OutlinedTextField(difficulty, { difficulty = it; edit() }, label = { Text("Dificultad autoevaluada (1–5, opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        enabled = !busy, isError = attempted && "difficulty" in errors, supportingText = { Text(if (attempted) errors["difficulty"] ?: "Puedes dejarla vacía." else "Puedes dejarla vacía.") })
                    OutlinedTextField(topics, { topics = it; edit() }, label = { Text("Contenidos o temario (opcional)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy,
                        isError = attempted && "topics" in errors, supportingText = { Text(errors["topics"] ?: "Hasta 2000 caracteres.") })
                }
                OutlinedTextField(notes, { notes = it; edit() }, label = { Text("Notas o lugar (opcional)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy,
                    isError = attempted && "notes" in errors, supportingText = { Text(errors["notes"] ?: "Solo texto local, sin usar GPS. Hasta 2000 caracteres.") })
                if (kind == ActivityKind.SPORT || kind == ActivityKind.PERSONAL || kind == ActivityKind.STUDY) ToggleField("Horario fijo", fixed, !busy) { fixed = it; edit() }
                }
                Button(onClick = {
                    attempted = true
                    if (errors.keys.none { it in setOf("title", "course", "notes", "topics", "estimate", "difficulty") }) { step = 1; attempted = false }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Continuar") }
            } else {
                Text("${kind.spanish()} · ${title.ifBlank { "Sin título" }}", style = MaterialTheme.typography.titleMedium)
                val timeOptions = when (kind) {
                    ActivityKind.TASK -> listOf("NONE" to "Sin fecha límite", "DATE" to "Fecha sin hora", "TIMED" to "Fecha y hora límite")
                    ActivityKind.EXAM -> listOf("DATE" to "Fecha sin hora conocida", "TIMED" to "Inicio y final con hora")
                    ActivityKind.STUDY -> listOf("TIMED" to "Sesión manual, una sola vez")
                    else -> listOf("TIMED" to "Una sola vez", "WEEKLY" to "Repetición semanal")
                }
                ChoiceField("Cómo registrar el tiempo", temporal, timeOptions, !busy) { temporal = it; edit() }
                if (temporal == "NONE") Text("Sin fecha: no aparecerá como vencida ni ocupará una franja del calendario.")
                if (temporal != "NONE") DateInput(if (temporal == "WEEKLY") "Inicio de vigencia" else "Fecha", date, if (attempted) errors["date"] else null, !busy) { date = it; startOffsetName = ""; edit() }
                if (temporal == "DATE") Text("Se conservará la fecha sin hora. No se inventa 00:00 ni 23:59, y no se reserva todo el día.")
                if (temporal == "TIMED" || temporal == "WEEKLY") {
                    TimeInput(if (kind == ActivityKind.TASK) "Hora límite" else "Hora de inicio", startTime, if (attempted) errors["startTime"] else null, !busy) { startTime = it; startOffsetName = ""; edit() }
                    if (startOffsets.size > 1) {
                        Text("Esta hora se repite por el cambio de horario. Elige cuál de las dos ocurrencias quieres guardar.")
                        ChoiceField("Desfase de la hora de inicio o límite", startOffsetName,
                            listOf("" to "Elegir ocurrencia") + startOffsets.mapIndexed { index, offset -> offset.id to "UTC${offset.id} · ${if (index == 0) "primera" else "segunda"} ocurrencia" }, !busy) {
                            startOffsetName = it; edit()
                        }
                    }
                    if (kind != ActivityKind.TASK) {
                        if (temporal == "TIMED") DateInput("Fecha final", endDate, if (attempted) errors["endDate"] else null, !busy) { endDate = it; endOffsetName = ""; edit() }
                        TimeInput("Hora final", endTime, if (attempted) errors["endTime"] else null, !busy) { endTime = it; endOffsetName = ""; edit() }
                        if (endOffsets.size > 1) {
                            Text("La hora final ocurre dos veces; elige el desfase de esa fecha.")
                            ChoiceField("Desfase de la hora final", endOffsetName,
                                listOf("" to "Elegir ocurrencia final") + endOffsets.mapIndexed { index, offset -> offset.id to "UTC${offset.id} · ${if (index == 0) "primera" else "segunda"} ocurrencia" }, !busy) {
                                endOffsetName = it; edit()
                            }
                        }
                    }
                    if (zoneOpen || (attempted && "zone" in errors)) OutlinedTextField(zone, { zone = it; startOffsetName = ""; endOffsetName = ""; edit() }, label = { Text("Zona horaria de la actividad") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
                        isError = attempted && "zone" in errors, supportingText = { Text(if (attempted) errors["zone"] ?: "Por ejemplo America/Lima." else "Por ejemplo America/Lima.") })
                    else TextButton(onClick = { zoneOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Zona horaria: $zone") }
                }
                if (temporal == "WEEKLY") {
                    Text("Días de repetición", style = MaterialTheme.typography.titleSmall)
                    DayOfWeek.entries.forEach { day ->
                        val bit = 1 shl (day.value - 1)
                        ToggleField(day.getDisplayName(TextStyle.FULL, spanishLocale), weekdayMask and bit != 0, !busy) { checked ->
                            weekdayMask = if (checked) weekdayMask or bit else weekdayMask and bit.inv(); edit()
                        }
                    }
                    if (attempted && "weekdays" in errors) Text(errors.getValue("weekdays"), color = MaterialTheme.colorScheme.error)
                    ToggleField("Termina al día siguiente", nextDay, !busy) { nextDay = it; edit() }
                    OutlinedTextField(interval, { interval = it; edit() }, label = { Text("Cada cuántas semanas") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        isError = attempted && "interval" in errors, supportingText = { Text(if (attempted) errors["interval"] ?: "De 1 a 52. La primera semana se cuenta desde el inicio de vigencia." else "De 1 a 52. La primera semana se cuenta desde el inicio de vigencia.") })
                    DateInput("Fin de vigencia (opcional)", until, if (attempted) errors["until"] else null, !busy) { until = it; edit() }
                    Text("Dejar el fin vacío conserva el patrón. Solo se calculan las ocurrencias del rango que estás consultando; no se crean eventos infinitos.")
                    Text("Las horas inexistentes o ambiguas por cambios de horario se rechazan. Si una proyección futura detecta una incidencia fuera de la ventana validada al guardar, la mostrará para que la corrijas; no ajusta la hora por su cuenta.")
                }
                if (attempted && errors.isNotEmpty()) {
                    Text(errors.values.distinct().joinToString("\n"), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Text("Si el error corresponde a los detalles, vuelve al paso anterior para corregirlo.")
                }
                FormMessage(mutation)
                Button(onClick = ::saveValidated, enabled = !busy && readAvailable, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(when (kind) { ActivityKind.TASK -> "Guardar tarea"; ActivityKind.EXAM -> "Guardar examen"; else -> "Guardar" })
                }
                OutlinedButton(onClick = { step = 0 }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Atrás") }
            }
            TextButton(onClick = { if (touched) discard = true else onBack() }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancelar") }
        }
    }
    if (discard) Confirmation("¿Descartar borrador?", "Esta actividad y los cambios de ambos pasos no se guardarán.", "Descartar", { discard = false; onBack() }, { discard = false })
    if (duplicate) Confirmation("Posible actividad duplicada", "Ya hay ${matches.size} registro(s) con el mismo título, curso y horario. ¿Guardar otro registro?", "Guardar otro", {
        duplicate = false
        if (kind == ActivityKind.EXAM && courseId.isBlank()) confirmNoCourse = true
        else if (initial.time is ActivityTime.Weekly) confirmSeries = true else draft?.let(onSave)
    }, { duplicate = false })
    if (confirmNoCourse) Confirmation("Examen sin curso", "Se guardará «${title.trim()}» como examen general, sin asociarlo a un curso. La fecha y la hora se conservan tal como las indicaste.", "Guardar examen", { confirmNoCourse = false; draft?.let(onSave) }, { confirmNoCourse = false })
    if (confirmSeries) Confirmation("¿Guardar cambios de la serie?", "La edición de «${title.trim()}» afectará todas las ocurrencias de su patrón, no solo la fecha que abriste. No elimina actividades puntuales de reemplazo. Las excepciones existentes que dejaran de corresponder bloquean el cambio hasta que las restaures expresamente.", "Guardar serie", { confirmSeries = false; draft?.let(onSave) }, { confirmSeries = false })
}
