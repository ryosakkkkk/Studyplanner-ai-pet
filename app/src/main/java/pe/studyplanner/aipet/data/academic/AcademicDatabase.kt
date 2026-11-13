package pe.studyplanner.aipet.data.academic

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "dataset_revision")
data class RevisionEntity(@PrimaryKey val scope: String, val revision: Long = 0)

@Entity(tableName = "course", primaryKeys = ["scope", "id"])
data class CourseEntity(
    val scope: String, val id: String, val name: String, val code: String, val colorToken: String,
    val archived: Boolean, val version: Long, val createdAt: Long, val updatedAt: Long,
)

@Entity(tableName = "activity", primaryKeys = ["scope", "id"], foreignKeys = [
    ForeignKey(entity = CourseEntity::class, parentColumns = ["scope", "id"],
        childColumns = ["scope", "courseId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index(value = ["scope", "courseId"]), Index(value = ["scope", "status", "startMillis"])])
data class ActivityEntity(
    val scope: String, val id: String, val kind: String, val title: String, val courseId: String?,
    val temporalKind: String, val dateEpochDay: Long?, val startMillis: Long?, val endMillis: Long?,
    val zoneId: String?, val status: String, val notes: String, val fixed: Boolean,
    val version: Long, val createdAt: Long, val updatedAt: Long,
)

@Entity(tableName = "task_detail", primaryKeys = ["scope", "activityId"], foreignKeys = [
    ForeignKey(entity = ActivityEntity::class, parentColumns = ["scope", "id"],
        childColumns = ["scope", "activityId"], onDelete = ForeignKey.CASCADE)
])
data class TaskDetailEntity(val scope: String, val activityId: String, val priority: String, val estimateMinutes: Int?)

@Entity(tableName = "exam_detail", primaryKeys = ["scope", "activityId"], foreignKeys = [
    ForeignKey(entity = ActivityEntity::class, parentColumns = ["scope", "id"],
        childColumns = ["scope", "activityId"], onDelete = ForeignKey.CASCADE)
])
data class ExamDetailEntity(val scope: String, val activityId: String, val difficulty: Int?, val topics: String)

@Entity(tableName = "study_detail", primaryKeys = ["scope", "activityId"], foreignKeys = [
    ForeignKey(entity = ActivityEntity::class, parentColumns = ["scope", "id"],
        childColumns = ["scope", "activityId"], onDelete = ForeignKey.CASCADE)
])
data class StudyDetailEntity(val scope: String, val activityId: String, val plannedMinutes: Int)

@Entity(tableName = "recurrence_rule", primaryKeys = ["scope", "activityId"], foreignKeys = [
    ForeignKey(entity = ActivityEntity::class, parentColumns = ["scope", "id"],
        childColumns = ["scope", "activityId"], onDelete = ForeignKey.CASCADE)
])
data class RuleEntity(
    val scope: String, val activityId: String, val weekdayMask: Int,
    val startSecond: Int, val endSecond: Int, val endDayOffset: Int, val zoneId: String,
    val startEpochDay: Long, val untilEpochDay: Long?, val intervalWeeks: Int,
)

@Entity(tableName = "recurrence_exception", primaryKeys = ["scope", "activityId", "occurrenceEpochDay"],
    foreignKeys = [
        ForeignKey(entity = RuleEntity::class, parentColumns = ["scope", "activityId"],
            childColumns = ["scope", "activityId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ActivityEntity::class, parentColumns = ["scope", "id"],
            childColumns = ["scope", "replacementActivityId"], onDelete = ForeignKey.RESTRICT)
    ], indices = [Index(value = ["scope", "replacementActivityId"])])
data class ExceptionEntity(val scope: String, val activityId: String, val occurrenceEpochDay: Long, val replacementActivityId: String?)

/** Single global local profile: switching/resetting DEMO never changes it. */
@Entity(tableName = "local_profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1, val studentName: String, val petName: String, val avatar: String,
    val zoneId: String, val weekStartsOn: String, val theme: String,
    val onboardingCompleted: Boolean, val petVisible: Boolean,
    val preferencesRevision: Long, val legacyImportVersion: Int,
)

@Dao
interface AcademicDao {
    @Query("SELECT revision FROM dataset_revision WHERE scope=:scope") fun observeRevision(scope: String): Flow<Long?>
    @Query("SELECT * FROM dataset_revision WHERE scope=:scope") suspend fun revision(scope: String): RevisionEntity?
    @Upsert suspend fun putRevision(row: RevisionEntity)
    @Query("SELECT * FROM course WHERE scope=:scope ORDER BY name,id") suspend fun courses(scope: String): List<CourseEntity>
    @Query("SELECT * FROM course WHERE scope=:scope AND id=:id") suspend fun course(scope: String, id: String): CourseEntity?
    @Insert suspend fun insertCourse(row: CourseEntity)
    @Update suspend fun updateCourse(row: CourseEntity)
    @Query("DELETE FROM course WHERE scope=:scope AND id=:id") suspend fun deleteCourse(scope: String, id: String)
    @Query("SELECT count(*) FROM activity WHERE scope=:scope AND courseId=:id") suspend fun courseUse(scope: String, id: String): Int
    @Query("SELECT * FROM activity WHERE scope=:scope ORDER BY createdAt,id") suspend fun activities(scope: String): List<ActivityEntity>
    @Query("SELECT * FROM activity WHERE scope=:scope AND id=:id") suspend fun activity(scope: String, id: String): ActivityEntity?
    @Insert suspend fun insertActivity(row: ActivityEntity)
    @Update suspend fun updateActivity(row: ActivityEntity)
    @Query("DELETE FROM activity WHERE scope=:scope AND id=:id") suspend fun deleteActivity(scope: String, id: String)
    @Query("SELECT * FROM task_detail WHERE scope=:scope") suspend fun tasks(scope: String): List<TaskDetailEntity>
    @Query("SELECT * FROM exam_detail WHERE scope=:scope") suspend fun exams(scope: String): List<ExamDetailEntity>
    @Query("SELECT * FROM study_detail WHERE scope=:scope") suspend fun studies(scope: String): List<StudyDetailEntity>
    @Query("SELECT * FROM recurrence_rule WHERE scope=:scope") suspend fun rules(scope: String): List<RuleEntity>
    @Query("SELECT * FROM recurrence_rule WHERE scope=:scope AND activityId=:id") suspend fun rule(scope: String, id: String): RuleEntity?
    @Query("SELECT * FROM recurrence_exception WHERE scope=:scope") suspend fun exceptions(scope: String): List<ExceptionEntity>
    @Upsert suspend fun putTask(row: TaskDetailEntity)
    @Upsert suspend fun putExam(row: ExamDetailEntity)
    @Upsert suspend fun putStudy(row: StudyDetailEntity)
    @Upsert suspend fun putRule(row: RuleEntity)
    @Upsert suspend fun putException(row: ExceptionEntity)
    @Query("DELETE FROM task_detail WHERE scope=:scope AND activityId=:id") suspend fun deleteTask(scope: String, id: String)
    @Query("DELETE FROM exam_detail WHERE scope=:scope AND activityId=:id") suspend fun deleteExam(scope: String, id: String)
    @Query("DELETE FROM study_detail WHERE scope=:scope AND activityId=:id") suspend fun deleteStudy(scope: String, id: String)
    @Query("DELETE FROM recurrence_rule WHERE scope=:scope AND activityId=:id") suspend fun deleteRule(scope: String, id: String)
    @Query("DELETE FROM recurrence_exception WHERE scope=:scope AND activityId=:id AND occurrenceEpochDay=:date")
    suspend fun deleteException(scope: String, id: String, date: Long)
    @Query("DELETE FROM recurrence_exception WHERE scope=:scope") suspend fun clearExceptions(scope: String)
    @Query("DELETE FROM activity WHERE scope=:scope") suspend fun clearActivities(scope: String)
    @Query("DELETE FROM course WHERE scope=:scope") suspend fun clearCourses(scope: String)
    @Query("SELECT * FROM local_profile WHERE id=1") suspend fun profile(): ProfileEntity?
    @Query("SELECT * FROM local_profile WHERE id=1") fun observeProfile(): Flow<ProfileEntity?>
    @Insert suspend fun insertProfile(row: ProfileEntity)
    @Update suspend fun updateProfile(row: ProfileEntity)
}

@Database(entities = [RevisionEntity::class, CourseEntity::class, ActivityEntity::class,
    TaskDetailEntity::class, ExamDetailEntity::class, StudyDetailEntity::class, RuleEntity::class,
    ExceptionEntity::class, ProfileEntity::class, PlannerPreferencesEntity::class, PlannerAvailabilityEntity::class,
    StudyPlanLinkEntity::class, PlanReceiptEntity::class, AlertEntity::class, AlertOutboxEntity::class,
    AlertDeliveryEntity::class, AgentReceiptEntity::class, AgentMessageEntity::class,
    AgentPreferencesEntity::class], version = 5, exportSchema = true)
abstract class AcademicDatabase : RoomDatabase() {
    abstract fun academicDao(): AcademicDao
    abstract fun plannerDao(): PlannerDao
    abstract fun alertDao(): AlertDao
    abstract fun agentDao(): AgentDao

    companion object {
        fun open(context: Context, name: String = "academic_v1.db"): AcademicDatabase =
            Room.databaseBuilder(context.applicationContext, AcademicDatabase::class.java, name)
                .addMigrations(PlannerMigration, AlertMigration, AlertQueueMigration, AgentMigration).addCallback(IntegrityConstraints).build()
    }
}

/** Room has no CHECK annotation. These triggers enforce the equivalent on real SQLite writes. */
private object IntegrityConstraints : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        PlannerMigration.installConstraints(db)
        AlertMigration.installConstraints(db)
        AgentMigration.installConstraints(db)
        val conditions = mapOf(
            "course" to """NEW.scope NOT IN ('PERSONAL','DEMO') OR NEW.version<1 OR length(trim(NEW.name)) NOT BETWEEN 1 AND 120 OR length(NEW.code)>40 OR length(NEW.colorToken) NOT BETWEEN 1 AND 40""",
            "dataset_revision" to """NEW.scope NOT IN ('PERSONAL','DEMO') OR NEW.revision<0""",
            "activity" to """NEW.scope NOT IN ('PERSONAL','DEMO') OR NEW.version<1
                OR NEW.kind NOT IN ('TASK','EXAM','CLASS','SPORT','PERSONAL','STUDY')
                OR NEW.status NOT IN ('TODO','IN_PROGRESS','COMPLETED','CANCELLED')
                OR length(trim(NEW.title)) NOT BETWEEN 1 AND 160 OR length(NEW.notes)>2000
                OR (NEW.kind='EXAM' AND NEW.fixed!=1)
                OR NOT (
                    (NEW.temporalKind='NONE' AND NEW.kind='TASK' AND NEW.dateEpochDay IS NULL AND NEW.startMillis IS NULL AND NEW.endMillis IS NULL AND NEW.zoneId IS NULL)
                    OR (NEW.temporalKind='DATE' AND NEW.kind IN ('TASK','EXAM') AND NEW.dateEpochDay IS NOT NULL AND NEW.startMillis IS NULL AND NEW.endMillis IS NULL AND NEW.zoneId IS NULL)
                    OR (NEW.temporalKind='TIMED' AND NEW.dateEpochDay IS NULL AND NEW.startMillis IS NOT NULL AND NEW.zoneId IS NOT NULL AND ((NEW.kind='TASK' AND NEW.endMillis IS NULL) OR (NEW.kind!='TASK' AND NEW.endMillis IS NOT NULL AND NEW.startMillis<NEW.endMillis)))
                    OR (NEW.temporalKind='WEEKLY' AND NEW.kind IN ('CLASS','SPORT','PERSONAL') AND NEW.dateEpochDay IS NULL AND NEW.startMillis IS NULL AND NEW.endMillis IS NULL AND NEW.zoneId IS NULL)
                )""",
            "task_detail" to """NEW.priority NOT IN ('LOW','MEDIUM','HIGH') OR (NEW.estimateMinutes IS NOT NULL AND NEW.estimateMinutes NOT BETWEEN 1 AND 10080) OR (SELECT kind FROM activity WHERE scope=NEW.scope AND id=NEW.activityId)!='TASK'""",
            "exam_detail" to """(NEW.difficulty IS NOT NULL AND NEW.difficulty NOT BETWEEN 1 AND 5) OR length(NEW.topics)>2000 OR (SELECT kind FROM activity WHERE scope=NEW.scope AND id=NEW.activityId)!='EXAM'""",
            "study_detail" to """NEW.plannedMinutes NOT BETWEEN 1 AND 10080 OR (SELECT kind FROM activity WHERE scope=NEW.scope AND id=NEW.activityId)!='STUDY'""",
            "recurrence_rule" to """NEW.weekdayMask NOT BETWEEN 1 AND 127 OR NEW.intervalWeeks NOT BETWEEN 1 AND 52 OR NEW.startSecond NOT BETWEEN 0 AND 86399 OR NEW.endSecond NOT BETWEEN 0 AND 86399 OR NEW.endDayOffset NOT BETWEEN 0 AND 1 OR (NEW.endDayOffset=0 AND NEW.startSecond>=NEW.endSecond) OR (NEW.untilEpochDay IS NOT NULL AND NEW.untilEpochDay<NEW.startEpochDay) OR (SELECT temporalKind FROM activity WHERE scope=NEW.scope AND id=NEW.activityId)!='WEEKLY'""",
            "recurrence_exception" to """NEW.activityId=NEW.replacementActivityId""",
            "local_profile" to """NEW.id!=1 OR NEW.preferencesRevision<0 OR NEW.legacyImportVersion!=1 OR NEW.avatar NOT IN ('FOX','OWL','CAT') OR NEW.theme NOT IN ('SYSTEM','LIGHT','DARK') OR NEW.weekStartsOn NOT IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')""",
        )
        for ((table, condition) in conditions) {
            for (operation in listOf("INSERT", "UPDATE")) {
                db.execSQL("CREATE TRIGGER ${table}_${operation.lowercase()}_integrity BEFORE $operation ON $table WHEN $condition BEGIN SELECT RAISE(ABORT, 'Invalid $table invariant'); END")
            }
        }
    }
}
