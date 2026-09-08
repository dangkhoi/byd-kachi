package com.byd.clusternav

import android.os.Handler

/** Every AmapService broadcast reason shares this one serialized lane. */
enum class AmapEmissionKind {
    SESSION_RESET,
    SOURCE_FRAME,
    HEARTBEAT,
    FORCED_REPLAY,
    STOP,
}

data class AmapFrameToken(
    val generation: Long,
    val emissionToken: Long,
    val sourceId: String,
    val sessionId: String,
    val sourceSequence: Long,
)

data class AmapEmission<T>(
    val kind: AmapEmissionKind,
    val token: AmapFrameToken,
    val payload: T,
)

fun interface AmapScheduledTask {
    fun cancel()
}

fun interface AmapEmissionScheduler {
    fun schedule(delayMs: Long, action: () -> Unit): AmapScheduledTask
}

class HandlerAmapEmissionScheduler(private val handler: Handler) : AmapEmissionScheduler {
    override fun schedule(delayMs: Long, action: () -> Unit): AmapScheduledTask {
        val runnable = Runnable { action() }
        handler.postDelayed(runnable, delayMs)
        return AmapScheduledTask { handler.removeCallbacks(runnable) }
    }
}

/**
 * Serializes source frames, the 400 ms heartbeat, one-shot forced replay, source/session changes,
 * and Stop. A cancelled scheduler callback is still harmless because every callback captures the
 * current generation and latest source sequence before it can emit.
 */
