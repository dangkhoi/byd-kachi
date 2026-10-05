package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ KHUNG CHIẾU: KẸP THÌ PHẢI **KẸP**, KHÔNG ĐƯỢC NỔ ═══════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-remove-legacy-screen.html` **R2(a)** — bộ chỉnh khung của màn cũ chuyển vào
 * *Cài đặt › Chiếu cụm* qua [CastGeometryGuard.clampBounds]. Bài này khoá đúng phần **thuần** của nó (CLAUDE.md §10:
 * cái gì tính được off-device thì phải có bài off-device).
 *
 * V-CLUSTER (2026-09-30): hàm DỜI từ `ClusterNavBridgeGeometry.kt` (`:app`) sang `:core` để tầng UI và lượt áp
 * hình học dùng CHUNG một phép kẹp (spec §11.4.4); bài dời theo, thân các ca giữ nguyên.
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
        assertEquals(b, CastGeometryGuard.clampBounds(b, 0, width, height))
    }

    @Test
    fun `qua bien thi bi keo ve trong dai`() {
        val out = CastGeometryGuard.clampBounds(CastBounds(-500, -500, 9000, 9000), 0, width, height)
        assertEquals(CastBounds(0, 0, width, height), out)
    }

    @Test
    fun `hai mep khong duoc chong len nhau`() {
        // Người dùng bấm "−" ở mép PHẢI liên tục: nó không được chui qua mép trái.
        val out = CastGeometryGuard.clampBounds(CastBounds(900, 300, 20, 10), 0, width, height)
        assertTrue(out.right - out.left >= 80) { "bề rộng còn ${out.right - out.left}px — ô biến mất khỏi cụm" }
        assertTrue(out.bottom - out.top >= 80) { "bề cao còn ${out.bottom - out.top}px — ô biến mất khỏi cụm" }
    }

    @Test
    fun `nua trai 10 phan tram cua mot cum hep KHONG duoc lam vang man`() {
        // Cụm 720 rộng, tỉ lệ chia 10% ⇒ dải trái 0..72, hẹp hơn cạnh nhỏ nhất 80.
        val narrow = 72
        val out = CastGeometryGuard.clampBounds(CastBounds(0, 0, narrow, 480), 0, narrow, 480)
        assertTrue(out.left in 0..narrow && out.right in 0..narrow) { "ra ngoài dải: $out" }
        assertTrue(out.left <= out.right) { "khung lộn ngược: $out" }
    }

    @Test
    fun `wmSize lieu linh dua chieu cao ve 0 van khong ném`() {
        val out = CastGeometryGuard.clampBounds(CastBounds(0, 0, 100, 100), 0, 100, 0)
        assertEquals(0, out.top)
        assertEquals(0, out.bottom)
    }

    @Test
    fun `dai rong bang 0 van tra ve mot khung hop le`() {
        val out = CastGeometryGuard.clampBounds(CastBounds(50, 50, 10, 10), 200, 200, 0)
        assertEquals(CastBounds(200, 0, 200, 0), out)
    }

    // ── Review 2.89 Pass 3 · cluster-r2-1 — khung gốc của bộ chỉnh theo KIỂU KHUNG CỦA PHIÊN ─────────────────────────────

    /** Phép kẹp `:app` dùng: dải X/Y của [ClusterRectLayout.editFrame] (cùng thân `ClusterNavBridgeGeometry.clampToBand`). */
    private fun clampIn(style: CastStyle, side: ClusterSlotSide?, pct: Int, b: CastBounds): CastBounds {
        val f = ClusterRectLayout.editFrame(style, side, pct, width, height)
        return CastGeometryGuard.clampBounds(b, f.left, f.right, f.top, f.bottom)
    }

    /**
     * Seal Chữ nhật, chia 50 %: nửa trái ghim (50,128)-(667,555). Bấm + mép phải 5 lần (5 × [GEOMETRY_STEP 20]) KHÔNG được vượt
     * 667 (dải cũ 0..960 cho tới 767 ⇒ hai app chồng 100 px [ĐO mã]); mép trên/dưới không ra khỏi vùng trống. Thử ĐỎ: trả
     * `editFrame` nhánh RECT về dải cả cụm.
     */
    @Test
    fun `Pass 3 - Chu nhat chia 50 - cong mep phai nua trai khong qua 667, Y trong vung trong`() {
        var b = ClusterRectLayout.slotFrame(ClusterSlotSide.LEFT, 50)
        assertEquals(CastBounds(50, 128, 667, 555), b)
        repeat(5) { b = clampIn(CastStyle.RECT, ClusterSlotSide.LEFT, 50, b.copy(right = b.right + 20)) }
        assertEquals(667, b.right, "nửa trái không lấn nửa phải: $b")
        val wild = clampIn(CastStyle.RECT, ClusterSlotSide.RIGHT, 50, CastBounds(0, 0, 1920, 720))
        assertEquals(CastBounds(667, 128, 1285, 555), wild, "nửa phải kẹp trong nửa vùng trống — không dưới nền ADAS")
        assertTrue(!ClusterRectLayout.intersects(wild, ClusterRectLayout.ADAS_PANEL))
    }

    /** "Đặt lại" ở Chữ nhật = đúng khung phiên ghim (slotFrame / FREE_AREA) — không bao giờ `0..W × 0..H` dưới nền ADAS. */
    @Test
    fun `Pass 3 - Dat lai o Chu nhat ve khung phien ghim`() {
        assertEquals(ClusterRectLayout.FREE_AREA, ClusterRectLayout.editFrame(CastStyle.RECT, null, 50, width, height))
        for (pct in CastProfile.SPLIT_PERCENTS) for (side in ClusterSlotSide.values()) {
            assertEquals(ClusterRectLayout.slotFrame(side, pct), ClusterRectLayout.editFrame(CastStyle.RECT, side, pct, width, height))
        }
    }

    /** Bo tròn = ĐÚNG số cũ của `ClusterNavBridgeGeometry` (dải theo tỉ lệ thô, `0..H`; kẹp 4 = kẹp 5 tham số) — CLAUDE.md §6. */
    @Test
    fun `Pass 3 - Bo tron giu nguyen tung so`() {
        assertEquals(CastBounds(0, 0, 1920, 720), ClusterRectLayout.editFrame(CastStyle.CURVED, null, 30, width, height))
        assertEquals(CastBounds(0, 0, 960, 720), ClusterRectLayout.editFrame(CastStyle.CURVED, ClusterSlotSide.LEFT, 50, width, height))
        assertEquals(CastBounds(576, 0, 1920, 720), ClusterRectLayout.editFrame(CastStyle.CURVED, ClusterSlotSide.RIGHT, 30, width, height))
        assertEquals(CastBounds(0, 0, 1, 0), ClusterRectLayout.editFrame(CastStyle.CURVED, ClusterSlotSide.LEFT, 0, 1, 0), "cụm 1 px: kẹp như cũ")
        val b = CastBounds(-5, -9, 2000, 900)
        assertEquals(CastGeometryGuard.clampBounds(b, 0, 960, 720), clampIn(CastStyle.CURVED, ClusterSlotSide.LEFT, 50, b))
        assertEquals(CastBounds(0, 0, 960, 720), clampIn(CastStyle.CURVED, ClusterSlotSide.LEFT, 50, b))
    }

    /** Chốt chống "bài xanh vì hằng đã đổi": dải chia thấp nhất phải vẫn là 10 (nguồn của ca hẹp ở trên). */
    @Test
    fun `ti le chia thap nhat van la 10 phan tram`() {
        assertEquals(10, CastProfile.SPLIT_PERCENTS.min(), "dải chia đổi ⇒ đọc lại ca 'dải hẹp' của bài này")
    }
}
