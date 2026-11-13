package pe.studyplanner.aipet.domain.companion

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado del enlace con la mascota física. */
enum class PetLinkStatus {
    /** El teléfono no tiene Bluetooth de bajo consumo, o no hay un enlace instalado (versión nula). */
    UNSUPPORTED,

    /** El Bluetooth está apagado. */
    BLUETOOTH_OFF,

    /** Faltan permisos de Bluetooth (o de ubicación en Android 11 y anteriores). */
    PERMISSION_NEEDED,

    /** Disponible pero sin conexión. */
    IDLE,

    /** Buscando una mascota StudyPet cercana. */
    SCANNING,

    /** Conectando y preparando los servicios. */
    CONNECTING,

    /** Lista para recibir caras y sonidos, y para enviar botones. */
    CONNECTED,
}

/**
 * @property message explicación en español para mostrar al estudiante (vacía si no hay nada que decir).
 * @property firmware versión del firmware, cuando ya se leyó STATUS.
 * @property batteryPercent batería estimada, o `null` si la mascota no la mide.
 * @property lastButton último botón recibido (para la pantalla de prueba), por ejemplo "B" o "TALK".
 */
data class PetLinkState(
    val status: PetLinkStatus = PetLinkStatus.IDLE,
    val message: String = "",
    val firmware: String = "",
    val batteryPercent: Int? = null,
    val lastButton: String = "",
) {
    val connected: Boolean get() = status == PetLinkStatus.CONNECTED
    val busy: Boolean get() = status == PetLinkStatus.SCANNING || status == PetLinkStatus.CONNECTING
}

/**
 * Abstracción de la mascota física. El tutor depende de esta interfaz, no de Bluetooth: si no hay mascota
 * ([NullPetLink]) o el Bluetooth falla, la app sigue funcionando igual.
 *
 * Todas las funciones se llaman desde el hilo principal y nunca lanzan excepciones.
 */
interface PetLink {
    val state: StateFlow<PetLinkState>

    /** Botones que llegan de la mascota. Se pierden si nadie escucha. */
    val events: SharedFlow<PetEvent>

    /** Vuelve a comprobar Bluetooth y permisos (por ejemplo, al volver de los ajustes). */
    fun refresh()

    /** Busca una StudyPet y se conecta. Los permisos deben pedirse antes desde la interfaz. */
    fun connect()

    /** Cancela la búsqueda o cierra la conexión. */
    fun disconnect()

    /** Cambia la cara y el texto. Se ignora si no hay conexión. */
    fun showFace(expression: PetExpression, text: String = "")

    /** Hace sonar un sonido. Se ignora si no hay conexión. */
    fun playSound(sound: PetSound)
}

/** Enlace que no hace nada: se usa cuando no hay Bluetooth LE y en las pruebas. */
class NullPetLink(
    message: String = "Este dispositivo no permite conectar la mascota física.",
) : PetLink {
    private val mutable = MutableStateFlow(PetLinkState(PetLinkStatus.UNSUPPORTED, message))
    private val mutableEvents = MutableSharedFlow<PetEvent>()
    override val state: StateFlow<PetLinkState> = mutable.asStateFlow()
    override val events: SharedFlow<PetEvent> = mutableEvents.asSharedFlow()

    override fun refresh() = Unit
    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun showFace(expression: PetExpression, text: String) = Unit
    override fun playSound(sound: PetSound) = Unit
}
