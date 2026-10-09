package com.byd.clusternav.launcher.escape

import com.byd.clusternav.launcher.HomeActivityCmd
import com.byd.clusternav.launcher.ShellAppLauncher
import com.byd.clusternav.launcher.behind.BehindHomePlan
import com.byd.clusternav.launcher.camera.CameraGuard
import com.byd.clusternav.modules.clustercast.StackEntry
import com.byd.clusternav.modules.clustercast.StackParse
import kotlin.math.ceil

/**
 * ═══ 2.98 · R7 (SLOT-ESCAPE-POLICY) — QUYẾT ĐỊNH đưa app thoát ô về đúng khung ô bằng freeform (thuần, `:core`) ═══════════
 *
 * Đầu vào là SỰ THẬT của hệ (`am stack list` đã qua [StackParse.parse]) + dấu bền ([EscapeMarker]) + nội dung ô đang hiện.
 * Không đọc cờ RAM nào (CLAUDE.md §5). Thi hành ở `:app` (`SlotEscape`). Cơ chế [ĐO xe 09/10] — evidence
 * `docs/diagnostics/oncar-freeform-waze-2026-10-09.md` §2.
 *
 * ## Bốn câu CLAUDE.md §4 cho mọi lệnh ở đây
 *  1. **Display nào** — chỉ task có `displayId=0` trong bản đọc của CHÍNH lượt đó; đưa lại lên = `am task focus <id>` của task đó
 *     (đường lùi `am start --display 0`).
 *  2. **App nào** — đúng gói ô đang hiện ([checkAdopt] `shownPkg == pkg`), task DUY NHẤT của gói trên display 0, stack chỉ có
 *     task của gói đó (allow-list, không "mọi thứ trừ…").
 *  3. **Loại stack nào** — `mActivityType=standard` bằng CHỮ (như `FloatingOrphanPlan`), chế độ fullscreen/freeform; pinned,
 *     split, home/recents bị loại.
 *  4. **Hoàn tác** — mã 89 mode 1 ([releaseCmds]): trả toàn màn, xoá luôn chế độ freeform Android nhớ theo app; KHÔNG BAO GIỜ
 *     `am stack remove`/`removeTask` ([ĐO máy ảo] giết tiến trình app).
 */
object SlotEscapePlan {

    const val MAIN_DISPLAY = 0
    const val STANDARD = "standard"
    const val LIST_CMD = "am stack list"

    /** [ĐO máy ảo 09/10] cỡ tối thiểu của task freeform 220dp — ô nhỏ hơn thì hệ kẹp khung, đọc lại sẽ không khớp. */
    const val MIN_DP = 220

    /** Vì sao không nhận. Mỗi giá trị = một dòng nhật ký `KachiEscape`; ô đi đường 2.93 (luật hoàn ô). */
    enum class Why { BAD_PKG, NO_READ, NOT_SHOWN, SMALL_RECT, NO_TASK, MULTI_TASK, NOT_STANDARD, MIXED_STACK, MODE }

    sealed interface Adopt {
        /** Nhận [task]; [toTop] = đưa lên trên cùng (app đang ở đỉnh hoặc màn nhà Kachi đang ở đỉnh). */
        data class Go(val task: StackEntry, val toTop: Boolean) : Adopt
        data class Stop(val why: Why) : Adopt
    }

    fun minPx(densityDpi: Int): Int = ceil(MIN_DP * densityDpi / 160.0).toInt()

    /** Task (không pinned) của [pkg] trên display 0, mỗi task một mục. */
    private fun onMain(entries: List<StackEntry>, pkg: String): List<StackEntry> =
        entries.filter { it.displayId == MAIN_DISPLAY && it.pkg == pkg && !it.isPinned }.distinctBy { it.taskId }

