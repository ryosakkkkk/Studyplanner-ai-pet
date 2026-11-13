package pe.studyplanner.aipet.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.contentDescription
import pe.studyplanner.aipet.ui.components.PetSticker
import pe.studyplanner.aipet.ui.theme.PetDesign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.ui.components.AppSection
import pe.studyplanner.aipet.ui.components.PetStill
import pe.studyplanner.aipet.ui.components.SectionNavigation

/** Perfil y ajustes, agrupados: tú, tu mascota y tus datos. Se abre desde el avatar de las tres pestañas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileHubScreen(
    profile:UserProfile,
    onBack:()->Unit,
    onEdit:()->Unit,
    onPersonalize:()->Unit,
    onAppearance:()->Unit,
    onPrivacy:()->Unit = {},
    onNavigate:((AppSection)->Unit)? = null,
    onAiSettings:(()->Unit)? = null,
    onPhysicalPet:(()->Unit)? = null,
    onAccount:(()->Unit)? = null,
) {
    var privacy by remember {mutableStateOf(false)}
    val context = LocalContext.current
    val prefs = remember { context.applicationContext.getSharedPreferences("talk_v1", Context.MODE_PRIVATE) }
    var voiceOn by remember { mutableStateOf(!prefs.getBoolean("muted", false)) }
    val app = context.applicationContext as? pe.studyplanner.aipet.StudyPlannerApplication
    val ai = app?.aiConnection?.state?.collectAsStateWithLifecycle()?.value
    val aiStatus = when {
        ai == null -> "No disponible en esta versión"
        ai.enabled && ai.hasToken && ai.endpoint.isNotBlank() -> "Activada"
        ai.endpoint.isBlank() || !ai.hasToken -> "Sin configurar"
        else -> "Desactivada"
    }
    val aiBuiltIn = ai?.builtIn == true
    val account = pe.studyplanner.aipet.ui.account.rememberCloudAccount()
    val accountState = account?.state?.collectAsStateWithLifecycle()?.value
    Scaffold(topBar={TopAppBar(title={Text("Perfil",style=MaterialTheme.typography.titleLarge)},navigationIcon={
        IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Volver")}
    })},bottomBar={if(onNavigate!=null)SectionNavigation(AppSection.SETTINGS,onNavigate)}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=8.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp)) {
            // Cabecera de color con la mascota elegida.
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(PetDesign.BrandGradient)).padding(16.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Surface(shape=CircleShape,color=Color.White.copy(alpha=.25f),modifier=Modifier.size(76.dp)) {
                    Box(contentAlignment=Alignment.Center){PetStill(profile.avatar,Modifier.size(68.dp),contentDescription=null)}
                }
                Column {
                    Text(profile.studentName.ifBlank{"Mi espacio"},style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold,color=Color.White)
                    Text("Con ${profile.petName}",style=MaterialTheme.typography.bodyMedium,color=Color.White.copy(alpha=.9f))
                }
            }
            Group("Tú") {
                if(onAccount!=null && account!=null) {
                    ProfileMenuRow("Cuenta","nube",profile,if(accountState?.signedIn==true) accountState.email else "Sin cuenta",onAccount)
                    Divider()
                }
                ProfileMenuRow("Información","lista",profile,"",onEdit)
                Divider()
                ProfileMenuRow("Apariencia","ajustes",profile,"",onAppearance)
            }
            Group("Tu mascota") {
                ProfileMenuRow("Mascota","feliz",profile,profile.petName,onPersonalize)
                Divider()
                Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    StickerBadge(profile,"musica")
                    Text("Voz",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    Switch(checked=voiceOn,onCheckedChange={voiceOn=it;prefs.edit().putBoolean("muted",!it).apply()},
                        modifier=Modifier.testTag("profile_voice").semantics{contentDescription=if(voiceOn) "${profile.petName} lee sus respuestas" else "Respuestas solo en texto"})
                }
                if(onPhysicalPet!=null) {
                    Divider()
                    ProfileMenuRow("Dispositivo","holograma",profile,"",onPhysicalPet)
                }
            }
            Group("Tus datos") {
                ProfileMenuRow("Privacidad","seguridad",profile,"",{privacy=true;onPrivacy()})
                // Con la IA incluida en la app no hay nada que configurar: la fila solo aparece en versiones sin servidor.
                if(onAiSettings!=null && !aiBuiltIn) {
                    Divider()
                    ProfileMenuRow("IA externa","idea",profile,aiStatus,onAiSettings)
                }
            }
            Text(if(accountState?.signedIn==true) "Copia en la nube activada · ${profile.zoneId}" else "Tus datos se guardan en este teléfono · ${profile.zoneId}",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=4.dp,vertical=4.dp))
        }
    }
    if(privacy)AlertDialog(onDismissRequest={privacy=false},title={Text("Tú tienes el control")},text={Text("Tu perfil y agenda se guardan en este dispositivo. El micrófono solo se enciende cuando tocas «Hablar». El servicio de voz del teléfono puede necesitar Internet; StudyPlanner no guarda audio. La mascota flotante requiere un permiso separado y no escucha. Las preguntas que la app no resuelve sola se envían a su servidor para que la IA responda. No se envían la agenda ni el historial.",Modifier.verticalScroll(rememberScrollState()))},confirmButton={TextButton(onClick={privacy=false}){Text("Entendido")}})
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary,
        modifier=Modifier.padding(start=4.dp,top=8.dp).semantics{heading()})
    Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation=1.dp,modifier=Modifier.fillMaxWidth()) { Column(content=content) }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start=72.dp),color=MaterialTheme.colorScheme.outlineVariant)

/** Lámina de la mascota como ícono de la fila (ajustes, nube, privacidad…), sobre un fondo suave. */
@Composable
private fun StickerBadge(profile:UserProfile,key:String) {
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) {
        PetSticker(profile.avatar,key,Modifier.size(46.dp))
    }
}

@Composable
private fun ProfileMenuRow(title:String,sticker:String,profile:UserProfile,value:String,onClick:()->Unit) {
    Surface(onClick=onClick,color=Color.Transparent,modifier=Modifier.fillMaxWidth().heightIn(min=64.dp).testTag("profile_row_$title")
        .semantics{contentDescription=if(value.isBlank()) title else "$title, $value"}) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            StickerBadge(profile,sticker)
            Text(title,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            if(value.isNotBlank()) Text(value,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,
                overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=150.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
