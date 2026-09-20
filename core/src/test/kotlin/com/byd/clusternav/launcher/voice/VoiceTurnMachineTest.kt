package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private typealias P = VoiceTurnPhase

/**
 * B1 (1.70) — máy trạng thái voice thuần. Khoá: luật chuyển hợp lệ + bất biến chống-loop (OQ5 owner).
 * Đây là thứ chùm-6-cờ cũ KHÔNG kiểm được off-car (chúng cần thiết bị để dựng ra race).
 */
class VoiceTurnMachineTest {
    private val M = VoiceTurnMachine

    @Test
    fun `chuyen hop le theo mot chuyen di dien hinh`() {
        // IDLE → LISTENING → DECODING → EXECUTING → FOLLOW_UP → LISTENING → CLOSING → IDLE
        assertEquals(P.LISTENING, M.next(P.IDLE, P.LISTENING))
        assertEquals(P.DECODING, M.next(P.LISTENING, P.DECODING))
        assertEquals(P.EXECUTING, M.next(P.DECODING, P.EXECUTING))
        assertEquals(P.FOLLOW_UP, M.next(P.EXECUTING, P.FOLLOW_UP))
        assertEquals(P.LISTENING, M.next(P.FOLLOW_UP, P.LISTENING))
        assertEquals(P.CLOSING, M.next(P.LISTENING, P.CLOSING))
        assertEquals(P.IDLE, M.next(P.CLOSING, P.IDLE))
    }

    @Test
    fun `xac nhan dong y chay viec, tu choi thi dong`() {
        assertEquals(P.EXECUTING, M.next(P.CONFIRMING, P.EXECUTING), "đồng ý ⇒ chạy việc")
        assertEquals(P.CLOSING, M.next(P.CONFIRMING, P.CLOSING), "từ chối/hết giờ ⇒ đóng")
        assertEquals(P.LISTENING, M.next(P.CONFIRMING, P.LISTENING), "mở lượt nghe câu trả lời")
    }

    @Test
    fun `hoi lai va hoi thoai deu quay ve LISTENING`() {
        assertEquals(P.CLARIFYING, M.next(P.DECODING, P.CLARIFYING), "không hiểu ⇒ hỏi lại")
        assertEquals(P.LISTENING, M.next(P.CLARIFYING, P.LISTENING))
        assertEquals(P.LISTENING, M.next(P.FOLLOW_UP, P.LISTENING))
    }

    /**
     * ═══ HỒI QUY [ĐO xe 2026-09-20 §5] — DỰNG LẠI ĐÚNG CHUỖI PHA CỦA MỘT LƯỢT HỎI-LẠI THẬT ══════════════════
     *
     * Nhật ký xe: `quyết định: … ⇒ không hiểu: NO_VERB` rồi `pha: chuyển KHÔNG hợp lệ EXECUTING ⇒ DECODING`.
     * Gốc: bảng cạnh thiếu **EXECUTING → CLARIFYING**, mà cổng hỏi-lại của `VoiceSession.execute` chạy SAU
     * `go(DECODING); go(EXECUTING)` ⇒ pha xuất phát của lượt hỏi lại là EXECUTING, không phải DECODING.
     *
     * Bài trên (`hoi lai va hoi thoai…`) **không** bắt được vì nó chỉ hỏi cạnh DECODING → CLARIFYING — nó soi đúng
     * cái bảng dữ liệu đang sai chứ không soi đường đi thật. Bài này đi **từng bước như `:app` gọi**, nên nó đỏ ngay
     * ở bước 4 khi cạnh bị gỡ, và nó cũng khoá luôn hai mốc kế (mở mic + lượt trả lời gọi `execute` lần hai).
     */
    @Test
    fun `chuoi pha cua mot luot HOI LAI that - khong buoc nao bi tu choi`() {
        // Lượt 1: bấm mic → nghe → giải mã → thi hành (VoiceSession.start + execute).
        var at = P.IDLE
        listOf(P.LISTENING, P.DECODING, P.EXECUTING).forEach { to ->
            at = M.next(at, to) ?: error("bước $at ⇒ $to phải hợp lệ")
        }
        // `clarifyAsk` != null ⇒ askAgain: ĐÂY là bước đã trượt trên xe.
        at = M.next(at, P.CLARIFYING) ?: error("EXECUTING ⇒ CLARIFYING phải hợp lệ (gốc lỗi phiên-thoại-chết)")
        // listenAgain: về LISTENING rồi mới được mở mic (bất biến chống-loop).
        at = M.next(at, P.LISTENING) ?: error("CLARIFYING ⇒ LISTENING phải hợp lệ")
        assertTrue(M.canOpenMic(at), "lượt hỏi lại phải mở được mic sau khi về LISTENING")
        // Câu trả lời về ⇒ execute lần hai: dòng `EXECUTING ⇒ DECODING` trong log xe sinh ra ở đây.
        at = M.next(at, P.DECODING) ?: error("LISTENING ⇒ DECODING phải hợp lệ ở lượt trả lời")
        assertEquals(P.EXECUTING, M.next(at, P.EXECUTING), "lượt trả lời phải thi hành được")
    }

