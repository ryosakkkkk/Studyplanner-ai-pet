package pe.studyplanner.aipet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pe.studyplanner.aipet.ui.theme.PetDesign

/** Si el tema actual es oscuro (según el fondo). */
@Composable
@ReadOnlyComposable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.3f

/**
 * Panel translúcido tipo vidrio: blanco semitransparente con borde claro y sombra suave. Deja ver el fondo de color
 * de la pantalla, como los materiales translúcidos de iOS.
 */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(22.dp), elevation: Dp = 6.dp): Modifier {
    val dark = isDarkTheme()
    return this
        .shadow(elevation, shape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
        .clip(shape)
        .background(if (dark) PetDesign.GlassDark else PetDesign.GlassLight)
        .border(1.dp, if (dark) PetDesign.GlassEdgeDark else PetDesign.GlassEdgeLight, shape)
}

/** Fondo de color de la marca para las pantallas con paneles de vidrio. */
@Composable
fun glassBackdrop(): Brush {
    val dark = isDarkTheme()
    // Los tonos del logo (azul, índigo) y un toque cálido del zorro abajo.
    return if (dark) Brush.verticalGradient(listOf(Color(0xFF16275E), Color(0xFF1B1840), Color(0xFF0B0E1F)))
    else Brush.verticalGradient(listOf(Color(0xFFC7DCFF), Color(0xFFE0DAFF), Color(0xFFFFEEDD)))
}
