package pe.studyplanner.aipet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.studyplanner.aipet.domain.profile.ProfileRepository
import pe.studyplanner.aipet.domain.profile.ProfileValidator
import pe.studyplanner.aipet.domain.profile.UserProfile

sealed interface ProfileState {
    data object Loading : ProfileState
    data class Ready(val profile: UserProfile) : ProfileState
    data object ReadError : ProfileState
}

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data object Saved : SaveState
    data class Failed(val message: String) : SaveState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(private val repository: ProfileRepository) : ViewModel() {
    private val reload = MutableStateFlow(0)
    private val mutableSave = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = mutableSave

    val state: StateFlow<ProfileState> = reload.flatMapLatest {
        repository.profile
            .map<UserProfile, ProfileState> { ProfileState.Ready(it) }
            .onStart { emit(ProfileState.Loading) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(ProfileState.ReadError)
            }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProfileState.Loading)

    fun retryRead() { reload.value += 1 }

    fun clearSaveMessage() {
        if (mutableSave.value !is SaveState.Saving) mutableSave.value = SaveState.Idle
    }

    fun save(profile: UserProfile) {
        if (mutableSave.value is SaveState.Saving || state.value !is ProfileState.Ready) return
        if (ProfileValidator.validate(profile).isNotEmpty()) {
            mutableSave.value = SaveState.Failed("Revisa los campos indicados antes de guardar.")
            return
        }
        mutableSave.value = SaveState.Saving
        viewModelScope.launch {
            try {
                repository.save(profile)
                mutableSave.value = SaveState.Saved
            } catch (error: CancellationException) {
                mutableSave.value = SaveState.Idle
                throw error
            } catch (_: IOException) {
                mutableSave.value = SaveState.Failed(
                    "No se pudo guardar en el dispositivo. La configuración anterior se conserva. Reintenta."
                )
            } catch (_: Exception) {
                mutableSave.value = SaveState.Failed(
                    "No se pudo actualizar el perfil. Conservamos los datos anteriores; revisa la configuración."
                )
            }
        }
    }

    fun togglePet() {
        val current = (state.value as? ProfileState.Ready)?.profile ?: return
        save(current.copy(petVisible = !current.petVisible))
    }
}

