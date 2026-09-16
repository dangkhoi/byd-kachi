package com.byd.clusternav.launcher.voice

import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ OQ4 · HỢP ĐỒNG *"ĐỌC XONG"* CỦA [VoiceSpeaker] ═════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-feedback.html` **OQ4** (owner duyệt 2026-09-16): câu hỏi xác nhận phải được đọc
 * **xong** rồi mới mở micro. Cổng an toàn của cả tính năng giọng nói nằm sau mốc ấy, nên mốc ấy phải có một bài
 * canh thuần, chạy off-car.
 *
 * ## Vì sao bài này khoá *"luôn gọi"* chứ không *"gọi khi thành công"*
 * `VoiceSession.confirm` mở micro **trong** `onDone`. Một bản cài đặt nào đó nuốt `onDone` ở nhánh hỏng (engine
 * từ chối câu, máy không có giọng) là cổng xác nhận **chết im**: tấm chữ hiện câu hỏi, micro không bao giờ mở,
 * và người lái chỉ còn đường bấm nút — trên một chiếc xe đang chạy. Trần `VoiceSession.ASK_ALOUD_CAP_MS` là lưới
 * an toàn cuối, không phải cái thay thế cho hợp đồng này (và nó là một con số còn đổi được sau phép đo trên xe —
 * spec §7 OQ7 — nên bài này cố ý KHÔNG nhắc giá trị).
 */
class VoiceSpeakerDoneContractTest {

    /** Máy đọc giả: [ok] quyết định [speak] trả gì. KHÔNG override bản hai tham số ⇒ dùng bản MẶC ĐỊNH. */
    private class Fake(private val ok: Boolean) : VoiceSpeaker {
        override val kind: VoiceSpeakerKind = VoiceSpeakerKind.ANDROID_TTS
        var spoken: String? = null
        override fun available(): Boolean = true
        override fun speak(text: String): Boolean { spoken = text; return ok }
        override fun stop() = Unit
        override fun shutdown() = Unit
    }

    @Test
    fun `ban mac dinh doc roi bao xong ngay`() {
        val s = Fake(ok = true)
        val n = AtomicInteger(0)
        assertTrue(s.speak("Mở khoá cửa?") { n.incrementAndGet() })
        assertEquals("Mở khoá cửa?", s.spoken)
        assertEquals(1, n.get(), "phải gọi onDone đúng MỘT lần")
    }

    /**
     * ⚠ Nhánh HỎNG cũng phải báo xong — vế (1) của hợp đồng.
     *
     * Đây là ca mà một bản cài đặt dễ quên nhất, và cũng là ca hậu quả nặng nhất: không gọi ⇒ micro không mở.
     */
    @Test
    fun `doc khong duoc thi VAN bao xong`() {
        val n = AtomicInteger(0)
        assertFalse(Fake(ok = false).speak("Mở khoá cửa?") { n.incrementAndGet() })
        assertEquals(1, n.get(), "trả false mà nuốt luôn onDone ⇒ cổng xác nhận chết im")
    }

    /** Máy không có giọng nào: *"đọc xong"* là ngay bây giờ — xe không có gói giọng vẫn phải mở được micro. */
    @Test
    fun `SilentSpeaker bao xong ngay`() {
        val n = AtomicInteger(0)
        assertFalse(SilentSpeaker.speak("Mở khoá cửa?") { n.incrementAndGet() })
        assertEquals(1, n.get())
    }
}
