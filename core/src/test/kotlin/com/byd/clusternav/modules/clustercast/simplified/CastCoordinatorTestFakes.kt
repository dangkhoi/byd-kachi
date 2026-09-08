package com.byd.clusternav.modules.clustercast.simplified

import java.util.concurrent.CopyOnWriteArrayList

// ─── Shared test fakes ──────────────────────────────────────────────────────────
//
// Extracted verbatim from SimpleCastCoordinatorTest.kt (2026-08-05, Stage 2 / T1) so the
// coordinator test file stays ≤ 500 LOC. These fakes are shared infrastructure — also used by
// CastCoordinatorPolicyEnforcementTest and CastSafetyTest in this package. Logic is unchanged.

class FakeShell : SimpleCastShell {
    val history = CopyOnWriteArrayList<String>()
    var shouldFail = false
    /** Packages to simulate as already running (with taskIds) for am stack list. */
    val runningTasks = mutableMapOf<String, Int>()
    /** When true, apps started on display 1 won't appear in subsequent stack list (simulates failed landing). */
    var blockAppOnDisplay1 = false
    /** Commands containing any of these substrings will fail. */
    val failCommands = mutableListOf<String>()

    init {
        // CP/AA are always already running when user requests cast (they're system apps)
        runningTasks["com.byd.autolink.carplay"] = 10
        runningTasks["com.google.android.projection.gearhead"] = 11
    }

    override fun execute(command: String): ShellResult {
        history.add(command)
        if (shouldFail) return ShellResult(1, "", "fake error")
        // Check per-command failures
        if (failCommands.any { command.contains(it) }) {
            return ShellResult(1, "", "fake failure for: $command")
        }
        // Simulate am stack list output for task/stack discovery
        if (command == "am stack list") {
            return ShellResult(0, fakeStackListOutput(), "")
        }
        return ShellResult(0, "", "")
    }

    /** Simulates am stack list with tasks on display 0 and a freeform stack on display 1. */
    private fun fakeStackListOutput(): String {
        val sb = StringBuilder()
        // Home stack on display 0
        sb.appendLine("Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0")
        sb.appendLine("  taskId=1: com.android.launcher3/com.android.launcher3.Launcher visible=true")
        // Running tasks on display 0 (except those moved to display 1)
        val movedTaskIds = history
            .filter { it.startsWith("am stack move-task") }
            .mapNotNull { Regex("""move-task\s+(\d+)""").find(it)?.groupValues?.get(1)?.toInt() }
            .toSet()
        for ((pkg, taskId) in runningTasks) {
            if (taskId !in movedTaskIds) {
                sb.appendLine("  taskId=$taskId: $pkg/.MainActivity visible=true")
            }
        }
        // Apps on display 1: started with --display 1 OR moved there via move-task
        if (blockAppOnDisplay1) {
            // Simulate: apps don't appear on display 1 (postcondition will fail)
            if (history.any { it.contains("--display 1") }) {
                sb.appendLine("Stack id=2 bounds=[0,0][1920,720] displayId=1 userId=0")
                sb.appendLine("  taskId=99: com.byd.clusternav/.modules.clustercast.ClusterBlackActivity visible=true")
            }
            return sb.toString()
        }
        val startedOnD1 = history
            .filter { it.contains("--display 1") && it.startsWith("am start") }
            .mapNotNull { cmd ->
                Regex("""-n\s+'?([^/']+)/""").find(cmd)?.groupValues?.get(1)
                    ?: Regex("""-n\s+'?(\S+)/""").find(cmd)?.groupValues?.get(1)?.removeSurrounding("'")
            }
            .distinct()
            .filter { it != "com.android.settings" }
        val movedToD1 = movedTaskIds.mapNotNull { tid ->
            runningTasks.entries.firstOrNull { it.value == tid }?.key
        }
        val allOnD1 = (startedOnD1 + movedToD1).distinct()
        if (allOnD1.isNotEmpty() || history.any { it.contains("--display 1") }) {
            sb.appendLine("Stack id=2 bounds=[0,0][1920,720] displayId=1 userId=0")
            sb.appendLine("  taskId=99: com.byd.clusternav/.modules.clustercast.ClusterBlackActivity visible=true")
            var tid = 100
            for (pkg in allOnD1) {
                // The ClusterNav projection placeholder is already emitted above as taskId=99
                // (ClusterBlackActivity). Launching it does NOT create a second MainActivity task on
                // the cluster, so don't fabricate one — that stray would (correctly) be evicted by
                // CastStackParser.tasksToClean and skew close/clean sequences (bug-b fix, 2026-08-12).
                if (pkg == "com.byd.clusternav") continue
                sb.appendLine("  taskId=${tid++}: $pkg/.MainActivity visible=true")
            }
        }
        return sb.toString()
    }
}

