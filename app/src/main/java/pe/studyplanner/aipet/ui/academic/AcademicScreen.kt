package pe.studyplanner.aipet.ui.academic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import pe.studyplanner.aipet.ui.theme.PetDesign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.calendar.*
import pe.studyplanner.aipet.domain.profile.UserProfile

enum class AcademicTab(val label: String) {
    COURSES("Cursos"), TASKS("Tareas"), EXAMS("Exámenes"), ACTIVITIES("Todas las actividades"), CALENDAR("Calendario")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicScreen(repository: AcademicRepository, profile: UserProfile, initialTab: AcademicTab = AcademicTab.CALENDAR, onBack: () -> Unit,
    onOpenPlanner: ((DatasetScope) -> Unit)? = null, onOpenAlerts: ((DatasetScope) -> Unit)? = null, initialScope:DatasetScope=DatasetScope.PERSONAL, initialCreateKind: ActivityKind? = null, onNavigate: ((pe.studyplanner.aipet.ui.components.AppSection)->Unit)? = null,
    onProfile: (() -> Unit)? = null, onOpenHabits: (() -> Unit)? = null,
    reviewCard: (@Composable (pe.studyplanner.aipet.domain.academic.AcademicSnapshot) -> Unit)? = null) {
    val model: AcademicViewModel = viewModel(factory = remember(repository) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AcademicViewModel(repository,initialScope) as T
        }
    })
    val load by model.state.collectAsStateWithLifecycle()
    val mutation by model.mutation.collectAsStateWithLifecycle()
    val selectedScope by model.scope.collectAsStateWithLifecycle()
    var tabName by rememberSaveable { mutableStateOf(initialTab.name) }
    var editor by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf("") }
    var newId by rememberSaveable { mutableStateOf("") }
    var newKind by rememberSaveable { mutableStateOf(ActivityKind.TASK.name) }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var confirmationId by rememberSaveable { mutableStateOf("") }
    var confirmationDate by rememberSaveable { mutableStateOf("") }
    var confirmationVersion by rememberSaveable { mutableLongStateOf(0L) }
    val busy = mutation is AcademicMutation.Saving
    val snapshot = (load as? AcademicLoadState.Ready)?.snapshot?.takeIf { it.scope == selectedScope }
    val editorSnapshot = snapshot ?: model.lastKnownSnapshot?.takeIf { it.scope == selectedScope }
    val baseline = model.editingBaseline?.takeIf { it.scope == selectedScope } ?: editorSnapshot
    fun closeEditor() { editor = ""; editingId = ""; newId = ""; model.finishEditing() }
    fun openCourse(course: Course? = null) {
        snapshot?.let(model::beginEditing)
        model.clearMessage(); editor = "COURSE"; editingId = course?.id ?: ""; newId = UUID.randomUUID().toString()
    }
    fun openActivity(activity: AcademicActivity? = null, kind: ActivityKind = ActivityKind.TASK) {
        snapshot?.let(model::beginEditing)
        model.clearMessage(); editor = "ACTIVITY"; editingId = activity?.id ?: ""; newKind = kind.name; newId = UUID.randomUUID().toString()
    }
    fun confirm(action: String, id: String = "", date: String = "") {
        model.clearMessage(); confirmation = action; confirmationId = id; confirmationDate = date
        confirmationVersion = snapshot?.activities?.firstOrNull { it.id == id }?.version
            ?: snapshot?.courses?.firstOrNull { it.id == id }?.version ?: 0L
    }
    var initialCreateOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(snapshot, initialCreateKind) {
        if(snapshot != null && initialCreateKind != null && !initialCreateOpened) {
            initialCreateOpened=true; openActivity(kind=initialCreateKind)
        }
    }
    LaunchedEffect(mutation) { if (mutation is AcademicMutation.Saved) closeEditor() }
    if (editorSnapshot != null && baseline != null && editor == "COURSE") {
        val initial = if (editingId.isEmpty()) Course(id = newId, name = "") else baseline.courses.firstOrNull { it.id == editingId }
        if (initial != null) {
            CourseEditor(initial, editorSnapshot.courses, mutation, model::saveCourse, model::clearMessage, ::closeEditor, editorSnapshot.scope,
                readAvailable = snapshot != null, onRetryRead = model::retry)
            return
        }
    }
    if (editorSnapshot != null && baseline != null && editor == "ACTIVITY") {
        val initial = if (editingId.isEmpty()) AcademicActivity(id = newId, kind = ActivityKind.valueOf(newKind), title = "")
            else baseline.activities.firstOrNull { it.id == editingId }
        if (initial != null) {
            ActivityEditor(initial, editorSnapshot.courses, editorSnapshot.activities, profile.zoneId, mutation, model::saveActivity, model::clearMessage, ::closeEditor, editorSnapshot.scope,
                readAvailable = snapshot != null, onRetryRead = model::retry)
            return
        }
    }
    val agendaFocus by AgendaFocusBus.focus.collectAsStateWithLifecycle()
    LaunchedEffect(agendaFocus?.activityId) { if (agendaFocus != null) tabName = AcademicTab.CALENDAR.name }
    // Al salir de la Agenda la marca de lo nuevo se quita.
    DisposableEffect(Unit) { onDispose { AgendaFocusBus.clear() } }
    val selectedTab = AcademicTab.valueOf(tabName)
    // Raíz de la pestaña Agenda: sin flecha atrás, con el avatar de perfil y accesos con nombre.
    val agendaRoot = onNavigate != null && initialTab == AcademicTab.CALENDAR && initialCreateKind == null
    var menu by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(when{agendaRoot->"Agenda";selectedTab==AcademicTab.TASKS->"Mis tareas";selectedTab==AcademicTab.EXAMS->"Exámenes";selectedTab==AcademicTab.CALENDAR->"Calendario";selectedTab==AcademicTab.COURSES->"Mis cursos";else->"Agenda"},
            style=if(agendaRoot)MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,fontWeight=if(agendaRoot)FontWeight.ExtraBold else FontWeight.Bold) }, actions = {
        if(selectedScope==DatasetScope.DEMO) Text("Demostración", Modifier.padding(end = 4.dp), style = MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.tertiary)
        Box {
            IconButton(onClick={menu=true},modifier=Modifier.testTag("agenda_menu")){Icon(Icons.Default.MoreVert,"Opciones de la agenda")}
            DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                if(onOpenPlanner!=null)DropdownMenuItem(text={Text("Organizar semana")},onClick={menu=false;onOpenPlanner(selectedScope)},enabled=!busy)
                if(onOpenAlerts!=null)DropdownMenuItem(text={Text("Recordatorios y alarmas")},onClick={menu=false;onOpenAlerts(selectedScope)},enabled=!busy)
                DropdownMenuItem(text={Text(if(selectedScope==DatasetScope.PERSONAL)"Abrir datos de demostración" else "Volver a mis datos")},onClick={menu=false;confirm(if(selectedScope==DatasetScope.PERSONAL)"ENTER_DEMO" else "EXIT_DEMO")},enabled=!busy)
            }
        }
        if(agendaRoot && onProfile!=null) pe.studyplanner.aipet.ui.components.ProfileAvatarButton(profile,onProfile)
    }, navigationIcon = {
        if(!agendaRoot) IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },bottomBar={if(onNavigate!=null)pe.studyplanner.aipet.ui.components.SectionNavigation(pe.studyplanner.aipet.ui.components.AppSection.CALENDAR,onNavigate)},floatingActionButton={
        ExtendedFloatingActionButton(onClick={if(!busy && snapshot!=null){if(selectedTab==AcademicTab.COURSES)openCourse() else openActivity(kind=when(selectedTab){AcademicTab.EXAMS->ActivityKind.EXAM;AcademicTab.ACTIVITIES->ActivityKind.PERSONAL;else->ActivityKind.TASK})}},
            containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary,modifier=Modifier.testTag("agenda_add"),
            icon={Icon(Icons.Default.Add,null)},text={Text(when(selectedTab){AcademicTab.COURSES->"Nuevo curso";AcademicTab.EXAMS->"Añadir examen";AcademicTab.ACTIVITIES->"Añadir actividad";else->"Añadir tarea"})})
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().testTag("academic_content"),
            contentPadding = PaddingValues(start=16.dp,end=16.dp,top=4.dp,bottom=96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(AcademicTab.CALENDAR,AcademicTab.TASKS,AcademicTab.EXAMS,AcademicTab.COURSES,AcademicTab.ACTIVITIES).forEach { section ->
                        FilterChip(selected=section==selectedTab,onClick={tabName=section.name;model.clearMessage()},enabled=!busy,modifier=Modifier.heightIn(min=48.dp),
                            label={Text(when(section){AcademicTab.CALENDAR->"Hoy";AcademicTab.ACTIVITIES->"Eventos";else->section.label})})
                    }
                }
            }
            if(mutation != AcademicMutation.Idle) item { FormMessage(mutation) }
            when (load) {
                AcademicLoadState.Loading -> item { CircularProgressIndicator(); Text("Leyendo la agenda local…") }
                AcademicLoadState.Failed -> item {
                    Text("No pudimos leer la agenda", style = MaterialTheme.typography.titleLarge)
                    Text("Los datos se conservan. No crearemos otra base sobre ellos ni confirmaremos cambios sin guardarlos.")
                    Button(onClick = model::retry) { Text("Reintentar lectura") }
                }
                is AcademicLoadState.Ready -> if (snapshot != null) {
                    if (selectedScope == DatasetScope.DEMO) item {
                        Text("Datos ficticios para ensayo. Los registros personales están en otro espacio.")
                        if (snapshot.courses.isEmpty() && snapshot.activities.isEmpty()) {
                            Button(onClick = { confirm("SEED_DEMO") }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Crear datos ficticios") }
                        }
                        OutlinedButton(onClick = { confirm("RESET_DEMO") }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Restablecer solo demostración") }
                    }
                    when (selectedTab) {
                        AcademicTab.COURSES -> courseList(snapshot, busy, ::openCourse, { course -> confirm("ARCHIVE_COURSE", course.id) },
                            { course -> model.saveCourse(course.copy(archived = false)) }, { course -> confirm("DELETE_COURSE", course.id) })
                        AcademicTab.CALENDAR -> {
                            val shownFocus = agendaFocus
                            if (shownFocus != null && selectedScope == DatasetScope.PERSONAL) item {
                                // Lo agendó la mascota: se ve aquí marcado y se puede deshacer con un toque.
                                val saved = snapshot.activities.firstOrNull { it.id == shownFocus.activityId }
                                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.fillMaxWidth().testTag("agenda_focus")) {
                                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${profile.petName} lo agendó: ${shownFocus.title}", Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        if (saved != null) TextButton(onClick = { model.deleteActivity(saved); AgendaFocusBus.clear() }, enabled = !busy,
                                            modifier = Modifier.heightIn(min = 48.dp).testTag("agenda_focus_undo")) { Text("Deshacer") }
                                        TextButton(onClick = { AgendaFocusBus.clear() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Listo") }
                                    }
                                }
                            }
                            if (reviewCard != null && selectedScope == DatasetScope.PERSONAL) item { reviewCard(snapshot) }
                            item {
                                key(snapshot.scope) {
                                    CalendarPanel(snapshot, profile, busy, onEdit = { openActivity(it) },
                                        onCancelOccurrence = { entry -> confirm("CANCEL_OCCURRENCE", entry.ruleId ?: entry.activityId, entry.occurrenceDate.toString()) },
                                        onRestoreOccurrence = { exception -> confirm("RESTORE_OCCURRENCE", exception.activityId, exception.occurrenceDate.toString()) },
                                        onNew = { openActivity(kind = ActivityKind.TASK) }, focus = agendaFocus)
                                }
                            }
                            if (onOpenAlerts != null || onOpenHabits != null || onOpenPlanner != null) item {
                                Text("Organizar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                                Spacer(Modifier.height(8.dp))
                                pe.studyplanner.aipet.ui.RowGroup {
                                    if (onOpenAlerts != null) pe.studyplanner.aipet.ui.NavRow(pe.studyplanner.aipet.R.drawable.ic_bell, PetDesign.Blue, "Recordatorios", "Avisos y alarmas") { onOpenAlerts(selectedScope) }
                                    if (onOpenHabits != null) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        pe.studyplanner.aipet.ui.NavRow(pe.studyplanner.aipet.R.drawable.ic_habit, PetDesign.Violet, "Hábitos", "Marca lo que cuidas cada día") { onOpenHabits() }
                                    }
                                    if (onOpenPlanner != null) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        pe.studyplanner.aipet.ui.NavRow(pe.studyplanner.aipet.R.drawable.ic_agenda, PetDesign.Turquoise, "Organizar semana", "Reparte el estudio en tus días libres") { onOpenPlanner(selectedScope) }
                                    }
                                }
                            }
                        }
                        else -> item {
                            key(snapshot.scope, selectedTab) {
                                ActivityList(snapshot, selectedTab, profile, busy, onNew = { kind -> openActivity(kind = kind) }, onEdit = { openActivity(it) },
                                    onDelete = { confirm("DELETE_ACTIVITY", it.id) }, onComplete = model::setCompleted)
                            }
                        }
                    }

                }
            }
        }
    }
    val course = snapshot?.courses?.firstOrNull { it.id == confirmationId }
    val activity = snapshot?.activities?.firstOrNull { it.id == confirmationId }
    val linked = snapshot?.activities?.count { it.courseId == confirmationId } ?: 0
    when (confirmation) {
        "ENTER_DEMO" -> Confirmation("Entrar en demostración", "Abrirás un espacio de datos ficticios separado de tus cursos y actividades personales. No se mezclan ni se copian automáticamente.", "Entrar en demo", { confirmation = ""; model.changeScope(DatasetScope.DEMO) }, { confirmation = "" })
        "EXIT_DEMO" -> Confirmation("Volver al espacio personal", "Dejarás de ver los datos ficticios. Tus registros de demostración se conservan en su espacio separado.", "Ver mis datos", { confirmation = ""; model.changeScope(DatasetScope.PERSONAL) }, { confirmation = "" })
        "SEED_DEMO" -> Confirmation("Crear datos ficticios", "Se crearán cursos, tareas y horarios de prueba solo en DEMO, tomando como referencia la fecha actual en ${profile.zoneId}. No representan participantes ni resultados científicos.", "Crear demo", { confirmation = ""; model.seedDemo(profile.zoneId) }, { confirmation = "" })
        "RESET_DEMO" -> Confirmation("¿Restablecer demostración?", "Se reemplazarán ${snapshot?.courses?.size ?: 0} curso(s), ${snapshot?.activities?.size ?: 0} actividad(es) y sus excepciones SOLO del espacio DEMO por los datos ficticios iniciales. El espacio PERSONAL y el perfil se conservan.", "Restablecer demo", { confirmation = ""; model.resetDemo(profile.zoneId) }, { confirmation = "" })
        "ARCHIVE_COURSE" -> if (course != null) Confirmation("¿Archivar ${course.name}?", "Tiene $linked actividad(es) vinculada(s). Se conservarán su historial y sus actividades; el curso dejará de aparecer entre las opciones para registros nuevos. Puedes restaurarlo desde esta lista.", "Archivar curso", { confirmation = ""; model.archiveCourse(course.copy(version = confirmationVersion)) }, { confirmation = "" })
        "DELETE_COURSE" -> if (course != null) Confirmation("¿Eliminar ${course.name}?", "Se eliminará este curso del espacio ${selectedScope.name}. Solo se permite cuando no tiene actividades vinculadas. Esta eliminación no se puede deshacer.", "Eliminar curso", { confirmation = ""; model.deleteCourse(course.copy(version = confirmationVersion)) }, { confirmation = "" })
        "DELETE_ACTIVITY" -> if (activity != null) Confirmation("¿Eliminar actividad?", "Se eliminará «${activity.title}» del espacio ${selectedScope.name}${if (activity.time is ActivityTime.Weekly) ", incluyendo su patrón semanal y sus excepciones. Las actividades puntuales de reemplazo, si existen, se conservan como independientes. Para omitir una sola fecha, cancela esa ocurrencia desde el calendario" else ""}. No se puede deshacer.", "Eliminar actividad", { confirmation = ""; model.deleteActivity(activity.copy(version = confirmationVersion)) }, { confirmation = "" })
        "CANCEL_OCCURRENCE" -> Confirmation("¿Cancelar solo esta ocurrencia?", "Se omitirá la ocurrencia del $confirmationDate de «${activity?.title ?: "esta serie"}». Las otras fechas y el patrón semanal se conservan.", "Cancelar ocurrencia", { confirmation = ""; model.cancelOccurrence(confirmationId, LocalDate.parse(confirmationDate), confirmationVersion) }, { confirmation = "" })
        "RESTORE_OCCURRENCE" -> Confirmation("¿Restaurar ocurrencia?", "Volverá a aparecer la ocurrencia original del $confirmationDate según el horario de su serie.", "Restaurar ocurrencia", { confirmation = ""; model.restoreOccurrence(confirmationId, LocalDate.parse(confirmationDate), confirmationVersion) }, { confirmation = "" })
    }
}

