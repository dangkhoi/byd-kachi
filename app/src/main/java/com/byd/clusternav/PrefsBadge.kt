package com.byd.clusternav

import android.content.Context

/**
 * ═══ Khoá của BIỂN BÁO TỐC ĐỘ trên cụm + BONG BÓNG VietMap — tách khỏi [Prefs] theo VAI ═══
 *
 * Tách THUẦN khỏi `Prefs.kt` (558 dòng → trần 500, L6-debt 2026-09-27), cùng cách [PrefsAutomation] / `PrefsInputd`: hàm mở
 * rộng của [Prefs], **cùng tệp `clusternav_prefs`** qua [Prefs.sp] (không mở cửa thứ hai vào cùng chỗ lưu). Thân hàm và
 * khoá giữ nguyên byte; bốn hằng `BADGE_DEFAULT_CENTER_*` · `BADGE_MIGRATE_CLUSTER_*` ở lại `Prefs` (API công khai).
 */

// ─── Cluster speed-limit badge overlay: ABSOLUTE POSITION + SIZE (persisted, live-adjustable) ─
// 2026-08-17 (spec speed-badge-placement-vietmap-logging §4.3): the old 4-corner model (corner + dp
// nudge) was REPLACED by an absolute CENTRE in cluster px so the driver can drag the badge anywhere on
// the cluster, not just to a corner the cast app kept covering. badgeCenterX/Y is the CENTRE of the badge
// in display-1 pixel coords; the overlay clamps it on-screen (BadgeLayout.clampCenter) and converts to a
// TOP|LEFT x/y (BadgeLayout.topLeftFromCenter). Size still clamps 60..240 dp on BOTH read & write via the
// tested BadgeLayout.clampSizeDp so a corrupt stored value can never blow the overlay up or shrink it away.
private const val K_BADGE_SIZE_DP = "badge_size_dp"
private const val K_BADGE_CENTER_X = "badge_center_x"
private const val K_BADGE_CENTER_Y = "badge_center_y"
private const val K_BADGE_ENABLED = "badge_enabled"

// ★ Badge on/off: MẶC ĐỊNH TẮT (owner 2026-08-28: mặc định tắt biển báo tốc độ VietMap trên cụm; trước
// đây 2026-08-18 mặc định BẬT). Gate riêng cho biển báo tốc độ trên CỤM (overlay display 1) — độc lập với
// nguồn tốc độ/HUD. Khi TẮT: SpeedBadgeOverlay.show() gỡ overlay + không attach (real pipeline lẫn debug
// force-show đều tôn trọng vì cả hai đi qua show()). Đọc trực tiếp trong overlay trên main handler
// (SharedPreferences cache sẵn nên rẻ, không chạm notification thread).
fun Prefs.badgeEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(K_BADGE_ENABLED, false)
fun Prefs.setBadgeEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_BADGE_ENABLED, v).apply()

// ★ Biển "giới hạn sắp tới" (spec upcoming-speed-limit-badge, owner 2026-08-18): MẶC ĐỊNH BẬT (theo tiền lệ
// badge). Gate riêng cho biển nhỏ (~70%) + nhãn cự ly đếm lùi, vẽ NGAY DƯỚI badge chính trên CỤM. KHÔNG có
// ngưỡng cự ly riêng (OQ2 — hiện/ẩn theo VietMap). TẮT → SpeedBadgeOverlay không bao giờ vẽ biển sắp-tới.
private const val K_SHOW_UPCOMING_BADGE = "show_upcoming_badge"
fun Prefs.showUpcomingBadge(ctx: Context): Boolean = sp(ctx).getBoolean(K_SHOW_UPCOMING_BADGE, true)
fun Prefs.setShowUpcomingBadge(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_SHOW_UPCOMING_BADGE, v).apply()
// B3.20 — road-alert / speed-camera chip toggle. Default OFF: it adds a THIRD element on the cluster, so it
// stays opt-in and never disturbs the owner's existing badge layout until turned on.
private const val K_SHOW_ALERT_CHIP = "show_alert_chip"
fun Prefs.showAlertChip(ctx: Context): Boolean = sp(ctx).getBoolean(K_SHOW_ALERT_CHIP, false)
fun Prefs.setShowAlertChip(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_SHOW_ALERT_CHIP, v).apply()

// ─── VietMap bubble-on-cluster toggle (owner 2026-08-28) ────────────────────────────────────
// Gate cho VỊ TRÍ bong bóng VietMap trên cụm (panel kéo-thả VmBubblePlacementView + VmOverlayPosition).
// MẶC ĐỊNH TẮT (opt-in). Giống badge tốc độ: khi BẬT sẽ auto-start VietMap MỘT LẦN ([VietMapAutostart],
// dedup bằng pidof) để bản mod VietMap có mặt mà nhận broadcast VM_BUBBLE_POS. runNow() auto-start nếu
// badge HOẶC cờ này bật — hai cờ độc lập, dedup pidof đảm bảo chỉ start một lần dù cả hai bật.
private const val K_VM_BUBBLE_ENABLED = "vm_bubble_enabled"
fun Prefs.vmBubbleEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(K_VM_BUBBLE_ENABLED, false)
fun Prefs.setVmBubbleEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_VM_BUBBLE_ENABLED, v).apply()

