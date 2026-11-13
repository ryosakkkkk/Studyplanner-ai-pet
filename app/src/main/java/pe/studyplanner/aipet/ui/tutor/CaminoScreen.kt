package pe.studyplanner.aipet.ui.tutor

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pe.studyplanner.aipet.data.ai.AiTransport
import pe.studyplanner.aipet.data.tutor.CaminoAiPrompt
import pe.studyplanner.aipet.data.tutor.CaminoHandoff
import pe.studyplanner.aipet.data.tutor.CaminoStore
import pe.studyplanner.aipet.data.tutor.TutorHistory
import pe.studyplanner.aipet.data.tutor.TutorHistoryState
import pe.studyplanner.aipet.domain.academic.AcademicSnapshot
import pe.studyplanner.aipet.domain.academic.DatasetScope
import pe.studyplanner.aipet.domain.agent.AgentContext
import pe.studyplanner.aipet.domain.json.MiniJson
import pe.studyplanner.aipet.domain.pet.PetEmotionBus
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.domain.tutor.MasteryStatus
import pe.studyplanner.aipet.domain.tutor.TutorContent
import pe.studyplanner.aipet.platform.pet3d.PetAssetServer
import pe.studyplanner.aipet.ui.components.PetAvatar
import pe.studyplanner.aipet.ui.components.isDarkTheme

/** Dirección local de la página del camino (se sirve desde `assets/web/camino`, sin Internet). */
private const val CAMINO_URL = "${PetAssetServer.BASE}camino/index.html"