class AmapEmissionArbiter<T>(
    private val scheduler: AmapEmissionScheduler,
    private val sink: (AmapEmission<T>) -> Unit,
    private val monotonicNowMs: () -> Long,
    val heartbeatMs: Long = 400L,
) {
    private data class Latest<T>(
        val sourceId: String,
        val sessionId: String,
        val sourceSequence: Long,
        val observedAtMs: Long,
        val validUntilMs: Long,
        val payload: T,
    )

    private val lock = Any()
    private var generation = 0L
    private var emissionToken = 0L
    private var latest: Latest<T>? = null
    private var pendingSourceId: String? = null
    private var pendingSessionId: String? = null
    private var heartbeat: AmapScheduledTask? = null
    private var lastReplayRequestGeneration = -1L

    /**
     * Announces an accepted source/session before its first frame. This immediately cancels the old
     * heartbeat and causes any queued foreign frame to be dropped. The field-proven reset pair is
     * emitted only when the matching first frame supplies a current payload/context.
     */
    fun beginSession(sourceId: String, sessionId: String): Boolean = synchronized(lock) {
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        val current = latest
        if ((current?.sourceId == sourceId && current.sessionId == sessionId) ||
            (pendingSourceId == sourceId && pendingSessionId == sessionId)
        ) return false
        cancelHeartbeatLocked()
        latest = null
        pendingSourceId = sourceId
        pendingSessionId = sessionId
        generation = increment(generation, "generation")
        lastReplayRequestGeneration = -1L
        true
    }

    fun sourceFrame(
        sourceId: String,
        sessionId: String,
        sourceSequence: Long,
        observedAtMs: Long,
        freshForMs: Long,
        payload: T,
    ): AmapFrameToken? = synchronized(lock) {
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(sourceSequence > 0L) { "sourceSequence must be positive" }
        require(observedAtMs >= 0L) { "observedAtMs must be non-negative" }
        require(freshForMs > 0L) { "freshForMs must be positive" }
        val validUntilMs = Math.addExact(observedAtMs, freshForMs)
        val pendingMatches = pendingSourceId == sourceId && pendingSessionId == sessionId
        if (pendingSourceId != null && !pendingMatches) return null
        val previous = latest
        val changed = previous == null ||
            previous.sourceId != sourceId || previous.sessionId != sessionId
        if (!changed && sourceSequence <= previous.sourceSequence) return null

        cancelHeartbeatLocked()
        if (changed && !pendingMatches) {
            generation = increment(generation, "generation")
            lastReplayRequestGeneration = -1L
        }
        if (pendingMatches) {
            pendingSourceId = null
            pendingSessionId = null
        }
        val next = Latest(sourceId, sessionId, sourceSequence, observedAtMs, validUntilMs, payload)
        latest = next
        if (changed) emitLocked(AmapEmissionKind.SESSION_RESET, next)
        val sourceToken = emitLocked(AmapEmissionKind.SOURCE_FRAME, next)
        scheduleHeartbeatLocked(next)
        sourceToken
    }

    /**
     * Emits exactly one additional replay for a fresh matching source token. Repeating the same
     * request generation, or presenting a stale/foreign session or sequence, is a no-op.
     */
    fun forceReplay(requestGeneration: Long, sessionId: String, sourceSequence: Long): Boolean = synchronized(lock) {
        require(requestGeneration >= 0L) { "requestGeneration must be non-negative" }
        val current = latest ?: return false
        if (requestGeneration <= lastReplayRequestGeneration ||
            current.sessionId != sessionId || current.sourceSequence != sourceSequence ||
            monotonicNowMs() >= current.validUntilMs
        ) return false

        lastReplayRequestGeneration = requestGeneration
        cancelHeartbeatLocked()
        emitLocked(AmapEmissionKind.FORCED_REPLAY, current)
        scheduleHeartbeatLocked(current)
        true
    }

    /** Explicit Stop is ordered after all accepted source work and invalidates every pending token. */
    fun stop(sourceId: String, sessionId: String, payload: T): AmapFrameToken = synchronized(lock) {
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        cancelHeartbeatLocked()
        val stopped = latest ?: Latest(
            sourceId = sourceId,
            sessionId = sessionId,
            sourceSequence = 0L,
            observedAtMs = monotonicNowMs().coerceAtLeast(0L),
            validUntilMs = monotonicNowMs().coerceAtLeast(0L),
            payload = payload,
        )
        latest = null
        pendingSourceId = null
        pendingSessionId = null
        generation = increment(generation, "generation")
        lastReplayRequestGeneration = -1L
        emitLocked(AmapEmissionKind.STOP, stopped)
    }

    fun latestToken(): AmapFrameToken? = synchronized(lock) {
        latest?.let { current ->
            AmapFrameToken(generation, emissionToken, current.sourceId, current.sessionId, current.sourceSequence)
        }
    }

    private fun heartbeat(expected: Latest<T>, expectedGeneration: Long): Unit = synchronized(lock) {
        val current = latest
        if (generation != expectedGeneration || current !== expected) return
        heartbeat = null
        if (monotonicNowMs() >= current.validUntilMs) {
            latest = null
            generation = increment(generation, "generation")
            lastReplayRequestGeneration = -1L
            emitLocked(AmapEmissionKind.STOP, current)
            return
        }
        emitLocked(AmapEmissionKind.HEARTBEAT, current)
        scheduleHeartbeatLocked(current)
    }

    private fun scheduleHeartbeatLocked(expected: Latest<T>) {
        val expectedGeneration = generation
        heartbeat = scheduler.schedule(heartbeatMs) { heartbeat(expected, expectedGeneration) }
    }

    private fun cancelHeartbeatLocked() {
        heartbeat?.cancel()
        heartbeat = null
    }

    private fun emitLocked(kind: AmapEmissionKind, current: Latest<T>): AmapFrameToken {
        emissionToken = increment(emissionToken, "emission token")
        val token = AmapFrameToken(
            generation = generation,
            emissionToken = emissionToken,
            sourceId = current.sourceId,
            sessionId = current.sessionId,
            sourceSequence = current.sourceSequence,
        )
        sink(AmapEmission(kind, token, current.payload))
        return token
    }

    private fun increment(value: Long, label: String): Long {
        check(value != Long.MAX_VALUE) { "$label exhausted" }
        return value + 1L
    }
}
