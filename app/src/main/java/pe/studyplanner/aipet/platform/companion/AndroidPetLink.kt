package pe.studyplanner.aipet.platform.companion

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.studyplanner.aipet.domain.companion.PetEvent
import pe.studyplanner.aipet.domain.companion.PetExpression
import pe.studyplanner.aipet.domain.companion.PetLink
import pe.studyplanner.aipet.domain.companion.PetLinkState
import pe.studyplanner.aipet.domain.companion.PetLinkStatus
import pe.studyplanner.aipet.domain.companion.PetProtocol
import pe.studyplanner.aipet.domain.companion.PetSound

/**
 * Enlace Bluetooth LE con la mascota física StudyPet (ver `esp32/PROTOCOLO_BLE.md`). Usa solo la API de Bluetooth
 * de Android, sin librerías.
 *
 * Reglas que cumple:
 *  - Todo ocurre en el hilo principal: los callbacks de GATT llegan por un [Handler] del hilo principal y los
 *    métodos públicos se llaman desde la interfaz.
 *  - Una sola operación GATT a la vez (cola), con un temporizador por operación para no quedarse esperando.
 *  - De las caras pendientes solo se conserva la última, porque el firmware también aplica solo la última.
 *  - Nunca lanza excepciones hacia afuera: cualquier fallo deja el enlace en reposo con un mensaje en español.
 *  - La app funciona igual sin la mascota: si algo falla, [state] lo explica y el resto de la app no se entera.
 *
 * Los permisos de ejecución se piden desde la interfaz (ver [PetPermissions]) antes de llamar a [connect].
 */
@SuppressLint("MissingPermission")
class AndroidPetLink(context: Context) : PetLink {

