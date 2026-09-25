package com.byd.clusternav.modules.navaccess

/**
 * CỔNG THUẦN cho hai đường tự-heal accessibility (B1 · `docs/specs/kachi-closeout-hardening.html` R3, BG-11/BG-14).
 *
 * ## Vì sao có cổng này
 * [ĐO máy ảo 2.65, đứng yên] alarm `REBIND_WATCHDOG` (window 45 s, lặp 60 s) + watchdog in-process 30 s của FGS
 * keep-alive cùng gọi `grantAccessibility`, và mỗi lượt grant — KỂ CẢ khi service đã bound — đi trọn đường dadb:
 * `settings get` → `settings put secure accessibility_enabled 1` (GHI Secure Settings) → sleep 1,2 s → `settings get`
 * → `dumpsys accessibility` = ≥ 4 lệnh shell + 1 ghi mỗi phút để rồi log "đã BOUND — không toggle".
 *
 * ## Vì sao gate bằng AccessibilityManager là ĐÚNG (đọc source, không nhớ — CLAUDE.md §3)
 * [ĐO AOSP `android-10.0.0_r47` `services/accessibility/.../AccessibilityManagerService.java`]
 *  - `:653-679` `getEnabledAccessibilityServiceList(feedbackType, userId)` duyệt **`userState.mBoundServices`**
 *    (lọc `mFeedbackType & feedbackType != 0`; app khai `feedbackGeneric` ⇒ luôn khớp `FEEDBACK_ALL_MASK`).
 *  - `:2563-2573` `dump()` in `"Bound services:{"` cũng từ **cùng** `userState.mBoundServices`.
 *  ⇒ Danh sách AccessibilityManager trả về **== danh sách `dumpsys accessibility` "Bound services"** mà
 *  [AccessibilityRebind.isClusterNavBound] parse qua dadb. Hai nguồn là MỘT; nguồn binder rẻ hơn ~4 lệnh shell.
 *  Android 12 (`android-12.0.0_r1` `:904-933`) giữ nguyên logic. Cờ RAM `NavAccessibilitySource.connected` thì
 *  KHÔNG dùng để gate (có thể kẹt true — KDoc `NavConnect.isAccessibilityBound`).
 *
 * Chỉ khi binder **trả lời được** và nói "bound" mới bỏ shell; binder ném/null (`null`) ⇒ đi đường shell như cũ
 * (không mất đường tự-heal — fix on-car 1.78, `docs/diagnostics/oncar-final-runbook-1.87.md:132`).
 */
object AccessibilityHealGates {

    /**
     * Một lượt grant: [boundPerManager] = kết quả AccessibilityManager (`true`/`false`, `null` = không hỏi được).
     * Đã bound ⇒ chạy [skipped] (0 lệnh shell, 0 ghi Secure Settings). Còn lại ⇒ chạy [shell] (đường cũ, đủ bước).
     */
    inline fun <R> grantOrSkip(boundPerManager: Boolean?, skipped: () -> R, shell: () -> R): R =
        if (boundPerManager == true) skipped() else shell()

    /**
     * Alarm 60 s có nên gọi heal không. Alarm là LƯỚI PHỤ: khi FGS keep-alive đang sống thì watchdog in-process
     * (30 s, không bị ROM `ssc_skip` drop) đã lo ⇒ alarm no-op, không tốn thêm binder/shell. FGS chết ⇒ cờ tĩnh
     * chết theo tiến trình ⇒ `inProcessWatchdogAlive=false` ⇒ alarm heal như cũ (đúng ca alarm sinh ra để chữa).
     */
    fun alarmShouldHeal(voiceKeyEnabled: Boolean, inProcessWatchdogAlive: Boolean): Boolean =
        voiceKeyEnabled && !inProcessWatchdogAlive
}
