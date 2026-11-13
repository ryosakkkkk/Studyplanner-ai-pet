package pe.studyplanner.aipet.data.academic

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "planner_preferences")
data class PlannerPreferencesEntity(@PrimaryKey val scope: String, val version: Long, val dailyMinutes: Int,
    val minBlock: Int, val maxBlock: Int, val breakMinutes: Int, val sleepStart: Int, val sleepEnd: Int,
    val preferredStart: Int, val preferredEnd: Int)

@Entity(tableName = "planner_availability", primaryKeys = ["scope", "weekday", "startSecond", "endSecond"], foreignKeys = [
    ForeignKey(entity = PlannerPreferencesEntity::class, parentColumns = ["scope"], childColumns = ["scope"], onDelete = ForeignKey.CASCADE)])
data class PlannerAvailabilityEntity(val scope: String, val weekday: Int, val startSecond: Int, val endSecond: Int)

@Entity(tableName = "study_plan_link", primaryKeys = ["scope", "sessionId"], foreignKeys = [
    ForeignKey(entity = StudyDetailEntity::class, parentColumns = ["scope", "activityId"], childColumns = ["scope", "sessionId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ActivityEntity::class, parentColumns = ["scope", "id"], childColumns = ["scope", "goalId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index(value = ["scope", "goalId"])])
data class StudyPlanLinkEntity(val scope: String, val sessionId: String, val goalId: String, val planId: String)

@Entity(tableName = "plan_receipt", primaryKeys = ["scope", "planId"])
data class PlanReceiptEntity(val scope: String, val planId: String, val fingerprint: String, val sessionIds: String, val appliedAt: Long,
    val goalRequests: String, val appliedRevision: Long)

@Dao
interface PlannerDao {
    @Query("SELECT * FROM planner_preferences WHERE scope=:scope") suspend fun preferences(scope: String): PlannerPreferencesEntity?
    @Upsert suspend fun putPreferences(row: PlannerPreferencesEntity)
    @Query("SELECT * FROM planner_availability WHERE scope=:scope ORDER BY weekday,startSecond,endSecond") suspend fun windows(scope: String): List<PlannerAvailabilityEntity>
    @Query("DELETE FROM planner_availability WHERE scope=:scope") suspend fun clearWindows(scope: String)
    @Insert suspend fun insertWindows(rows: List<PlannerAvailabilityEntity>)
    @Query("SELECT * FROM study_plan_link WHERE scope=:scope ORDER BY sessionId") suspend fun links(scope: String): List<StudyPlanLinkEntity>
    @Query("SELECT count(*) FROM study_plan_link WHERE scope=:scope AND goalId=:goalId") suspend fun goalUse(scope: String, goalId: String): Int
    @Upsert suspend fun putLink(row: StudyPlanLinkEntity)
    @Query("DELETE FROM study_plan_link WHERE scope=:scope") suspend fun clearLinks(scope: String)
    @Query("SELECT * FROM plan_receipt WHERE scope=:scope AND planId=:id") suspend fun receipt(scope: String, id: String): PlanReceiptEntity?
    @Query("SELECT * FROM plan_receipt WHERE scope=:scope ORDER BY appliedRevision DESC,planId") suspend fun receipts(scope: String): List<PlanReceiptEntity>
    @Insert suspend fun insertReceipt(row: PlanReceiptEntity)
}

object PlannerMigration : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `planner_preferences` (`scope` TEXT NOT NULL, `version` INTEGER NOT NULL, `dailyMinutes` INTEGER NOT NULL, `minBlock` INTEGER NOT NULL, `maxBlock` INTEGER NOT NULL, `breakMinutes` INTEGER NOT NULL, `sleepStart` INTEGER NOT NULL, `sleepEnd` INTEGER NOT NULL, `preferredStart` INTEGER NOT NULL, `preferredEnd` INTEGER NOT NULL, PRIMARY KEY(`scope`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `planner_availability` (`scope` TEXT NOT NULL, `weekday` INTEGER NOT NULL, `startSecond` INTEGER NOT NULL, `endSecond` INTEGER NOT NULL, PRIMARY KEY(`scope`, `weekday`, `startSecond`, `endSecond`), FOREIGN KEY(`scope`) REFERENCES `planner_preferences`(`scope`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `study_plan_link` (`scope` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `goalId` TEXT NOT NULL, `planId` TEXT NOT NULL, PRIMARY KEY(`scope`, `sessionId`), FOREIGN KEY(`scope`, `sessionId`) REFERENCES `study_detail`(`scope`, `activityId`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`scope`, `goalId`) REFERENCES `activity`(`scope`, `id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_plan_link_scope_goalId` ON `study_plan_link` (`scope`, `goalId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `plan_receipt` (`scope` TEXT NOT NULL, `planId` TEXT NOT NULL, `fingerprint` TEXT NOT NULL, `sessionIds` TEXT NOT NULL, `appliedAt` INTEGER NOT NULL, `goalRequests` TEXT NOT NULL, `appliedRevision` INTEGER NOT NULL, PRIMARY KEY(`scope`, `planId`))")
        installConstraints(db)
    }
    fun installConstraints(db: SupportSQLiteDatabase) {
        val conditions = mapOf(
            "planner_preferences" to "NEW.scope NOT IN ('PERSONAL','DEMO') OR NEW.version<1 OR NEW.dailyMinutes NOT BETWEEN 25 AND 720 OR NEW.minBlock NOT BETWEEN 5 AND 120 OR NEW.maxBlock NOT BETWEEN NEW.minBlock AND 180 OR NEW.breakMinutes NOT BETWEEN 0 AND 60 OR NEW.sleepStart NOT BETWEEN 0 AND 86399 OR NEW.sleepEnd NOT BETWEEN 0 AND 86399 OR NEW.sleepStart=NEW.sleepEnd OR NEW.preferredStart<0 OR NEW.preferredStart>=NEW.preferredEnd OR NEW.preferredEnd>86399",
            "planner_availability" to "NEW.weekday NOT BETWEEN 1 AND 7 OR NEW.startSecond<0 OR NEW.startSecond>=NEW.endSecond OR NEW.endSecond>86399",
            "study_plan_link" to "NEW.sessionId=NEW.goalId OR length(NEW.planId)!=64 OR (SELECT kind FROM activity WHERE scope=NEW.scope AND id=NEW.goalId) NOT IN ('TASK','EXAM')",
            "plan_receipt" to "NEW.scope NOT IN ('PERSONAL','DEMO') OR length(NEW.planId)!=64 OR length(NEW.fingerprint)!=64 OR NEW.appliedRevision<1"
        )
        for ((table, condition) in conditions) for (operation in listOf("INSERT", "UPDATE"))
            db.execSQL("CREATE TRIGGER IF NOT EXISTS ${table}_${operation.lowercase()}_integrity BEFORE $operation ON $table WHEN $condition BEGIN SELECT RAISE(ABORT, 'Invalid $table invariant'); END")
    }
}
