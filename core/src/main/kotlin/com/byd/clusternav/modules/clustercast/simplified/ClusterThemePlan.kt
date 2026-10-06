package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.modules.clustercast.StackEntry

/**
 * ═══ CLUSTER-THEME-SAFE (2.89, P0) — QUYẾT ĐỊNH gửi hay không một opcode đổi theme cụm (thuần, không chạy lệnh) ══════════
 *
 * ## Vì sao
 * [ĐO xe 05/10, Seal DL3.0 fw 2602030, `docs/diagnostics/oncar-2026-10-05-slot-cluster.md` §4] gửi opcode theme (30/31) qua
 * `AutoContainer` khi màn ảo chiếu cụm còn lớp Android (Maps trên display 8) ⇒ SurfaceFlinger chết
 * (`Failed HIDL return status not checked: DEAD_OBJECT`, `HWComposer::getActiveConfig` từ `Layer::Handle` huỷ) ⇒
 * `system_server` khởi động lại (15:39:17, màn chính dựng lại) — lần thứ hai sau 11/08. Kachi gửi `30` MỖI lần mở chiếu,
 * và [ĐO] `ClusterBlackActivity` của chính Kachi nằm lại trên màn ảo sau khi tắt chiếu ⇒ lần mở sau gửi theme đè lên lớp.
 * Cơ chế [SUY mạnh]: theme đổi ⇒ container dựng lại màn ảo (31 ⇒ display mới 8 [ĐO]) ⇒ lớp của app trên màn ảo cũ bị huỷ
 * khi HWC của nó đã chết. Lớp KHÔNG phải task (cửa sổ phủ) [SUY] chịu cùng rủi ro ⇒ đọc cả `dumpsys window windows`.
 *
 * ## Luật (thứ tự xét — mọi nhánh "không chắc" đều rơi về KHÔNG GỬI, không bao giờ đoán)
 *  1. Không đọc được danh sách display ⇒ [Reason.UNREADABLE].
 *  2. **B1a — mức B tắt**: có màn ảo cụm (dù trống) mà hồ sơ không bật `themeOnVacantVd` ⇒ [Reason.VD_PRESENT]. 2.90: mức B
 *     BẬT cho đời xe đã đo (Seal `car.type=138` — `ClusterProfile.forCarType`): [ĐO xe 06/10] màn ảo cụm có TỪ LÚC đầu máy
 *     khởi động (mức A không bao giờ gửi được), và gửi `31` khi màn ảo có 0 task + 0 cửa sổ ⇒ KHÔNG sập, màn ảo dựng lại với
 *     id MỚI (4 → 9) — bên gọi phải dò lại id sau khi gửi (`ClusterDisplayResolver.awaitAndPersist(exclude)`).
 *  3. Đã gửi CÙNG opcode trong tiến trình này VÀ id màn ảo cụm không đổi ([Marker]) ⇒ [Reason.SAME_THEME]. Cờ RAM chỉ được
 *     phép làm ĐỠ một lượt gửi, không bao giờ cho phép gửi (CLAUDE.md §5 — sự thật là bản đọc).
 *  4. **B1a** — lần đổi theme trước (sổ bền, cùng boot) chưa đủ [ThemeLedger.MIN_GAP_MS] ⇒ [Reason.TOO_SOON] (không ngủ chờ).
 *  5. Không có màn ảo cụm nào ⇒ [Decision.Send] — không cần đọc stack/cửa sổ: chưa có màn ảo thì không lớp nào nằm trên nó.
 *  6. Không đọc được `am stack list` hoặc danh sách cửa sổ ⇒ [Reason.UNREADABLE].
 *  7. Không task / cửa sổ nào trên màn ảo cụm ⇒ [Decision.Send].
 *  8. Có task / cửa sổ KHÔNG phải placeholder `ClusterBlack` của chính Kachi ⇒ [Reason.FOREIGN] (0 lệnh ghi — không gỡ
 *     ClusterBlack vô ích khi đằng nào cũng bỏ theme). 2.90: 0 task lạ và MỌI cửa sổ lạ là cửa sổ phủ (tên = gói trần) của một
 *     app bóng nổi đã biết ([ClusterBubbleApps]) ⇒ [Reason.BUBBLE] kèm nhãn app (Cài đặt nói "tắt bóng … rồi Áp ngay"; Kachi
 *     KHÔNG tự dừng app bên thứ ba).
 *  9. Chỉ còn placeholder mà đây là bản đọc SAU lượt gỡ ⇒ [Reason.STILL_OCCUPIED].
 * 10. Chỉ còn placeholder ⇒ gỡ đúng stack của nó ([Decision.RemovePlaceholder]) — CHỈ khi mọi task placeholder nằm trong
 *     stack qua [admissible]; không thì [Reason.NOT_REMOVABLE].
 * Luật 6–10 chỉ còn chạy khi `themeOnVacantVd` bật (mức B); lượt gỡ ClusterBlack lúc TẮT chiếu vẫn chạy (vệ sinh) nhưng
 * KHÔNG còn mở khoá lượt gửi theme nào.
 *
 * ## Bốn câu CLAUDE.md §4 cho `am stack remove <id>` ở đây
 *  1. **Display** — chỉ id trong tập màn ảo cụm dò bằng `ClusterDisplayResolver.DETECT_CMD` (fission/xdja, trừ màn ảo do
 *     chính Kachi sở hữu). Tập rỗng ⇒ không gỡ gì. Display 0, màn ảo của ô: không bao giờ.
 *  2. **App** — chỉ `<selfPkg>/…ClusterBlackActivity` (allow-list một component), không bao giờ app khác.
 *  3. **Loại stack** — `mActivityType=standard` bằng CHỮ (chuỗi trống không tính — A12 `removeTask` không tự kiểm loại),
 *     không `pinned`, id > 0; MỌI task của stack phải qua cả ba điều trên trong CÙNG bản đọc.
 *  4. **Hoàn tác** — ClusterBlack là placeholder giữ projection sống; lượt mở chiếu kế tiếp tự dựng lại nó
 *     (`openProjectionBody`). Gỡ hỏng giữa chừng ⇒ đọc lại vẫn thấy ⇒ [Reason.STILL_OCCUPIED] ⇒ không gửi theme.
 */
