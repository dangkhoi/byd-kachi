package com.byd.clusternav.launcher.testbridge

import android.content.Context
import com.byd.clusternav.Prefs
import com.byd.clusternav.setCameraSignalEnabled
import com.byd.clusternav.setCameraOnCluster
import com.byd.clusternav.setCameraCamId
import com.byd.clusternav.setCameraPos
import com.byd.clusternav.launcher.camera.CameraSignalPolicy
import com.byd.clusternav.launcher.WorkspacePrefs
import com.byd.clusternav.launcher.voice.SherpaModelCatalog
import com.byd.clusternav.launcher.voice.VoiceEndpointer
import com.byd.clusternav.launcher.voice.VoiceMicSource
import com.byd.clusternav.launcher.voice.VoiceVadTrim
import com.byd.clusternav.launcher.voice.VoiceRiskTable
import com.byd.clusternav.setVoiceBeam
import com.byd.clusternav.setVoiceConfirmIds
import com.byd.clusternav.setVoiceEndpointFloorCap
import com.byd.clusternav.setVoiceEndpointMinSpeechMs
import com.byd.clusternav.setVoiceEndpointSilenceMs
import com.byd.clusternav.setVoiceFollowUpMs
import com.byd.clusternav.setVoiceHotwordScore
import com.byd.clusternav.setVoiceKeepLog
import com.byd.clusternav.setVoiceTtsSpeed
import com.byd.clusternav.setVoiceVadMinSilenceMs
import com.byd.clusternav.setVoiceVadMinSpeechMs
import com.byd.clusternav.setVoiceVadThreshold
import com.byd.clusternav.setVoiceMicSource
import com.byd.clusternav.voiceBeam
import com.byd.clusternav.voiceConfirmIds
import com.byd.clusternav.voiceEndpointFloorCap
import com.byd.clusternav.voiceEndpointMinSpeechMs
import com.byd.clusternav.voiceEndpointSilenceMs
import com.byd.clusternav.voiceFollowUpMs
import com.byd.clusternav.voiceHotwordScore
import com.byd.clusternav.voiceKeepLog
import com.byd.clusternav.voiceTtsSpeed
import com.byd.clusternav.launcher.voice.SherpaTtsCatalog
import com.byd.clusternav.voiceMicSource
import com.byd.clusternav.voiceVadMinSilenceMs
import com.byd.clusternav.voiceVadMinSpeechMs
import com.byd.clusternav.voiceVadThreshold

/**
 * ═══ T-BRIDGE · LỆNH `prefs_set` — GHI một khoá trong danh sách trắng ════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` **R7 · R9 · R14** (lớp canh E2E cho chúng).
 *
 * ## Vì sao lệnh này sinh ra ([SOÁT Pass 1 · P2] 2026-09-16)
 * 1.66 đưa quyết định *"việc nào phải hỏi lại"* về tay người dùng (tập `voice_confirm_ids`, mặc định RỖNG). Vì
 * cầu kiểm thử **không ghi được prefs**, 8 ca `confirm=1` của `voice-cases.tsv` đã phải hạ về 0 — tức lớp canh
 * E2E của **cổng an toàn quan trọng nhất** biến mất trong cùng lượt đổi mặc định. Lệnh này trả nó lại: harness
 * bật đúng một mã trước ca, chạy ca, rồi dọn trong `trap`.
 *
 * ## Vì sao KHÔNG phải một cửa chung vào SharedPreferences
 * Receiver là `exported=true` (KDoc [KachiTestBridge] giải thích vì sao buộc phải thế). Một lệnh *"ghi khoá bất
 * kỳ"* biến mọi app trên xe thành một đường sửa cấu hình launcher ngay khi chế độ kiểm thử đang mở — kể cả những
 * khoá không liên quan gì tới việc đo (cast, phím vô-lăng, cụm). Danh sách trắng ở `:core`
 * ([TestBridgeCommands.WRITABLE_PREFS_KEYS]) chặn ở **tầng phân tích**, và mọi khoá trong đó đều đảo lại được
 * bằng một cú chạm trong Cài đặt.
 *
 * ## Vì sao `top_strip_labels` đi đường KHÁC bốn khoá kia
 * Bốn khoá giọng nói là khoá **theo xe**, đọc lại ở mỗi lần dùng ⇒ ghi thẳng prefs là đủ, và chạy được cả khi
 * màn chính chưa lên. `top_strip_labels` thì **theo hồ sơ** *và* đang nằm trong `HomeUiState` mà màn hình đang
 * vẽ; ghi thẳng prefs dưới chân màn hình sẽ cho `state.bars.chip_labels` báo giá trị CŨ — tức một phép đo nói
 * một đằng màn hình hiện một nẻo, đúng thứ KDoc [TestBridgeHooks] cấm. Nó đi qua **đúng** lambda mà ô tích trong
 * Cài đặt đi ([TestBridgeHooks.setTopStripLabels]), nên cần màn chính đang chạy.
 */
