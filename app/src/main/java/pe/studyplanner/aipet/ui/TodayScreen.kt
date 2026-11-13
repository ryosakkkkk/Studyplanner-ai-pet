package pe.studyplanner.aipet.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import pe.studyplanner.aipet.R
import pe.studyplanner.aipet.data.tutor.TutorHistory
import pe.studyplanner.aipet.data.tutor.TutorHistoryState
import pe.studyplanner.aipet.domain.academic.AcademicRepository
import pe.studyplanner.aipet.domain.academic.AcademicSnapshot
import pe.studyplanner.aipet.domain.academic.ActivityStatus
import pe.studyplanner.aipet.domain.academic.DatasetScope
import pe.studyplanner.aipet.domain.calendar.CalendarEngine
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.domain.today.TodaySummary
import pe.studyplanner.aipet.domain.tutor.TutorContent
import pe.studyplanner.aipet.ui.components.AppSection
import pe.studyplanner.aipet.ui.components.PetAvatar
import pe.studyplanner.aipet.ui.components.ProfileAvatarButton
import pe.studyplanner.aipet.ui.components.SectionNavigation
import pe.studyplanner.aipet.ui.components.glass
import pe.studyplanner.aipet.ui.components.glassBackdrop
import pe.studyplanner.aipet.ui.components.isDarkTheme
import pe.studyplanner.aipet.ui.theme.PetDesign

/**
 * Pestaña «Hoy» (versión 0.20), con los colores del logo. Arriba, el saludo con la racha y la meta. En el centro, la
 * mascota 3D sobre un escenario de color: se toca y se ríe. Una frase corta debajo. Abajo, tres misiones grandes de
 * color (Practicar, Comprobar, Repasar) con su avance y el botón principal «Hablar». Al cumplir la meta, confeti.
 */
