package com.byd.clusternav

import android.content.Context

/**
 * ═══ Khoá của hai AUTOMATION — tách khỏi [Prefs] theo VAI (1.85, trần 500 dòng) ═══════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R1 · R2 · R5. Cùng cách [PrefsVoiceV3] / `PrefsInputd` tách nhóm: hàm
 * mở rộng của [Prefs], **cùng tệp `clusternav_prefs`** (mở tệp thứ hai là dựng cửa thứ hai vào cùng chỗ lưu —
 * thứ [com.byd.clusternav.launcher.SettingsCatalog.PREFS_FILES] sinh ra để bắt).
 *
 * ## Cả ba khoá theo XE (`ProfileScope.DEVICE_KEYS`)
 * Automation là việc của **chiếc xe** (*"khi mưa thì sấy kính"*, *"7–9h thứ Hai thì dẫn đến công ty"*), không
 * phải sở thích đi theo người lái — cùng họ `cast_enabled` / `voice_wake_enabled`. Hệ quả **phải biết**: sổ địa
 * chỉ thì theo **hồ sơ** (`<hồ sơ>__saved_places`), nên một luật trỏ tới mục không có trong hồ sơ đang dùng sẽ
 * **bỏ lượt** (`ScheduledNavApplier.launch` ghi log rồi thôi) — degrade an toàn, không nổ, và không đóng dấu
 * đã-dẫn nên đổi lại hồ sơ trong khung giờ thì lượt đi vẫn còn.
 */

private fun autoPrefs(ctx: Context) =
    ctx.applicationContext.getSharedPreferences("clusternav_prefs", Context.MODE_PRIVATE)

// ── AUTOMATION #1 · Tự sấy kính khi mưa (R1) ──────────────────────────────────────────────────────
// MẶC ĐỊNH TẮT — cài mới KHÔNG đọc cảm biến, KHÔNG đụng nút sấy tới khi owner tự bật. BẬT ⇒
// `AutomationService` đọc `SETTING_FRONT_RAIN_WIPER_SPEED` ([ĐO xe 2026-09-20]: 1 khô / ≥2 mưa) mỗi ~5 phút và
// bật/tắt sấy TRƯỚC+SAU theo `RainDefrostPolicy`.
// ⚠ Ký ức R1.5 (*"sấy này của tôi"* / *"người lái vừa tự tắt"*) là cờ RAM trong `RainDefrostApplier`, KHÔNG ở
// đây — lý do đầy đủ ở KDoc `RainDefrostState` (nổ máy lại thì quên là hướng sai AN TOÀN).
private const val K_RAIN_DEFROST = "rain_defrost_enabled"

/** AUTOMATION #1 — "Tự sấy kính khi mưa". Mặc định **false**. */
fun Prefs.rainDefrostEnabled(ctx: Context): Boolean = autoPrefs(ctx).getBoolean(K_RAIN_DEFROST, false)

/** Xem [rainDefrostEnabled]. Chỗ gọi phải `AutomationService.sync` sau khi ghi (xem `ClusterNavBridge`). */
fun Prefs.setRainDefrostEnabled(ctx: Context, v: Boolean) =
    autoPrefs(ctx).edit().putBoolean(K_RAIN_DEFROST, v).apply()

// ── CAMERA theo xi-nhan (owner 2026-09-22) — mặc định TẮT ("đang phát triển") ────────────────────
private const val K_CAMERA_SIGNAL = "camera_signal_enabled"

/** Camera theo xi-nhan (xi-nhan → mở camera bên đó, overlay). Mặc định **false** (đang phát triển). */
fun Prefs.cameraSignalEnabled(ctx: Context): Boolean = autoPrefs(ctx).getBoolean(K_CAMERA_SIGNAL, false)
fun Prefs.setCameraSignalEnabled(ctx: Context, v: Boolean) =
    autoPrefs(ctx).edit().putBoolean(K_CAMERA_SIGNAL, v).apply()

