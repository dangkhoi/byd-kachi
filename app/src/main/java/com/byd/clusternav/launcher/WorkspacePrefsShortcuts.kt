package com.byd.clusternav.launcher

import android.util.Log

/**
 * ═══ F1 — LỐI TẮT ỨNG DỤNG theo hồ sơ: đọc/ghi khoá `app_shortcuts` ═════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` §4.4.1 · §4.7 (C9). Tệp riêng vì `WorkspacePrefs.kt` đang ở
 * 499/500 dòng (CLAUDE.md global §4.1). Là **hàm mở rộng của chính [WorkspacePrefs]** (cùng khuôn `WorkspacePrefsLang.kt`)
 * ⇒ ghi vào ĐÚNG MỘT `SharedPreferences` (`sp` của lớp ấy) — không mở cửa thứ hai vào `kachi_workspace`.
 *
 * Khoá theo HỒ SƠ: `<hồ sơ>__app_shortcuts` ([ProfileScope.LAUNCHER_LAYOUT_SUFFIXES]), kiểu STRING
 * ([ProfileScopeLauncher.DECLARED_TYPES]), đi theo bản chia sẻ ([ProfileSharePolicy.SHAREABLE]). Vì là hậu tố theo hồ sơ
 * nên nhân bản / xuất / nhập / xoá hồ sơ tự mang theo (các đường đó lặp trên [ProfileScope.LAUNCHER_SUFFIXES]).
 *
 * ⚠ Tên tệp khớp `WorkspacePrefs*.kt` và có mặt trong danh sách quét của `LauncherProfileTypesCoverageTest` ⇒ bài canh
 * thấy lượt ghi `putString(key(K_APP_SHORTCUTS)…)` + lượt đọc `stringOrNull(key(K_APP_SHORTCUTS))` và đòi kiểu khớp bảng.
 */

/** Hậu tố khoá — `const` ở cấp tệp để bài canh kiểu đọc được GIÁ TRỊ khoá (nó giải hằng `K_*` trong tệp được quét). */
private const val K_APP_SHORTCUTS = "app_shortcuts"

/**
 * Lối tắt của hồ sơ đang dùng. Đọc qua [stringOrNull] (giá trị sai kiểu từ một tệp nhập ≤ 2.84 = VẮNG, không ném).
 * Khoá MỚI hoàn toàn (không có bản chung-cả-máy để lùi về) ⇒ đọc thẳng `key()`, như `saved_places`. Chuỗi rác ⇒ phép
 * giải mã dễ dãi của `:core` bỏ mục hỏng; quá trần ⇒ cắt + MỘT dòng log (R1.1: "quá 8 ⇒ cắt và log").
 */
fun WorkspacePrefs.appShortcuts(): List<AppShortcut> {
    val out = AppShortcutCodec.decodeReport(sp.stringOrNull(key(K_APP_SHORTCUTS)))
    if (out.truncated > 0) Log.i("KachiShortcut", "app_shortcuts: bỏ ${out.truncated} mục quá trần kỹ thuật ${AppShortcutCodec.MAX}")
    return out.items
}

/** Ghi CẢ danh sách một lượt (phép sửa là hàm thuần ở [ShortcutSelection]); mã hoá ghi CHẶT ([AppShortcutCodec.encode]). */
fun WorkspacePrefs.setAppShortcuts(items: List<AppShortcut>) {
    sp.edit().putString(key(K_APP_SHORTCUTS), AppShortcutCodec.encode(items)).apply()
}
