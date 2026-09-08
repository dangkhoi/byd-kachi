package com.byd.clusternav.modules.clustercast.simplified

/**
 * Parser for `am stack list` output on BYD DiLink3 (Android 10).
 *
 * Extracts: foreground package, task IDs for packages, visible tasks on a display,
 * and all non-system tasks on a given display (for cleanup).
 *
 * Format reference (measured on vehicle):
 * ```
 * Stack id=10 bounds=[0,0][1920,1080] displayId=0 userId=0
 *   taskId=33: vn.vietmap.live/vn.vietmap.live.MainActivity bounds=... visible=true topActivity=...
 * ```
 */
object CastStackParser {

    /** Result of parsing a single task entry from `am stack list`. */
    data class ParsedTask(
        val taskId: Int,
        val component: String,
        val pkg: String,
        val displayId: Int,
        val visible: Boolean,
    )

    /**
     * Parse all tasks from `am stack list` output.
     * Returns list of tasks with their display assignment and visibility.
     */
    fun parseTasks(amOutput: String): List<ParsedTask> {
        val tasks = mutableListOf<ParsedTask>()
        var currentDisplayId = -1
        for (line in amOutput.lines()) {
            val stackMatch = STACK_HEADER.find(line)
            if (stackMatch != null) {
                currentDisplayId = stackMatch.groupValues[1].toIntOrNull() ?: -1
                continue
            }
            val taskMatch = TASK_LINE.find(line) ?: continue
            val taskId = taskMatch.groupValues[1].toIntOrNull() ?: continue
            val component = taskMatch.groupValues[2]
            val pkg = component.substringBefore("/")
            val visible = line.contains("visible=true")
            tasks += ParsedTask(taskId, component, pkg, currentDisplayId, visible)
        }
        return tasks
    }

    /**
     * Find the foreground package on [displayId], excluding packages in [excluded].
     * Skips tasks in pinned (PiP) stacks — those are overlay windows, not the actual foreground app.
     * Returns null if no visible non-excluded package, or if shell output is blank.
     */
    fun foreground(amOutput: String, displayId: Int, excluded: Set<String>): String? {
        var currentDisplayId = -1
        var currentStackPinned = false
        for (line in amOutput.lines()) {
            val stackMatch = STACK_HEADER.find(line)
            if (stackMatch != null) {
                currentDisplayId = stackMatch.groupValues[1].toIntOrNull() ?: -1
                currentStackPinned = false // reset; will be set by configuration line
                continue
            }
            // Detect pinned windowing mode from configuration line
            if (line.contains("mWindowingMode=pinned")) {
                currentStackPinned = true
                continue
            }
            if (currentDisplayId == displayId && !currentStackPinned && line.contains("visible=true")) {
                val taskMatch = Regex("""taskId=\d+:\s*([^/\s]+)/""").find(line)
                val pkg = taskMatch?.groupValues?.get(1) ?: continue
                if (pkg in excluded) continue
                return pkg
            }
        }
        return null
    }

    /**
     * Result of a typed task lookup — either exactly one match, ambiguous, or not found.
     */
    sealed interface TaskLookupResult {
        data class Found(val taskId: String, val displayId: Int) : TaskLookupResult
        data class Ambiguous(val matches: List<Pair<String, Int>>) : TaskLookupResult
        object NotFound : TaskLookupResult
    }

    /**
     * Find task ID for a package, preferring tasks on [preferredDisplayId].
     * Returns null if not found.
     *
     * NOTE: This is the legacy nullable API. For safety-critical paths, use [findTaskIdTyped].
     */
    fun findTaskId(amOutput: String, pkg: String, preferredDisplayId: Int): String? {
        val result = findTaskIdTyped(amOutput, pkg, preferredDisplayId)
        return when (result) {
            is TaskLookupResult.Found -> result.taskId
            is TaskLookupResult.Ambiguous -> result.matches.firstOrNull()?.first
            TaskLookupResult.NotFound -> null
        }
    }