    /** Hỏi lại xong mà vẫn không hiểu ⇒ hỏi lại lần hai: cạnh EXECUTING → CLARIFYING phải dùng LẠI được. */
    @Test
    fun `hoi lai duoc LAN HAI trong cung mot phien`() {
        var at = P.EXECUTING
        repeat(2) {
            at = M.next(at, P.CLARIFYING) ?: error("lượt hỏi lại thứ ${it + 1} bị từ chối")
            at = M.next(at, P.LISTENING) ?: error("không về được LISTENING")
            at = M.next(at, P.DECODING) ?: error("không giải mã được câu trả lời")
            at = M.next(at, P.EXECUTING) ?: error("không thi hành được")
        }
        assertEquals(P.EXECUTING, at)
    }

    @Test
    fun `CLOSING den duoc tu MOI pha - huy bat cu luc nao`() {
        P.entries.filter { it != P.CLOSING }.forEach { from ->
            assertTrue(M.canGo(from, P.CLOSING), "phải huỷ được từ $from")
        }
        assertFalse(M.canGo(P.CLOSING, P.CLOSING), "đã CLOSING thì không tự-lặp CLOSING")
    }

    @Test
    fun `CLOSING chi ve IDLE - la ho hut`() {
        P.entries.filter { it != P.IDLE }.forEach { to ->
            assertFalse(M.canGo(P.CLOSING, to), "CLOSING không được sang $to (chỉ IDLE)")
        }
        assertTrue(M.canGo(P.CLOSING, P.IDLE))
    }

    @Test
    fun `chuyen khong hop le tra null - khong chuyen len`() {
        assertNull(M.next(P.IDLE, P.EXECUTING), "IDLE không nhảy thẳng sang EXECUTING")
        assertNull(M.next(P.LISTENING, P.EXECUTING), "phải qua DECODING")
        assertNull(M.next(P.DECODING, P.LISTENING), "giải mã xong không quay lại nghe thẳng")
        assertNull(M.next(P.IDLE, P.FOLLOW_UP))
    }

    @Test
    fun `bat bien chong-loop CHI LISTENING duoc mo mic`() {
        P.entries.forEach { phase ->
            assertEquals(phase == P.LISTENING, M.canOpenMic(phase), "chỉ LISTENING mở mic, $phase thì không")
        }
    }

    @Test
    fun `ba pha noi khong duoc mo mic thang - phai ve LISTENING truoc`() {
        // CONFIRMING/CLARIFYING/FOLLOW_UP là nơi vòng lặp hoang sinh ra ⇒ không được tự mở mic;
        // phải chuyển VỀ LISTENING (tiêu một suất trần lượt nối ở :app) rồi mới mở.
        listOf(P.CONFIRMING, P.CLARIFYING, P.FOLLOW_UP).forEach {
            assertFalse(M.canOpenMic(it), "$it không được mở mic thẳng")
            assertTrue(M.canGo(it, P.LISTENING), "$it phải chuyển về LISTENING được")
        }
    }

    @Test
    fun `isDone dung cho CLOSING va IDLE`() {
        assertTrue(M.isDone(P.CLOSING)); assertTrue(M.isDone(P.IDLE))
        listOf(P.LISTENING, P.DECODING, P.EXECUTING, P.CONFIRMING, P.CLARIFYING, P.FOLLOW_UP).forEach {
            assertFalse(M.isDone(it), "$it chưa xong")
        }
    }
}
