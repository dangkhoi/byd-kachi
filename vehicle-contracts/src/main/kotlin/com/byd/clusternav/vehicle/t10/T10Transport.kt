package com.byd.clusternav.vehicle.t10

class MonotonicDeadline private constructor(val elapsedRealtimeDeadlineMs: Long) {
    init { require(elapsedRealtimeDeadlineMs >= 0) }

    fun isExpiredAt(elapsedRealtimeNowMs: Long): Boolean {
        require(elapsedRealtimeNowMs >= 0)
        return elapsedRealtimeNowMs >= elapsedRealtimeDeadlineMs
    }

    fun remainingAt(elapsedRealtimeNowMs: Long): Long {
        require(elapsedRealtimeNowMs >= 0)
        return if (elapsedRealtimeNowMs >= elapsedRealtimeDeadlineMs) 0 else elapsedRealtimeDeadlineMs - elapsedRealtimeNowMs
    }

    companion object {
        fun at(elapsedRealtimeDeadlineMs: Long) = MonotonicDeadline(elapsedRealtimeDeadlineMs)
        fun after(elapsedRealtimeNowMs: Long, timeoutMs: Long): MonotonicDeadline {
            require(elapsedRealtimeNowMs >= 0 && timeoutMs >= 0)
            return MonotonicDeadline(Math.addExact(elapsedRealtimeNowMs, timeoutMs))
        }
    }
}

fun interface MonotonicClock { fun elapsedRealtimeMs(): Long }

fun interface VehicleTransport {
    fun dispatch(operation: FixedReadOperation, deadline: MonotonicDeadline): DispatchOutcome
}

enum class TransportFailureKind {
    CONNECTION_UNAVAILABLE, TARGET_OFFLINE, TARGET_UNAUTHORIZED, PROTOCOL_FAILURE, IO_FAILURE,
}

enum class OperationRejectionReason {
    PERMISSION_DENIED, UNSUPPORTED_OPERATION, INVALID_TARGET_STATE, CODEC_REJECTED,
}

sealed interface OperationOutcome {
    data class Success(val localEvidenceId: LocalEvidenceId) : OperationOutcome
    data class Rejected(val reason: OperationRejectionReason) : OperationOutcome
}

sealed interface DispatchOutcome {
    data class Blocked(val reason: BindingBlockReason) : DispatchOutcome
    data object TimeoutBeforeDispatch : DispatchOutcome
    data class TransportFailure(val kind: TransportFailureKind) : DispatchOutcome
    data class Completed(val operationOutcome: OperationOutcome) : DispatchOutcome
}

/** Tracked evidence reference derived only from a content SHA-256; it is never a path or payload. */
class LocalEvidenceId private constructor(val value: String) : Comparable<LocalEvidenceId> {
    override fun compareTo(other: LocalEvidenceId) = value.compareTo(other.value)
    override fun equals(other: Any?) = other is LocalEvidenceId && value == other.value
    override fun hashCode() = value.hashCode()
    override fun toString() = value

    companion object {
        private val PATTERN = Regex("^LOCAL-EVIDENCE-[0-9a-f]{64}$")
        fun fromContentSha256(contentSha256: Sha256) = LocalEvidenceId("LOCAL-EVIDENCE-${contentSha256.value}")
        fun parse(value: String): LocalEvidenceId {
            require(PATTERN.matches(value)) { "invalid local evidence ID" }
            return LocalEvidenceId(value)
        }
    }
}

enum class DispatchResultState {
    BLOCKED, TIMEOUT_BEFORE_DISPATCH, TRANSPORT_FAILURE, OPERATION_SUCCESS, OPERATION_REJECTED,
}

