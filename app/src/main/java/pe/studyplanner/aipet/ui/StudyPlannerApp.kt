package pe.studyplanner.aipet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pe.studyplanner.aipet.domain.profile.ThemeMode
import pe.studyplanner.aipet.domain.academic.AcademicRepository
import pe.studyplanner.aipet.ui.academic.AcademicScreen
import pe.studyplanner.aipet.ui.academic.AcademicTab
import pe.studyplanner.aipet.ui.components.AppSection
import pe.studyplanner.aipet.ui.theme.StudyPlannerTheme

@Composable
fun StudyPlannerApp(model: ProfileViewModel, academicRepository: AcademicRepository? = null,
    plannerRepository: pe.studyplanner.aipet.domain.planner.PlannerRepository? = null,
    alertService: pe.studyplanner.aipet.data.academic.AlertService? = null, alertLink:String? = null,
    agentGateway: pe.studyplanner.aipet.domain.agent.AgentGateway? = null, voiceEnabled:Boolean=false,
    overlayEnabled: Boolean = false,externalLinkVersion:Long=0L) {
    val state by model.state.collectAsStateWithLifecycle()
    val saveState by model.saveState.collectAsStateWithLifecycle()
    val profile = (state as? ProfileState.Ready)?.profile
    val effectiveDark = when (profile?.theme ?: ThemeMode.SYSTEM) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val activity = LocalContext.current.findActivity()
    val overlayContext = LocalContext.current
    LaunchedEffect(overlayEnabled, profile?.petVisible) {
        if (overlayEnabled && profile?.petVisible != true) pe.studyplanner.aipet.platform.overlay.PetOverlay.stop(overlayContext)
    }
    SideEffect {
        // Sincroniza iconos y fondo con el tema elegido, también en Android 26–28.
        // Los scrims conservan los valores predeterminados de AndroidX Activity 1.10.1.
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { effectiveDark },
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.argb(0xe6, 0xff, 0xff, 0xff),
                darkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b),
            ) { effectiveDark },
        )
    }
    StudyPlannerTheme(profile?.theme ?: ThemeMode.SYSTEM) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (state) {
                ProfileState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text("Abriendo tu espacio…")
                    }
                }
                ProfileState.ReadError -> Column(
                    Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No pudimos leer tu perfil", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Text("Tus datos se conservan. No crearemos un perfil nuevo sobre ellos. Puedes volver a intentar la lectura.")
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = model::retryRead) { Text("Reintentar") }
                }
                is ProfileState.Ready -> {
                    val current = requireNotNull(profile)
                    if (!current.onboardingCompleted) {
                        var showEditor by rememberSaveable { mutableStateOf(false) }
                        var accountMode by rememberSaveable { mutableStateOf<pe.studyplanner.aipet.ui.account.AccountMode?>(null) }
                        val account = pe.studyplanner.aipet.ui.account.rememberCloudAccount()
                        val accountContext = LocalContext.current
                        val accountScope = rememberCoroutineScope()
                        val mode = accountMode
                        if (mode != null && account != null) {
                            // Entrar o crear cuenta al inicio: si la nube ya tiene datos, se recuperan y la app se vuelve a abrir.
                            pe.studyplanner.aipet.ui.account.AccountFormScreen(account, mode, onBack = { accountMode = null }) { name, cloudAt ->
                                if (cloudAt != null && cloudAt > 0) accountScope.launch {
                                    account.downloadForRestore().onSuccess { pe.studyplanner.aipet.data.account.CloudAccount.restartApp(accountContext) }
                                } else model.save(current.copy(studentName = name.ifBlank { current.studentName }, onboardingCompleted = true))
                            }
                        } else if (showEditor) {
                            ProfileEditor(
                                current, true, saveState, model::save, model::clearSaveMessage,
                                onBack = { showEditor = false }, voiceEnabled=voiceEnabled, hasAlerts=alertService!=null
                            )
                        } else {
                            // «Empezar» entra con el zorro y los valores por defecto; elegir mascota y nombre es opcional.
                            WelcomeScreen(onStart = { model.save(current.copy(onboardingCompleted = true)) }, onCustomize = { showEditor = true },
                                onAccount = if (account != null) {{ accountMode = it }} else null,
                                hasAgenda = academicRepository != null, hasAlerts = alertService != null, hasAgent = agentGateway != null, hasVoice=voiceEnabled)
                        }
                    } else {
                        val nav = rememberNavController()
                        LaunchedEffect(alertLink,externalLinkVersion) {
                            val uri=alertLink?.let(android.net.Uri::parse)
                            if(uri?.scheme=="studyplanner" && uri.host=="companion") {
                                // Desde la mascota flotante se abre el Asistente sin encender el micrófono.
                                nav.navigate(AppSection.CHAT.route) { popUpTo(AppSection.TODAY.route) { this.saveState = true }; restoreState = true; launchSingleTop=true }
                                return@LaunchedEffect
                            }
                            val scope=uri?.pathSegments?.firstOrNull()?.takeIf { it in listOf("PERSONAL","DEMO") }
                            if(alertService!=null && scope!=null) nav.navigate("alerts/$scope") { launchSingleTop=true }
                        }
                        val navigateSection: (AppSection) -> Unit = { section ->
                            model.clearSaveMessage()
                            if (section == AppSection.TODAY) {
                                nav.popBackStack(AppSection.TODAY.route, false)
                            } else nav.navigate(section.route) {
                                if (section != AppSection.SETTINGS) {
                                    popUpTo(AppSection.TODAY.route) { this.saveState = true }
                                    restoreState = true
                                }
                                launchSingleTop = true
                            }
                        }
                        val tutorApp = LocalContext.current.applicationContext as? pe.studyplanner.aipet.StudyPlannerApplication
                        NavHost(navController = nav, startDestination = AppSection.TODAY.route) {
                            composable(AppSection.TODAY.route) {
                                TodayScreen(
                                    profile = current,
                                    history = tutorApp?.tutorHistory,
                                    academic = academicRepository,
                                    onNavigate = navigateSection,
                                    onProfile = { navigateSection(AppSection.SETTINGS) },
                                    onTalk = { nav.navigate("chat?hablar=true") { popUpTo(AppSection.TODAY.route) { this.saveState = true }; launchSingleTop = true } },
                                    onPractice = { id -> if (id != null) nav.navigate("tutor/$id") { launchSingleTop = true } else navigateSection(AppSection.PRACTICE) },
                                    onCheck = { id -> if (id != null) nav.navigate("tutor/$id?comprobar=true") { launchSingleTop = true } else navigateSection(AppSection.PRACTICE) },
                                    onReview = { navigateSection(AppSection.CALENDAR) },
                                )
                            }
                            composable(AppSection.HOME.route) {
                                HomeScreen(
                                    profile = current,
                                    saveState = saveState,
                                    onSettings = { navigateSection(AppSection.SETTINGS) },
                                    onNavigate = navigateSection,
                                    history = tutorApp?.tutorHistory,
                                    onOpenTopic = { id -> nav.navigate("tutor/$id") { launchSingleTop = true } },
                                    hasAgenda = academicRepository != null,
                                    onFeature = { feature -> nav.navigate(when(feature){"exams"->"tabs/EXAMS";"alerts"->"alerts/PERSONAL";else->feature}) },
                                )
                            }
                            listOf(AppSection.CALENDAR, AppSection.TASKS, AppSection.CHAT).forEach { section ->
                                composable(if (section == AppSection.CHAT) "chat?hablar={hablar}" else section.route,
                                    arguments = if (section == AppSection.CHAT) listOf(androidx.navigation.navArgument("hablar") {
                                        type = androidx.navigation.NavType.BoolType; defaultValue = false }) else emptyList()) { entry ->
                                    if (section == AppSection.CHAT && agentGateway != null) {
                                        pe.studyplanner.aipet.ui.talk.TalkScreen(agentGateway,current,voiceEnabled,
                                            onNavigate=navigateSection,
                                            onOpenChat={nav.navigate("chat/texto"){launchSingleTop=true}},
                                            onOpenPractice={navigateSection(AppSection.PRACTICE)},
                                            onOpenReview={
                                                val last=tutorApp?.tutorHistory?.state?.value?.attempts?.maxByOrNull{it.timestampMillis}?.topicId
                                                if(last!=null) nav.navigate("tutor/$last"){launchSingleTop=true} else navigateSection(AppSection.PRACTICE)
                                            },
                                            onProfile={navigateSection(AppSection.SETTINGS)},
                                            onPersonalize={nav.navigate("personalize")},
                                            onDevice=if(tutorApp!=null) ({nav.navigate("physical-pet")}) else null,
                                            onTogglePet=model::togglePet,
                                            listenOnOpen=entry.arguments?.getBoolean("hablar") == true,
                                            onAgendaSaved={navigateSection(AppSection.CALENDAR)})
                                    } else if (academicRepository != null && section != AppSection.CHAT) {
                                        AcademicScreen(
                                            repository = academicRepository,
                                            profile = current,
                                            initialTab = if (section == AppSection.CALENDAR) AcademicTab.CALENDAR else AcademicTab.TASKS,
                                            onBack = { nav.popBackStack() },
                                            onNavigate = navigateSection,
                                            onOpenPlanner = plannerRepository?.let { { scope -> nav.navigate("planner/${scope.name}") } },
                                            onOpenAlerts = alertService?.let { { scope -> nav.navigate("alerts/${scope.name}") } },
                                            onProfile = { navigateSection(AppSection.SETTINGS) },
                                            onOpenHabits = { nav.navigate("habits") },
                                            reviewCard = tutorApp?.let { app -> { snap -> pe.studyplanner.aipet.ui.academic.ReviewProposalCard(app.tutorHistory, academicRepository, snap, current) } },
                                        )
                                    } else {
                                        UnavailableSectionScreen(
                                            section = section,
                                            onNavigate = navigateSection,
                                            onBack = { nav.popBackStack() },
                                        )
                                    }
                                }
                            }
                            composable("talk/escuchar") {
                                // Ruta antigua de la mascota flotante: ahora solo redirige al Asistente, sin micrófono.
                                LaunchedEffect(Unit) { navigateSection(AppSection.CHAT) }
                            }
                            composable("chat/texto") {
                                if (agentGateway != null) {
                                        pe.studyplanner.aipet.ui.agent.AgentScreen(agentGateway,
                                            onBack={nav.popBackStack()},onOpenAgenda={scope->nav.navigate("academic/${scope.name}")},
                                            onOpenPlanner={scope->nav.navigate("planner/${scope.name}")},
                                            onOpenAlerts={scope->nav.navigate("alerts/${scope.name}")},
                                            voiceEnabled=voiceEnabled,profile=current,onTogglePet=model::togglePet,onNavigate=navigateSection,
                                            onAiSettings={nav.navigate("ai/connection")})
                                }
                            }
                            composable("create/{kind}") { entry ->
                                if(academicRepository!=null) AcademicScreen(academicRepository,current,
                                    initialTab=if(entry.arguments?.getString("kind")=="EXAM") AcademicTab.EXAMS else AcademicTab.TASKS,
                                    onBack={nav.popBackStack()}, initialCreateKind=pe.studyplanner.aipet.domain.academic.ActivityKind.valueOf(requireNotNull(entry.arguments?.getString("kind"))))
                            }
                            composable(AppSection.PROGRESS.route) {
                                if(academicRepository!=null) ProgressScreen(academicRepository,current,navigateSection)
                            }
                            composable("academic/{scope}") { entry ->
                                if (academicRepository != null) AcademicScreen(academicRepository,current,AcademicTab.TASKS,
                                    onBack={nav.popBackStack()},
                                    onOpenPlanner=plannerRepository?.let{{scope->nav.navigate("planner/${scope.name}")}},
                                    onOpenAlerts=alertService?.let{{scope->nav.navigate("alerts/${scope.name}")}},
                                    initialScope=pe.studyplanner.aipet.domain.academic.DatasetScope.valueOf(requireNotNull(entry.arguments?.getString("scope"))))
                            }
                            composable("academic") {
                                if (academicRepository != null) {
                                    AcademicScreen(academicRepository, current, AcademicTab.COURSES, onBack = { nav.popBackStack() },
                                        onOpenPlanner = plannerRepository?.let { { scope -> nav.navigate("planner/${scope.name}") } },
                                        onOpenAlerts = alertService?.let { { scope -> nav.navigate("alerts/${scope.name}") } })
                                }
                            }
                            composable("tabs/{tab}") { entry ->
                                if(academicRepository!=null) AcademicScreen(academicRepository,current,
                                    initialTab=AcademicTab.valueOf(requireNotNull(entry.arguments?.getString("tab"))),onBack={nav.popBackStack()},
                                    onNavigate=navigateSection,onOpenPlanner=plannerRepository?.let{{scope->nav.navigate("planner/${scope.name}")}},onOpenAlerts=alertService?.let{{scope->nav.navigate("alerts/${scope.name}")}})
                            }
                            composable("study") {
                                pe.studyplanner.aipet.ui.companion.StudyScreen(current,onBack={nav.popBackStack()},onAlerts={nav.navigate("alerts/PERSONAL")})
                            }
                            composable("habits") {
                                pe.studyplanner.aipet.ui.companion.HabitsScreen(current,onBack={nav.popBackStack()})
                            }
                            composable("appearance") {
                                pe.studyplanner.aipet.ui.companion.AppearanceScreen(current,saveState,onTheme={theme->model.save(current.copy(theme=theme))},onBack={nav.popBackStack()})
                            }
                            composable("ai/connection") {
                                val app=LocalContext.current.applicationContext as? pe.studyplanner.aipet.StudyPlannerApplication
                                if(app!=null)pe.studyplanner.aipet.ui.agent.AiConnectionScreen(app.aiConnection,onBack={nav.popBackStack()})
                            }
                            composable("pet/reactions") {
                                pe.studyplanner.aipet.ui.companion.PetReactionsScreen(current,onBack={nav.popBackStack()})
                            }
                            composable("camino") {
                                val app=LocalContext.current.applicationContext as? pe.studyplanner.aipet.StudyPlannerApplication
                                if(app!=null) {
                                    val aiState by app.aiConnection.state.collectAsStateWithLifecycle()
                                    // El tutor del camino conversa con la misma IA en la nube que el resto de la app, solo si está activa.
                                    val transport=remember(aiState.enabled,aiState.hasToken,aiState.endpoint) {
                                        if(aiState.enabled && aiState.hasToken && aiState.endpoint.isNotBlank()) pe.studyplanner.aipet.data.ai.GeminiProxyClient(app.aiConnection) else null
                                    }
                                    pe.studyplanner.aipet.ui.tutor.CaminoScreen(profile=current,history=app.tutorHistory,ai=transport,
                                        onBack={nav.popBackStack()},
                                        onOpenTopic={id->nav.navigate("tutor/$id"){launchSingleTop=true}})
                                }
                            }
                            composable(AppSection.PRACTICE.route) {
                                val app=LocalContext.current.applicationContext as? pe.studyplanner.aipet.StudyPlannerApplication
                                if(app!=null)pe.studyplanner.aipet.ui.tutor.TutorTopicsScreen(app.tutorHistory,
                                    onOpenTopic={id->nav.navigate("tutor/$id"){launchSingleTop=true}},
                                    onNavigate=navigateSection,onBack={nav.popBackStack()})
                            }
                            composable("tutor/{topicId}?comprobar={comprobar}", arguments = listOf(
                                androidx.navigation.navArgument("comprobar") { type = androidx.navigation.NavType.BoolType; defaultValue = false },
                            )) { entry ->
                                val app=LocalContext.current.applicationContext as? pe.studyplanner.aipet.StudyPlannerApplication
                                if(app!=null) {
                                    val aiState by app.aiConnection.state.collectAsStateWithLifecycle()
                                    // «Otra explicación (IA)» solo aparece si el estudiante ya activó la IA en la nube.
                                    val explainer=remember(aiState.enabled,aiState.hasToken,aiState.endpoint) {
                                        if(aiState.enabled && aiState.hasToken && aiState.endpoint.isNotBlank())
                                            pe.studyplanner.aipet.data.tutor.TutorAiExplainer(pe.studyplanner.aipet.data.ai.GeminiProxyClient(app.aiConnection))
                                        else null
                                    }
                                    pe.studyplanner.aipet.ui.tutor.TutorScreen(
                                        topicId=entry.arguments?.getString("topicId").orEmpty(),
                                        history=app.tutorHistory,petLink=app.petLink,academic=academicRepository,ai=explainer,
                                        profile=current,voiceEnabled=voiceEnabled,
                                        onBack={nav.popBackStack()},
                                        onOpenAgenda={nav.navigate("calendar"){launchSingleTop=true}},
                                        startAtCheck=entry.arguments?.getBoolean("comprobar") == true,
                                        // Con la IA activa, la pregunta abierta se puede conversar con el tutor (página del camino).
                                        onAskTutor=if(explainer!=null){json->pe.studyplanner.aipet.data.tutor.CaminoHandoff.put(json);nav.navigate("camino"){launchSingleTop=true}}else null)
                                }
                            }
                            composable("account") {
                                val account = pe.studyplanner.aipet.ui.account.rememberCloudAccount()
                                if (account != null) pe.studyplanner.aipet.ui.account.AccountScreen(account, current.avatar, current.zoneId, onBack = { nav.popBackStack() })
                            }
                            composable("physical-pet") {
                                val app=LocalContext.current.applicationContext as? pe.studyplanner.aipet.StudyPlannerApplication
                                if(app!=null)pe.studyplanner.aipet.ui.companion.PhysicalPetScreen(app.petLink,onBack={nav.popBackStack()})
                            }
                            composable(AppSection.SETTINGS.route) {
                                ProfileHubScreen(current,onBack={nav.popBackStack()},onEdit={nav.navigate("profile/edit")},onPersonalize={nav.navigate("personalize")},onAppearance={nav.navigate("appearance")},onNavigate=navigateSection,onAiSettings={nav.navigate("ai/connection")},onPhysicalPet={nav.navigate("physical-pet")},onAccount={nav.navigate("account")})
                            }
                            listOf("profile/edit","personalize").forEach { route -> composable(route) {
                                ProfileEditor(current,false,saveState,model::save,model::clearSaveMessage,onBack={nav.popBackStack()},voiceEnabled=voiceEnabled,hasAlerts=alertService!=null,
                                    screenTitle=when(route){"personalize"->"Personalización";"appearance"->"Apariencia";else->"Mi información"},
                                    onReactions={nav.navigate("pet/reactions")},
                                    extrasContent=if(overlayEnabled){{pe.studyplanner.aipet.ui.overlay.OverlayPanel(current)}}else null)
                            }}
                            composable("planner/{scope}") { entry ->
                                if (plannerRepository != null) pe.studyplanner.aipet.ui.planner.PlannerScreen(plannerRepository,
                                    pe.studyplanner.aipet.domain.academic.DatasetScope.valueOf(requireNotNull(entry.arguments?.getString("scope"))), onBack = { nav.popBackStack() })
                            }
                            composable("alerts/{scope}") { entry ->
                                if(alertService!=null) pe.studyplanner.aipet.ui.alerts.AlertScreen(alertService,
                                    pe.studyplanner.aipet.domain.academic.DatasetScope.valueOf(requireNotNull(entry.arguments?.getString("scope"))),current.zoneId,onBack={nav.popBackStack()})
                            }
                        }
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
