package com.byd.clusternav.launcher.behind

import com.byd.clusternav.launcher.FreeformLaunch
import com.byd.clusternav.launcher.ShellAppLauncher
import com.byd.clusternav.modules.clustercast.StackEntry

/**
 * ═══ BEHIND-HOME — đưa app ra PHÍA SAU màn nhà, không che, không giết (quyết định thuần, `:core`) ═════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` R0 · §4.2 · §4.9 (C4). Thi hành ở `BehindHomeRunner`
 * (`:app`); lớp này chỉ ĐỌC bản `am stack list` đã qua `StackParse.parse` và dựng chuỗi lệnh. Không cờ RAM nào quyết
 * định đổi cửa sổ (CLAUDE.md §5): mọi bước đọc lại sự thật ngay trước và ngay sau lệnh.
 *
 * ## Chuỗi đúng (đã đo, máy ảo A10 01–02/10, `docs/diagnostics/behind-home-emulator-2026-10-01/`)
 *  1. B mở vào CÙNG màn ảo của ô (đường mở ô sẵn có) ⇒ B ở đỉnh màn ảo, A nằm dưới (O1, `06` bước 3).
 *  2. Đọc lại: đỉnh màn ảo KHÔNG chứa A ([checkEvict]).
 *  3. Tạo S = stack của activity giữ chỗ (khoá Bundle [AVOID_MOVE_TO_FRONT]) ⇒ stack mới nằm ĐÁY display 0 (O3).
 *  4. `am stack move-task <A> <S> true` ([moveTaskCmd]) ⇒ display 0 không có sự kiện focus nào (`06` bước 4, `10`).
 *  5. Đọc lại ([verifyMoved]) rồi gỡ activity giữ chỗ.
 *
 * ## Vì sao thứ tự "B trước, A sau" là điều kiện cứng [ĐO nguồn]
 * `TaskRecord.reparent` A10 (`TaskRecord.java:728-749`) tính `wasFront = sourceStack.isTopStackOnDisplay() && …` trên
 * display của MÀN ẢO; đúng thì stack đích bị đưa lên trước ⇒ S che màn nhà ([ĐO] `06` bước 6: `am_focused_stack
 * [0,0,68,0,moveTaskToStack]`, KachiHome stop). A12 giữ logic ở `Task.java:1134-1146`. Tham số `true/false` của
 * move-task chỉ quyết vị trí task BÊN TRONG S.
 *
 * ## Bốn câu CLAUDE.md §4 cho [moveTaskCmd] (lệnh K6)
 *  1. **Display nào** — nguồn: màn ảo ô của Kachi (`vd ≥ 1`, task A đọc lại có `displayId = vd`); đích: S trên display
 *     [MAIN_DISPLAY]. `vd < 1` ⇒ không làm gì.
 *  2. **App nào** — đúng gói A, `taskId` đọc từ bản đọc NGAY TRƯỚC; không phải chính Kachi; không phải app hệ thống;
 *     không có task trên display khác (đang chiếu cụm) — R0.6.
 *  3. **Loại stack nào** — nguồn: không `home/recents/assistant/pinned`; đích S CHẶT: `displayId=0`,
 *     `mActivityType=standard` bằng CHỮ (chuỗi trống không tính — bản dump đã lọc mất dòng `configuration` thì
 *     KHÔNG chọn), `mWindowingMode=fullscreen`, chỉ chứa task giữ chỗ của Kachi, không phải stack đỉnh, không hiện.
 *     Framework cũng từ chối đích không standard (A10 `ActivityTaskManagerService.java:2570-2573`, A12 `:2256-2259`).
 *  4. **Hoàn tác** — `am start --display <vd> -n <A>` ([bringToFrontCmd], `06` bước 5: A về màn ảo, pid giữ).
 *
 * `move-task` với `taskId` không còn là no-op exit 0 (A10 `ActivityTaskManagerService.java:2556-2560`) ⇒ luôn đọc lại.
 * Lệnh không có cờ `--display` nên cổng ownership của kênh không kiểm được đích (`WindowCommandDispatcher.kt:102-105`)
 * ⇒ bộ kiểm ở đây là rào DUY NHẤT của lệnh này.
 */