/** Sanitized result projection. No exception message, device identifier, path, or operation payload is retained. */
class TrackedDispatchResult private constructor(
    val operation: FixedReadOperation,
    val state: DispatchResultState,
    val bindingBlockReason: BindingBlockReason?,
    val transportFailureKind: TransportFailureKind?,
    val operationRejectionReason: OperationRejectionReason?,
    val localEvidenceId: LocalEvidenceId?,
    val selfSha256: Sha256,
) {
    val schemaId = SCHEMA_ID

    init {
        when (state) {
            DispatchResultState.BLOCKED -> require(bindingBlockReason != null && transportFailureKind == null && operationRejectionReason == null && localEvidenceId == null)
            DispatchResultState.TIMEOUT_BEFORE_DISPATCH -> require(bindingBlockReason == null && transportFailureKind == null && operationRejectionReason == null && localEvidenceId == null)
            DispatchResultState.TRANSPORT_FAILURE -> require(bindingBlockReason == null && transportFailureKind != null && operationRejectionReason == null && localEvidenceId == null)
            DispatchResultState.OPERATION_SUCCESS -> require(bindingBlockReason == null && transportFailureKind == null && operationRejectionReason == null && localEvidenceId != null)
            DispatchResultState.OPERATION_REJECTED -> require(bindingBlockReason == null && transportFailureKind == null && operationRejectionReason != null && localEvidenceId == null)
        }
    }

    fun toCanonicalBytes(): ByteArray = T10Canonical.render(canonicalValue(true))

    internal fun canonicalValue(includeSelf: Boolean): T10JsonValue = T10Canonical.obj(*buildList {
        add("bindingBlockReason" to bindingBlockReason.jsonText())
        add("localEvidenceId" to (localEvidenceId?.let { T10Canonical.text(it.value) } ?: T10Canonical.nullValue()))
        add("operationId" to T10Canonical.text(operation.wireName))
        add("operationRejectionReason" to operationRejectionReason.jsonText())
        add("schemaId" to T10Canonical.text(schemaId))
        if (includeSelf) add("selfSha256" to T10Canonical.text(selfSha256.value))
        add("state" to T10Canonical.text(state.name))
        add("transportFailureKind" to transportFailureKind.jsonText())
    }.toTypedArray())

    companion object {
        const val SCHEMA_ID = "clusternav.t10-dispatch-result/v1"

        fun from(operation: FixedReadOperation, outcome: DispatchOutcome): TrackedDispatchResult {
            val values = when (outcome) {
                is DispatchOutcome.Blocked -> Values(DispatchResultState.BLOCKED, block = outcome.reason)
                DispatchOutcome.TimeoutBeforeDispatch -> Values(DispatchResultState.TIMEOUT_BEFORE_DISPATCH)
                is DispatchOutcome.TransportFailure -> Values(DispatchResultState.TRANSPORT_FAILURE, failure = outcome.kind)
                is DispatchOutcome.Completed -> when (val result = outcome.operationOutcome) {
                    is OperationOutcome.Success -> Values(DispatchResultState.OPERATION_SUCCESS, evidence = result.localEvidenceId)
                    is OperationOutcome.Rejected -> Values(DispatchResultState.OPERATION_REJECTED, rejection = result.reason)
                }
            }
            val draft = create(operation, values, TRANSPORT_ZERO_SHA256)
            return create(operation, values, T10Canonical.sha256(draft.canonicalValue(false)))
        }

        internal fun create(operation: FixedReadOperation, values: Values, self: Sha256) = TrackedDispatchResult(
            operation, values.state, values.block, values.failure, values.rejection, values.evidence, self,
        )
    }

    internal data class Values(
        val state: DispatchResultState,
        val block: BindingBlockReason? = null,
        val failure: TransportFailureKind? = null,
        val rejection: OperationRejectionReason? = null,
        val evidence: LocalEvidenceId? = null,
    )
}

object T10DispatchResultLoader {
    fun load(bytes: ByteArray): TrackedDispatchResult {
        val fields = T10Canonical.parse(bytes).objectFields("dispatch result")
        fields.requireExactKeys("dispatch result", "bindingBlockReason", "localEvidenceId", "operationId",
            "operationRejectionReason", "schemaId", "selfSha256", "state", "transportFailureKind")
        require(fields.getValue("schemaId").stringValue("schemaId") == TrackedDispatchResult.SCHEMA_ID)
        fun nullable(name: String): String? = fields.getValue(name).let { if (it.isNull()) null else it.stringValue(name) }
        val values = TrackedDispatchResult.Values(
            state = enumValue(fields.getValue("state").stringValue("state"), "dispatch result state"),
            block = nullable("bindingBlockReason")?.let { enumValue(it, "binding block reason") },
            failure = nullable("transportFailureKind")?.let { enumValue(it, "transport failure kind") },
            rejection = nullable("operationRejectionReason")?.let { enumValue(it, "operation rejection reason") },
            evidence = nullable("localEvidenceId")?.let(LocalEvidenceId::parse),
        )
        val result = TrackedDispatchResult.create(FixedReadOperation.parse(fields.getValue("operationId").stringValue("operationId")), values,
            Sha256.parse(fields.getValue("selfSha256").stringValue("selfSha256")))
        require(T10Canonical.sha256(result.canonicalValue(false)) == result.selfSha256) { "dispatch result self SHA-256 mismatch" }
        return result
    }
}

private fun Enum<*>?.jsonText(): T10JsonValue = this?.let { T10Canonical.text(it.name) } ?: T10Canonical.nullValue()
private val TRANSPORT_ZERO_SHA256 = Sha256.parse("0".repeat(64))