object ClusterThemePlan {

    /** Opcode theme đã gửi THÀNH CÔNG gần nhất trong tiến trình này + id màn ảo cụm dò được sau lượt mở đó. */
    data class Marker(val op: Int, val vd: Int)

    /** Một cửa sổ WM và display nó nằm ([parseWindows]). */
    data class WindowOnDisplay(val name: String, val displayId: Int)

    enum class Reason { UNREADABLE, VD_PRESENT, SAME_THEME, TOO_SOON, FOREIGN, BUBBLE, STILL_OCCUPIED, NOT_REMOVABLE }

    sealed interface Decision {
        object Send : Decision { override fun toString() = "SEND" }
        data class RemovePlaceholder(val stackIds: List<Int>) : Decision
        /** [apps] = nhãn app bóng nổi đang chặn ([Reason.BUBBLE]); rỗng với mọi lý do khác. */
        data class Skip(val reason: Reason, val detail: String, val apps: List<String> = emptyList()) : Decision
    }

    /** Tên lớp placeholder (khớp cách in `pkg/.x.ClusterBlackActivity` lẫn `pkg/com…ClusterBlackActivity`). */
    const val PLACEHOLDER_CLASS: String = CastStackParser.CLUSTER_PLACEHOLDER_ACTIVITY

    /** Loại stack DUY NHẤT được gỡ (so CHỮ). */
    private const val STANDARD = "standard"

