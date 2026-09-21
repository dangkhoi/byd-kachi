package com.byd.clusternav

import android.content.Context
import com.byd.clusternav.launcher.voice.SherpaModelCatalog
import com.byd.clusternav.launcher.voice.SherpaTtsCatalog
import com.byd.clusternav.launcher.voice.VoiceEndpointer
import com.byd.clusternav.launcher.voice.VoiceSpeakerSelector
import com.byd.clusternav.launcher.voice.VoiceVadTrim

/**
 * ═══ V3 (1.66) — KHOÁ RIÊNG của đường giọng nói, tách khỏi [Prefs] ═══════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` R1 · R7 · R9, cộng **H2/H5** (2026-09-16): một công tắc nhật ký
 * lượt nói và bốn núm chỉnh bộ nghe.
 *
 * ## Vì sao tách, và vì sao vẫn là **cùng một tệp prefs**
 * [Prefs] đã sát trần 500 dòng (CLAUDE.md §4.1) và ba khoá này là **một nhóm có nghĩa riêng**, nên chúng ra
 * đây dưới dạng **hàm mở rộng của chính [Prefs]** — cùng cách `ClusterNavBridgeKeys.kt` tách khỏi
 * `ClusterNavBridge`. Bề mặt gọi không đổi một ký tự (`Prefs.voiceMicSource(ctx)`), và **ô nhớ vẫn là tệp
 * `clusternav_prefs`** cũ: mở một tệp prefs thứ hai cho ba khoá là dựng một cửa thứ hai vào cùng chỗ lưu — đúng
 * thứ `SettingsCatalog.PREFS_FILES` sinh ra để bắt.
 *
 * Cả ba là khoá **THEO XE** ([com.byd.clusternav.launcher.ProfileScope.DEVICE_KEYS] ghi lý do từng cái).
 */

/** Cùng tệp `clusternav_prefs` với mọi khoá của [Prefs] — xem KDoc trên. */
private fun voicePrefs(ctx: Context) =
    ctx.applicationContext.getSharedPreferences("clusternav_prefs", Context.MODE_PRIVATE)

private const val K_VOICE_MIC_SOURCE = "voice_mic_source"
private const val K_VOICE_CONFIRM_IDS = "voice_confirm_ids"
private const val K_VOICE_FOLLOW_UP_MS = "voice_follow_up_ms"
private const val K_VOICE_KEEP_LOG = "voice_keep_log"
private const val K_VOICE_ENDPOINT_SILENCE_MS = "voice_endpoint_silence_ms"
private const val K_VOICE_ENDPOINT_MIN_SPEECH_MS = "voice_endpoint_min_speech_ms"
private const val K_VOICE_ENDPOINT_FLOOR_CAP = "voice_endpoint_floor_cap"
private const val K_VOICE_VAD_THRESHOLD = "voice_vad_threshold"
private const val K_VOICE_TTS_SPEED = "voice_tts_speed"
private const val K_VOICE_VAD_MIN_SPEECH_MS = "voice_vad_min_speech_ms"
private const val K_VOICE_VAD_MIN_SILENCE_MS = "voice_vad_min_silence_ms"
private const val K_VOICE_BEAM = "voice_beam"
private const val K_VOICE_HOTWORD_SCORE = "voice_hotword_score"

/** 5 giây — owner **D1** 2026-09-16 (*"giữ mic 5 s"*, nâng từ đề xuất 3 s). */
const val VOICE_FOLLOW_UP_DEFAULT_MS = 5_000

/**
 * R1 — nguồn micro muốn thử TRƯỚC. `0` = để Kachi tự chọn (mặc định), còn lại là hằng
 * `MediaRecorder.AudioSource` (xem [com.byd.clusternav.launcher.voice.VoiceMicSource]).
 *
 * Có mặt để lượt xe sau **đo được từng nguồn mà không phải build lại APK**: câu hỏi còn mở là nguồn nào
 * thắng khi xe đang chạy 60–80 km/h và đang mở nhạc (owner **D5**) — thứ chỉ đo được trên đường.
 */
