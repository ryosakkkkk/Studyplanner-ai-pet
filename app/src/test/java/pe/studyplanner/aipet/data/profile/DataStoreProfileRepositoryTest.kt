package pe.studyplanner.aipet.data.profile

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.io.IOException
import java.time.DayOfWeek
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pe.studyplanner.aipet.domain.profile.AvatarStyle
import pe.studyplanner.aipet.domain.profile.ThemeMode
import pe.studyplanner.aipet.domain.profile.UserProfile

class DataStoreProfileRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `solo un almacen nuevo expone los valores iniciales`() = runTest {
        withStore { store ->
            assertEquals(UserProfile(), DataStoreProfileRepository(store).profile.first())
        }
    }

    @Test
    fun `guarda todos los campos y los recupera al cerrar y reabrir el archivo`() = runTest {
        val file = File(temporaryFolder.root, "persistencia.preferences_pb")
        val expected = UserProfile(
            studentName = "María José",
            petName = "Lucía",
            avatar = AvatarStyle.CAT,
            zoneId = "Europe/Madrid",
            weekStartsOn = DayOfWeek.SUNDAY,
            theme = ThemeMode.DARK,
            onboardingCompleted = true,
            petVisible = false,
        )
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = createJvmStore(file, firstScope)
            val repository = DataStoreProfileRepository(store)
            repository.save(expected.copy(studentName = "  Mari\u0301a Jose\u0301  ", petName = " Luci\u0301a "))
            assertEquals(expected, repository.profile.first())
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
        }

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = createJvmStore(file, secondScope)
            assertEquals(expected, DataStoreProfileRepository(reopened).profile.first())
        } finally {
            secondScope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test
    fun `un perfil invalido no sustituye el registro valido existente`() = runTest {
        withStore { store ->
            val repository = DataStoreProfileRepository(store)
            val expected = UserProfile(petName = "Inti", onboardingCompleted = true)
            repository.save(expected)
            val error = runCatching { repository.save(expected.copy(petName = "   ")) }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertEquals(expected, repository.profile.first())
        }
    }

    @Test
    fun `campos faltantes producen error sin restaurar defaults ni permitir sobrescritura`() = runTest {
        withStore { store ->
            store.edit { it[stringPreferencesKey("pet_name")] = "Inti" }
            val before = store.data.first()
            val repository = DataStoreProfileRepository(store)
            assertTrue(runCatching { repository.profile.first() }.exceptionOrNull() is InvalidStoredProfileException)
            assertTrue(runCatching { repository.save(UserProfile()) }.exceptionOrNull() is InvalidStoredProfileException)
            assertEquals(before, store.data.first())
        }
    }

    @Test
    fun `version futura enum desconocido y zona invalida no se reemplazan por defaults`() = runTest {
        withStore { store ->
            val repository = DataStoreProfileRepository(store)
            repository.save(UserProfile())
            val valid = store.data.first()
            store.edit { it[intPreferencesKey("schema_version")] = 99 }
            assertTrue(runCatching { repository.profile.first() }.exceptionOrNull() is InvalidStoredProfileException)

            store.updateData { valid }
            store.edit { it[stringPreferencesKey("avatar")] = "INVENTADO" }
            assertTrue(runCatching { repository.profile.first() }.exceptionOrNull() is InvalidStoredProfileException)

            store.updateData { valid }
            store.edit { it[stringPreferencesKey("zone_id")] = "Zona/Inventada" }
            assertTrue(runCatching { repository.profile.first() }.exceptionOrNull() is InvalidStoredProfileException)
        }
    }

    @Test
    fun `propaga errores de lectura y corrupcion sin emitir un perfil nuevo`() = runTest {
        listOf(IOException("Fallo de lectura"), CorruptionException("Datos ilegibles")).forEach { failure ->
            val repository = DataStoreProfileRepository(FailingStore(failure))
            assertSame(failure, runCatching { repository.profile.first() }.exceptionOrNull())
        }
    }

    @Test
    fun `propaga error de escritura y no confirma exito`() = runTest {
        val failure = IOException("Fallo de escritura")
        val repository = DataStoreProfileRepository(FailingStore(failure))
        assertSame(failure, runCatching { repository.save(UserProfile()) }.exceptionOrNull())
    }

    private suspend fun withStore(block: suspend (DataStore<Preferences>) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(temporaryFolder.root, "perfil.preferences_pb")
        try {
            block(createJvmStore(file, scope))
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    // El FileStorage de la variante Android usa File.renameTo, que no sustituye un
    // destino existente en Windows. Para el host JVM usamos el almacenamiento real
    // Okio/NIO (movimiento atómico con REPLACE_EXISTING), sin cambiar el serializador
    // de preferencias ni el repositorio. La variante Android se prueba en instrumentación.
    private fun createJvmStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            storage = OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = PreferencesSerializer,
                producePath = { file.absolutePath.toPath() },
            ),
            scope = scope,
        )

    private class FailingStore(private val failure: IOException) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw failure }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw failure
    }
}