class FakePrefs : SimpleCastPrefs {
    private val configs = mutableMapOf<String, DisplayConfig>()
    private var lastDisplay: Int? = null
    private var _autoStartPackage: String? = null
    private var _autoStartEnabled: Boolean = false
    private var _splitRatioLeftPercent: Int = 50
    private var _dozeWhitelistApplied: Boolean = false
    private var _autoStartLeftPackage: String? = null
    private var _autoStartRightPackage: String? = null
    private var _autoStartSplitEnabled: Boolean = false
    private var _castEnabled: Boolean = false

    override fun displayConfigFor(pkg: String): DisplayConfig? = displayConfigFor(pkg, CastProfile.FULL)
    override fun displayConfigFor(pkg: String, profile: CastProfile): DisplayConfig? = configs[profileKey(pkg, profile)]
    override fun saveDisplayConfig(pkg: String, config: DisplayConfig) = saveDisplayConfig(pkg, CastProfile.FULL, config)
    override fun saveDisplayConfig(pkg: String, profile: CastProfile, config: DisplayConfig) {
        configs[profileKey(pkg, profile)] = config
    }

    /** Mirrors SharedPrefsSimpleCastPrefs: FULL = bare pkg key, others append `__<profile.key>`. */
    private fun profileKey(pkg: String, profile: CastProfile): String =
        if (profile.isFull) pkg else "${pkg}__${profile.key}"
    override fun lastDisplayId(): Int? = lastDisplay
    override fun saveLastDisplayId(id: Int) { lastDisplay = id }

    override fun autoStartPackage(): String? = _autoStartPackage
    override fun setAutoStartPackage(pkg: String?) { _autoStartPackage = pkg }
    override fun autoStartEnabled(): Boolean = _autoStartEnabled
    override fun setAutoStartEnabled(enabled: Boolean) { _autoStartEnabled = enabled }

    override fun splitRatioLeftPercent(): Int = _splitRatioLeftPercent
    override fun setSplitRatioLeftPercent(pct: Int) { _splitRatioLeftPercent = pct }

    override fun dozeWhitelistApplied(): Boolean = _dozeWhitelistApplied
    override fun setDozeWhitelistApplied(applied: Boolean) { _dozeWhitelistApplied = applied }

    override fun autoStartLeftPackage(): String? = _autoStartLeftPackage
    override fun setAutoStartLeftPackage(pkg: String?) { _autoStartLeftPackage = pkg }
    override fun autoStartRightPackage(): String? = _autoStartRightPackage
    override fun setAutoStartRightPackage(pkg: String?) { _autoStartRightPackage = pkg }
    override fun autoStartSplitEnabled(): Boolean = _autoStartSplitEnabled
    override fun setAutoStartSplitEnabled(enabled: Boolean) { _autoStartSplitEnabled = enabled }

    override fun castEnabled(): Boolean = _castEnabled
    override fun setCastEnabled(enabled: Boolean) { _castEnabled = enabled }
}
