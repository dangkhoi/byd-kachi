package com.byd.clusternav.launcher.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ R7 · MA TRẬN crop + xoay của overlay camera — chứng minh BẰNG SỐ, off-car ══════════════════════════════════
 *
 * Trước bản này phép toán nằm trong `:app` và **không có test nào**: `android.graphics.Matrix` là stub trên JVM (dự
 * án không dùng Robolectric) nên hai công thức dễ sai nhất — dịch sau crop, và bù tỉ lệ khi xoay ±90 trong khung
 * KHÔNG vuông — chỉ được "kiểm" bằng mắt trên xe đang chạy. Bài này kiểm bằng cách **map bốn góc vùng crop** và đòi
 * chúng rơi đúng bốn góc view: điều đó đồng thời chứng minh (a) tâm crop ở tâm view, (b) crop **phủ đủ** view ⇒
 * không lộ mép đen, (c) không tràn quá cần.
 *
 * Quy ước: [CameraOverlayTransform.mapSource] nhận `(u,v)` chuẩn hoá của ẢNH NGUỒN — đúng thứ `TextureView` căng ra
 * trước khi nhân ma trận.
 */
class CameraOverlayTransformTest {

    private companion object {
        /** Crop gương TRÁI thật của `CamView.MIRROR_LEFT` (RE kinex pano) — dải DỌC 10 % bề ngang. */
        val MIRROR_LEFT = floatArrayOf(0.25f, 0f, 0.35f, 1f)
        const val EPS = 0.02f
    }

    /** [CameraOverlayTransform.matrix] nhưng NỔ nếu `null` — dùng cho ca bắt buộc phải có ma trận. */
    private fun mat(vw: Int, vh: Int, crop: FloatArray?, deg: Int): FloatArray =
        requireNotNull(CameraOverlayTransform.matrix(vw, vh, crop, deg)) { "chờ có ma trận cho ${vw}x$vh deg=$deg" }

    /** Bốn góc vùng [crop] sau biến đổi, theo thứ tự (x0y0, x1y0, x1y1, x0y1) — px trong view. */
    private fun cropCorners(vw: Int, vh: Int, crop: FloatArray, deg: Int): List<Pair<Float, Float>> {
        val m = CameraOverlayTransform.matrix(vw, vh, crop, deg)
        return listOf(
            crop[0] to crop[1], crop[2] to crop[1], crop[2] to crop[3], crop[0] to crop[3],
        ).map { (u, v) ->
            val p = CameraOverlayTransform.mapSource(m, vw, vh, u, v)
            p[0] to p[1]
        }
    }

    /** Tập bốn góc (không quan tâm thứ tự) có trùng chữ nhật view `0..vw × 0..vh` không. */
    private fun assertFillsView(vw: Int, vh: Int, corners: List<Pair<Float, Float>>, what: String) {
        val want = listOf(0f to 0f, vw.toFloat() to 0f, vw.toFloat() to vh.toFloat(), 0f to vh.toFloat())
        want.forEach { (wx, wy) ->
            assertTrue(
                corners.any { (x, y) -> Math.abs(x - wx) < EPS && Math.abs(y - wy) < EPS },
                "$what: góc view ($wx,$wy) KHÔNG có góc crop nào rơi vào ⇒ hở mép đen. Bốn góc crop = $corners",
            )
        }
        // Và ngược lại: không góc crop nào nằm ngoài khung (nếu có thì hình bị phóng quá, mất vùng nhìn).
        corners.forEach { (x, y) ->
            assertTrue(
                x > -EPS && x < vw + EPS && y > -EPS && y < vh + EPS,
                "$what: góc crop ($x,$y) nằm NGOÀI view ${vw}×$vh ⇒ phóng quá, cắt mất vùng nhìn",
            )
        }
    }

    // ══ 1 · CROP (đường đã chạy hiện trường từ 2.36 — CLAUDE.md §6, ghim lại nguyên si) ═══════════════════════

