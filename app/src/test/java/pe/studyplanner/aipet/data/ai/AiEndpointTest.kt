package pe.studyplanner.aipet.data.ai

import org.junit.Assert.*
import org.junit.Test

class AiEndpointTest {
    @Test fun acceptsOnlyHttpsOrigin(){assertEquals("https://example.workers.dev",AiConnectionStore.validateEndpoint(" https://example.workers.dev/ "))}
    @Test fun rejectsSecretInUrlAndRedirectEndpoints(){
        listOf("http://example.com","https://me:secret@example.com","https://example.com/chat","https://example.com/?key=secret","https://example.com/#secret","https://generativelanguage.googleapis.com","https://example.com:8080").forEach {
            assertTrue(it,runCatching{AiConnectionStore.validateEndpoint(it)}.isFailure)
        }
    }
}
