package com.byd.clusternav.modules.clustercast.simplified

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Bounded executor for cast operations.
 *
 * Guarantees:
 * - At most 1 active operation + 1 pending operation (bounded queue capacity = 1).
 * - New submit when queue full → oldest pending is dropped (latest intent wins) — 2.96 · R5: mỗi lần bỏ đều báo [onDiscard].
 * - Background probes go through [submitIfIdle] (2.96 · R5): enqueued only when nothing runs/waits ⇒ never evict a user intent.
 * - [submitStop] is PRIORITY: cancels active + clears pending, then executes stop immediately.
 * - Every operation has a hard deadline. On timeout: [Future.cancel(true)] + callback.
 * - Dedicated thread (daemon) — does not block the main/UI thread.
 *
 * 2.93 · CAST-OPEN-TIMEOUT — hạn của MỖI thao tác đi kèm thao tác đó: [submit] nhận hạn riêng (lượt mở chiếu dùng
 * [OPEN_TIMEOUT_MS]), và thân đang chạy hỏi được phần hạn còn lại qua [remainingMs] (cổng theme dùng nó để không chờ bóng
 * nổi ẩn lâu tới mức lượt mở chạm hạn cứng — `ClusterThemeGuard`).
 *
 * Not a generic executor — purpose-built for cast safety.
 */
