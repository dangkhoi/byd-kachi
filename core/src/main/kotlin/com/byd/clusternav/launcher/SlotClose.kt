package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.StackEntry

/**
 * ═══ L6 · (c) *TẮT* app trong ô — CHỌN stack cần gỡ (thuần, không chạy lệnh) ══════════════════════════════════════════
 *
 * Owner 03/10: *"2 là tắt app luôn, để UI trong suốt thấy nền background cho đẹp"*. Tắt = gỡ ĐÚNG task của app trên màn ảo
 * của ô — tương đương vuốt app khỏi Gần đây, KHÔNG `am force-stop` cả gói (force-stop giết cả dịch vụ / báo thức / nhạc
 * của app ở mọi nơi). Lệnh dùng lại bộ dựng DUY NHẤT [FloatingOrphanPlan.removeCmd] (`am stack remove <id>`, bài
 * `FloatingWiringContractTest` khoá "một chỗ dựng"); đọc sự thật bằng [StackReads]. Thi hành ở [SlotCloseRun].
 *
 * ## Lệnh làm gì — [ĐO nguồn AOSP]
 *  - A10 r47: `ActivityManagerShellCommand.runStackRemove` (`:2629-2634`) → `ActivityTaskManagerService.removeStack`
 *    (`:3373-3392`: stack không phải standard/undefined ⇒ NÉM) → `ActivityStackSupervisor.removeStack` (`:1766-1768`) →
 *    `removeStackInSurfaceTransaction` (`:1733-1759`): mỗi task ⇒ `removeTaskByIdLocked(…, killProcess = true,
 *    REMOVE_FROM_RECENTS, "remove-stack")` → `cleanUpRemovedTaskLocked` (`:1806-1866`): dừng dịch vụ gắn task
 *    (`cleanUpServices`), và CHỈ giết tiến trình khi nó không còn activity ở task khác trong Gần đây
 *    (`shouldKillProcessForRemovedTask`, `:1848-1852`) và không có dịch vụ tiền cảnh (`hasForegroundServices`,
 *    `:1854-1857`) ⇒ app đang dẫn đường / phát nhạc bằng dịch vụ tiền cảnh sống tiếp. Không phụ thuộc display nào.
 *  - A12 r34 (DL5): `runRootTaskRemove` (`:2716-2721`, cùng chữ `am stack remove <id>`) → `removeTask` (`:1904-1926`) —
 *    KHÔNG kiểm loại stack ⇒ bộ lọc standard bằng CHỮ ở [admissible] là rào DUY NHẤT trên DL5.
 *
 * ## Bốn câu của CLAUDE.md §4 cho `am stack remove <id>` ở đây
 *  1. **Display nào** — đúng màn ảo của Ô (`vd` do host của ô giữ, `≥ 1`), MỌI task của stack phải có `displayId = vd`
 *     trong bản đọc của chính lượt đó. `vd < 1` ⇒ không làm gì. Cụm, display 0: không bao giờ.
 *  2. **App nào** — đúng gói ô đang giữ (host so `pkg`); mọi task của stack là gói đó; không bao giờ chính Kachi.
 *  3. **Loại stack nào** — `mActivityType=standard` bằng CHỮ (chuỗi trống không tính), không `pinned`.
 *  4. **Hoàn tác** — không hoàn tác được (≈ vuốt khỏi Gần đây). Người dùng mở lại bằng ⇄ / lối tắt / ngăn kéo; khởi động
 *     lại Kachi thì ô về hồ sơ (app LƯU mở lại). Hỏng giữa chừng ⇒ đọc lại vẫn thấy app ⇒ ô KHÔNG đổi + một câu báo.
 */
object SlotClosePlan {

    /** Stack nào của [pkg] trên màn ảo [vd] được gỡ — theo thứ tự trong bản đọc. Rỗng = không làm gì. */
    fun targets(entries: List<StackEntry>, vd: Int, pkg: String, selfPkg: String): List<Int> =
        entries.filter { it.displayId == vd && it.pkg == pkg }.map { it.stackId }.distinct()
            .filter { admissible(it, entries, vd, pkg, selfPkg) }

    /**
     * Stack [stackId] có được gỡ không — xét MỌI task của nó trong [entries] (bản đọc của chính lượt đó). Đây là hàm bộ
     * thi hành gọi lại ngay trước lệnh (guard ở tầng thi hành, CLAUDE.md §5).
     */
    fun admissible(stackId: Int, entries: List<StackEntry>, vd: Int, pkg: String, selfPkg: String): Boolean {
        if (vd < 1 || pkg.isBlank() || selfPkg.isBlank() || pkg == selfPkg) return false
        val tasks = entries.filter { it.stackId == stackId }
        if (tasks.isEmpty()) return false
        return tasks.all {
            it.displayId == vd && it.pkg == pkg && it.activityType == FloatingOrphanPlan.STANDARD && !it.isPinned
        }
    }

