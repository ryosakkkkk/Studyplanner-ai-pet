package pe.studyplanner.aipet

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import pe.studyplanner.aipet.ui.ProfileViewModel
import pe.studyplanner.aipet.ui.StudyPlannerApp

class MainActivity : ComponentActivity() {
    private var alertLink by mutableStateOf<String?>(null)
    private var externalLinkVersion by mutableLongStateOf(0L)
    // La mascota flotante es para usarla sobre otras apps: dentro de StudyPlanner se oculta para no tapar botones.
    override fun onResume() { super.onResume(); pe.studyplanner.aipet.platform.overlay.PetOverlay.setAppInForeground(true) }
    override fun onPause() { pe.studyplanner.aipet.platform.overlay.PetOverlay.setAppInForeground(false); super.onPause() }
    // Al salir de la app, la copia en la nube se actualiza si hay cuenta.
    override fun onStop() { (application as StudyPlannerApplication).backupInBackground(); super.onStop() }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent);setIntent(intent);alertLink=intent.dataString;externalLinkVersion++ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Arranca el motor del WebView apenas la pantalla queda libre: así la mascota 3D aparece antes.
        android.os.Looper.myQueue().addIdleHandler { runCatching { android.webkit.WebSettings.getDefaultUserAgent(this) }; false }
        alertLink=intent.dataString
        enableEdgeToEdge()
        val app = application as StudyPlannerApplication
        val repository = app.profileRepository
        setContent {
            val model: ProfileViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(ProfileViewModel::class.java))
                    return ProfileViewModel(repository) as T
                }
            })
            StudyPlannerApp(model, app.academicRepository, app.plannerRepository,app.alertService,alertLink,app.agentService,voiceEnabled=true,overlayEnabled=true,externalLinkVersion=externalLinkVersion)
        }
    }
}
