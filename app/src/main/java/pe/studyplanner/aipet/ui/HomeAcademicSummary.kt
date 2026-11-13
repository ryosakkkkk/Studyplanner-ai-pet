package pe.studyplanner.aipet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import pe.studyplanner.aipet.ui.theme.PetDesign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.calendar.CalendarEngine
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.ui.components.*

@Composable
private fun personalSnapshot(repository: AcademicRepository): Pair<AcademicSnapshot?,String> {
    var snapshot by remember(repository) { mutableStateOf<AcademicSnapshot?>(null) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(repository) {
        try { repository.observe(DatasetScope.PERSONAL).collect { snapshot=it;error="" } }
        catch(e:CancellationException){throw e}
        catch(e:Exception){error="No pudimos leer tu agenda. Vuelve a abrir esta pantalla para reintentar."}
    }
    return snapshot to error
}

@Composable
private fun TaskProgress(snapshot: AcademicSnapshot) {
    val tasks=snapshot.activities.filter { it.kind==ActivityKind.TASK && it.status!=ActivityStatus.CANCELLED }
    val done=tasks.count { it.status==ActivityStatus.COMPLETED }
    val ratio=if(tasks.isEmpty())0f else done.toFloat()/tasks.size
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.16f)),elevation=CardDefaults.cardElevation(defaultElevation=2.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(52.dp),contentAlignment=Alignment.Center) {
                CircularProgressIndicator(progress={ratio},modifier=Modifier.fillMaxSize(),strokeWidth=6.dp,color=PetDesign.Turquoise,trackColor=MaterialTheme.colorScheme.primaryContainer)
                Text("${(ratio*100).toInt()}%",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
            }
            Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("Progreso de tareas",style=MaterialTheme.typography.titleMedium)
                Text(if(tasks.isEmpty())"Agrega tu primera tarea" else "$done de ${tasks.size} completadas",style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun HomeProgressCard(repository:AcademicRepository) {
    val (snapshot,error)=personalSnapshot(repository)
    snapshot?.let { TaskProgress(it) }
    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
}

@Composable
fun HomeAcademicSummary(repository: AcademicRepository, profile: UserProfile, onOpenAgenda: () -> Unit) {
    val (snapshot,error)=personalSnapshot(repository)
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit){while(true){delay(30_000);now=Instant.now()}}
    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
    val current=snapshot
    if(current==null && error.isBlank())LinearProgressIndicator(Modifier.fillMaxWidth())
    if(current!=null) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("En tu agenda",style=MaterialTheme.typography.titleLarge)
            TextButton(onClick=onOpenAgenda){Text("Ver todo")}
        }
        val upcoming=remember(current,now,profile.zoneId){CalendarEngine.next(current,now,profile.zoneId).take(3)}
        if(upcoming.isEmpty()) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("Espacio para lo que viene",style=MaterialTheme.typography.titleMedium)
                Text("Aún no hay próximos compromisos con fecha. Crea una tarea o revisa tu agenda.")
            }
        }
        upcoming.forEach { entry ->
            Card(onClick=onOpenAgenda,modifier=Modifier.fillMaxWidth(),border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.12f)),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(when(entry.kind){ActivityKind.EXAM->"▣";ActivityKind.TASK->"✓";else->"◇"},color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.headlineSmall)
                    Column(Modifier.weight(1f)) {
                        Text(entry.title,style=MaterialTheme.typography.titleMedium)
                        Text(entry.start?.atZone(ZoneId.of(profile.zoneId))?.format(DateTimeFormatter.ofPattern("dd MMM · HH:mm")) ?: "${entry.date} · sin hora",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(repository:AcademicRepository,profile:UserProfile,onNavigate:(AppSection)->Unit) {
    val (snapshot,error)=personalSnapshot(repository)
    val store=pe.studyplanner.aipet.ui.companion.rememberCompanionStore()
    val companion by store.state.collectAsStateWithLifecycle()
    var achievements by remember {mutableStateOf(false)}
    val colors=MaterialTheme.colorScheme
    Scaffold(topBar={TopAppBar(title={Text("Progreso")},navigationIcon={IconButton(onClick={onNavigate(AppSection.HOME)}){Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,"Volver a Estudiar")}})},bottomBar={SectionNavigation(AppSection.PROGRESS,onNavigate)}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                FilterChip(selected=!achievements,onClick={achievements=false},label={Text("Resumen")})
                FilterChip(selected=achievements,onClick={achievements=true},label={Text("Logros")})
            }
            if(error.isNotBlank())Text(error)
            snapshot?.let { data ->
                val tasks=data.activities.filter{it.kind==ActivityKind.TASK && it.status!=ActivityStatus.CANCELLED}
                val done=tasks.count{it.status==ActivityStatus.COMPLETED}
                val today=java.time.LocalDate.now(ZoneId.of(profile.zoneId)).toString()
                val habits=companion.habits.count{today in it.dates}
                val minutes=companion.blocks.sumOf{it.minutes}
                if(!achievements) {
                    val ratio=if(tasks.isEmpty())0f else done.toFloat()/tasks.size
                    Box(Modifier.size(190.dp),contentAlignment=Alignment.Center) {
                        CircularProgressIndicator(progress={ratio},modifier=Modifier.fillMaxSize(),strokeWidth=12.dp,color=PetDesign.Turquoise,trackColor=colors.primaryContainer)
                        Column(horizontalAlignment=Alignment.CenterHorizontally){Text("${(ratio*100).toInt()}%",style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold);Text("Tareas completadas",style=MaterialTheme.typography.bodySmall)}
                    }
                    Text("A tu ritmo, cada paso cuenta",style=MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        MetricCard("$done","Tareas",Modifier.weight(1f))
                        MetricCard("$minutes","Min enfoque",Modifier.weight(1f))
                        MetricCard("$habits","Hábitos hoy",Modifier.weight(1f))
                    }
                    Text("El enfoque cuenta bloques completos del temporizador; los hábitos corresponden a tus marcas de hoy.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                } else {
                    listOf(Triple("Primer paso","Completa una tarea",done>0),Triple("Tiempo para ti","Completa un bloque de enfoque",companion.blocks.isNotEmpty()),Triple("Un buen comienzo","Crea tu primer hábito",companion.habits.isNotEmpty())).forEach { (name,requirement,unlocked) ->
                        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=if(unlocked)colors.primaryContainer else colors.surfaceContainer)) {
                            Row(Modifier.padding(18.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically){Text(if(unlocked)"★" else "☆",style=MaterialTheme.typography.headlineMedium,color=if(unlocked)PetDesign.Orange else colors.onSurfaceVariant);Column{Text(name,style=MaterialTheme.typography.titleMedium);Text(if(unlocked)"¡Logrado!" else requirement,style=MaterialTheme.typography.bodySmall)}}
                        }
                    }
                }
                if(companion.error.isNotBlank())Text(companion.error,color=colors.error)
            }
        }
    }
}

@Composable
private fun MetricCard(value:String,label:String,modifier:Modifier) {
    Card(modifier,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.14f))) {
        Column(Modifier.fillMaxWidth().padding(vertical=18.dp,horizontal=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text(value,style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
            Text(label,style=MaterialTheme.typography.labelSmall)
        }
    }
}