    /**
     * Có nhận app [pkg] vừa thoát ô không, theo bản đọc [entries] ngay trước lệnh. Thứ tự kiểm: rẻ → đắt về hậu quả.
     * [shownPkg] = gói ô đang hiện (lớp HIỆN của state) · [rect] = khung ô trên display 0 · [homeComps] = các dạng in của màn nhà
     * Kachi (`DefaultHome.shownComponents`).
     */
    fun checkAdopt(
        entries: List<StackEntry>,
        pkg: String,
        shownPkg: String?,
        rect: PxRect,
        densityDpi: Int,
        homeComps: Collection<String>,
    ): Adopt {
        if (!pkg.matches(ShellAppLauncher.PKG)) return Adopt.Stop(Why.BAD_PKG)
        if (entries.isEmpty()) return Adopt.Stop(Why.NO_READ)
        if (shownPkg != pkg) return Adopt.Stop(Why.NOT_SHOWN)
        val min = minPx(densityDpi)
        if (rect.width < min || rect.height < min) return Adopt.Stop(Why.SMALL_RECT)
        val mine = onMain(entries, pkg)
        if (mine.isEmpty()) return Adopt.Stop(Why.NO_TASK)
        if (mine.size > 1) return Adopt.Stop(Why.MULTI_TASK)
        val t = mine.single()
        if (t.activityType != STANDARD) return Adopt.Stop(Why.NOT_STANDARD)
        if (entries.any { it.stackId == t.stackId && it.pkg != pkg }) return Adopt.Stop(Why.MIXED_STACK)
        if (t.mode != MODE_FULLSCREEN && t.mode != MODE_FREEFORM) return Adopt.Stop(Why.MODE)
        val toTop = BehindHomePlan.topVisibleStackId(entries, MAIN_DISPLAY) == t.stackId || BehindHomePlan.homeOnTop(entries, homeComps)
        return Adopt.Go(t, toTop)
    }

    /** Hai lệnh nhận: freeform (mã 89 mode 5) rồi kéo đúng khung ô. */
    fun adoptCmds(codes: TaskBinderCodes, task: Int, toTop: Boolean, rect: PxRect): List<String> =
        listOf(codes.modeCmd(task, TaskBinderCodes.MODE_FREEFORM, toTop), resizeCmd(task, rect))

    fun resizeCmd(task: Int, rect: PxRect): String = "am task resize $task ${rect.args()}"

    /**
     * Khung TASK (không phải stack) trên dòng `taskId=N: … bounds=[…][…]`. [ĐO máy ảo 09/10] stack freeform giữ khung
     * `[0,0][1920,1080]` ở dòng tiêu đề, khung thật nằm trên dòng task ⇒ không dùng `StackEntry.bounds`.
     */
    fun taskBounds(out: String, task: Int): PxRect? {
        val re = Regex("^\\s*taskId=$task:\\s*\\S+\\s+bounds=(\\[-?\\d+,-?\\d+\\]\\[-?\\d+,-?\\d+\\])")
        return out.lineSequence().firstNotNullOfOrNull { re.find(it) }?.let { PxRect.parse(it.groupValues[1]) }
    }

    /** Đọc lại sau lệnh: task [task] ở display 0, freeform, đúng khung [rect]. */
    fun verify(out: String, task: Int, rect: PxRect): Boolean {
        val e = StackParse.parse(out).firstOrNull { it.taskId == task } ?: return false
        return e.displayId == MAIN_DISPLAY && e.isFreeform && taskBounds(out, task) == rect
    }

    /**
     * Đưa task [task] đang được quản lại lên trên màn nhà — R10 (owner 09/10 "làm hết"): `am task focus <id>` thay `am start
     * MAIN/LAUNCHER`. [ĐO máy ảo 09/10, evidence `emu-slot-escape-app-sweep-2026-10-09.md` §2] `am start MAIN/LAUNCHER -n <root>`
     * lên task có intent gốc KHÁC MAIN/LAUNCHER (task do chính app tự mở) ⇒ `am_create_activity` root MỚI mỗi lần (Messages: Hist
     * 2 → 8 sau 3 HOME; AOSP Q `ActivityStarter.setTaskFromIntentActivity` `!isSameIntentFilter`), VietMap singleTask nhận
     * `onNewIntent` thừa mỗi HOME; `am task focus` chỉ `am_resume_activity`, giữ khung, cùng pid (6/6 app). Id lấy từ CHÍNH bản đọc
     * của lượt đối chiếu ([Step.Keep] — không phải id cũ trong dấu). [ĐO máy ảo] id không tồn tại ⇒ in `Setting focus to task N`,
     * exit 0, không làm gì (`runTaskFocus` → `setFocusedTask` trả im khi `anyTaskForId == null`) — task mất giữa hai lệnh thì lượt
     * đối chiếu kế thấy [Step.Gone]. Rào [CameraGuard.onHomeUnlessCamera]: chỉ chạy khi màn nhà Kachi đang hiện trên display 0 và
     * không có màn camera.
     */
    fun focusCmd(task: Int, cameraSig: String?, homeComps: List<String>): String? {
        if (task <= 0 || homeComps.isEmpty()) return null
        return runCatching { CameraGuard.onHomeUnlessCamera(cameraSig, homeComps, "am task focus $task") }.getOrNull()
    }

