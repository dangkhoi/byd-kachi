package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.SlotCloseConfirm.Tap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Soát 2.87 · P2 — *tắt* ở đầu ô là HAI chạm ([SlotCloseConfirm]). Ca hỏng có thật trong mã trước bản này: lái xe kéo bản đồ
 * Google Maps ⇒ hàng đầu ô hiện 3 s ⇒ chạm ô tìm kiếm (giữa-trên, đúng chỗ nút *tắt*) ⇒ `am stack remove` NGAY, không hoàn tác.
 * Bảng viết TAY; nhịp nhấp đúp 300 ms (mặc định AOSP `ViewConfiguration.DOUBLE_TAP_TIMEOUT`).
 */
class SlotCloseConfirmTest {

    private val gap = 300L

    /** Soát vòng 2 — ĐỔI đầu vào có lý do: khoảng nhấp đúp là DOWN₂ − UP₁ (cột 3), không phải click-tới-click (KDoc lớp). */
    @Test
    fun `bang hai buoc`() {
        val t0 = 10_000L
        data class Row(val armedAt: Long?, val now: Long, val downGap: Long?, val want: Tap)
        val rows = listOf(
            Row(null, t0, null, Tap.ARM),                       // chạm đầu: chỉ đổi trạng thái, KHÔNG tắt
            Row(t0, t0 + 250, 100, Tap.WAIT),                   // nhấp đúp (vd phóng to bản đồ đúng chỗ nút) ⇒ không tắt
            Row(t0, t0 + 600, 300, Tap.FIRE),                   // DOWN₂ đúng bằng nhịp nhấp đúp ⇒ người đã thấy nút đỏ rồi chạm
            Row(t0, t0 + 1_999, 1_500, Tap.FIRE),               // còn trong 2 s
            Row(t0, t0 + 2_000, 1_700, Tap.ARM),                // hết 2 s ⇒ lại là chạm đầu
            Row(t0, t0 + 60_000, 50_000, Tap.ARM),
            Row(t0, t0 - 1, 100, Tap.ARM),                      // đồng hồ lùi ⇒ không bao giờ FIRE nhờ hiệu âm
            Row(t0, t0 + 1_000, null, Tap.FIRE),                // cú chạm không đến từ ngón (trợ năng / bàn phím) ⇒ không chặn
            Row(t0, t0 + 1_000, -5, Tap.WAIT),                  // DOWN₂ trước UP₁ = số đo vô nghĩa ⇒ không FIRE trên nó
            // Soát vòng 3 [P3] — chốt thứ hai click-tới-click: không có số đo DOWN₂ − UP₁ (click không ghép được lần nhấn) mà lượt
            // hai tới 120 ms sau lượt đầu ⇒ không ai kịp thấy đĩa đỏ rồi xác nhận ⇒ WAIT, không FIRE.
            Row(t0, t0 + 120, null, Tap.WAIT),
            Row(t0, t0 + 299, null, Tap.WAIT),
            Row(t0, t0 + 300, null, Tap.FIRE),
        )
        rows.forEach { r ->
            assertEquals(r.want, SlotCloseConfirm.onTap(r.armedAt, r.now, r.downGap, gap), "$r")
        }
    }

    /**
     * Soát vòng 2 [P3] — ca hỏng của bản click-tới-click: lái xe nhấp đúp phóng to Google Maps đúng chỗ nút *tắt* đang hiện.
     * Cú 1: click ở UP₁ ⇒ ARM. Cú 2: DOWN₂ sau UP₁ 200 ms (nhấp đúp hợp lệ — AOSP tính ≤ 300 ms, `GestureDetector`
     * `isConsideredDoubleTap`), nhấn 120 ms ⇒ click thứ hai cách click đầu 320 ms ≥ 300 ⇒ bản cũ FIRE ⇒ `am stack remove`.
     */
    @Test
    fun `nhap dup that khong tat du hai click cach nhau hon nhip nhap dup`() {
        val up1 = 50_000L
        val down2 = up1 + 200
        val click2 = down2 + 120
        assertEquals(Tap.WAIT, SlotCloseConfirm.onTap(armedAt = up1, now = click2, downGapMs = down2 - up1, minGapMs = gap))
        assertTrue(click2 - up1 >= gap, "đúng ca mà phép đo click-tới-click để lọt")
    }

    @Test
    fun `mot cham nham khong bao gio tat`() {
        assertEquals(Tap.ARM, SlotCloseConfirm.onTap(null, 0L, null, gap))
        assertEquals(Tap.ARM, SlotCloseConfirm.onTap(null, Long.MAX_VALUE, 0L, gap))
    }

