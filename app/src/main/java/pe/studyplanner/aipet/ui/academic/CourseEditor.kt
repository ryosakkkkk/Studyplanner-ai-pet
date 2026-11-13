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
import androidx.compose.ui.unit.dp
import pe.studyplanner.aipet.domain.academic.Course
import pe.studyplanner.aipet.domain.academic.DatasetScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseEditor(initial: Course, courses: List<Course>, mutation: AcademicMutation,
    onSave: (Course) -> Unit, onEdit: () -> Unit, onBack: () -> Unit, scope: DatasetScope,
    readAvailable: Boolean = true, onRetryRead: () -> Unit = {}) {
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var code by rememberSaveable(initial.id) { mutableStateOf(initial.code) }
    var color by rememberSaveable(initial.id) { mutableStateOf(initial.colorToken) }
    val originalVersion by rememberSaveable(initial.id) { mutableLongStateOf(initial.version) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var duplicate by rememberSaveable { mutableStateOf(false) }
    val busy = mutation is AcademicMutation.Saving
    val dirty = name != initial.name || code != initial.code || color != initial.colorToken
    val back = { if (!busy) { if (dirty) discard = true else onBack() } }
    BackHandler { back() }
    val nameError = when {
        name.isBlank() -> "Escribe el nombre del curso."
        name.trim().length > 120 -> "Usa como máximo 120 caracteres."
        else -> null
    }
    val codeError = if (code.trim().length > 40) "Usa como máximo 40 caracteres." else null
    val draft = initial.copy(name = name.trim(), code = code.trim(), colorToken = color, version = originalVersion)
    val matches = courses.filter { it.id != initial.id && (
        normalizeAcademicName(it.name) == normalizeAcademicName(name) ||
            (code.isNotBlank() && normalizeAcademicName(it.code) == normalizeAcademicName(code))) }
    Scaffold(topBar = { TopAppBar(title = { Text(if (initial.version == 0L) "Crear" else "Editar") },
        actions = { Text(scope.name, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.labelSmall) },
        navigationIcon = { IconButton(onClick = back, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DatasetBanner(scope)
            if (!readAvailable) {
                Text("No se puede guardar hasta recuperar la lectura de la agenda. El borrador se conserva.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetryRead, enabled = !busy) { Text("Reintentar lectura") }
            }
            Text("Los cursos y sus actividades se guardan localmente.")
            OutlinedTextField(name, { name = it; onEdit() }, label = { Text("Nombre del curso") }, enabled = !busy,
                modifier = Modifier.fillMaxWidth(), singleLine = true, isError = attempted && nameError != null,
                supportingText = { Text(if (attempted && nameError != null) nameError else "Obligatorio, hasta 120 caracteres.") })
            OutlinedTextField(code, { code = it; onEdit() }, label = { Text("Código o alias (opcional)") }, enabled = !busy,
                modifier = Modifier.fillMaxWidth(), singleLine = true, isError = attempted && codeError != null,
                supportingText = { Text(if (attempted && codeError != null) codeError else "Ejemplo: MAT101. Hasta 40 caracteres.") })
            ChoiceField("Color identificador", color, listOf("INDIGO" to "Índigo", "BLUE" to "Azul", "GREEN" to "Verde", "ORANGE" to "Naranja", "PURPLE" to "Morado"), !busy) {
                color = it; onEdit()
            }
            Text("El nombre y el código siguen visibles: el color nunca es la única forma de identificar un curso.")
            FormMessage(mutation)
            Button(onClick = {
                attempted = true
                if (nameError == null && codeError == null) {
                    if (matches.isNotEmpty()) duplicate = true else onSave(draft)
                }
            }, enabled = !busy && readAvailable && (initial.version == 0L || dirty), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Guardar curso")
            }
            OutlinedButton(onClick = back, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Cancelar") }
        }
    }
    if (discard) Confirmation("¿Descartar borrador?", "Los cambios de este curso no se guardarán.", "Descartar", onConfirm = { discard = false; onBack() }, onDismiss = { discard = false })
    if (duplicate) Confirmation("Posible curso duplicado", "Ya existe ${matches.joinToString { it.name }}. Puedes volver y revisar, o crear otro curso con un identificador distinto. ¿Guardar de todos modos?", "Guardar otro", onConfirm = { duplicate = false; onSave(draft) }, onDismiss = { duplicate = false })
}
