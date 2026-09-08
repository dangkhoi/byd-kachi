package com.byd.clusternav.vehicle.t10

import java.util.Collections

enum class T10MutationDimension { SYNTHETIC_VALUE }
enum class T10PriorCaptureOperationId { CAPTURE_SYNTHETIC_VALUE }
enum class T10MutationOperationId { APPLY_SYNTHETIC_VALUE }
enum class T10ReadBackOperationId { VERIFY_MUTATION, VERIFY_RESTORE }
enum class T10ClearOperationId { CLEAR_SYNTHETIC_VALUE }
enum class T10InverseOperationId { PREPARE_CAPTURED_VALUE, APPLY_CAPTURED_VALUE }

/** One typed scalar is the complete synthetic mutation dimension. */
class T10PriorState private constructor(val syntheticValue: Int) {
    internal fun snapshot(): T10PriorState = T10PriorState(syntheticValue)

    override fun equals(other: Any?): Boolean =
        other is T10PriorState && syntheticValue == other.syntheticValue

    override fun hashCode(): Int = syntheticValue

    companion object {
        fun of(syntheticValue: Int): T10PriorState = T10PriorState(syntheticValue)
    }
}

sealed interface T10PriorCapture {
    data class Captured(val prior: T10PriorState) : T10PriorCapture
    data object Failed : T10PriorCapture
    data object Timeout : T10PriorCapture
}

enum class T10OperationAttempt { PASS, FAIL, TIMEOUT }

interface T10RollbackPort {
    fun capturePrior(operationId: T10PriorCaptureOperationId): T10PriorCapture
    fun mutate(operationId: T10MutationOperationId): T10OperationAttempt
    fun readBack(operationId: T10ReadBackOperationId, prior: T10PriorState): T10OperationAttempt
    fun clear(operationId: T10ClearOperationId): T10OperationAttempt
    fun restore(operationId: T10InverseOperationId, prior: T10PriorState): T10OperationAttempt
}

class T10SyntheticMutationPlan private constructor(
    val dimension: T10MutationDimension,
    val priorCaptureOperationId: T10PriorCaptureOperationId,
    val mutationOperationId: T10MutationOperationId,
    val mutationReadBackOperationId: T10ReadBackOperationId,
    val clearOperationId: T10ClearOperationId,
    inverseOperationIds: List<T10InverseOperationId>,
    val restoreReadBackOperationId: T10ReadBackOperationId,
) {
    val inverseOperationIds: List<T10InverseOperationId> =
        Collections.unmodifiableList(ArrayList(inverseOperationIds))

    companion object {
        fun create(
            inverseOperationIds: Collection<T10InverseOperationId> = T10InverseOperationId.entries,
        ): T10SyntheticMutationPlan {
            val inverse = inverseOperationIds.toList()
            require(inverse == T10InverseOperationId.entries) {
                "synthetic inverse plan must preserve the complete canonical operation order"
            }
            return T10SyntheticMutationPlan(
                dimension = T10MutationDimension.SYNTHETIC_VALUE,
                priorCaptureOperationId = T10PriorCaptureOperationId.CAPTURE_SYNTHETIC_VALUE,
                mutationOperationId = T10MutationOperationId.APPLY_SYNTHETIC_VALUE,
                mutationReadBackOperationId = T10ReadBackOperationId.VERIFY_MUTATION,
                clearOperationId = T10ClearOperationId.CLEAR_SYNTHETIC_VALUE,
                inverseOperationIds = inverse,
                restoreReadBackOperationId = T10ReadBackOperationId.VERIFY_RESTORE,
            )
        }
    }
}

class T10RollbackFrame internal constructor(
    val dimension: T10MutationDimension,
    prior: T10PriorState,
    inverseOperationIds: Collection<T10InverseOperationId>,
) {
    val prior: T10PriorState = prior.snapshot()
    val inverseOperationIds: List<T10InverseOperationId> =
        Collections.unmodifiableList(ArrayList(inverseOperationIds))
}

enum class T10RollbackStage {
    PRIOR_CAPTURE,
    MUTATION,
    READ_BACK,
    CLEAR,
    INVERSE,
    RESTORE_VERIFICATION,
}

enum class T10RollbackFailureKind {
    REPORTED_FAILURE,
    TIMEOUT,
    DEADLINE,
    UNEXPECTED_EXCEPTION,
}

data class T10RollbackFailure(
    val stage: T10RollbackStage,
    val kind: T10RollbackFailureKind,
    val inverseOperationId: T10InverseOperationId? = null,
)

