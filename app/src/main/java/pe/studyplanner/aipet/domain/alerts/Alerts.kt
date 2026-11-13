package pe.studyplanner.aipet.domain.alerts

import java.time.*
import java.util.UUID
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.calendar.*

enum class AlertKind { ALARM, REMINDER }
enum class AlertPrecision { EXACT, FLEXIBLE }
enum class AlertSound { SOUND, VIBRATE, SILENT }
enum class AlertState { PENDING, EXACT, FLEXIBLE, NEEDS_NOTIFICATIONS, NEEDS_EXACT, FAILED, DISABLED, MISSED, POSTED, UNCERTAIN }

sealed interface AlertSchedule {
    data class Local(val date: LocalDate, val time: LocalTime, val zoneId: String,
        val weekdays: Set<DayOfWeek> = emptySet(), val until: LocalDate? = null) : AlertSchedule
    /** Monotonic in the same boot. The absolute anchor is used after a reboot, explicitly. */
    data class Relative(val at: Instant, val elapsedMillis: Long, val boot: Int, val zoneId: String) : AlertSchedule
    data class Linked(val activityId: String, val offsetMinutes: Int, val zoneId: String,
        val dateOnlyTime: LocalTime? = null) : AlertSchedule
}

data class AlertDefinition(
    val id: String = UUID.randomUUID().toString(), val kind: AlertKind, val title: String,
    val schedule: AlertSchedule, val precision: AlertPrecision = AlertPrecision.FLEXIBLE,
    val sound: AlertSound = AlertSound.SOUND, val snoozeMinutes: Int = 10,
    val quietEnabled: Boolean = true, val quietStart: LocalTime = LocalTime.of(22, 0),
    val quietEnd: LocalTime = LocalTime.of(7, 0),
)

data class AlertFire(val at: Instant, val occurrence: Instant, val elapsedMillis: Long? = null,
    val silentNight: Boolean = false, val note: String = "")

/** No Android calls or persistence. Local gaps/overlaps require correction, never silent shifting. */
object AlertRules {
    fun validate(d: AlertDefinition) {
        require(runCatching { UUID.fromString(d.id).toString() == d.id }.getOrDefault(false)) { "Identificador de aviso inválido." }
        require(d.title.trim().isNotEmpty() && d.title.length <= 160) { "Escribe un título de hasta 160 caracteres." }
        require(d.snoozeMinutes in 1..120) { "Posponer debe estar entre 1 y 120 minutos." }
        require(d.quietStart != d.quietEnd) { "El inicio y fin del silencio deben ser distintos." }
        when (val s = d.schedule) {
            is AlertSchedule.Local -> {
                StrictLocalTime.zone(s.zoneId)
                require(s.until == null || s.until >= s.date) { "La repetición termina antes de comenzar." }
                StrictLocalTime.resolve(s.date.atTime(s.time), s.zoneId)
            }
            is AlertSchedule.Relative -> { StrictLocalTime.zone(s.zoneId); require(s.elapsedMillis > 0 && s.boot >= 0) { "Ancla de tiempo relativo inválida." } }
            is AlertSchedule.Linked -> {
                require(s.activityId.isNotBlank() && s.offsetMinutes in 0..10080) { "Vínculo o anticipación inválidos (0–10080 min)." }
                require(d.kind == AlertKind.REMINDER) { "Solo los recordatorios se vinculan a actividades." }
                StrictLocalTime.zone(s.zoneId)
            }
        }
    }

