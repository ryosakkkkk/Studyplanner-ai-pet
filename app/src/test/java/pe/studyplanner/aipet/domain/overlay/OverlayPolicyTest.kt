package pe.studyplanner.aipet.domain.overlay

import org.junit.Assert.*
import org.junit.Test

class OverlayPolicyTest {
    private val ready = OverlayConditions(true, true, true, true, true)
    @Test fun explicitForegroundRequestCanProceed() { assertNull(OverlayPolicy.rejection(ready)) }
    @Test fun returningFromPermissionWithoutForegroundRequestCannotProceed() { assertNotNull(OverlayPolicy.rejection(ready.copy(foreground=false))) }
    @Test fun hiddenPetCannotStart() { assertNotNull(OverlayPolicy.rejection(ready.copy(petVisible=false))) }
    @Test fun missingSpecialPermissionCannotStart() { assertNotNull(OverlayPolicy.rejection(ready.copy(overlayAllowed=false))) }
    @Test fun blockedNotificationCannotStart() { assertNotNull(OverlayPolicy.rejection(ready.copy(notificationsAllowed=false))) }
    @Test fun lockedOrScreenOffCannotStart() { assertNotNull(OverlayPolicy.rejection(ready.copy(unlocked=false))) }
    @Test fun draggingBeyondEdgesKeepsAllControlsInside() {
        assertEquals(OverlayPosition(8,8), OverlayPosition(-500,-1).within(720,1100,248,144,8))
        assertEquals(OverlayPosition(464,948), OverlayPosition(9999,9999).within(720,1100,248,144,8))
    }
    @Test fun rotationReclampsAnExistingPosition() {
        assertEquals(OverlayPosition(464,300), OverlayPosition(900,300).within(720,1100,248,144,8))
    }
    @Test fun degenerateViewportHasDefinedNonNegativePosition() {
        assertEquals(OverlayPosition(8,8), OverlayPosition(99,-99).within(0,0,248,144,8))
    }
    @Test fun settlingChoosesNearestEdgeWithoutChangingHeight() {
        assertEquals(OverlayPosition(8,300),OverlayPosition(80,300).atNearestEdge(720,1100,208,288,8))
        assertEquals(OverlayPosition(504,300),OverlayPosition(440,300).atNearestEdge(720,1100,208,288,8))
    }
    @Test fun settlingAfterRotationKeepsButtonsWithinViewport() {
        assertEquals(OverlayPosition(8,8),OverlayPosition(999,-100).atNearestEdge(0,0,208,288,8))
        assertEquals(OverlayPosition(504,804),OverlayPosition(9999,9999).atNearestEdge(720,1100,208,288,8))
    }
}
