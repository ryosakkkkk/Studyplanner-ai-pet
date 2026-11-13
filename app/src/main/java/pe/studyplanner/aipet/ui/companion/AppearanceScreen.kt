package pe.studyplanner.aipet.ui.companion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pe.studyplanner.aipet.domain.profile.*
import pe.studyplanner.aipet.ui.SaveState
import pe.studyplanner.aipet.ui.components.PetAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(profile:UserProfile,saveState:SaveState,onTheme:(ThemeMode)->Unit,onBack:()->Unit) {
    Scaffold(topBar={TopAppBar(title={Text("Apariencia")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Volver")}})}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            PetAvatar(profile.avatar,Modifier.size(150.dp))
            Text("Tu espacio, como te gusta",style=MaterialTheme.typography.headlineSmall)
            Text("Elige un tema. Se guarda en este dispositivo.",style=MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                listOf(ThemeMode.LIGHT to "Claro",ThemeMode.DARK to "Oscuro").forEach { (mode,label) ->
                    val background=if(mode==ThemeMode.LIGHT)Color(0xFFFAF8FF)else Color(0xFF11192D)
                    val foreground=if(mode==ThemeMode.LIGHT)Color(0xFF211A47)else Color(0xFFE8E0FF)
                    Card(onClick={onTheme(mode)},enabled=saveState !is SaveState.Saving,modifier=Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=background),border=BorderStroke(if(profile.theme==mode)2.dp else 1.dp,if(profile.theme==mode)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text(label,color=foreground,style=MaterialTheme.typography.titleMedium)
                            Surface(shape=MaterialTheme.shapes.medium,color=Color(0xFF7047E8)){Box(Modifier.fillMaxWidth().height(54.dp),contentAlignment=Alignment.Center){Text("✦",color=Color.White,style=MaterialTheme.typography.headlineSmall)}}
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){repeat(3){Surface(Modifier.weight(1f).height(26.dp),shape=MaterialTheme.shapes.small,color=foreground.copy(alpha=.12f)){}}}
                            RadioButton(selected=profile.theme==mode,onClick={onTheme(mode)},enabled=saveState !is SaveState.Saving)
                        }
                    }
                }
            }
            OutlinedButton(onClick={onTheme(ThemeMode.SYSTEM)},enabled=saveState !is SaveState.Saving,modifier=Modifier.fillMaxWidth()){Text(if(profile.theme==ThemeMode.SYSTEM)"✓ Según el dispositivo" else "Según el dispositivo")}
            if(saveState is SaveState.Saving)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(saveState is SaveState.Failed)Text(saveState.message,color=MaterialTheme.colorScheme.error)
        }
    }
}
