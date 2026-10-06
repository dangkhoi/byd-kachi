package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.launcher.FloatingOrphanPlan
import com.byd.clusternav.launcher.FloatingOrphanSweep
import com.byd.clusternav.modules.clustercast.DisplayParse
import com.byd.clusternav.modules.clustercast.StackEntry
import com.byd.clusternav.modules.clustercast.StackParse
import com.byd.clusternav.modules.clustercast.WmParse

/**
 * ═══ CLUSTER-THEME-SAFE (2.89, P0) — BỘ THI HÀNH cổng theme (đọc sự thật → [ClusterThemePlan.decide] → gỡ placeholder) ═══
 *
 * Một thực thể mỗi coordinator (tiến trình). Chạy TRÊN executor của coordinator (shell I/O chặn) — không bao giờ luồng vẽ.
 *
 *  • [admit] (gọi từ [ProjectionManager] ngay trước mỗi opcode theme): đọc `dumpsys display` (tập màn ảo cụm, cách dò SẴN
 *    CÓ — [ClusterDisplayResolver.DETECT_CMD] + [WmParse.clusterDisplayIds] − màn ảo của chính Kachi) và sổ theme
 *    ([ThemeLedger], khoảng 15 s); quyết bằng [ClusterThemePlan.decide]. B1a: có màn ảo cụm mà hồ sơ không bật
 *    `themeOnVacantVd` ⇒ `VD_PRESENT` ngay, 0 lệnh đọc thêm; chưa có màn ảo ⇒ không đọc stack/cửa sổ (không có
 *    lớp nào để đọc). Cờ bật (mức B — 2.90 bật cho Seal 138 sau phép đo 06/10, `oncar-2026-10-06-cluster-rect.md` F4): đọc `am stack list` + `dumpsys window windows`; nếu chỉ còn
 *    `ClusterBlack` của Kachi ⇒ gỡ đúng stack của nó, đọc lại tới khi trống (trần [SETTLE_READS] × [SETTLE_STEP_MS]) rồi
 *    quyết LẦN HAI. Lời đáp [ThemeVerdict] qua [ClusterThemePlan.verdict]; quyết cuối ở [ClusterStylePlan].
 *  • [inspect]: CÙNG các lượt đọc + quyết định, KHÔNG gỡ, KHÔNG ghi sổ — cho `ClusterDiag` "Cổng theme (chỉ đọc)".
 *  • [sending] / [sent]: sổ theme bền `pending` TRƯỚC lệnh, `ok` sau khi shell nhận (CLAUDE.md §5). Pass 2 · cluster-r1-5:
 *    ghi `pending` hỏng ⇒ [sending] trả `false` ⇒ KHÔNG gửi; khoảng 15 s lấy mốc muộn hơn giữa sổ và RAM ([remainingGapMs]).
 *  • [sent] / [beginOpen] / [bindVd]: dấu RAM "đã gửi opcode X, màn ảo cụm sau đó là Y" — chỉ dùng để ĐỠ một lượt gửi
 *    trùng ([ClusterThemePlan.Reason.SAME_THEME]); không bao giờ là lý do để gửi (CLAUDE.md §5).
 *  • [removePlaceholder] (lượt TẮT chiếu, vệ sinh): gỡ `ClusterBlack` của Kachi khỏi màn ảo cụm. B1a: KHÔNG còn mở khoá
 *    lượt gửi theme nào (màn ảo còn ⇒ `VD_PRESENT` dù trống).
 *
 * Bốn câu CLAUDE.md §4 cho lệnh gỡ: KDoc [ClusterThemePlan]. Lệnh gỡ dựng ở MỘT chỗ ([FloatingOrphanPlan.removeCmd]).
 *
 * ## Gỡ TRONG tiến trình trước, `am stack remove` chỉ cho mồ côi (review 2.89 Pass 1 · safety-5)
 * [ĐO nguồn AOSP fetch 05/10] `am stack remove` → `removeTaskByIdLocked(…, killProcess = true …)` (đường trích ở KDoc
 * `SlotClosePlan`) → `cleanUpRemovedTaskLocked` (A10 r47 `ActivityStackSupervisor.java:1806-1870`, A12 r34
 * `ActivityTaskSupervisor.java:1574-1638`) duyệt MỌI tiến trình của gói: chỉ bỏ qua `mHomeProcess` (`:1839-1842`), rồi
 * tiến trình nào không còn activity chưa dừng (`WindowProcessController.shouldKillProcessForRemovedTask` A10 `:694-709`) và
 * không có dịch vụ tiền cảnh ⇒ vào danh sách giết. [SUY mạnh từ nguồn, chưa đo trên xe] Kachi là HOME ⇒ tiến trình chính được
 * tha, nhưng `:tts` (PiperTtsService, không tiền cảnh, không activity) bị giết — trừ khi một tiến trình khác của gói chặn cả
 * lượt (`return` ở `:1848-1857`: Kachi không là HOME mà còn activity chưa dừng, hoặc `:wake` tiền cảnh khi Hey Kachi bật).
 * Bản KDoc cũ chỉ xét tiến trình chính — sai.
 * Nên ClusterBlack của CHÍNH tiến trình này gỡ bằng [OwnPlaceholder] (`Activity.finishAndRemoveTask` ⇒ A10
 * `ActivityTaskManagerService.java:1597-1607` / A12 `ActivityClientController.java:434` `removeTask…(killProcess = false)` ⇒
 * dừng ở `if (!killProcess) return` `:1821-1823`, không giết gì). `am stack remove` chỉ còn cho ClusterBlack mồ côi của một pid
 * đã chết (không có thực thể sống nào để gọi). Hai đường cùng gửi `cleanUpServices` (`:1817-1819` → `ActiveServices.java:3489-3521`
 * ⇒ `onTaskRemoved` cho dịch vụ đã start của gói) — dịch vụ của Kachi không ghi đè `onTaskRemoved`, không `stopWithTask`
 * [ĐO grep 05/10] ⇒ vô hại.
 */