private const val K_CAMERA_ON_CLUSTER = "camera_on_cluster"
/** Hiện overlay camera lên MÀN CỤM thay màn chính (owner 2026-09-24). Mặc định false = màn chính. */
fun Prefs.cameraOnCluster(ctx: Context): Boolean = autoPrefs(ctx).getBoolean(K_CAMERA_ON_CLUSTER, false)
fun Prefs.setCameraOnCluster(ctx: Context, v: Boolean) =
    autoPrefs(ctx).edit().putBoolean(K_CAMERA_ON_CLUSTER, v).apply()

// Phương án LVDS/hiển thị camera để thử NHANH trên xe không cần rebuild (findings 2026-09-23, runbook A–J).
// Chuỗi 1 ký tự: "A"(mặc định) · "B"(zOrderMediaOverlay) · "C"(setLVDS trước) · "D"(FULL_SCREEN) · "G"(cụm) ·
// "H"(chờ workState ON). Chỉnh qua prefs_set khi test 10 option, chốt được rồi đặt mặc định.
private const val K_CAMERA_LVDS = "camera_lvds_option"
fun Prefs.cameraLvdsOption(ctx: Context): String = autoPrefs(ctx).getString(K_CAMERA_LVDS, "A") ?: "A"
fun Prefs.setCameraLvdsOption(ctx: Context, v: String) = autoPrefs(ctx).edit().putString(K_CAMERA_LVDS, v).apply()

// ── AUTOMATION #2 · Tự dẫn đường theo lịch (R2) ───────────────────────────────────────────────────
// Hai khoá, hai VAI khác nhau — cố ý KHÔNG gộp:
//  • `nav_automation_rules` = CẤU HÌNH (sổ luật người dùng đặt trong Cài đặt › Dẫn đường), mã hoá bởi
//    `NavAutomationBook` (`:core`); rỗng = chưa có luật nào ⇒ engine không làm gì.
//  • `nav_automation_fired` = TRẠNG THÁI CHẠY (`id luật` → ngày đã dẫn, `NavAutomationFired`), thứ thi hành luật
//    "1 lần / khung / ngày" (R2.4). Nó KHÔNG phải cấu hình ⇒ khai ở `SettingsCatalog.NOT_SETTINGS`.
// Gộp hai vai vào một khoá thì một lượt SỬA luật sẽ xoá sạch dấu đã-dẫn: sửa giờ lúc 8h05 ⇒ dẫn lại ngay lần thứ
// hai, ngay trước mặt người đang lái.
private const val K_NAV_AUTOMATION = "nav_automation_rules"
private const val K_NAV_AUTOMATION_FIRED = "nav_automation_fired"

/** AUTOMATION #2 — sổ luật, dạng chuỗi của `NavAutomationBook.encode`. Rỗng = chưa có luật nào. */
fun Prefs.navAutomationRules(ctx: Context): String =
    autoPrefs(ctx).getString(K_NAV_AUTOMATION, "").orEmpty()

/** Xem [navAutomationRules]. Nhận chuỗi ĐÃ mã hoá — phép thêm/sửa/xoá là hàm thuần ở `:core`. */
fun Prefs.setNavAutomationRules(ctx: Context, encoded: String) =
    autoPrefs(ctx).edit().putString(K_NAV_AUTOMATION, encoded).apply()

/** Sổ ĐÃ-DẪN (`id=ngày`), dạng chuỗi của `NavAutomationFired.encode`. Rỗng = chưa dẫn lần nào. */
fun Prefs.navAutomationFired(ctx: Context): String =
    autoPrefs(ctx).getString(K_NAV_AUTOMATION_FIRED, "").orEmpty()

/** Xem [navAutomationFired]. */
fun Prefs.setNavAutomationFired(ctx: Context, encoded: String) =
    autoPrefs(ctx).edit().putString(K_NAV_AUTOMATION_FIRED, encoded).apply()
