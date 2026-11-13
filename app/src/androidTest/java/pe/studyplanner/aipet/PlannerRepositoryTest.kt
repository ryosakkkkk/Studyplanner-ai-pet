package pe.studyplanner.aipet

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import pe.studyplanner.aipet.data.academic.*
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.planner.*

class PlannerRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AcademicDatabase
    private lateinit var academic: RoomAcademicRepository
    private lateinit var planner: RoomPlannerRepository
    private lateinit var name: String
    private val clock=Clock.fixed(Instant.parse("2026-10-20T13:00:00Z"),ZoneOffset.UTC)
    private val date=LocalDate.of(2026,10,20)
    private val scope=DatasetScope.PERSONAL
    @Before fun prepare() = runBlocking {
        context=ApplicationProvider.getApplicationContext(); name="planner-test-${UUID.randomUUID()}.db"
        open()
        database.academicDao().insertProfile(ProfileEntity(studentName="QA",petName="Kiro",avatar="FOX",zoneId="America/Lima",weekStartsOn="MONDAY",theme="LIGHT",onboardingCompleted=true,petVisible=true,preferencesRevision=0,legacyImportVersion=1))
    }
    private fun open() { database=AcademicDatabase.open(context,name);academic=RoomAcademicRepository(database,clock);planner=RoomPlannerRepository(database,clock) }
    @After fun clean() { database.close();context.deleteDatabase(name) }
    private fun settings(version: Long=0)=PlannerSettings(version,DayOfWeek.entries.map { Availability(it,LocalTime.of(18,0),LocalTime.of(20,0)) })
    private suspend fun fixture(targetScope: DatasetScope=scope): AcademicActivity {
        planner.saveSettings(targetScope,settings())
        return academic.saveActivity(targetScope,AcademicActivity(kind=ActivityKind.TASK,title="Objetivo QA",estimateMinutes=100,time=ActivityTime.DateOnly(date.plusDays(3))))
    }
    private fun request(goal: AcademicActivity, moved: Set<String> = emptySet())=PlanRequest(date,7,listOf(PlanGoal(goal.id,100)),moved)
    private suspend inline fun <reified T:Throwable> fails(crossinline action:suspend ()->Unit) {
        try { action();fail("Se esperaba ${T::class.java.simpleName}") } catch(e:Throwable) { if(e !is T) throw e }
    }
    @Test fun realF2VersionOneMigratesAndPreservesAllNineTables() = runBlocking {
        database.close();context.deleteDatabase(name)
        val target=context.getDatabasePath(name);target.parentFile!!.mkdirs()
        InstrumentationRegistry.getInstrumentation().context.assets.open("migrations/fase2-v1.db").use { input->target.outputStream().use(input::copyTo) }
        val tables=listOf("dataset_revision","course","activity","task_detail","exam_detail","study_detail","recurrence_rule","recurrence_exception","local_profile")
        fun dump(db:SQLiteDatabase)=tables.associateWith { table->db.rawQuery("SELECT * FROM $table ORDER BY rowid",null).use { cursor->buildList { while(cursor.moveToNext()) add((0 until cursor.columnCount).map { if(cursor.isNull(it)) null else cursor.getString(it) }) } } }
        val before=SQLiteDatabase.openDatabase(target.path,null,SQLiteDatabase.OPEN_READONLY).use { assertEquals(1,it.version);dump(it) }
        open();assertEquals(11,academic.snapshot(DatasetScope.DEMO).activities.size)
        val db=database.openHelper.writableDatabase
        assertEquals(5,db.version)
        val after=tables.associateWith { table->db.query("SELECT * FROM $table ORDER BY rowid").use { c->buildList { while(c.moveToNext()) add((0 until c.columnCount).map { if(c.isNull(it)) null else c.getString(it) }) } } }
        assertEquals(before,after)
        db.query("PRAGMA foreign_key_check").use { assertEquals(0,it.count) }
        db.query("SELECT count(*) FROM sqlite_master WHERE type='trigger'").use { assertTrue(it.moveToFirst());assertEquals(45,it.getInt(0)) }
        assertEquals(0L,planner.load(DatasetScope.DEMO).settings.version)
        planner.saveSettings(DatasetScope.DEMO,settings())
        database.close();open();assertEquals(1L,planner.load(DatasetScope.DEMO).settings.version)
    }
    @Test fun preferencesPersistAreScopedAndRejectStaleEdits() = runBlocking {
        val saved=planner.saveSettings(scope,settings());assertEquals(1L,saved.version)
        fails<StaleAcademicWriteException> { planner.saveSettings(scope,settings()) }
        database.close();open()
        assertEquals(saved,planner.load(scope).settings)
        assertEquals(0L,planner.load(DatasetScope.DEMO).settings.version)
    }
    @Test fun proposalAndDiscardHaveNoDatabaseMutation() = runBlocking {
        val goal=fixture();val before=planner.load(scope)
        val draft=planner.propose(scope,request(goal));assertTrue(draft.changes.isNotEmpty())
        assertEquals(before,planner.load(scope))
        assertNull(database.plannerDao().receipt(scope.name,draft.id))
    }
    @Test fun applyPersistsLinksAndReceiptAcrossReopenAndReplayCreatesNothing() = runBlocking {
        val goal=fixture();val draft=planner.propose(scope,request(goal));val receipt=planner.apply(draft)
        assertFalse(receipt.replay);assertEquals(2,receipt.sessionIds.size)
        val snapshot=planner.load(scope);assertEquals(2,snapshot.links.size);assertEquals(100,snapshot.snapshot.activities.filter { it.kind==ActivityKind.STUDY }.sumOf { it.estimateMinutes!! })
        database.close();open();assertEquals(snapshot,planner.load(scope));assertEquals(100,planner.load(scope).goalDefaults[goal.id]!!.totalMinutes)
        val replay=planner.apply(draft);assertTrue(replay.replay);assertEquals(receipt.sessionIds,replay.sessionIds);assertEquals(snapshot,planner.load(scope))
    }
    @Test fun concurrentAgendaEditRejectsWholeDraft() = runBlocking {
        val goal=fixture();val draft=planner.propose(scope,request(goal))
        academic.saveActivity(scope,goal.copy(title="Cambio concurrente"));val before=planner.load(scope)
        fails<StaleAcademicWriteException> { planner.apply(draft) };assertEquals(before,planner.load(scope));assertNull(database.plannerDao().receipt(scope.name,draft.id))
    }
    @Test fun preferenceAndProfileChangesInvalidateReviewedDraft() = runBlocking {
        val goal=fixture();val first=planner.propose(scope,request(goal))
        planner.saveSettings(scope,settings(1).copy(dailyMinutes=60));fails<StaleAcademicWriteException> { planner.apply(first) }
        val second=planner.propose(scope,request(goal));val p=database.academicDao().profile()!!
        database.academicDao().updateProfile(p.copy(preferencesRevision=p.preferencesRevision+1,zoneId="America/Bogota"))
        fails<StaleAcademicWriteException> { planner.apply(second) };assertTrue(planner.load(scope).links.isEmpty())
    }
    @Test fun alteredDraftIsRecomputedAndRejectedWithoutAnyWrites() = runBlocking {
        val goal=fixture();val draft=planner.propose(scope,request(goal));val before=planner.load(scope)
        val change=draft.changes.first();val altered=draft.copy(changes=listOf(change.copy(after=change.after!!.copy(fixed=true))))
        fails<IllegalArgumentException> { planner.apply(altered) };assertEquals(before,planner.load(scope))
    }
    @Test fun interruptedCommitRollsBackSessionsLinksRevisionAndReceiptThenRetrySucceeds() = runBlocking {
        val goal=fixture();val draft=planner.propose(scope,request(goal));val before=planner.load(scope)
        val failing=RoomPlannerRepository(database,clock,beforeReceipt={ error("Interrupción inducida") })
        fails<IllegalStateException> { failing.apply(draft) }
        assertEquals(before,planner.load(scope));assertNull(database.plannerDao().receipt(scope.name,draft.id))
        assertEquals(2,planner.apply(draft).sessionIds.size)
    }
    @Test fun explicitReplanPreservesIdentityAndCannotMoveUnselectedSession() = runBlocking {
        val goal=fixture();val receipt=planner.apply(planner.propose(scope,request(goal)))
        val before=planner.load(scope);val session=before.snapshot.activities.first { it.id==receipt.sessionIds.first() }
        val keep=before.snapshot.activities.first { it.id==receipt.sessionIds.last() }
        academic.saveActivity(scope,AcademicActivity(kind=ActivityKind.CLASS,title="Clase fija nueva",time=session.time))
        val draft=planner.propose(scope,request(goal,setOf(session.id)))
        assertTrue(draft.changes.any { it.before?.id==session.id && it.after?.id==session.id });assertTrue(draft.changes.none { it.before?.id==keep.id })
        planner.apply(draft);assertEquals(keep,planner.load(scope).snapshot.activities.first { it.id==keep.id })
    }
    @Test fun demoResetWithLinkedSessionsPreservesPersonalAndDoesNotResurrectOldReceipt() = runBlocking {
        val personal=fixture();val demo=fixture(DatasetScope.DEMO)
        val draft=planner.propose(DatasetScope.DEMO,request(demo));planner.apply(draft)
        val before=planner.load(scope);academic.resetDemo(date);val afterReset=planner.load(DatasetScope.DEMO)
        assertEquals(before,planner.load(scope));assertEquals(personal,before.snapshot.activities.single());assertEquals(11,afterReset.snapshot.activities.size);assertTrue(afterReset.links.isEmpty())
        assertTrue(planner.apply(draft).replay);assertEquals(afterReset,planner.load(DatasetScope.DEMO))
    }
    @Test fun expiredProposalCannotCreateSessions() = runBlocking {
        val goal=fixture();val draft=planner.propose(scope,request(goal));val before=planner.load(scope)
        val later=RoomPlannerRepository(database,Clock.fixed(clock.instant().plusSeconds(31*60),ZoneOffset.UTC))
        fails<IllegalArgumentException> { later.apply(draft) };assertEquals(before,planner.load(scope))
    }
    @Test fun linkedGoalCannotBeDeletedOrConvertedUntilItsSessionsAreRemoved() = runBlocking {
        val goal=fixture();val receipt=planner.apply(planner.propose(scope,request(goal)))
        fails<IllegalArgumentException> { academic.deleteActivity(scope,goal.id,goal.version) }
        fails<IllegalArgumentException> { academic.saveActivity(scope,goal.copy(kind=ActivityKind.PERSONAL,estimateMinutes=null,time=ActivityTime.Timed(clock.instant().plusSeconds(3600),clock.instant().plusSeconds(7200),"America/Lima"))) }
        val snapshot=academic.snapshot(scope)
        for(id in receipt.sessionIds) { val session=snapshot.activities.first { it.id==id };academic.deleteActivity(scope,id,session.version) }
        academic.deleteActivity(scope,goal.id,goal.version);assertTrue(academic.snapshot(scope).activities.isEmpty())
    }
    @Test fun explicitEffortForTaskAndExamSurvivesProcessStyleReopenWithoutChangingSource() = runBlocking {
        val goal=fixture()
        planner.saveSettings(scope,settings(1).copy(windows=listOf(Availability(DayOfWeek.TUESDAY,LocalTime.of(18,0),LocalTime.of(20,0)))))
        val exam=academic.saveActivity(scope,AcademicActivity(kind=ActivityKind.EXAM,title="Examen QA",time=ActivityTime.DateOnly(date.plusDays(1))))
        val request=PlanRequest(date,7,listOf(PlanGoal(goal.id,200),PlanGoal(exam.id,50)))
        val draft=planner.propose(scope,request);assertTrue(draft.goals.sumOf { it.pending ?: 0 }>0);planner.apply(draft)
        database.close();open();val recovered=planner.load(scope)
        assertEquals(200,recovered.goalDefaults[goal.id]!!.totalMinutes);assertEquals(50,recovered.goalDefaults[exam.id]!!.totalMinutes)
        assertEquals(goal,recovered.snapshot.activities.first { it.id==goal.id });assertEquals(exam,recovered.snapshot.activities.first { it.id==exam.id })
        val next=planner.propose(scope,request);assertEquals(draft.goals.first { it.goalId==goal.id }.pending,next.goals.first { it.goalId==goal.id }.pending)
    }
}
