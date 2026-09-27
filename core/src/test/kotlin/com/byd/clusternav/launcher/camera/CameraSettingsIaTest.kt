package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.testbridge.TestBridgeCommands
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.77 — IA MỘT tầng của *Tiện nghi xe › Camera* (AC: danh sách hàng thấy được là ĐÚNG danh sách) ═══════════════
 *
 * Owner trên xe 27/09 (buổi closing): *"bỏ hết phần nâng cao đi, bỏ luôn nguồn vì chốt là toàn cảnh khung ghép rồi"*
 * · *"bỏ cái 1 cam ra, nhiều option quá rối cho người dùng, bỏ luôn ở phần kỹ thuật"*.
 *
 * Bài này là hợp đồng, và là cái **đỏ lên** khi ai đó thêm một hàng camera vào màn người lái mà không có quyết định:
 *  1. [CameraSettingsIa.USER_KEYS] = **đúng 10 khoá**, đúng thứ tự — owner ĐẾM hàng ấy trên xe (CAM-F1);
 *  2. [CameraSettingsIa.NO_UI_KEYS] rời hẳn USER_KEYS (một khoá không được ở hai danh sách);
 *  3. hợp của hai = **đúng** tập `camera_*` mà `prefs_set` ghi được ⇒ một khoá mới sinh ra mà không xếp vào một trong
 *     hai là đỏ, và một khoá bị bỏ khỏi danh sách trắng mà còn nhắc ở đây cũng đỏ (bài canh rữa).
 *
 * ⚠ Đổi con số ⇒ đổi `camera-ia-profile.md` §IA + dòng CAM-F1 của runbook. 2.76 từng ghi sai số ở KDoc trong khi
 * CAM-F1 là một phép **ĐẾM HÀNG** thật ([P2] soát Opus 27/09) — nên số ở đây phải là số duy nhất.
 */
class CameraSettingsIaTest {

    /** 2.77: **10 hàng** — `camera_source` (hàng *Nguồn*) đã XOÁ cùng cả nguồn *Một camera*. */
    @Test fun `man nguoi lai co dung 10 khoa, dung thu tu`() {
        assertEquals(
            listOf(
                "camera_signal_enabled", "camera_on_cluster",
                "camera_pos_left", "camera_pos_right",
                "camera_rot_left", "camera_rot_right",
                "camera_mirror_left", "camera_mirror_right",
                "camera_shape", "camera_dewarp_amount",
            ),
            CameraSettingsIa.USER_KEYS,
            "đổi danh sách ⇒ đổi doc camera-ia-profile.md §IA + dòng CAM-F1 của runbook (owner đếm hàng trên xe)",
        )
        assertEquals(10, CameraSettingsIa.USER_KEYS.size, "owner ĐẾM đúng 10 hàng trên xe — thêm hàng phải là một quyết định")
        CameraSettingsIa.NO_UI_KEYS.forEach {
            assertFalse(it in CameraSettingsIa.USER_KEYS, "khoá không-UI $it lại lộ ra ở màn người lái")
        }
    }

    /**
     * Hai khoá của nguồn *Một camera* **bị xoá khỏi cả hai danh sách VÀ khỏi danh sách trắng** — không phải "ẩn UI".
     *
     * [ĐO xe 27/09, hai khung thô cùng cảnh] dải ghép có năng lượng cạnh **686 vs 351**, tỉ lệ chi tiết ngang/dọc
     * **0,30 (ghép) vs 0,19 (một kênh)** ⇒ một kênh chỉ bị KÉO NGANG, không mang thêm điểm ảnh thật. Một khoá không
     * còn đường code nào đọc mà vẫn ghi được là một lệnh `prefs_set` báo `ok` rồi không làm gì.
     */
    @Test fun `camera_source va camera_hal_mode da xoa han`() {
        listOf("camera_source", "camera_hal_mode").forEach { k ->
            assertFalse(k in CameraSettingsIa.USER_KEYS, "$k phải XOÁ, không phải ẩn")
            assertFalse(k in CameraSettingsIa.NO_UI_KEYS, "$k phải XOÁ, không phải chuyển sang danh sách không-UI")
            assertFalse(k in TestBridgeCommands.WRITABLE_PREFS_KEYS, "$k không còn ai đọc ⇒ không được ghi được")
        }
    }

    /** 15 khoá còn lại: không hàng nào trên màn, nhưng `prefs_set` vẫn ghi/đọc (đường chẩn đoán CLAUDE.md §15). */
    @Test fun `15 khoa khong co UI van ghi doc duoc qua cau kiem thu`() {
        assertEquals(15, CameraSettingsIa.NO_UI_KEYS.size, "đổi số ⇒ đổi doc camera-ia-profile.md §IA")
        listOf("camera_render", "camera_span", "camera_gl_texmatrix", "camera_dewarp_k", "camera_cam_left").forEach {
            assertTrue(it in CameraSettingsIa.NO_UI_KEYS, "$it không còn hàng ⇒ phải ở danh sách không-UI")
            assertTrue(it in TestBridgeCommands.WRITABLE_PREFS_KEYS, "$it phải còn ghi được để dò trên xe")
        }
    }

    /** Hai danh sách rời nhau và hợp lại = ĐÚNG tập `camera_*` của danh sách trắng `prefs_set`. */
    @Test fun `hai danh sach roi nhau va phu tron danh sach trang camera`() {
        val user = CameraSettingsIa.USER_KEYS.toSet()
        val noUi = CameraSettingsIa.NO_UI_KEYS.toSet()
        assertTrue((user intersect noUi).isEmpty(), "một khoá không được ở hai danh sách: ${user intersect noUi}")
        val writable = TestBridgeCommands.WRITABLE_PREFS_KEYS.filter { it.startsWith("camera_") }.toSet()
        assertEquals(emptySet<String>(), writable - user - noUi, "khoá camera chưa được xếp vào danh sách nào")
        assertEquals(emptySet<String>(), (user + noUi) - writable, "danh sách nhắc một khoá không còn ghi được qua prefs_set (bài canh rữa)")
        assertEquals(CameraSettingsIa.USER_KEYS.size, user.size, "không trùng trong USER_KEYS")
        assertEquals(CameraSettingsIa.NO_UI_KEYS.size, noUi.size, "không trùng trong NO_UI_KEYS")
        assertEquals(25, writable.size, "10 hàng + 15 khoá không-UI")
    }
}
