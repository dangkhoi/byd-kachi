package com.byd.clusternav

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * Schedules a self-relaunch of Home shortly after an OTA self-update.
 *
 * WHY an alarm instead of post-install code or a MY_PACKAGE_REPLACED receiver:
 * a successful `pm install -r` of THIS app kills our process, so nothing after the install call
 * runs; and MY_PACKAGE_REPLACED fires only AFTER the replace, from a freshly-installed (stopped)
 * process with no foreground history — verified NOT to relaunch on Android 12+ (emulator). The alarm
 * is owned by the system and survives our death. It is scheduled while the app is still in the
 * FOREGROUND (the user just tapped "Tải & cài"), so on Android 10 (the DiLink head unit) the
 * "recently foreground" + SYSTEM_ALERT_WINDOW background-activity-start grace lets the alarm's
 * PendingIntent open [MainActivity]. Best-effort on newer Android where BAL is stricter — worst case
 * the user taps the app icon. Cancelled by [cancel] if the install did not actually happen.
 */
object UpdateRelaunch {
    private const val TAG = "UpdateRelaunch"
    private const val DELAY_MS = 5_000L
    private const val REQ = 0xC1A7

    private fun pending(ctx: Context): PendingIntent {
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(ctx.applicationContext, REQ, launch, flags)
    }

    /** Arm the relaunch ~[DELAY_MS] out. Call while the app is still foreground, BEFORE installing. */
    fun schedule(ctx: Context) {
        runCatching {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            // setAndAllowWhileIdle: fires in doze and needs NO SCHEDULE_EXACT_ALARM permission (API 31+).
            am.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + DELAY_MS,
                pending(ctx),
            )
            Log.i(TAG, "relaunch armed (+${DELAY_MS}ms)")
        }.onFailure { Log.e(TAG, "schedule failed", it) }
    }

    /** Cancel the armed relaunch (install failed / nothing was replaced). */
    fun cancel(ctx: Context) {
        runCatching {
            (ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(pending(ctx))
        }
    }
}
