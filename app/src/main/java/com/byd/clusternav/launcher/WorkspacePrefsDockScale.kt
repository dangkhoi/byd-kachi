package com.byd.clusternav.launcher

import android.content.SharedPreferences

/**
 * ═══ 2.89 · B3 DOCK-SCALE — cỡ thanh nút theo hồ sơ: đọc/ghi khoá `dock_scale` ═══════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-289-field-fixes.html` §B3. Tệp riêng vì `WorkspacePrefs.kt` ở 499/500 dòng (CLAUDE.md global
 * §4.1), cùng khuôn `WorkspacePrefsSlotHead.kt`: **hàm mở rộng của chính [WorkspacePrefs]** ⇒ đọc/ghi ĐÚNG MỘT
 * `SharedPreferences` (`sp` của lớp ấy). `loadDock`/`saveDock` gọi hai hàm này — cấu hình thanh vẫn đi một cửa.
 *
 * Khoá theo HỒ SƠ: `<hồ sơ>__dock_scale` ([ProfileScope.LAUNCHER_LAYOUT_SUFFIXES]), kiểu STRING
 * ([ProfileScopeLauncher.DECLARED_TYPES] — lệ launcher *"chuỗi mã hoá hoặc cờ"*), đi theo bản chia sẻ
 * ([ProfileSharePolicy.SHAREABLE]) ⇒ nhân bản / xuất / nhập / xoá hồ sơ tự mang theo. Tên tệp nằm trong danh sách quét
 * của `LauncherProfileTypesCoverageTest`.
 *
 * Vì sao khoá RIÊNG, không ghép vào `dock_edge` (`"BOTTOM;85"`): APK cũ đọc `DockEdge.valueOf` hỏng ⇒ mất viền về BOTTOM.
 */

/** Hậu tố khoá — `const` cấp tệp để bài canh kiểu đọc được GIÁ TRỊ khoá. */
private const val K_DOCK_SCALE = "dock_scale"

/**
 * % của hồ sơ đang dùng. Vắng / rác / sai kiểu (tệp nhập ≤ 2.88) ⇒ 100 = hôm nay ([BarScale.decode] + [stringOrNull]
 * không bao giờ ném).
 */
fun WorkspacePrefs.dockScalePct(): Int = BarScale.decode(sp.stringOrNull(key(K_DOCK_SCALE)))

/**
 * Ghi % vào lượt sửa [e] của `saveDock` (cùng một `apply`). 100 % ⇒ XOÁ khoá ([BarScale.encode] = `null`): tệp prefs của
 * người chưa chạm thanh kéo giữ nguyên từng byte.
 */
internal fun WorkspacePrefs.putDockScale(e: SharedPreferences.Editor, pct: Int): SharedPreferences.Editor =
    BarScale.encode(pct)?.let { e.putString(key(K_DOCK_SCALE), it) } ?: e.remove(key(K_DOCK_SCALE))
