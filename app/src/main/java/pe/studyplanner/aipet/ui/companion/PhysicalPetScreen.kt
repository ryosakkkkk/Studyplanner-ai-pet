package pe.studyplanner.aipet.ui.companion

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.studyplanner.aipet.domain.companion.PetExpression
import pe.studyplanner.aipet.domain.companion.PetLink
import pe.studyplanner.aipet.domain.companion.PetLinkStatus
import pe.studyplanner.aipet.domain.companion.PetSound
import pe.studyplanner.aipet.platform.companion.PetPermissions

/**
 * Ajustes de la mascota física (StudyPet): conectar, desconectar, ver el estado y probar la cara, el sonido y los
 * botones. Es opcional: sin la mascota, toda la app funciona igual.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicalPetScreen(link: PetLink, onBack: () -> Unit) {
    val state by link.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permissionMessage by remember { mutableStateOf("") }
    var denied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        link.refresh()
        if (result.isNotEmpty() && result.values.all { it }) {
            permissionMessage = ""
            denied = false
            link.connect()
        } else {
            denied = true
            permissionMessage = "Sin ese permiso no podemos buscar la mascota. Puedes concederlo desde los ajustes de la app."
        }
    }

    DisposableEffect(owner, link) {
        link.refresh()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) link.refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun connect() {
        val missing = PetPermissions.missing(context)
        if (missing.isEmpty()) link.connect() else permissionLauncher.launch(missing.toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mascota física", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("physical_pet"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "StudyPet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "Una mascota con pantalla y botones que reacciona cuando practicas matemática: muestra caras, " +
                            "suena cuando aciertas y te deja responder con los botones A a E. Es opcional: la app " +
                            "funciona igual sin ella.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    Modifier.padding(16.dp).testTag("physical_pet_status"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        Text(
                            "Estado: ${statusLabel(state.status)}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    if (state.message.isNotBlank()) {
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    if (state.connected) {
                        if (state.firmware.isNotBlank()) Text("Firmware: ${state.firmware}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            state.batteryPercent?.let { "Batería estimada: $it %" } ?: "Batería: esta mascota no la mide.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            if (state.lastButton.isBlank()) "Último botón: aún no pulsaste ninguno." else "Último botón: ${state.lastButton}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("physical_pet_last_button"),
                        )
                    }
                    if (permissionMessage.isNotBlank()) {
                        Text(permissionMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            when (state.status) {
                PetLinkStatus.UNSUPPORTED -> Unit
                PetLinkStatus.BLUETOOTH_OFF -> {
                    Button(
                        onClick = { openSystemScreen(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text("Abrir ajustes de Bluetooth") }
                    OutlinedButton(onClick = { link.refresh() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Ya lo activé")
                    }
                }
                PetLinkStatus.PERMISSION_NEEDED -> {
                    Button(
                        onClick = { connect() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("physical_pet_permission"),
                    ) { Text("Dar permiso y buscar") }
                    if (denied) {
                        OutlinedButton(
                            onClick = {
                                openSystemScreen(
                                    context,
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                )
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) { Text("Abrir ajustes de la app") }
                    }
                }
                PetLinkStatus.IDLE -> Button(
                    onClick = { connect() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("physical_pet_connect"),
                ) { Text("Conectar") }
                PetLinkStatus.SCANNING, PetLinkStatus.CONNECTING -> OutlinedButton(
                    onClick = { link.disconnect() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("physical_pet_cancel"),
                ) { Text("Cancelar") }
                PetLinkStatus.CONNECTED -> {
                    Button(
                        onClick = {
                            link.showFace(PetExpression.FELIZ, "¡Hola desde la app!")
                            link.playSound(PetSound.CORRECTO)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("physical_pet_test"),
                    ) { Text("Probar cara y sonido") }
                    OutlinedButton(
                        onClick = { link.disconnect() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("physical_pet_disconnect"),
                    ) { Text("Desconectar") }
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cómo se usa", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    Text("• Botones A a E: responden la alternativa del ejercicio que ves en el teléfono.", style = MaterialTheme.typography.bodySmall)
                    Text("• TALK (pulsación corta): empieza a escuchar tu respuesta; el teléfono te pide confirmar.", style = MaterialTheme.typography.bodySmall)
                    Text("• TALK mantenido (casi un segundo): pasa a lo siguiente.", style = MaterialTheme.typography.bodySmall)
                    Text("• En la comprobación sin ayuda, la mascota no dice si acertaste.", style = MaterialTheme.typography.bodySmall)
                    Text("Privacidad", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
                    Text(
                        "La conexión no usa emparejamiento ni cifrado. Solo viajan frases cortas del tutor, caras, sonidos y la " +
                            "letra del botón que pulsas; ningún dato personal ni audio. Conecta la mascota solo cuando estés " +
                            "cerca de ella.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun statusLabel(status: PetLinkStatus): String = when (status) {
    PetLinkStatus.UNSUPPORTED -> "no disponible en este teléfono"
    PetLinkStatus.BLUETOOTH_OFF -> "Bluetooth apagado"
    PetLinkStatus.PERMISSION_NEEDED -> "falta un permiso"
    PetLinkStatus.IDLE -> "sin conexión"
    PetLinkStatus.SCANNING -> "buscando"
    PetLinkStatus.CONNECTING -> "conectando"
    PetLinkStatus.CONNECTED -> "conectada"
}

/** Abre una pantalla de ajustes del sistema. Si el teléfono no la tiene, no pasa nada. */
private fun openSystemScreen(context: Context, intent: Intent) {
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Sin pantalla de ajustes disponible: el estudiante puede abrirla a mano.
    }
}
