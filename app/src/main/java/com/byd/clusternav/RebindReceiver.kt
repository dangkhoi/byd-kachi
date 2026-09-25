package com.byd.clusternav

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import com.byd.clusternav.launcher.KachiHomeActivity
import com.byd.clusternav.modules.navaccess.AccessibilityHealGates

/**
 * SELF-HEAL nav listener — auto-rebind KHÔNG cần mở app / không cần disallow→allow tay.
 *
 * Head-unit BYD hay GIỮ quyền listener nhưng KHÔNG bind (hoặc THẢ binding lúc chạy)
 * → [NavNotificationListener.onNotificationPosted] câm = "nav không lên / flaky".
 * Quyền vẫn ON, Maps vẫn đẩy noti category=navigation, nhưng service không ở trạng thái bound.
 *
 * Ba lớp tự hồi phục:
 *  1. Sự kiện hệ thống: MY_PACKAGE_REPLACED + BOOT_COMPLETED + LOCKED_BOOT_COMPLETED → rebind ngay.
 *  2. [NavNotificationListener.onListenerDisconnected] → rebind ngay khi binding rớt.
 *  3. WATCHDOG định kỳ ([ACTION_WATCHDOG] qua AlarmManager ~60s) → rebind lại kể cả khi
 *     binding CHƯA TỪNG lên (case "sáng nay đi không lên") mà không cần thao tác tay.
 *
 * Đăng ký trong AndroidManifest (manifest-declared, để nhận được kể cả khi process đã chết).
 */
class RebindReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "rebind trigger: $action")
        rebind(context)
        // B2 · BIND-SELFHEAL (owner 2026-09-22): vòng NỀN định kỳ tự chữa binding PHÍM VÔ-LĂNG (accessibility)
        // khi enabled-nhưng-chưa-BOUND — ca "phím chết giữa lúc lái do CPU cao" mà người dùng KHÔNG mở app.
        // `rebind()` ở trên chỉ lo notification-listener; phím vô-lăng đi qua NavAccessibilityService, cần đường
        // heal RIÊNG. `NavConnect.grantAccessibility` idempotent (verify `dumpsys` bound TRƯỚC, chỉ toggle khi
        // enabled-nhưng-chưa-bound) ⇒ gọi định kỳ an toàn. Gate: chỉ khi voice-key BẬT và cờ in-process nói CHƯA
        // bound (tránh dadb thừa mỗi 60s khi đang bound tốt).
        // ⚠ Cửa fail đã soi (owner 2026-09-23 "còn cửa nào fail?"): KHÔNG gate heal bằng tín hiệu IN-PROCESS.
        // Cả cờ `connected` LẪN `AccessibilityManager.getEnabledAccessibilityServiceList` đều có thể DƯƠNG-TÍNH-GIẢ
        // khi service CHẾT mà settings vẫn liệt kê "enabled" (Android giữ enabled qua crash/unbind ngầm — xác nhận
        // tài liệu). Nguồn SỰ THẬT duy nhất về BOUND = `dumpsys accessibility` "Bound services", mà chỉ đọc được
        // qua dadb. `grantAccessibility` idempotent: nó verify `dumpsys` bound TRƯỚC, đã bound → no-op/no-toggle,
        // chưa bound → toggle ép rebind. Nên watchdog GỌI THẲNG (không gate in-process) — 1 lệnh dumpsys/60s là
        // giá chấp nhận để tự-heal ĐÚNG cả ca "enabled nhưng instance chết". Single-flight `grantingAcc` chống trùng.
        // B1 (BG-14, 2026-09-25) — hai sửa, KHÔNG bỏ đường heal:
        //  (a) Đoạn trên nói `getEnabledAccessibilityServiceList` có thể dương-tính-giả — [ĐO AOSP android-10.0.0_r47
        //      `AccessibilityManagerService.java:653-679`] nó duyệt `mBoundServices`, CÙNG danh sách `dumpsys` in ở
        //      "Bound services:{" (`:2563`) ⇒ hai nguồn là một. `grantAccessibility` nay hỏi binder trước, đã bound ⇒
        //      0 lệnh shell (xem `AccessibilityHealGates`); chưa bound / binder ném ⇒ dadb đầy đủ như cũ.
        //  (b) Alarm là LƯỚI PHỤ: FGS keep-alive sống ⇒ watchdog in-process 30 s đã lo ⇒ alarm no-op (cờ tĩnh chết
        //      theo tiến trình ⇒ FGS chết thì alarm lại heal — đúng ca nó sinh ra). Alarm KHÔNG cancel: giữ cho ca chết.
        if (AccessibilityHealGates.alarmShouldHeal(
                voiceKeyEnabled = Prefs.voiceKeyEnabled(context),
                inProcessWatchdogAlive = VoiceKeyKeepAliveService.inProcessWatchdogAlive,
            )
        ) {
            runCatching { NavConnect.grantAccessibility(context.applicationContext) }
                .onFailure { Log.e(TAG, "accessibility self-heal failed", it) }
        } else if (action == ACTION_WATCHDOG) {
            Log.d(TAG, "watchdog alarm no-op: FGS keep-alive đang chạy watchdog in-process (hoặc phím-thoại tắt)")
        }
        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                scheduleWatchdog(context)
                // 1.21 Item 1 (owner): HEADLESS auto-start — do the boot setup in a background
                // foreground-service (BootSetupService) WITHOUT foregrounding any screen on the main
                // display (also dodges the dudu size-compat letterbox). Toggle defaults ON; when OFF, fall
                // back to the 1.14 I5 behaviour (auto-open Home on start). Auto-cast (castBootWork below) is
                // unchanged either way — the bubble/cast track is already headless and self-driven.
                if (Prefs.headlessAutostart(context)) startBootSetup(context) else launchHome(context)
                castBootWork(context, automation = true)
                // B6 (launcher): surface-independent boot orchestration (seed freeform + set-home + ensure the
                // Kachi HOME activity is up so it restores + mounts the saved workspace). Independent of the
                // ClusterNav headlessAutostart toggle above — the launcher should come up ready regardless; its
                // own pref + anti-loop gate live in KachiAutostart.runBoot. Best-effort (never throws).
                KachiAutostartService.startForBoot(context)
            }
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                scheduleWatchdog(context)
                castBootWork(context, automation = false)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                scheduleWatchdog(context)   // #4: alarm mất sau replace/force-stop; đặt lại (idempotent FLAG_UPDATE_CURRENT)
                castBootWork(context, automation = false)
                // OTA auto-reopen (owner 2026-08-12): the installer kills us on update and does NOT
                // relaunch. Bring the app back to the foreground so the user lands on Home after an
                // update instead of a blank screen. Manifest-declared receiver ⇒ delivered even though
                // our process was replaced. Background-activity-start is allowed here because the app
                // holds SYSTEM_ALERT_WINDOW (the overlay/bubble permission) — the standard A10
                // exemption; best-effort (runCatching) if the grant is missing.
                // 1.21 Item 1 (owner): same headless gate as boot — when "Tự khởi động nền" is ON, run the
                // background setup instead of reopening Home after an OTA self-update.
                if (Prefs.headlessAutostart(context)) startBootSetup(context) else launchHome(context)
                // B6 (launcher): the installer kills us on update and does NOT relaunch → ensure the Kachi HOME
                // activity comes back up (which restores + mounts the saved workspace) via the surface-independent
                // orchestration. Same independence + best-effort as the boot path.
                KachiAutostartService.startForBoot(context)
            }
        }
    }

    /**
     * One bounded background pass: read-only Cast rehydration, optional opted-in Bubble presentation
     * and, only for post-unlock BOOT_COMPLETED, the durable-first boot automation record.
     *
     * 2026-08-03: V2 lifecycle rehydrate removed — simplified coordinator owns projection.
     * Only bubble presentation and boot automation remain.
     */
    private fun castBootWork(context: Context, automation: Boolean) {
        val pending = goAsync()
        Thread {
            try {
                val app = context.applicationContext
                // V2 CastAndroidLifecycle.rehydrate removed — simplified coordinator active
                // I5 (1.14): nút nổi chỉ khi Cast BẬT (owner: "nếu có enable cast cluster thì mới start nút nổi").
                // Trước đây start vô điều kiện rồi FloatingBubbleService tự đứng xuống nếu Cast off — nay gate hẳn.
                runCatching {
                    if (com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
                            .coordinator(app).prefs.castEnabled()
                    ) {
                        startOptedInBubble(app)
                    }
                }.onFailure { Log.e(TAG, "bubble restore failed", it) }
                if (automation) {
                    runCatching {
                        com.byd.clusternav.modules.clustercast.CastAutomationService.recordAndEnqueue(app)
                    }.onFailure { Log.e(TAG, "boot automation record failed", it) }
                    Log.i(TAG, "Cast boot automation: disabled (simplified coordinator)")
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    /**
     * Presentation-only restore of the always-on Bubble; it never dispatches Cast work.
     *
     * v0.72: the bubble no longer has an enable/disable toggle (docs/specs/cast-simplified-active-app-toggle.html)
     * -- it starts on every boot as long as the overlay permission is already granted. If it is not,
     * `FloatingBubbleService.onStartCommand` itself sends the user to the one system screen that can
     * grant it, so starting the service unconditionally here is what lets that happen on first boot too.
     */
    private fun startOptedInBubble(app: Context) {
        runCatching {
            app.startForegroundService(
                Intent(app, com.byd.clusternav.modules.clustercast.FloatingBubbleService::class.java),
            )
        }.onFailure { Log.e(TAG, "auto-start bubble failed", it) }
    }

    /**
     * 1.21 Item 1: start the short-lived headless [BootSetupService] instead of foregrounding a screen.
     * A foreground service (not a plain [launchHome]) because the relocated accessibility
     * grant + force-bind takes ~3–5 s over dadb — longer than a BroadcastReceiver's execution budget — so
     * it needs the FGS to keep the process alive. Best-effort: startForegroundService can throw in some
     * background-start-restricted states, so it is wrapped; the nav pipeline + auto-cast still self-heal via
     * their own headless paths (listener bind, castBootWork), and the same setup re-runs when the app is
     * opened (the old ClusterNav screen carried it until it was removed on 2026-09-13; it now lives in
     * [BootSetupService] and [com.byd.clusternav.launcher.KachiHomeActivity]'s startup path).
     */
    private fun startBootSetup(context: Context) {
        runCatching {
            val app = context.applicationContext
            app.startForegroundService(Intent(app, BootSetupService::class.java))
            Log.i(TAG, "headless boot setup requested")
        }.onFailure { Log.e(TAG, "headless boot setup start failed", it) }
    }

    /**
     * Bring the app to the foreground. Used both on car BOOT_COMPLETED (I5 1.14:
     * auto-open on start, per owner) and after a self-update (MY_PACKAGE_REPLACED). Uses the package's own
     * launcher intent with NEW_TASK; CLEAR_TOP so a stale task isn't stacked. Best-effort: background
     * activity-start needs the SYSTEM_ALERT_WINDOW exemption, so this may be a no-op if the overlay grant is
     * absent — it never throws.
     *
     * NOTE (S3, 2026-09-13 — docs/specs/kachi-remove-legacy-screen.html R1): the launcher intent resolves to
     * [KachiHomeActivity], and so does the fallback below — the old ClusterNav screen was removed on
     * 2026-09-13, so Kachi is the only screen this can open. It only fires when the `headless_autostart`
     * toggle is OFF; the default-ON path still starts [BootSetupService] with no UI.
     *
     * NOTE 2 (S3 · soát 2026-09-13): nhánh này là nhánh KHÔNG chạy [BootSetupService], nên nó phải tự gọi
     * [BootSetupService.forcedPrefs] — ba khoá ép-mỗi-lần-nổ-máy (`hud`=false · `interpolate`/`acc_booster`=true)
     * trước 2026-09-13 do màn ClusterNav cũ ghi đè mỗi lần mở; gỡ màn mà chỉ đặt lại ở nhánh headless thì máy
     * nào TẮT *"Tự khởi động nền"* sẽ đóng băng giá trị cũ vĩnh viễn (xem KDoc của hàm đó).
     */
    private fun launchHome(context: Context) {
        runCatching { BootSetupService.forcedPrefs(context.applicationContext) }
            .onFailure { Log.w(TAG, "forced prefs failed", it) }
        runCatching {
            val app = context.applicationContext
            val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                ?: Intent(app, KachiHomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(launch)
            Log.i(TAG, "launch Home requested")
        }.onFailure { Log.e(TAG, "launch Home failed", it) }
    }
    companion object {
        private const val TAG = "NavRebind"
        const val ACTION_WATCHDOG = "com.byd.clusternav.REBIND_WATCHDOG"

        private const val INTERVAL_MS = 60_000L

        /** Ép hệ thống bind lại nav listener (an toàn gọi nhiều lần; no-op nếu đã bound). */
        fun rebind(context: Context) {
            runCatching {
                NotificationListenerService.requestRebind(
                    ComponentName(context, NavNotificationListener::class.java)
                )
            }.onFailure { Log.e(TAG, "requestRebind failed", it) }
        }

        /** Đặt alarm lặp ~60s gọi lại [rebind] → tự hồi phục binding khi đang chạy/đỗ. */
        fun scheduleWatchdog(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, RebindReceiver::class.java).setAction(ACTION_WATCHDOG),
                flags
            )
            runCatching {
                am.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,   // R4: WAKEUP để watchdog vẫn chạy khi head-unit SoC suspend
                    SystemClock.elapsedRealtime() + INTERVAL_MS,
                    INTERVAL_MS,
                    pi
                )
            }.onFailure { Log.e(TAG, "scheduleWatchdog failed", it) }
        }
    }
}
