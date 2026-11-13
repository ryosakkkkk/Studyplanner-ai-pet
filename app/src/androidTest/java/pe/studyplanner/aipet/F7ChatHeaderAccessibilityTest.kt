package pe.studyplanner.aipet

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import pe.studyplanner.aipet.domain.profile.ThemeMode
import pe.studyplanner.aipet.ui.agent.AdaptiveAgentTopBar
import pe.studyplanner.aipet.ui.theme.StudyPlannerTheme

/**
 * Production chat header in a real Activity/Scaffold at Android font_scale=2.
 * The body is a scroll fixture; this does not exercise the agent or TalkBack.
 * No LocalDensity or LocalConfiguration override substitutes for device settings.
 */
class F7ChatHeaderAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var captures: File
    private var captureIndex = 0

    @Test fun lightPortrait200() = matrixCell(ThemeMode.LIGHT, Configuration.ORIENTATION_PORTRAIT)
    @Test fun lightLandscape200() = matrixCell(ThemeMode.LIGHT, Configuration.ORIENTATION_LANDSCAPE)
    @Test fun darkPortrait200() = matrixCell(ThemeMode.DARK, Configuration.ORIENTATION_PORTRAIT)
    @Test fun darkLandscape200() = matrixCell(ThemeMode.DARK, Configuration.ORIENTATION_LANDSCAPE)

    private fun matrixCell(theme: ThemeMode, orientation: Int) {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = if (orientation == Configuration.ORIENTATION_PORTRAIT)
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitUntil(15_000) { compose.activity.resources.configuration.orientation == orientation }
        val configuration = compose.activity.resources.configuration
        assertEquals("Configura font_scale=2 real antes de ejecutar esta matriz", 2f, configuration.fontScale, 0.01f)
        val cell = "${theme.name.lowercase()}-${if (orientation == Configuration.ORIENTATION_PORTRAIT) "portrait" else "landscape"}"
        captures = File(requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)),
            "qa-f7-chat-header-200/$cell-${System.currentTimeMillis()}").apply { check(mkdirs() || isDirectory) }
        File(captures, "fixture.txt").writeText(
            "cell=$cell\nfontScale=${configuration.fontScale}\norientation=${configuration.orientation}\n" +
                "sdk=${android.os.Build.VERSION.SDK_INT}\ndensity=${compose.activity.resources.displayMetrics.density}\n" +
                "screen=production AdaptiveAgentTopBar in real ComponentActivity/Scaffold\n" +
                "body=scroll fixture; no agent, voice or storage\nTalkBack=not executed by this test\n")
        val busy = mutableStateOf(false)
        val backs = AtomicInteger()
        compose.setContent {
            StudyPlannerTheme(theme) {
                Scaffold(
                    modifier=Modifier.fillMaxSize(),
                    topBar={AdaptiveAgentTopBar(busy=busy.value,onBack={backs.incrementAndGet()})},
                ) { padding ->
                    LazyColumn(
                        modifier=Modifier.fillMaxSize().padding(padding).testTag(BODY),
                        contentPadding=PaddingValues(20.dp),
                        verticalArrangement=Arrangement.spacedBy(12.dp),
                    ) {
                        items((0 until ROWS).toList()) { index ->
                            Text("Fila de prueba $index", Modifier.fillMaxWidth().testTag("header_body_$index"))
                        }
                    }
                }
            }
        }
        try {
            assertHeader()
            assertBodyBelowHeader()
            val back = assertBackTarget()
            back.assertIsEnabled().performTouchInput { click() }
            compose.waitForIdle()
            assertEquals("Volver habilitado debe invocar una vez su callback", 1, backs.get())
            capture("encabezado-completo")

            compose.runOnIdle { busy.value=true }
            assertHeader()
            assertBackTarget().assertIsNotEnabled().performTouchInput { click() }
            compose.waitForIdle()
            assertEquals("Volver bloqueado no debe invocar su callback", 1, backs.get())
            capture("ocupado-volver-bloqueado")

            compose.runOnIdle { busy.value=false }
            assertBackTarget().assertIsEnabled().performTouchInput { click() }
            compose.waitForIdle()
            assertEquals("Volver se debe recuperar al terminar la operación", 2, backs.get())

            compose.onNodeWithTag(BODY).performScrollToNode(hasTestTag("header_body_${ROWS-1}"))
            val last = compose.onNodeWithTag("header_body_${ROWS-1}").performScrollTo().assertIsDisplayed()
            assertFullyInside(last, compose.onNodeWithTag(BODY).fetchSemanticsNode().boundsInRoot, "última fila")
            assertBodyBelowHeader()
            assertHeader()
            capture("final-del-cuerpo-alcanzable")

            compose.onNodeWithTag(BODY).performScrollToNode(hasTestTag("header_body_0"))
            val first = compose.onNodeWithTag("header_body_0").performScrollTo().assertIsDisplayed()
            assertFullyInside(first, compose.onNodeWithTag(BODY).fetchSemanticsNode().boundsInRoot, "primera fila")
            assertBodyBelowHeader()
            File(captures, "resultado.txt").writeText(
                "PASA título íntegro/heading, viewport, objetivo táctil, busy/callback y scroll en esta celda. " +
                    "No acredita TalkBack ni el flujo del agente. PNG pendientes de revisión visual externa.")
        } catch (failure: Throwable) {
            runCatching { capture("fallo") }
            File(captures, "resultado.txt").writeText("FALLO: ${failure.javaClass.name}: ${failure.message}")
            throw failure
        }
    }

    private fun assertHeader() {
        val title = compose.onNodeWithTag("agent_title").assertIsDisplayed().assertTextEquals(TITLE)
        assertNotNull("El título debe conservar semántica de encabezado",
            title.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Heading))
        val layouts = mutableListOf<TextLayoutResult>()
        title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            assertTrue("No se obtuvo el layout del título", action(layouts))
        }
        assertTrue("El título debe tener layout", layouts.isNotEmpty())
        layouts.forEach { layout ->
            val metrics = "title='$TITLE'; size=${layout.size}; lines=${layout.lineCount}; " +
                "overflowWidth=${layout.didOverflowWidth}; overflowHeight=${layout.didOverflowHeight}; " +
                "exceedMaxLines=${layout.multiParagraph.didExceedMaxLines}; bounds=${title.fetchSemanticsNode().boundsInRoot}; " +
                "unclipped=${title.getUnclippedBoundsInRoot()}"
            File(captures, "metricas.txt").appendText("$metrics\n")
            assertEquals("Debe maquetarse el título completo", TITLE, layout.layoutInput.text.text)
            assertFalse("Título desbordado horizontalmente: $metrics", layout.didOverflowWidth)
            assertFalse("Título desbordado verticalmente: $metrics", layout.didOverflowHeight)
            assertFalse("Título limitado por maxLines: $metrics", layout.multiParagraph.didExceedMaxLines)
            assertTrue("Título sin líneas: $metrics", layout.lineCount > 0)
            for (line in 0 until layout.lineCount) {
                assertFalse("Título con elipsis: $metrics; line=$line", layout.isLineEllipsized(line))
                assertTrue("Glifos recortados horizontalmente: $metrics; line=$line",
                    layout.getLineLeft(line) >= -2f && layout.getLineRight(line) <= layout.size.width+2f)
                assertTrue("Glifos recortados verticalmente: $metrics; line=$line",
                    layout.getLineTop(line) >= -2f && layout.getLineBottom(line) <= layout.size.height+2f)
            }
            assertTrue("El final del título no está maquetado: $metrics",
                layout.getLineEnd(layout.lineCount-1,visibleEnd=false) >= TITLE.length)
        }
        assertFullyInside(title, compose.onRoot().fetchSemanticsNode().boundsInRoot, "título en pantalla")
        assertFullyInside(title, compose.onNodeWithTag("agent_top_bar").fetchSemanticsNode().boundsInRoot, "título en barra")
    }

    private fun assertBackTarget(): SemanticsNodeInteraction {
        val back = compose.onNodeWithTag("agent_back").assertIsDisplayed().assertTextEquals("Volver")
        val node = back.fetchSemanticsNode()
        assertEquals("Rol de Volver", Role.Button, node.config.getOrNull(SemanticsProperties.Role))
        assertNotNull("Acción accesible de Volver", node.config.getOrNull(SemanticsActions.OnClick))
        val density = compose.activity.resources.displayMetrics.density
        val touch = node.touchBoundsInRoot
        File(captures, "metricas.txt").appendText("back touchPx=$touch; touchDp=${touch.width/density}x${touch.height/density}\n")
        assertTrue("Volver debe tener objetivo táctil mínimo de 48 dp: $touch; density=$density",
            touch.width/density >= 47.9f && touch.height/density >= 47.9f)
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Objetivo táctil de Volver recortado por la pantalla: $touch; viewport=$root",
            touch.left >= root.left-2f && touch.right <= root.right+2f &&
                touch.top >= root.top-2f && touch.bottom <= root.bottom+2f)
        assertFullyInside(back, compose.onNodeWithTag("agent_top_bar").fetchSemanticsNode().boundsInRoot, "Volver")
        return back
    }

    private fun assertBodyBelowHeader() {
        val header = compose.onNodeWithTag("agent_top_bar").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag(BODY).fetchSemanticsNode().boundsInRoot
        assertTrue("El cuerpo necesita un viewport visible", viewport.width > 0f && viewport.height > 0f)
        assertTrue("El cuerpo no debe quedar debajo de la barra: header=$header; body=$viewport",
            viewport.top >= header.bottom-2f)
        val range = compose.onNodeWithTag(BODY).fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        assertNotNull("El cuerpo debe conservar desplazamiento vertical", range)
        assertTrue("El cuerpo de prueba debe poder desplazarse", requireNotNull(range).maxValue() > 0f)
    }

    private fun assertFullyInside(interaction: SemanticsNodeInteraction, viewport: Rect, label: String) {
        val density = compose.activity.resources.displayMetrics.density
        val visible = interaction.fetchSemanticsNode().boundsInRoot
        val full = interaction.getUnclippedBoundsInRoot()
        val left=full.left.value*density
        val right=full.right.value*density
        val top=full.top.value*density
        val bottom=full.bottom.value*density
        assertTrue("Sin área visible para $label", visible.width > 0f && visible.height > 0f)
        assertTrue("$label recortado por un ancestro: full=$full; visible=$visible",
            left >= visible.left-2f && right <= visible.right+2f && top >= visible.top-2f && bottom <= visible.bottom+2f)
        assertTrue("$label fuera del viewport: full=$full; viewport=$viewport",
            left >= viewport.left-2f && right <= viewport.right+2f && top >= viewport.top-2f && bottom <= viewport.bottom+2f)
    }

    private fun capture(label: String) {
        compose.waitForIdle()
        val name = "${(++captureIndex).toString().padStart(2,'0')}-$label"
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(captures, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        bitmap.recycle()
        File(captures, "$name-semantica.txt").writeText(compose.onRoot(useUnmergedTree=true).printToString())
    }

    private companion object {
        const val TITLE = "Chat de planificación"
        const val BODY = "f7_chat_header_body"
        const val ROWS = 40
    }
}
