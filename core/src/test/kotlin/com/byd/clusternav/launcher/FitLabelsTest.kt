package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.FitLabels.Shown
import com.byd.clusternav.launcher.FitLabels.Variant
import com.byd.clusternav.launcher.FitLabels.Variant.FULL
import com.byd.clusternav.launcher.FitLabels.Variant.FULL_START
import com.byd.clusternav.launcher.FitLabels.Variant.SHORT
import com.byd.clusternav.launcher.FitLabels.Variant.SHORT_START
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ J1 (2.87, QA2 04/10) — chữ TÊN của từng ô: nhãn đầy · nhãn ngắn · cắt đầu, và luật PHÂN BIỆT ([FitLabels]) ════════
 *
 * Khoá hai lỗi P2 của QA2 ([ĐO] máy ảo, `fit-2x1-dock-4lang.png`, `fit-3x1-th-ms.png` (bằng chứng phiên, ngoài repo)): widget 6 nút kính
 * (4 kính + mở/đóng hết) trong khung 2×1 / 3×1 không bố cục nào đọc được ⇒ nhãn bị `…` tới mức các nút hiện Y HỆT nhau.
 *
 * Chữ thấy được trong các ca dưới:
 *  - cột "đầy" của khung 3×1 Mã Lai và 2×1 bốn tiếng = đúng chữ trên ảnh QA2 [ĐO];
 *  - các cột còn lại (nhãn ngắn, cắt đầu) = MÔ PHỎNG [SUY] cùng phép `StaticLayout.calculateEllipsis` r47
 *    (`:1078-1124`, cắt theo bề rộng ký tự) trên bề rộng PIL của Roboto/Noto (công cụ ngoài repo `vis.py`, chữ 15px = 11.5sp × 0,87
 *    × 1,5; chỗ cho nhãn ≈ 42px trong ô 84px dạng ngang, ≈ 52px trong ô 62px dạng dọc). Trên máy, `FitNames` đọc chữ thấy
 *    được thật từ `Layout` — bài này chỉ khoá LUẬT chọn.
 */
class FitLabelsTest {

    private fun t(vararg v: Pair<Variant, Shown>): Map<Variant, Shown> = mapOf(*v)
    private fun ok(s: String) = Shown(s, cut = false)
    private fun cut(s: String) = Shown(s, cut = true)

    private fun shown(tiles: List<Map<Variant, Shown>?>, pick: List<Variant?>) =
        tiles.indices.map { i -> pick[i]?.let { tiles[i]!![it]!!.text } }

    private fun assertDistinct(texts: List<String?>) {
        val live = texts.filterNotNull()
        assertEquals(live.size, live.toSet().size, "hai ô hiện cùng một chữ: $live")
    }

