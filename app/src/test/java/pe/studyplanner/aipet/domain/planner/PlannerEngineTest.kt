package pe.studyplanner.aipet.domain.planner

import java.time.*
import kotlin.random.Random
import org.junit.Test
import org.junit.Assert.*
import pe.studyplanner.aipet.domain.academic.*

class PlannerEngineTest {
    private val zone = "America/Lima"
    private val date = LocalDate.of(2026, 10, 20)
    private val now = date.minusDays(1).atTime(8, 0).atZone(ZoneId.of(zone)).toInstant()
    private fun at(day: LocalDate = date, h: Int, m: Int = 0) = day.atTime(h,m).atZone(ZoneId.of(zone)).toInstant()
    private fun task(id: String = "task", minutes: Int = 200, time: ActivityTime = ActivityTime.DateOnly(date.plusDays(3))) =
        AcademicActivity(id = id, kind = ActivityKind.TASK, title = id, estimateMinutes = minutes, time = time, version = 1)
    private fun block(id: String, h: Int, end: Int, kind: ActivityKind = ActivityKind.CLASS, fixed: Boolean = true, day: LocalDate = date) =
        AcademicActivity(id = id, title = id, kind = kind, time = ActivityTime.Timed(at(day,h), at(day,end), zone), fixed = fixed, estimateMinutes = if (kind == ActivityKind.STUDY) (end-h)*60 else null, version = 1)
    private fun settings(windows: List<Availability> = listOf(Availability(DayOfWeek.TUESDAY,LocalTime.of(18,0),LocalTime.of(20,0)),Availability(DayOfWeek.THURSDAY,LocalTime.of(18,0),LocalTime.of(20,0)))) = PlannerSettings(1, windows)
    private fun context(activities: List<AcademicActivity> = listOf(task()), s: PlannerSettings = settings(), links: List<StudyLink> = emptyList()) = PlanContext(AcademicSnapshot(DatasetScope.DEMO,1,activities=activities),s,links,zone,0)
    private fun plan(c: PlanContext = context(), minutes: Int? = 200, moved: Set<String> = emptySet(), splittable: Boolean = true) =
        PlannerEngine.generate(c,PlanRequest(date,7,listOf(PlanGoal("task",minutes,splittable)),moved),now)
    private fun newMinutes(p: PlanDraft) = p.changes.sumOf { it.after?.estimateMinutes ?: 0 }
    private inline fun fails(body: () -> Unit) { try { body(); fail("Debió rechazar la entrada") } catch (_: IllegalArgumentException) {} }

