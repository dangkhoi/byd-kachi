package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ OQ7 · 2.76 · R11 (2) — KHỐI 4 THẺ LỐP có tâm = TÂM ẢNH XE, ở MỌI cỡ ô, và không thẻ nào đè ảnh ═══════════
 *
 * [ĐO ảnh máy ảo 2026-09-27 lượt 1, `visual-pass-2026-09-27.md` §5] ô lốp 1836×410 px: bốn thẻ tâm y = 259,0 /
 * 467,5 ⇒ tâm khối = **363,25**, còn ảnh xe (khung `CAR_TOP..CAR_BOTTOM` = 0,04..0,98, letterbox) có tâm
 * `0,51 × 410 = 209,1` + 143 (mép ô) = **352,1** ⇒ lệch **11 px** xuống dưới (= 0,03 × Hc với Hc ≈ 386 — bài
 * `CarLayoutTest` ghim con số 0,03). Owner 2026-09-25 (C): *"xe nhô lên so với khối thẻ"*.
 *
 * Bài này dựng lại ĐÚNG hình học `TyreBoardView.onDraw` (hằng chép ở đây như `CellTextLayoutTest`) trên lưới cỡ ô
 * THẬT của mọi preset [WorkspaceLayout] × 3 tỉ lệ ảnh, và khoá bốn điều:
 *  1. tâm khối 4 thẻ = tâm ảnh xe ± 1 px;
 *  2. thẻ nằm TRỌN trong ô (không tràn mép trên/dưới sau khi dịch);
 *  3. thẻ KHÔNG giao khung ảnh (UX2 giữ nguyên — dịch dọc không đổi mép ngang);
 *  4. cổng phủ định: công thức CŨ (thẻ tại neo bánh) lệch đúng `0,03 × Hc` ≥ 1 px ở mọi ô thật — bài không kiểm
 *     một lỗi tưởng tượng.
 */
class TyreCardBlockTest {

    // Hằng của tầng vẽ (TyreBoardView) — chép để dựng đúng hình học thật.
    private val carLeftFrac = 0.30f
    private val carRightFrac = 0.70f
    private val carTopFrac = 0.04f
    private val carBottomFrac = 0.98f
    private val padFrac = 0.02f
    private val gapFrac = 0.03f
    private val aspects = listOf(0.3f, 678f / 1397f, 0.8f)