    private val appContext: Context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val mutableState = MutableStateFlow(availability())
    private val mutableEvents = MutableSharedFlow<PetEvent>(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val state: StateFlow<PetLinkState> = mutableState.asStateFlow()
    override val events: SharedFlow<PetEvent> = mutableEvents.asSharedFlow()

    private sealed interface Op {
        class WriteChar(val target: BluetoothGattCharacteristic, var value: ByteArray) : Op
        class WriteDesc(val target: BluetoothGattDescriptor, val value: ByteArray) : Op
        class ReadChar(val target: BluetoothGattCharacteristic) : Op
    }

    private var gatt: BluetoothGatt? = null
    private var face: BluetoothGattCharacteristic? = null
    private var cue: BluetoothGattCharacteristic? = null
    private var mtu = PetProtocol.DEFAULT_MTU
    private var discoverStarted = false
    private var userDisconnect = false
    private var scanning = false

    private val queue = ArrayDeque<Op>()
    private var inFlight: Op? = null

    // ---- temporizadores ----

    private val scanTimeout = Runnable {
        if (mutableState.value.status == PetLinkStatus.SCANNING) {
            stopScan()
            val hint = if (PetPermissions.needsLocationService()) {
                " Si tu Android es 11 o anterior, revisa que la ubicación del teléfono esté activada: algunos modelos la exigen para buscar Bluetooth."
            } else {
                ""
            }
            publish(PetLinkState(PetLinkStatus.IDLE, "No encontramos ninguna StudyPet. Enciéndela, acércala e inténtalo otra vez.$hint"))
        }
    }
    private val connectTimeout = Runnable {
        if (mutableState.value.status == PetLinkStatus.CONNECTING) fail("No pudimos conectar con la mascota a tiempo. Inténtalo otra vez.")
    }
    private val mtuFallback = Runnable { gatt?.let { discover(it) } }
    private val closeFallback = Runnable {
        closeGatt()
        publish(availability("Mascota desconectada."))
    }
    private val opTimeout = Runnable {
        Log.w(TAG, "Operación GATT sin respuesta; se continúa con la siguiente.")
        inFlight = null
        pump()
    }

    // ---- PetLink ----

    override fun refresh() {
        val current = mutableState.value
        if (current.busy || current.connected) return
        val fresh = availability()
        mutableState.value = if (fresh.status == PetLinkStatus.IDLE && current.status == PetLinkStatus.IDLE) current else fresh
    }

    override fun connect() {
        val current = mutableState.value
        if (current.busy || current.connected) return
        val fresh = availability()
        if (fresh.status != PetLinkStatus.IDLE) {
            publish(fresh)
            return
        }
        startScan()
    }

    override fun disconnect() {
        when (mutableState.value.status) {
            PetLinkStatus.SCANNING -> {
                stopScan()
                publish(availability())
            }
            PetLinkStatus.CONNECTING, PetLinkStatus.CONNECTED -> {
                userDisconnect = true
                val g = gatt
                if (g == null) {
                    closeGatt()
                    publish(availability("Mascota desconectada."))
                } else {
                    try {
                        g.disconnect()
                        main.postDelayed(closeFallback, DISCONNECT_TIMEOUT_MS)
                    } catch (_: Exception) {
                        closeGatt()
                        publish(availability("Mascota desconectada."))
                    }
                }
            }
            else -> Unit
        }
    }

    override fun showFace(expression: PetExpression, text: String) {
        if (!mutableState.value.connected) return
        val target = face ?: return
        val bytes = PetProtocol.encodeFace(expression, text, PetProtocol.payloadLimit(mtu))
        // Solo cuenta la última cara pendiente: el firmware tampoco aplica las anteriores.
        val pending = queue.firstOrNull { it is Op.WriteChar && it.target === target } as? Op.WriteChar
        if (pending != null) pending.value = bytes else queue.addLast(Op.WriteChar(target, bytes))
        pump()
    }

    override fun playSound(sound: PetSound) {
        if (!mutableState.value.connected) return
        val target = cue ?: return
        while (queue.count { it is Op.WriteChar && it.target === target } >= MAX_QUEUED_CUES) {
            val oldest = queue.indexOfFirst { it is Op.WriteChar && it.target === target }
            if (oldest < 0) break
            queue.removeAt(oldest)
        }
        queue.addLast(Op.WriteChar(target, PetProtocol.encodeCue(sound)))
        pump()
    }

    // ---- disponibilidad ----

    private fun availability(idleMessage: String = ""): PetLinkState {
        val bluetooth = adapter
        return when {
            bluetooth == null || !appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) ->
                PetLinkState(PetLinkStatus.UNSUPPORTED, "Este teléfono no tiene Bluetooth de bajo consumo, así que no puede conectar la mascota física.")
            !isBluetoothOn(bluetooth) ->
                PetLinkState(PetLinkStatus.BLUETOOTH_OFF, "Activa el Bluetooth para conectar la mascota física.")
            PetPermissions.missing(appContext).isNotEmpty() ->
                PetLinkState(PetLinkStatus.PERMISSION_NEEDED, PetPermissions.explanation())
            else -> PetLinkState(PetLinkStatus.IDLE, idleMessage)
        }
    }

    private fun isBluetoothOn(bluetooth: BluetoothAdapter): Boolean = try {
        bluetooth.isEnabled
    } catch (_: Exception) {
        false
    }

    private fun publish(newState: PetLinkState) {
        mutableState.value = newState
    }