enum class T10RollbackOutcome {
    PASS,
    PRIOR_CAPTURE_FAILED,
    MUTATION_FAILED,
    READ_BACK_FAILED,
    DEADLINE_EXPIRED,
    CLEAR_FAILED,
    RESTORE_FAILED,
    RESTORE_VERIFICATION_FAILED,
    BLOCKED_ARMED_FRAME,
}

class T10RollbackExecution internal constructor(
    val outcome: T10RollbackOutcome,
    failures: Collection<T10RollbackFailure>,
    val armedFrame: T10RollbackFrame?,
) {
    val failures: List<T10RollbackFailure> = Collections.unmodifiableList(ArrayList(failures))
    val recoveryBlocked: Boolean get() = armedFrame != null
}

/** Synthetic harness only. Session N has no production mutation operation. */
class T10RollbackExecutor {
    private var liveFrame: T10RollbackFrame? = null

    @Synchronized
    fun armedFrame(): T10RollbackFrame? = liveFrame

    @Synchronized
    fun isRecoveryBlocked(): Boolean = liveFrame != null

    @Synchronized
    fun execute(
        plan: T10SyntheticMutationPlan,
        port: T10RollbackPort,
        clock: MonotonicClock,
        deadline: MonotonicDeadline,
    ): T10RollbackExecution {
        liveFrame?.let {
            return execution(T10RollbackOutcome.BLOCKED_ARMED_FRAME, emptyList(), it)
        }
        val initialFailures = mutableListOf<T10RollbackFailure>()
        when (deadlineCheck(clock, deadline, T10RollbackStage.PRIOR_CAPTURE, initialFailures)) {
            DeadlineCheck.EXPIRED -> return execution(T10RollbackOutcome.DEADLINE_EXPIRED, initialFailures)
            DeadlineCheck.FAILED -> return execution(T10RollbackOutcome.PRIOR_CAPTURE_FAILED, initialFailures)
            DeadlineCheck.ACTIVE -> Unit
        }

        val captureFailures = mutableListOf<T10RollbackFailure>()
        val prior = when (val capture = capture(port, plan.priorCaptureOperationId, captureFailures)) {
            is T10PriorCapture.Captured -> capture.prior
            T10PriorCapture.Failed -> return execution(T10RollbackOutcome.PRIOR_CAPTURE_FAILED, captureFailures)
            T10PriorCapture.Timeout -> return execution(T10RollbackOutcome.DEADLINE_EXPIRED, captureFailures)
        }
        val frame = T10RollbackFrame(plan.dimension, prior, plan.inverseOperationIds)
        liveFrame = frame

        val failures = mutableListOf<T10RollbackFailure>()
        when (deadlineCheck(clock, deadline, T10RollbackStage.MUTATION, failures)) {
            DeadlineCheck.EXPIRED ->
                return cleanup(plan, port, frame, T10RollbackOutcome.DEADLINE_EXPIRED, failures)
            DeadlineCheck.FAILED ->
                return cleanup(plan, port, frame, T10RollbackOutcome.MUTATION_FAILED, failures)
            DeadlineCheck.ACTIVE -> Unit
        }

        val mutation = attempt(T10RollbackStage.MUTATION, failures) {
            port.mutate(plan.mutationOperationId)
        }
        if (mutation != T10OperationAttempt.PASS) {
            val outcome = if (mutation == T10OperationAttempt.TIMEOUT) {
                T10RollbackOutcome.DEADLINE_EXPIRED
            } else {
                T10RollbackOutcome.MUTATION_FAILED
            }
            return cleanup(plan, port, frame, outcome, failures)
        }
        when (deadlineCheck(clock, deadline, T10RollbackStage.READ_BACK, failures)) {
            DeadlineCheck.EXPIRED ->
                return cleanup(plan, port, frame, T10RollbackOutcome.DEADLINE_EXPIRED, failures)
            DeadlineCheck.FAILED ->
                return cleanup(plan, port, frame, T10RollbackOutcome.READ_BACK_FAILED, failures)
            DeadlineCheck.ACTIVE -> Unit
        }

        val readBack = attempt(T10RollbackStage.READ_BACK, failures) {
            port.readBack(plan.mutationReadBackOperationId, frame.prior)
        }
        if (readBack != T10OperationAttempt.PASS) {
            val outcome = if (readBack == T10OperationAttempt.TIMEOUT) {
                T10RollbackOutcome.DEADLINE_EXPIRED
            } else {
                T10RollbackOutcome.READ_BACK_FAILED
            }
            return cleanup(plan, port, frame, outcome, failures)
        }
        when (deadlineCheck(clock, deadline, T10RollbackStage.CLEAR, failures)) {
            DeadlineCheck.EXPIRED ->
                return cleanup(plan, port, frame, T10RollbackOutcome.DEADLINE_EXPIRED, failures)
            DeadlineCheck.FAILED ->
                return cleanup(plan, port, frame, T10RollbackOutcome.CLEAR_FAILED, failures)
            DeadlineCheck.ACTIVE -> Unit
        }
        return cleanup(plan, port, frame, T10RollbackOutcome.PASS, failures)
    }

