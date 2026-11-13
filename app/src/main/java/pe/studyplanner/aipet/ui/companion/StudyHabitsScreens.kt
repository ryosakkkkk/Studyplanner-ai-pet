package pe.studyplanner.aipet.ui.companion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import pe.studyplanner.aipet.data.companion.*
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.ui.components.PetAvatar
import pe.studyplanner.aipet.ui.theme.PetDesign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(profile:UserProfile,onBack:()->Unit,onAlerts:()->Unit) {
    val store=rememberCompanionStore();val data by store.state.collectAsStateWithLifecycle()
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(true){now=System.currentTimeMillis();store.finishDue(now);delay(500)}}}
    val timer=data.timer;val remaining=timer.remaining(now);val running=timer.deadline!=null
    val total=(if(timer.breakMode)5 else timer.workMinutes)*60_000f
    var reset by remember {mutableStateOf(false)}
    val colors=MaterialTheme.colorScheme
    Scaffold(topBar={TopAppBar(title={Text("Modo estudio")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Volver")}},actions={TextButton(onClick=onAlerts){Text("Avisos")}})}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=!timer.breakMode,onClick={store.setMode(false)},enabled=!running && remaining==total.toLong(),label={Text("Enfoque")})
                FilterChip(selected=timer.breakMode,onClick={store.setMode(true)},enabled=!running && remaining==total.toLong(),label={Text("Descanso")})
            }
            if(profile.petVisible)PetAvatar(profile.avatar,Modifier.size(112.dp),mood=if(timer.breakMode)"rest" else if(running)"studying" else "idle")
            Box(Modifier.size(218.dp),contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                    val stroke=Stroke(12.dp.toPx(),cap=StrokeCap.Round)
                    drawArc(colors.primaryContainer,0f,360f,false,style=stroke)
                    drawArc(Brush.sweepGradient(listOf(PetDesign.Violet,PetDesign.Blue,PetDesign.Pink,PetDesign.Violet)),-90f,360f*(remaining/total).coerceIn(0f,1f),false,style=stroke)
                }
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    val seconds=(remaining+999)/1000
                    Text(String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60),fontSize=44.sp,fontWeight=FontWeight.Bold,color=colors.onSurface)
                    Text(if(timer.breakMode)"Descansa a tu ritmo" else if(running)"Concentración" else "Tu momento de enfoque",style=MaterialTheme.typography.bodySmall)
                }
            }
            if(data.error.isNotBlank())Text(data.error,color=colors.error)
            Button(onClick={store.toggleTimer(System.currentTimeMillis())},enabled=data.error.isBlank(),modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) {Text(if(running)"Pausar" else if(remaining<total.toLong())"Continuar" else "Iniciar",style=MaterialTheme.typography.titleMedium)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                OutlinedButton(onClick={reset=true},enabled=data.error.isBlank() && (running || remaining<total.toLong()),modifier=Modifier.heightIn(min=48.dp)){Text("Terminar")}
                if(!running && !timer.breakMode) listOf(25,45,50).forEach{minutes->FilterChip(selected=timer.workMinutes==minutes,onClick={store.resetTimer(minutes)},enabled=remaining==total.toLong(),label={Text("$minutes min")})}
            }
            Text("El tiempo se conserva si sales de la app.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
        }
    }
    if(reset)AlertDialog(onDismissRequest={reset=false},title={Text("¿Terminar este bloque?")},text={Text("Se descartará el tiempo parcial. Tus sesiones completadas se conservan.")},confirmButton={TextButton(onClick={store.resetTimer(timer.workMinutes);reset=false}){Text("Terminar")}},dismissButton={TextButton(onClick={reset=false}){Text("Seguir")}})
}

@Composable
fun rememberCompanionStore():CompanionStore {val context=LocalContext.current;return remember(context){CompanionStore.get(context)}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(profile:UserProfile,onBack:()->Unit) {
    val store=rememberCompanionStore();val data by store.state.collectAsStateWithLifecycle()
    var adding by remember{mutableStateOf(false)};var title by remember{mutableStateOf("")};var deleting by remember{mutableStateOf<Habit?>(null)}
    var today by remember(profile.zoneId){mutableStateOf(LocalDate.now(ZoneId.of(profile.zoneId)).toString())}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(profile.zoneId,lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(true){today=LocalDate.now(ZoneId.of(profile.zoneId)).toString();delay(30_000)}}}
    val colors=MaterialTheme.colorScheme
    Scaffold(topBar={TopAppBar(title={Text("Hábitos")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Volver")}})},floatingActionButton={ExtendedFloatingActionButton(onClick={adding=true},containerColor=PetDesign.Violet,contentColor=androidx.compose.ui.graphics.Color.White){Text("＋ Agregar hábito")}}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom=80.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("Pequeños pasos, cada día",style=MaterialTheme.typography.headlineSmall)
            Text("${data.habits.count{today in it.dates}} de ${data.habits.size} completados hoy",color=colors.onSurfaceVariant)
            if(data.error.isNotBlank())Text(data.error,color=colors.error)
            if(data.habits.isEmpty())Card(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Empieza con algo pequeño",style=MaterialTheme.typography.titleMedium);Text("Añade un hábito que quieras cuidar. Tus marcas se guardarán por día.")}}
            data.habits.forEachIndexed { index,habit ->
                val accent=listOf(PetDesign.Orange,PetDesign.Turquoise,PetDesign.Pink,PetDesign.Blue)[index%4]
                Card(onClick={store.toggleHabit(habit.id,today)},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=colors.surface),border=androidx.compose.foundation.BorderStroke(1.dp,accent.copy(alpha=.18f))) {
                    Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Surface(shape=RoundedCornerShape(12.dp),color=accent.copy(alpha=.12f)){Text(habit.title.take(1).uppercase(),Modifier.padding(12.dp),color=accent,fontWeight=FontWeight.Bold)}
                        Column(Modifier.weight(1f)){Text(habit.title,style=MaterialTheme.typography.titleMedium);Text("${habit.dates.size} días registrados",style=MaterialTheme.typography.bodySmall)}
                        Checkbox(checked=today in habit.dates,onCheckedChange={store.toggleHabit(habit.id,today)},enabled=data.error.isBlank())
                        TextButton(onClick={deleting=habit},contentPadding=PaddingValues(0.dp)){Text("⋮")}
                    }
                }
            }
        }
    }
    if(adding)AlertDialog(onDismissRequest={adding=false},title={Text("Nuevo hábito")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(title,{if(it.length<=48)title=it},label={Text("¿Qué quieres cuidar?")},singleLine=true)
        Text("Por ejemplo: leer, estudiar o descansar.",style=MaterialTheme.typography.bodySmall)
        if(data.habits.any{it.title.equals(title.trim(),true)})Text("Ese hábito ya está en tu lista.")
    }},confirmButton={TextButton(onClick={store.addHabit(title);title="";adding=false},enabled=title.isNotBlank() && data.error.isBlank() && data.habits.none{it.title.equals(title.trim(),true)}){Text("Agregar")}},dismissButton={TextButton(onClick={adding=false}){Text("Cancelar")}})
    deleting?.let{habit->AlertDialog(onDismissRequest={deleting=null},title={Text("Eliminar «${habit.title}»")},text={Text("Se eliminarán este hábito y sus marcas guardadas.")},confirmButton={TextButton(onClick={store.deleteHabit(habit.id);deleting=null}){Text("Eliminar")}},dismissButton={TextButton(onClick={deleting=null}){Text("Conservar")}})}
}
