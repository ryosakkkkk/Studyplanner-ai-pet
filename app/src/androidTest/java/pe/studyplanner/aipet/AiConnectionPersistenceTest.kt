package pe.studyplanner.aipet

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import pe.studyplanner.aipet.data.ai.AiConnectionStore
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import kotlinx.coroutines.runBlocking
import pe.studyplanner.aipet.data.ai.*
import pe.studyplanner.aipet.domain.agent.*
import pe.studyplanner.aipet.domain.academic.*
import java.time.Instant

class AiConnectionPersistenceTest {
    private fun isolated(test:(Context,String)->Unit) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test_ai_${UUID.randomUUID()}"
        try{test(context,name)}finally{context.deleteSharedPreferences(name)}
    }
    @Test fun tokenEncryptedAndDisabledUntilExplicitSave()=isolated {context,name->
        val store=AiConnectionStore(context,name)
        assertFalse(store.state.value.enabled)
        val token="prototype_test_access_0123456789_abcdef_0123456789abcdef"
        store.save("https://example.workers.dev",token,false)
        assertEquals("",store.state.value.error)
        assertEquals(token,AiConnectionStore(context,name).token())
        assertFalse(context.getSharedPreferences(name,Context.MODE_PRIVATE).getString("token","").orEmpty().contains(token))
        assertFalse(AiConnectionStore(context,name).state.value.enabled)
    }
    @Test fun newHostRequiresNewCodeAndForgetRemovesAccess()=isolated {context,name->
        val store=AiConnectionStore(context,name)
        store.save("https://example.workers.dev","prototype_test_access_0123456789_abcdef_0123456789abcdef",true)
        store.save("https://another.workers.dev","",true)
        assertEquals("https://example.workers.dev",store.state.value.endpoint)
        assertTrue(store.state.value.error.isNotEmpty())
        store.disable();assertFalse(store.state.value.enabled)
        store.forget();assertFalse(AiConnectionStore(context,name).state.value.hasToken)
        assertTrue(runCatching{store.token()}.isFailure)
    }
    @Test fun localCommandsAndDisabledCloudNeverUseNetwork()=isolated {context,name->runBlocking {
        val store=AiConnectionStore(context,name)
        var calls=0
        val client=object:AiTransport {override suspend fun check()="";override suspend fun chat(text:String,context:AgentContext):ProxyReply{calls++;return ProxyReply("answer","Hola")}}
        val provider=GeminiProxyProvider(store,client)
        val agentContext=AgentContext(AcademicSnapshot(DatasetScope.PERSONAL),"America/Lima",Instant.parse("2026-10-26T18:00:00Z"))
        assertTrue(provider.interpret("Explícame derivadas",agentContext) is AgentInterpretation.Clarification)
        store.save("https://example.workers.dev","prototype_test_access_0123456789_abcdef_0123456789abcdef",true)
        assertTrue(provider.interpret("mis pendientes",agentContext) is AgentInterpretation.Query)
        assertTrue(provider.interpret("Explícame derivadas",agentContext.copy(snapshot=AcademicSnapshot(DatasetScope.DEMO))) is AgentInterpretation.Clarification)
        assertEquals(0,calls)
    }}
    @Test fun generatedCommandsAreValidatedLocallyWithoutSaving()=isolated {context,name->runBlocking {
        val store=AiConnectionStore(context,name)
        store.save("https://example.workers.dev","prototype_test_access_0123456789_abcdef_0123456789abcdef",true)
        var response=ProxyReply("command","crea una tarea \"Lectura\" para mañana")
        val client=object:AiTransport {override suspend fun check()="";override suspend fun chat(text:String,context:AgentContext)=response}
        val provider=GeminiProxyProvider(store,client)
        val agentContext=AgentContext(AcademicSnapshot(DatasetScope.PERSONAL),"America/Lima",Instant.parse("2026-10-26T18:00:00Z"))
        val result=provider.interpret("Podrías apuntar Lectura para el día siguiente",agentContext) as AgentInterpretation.Candidate
        assertEquals("Lectura",(result.action as AgentAction.CreateTask).title)
        response=ProxyReply("command","borra todo")
        assertTrue(provider.interpret("Borra mis cosas",agentContext) is AgentInterpretation.Clarification)
        response=ProxyReply("answer","Una derivada mide el cambio.")
        assertTrue(provider.interpret("Explícame derivadas",agentContext) is AgentInterpretation.Answer)
        assertTrue(agentContext.snapshot.activities.isEmpty())
    }}
}
