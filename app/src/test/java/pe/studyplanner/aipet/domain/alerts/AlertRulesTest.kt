package pe.studyplanner.aipet.domain.alerts

import java.time.*
import org.junit.Test
import org.junit.Assert.*
import pe.studyplanner.aipet.domain.academic.*

class AlertRulesTest {
    private val now=Instant.parse("2026-10-20T13:00:00Z")
    private val date=LocalDate.of(2026,10,21)
    private val zone="America/Lima"
    private val empty=AcademicSnapshot(DatasetScope.PERSONAL)
    private fun definition(schedule:AlertSchedule,kind:AlertKind=AlertKind.ALARM)=AlertDefinition(kind=kind,title="Aviso QA",schedule=schedule)
    private fun next(d:AlertDefinition,snapshot:AcademicSnapshot=empty,at:Instant=now,boot:Int=1,elapsed:Long=1000,after:Instant?=null)=AlertRules.next(d,snapshot,at,boot,elapsed,after)
    private fun linked(time:ActivityTime,offset:Int=30,hour:LocalTime?=null):Pair<AlertDefinition,AcademicSnapshot> {
        val activity=AcademicActivity(id="source",kind=ActivityKind.EXAM,title="Examen",time=time)
        return definition(AlertSchedule.Linked(activity.id,offset,zone,hour),AlertKind.REMINDER) to empty.copy(activities=listOf(activity))
    }
    @Test fun localOneShotUsesExplicitZoneAndIsNotRepeated() {
        val d=definition(AlertSchedule.Local(date,LocalTime.of(18,0),zone))
        val fire=next(d)!!;assertEquals(Instant.parse("2026-10-21T23:00:00Z"),fire.at)
        assertNull(next(d,after=fire.occurrence))
    }
    @Test fun weeklySkipsPastAndStopsAtUntil() {
        val d=definition(AlertSchedule.Local(LocalDate.of(2026,10,19),LocalTime.of(7,0),zone,setOf(DayOfWeek.TUESDAY)))
        assertEquals(Instant.parse("2026-10-27T12:00:00Z"),next(d)!!.at)
        assertNull(next(d.copy(schedule=(d.schedule as AlertSchedule.Local).copy(until=LocalDate.of(2026,10,20)))))
    }
    @Test fun relativeIgnoresWallClockJumpWithinSameBoot() {
        val d=definition(AlertSchedule.Relative(now.plusSeconds(120),121000,1,zone))
        val changed=next(d,at=now.plusSeconds(3600),elapsed=61000)!!
        assertEquals(now.plusSeconds(3660),changed.at);assertEquals(121000L,changed.elapsedMillis)
    }
    @Test fun relativeRebootUsesAbsoluteAnchorAndReportsRecovery() {
        val d=definition(AlertSchedule.Relative(now.plusSeconds(120),121000,1,zone))
        val f=next(d,boot=2)!!;assertNull(f.elapsedMillis);assertEquals(now.plusSeconds(120),f.at);assertTrue(f.note.contains("reiniciar"))
    }
    @Test fun dateOnlyRequiresAnExplicitReminderHourWithoutChangingExam() {
        val (d,s)=linked(ActivityTime.DateOnly(date))
        try {next(d,s);fail()} catch(_:IllegalArgumentException) {}
        val fixed=d.copy(schedule=(d.schedule as AlertSchedule.Linked).copy(dateOnlyTime=LocalTime.of(9,0)))
        assertEquals(Instant.parse("2026-10-21T13:30:00Z"),next(fixed,s)!!.at)
        assertEquals(ActivityTime.DateOnly(date),s.activities.single().time)
    }
    @Test fun timedOffsetFollowsSourceInstant() {
        val (d,s)=linked(ActivityTime.Timed(now.plusSeconds(7200),now.plusSeconds(10800),zone))
        assertEquals(now.plusSeconds(5400),next(d,s)!!.at)
    }
    @Test fun weeklyLinkedReminderRespectsCancelledOccurrence() {
        val rule=WeeklyRule(setOf(DayOfWeek.WEDNESDAY),LocalTime.of(10,0),LocalTime.of(11,0),zone,date)
        val (d,s)=linked(ActivityTime.Weekly(rule))
        val changed=s.copy(exceptions=listOf(RecurrenceException("source",date)))
        assertEquals(Instant.parse("2026-10-28T14:30:00Z"),next(d,changed)!!.at)
    }
    @Test fun weeklyReplacementUsesItsActualTime() {
        val rule=WeeklyRule(setOf(DayOfWeek.WEDNESDAY),LocalTime.of(10,0),LocalTime.of(11,0),zone,date)
        val (d,s)=linked(ActivityTime.Weekly(rule))
        val replacement=AcademicActivity(id="replacement",kind=ActivityKind.PERSONAL,title="Cambio",time=ActivityTime.Timed(now.plusSeconds(3600),now.plusSeconds(7200),zone))
        val changed=s.copy(activities=s.activities+replacement,exceptions=listOf(RecurrenceException("source",date,"replacement")))
        assertEquals(now.plusSeconds(1800),next(d,changed)!!.at)
    }
    @Test fun missingCancelledCompletedOrUndatedSourceCannotSchedule() {
        val (d,s)=linked(ActivityTime.Unscheduled)
        for(snapshot in listOf(empty,s,s.copy(activities=s.activities.map { it.copy(status=ActivityStatus.COMPLETED) }),s.copy(activities=s.activities.map { it.copy(status=ActivityStatus.CANCELLED) }))) {
            try {next(d,snapshot);fail()} catch(_:IllegalStateException) {} catch(_:IllegalArgumentException) {}
        }
    }
    @Test fun dstGapAndOverlapAreNeverSilentlyShifted() {
        for((day,hour) in listOf("2026-03-08" to "02:30","2026-11-01" to "01:30")) {
            val d=definition(AlertSchedule.Local(LocalDate.parse(day),LocalTime.parse(hour),"America/New_York"))
            try {next(d);fail()} catch(_:DateTimeException) {}
        }
    }
    @Test fun silenceIsDefaultOnlyForReminderAndUsesSelectedZone() {
        val schedule=AlertSchedule.Local(date,LocalTime.of(23,0),zone)
        assertTrue(next(definition(schedule,AlertKind.REMINDER))!!.silentNight)
        assertFalse(next(definition(schedule))!!.silentNight)
        assertFalse(next(definition(schedule,AlertKind.REMINDER).copy(quietEnabled=false))!!.silentNight)
        assertTrue(AlertRules.inQuiet(LocalTime.of(22,0),LocalTime.of(22,0),LocalTime.of(7,0)))
        assertFalse(AlertRules.inQuiet(LocalTime.of(7,0),LocalTime.of(22,0),LocalTime.of(7,0)))
    }
    @Test fun invalidDefinitionIsRejectedBeforeAnyPlatformOperation() {
        val base=definition(AlertSchedule.Local(date,LocalTime.NOON,zone))
        for(d in listOf(base.copy(title=" "),base.copy(snoozeMinutes=0),base.copy(quietEnd=base.quietStart),base.copy(id="not-a-uuid"))) {
            try {AlertRules.validate(d);fail()} catch(_:IllegalArgumentException) {}
        }
    }
}
