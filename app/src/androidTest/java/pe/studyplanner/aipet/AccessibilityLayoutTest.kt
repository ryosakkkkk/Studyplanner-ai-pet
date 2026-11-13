package pe.studyplanner.aipet

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.studyplanner.aipet.domain.profile.ProfileRepository
import pe.studyplanner.aipet.domain.profile.ThemeMode
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.ui.ProfileViewModel
import pe.studyplanner.aipet.ui.StudyPlannerApp
import pe.studyplanner.aipet.ui.components.AppSection

/**
 * Recorrido de los composables de producción en una Activity Android real.
 * Requiere font_scale=2 configurado en el dispositivo antes de ejecutarlo.
 * Solo uiMode se fija en la composición para cubrir el tema del sistema cuando
 * aún no existe un perfil legible. Orientación, densidad y fuente son reales.
 * El repositorio en memoria y su fallo de lectura son fixtures de prueba:
 * estas capturas no acreditan persistencia, fallo físico de disco ni TalkBack.
 */
class AccessibilityLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val viewModels = ViewModelStore()
    private lateinit var captures: File
    private var captureIndex = 0

    @After fun cleanup() {
        viewModels.clear()
    }

    @Test fun lightPortrait() = runMatrixCell(ThemeMode.LIGHT, Configuration.ORIENTATION_PORTRAIT)
    @Test fun lightLandscape() = runMatrixCell(ThemeMode.LIGHT, Configuration.ORIENTATION_LANDSCAPE)
    @Test fun darkPortrait() = runMatrixCell(ThemeMode.DARK, Configuration.ORIENTATION_PORTRAIT)
    @Test fun darkLandscape() = runMatrixCell(ThemeMode.DARK, Configuration.ORIENTATION_LANDSCAPE)

    private fun runMatrixCell(theme: ThemeMode, orientation: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cell = "${theme.name.lowercase()}-${if (orientation == Configuration.ORIENTATION_PORTRAIT) "portrait" else "landscape"}"
        captures = File(
            requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)),
            "qa-accessibility-200/$cell-${System.currentTimeMillis()}",
        ).apply { check(mkdirs() || isDirectory) }

        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitUntil(15_000) {
            compose.activity.resources.configuration.orientation == orientation
        }
        val actualConfiguration = compose.activity.resources.configuration
        assertEquals("Configura font_scale=2 antes de ejecutar esta matriz", 2f, actualConfiguration.fontScale, 0.01f)
        File(captures, "fixture.txt").writeText(
            """
            screen=Android real; componentes de producción
            fixture=perfil en memoria; fallo de lectura inducido solo en prueba
            fontScale=${actualConfiguration.fontScale}
            orientation=$orientation
            theme=$theme; uiMode fijado solo en CompositionLocalProvider
            sdk=${android.os.Build.VERSION.SDK_INT}
            density=${compose.activity.resources.displayMetrics.density}
            scope=layout; no acredita TalkBack, persistencia ni fallo físico de almacenamiento
            """.trimIndent(),
        )
        val repository = LayoutRepository(UserProfile(theme = theme))
        val model = ProfileViewModel(repository)
        viewModels.put("layout", model)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (theme == ThemeMode.DARK) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                StudyPlannerApp(model)
            }
        }

        try {
            // La recuperación debe conservar el estado de error hasta un reintento válido.
            waitFor("No pudimos leer tu perfil")
            compose.onNodeWithText("No pudimos leer tu perfil").performScrollTo()
            capture("recuperacion-inicio")
            reachable("Reintentar", scroll = true)
            capture("recuperacion-accion")
            val previousReads = repository.readAttempts.get()
            compose.onNodeWithText("Reintentar").performClick()
            compose.waitUntil(10_000) {
                repository.readAttempts.get() > previousReads &&
                    compose.onAllNodesWithText("No pudimos leer tu perfil").fetchSemanticsNodes().isNotEmpty()
            }
            compose.runOnIdle { repository.failReads = false }
            compose.onNodeWithText("Reintentar").performScrollTo().performClick()

            waitFor("Empezar")
            compose.onNodeWithText("STUDYPLANNER AI PET").performScrollTo()
            capture("bienvenida-inicio")
            reachable("Empezar", scroll = true)
            reachable("Omitir presentación", scroll = true)
            reachable("Privacidad y datos", scroll = true)
            capture("bienvenida-acciones")
            compose.onNodeWithText("Privacidad y datos").performClick()
            reachable("Entendido")
            capture("privacidad-inicio")
            assertCompleteScrollableText(PRIVACY_TEXT)
            reachable("Entendido")
            capture("privacidad-desplazada")
            compose.onNodeWithText("Entendido").performClick()
            compose.onNodeWithText("Empezar").performScrollTo().performClick()

            waitFor("Nombre de tu mascota")
            compose.onNodeWithText("Nombre de tu mascota").performScrollTo().performTextReplacement("")
            reachable("Crear mi espacio", scroll = true)
            compose.onNodeWithText("Crear mi espacio").performClick()
            capture("validacion-desde-guardar")
            assertEquals("Un formulario inválido no debe escribir", 0, repository.writes)
            compose.onNodeWithText(PET_ERROR).performScrollTo().assertIsDisplayed()
            capture("validacion-campo")
            compose.onNodeWithText("Nombre de tu mascota").performScrollTo().performTextReplacement("a".repeat(25))
            Espresso.closeSoftKeyboard()
            reachable("Crear mi espacio", scroll = true)
            compose.onNodeWithText("Crear mi espacio").performClick()
            compose.onNodeWithText(PET_ERROR).performScrollTo().assertIsDisplayed()
            capture("validacion-25-grafemas")
            assertEquals("25 grafemas no deben escribir", 0, repository.writes)
            compose.onNodeWithText("Nombre de tu mascota").performScrollTo().performTextReplacement("Mascota QA")
            compose.onNodeWithText("Zona horaria").performScrollTo().performTextReplacement("Zona/Inexistente")
            Espresso.closeSoftKeyboard()
            reachable("Crear mi espacio", scroll = true)
            compose.onNodeWithText("Crear mi espacio").performClick()
            compose.onNodeWithText(ZONE_ERROR).performScrollTo().assertIsDisplayed()
            capture("validacion-zona-invalida")
            assertEquals("Una zona inválida no debe escribir", 0, repository.writes)
            compose.onNodeWithText("Zona horaria").performScrollTo().performTextReplacement("America/Lima")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Nombre de tu mascota").performScrollTo()
            compose.onNodeWithText("Nombre de tu mascota").performClick()
            capture("editor-teclado")
            Espresso.closeSoftKeyboard()
            reachable("Búho", scroll = true)
            compose.onNodeWithText("Búho").performClick()
            compose.onNodeWithText("Búho").assertIsSelected()
            compose.onNodeWithText("Tu nombre (opcional)").performScrollTo().performTextReplacement("QA 200")
            compose.onNodeWithText("Zona horaria").performScrollTo().assertTextContains("America/Lima")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("lunes").performScrollTo().performClick()
            reachable("domingo", scroll = true)
            capture("selector-semana")
            compose.onNodeWithText("domingo").performClick()
            compose.onNodeWithText(if (theme == ThemeMode.DARK) "Oscura" else "Clara")
                .performScrollTo().assertIsSelected()
            reachable("Crear mi espacio", scroll = true)
            capture("editor-acciones")

            compose.onNodeWithContentDescription("Volver").performClick()
            waitFor("¿Descartar tus cambios?")
            reachable("Seguir editando")
            reachable("Descartar")
            capture("descarte-inicio")
            assertCompleteScrollableText("Los cambios que aún no guardaste se perderán.")
            capture("descarte-desplazado")
            compose.onNodeWithText("Seguir editando").performClick()
            compose.onNodeWithText("Nombre de tu mascota").performScrollTo().assertTextContains("Mascota QA")
            compose.onNodeWithContentDescription("Volver").performClick()
            compose.onNodeWithText("Descartar").performClick()
            waitFor("Empezar")
            assertEquals("Descartar el borrador no debe escribir", 0, repository.writes)

            // Preparación explícita del estado completado; no es un guardado
            // desde la UI ni evidencia de DataStore o persistencia del perfil.
            runBlocking {
                repository.save(UserProfile(petName = "Mascota QA", studentName = "QA 200", theme = theme, onboardingCompleted = true))
            }
            waitFor("Hola, QA 200.")
            assertEquals("Una escritura corresponde exclusivamente al fixture", 1, repository.writes)
            completePageText("Hola, QA 200.")
            completePageText("Mascota QA")
            compose.onNodeWithTag("home_pet").performScrollTo().assertIsDisplayed()
            capture("inicio-mascota-visible")
            completePageText("Ocultar mascota")
            compose.onNodeWithText("Ocultar mascota").performScrollTo().performClick()
            waitFor("Mostrar mascota")
            compose.onNodeWithTag("home_pet").assertDoesNotExist()
            completePageText("Tu mascota está oculta. Puedes mostrarla cuando quieras.")
            completePageText("Mostrar mascota")
            capture("inicio-mascota-oculta")
            compose.onNodeWithText("Mostrar mascota").performScrollTo().performClick()
            waitFor("Ocultar mascota")
            assertEquals("Fixture y dos cambios reales de visibilidad en memoria", 3, repository.writes)
            completePageText("Personalizar mi perfil")
            completePageText(HOME_SCOPE_TEXT)
            reachable("Secciones")
            capture("inicio-acciones-inferiores")

            // Comprueba los cinco destinos y el estado seleccionado en cada
            // menú, también cuando sus elementos necesitan desplazamiento.
            for (section in listOf(AppSection.CALENDAR, AppSection.TASKS, AppSection.CHAT)) {
                selectSection(AppSection.HOME, section)
                compose.onNodeWithTag("section_screen_${section.route}").assertExists()
                completePageText("Aún no disponible")
                capture("${section.route}-inicio")
                completePageText(requireNotNull(SECTION_EXPLANATIONS[section]))
                capture("${section.route}-explicacion")
                completePageText(SECTION_SCOPE_TEXT)
                reachable("Secciones")
                capture("${section.route}-acciones")
                // Seleccionar el destino actual debe cerrar el menú sin
                // simular una acción o cambiar la sección.
                selectSection(section, section)
                compose.onNodeWithTag("section_screen_${section.route}").assertExists()
                compose.onNodeWithContentDescription("Volver a Inicio").assertIsDisplayed().performClick()
                waitFor("Hola, QA 200.")
            }
            selectSection(AppSection.HOME, AppSection.SETTINGS)
            waitFor("Tu perfil")
            compose.onNodeWithText("Nombre de tu mascota").performScrollTo().assertTextContains("Mascota QA")
            capture("ajustes-desde-secciones")
            compose.onNodeWithContentDescription("Volver").performClick()
            waitFor("Hola, QA 200.")
            compose.onNodeWithText("Personalizar mi perfil").performScrollTo().performClick()
            waitFor("Tu perfil")
            compose.onNodeWithContentDescription("Volver").performClick()
            waitFor("Hola, QA 200.")
            assertEquals("Navegar no debe añadir escrituras al fixture y dos cambios de visibilidad", 3, repository.writes)
            File(captures, "resultado.txt").writeText("PASA el recorrido automatizado de layout: recuperación, bienvenida, privacidad, validación, editor, descarte, Inicio visible/oculto, cinco destinos del menú y secciones pendientes. Escrituras en memoria: 1 fixture explícito + 2 cambios de visibilidad. No acredita persistencia. Revisar PNG para contenido visual y contraste. TalkBack no ejecutado.")
        } catch (failure: Throwable) {
            runCatching { capture("fallo") }
            File(captures, "resultado.txt").writeText("FALLO: ${failure.javaClass.name}: ${failure.message}")
            throw failure
        }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun selectSection(current: AppSection, destination: AppSection) {
        reachable("Secciones")
        compose.onNodeWithContentDescription("Abrir secciones").performClick()
        for (section in AppSection.entries) {
            val item = compose.onNodeWithTag("section_menu_${section.route}")
            item.performScrollTo().assertIsDisplayed()
            if (section == current) item.assertIsSelected() else item.assertIsNotSelected()
            completePageText(section.label, scroll = false, withinTag = "section_menu_${section.route}")
        }
        capture("menu-${current.route}-seleccion")
        compose.onNodeWithTag("section_menu_${destination.route}").performScrollTo().performClick()
    }

    /** Verifica layout y ambos extremos del texto dentro de su viewport real.
     * Un párrafo largo puede ocupar varias ventanas, pero todo debe alcanzarse. */
    private fun completePageText(text: String, scroll: Boolean = true, withinTag: String? = null) {
        // El título de la sección permanece compuesto detrás del popup. El
        // selector identifica el destino por su etiqueta, sin elegir el primero.
        val interaction = if (withinTag == null) compose.onNodeWithText(text) else compose.onNode(
            hasText(text) and (hasTestTag(withinTag) or hasAnyAncestor(hasTestTag(withinTag))),
        )
        if (scroll) interaction.performScrollTo()
        interaction.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        interaction.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            assertTrue("No se obtuvo layout de '$text'", action(layouts))
        }
        assertTrue("No hay layout de '$text'", layouts.isNotEmpty())
        layouts.forEachIndexed { index, layout ->
            val metrics = buildString {
                append("texto='$text'; resultado=$index/${layouts.size}; ")
                append("overflowWidth=${layout.didOverflowWidth}; overflowHeight=${layout.didOverflowHeight}; ")
                append("size=${layout.size}; constraints=${layout.layoutInput.constraints}; ")
                append("paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}; ")
                append("exceedMaxLines=${layout.multiParagraph.didExceedMaxLines}; ")
                append("lineCount=${layout.lineCount}; maxLines=${layout.layoutInput.maxLines}; softWrap=${layout.layoutInput.softWrap}; ")
                append("fontSize=${layout.layoutInput.style.fontSize}; lineHeight=${layout.layoutInput.style.lineHeight}; ")
                append("bounds=${interaction.fetchSemanticsNode().boundsInRoot}; unclipped=${interaction.getUnclippedBoundsInRoot()}; ")
                append("lines=")
                for (line in 0 until layout.lineCount) {
                    append("[$line:left=${layout.getLineLeft(line)},right=${layout.getLineRight(line)},top=${layout.getLineTop(line)},bottom=${layout.getLineBottom(line)},ellipsized=${layout.isLineEllipsized(line)}]")
                }
            }
            File(captures, "metricas-texto.txt").appendText("$metrics\n")
            // En Compose de este proyecto el párrafo puede conservar el ancho
            // máximo (624 px) y el Text ajustarse al ancho de la línea (457 px).
            // didOverflowWidth resulta true en ese caso aunque los glifos
            // estén íntegros. Comprobamos sus límites reales, no esa caja vacía.
            assertTrue("Texto recortado verticalmente: $metrics", !layout.didOverflowHeight)
            assertTrue("Texto limitado por maxLines: $metrics", !layout.multiParagraph.didExceedMaxLines)
            assertTrue("Texto sin líneas: $metrics", layout.lineCount > 0)
            for (line in 0 until layout.lineCount) {
                assertTrue("Línea $line recortada horizontalmente: $metrics",
                    layout.getLineLeft(line) >= -2f && layout.getLineRight(line) <= layout.size.width + 2f)
                assertTrue("Línea $line recortada verticalmente: $metrics",
                    layout.getLineTop(line) >= -2f && layout.getLineBottom(line) <= layout.size.height + 2f)
                assertTrue("Línea $line con puntos suspensivos: $metrics", !layout.isLineEllipsized(line))
            }
            assertTrue("El layout no contiene el final del texto: $metrics",
                layout.getLineEnd(layout.lineCount - 1, visibleEnd = false) >= layout.layoutInput.text.length)
        }
        val density = compose.activity.resources.displayMetrics.density
        val scrollNode = generateSequence(interaction.fetchSemanticsNode()) { it.parent }
            .firstOrNull { (it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue() ?: 0f) > 0f }
        for (bottom in listOf(false, true)) {
            if (scrollNode != null) {
                val viewport = compose.onNode(SemanticsMatcher("viewport del párrafo") { it.id == scrollNode.id }, useUnmergedTree = true)
                val viewportBounds = viewport.fetchSemanticsNode().boundsInRoot
                val full = interaction.getUnclippedBoundsInRoot()
                val delta = if (bottom) full.bottom.value * density - viewportBounds.bottom
                    else full.top.value * density - viewportBounds.top
                viewport.performSemanticsAction(SemanticsActions.ScrollBy) { action -> action(0f, delta) }
                compose.waitForIdle()
            }
            val visible = interaction.fetchSemanticsNode().boundsInRoot
            val full = interaction.getUnclippedBoundsInRoot()
            assertTrue("Texto sin área visible: '$text'", visible.width > 0f && visible.height > 0f)
            assertTrue("Texto recortado horizontalmente: '$text'", full.left.value * density >= visible.left - 2f && full.right.value * density <= visible.right + 2f)
            assertTrue("No se alcanza ${if (bottom) "el final" else "el inicio"} de '$text'",
                if (bottom) full.bottom.value * density <= visible.bottom + 2f else full.top.value * density >= visible.top - 2f)
        }
    }

    /** Un nodo visible puede contener texto truncado: comprobamos su layout y
     * que se pueda llegar al final cuando la ventana solo admite una parte. */
    private fun assertCompleteScrollableText(text: String) {
        val interaction = compose.onNodeWithText(text)
        interaction.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        interaction.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            assertTrue("No se obtuvo layout de '$text'", action(layouts))
        }
        assertTrue("No hay layout de '$text'", layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertTrue("Texto recortado verticalmente: '$text'", !layout.didOverflowHeight)
            assertTrue("Texto recortado horizontalmente: '$text'", !layout.didOverflowWidth)
        }
        // El scroll puede pertenecer al propio Text o a un contenedor. Un
        // Text medido completo también puede estar recortado por ese padre:
        // assertIsDisplayed y didOverflowHeight no detectan por sí solos eso.
        val scrollNode = generateSequence(interaction.fetchSemanticsNode()) { it.parent }
            .firstOrNull {
                (it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue() ?: 0f) > 0f
            }
        val density = compose.activity.resources.displayMetrics.density
        fun assertVisibleEdge(bottom: Boolean) {
            val visible = interaction.fetchSemanticsNode().boundsInRoot
            val full = interaction.getUnclippedBoundsInRoot()
            val tolerance = 2f
            assertTrue("El texto no tiene área visible: '$text'", visible.width > 0f && visible.height > 0f)
            assertTrue(
                "El texto está recortado horizontalmente por su contenedor: '$text'",
                full.left.value * density >= visible.left - tolerance &&
                    full.right.value * density <= visible.right + tolerance,
            )
            assertTrue(
                "No se puede alcanzar el ${if (bottom) "final" else "inicio"} del texto: '$text'",
                if (bottom) full.bottom.value * density <= visible.bottom + tolerance
                else full.top.value * density >= visible.top - tolerance,
            )
        }
        if (scrollNode == null) {
            assertVisibleEdge(bottom = false)
            assertVisibleEdge(bottom = true)
        } else {
            val scroller = compose.onNode(
                SemanticsMatcher("contenedor desplazable del texto") { it.id == scrollNode.id },
                useUnmergedTree = true,
            )
            val scroll = requireNotNull(scrollNode.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange))
            scroller.performSemanticsAction(SemanticsActions.ScrollBy) { action ->
                assertTrue("El contenido no permite volver al inicio", action(0f, -scroll.maxValue()))
            }
            compose.waitForIdle()
            assertVisibleEdge(bottom = false)
            scroller.performSemanticsAction(SemanticsActions.ScrollBy) { action ->
                assertTrue("El contenido no permite desplazarse hasta el final", action(0f, scroll.maxValue()))
            }
            compose.waitForIdle()
            assertTrue("No se alcanzó el final del texto", scroll.value() >= scroll.maxValue() - 1f)
            assertVisibleEdge(bottom = true)
        }
    }

    /** Comprueba que el nodo completo, no solo una intersección, cabe en su raíz. */
    private fun reachable(text: String, scroll: Boolean = false) {
        val interaction = compose.onNodeWithText(text)
        if (scroll) interaction.performScrollTo()
        interaction.assertIsDisplayed()
        val node = interaction.fetchSemanticsNode()
        var root = node
        while (root.parent != null) root = requireNotNull(root.parent)
        val bounds = interaction.getUnclippedBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        val rootBounds = root.boundsInRoot
        val tolerance = 2f
        assertTrue(
            "'$text' se recorta: nodo=$bounds raíz=$rootBounds densidad=$density",
            bounds.left.value * density >= rootBounds.left - tolerance &&
                bounds.top.value * density >= rootBounds.top - tolerance &&
                bounds.right.value * density <= rootBounds.right + tolerance &&
                bounds.bottom.value * density <= rootBounds.bottom + tolerance,
        )
    }

    private fun capture(label: String) {
        compose.waitForIdle()
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()) {
            "Android no devolvió una captura de pantalla"
        }
        try {
            val file = File(captures, "%02d-%s.png".format(++captureIndex, label))
            file.outputStream().use { check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            screenshot.recycle()
        }
    }

    private class LayoutRepository(initial: UserProfile) : ProfileRepository {
        private val state = MutableStateFlow(initial)
        @Volatile var failReads = true
        val readAttempts = AtomicInteger()
        var writes = 0
            private set
        override val profile: Flow<UserProfile> = flow {
            readAttempts.incrementAndGet()
            if (failReads) throw IOException("Fallo de lectura inducido por AccessibilityLayoutTest")
            emitAll(state)
        }
        override suspend fun save(profile: UserProfile) {
            writes += 1
            state.value = profile
        }
    }

    companion object {
        private const val PET_ERROR = "Escribe un nombre de mascota de 1 a 24 caracteres visibles."
        private const val ZONE_ERROR = "Selecciona una zona horaria válida, por ejemplo America/Lima."
        private const val HOME_SCOPE_TEXT = "En esta versión puedes personalizar tu perfil y tu compañero. La agenda, la IA y los avisos todavía no están disponibles."
        private const val SECTION_SCOPE_TEXT = "Por ahora puedes personalizar tu perfil y tu compañero desde Ajustes. Usa Secciones para seguir explorando."
        private val SECTION_EXPLANATIONS = mapOf(
            AppSection.CALENDAR to "Esta versión todavía no permite registrar ni consultar compromisos. Aquí no se muestran actividades de ejemplo como si fueran tuyas.",
            AppSection.TASKS to "Todavía no puedes crear, editar ni completar tareas en esta versión. No hay tareas registradas mediante la aplicación.",
            AppSection.CHAT to "La conversación con IA todavía no está disponible. Esta pantalla no recibe mensajes, no usa el micrófono ni ejecuta acciones.",
        )
        private const val PRIVACY_TEXT = "Esta versión guarda tu perfil únicamente en este dispositivo. Tu nombre es opcional. No utiliza micrófono, no envía datos a servicios de IA y no recoge datos de investigación. Participar en un estudio requerirá un consentimiento separado."
    }
}
