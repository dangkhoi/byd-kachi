package com.byd.clusternav.modules.clustercast.simplified

/**
 * Manages cluster projection lifecycle.
 *
 * Open = [opcode theme do [ClusterStylePlan] chọn, hoặc không] + [ProjectionRecipe.castSeq] (Seal DL3 lần mở đầu sau nổ máy:
 * 30 → 16 → 35) → cluster shows projection surface.
 * Close = [ProjectionRecipe.teardownSeq] (Seal DL3: 18 → 0) → cluster returns to gauges.
 *
 * Idempotent: calling open when already open is a no-op, same for close.
 * Field-proven on Seal DL3 (BYD Song Plus DM-i 2023), 2026-08-02.
 *
 * ## CLUSTER-THEME-SAFE (2.89)
 *  • Chuỗi lệnh lấy từ hồ sơ đời xe ([recipe], `ClusterProfile.projectionRecipe()`), không ghi cứng — mặc định
 *    [ProjectionRecipe.SEAL_DL3] = đúng chuỗi cũ từng byte (CLAUDE.md §6: đường đã chạy không đổi thứ tự).
 *  • B1a: opcode theme KHÔNG nằm trong `castSeq`. [ClusterStylePlan.wanted] chọn opcode cần (theo kiểu người lái + sổ theme),
 *    [ThemeGate.admit] quyết cho/không NGAY trước khi gửi, [ClusterStylePlan.decide] ra quyết định cuối: gửi opcode rồi chuỗi
 *    chiếu · bỏ opcode (và nhịp ngủ của nó) rồi chuỗi chiếu · DỪNG (không 16/35 — xem KDoc [ClusterStylePlan]).
 *  • Chưa có màn ảo cụm TRƯỚC lượt mở (`displayId < 1` — bên gọi truyền id dò được trước khi mở) mà cổng nói SKIP_KNOWN
 *    (hai bản đọc lệch nhau) ⇒ coi như ABORT (review 2.89 Pass 1 · safety-1).
 *  • Sổ theme: [ThemeGate.sending] (`pending`) ngay TRƯỚC lệnh theme, [ThemeGate.sent] (`ok`) sau khi shell nhận. Ghi `pending`
 *    hỏng ⇒ KHÔNG gửi theme, quyết lại như cổng ABORT (review 2.89 Pass 2 · cluster-r1-5, CLAUDE.md §5).
 *  • Nhịp ngủ giữ đúng đường cũ: 2 s sau mỗi lệnh mở, 1 s sau lệnh CUỐI; 300 ms giữa các lệnh đóng.
 */
