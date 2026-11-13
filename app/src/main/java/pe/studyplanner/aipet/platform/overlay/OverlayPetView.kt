package pe.studyplanner.aipet.platform.overlay

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import pe.studyplanner.aipet.domain.pet.PetClips
import pe.studyplanner.aipet.domain.pet.PetEmotionBus
import pe.studyplanner.aipet.domain.profile.AvatarStyle
import kotlin.math.hypot
import pe.studyplanner.aipet.ui.components.stillImage

/**
 * Mascota flotante que se arrastra. Para las mascotas con modelo 0.17 muestra la animación WebP pregrabada del mismo
 * modelo (casi sin costo de batería); el Conejo usa su imagen. Durante un aviso muestra la campana de sus láminas.
 */
internal class OverlayPetView(context: Context, private val avatar: AvatarStyle) : FrameLayout(context) {
    private val model = PetClips.modelName(avatar)
    private val sprite: ImageView = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private var spriteClip = ""
    private var resumed = false
    var mood: String = "idle"
    var reducedMotion: Boolean = false
        set(value) { field = value; refreshSprite(force = true) }
    private val busWatch = object : Runnable {
        override fun run() { refreshSprite(); if (resumed) postDelayed(this, 500) }
    }
    init {
        addView(sprite, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        refreshSprite(force = true)
    }
    fun onResume() { resumed = true; removeCallbacks(busWatch); post(busWatch); (sprite.drawable as? android.graphics.drawable.Animatable)?.start() }
    fun onPause() { resumed = false; removeCallbacks(busWatch); (sprite.drawable as? android.graphics.drawable.Animatable)?.stop() }
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    private fun refreshSprite(force: Boolean = false) {
        val image = sprite
        val signal = PetEmotionBus.active(SystemClock.uptimeMillis())
        // Durante un aviso muestra la pose de las láminas («campana» o «alarma»); si no, su animación en reposo.
        val sticker = when (signal) { null -> null; "notify" -> "alarma"; else -> pe.studyplanner.aipet.domain.pet.PetStickers.forMood(signal) ?: "campana" }
        val clip = sticker ?: "idle"
        if (!force && clip == spriteClip) return
        spriteClip = clip
        if (sticker != null) {
            pe.studyplanner.aipet.platform.pet3d.PetStickerImages.load(context, avatar, sticker)?.let { image.setImageBitmap(it); return }
        }
        val name = model
        if (name == null) { image.setImageResource(avatar.stillImage()); return }
        val path = "sprites/${name}_idle.webp"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !reducedMotion) {
                val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.assets, path))
                image.setImageDrawable(drawable)
                if (drawable is AnimatedImageDrawable && resumed) drawable.start()
            } else {
                context.assets.open("sprites/${name}_idle.png").use { image.setImageBitmap(BitmapFactory.decodeStream(it)) }
            }
        } catch (_: Exception) {
            image.setImageResource(avatar.stillImage())
        }
    }
    var onOpen: () -> Unit = {}
    var onMove: (Int, Int) -> Unit = { _, _ -> }
    var onRelease: () -> Unit = {}
    var onDragStart: () -> Unit = {}
    var onLongPress: () -> Unit = {}
    var onCloseAction: () -> Unit = {}
    private val longPress = Runnable { if (!dragging) { longPressed = true; performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); onLongPress() } }
    private var longPressed = false
    private var startX = 0f; private var startY = 0f
    private var lastX = 0f; private var lastY = 0f
    private var dragging = false
    init { isClickable = true; isFocusable = true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { onDragStart(); startX = event.rawX; startY = event.rawY; lastX = startX; lastY = startY; dragging = false; longPressed = false; isPressed = true
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong()); return true }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(event.rawX - startX, event.rawY - startY) > ViewConfiguration.get(context).scaledTouchSlop) { dragging = true; removeCallbacks(longPress) }
                if (dragging) { onMove((event.rawX - lastX).toInt(), (event.rawY - lastY).toInt()); lastX = event.rawX; lastY = event.rawY }
                return true
            }
            MotionEvent.ACTION_UP -> { isPressed = false; removeCallbacks(longPress); if (longPressed) Unit else if (!dragging) performClick() else onRelease(); return true }
            MotionEvent.ACTION_CANCEL -> { isPressed = false; removeCallbacks(longPress); if (dragging) onRelease(); dragging = true; return true }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); onOpen(); return true }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Button"
        listOf("Mover arriba", "Mover abajo", "Mover a la izquierda", "Mover a la derecha").forEachIndexed { index, label ->
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(MOVE + index, label))
        }
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(MOVE + 4, "Cerrar mascota flotante"))
    }
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val step = (24 * resources.displayMetrics.density).toInt()
        when (action) {
            MOVE -> onMove(0, -step); MOVE + 1 -> onMove(0, step)
            MOVE + 2 -> onMove(-step, 0); MOVE + 3 -> onMove(step, 0)
            MOVE + 4 -> { onCloseAction(); return true }
            else -> return super.performAccessibilityAction(action, arguments)
        }
        onRelease()
        return true
    }
    private companion object { const val MOVE = 0x01020001 }
}