    /**
     * @param vds tập id màn ảo cụm (đã trừ màn ảo của Kachi); `null` = không đọc được `dumpsys display`.
     * @param primaryVd id cụm chính (`ClusterDisplayResolver.resolve`) — so với [marker]; `-1` = chưa có màn ảo cụm.
     * @param tasks `am stack list` đã qua `StackParse.parse`; `null` = không đọc được.
     * @param windows [parseWindows]; `null` = không đọc được.
     * @param afterRemoval bản đọc này là bản SAU lượt gỡ placeholder.
     * @param themeOnVacantVd cờ hồ sơ (`ProjectionRecipe.themeOnVacantVd`) — `false` ⇒ có màn ảo cụm là bỏ (luật 2).
     * @param gapRemainingMs [ThemeLedger.remainingGapMs]; khác `null` ⇒ chưa đủ 15 s (luật 4).
     */
    fun decide(
        op: Int,
        vds: Set<Int>?,
        primaryVd: Int,
        marker: Marker?,
        tasks: List<StackEntry>?,
        windows: List<WindowOnDisplay>?,
        selfPkg: String,
        afterRemoval: Boolean = false,
        themeOnVacantVd: Boolean = false,
        gapRemainingMs: Long? = null,
    ): Decision {
        if (vds == null) return Decision.Skip(Reason.UNREADABLE, "không đọc được dumpsys display")
        if (vds.isNotEmpty() && !themeOnVacantVd) {
            return Decision.Skip(Reason.VD_PRESENT, "màn ảo cụm ${vds.sorted()} đang có — theme chỉ gửi khi chưa có màn ảo (mức A)")
        }
        if (marker != null && marker.op == op && marker.vd >= 1 && marker.vd == primaryVd) {
            return Decision.Skip(Reason.SAME_THEME, "đã gửi $op trong tiến trình này, màn ảo cụm vẫn là ${marker.vd}")
        }
        if (gapRemainingMs != null) {
            return Decision.Skip(Reason.TOO_SOON, "lần đổi theme trước chưa đủ ${ThemeLedger.MIN_GAP_MS / 1000} s (còn $gapRemainingMs ms)")
        }
        if (vds.isEmpty()) return Decision.Send
        if (tasks == null) return Decision.Skip(Reason.UNREADABLE, "không đọc được am stack list")
        if (windows == null) return Decision.Skip(Reason.UNREADABLE, "không đọc được dumpsys window windows")
        if (selfPkg.isBlank()) return Decision.Skip(Reason.UNREADABLE, "thiếu selfPkg")
        val tasksOn = tasks.filter { it.displayId in vds }
        val windowsOn = windows.filter { it.displayId in vds }
        if (tasksOn.isEmpty() && windowsOn.isEmpty()) return Decision.Send
        val foreignTasks = tasksOn.filterNot { isPlaceholderComp(it.comp, selfPkg) }
        val foreignWindows = windowsOn.filterNot { isPlaceholderComp(it.name, selfPkg) }
        if (foreignTasks.isEmpty() && foreignWindows.isNotEmpty()) {
            val labels = foreignWindows.map { w -> w.name.takeIf { '/' !in it }?.let(ClusterBubbleApps::labelOf) }
            if (labels.none { it == null }) {
                val apps = labels.filterNotNull().distinct()
                return Decision.Skip(
                    Reason.BUBBLE,
                    "cửa sổ phủ của ${apps.joinToString()} (${foreignWindows.size}) trên ${vds.sorted()} — tắt bóng rồi Áp ngay",
                    apps,
                )
            }
        }
        if (foreignTasks.isNotEmpty() || foreignWindows.isNotEmpty()) {
            val what = foreignTasks.map { "task ${it.taskId} ${it.comp} @d${it.displayId}" } +
                foreignWindows.map { "cửa sổ ${it.name} @d${it.displayId}" }
            return Decision.Skip(Reason.FOREIGN, what.joinToString("; "))
        }
        if (afterRemoval) {
            return Decision.Skip(Reason.STILL_OCCUPIED, "placeholder còn trên ${vds.sorted()} sau lượt gỡ")
        }
        val stacks = tasksOn.map { it.stackId }.distinct()
        if (stacks.isEmpty()) {
            return Decision.Skip(Reason.NOT_REMOVABLE, "còn cửa sổ placeholder mà không có task để gỡ")
        }
        val refused = stacks.filterNot { admissible(it, tasks, vds, selfPkg) }
        if (refused.isNotEmpty()) {
            return Decision.Skip(Reason.NOT_REMOVABLE, "stack $refused không qua rào (loại/ghim/lẫn task khác)")
        }
        return Decision.RemovePlaceholder(stacks)
    }