    /** Kết quả [focusCmd]: chạy và in dòng xác nhận · rào không cho chạy (đầu ra rỗng) · lệnh hỏng (ROM cắt lệnh / ngoại lệ). */
    enum class Focus { OK, SKIPPED, FAILED }

    /** [ĐO máy ảo A10] dòng `runTaskFocus` in TRƯỚC `setFocusedTask` (`ActivityManagerShellCommand.java:2794` theo stack máy ảo). */
    const val FOCUS_OK = "Setting focus to task "

    fun focusResult(out: String?): Focus = when {
        out == null -> Focus.FAILED
        out.isBlank() -> Focus.SKIPPED
        FOCUS_OK in out && "Exception" !in out && "Error" !in out -> Focus.OK
        else -> Focus.FAILED
    }

    /** Đọc intent gốc của task khi [focusCmd] hỏng — chỉ đọc, chỉ chạy ở đường lùi (không phải mỗi HOME). */
    const val RECENTS_CMD = "dumpsys activity recents"

    /**
     * Intent GỐC của task [task] là MAIN/LAUNCHER không, từ nguyên văn `dumpsys activity recents` (A10: dòng `* Recent #N:
     * TaskRecord{… #<id> …}` rồi `intent={act=… cat=[…] …}` trong khối của nó). Không thấy task / không thấy dòng intent ⇒ `false`
     * (đường lùi KHÔNG bắn `am start` — chồng instance tệ hơn không đưa lên).
     */
    fun rootIsLauncher(recents: String, task: Int): Boolean {
        val head = Regex("TaskRecord\\{\\S+ #$task\\s")
        var inTask = false
        for (line in recents.lineSequence()) {
            if ("TaskRecord{" in line && line.trimStart().startsWith("*")) inTask = head.containsMatchIn(line)
            else if (inTask && line.trimStart().startsWith("intent={")) {
                return "act=android.intent.action.MAIN" in line && "cat=[android.intent.category.LAUNCHER" in line
            }
        }
        return false
    }

    /**
     * Đường LÙI của [focusCmd] (chỉ khi focus hỏng VÀ [rootIsLauncher]): `am start --display 0 MAIN/LAUNCHER -n <comp>` — [ĐO xe
     * 09/10] §2 mục 6 đúng khung, cùng pid khi intent gốc là MAIN/LAUNCHER (hệ đưa task cũ lên, không dựng activity mới). Component
     * lạ (có `$`, khoảng trắng…) ⇒ `null`, không lệnh.
     */
    fun launcherFrontCmd(comp: String, cameraSig: String?, homeComps: List<String>): String? {
        if (!comp.matches(SAFE_COMP) || homeComps.isEmpty()) return null
        val start = "am start --display $MAIN_DISPLAY -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $comp"
        return runCatching { CameraGuard.onHomeUnlessCamera(cameraSig, homeComps, start) }.getOrNull()
    }

    /**
     * Trả task về toàn màn (thôi quản). [homeFirst] (task freeform đang ở ĐỈNH display 0, tức nổi trên màn nhà) ⇒ Home trước
     * (qua rào camera) rồi mới mode 1 với toTop 0 — [ĐO máy ảo 09/10] app nằm DƯỚI Kachi, cùng pid; mode 1 trước thì app toàn
     * màn đè lên màn nhà đúng lúc người dùng vừa gỡ nó khỏi ô.
     */
    fun releaseCmds(codes: TaskBinderCodes, task: Int, homeFirst: Boolean, cameraSig: String?, homeComps: List<String>): List<String> {
        val home = if (homeFirst) homeCmd(cameraSig, homeComps) else null
        return listOfNotNull(home, codes.modeCmd(task, TaskBinderCodes.MODE_FULLSCREEN, toTop = false))
    }