object BehindHomePlan {

    /** Đọc sự thật — cùng chuỗi mọi chỗ khác của dự án dùng. */
    const val LIST_CMD = "am stack list"

    /** Màn chính — nơi DUY NHẤT S được phép nằm. */
    const val MAIN_DISPLAY = 0

    const val STANDARD = "standard"
    const val FULLSCREEN = "fullscreen"
    private const val HOME = "home"

    /**
     * Khoá Bundle KHÔNG công bố của `ActivityOptions` (A10 `ActivityOptions.java:221` đọc ở `:954`; A12 `:265`/`:1148`;
     * `setAvoidMoveToFront()` là `@hide`, không có trong greylist A10). `ActivityStarter` A10 `:1868-1870` đặt
     * `mDoResume = false` khi có khoá ⇒ activity giữ chỗ không bao giờ chạy, stack mới nằm đáy display 0 ([ĐO] O3).
     * ROM BYD có tôn trọng không: [CHƯA BIẾT] ⇒ `BehindAnchorActivity` tự báo khi bị chạy (R0.5).
     */
    const val AVOID_MOVE_TO_FRONT = "android.activity.avoidMoveToFront"

    /** K6 — đưa task [taskId] vào CUỐI (trên cùng bên trong) stack [stackId]. */
    fun moveTaskCmd(taskId: Int, stackId: Int): String = "am stack move-task $taskId $stackId true"

    /**
     * K3/K8 — đưa task sẵn có của [component] lên đỉnh màn ảo [vd] (`"brought to front"`, không force-stop): đưa app
     * của ô lên lại (K3) hoặc kéo app đang sống sau màn nhà về ô (K8, [ĐO] `06` bước 5). `vd < 1` ⇒ ném.
     */
    fun bringToFrontCmd(vd: Int, component: String): String {
        require(vd >= 1) { "vd=$vd: lệnh đưa lên đỉnh chỉ nhắm màn ảo ô" }
        require(safeComponent(component)) { "component không hợp lệ: $component" }
        return "am start --display $vd -n '$component'"
    }

    /** K4 — mở app X (nguội) vào màn ảo ô dàn dựng; cùng bộ dựng (byte) với đường mở ô. */
    fun stageCmd(vd: Int, component: String): String {
        require(vd >= 1) { "vd=$vd: dàn dựng chỉ trên màn ảo ô" }
        require(safeComponent(component)) { "component không hợp lệ: $component" }
        return FreeformLaunch.launchOnDisplayCmd(component, vd, windowingMode = 1)
    }

    /**
     * `pkg/cls` an toàn để nằm trong cặp nháy đơn: chữ, số, `_`, `.`, `$` (lớp lồng — vd YouTube `Shell$HomeActivity`,
     * nên PHẢI có nháy đơn), đúng một `/`. Không bao giờ có `'` ⇒ không thoát được khỏi cặp nháy.
     */
    fun safeComponent(c: String): Boolean = COMPONENT.matches(c)

    private val COMPONENT = Regex("[A-Za-z][A-Za-z0-9_.]*/[A-Za-z0-9_.$]+")

    // ── Đọc bản `am stack list` ──────────────────────────────────────────────────────────────────────────────

    /**
     * Stack ĐỈNH của [display] = stack KHÔNG GHIM đầu tiên của display đó trong bản đọc: `getAllStackInfos` duyệt stack
     * của mỗi display từ trên xuống (A10 `RootActivityContainer.java:1321-1331`, `stackNdx = getChildCount() - 1 … 0`).
     *
     * Bỏ qua stack `pinned` (cửa sổ PIP): nó là lớp phủ LUÔN ở trên mọi stack khác — stack nào được đưa lên trước cũng
     * chen NGAY DƯỚI nó (A10 r47 `ActivityDisplay.java:302-322` `getTopInsertPosition`; A12 r34 `TaskDisplayArea.java:576-588`
     * `getPriority` — pinned = 2, trên mọi root task thường). Tính cả PIP thì [homeOnTop],
     * [pickAnchor], [verifyMoved] luôn thấy PIP là đỉnh và mù trước việc stack giữ chỗ / app vừa lên che màn nhà ngay
     * dưới PIP ([ĐO xe 29/09] GMaps PIP trên display 0 — fixture `camera-under-pip-derived`, dạng khối đo máy ảo).
     */
    fun topStackId(entries: List<StackEntry>, display: Int): Int? =
        entries.firstOrNull { it.displayId == display && !it.isPinned }?.stackId

