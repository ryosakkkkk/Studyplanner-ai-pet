package pe.studyplanner.aipet

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import pe.studyplanner.aipet.data.academic.*
import pe.studyplanner.aipet.domain.academic.DatasetScope
import pe.studyplanner.aipet.domain.profile.ThemeMode
import pe.studyplanner.aipet.ui.alerts.AlertScreen
import pe.studyplanner.aipet.ui.theme.StudyPlannerTheme

class AlertFlowTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var context:Context;private lateinit var db:AcademicDatabase;private lateinit var name:String
    private lateinit var service:AlertService
    private val platform=AlertTestPlatform()
    @Before fun prepare(){context=ApplicationProvider.getApplicationContext();name="alert-flow-${UUID.randomUUID()}.db";db=AcademicDatabase.open(context,name);service=AlertService(db,platform)}
    @After fun clean(){compose.runOnIdle{compose.activity.viewModelStore.clear()};db.close();context.deleteDatabase(name)}
    private fun rows():List<AlertEntity>{val other=AcademicDatabase.open(context,name);return try{runBlocking{other.alertDao().all()}}finally{other.close()}}
    private fun show(){compose.setContent{StudyPlannerTheme(ThemeMode.LIGHT){AlertScreen(service,DatasetScope.DEMO,"America/Lima",onBack={})}};compose.waitUntil(15000){compose.onAllNodesWithTag("alerts_content").fetchSemanticsNodes().isNotEmpty()}}
    private fun text(value:String,editor:Boolean=false):SemanticsNodeInteraction {
        val tag=if(editor) "alert_editor" else "alerts_content"
        compose.waitUntil(15000){runCatching{compose.onNodeWithTag(tag).performScrollToNode(hasText(value));compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
        return compose.onNodeWithText(value).performScrollTo().assertIsDisplayed()
    }
    private fun field(label:String,value:String){text(label,true).performTextReplacement(value);Espresso.closeSoftKeyboard()}
    private fun openRelative(){
        show();val create=text("Crear alarma")
        compose.waitUntil(15000){runCatching{create.assertIsEnabled()}.isSuccess}
        create.performClick();field("Título del aviso","Prueba UI real");text("Fecha y hora",true).performClick();compose.onNodeWithText("En unos minutos (prueba real)").performClick()
    }
    @Test fun explicitReviewAndConfirmationPersistOnlyInDemoWithRealRoom(){
        openRelative();text("Revisar aviso",true).performClick();compose.onNodeWithText("Confirmar aviso real").assertIsDisplayed();assertTrue(rows().isEmpty());assertTrue(platform.scheduled.isEmpty())
        compose.onNodeWithText("Seguir editando").performClick();assertTrue(rows().isEmpty())
        text("Revisar aviso",true).performClick();compose.onNodeWithText("Guardar y programar").performClick()
        // A second connection can see the durable PENDING row before the OS stage finishes.
        compose.waitUntil(15000){rows().singleOrNull()?.state=="EXACT"};val saved=rows().single();assertEquals("DEMO",saved.scope);assertEquals("EXACT",saved.state);assertEquals(1,platform.scheduled.size)
    }
    @Test fun invalidRelativeTimeDoesNotPersistOrSchedule(){
        openRelative();field("Avisar en minutos (1–10080)","0");text("Revisar aviso",true).performClick();text("Indica de 1 a 10080 minutos.",true).assertIsDisplayed()
        assertTrue(rows().isEmpty());assertTrue(platform.scheduled.isEmpty())
    }
}
