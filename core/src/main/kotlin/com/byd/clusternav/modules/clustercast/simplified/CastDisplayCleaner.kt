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
        /** Dựng lệnh AutoContainer theo hồ sơ đời xe (2.89 · CLUSTER-THEME-SAFE: đúng tên service, luôn kèm `s16 ""`). */
        command: (Int) -> String = ProjectionRecipe.SEAL_DL3::command,
    ) {
        // Guard tầng thi hành (CLAUDE §4/§5): không bao giờ quét display 0 hay id chưa dò được. Caller phải truyền
        // id cụm đã xác minh live (R1/R2 spec kachi-hal187-cast-remediation) — dọn nhầm VD ô của launcher = bê
        // app trong ô về màn giữa.
        if (displayId < 1) return
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

        // Close stale projection — opcode 18 → 0 giữ nguyên (đường đã chạy); chỉ tên service đi theo hồ sơ.
        shell.execute(command(18))
        sleepMs(300)
        shell.execute(command(0))
        sleepMs(500)
    }
}
