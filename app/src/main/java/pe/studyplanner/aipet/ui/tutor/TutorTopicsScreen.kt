package pe.studyplanner.aipet.ui.tutor

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pe.studyplanner.aipet.data.tutor.TutorHistory
import pe.studyplanner.aipet.data.tutor.TutorHistoryState
import pe.studyplanner.aipet.domain.tutor.MasteryStatus
import pe.studyplanner.aipet.domain.tutor.Topic
import pe.studyplanner.aipet.domain.tutor.TutorContent
import pe.studyplanner.aipet.ui.components.AppSection
import pe.studyplanner.aipet.ui.components.SectionNavigation

/** Lista de temas agrupados por área, con su estado de dominio. Punto de entrada de la sección «Practicar». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorTopicsScreen(
    history: TutorHistory,
    onOpenTopic: (String) -> Unit,
    onNavigate: (AppSection) -> Unit,
    onBack: () -> Unit,
) {
    val data by history.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportMessage by remember { mutableStateOf("") }
    val catalog = TutorContent.catalog
    val mastered = catalog.topics.count { data.masteryOf(it.id) == MasteryStatus.DOMINIO_SIN_AYUDA }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Temas", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
            )
        },
        bottomBar = { SectionNavigation(AppSection.PRACTICE, onNavigate) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("tutor_topics"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                // Una línea de estado, sin tarjeta de presentación: la lista es lo principal.
                Text(
                    "Comprobados sin ayuda: $mastered de ${catalog.topics.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (data.error.isNotBlank()) {
                item { Text(data.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            // Un encabezado por área, en el orden del temario: primero matemática y después las demás materias.
            for (area in catalog.areas) {
                val topics = catalog.byArea(area)
                item(key = "area-${area.name}") {
                    Text(
                        area.label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp).semantics { heading() },
                    )
                }
                item(key = "group-${area.name}") {
                    pe.studyplanner.aipet.ui.RowGroup {
                        topics.forEachIndexed { index, topic ->
                            if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            TopicCard(topic, data, onClick = { onOpenTopic(topic.id) })
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = { scope.launch { exportMessage = shareAttempts(context, history) } },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("tutor_export"),
                    ) { Text("Exportar registro (CSV)") }
                    Text(
                        "El archivo solo tiene fecha, tema, ejercicio y opción elegida: ningún dato personal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (exportMessage.isNotBlank()) {
                        Text(exportMessage, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicCard(topic: Topic, data: TutorHistoryState, onClick: () -> Unit) {
    val status = data.masteryOf(topic.id)
    // Fila de lista agrupada: título, una línea de objetivo y el estado a la derecha.
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics {
            contentDescription = "${topic.title}. Estado: ${status.label}. Toca para abrir."
        }.testTag("tutor_topic_${topic.id}"),
    ) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(topic.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(topic.objective, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MasteryBadge(status, compact = true)
        }
    }
}

/** Insignia con el estado de dominio. El estado siempre va escrito: el color solo acompaña. */
@Composable
fun MasteryBadge(status: MasteryStatus, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        MasteryStatus.NO_INICIADO -> colors.surfaceVariant to colors.onSurfaceVariant
        MasteryStatus.EN_PRACTICA -> colors.secondaryContainer to colors.onSecondaryContainer
        MasteryStatus.SOLO_CON_AYUDA -> colors.primaryContainer to colors.onPrimaryContainer
        MasteryStatus.DOMINIO_SIN_AYUDA -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    val label = if (compact) when (status) {
        MasteryStatus.NO_INICIADO -> "Nuevo"
        MasteryStatus.EN_PRACTICA -> "En práctica"
        MasteryStatus.SOLO_CON_AYUDA -> "Con ayuda"
        MasteryStatus.DOMINIO_SIN_AYUDA -> "Comprobado"
    } else if (status == MasteryStatus.DOMINIO_SIN_AYUDA) "✓ ${status.label}" else status.label
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Tarjeta de Inicio que lleva a «Practicar». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorHomeCard(history: TutorHistory, onOpen: () -> Unit) {
    val data by history.state.collectAsStateWithLifecycle()
    val catalog = TutorContent.catalog
    val mastered = catalog.topics.count { data.masteryOf(it.id) == MasteryStatus.DOMINIO_SIN_AYUDA }
    val started = data.attempts.isNotEmpty()
    val summary = if (started) "$mastered de ${catalog.topics.size} temas con dominio sin ayuda"
    else "Aprende, practica con pistas y comprueba sin ayuda"
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).semantics {
            contentDescription = "Practicar para tu examen. $summary. Toca para abrir."
        }.testTag("home_practice"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Practicar para tu examen", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** Abre el selector de apps con el CSV como texto. No usa FileProvider: el archivo nunca sale del almacenamiento interno. */
private suspend fun shareAttempts(context: Context, history: TutorHistory): String {
    val csv = history.exportCsv()
    if (csv.lineSequence().count { it.isNotBlank() } <= 1) return "Aún no hay intentos para exportar."
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "Registro de práctica")
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    val chooser = Intent.createChooser(send, "Exportar registro (CSV)")
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(chooser)
        ""
    } catch (_: ActivityNotFoundException) {
        "No hay una app que pueda recibir el registro."
    } catch (_: Exception) {
        "No se pudo abrir el selector para compartir."
    }
}