    // ---- búsqueda ----

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (mutableState.value.status != PetLinkStatus.SCANNING) return
            stopScan()
            openGatt(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            if (mutableState.value.status != PetLinkStatus.SCANNING) return
            Log.w(TAG, "La búsqueda falló: código $errorCode")
            stopScan()
            publish(PetLinkState(PetLinkStatus.IDLE, "No se pudo buscar la mascota (código $errorCode). Revisa el Bluetooth e inténtalo otra vez."))
        }
    }

    private fun startScan() {
        val scanner = try {
            adapter?.bluetoothLeScanner
        } catch (_: Exception) {
            null
        }
        if (scanner == null) {
            publish(availability())
            return
        }
        // El UUID del servicio va en el anuncio; el nombre es un respaldo (puede ir en la respuesta de escaneo).
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(PetProtocol.SERVICE)).build(),
            ScanFilter.Builder().setDeviceName(PetProtocol.DEVICE_NAME).build(),
        )
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(filters, settings, scanCallback)
            scanning = true
        } catch (_: SecurityException) {
            publish(availability())
            return
        } catch (_: Exception) {
            publish(PetLinkState(PetLinkStatus.IDLE, "No se pudo iniciar la búsqueda. Revisa el Bluetooth e inténtalo otra vez."))
            return
        }
        publish(PetLinkState(PetLinkStatus.SCANNING, "Buscando tu StudyPet… Enciéndela y acércala al teléfono."))
        main.postDelayed(scanTimeout, SCAN_TIMEOUT_MS)
    }

    private fun stopScan() {
        main.removeCallbacks(scanTimeout)
        if (!scanning) return
        scanning = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
            // El Bluetooth pudo apagarse mientras buscaba: no hay nada que detener.
        }
    }

    // ---- conexión ----

    private fun openGatt(device: BluetoothDevice) {
        publish(PetLinkState(PetLinkStatus.CONNECTING, "Conectando con la mascota…"))
        discoverStarted = false
        userDisconnect = false
        mtu = PetProtocol.DEFAULT_MTU
        main.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
        val opened = try {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK, main)
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
        if (opened == null) {
            main.removeCallbacks(connectTimeout)
            publish(availability("No se pudo abrir la conexión con la mascota."))
        } else {
            gatt = opened
        }
    }

    private fun discover(g: BluetoothGatt) {
        main.removeCallbacks(mtuFallback)
        if (discoverStarted) return
        discoverStarted = true
        val started = try {
            g.discoverServices()
        } catch (_: Exception) {
            false
        }
        if (!started) fail("No se pudo preparar la conexión con la mascota.")
    }

    private fun markConnected() {
        main.removeCallbacks(connectTimeout)
        publish(mutableState.value.copy(status = PetLinkStatus.CONNECTED, message = "Mascota conectada."))
    }

    /** Cierra la conexión y deja el enlace en reposo con [message]. */
    private fun fail(message: String) {
        main.removeCallbacks(connectTimeout)
        closeGatt()
        publish(availability(message))
    }

    private fun closeGatt() {
        main.removeCallbacks(opTimeout)
        main.removeCallbacks(mtuFallback)
        main.removeCallbacks(closeFallback)
        main.removeCallbacks(connectTimeout)
        queue.clear()
        inFlight = null
        face = null
        cue = null
        mtu = PetProtocol.DEFAULT_MTU
        discoverStarted = false
        val g = gatt
        gatt = null
        try {
            g?.close()
        } catch (_: Exception) {
            // Ya estaba cerrada.
        }
    }

    // ---- cola de operaciones GATT (una a la vez) ----

    private fun pump() {
        if (inFlight != null) return
        val g = gatt ?: return
        while (true) {
            val op = queue.removeFirstOrNull() ?: return
            val started = try {
                start(g, op)
            } catch (_: SecurityException) {
                fail(PetPermissions.explanation())
                return
            } catch (_: Exception) {
                false
            }
            if (started) {
                inFlight = op
                main.postDelayed(opTimeout, OP_TIMEOUT_MS)
                return
            }
            Log.w(TAG, "No se pudo iniciar una operación GATT; se omite.")
        }
    }

    private fun finishOp() {
        main.removeCallbacks(opTimeout)
        inFlight = null
        pump()
    }

    private fun start(g: BluetoothGatt, op: Op): Boolean = when (op) {
        is Op.WriteChar -> writeCharacteristic(g, op.target, op.value)
        is Op.WriteDesc -> writeDescriptor(g, op.target, op.value)
        is Op.ReadChar -> g.readCharacteristic(op.target)
    }

    // Android 13 (API 33) cambió las firmas de escritura; las anteriores siguen funcionando pero están obsoletas.
    @Suppress("DEPRECATION")
    private fun writeCharacteristic(g: BluetoothGatt, target: BluetoothGattCharacteristic, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(target, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            target.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            target.value = value
            g.writeCharacteristic(target)
        }

    @Suppress("DEPRECATION")
    private fun writeDescriptor(g: BluetoothGatt, target: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(target, value) == BluetoothStatusCodes.SUCCESS
        } else {
            target.value = value
            g.writeDescriptor(target)
        }

    // ---- callbacks de GATT (llegan por el Handler del hilo principal) ----

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g !== gatt) return
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                publish(PetLinkState(PetLinkStatus.CONNECTING, "Preparando la conexión…"))
                val asked = try {
                    g.requestMtu(PetProtocol.PREFERRED_MTU)
                } catch (_: Exception) {
                    false
                }
                if (asked) main.postDelayed(mtuFallback, MTU_TIMEOUT_MS) else discover(g)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                val wasConnected = mutableState.value.connected
                val message = when {
                    userDisconnect -> "Mascota desconectada."
                    wasConnected -> "La mascota se desconectó. Pulsa Conectar para enlazarla otra vez."
                    else -> "No pudimos conectar con la mascota. Inténtalo otra vez."
                }
                closeGatt()
                publish(availability(message))
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (g !== gatt) return
            if (status == BluetoothGatt.GATT_SUCCESS) this@AndroidPetLink.mtu = mtu
            discover(g)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("No se pudieron leer los servicios de la mascota.")
                return
            }
            val service = g.getService(PetProtocol.SERVICE)
            val faceChar = service?.getCharacteristic(PetProtocol.FACE)
            val cueChar = service?.getCharacteristic(PetProtocol.CUE)
            val eventChar = service?.getCharacteristic(PetProtocol.EVENT)
            val cccd = eventChar?.getDescriptor(PetProtocol.CCCD)
            if (service == null || faceChar == null || cueChar == null || eventChar == null || cccd == null) {
                fail("Este dispositivo no es una StudyPet compatible.")
                return
            }
            val subscribed = try {
                g.setCharacteristicNotification(eventChar, true)
            } catch (_: Exception) {
                false
            }
            if (!subscribed) {
                fail("No pudimos activar los botones de la mascota.")
                return
            }
            face = faceChar
            cue = cueChar
            queue.addLast(Op.WriteDesc(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE))
            service.getCharacteristic(PetProtocol.STATUS)?.let { queue.addLast(Op.ReadChar(it)) }
            pump()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (g !== gatt) return
            val isEventSubscription = descriptor.uuid == PetProtocol.CCCD && descriptor.characteristic?.uuid == PetProtocol.EVENT
            if (isEventSubscription) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail("No pudimos activar los botones de la mascota.")
                    return
                }
                markConnected()
            }
            finishOp()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (g !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) Log.w(TAG, "Escritura rechazada por la mascota: estado $status")
            finishOp()
        }

        // Android 13 o más: el valor llega como parámetro.
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            handleRead(g, characteristic.uuid, value, status)
        }

        // Android 12 o menos: el valor está en la característica (API obsoleta desde Android 13).
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            handleRead(g, characteristic.uuid, characteristic.value, status)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleChanged(g, characteristic.uuid, value)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            handleChanged(g, characteristic.uuid, characteristic.value)
        }
    }

    private fun handleRead(g: BluetoothGatt, uuid: UUID, value: ByteArray?, status: Int) {
        if (g !== gatt) return
        if (uuid == PetProtocol.STATUS && status == BluetoothGatt.GATT_SUCCESS && value != null) {
            val parsed = PetProtocol.parseStatus(value)
            if (parsed != null) {
                if (!parsed.compatible) {
                    fail("La mascota tiene el firmware ${parsed.firmware}, que esta versión de la app no entiende. Actualiza la app o la mascota.")
                    return
                }
                publish(mutableState.value.copy(firmware = parsed.firmware, batteryPercent = parsed.batteryPercent))
            }
        }
        finishOp()
    }

    private fun handleChanged(g: BluetoothGatt, uuid: UUID, value: ByteArray?) {
        if (g !== gatt || value == null || uuid != PetProtocol.EVENT) return
        val event = PetProtocol.parseEvent(value) ?: return
        publish(mutableState.value.copy(lastButton = PetProtocol.buttonLabel(event)))
        mutableEvents.tryEmit(event)
    }

    private companion object {
        const val TAG = "StudyPetLink"
        const val EVENT_BUFFER = 16
        const val MAX_QUEUED_CUES = 4
        const val SCAN_TIMEOUT_MS = 12_000L
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val MTU_TIMEOUT_MS = 2_000L
        const val OP_TIMEOUT_MS = 4_000L
        const val DISCONNECT_TIMEOUT_MS = 2_000L
    }
}