// 2.90 · R8 (review an toàn hiện trường) — người lái CHỦ ĐỘNG ẩn bóng VietMap trên cụm. MẶC ĐỊNH false: trước 2.90 bản mod
// LUÔN hiện bóng, còn `vm_bubble_enabled` mặc định TẮT (nghĩa của nó vẫn là "tự mở VietMap") ⇒ suy "ẩn" từ nó sẽ gỡ bóng của
// mọi người chưa từng bật công tắc sau khi nâng cấp. Chỉ [ClusterNavBridge.setVmBubbleShown] ghi cờ này (2.91 · F1: công tắc hiện
// bóng đọc/ghi CHÍNH cờ này; `vm_bubble_enabled` có hàng riêng "Tự mở VietMap cho bong bóng").
private const val K_VM_BUBBLE_HIDDEN = "vm_bubble_hidden"
fun Prefs.vmBubbleHidden(ctx: Context): Boolean = sp(ctx).getBoolean(K_VM_BUBBLE_HIDDEN, false)
fun Prefs.setVmBubbleHidden(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_VM_BUBBLE_HIDDEN, v).apply()

// 2.89 · B2 VM-PREREQ-TRUTH: cờ một-lần `vm_float_whitelist_applied` đã BỎ (không còn đọc/ghi). Nó chặn lại công thức
// "byd_float_app_list + appops SYSTEM_ALERT_WINDOW" sau lần đầu, kể cả khi VietMap đã gỡ-cài-lại (ROM xoá appop theo gói) —
// owner phải cấp tay lại. Nay quyền vẽ nổi đọc sự thật mỗi lượt (`AppPrereqs` · `OverlayOpRead`); `byd_float_app_list` chỉ
// ghi khi đọc thấy VẮNG và [ĐO nguồn ROM 2602030] không chỗ nào ở system/product đọc nó. Khoá cũ còn trên máy được xếp
// loại ở `ProfileScopeCluster.DEVICE_KEYS` (đời cũ, không chép).
// Legacy keys (4-corner model) — read once by [migrateBadgeIfNeeded] to seed the centre, never written.
private const val K_BADGE_CORNER = "badge_corner"
private const val K_BADGE_DX = "badge_dx"
private const val K_BADGE_DY = "badge_dy"


fun Prefs.badgeSizeDp(ctx: Context): Int =
    com.byd.clusternav.speedbadge.BadgeLayout.clampSizeDp(
        sp(ctx).getInt(K_BADGE_SIZE_DP, com.byd.clusternav.speedbadge.BadgeLayout.SIZE_DEFAULT_DP),
    )
fun Prefs.setBadgeSizeDp(ctx: Context, v: Int) =
    sp(ctx).edit().putInt(K_BADGE_SIZE_DP, com.byd.clusternav.speedbadge.BadgeLayout.clampSizeDp(v)).apply()

fun Prefs.badgeCenterX(ctx: Context): Int {
    migrateBadgeIfNeeded(ctx)
    return sp(ctx).getInt(K_BADGE_CENTER_X, BADGE_DEFAULT_CENTER_X)
}
fun Prefs.setBadgeCenterX(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_BADGE_CENTER_X, v).apply()

fun Prefs.badgeCenterY(ctx: Context): Int {
    migrateBadgeIfNeeded(ctx)
    return sp(ctx).getInt(K_BADGE_CENTER_Y, BADGE_DEFAULT_CENTER_Y)
}
fun Prefs.setBadgeCenterY(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_BADGE_CENTER_Y, v).apply()

/**
 * ONE-TIME migration from the legacy 4-corner model to an absolute centre. Fires only when the old
 * corner key is present AND the new centre keys are absent, so it runs at most once per install and never
 * clobbers a user's chosen absolute position (a fresh install just uses the defaults). Computes the centre
 * from old corner + dp nudge + size on the default 1920×720 cluster, then clamps it on-screen. Approximate
 * by design (treats stored dp as px on the migration cluster) — it only needs to keep existing users near
 * their old corner rather than jumping to the default.
 */
private fun Prefs.migrateBadgeIfNeeded(ctx: Context) {
    val p = sp(ctx)
    if (!p.contains(K_BADGE_CORNER)) return                                   // fresh install → defaults
    if (p.contains(K_BADGE_CENTER_X) || p.contains(K_BADGE_CENTER_Y)) return  // already migrated / user-set
    val corner = p.getInt(K_BADGE_CORNER, 1)                                  // legacy ids: 0=TL,1=TR,2=BL,3=BR
    val dx = p.getInt(K_BADGE_DX, 24)
    val dy = p.getInt(K_BADGE_DY, 24)
    val sizePx = com.byd.clusternav.speedbadge.BadgeLayout.clampSizeDp(
        p.getInt(K_BADGE_SIZE_DP, com.byd.clusternav.speedbadge.BadgeLayout.SIZE_DEFAULT_DP),
    )
    val half = sizePx / 2
    val isLeft = corner == 0 || corner == 2
    val isBottom = corner == 2 || corner == 3
    val cx = if (isLeft) dx + half else BADGE_MIGRATE_CLUSTER_W - dx - half
    val cy = if (isBottom) BADGE_MIGRATE_CLUSTER_H - dy - half else dy + half
    val (ccx, ccy) = com.byd.clusternav.speedbadge.BadgeLayout.clampCenter(
        cx, cy, sizePx, BADGE_MIGRATE_CLUSTER_W, BADGE_MIGRATE_CLUSTER_H,
    )
    p.edit().putInt(K_BADGE_CENTER_X, ccx).putInt(K_BADGE_CENTER_Y, ccy).apply()
}
