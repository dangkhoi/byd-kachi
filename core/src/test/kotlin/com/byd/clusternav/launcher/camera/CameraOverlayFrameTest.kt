package com.byd.clusternav.launcher.camera

import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ CAM-ROT-2 · CỠ CỬA SỔ overlay = ĐÚNG TỈ LỆ ảnh sau xoay — chứng minh BẰNG SỐ, off-car ═══════════════════
 *
 * Owner 2026-09-26: *"không muốn có viền đen, nên làm overlay cho nó đúng với tỷ lệ camera, không fix bừa"*.
 * Hai tính chất phải chứng minh, và chúng là HAI chuyện khác nhau:
 *  1. **Cỡ cửa sổ** đúng tỉ lệ vùng crop sau xoay và không vượt vùng cho phép ([CameraOverlayFrame.fit]);
 *  2. **Không méo**: cùng ma trận cũ ([CameraOverlayTransform]) trên khung MỚI phải là phép **đẳng hướng** — một ô
 *     vuông của ảnh nguồn phải ra một ô vuông trên màn. Đây là điều mà bài của 2.67 KHÔNG kiểm được (khung vuông
 *     thì nó vốn dĩ méo, và đó là thứ owner đang phàn nàn).
 */
class CameraOverlayFrameTest {

    private companion object {
        /** Crop gương TRÁI thật của `CamView.MIRROR_LEFT` (RE kinex pano) — dải DỌC 10 % bề ngang. */
        val MIRROR_LEFT = floatArrayOf(0.25f, 0f, 0.35f, 1f)

        /** Cỡ ảnh 4-in-1 [ĐO RE kinex `Y0/C0094o.java:318,342`]. */
        const val PANO_W = 5120
        const val PANO_H = 960
    }

    // ══ (1) CỠ CỬA SỔ — bảng số của đề bài ═════════════════════════════════════════════════════════════════

    /**
     * Ảnh 1280×720, crop TOÀN khung, vùng cho phép 800×600 — bốn góc bội của 90.
     *
     * 0/180: tỉ lệ 16:9 rộng hơn vùng (4:3) ⇒ bề rộng quyết định ⇒ 800×450.
     * 90/270: tỉ lệ 9:16 ⇒ bề CAO quyết định ⇒ 600 cao, rộng `600 × 720/1280 = 337,5 → 338`.
     */
    @Test fun `bang 1280x720 trong vung 800x600 theo bon goc`() {
        fun f(deg: Int) = CameraOverlayFrame.fit(1280, 720, null, deg, 800, 600)
        listOf(0, 180, 360, -180).forEach { deg ->
            assertEquals(800, f(deg).w, "deg=$deg bề rộng")
            assertEquals(450, f(deg).h, "deg=$deg bề cao")
            assertTrue(f(deg).streamKnown)
        }
        listOf(90, 270, -90, -270).forEach { deg ->
            assertEquals(338, f(deg).w, "deg=$deg bề rộng (600 × 720/1280 = 337,5)")
            assertEquals(600, f(deg).h, "deg=$deg bề cao")
        }
    }

    /**
     * Ca THẬT trên xe: dải gương 512×960 px (10 % của 5120×960) trong vùng vuông 360×360 (50 % chiều cao màn 720).
     *
     * Xoay ±90 ⇒ ảnh hiện ra 960×512 = **1,875:1 NGANG** ⇒ khung **360×192**. Backlog CAM-ROT-2 viết "khung dọc" —
     * hình học nói NGANG, và RE kinex cũng xuất 640×480 ngang cho blind-spot đã xoay (`C0094o.java:343-344`).
     * Không xoay ⇒ dải dọc 512:960 ⇒ khung **192×360**.
     */
    @Test fun `guong trai 512x960 trong vung vuong 360`() {
        val rotated = CameraOverlayFrame.fit(PANO_W, PANO_H, MIRROR_LEFT, -90, 360, 360)
        assertEquals(360, rotated.w, "xoay ±90 ⇒ NGANG, bề rộng chạm trần vùng")
        assertEquals(192, rotated.h, "360 × 512/960 = 192")
        val upright = CameraOverlayFrame.fit(PANO_W, PANO_H, MIRROR_LEFT, 0, 360, 360)
        assertEquals(192, upright.w, "không xoay ⇒ dải DỌC: 360 × 512/960 = 192")
        assertEquals(360, upright.h)
        // 180° không đổi vai hai trục ⇒ y như 0°.
        assertEquals(upright.w to upright.h, CameraOverlayFrame.fit(PANO_W, PANO_H, MIRROR_LEFT, 180, 360, 360).let { it.w to it.h })
    }

