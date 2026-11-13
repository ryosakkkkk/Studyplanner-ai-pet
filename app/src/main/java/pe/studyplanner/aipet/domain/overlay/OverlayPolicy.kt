package pe.studyplanner.aipet.domain.overlay

data class OverlayConditions(
    val foreground: Boolean, val petVisible: Boolean, val overlayAllowed: Boolean,
    val notificationsAllowed: Boolean, val unlocked: Boolean,
)

object OverlayPolicy {
    const val SESSION_MILLIS = 10 * 60 * 1000L
    const val TICKET_MILLIS = 5_000L
    fun rejection(c: OverlayConditions): String? = when {
        !c.foreground -> "Abre StudyPlanner y pulsa Mostrar para iniciar."
        !c.petVisible -> "Muestra primero tu mascota dentro de StudyPlanner."
        !c.overlayAllowed -> "Falta permitir que StudyPlanner se muestre sobre otras apps."
        !c.notificationsAllowed -> "Activa las notificaciones de la sesión flotante para disponer del control Detener."
        !c.unlocked -> "Desbloquea la pantalla y vuelve a pulsar Mostrar."
        else -> null
    }
}

data class OverlayPosition(val x: Int, val y: Int) {
    fun within(screenWidth: Int, screenHeight: Int, width: Int, height: Int, margin: Int): OverlayPosition {
        val min = margin.coerceAtLeast(0)
        val maxX = (screenWidth - width - min).coerceAtLeast(min)
        val maxY = (screenHeight - height - min).coerceAtLeast(min)
        return OverlayPosition(x.coerceIn(min, maxX), y.coerceIn(min, maxY))
    }

    fun atNearestEdge(screenWidth: Int, screenHeight: Int, width: Int, height: Int, margin: Int): OverlayPosition {
        val safe = within(screenWidth, screenHeight, width, height, margin)
        val left = margin.coerceAtLeast(0)
        val right = (screenWidth - width - left).coerceAtLeast(left)
        return safe.copy(x = if (safe.x - left <= right - safe.x) left else right)
    }
}
