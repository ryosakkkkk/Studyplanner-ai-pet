package pe.studyplanner.aipet.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.studyplanner.aipet.domain.profile.AvatarStyle
import pe.studyplanner.aipet.domain.profile.ProfileRepository
import pe.studyplanner.aipet.domain.profile.ProfileValidator
import pe.studyplanner.aipet.domain.profile.ThemeMode
import pe.studyplanner.aipet.domain.profile.UserProfile

/** Un registro completo de perfil para fase 1; la aplicación aporta una única instancia del almacén. */
class DataStoreProfileRepository(
    private val dataStore: DataStore<Preferences>,
) : ProfileRepository {
    // Los errores de disco y corrupción se propagan: nunca se convierten en un perfil nuevo.
    override val profile: Flow<UserProfile> = dataStore.data.map(::decode)

    override suspend fun save(profile: UserProfile) {
        val errors = ProfileValidator.validate(profile)
        require(errors.isEmpty()) { errors.values.joinToString(" ") }
        val normalized = ProfileValidator.normalize(profile)
        dataStore.edit { preferences ->
            // Evita sobrescribir un registro incompleto o de una versión desconocida.
            decode(preferences)
            preferences[Keys.schemaVersion] = SCHEMA_VERSION
            preferences[Keys.studentName] = normalized.studentName
            preferences[Keys.petName] = normalized.petName
            preferences[Keys.avatar] = normalized.avatar.name
            preferences[Keys.zoneId] = normalized.zoneId
            preferences[Keys.weekStartsOn] = normalized.weekStartsOn.name
            preferences[Keys.theme] = normalized.theme.name
            preferences[Keys.onboardingCompleted] = normalized.onboardingCompleted
            preferences[Keys.petVisible] = normalized.petVisible
        }
    }

    private fun decode(preferences: Preferences): UserProfile {
        if (preferences.asMap().isEmpty()) return UserProfile()

        try {
            if (preferences.required(Keys.schemaVersion) != SCHEMA_VERSION) {
                throw InvalidStoredProfileException("La versión del perfil guardado no es compatible.")
            }
            val profile = UserProfile(
                studentName = preferences.required(Keys.studentName),
                petName = preferences.required(Keys.petName),
                avatar = enumValueOf<AvatarStyle>(preferences.required(Keys.avatar)),
                zoneId = preferences.required(Keys.zoneId),
                weekStartsOn = enumValueOf<DayOfWeek>(preferences.required(Keys.weekStartsOn)),
                theme = enumValueOf<ThemeMode>(preferences.required(Keys.theme)),
                onboardingCompleted = preferences.required(Keys.onboardingCompleted),
                petVisible = preferences.required(Keys.petVisible),
            )
            if (ProfileValidator.validate(profile).isNotEmpty() ||
                ProfileValidator.normalize(profile) != profile
            ) {
                throw InvalidStoredProfileException("El perfil guardado contiene datos inválidos.")
            }
            return profile
        } catch (error: ClassCastException) {
            throw InvalidStoredProfileException("Un campo del perfil guardado tiene un tipo incorrecto.", error)
        } catch (error: IllegalArgumentException) {
            throw InvalidStoredProfileException("El perfil guardado contiene una opción desconocida.", error)
        }
    }

    private fun <T> Preferences.required(key: Preferences.Key<T>): T =
        this[key] ?: throw InvalidStoredProfileException("Falta un campo obligatorio del perfil guardado.")

    private object Keys {
        val schemaVersion = intPreferencesKey("schema_version")
        val studentName = stringPreferencesKey("student_name")
        val petName = stringPreferencesKey("pet_name")
        val avatar = stringPreferencesKey("avatar")
        val zoneId = stringPreferencesKey("zone_id")
        val weekStartsOn = stringPreferencesKey("week_starts_on")
        val theme = stringPreferencesKey("theme")
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val petVisible = booleanPreferencesKey("pet_visible")
    }

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}

class InvalidStoredProfileException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