fun Prefs.voiceMicSource(ctx: Context): Int = voicePrefs(ctx).getInt(K_VOICE_MIC_SOURCE, 0)
fun Prefs.setVoiceMicSource(ctx: Context, v: Int) = voicePrefs(ctx).edit().putInt(K_VOICE_MIC_SOURCE, v).apply()

/**
 * R7 — mã các việc **phải hỏi lại** trước khi chạy. **Mặc định RỖNG** (owner 2026-09-16: *"cái nào nguy hiểm
 * lái xe mới hỏi, chứ mở cửa hỏi làm gì? cần document lại cái nào cần đồng ý để tôi chọn"*).
 *
 * Lưu bằng `StringSet` chứ không phải một chuỗi ghép: tập này do một lưới ô tích ghi (mỗi ô một mã), và mã
 * thì có dấu gạch dưới — mọi ký tự ngăn chọn tay đều là một chỗ để mã lẫn vào nhau (bài học `SlotCodec`).
 */
fun Prefs.voiceConfirmIds(ctx: Context): Set<String> =
    voicePrefs(ctx).getStringSet(K_VOICE_CONFIRM_IDS, emptySet())?.toSet().orEmpty()

fun Prefs.setVoiceConfirmIds(ctx: Context, ids: Set<String>) =
    voicePrefs(ctx).edit().putStringSet(K_VOICE_CONFIRM_IDS, ids).apply()

/** R9 — giữ micro mở bao lâu sau khi đã trả lời xong, cho câu tiếp. `0` = tắt hẳn hội thoại. */
fun Prefs.voiceFollowUpMs(ctx: Context): Int = voicePrefs(ctx).getInt(K_VOICE_FOLLOW_UP_MS, VOICE_FOLLOW_UP_DEFAULT_MS)
fun Prefs.setVoiceFollowUpMs(ctx: Context, v: Int) = voicePrefs(ctx).edit().putInt(K_VOICE_FOLLOW_UP_MS, v).apply()

// ── H2 · nhật ký lượt nói ────────────────────────────────────────────────────────────────────

/**
 * ═══ H2 — GIỮ LẠI tiếng + kết quả của mỗi lượt nghe, **BẬT SẴN** ═════════════════════════════════════════
 *
 * Owner 2026-09-16: cái còn thiếu để chỉnh đường nghe không phải thêm ý tưởng mà là **dữ liệu thật trên đường** —
 * giọng thật, mic 4 kênh, 80 km/h, điều hoà, nhạc. Off-car chỉ có 25 tệp TTS macOS, và CLAUDE.md §2 đã ghi rõ số
 * đo trên tập ấy KHÔNG nói được gì về xe. Nên mặc định là BẬT: một chuyến đi không ghi lại là một chuyến đi phải
 * lái lại.
 *
 * ## Vì sao bật sẵn mà vẫn giữ được lời hứa *"tiếng không rời khỏi xe"*
 * Ba tính chất, cả ba đo được từ mã ([com.byd.clusternav.launcher.voice.VoiceUtteranceLog]):
 *  1. tệp nằm trong `filesDir/voice-log/` — **bộ nhớ riêng của app**, app khác không đọc được;
 *  2. không dòng nào của lớp ấy chạm mạng (bài canh *"không tệp Voice\* nào gửi tiếng nói ra mạng"* vẫn nguyên);
 *  3. vòng đệm **30 mục / 30 MB** ⇒ nó không lớn dần theo thời gian, và tiếng cũ tự biến mất.
 *
 * Rời khỏi xe chỉ xảy ra khi có người **tự chạy** lệnh `voice_dump` qua cầu kiểm thử (nút *Xuất nhật ký voice*
 * đã gỡ khỏi Cài đặt ở bản release production, owner 2026-09-21) — một lượt
 * nén ra thẻ, do họ quyết định, với câu chữ nói thẳng *"chỉ lưu trên xe, không gửi đi"* ngay cạnh ô tích.
 */
fun Prefs.voiceKeepLog(ctx: Context): Boolean = voicePrefs(ctx).getBoolean(K_VOICE_KEEP_LOG, true)
fun Prefs.setVoiceKeepLog(ctx: Context, on: Boolean) =
    voicePrefs(ctx).edit().putBoolean(K_VOICE_KEEP_LOG, on).apply()