@Composable
fun TodayScreen(
    profile: UserProfile,
    history: TutorHistory?,
    academic: AcademicRepository?,
    onNavigate: (AppSection) -> Unit,
    onProfile: () -> Unit,
    onTalk: () -> Unit,
    onPractice: (topicId: String?) -> Unit,
    onCheck: (topicId: String?) -> Unit,
    onReview: () -> Unit,
) {
    LaunchedEffect(history) { history?.start() }
    val data = history?.state?.collectAsStateWithLifecycle()?.value ?: TutorHistoryState(loaded = true)
    val snapshotFlow = remember(academic) { academic?.observe(DatasetScope.PERSONAL) ?: emptyFlow() }
    val snapshot by snapshotFlow.collectAsStateWithLifecycle(initialValue = null as AcademicSnapshot?)
    val zone = remember(profile.zoneId) { runCatching { ZoneId.of(profile.zoneId) }.getOrDefault(ZoneId.systemDefault()) }
    val today = LocalDate.now(zone)
    val attempts = data.attempts
    // Lo respondido en el «Camino UNAMBA» cuenta para la racha y la meta del día (no para elegir tema ni repaso).
    val caminoContext = androidx.compose.ui.platform.LocalContext.current
    val caminoDays = remember { pe.studyplanner.aipet.data.tutor.CaminoStore.days(caminoContext) }
    val counted = remember(attempts, caminoDays, zone) { attempts + pe.studyplanner.aipet.data.tutor.CaminoDays.asAttempts(caminoDays, zone) }
    val streak = TodaySummary.streakDays(counted, zone, today)
    val goal = TodaySummary.goalFraction(counted, zone, today)
    val practiceTopic = TodaySummary.topicToPractice(attempts)
    val checkTopic = TodaySummary.topicToCheck(attempts)
    val pending = TodaySummary.pendingReview(attempts, zone, today, snapshot?.activities?.mapTo(HashSet()) { it.id }.orEmpty())
    val nextToday = remember(snapshot, profile.zoneId) {
        snapshot?.let { s ->
            runCatching { CalendarEngine.next(s, Instant.now(), profile.zoneId, limit = 3) }.getOrNull()
                ?.firstOrNull { it.date == today && it.start != null && it.status != ActivityStatus.COMPLETED }
        }
    }
    val title = { id: String? -> id?.let { TutorContent.catalog.topic(it)?.title } }
    val line = when {
        goal >= 1f -> "¡Meta cumplida!"
        pending != null -> "Toca repasar ${title(pending.topicId) ?: "un tema"}."
        nextToday?.start != null -> "«${nextToday.title}» a las ${nextToday.start.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))}."
        practiceTopic != null -> "¿Seguimos con ${title(practiceTopic) ?: "tu tema"}?"
        attempts.isEmpty() -> "Soy ${profile.petName.ifBlank { "tu mascota" }}. ¿Empezamos?"
        else -> "¿Qué estudiamos hoy?"
    }
    // Saluda al entrar y luego queda tranquila; si ya cumplió la meta, celebra una vez.
    var mood by rememberSaveable { mutableStateOf("wave") }
    var pokeLine by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        delay(2500)
        mood = if (goal >= 1f) "celebrate" else "idle"
        delay(2500)
        mood = "idle"
    }
    LaunchedEffect(pokeLine) { if (pokeLine != null) { delay(2200); pokeLine = null } }
    val answered = TodaySummary.answeredToday(counted, zone, today)
    val checked = TodaySummary.checkedToday(attempts, zone, today)
    val practiceDone = answered >= TodaySummary.DAILY_GOAL
    val reviewDone = pending == null && attempts.isNotEmpty()

    Box(Modifier.fillMaxSize().background(glassBackdrop())) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = { SectionNavigation(AppSection.TODAY, onNavigate) },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Cabecera: saludo grande, racha (llama) y meta (anillo), y el avatar que abre Perfil.
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (profile.studentName.isBlank()) "Hola" else "Hola, ${profile.studentName.trim()}",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).semantics { heading() }.testTag("today_greeting"),
                    )
                    StreakChip(streak, goal, Modifier.testTag("today_streak"))
                    ProfileAvatarButton(profile, onProfile)
                }
                // Escenario de la mascota: círculo de color del logo y su sombra en el piso.
                Box(Modifier.weight(1f).fillMaxWidth().heightIn(min = 140.dp), contentAlignment = Alignment.Center) {
                    pe.studyplanner.aipet.ui.components.PetStage(Modifier.fillMaxSize())
                    if (profile.petVisible) {
                        PetAvatar(profile.avatar, Modifier.fillMaxSize().testTag("today_pet"), mood = mood,
                            onPart = { part -> pokeLine = pe.studyplanner.aipet.domain.pet.PetPokeLines.forPart(part) })
                    }
                    if (goal >= 1f) Confetti(Modifier.fillMaxSize())
                }
                // Una frase corta de la mascota (sin láminas al lado: la que reacciona es la mascota 3D).
                Text(
                    pokeLine ?: line,
                    Modifier.padding(top = 2.dp).glass(RoundedCornerShape(50), elevation = 3.dp).testTag("today_bubble")
                        .padding(horizontal = 18.dp, vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                // Misiones de hoy: tres fichas grandes de color, una palabra y su avance.
                Row(Modifier.fillMaxWidth().padding(top = 14.dp).testTag("today_missions"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuestTile(R.drawable.ic_book, PetDesign.QuestBlue, "Practicar", "$answered/${TodaySummary.DAILY_GOAL}",
                        "Practica con pistas. $answered de ${TodaySummary.DAILY_GOAL} respuestas hoy.",
                        done = practiceDone, progress = goal, tag = "today_practice", modifier = Modifier.weight(1f)) { onPractice(practiceTopic) }
                    QuestTile(R.drawable.ic_check, PetDesign.QuestGreen, "Comprobar", if (checked) "Hecho" else title(checkTopic)?.let { "1 tema" } ?: "Sin ayuda",
                        "Comprueba sin ayuda. " + when { checked -> "Hecho hoy"; checkTopic != null -> title(checkTopic) ?: "Tema practicado"; else -> "Después de practicar un tema" },
                        done = checked, progress = if (checked) 1f else 0f, tag = "today_check", modifier = Modifier.weight(1f)) { onCheck(checkTopic) }
                    QuestTile(R.drawable.ic_review, PetDesign.QuestOrange, "Repasar",
                        when { pending != null -> reviewLabel(pending.due, today).replaceFirstChar { it.uppercase() }; reviewDone -> "Al día"; else -> "Luego" },
                        "Repaso. " + when {
                            pending != null -> "${title(pending.topicId) ?: "Tema"}, ${reviewLabel(pending.due, today)}. Toca para agendar"
                            reviewDone -> "Al día, no hay repasos pendientes"
                            else -> "Aparece después de comprobar un tema"
                        },
                        done = reviewDone, progress = if (reviewDone) 1f else 0f, tag = "today_review", modifier = Modifier.weight(1f), onClick = onReview)
                }
                TalkButton(onTalk, Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp).testTag("today_talk"))
            }
        }
    }
}

private fun reviewLabel(due: LocalDate?, today: LocalDate): String = when {
    due == null -> "ver agenda"
    due == today -> "hoy"
    due == today.plusDays(1) -> "mañana"
    else -> "en ${java.time.temporal.ChronoUnit.DAYS.between(today, due)} días"
}

