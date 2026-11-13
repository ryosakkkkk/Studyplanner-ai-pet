package pe.studyplanner.aipet

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.withTransaction
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.*
import java.util.UUID
import pe.studyplanner.aipet.data.academic.*
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.alerts.*
import pe.studyplanner.aipet.domain.planner.*

internal class AlertTestClock(var current:Instant=Instant.parse("2026-10-20T13:00:00Z")):Clock() {
    override fun instant()=current
    override fun getZone():ZoneId=ZoneOffset.UTC
    override fun withZone(zone:ZoneId):Clock=this
}
internal class AlertTestPlatform:AlertPlatform {
    var exact=true;var notify=true;var failSchedule=false;var failPost=false;var uptime=1000L;var bootCount=1
    val scheduled=mutableMapOf<String,AlertEntity>();val posted=mutableListOf<AlertEntity>();val cancelled=mutableListOf<String>()
    override fun boot()=bootCount
    override fun elapsed()=uptime
    override fun capability(d:AlertDefinition,fire:AlertFire)=AlertCapability(notify,exact,"denegado en fixture")
    override fun cancel(scope:String,id:String){scheduled.remove("$scope/$id");cancelled+="$scope/$id"}
    override fun cancelNotification(scope:String,id:String) {}
    override fun schedule(row:AlertEntity,d:AlertDefinition,fire:AlertFire){check(!failSchedule){"Fallo inducido de scheduler"};scheduled["${row.scope}/${row.id}"]=row}
    override fun post(row:AlertEntity,d:AlertDefinition,fire:AlertFire,token:String,canComplete:Boolean){check(!failPost){"Fallo inducido al publicar"};posted+=row}
}

class AlertRepositoryTest {
    private lateinit var context:Context;private lateinit var db:AcademicDatabase;private lateinit var name:String
    private lateinit var service:AlertService;private lateinit var academic:RoomAcademicRepository
    private val clock=AlertTestClock();private val platform=AlertTestPlatform()
    private val scope=DatasetScope.PERSONAL
    @Before fun prepare() = runBlocking {
        context=ApplicationProvider.getApplicationContext();name="alert-${UUID.randomUUID()}.db";open()
        db.academicDao().insertProfile(ProfileEntity(studentName="QA",petName="Kiro",avatar="FOX",zoneId="America/Lima",weekStartsOn="MONDAY",theme="LIGHT",onboardingCompleted=true,petVisible=true,preferencesRevision=0,legacyImportVersion=1))
    }
    private fun open(){db=AcademicDatabase.open(context,name);service=AlertService(db,platform,clock);academic=RoomAcademicRepository(db,clock)}
    @After fun clean(){db.close();context.deleteDatabase(name)}
    private fun alarm(at:Instant=clock.instant().plusSeconds(120))=AlertDefinition(kind=AlertKind.ALARM,title="Alarma QA",schedule=AlertSchedule.Local(at.atZone(ZoneId.of("America/Lima")).toLocalDate(),at.atZone(ZoneId.of("America/Lima")).toLocalTime(),"America/Lima"),precision=AlertPrecision.EXACT)
    private suspend fun task(target:DatasetScope=scope)=academic.saveActivity(target,AcademicActivity(kind=ActivityKind.TASK,title="Tarea QA",time=ActivityTime.Timed(clock.instant().plusSeconds(3600),zoneId="America/Lima")))
    private suspend fun linked(task:AcademicActivity,target:DatasetScope=scope):AlertEntity {
        val d=AlertDefinition(kind=AlertKind.REMINDER,title="Aviso vinculado",schedule=AlertSchedule.Linked(task.id,10,"America/Lima"),precision=AlertPrecision.EXACT)
        return service.save(target,d,expectedAcademicRevision=academic.snapshot(target).revision)
    }
    private suspend fun row(id:String,target:DatasetScope=scope)=requireNotNull(db.alertDao().get(target.name,id))
    private fun advance(seconds:Long){clock.current=clock.current.plusSeconds(seconds);platform.uptime+=seconds*1000}
    private suspend inline fun <reified T:Throwable> fails(crossinline action:suspend()->Unit){try{action();fail("Debió fallar")}catch(e:Throwable){if(e !is T) throw e}}