// ── H5 · bốn núm chỉnh bộ nghe — MẶC ĐỊNH = HẰNG ĐANG CHẠY, không đổi hành vi ─────────────────

/**
 * ═══ H5 — vì sao bốn khoá này tồn tại, và vì sao KHÔNG cái nào đổi mặc định ══════════════════════════════
 *
 * Bốn con số dưới đây đều là **hằng đã chọn bằng số đo off-car hoặc bằng lập luận**, chưa cái nào được chốt trên
 * cabin thật đang chạy. Chúng là đúng loại tham số mà một lượt lên xe trả lời được trong vài phút — *nói một câu,
 * đổi số, nói lại* — nhưng chỉ khi đổi được **tại chỗ**. Không có bốn khoá này thì mỗi con số là một vòng
 * sửa-build-cài-lên-xe, tức thực tế là không ai đo.
 *
 * ⇒ Hợp đồng của cả nhóm: **mặc định của từng khoá bằng đúng hằng hôm nay**, nên một máy chưa ai chỉnh chạy y hệt
 * 1.68. Chúng chỉ mở đường ĐO, không mang theo một quyết định nào.
 *
 * Dải hợp lệ khai ở `:core` ([VoiceEndpointer] · [SherpaModelCatalog]) — cầu kiểm thử kẹp theo đúng dải đó, và
 * hàm đọc dưới đây **cũng kẹp lại một lần nữa**: một giá trị rác còn sót trong prefs (bản cũ, tay người sửa file)
 * không được phép đi thẳng vào cấu hình giải mã.
 */
fun Prefs.voiceEndpointSilenceMs(ctx: Context): Int =
    voicePrefs(ctx).getInt(K_VOICE_ENDPOINT_SILENCE_MS, VoiceEndpointer.HANGOVER_MS)
        .coerceIn(VoiceEndpointer.MIN_HANGOVER_MS, VoiceEndpointer.MAX_HANGOVER_MS)

fun Prefs.setVoiceEndpointSilenceMs(ctx: Context, v: Int) =
    voicePrefs(ctx).edit().putInt(K_VOICE_ENDPOINT_SILENCE_MS, v).apply()

fun Prefs.voiceEndpointMinSpeechMs(ctx: Context): Int =
    voicePrefs(ctx).getInt(K_VOICE_ENDPOINT_MIN_SPEECH_MS, VoiceEndpointer.MIN_SPEECH_MS)
        .coerceIn(VoiceEndpointer.MIN_MIN_SPEECH_MS, VoiceEndpointer.MAX_MIN_SPEECH_MS)

fun Prefs.setVoiceEndpointMinSpeechMs(ctx: Context, v: Int) =
    voicePrefs(ctx).edit().putInt(K_VOICE_ENDPOINT_MIN_SPEECH_MS, v).apply()

/**
 * [P0-2] **Trần** của mức nền mà bộ ngắt câu đo được.
 *
 * ⚠ Khác ba núm kia ở một điểm quan trọng: mặc định của nó ([VoiceEndpointer.FLOOR_CAP] = 90) **là một thay đổi
 * hành vi**, không phải một hằng cũ. Ràng buộc *"không đổi mặc định"* của H5 được **gỡ có chủ đích** ngày
 * 2026-09-16 vì tiền đề của nó đã hết: H5 cấm đổi mặc định khi chưa có ca tái hiện, và bản ghi 12 phút trên xe
 * owner (`voice-1.68-real.txt`) chính là ca tái hiện — 189/300 lượt không bao giờ nghe thấy tiếng. Lý do đầy đủ
 * + con số ở KDoc [VoiceEndpointer]. Ba núm còn lại vẫn giữ nguyên mặc định cũ.
 */
fun Prefs.voiceEndpointFloorCap(ctx: Context): Int =
    voicePrefs(ctx).getInt(K_VOICE_ENDPOINT_FLOOR_CAP, VoiceEndpointer.FLOOR_CAP)
        .coerceIn(VoiceEndpointer.MIN_FLOOR_CAP, VoiceEndpointer.MAX_FLOOR_CAP)

