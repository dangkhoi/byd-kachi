package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ QA 04/10 (P3) — nhãn tiếng Mã Lai trên ô THANH NÚT bị cắt `…` ═════════════════════════════════════════════════
 *
 * [ĐO] máy ảo (`l1/dock-compare.png`): ô "Pengudaraan tempat duduk pemandu" hiện `Pengudaraan tempat dudu…`. Ô thanh nút
 * KHÔNG nằm trong phép khớp lưới (L5) — nó vẽ nhãn ĐẦY (`TileSize.DOCK`, `narrow = false` ⇒ `displayLabel`) trong
 * hai dòng cố định (`reserveTwoLines`, `maxLines = 2` + `…`). Nên luật ở đây là luật DỮ LIỆU: nhãn mọi nút/gói lệnh
 * (nút nào cũng đặt được lên thanh) phải xuống dòng được thành ≤ 2 dòng, mỗi dòng ≤ [LangCoverageFixtures.SHORT_CAP]
 * ký tự hiển thị (trần nhãn ngắn th/ms) — CÙNG ngân sách mà mọi nhãn VI/EN đang ship đều đạt (bài này khoá cả hai).
 *
 * Chỗ xuống dòng: khoảng trắng và SAU gạch nối (`kanak-|kanak`, ICU UAX #14 LB21 — minikin bẻ dòng theo ICU).
 * Đếm ký tự là [SUY] thay cho bề rộng px (Roboto 10sp ≈ 6,5–7,5 px/ký tự ⇒ 14 ký tự ≈ 94–106px, ô thanh nút đo ở QA
 * ≈ 94px): bề rộng thật chỉ đo được trên máy — QA soi lại `dock-compare` sau bản này.
 */
class MsDockLabelFitTest {

    /** [s] tách được thành ≤ 2 dòng, mỗi dòng ≤ [cap] ký tự hiển thị, tại khoảng trắng hoặc sau `-`. */
    private fun twoLines(s: String, cap: Int): Boolean {
        if (I18nPairs.displayLength(s) <= cap) return true
        val breaks = s.indices.filter { s[it] == ' ' || (s[it] == '-' && it + 1 < s.length) }
        return breaks.any { i ->
            val first = if (s[i] == ' ') s.substring(0, i) else s.substring(0, i + 1)
            val rest = s.substring(i + 1)
            I18nPairs.displayLength(first.trimEnd()) <= cap && I18nPairs.displayLength(rest.trim()) <= cap
        }
    }

    private fun dockLabels(lang: Lang): List<Pair<String, String>> = I18nPairs.inLang(lang) {
        ControlRegistry.ALL.map { it.id to it.displayLabel } + ActionMacros.ALL.map { it.id to it.displayLabel }
    }

    @Test
    fun `nhan o thanh nut tieng Ma Lai vua hai dong trong tran nhan ngan`() {
        val cap = LangCoverageFixtures.SHORT_CAP
        val labels = dockLabels(Lang.MS)
        assertTrue(labels.size >= 30, "phép duyệt registry hụt: ${labels.size}")
        val bad = labels.filterNot { twoLines(it.second, cap) }
        assertEquals(emptyList<Pair<String, String>>(), bad, "nhãn ms dài hơn 2 dòng × $cap ký tự ⇒ ô thanh nút cắt '…'")
        // Khoá đúng ca QA: nhãn mới ngắn hơn bản bị cắt và không còn "tempat duduk".
        val vent = labels.first { it.first == "seatc" }.second
        assertTrue(vent.length < "Pengudaraan tempat duduk pemandu".length && "tempat duduk" !in vent, vent)
    }

    @Test
    fun `cung ngan sach voi VI va EN - ngan sach khong phai bia rieng cho ms`() {
        for (lang in listOf(Lang.VI, Lang.EN)) {
            val bad = dockLabels(lang).filterNot { twoLines(it.second, LangCoverageFixtures.SHORT_CAP) }
            assertEquals(emptyList<Pair<String, String>>(), bad, "$lang vượt ngân sách mà QA thấy vừa ⇒ ngân sách sai")
        }
    }

    @Test
    fun `phep tach dong dung - khoang trang va sau gach noi`() {
        assertTrue(twoLines("Kunci kanak-kanak kanan", 14), "Kunci kanak- | kanak kanan")
        assertTrue(twoLines("Pengudaraan pemandu", 14))
        assertTrue(!twoLines("Pengudaraan tempat duduk pemandu", 14))
        assertTrue(!twoLines("Pemanasan kerusi penumpang", 14), "kerusi penumpang = 16 ký tự")
    }
}
