package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Khoá tầng nối của LOG-41KB: [KachiLog.throttled] — **dòng nào thật sự phải xuống thẻ**.
 *
 * Luật + số đo ở `LogLineThrottle`; ở đây canh đúng ba quyết định của tầng này, mỗi cái là một cách bản vá hoá
 * thành có hại: bỏ dòng W/E (mất đúng thứ tệp log tồn tại để cứu), cắt khoá sai chỗ (không có gì trùng ⇒ không
 * cắt được gì), hoặc bỏ dòng mà không để lại dấu.
 */
class KachiLogThrottleTest {

    /** Đúng dạng `logcat -v time` mà [KachiLog.startCapture] đọc — dấu thời gian dài đúng [KachiLog.SEVERITY_COL]. */
    private fun line(sev: Char, at: String, msg: String) = "09-26 $at $sev/BYDAutoAcDevice(17149): $msg"

    @Test
    fun `dong W E F A KHONG BAO GIO bi bo, du lap y nguyen`() {
        val t = LogLineThrottle()
        listOf('W', 'E', 'F', 'A').forEach { sev ->
            val l = line(sev, "18:12:30.100", "cùng một cảnh báo")
            assertEquals(l, KachiLog.throttled(l, 0, t), "mức $sev phải qua lần đầu")
            assertEquals(l, KachiLog.throttled(l, 1, t), "mức $sev lặp lại VẪN phải qua — nó đang lặp là thông tin")
        }
    }

    @Test
    fun `dong D I lap trong cua so bi bo, va lan ghi ke mang dau vet`() {
        val t = LogLineThrottle(windowMs = 10_000L)
        val a = line('D', "18:12:30.100", "getTemprature temprature is: 24")
        // Cùng câu chữ, KHÁC dấu thời gian ⇒ phải cùng một khoá (đây là chỗ dễ làm sai nhất).
        val aLater = line('D', "18:12:35.900", "getTemprature temprature is: 24")
        assertEquals(a, KachiLog.throttled(a, 0, t))
        assertNull(KachiLog.throttled(aLater, 5_800, t), "cùng nội dung, khác giờ ⇒ vẫn là dòng trùng")
        assertEquals(
            line('D', "18:12:40.100", "getTemprature temprature is: 24") + "   [+1 lặp]",
            KachiLog.throttled(line('D', "18:12:40.100", "getTemprature temprature is: 24"), 10_000, t),
            "hết cửa sổ ⇒ ghi lại, kèm số dòng đã bỏ",
        )
    }

    @Test
    fun `gia tri DOI thi qua ngay`() {
        val t = LogLineThrottle(windowMs = 10_000L)
        val at24 = line('D', "18:12:30.100", "getTemprature temprature is: 24")
        val at25 = line('D', "18:12:30.900", "getTemprature temprature is: 25")
        assertEquals(at24, KachiLog.throttled(at24, 0, t))
        assertEquals(at25, KachiLog.throttled(at25, 800, t), "24 → 25 là dữ kiện: không được chờ hết cửa sổ")
    }

    /** logcat chèn dòng mốc / cắt dòng: không được ném, và (từ soát Pass 3) đi thẳng vì không phải đầu bản ghi. */
    @Test
    fun `dong ngan hong dinh dang khong lam no`() {
        val t = LogLineThrottle(windowMs = 10_000L)
        assertEquals("", KachiLog.throttled("", 0, t))
        assertEquals("--------- beginning of main", KachiLog.throttled("--------- beginning of main", 0, t))
        assertEquals("--------- beginning of main", KachiLog.throttled("--------- beginning of main", 1, t))
        assertEquals("x", KachiLog.throttled("x", 2, t))
    }

    /**
     * [SOÁT Pass 3 · P2 · 2026-09-26] **Vết gọi của một ngoại lệ không được bị cắt giữa.**
     *
     * `Log.w(TAG, "gửi VM_BUBBLE_POS lỗi", it)` ra một dòng đầu mức `W` cộng N dòng `\tat …` **không có dấu thời
     * gian, không có mức**. Hai ngoại lệ khác nhau trong cùng cửa sổ thường trùng phần ĐUÔI vết gọi, nên nếu dòng
     * phụ đi qua bộ tiết chế thì bản ghi thứ hai còn cái đầu mà mất khúc đuôi — đúng thứ mà luật *"W/E/F/A không
     * bao giờ bị bỏ"* lập ra để tránh, chỉ ở một dòng khác.
     */
    @Test
    fun `dong phu cua vet goi ngoai le KHONG BAO GIO bi bo`() {
        val t = LogLineThrottle(windowMs = 10_000L)
        val head = line('W', "18:12:30.100", "gửi VM_BUBBLE_POS lỗi")
        val frame = "\tat java.lang.Thread.run(Thread.java:919)"
        assertEquals(head, KachiLog.throttled(head, 0, t))
        assertEquals(frame, KachiLog.throttled(frame, 1, t))
        // Ngoại lệ THỨ HAI, 2 s sau, khác chỗ ném nhưng trùng đuôi vết gọi: cả hai dòng phải còn nguyên.
        val head2 = line('E', "18:12:32.100", "mở camera lỗi")
        assertEquals(head2, KachiLog.throttled(head2, 2_000, t))
        assertEquals(frame, KachiLog.throttled(frame, 2_001, t), "đuôi vết gọi trùng ⇒ vẫn phải ghi, nếu không vết gọi bị cắt giữa")
        // Và một dòng D thường vẫn bị tiết chế như trước — bản vá này KHÔNG mở cổng cho dòng lặp thật.
        val noise = line('D', "18:12:33.000", "getTemprature temprature is: 24")
        assertEquals(noise, KachiLog.throttled(noise, 3_000, t))
        assertNull(KachiLog.throttled(line('D', "18:12:34.000", "getTemprature temprature is: 24"), 4_000, t))
    }
}
