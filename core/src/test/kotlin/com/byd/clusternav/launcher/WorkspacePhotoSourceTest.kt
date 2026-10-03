package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [WorkspacePhotoSource] — tách khỏi `WorkspaceView` (2.87, trần 500 dòng). Khoá đúng bài học [SOÁT] của hàm gốc:
 * so nguồn ảnh theo NỘI DUNG, không theo số lượng (xoá 1 + thêm 1 ảnh khác = cùng số lượng nhưng PHẢI dựng lại widget).
 */
class WorkspacePhotoSourceTest {

    @Test
    fun `mac dinh rong va nhip mac dinh`() {
        val s = WorkspacePhotoSource()
        assertEquals(emptyList<String>(), s.provider())
        assertEquals(Slideshow.DEFAULT_INTERVAL_SEC, s.intervalSec)
    }

    @Test
    fun `so theo noi dung khong theo so luong`() {
        val s = WorkspacePhotoSource()
        assertTrue(s.set(listOf("a.jpg", "b.jpg"), 60), "lần đầu luôn là đổi")
        assertFalse(s.set(listOf("a.jpg", "b.jpg"), 60), "gọi lại cùng nguồn ⇒ không dựng lại")
        assertTrue(s.set(listOf("a.jpg", "c.jpg"), 60), "cùng SỐ LƯỢNG mà khác ảnh ⇒ phải dựng lại")
        assertEquals(listOf("a.jpg", "c.jpg"), s.provider())
        assertTrue(s.set(listOf("a.jpg", "c.jpg"), 30), "đổi nhịp ⇒ phải dựng lại")
        assertEquals(30, s.intervalSec)
    }
}
