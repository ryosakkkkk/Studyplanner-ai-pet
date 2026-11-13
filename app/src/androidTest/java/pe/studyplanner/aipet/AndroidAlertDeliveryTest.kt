package pe.studyplanner.aipet

import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
import pe.studyplanner.aipet.data.academic.*
import pe.studyplanner.aipet.domain.academic.DatasetScope
import pe.studyplanner.aipet.domain.alerts.*

/** Uses AlarmManager, the production receiver and NotificationManager, no fake adapter. */
class AndroidAlertDeliveryTest {
    @Test fun realOsAlarmPublishesPrivateDemoNotificationAndSnoozeIsIdempotent()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<StudyPlannerApplication>()
        val service=app.alertService;val platform=app.alertPlatform
        val notifications=app.getSystemService(NotificationManager::class.java)
        val d=AlertDefinition(kind=AlertKind.ALARM,title="Ensayo automático F4 · ficticio",
            schedule=AlertSchedule.Relative(Instant.now().plusSeconds(6),platform.elapsed()+6000,platform.boot(),"America/Lima"),precision=AlertPrecision.EXACT,sound=AlertSound.SILENT,snoozeMinutes=1)
        var saved:AlertEntity?=null
        try {
            saved=service.save(DatasetScope.DEMO,d);assertEquals("EXACT",saved.state)
            withTimeout(45_000){while(notifications.activeNotifications.none{it.tag=="DEMO/${d.id}"})delay(200)}
            val card=notifications.activeNotifications.single{it.tag=="DEMO/${d.id}"}.notification
            assertEquals(Notification.VISIBILITY_PRIVATE,card.visibility)
            assertEquals("StudyPlanner",card.publicVersion.extras.getString(Notification.EXTRA_TITLE))
            assertTrue(card.extras.getString(Notification.EXTRA_TITLE)!!.startsWith("DEMO ·"))
            val action=card.actions.single{it.title.toString().startsWith("Posponer")}.actionIntent
            action.send()
            withTimeout(10_000){while(service.database.alertDao().get("DEMO",d.id)?.snoozeAt==null)delay(100)}
            val once=service.database.alertDao().get("DEMO",d.id)!!
            action.send();delay(300)
            assertEquals(once.generation,service.database.alertDao().get("DEMO",d.id)!!.generation)
            assertEquals(1,service.database.alertDao().deliveries("DEMO").count{it.id==d.id})
            assertTrue(notifications.activeNotifications.none{it.tag=="DEMO/${d.id}"})
        } finally {
            if(saved!=null) service.database.alertDao().get("DEMO",d.id)?.let{if(it.enabled) service.setEnabled(DatasetScope.DEMO,it.id,it.generation,false)}
        }
    }
}
