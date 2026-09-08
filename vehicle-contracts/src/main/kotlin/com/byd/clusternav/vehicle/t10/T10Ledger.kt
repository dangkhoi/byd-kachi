package com.byd.clusternav.vehicle.t10

enum class T10LedgerEventType {
    SESSION_START,
    PRECONDITION,
    DISCOVERY_ONLY,
    OBSERVATION,
    PASS,
    FAIL,
    INCONCLUSIVE,
    BLOCKED,
    SKIPPED,
    MUTATION,
    READ_BACK,
    CLEAR,
    RESTORE,
    ROLLBACK_ARMED,
}

enum class T10LedgerOutcome { PASS, FAIL, INCONCLUSIVE, BLOCKED, SKIPPED }

enum class T10LedgerReasonId(val wireName: String) {
    PRECONDITION_FAILED("REASON-PRECONDITION-FAILED"),
    DEPENDENCY_BLOCKED("REASON-DEPENDENCY-BLOCKED"),
    TIME_BUDGET("REASON-TIME-BUDGET"),
    BINDING_BLOCKED("REASON-BINDING-BLOCKED"),
    TRANSPORT_TIMEOUT("REASON-TRANSPORT-TIMEOUT"),
    TRANSPORT_FAILURE("REASON-TRANSPORT-FAILURE"),
    OPERATION_REJECTED("REASON-OPERATION-REJECTED"),
    OBSERVATION_INCONCLUSIVE("REASON-OBSERVATION-INCONCLUSIVE");

    companion object {
        fun parse(value: String) = entries.singleOrNull { it.wireName == value }
            ?: throw IllegalArgumentException("unknown T10 ledger reason ID")
    }
}

