package pe.studyplanner.aipet.ui.tutor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import pe.studyplanner.aipet.domain.tutor.SessionSummary
import pe.studyplanner.aipet.domain.tutor.TutorReviewPlanner
import pe.studyplanner.aipet.domain.tutor.UnaidedReviewItem

private const val OPTION_LETTERS = "ABCDE"

/** «martes 3 de noviembre», en español de Perú. */
internal fun formatReviewDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-PE")))

/** Tarjeta con una frase destacada del tutor. */
@Composable
internal fun TutorMessageCard(title: String, text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            Modifier.padding(16.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.semantics { heading() },
            )
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** Aviso fijo de la comprobación sin ayuda. */
@Composable
internal fun UnaidedBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("tutor_unaided_banner"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Comprobación sin ayuda", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(
                "Sin pistas ni avisos. Al final verás cómo te fue.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Enunciado del ejercicio. */
@Composable
internal fun StatementCard(statement: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("tutor_statement"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Text(statement, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Una pista ya revelada. */
@Composable
internal fun HintCard(number: Int, total: Int, text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(14.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Pista $number de $total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

/**
 * Alternativa A–E (al menos 60 dp de alto). Mientras se puede responder es un botón; cuando ya no (alternativa
 * descartada, o ejercicio resuelto) es una tarjeta sin acción, para que los lectores de pantalla no la anuncien como
 * botón. [eliminated] atenúa y tacha las ya descartadas; [correct] resalta la correcta cuando el ejercicio la reveló.
 */
@Composable
internal fun OptionButton(
    index: Int,
    text: String,
    enabled: Boolean,
    eliminated: Boolean,
    correct: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val letter = OPTION_LETTERS[index].toString()
    val colors = MaterialTheme.colorScheme
    val description = buildString {
        append("Opción $letter: $text")
        if (eliminated) append(". Descartada")
        if (correct) append(". Respuesta correcta")
        if (selected) append(". Elegida")
    }
    val border = BorderStroke(if (correct || selected) 2.dp else 1.dp, if (correct) colors.tertiary else if (selected) colors.primary else colors.outline)
    val shape = RoundedCornerShape(16.dp)
    val label: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = CircleShape, color = if (correct) colors.tertiaryContainer else colors.primaryContainer) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Text(letter, style = MaterialTheme.typography.titleMedium, color = if (correct) colors.onTertiaryContainer else colors.onPrimaryContainer)
                }
            }
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (eliminated) TextDecoration.LineThrough else null,
            )
            if (correct) Text("✓ Correcta", style = MaterialTheme.typography.labelMedium, color = colors.tertiary)
        }
    }
    val base = modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("tutor_option_$letter")
    if (enabled) {
        OutlinedButton(
            onClick = onClick,
            modifier = base.semantics { contentDescription = description },
            shape = shape,
            border = border,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface,
                containerColor = if (selected) colors.primaryContainer.copy(alpha = .55f) else androidx.compose.ui.graphics.Color.Transparent),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) { label() }
    } else {
        Surface(
            modifier = base.semantics(mergeDescendants = true) { contentDescription = description },
            shape = shape,
            color = if (correct) colors.tertiaryContainer.copy(alpha = .35f) else colors.surface,
            contentColor = if (eliminated) colors.onSurface.copy(alpha = .6f) else colors.onSurface,
            border = border,
        ) {
            Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { label() }
        }
    }
}

