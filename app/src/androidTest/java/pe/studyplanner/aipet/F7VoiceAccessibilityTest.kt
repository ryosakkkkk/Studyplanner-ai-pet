package pe.studyplanner.aipet

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import pe.studyplanner.aipet.domain.profile.ThemeMode
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.domain.voice.*
import pe.studyplanner.aipet.ui.theme.StudyPlannerTheme
import pe.studyplanner.aipet.ui.voice.VoicePanel
import java.io.File

/**
 * Production VoicePanel in a real Android Activity at system font_scale=2.
 * Orientation and density come from Android, not an overridden LocalDensity.
 * Only recognition/TTS callbacks are controlled. This is not TalkBack, acoustic
 * dictation, native permission-dialog coverage, or an evaluation with students.
 */
class F7VoiceAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var voice: VoiceSession
    private lateinit var scope: CoroutineScope
    private lateinit var captures: File
    private var captureIndex = 0
    private val platform = AccessibilityVoicePlatform()
    private val usedText = mutableListOf<String>()
    private val profile = mutableStateOf(UserProfile(studentName = "Prueba", petName = "KiroQA", petVisible = true))

    @After fun clean() {
        if (::voice.isInitialized) compose.runOnIdle {
            voice.close()
            scope.cancel()
        }
    }

    @Test fun lightPortrait200() = matrixCell(ThemeMode.LIGHT, Configuration.ORIENTATION_PORTRAIT)
    @Test fun lightLandscape200() = matrixCell(ThemeMode.LIGHT, Configuration.ORIENTATION_LANDSCAPE)
    @Test fun darkPortrait200() = matrixCell(ThemeMode.DARK, Configuration.ORIENTATION_PORTRAIT)
    @Test fun darkLandscape200() = matrixCell(ThemeMode.DARK, Configuration.ORIENTATION_LANDSCAPE)

    private fun matrixCell(theme: ThemeMode, orientation: Int) {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = if (orientation == Configuration.ORIENTATION_PORTRAIT)
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitUntil(15000) { compose.activity.resources.configuration.orientation == orientation }
        val configuration = compose.activity.resources.configuration
        assertEquals("The runner must set real Android font_scale=2.", 2f, configuration.fontScale, 0.01f)
        assertEquals("The runner grants microphone permission temporarily for controlled-service UI tests.",
            PackageManager.PERMISSION_GRANTED, compose.activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        val cell = "${theme.name.lowercase()}-${if (orientation == Configuration.ORIENTATION_PORTRAIT) "portrait" else "landscape"}"
        captures = File(requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)),
            "qa-f7-voice-200/$cell-${System.currentTimeMillis()}").apply { check(mkdirs() || isDirectory) }
        File(captures, "fixture.txt").writeText(
            "cell=$cell\nfontScale=${configuration.fontScale}\norientation=$orientation\n" +
                "sdk=${android.os.Build.VERSION.SDK_INT}\ndensity=${compose.activity.resources.displayMetrics.density}\n" +
                "screen=production VoicePanel in real Activity/LazyColumn\nvoice=controlled callbacks; no acoustic capture\n" +
                "storage=none; no agent or Room fixture\nTalkBack=not executed by this test\n" +
                "contrast=visual review pending; no numeric contrast oracle\n")
        compose.runOnIdle {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            voice = VoiceSession(platform, scope)
        }
        compose.setContent {
            StudyPlannerTheme(theme) {
                Surface(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize().testTag(SCROLL), contentPadding = PaddingValues(20.dp)) {
                        item {
                            VoicePanel(voice, profile.value,
                                onTogglePet = { profile.value = profile.value.copy(petVisible = !profile.value.petVisible) },
                                onUseTranscript = { usedText += it }, readText = "Revisa la agenda de prueba.", enabled = true)
                        }
                    }
                }
            }
        }
        try {
            completeText("Voz y mascota")
            completeText("KiroQA")
            completeText("El dictado solo prepara texto. Revisa la transcripción, envía la solicitud y confirma cualquier cambio por separado.")
            compose.onNodeWithContentDescription("Mascota zorro").performScrollTo().assertIsDisplayed()
            stateLabel("En reposo")
            capture("reposo")
            val hide = button("Ocultar mascota")
            hide.performClick()
            button("Mostrar mascota").performClick()
            assertEquals(0, platform.recognitionStarts)
            assertEquals(0, platform.speechStarts)

            button("Dictar solicitud")
            button("Escuchar mi nombre y dictar")
            switch("Usar reconocimiento en el dispositivo").assertIsOff()
            val readAloud = switch("Activar lectura en voz alta", "voice_read_aloud")
            readAloud.assertIsOff()
            button("Leer texto mostrado").assertIsNotEnabled()
            switch("Reducir movimiento").assertIsOn()
            completeText("Estas opciones de voz se conservan solo mientras el chat está abierto. Ocultar la mascota o salir detiene el audio; la agenda y sus avisos se conservan.")
            capture("alternativa-sin-audio")

            readAloud.performScrollTo().performClick()
            button("Leer texto mostrado").performClick()
            assertEquals(1, platform.speechStarts)
            compose.runOnIdle { requireNotNull(platform.speech).onStart() }
            stateLabel("Leyendo en voz alta")
            capture("lectura-controlada")
            button("Detener audio").performClick()
            assertEquals(VoicePhase.IDLE, voice.state.value.phase)
            chip("Lenta · 0,8×").performClick()
            chip("Lenta · 0,8×").assertIsSelected()
            chip("Normal · 1×").performClick()
            chip("Rápida · 1,2×")
            chip("Volumen bajo").performClick()
            chip("Volumen bajo").assertIsSelected()
            chip("Volumen normal").performClick()
            switch("Activar lectura en voz alta", "voice_read_aloud").performClick()
            switch("Reducir movimiento").performClick()
            switch("Reducir movimiento").assertIsOff()
            switch("Reducir movimiento").performClick()
            capture("ajustes-alcanzables")

            button("Dictar solicitud").performClick()
            completeText("Antes de usar el micrófono", scroll = false)
            capture("privacidad-titulo")
            completeText(PRIVACY_TEXT, scroll = false)
            capture("privacidad-texto-final")
            button("Continuar con el dictado", scroll = false)
            button("Ahora no", scroll = false)
            capture("privacidad-acciones")
            assertEquals(0, platform.recognitionStarts)
            compose.onNodeWithText("Continuar con el dictado").performScrollTo().performClick()
            compose.waitUntil(15000) { platform.recognitionStarts == 1 }
            compose.runOnIdle { requireNotNull(platform.recognition).onReady() }
            stateLabel("Escuchando · sesión de hasta 15 segundos")
            button("Cancelar dictado")
            capture("captura-controlada")
            compose.runOnIdle { requireNotNull(platform.recognition).onResult("crea tarea \"F7\" para mañana") }
            compose.waitUntil(15000) { voice.state.value.phase == VoicePhase.REVIEW }
            stateLabel("Transcripción pendiente de tu revisión")
            val transcript = compose.onNodeWithTag("voice_transcript").performScrollTo().assertIsDisplayed()
            transcript.assertTextContains("crea tarea \"F7\" para mañana")
            assertNotNull(transcript.fetchSemanticsNode().config.getOrNull(SemanticsActions.SetText))
            transcript.performTextReplacement("crea tarea \"F7 corregida\" para mañana")
            Espresso.closeSoftKeyboard()
            assertTrue(usedText.isEmpty())
            button("Descartar transcripción")
            button("Usar transcripción")
            capture("revision-editable")
            button("Usar transcripción").performClick()
            assertEquals(listOf("crea tarea \"F7 corregida\" para mañana"), usedText)
            assertEquals(VoicePhase.IDLE, voice.state.value.phase)

            compose.runOnIdle { platform.available = VoiceCapabilities(); voice.refresh() }
            completeText("No hay un servicio de dictado disponible. Puedes escribir tu solicitud.")
            button("Dictar solicitud").assertIsNotEnabled()
            completeText("No hay un motor de lectura disponible. El texto permanece visible.")
            button("Leer texto mostrado").assertIsNotEnabled()
            capture("servicios-ausentes")
            assertEquals(1, platform.recognitionStarts)
            assertEquals(1, platform.speechStarts)
            File(captures, "resultado.txt").writeText("PASA layout/semántica/tamaños táctiles de esta celda con motores controlados. " +
                "No acredita TalkBack, dictado acústico, permisos nativos ni audición. PNG pendientes de revisión visual externa.")
        } catch (failure: Throwable) {
            runCatching { capture("fallo") }
            File(captures, "resultado.txt").writeText("FALLO: ${failure.javaClass.name}: ${failure.message}")
            throw failure
        }
    }

    private fun completeText(value: String, scroll: Boolean = true): SemanticsNodeInteraction {
        val interaction = compose.onNodeWithText(value)
        if (scroll) {
            compose.onNodeWithTag(SCROLL).performScrollToNode(hasText(value))
            interaction.performScrollTo()
        } else {
            // Dialog content has its own viewport, separate from the page's
            // LazyColumn. Reach its real scroll ancestor rather than the page.
            val ownScroll = generateSequence(interaction.fetchSemanticsNode()) { it.parent }
                .any { (it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue() ?: 0f) > 0f }
            if (ownScroll) interaction.performScrollTo()
        }
        interaction.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        interaction.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            assertTrue("No se obtuvo layout de '$value'", action(layouts))
        }
        assertTrue("Texto sin layout: '$value'", layouts.isNotEmpty())
        layouts.forEach { layout ->
            val metrics = "text='$value'; size=${layout.size}; lines=${layout.lineCount}; " +
                "overflowHeight=${layout.didOverflowHeight}; maxLinesExceeded=${layout.multiParagraph.didExceedMaxLines}; " +
                "visible=${interaction.fetchSemanticsNode().boundsInRoot}; full=${interaction.getUnclippedBoundsInRoot()}"
            File(captures, "metricas.txt").appendText("$metrics\n")
            assertFalse("Texto truncado: $metrics", layout.didOverflowHeight)
            assertFalse("Texto limitado por maxLines: $metrics", layout.multiParagraph.didExceedMaxLines)
            assertTrue("Texto sin líneas: $metrics", layout.lineCount > 0)
            for (line in 0 until layout.lineCount) {
                assertTrue("Glifos recortados horizontalmente: $metrics; line=$line",
                    layout.getLineLeft(line) >= -2f && layout.getLineRight(line) <= layout.size.width + 2f)
                assertTrue("Glifos recortados verticalmente: $metrics; line=$line",
                    layout.getLineTop(line) >= -2f && layout.getLineBottom(line) <= layout.size.height + 2f)
                assertFalse("Texto con elipsis: $metrics; line=$line", layout.isLineEllipsized(line))
            }
            assertTrue("El final del texto no está maquetado: $metrics",
                layout.getLineEnd(layout.lineCount - 1, visibleEnd = false) >= layout.layoutInput.text.length)
        }
        assertReachableEdges(interaction, value)
        return interaction
    }

    private fun assertReachableEdges(interaction: SemanticsNodeInteraction, label: String) {
        val density = compose.activity.resources.displayMetrics.density
        val scrollNode = generateSequence(interaction.fetchSemanticsNode()) { it.parent }
            .firstOrNull { (it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue() ?: 0f) > 0f }
        for (bottom in listOf(false, true)) {
            if (scrollNode != null) {
                val viewport = compose.onNode(SemanticsMatcher("viewport de '$label'") { it.id == scrollNode.id }, useUnmergedTree = true)
                val viewportBounds = viewport.fetchSemanticsNode().boundsInRoot
                val full = interaction.getUnclippedBoundsInRoot()
                val delta = if (bottom) full.bottom.value * density - viewportBounds.bottom else full.top.value * density - viewportBounds.top
                viewport.performSemanticsAction(SemanticsActions.ScrollBy) { action -> action(0f, delta) }
                compose.waitForIdle()
            }
            val visible = interaction.fetchSemanticsNode().boundsInRoot
            val full = interaction.getUnclippedBoundsInRoot()
            assertTrue("Texto sin área visible: '$label'", visible.width > 0f && visible.height > 0f)
            assertTrue("Recorte horizontal por viewport: '$label'",
                full.left.value * density >= visible.left - 2f && full.right.value * density <= visible.right + 2f)
            assertTrue("No se alcanza ${if (bottom) "el final" else "el inicio"}: '$label'",
                if (bottom) full.bottom.value * density <= visible.bottom + 2f else full.top.value * density >= visible.top - 2f)
        }
    }

    private fun button(label: String, scroll: Boolean = true): SemanticsNodeInteraction {
        val interaction = completeText(label, scroll)
        assertEquals("Rol accesible de '$label'", Role.Button, interaction.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        assertNotNull("Acción accesible de '$label'", interaction.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick))
        targetSize(interaction, label)
        return interaction
    }

    private fun chip(label: String): SemanticsNodeInteraction {
        val interaction = completeText(label)
        assertNotNull("Selección semántica de '$label'", interaction.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        assertNotNull("Acción accesible de '$label'", interaction.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick))
        targetSize(interaction, label)
        return interaction
    }

    private fun switch(label: String, tag: String? = null): SemanticsNodeInteraction {
        completeText(label)
        val interaction = if (tag == null) compose.onNodeWithContentDescription(label) else compose.onNodeWithTag(tag)
        interaction.performScrollTo().assertIsDisplayed()
        val config = interaction.fetchSemanticsNode().config
        assertEquals("Rol accesible de '$label'", Role.Switch, config.getOrNull(SemanticsProperties.Role))
        assertNotNull("Estado accesible de '$label'", config.getOrNull(SemanticsProperties.ToggleableState))
        assertNotNull("Acción accesible de '$label'", config.getOrNull(SemanticsActions.OnClick))
        assertTrue(config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains(label))
        targetSize(interaction, label)
        return interaction
    }

    private fun targetSize(interaction: SemanticsNodeInteraction, label: String) {
        // The layout of an M3 switch is 52x32 dp while Android expands its touch
        // target. Measuring getUnclippedBoundsInRoot() alone confuses the two.
        assertReachableEdges(interaction, label)
        val density = compose.activity.resources.displayMetrics.density
        val node = interaction.fetchSemanticsNode()
        val scrollNode = generateSequence(node) { it.parent }
            .firstOrNull { (it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue() ?: 0f) > 0f }
        if (scrollNode != null) {
            val scroller = compose.onNode(SemanticsMatcher("viewport táctil de '$label'") { it.id == scrollNode.id }, useUnmergedTree = true)
            val viewport = scroller.fetchSemanticsNode().boundsInRoot
            val layout = interaction.getUnclippedBoundsInRoot()
            val delta = (layout.top.value + layout.bottom.value) * density / 2f - (viewport.top + viewport.bottom) / 2f
            scroller.performSemanticsAction(SemanticsActions.ScrollBy) { action -> action(0f, delta) }
            compose.waitForIdle()
        }
        val current = interaction.fetchSemanticsNode()
        val touch = current.touchBoundsInRoot
        val layout = interaction.getUnclippedBoundsInRoot()
        val widthDp = touch.width / density
        val heightDp = touch.height / density
        File(captures, "metricas.txt").appendText("target='$label'; layoutDp=$layout; touchPx=$touch; touchDp=${widthDp}x$heightDp\n")
        assertTrue("Objetivo táctil de '$label' menor de 48 dp: touch=$touch; density=$density; layout=$layout",
            widthDp >= 47.9f && heightDp >= 47.9f)
        val visibleViewport = if (scrollNode != null) compose.onNode(
            SemanticsMatcher("viewport visible de '$label'") { it.id == scrollNode.id }, useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot else {
            var root = current
            while (root.parent != null) root = requireNotNull(root.parent)
            root.boundsInRoot
        }
        assertTrue("Objetivo táctil recortado para '$label': touch=$touch; viewport=$visibleViewport",
            touch.left >= visibleViewport.left - 2f && touch.right <= visibleViewport.right + 2f &&
                touch.top >= visibleViewport.top - 2f && touch.bottom <= visibleViewport.bottom + 2f)
    }

    private fun stateLabel(label: String) {
        completeText(label)
        assertEquals(LiveRegionMode.Polite, compose.onNodeWithTag("voice_state").fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion))
    }

    private fun capture(label: String) {
        compose.waitForIdle()
        val name = "${(++captureIndex).toString().padStart(2, '0')}-$label"
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(captures, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        val trees = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().map { root ->
            compose.onNode(SemanticsMatcher("root ${root.id}") { it.id == root.id }, useUnmergedTree = true).printToString()
        }
        File(captures, "$name-semantica.txt").writeText(trees.joinToString("\n\n"))
    }

    private class AccessibilityVoicePlatform : VoicePlatform {
        var available = VoiceCapabilities(true, true, true)
        var recognitionStarts = 0
        var speechStarts = 0
        var recognition: RecognitionEvents? = null
        var speech: SpeechEvents? = null
        override fun capabilities() = available
        override fun startRecognition(onDevice: Boolean, listener: RecognitionEvents) { recognitionStarts++; recognition = listener }
        override fun stopRecognition() = Unit
        override fun speak(text: String, listener: SpeechEvents) { speechStarts++; speech = listener }
        override fun stopSpeech() = Unit
        override fun release() = Unit
    }

    private companion object {
        const val SCROLL = "f7_voice_accessibility_scroll"
        const val PRIVACY_TEXT = "El servicio de reconocimiento de Android puede enviar tu audio a sus servidores y necesitar Internet. StudyPlanner no guarda audio. La captura dura hasta 15 segundos y no envía ni confirma cambios. Continúa solo si aceptas usar ese servicio; también puedes escribir."
    }
}