/** A ledger event contains only closed identifiers and hash-derived evidence references. */
class T10LedgerEvent private constructor(
    val eventType: T10LedgerEventType,
    val sessionId: SessionId,
    val sequence: Long,
    val sessionInstanceSha256: Sha256,
    val exactIdentitySha256: Sha256,
    val elapsedOffsetMs: Long,
    val previousEventHash: Sha256?,
    val rowId: T10RowId?,
    val rowKind: T10SessionRowKind?,
    val probeId: T10ProbeId?,
    val resultIdentityId: T10ResultIdentityId?,
    val observationId: T10ObservationId?,
    val outcome: T10LedgerOutcome?,
    val reasonId: T10LedgerReasonId?,
    evidenceIds: List<LocalEvidenceId>,
    val eventSha256: Sha256,
) {
    val evidenceIds: List<LocalEvidenceId> = immutableList(evidenceIds)
    val eventId: String = "EVENT-${sequence.toString().padStart(6, '0')}"

    init {
        require(sequence in 1..999_999 && elapsedOffsetMs >= 0)
        require(evidenceIds.distinct().size == evidenceIds.size) { "evidence IDs must be unique" }
    }

    fun computedSha256(): Sha256 = T10Canonical.sha256(canonicalValue(false))

    internal fun canonicalValue(includeEventHash: Boolean): T10JsonValue = T10Canonical.obj(*buildList {
        add("elapsedOffsetMs" to T10Canonical.integer(elapsedOffsetMs))
        add("eventId" to T10Canonical.text(eventId))
        if (includeEventHash) add("eventSha256" to T10Canonical.text(eventSha256.value))
        add("eventType" to T10Canonical.text(eventType.name))
        add("evidenceIds" to T10Canonical.array(evidenceIds.map { T10Canonical.text(it.value) }))
        add("exactIdentitySha256" to T10Canonical.text(exactIdentitySha256.value))
        add("observationId" to observationId.jsonText())
        add("outcome" to outcome.jsonText())
        add("previousEventHash" to previousEventHash.jsonText())
        add("probeId" to probeId.jsonText())
        add("reasonId" to (reasonId?.let { T10Canonical.text(it.wireName) } ?: T10Canonical.nullValue()))
        add("resultIdentityId" to resultIdentityId.jsonText())
        add("rowId" to rowId.jsonText())
        add("rowKind" to rowKind.jsonText())
        add("sequence" to T10Canonical.integer(sequence))
        add("sessionId" to T10Canonical.text(sessionId.value))
        add("sessionInstanceSha256" to T10Canonical.text(sessionInstanceSha256.value))
    }.toTypedArray())

    companion object {
        fun seal(
            eventType: T10LedgerEventType,
            freeze: SessionPackFreeze,
            sequence: Long,
            elapsedOffsetMs: Long,
            previousEventHash: Sha256?,
            rowId: T10RowId? = null,
            rowKind: T10SessionRowKind? = null,
            probeId: T10ProbeId? = null,
            resultIdentityId: T10ResultIdentityId? = null,
            observationId: T10ObservationId? = null,
            outcome: T10LedgerOutcome? = null,
            reasonId: T10LedgerReasonId? = null,
            evidenceIds: Collection<LocalEvidenceId> = emptyList(),
        ): T10LedgerEvent {
            val draft = T10LedgerEvent(eventType, freeze.sessionId, sequence, freeze.sessionInstanceSha256,
                freeze.exactIdentity.canonicalSha256(), elapsedOffsetMs, previousEventHash, rowId, rowKind,
                probeId, resultIdentityId, observationId, outcome, reasonId, evidenceIds.toList(), LEDGER_ZERO_SHA256)
            return T10LedgerEvent(eventType, freeze.sessionId, sequence, freeze.sessionInstanceSha256,
                freeze.exactIdentity.canonicalSha256(), elapsedOffsetMs, previousEventHash, rowId, rowKind,
                probeId, resultIdentityId, observationId, outcome, reasonId, evidenceIds.toList(), draft.computedSha256())
        }

        internal fun parsed(
            eventType: T10LedgerEventType, sessionId: SessionId, sequence: Long, sessionInstanceSha256: Sha256,
            exactIdentitySha256: Sha256, elapsedOffsetMs: Long, previousEventHash: Sha256?, rowId: T10RowId?,
            rowKind: T10SessionRowKind?, probeId: T10ProbeId?, resultIdentityId: T10ResultIdentityId?,
            observationId: T10ObservationId?, outcome: T10LedgerOutcome?, reasonId: T10LedgerReasonId?,
            evidenceIds: List<LocalEvidenceId>, eventSha256: Sha256,
        ) = T10LedgerEvent(eventType, sessionId, sequence, sessionInstanceSha256, exactIdentitySha256,
            elapsedOffsetMs, previousEventHash, rowId, rowKind, probeId, resultIdentityId, observationId,
            outcome, reasonId, evidenceIds, eventSha256)
    }
}

class T10ResultLedger private constructor(
    val freeze: SessionPackFreeze,
    events: List<T10LedgerEvent>,
    val selfSha256: Sha256,
) {
    val schemaId = SCHEMA_ID
    val events: List<T10LedgerEvent> = immutableList(events)
    val freezeSha256: Sha256 get() = freeze.sessionInstanceSha256

    fun toCanonicalBytes(): ByteArray = T10Canonical.render(canonicalValue(true))
    internal fun canonicalValue(includeSelf: Boolean): T10JsonValue = T10Canonical.obj(*buildList {
        add("events" to T10Canonical.array(events.map { it.canonicalValue(true) }))
        add("freezeSha256" to T10Canonical.text(freezeSha256.value))
        add("schemaId" to T10Canonical.text(schemaId))
        if (includeSelf) add("selfSha256" to T10Canonical.text(selfSha256.value))
    }.toTypedArray())

    companion object {
        const val SCHEMA_ID = "clusternav.t10-result-ledger/v1"
        fun create(freeze: SessionPackFreeze, events: Collection<T10LedgerEvent>): T10ResultLedger {
            val copied = events.toList()
            val draft = T10ResultLedger(freeze, copied, LEDGER_ZERO_SHA256)
            return T10ResultLedger(freeze, copied, T10Canonical.sha256(draft.canonicalValue(false)))
        }
        internal fun parsed(freeze: SessionPackFreeze, events: List<T10LedgerEvent>, self: Sha256) =
            T10ResultLedger(freeze, events, self)
    }
}

