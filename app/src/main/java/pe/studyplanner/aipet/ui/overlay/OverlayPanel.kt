package pe.studyplanner.aipet.ui.overlay

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.platform.overlay.*

@Composable
fun OverlayPanel(profile: UserProfile) {
    val context = LocalContext.current
    val localLifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycle = context.activity()?.lifecycle ?: localLifecycle
    val status by PetOverlay.status.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(OverlayPermission.allowed(context)) }
    var notifications by remember { mutableStateOf(PetOverlay.notificationsAllowed(context)) }
    var permissionReview by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val preferences=remember(context){OverlayPreferences(context)}
    var petSize by remember { mutableStateOf(preferences.size) }
    var snapToEdge by remember { mutableStateOf(preferences.snapToEdge) }
    var reducedMotion by remember { mutableStateOf(preferences.reducedMotion) }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        allowed = OverlayPermission.allowed(context)
        notifications = PetOverlay.notificationsAllowed(context)
    }
    LaunchedEffect(lifecycle, context) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Some devices publish the changed capability after the return
            // callback. Keep this visible panel fresh without starting a session.
            while (isActive) {
                allowed = OverlayPermission.allowed(context)
                notifications = PetOverlay.notificationsAllowed(context)
                delay(750)
            }
        }
    }
    fun settings(intent: Intent) {
        PetOverlay.stop(context)
        error = null
        try { settingsLauncher.launch(intent) }
        catch (_: RuntimeException) { error = "No se pudo abrir Ajustes. Busca StudyPlanner en los ajustes de Android." }
    }
    OverlayControls(status, allowed, notifications, profile.petVisible,
        onPermission = { permissionReview = true },
        onNotifications = { settings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) },
        onShow = { context.activity()?.let { PetOverlay.show(it, profile) } ?: run { error = "Abre StudyPlanner para iniciar la sesión." } },
        onStop = { PetOverlay.stop(context) },
        settingsContent = {
            Text("Tamaño",style=MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OverlaySize.entries.forEach { size ->
                    FilterChip(selected=petSize==size,onClick={petSize=size;preferences.setSize(size)},
                        enabled=!status.active && !status.starting,label={Text(size.label)},modifier=Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text("Acoplar al borde al soltar",Modifier.weight(1f))
                Switch(checked=snapToEdge,onCheckedChange={snapToEdge=it;preferences.setSnapToEdge(it)},enabled=!status.active && !status.starting)
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text("Reducir movimiento",Modifier.weight(1f))
                Switch(checked=reducedMotion,onCheckedChange={reducedMotion=it;preferences.setReducedMotion(it)},enabled=!status.active && !status.starting)
            }
            if(status.active || status.starting) Text("Detén la sesión para cambiar su tamaño o movimiento.",style=MaterialTheme.typography.bodySmall)
        })
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    if (permissionReview) AlertDialog(
        onDismissRequest = { permissionReview = false },
        title = { Text("Mascota sobre otras apps") },
        text = { Text("Es opcional. Android pedirá permiso para mostrar una ventana pequeña sobre otras apps. Puedes moverla y cerrarla; no lee la pantalla ni captura audio. La sesión termina al apagar la pantalla o después de diez minutos. Android puede ocultarla en pantallas protegidas. Volver de Ajustes no la inicia: debes pulsar Mostrar.", modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { permissionReview = false; settings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }) { Text("Ir al permiso de Android") } },
        dismissButton = { TextButton(onClick = { permissionReview = false }) { Text("Ahora no") } })
}

@Composable
internal fun OverlayControls(status: OverlayStatus, allowed: Boolean, notifications: Boolean, petVisible: Boolean,
    onPermission: () -> Unit, onNotifications: () -> Unit, onShow: () -> Unit, onStop: () -> Unit,
    settingsContent: (@Composable () -> Unit)? = null) {
    HorizontalDivider()
    Text("Mascota flotante", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Text("Aparece sobre otras apps: tócala para abrir el asistente, arrástrala para moverla y mantenla presionada para cerrarla. Dentro de StudyPlanner se oculta. Dura hasta 10 minutos y no escucha.")
    settingsContent?.invoke()
    Text(status.message, modifier = Modifier.testTag("overlay_status").semantics { liveRegion = LiveRegionMode.Polite })
    if (!petVisible) Text("Tu mascota está oculta. Muéstrala antes de iniciar la mascota flotante.")
    Text(if (allowed) "Permiso concedido. Tú eliges cuándo mostrarla." else "Permiso de superposición: pendiente. La app funciona sin concederlo.")
    OutlinedButton(onClick = onPermission, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("overlay_permission")) { Text("Revisar permiso de superposición") }
    if (!notifications) OutlinedButton(onClick = onNotifications, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("overlay_notifications")) { Text("Revisar notificaciones de la sesión") }
    Button(onClick = onShow, enabled = allowed && notifications && petVisible && !status.active && !status.starting,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("overlay_show")) { Text("Mostrar mascota flotante") }
    OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("overlay_stop")) { Text("Detener sesión flotante") }
}

private tailrec fun Context.activity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
