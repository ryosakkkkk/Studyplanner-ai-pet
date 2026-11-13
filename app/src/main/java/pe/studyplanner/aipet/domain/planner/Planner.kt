package pe.studyplanner.aipet.domain.planner

import java.time.*
import java.security.MessageDigest
import java.util.UUID
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.calendar.*

data class Availability(val day: DayOfWeek, val start: LocalTime, val end: LocalTime)
data class PlannerSettings(val version: Long = 0, val windows: List<Availability> = emptyList(),
    val dailyMinutes: Int = 120, val minBlock: Int = 25, val maxBlock: Int = 50, val breakMinutes: Int = 10,
    val sleepStart: LocalTime = LocalTime.of(23, 0), val sleepEnd: LocalTime = LocalTime.of(7, 0),
    val preferredStart: LocalTime = LocalTime.of(18, 0), val preferredEnd: LocalTime = LocalTime.of(20, 0)) {
    fun validate() {
        require(windows.size in 1..28 && windows.distinct().size == windows.size) { "Configura de 1 a 28 franjas de disponibilidad distintas." }
        require(windows.all { it.start < it.end && it.start.second == 0 && it.end.second == 0 && it.start.nano == 0 && it.end.nano == 0 }) { "Cada franja debe terminar el mismo día después del inicio, con precisión de minutos." }
        require(dailyMinutes in 25..720 && minBlock in 5..120 && maxBlock in minBlock..180 && breakMinutes in 0..60) { "Revisa máximo diario, bloques (5–180 min) y pausa (0–60 min)." }
        require(sleepStart != sleepEnd && preferredStart < preferredEnd) { "Revisa descanso y franja preferida." }
        require(listOf(sleepStart, sleepEnd, preferredStart, preferredEnd).all { it.second == 0 && it.nano == 0 }) { "Usa horas con precisión de minutos." }
    }
}
data class StudyLink(val sessionId: String, val goalId: String, val planId: String)
data class PlanGoal(val activityId: String, val totalMinutes: Int?, val splittable: Boolean = true)
data class PlanRequest(val startDate: LocalDate, val days: Int = 7, val goals: List<PlanGoal>, val movableSessionIds: Set<String> = emptySet())
data class PlanContext(val snapshot: AcademicSnapshot, val settings: PlannerSettings, val links: List<StudyLink>, val zoneId: String, val profileRevision: Long,
    val goalDefaults: Map<String, PlanGoal> = emptyMap())
data class GoalResult(val goalId: String, val title: String, val required: Int?, val credited: Int, val assigned: Int, val pending: Int?, val reason: String)
data class SessionChange(val goalId: String, val before: AcademicActivity?, val after: AcademicActivity?)
data class PlanDraft(val id: String, val scope: DatasetScope, val revision: Long, val settingsVersion: Long, val profileRevision: Long,
    val zoneId: String, val generatedAt: Instant, val request: PlanRequest, val changes: List<SessionChange>, val goals: List<GoalResult>)
data class PlanReceipt(val planId: String, val sessionIds: List<String>, val appliedAt: Instant, val replay: Boolean = false)
interface PlannerRepository {
    suspend fun load(scope: DatasetScope): PlanContext
    suspend fun saveSettings(scope: DatasetScope, settings: PlannerSettings): PlannerSettings
    suspend fun propose(scope: DatasetScope, request: PlanRequest): PlanDraft
    suspend fun apply(draft: PlanDraft): PlanReceipt
}

