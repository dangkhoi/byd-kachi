package com.byd.clusternav.launcher

/**
 * ═══ 2.87 · R-AH3 — "Tự ẩn nút ⇄" theo hồ sơ: đọc/ghi khoá `swap_button_autohide` ═════════════════════════════════════
 *
 * Spec `docs/specs/kachi-287-look-and-keys.html` §3 R-AH3. Tệp riêng vì `WorkspacePrefs.kt` đang ở 499/500 dòng (CLAUDE.md
 * global §4.1), cùng khuôn `WorkspacePrefsShortcuts.kt`: **hàm mở rộng của chính [WorkspacePrefs]** ⇒ ghi vào ĐÚNG MỘT
 * `SharedPreferences` (`sp` của lớp ấy) — không mở cửa thứ hai vào `kachi_workspace`.
 *
 * Khoá theo HỒ SƠ: `<hồ sơ>__swap_button_autohide` ([ProfileScope.LAUNCHER_LAYOUT_SUFFIXES]), kiểu BOOLEAN
 * ([ProfileScopeLauncher.DECLARED_TYPES]), đi theo bản chia sẻ ([ProfileSharePolicy.SHAREABLE]) ⇒ nhân bản / xuất / nhập /
 * xoá hồ sơ tự mang theo. Tên tệp nằm trong danh sách quét của `LauncherProfileTypesCoverageTest`.
 *
 * ⚠ Tên khoá KHÔNG mở đầu bằng `slot_` dù trong mã nó là "slot head": `slot_` là HỌ khoá nội dung ô
 * ([SettingsCatalog.SLOT_KEY_PREFIX] · [ProfileScope.PROFILE_KEY_PREFIXES]) — [ĐO] đặt `slot_head_autohide` thì
 * `ProfileScopeTest.o duoc sinh theo SLOT_CAP` đỏ, và bài canh kiểu giải `"slot_$i"` ra cả khoá cờ này (sai kiểu).
 */

/** Hậu tố khoá — `const` cấp tệp để bài canh kiểu đọc được GIÁ TRỊ khoá. */
private const val K_SWAP_AUTOHIDE = "swap_button_autohide"

/**
 * Cờ của hồ sơ đang dùng. Vắng khoá ⇒ BẬT (owner muốn mặc định ẩn; khớp `HomeUiState.slotHeadAutoHide`). Đọc qua
 * [booleanOrNull]: giá trị sai kiểu (tệp nhập ≤ 2.84) = VẮNG, không ném. Khoá MỚI hoàn toàn ⇒ đọc thẳng `key()`.
 */
fun WorkspacePrefs.slotHeadAutoHide(): Boolean = sp.booleanOrNull(key(K_SWAP_AUTOHIDE)) ?: true

fun WorkspacePrefs.setSlotHeadAutoHide(on: Boolean) {
    sp.edit().putBoolean(key(K_SWAP_AUTOHIDE), on).apply()
}
