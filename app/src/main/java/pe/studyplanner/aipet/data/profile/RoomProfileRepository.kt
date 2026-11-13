package pe.studyplanner.aipet.data.profile

import androidx.room.withTransaction
import java.time.DayOfWeek
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.studyplanner.aipet.data.academic.AcademicDatabase
import pe.studyplanner.aipet.data.academic.ProfileEntity
import pe.studyplanner.aipet.domain.profile.*

/**
 * The legacy repository is read only here. After the atomic import, Room is the sole writer
 * of every profile field. The unchanged DataStore file is retained as an immutable F1 backup.
 * A failed or invalid import never creates defaults over an existing record.
 */
class RoomProfileRepository(
    private val database: AcademicDatabase,
    private val legacy: ProfileRepository,
    private val preserveLegacyBackup: suspend () -> Unit = {},
    private val beforeImportCommit: suspend () -> Unit = {},
) : ProfileRepository {
    private val dao = database.academicDao()
    private val importMutex = Mutex()

    override val profile: Flow<UserProfile> = flow {
        ensureImported()
        emitAll(dao.observeProfile().map { row ->
            requireNotNull(row) { "No se encontró el perfil local ya importado." }.toDomain()
        })
    }

    override suspend fun save(profile: UserProfile) {
        require(ProfileValidator.validate(profile).isEmpty()) { "El perfil contiene campos inválidos." }
        val normalized = ProfileValidator.normalize(profile)
        ensureImported()
        database.withTransaction {
            val old = requireNotNull(dao.profile()) { "No se pudo leer el perfil local." }
            dao.updateProfile(normalized.toEntity(old.preferencesRevision + 1))
            check(dao.profile()?.toDomain() == normalized) { "La lectura posterior no coincide con el perfil guardado." }
        }
    }

    suspend fun ensureImported() = importMutex.withLock {
        if (dao.profile() != null) return@withLock
        // Decode and validate BEFORE entering Room: invalid/corrupt F1 stays untouched.
        val imported = legacy.profile.first()
        require(ProfileValidator.validate(imported).isEmpty() && ProfileValidator.normalize(imported) == imported) {
            "El perfil anterior contiene datos inválidos y se ha conservado sin cambios."
        }
        preserveLegacyBackup()
        database.withTransaction {
            if (dao.profile() == null) {
                dao.insertProfile(imported.toEntity(0))
                beforeImportCommit()
                check(dao.profile()?.toDomain() == imported) { "La verificación de importación del perfil falló." }
            }
        }
        check(dao.profile()?.legacyImportVersion == 1) { "La importación del perfil no se confirmó." }
    }
}

private fun UserProfile.toEntity(revision: Long) = ProfileEntity(studentName = studentName, petName = petName,
    avatar = avatar.name, zoneId = zoneId, weekStartsOn = weekStartsOn.name, theme = theme.name,
    onboardingCompleted = onboardingCompleted, petVisible = petVisible,
    preferencesRevision = revision, legacyImportVersion = 1)

private fun ProfileEntity.toDomain(): UserProfile {
    check(legacyImportVersion == 1) { "La versión del perfil local no es compatible." }
    val value = UserProfile(studentName, petName, enumValueOf<AvatarStyle>(avatar), zoneId,
        enumValueOf<DayOfWeek>(weekStartsOn), enumValueOf<ThemeMode>(theme), onboardingCompleted, petVisible)
    check(ProfileValidator.validate(value).isEmpty() && ProfileValidator.normalize(value) == value) {
        "El perfil local almacenado contiene datos inválidos."
    }
    return value
}

/** Same-directory rename publishes only a verified complete backup; interrupted files stay diagnosable. */
object LegacyProfileBackup {
    fun preserve(filesDir: File, beforePublish: () -> Unit = {}) {
        val original = File(filesDir, "datastore/profile_v1.preferences_pb")
        if (!original.exists()) return
        val directory = File(filesDir, "legacy-profile-backup").apply {
            check(isDirectory || mkdirs()) { "No se pudo preparar el respaldo del perfil." }
        }
        val backup = File(directory, "profile_v1.preferences_pb")
        if (backup.exists() && digest(original) == digest(backup)) return
        val pending = File(directory, "profile_v1.pending-${UUID.randomUUID()}")
        check(pending.createNewFile()) { "No se pudo reservar el archivo temporal del respaldo." }
        FileOutputStream(pending).use { output ->
            original.inputStream().use { input -> input.copyTo(output) }
            output.fd.sync()
        }
        check(digest(original) == digest(pending)) { "La copia temporal del perfil no coincide. Se conservó para diagnóstico." }
        beforePublish()
        if (backup.exists()) {
            val diagnostic = File(directory, "profile_v1.previous-${UUID.randomUUID()}")
            check(backup.renameTo(diagnostic)) { "No se pudo preservar el respaldo anterior para diagnóstico." }
        }
        check(pending.renameTo(backup)) { "No se pudo publicar el respaldo verificado. El original permanece intacto." }
        check(digest(original) == digest(backup)) { "La verificación final del respaldo falló." }
    }

    private fun digest(file: File): List<Byte> = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
}
