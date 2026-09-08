package com.byd.clusternav

import android.content.Context
import com.byd.clusternav.core.CsvEscape
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * RAW notification capture (diagnostic) — persist EVERY notification posted by the five nav packages
 * (com.google.android.apps.maps, app.revanced.android.apps.maps, vn.vietmap.live, com.waze,
 * com.chisadin.wazemod), tagged by package and by whether it looked like nav (category / distance token).
 *
 * Unlike [NavNotifLog] — which only records the notifications that already PASSED the `isNav || hasDist`
 * gate in [NavNotificationListener.handle] — this writer is wired in BEFORE that gate, so a diagnostic
 * drive can EMPIRICALLY see what each app posts (nav or not): "Waze is running", VietMap "Ứng dụng đang
 * chạy", WazeMod status, etc. — the per-app notif-channel yield the parsed log necessarily hides.
 * Pull: adb pull /sdcard/Android/data/com.byd.clusternav2/files/nav_notif_raw_*.csv
 *
 * Purely diagnostic + additive: it NEVER touches SourceArbiter / the cluster feed / nav state.
 * Mirrors [NavNotifLog] exactly: (a) GATED behind [NavLog.verbose] (default OFF — toggled by the hidden
 * long-press on the version label) and (b) file open + write + flush run on its OWN single-thread daemon
 * Executor, never on the listener's main-looper callback thread. Every field is CSV-escaped ([CsvEscape])
 * AND newline-stripped ([oneLine]) so a raw notification's commas / quotes / embedded line breaks keep each
 * record on ONE physical line for line-based post-drive analysis. Lazy → the thread is not created until
 * verbose actually turns on. Degrade-safe: any failure is swallowed per row so logging can never affect nav.
 */
object NavNotifRawLog {
    private var w: BufferedWriter? = null
    @Volatile var path: String = ""; private set

    private val io: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "navnotifrawlog").apply { isDaemon = true } }
    }

    const val HEADER = "t_ms,pkg,category,isNav,hasDist,hasLargeIcon,title,text,subText,bigText"

    private fun ensureLocked(ctx: Context) {
        if (w != null) return
        runCatching {
            val f = File(ctx.applicationContext.getExternalFilesDir(null), "nav_notif_raw_${System.currentTimeMillis()}.csv")
            w = f.bufferedWriter().also { it.appendLine(HEADER) }
            path = f.absolutePath
        }
    }

    /**
     * Build one CSV row (PURE — no Android, no I/O) so the column shape is unit-tested off-car and can never
     * drift from [HEADER]. String fields are newline-stripped (CR/LF → space, see [oneLine]) BEFORE [CsvEscape]
     * wraps commas/quotes, so an embedded line break can never split a record. The field order + count here is
     * the single source of truth the [HEADER] must match (10 columns).
     */
    fun buildRow(
        tMs: Long,
        pkg: String,
        category: String,
        isNav: Boolean,
        hasDist: Boolean,
        hasLargeIcon: Boolean,
        title: String,
        text: String,
        subText: String,
        bigText: String,
    ): String = CsvEscape.row(
        listOf(
            tMs.toString(), oneLine(pkg), oneLine(category), isNav.toString(), hasDist.toString(),
            hasLargeIcon.toString(), oneLine(title), oneLine(text), oneLine(subText), oneLine(bigText),
        ),
    )

    /** Collapse CR / LF / CRLF to a single space so an embedded line break never splits a CSV record. */
    private fun oneLine(value: String): String =
        if (value.isEmpty()) value else value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')

    /**
     * Record one RAW notification. No-op unless [NavLog.verbose]; the timestamp is captured on the calling
     * (listener) thread, the rest runs off-main on this object's own daemon Executor.
     */
    fun record(
        ctx: Context,
        pkg: String,
        category: String,
        isNav: Boolean,
        hasDist: Boolean,
        hasLargeIcon: Boolean,
        title: String,
        text: String,
        subText: String,
        bigText: String,
    ) {
        if (!NavLog.verbose) return
        val t = System.currentTimeMillis()
        io.execute {
            recordLocked(ctx, t, pkg, category, isNav, hasDist, hasLargeIcon, title, text, subText, bigText)
        }
    }

    private fun recordLocked(
        ctx: Context,
        t: Long,
        pkg: String,
        category: String,
        isNav: Boolean,
        hasDist: Boolean,
        hasLargeIcon: Boolean,
        title: String,
        text: String,
        subText: String,
        bigText: String,
    ) {
        ensureLocked(ctx)
        val ww = w ?: return
        runCatching {
            ww.appendLine(
                buildRow(t, pkg, category, isNav, hasDist, hasLargeIcon, title, text, subText, bigText),
            )
            ww.flush()
        }
    }
}
