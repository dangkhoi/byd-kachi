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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() contract: lên foreground trong ~5s hoặc bị kill.
        if (!startForegroundOnce()) { stopSelf(startId); return START_NOT_STICKY }
        // Phím-thoại TẮT ⇒ không cần giữ tiến trình → đứng xuống (stopSelf sau startForeground là hợp lệ).
        if (!Prefs.voiceKeyEnabled(applicationContext)) {
            Log.i(TAG, "voice key OFF → keep-alive stand down")
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // Đảm bảo watchdog alarm còn sống (idempotent) — cùng nhịp giữ-tiến-trình.
        runCatching { RebindReceiver.scheduleWatchdog(applicationContext) }
        Log.i(TAG, "voice-key keep-alive foreground (giữ tiến trình cho watchdog/onKeyEvent)")
        return START_STICKY   // hệ dựng lại nếu bị kill → tiến trình quay lại RUNNING
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
        // Riêng với FloatingBubble(1042)/BootSetup(1043)/VMAutostart(1044) để cùng tồn tại.
        private const val NOTIFICATION_ID = 1045
        private const val CHANNEL_ID = "clusternav_voicekey_keepalive"

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
