package com.byd.clusternav.modules.clustercast.simplified

/**
 * Moves apps between display 0 (main) and display 1 (cluster).
 *
 * Strategy per app type:
 * - CP/AA: use `am stack move-task` (field-proven, no NPE)
 * - Normal: use `am start --display <id>` with freeform windowing mode
 *
 * Field-proven on BYD DiLink3 (Android 10), 2026-08-02.
 */
class AppMover(
    private val shell: SimpleCastShell,
    private val sleepMs: (Long) -> Unit = { Thread.sleep(it) },
    private val log: (String) -> Unit = { println("[AppMover] $it") },
) {
    /**
     * Moves an app to the cluster display using field-proven commands.
     * Returns the taskId that was moved (for CP/AA, needed for exact return), or null on failure.
     * For normal apps, returns -1 on success (taskId not tracked).
     */
    fun castToCluster(
        pkg: String,
        activity: String?,
        displayId: Int,
        appType: AppType,
        taskId: Int? = null,
        stackId: Int? = null,
        slotSide: ClusterSlotSide? = null,
        leftPercent: Int = 50,
    ): Int? {
        return when (appType) {
            AppType.CARPLAY, AppType.ANDROID_AUTO -> {
                // CP/AA uses move-task into an existing stack on the cluster display.
                // Try fullscreen stack first (windowingMode 1) — CP in freeform gets tiny bounds
                // and causes surfaceflinger crash on return. Fullscreen stack = CP fills display.
                val clusterStackId = stackId ?: findOrCreateClusterStack(displayId)
                    ?: run { log("CP cast FAIL: cannot find/create stack on display $displayId"); return null }
                val cpTaskId = taskId ?: findTaskId(pkg)
                    ?: run { log("CP cast FAIL: cannot find taskId for $pkg"); return null }
                val cmd = "am stack move-task $cpTaskId $clusterStackId true"
                log("CP cast: $cmd")
                val result = shell.execute(cmd)
                log("CP cast result: exit=${result.exitCode}, stdout=${result.stdout.take(200)}, stderr=${result.stderr.take(200)}")
                if (!result.success) return null
                // After move-task, CP inherits the stack's windowing mode.
                sleepMs(500)
                val (w, h) = when (appType) {
                    AppType.CARPLAY -> 1422 to 800
                    AppType.ANDROID_AUTO -> 1920 to 720
                    else -> 1920 to 720
                }
                val resizeCmd = "am task resize $cpTaskId 0 0 $w $h"
                log("CP resize (may fail for unresizable): $resizeCmd")
                val resizeResult = shell.execute(resizeCmd)
                log("CP resize result: exit=${resizeResult.exitCode}, stderr=${resizeResult.stderr.take(200)}")
                cpTaskId  // return the exact taskId we moved
            }
            AppType.NORMAL -> {
                // Resolve launcher activity component (proven pattern from CastPlacementCommands)
                val component = activity ?: resolveLauncherComponent(pkg) ?: "$pkg/.MainActivity"
                // R1 — fresh launch with freeform on cluster display.
                // NOTE: do NOT use --activity-clear-task — it kills the existing task and on
                // Android 10 BYD the new task fails to land on the cluster.
                // force_resizable_activities=1 must be set (checked/set on vehicle).
                val launchCmd = "am start -a android.intent.action.MAIN" +
                    " -c android.intent.category.LAUNCHER" +
                    " --display $displayId --windowingMode 5" +
                    " -n '$component'"
                val result = shell.execute(launchCmd)
                // Give the task ~1s to land, THEN verify by truth (`am stack list`) — never trust the
                // am-start exit code alone. X2 (measured DiLink3.0 2026-09-14): `am start --display <VD>`
                // onto a virtual display OWNED BY ANOTHER UID (cluster = com.xdja.containerservice) is
                // rejected by SafeActivityOptions.checkPermissions ("Permission Denial ... launchDisplayId"),
                // OR ActivityStarter silently re-targets display 0 — either way the app is left on display 0.
                sleepMs(1000)
                if (findTaskIdOnDisplay(pkg, displayId) == null) {
                    val denied = launchWasDenied(result)
                    log("cast R1 did not land $pkg on display $displayId (${if (denied) "Permission Denial" else "redirected/absent"}) → R2 move-task fallback")
                    if (!moveTaskToCluster(pkg, displayId, launchCmd)) {
                        log("cast FAIL: R1 (am start) + R2 (move-task) both failed to place $pkg on display $displayId")
                        return null
                    }
                }
                // Fit to cluster viewport after landing.
                fitToCluster(pkg, displayId, slotSide, leftPercent)
                -1  // success, taskId not tracked for normal apps
            }
        }
    }

    /**
     * R2 (X2) — đưa app [pkg] (bị bỏ lại trên display 0 sau khi R1 `am start --display` bị Permission Denial /
     * redirect) lên VD cụm bằng đường **AN TOÀN** `am stack move-task <taskId> <clusterStackId> true`.
     *
     * ⚠ [P0-1 · quality-review 2026-09-15] TRƯỚC ĐÂY dùng `am display move-stack` — chính repo CẤM bằng chữ:
     * `CarExecClusterProjectionCatalog.kt:60-83` ghi lệnh này **treo system_server 3/3** trên DiLink3 (NPE
     * `DisplayContent.moveStackToDisplay`, `TaskStack.mDisplayContent=null` khi vượt biên FREEFORM), phải rút cắm
     * lại, Android 10 không patch; `CastShell.kt` cũng cấm ở mọi nhánh. KDoc cũ bảo "proven ClusterCast.placeLadder"
     * nhưng ClusterCast là code **chết không chạy tới** ⇒ nhãn proven vô căn cứ. Nay dùng move-task như nhánh CP/AA
     * (đã proven): chuyển TASK của app vào một stack ĐÃ nằm trên màn cụm (dựng freeform để giữ fit-to-slot).
     * `true` = di chuyển cả các task phía trên trong stack nguồn cùng lên.
     *
     * @return true nếu SAU move-task app đã bám VD (kiểm bằng `am stack list`), else false.
     * 🚗 Cần verify trên xe ĐỖ (owner 2026-09-15: lên xe test trước khi push).
     */
    private fun moveTaskToCluster(pkg: String, displayId: Int, reissueCmd: String): Boolean {
        val taskId = findTaskId(pkg)
        if (taskId == null) { log("move-task: no task hosting $pkg to move"); return false }
        val clusterStackId = findOrCreateClusterStack(displayId, windowingMode = 5)
        if (clusterStackId == null) { log("move-task: cannot find/create stack on display $displayId"); return false }
        val out = shell.execute("am stack move-task $taskId $clusterStackId true")
        if (!out.success) { log("move-task rejected: ${out.stderr.take(120)}${out.stdout.take(120)}"); return false }
        // Ép composite: task đã ở VD nên am start không kéo được về 0.
        shell.execute(reissueCmd)
        sleepMs(700)
        return findTaskIdOnDisplay(pkg, displayId) != null
    }

    /** R1 bị SafeActivityOptions từ chối (VD của uid khác)? Đọc cả stdout lẫn stderr (am in Permission Denial ra cả 2). */
    private fun launchWasDenied(result: ShellResult): Boolean {
        val text = (result.stdout + "\n" + result.stderr)
        return text.contains("Permission Denial", ignoreCase = true) ||
            (text.contains("SecurityException") && text.contains("launchDisplayId"))
    }

    /**
     * Find an existing stack on the cluster display (any mode).
     * If none exists, launch a lightweight activity in FULLSCREEN mode to create one.
     * CP/AA must be in fullscreen stack — freeform causes tiny bounds + crash on return.
     * The dummy activity gets displaced when CP/AA moves into the stack.
     */
    private fun findOrCreateClusterStack(displayId: Int, windowingMode: Int = 1): Int? {
        // First: check if a stack already exists on the display
        findStackOnDisplay(displayId)?.let {
            log("cluster stack: found existing $it on display $displayId")
            return it
        }
        // None exists — launch a lightweight activity (Settings, có trên mọi đầu xe BYD) để DỰNG cấu trúc stack
        // trên màn cụm với [windowingMode] mong muốn: 1 = fullscreen (CP/AA), 5 = freeform (app thường — giữ được
        // fit-to-slot mà fitToCluster làm sau). move-task sau đó đẩy Settings ra.
        log("cluster stack: none on display $displayId, creating (mode=$windowingMode) via Settings")
        shell.execute(
            "am start --display $displayId --windowingMode $windowingMode -n 'com.android.settings/.Settings'"
        )
        sleepMs(1500)
        // Stack now exists; CP/AA move-task will displace settings automatically.
        return findStackOnDisplay(displayId).also {
            log("CP: after create, stack on display $displayId = $it")
        }
    }

    /** Find any freeform stack on the given display. */
    private fun findStackOnDisplay(displayId: Int): Int? {
        val result = shell.execute("am stack list")
        if (!result.success) return null
        // Parse: "Stack id=<N> ... displayId=<D> ..."
        val regex = Regex("Stack id=(\\d+)[^\\n]*displayId=$displayId")
        return regex.find(result.stdout)?.groupValues?.get(1)?.toIntOrNull()
    }

    /** Find a stack on the given display that already has tasks (proven to accept move-task).
     *  If none exists on display 0, create one by launching Settings. */
    private fun findNonHomeStackOnDisplay(targetDisplayId: Int): Int? {
        val found = findExistingStackOnDisplay(targetDisplayId)
        if (found != null) return found
        if (targetDisplayId != 0) return null
        // No usable stack on display 0 — create one
        shell.execute("am start --display 0 --windowingMode 1 -n 'com.android.settings/.Settings'")
        sleepMs(1000)
        return findExistingStackOnDisplay(0)
    }

    private fun findExistingStackOnDisplay(targetDisplayId: Int): Int? {
        val result = shell.execute("am stack list")
        if (!result.success) return null
        // Any non-home (id > 0) stack on the target display is a valid move-task target
        val regex = Regex("""Stack id=(\d+).*displayId=$targetDisplayId""")
        for (match in regex.findAll(result.stdout)) {
            val stackId = match.groupValues[1].toIntOrNull() ?: continue
            if (stackId > 0) return stackId
        }
        return null
    }

    /** Find the taskId for a running package (first match). */
    private fun findTaskId(pkg: String): Int? {
        val result = shell.execute("am stack list")
        if (!result.success) return null
        val regex = Regex("taskId=(\\d+):[^\\n]*$pkg")
        return regex.find(result.stdout)?.groupValues?.get(1)?.toIntOrNull()
    }

    /** Find the taskId for a package ON A SPECIFIC DISPLAY. Needed for CP return —
     *  CP may have multiple tasks, we need the one on the cluster (display 1). */
    private fun findTaskIdOnDisplay(pkg: String, targetDisplayId: Int): Int? {
        val result = shell.execute("am stack list")
        if (!result.success) return null
        var currentDisplayId = -1
        for (line in result.stdout.lines()) {
            val stackMatch = Regex("""Stack id=\d+.*displayId=(\d+)""").find(line)
            if (stackMatch != null) {
                currentDisplayId = stackMatch.groupValues[1].toIntOrNull() ?: -1
                continue
            }
            if (currentDisplayId == targetDisplayId) {
                val taskMatch = Regex("""taskId=(\d+):[^\n]*$pkg""").find(line)
                if (taskMatch != null) {
                    return taskMatch.groupValues[1].toIntOrNull()
                }
            }
        }
        return null
    }

    /**
     * After app lands on cluster, resize task to fill the cluster viewport.
     * Proven bounds: [0,90][1920,630] for default viewport on DiLink3 1920×720.
     * `am task resize` takes left, top, right, bottom — NOT width/height.
     *
     * If [slotSide] is specified, the bounds are calculated based on [leftPercent]:
     * - LEFT: [0, 90, 1920*leftPercent/100, 630]
     * - RIGHT: [1920*leftPercent/100, 90, 1920, 630]
     */
    private fun fitToCluster(pkg: String, displayId: Int, slotSide: ClusterSlotSide? = null, leftPercent: Int = 50) {
        // Find taskId ON THE CLUSTER DISPLAY specifically — not the first global match.
        // Bug fixed 2026-08-04: previous regex matched the first taskId across all displays,
        // so if the app had a task on display 0 it would resize the wrong one, leaving the
        // cluster task at full bounds (SL6 split cast failure).
        val taskId = findTaskIdOnDisplay(pkg, displayId)?.toString()
        if (taskId == null) {
            log("fitToCluster: no task for $pkg on display $displayId")
            return
        }
        // Calculate bounds based on slot side.
        // Use the display's actual wm size rather than hardcoded Seal values (1920×720).
        // SL6 cluster is 1920×800; other DiLink3 models may differ.
        val (dispWidth, dispHeight) = queryDisplaySize(displayId) ?: (1920 to 720)
        val left: Int
        val top = 0
        val right: Int
        val bottom = dispHeight
        when (slotSide) {
            ClusterSlotSide.LEFT -> {
                left = 0
                right = dispWidth * leftPercent / 100
            }
            ClusterSlotSide.RIGHT -> {
                left = dispWidth * leftPercent / 100
                right = dispWidth
            }
            null -> {
                left = 0
                right = dispWidth
            }
        }
        val resizeResult = shell.execute("am task resize $taskId $left $top $right $bottom")
        log("fitToCluster: $pkg task=$taskId bounds=[$left,$top,$right,$bottom] ok=${resizeResult.success}")
        if (!resizeResult.success && slotSide != null) {
            // Split REQUIRES per-app bounds via am task resize, which needs freeform alive.
            // wm size/overscan are display-global and cannot place two apps in two halves.
            // If rejected here, freeform is not alive → needs one-time vehicle power-cycle
            // (enable_freeform_support flag is set on projection open, read only at boot).
            log("fitToCluster: SPLIT needs freeform — am task resize rejected. " +
                "Power-cycle vehicle once after flags set. stderr=${resizeResult.stderr.take(120)}")
        }
    }

    /** Parse the physical or override display size for `am task resize` bounds.
     *  Internal so [SimpleCastCoordinator.applySplitRatioLive] can reuse the same query instead of
     *  opening a second display-size probe path. */
    internal fun queryDisplaySize(displayId: Int): Pair<Int, Int>? {
        val result = shell.execute("wm size -d $displayId")
        if (!result.success) return null
        // Output: "Physical size: 1920x720" or "Override size: 1920x720\nPhysical size: 1920x720"
        // Use override if present, else physical.
        val regex = Regex("(?:Override|Physical) size:\\s*(\\d+)x(\\d+)")
        val matches = regex.findAll(result.stdout).toList()
        val match = matches.firstOrNull { it.value.startsWith("Override") } ?: matches.firstOrNull()
            ?: return null
        val w = match.groupValues[1].toIntOrNull() ?: return null
        val h = match.groupValues[2].toIntOrNull() ?: return null
        return if (w > 0 && h > 0) w to h else null
    }

    /**
     * R6 (#1): best-effort check that [pkg] came back from the cluster as a freeform/floating WINDOW
     * on display 0 — i.e. its task sits under a stack on display 0 whose bounds do NOT cover the full
     * display. Used only to decide whether to re-issue the fullscreen reset once ([returnToMain]).
     *
     * This is a heuristic read of `am stack list`; the authoritative windowing-mode truth needs
     * `dumpsys window displays` (what the legacy [com.byd.clusternav.modules.clustercast.CastShell]
     * uses on-car). Fail-open: if the display size or stack bounds can't be parsed it returns false
     * (no retry), so it can never loop or relaunch spuriously.
     */
    private fun isWindowedOnMain(pkg: String): Boolean {
        val list = shell.execute("am stack list")
        if (!list.success) return false
        val (dispW, dispH) = queryDisplaySize(0) ?: return false
        val stackRegex = Regex("""Stack id=\d+ bounds=\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)] displayId=(\d+)""")
        var windowedStackOnMain = false
        for (line in list.stdout.lines()) {
            val m = stackRegex.find(line)
            if (m != null) {
                val left = m.groupValues[1].toInt(); val top = m.groupValues[2].toInt()
                val right = m.groupValues[3].toInt(); val bottom = m.groupValues[4].toInt()
                val disp = m.groupValues[5].toIntOrNull() ?: -1
                // A fullscreen stack on display 0 covers [0,0][dispW,dispH]; anything offset or
                // smaller (allowing a 2px rounding slop) is a freeform/floating window.
                windowedStackOnMain = disp == 0 &&
                    (left > 2 || top > 2 || (right - left) < dispW - 2 || (bottom - top) < dispH - 2)
                continue
            }
            if (windowedStackOnMain && line.contains(pkg)) return true
        }
        return false
    }

    /**
     * Resolve the launcher activity for a package.
     * Uses `cmd package resolve-activity` (same as CastPlacementCommands).
     */
    private fun resolveLauncherComponent(pkg: String): String? {
        val result = shell.execute(
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $pkg"
        )
        if (!result.success) return null
        // Output format: last line is "pkg/activity"
        return result.stdout.trim().lines().lastOrNull()
            ?.takeIf { it.contains("/") && it.contains(pkg) }
    }

    /**
     * Returns an app from the cluster back to display 0.
     *
     * @param pkg package name
     * @param activity fully qualified activity name, nullable for move-task
     * @param appType determines the return strategy
     * @param taskId required for CP/AA
     * @param mainStackId stack ID on display 0 for CP/AA
     * @return true on success
     */
    /**
     * Returns an app from the cluster back to display 0.
     *
     * Normal (R6/#1): [fullscreenReturnCommand] — LAUNCHER + `FLAG_ACTIVITY_SINGLE_TOP` +
     *   `--windowingMode 1`, re-issued once if a freeform window still lingers ([isWindowedOnMain]).
     * CP/AA: `am stack move-task <taskId> <homeStackId> true` (unchanged).
     */
    fun returnToMain(
        pkg: String,
        activity: String?,
        appType: AppType,
        taskId: Int? = null,
        mainStackId: Int? = null,
        clusterDisplayId: Int = 1,
    ): Boolean {
        return when (appType) {
            AppType.CARPLAY, AppType.ANDROID_AUTO -> {
                // Must find the CP task on the CLUSTER display (not any task on display 0)
                val cpTaskId = taskId ?: findTaskIdOnDisplay(pkg, clusterDisplayId) ?: findTaskId(pkg)
                if (cpTaskId == null) { log("CP return FAIL: cannot find taskId for $pkg"); return false }
                val homeStack = mainStackId ?: findNonHomeStackOnDisplay(0)
                if (homeStack == null) { log("CP return FAIL: cannot find non-home stack on display 0"); return false }
                val cmd = "am stack move-task $cpTaskId $homeStack true"
                log("CP return: $cmd")
                val result = shell.execute(cmd)
                log("CP return result: exit=${result.exitCode}, stdout=${result.stdout.take(200)}, stderr=${result.stderr.take(200)}")
                result.success
            }
            AppType.NORMAL -> {
                // R6 (#1): a NORMAL app is cast with `--windowingMode 5` (freeform) + `am task
                // resize`, so its task is left in freeform mode with cluster bounds. The old bare
                // `am start --display 0 --windowingMode 1 -n <comp>` did NOT reliably clear freeform
                // on an already-running task (A10, DiLink3): the app returned to display 0 but kept
                // freeform + its [0,0,W,H] cluster rect and rendered as a skewed floating WINDOW —
                // and every cast/return cycle kept it stuck (owner 2026-08: "cast VietMap back and
                // forth → window, even 'return to main' stays windowed"). Use the field-proven
                // fullscreen recipe (LAUNCHER + FLAG_ACTIVITY_SINGLE_TOP; see fullscreenReturnCommand)
                // and self-heal: if a freeform window still lingers on the main display, re-issue once.
                val component = activity ?: resolveLauncherComponent(pkg) ?: "$pkg/.MainActivity"
                val cmd = fullscreenReturnCommand(component)
                var result = shell.execute(cmd)
                if (result.success && isWindowedOnMain(pkg)) {
                    log("returnToMain: $pkg still a freeform window on display 0 → re-issuing fullscreen reset (R6/#1)")
                    result = shell.execute(cmd)
                }
                result.success
            }
        }
    }

    companion object {
        /**
         * R6 (#1, docs/specs/cast-nav-ux-release-v104.html): the field-proven verb that returns a
         * NORMAL app from the cluster to display 0 AND resets its windowing-mode to FULLSCREEN.
         *
         * A bare `am start --display 0 --windowingMode 1 -n <comp>` did not reliably clear freeform
         * on an already-running task (A10 / DiLink3), leaving the returned app as a skewed floating
         * window. Adding the LAUNCHER intent + `FLAG_ACTIVITY_SINGLE_TOP` (0x20000000) brings the
         * EXISTING task forward and reparents it into a fullscreen stack instead of adding a
         * duplicate activity — the same recipe the legacy CastShell.restoreFullscreenOnMain used
         * after the 2026-07-22 field failure ("VietMap scaled on the main screen, still scaled after
         * restart"). `--windowingMode 1` is the only shell verb that changes a running task's
         * windowing-mode on Android 10.
         */
        fun fullscreenReturnCommand(component: String): String =
            "am start --display 0 --windowingMode 1 -f 0x20000000" +
                " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n '$component'"

        /**
         * Phân loại app → [AppType]. Hằng/logic nay ở [ProjectionApps] (nguồn DUY NHẤT — Pha 3c). Đây giữ làm
         * facade "authoritative" mà SimpleCast/bridge/bubble đang gọi (giữ API ổn định), chỉ uỷ quyền phân loại.
         */
        fun classifyApp(pkg: String): AppType = when {
            ProjectionApps.isCarPlay(pkg) -> AppType.CARPLAY
            ProjectionApps.isAndroidAuto(pkg) -> AppType.ANDROID_AUTO
            else -> AppType.NORMAL
        }

        /** Launcher/home KHÔNG được chiếu (guard R2 #3). Uỷ quyền [ProjectionApps.isLauncher] (nguồn duy nhất). */
        fun isLauncher(pkg: String): Boolean = ProjectionApps.isLauncher(pkg)
    }
}
