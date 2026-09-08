package com.byd.clusternav

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.core.StorageCapPlanner
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Defensive, ALWAYS-ON storage cap for the app-external diagnostics dir (`getExternalFilesDir(null)`) — where
 * every verbose writer lands its output: NavNotifLog / NavNotifRawLog (`*.csv`, the GMaps notification capture)
 * and the ClusterCast TEE (`castlog`).
 *
 * A data-collection drive with per-frame PNGs + screenshots previously filled the car's storage (7 GB+). This
 * prunes the dir down to [CAP_BYTES] (~150 MB) by deleting the OLDEST files first, using the pure, unit-tested
 * [StorageCapPlanner]. It runs even while verbose is ON (so a single long drive can't blow the budget) AND is
 * useful when verbose is OFF (it trims whatever a previous session left behind at the next session start).
 *
 * Safety envelope:
 *  • all enumeration + deletion runs on a single-thread daemon [io] Executor — NEVER the main / nav /
 *    notification thread;
 *  • every step is wrapped in `runCatching` so a filesystem error can never throw into navigation;
 *  • throttled to at most once per [MIN_INTERVAL_MS] unless [force], so the ~4 Hz nav frame path can call it
 *    opportunistically for the price of a volatile read.
 *
 * The OTA update APK lives in INTERNAL `filesDir/update` (see [UpdateChecker]), NOT under
 * `getExternalFilesDir`, so it is never a deletion candidate.
 */
object DiagStorageCap {
    private const val TAG = "DiagStorageCap"

    /** Cap (~150 MB) — the pure planner owns the number so the app and its unit test agree. */
    val CAP_BYTES: Long = StorageCapPlanner.DEFAULT_CAP_BYTES

    /** Minimum gap between two throttled enforcements so opportunistic callers can't hammer the disk. */
    private const val MIN_INTERVAL_MS = 60_000L

    @Volatile private var lastEnforceMs = 0L

    private val io: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "diagstoragecap").apply { isDaemon = true } }
    }

    /**
     * Prune the app-external files dir down to [CAP_BYTES], OLDEST first, off-thread. No-op if throttled (last
     * run < [MIN_INTERVAL_MS] ago) unless [force]. Degrade-safe: never throws into the caller.
     *
     * @param force run even if within the throttle window — used at session start / when verbose is just enabled.
     */
    fun enforce(ctx: Context, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && lastEnforceMs != 0L && now - lastEnforceMs < MIN_INTERVAL_MS) return
        lastEnforceMs = now
        val app = ctx.applicationContext
        runCatching { io.execute { enforceLocked(app) } }
    }

    private fun enforceLocked(app: Context) {
        runCatching {
            val base = app.getExternalFilesDir(null) ?: return
            val files = ArrayList<File>()
            collectFiles(base, files)
            if (files.isEmpty()) return
            val entries = files.map { StorageCapPlanner.Entry(it.absolutePath, it.length(), it.lastModified()) }
            val toDelete = StorageCapPlanner.selectForDeletion(entries, CAP_BYTES)
            if (toDelete.isEmpty()) return
            var freed = 0L
            var deleted = 0
            for (path in toDelete) {
                val f = File(path)
                val len = f.length()
                if (runCatching { f.delete() }.getOrDefault(false)) {
                    freed += len
                    deleted++
                }
            }
            runCatching { pruneEmptyDirs(base) }
            Log.i(TAG, "pruned $deleted file(s) ~${freed / (1024L * 1024L)} MB → cap ${CAP_BYTES / (1024L * 1024L)} MB")
        }.onFailure { Log.w(TAG, "storage cap enforce failed", it) }
    }

    /** Recursively collect regular files (not directories) under [dir]. */
    private fun collectFiles(dir: File, out: MutableList<File>) {
        val children = dir.listFiles() ?: return
        for (c in children) {
            if (c.isDirectory) collectFiles(c, out) else out.add(c)
        }
    }

    /** Depth-first removal of directories left empty after pruning (so stale empty subdirs go too). */
    private fun pruneEmptyDirs(dir: File) {
        val children = dir.listFiles() ?: return
        for (c in children) {
            if (c.isDirectory) {
                pruneEmptyDirs(c)
                runCatching { if (c.listFiles()?.isEmpty() == true) c.delete() }
            }
        }
    }
}
