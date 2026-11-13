package pe.studyplanner.aipet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.studyplanner.aipet.R
import pe.studyplanner.aipet.data.tutor.TutorHistory
import pe.studyplanner.aipet.data.tutor.TutorHistoryState
import pe.studyplanner.aipet.domain.profile.AvatarStyle
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.domain.tutor.MasteryStatus
import pe.studyplanner.aipet.domain.tutor.Topic
import pe.studyplanner.aipet.domain.tutor.TutorContent
import pe.studyplanner.aipet.ui.components.AppSection
import pe.studyplanner.aipet.ui.components.PetAvatar
import pe.studyplanner.aipet.ui.components.ProfileAvatarButton
import pe.studyplanner.aipet.ui.components.SectionNavigation
import pe.studyplanner.aipet.ui.theme.PetDesign
import pe.studyplanner.aipet.ui.tutor.MasteryBadge

/**
 * Entrada con los colores del logo: la mascota 3D saluda y hay tres caminos. «Crear cuenta» y «Ya tengo cuenta»
 * guardan los datos en la nube; «Seguir sin cuenta» deja todo solo en el teléfono (se puede crear la cuenta después en
 * Perfil). Sin servidor configurado queda solo «Empezar».
 */
@Composable
fun WelcomeScreen(onStart: () -> Unit, onCustomize: () -> Unit = onStart, hasAgenda: Boolean = false, hasAlerts:Boolean = false, hasAgent:Boolean = false, hasVoice:Boolean=false,
    onAccount: ((pe.studyplanner.aipet.ui.account.AccountMode) -> Unit)? = null) {
    var privacy by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(PetDesign.BrandBlue, PetDesign.BrandIndigo, Color(0xFF3A2C9E))))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=16.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(8.dp))
            Text("StudyPlanner AI Pet",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold,color=Color.White,textAlign=TextAlign.Center,
                modifier=Modifier.semantics{heading()})
            Text("Aprende y organiza tu día",style=MaterialTheme.typography.titleMedium,color=Color.White.copy(alpha=.9f),textAlign=TextAlign.Center)
            Box(Modifier.size(280.dp),contentAlignment=Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0x55FFFFFF),Color(0x00FFFFFF))),size.minDimension*0.48f)
                }
                PetAvatar(AvatarStyle.FOX,Modifier.fillMaxSize().testTag("welcome_pet"),mood="wave")
            }
            if (onAccount != null) {
                WelcomeButton("Crear cuenta", filled = true, tag = "welcome_register") { onAccount(pe.studyplanner.aipet.ui.account.AccountMode.REGISTER) }
                WelcomeButton("Ya tengo cuenta", filled = false, tag = "welcome_signin") { onAccount(pe.studyplanner.aipet.ui.account.AccountMode.SIGN_IN) }
                TextButton(onClick=onStart,modifier=Modifier.heightIn(min=48.dp).testTag("welcome_start")) {
                    Text("Seguir sin cuenta",color=Color.White,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                }
            } else {
                WelcomeButton("Empezar", filled = true, tag = "welcome_start", onClick = onStart)
                TextButton(onClick=onCustomize,modifier=Modifier.heightIn(min=48.dp)){Text("Elegir mascota y nombre",color=Color.White)}
            }
            TextButton(onClick={privacy=true},modifier=Modifier.heightIn(min=48.dp)){Text("Privacidad y datos",color=Color.White.copy(alpha=.85f))}
        }
    }
    if (privacy) AlertDialog(
        onDismissRequest = { privacy = false },
        title = { Text("Tú tienes el control") },
        text = {
            Text(if(hasVoice) "Tu perfil y agenda se guardan en este dispositivo. Si creas una cuenta, también se guarda una copia en la nube, que solo se abre con tu contraseña. Tu nombre es opcional. El micrófono solo se enciende cuando tocas «Hablar». El reconocimiento de voz de Android puede usar Internet; StudyPlanner no guarda audio. La IA en la nube está apagada hasta que la actives y solo recibe tu pregunta." else if (hasAgenda) "Tu perfil y agenda se guardan únicamente en este dispositivo. Tu nombre es opcional. No se usa el micrófono ni se envían datos a servicios de IA." else "Tu perfil se guarda únicamente en este dispositivo. Tu nombre es opcional.", modifier = Modifier.verticalScroll(rememberScrollState()))
        },
        confirmButton = { TextButton(onClick = { privacy = false }) { Text("Entendido") } }
    )
}

