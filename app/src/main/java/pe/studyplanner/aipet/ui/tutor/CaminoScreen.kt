package pe.studyplanner.aipet.ui.tutor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference
import pe.studyplanner.aipet.data.tutor.CaminoStore
import pe.studyplanner.aipet.data.tutor.TutorHistory
import pe.studyplanner.aipet.data.tutor.TutorHistoryState
import pe.studyplanner.aipet.domain.json.MiniJson
import pe.studyplanner.aipet.domain.pet.PetEmotionBus
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.domain.tutor.MasteryStatus
import pe.studyplanner.aipet.domain.tutor.Speech
import pe.studyplanner.aipet.domain.tutor.SpokenOption
import pe.studyplanner.aipet.domain.tutor.TutorContent
import pe.studyplanner.aipet.domain.voice.VoicePhase
import pe.studyplanner.aipet.domain.voice.VoiceSession
import pe.studyplanner.aipet.platform.pet3d.PetAssetServer
import pe.studyplanner.aipet.platform.voice.AndroidVoicePlatform
import pe.studyplanner.aipet.ui.components.PetAvatar
import pe.studyplanner.aipet.ui.components.isDarkTheme

/** Dirección local de la página del camino (se sirve desde `assets/web/camino`, sin Internet). */
private const val CAMINO_URL = "${PetAssetServer.BASE}camino/index.html"

/** Preferencias de la voz que comparte con «Hablar»: si la mascota está silenciada y si ya se aceptó el aviso del micrófono. */
private const val TALK_PREFS = "talk_v1"

