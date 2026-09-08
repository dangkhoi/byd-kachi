package com.byd.clusternav

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * R7 (#2) WIRING contract. The pure decisions live in `NavArrivalGuard` (:core, unit-tested by
 * `NavArrivalGuardTest`); [NavNotificationListener] extends an Android `NotificationListenerService`
 * so it needs a device/Robolectric to run. Like `NavNotificationListenerTest` /
 * `BubbleGestureContractTest`, this locks the wiring by reading the source: arrival must emit a
 * CLEAR/STOP (not plant a lingering icon-15 frame), and the distance-regression guard must gate
 * ingest. On-car confirmation (Google Maps arrival clears the cluster) is left to §Verification.
 */
class NavArrivalClearContractTest {

    private val listener: String by lazy {
        val current = Path.of(System.getProperty("user.dir"))
        val base = if (Files.exists(current.resolve("src"))) current else current.resolve("app")
        base.resolve("src/main/java/com/byd/clusternav/NavNotificationListener.kt").toFile().readText()
    }

    @Test
    fun `arrival is detected through the shared pure guard`() {
        assertTrue(
            listener.contains("NavArrivalGuard.isArrivalText(title, text, big)"),
            "arrival must be detected via the shared :core guard",
        )
    }

    @Test
    fun `arrival emits STOP-clear instead of planting a lingering destination frame`() {
        assertTrue(
            listener.contains("NavRepository.stop(applicationContext)"),
            "arrival/route-end must clear the cluster via NavRepository.stop",
        )
        // The old behaviour planted an icon-15 'Đã đến' frame that heart-beat for STALE_MS and got stuck.
        assertFalse(
            listener.contains("maneuverIcon = 15"),
            "arrival must NOT re-ingest a lingering destination frame (that was the stuck-frame bug)",
        )
    }

    @Test
    fun `route-remaining collapse also clears the cluster`() {
        assertTrue(
            listener.contains("arrivalGuard.arrivedByRouteRemaining("),
            "route-remaining ~0 must be treated as arrival and clear the cluster",
        )
    }

    @Test
    fun `distance-regression guard gates ingest`() {
        assertTrue(
            listener.contains("arrivalGuard.acceptDistance("),
            "a spurious distance jump-up must be dropped before ingest",
        )
        // The guard must sit BEFORE the ingest call so a rejected frame never reaches the cluster.
        val guardIdx = listener.indexOf("arrivalGuard.acceptDistance(")
        val ingestIdx = listener.indexOf("NavRepository.ingest(applicationContext, sbn.packageName, null, state)")
        assertTrue(guardIdx in 1 until ingestIdx, "distance guard must precede ingest (guard=$guardIdx ingest=$ingestIdx)")
    }

    @Test
    fun `notification removal resets the guard for the next route`() {
        assertTrue(
            listener.contains("arrivalGuard.reset()"),
            "the per-session guard must reset on stop/arrival/removal",
        )
    }
}