/**
 * Pestaña «Estudiar»: saludo, «Continuar» (o «Elegir tema» la primera vez), buscador, temas con su estado y accesos con
 * nombre a Progreso, Exámenes y Modo estudio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    profile: UserProfile,
    saveState: SaveState,
    onSettings: () -> Unit,
    onNavigate: (AppSection) -> Unit,
    history: TutorHistory? = null,
    onOpenTopic: (String) -> Unit = {},
    hasAgenda: Boolean = false,
    onFeature: (String)->Unit = {},
) {
    val data = history?.state?.collectAsStateWithLifecycle()?.value ?: TutorHistoryState(loaded = true)
    val catalog = TutorContent.catalog
    val mastered = catalog.topics.count { data.masteryOf(it.id) == MasteryStatus.DOMINIO_SIN_AYUDA }
    val lastTopic = data.attempts.maxByOrNull { it.timestampMillis }?.topicId?.let { id -> catalog.topics.firstOrNull { it.id == id } }
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val caminoContext = androidx.compose.ui.platform.LocalContext.current
    val camino = remember { pe.studyplanner.aipet.data.tutor.CaminoStore.summary(caminoContext) }
    Scaffold(topBar={
        TopAppBar(title={Text("Estudiar",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold)},
            actions={ProfileAvatarButton(profile,onSettings)},
            colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))
    },bottomBar={SectionNavigation(AppSection.HOME,onNavigate)}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal=16.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            CaminoCard(camino) { onFeature("camino") }
            ContinueCard(lastTopic, lastTopic?.let { data.masteryOf(it.id) }, onContinue={ lastTopic?.let { onOpenTopic(it.id) } },
                onChoose={onNavigate(AppSection.PRACTICE)})
            OutlinedTextField(query,{query=it.take(60)},Modifier.fillMaxWidth().testTag("study_search"),
                placeholder={Text("Buscar tema")},singleLine=true,shape=RoundedCornerShape(24.dp),
                leadingIcon={Icon(Icons.Default.Search,null)},
                trailingIcon={if(query.isNotEmpty())IconButton(onClick={query=""}){Icon(Icons.Default.Clear,"Borrar búsqueda")}},
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={focus.clearFocus()}))
            val shown = if (query.isBlank()) suggestedTopics(catalog.topics, data) else catalog.topics.filter { plain(it.title).contains(plain(query.trim())) }.take(8)
            SectionTitle(if(query.isBlank()) "Temas" else "Resultados", if(query.isBlank()) "Ver los ${catalog.topics.size}" else null) { onNavigate(AppSection.PRACTICE) }
            if (shown.isEmpty()) Text("No encontré temas con «${query.trim()}».",style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=4.dp))
            else RowGroup {
                shown.forEachIndexed { index, topic ->
                    if (index > 0) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    TopicRow(topic, data.masteryOf(topic.id)) { onOpenTopic(topic.id) }
                }
            }
            if(data.error.isNotBlank()) Text(data.error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            SectionTitle("Más para estudiar", null) {}
            RowGroup {
                if (hasAgenda) {
                    NavRow(R.drawable.ic_progress, PetDesign.Turquoise, "Progreso", "Comprobados sin ayuda: $mastered de ${catalog.topics.size}") { onNavigate(AppSection.PROGRESS) }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    NavRow(R.drawable.ic_exam, PetDesign.Pink, "Exámenes", "Fechas y temas de tus evaluaciones") { onFeature("exams") }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                }
                NavRow(R.drawable.ic_timer, PetDesign.Orange, "Modo estudio", "Temporizador de concentración") { onFeature("study") }
            }
            if(saveState is SaveState.Failed)Text(saveState.message,color=MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Botón de la entrada: blanco lleno (principal) o con borde blanco. */
@Composable
private fun WelcomeButton(text: String, filled: Boolean, tag: String, onClick: () -> Unit) {
    if (filled) Button(onClick=onClick,shape=androidx.compose.foundation.shape.CircleShape,colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=PetDesign.BrandInk),
        modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag(tag)) { Text(text,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold) }
    else OutlinedButton(onClick=onClick,shape=androidx.compose.foundation.shape.CircleShape,border=BorderStroke(2.dp,Color.White),
        colors=ButtonDefaults.outlinedButtonColors(contentColor=Color.White),
        modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag(tag)) { Text(text,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold) }
}

/** Primero lo que está en curso (lo más reciente arriba), luego lo nuevo en el orden del temario. */
private fun suggestedTopics(topics: List<Topic>, data: TutorHistoryState): List<Topic> {
    val recent = data.attempts.sortedByDescending { it.timestampMillis }.map { it.topicId }.distinct()
    val inProgress = recent.mapNotNull { id -> topics.firstOrNull { it.id == id } }
        .filter { data.masteryOf(it.id) != MasteryStatus.DOMINIO_SIN_AYUDA }
    val fresh = topics.filter { data.masteryOf(it.id) == MasteryStatus.NO_INICIADO }
    return (inProgress + fresh + topics).distinctBy { it.id }.take(3)
}

