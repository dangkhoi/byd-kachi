package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.ShortcutGridFit.Scroll
import com.byd.clusternav.launcher.ShortcutScrollKeep.Wanted
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 (spec `docs/specs/kachi-293-slot.html` R1/R2) — vị trí cuộn của lưới lối tắt sống qua lượt đo ở khung lạ và qua lượt
 * dựng lại. px ở mật độ 1,5 như `ShortcutGridScrollTest`: khe 12 · sàn 60 · trần 180.
 */
class ShortcutScrollKeepTest {

    private fun fit(n: Int, w: Int, h: Int) = ShortcutGridFit.fit(n, w, h, 12, 60, 180)

    /**
     * Khoá lỗi QA máy ảo 2.92 `SHORTCUT-SCROLL-DOCK-RELAYOUT` [ĐO 2/2 lần]: 22 app, thanh nút cạnh bên 150 % ⇒ khung thật
     * 1362×148, cuộn tới cuối = 264 px; về HOME có MỘT lượt đo ở khung của cấu hình thanh mặc định (dưới, 100 %) 1558×123
     * ⇒ quãng chỉ còn 43 ⇒ bản 2.92 (một con số, kẹp mỗi lượt đo) trả 43 vĩnh viễn. Ba số của QA phải ra đúng từ phép khớp
     * thật — nếu phép khớp đổi thì bài này báo trước khi con số trong tài liệu thành sai.
     */
    @Test
    fun `luot do o khung la khong xoa vi tri nguoi lai chon - so QA 264 va 43`() {
        val real = fit(22, 1362, 148)
        val transient = fit(22, 1558, 123)
        assertEquals(Scroll.HORIZONTAL, real.scroll)
        assertEquals(Scroll.HORIZONTAL, transient.scroll)
        assertEquals(264, real.maxScrollPx, "khung thật của QA: cuộn tới cuối = 264")
        assertEquals(43, transient.maxScrollPx, "khung lượt đầu của QA: quãng 43")

        // Mô hình 2.92: một vị trí, kẹp ở mỗi lượt đo ⇒ 264 → 43 → 43 (lỗi).
        var old = real.maxScrollPx
        listOf(transient, real).forEach { f -> old = old.coerceIn(0, f.maxScrollPx) }
        assertEquals(43, old, "mô hình cũ tái lập đúng số QA")

        // 2.93: người lái chọn 264; lượt đo khung lạ áp 43 nhưng KHÔNG ghi; lượt khung thật trả lại 264.
        val wanted = ShortcutScrollKeep.userScrolled(Scroll.HORIZONTAL, real.maxScrollPx)
        assertEquals(43, ShortcutScrollKeep.applied(wanted, transient))
        assertEquals(264, ShortcutScrollKeep.applied(wanted, real))
    }

    @Test
    fun `khung khong cuon hoac cuon truc khac ap 0 nhung giu lua chon cua truc cu`() {
        val wanted = Wanted(Scroll.VERTICAL, 300)
        val none = fit(3, 600, 600)
        assertEquals(Scroll.NONE, none.scroll)
        assertEquals(0, ShortcutScrollKeep.applied(wanted, none))
        val horizontal = fit(40, 1558, 123)
        assertEquals(Scroll.HORIZONTAL, horizontal.scroll)
        assertEquals(0, ShortcutScrollKeep.applied(wanted, horizontal), "trục khác ⇒ 0, không đem số dọc sang ngang")
        val vertical = fit(40, 262, 956)
        assertEquals(Scroll.VERTICAL, vertical.scroll)
        assertEquals(minOf(300, vertical.maxScrollPx), ShortcutScrollKeep.applied(wanted, vertical), "về trục cũ ⇒ trả lại")
    }

    @Test
    fun `kep vao quang moi khi danh sach ngan lai, mo rong lai thi tra dung cho cu`() {
        val wanted = ShortcutScrollKeep.userScrolled(Scroll.HORIZONTAL, 500)
        val shorter = fit(20, 1362, 148)
        assertTrue(shorter.maxScrollPx in 1 until 500, "giả định của bài: quãng mới ngắn hơn lựa chọn")
        assertEquals(shorter.maxScrollPx, ShortcutScrollKeep.applied(wanted, shorter), "kẹp như settleScroll cũ")
        val longer = fit(30, 1362, 148)
        assertTrue(longer.maxScrollPx >= 500)
        assertEquals(500, ShortcutScrollKeep.applied(wanted, longer))
    }

    @Test
    fun `nguoi lai cuon - truc NONE ve goc, so am ve 0`() {
        assertEquals(Wanted.ORIGIN, ShortcutScrollKeep.userScrolled(Scroll.NONE, 120))
        assertEquals(Wanted(Scroll.VERTICAL, 0), ShortcutScrollKeep.userScrolled(Scroll.VERTICAL, -5))
        assertEquals(0, ShortcutScrollKeep.applied(Wanted.ORIGIN, fit(40, 1558, 123)), "chưa cuộn ⇒ đầu dải")
    }

    /** SCROLL-KEEP: phát tin của gói NGOÀI danh sách không được chạm lưới (2.92 dựng lại cả lưới ở MỌI phát tin). */
    @Test
    fun `phat tin goi ngoai danh sach khong cham luoi, khong ten goi thi lam moi`() {
        val listed = listOf("com.waze", "vn.vietmap.live")
        assertTrue(ShortcutScrollKeep.touches("com.waze", listed))
        assertFalse(ShortcutScrollKeep.touches("com.android.chrome", listed))
        assertTrue(ShortcutScrollKeep.touches(null, listed))
        assertTrue(ShortcutScrollKeep.touches("", listed))
    }

    @Test
    fun `chi dung lai cau truc khi danh sach doi`() {
        val a = listOf(AppShortcut("com.waze", ShortcutMode.Full), AppShortcut("vn.vietmap.live", ShortcutMode.Full))
        assertTrue(ShortcutScrollKeep.needsRebuild(null, a), "chưa dựng lần nào")
        assertFalse(ShortcutScrollKeep.needsRebuild(a, a.toList()), "cùng danh sách (bản sao) ⇒ chỉ làm mới icon")
        assertTrue(ShortcutScrollKeep.needsRebuild(a, a.reversed()), "đổi thứ tự")
        assertTrue(ShortcutScrollKeep.needsRebuild(a, a.take(1)), "bớt app")
        assertTrue(ShortcutScrollKeep.needsRebuild(a, listOf(a[0], a[1].copy(mode = ShortcutMode.Background))), "đổi kiểu mở")
    }
}
