package pe.studyplanner.aipet.data.academic

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Kept separately from conversation text: clearing chat cannot erase an execution receipt. */
@Entity(tableName="agent_receipt", primaryKeys=["scope","requestId"], indices=[Index(value=["proposalId"],unique=true)])
data class AgentReceiptEntity(val scope:String,val requestId:String,val payloadHash:String,
    val proposalId:String,val tool:String,val entityIds:String,val result:String,val createdAt:Long)

@Entity(tableName="agent_message",primaryKeys=["scope","id"],indices=[Index(value=["scope","timestamp"])])
data class AgentMessageEntity(val scope:String,val id:String,val role:String,val text:String,val timestamp:Long)

@Entity(tableName="agent_preferences")
data class AgentPreferencesEntity(@PrimaryKey val scope:String,val historyEnabled:Boolean=false)

@Dao
interface AgentDao {
    @Query("SELECT * FROM agent_receipt WHERE scope=:scope AND requestId=:requestId")
    suspend fun receipt(scope:String,requestId:String):AgentReceiptEntity?
    @Query("SELECT * FROM agent_receipt WHERE proposalId=:proposalId")
    suspend fun receiptByProposal(proposalId:String):AgentReceiptEntity?
    @Insert suspend fun insertReceipt(row:AgentReceiptEntity)
    @Query("SELECT * FROM agent_preferences WHERE scope=:scope") suspend fun preferences(scope:String):AgentPreferencesEntity?
    @Upsert suspend fun putPreferences(row:AgentPreferencesEntity)
    @Query("SELECT * FROM agent_message WHERE scope=:scope ORDER BY timestamp,rowid") suspend fun messages(scope:String):List<AgentMessageEntity>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertMessage(row:AgentMessageEntity)
    @Query("DELETE FROM agent_message WHERE scope=:scope") suspend fun clearMessages(scope:String)
    @Query("DELETE FROM agent_message WHERE scope=:scope AND id NOT IN (SELECT id FROM agent_message WHERE scope=:scope ORDER BY timestamp DESC,rowid DESC LIMIT 100)")
    suspend fun trimMessages(scope:String)
}

object AgentMigration:Migration(4,5) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `agent_receipt` (`scope` TEXT NOT NULL, `requestId` TEXT NOT NULL, `payloadHash` TEXT NOT NULL, `proposalId` TEXT NOT NULL, `tool` TEXT NOT NULL, `entityIds` TEXT NOT NULL, `result` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`scope`,`requestId`))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_agent_receipt_proposalId` ON `agent_receipt` (`proposalId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `agent_message` (`scope` TEXT NOT NULL, `id` TEXT NOT NULL, `role` TEXT NOT NULL, `text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`scope`,`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_message_scope_timestamp` ON `agent_message` (`scope`,`timestamp`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `agent_preferences` (`scope` TEXT NOT NULL, `historyEnabled` INTEGER NOT NULL, PRIMARY KEY(`scope`))")
        installConstraints(db)
    }
    fun installConstraints(db:SupportSQLiteDatabase) {
        val rules=mapOf(
            "agent_receipt" to "NEW.scope NOT IN ('PERSONAL','DEMO') OR length(NEW.requestId)!=36 OR length(NEW.proposalId)!=36 OR length(NEW.payloadHash)!=64 OR length(NEW.entityIds)>64000 OR length(NEW.result)>64000 OR length(NEW.tool) NOT BETWEEN 1 AND 40",
            "agent_message" to "NEW.scope NOT IN ('PERSONAL','DEMO') OR length(NEW.id)!=36 OR NEW.role NOT IN ('user','assistant') OR length(NEW.text) NOT BETWEEN 1 AND 8000",
            "agent_preferences" to "NEW.scope NOT IN ('PERSONAL','DEMO') OR NEW.historyEnabled NOT IN (0,1)"
        )
        for((table,rule) in rules) for(operation in listOf("INSERT","UPDATE"))
            db.execSQL("CREATE TRIGGER IF NOT EXISTS ${table}_${operation.lowercase()}_integrity BEFORE $operation ON $table WHEN $rule BEGIN SELECT RAISE(ABORT,'Invalid $table invariant'); END")
    }
}