    @Test fun feasible200UsesFourSessionsAndTenMinuteBreaks() {
        val p=plan();assertEquals(200,newMinutes(p));assertEquals(4,p.changes.size);assertEquals(0,p.goals.single().pending)
        val spans=p.changes.map { it.after!!.time as ActivityTime.Timed }.sortedBy { it.start }
        assertEquals(50,Duration.between(spans[0].start,spans[0].end).toMinutes().toInt())
        assertEquals(10,Duration.between(spans[0].end,spans[1].start).toMinutes().toInt())
    }
    @Test fun insufficient480Returns300PendingWithoutOverbooking() {
        val s=settings(listOf(DayOfWeek.TUESDAY,DayOfWeek.THURSDAY).map { Availability(it,LocalTime.of(18,0),LocalTime.of(19,40)) })
        val p=plan(context(s=s),480);assertEquals(180,newMinutes(p));assertEquals(300,p.goals.single().pending)
    }
    @Test fun unknownEffortIsUnplannedAndNeverDefaulted() { val p=plan(minutes=null);assertTrue(p.changes.isEmpty());assertNull(p.goals.single().pending);assertNull(p.goals.single().required) }
    @Test fun fixedClassIsNeverMovedOrOverlapped() {
        val fixed=block("fixed",18,20);val c=context(listOf(task(),fixed));val p=plan(c)
        assertTrue(p.changes.none { it.before?.id==fixed.id });assertTrue(p.changes.mapNotNull { it.after?.time as? ActivityTime.Timed }.all { it.start>=at(date.plusDays(2),18) });assertEquals(fixed,c.snapshot.activities.last())
    }
    @Test fun protectedSleepWinsOverPreferredWindow() {
        val s=settings().copy(sleepStart=LocalTime.of(18,0),sleepEnd=LocalTime.of(20,0));val p=plan(context(s=s));assertEquals(200,p.goals.single().pending);assertTrue(p.changes.isEmpty())
    }
    @Test fun existingStudyCountsAgainstDailyMaximum() {
        val c=context(listOf(task(),block("manual",15,17,ActivityKind.STUDY)),settings().copy(dailyMinutes=120));val p=plan(c)
        assertEquals(100,newMinutes(p));assertTrue(p.changes.all { (it.after!!.time as ActivityTime.Timed).start.atZone(ZoneId.of(zone)).toLocalDate()==date.plusDays(2) })
    }
    @Test fun adjacentFixedIntervalIsAllowed() {
        val p=plan(context(listOf(task(),block("fixed",17,18))),50);assertEquals(at(h=18),(p.changes.single().after!!.time as ActivityTime.Timed).start)
    }
    @Test fun weeklyExceptionFreesOnlyItsOccurrence() {
        val weekly=AcademicActivity(id="weekly",kind=ActivityKind.CLASS,title="Clase",version=1,time=ActivityTime.Weekly(WeeklyRule(setOf(DayOfWeek.TUESDAY,DayOfWeek.THURSDAY),LocalTime.of(18,0),LocalTime.of(20,0),zone,date)))
        val c=context(listOf(task(),weekly));val empty=plan(c);assertEquals(0,newMinutes(empty))
        val except=c.copy(snapshot=c.snapshot.copy(exceptions=listOf(RecurrenceException("weekly",date))))
        val p=plan(except);assertEquals(100,newMinutes(p));assertTrue(p.changes.all { (it.after!!.time as ActivityTime.Timed).start.atZone(ZoneId.of(zone)).toLocalDate()==date })
    }
    @Test fun examDateOnlyUsesConservativePreviousDayCutoff() {
        val exam=task().copy(kind=ActivityKind.EXAM,time=ActivityTime.DateOnly(date.plusDays(2)))
        val p=plan(context(listOf(exam)));assertEquals(100,newMinutes(p));assertEquals(100,p.goals.single().pending)
    }
    @Test fun pastDeadlineDoesNotCreatePastOrFutureSessions() { val p=plan(context(listOf(task(time=ActivityTime.Timed(now.minusSeconds(1),zoneId=zone)))));assertEquals(200,p.goals.single().pending);assertTrue(p.changes.isEmpty()) }
    @Test fun sameInputsAndOrderIndependentSnapshotProduceSameDraft() {
        val c=context(listOf(task(),block("elsewhere",10,11)));assertEquals(plan(c),plan(c.copy(snapshot=c.snapshot.copy(activities=c.snapshot.activities.reversed()))))
    }
    @Test fun indivisibleGoalCannotBePartiallyAssigned() { val p=plan(minutes=80,splittable=false);assertTrue(p.changes.isEmpty());assertEquals(80,p.goals.single().pending) }
    @Test fun completedGoalCannotBePlanned() { fails { plan(context(listOf(task().copy(status=ActivityStatus.COMPLETED)))) } }
    @Test fun fixedStudyCannotBeAuthorizedForMovement() {
        fails { plan(context(listOf(task(),block("study",18,19,ActivityKind.STUDY)),links=listOf(StudyLink("study","task","old"))),moved=setOf("study")) }
    }
    @Test fun unchangedAuthorizedSessionsKeepTheirIdsAndNeedNoWrites() {
        val original=plan(minutes=50).changes.single().after!!.copy(id="existing",version=4)
        val p=plan(context(listOf(task(),original),links=listOf(StudyLink("existing","task","old"))),50,setOf("existing"))
        assertTrue(p.changes.isEmpty());assertEquals(0,p.goals.single().pending)
    }
    @Test fun replanMovesOnlyAuthorizedFutureSessionAndPreservesItsId() {
        val session=block("study",18,19,ActivityKind.STUDY,false)
        val fixed=block("new-fixed",18,20)
        val p=plan(context(listOf(task(),session,fixed),links=listOf(StudyLink("study","task","old"))),60,setOf("study"))
        assertEquals("study",p.changes.first().after!!.id);assertEquals(session,p.changes.first().before);assertTrue(p.changes.all { it.before?.id != fixed.id })
    }
    @Test fun validLinkedSessionsAreCreditedWithoutAssumingUnlinkedEffort() {
        val session=block("linked",14,15,ActivityKind.STUDY,false)
        val p=plan(context(listOf(task(),session),links=listOf(StudyLink("linked","task","old"))),100)
        assertEquals(60,p.goals.single().credited);assertEquals(40,newMinutes(p))
        assertEquals(0,plan(context(listOf(task(),session)),100).goals.single().credited)
    }
    @Test fun missedPastSessionIsNotCreditedButCompletedPastOneIs() {
        val old=block("old",10,11,ActivityKind.STUDY,false,date.minusDays(2))
        val c=context(listOf(task(),old),links=listOf(StudyLink("old","task","oldplan")))
        assertEquals(0,plan(c).goals.single().credited)
        assertEquals(60,plan(c.copy(snapshot=c.snapshot.copy(activities=listOf(task(),old.copy(status=ActivityStatus.COMPLETED))))).goals.single().credited)
    }
    @Test fun overlappingAvailabilityIsNotDoubleCounted() {
        val s=settings(listOf(Availability(DayOfWeek.TUESDAY,LocalTime.of(18,0),LocalTime.of(20,0)),Availability(DayOfWeek.TUESDAY,LocalTime.of(19,0),LocalTime.of(20,0))))
        assertEquals(100,newMinutes(plan(context(s=s))))
    }
    @Test fun dstGapIsRejectedInsteadOfSilentlyShifted() {
        val d=LocalDate.of(2026,3,8);val c=context(s=settings(listOf(Availability(DayOfWeek.SUNDAY,LocalTime.of(2,30),LocalTime.of(4,0))))).copy(zoneId="America/New_York")
        try { PlannerEngine.generate(c,PlanRequest(d,1,listOf(PlanGoal("task",50))),Instant.parse("2026-03-07T00:00:00Z"));fail() } catch (_: DateTimeException) {}
    }
    @Test fun horizonsAndDuplicateGoalsAreBounded() {
        fails { PlannerEngine.generate(context(),PlanRequest(date,32,listOf(PlanGoal("task",50))),now) }
        fails { PlannerEngine.generate(context(),PlanRequest(date,7,listOf(PlanGoal("task",50),PlanGoal("task",20))),now) }
    }
    @Test fun earliestDeadlinePrecedesHigherPriorityLaterDeadline() {
        val early=task("early",100,ActivityTime.DateOnly(date)).copy(priority=Priority.LOW)
        val late=task("late",100).copy(priority=Priority.HIGH)
        val p=PlannerEngine.generate(context(listOf(late,early)),PlanRequest(date,1,listOf(PlanGoal("late",100),PlanGoal("early",100))),now)
        assertEquals(100,p.goals.first { it.goalId=="early" }.assigned);assertEquals(0,p.goals.first { it.goalId=="late" }.assigned)
    }
    @Test fun sixtyMinutesSplitsIntoLegalBlocksInsteadOfLeavingTen() {
        val p=plan(minutes=60);assertEquals(60,newMinutes(p));assertTrue(p.changes.all { it.after!!.estimateMinutes!!>=25 })
    }
    @Test fun randomizedPlansRespectHardConstraintsAcross300Fixtures() {
        val random=Random(3187)
        repeat(300) {
            val required=random.nextInt(25,501);val cap=random.nextInt(50,181)
            val fixed=block("fixed",random.nextInt(16,19),20)
            val p=plan(context(listOf(task(),fixed),settings().copy(dailyMinutes=cap)),required)
            val times=p.changes.mapNotNull { it.after?.time as? ActivityTime.Timed }.sortedBy { it.start }
            assertEquals(required,p.goals.single().assigned+p.goals.single().pending!!)
            assertTrue(times.all { t -> t.start>=now && t.end!!<=at(date.plusDays(4),0) && !(t.start<at(h=20)&&t.end!!>(fixed.time as ActivityTime.Timed).start) })
            times.zipWithNext().forEach { (a,b) -> assertTrue(a.end!!<=b.start);assertTrue(Duration.between(a.end,b.start).toMinutes()>=10) }
            times.groupBy { t -> t.start.atZone(ZoneId.of(zone)).toLocalDate() }.values.forEach { ts -> assertTrue(ts.sumOf { t->Duration.between(t.start,t.end).toMinutes() }<=cap) }
        }
    }
    @Test fun newEarlierBlockDoesNotStealIdFromUnchangedLaterBlock() {
        val first=plan(minutes=200)
        val sessions=first.changes.mapNotNull { it.after }.sortedBy { (it.time as ActivityTime.Timed).start }
        val tuesdayLater=sessions[1].copy(id="tuesday-later",version=2)
        val thursdayFirst=sessions[2].copy(id="thursday-first",version=2)
        val c=context(listOf(task(),tuesdayLater,thursdayFirst),links=listOf(StudyLink(tuesdayLater.id,"task","old"),StudyLink(thursdayFirst.id,"task","old")))
        val p=plan(c,150,setOf(tuesdayLater.id,thursdayFirst.id))
        assertEquals(1,p.changes.size);assertNull(p.changes.single().before);assertEquals(150,p.goals.single().assigned)
    }
}
