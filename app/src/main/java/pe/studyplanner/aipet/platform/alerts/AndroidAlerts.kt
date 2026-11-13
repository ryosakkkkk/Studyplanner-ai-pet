package pe.studyplanner.aipet.platform.alerts

import android.app.*
import android.app.job.*
import android.content.*
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.*
import pe.studyplanner.aipet.MainActivity
import pe.studyplanner.aipet.StudyPlannerApplication
import pe.studyplanner.aipet.R
import pe.studyplanner.aipet.data.academic.*
import pe.studyplanner.aipet.domain.alerts.*

class AndroidAlertPlatform(private val context: Context) : AlertPlatform {
    private val alarms=context.getSystemService(AlarmManager::class.java)
    private val notifications=context.getSystemService(NotificationManager::class.java)
    override fun boot()=Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,0)
    override fun elapsed()=SystemClock.elapsedRealtime()

    fun ensureChannels() {
        for(kind in AlertKind.entries) for(sound in AlertSound.entries) {
            val name=(if(kind==AlertKind.ALARM) "Alarmas" else "Recordatorios")+" · "+when(sound) { AlertSound.SOUND -> "sonido"; AlertSound.VIBRATE -> "vibración"; AlertSound.SILENT -> "silencio" }
            val channel=NotificationChannel(channelId(kind,sound),name,if(sound==AlertSound.SILENT) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH)
            channel.description="Avisos locales solicitados por el estudiante. El sistema controla sonido, vibración y No molestar."
            channel.lockscreenVisibility=Notification.VISIBILITY_PRIVATE
            val uri=if(sound!=AlertSound.SOUND) null else RingtoneManager.getDefaultUri(if(kind==AlertKind.ALARM) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
            channel.setSound(uri,AudioAttributes.Builder().setUsage(if(kind==AlertKind.ALARM) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION).build())
            channel.enableVibration(sound!=AlertSound.SILENT)
            notifications.createNotificationChannel(channel) // existing user settings are never overwritten
        }
    }
    fun notificationsEnabled()=notifications.areNotificationsEnabled()
    fun exactAllowed()=Build.VERSION.SDK_INT<31 || alarms.canScheduleExactAlarms()
    override fun capability(d:AlertDefinition,fire:AlertFire):AlertCapability {
        ensureChannels()
        val granted=Build.VERSION.SDK_INT<33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED
        val channel=notifications.getNotificationChannel(channelId(d.kind,if(fire.silentNight) AlertSound.SILENT else d.sound))
        val canPost=granted && notifications.areNotificationsEnabled() && channel?.importance!=NotificationManager.IMPORTANCE_NONE
        return AlertCapability(canPost,exactAllowed(),if(canPost) "" else "Notificaciones o canal desactivados. Revisa los ajustes del sistema.")
    }
    private fun alarmIntent(scope:String,id:String)=Intent(context,AlertReceiver::class.java)
        .setAction("pe.studyplanner.aipet.FIRE").setData(Uri.parse("studyplanner://alarm/$scope/$id"))
    private fun alarmPending(scope:String,id:String,generation:Long=0)=PendingIntent.getBroadcast(context,0,
        alarmIntent(scope,id).putExtra("scope",scope).putExtra("id",id).putExtra("generation",generation),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    override fun cancel(scope:String,id:String) { alarms.cancel(alarmPending(scope,id)) }
    override fun cancelNotification(scope:String,id:String) { notifications.cancel("$scope/$id",1) }
    override fun schedule(row:AlertEntity,d:AlertDefinition,fire:AlertFire) {
        val pending=alarmPending(row.scope,row.id,row.generation)
        val type=if(fire.elapsedMillis!=null) AlarmManager.ELAPSED_REALTIME_WAKEUP else AlarmManager.RTC_WAKEUP
        val at=fire.elapsedMillis ?: fire.at.toEpochMilli()
        if(d.precision==AlertPrecision.EXACT) {
            check(exactAllowed()) { "Se revocó el acceso exacto antes de programar." }
            alarms.setExactAndAllowWhileIdle(type,at,pending)
        } else alarms.setAndAllowWhileIdle(type,at,pending)
        Log.i("StudyPlannerAlerts","scheduled scope=${row.scope} id=${row.id} generation=${row.generation} at=${fire.at} precision=${d.precision}")
    }
    override fun post(row:AlertEntity,d:AlertDefinition,fire:AlertFire,token:String,canComplete:Boolean) {
        check(capability(d,fire).notifications) { "Notificaciones bloqueadas." }
        val openIntent=Intent(context,MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("studyplanner://alert/${row.scope}/${row.id}/$token"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open=PendingIntent.getActivity(context,0,openIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(name:String)=PendingIntent.getBroadcast(context,0,Intent(context,AlertActionReceiver::class.java)
            .setAction("pe.studyplanner.aipet.$name").setData(Uri.parse("studyplanner://action/${row.scope}/${row.id}/$token/$name"))
            .putExtra("scope",row.scope).putExtra("id",row.id).putExtra("token",token).putExtra("operation",name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val channel=channelId(d.kind,if(fire.silentNight) AlertSound.SILENT else d.sound)
        val generic=Notification.Builder(context,channel).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("StudyPlanner").setContentText("Tienes un aviso. Abre la aplicación para verlo.").build()
        val builder=Notification.Builder(context,channel).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle((if(row.scope=="DEMO") "DEMO · " else "")+d.title)
            .setContentText("Previsto: "+fire.at.atZone(java.time.ZoneId.of(AlertService.zone(d))).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"))+" · toca para revisar")
            .setCategory(if(d.kind==AlertKind.ALARM) Notification.CATEGORY_ALARM else Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(generic).setContentIntent(open)
            .setAutoCancel(true).setOnlyAlertOnce(true)
            .apply {
                // La mascota elegida con la pose de aviso de sus láminas (campana o alarma).
                val pet=pe.studyplanner.aipet.platform.pet3d.PetStyleStore.currentPet(context)
                pe.studyplanner.aipet.platform.pet3d.PetStickerImages.load(context,pet,if(d.kind==AlertKind.ALARM)"alarma" else "campana")?.let { setLargeIcon(it) }
            }
            .addAction(Notification.Action.Builder(null,"Posponer ${d.snoozeMinutes} min",action("SNOOZE")).build())
            .addAction(Notification.Action.Builder(null,"Ver",open).build())
        if(canComplete) builder.addAction(Notification.Action.Builder(null,"Completar",action("COMPLETE")).build())
        // A later occurrence must alert again even if the preceding card was never dismissed.
        notifications.cancel("${row.scope}/${row.id}",1)
        notifications.notify("${row.scope}/${row.id}",1,builder.build())
        // La mascota (también la flotante, mismo proceso) muestra el aviso durante unos segundos.
        pe.studyplanner.aipet.domain.pet.PetEmotionBus.emit(if(d.kind==AlertKind.ALARM) "notify" else "reminder",8000,SystemClock.uptimeMillis())
        Log.i("StudyPlannerAlerts","posted scope=${row.scope} id=${row.id} token=$token planned=${fire.at} elapsed=${elapsed()}")
    }
    companion object { fun channelId(kind:AlertKind,sound:AlertSound)="studyplanner_${kind.name.lowercase()}_${sound.name.lowercase()}_v1" }
}

private fun BroadcastReceiver.launchWork(context:Context,work:suspend (AlertService)->Unit) {
    val pending=goAsync()
    CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
        try { withTimeout(8_000) { work((context.applicationContext as StudyPlannerApplication).alertService) } }
        catch(error:Exception) { Log.e("StudyPlannerAlerts","Receiver pendiente o fallido",error);AlertJobs.request(context) }
        finally { pending.finish() }
    }
}

class AlertReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!="pe.studyplanner.aipet.FIRE") return
        val scope=intent.getStringExtra("scope") ?: return;val id=intent.getStringExtra("id") ?: return
        val generation=intent.getLongExtra("generation",-1)
        launchWork(context) { it.fire(scope,id,generation) }
    }
}
class AlertActionReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val scope=intent.getStringExtra("scope") ?: return;val id=intent.getStringExtra("id") ?: return
        val token=intent.getStringExtra("token") ?: return;val action=intent.getStringExtra("operation") ?: return
        if(action !in setOf("SNOOZE","COMPLETE")) return
        launchWork(context) { it.action(scope,id,token,action) }
    }
}
class AlertRecoveryReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        Log.i("StudyPlannerAlerts","recovery ${intent.action}")
        AlertJobs.request(context)
        launchWork(context) { it.reconcile(true) }
    }
}