fun Prefs.setVoiceEndpointFloorCap(ctx: Context, v: Int) =
    voicePrefs(ctx).edit().putInt(K_VOICE_ENDPOINT_FLOOR_CAP, v).apply()

// ── Silero VAD — BA núm của đường ngắt câu CHÍNH (docs/diagnostics/voice-stream-eval-2026-09-16.md §8) ──

/**
 * ═══ Ba tham số của Silero VAD, mặc định = ĐÚNG bộ đã chốt bằng lưới trên host ═══════════════════════════
 *
 * `threshold 0.5` · `min_speech 0.10 s` · `min_silence 0.15 s` · `margin 0` — [ĐO host §8]. Với bộ này: endpoint
 * **p50 660 ms**, **0/1 899 cắt giữa câu**, **0/1 899 không nổ** (§5), so với bộ RMS trên xe thật gần như không
 * bao giờ nổ (`chot=4200ms` ở 165/299 lượt).
 *
 * Giữ đơn vị **mili-giây (Int)** cho hai quãng thời gian, đúng họ với `voice_follow_up_ms` · `voice_endpoint_*`;
 * `SileroVadModelConfig` nhận **giây (Float)** nên phép đổi nằm đúng MỘT chỗ ([VoiceVad.open]). Ngưỡng thì giữ
 * nguyên đơn vị xác suất 0..1 của chính mô hình — đổi nó sang phần trăm là dựng một đơn vị thứ hai cho một con
 * số mà tài liệu sherpa, script host và lưới đo đều gọi bằng `0.5`.
 *
 * Cả ba kẹp lại khi đọc: một giá trị rác còn sót trong prefs không được đi thẳng vào cấu hình ONNX.
 */
fun Prefs.voiceVadThreshold(ctx: Context): Float =
    voicePrefs(ctx).getFloat(K_VOICE_VAD_THRESHOLD, VoiceVadTrim.THRESHOLD)
        .coerceIn(VoiceVadTrim.MIN_THRESHOLD, VoiceVadTrim.MAX_THRESHOLD)

fun Prefs.setVoiceVadThreshold(ctx: Context, v: Float) =
    voicePrefs(ctx).edit().putFloat(K_VOICE_VAD_THRESHOLD, v).apply()

fun Prefs.voiceVadMinSpeechMs(ctx: Context): Int =
    voicePrefs(ctx).getInt(K_VOICE_VAD_MIN_SPEECH_MS, VoiceVadTrim.MIN_SPEECH_MS)
        .coerceIn(VoiceVadTrim.MIN_MIN_SPEECH_MS, VoiceVadTrim.MAX_MIN_SPEECH_MS)

fun Prefs.setVoiceVadMinSpeechMs(ctx: Context, v: Int) =
    voicePrefs(ctx).edit().putInt(K_VOICE_VAD_MIN_SPEECH_MS, v).apply()

fun Prefs.voiceVadMinSilenceMs(ctx: Context): Int =
    voicePrefs(ctx).getInt(K_VOICE_VAD_MIN_SILENCE_MS, VoiceVadTrim.MIN_SILENCE_MS)
        .coerceIn(VoiceVadTrim.MIN_MIN_SILENCE_MS, VoiceVadTrim.MAX_MIN_SILENCE_MS)

fun Prefs.setVoiceVadMinSilenceMs(ctx: Context, v: Int) =
    voicePrefs(ctx).edit().putInt(K_VOICE_VAD_MIN_SILENCE_MS, v).apply()

/** Bề rộng chùm giải mã. Giá trị lạ ⇒ lùi về [SherpaModelCatalog.MAX_ACTIVE_PATHS] (fail-safe, không ném). */
fun Prefs.voiceBeam(ctx: Context): Int =
    voicePrefs(ctx).getInt(K_VOICE_BEAM, SherpaModelCatalog.MAX_ACTIVE_PATHS)
        .takeIf { it in SherpaModelCatalog.BEAM_CHOICES } ?: SherpaModelCatalog.MAX_ACTIVE_PATHS

