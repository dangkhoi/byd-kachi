package com.byd.clusternav.navigation

import com.byd.clusternav.contracts.FreshnessState
import com.byd.clusternav.contracts.SpeedLimitClearReason
import com.byd.clusternav.contracts.SpeedLimitFrame
import com.byd.clusternav.contracts.SpeedLimitSource
import com.byd.clusternav.contracts.SpeedLimitType
import com.byd.clusternav.contracts.SpeedSignType
import com.byd.clusternav.contracts.SpeedUnit
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

fun interface SpeedSignScheduledTask {
    fun cancel()
}

fun interface SpeedSignScheduler {
    fun schedule(delayMs: Long, action: () -> Unit): SpeedSignScheduledTask
}

data class SpeedSignLifecycleSnapshot(
    val processEpoch: Long?,
    val generation: Long,
    val selectedSource: SpeedLimitSource,
    val masterEnabled: Boolean,
    val enabledOutputs: Set<SpeedSignOutput>,
    val activeFrame: SpeedLimitFrame?,
)

/**
 * Owns speed-sign source selection and stale-clear truth only. Vehicle encoding is intentionally
 * absent. Both outputs have independent port generations; a priority clear invalidates queued older
 * positives without waiting for, or backpressuring, the peer output.
 */
class SpeedSignLifecycleCoordinator(
    clusterPort: SpeedSignPort,
    hudPort: SpeedSignPort,
    private val monotonicNowMs: () -> Long,
    scheduler: SpeedSignScheduler? = null,
    val ttlMs: Long = 5_000L,
) : AutoCloseable {
    private data class PortState(
        val port: SpeedSignPort,
        var enabled: Boolean = false,
        var generation: Long = 0L,
        var clearLatched: Boolean = true,
    )

    private val lock = Any()
    private val ownedScheduler = scheduler == null
    private val executorScheduler = if (ownedScheduler) ExecutorScheduler() else null
    private val scheduler = scheduler ?: executorScheduler!!
    private val ports = linkedMapOf(
        SpeedSignOutput.CLUSTER to PortState(clusterPort),
        SpeedSignOutput.HUD to PortState(hudPort),
    )

    private var processEpoch: Long? = null
    private var generation = 0L
    private var sequence = 0L
    private var selectedSource = SpeedLimitSource.NONE
    private var masterEnabled = false
    private var activeFrame: SpeedLimitFrame? = null
    private var lastObservedAtMs = -1L
    private var expiryTask: SpeedSignScheduledTask? = null
    private var closed = false

    init {
        require(clusterPort.output == SpeedSignOutput.CLUSTER) { "clusterPort has wrong output" }
        require(hudPort.output == SpeedSignOutput.HUD) { "hudPort has wrong output" }
        require(ttlMs == 5_000L) { "speed-sign TTL is fixed at 5000ms" }
    }

    /** A new process epoch force-clears both NOOP/recording ports before accepting source values. */
    fun onProcessRestart(epoch: Long): Boolean = synchronized(lock) {
        require(epoch >= 0L) { "process epoch must be non-negative" }
        val previous = processEpoch
        if (previous != null && epoch <= previous) return false
        val clearSource = activeFrame?.source ?: selectedSource
        cancelExpiryLocked()
        generation = increment(generation, "generation")
        processEpoch = epoch
        activeFrame = null
        selectedSource = SpeedLimitSource.NONE
        masterEnabled = false
        lastObservedAtMs = -1L
        ports.values.forEach { it.enabled = false }
        clearLocked(SpeedLimitClearReason.PROCESS_RESTARTED, clearSource, ports.values.toList(), force = true)
        true
    }

    fun onMasterEnabled(enabled: Boolean): Unit = synchronized(lock) {
        ensureOpenAndEpochLocked()
        if (masterEnabled == enabled) return
        masterEnabled = enabled
        if (!enabled) invalidateAndClearLocked(SpeedLimitClearReason.MASTER_DISABLED, selectedSource)
    }

    fun onSourceSelected(source: SpeedLimitSource): Unit = synchronized(lock) {
        ensureOpenAndEpochLocked()
        require(source != SpeedLimitSource.NONE) { "a real source must be selected" }
        if (selectedSource == source) return
        val previous = activeFrame?.source ?: selectedSource
        selectedSource = source
        lastObservedAtMs = -1L
        invalidateAndClearLocked(SpeedLimitClearReason.SOURCE_SWITCHED, previous)
    }

    fun onOutputEnabled(output: SpeedSignOutput, enabled: Boolean): Unit = synchronized(lock) {
        ensureOpenAndEpochLocked()
        val state = checkNotNull(ports[output])
        if (state.enabled == enabled) return
        state.enabled = enabled
        if (!enabled) {
            clearPortLocked(state, SpeedLimitClearReason.OUTPUT_DISABLED, activeFrame?.source ?: selectedSource)
            return
        }
        val current = activeFrame ?: return
        if (!masterEnabled || !current.freshness.isFreshAt(monotonicNowMs())) return
        val replay = current.copy(sequence = nextSequenceLocked())
        activeFrame = replay
        if (publishLocked(state, replay) == SpeedSignSubmission.QUEUE_SATURATED) {
            invalidateAndClearLocked(SpeedLimitClearReason.QUEUE_SATURATED, current.source, force = true)
        } else {
            cancelExpiryLocked()
            scheduleExpiryLocked(replay, generation)
        }
    }

    /** Returns false for a stale process callback, foreign source, regressing timestamp, or disabled master. */
    fun onSpeedLimit(
        source: SpeedLimitSource,
        valueKph: Int,
        observedAtMonotonicMs: Long,
        callbackProcessEpoch: Long,
    ): Boolean = synchronized(lock) {
        ensureOpenAndEpochLocked()
        require(source != SpeedLimitSource.NONE) { "source must be WAZE or VIETMAP" }
        require(observedAtMonotonicMs >= 0L) { "observation time must be non-negative" }
        if (callbackProcessEpoch != processEpoch || source != selectedSource ||
            observedAtMonotonicMs < lastObservedAtMs || !masterEnabled
        ) return false
        lastObservedAtMs = observedAtMonotonicMs
        if (valueKph <= 0) {
            invalidateAndClearLocked(SpeedLimitClearReason.ZERO_VALUE, source)
            return true
        }

        cancelExpiryLocked()
        generation = increment(generation, "generation")
        val frame = SpeedLimitFrame.active(
            value = valueKph,
            signType = SpeedSignType.UNKNOWN,
            limitType = SpeedLimitType.UNKNOWN,
            unit = SpeedUnit.KPH,
            source = source,
            sequence = nextSequenceLocked(),
            observedAtMonotonicMs = observedAtMonotonicMs,
            validUntilMonotonicMs = Math.addExact(observedAtMonotonicMs, ttlMs),
        )
        activeFrame = frame
        var saturated = false
        ports.values.filter { it.enabled }.forEach { state ->
            saturated = publishLocked(state, frame) == SpeedSignSubmission.QUEUE_SATURATED || saturated
        }
        if (saturated) {
            invalidateAndClearLocked(SpeedLimitClearReason.QUEUE_SATURATED, source, force = true)
        } else {
            scheduleExpiryLocked(frame, generation)
        }
        true
    }

    fun onProviderDisconnected(source: SpeedLimitSource): Unit = synchronized(lock) {
        ensureOpenAndEpochLocked()
        if (source == selectedSource) invalidateAndClearLocked(SpeedLimitClearReason.PROVIDER_DISCONNECTED, source)
    }

    fun onSourceStopped(source: SpeedLimitSource): Unit = synchronized(lock) {
        ensureOpenAndEpochLocked()
        if (source == selectedSource) invalidateAndClearLocked(SpeedLimitClearReason.SOURCE_STOPPED, source)
    }

    fun currentProcessEpoch(): Long = synchronized(lock) { checkNotNull(processEpoch) }

    fun snapshot(): SpeedSignLifecycleSnapshot = synchronized(lock) {
        SpeedSignLifecycleSnapshot(
            processEpoch = processEpoch,
            generation = generation,
            selectedSource = selectedSource,
            masterEnabled = masterEnabled,
            enabledOutputs = ports.filterValues { it.enabled }.keys.toSet(),
            activeFrame = activeFrame,
        )
    }

    override fun close() {
        val toClose = synchronized(lock) {
            if (closed) return
            closed = true
            cancelExpiryLocked()
            ports.values.map { it.port }
        }
        toClose.forEach { runCatching { it.close() } }
        executorScheduler?.close()
    }

    private fun publishLocked(state: PortState, frame: SpeedLimitFrame): SpeedSignSubmission {
        val result = try {
            state.port.publish(frame, state.generation)
        } catch (_: RuntimeException) {
            SpeedSignSubmission.QUEUE_SATURATED
        }
        if (result == SpeedSignSubmission.ACCEPTED) state.clearLatched = false
        return result
    }

    private fun invalidateAndClearLocked(
        reason: SpeedLimitClearReason,
        source: SpeedLimitSource,
        force: Boolean = false,
    ) {
        cancelExpiryLocked()
        activeFrame = null
        generation = increment(generation, "generation")
        clearLocked(reason, source, ports.values.toList(), force = force)
    }

    private fun clearPortLocked(state: PortState, reason: SpeedLimitClearReason, source: SpeedLimitSource) {
        clearLocked(reason, source, listOf(state), force = false)
    }

    private fun clearLocked(
        reason: SpeedLimitClearReason,
        source: SpeedLimitSource,
        targets: List<PortState>,
        force: Boolean,
    ) {
        val pending = targets.filter { force || !it.clearLatched }
        if (pending.isEmpty()) return
        val now = monotonicNowMs().coerceAtLeast(lastObservedAtMs.coerceAtLeast(0L))
        val clear = SpeedLimitFrame.clear(
            unit = SpeedUnit.KPH,
            source = source,
            sequence = nextSequenceLocked(),
            observedAtMonotonicMs = now,
            reason = reason,
            state = if (reason == SpeedLimitClearReason.TTL_EXPIRED) FreshnessState.STALE else FreshnessState.UNAVAILABLE,
        )
        pending.forEach { state ->
            state.generation = increment(state.generation, "port generation")
            try {
                state.port.replaceWithClear(clear, state.generation)
            } catch (_: RuntimeException) {
                // The peer clear must still run. T7 ports are recording/NOOP and cannot throw.
            }
            state.clearLatched = true
        }
    }

    private fun scheduleExpiryLocked(frame: SpeedLimitFrame, expectedGeneration: Long) {
        val delay = (frame.freshness.validUntilMonotonicMs - monotonicNowMs()).coerceAtLeast(0L)
        expiryTask = scheduler.schedule(delay) { expire(frame, expectedGeneration) }
    }

    private fun expire(expected: SpeedLimitFrame, expectedGeneration: Long): Unit = synchronized(lock) {
        if (closed || generation != expectedGeneration || activeFrame !== expected) return
        expiryTask = null
        val now = monotonicNowMs()
        if (now < expected.freshness.validUntilMonotonicMs) {
            scheduleExpiryLocked(expected, expectedGeneration)
            return
        }
        invalidateAndClearLocked(SpeedLimitClearReason.TTL_EXPIRED, expected.source)
    }

    private fun cancelExpiryLocked() {
        expiryTask?.cancel()
        expiryTask = null
    }

    private fun nextSequenceLocked(): Long {
        sequence = increment(sequence, "sequence")
        return sequence
    }

    private fun ensureOpenAndEpochLocked() {
        check(!closed) { "coordinator is closed" }
        check(processEpoch != null) { "process epoch must be established first" }
    }

    private fun increment(value: Long, label: String): Long {
        check(value != Long.MAX_VALUE) { "$label exhausted" }
        return value + 1L
    }

    private class ExecutorScheduler : SpeedSignScheduler, AutoCloseable {
        private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "speed-sign-ttl").apply { isDaemon = true }
        }

        override fun schedule(delayMs: Long, action: () -> Unit): SpeedSignScheduledTask {
            val future = executor.schedule(action, delayMs, TimeUnit.MILLISECONDS)
            return SpeedSignScheduledTask { future.cancel(false) }
        }

        override fun close() {
            executor.shutdownNow()
        }
    }
}
