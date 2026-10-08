package com.byd.clusternav

import android.content.Context
import com.byd.clusternav.core.CsvEscape
import com.byd.clusternav.core.FileByteBudget
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * T2 telemetry — persist the RAW GMaps/VietMap navigation notification (title/text/subText/bigText + large-icon
 * presence) ALONGSIDE what we parsed from it (maneuver icon / distance / road / eta) to a pullable CSV, so a
 * long drive's per-turn data can be pulled and used to improve arrow/road/distance accuracy.
 * Pull: adb pull /sdcard/Android/data/com.byd.clusternav2/files/nav_notif_log_*.csv
 *
 * Mirrors [NavDistanceLog] in structure: (a) GATED behind [NavLog.verbose] (default OFF — toggled by the hidden
 * long-press on the version label) and (b) file open + write + flush run on a single-thread daemon Executor,
 * never on the main thread ([NavNotificationListener.handle] runs on the main looper). Every field is
 * CSV-escaped ([CsvEscape]) because a raw notification legitimately contains commas / quotes / newlines. Lazy →
 * the thread is not created until verbose actually turns on. Degrade-safe: any failure is swallowed per row so
 * logging can never affect navigation.
 */
object NavNotifLog {
    private var w: BufferedWriter? = null
    @Volatile var path: String = ""; private set

    private val io: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "navnotiflog").apply { isDaemon = true } }
    }

    const val HEADER =
        "t_ms,pkg,title,text,subText,bigText,hasLargeIcon,parsedManeuverIcon,parsedDistance,parsedRoad,parsedEta"

    /** 2.98 · R6-F — trần TỪNG tệp 8 MiB (cùng `usage-*.log`), quá thì xoay sang tệp mới; trần tổng vẫn là `DiagStorageCap`. */
    private val budget = FileByteBudget()

    /**
     * Mở tệp nếu chưa có, hoặc XOAY sang tệp mới khi dòng sắp ghi ([rowBytes]) làm tệp hiện tại vượt trần ([FileByteBudget]).
     * Chỉ chạy trên [io] (luồng đơn) ⇒ không cần khoá.
     */
    private fun ensureLocked(ctx: Context, rowBytes: Long) {
        if (w != null && !budget.mustRotateBefore(rowBytes)) return
        w?.let { old -> runCatching { old.close() } }
        w = null
        runCatching {
            val f = File(ctx.applicationContext.getExternalFilesDir(null), "nav_notif_log_${System.currentTimeMillis()}.csv")
            w = f.bufferedWriter().also { it.appendLine(HEADER) }
            budget.startFile(HEADER.toByteArray(Charsets.UTF_8).size + 1L)
            path = f.absolutePath
        }
    }

    /**
     * Record one accepted navigation notification and its parsed [NavState] fields. No-op unless
     * [NavLog.verbose]; the timestamp is captured on the calling thread, the rest runs off-main.
     */
    fun record(
        ctx: Context,
        pkg: String,
        title: String,
        text: String,
        subText: String,
        bigText: String,
        hasLargeIcon: Boolean,
        parsedManeuverIcon: Int,
        parsedDistance: String,
        parsedRoad: String,
        parsedEta: String,
    ) {
        if (!NavLog.verbose) return
        val t = System.currentTimeMillis()
        io.execute {
            recordLocked(
                ctx, t, pkg, title, text, subText, bigText, hasLargeIcon,
                parsedManeuverIcon, parsedDistance, parsedRoad, parsedEta,
            )
        }
    }

    private fun recordLocked(
        ctx: Context,
        t: Long,
        pkg: String,
        title: String,
        text: String,
        subText: String,
        bigText: String,
        hasLargeIcon: Boolean,
        parsedManeuverIcon: Int,
        parsedDistance: String,
        parsedRoad: String,
        parsedEta: String,
    ) {
        val row = CsvEscape.row(
            listOf(
                t.toString(), pkg, title, text, subText, bigText, hasLargeIcon.toString(),
                parsedManeuverIcon.toString(), parsedDistance, parsedRoad, parsedEta,
            ),
        )
        val n = row.toByteArray(Charsets.UTF_8).size + 1L
        ensureLocked(ctx, n)
        val ww = w ?: return
        runCatching {
            ww.appendLine(row)
            ww.flush()
            budget.record(n)
        }
    }
}