    /**
     * Lời đáp cho [ProjectionManager] từ quyết định [d] (review 2.89 Pass 1 · safety-1). [vdBefore] = bản đọc display ĐẦU của
     * lượt có ít nhất một màn ảo cụm (đọc được và khác rỗng).
     *  - [Decision.Send] ⇒ [ThemeVerdict.SEND].
     *  - [Reason.UNREADABLE] ⇒ [ThemeVerdict.ABORT] — cổng không biết cụm ở theme nào (bỏ theme mà vẫn 16/35 ở lần mở đầu sau
     *    nổ máy là chiếu trong theme GỐC — Seal 10.25" mất số km/h [ĐO 05/10]); B1a: sổ cùng tiến trình chứng minh được kiểu
     *    thì [ClusterStylePlan] vẫn cho đi tiếp.
     *  - [Reason.SAME_THEME] / [Reason.VD_PRESENT] ⇒ [ThemeVerdict.SKIP_KNOWN] (màn ảo cụm có từ trước ⇒ cụm đang trong một
     *    phiên chiếu).
     *  - [Reason.TOO_SOON] / [Reason.FOREIGN] / [Reason.BUBBLE] / [Reason.STILL_OCCUPIED] / [Reason.NOT_REMOVABLE] ⇒ [ThemeVerdict.SKIP_KNOWN]
     *    chỉ khi [vdBefore] (cụm đang chiếu từ trước), không thì [ThemeVerdict.ABORT]. [CHƯA BIẾT] màn ảo fission có sống qua
     *    một vòng tắt-nổ máy trong khi cụm về theme gốc không (OC-7) — nếu có, bỏ theme trên màn ảo sống sót cũng phải DỪNG
     *    (🚗 R1-V3).
     *  - [Decision.RemovePlaceholder] (bộ thi hành không bao giờ trả thẳng — nó gỡ rồi quyết lại) ⇒ [ThemeVerdict.ABORT].
     * [ThemeVerdict.ABORT] chưa phải "dừng": [ClusterStylePlan.decide] cho đi tiếp khi sổ chứng minh cụm đã đúng kiểu.
     */
    fun verdict(d: Decision, vdBefore: Boolean): ThemeVerdict = when (d) {
        Decision.Send -> ThemeVerdict.SEND
        is Decision.RemovePlaceholder -> ThemeVerdict.ABORT
        is Decision.Skip -> when (d.reason) {
            Reason.UNREADABLE -> ThemeVerdict.ABORT
            Reason.SAME_THEME, Reason.VD_PRESENT -> ThemeVerdict.SKIP_KNOWN
            Reason.TOO_SOON, Reason.FOREIGN, Reason.BUBBLE, Reason.STILL_OCCUPIED, Reason.NOT_REMOVABLE ->
                if (vdBefore) ThemeVerdict.SKIP_KNOWN else ThemeVerdict.ABORT
        }
    }

