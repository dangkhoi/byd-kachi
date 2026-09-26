package com.byd.clusternav.launcher.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.voiceEndpointFloorCap
import com.byd.clusternav.voiceEndpointMinSpeechMs
import com.byd.clusternav.voiceEndpointSilenceMs
import com.byd.clusternav.voiceMicSource

/**
 * ═══ THIẾT BỊ MICRO — **dựng** [AudioRecord] và bộ ngắt câu theo prefs của chiếc xe này ══════════════════════
 *
 * Tách khỏi [VoiceCapture] ở **VOICE-OPEN-TURN (2026-09-26)** vì trần 500 dòng (CLAUDE.md §4.1 ·
 * `VoiceListenWiringContractTest`): tệp kia đã 492/500 và OQ9 thêm một vai mới (giữ lượt nghe mở qua một quãng
 * ngừng) vào chính vòng đọc micro. **Pure move**, không đổi một dòng logic nào — cùng thứ tự nguồn, cùng sàn
 * đệm, cùng ba `runCatching`.
 *
 * ## Vì sao cắt ĐÚNG hai hàm này
 * Chúng là vai *"nói chuyện với phần cứng + với prefs"*: vào là một [Context], ra là một thiết bị đã mở (hoặc
 * `null`) và một bộ ngắt câu đã cấu hình. Không trạng thái, không vòng đời, không biết gì về lượt nghe — nên đem
 * ra ngoài là **hết** phụ thuộc. Phần còn lại của [VoiceCapture] (chốt một-micro, vòng đọc, phép cắt, đóng mic)
 * đều bám vào một lượt cụ thể và ở lại.
 *
 * ⚠ [VoiceCapture] vẫn là **chỗ duy nhất** mở một lượt nghe; tệp này chỉ là cánh tay dựng thiết bị của nó, và
 * `AudioRecord(` vẫn chỉ xuất hiện ở **đúng một** thân hàm trong cả pha NGHE
 * (`VoiceListenWiringContractTest.chi mot tep mo micro` + `VoiceLoopGuardWiringContractTest` canh cả hai vế).
 */
internal object VoiceCaptureDevice {

    private const val TAG = "KachiVoiceMic"

    /** Sàn đệm `AudioRecord` — một giây tiếng (xem [open]). */
    const val MIN_BUFFER_MS = 1_000

    /**
     * Micro đã mở + **nguồn thật sự thắng**.
     *
     * H2 cần con số ấy trong tệp JSON, và nó không suy lại được từ prefs: `pref` chỉ nói *"thử cái này trước"*,
     * còn vòng lùi ở trên có thể đã rơi sang nguồn thứ hai/thứ ba mà không có gì khác ghi lại. Đúng câu hỏi mà
     * một buổi so *"vì sao lượt này nghe rõ, lượt kia câm"* phải trả lời được.
     */
    data class Opened(val record: AudioRecord, val source: Int)

    /**
     * Dựng [AudioRecord]: thử `VOICE_RECOGNITION` rồi mới `MIC` — xem KDoc [VoiceCapture], quyết định (1).
     *
     * Bộ đệm lấy **gấp đôi** mức tối thiểu của ROM: mức tối thiểu là ngưỡng *"không tràn nếu đọc đúng nhịp"*,
     * mà luồng nghe của ta còn phải chạy giải mã Kaldi giữa hai lượt đọc. Thiếu chỗ đệm ⇒ mất mẫu ⇒ mất chữ,
     * và mất kiểu đó không có lỗi nào báo.
     */
    fun open(ctx: Context): Opened? {
        // Quyền RUNTIME có thể bị thu hồi sau khi phiên đã dựng ⇒ hỏi lại ngay trước khi dựng AudioRecord; thiếu ⇒ null.
        if (!VoiceCapture.micGranted(ctx)) { Log.w(TAG, "chưa có quyền RECORD_AUDIO — không mở micro"); return null }
        val rate = VoiceCapture.SAMPLE_RATE
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // 1.70 — đệm ≥ [MIN_BUFFER_MS] tiếng: [ĐO xe 2026-09-17] `min*2` = **2 560 byte = 80 ms** trên DL3, và
        // một lượt chặn 3 s (âm báo) trên luồng đọc làm rơi **toàn bộ** tiếng của khoảng đó, không lỗi nào báo.
        // Âm báo đã dời khỏi luồng đọc; đệm rộng là lớp thứ hai cho mọi lượt chặn chưa ai đo (GC, giải mã
        // partial, một app khác giành CPU).
        val floor = rate * MIN_BUFFER_MS / 1000 * 2
        val size = maxOf(if (min > 0) min * 2 else VoiceCapture.CHUNK_SAMPLES * 2 * 8, floor)
        // V3 · R1 — thứ tự nguồn từ `:core` ([VoiceMicSource]) + lựa chọn người dùng; MIC trước ([ĐO xe 2026-09-16]), ép nguồn vẫn có đường lùi.
        val pref = runCatching { Prefs.voiceMicSource(ctx) }.getOrDefault(VoiceMicSource.PREF_AUTO)
        for (source in VoiceMicSource.order(pref)) {
            // `SecurityException` tường minh: quyền có thể bị thu hồi giữa lượt hỏi ở trên và dòng này.
            val r = try {
                AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
            } catch (e: SecurityException) {
                Log.w(TAG, "quyền RECORD_AUDIO bị thu hồi giữa chừng — không mở micro", e); return null
            } catch (e: RuntimeException) {
                // Giữ nguyên hành vi hiện trường trước 2026-09-25 (`runCatching … ?: continue`): ROM từ chối nguồn
                // (IllegalArgumentException theo tài liệu, nhưng ROM lệch có thể ném loại khác) ⇒ thử nguồn sau.
                Log.w(TAG, "nguồn $source bị ROM từ chối (${e.javaClass.simpleName}: ${e.message}) — thử nguồn sau"); continue
            }
            if (r.state == AudioRecord.STATE_INITIALIZED) {
                Log.i(TAG, "micro mở bằng nguồn ${VoiceMicSource.sourceName(source)} (đệm $size byte · pref=$pref)")
                return Opened(r, source)
            }
            Log.w(TAG, "nguồn $source dựng ra AudioRecord chưa khởi tạo — thử nguồn sau")
            runCatching { r.release() }
        }
        Log.w(TAG, "không mở được micro bằng nguồn nào")
        return null
    }

    /**
     * H5 — bộ ngắt câu dựng theo **prefs của chiếc xe này**.
     *
     * `:core` phải thuần nên nó KHÔNG đọc prefs; hai con số đi vào bằng tham số dựng, đúng chỗ này. `runCatching`
     * + mặc định = hằng cũ: một lượt đọc prefs hỏng không được phép làm phiên nghe chạy **không** có bộ ngắt câu
     * (tức lùi về cái giá cố định 8 giây cho mọi câu — xem KDoc [VoiceEndpointer]).
     */
    fun endpointer(ctx: Context): VoiceEndpointer = VoiceEndpointer(
        minSpeechMs = runCatching { Prefs.voiceEndpointMinSpeechMs(ctx) }
            .getOrDefault(VoiceEndpointer.MIN_SPEECH_MS),
        hangoverMs = runCatching { Prefs.voiceEndpointSilenceMs(ctx) }
            .getOrDefault(VoiceEndpointer.HANGOVER_MS),
        floorCap = runCatching { Prefs.voiceEndpointFloorCap(ctx) }
            .getOrDefault(VoiceEndpointer.FLOOR_CAP),
    )
}
