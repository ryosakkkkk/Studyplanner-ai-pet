package pe.studyplanner.aipet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.studyplanner.aipet.domain.profile.ThemeMode

// Colores de la marca (logo: degradado azul → índigo y zorro naranja). El azul de acción es la mezcla de los dos
// tonos del logo con contraste 5:1 sobre blanco; el fondo lleva un tinte azul muy suave en vez de gris. Los verdes,
// rojos y naranjas de texto están oscurecidos para cumplir contraste 4.5:1.
private val LightColors = lightColorScheme(
    primary = Color(0xFF3A5BF0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8FF),
    onPrimaryContainer = Color(0xFF13246E),
    secondary = Color(0xFFB25000),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE9CC),
    onSecondaryContainer = Color(0xFF4A2300),
    tertiary = Color(0xFF1F7A35),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF6E4),
    onTertiaryContainer = Color(0xFF0B3D1A),
    background = Color(0xFFF4F6FF),
    onBackground = Color(0xFF161A30),
    surface = Color(0xFFF4F6FF),
    onSurface = Color(0xFF161A30),
    surfaceDim = Color(0xFFD9DDF0),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FAFF),
    surfaceContainer = Color(0xFFEEF1FC),
    surfaceContainerHigh = Color(0xFFE6EAF8),
    surfaceContainerHighest = Color(0xFFDDE2F3),
    surfaceVariant = Color(0xFFE3E7F6),
    onSurfaceVariant = Color(0xFF575C75),
    outline = Color(0xFF8A8FA8),
    outlineVariant = Color(0xFFD5D9EA),
    error = Color(0xFFD70015),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF5C0009),
    inverseSurface = Color(0xFF262A40),
    inverseOnSurface = Color(0xFFF4F6FF),
    inversePrimary = Color(0xFF9DB0FF),
    surfaceTint = Color(0xFF3A5BF0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8EA4FF),
    onPrimary = Color(0xFF0B1660),
    primaryContainer = Color(0xFF2A3A9E),
    onPrimaryContainer = Color(0xFFE0E6FF),
    secondary = Color(0xFFFFB45C),
    onSecondary = Color(0xFF452200),
    secondaryContainer = Color(0xFF5C3300),
    onSecondaryContainer = Color(0xFFFFE2C2),
    tertiary = Color(0xFF30D158),
    onTertiary = Color(0xFF00330F),
    tertiaryContainer = Color(0xFF173D22),
    onTertiaryContainer = Color(0xFFD5F7DE),
    background = Color(0xFF0B0E1F),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0B0E1F),
    onSurface = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFF0B0E1F),
    surfaceBright = Color(0xFF353A58),
    surfaceContainerLowest = Color(0xFF151A30),
    surfaceContainerLow = Color(0xFF181D35),
    surfaceContainer = Color(0xFF20263F),
    surfaceContainerHigh = Color(0xFF2A304B),
    surfaceContainerHighest = Color(0xFF353B57),
    surfaceVariant = Color(0xFF242A44),
    onSurfaceVariant = Color(0xFFB4B9D2),
    outline = Color(0xFF8A8FA8),
    outlineVariant = Color(0xFF353A55),
    error = Color(0xFFFF453A),
    onError = Color(0xFF410002),
    errorContainer = Color(0xFF5C1512),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF4F6FF),
    inverseOnSurface = Color(0xFF161A30),
    inversePrimary = Color(0xFF3A5BF0),
    surfaceTint = Color(0xFF8EA4FF),
)

private fun typeStyle(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

private val StudyTypography = Typography(
    displayLarge = typeStyle(52, 60, FontWeight.Bold),
    displayMedium = typeStyle(44, 52, FontWeight.Bold),
    displaySmall = typeStyle(36, 44, FontWeight.Bold),
    headlineLarge = typeStyle(32, 40, FontWeight.Bold),
    headlineMedium = typeStyle(28, 36, FontWeight.Bold),
    headlineSmall = typeStyle(26, 32, FontWeight.Bold),
    titleLarge = typeStyle(20, 26, FontWeight.SemiBold),
    titleMedium = typeStyle(16, 24, FontWeight.SemiBold),
    titleSmall = typeStyle(14, 20, FontWeight.SemiBold),
    bodyLarge = typeStyle(17, 24, FontWeight.Normal),
    bodyMedium = typeStyle(14, 22, FontWeight.Normal),
    bodySmall = typeStyle(12, 18, FontWeight.Normal),
    labelLarge = typeStyle(14, 20, FontWeight.SemiBold),
    labelMedium = typeStyle(12, 18, FontWeight.Medium),
    labelSmall = typeStyle(11, 16, FontWeight.Medium),
)

private val StudyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun StudyPlannerTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = StudyTypography,
        shapes = StudyShapes,
        content = content,
    )
}
