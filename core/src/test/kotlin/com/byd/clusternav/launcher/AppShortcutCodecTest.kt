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

    /**
     * 2.92 (spec `kachi-292-shortcut-widget.html` R2) — ĐỔI GHIM có lý do: bài cũ "quá trần 8 thì cắt" khoá đúng trần owner
     * 06/10 bảo gỡ (*"không nên giới hạn 8 app"*). [ĐO máy ảo 2.89] 12/20/30 app ghi vào hồ sơ ⇒ log `bỏ 4/12/22 mục quá
     * trần 8`, widget chỉ hiện 8. Nay 12/20/30 app khứ hồi đủ + đúng thứ tự.
     */
    @Test
    fun `12, 20, 30 app khu hoi du va dung thu tu - khong con tran 8`() {
        listOf(12, 20, 30).forEach { n ->
            val list = (1..n).map { AppShortcut("com.app$it", if (it % 3 == 0) ShortcutMode.Background else ShortcutMode.Full) }
            val r = AppShortcutCodec.decodeReport(AppShortcutCodec.encode(list))
            assertEquals(list, r.items, "$n app")
            assertEquals(0, r.truncated, "$n app không bị cắt")
        }
    }

    /** Trần KỸ THUẬT (chống tệp hồ sơ hỏng/độc dựng hàng trăm nghìn icon trên màn nhà) vẫn cắt + báo số mục bị cắt. */
    @Test
    fun `qua tran ky thuat thi cat va bao so muc bi cat`() {
        assertTrue(AppShortcutCodec.MAX >= 200, "trần kỹ thuật phải ≫ số app có màn khởi chạy (máy ảo: 23)")
        val raw = (1..AppShortcutCodec.MAX + 3).joinToString(",") { "com.app$it|F" }
        val r = AppShortcutCodec.decodeReport(raw)
        assertEquals(AppShortcutCodec.MAX, r.items.size)
        assertEquals(3, r.truncated)
        assertEquals("com.app1", r.items.first().pkg)
        assertEquals(AppShortcutCodec.MAX, AppShortcutCodec.sanitize(r.items + AppShortcut("com.more", ShortcutMode.Full)).size)
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
