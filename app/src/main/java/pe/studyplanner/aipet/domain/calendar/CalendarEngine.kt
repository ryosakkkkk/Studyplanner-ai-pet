package pe.studyplanner.aipet.domain.calendar

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import pe.studyplanner.aipet.domain.academic.AcademicActivity
import pe.studyplanner.aipet.domain.academic.AcademicSnapshot
import pe.studyplanner.aipet.domain.academic.ActivityKind
import pe.studyplanner.aipet.domain.academic.ActivityStatus
import pe.studyplanner.aipet.domain.academic.ActivityTime
import pe.studyplanner.aipet.domain.academic.WeeklyRule

data class CalendarRange(val startDate: LocalDate, val endDateInclusive: LocalDate, val zoneId: String) {
    init {
        require(!endDateInclusive.isBefore(startDate)) { "La fecha final debe ser igual o posterior al inicio." }
        require(ChronoUnit.DAYS.between(startDate, endDateInclusive) < 366) { "Consulta como máximo 366 días." }
        require(startDate > LocalDate.MIN && endDateInclusive < LocalDate.MAX) { "El rango está fuera de las fechas consultables." }
        StrictLocalTime.zone(zoneId)
    }
}

data class CalendarFilter(
    val courseIds: Set<String>? = null,
    val kinds: Set<ActivityKind>? = null,
    val includeCompleted: Boolean = false,
) {
    internal fun accepts(activity: AcademicActivity): Boolean =
        activity.status != ActivityStatus.CANCELLED &&
            (includeCompleted || activity.status != ActivityStatus.COMPLETED) &&
            (courseIds == null || activity.courseId in courseIds) &&
            (kinds == null || activity.kind in kinds)
}

/** Derived projection only. Source instants and date-only values are never rewritten. */
data class CalendarEntry(
    val key: String,
    val activityId: String,
    val title: String,
    val kind: ActivityKind,
    val courseId: String?,
    val date: LocalDate,
    val start: Instant?,
    val end: Instant?,
    val dateOnly: Boolean,
    val occupiesTime: Boolean,
    val status: ActivityStatus,
    val sourceZoneId: String?,
    val occurrenceDate: LocalDate? = null,
    val ruleId: String? = null,
)

data class CalendarConflict(val firstKey: String, val secondKey: String, val start: Instant, val end: Instant)
data class CalendarIssue(val activityId: String, val occurrenceDate: LocalDate?, val message: String)
data class CalendarProjection(
    val range: CalendarRange,
    val entries: List<CalendarEntry>,
    val conflicts: List<CalendarConflict>,
    val issues: List<CalendarIssue>,
    val revision: Long,
)

/** Stateless, finite expansion of the Room snapshot; it owns no second calendar store. */
object CalendarEngine {
    fun day(snapshot: AcademicSnapshot, date: LocalDate, zoneId: String, filter: CalendarFilter = CalendarFilter()) =
        project(snapshot, CalendarRange(date, date, zoneId), filter)

    fun week(
        snapshot: AcademicSnapshot,
        referenceDate: LocalDate,
        zoneId: String,
        weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
        filter: CalendarFilter = CalendarFilter(),
    ): CalendarProjection {
        val first = referenceDate.with(TemporalAdjusters.previousOrSame(weekStartsOn))
        return project(snapshot, CalendarRange(first, first.plusDays(6), zoneId), filter)
    }

