package com.byd.clusternav

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * FGS MẢNH giữ tiến trình launcher SỐNG khi phím-thoại (voice key) đang bật.
 *
 * ## Vì sao (deep-pass 2026-09-23, [P1] #3 — "reset mới hết" ở tầng process-death)
 * [ĐO logcat 2026-07-30] ROM DiLink3 có `ssc_skip`: khi tiến trình đích **NOT RUNNING**, nó **DROP cả broadcast
 * `REBIND_WATCHDOG` LẪN `bindServiceLocked` từ system_server** ("UID xxxx is not running ... ignored !!!").
 * Chuỗi chết: launcher chết (SIGSEGV/LMK dưới load) → a11y unbound → system_server bind lại bị `ssc_skip` →
 * watchdog 60s cũng bị DROP (không dựng lại tiến trình) → phím CHẾT tới khi REBOOT = đúng "reset mới hết".
 * Trước đây che khuất nhờ [FloatingBubbleService] (FGS của CAST) giữ tiến trình sống — nhưng nó CHỈ chạy khi
 * bật Chiếu cụm; owner dùng phím-thoại mà KHÔNG bật cast thì không có gì giữ tiến trình.
 *
 * Service này KHÔNG làm gì ngoài việc TỒN TẠI (IMPORTANCE_MIN, không mic, không nhịp, không dadb): chỉ cần tiến
 * trình luôn RUNNING thì `ssc_skip` không áp ⇒ watchdog broadcast tới được ⇒ phím tự-heal. START_STICKY để hệ
 * dựng lại nếu bị kill. Tự dừng khi phím-thoại TẮT (không giữ tiến trình vô cớ).
 *
 * Chung sống với các FGS khác nhờ NOTIFICATION_ID/CHANNEL riêng. Never exported.
 */
