package com.byd.clusternav

import android.content.Context
import com.byd.clusternav.launcher.camera.CameraSignalPolicy

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

// ── V7 (owner 2026-09-25) — CHỌN kính nào được sấy: trước · sau+gương · cả hai ────────────────────
// Owner: *"tách 2 option riêng, user chọn cả 2 hoặc 1 trong 2"*. Hai khoá con, KHÔNG phải một khoá 3 giá trị
// ("front"/"rear"/"both"): ba-giá-trị-trong-một-chuỗi là chỗ sinh ra trạng thái thứ tư không ai định nghĩa khi
// prefs bị sửa tay (`prefs_set` trên xe), và nó cũng không nói được ca "cả hai TẮT".
//
// ⚠ Quan hệ với [K_RAIN_DEFROST]: đó là công tắc CHÍNH (bật/tắt tính năng); hai khoá này chỉ có nghĩa khi chính
// đang bật. Cả hai TẮT ⇒ `RainDefrostApplier` coi như tính năng tắt (không đọc cảm biến, không ghi nút nào) — xem
// KDoc `RainDefrostApplier.selection`.
//
// MẶC ĐỊNH CẢ HAI BẬT = giữ NGUYÊN hành vi của bản trước (1.85 ghi cả hai nút, R1.3): người đã bật tính năng rồi
// nâng cấp lên bản này không được thấy nó lặng lẽ làm ít hơn hôm qua.
private const val K_RAIN_DEFROST_FRONT = "rain_defrost_front"
private const val K_RAIN_DEFROST_REAR = "rain_defrost_rear"

/** V7 — mưa thì bật sấy kính TRƯỚC. Mặc định **true** (hành vi 1.85). */
fun Prefs.rainDefrostFront(ctx: Context): Boolean = autoPrefs(ctx).getBoolean(K_RAIN_DEFROST_FRONT, true)

/** Xem [rainDefrostFront]. Chỗ gọi phải `AutomationService.sync` sau khi ghi (xem `ClusterNavBridge`). */
fun Prefs.setRainDefrostFront(ctx: Context, v: Boolean) =
    autoPrefs(ctx).edit().putBoolean(K_RAIN_DEFROST_FRONT, v).apply()

/** V7 — mưa thì bật sấy kính SAU + gương chiếu hậu (`defrost_rear`). Mặc định **true** (hành vi 1.85). */
fun Prefs.rainDefrostRear(ctx: Context): Boolean = autoPrefs(ctx).getBoolean(K_RAIN_DEFROST_REAR, true)

/** Xem [rainDefrostRear]. */
fun Prefs.setRainDefrostRear(ctx: Context, v: Boolean) =
    autoPrefs(ctx).edit().putBoolean(K_RAIN_DEFROST_REAR, v).apply()

// ── V8 (owner 2026-09-25) — TỰ CẬP NHẬT khi mở app ───────────────────────────────────────────────
// Owner: *"tách auto-update thành 1 toggle riêng ở Hệ thống, KHÔNG gắn với Nav+HUD"*. Trước V8 lượt dò bản mới
// chỉ đi kèm đường Nav+HUD / nút bấm tay, tức ai tắt dẫn đường thì không bao giờ được cập nhật mà không có gì
// nói ra điều đó.
//
// MẶC ĐỊNH TẮT: nó mở một kết nối HTTPS ra GitHub mỗi lần mở launcher và có thể dựng hộp thoại *"cài bản mới?"*
// trước mặt người đang lái. Một tính năng tự-tải-về-rồi-cài-đè phải do chủ xe bật tường minh — cùng lẽ
// `rain_defrost_enabled` / `voice_wake_enabled` mặc định TẮT.
private const val K_AUTO_UPDATE = "auto_update_enabled"

/** V8 — "Tự động cập nhật": mở launcher thì tự dò bản mới trong `apk/`. Mặc định **false**. */
fun Prefs.autoUpdateEnabled(ctx: Context): Boolean = autoPrefs(ctx).getBoolean(K_AUTO_UPDATE, false)