class ValidatedSessionNLedger internal constructor(ledger: T10ResultLedger, val complete: Boolean) {
    val freeze = ledger.freeze
    val events: List<T10LedgerEvent> = immutableList(ledger.events)
    val startedRowCount = events.mapNotNull { it.rowId }.distinct().size
}

object T10LedgerValidator {
    private val forbidden = setOf(
        T10LedgerEventType.MUTATION, T10LedgerEventType.READ_BACK, T10LedgerEventType.CLEAR,
        T10LedgerEventType.RESTORE, T10LedgerEventType.ROLLBACK_ARMED,
    )
    private val terminals = setOf(
        T10LedgerEventType.PASS, T10LedgerEventType.FAIL, T10LedgerEventType.INCONCLUSIVE,
        T10LedgerEventType.BLOCKED, T10LedgerEventType.SKIPPED,
    )

    fun validateSessionN(ledger: T10ResultLedger): ValidatedSessionNLedger {
        val freeze = ledger.freeze
        require(freeze.sessionInstanceSha256 == T10Canonical.sha256(freeze.canonicalValue(false))) {
            "freeze instance SHA-256 mismatch"
        }
        require(freeze.deadlineElapsedMs == Math.addExact(freeze.sessionStartElapsedMs, freeze.budgetMs)) {
            "freeze deadline mismatch"
        }
        require(freeze.allowedMutationCandidateRevisionIds.isEmpty() &&
            freeze.rows.none { it.kind == T10SessionRowKind.MUTATION }) { "Session N freeze contains mutation material" }
        require(ledger.selfSha256 == T10Canonical.sha256(ledger.canonicalValue(false))) { "ledger self SHA-256 mismatch" }
        require(ledger.events.isNotEmpty()) { "ledger is empty" }
        ledger.events.forEachIndexed { index, event ->
            require(event.eventType !in forbidden) { "forbidden Session N event: ${event.eventType}" }
            require(event.sequence == index + 1L && event.eventId == "EVENT-${(index + 1).toString().padStart(6, '0')}")
            require(event.sessionId == freeze.sessionId && event.sessionInstanceSha256 == freeze.sessionInstanceSha256)
            require(event.exactIdentitySha256 == freeze.exactIdentity.canonicalSha256())
            require(event.previousEventHash == ledger.events.getOrNull(index - 1)?.eventSha256)
            require(event.eventSha256 == event.computedSha256()) { "event hash mismatch at sequence ${event.sequence}" }
            require(event.elapsedOffsetMs <= freeze.budgetMs)
            if (index > 0) require(event.elapsedOffsetMs >= ledger.events[index - 1].elapsedOffsetMs)
        }
        validateSessionStart(ledger.events.first())
        val groups = contiguousRowGroups(ledger.events.drop(1))
        var budgetTruncated = false
        groups.forEachIndexed { index, group ->
            require(index < freeze.rows.size && group.first().rowId == freeze.rows[index].rowId) {
                "ledger row order is not the contiguous frozen order"
            }
            val row = freeze.rows[index]
            if (budgetTruncated) {
                require(group.size == 1 && group.single().eventType == T10LedgerEventType.SKIPPED) {
                    "every row after the first non-fitting row must remain skipped"
                }
            }
            validateRowLifecycle(row, group)
            if (group.size == 1 && group.single().eventType == T10LedgerEventType.SKIPPED) {
                val skippedAt = group.single().elapsedOffsetMs
                if (!budgetTruncated) {
                    require(row.template.estimatedTimeMs > freeze.budgetMs - skippedAt) {
                        "first budget-skipped row still fits the frozen budget"
                    }
                }
                budgetTruncated = true
            }
        }
        return ValidatedSessionNLedger(ledger, complete = groups.size == freeze.rows.size)
    }

