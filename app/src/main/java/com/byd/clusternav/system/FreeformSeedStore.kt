package com.byd.clusternav.system

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

/**
 * :app executor side of [FreeformSeedPolicy] — the durable [FreeformSeedPolicy.MarkerStore] backed by
 * SharedPreferences, plus factories that wire the marker + the single [ShellTransport] owner into a policy.
 *
 * ── ONE SHARED MARKER ─────────────────────────────────────────────────────────────────────────────────────────
 * Uses the SAME SharedPreferences file/key cast has always used (`clusternav_state` / `freeform_state`), so the
 * cluster-cast path and the launcher path read/write ONE coordinated marker: if the user removes freeform from the
 * Cast screen, the launcher's [FreeformSeedPolicy.ensureSeed] sees [FreeformSeedPolicy.SeedState.USER_REMOVED] and
 * will not silently re-seed (and vice-versa). This is the "single coordinated owner" the brick-vector fix requires.
 *
 * [commit] uses `commit()` (synchronous), never `apply()` — commit-before-mutate needs the record flushed before
 * the Settings.Global write, so a process death mid-write cannot lose it.
 */
class FreeformSeedStore(context: Context) : FreeformSeedPolicy.MarkerStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    override fun read(): FreeformSeedPolicy.SeedState =
        FreeformSeedPolicy.SeedState.of(prefs.getInt(KEY, FreeformSeedPolicy.SeedState.NONE.code))

    // lint ApplySharedPref: `commit()` đồng bộ là CỐ Ý — marker phải nằm trên đĩa TRƯỚC khi ghi Settings.Global
    // (CLAUDE.md §5: state đổi ngoài hệ thống sống dai hơn tiến trình; chết giữa chừng không được mất dấu).
    @SuppressLint("ApplySharedPref")
    override fun commit(state: FreeformSeedPolicy.SeedState) {
        prefs.edit().putInt(KEY, state.code).commit()
    }

    companion object {
        /** Existing cast marker file — reused so cast + launcher share ONE marker (do not rename: on-disk state). */
        const val PREF = "clusternav_state"

        /** Existing cast marker key. */
        const val KEY = "freeform_state"

        /**
         * The sanctioned LAUNCHER freeform/geometry writer. Routes shell through the launcher ownership seam
         * ([WindowCommandDispatcher.launcherSeam]) → single [ShellTransport] owner, and any launcher geometry write
         * that targets the cluster (display ≥1) is rejected at the ownership gate. The freeform SEED itself carries
         * no `--display`, so it is always ALLOWed. B6 (auto-start / reflow) calls [FreeformSeedPolicy.ensureSeed]
         * through this — the launcher has NO other way to write these (enforced by PersistentWindowStateWriterGuardTest).
         */
        fun forLauncher(context: Context, log: (String) -> Unit = {}): FreeformSeedPolicy {
            val app = context.applicationContext
            // 2.96 · R13 (soát Pass 1): cờ đọc TRONG tiến trình (0 shell) — [readGlobal]; ghi vẫn qua seam launcher.
            return FreeformSeedPolicy(
                FreeformSeedStore(app), WindowCommandDispatcher.get(app).launcherSeam(), readFlag = readGlobal(app), log = log,
            )
        }

        /**
         * 2.96 · R13 (soát Pass 1 [P2]) — MỘT bộ đọc `Settings.Global` theo khoá cho cả hai đường ghi cờ freeform
         * ([forLauncher] và `SimpleCastRuntime` → `CastGeometryController`): `Settings.Global.getString` là một lượt
         * ContentProvider trong tiến trình (đọc global không cần quyền), 0 lệnh shell. `null` = khoá chưa có / đọc hỏng ⇒
         * bên gọi ghi như trước R13 (fail-safe). Đây là READ — không phải writer (PersistentWindowStateWriterGuardTest).
         */
        fun readGlobal(context: Context): (String) -> String? {
            val app = context.applicationContext
            return { key -> runCatching { Settings.Global.getString(app.contentResolver, key) }.getOrNull() }
        }
    }
}
