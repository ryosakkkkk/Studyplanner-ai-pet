package pe.studyplanner.aipet.ui.tutor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pe.studyplanner.aipet.data.tutor.TutorHistory
import pe.studyplanner.aipet.domain.academic.AcademicRepository
import pe.studyplanner.aipet.domain.companion.PetLink

/**
 * Contenedor de una sesión de práctica. Sobrevive a los giros de pantalla; al salir de la pantalla de tutoría se
 * destruye y la mascota física vuelve a reposo. Toda la lógica está en [TutorCoordinator].
 */
class TutorViewModel(
    topicId: String,
    history: TutorHistory,
    petLink: PetLink,
    academic: AcademicRepository?,
    zoneId: String,
) : ViewModel() {
    val coordinator = TutorCoordinator(topicId, viewModelScope, history, petLink, academic, zoneId)

    override fun onCleared() {
        coordinator.close()
        super.onCleared()
    }
}