    /** Không xoay: vùng crop căng ĐÚNG khung view, và ma trận đúng công thức `setScale`+`postTranslate` cũ. */
    @Test fun `crop khong xoay cang dung khung view`() {
        val vw = 360
        val vh = 360
        assertFillsView(vw, vh, cropCorners(vw, vh, MIRROR_LEFT, 0), "crop 0°")
        val m = mat(vw, vh, MIRROR_LEFT, 0)
        // sx = 1/0.10 = 10 ; tx = -0.25·10·360 = -900 ; sy = 1/1 = 1 ; ty = 0 — y nguyên công thức trước R7.
        assertEquals(10f, m[0], 1e-3f); assertEquals(0f, m[1]); assertEquals(-900f, m[2], 1e-2f)
        assertEquals(0f, m[3]); assertEquals(1f, m[4], 1e-3f); assertEquals(0f, m[5], 1e-3f)
        assertEquals(1f, m[8])
    }

    /** Crop toàn khung, hoặc view suy biến, hoặc không crop + không xoay ⇒ `null` = KHÔNG đụng `setTransform`. */
    @Test fun `khong can transform thi tra null`() {
        assertNull(CameraOverlayTransform.matrix(360, 360, null, 0))
        assertNull(CameraOverlayTransform.matrix(360, 360, floatArrayOf(0f, 0f, 1f, 1f), 0))
        assertNull(CameraOverlayTransform.matrix(0, 360, MIRROR_LEFT, 90), "view rộng 0")
        assertNull(CameraOverlayTransform.matrix(360, -1, MIRROR_LEFT, 90), "view cao âm")
        assertNull(CameraOverlayTransform.matrix(360, 360, floatArrayOf(0f, 0f), 0), "crop thiếu phần tử")
        // Crop toàn khung nhưng CÓ xoay ⇒ vẫn phải có ma trận (xoay không phụ thuộc crop).
        assertNotNull(CameraOverlayTransform.matrix(360, 360, floatArrayOf(0f, 0f, 1f, 1f), 180))
    }

