package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Khoá tầng nối của LOG-41KB → LOG-VOLUME (3.1.10): [KachiLog.throttled] — **dòng nào thật sự phải xuống thẻ**.
 *
 * Luật + số đo ở `SessionLogGate` (`:core`, có bài riêng + bài phát lại log xe thật); ở đây canh rằng vòng chụp của
 * app đi qua ĐÚNG cổng ấy, và các quyết định cũ còn giữ: F/A không bao giờ bỏ, W/E bản đầu luôn ghi, đổi giá trị
 * qua ngay, dòng bị bỏ có số đếm, dòng lệch khuôn đi thẳng, vết gọi không bị cắt giữa.
 *
 * [3.1.10] Hai vế cũ ĐỔI CÓ CHỦ ĐÍCH (diagnostics `log-volume-2026-10-16.md`): (1) W/E lặp Y NGUYÊN trong 10 s nay
 * thành số đếm (`E/FrameEvents` ~500 bản/chuyến) — bản đầu vẫn ghi + xả ngay; (2) dòng D lặp y nguyên gộp 60 s.
 */
class KachiLogThrottleTest {

    /** Đúng dạng `logcat -v time` mà [KachiLog.startCapture] đọc — dấu thời gian dài đúng [KachiLog.SEVERITY_COL]. */
    private fun line(sev: Char, at: String, msg: String) = "09-26 $at $sev/BYDAutoAcDevice(17149): $msg"

    @Test
    fun `dong F A KHONG BAO GIO bi bo, W E ban dau luon ghi va ban sao co so dem`() {
        listOf('F', 'A').forEach { sev ->
            val g = SessionLogGate()
            val l = line(sev, "18:12:30.100", "cùng một lỗi chết")
            assertEquals(l, KachiLog.throttled(l, 0, g))
            val l2 = line(sev, "18:12:31.100", "cùng một lỗi chết")
            assertEquals(l2, KachiLog.throttled(l2, 1_000, g), "mức $sev lặp lại VẪN phải qua")
        }
        listOf('W', 'E').forEach { sev ->
            val g = SessionLogGate()
            val l = line(sev, "18:12:30.100", "cùng một cảnh báo")
            assertEquals(l, KachiLog.throttled(l, 0, g), "mức $sev phải qua lần đầu")
            assertNull(KachiLog.throttled(line(sev, "18:12:31.100", "cùng một cảnh báo"), 1_000, g))
            assertEquals(
                line(sev, "18:12:40.100", "cùng một cảnh báo") + "   [+1 lặp]",
                KachiLog.throttled(line(sev, "18:12:40.100", "cùng một cảnh báo"), 10_000, g),
                "bản sao không mất dấu: lần ghi kế mang số lần lặp",
            )
        }
    }

    @Test
    fun `dong lap trong cua so bi bo, va lan ghi ke mang dau vet`() {
        val g = SessionLogGate()
        val a = line('I', "18:12:30.100", "getTemprature temprature is: 24")
        // Cùng câu chữ, KHÁC dấu thời gian ⇒ phải cùng một khoá (đây là chỗ dễ làm sai nhất).
        val aLater = line('I', "18:12:35.900", "getTemprature temprature is: 24")
        assertEquals(a, KachiLog.throttled(a, 0, g))
        assertNull(KachiLog.throttled(aLater, 5_800, g), "cùng nội dung, khác giờ ⇒ vẫn là dòng trùng")
        assertEquals(
            line('I', "18:12:40.100", "getTemprature temprature is: 24") + "   [+1 lặp]",
            KachiLog.throttled(line('I', "18:12:40.100", "getTemprature temprature is: 24"), 10_000, g),
            "hết cửa sổ ⇒ ghi lại, kèm số dòng đã bỏ",
        )
        // Mức D: lặp y nguyên gộp 60 s (không mất số đếm).
        val d = line('D', "18:12:30.100", "getAcDefrostState area is: 1")
        assertEquals(d, KachiLog.throttled(d, 0, g))
        assertNull(KachiLog.throttled(line('D', "18:12:40.100", "getAcDefrostState area is: 1"), 10_000, g))
        assertEquals(
            line('D', "18:13:30.100", "getAcDefrostState area is: 1") + "   [+1 lặp]",
            KachiLog.throttled(line('D', "18:13:30.100", "getAcDefrostState area is: 1"), 60_000, g),
        )
    }

