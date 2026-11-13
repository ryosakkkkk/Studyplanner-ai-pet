package pe.studyplanner.aipet.data.academic

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import pe.studyplanner.aipet.domain.alerts.*

/** Tombstones are retained so deletion of a parent can cancel the OS PendingIntent after commit. */
@Entity(tableName = "alert_record", primaryKeys = ["scope", "id"], indices = [Index(value = ["scope", "sourceId"])])
data class AlertEntity(val scope: String, val id: String, val definition: String, val sourceId: String?,
    val enabled: Boolean, val generation: Long, val state: String, val reason: String,
    val nextAt: Long?, val occurrenceAt: Long?, val elapsedAt: Long?, val boot: Int?,
    val lastOccurrenceAt: Long?, val snoozeAt: Long?, val deliveryToken: String?, val updatedAt: Long)

@Entity(tableName = "alert_outbox", primaryKeys = ["scope", "id"], foreignKeys = [
    ForeignKey(entity = AlertEntity::class, parentColumns = ["scope", "id"], childColumns = ["scope", "id"], onDelete = ForeignKey.CASCADE)])
data class AlertOutboxEntity(val scope: String, val id: String, val generation: Long)

@Entity(tableName = "alert_delivery", primaryKeys = ["scope", "id", "token"], foreignKeys = [
    ForeignKey(entity = AlertEntity::class, parentColumns = ["scope", "id"], childColumns = ["scope", "id"], onDelete = ForeignKey.CASCADE)])
data class AlertDeliveryEntity(val scope: String, val id: String, val token: String, val plannedAt: Long,
    val receiverAt: Long, val publishedAt: Long?, val result: String, val action: String?)

@Dao
interface AlertDao {
    @Query("SELECT * FROM alert_record ORDER BY scope, nextAt, id") suspend fun all(): List<AlertEntity>
    @Query("SELECT * FROM alert_record WHERE scope=:scope AND id=:id") suspend fun get(scope: String, id: String): AlertEntity?
    @Query("SELECT * FROM alert_record WHERE scope=:scope ORDER BY nextAt,id") fun observe(scope: String): Flow<List<AlertEntity>>
    @Insert suspend fun insert(row: AlertEntity)
    @Update suspend fun update(row: AlertEntity)
    @Query("SELECT * FROM alert_outbox ORDER BY scope,id") suspend fun outbox(): List<AlertOutboxEntity>
    @Query("SELECT * FROM alert_outbox ORDER BY scope,id") fun observeOutbox(): Flow<List<AlertOutboxEntity>>
    @Query("DELETE FROM alert_outbox WHERE scope=:scope AND id=:id AND generation=:generation") suspend fun acknowledge(scope: String, id: String, generation: Long)
    @Query("SELECT * FROM alert_delivery WHERE scope=:scope AND id=:id AND token=:token") suspend fun delivery(scope: String, id: String, token: String): AlertDeliveryEntity?
    @Insert suspend fun insertDelivery(row: AlertDeliveryEntity)
    @Update suspend fun updateDelivery(row: AlertDeliveryEntity)
    @Query("SELECT * FROM alert_delivery WHERE scope=:scope ORDER BY receiverAt DESC LIMIT 100") suspend fun deliveries(scope: String): List<AlertDeliveryEntity>
}

