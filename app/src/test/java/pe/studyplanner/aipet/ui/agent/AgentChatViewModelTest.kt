package pe.studyplanner.aipet.ui.agent

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import pe.studyplanner.aipet.domain.academic.DatasetScope
import pe.studyplanner.aipet.domain.agent.*
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AgentChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: ViewModelStore

    @Before fun prepare() {
        Dispatchers.setMain(dispatcher)
        store = ViewModelStore()
    }
    @After fun clean() {
        store.clear()
        Dispatchers.resetMain()
    }
    private fun model(gateway: FixtureGateway) = AgentChatViewModel(gateway).also { store.put("agent", it) }

    @Test fun failedHistoryReadBlocksSendingAndPreferenceChangesUntilStateIsKnown() = runTest {
        val gateway = FixtureGateway().apply { historyFailure = true }
        val model = model(gateway)
        runCurrent()

        assertFalse(model.state.value.ready)
        assertFalse(model.state.value.busy)
        assertTrue(model.state.value.error.contains("historial"))
        model.send("mis pendientes")
        model.historyEnabled(false)
        runCurrent()

        assertTrue(gateway.persistentHistory)
        assertEquals(0, gateway.prepareCalls)
        assertTrue(gateway.recordAttempts.isEmpty())
        assertEquals(0, gateway.preferenceWrites)
        assertFalse(model.state.value.ready)
    }

    @Test fun retryLoadsTheExistingHistoryPreferenceBeforeAllowingNewRequests() = runTest {
        val previous = AgentMessage(role = "assistant", text = "Mensaje conservado anteriormente")
        val gateway = FixtureGateway().apply {
            historyFailure = true
            savedMessages += previous
        }
        val model = model(gateway)
        runCurrent()
        assertFalse(model.state.value.ready)

        gateway.historyFailure = false
        model.retryLoad()
        assertTrue(model.state.value.busy)
        runCurrent()
        assertTrue(model.state.value.ready)
        assertTrue(model.state.value.historyEnabled)
        assertEquals(listOf(previous), model.state.value.messages)
        assertEquals("", model.state.value.error)
        assertEquals(2, gateway.historyReads)

        model.send("mis pendientes")
        runCurrent()
        assertEquals(1, gateway.prepareCalls)
        assertEquals(listOf("user", "assistant"), gateway.recordAttempts.map { it.second.role })
        assertTrue(gateway.recordAttempts.all { it.first == DatasetScope.PERSONAL })
        assertEquals(3, gateway.savedMessages.size)
        assertTrue(model.state.value.ready)
        assertFalse(model.state.value.busy)
        assertEquals(0, gateway.preferenceWrites)
    }

    @Test fun committedReceiptSurvivesHistoryWriteFailureAndCannotBeConfirmedTwice() = runTest {
        val gateway = FixtureGateway().apply { reviewNext = true; assistantWriteFailure = true }
        val model = model(gateway)
        runCurrent()
        model.send("crea una tarea \"Tarea confirmada\"")
        runCurrent()
        val proposal = requireNotNull(model.state.value.proposal)
        assertEquals(0, gateway.confirmCalls)

        model.confirm()
        model.confirm() // A second tap while the first confirmation is in flight.
        runCurrent()
        val receipt = requireNotNull(model.state.value.receipt)
        assertEquals(requireNotNull(gateway.committedReceipt), receipt)
        assertEquals(proposal.id, receipt.proposalId)
        assertEquals("COMMITTED", receipt.status)
        assertNull(model.state.value.proposal)
        assertFalse(model.state.value.busy)
        assertTrue(model.state.value.error.contains("operación está registrada"))
        assertTrue(model.state.value.error.contains("historial"))
        assertTrue(model.state.value.error.contains("comprobante"))
        assertTrue(model.state.value.messages.any { it.role == "assistant" && it.text == receipt.message })
        assertEquals(listOf("user", "assistant"), gateway.recordAttempts.map { it.second.role })
        assertEquals(listOf("user"), gateway.savedMessages.map { it.role })

        model.confirm() // No proposal remains even though persisting its message failed.
        runCurrent()
        assertEquals(1, gateway.confirmCalls)
        assertEquals(receipt, model.state.value.receipt)
    }

    /** JVM-only fixture; production execution and Room effects are covered by AgentFlowTest. */
    private class FixtureGateway : AgentGateway {
        override val mode = "FIXTURE"
        var persistentHistory = true
        var historyFailure = false
        var assistantWriteFailure = false
        var reviewNext = false
        var prepareCalls = 0
        var confirmCalls = 0
        var preferenceWrites = 0
        var historyReads = 0
        val savedMessages = mutableListOf<AgentMessage>()
        val recordAttempts = mutableListOf<Pair<DatasetScope, AgentMessage>>()
        private val now = Instant.now()
        private var proposal: AgentProposal? = null
        var committedReceipt: AgentReceipt? = null
            private set

        override suspend fun prepare(scope: DatasetScope, text: String, requestId: String): AgentTurn {
            prepareCalls++
            if (!reviewNext) return AgentTurn.Reply("No hay tareas en este espacio.")
            val created = AgentProposal(requestId = requestId, scope = scope,
                action = AgentAction.CreateTask("Tarea confirmada"), agendaRevision = 0, profileRevision = 0,
                createdAt = now, expiresAt = now.plusSeconds(600), payloadHash = "0".repeat(64), preview = "Crear una tarea")
            proposal = created
            return AgentTurn.Review(created)
        }
        override suspend fun confirm(proposalId: String): AgentReceipt {
            confirmCalls++
            val reviewed = requireNotNull(proposal)
            check(reviewed.id == proposalId)
            return AgentReceipt(reviewed.requestId, reviewed.id, reviewed.scope, "create_task",
                listOf(UUID.randomUUID().toString()), "Tarea guardada y verificada.", "COMMITTED").also {
                committedReceipt = it
                proposal = null
            }
        }
        override fun discard(proposalId: String) {
            if (proposal?.id == proposalId) proposal = null
        }
        override suspend fun history(scope: DatasetScope): List<AgentMessage> {
            historyReads++
            check(!historyFailure) { "Fallo de lectura inducido" }
            return savedMessages.toList()
        }
        override suspend fun historyEnabled(scope: DatasetScope) = persistentHistory
        override suspend fun setHistoryEnabled(scope: DatasetScope, enabled: Boolean) {
            preferenceWrites++
            persistentHistory = enabled
        }
        override suspend fun clearHistory(scope: DatasetScope) { savedMessages.clear() }
        override suspend fun recordMessage(scope: DatasetScope, message: AgentMessage) {
            recordAttempts += scope to message
            check(!assistantWriteFailure || message.role != "assistant") { "Fallo al guardar el mensaje posterior al commit" }
            if (persistentHistory) savedMessages += message
        }
    }
}
