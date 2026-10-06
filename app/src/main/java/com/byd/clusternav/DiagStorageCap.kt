package com.byd.clusternav

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.core.DiagFiles
import com.byd.clusternav.core.StorageCapPlanner
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Defensive, ALWAYS-ON storage cap for the app's DIAGNOSTIC files under `getExternalFilesDir(null)` — the
 * [DiagFiles] allow-list ONLY: `kachi-logs/`, `diag/`, `test/`, legacy `castlog/`, and the root
 * `nav_notif_log_*.csv` / `nav_notif_raw_*.csv` / `kachi-voice-*.zip` / `kachi-voice-test.wav`.
 *
 * A data-collection drive with per-frame PNGs + screenshots previously filled the car's storage (7 GB+). This
 * prunes the diagnostic set down to [CAP_BYTES] (~150 MB) by deleting the OLDEST files first, using the pure,
 * unit-tested [StorageCapPlanner]. It runs even while verbose is ON (so a single long drive can't blow the budget)
 * AND is useful when verbose is OFF (it trims whatever a previous session left behind at the next session start).
 *
 * ⚠ 2.92 (DIAG-CAP-USERDATA, P1): up to 2.91 this walked the WHOLE external tree, which also holds USER data —
 * slideshow `photos/`, `wallpapers/` (+ its `.kachi-art/` cache), `car/` images, `profiles/` exports and the
 * side-loaded voice packs in `sherpa/import/` — and those, being the oldest files, were the first to go. It now
 * walks ONLY the allow-listed paths, and the planner re-checks every id against [DiagFiles] (defence in depth), so
 * any path not on the list — including a folder added later — is never even listed, let alone deleted. Empty-dir
 * cleanup is confined to the allow-listed dirs too (the old sweep also removed the empty `photos/` / `wallpapers/`
 * / `car/` folders the app creates on purpose so the user can see where to drop images).
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
     * Prune the DIAGNOSTIC files ([DiagFiles] allow-list — never user data) down to [CAP_BYTES], OLDEST first,
     * off-thread. No-op if throttled (last run < [MIN_INTERVAL_MS] ago) unless [force]. Degrade-safe: never throws
     * into the caller.
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
            val files = diagnosticFiles(base)
            if (files.isEmpty()) return
            val byId = files.associateBy { it.relativeTo(base).invariantSeparatorsPath }
            val entries = byId.map { (id, f) -> StorageCapPlanner.Entry(id, f.length(), f.lastModified()) }
            val toDelete = StorageCapPlanner.selectDiagForDeletion(entries, CAP_BYTES)
            if (toDelete.isEmpty()) return
            var freed = 0L
            var deleted = 0
            for (id in toDelete) {
                val f = byId[id] ?: continue
                val len = f.length()
                if (runCatching { f.delete() }.getOrDefault(false)) {
                    freed += len
                    deleted++
                }
            }
            DiagFiles.DIRS.forEach { d -> runCatching { pruneEmptyDirs(File(base, d)) } }
            Log.i(TAG, "pruned $deleted file(s) ~${freed / (1024L * 1024L)} MB → cap ${CAP_BYTES / (1024L * 1024L)} MB")
        }.onFailure { Log.w(TAG, "storage cap enforce failed", it) }
    }

    /**
     * The ONLY files this cap may ever see: everything under the [DiagFiles.DIRS] folders plus the root files
     * [DiagFiles.isDiagnostic] recognises. User folders are never listed (not even stat-ed — the root filter checks the
     * NAME before `isFile`; senior review 2.92 Pass 3), so a huge side-loaded voice pack costs nothing here either.
     */
    private fun diagnosticFiles(base: File): List<File> {
        val out = ArrayList<File>()
        DiagFiles.DIRS.forEach { d ->
            File(base, d).takeIf { it.isDirectory && !isLink(it) }?.let { collectFiles(it, out) }
        }
        base.listFiles()?.filterTo(out) { DiagFiles.isDiagnostic(it.name) && it.isFile }
        return out
    }

    /** Recursively collect regular files (not directories) under [dir]; never follows a symlinked directory. */
    private fun collectFiles(dir: File, out: MutableList<File>) {
        val children = dir.listFiles() ?: return
        for (c in children) {
            if (c.isDirectory) { if (!isLink(c)) collectFiles(c, out) } else if (c.isFile) out.add(c)
        }
    }

    /** A link inside an allow-listed dir must not lead the sweep into user folders (or anywhere else). */
    private fun isLink(f: File): Boolean = runCatching { Files.isSymbolicLink(f.toPath()) }.getOrDefault(true)

    /** Depth-first removal of SUBdirectories left empty after pruning — inside an allow-listed [dir] only. */
    private fun pruneEmptyDirs(dir: File) {
        if (isLink(dir)) return   // an allow-listed name that is a link must not lead the cleanup elsewhere either
        val children = dir.listFiles() ?: return
        for (c in children) {
            if (c.isDirectory && !isLink(c)) {
                pruneEmptyDirs(c)
                runCatching { if (c.listFiles()?.isEmpty() == true) c.delete() }
            }
        }
    }
}
