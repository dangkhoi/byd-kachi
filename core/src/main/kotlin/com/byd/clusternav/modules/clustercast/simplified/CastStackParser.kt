package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.modules.clustercast.StackParse

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
                val taskMatch = TASK_PKG.find(line)
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
     *
     * ⚠ V-CLUSTER senior review (2.84, 2026-09-30) — CLAUDE.md §4 câu 3 (*loại stack nào?*) phải trả lời bằng LOẠI STACK,
     * không bằng tên gói. Bộ lọc gói ở dưới chỉ chặn `com.android.*`/launcher3/systemui/CarPlay; một task nằm trong stack
     * `home`/`recents`/`assistant` mang gói khác (home phụ của một launcher bên thứ ba, nếu VD từng được cấp system
     * decorations — [ĐO AOSP 10 r47] `DisplayContent.java:5023-5029`) sẽ lọt qua và bị `am stack move-task` bê sang
     * display 0. Nay loại mọi task của stack KHÔNG standard ([nonStandardStackTasks]) — chúng không cần ai bê: gỡ VD là
     * framework tự `finishAllActivities` stack không standard ([ĐO AOSP] `ActivityDisplay.java:1137-1139`).
     *
     * Task trong stack `pinned` (loại vẫn là standard) GIỮ hành vi cũ — bị bê ở mức TASK vào một stack standard
     * fullscreen: đó là đường AN TOÀN hơn để lại, vì gỡ VD thì framework bê cả STACK pinned sang display 0
     * (`ActivityDisplay.java:1147`), và `addStackReferenceIfNeeded` NÉM nếu display 0 đã có stack pinned
     * (`ActivityDisplay.java:710-715`) — đúng lớp lỗi "stack mồ côi" của vụ đơ Dudu (CLAUDE.md §4).
     */
    fun tasksToClean(amOutput: String, displayId: Int): List<ParsedTask> {
        val forbidden = nonStandardStackTasks(amOutput, displayId)
        return parseTasks(amOutput).filter { task ->
            task.displayId == displayId &&
                task.taskId !in forbidden &&
                !task.pkg.startsWith("com.android.") &&
                task.pkg !in ProjectionApps.STACK_SKIP_PKGS &&
                // Keep ONLY the black placeholder; every other ClusterNav activity on the cluster is
                // a leak and must be evicted (bug b). Non-ClusterNav apps are unaffected.
                !task.component.contains(CLUSTER_PLACEHOLDER_ACTIVITY)
        }
    }

    /**
     * Stack id của stack ĐANG chứa task của [pkg] trên [displayId] (khớp gói CHÍNH XÁC, có escape).
     * Null nếu không có. Dùng cho đường R2 move-stack (X2): khi `am start --display <VD>` bị
     * SafeActivityOptions Permission Denial (VD của uid khác) HOẶC bị ActivityStarter âm thầm nhắm lại
     * display 0, app nằm lại display 0 — cần biết stack id của nó để `am display move-stack <stackId> <VD>`
     * (đường proven trong `ClusterCast.placeLadder` R2, bypass ActivityStarter/checkPermissions).
     *
     * Ghép theo Stack header (`Stack id=<S> ... displayId=<D>`) rồi kiểm task-line trong block đó khớp [pkg].
     */
    fun findStackIdForPkg(amOutput: String, pkg: String, displayId: Int): Int? {
        val escapedPkg = Regex.escape(pkg)
        val taskRegex = Regex("""taskId=\d+:\s*$escapedPkg/""")
        var currentStackId = -1
        var currentDisplayId = -1
        for (line in amOutput.lines()) {
            val sm = STACK_HEADER_WITH_ID.find(line)
            if (sm != null) {
                currentStackId = sm.groupValues[1].toIntOrNull() ?: -1
                currentDisplayId = sm.groupValues[2].toIntOrNull() ?: -1
                continue
            }
            if (currentDisplayId == displayId && currentStackId >= 0 && taskRegex.containsMatchIn(line)) {
                return currentStackId
            }
        }
        return null
    }

    /**
     * Find a usable target stack on display 0 (non-home, id > 0).
     *
     * V-CLUSTER senior review (2026-09-30): "non-home" trước đây chỉ là giả định *home = stack 0* ([ĐO] đúng ở dump xe
     * `docs/diagnostics/carlog-kachi-20260914-2044/10-am-stack-list.txt`). Nay bỏ qua TƯỜNG MINH stack không standard
     * (home/recents — `am stack move-task` vào đó thì ATMS NÉM, [ĐO AOSP 10 r47] `ActivityTaskManagerService.java:2570-2573`)
     * và stack `pinned` (bê task vào đó là biến app vừa dọn khỏi cụm thành cửa sổ PiP trên màn chính). Thứ tự dòng tiêu đề
     * giữ nguyên ⇒ dump thường (stack standard đầu tiên id > 0) ra đúng số như cũ.
     */
    fun findTargetStackOnDisplay0(amOutput: String): Int? {
        val unfit = StackParse.parse(amOutput)
            .filter { it.displayId == 0 && (!it.isStandard || it.isPinned) }
            .mapTo(HashSet()) { it.stackId }
        for (line in amOutput.lines()) {
            val m = Regex("""Stack id=(\d+).*displayId=0""").find(line) ?: continue
            val stackId = m.groupValues[1].toIntOrNull() ?: continue
            if (stackId > 0 && stackId !in unfit) return stackId
        }
        return null
    }

    /**
     * Task của stack KHÔNG standard (home · recents · assistant) trên [displayId] — vùng cấm của lượt dọn (CLAUDE.md §4
     * câu 3). Phân loại dùng CHUNG [StackParse] (bài học đơ Dudu, `StackEntry.isStandard`) — không viết parser thứ hai.
     * Dòng `configuration=` vắng ⇒ loại trống ⇒ coi là standard, đúng như `StackEntry.isStandard` (giữ hành vi cũ).
     */
    private fun nonStandardStackTasks(amOutput: String, displayId: Int): Set<Int> =
        StackParse.parse(amOutput)
            .filter { it.displayId == displayId && !it.isStandard }
            .mapTo(HashSet()) { it.taskId }

    /**
     * B1b — khung THẬT của task [pkg] trên [displayId], đọc từ `bounds=[l,t][r,b]` của DÒNG TASK (không phải dòng stack) —
     * cách đọc lại sau `am task resize` ở cụm Chữ nhật. Định dạng [ĐO dump xe] `carlog-kachi-20260914-2044/10-am-stack-list.txt`
     * (`taskId=10: vn.vietmap.live/… bounds=[0,0][1920,1080] … visible=false`) và [ĐO máy ảo 14/09] task freeform sau resize in
     * đúng từng px (`waze-into-slot-research-2026-09-14.md` §3: `taskId=795 … bounds=[100,200][900,800]`). Khớp gói CHÍNH XÁC
     * (không tiền tố); bỏ task trong stack `pinned`. `null` = không thấy task trên display đó, hoặc dòng task không có bounds.
     */
    fun taskBoundsOn(amOutput: String, pkg: String, displayId: Int): CastBounds? {
        var currentDisplayId = -1
        var pinned = false
        for (line in amOutput.lines()) {
            val sm = STACK_HEADER.find(line)
            if (sm != null) { currentDisplayId = sm.groupValues[1].toIntOrNull() ?: -1; pinned = false; continue }
            if (line.contains("mWindowingMode=pinned")) { pinned = true; continue }
            if (currentDisplayId != displayId || pinned) continue
            val tm = TASK_LINE.find(line) ?: continue
            if (tm.groupValues[2].substringBefore("/") != pkg) continue
            val b = TASK_BOUNDS.find(line) ?: return null
            val (l, t, r, bt) = (1..4).map { b.groupValues[it].toInt() }
            return CastBounds(l, t, r, bt)
        }
        return null
    }

    /** `bounds=[l,t][r,b]` trên dòng task (sau `taskId=N: comp`). */
    private val TASK_BOUNDS = Regex("""taskId=\d+:\s*\S+.*?bounds=\[(-?\d{1,5}),(-?\d{1,5})]\[(-?\d{1,5}),(-?\d{1,5})]""")

    private val STACK_HEADER = Regex("""Stack id=\d+.*displayId=(\d+)""")
    /** Như [STACK_HEADER] nhưng CAPTURE cả stack id (group 1) lẫn display id (group 2). */
    private val STACK_HEADER_WITH_ID = Regex("""Stack id=(\d+).*displayId=(\d+)""")
    private val TASK_LINE = Regex("""taskId=(\d+):\s*(\S+)""")

    /** Biên dịch một lần — [foreground] quét từng dòng. */
    private val TASK_PKG = Regex("""taskId=\d+:\s*([^/\s]+)/""")
}
