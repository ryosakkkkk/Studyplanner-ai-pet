package pe.studyplanner.aipet.data.academic

import androidx.room.withTransaction
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.calendar.StrictLocalTime

class RoomAcademicRepository(
    private val database: AcademicDatabase,
    private val clock: Clock = Clock.systemUTC(),
) : AcademicRepository {
    private val dao = database.academicDao()

    override fun observe(scope: DatasetScope): Flow<AcademicSnapshot> =
        dao.observeRevision(scope.name).map { snapshot(scope) }

    override suspend fun snapshot(scope: DatasetScope): AcademicSnapshot = database.withTransaction {
        val key = scope.name
        val tasks = dao.tasks(key).associateBy { it.activityId }
        val exams = dao.exams(key).associateBy { it.activityId }
        val studies = dao.studies(key).associateBy { it.activityId }
        val rules = dao.rules(key).associateBy { it.activityId }
        AcademicSnapshot(scope, dao.revision(key)?.revision ?: 0,
            dao.courses(key).map { it.toDomain() },
            dao.activities(key).map { row ->
                check((row.kind == "TASK") == tasks.containsKey(row.id) &&
                    (row.kind == "EXAM") == exams.containsKey(row.id) &&
                    (row.kind == "STUDY") == studies.containsKey(row.id) &&
                    (row.temporalKind == "WEEKLY") == rules.containsKey(row.id)) {
                    "La actividad almacenada no coincide con sus detalles normalizados."
                }
                checkStoredTemporalShape(row)
                val time = when (row.temporalKind) {
                    "NONE" -> ActivityTime.Unscheduled
                    "DATE" -> ActivityTime.DateOnly(LocalDate.ofEpochDay(requireNotNull(row.dateEpochDay)))
                    "TIMED" -> ActivityTime.Timed(Instant.ofEpochMilli(requireNotNull(row.startMillis)),
                        row.endMillis?.let(Instant::ofEpochMilli), requireNotNull(row.zoneId))
                    "WEEKLY" -> ActivityTime.Weekly(requireNotNull(rules[row.id]).toDomain())
                    else -> error("Tipo temporal almacenado desconocido.")
                }
                AcademicActivity(row.id, ActivityKind.valueOf(row.kind), row.title, row.courseId, time,
                    ActivityStatus.valueOf(row.status), tasks[row.id]?.priority?.let(Priority::valueOf) ?: Priority.MEDIUM,
                    tasks[row.id]?.estimateMinutes ?: studies[row.id]?.plannedMinutes,
                    exams[row.id]?.difficulty, exams[row.id]?.topics.orEmpty(), row.notes, row.fixed,
                    row.version, Instant.ofEpochMilli(row.createdAt), Instant.ofEpochMilli(row.updatedAt)).also {
                    validate(it, resolveRecurrences = false)
                    check(it.title == it.title.trim() && it.version >= 1) { "La actividad almacenada no está normalizada." }
                }
            }, dao.exceptions(key).map { RecurrenceException(it.activityId,
                LocalDate.ofEpochDay(it.occurrenceEpochDay), it.replacementActivityId) })
    }

    override suspend fun saveCourse(scope: DatasetScope, course: Course): Course = database.withTransaction {
        saveCourseInside(scope, course).also { bump(scope) }
    }

    private suspend fun saveCourseInside(scope: DatasetScope, course: Course): Course {
        require(course.id.isNotBlank() && course.id.length <= 128) { "Identificador de curso inválido." }
        require(course.name.trim().isNotEmpty() && course.name.length <= 120) { "Escribe un curso de hasta 120 caracteres." }
        require(course.code.length <= 40 && course.colorToken.length in 1..40) { "Código o color de curso inválido." }
        val existing = dao.course(scope.name, course.id)
        checkVersion(existing?.version, course.version)
        val now = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        val saved = course.copy(name = course.name.trim(), code = course.code.trim(),
            version = (existing?.version ?: dao.revision(scope.name)?.revision ?: 0) + 1,
            createdAt = existing?.createdAt?.let(Instant::ofEpochMilli) ?: now, updatedAt = now)
        val row = saved.toEntity(scope)
        if (existing == null) dao.insertCourse(row) else dao.updateCourse(row)
        return saved
    }

    override suspend fun archiveCourse(scope: DatasetScope, id: String, expectedVersion: Long): Course =
        database.withTransaction {
            val old = dao.course(scope.name, id) ?: throw StaleAcademicWriteException()
            checkVersion(old.version, expectedVersion)
            saveCourseInside(scope, old.toDomain().copy(archived = true)).also { bump(scope) }
        }

    override suspend fun deleteCourse(scope: DatasetScope, id: String, expectedVersion: Long) = database.withTransaction {
        val old = dao.course(scope.name, id) ?: throw StaleAcademicWriteException()
        checkVersion(old.version, expectedVersion)
        require(dao.courseUse(scope.name, id) == 0) { "El curso tiene actividades vinculadas. Puedes archivarlo para conservar su historia." }
        dao.deleteCourse(scope.name, id)
        bump(scope)
    }

    override suspend fun saveActivity(scope: DatasetScope, activity: AcademicActivity): AcademicActivity =
        database.withTransaction { saveActivityInside(scope, activity).also { bump(scope) } }

    private suspend fun saveActivityInside(scope: DatasetScope, activity: AcademicActivity): AcademicActivity {
        validate(activity)
        require(activity.kind in listOf(ActivityKind.TASK, ActivityKind.EXAM) || database.plannerDao().goalUse(scope.name, activity.id) == 0) {
            "El objetivo tiene sesiones vinculadas; no se puede cambiar a ese tipo de actividad."
        }
        val old = dao.activity(scope.name, activity.id)
        checkVersion(old?.version, activity.version)
        val oldExceptions = dao.exceptions(scope.name).filter { it.activityId == activity.id }
        require(oldExceptions.isEmpty() || (activity.time is ActivityTime.Weekly && oldExceptions.all {
            matches(activity.time.rule, LocalDate.ofEpochDay(it.occurrenceEpochDay))
        })) { "El cambio eliminaría excepciones de la serie. Restaura explícitamente esas ocurrencias antes de cambiar la regla." }
        activity.courseId?.let { id ->
            val course = requireNotNull(dao.course(scope.name, id)) { "El curso no pertenece a este conjunto de datos." }
            require(!course.archived || old?.courseId == id) { "No se puede asignar un curso archivado a una nueva actividad." }
        }
        val now = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        val saved = activity.copy(title = activity.title.trim(), version = (old?.version ?: dao.revision(scope.name)?.revision ?: 0) + 1,
            createdAt = old?.createdAt?.let(Instant::ofEpochMilli) ?: now, updatedAt = now)
        val time = saved.time
        val row = ActivityEntity(scope.name, saved.id, saved.kind.name, saved.title, saved.courseId,
            when(time) { ActivityTime.Unscheduled -> "NONE"; is ActivityTime.DateOnly -> "DATE";
                is ActivityTime.Timed -> "TIMED"; is ActivityTime.Weekly -> "WEEKLY" },
            (time as? ActivityTime.DateOnly)?.date?.toEpochDay(),
            (time as? ActivityTime.Timed)?.start?.toEpochMilli(), (time as? ActivityTime.Timed)?.end?.toEpochMilli(),
            (time as? ActivityTime.Timed)?.zoneId, saved.status.name, saved.notes, saved.fixed,
            saved.version, saved.createdAt.toEpochMilli(), saved.updatedAt.toEpochMilli())
        if (old == null) dao.insertActivity(row) else dao.updateActivity(row)
        if (saved.kind == ActivityKind.TASK) dao.putTask(TaskDetailEntity(scope.name, saved.id,
            saved.priority.name, saved.estimateMinutes)) else dao.deleteTask(scope.name, saved.id)
        if (saved.kind == ActivityKind.EXAM) dao.putExam(ExamDetailEntity(scope.name, saved.id,
            saved.difficulty, saved.topics)) else dao.deleteExam(scope.name, saved.id)
        if (saved.kind == ActivityKind.STUDY) dao.putStudy(StudyDetailEntity(scope.name, saved.id,
            requireNotNull(saved.estimateMinutes))) else dao.deleteStudy(scope.name, saved.id)
        if (time is ActivityTime.Weekly) {
            val rule = time.rule
            dao.putRule(RuleEntity(scope.name, saved.id, rule.weekdays.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) },
                rule.startTime.toSecondOfDay(), rule.endTime.toSecondOfDay(), rule.endDayOffset, rule.zoneId,
                rule.startDate.toEpochDay(), rule.until?.toEpochDay(), rule.intervalWeeks))
            // Exceptions are preserved. A change that would discard them was rejected above.
        } else dao.deleteRule(scope.name, saved.id)
        return saved
    }

    override suspend fun deleteActivity(scope: DatasetScope, id: String, expectedVersion: Long) = database.withTransaction {
        val old = dao.activity(scope.name, id) ?: throw StaleAcademicWriteException()
        checkVersion(old.version, expectedVersion)
        require(database.plannerDao().goalUse(scope.name, id) == 0) { "Hay sesiones de estudio vinculadas. Elimínalas antes de borrar este objetivo." }
        require(dao.exceptions(scope.name).none { it.replacementActivityId == id }) {
            "La actividad reemplaza una ocurrencia. Retira esa excepción antes de borrarla." }
        dao.deleteActivity(scope.name, id)
        bump(scope)
    }

    override suspend fun putException(scope: DatasetScope, exception: RecurrenceException, expectedActivityVersion: Long) = database.withTransaction {
        val series = requireNotNull(dao.activity(scope.name, exception.activityId)) { "La serie no existe en este conjunto." }
        checkVersion(series.version, expectedActivityVersion)
        val rule = requireNotNull(dao.rule(scope.name, exception.activityId)) { "La serie no existe en este conjunto." }.toDomain()
        require(matches(rule, exception.occurrenceDate)) { "La fecha no pertenece a esta serie." }
        exception.replacementActivityId?.let { replacementId ->
            require(replacementId != exception.activityId) { "Una serie no puede reemplazarse a sí misma." }
            val replacement = requireNotNull(dao.activity(scope.name, replacementId)) { "El reemplazo no pertenece al conjunto." }
            require(replacement.temporalKind == "TIMED" || replacement.temporalKind == "DATE") { "El reemplazo debe ser una actividad puntual." }
            require(dao.exceptions(scope.name).none { it.replacementActivityId == replacementId &&
                (it.activityId != exception.activityId || it.occurrenceEpochDay != exception.occurrenceDate.toEpochDay()) }) {
                "El reemplazo ya corresponde a otra ocurrencia." }
        }
        dao.putException(ExceptionEntity(scope.name, exception.activityId, exception.occurrenceDate.toEpochDay(), exception.replacementActivityId))
        dao.updateActivity(series.copy(version = series.version + 1, updatedAt = clock.millis()))
        bump(scope)
    }

    override suspend fun removeException(scope: DatasetScope, activityId: String, occurrenceDate: LocalDate, expectedActivityVersion: Long) = database.withTransaction {
        val series = requireNotNull(dao.activity(scope.name, activityId)) { "La serie no existe en este conjunto." }
        checkVersion(series.version, expectedActivityVersion)
        require(dao.rule(scope.name, activityId) != null) { "La actividad no es una serie." }
        dao.deleteException(scope.name, activityId, occurrenceDate.toEpochDay())
        dao.updateActivity(series.copy(version = series.version + 1, updatedAt = clock.millis()))
        bump(scope)
    }

    override suspend fun seedDemo(referenceDate: LocalDate) = database.withTransaction {
        if (dao.courses(DatasetScope.DEMO.name).isEmpty() && dao.activities(DatasetScope.DEMO.name).isEmpty()) {
            seedInside(referenceDate); bump(DatasetScope.DEMO)
        }
    }

    override suspend fun resetDemo(referenceDate: LocalDate) = database.withTransaction {
        val key = DatasetScope.DEMO.name
        database.alertDao().all().filter { it.scope==key && it.enabled }.forEach {
            database.alertDao().update(it.copy(enabled=false,generation=it.generation+1,state="PENDING",nextAt=null,snoozeAt=null,deliveryToken=null))
        }
        database.plannerDao().clearLinks(key)
        dao.clearExceptions(key); dao.clearActivities(key); dao.clearCourses(key)
        seedInside(referenceDate); bump(DatasetScope.DEMO)
    }

    private suspend fun seedInside(date: LocalDate) {
        val scope = DatasetScope.DEMO
        val course = saveCourseInside(scope, Course(id = demoId("course-ihc"), name = "Interacción Humano-Computador · ficticio", code = "DEMO"))
        saveActivityInside(scope, AcademicActivity(id = demoId("task"), kind = ActivityKind.TASK,
            title = "Entrega de bocetos · ficticia", courseId = course.id,
            time = ActivityTime.DateOnly(date.plusDays(2)), priority = Priority.HIGH, estimateMinutes = 60))
        saveActivityInside(scope, AcademicActivity(id = demoId("exam"), kind = ActivityKind.EXAM,
            title = "Examen de práctica · ficticio", courseId = course.id, time = ActivityTime.DateOnly(date.plusDays(4))))
        val start = date.atTime(16, 0).atZone(ZoneId.of("America/Lima")).toInstant()
        saveActivityInside(scope, AcademicActivity(id = demoId("sport"), kind = ActivityKind.SPORT,
            title = "Fútbol · ficticio", time = ActivityTime.Timed(start, start.plusSeconds(7200), "America/Lima")))
        saveActivityInside(scope, AcademicActivity(id = demoId("personal"), kind = ActivityKind.PERSONAL,
            title = "Compromiso personal · ficticio", time = ActivityTime.Timed(start.plusSeconds(3600), start.plusSeconds(10800), "America/Lima")))
        saveActivityInside(scope, AcademicActivity(id = demoId("class"), kind = ActivityKind.CLASS,
            title = "Clase semanal · ficticia", courseId = course.id,
            time = ActivityTime.Weekly(WeeklyRule(setOf(date.dayOfWeek), LocalTime.of(9,0), LocalTime.of(10,30),
                "America/Lima", date, date.plusWeeks(4)))))
        saveActivityInside(scope, AcademicActivity(id = demoId("study"), kind = ActivityKind.STUDY,
            title = "Repaso manual · ficticio", courseId = course.id, estimateMinutes = 45,
            time = ActivityTime.Timed(start.minusSeconds(7200), start.minusSeconds(4500), "America/Lima"), fixed = false))
        saveActivityInside(scope, AcademicActivity(id = demoId("task-undated"), kind = ActivityKind.TASK,
            title = "Lectura pendiente sin fecha · ficticia", priority = Priority.LOW, estimateMinutes = 30))
        saveActivityInside(scope, AcademicActivity(id = demoId("task-completed"), kind = ActivityKind.TASK,
            title = "Ficha completada · ficticia", time = ActivityTime.DateOnly(date.minusDays(1)), status = ActivityStatus.COMPLETED))
        saveActivityInside(scope, AcademicActivity(id = demoId("exam-timed"), kind = ActivityKind.EXAM,
            title = "Evaluación con hora · ficticia", courseId = course.id, difficulty = 3, topics = "Ejemplo de práctica",
            time = ActivityTime.Timed(start.plusSeconds(86400), start.plusSeconds(90000), "America/Lima")))
        saveActivityInside(scope, AcademicActivity(id = demoId("personal-next"), kind = ActivityKind.PERSONAL,
            title = "Visita programada · ficticia", time = ActivityTime.Timed(start.plusSeconds(172800), start.plusSeconds(176400), "America/Lima")))
        saveActivityInside(scope, AcademicActivity(id = demoId("task-deadline"), kind = ActivityKind.TASK,
            title = "Entrega con hora · ficticia", time = ActivityTime.Timed(start.plusSeconds(259200), zoneId = "America/Lima"), priority = Priority.HIGH))
    }

    private suspend fun bump(scope: DatasetScope) {
        dao.putRevision(RevisionEntity(scope.name, (dao.revision(scope.name)?.revision ?: 0) + 1))
    }

    private fun demoId(key: String): String =
        UUID.nameUUIDFromBytes("pe.studyplanner.aipet/DEMO/v1/$key".toByteArray(Charsets.UTF_8)).toString()

    private fun validate(a: AcademicActivity, resolveRecurrences: Boolean = true) {
        require(a.id.isNotBlank() && a.id.length <= 128) { "Identificador de actividad inválido." }
        require(a.title.trim().isNotEmpty() && a.title.length <= 160) { "Escribe un título de hasta 160 caracteres." }
        require(a.notes.length <= 2000 && a.topics.length <= 2000) { "Notas o temas demasiado largos." }
        require(a.estimateMinutes == null || a.estimateMinutes in 1..10080) { "La duración debe ser positiva, hasta una semana." }
        require(a.difficulty == null || a.difficulty in 1..5) { "La dificultad debe estar entre 1 y 5." }
        when (val time = a.time) {
            ActivityTime.Unscheduled -> require(a.kind == ActivityKind.TASK) { "Selecciona una fecha o intervalo para esta actividad." }
            is ActivityTime.DateOnly -> require(a.kind == ActivityKind.TASK || a.kind == ActivityKind.EXAM) { "Esta actividad necesita un intervalo con hora explícita." }
            is ActivityTime.Timed -> {
                StrictLocalTime.zone(time.zoneId)
                require(time.start.nano % 1_000_000 == 0 && (time.end?.nano ?: 0) % 1_000_000 == 0) { "El instante debe usar precisión de milisegundos o menor." }
                require(if (a.kind == ActivityKind.TASK) time.end == null else time.end != null && time.start < time.end) {
                    "Una tarea usa una fecha límite; los eventos necesitan inicio anterior al fin." }
            }
            is ActivityTime.Weekly -> {
                require(a.kind == ActivityKind.CLASS || a.kind == ActivityKind.SPORT || a.kind == ActivityKind.PERSONAL) { "La repetición semanal corresponde a clases o compromisos." }
                val r = time.rule
                StrictLocalTime.zone(r.zoneId)
                require(r.weekdays.isNotEmpty() && r.intervalWeeks in 1..52 && r.endDayOffset in 0..1) { "Repetición semanal inválida." }
                require(r.startTime.nano == 0 && r.endTime.nano == 0) { "La repetición usa precisión de segundos." }
                require(r.endDayOffset == 1 || r.startTime < r.endTime) { "El inicio debe ser anterior al fin." }
                require(r.until == null || r.until >= r.startDate) { "El fin de repetición es anterior al inicio." }
                // Finite validation: first year plus the current year of an older open series.
                // Later calendar queries also resolve strictly and surface any future rule change.
                if (resolveRecurrences) {
                    validateOccurrenceWindow(r, r.startDate)
                    val today = LocalDate.now(clock.withZone(StrictLocalTime.zone(r.zoneId)))
                    if (today > r.startDate.plusDays(365) && (r.until == null || today <= r.until))
                        validateOccurrenceWindow(r, today)
                }
            }
        }
        require(a.kind != ActivityKind.STUDY || a.estimateMinutes != null) { "La sesión necesita una duración planificada." }
        require(a.kind != ActivityKind.EXAM || a.fixed) { "El examen debe conservarse como compromiso fijo." }
    }

    private fun checkVersion(stored: Long?, supplied: Long) {
        if ((stored == null && supplied != 0L) || (stored != null && stored != supplied)) throw StaleAcademicWriteException()
    }

    private fun validateOccurrenceWindow(rule: WeeklyRule, from: LocalDate) {
        val end = minOf(rule.until ?: from.plusDays(365), from.plusDays(365))
        var date = from
        while (date <= end) {
            if (matches(rule, date)) {
                val start = StrictLocalTime.resolve(date.atTime(rule.startTime), rule.zoneId)
                val finish = StrictLocalTime.resolve(date.plusDays(rule.endDayOffset.toLong()).atTime(rule.endTime), rule.zoneId)
                require(start < finish) { "El intervalo de la repetición no es válido en su zona horaria." }
            }
            date = date.plusDays(1)
        }
    }
}