    /**
     * Home qua rào camera (chỉ khi màn nhà Kachi đang hiện, không có màn camera) — đẩy cửa sổ freeform xuống DƯỚI màn nhà. Dùng
     * khi trả ([releaseCmds]) và khi một bảng Kachi (Cài đặt · sửa bố cục) mở. [ĐO máy ảo 09/10] Home ⇒ task freeform
     * `visible=false`, bảng Cài đặt hiện đủ; còn `ActivityManager.moveTaskToFront` (task Kachi) để app freeform VẪN `RESUMED`,
     * cửa sổ vẫn vẽ đè lên bảng ⇒ không dùng.
     */
    fun homeCmd(cameraSig: String?, homeComps: List<String>): String? =
        if (homeComps.isEmpty()) null
        else runCatching { CameraGuard.onHomeUnlessCamera(cameraSig, homeComps, HomeActivityCmd.GO_HOME) }.getOrNull()

    /** Một bước của lượt đối chiếu dấu ↔ sự thật ([reconcile]). */
    sealed interface Step {
        val marker: EscapeMarker

        /** Task sống, freeform, ô còn hiện gói ⇒ giữ (dấu cập nhật task id); [onTop] = đang ở đỉnh display 0. */
        data class Keep(override val marker: EscapeMarker, val task: StackEntry, val onTop: Boolean) : Step

        /** Ô không còn hiện gói ⇒ trả toàn màn ([releaseCmds]) rồi xoá dấu. */
        data class Release(override val marker: EscapeMarker, val task: StackEntry, val homeFirst: Boolean) : Step

        /** Task đã mất mà ô còn hiện gói ⇒ ô đi luật hoàn ô như app vừa đóng; dấu GIỮ (Android còn nhớ freeform theo app). */
        data class Gone(override val marker: EscapeMarker) : Step

        /** Task đã mất, ô không hiện gói ⇒ giữ dấu (chờ app hiện lại để trả toàn màn); không lệnh. */
        data class Dormant(override val marker: EscapeMarker) : Step

        /** Gói đã gỡ ⇒ xoá dấu, không lệnh. */
        data class Forget(override val marker: EscapeMarker) : Step

        /** Chưa quyết được (nhiều task của gói / ô hiện gói mà task không phải freeform) ⇒ không lệnh, giữ dấu. */
        data class Wait(override val marker: EscapeMarker) : Step
    }

    /**
     * Đối chiếu từng dấu với bản đọc [entries]. [shownAt] = gói ô [slot] đang hiện (`null` = ô không hiện app nào / không có ô);
     * [installed] = gói còn cài. Bản đọc rỗng ⇒ rỗng (đọc hỏng là không quyết gì).
     */
    fun reconcile(
        markers: List<EscapeMarker>,
        entries: List<StackEntry>,
        shownAt: (Int) -> String?,
        installed: (String) -> Boolean,
    ): List<Step> {
        if (entries.isEmpty()) return emptyList()
        val top = BehindHomePlan.topVisibleStackId(entries, MAIN_DISPLAY)
        return markers.map { m ->
            val mine = onMain(entries, m.pkg).filter { it.activityType == STANDARD }
            val t = mine.firstOrNull { it.taskId == m.task } ?: mine.singleOrNull()
            val shown = shownAt(m.slot) == m.pkg
            when {
                t == null && mine.size > 1 -> Step.Wait(m)
                t == null && !installed(m.pkg) -> Step.Forget(m)
                t == null -> if (shown) Step.Gone(m) else Step.Dormant(m)
                !shown -> Step.Release(m, t, homeFirst = t.isFreeform && top == t.stackId)
                t.isFreeform -> Step.Keep(m.copy(task = t.taskId), t, onTop = top == t.stackId)
                else -> Step.Wait(m)
            }
        }
    }

    /** Task freeform đang sống của gói [pkg] mà dấu nói thuộc ô [slot] — host của ô đó KHÔNG mở lại app vào màn ảo. */
    fun claims(markers: List<EscapeMarker>, entries: List<StackEntry>, slot: Int, pkg: String): StackEntry? {
        val m = markers.firstOrNull { it.pkg == pkg && it.slot == slot } ?: return null
        val mine = onMain(entries, pkg).filter { it.activityType == STANDARD && it.isFreeform }
        return mine.firstOrNull { it.taskId == m.task } ?: mine.singleOrNull()
    }

    private const val MODE_FULLSCREEN = "fullscreen"
    private const val MODE_FREEFORM = "freeform"

    /** Component an toàn trong chuỗi `case` của [CameraGuard] (không `$`, không khoảng trắng, không nháy). */
    private val SAFE_COMP = Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.]+")
}