    /**
     * Stack [stackId] có được `am stack remove` không — xét MỌI task của nó trong [tasks] (bản đọc của CHÍNH lượt đó). Bộ
     * thi hành gọi lại ngay trước lệnh (guard ở tầng thi hành, CLAUDE.md §5). Xem bốn câu §4 ở KDoc lớp.
     */
    fun admissible(stackId: Int, tasks: List<StackEntry>, vds: Set<Int>, selfPkg: String): Boolean {
        if (stackId <= 0 || selfPkg.isBlank() || vds.isEmpty() || vds.any { it < 1 }) return false
        val inStack = tasks.filter { it.stackId == stackId }
        if (inStack.isEmpty()) return false
        return inStack.all {
            it.displayId in vds && isPlaceholderComp(it.comp, selfPkg) && it.activityType == STANDARD && !it.isPinned
        }
    }

    /**
     * Stack placeholder của Kachi trên [vds] được phép gỡ — dùng cho lượt TẮT chiếu (bước 3 của CLUSTER-THEME-SAFE: lần mở
     * sau thấy màn ảo trống). Stack có lẫn task khác / loại lạ ⇒ bỏ (không gỡ).
     */
    fun placeholderStacks(tasks: List<StackEntry>, vds: Set<Int>, selfPkg: String): List<Int> =
        tasks.filter { it.displayId in vds && isPlaceholderComp(it.comp, selfPkg) }
            .map { it.stackId }.distinct()
            .filter { admissible(it, tasks, vds, selfPkg) }

    /** `true` khi màn ảo cụm không còn task / cửa sổ nào (bản đọc lại sau lượt gỡ). */
    fun vacant(tasks: List<StackEntry>, windows: List<WindowOnDisplay>, vds: Set<Int>): Boolean =
        tasks.none { it.displayId in vds } && windows.none { it.displayId in vds }

    /**
     * `pkg/class` (task) hay tên cửa sổ (`pkg/class` với cửa sổ của activity) là placeholder `ClusterBlack` của [selfPkg]:
     * gói khớp TRỌN, tên lớp đơn giản đúng [PLACEHOLDER_CLASS]. Cửa sổ phủ của Kachi (tên = gói trần) KHÔNG phải placeholder.
     */
    fun isPlaceholderComp(comp: String, selfPkg: String): Boolean {
        if (!comp.contains('/')) return false
        val pkg = comp.substringBefore('/')
        val cls = comp.substringAfter('/').substringAfterLast('.')
        return pkg == selfPkg && cls == PLACEHOLDER_CLASS
    }

    private val RE_WIN_HDR = Regex("^Window #\\d+ Window\\{\\S+ u\\d+ (.*)\\}:?$")
    private val RE_WIN_DISPLAY = Regex("^mDisplayId=(\\d+)")

    /**
     * Parse `dumpsys window windows` (bản đầy đủ hoặc đã `grep -E 'Window #|mDisplayId='`). Định dạng [ĐO dump xe 14/09,
     * `docs/diagnostics/carlog-kachi-20260914-2044/10-window-windows.txt`]: `  Window #6 Window{a4544b6 u0 vn.vietmap.live}:`
     * rồi dòng đầu tiên `    mDisplayId=0 stackId=0 mSession=…` của khối là display của cửa sổ đó.
     * Trả `null` khi không thấy tiêu đề cửa sổ nào — máy đang chạy luôn có cửa sổ (thanh trạng thái…) nên rỗng = đọc hỏng.
     */
    fun parseWindows(out: String): List<WindowOnDisplay>? {
        val res = ArrayList<WindowOnDisplay>()
        var name: String? = null
        var headers = 0
        for (raw in out.lineSequence()) {
            val line = raw.trim()
            val header = RE_WIN_HDR.find(line)
            if (header != null) {
                name = header.groupValues[1]
                headers++
                continue
            }
            val current = name ?: continue
            RE_WIN_DISPLAY.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { d ->
                res.add(WindowOnDisplay(current, d))
                name = null                 // chỉ dòng mDisplayId ĐẦU TIÊN của khối
            }
        }
        return if (headers == 0) null else res
    }
}