    /**
     * Typed task lookup with ambiguity detection.
     *
     * Uses [Regex.escape] on [pkg] to prevent regex injection from package names
     * containing metacharacters (e.g. `com.test+app` or attacker-crafted names).
     *
     * Returns:
     * - [TaskLookupResult.Found] if exactly one task matches on preferred display (or fallback).
     * - [TaskLookupResult.Ambiguous] if multiple distinct tasks match.
     * - [TaskLookupResult.NotFound] if no match.
     */
    fun findTaskIdTyped(amOutput: String, pkg: String, preferredDisplayId: Int): TaskLookupResult {
        val escapedPkg = Regex.escape(pkg)
        val taskRegex = Regex("""taskId=(\d+):[^\n]*$escapedPkg""")
        var currentDisplayId = -1
        val allMatches = mutableListOf<Pair<String, Int>>() // taskId to displayId

        for (line in amOutput.lines()) {
            val sm = STACK_HEADER.find(line)
            if (sm != null) { currentDisplayId = sm.groupValues[1].toIntOrNull() ?: -1; continue }
            val tm = taskRegex.find(line)
            if (tm != null) {
                allMatches += tm.groupValues[1] to currentDisplayId
            }
        }

        if (allMatches.isEmpty()) return TaskLookupResult.NotFound

        // Prefer match on the target display
        val preferred = allMatches.filter { it.second == preferredDisplayId }
        if (preferred.size == 1) return TaskLookupResult.Found(preferred[0].first, preferred[0].second)
        if (preferred.size > 1) return TaskLookupResult.Ambiguous(preferred)

        // Fallback to first match on any display
        if (allMatches.size == 1) return TaskLookupResult.Found(allMatches[0].first, allMatches[0].second)
        return TaskLookupResult.Ambiguous(allMatches)
    }

    /**
     * Check if a package has a visible task on [displayId].
     * Uses exact package match (escaped) — no substring/prefix matching.
     */
    fun isAppOnDisplay(amOutput: String, pkg: String, displayId: Int): Boolean {
        var currentDisplayId = -1
        for (line in amOutput.lines()) {
            val sm = STACK_HEADER.find(line)
            if (sm != null) { currentDisplayId = sm.groupValues[1].toIntOrNull() ?: -1; continue }
            if (currentDisplayId == displayId && line.contains(pkg) && line.contains("visible=true")) {
                // Double-check with boundary: ensure it's the full package, not a prefix match
                // e.g. "com.test" should not match "com.test.other"
                val taskMatch = TASK_LINE.find(line) ?: continue
                val foundPkg = taskMatch.groupValues[2].substringBefore("/")
                if (foundPkg == pkg) return true
            }
        }
        return false
    }

    /**
     * The ONE ClusterNav activity allowed to remain on the cluster: the black projection
     * placeholder ([com.byd.clusternav.modules.clustercast.ClusterBlackActivity]). It is launched on
     * display 1 to keep the OEM projection alive, so cleanup must never evict it.
     *
     * Matched by simple class-name substring so it is independent of the leading-dot vs
     * fully-qualified component spelling that `am stack list` may print.
     */
    const val CLUSTER_PLACEHOLDER_ACTIVITY = "ClusterBlackActivity"

    /**
     * Find all non-system tasks on [displayId] that should be cleaned (moved back to display 0).
     * Excludes: launcher, system UI, system framework, CarPlay (auto-relaunches), and the
     * ClusterNav projection PLACEHOLDER ([CLUSTER_PLACEHOLDER_ACTIVITY]).
     *
     * Bug (b) fix (owner 2026-08-12): the previous version skipped the WHOLE `com.byd.clusternav`
     * package, so a ClusterNav non-placeholder activity that got stuck on the cluster (observed:
     * `MainActivity` surfacing on display 1 after a CarPlay cast→return→cast cycle) was NEVER
     * evicted — it stayed "forever until manually cleaned". We now keep ONLY the black placeholder
     * and evict every other ClusterNav activity (MainActivity, ClusterNavActivity, …) so the cluster
     * can return to the cast app or native gauges.
     */
    fun tasksToClean(amOutput: String, displayId: Int): List<ParsedTask> {
        val skipExactPkgs = setOf(
            "com.android.launcher3",
            "com.android.systemui",
            "com.byd.carplay.ui",
        )
        return parseTasks(amOutput).filter { task ->
            task.displayId == displayId &&
                !task.pkg.startsWith("com.android.") &&
                task.pkg !in skipExactPkgs &&
                // Keep ONLY the black placeholder; every other ClusterNav activity on the cluster is
                // a leak and must be evicted (bug b). Non-ClusterNav apps are unaffected.
                !task.component.contains(CLUSTER_PLACEHOLDER_ACTIVITY)
        }
    }

    /**
     * Find a usable target stack on display 0 (non-home, id > 0).
     */
    fun findTargetStackOnDisplay0(amOutput: String): Int? {
        for (line in amOutput.lines()) {
            val m = Regex("""Stack id=(\d+).*displayId=0""").find(line) ?: continue
            val stackId = m.groupValues[1].toIntOrNull() ?: continue
            if (stackId > 0) return stackId
        }
        return null
    }

    private val STACK_HEADER = Regex("""Stack id=\d+.*displayId=(\d+)""")
    private val TASK_LINE = Regex("""taskId=(\d+):\s*(\S+)""")
}
