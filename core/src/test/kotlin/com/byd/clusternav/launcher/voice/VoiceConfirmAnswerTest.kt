package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * ═══ C1 · CỔNG XÁC NHẬN PHẢI NHẬN CÁCH NGƯỜI TA TRẢ LỜI THẬT ════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-feedback.html` **R3**. Bài này khoá quyết định của owner 2026-09-16, và nó khoá
 * **hai chiều** — vì cả hai chiều đều đã hỏng một lần:
 *
 *  • [ĐO xe 2026-09-16] người lái trả lời hộp xác nhận bằng đúng chữ *"ừ"*. Bảng cũ cố ý bỏ từ một âm tiết, nên
 *    câu ấy rơi vào `null` ⇒ **KHÔNG** ⇒ Kachi im lặng huỷ việc. Owner đọc cái im lặng đó thành *"voice hỏng"*.
 *    Một cổng an toàn mà không nhận câu trả lời tự nhiên thì người ta bỏ cả tính năng, chứ không đổi cách nói.
 *  • Chiều ngược lại vẫn phải đứng: cổng này gác *"mở khoá cửa"* / *"hạ hết kính"*, nên một câu DÀI có chứa
 *    tiếng ậm ừ tuyệt đối không được thành ĐỒNG Ý (luật *"cả câu đúng bằng một cụm"*, xem [VoiceLexicon.confirmAnswer]).
 */
class VoiceConfirmAnswerTest {

    /** Mười cách nói ĐỒNG Ý mà owner liệt kê tên (2026-09-16) — đủ cả dạng một âm tiết. */
    @Test
    fun `moi tu dong nghia CO deu la dong y`() {
        listOf(
            "ừ", "vâng", "có", "được", "ok", "OK", "đúng rồi", "làm đi", "đồng ý", "xác nhận",
            "yes", "confirm",
        ).forEach { assertEquals(true, VoiceLexicon.confirmAnswer(it), "câu «$it» phải là ĐỒNG Ý") }
    }

    /** Từ chối giữ nguyên — không có từ nào nhảy nhóm khi bảng ĐỒNG Ý nở ra. */
    @Test
    fun `cach noi KHONG van la tu choi`() {
        listOf("huỷ", "hủy bỏ", "không", "thôi", "no", "cancel")
            .forEach { assertEquals(false, VoiceLexicon.confirmAnswer(it), "câu «$it» phải là TỪ CHỐI") }
    }

    /**
     * ⚠⚠ Luật **cả câu đúng bằng một cụm** là thứ giữ cổng này, không phải độ dài của bảng.
     *
     * Nới bảng ĐỒNG Ý mà đánh mất luật này thì mọi câu có chữ *"có"* ở giữa đều mở khoá xe — tức đổi một lỗi
     * *"không nhận câu trả lời"* lấy một lỗi hoàn toàn không hoàn lại được.
     */
    @Test
    fun `cau DAI chua tieng am u KHONG phai la cau tra loi`() {
        listOf(
            "ừ thì bật đèn đọc", "có mở cửa không", "đồng ý rồi bật đèn", "được rồi để sau", "đúng là vậy",
        ).forEach { assertNull(VoiceLexicon.confirmAnswer(it), "câu «$it» KHÔNG phải câu trả lời ⇒ phải là null") }
        assertNull(VoiceLexicon.confirmAnswer(""), "câu rỗng ⇒ null, chỗ gọi tự hiểu là KHÔNG")
    }

    /**
     * Va chạm `"đúng"` ↔ `"dừng"` (cùng bỏ dấu ra `dung`): chốt 2026-09-16 — **bỏ `"đúng"` đứng một mình**, giữ
     * `"đúng rồi"`. Trong lượt nghe xác nhận, *"dừng"* (ý người lái là THÔI) không bao giờ được thành ĐỒNG Ý.
     */
    @Test
    fun `dung mot minh khong phai dong y — vi trung dung cua dung nhac`() {
        assertEquals(null, VoiceLexicon.confirmAnswer("đúng"))
        assertEquals(null, VoiceLexicon.confirmAnswer("dừng"))
        assertEquals(true, VoiceLexicon.confirmAnswer("đúng rồi"))
    }
}
