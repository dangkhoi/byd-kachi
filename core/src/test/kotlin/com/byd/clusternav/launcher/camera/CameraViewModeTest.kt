package com.byd.clusternav.launcher.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.92 · mã kiểu hình + thu phóng — mặc định KHÔNG đổi, đời cũ giữ nghĩa, đường vẽ nào làm được gì ═════════════
 *
 * Spec `docs/specs/kachi-292-camera-full-view.html` R1/R2/R5/R6. Mỗi bài khoá một quyết định có thể làm đổi thứ người lái
 * đang thấy mà build vẫn xanh.
 */
class CameraViewModeTest {

    @Test fun `ba kieu, mac dinh Nan thang dung dau`() {
        assertEquals(listOf("STRAIGHT", "WIDE", "FISHEYE"), CameraViewMode.MODES, "thứ tự = chip; mã MỚI đứng sau")
        assertEquals(CameraViewMode.STRAIGHT, CameraViewMode.defaultMode())
        assertFalse(CameraViewMode.isMode("straight"), "mã lưu bền phân biệt hoa thường — prefs_set chuẩn hoá trước")
        assertFalse(CameraViewMode.isMode(null))
    }

    /** Xe không chạm gì ⇒ Nắn thẳng; người lái đã TẮT *Nắn hình* trước 2.92 ⇒ Gương cầu (người thừa kế đúng ý). */
    @Test fun `resolve - vang khoa giu Nan thang, amount 0 doi cu thanh Guong cau`() {
        assertEquals(CameraViewMode.STRAIGHT, CameraViewMode.resolve(null, CameraDewarpPrefs.AMOUNT_DEFAULT))
        assertEquals(CameraViewMode.STRAIGHT, CameraViewMode.resolve(null, 50), "trộn dở dang ≠ tắt")
        assertEquals(CameraViewMode.FISHEYE, CameraViewMode.resolve(null, CameraDewarpPrefs.AMOUNT_MIN))
        assertEquals(CameraViewMode.FISHEYE, CameraViewMode.resolve("rác", CameraDewarpPrefs.AMOUNT_MIN))
        // Đã chọn kiểu tường minh ⇒ kiểu thắng, kể cả khi amount đời cũ là 0.
        assertEquals(CameraViewMode.STRAIGHT, CameraViewMode.resolve("STRAIGHT", CameraDewarpPrefs.AMOUNT_MIN))
        assertEquals(CameraViewMode.WIDE, CameraViewMode.resolve("WIDE", 100))
    }

    @Test fun `effective - GL lam du ba, TV khong co kappa, SV giu hom nay`() {
        val gl = CameraSignalPolicy.RENDER_GL
        val tv = CameraSignalPolicy.RENDER_TEXTURE
        val sv = CameraSignalPolicy.RENDER_SURFACE
        CameraViewMode.MODES.forEach { assertEquals(it, CameraViewMode.effective(it, gl)) }
        assertEquals(CameraViewMode.STRAIGHT, CameraViewMode.effective(CameraViewMode.STRAIGHT, tv))
        assertEquals(CameraViewMode.FISHEYE, CameraViewMode.effective(CameraViewMode.WIDE, tv), "TV không có shader κ")
        assertEquals(CameraViewMode.FISHEYE, CameraViewMode.effective(CameraViewMode.FISHEYE, tv))
        CameraViewMode.MODES.forEach { assertEquals(CameraViewMode.STRAIGHT, CameraViewMode.effective(it, sv)) }
        assertEquals(CameraViewMode.STRAIGHT, CameraViewMode.effective("rác", gl))
        assertEquals(CameraViewMode.FISHEYE, CameraViewMode.effective(CameraViewMode.WIDE, "rác"), "mã kết xuất lạ ⇒ TV")
    }

    @Test fun `Theo cum o kieu tron dai ve chu nhat, con lai giu nguyen`() {
        val cluster = CameraSignalPolicy.SHAPE_CLUSTER
        assertEquals(CameraSignalPolicy.SHAPE_RECT, CameraViewMode.frameShape(CameraViewMode.FISHEYE, cluster))
        assertEquals(CameraSignalPolicy.SHAPE_RECT, CameraViewMode.frameShape(CameraViewMode.WIDE, cluster))
        assertEquals(cluster, CameraViewMode.frameShape(CameraViewMode.STRAIGHT, cluster), "Nắn thẳng y nguyên")
        CameraSignalPolicy.SHAPES.filter { it != cluster }.forEach {
            CameraViewMode.MODES.forEach { m -> assertEquals(it, CameraViewMode.frameShape(m, it)) }
        }
    }

    @Test fun `thu phong 50 den 150 buoc 5, nac va phan tram khu hoi`() {
        assertEquals(20, CameraViewMode.ZOOM_POSITIONS)
        assertTrue(CameraViewMode.isZoomPct(50) && CameraViewMode.isZoomPct(150) && CameraViewMode.isZoomPct(100))
        assertFalse(CameraViewMode.isZoomPct(45) || CameraViewMode.isZoomPct(155))
        for (pos in 0..CameraViewMode.ZOOM_POSITIONS) {
            assertEquals(pos, CameraViewMode.zoomPosition(CameraViewMode.zoomAt(pos)))
        }
        assertEquals(10, CameraViewMode.zoomPosition(CameraViewMode.ZOOM_DEFAULT), "100 % ở giữa thanh")
        assertEquals(0, CameraViewMode.zoomPosition(10), "giá trị lạ kẹp vào miền")
        assertEquals(150, CameraViewMode.zoomAt(99))
    }

    @Test fun `bo so Thang rong mac dinh nam trong mien cua nut`() {
        assertTrue(CameraDewarpPrefs.isKappaPct(CameraViewMode.WIDE_KAPPA_PCT_DEFAULT))
        assertTrue(CameraDewarpPrefs.isPct(CameraViewMode.WIDE_FOCAL_PCT_DEFAULT))
        assertTrue(CameraDewarpPrefs.isPanPct(CameraViewMode.WIDE_PAN_X_PCT_DEFAULT))
        assertTrue(CameraViewMode.WIDE_PAN_X_PCT_DEFAULT < 0, "âm = về phía đuôi (cùng nghĩa camera_dewarp_pan_x)")
    }
}
