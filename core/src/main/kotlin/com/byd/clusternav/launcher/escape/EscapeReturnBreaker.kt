package com.byd.clusternav.launcher.escape

/**
 * ═══ 2.98 · R18 — CẦU CHÌ của daemon dời stack (thuần, `:core`; một bản mỗi đời daemon) ═══════════════════════════════════
 *
 * CLAUDE.md §4/§5 + bài học 08-01 (`am display move-stack` ném NPE `TaskSnapshotController.createTaskSnapshot` trên DiLink3, task
 * MẤT): cơ chế mới chưa chứng minh trên xe thì lỗi đầu tiên phải dừng nó lại, không thử lại theo vòng. Ba mức:
 *  - [EscapeReport.Scope.PKG] — một gói thôi được dời (lệnh ném `IllegalArgumentException` = đua: stack đã đi/đã ở đó · lệnh xong mà
 *    task còn ở display khác · gói thoát quá [maxPerMinute] lần/60 s — [ĐO máy ảo] Waze 2–3/phút khi tìm đường, nên 10 là bão).
 *  - [EscapeReport.Scope.ALL] — tắt dời cho mọi gói tới hết đời daemon (lỗi cơ chế: quyền, phản chiếu, lỗi lạ).
 *  - [EscapeReport.Scope.PERSIST] — như ALL và Kachi GHI BỀN (bản cài này không dời nữa): dấu hiệu vụ 08-01 — NPE, chữ
 *    `createTaskSnapshot`, hoặc task không còn ở đâu sau lệnh.
 * Ngắt ⇒ ô đi đúng đường 2.93 (nhịp đo `SlotLiveProbe` thấy app ở display 0 ⇒ `APP_ELSEWHERE`). Không có đường tự đóng lại cầu chì.
 */
class EscapeReturnBreaker(
    private val maxPerMinute: Int = MAX_PER_MINUTE,
    private val windowMs: Long = WINDOW_MS,
) {
    private val moves = HashMap<String, ArrayDeque<Long>>()
    private val trippedPkgs = HashMap<String, String>()

    /** Lý do ngắt tất cả (`null` = chưa ngắt). */
    var allTripped: String? = null
        private set

    /**
     * Soát R18 Pass 12 [P2]: báo cáo ngắt BỀN đã phát (`null` = chưa). Daemon phát lại cho MỌI client gửi bảng có api về sau — một
     * `Tripped(PERSIST)` chỉ gửi một lần thì Kachi chết đúng lúc (BYD giết lúc tắt máy) / socket ghi hỏng là mất dấu bền, và Kachi nối
     * lại cùng daemon chỉ nhận `Off("tripped:…")` (không ghi gì) ⇒ lượt khởi động sau thử lại đúng lệnh vừa làm mất task.
     */
    var persisted: EscapeReport.Tripped? = null
        private set

    /** Gói này có bị chặn không (hỏi, không ghi). */
    fun blocks(pkg: String, nowMs: Long): EscapeReturnGuard.Why? = when {
        allTripped != null -> EscapeReturnGuard.Why.TRIPPED_ALL
        pkg in trippedPkgs -> EscapeReturnGuard.Why.TRIPPED_PKG
        recent(pkg, nowMs) >= maxPerMinute -> EscapeReturnGuard.Why.RATE
        else -> null
    }

    /** Đã dời xong [pkg] lúc [nowMs]; chạm trần ⇒ ngắt gói (lượt sau bị chặn) và trả báo cáo. */
    fun onMoved(pkg: String, nowMs: Long): EscapeReport.Tripped? {
        val q = moves.getOrPut(pkg) { ArrayDeque() }
        q.addLast(nowMs)
        prune(q, nowMs)
        if (q.size < maxPerMinute) return null
        return trip(EscapeReport.Scope.PKG, pkg, "rate>=${maxPerMinute}/${windowMs / 1000}s")
    }

    /** Lệnh dời ném [error] (tên lớp + thông điệp + vết, đã gộp chữ). */
    fun onMoveThrew(pkg: String, errorClass: String, text: String): EscapeReport.Tripped =
        trip(classify(errorClass, text), pkg, "${errorClass.substringAfterLast('.')}:${text.take(80)}")

    /** Đọc lại sau lệnh ([EscapeReturnGuard.verify]). */
    fun onAfter(pkg: String, after: EscapeReturnGuard.After): EscapeReport.Tripped? = when (after) {
        EscapeReturnGuard.After.IN_SLOT -> null
        EscapeReturnGuard.After.ELSEWHERE -> trip(EscapeReport.Scope.PKG, pkg, "moved-but-not-in-slot")
        EscapeReturnGuard.After.LOST -> trip(EscapeReport.Scope.PERSIST, pkg, "task-lost-after-move")
    }

    /** Lỗi cơ chế không gắn gói (đăng ký bộ nghe, đọc stack…) ⇒ tắt tất cả. */
    fun onMechanismError(why: String): EscapeReport.Tripped = trip(EscapeReport.Scope.ALL, null, why)

    private fun trip(scope: EscapeReport.Scope, pkg: String?, why: String): EscapeReport.Tripped {
        if (scope == EscapeReport.Scope.PKG && pkg != null) trippedPkgs[pkg] = why else allTripped = why
        val r = EscapeReport.Tripped(scope, pkg, why)
        if (scope == EscapeReport.Scope.PERSIST && persisted == null) persisted = r   // giữ lần ĐẦU (chữ ký thật), không ghi đè
        return r
    }

    private fun recent(pkg: String, nowMs: Long): Int = moves[pkg]?.let { prune(it, nowMs); it.size } ?: 0

    private fun prune(q: ArrayDeque<Long>, nowMs: Long) {
        while (q.isNotEmpty() && nowMs - q.first() >= windowMs) q.removeFirst()
    }

    companion object {
        const val MAX_PER_MINUTE = 10
        const val WINDOW_MS = 60_000L

        /**
         * Giá trị ghi bền khi daemon báo [EscapeReport.Scope.PERSIST] — `versionCode|lý do`. Theo BẢN CÀI: cùng bản ⇒ không dời nữa
         * (qua mọi lần khởi động lại); bản cài khác (versionCode khác) ⇒ thử lại MỘT lần với mã mới, và nếu lại hỏng thì lại ghi.
         */
        fun persistValue(versionCode: Int, why: String): String = "$versionCode|${why.take(160)}"

        /** Dấu bền [stored] còn chặn bản cài [versionCode] không. Đọc hỏng ⇒ không chặn (dấu hỏng không phải bằng chứng). */
        fun persistActive(stored: String?, versionCode: Int): Boolean =
            stored?.substringBefore('|')?.trim()?.toIntOrNull() == versionCode

        /**
         * Phân loại lỗi của lệnh dời. NPE / chữ `createTaskSnapshot` = chữ ký vụ 08-01 ⇒ bền; `IllegalArgumentException`
         * (`RootActivityContainer.moveStackToDisplay` `:938-958`: stack/màn không tồn tại, đã ở màn đó) ⇒ đua, chỉ gói; còn lại ⇒ tất cả.
         */
        fun classify(errorClass: String, text: String): EscapeReport.Scope = when {
            errorClass.endsWith("NullPointerException") || "createTaskSnapshot" in text -> EscapeReport.Scope.PERSIST
            errorClass.endsWith("IllegalArgumentException") -> EscapeReport.Scope.PKG
            else -> EscapeReport.Scope.ALL
        }
    }
}
