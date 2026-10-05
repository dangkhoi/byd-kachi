package com.byd.clusternav.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [DisplayOwnershipRegistry] — ma trận validate `issuer × display đích → allow/reject` cưỡng chế ranh giới 2-nhánh: launcher
 * sở hữu display 0 + VD nó đã đăng ký (id ≥ 1 bất kỳ); cast sở hữu ĐÚNG id cụm đường cast dò live ([DisplayOwnershipRegistry
 * .setCastDisplay]); không biết ⇒ từ chối; force-stop/[WindowMutation.NO_DISPLAY] qua cho cả hai. Thuần JVM.
 *
 * B4 · DISPLAY-OWNER-DYNAMIC (2.89) khoá: bản cũ ghi cứng `ownerOf(1) = CAST` + `registerVirtualDisplay` đòi `id > 1` ⇒ sau
 * khởi động nguội ô `kachi-slot-0` nhận display 1 [ĐO xe 15/09 · máy ảo 05/10] và mọi lệnh mở app vào ô bị REJECT.
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
    fun `ownerOf - display 0 LAUNCHER, every secondary id unknown until registered or detected`() {
        val r = reg()
        assertEquals(DisplayOwner.LAUNCHER, r.ownerOf(0))
        assertNull(r.ownerOf(1), "display 1 KHÔNG còn mặc định là cụm — ai tạo màn phụ trước thì nó là 1")
        assertNull(r.ownerOf(7))
        assertNull(r.castDisplay())
    }

    @Test
    fun `slot VD with id 1 registered by the launcher is LAUNCHER`() {
        val r = reg()
        r.registerVirtualDisplay(1)   // khởi động nguội: ô kachi-slot-0 là màn phụ đầu tiên ⇒ display 1
        assertEquals(DisplayOwner.LAUNCHER, r.ownerOf(1))
        assertTrue(r.validate(launcherMutation(1), DisplayOwner.LAUNCHER).allowed)
        assertFalse(r.validate(castMutation(1), DisplayOwner.CAST).allowed, "cast không được chạm ô của launcher")
    }

    @Test
    fun `cast id detected live (8) is CAST, launcher is rejected there`() {
        val r = reg()
        r.setCastDisplay(8)
        assertEquals(DisplayOwner.CAST, r.ownerOf(8))
        assertTrue(r.isCastDisplay(8))
        assertTrue(r.validate(castMutation(8), DisplayOwner.CAST).allowed)
        val res = r.validate(launcherMutation(8), DisplayOwner.LAUNCHER)
        assertFalse(res.allowed)
        assertTrue((res as ValidationResult.Reject).reason.contains("cross-boundary"), res.reason)
    }

    @Test
    fun `unknown secondary display is rejected for both issuers (fail-safe deny)`() {
        val r = reg()
        r.registerVirtualDisplay(3)
        r.setCastDisplay(8)
        assertFalse(r.validate(launcherMutation(5), DisplayOwner.LAUNCHER).allowed)
        assertFalse(r.validate(castMutation(5), DisplayOwner.CAST).allowed)
        val reason = (r.validate(launcherMutation(5), DisplayOwner.LAUNCHER) as ValidationResult.Reject).reason
        assertTrue(reason.contains("không có chủ"), reason)
    }

    @Test
    fun `cast id cleared (projection closed or detection missed) - that display is rejected again`() {
        val r = reg()
        r.setCastDisplay(8)
        r.setCastDisplay(null)
        assertNull(r.ownerOf(8))
        assertFalse(r.isCastDisplay(8))
        assertFalse(r.validate(castMutation(8), DisplayOwner.CAST).allowed)
        assertFalse(r.validate(launcherMutation(8), DisplayOwner.LAUNCHER).allowed)
        // `< 1` (cách coordinator báo "dò hụt") cũng là xoá; 0 không bao giờ thành cụm.
        r.setCastDisplay(8)
        r.setCastDisplay(-1)
        assertNull(r.castDisplay())
        r.setCastDisplay(0)
        assertEquals(DisplayOwner.LAUNCHER, r.ownerOf(0))
        assertNull(r.castDisplay())
    }

    @Test
    fun `a new detection replaces the previous cast id`() {
        val r = reg()
        r.setCastDisplay(2)
        r.setCastDisplay(9)
        assertNull(r.ownerOf(2), "phiên cũ không còn là cụm")
        assertEquals(DisplayOwner.CAST, r.ownerOf(9))
    }

    @Test
    fun `collision - a launcher-registered VD always wins over the cast id, and it is logged both ways`() {
        val logs = mutableListOf<String>()
        val r = DisplayOwnershipRegistry(log = { logs += it })
        r.setCastDisplay(4)
        r.registerVirtualDisplay(4)
        assertEquals(DisplayOwner.LAUNCHER, r.ownerOf(4), "Kachi tạo VD này — sự thật mạnh nhất")
        assertEquals(1, logs.size, "đăng ký trùng id cụm phải log: $logs")
        r.setCastDisplay(4)
        assertEquals(DisplayOwner.LAUNCHER, r.ownerOf(4))
        assertEquals(2, logs.size, "báo id cụm trùng VD launcher phải log: $logs")
        r.unregisterVirtualDisplay(4)
        assertEquals(DisplayOwner.CAST, r.ownerOf(4), "VD launcher gỡ rồi ⇒ id cụm (sự thật dò được) có hiệu lực")
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
    fun `registering the main display 0 or a negative id as a virtual display is rejected, id 1 is accepted`() {
        val r = reg()
        assertThrowsIllegalArgument { r.registerVirtualDisplay(0) }
        assertThrowsIllegalArgument { r.registerVirtualDisplay(-1) }
        r.registerVirtualDisplay(1)
        assertTrue(r.registeredVirtualDisplays().contains(1))
    }

    // ─────────── ma trận validate ───────────

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
        val r = reg()
        r.setCastDisplay(2)
        val reason = (r.validate(launcherMutation(2), DisplayOwner.LAUNCHER) as ValidationResult.Reject).reason
        assertTrue(
            reason.contains("LAUNCHER") && reason.contains("2") && reason.contains("CAST"),
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
