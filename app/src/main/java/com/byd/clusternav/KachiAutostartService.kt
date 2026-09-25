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
 * Foreground service for the Kachi LAUNCHER auto-start (B6) — the surface-INDEPENDENT boot orchestration
 * ([KachiAutostart.runBoot]: freeform seed + set-home + cast-coordination plan + ensure HOME up).
 *
 * WHY A FOREGROUND SERVICE: the setup runs several dadb round-trips (`settings`, `cmd package`, `am`) which can
 * take longer than a BroadcastReceiver's budget — the FGS keeps the process alive until they finish. It does NOT
 * mount slots (VirtualDisplays are surface-bound → they live in [com.byd.clusternav.launcher.KachiHomeActivity],
 * which restores + mounts the saved workspace when it renders).
 *
 * Modeled on [VietMapAutostartService]: [startForeground] FIRST (inside the ~5 s `startForegroundService` budget),
 * a SINGLE background worker (CAS latch + `latestStartId`) so overlapping starts (boot + OTA + a relaunch) don't
 * tear the FGS mid-run, then [finish] (stopForeground + stopSelf). The anti-loop guard (in-flight + cooldown)
 * lives in [KachiAutostart.runBoot], so a duplicate start is a harmless no-op.
 *
 * Never exported. Started from a foreground context ([RebindReceiver] on boot / OTA) so it is exempt from the
 * Android 12+ background-FGS-start restriction.
 */
class KachiAutostartService : Service() {

    // Exactly one worker at a time. Overlapping starts (BOOT_COMPLETED + MY_PACKAGE_REPLACED close together) must
    // not each spawn a worker + stopSelf(startId): a newer start's stopSelf could tear the FGS while the real
    // worker is still running dadb. So only the first start's worker runs (CAS); extra starts just bump
    // [latestStartId]; the worker releases the flag BEFORE finish() (same anti-FGS-leak reasoning as VietMap).
    private val workerActive = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var latestStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startForegroundOnce()) { stopSelf(startId); return START_NOT_STICKY }
        latestStartId = startId
        if (workerActive.compareAndSet(false, true)) {
            Thread({
                try {
                    runCatching { KachiAutostart.runBoot(applicationContext) }
                        .onFailure { Log.e(TAG, "Kachi autostart service failed", it) }
                } finally {
                    // Release BEFORE finish() — a start landing in the gap CAS-wins → owns its own lifecycle; its
                    // runBoot is a no-op (cooldown/in-flight still held), and the old worker's finish() becomes a
                    // harmless extra stopSelf. Turns a permanent FGS leak into a no-op (mirrors VietMap).
                    workerActive.set(false)
                    finish(latestStartId)
                }
            }, "kachi-autostart").start()
        } else {
            Log.i(TAG, "Kachi autostart already running — ignoring duplicate start (startId=$startId); runBoot has its own cooldown")
        }
        return START_NOT_STICKY
    }

    /** ALWAYS the last step: leave foreground + stop. */
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
                NotificationChannel(CHANNEL_ID, "Kachi khởi động", NotificationManager.IMPORTANCE_MIN),
            )
        }
        @Suppress("DEPRECATION")
        return android.app.Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Kachi")
            .setContentText(Lang.t("Đang chuẩn bị màn hình chính…", "Preparing home screen…"))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "KachiAutostartSvc"
        // B1 2026-09-25: 1045 TRÙNG VoiceKeyKeepAliveService ⇒ finish() ở đây (stopForeground REMOVE) gỡ thông báo
        // keep-alive đang thường trú. Bảng ID duy nhất: KDoc `VoiceKeyKeepAliveService.NOTIFICATION_ID`.
        private const val NOTIFICATION_ID = 1047
        private const val CHANNEL_ID = "clusternav_kachi_autostart"

        /** Boot / OTA: run the launcher's surface-independent boot setup. */
        fun startForBoot(ctx: Context) {
            val app = ctx.applicationContext
            val i = Intent(app, KachiAutostartService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i) else app.startService(i)
            }.onFailure { Log.w(TAG, "start KachiAutostartService failed: ${it.message}") }
        }
    }
}
