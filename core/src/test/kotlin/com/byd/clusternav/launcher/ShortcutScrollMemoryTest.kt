package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.ShortcutGridFit.Scroll
import com.byd.clusternav.launcher.ShortcutScrollKeep.Wanted
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 wave 2A · SHORTCUT-SCROLL-REBUILD — lựa chọn cuộn của người lái sống qua lượt dựng VIEW MỚI của ô ═════════════
 *
 * Khoá senior review SLOT Pass 2 mục 4 [P3, SUY đọc mã]: đổi Sáng/Tối / đổi đơn vị / Activity dựng lại tạo `ShortcutIconsView`
 * MỚI ⇒ bản trước bắt đầu từ [Wanted.ORIGIN] (dải về đầu). Nay view mới của CÙNG ô + CÙNG tổ hợp đọc [ShortcutScrollMemory]
 * rồi áp như lượt dựng lại trong cùng view ([ShortcutScrollKeep.applied]). Số khung lấy đúng ca QA 2.92 (22 app, 1362×148 ⇒
 * cuộn tới cuối 264 px) như `ShortcutScrollKeepTest`. Khoá mỗi bài mang tiền tố riêng (đối tượng sống suốt tiến trình test).
 */
class ShortcutScrollMemoryTest {

    private fun fit(n: Int, w: Int, h: Int) = ShortcutGridFit.fit(n, w, h, 12, 60, 180)

    @Test
    fun `view moi cua cung o tiep tuc dung cho nguoi lai da chon`() {
        val real = fit(22, 1362, 148)
        val key = ShortcutScrollMemory.slotKey(2, listOf("w_apps"))
        // View cũ: người lái cuộn tới cuối (264) — chỗ ghi DUY NHẤT là cú cuộn của người lái (ShortcutGridLayout.scrollAlongTo).
        ShortcutScrollMemory.remember(key, ShortcutScrollKeep.userScrolled(Scroll.HORIZONTAL, real.maxScrollPx))
        // View MỚI (restyle Sáng/Tối): bắt đầu từ bản nhớ, không từ đầu.
        val start = ShortcutScrollMemory.recall(key) ?: Wanted.ORIGIN
        assertEquals(264, ShortcutScrollKeep.applied(start, real), "dải ở đúng chỗ người lái để, không về 0")
        // Khung lượt đầu lạ (1558×123) vẫn chỉ ÁP, không ghi — lượt khung thật trả lại 264 (luật R2 giữ nguyên).
        assertEquals(43, ShortcutScrollKeep.applied(start, fit(22, 1558, 123)))
        assertEquals(264, ShortcutScrollKeep.applied(ShortcutScrollMemory.recall(key)!!, real))
    }

    @Test
    fun `khoa theo o va to hop - doi o hay doi to hop thi bat dau tu dau`() {
        val a = ShortcutScrollMemory.slotKey(1, listOf("w_apps", "w_clock"))
        ShortcutScrollMemory.remember(a, Wanted(Scroll.VERTICAL, 120))
        assertNull(ShortcutScrollMemory.recall(ShortcutScrollMemory.slotKey(3, listOf("w_apps", "w_clock"))), "ô khác")
        assertNull(ShortcutScrollMemory.recall(ShortcutScrollMemory.slotKey(1, listOf("w_clock", "w_apps"))), "tổ hợp khác thứ tự")
        assertNull(ShortcutScrollMemory.recall(ShortcutScrollMemory.slotKey(1, listOf("w_apps"))), "tổ hợp khác")
        assertEquals(Wanted(Scroll.VERTICAL, 120), ShortcutScrollMemory.recall(a))
        assertNotEquals(ShortcutScrollMemory.slotKey(12, listOf("w")), ShortcutScrollMemory.slotKey(1, listOf("2|w")), "khoá không nhập nhằng")
    }

    @Test
    fun `khung khong cuon - quen khoa`() {
        val k = ShortcutScrollMemory.slotKey(40, listOf("w_apps", "origin-case"))
        ShortcutScrollMemory.remember(k, Wanted(Scroll.HORIZONTAL, 80))
        ShortcutScrollMemory.remember(k, ShortcutScrollKeep.userScrolled(Scroll.NONE, 0))
        assertNull(ShortcutScrollMemory.recall(k))
    }

    @Test
    fun `tran LRU - khoa cu nhat roi ra, khong phinh vo han`() {
        val keys = (0..ShortcutScrollMemory.CAPACITY).map { ShortcutScrollMemory.slotKey(100 + it, listOf("w_apps", "lru")) }
        keys.forEach { ShortcutScrollMemory.remember(it, Wanted(Scroll.VERTICAL, it.length)) }
        assertNull(ShortcutScrollMemory.recall(keys.first()), "vượt ${ShortcutScrollMemory.CAPACITY} khoá ⇒ khoá cũ nhất rơi")
        assertEquals(Wanted(Scroll.VERTICAL, keys.last().length), ShortcutScrollMemory.recall(keys.last()))
    }
}
