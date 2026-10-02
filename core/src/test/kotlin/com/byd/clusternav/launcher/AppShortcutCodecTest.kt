package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** C1 (spec shortcuts-autostart §4.4.1) — mã hoá danh sách lối tắt: khứ hồi, dễ dãi khi đọc, chặt khi ghi. */
class AppShortcutCodecTest {

    private val yt = "com.google.android.youtube"
    private val vm = "vn.vietmap.live"
    private val gm = "com.google.android.apps.maps"

    @Test
    fun `khu hoi ba kieu`() {
        val list = listOf(
            AppShortcut(yt, ShortcutMode.Slot(2)),
            AppShortcut(vm, ShortcutMode.Full),
            AppShortcut(gm, ShortcutMode.Background),
        )
        val raw = AppShortcutCodec.encode(list)
        assertEquals("$yt|S2,$vm|F,$gm|B", raw)
        assertEquals(list, AppShortcutCodec.decode(raw))
    }

    @Test
    fun `de dai khi doc - kieu la, o ngoai tam, thieu kieu thanh Toan man`() {
        val d = AppShortcutCodec.decode("$yt|X,$vm|S9,$gm|S0,com.a|S,com.b")
        assertEquals(List(5) { ShortcutMode.Full }, d.map { it.mode })
    }

    @Test
    fun `goi trung giu lan dau, goi khong hop le bi bo`() {
        val d = AppShortcutCodec.decode("$yt|S1,$yt|F,x;rm -rf|F,|F,1abc|B,$vm|B")
        assertEquals(listOf(AppShortcut(yt, ShortcutMode.Slot(1)), AppShortcut(vm, ShortcutMode.Background)), d)
    }

    @Test
    fun `qua tran 8 thi cat va bao so muc bi cat`() {
        val raw = (1..11).joinToString(",") { "com.app$it|F" }
        val r = AppShortcutCodec.decodeReport(raw)
        assertEquals(AppShortcutCodec.MAX, r.items.size)
        assertEquals(3, r.truncated)
        assertEquals("com.app1", r.items.first().pkg)
    }

    @Test
    fun `ghi khong bao gio chua tab hay xuong dong (dau ngan ProfileTransfer)`() {
        val dirty = listOf(
            AppShortcut("com.ok", ShortcutMode.Slot(7)),
            AppShortcut("com.bad\tpkg", ShortcutMode.Full),
            AppShortcut("com.bad\npkg", ShortcutMode.Full),
        )
        val raw = AppShortcutCodec.encode(dirty)
        assertEquals("com.ok|F", raw, "ô 7 ngoài 1…6 ⇒ Toàn màn; gói có tab/xuống dòng bị bỏ")
        assertFalse(raw.contains('\t') || raw.contains('\n'))
    }

    @Test
    fun `rong hay null thi danh sach rong`() {
        assertTrue(AppShortcutCodec.decode(null).isEmpty())
        assertTrue(AppShortcutCodec.decode("  ").isEmpty())
        assertEquals("", AppShortcutCodec.encode(emptyList()))
    }
}