    /** Không bao giờ vượt vùng cho phép, không bao giờ ra 0 (cửa sổ 0 px = overlay vô hình mà không ai báo lỗi). */
    @Test fun `luon nam trong vung va khong bao gio 0`() {
        val cases = listOf(
            Triple(5120, 960, 0), Triple(5120, 960, 90), Triple(1, 4000, 90), Triple(4000, 1, 270),
        )
        cases.forEach { (w, h, deg) ->
            val f = CameraOverlayFrame.fit(w, h, null, deg, 360, 360)
            assertTrue(f.w in 1..360 && f.h in 1..360, "nguồn ${w}x$h deg=$deg ⇒ ${f.w}x${f.h} phải nằm trong 1..360")
        }
    }

    /** Chưa biết cỡ nguồn (chưa đo + không gợi ý) ⇒ **giữ nguyên vùng vuông của 2.72**, và nói thẳng là chưa biết. */
    @Test fun `chua biet co nguon thi giu nguyen vung vuong`() {
        listOf(0 to 0, 0 to 960, 5120 to 0, -1 to -1).forEach { (w, h) ->
            val f = CameraOverlayFrame.fit(w, h, MIRROR_LEFT, -90, 360, 360)
            assertEquals(360, f.w, "nguồn ${w}x$h ⇒ không đoán tỉ lệ")
            assertEquals(360, f.h)
            assertFalse(f.streamKnown, "nguồn ${w}x$h phải được báo là CHƯA BIẾT")
        }
        // Vùng suy biến: trả nguyên vùng, không ném (chỗ gọi đọc displayMetrics, off-car có thể ra 0).
        assertEquals(0, CameraOverlayFrame.fit(PANO_W, PANO_H, null, 0, 0, 360).w)
        assertFalse(CameraOverlayFrame.fit(PANO_W, PANO_H, null, 0, 360, 0).streamKnown)
    }

