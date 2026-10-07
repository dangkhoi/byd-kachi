package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.96 WALL-RESCAN — khoá lỗi máy ảo 07/10: bật hình nền trước, chép ảnh sau ⇒ nền không bao giờ lên khi màn chính đứng yên.
 * Thử ĐỎ: `due` trả `false` khi thư mục trống ⇒ bài đầu đỏ.
 */
class WallpaperRescanTest {

    @Test
    fun `thu muc trong - quet moi nhip de anh chep vao hien ngay`() {
        assertTrue(WallpaperRescan.due(ticksSinceScan = 1, haveImages = false))
    }

    @Test
    fun `da co anh - chi quet lai sau 60 s`() {
        (1 until WallpaperRescan.TICKS_WITH_PHOTOS).forEach { assertFalse(WallpaperRescan.due(it, haveImages = true), "nhịp $it") }
        assertTrue(WallpaperRescan.due(WallpaperRescan.TICKS_WITH_PHOTOS, haveImages = true))
        assertEquals(6, WallpaperRescan.TICKS_WITH_PHOTOS, "6 × nhịp 10 s = 60 s")
    }

    @Test
    fun `so danh sach - bon ket qua`() {
        val a = listOf("/w/1.jpg"); val ab = listOf("/w/1.jpg", "/w/2.jpg")
        assertEquals(WallpaperRescan.Outcome.SAME, WallpaperRescan.outcome(a, a))
        assertEquals(WallpaperRescan.Outcome.SAME, WallpaperRescan.outcome(emptyList(), emptyList()))
        assertEquals(WallpaperRescan.Outcome.FIRST, WallpaperRescan.outcome(emptyList(), a))
        assertEquals(WallpaperRescan.Outcome.CHANGED, WallpaperRescan.outcome(a, ab))
        assertEquals(WallpaperRescan.Outcome.EMPTIED, WallpaperRescan.outcome(ab, emptyList()))
    }
}
