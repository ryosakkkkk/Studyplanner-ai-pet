package pe.studyplanner.aipet.data.tutor

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.studyplanner.aipet.domain.tutor.Attempt
import pe.studyplanner.aipet.domain.tutor.MasteryPolicy
import pe.studyplanner.aipet.domain.tutor.MasteryStatus

/**
 * @property attempts todos los intentos conocidos, del más antiguo al más reciente.
 * @property loaded `true` cuando ya se leyó el archivo.
 * @property error mensaje si el archivo no se pudo leer o escribir (los intentos siguen en memoria).
 */
data class TutorHistoryState(
    val attempts: List<Attempt> = emptyList(),
    val loaded: Boolean = false,
    val error: String = "",
) {
    /** Estado de dominio de un tema según el historial. */
    fun masteryOf(topicId: String): MasteryStatus = MasteryPolicy.status(topicId, attempts)
}

/**
 * Historial de práctica en memoria con respaldo en [TutorAttemptStore]. Lee el archivo al iniciar ([start]) y
 * escribe los intentos nuevos en orden, en un hilo de entrada y salida, sin bloquear la interfaz.
 *
 * Vive tanto como la aplicación: un intento anotado se escribe aunque la pantalla que lo generó ya se haya cerrado.
 */
class TutorHistory(
    private val store: TutorAttemptStore,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutable = MutableStateFlow(TutorHistoryState())
    private val pending = Channel<List<Attempt>>(Channel.UNLIMITED)
    private val started = AtomicBoolean(false)

    val state: StateFlow<TutorHistoryState> = mutable.asStateFlow()

    /** Carga el archivo y empieza a escribir. Es seguro llamarla varias veces. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch(io) {
            try {
                val read = store.readAll()
                mutable.update { it.copy(attempts = read.attempts + it.attempts, loaded = true) }
            } catch (_: Exception) {
                mutable.update { it.copy(loaded = true, error = READ_ERROR) }
            }
            for (batch in pending) {
                try {
                    store.append(batch)
                } catch (_: Exception) {
                    mutable.update { it.copy(error = WRITE_ERROR) }
                }
            }
        }
    }

    /** Anota intentos: aparecen de inmediato en [state] y se escriben al archivo en segundo plano. */
    fun append(batch: List<Attempt>) {
        if (batch.isEmpty()) return
        mutable.update { it.copy(attempts = it.attempts + batch) }
        pending.trySend(batch)
    }

    /** CSV para compartir (se genera fuera del hilo principal). */
    suspend fun exportCsv(): String = withContext(io) { store.exportCsv() }

    companion object {
        const val READ_ERROR = "No pudimos leer el registro de práctica. Se empezará con uno nuevo en pantalla."
        const val WRITE_ERROR = "No pudimos guardar el último intento en el dispositivo. Sigue en pantalla, pero podría perderse."
    }
}
