package com.byd.clusternav.modules.clustercast.simplified

import java.util.concurrent.CopyOnWriteArrayList

/**
 * ═══ Hàng GỘP chung của các bước −/+ khi đang chiếu (3.1 · R2/R3 khung · R6 dịch nội dung) ══════════════════════════════
 *
 * Thân của [CastResizeQueue] tách ra nguyên luật để bước **"Dịch nội dung"** (R6, `wm overscan`) dùng CHUNG một cơ chế gộp với
 * bốn thanh khung, không mở đường thứ hai (G4.1 #3). Luật (KDoc gốc ở [CastResizeQueue]):
 *  1. **Một ô chờ cho mỗi khoá** ([keyOf]) — yêu cầu mới THAY yêu cầu đang chờ của cùng khoá; chỉ giá trị CUỐI được áp.
 *  2. **Tối đa một việc "xả" chờ trên executor** — nhấn giữ bao lâu cũng chỉ một chỗ trong hàng.
 *  3. **Mọi yêu cầu có kết cục** — [run] trả kết cục; ném ⇒ [failedResult]; bị executor bỏ ([dropped]) / hết hạn khi chờ (quá
 *     [staleMs] ⇒ xếp lại) / executor tắt ⇒ [droppedResult]. Hàng không bao giờ kẹt vĩnh viễn.
 *
 * Tệp này KHÔNG chứa chuỗi lệnh shell nào — thân áp là [run] do coordinator cấp.
 */
class CastCoalescingQueue<K, Q, R>(
    /** Thẻ của việc xả trên executor — [dropped] khớp đúng thẻ này; cũng là tiền tố dòng log. */
    private val tag: String,
    private val keyOf: (Q) -> K,
    /** Xếp một việc lên executor nối tiếp của coordinator; `false` = executor đã tắt. */
    private val submit: (tag: String, body: () -> Unit) -> Boolean,
    /** Áp MỘT yêu cầu (chạy trên luồng executor). */
    private val run: (Q) -> R,
    private val failedResult: R,
    private val droppedResult: R,
    /** Việc xả chờ lâu hơn mức này coi như đã bị executor huỷ vì hết hạn (= hạn thao tác `castTimeoutMs`). */
    private val staleMs: Long,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val log: (String) -> Unit = {},
    private val describe: (Q) -> String = { it.toString() },
) {
    private val lock = Any()
    private val pending = LinkedHashMap<K, Q>()
    private val running = HashSet<K>()
    private var drainQueuedAt: Long? = null
    private val listeners = CopyOnWriteArrayList<(Q, R) -> Unit>()

    /** Gửi yêu cầu [request]; trả `false` khi executor đã tắt (yêu cầu nhận kết cục [droppedResult]). */
    fun offer(request: Q): Boolean {
        val schedule = synchronized(lock) {
            pending.put(keyOf(request), request)?.let { log("$tag: gộp — ${describe(it)} → ${describe(request)}") }
            val at = drainQueuedAt
            val now = nowMs()
            if (at == null || now - at > staleMs) {
                if (at != null) log("$tag: việc xả chờ quá $staleMs ms (đã bị huỷ?) ⇒ xếp lại")
                drainQueuedAt = now
                true
            } else {
                false
            }
        }
        if (!schedule) return true
        if (submit(tag) { drain() }) return true
        log("$tag: executor đã tắt — bỏ yêu cầu")
        dropQueued()
        return false
    }

    /** Khoá [key] còn yêu cầu đang chờ hoặc đang áp — màn chưa nên đồng bộ số về sự thật (sự thật sắp đổi). */
    fun busy(key: K): Boolean = synchronized(lock) { key in pending || key in running }

    /** Nối từ `BoundedCastExecutor.onDiscard`: việc xả của hàng này bị bỏ khỏi hàng ⇒ mọi yêu cầu chờ thành [droppedResult]. */
    fun dropped(tag: String) {
        if (tag == this.tag) dropQueued()
    }

    /** Nghe kết cục từng yêu cầu (gọi trên luồng executor). Trả hàm gỡ. */
    fun addListener(listener: (Q, R) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    private fun dropQueued() {
        val lost = synchronized(lock) {
            if (drainQueuedAt == null) return
            drainQueuedAt = null
            pending.values.toList().also { pending.clear() }
        }
        lost.forEach { emit(it, droppedResult) }
    }

    private fun drain() {
        val batch = synchronized(lock) {
            drainQueuedAt = null
            pending.values.toList().also { list ->
                pending.clear()
                list.forEach { running.add(keyOf(it)) }
            }
        }
        var done = 0
        try {
            for (request in batch) {
                var result = failedResult
                try {
                    result = run(request)
                } catch (e: RuntimeException) {
                    // Không nuốt: log + kết cục hỏng (màn báo người lái). `Error`/ngắt luồng (hết hạn) vẫn bay lên executor.
                    log("$tag: áp ${describe(request)} ném ${e.javaClass.simpleName}: ${e.message}")
                } finally {
                    synchronized(lock) { running.remove(keyOf(request)) }
                }
                done++
                emit(request, result)
            }
        } finally {
            // Bị ngắt giữa chừng (executor huỷ vì hết hạn): phần còn lại của lô không bao giờ chạy — nhả cờ "đang áp" và báo
            // bỏ, không thì [busy] kẹt `true` mãi và màn không bao giờ đồng bộ lại số.
            val left = batch.drop(done)
            synchronized(lock) { left.forEach { running.remove(keyOf(it)) } }
            left.forEach { emit(it, droppedResult) }
        }
    }

    private fun emit(request: Q, result: R) {
        listeners.forEach { l ->
            try {
                l(request, result)
            } catch (e: RuntimeException) {
                log("$tag: người nghe kết cục ném ${e.javaClass.simpleName}")
            }
        }
    }
}