@Composable
internal fun DatasetBanner(scope: DatasetScope) {
    Surface(color = if (scope == DatasetScope.DEMO) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()) {
        Text(if (scope == DatasetScope.DEMO) "DEMO · Datos ficticios" else "PERSONAL · Mis datos", Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.courseList(snapshot: AcademicSnapshot, busy: Boolean,
    onEdit: (Course?) -> Unit, onArchive: (Course) -> Unit, onRestore: (Course) -> Unit, onDelete: (Course) -> Unit) {
    item {
        Text("Cursos", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { onEdit(null) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Nuevo curso") }
    }
    if (snapshot.courses.isEmpty()) item { Text("Todavía no hay cursos en este espacio. Crea el primero para asociar tus clases y entregas.") }
    items(snapshot.courses, key = { "course_${it.id}" }) { course ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = course.accentColor(), thickness = 4.dp)
                Text(course.name, style = MaterialTheme.typography.titleLarge)
                if (course.code.isNotEmpty()) Text("Código: ${course.code}")
                Text("${course.colorName()} · ${if (course.archived) "Archivado" else "Activo"} · ${snapshot.activities.count { it.courseId == course.id }} actividad(es)")
                OutlinedButton(onClick = { onEdit(course) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Editar ${course.name}") }
                if (!course.archived) OutlinedButton(onClick = { onArchive(course) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Archivar ${course.name}") }
                else OutlinedButton(onClick = { onRestore(course) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Restaurar ${course.name}") }
                if (snapshot.activities.none { it.courseId == course.id }) {
                    OutlinedButton(onClick = { onDelete(course) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Eliminar ${course.name}") }
                } else Text("Para preservar sus actividades, este curso se puede archivar. No se puede eliminar mientras tenga registros vinculados.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ActivityList(snapshot: AcademicSnapshot, tab: AcademicTab, profile: UserProfile, busy: Boolean,
    onNew: (ActivityKind) -> Unit, onEdit: (AcademicActivity) -> Unit, onDelete: (AcademicActivity) -> Unit, onComplete: (AcademicActivity) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var courseFilter by rememberSaveable { mutableStateOf("") }
    var kindFilter by rememberSaveable { mutableStateOf("") }
    var taskFilter by rememberSaveable { mutableStateOf("PENDING") }
    var order by rememberSaveable { mutableStateOf("DATE") }
    var filters by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now(ZoneId.of(profile.zoneId))
    val now = Instant.now()
    val desiredKind = when (tab) { AcademicTab.TASKS -> ActivityKind.TASK; AcademicTab.EXAMS -> ActivityKind.EXAM; else -> null }
    var filtered = snapshot.activities.filter { activity ->
        val activityDate = activity.localDate(profile.zoneId)
        val pending = activity.status != ActivityStatus.COMPLETED && activity.status != ActivityStatus.CANCELLED
        val overdue = when (val time = activity.time) { is ActivityTime.DateOnly -> time.date < today; is ActivityTime.Timed -> time.start < now; else -> false }
        (desiredKind == null || activity.kind == desiredKind) && (kindFilter.isEmpty() || activity.kind.name == kindFilter) &&
            (courseFilter.isEmpty() || activity.courseId == courseFilter) &&
            (search.isBlank() || activity.title.contains(search.trim(), ignoreCase = true) || activity.notes.contains(search.trim(), ignoreCase = true)) &&
            when (taskFilter) {
                "PENDING" -> pending
                "TODAY" -> pending && activityDate == today
                "UPCOMING" -> pending && activityDate != null && activityDate >= today
                "OVERDUE" -> pending && overdue
                "COMPLETED" -> activity.status == ActivityStatus.COMPLETED
                else -> true
            }
    }
    filtered = when (order) {
        "PRIORITY" -> filtered.sortedWith(compareByDescending<AcademicActivity> { it.priority.ordinal }.thenBy { it.title })
        "COURSE" -> filtered.sortedWith(compareBy<AcademicActivity> { snapshot.courses.firstOrNull { course -> course.id == it.courseId }?.name ?: "" }.thenBy { it.title })
        else -> filtered.sortedWith(compareBy<AcademicActivity> { it.localDate(profile.zoneId) ?: LocalDate.MAX }.thenBy { (it.time as? ActivityTime.Timed)?.start ?: Instant.MAX }.thenBy { it.title })
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if(desiredKind==ActivityKind.EXAM) {
            Text("Prepárate con tiempo",style=MaterialTheme.typography.titleMedium)
            Text("Tus evaluaciones, fechas y contenidos en un solo lugar.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("PENDING" to "Pendientes","COMPLETED" to "Completadas").forEach { (value,label)->
                FilterChip(selected=taskFilter==value,onClick={taskFilter=value},label={Text(label)},modifier=Modifier.weight(1f))
            }
        }
        OutlinedTextField(search,{search=it},placeholder={Text("Buscar ${if(desiredKind==ActivityKind.EXAM) "examen" else "tarea"}")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Text("${filtered.size} ${if(desiredKind==ActivityKind.EXAM) "exámenes" else "actividades"}",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={filters=!filters}){Text(if(filters)"Cerrar filtros" else "Filtrar")}
        }
        if(filters) {
            ChoiceField("Curso de la lista",courseFilter,listOf("" to "Todos los cursos")+snapshot.courses.map{it.id to it.name}){courseFilter=it}
            if(desiredKind==null)ChoiceField("Tipo de la lista",kindFilter,listOf("" to "Todos los tipos")+ActivityKind.entries.map{it.name to it.spanish()}){kindFilter=it}
            ChoiceField("Estado y fecha",taskFilter,listOf("PENDING" to "Pendientes","TODAY" to "Hoy","UPCOMING" to "Próximas","OVERDUE" to "Atrasadas","COMPLETED" to "Completadas","ALL" to "Todas")){taskFilter=it}
            ChoiceField("Ordenar por",order,listOf("DATE" to "Fecha","PRIORITY" to "Prioridad","COURSE" to "Curso")){order=it}
        }
        if(filtered.isEmpty()) {
            Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.35f))) {
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(if(taskFilter=="COMPLETED")"Tus avances aparecerán aquí" else "Todo empieza con una actividad",style=MaterialTheme.typography.titleMedium)
                    Text("No hay registros con los filtros actuales.",style=MaterialTheme.typography.bodyMedium)
                    TextButton(onClick={onNew(desiredKind?:ActivityKind.PERSONAL)},enabled=!busy){Text(if(desiredKind==ActivityKind.EXAM)"＋ Agregar examen" else "＋ Agregar actividad")}
                }
            }
        }
        filtered.forEach { activity ->
            ActivityCard(activity, snapshot, profile.zoneId, busy, onEdit, onDelete, onComplete)
        }
    }
}

@Composable
private fun ActivityCard(activity: AcademicActivity, snapshot: AcademicSnapshot, zoneId: String, busy: Boolean,
    onEdit: (AcademicActivity) -> Unit, onDelete: (AcademicActivity) -> Unit, onComplete: (AcademicActivity) -> Unit) {
    var menu by remember {mutableStateOf(false)}
    var details by remember {mutableStateOf(false)}
    val accent=when(activity.kind){ActivityKind.TASK->PetDesign.Blue;ActivityKind.EXAM->PetDesign.Pink;ActivityKind.STUDY->PetDesign.Violet;ActivityKind.SPORT->PetDesign.Turquoise;else->PetDesign.Orange}
    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,accent.copy(alpha=.20f)),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Top) {
            if(activity.kind==ActivityKind.TASK || activity.kind==ActivityKind.STUDY)Checkbox(checked=activity.status==ActivityStatus.COMPLETED,onCheckedChange={onComplete(activity)},enabled=!busy)
            else Surface(shape=RoundedCornerShape(12.dp),color=accent.copy(alpha=.12f)){Text("▣",Modifier.padding(12.dp),color=accent)}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(activity.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                snapshot.courses.firstOrNull{it.id==activity.courseId}?.let{Text(it.name,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                Text(activity.time.shortDescription(zoneId),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                if(activity.kind==ActivityKind.TASK)Text("Prioridad ${activity.priority.spanish()}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(details) {
                    Text(activity.time.description(zoneId),style=MaterialTheme.typography.bodySmall)
                    Text("Estado: ${activity.status.spanish()}",style=MaterialTheme.typography.bodySmall)
                    activity.estimateMinutes?.let{Text("Estimación: $it minutos",style=MaterialTheme.typography.bodySmall)}
                    activity.difficulty?.let{Text("Dificultad: $it de 5",style=MaterialTheme.typography.bodySmall)}
                    if(activity.topics.isNotBlank())Text(activity.topics,style=MaterialTheme.typography.bodySmall)
                    if(activity.notes.isNotBlank())Text(activity.notes,style=MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick={onEdit(activity)},enabled=!busy,contentPadding=PaddingValues(0.dp)){Text("Editar")}
            }
            Box {
                TextButton(onClick={menu=true},contentPadding=PaddingValues(4.dp)){Text("•••")}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    DropdownMenuItem(text={Text(if(details)"Ocultar detalles" else "Ver detalles")},onClick={details=!details;menu=false})
                    DropdownMenuItem(text={Text("Eliminar")},onClick={menu=false;onDelete(activity)},enabled=!busy)
                }
            }
        }
    }
}

internal fun ActivityStatus.spanish() = when (this) {
    ActivityStatus.TODO -> "Pendiente"; ActivityStatus.IN_PROGRESS -> "En curso"; ActivityStatus.COMPLETED -> "Completada"; ActivityStatus.CANCELLED -> "Cancelada"
}

internal fun Course.colorName(): String = when (colorToken) {
    "INDIGO" -> "Índigo"; "BLUE" -> "Azul"; "GREEN" -> "Verde"; "ORANGE" -> "Naranja"; "PURPLE" -> "Morado"; else -> "Color personalizado"
}

private fun Course.accentColor(): Color = when (colorToken) {
    "BLUE" -> Color(0xFF3888CC); "GREEN" -> Color(0xFF399963); "ORANGE" -> Color(0xFFC77727); "PURPLE" -> Color(0xFF9460B8)
    else -> Color(0xFF7275D5)
}

internal fun AcademicActivity.localDate(zoneId: String): LocalDate? = when (val value = time) {
    ActivityTime.Unscheduled -> null
    is ActivityTime.DateOnly -> value.date
    is ActivityTime.Timed -> value.start.atZone(ZoneId.of(zoneId)).toLocalDate()
    is ActivityTime.Weekly -> value.rule.startDate
}

internal fun ActivityTime.description(profileZone: String): String = when (this) {
    ActivityTime.Unscheduled -> "Sin fecha límite; no ocupa una franja."
    is ActivityTime.DateOnly -> "${date.format(displayDate)} · sin hora; no ocupa todo el día."
    is ActivityTime.Timed -> {
        val format = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", spanishLocale)
        "${start.atZone(ZoneId.of(profileZone)).format(format)}${end?.let { " — ${it.atZone(ZoneId.of(profileZone)).format(format)}" } ?: " · fecha y hora límite"} · $profileZone${if (zoneId != profileZone) " (origen: $zoneId)" else ""}"
    }
    is ActivityTime.Weekly -> "${rule.weekdays.sortedBy { it.value }.joinToString { it.getDisplayName(java.time.format.TextStyle.FULL, spanishLocale) }} · ${rule.startTime}–${rule.endTime}${if (rule.endDayOffset == 1) " del día siguiente" else ""} · ${rule.zoneId}. Cada ${rule.intervalWeeks} semana(s), desde ${rule.startDate}${rule.until?.let { " hasta $it" } ?: ", sin fin de vigencia"}."
}

internal fun ActivityTime.shortDescription(zone:String):String=when(this){
    ActivityTime.Unscheduled->"Sin fecha"
    is ActivityTime.DateOnly->date.format(DateTimeFormatter.ofPattern("d MMM",spanishLocale))
    is ActivityTime.Timed->start.atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("d MMM · HH:mm",spanishLocale))
    is ActivityTime.Weekly->"Cada semana · ${rule.startTime}"
}
