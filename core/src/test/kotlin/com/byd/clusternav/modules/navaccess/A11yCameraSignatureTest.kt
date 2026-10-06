package com.byd.clusternav.modules.navaccess

import com.byd.clusternav.launcher.HomeActivityCmd
import com.byd.clusternav.launcher.camera.CameraGuard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · CODE-FIX-AFTER-283 (6) — rào camera của lượt chữa phím theo dấu của ĐỜI XE, không tên gói viết cứng ═══════
 *
 * 2.83 rào `GO_HOME_UNLESS_CAMERA` khớp hằng `"com.byd.avc/"` (đo trên Seal DL3). CLAUDE.md §7: khác biệt đời xe nằm ở
 * `ClusterProfile` — đời xe dùng app camera khác thì rào không thấy camera [CHƯA BIẾT cho DL4/DL5]. Nay lệnh nhận dấu
 * camera làm tham số (NavConnect truyền `ClusterProfile.cameraSignature`, đời chưa đo ⇒ dấu 2.83). Mặc định = đúng
 * từng byte 2.83 (`ForceStopReturnHomeTest` khoá chuỗi gốc — §6 không đổi đường đang chạy).
 */
class A11yCameraSignatureTest {

    private val pkg = "com.byd.launcher"
    private val comp = "$pkg/com.byd.clusternav.modules.navaccess.NavAccessibilityService"

    @Test
    fun `mac dinh trung tung byte ban 2_83`() {
        assertEquals(
            CameraGuard.unlessCamera(AccessibilityRebind.CAMERA_SCREEN_SIGNATURE, null, HomeActivityCmd.GO_HOME),
            AccessibilityRebind.GO_HOME_UNLESS_CAMERA,
        )
        assertEquals(AccessibilityRebind.goHomeUnlessCamera(AccessibilityRebind.CAMERA_SCREEN_SIGNATURE), AccessibilityRebind.GO_HOME_UNLESS_CAMERA)
        assertEquals(
            AccessibilityRebind.returnHomeIfOrphaned(AccessibilityRebind.CAMERA_SCREEN_SIGNATURE), AccessibilityRebind.RETURN_HOME_IF_ORPHANED,
        )
        AccessibilityRebind.HomeTail.values().forEach { tail ->
            assertEquals(
                AccessibilityRebind.forceStopRebindCommand("", pkg, comp, homeTail = tail),
                AccessibilityRebind.forceStopRebindCommand("", pkg, comp, homeTail = tail, cameraSig = AccessibilityRebind.CAMERA_SCREEN_SIGNATURE),
                "tham số mới mặc định không đổi một byte ($tail)",
            )
        }
    }

    @Test
    fun `doi xe co dau camera khac - moi lan Home deu rao dung dau do`() {
        val other = "com.oem.camera/"
        AccessibilityRebind.HomeTail.values().forEach { tail ->
            val cmd = AccessibilityRebind.forceStopRebindCommand("", pkg, comp, homeTail = tail, cameraSig = other)
            assertTrue(cmd.contains("*\"$other\"*) ;;"), "$tail: rào phải khớp dấu của đời xe")
            assertFalse(cmd.contains(AccessibilityRebind.CAMERA_SCREEN_SIGNATURE), "$tail: không còn dấu viết cứng 2.83")
            assertTrue(cmd.startsWith("nohup sh -c '") && cmd.count { it == '\'' } == 2, "$tail: không thoát khỏi dấu nháy")
        }
    }

    @Test
    fun `dau camera khong an toan - khong dung lenh`() {
        listOf("com.x';reboot;'", "a b/", "\$(id)/", "").forEach { bad ->
            assertEquals("", AccessibilityRebind.forceStopRebindCommand("", pkg, comp, cameraSig = bad), "dấu hỏng '$bad' ⇒ không leo")
        }
    }
}
