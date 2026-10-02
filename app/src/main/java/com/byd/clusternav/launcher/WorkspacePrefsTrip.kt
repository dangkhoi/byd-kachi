package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.trip.TripAppCodec
import com.byd.clusternav.launcher.trip.TripConfig
import com.byd.clusternav.launcher.trip.TripMusicCodec

/**
 * ═══ F2/F3 — CHUYẾN LÊN XE theo hồ sơ: đọc/ghi hai khoá `ignition_apps` + `ignition_music` ══════════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` §4.7 (C9 phần nhóm C). Cùng khuôn `WorkspacePrefsShortcuts.kt`:
 * hàm mở rộng của CHÍNH [WorkspacePrefs] (một `SharedPreferences` duy nhất cho `kachi_workspace`), tệp riêng vì
 * `WorkspacePrefs.kt` sát trần 500 dòng. Hai khoá theo HỒ SƠ ([ProfileScope.LAUNCHER_PERSONAL_SUFFIXES]) — *"mọi cấu
 * hình theo hồ sơ"* (S4) — kiểu STRING ([ProfileScopeLauncher.DECLARED_TYPES]), đi theo bản chia sẻ
 * ([ProfileSharePolicy.SHAREABLE] — tên gói + kiểu + từ khoá/link, không vị trí). Nhân bản/xuất/nhập/xoá hồ sơ tự mang
 * theo vì các đường đó lặp trên [ProfileScope.LAUNCHER_SUFFIXES].
 *
 * Chuyến ĐỌC ở mức tiến trình (`TripStart`, hồ sơ đang hiệu lực lúc nổ máy — `PrefsWorkspaceRepository.kt:67-89` áp hồ
 * sơ lúc nổ máy khi tiến trình khởi động nguội) bằng cùng hàm [tripConfig]; GHI chỉ qua `HomeViewModel.setTripConfig`
 * (`GridSeamGuardTest.chi ViewModel duoc ghi ben`).
 */

/**
 * Hậu tố khoá — `const` cấp tệp để `LauncherProfileTypesCoverageTest` đọc được GIÁ TRỊ khoá.
 *
 * Tên `ignition_*`, KHÔNG `trip_*` như bản nháp spec §4.7: `ProfileSharePolicyTest` coi mọi khoá theo hồ sơ có từ `trip`
 * là dáng "nhật ký chuyến đi" và đòi nó nằm ở bảng RIÊNG TƯ (owner duyệt đúng ba khoá). Hai khoá này là CẤU HÌNH (gói
 * app + kiểu + từ khoá/link), không phải nhật ký — đặt đúng tên việc của nó ("khi nổ máy") thay vì nới bài canh.
 */
private const val K_IGNITION_APPS = "ignition_apps"
private const val K_IGNITION_MUSIC = "ignition_music"

/** Cấu hình chuyến của hồ sơ đang dùng. Giá trị sai kiểu (tệp nhập ≤ 2.84) = VẮNG; chuỗi hỏng ⇒ giải mã dễ dãi bỏ mục hỏng. */
fun WorkspacePrefs.tripConfig(): TripConfig = TripConfig(
    apps = TripAppCodec.decode(sp.stringOrNull(key(K_IGNITION_APPS))),
    music = TripMusicCodec.decode(sp.stringOrNull(key(K_IGNITION_MUSIC))),
)

/** Ghi CẢ cấu hình một lượt (mã hoá chặt ở `:core`). */
fun WorkspacePrefs.setTripConfig(cfg: TripConfig) {
    sp.edit()
        .putString(key(K_IGNITION_APPS), TripAppCodec.encode(cfg.apps))
        .putString(key(K_IGNITION_MUSIC), TripMusicCodec.encode(cfg.music))
        .apply()
}
