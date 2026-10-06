package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.testbridge.TestBridgeCommands
import com.byd.clusternav.launcher.testbridge.TestBridgeParse
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
 *  1. [CameraSettingsIa.USER_KEYS] = **đúng 14 khoá**, đúng thứ tự — owner ĐẾM hàng ấy trên xe (CAM-F1);
 *  2. [CameraSettingsIa.NO_UI_KEYS] rời hẳn USER_KEYS (một khoá không được ở hai danh sách);
 *  3. hợp của hai = **đúng** tập `camera_*` mà `prefs_set` ghi được ⇒ một khoá mới sinh ra mà không xếp vào một trong
 *     hai là đỏ, và một khoá bị bỏ khỏi danh sách trắng mà còn nhắc ở đây cũng đỏ (bài canh rữa).
 *
 * ⚠ Đổi con số ⇒ đổi `camera-ia-profile.md` §IA + dòng CAM-F1 của runbook. 2.76 từng ghi sai số ở KDoc trong khi
 * CAM-F1 là một phép **ĐẾM HÀNG** thật ([P2] soát Opus 27/09) — nên số ở đây phải là số duy nhất.
 */
class CameraSettingsIaTest {

    /**
     * 2.77: 10 hàng — `camera_source` (hàng *Nguồn*) đã XOÁ cùng cả nguồn *Một camera*.
     * 2.80/2.81: **14 hàng** — thêm *thử camera số* và *dải hình*, mỗi thứ hai bên (CAM-SL6-RIGHT: owner trên
     * Sealion 6 không mở được cam phải, cần đường tự dò). Xem `specs/kachi-camera-source-picker.html`.
     * 2.82: 16 hàng (+2 *vạch chuẩn khoảng cách*) · 2.83: **về lại 14** — owner gỡ hẳn vạch (*"dẹp vạch đi"*),
     * xem bài `vach chuan khoang cach da xoa han` bên dưới.
     */
    @Test fun `man nguoi lai co dung 10 khoa, dung thu tu`() {
        assertEquals(
            listOf(
                "camera_signal_enabled", "camera_on_cluster",
                // 2.93 · CAMERA-PER-CAM-CONFIG: góc · xoay · lật trái/phải (6 khoá) DỜI sang bộ chỉnh *Từng camera* — một
                // khoá không được có HAI hàng trên cùng màn (bài `bo chinh tung camera mang dung 28 khoa` dưới).
                "camera_shape",
                // 2.92 (owner 06/10 *"cắt hơi lố"* · *"lấy hết được không?"*): ô tích *Nắn hình* (`camera_dewarp_amount`)
                // ⇒ hàng chip *Kiểu hình* + thanh *Thu phóng* — một QUYẾT ĐỊNH về IA, spec kachi-292-camera-full-view R1/R5.
                "camera_projection", "camera_zoom",
                // +2 (2026-09-28): khối *Nếu camera không hiện*. Owner trên SL6 không mở được cam phải và
                // yêu cầu tự chọn được góc nhìn — đây là một QUYẾT ĐỊNH về IA, không phải lỡ tay thêm hàng.
                "camera_view_left", "camera_view_right",
                // +2 (2026-09-28, cùng ngày) — dải hình từng bên. Không có nó thì chọn được số camera mà hình
                // vẫn ra nguyên khung ghép: đúng lỗi owner báo trên SL6.
                "camera_pano_left", "camera_pano_right",
            ),
            CameraSettingsIa.USER_KEYS,
            "đổi danh sách ⇒ đổi doc camera-ia-profile.md §IA + dòng CAM-F1 của runbook (owner đếm hàng trên xe)",
        )
        assertEquals(9, CameraSettingsIa.USER_KEYS.size, "owner ĐẾM hàng trên xe — thêm hàng phải là một quyết định")
        CameraSettingsIa.NO_UI_KEYS.forEach {
            assertFalse(it in CameraSettingsIa.USER_KEYS, "khoá không-UI $it lại lộ ra ở màn người lái")
        }
    }

