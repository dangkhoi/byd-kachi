package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Bug (b) regression: cluster cleanup must EVICT a stray ClusterNav activity (e.g. MainActivity
 * that surfaced on display 1 after a CarPlay cast→return→cast cycle) while KEEPING the black
 * projection placeholder. The previous implementation skipped the whole `com.byd.clusternav`
 * package, so the stray stayed on the cluster "forever until manually cleaned" (owner 2026-08-12).
 */
class CastStackParserTasksToCleanTest {

    private val CLUSTER = 1

    private fun stack(displayId: Int, vararg tasks: String): String = buildString {
        appendLine("Stack id=${10 + displayId} bounds=[0,0][1920,720] displayId=$displayId userId=0")
        tasks.forEach { appendLine("  $it") }
    }

    private fun task(taskId: Int, component: String, visible: Boolean = true): String =
        "taskId=$taskId: $component bounds=[0,0][1920,720] visible=$visible topActivity=ComponentInfo{$component}"

    private val blackPlaceholder =
        task(99, "com.byd.clusternav/.modules.clustercast.ClusterBlackActivity")
    private val mainActivity =
        task(33, "com.byd.clusternav/.MainActivity")

    @Test fun `stray ClusterNav MainActivity on cluster is evicted`() {
        val out = stack(CLUSTER, mainActivity)
        val cleaned = CastStackParser.tasksToClean(out, CLUSTER)
        assertEquals(listOf(33), cleaned.map { it.taskId }, "MainActivity stuck on cluster must be cleaned")
    }

    @Test fun `black placeholder on cluster is kept`() {
        val out = stack(CLUSTER, blackPlaceholder)
        val cleaned = CastStackParser.tasksToClean(out, CLUSTER)
        assertTrue(cleaned.isEmpty(), "ClusterBlackActivity keeps projection alive — must never be cleaned")
    }

    @Test fun `placeholder kept but stray MainActivity evicted when both present`() {
        val out = stack(CLUSTER, blackPlaceholder, mainActivity)
        val cleaned = CastStackParser.tasksToClean(out, CLUSTER)
        assertEquals(listOf(33), cleaned.map { it.taskId })
        assertFalse(cleaned.any { it.component.contains("ClusterBlackActivity") })
    }

    @Test fun `normal third-party app on cluster is still cleaned`() {
        val out = stack(CLUSTER, task(41, "vn.vietmap.live/vn.vietmap.live.MainActivity"))
        val cleaned = CastStackParser.tasksToClean(out, CLUSTER)
        assertEquals(listOf(41), cleaned.map { it.taskId })
    }

    @Test fun `system, launcher and carplay tasks are never cleaned`() {
        val out = stack(
            CLUSTER,
            task(1, "com.android.systemui/.Recents"),
            task(2, "com.android.launcher3/.Launcher"),
            task(3, "com.byd.carplay.ui/.CarPlayActivity"),
            task(4, "com.android.settings/.Settings"),
        )
        assertTrue(CastStackParser.tasksToClean(out, CLUSTER).isEmpty())
    }

    @Test fun `ClusterNav activity on the home display is not touched (only the cluster is cleaned)`() {
        // MainActivity belongs on display 0 — cleaning targets the cluster display only.
        val out = stack(0, mainActivity) + stack(CLUSTER, blackPlaceholder)
        assertTrue(CastStackParser.tasksToClean(out, CLUSTER).isEmpty())
    }

    @Test fun `ClusterNavActivity card fallback stuck on cluster is also evicted`() {
        val out = stack(CLUSTER, task(55, "com.byd.clusternav/.ClusterNavActivity"))
        val cleaned = CastStackParser.tasksToClean(out, CLUSTER)
        assertEquals(listOf(55), cleaned.map { it.taskId })
    }
}
