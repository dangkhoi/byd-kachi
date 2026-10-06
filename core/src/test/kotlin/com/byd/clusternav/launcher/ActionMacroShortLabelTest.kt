package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 `ACTIONMACRO-SHORT-LABEL` (spec `docs/specs/kachi-293-widget.html` R-W15) — gói lệnh có nhãn NGẮN như nút (J1,
 * `FitLabels`): [ĐO máy ảo QA3 04/10] khung 2×1 có 6 nút hiện `Mở h…` / `Đóng…` / ZH `…打开` vì gói lệnh chưa có bản ngắn.
 *
 * Khoá: mọi gói định sẵn có bản ngắn KHÁC bản đầy (VI · ZH); hai gói không bao giờ cùng một chữ ngắn ở bất kỳ tiếng nào
 * (luật phân biệt của lưới không cần tới cắt đầu); bậc lùi y hệt nút ([ControlDef.shortLabelIn]); trần chữ ngắn cùng
 * trần của chip/hàng nút (zh ≤ 6 ký tự).
 */
class ActionMacroShortLabelTest {

    @Test
    fun `moi goi dinh san co nhan ngan ngan hon nhan day`() {
        ActionMacros.ALL.forEach { m ->
            assertTrue(m.short != null, "${m.id}: thiếu nhãn ngắn")
            assertTrue(m.shortLabel.length < m.label.length, "${m.id}: '${m.shortLabel}' không ngắn hơn '${m.label}'")
            assertNotEquals(m.labelIn(Lang.ZH), m.shortLabelIn(Lang.ZH), "${m.id}: bản ngắn ZH phải khác bản đầy")
            assertTrue(m.shortLabelIn(Lang.ZH).length <= 6, "${m.id}: trần chip zh6 — '${m.shortLabelIn(Lang.ZH)}'")
        }
        assertEquals("Mở hết", ActionMacros.byId("mac_win_open_all")!!.shortLabel)
        assertEquals("Đóng hết", ActionMacros.byId("mac_win_close_all")!!.shortLabel)
        assertEquals("全开", ActionMacros.byId("mac_win_open_all")!!.shortLabelIn(Lang.ZH))
    }

    @Test
    fun `hai goi khong bao gio cung mot chu ngan o bat ky tieng nao`() {
        Lang.values().forEach { lang ->
            val shorts = ActionMacros.ALL.map { it.shortLabelIn(lang) }
            assertEquals(shorts.size, shorts.toSet().size, "$lang: $shorts")
        }
    }

    @Test
    fun `bac lui giong nut - EN khong khai thi dung labelEn`() {
        val m = ActionMacros.byId("mac_win_open_all")!!
        assertEquals("Open all", m.shortLabelIn(Lang.EN), "EN đã ngắn — không khai shortEn")
        val bare = ActionMacro("x", "Nhãn dài", "ic-x", Domain.BODY, emptyList())
        assertEquals("Nhãn dài", bare.shortLabelIn(Lang.VI), "không khai ⇒ lùi về nhãn đầy")
        val en = ActionMacro("y", "Dài", "ic-x", Domain.BODY, emptyList(), labelEn = "Long", short = "Ngắn", shortEn = "S")
        assertEquals("S", en.shortLabelIn(Lang.EN))
        assertEquals("Ngắn", en.shortLabelIn(Lang.VI))
    }
}