    // ── luật nền ────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `cach hien - nhan ngan chi khi co ban ngan khac, cat dau chi khi mot dong`() {
        assertEquals(listOf(FULL, SHORT, FULL_START, SHORT_START), FitLabels.variants(hasShort = true, oneLine = true))
        assertEquals(listOf(FULL, FULL_START), FitLabels.variants(hasShort = false, oneLine = true))
        // StaticLayout r47 :1078-1103 — `…` ở đầu chỉ khi mMaximumVisibleLineCount == 1.
        assertEquals(listOf(FULL, SHORT), FitLabels.variants(hasShort = true, oneLine = false))
        assertEquals(listOf(FULL), FitLabels.variants(hasShort = false, oneLine = false))
    }

    @Test
    fun `chu thay duoc - doc dung vung bi cat cua Layout`() {
        // `…` cuối: getEllipsisStart = 4, count = 9 ("Kính sau trái", 13 ký tự, một dòng).
        assertEquals("Kính…", FitLabels.visible("Kính sau trái", listOf(FitLabels.Line(0, 13, 4, 9))))
        // `…` đầu: getEllipsisStart = 0, count = 7 ("Kính sa" bị thay).
        assertEquals("…u trái", FitLabels.visible("Kính sau trái", listOf(FitLabels.Line(0, 13, 0, 7))))
        // Hai dòng, dòng 2 bị `…` ("Kaca " + "blkg k…") — đúng chữ ảnh QA2 khung 3×1.
        assertEquals("Kaca blkg k…", FitLabels.visible("Kaca blkg kiri", listOf(FitLabels.Line(0, 5), FitLabels.Line(5, 14, 6, 3))))
        assertEquals("Kính lái", FitLabels.visible("Kính lái", listOf(FitLabels.Line(0, 8))), "không cắt ⇒ chữ trọn")
    }

    @Test
    fun `cach hien goc - nhan day neu tron, khong thi nhan ngan truoc khi cat`() {
        assertEquals(FULL, FitLabels.base(t(FULL to ok("Kính lái"), SHORT to ok("Kính lái"))))
        assertEquals(SHORT, FitLabels.base(t(FULL to cut("Kaca blkg k…"), SHORT to ok("Kaca BKr"))))
        // Cả hai đều cắt ⇒ nhãn ngắn (quyết định J1: đổi sang nhãn ngắn TRƯỚC khi `…`).
        assertEquals(SHORT, FitLabels.base(t(FULL to cut("Kính…"), SHORT to cut("Kính…"))))
        assertEquals(FULL, FitLabels.base(t(FULL to cut("Mở …"), FULL_START to cut("… kính"))), "không có nhãn ngắn")
        // Cắt đầu KHÔNG bao giờ là cách hiện gốc — chỉ là đường phân biệt.
        assertEquals(FULL, FitLabels.base(t(FULL to cut("全…"), FULL_START to cut("…开"))))
    }

    // ── QA2 · khung 3×1 Mã Lai (6×1 ô 62px, dạng dọc 2 dòng ⇒ không cắt đầu) ─────────────────────────────────────

    @Test
    fun `QA2 3x1 Ma Lai - Kaca blkg k x2 thanh Kaca BKr va Kaca BKn`() {
        val tiles = listOf(
            t(FULL to cut("Kaca pema…")),                                // nhãn ngắn = nhãn đầy ("Kaca pemandu")
            t(FULL to cut("Kaca penu…")),
            t(FULL to cut("Kaca blkg k…"), SHORT to ok("Kaca BKr")),
            t(FULL to cut("Kaca blkg k…"), SHORT to ok("Kaca BKn")),
            t(FULL to ok("Buka semua")),
            t(FULL to ok("Tutup semua")),
        )
        val pick = FitLabels.choose(tiles)
        assertEquals(listOf(FULL, FULL, SHORT, SHORT, FULL, FULL), pick)
        assertDistinct(shown(tiles, pick))
    }

    // ── QA2 · khung 2×1 có thanh dock (3×2 ô 84×43px, dạng ngang MỘT dòng) ───────────────────────────────────────

    @Test
    fun `QA2 2x1 tieng Viet - bon Kinh cat cuoi thanh cat dau, ca nhom cung mot kieu`() {
        val tiles = listOf(
            t(FULL to cut("Kính…"), FULL_START to cut("…h lái")),
            t(FULL to cut("Kính…"), FULL_START to cut("… phụ")),
            t(FULL to cut("Kính…"), SHORT to cut("Kính…"), FULL_START to cut("… trái"), SHORT_START to cut("…h ST")),
            t(FULL to cut("Kính…"), SHORT to cut("Kính…"), FULL_START to cut("… phải"), SHORT_START to cut("…h SP")),
            t(FULL to cut("Mở …"), FULL_START to cut("… kính")),
            t(FULL to cut("Đón…"), FULL_START to cut("… kính")),
        )
        val pick = FitLabels.choose(tiles)
        assertEquals(listOf(FULL_START, FULL_START, FULL_START, FULL_START, FULL, FULL), pick)
        assertDistinct(shown(tiles, pick))
    }

    @Test
    fun `QA2 2x1 tieng Trung - toan bo x2 thanh mo va dong, kinh giu nguyen`() {
        val tiles = listOf(
            t(FULL to cut("主驾…"), FULL_START to cut("…车窗")),
            t(FULL to cut("副驾…"), FULL_START to cut("…车窗")),
            t(FULL to cut("左后…"), FULL_START to cut("…车窗")),
            t(FULL to cut("右后…"), FULL_START to cut("…车窗")),
            t(FULL to cut("全部…"), FULL_START to cut("…打开")),
            t(FULL to cut("全部…"), FULL_START to cut("…关闭")),
        )
        val pick = FitLabels.choose(tiles)
        assertEquals(listOf(FULL, FULL, FULL, FULL, FULL_START, FULL_START), pick, "chỉ nhóm trùng đổi")
        assertDistinct(shown(tiles, pick))
    }

    @Test
    fun `QA2 2x1 tieng Thai va Ma Lai - bon kinh cat dau`() {
        val th = listOf(
            t(FULL to cut("กระ…"), FULL_START to cut("…นขับ")),
            t(FULL to cut("กระ…"), FULL_START to cut("…สาร")),
            t(FULL to cut("กระ…"), FULL_START to cut("…ซ้าย")),
            t(FULL to cut("กระ…"), FULL_START to cut("…งขวา")),
            t(FULL to cut("เปิด…"), FULL_START to cut("…หมด")),
            t(FULL to cut("ปิดก…"), FULL_START to cut("…หมด")),
        )
        val tp = FitLabels.choose(th)
        assertEquals(listOf(FULL_START, FULL_START, FULL_START, FULL_START, FULL, FULL), tp, "mở/đóng hết vẫn khác nhau ở cuối")
        assertDistinct(shown(th, tp))
        val ms = listOf(
            t(FULL to cut("Kac…"), FULL_START to cut("…ndu")),
            t(FULL to cut("Kac…"), FULL_START to cut("…ang")),
            t(FULL to cut("Kac…"), SHORT to cut("Kac…"), FULL_START to cut("…g kiri"), SHORT_START to cut("… BKr")),
            t(FULL to cut("Kac…"), SHORT to cut("Kac…"), FULL_START to cut("…nan"), SHORT_START to cut("… BKn")),
            t(FULL to cut("Buk…"), FULL_START to cut("…mua")),
            t(FULL to cut("Tutu…"), FULL_START to cut("…mua")),
        )
        val mp = FitLabels.choose(ms)
        assertEquals(listOf(FULL_START, FULL_START, FULL_START, FULL_START, FULL, FULL), mp)
        assertDistinct(shown(ms, mp))
    }

    @Test
    fun `tieng Anh - hai kinh sau chi khac nhau o nhan ngan cat dau`() {
        val tiles = listOf(
            t(FULL to cut("Driv…"), FULL_START to cut("…dow")),
            t(FULL to cut("Pas…"), FULL_START to cut("…dow")),
            t(FULL to cut("Rear…"), SHORT to cut("Win …"), FULL_START to cut("…dow"), SHORT_START to cut("…n RL")),
            t(FULL to cut("Rear…"), SHORT to cut("Win …"), FULL_START to cut("…dow"), SHORT_START to cut("…n RR")),
            t(FULL to cut("Ope…"), FULL_START to cut("…n all")),
            t(FULL to cut("Clos…"), FULL_START to cut("…e all")),
        )
        val pick = FitLabels.choose(tiles)
        assertEquals(SHORT_START, pick[2]); assertEquals(SHORT_START, pick[3])
        assertEquals(FULL, pick[0], "ô không trùng giữ cách hiện gốc")
        assertDistinct(shown(tiles, pick))
    }

    // ── QA2 · khung 2×1 KHÔNG dock (ô 84×56px, dạng ngang 2 dòng dự phòng ⇒ không cắt đầu) ───────────────────────

    @Test
    fun `QA2 2x1 khong dock tieng Viet - Kinh sau x2 thanh Kinh ST va Kinh SP`() {
        val tiles = listOf(
            t(FULL to ok("Kính lái")),
            t(FULL to ok("Kính phụ")),
            t(FULL to cut("Kính sau …"), SHORT to ok("Kính ST")),
            t(FULL to cut("Kính sau …"), SHORT to ok("Kính SP")),
            t(FULL to cut("Mở hết …")),
            t(FULL to cut("Đóng hết …")),
        )
        val pick = FitLabels.choose(tiles)
        assertEquals(listOf(FULL, FULL, SHORT, SHORT, FULL, FULL), pick)
        assertDistinct(shown(tiles, pick))
    }

    // ── bất biến ────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `luoi doc duoc, nhan khac nhau - khong doi gi`() {
        val tiles = listOf(t(FULL to ok("Kính lái"), SHORT to ok("Kính lái")), t(FULL to ok("Kính phụ")), null)
        assertEquals(listOf(FULL, FULL, null), FitLabels.choose(tiles), "ô không có chữ tên (null) bỏ qua")
    }

    @Test
    fun `hai nut cung nhan day - nhan ngan khac nhau thi dung nhan ngan`() {
        // Hai mã khác nhau mà bản dịch đầy trùng chữ (vd MS "Tutup semua" cho 'Đóng cả cụm' và 'Đóng hết kính').
        val tiles = listOf(t(FULL to ok("Tutup semua"), SHORT to ok("Tutup kaca")), t(FULL to ok("Tutup semua"), SHORT to ok("Tutup gugus")))
        assertEquals(listOf(SHORT, SHORT), FitLabels.choose(tiles))
    }

    @Test
    fun `khong cach nao phan biet duoc - giu goc, khong lap mai`() {
        val same = t(FULL to cut("Kính…"), FULL_START to cut("…kính"))
        val pick = FitLabels.choose(listOf(same, same, same))
        assertEquals(listOf(FULL, FULL, FULL), pick)
        assertEquals(pick, FitLabels.choose(listOf(same, same, same)), "xác định")
    }

    @Test
    fun `cach gan nhat - o thieu cach hien thi lui ve cach no co`() {
        val full = t(FULL to cut("A…"))
        assertEquals(FULL, FitLabels.nearest(full, SHORT_START))
        val start = t(FULL to cut("A…"), FULL_START to cut("…a"))
        assertEquals(FULL_START, FitLabels.nearest(start, SHORT_START), "bỏ nhãn ngắn trước, giữ cắt đầu")
        val short = t(FULL to cut("A…"), SHORT to ok("A"))
        assertEquals(SHORT, FitLabels.nearest(short, SHORT_START), "không cắt đầu được ⇒ nhãn ngắn cắt cuối")
        assertTrue(Variant.values().all { FitLabels.nearest(full, it) == FULL })
    }
}