    fun next(d: AlertDefinition, snapshot: AcademicSnapshot, now: Instant, boot: Int,
        elapsedNow: Long, afterOccurrence: Instant? = null): AlertFire? {
        validate(d)
        val s = d.schedule
        val raw = when (s) {
            is AlertSchedule.Relative -> {
                if (afterOccurrence != null) return null
                val at = if (boot == s.boot) now.plusMillis(s.elapsedMillis - elapsedNow) else s.at
                AlertFire(at, s.at, s.elapsedMillis.takeIf { boot == s.boot }, note =
                    if (boot == s.boot) "Reloj monótono de este arranque." else "Recuperado con la hora absoluta tras reiniciar.")
            }
            is AlertSchedule.Local -> {
                if (s.weekdays.isEmpty()) {
                    if (afterOccurrence != null) return null
                    val at = StrictLocalTime.resolve(s.date.atTime(s.time), s.zoneId)
                    AlertFire(at, at)
                } else {
                    val today = now.atZone(ZoneId.of(s.zoneId)).toLocalDate()
                    var date = maxOf(s.date, today.minusDays(1))
                    var found: Instant? = null
                    // At most eight dates; a weekly rule necessarily has a weekday in this window.
                    repeat(9) {
                        if (found == null && (s.until == null || date <= s.until) && date.dayOfWeek in s.weekdays) {
                            val at = StrictLocalTime.resolve(date.atTime(s.time), s.zoneId)
                            if (at > now && (afterOccurrence == null || at > afterOccurrence)) found = at
                        }
                        date = date.plusDays(1)
                    }
                    found?.let { AlertFire(it, it) } ?: return null
                }
            }
            is AlertSchedule.Linked -> {
                val source = snapshot.activities.firstOrNull { it.id == s.activityId }
                    ?: error("La actividad vinculada ya no existe.")
                require(source.status !in setOf(ActivityStatus.COMPLETED, ActivityStatus.CANCELLED)) { "La actividad está completada o cancelada." }
                val at = when (val time = source.time) {
                    ActivityTime.Unscheduled -> error("La actividad no tiene fecha. Añádela antes de crear un aviso vinculado.")
                    is ActivityTime.Timed -> if (afterOccurrence != null) return null else time.start.minusSeconds(s.offsetMinutes * 60L)
                    is ActivityTime.DateOnly -> {
                        if (afterOccurrence != null) return null
                        val hour = requireNotNull(s.dateOnlyTime) { "La actividad solo tiene fecha: elige la hora del aviso; no se inventa una hora de examen." }
                        StrictLocalTime.resolve(time.date.atTime(hour), s.zoneId).minusSeconds(s.offsetMinutes * 60L)
                    }
                    is ActivityTime.Weekly -> {
                        val day = now.atZone(ZoneId.of(s.zoneId)).toLocalDate()
                        val projection = CalendarEngine.project(snapshot, CalendarRange(day.minusDays(1), day.plusDays(364), s.zoneId))
                        require(projection.issues.none { it.activityId == source.id }) { "La serie tiene horas ambiguas o inválidas. Corrígela antes de programar." }
                        projection.entries.asSequence().filter { it.ruleId == source.id || it.activityId == source.id }
                            .mapNotNull { it.start?.minusSeconds(s.offsetMinutes * 60L) }.distinct()
                            .filter { it > now && (afterOccurrence == null || it > afterOccurrence) }.minOrNull() ?: return null
                    }
                }
                AlertFire(at, at)
            }
        }
        val zone = when (s) { is AlertSchedule.Local -> s.zoneId; is AlertSchedule.Linked -> s.zoneId; is AlertSchedule.Relative -> s.zoneId }
        val time = raw.at.atZone(ZoneId.of(zone)).toLocalTime()
        return raw.copy(silentNight = d.kind == AlertKind.REMINDER && d.quietEnabled && inQuiet(time, d.quietStart, d.quietEnd))
    }

    fun inQuiet(time: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (start < end) time >= start && time < end else time >= start || time < end

    fun recurring(d: AlertDefinition, snapshot: AcademicSnapshot): Boolean = when (val s = d.schedule) {
        is AlertSchedule.Local -> s.weekdays.isNotEmpty()
        is AlertSchedule.Linked -> snapshot.activities.firstOrNull { it.id == s.activityId }?.time is ActivityTime.Weekly
        else -> false
    }
}
