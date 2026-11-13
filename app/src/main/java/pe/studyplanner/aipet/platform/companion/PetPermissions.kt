package pe.studyplanner.aipet.platform.companion

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Permisos de ejecución que necesita la mascota física según la versión de Android:
 *  - Android 12 o más (API 31+): `BLUETOOTH_SCAN` (buscar) y `BLUETOOTH_CONNECT` (conectar). El primero se declara
 *    con `neverForLocation`: la app no deduce la ubicación a partir de los resultados.
 *  - Android 8 a 11 (API 26 a 30): `ACCESS_FINE_LOCATION`, que el sistema exige para buscar dispositivos BLE.
 *    `BLUETOOTH` y `BLUETOOTH_ADMIN` solo se declaran en el manifiesto (no se piden en ejecución).
 */
object PetPermissions {

    /** Permisos que hay que pedir en ejecución. [sdkInt] se puede pasar para probar cada versión. */
    @SuppressLint("InlinedApi")
    fun required(sdkInt: Int = Build.VERSION.SDK_INT): List<String> =
        if (sdkInt >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** Los permisos que todavía no se concedieron. */
    fun missing(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): List<String> =
        required(sdkInt).filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    /** Por qué se piden, en palabras del estudiante. */
    fun explanation(sdkInt: Int = Build.VERSION.SDK_INT): String =
        if (sdkInt >= Build.VERSION_CODES.S) {
            "Android pide el permiso «Dispositivos cercanos» para buscar y conectar la mascota. " +
                "La app no usa tu ubicación."
        } else {
            "Android 11 y anteriores piden el permiso de ubicación para buscar dispositivos Bluetooth. " +
                "La app no lee ni guarda tu ubicación."
        }

    /** En Android 11 o anterior, muchos teléfonos no devuelven dispositivos Bluetooth con la ubicación apagada. */
    fun needsLocationService(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt < Build.VERSION_CODES.S
}