    private fun validateSessionStart(event: T10LedgerEvent) {
        require(event.eventType == T10LedgerEventType.SESSION_START && event.sequence == 1L &&
            event.elapsedOffsetMs == 0L && event.previousEventHash == null && event.rowId == null &&
            event.rowKind == null && event.probeId == null && event.resultIdentityId == null &&
            event.observationId == null && event.outcome == null && event.reasonId == null && event.evidenceIds.isEmpty())
    }

    private fun contiguousRowGroups(events: List<T10LedgerEvent>): List<List<T10LedgerEvent>> {
        if (events.isEmpty()) return emptyList()
        require(events.none { it.rowId == null }) { "non-start event is missing a row ID" }
        val groups = ArrayList<List<T10LedgerEvent>>()
        var start = 0
        while (start < events.size) {
            val row = events[start].rowId
            var end = start + 1
            while (end < events.size && events[end].rowId == row) end++
            require(groups.none { it.first().rowId == row }) { "row events are not contiguous" }
            groups += immutableList(events.subList(start, end))
            start = end
        }
        return groups
    }

    private fun validateRowLifecycle(row: SessionRow, events: List<T10LedgerEvent>) {
        events.forEach { event ->
            require(event.rowId == row.rowId && event.rowKind == row.kind)
            require(event.probeId == row.probeIds.singleOrNull())
            require(event.resultIdentityId == if (row.kind == T10SessionRowKind.MILESTONE) row.resultIdentityId else null)
        }
        val terminalEvents = events.filter { it.eventType in terminals }
        require(terminalEvents.size == 1 && terminalEvents.single() == events.last()) { "row must have exactly one final terminal" }
        if (events.size == 1) {
            require(events.single().eventType in setOf(T10LedgerEventType.BLOCKED, T10LedgerEventType.SKIPPED))
            validateTerminal(events.single())
            return
        }
        val precondition = events.first()
        require(precondition.eventType == T10LedgerEventType.PRECONDITION &&
            precondition.outcome in setOf(T10LedgerOutcome.PASS, T10LedgerOutcome.FAIL) &&
            precondition.observationId == null && precondition.reasonId == null &&
            precondition.evidenceIds.isEmpty())
        if (precondition.outcome == T10LedgerOutcome.FAIL) {
            require(events.size == 2 && events.last().eventType == T10LedgerEventType.BLOCKED &&
                events.last().reasonId == T10LedgerReasonId.PRECONDITION_FAILED)
            validateTerminal(events.last())
            return
        }
        when (row.kind) {
            T10SessionRowKind.READ_ONLY -> {
                require(events.drop(1).dropLast(1).all { it.eventType == T10LedgerEventType.DISCOVERY_ONLY &&
                    it.observationId == null && it.outcome == null && it.reasonId == null &&
                    it.evidenceIds.isNotEmpty() })
            }
            T10SessionRowKind.MILESTONE -> {
                require(events.size == 3 && events[1].eventType == T10LedgerEventType.OBSERVATION &&
                    events[1].observationId == row.template.observations.single() &&
                    events[1].outcome == null && events[1].reasonId == null)
            }
            T10SessionRowKind.MUTATION -> error("Session N mutation row")
        }
        validateTerminal(events.last())
    }

    private fun validateTerminal(event: T10LedgerEvent) {
        val expected = when (event.eventType) {
            T10LedgerEventType.PASS -> T10LedgerOutcome.PASS
            T10LedgerEventType.FAIL -> T10LedgerOutcome.FAIL
            T10LedgerEventType.INCONCLUSIVE -> T10LedgerOutcome.INCONCLUSIVE
            T10LedgerEventType.BLOCKED -> T10LedgerOutcome.BLOCKED
            T10LedgerEventType.SKIPPED -> T10LedgerOutcome.SKIPPED
            else -> throw IllegalArgumentException("not a terminal event")
        }
        require(event.outcome == expected && event.observationId == null)
        when (event.eventType) {
            T10LedgerEventType.PASS -> require(event.reasonId == null)
            T10LedgerEventType.SKIPPED -> require(event.reasonId == T10LedgerReasonId.TIME_BUDGET)
            else -> require(event.reasonId != null)
        }
    }
}

