package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.FitRules.Forms
import com.byd.clusternav.launcher.GridFit.Form
import com.byd.clusternav.launcher.GridFit.Shape
import kotlin.math.roundToInt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Soát vòng 6 (2.87, 04/10) — quyết định thuần mới của tầng vẽ:
 *  - [FitRules.insetOf]: lề của icon tính theo DẠNG — dạng NGANG xoay lề "chỉ dọc" của con khối chính sang ngang (đúng như
 *    `FitScale.padding`/`params`). [ĐO máy ảo QA3 `th-dock-widgetfit.log` (bằng chứng phiên, ngoài repo)] `615x123 -> 3x2 cell=189x43 HORIZONTAL/1 k=0.969
 *    legible=true`, icon nút kính 26px cạnh icon gói lệnh 29px — số học dưới đây tái lập ĐÚNG cả hai số;
 *  - [FitRules.iconShared]: trần icon chung không kéo icon của loại ô khác (to hơn hẳn) xuống theo ô chật nhất;
 *  - [FitRules.settleMore] / [FitRules.primaryOnly]: phép gộp dạng PHỤ đo lười (trước chỉ có bài canh chuỗi nguồn).
 *
 * Số dp của bộ dựng ở mật độ 1,5 (máy ảo QA): ô nút kính (`ControlTileFactory.actionTile`, `TileSize.DOCK`) lề trong 4dp = 6px,
 * icon 20dp = 30px + lề dưới XS 4dp = 6px; ô gói lệnh (`macroTile`) không lề icon; ô nén `MiniCard` lề trong 8dp = 12px, icon 20dp
 * + lề dưới 4dp. Khung nội dung của ô là CHÍNH khối chính (lề trong của nó không xoay), icon là con trực tiếp (lề ngoài xoay).
 */
class FitRulesRound6Test {

    private fun plus(a: Pair<Int, Int>, b: Pair<Int, Int>) = (a.first + b.first) to (a.second + b.second)

    /** Lề (ngang, dọc) của icon: lề ngoài [margin] (l,t,r,b) của icon + lề trong [pad] của khối chính, ở dạng [rotated]. */
    private fun inset(margin: IntArray, pad: Int, rotated: Boolean) = plus(
        FitRules.insetOf(margin[0], margin[1], margin[2], margin[3], rotated),
        FitRules.insetOf(pad, pad, pad, pad, rotated = false),
    )

    private val tileIcon = intArrayOf(0, 0, 0, 6)
    private val noMargin = intArrayOf(0, 0, 0, 0)

    /** Cạnh icon 30px sau `FitScale.iconK`/`iconSides` (ô [cw]×[ch], lưới [k], lề [inset] px gốc × k làm tròn). */
    private fun side(k: Double, inset: Pair<Int, Int>, cw: Int, ch: Int): Double =
        FitRules.iconScale(k, 30, 30, cw - (inset.first * k).roundToInt(), ch - (inset.second * k).roundToInt()) * 30

    @Test
    fun `o nut kinh NGANG 189x43 va 136x43 - icon giu k x 30px, khong con tran chung`() {
        val k = 0.969
        assertEquals(12 to 18, inset(tileIcon, 6, rotated = false), "dạng dọc: lề dưới icon tính vào chỗ DỌC")
        assertEquals(18 to 12, inset(tileIcon, 6, rotated = true), "dạng ngang: lề dưới icon xoay thành lề PHẢI")
        for (cw in listOf(189, 136)) {
            assertEquals(26.0, side(k, inset(tileIcon, 6, rotated = false), cw, 43), 1e-9, "bản lỗi: 43 − 17 = 26px — đúng số QA3")
            assertEquals(k * 30, side(k, inset(tileIcon, 6, rotated = true), cw, 43), 1e-9,
                "chỗ dọc thật 43 − 12 = 31px ⇒ icon theo k (29px — khớp hộp đo dò, khung đọc được)")
            assertEquals(k * 30, side(k, inset(noMargin, 6, rotated = true), cw, 43), 1e-9, "ô gói lệnh 29px như QA3")
            // 4 nút kính + 2 gói lệnh: không icon nào bị chặn ⇒ không trần chung ⇒ mọi icon 29px (không kéo cả lưới về 26).
            val icons = List(4) { k * 30 to side(k, inset(tileIcon, 6, true), cw, 43) } + List(2) { k * 30 to side(k, inset(noMargin, 6, true), cw, 43) }
            assertNull(FitRules.iconCap(icons), "$cw×43: không còn trần chung trong khung đọc được")
            val old = List(4) { k * 30 to side(k, inset(tileIcon, 6, false), cw, 43) } + List(2) { k * 30 to side(k, inset(noMargin, 6, false), cw, 43) }
            assertEquals(26.0, FitRules.iconCap(old)!!, 1e-9, "bản lỗi: trần 26 kéo icon gói lệnh 29 → 26 (QA4: cả lưới 26px)")
        }
        assertEquals(8 to 12, FitRules.insetOf(4, 6, 4, 6, rotated = true), "lề có trái/phải ⇒ không xoay (như FitScale.padding/params)")
    }

