package pe.studyplanner.aipet.domain.focus
import org.junit.Test
import org.junit.Assert.*
class FocusTimerTest {
 @Test fun pauseAndResumeKeepOnlyRemainingTime() {
  val timer=FocusTimer(remainingMillis=60_000).start(1000)
  assertEquals(30_000L,timer.remaining(31_000))
  val paused=timer.pause(31_000);assertNull(paused.deadline)
  assertEquals(30_000L,paused.remaining(90_000))
  assertEquals(120_000L,paused.start(90_000).deadline)
 }
 @Test fun expiredTimeNeverBecomesNegative() {assertEquals(0L,FocusTimer(deadline=1000).remaining(2000))}
 @Test fun repeatedStartDoesNotExtendDeadline() {val timer=FocusTimer().start(1000);assertEquals(timer,timer.start(4000))}
 @Test fun restoredTimerCalculatesFromSavedDeadline() {val original=FocusTimer().start(1000);val restored=original.copy();assertEquals(1_440_000L,restored.remaining(61_000))}
}