/** Racha (llama naranja con los días) con el anillo de la meta del día alrededor de la llama. */
@Composable
private fun StreakChip(streak: Int, goal: Float, modifier: Modifier) {
    val shown by animateFloatAsState(goal, tween(900), label = "meta")
    val track = if (isDarkTheme()) Color(0x33FFFFFF) else Color(0x1F3A5BF0)
    Row(
        modifier.glass(CircleShape, elevation = 2.dp).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Racha de $streak días. Meta de hoy ${(goal * 100).toInt()} por ciento." },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 3.5.dp.toPx()
                val tl = Offset(w / 2, w / 2); val sz = Size(size.width - w, size.height - w)
                drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(w))
                drawArc(Brush.sweepGradient(listOf(PetDesign.BrandBlue, PetDesign.BrandIndigo, PetDesign.BrandBlue)), -90f, 360f * shown, false, tl, sz,
                    style = Stroke(w, cap = StrokeCap.Round))
            }
            Box(Modifier.size(24.dp).clip(CircleShape).background(Brush.verticalGradient(PetDesign.StreakGradient)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_flame), null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Text("$streak", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
            color = if (isDarkTheme()) PetDesign.Amber else PetDesign.AmberText)
    }
}

/** Ficha de misión: color propio, ícono blanco, una palabra y el avance; si está hecha, una marca verde. */
@Composable
private fun QuestTile(icon: Int, colors: List<Color>, label: String, counter: String, description: String, done: Boolean, progress: Float,
    tag: String, modifier: Modifier, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "toque")
    Surface(
        onClick = onClick, interactionSource = press, shape = RoundedCornerShape(24.dp), color = Color.Transparent,
        modifier = modifier.scale(scale).heightIn(min = 118.dp).shadow(8.dp, RoundedCornerShape(24.dp), spotColor = colors.last())
            .testTag(tag).semantics { contentDescription = description + if (done) ". Hecho." else ""; role = Role.Button },
    ) {
        Box(Modifier.background(Brush.linearGradient(colors)).padding(horizontal = 10.dp, vertical = 12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.height(2.dp))
                // La palabra se achica sola si la ficha es angosta (teléfonos chicos o letra grande).
                androidx.compose.foundation.text.BasicText(label, maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold),
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 17.sp, stepSize = 0.5.sp))
                Text(counter, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            // Avance arriba a la derecha.
            Box(Modifier.align(Alignment.TopEnd).size(26.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 3.dp.toPx()
                    if (done) drawCircle(Color.White)
                    else {
                        drawCircle(Color(0x55FFFFFF), style = Stroke(w))
                        if (progress > 0f) drawArc(Color.White, -90f, 360f * progress, false, style = Stroke(w, cap = StrokeCap.Round))
                    }
                }
                if (done) Icon(painterResource(R.drawable.ic_tick), null, tint = colors.last(), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Botón principal: píldora grande con el degradado del logo y un brillo que late suave para invitar a hablar. */
@Composable
private fun TalkButton(onClick: () -> Unit, modifier: Modifier) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "toque")
    val pulse by rememberInfiniteTransition(label = "latido").animateFloat(0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "brillo")
    Surface(
        onClick = onClick, interactionSource = press, shape = CircleShape, color = Color.Transparent,
        modifier = modifier.scale(scale).height(62.dp).shadow(10.dp, CircleShape, spotColor = PetDesign.BrandIndigo)
            .semantics { contentDescription = "Hablar con la mascota" },
    ) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(PetDesign.BrandGradient)), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).drawBehind {
                    // onda que sale del micrófono
                    drawCircle(Color.White.copy(alpha = 0.35f * (1f - pulse)), radius = size.minDimension / 2 * (1f + 0.45f * pulse))
                }.clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_mic), null, tint = PetDesign.BrandInk, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Hablar", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** Confeti de colores que cae una vez al cumplir la meta. */
@Composable
private fun Confetti(modifier: Modifier) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(600); t.animateTo(1f, tween(2600, easing = LinearEasing)) }
    if (t.value <= 0f || t.value >= 1f) return
    val colors = listOf(PetDesign.BrandBlue, PetDesign.BrandIndigo, PetDesign.BrandOrange, Color(0xFF34C759), Color(0xFFFF375F), Color(0xFFFFD60A))
    val pieces = remember { List(46) { i -> floatArrayOf(((i * 37) % 100) / 100f, ((i * 53) % 100) / 100f, ((i * 71) % 100) / 100f) } }
    Canvas(modifier) {
        val p = t.value
        pieces.forEachIndexed { i, (x0, d, s) ->
            val y = (-0.1f + (p * (1.1f + d * 0.4f)) - d * 0.25f) * size.height
            if (y < -20f || y > size.height) return@forEachIndexed
            val x = (x0 + 0.04f * sin((p * 6f + d * 6f) * PI.toFloat())) * size.width
            rotate((p * 720f * (s - 0.5f)), Offset(x, y)) {
                drawRect(colors[i % colors.size].copy(alpha = (1f - p).coerceIn(0f, 1f) * 0.95f + 0.05f),
                    topLeft = Offset(x - 5f, y - 3f), size = Size(10f + s * 6f, 6f))
            }
        }
    }
}