    /**
     * Stack ĐANG HIỆN trên cùng của [display] (bỏ qua PIP, cùng lẽ [topStackId]) — "đang hiện", không phải "đầu danh
     * sách": sau lần BYD giết Kachi, stack rỗng của `KachiHomeActivity` có thể nằm trên cùng mà không hiện ([ĐO] fixture
     * `tm1-killed-surfaced`). MỘT phép cho [com.byd.clusternav.launcher.trip.TripPlan.homeTopVisible] và
     * [BehindMarks.surfaced] — hai bản chép là hai chỗ để quên PIP.
     */
    fun topVisibleStackId(entries: List<StackEntry>, display: Int): Int? =
        entries.firstOrNull { it.displayId == display && it.visible && !it.isPinned }?.stackId

    private fun stack(entries: List<StackEntry>, id: Int): List<StackEntry> = entries.filter { it.stackId == id }

    /**
     * Stack đỉnh của display 0 là màn nhà: loại `home` đọc bằng chữ, hoặc chứa một trong [homeComps] — màn nhà Kachi khi
     * bản đọc thiếu dòng cấu hình, HOẶC khi màn nhà là task `…KachiHomeActivity` trong stack `standard` ([ĐO máy ảo 02/10,
     * E2E `c5a-trip-generic`]: dựng bằng `am start -n` lúc `MY_PACKAGE_REPLACED`, stack `home` rỗng). Bên `:app` truyền
     * `DefaultHome.shownComponents`.
     *
     * Đỉnh ĐANG HIỆN ([topVisibleStackId]), không phải đầu danh sách: nhận dạng activity thật thì stack RỖNG của một
     * `KachiHomeActivity` đã chết (không hiện — fixture `tm1-killed-surfaced` stack 71/53, `e2e-standard-home` 208/71/53)
     * nằm trên cùng sẽ bị đọc thành "màn nhà ở đỉnh" ⇒ K12 đè lên app người dùng đang thấy.
     */
    fun homeOnTop(entries: List<StackEntry>, homeComps: Collection<String>): Boolean {
        val top = topVisibleStackId(entries, MAIN_DISPLAY) ?: return false
        return stack(entries, top).any { it.activityType == HOME || it.comp in homeComps }
    }

    // ── R0.1/R0.2 — tiền điều kiện đẩy A khỏi ô ──────────────────────────────────────────────────────────────

    /** Vì sao không đẩy. Mỗi giá trị = một dòng log `KachiBehind` + A ở lại dưới B trong màn ảo (O1). */
    enum class Why { NO_READ, BAD_VD, SELF, SYSTEM_APP, A_NOT_ON_VD, A_MULTI_TASK, A_ON_TOP, B_NOT_ON_TOP, CAST }

    sealed interface Evict {
        data class Go(val taskId: Int) : Evict
        data class Stop(val why: Why) : Evict
    }

