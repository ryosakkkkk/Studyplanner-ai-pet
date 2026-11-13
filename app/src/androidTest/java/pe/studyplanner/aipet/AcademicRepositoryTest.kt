package pe.studyplanner.aipet

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.DayOfWeek
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import pe.studyplanner.aipet.data.academic.*
import pe.studyplanner.aipet.data.profile.*
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.profile.*

/** Real file-backed Room and DataStore fixtures; none of these records are participant data. */
@RunWith(AndroidJUnit4::class)
class AcademicRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AcademicDatabase
    private lateinit var repository: RoomAcademicRepository
    private lateinit var databaseName: String
    private val personal = DatasetScope.PERSONAL
    private val demo = DatasetScope.DEMO
    private val date = LocalDate.of(2026, 10, 20)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "academic-test-${UUID.randomUUID()}.db"
        database = AcademicDatabase.open(context, databaseName)
        repository = RoomAcademicRepository(database)
    }

    @After fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun realDatabaseReopenPreservesDatesRelationshipsAndCompletion() = runBlocking {
        val course = repository.saveCourse(personal, Course(name = "Curso de prueba"))
        val created = repository.saveActivity(personal, AcademicActivity(kind = ActivityKind.TASK,
            title = "Entrega de prueba", courseId = course.id, time = ActivityTime.DateOnly(date)))
        repository.saveActivity(personal, created.copy(status = ActivityStatus.COMPLETED))
        database.close()
        database = AcademicDatabase.open(context, databaseName)
        repository = RoomAcademicRepository(database)
        val snapshot = repository.snapshot(personal)
        assertEquals(1, snapshot.courses.size)
        assertEquals(course.id, snapshot.activities.single().courseId)
        assertEquals(ActivityTime.DateOnly(date), snapshot.activities.single().time)
        assertEquals(ActivityStatus.COMPLETED, snapshot.activities.single().status)
        assertEquals(created.version + 1, snapshot.activities.single().version)
    }

    @Test fun scopeForeignKeysRejectCrossScopeLinksAndRollback() = runBlocking {
        val foreign = repository.saveCourse(demo, Course(name = "Solo demo"))
        val revision = repository.snapshot(personal).revision
        expectFailure<IllegalArgumentException> {
            repository.saveActivity(personal, AcademicActivity(kind = ActivityKind.TASK, title = "Inválida", courseId = foreign.id))
        }
        assertTrue(repository.snapshot(personal).activities.isEmpty())
        assertEquals(revision, repository.snapshot(personal).revision)
        // The SQLite FK itself protects scope even if a caller bypasses the repository.
        expectFailure<SQLiteConstraintException> {
            database.academicDao().insertActivity(ActivityEntity("PERSONAL", "fk-fixture", "TASK", "Prueba FK", foreign.id,
                "NONE", null, null, null, null, "TODO", "", true, 1, 0, 0))
        }
    }

    @Test fun archivedCourseKeepsHistoryAndDeletionRequiresNoUsage() = runBlocking {
        val course = repository.saveCourse(personal, Course(name = "Historia"))
        val task = repository.saveActivity(personal, AcademicActivity(kind = ActivityKind.TASK,
            title = "Conservar", courseId = course.id))
        val archived = repository.archiveCourse(personal, course.id, course.version)
        assertTrue(archived.archived)
        assertEquals(course.id, repository.snapshot(personal).activities.single().courseId)
        expectFailure<IllegalArgumentException> { repository.deleteCourse(personal, course.id, archived.version) }
        repository.deleteActivity(personal, task.id, task.version)
        repository.deleteCourse(personal, course.id, archived.version)
        assertTrue(repository.snapshot(personal).courses.isEmpty())
    }

    @Test fun staleWriteDoesNotOverwriteCommittedActivity() = runBlocking {
        val activity = repository.saveActivity(personal, AcademicActivity(kind = ActivityKind.TASK, title = "Inicial"))
        val changed = repository.saveActivity(personal, activity.copy(title = "Confirmada"))
        val revision = repository.snapshot(personal).revision
        expectFailure<StaleAcademicWriteException> { repository.saveActivity(personal, activity.copy(title = "Obsoleta")) }
        assertEquals(changed, repository.snapshot(personal).activities.single())
        assertEquals(revision, repository.snapshot(personal).revision)
    }

    @Test fun sqliteTriggersRejectInvalidTemporalBranchesAndDetailDurations() = runBlocking {
        expectFailure<SQLiteConstraintException> {
            database.academicDao().insertActivity(ActivityEntity("PERSONAL", "bad-time", "SPORT", "Intervalo inválido", null,
                "TIMED", null, 2000, 1000, "America/Lima", "TODO", "", true, 1, 0, 0))
        }
        val activity = repository.saveActivity(personal, AcademicActivity(kind = ActivityKind.TASK, title = "Duración de prueba"))
        expectFailure<SQLiteConstraintException> {
            database.academicDao().putTask(TaskDetailEntity("PERSONAL", activity.id, "MEDIUM", -5))
        }
        assertNull(repository.snapshot(personal).activities.single().estimateMinutes)
    }

    @Test fun missingNormalizedDetailIsReportedInsteadOfDefaulted() = runBlocking {
        database.academicDao().insertActivity(ActivityEntity("PERSONAL", "missing-task-detail", "TASK", "Falta detalle", null,
            "NONE", null, null, null, null, "TODO", "", true, 1, 0, 0))
        expectFailure<IllegalStateException> { repository.snapshot(personal) }
        assertEquals(1, database.academicDao().activities("PERSONAL").size)
        assertTrue(database.academicDao().tasks("PERSONAL").isEmpty())
    }

    @Test fun occurrenceExceptionIsScopedAndPreservesSeries() = runBlocking {
        val rule = WeeklyRule(setOf(date.dayOfWeek), LocalTime.of(8,0), LocalTime.of(9,0), "America/Lima", date, date.plusWeeks(3))
        val series = repository.saveActivity(personal, AcademicActivity(kind = ActivityKind.CLASS,
            title = "Serie de prueba", time = ActivityTime.Weekly(rule)))
        repository.putException(personal, RecurrenceException(series.id, date), series.version)
        assertEquals(1, repository.snapshot(personal).exceptions.size)
        val changed = repository.snapshot(personal).activities.single()
        assertEquals(series.time, changed.time)
        assertEquals(series.version + 1, changed.version)
        expectFailure<IllegalArgumentException> { repository.putException(demo, RecurrenceException(series.id, date), changed.version) }
        expectFailure<IllegalArgumentException> { repository.putException(personal, RecurrenceException(series.id, date.plusDays(1)), changed.version) }
        expectFailure<StaleAcademicWriteException> { repository.removeException(personal, series.id, date, series.version) }
        expectFailure<IllegalArgumentException> { repository.saveActivity(personal, changed.copy(time = ActivityTime.Weekly(rule.copy(weekdays = setOf(date.plusDays(1).dayOfWeek))))) }
        assertEquals(1, repository.snapshot(personal).exceptions.size)
        repository.removeException(personal, series.id, date, changed.version)
        assertTrue(repository.snapshot(personal).exceptions.isEmpty())
    }

    @Test fun recurringInvalidDstTimeIsRejectedWithoutCommit() = runBlocking {
        val gapDate = LocalDate.of(2026, 3, 8)
        val gap = WeeklyRule(setOf(DayOfWeek.SUNDAY), LocalTime.of(2,30), LocalTime.of(3,30),
            "America/New_York", gapDate, gapDate)
        expectFailure<DateTimeException> {
            repository.saveActivity(personal, AcademicActivity(kind = ActivityKind.CLASS, title = "Hora inexistente", time = ActivityTime.Weekly(gap)))
        }
        val overlapDate = LocalDate.of(2026, 11, 1)
        val overlap = gap.copy(startTime = LocalTime.of(1,30), endTime = LocalTime.of(2,30), startDate = overlapDate, until = overlapDate)
        expectFailure<DateTimeException> {
            repository.saveActivity(personal, AcademicActivity(kind = ActivityKind.CLASS, title = "Hora ambigua", time = ActivityTime.Weekly(overlap)))
        }
        assertTrue(repository.snapshot(personal).activities.isEmpty())
        assertEquals(0L, repository.snapshot(personal).revision)
    }

    @Test fun demoSeedAndResetAreAtomicIdempotentAndPreservePersonal() = runBlocking {
        val course = repository.saveCourse(personal, Course(name = "Real local de prueba"))
        repository.saveActivity(personal, AcademicActivity(kind = ActivityKind.TASK, title = "Personal", courseId = course.id))
        val before = repository.snapshot(personal)
        repository.seedDemo(date)
        val seeded = repository.snapshot(demo)
        (seeded.courses.map { it.id } + seeded.activities.map { it.id }).forEach { id ->
            assertEquals(id, UUID.fromString(id).toString())
        }
        repository.seedDemo(date.plusDays(1))
        assertEquals(seeded, repository.snapshot(demo))
        repository.resetDemo(date.plusDays(1))
        val afterReset = repository.snapshot(demo)
        expectFailure<StaleAcademicWriteException> { repository.saveActivity(demo, seeded.activities.first().copy(title = "Confirmación anterior al reset")) }
        expectFailure<StaleAcademicWriteException> { repository.saveCourse(demo, seeded.courses.first().copy(name = "Curso anterior al reset")) }
        assertEquals(afterReset, repository.snapshot(demo))
        assertEquals(before, repository.snapshot(personal))
        assertEquals(11, repository.snapshot(demo).activities.size)
        assertEquals(seeded.revision + 1, repository.snapshot(demo).revision)
        database.close()
        database = AcademicDatabase.open(context, databaseName)
        repository = RoomAcademicRepository(database)
        assertEquals(11, repository.snapshot(demo).activities.size)
        assertEquals(before, repository.snapshot(personal))
    }

    @Test fun realDataStoreMigrationIsVerifiedAndRepeatedImportUsesRoom() = runBlocking {
        withLegacy { legacy, file ->
            val profile = UserProfile(studentName = "QA Room", petName = "Migrado", zoneId = "America/Lima",
                weekStartsOn = DayOfWeek.SUNDAY, theme = ThemeMode.DARK, onboardingCompleted = true, petVisible = false)
            legacy.save(profile)
            val bytes = file.readBytes()
            var backups = 0
            val migrated = RoomProfileRepository(database, legacy, preserveLegacyBackup = { backups++ })
            assertEquals(profile, migrated.profile.first())
            assertEquals(1, backups)
            val updated = profile.copy(petName = "Solo Room")
            migrated.save(updated)
            assertArrayEquals(bytes, file.readBytes())
            database.close()
            database = AcademicDatabase.open(context, databaseName)
            val unreadableLegacy = object : ProfileRepository {
                override val profile = kotlinx.coroutines.flow.flow<UserProfile> { error("El respaldo no debe releerse tras el commit") }
                override suspend fun save(profile: UserProfile) = error("No hay escritor legado")
            }
            assertEquals(updated, RoomProfileRepository(database, unreadableLegacy).profile.first())
            assertEquals(1, database.academicDao().profile()?.legacyImportVersion)
            assertEquals(1L, database.academicDao().profile()?.preferencesRevision)
        }
    }

    @Test fun interruptedImportRollsBackAndRetryKeepsLegacyBytes() = runBlocking {
        withLegacy { legacy, file ->
            val profile = UserProfile(petName = "Reintento", onboardingCompleted = true)
            legacy.save(profile)
            val bytes = file.readBytes()
            val interrupted = RoomProfileRepository(database, legacy, beforeImportCommit = { error("Interrupción inyectada") })
            expectFailure<IllegalStateException> { interrupted.ensureImported() }
            assertNull(database.academicDao().profile())
            assertArrayEquals(bytes, file.readBytes())
            assertEquals(profile, RoomProfileRepository(database, legacy).profile.first())
        }
    }

    @Test fun invalidLegacyRecordIsNotReplacedByDefaults() = runBlocking {
        val file = File(context.filesDir, "invalid-profile-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            store.edit { it[intPreferencesKey("schema_version")] = 1 }
            val bytes = file.readBytes()
            val legacy = DataStoreProfileRepository(store)
            expectFailure<InvalidStoredProfileException> { RoomProfileRepository(database, legacy).ensureImported() }
            assertNull(database.academicDao().profile())
            assertArrayEquals(bytes, file.readBytes())
        } finally { scope.cancel(); file.delete() }
    }

    @Test fun incompleteOrInterruptedBackupIsPreservedAndRetryPublishesVerifiedCopy() {
        val directory = File(context.cacheDir, "backup-fixture-${UUID.randomUUID()}")
        try {
            val original = File(directory, "datastore/profile_v1.preferences_pb")
            original.parentFile!!.mkdirs()
            val bytes = "Respaldo ficticio completo".toByteArray()
            original.writeBytes(bytes)
            val backup = File(directory, "legacy-profile-backup/profile_v1.preferences_pb")
            backup.parentFile!!.mkdirs()
            backup.writeBytes(byteArrayOf(1,2))
            try { LegacyProfileBackup.preserve(directory, beforePublish = { error("Interrupción inyectada") }); fail() }
            catch (_: IllegalStateException) { /* Retry must not be blocked by either incomplete file. */ }
            assertArrayEquals(byteArrayOf(1,2), backup.readBytes())
            LegacyProfileBackup.preserve(directory)
            assertArrayEquals(bytes, original.readBytes())
            assertArrayEquals(bytes, backup.readBytes())
            assertTrue(backup.parentFile!!.listFiles()!!.any { it.name.startsWith("profile_v1.previous-") && it.readBytes().contentEquals(byteArrayOf(1,2)) })
            val names = backup.parentFile!!.list()!!.toSet()
            LegacyProfileBackup.preserve(directory)
            assertEquals(names, backup.parentFile!!.list()!!.toSet())
        } finally { directory.deleteRecursively() }
    }

    private suspend fun withLegacy(block: suspend (DataStoreProfileRepository, File) -> Unit) {
        val file = File(context.filesDir, "legacy-profile-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            block(DataStoreProfileRepository(store), file)
        } finally { scope.cancel(); file.delete() }
    }

    private suspend inline fun <reified T : Throwable> expectFailure(block: suspend () -> Unit) {
        try { block(); fail("Se esperaba ${T::class.java.simpleName}") }
        catch (error: Throwable) { if (error !is T) throw error }
    }
}
