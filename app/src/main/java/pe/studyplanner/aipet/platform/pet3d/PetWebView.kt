package pe.studyplanner.aipet.platform.pet3d

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Color
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import java.io.IOException
import pe.studyplanner.aipet.domain.pet.PetClips

/**
 * Sirve los archivos de `assets/web` bajo una dirección https local, sin red: el visor usa módulos de JavaScript y
 * `fetch` para el GLB, y ambos exigen un origen https (con `file://` el navegador los bloquea).
 */
internal object PetAssetServer {
    const val HOST = "appassets.androidplatform.net"
    const val BASE = "https://$HOST/web/"

    fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html"
        "css" -> "text/css"
        "woff2" -> "font/woff2"
        "svg" -> "image/svg+xml"
        "js", "mjs" -> "text/javascript"
        "json" -> "application/json"
        "glb" -> "model/gltf-binary"
        "webp" -> "image/webp"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }

    /** Ruta dentro de assets para una dirección del visor, o null si no le corresponde (otro host, `..`, etc.). */
    fun assetPath(url: Uri): String? {
        if (url.scheme != "https" || url.host != HOST) return null
        val path = url.path ?: return null
        if (!path.startsWith("/web/") || path.contains("..")) return null
        return path.removePrefix("/")
    }

    fun serve(assets: AssetManager, url: Uri): WebResourceResponse? {
        val path = assetPath(url) ?: return if (url.host == HOST) notFound() else null
        return try {
            val mime = mimeOf(path)
            val encoding = if (mime.startsWith("text/") || mime == "application/json") "utf-8" else null
            WebResourceResponse(mime, encoding, assets.open(path)).apply {
                responseHeaders = mapOf("Cache-Control" to "no-cache")
            }
        } catch (_: IOException) {
            notFound()
        }
    }

    private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), null)
}

