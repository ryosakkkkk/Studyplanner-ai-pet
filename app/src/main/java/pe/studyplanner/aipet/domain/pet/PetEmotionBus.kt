package pe.studyplanner.aipet.domain.pet

/**
 * Emoción temporal global (p. ej. «celebrate» durante 3 s) que ven a la vez las mascotas dentro de la app y la flotante,
 * porque viven en el mismo proceso: cada vista lee el bus en cada fotograma y, mientras haya señal, manda sobre su `mood`.
 *
 * Los tiempos son milisegundos de un reloj monótono cualquiera, pero el mismo para emitir y consultar. Las vistas usan
 * `SystemClock.uptimeMillis()`, así que quien emita desde la app debe pasar ese mismo reloj:
 * `PetEmotionBus.emit("celebrate", 3000, SystemClock.uptimeMillis())`.
 */
object PetEmotionBus {
    /** Emoción pedida y el instante en que deja de valer. */
    class Signal(val emotion: String, val startMs: Long, val untilMs: Long)

    @Volatile var current: Signal? = null

    fun emit(emotion: String, durationMs: Long = 3000, nowMs: Long) {
        if (emotion.isBlank()) return
        current = Signal(emotion, nowMs, nowMs + durationMs.coerceAtLeast(0L))
    }

    /** La emoción vigente en [nowMs], o null si no hay señal o ya venció. */
    fun active(nowMs: Long): String? {
        val s = current ?: return null
        return if (nowMs < s.untilMs) s.emotion else null
    }

    /** Emoción que debe mostrar una vista: el saludo de un toque (hasta [greetUntilMs]) manda; luego la señal del bus; si no, su propio [mood]. */
    fun effective(mood: String, nowMs: Long, greetUntilMs: Long = 0L): String = if (nowMs < greetUntilMs) "greet" else active(nowMs) ?: mood

    fun clear() { current = null }
}
