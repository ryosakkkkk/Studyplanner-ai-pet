package pe.studyplanner.aipet.platform.overlay

import android.app.*
import android.animation.ValueAnimator
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.*
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.studyplanner.aipet.MainActivity
import pe.studyplanner.aipet.R
import pe.studyplanner.aipet.domain.overlay.*
import pe.studyplanner.aipet.domain.profile.UserProfile

data class OverlayStatus(val active: Boolean = false, val starting: Boolean = false,
    val message: String = "Sesión flotante apagada.")

/** Process-only state. A permission return, boot or process restart never creates a ticket. */
object PetOverlay {
    const val CHANNEL = "pet_overlay_session"
    const val STOP = "pe.studyplanner.aipet.overlay.STOP"
    const val COMPANION_LINK = "studyplanner://companion"
    private val mutable = MutableStateFlow(OverlayStatus())
    val status = mutable.asStateFlow()
    private data class Ticket(val created: Long, val profile: UserProfile)
    private var ticket: Ticket? = null
    @Volatile private var appForeground = false
    internal var onAppForeground: ((Boolean) -> Unit)? = null
    /** La app avisa cuando está en primer plano: entonces la ventana flotante se oculta y deja pasar los toques. */
    fun setAppInForeground(value: Boolean) { appForeground = value; onAppForeground?.invoke(value) }
    internal fun appInForeground() = appForeground

    fun notificationsAllowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() &&
            (manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)
    }
    fun unlocked(context: Context): Boolean = context.getSystemService(PowerManager::class.java).isInteractive &&
        !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
    fun show(activity: ComponentActivity, profile: UserProfile) {
        if (mutable.value.active || mutable.value.starting) return
        val reason = OverlayPolicy.rejection(OverlayConditions(
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED), profile.petVisible,
            OverlayPermission.allowed(activity), notificationsAllowed(activity), unlocked(activity)))
        if (reason != null) { report(reason); return }
        ticket = Ticket(SystemClock.elapsedRealtime(), profile)
        mutable.value = OverlayStatus(starting = true, message = "Abriendo sesión flotante…")
        try { activity.startForegroundService(Intent(activity, PetOverlayService::class.java)) }
        catch (_: RuntimeException) { ticket = null; report("Android no permitió iniciar la sesión. Vuelve a abrir la app y revisa sus permisos.") }
    }
    internal fun consumeTicket(): UserProfile? {
        val current = ticket
        ticket = null
        return current?.takeIf { SystemClock.elapsedRealtime() - it.created in 0..OverlayPolicy.TICKET_MILLIS }?.profile
    }
    internal fun started() { mutable.value = OverlayStatus(active = true, message = "Activa. Aparece cuando sales de StudyPlanner; dentro de la app se oculta.") }
    internal fun report(message: String) { mutable.value = OverlayStatus(message = message) }
    fun stop(context: Context) {
        ticket = null
        if (!context.stopService(Intent(context, PetOverlayService::class.java))) report("Sesión flotante apagada.")
    }
}