/** Resultados de la sesión: con ayuda frente a sin ayuda, pistas usadas y estado de dominio. */
@Composable
internal fun SummaryCard(summary: SessionSummary, hintsUsed: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("tutor_summary"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tus resultados", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            SummaryRow("Práctica guiada (con ayuda)", "${summary.guidedSolved} de ${summary.guidedTotal} resueltos")
            SummaryRow("Pistas usadas", "$hintsUsed")
            SummaryRow("Comprobación sin ayuda", "${summary.unaidedCorrect} de ${summary.unaidedTotal} correctas")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Estado del tema:", style = MaterialTheme.typography.bodyMedium)
                MasteryBadge(summary.mastery)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** Repaso de un ejercicio de la comprobación sin ayuda. Solo se muestra en el resumen. */
@Composable
internal fun UnaidedReviewCard(number: Int, item: UnaidedReviewItem, modifier: Modifier = Modifier) {
    var showSolution by remember(item.exerciseId) { mutableStateOf(false) }
    val verdict = if (item.correct) "✓ Correcta" else "✗ Incorrecta"
    val chosen = if (item.selectedIndex in item.options.indices) {
        "${OPTION_LETTERS[item.selectedIndex]}) ${item.options[item.selectedIndex]}"
    } else {
        "Marcaste «No sé»"
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Pregunta $number · $verdict", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(item.statement, style = MaterialTheme.typography.bodyMedium)
            Text("Tu respuesta: $chosen", style = MaterialTheme.typography.bodyMedium)
            if (!item.correct) {
                val right = "${OPTION_LETTERS[item.correctIndex]}) ${item.options[item.correctIndex]}"
                Text("Respuesta correcta: $right", style = MaterialTheme.typography.bodyMedium)
                Text(item.feedback, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { showSolution = !showSolution }) {
                Text(if (showSolution) "Ocultar solución" else "Ver solución paso a paso")
            }
            if (showSolution) {
                Text(
                    if (item.solutionSteps.size == 1) item.solutionSteps[0]
                    else item.solutionSteps.mapIndexed { i, step -> "${i + 1}) $step" }.joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Las preguntas de banco traen sus fuentes; aquí ya es el resumen, así que no adelantan la respuesta.
                item.sources.forEach { source ->
                    Text(
                        "Fuente: ${source.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("tutor_source"),
                    )
                }
            }
        }
    }
}

/** Propuesta de repaso. No toca la agenda hasta que el estudiante pulsa «Programar repaso». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewProposalCard(
    review: ReviewUi,
    onHour: (Int) -> Unit,
    onSchedule: () -> Unit,
    onDecline: () -> Unit,
    onOpenAgenda: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (review) {
        ReviewUi.None -> Unit
        is ReviewUi.Offer -> Card(
            modifier = modifier.fillMaxWidth().testTag("tutor_review_offer"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "¿Programar repaso para el ${formatReviewDate(review.dueOn)}?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.semantics { heading() },
                )
                Text(review.proposal.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (review.canSchedule) {
                    Text(
                        "Hora de inicio · ${review.minutes} minutos",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (hour in TutorReviewPlanner.START_HOURS) {
                            FilterChip(
                                selected = review.startHour == hour,
                                onClick = { onHour(hour) },
                                enabled = !review.saving,
                                label = { Text("$hour:00") },
                            )
                        }
                    }
                    Text(
                        "Se agregará a tu Agenda solo si pulsas «Programar repaso».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (review.error.isNotBlank()) {
                        Text(review.error, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = onSchedule,
                        enabled = !review.saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("tutor_review_confirm"),
                    ) { Text(if (review.saving) "Programando…" else "Programar repaso") }
                    OutlinedButton(
                        onClick = onDecline,
                        enabled = !review.saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text("Ahora no") }
                } else {
                    Text(
                        "La agenda no está disponible en este momento, así que solo te lo sugerimos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        is ReviewUi.Saved -> Card(
            modifier = modifier.fillMaxWidth().testTag("tutor_review_saved"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val prefix = if (review.alreadyScheduled) "Ese repaso ya estaba en tu Agenda" else "Repaso programado"
                Text(
                    "$prefix: ${formatReviewDate(review.dueOn)} a las ${review.startHour}:00.",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                OutlinedButton(onClick = onOpenAgenda, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Ver en Agenda") }
            }
        }
        ReviewUi.Declined -> Text(
            "No programamos el repaso. Puedes crearlo cuando quieras desde la Agenda.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
