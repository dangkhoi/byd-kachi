package com.byd.clusternav.navigation

/**
 * Typed result from a physical output delivery attempt.
 *
 * Every physical write (Lane HAL, HUD HAL, Speed-sign HAL) returns this — the worker
 * uses [applied] to decide whether to commit dedup state. Callers never see Unit from
 * a HAL call: they see exactly what happened.
 */
data class OutputDeliveryResult(
    val attemptedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val applied: Boolean,
    val failure: OutputFailureReason?
) {
    init {
        require(attemptedAtEpochMs >= 0) { "attemptedAtEpochMs must be non-negative" }
        require(completedAtEpochMs == null || completedAtEpochMs >= attemptedAtEpochMs) {
            "completedAtEpochMs must be null or >= attemptedAtEpochMs"
        }
        require(applied xor (failure != null)) { "exactly one of applied=true or failure!=null" }
    }
}

/**
 * Why a physical output delivery failed. This is the WRITER-SIDE reason (HAL/transport layer),
 * distinct from [NavigationOutputFailureReason] which is the QUEUE-SIDE/orchestration reason.
 */
enum class OutputFailureReason {
    /** Delivery lambda exceeded its deadline and was interrupted. */
    TIMEOUT,
    /** HAL/transport threw an exception during write. */
    TRANSPORT_ERROR,
    /** HAL acknowledged the write but rejected it (e.g., invalid value). */
    HAL_REJECTED,
    /** The output's bounded queue was full when submission was attempted. */
    QUEUE_FULL,
    /** The generation counter advanced between enqueue and execution — write is stale. */
    GENERATION_STALE
}

/**
 * Delivery contract that returns a typed result.
 *
 * Implementors wrap the actual HAL call and return success/failure. The worker uses the
 * result to decide whether to commit the frame as "applied" for dedup purposes.
 */
fun interface TypedNavigationFrameDelivery {
    /**
     * Deliver [frame] to the physical output and return the result.
     * Must not throw — wrap all exceptions into [OutputDeliveryResult] with appropriate [OutputFailureReason].
     */
    fun deliver(frame: NavigationFrame): OutputDeliveryResult
}
