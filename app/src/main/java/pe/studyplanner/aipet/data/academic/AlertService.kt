package pe.studyplanner.aipet.data.academic

import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.alerts.*

data class AlertCapability(val notifications: Boolean, val exact: Boolean, val reason: String = "")

interface AlertPlatform {
    fun boot(): Int
    fun elapsed(): Long
    fun capability(d: AlertDefinition, fire: AlertFire): AlertCapability
    fun cancel(scope: String, id: String)
    fun cancelNotification(scope: String, id: String)
    fun schedule(row: AlertEntity, d: AlertDefinition, fire: AlertFire)
    /** API acceptance is a publication attempt, never proof that a person saw/heard the alert. */
    fun post(row: AlertEntity, d: AlertDefinition, fire: AlertFire, token: String, canComplete: Boolean)
}

/** DB is authoritative. The outbox survives process death; all OS operations are generation guarded. */
class AlertService(val database: AcademicDatabase, val platform: AlertPlatform,
    private val clock: Clock = Clock.systemUTC(), private val beforeSaveCommit: () -> Unit = {}) {
    private val mutex=Mutex()
    private val dao=database.alertDao()
    private val academic=RoomAcademicRepository(database,clock)

    suspend fun save(scope: DatasetScope, definition: AlertDefinition, expectedGeneration: Long = 0, expectedAcademicRevision: Long? = null): AlertEntity = mutex.withLock {
        AlertRules.validate(definition)
        database.withTransaction {
            val old=dao.get(scope.name,definition.id)
            check((old?.generation ?: 0)==expectedGeneration) { "El aviso cambió. Recarga antes de guardar." }
            val snapshot=academic.snapshot(scope)
            if(definition.schedule is AlertSchedule.Linked) check(expectedAcademicRevision==snapshot.revision) { "La actividad cambió desde la revisión. Recarga y revisa de nuevo su horario." }
            val fire=requireNotNull(AlertRules.next(definition,snapshot,clock.instant(),platform.boot(),platform.elapsed())) { "No hay una próxima ocurrencia. Revisa la fecha o repetición." }
            require(fire.at>clock.instant()) { "El aviso debe ser futuro. No programamos avisos pasados." }
            require(old?.enabled==true || dao.all().count { it.scope==scope.name && it.enabled }<50) { "Máximo 50 avisos activos por espacio. Desactiva uno antes de añadir otro." }
            val row=AlertEntity(scope.name,definition.id,AlertCodec.encode(definition.copy(title=definition.title.trim())),
                (definition.schedule as? AlertSchedule.Linked)?.activityId,true,(old?.generation ?: 0)+1,
                AlertState.PENDING.name,"Guardado; programación pendiente.",null,null,null,null,null,null,null,clock.millis())
            if(old==null) dao.insert(row) else dao.update(row)
            beforeSaveCommit()
        }
        reconcileInside(false)
        requireNotNull(dao.get(scope.name,definition.id))
    }

    suspend fun setEnabled(scope: DatasetScope,id:String,generation:Long,enabled:Boolean) = mutex.withLock {
        database.withTransaction {
            val old=requireNotNull(dao.get(scope.name,id));check(old.generation==generation) { "El aviso cambió. Recarga la lista." }
            if(enabled) {
                val fire=AlertRules.next(AlertCodec.decode(old.definition),academic.snapshot(scope),clock.instant(),platform.boot(),platform.elapsed())
                require(fire!=null && fire.at>clock.instant()) { "Edita la fecha antes de reactivar este aviso." }
                require(dao.all().count { it.scope==scope.name && it.enabled }<50) { "Ya hay 50 avisos activos." }
            }
            dao.update(old.copy(enabled=enabled,generation=old.generation+1,state="PENDING",nextAt=null,
                lastOccurrenceAt=null,snoozeAt=null,deliveryToken=null,updatedAt=clock.millis()))
        }
        reconcileInside(false)
    }

    suspend fun reconcile(all:Boolean=true) = mutex.withLock { reconcileInside(all) }

    private suspend fun reconcileInside(all:Boolean) {
        val ids=if(all) dao.all().map { it.scope to it.id } else dao.outbox().map { it.scope to it.id }
        for((scope,id) in ids) database.withTransaction {
            val row=dao.get(scope,id) ?: return@withTransaction
            try {
                // Application.onCreate can run just before an alarm receiver in a new process.
                // Do not steal a due, still-valid generation from that receiver while recovering.
                val due=if(row.elapsedAt!=null && row.boot==platform.boot()) row.elapsedAt-platform.elapsed() else row.nextAt?.minus(clock.millis())
                val grace=if(row.state=="FLEXIBLE") 7_200_000L else 900_000L
                if(row.state in setOf("EXACT","FLEXIBLE") && row.enabled && row.boot==platform.boot() && due!=null && due in -grace..0L) {
                    dao.acknowledge(scope,id,row.generation);return@withTransaction
                }
                // Identity is URI + explicit component, never an Int hash susceptible to collisions.
                platform.cancel(scope,id)
                if(!row.enabled || row.deliveryToken==null) platform.cancelNotification(scope,id)
                if(!row.enabled) {
                    dao.update(row.copy(state="DISABLED",reason="Desactivado. La actividad se conserva.",nextAt=null,elapsedAt=null))
                    dao.acknowledge(scope,id,row.generation);return@withTransaction
                }
                val d=AlertCodec.decode(row.definition)
                val snapshot=academic.snapshot(DatasetScope.valueOf(scope))
                val claim=row.deliveryToken?.let { dao.delivery(scope,id,it) }
                if(claim?.result=="CLAIMED") dao.updateDelivery(claim.copy(result="UNCERTAIN"))
                val fire=if(row.snoozeAt!=null) {
                    val mono=row.elapsedAt?.takeIf { row.boot==platform.boot() }
                    val at=if(mono==null) Instant.ofEpochMilli(row.snoozeAt) else clock.instant().plusMillis(mono-platform.elapsed())
                    AlertFire(at,Instant.ofEpochMilli(requireNotNull(row.lastOccurrenceAt)),mono,
                        silentNight=d.kind==AlertKind.REMINDER && d.quietEnabled && AlertRules.inQuiet(at.atZone(ZoneId.of(zone(d))).toLocalTime(),d.quietStart,d.quietEnd),note="Posposición solicitada.")
                } else AlertRules.next(d,snapshot,clock.instant(),platform.boot(),platform.elapsed(),row.lastOccurrenceAt?.let(Instant::ofEpochMilli))
                if(fire==null) {
                    val state=if(claim?.result in setOf("CLAIMED","UNCERTAIN")) "UNCERTAIN" else if(claim?.result=="POSTED") "POSTED" else "MISSED"
                    dao.update(row.copy(state=state,reason=if(state=="POSTED") "Publicación aceptada por Android; recepción visual no comprobada." else "Sin una próxima ocurrencia. No se repiten avisos vencidos o inciertos.",nextAt=null,elapsedAt=null))
                } else if(fire.at<=clock.instant()) {
                    dao.update(row.copy(state="MISSED",reason="El horario venció. No se reproduce una ráfaga al recuperar la app.",nextAt=null,elapsedAt=null))
                } else {
                    val capability=platform.capability(d,fire)
                    val state=when { !capability.notifications -> AlertState.NEEDS_NOTIFICATIONS; d.precision==AlertPrecision.EXACT && !capability.exact -> AlertState.NEEDS_EXACT; d.precision==AlertPrecision.EXACT -> AlertState.EXACT; else -> AlertState.FLEXIBLE }
                    val reason=when(state) {
                        AlertState.NEEDS_NOTIFICATIONS -> "Aviso guardado, sin programación visible: ${capability.reason}"
                        AlertState.NEEDS_EXACT -> "Falta acceso a alarmas exactas. Concede acceso o edita y elige flexible; no se cambia automáticamente."
                        AlertState.EXACT -> "API exacta aceptada. Android, el canal y No molestar pueden limitar la entrega."
                        else -> "API flexible aceptada; Android puede retrasar el aviso."
                    } + (if(fire.silentNight) " Silencio nocturno: sin sonido ni vibración." else "") + " " + fire.note
                    val scheduled=row.copy(state=state.name,reason=reason.trim(),nextAt=fire.at.toEpochMilli(),
                        occurrenceAt=fire.occurrence.toEpochMilli(),elapsedAt=fire.elapsedMillis,boot=platform.boot())
                    if(state in setOf(AlertState.EXACT,AlertState.FLEXIBLE)) platform.schedule(scheduled,d,fire)
                    dao.update(scheduled)
                }
                dao.acknowledge(scope,id,row.generation)
            } catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled } catch(error:Exception) {
                // Keep the outbox: a later launch/job can recover after transient OS/IO failures.
                android.util.Log.e("StudyPlannerAlerts","Programación pendiente de recuperación",error)
                dao.update(row.copy(state="FAILED",reason=if(error is SecurityException) "Android rechazó el acceso. Revisa los permisos y vuelve a intentar." else "No se pudo programar. Los datos se conservan y se reintentará la recuperación.",nextAt=null))
            }
        }
    }

    suspend fun fire(scope:String,id:String,generation:Long) = mutex.withLock {
        val token="$generation"
        var definition:AlertDefinition?=null
        var fire:AlertFire?=null
        var claimed:AlertEntity?=null
        var canComplete=false
        database.withTransaction {
            val row=dao.get(scope,id) ?: return@withTransaction
            if(!row.enabled || row.generation!=generation || row.state !in setOf("EXACT","FLEXIBLE") || row.nextAt==null || dao.delivery(scope,id,token)!=null) return@withTransaction
            val d=AlertCodec.decode(row.definition)
            val snapshot=academic.snapshot(DatasetScope.valueOf(scope))
            val source=(d.schedule as? AlertSchedule.Linked)?.let { s -> snapshot.activities.firstOrNull { it.id==s.activityId } }
            if(row.sourceId!=null && (source==null || source.status in setOf(ActivityStatus.COMPLETED,ActivityStatus.CANCELLED))) return@withTransaction
            val remaining=if(row.elapsedAt!=null && row.boot==platform.boot()) row.elapsedAt-platform.elapsed() else row.nextAt-clock.millis()
            if(remaining>1000) return@withTransaction // premature/stale delivery cannot create an early notification
            val grace=if(d.precision==AlertPrecision.FLEXIBLE) 7_200_000L else 900_000L
            if(remaining < -grace) {
                dao.insertDelivery(AlertDeliveryEntity(scope,id,token,row.nextAt,clock.millis(),null,"MISSED",null))
                dao.update(row.copy(state="MISSED",reason="Receiver fuera de tolerancia (${grace/60_000} min). No se reproduce el aviso vencido.",
                    nextAt=null,lastOccurrenceAt=row.occurrenceAt,generation=row.generation+1));return@withTransaction
            }
            val resolved=AlertFire(Instant.ofEpochMilli(row.nextAt),Instant.ofEpochMilli(requireNotNull(row.occurrenceAt)),row.elapsedAt,
                AlertRules.inQuiet(Instant.ofEpochMilli(row.nextAt).atZone(ZoneId.of(zone(d))).toLocalTime(),d.quietStart,d.quietEnd) && d.quietEnabled && d.kind==AlertKind.REMINDER)
            if(!platform.capability(d,resolved).notifications) {
                dao.update(row.copy(state="NEEDS_NOTIFICATIONS",reason="El sistema o canal bloqueó notificaciones antes del disparo.",nextAt=null));return@withTransaction
            }
            val c=row.copy(state="UNCERTAIN",reason="Receiver recibido; publicación aún no confirmada.",deliveryToken=token,
                nextAt=null,lastOccurrenceAt=row.occurrenceAt,snoozeAt=null,updatedAt=clock.millis())
            dao.insertDelivery(AlertDeliveryEntity(scope,id,token,row.nextAt,clock.millis(),null,"CLAIMED",null))
            dao.update(c);definition=d;fire=resolved;claimed=c
            canComplete=source?.kind==ActivityKind.TASK
        }
        val c=claimed ?: run { reconcileInside(false);return@withLock }
        // A durable claim precedes the OS effect. Interrupted claims are never blindly replayed.
        database.withTransaction {
            val current=dao.get(scope,id) ?: return@withTransaction
            val delivery=requireNotNull(dao.delivery(scope,id,token))
            if(current.generation!=generation || !current.enabled) { dao.updateDelivery(delivery.copy(result="CANCELLED"));return@withTransaction }
            try {
                platform.post(c,requireNotNull(definition),requireNotNull(fire),token,canComplete)
                dao.updateDelivery(delivery.copy(publishedAt=clock.millis(),result="POSTED"))
                dao.update(current.copy(state="POSTED",reason="Publicación aceptada por Android; no prueba que se vio o escuchó.",generation=current.generation+1))
            } catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled } catch(error:Exception) {
                dao.updateDelivery(delivery.copy(result="UNCERTAIN"))
                dao.update(current.copy(state="UNCERTAIN",generation=current.generation+1,reason="No se pudo confirmar la publicación. No se repetirá automáticamente."))
            }
        }
        reconcileInside(false)
    }

    suspend fun action(scope:String,id:String,token:String,action:String):Boolean = mutex.withLock {
        require(action in setOf("SNOOZE","COMPLETE")) { "Acción de aviso desconocida." }
        val applied=database.withTransaction {
            val row=dao.get(scope,id) ?: return@withTransaction false
            val delivery=dao.delivery(scope,id,token) ?: return@withTransaction false
            if(!row.enabled || row.deliveryToken!=token || delivery.action!=null || delivery.result!="POSTED") return@withTransaction false
            val d=AlertCodec.decode(row.definition)
            if(action=="COMPLETE") {
                val source=requireNotNull(row.sourceId) { "Este aviso no pertenece a una tarea." }
                val task=academic.snapshot(DatasetScope.valueOf(scope)).activities.firstOrNull { it.id==source } ?: return@withTransaction false
                require(task.kind==ActivityKind.TASK) { "La acción Completar solo modifica tareas." }
                academic.saveActivity(DatasetScope.valueOf(scope),task.copy(status=ActivityStatus.COMPLETED))
            } else {
                val delta=d.snoozeMinutes*60_000L
                dao.update(row.copy(generation=row.generation+1,state="PENDING",snoozeAt=clock.millis()+delta,
                    elapsedAt=platform.elapsed()+delta,boot=platform.boot(),nextAt=null,deliveryToken=null,updatedAt=clock.millis()))
            }
            dao.updateDelivery(delivery.copy(action=action))
            true
        }
        if(applied) { platform.cancelNotification(scope,id);reconcileInside(false) }
        applied
    }

    companion object {
        fun zone(d:AlertDefinition):String = when(val s=d.schedule) { is AlertSchedule.Local -> s.zoneId; is AlertSchedule.Linked -> s.zoneId; is AlertSchedule.Relative -> s.zoneId }
    }
}
