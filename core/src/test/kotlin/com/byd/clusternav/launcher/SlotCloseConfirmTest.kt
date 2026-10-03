package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.SlotCloseConfirm.Tap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Soát 2.87 · P2 — *tắt* ở đầu ô là HAI chạm ([SlotCloseConfirm]). Ca hỏng có thật trong mã trước bản này: lái xe kéo bản đồ
 * Google Maps ⇒ hàng đầu ô hiện 3 s ⇒ chạm ô tìm kiếm (giữa-trên, đúng chỗ nút *tắt*) ⇒ `am stack remove` NGAY, không hoàn tác.
 * Bảng viết TAY; nhịp nhấp đúp 300 ms (mặc định AOSP `ViewConfiguration.DOUBLE_TAP_TIMEOUT`).
 */
class SlotCloseConfirmTest {

    private val gap = 300L

    @Test
    fun `bang hai buoc`() {
        val t0 = 10_000L
        val rows = listOf(
            Triple(null, t0, Tap.ARM),                          // chạm đầu: chỉ đổi trạng thái, KHÔNG tắt
            Triple(t0, t0 + 299, Tap.WAIT),                     // nhấp đúp (vd phóng to bản đồ đúng chỗ nút) ⇒ không tắt
            Triple(t0, t0 + 300, Tap.FIRE),                     // đã thấy nút đỏ rồi chạm lần hai ⇒ tắt
            Triple(t0, t0 + 1_999, Tap.FIRE),                   // còn trong 2 s
            Triple(t0, t0 + 2_000, Tap.ARM),                    // hết 2 s ⇒ lại là chạm đầu
            Triple(t0, t0 + 60_000, Tap.ARM),
            Triple(t0, t0 - 1, Tap.ARM),                        // đồng hồ lùi ⇒ không bao giờ FIRE nhờ hiệu âm
        )
        rows.forEach { (armedAt, now, want) ->
            assertEquals(want, SlotCloseConfirm.onTap(armedAt, now, gap), "armedAt=$armedAt now=$now")
        }
    }

    @Test
    fun `mot cham nham khong bao gio tat`() {
        assertEquals(Tap.ARM, SlotCloseConfirm.onTap(null, 0L, gap))
        assertEquals(Tap.ARM, SlotCloseConfirm.onTap(null, Long.MAX_VALUE, gap))
    }

    @Test
    fun `nhip nhap dup bat thuong khong bien nut thanh nut chet`() {
        assertEquals(Tap.FIRE, SlotCloseConfirm.onTap(0L, 1_000L, minGapMs = 5_000L), "ngưỡng kẹp ≤ 1 s ⇒ vẫn xác nhận được")
        assertEquals(Tap.WAIT, SlotCloseConfirm.onTap(0L, 999L, minGapMs = 5_000L))
        assertEquals(Tap.FIRE, SlotCloseConfirm.onTap(0L, 0L, minGapMs = -1L), "ngưỡng âm ⇒ 0")
        assertEquals(2_000L, SlotCloseConfirm.WINDOW_MS, "quyết định điều phối: 2 s")
    }
}
