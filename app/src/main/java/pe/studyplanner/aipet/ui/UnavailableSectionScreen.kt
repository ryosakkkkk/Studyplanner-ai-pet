package pe.studyplanner.aipet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.studyplanner.aipet.ui.components.AppSection
import pe.studyplanner.aipet.ui.components.SectionNavigation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnavailableSectionScreen(
    section: AppSection,
    onNavigate: (AppSection) -> Unit,
    onBack: () -> Unit,
) {
    val explanation = when (section) {
        AppSection.CALENDAR -> "Esta versión todavía no permite registrar ni consultar compromisos. Aquí no se muestran actividades de ejemplo como si fueran tuyas."
        AppSection.TASKS -> "Todavía no puedes crear, editar ni completar tareas en esta versión. No hay tareas registradas mediante la aplicación."
        AppSection.CHAT -> "La conversación con IA todavía no está disponible. Esta pantalla no recibe mensajes, no usa el micrófono ni ejecuta acciones."
        else -> error("Esta pantalla solo describe secciones pendientes.")
    }
    Scaffold(
        modifier = Modifier.testTag("section_screen_${section.route}"),
        topBar = {
            TopAppBar(
                title = { Text(section.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a Inicio")
                    }
                },
            )
        },
        bottomBar = { SectionNavigation(section, onNavigate) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("Aún no disponible", style = MaterialTheme.typography.headlineMedium)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Text(explanation, modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Por ahora puedes personalizar tu perfil y tu compañero desde Ajustes. Usa Secciones para seguir explorando.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
