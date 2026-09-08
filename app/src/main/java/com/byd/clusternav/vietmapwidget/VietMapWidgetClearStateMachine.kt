package com.byd.clusternav.vietmapwidget

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Retryable speed-sign clear state machine.
 *
 * States: ACTIVE → CLEARING → CLEARED (terminal success)
 *                 └→ RETRY_PENDING → CLEARING (retry loop with backoff)
 *
 * Triggers: master OFF, stale threshold, provider disconnect, service destroy, process bootstrap.
 * Clear attempts retry with exponential backoff until acknowledged or max attempts exhausted.
 *
 * Thread safety: all transitions run on the main handler. External callers may invoke
 * [requestClear] and [acknowledgeCleared] from any thread.
 */
class VietMapWidgetClearStateMachine(
    private val clearAction: () -> Boolean,
    private val onStateChanged: (SpeedSignClearState) -> Unit = {},
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    handler: Handler? = null,
) {
    private val main = handler ?: Handler(Looper.getMainLooper())

    @Volatile var state: SpeedSignClearState = SpeedSignClearState.ACTIVE
        private set

    private var attempt = 0
    private var lastTrigger: SpeedSignClearTrigger? = null
    private var lastAttemptMs: Long? = null

    private val retryRunnable = Runnable { executeAttempt() }

    /**
     * Request a clear from any thread. Idempotent when already clearing/cleared.
     * @return true if clear was initiated or already in progress.
     */
    fun requestClear(trigger: SpeedSignClearTrigger): Boolean {
        main.post { doRequestClear(trigger) }
        return true
    }

    /**
     * Acknowledge that the clear was received by the downstream HAL/display.
     * Transitions from CLEARING/RETRY_PENDING → CLEARED.
     */
    fun acknowledgeCleared() {
        main.post { doAcknowledge() }
    }

    /**
     * Reset to ACTIVE (e.g., when a new speed limit value arrives).
     * Cancels any pending retries.
     */
    fun reset() {
        main.post { doReset() }
    }

    /** Cancel pending retries and release handler references. */
    fun dispose() {
        main.removeCallbacks(retryRunnable)
        state = SpeedSignClearState.CLEARED
    }

    private fun doRequestClear(trigger: SpeedSignClearTrigger) {
        when (state) {
            SpeedSignClearState.CLEARED -> return // already done
            SpeedSignClearState.CLEARING, SpeedSignClearState.RETRY_PENDING -> {
                // already in progress — update trigger for diagnostics
                lastTrigger = trigger
                return
            }
            SpeedSignClearState.ACTIVE -> {
                lastTrigger = trigger
                attempt = 0
                transition(SpeedSignClearState.CLEARING)
                executeAttempt()
            }
        }
    }

    private fun executeAttempt() {
        if (state != SpeedSignClearState.CLEARING && state != SpeedSignClearState.RETRY_PENDING) return
        transition(SpeedSignClearState.CLEARING)
        attempt++
        lastAttemptMs = clock()
        val success = try {
            clearAction()
        } catch (e: RuntimeException) {
            Log.w(TAG, "clear attempt $attempt failed: ${e.message}")
            false
        }
        if (success) {
            transition(SpeedSignClearState.CLEARED)
        } else if (attempt >= MAX_ATTEMPTS) {
            Log.e(TAG, "clear exhausted after $MAX_ATTEMPTS attempts (trigger=$lastTrigger)")
            // Transition to CLEARED to avoid infinite loop — best-effort
            transition(SpeedSignClearState.CLEARED)
        } else {
            transition(SpeedSignClearState.RETRY_PENDING)
            val delayMs = backoffMs(attempt)
            Log.i(TAG, "clear retry scheduled: attempt=$attempt delay=${delayMs}ms trigger=$lastTrigger")
            main.postDelayed(retryRunnable, delayMs)
        }
    }

    private fun doAcknowledge() {
        main.removeCallbacks(retryRunnable)
        if (state != SpeedSignClearState.ACTIVE) {
            transition(SpeedSignClearState.CLEARED)
        }
    }

    private fun doReset() {
        main.removeCallbacks(retryRunnable)
        attempt = 0
        lastTrigger = null
        lastAttemptMs = null
        transition(SpeedSignClearState.ACTIVE)
    }

    private fun transition(newState: SpeedSignClearState) {
        if (state == newState) return
        val old = state
        state = newState
        Log.d(TAG, "state: $old → $newState (attempt=$attempt, trigger=$lastTrigger)")
        onStateChanged(newState)
    }

    companion object {
        private const val TAG = "WidgetClearSM"
        internal const val MAX_ATTEMPTS = 5
        internal const val INITIAL_BACKOFF_MS = 200L
        internal const val MAX_BACKOFF_MS = 5_000L

        /** Exponential backoff with cap: 200, 400, 800, 1600, 3200 → capped at 5000. */
        internal fun backoffMs(attempt: Int): Long {
            val delay = INITIAL_BACKOFF_MS * (1L shl (attempt - 1).coerceIn(0, 10))
            return delay.coerceAtMost(MAX_BACKOFF_MS)
        }
    }
}
