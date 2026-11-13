package pe.studyplanner.aipet.data.tutor

import java.time.Instant
import kotlinx.coroutines.CancellationException
import pe.studyplanner.aipet.data.ai.AiTransport
import pe.studyplanner.aipet.domain.academic.AcademicSnapshot
import pe.studyplanner.aipet.domain.academic.DatasetScope
import pe.studyplanner.aipet.domain.agent.AgentContext
import pe.studyplanner.aipet.domain.tutor.Topic

/** Resultado de pedir otra explicación a la IA. */
sealed interface AiExplanation {
    data class Success(val text: String) : AiExplanation
    data class Failure(val message: String) : AiExplanation
}

/** Texto que se envía a la IA y limpieza de lo que responde. Es de Kotlin puro para probarlo en la JVM. */
object TutorAiPrompt {
    /** Rótulo obligatorio junto a cualquier texto generado por la IA. */
    const val LABEL = "Generado por IA — puede contener errores"

    private const val MAX_EXPLANATION_CHARS = 2500
    private const val MAX_REPLY_CHARS = 1500

    /**
     * Petición en una sola línea (el servidor rechaza caracteres de control): título del tema, la explicación local y
     * la orden de dar OTRA explicación sin resolver ejercicios. No incluye respuestas del estudiante ni su agenda.
     */
    fun build(topic: Topic): String {
        val explanation = topic.explanation.joinToString(" ").replace(Regex("\\s+"), " ").take(MAX_EXPLANATION_CHARS)
        val subject = if (topic.area.math) "matemática" else topic.area.label
        return "Eres un tutor de $subject para estudiantes de secundaria que se preparan para un examen de admisión. " +
            "Tema: «${topic.title}». Explicación actual del tema: $explanation " +
            "Tarea: escribe OTRA explicación del mismo tema, con palabras distintas y una analogía sencilla, " +
            "en español de Perú, en un solo párrafo de máximo 120 palabras. " +
            "Reglas: no resuelvas ningún ejercicio ni problema, no des resultados numéricos de ejercicios, " +
            "no uses viñetas, tablas ni Markdown. Responde solo con la explicación."
    }

    /** Quita caracteres de control y marcas de Markdown, y recorta a un largo razonable. */
    fun cleanReply(text: String): String {
        val noControl = text.filter { it == '\n' || !Character.isISOControl(it) }
        val noMarkdown = noControl.replace("**", "").replace("__", "").replace("`", "").replace(Regex("(?m)^\\s*#+\\s*"), "")
        return noMarkdown.replace(Regex("\\n{3,}"), "\n\n").trim().take(MAX_REPLY_CHARS)
    }
}

/**
 * Pide a la IA otra explicación de un tema, usando el servidor de IA que el estudiante configuró en "Conectar IA".
 * La práctica local no depende de esto: sin conexión configurada, la interfaz oculta el botón.
 */
class TutorAiExplainer(
    private val transport: AiTransport,
    private val now: () -> Instant = { Instant.now() },
) {
    suspend fun explain(topic: Topic, zoneId: String): AiExplanation {
        return try {
            val context = AgentContext(AcademicSnapshot(DatasetScope.PERSONAL), zoneId, now())
            val reply = transport.chat(TutorAiPrompt.build(topic), context)
            val clean = TutorAiPrompt.cleanReply(reply.text)
            when {
                // Solo una respuesta tipo «answer» es una explicación; una orden o una pregunta del servidor no lo es.
                reply.kind != "answer" || clean.isBlank() -> AiExplanation.Failure(UNUSABLE)
                else -> AiExplanation.Success(clean)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            AiExplanation.Failure(e.message?.takeIf { it.isNotBlank() } ?: GENERIC)
        } catch (_: Exception) {
            AiExplanation.Failure(GENERIC)
        }
    }

    companion object {
        const val GENERIC = "No pudimos obtener otra explicación. La práctica sigue funcionando sin conexión."
        const val UNUSABLE = "La IA respondió algo que no podemos mostrar como explicación. Intenta de nuevo."
    }
}
