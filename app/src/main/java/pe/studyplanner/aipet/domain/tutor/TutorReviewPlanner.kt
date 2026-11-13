package pe.studyplanner.aipet.domain.tutor

import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import pe.studyplanner.aipet.domain.academic.AcademicActivity
import pe.studyplanner.aipet.domain.academic.ActivityKind
import pe.studyplanner.aipet.domain.academic.ActivityTime
import pe.studyplanner.aipet.domain.calendar.StrictLocalTime

/**
 * Convierte la propuesta de repaso del tutor en una sesión de estudio de la agenda. No guarda nada: la interfaz
 * solo llama al repositorio después de que el estudiante confirme.
 */
object TutorReviewPlanner {
    /** Horas de inicio que se ofrecen para el repaso. */
    val START_HOURS: List<Int> = listOf(16, 18, 20)

    const val DEFAULT_START_HOUR = 18
    const val DEFAULT_MINUTES = 30

    private const val MAX_MINUTES = 240
    private const val MAX_TITLE = 160
    private const val MAX_NOTES = 2000
    private const val SECONDS_PER_MINUTE = 60L

    /**
     * Sesión de estudio fija, de [minutes] minutos, el día de la propuesta a las [startHour] en la zona [zoneId].
     * El identificador sale del tema, la fecha y la hora: guardar dos veces el mismo repaso no lo duplica (el
     * repositorio rechaza el segundo intento como dato ya existente).
     *
     * Lanza [java.time.DateTimeException] si esa hora no existe en la zona (cambio de horario).
     */
    fun activityFor(
        topic: Topic,
        proposal: ReviewProposal,
        today: LocalDate,
        zoneId: String,
        startHour: Int = DEFAULT_START_HOUR,
        minutes: Int = DEFAULT_MINUTES,
    ): AcademicActivity {
        require(startHour in 0..23) { "La hora de inicio debe estar entre 0 y 23." }
        require(minutes in 1..MAX_MINUTES) { "La duración debe ser de 1 a $MAX_MINUTES minutos." }
        val date = proposal.dueOn(today)
        val start = StrictLocalTime.resolve(date.atTime(startHour, 0), zoneId)
        val end = start.plusSeconds(minutes * SECONDS_PER_MINUTE)
        val prefix = if (proposal.relearn) "Volver a aprender" else "Repaso"
        return AcademicActivity(
            id = idFor(topic.id, date, startHour),
            kind = ActivityKind.STUDY,
            title = "$prefix: ${topic.title}".take(MAX_TITLE),
            time = ActivityTime.Timed(start, end, zoneId),
            estimateMinutes = minutes,
            notes = "Propuesto por el tutor de matemática. ${proposal.reason}".take(MAX_NOTES),
            fixed = true,
        )
    }

    /** Identificador estable de un repaso (tema + fecha + hora). */
    fun idFor(topicId: String, date: LocalDate, startHour: Int): String =
        UUID.nameUUIDFromBytes(
            "pe.studyplanner.aipet/tutor-review/v1/$topicId/$date/${String.format(Locale.ROOT, "%02d", startHour)}"
                .toByteArray(Charsets.UTF_8),
        ).toString()
}
