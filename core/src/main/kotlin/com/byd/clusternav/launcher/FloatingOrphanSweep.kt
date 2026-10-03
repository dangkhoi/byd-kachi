package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.StackEntry

/**
 * ═══ PROFILE-SWITCH-SLOTS · R-B3/R-B6 — MỘT LƯỢT DỌN cửa sổ nổi do Kachi mở (thuần JVM, nhận kênh shell) ══════════
 *
 * Trình tự: dấu rỗng ⇒ thoát, 0 lệnh shell · `am stack list` → [FloatingOrphanPlan.plan] → `am stack remove <id>` từng
 * stack (qua [removeStack], guard tầng thi hành) → `am stack list` lần hai (đọc lại tới khi stack đã gửi biến mất, tối
 * đa [SETTLE_READS] lần — xem [settle]) → gỡ thành công hay không quyết bằng bản đọc lần hai (SỰ THẬT, không đoán theo
 * chữ in ra của lệnh gỡ) → xoá khỏi dấu các gói [FloatingOrphanPlan.forgettable].
 * Không `am force-stop`, không `fullscreenCmd`.
 *
 * Ở `:core` (không phải `:app` như spec Pass 0 ghi) để chuỗi lệnh + hành vi được test bằng shell ghi âm trên fixture
 * nguyên văn, không chỉ bằng quét mã. `:app` chỉ còn: tạo kho prefs, tính gói còn giữ, nộp lên luồng nền, ghi log.
 *
 * ⚠ CHẶN (dadb) — chỉ gọi trên luồng nền (`LauncherWindows.sweepFloating` nộp vào `winExec`).
 */