internal object TestBridgePrefsSet {

    /** `top_strip_labels` cần màn chính (xem KDoc lớp) — cùng mã lỗi với mọi lệnh cần móc. */
    const val KEY_TOP_STRIP_LABELS = "top_strip_labels"

    /** `--es text` không phải giá trị hợp lệ cho khoá ấy — nối nguyên văn để người đo thấy mình gõ gì. */
    const val ERR_BAD_VALUE = "bad_prefs_value:"

    /** Trần cho `voice_follow_up_ms` — 60 s, dài hơn mọi giá trị người dùng chọn được. Xem chú thích tại chỗ dùng. */
    const val MAX_FOLLOW_UP_MS = 60_000

    fun run(app: Context, cmd: TestBridgeCommand, hooks: TestBridgeHooks?, reply: TestBridgeReply) {
        val raw = cmd.text.trim()
        val applied: String? = when (cmd.key) {
            "voice_confirm_ids" -> {
                // Ngăn PHẨY, khoảng trắng bỏ qua; chuỗi rỗng ⇒ tập rỗng = *"không hỏi gì cả"* (mặc định owner).
                val ids = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                // ⚠ Từ chối mã KHÔNG có trong bảng việc-hỏi-được. Hai lý do: (a) một mã lạ nằm lại trong tập mãi
                // mãi (màn Cài đặt ghi lại cả tập, kể cả mã nó không hiểu); (b) quan trọng hơn — một ca E2E gõ sai
                // mã sẽ **im lặng** thành *"không hỏi"* và ca đó FAIL với lý do sai địa chỉ. Bảng do `:core` giữ.
                val askable = VoiceRiskTable.askableIds().toSet()
                if (ids.any { it !in askable }) return reply.fail(
                    ERR_BAD_VALUE + raw,
                    "key" to cmd.key,
                    "askable" to askable.sorted().joinToString(","),
                )
                Prefs.setVoiceConfirmIds(app, ids)
                ids.joinToString(",")
            }
            "voice_ask_aloud" -> bool(raw)?.let { Prefs.setVoiceAskAloud(app, it); it.toString() }
            // Kẹp trần: đây là quãng micro **mở**. Không kẹp thì một giá trị 2 tỉ ms giữ micro mở tới hết chuyến —
            // chế độ kiểm thử có công tắc và có tấm chữ, nhưng "mic mở vô hạn" không được phép là một giá trị hợp lệ.
            "voice_follow_up_ms" -> int(raw)?.takeIf { it in 0..MAX_FOLLOW_UP_MS }
                ?.let { Prefs.setVoiceFollowUpMs(app, it); it.toString() }
            "voice_mic_source" -> int(raw)?.takeIf { it in VoiceMicSource.CHOICES }
                ?.let { Prefs.setVoiceMicSource(app, it); it.toString() }
            // ═══ H5 — bốn núm chỉnh bộ nghe ═══════════════════════════════════════════════════════════
            // Dải hợp lệ lấy từ `:core` ([VoiceEndpointer] · [SherpaModelCatalog]), KHÔNG viết số ở đây: đây là
            // bản sao thứ hai của một con số nếu chép, và bản sao ấy sẽ lệch đúng vào lần ai đó nới dải. Ngoài
            // dải ⇒ `bad_prefs_value:` (cùng khuôn `voice_follow_up_ms`), không kẹp im lặng — một ca E2E gõ 3000
            // rồi được kẹp về 1500 sẽ PASS với một giá trị khác hẳn thứ nó tưởng mình đang đo.
            "voice_endpoint_silence_ms" ->
                int(raw)?.takeIf { it in VoiceEndpointer.MIN_HANGOVER_MS..VoiceEndpointer.MAX_HANGOVER_MS }
                    ?.let { Prefs.setVoiceEndpointSilenceMs(app, it); it.toString() }
            "voice_endpoint_min_speech_ms" ->
                int(raw)?.takeIf { it in VoiceEndpointer.MIN_MIN_SPEECH_MS..VoiceEndpointer.MAX_MIN_SPEECH_MS }
                    ?.let { Prefs.setVoiceEndpointMinSpeechMs(app, it); it.toString() }
            // [P0-2] Trần nền của bộ ngắt câu — núm quan trọng nhất của lượt xe kế tiếp: nó là thứ quyết định
            // một cabin ồn có bị đẩy ngưỡng ra khỏi tầm giọng nói hay không (xem KDoc [VoiceEndpointer]).
            "voice_endpoint_floor_cap" ->
                int(raw)?.takeIf { it in VoiceEndpointer.MIN_FLOOR_CAP..VoiceEndpointer.MAX_FLOOR_CAP }
                    ?.let { Prefs.setVoiceEndpointFloorCap(app, it); it.toString() }
            // Ba núm Silero VAD — dải ở `:core` ([VoiceVadTrim]); ngưỡng là xác suất 0..1 (Float), hai quãng
            // thời gian là mili-giây (Int) đúng họ với mọi khoá thời gian khác của đường giọng nói.
            "voice_vad_threshold" -> raw.toFloatOrNull()
                ?.takeIf { it in VoiceVadTrim.MIN_THRESHOLD..VoiceVadTrim.MAX_THRESHOLD }
                ?.let { Prefs.setVoiceVadThreshold(app, it); it.toString() }
            "voice_vad_min_speech_ms" ->
                int(raw)?.takeIf { it in VoiceVadTrim.MIN_MIN_SPEECH_MS..VoiceVadTrim.MAX_MIN_SPEECH_MS }
                    ?.let { Prefs.setVoiceVadMinSpeechMs(app, it); it.toString() }
            "voice_vad_min_silence_ms" ->
                int(raw)?.takeIf { it in VoiceVadTrim.MIN_MIN_SILENCE_MS..VoiceVadTrim.MAX_MIN_SILENCE_MS }
                    ?.let { Prefs.setVoiceVadMinSilenceMs(app, it); it.toString() }
            "voice_beam" -> int(raw)?.takeIf { it in SherpaModelCatalog.BEAM_CHOICES }
                ?.let { Prefs.setVoiceBeam(app, it); it.toString() }
            "voice_hotword_score" -> raw.toFloatOrNull()
                ?.takeIf { it in SherpaModelCatalog.MIN_HOTWORDS_SCORE..SherpaModelCatalog.MAX_HOTWORDS_SCORE }
                ?.let { Prefs.setVoiceHotwordScore(app, it); it.toString() }
            "voice_tts_speed" -> raw.toFloatOrNull()
                ?.takeIf { it in SherpaTtsCatalog.MIN_SPEED..SherpaTtsCatalog.MAX_SPEED }
                ?.let { Prefs.setVoiceTtsSpeed(app, it); it.toString() }
            // owner 2026-09-21 — công tắc giữ nhật ký lượt nói. Ô tích của nó đã gỡ khỏi Cài đặt (bản release
            // production dọn hết bề mặt dev/log), nên ĐÂY là đường chỉnh duy nhất còn lại. Ghi thẳng prefs như ba
            // khoá theo-xe kia: `VoiceUtteranceLog.enabled` đọc lại ở mỗi lượt ghi, không cache.
            "voice_keep_log" -> bool(raw)?.let { Prefs.setVoiceKeepLog(app, it); it.toString() }
            // Camera theo xi-nhan (findings 2026-09-23): bật/tắt + chọn cam + chọn GÓC hiện từng bên.
            "camera_signal_enabled" -> bool(raw)?.let { Prefs.setCameraSignalEnabled(app, it); it.toString() }
            "camera_on_cluster" -> bool(raw)?.let { Prefs.setCameraOnCluster(app, it); it.toString() }
            "camera_cam_left" -> raw.trim().toIntOrNull()?.let { Prefs.setCameraCamId(app, left = true, it); it.toString() }
            "camera_cam_right" -> raw.trim().toIntOrNull()?.let { Prefs.setCameraCamId(app, left = false, it); it.toString() }
            // Góc hiện overlay: CHỈ nhận "TL"/"TR" ([CameraSignalPolicy.isCorner]). Từ chối chuỗi lạ thay vì ghi
            // rồi để lượt đọc âm thầm rơi về mặc định — nếu không, một ca E2E gõ "TOPLEFT" sẽ PASS trong khi
            // overlay nằm ở góc khác hẳn thứ nó tưởng mình đang đo (cùng lẽ `voice_confirm_ids`).
            "camera_pos_left" -> raw.trim().uppercase().takeIf { CameraSignalPolicy.isCorner(it) }
                ?.let { Prefs.setCameraPos(app, left = true, v = it); it }
            "camera_pos_right" -> raw.trim().uppercase().takeIf { CameraSignalPolicy.isCorner(it) }
                ?.let { Prefs.setCameraPos(app, left = false, v = it); it }
            KEY_TOP_STRIP_LABELS -> {
                val on = bool(raw) ?: return reply.fail(ERR_BAD_VALUE + raw, "key" to cmd.key)
                val h = hooks ?: return reply.fail(KachiTestBridge.ERR_NO_HOME, "key" to cmd.key)
                h.setTopStripLabels(on)
                on.toString()
            }
            // Không thể tới đây: `:core` đã chặn khoá lạ ở tầng phân tích. Giữ nhánh để lượt thêm khoá mới mà quên
            // nối dây trả về một mã lỗi thay vì báo "đã ghi" cho một việc chưa xảy ra.
            else -> return reply.fail(TestBridgeCommands.ERR_BAD_PREFS_KEY + cmd.key)
        }
        if (applied == null) {
            reply.fail(ERR_BAD_VALUE + raw, "key" to cmd.key)
            return
        }
        reply.ok("key" to cmd.key, "value" to applied, "read_back" to readBack(app, cmd.key, hooks))
    }

