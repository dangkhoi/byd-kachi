package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V3 · R1 — THỨ TỰ NGUỒN MICRO ═══════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` R1. Bài này khoá lại **một đổi hành vi có số đo**: tới 1.65
 * `VoiceCapture` mở micro bằng `VOICE_RECOGNITION` trước; [ĐO xe 2026-09-16] nguồn đó cho **đỉnh 136–255/32767 ·
 * rms 30–50** (gần câm) 3/4 lượt, còn `MIC` — đúng nguồn Kiki dùng — cho **đỉnh 4429 · rms 237**.
 *
 * Thử làm nó ĐỎ: đảo [VoiceMicSource.DEFAULT_ORDER] về `[6, 1]` ⇒ bài đầu tiên đỏ ngay.
 */
class VoiceMicSourceTest {

    @Test
    fun `mac dinh thu MIC truoc — do tren xe 2026-09-16`() {
        assertEquals(VoiceMicSource.MIC, VoiceMicSource.DEFAULT_ORDER.first())
        assertEquals(
            listOf(VoiceMicSource.MIC, VoiceMicSource.VOICE_COMMUNICATION, VoiceMicSource.VOICE_RECOGNITION),
            VoiceMicSource.order(VoiceMicSource.PREF_AUTO),
        )
    }

    @Test
    fun `ep mot nguon thi no dung DAU, nhung duong lui van con`() {
        val order = VoiceMicSource.order(VoiceMicSource.VOICE_RECOGNITION)
        assertEquals(VoiceMicSource.VOICE_RECOGNITION, order.first())
        // ⚠ Đây là điểm quan trọng nhất của bài: ép ⇒ *"thử cái này TRƯỚC"*, KHÔNG phải *"chỉ dùng cái này"*.
        // Một ROM có thể dựng `AudioRecord` ở `STATE_UNINITIALIZED` mà không ném; ép cứng rồi bỏ cuộc nghĩa là
        // một lần chọn sai trong Cài đặt làm câm hẳn tính năng.
        assertEquals(VoiceMicSource.DEFAULT_ORDER.size, order.size, "ép nguồn vẫn phải còn đủ đường lùi")
        assertEquals(VoiceMicSource.DEFAULT_ORDER.toSet(), order.toSet())
    }

    @Test
    fun `gia tri la bi bo qua — pref hong khong duoc lam cam micro`() {
        assertEquals(VoiceMicSource.DEFAULT_ORDER, VoiceMicSource.order(99))
        assertEquals(VoiceMicSource.DEFAULT_ORDER, VoiceMicSource.order(-1))
    }

    @Test
    fun `moi lua chon trong Cai dat deu co nhan, va nhan cua nguon la TEN HANG Android`() {
        assertTrue(VoiceMicSource.PREF_AUTO in VoiceMicSource.CHOICES)
        VoiceMicSource.DEFAULT_ORDER.forEach { assertTrue(it in VoiceMicSource.CHOICES) }
        VoiceMicSource.CHOICES.forEach { assertTrue(VoiceMicSource.sourceName(it).isNotBlank()) }
        // Không dịch: hai lượt đo trên hai máy khác ngôn ngữ phải grep được bằng MỘT chuỗi (cùng luật mã lỗi cầu).
        assertEquals("MIC (1)", VoiceMicSource.sourceName(VoiceMicSource.MIC))
        assertEquals("VOICE_RECOGNITION (6)", VoiceMicSource.sourceName(VoiceMicSource.VOICE_RECOGNITION))
        assertEquals("VOICE_COMMUNICATION (7)", VoiceMicSource.sourceName(VoiceMicSource.VOICE_COMMUNICATION))
    }

    @Test
    fun `ba hang dung bang so cua MediaRecorder AudioSource`() {
        // Chốt bằng SỐ vì `:core` không được import `android.*`: sai một con số ở đây là mở nhầm nguồn trên xe.
        assertEquals(1, VoiceMicSource.MIC)
        assertEquals(6, VoiceMicSource.VOICE_RECOGNITION)
        assertEquals(7, VoiceMicSource.VOICE_COMMUNICATION)
    }
}
