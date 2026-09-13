package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.simplified.CastBounds
import com.byd.clusternav.modules.clustercast.simplified.CastProfile
import com.byd.clusternav.modules.clustercast.simplified.DisplayConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ KHUNG CHIẾU: KẸP THÌ PHẢI **KẸP**, KHÔNG ĐƯỢC NỔ ═══════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-remove-legacy-screen.html` **R2(a)** — bộ chỉnh khung của màn cũ chuyển vào
 * *Cài đặt › Chiếu cụm* qua [clampBounds]. Bài này khoá đúng phần **thuần** của nó (CLAUDE.md §10: cái gì tính
 * được off-device thì phải có bài off-device; cả tệp `ClusterNavBridgeGeometry.kt` trước đó không có bài nào).
 *
 * ## Lỗi bài này sinh ra để khoá — [ĐO] soát 2026-09-13
 * Bản đầu kẹp bằng `bounds.left.coerceIn(bandMin, bandMax - MIN_SPAN)`. `coerceIn` **ném**
 * `IllegalArgumentException` khi `min > max`, nên bất kỳ dải nào hẹp hơn 80px là màn *Chiếu cụm* văng lúc mở —
 * mà dải hẹp có thật: [CastProfile.SPLIT_PERCENTS] xuống tới **10%** (cụm rộng < 800 ⇒ nửa trái < 80), còn
 * `wmSize` thì đọc từ prefs của từng đời cụm (CLAUDE.md §7) nên có thể ra số lạ.
 *
 * Hợp đồng: **mọi** đầu vào ra được một khung hợp lệ (`left ≤ right`, `top ≤ bottom`, nằm trong dải) —
 * không ngoại lệ, không ném.
 */
class CastGeometryClampTest {

    private val frame = DisplayConfig.NORMAL_DEFAULT.wmSize.split("x").map { it.toInt() }
    private val width = frame[0]
    private val height = frame[1]

    @Test
    fun `khung binh thuong giu nguyen`() {
        val b = CastBounds(100, 40, 1800, 700)
        assertEquals(b, clampBounds(b, 0, width, height))
    }

    @Test
    fun `qua bien thi bi keo ve trong dai`() {
        val out = clampBounds(CastBounds(-500, -500, 9000, 9000), 0, width, height)
        assertEquals(CastBounds(0, 0, width, height), out)
    }

    @Test
    fun `hai mep khong duoc chong len nhau`() {
        // Người dùng bấm "−" ở mép PHẢI liên tục: nó không được chui qua mép trái.
        val out = clampBounds(CastBounds(900, 300, 20, 10), 0, width, height)
        assertTrue(out.right - out.left >= 80) { "bề rộng còn ${out.right - out.left}px — ô biến mất khỏi cụm" }
        assertTrue(out.bottom - out.top >= 80) { "bề cao còn ${out.bottom - out.top}px — ô biến mất khỏi cụm" }
    }

    @Test
    fun `nua trai 10 phan tram cua mot cum hep KHONG duoc lam vang man`() {
        // Cụm 720 rộng, tỉ lệ chia 10% ⇒ dải trái 0..72, hẹp hơn cạnh nhỏ nhất 80.
        val narrow = 72
        val out = clampBounds(CastBounds(0, 0, narrow, 480), 0, narrow, 480)
        assertTrue(out.left in 0..narrow && out.right in 0..narrow) { "ra ngoài dải: $out" }
        assertTrue(out.left <= out.right) { "khung lộn ngược: $out" }
    }

    @Test
    fun `wmSize lieu linh dua chieu cao ve 0 van khong ném`() {
        val out = clampBounds(CastBounds(0, 0, 100, 100), 0, 100, 0)
        assertEquals(0, out.top)
        assertEquals(0, out.bottom)
    }

    @Test
    fun `dai rong bang 0 van tra ve mot khung hop le`() {
        val out = clampBounds(CastBounds(50, 50, 10, 10), 200, 200, 0)
        assertEquals(CastBounds(200, 0, 200, 0), out)
    }

    /** Chốt chống "bài xanh vì hằng đã đổi": dải chia thấp nhất phải vẫn là 10 (nguồn của ca hẹp ở trên). */
    @Test
    fun `ti le chia thap nhat van la 10 phan tram`() {
        assertEquals(10, CastProfile.SPLIT_PERCENTS.min(), "dải chia đổi ⇒ đọc lại ca 'dải hẹp' của bài này")
    }
}
