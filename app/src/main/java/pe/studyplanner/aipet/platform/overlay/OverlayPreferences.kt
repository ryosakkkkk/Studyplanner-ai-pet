package pe.studyplanner.aipet.platform.overlay

import android.content.Context

enum class OverlaySize(val label: String, val petDp: Int) {
    COMPACT("Pequeña", 88), NORMAL("Mediana", 112), LARGE("Grande", 136)
}

/** Only presentation preferences; never restores or starts a service. */
class OverlayPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pet_overlay_ui", Context.MODE_PRIVATE)
    val size: OverlaySize get() = runCatching { OverlaySize.valueOf(prefs.getString("size", null) ?: "COMPACT") }.getOrDefault(OverlaySize.COMPACT)
    val snapToEdge: Boolean get() = prefs.getBoolean("snap_to_edge", true)
    val reducedMotion: Boolean get() = prefs.getBoolean("reduced_motion", false)
    val xFraction: Float get() = prefs.getFloat("x_fraction", 1f).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
    val yFraction: Float get() = prefs.getFloat("y_fraction", .4f).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .4f
    fun setSize(value: OverlaySize) { prefs.edit().putString("size", value.name).apply() }
    fun setSnapToEdge(value: Boolean) { prefs.edit().putBoolean("snap_to_edge", value).apply() }
    fun setReducedMotion(value: Boolean) { prefs.edit().putBoolean("reduced_motion", value).apply() }
    fun savePosition(x: Float, y: Float) { prefs.edit().putFloat("x_fraction", x.coerceIn(0f, 1f)).putFloat("y_fraction", y.coerceIn(0f, 1f)).apply() }
}
