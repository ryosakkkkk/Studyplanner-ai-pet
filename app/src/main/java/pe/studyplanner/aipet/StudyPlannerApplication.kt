package pe.studyplanner.aipet

import android.app.Application
import androidx.datastore.preferences.preferencesDataStore
import pe.studyplanner.aipet.data.profile.DataStoreProfileRepository
import pe.studyplanner.aipet.data.profile.RoomProfileRepository
import pe.studyplanner.aipet.data.profile.LegacyProfileBackup
import pe.studyplanner.aipet.data.academic.AcademicDatabase
import pe.studyplanner.aipet.data.academic.RoomAcademicRepository
import pe.studyplanner.aipet.domain.academic.AcademicRepository
import pe.studyplanner.aipet.domain.profile.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import pe.studyplanner.aipet.data.academic.AlertService
import pe.studyplanner.aipet.platform.alerts.*

private val Application.profileDataStore by preferencesDataStore(name = "profile_v1")

class StudyPlannerApplication : Application() {
    val academicDatabase: AcademicDatabase by lazy { AcademicDatabase.open(this) }
    val academicRepository: AcademicRepository by lazy { RoomAcademicRepository(academicDatabase) }
    val plannerRepository by lazy { pe.studyplanner.aipet.data.academic.RoomPlannerRepository(academicDatabase) }
    val alertPlatform by lazy { AndroidAlertPlatform(this) }
    val alertService by lazy { AlertService(academicDatabase,alertPlatform) }
    val aiConnection by lazy { pe.studyplanner.aipet.data.ai.AiConnectionStore(this) }
    /**
     * Lo que la mascota sabe sin Internet («Hablar»): `assets/conocimiento/base.json` (glosario propio, Camino y bancos)
     * más los temas de la app que no están ahí. Se arma una vez, en segundo plano al abrir la app.
     */
    val studyKnowledge: pe.studyplanner.aipet.domain.knowledge.StudyKnowledge by lazy {
        val (entries,courses)=runCatching {
            pe.studyplanner.aipet.domain.knowledge.StudyKnowledge.parse(assets.open("conocimiento/base.json").bufferedReader(Charsets.UTF_8).use{it.readText()})
        }.getOrElse { android.util.Log.e("StudyPlannerSaber","No se pudo leer la base de conocimiento",it); emptyList<pe.studyplanner.aipet.domain.knowledge.KnowledgeEntry>() to emptyMap() }
        val known=entries.mapTo(HashSet()){it.topicId}
        pe.studyplanner.aipet.domain.knowledge.StudyKnowledge(entries+pe.studyplanner.aipet.domain.knowledge.StudyKnowledge.fromTopics(pe.studyplanner.aipet.domain.tutor.TutorContent.catalog.topics,known),courses)
    }
    val agentService by lazy { pe.studyplanner.aipet.data.academic.AgentService(academicDatabase,alertService,pe.studyplanner.aipet.data.ai.GeminiProxyProvider(aiConnection,knowledge={studyKnowledge})) }
    private val applicationScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    /** Historial de práctica de matemática: `filesDir/tutor/intentos.csv`, sin datos personales. */
    val tutorHistory by lazy { pe.studyplanner.aipet.data.tutor.TutorHistory(pe.studyplanner.aipet.data.tutor.TutorAttemptStore(java.io.File(filesDir,"tutor/intentos.csv")),applicationScope) }
    /** Enlace opcional con la mascota física StudyPet (Bluetooth LE). Sin Bluetooth, la app funciona igual. */
    val petLink: pe.studyplanner.aipet.domain.companion.PetLink by lazy { pe.studyplanner.aipet.platform.companion.AndroidPetLink(this) }
    /** Cuenta opcional y copia en la nube (en el mismo servidor de la IA). Sin cuenta, todo queda en el teléfono. */
    val cloudAccount by lazy { pe.studyplanner.aipet.data.account.CloudAccount(this,aiConnection) { academicDatabase } }
    /** Sube la copia si hay cuenta y algo cambió (al salir de la app). */
    fun backupInBackground() { applicationScope.launch { runCatching { cloudAccount.backup() } } }
    override fun onCreate() {
        super.onCreate()
        // Una copia recuperada de la nube se pone en su lugar antes de abrir la base de datos.
        pe.studyplanner.aipet.data.account.CloudAccount.applyPendingRestore(this)
        aiConnection.builtIn(BuildConfig.AI_ENDPOINT,BuildConfig.AI_TOKEN)
        AlertJobs.install(this)
        installTutorBanks()
        tutorHistory.start()
        // Se arma el índice de lo que sabe la mascota antes de la primera pregunta (no bloquea la pantalla).
        applicationScope.launch { runCatching { studyKnowledge.entries.size } }
        applicationScope.launch {
            try {
                alertService.reconcile(true)
                academicDatabase.alertDao().observeOutbox().distinctUntilChanged().collect { rows ->
                    if(rows.isNotEmpty()) { AlertJobs.request(this@StudyPlannerApplication);alertService.reconcile(false) }
                }
            } catch(error:Exception) { android.util.Log.e("StudyPlannerAlerts","Recuperación al iniciar pendiente",error);AlertJobs.request(this@StudyPlannerApplication) }
        }
    }
    /** Temas de práctica de las demás materias: bancos de preguntas verificadas en `assets/tutor/`. Si fallan, queda la matemática. */
    private fun installTutorBanks() {
        val loaded=pe.studyplanner.aipet.data.tutor.TutorBankLoader.load{name->assets.open(name).bufferedReader(Charsets.UTF_8).use{it.readText()}}
        pe.studyplanner.aipet.domain.tutor.TutorContent.install(loaded.catalog)
        loaded.errors.forEach{android.util.Log.e("StudyPlannerTutor","Banco de preguntas omitido: $it")}
    }
    val profileRepository: ProfileRepository by lazy {
        RoomProfileRepository(academicDatabase, DataStoreProfileRepository(profileDataStore),
            preserveLegacyBackup = {
                withContext(Dispatchers.IO) {
                    LegacyProfileBackup.preserve(filesDir)
                }
            })
    }
}
