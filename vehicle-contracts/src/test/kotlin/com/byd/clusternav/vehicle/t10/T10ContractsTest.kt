package com.byd.clusternav.vehicle.t10

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class T10ContractsTest {
    @Test
    fun `canonical JSON rejects duplicate keys ordering whitespace and alternate encodings`() {
        assertThrows(IllegalArgumentException::class.java) { canonical("{\"a\":1,\"a\":2}") }
        assertThrows(IllegalArgumentException::class.java) { canonical("{\"b\":1,\"a\":2}") }
        assertThrows(IllegalArgumentException::class.java) { canonical("{ \"a\":1}") }
        assertThrows(IllegalArgumentException::class.java) { canonical("{\"a\":\"\\u0061\"}") }
        assertThrows(IllegalArgumentException::class.java) { canonical("{\"a\":\"e\\u0301\"}") }
        assertArrayEquals("{\"a\":\"é\",\"z\":[2,1]}".toByteArray(),
            T10Canonical.render(T10Canonical.obj("z" to T10Canonical.array(listOf(T10Canonical.integer(2), T10Canonical.integer(1))),
                "a" to T10Canonical.text("e\u0301"))))
    }

    @Test
    fun `canonical JSON rejects BOM malformed UTF8 floats exponents and integer overflow`() {
        assertThrows(IllegalArgumentException::class.java) { T10Canonical.parse(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + "{}".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { T10Canonical.parse(byteArrayOf(0xc3.toByte(), 0x28)) }
        listOf("1.0", "1e2", "-0", "01", "-01", "+1", "9223372036854775808",
            "-9223372036854775809").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { canonical(value) }
        }
        assertEquals(Long.MIN_VALUE, canonical(Long.MIN_VALUE.toString()).longValue("minimum"))
        assertEquals(Long.MAX_VALUE, canonical(Long.MAX_VALUE.toString()).longValue("maximum"))
        assertThrows(IllegalArgumentException::class.java) { Sha256.parse("A".repeat(64)) }
        assertThrows(IllegalArgumentException::class.java) { canonical("{\"é\":1}") }
        assertThrows(IllegalArgumentException::class.java) { canonical("{\"a\":\"e${1.toChar()}\"}") }
    }

    @Test
    fun `safe repository paths reject absolute traversal dot and slash ambiguity`(@TempDir directory: Path) {
        listOf("/tmp/a", "../a", "a/../b", "a/./b", ".hidden", "a\\b", "a//b", "C:/tmp/a").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { RepoRelativePath.parse(value) }
        }
        Files.createDirectories(directory.resolve("safe"))
        Files.writeString(directory.resolve("safe/input.json"), "{}")
        assertEquals("{}", RepoRelativePath.parse("safe/input.json").readBytesNoFollow(directory).decodeToString())
        Files.createSymbolicLink(directory.resolve("linked"), directory.resolve("safe"))
        assertThrows(IllegalArgumentException::class.java) {
            RepoRelativePath.parse("linked/input.json").readBytesNoFollow(directory)
        }
    }

    @Test
    fun `closed schema is compact canonical and declares all runtime roots`() {
        val bytes = requireNotNull(javaClass.classLoader.getResourceAsStream("t10-contracts.schema.json")).use { it.readBytes() }
        val parsed = T10Canonical.parse(bytes)
        assertArrayEquals(bytes, T10Canonical.render(parsed))
        val text = bytes.decodeToString()
        assertFalse(text.contains('\n'))
        listOf("ArtifactHandoffRoot", "VehicleSessionPlanRoot", "SessionPackFreeze", "ResultLedgerRoot", "DispatchResultRoot").forEach {
            assertTrue(text.contains("\"$it\""), it)
        }
        assertTrue(text.contains("\"additionalProperties\":false"))
        assertFalse(text.contains("placeholder", ignoreCase = true))
    }

    @Test
    fun `strict Stage A loader consumes exact current 7 read 4 milestone zero mutation truth`() {
        val plan = currentPlan()
        assertEquals(T10SessionPlanLoader.STAGE_A_PACK_SHA256, plan.template.packSha256)
        assertEquals(T10SessionPlanLoader.STAGE_A_SELF_SHA256, plan.selfSha256)
        assertEquals(T10SessionPlanLoader.STAGE_A_FILE_SHA256, plan.fileSha256)
        assertEquals(11, plan.template.rows.size)
        assertEquals(7, plan.template.rows.count { it.kind == T10SessionRowKind.READ_ONLY })
        assertEquals(4, plan.template.rows.count { it.kind == T10SessionRowKind.MILESTONE })
        assertEquals(0, plan.template.allowedMutationCandidateRevisionIds.size)
        assertEquals(T10ProbeId.entries, plan.template.allowedProbeIds)
        assertTrue(plan.template.identityRequirement is T10IdentityRequirement.Inert)
        assertEquals(T10RowId.entries, plan.template.rows.map { it.rowId })
    }

    @Test
    fun `model loaders reject unknown keys IDs and enums`() {
        val original = currentPlan().toCanonicalBytes().decodeToString()
        val unknownKey = original.replaceFirst("{\"schemaId\"", "{\"extra\":null,\"schemaId\"")
        assertThrows(IllegalArgumentException::class.java) { T10SessionPlanLoader.load(unknownKey.toByteArray()) }
        val unknownKind = original.replaceFirst("\"kind\":\"READ_ONLY\"", "\"kind\":\"UNKNOWN\"")
        assertThrows(IllegalArgumentException::class.java) { T10SessionPlanLoader.load(unknownKind.toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { T10ProbeId.parse("PROBE-READ-UNKNOWN") }
        assertThrows(IllegalArgumentException::class.java) { FixedReadOperation.parse("OP-READ-UNKNOWN") }
        assertThrows(IllegalArgumentException::class.java) { T10CandidateRevisionId.parse("CAND-H-999-UNKNOWN@1") }
        assertThrows(IllegalArgumentException::class.java) { T10RowId.parse("ROW-9999-UNKNOWN") }
        assertThrows(IllegalArgumentException::class.java) { T10ObservationId.parse("OBS-M1-UNKNOWN") }
        assertThrows(IllegalArgumentException::class.java) { T10ResultIdentityId.parse("RESULT-D-M1-9999") }
        assertThrows(IllegalArgumentException::class.java) { T10ArtifactId.parse("ARTIFACT-UNKNOWN") }
        assertThrows(IllegalArgumentException::class.java) { T10BlockerId.parse("BLOCKER-UNKNOWN") }
        assertThrows(IllegalArgumentException::class.java) { T10LedgerReasonId.parse("REASON-UNKNOWN") }
        assertThrows(IllegalArgumentException::class.java) { LocalEvidenceId.parse("LOCAL-EVIDENCE-/tmp/raw") }
        assertThrows(IllegalArgumentException::class.java) { SessionId.parse("SESSION-raw-id") }
    }

    @Test
    fun `exact identity equality includes every field and signer set aggregate`() {
        val plan = currentPlan()
        val first = identity(plan)
        val same = identity(plan)
        assertEquals(first, same)
        assertEquals(first.signerCertificateSha256s.sorted(), first.signerCertificateSha256s)
        assertEquals(2, first.signerCertificateSha256s.distinct().size)
        val mismatches = listOf(
            first.copy(sourceSnapshotSha256 = hash("other-source")),
            first.copy(diffFileSha256 = hash("other-diff")),
            first.copy(apkFileSha256 = hash("other-apk")),
            first.copy(signer = SignerIdentity.fromCertificateHashes(listOf(hash("other-cert")))),
            first.copy(registryFileSha256 = hash("other-registry")),
            first.copy(packSha256 = hash("other-pack")),
            first.copy(candidateSetSha256 = hash("other-candidates")),
            first.copy(variant = T10Variant.DEBUG),
            first.copy(componentId = T10ComponentId.COMPONENT_PROBE_ACTIVITY),
            first.copy(permissionId = T10PermissionId.PERMISSION_NONE),
            first.copy(profileId = T10ProfileId.PROFILE_UNASSIGNED),
        )
        mismatches.forEach { assertNotEquals(first, it) }
        assertNotEquals(plan.fileSha256, first.logicalPackSha256)
        assertThrows(IllegalArgumentException::class.java) {
            SignerIdentity.verified(first.signerCertificateSha256s, hash("wrong-aggregate"))
        }
    }

    @Test
    fun `artifact handoff round trips only as operationally blocked`() {
        val handoff = T10ArtifactHandoff.create(identity(currentPlan()), listOf(
            T10BlockerId.MISSING_AUTHORIZED_T10_HANDOFF,
            T10BlockerId.UNPROVEN_APP_REACHABILITY,
        ))
        val loaded = T10IdentityLoader.loadHandoff(handoff.toCanonicalBytes())
        assertEquals(ArtifactHandoffState.ARTIFACT_READY_OPERATIONALLY_BLOCKED, loaded.state)
        assertEquals(handoff.exactIdentity, loaded.exactIdentity)
        assertEquals(handoff.blockerIds, loaded.blockerIds)
        val unknown = handoff.toCanonicalBytes().decodeToString()
            .replaceFirst("{\"blockerIds\"", "{\"aaa\":null,\"blockerIds\"")
        assertThrows(IllegalArgumentException::class.java) { T10IdentityLoader.loadHandoff(unknown.toByteArray()) }
    }

    @Test
    fun `fixed bindings carry no command payload and preserve exact codec mapping`() {
        assertEquals(6, T10ProbeId.entries.size)
        T10ProbeId.entries.forEach { probe ->
            val operation = FixedReadOperation.forProbe(probe)
            assertEquals(probe, operation.probeId)
            assertEquals(FixedBinding.Supported(operation, operation.codec), FixedBinding.Supported(operation, operation.codec))
        }
        assertEquals(FixedBinding.Blocked(BindingBlockReason.UNPROVEN_APP_REACHABILITY),
            FixedBinding.Blocked(BindingBlockReason.UNPROVEN_APP_REACHABILITY))
        assertThrows(IllegalArgumentException::class.java) {
            FixedBinding.Supported(FixedReadOperation.READ_PROPERTY_CONFIG, FixedResultCodec.SERVICE_METADATA)
        }
    }

    @Test
    fun `freeze derives deterministic IDs hashes deadline and deep-equal template projection`() {
        val plan = currentPlan()
        val resolved = plan.template.withResolvedIdentity(identity(plan))
        val nonce = hash("nonce")
        val freeze = SessionFreezeFactory.freeze(resolved, plan.fileSha256, nonce, 123L)
        val projection = "{\"packSha256\":\"${resolved.packSha256.value}\",\"sessionNonceSha256\":\"${nonce.value}\",\"sessionStartElapsedMs\":123}"
        val expected = digest(projection.toByteArray()).take(16).uppercase()
        assertEquals("SESSION-$expected", freeze.sessionId.value)
        assertEquals(3_600_123L, freeze.deadlineElapsedMs)
        assertEquals(T10Canonical.sha256(freeze.canonicalValue(false)), freeze.sessionInstanceSha256)
        assertTrue(freeze.matchesTemplate(resolved))
        assertEquals(resolved.rows, freeze.rows.map { it.template })
        assertEquals(resolved.packSha256, freeze.packSha256)
        assertNotEquals(plan.fileSha256, freeze.packSha256)
        assertFalse(freeze.toCanonicalBytes().decodeToString().contains("nonce"))
    }

    @Test
    fun `freeze rejects inert identity overflow and non vehicle-test identity`() {
        val plan = currentPlan()
        assertThrows(IllegalStateException::class.java) { SessionFreezeFactory.freeze(plan, hash("nonce"), 0) }
        val resolved = plan.template.withResolvedIdentity(identity(plan))
        assertThrows(ArithmeticException::class.java) {
            SessionFreezeFactory.freeze(resolved, plan.fileSha256, hash("nonce"), Long.MAX_VALUE)
        }
        val release = plan.template.withResolvedIdentity(identity(plan).copy(variant = T10Variant.RELEASE))
        assertThrows(IllegalArgumentException::class.java) {
            SessionFreezeFactory.freeze(release, plan.fileSha256, hash("nonce"), 0)
        }
    }

    @Test
    fun `freeze and signer use defensive immutable copies`() {
        val plan = currentPlan()
        val mutableHashes = mutableListOf(hash("cert-a"), hash("cert-b"))
        val signer = SignerIdentity.fromCertificateHashes(mutableHashes)
        mutableHashes.clear()
        assertEquals(2, signer.certificateSha256s.size)
        val freeze = freeze(plan)
        assertThrows(UnsupportedOperationException::class.java) { (freeze.rows as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (freeze.allowedProbeIds as MutableList).clear() }
        assertEquals(11, freeze.rows.size)
    }

    @Test
    fun `transport deadline and tracked outcomes keep transport separate from operation result`() {
        val deadline = MonotonicDeadline.after(100, 25)
        assertFalse(deadline.isExpiredAt(124))
        assertTrue(deadline.isExpiredAt(125))
        assertEquals(1, deadline.remainingAt(124))
        assertThrows(ArithmeticException::class.java) { MonotonicDeadline.after(Long.MAX_VALUE, 1) }
        val evidence = LocalEvidenceId.fromContentSha256(hash("raw-local-evidence"))
        val cases = listOf(
            DispatchOutcome.Blocked(BindingBlockReason.UNPROVEN_APP_REACHABILITY) to DispatchResultState.BLOCKED,
            DispatchOutcome.TimeoutBeforeDispatch to DispatchResultState.TIMEOUT_BEFORE_DISPATCH,
            DispatchOutcome.TransportFailure(TransportFailureKind.TARGET_OFFLINE) to DispatchResultState.TRANSPORT_FAILURE,
            DispatchOutcome.Completed(OperationOutcome.Success(evidence)) to DispatchResultState.OPERATION_SUCCESS,
            DispatchOutcome.Completed(OperationOutcome.Rejected(OperationRejectionReason.PERMISSION_DENIED)) to
                DispatchResultState.OPERATION_REJECTED,
        )
        cases.forEach { (outcome, expectedState) ->
            val tracked = TrackedDispatchResult.from(FixedReadOperation.READ_PROPERTY_CONFIG, outcome)
            val loaded = T10DispatchResultLoader.load(tracked.toCanonicalBytes())
            assertEquals(expectedState, loaded.state)
            assertEquals(tracked.selfSha256, loaded.selfSha256)
        }
        val success = TrackedDispatchResult.from(FixedReadOperation.READ_PROPERTY_CONFIG,
            DispatchOutcome.Completed(OperationOutcome.Success(evidence)))
        assertEquals(evidence, success.localEvidenceId)
        val failure = TrackedDispatchResult.from(FixedReadOperation.READ_PROPERTY_CONFIG,
            DispatchOutcome.TransportFailure(TransportFailureKind.TARGET_OFFLINE))
        assertTrue(failure.localEvidenceId == null)
        val serializedFailure = failure.toCanonicalBytes().decodeToString()
        assertFalse(serializedFailure.contains("/Users/") || serializedFailure.contains("C:\\"))
    }

    @Test
    fun `Session N ledger validates gapless chain frozen row order and one terminal each`() {
        val ledger = completeLedger(freeze(currentPlan()))
        val validated = T10LedgerValidator.validateSessionN(ledger)
        assertTrue(validated.complete)
        assertEquals(11, validated.startedRowCount)
        assertEquals((1L..ledger.events.size.toLong()).toList(), ledger.events.map { it.sequence })
        ledger.events.zipWithNext().forEach { (left, right) -> assertEquals(left.eventSha256, right.previousEventHash) }
        val loaded = T10LedgerLoader.load(ledger.toCanonicalBytes(), ledger.freeze)
        assertEquals(ledger.selfSha256, loaded.selfSha256)
    }

    @Test
    fun `Session N ledger rejects forged freeze hash and deadline`() {
        val plan = currentPlan()
        val template = plan.template.withResolvedIdentity(identity(plan))
        val valid = SessionFreezeFactory.freeze(template, plan.fileSha256, hash("freeze-integrity"), 100)
        val forgedHash = SessionPackFreeze.create(valid.sessionId, hash("forged-instance"), plan.fileSha256,
            valid.exactIdentity, template, valid.sessionStartElapsedMs, valid.deadlineElapsedMs)
        assertThrows(IllegalArgumentException::class.java) {
            T10LedgerValidator.validateSessionN(completeLedger(forgedHash))
        }

        val wrongDeadline = valid.deadlineElapsedMs + 1
        val draft = SessionPackFreeze.create(valid.sessionId, hash("draft-instance"), plan.fileSha256,
            valid.exactIdentity, template, valid.sessionStartElapsedMs, wrongDeadline)
        val internallyHashed = SessionPackFreeze.create(valid.sessionId,
            T10Canonical.sha256(draft.canonicalValue(false)), plan.fileSha256, valid.exactIdentity,
            template, valid.sessionStartElapsedMs, wrongDeadline)
        assertThrows(IllegalArgumentException::class.java) {
            T10LedgerValidator.validateSessionN(completeLedger(internallyHashed))
        }
    }

    @Test
    fun `Session N ledger rejects forbidden event broken hash and missing terminal`() {
        val freeze = freeze(currentPlan())
        val start = event(freeze, emptyList(), T10LedgerEventType.SESSION_START)
        val forbidden = event(freeze, listOf(start), T10LedgerEventType.MUTATION, freeze.rows.first())
        assertThrows(IllegalArgumentException::class.java) {
            T10LedgerValidator.validateSessionN(T10ResultLedger.create(freeze, listOf(start, forbidden)))
        }
        val complete = completeLedger(freeze)
        val firstRow = complete.events[1]
        val broken = T10LedgerEvent.parsed(firstRow.eventType, firstRow.sessionId, firstRow.sequence,
            firstRow.sessionInstanceSha256, firstRow.exactIdentitySha256, firstRow.elapsedOffsetMs,
            hash("wrong-previous"), firstRow.rowId, firstRow.rowKind, firstRow.probeId, firstRow.resultIdentityId,
            firstRow.observationId, firstRow.outcome, firstRow.reasonId, firstRow.evidenceIds, firstRow.eventSha256)
        val badEvents = complete.events.toMutableList().also { it[1] = broken }
        assertThrows(IllegalArgumentException::class.java) {
            T10LedgerValidator.validateSessionN(T10ResultLedger.create(freeze, badEvents))
        }
        assertThrows(IllegalArgumentException::class.java) {
            T10LedgerValidator.validateSessionN(T10ResultLedger.create(freeze, complete.events.dropLast(1)))
        }
    }

    @Test
    fun `Session N budget skip is first nonfit and forces every later row skipped`() {
        val freeze = freeze(currentPlan())
        val firstSkipAt = freeze.budgetMs - freeze.rows.first().template.estimatedTimeMs + 1
        val allSkipped = mutableListOf<T10LedgerEvent>()
        allSkipped += event(freeze, allSkipped, T10LedgerEventType.SESSION_START)
        freeze.rows.forEach { row ->
            allSkipped += event(freeze, allSkipped, T10LedgerEventType.SKIPPED, row,
                T10LedgerOutcome.SKIPPED, reason = T10LedgerReasonId.TIME_BUDGET,
                elapsedOffsetMs = firstSkipAt)
        }
        assertTrue(T10LedgerValidator.validateSessionN(T10ResultLedger.create(freeze, allSkipped)).complete)

        val resumed = mutableListOf<T10LedgerEvent>()
        resumed += event(freeze, resumed, T10LedgerEventType.SESSION_START)
        resumed += event(freeze, resumed, T10LedgerEventType.SKIPPED, freeze.rows[0],
            T10LedgerOutcome.SKIPPED, reason = T10LedgerReasonId.TIME_BUDGET,
            elapsedOffsetMs = firstSkipAt)
        resumed += event(freeze, resumed, T10LedgerEventType.PRECONDITION, freeze.rows[1],
            T10LedgerOutcome.PASS, elapsedOffsetMs = firstSkipAt)
        resumed += event(freeze, resumed, T10LedgerEventType.DISCOVERY_ONLY, freeze.rows[1],
            evidence = listOf(LocalEvidenceId.fromContentSha256(hash("resumed"))), elapsedOffsetMs = firstSkipAt)
        resumed += event(freeze, resumed, T10LedgerEventType.PASS, freeze.rows[1],
            T10LedgerOutcome.PASS, elapsedOffsetMs = firstSkipAt)
        assertThrows(IllegalArgumentException::class.java) {
            T10LedgerValidator.validateSessionN(T10ResultLedger.create(freeze, resumed))
        }

        val stillFits = listOf(
            event(freeze, emptyList(), T10LedgerEventType.SESSION_START),
        ).toMutableList()
        stillFits += event(freeze, stillFits, T10LedgerEventType.SKIPPED, freeze.rows[0],
            T10LedgerOutcome.SKIPPED, reason = T10LedgerReasonId.TIME_BUDGET, elapsedOffsetMs = 0)
        assertThrows(IllegalArgumentException::class.java) {
            T10LedgerValidator.validateSessionN(T10ResultLedger.create(freeze, stillFits))
        }
    }

    @Test
    fun `Session N rejects evidence or reasons smuggled into precondition shape`() {
        val freeze = freeze(currentPlan())
        val row = freeze.rows.first()
        val events = mutableListOf<T10LedgerEvent>()
        events += event(freeze, events, T10LedgerEventType.SESSION_START)
        events += event(freeze, events, T10LedgerEventType.PRECONDITION, row, T10LedgerOutcome.PASS,
            evidence = listOf(LocalEvidenceId.fromContentSha256(hash("not-precondition-evidence"))))
        events += event(freeze, events, T10LedgerEventType.DISCOVERY_ONLY, row,
            evidence = listOf(LocalEvidenceId.fromContentSha256(hash("discovery"))))
        events += event(freeze, events, T10LedgerEventType.PASS, row, T10LedgerOutcome.PASS)
        assertThrows(IllegalArgumentException::class.java) {
            T10LedgerValidator.validateSessionN(T10ResultLedger.create(freeze, events))
        }
    }

    @Test
    fun `ledger loader rejects unknown event type`() {
        val ledger = completeLedger(freeze(currentPlan()))
        val unknown = ledger.toCanonicalBytes().decodeToString()
            .replaceFirst("\"eventType\":\"PASS\"", "\"eventType\":\"UNKNOWN\"")
        assertThrows(IllegalArgumentException::class.java) { T10LedgerLoader.load(unknown.toByteArray(), ledger.freeze) }
    }

    private fun completeLedger(freeze: SessionPackFreeze): T10ResultLedger {
        val events = mutableListOf<T10LedgerEvent>()
        events += event(freeze, events, T10LedgerEventType.SESSION_START)
        freeze.rows.forEach { row ->
            events += event(freeze, events, T10LedgerEventType.PRECONDITION, row, T10LedgerOutcome.PASS)
            if (row.kind == T10SessionRowKind.READ_ONLY) {
                events += event(freeze, events, T10LedgerEventType.DISCOVERY_ONLY, row,
                    evidence = listOf(LocalEvidenceId.fromContentSha256(hash(row.rowId.wireName))))
            } else {
                events += event(freeze, events, T10LedgerEventType.OBSERVATION, row,
                    observation = row.template.observations.single())
            }
            events += event(freeze, events, T10LedgerEventType.PASS, row, T10LedgerOutcome.PASS)
        }
        return T10ResultLedger.create(freeze, events)
    }

    private fun event(
        freeze: SessionPackFreeze, prior: List<T10LedgerEvent>, type: T10LedgerEventType,
        row: SessionRow? = null, outcome: T10LedgerOutcome? = null,
        observation: T10ObservationId? = null, evidence: List<LocalEvidenceId> = emptyList(),
        reason: T10LedgerReasonId? = null, elapsedOffsetMs: Long = prior.size.toLong(),
    ) = T10LedgerEvent.seal(type, freeze, prior.size + 1L, elapsedOffsetMs, prior.lastOrNull()?.eventSha256,
        row?.rowId, row?.kind, row?.probeIds?.singleOrNull(), row?.resultIdentityId, observation, outcome,
        reasonId = reason, evidenceIds = evidence)

    private fun freeze(plan: T10SessionPlan): SessionPackFreeze = SessionFreezeFactory.freeze(
        plan.template.withResolvedIdentity(identity(plan)), plan.fileSha256, hash("session-nonce"), 100,
    )

    private fun identity(plan: T10SessionPlan): ExactIdentity = ExactIdentity(
        hash("source"), hash("diff"), hash("apk"),
        SignerIdentity.fromCertificateHashes(listOf(hash("cert-b"), hash("cert-a"))),
        plan.template.registryFileSha256, plan.template.packSha256, hash("candidate-set"),
        T10Variant.VEHICLE_TEST, T10SenderId.SENDER_CLUSTER_NAV, T10ComponentId.COMPONENT_PROBE_RECEIVER,
        T10PermissionId.PERMISSION_VENDOR_CAR, T10ProfileId.PROFILE_SEAL_T10,
    )

    private fun currentPlan(): T10SessionPlan = T10SessionPlanLoader.load(
        Files.readAllBytes(projectRoot().resolve("docs/diagnostics/hud-sign-re/expansion/vehicle-session-plan.json")),
    )

    private fun projectRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        repeat(5) {
            if (Files.isRegularFile(current.resolve("docs/diagnostics/hud-sign-re/expansion/vehicle-session-plan.json"))) return current
            current = requireNotNull(current.parent)
        }
        error("cannot locate ClusterNav project root")
    }

    private fun canonical(value: String) = T10Canonical.parse(value.toByteArray(StandardCharsets.UTF_8))
    private fun hash(value: String) = T10Canonical.sha256(value.toByteArray(StandardCharsets.UTF_8))
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