/**
 * «Camino UNAMBA»: el postulante elige su grupo (A, B o C), responde un diagnóstico corto y recibe un camino ordenado
 * por lo que más pesa en su examen y lo que más le falta. La pantalla es una página local (`assets/web/camino`) porque
 * las explicaciones llevan notación matemática (KaTeX) y gráficas; aquí solo van la barra, la mascota y el puente.
 *
 * Los temas que ya existen en la app se abren con [onOpenTopic] (su lección, pistas y comprobación de siempre); los
 * temas nuevos se practican dentro de la página con guía socrática: si el estudiante falla o no sabe, la página no le
 * da la respuesta, le hace preguntas paso a paso. El estado se guarda con [CaminoStore].
 *
 * Si la práctica de la app dejó una pregunta en [CaminoHandoff], la página abre directo la conversación con el tutor
 * sobre esa pregunta y «atrás» vuelve a la práctica.
 *
 * @param ai transporte de la IA en la nube para conversar con el tutor; `null` si no está activa (la página oculta la
 *   conversación y deja la guía escrita, que no necesita Internet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaminoScreen(
    profile: UserProfile,
    history: TutorHistory?,
    ai: AiTransport?,
    onBack: () -> Unit,
    onOpenTopic: (String) -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val dark = isDarkTheme()
    val zone = remember(profile.zoneId) { runCatching { ZoneId.of(profile.zoneId) }.getOrDefault(ZoneId.systemDefault()) }
    LaunchedEffect(history) { history?.start() }
    val data = history?.state?.collectAsStateWithLifecycle()?.value ?: TutorHistoryState(loaded = true)
    val masteryText = remember(data.attempts.size) { masteryAsJson(data) }
    val mastery = remember { AtomicReference("{}") }
    SideEffect { mastery.set(masteryText) }

    var title by remember { mutableStateOf("Camino UNAMBA") }
    var fraction by remember { mutableFloatStateOf(-1f) }
    val aiNow by rememberUpdatedState(ai)
    val openNow by rememberUpdatedState(onOpenTopic)
    val backNow by rememberUpdatedState(onBack)
    val zoneNow by rememberUpdatedState(zone)
    val zoneIdNow by rememberUpdatedState(profile.zoneId)

    val web = remember(dark) {
        createCaminoWebView(context, dark) { view ->
            CaminoBridge(
                context = context.applicationContext,
                mastery = { mastery.get() },
                aiAvailable = { aiNow != null },
                onHeader = { text, value -> title = text.take(80).ifBlank { "Camino UNAMBA" }; fraction = value },
                onEvent = { type ->
                    val now = SystemClock.uptimeMillis()
                    when (type) {
                        "correcta" -> { PetEmotionBus.emit("correct", 2500L, now); CaminoStore.recordAnswer(context, LocalDate.now(zoneNow)) }
                        "incorrecta" -> { PetEmotionBus.emit("wrong", 2500L, now); CaminoStore.recordAnswer(context, LocalDate.now(zoneNow)) }
                        // «No lo sé»: cuenta como pregunta trabajada, sin reacción de error.
                        "duda" -> { PetEmotionBus.emit("think", 3000L, now); CaminoStore.recordAnswer(context, LocalDate.now(zoneNow)) }
                        // Acertó después de la guía: el día ya contó esta pregunta en su primer intento.
                        "guiada" -> PetEmotionBus.emit("correct", 2500L, now)
                        "logro" -> PetEmotionBus.emit("celebrate", 3200L, now)
                        else -> Unit
                    }
                },
                onOpenTopic = { id -> if (TutorContent.catalog.topic(id) != null) openNow(id) },
                onClose = { backNow() },
                onTutor = { id, request ->
                    // El identificador vuelve dentro de un script: solo letras y números.
                    val safeId = id.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }.take(24)
                    val transport = aiNow
                    if (transport == null) view.evaluateJavascript("window.caminoTutor && caminoTutor('$safeId', false, '')", null)
                    else scope.launch {
                        var failure = ""
                        val text = try {
                            val reply = transport.chat(
                                CaminoAiPrompt.relay(request),
                                AgentContext(AcademicSnapshot(DatasetScope.PERSONAL), zoneIdNow, Instant.now()),
                            )
                            CaminoAiPrompt.replyText(reply.kind, reply.text)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: IllegalStateException) {
                            failure = CaminoAiPrompt.failureText(e.message)
                            null
                        } catch (_: Exception) {
                            null
                        }
                        val call = if (text != null) "caminoTutor('$safeId', true, ${MiniJson.quote(text)})" else "caminoTutor('$safeId', false, ${MiniJson.quote(failure)})"
                        view.evaluateJavascript("window.caminoTutor && $call", null)
                    }
                },
            )
        }
    }
    DisposableEffect(web, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                web.onResume()
                // Al volver de un tema de la app, la página vuelve a leer el dominio y repinta el camino.
                web.evaluateJavascript("window.caminoVolver && caminoVolver()", null)
            }
            if (event == Lifecycle.Event.ON_PAUSE) web.onPause()
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
        web.evaluateJavascript("(window.caminoAtras && caminoAtras()) ? 'si' : 'no'") { result ->
            if (result == null || !result.contains("si")) backNow()
        }
    }
    BackHandler(onBack = goBack)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Camino UNAMBA", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
    }) { padding ->
        // imePadding: al escribirle al tutor, el teclado no tapa la caja de texto.
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (profile.petVisible) PetAvatar(profile.avatar, Modifier.size(84.dp), mood = "idle", closeUp = true)
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
}

private const val BRIDGE_NAME = "AndroidCamino"

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

/** Crea el puente cuando la vista ya existe: el puente la necesita para devolverle las respuestas del tutor. */
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
 * que toca la interfaz se pasa al hilo principal.
 */
private class CaminoBridge(
    private val context: Context,
    private val mastery: () -> String,
    private val aiAvailable: () -> Boolean,
    private val onHeader: (String, Float) -> Unit,
    private val onEvent: (String) -> Unit,
    private val onOpenTopic: (String) -> Unit,
    private val onClose: () -> Unit,
    private val onTutor: (String, String) -> Unit,
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

    /** «Volver a mi práctica»: cierra esta pantalla (cuando se abrió desde un ejercicio de la app). */
    @JavascriptInterface
    fun cerrar() {
        main.post { onClose() }
    }

    @JavascriptInterface
    fun iaDisponible(): Boolean = aiAvailable()

    /** Pregunta que llega desde la práctica de la app para conversarla con el tutor (JSON), o vacío. Se entrega una vez. */
    @JavascriptInterface
    fun pendiente(): String = CaminoHandoff.take()

    /** Petición al tutor con IA: [id] identifica la conversación y vuelve con la respuesta en `caminoTutor(id, ok, texto)`. */
    @JavascriptInterface
    fun tutorIA(id: String, peticion: String) {
        main.post { onTutor(id, peticion) }
    }
}