    /** App còn task nào trên màn ảo [vd] không (bản đọc lại sau lệnh). */
    fun onVd(entries: List<StackEntry>, vd: Int, pkg: String): Boolean = entries.any { it.displayId == vd && it.pkg == pkg }
}

/**
 * ═══ L6 · (c) *TẮT* app trong ô — MỘT LƯỢT (thuần JVM, nhận kênh shell; khuôn [FloatingOrphanSweep]) ══════════════════
 *
 * `am stack list` → [SlotClosePlan.targets] → `am stack remove <id>` từng stack (guard [SlotClosePlan.admissible] trên
 * CÙNG bản đọc) → báo "đã gửi" ([run] `onSent` — màn chính giấu mặt vẽ NGAY, A3) → đọc lại tới khi app rời màn ảo ô (lịch
 * [SETTLE_STEPS_MS] ≈ 4 s, [StackReads.settle]). Kết luận bằng bản đọc LẦN HAI (sự thật), không theo chữ in ra của lệnh gỡ.
 * Không `am force-stop`.
 *
 * ⚠ CHẶN (dadb) — chỉ gọi trên luồng nền.
 */
class SlotCloseRun(
    private val selfPkg: String,
    /** Ngủ giữa hai lần đọc lại. Tiêm vào để test không ngủ thật; bản chạy = `Thread.sleep`. */
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {

    /**
     * [CLOSED] app không còn task trên màn ảo ô (đọc lại) · [NOT_ON_VD] đọc được mà app đã không ở màn ảo ô từ trước (đã
     * tự đóng / đang toàn màn chỗ khác — 0 lệnh gỡ) · [STILL_THERE] đã gửi lệnh mà đọc lại vẫn thấy · [REFUSED] app có ở
     * màn ảo nhưng không stack nào qua rào (loại lạ / ghim / lẫn gói khác) — 0 lệnh · [READ_FAILED] không đọc được.
     */
    enum class Outcome { CLOSED, NOT_ON_VD, STILL_THERE, REFUSED, READ_FAILED }

    data class Report(
        val vd: Int,
        val pkg: String,
        val outcome: Outcome,
        val sent: List<Int> = emptyList(),
        val reads: Int = 0,
        val error: String? = null,
        /** A3 — tổng thời gian đã NGHỈ giữa các lần đọc lại (ms) — nhật ký: lệnh gỡ trên xe chậm bao lâu (OQ A1-OQ2). */
        val waitedMs: Long = 0,
    ) {
        /** Ô đã không còn app trên màn ảo ⇒ áp luật hoàn ô ([SlotRevertPlan], `APP_CLOSED`). */
        val slotFree: Boolean get() = outcome == Outcome.CLOSED || outcome == Outcome.NOT_ON_VD

        /** Một dòng nhật ký `KachiSlotLife` (không dịch — nhật ký). */
        fun line(): String = "tắt $pkg vd=$vd → $outcome gửi=$sent đọc-lại=$reads chờ=${waitedMs}ms" + (error?.let { " lỗi=$it" } ?: "")
    }

    /**
     * Một lượt *tắt*. [onSent] chạy (trên luồng NÀY) đúng một lần, ngay khi ít nhất một lệnh gỡ đã được gửi mà không ném —
     * TRƯỚC vòng đọc lại: bên gọi coi "đã gửi lệnh + app đang rời" là ĐANG TẮT (A3: giấu mặt vẽ ngay, không để khung đứng
     * suốt lượt chờ). Không gửi được lệnh nào ⇒ không gọi.
     */
    fun run(sh: (String) -> String, vd: Int, pkg: String, onSent: () -> Unit = {}): Report {
        if (vd < 1 || pkg.isBlank() || pkg == selfPkg) return Report(vd, pkg, Outcome.REFUSED)
        val first = StackReads.read(sh)
        val before = first.entries ?: return Report(vd, pkg, Outcome.READ_FAILED, error = first.error)
        if (!SlotClosePlan.onVd(before, vd, pkg)) return Report(vd, pkg, Outcome.NOT_ON_VD)
        val sent = ArrayList<Int>()
        var error: String? = null
        for (id in SlotClosePlan.targets(before, vd, pkg, selfPkg)) {
            if (!SlotClosePlan.admissible(id, before, vd, pkg, selfPkg)) continue
            try {
                sh(FloatingOrphanPlan.removeCmd(id))
                sent += id
            } catch (e: Exception) {
                // Bắt rộng CÓ CHỦ Ý (cùng lẽ `FloatingOrphanSweep.removeStack`): luồng nền của màn nhà, lọt ra là sập HOME.
                // Kết quả thật do bản đọc lần hai quyết.
                error = "gỡ $id ném ${e.javaClass.simpleName}"
            }
        }
        if (sent.isEmpty()) return Report(vd, pkg, if (error != null) Outcome.STILL_THERE else Outcome.REFUSED, error = error)
        onSent()
        val settled = StackReads.settle(sh, sleep, SETTLE_STEPS_MS) { e -> !SlotClosePlan.onVd(e, vd, pkg) }
        val waited = SlotCloseSettle.waited(SETTLE_STEPS_MS, settled.reads)
        val after = settled.read.entries
            ?: return Report(vd, pkg, Outcome.READ_FAILED, sent, settled.reads, settled.read.error ?: error, waited)
        val outcome = if (SlotClosePlan.onVd(after, vd, pkg)) Outcome.STILL_THERE else Outcome.CLOSED
        return Report(vd, pkg, outcome, sent, settled.reads, error, waited)
    }

    companion object {
        /**
         * A3 · SLOT-CLOSE-SETTLE (2.89) — lịch nghỉ giữa các lần đọc lại sau lệnh gỡ: 250 → 400 → 640 → 1000 → 1000 → 710 ms
         * (tổng 4 s, 7 lần đọc). Nhịp đầu giữ 250 ms của `FloatingOrphanSweep` ([ĐO máy ảo E2E 3b]: một nhịp thường đủ ⇒ ca
         * nhanh vẫn kết luận sau ~250 ms); giãn dần vì [ĐO xe 05/10] `am stack remove` trên màn ảo ô chậm hơn cửa sổ cũ
         * 5 × 250 ms (`STILL_THERE … đọc-lại=5`, app chỉ rời ô ~27 s sau ở nhịp đo ô) ⇒ báo nhầm "chưa tắt được".
         */
        internal val SETTLE_STEPS_MS: LongArray = SlotCloseSettle.steps()   // internal: mảng — không phơi cho ai sửa
    }
}

/**
 * ═══ A3 · SLOT-CLOSE-SETTLE — lịch đọc lại sau lệnh *tắt* (thuần) ══════════════════════════════════════════════════════
 *
 * Lũy thừa có trần: bước `k` = `first × factor^k`, kẹp ≤ [CAP_MS]; cộng dồn tới khi chạm [BUDGET_MS], phần dư cuối (≥ bước
 * đầu) thành bước chót ⇒ tổng nghỉ đúng bằng ngân sách. Tổng ngân sách là trần THỜI GIAN luồng nền của màn chính bị chặn bởi
 * một lượt *tắt* — không phải nhịp: lệnh xong sớm ⇒ vòng dừng ở lần đọc đầu thấy app rời ô.
 */
object SlotCloseSettle {

    /** Nhịp đầu — cùng `FloatingOrphanSweep.SETTLE_STEP_MS`. */
    const val FIRST_MS = FloatingOrphanSweep.SETTLE_STEP_MS

    /** Hệ số giãn giữa hai nhịp. */
    const val FACTOR = 1.6

    /** Trần một nhịp — đọc dày hơn 1 s/lần không cần, thưa hơn thì ô chờ lâu sau khi app đã rời. */
    const val CAP_MS = 1_000L

    /** Tổng thời gian nghỉ tối đa (~4 s, brief 2.89 A3). */
    const val BUDGET_MS = 4_000L

    fun steps(first: Long = FIRST_MS, factor: Double = FACTOR, cap: Long = CAP_MS, budget: Long = BUDGET_MS): LongArray {
        require(first > 0 && factor >= 1.0 && cap >= first && budget >= first) { "lịch hỏng: $first/$factor/$cap/$budget" }
        val out = ArrayList<Long>()
        var step = first.toDouble()
        var total = 0L
        while (true) {
            val s = minOf(cap, step.toLong())
            if (total + s > budget) break
            out += s; total += s
            step *= factor
        }
        val rest = budget - total
        if (rest >= first) out += rest
        return out.toLongArray()
    }

    /** Tổng đã nghỉ khi vòng dừng ở lần đọc thứ [reads] (1 = chưa nghỉ lần nào). */
    fun waited(steps: LongArray, reads: Int): Long = steps.take((reads - 1).coerceIn(0, steps.size)).sum()
}