    @Test fun v2MigrationPreservesAllThirteenTablesAndAddsEmptyAlertTables()=runBlocking {
        db.close();context.deleteDatabase(name);val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
        InstrumentationRegistry.getInstrumentation().context.assets.open("migrations/fase3-v2.db").use{input->file.outputStream().use(input::copyTo)}
        val tables=listOf("dataset_revision","course","activity","task_detail","exam_detail","study_detail","recurrence_rule","recurrence_exception","local_profile","planner_preferences","planner_availability","study_plan_link","plan_receipt")
        val before=SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY).use{raw->assertEquals(2,raw.version);tables.associateWith{t->raw.rawQuery("SELECT * FROM $t ORDER BY rowid",null).use{c->buildList{while(c.moveToNext())add((0 until c.columnCount).map{if(c.isNull(it))null else c.getString(it)})}}}}
        open();val sql=db.openHelper.writableDatabase;assertEquals(5,sql.version)
        val after=tables.associateWith{t->sql.query("SELECT * FROM $t ORDER BY rowid").use{c->buildList{while(c.moveToNext())add((0 until c.columnCount).map{if(c.isNull(it))null else c.getString(it)})}}}
        assertEquals(before,after);assertTrue(db.alertDao().all().isEmpty());assertTrue(db.alertDao().outbox().isEmpty())
        sql.query("SELECT count(*) FROM sqlite_master WHERE type='trigger'").use{assertTrue(it.moveToFirst());assertEquals(45,it.getInt(0))}
        sql.query("PRAGMA foreign_key_check").use{assertEquals(0,it.count)}
    }
    @Test fun deniedExactNeverSilentlyDegradesAndGrantRecovers()=runBlocking {
        platform.exact=false;val d=alarm();val saved=service.save(scope,d)
        assertEquals("NEEDS_EXACT",saved.state);assertTrue(platform.scheduled.isEmpty())
        platform.exact=true;service.reconcile();assertEquals("EXACT",row(d.id).state);assertEquals(1,platform.scheduled.size)
    }
    @Test fun deniedNotificationsPreserveAgendaAndCanRecover()=runBlocking {
        val task=task();platform.notify=false;val saved=linked(task);assertEquals("NEEDS_NOTIFICATIONS",saved.state)
        assertEquals(task,academic.snapshot(scope).activities.single());assertTrue(platform.scheduled.isEmpty())
        platform.notify=true;service.reconcile();assertEquals("EXACT",row(saved.id).state)
    }
    @Test fun flexibleIsExplicitAndPersistentAcrossReopen()=runBlocking {
        platform.exact=false;val d=alarm().copy(precision=AlertPrecision.FLEXIBLE);val saved=service.save(scope,d)
        assertEquals("FLEXIBLE",saved.state);db.close();open();assertEquals(saved,row(d.id));assertEquals(d,AlertCodec.decode(row(d.id).definition))
    }
    @Test fun platformFailureRetainsOutboxForLaterRecovery()=runBlocking {
        platform.failSchedule=true;val d=alarm();assertEquals("FAILED",service.save(scope,d).state);assertEquals(1,db.alertDao().outbox().size)
        db.close();open();platform.failSchedule=false;service.reconcile(false);assertEquals("EXACT",row(d.id).state);assertTrue(db.alertDao().outbox().isEmpty())
    }
    @Test fun failedDatabaseCommitCreatesNeitherAlertNorOsSchedule()=runBlocking {
        val failing=AlertService(db,platform,clock,beforeSaveCommit={error("Fallo antes de commit")})
        fails<IllegalStateException>{failing.save(scope,alarm())};assertTrue(db.alertDao().all().isEmpty());assertTrue(db.alertDao().outbox().isEmpty());assertTrue(platform.scheduled.isEmpty())
    }
    @Test fun parentEditInvalidatesOldReceiverAndSchedulesNewDeadline()=runBlocking {
        val task=task();val saved=linked(task)
        academic.saveActivity(scope,task.copy(time=ActivityTime.Timed(clock.instant().plusSeconds(7200),zoneId="America/Lima")))
        val pending=row(saved.id);assertTrue(pending.generation>saved.generation);assertEquals("PENDING",pending.state);assertEquals(1,db.alertDao().outbox().size)
        service.fire(scope.name,saved.id,saved.generation);assertTrue(platform.posted.isEmpty())
        service.reconcile(false);assertEquals(clock.millis()+6600_000,row(saved.id).nextAt)
    }
    @Test fun parentDeletionCancelsAndNeverResurrectsWhenIdIsRecreated()=runBlocking {
        val task=task();val saved=linked(task);academic.deleteActivity(scope,task.id,task.version)
        assertFalse(row(saved.id).enabled);service.reconcile(false);assertEquals("DISABLED",row(saved.id).state);assertTrue(platform.scheduled.isEmpty())
        academic.saveActivity(scope,task.copy(version=0));service.reconcile();assertFalse(row(saved.id).enabled)
    }
    @Test fun staleEditsAndChangedLinkedPreviewAreRejected()=runBlocking {
        val d=alarm();val saved=service.save(scope,d);service.save(scope,d.copy(title="Nuevo"),saved.generation)
        fails<IllegalStateException>{service.save(scope,d,saved.generation)}
        val task=task();val revision=academic.snapshot(scope).revision;academic.saveActivity(scope,task.copy(title="Cambiada"))
        val linked=AlertDefinition(kind=AlertKind.REMINDER,title="Revisión antigua",schedule=AlertSchedule.Linked(task.id,0,"America/Lima"))
        fails<IllegalStateException>{service.save(scope,linked,expectedAcademicRevision=revision)}
    }
    @Test fun duplicateReceiverAndSnoozeActionProduceOnlyOnePublicationAndNewSchedule()=runBlocking {
        val saved=service.save(scope,alarm());advance(120);service.fire(scope.name,saved.id,saved.generation);service.fire(scope.name,saved.id,saved.generation)
        assertEquals(1,platform.posted.size);val fired=row(saved.id);assertEquals("POSTED",fired.state)
        assertTrue(service.action(scope.name,saved.id,fired.deliveryToken!!,"SNOOZE"));assertFalse(service.action(scope.name,saved.id,fired.deliveryToken,"SNOOZE"))
        val snoozed=row(saved.id);assertEquals("EXACT",snoozed.state);assertEquals(clock.millis()+600_000,snoozed.nextAt)
        advance(600);service.fire(scope.name,saved.id,snoozed.generation);assertEquals(2,platform.posted.size)
    }
    @Test fun completeActionChangesRealTaskAndCancelsEveryRelatedAlert()=runBlocking {
        val task=task();val a=linked(task);val b=linked(task);advance(3000);service.fire(scope.name,a.id,a.generation)
        val fired=row(a.id);assertTrue(service.action(scope.name,a.id,fired.deliveryToken!!,"COMPLETE"))
        assertEquals(ActivityStatus.COMPLETED,academic.snapshot(scope).activities.single().status)
        assertFalse(row(a.id).enabled);assertFalse(row(b.id).enabled);assertTrue(platform.scheduled.isEmpty())
        assertFalse(service.action(scope.name,a.id,fired.deliveryToken,"COMPLETE"))
    }
    @Test fun cancelledOrEditedAlertRejectsOldNotificationActions()=runBlocking {
        val saved=service.save(scope,alarm());advance(120);service.fire(scope.name,saved.id,saved.generation);val fired=row(saved.id)
        service.setEnabled(scope,saved.id,fired.generation,false)
        assertFalse(service.action(scope.name,saved.id,fired.deliveryToken!!,"SNOOZE"));assertTrue(platform.scheduled.isEmpty())
    }
    @Test fun uncertainPublicationIsDurableAndNeverBlindlyRetried()=runBlocking {
        val saved=service.save(scope,alarm());platform.failPost=true;advance(120);service.fire(scope.name,saved.id,saved.generation)
        assertEquals("UNCERTAIN",row(saved.id).state);assertEquals("UNCERTAIN",db.alertDao().deliveries(scope.name).single().result)
        platform.failPost=false;db.close();open();service.reconcile();service.fire(scope.name,saved.id,saved.generation)
        assertTrue(platform.posted.isEmpty());assertEquals("UNCERTAIN",row(saved.id).state)
    }
    @Test fun recoveryOfPastOneShotMarksMissedWithoutBurst()=runBlocking {
        val saved=service.save(scope,alarm());advance(1200);platform.bootCount=2;service.reconcile()
        assertEquals("MISSED",row(saved.id).state);assertTrue(platform.posted.isEmpty());assertTrue(platform.scheduled.isEmpty())
    }
    @Test fun processStartupRecoveryDoesNotStealDueAlarmFromReceiver()=runBlocking {
        val saved=service.save(scope,alarm());advance(120)
        service.reconcile(true);assertEquals("EXACT",row(saved.id).state)
        service.fire(scope.name,saved.id,saved.generation);assertEquals(1,platform.posted.size)
    }
    @Test fun flexibleReceiverCanArriveTwentyMinutesLateWithoutBeingStolenByRecovery()=runBlocking {
        val saved=service.save(scope,alarm().copy(precision=AlertPrecision.FLEXIBLE));advance(1320)
        service.reconcile(true);assertEquals("FLEXIBLE",row(saved.id).state)
        service.fire(scope.name,saved.id,saved.generation);assertEquals(1,platform.posted.size)
    }
    @Test fun excessivelyLateReceiverIsRecordedWithoutPublishing()=runBlocking {
        val saved=service.save(scope,alarm().copy(precision=AlertPrecision.FLEXIBLE));advance(7500)
        service.fire(scope.name,saved.id,saved.generation)
        assertTrue(platform.posted.isEmpty());assertEquals("MISSED",row(saved.id).state)
        assertEquals("MISSED",db.alertDao().deliveries(scope.name).single().result)
    }
    @Test fun resetDemoCancelsIndependentAndLinkedAlertsWithoutTouchingPersonal()=runBlocking {
        val personal=service.save(scope,alarm());val demo=service.save(DatasetScope.DEMO,alarm());val task=task(DatasetScope.DEMO);val linked=linked(task,DatasetScope.DEMO)
        academic.resetDemo(LocalDate.of(2026,10,20));service.reconcile(false)
        assertEquals(personal,row(personal.id));assertFalse(row(demo.id,DatasetScope.DEMO).enabled);assertFalse(row(linked.id,DatasetScope.DEMO).enabled)
        assertEquals(1,platform.scheduled.size)
    }
    @Test fun relativeSnoozeSurvivesWallChangeAndRebootUsesAnchor()=runBlocking {
        val d=alarm().copy(schedule=AlertSchedule.Relative(clock.instant().plusSeconds(120),platform.uptime+120_000,platform.bootCount,"America/Lima"))
        val saved=service.save(scope,d);clock.current=clock.current.plusSeconds(3600);platform.uptime+=60_000;service.reconcile()
        assertEquals(clock.millis()+60_000,row(saved.id).nextAt)
        platform.bootCount=2;service.reconcile();assertEquals("MISSED",row(saved.id).state)
    }
    @Test fun weeklyAlarmSchedulesOneNextOccurrenceAfterPublication()=runBlocking {
        val d=alarm().copy(schedule=AlertSchedule.Local(LocalDate.of(2026,10,20),LocalTime.of(8,2),"America/Lima",setOf(DayOfWeek.TUESDAY)))
        val saved=service.save(scope,d);advance(120);service.fire(scope.name,saved.id,saved.generation)
        assertEquals(1,platform.posted.size);assertEquals("EXACT",row(saved.id).state);assertEquals(clock.instant().plusSeconds(7*86400).toEpochMilli(),row(saved.id).nextAt)
    }
    @Test fun recurrenceExceptionCancelsOriginalOccurrenceAndRecomputes()=runBlocking {
        val rule=WeeklyRule(setOf(DayOfWeek.TUESDAY),LocalTime.of(10,0),LocalTime.of(11,0),"America/Lima",LocalDate.of(2026,10,20))
        val series=academic.saveActivity(scope,AcademicActivity(kind=ActivityKind.CLASS,title="Clase",time=ActivityTime.Weekly(rule)))
        val alert=linked(series);academic.putException(scope,RecurrenceException(series.id,LocalDate.of(2026,10,20)),series.version)
        service.reconcile(false);assertEquals(Instant.parse("2026-10-27T14:50:00Z").toEpochMilli(),row(alert.id).nextAt)
    }
    @Test fun acceptedReplanInvalidatesLinkedSessionAlarmInSameTransaction()=runBlocking {
        val planner=RoomPlannerRepository(db,clock)
        val goal=academic.saveActivity(scope,AcademicActivity(kind=ActivityKind.TASK,title="Objetivo",estimateMinutes=50,time=ActivityTime.DateOnly(LocalDate.of(2026,10,22))))
        val settings=planner.saveSettings(scope,PlannerSettings(windows=listOf(Availability(DayOfWeek.TUESDAY,LocalTime.of(18,0),LocalTime.of(20,0)))))
        val request=PlanRequest(LocalDate.of(2026,10,20),1,listOf(PlanGoal(goal.id,50)))
        val receipt=planner.apply(planner.propose(scope,request));val session=academic.snapshot(scope).activities.single{it.id==receipt.sessionIds.single()}
        val alert=linked(session);assertEquals(Instant.parse("2026-10-20T22:50:00Z").toEpochMilli(),alert.nextAt)
        planner.saveSettings(scope,settings.copy(windows=listOf(Availability(DayOfWeek.TUESDAY,LocalTime.of(19,0),LocalTime.of(20,0)))))
        planner.apply(planner.propose(scope,request.copy(movableSessionIds=setOf(session.id))))
        assertEquals("PENDING",row(alert.id).state);assertTrue(row(alert.id).generation>alert.generation)
        service.reconcile(false);assertEquals(Instant.parse("2026-10-20T23:50:00Z").toEpochMilli(),row(alert.id).nextAt)
    }
}
