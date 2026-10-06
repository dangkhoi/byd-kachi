package com.byd.clusternav.launcher.voice

import android.content.Context
import android.util.Log
import com.byd.clusternav.launcher.WorkspacePrefs
import com.byd.clusternav.launcher.voiceAppNames

/**
 * ═══ 2.91 VOICE-APP-NAMES · A2 — TÊN ĐÃ DẠY của hồ sơ đang dùng, theo ĐÚNG tiến trình đang hỏi ══════════════════
 *
 * Spec §4.5 *"Sang `:wake`"* · R5 AC (*"đổi hồ sơ ⇒ câu kế tiếp dùng bộ tên mới ở CẢ tiến trình chính lẫn `:wake`"*).
 *  • Tiến trình CHÍNH: đọc prefs (`WorkspacePrefs.voiceAppNames`) — nguồn sự thật, đọc mỗi lượt.
 *  • `:wake` (và mọi tiến trình khác): đọc ẢNH CHỤP ngữ pháp ([VoiceGrammarSnapshotStore.read]) — cache
 *    `SharedPreferences` của `:wake` không bao giờ nạp lại, nên đọc prefs ở đó là đọc bộ tên CŨ (KDoc ảnh chụp).
 * KHÔNG đi qua `state()` của dispatcher `:wake` (bài canh `VoiceWakeFakeStateContractTest`).
 *
 * Một chỗ quyết, mọi bề mặt dùng chung: [VoiceWiring.appsByLabel] (bảng gọi app) · [VoiceWiring.aliases] (từ vựng của
 * parser) · [VoiceRecognizer.open] (hotword). Lỗi đọc ⇒ rỗng (degrade-safe R-nf5: mất tên đã dạy, không mất lệnh).
 */
object VoiceTaughtSource {

    private const val TAG = "KachiVoiceNames"

    fun names(ctx: Context): List<TaughtName> = runCatching {
        if (VoiceGrammarSnapshotStore.isMainProcess(ctx)) WorkspacePrefs(ctx).voiceAppNames()
        else VoiceGrammarSnapshotStore.read(ctx).aliases
    }.onFailure { Log.w(TAG, "đọc tên đã dạy hỏng — phiên này không có tên đã dạy", it) }.getOrDefault(emptyList())

    /**
     * Tên được BIAS cho phiên lệnh (R8): nguồn giọng (OQ3), CÙNG luật sống của từ vựng parser — hàm thuần
     * [VoiceAppIndex.hotwordNames] (`:core`, có test): gói có mặt ([installed]), một chủ duy nhất, không trùng khoá của
     * app khác ([apps]), kể cả tên của gói thua khử trùng nhãn. Luật đơn điệu + nối dài ở [SherpaTaughtHotwords].
     */
    fun forHotwords(ctx: Context, apps: List<String>, installed: Set<String>): List<TaughtName> =
        VoiceAppIndex.hotwordNames(apps, installed, names(ctx))
}
