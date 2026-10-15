package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * ═══ [SOÁT 2026-09-18] Phiên lệnh phải giành lại được micro từ bộ nghe "Hey Kachi" ════════════════════════════
 *
 * Bài này khoá đúng chỗ mà *"bật Hey Kachi làm chết nút mic"* sẽ mọc lại: bộ nghe giữ chốt liên tục, nên nếu phép
 * chờ-nhường bị gỡ thì mọi lối vào phiên lệnh chỉ nhận `Busy`. [VoiceMicPreempt.sleep] bơm được ⇒ kiểm cả ba ca
 * mà không chờ thật một milli giây nào.
 */
class VoiceMicPreemptTest {

    @BeforeEach fun reset() = VoiceSingleFlight.reset()

    /** Bộ nghe nhả sau vài nhịp ⇒ phiên lệnh cầm được chốt. */
    @Test fun `nhuong kip thi phien lenh cam duoc chot`() {
        VoiceSingleFlight.acquireWake("wake#1")
        var steps = 0
        val ok = VoiceMicPreempt.preempt("chinh", waitMs = 600L, stepMs = 30L) {
            // Mô phỏng bộ nghe: nó hỏi cờ nhường mỗi khung rồi nhả — ở đây là sau nhịp thứ 2.
            if (++steps == 2) {
                assertTrue(VoiceSingleFlight.yieldRequested(), "bộ nghe phải thấy yêu cầu nhường")
                VoiceSingleFlight.release("wake#1")
            }
            true
        }
        assertTrue(ok, "phải cầm được chốt sau khi bộ nghe nhả")
        assertEquals(2, steps, "và không chờ thêm nhịp nào sau khi đã cầm được")
    }

    /**
     * 3.1.6 — owner 15/10 *"lần đầu mở voice không nghe, lần 2 mới được"*. [ĐO xe] bộ nghe wake ASR đang giải mã một cửa sổ
     * 1,6 s thì không hỏi cờ nhường được; nhịp giải mã đo trên xe max **2 667 ms**. Với trần mặc định cũ (600 ms) bài này ĐỎ
     * (phiên lệnh rút ⇒ *"không nghe rõ"*); trần mặc định phải đợi được bộ nghe nhả sau đúng lượt giải mã dài nhất đã đo.
     */
    @Test fun `tran mac dinh doi duoc mot luot giai ma wake dai nhat da do tren xe`() {
        VoiceSingleFlight.acquireWake("wake#9")
        val decodeMs = 2_667L
        var waited = 0L
        val ok = VoiceMicPreempt.preempt("chinh") { ms ->
            waited += ms
            if (waited >= decodeMs) VoiceSingleFlight.release("wake#9")   // giải mã xong ⇒ hỏi cờ ⇒ nhả
            true
        }
        assertTrue(ok, "phiên lệnh phải cầm được micro sau khi lượt giải mã wake (2,7 s) xong")
        assertTrue(waited < VoiceMicPreempt.WAIT_MS, "và vẫn trong trần hằng — không chờ vô hạn")
    }

    /** Bộ nghe KHÔNG nhả ⇒ hết trần thì rút, KHÔNG chờ mãi và KHÔNG cầm chốt. */
    @Test fun `khong nhuong thi het tran roi rut, khong cam chot`() {
        VoiceSingleFlight.acquireWake("wake#2")
        var steps = 0
        val ok = VoiceMicPreempt.preempt("chinh", waitMs = 300L, stepMs = 30L) { steps++; true }
        assertFalse(ok, "không nhường ⇒ không cầm chốt")
        assertEquals(10, steps, "số nhịp chờ là HẰNG (300/30) — không có đường chờ vô hạn")
        assertEquals(VoiceSingleFlight.Grant.Busy("wake#2"), VoiceSingleFlight.acquire("khac", 1L),
            "chốt vẫn thuộc bộ nghe — phép chờ không được cướp")
    }

    /** Bị interrupt giữa lúc chờ ⇒ thôi ngay (luồng đang bị dừng). */
    @Test fun `bi interrupt thi thoi ngay`() {
        VoiceSingleFlight.acquireWake("wake#3")
        var steps = 0
        val ok = VoiceMicPreempt.preempt("chinh", waitMs = 600L, stepMs = 30L) { steps++; false }
        assertFalse(ok)
        assertEquals(1, steps, "interrupt ⇒ rút ở nhịp đầu")
    }

    /** Cầu chì nổ trong lúc chờ ⇒ rút (nhánh gọi báo đúng lý do cũ, không nhân đôi nhật ký). */
    @Test fun `cau chi trong luc cho thi rut`() {
        // ⚠ [VoiceMicPreempt.preempt] xin chốt bằng **đồng hồ thật** (đúng cho bản chạy thật), nên cửa sổ cầu chì
        // phải được lấp quanh `System.currentTimeMillis()` — lấp bằng mốc giả nhỏ thì `trim` dọn hết và không nổ.
        // ⚠ Dùng nhãn AUTO ("hoi-lai"): nhãn người-bấm ("chinh") nay MIỄN cầu chì (vá "seri ngu",
        // [VoiceSingleFlight.LABEL_COMMAND]) nên nó không đi vào nhánh Fused này — đúng là điều ta muốn.
        val now = System.currentTimeMillis()
        for (i in 0 until VoiceSingleFlight.MAX_OPENS_PER_MINUTE) {
            VoiceSingleFlight.acquire("x$i", now - 1_000 + i); VoiceSingleFlight.release()
        }
        VoiceSingleFlight.acquireWake("wake#4") // không tiêu hạn mức, nhưng hạn mức đã đầy vì 12 lượt trên
        var steps = 0
        val ok = VoiceMicPreempt.preempt("hoi-lai", waitMs = 600L, stepMs = 30L) {
            if (++steps == 1) VoiceSingleFlight.release("wake#4")
            true
        }
        assertFalse(ok, "hạn mức đã đầy ⇒ Fused ⇒ rút")
        assertEquals(1, steps, "không chờ tiếp sau khi đã biết là cầu chì")
    }
}
