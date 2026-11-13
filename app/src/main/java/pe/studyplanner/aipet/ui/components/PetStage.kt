package pe.studyplanner.aipet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Círculo de color detrás de la mascota (azul → índigo del logo) y una sombra suave en el piso. */
@Composable
fun PetStage(modifier: Modifier) {
    val dark = isDarkTheme()
    Canvas(modifier) {
        val r = minOf(size.width, size.height) * 0.42f
        val c = Offset(size.width / 2, size.height * 0.48f)
        drawCircle(Brush.radialGradient(
            listOf(if (dark) Color(0x664F6BFF) else Color(0x803A8DFF), if (dark) Color(0x335E5CE6) else Color(0x405E5CE6), Color.Transparent),
            center = c, radius = r * 1.25f), r * 1.25f, c)
        drawCircle(if (dark) Color(0x22FFFFFF) else Color(0x66FFFFFF), r, c)
        // sombra del piso
        drawOval(Brush.radialGradient(listOf(Color(0x33141A4D), Color.Transparent), center = Offset(c.x, size.height * 0.9f), radius = r * 0.7f),
            topLeft = Offset(c.x - r * 0.7f, size.height * 0.9f - r * 0.12f), size = Size(r * 1.4f, r * 0.24f))
    }
}