    @Test
    fun `nhip nhap dup bat thuong khong bien nut thanh nut chet`() {
        assertEquals(Tap.FIRE, SlotCloseConfirm.onTap(0L, 1_200L, 1_000L, minGapMs = 5_000L), "ngưỡng kẹp ≤ 1 s ⇒ vẫn xác nhận được")
        assertEquals(Tap.WAIT, SlotCloseConfirm.onTap(0L, 1_200L, 999L, minGapMs = 5_000L))
        assertEquals(Tap.FIRE, SlotCloseConfirm.onTap(0L, 0L, 0L, minGapMs = -1L), "ngưỡng âm ⇒ 0")
        assertEquals(2_000L, SlotCloseConfirm.WINDOW_MS, "quyết định điều phối: 2 s")
    }

    /**
     * Soát vòng 2 [P3] — cửa sổ chờ theo cài đặt trợ năng *"Thời gian thực hiện hành động"* (`getRecommendedTimeoutMillis`,
     * API 29): người đặt 10 s thì lần xác nhận ở giây thứ 5 vẫn TẮT (bản 2 s cố định: ARM lại mãi, nút chết với người đó);
     * không bao giờ NGẮN hơn gốc 2 s; đầu ô không ẩn trước khi lượt chờ hết (ẩn = gỡ lượt chờ).
     */
    @Test
    fun `cua so theo thoi gian thuc hien hanh dong cua tro nang`() {
        val win = SlotCloseConfirm.window(10_000L)
        assertEquals(10_000L, win)
        assertEquals(Tap.FIRE, SlotCloseConfirm.onTap(0L, 5_000L, null, gap, win), "trong cửa sổ 10 s")
        assertEquals(Tap.FIRE, SlotCloseConfirm.onTap(0L, 9_999L, 2_000L, gap, win))
        assertEquals(Tap.ARM, SlotCloseConfirm.onTap(0L, 10_000L, 2_000L, gap, win), "hết cửa sổ 10 s")
        assertEquals(2_000L, SlotCloseConfirm.window(0L), "không ngắn hơn gốc")
        assertEquals(2_000L, SlotCloseConfirm.window(1_500L))
        assertEquals(Tap.ARM, SlotCloseConfirm.onTap(0L, 2_000L, null, gap, windowMs = 500L), "cửa sổ truyền vào ngắn hơn gốc ⇒ gốc")
        // Đầu ô: không chờ / cửa sổ gốc ⇒ đúng 3 s như hôm nay; cửa sổ 10 s ⇒ ẩn sau khi hết chờ + 1 s.
        assertEquals(SlotHeadRest.HIDE_AFTER_MS, SlotCloseConfirm.hideAfterMs(null))
        assertEquals(SlotHeadRest.HIDE_AFTER_MS, SlotCloseConfirm.hideAfterMs(SlotCloseConfirm.WINDOW_MS), "cửa sổ gốc: byte như hôm nay")
        assertEquals(SlotHeadRest.HIDE_AFTER_MS, SlotCloseConfirm.hideAfterMs(500L))
        assertEquals(11_000L, SlotCloseConfirm.hideAfterMs(10_000L))
    }

    /**
     * Soát vòng 5 [P3] — lần nhấn chỉ là XÁC NHẬN khi ngón nhấn xuống SAU khung vẽ đầu tiên của đĩa đỏ; cú chạm không đến từ
     * ngón (trợ năng) không xét. Chưa thấy ⇒ một lần chạm hợp lệ về khoảng cách vẫn chỉ WAIT (ô vẫn chờ).
     */
    @Test
    fun `chua thay dia do thi chua phai xac nhan`() {
        assertTrue(SlotCloseConfirm.seen(pressDown = null, shownAt = null), "trợ năng: không có lần nhấn")
        assertFalse(SlotCloseConfirm.seen(pressDown = 1_600, shownAt = null), "chưa vẽ lần nào")
        assertFalse(SlotCloseConfirm.seen(pressDown = 1_600, shownAt = 2_516), "nhấn TRƯỚC khung vẽ")
        assertTrue(SlotCloseConfirm.seen(pressDown = 2_516, shownAt = 2_516))
        assertTrue(SlotCloseConfirm.seen(pressDown = 2_800, shownAt = 2_516))
        assertEquals(Tap.WAIT, SlotCloseConfirm.onTap(1_000, 1_700, 600, 300, seen = false))
        assertEquals(Tap.FIRE, SlotCloseConfirm.onTap(1_000, 1_700, 600, 300, seen = true))
        assertEquals(Tap.ARM, SlotCloseConfirm.onTap(null, 1_700, null, 300, seen = false), "lượt đầu không cần thấy gì")
        assertEquals(Tap.ARM, SlotCloseConfirm.onTap(1_000, 3_100, 600, 300, seen = false), "quá cửa sổ ⇒ lượt đầu mới")
    }
}
