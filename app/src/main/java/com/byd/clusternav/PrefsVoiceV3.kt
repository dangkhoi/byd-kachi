package com.byd.clusternav

import android.content.Context

/**
 * ═══ V3 (1.66) — BA KHOÁ MỚI của đường giọng nói, tách khỏi [Prefs] ══════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` R1 · R7 · R9.
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