class BoundedCastExecutor(
    private val castTimeoutMs: Long = 15_000L,
    private val stopTimeoutMs: Long = 5_000L,
    private val onTimeout: ((String) -> Unit)? = null,
    /**
     * Gọi khi block ném exception. Mặc định in ra stderr — KHÔNG được nuốt im lặng: một lần nuốt là
     * state machine kẹt ở Opening/Stopping mà không ai biết (không có Error, UI không nhả).
     */
    private val onFailure: ((String, Throwable) -> Unit)? = null,
    /**
     * 2.96 · R5 — gọi MỖI khi một thao tác CHỜ bị bỏ khỏi hàng mà không bao giờ chạy: `(tag bị bỏ, lý do)`. [ĐO log xe 07/10
     * 20:48:55] `split right: vn.vietmap.live` rồi 0 lệnh `am start` — bị `DiscardOldestPolicy` vứt IM LẶNG; từ nay mọi lần bỏ đều
     * có dấu. Mặc định `null` = không báo.
     */
    private val onDiscard: ((String, String) -> Unit)? = null,
) {
    /** Thao tác mang tên — để lượt bỏ khỏi hàng ([DiscardOldestLogged], [submitStop]) nói được CÁI GÌ bị bỏ. */
    private class TaggedTask(val tag: String, body: Callable<Unit>) : FutureTask<Unit>(body)

    /**
     * Như `DiscardOldestPolicy` (hàng đầy ⇒ bỏ thao tác chờ CŨ nhất, thao tác mới vào — "ý định mới nhất thắng", hành vi cũ giữ
     * nguyên) nhưng huỷ + BÁO thao tác bị bỏ ([onDiscard]). Huỷ (`cancel(false)`) để `Future` của nó xong hẳn — bộ hẹn giờ của
     * nó thấy `isDone` và không báo `TIMEOUT` giả.
     */
    private inner class DiscardOldestLogged : RejectedExecutionHandler {
        override fun rejectedExecution(r: Runnable, e: ThreadPoolExecutor) {
            if (e.isShutdown) return
            val dropped = e.queue.poll()
            (dropped as? Future<*>)?.cancel(false)
            if (dropped != null) reportDiscard(dropped, "bị thay bởi ${(r as? TaggedTask)?.tag ?: "?"} (hàng 1 chỗ)")
            e.execute(r)
        }
    }

    private fun reportDiscard(dropped: Runnable, why: String) {
        val tag = (dropped as? TaggedTask)?.tag ?: "?"
        try {
            onDiscard?.invoke(tag, why)
        } catch (e: RuntimeException) {
            System.err.println("BoundedCastExecutor: onDiscard ném ${e.javaClass.simpleName} cho $tag")
        }
    }

    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(1), // capacity 1 = at most 1 pending
        ThreadFactory { r -> Thread(r, "CastBounded").apply { isDaemon = true } },
        DiscardOldestLogged(), // drop oldest pending on overflow — có báo (2.96 · R5)
    )

    /**
     * 2.96 · R5 — khoá MỌI lượt xếp hàng: [submitIfIdle] kiểm "rảnh" rồi xếp trong CÙNG khoá ⇒ không lệnh người dùng nào lọt vào
     * hàng giữa hai bước để rồi bị lượt dò nền đẩy ra. Luồng worker không cần khoá: nó chỉ làm hàng VƠI đi.
     */
    private val submitLock = Any()

    /** Handle to the most recently SUBMITTED future — may still be PENDING in the queue (cancelled by stop with the rest). */
    private val activeFuture = AtomicReference<Future<*>?>(null)

    /**
     * 2.93 · review CAST Pass 1 F2 — thao tác ĐANG CHẠY trên luồng. Khác [activeFuture] (thao tác gửi GẦN NHẤT): có thao tác chờ
     * thì [activeFuture] là thao tác chờ, và [submitStop] bản cũ chỉ huỷ nó — lượt mở đang chạy không bị ngắt.
     */
    private val runningFuture = AtomicReference<Future<*>?>(null)

    /**
     * Xếp [body] vào hàng; trong lúc chạy, [runningFuture] trỏ đúng thao tác này. [started] (nếu có) bật lên khi thân BẮT ĐẦU
     * chạy — thao tác bị bỏ khỏi hàng (DiscardOldest / Dừng dọn hàng) không bao giờ bật nó.
     */
    private fun enqueue(tag: String, started: AtomicBoolean? = null, body: () -> Unit): FutureTask<Unit> {
        val self = AtomicReference<FutureTask<Unit>>()
        val task = TaggedTask(tag, Callable {
            val me = self.get()
            runningFuture.set(me)
            started?.set(true)
            try {
                body()
            } finally {
                runningFuture.compareAndSet(me, null)
            }
        })
        self.set(task)
        synchronized(submitLock) { executor.execute(task) }
        return task
    }

    /** Whether shutdown has been called. */
    @Volatile
    private var isShutdown = false

    /**
     * Submit a normal cast operation (cast-full, cast-slot, resize, etc).
     * Bounded: if queue is full, oldest pending is dropped.
     * Returns false if executor is shutdown.
     *
     * @param timeoutMs hạn cứng của RIÊNG thao tác này, tính từ lúc gửi (bộ hẹn giờ bắt đầu ở đây, không phải lúc thao tác
     *   bắt đầu chạy). Mặc định = hạn chung `castTimeoutMs`.
     */
    fun submit(tag: String, timeoutMs: Long = castTimeoutMs, block: () -> Unit): Boolean {
        if (isShutdown) return false
        val deadline = nowMs() + timeoutMs
        val started = AtomicBoolean(false)
        val future = enqueue(tag, started) { runGuarded(tag, deadline, block) }
        armTimeout(tag, future, started, timeoutMs)
        return true
    }

    /**
     * 2.96 · R5 — gửi một thao tác NỀN (lượt dò định kỳ — watchdog repin) CHỈ khi executor rảnh hẳn: không thao tác nào đang chạy
     * VÀ hàng trống. Bận ⇒ KHÔNG xếp, trả `false` (bên gọi thử lại nhịp sau).
     *
     * Vì sao ([ĐO log xe 07/10 20:48:55] + [ĐO mã]): hàng 1 chỗ + bỏ-cũ-nhất ⇒ một lượt dò nền gửi khi lệnh chiếu nửa PHẢI đang
     * CHỜ (sau nửa TRÁI đang chạy) đẩy lệnh đó ra khỏi hàng — VietMap không bao giờ được chiếu. Lượt nền không bao giờ được thắng ý
     * định của người dùng/tự chiếu. Kiểm-rồi-xếp nằm trong [submitLock] (cùng khoá với [submit]/[submitStop]) ⇒ không có khe để
     * một lệnh người dùng vào hàng giữa lúc kiểm và lúc xếp. Chiều ngược giữ như cũ: lệnh người dùng tới sau đẩy được lượt nền
     * còn chờ (có báo [onDiscard]).
     */
    fun submitIfIdle(tag: String, timeoutMs: Long = castTimeoutMs, block: () -> Unit): Boolean {
        if (isShutdown) return false
        val deadline = nowMs() + timeoutMs
        val started = AtomicBoolean(false)
        val future = synchronized(submitLock) {
            if (!idleNow()) return false
            enqueue(tag, started) { runGuarded(tag, deadline, block) }
        }
        armTimeout(tag, future, started, timeoutMs)
        return true
    }

    /**
     * Rảnh hẳn: [isIdle] (hàng trống + không worker nào đang chạy, kể cả thao tác đã bị huỷ mà thân còn chạy nốt) VÀ
     * [runningFuture] đã trả về `null` — một phép "rảnh" duy nhất (soát 2.96 Pass 1 [P3]: không giữ hai bản chép của cùng điều kiện).
     */
    private fun idleNow(): Boolean = isIdle && runningFuture.get() == null

    private fun armTimeout(tag: String, future: Future<*>, started: AtomicBoolean, timeoutMs: Long) {
        activeFuture.set(future)
        TIMEOUT_SCHEDULER.schedule({
            if (!future.isDone) {
                future.cancel(true)
                // Review CAST Pass 1 F4: thao tác bị bỏ khỏi hàng (chưa từng chạy) không được báo `TIMEOUT` giả — log hiện trường
                // chỉ được có `TIMEOUT: <tag>` khi thao tác ấy THẬT SỰ chạy quá hạn.
                if (started.get()) onTimeout?.invoke(tag)
            }
        }, timeoutMs, TimeUnit.MILLISECONDS)
    }

    /**
     * Submit a STOP operation with priority:
     * 1. Cancel the active operation (interrupt).
     * 2. Purge any pending from queue.
     * 3. Execute stop immediately (bypasses normal queue).
     *
     * Stop has its own (shorter) timeout.
     */
    fun submitStop(tag: String, block: () -> Unit): Boolean {
        if (isShutdown) return false
        // 1. Ngắt thao tác ĐANG CHẠY + huỷ thao tác gửi gần nhất (có thể đang chờ). Review CAST Pass 1 F2: bản cũ chỉ huỷ
        //    [activeFuture] — có thao tác chờ thì đó là thao tác CHỜ, lượt mở đang chạy không bị ngắt.
        runningFuture.get()?.cancel(true)
        activeFuture.getAndSet(null)?.cancel(true)
        // 2. Purge pending — 2.96 · R5: mỗi thao tác bị dọn đều có dấu (Dừng/Đóng có quyền trước — hành vi không đổi).
        val purged = ArrayList<Runnable>()
        synchronized(submitLock) { executor.queue.drainTo(purged) }
        for (r in purged) {
            (r as? Future<*>)?.cancel(false)
            reportDiscard(r, "dọn hàng vì $tag (ưu tiên)")
        }
        // 3. Dừng: hạn tính từ lúc nó BẮT ĐẦU CHẠY (review CAST Pass 1 F2) — nó có thể phải đợi lệnh shell không ngắt được của
        //    thao tác trước chạy nốt (mỗi lệnh có hạn đọc riêng); tính từ lúc gửi (bản cũ) thì Dừng bị huỷ trước khi kịp chạy.
        val future = enqueue(tag) {
            val me = runningFuture.get()
            TIMEOUT_SCHEDULER.schedule({
                if (me != null && !me.isDone) {
                    me.cancel(true)
                    onTimeout?.invoke(tag)
                }
            }, stopTimeoutMs, TimeUnit.MILLISECONDS)
            runGuarded(tag, nowMs() + stopTimeoutMs, block)
        }
        activeFuture.set(future)
        return true
    }

    /**
     * Chạy [block] và KHÔNG để exception biến mất vào `Future` mà không ai `get()`.
     * `InterruptedException` (do timeout/stop `cancel(true)`) là đường bình thường ⇒ chỉ đặt lại cờ interrupt.
     * [deadline] (`null` = việc hẹn giờ, không có hạn thao tác) được treo trên luồng trong lúc [block] chạy — [remainingMs].
     */
    private fun runGuarded(tag: String, deadline: Long?, block: () -> Unit) {
        OP_DEADLINE.set(deadline)
        try {
            block()
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (t: Throwable) {
            if (onFailure != null) onFailure.invoke(tag, t) else t.printStackTrace()
            if (t is Error) throw t
        } finally {
            OP_DEADLINE.remove()
        }
    }

    /**
     * Hẹn giờ trên scheduler riêng (KHÔNG chiếm worker duy nhất).
     * Dùng cho việc "chờ rồi làm" — nếu xếp vào [submit] thì `Thread.sleep` sẽ khoá hàng đợi
     * (sức chứa 1 + DiscardOldestPolicy ⇒ lệnh chiếu kế tiếp của người dùng bị âm thầm vứt).
     */
    fun schedule(delayMs: Long, block: () -> Unit) {
        if (isShutdown) return
        TIMEOUT_SCHEDULER.schedule({
            if (!isShutdown) runGuarded("scheduled", null, block)
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    /** Drain queue and shut down. Blocks up to 2s for active operation to finish. */
    fun shutdown() {
        isShutdown = true
        activeFuture.getAndSet(null)?.cancel(true)
        executor.shutdownNow()
        executor.awaitTermination(2, TimeUnit.SECONDS)
    }

    /**
     * True if no operation is active and queue is empty. 2.93 · READY-RESTART-MID-CAST: thao tác đã bị `cancel(true)` (quá hạn)
     * mà thân còn chạy nốt (lệnh shell không ngắt được) vẫn tính là BẬN — đúng sự thật: luồng còn đang gửi lệnh tới cụm.
     */
    val isIdle: Boolean
        get() = executor.queue.isEmpty() && executor.activeCount == 0

    companion object {
        /** Shared scheduler for timeout watchers (lightweight — only fires timers). */
        private val TIMEOUT_SCHEDULER = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "CastTimeout").apply { isDaemon = true }
        }

        /**
         * 2.93 · CAST-OPEN-TIMEOUT — hạn cứng RIÊNG của lượt mở chiếu (các thao tác khác giữ hạn chung 15 s).
         *
         * [ĐO log xe 06/10 13:49 · 15:13] `TIMEOUT: openProjection` hai lần. [SUY từ log xe cùng ngày: lệnh shell ≈ 0,27 s/lệnh —
         * 12 lần đọc của lượt "dọn cụm" mất ~3,3 s ngoài 1,5 s ngủ; khoảng `theme 31 → SEND` ↔ `16` = 2,1 s] chuỗi mở ở mức B
         * (màn ảo cụm có sẵn) khi phải dọn bóng nổi + gỡ ClusterBlack mồ côi rồi gửi theme, MỖI lượt chờ chỉ MỘT lần đọc lại:
         * ~5 lệnh trước cổng + 12 lệnh của cổng + ~13 lệnh đuôi ≈ 30 lệnh + 6,5 s ngủ cố định (2 + 2 + 1 sau opcode, 1 sau
         * ClusterBlack, 2 × 0,25 s) ≈ 14,6 s ở 0,27 s/lệnh — sát 15 s ngay cả khi mọi thứ thuận; ≈ 21,5 s ở 0,5 s/lệnh (lúc nổ máy,
         * [ĐO usage-cycle2 29/09] 0,2–0,5 s). 25 s phủ ca đó. Hạn này chỉ chặn một thao tác treo VĨNH VIỄN (mỗi lệnh shell đã có
         * hạn đọc 10 s của `ShellTransport`). Dừng ([submitStop]) ngắt thao tác ĐANG CHẠY (kể cả khi có thao tác chờ sau nó) và
         * tính hạn của chính nó từ lúc nó bắt đầu chạy — review 2.93 CAST Pass 1 F2 (lỗi có từ trước, hạn 25 s làm nặng thêm).
         */
        const val OPEN_TIMEOUT_MS: Long = 25_000L

        /** Hạn tuyệt đối (đồng hồ đơn điệu, ms) của thao tác đang chạy trên luồng NÀY; `null` = không trong thao tác nào. */
        private val OP_DEADLINE = ThreadLocal<Long?>()

        private fun nowMs(): Long = System.nanoTime() / 1_000_000L

        /**
         * Phần hạn còn lại (ms, có thể âm) của thao tác executor đang chạy trên luồng gọi; `null` khi luồng gọi không ở trong
         * thao tác nào của một [BoundedCastExecutor] (test gọi thẳng, luồng chẩn đoán) ⇒ bên gọi coi như không giới hạn.
         */
        fun remainingMs(): Long? = OP_DEADLINE.get()?.let { it - nowMs() }
    }
}
