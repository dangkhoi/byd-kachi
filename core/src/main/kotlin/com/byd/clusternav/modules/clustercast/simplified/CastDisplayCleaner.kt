package com.byd.clusternav.modules.clustercast.simplified

/**
 * Utility for cleaning up stale tasks from the cluster display.
 *
 * Extracted from SimpleCastCoordinator to keep it under 500 LOC.
 * Called during openProjection and closeProjection for slate cleanup.
 */
internal object CastDisplayCleaner {

    /**
     * Return ALL non-system tasks from [displayId] to display 0.
     * Uses CastStackParser to find tasks, then moves each back.
     * Idempotent: safe to call even if display is already empty.
     */
    fun cleanDisplay(
        shell: SimpleCastShell,
        displayId: Int,
        sleepMs: (Long) -> Unit = { Thread.sleep(it) },
    ) {
        val result = shell.execute("am stack list")
        if (!result.success) return

        val tasksOnCluster = CastStackParser.tasksToClean(result.stdout, displayId)
        if (tasksOnCluster.isEmpty()) return

        // Find a target stack on display 0
        var targetStack = CastStackParser.findTargetStackOnDisplay0(result.stdout)

        // Ensure we have a target stack on display 0 — create one if needed
        if (targetStack == null) {
            shell.execute("am start --display 0 --windowingMode 1 -n 'com.android.settings/.Settings'")
            sleepMs(1000)
            val recheck = shell.execute("am stack list")
            if (recheck.success) {
                targetStack = CastStackParser.findTargetStackOnDisplay0(recheck.stdout)
            }
        }

        for (task in tasksOnCluster) {
            if (targetStack != null) {
                val moveResult = shell.execute("am stack move-task ${task.taskId} $targetStack true")
                if (moveResult.success) {
                    val activity = task.component.takeIf { it.contains("/") }
                    if (activity != null) {
                        shell.execute("am start --display 0 --windowingMode 1 -n '$activity'")
                    }
                    sleepMs(300)
                    continue
                }
            }
            // Fallback: try am start (works for exported activities)
            val activity = task.component.takeIf { it.contains("/") }
            if (activity != null) {
                shell.execute("am start --display 0 --windowingMode 1 -n '$activity'")
            }
            sleepMs(300)
        }

        // Close stale projection
        shell.execute("service call AutoContainer 2 i32 1000 i32 18 s16 \"\"")
        sleepMs(300)
        shell.execute("service call AutoContainer 2 i32 1000 i32 0 s16 \"\"")
        sleepMs(500)
    }
}
