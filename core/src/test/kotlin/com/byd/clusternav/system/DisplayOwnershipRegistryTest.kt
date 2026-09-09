package com.byd.clusternav.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [DisplayOwnershipRegistry] — ma trận validate `issuer × display đích → allow/reject` cưỡng chế ranh giới
 * 2-nhánh: launcher sở hữu display 0 + VD của nó; cast sở hữu display 1; force-stop/[WindowMutation.NO_DISPLAY]
 * qua cho cả hai. Thuần JVM.
 */
class DisplayOwnershipRegistryTest {

    private fun reg() = DisplayOwnershipRegistry()

    /** Một mutation của LAUNCHER nhắm [displayId]. */
    private fun launcherMutation(displayId: Int) =
        WindowMutation.LaunchOnDisplay("com.foo/.Main", displayId, windowingMode = 1)

    /** Một mutation của CAST nhắm [displayId] (bọc chuỗi cast qua Raw). */
    private fun castMutation(displayId: Int) =
        WindowMutation.Raw("cast-cmd", targetDisplayId = displayId, priority = MutationPriority.NORMAL)

    // ─────────── ownerOf ───────────

    @Test
    fun `ownerOf maps display 0 to LAUNCHER, display 1 to CAST, unknown to null`() {
        val r = reg()
        assertEquals(DisplayOwner.LAUNCHER, r.ownerOf(0))
        assertEquals(DisplayOwner.CAST, r.ownerOf(1))
        assertNull(r.ownerOf(7))
    }

    @Test
    fun `registered virtual display is owned by LAUNCHER, released on unregister`() {
        val r = reg()
        r.registerVirtualDisplay(7)
        assertEquals(DisplayOwner.LAUNCHER, r.ownerOf(7))
        assertTrue(r.registeredVirtualDisplays().contains(7))
        r.unregisterVirtualDisplay(7)
        assertNull(r.ownerOf(7))
        assertFalse(r.registeredVirtualDisplays().contains(7))
    }

    @Test
    fun `registering display 0 or 1 as a virtual display is rejected`() {
        val r = reg()
        assertThrowsIllegalArgument { r.registerVirtualDisplay(0) }
        assertThrowsIllegalArgument { r.registerVirtualDisplay(1) }
    }

    // ─────────── ma trận validate ───────────

    @Test
    fun `LAUNCHER to cluster display 1 is REJECTED`() {
        val res = reg().validate(launcherMutation(1), DisplayOwner.LAUNCHER)
        assertFalse(res.allowed)
        assertTrue(res is ValidationResult.Reject)
    }

    @Test
    fun `CAST to cluster display 1 is ALLOWED`() {
        assertTrue(reg().validate(castMutation(1), DisplayOwner.CAST).allowed)
    }

    @Test
    fun `LAUNCHER to main display 0 is ALLOWED`() {
        assertTrue(reg().validate(launcherMutation(0), DisplayOwner.LAUNCHER).allowed)
    }

    @Test
    fun `LAUNCHER to its registered virtual display is ALLOWED`() {
        val r = reg()
        r.registerVirtualDisplay(7)
        assertTrue(r.validate(launcherMutation(7), DisplayOwner.LAUNCHER).allowed)
    }

    @Test
    fun `CAST to a launcher virtual display is REJECTED`() {
        val r = reg()
        r.registerVirtualDisplay(7)
        val res = r.validate(castMutation(7), DisplayOwner.CAST)
        assertFalse(res.allowed)
        assertTrue(res is ValidationResult.Reject)
    }

    @Test
    fun `CAST to the launcher main display 0 is REJECTED`() {
        assertFalse(reg().validate(castMutation(0), DisplayOwner.CAST).allowed)
    }

    @Test
    fun `either issuer to an unowned display is REJECTED (fail-safe deny)`() {
        val r = reg()
        assertFalse(r.validate(launcherMutation(99), DisplayOwner.LAUNCHER).allowed)
        assertFalse(r.validate(castMutation(99), DisplayOwner.CAST).allowed)
    }

    @Test
    fun `force-stop NO_DISPLAY is ALLOWED for both issuers`() {
        val r = reg()
        assertTrue(r.validate(WindowMutation.ForceStop("com.foo"), DisplayOwner.LAUNCHER).allowed)
        assertTrue(r.validate(WindowMutation.ForceStop("com.foo"), DisplayOwner.CAST).allowed)
    }

    @Test
    fun `a Raw NO_DISPLAY command is ALLOWED for both issuers`() {
        val r = reg()
        val m = WindowMutation.Raw("settings put global x 1", WindowMutation.NO_DISPLAY)
        assertTrue(r.validate(m, DisplayOwner.LAUNCHER).allowed)
        assertTrue(r.validate(m, DisplayOwner.CAST).allowed)
    }

    @Test
    fun `reject reason names issuer, target and owner`() {
        val reason = (reg().validate(launcherMutation(1), DisplayOwner.LAUNCHER) as ValidationResult.Reject).reason
        assertTrue(
            reason.contains("LAUNCHER") && reason.contains("1") && reason.contains("CAST"),
            "reason should name issuer + target + owner: $reason",
        )
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // ok
        }
    }
}