    /**
     * Đọc LẠI từ nơi lưu bền (hoặc từ `HomeUiState` cho khoá theo hồ sơ) — lời đáp nói **giá trị thật sau lượt
     * ghi**, không phải giá trị vừa nhận. Một lượt ghi hỏng im lặng (prefs đọc-chỉ, hồ sơ vừa đổi) thì phép đo
     * phải thấy ngay, không phải ở ca thất bại sau đó ba bước.
     */
    private fun readBack(app: Context, key: String, hooks: TestBridgeHooks?): String = runCatching {
        when (key) {
            "voice_confirm_ids" -> Prefs.voiceConfirmIds(app).sorted().joinToString(",")
            "voice_ask_aloud" -> Prefs.voiceAskAloud(app).toString()
            "voice_follow_up_ms" -> Prefs.voiceFollowUpMs(app).toString()
            "voice_mic_source" -> Prefs.voiceMicSource(app).toString()
            "voice_endpoint_silence_ms" -> Prefs.voiceEndpointSilenceMs(app).toString()
            "voice_endpoint_min_speech_ms" -> Prefs.voiceEndpointMinSpeechMs(app).toString()
            "voice_endpoint_floor_cap" -> Prefs.voiceEndpointFloorCap(app).toString()
            "voice_vad_threshold" -> Prefs.voiceVadThreshold(app).toString()
            "voice_vad_min_speech_ms" -> Prefs.voiceVadMinSpeechMs(app).toString()
            "voice_vad_min_silence_ms" -> Prefs.voiceVadMinSilenceMs(app).toString()
            "voice_beam" -> Prefs.voiceBeam(app).toString()
            "voice_hotword_score" -> Prefs.voiceHotwordScore(app).toString()
            "voice_tts_speed" -> Prefs.voiceTtsSpeed(app).toString()
            "voice_keep_log" -> Prefs.voiceKeepLog(app).toString()
            KEY_TOP_STRIP_LABELS -> (hooks?.state()?.topStrip?.showLabels ?: WorkspacePrefs(app).topStrip().showLabels)
                .toString()
            else -> ""
        }
    }.getOrDefault("")

    /** `1/0/true/false/on/off` — cố ý KHÔNG nhận chuỗi rỗng: *"quên truyền giá trị"* phải là một lỗi, không phải `false`. */
    private fun bool(s: String): Boolean? = when (s.lowercase()) {
        "1", "true", "on", "yes" -> true
        "0", "false", "off", "no" -> false
        else -> null
    }

    private fun int(s: String): Int? = s.toIntOrNull()
}
