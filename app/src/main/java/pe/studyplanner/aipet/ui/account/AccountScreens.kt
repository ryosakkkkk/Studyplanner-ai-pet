package pe.studyplanner.aipet.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import pe.studyplanner.aipet.StudyPlannerApplication
import pe.studyplanner.aipet.data.account.AccountState
import pe.studyplanner.aipet.data.account.CloudAccount
import pe.studyplanner.aipet.domain.account.AccountRules
import pe.studyplanner.aipet.domain.profile.AvatarStyle
import pe.studyplanner.aipet.ui.components.PetSticker
import pe.studyplanner.aipet.ui.theme.PetDesign

/** Cuenta de la app, si esta versión tiene servidor. */
@Composable
fun rememberCloudAccount(): CloudAccount? {
    val app = LocalContext.current.applicationContext as? StudyPlannerApplication
    return remember(app) { app?.cloudAccount?.takeIf { it.available } }
}

enum class AccountMode { SIGN_IN, REGISTER }

/**
 * Formulario para entrar o crear la cuenta. [onSignedIn] recibe el nombre y la fecha de la copia en la nube (si hay).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormScreen(account: CloudAccount, start: AccountMode, onBack: () -> Unit, onSignedIn: (name: String, cloudBackupAt: Long?) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(start) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }
    val state by account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val register = mode == AccountMode.REGISTER
    val nameProblem = if (register) AccountRules.nameProblem(name) else null
    val emailProblem = AccountRules.emailProblem(email)
    val passwordProblem = AccountRules.passwordProblem(password)
    LaunchedEffect(Unit) { account.clearMessage() }
    androidx.activity.compose.BackHandler(onBack = onBack)
    val submit = {
        tried = true
        if (nameProblem == null && emailProblem == null && passwordProblem == null && !state.busy) {
            focus.clearFocus()
            scope.launch {
                val result = if (register) account.register(name, email, password) else account.signIn(email, password)
                result.onSuccess { password = ""; onSignedIn(account.state.value.name, it.cloudBackupAt) }
            }
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = {}, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
    }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PetSticker(AvatarStyle.FOX, "nube", Modifier.size(120.dp))
            Text(if (register) "Crea tu cuenta" else "Entra a tu cuenta", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
            Text("Tus datos quedan guardados en la nube.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !register, onClick = { mode = AccountMode.SIGN_IN; tried = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2), modifier = Modifier.testTag("account_mode_signin")) { Text("Entrar") }
                SegmentedButton(selected = register, onClick = { mode = AccountMode.REGISTER; tried = false },
                    shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.testTag("account_mode_register")) { Text("Crear cuenta") }
            }
            if (register) OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth().testTag("account_name"),
                label = { Text("Tu nombre") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                isError = tried && nameProblem != null, supportingText = if (tried && nameProblem != null) {{ Text(nameProblem) }} else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            OutlinedTextField(email, { email = it.take(120) }, Modifier.fillMaxWidth().testTag("account_email"),
                label = { Text("Correo") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                isError = tried && emailProblem != null, supportingText = if (tried && emailProblem != null) {{ Text(emailProblem) }} else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
            OutlinedTextField(password, { password = it.take(128) }, Modifier.fillMaxWidth().testTag("account_password"),
                label = { Text("Contraseña") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Ocultar" else "Ver") } },
                isError = tried && passwordProblem != null,
                supportingText = { Text(if (tried && passwordProblem != null) passwordProblem else if (register) "Mínimo ${AccountRules.MIN_PASSWORD} caracteres" else "") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }))
            if (state.message.isNotBlank()) Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("account_message"))
            BrandButton(if (register) "Crear cuenta" else "Entrar", busy = state.busy, modifier = Modifier.fillMaxWidth().testTag("account_submit"), onClick = submit)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Botón principal con el degradado del logo. */
