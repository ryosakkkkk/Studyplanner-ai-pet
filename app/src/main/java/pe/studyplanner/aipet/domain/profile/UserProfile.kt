package pe.studyplanner.aipet.domain.profile

import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow

enum class AvatarStyle(val label: String) { FOX("Zorro"), OWL("Búho"), CAT("Gato"), PANDA("Panda"), RABBIT("Conejo"), PENGUIN("Pingüino") }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserProfile(
    val studentName: String = "",
    val petName: String = "Kiro",
    val avatar: AvatarStyle = AvatarStyle.FOX,
    val zoneId: String = "America/Lima",
    val weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val onboardingCompleted: Boolean = false,
    val petVisible: Boolean = true,
)

interface ProfileRepository {
    val profile: Flow<UserProfile>

    suspend fun save(profile: UserProfile)
}
