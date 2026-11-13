package pe.studyplanner.aipet.domain.academic

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class DatasetScope { PERSONAL, DEMO }
enum class ActivityKind { TASK, EXAM, CLASS, SPORT, PERSONAL, STUDY }
enum class ActivityStatus { TODO, IN_PROGRESS, COMPLETED, CANCELLED }
enum class Priority { LOW, MEDIUM, HIGH }

data class Course(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val code: String = "",
    val colorToken: String = "INDIGO",
    val archived: Boolean = false,
    val version: Long = 0,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
)

/** A date-only deadline never receives an invented hour. Timed TASK means its deadline. */
sealed interface ActivityTime {
    data object Unscheduled : ActivityTime
    data class DateOnly(val date: LocalDate) : ActivityTime
    data class Timed(val start: Instant, val end: Instant? = null, val zoneId: String) : ActivityTime
    data class Weekly(val rule: WeeklyRule) : ActivityTime
}

data class WeeklyRule(
    val weekdays: Set<DayOfWeek>,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val zoneId: String,
    val startDate: LocalDate,
    val until: LocalDate? = null,
    val intervalWeeks: Int = 1,
    val endDayOffset: Int = 0,
)

data class AcademicActivity(
    val id: String = UUID.randomUUID().toString(),
    val kind: ActivityKind,
    val title: String,
    val courseId: String? = null,
    val time: ActivityTime = ActivityTime.Unscheduled,
    val status: ActivityStatus = ActivityStatus.TODO,
    val priority: Priority = Priority.MEDIUM,
    val estimateMinutes: Int? = null,
    val difficulty: Int? = null,
    val topics: String = "",
    val notes: String = "",
    val fixed: Boolean = true,
    val version: Long = 0,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
)

/** One local occurrence is omitted or replaced; the series itself remains unchanged. */
data class RecurrenceException(
    val activityId: String,
    val occurrenceDate: LocalDate,
    val replacementActivityId: String? = null,
)

data class AcademicSnapshot(
    val scope: DatasetScope,
    val revision: Long = 0,
    val courses: List<Course> = emptyList(),
    val activities: List<AcademicActivity> = emptyList(),
    val exceptions: List<RecurrenceException> = emptyList(),
)

interface AcademicRepository {
    fun observe(scope: DatasetScope): Flow<AcademicSnapshot>
    suspend fun snapshot(scope: DatasetScope): AcademicSnapshot
    /** Version zero inserts. Updates require the current version; stale writes fail atomically. */
    suspend fun saveCourse(scope: DatasetScope, course: Course): Course
    suspend fun archiveCourse(scope: DatasetScope, id: String, expectedVersion: Long): Course
    suspend fun deleteCourse(scope: DatasetScope, id: String, expectedVersion: Long)
    suspend fun saveActivity(scope: DatasetScope, activity: AcademicActivity): AcademicActivity
    suspend fun deleteActivity(scope: DatasetScope, id: String, expectedVersion: Long)
    suspend fun putException(scope: DatasetScope, exception: RecurrenceException, expectedActivityVersion: Long)
    suspend fun removeException(scope: DatasetScope, activityId: String, occurrenceDate: LocalDate, expectedActivityVersion: Long)
    suspend fun seedDemo(referenceDate: LocalDate)
    suspend fun resetDemo(referenceDate: LocalDate)
}

class StaleAcademicWriteException : IllegalStateException("Los datos cambiaron. Vuelve a cargar antes de guardar.")