class FloatingOrphanSweep(
    private val ledger: FloatingWindowLedger,
    private val selfPkg: String,
    /** Ngủ giữa hai lần đọc lại của [settle]. Tiêm vào để test không ngủ thật; bản chạy = `Thread.sleep`. */
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {

    enum class Outcome { EMPTY_LEDGER, READ_FAILED, READ2_FAILED, DONE }

    /** Kết quả bộ thi hành cho MỘT id. */
    enum class Exec { SENT, REFUSED, ERROR }

    /** Kết quả một lượt — [line] là dòng log `KachiFloat` (R-B6). */
    data class Report(
        val reason: String,
        val outcome: Outcome,
        val ledger: List<String> = emptyList(),
        val sent: List<Int> = emptyList(),
        val removed: List<Int> = emptyList(),
        val refused: List<Int> = emptyList(),
        val stillFloating: Set<String> = emptySet(),
        val forgotten: Set<String> = emptySet(),
        val error: String? = null,
        /** Số lần đọc `am stack list` SAU lệnh gỡ ([settle]); 0 = không gửi lệnh gỡ nào. */
        val settleReads: Int = 0,
    ) {
        fun line(): String = when (outcome) {
            Outcome.EMPTY_LEDGER -> "dọn[$reason] bỏ: dấu rỗng (0 lệnh shell)"
            Outcome.READ_FAILED -> "dọn[$reason] bỏ: đọc am stack list hỏng${error?.let { " ($it)" } ?: ""} — giữ dấu $ledger"
            Outcome.READ2_FAILED ->
                "dọn[$reason] đã gửi gỡ $sent, đọc lần hai hỏng${error?.let { " ($it)" } ?: ""} — giữ dấu $ledger"
            Outcome.DONE ->
                "dọn[$reason] dấu=$ledger gỡ=$removed/$sent từ-chối=$refused còn-nổi=$stillFloating xoá-dấu=$forgotten" +
                    (if (settleReads > 0) " đọc-lại=$settleReads" else "") + (error?.let { " lỗi=$it" } ?: "")
        }
    }

    /**
     * Một lượt dọn. [held] = mọi gói `SlotContent.App` state đang giữ (kể cả ô tràn) — đọc LÚC CHẠY ở chỗ gọi.
     * Tuần tự hoá toàn tiến trình ([LOCK]): hai mốc (gỡ app · kênh vừa lên) có thể trùng giờ.
     */
    fun run(sh: (String) -> String, held: Set<String>, reason: String): Report =
        synchronized(LOCK) { runLocked(sh, held, reason) }

    private fun runLocked(sh: (String) -> String, held: Set<String>, reason: String): Report {
        val opened = ledger.opened()
        if (opened.isEmpty()) return Report(reason, Outcome.EMPTY_LEDGER)
        val first = read(sh)
        val before = first.entries ?: return Report(reason, Outcome.READ_FAILED, opened, error = first.error)
        val sent = ArrayList<Int>()
        val refused = ArrayList<Int>()
        var error: String? = null
        for (id in FloatingOrphanPlan.plan(before, opened, held, selfPkg)) {
            when (removeStack(sh, id, before, opened, held)) {
                Exec.SENT -> sent += id
                Exec.REFUSED -> refused += id
                Exec.ERROR -> error = "gỡ $id ném lỗi"
            }
        }
        // Không gửi lệnh gỡ nào thì bản đọc lần một vẫn là sự thật hiện tại — không tốn thêm một lượt dadb.
        val second = if (sent.isEmpty()) null else settle(sh, sent)
        val settleReads = second?.reads ?: 0
        val after = if (second == null) before else {
            second.read.entries ?: return Report(
                reason, Outcome.READ2_FAILED, opened, sent = sent, refused = refused, error = second.read.error,
                settleReads = settleReads,
            )
        }
        val alive = after.map { it.stackId }.toSet()
        val forgotten = FloatingOrphanPlan.forgettable(after, opened, held)
        ledger.forget(forgotten)
        return Report(
            reason, Outcome.DONE, opened, sent = sent, removed = sent.filter { it !in alive }, refused = refused,
            stillFloating = FloatingOrphanPlan.stillFloating(after, opened), forgotten = forgotten.toSortedSet(),
            error = error, settleReads = settleReads,
        )
    }

    /**
     * Bản đọc lần hai SAU lệnh gỡ: đọc lại tới khi không còn id nào trong [sent] (hoặc tới [SETTLE_READS] lần, cách
     * nhau [SETTLE_STEP_MS]); trả bản đọc CUỐI + số lần đọc. Đọc hỏng ⇒ trả ngay bản hỏng (chỗ gọi giữ dấu).
     *
     * ## Vì sao phải chờ — [ĐO máy ảo 2026-10-01 E2E ca 3b] + [ĐO AOSP r47]
     * Lệnh gỡ trả về TRƯỚC khi task rời stack: `removeTaskByIdLocked` → `TaskRecord.removeTaskActivitiesLocked`
     * (`ActivityStackSupervisor.java:1789-1800`, `TaskRecord.java:1530-1533`) chỉ YÊU CẦU từng activity kết thúc
     * (`performClearTaskAtIndexLocked` → `finishActivityLocked(…, pauseImmediately=false)` `:1432-1452`); task chỉ rời
     * stack khi activity cuối được gỡ thật (`removeActivity` `:1371-1395`) — sau lượt pause/destroy bất đồng bộ. Bản đọc
     * ngay sau lệnh gỡ vì thế còn thấy stack: E2E dòng log lượt dọn 20:14:17.607 ghi `gỡ=[]/[40] còn-nổi=[vn.vietmap.live]
     * xoá-dấu=[]`, VietMap kết thúc (`System.exit`) lúc 17.651 và +5 s không còn stack nào ⇒ log sai và dấu ở lại tới
     * lượt dọn sau. A12 (DL5): [SUY] cùng kiểu kết-thúc-bất-đồng-bộ — vòng đọc lại không phụ thuộc đời Android nên không
     * cần mục `ClusterProfile`.
     *
     * Bị ngắt (màn huỷ ⇒ `winExec.shutdownNow()`) ⇒ trả bản đọc gần nhất (an toàn: dấu chỉ xoá theo sự thật đã đọc),
     * giữ cờ ngắt, KHÔNG ném ra luồng nền (ném ra là sập HOME — xem [removeStack]).
     */
    private fun settle(sh: (String) -> String, sent: List<Int>): StackReads.Settled =
        StackReads.settle(sh, sleep, SETTLE_READS, SETTLE_STEP_MS) { e -> sent.none { id -> e.any { it.stackId == id } } }

    /**
     * ★ GUARD Ở TẦNG THI HÀNH (CLAUDE.md §5 · R-nf2) — chỗ DUY NHẤT chạy lệnh gỡ CỬA SỔ NỔI (gỡ app trong ô: [SlotCloseRun]).
     *
     * Kiểm LẠI [FloatingOrphanPlan.admissible] trên [read] — bản đọc `am stack list` của CHÍNH lượt này — ngay trước
     * khi chạy lệnh: id không có trong bản đọc · stack không ở display 0 · không `standard` · không `freeform` · pinned
     * · có gói ngoài dấu / còn giữ / chính Kachi ⇒ [Exec.REFUSED], KHÔNG một lệnh shell nào. Một lỗi ở tầng kế hoạch
     * (hay một chỗ gọi mới mai sau) vì thế không bao giờ biến thành `am stack remove 0` (stack home — A12 không tự chặn).
     */
    internal fun removeStack(
        sh: (String) -> String,
        stackId: Int,
        read: List<StackEntry>,
        opened: Collection<String>,
        held: Collection<String>,
    ): Exec {
        if (!FloatingOrphanPlan.admissible(stackId, read, opened, held, selfPkg)) return Exec.REFUSED
        return try {
            sh(FloatingOrphanPlan.removeCmd(stackId))
            Exec.SENT
        } catch (e: Exception) {
            // Bắt rộng CÓ CHỦ Ý: kênh dadb có thể ném IOException/IllegalStateException/… (lambda Kotlin không khai
            // ngoại lệ). Đây là lượt dọn phụ chạy trên luồng nền của màn nhà: để lọt ra là sập HOME trên xe đang chạy
            // (luồng nền Android không ai bắt). Nuốt có báo: kết quả thật do bản đọc lần hai quyết, dấu còn ⇒ lượt sau
            // làm lại. Error (OOM…) không bắt.
            Exec.ERROR
        }
    }

    /** Đọc + parse — bản dùng chung [StackReads.read] (ném / parse rỗng ⇒ `entries = null` = đọc hỏng, giữ dấu). */
    private fun read(sh: (String) -> String): StackReads.Read = StackReads.read(sh)

    companion object {
        /** Khoá toàn tiến trình — dấu là MỘT tệp của cả xe, nên lượt dọn cũng chỉ được MỘT tại một thời điểm. */
        private val LOCK = Any()

        /**
         * Trần số lần đọc `am stack list` sau lệnh gỡ ([settle]). 5 lần × [SETTLE_STEP_MS] ⇒ chặn `winExec` thêm tối
         * đa ~1 s, và chỉ khi đã gửi lệnh gỡ mà stack chưa đi (ca hiếm). [ĐO máy ảo E2E 3b] app kết thúc ~44 ms sau
         * dòng log của lượt dọn (tức sau bản đọc lại đầu) ⇒ một nhịp nghỉ thường là đủ; năm lần là trần, không phải nhịp.
         */
        const val SETTLE_READS = 5

        /** Khoảng nghỉ giữa hai lần đọc lại của [settle]. */
        const val SETTLE_STEP_MS = 250L
    }
}
