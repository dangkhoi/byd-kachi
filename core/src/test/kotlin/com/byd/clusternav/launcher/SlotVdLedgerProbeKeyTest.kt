package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 2.98 · R14 (OQ4 `SLOT-ADOPT-NOTIFY-OLD-HOST`) — màn ảo của màn chính CŨ bị lấy ⇒ `SlotVdOwner` gỡ bộ đo của host cũ theo khoá
 * [SlotVdLedger.keyOf] của mục bị trả về. Khoá phải trùng khoá bộ đo (`VdAppHost.probeKey`, [ĐO log máy ảo] `ô ws@34366045#0: …`).
 * [ĐO máy ảo 09/10 15:48:57] HOME dựng màn chính mới ⇒ `slot-taken` ⇒ 1,6 s sau bộ đo màn cũ `ws@34366045#0` kết luận "app đã
 * đóng" ⇒ `APP_DIED -> Clear` trên màn đang ẩn.
 */
class SlotVdLedgerProbeKeyTest {

    @Test
    fun `muc bi lay tra ve dung khoa bo do cua host cu`() {
        val l = SlotVdLedger<String>()
        l.adopt("ws@34366045", 0, "kachi-slot-0", "vd10")
        val stale = l.adopt("ws@228132583", 0, "kachi-slot-0-g1", "vd14")
        assertEquals(listOf("ws@34366045#0"), stale.map { SlotVdLedger.keyOf(it.owner, it.slot) })
        assertEquals("ws@228132583#0", SlotVdLedger.keyOf("ws@228132583", 0), "khoá host mới khác khoá cũ ⇒ không gỡ nhầm bộ đo mới")
    }

    @Test
    fun `cung man ao dang ky lai khong bi coi la bi lay`() {
        val l = SlotVdLedger<String>()
        l.adopt("ws@1", 0, "kachi-slot-0", "vd10")
        assertEquals(emptyList<Any>(), l.adopt("ws@1", 0, "kachi-slot-0", "vd10"))
    }
}