    @Test
    fun `luoi tron nut kinh + o nen - tran chung khong keo icon nut kinh xuong`() {
        // [SUY từ mã, người soát vòng 6] 4 nút kính + w_speed + w_energy trong khung 2×1 có dock: ô 84×43 NGANG, k ≈ 0,952.
        val k = 0.952
        val mini = side(k, inset(tileIcon, 12, rotated = true), 84, 43)
        assertEquals(20.0, mini, 1e-9, "ô nén: chỗ dọc thật 43 − 23 = 20px")
        assertEquals(14.0, side(k, inset(tileIcon, 12, rotated = false), 84, 43), 1e-9, "bản lỗi: 43 − 29 = 14px")
        val tile = side(k, inset(tileIcon, 6, rotated = true), 84, 43)
        assertEquals(k * 30, tile, 1e-9)
        val cap = FitRules.iconCap(List(4) { k * 30 to tile } + List(2) { k * 30 to mini })
        assertEquals(20.0, cap!!, 1e-9)
        assertEquals(k * 30, FitRules.iconShared(tile, cap), 1e-9, "icon nút kính to hơn trần > 15 % ⇒ giữ cỡ của nó (không 20, không 14)")
        assertEquals(20.0, FitRules.iconShared(mini, cap), 1e-9)
        // Gần trần (≤ 15 %) ⇒ vẫn về trần — "đồng size" của QA3 giữ nguyên (26 vs 29: 29 ≤ 26 × 1,15).
        assertEquals(26.0, FitRules.iconShared(0.969 * 30, 26.0), 1e-9)
        assertEquals(24.0, FitRules.iconShared(24.0, 26.0), 1e-9, "nhỏ hơn trần ⇒ không bị kéo lên")
        assertEquals(29.0, FitRules.iconShared(29.0, null), 1e-9, "không trần")
    }

    private fun shape(w: Double, h: Double = 60.0, form: Form = Form.HORIZONTAL, short: Boolean = false) =
        Shape(form, w, h, 0.9, 1, short = short)

    /** 8 dạng như `FitProbe.OPTIONS`: 0..2 chính (đã gộp ở `settled`), 3..7 phụ đo lười. */
    private val had = List(8) { it < 3 }
    private val primary = listOf(true, true, true, false, false, false, false, false)

    @Test
    fun `settleMore - ban sao bang GIA TRI so cu khong tinh la giu`() {
        // Ô không nhãn: dạng phụ là bản sao số đo dạng chính (twin) — đối tượng MỚI, cùng số với lượt trước.
        val prev = Forms(List(8) { shape(150.0) }, List(8) { true })
        val got = Forms(List(8) { shape(150.0) }, List(8) { true })
        assertNull(FitRules.settleMore(prev, had, got, grew = false),
            "so bằng giá trị: twin trùng số cũ ⇒ không giữ ⇒ không Cell.keep (so đồng nhất thì mỗi lượt thưa thêm một lượt nhận)")
    }

    @Test
    fun `settleMore - luot thua nho di duoi 15 phan tram giu hop cu va co dung duoc cu`() {
        val old = shape(150.0, short = true)
        val prev = Forms(List(8) { i -> if (i == 6) old else shape(100.0) }, List(8) { i -> i != 6 })
        val got = Forms(List(8) { i -> if (i == 6) old.copy(widthPx = 142.0) else shape(100.0) }, List(8) { true })
        val kept = FitRules.settleMore(prev, had, got, grew = false)
        assertNotNull(kept, "có dạng giữ số cũ ⇒ chỗ gọi Cell.keep + giữ số đo thật")
        val m = kept!!
        assertSame(old, m.shapes[6], "100 → 99 km/h (−5 %) ở lượt thưa ⇒ giữ hộp cũ ⇒ k không 'thở'")
        assertEquals(false, m.usable[6], "giữ cả cờ dùng được cũ")
        (0 until 8).filter { it != 6 }.forEach { i -> assertSame(got.shapes[i], m.shapes[i], "dạng $i: số của lượt này"); assertTrue(m.usable[i]) }
        assertNull(FitRules.settleMore(prev, had, got, grew = true), "lượt vì chữ bị CẮT ⇒ nhận số mới")
        val bigShrink = Forms(List(8) { i -> if (i == 6) old.copy(widthPx = 120.0) else shape(100.0) }, List(8) { true })
        assertNull(FitRules.settleMore(prev, had, bigShrink, grew = false), "nhỏ đi > 15 % ⇒ nhận (giãn lại được)")
        // Dạng CHÍNH (had) không bao giờ gộp lại ở đây — đã gộp ở `settled`.
        val mainShrink = Forms(List(8) { i -> if (i == 0) shape(95.0) else shape(100.0) }, List(8) { true })
        assertNull(FitRules.settleMore(Forms(List(8) { shape(100.0) }, List(8) { true }), had, mainShrink, grew = false))
    }

    @Test
    fun `settleMore - khong co so truoc luot do do thi dung nguyen so vua do`() {
        val got = Forms(List(8) { shape(100.0) }, List(8) { true })
        assertNull(FitRules.settleMore(null, had, got, grew = false))
        val gaps = Forms(List(8) { i -> if (i == 6) null else shape(100.0) }, List(8) { true })
        assertNull(FitRules.settleMore(Forms(List(8) { shape(150.0) }, List(8) { true }), had, gaps, grew = false), "dạng chưa đo ⇒ không gộp")
    }

    @Test
    fun `primaryOnly - so do that de nhan chi giu dang chinh`() {
        val n = Forms(List(8) { shape(100.0 + it) }, List(8) { true })
        val p = FitRules.primaryOnly(n, primary)
        assertEquals(n.shapes.take(3), p.shapes.take(3))
        assertTrue(p.shapes.drop(3).all { it == null }, "dạng phụ để trống ⇒ nhận rồi đo lười lại")
        assertEquals(primary, p.usable)
    }
}