    /** Dải suy biến (x1 == x0) không được chia cho 0 ⇒ ma trận vẫn hữu hạn. */
    @Test fun `crop suy bien khong chia 0`() {
        val m = mat(360, 360, floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f), 90)
        assertTrue(m.all { it.isFinite() }, "ma trận có NaN/Inf: ${m.toList()}")
    }

    // ══ 2 · XOAY ±90 — khung VUÔNG (cửa sổ thật hôm nay) ══════════════════════════════════════════════════════

    /**
     * Owner 2026-09-26, khung vuông 360×360, crop gương trái, xoay **−90** (↺): vùng crop vẫn phủ đủ view, tâm crop
     * ở tâm view. Đây chính là ca chạy thật trên xe.
     */
    @Test fun `xoay -90 khung vuong phu du view va giu tam`() {
        val w = 360
        assertFillsView(w, w, cropCorners(w, w, MIRROR_LEFT, -90), "vuông −90°")
        val c = CameraOverlayTransform.mapSource(
            mat(w, w, MIRROR_LEFT, -90), w, w,
            (MIRROR_LEFT[0] + MIRROR_LEFT[2]) / 2f, (MIRROR_LEFT[1] + MIRROR_LEFT[3]) / 2f,
        )
        assertEquals(w / 2f, c[0], EPS, "tâm crop lệch ngang khỏi tâm view")
        assertEquals(w / 2f, c[1], EPS, "tâm crop lệch dọc khỏi tâm view")
    }

    /** Cả bốn góc bội-90 (kể cả 180 và 270 = −90 chuẩn hoá) đều phủ đủ khung vuông. */
    @Test fun `moi goc boi 90 phu du khung vuong`() {
        listOf(0, 90, 180, 270, -90, -270, 450).forEach { deg ->
            assertFillsView(400, 400, cropCorners(400, 400, MIRROR_LEFT, deg), "vuông $deg°")
        }
    }

    /** Chiều quay: `−90` đưa mép TRÊN của crop sang mép TRÁI của view (↺); `+90` sang mép PHẢI (↻). */
    @Test fun `chieu quay -90 la nguoc kim dong ho`() {
        val w = 400
        // Điểm giữa mép TRÊN của vùng crop.
        val uMid = (MIRROR_LEFT[0] + MIRROR_LEFT[2]) / 2f
        val ccw = CameraOverlayTransform.mapSource(mat(w, w, MIRROR_LEFT, -90), w, w, uMid, 0f)
        val cw = CameraOverlayTransform.mapSource(mat(w, w, MIRROR_LEFT, 90), w, w, uMid, 0f)
        assertEquals(0f, ccw[0], EPS, "−90 (↺): mép TRÊN của crop phải về mép TRÁI view")
        assertEquals(w / 2f, ccw[1], EPS)
        assertEquals(w.toFloat(), cw[0], EPS, "+90 (↻): mép TRÊN của crop phải về mép PHẢI view")
        assertEquals(w / 2f, cw[1], EPS)
    }

    // ══ 3 · XOAY ±90 — khung KHÔNG vuông (chỗ dễ sai nhất: chiều của `postScale(vw/vh, vh/vw)`) ════════════════

    /**
     * 400×300 (ngang) và 300×400 (dọc), cả `+90` lẫn `−90`: vùng crop vẫn phủ ĐÚNG khung, không hở mép và không
     * tràn. Đây là bài canh chiều của hệ số bù — đảo `vw/vh` ↔ `vh/vw` là hở hai mép ngay (đã thử ĐỎ).
     */
    @Test fun `xoay 90 khung khong vuong van phu dung khung`() {
        listOf(400 to 300, 300 to 400, 1920 to 720).forEach { (vw, vh) ->
            listOf(90, -90, 270).forEach { deg ->
                assertFillsView(vw, vh, cropCorners(vw, vh, MIRROR_LEFT, deg), "${vw}×$vh $deg°")
            }
        }
    }

    /** 180° trong khung không vuông KHÔNG được bù tỉ lệ (hình quay 180 vẫn đúng cỡ) — bù là làm méo thêm. */
    @Test fun `xoay 180 khung khong vuong khong bu ti le`() {
        val m = mat(400, 300, MIRROR_LEFT, 180)
        // |MSCALE| của phần crop = 10 (ngang) và 1 (dọc); 180° chỉ đổi DẤU, không đổi độ lớn.
        assertEquals(10f, Math.abs(m[0]), 1e-3f)
        assertEquals(1f, Math.abs(m[4]), 1e-3f)
        assertFillsView(400, 300, cropCorners(400, 300, MIRROR_LEFT, 180), "400×300 180°")
    }

    // ══ 4 · Khớp với bảng luật của `CameraSignalPolicy` ════════════════════════════════════════════════════════

    /**
     * Mọi góc mà [CameraSignalPolicy.rotationDegrees] sinh ra đều là **bội của 90** — điều kiện để bước bù tỉ lệ
     * (chỉ xử lý 90/270) là đủ. Thêm một chế độ trả 45° thì bài này đỏ trước khi mép đen xuất hiện trên xe.
     */
    @Test fun `policy chi sinh boi cua 90`() {
        CameraSignalPolicy.ROTATIONS.forEach { mode ->
            listOf(true, false).forEach { left ->
                val deg = CameraSignalPolicy.rotationDegrees(mode, left)
                assertEquals(0, ((deg % 90) + 90) % 90, "mode=$mode left=$left cho $deg° — không phải bội 90")
            }
        }
    }

    /** Mỗi (chế độ × bên) đều dựng được ma trận phủ đủ khung vuông — vòng khép từ luật tới hình học. */
    @Test fun `moi che do x ben deu phu du khung`() {
        CameraSignalPolicy.ROTATIONS.forEach { mode ->
            listOf(true, false).forEach { left ->
                val deg = CameraSignalPolicy.rotationDegrees(mode, left)
                assertFillsView(360, 360, cropCorners(360, 360, MIRROR_LEFT, deg), "$mode/left=$left ($deg°)")
            }
        }
    }
}
