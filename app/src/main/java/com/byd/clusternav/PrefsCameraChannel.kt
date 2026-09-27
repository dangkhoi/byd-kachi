package com.byd.clusternav

import android.content.Context

/**
 * ═══ DẤU "đã lùi về toàn cảnh" của nguồn MỘT KÊNH (2.76 · R3) ═══════════════════════════════════════════════════
 *
 * Hàm mở rộng của [Prefs], cùng tệp `clusternav_prefs` với [PrefsAutomation] (dùng lại `autoPrefs`). Tệp riêng vì
 * đây là **trạng thái**, không phải cấu hình: `CameraSignalController` ghi khi một phiên CHANNEL phải dựng lại ở
 * PANO (HAL từ chối kênh / không có khung đầu), xoá ở lượt xi-nhan kế tiếp; màn Cài đặt chỉ **đọc** để in phụ đề
 * *"lần xi-nhan gần nhất đã lùi về toàn cảnh"* dưới hàng *Nguồn*.
 *
 * Vì sao lưu bền chứ không cờ RAM: Cài đặt và controller cùng tiến trình, nhưng owner mở Cài đặt **sau** khi đã đỗ
 * xe — lúc đó phiên camera đã đóng và cờ RAM đã theo phiên mà đi. CLAUDE.md §5: cờ chỉ để hiển thị, và đây đúng là
 * một cờ hiển thị. Không nằm trong danh sách trắng `prefs_set` (không phải thứ để đặt), không theo hồ sơ (chuyện
 * của XE).
 */

private const val K_CHANNEL_FALLBACK = "camera_channel_fallback"

/** Lý do lần lùi gần nhất (`"rc=false"` / `"no-frame"`), rỗng = phiên CHANNEL gần nhất chạy được. */
fun Prefs.cameraChannelFallback(ctx: Context): String =
    autoPrefs(ctx).getString(K_CHANNEL_FALLBACK, "") ?: ""

/** Ghi lý do lùi (controller). Rỗng = xoá dấu. */
fun Prefs.setCameraChannelFallback(ctx: Context, reason: String) =
    autoPrefs(ctx).edit().putString(K_CHANNEL_FALLBACK, reason).apply()
