package pe.studyplanner.aipet.ui.companion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.studyplanner.aipet.domain.pet.PetStickers
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.ui.components.PetAvatar
import pe.studyplanner.aipet.ui.components.PetSticker

/**
 * Galería «Reacciones»: las expresiones y poses de las láminas de la mascota sirven de botones. Al tocar una, la
 * mascota 3D hace el movimiento y la cara que le corresponden (no se muestra la lámina junto a la mascota).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PetReactionsScreen(profile: UserProfile, onBack: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf("feliz") }
    val label = PetStickers.ALL.firstOrNull { it.key == selected }?.label.orEmpty()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reacciones de ${profile.petName}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Solo la mascota 3D hace la reacción elegida; debajo, su nombre.
            Box(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(260.dp).clip(RoundedCornerShape(28.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(
                        androidx.compose.ui.graphics.Color(0xFFC7DCFF), androidx.compose.ui.graphics.Color(0xFFE0DAFF)))),
                contentAlignment = Alignment.BottomCenter,
            ) {
                PetAvatar(profile.avatar, Modifier.fillMaxSize().padding(bottom = 36.dp), mood = PetStickers.moodFor(selected),
                    showReaction = false, holdMood = true)
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp).testTag("reaction_selected").semantics { contentDescription = label }) {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PetStickers.GROUPS.forEach { group ->
                    Text(group.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() }.padding(top = 6.dp))
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.items.forEach { item ->
                            val on = item.key == selected
                            Surface(
                                onClick = { selected = item.key },
                                shape = RoundedCornerShape(18.dp),
                                color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                                border = if (on) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.size(width = 76.dp, height = 92.dp)
                                    .semantics { contentDescription = item.label }.testTag("reaction_${item.key}"),
                            ) {
                                Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    PetSticker(profile.avatar, item.key, Modifier.size(60.dp))
                                    Text(item.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
