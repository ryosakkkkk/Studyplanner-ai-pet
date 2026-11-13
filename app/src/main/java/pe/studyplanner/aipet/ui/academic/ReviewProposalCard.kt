package pe.studyplanner.aipet.ui.academic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch
import pe.studyplanner.aipet.data.tutor.TutorHistory
import pe.studyplanner.aipet.domain.academic.AcademicRepository
import pe.studyplanner.aipet.domain.academic.AcademicSnapshot
import pe.studyplanner.aipet.domain.academic.DatasetScope
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.domain.today.TodaySummary
import pe.studyplanner.aipet.domain.tutor.ReviewProposal
import pe.studyplanner.aipet.domain.tutor.TutorContent
import pe.studyplanner.aipet.domain.tutor.TutorReviewPlanner
import pe.studyplanner.aipet.ui.components.PetStill

/**
 * Tarjeta «Repaso propuesto» de la Agenda (versión 0.17). Aparece después de una comprobación sin ayuda si el repaso
 * aún no está en la agenda. Solo se guarda cuando el estudiante toca «Aceptar».
 */
@Composable
fun ReviewProposalCard(history: TutorHistory, repository: AcademicRepository, snapshot: AcademicSnapshot, profile: UserProfile) {
    LaunchedEffect(history) { history.start() }
    val data by history.state.collectAsStateWithLifecycle()
    val zone = remember(profile.zoneId) { runCatching { ZoneId.of(profile.zoneId) }.getOrDefault(ZoneId.systemDefault()) }
    val today = LocalDate.now(zone)
    val ids = remember(snapshot) { snapshot.activities.mapTo(HashSet()) { it.id } }
    val pending = TodaySummary.pendingReview(data.attempts, zone, today, ids) ?: return
    val topic = TutorContent.catalog.topic(pending.topicId) ?: return
    // Día elegido como (día base, propuesta): «Cambiar día» cuenta desde hoy.
    var choice by remember(pending) { mutableStateOf(pending.base to pending.proposal) }
    var menu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val due = choice.second.dueOn(choice.first)
    Card(
        Modifier.fillMaxWidth().testTag("agenda_review"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            pe.studyplanner.aipet.ui.components.PetSticker(profile.avatar, "calendario", Modifier.size(68.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(if (choice.second.relearn) "Volver a aprender: " else "Repaso propuesto: ") }
                        append("${topic.title}, ${TodaySummary.dayLabel(due, today)} · ${TutorReviewPlanner.DEFAULT_MINUTES} min")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            saving = true; message = ""
                            scope.launch {
                                message = runCatching {
                                    repository.saveActivity(DatasetScope.PERSONAL,
                                        TutorReviewPlanner.activityFor(topic, choice.second, choice.first, profile.zoneId))
                                }.fold({ "" }, { "No se pudo guardar el repaso. Prueba otro día." })
                                saving = false
                            }
                        },
                        enabled = !saving,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("agenda_review_accept"),
                    ) { Text("Aceptar", maxLines = 1) }
                    Box {
                        FilledTonalButton(onClick = { menu = true }, enabled = !saving,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("agenda_review_change")) {
                            Text("Cambiar día", maxLines = 1)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            listOf(1, 2, 3, 7).forEach { days ->
                                val option = today.plusDays(days.toLong())
                                DropdownMenuItem(
                                    text = { Text(TodaySummary.dayLabel(option, today).replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        menu = false
                                        choice = today to ReviewProposal(pending.topicId, days, pending.proposal.reason, pending.proposal.relearn)
                                    },
                                )
                            }
                        }
                    }
                }
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
}
