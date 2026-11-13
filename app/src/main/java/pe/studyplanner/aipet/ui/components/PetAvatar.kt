package pe.studyplanner.aipet.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pe.studyplanner.aipet.R
import pe.studyplanner.aipet.domain.pet.PetClips
import pe.studyplanner.aipet.domain.pet.PetEmotionBus
import pe.studyplanner.aipet.domain.pet.PetOutfits
import pe.studyplanner.aipet.domain.pet.PetStickers
import pe.studyplanner.aipet.domain.profile.AvatarStyle
import pe.studyplanner.aipet.platform.pet3d.PetStickerImages
import pe.studyplanner.aipet.platform.pet3d.PetStyleStore
import pe.studyplanner.aipet.platform.pet3d.PetWebView

/** Imagen fija del mismo personaje. Sirve de respaldo y de miniatura. */
fun AvatarStyle.stillImage(): Int = when (this) {
    AvatarStyle.FOX -> R.drawable.pet_still_fox
    AvatarStyle.CAT -> R.drawable.pet_still_cat
    AvatarStyle.PANDA -> R.drawable.pet_still_panda
    AvatarStyle.PENGUIN -> R.drawable.pet_still_penguin
    AvatarStyle.OWL -> R.drawable.pet_still_owl
    AvatarStyle.RABBIT -> R.drawable.pet_still_rabbit
}

/**
 * La imagen es un cuadro de la 3D de frente con el mismo encuadre del visor; [zoom] la acerca para miniaturas (1,2 deja
 * a la mascota entera dentro del recuadro).
 */
@Composable
fun PetStill(style: AvatarStyle, modifier: Modifier = Modifier, contentDescription: String? = "Mascota ${style.label}", zoom: Float = 1.2f) {
    Image(painterResource(style.stillImage()), contentDescription,
        modifier.clipToBounds().graphicsLayer { scaleX = zoom; scaleY = zoom }, contentScale = ContentScale.Fit)
}

/** Una expresión o pose de las láminas de la mascota. Mientras se lee, muestra la imagen fija. */
@Composable
fun PetSticker(style: AvatarStyle, key: String, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, style, key) {
        value = withContext(Dispatchers.IO) { PetStickerImages.load(context, style, key) }
    }
    val image = bitmap
    if (image != null) Image(image.asImageBitmap(), contentDescription, modifier, contentScale = ContentScale.Fit)
    else PetStill(style, modifier, contentDescription)
}

/**
 * El visor web carga three.js y el modelo GLB: en equipos lentos tarda. Pasado este tiempo la 3D sigue cargando, pero
 * aparece «Reintentar» por si se quedó trabada.
 */
private const val WEB_SLOW_MS = 25000L

/** Cuánto dura a la vista una reacción (globo con la expresión). */
private const val REACTION_MS = 2800L

/**
 * Mascota animada en 3D (las seis): 17 animaciones, ojos y boca que se mueven, colores de Personalización. Se gira con
 * el dedo (vuelve sola de frente) y cada parte responde distinto al tocarla: cabeza, manos, panza, pies y cola.
 * Mientras la 3D carga se ve la imagen del mismo personaje respirando; la 3D nunca se cambia por la imagen por tardar.
 * Solo si la 3D falla de verdad (dos veces seguidas) queda la imagen con «Reintentar».
 *
 * @param closeUp encuadre de medio cuerpo para huecos pequeños.
 * @param onPart parte tocada: «cabeza», «mano», «panza», «pies», «cola», «baile» (tres toques) o «cuerpo».
 */
