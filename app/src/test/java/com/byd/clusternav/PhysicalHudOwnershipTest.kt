package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhysicalHudOwnershipTest {
    @Test
    fun `stop arrival output off never undo durable physical HUD ON`() {
        val controller = HudMirrorController { 123L }
        controller.recordPhysicalHudConfirmedOn()
        controller.requestEnabled(true)
        controller.onStop()
        controller.onArrival()
        controller.onOutputDisabled()

        val snapshot = controller.snapshot()
        assertEquals(PhysicalHudOwnership.DURABLE_USER_ON, snapshot.physicalOwnership)
        assertEquals(0, snapshot.physicalWriteCount)
        assertFalse(snapshot.requested)
    }

    @Test
    fun `cluster center-nav HAL write is single-owner and wired via NavRepository two-track`() {
        val controller = SourceRoots.text("src/main/java/com/byd/clusternav/HudMirrorController.kt")
        val broadcaster = SourceRoots.text("src/main/java/com/byd/clusternav/ClusterBroadcaster.kt")
        val hal = SourceRoots.text("src/main/java/com/byd/clusternav/modules/hal/BydHal.kt")
        val navRepo = SourceRoots.text("src/main/java/com/byd/clusternav/NavRepository.kt")

        // Broadcast/lane feeder and HUD-mirror controller must NOT touch the HAL directly.
        listOf(controller, broadcaster).forEach { text ->
            assertFalse(text.contains("BydHal"))
            assertFalse(text.contains("writeNavFrame"))
            assertFalse(text.contains("writeSpeedLimit"))
            assertFalse(text.contains("clearSpeedLimit"))
        }
        // Cluster-lane broadcast path stays decoupled from the HAL owner (owner is driven from NavRepository).
        assertFalse(broadcaster.contains("NavigationHudOwner"))
        assertFalse(hal.contains("writeSpeedLimit"))
        assertFalse(hal.contains("clearSpeedLimit"))
        assertFalse(hal.contains("ADAS_TRAFFIC_LIMIT_SPEED_STATUS_PROMPT"))

        // Ownership boundary preserved: ONLY NavigationHudOwner may call BydHal.writeNavFrame.
        val callers = Files.walk(SourceRoots.path("src/main/java/com/byd/clusternav")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .filter { it.fileName.toString() != "NavigationHudOwner.kt" }
                .filter { it.fileName.toString() != "BydHal.kt" }
                .filter { it.toFile().readText().contains("BydHal.writeNavFrame") }
                .toList()
        }
        assertTrue(callers.isEmpty(), "only NavigationHudOwner may call BydHal.writeNavFrame: $callers")

        // 2026-08-13: the center-nav path IS now wired (was orphaned/fail-closed). NavRepository instantiates
        // the owner and gates it two-track (Cast master OFF) so the app drives "Giữa + ETA" via the proven HAL
        // path instead of the no-op ch1000 op39.
        assertTrue(navRepo.contains("NavigationHudOwner"), "NavRepository must wire the cluster center-nav owner")
        assertTrue(
            navRepo.contains("navOnlyMode") && navRepo.contains("castEnabled"),
            "cluster center-nav must be gated by the Cast-OFF two-track check",
        )
    }
}