class ProjectionManager(
    private val shell: SimpleCastShell,
    private val sleepMs: (Long) -> Unit = { Thread.sleep(it) },
    recipe: ProjectionRecipe = ProjectionRecipe.SEAL_DL3,
) {
    /**
     * Công thức đang dùng. Chỉ đổi qua [refreshRecipe] (lượt mở đầu của tiến trình, khi `:app` dò được đời xe bằng shell —
     * `car.type` không đọc được trong tiến trình). `@Volatile`: đọc từ executor của coordinator lẫn luồng gọi.
     */
    @Volatile
    var recipe: ProjectionRecipe = recipe
        private set

    @Volatile
    var isOpen: Boolean = false
        private set

    /**
     * Opcode theme làm lượt [open] gần nhất DỪNG ([ClusterStylePlan.Plan.abort]); `null` = lượt đó không dừng vì cổng. Chỉ để
     * bên gọi viết câu lỗi (`setError`) — không quyết lệnh nào (CLAUDE.md §5).
     */
    @Volatile
    var abortedOn: Int? = null
        private set

    /** Kế hoạch kiểu cụm của lượt [open] gần nhất (cho log / `ClusterDiag`); `null` = chưa mở lần nào. */
    @Volatile
    var lastPlan: ClusterStylePlan.Plan? = null
        private set

    /**
     * B1b — kiểu cụm của PHIÊN đang mở ([CastSessionStyle]): ghim MỘT lần khi [open] thành công, xoá khi [close] thành công hoặc
     * [resetState] về đóng. `null` = không có phiên. Khung app và khoá lưu `__RECT` đọc ở đây — không đọc
     * lại lựa chọn của hồ sơ giữa phiên (mẫu VC-R6).
     */
    @Volatile
    var session: CastSessionStyle? = null
        private set

    /** Thay công thức (chỉ khi KHÔNG đang chiếu — chuỗi tắt phải khớp chuỗi đã mở). Trả `true` khi đã thay. */
    fun refreshRecipe(next: ProjectionRecipe): Boolean {
        if (isOpen) return false
        recipe = next
        return true
    }

    /**
     * Opens the cluster projection. After this call the cluster surface is active and ready to receive app content on the
     * live-resolved cluster display.
     *
     * @param displayId id màn ảo cụm dò được TRƯỚC lượt mở (`< 1` = chưa có).
     * @param gate cổng của opcode đổi theme — bắt buộc, không có giá trị mặc định: không còn đường mở nào gửi theme mù.
     * @param desired kiểu người lái chọn (B1a: mặc định Bo tròn — D3, đường đang chạy).
     * @return true if projection was opened (or already open), false on shell failure or when the style plan stopped the open
     *   ([abortedOn]).
     */
    fun open(displayId: Int, gate: ThemeGate, desired: CastStyle = CastStyle.CURVED): Boolean {
        if (isOpen) return true
        abortedOn = null
        session = null
        val r = recipe
        val ledger = gate.ledger()
        val want = ClusterStylePlan.wanted(r, desired, ledger)
        var verdict = want?.let { gate.admit(it) }
        if (verdict == ThemeVerdict.SKIP_KNOWN && displayId < 1) verdict = ThemeVerdict.ABORT
        var plan = ClusterStylePlan.decide(r, desired, ledger, want, verdict, gate.now())
        // Review 2.89 Pass 2 · cluster-r1-5 — sổ `pending` ghi TRƯỚC lệnh theme (opcode theme luôn đứng đầu chuỗi); ghi hỏng ⇒
        // KHÔNG gửi theme: quyết lại như cổng ABORT — đi tiếp 16/35 chỉ khi sổ (cùng tiến trình) chứng minh cụm đã đúng kiểu.
        val themeOp = plan.themeOp
        if (!plan.abort && themeOp != null && !gate.sending(themeOp)) {
            val again = ClusterStylePlan.decide(r, desired, ledger, want, ThemeVerdict.ABORT, gate.now())
            plan = again.copy(why = "sổ theme không ghi được 'pending' cho $themeOp ⇒ không gửi · ${again.why}")
        }
        lastPlan = plan
        if (plan.abort) {
            abortedOn = want
            return false
        }
        // Phòng thủ: castSeq do [ProjectionRecipe.of] đã bóc opcode theme; công thức dựng tay mà còn sót thì KHÔNG gửi ngoài cổng.
        val seq = listOfNotNull(plan.themeOp) + r.castSeq.filterNot { r.isTheme(it) }
        for ((i, op) in seq.withIndex()) {
            val theme = i == 0 && plan.themeOp != null
            val res = shell.execute(r.command(op))
            if (!res.success) return false
            if (theme) gate.sent(op)
            sleepMs(if (i == seq.lastIndex) OPEN_LAST_SLEEP_MS else OPEN_STEP_SLEEP_MS)
        }
        session = CastSessionStyle.of(r, desired, plan)
        isOpen = true
        return true
    }

    /**
     * Closes the cluster projection. After this call the cluster returns to the native gauge display.
     *
     * Seal [18, 0] không có opcode theme ([ProjectionRecipe.of] bóc mọi opcode theme khỏi chuỗi tắt — D6: tắt chiếu không đổi
     * theme). Công thức dựng tay còn sót opcode theme thì vẫn phải qua [gate] như lúc mở — mọi lời khác
     * [ThemeVerdict.SEND] ⇒ bỏ opcode đó, chuỗi trả đồng hồ đi tiếp.
     *
     * @return true if projection was closed (or already closed), false on shell failure.
     */
    fun close(displayId: Int, gate: ThemeGate): Boolean {
        if (!isOpen) return true
        val r = recipe
        val seq = r.teardownSeq
        for ((i, op) in seq.withIndex()) {
            val theme = r.isTheme(op)
            if (theme && gate.admit(op) != ThemeVerdict.SEND) continue
            if (theme && !gate.sending(op)) continue   // Pass 2 · cluster-r1-5: không ghi được dấu ⇒ không gửi
            val res = shell.execute(r.command(op))
            if (!res.success) return false
            if (theme) gate.sent(op)
            if (i < seq.lastIndex) sleepMs(CLOSE_STEP_SLEEP_MS)
        }
        isOpen = false
        session = null
        return true
    }

    /** Reset state without issuing commands (e.g. after process restart). B1b: về đóng ⇒ không còn phiên. */
    fun resetState(open: Boolean) {
        isOpen = open
        if (!open) session = null
    }

    private companion object {
        /** Sau mỗi lệnh mở trừ lệnh cuối (Seal: sau 30, sau 16). */
        const val OPEN_STEP_SLEEP_MS = 2000L
        /** Sau lệnh mở cuối (Seal: sau 35). */
        const val OPEN_LAST_SLEEP_MS = 1000L
        /** Giữa hai lệnh đóng (Seal: giữa 18 và 0). */
        const val CLOSE_STEP_SLEEP_MS = 300L
    }
}
