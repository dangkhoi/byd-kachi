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

    /**
     * QA2 04/10 (P3, [ĐO] mỗi khung ghi `WidgetFit … refit#1` HAI lần cách 0,5–1 s mỗi lần mở máy): lượt quét ảnh đầu tiên
     * sau `onResume` (`WallpaperController.reload` → `WorkspaceView.setPhotoSource`) trả "đổi" dù nguồn y hệt nguồn widget
     * vừa được dựng (rỗng) ⇒ `rebuildWidgetSlots` dựng lại MỌI ô widget, mọi lưới khớp hai lần. Nguồn không đổi ⇒ không dựng.
     */
    @Test
    fun `luot quet dau khong co anh - khong phai doi, widget khong dung lai`() {
        val s = WorkspacePhotoSource()
        assertFalse(s.set(emptyList(), Slideshow.DEFAULT_INTERVAL_SEC), "y hệt nguồn widget đang dùng")
        assertFalse(s.set(emptyList(), 30), "không có ảnh ⇒ nhịp không đổi gì widget thấy")
        assertEquals(30, s.intervalSec, "nhịp vẫn được nhớ cho lần có ảnh")
        assertTrue(s.set(listOf("a.jpg"), 30), "có ảnh ⇒ đổi thật")
        assertFalse(s.set(listOf("a.jpg"), 30))
        assertTrue(s.set(emptyList(), 30), "xoá hết ảnh ⇒ đổi thật (widget phải bỏ ảnh cũ)")
    }

    /** Đổi nguồn ảnh chỉ dựng lại ô ĐỌC ảnh — lưới nút kính/ô số không khớp lại lần hai (QA2 P3, người có ảnh). */
    @Test
    fun `chi o doc anh moi dung lai khi nguon anh doi`() {
        val s = WorkspacePhotoSource()
        assertTrue(s.consumes(listOf(WorkspacePhotoSource.PHOTO_WIDGET)))
        assertTrue(s.consumes(listOf("w_clock", "w_photos", "win_lf")), "ô nén có trình chiếu")
        assertFalse(s.consumes(listOf("win_lf", "win_rf", "win_lr", "win_rr", "mac_win_open_all", "mac_win_close_all")))
        assertFalse(s.consumes(emptyList()))
        // Mã đúng là mã của bộ đăng ký widget (không gõ lệch một chữ).
        assertTrue(WidgetRegistry.ALL.any { it.id == WorkspacePhotoSource.PHOTO_WIDGET })
    }
}