/**
 * «Camino UNAMBA»: el postulante elige su grupo (A, B o C), responde un diagnóstico corto y recibe un camino ordenado
 * por lo que más pesa en su examen y lo que más le falta. La pantalla es una página local (`assets/web/camino`) porque
 * las explicaciones llevan notación matemática (KaTeX) y gráficas; aquí solo van la barra, la mascota y el puente.
 *
 * Los temas que ya existen en la app se abren con [onOpenTopic]; los temas nuevos se practican dentro de la página con
 * respuesta inmediata: al responder se ve al instante si acertó y por qué, y lo que falló vuelve al final de la
 * lección con datos nuevos. Todo funciona sin Internet. El estado se guarda con [CaminoStore].
 *
 * Voz (sin Internet si el teléfono tiene la voz en español): la mascota lee la pregunta y la explicación con el motor
 * de lectura de Android, y se puede responder diciendo «la B». Lo dictado solo marca la alternativa: el estudiante
 * confirma con «Comprobar».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaminoScreen(
    profile: UserProfile,
    history: TutorHistory?,
    voiceEnabled: Boolean,
    onBack: () -> Unit,
    onOpenTopic: (String) -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val dark = isDarkTheme()
    val zone = remember(profile.zoneId) { runCatching { ZoneId.of(profile.zoneId) }.getOrDefault(ZoneId.systemDefault()) }
    LaunchedEffect(history) { history?.start() }
    val data = history?.state?.collectAsStateWithLifecycle()?.value ?: TutorHistoryState(loaded = true)
    val masteryText = remember(data.attempts.size) { masteryAsJson(data) }
    val mastery = remember { AtomicReference("{}") }
    SideEffect { mastery.set(masteryText) }

    // ---- voz de la mascota: lectura y dictado con los motores del teléfono ----
    val prefs = remember { context.applicationContext.getSharedPreferences(TALK_PREFS, Context.MODE_PRIVATE) }
    val voiceScope = rememberCoroutineScope()
    val voice = remember(voiceEnabled, profile.petVisible) {
        if (voiceEnabled && profile.petVisible) VoiceSession(AndroidVoicePlatform(context.applicationContext), voiceScope) else null
    }
    DisposableEffect(voice) { onDispose { voice?.close() } }
    val voiceUi = voice?.state?.collectAsStateWithLifecycle()?.value
    val voiceNow by rememberUpdatedState(voice)
    var askMic by remember { mutableStateOf(false) }
    var micPending by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("Camino UNAMBA") }
    var fraction by remember { mutableFloatStateOf(-1f) }
    val openNow by rememberUpdatedState(onOpenTopic)
    val backNow by rememberUpdatedState(onBack)
    val zoneNow by rememberUpdatedState(zone)
    val listen = remember { AtomicReference<() -> Unit>({}) }

    val web = remember(dark) {
        createCaminoWebView(context, dark) {
            CaminoBridge(
                context = context.applicationContext,
                mastery = { mastery.get() },
                onHeader = { text, value -> title = text.take(80).ifBlank { "Camino UNAMBA" }; fraction = value },
                onEvent = { type ->
                    val now = SystemClock.uptimeMillis()
                    when (type) {
                        "correcta" -> { PetEmotionBus.emit("correct", 2500L, now); CaminoStore.recordAnswer(context, LocalDate.now(zoneNow)) }
                        "incorrecta" -> { PetEmotionBus.emit("wrong", 2500L, now); CaminoStore.recordAnswer(context, LocalDate.now(zoneNow)) }
                        // «No lo sé»: cuenta como pregunta trabajada, sin reacción de error.
                        "duda" -> { PetEmotionBus.emit("think", 3000L, now); CaminoStore.recordAnswer(context, LocalDate.now(zoneNow)) }
                        // Corrigió una que había fallado: el día ya la contó en su primer intento.
                        "corregida" -> PetEmotionBus.emit("correct", 2500L, now)
                        "racha" -> PetEmotionBus.emit("happy", 2500L, now)
                        "logro" -> PetEmotionBus.emit("celebrate", 3200L, now)
                        else -> Unit
                    }
                },
                onOpenTopic = { id -> if (TutorContent.catalog.topic(id) != null) openNow(id) },
                voiceReady = { voiceNow?.state?.value?.capabilities?.ttsEngineAvailable == true },
                listenReady = { voiceNow?.state?.value?.capabilities?.let { it.speechAvailable || it.onDeviceAvailable } == true },
                muted = { prefs.getBoolean("muted", false) },
                onSpeak = { text ->
                    voiceNow?.let { v ->
                        v.setReadAloudEnabled(true)
                        v.speak(Speech.forTts(text).take(MAX_SPOKEN_CHARS))
                    }
                },
                onStopSpeaking = { voiceNow?.stopSpeaking() },
                onListen = { listen.get().invoke() },
            )
        }
    }

    fun answerToPage(index: Int, heard: String) {
        web.evaluateJavascript("window.caminoOido && caminoOido($index, ${MiniJson.quote(heard)})", null)
    }

    // Lo dictado se convierte en una alternativa («la be» → B) y se marca en la página; no se responde solo.
    LaunchedEffect(voiceUi?.phase) {
        val v = voice ?: return@LaunchedEffect
        when (voiceUi?.phase) {
            VoicePhase.REVIEW -> {
                val heard = v.takeTranscript().orEmpty()
                answerToPage(SpokenOption.parse(heard) ?: -1, heard.take(80))
            }
            VoicePhase.ERROR -> if (micPending) { micPending = false; v.discardTranscript(); answerToPage(-1, "") }
            else -> Unit
        }
        if (voiceUi?.phase == VoicePhase.REVIEW) micPending = false
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val v = voice
        if (granted && v != null) { micPending = true; v.startDictation(true, onDeviceOnly(v)) } else answerToPage(-1, "")
    }
    fun startListening() {
        val v = voice ?: return answerToPage(-1, "")
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            micPending = true
            v.startDictation(true, onDeviceOnly(v))
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    SideEffect {
        listen.set {
            // Primera vez: el mismo aviso corto que en «Hablar» antes de encender el micrófono.
            if (prefs.getBoolean("notice", false)) startListening() else askMic = true
        }
    }

    DisposableEffect(web, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                web.onResume()
                voice?.refresh()
                // Al volver de un tema de la app, la página vuelve a leer el dominio y repinta el camino.
                web.evaluateJavascript("window.caminoVolver && caminoVolver()", null)
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                web.onPause()
                voice?.suspendSession()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            web.removeJavascriptInterface(BRIDGE_NAME)
            web.stopLoading()
            (web.parent as? ViewGroup)?.removeView(web)
            Handler(Looper.getMainLooper()).post { web.destroy() }
        }
    }
    // «Atrás» primero retrocede dentro de la página; cuando ya no hay a dónde, sale de la pantalla.
    val goBack: () -> Unit = {
        voice?.stopSpeaking()
        web.evaluateJavascript("(window.caminoAtras && caminoAtras()) ? 'si' : 'no'") { result ->
            if (result == null || !result.contains("si")) backNow()
        }
    }
    BackHandler(onBack = goBack)

    val mood = when (voiceUi?.phase) {
        VoicePhase.REQUESTING, VoicePhase.LISTENING, VoicePhase.TRANSCRIBING -> "listen"
        VoicePhase.PREPARING_SPEECH, VoicePhase.SPEAKING -> "speak"
        else -> "idle"
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Camino UNAMBA", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (profile.petVisible) PetAvatar(profile.avatar, Modifier.size(84.dp), mood = mood, closeUp = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("camino_title").semantics { heading() },
                    )
                    if (fraction >= 0f) {
                        LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            // Si cambia el tema se crea otra página: la clave hace que la vista se vuelva a montar con ella.
            key(web) { AndroidView(factory = { web }, modifier = Modifier.fillMaxSize().testTag("camino_web")) }
        }
    }

    if (askMic) AlertDialog(
        onDismissRequest = { askMic = false; answerToPage(-1, "") },
        title = { Text("Responder con la voz") },
        text = { Text("Di la letra, por ejemplo «la B». El micrófono se apaga solo y no se guarda audio. Luego tocas «Comprobar».") },
        confirmButton = {
            Button(onClick = { askMic = false; prefs.edit().putBoolean("notice", true).apply(); startListening() }) { Text("Hablar") }
        },
        dismissButton = { TextButton(onClick = { askMic = false; answerToPage(-1, "") }) { Text("Ahora no") } },
    )
}

private const val BRIDGE_NAME = "AndroidCamino"
private const val MAX_SPOKEN_CHARS = 3800

/** Si solo hay reconocimiento en el dispositivo (sin el servicio general), se usa ese: funciona sin Internet. */
private fun onDeviceOnly(voice: VoiceSession): Boolean {
    val caps = voice.state.value.capabilities
    return !caps.speechAvailable && caps.onDeviceAvailable
}