    /** Crop suy biến (dải 0 hoặc thiếu phần tử) ⇒ dùng ngưỡng chung [CameraOverlayTransform.MIN_SPAN], không chia 0. */
    @Test fun `crop suy bien khong lam no`() {
        val zero = CameraOverlayFrame.fit(PANO_W, PANO_H, floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f), 0, 360, 360)
        assertTrue(zero.w in 1..360 && zero.h in 1..360, "crop dải 0 ⇒ ${zero.w}x${zero.h}")
        val short = CameraOverlayFrame.fit(PANO_W, PANO_H, floatArrayOf(0f, 0f), 0, 360, 360)
        assertEquals(360, short.w, "crop thiếu phần tử ⇒ coi như TOÀN khung: 5120:960 rộng hơn vùng ⇒ chạm trần rộng")
        assertEquals(68, short.h, "360 × 960/5120 = 67,5 → 68")
    }

    // ══ (2) KHÔNG MÉO — ma trận cũ + khung mới = phép ĐẲNG HƯỚNG ═══════════════════════════════════════════

    /**
     * Ô vuông trong ảnh nguồn ⇒ ô vuông trên màn, cho cả 4 góc, ở đúng khung mà [CameraOverlayFrame.fit] trả.
     *
     * Cách đo: lấy hai đoạn nguồn có **cùng độ dài px** (một theo x, một theo y) rồi so độ dài ảnh của chúng sau
     * ma trận. Lệch > 1 % ⇒ méo. Đây chính là bài mà khung VUÔNG của 2.72 sẽ ĐỎ (lệch 3,5×) — xem
     * `khung vuong 2_72 thi meo` ngay dưới, bài đó ghim lại vì sao cần bản này.
     */
    @Test fun `khong meo khi khung dung ti le`() {
        listOf(0, 90, 180, 270, -90).forEach { deg ->
            val f = CameraOverlayFrame.fit(PANO_W, PANO_H, MIRROR_LEFT, deg, 360, 360)
            assertTrue(f.streamKnown)
            val r = anisotropy(f.w, f.h, MIRROR_LEFT, deg)
            assertTrue(abs(r - 1f) < 0.01f, "deg=$deg khung ${f.w}x${f.h}: tỉ số giãn x/y = $r (phải ≈ 1)")
        }
    }

    /** Bài ĐỐI CHỨNG: cùng ảnh, cùng ma trận, nhưng khung VUÔNG 360×360 (2.35–2.72) ⇒ giãn ~1,875× — đúng thứ owner thấy. */
    @Test fun `khung vuong 2_72 thi meo`() {
        val r = anisotropy(360, 360, MIRROR_LEFT, 0)
        assertTrue(abs(r - 1.875f) < 0.02f, "khung vuông phải giãn ~1,875× (đo được $r) — lý do CAM-ROT-2 tồn tại")
    }

    /**
     * Tỉ số *giãn theo trục x nguồn* / *giãn theo trục y nguồn* sau [CameraOverlayTransform.matrix] trên khung
     * [vw]×[vh]. `1` = đẳng hướng (không méo).
     *
     * Dùng [CameraOverlayTransform.mapSource] (đúng phép mà `TextureView` làm: căng `(u,v)` → px view rồi nhân ma
     * trận) nên bài này đo **đường thật**, không đo lại công thức bằng một bản sao.
     */
    private fun anisotropy(vw: Int, vh: Int, crop: FloatArray, deg: Int): Float {
        val m = CameraOverlayTransform.matrix(vw, vh, crop, deg)
        val u0 = (crop[0] + crop[2]) / 2f
        val v0 = (crop[1] + crop[3]) / 2f
        val stepPx = 32f
        val du = stepPx / PANO_W
        val dv = stepPx / PANO_H
        val c = CameraOverlayTransform.mapSource(m, vw, vh, u0, v0)
        val x = CameraOverlayTransform.mapSource(m, vw, vh, u0 + du, v0)
        val y = CameraOverlayTransform.mapSource(m, vw, vh, u0, v0 + dv)
        val lenX = Math.hypot((x[0] - c[0]).toDouble(), (x[1] - c[1]).toDouble()).toFloat()
        val lenY = Math.hypot((y[0] - c[0]).toDouble(), (y[1] - c[1]).toDouble()).toFloat()
        return lenX / lenY
    }

    // ══ (3) ĐƯỜNG `SurfaceView` — crop bằng cỡ + lề âm (không có setTransform) ══════════════════════════════

    /**
     * Khung 192×360 với crop gương trái `x[0.25..0.35]` ⇒ lớp video **1920×360** đặt ở `x = −480`: đúng dải
     * `0.25..0.35` của ảnh lọt vào cửa sổ `0..192`.
     */
    @Test fun `stretch cat dung dai guong bang le am`() {
        val s = CameraOverlayFrame.stretch(192, 360, MIRROR_LEFT)
        assertEquals(1920, s.w, "192 / 0.10")
        assertEquals(360, s.h, "dải y là toàn khung ⇒ không phóng")
        assertEquals(-480, s.x, "0.25 × 1920")
        assertEquals(0, s.y)
        // Mép trái/phải của dải gương phải trùng mép cửa sổ: -480 + 0.25·1920 = 0 và -480 + 0.35·1920 = 192.
        assertEquals(0, s.x + (0.25f * s.w).toInt())
        assertEquals(192, s.x + (0.35f * s.w).toInt())
    }

    /** Không crop ⇒ lớp video lấp đúng cửa sổ; khung suy biến ⇒ trả lại chính nó, không ném. */
    @Test fun `stretch khong crop va khung suy bien`() {
        val full = CameraOverlayFrame.stretch(200, 100, null)
        assertEquals(200, full.w); assertEquals(100, full.h); assertEquals(0, full.x); assertEquals(0, full.y)
        assertEquals(0, CameraOverlayFrame.stretch(0, 100, MIRROR_LEFT).w)
    }

    /** Bội LẺ của 90 (kể cả âm / > 360) mới đổi vai hai trục — cùng quy ước với [CameraOverlayTransform]. */
    @Test fun `quarterTurn dung cho goc am va vuot 360`() {
        listOf(90, 270, -90, -270, 450).forEach { assertTrue(CameraOverlayFrame.quarterTurn(it), "deg=$it") }
        listOf(0, 180, -180, 360, 540).forEach { assertFalse(CameraOverlayFrame.quarterTurn(it), "deg=$it") }
    }
}
