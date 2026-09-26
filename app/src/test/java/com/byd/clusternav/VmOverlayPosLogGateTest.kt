package com.byd.clusternav

import com.byd.clusternav.launcher.ResendGate
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * LOG-41KB (buổi xe 2026-09-26): `VmOverlayPos` ghi **3,8 dòng/phút** cùng một câu `gửi VM_BUBBLE_POS x=1339
 * y=100` suốt phiên — nhịp 2 s của `FloatingBubbleService` qua cổng gửi-lại 15 s.
 *
 * Bài canh giữ **hai** vế đối nhau, vì bản vá này rất dễ chữa quá tay:
 *  1. lượt **bắn** broadcast KHÔNG được thưa đi (bản mod dựng lại bong bóng giữa chuyến thì chính lượt bắn lặp
 *     ấy đưa nó về chỗ owner đã chỉnh — K8/1.70);
 *  2. dòng **log** lặp cho cùng toạ độ thì thôi ghi.
 *
 * Quét source vì [VmOverlayPosition.send] cần `Context`/`Log` thật (không Robolectric — cùng lệ
 * `VoiceOverlayImmersiveContractTest`); phần LUẬT thì canh trực tiếp trên [ResendGate].
 */
class VmOverlayPosLogGateTest {

    private val src by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/VmOverlayPosition.kt") }

    @Test
    fun `dong log lap di qua cong, lenh ban thi khong`() {
        val send = SourceRoots.body(src, "fun send(ctx: Context)")
        assertTrue(send.contains("app.sendBroadcast("), "lượt bắn phải nằm NGOÀI mọi cổng tiết chế log")
        assertTrue(send.contains("logGate.shouldSend("), "dòng log phải đi qua cổng")
        assertFalse(
            send.contains("gate.shouldSend("),
            "cổng GỬI (15 s) là việc của applyOnOpen — kéo nó vào send là chặn cả lượt kéo-thả của người dùng",
        )
        // Cổng log dùng lại ResendGate chứ không tự viết bộ đếm thứ hai (CLAUDE.md §4.1 DRY).
        assertTrue(src.contains("ResendGate(LOG_REPEAT_MS)"))
    }

    /** Luật thật của cổng: cùng nội dung ⇒ im tới hết cửa sổ; nội dung ĐỔI (người kéo bong bóng) ⇒ ghi ngay. */
    @Test
    fun `toa do doi thi ghi ngay, toa do cu thi cho het cua so`() {
        val gate = ResendGate(300_000L)
        assertTrue(gate.shouldSend(0, "x=1339 y=100"))
        assertFalse(gate.shouldSend(16_000, "x=1339 y=100"), "lượt lặp thứ hai không mang thêm dữ kiện nào")
        assertTrue(gate.shouldSend(16_001, "x=900 y=100"), "người dùng kéo bong bóng ⇒ phải thấy ngay trong log")
        assertTrue(gate.shouldSend(316_002, "x=900 y=100"), "hết cửa sổ ⇒ ghi lại một mốc chứng minh nhịp còn chạy")
    }
}
