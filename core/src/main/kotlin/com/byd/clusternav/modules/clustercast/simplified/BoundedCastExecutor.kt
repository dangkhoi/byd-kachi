package com.byd.clusternav.modules.clustercast.simplified

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Bounded executor for cast operations.
 *
 * Guarantees:
 * - At most 1 active operation + 1 pending operation (bounded queue capacity = 1).
 * - New submit when queue full → oldest pending is dropped (latest intent wins).
 * - [submitStop] is PRIORITY: cancels active + clears pending, then executes stop immediately.
 * - Every operation has a hard deadline. On timeout: [Future.cancel(true)] + callback.
 * - Dedicated thread (daemon) — does not block the main/UI thread.
 *
 * Not a generic executor — purpose-built for cast safety.
 */
class BoundedCastExecutor(
    private val castTimeoutMs: Long = 15_000L,
    private val stopTimeoutMs: Long = 5_000L,
    private val onTimeout: ((String) -> Unit)? = null,
) {
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(1), // capacity 1 = at most 1 pending
        ThreadFactory { r -> Thread(r, "CastBounded").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(), // drop oldest pending on overflow
    )

    /** Handle to the currently executing future (for cancellation by stop). */
    private val activeFuture = AtomicReference<Future<*>?>(null)

    /** Whether shutdown has been called. */
    @Volatile
    private var isShutdown = false

    /**
     * Submit a normal cast operation (cast-full, cast-slot, resize, etc).
     * Bounded: if queue is full, oldest pending is dropped.
     * Returns false if executor is shutdown.
     */
    fun submit(tag: String, block: () -> Unit): Boolean {
        if (isShutdown) return false
        val future = executor.submit {
            block()
        }
        activeFuture.set(future)
        TIMEOUT_SCHEDULER.schedule({
            if (!future.isDone) {
                future.cancel(true)
                onTimeout?.invoke(tag)
            }
        }, castTimeoutMs, TimeUnit.MILLISECONDS)
        return true
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
        // 1. Cancel active
        activeFuture.getAndSet(null)?.cancel(true)
        // 2. Purge pending
        executor.queue.clear()
        // 3. Execute stop — goes to front since queue is now empty
        val future = executor.submit {
            block()
        }
        activeFuture.set(future)
        TIMEOUT_SCHEDULER.schedule({
            if (!future.isDone) {
                future.cancel(true)
                onTimeout?.invoke(tag)
            }
        }, stopTimeoutMs, TimeUnit.MILLISECONDS)
        return true
    }

    /** Drain queue and shut down. Blocks up to 2s for active operation to finish. */
    fun shutdown() {
        isShutdown = true
        activeFuture.getAndSet(null)?.cancel(true)
        executor.shutdownNow()
        executor.awaitTermination(2, TimeUnit.SECONDS)
    }

    /** True if no operation is active and queue is empty. */
    val isIdle: Boolean
        get() = executor.queue.isEmpty() && executor.activeCount == 0

    companion object {
        /** Shared scheduler for timeout watchers (lightweight — only fires timers). */
        private val TIMEOUT_SCHEDULER = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "CastTimeout").apply { isDaemon = true }
        }
    }
}
