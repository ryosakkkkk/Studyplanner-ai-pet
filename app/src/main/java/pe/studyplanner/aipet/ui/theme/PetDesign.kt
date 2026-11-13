package pe.studyplanner.aipet.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colores de la marca, tomados del logo (degradado azul → índigo con el zorro naranja), y acentos vivos para
 * pestañas, misiones y botones.
 */
object PetDesign {
    /** Logo: azul y índigo del degradado del ícono, y el naranja del zorro. */
    val BrandBlue=Color(0xFF0A84FF)
    val BrandIndigo=Color(0xFF5E5CE6)
    val BrandOrange=Color(0xFFFF9F0A)
    /** Azul de la marca con contraste suficiente para texto blanco encima. */
    val BrandInk=Color(0xFF3A5BF0)
    val BrandGradient=listOf(BrandBlue, BrandIndigo)

    val Violet=Color(0xFF8E44C9)
    val Blue=Color(0xFF0A84FF)
    val Turquoise=Color(0xFF00A3A0)
    val Pink=Color(0xFFE0245E)
    val Orange=Color(0xFFE07A00)
    val Green=Color(0xFF34C759)
    /** Racha y meta del día (pantalla Hoy). */
    val Amber=Color(0xFFFF9F0A)
    val AmberText=Color(0xFFB25000)

    /** Misiones de Hoy: cada una con su color (degradado de claro a oscuro para que el texto blanco se lea). */
    val QuestBlue=listOf(Color(0xFF2E8BFF), Color(0xFF3A5BF0))
    val QuestGreen=listOf(Color(0xFF2DB55D), Color(0xFF1F8A3D))
    val QuestOrange=listOf(Color(0xFFFF8A1F), Color(0xFFE8590C))
    val StreakGradient=listOf(Color(0xFFFFB020), Color(0xFFF76707))

    /** Vidrio: paneles translúcidos sobre el fondo (barra inferior, globo y tarjetas de Hoy). */
    val GlassLight=Color(0xD9FFFFFF)
    val GlassDark=Color(0xB3181C33)
    val GlassEdgeLight=Color(0x99FFFFFF)
    val GlassEdgeDark=Color(0x33FFFFFF)
    val ActionGradient=listOf(Color(0xFF0A84FF),Color(0xFF5E5CE6))
}