    private enum class DeadlineCheck { ACTIVE, EXPIRED, FAILED }

    private fun deadlineCheck(
        clock: MonotonicClock,
        deadline: MonotonicDeadline,
        stage: T10RollbackStage,
        failures: MutableList<T10RollbackFailure>,
    ): DeadlineCheck = try {
        if (deadline.isExpiredAt(clock.elapsedRealtimeMs())) {
            failures += T10RollbackFailure(stage, T10RollbackFailureKind.DEADLINE)
            DeadlineCheck.EXPIRED
        } else {
            DeadlineCheck.ACTIVE
        }
    } catch (_: Exception) {
        failures += T10RollbackFailure(stage, T10RollbackFailureKind.UNEXPECTED_EXCEPTION)
        DeadlineCheck.FAILED
    }

    private fun cleanup(
        plan: T10SyntheticMutationPlan,
        port: T10RollbackPort,
        frame: T10RollbackFrame,
        primaryOutcome: T10RollbackOutcome,
        failures: MutableList<T10RollbackFailure>,
    ): T10RollbackExecution {
        val clearPassed = attempt(T10RollbackStage.CLEAR, failures) {
            port.clear(plan.clearOperationId)
        } == T10OperationAttempt.PASS

        frame.inverseOperationIds.forEach { operationId ->
            val restored = attempt(T10RollbackStage.INVERSE, failures, operationId) {
                port.restore(operationId, frame.prior)
            }
            if (restored != T10OperationAttempt.PASS) {
                return execution(T10RollbackOutcome.RESTORE_FAILED, failures, frame)
            }
        }

        val verified = attempt(T10RollbackStage.RESTORE_VERIFICATION, failures) {
            port.readBack(plan.restoreReadBackOperationId, frame.prior)
        }
        if (verified != T10OperationAttempt.PASS) {
            return execution(T10RollbackOutcome.RESTORE_VERIFICATION_FAILED, failures, frame)
        }

        liveFrame = null
        return execution(if (clearPassed) primaryOutcome else T10RollbackOutcome.CLEAR_FAILED, failures)
    }

    private fun capture(
        port: T10RollbackPort,
        operationId: T10PriorCaptureOperationId,
        failures: MutableList<T10RollbackFailure>,
    ): T10PriorCapture = try {
        port.capturePrior(operationId).also { capture ->
            when (capture) {
                is T10PriorCapture.Captured -> Unit
                T10PriorCapture.Failed -> failures += T10RollbackFailure(
                    T10RollbackStage.PRIOR_CAPTURE,
                    T10RollbackFailureKind.REPORTED_FAILURE,
                )
                T10PriorCapture.Timeout -> failures += T10RollbackFailure(
                    T10RollbackStage.PRIOR_CAPTURE,
                    T10RollbackFailureKind.TIMEOUT,
                )
            }
        }
    } catch (_: Exception) {
        failures += T10RollbackFailure(
            T10RollbackStage.PRIOR_CAPTURE,
            T10RollbackFailureKind.UNEXPECTED_EXCEPTION,
        )
        T10PriorCapture.Failed
    }

    private inline fun attempt(
        stage: T10RollbackStage,
        failures: MutableList<T10RollbackFailure>,
        inverseOperationId: T10InverseOperationId? = null,
        block: () -> T10OperationAttempt,
    ): T10OperationAttempt = try {
        block().also { outcome ->
            when (outcome) {
                T10OperationAttempt.PASS -> Unit
                T10OperationAttempt.FAIL -> failures += T10RollbackFailure(
                    stage,
                    T10RollbackFailureKind.REPORTED_FAILURE,
                    inverseOperationId,
                )
                T10OperationAttempt.TIMEOUT -> failures += T10RollbackFailure(
                    stage,
                    T10RollbackFailureKind.TIMEOUT,
                    inverseOperationId,
                )
            }
        }
    } catch (_: Exception) {
        failures += T10RollbackFailure(
            stage,
            T10RollbackFailureKind.UNEXPECTED_EXCEPTION,
            inverseOperationId,
        )
        T10OperationAttempt.FAIL
    }

    private fun execution(
        outcome: T10RollbackOutcome,
        failures: Collection<T10RollbackFailure>,
        frame: T10RollbackFrame? = null,
    ) = T10RollbackExecution(outcome, failures, frame)
}
