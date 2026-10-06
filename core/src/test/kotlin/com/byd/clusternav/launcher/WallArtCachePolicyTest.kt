package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 `WALLART-CACHE-BOUND` — bộ đệm ảnh mờ `wallpapers/.kachi-art/` có trần. Khoá theo đúng khuôn `WallArtBuilder.build`:
 * `<tên ảnh>-<mtime>-<cỡ>-<W>x<H>-<FIT>-<dim>`.
 */
class WallArtCachePolicyTest {

    private fun k(src: String, mtime: Long, dim: Int = 35, fit: String = "FILL") = "$src-$mtime-12345-1920x1080-$fit-$dim"
    private fun e(key: String, t: Long) = WallArtCachePolicy.Entry(key, t)

    @Test
    fun `ten anh nguon - chua gach noi, co duoi, tep la thi khong dung`() {
        assertEquals("beach.jpg", WallArtCachePolicy.sourceOf(k("beach.jpg", 1700000000000)))
        assertEquals("IMG-2024-01-01.jpg", WallArtCachePolicy.sourceOf(k("IMG-2024-01-01.jpg", 0)))
        assertEquals("beach", WallArtCachePolicy.sourceOf("beach-1-2-3x4-FILL-35"), "khuôn bài DiagFilesTest")
        assertNull(WallArtCachePolicy.sourceOf("notes"), "tệp lạ ⇒ không đụng")
        assertNull(WallArtCachePolicy.sourceOf("a-b-c-d-e-f"), "đuôi sai khuôn ⇒ không đụng")
        assertNull(WallArtCachePolicy.sourceOf("-1-2-3x4-FILL-35"), "không có tên ảnh")
    }

    @Test
    fun `anh da go thi xoa moi khoa cua no, khoa la khong bao gio xoa`() {
        val entries = listOf(e(k("old.jpg", 1), 10), e(k("old.jpg", 2), 20), e(k("keep.jpg", 3), 30), e("README", 5))
        val gone = WallArtCachePolicy.victims(entries, setOf("keep.jpg"), protect = null)
        assertEquals(setOf(k("old.jpg", 1), k("old.jpg", 2)), gone.toSet())
    }

    @Test
    fun `moi anh giu hai khoa moi nhat - doi qua lai muc lam toi khong phai nau lai`() {
        val entries = (0 until 5).map { i -> e(k("a.jpg", 100, dim = i * 10), 1000L + i) }
        val gone = WallArtCachePolicy.victims(entries, setOf("a.jpg"), protect = null)
        assertEquals(setOf(k("a.jpg", 100, 0), k("a.jpg", 100, 10), k("a.jpg", 100, 20)), gone.toSet(), "giữ dim 40 và 30")
    }

    @Test
    fun `tran tong - bo khoa cu nhat, khoa vua nau duoc bao ve`() {
        val entries = (0 until 10).map { i -> e(k("p$i.jpg", 1), i.toLong()) }
        val sources = (0 until 10).map { "p$it.jpg" }.toSet()
        val gone = WallArtCachePolicy.victims(entries, sources, protect = k("p0.jpg", 1), maxKeys = 4)
        assertEquals((1 until 6).map { k("p$it.jpg", 1) }.toSet(), gone.toSet(), "6 khoá cũ nhất trừ khoá được bảo vệ")
        assertTrue(k("p0.jpg", 1) !in gone)
        // Bộ đệm nhỏ hơn trần ⇒ không xoá gì.
        assertEquals(emptyList<String>(), WallArtCachePolicy.victims(entries.take(3), sources, protect = null))
    }
}