/** Native persistent JobScheduler only reconciles outbox/state. It is never used as an alarm clock. */
object AlertJobs {
    private const val PERIODIC=4101
    private const val OUTBOX=4102
    fun install(context:Context) {
        val jobs=context.getSystemService(JobScheduler::class.java)
        if(jobs.getPendingJob(PERIODIC)==null) jobs.schedule(JobInfo.Builder(PERIODIC,ComponentName(context,AlertReconcileJob::class.java))
            .setPersisted(true).setPeriodic(15*60_000L).build())
    }
    fun request(context:Context) {
        context.getSystemService(JobScheduler::class.java).schedule(JobInfo.Builder(OUTBOX,ComponentName(context,AlertReconcileJob::class.java))
            .setPersisted(true).setMinimumLatency(1000).setOverrideDeadline(10_000).build())
    }
}
class AlertReconcileJob:JobService() {
    private val work=mutableMapOf<Int,Job>()
    override fun onStartJob(params:JobParameters):Boolean {
        val job=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate).launch(start=CoroutineStart.LAZY) {
            var retry=false
            try { withContext(Dispatchers.IO) {
                val service=(application as StudyPlannerApplication).alertService
                service.reconcile(true)
                retry=service.database.alertDao().outbox().isNotEmpty()
            } }
            catch(error:CancellationException) { throw error }
            catch(error:Exception) { retry=true;Log.e("StudyPlannerAlerts","Reconciliación diferida fallida",error) }
            finally { if(work.remove(params.jobId)!=null) jobFinished(params,retry) }
        }
        work[params.jobId]=job
        job.start()
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean { work.remove(params.jobId)?.cancel();return true }
    override fun onDestroy() { val jobs=work.values.toList();work.clear();jobs.forEach { it.cancel() };super.onDestroy() }
}