    /**
     * 2.93 · CAMERA-PER-CAM-CONFIG (owner 06/10 *"cho chỉnh size và vị trí từng camera"*) — bộ chỉnh *Từng camera*: bốn
     * camera × bảy khoá, sáu khoá cũ của hai camera gương (góc 2.35 · xoay 2.71 · lật 2.76) DÙNG LẠI chứ không nhân đôi.
     */
    @Test fun `bo chinh tung camera mang dung 28 khoa, dung lai sau khoa cu`() {
        assertEquals(CameraCamConfig.ALL_KEYS, CameraSettingsIa.PER_CAMERA_KEYS)
        assertEquals(28, CameraSettingsIa.PER_CAMERA_KEYS.size)
        listOf("camera_pos_left", "camera_pos_right", "camera_rot_left", "camera_rot_right", "camera_mirror_left", "camera_mirror_right")
            .forEach { assertTrue(it in CameraSettingsIa.PER_CAMERA_KEYS, "$it phải là khoá CŨ dùng lại ở bộ chỉnh, không khoá mới") }
        assertEquals(22, CameraCamConfig.NEW_KEYS.size, "28 − 6 khoá cũ")
        CameraSettingsIa.PER_CAMERA_KEYS.forEach {
            assertFalse(it in CameraSettingsIa.USER_KEYS, "$it có hai hàng trên cùng màn")
            assertFalse(it in CameraSettingsIa.NO_UI_KEYS, "$it có hàng ở bộ chỉnh ⇒ không phải khoá không-UI")
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

    /**
     * 2.83 — hai khoá VẠCH CHUẨN khoảng cách (thêm ở 2.82) **bị xoá hẳn**, cùng lệ với cặp nguồn một-kênh của 2.77.
     *
     * Owner 29/09 sau buổi xe: *"dẹp vạch đi"*. Xoá khỏi cả hai danh sách IA **và** khỏi danh sách trắng, rồi hỏi
     * thẳng bộ phân tích: `prefs_set` với khoá cũ phải trả `bad_prefs_key:<khoá>` — không phải `ok` rồi ghi vào một
     * khoá không còn ai đọc. Hỏi qua `parse` (hành vi) chứ không chỉ `!in` (dữ liệu): một đường tắt nào đó nhận khoá
     * trước danh sách trắng thì bài `!in` vẫn xanh còn bài này đỏ.
     *
     * Giá trị cũ trên đĩa (xe đã cài 2.82 và canh vạch) để nguyên: không dòng mã nào đọc nó nữa, và đó là prefs của
     * chính Kachi, không phải state hệ thống (CLAUDE.md §5 không áp) — cùng cách 2.77 để lại `camera_source`.
     */
    @Test fun `vach chuan khoang cach da xoa han`() {
        listOf("camera_guide_left", "camera_guide_right").forEach { k ->
            assertFalse(k in CameraSettingsIa.USER_KEYS, "$k phải XOÁ, không phải ẩn")
            assertFalse(k in CameraSettingsIa.NO_UI_KEYS, "$k phải XOÁ, không phải chuyển sang danh sách không-UI")
            assertFalse(k in TestBridgeCommands.WRITABLE_PREFS_KEYS, "$k không còn ai đọc ⇒ không được ghi được")
            val r = TestBridgeCommands.parse(
                mapOf(
                    TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.PREFS_SET,
                    TestBridgeCommands.EXTRA_KEY to k,
                    TestBridgeCommands.EXTRA_TEXT to "5",
                ),
                emptySet(),
            )
            assertEquals(
                TestBridgeParse.Err(TestBridgeCommands.ERR_BAD_PREFS_KEY + k), r,
                "prefs_set $k phải bị từ chối ở tầng phân tích, nêu đúng tên khoá",
            )
        }
    }

    /** 15 khoá còn lại: không hàng nào trên màn, nhưng `prefs_set` vẫn ghi/đọc (đường chẩn đoán CLAUDE.md §15). */
    @Test fun `19 khoa khong co UI van ghi doc duoc qua cau kiem thu`() {
        // 15 → 19 (2.92): +`camera_dewarp_amount` (ô tích gỡ, khoá vẫn đọc) + ba núm `camera_wide_*` của *Thẳng rộng*.
        assertEquals(19, CameraSettingsIa.NO_UI_KEYS.size, "đổi số ⇒ đổi doc camera-ia-profile.md §IA")
        listOf(
            "camera_render", "camera_span", "camera_gl_texmatrix", "camera_dewarp_k", "camera_cam_left",
            "camera_dewarp_amount", "camera_wide_kappa", "camera_wide_focal", "camera_wide_pan_x",
        ).forEach {
            assertTrue(it in CameraSettingsIa.NO_UI_KEYS, "$it không còn hàng ⇒ phải ở danh sách không-UI")
            assertTrue(it in TestBridgeCommands.WRITABLE_PREFS_KEYS, "$it phải còn ghi được để dò trên xe")
        }
    }

    /** Hai danh sách rời nhau và hợp lại = ĐÚNG tập `camera_*` của danh sách trắng `prefs_set`. */
    @Test fun `hai danh sach roi nhau va phu tron danh sach trang camera`() {
        val user = CameraSettingsIa.USER_KEYS.toSet()
        val perCam = CameraSettingsIa.PER_CAMERA_KEYS.toSet()
        val noUi = CameraSettingsIa.NO_UI_KEYS.toSet()
        assertTrue((user intersect noUi).isEmpty(), "một khoá không được ở hai danh sách: ${user intersect noUi}")
        assertTrue((user intersect perCam).isEmpty() && (perCam intersect noUi).isEmpty(), "một khoá ở hai danh sách")
        val writable = TestBridgeCommands.WRITABLE_PREFS_KEYS.filter { it.startsWith("camera_") }.toSet()
        assertEquals(emptySet<String>(), writable - user - perCam - noUi, "khoá camera chưa được xếp vào danh sách nào")
        assertEquals(emptySet<String>(), (user + perCam + noUi) - writable, "danh sách nhắc một khoá không còn ghi được qua prefs_set (bài canh rữa)")
        assertEquals(CameraSettingsIa.USER_KEYS.size, user.size, "không trùng trong USER_KEYS")
        assertEquals(CameraSettingsIa.PER_CAMERA_KEYS.size, perCam.size, "không trùng trong PER_CAMERA_KEYS")
        assertEquals(CameraSettingsIa.NO_UI_KEYS.size, noUi.size, "không trùng trong NO_UI_KEYS")
        assertEquals(CameraSettingsIa.ALL_KEYS.toSet(), user + perCam + noUi)
        // 31 → 29 (2.83): −2 vạch chuẩn khoảng cách (2.82), gỡ hẳn theo owner. 29 → 34 (2.92): +kiểu hình, +thu phóng,
        // +3 núm Thẳng rộng. 34 → 56 (2.93): +22 khoá MỚI của bộ chỉnh *Từng camera* (6 khoá cũ chỉ đổi danh sách).
        assertEquals(56, writable.size, "9 hàng chung + 28 khoá bộ chỉnh từng camera + 19 khoá không-UI (2.93)")
    }
}