    @Test
    fun `gia tri DOI thi qua ngay`() {
        val g = SessionLogGate()
        val at3 = line('D', "18:12:30.100", "getSeatVentilatingState: The state of 1 seat ventilating is 3")
        val at1 = line('D', "18:12:30.900", "getSeatVentilatingState: The state of 1 seat ventilating is 1")
        assertEquals(at3, KachiLog.throttled(at3, 0, g))
        assertEquals(at1, KachiLog.throttled(at1, 800, g), "3 → 1 là dữ kiện: không được chờ hết cửa sổ")
        val i24 = line('I', "18:12:31.100", "getTemprature temprature is: 24")
        val i25 = line('I', "18:12:31.900", "getTemprature temprature is: 25")
        assertEquals(i24, KachiLog.throttled(i24, 1_000, g))
        assertEquals(i25, KachiLog.throttled(i25, 1_800, g), "mức I: chuỗi mới qua ngay như 3.1.9")
    }

    /** logcat chèn dòng mốc / cắt dòng: không được ném, và đi thẳng vì không phải đầu bản ghi. */
    @Test
    fun `dong ngan hong dinh dang khong lam no`() {
        val g = SessionLogGate()
        assertEquals("", KachiLog.throttled("", 0, g))
        assertEquals("--------- beginning of main", KachiLog.throttled("--------- beginning of main", 0, g))
        assertEquals("--------- beginning of main", KachiLog.throttled("--------- beginning of main", 1, g))
        assertEquals("x", KachiLog.throttled("x", 2, g))
    }

    /**
     * [SOÁT Pass 3 · P2 · 2026-09-26] **Vết gọi của một ngoại lệ không được bị cắt giữa.** Dòng `\tat …` không có
     * đầu dòng (nếu logcat có in kiểu ấy) đi thẳng; dòng `\tat …` CÓ đầu dòng (đúng `logcat -v time` thật — [ĐO
     * 3.1.10]) theo luật bản ghi nhiều dòng của `SessionLogGate`: luôn ghi, kéo đầu bản ghi vừa giữ ra trước.
     */
    @Test
    fun `dong phu cua vet goi ngoai le KHONG BAO GIO bi bo`() {
        val g = SessionLogGate()
        val head = line('W', "18:12:30.100", "gửi VM_BUBBLE_POS lỗi")
        val frame = "\tat java.lang.Thread.run(Thread.java:919)"
        assertEquals(head, KachiLog.throttled(head, 0, g))
        assertEquals(frame, KachiLog.throttled(frame, 1, g))
        val head2 = line('E', "18:12:32.100", "mở camera lỗi")
        assertEquals(head2, KachiLog.throttled(head2, 2_000, g))
        assertEquals(frame, KachiLog.throttled(frame, 2_001, g), "đuôi vết gọi trùng ⇒ vẫn phải ghi, nếu không vết gọi bị cắt giữa")
        // Ngoại lệ lặp y nguyên trong 10 s, vết gọi CÓ đầu dòng: đầu bản ghi được thả ra cùng dòng vết gọi.
        val again = line('W', "18:12:33.000", "gửi VM_BUBBLE_POS lỗi")
        val againFrame = line('W', "18:12:33.000", "\tat java.lang.Thread.run(Thread.java:919)")
        assertNull(KachiLog.throttled(again, 3_000, g))
        assertEquals(again + "\n" + againFrame, KachiLog.throttled(againFrame, 3_000, g))
        // Và một dòng D thường vẫn bị tiết chế như trước — bản vá này KHÔNG mở cổng cho dòng lặp thật.
        val noise = line('D', "18:12:34.000", "getTemprature temprature is: 24")
        assertEquals(noise, KachiLog.throttled(noise, 4_000, g))
        assertNull(KachiLog.throttled(line('D', "18:12:35.000", "getTemprature temprature is: 24"), 5_000, g))
    }
}
