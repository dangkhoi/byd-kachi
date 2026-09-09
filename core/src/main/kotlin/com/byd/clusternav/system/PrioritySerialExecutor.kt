package com.byd.clusternav.system

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.RunnableFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Executor MỘT-WORKER chạy từng body ĐÚNG MỘT-TẠI-MỘT-THỜI-ĐIỂM (serialize), NHƯNG rút backlog theo
 * [MutationPriority]: một body [MutationPriority.STOP]/[MutationPriority.RESCUE] nạp vào KHI worker đang bận sẽ
 * VƯỢT LÊN TRƯỚC mọi body [MutationPriority.NORMAL] đang chờ. Trong CÙNG một mức ưu tiên → giữ FIFO (đúng thứ
 * tự nạp), nên một chuỗi lệnh cùng mức (vd reflow: closeSlot → openInSlot…) KHÔNG bao giờ bị đảo.
 *
 * ── VÌ SAO (Stage B2b) ─────────────────────────────────────────────────────────────────────────────────────
 * B1 gộp MỌI lệnh cửa sổ (launcher + cast) về MỘT `ShellTransport` (một owner thread). Nếu owner đó rút theo
 * FIFO thuần thì một cast STOP có thể KẸT sau một chuỗi lệnh launcher NORMAL đã xếp trước → regress an toàn
 * (handoff B1 cấm hạ về FIFO). Đây là hàng đợi ưu tiên hợp nhất mà ShellTransport dùng: one-at-a-time NHƯNG
 * STOP/RESCUE rút trước NORMAL.
 *
 * ── one-at-a-time + ưu tiên CÙNG TỒN TẠI như thế nào ────────────────────────────────────────────────────────
 * [ThreadPoolExecutor] core=max=1 (đúng MỘT worker → serialize) + [PriorityBlockingQueue] (rút theo thứ tự
 * [Comparable]). `newTaskFor` bọc mỗi [Callable] thành [PriorityTask] so sánh theo (drainRank, seq): rank nhỏ
 * rút trước; cùng rank thì seq nhỏ (nạp trước) rút trước → FIFO trong-mức. `prestartCoreThread` để CẢ task đầu
 * tiên cũng đi qua hàng đợi (worker tồn tại sẵn) → bất biến "mọi task đi qua hàng đợi ưu tiên" luôn đúng.
 *
 * [submit] CHẶN cho tới khi body xong ([Future.get]) và mở-bọc [ExecutionException] về đúng exception gốc —
 * y hệt hợp đồng `owner.submit(Callable).get()` mà ShellTransport dùng trước B2b (chỉ đổi FIFO → ưu tiên).
 *
 * Thuần JVM (:core) — chỉ `java.util.concurrent` + [MutationPriority]; KHÔNG android.*, KHÔNG dadb. `submit` an
 * toàn khi gọi từ NHIỀU thread; KHÔNG gọi từ chính worker thread (không có ai tự-nạp nên không tự-deadlock).
 */
class PrioritySerialExecutor(threadName: String = "kachi-priority-shell") {

    /** Cấp thứ tự nạp tăng dần → FIFO tie-break trong cùng một mức ưu tiên. */
    private val seq = AtomicLong(0)

    /** Đúng MỘT worker (core=max=1) rút từ hàng đợi ƯU TIÊN → serialize + ưu tiên cùng lúc. */
    private val pool = object : ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        PriorityBlockingQueue<Runnable>(),
        ThreadFactory { r -> Thread(r, threadName).apply { isDaemon = true } },
    ) {
        override fun <T : Any?> newTaskFor(callable: Callable<T>): RunnableFuture<T> {
            val rank = (callable as? Ranked)?.rank ?: MutationPriority.NORMAL.drainRank
            return PriorityTask(callable, rank, seq.getAndIncrement())
        }
    }

    init {
        // Worker tồn tại sẵn → task ĐẦU TIÊN cũng đi qua hàng đợi (TPE chỉ bỏ qua hàng đợi khi CHƯA đủ core
        // thread). Nhờ vậy bất biến "mọi task đi qua PriorityBlockingQueue" luôn đúng ngay từ lệnh đầu.
        pool.prestartCoreThread()
    }

    /**
     * Nạp [body] ở mức [priority], CHẶN cho tới khi có kết quả và TRẢ kết quả đó. Serialize; body ưu tiên cao
     * (STOP/RESCUE) rút trước NORMAL đang chờ. [ExecutionException] được mở-bọc → ném đúng exception gốc.
     */
    fun <T> submit(priority: MutationPriority, body: () -> T): T = try {
        pool.submit(RankedCallable(priority.drainRank, body)).get()
    } catch (e: ExecutionException) {
        throw (e.cause ?: e)
    }

    /** Số task đang CHỜ trong hàng đợi (KHÔNG kể task đang chạy). Chủ yếu cho test xác nhận thứ tự nạp. */
    fun pendingCount(): Int = pool.queue.size

    /** true nếu không có task đang chạy và hàng đợi rỗng. */
    val isIdle: Boolean get() = pool.queue.isEmpty() && pool.activeCount == 0

    /** Dừng worker (huỷ task đang chạy nếu có). Gọi khi teardown. */
    fun shutdown() {
        pool.shutdownNow()
    }

    /** Đánh dấu một [Callable] mang mức ưu tiên để `newTaskFor` đọc ra [rank]. */
    private interface Ranked {
        val rank: Int
    }

    private class RankedCallable<T>(override val rank: Int, private val body: () -> T) : Callable<T>, Ranked {
        override fun call(): T = body()
    }

    /**
     * [FutureTask] có thể SO SÁNH cho [PriorityBlockingQueue]: rút theo (rank, order) tăng dần — rank nhỏ (ưu
     * tiên cao) trước; cùng rank thì order nhỏ (nạp trước) trước → FIFO trong-mức.
     */
    private class PriorityTask<T>(
        callable: Callable<T>,
        private val rank: Int,
        private val order: Long,
    ) : FutureTask<T>(callable), Comparable<PriorityTask<*>> {
        override fun compareTo(other: PriorityTask<*>): Int {
            val byRank = rank.compareTo(other.rank)
            return if (byRank != 0) byRank else order.compareTo(other.order)
        }
    }
}