/**
 * Mascota 3D: el visor three.js de `assets/web/pet-viewer.html` dentro de un WebView transparente. No recibe
 * toques directos (los maneja Compose y se los pasa: toque por partes y giro con el dedo), así que no roba el
 * desplazamiento de la pantalla.
 *
 * @param model nombre del GLB en `assets/web/pets` (p. ej. «zorro»).
 * @param closeUp encuadre de medio cuerpo para huecos pequeños.
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class PetWebView(context: Context, model: String, closeUp: Boolean, initialColors: String = "{}") : FrameLayout(context) {
    /** Modelo que se muestra; cambiarlo carga otra mascota en la misma vista (sin crear otro contexto WebGL). */
    var model: String = model
        private set
    /** Se llama en el hilo principal cuando el modelo ya se dibujó. */
    var onReady: (() -> Unit)? = null

    /** Se llama en el hilo principal si WebGL o el modelo fallan; quien lo use muestra la imagen fija. */
    var onFailed: (() -> Unit)? = null

    /** Parte que se tocó (cabeza, mano, panza, pies, cola, baile), en el hilo principal. */
    var onPoke: ((String) -> Unit)? = null

    private val web = WebView(context)
    private var loaded = false
    private var released = false
    private var shown: PetClips.Choice? = null
    private var paused = false
    private var colorsJson = initialColors


    init {
        web.setBackgroundColor(Color.TRANSPARENT)
        with(web.settings) {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
            mediaPlaybackRequiresUserGesture = true
            setSupportZoom(false)
        }
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                PetAssetServer.serve(view.context.assets, request.url)

            // El visor no navega a ningún sitio.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                post { fail() }
                return true
            }
        }
        web.addJavascriptInterface(Bridge(), "AndroidPet")
        addView(web, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        web.loadUrl("${PetAssetServer.BASE}pet-viewer.html?pet=$model&frame=${if (closeUp) "bust" else "full"}" +
            "&colors=${android.net.Uri.encode(initialColors)}" + debugMode(context))
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = false

    /** Cambia de clip solo si cambió lo pedido. */
    fun show(choice: PetClips.Choice) {
        if (choice == shown) return
        shown = choice
        if (loaded) apply(choice)
    }

    /** Cambia de mascota en la misma vista. Hasta que la nueva dibuje, quien la usa muestra la imagen fija. */
    fun setModel(name: String) {
        if (name == model || released) return
        model = name
        loaded = false
        shown = null
        js("PetViewer.load('$name')")
    }

    /** Colores de la ropa y los accesorios: JSON de [pe.studyplanner.aipet.domain.pet.PetOutfits.toJson]. */
    fun setColors(json: String) {
        if (json == colorsJson) return
        colorsJson = json
        if (loaded) js("PetViewer.setColors($json)")
    }

    /** Saludo de un toque; luego vuelve a lo que estaba. */
    fun greet() {
        if (!loaded) return
        js("PetViewer.play('wave', false)")
    }

    /** Toque en la mascota, en proporción de la vista (0 a 1): mira ahí; en la cabeza ríe por las cosquillas. */
    fun poke(x: Float, y: Float) {
        if (!loaded) return
        js("PetViewer.poke(${x.coerceIn(0f, 1f)}, ${y.coerceIn(0f, 1f)})")
    }

    /** Arrastre horizontal en fracción del ancho de la vista: gira la mascota. */
    fun drag(dx: Float) {
        if (!loaded) return
        js("PetViewer.drag(${dx.coerceIn(-1f, 1f)})")
    }

    /** Fin del arrastre: sigue girando con inercia y después vuelve de frente. */
    fun dragEnd() {
        if (!loaded) return
        js("PetViewer.release()")
    }

    fun setPaused(value: Boolean) {
        if (released || value == paused) return
        paused = value
        if (value) web.onPause() else web.onResume()
        js("window.PetViewer && PetViewer.setPaused($value)")
    }

    fun release() {
        if (released) return
        released = true
        onReady = null
        onFailed = null
        onPoke = null
        // Primero suelta el contexto WebGL desde la página y luego destruye la vista.
        var destroyed = false
        val finish = Runnable {
            if (!destroyed) {
                destroyed = true
                web.removeJavascriptInterface("AndroidPet")
                web.stopLoading()
                removeView(web)
                web.destroy()
            }
        }
        // La vista ya salió de la pantalla: se usa el hilo principal directamente (post() de una vista suelta no corre).
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        web.evaluateJavascript("window.PetViewer && PetViewer.dispose()") { main.post(finish) }
        main.postDelayed(finish, 600)
    }

    private fun apply(choice: PetClips.Choice) {
        require(choice.clip in PetClips.ALL)
        js("PetViewer.play('${choice.clip}', ${choice.loop}); PetViewer.talk(${choice.talking})")
    }

    private fun js(code: String) {
        if (!released) web.evaluateJavascript(code, null)
    }

    private fun fail() {
        if (!released) onFailed?.invoke()
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onLoaded(@Suppress("UNUSED_PARAMETER") name: String) {
            post {
                if (released) return@post
                loaded = true
                js("PetViewer.setColors($colorsJson)")
                shown?.let(::apply)
                onReady?.invoke()
            }
        }

        @JavascriptInterface
        fun onError(what: String) {
            // «shader»: el teléfono no aceptó el sombreador de colores y el visor ya dibuja con el material normal.
            if (what == "shader") return
            post { fail() }
        }

        @JavascriptInterface
        fun onPoke(part: String) {
            post { if (!released) onPoke?.invoke(part) }
        }

        @JavascriptInterface
        fun onClipFinished(@Suppress("UNUSED_PARAMETER") name: String) = Unit
    }
}

/**
 * Solo en la versión de prueba: si existe `files/visor.txt` (se escribe con `adb shell run-as`), su contenido pasa al
 * visor como `modo` para apagar partes del dibujo (sombreador de zonas, párpados, accesorios) y aislar fallas del
 * emulador. En la versión publicada no hace nada.
 */
private fun debugMode(context: android.content.Context): String {
    if (!pe.studyplanner.aipet.BuildConfig.DEBUG) return ""
    val text = runCatching { java.io.File(context.filesDir, "visor.txt").takeIf { it.isFile }?.readText()?.trim() }.getOrNull()
        ?.filter { it.isLetter() || it == ' ' }?.take(60).orEmpty()
    return if (text.isBlank()) "" else "&modo=" + android.net.Uri.encode(text)
}