@Composable
fun BrandButton(text: String, modifier: Modifier = Modifier, busy: Boolean = false, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !busy, modifier = modifier.heightIn(min = 56.dp), shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).background(Brush.horizontalGradient(PetDesign.BrandGradient), CircleShape),
            contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 3.dp)
            else Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** «Cuenta» dentro de Perfil: entrar, o ver la copia en la nube, guardar ahora, recuperar y cerrar sesión. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(account: CloudAccount, pet: AvatarStyle, zoneId: String, onBack: () -> Unit) {
    val state by account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var form by rememberSaveable { mutableStateOf<AccountMode?>(null) }
    var choice by remember { mutableStateOf<Long?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    val mode = form
    if (mode != null && !state.signedIn) {
        AccountFormScreen(account, mode, onBack = { form = null }) { _, cloudAt ->
            form = null
            // Si ya hay una copia en la nube, el estudiante elige qué datos quedan; si no, se sube lo de este teléfono.
            if (cloudAt != null && cloudAt > 0) choice = cloudAt else scope.launch { account.backup(force = true) }
        }
        return
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Cuenta") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.background(Brush.linearGradient(PetDesign.BrandGradient)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PetSticker(pet, "nube", Modifier.size(110.dp))
                    Text(if (state.signedIn) state.name.ifBlank { "Tu cuenta" } else "Sin cuenta", color = Color.White,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.testTag("account_title"))
                    Text(if (state.signedIn) state.email else "Tus datos están solo en este teléfono.", color = Color.White.copy(alpha = .9f),
                        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
            if (state.signedIn) {
                Text(if (state.lastBackup > 0) "Última copia: ${shortDate(state.lastBackup, zoneId)}" else "Todavía no hay copia",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("account_last"))
                BrandButton("Guardar ahora", Modifier.fillMaxWidth().testTag("account_backup"), busy = state.busy) {
                    scope.launch { account.backup(force = true) }
                }
                OutlinedButton(onClick = { confirmRestore = true }, enabled = !state.busy, shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("account_restore")) { Text("Recuperar copia") }
                TextButton(onClick = { scope.launch { account.signOut() } }, modifier = Modifier.heightIn(min = 48.dp).testTag("account_signout")) {
                    Text("Cerrar sesión")
                }
                Text("La copia se guarda sola cuando sales de la app.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            } else {
                BrandButton("Crear cuenta", Modifier.fillMaxWidth().testTag("account_open_register")) { form = AccountMode.REGISTER }
                OutlinedButton(onClick = { form = AccountMode.SIGN_IN }, shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("account_open_signin")) { Text("Ya tengo cuenta") }
            }
            if (state.message.isNotBlank()) Text(state.message, textAlign = TextAlign.Center,
                color = if (state.message.startsWith("Copia")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("account_message"))
        }
    }
    val restore = {
        scope.launch { account.downloadForRestore().onSuccess { CloudAccount.restartApp(context) } }
        Unit
    }
    choice?.let { at ->
        AlertDialog(onDismissRequest = {}, title = { Text("Ya tienes una copia") },
            text = { Text("Hay datos en la nube del ${shortDate(at, zoneId)}. ¿Cuáles quieres usar?") },
            confirmButton = { TextButton(onClick = { choice = null; restore() }, modifier = Modifier.testTag("account_use_cloud")) { Text("Los de la nube") } },
            dismissButton = { TextButton(onClick = { choice = null; scope.launch { account.backup(force = true) } },
                modifier = Modifier.testTag("account_keep_phone")) { Text("Los de este teléfono") } })
    }
    if (confirmRestore) AlertDialog(onDismissRequest = { confirmRestore = false }, title = { Text("¿Recuperar la copia?") },
        text = { Text("Los datos de este teléfono se cambian por los de la nube y la app se vuelve a abrir.") },
        confirmButton = { TextButton(onClick = { confirmRestore = false; restore() }) { Text("Recuperar") } },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancelar") } })
}

private fun shortDate(millis: Long, zoneId: String): String {
    val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
    val time = Instant.ofEpochMilli(millis).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    return when (time.toLocalDate()) {
        today -> "hoy " + time.format(DateTimeFormatter.ofPattern("HH:mm"))
        today.minusDays(1) -> "ayer " + time.format(DateTimeFormatter.ofPattern("HH:mm"))
        else -> time.format(DateTimeFormatter.ofPattern("d/M/yyyy HH:mm"))
    }
}

/** Ayuda para mostrar el estado en Perfil. */
fun AccountState.summary(): String = if (signedIn) email else "Sin cuenta"
