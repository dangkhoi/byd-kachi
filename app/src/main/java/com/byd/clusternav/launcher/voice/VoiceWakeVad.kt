package com.byd.clusternav.launcher.voice

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * ═══ Silero VAD **riêng** cho bộ nghe "Hey Kachi" — chỉ chấm xác suất, không gom đoạn ════════════════════════════
 *
 * VOICE-WAKE-CPU-BUDGET (2026-10-15). Cấp xác suất tiếng người cho [VoiceWakeSpeechGate] (`:core`, số học + luật).
 *
 * ## Vì sao không dùng [VoiceVad]
 *  • [VoiceVad] bọc `acceptWaveform` (bộ gom đoạn đầy đủ). [ĐO source sherpa-onnx v1.13.8
 *    `csrc/voice-activity-detector.cc` AcceptWaveform] khi đang trong đoạn tiếng, MỖI lượt nạp chép lại cả đoạn đang
 *    mở (`cur_segment_.samples = buffer_.Get(start_, …)`) — nghe nền liên tục dưới nhạc có lời là một đoạn kéo dài
 *    tới hàng chục giây ⇒ chép MB mỗi khung. Wake chỉ cần xác suất ⇒ `Vad.compute(window)` (= `SileroVadModel::Run`,
 *    `csrc/silero-vad-model.cc:516`), không bộ đệm, không đoạn.
 *  • Instance chung của [VoiceVad] là của phiên lệnh; bộ nghe nền giữ nó mãi thì mọi phiên lệnh phải dựng VAD riêng
 *    (+ trễ "bấm → mic mở" mà B1.1 đã gỡ). Wake dựng cái của nó.
 *
 * Mô hình: cùng asset [VoiceVad.ASSET_NAME] (0,64 MB, trong APK). [ĐO 15/10 `strings` tệp asset] có đầu vào `h`/`c`
 * ⇒ Silero **v4** (`silero-vad-model.cc:208-211`: 4 vào/3 ra ⇒ `is_v5_ = false`, `window_overlap_ = 0`) ⇒ một cửa sổ
 * đúng 512 mẫu, trạng thái LSTM giữ giữa các lượt `compute`. ⚠ Đổi asset sang v5 thì `compute(512)` thiếu 64 mẫu
 * ngữ cảnh — vẫn chạy nhưng phải đo lại.
 *
 * 1 luồng onnx. Không thread-safe — chỉ [VoiceWakeAsr] gọi, dưới khoá của nó.
 */
internal class VoiceWakeVad private constructor(private val vad: Vad) {

    /** Xác suất tiếng người của một cửa sổ 512 mẫu; `null` khi native ném (cổng chuyển sang MỞ, xem [VoiceWakeSpeechGate]). */
    fun prob(window: FloatArray): Float? =
        runCatching { vad.compute(window) }.onFailure { Log.w(TAG, "VAD wake compute hỏng — cổng tiếng người MỞ", it) }.getOrNull()

    fun release() { runCatching { vad.release() } }

    companion object {
        private const val TAG = "KachiWakeVad"

        /** `null` khi không dựng được (asset/ROM) ⇒ [VoiceWakeAsr] chạy chỉ với cổng RMS như trước, có log. */
        fun open(ctx: Context): VoiceWakeVad? {
            val config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = VoiceVad.ASSET_NAME,
                    threshold = VoiceVadTrim.THRESHOLD,
                    windowSize = VoiceVadTrim.WINDOW_SIZE,
                ),
                sampleRate = 16_000, numThreads = 1, provider = "cpu", debug = false,
            )
            return runCatching { VoiceWakeVad(Vad(ctx.applicationContext.assets, config)) }
                .onFailure { Log.w(TAG, "không dựng được VAD wake — chỉ còn cổng RMS", it) }.getOrNull()
        }
    }
}
