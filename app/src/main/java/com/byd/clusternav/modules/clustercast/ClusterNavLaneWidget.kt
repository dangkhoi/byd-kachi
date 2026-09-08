package com.byd.clusternav.modules.clustercast

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import java.util.concurrent.Executors

/**
 * Nav-track activator for the OEM "simple navigation" cluster widget (clusterDebug opcode 39).
 *
 * WHY (on-car proof 2026-08-12, ClusterNav 1.06 / DiLink3.0, parked): with the cluster on native
 * gauges, broadcasting AUTONAVI nav frames alone did NOT surface the nav overlay — issuing
 * `service call AutoContainer 2 i32 1000 i32 39 s16 ""` ("simple navigation") is what makes the OEM
 * nav overlay (turn arrow + distance + road + live ETA) appear in the CENTER of the cluster, fed by
 * the ongoing nav broadcast. Byte-for-byte the same command proven in
 * scripts/vehicle/nav-mode-probe.sh.
 *
 * TWO-TRACK boundary: op 39 is asserted ONLY in nav-only mode (Cast master switch OFF). When Cast
 * is ON the Cast track owns the cluster surface and we must not fight it. The gate reads the
 * persisted `castEnabled` flag (a user config, not live cast-control state), keeping the tracks
 * decoupled, and reuses the shared privileged shell gateway ([SimpleCastCoordinator.executeShell])
 * rather than opening a second transport.
 *
 * DELIVERY (2026-08-12 self-diagnose · docs/specs/nav-cluster-op39-selfdiagnose.html): the command
 * is delivered over the dadb loopback to localhost:5555. If that loopback is down (e.g. ADB-over-TCP
 * dropped after a power-cycle) the shell returns a non-success [ShellResult] rather than throwing.
 * A previous version marked the widget "asserted" BEFORE the shell ran, so a single failed attempt
 * consumed the 30s debounce and blocked all retries. This version only holds after a SUCCESS
 * ([shouldIssueNow]); a failure retries after a short backoff so a recovered loopback re-asserts on
 * the next nav frame. The last outcome is published via [status]/[detail] for the Nav card, and
 * logged once per state change (no notification-rate spam).
 *
 * All shell I/O runs on a dedicated single thread so notification-listener callbacks never block.
 */
object ClusterNavLaneWidget {
    private const val TAG = "ClusterNavWidget"

    /** Hold this long after a SUCCESSFUL assert before re-issuing (idempotent refresh cadence). */
    const val REASSERT_MS = 30_000L

    /** Minimum gap between attempts while op 39 has NOT yet succeeded — a retry backoff that lets a
     *  recovered loopback re-assert quickly without hammering dadb at notification rate. */
    const val RETRY_AFTER_FAIL_MS = 4_000L

    /** clusterDebug opcode: 39 = "simple navigation" (raise the OEM nav overlay on the cluster). */
    const val OP_SIMPLE_NAV = 39

    /** Exact proven command (matches nav-mode-probe.sh). Kept in one place so tests/D4 agree. */
    private const val CMD = "service call AutoContainer 2 i32 1000 i32 $OP_SIMPLE_NAV s16 \"\""

    /** Runtime outcome, published for the Nav card status line (D3). */
    enum class Op39Status { IDLE, ASSERTED, GATED_CAST, SHELL_UNREACHABLE }