/** Xem [autoUpdateEnabled]. Không có tác dụng phụ nào phải đồng bộ: lượt dò đọc khoá này mỗi lần màn chính lên. */
fun Prefs.setAutoUpdateEnabled(ctx: Context, v: Boolean) =
    autoPrefs(ctx).edit().putBoolean(K_AUTO_UPDATE, v).apply()

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
// ⚠ GỠ HẲN 2026-09-25 (spec `camera-turn-signal-hal-socket.html` R6): mười option A–J là **thử nghiệm LVDS**, và
// [ĐO xe 2026-09-25] đường có HÌNH là AVMCamera đổ frame vào Surface, không phải LVDS thụ động ⇒ cả họ option
// mất lý do tồn tại. Giữ lại một pref chết thì nó sẽ còn được `prefs_set` ghi trên xe và không ai đọc — tệ hơn
// là không có. Chỗ trống này nay là VỊ TRÍ GÓC (dưới đây), thứ owner thật sự cần chỉnh trên xe.

// ── Vị trí GÓC của overlay camera, RIÊNG từng bên xi-nhan (spec R3 · R4) ─────────────────────────
// Hai khoá vì đây là hai lựa chọn ĐỘC LẬP: owner chốt *"trái vẫn có thể hiện bên phải"* (R4) — người lái ngồi
// bên trái nên góc trên-trái có thể bị vành lái/cột A che ở một số cách ngồi. Một khoá dùng chung sẽ buộc hai
// bên đối xứng, tức làm mất đúng thứ yêu cầu xin.
// Mặc định = [CameraSignalPolicy.defaultCorner] (trái→TL, phải→TR) — hằng ở `:core`, KHÔNG chép số vào đây.
private fun cameraPosKey(left: Boolean) = if (left) "camera_pos_left" else "camera_pos_right"

/**
 * Góc hiện overlay camera cho bên xi-nhan [left] — `"TL"` (trên-trái) hoặc `"TR"` (trên-phải).
 *
 * Giá trị lạ trên đĩa (prefs sửa tay qua `prefs_set`, hoặc dữ liệu của bản trước) ⇒ trả về mặc định thay vì trả
 * nguyên văn: tầng vẽ chỉ biết hai góc, nên một chuỗi thứ ba đi tới đó sẽ thành *"rơi vào nhánh else"* — tức
 * overlay lặng lẽ nằm sai góc mà không ai biết vì sao.
 */
fun Prefs.cameraPos(ctx: Context, left: Boolean): String {
    val fallback = CameraSignalPolicy.defaultCorner(left)
    val raw = autoPrefs(ctx).getString(cameraPosKey(left), fallback) ?: fallback
    return if (CameraSignalPolicy.isCorner(raw)) raw else fallback
}

/** Xem [cameraPos]. Nhận `"TL"`/`"TR"`; chuỗi khác ghi được nhưng lượt đọc sẽ bỏ qua (xem KDoc trên). */
fun Prefs.setCameraPos(ctx: Context, left: Boolean, v: String) =
    autoPrefs(ctx).edit().putString(cameraPosKey(left), v).apply()

// cameraId AVMCamera trái/phải — đổi trên xe để tìm đúng cam (chưa chắc map). Mặc định = [default] (CamView.cameraId).
fun Prefs.cameraCamId(ctx: Context, left: Boolean, default: Int): Int {
    val k = if (left) "camera_cam_left" else "camera_cam_right"
    return autoPrefs(ctx).getInt(k, default)
}
fun Prefs.setCameraCamId(ctx: Context, left: Boolean, v: Int) =
    autoPrefs(ctx).edit().putInt(if (left) "camera_cam_left" else "camera_cam_right", v).apply()

// Cắt vùng gương (crop) — Seal: cam gương là fisheye 5120×960 ⇒ CẦN crop vùng trái/phải; SL6/xe khác: cam thường
// ⇒ crop ra sai/đen ⇒ TẮT để hiện full khung. Mặc định BẬT (giữ hành vi Seal đang chạy ngon).
fun Prefs.cameraCrop(ctx: Context): Boolean = autoPrefs(ctx).getBoolean("camera_crop", true)
fun Prefs.setCameraCrop(ctx: Context, v: Boolean) = autoPrefs(ctx).edit().putBoolean("camera_crop", v).apply()

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