    fun project(snapshot: AcademicSnapshot, range: CalendarRange, filter: CalendarFilter = CalendarFilter()): CalendarProjection {
        val zone = StrictLocalTime.zone(range.zoneId)
        val windowStart = range.startDate.atStartOfDay(zone).toInstant()
        val windowEnd = range.endDateInclusive.plusDays(1).atStartOfDay(zone).toInstant()
        val entries = mutableListOf<CalendarEntry>()
        val issues = mutableListOf<CalendarIssue>()
        val activities = snapshot.activities.associateBy { it.id }
        val exceptionGroups = snapshot.exceptions.groupBy { it.activityId to it.occurrenceDate }
        val replacementIds = snapshot.exceptions.mapNotNull { it.replacementActivityId }.toSet()

        fun addTimed(activity: AcademicActivity, time: ActivityTime.Timed, occurrence: LocalDate? = null, seriesId: String? = null) {
            val end = time.end
            if (end != null && !end.isAfter(time.start)) {
                issues += CalendarIssue(activity.id, occurrence, "El fin debe ser posterior al inicio.")
                return
            }
            val occupies = activity.kind != ActivityKind.TASK && end != null
            val intersects = if (occupies) time.start < windowEnd && end!! > windowStart
                else time.start >= windowStart && time.start < windowEnd
            if (!intersects) return
            val firstDate = maxOf(time.start.atZone(zone).toLocalDate(), range.startDate)
            val lastDate = if (occupies) minOf(end!!.minusNanos(1).atZone(zone).toLocalDate(), range.endDateInclusive) else firstDate
            var date = firstDate
            while (!date.isAfter(lastDate)) {
                entries += CalendarEntry(
                    key = if (seriesId == null) activity.id else "$seriesId@$occurrence",
                    activityId = activity.id, title = activity.title, kind = activity.kind, courseId = activity.courseId,
                    date = date, start = time.start, end = end, dateOnly = false, occupiesTime = occupies,
                    status = activity.status, sourceZoneId = time.zoneId, occurrenceDate = occurrence, ruleId = seriesId,
                )
                date = date.plusDays(1)
            }
        }

        fun addStandalone(activity: AcademicActivity, seriesId: String? = null, occurrence: LocalDate? = null) {
            if (!filter.accepts(activity)) return
            when (val time = activity.time) {
                ActivityTime.Unscheduled -> Unit
                is ActivityTime.DateOnly -> if (time.date >= range.startDate && time.date <= range.endDateInclusive) {
                    entries += CalendarEntry(
                        key = if (seriesId == null) activity.id else "$seriesId@$occurrence",
                        activityId = activity.id, title = activity.title, kind = activity.kind, courseId = activity.courseId,
                        date = time.date, start = null, end = null, dateOnly = true, occupiesTime = false,
                        status = activity.status, sourceZoneId = null, occurrenceDate = occurrence, ruleId = seriesId,
                    )
                }
                is ActivityTime.Timed -> addTimed(activity, time, occurrence, seriesId)
                is ActivityTime.Weekly -> Unit
            }
        }

        snapshot.activities.sortedBy { it.id }.forEach { activity ->
            if (activity.id in replacementIds) return@forEach
            val time = activity.time
            if (time !is ActivityTime.Weekly) {
                addStandalone(activity)
                return@forEach
            }
            if (!filter.accepts(activity)) return@forEach
            val rule = time.rule
            if (rule.intervalWeeks < 1 || rule.endDayOffset !in 0..1 || rule.weekdays.isEmpty() ||
                rule.until?.isBefore(rule.startDate) == true ||
                (rule.endDayOffset == 0 && !rule.endTime.isAfter(rule.startTime))) {
                issues += CalendarIssue(activity.id, null, "La regla semanal tiene un rango u horario inválido.")
                return@forEach
            }
            val seriesZone = try { StrictLocalTime.zone(rule.zoneId) } catch (error: IllegalArgumentException) {
                issues += CalendarIssue(activity.id, null, error.message ?: "Zona inválida.")
                return@forEach
            }
            // Convert the query bounds to the series zone; include the previous start day for overnight events.
            var date = maxOf(rule.startDate, windowStart.atZone(seriesZone).toLocalDate().minusDays(1))
            val lastDate = minOf(rule.until ?: LocalDate.MAX, windowEnd.minusNanos(1).atZone(seriesZone).toLocalDate())
            while (!date.isAfter(lastDate)) {
                if (matches(rule, date) && exceptionGroups[activity.id to date] == null) {
                    try {
                        val start = StrictLocalTime.resolve(date.atTime(rule.startTime), rule.zoneId)
                        val end = StrictLocalTime.resolve(date.plusDays(rule.endDayOffset.toLong()).atTime(rule.endTime), rule.zoneId)
                        addTimed(activity, ActivityTime.Timed(start, end, rule.zoneId), date, activity.id)
                    } catch (error: DateTimeException) {
                        issues += CalendarIssue(activity.id, date, error.message ?: "Hora local inválida.")
                    }
                }
                date = date.plusDays(1)
            }
        }

        // Resolve replacements independently of the original date: a replacement may move into this query.
        exceptionGroups.toSortedMap(compareBy<Pair<String, LocalDate>> { it.first }.thenBy { it.second }).forEach { (key, changes) ->
            val original = activities[key.first]
            val rule = (original?.time as? ActivityTime.Weekly)?.rule
            if (rule == null || !matches(rule, key.second)) {
                issues += CalendarIssue(key.first, key.second, "La excepción no corresponde a una ocurrencia de la serie.")
            } else if (changes.size != 1) {
                issues += CalendarIssue(key.first, key.second, "Hay excepciones duplicadas para esta ocurrencia.")
            } else if (changes.single().replacementActivityId != null) {
                val replacement = activities[changes.single().replacementActivityId]
                if (replacement == null || replacement.time is ActivityTime.Weekly || replacement.id == original.id) {
                    issues += CalendarIssue(key.first, key.second, "No se encuentra un reemplazo puntual válido.")
                } else if (filter.copy(courseIds = null, kinds = null).accepts(original)) {
                    addStandalone(replacement, original.id, key.second)
                }
            }
        }
        val ordered = entries.sortedWith(compareBy<CalendarEntry> { it.date }.thenBy { it.start != null }
            .thenBy { it.start }.thenBy { it.end }.thenBy { it.title }.thenBy { it.activityId }.thenBy { it.key })
        val blocks = ordered.filter { it.occupiesTime }.distinctBy { it.key }
            .sortedWith(compareBy<CalendarEntry> { it.start }.thenBy { it.key })
        val conflicts = mutableListOf<CalendarConflict>()
        blocks.forEachIndexed { index, first ->
            val firstStart = checkNotNull(first.start)
            val firstEnd = checkNotNull(first.end)
            for (secondIndex in index + 1 until blocks.size) {
                val second = blocks[secondIndex]
                val secondStart = checkNotNull(second.start)
                val secondEnd = checkNotNull(second.end)
                if (secondStart >= firstEnd) break
                val overlapStart = maxOf(firstStart, secondStart, windowStart)
                val overlapEnd = minOf(firstEnd, secondEnd, windowEnd)
                if (overlapStart < overlapEnd) conflicts += CalendarConflict(first.key, second.key, overlapStart, overlapEnd)
            }
        }
        return CalendarProjection(range, ordered, conflicts, issues.distinct(), snapshot.revision)
    }

    fun next(
        snapshot: AcademicSnapshot,
        from: Instant,
        zoneId: String,
        limit: Int = 3,
        horizonDays: Int = 366,
        filter: CalendarFilter = CalendarFilter(),
    ): List<CalendarEntry> {
        require(limit in 1..100) { "El límite debe estar entre 1 y 100." }
        require(horizonDays in 1..366) { "El horizonte debe estar entre 1 y 366 días." }
        val date = from.atZone(StrictLocalTime.zone(zoneId)).toLocalDate()
        return project(snapshot, CalendarRange(date, date.plusDays(horizonDays - 1L), zoneId), filter).entries
            .filter { it.start == null || it.start >= from }.distinctBy { it.key }.take(limit)
    }

    /** Monday anchors week intervals independently from the selected presentation week start. */
    fun matches(rule: WeeklyRule, date: LocalDate): Boolean {
        if (rule.intervalWeeks < 1 || date < rule.startDate || rule.until?.let { date > it } == true || date.dayOfWeek !in rule.weekdays) return false
        val anchor = rule.startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val currentWeek = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return ChronoUnit.WEEKS.between(anchor, currentWeek) % rule.intervalWeeks == 0L
    }
}
