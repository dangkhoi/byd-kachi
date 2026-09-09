package com.byd.clusternav.system

import android.content.Context

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
            return FreeformSeedPolicy(FreeformSeedStore(app), WindowCommandDispatcher.get(app).launcherSeam(), log)
        }
    }
}