/** User-started bounded 3D overlay. Only its presentation is persisted; no audio or auto-restart. */
class PetOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windows: WindowManager
    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var petView: OverlayPetView? = null
    private lateinit var preferences: OverlayPreferences
    private var snapAnimation: ValueAnimator? = null
    private var registered = false
    private var stoppedMessage = "Sesión flotante apagada."
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { finish("La pantalla se apagó. Pulsa Mostrar en la app para iniciar otra sesión.") }
    }
    private val expire = Runnable { finish("Terminó la sesión de diez minutos. Puedes iniciar otra desde la app.") }
    private val monitor = object : Runnable {
        override fun run() {
            if (!OverlayPermission.allowed(this@PetOverlayService) || !PetOverlay.notificationsAllowed(this@PetOverlayService) || !PetOverlay.unlocked(this@PetOverlayService)) {
                finish("Sesión detenida: revisa el permiso, las notificaciones y el bloqueo de pantalla.")
            } else handler.postDelayed(this, 2_000)
        }
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PetOverlay.STOP) { finish("Sesión detenida desde la notificación."); return START_NOT_STICKY }
        if (root != null) return START_NOT_STICKY
        val profile = PetOverlay.consumeTicket()
        if (profile == null || !OverlayPermission.allowed(this) || !PetOverlay.notificationsAllowed(this) || !PetOverlay.unlocked(this)) {
            finish("No hay una solicitud válida. Pulsa Mostrar desde StudyPlanner.")
            return START_NOT_STICKY
        }
        try {
            promote()
            windows = getSystemService(WindowManager::class.java)
            addWindow(profile)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
            else @Suppress("DEPRECATION") registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
            registered = true
            handler.postDelayed(expire, OverlayPolicy.SESSION_MILLIS)
            handler.postDelayed(monitor, 2_000)
        } catch (_: RuntimeException) { finish("Android no pudo mostrar la mascota flotante. La agenda se conserva.") }
        return START_NOT_STICKY
    }
    private fun promote() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(PetOverlay.CHANNEL, "Sesión de mascota flotante", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Control permanente de la sesión flotante iniciada desde la app."
            setSound(null, null); enableVibration(false); lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
        val stop = PendingIntent.getService(this, 8101, Intent(this, PetOverlayService::class.java).setAction(PetOverlay.STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 8102, companionIntent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, PetOverlay.CHANNEL)
            .setSmallIcon(R.drawable.ic_pet_overlay).setContentTitle("Mascota flotante activa")
            .setContentText("Toca para abrir tu compañero · sin micrófono activo.")
            .setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(open).addAction(Notification.Action.Builder(null, "Detener", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(8100, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(8100, notification)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun companionIntent() = Intent(this, MainActivity::class.java)
        .setData(android.net.Uri.parse(PetOverlay.COMPANION_LINK))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    private fun openCompanion() {
        // Abre el asistente sin encender el micrófono. La sesión sigue: al salir de la app la mascota vuelve a aparecer.
        val opened = try { startActivity(companionIntent()); true } catch (_: RuntimeException) { false }
        if (!opened) finish("No se pudo abrir la app. La sesión flotante se cerró.")
    }
    private var closeBadge: View? = null
    private val hideBadge = Runnable { closeBadge?.visibility = View.GONE }
    private fun addWindow(profile: UserProfile) {
        preferences = OverlayPreferences(this)
        val petSize = preferences.size.petDp
        // Solo la mascota con sus efectos: sin tarjeta ni barra de botones. Mantener presionado muestra «Cerrar».
        val layout = FrameLayout(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            clipChildren = false
        }
        val pet = OverlayPetView(this, profile.avatar).apply {
            contentDescription = "${profile.petName}. Toca para abrir el asistente. Arrastra para mover. Mantén presionado para cerrar."
            mood = "floating"
            reducedMotion = preferences.reducedMotion
            onOpen = ::openCompanion
            onMove = { dx, dy -> moveBy(dx, dy) }
            onDragStart = { snapAnimation?.cancel(); snapAnimation = null }
            onRelease = ::settlePosition
            onLongPress = { showCloseBadge() }
            onCloseAction = { finish("Mascota flotante cerrada.") }
        }
        petView = pet
        layout.addView(pet, FrameLayout.LayoutParams(dp(petSize), dp(petSize), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        val close = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel); setColorFilter(0xFF3B2A86.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0xF2FFFFFF.toInt()); setStroke(dp(1), 0xFFCEC1F7.toInt())
            }
            contentDescription = "Cerrar mascota flotante"
            visibility = View.GONE
            setOnClickListener { finish("Mascota flotante cerrada.") }
        }
        closeBadge = close
        layout.addView(close, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END))
        params = WindowManager.LayoutParams(dp(petSize + 12), dp(petSize + 12), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT; title = "StudyPlanner mascota flotante"
        }
        restorePosition()
        root = layout
        layout.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: android.view.View) { PetOverlay.started(); layout.post { moveBy(0, 0) } }
            override fun onViewDetachedFromWindow(view: android.view.View) { }
        })
        windows.addView(layout, params)
        PetOverlay.onAppForeground = { inApp -> handler.post { applyAppForeground(inApp) } }
        applyAppForeground(PetOverlay.appInForeground())
    }
    private fun showCloseBadge() {
        val close = closeBadge ?: return
        close.visibility = View.VISIBLE
        handler.removeCallbacks(hideBadge); handler.postDelayed(hideBadge, 3_000)
    }
    /** Dentro de la app: invisible y sin recibir toques. Fuera: visible y movible. */
    private fun applyAppForeground(inApp: Boolean) {
        val view = root ?: return
        val p = params ?: return
        view.visibility = if (inApp) View.INVISIBLE else View.VISIBLE
        p.flags = if (inApp) p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        if (inApp) petView?.onPause() else petView?.onResume()
        try { windows.updateViewLayout(view, p) } catch (_: RuntimeException) { }
    }
    @Suppress("DEPRECATION")
    private fun usableSize(): Point {
        if(Build.VERSION.SDK_INT >= 30) {
            val metrics = windows.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return Point(metrics.bounds.width()-insets.left-insets.right, metrics.bounds.height()-insets.top-insets.bottom)
        }
        return Point().also { windows.defaultDisplay.getSize(it); it.y=(it.y-dp(24)).coerceAtLeast(1) }
    }
    private fun restorePosition() {
        val p = params ?: return
        val size = usableSize(); val margin=dp(8)
        p.x = margin + ((size.x-p.width-2*margin).coerceAtLeast(0)*preferences.xFraction).toInt()
        p.y = margin + ((size.y-p.height-2*margin).coerceAtLeast(0)*preferences.yFraction).toInt()
    }
    private fun savePosition() {
        val p=params ?: return
        val size=usableSize();val margin=dp(8)
        preferences.savePosition((p.x-margin).toFloat()/(size.x-p.width-2*margin).coerceAtLeast(1), (p.y-margin).toFloat()/(size.y-p.height-2*margin).coerceAtLeast(1))
    }
    private fun moveBy(dx: Int, dy: Int) {
        val view = root ?: return
        val p = params ?: return
        val size = usableSize()
        val position = OverlayPosition(p.x + dx, p.y + dy).within(size.x, size.y, p.width, p.height, dp(8))
        p.x = position.x; p.y = position.y
        try { windows.updateViewLayout(view, p) } catch (_: RuntimeException) { finish("La ventana dejó de estar disponible.") }
    }
    private fun settlePosition() {
        val p=params ?: return
        if(!preferences.snapToEdge) { savePosition(); return }
        val size=usableSize()
        val target=OverlayPosition(p.x,p.y).atNearestEdge(size.x,size.y,p.width,p.height,dp(8))
        if(preferences.reducedMotion) { moveBy(target.x-p.x,target.y-p.y);savePosition();return }
        snapAnimation?.cancel()
        snapAnimation=ValueAnimator.ofInt(p.x,target.x).apply {
            duration=180
            addUpdateListener { animator -> val current=params ?: return@addUpdateListener;moveBy((animator.animatedValue as Int)-current.x,0) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled=false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled=true }
                override fun onAnimationEnd(animation: android.animation.Animator) { if(!cancelled)savePosition() }
            })
            start()
        }
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) { super.onConfigurationChanged(newConfig); snapAnimation?.cancel();root?.post { restorePosition();moveBy(0,0) } }
    private fun finish(message: String) { stoppedMessage = message; release(); stopSelf() }
    private fun release() {
        handler.removeCallbacksAndMessages(null)
        snapAnimation?.cancel();snapAnimation=null
        if(params != null && ::preferences.isInitialized) savePosition()
        PetOverlay.onAppForeground = null
        closeBadge = null
        petView?.onPause();petView=null
        if (registered) { unregisterReceiver(screenOff); registered = false }
        root?.let { view -> if (::windows.isInitialized) runCatching { windows.removeViewImmediate(view) } }
        root = null; params = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        PetOverlay.report(stoppedMessage)
    }
    override fun onTaskRemoved(rootIntent: Intent?) { finish("La app se retiró de recientes. La sesión flotante terminó."); super.onTaskRemoved(rootIntent) }
    override fun onDestroy() { release(); super.onDestroy() }
}
