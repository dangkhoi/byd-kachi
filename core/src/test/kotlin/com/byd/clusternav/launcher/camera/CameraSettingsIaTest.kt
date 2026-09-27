package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.testbridge.TestBridgeCommands
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.76 · R1 — IA hai tầng của *Tiện nghi xe › Camera* (AC: danh sách khoá thấy được là ĐÚNG danh sách) ══════════
 *
 * Owner 27/09: *"chỉ để lại setting mà user cần"*. Bài này là hợp đồng: tắt chế độ kiểm thử ⇒ **đúng 11** khoá (10 nếu
 * hồ sơ chưa có bản đồ kênh ⇒ hàng *Nguồn* vắng), không hơn; bật ⇒ thêm đủ tầng kỹ thuật; và hai tầng phủ **trọn** tập
 * `camera_*` mà cầu kiểm thử ghi được — một khoá mới sinh ra mà không xếp tầng là đỏ.
 *
 * ⚠ [P2 · SOÁT Opus 2026-09-27] Con số ở KDoc này từng nói **9** sau khi L7 thêm hai khoá lật gương — chính tệp khoá
 * số lại ghi sai số, trong khi CAM-F1 là một phép **ĐẾM HÀNG** owner làm trên xe. Đổi số ⇒ đổi cả
 * `camera-ia-profile.md` §IA + dòng CAM-F1 của `oncar-runbook-2.76.md`.
 */
class CameraSettingsIaTest {

    /** 2.76 L7: +2 khoá LẬT GƯƠNG từng bên (research §6.2) — người lái quyết, mặc định tắt ⇒ 11 khoá (10 nếu chưa có bản đồ kênh). */
    @Test fun `tat test mode thi chi dung 11 khoa nguoi lai`() {
        assertEquals(
            listOf(
                "camera_signal_enabled", "camera_on_cluster",
                "camera_pos_left", "camera_pos_right",
                "camera_rot_left", "camera_rot_right",
                "camera_mirror_left", "camera_mirror_right",
                "camera_shape", "camera_dewarp_amount", "camera_source",
            ),
            CameraSettingsIa.USER_KEYS,
            "đổi danh sách ⇒ đổi doc camera-ia-profile.md §IA + dòng CAM-F1 của runbook (owner đếm hàng trên xe)",
        )
        CameraSettingsIa.TECH_KEYS.forEach { assertFalse(it in CameraSettingsIa.USER_KEYS, "khoá kỹ thuật $it lộ ra ở tầng người lái") }
    }

    /** Hàng Nguồn là khoá NGƯỜI LÁI có điều kiện (hồ sơ có bản đồ kênh) — điều kiện ấy nối ở `:app` (`cameraChannelSupported`). */
    @Test fun `hang Nguon la khoa nguoi lai co dieu kien`() {
        assertEquals("camera_source", CameraSettingsIa.KEY_SOURCE)
        assertTrue(CameraSettingsIa.KEY_SOURCE in CameraSettingsIa.USER_KEYS)
        assertFalse(CameraSettingsIa.KEY_SOURCE in CameraSettingsIa.TECH_KEYS)
    }

    @Test fun `tang ky thuat co du 16 moc do`() {
        assertEquals(16, CameraSettingsIa.TECH_KEYS.size, "đổi số ⇒ đổi doc camera-ia-profile.md §IA")
        listOf("camera_render", "camera_span", "camera_hal_mode", "camera_gl_texmatrix", "camera_dewarp_k").forEach {
            assertTrue(it in CameraSettingsIa.TECH_KEYS, "$it là móc đo, phải ở tầng kỹ thuật")
        }
    }

    /** Hai tầng rời nhau và hợp lại = ĐÚNG tập `camera_*` của danh sách trắng `prefs_set` — không khoá nào không có tầng. */
    @Test fun `hai tang roi nhau va phu tron danh sach trang camera`() {
        val user = CameraSettingsIa.USER_KEYS.toSet()
        val tech = CameraSettingsIa.TECH_KEYS.toSet()
        assertTrue((user intersect tech).isEmpty(), "một khoá không được ở hai tầng: ${user intersect tech}")
        val writable = TestBridgeCommands.WRITABLE_PREFS_KEYS.filter { it.startsWith("camera_") }.toSet()
        assertEquals(emptySet<String>(), writable - user - tech, "khoá camera chưa xếp tầng")
        assertEquals(emptySet<String>(), (user + tech) - writable, "tầng nhắc một khoá không còn ghi được qua prefs_set (bài canh rữa)")
        assertEquals(CameraSettingsIa.USER_KEYS.size, user.size, "không trùng trong USER_KEYS")
        assertEquals(CameraSettingsIa.TECH_KEYS.size, tech.size, "không trùng trong TECH_KEYS")
    }
}
