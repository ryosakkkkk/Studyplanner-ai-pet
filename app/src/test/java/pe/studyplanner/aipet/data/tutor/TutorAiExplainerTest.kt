package pe.studyplanner.aipet.data.tutor

import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import pe.studyplanner.aipet.data.ai.AiTransport
import pe.studyplanner.aipet.data.ai.ProxyReply
import pe.studyplanner.aipet.domain.agent.AgentContext
import pe.studyplanner.aipet.domain.tutor.TutorRegistry

class TutorAiExplainerTest {
    private val topic = TutorRegistry.requireTopic("ecuaciones-lineales")

    private class FakeTransport(private val reply: () -> ProxyReply) : AiTransport {
        val prompts = mutableListOf<String>()
        val contexts = mutableListOf<AgentContext>()
        override suspend fun check(): String = "ok"
        override suspend fun chat(text: String, context: AgentContext): ProxyReply {
            prompts += text
            contexts += context
            return reply()
        }
    }

    private fun explainer(transport: AiTransport) = TutorAiExplainer(transport) { Instant.parse("2026-10-28T20:00:00Z") }

    @Test fun promptIsOneLineNamesTheTopicAndForbidsSolvingExercises() {
        val prompt = TutorAiPrompt.build(topic)
        assertTrue(prompt.contains(topic.title))
        assertTrue(prompt.contains("no resuelvas ningún ejercicio"))
        assertTrue(prompt.contains("OTRA explicación"))
        assertFalse("sin saltos de línea ni controles", prompt.any { Character.isISOControl(it) })
        assertTrue(prompt.length < 4000)
    }

    @Test fun promptNeverCarriesAnExerciseOrAnAnswer() {
        val prompt = TutorAiPrompt.build(topic)
        val exercise = TutorRegistry.generate(topic.id, 5L, 2)
        assertFalse(prompt.contains(exercise.statement))
        assertFalse(prompt.contains(exercise.solutionSteps.last()))
    }

    @Test fun successReturnsTheCleanedText() = runBlocking {
        val transport = FakeTransport { ProxyReply("answer", "**Piensa** en una balanza.\u0007 Lo que haces a un lado, hazlo al otro.") }
        val result = explainer(transport).explain(topic, "America/Lima")
        assertEquals(AiExplanation.Success("Piensa en una balanza. Lo que haces a un lado, hazlo al otro."), result)
        assertEquals(1, transport.prompts.size)
        assertEquals("America/Lima", transport.contexts.single().zoneId)
        assertTrue("sin datos de la agenda", transport.contexts.single().snapshot.activities.isEmpty())
    }

    @Test fun aCommandReplyIsNotShownAsAnExplanation() = runBlocking {
        val result = explainer(FakeTransport { ProxyReply("command", "{\"accion\":\"crear\"}") }).explain(topic, "America/Lima")
        assertEquals(AiExplanation.Failure(TutorAiExplainer.UNUSABLE), result)
    }

    @Test fun aClarifyReplyIsNotShownAsAnExplanation() = runBlocking {
        val result = explainer(FakeTransport { ProxyReply("clarify", "¿Qué tema quieres repasar?") }).explain(topic, "America/Lima")
        assertEquals(AiExplanation.Failure(TutorAiExplainer.UNUSABLE), result)
    }

    @Test fun anEmptyReplyIsAFailure() = runBlocking {
        val result = explainer(FakeTransport { ProxyReply("answer", "  \n ") }).explain(topic, "America/Lima")
        assertEquals(AiExplanation.Failure(TutorAiExplainer.UNUSABLE), result)
    }

    @Test fun configurationErrorsKeepTheirOwnMessage() = runBlocking {
        val result = explainer(FakeTransport { throw IllegalStateException("Configura el código de acceso de tu servidor.") })
            .explain(topic, "America/Lima")
        assertEquals(AiExplanation.Failure("Configura el código de acceso de tu servidor."), result)
    }

    @Test fun anyOtherErrorBecomesTheGenericMessage() = runBlocking {
        val result = explainer(FakeTransport { throw RuntimeException("socket") }).explain(topic, "America/Lima")
        assertEquals(AiExplanation.Failure(TutorAiExplainer.GENERIC), result)
    }

    @Test fun cancellationIsNotSwallowed() {
        try {
            runBlocking { explainer(FakeTransport { throw CancellationException("cancelado") }).explain(topic, "America/Lima") }
            fail("la cancelación debe propagarse")
        } catch (_: CancellationException) {
        }
    }

    @Test fun cleanReplyRemovesMarkdownAndLimitsTheLength() {
        assertEquals("Título\n\nTexto", TutorAiPrompt.cleanReply("# Título\n\n\n\n__Texto__"))
        assertEquals(1500, TutorAiPrompt.cleanReply("a".repeat(5000)).length)
    }

    @Test fun theMandatoryLabelIsPresent() {
        assertEquals("Generado por IA — puede contener errores", TutorAiPrompt.LABEL)
    }
}
