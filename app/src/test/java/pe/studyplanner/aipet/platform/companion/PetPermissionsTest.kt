package pe.studyplanner.aipet.platform.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetPermissionsTest {

    @Test fun androidTwelveAndLaterNeedsScanAndConnect() {
        val expected = listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT")
        assertEquals(expected, PetPermissions.required(31))
        assertEquals(expected, PetPermissions.required(34))
        assertEquals(expected, PetPermissions.required(36))
    }

    @Test fun androidElevenAndEarlierNeedsFineLocationToScan() {
        val expected = listOf("android.permission.ACCESS_FINE_LOCATION")
        assertEquals(expected, PetPermissions.required(30))
        assertEquals(expected, PetPermissions.required(26))
    }

    @Test fun onlyOldAndroidAsksForTheLocationService() {
        assertTrue(PetPermissions.needsLocationService(30))
        assertTrue(PetPermissions.needsLocationService(26))
        assertFalse(PetPermissions.needsLocationService(31))
    }

    @Test fun theExplanationNeverPromisesToUseLocation() {
        val old = PetPermissions.explanation(30)
        val new = PetPermissions.explanation(33)
        assertNotEquals(old, new)
        assertTrue(old.contains("ubicación"))
        assertTrue(old.contains("no lee ni guarda"))
        assertTrue(new.contains("Dispositivos cercanos"))
        assertTrue(new.contains("no usa tu ubicación"))
    }
}
