package pe.studyplanner.aipet.ui.planner

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import java.time.*
import org.junit.*
import org.junit.Assert.*
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.planner.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private lateinit var store:ViewModelStore
    @Before fun before() { Dispatchers.setMain(dispatcher);store=ViewModelStore() }
    @After fun after() { store.clear();Dispatchers.resetMain() }
    private fun model(repo:FixtureRepository)=PlannerViewModel(repo,DatasetScope.PERSONAL).also { store.put("planner",it) }
    @Test fun noSuccessBeforeCommitAndRepeatedTapIsBlocked()=runTest {
        val repo=FixtureRepository();val model=model(repo);runCurrent();model.propose(repo.request);runCurrent()
        model.apply();model.apply();runCurrent();assertTrue(model.busy);assertEquals("",model.message);assertEquals(1,repo.applyCalls)
        repo.gate.complete(Unit);runCurrent();assertFalse(model.busy);assertNull(model.draft);assertTrue(model.message.startsWith("Plan guardado"))
    }
    @Test fun failedCommitKeepsDraftAndNeverClaimsSaved()=runTest {
        val repo=FixtureRepository().apply { failure=true };val model=model(repo);runCurrent();model.propose(repo.request);runCurrent()
        model.apply();runCurrent();assertNotNull(model.draft);assertTrue(model.message.startsWith("No se pudo completar"));assertFalse(model.busy)
        model.discard();assertNull(model.draft);assertEquals(1,repo.applyCalls)
    }
    @Test fun successfulCommitFollowedByReadFailureStillReportsConfirmedWrite()=runTest {
        val repo=FixtureRepository().apply { failReadAfterApply=true;gate.complete(Unit) };val model=model(repo);runCurrent();model.propose(repo.request);runCurrent()
        model.apply();runCurrent();assertNull(model.draft);assertTrue(model.message.startsWith("Plan guardado"));assertTrue(model.message.contains("No se pudo recargar"));assertEquals(1,repo.applyCalls)
    }
    private class FixtureRepository:PlannerRepository {
        val gate=CompletableDeferred<Unit>();var failure=false;var failReadAfterApply=false;var applied=false;var applyCalls=0
        private val date=LocalDate.of(2026,10,20)
        private val context=PlanContext(AcademicSnapshot(DatasetScope.PERSONAL,1,activities=listOf(AcademicActivity(id="goal",kind=ActivityKind.TASK,title="QA",time=ActivityTime.DateOnly(date.plusDays(2)),version=1))),PlannerSettings(1,listOf(Availability(DayOfWeek.TUESDAY,LocalTime.of(18,0),LocalTime.of(20,0)))),emptyList(),"America/Lima",0)
        val request=PlanRequest(date,7,listOf(PlanGoal("goal",50)))
        override suspend fun load(scope:DatasetScope):PlanContext { if(applied&&failReadAfterApply) error("Fallo de lectura");return context }
        override suspend fun saveSettings(scope:DatasetScope,settings:PlannerSettings)=settings
        override suspend fun propose(scope:DatasetScope,request:PlanRequest)=PlannerEngine.generate(context,request,Instant.parse("2026-10-20T13:00:00Z"))
        override suspend fun apply(draft:PlanDraft):PlanReceipt { applyCalls++;if(failure) error("Fallo inducido");gate.await();applied=true;return PlanReceipt(draft.id,listOf("session"),Instant.parse("2026-10-20T13:00:00Z")) }
    }
}
