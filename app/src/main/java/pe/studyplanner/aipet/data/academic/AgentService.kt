package pe.studyplanner.aipet.data.academic

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.util.UUID
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.agent.*
import pe.studyplanner.aipet.domain.alerts.*
import pe.studyplanner.aipet.domain.calendar.*
import pe.studyplanner.aipet.domain.planner.*
import pe.studyplanner.aipet.domain.profile.*

/** Providers propose data. Only confirm(localId) crosses the transaction boundary. */
class AgentService(private val database:AcademicDatabase,private val alerts:AlertService,
    private val provider:AgentProvider=LocalRulesFallback(),private val clock:Clock=Clock.systemUTC(),
    private val beforeReceipt:suspend()->Unit={}) : AgentGateway {
    override val mode:String get()=provider.mode
    private val mutex=Mutex()
    private class Pending(val proposal:AgentProposal) { val cancelled=java.util.concurrent.atomic.AtomicBoolean(false) }
    private val pending=java.util.concurrent.ConcurrentHashMap<String,Pending>()
    private val dao=database.agentDao()
    private val academic=RoomAcademicRepository(database,clock)
    private val planner=RoomPlannerRepository(database,clock)

    override suspend fun prepare(scope:DatasetScope,text:String,requestId:String):AgentTurn=mutex.withLock {
        AgentCodec.uuid(requestId)
        require(text.isNotBlank() && text.length<=4000){"Escribe una solicitud de hasta 4000 caracteres."}
        pending.entries.removeAll{it.value.proposal.expiresAt<clock.instant()}
        val context=database.withTransaction {
            val profile=requireNotNull(database.academicDao().profile()){"Completa primero tu perfil local."}
            AgentContext(academic.snapshot(scope),profile.zoneId,clock.instant(),profile.preferencesRevision,
                database.alertDao().all().filter{it.scope==scope.name}.map{AgentAlertRef(it.id,AlertCodec.decode(it.definition).title,it.generation,it.enabled)})
        }
        when(val interpretation=provider.interpret(text,context)) {
            is AgentInterpretation.Clarification->AgentTurn.Clarify(interpretation.message)
            is AgentInterpretation.Answer->AgentTurn.Reply("No se ejecutó ninguna acción. ${interpretation.message}")
            is AgentInterpretation.Query->AgentTurn.Reply(query(interpretation.query,context))
            is AgentInterpretation.Candidate->{
                val action=AgentCodec.decode(AgentCodec.encode(interpretation.action))
                // A provider cannot turn a query or embedded task text into a destructive command.
                // Deletion requires the same literal, single-task intent under the local grammar.
                if(action is AgentAction.DeleteTask) {
                    val authorized=LocalRulesFallback().interpret(text,context) as? AgentInterpretation.Candidate
                    if(authorized?.action!=action) return@withLock AgentTurn.Clarify("Para eliminar una tarea, escribe una orden literal como: elimina la tarea \"Título exacto\". No se preparó ninguna eliminación.")
                }
                val hash=AgentCodec.hash(action)
                dao.receipt(scope.name,requestId)?.let { receipt->
                    require(receipt.payloadHash==hash){"Conflicto: ese identificador ya pertenece a otra solicitud."}
                    return@withLock AgentTurn.Reply(liveReceipt(receipt,true).message)
                }
                pending.values.map{it.proposal}.firstOrNull{it.scope==scope && it.requestId==requestId}?.let {
                    require(it.payloadHash==hash){"Conflicto: no se puede cambiar una solicitud ya revisada."}
                    return@withLock AgentTurn.Review(it)
                }
                val plan=if(action is AgentAction.PlanWeek) {
                    val c=planner.load(scope)
                    check(c.snapshot.revision==context.snapshot.revision && c.profileRevision==context.profileRevision){"La agenda cambió mientras se preparaba la propuesta. Reintenta."}
                    val goals=c.snapshot.activities.filter{it.kind in listOf(ActivityKind.TASK,ActivityKind.EXAM) && it.status in listOf(ActivityStatus.TODO,ActivityStatus.IN_PROGRESS)}
                    require(goals.isNotEmpty()){"No hay tareas o exámenes pendientes para planificar."}
                    require(goals.size<=50){"Hay más de 50 objetivos. Usa el planificador para seleccionar un grupo."}
                    planner.propose(scope,PlanRequest(action.start,action.days,goals.map { a->c.goalDefaults[a.id] ?: PlanGoal(a.id,a.estimateMinutes) }))
                } else null
                val preview=preview(action,context,plan)
                require(pending.size<50){"Revisa o descarta las propuestas pendientes antes de crear más."}
                val now=clock.instant()
                val proposal=AgentProposal(requestId=requestId,scope=scope,action=action,
                    agendaRevision=context.snapshot.revision,profileRevision=context.profileRevision,
                    createdAt=now,expiresAt=now.plusSeconds(600),payloadHash=hash,preview=preview,plan=plan)
                pending[proposal.id]=Pending(proposal)
                AgentTurn.Review(proposal)
            }
        }
    }

    override fun discard(proposalId:String) { pending.remove(proposalId)?.cancelled?.set(true) }

    override suspend fun confirm(proposalId:String):AgentReceipt=mutex.withLock {
        AgentCodec.uuid(proposalId)
        val state=pending[proposalId]
        if(state==null) {
            val saved=requireNotNull(dao.receiptByProposal(proposalId)){"Esta propuesta ya no está disponible. Vuelve a solicitarla; no se guardó ningún cambio pendiente."}
            reconcileAfterCommit()
            return@withLock liveReceipt(saved,true)
        }
        val proposal=state.proposal
        var replay=false
        val saved=database.withTransaction {
            dao.receipt(proposal.scope.name,proposal.requestId)?.let {
                require(it.payloadHash==proposal.payloadHash){"Conflicto entre identificador y contenido de la solicitud."}
                replay=true;return@withTransaction it
            }
            val now=clock.instant()
            check(!state.cancelled.get()){"La propuesta fue descartada; no se guardaron cambios."}
            require(now>=proposal.createdAt && now<=proposal.expiresAt){"La revisión caducó (10 minutos). Solicita una nueva propuesta."}
            check(AgentCodec.hash(proposal.action)==proposal.payloadHash){"El contenido cambió después de la revisión."}
            val profile=requireNotNull(database.academicDao().profile())
            if(academic.snapshot(proposal.scope).revision!=proposal.agendaRevision || profile.preferencesRevision!=proposal.profileRevision)throw StaleAcademicWriteException()
            val ids=execute(proposal)
            beforeReceipt() // All academic/profile/alert writes roll back with this receipt on failure.
            check(!state.cancelled.get()){"La propuesta fue descartada antes de guardar; no se guardaron cambios."}
            AgentReceiptEntity(proposal.scope.name,proposal.requestId,proposal.payloadHash,proposal.id,
                AgentCodec.tool(proposal.action),JSONArray(ids).toString(),
                JSONObject().put("message",resultMessage(proposal.action)).put("status","COMMITTED").toString(),clock.millis()).also{dao.insertReceipt(it)}
        }
        pending.remove(proposal.id)
        // OS effects are deliberately outside the business + receipt transaction. Outbox survives a crash here.
        reconcileAfterCommit()
        liveReceipt(saved,replay)
    }

    private suspend fun reconcileAfterCommit() {
        try { alerts.reconcile(false) } catch(e:CancellationException){throw e} catch(_:Exception){ /* Durable pending state is returned truthfully; normal startup/job also retries. */ }
    }

    private suspend fun execute(p:AgentProposal):List<String> {
        val scope=p.scope
        fun newId()=UUID.nameUUIDFromBytes("agent/${scope.name}/${p.requestId}/${AgentCodec.tool(p.action)}".toByteArray(Charsets.UTF_8)).toString()
        suspend fun task(id:String,version:Long):AcademicActivity {
            val a=academic.snapshot(scope).activities.firstOrNull{it.id==id} ?: throw StaleAcademicWriteException()
            require(a.kind==ActivityKind.TASK){"Esta acción solo admite una tarea individual del espacio actual."}
            if(a.version!=version)throw StaleAcademicWriteException()
            return a
        }
        suspend fun readBack(id:String):String=requireNotNull(database.academicDao().activity(scope.name,id)){"No se confirmó la actividad en Room."}.id
        return when(val a=p.action) {
            is AgentAction.CreateTask->{
                require(a.time !is ActivityTime.Timed || a.time.end==null){"Una tarea tiene vencimiento; no recibe una duración horaria."}
                val saved=academic.saveActivity(scope,AcademicActivity(id=newId(),kind=ActivityKind.TASK,title=a.title,courseId=a.courseId,time=a.time,priority=a.priority,estimateMinutes=a.estimateMinutes))
                listOf(readBack(saved.id))
            }
            is AgentAction.CreateExam->{val saved=academic.saveActivity(scope,AcademicActivity(id=newId(),kind=ActivityKind.EXAM,title=a.title,courseId=a.courseId,time=a.time,fixed=true));listOf(readBack(saved.id))}
            is AgentAction.RenameTask->{val old=task(a.id,a.version);val saved=academic.saveActivity(scope,old.copy(title=a.title));listOf(readBack(saved.id))}
            is AgentAction.CompleteTask->{val old=task(a.id,a.version);val saved=academic.saveActivity(scope,old.copy(status=ActivityStatus.COMPLETED));listOf(readBack(saved.id))}
            is AgentAction.DeleteTask->{task(a.id,a.version);academic.deleteActivity(scope,a.id,a.version);check(database.academicDao().activity(scope.name,a.id)==null);listOf(a.id)}
            is AgentAction.SetAlarm->{
                val d=definition(a,newId());AlertRules.validate(d)
                val next=requireNotNull(AlertRules.next(d,academic.snapshot(scope),clock.instant(),alerts.platform.boot(),alerts.platform.elapsed()))
                require(next.at>clock.instant()){"El aviso debe estar en el futuro."}
                require(database.alertDao().all().count{it.scope==scope.name && it.enabled}<50){"Ya hay 50 avisos activos en este espacio."}
                database.alertDao().insert(AlertEntity(scope.name,d.id,AlertCodec.encode(d),null,true,1,"PENDING","Guardado por el agente; programación pendiente.",null,null,null,null,null,null,null,clock.millis()))
                listOf(requireNotNull(database.alertDao().get(scope.name,d.id)).id)
            }
            is AgentAction.CancelAlarm->{
                val old=requireNotNull(database.alertDao().get(scope.name,a.id)){"El aviso no pertenece al espacio actual."}
                check(old.generation==a.generation && old.enabled){"El aviso cambió o ya estaba desactivado. Revísalo otra vez."}
                database.alertDao().update(old.copy(enabled=false,generation=old.generation+1,state="PENDING",reason="Desactivado por confirmación del agente.",nextAt=null,elapsedAt=null,lastOccurrenceAt=null,snoozeAt=null,deliveryToken=null,updatedAt=clock.millis()))
                listOf(requireNotNull(database.alertDao().get(scope.name,a.id)).id)
            }
            is AgentAction.PlanWeek->{val draft=requireNotNull(p.plan);val receipt=planner.apply(draft);receipt.sessionIds.map{readBack(it)}}
            is AgentAction.ChangePetName,AgentAction.HidePet->{
                val old=requireNotNull(database.academicDao().profile())
                val domain=UserProfile(old.studentName,old.petName,AvatarStyle.valueOf(old.avatar),old.zoneId,DayOfWeek.valueOf(old.weekStartsOn),ThemeMode.valueOf(old.theme),old.onboardingCompleted,old.petVisible)
                val changed=when(a){is AgentAction.ChangePetName->domain.copy(petName=a.name);else->domain.copy(petVisible=false)}
                require(ProfileValidator.validate(changed).isEmpty()){"El perfil propuesto contiene campos inválidos."}
                val normalized=ProfileValidator.normalize(changed)
                database.academicDao().updateProfile(old.copy(petName=normalized.petName,petVisible=normalized.petVisible,preferencesRevision=old.preferencesRevision+1))
                val actual=requireNotNull(database.academicDao().profile());check(actual.petName==normalized.petName && actual.petVisible==normalized.petVisible)
                listOf(actual.id.toString())
            }
        }
    }

    private fun definition(a:AgentAction.SetAlarm,id:String):AlertDefinition {
        val local=a.at.atZone(ZoneId.of(a.zone))
        return AlertDefinition(id=id,kind=if(a.reminder)AlertKind.REMINDER else AlertKind.ALARM,title=a.title.trim(),
            schedule=AlertSchedule.Local(local.toLocalDate(),local.toLocalTime(),a.zone),precision=a.precision)
    }

    private fun preview(a:AgentAction,c:AgentContext,plan:PlanDraft?):String {
        val scope="Espacio: ${c.snapshot.scope.name}. Revisión de agenda: ${c.snapshot.revision}."
        fun task(id:String,version:Long):AcademicActivity {val row=requireNotNull(c.snapshot.activities.firstOrNull{it.id==id}){"La tarea no pertenece al espacio actual."};require(row.kind==ActivityKind.TASK && row.version==version){"La tarea cambió o no es una tarea individual."};return row}
        fun course(id:String?):String {if(id==null)return "Sin curso";val course=requireNotNull(c.snapshot.courses.firstOrNull{it.id==id && !it.archived}){"El curso no existe aquí o está archivado."};return "Curso: ${course.name} (${course.id})"}
        val detail=when(a) {
            is AgentAction.CreateTask->{require(a.time !is ActivityTime.Timed || a.time.end==null);"Crear tarea: ${a.title}\n${course(a.courseId)}\n${timeText(a.time,c.zoneId)}\nPrioridad: ${a.priority}. Esfuerzo: ${a.estimateMinutes?.let{"$it min"} ?: "sin estimar"}."}
            is AgentAction.CreateExam->"Crear examen: ${a.title}\n${course(a.courseId)}\n${timeText(a.time,c.zoneId)}"
            is AgentAction.RenameTask->{val old=task(a.id,a.version);"Renombrar tarea: ${old.title} → ${a.title}\nID: ${old.id}. Versión: ${old.version}."}
            is AgentAction.CompleteTask->{val old=task(a.id,a.version);"Completar tarea: ${old.title}\nID: ${old.id}. Versión: ${old.version}. Los avisos vinculados se desactivarán."}
            is AgentAction.DeleteTask->{val old=task(a.id,a.version);"Eliminar solo esta tarea: ${old.title}\nID: ${old.id}. Versión: ${old.version}. No se puede deshacer desde el chat. Si tiene sesiones vinculadas, se rechazará la eliminación. Sus avisos se desactivarán."}
            is AgentAction.SetAlarm->{require(a.at>c.now){"La hora del aviso ya pasó."};AlertRules.validate(definition(a,UUID.randomUUID().toString()));"Crear ${if(a.reminder)"recordatorio" else "alarma"}: ${a.title}\n${a.at.atZone(ZoneId.of(a.zone))}\nPrecisión elegida: ${a.precision}. Sonido activado; posposición 10 min.${if(a.reminder)" Silencio nocturno 22:00–07:00." else ""}\nSe comprobarán permisos y canal después de guardar; no se garantiza que Android lo muestre o suene."}
            is AgentAction.CancelAlarm->{val row=requireNotNull(c.alerts.firstOrNull{it.id==a.id && it.enabled && it.generation==a.generation}){"El aviso cambió o no está activo en este espacio."};"Desactivar aviso: ${row.title}\nID: ${row.id}. Generación: ${row.generation}. Se conserva la actividad."}
            is AgentAction.PlanWeek->{val d=requireNotNull(plan);buildString{append("Planificar ${a.days} días desde ${a.start}, zona ${d.zoneId}.\n");for(g in d.goals)append("${g.title}: estimado ${g.required ?: "sin estimar"}, ya cubierto ${g.credited} min, propuesto ${g.assigned} min, pendiente ${g.pending ?: "sin estimar"}. ${g.reason}\n");for(change in d.changes){append("${if(change.before==null)"Añadir" else if(change.after==null)"Eliminar" else "Modificar"} sesión para ${change.goalId}: ${change.after?.let{timeText(it.time,d.zoneId)} ?: change.before?.id}\n")};if(d.changes.isEmpty())append("No hay cambios para aplicar. Ajusta esfuerzo o disponibilidad.")}}
            is AgentAction.ChangePetName->{require(a.name.isNotBlank());"Cambiar nombre de la mascota a: ${a.name}. Este perfil es global y se comparte entre PERSONAL y DEMO."}
            AgentAction.HidePet->"Ocultar la mascota en el perfil global. Las actividades y los avisos se conservan. Puedes mostrarla después desde Inicio."
        }
        return "$scope\n$detail\nNada se guarda hasta confirmar esta propuesta local. Caduca en 10 minutos."
    }

    private fun timeText(t:ActivityTime,zone:String):String=when(t) {
        ActivityTime.Unscheduled->"Sin fecha ni hora."
        is ActivityTime.DateOnly->"Fecha: ${t.date}; sin hora."
        is ActivityTime.Timed->"Inicio/vencimiento: ${t.start.atZone(ZoneId.of(t.zoneId))}${t.end?.let{"; final: ${it.atZone(ZoneId.of(t.zoneId))}"}.orEmpty()}."
        is ActivityTime.Weekly->"Serie semanal en $zone."
    }
    private fun query(q:AgentQuery,c:AgentContext):String {
        fun <T> bounded(rows:List<T>,render:(T)->String):String=rows.take(100).joinToString("\n",transform=render)+(if(rows.size>100)"\nHay más resultados. Abre el formulario correspondiente para consultar el resto." else "")
        return when(q) {
        AgentQuery.Tasks->{val list=c.snapshot.activities.filter{it.kind==ActivityKind.TASK && it.status in setOf(ActivityStatus.TODO,ActivityStatus.IN_PROGRESS)};if(list.isEmpty())"No hay tareas pendientes en ${c.snapshot.scope}." else bounded(list){"${it.title} · ${it.status} · ${timeText(it.time,c.zoneId)} · ID ${it.id}"}}
        AgentQuery.Courses->if(c.snapshot.courses.isEmpty())"No hay cursos en ${c.snapshot.scope}." else bounded(c.snapshot.courses){"${it.name}${if(it.archived)" (archivado)" else ""} · ID ${it.id}"}
        AgentQuery.Alarms->if(c.alerts.isEmpty())"No hay avisos en ${c.snapshot.scope}." else bounded(c.alerts){"${it.title} · ${if(it.enabled)"activo" else "desactivado"} · ID ${it.id}"}
        is AgentQuery.Calendar->{require(q.days in 1..31);val result=CalendarEngine.project(c.snapshot,CalendarRange(q.start,q.start.plusDays(q.days.toLong()-1),c.zoneId));if(result.issues.isNotEmpty())"Hay incidencias en la agenda: ${result.issues.take(100).joinToString{it.message}}" else if(result.entries.isEmpty())"No hay actividades en ese intervalo (${c.snapshot.scope})." else bounded(result.entries){"${it.title} · ${it.start ?: "${it.date}; sin hora"}"}}
        }
    }
    private fun resultMessage(a:AgentAction):String=when(a) {
        is AgentAction.CreateTask->"Tarea guardada y verificada en la agenda local."
        is AgentAction.CreateExam->"Examen guardado y verificado en la agenda local."
        is AgentAction.RenameTask->"Nombre de la tarea actualizado y verificado."
        is AgentAction.CompleteTask->"Tarea marcada como completada y verificada."
        is AgentAction.DeleteTask->"Tarea eliminada de la agenda local."
        is AgentAction.SetAlarm->"Aviso guardado; consulta el estado real de programación."
        is AgentAction.CancelAlarm->"Aviso desactivado en la base local."
        is AgentAction.PlanWeek->"Sesiones propuestas guardadas y verificadas en la agenda."
        is AgentAction.ChangePetName->"Nombre de la mascota actualizado y verificado en el perfil local."
        AgentAction.HidePet->"Mascota oculta en el perfil local."
    }
    private suspend fun liveReceipt(row:AgentReceiptEntity,replay:Boolean):AgentReceipt {
        val ids=JSONArray(row.entityIds).let{a->(0 until a.length()).map{a.getString(it)}}
        val result=JSONObject(row.result)
        var status=result.getString("status");var message=result.getString("message");var state:String?=null
        if(row.tool in setOf("set_alarm","set_reminder","cancel_alarm")) {
            val alert=ids.firstOrNull()?.let{database.alertDao().get(row.scope,it)}
            state=alert?.state ?: "MISSING"
            status=when(state){"EXACT","FLEXIBLE","DISABLED"->"COMMITTED";"PENDING","FAILED"->"SAVED_PENDING";else->"SAVED_BLOCKED"}
            message=if(alert==null)"El comprobante existe, pero el aviso ya no está disponible." else "Aviso ${alert.id}: ${alert.reason} Estado actual: ${alert.state}."
        }
        return AgentReceipt(row.requestId,row.proposalId,DatasetScope.valueOf(row.scope),row.tool,ids,message,status,state,replay)
    }

    override suspend fun history(scope:DatasetScope):List<AgentMessage> = database.withTransaction {
        if(dao.preferences(scope.name)?.historyEnabled!=true)emptyList() else dao.messages(scope.name).map{AgentMessage(it.id,it.role,it.text)}
    }
    override suspend fun historyEnabled(scope:DatasetScope):Boolean=dao.preferences(scope.name)?.historyEnabled ?: false
    override suspend fun setHistoryEnabled(scope:DatasetScope,enabled:Boolean) { dao.putPreferences(AgentPreferencesEntity(scope.name,enabled)) }
    override suspend fun clearHistory(scope:DatasetScope) { mutex.withLock {
        pending.values.filter{it.proposal.scope==scope}.forEach{it.cancelled.set(true)}
        pending.entries.removeAll{it.value.proposal.scope==scope};dao.clearMessages(scope.name)
    } }
    override suspend fun recordMessage(scope:DatasetScope,message:AgentMessage) { database.withTransaction {
        if(dao.preferences(scope.name)?.historyEnabled!=true)return@withTransaction
        AgentCodec.uuid(message.id);require(message.role in setOf("user","assistant") && message.text.isNotBlank() && message.text.length<=8000){"Mensaje inválido."}
        dao.insertMessage(AgentMessageEntity(scope.name,message.id,message.role,message.text,clock.millis()));dao.trimMessages(scope.name)
    } }
}