@Composable
fun PetAvatar(style: AvatarStyle, modifier: Modifier = Modifier,
    mood: String = "idle", reducedMotion: Boolean = false, closeUp: Boolean = false, onTap: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") showReaction: Boolean = false, holdMood: Boolean = false, onPart: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val model = PetClips.modelName(style)
    LaunchedEffect(style) { PetStyleStore.setCurrentPet(context, style) }
    var attempt by remember { mutableIntStateOf(0) }
    var failures by remember(style) { mutableIntStateOf(0) }
    var ready by remember(style, attempt) { mutableStateOf(model == null) }
    var slow by remember(style, attempt) { mutableStateOf(false) }
    LaunchedEffect(style, attempt) {
        if (model == null) return@LaunchedEffect
        delay(WEB_SLOW_MS)
        if (!ready) slow = true
    }
    // Las emociones globales (recordatorio, celebración) mandan sobre el estado de la pantalla mientras duran.
    val busMood by produceState<String?>(null) {
        while (true) { value = PetEmotionBus.active(SystemClock.uptimeMillis()); delay(300) }
    }
    var tapGreet by remember { mutableIntStateOf(0) }
    var tapAt by remember { mutableStateOf(androidx.compose.ui.geometry.Offset(0.5f, 0.3f)) }
    var viewRef by remember { mutableStateOf<PetWebView?>(null) }
    val effective = busMood ?: mood
    PetStyleStore.init(context)
    val saved by PetStyleStore.colors.collectAsStateWithLifecycle()
    val colorsJson = PetOutfits.jsonFor(style, saved)
    val broken = failures >= 2

    Box(modifier.semantics { contentDescription = "Mascota ${style.label}" }
        .pointerInput(style) {
            detectTapGestures { o ->
                tapAt = androidx.compose.ui.geometry.Offset(o.x / size.width.coerceAtLeast(1), o.y / size.height.coerceAtLeast(1))
                tapGreet++; onTap?.invoke()
            }
        }
        .pointerInput(style) {
            // Girar con el dedo: solo el arrastre de lado, así el desplazamiento vertical de la pantalla sigue igual.
            detectHorizontalDragGestures(
                onDragEnd = { viewRef?.dragEnd() },
                onDragCancel = { viewRef?.dragEnd() },
            ) { change, dx ->
                change.consume()
                viewRef?.drag(dx / size.width.coerceAtLeast(1))
            }
        },
        contentAlignment = Alignment.Center) {
        if (model == null) {
            PetFlat(style, effective, reducedMotion, Modifier.fillMaxSize().padding(6.dp))
        } else {
            if (!ready || broken) PetLoading(style, reducedMotion || broken, Modifier.fillMaxSize())
            // Una sola vista 3D por lugar: al cambiar de mascota se carga la otra en la misma vista (key solo por reintento).
            if (!broken) key(attempt) {
                PetWebAvatar(model, effective, reducedMotion, closeUp, tapGreet, tapAt, colorsJson, Modifier.fillMaxSize(), holdMood,
                    onReady = { ready = true; failures = 0; slow = false },
                    onFailed = { failures++; if (failures < 2) attempt++ },
                    onPart = { part -> onPart?.invoke(part) },
                    onView = { viewRef = it })
            }
            if (broken || (slow && !ready)) {
                Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    OutlinedButton(onClick = { failures = 0; attempt++ }, modifier = Modifier.heightIn(min = 48.dp).testTag("pet_retry")) {
                        Text("Reintentar", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** Mientras carga la 3D: la imagen del mismo personaje, de frente, con una respiración suave (no una imagen quieta). */
@Composable
private fun PetLoading(style: AvatarStyle, still: Boolean, modifier: Modifier) {
    val breath = rememberInfiniteTransition(label = "cargando")
    val t by breath.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "t")
    val k = if (still) 0f else t
    PetStill(style, modifier.graphicsLayer {
        scaleY = 1f + 0.02f * k; scaleX = 1f - 0.008f * k; translationY = -5f * k
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
    }, contentDescription = null, zoom = 1f)
}

/** Conejo: sus láminas con un balanceo suave; cada reacción cambia la imagen por la pose de las láminas. */
@Composable
private fun PetFlat(style: AvatarStyle, mood: String, reducedMotion: Boolean, modifier: Modifier) {
    val key = PetStickers.forMood(mood)
    var shown by remember(style) { mutableStateOf<String?>(null) }
    LaunchedEffect(key) { shown = key; if (key != null && PetClips.forMood(mood).loop.not()) { delay(REACTION_MS); shown = null } }
    val breath = rememberInfiniteTransition(label = "respirar")
    val t by breath.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "t")
    val k = if (reducedMotion) 0f else t
    val talking = PetClips.forMood(mood).talking
    val m = Modifier.graphicsLayer {
        scaleY = 1f + 0.025f * k + if (talking) 0.02f * k else 0f
        scaleX = 1f - 0.01f * k
        translationY = -6f * k
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
    }
    val s = shown
    if (s != null) PetSticker(style, s, modifier.then(m)) else PetStill(style, modifier.then(m), contentDescription = null)
}

@Composable
private fun PetWebAvatar(model: String, mood: String, reducedMotion: Boolean, closeUp: Boolean, greet: Int,
    tapAt: androidx.compose.ui.geometry.Offset, colorsJson: String,
    modifier: Modifier, hold: Boolean, onReady: () -> Unit, onFailed: () -> Unit, onPart: (String) -> Unit,
    onView: (PetWebView?) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latestReady by rememberUpdatedState(onReady)
    val latestFailed by rememberUpdatedState(onFailed)
    val latestPart by rememberUpdatedState(onPart)
    val latestView by rememberUpdatedState(onView)
    val view = remember(closeUp) { PetWebView(context, model, closeUp, colorsJson) }
    DisposableEffect(view, owner) {
        view.onReady = { latestReady() }
        view.onFailed = { latestFailed() }
        view.onPoke = { latestPart(it) }
        latestView(view)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) view.setPaused(false)
            if (event == Lifecycle.Event.ON_PAUSE) view.setPaused(true)
        }
        owner.lifecycle.addObserver(observer)
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.setPaused(true)
        onDispose { owner.lifecycle.removeObserver(observer); latestView(null); view.release() }
    }
    LaunchedEffect(view, greet) { if (greet > 0) view.poke(tapAt.x, tapAt.y) }
    val choice = PetClips.forMood(mood).let { if (reducedMotion && !it.talking) PetClips.forMood("idle") else if (hold) it.copy(loop = true) else it }
    AndroidView(factory = { view }, modifier = modifier, update = { it.setModel(model); it.setColors(colorsJson); it.show(choice) })
}
