package com.byd.clusternav.launcher

import java.util.concurrent.atomic.AtomicBoolean

/**
 * REUSABLE single-in-flight + cooldown guard (pure JVM, :core, unit-tested).
 *
 * ── Why (B6) ────────────────────────────────────────────────────────────────────────────────────────────────
 * Generalizes the PROVEN on-car guard that [com.byd.clusternav.VietMapAutostart] uses (in-flight `AtomicBoolean`
 * CAS + a cooldown timestamp) so the launcher auto-start ([com.byd.clusternav.KachiAutostart]) reuses the SAME
 * algorithm WITHOUT duplicating the logic and WITHOUT touching the proven VietMap file. A burst of boot triggers
 * (BOOT_COMPLETED + MY_PACKAGE_REPLACED + a HOME relaunch in quick succession) then runs the setup at most once.
 *
 * The guard/decision is Android-free, so per LayeringRules it lives in :core (not :app). The Android side
 * ([com.byd.clusternav.KachiAutostart]) holds ONE instance and wraps [tryBegin]/[finish] in a `try/finally`.
 *
 * Idempotency here is w.r.t a SINGLE process (RAM latch). Durable, cross-process idempotency for the freeform
 * seed is a separate concern owned by [com.byd.clusternav.system.FreeformSeedPolicy]'s 3-state marker.
 *
 * @param cooldownMs minimum spacing between two runs; a claim inside the window (after the previous finished) is refused.
 */
class AutostartGate(private val cooldownMs: Long) {

    private val inFlight = AtomicBoolean(false)

    @Volatile
    private var lastRunAtMs = 0L

    /**
     * Claim ONE run: returns true iff allowed to proceed (marks in-flight + stamps the clock). Returns false if a
     * run is already in-flight OR still inside [cooldownMs]. On success the caller MUST call [finish] (try/finally).
     */
    fun tryBegin(nowMs: Long): Boolean {
        if (!inFlight.compareAndSet(false, true)) return false          // a run is already in-flight
        if (!outsideCooldown(nowMs, lastRunAtMs, cooldownMs)) {          // still inside the cooldown window
            inFlight.set(false)
            return false
        }
        lastRunAtMs = nowMs
        return true
    }

    /** Release the claimed run (call in `finally`). */
    fun finish() {
        inFlight.set(false)
    }

    /** Test-only: clear the latch + clock between tests (state is instance-global). */
    fun reset() {
        inFlight.set(false)
        lastRunAtMs = 0L
    }

    companion object {
        /**
         * PURE: has [nowMs] moved at/after the cooldown boundary from [lastRunAtMs] (0 = never run yet, always true)?
         * Extracted so the boundary math is unit-testable independently of the CAS latch.
         */
        fun outsideCooldown(nowMs: Long, lastRunAtMs: Long, cooldownMs: Long): Boolean =
            lastRunAtMs == 0L || nowMs - lastRunAtMs >= cooldownMs
    }
}