    /**
     * Có được đẩy A khỏi màn ảo [vd] không, theo bản đọc [entries] ngay trước lệnh.
     *
     * Thứ tự kiểm là thứ tự RẺ → ĐẮT về hậu quả: đọc hỏng / vd lạ / chính mình / app hệ thống / A không ở đó / A có
     * nhiều task / A đang chiếu nơi khác / A ở ĐỈNH màn ảo (O2-sai, CẤM) / B không ở đỉnh.
     */
    fun checkEvict(
        entries: List<StackEntry>,
        vd: Int,
        a: String,
        b: String,
        selfPkg: String,
        aIsSystem: Boolean,
    ): Evict {
        if (entries.isEmpty()) return Evict.Stop(Why.NO_READ)
        if (vd < 1) return Evict.Stop(Why.BAD_VD)
        if (a == selfPkg || selfPkg.isBlank()) return Evict.Stop(Why.SELF)
        if (aIsSystem) return Evict.Stop(Why.SYSTEM_APP)
        val onVd = entries.filter { it.displayId == vd && it.pkg == a && !it.isSystemStack && !it.isPinned }
        if (onVd.isEmpty()) return Evict.Stop(Why.A_NOT_ON_VD)
        if (onVd.map { it.taskId }.distinct().size > 1) return Evict.Stop(Why.A_MULTI_TASK)
        if (entries.any { it.pkg == a && it.displayId != vd && it.displayId != MAIN_DISPLAY }) return Evict.Stop(Why.CAST)
        val top = topStackId(entries, vd) ?: return Evict.Stop(Why.NO_READ)
        val topTasks = stack(entries, top)
        if (topTasks.any { it.pkg == a }) return Evict.Stop(Why.A_ON_TOP)
        if (topTasks.none { it.pkg == b }) return Evict.Stop(Why.B_NOT_ON_TOP)
        return Evict.Go(onVd.first().taskId)
    }

    // ── O3 — chọn S ──────────────────────────────────────────────────────────────────────────────────────────

    sealed interface Anchor {
        /** S hợp lệ — dùng được làm đích. */
        data class Ok(val stackId: Int) : Anchor
        /** Chưa thấy stack giữ chỗ mới (hệ chưa dựng xong, hoặc lượt mở bị từ chối). */
        object Missing : Anchor { override fun toString() = "Missing" }
        /** Stack giữ chỗ ở ĐỈNH hoặc đang HIỆN ⇒ ROM bỏ qua [AVOID_MOVE_TO_FRONT] — tắt tính năng (R0.5a). */
        data class InFront(val stackId: Int) : Anchor
        /** Có stack giữ chỗ mới nhưng không qua bộ lọc chặt (loại không đọc được bằng chữ, không toàn màn…). */
        data class NotStrict(val stackId: Int) : Anchor
    }

    /**
     * Stack giữ chỗ VỪA tạo: có trong [after] mà không có trong [before], nằm trên display 0, MỌI task của nó là
     * [anchorComp]. Nhiều ứng viên (không nên xảy ra) ⇒ lấy id lớn nhất (id stack tăng dần).
     */
    fun pickAnchor(before: List<StackEntry>, after: List<StackEntry>, anchorComp: String): Anchor {
        val old = before.map { it.stackId }.toSet()
        val id = after.filter { it.displayId == MAIN_DISPLAY && it.stackId !in old }
            .map { it.stackId }.distinct()
            .filter { s -> stack(after, s).let { t -> t.isNotEmpty() && t.all { it.comp == anchorComp } } }
            .maxOrNull() ?: return Anchor.Missing
        val tasks = stack(after, id)
        if (topStackId(after, MAIN_DISPLAY) == id || tasks.any { it.visible }) return Anchor.InFront(id)
        val strict = tasks.all { it.activityType == STANDARD && it.mode == FULLSCREEN && !it.isPinned }
        return if (strict) Anchor.Ok(id) else Anchor.NotStrict(id)
    }

    /** Stack nào đang chứa task giữ chỗ (mọi display) — CHỈ test dùng (khoá "không mồ côi" trên fixture T-M1). */
    fun anchorStacks(entries: List<StackEntry>, anchorComp: String): List<Int> =
        entries.filter { it.comp == anchorComp }.map { it.stackId }.distinct()

    // ── Đọc lại sau move-task ────────────────────────────────────────────────────────────────────────────────

    enum class Moved { OK, NOT_MOVED, FRONT_CHANGED, S_VISIBLE }

