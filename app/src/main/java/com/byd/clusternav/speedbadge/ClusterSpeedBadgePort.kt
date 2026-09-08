package com.byd.clusternav.speedbadge

import android.util.Log
import com.byd.clusternav.contracts.SpeedLimitFrame
import com.byd.clusternav.navigation.SpeedSignOutput
import com.byd.clusternav.navigation.SpeedSignPort
import com.byd.clusternav.navigation.SpeedSignSubmission

/**
 * SpeedSignPort for CLUSTER output: renders the speed-limit badge as a
 * TYPE_APPLICATION_OVERLAY on display 1 (the instrument cluster).
 *
 * The [SpeedBadgeOverlay] is injected (owned by [com.byd.clusternav.NavigationSpeedSignOwner]) so the real
 * speed-sign pipeline and the debug force-show share exactly ONE overlay window on display 1 — one badge, not
 * two (BUG-1, 2026-08-17). If display 1 is unavailable (off-car), the overlay degrades to no-op silently.
 * Generation fencing matches NoopSpeedSignPort semantics exactly.
 */
class ClusterSpeedBadgePort(private val overlay: SpeedBadgeOverlay) : SpeedSignPort {

    companion object {
        private const val TAG = "ClusterSpeedBadge"
    }

    override val output: SpeedSignOutput = SpeedSignOutput.CLUSTER

    private val lock = Any()
    private var acceptedGeneration = 0L

    override fun publish(frame: SpeedLimitFrame, generation: Long): SpeedSignSubmission = synchronized(lock) {
        require(frame.value != null) { "publish requires an active frame" }
        if (generation < acceptedGeneration) return SpeedSignSubmission.STALE_DROPPED
        acceptedGeneration = generation
        val v = frame.value ?: return SpeedSignSubmission.STALE_DROPPED
        overlay.show(v, frame.signType)
        Log.d(TAG, "show $v km/h gen=$generation")
        SpeedSignSubmission.ACCEPTED
    }

    override fun replaceWithClear(frame: SpeedLimitFrame, generation: Long): SpeedSignSubmission = synchronized(lock) {
        require(frame.value == null) { "replaceWithClear requires a clear frame" }
        if (generation < acceptedGeneration) return SpeedSignSubmission.STALE_DROPPED
        acceptedGeneration = generation
        overlay.hide()
        Log.d(TAG, "hide gen=$generation reason=${frame.clearReason}")
        SpeedSignSubmission.ACCEPTED
    }

    override fun close() {
        overlay.close()
    }

    /** Test-only: current accepted generation for assertions. */
    internal fun acceptedGenerationForTest(): Long = synchronized(lock) { acceptedGeneration }
}
