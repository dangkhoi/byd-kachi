package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ 3.1 · R2/R3 — GỘP lệnh −/+ khung chiếu + báo KẾT QUẢ về màn ════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-301-cast-steps-dialog-ia.html` §2.1, §4.1.
 *
 * ## Bệnh nó chữa [SUY — đọc mã, chưa tái lập trên máy ảo]
 * Mỗi cú −/+ là một `executor.submit("resize")` (`SimpleCastCoordinator.resizeActiveTarget` cũ) ⇒ nhấn giữ 2 s là ~13 việc
 * xếp hàng, mỗi việc một `am task resize` (100–400 ms [ĐOÁN]); hàng 1 chỗ + bỏ-cũ-nhất thì việc chờ của NGƯỜI KHÁC (chip DPI,
 * đổi tỉ lệ) bị đẩy ra theo từng cú. Và lệnh hỏng thì không ai biết: thân cũ `return` im, màn vẫn nói số mới.
 *
 * ## Luật
 *  1. **Một ô chờ cho mỗi ô khung** ([CastResizeRequest.side]: `null` = toàn cụm, LEFT/RIGHT = một nửa). Yêu cầu mới THAY yêu
 *     cầu đang chờ của cùng ô — chỉ khung CUỐI được áp.
 *  2. **Tối đa một việc "xả" đang chờ trên executor** — nhấn giữ bao lâu cũng chỉ một chỗ trong hàng, không đẩy việc khác ra
 *     theo từng cú. Việc đang CHẠY lấy hết ô chờ lúc bắt đầu ⇒ yêu cầu tới sau nó xếp đúng một việc xả mới.
 *  3. **Mọi yêu cầu đều có kết cục** ([CastResizeOutcome]) — APPLIED / FAILED / DROPPED — phát cho người nghe (màn Cài đặt
 *     đồng bộ lại số với sự thật, R2). Việc xả bị executor bỏ khỏi hàng ([dropped], nối từ `onDiscard`) hoặc chết lặng vì hết
 *     hạn khi còn chờ (quá [staleMs] ⇒ xếp lại) không được làm kẹt hàng gộp vĩnh viễn.
 *
 * Tệp này KHÔNG chứa chuỗi lệnh shell nào — thân áp là [run] (`CastSessionPin.runResize` → `CastGeometryController`), đúng một
 * đường `am task resize` như cũ (CLAUDE.md §4: không thêm loại lệnh).
 */
data class CastResizeRequest(val side: ClusterSlotSide?, val bounds: CastBounds)

/** Kết cục của một yêu cầu khung. */
enum class CastResizeResult {
    /** Lệnh shell đã áp (bản ghim của phiên vừa đổi theo). */
    APPLIED,

    /** Đã gửi nhưng hỏng (`am task resize` bị từ chối, không thấy task…) — màn trả số về sự thật + báo một dòng. */
    FAILED,

    /** Không chạy: bị yêu cầu khác/Dừng đẩy khỏi hàng, hoặc trạng thái chiếu không còn khớp ô — màn chỉ đồng bộ lại, im lặng. */
    DROPPED,
}

data class CastResizeOutcome(val request: CastResizeRequest, val result: CastResizeResult)

class CastResizeQueue(
    /** Xếp một việc lên executor nối tiếp của coordinator; `false` = executor đã tắt. */
    submit: (tag: String, body: () -> Unit) -> Boolean,
    /** Áp MỘT yêu cầu (chạy trên luồng executor). */
    run: (CastResizeRequest) -> CastResizeResult,
    /** Việc xả chờ lâu hơn mức này coi như đã bị executor huỷ vì hết hạn (= hạn thao tác `castTimeoutMs`). */
    staleMs: Long,
    nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    log: (String) -> Unit = {},
) {
    // 3.1 · R6 — thân gộp tách sang [CastCoalescingQueue] (dùng chung với bước "Dịch nội dung"); luật + chữ log giữ nguyên.
    private val queue = CastCoalescingQueue<ClusterSlotSide?, CastResizeRequest, CastResizeResult>(
        TAG, { it.side }, submit, run, CastResizeResult.FAILED, CastResizeResult.DROPPED, staleMs, nowMs, log,
        describe = { "${it.bounds} (side=${it.side})" },
    )

    /** Gửi yêu cầu [request]; trả `false` khi executor đã tắt (yêu cầu nhận kết cục DROPPED). */
    fun offer(request: CastResizeRequest): Boolean = queue.offer(request)

    /** Ô [side] còn yêu cầu đang chờ hoặc đang áp — màn chưa nên đồng bộ số về bản ghim (sự thật sắp đổi). */
    fun busy(side: ClusterSlotSide?): Boolean = queue.busy(side)

    /** Nối từ `BoundedCastExecutor.onDiscard`: việc xả của hàng này bị bỏ khỏi hàng ⇒ mọi yêu cầu chờ thành DROPPED. */
    fun dropped(tag: String) = queue.dropped(tag)

    /** Nghe kết cục từng yêu cầu (gọi trên luồng executor). Trả hàm gỡ. */
    fun addListener(listener: (CastResizeOutcome) -> Unit): () -> Unit =
        queue.addListener { request, result -> listener(CastResizeOutcome(request, result)) }

    companion object {
        /** Thẻ của việc xả trên executor — [dropped] khớp đúng thẻ này. */
        const val TAG = "resize"
    }
}