    /**
     * Sau `move-task`: task [taskId] phải ở stack [stackId] trên display 0, S không hiện, và stack đỉnh display 0 vẫn
     * là [topBefore] (đọc ngay trước lệnh). [FRONT_CHANGED]/[S_VISIBLE] ⇒ bên thi hành đưa HOME lên lại bằng rào
     * camera (K12) nếu trước đó HOME ở đỉnh.
     */
    fun verifyMoved(after: List<StackEntry>, taskId: Int, stackId: Int, topBefore: Int?): Moved {
        val t = after.firstOrNull { it.taskId == taskId } ?: return Moved.NOT_MOVED
        if (t.stackId != stackId || t.displayId != MAIN_DISPLAY) return Moved.NOT_MOVED
        if (topStackId(after, MAIN_DISPLAY) != topBefore) return Moved.FRONT_CHANGED
        if (stack(after, stackId).any { it.visible }) return Moved.S_VISIBLE
        return Moved.OK
    }

    // ── R0.3 — chỗ dàn dựng ──────────────────────────────────────────────────────────────────────────────────

    /** Một ô app ứng viên làm chỗ dàn dựng: màn ảo [vd], app [pkg], diện tích [area] px², đã thấy sống [alive]. */
    data class Stage(val slot: Int, val vd: Int, val pkg: String, val area: Long, val alive: Boolean) {
        /** Màn ảo ẨN (L4 · D2) — không phải ô: id màn ảo chỉ biết lúc tạo, không có app C nào trên đó. */
        val hidden: Boolean get() = slot == HIDDEN_SLOT

        companion object {
            private const val HIDDEN_SLOT = -1
            val HIDDEN = Stage(HIDDEN_SLOT, -1, "", 0L, alive = true)
        }
    }

    /** Ô dàn dựng cho X: ô sống, có màn ảo, app ≠ X, diện tích NHỎ nhất (bằng nhau ⇒ ô chỉ số nhỏ). Không có ⇒ `null`. */
    fun stagingSlot(cands: List<Stage>, x: String): Stage? =
        cands.filter { it.alive && it.vd >= 1 && it.pkg.isNotBlank() && it.pkg != x }
            .minWithOrNull(compareBy<Stage>({ it.area }, { it.slot }))

    /**
     * R0.3 — X KHÔNG ở lại màn ảo dàn dựng mà đang hiện TRƯỚC màn nhà: stack đang hiện trên cùng của display 0 (bỏ PIP,
     * [topVisibleStackId]) là stack `standard` bằng chữ chứa X. Đọc hỏng ⇒ `false` (không biết thì không đổi gì).
     */
    fun fellFront(entries: List<StackEntry>, x: String): Boolean {
        val top = topVisibleStackId(entries, MAIN_DISPLAY) ?: return false
        return stack(entries, top).any { it.pkg == x && it.activityType == STANDARD }
    }

    /**
     * Các task của [pkgs] đang nằm trên display 0 (stack `standard` bằng chữ, không PIP) — thứ K12 SẮP đẩy ra sau màn nhà
     * mà chưa chắc có dấu. Review lượt 3 + [ĐO máy ảo 02/10 `behind-home-…/finish/esc`]: X tự lên trước màn nhà (R0.3) hoặc
     * B thoát khỏi ô ra display 0 (Waze, `launchToSide`) ⇒ K12 đưa màn nhà lên lại, task đó nằm SAU màn nhà mà không mang
     * dấu ⇒ Kachi chết thì nó nổi lên và lượt trả lại ([BehindMarks.surfaced]) không nhận ra ⇒ bên thi hành ghi dấu TRƯỚC K12.
     */
    fun mainTasksOf(entries: List<StackEntry>, pkgs: Set<String>): List<StackEntry> =
        entries.filter { it.displayId == MAIN_DISPLAY && it.pkg in pkgs && it.activityType == STANDARD && !it.isPinned }
            .distinctBy { it.taskId }