    private data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val centerY: Float get() = (top + bottom) / 2f
        fun intersects(o: Box) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    }

    private fun frames(): List<Pair<Float, Float>> {
        val out = mutableListOf<Pair<Float, Float>>()
        for (workspaceH in listOf(520, 560, 600)) for (preset in LayoutPreset.values()) {
            WorkspaceLayout.slots(preset, 1920, workspaceH, gap = 9).forEach { out += it.width.toFloat() to it.height.toFloat() }
        }
        // Ô lốp THẬT của ảnh máy ảo (1836×410) + vài tỉ lệ khác để bao cả hai nhánh letterbox.
        out += 1836f to 410f
        listOf(0.5f, 1.14f, 2.0f, 3.0f).forEach { r -> out += 560f * r to 560f }
        return out.distinct()
    }

    /** Khung ảnh xe thật — cùng phép toán `CarImageLayer.fitRect` trong `carDstIn`. */
    private fun content(w: Float, h: Float, aspect: Float): Box {
        val dl = w * carLeftFrac; val dr = w * carRightFrac
        val dt = h * carTopFrac; val db = h * carBottomFrac
        val scale = minOf((dr - dl) / aspect, (db - dt))
        val cw = aspect * scale; val ch = scale
        val cx = (dl + dr) / 2f; val cy = (dt + db) / 2f
        return Box(cx - cw / 2f, cy - ch / 2f, cx + cw / 2f, cy + ch / 2f)
    }

    private fun cellH(w: Float, h: Float, c: Box): Float {
        val m = minOf(w, h)
        val wheelSpanY = (CarLayout.wheel(TyreCorner.REAR_LEFT).y - CarLayout.wheel(TyreCorner.FRONT_LEFT).y) * c.height
        return minOf(h * 0.24f, wheelSpanY - m * 0.035f).coerceAtLeast(m * 0.10f)
    }

    /** Bốn thẻ theo đúng `onDraw`: mép ngang `cardSpanX`, tâm dọc [CarLayout.tyreCardCenterY] (mới) hoặc neo (cũ). */
    private fun cards(w: Float, h: Float, c: Box, shifted: Boolean): List<Box> {
        val m = minOf(w, h)
        val gap = m * gapFrac; val pad = w * padFrac
        val ch = cellH(w, h, c)
        return TyreCorner.values().map { corner ->
            val a = CarLayout.wheel(corner)
            val wheelX = c.left + a.x * c.width
            val span = CellTextLayout.cardSpanX(a.x < 0.5f, w, pad, c.left, c.right, wheelX, gap)
            val cy = if (shifted) CarLayout.tyreCardCenterY(corner, c.top, c.height) else c.top + a.y * c.height
            Box(span.start, cy - ch / 2f, span.end, cy + ch / 2f)
        }
    }

    @Test
    fun `tam khoi 4 the = tam anh xe, sai so 1 px, moi co o va moi ti le anh`() {
        var checked = 0
        for ((w, h) in frames()) for (aspect in aspects) {
            val c = content(w, h, aspect)
            val cs = cards(w, h, c, shifted = true)
            val blockCenter = (cs.minOf { it.top } + cs.maxOf { it.bottom }) / 2f
            assertEquals(c.centerY, blockCenter, 1f, "ô ${w.toInt()}×${h.toInt()} tỉ lệ $aspect: khối thẻ lệch tâm ảnh")
            // Hai hàng thẻ đối xứng qua tâm ảnh (trên cách tâm bằng dưới cách tâm) — thứ tự TyreCorner: FL FR RL RR.
            assertEquals(c.centerY - cs[0].centerY, cs[2].centerY - c.centerY, 0.01f)
            assertEquals(cs[0].centerY, cs[1].centerY, 0f); assertEquals(cs[2].centerY, cs[3].centerY, 0f)
            checked++
        }
        assertTrue(checked >= 40, "lưới quá mỏng ($checked ca)")
    }

    @Test
    fun `the nam tron trong o sau khi dich - khong tran mep tren duoi`() {
        for ((w, h) in frames()) for (aspect in aspects) {
            val c = content(w, h, aspect)
            cards(w, h, c, shifted = true).forEach { b ->
                assertTrue(b.top >= 0f - 0.01f, "ô ${w.toInt()}×${h.toInt()} tỉ lệ $aspect: thẻ tràn mép TRÊN ${b.top} px")
                assertTrue(b.bottom <= h + 0.01f, "ô ${w.toInt()}×${h.toInt()} tỉ lệ $aspect: thẻ tràn mép DƯỚI ${b.bottom - h} px")
            }
        }
    }

    /** UX2 giữ nguyên: dịch DỌC không đổi mép NGANG, và thẻ (đã kẹp theo khung ảnh) không giao khung ảnh. */
    @Test
    fun `khong the nao giao khung anh xe - UX2 khong hoi quy`() {
        for ((w, h) in frames()) for (aspect in aspects) {
            val c = content(w, h, aspect)
            val now = cards(w, h, c, shifted = true)
            val before = cards(w, h, c, shifted = false)
            now.forEachIndexed { i, b ->
                if (b.width <= 0f) return@forEachIndexed   // ô quá hẹp: thẻ bị kẹp hết (đã có bài riêng)
                assertTrue(!b.intersects(c), "ô ${w.toInt()}×${h.toInt()} tỉ lệ $aspect: thẻ $i đè khung ảnh")
                assertEquals(before[i].left, b.left, 0f, "mép ngang không đổi theo dịch dọc")
                assertEquals(before[i].right, b.right, 0f)
            }
        }
    }

    /** Cổng phủ định: công thức CŨ lệch đúng `0,03 × Hc` xuống dưới — ≥ 1 px ở MỌI ô thật, ≈ 11 px ở ô lốp máy ảo. */
    @Test
    fun `cong thuc CU lech 0,03 x Hc xuong duoi - o lop may ao lech 11 px`() {
        for ((w, h) in frames()) for (aspect in aspects) {
            val c = content(w, h, aspect)
            val old = cards(w, h, c, shifted = false)
            val oldCenter = (old.minOf { it.top } + old.maxOf { it.bottom }) / 2f
            assertEquals(0.03f * c.height, oldCenter - c.centerY, 0.01f, "ô ${w.toInt()}×${h.toInt()}: lệch cũ phải = 0,03·Hc")
            assertTrue(oldCenter - c.centerY >= 1f, "ô ${w.toInt()}×${h.toInt()} tỉ lệ $aspect: lệch cũ < 1 px — lỗi tưởng tượng?")
        }
        val c = content(1836f, 410f, 678f / 1397f)
        val old = cards(1836f, 410f, c, shifted = false)
        val oldCenter = (old.minOf { it.top } + old.maxOf { it.bottom }) / 2f
        assertEquals(11.6f, oldCenter - c.centerY, 0.6f, "ô lốp máy ảo: ảnh đo 11 px (tâm thẻ 363,25 vs tâm ảnh 352,1)")
    }
}
