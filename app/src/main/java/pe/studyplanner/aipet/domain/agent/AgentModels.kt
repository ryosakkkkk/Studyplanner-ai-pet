package pe.studyplanner.aipet.domain.agent

import java.time.*
import java.util.UUID
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.alerts.AlertPrecision
import pe.studyplanner.aipet.domain.planner.PlanDraft

/** Provider output is a candidate. Only a locally reviewed proposal can be committed. */
sealed interface AgentAction {
    data class CreateTask(val title:String,val time:ActivityTime=ActivityTime.Unscheduled,
        val priority:Priority=Priority.MEDIUM,val courseId:String?=null,val estimateMinutes:Int?=null):AgentAction
    data class CreateExam(val title:String,val time:ActivityTime,val courseId:String?=null):AgentAction
    data class RenameTask(val id:String,val version:Long,val title:String):AgentAction
    data class CompleteTask(val id:String,val version:Long):AgentAction
    data class DeleteTask(val id:String,val version:Long):AgentAction
    data class SetAlarm(val title:String,val at:Instant,val zone:String,
        val precision:AlertPrecision=AlertPrecision.EXACT,val reminder:Boolean=false):AgentAction
    data class CancelAlarm(val id:String,val generation:Long):AgentAction
    data class PlanWeek(val start:LocalDate,val days:Int=7):AgentAction
    data class ChangePetName(val name:String):AgentAction
    data object HidePet:AgentAction
}

sealed interface AgentQuery {
    data object Tasks:AgentQuery
    data object Courses:AgentQuery
    data object Alarms:AgentQuery
    data class Calendar(val start:LocalDate,val days:Int=7):AgentQuery
}
data class AgentAlertRef(val id:String,val title:String,val generation:Long,val enabled:Boolean)
data class AgentContext(val snapshot:AcademicSnapshot,val zoneId:String,val now:Instant,
    val profileRevision:Long=0,val alerts:List<AgentAlertRef> = emptyList())

sealed interface AgentInterpretation {
    data class Candidate(val action:AgentAction):AgentInterpretation
    data class Query(val query:AgentQuery):AgentInterpretation
    data class Clarification(val message:String):AgentInterpretation
    data class Answer(val message:String):AgentInterpretation
}
interface AgentProvider {
    val mode:String
    suspend fun interpret(text:String,context:AgentContext):AgentInterpretation
}

data class AgentProposal(val id:String=UUID.randomUUID().toString(),val requestId:String,
    val scope:DatasetScope,val action:AgentAction,val agendaRevision:Long,val profileRevision:Long,
    val createdAt:Instant,val expiresAt:Instant,val payloadHash:String,val preview:String,
    val plan:PlanDraft?=null)
data class AgentReceipt(val requestId:String,val proposalId:String,val scope:DatasetScope,
    val tool:String,val entityIds:List<String>,val message:String,val status:String,
    val schedulerState:String?=null,val replay:Boolean=false)
sealed interface AgentTurn {
    data class Reply(val text:String):AgentTurn
    data class Clarify(val text:String):AgentTurn
    data class Review(val proposal:AgentProposal):AgentTurn
}
data class AgentMessage(val id:String=UUID.randomUUID().toString(),val role:String,val text:String)

interface AgentGateway {
    val mode:String
    suspend fun prepare(scope:DatasetScope,text:String,requestId:String=UUID.randomUUID().toString()):AgentTurn
    suspend fun confirm(proposalId:String):AgentReceipt
    fun discard(proposalId:String)
    suspend fun history(scope:DatasetScope):List<AgentMessage>
    suspend fun historyEnabled(scope:DatasetScope):Boolean
    suspend fun setHistoryEnabled(scope:DatasetScope,enabled:Boolean)
    suspend fun clearHistory(scope:DatasetScope)
    suspend fun recordMessage(scope:DatasetScope,message:AgentMessage)
}
