package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceSpeakerSelector.VoiceSpeakerProbe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V1 pha NÓI · BÀI KHOÁ LUẬT CHỌN MÁY ĐỌC ═════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-feedback.html` **R2**. Thuần JVM (không Robolectric): [VoiceSpeakerSelector] cố ý
 * không `import android.*` đúng để bài này chạy được off-car.
 *
 * ## ⚠ Bài này sinh ra từ một phép ĐO, không từ suy luận
 * [ĐO] 2026-09-15, máy ảo `emulator-5554`: `com.google.android.tts` **có mặt** (`pm list packages`, và
 * `cmd package query-services -a android.intent.action.TTS_SERVICE` trả về `GoogleTTSService` đang bật), nhưng
 * `/data/data/com.google.android.tts/app_voices` **rỗng** và `settings get secure tts_default_synth` = `null`.
 *
 * Tức phép kiểm *"máy có engine đọc không"* trả **CÓ** trên đúng chiếc máy **không đọc được tiếng nào**. Đó là
 * lý do luật ở đây hỏi `isLanguageAvailable`, và hỏi ở ngưỡng `>= LANG_AVAILABLE` chứ không phải `!= NOT_SUPPORTED`.
 */
class VoiceSpeakerSelectorTest {

    // ══ (1) NGƯỠNG `isLanguageAvailable` — chỗ dễ sai nhất ════════════════════════════════════════════════

    @Test
    fun `thieu goi giong -- LANG_MISSING_DATA -- KHONG duoc coi la dung duoc`() {
        // −1 = LANG_MISSING_DATA: `speak()` vẫn trả SUCCESS mà không phát ra tiếng nào. Xem KDoc lớp.
        val p = VoiceSpeakerProbe(androidLangStatus = -1, sherpaVoiceReady = false)
        assertFalse(VoiceSpeakerSelector.androidUsable(p))
        assertEquals(VoiceSpeakerKind.NONE, VoiceSpeakerSelector.choose(p))
    }

    @Test
    fun `khong ho tro -- LANG_NOT_SUPPORTED -- cung khong dung duoc`() {
        val p = VoiceSpeakerProbe(androidLangStatus = -2, sherpaVoiceReady = false)
        assertFalse(VoiceSpeakerSelector.androidUsable(p))
    }

    @Test
    fun `chua dung xong dich vu -- null -- khong duoc coi la co giong`() {
        // Máy đọc của hệ thống dựng BẤT ĐỒNG BỘ: `null` là trạng thái thật trong vài trăm ms đầu, và nó phải
        // được hiểu là "chưa biết" chứ không phải "có" — đoán "có" là một câu không ai nghe thấy.
        val p = VoiceSpeakerProbe(androidLangStatus = null, sherpaVoiceReady = false)
        assertFalse(VoiceSpeakerSelector.androidUsable(p))
        assertEquals(VoiceSpeakerKind.NONE, VoiceSpeakerSelector.choose(p))
    }

    @Test
    fun `ca ba muc CO giong deu dung duoc`() {
        // LANG_AVAILABLE(0) · LANG_COUNTRY_AVAILABLE(1) · LANG_COUNTRY_VAR_AVAILABLE(2)
        listOf(0, 1, 2).forEach { st ->
            val p = VoiceSpeakerProbe(androidLangStatus = st, sherpaVoiceReady = false)
            assertTrue(VoiceSpeakerSelector.androidUsable(p), "isLanguageAvailable=$st phải dùng được")
            assertEquals(VoiceSpeakerKind.ANDROID_TTS, VoiceSpeakerSelector.choose(p))
        }
    }

    @Test
    fun `nguong khop dung hang cua nen tang`() {
        assertEquals(
            0, VoiceSpeakerSelector.LANG_AVAILABLE,
            "ngưỡng lệch khỏi TextToSpeech.LANG_AVAILABLE ⇒ nhánh chọn sai ở mọi máy",
        )
    }

    // ══ (2) THỨ TỰ BA ĐƯỜNG ═══════════════════════════════════════════════════════════════════════════════

    @Test
    fun `khong co giong he thong thi dung goi offline`() {
        val p = VoiceSpeakerProbe(androidLangStatus = -1, sherpaVoiceReady = true)
        assertEquals(VoiceSpeakerKind.SHERPA_OFFLINE, VoiceSpeakerSelector.choose(p))
    }

    @Test
    fun `ca hai cung dung duoc thi giong he thong thang -- 0 MB dia, 0 MB RAM`() {
        val p = VoiceSpeakerProbe(androidLangStatus = 1, sherpaVoiceReady = true)
        assertEquals(VoiceSpeakerKind.ANDROID_TTS, VoiceSpeakerSelector.choose(p))
    }

    @Test
    fun `nguoi dung uu tien offline thi goi offline thang`() {
        val p = VoiceSpeakerProbe(androidLangStatus = 1, sherpaVoiceReady = true, preferOffline = true)
        assertEquals(VoiceSpeakerKind.SHERPA_OFFLINE, VoiceSpeakerSelector.choose(p))
    }

    @Test
    fun `uu tien offline nhung chua lap goi thi van lui ve giong he thong`() {
        // Cờ ưu tiên KHÔNG được thắng một phép đo: gói chưa có trên đĩa thì chọn nó là chọn sự im lặng.
        val p = VoiceSpeakerProbe(androidLangStatus = 1, sherpaVoiceReady = false, preferOffline = true)
        assertEquals(VoiceSpeakerKind.ANDROID_TTS, VoiceSpeakerSelector.choose(p))
    }

    @Test
    fun `khong co duong nao thi KHONG doc -- va do khong phai loi`() {
        val p = VoiceSpeakerProbe(androidLangStatus = null, sherpaVoiceReady = false)
        assertEquals(VoiceSpeakerKind.NONE, VoiceSpeakerSelector.choose(p))
    }

    // ══ (3) MÁY ĐỌC IM LẶNG — đường lùi phải THẬT SỰ không làm gì ═════════════════════════════════════════

    @Test
    fun `SilentSpeaker khong bao gio bao la doc duoc`() {
        assertFalse(SilentSpeaker.available())
        assertFalse(SilentSpeaker.speak("Đã đặt nhiệt độ 24"))
        assertEquals(VoiceSpeakerKind.NONE, SilentSpeaker.kind)
        // Gọi thừa phải an toàn: `VoiceSession.cancel`/`stop` gọi vô điều kiện.
        SilentSpeaker.stop()
        SilentSpeaker.stop()
        SilentSpeaker.shutdown()
    }

    // ══ (4) GIỌNG PHẢN HỒI — MẶC ĐỊNH PIPER (voice-clone R6) ══════════════════════════════════════════════

    @Test
    fun `mac dinh la Piper -- so 1`() {
        assertEquals(1, VoiceSpeakerSelector.FEEDBACK_PIPER)
        assertEquals(2, VoiceSpeakerSelector.FEEDBACK_CHILD)
    }

    @Test
    fun `mac dinh KHONG dung giong be du goi da san sang`() {
        // Owner chốt: giọng bé chỉ là lựa chọn. Giá trị mặc định (Piper) không bao giờ rẽ sang clip.
        assertFalse(VoiceSpeakerSelector.usesChildVoice(VoiceSpeakerSelector.FEEDBACK_PIPER, childReady = true))
    }

    @Test
    fun `chon giong be VA goi san sang thi moi dung clip`() {
        assertTrue(VoiceSpeakerSelector.usesChildVoice(VoiceSpeakerSelector.FEEDBACK_CHILD, childReady = true))
    }

    @Test
    fun `chon giong be nhung goi chua co thi van Piper`() {
        assertFalse(VoiceSpeakerSelector.usesChildVoice(VoiceSpeakerSelector.FEEDBACK_CHILD, childReady = false))
    }

    @Test
    fun `gia tri la thi coi nhu Piper -- khong bao gio lo bat giong be`() {
        // Một pref rác (0 · 99 · số âm) còn sót không được lỡ bật giọng bé.
        assertFalse(VoiceSpeakerSelector.usesChildVoice(0, childReady = true))
        assertFalse(VoiceSpeakerSelector.usesChildVoice(99, childReady = true))
        assertFalse(VoiceSpeakerSelector.usesChildVoice(-1, childReady = true))
    }
}