/** Dominio de los temas de la app que ya tienen intentos, como JSON `{"mcd-mcm":"EN_PRACTICA", …}`. */
private fun masteryAsJson(data: TutorHistoryState): String {
    val practiced = data.attempts.mapTo(HashSet()) { it.topicId }
    val map = LinkedHashMap<String, Any?>()
    for (topic in TutorContent.catalog.topics) {
        if (topic.id !in practiced) continue
        val status = data.masteryOf(topic.id)
        if (status != MasteryStatus.NO_INICIADO) map[topic.id] = status.name
    }
    return MiniJson.write(map)
}

/** Crea el puente cuando la vista ya existe. */
private fun interface CaminoBridgeFactory {
    fun create(view: WebView): CaminoBridge
}

@SuppressLint("SetJavaScriptEnabled")
private fun createCaminoWebView(context: Context, dark: Boolean, bridge: CaminoBridgeFactory): WebView {
    val web = WebView(context)
    web.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    with(web.settings) {
        javaScriptEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        domStorageEnabled = false
        setSupportZoom(false)
    }
    web.overScrollMode = View.OVER_SCROLL_NEVER
    web.webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            PetAssetServer.serve(view.context.assets, request.url)

        // La página no navega a ningún sitio externo.
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
    }
    web.addJavascriptInterface(bridge.create(web), BRIDGE_NAME)
    web.loadUrl(CAMINO_URL + if (dark) "?tema=oscuro" else "")
    return web
}

/**
 * Lo que la página del camino puede pedirle a la app. Android llama a estos métodos en un hilo propio del WebView: lo
 * que toca la interfaz o la voz se pasa al hilo principal.
 */
private class CaminoBridge(
    private val context: Context,
    private val mastery: () -> String,
    private val onHeader: (String, Float) -> Unit,
    private val onEvent: (String) -> Unit,
    private val onOpenTopic: (String) -> Unit,
    private val voiceReady: () -> Boolean,
    private val listenReady: () -> Boolean,
    private val muted: () -> Boolean,
    private val onSpeak: (String) -> Unit,
    private val onStopSpeaking: () -> Unit,
    private val onListen: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun cargar(): String = CaminoStore.state(context)

    @JavascriptInterface
    fun guardar(estado: String, resumen: String) = CaminoStore.save(context, estado, resumen)

    @JavascriptInterface
    fun dominio(): String = mastery()

    @JavascriptInterface
    fun cabecera(titulo: String, fraccion: Double) {
        main.post { onHeader(titulo, fraccion.toFloat()) }
    }

    @JavascriptInterface
    fun evento(tipo: String) {
        main.post { onEvent(tipo) }
    }

    @JavascriptInterface
    fun abrirTema(id: String) {
        main.post { onOpenTopic(id) }
    }

    /** Si la mascota puede leer en voz alta (hay motor de lectura en el teléfono). */
    @JavascriptInterface
    fun vozDisponible(): Boolean = voiceReady()

    /** Si se puede responder dictando (hay reconocimiento de voz en el teléfono). */
    @JavascriptInterface
    fun escuchaDisponible(): Boolean = listenReady()

    /** El estudiante silenció la voz de la mascota en Perfil: la página no lee nada por su cuenta. */
    @JavascriptInterface
    fun silencio(): Boolean = muted()

    /** Lee [texto] en voz alta (texto simple; la página ya quitó las etiquetas y la notación de libro). */
    @JavascriptInterface
    fun hablar(texto: String) {
        val clean = texto.filter { !Character.isISOControl(it) || it == '\n' }.take(MAX_SPOKEN_CHARS)
        if (clean.isNotBlank()) main.post { onSpeak(clean) }
    }

    @JavascriptInterface
    fun callar() {
        main.post { onStopSpeaking() }
    }

    /** Escucha una respuesta dictada; vuelve con `caminoOido(indice, texto)` (índice -1 si no se entendió). */
    @JavascriptInterface
    fun escuchar() {
        main.post { onListen() }
    }
}
