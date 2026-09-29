package com.byd.clusternav

import android.content.Context

/**
 * ═══ Hai MARKER của tự chữa phím vô-lăng theo vòng đời xe (2.83, lớp 1 tắt máy · lớp 2 mở xe) ═══
 * (+ mốc "đã chấm điểm" [a11yScoredFor] ở cuối tệp — một lượt chấm `sau-chua-*` mỗi lượt leo.)
 *
 * Tách khỏi `Prefs.kt` (488 dòng, trần 500 — CLAUDE.md §4.1) theo đúng khuôn [PrefsBadge]/`PrefsInputd`: hàm mở rộng
 * của [Prefs], **cùng tệp `clusternav_prefs`** qua [Prefs.sp]. Không phải cài đặt người dùng: không có UI, không theo
 * hồ sơ (cùng loại với `a11y_forcestop_elapsed` / `a11y_deep_sleep_ms` ở `Prefs.kt` — trạng thái nội bộ theo XE).
 *
 * Cả hai là mốc `SystemClock.elapsedRealtime()` (về 0 khi khởi động lại ⇒ mốc lớn hơn "bây giờ" = đời máy trước, xem
 * [com.byd.clusternav.modules.navaccess.AccessibilityHealGates.escalatedThisBoot]); `-1` = chưa từng.
 *
 * ⚠ `commit()` chứ KHÔNG `apply()` — cùng lý do với `Prefs.setA11yEscalatedAt`: marker được ghi TRƯỚC khi đo và
 * TRƯỚC khi bắn lệnh tự giết tiến trình (CLAUDE.md §5). `apply()` ghi nền; tiến trình chết trước khi flush là mất
 * marker ⇒ tiến trình dựng lại tưởng sự kiện chưa ai lo ⇒ lượt thứ hai cho CÙNG sự kiện = vòng lặp giết launcher.
 */
private const val K_A11Y_TAT_MAY_AT = "a11y_tat_may_elapsed"
private const val K_A11Y_MO_XE_AT = "a11y_mo_xe_elapsed"

/** Mốc claim của lượt "tắt máy" gần nhất (lớp 1) — một lượt mỗi lần tắt máy. */
fun Prefs.a11yTatMayAt(ctx: Context): Long = sp(ctx).getLong(K_A11Y_TAT_MAY_AT, -1L)

fun Prefs.setA11yTatMayAt(ctx: Context, v: Long): Boolean = sp(ctx).edit().putLong(K_A11Y_TAT_MAY_AT, v).commit()

/**
 * Mốc `ACTION_SCREEN_ON` gần nhất đã được claim (lớp 2). Vừa là hạn mức "một lượt mỗi lần mở xe", vừa là bằng chứng
 * "đã có một lần mở xe sau lượt tắt máy trước" để lớp 1 nhận ra lần tắt máy MỚI.
 */
fun Prefs.a11yMoXeAt(ctx: Context): Long = sp(ctx).getLong(K_A11Y_MO_XE_AT, -1L)

fun Prefs.setA11yMoXeAt(ctx: Context, v: Long): Boolean = sp(ctx).edit().putLong(K_A11Y_MO_XE_AT, v).commit()

private const val K_A11Y_SCORED_FOR = "a11y_scored_for_elapsed"

/**
 * Mốc leo ([Prefs.a11yEscalatedAt]) mà một tiến trình đã NHẬN chấm điểm `sau-chua-*` — một lượt chấm mỗi lượt leo,
 * luật ở [com.byd.clusternav.modules.navaccess.AccessibilityHealGates.firstStartAfterHeal]. `-1` = chưa từng.
 * Ghi `commit()` TRƯỚC khi chấm (cùng lý do marker ở trên): tiến trình chết giữa chừng thì lượt sau không chấm bừa.
 */
fun Prefs.a11yScoredFor(ctx: Context): Long = sp(ctx).getLong(K_A11Y_SCORED_FOR, -1L)

fun Prefs.setA11yScoredFor(ctx: Context, v: Long): Boolean = sp(ctx).edit().putLong(K_A11Y_SCORED_FOR, v).commit()