/** Bounded deterministic heuristic. No writes, randomness, remote provider or hidden relaxation. */
object PlannerEngine {
    private data class Span(val start: Instant, val end: Instant)
    fun generate(context: PlanContext, request: PlanRequest, now: Instant): PlanDraft {
        val settings = context.settings.also { it.validate() }
        require(settings.version >= 1) { "Guarda primero tu disponibilidad y límites." }
        val zone = StrictLocalTime.zone(context.zoneId)
        require(request.days in 1..31 && request.goals.size in 1..50 && request.goals.map { it.activityId }.distinct().size == request.goals.size) { "Selecciona 1–50 objetivos distintos y un horizonte de 1–31 días." }
        require(request.startDate >= now.atZone(zone).toLocalDate()) { "El horizonte no puede comenzar en el pasado." }
        require(request.goals.all { it.totalMinutes == null || it.totalMinutes in 1..10080 }) { "El esfuerzo total debe ser 1–10080 minutos o quedar sin estimar." }
        val snapshot = context.snapshot
        val activities = snapshot.activities.associateBy { it.id }
        val goalIds = request.goals.map { it.activityId }.toSet()
        require(goalIds.all { activities[it]?.kind in listOf(ActivityKind.TASK, ActivityKind.EXAM) && activities[it]?.status in listOf(ActivityStatus.TODO, ActivityStatus.IN_PROGRESS) }) { "Solo se planifican tareas o exámenes pendientes del espacio actual." }
        require(context.links.map { it.sessionId }.distinct().size == context.links.size) { "Hay vínculos de sesión duplicados." }
        val links = context.links.associateBy { it.sessionId }
        val movable = request.movableSessionIds.map { id ->
            val activity = requireNotNull(activities[id]) { "La sesión a mover ya no existe." }
            val time = activity.time as? ActivityTime.Timed
            require(activity.kind == ActivityKind.STUDY && !activity.fixed && activity.status == ActivityStatus.TODO && time != null && time.start >= now && links[id]?.goalId in goalIds) { "Solo puedes mover sesiones flexibles futuras, pendientes y vinculadas a los objetivos elegidos." }
            activity
        }.sortedBy { it.id }
        val canonical = listOf("planner-v1", snapshot.scope, snapshot.revision, context.profileRevision, context.zoneId, now,
            settings.copy(windows = settings.windows.sortedWith(compareBy<Availability> { it.day.value }.thenBy { it.start }.thenBy { it.end })),
            snapshot.courses.sortedBy { it.id }, snapshot.activities.sortedBy { it.id }.map { a ->
                val weekly = a.time as? ActivityTime.Weekly
                if (weekly == null) a else a.copy(time = ActivityTime.Weekly(weekly.rule.copy(weekdays = weekly.rule.weekdays.toSortedSet()))) },
            snapshot.exceptions.sortedWith(compareBy<RecurrenceException> { it.activityId }.thenBy { it.occurrenceDate }),
            context.links.sortedBy { it.sessionId }, request.copy(goals = request.goals.sortedBy { it.activityId }, movableSessionIds = request.movableSessionIds.toSortedSet())).joinToString("\n")
        val id = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val lastDate = request.startDate.plusDays(request.days.toLong() - 1)
        val horizonEnd = lastDate.plusDays(1).atStartOfDay(zone).toInstant()
        val projection = CalendarEngine.project(snapshot.copy(activities = snapshot.activities.filter { it.id !in request.movableSessionIds }),
            CalendarRange(request.startDate, lastDate, context.zoneId), CalendarFilter(includeCompleted = true))
        require(projection.issues.isEmpty()) { "Corrige primero la agenda: ${projection.issues.first().message}" }
        val busy = projection.entries.filter { it.occupiesTime }.distinctBy { it.key }.map { Span(requireNotNull(it.start), requireNotNull(it.end)) }.toMutableList()
        val study = projection.entries.filter { it.kind == ActivityKind.STUDY && it.occupiesTime }.distinctBy { it.key }.map { Span(requireNotNull(it.start), requireNotNull(it.end)) }.toMutableList()
        val free = mutableListOf<Span>()
        val sleeps = mutableListOf<Span>()
        var date = request.startDate.minusDays(1)
        while (date <= lastDate) {
            val endDate = if (settings.sleepEnd <= settings.sleepStart) date.plusDays(1) else date
            sleeps += Span(StrictLocalTime.resolve(date.atTime(settings.sleepStart), context.zoneId), StrictLocalTime.resolve(endDate.atTime(settings.sleepEnd), context.zoneId))
            date = date.plusDays(1)
        }
        date = request.startDate
        while (date <= lastDate) {
            settings.windows.filter { it.day == date.dayOfWeek }.forEach { window ->
                val start = maxOf(now, StrictLocalTime.resolve(date.atTime(window.start), context.zoneId))
                val end = StrictLocalTime.resolve(date.atTime(window.end), context.zoneId)
                if (start < end) free += Span(ceilMinute(start), end)
            }
            date = date.plusDays(1)
        }
        val availability = merge(free)
        val chosen = mutableListOf<Pair<String, Span>>()
        val results = mutableListOf<GoalResult>()
        fun cutoff(a: AcademicActivity): Instant = when (val t = a.time) {
            is ActivityTime.Timed -> t.start
            is ActivityTime.DateOnly -> (if (a.kind == ActivityKind.EXAM) t.date else t.date.plusDays(1)).atStartOfDay(zone).toInstant()
            else -> horizonEnd
        }
        val ordered = request.goals.sortedWith(compareBy<PlanGoal> { cutoff(activities.getValue(it.activityId)) }
            .thenByDescending { activities.getValue(it.activityId).priority.ordinal }.thenBy { it.totalMinutes ?: Int.MAX_VALUE }.thenBy { it.activityId })
        for (goal in ordered) {
            val a = activities.getValue(goal.activityId)
            val deadline = minOf(cutoff(a), horizonEnd)
            val credit = context.links.filter { it.goalId == a.id && it.sessionId !in request.movableSessionIds }.sumOf { link ->
                val session = activities[link.sessionId]
                val t = session?.time as? ActivityTime.Timed
                if (session?.kind == ActivityKind.STUDY && t?.end != null && t.end <= cutoff(a) &&
                    ((session.status == ActivityStatus.COMPLETED && t.end <= now) || (session.status == ActivityStatus.TODO && t.start >= now)))
                    Duration.between(t.start, t.end).toMinutes().toInt() else 0
            }
            val required = goal.totalMinutes
            val archived = a.courseId?.let { courseId -> snapshot.courses.firstOrNull { it.id == courseId }?.archived } == true
            var remaining = required?.let { maxOf(0, it - credit) } ?: 0
            var assigned = 0
            if (required != null && !archived) while (remaining >= settings.minBlock && chosen.size < 512) {
                val protected = busy + sleeps + study.map { Span(it.start.minusSeconds(settings.breakMinutes * 60L), it.end.plusSeconds(settings.breakMinutes * 60L)) }
                val slots = subtract(availability.mapNotNull { s -> minOf(s.end, deadline).let { end -> if (s.start < end) Span(s.start, end) else null } }, protected)
                data class Candidate(val span: Span, val preferred: Boolean, val load: Int)
                val candidates = slots.mapNotNull { slot ->
                    val day = slot.start.atZone(zone).toLocalDate()
                    val dayStart = day.atStartOfDay(zone).toInstant(); val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
                    val load = study.sumOf { minutes(maxOf(it.start, dayStart), minOf(it.end, dayEnd)) }
                    val limit = minOf(settings.dailyMinutes - load, minutes(slot.start, slot.end), settings.maxBlock)
                    var amount = minOf(remaining, limit)
                    if (!goal.splittable && amount < remaining) amount = 0
                    if (goal.splittable && remaining - amount in 1 until settings.minBlock) amount -= settings.minBlock - (remaining - amount)
                    if (amount < settings.minBlock) null else {
                        val preferredStart = StrictLocalTime.resolve(day.atTime(settings.preferredStart), context.zoneId)
                        val preferredEnd = StrictLocalTime.resolve(day.atTime(settings.preferredEnd), context.zoneId)
                        val proposedStart = maxOf(slot.start, preferredStart)
                        val preferred = proposedStart.plusSeconds(amount * 60L) <= minOf(slot.end, preferredEnd)
                        val start = if (preferred) proposedStart else slot.start
                        Candidate(Span(start, start.plusSeconds(amount * 60L)), preferred, load)
                    }
                }.sortedWith(compareByDescending<Candidate> { it.preferred }.thenBy { it.load }.thenBy { it.span.start })
                val best = candidates.firstOrNull()?.span ?: break
                chosen += a.id to best; busy += best; study += best
                val amount = minutes(best.start, best.end); remaining -= amount; assigned += amount
            }
            results += GoalResult(a.id, a.title, required, credit, assigned, required?.let { maxOf(0, it - credit - assigned) },
                when { required == null -> "Falta estimar el esfuerzo; no se inventaron minutos."
                    archived -> "El curso está archivado; no se crean sesiones nuevas."
                    deadline <= now -> "El plazo ya pasó o no deja tiempo futuro."
                    remaining > 0 && chosen.size >= 512 -> "Se alcanzó el límite de 512 sesiones por propuesta; divide la solicitud."
                    remaining > 0 -> "No se encontró hueco bajo estas reglas; no es una prueba de imposibilidad general."
                    else -> "Esfuerzo cubierto por sesiones vinculadas y esta propuesta." })
        }
        val unused = movable.toMutableList()
        val exact = chosen.associateWith { (goalId, span) -> movable.firstOrNull {
            links[it.id]?.goalId == goalId && (it.time as? ActivityTime.Timed)?.let { time -> time.start == span.start && time.end == span.end } == true
        } }
        val reservedIds = exact.values.mapNotNull { it?.id }.toSet()
        val changes = chosen.mapIndexed { index, (goalId, span) ->
            val matching = exact[goalId to span] ?: unused.firstOrNull { links[it.id]?.goalId == goalId && it.id !in reservedIds }
            if (matching != null) unused.remove(matching)
            val source = activities.getValue(goalId)
            val session = if (exact[goalId to span] != null) requireNotNull(matching) else (matching ?: AcademicActivity(id = UUID.nameUUIDFromBytes("$id/$goalId/$index".toByteArray(Charsets.UTF_8)).toString(), kind = ActivityKind.STUDY, title = "Estudiar: ${source.title}".take(160), courseId = source.courseId, fixed = false))
                .copy(time = ActivityTime.Timed(span.start, span.end, context.zoneId), estimateMinutes = minutes(span.start, span.end))
            SessionChange(goalId, matching, session)
        }.filter { it.before != it.after } + unused.map { SessionChange(links.getValue(it.id).goalId, it, null) }
        return PlanDraft(id, snapshot.scope, snapshot.revision, settings.version, context.profileRevision, context.zoneId, now, request, changes, results)
    }
    private fun ceilMinute(i: Instant): Instant = Instant.ofEpochSecond(Math.floorDiv(i.epochSecond + if (i.nano > 0) 60 else 59, 60) * 60)
    private fun minutes(start: Instant, end: Instant): Int = if (end <= start) 0 else Duration.between(start, end).toMinutes().toInt()
    private fun merge(spans: List<Span>): List<Span> {
        val result = mutableListOf<Span>()
        for (span in spans.sortedBy { it.start }) {
            val last = result.lastOrNull()
            if (last != null && span.start <= last.end) result[result.lastIndex] = Span(last.start, maxOf(last.end, span.end)) else result += span
        }
        return result
    }
    private fun subtract(windows: List<Span>, blocks: List<Span>): List<Span> {
        var result = windows
        for (block in merge(blocks)) result = result.flatMap { span ->
            if (block.end <= span.start || block.start >= span.end) listOf(span)
            else listOfNotNull(if (span.start < block.start) Span(span.start, block.start) else null, if (block.end < span.end) Span(block.end, span.end) else null)
        }
        return result
    }
}
