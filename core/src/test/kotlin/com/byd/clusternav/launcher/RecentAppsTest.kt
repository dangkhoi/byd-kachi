package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Khoá logic danh sách app dùng gần đây (U3) — thứ tự mới-nhất-trước, khử trùng, cắt trần, chịu dữ liệu rác. */
class RecentAppsTest {

    @Test
    fun `app vua mo len DAU danh sach`() {
        assertEquals(listOf("c", "a", "b"), RecentApps.touch(listOf("a", "b"), "c"))
    }

    @Test
    fun `mo lai app da co thi chi DOI CHO len dau, khong nhan doi`() {
        assertEquals(listOf("b", "a", "c"), RecentApps.touch(listOf("a", "b", "c"), "b"))
    }

    @Test
    fun `cat con dung tran, bo phan tu cu nhat`() {
        val cur = (1..RecentApps.CAP).map { "p$it" }
        val out = RecentApps.touch(cur, "moi")
        assertEquals(RecentApps.CAP, out.size)
        assertEquals("moi", out.first())
        assertEquals("p${RecentApps.CAP - 1}", out.last())   // p<CAP> bị đẩy ra
    }

    @Test
    fun `ten goi rong hoac toan khoang trang thi giu nguyen danh sach`() {
        val cur = listOf("a", "b")
        assertEquals(cur, RecentApps.touch(cur, ""))
        assertEquals(cur, RecentApps.touch(cur, "   "))
    }

    @Test
    fun `tran bang 0 thi khong nho gi`() {
        assertEquals(listOf("a"), RecentApps.touch(listOf("a"), "b", cap = 0))
    }

    @Test
    fun `ma hoa roi giai ma tra ve dung danh sach`() {
        val list = listOf("com.a", "com.b", "com.c")
        assertEquals(list, RecentApps.decode(RecentApps.encode(list)))
    }

    @Test
    fun `giai ma chiu duoc null, dong rong va ban trung`() {
        assertEquals(emptyList<String>(), RecentApps.decode(null))
        assertEquals(emptyList<String>(), RecentApps.decode(""))
        assertEquals(listOf("a", "b"), RecentApps.decode("a\n\n  \nb\na"))
    }

    @Test
    fun `giai ma cat theo tran`() {
        val raw = (1..30).joinToString("\n") { "p$it" }
        assertEquals(RecentApps.CAP, RecentApps.decode(raw).size)
    }
}
