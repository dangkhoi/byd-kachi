package com.byd.clusternav.modules.clustercast.simplified

/**
 * Typed postcondition result for every cast/stop mutation.
 *
 * Every shell-mutating operation in the cast pipeline MUST return this type.
 * State commits are gated on [Verified] — all other outcomes preserve prior state.
 *
 * Safety contract:
 * - Preferences (density, bounds) persist ONLY after [Verified].
 * - [Rejected] short-circuits before any shell command executes.
 * - [TimedOut] closes the dadb transport to prevent resource leak.
 * - [Unknown] captures unexpected shell output for diagnostics.
 */
sealed interface CastMutationOutcome {
    /** Postcondition verified: target task visible on expected display with expected bounds. */
    data class Verified(
        val taskId: Int,
        val displayId: Int,
        val bounds: CastBounds? = null,
    ) : CastMutationOutcome

    /** Pre-condition check failed — operation rejected before execution. */
    data class Rejected(val reason: CastRejectReason) : CastMutationOutcome

    /** Shell command returned unexpected output; state indeterminate. */
    data class Unknown(val command: String, val detail: String) : CastMutationOutcome

    /** Operation exceeded deadline; transport closed. */
    data class TimedOut(val command: String, val elapsedMs: Long) : CastMutationOutcome
}

/**
 * Reasons an operation is rejected BEFORE execution (fail-fast).
 */
enum class CastRejectReason {
    /** CP/AA attempted freeform/split — only fullscreen on standard stack is allowed. */
    PROTECTED_FULLSCREEN_STACK_UNPROVEN,

    /** Multiple tasks match the package query — cannot determine which to move. */
    AMBIGUOUS_TASK,

    /** Target slot already has an active app. Must stop it first. */
    SLOT_OCCUPIED,

    /** Package name is empty, blank, or fails validation. */
    INVALID_PACKAGE,

    /** Requested bounds are invalid (zero area, negative, exceeds display). */
    INVALID_BOUNDS,

    /** Target display not found or projection not open. */
    DISPLAY_UNAVAILABLE,

    /** Dadb/shell transport is closed or unreachable. */
    TRANSPORT_CLOSED,
}