    /** X đã lên ĐỈNH màn ảo [vd] chưa (bước chờ của R0.3 trước khi đưa app ô lên lại). */
    fun topIs(entries: List<StackEntry>, vd: Int, pkg: String): Boolean {
        val top = topStackId(entries, vd) ?: return false
        return stack(entries, top).any { it.pkg == pkg }
    }

    // ── L4 · D4 — "đang chạy" = có TASK **và** có TIẾN TRÌNH ─────────────────────────────────────────────────

    /** K11 — đọc pid của [pkg] (chỉ đọc, không đổi gì). Tên gói đi vào lệnh shell ⇒ lọc bằng CÙNG regex đường mở app. */
    fun pidCmd(pkg: String): String {
        require(pkg.matches(ShellAppLauncher.PKG)) { "tên gói lạ: $pkg" }
        return "pidof $pkg"
    }

    /**
     * L4 · D4 — X ĐANG CHẠY thật: có task trong [entries] VÀ [pidOut] (kết quả [pidCmd]) có ít nhất một pid.
     *
     * Vì sao cần cả hai [ĐO máy ảo 03/10]:
     *  - task KHÔNG tiến trình (`e2e/e2b-bg-ytmusic-dead-proc`): app bị giết mà task còn (BYD giết tiến trình lúc tắt máy)
     *    ⇒ bản cũ chỉ nhìn task coi là "đang chạy" ⇒ 0 lệnh, chuyến ghi đã chạy mà app không chạy. Nay coi là NGUỘI: K4
     *    (`am start --display <vd> -n X`) tìm thấy task cũ trên display 0 và KÉO nó vào màn ảo (`reparentToDisplay`, tiêu
     *    điểm chỉ trên màn ảo — [ĐO `e2e-L4 · m1-stale-task-k4` (bằng chứng phiên, ngoài repo)]; nguồn A10 r47 `ActivityStarter.java:2096-2170`:
     *    `mPreferredDisplayId != mTargetStack.mDisplayId` ⇒ `reparent(launchStack, ON_TOP, REPARENT_MOVE_STACK_TO_FRONT)`).
     *  - tiến trình KHÔNG task (widget bật tiến trình bằng broadcast, `e2e/r2-alias-trip`) ⇒ vẫn nguội (luật Pass 6 giữ).
     */
    fun running(entries: List<StackEntry>, pkg: String, pidOut: String): Boolean =
        entries.any { it.pkg == pkg } && pidOut.trim().split(Regex("\\s+")).any { (it.toIntOrNull() ?: 0) > 0 }

    /**
     * L4 · D2 — chọn chỗ dàn dựng cho X: một ô SỐNG nếu có ([stagingSlot], đường đã đo — LUÔN đứng trước, CLAUDE.md §6),
     * không thì màn ảo ẨN của Kachi ([Stage.HIDDEN] — đường mới, đứng CUỐI). Bố cục chỉ có widget không còn ra `NO_STAGE`.
     */
    fun stageFor(cands: List<Stage>, x: String): Stage = stagingSlot(cands, x) ?: Stage.HIDDEN

    // ── R1.8 — app đang sống sau màn nhà ─────────────────────────────────────────────────────────────────────

    /**
     * Task của [pkg] đang nằm SAU màn nhà theo nghĩa chặt: display 0, stack không hiện, standard bằng chữ, toàn màn,
     * stack chỉ chứa task của [pkg] (đúng hình dạng S sau khi gỡ giữ chỗ). Không phải stack đỉnh. `null` = không có.
     * CHỈ test dùng (khoá hình S trên fixture T-M1); đường R1.8 thật chọn K8 bằng [SlotReturn.markedBehind] (đòi thêm dấu bền).
     */
    fun behindTaskOf(entries: List<StackEntry>, pkg: String): StackEntry? {
        val top = topStackId(entries, MAIN_DISPLAY)
        return entries.firstOrNull { e ->
            e.pkg == pkg && e.displayId == MAIN_DISPLAY && e.stackId != top && !e.visible &&
                stack(entries, e.stackId).all {
                    it.pkg == pkg && it.activityType == STANDARD && it.mode == FULLSCREEN && !it.isPinned
                }
        }
    }
}
