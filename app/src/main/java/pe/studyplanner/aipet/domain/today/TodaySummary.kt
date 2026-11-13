package pe.studyplanner.aipet.domain.today

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import pe.studyplanner.aipet.domain.tutor.Attempt
import pe.studyplanner.aipet.domain.tutor.AttemptMode
import pe.studyplanner.aipet.domain.tutor.MasteryPolicy
import pe.studyplanner.aipet.domain.tutor.MasteryStatus
import pe.studyplanner.aipet.domain.tutor.ReviewProposal
import pe.studyplanner.aipet.domain.tutor.TutorReviewPlanner

/**
 * Cuentas de la pantalla «Hoy»: racha, meta del día, tema para practicar o comprobar y repaso pendiente.
 * Todo sale del historial de intentos del tutor; no se guarda nada aparte.
 */
object TodaySummary {
    /** Respuestas por día que completan la meta (100 %). */
    const val DAILY_GOAL = 5

    /** Días seguidos con al menos una respuesta, contando hasta hoy (o hasta ayer si hoy todavía no practicó). */
    fun streakDays(attempts: List<Attempt>, zone: ZoneId, today: LocalDate): Int {
        val days = attempts.mapTo(HashSet()) { dayOf(it, zone) }
        var day = if (today in days) today else today.minusDays(1)
        var count = 0
        while (day in days) { count++; day = day.minusDays(1) }
        return count
    }

    /** Fracción 0..1 de la meta de hoy. */
    fun goalFraction(attempts: List<Attempt>, zone: ZoneId, today: LocalDate): Float =
        (attempts.count { dayOf(it, zone) == today }.toFloat() / DAILY_GOAL).coerceIn(0f, 1f)

    /** Respuestas de hoy (para «x de 5»). */
    fun answeredToday(attempts: List<Attempt>, zone: ZoneId, today: LocalDate): Int = attempts.count { dayOf(it, zone) == today }

    /** Si hoy ya hizo una comprobación sin ayuda. */
    fun checkedToday(attempts: List<Attempt>, zone: ZoneId, today: LocalDate): Boolean =
        attempts.any { it.mode == AttemptMode.UNAIDED && dayOf(it, zone) == today }

    /** Tema que conviene practicar: el último que tocó sin dominarlo todavía, o null. */
    fun topicToPractice(attempts: List<Attempt>): String? =
        attempts.sortedByDescending { it.timestampMillis }.map { it.topicId }.distinct()
            .firstOrNull { MasteryPolicy.status(it, attempts) != MasteryStatus.DOMINIO_SIN_AYUDA }

    /** Tema listo para comprobar sin ayuda: ya practicado con pistas y aún sin dominio. */
    fun topicToCheck(attempts: List<Attempt>): String? =
        attempts.sortedByDescending { it.timestampMillis }.map { it.topicId }.distinct().firstOrNull { id ->
            val status = MasteryPolicy.status(id, attempts)
            status == MasteryStatus.EN_PRACTICA || status == MasteryStatus.SOLO_CON_AYUDA
        }

    /**
     * @property due día propuesto para el repaso.
     * @property base día desde el que se cuenta la propuesta (el de la comprobación); [TutorReviewPlanner] lo usa así.
     */
    data class PendingReview(val topicId: String, val proposal: ReviewProposal, val base: LocalDate) {
        val due: LocalDate get() = proposal.dueOn(base)
    }

    /**
     * Repaso que propone el tutor tras la última comprobación sin ayuda, si todavía no está en la agenda.
     * Solo cuenta una comprobación de hoy o de ayer y cuyo día propuesto no pasó.
     *
     * @param activityIds identificadores de las actividades guardadas en la agenda.
     */
    fun pendingReview(attempts: List<Attempt>, zone: ZoneId, today: LocalDate, activityIds: Set<String>): PendingReview? {
        val last = attempts.filter { it.mode == AttemptMode.UNAIDED }.maxByOrNull { it.timestampMillis } ?: return null
        val checkDay = dayOf(last, zone)
        if (checkDay.isBefore(today.minusDays(1)) || checkDay.isAfter(today)) return null
        val check = attempts.filter { it.mode == AttemptMode.UNAIDED && it.topicId == last.topicId && dayOf(it, zone) == checkDay }
        val proposal = MasteryPolicy.proposeReview(last.topicId, check) ?: return null
        val pending = PendingReview(last.topicId, proposal, checkDay)
        if (pending.due.isBefore(today)) return null
        if (alreadyScheduled(last.topicId, checkDay, activityIds)) return null
        return pending
    }

    /** Si ya hay un repaso de ese tema en la agenda en las dos semanas siguientes a la comprobación. */
    fun alreadyScheduled(topicId: String, from: LocalDate, activityIds: Set<String>): Boolean =
        (1..14L).any { offset ->
            TutorReviewPlanner.START_HOURS.any { hour -> TutorReviewPlanner.idFor(topicId, from.plusDays(offset), hour) in activityIds }
        }

    /** «hoy», «mañana» o «el viernes 13». */
    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "hoy"
        today.plusDays(1) -> "mañana"
        else -> "el " + date.format(DateTimeFormatter.ofPattern("EEEE d", Locale.forLanguageTag("es-PE")))
    }

    private fun dayOf(a: Attempt, zone: ZoneId): LocalDate = Instant.ofEpochMilli(a.timestampMillis).atZone(zone).toLocalDate()
}
