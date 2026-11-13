package pe.studyplanner.aipet.ui.academic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.studyplanner.aipet.domain.academic.*

sealed interface AcademicLoadState {
    data object Loading : AcademicLoadState
    data class Ready(val snapshot: AcademicSnapshot) : AcademicLoadState
    data object Failed : AcademicLoadState
}

sealed interface AcademicMutation {
    data object Idle : AcademicMutation
    data object Saving : AcademicMutation
    data class Saved(val message: String) : AcademicMutation
    data class Failed(val message: String) : AcademicMutation
}

/** UI state never claims success until the repository transaction has returned. */
@OptIn(ExperimentalCoroutinesApi::class)
class AcademicViewModel(private val repository: AcademicRepository, initialScope:DatasetScope=DatasetScope.PERSONAL) : ViewModel() {
    private val scopeValue = MutableStateFlow(initialScope)
    private val reload = MutableStateFlow(0)
    private val mutationValue = MutableStateFlow<AcademicMutation>(AcademicMutation.Idle)
    val scope: StateFlow<DatasetScope> = scopeValue
    val mutation: StateFlow<AcademicMutation> = mutationValue
    var lastKnownSnapshot: AcademicSnapshot? = null
        private set
    var editingBaseline: AcademicSnapshot? = null
        private set
    fun beginEditing(snapshot: AcademicSnapshot) { editingBaseline = snapshot }
    fun finishEditing() { editingBaseline = null }
    val state: StateFlow<AcademicLoadState> = combine(scopeValue, reload) { scope, _ -> scope }
        .flatMapLatest { scope ->
            repository.observe(scope)
                .map<AcademicSnapshot, AcademicLoadState> {
                    if (it.scope == scopeValue.value) lastKnownSnapshot = it
                    AcademicLoadState.Ready(it)
                }
                .onStart { emit(AcademicLoadState.Loading) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(AcademicLoadState.Failed)
                }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AcademicLoadState.Loading)

    fun retry() { reload.value += 1 }
    fun clearMessage() {
        if (mutationValue.value !is AcademicMutation.Saving) mutationValue.value = AcademicMutation.Idle
    }
    fun changeScope(scope: DatasetScope) {
        if (mutationValue.value is AcademicMutation.Saving) return
        scopeValue.value = scope
        lastKnownSnapshot = null
        editingBaseline = null
        clearMessage()
    }

    private fun write(success: String, action: suspend (DatasetScope) -> Unit) {
        if (mutationValue.value is AcademicMutation.Saving ||
            (state.value as? AcademicLoadState.Ready)?.snapshot?.scope != scopeValue.value) return
        val scope = scopeValue.value
        mutationValue.value = AcademicMutation.Saving
        viewModelScope.launch {
            try {
                action(scope)
                mutationValue.value = AcademicMutation.Saved(success)
            } catch (error: CancellationException) {
                mutationValue.value = AcademicMutation.Idle
                throw error
            } catch (_: StaleAcademicWriteException) {
                mutationValue.value = AcademicMutation.Failed(
                    "Los datos cambiaron desde que abriste el formulario. Conserva tu borrador, vuelve a la lista y revisa la versión actual."
                )
            } catch (error: IllegalArgumentException) {
                mutationValue.value = AcademicMutation.Failed(error.message ?: "Revisa los datos antes de guardar.")
            } catch (_: Exception) {
                mutationValue.value = AcademicMutation.Failed(
                    "No se pudo completar la operación local. Los datos anteriores se conservan. Puedes reintentar."
                )
            }
        }
    }

    fun saveCourse(course: Course) = write("Curso guardado en este dispositivo.") {
        repository.saveCourse(it, course)
    }
    fun archiveCourse(course: Course) = write("Curso archivado. Sus actividades se conservan.") {
        repository.archiveCourse(it, course.id, course.version)
    }
    fun deleteCourse(course: Course) = write("Curso eliminado.") {
        repository.deleteCourse(it, course.id, course.version)
    }
    fun saveActivity(activity: AcademicActivity) = write("Actividad guardada en este dispositivo.") {
        repository.saveActivity(it, activity)
    }
    fun deleteActivity(activity: AcademicActivity) = write("Actividad eliminada.") {
        repository.deleteActivity(it, activity.id, activity.version)
    }
    fun setCompleted(activity: AcademicActivity) = saveActivity(activity.copy(
        status = if (activity.status == ActivityStatus.COMPLETED) ActivityStatus.TODO else ActivityStatus.COMPLETED
    ))
    fun cancelOccurrence(activityId: String, date: LocalDate, expectedVersion: Long) = write("Solo esta ocurrencia fue cancelada. La serie se conserva.") {
        repository.putException(it, RecurrenceException(activityId, date), expectedVersion)
    }
    fun restoreOccurrence(activityId: String, date: LocalDate, expectedVersion: Long) = write("Ocurrencia restaurada.") {
        repository.removeException(it, activityId, date, expectedVersion)
    }
    fun seedDemo(zoneId: String) = write("Datos ficticios de demostración creados.") {
        check(it == DatasetScope.DEMO)
        repository.seedDemo(LocalDate.now(ZoneId.of(zoneId)))
    }
    fun resetDemo(zoneId: String) = write("Demostración restablecida. Tus datos personales se conservan.") {
        check(it == DatasetScope.DEMO)
        repository.resetDemo(LocalDate.now(ZoneId.of(zoneId)))
    }
}
