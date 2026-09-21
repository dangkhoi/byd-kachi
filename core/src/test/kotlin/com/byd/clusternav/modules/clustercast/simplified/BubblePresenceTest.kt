package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * WP6 · R6.1 — ba nhánh của [BubblePresence], mỗi bài đặt tên theo **lỗi nó chặn**, không theo bảng chân lý.
 *
 * Bảng chân lý 2×2 thì ai đọc mã cũng suy ra được; thứ không suy ra được là *"nhánh này sai thì hỏng cái gì"* —
 * và đó là lý do ba nhánh này không được gộp thành một `Boolean`.
 */
class BubblePresenceTest {

    @Test
    fun `an nut noi KHONG duoc di xin quyen ve-tren-app-khac`() {
        // Cả hai ca "đã tắt": có quyền hay không, câu trả lời vẫn là HIDDEN. Nếu xét quyền trước thì máy chưa cấp
        // quyền sẽ bung màn hệ thống *"cho phép hiển thị trên ứng dụng khác"* cho một thứ owner vừa tắt.
        assertEquals(BubblePresence.HIDDEN, BubblePresence.decide(visible = false, overlayGranted = false))
        assertEquals(BubblePresence.HIDDEN, BubblePresence.decide(visible = false, overlayGranted = true))
    }

    @Test
    fun `muon hien ma thieu quyen thi phai XIN, khong duoc im lang`() {
        // Bỏ nhánh này ⇒ nút nổi không bao giờ hiện và không có gì nói vì sao — đúng loại hỏng im lặng dự án cấm.
        assertEquals(
            BubblePresence.NEEDS_OVERLAY_PERMISSION,
            BubblePresence.decide(visible = true, overlayGranted = false),
        )
    }

    @Test
    fun `du ca hai thi dung cua so`() {
        assertEquals(BubblePresence.SHOW, BubblePresence.decide(visible = true, overlayGranted = true))
    }

    @Test
    fun `dung ba nhanh - them nhanh thu tu la doi hop dong voi cho goi`() {
        // Chỗ gọi (`FloatingBubbleService`) rẽ bằng `when` trên enum này; thêm một giá trị mà không sửa chỗ gọi là
        // một nhánh không ai xử. Ghim số để lượt thêm phải đi qua bài này.
        assertEquals(3, BubblePresence.values().size)
    }
}
