package com.byd.clusternav.launcher

import android.util.Log
import com.byd.clusternav.launcher.voice.TaughtName
import com.byd.clusternav.launcher.voice.TaughtNamesCodec
import com.byd.clusternav.launcher.voice.VoiceGrammarSnapshotStore

/**
 * ═══ 2.91 VOICE-APP-NAMES · A1 — TÊN APP TỰ DẠY theo hồ sơ: đọc/ghi khoá `voice_app_names` ════════════════════════
 *
 * Spec `docs/specs/kachi-290-voice-app-names.html` §4.5 · R5. Tệp riêng vì `WorkspacePrefs.kt` 499/500 dòng; hàm mở rộng
 * của CHÍNH [WorkspacePrefs] (khuôn `WorkspacePrefsShortcuts.kt`) ⇒ một `SharedPreferences` duy nhất (`kachi_workspace`).
 *
 * Khoá theo HỒ SƠ `<hồ sơ>__voice_app_names` ([ProfileScope.LAUNCHER_PERSONAL_SUFFIXES]), kiểu STRING
 * ([ProfileScopeLauncher.DECLARED_TYPES]), KHÔNG đi theo bản chia sẻ ([ProfileSharePolicy.PRIVATE], OQ2) — bản FULL mang.
 *
 * ## Hai luật bền (CLAUDE.md §5)
 *  • **Chỉ tiến trình chính ghi** (R-nf2): `:wake` đọc tên qua ảnh chụp ngữ pháp, không qua prefs (cache của nó không bao
 *    giờ nạp lại). Mọi lượt ghi ở đây gọi [VoiceGrammarSnapshotStore.write] — cùng khuôn `setSavedPlaces`.
 *  • **Phiên bản lạ ⇒ không ghi đè** ([TaughtNamesCodec.Decoded.readOnly]): dữ liệu từ một bản Kachi MỚI hơn (hạ cấp /
 *    nhập chéo) giữ nguyên chuỗi gốc; trang hiện "chỉ đọc".
 */

/** Hậu tố khoá — `const` cấp tệp để bài canh kiểu đọc được GIÁ TRỊ khoá. */
private const val K_VOICE_APP_NAMES = "voice_app_names"

/** Bản giải mã đầy đủ (kèm cờ chỉ-đọc + lý do) của hồ sơ đang dùng. Chuỗi sai kiểu (tệp nhập cũ) ⇒ VẮNG, không ném. */
fun WorkspacePrefs.voiceAppNamesDecoded(): TaughtNamesCodec.Decoded {
    val d = TaughtNamesCodec.decode(sp.stringOrNull(key(K_VOICE_APP_NAMES)))
    // Lý do chỉ nói số dòng/độ dài — không in chữ người dùng (R-nf3).
    d.problem?.let { Log.i(TAG_NAMES, "voice_app_names: $it (giữ ${d.names.size} tên)") }
    return d
}

/** Tên đã dạy của hồ sơ đang dùng. */
fun WorkspacePrefs.voiceAppNames(): List<TaughtName> = voiceAppNamesDecoded().names

/**
 * Ghi CẢ danh sách một lượt (phép sửa là hàm thuần ở `TaughtNames`). `false` = KHÔNG ghi vì dữ liệu đang có là của một
 * phiên bản lạ (chỉ đọc). Ghi xong cập nhật ảnh chụp ngữ pháp để `:wake` dùng bộ tên mới ở phiên kế tiếp.
 */
fun WorkspacePrefs.setVoiceAppNames(names: List<TaughtName>): Boolean {
    if (voiceAppNamesDecoded().readOnly) {
        Log.w(TAG_NAMES, "voice_app_names: dữ liệu phiên bản lạ — từ chối ghi đè (${names.size} tên chưa lưu)")
        return false
    }
    sp.edit().putString(key(K_VOICE_APP_NAMES), TaughtNamesCodec.encode(names)).apply()
    VoiceGrammarSnapshotStore.write(this)
    Log.i(TAG_NAMES, "voice_app_names: lưu ${names.size} tên")
    return true
}

private const val TAG_NAMES = "KachiVoiceNames"
