package pe.studyplanner.aipet.data.academic

import androidx.room.withTransaction
import java.time.*
import java.security.MessageDigest
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.planner.*
import org.json.JSONArray
import org.json.JSONObject

class RoomPlannerRepository(private val database: AcademicDatabase, private val clock: Clock = Clock.systemUTC(),
    private val beforeReceipt: suspend () -> Unit = {}) : PlannerRepository {
    private val dao = database.plannerDao()
    private val academic = RoomAcademicRepository(database, clock)
    override suspend fun load(scope: DatasetScope): PlanContext = database.withTransaction {
        val profile = requireNotNull(database.academicDao().profile()) { "No se pudo leer el perfil. Vuelve a Inicio y reintenta." }
        val snapshot = academic.snapshot(scope)
        val defaults = linkedMapOf<String, PlanGoal>()
        for (receipt in dao.receipts(scope.name)) {
            val goals = JSONArray(receipt.goalRequests)
            for (index in 0 until goals.length()) {
                val goal = goals.getJSONObject(index)
                val id = goal.getString("id")
                val source = snapshot.activities.firstOrNull { it.id == id } ?: continue
                val originalEstimate = if (goal.isNull("estimate")) null else goal.getInt("estimate")
                if (id !in defaults && source.version <= receipt.appliedRevision && source.createdAt.toEpochMilli() == goal.getLong("createdAt") && source.estimateMinutes == originalEstimate) {
                    defaults[id] = PlanGoal(id, if (goal.isNull("minutes")) null else goal.getInt("minutes"), goal.getBoolean("splittable"))
                }
            }
        }
        PlanContext(snapshot, settings(scope), dao.links(scope.name).map { StudyLink(it.sessionId, it.goalId, it.planId) }, profile.zoneId, profile.preferencesRevision, defaults)
    }
    private suspend fun settings(scope: DatasetScope): PlannerSettings {
        val p = dao.preferences(scope.name) ?: return PlannerSettings()
        return PlannerSettings(p.version, dao.windows(scope.name).map { Availability(DayOfWeek.of(it.weekday), LocalTime.ofSecondOfDay(it.startSecond.toLong()), LocalTime.ofSecondOfDay(it.endSecond.toLong())) },
            p.dailyMinutes, p.minBlock, p.maxBlock, p.breakMinutes, LocalTime.ofSecondOfDay(p.sleepStart.toLong()), LocalTime.ofSecondOfDay(p.sleepEnd.toLong()),
            LocalTime.ofSecondOfDay(p.preferredStart.toLong()), LocalTime.ofSecondOfDay(p.preferredEnd.toLong())).also { it.validate() }
    }
    override suspend fun saveSettings(scope: DatasetScope, settings: PlannerSettings): PlannerSettings = database.withTransaction {
        settings.validate()
        val current = dao.preferences(scope.name)
        if ((current?.version ?: 0) != settings.version) throw StaleAcademicWriteException()
        val saved = settings.copy(version = settings.version + 1)
        dao.putPreferences(PlannerPreferencesEntity(scope.name, saved.version, saved.dailyMinutes, saved.minBlock, saved.maxBlock, saved.breakMinutes,
            saved.sleepStart.toSecondOfDay(), saved.sleepEnd.toSecondOfDay(), saved.preferredStart.toSecondOfDay(), saved.preferredEnd.toSecondOfDay()))
        dao.clearWindows(scope.name)
        dao.insertWindows(saved.windows.map { PlannerAvailabilityEntity(scope.name, it.day.value, it.start.toSecondOfDay(), it.end.toSecondOfDay()) })
        saved
    }
    override suspend fun propose(scope: DatasetScope, request: PlanRequest): PlanDraft = database.withTransaction {
        PlannerEngine.generate(load(scope), request, clock.instant())
    }
    override suspend fun apply(draft: PlanDraft): PlanReceipt = database.withTransaction {
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(draft.toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        dao.receipt(draft.scope.name, draft.id)?.let {
            require(it.fingerprint == fingerprint) { "El comprobante no corresponde a esta propuesta." }
            return@withTransaction PlanReceipt(it.planId, it.sessionIds.split(',').filter(String::isNotEmpty), Instant.ofEpochMilli(it.appliedAt), true)
        }
        val current = load(draft.scope)
        if (current.snapshot.revision != draft.revision || current.settings.version != draft.settingsVersion || current.profileRevision != draft.profileRevision || current.zoneId != draft.zoneId)
            throw StaleAcademicWriteException()
        val now = clock.instant()
        require(!draft.generatedAt.isAfter(now) && Duration.between(draft.generatedAt, now) <= Duration.ofMinutes(30)) { "La propuesta caducó. Genérala otra vez." }
        require(draft.changes.isNotEmpty()) { "Esta propuesta no tiene cambios para guardar." }
        require(PlannerEngine.generate(current, draft.request, draft.generatedAt) == draft) { "La propuesta no coincide con los datos y reglas verificados." }
        require(draft.changes.mapNotNull { (it.after?.time as? ActivityTime.Timed)?.start }.all { it >= now }) { "Una sesión ya comenzaría en el pasado. Genera otra propuesta." }
        val ids = mutableListOf<String>()
        for (change in draft.changes) {
            if (change.after == null) {
                val before = requireNotNull(change.before)
                academic.deleteActivity(draft.scope, before.id, before.version)
            } else {
                val saved = academic.saveActivity(draft.scope, change.after)
                dao.putLink(StudyPlanLinkEntity(draft.scope.name, saved.id, change.goalId, draft.id)); ids += saved.id
            }
        }
        beforeReceipt() // Fault-injection seam proves rollback of sessions, links, revisions and receipt together.
        val goals = JSONArray()
        for (goal in draft.request.goals) {
            val source = current.snapshot.activities.first { it.id == goal.activityId }
            goals.put(JSONObject().put("id", goal.activityId).put("minutes", goal.totalMinutes ?: JSONObject.NULL)
                .put("splittable", goal.splittable).put("estimate", source.estimateMinutes ?: JSONObject.NULL).put("createdAt", source.createdAt.toEpochMilli()))
        }
        dao.insertReceipt(PlanReceiptEntity(draft.scope.name, draft.id, fingerprint, ids.joinToString(","), now.toEpochMilli(), goals.toString(),
            requireNotNull(database.academicDao().revision(draft.scope.name)).revision))
        PlanReceipt(draft.id, ids, now)
    }
}
