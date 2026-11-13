package pe.studyplanner.aipet.ui.academic

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal val spanishLocale = java.util.Locale.forLanguageTag("es-PE")
internal val displayDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, yyyy", spanishLocale)

@Composable
internal fun FormMessage(mutation: AcademicMutation) {
    when (mutation) {
        AcademicMutation.Saving -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text("Guardando en este dispositivo…")
        }
        is AcademicMutation.Failed -> Text(mutation.message, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        is AcademicMutation.Saved -> Text(mutation.message, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        else -> Unit
    }
}

@Composable
internal fun ChoiceField(label: String, selected: String, options: List<Pair<String, String>>, enabled: Boolean = true, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.titleSmall)
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(options.firstOrNull { it.first == selected }?.second ?: selected)
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            options.forEach { (key, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onChange(key) })
            }
        }
    }
}

@Composable
internal fun ToggleField(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
        .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, Modifier.weight(1f).padding(top = 12.dp, bottom = 12.dp))
    }
}

@Composable
internal fun DateInput(label: String, value: String, error: String?, enabled: Boolean, onChange: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = true, isError = error != null,
        supportingText = { Text(error ?: "AAAA-MM-DD. Elige una fecha o escríbela.") })
    TextButton(enabled = enabled, onClick = {
        val date = runCatching { LocalDate.parse(value) }.getOrElse { LocalDate.now() }
        DatePickerDialog(context, { _, year, month, day -> onChange(LocalDate.of(year, month + 1, day).toString()) },
            date.year, date.monthValue - 1, date.dayOfMonth).show()
    }) { Text("Elegir $label") }
}

@Composable
internal fun TimeInput(label: String, value: String, error: String?, enabled: Boolean, onChange: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = true, isError = error != null,
        supportingText = { Text(error ?: "HH:mm, formato de 24 horas.") })
    TextButton(enabled = enabled, onClick = {
        val time = runCatching { LocalTime.parse(value) }.getOrElse { LocalTime.of(9, 0) }
        TimePickerDialog(context, { _, hour, minute -> onChange("%02d:%02d".format(hour, minute)) },
            time.hour, time.minute, true).show()
    }) { Text("Elegir $label") }
}

@Composable
internal fun Confirmation(title: String, text: String, confirm: String = "Confirmar", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Text(text, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

internal fun normalizeAcademicName(value: String): String = java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFC)
    .lowercase(spanishLocale).replace(Regex("\\s+"), " ")