class VoiceKeyKeepAliveService : Service() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var watching = false

    /**
     * Watchdog IN-PROCESS (owner 2026-09-25 "anh em lỗi mãi"). [ĐO xe] a11y ENABLED mà KHÔNG BOUND, và broadcast
     * `REBIND_WATCHDOG` bị ROM **`ssc_skip` DROP** dù tiến trình đang RUNNING ⇒ watchdog-qua-AlarmManager-broadcast
     * KHÔNG tin cậy trên DiLink. Đây là vòng kiểm chạy THẲNG trong tiến trình sống (FGS): mỗi [WATCHDOG_MS] đọc
     * [NavConnect.isAccessibilityBound] (AccessibilityManager, không cờ kẹt) — chưa bound thì `grantAccessibility`
     * (idempotent: verify dumpsys, toggle rebind qua dadb khi cần). Không broadcast, không AlarmManager ⇒ ROM
     * không có gì để drop. Đây là đường tự-heal CHÍNH; broadcast/alarm giữ làm lưới phụ.
     */
    private val watchdog = object : Runnable {
        override fun run() {
            if (!Prefs.voiceKeyEnabled(applicationContext)) return
            runCatching {
                if (!NavConnect.isAccessibilityBound(applicationContext)) {
                    Log.w(TAG, "a11y KHÔNG bound → re-grant (in-process watchdog)")
                    NavConnect.grantAccessibility(applicationContext)
                }
            }.onFailure { Log.w(TAG, "watchdog re-grant lỗi: ${it.message}") }
            handler.postDelayed(this, WATCHDOG_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() contract: lên foreground trong ~5s hoặc bị kill.
        if (!startForegroundOnce()) { stopSelf(startId); return START_NOT_STICKY }
        // Phím-thoại TẮT ⇒ không cần giữ tiến trình → đứng xuống (stopSelf sau startForeground là hợp lệ).
        if (!Prefs.voiceKeyEnabled(applicationContext)) {
            Log.i(TAG, "voice key OFF → keep-alive stand down")
            handler.removeCallbacks(watchdog); watching = false; inProcessWatchdogAlive = false
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // Đảm bảo watchdog alarm còn sống (idempotent) — LƯỚI PHỤ (broadcast bị ssc_skip nên không đủ tin).
        runCatching { RebindReceiver.scheduleWatchdog(applicationContext) }
        // Watchdog IN-PROCESS — đường tự-heal CHÍNH (không bị ssc_skip). Chạy một lần, tự lặp.
        if (!watching) { watching = true; handler.postDelayed(watchdog, WATCHDOG_FIRST_MS) }
        inProcessWatchdogAlive = true   // B1: alarm 60 s thấy cờ này ⇒ no-op (lưới phụ); tiến trình chết ⇒ cờ chết theo
        Log.i(TAG, "voice-key keep-alive foreground + in-process a11y watchdog")
        return START_STICKY   // hệ dựng lại nếu bị kill → tiến trình quay lại RUNNING
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdog); watching = false; inProcessWatchdogAlive = false
        super.onDestroy()
    }

    private fun startForegroundOnce(): Boolean = runCatching {
        startForeground(NOTIFICATION_ID, notification())
        true
    }.getOrElse { Log.e(TAG, "startForeground denied", it); false }

    private fun notification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Kachi phím-thoại", NotificationManager.IMPORTANCE_MIN),
            )
        }
        @Suppress("DEPRECATION")
        return android.app.Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Kachi")
            .setContentText(Lang.t("Phím-thoại đang bật", "Voice key active"))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "VoiceKeyKeepAlive"

        /**
         * BẢNG ID THÔNG BÁO FGS — nguồn duy nhất, khoá bởi `ForegroundNotificationIdGuardTest` (quét `app/src/main`,
         * mọi ID phải DUY NHẤT). Hai FGS trùng id ⇒ `stopForeground(REMOVE)` cái này GỠ thông báo cái kia (lỗi hợp
         * đồng FGS, kiểm kê `perf-inventory-2026-09-25.md` BG-09/12/23/10; trước 2026-09-25 1044 ×2 và 1045 ×2).
         *
         * | ID   | Service                                   |
         * |------|-------------------------------------------|
         * | 1042 | `clustercast.BubbleForegroundNotice.ID` (FloatingBubbleService) |
         * | 1043 | `BootSetupService`                        |
         * | 1044 | `automation.AutomationService`            |
         * | 1045 | `VoiceKeyKeepAliveService` (file này)     |
         * | 1046 | `VietMapAutostartService`                 |
         * | 1047 | `KachiAutostartService`                   |
         * | 4801 | `launcher.voice.VoiceWakeService`         |
         *
         * Thêm FGS mới: lấy số kế tiếp, ghi vào bảng này; test sẽ đỏ nếu trùng.
         */
        private const val NOTIFICATION_ID = 1045

        /**
         * Sự thật "watchdog in-process đang chạy" cho [RebindReceiver] (alarm 60 s = lưới phụ ⇒ no-op khi cờ này bật).
         * Cờ tĩnh sống theo tiến trình: FGS/tiến trình chết ⇒ về false ⇒ alarm heal như cũ. Không đọc từ shell.
         */
        @Volatile var inProcessWatchdogAlive: Boolean = false
            private set
        private const val CHANNEL_ID = "clusternav_voicekey_keepalive"

        /** Chu kỳ watchdog in-process. 30s: đủ nhanh để phím rớt tự về trong nửa phút, đủ thưa để không tốn. */
        private const val WATCHDOG_MS = 30_000L

        /** Lần kiểm đầu sau khi FGS lên (cho hệ ổn định trước khi đọc bound). */
        private const val WATCHDOG_FIRST_MS = 5_000L

        /** Bật/tắt theo pref phím-thoại. Gọi lúc boot, mở app, và khi toggle phím-thoại. */
        fun sync(ctx: Context) {
            val app = ctx.applicationContext
            val i = Intent(app, VoiceKeyKeepAliveService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i) else app.startService(i)
            }.onFailure { Log.w(TAG, "start VoiceKeyKeepAliveService failed: ${it.message}") }
        }
    }
}
