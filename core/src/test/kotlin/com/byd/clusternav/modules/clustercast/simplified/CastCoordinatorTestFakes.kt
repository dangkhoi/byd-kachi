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
    /** When true, apps started on the cluster display won't appear in subsequent stack list (simulates failed landing). */
    var blockAppOnDisplay1 = false
    /** Commands containing any of these substrings will fail. */
    val failCommands = mutableListOf<String>()

    /**
     * Logical id của VD cụm giả lập (mặc định 1 = giá trị lịch sử của các test cũ). Regression 2026-09-15
     * ([ĐO] cụm = display 2, display 1 = ô `kachi-slot-0` của launcher) dùng 2 + [clusterDetectOut] có dòng slot.
     */
    var clusterDisplayId = 1
    /** Gói của chính launcher (khớp `selfPackage` truyền vào coordinator) — placeholder ClusterBlack không bị coi là app. */
    var selfPackage = "com.byd.clusternav"
    /** Output trả cho [ClusterDisplayResolver.DETECT_CMD]; null = dựng từ [clusterDisplayId] (dạng grep thật trên xe). */
    var clusterDetectOut: String? = null

    /**
     * CLUSTER-THEME-SAFE (2.89) — dòng `configuration=` của stack cụm (id 2). `null` (mặc định) = KHÔNG in, đúng hành vi
     * cũ của fake ⇒ loại stack đọc ra trống ⇒ rào gỡ ([ClusterThemePlan.admissible], so CHỮ `standard`) từ chối gỡ.
     * Bài theme đặt dòng có `mActivityType=standard` (dạng dump thật `carlog-kachi-20260914-2044/10-am-stack-list.txt`).
     */
    var clusterStackConfig: String? = null

    /**
     * CLUSTER-THEME-SAFE B1a — `true` = lần mở ĐẦU sau nổ máy: màn ảo cụm CHƯA có cho tới khi lệnh chiếu `16` đã gửi
     * ([ĐO F2] sau reboot màn ảo cụm vắng, AutoContainer tạo nó khi mở projection). Mặc định `false` = hành vi lịch sử của
     * fake (màn ảo luôn có).
     */
    var vdAbsentUntilCast = false

    private fun vdExists(): Boolean = !vdAbsentUntilCast || history.any { it.contains(" i32 1000 i32 16 ") }

    /**
     * B1b — in `bounds=[l,t][r,b]` trên DÒNG TASK của app trên cụm (dạng dump thật, `CastStackParser.taskBoundsOn`): khung =
     * lệnh `am task resize` gần nhất được "áp" cho task đó, chưa có ⇒ `[0,0][1920,720]`. `false` (mặc định) = hành vi cũ.
     */
    var reportTaskBounds = false

    /**
     * B1b — mỗi phần tử `"l t r b"` "nuốt" MỘT lệnh `am task resize <id> l t r b` khớp (exit 0 mà WM không áp) — giả lập khung
     * lệch cho đường đọc-lại + thử-lại-một-lần.
     */
    val swallowResizeBounds = mutableListOf<String>()

    private val taskBounds = mutableMapOf<Int, String>()

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
        if (command.startsWith("am task resize ")) {
            val p = command.removePrefix("am task resize ").trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
            if (p.size == 5) {
                val b = "${p[1]} ${p[2]} ${p[3]} ${p[4]}"
                if (!swallowResizeBounds.remove(b)) taskBounds[p[0]] = "[${p[1]},${p[2]}][${p[3]},${p[4]}]"
            }
            return ShellResult(0, "", "")
        }
        // Simulate am stack list output for task/stack discovery
        if (command == "am stack list") {
            return ShellResult(0, fakeStackListOutput(), "")
        }
        // Simulate `dumpsys display | grep …` — cluster VD detection (R1: coordinator resolves LIVE before placing)
        if (command == ClusterDisplayResolver.DETECT_CMD) {
            return ShellResult(0, clusterDetectOut ?: if (vdExists()) defaultDetectOut() else "  Display 0:\n", "")
        }
        // CLUSTER-THEME-SAFE — mỗi task trong bản `am stack list` giả có một cửa sổ cùng display (+ bàn phím ở display 0).
        if (command == ClusterThemeGuard.WINDOWS_CMD) {
            return ShellResult(0, fakeWindowsOutput(), "")
        }
        return ShellResult(0, "", "")
    }

    /** `am stack remove 2` (stack cụm) gần hơn lần `am start` ClusterBlack gần nhất ⇒ placeholder đã bị gỡ. */
    private fun placeholderRemoved(onCluster: String): Boolean {
        val removed = history.indexOfLast { it == "am stack remove $CLUSTER_STACK" }
        val started = history.indexOfLast { it.startsWith("am start") && it.contains(onCluster) && it.contains("ClusterBlackActivity") }
        return removed > started
    }

    /** Dạng `dumpsys window windows | grep -E 'Window #|mDisplayId='` thật (dòng tiêu đề + dòng mDisplayId). */
    private fun fakeWindowsOutput(): String {
        val sb = StringBuilder()
        sb.appendLine("  Window #0 Window{739f3cf u0 InputMethod}:")
        sb.appendLine("    mDisplayId=0 stackId=0 mSession=Session{b250230 3982:u0a10062} mClient=android.os.BinderProxy@bbadc2e")
        com.byd.clusternav.modules.clustercast.StackParse.parse(fakeStackListOutput()).forEachIndexed { i, e ->
            sb.appendLine("  Window #${i + 1} Window{${(0xa000 + i).toString(16)} u0 ${e.comp}}:")
            sb.appendLine("    mDisplayId=${e.displayId} stackId=${e.stackId} mSession=Session{0 0:u0a10138} mClient=android.os.BinderProxy@0")
        }
        return sb.toString()
    }

    /** Dạng grep thật trên xe 2026-09-15 (fission = cụm), id thay bằng [clusterDisplayId]. */
    private fun defaultDetectOut(): String = """
        |  DisplayDeviceInfo{"fission_bg_xdjaVirtualSurface": uniqueId="virtual:com.xdja.containerservice,1000,fission_bg_xdjaVirtualSurface,0", 1920 x 720, modeId 3, defaultModeId 3, supportedModes [{id=3, width=1920, height=720, fps=60.0}], colorMode 0, supportedColorModes [0], HdrCapabilities null, density 320, 320.0 x 320.0 dpi, appVsyncOff 0, presDeadline 16666666, touch NONE, rotation 0, type VIRTUAL, state ON, owner com.xdja.containerservice (uid 1000), FLAG_PRESENTATION, FLAG_OWN_CONTENT_ONLY}
        |    mUniqueId=virtual:com.xdja.containerservice,1000,fission_bg_xdjaVirtualSurface,0
        |  Display 0:
        |  Display $clusterDisplayId:
        |    mPrimaryDisplayDevice=fission_bg_xdjaVirtualSurface
        |    mBaseDisplayInfo=DisplayInfo{"fission_bg_xdjaVirtualSurface, displayId $clusterDisplayId", uniqueId "virtual:com.xdja.containerservice,1000,fission_bg_xdjaVirtualSurface,0", app 1920 x 720, real 1920 x 720, ...}
        |""".trimMargin()

    /** Simulates am stack list with tasks on display 0 and a freeform stack on the cluster display. */
    private fun fakeStackListOutput(): String {
        val sb = StringBuilder()
        val d = clusterDisplayId
        val onCluster = "--display $d"
        // Home stack on display 0
        sb.appendLine("Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0")
        sb.appendLine("  taskId=1: com.android.launcher3/com.android.launcher3.Launcher visible=true")
        // Running tasks on display 0 (except those moved to the cluster)
        val movedTaskIds = history
            .filter { it.startsWith("am stack move-task") }
            .mapNotNull { Regex("""move-task\s+(\d+)""").find(it)?.groupValues?.get(1)?.toInt() }
            .toSet()
        for ((pkg, taskId) in runningTasks) {
            if (taskId !in movedTaskIds) {
                sb.appendLine("  taskId=$taskId: $pkg/.MainActivity visible=true")
            }
        }
        // Apps on the cluster: started with --display <cluster> OR moved there via move-task
        val removed = placeholderRemoved(onCluster)
        if (blockAppOnDisplay1) {
            // Simulate: apps don't appear on the cluster (postcondition will fail)
            if (history.any { it.contains(onCluster) } && !removed) {
                sb.appendLine("Stack id=$CLUSTER_STACK bounds=[0,0][1920,720] displayId=$d userId=0")
                clusterStackConfig?.let { sb.appendLine(it) }
                sb.appendLine("  taskId=99: $selfPackage/.modules.clustercast.ClusterBlackActivity visible=true")
            }
            return sb.toString()
        }
        val startedOnCluster = history
            .filter { it.contains(onCluster) && it.startsWith("am start") }
            .mapNotNull { cmd ->
                Regex("""-n\s+'?([^/']+)/""").find(cmd)?.groupValues?.get(1)
                    ?: Regex("""-n\s+'?(\S+)/""").find(cmd)?.groupValues?.get(1)?.removeSurrounding("'")
            }
            .distinct()
            .filter { it != "com.android.settings" }
        val movedToCluster = movedTaskIds.mapNotNull { tid ->
            runningTasks.entries.firstOrNull { it.value == tid }?.key
        }
        val allOnCluster = (startedOnCluster + movedToCluster).distinct()
        val apps = allOnCluster.filter { it != selfPackage }
        if (apps.isNotEmpty() || (history.any { it.contains(onCluster) } && !removed)) {
            sb.appendLine("Stack id=$CLUSTER_STACK bounds=[0,0][1920,720] displayId=$d userId=0")
            clusterStackConfig?.let { sb.appendLine(it) }
            if (!removed) sb.appendLine("  taskId=99: $selfPackage/.modules.clustercast.ClusterBlackActivity visible=true")
            var tid = 100
            for (pkg in allOnCluster) {
                // The ClusterNav projection placeholder is already emitted above as taskId=99
                // (ClusterBlackActivity). Launching it does NOT create a second MainActivity task on
                // the cluster, so don't fabricate one — that stray would (correctly) be evicted by
                // CastStackParser.tasksToClean and skew close/clean sequences (bug-b fix, 2026-08-12).
                if (pkg == selfPackage) continue
                val t = tid++
                val b = if (reportTaskBounds) " bounds=${taskBounds[t] ?: "[0,0][1920,720]"}" else ""
                sb.appendLine("  taskId=$t: $pkg/.MainActivity$b visible=true")
            }
        }
        return sb.toString()
    }
}

