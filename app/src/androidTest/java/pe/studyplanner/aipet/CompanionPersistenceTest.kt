package pe.studyplanner.aipet

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.Assert.*
import pe.studyplanner.aipet.data.companion.CompanionStore
import java.util.UUID

class CompanionPersistenceTest {
 private fun isolated(check:(Context,String,CompanionStore)->Unit) {
  val context=ApplicationProvider.getApplicationContext<Context>();val name="qa-companion-${UUID.randomUUID()}"
  try {check(context,name,CompanionStore(context,name))} finally {context.deleteSharedPreferences(name)}
 }
 @Test fun habitsAndDailyMarksSurviveRecreation()=isolated { context,name,store->
  store.addHabit("Leer");val id=store.state.value.habits.single().id
  store.toggleHabit(id,"2026-10-25");store.toggleHabit(id,"2026-10-26")
  val restored=CompanionStore(context,name);assertEquals(setOf("2026-10-25","2026-10-26"),restored.state.value.habits.single().dates)
  restored.toggleHabit(id,"2026-10-26");assertEquals(setOf("2026-10-25"),CompanionStore(context,name).state.value.habits.single().dates)
 }
 @Test fun pausedTimerSurvivesRecreation()=isolated { context,name,store->
  val now=System.currentTimeMillis()+60_000;store.toggleTimer(now);store.toggleTimer(now+60_000)
  val restored=CompanionStore(context,name).state.value.timer
  assertNull(restored.deadline);assertEquals(24*60_000L,restored.remainingMillis)
 }
 @Test fun completionIsRecordedOnceAndResetKeepsHistory()=isolated { context,name,store->
  val now=System.currentTimeMillis()+60_000;store.toggleTimer(now);val deadline=store.state.value.timer.deadline!!
  store.finishDue(deadline);store.finishDue(deadline+1000)
  assertEquals(1,store.state.value.blocks.size);assertTrue(store.state.value.timer.breakMode)
  val restored=CompanionStore(context,name);assertEquals(25,restored.state.value.blocks.single().minutes)
  restored.resetTimer(45);assertEquals(1,restored.state.value.blocks.size);assertEquals(45,restored.state.value.timer.workMinutes)
 }
 @Test fun pausingExpiredTimerRecordsCompletionAndResetKeepsBreak()=isolated { context,name,store->
  val now=System.currentTimeMillis()+60_000;store.toggleTimer(now);val end=store.state.value.timer.deadline!!
  store.toggleTimer(end+1);assertEquals(1,store.state.value.blocks.size);assertTrue(store.state.value.timer.breakMode)
  store.toggleTimer(end+2);store.toggleTimer(end+60_000);store.resetTimer(25)
  assertTrue(store.state.value.timer.breakMode);assertEquals(5*60_000L,store.state.value.timer.remainingMillis)
 }
 @Test fun malformedDataIsNotOverwritten()=isolated { context,name,store->
  val preferences=context.getSharedPreferences(name,Context.MODE_PRIVATE);preferences.edit().putString("data","broken{").commit()
  val broken=CompanionStore(context,name);assertTrue(broken.state.value.error.isNotBlank());broken.addHabit("No guardar")
  assertEquals("broken{",preferences.getString("data",null))
 }
}
