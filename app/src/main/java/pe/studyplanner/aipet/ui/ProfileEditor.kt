package pe.studyplanner.aipet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import pe.studyplanner.aipet.domain.profile.*
import pe.studyplanner.aipet.ui.components.PetAvatar
import pe.studyplanner.aipet.ui.components.PetStill
import androidx.compose.foundation.BorderStroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditor(
    profile: UserProfile,
    onboarding: Boolean,
    saveState: SaveState,
    onSave: (UserProfile) -> Unit,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    voiceEnabled: Boolean = false,
    hasAlerts: Boolean = false,
    extrasContent: (@Composable () -> Unit)? = null,
    screenTitle: String? = null,
    onReactions:(()->Unit)? = null,
) {
    var student by rememberSaveable { mutableStateOf(profile.studentName) }
    var pet by rememberSaveable { mutableStateOf(profile.petName) }
    var zone by rememberSaveable { mutableStateOf(profile.zoneId) }
    var avatar by rememberSaveable { mutableStateOf(profile.avatar.name) }
    var theme by rememberSaveable { mutableStateOf(profile.theme.name) }
    var week by rememberSaveable { mutableIntStateOf(profile.weekStartsOn.value) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var dayMenu by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val draft = profile.copy(
        studentName = student, petName = pet, zoneId = zone,
        avatar = AvatarStyle.valueOf(avatar), theme = ThemeMode.valueOf(theme),
        weekStartsOn = DayOfWeek.of(week), onboardingCompleted = true
    )
    val errors = ProfileValidator.validate(draft)
    val dirty = ProfileValidator.normalize(draft).copy(onboardingCompleted = profile.onboardingCompleted) != profile
    val saving = saveState is SaveState.Saving
    val requestBack = {
        if (!saving) {
            if (dirty) discard = true else onBack()
        }
    }
    BackHandler(enabled = true) { requestBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle ?: "Personalización", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = requestBack, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
        // La vista previa queda fija arriba: al elegir mascota o colores se ve el cambio sin subir la pantalla.
        // Fondo con el degradado de la marca (azul → índigo) y la mascota entera dentro del recuadro; se gira con el dedo.
        val dark = pe.studyplanner.aipet.ui.components.isDarkTheme()
        val previewBrush = Brush.verticalGradient(
            if (dark) listOf(androidx.compose.ui.graphics.Color(0xFF1A2350), androidx.compose.ui.graphics.Color(0xFF272560))
            else listOf(androidx.compose.ui.graphics.Color(0xFFE3EEFF), androidx.compose.ui.graphics.Color(0xFFECE9FF)))
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth().height(232.dp)
            .clip(RoundedCornerShape(28.dp)).background(previewBrush), contentAlignment = Alignment.Center) {
            pe.studyplanner.aipet.ui.components.PetStage(Modifier.fillMaxSize())
            PetAvatar(AvatarStyle.valueOf(avatar), Modifier.fillMaxSize().padding(vertical = 6.dp).testTag("editor_preview"), mood = "wave")
            Text("Gírala con el dedo", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (onboarding) {
                Text("Elige tu mascota", style = MaterialTheme.typography.headlineSmall)
                Text("Puedes cambiarla cuando quieras. Tu nombre es opcional.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarStyle.entries.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            val selected = avatar == option.name
                            Surface(
                                modifier = Modifier.weight(1f).heightIn(min = 104.dp)
                                    .selectable(selected = selected, enabled = !saving, role = Role.RadioButton) { avatar = option.name; onEdit() },
                                shape = RoundedCornerShape(18.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PetStill(option, Modifier.size(64.dp), contentDescription = null)
                                    // El nombre se achica solo si la ficha es angosta: nunca se sale de su casilla.
                                    androidx.compose.foundation.text.BasicText(option.label, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp),
                                        style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp, stepSize = 0.5.sp))
                                }
                            }
                        }
                    }
                }
            }
            pe.studyplanner.aipet.ui.components.PetColorPicker(AvatarStyle.valueOf(avatar), enabled = !saving)
            OutlinedTextField(
                value = pet,
                onValueChange = { pet = it; onEdit() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nombre de tu mascota") },
                supportingText = { Text(if (attempted && errors["petName"] != null) errors.getValue("petName") else "De 1 a 24 caracteres.") },
                isError = attempted && errors.containsKey("petName"),
                singleLine = true, enabled = !saving
            )
            if(onReactions!=null)TextButton(onClick=onReactions,enabled=!saving,modifier=Modifier.heightIn(min=48.dp)){Text("Ver sus reacciones")}
            HorizontalDivider()
            OutlinedTextField(
                value = student, onValueChange = { student = it; onEdit() },
                modifier = Modifier.fillMaxWidth(), label = { Text("Tu nombre (opcional)") },
                supportingText = { Text(if (attempted && errors["studentName"] != null) errors.getValue("studentName") else "Solo para saludarte. Se guarda en este dispositivo.") },
                isError = attempted && errors.containsKey("studentName"),
                singleLine = true, enabled = !saving
            )
            TextButton(onClick={advanced=!advanced}) {Text(if(advanced) "Ocultar preferencias de calendario" else "Preferencias de calendario")}
            if(advanced) {
            OutlinedTextField(
                value = zone, onValueChange = { zone = it; onEdit() },
                modifier = Modifier.fillMaxWidth(), label = { Text("Zona horaria") },
                supportingText = { Text(if (attempted && errors["zoneId"] != null) errors.getValue("zoneId") else "Ejemplo: America/Lima") },
                isError = attempted && errors.containsKey("zoneId"),
                singleLine = true, enabled = !saving
            )
            Text("Inicio de semana", style = MaterialTheme.typography.titleMedium)
            Box {
                OutlinedButton(onClick = { dayMenu = true }, enabled = !saving) {
                    Text(DayOfWeek.of(week).getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-PE")))
                }
                DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                    DayOfWeek.entries.forEach { day ->
                        DropdownMenuItem(
                            text = { Text(day.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-PE"))) },
                            onClick = { week = day.value; dayMenu = false; onEdit() }
                        )
                    }
                }
            }
            }
            HorizontalDivider()
            Text("Apariencia", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                ThemeMode.entries.forEach { option ->
                    val label=when(option){ThemeMode.SYSTEM->"Según el dispositivo";ThemeMode.LIGHT->"Clara";ThemeMode.DARK->"Oscura"}
                    ChoiceRow(label,theme==option.name,!saving){theme=option.name;onEdit()}
                }
            }
            when (saveState) {
                is SaveState.Failed -> Text(
                    saveState.message, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                SaveState.Saved -> if (!dirty) Text(
                    "Cambios guardados en este dispositivo.",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                else -> Unit
            }
            Button(
                onClick = {
                    attempted = true
                    focus.clearFocus()
                    if (errors.isEmpty()) onSave(draft) else if(errors.containsKey("zoneId")) advanced=true
                },
                enabled = !saving && (onboarding || dirty),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Guardando…")
                } else Text(if (onboarding) "Guardar y empezar" else "Guardar")
            }
            Text(
                if(voiceEnabled) "El micrófono solo se solicita al elegir dictar y aceptar el aviso previo. El servicio de voz de Android puede procesar audio en sus servidores. La lectura es opcional y los avisos tienen permisos propios. No se guarda audio ni se recogen datos de investigación."
                else if(hasAlerts) "Los avisos requieren sus permisos de Android. No se solicita micrófono ni se recogen datos de investigación."
                else "El micrófono y las notificaciones no se solicitan en esta versión. No se recogen datos de investigación.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            extrasContent?.invoke()
        }
        }
    }
    if (discard) AlertDialog(
        onDismissRequest = { discard = false },
        title = { Text("¿Descartar tus cambios?") },
        text = {
            Text(
                "Los cambios que aún no guardaste se perderán.",
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        },
        confirmButton = { TextButton(onClick = { discard = false; onEdit(); onBack() }) { Text("Descartar") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Seguir editando") } }
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
