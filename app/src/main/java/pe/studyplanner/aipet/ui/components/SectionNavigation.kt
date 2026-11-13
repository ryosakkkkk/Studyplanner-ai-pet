package pe.studyplanner.aipet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.studyplanner.aipet.R
import pe.studyplanner.aipet.domain.profile.UserProfile

/**
 * Destinos de la app. Solo tres aparecen en la barra inferior (Hoy, Estudiar y Agenda); el resto son pantallas
 * dentro de uno de ellos y marcan su pestaña. El Asistente se abre con «Hablar» desde Hoy y Perfil desde el avatar.
 */
enum class AppSection(val route: String, val label: String) {
    TODAY("today", "Hoy"),
    HOME("home", "Estudiar"),
    CALENDAR("calendar", "Agenda"),
    TASKS("tasks", "Tareas"),
    CHAT("chat", "Asistente"),
    SETTINGS("settings", "Perfil"),
    PROGRESS("progress", "Progreso"),
    PRACTICE("practice", "Temas"),
}

/** Pestaña de la barra inferior a la que pertenece cada pantalla. Perfil no marca ninguna. */
fun AppSection.tab(): AppSection? = when (this) {
    AppSection.HOME, AppSection.PRACTICE, AppSection.PROGRESS -> AppSection.HOME
    AppSection.CALENDAR, AppSection.TASKS -> AppSection.CALENDAR
    AppSection.TODAY, AppSection.CHAT -> AppSection.TODAY
    AppSection.SETTINGS -> null
}

val MainTabs = listOf(AppSection.TODAY, AppSection.HOME, AppSection.CALENDAR)

@Composable
fun SectionNavigation(selectedSection: AppSection, onSelect: (AppSection) -> Unit) {
    val current = selectedSection.tab()
    // Barra translúcida con una línea fina arriba, como las barras de pestañas de iOS.
    val dark = isDarkTheme()
    NavigationBar(
        containerColor = if (dark) pe.studyplanner.aipet.ui.theme.PetDesign.GlassDark else pe.studyplanner.aipet.ui.theme.PetDesign.GlassLight,
        tonalElevation = 0.dp,
        modifier = Modifier.drawBehind {
            drawLine(if (dark) androidx.compose.ui.graphics.Color(0x33FFFFFF) else androidx.compose.ui.graphics.Color(0x1F000000),
                androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1f)
        },
    ) {
        MainTabs.forEach { section ->
            val icon = when (section) {
                AppSection.TODAY -> R.drawable.ic_home
                AppSection.CALENDAR -> R.drawable.ic_agenda
                else -> R.drawable.ic_study
            }
            val selected = section == current
            NavigationBarItem(
                selected = selected,
                // Tocar la pestaña actual desde una pantalla interior vuelve a su raíz.
                onClick = { if (section != selectedSection) onSelect(section) },
                // La pestaña elegida lleva una píldora con el degradado del logo.
                icon = {
                    Box(
                        Modifier.size(width = 58.dp, height = 32.dp).clip(CircleShape)
                            .background(if (selected) androidx.compose.ui.graphics.Brush.horizontalGradient(pe.studyplanner.aipet.ui.theme.PetDesign.BrandGradient)
                                else androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Transparent)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp),
                            tint = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                label = { Text(section.label, maxLines = 1, style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = androidx.compose.ui.graphics.Color.White,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.testTag("section_menu_${section.route}"),
            )
        }
    }
}

/** Avatar del estudiante, igual en las tres pestañas: abre Perfil y ajustes. */
@Composable
fun ProfileAvatarButton(profile: UserProfile, onClick: () -> Unit) {
    val initial = profile.studentName.trim().firstOrNull()?.uppercaseChar()
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).testTag("profile_avatar")
        .semantics { contentDescription = "Abrir perfil y ajustes" }) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(2.dp, androidx.compose.ui.graphics.Brush.linearGradient(pe.studyplanner.aipet.ui.theme.PetDesign.BrandGradient)),
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (initial != null) Text(initial.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                else PetStill(profile.avatar, Modifier.size(36.dp), contentDescription = null)
            }
        }
    }
}