class ClusterThemeGuard(
    private val shell: SimpleCastShell,
    private val selfPackage: String,
    private val own: OwnPlaceholder = OwnPlaceholder.NONE,
    private val sleepMs: (Long) -> Unit = { Thread.sleep(it) },
    /** B1a — sổ theme bền (phạm vi XE). JVM/test: trong bộ nhớ. */
    private val store: ThemeLedger.Store = ThemeLedger.InMemory(),
    /** B1a — đồng hồ của sổ. JVM/test: [ThemeLedger.JVM_CLOCK]. */
    private val clock: ThemeLedger.Clock = ThemeLedger.JVM_CLOCK,
    /** B1a — cờ hồ sơ `themeOnVacantVd`, đọc lúc quyết (công thức có thể được dò lại ở lượt mở đầu). Mặc định TẮT. */
    private val vacantVdAllowed: () -> Boolean = { false },
    /** 2.90 · R9 — bộ thi hành "dọn cụm / trả cụm" ([ClusterLayerPause]); mặc định không có lớp nào (hành vi trước Pass 3). */
    private val layers: ClusterLayerPort = ClusterLayerPort.NONE,
    /**
     * 2.93 · CAST-OPEN-TIMEOUT — phần hạn còn lại (ms) của thao tác executor đang chạy cổng này; `null` = không giới hạn. Mặc định
     * đọc hạn của chính thao tác trên luồng gọi ([BoundedCastExecutor.remainingMs]) — test truyền đồng hồ giả.
     */
    private val budgetLeftMs: () -> Long? = { BoundedCastExecutor.remainingMs() },
    private val log: (String) -> Unit = {},
) : ThemeGate {

    /**
     * Gỡ ClusterBlack của CHÍNH tiến trình này bằng API trong tiến trình (`:app` — `ClusterBlackActivity.finishOwn`:
     * `Activity.finishAndRemoveTask`, không giết tiến trình nào — KDoc lớp). [finish] nhận task id của MỘT stack đã qua rào
     * [ClusterThemePlan.admissible], trả những task id đã giao cho `finishAndRemoveTask`. Task không có trong kết quả ⇒ không
     * có thực thể sống (mồ côi của pid đã chết) ⇒ bộ thi hành dùng `am stack remove`. Không được ném.
     */
    fun interface OwnPlaceholder {
        fun finish(taskIds: Set<Int>): Set<Int>

        companion object {
            /** JVM / test: không có thực thể nào trong tiến trình ⇒ luôn đường shell (hành vi trước Pass 1). */
            val NONE: OwnPlaceholder = OwnPlaceholder { emptySet() }
        }
    }

    @Volatile private var marker: ClusterThemePlan.Marker? = null
    @Volatile private var pendingOp: Int? = null

    /** Mục `pending` vừa ghi ở [sending] — [sent] đổi ĐÚNG mục đó thành `ok` (giữ mốc giờ gửi cho khoảng 15 s). */
    @Volatile private var lastPending: ThemeLedger.Entry? = null

    /**
     * Review 2.89 Pass 2 · cluster-r1-5 — lần gửi theme gần nhất của TIẾN TRÌNH này (RAM): khoảng 15 s lấy mốc MUỘN hơn giữa RAM
     * và sổ ([remainingGapMs]) ⇒ sổ đọc hỏng / bị ghi đè sau khi gửi vẫn không phá được khoá 15 s trong tiến trình.
     */
    @Volatile private var lastSend: ThemeLedger.Entry? = null

    /** Một dòng mô tả lượt quyết gần nhất (cho log/màn chẩn đoán). */
    @Volatile var lastVerdict: String? = null
        private set

    /** Đầu một lượt mở chiếu: bỏ opcode "đã gửi" còn treo của lượt trước (lượt đó có thể đã hỏng trước khi [bindVd]). */
    fun beginOpen() { pendingOp = null; openScope = true }

    /** Sau khi dò được màn ảo cụm của lượt mở này: chốt dấu "đã gửi [pendingOp], màn ảo = [vd]". */
    fun bindVd(vd: Int) {
        val op = pendingOp
        pendingOp = null
        if (op != null && vd >= 1) marker = ClusterThemePlan.Marker(op, vd)
    }

    override fun ledger(): ThemeLedger.Entry? = ThemeLedger.decode(runCatching { store.read() }.getOrNull())

    override fun now(): ThemeLedger.Now = clock.now()

    override fun sending(op: Int): Boolean {
        val n = clock.now()
        val e = ThemeLedger.Entry(op, ThemeLedger.State.PENDING, n.elapsedMs, n.boot)
        if (!write(e)) {
            // Pass 2 · cluster-r1-5: dấu không chạm đĩa ⇒ KHÔNG gửi (sổ còn mục cũ — khoảng 15 s và kiểu suy ra đều sai).
            lastPending = null
            log("sổ theme: KHÔNG ghi được 'pending' cho $op ⇒ KHÔNG gửi opcode này (CLAUDE.md §5)")
            return false
        }
        lastPending = e
        lastSend = e
        return true
    }

    /**
     * Còn bao lâu mới được đổi theme lần nữa (`null` = được): mốc muộn hơn giữa sổ bền và [lastSend] (RAM) — cổng ([admit]) và
     * lượt thử lại tự mở chiếu (`themeGapRetryMs`, whole-r1-5) cùng đọc ở đây.
     */
    fun remainingGapMs(): Long? {
        val n = clock.now()
        return listOfNotNull(ThemeLedger.remainingGapMs(ledger(), n), ThemeLedger.remainingGapMs(lastSend, n)).maxOrNull()
    }

    override fun sent(op: Int) {
        pendingOp = op
        val p = lastPending?.takeIf { it.op == op }
        val n = clock.now()
        val e = p?.copy(state = ThemeLedger.State.OK) ?: ThemeLedger.Entry(op, ThemeLedger.State.OK, n.elapsedMs, n.boot)
        lastPending = null
        if (!write(e)) log("sổ theme: KHÔNG ghi được 'ok' cho $op")
    }

    private fun write(e: ThemeLedger.Entry): Boolean = runCatching { store.write(ThemeLedger.encode(e)) }.getOrDefault(false)

    /** Kết quả một lượt đọc + quyết của [inspect] (không gỡ, không ghi). */
    class Inspection(
        val op: Int,
        val vds: Set<Int>?,
        val decision: ClusterThemePlan.Decision,
        val verdict: ThemeVerdict,
        val line: String,
    )

    /**
     * Đọc + quyết, KHÔNG một lệnh ghi (không gỡ placeholder, không ghi sổ, không đổi dấu RAM) — `ClusterDiag` "Cổng theme".
     * [ClusterThemePlan.Decision.RemovePlaceholder] được báo nguyên văn (lượt thật sẽ gỡ rồi quyết lại).
     */
    fun inspect(op: Int): Inspection {
        val r = readAndDecide(op)
        val v = ClusterThemePlan.verdict(r.decision, r.vdBefore)
        return Inspection(op, r.displays?.vds, r.decision, v, line(op, r.displays, r.decision, v))
    }

    /**
     * 2.90 · R1 — nhãn app bóng nổi chặn lượt [admit] GẦN NHẤT ([ClusterThemePlan.Reason.BUBBLE]); rỗng = lượt đó không bị
     * bóng nổi chặn. Chỉ để Cài đặt / Chẩn đoán nói lý do ("tắt bóng VietMap rồi Áp ngay") — không quyết lệnh nào (CLAUDE.md §5).
     */
    @Volatile var lastBlockers: List<String> = emptyList()
        private set

    /**
     * 2.90 · R9 — lượt [admit] gần nhất đã DỌN mà bóng nổi vẫn còn ⇒ bản mod chưa hỗ trợ `VM_BUBBLE_VIS` (Cài đặt: "bản mod VietMap
     * cũ chưa hỗ trợ ẩn bóng — tắt VietMap rồi Áp ngay"). Chỉ để nói lý do, không quyết lệnh nào.
     */
    @Volatile var lastBubbleOldMod: Boolean = false
        private set

    /** 2.90 · R9 — đã DỌN cụm mà chưa TRẢ ([resumeLayers]). RAM: tiến trình chết ⇒ lớp của Kachi dựng lại từ đầu (mặc định "không dọn"). */
    @Volatile private var paused = false

    /**
     * Review Pass 3 [P2] — DỌN chỉ được phép trong một lượt MỞ ([beginOpen] … [resumeLayers] ở `finally` của
     * `openProjectionGuarded`). Lượt TẮT (`ProjectionManager.close` với công thức còn opcode theme) không có `finally` TRẢ ⇒ nếu dọn
     * ở đó, badge + bóng nằm ẩn tới lượt mở sau. Ngoài lượt mở ⇒ luật cũ (FOREIGN/BUBBLE ⇒ bỏ opcode).
     */
    @Volatile private var openScope = false

    /** Nhãn app bóng nổi của lượt DỌN đang chạy — [verdict] quyết [lastBubbleOldMod] từ quyết định CUỐI (kể cả sau gỡ ClusterBlack). */
    @Volatile private var pausedBubbles: List<String> = emptyList()

    /** 2.93 wave 2A · VM-BUBBLE-OLDMOD-MEMO — sổ "bản mod đang cài đã chứng minh không ẩn bóng" ([BubbleOldModMemo]). */
    private val oldMods = OldModMemoGate(layers, log)

    override fun admit(op: Int): ThemeVerdict = admitInner(op)

    /**
     * 2.90 · R9 — TRẢ cụm sau một lượt mở có DỌN (idempotent: chưa dọn ⇒ không làm gì). [liveId] = id cụm dò SAU khi mở (màn ảo có
     * thể đã dựng lại với id mới — [ĐO xe 06/10] 4 → 9); `< 1` ⇒ lớp của Kachi tự chọn display. `show` của bóng = công tắc người lái
     * ([ClusterLayerPause.resume]). Không bao giờ ném — đường mở chiếu không được gãy vì một lớp phủ.
     */
    fun resumeLayers(liveId: Int) {
        openScope = false
        if (!paused) return
        paused = false
        val plan = runCatching { ClusterLayerPause.resume(liveId, layers.bubbleHiddenByUser(), layers.bubbleInstalled()) }
            .getOrElse { ClusterLayerPause.Resume(if (liveId >= 1) liveId else -1, null) }
        runCatching { layers.resumeOwn(plan.reattachOn) }.onFailure { log("trả cụm: gắn lại lớp Kachi lỗi ${it.message}") }
        plan.bubbleShow?.let { show -> runCatching { layers.sendBubble(show) }.onFailure { log("trả cụm: VM_BUBBLE_VIS lỗi ${it.message}") } }
        log("trả cụm: id=${plan.reattachOn} lớp Kachi gắn lại · VM_BUBBLE_VIS ${plan.bubbleShow?.let { "show=$it" } ?: "không gửi (VietMap không cài)"}")
    }

    /**
     * 2.90 · R9 — DỌN rồi đọc lại tới khi chỉ còn placeholder (trần [PAUSE_READS] × [SETTLE_STEP_MS]), rồi QUYẾT LẠI toàn bộ từ bản
     * đọc mới (gồm nhánh gỡ ClusterBlack). Một lần mỗi lượt [admit]. Luật gửi không đổi — chỉ bản đọc đổi.
     *
     * 2.93 · CAST-OPEN-TIMEOUT — lượt đọc lại thứ hai trở đi chỉ chạy khi lượt mở còn đủ hạn ([affordable]); lượt đầu đã được bên gọi
     * xin trước khi dọn. Dừng sớm ⇒ quyết từ bản đọc lại cuối (bóng còn ⇒ BUBBLE ⇒ KHÔNG gửi — rào an toàn giữ nguyên). Dòng log
     * mang thời gian thật tới khi sạch (🚗 đo mod v2: OQ6 spec 290).
     */
    private fun pauseAndReread(op: Int, first: Read, pause: ClusterLayerPause.Pause): Read {
        val vds = first.displays?.vds ?: return first
        paused = true
        runCatching { layers.pauseOwn() }.onFailure { log("dọn cụm: gỡ lớp Kachi lỗi ${it.message}") }
        val hide = runCatching { layers.bubbleInstalled() }.getOrDefault(false)
        if (hide) runCatching { layers.sendBubble(false) }.onFailure { log("dọn cụm: VM_BUBBLE_VIS lỗi ${it.message}") }
        log("dọn cụm: theme $op trên ${vds.sorted()} — lớp Kachi ${pause.ownWindows} cửa sổ, bóng ${pause.bubbleApps}" +
            " · VM_BUBBLE_VIS ${if (hide) "show=false" else "không gửi (app bóng không cài)"} rồi đọc lại")
        val t0 = System.nanoTime()
        var reads = 0
        var cleared = false
        for (i in 0 until PAUSE_READS) {
            if (i > 0 && !affordable("dọn cụm", op)) break
            sleepMs(SETTLE_STEP_MS)
            val t = readTasks()
            val w = readWindows()
            reads++
            if (t != null && w != null && ClusterLayerPause.cleared(t, w, vds, selfPackage)) { cleared = true; break }
        }
        log("dọn cụm: ${if (cleared) "SẠCH" else "chưa sạch"} sau ${(System.nanoTime() - t0) / 1_000_000L} ms · $reads/$PAUSE_READS lần đọc lại")
        // Wave 2A · VM-BUBBLE-OLDMOD-MEMO: chỉ lượt TRỌN (đủ lượt đọc lại, bóng vẫn còn) mới ghi sổ; bóng ẩn ⇒ xoá sổ. Review Pass 1
        // [P2]: lệnh ẩn chỉ là phép THỬ bản mod khi bản đọc ĐẦU có bóng — dọn chỉ lớp Kachi (VietMap không chạy) sạch ngay mà
        // không chứng minh gì; bản trước coi đó là "bóng đã ẩn" ⇒ XOÁ sổ ⇒ mod v1 lại tốn ~5 s ở lượt mở kế có bóng.
        val probed = hide && pause.bubbleApps.isNotEmpty()
        return readAndDecide(op).also { oldMods.settle(probed, reads == PAUSE_READS, cleared, ClusterLayerPause.oldMod(it.decision)) }
    }

    /**
     * 2.93 · CAST-OPEN-TIMEOUT — trong lượt MỞ, một lượt chờ/đọc lại nữa chỉ được chạy khi phần hạn còn lại của thao tác ([budgetLeftMs])
     * lớn hơn [OPEN_TAIL_RESERVE_MS] — thứ lượt mở còn PHẢI làm sau quyết định theme. Ngoài lượt mở / ngoài executor (`null`) ⇒ luôn
     * được (hành vi cũ). Không đủ ⇒ một dòng log; bên gọi thôi chờ và quyết từ bản đọc đang có — chỉ có thể ra "không gửi".
     */
    private fun affordable(what: String, op: Int): Boolean {
        if (!openScope) return true
        val left = runCatching { budgetLeftMs() }.getOrNull() ?: return true
        if (left > OPEN_TAIL_RESERVE_MS) return true
        log("$what (theme $op): lượt mở còn $left ms ≤ dự trữ đuôi $OPEN_TAIL_RESERVE_MS ms ⇒ thôi chờ, quyết từ bản đọc hiện có")
        return false
    }

    private fun admitInner(op: Int): ThemeVerdict {
        lastBubbleOldMod = false
        pausedBubbles = emptyList()
        var first = readAndDecide(op)
        val d0 = first.decision
        if (d0 is ClusterThemePlan.Decision.Skip && openScope && !paused &&
            (d0.reason == ClusterThemePlan.Reason.FOREIGN || d0.reason == ClusterThemePlan.Reason.BUBBLE)
        ) {
            val pause = first.displays?.vds?.let { ClusterLayerPause.pausable(first.tasks, first.windows, it, selfPackage) }
            // Wave 2A · VM-BUBBLE-OLDMOD-MEMO: bản mod ĐANG CÀI đã chứng minh không ẩn bóng ⇒ bỏ dọn (0 broadcast, 0 lượt đọc lại);
            // quyết = BUBBLE như lượt dọn thật sẽ ra — chỉ có thể là "không gửi". Không cần hạn (không chờ gì).
            // 2.93 · CAST-OPEN-TIMEOUT (nhánh dọn thật): không đủ hạn cho dù MỘT lượt đọc lại ⇒ KHÔNG dọn (không ẩn bóng của người lái
            // vô ích, không báo "mod cũ" oan) — quyết từ bản đọc đầu (BUBBLE/FOREIGN ⇒ bỏ theme).
            if (pause != null && pause.bubbleApps.isNotEmpty() && oldMods.skip(op)) {
                pausedBubbles = pause.bubbleApps
                first = Read(first.displays, first.tasks, BubbleOldModMemo.skipDecision(pause.bubbleApps), first.vdBefore, first.windows)
            } else if (pause != null && affordable("dọn cụm", op)) {
                pausedBubbles = pause.bubbleApps
                first = pauseAndReread(op, first, pause)
            }
        }
        val vdBefore = first.vdBefore
        val removal = when (val d = first.decision) {
            ClusterThemePlan.Decision.Send, is ClusterThemePlan.Decision.Skip -> return verdict(op, first.displays, d, vdBefore)
            is ClusterThemePlan.Decision.RemovePlaceholder -> d
        }
        // decide() chỉ ra RemovePlaceholder khi cả ba bản đọc có mặt (mức B — cờ hồ sơ bật).
        val vds = first.displays?.vds ?: return ThemeVerdict.ABORT
        val before = first.tasks ?: return ThemeVerdict.ABORT
        // 2.93 · CAST-OPEN-TIMEOUT: không đủ hạn để gỡ rồi đọc lại ⇒ KHÔNG gỡ (0 lệnh ghi): gỡ mà không kịp thấy trống thì đằng nào cũng
        // không gửi theme; ClusterBlack để nguyên, lượt mở tự dựng lại nó như mọi lần.
        if (!affordable("gỡ ClusterBlack", op)) {
            val late = ClusterThemePlan.Decision.Skip(ClusterThemePlan.Reason.NOT_REMOVABLE, "lượt mở không còn đủ hạn để gỡ ${removal.stackIds} rồi đọc lại")
            return verdict(op, first.displays, late, vdBefore)
        }
        val sentIds = removeStacks(removal.stackIds, before, vds)
        if (sentIds.isEmpty()) {
            val none = ClusterThemePlan.Decision.Skip(ClusterThemePlan.Reason.NOT_REMOVABLE, "lệnh gỡ ${removal.stackIds} không chạy được")
            return verdict(op, first.displays, none, vdBefore)
        }
        log("theme $op: gỡ placeholder ClusterBlack stack=$sentIds trên ${vds.sorted()} rồi đọc lại")
        var tasks: List<StackEntry>? = before
        var windows: List<ClusterThemePlan.WindowOnDisplay>? = null
        for (i in 0 until SETTLE_READS) {
            if (i > 0 && !affordable("gỡ ClusterBlack", op)) break
            sleepMs(SETTLE_STEP_MS)
            tasks = readTasks()
            windows = readWindows()
            val t = tasks
            val w = windows
            if (t != null && w != null && ClusterThemePlan.vacant(t, w, vds)) break
        }
        val fresh = readDisplays()
        val second = ClusterThemePlan.decide(
            op, fresh?.vds, fresh?.primary ?: -1, null, tasks, windows, selfPackage, afterRemoval = true,
            themeOnVacantVd = vacantVdAllowed(), gapRemainingMs = remainingGapMs(),
        )
        return verdict(op, fresh, second, vdBefore)
    }

    private class Read(
        val displays: Displays?,
        val tasks: List<StackEntry>?,
        val decision: ClusterThemePlan.Decision,
        val vdBefore: Boolean,
        val windows: List<ClusterThemePlan.WindowOnDisplay>? = null,
    )

    /**
     * Bản đọc ĐẦU của một lượt: display + sổ; stack + cửa sổ CHỈ khi có màn ảo cụm VÀ cờ mức B bật (B1a — không đọc thứ luật
     * không dùng: chưa có màn ảo ⇒ không lớp nào nằm trên nó; có màn ảo mà cờ tắt ⇒ đằng nào cũng bỏ).
     */
    private fun readAndDecide(op: Int): Read {
        val displays = readDisplays()
        val vacantOk = vacantVdAllowed()
        val needLayers = displays != null && displays.vds.isNotEmpty() && vacantOk
        val tasks = if (needLayers) readTasks() else null
        val windows = if (needLayers) readWindows() else null
        val gap = remainingGapMs()
        // safety-1: màn ảo cụm có TỪ TRƯỚC lượt này (bản đọc ĐẦU) — quyết lời từ chối là "bỏ, đi tiếp" hay "chưa bảo đảm".
        val vdBefore = displays?.vds?.isNotEmpty() == true
        val d = ClusterThemePlan.decide(
            op, displays?.vds, displays?.primary ?: -1, marker, tasks, windows, selfPackage,
            themeOnVacantVd = vacantOk, gapRemainingMs = gap,
        )
        return Read(displays, tasks, d, vdBefore, windows)
    }

    /**
     * Lượt TẮT chiếu (bước 3, vệ sinh): gỡ `ClusterBlack` của Kachi khỏi MỌI màn ảo cụm (đọc tươi, phạm vi ở
     * [ClusterThemePlan]). Không chờ đọc lại. Trả số stack đã gửi lệnh gỡ; 0 khi không đọc được / không có.
     */
    fun removePlaceholder(tag: String): Int {
        val displays = readDisplays() ?: run { log("$tag: không đọc được dumpsys display — không gỡ ClusterBlack"); return 0 }
        if (displays.vds.isEmpty()) return 0
        val tasks = readTasks() ?: run { log("$tag: không đọc được am stack list — không gỡ ClusterBlack"); return 0 }
        val ids = ClusterThemePlan.placeholderStacks(tasks, displays.vds, selfPackage)
        if (ids.isEmpty()) return 0
        val sentIds = removeStacks(ids, tasks, displays.vds)
        log("$tag: gỡ ClusterBlack khỏi màn ảo cụm ${displays.vds.sorted()} stack=$sentIds")
        return sentIds.size
    }

    /**
     * Gỡ từng stack — kiểm LẠI rào trên CÙNG bản đọc ngay trước lệnh (guard tầng thi hành, CLAUDE.md §5). safety-5: mọi task
     * của stack có thực thể sống trong tiến trình này ⇒ gỡ trong tiến trình ([own], không giết `:tts`); còn lại (mồ côi) ⇒
     * `am stack remove`.
     */
    private fun removeStacks(ids: List<Int>, tasks: List<StackEntry>, vds: Set<Int>): List<Int> {
        val sent = ArrayList<Int>()
        for (id in ids) {
            if (!ClusterThemePlan.admissible(id, tasks, vds, selfPackage)) continue
            val taskIds = tasks.filter { it.stackId == id }.mapTo(HashSet()) { it.taskId }
            if (taskIds.isNotEmpty() && own.finish(taskIds).containsAll(taskIds)) {
                log("gỡ stack $id trong tiến trình (finishAndRemoveTask task=${taskIds.sorted()}, không giết tiến trình)")
                sent += id
                continue
            }
            val r = runCatching { shell.execute(FloatingOrphanPlan.removeCmd(id)) }.getOrNull()
            if (r != null && r.success) sent += id else log("gỡ stack $id hỏng: ${r?.stderr ?: "shell ném"}")
        }
        return sent
    }

    private fun verdict(op: Int, displays: Displays?, d: ClusterThemePlan.Decision, vdBefore: Boolean): ThemeVerdict {
        val v = ClusterThemePlan.verdict(d, vdBefore)
        lastBlockers = (d as? ClusterThemePlan.Decision.Skip)?.takeIf { it.reason == ClusterThemePlan.Reason.BUBBLE }?.apps.orEmpty()
        val bubbles = pausedBubbles
        pausedBubbles = emptyList()
        lastBubbleOldMod = bubbles.isNotEmpty() && ClusterLayerPause.oldMod(d)
        // Review wave 2A Pass 1 [P3]: [paused] = lượt này DỌN thật (đã gửi VM_BUBBLE_VIS); lượt bỏ dọn nhờ sổ mod cũ thì 0 broadcast —
        // dòng log không được nói "sau VM_BUBBLE_VIS" (buổi xe đọc dòng này để biết lệnh ẩn có đi không).
        if (lastBubbleOldMod) log(
            if (paused) "dọn cụm: bóng $bubbles VẪN còn sau VM_BUBBLE_VIS ⇒ bản mod chưa hỗ trợ ẩn — bỏ theme (BUBBLE)"
            else "dọn cụm: bóng $bubbles — sổ mod cũ, lượt này KHÔNG dọn (0 broadcast) ⇒ bỏ theme (BUBBLE)",
        )
        val text = line(op, displays, d, v)
        lastVerdict = text
        log(text)
        return v
    }

    private fun line(op: Int, displays: Displays?, d: ClusterThemePlan.Decision, v: ThemeVerdict): String =
        "theme $op cụm=${displays?.vds?.sorted() ?: "?"} → $d ⇒ $v"

    private class Displays(val vds: Set<Int>, val primary: Int)

    /**
     * Tập màn ảo cụm từ CÙNG lệnh dò của đường đặt app ([ClusterDisplayResolver.DETECT_CMD]). Bản đọc lành luôn có tiêu đề
     * `Display 0:` (grep giữ `Display [0-9]+:`) — thiếu nó = đọc hỏng (`null`), không phải "không có màn ảo nào".
     */
    private fun readDisplays(): Displays? {
        val r = runCatching { shell.execute(ClusterDisplayResolver.DETECT_CMD) }.getOrNull() ?: return null
        if (!r.success || !DISPLAY0.containsMatchIn(r.stdout)) return null
        val owned = DisplayParse.ownedVirtualDisplayIds(r.stdout, selfPackage)
        val vds = WmParse.clusterDisplayIds(r.stdout) - owned
        return Displays(vds, ClusterDisplayResolver.resolve(r.stdout, selfPackage))
    }

    /** `am stack list` — rỗng trên máy đang chạy là đọc hỏng (display 0 luôn có stack home) ⇒ `null`. */
    private fun readTasks(): List<StackEntry>? {
        val r = runCatching { shell.execute(STACK_CMD) }.getOrNull() ?: return null
        if (!r.success || r.stdout.isBlank()) return null
        return StackParse.parse(r.stdout).takeIf { it.isNotEmpty() }
    }

    private fun readWindows(): List<ClusterThemePlan.WindowOnDisplay>? {
        val r = runCatching { shell.execute(WINDOWS_CMD) }.getOrNull() ?: return null
        if (!r.success) return null
        return ClusterThemePlan.parseWindows(r.stdout)
    }

    companion object {
        const val STACK_CMD: String = "am stack list"

        /**
         * Cửa sổ + display của chúng. `grep -E` (toybox trên xe không nhận `\|` — session-findings 14/09) giữ đúng hai loại
         * dòng [ClusterThemePlan.parseWindows] cần.
         */
        const val WINDOWS_CMD: String = "dumpsys window windows | grep -E 'Window #|mDisplayId='"

        /**
         * Số lần đọc lại sau lệnh gỡ placeholder — bước 250 ms như `FloatingOrphanSweep`, 4 lần thay vì 5. Hết lượt mà còn thấy ⇒
         * KHÔNG gửi theme (hướng an toàn). [CHƯA BIẾT] trên xe `am stack remove` một activity đen mất bao lâu ([ĐO 05/10] VietMap:
         * > 1,25 s). ⚠ "≤ 1 s" chỉ là phần NGỦ: mỗi lượt còn hai lệnh đọc — [SUY log xe 06/10] ≈ 0,9 s/lượt trên xe ⇒ tới ~3,7 s;
         * 2.93: lượt thứ hai trở đi chỉ chạy khi lượt mở còn đủ hạn ([OPEN_TAIL_RESERVE_MS]).
         */
        const val SETTLE_READS: Int = 4

        /**
         * 2.90 · R9 — số lần đọc lại sau DỌN: broadcast tới mod rồi mod `removeView` trên luồng chính của nó. [CHƯA BIẾT] trên xe mất
         * bao lâu (spec 290 OQ6) — hết lượt mà còn bóng ⇒ coi như mod cũ, KHÔNG gửi (an toàn). ⚠ Bản 2.90 ghi "≤ 1,5 s" (chỉ tính phần
         * ngủ); [ĐO log xe 06/10 13:49 · 15:13] cả 6 lượt mất ~4,5–5 s với mod v1 (bóng không bao giờ ẩn) — góp phần làm lượt mở
         * chạm hạn 15 s. 2.93: lượt thứ hai trở đi chỉ chạy khi lượt mở còn đủ hạn ([OPEN_TAIL_RESERVE_MS]).
         */
        const val PAUSE_READS: Int = 6
        const val SETTLE_STEP_MS: Long = FloatingOrphanSweep.SETTLE_STEP_MS

        /**
         * 2.93 · CAST-OPEN-TIMEOUT — phần hạn lượt mở chiếu PHẢI còn sau mọi lượt chờ tuỳ chọn của cổng (dọn cụm, đọc lại sau gỡ
         * ClusterBlack) để đuôi bắt buộc chạy xong trong hạn cứng ([BoundedCastExecutor.OPEN_TIMEOUT_MS]): đọc-quyết lại (3 lệnh) +
         * gỡ/đọc display (2) + opcode theme · 16 · 35 (3 lệnh, ngủ 2 + 2 + 1 s) + điều kiện nền (~2) + dò cụm (≥ 1) + `wm` (3) +
         * ClusterBlack (3 lệnh, ngủ 1 s) + nhận lại (1) ≈ 18 lệnh + 6 s ngủ ⇒ [SUY log xe 06/10, ≈ 0,27–0,33 s/lệnh] 11–12 s.
         * Đuôi của nhánh bỏ theme ngắn hơn ⇒ dự trữ này là cận trên. Đo lại ở 🚗 (spec `kachi-293-cast.html`).
         */
        const val OPEN_TAIL_RESERVE_MS: Long = 12_000L

        private val DISPLAY0 = Regex("Display 0:")
    }
}
