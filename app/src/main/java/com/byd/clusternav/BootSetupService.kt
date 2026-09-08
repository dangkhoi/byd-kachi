package com.byd.clusternav

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.byd.clusternav.modules.navaccess.NavAccessibilitySource
import com.byd.clusternav.navigation.NavigationOutputTarget
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * HEADLESS boot setup (1.21 Item 1). Short-lived foreground service started by [RebindReceiver] on
 * BOOT_COMPLETED / MY_PACKAGE_REPLACED when "Tự khởi động nền" ([Prefs.headlessAutostart]) is ON, so the
 * app performs its boot setup WITHOUT foregrounding [MainActivity] on the main display (bonus: dodges the
 * dudu size-compat letterbox — MainActivity never auto-foregrounds).
 *
 * Relocates the ONLY boot-setup that was tied to MainActivity.onCreate:
 *   1. accessibility grant + force-bind ([NavConnect.grantAccessibility] — includes the 1.20 force-bind), and
 *   2. re-assert the cluster-lane output ([NavRepository.setOutputEnabled] CLUSTER_LANE=true) — covers an
 *      OLD persisted `lane=false` pref for a user who upgraded and never opens the app in headless mode
 *      (MainActivity's Prefs.setLane(true) migration would otherwise never run for them).
 * Both are ADDITIVE and idempotent; MainActivity.onCreate keeps the same setup for the user-opens-app case.
 *
 * NOT touched here (already headless): the nav pipeline (NavNotificationListener.onListenerConnected →
 * NavRepository.setPermission(GRANTED) → connect()) and auto-cast (the cast bubble service is the sole
 * autostart driver, started by RebindReceiver.castBootWork when Cast is enabled).
 *
 * Safety:
 *  • [startForeground] is called FIRST (well within the ~5 s startForegroundService() budget) so a
 *    background start can never be killed with RemoteServiceException.
 *  • The setup runs on a background thread wrapped in runCatching → it can NEVER crash the process.
 *  • The service ALWAYS [finish]es (stopForeground + stopSelf) — the call sits OUTSIDE the runCatching, so
 *    an exception or interrupt still tears the service down.
 *  • The FGS (and therefore the process) is kept alive until the async dadb grant reports back (bounded by
 *    [GRANT_TIMEOUT_MS]) — the plan's rationale for a foreground service: the grant takes ~3–5 s, longer
 *    than a BroadcastReceiver's budget, so a pure-boot stopSelf must not kill it early.
 */
class BootSetupService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() contract: go foreground within ~5 s or the system kills us. Do it FIRST,
        // before any (blocking) work; if the platform denies it, stop cleanly.
        if (!startForegroundOnce()) { stopSelf(startId); return START_NOT_STICKY }
        Thread({
            runCatching {
                // Accessibility grant + 1.20 force-bind: cần khi Nav+HUD (booster đọc GMaps) HOẶC voice-key
                // (nút vật lý → trợ lý) bật. Voice-key KHÔNG phụ thuộc Nav+HUD (owner 2026-09-01: hai tính năng
                // RIÊNG — trước gate chung Nav+HUD nên phím-thoại chết sau boot khi Nav+HUD tắt). Chỉ escalate khi
                // service CHƯA bound (idempotent: grantAccessibility verify dumpsys trước khi toggle → no-op/no
                // flicker nếu đã bound). Async trên thread riêng, báo về main looper → đếm latch; giữ FGS sống tới
                // khi grant xong (bounded GRANT_TIMEOUT_MS).
                if (Prefs.enabled(applicationContext) || Prefs.voiceKeyEnabled(applicationContext)) {
                    if (!NavAccessibilitySource.connected) {
                        val latch = CountDownLatch(1)
                        NavConnect.grantAccessibility(applicationContext) { latch.countDown() }
                        latch.await(GRANT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    }
                }
                if (Prefs.enabled(applicationContext)) {
                    // Re-assert the cluster-lane output (belt-and-suspenders for an old lane=false pref). Nav+HUD only.
                    NavRepository.setOutputEnabled(
                        applicationContext, NavigationOutputTarget.CLUSTER_LANE, true,
                    )
                }
                // BOOT headless: auto-start VietMap chạy trong FGS RIÊNG ([VietMapAutostartService]) — KHÔNG
                // block chuỗi setup này (ghế / lọc bụi / Gemini phía dưới chạy NGAY, không đợi VietMap). Nhánh
                // bóng poll tới khi VietMap vào map (tuỳ network) nên tách ra service riêng; boot → về HOME sau.
                // Gate (badge / bóng / cast) + chống-loop nằm trong runNow của service.
                VietMapAutostartService.startForBoot(applicationContext)
                // Ghế: áp mức làm-mát/sưởi lên HAL ~5s sau boot nếu công tắc BẬT (headless boot cũng tự áp,
                // giống app tham chiếu). Gate seatComfortEnabled + degrade-safe nằm trong applyOnStart.
                com.byd.clusternav.comfort.SeatComfortApplier.applyOnStart(applicationContext)
                // Lọc bụi mịn PM2.5: bật lọc-liên-tục (không popup) ~5s sau boot nếu công tắc BẬT. Gate
                // pm25FilterEnabled + degrade-safe nằm trong applyOnStart.
                com.byd.clusternav.comfort.Pm25FilterApplier.applyOnStart(applicationContext)
                // F4e boot (owner 08-25): boot headless KHÔNG mở MainActivity ⇒ onCreate không chạy ⇒ trợ lý
                // hệ thống chưa được đặt = Gemini ⇒ hold-mic → keyevent 231 route sai. Đặt luôn ở đây NẾU có
                // binding Gemini, để hold-mic → Gemini ready NGAY sau nổ máy mà KHÔNG cần mở app (owner
                // 08-25: "kể cả khởi động nền hay full app đều enable service gemini lên là OK").
                // retry NONE: boot owner KHÔNG ở màn hình để bấm "Cho phép gỡ lỗi USB" ⇒ MỘT lần, không chờ
                // ~31s (tránh treo boot — F6). Hỏng (chưa cấp quyền) ⇒ bỏ; owner mở app lần đầu thì
                // MainActivity.onCreate re-apply với AWAIT_ADB_APPROVAL (có mặt owner để cấp quyền).
                if (com.byd.clusternav.modules.voicekey.AssistantLauncher.hasGeminiBinding(applicationContext)) {
                    val err = com.byd.clusternav.modules.voicekey.AssistantLauncher.setSystemAssistant(
                        applicationContext, com.byd.clusternav.carexec.LocalShellRetry.NONE,
                    )
                    if (err.isNotEmpty()) Log.i(TAG, "boot re-apply Gemini assistant: $err (owner mở app sẽ thử lại có chờ cấp quyền)")
                }
            }.onFailure { Log.e(TAG, "headless boot setup failed", it) }
            finish(startId)
        }, "boot-setup").start()
        return START_NOT_STICKY
    }

    /** ALWAYS the last step: leave the foreground state + stop the service. Safe to call once per start. */
    private fun finish(startId: Int) {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            .onFailure { Log.w(TAG, "stopForeground failed", it) }
        stopSelf(startId)
    }

    private fun startForegroundOnce(): Boolean = runCatching {
        startForeground(NOTIFICATION_ID, notification())
        true
    }.getOrElse {
        Log.e(TAG, "startForeground denied", it)
        false
    }

    private fun notification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "ClusterNav khởi động", NotificationManager.IMPORTANCE_MIN),
            )
        }
        @Suppress("DEPRECATION")
        return android.app.Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("ClusterNav")
            .setContentText(Lang.t("Đang khởi động nền…", "Starting in background…"))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "BootSetup"
        // Distinct from the cast bubble service (1042) / CastAutomationService so the two can coexist on boot.
        private const val NOTIFICATION_ID = 1043
        private const val CHANNEL_ID = "clusternav_boot_setup"
        // Upper bound on how long the FGS lingers waiting for the async dadb accessibility grant to report
        // back (settle 1.2 s + toggle 0.8 s + dumpsys/dadb round-trips ≈ 3–6 s). Bounded so we ALWAYS stop.
        private const val GRANT_TIMEOUT_MS = 8_000L
    }
}
