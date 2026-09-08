package com.byd.clusternav.modules.clustercast

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Deep-rescue WIRING contract (owner 2026-08-11 — DashCast conflict clean). The action is pure Android
 * glue (AlertDialog, shell I/O, stopService) so — like [BubbleGestureContractTest] — it is locked by
 * reading the source. On-car proof (recovering an actual DashCast jam) is noted for the owner; the
 * conflict is NOT reproduced off-car (reflash risk).
 */
class CastDeepRescueContractTest {

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }

    private fun source(relative: String): String =
        app("src/main/java/com/byd/clusternav/modules/clustercast/$relative").toFile().readText()

    private val action by lazy { source("CastDeepRescueAction.kt") }
    private val controller by lazy { source("MainActivityCastController.kt") }

    @Test
    fun `force-stops DashCast and the xdja cluster helper`() {
        assertTrue(action.contains("am force-stop"), "force-stops the conflicting app")
        assertTrue(action.contains("com.byd.dashcast"), "targets DashCast")
        assertTrue(action.contains("com.xdja.clusterdemo"), "targets the xdja cluster helper")
    }

    @Test
    fun `stands our own cast fully down and does NOT reopen the projection`() {
        assertTrue(action.contains("SimpleCastIntent.Stop()"), "stops our own cast")
        assertTrue(action.contains("closeProjection()"), "closes our projection")
        assertTrue(action.contains("stopOwnServices()"), "stops our own bubble service")
        assertTrue(!action.contains("openProjection"), "does NOT reopen (leave the cluster on native gauges)")
    }

    @Test
    fun `resets the cluster virtual display to defaults`() {
        assertTrue(action.contains("DisplayParse.clusterDisplayId"), "targets the cluster VD id")
        assertTrue(
            action.contains("wm size reset") && action.contains("wm density reset") && action.contains("wm overscan reset"),
            "resets VD size/density/overscan",
        )
    }

    @Test
    fun `is honest — never promises firmware-level recovery`() {
        assertTrue(action.contains("power-cycle"), "final message tells the user to power-cycle")
        assertTrue(action.contains("UNINSTALL DashCast") || action.contains("GỠ DashCast"), "tells the user to uninstall DashCast")
    }

    @Test
    fun `controller constructs and binds the deep-rescue button`() {
        assertTrue(controller.contains("CastDeepRescueAction("), "controller constructs the action")
        assertTrue(controller.contains("R.id.cast_deep_rescue"), "binds the deep-rescue button")
        assertTrue(controller.contains("FloatingBubbleService"), "stopOwnServices targets the bubble service")
    }
}