/** Id stack cụm trong bản `am stack list` giả của [FakeShell]. */
private const val CLUSTER_STACK = 2

class FakePrefs : SimpleCastPrefs {
    private val configs = mutableMapOf<String, DisplayConfig>()
    private var lastDisplay: Int? = null
    private var _autoStartPackage: String? = null
    private var _autoStartEnabled: Boolean = false
    private var _splitRatioLeftPercent: Int = 50
    private var _autoStartLeftPackage: String? = null
    private var _autoStartRightPackage: String? = null
    private var _autoStartSplitEnabled: Boolean = false
    private var _castEnabled: Boolean = false
    private var _bubbleVisible: Boolean = true

    override fun displayConfigFor(pkg: String): DisplayConfig? = displayConfigFor(pkg, CastProfile.FULL)
    override fun displayConfigFor(pkg: String, profile: CastProfile): DisplayConfig? = configs[profileKey(pkg, profile)]
    override fun saveDisplayConfig(pkg: String, config: DisplayConfig) = saveDisplayConfig(pkg, CastProfile.FULL, config)
    override fun saveDisplayConfig(pkg: String, profile: CastProfile, config: DisplayConfig) {
        configs[profileKey(pkg, profile)] = config
    }

    /** Mirrors SharedPrefsSimpleCastPrefs: cùng một bộ dựng khoá [CastProfile.recordKey] (B1b — có hậu tố `__RECT`). */
    private fun profileKey(pkg: String, profile: CastProfile): String = profile.recordKey(pkg)