private fun plain(text: String) = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

@Composable
private fun ContinueCard(topic: Topic?, status: MasteryStatus?, onContinue: () -> Unit, onChoose: () -> Unit) {
    // Una fila destacada, no una portada: qué sigue y un toque para seguir.
    Surface(onClick=if(topic!=null) onContinue else onChoose, shape=RoundedCornerShape(20.dp),
        color=MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier=Modifier.fillMaxWidth().heightIn(min=72.dp).testTag("study_continue")) {
        Row(Modifier.padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primary) {
                Icon(painterResource(R.drawable.ic_study),null,Modifier.padding(9.dp).size(24.dp),tint=Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(if(topic!=null) "Continuar" else "Primer paso",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                Text(topic?.title ?: "Elige un tema para empezar",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
                if(topic!=null && status!=null) Text(status.label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape=RoundedCornerShape(50),color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("study_continue_button")) {
                Text(if(topic!=null) "Seguir" else "Elegir",Modifier.padding(horizontal=14.dp,vertical=8.dp),
                    style=MaterialTheme.typography.labelLarge,color=Color.White)
            }
        }
    }
}

/**
 * Entrada al «Camino UNAMBA»: sin grupo elegido invita a elegirlo (A Ingenierías, B Biomédicas, C Sociales); después
 * muestra el grupo, la etapa que sigue y el avance.
 */
@Composable
private fun CaminoCard(summary: pe.studyplanner.aipet.data.tutor.CaminoSummary?, onOpen: () -> Unit) {
    Surface(onClick=onOpen, shape=RoundedCornerShape(20.dp), color=Color.Transparent,
        modifier=Modifier.fillMaxWidth().heightIn(min=88.dp).testTag("study_camino")) {
        Box(Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF2A62E8), PetDesign.BrandIndigo)))) {
            Row(Modifier.padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(if(summary==null) "Postulas a la UNAMBA" else "Grupo ${summary.group} · ${summary.groupName}",
                        style=MaterialTheme.typography.labelLarge,color=Color.White)
                    Text(when {
                            summary==null -> "Elige tu grupo y arma tu camino"
                            summary.next.isNotBlank() -> summary.next
                            else -> "Mi camino"
                        },style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=Color.White,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(when {
                            summary==null -> "A\u00A0Ingenierías · B\u00A0Biomédicas · C\u00A0Sociales"
                            !summary.diagnosed -> "Falta tu diagnóstico"
                            else -> "${summary.done} de ${summary.total} temas dominados"
                        },style=MaterialTheme.typography.bodySmall,color=Color.White)
                }
                Surface(shape=RoundedCornerShape(50),color=Color.White) {
                    Text(if(summary==null) "Empezar" else "Seguir",Modifier.padding(horizontal=14.dp,vertical=8.dp),
                        style=MaterialTheme.typography.labelLarge,color=PetDesign.BrandInk)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start=4.dp,top=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Text(title.uppercase(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,
            modifier=Modifier.padding(start=12.dp).semantics{heading()})
        if(action!=null) TextButton(onClick=onAction,modifier=Modifier.heightIn(min=48.dp)){Text(action)}
    }
}

@Composable
internal fun RowGroup(content: @Composable ColumnScope.() -> Unit) {
    // Lista agrupada al estilo iOS: bloque blanco sobre el fondo gris, sin borde.
    Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerLowest,modifier=Modifier.fillMaxWidth()) {
        Column(content=content)
    }
}

@Composable
private fun TopicRow(topic: Topic, status: MasteryStatus, onClick: () -> Unit) {
    Surface(onClick=onClick,color=Color.Transparent,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)
        .semantics{contentDescription="${topic.title}. ${status.label}."}.testTag("study_topic_${topic.id}")) {
        Row(Modifier.padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(topic.title,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
            MasteryBadge(status, compact = true)
        }
    }
}

@Composable
internal fun NavRow(icon: Int, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick=onClick,color=Color.Transparent,modifier=Modifier.fillMaxWidth().heightIn(min=64.dp).testTag("row_$title")) {
        Row(Modifier.padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Surface(shape=RoundedCornerShape(12.dp),color=tint.copy(alpha=.14f)) {
                Icon(painterResource(icon),null,Modifier.padding(8.dp).size(22.dp),tint=tint)
            }
            Column(Modifier.weight(1f)) {
                Text(title,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