object T10LedgerLoader {
    fun load(bytes: ByteArray, freeze: SessionPackFreeze): T10ResultLedger {
        val fields = T10Canonical.parse(bytes).objectFields("result ledger")
        fields.requireExactKeys("result ledger", "events", "freezeSha256", "schemaId", "selfSha256")
        require(fields.getValue("schemaId").stringValue("schemaId") == T10ResultLedger.SCHEMA_ID)
        require(Sha256.parse(fields.getValue("freezeSha256").stringValue("freezeSha256")) == freeze.sessionInstanceSha256)
        val ledger = T10ResultLedger.parsed(freeze, fields.getValue("events").arrayValues("events").map(::parseEvent),
            Sha256.parse(fields.getValue("selfSha256").stringValue("selfSha256")))
        require(ledger.selfSha256 == T10Canonical.sha256(ledger.canonicalValue(false))) { "ledger self SHA-256 mismatch" }
        return ledger
    }

    private fun parseEvent(value: T10JsonValue): T10LedgerEvent {
        val f = value.objectFields("ledger event")
        f.requireExactKeys("ledger event", "elapsedOffsetMs", "eventId", "eventSha256", "eventType", "evidenceIds",
            "exactIdentitySha256", "observationId", "outcome", "previousEventHash", "probeId", "reasonId",
            "resultIdentityId", "rowId", "rowKind", "sequence", "sessionId", "sessionInstanceSha256")
        fun nullable(name: String): String? = f.getValue(name).let { if (it.isNull()) null else it.stringValue(name) }
        val sequence = f.getValue("sequence").longValue("sequence")
        require(f.getValue("eventId").stringValue("eventId") == "EVENT-${sequence.toString().padStart(6, '0')}")
        return T10LedgerEvent.parsed(
            enumValue(f.getValue("eventType").stringValue("eventType"), "ledger event type"),
            SessionId.parse(f.getValue("sessionId").stringValue("sessionId")), sequence,
            Sha256.parse(f.getValue("sessionInstanceSha256").stringValue("sessionInstanceSha256")),
            Sha256.parse(f.getValue("exactIdentitySha256").stringValue("exactIdentitySha256")),
            f.getValue("elapsedOffsetMs").longValue("elapsedOffsetMs"), nullable("previousEventHash")?.let(Sha256::parse),
            nullable("rowId")?.let(T10RowId::parse), nullable("rowKind")?.let { enumValue(it, "row kind") },
            nullable("probeId")?.let(T10ProbeId::parse), nullable("resultIdentityId")?.let(T10ResultIdentityId::parse),
            nullable("observationId")?.let(T10ObservationId::parse), nullable("outcome")?.let { enumValue(it, "ledger outcome") },
            nullable("reasonId")?.let(T10LedgerReasonId::parse),
            f.getValue("evidenceIds").arrayValues("evidenceIds").map { LocalEvidenceId.parse(it.stringValue("evidenceId")) },
            Sha256.parse(f.getValue("eventSha256").stringValue("eventSha256")),
        )
    }
}

private fun Enum<*>?.jsonText(): T10JsonValue = this?.let { T10Canonical.text(it.name) } ?: T10Canonical.nullValue()
private fun Sha256?.jsonText(): T10JsonValue = this?.let { T10Canonical.text(it.value) } ?: T10Canonical.nullValue()
private fun T10ProbeId?.jsonText(): T10JsonValue = this?.let { T10Canonical.text(it.wireName) } ?: T10Canonical.nullValue()
private fun T10ResultIdentityId?.jsonText(): T10JsonValue = this?.let { T10Canonical.text(it.wireName) } ?: T10Canonical.nullValue()
private fun T10ObservationId?.jsonText(): T10JsonValue = this?.let { T10Canonical.text(it.wireName) } ?: T10Canonical.nullValue()
private fun T10RowId?.jsonText(): T10JsonValue = this?.let { T10Canonical.text(it.wireName) } ?: T10Canonical.nullValue()
private val LEDGER_ZERO_SHA256 = Sha256.parse("0".repeat(64))