    /** B1b — các khoá bản ghi đã lưu (để test khẳng định khung Chữ nhật KHÔNG đè khoá Bo tròn). */
    val savedRecordKeys: Set<String> get() = configs.keys.toSet()
    override fun lastDisplayId(): Int? = lastDisplay
    override fun saveLastDisplayId(id: Int) { lastDisplay = id }

    override fun autoStartPackage(): String? = _autoStartPackage
    override fun setAutoStartPackage(pkg: String?) { _autoStartPackage = pkg }
    override fun autoStartEnabled(): Boolean = _autoStartEnabled
    override fun setAutoStartEnabled(enabled: Boolean) { _autoStartEnabled = enabled }

    override fun splitRatioLeftPercent(): Int = _splitRatioLeftPercent
    override fun setSplitRatioLeftPercent(pct: Int) { _splitRatioLeftPercent = pct }

    override fun autoStartLeftPackage(): String? = _autoStartLeftPackage
    override fun setAutoStartLeftPackage(pkg: String?) { _autoStartLeftPackage = pkg }
    override fun autoStartRightPackage(): String? = _autoStartRightPackage
    override fun setAutoStartRightPackage(pkg: String?) { _autoStartRightPackage = pkg }
    override fun autoStartSplitEnabled(): Boolean = _autoStartSplitEnabled
    override fun setAutoStartSplitEnabled(enabled: Boolean) { _autoStartSplitEnabled = enabled }

    override fun castEnabled(): Boolean = _castEnabled
    override fun setCastEnabled(enabled: Boolean) { _castEnabled = enabled }

    /** WP6 · R6.1 — mặc định TRUE, y như bản thật (`SharedPrefsSimpleCastPrefs`): nút nổi có mặt tới khi bị tắt. */
    override fun bubbleVisible(): Boolean = _bubbleVisible
    override fun setBubbleVisible(visible: Boolean) { _bubbleVisible = visible }
}