fun Prefs.setVoiceBeam(ctx: Context, v: Int) = voicePrefs(ctx).edit().putInt(K_VOICE_BEAM, v).apply()

fun Prefs.voiceHotwordScore(ctx: Context): Float =
    voicePrefs(ctx).getFloat(K_VOICE_HOTWORD_SCORE, SherpaModelCatalog.HOTWORDS_SCORE)
        .coerceIn(SherpaModelCatalog.MIN_HOTWORDS_SCORE, SherpaModelCatalog.MAX_HOTWORDS_SCORE)

fun Prefs.setVoiceHotwordScore(ctx: Context, v: Float) =
    voicePrefs(ctx).edit().putFloat(K_VOICE_HOTWORD_SCORE, v).apply()

// ── Tốc độ đọc Piper (owner 2026-09-17: "Piper nói nhanh quá") — nhỏ hơn = chậm hơn ─────────────────

/** Tốc độ đọc TTS, kẹp [MIN_SPEED, MAX_SPEED]; mặc định [SherpaTtsCatalog.DEFAULT_SPEED] = 0.9 (chậm hơn gốc). */
fun Prefs.voiceTtsSpeed(ctx: Context): Float =
    voicePrefs(ctx).getFloat(K_VOICE_TTS_SPEED, SherpaTtsCatalog.DEFAULT_SPEED)
        .coerceIn(SherpaTtsCatalog.MIN_SPEED, SherpaTtsCatalog.MAX_SPEED)

fun Prefs.setVoiceTtsSpeed(ctx: Context, v: Float) =
    voicePrefs(ctx).edit().putFloat(K_VOICE_TTS_SPEED, v).apply()

// ── Giọng PHẢN HỒI: chọn Piper (chuẩn) hay giọng bé (clone) — owner chốt 2026-09-17 ─────────────────

private const val K_VOICE_FEEDBACK_VOICE = "voice_feedback_voice"

/**
 * ═══ Người dùng chọn GIỌNG nào đọc câu trả lời — spec `kachi-voice-clone.html` **T7/T8/R6** ══════════════════
 *
 * **Int** chứ không **Boolean**: hôm nay chỉ hai giá trị ([VoiceSpeakerSelector.FEEDBACK_PIPER] ·
 * [VoiceSpeakerSelector.FEEDBACK_CHILD]), nhưng đường đọc có thể mọc thêm giọng thứ ba (owner đã nhắc *"Giọng
 * Kachi bố"* — spec T12) và một `Boolean` sẽ phải đổi kiểu lúc đó, kéo theo mọi chỗ đọc. Hằng khai MỘT chỗ ở
 * `:core` ([VoiceSpeakerSelector]) để bộ chọn thuần và bài canh cùng dùng.
 *
 * ## ⚠ MẶC ĐỊNH BẮT BUỘC là Piper (owner 2026-09-17)
 * Giọng bé owner nghe ~80 % (việt-kiều, gãy khúc) nên **KHÔNG** được là mặc định — nó là một lựa chọn người dùng
 * tự bật. `getInt(..., FEEDBACK_PIPER)` giữ đúng điều đó: máy chưa ai chỉnh đọc bằng Piper y như 1.69, và
 * `VoiceSpeakerRouter` chỉ rẽ sang `ClipSpeaker` khi giá trị = [VoiceSpeakerSelector.FEEDBACK_CHILD] **và** gói
 * clip đã có trên đĩa (`VoiceSpeakerSelector.usesChildVoice`). Bài canh khoá cả hai điều đó.
 *
 * Theo XE ([com.byd.clusternav.launcher.ProfileScope.DEVICE_KEYS]) như mọi khoá giọng nói khác: gói clip nằm
 * trên đĩa của chính xe này, không đi theo hồ sơ.
 */
fun Prefs.voiceFeedbackVoice(ctx: Context): Int =
    voicePrefs(ctx).getInt(K_VOICE_FEEDBACK_VOICE, VoiceSpeakerSelector.FEEDBACK_PIPER)

fun Prefs.setVoiceFeedbackVoice(ctx: Context, v: Int) =
    voicePrefs(ctx).edit().putInt(K_VOICE_FEEDBACK_VOICE, v).apply()