private fun CourseEntity.toDomain(): Course {
    check(id.isNotBlank() && id.length <= 128 && name.isNotBlank() && name == name.trim() &&
        name.length <= 120 && code.length <= 40 && colorToken.length in 1..40 && version >= 1) {
        "El curso almacenado contiene datos inválidos."
    }
    return Course(id, name, code, colorToken, archived, version,
        Instant.ofEpochMilli(createdAt), Instant.ofEpochMilli(updatedAt))
}
private fun Course.toEntity(scope: DatasetScope) = CourseEntity(scope.name, id, name, code, colorToken, archived,
    version, createdAt.toEpochMilli(), updatedAt.toEpochMilli())
private fun RuleEntity.toDomain(): WeeklyRule {
    check(weekdayMask in 1..127 && startSecond in 0..86399 && endSecond in 0..86399 &&
        intervalWeeks in 1..52 && endDayOffset in 0..1 &&
        (endDayOffset == 1 || startSecond < endSecond) && (untilEpochDay == null || untilEpochDay >= startEpochDay)) {
        "La regla semanal almacenada contiene datos inválidos."
    }
    return WeeklyRule(DayOfWeek.entries.filter { weekdayMask and (1 shl (it.value - 1)) != 0 }.toSet(),
        LocalTime.ofSecondOfDay(startSecond.toLong()), LocalTime.ofSecondOfDay(endSecond.toLong()), zoneId,
        LocalDate.ofEpochDay(startEpochDay), untilEpochDay?.let(LocalDate::ofEpochDay), intervalWeeks, endDayOffset)
}
private fun matches(rule: WeeklyRule, date: LocalDate): Boolean = date >= rule.startDate &&
    (rule.until == null || date <= rule.until) && date.dayOfWeek in rule.weekdays &&
    ChronoUnit.WEEKS.between(rule.startDate.with(DayOfWeek.MONDAY), date.with(DayOfWeek.MONDAY)) % rule.intervalWeeks == 0L

private fun checkStoredTemporalShape(row: ActivityEntity) {
    val valid = when (row.temporalKind) {
        "NONE", "WEEKLY" -> row.dateEpochDay == null && row.startMillis == null && row.endMillis == null && row.zoneId == null
        "DATE" -> row.dateEpochDay != null && row.startMillis == null && row.endMillis == null && row.zoneId == null
        "TIMED" -> row.dateEpochDay == null && row.startMillis != null && row.zoneId != null &&
            (if (row.kind == "TASK") row.endMillis == null else row.endMillis != null && row.startMillis < row.endMillis)
        else -> false
    }
    check(valid) { "La rama temporal almacenada contiene campos contradictorios." }
}