    /** Pure gate decision (unit-tested). */
    enum class Decision { ASSERT, GATED_CAST, SKIP_IDLE }

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "cluster-nav-widget") }

    @Volatile private var lastOkAtMs = 0L
    @Volatile private var lastAttemptAtMs = 0L

    /** Last decided/observed op-39 outcome. Written only on the [io] thread; read from any thread. */
    @Volatile
    var status: Op39Status = Op39Status.IDLE
        private set

    /** Human/diagnostic detail for [status] (e.g. "exit=0" or "exit=-1 <err>"). */
    @Volatile
    var detail: String = ""
        private set

    /**
     * Pure gate: assert op 39 whenever navigation is active AND Cast is OFF (nav-only mode). op 39
     * "simple navigation" (Giữa + ETA) is the ONLY nav-on-cluster mode (owner 2026-08-12); the old
     * "small/top" variant and its display-mode gate are gone. Cast ON still wins — the Cast track owns
     * the cluster surface and the widget must not fight it.
     */
    fun decide(navActive: Boolean, castEnabled: Boolean): Decision = when {
        !navActive -> Decision.SKIP_IDLE
        castEnabled -> Decision.GATED_CAST
        else -> Decision.ASSERT
    }

    /** Back-compat predicate (existing callers/tests): true only when [decide] == [Decision.ASSERT]. */
    fun shouldAssert(navActive: Boolean, castEnabled: Boolean): Boolean =
        decide(navActive, castEnabled) == Decision.ASSERT

    /**
     * Pure timing gate (unit-tested): should we (re)issue op 39 at [nowMs]?
     * - Hold for [REASSERT_MS] after the last SUCCESS ([lastOkAtMs]) — idempotent refresh cadence.
     * - Otherwise retry, but never more often than [RETRY_AFTER_FAIL_MS] ([lastAttemptAtMs]) so a
     *   down loopback is not hammered at notification rate.
     * A fresh state (both timestamps 0) issues immediately.
     */
    fun shouldIssueNow(nowMs: Long, lastOkAtMs: Long, lastAttemptAtMs: Long): Boolean {
        if (lastOkAtMs != 0L && nowMs - lastOkAtMs < REASSERT_MS) return false
        if (lastAttemptAtMs != 0L && nowMs - lastAttemptAtMs < RETRY_AFTER_FAIL_MS) return false
        return true
    }

    /**
     * Nav became / stayed active. Offloads to [io]; re-evaluates the gate every call (so turning Cast
     * off mid-session lets op 39 fire) but only runs the shell when [shouldIssueNow] allows. Safe to
     * call at notification rate and from any thread.
     */
    fun onNavActive(context: Context) {
        val appCtx = context.applicationContext
        io.execute {
            val castEnabled = runCatching {
                SimpleCastRuntime.coordinator(appCtx).prefs.castEnabled()
            }.getOrDefault(false)

            when (decide(navActive = true, castEnabled = castEnabled)) {
                Decision.GATED_CAST -> return@execute setStatus(Op39Status.GATED_CAST, "Cast đang bật")
                Decision.SKIP_IDLE -> return@execute // unreachable here (navActive hard-coded true)
                Decision.ASSERT -> Unit
            }

            val now = SystemClock.elapsedRealtime()
            if (!shouldIssueNow(now, lastOkAtMs, lastAttemptAtMs)) return@execute
            issue(appCtx, now)
        }
    }

    /**
     * Nav stopped. Resets timers + status so the next session re-asserts promptly. Marshalled onto
     * [io] (like [onNavActive]) so ALL writes to [lastOkAtMs]/[lastAttemptAtMs]/[status] stay confined
     * to the single widget thread. This also preserves FIFO ordering: a still-queued [onNavActive]
     * attempt cannot land AFTER this reset and resurrect a stale ASSERTED/SHELL_UNREACHABLE status.
     * Safe to call at notification rate and from any thread.
     */
    fun onNavIdle() {
        io.execute {
            lastOkAtMs = 0L
            lastAttemptAtMs = 0L
            setStatus(Op39Status.IDLE, "")
        }
    }

    /**
     * Run the op-39 shell once (MUST be called on [io]). Records the attempt time always and the
     * success time only on success, then publishes the resulting status. Returns the outcome.
     */
    private fun issue(appCtx: Context, now: Long): Op39Status {
        lastAttemptAtMs = now
        val result = runCatching { SimpleCastRuntime.coordinator(appCtx).executeShell(CMD) }.getOrNull()
        return if (result != null && result.success) {
            lastOkAtMs = now
            setStatus(Op39Status.ASSERTED, "exit=0")
            Op39Status.ASSERTED
        } else {
            val why = result?.let { "exit=${it.exitCode} ${it.stderr}".trim() } ?: "shell không phản hồi"
            setStatus(Op39Status.SHELL_UNREACHABLE, why)
            Op39Status.SHELL_UNREACHABLE
        }
    }

    /** Publish runtime status; log ONLY on change so notification-rate calls don't spam the bus. */
    private fun setStatus(next: Op39Status, why: String) {
        val changed = status != next || detail != why
        status = next
        detail = why
        if (!changed) return
        when (next) {
            Op39Status.ASSERTED -> Log.i(TAG, "op39 asserted ok ($why)")
            Op39Status.GATED_CAST -> Log.i(TAG, "op39 skip: Cast đang bật (nav nhường cụm)")
            Op39Status.SHELL_UNREACHABLE -> Log.w(TAG, "op39 shell KHÔNG tới cụm: $why")
            Op39Status.IDLE -> Unit
        }
    }
}
