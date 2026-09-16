package com.byd.clusternav.launcher.voice

import android.content.Context
import android.util.Log

/**
 * ═══ V1.1 · LƯỢT 2 — bộ giải mã **TỰ DO** trên đúng khúc tiếng vừa thu (R16) ═════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R16**. Tách khỏi [VoiceSession] ở **voice pha 2 (2026-09-16)** vì trần
 * 500 dòng (CLAUDE.md §4.1): OQ4 (*"đọc xong câu hỏi rồi mới mở micro"*) thêm một vai mới vào phiên nghe, và
 * tệp kia đã sát trần.
 *
 * ## Vì sao cắt ĐÚNG khối này
 * Nó là **một vai trọn vẹn và không có trạng thái**: vào là một khúc PCM đã thu xong + câu mà lượt 1 nghe ra,
 * ra là một chuỗi chữ. Nó không đụng tấm chữ, không đụng micro, không đụng cổng xác nhận, không đọc một `Atomic`
 * nào của phiên — nên đem ra ngoài là **hết** phụ thuộc, không phải nửa vời. Mọi vai khác của [VoiceSession]
 * đều bám vào vòng đời của phiên.
 *
 * ## Điều kiện chạy — hai vế, và bài canh đọc được cả hai từ mã
 * Chỉ chạy khi [VoiceOpenVocab.triggerOf] khác `null`, tức lượt 1 nghe ra *"&lt;cụm kích hoạt&gt; `[unk]`…"*.
 * Câu lệnh xe thường (*"bật đèn đọc"*) không có `[unk]` nào ⇒ **không tốn lượt giải mã nào**, và cũng không có
 * một chuỗi tự do kém chính xác nào len được vào đường điều khiển xe.
 *
 * Hỏng ở bất kỳ đâu (không dựng được bộ giải mã, không nghe ra đuôi) ⇒ **lùi về bản ngữ pháp** đã bỏ `[unk]`.
 * Người lái nhận được *"Phát nhạc"* thay vì *"Tìm bài «Diễm Xưa»"* — ít hơn, nhưng không sai.
 */
internal object VoiceFreeTail {

    private const val TAG = "KachiVoiceSession"

    /**
     * **CHẶN** ⇒ gọi trên luồng nền (chỗ gọi đang ở trong `VoiceSession.runSession`).
     *
     * @return câu hoàn chỉnh để đưa xuống `VoiceDispatcher`.
     */
    fun decode(ctx: Context, heard: VoiceCapture.Heard): String {
        val plain = VoiceOpenVocab.stripUnk(heard.text)
        val trigger = VoiceOpenVocab.triggerOf(heard.text) ?: return plain
        if (heard.samples <= 0) return plain
        val t0 = System.currentTimeMillis()
        val free = runCatching {
            VoiceRecognizer.openFree(ctx)?.use { it.decodeAll(heard.pcm, heard.samples) }
        }.onFailure { Log.w(TAG, "lượt 2 hỏng", it) }.getOrNull().orEmpty()
        val merged = VoiceOpenVocab.merge(heard.text, free)
        Log.i(
            TAG,
            "lượt 2 (tự do) sau cụm \"${trigger.joinToString(" ")}\" trong ${System.currentTimeMillis() - t0} ms: " +
                "\"$free\" ⇒ \"${merged.text}\"",
        )
        return merged.text.ifBlank { plain }
    }
}