object AlertMigration : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `alert_record` (`scope` TEXT NOT NULL, `id` TEXT NOT NULL, `definition` TEXT NOT NULL, `sourceId` TEXT, `enabled` INTEGER NOT NULL, `generation` INTEGER NOT NULL, `state` TEXT NOT NULL, `reason` TEXT NOT NULL, `nextAt` INTEGER, `occurrenceAt` INTEGER, `elapsedAt` INTEGER, `boot` INTEGER, `lastOccurrenceAt` INTEGER, `snoozeAt` INTEGER, `deliveryToken` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`scope`, `id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_alert_record_scope_sourceId` ON `alert_record` (`scope`, `sourceId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `alert_outbox` (`scope` TEXT NOT NULL, `id` TEXT NOT NULL, `generation` INTEGER NOT NULL, PRIMARY KEY(`scope`, `id`), FOREIGN KEY(`scope`, `id`) REFERENCES `alert_record`(`scope`, `id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `alert_delivery` (`scope` TEXT NOT NULL, `id` TEXT NOT NULL, `token` TEXT NOT NULL, `plannedAt` INTEGER NOT NULL, `receiverAt` INTEGER NOT NULL, `publishedAt` INTEGER, `result` TEXT NOT NULL, `action` TEXT, PRIMARY KEY(`scope`, `id`, `token`), FOREIGN KEY(`scope`, `id`) REFERENCES `alert_record`(`scope`, `id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        installConstraints(db)
    }
    fun installConstraints(db: SupportSQLiteDatabase) {
        val states = AlertState.entries.joinToString(",") { "'${it.name}'" }
        for (operation in listOf("INSERT", "UPDATE")) {
            db.execSQL("CREATE TRIGGER IF NOT EXISTS alert_${operation.lowercase()}_integrity BEFORE $operation ON alert_record WHEN NEW.scope NOT IN ('PERSONAL','DEMO') OR length(NEW.id)!=36 OR NEW.generation<1 OR NEW.state NOT IN ($states) OR length(NEW.definition)>5000 OR length(NEW.reason)>1000 BEGIN SELECT RAISE(ABORT, 'Invalid alert invariant'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS alert_${operation.lowercase()}_outbox AFTER $operation ON alert_record " +
                (if (operation == "UPDATE") "WHEN NEW.generation!=OLD.generation " else "") +
                // An outer Room UPDATE OR ABORT overrides an inner OR REPLACE on old SQLite.
                // UPDATE + guarded INSERT has no uniqueness conflict to override.
                "BEGIN UPDATE alert_outbox SET generation=NEW.generation WHERE scope=NEW.scope AND id=NEW.id; INSERT INTO alert_outbox(scope,id,generation) SELECT NEW.scope,NEW.id,NEW.generation WHERE NOT EXISTS (SELECT 1 FROM alert_outbox WHERE scope=NEW.scope AND id=NEW.id); END")
        }
        fun invalidate(name: String, operation: String, table: String, source: String, enabled: String = "enabled", whenClause: String = "") {
            db.execSQL("CREATE TRIGGER IF NOT EXISTS $name AFTER $operation ON $table $whenClause BEGIN UPDATE alert_record SET generation=generation+1,state='PENDING',reason='La actividad vinculada cambió; programación pendiente.',enabled=$enabled,nextAt=NULL,elapsedAt=NULL,snoozeAt=NULL,lastOccurrenceAt=NULL,deliveryToken=NULL WHERE scope=$source.scope AND sourceId=$source.${if(table=="activity") "id" else "activityId"} AND enabled=1; END")
        }
        invalidate("alert_parent_updated", "UPDATE", "activity", "NEW", "CASE WHEN NEW.status IN ('COMPLETED','CANCELLED') THEN 0 ELSE enabled END")
        invalidate("alert_parent_deleted", "DELETE", "activity", "OLD", "0")
        // Replacement changes also affect reminders attached to the original recurring series.
        db.execSQL("CREATE TRIGGER IF NOT EXISTS alert_replacement_updated AFTER UPDATE ON activity BEGIN UPDATE alert_record SET generation=generation+1,state='PENDING',nextAt=NULL,snoozeAt=NULL,lastOccurrenceAt=NULL,deliveryToken=NULL WHERE scope=NEW.scope AND enabled=1 AND sourceId IN (SELECT activityId FROM recurrence_exception WHERE scope=NEW.scope AND replacementActivityId=NEW.id); END")
        for (operation in listOf("INSERT","UPDATE","DELETE")) {
            val ref = if(operation=="DELETE") "OLD" else "NEW"
            invalidate("alert_rule_${operation.lowercase()}", operation, "recurrence_rule", ref)
            invalidate("alert_exception_${operation.lowercase()}", operation, "recurrence_exception", ref)
        }
    }
}

/** Upgrade the development v3 cut too; never rely on silently replacing an existing trigger. */
object AlertQueueMigration:Migration(3,4) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("DROP TRIGGER IF EXISTS alert_insert_outbox")
        db.execSQL("DROP TRIGGER IF EXISTS alert_update_outbox")
        AlertMigration.installConstraints(db)
    }
}

/** Versioned on-disk format; never deserialize an unknown version as defaults. */
object AlertCodec {
    fun encode(d: AlertDefinition): String {
        val o=JSONObject().put("v",1).put("id",d.id).put("kind",d.kind.name).put("title",d.title)
            .put("precision",d.precision.name).put("sound",d.sound.name).put("snooze",d.snoozeMinutes)
            .put("quiet",d.quietEnabled).put("quietStart",d.quietStart.toString()).put("quietEnd",d.quietEnd.toString())
        val s=JSONObject()
        when(val t=d.schedule) {
            is AlertSchedule.Local -> s.put("type","LOCAL").put("date",t.date.toString()).put("time",t.time.toString()).put("zone",t.zoneId)
                .put("days",JSONArray(t.weekdays.sortedBy { it.value }.map { it.value })).put("until",t.until?.toString())
            is AlertSchedule.Relative -> s.put("type","RELATIVE").put("at",t.at.toEpochMilli()).put("elapsed",t.elapsedMillis).put("boot",t.boot).put("zone",t.zoneId)
            is AlertSchedule.Linked -> s.put("type","LINKED").put("source",t.activityId).put("offset",t.offsetMinutes).put("zone",t.zoneId).put("hour",t.dateOnlyTime?.toString())
        }
        return o.put("schedule",s).toString()
    }
    fun decode(raw:String):AlertDefinition {
        val o=JSONObject(raw);require(o.getInt("v")==1) { "Formato de aviso no compatible." };val s=o.getJSONObject("schedule")
        val schedule=when(s.getString("type")) {
            "LOCAL" -> AlertSchedule.Local(LocalDate.parse(s.getString("date")),LocalTime.parse(s.getString("time")),s.getString("zone"),
                s.getJSONArray("days").let { a -> (0 until a.length()).map { DayOfWeek.of(a.getInt(it)) }.toSet() },s.optString("until").takeIf { it.isNotBlank() }?.let(LocalDate::parse))
            "RELATIVE" -> AlertSchedule.Relative(Instant.ofEpochMilli(s.getLong("at")),s.getLong("elapsed"),s.getInt("boot"),s.getString("zone"))
            "LINKED" -> AlertSchedule.Linked(s.getString("source"),s.getInt("offset"),s.getString("zone"),s.optString("hour").takeIf { it.isNotBlank() }?.let(LocalTime::parse))
            else -> error("Horario de aviso desconocido.")
        }
        return AlertDefinition(o.getString("id"),AlertKind.valueOf(o.getString("kind")),o.getString("title"),schedule,
            AlertPrecision.valueOf(o.getString("precision")),AlertSound.valueOf(o.getString("sound")),o.getInt("snooze"),o.getBoolean("quiet"),
            LocalTime.parse(o.getString("quietStart")),LocalTime.parse(o.getString("quietEnd"))).also(AlertRules::validate)
    }
}
