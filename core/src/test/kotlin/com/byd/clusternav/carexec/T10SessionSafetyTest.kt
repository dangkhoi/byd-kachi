package com.byd.clusternav.vehicle.t10

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class T10SessionSafetyTest {
    @Test
    fun `all six production probes resolve to the same shared blocked binding`() {
        val expected = FixedBinding.Blocked(BindingBlockReason.UNPROVEN_APP_REACHABILITY)
        assertEquals(6, T10ProbeId.entries.size)
        assertEquals(T10ProbeId.entries.toSet(), T10FixedOperationCatalog.productionReadBindings.keys)
        T10ProbeId.entries.forEach { probe ->
            assertEquals(expected, T10FixedOperationCatalog.resolve(probe), probe.wireName)
            assertEquals(expected, T10FixedOperationCatalog.productionReadBindings.getValue(probe))
        }
        val bindings: Collection<FixedBinding> = T10FixedOperationCatalog.productionReadBindings.values
        assertFalse(bindings.any { it is FixedBinding.Supported })
        assertTrue(T10FixedOperationCatalog.productionMutationOperations.isEmpty())

        val catalogSource = Files.readString(projectRoot().resolve(CATALOG_PATH))
        listOf("FixedRead" + "Operation", "FixedResult" + "Codec", "OP-", "selectorId", "providerId")
            .forEach { forbidden -> assertFalse(catalogSource.contains(forbidden), forbidden) }
        val fields = FixedBinding.Blocked::class.java.declaredFields.filterNot { it.isSynthetic }
        assertEquals(listOf("reason"), fields.map { it.name })
    }

    @Test
    fun `H8 routes only to blocked property read and S11 S12 are separately closed`() {
        val h8 = T10FixedOperationCatalog.resolveCandidate(T10CatalogCandidateId.H8_PROPERTY_CONFIG_METADATA_R3)
        assertEquals(T10CatalogCandidateState.READ_ONLY_READY, h8.state)
        val routed = h8.decision as CandidateBindingDecision.ReadOnlyProbe
        assertEquals(T10ProbeId.READ_PROPERTY_CONFIG, routed.probeId)
        assertEquals(T10FixedOperationCatalog.resolve(T10ProbeId.READ_PROPERTY_CONFIG), routed.binding)

        val s11 = T10FixedOperationCatalog.resolveCandidate(T10CatalogCandidateId.S11_SOURCE_DOMAIN_R1)
        assertEquals(T10CatalogCandidateState.MUTATION_REVIEW, s11.state)
        assertEquals(
            CandidateBindingDecision.Closed(CandidateBindingReason.CANDIDATE_NOT_READY),
            s11.decision,
        )
        val s12 = T10FixedOperationCatalog.resolveCandidate(T10CatalogCandidateId.S12_REJECTED_SHAPE_R1)
        assertEquals(T10CatalogCandidateState.REJECTED, s12.state)
        assertEquals(
            CandidateBindingDecision.Closed(CandidateBindingReason.REJECTED_REVISION),
            s12.decision,
        )
    }

    @Test
    fun `unknown external IDs fail in closed parsers before catalog resolution`() {
        var resolutions = 0
        val resolver = T10ReadBindingResolver {
            resolutions++
            T10FixedOperationCatalog.resolve(it)
        }
        assertThrows(IllegalArgumentException::class.java) { T10ProbeId.parse("PROBE-READ-UNKNOWN") }
        assertThrows(IllegalArgumentException::class.java) {
            T10CatalogCandidateId.parse("CAND-H-999-UNKNOWN@1")
        }
        assertEquals(0, resolutions)
        assertNotNull(resolver)
    }

    @Test
    fun `T10 sources have no legacy execution token or raw surface reference`() {
        val source = SOURCE_PATHS.joinToString("\n") { Files.readString(projectRoot().resolve(it)) }
        listOf(
            "Car" + "ExecCatalog",
            "Car" + "ExecCommands",
            "Car" + "ExecCli",
            "Car" + "ExecShell",
            "Local" + "DeviceShell",
            "Step" + "Candidate",
            ".com" + "mands",
            "0x38B" + "00030",
        ).forEach { forbidden -> assertFalse(source.contains(forbidden), forbidden) }
    }

    @Test
    fun `actual repository plan is exact seven four zero with frozen mappings`() {
        val plan = currentPlan()
        assertEquals(T10RowId.entries, plan.template.rows.map { it.rowId })
        assertEquals(7, plan.template.rows.count { it.kind == T10SessionRowKind.READ_ONLY })
        assertEquals(4, plan.template.rows.count { it.kind == T10SessionRowKind.MILESTONE })
        assertEquals(0, plan.template.rows.count { it.kind == T10SessionRowKind.MUTATION })
        assertTrue(plan.template.allowedMutationCandidateRevisionIds.isEmpty())
        assertEquals(T10ProbeId.entries, plan.template.allowedProbeIds)
        assertEquals(T10ProbeId.entries, plan.template.rows.take(6).map { it.probeIds.single() })
        assertEquals(
            T10CandidateRevisionId.H8_PROPERTY_CONFIG_METADATA_R3,
            plan.template.rows[6].candidateRevisionId,
        )
        assertEquals(listOf(T10ProbeId.READ_PROPERTY_CONFIG), plan.template.rows[6].probeIds)
        assertEquals(
            T10ResultIdentityId.entries,
            plan.template.rows.drop(7).map { requireNotNull(it.resultIdentityId) },
        )
    }

    @Test
    fun `session result codes are stable zero and twenty through twenty eight`() {
        assertEquals(listOf(0) + (20..28), T10SessionResultCode.entries.map { it.processCode })
        T10SessionResultCode.entries.forEach {
            assertSame(it, T10SessionResultCode.fromProcessCode(it.processCode))
        }
        assertEquals(23, T10SessionResultCode.BINDING_BLOCKED.processCode)
        assertThrows(IllegalArgumentException::class.java) { T10SessionResultCode.fromProcessCode(19) }
    }

    @Test
    fun `shape and cross identity failures precede bindings and every injected side effect`() {
        val plan = currentPlan()
        val validIdentity = identity(plan)
        var shapeResolutions = 0
        val shapeEffects = CountingEffects(T10AuthorizedTarget(validIdentity))
        val shapeResult = T10SessionEngine.withShapeRejectionForVerification(
            T10ReadBindingResolver { shapeResolutions++; T10FixedOperationCatalog.resolve(it) },
        ).start(plan, handoff(validIdentity), shapeEffects.runtime())
        assertBlocked(shapeResult, T10SessionResultCode.SESSION_N_SHAPE_INVALID)
        assertEquals(0, shapeResolutions)
        shapeEffects.assertZero()
        val invalid = listOf(
            validIdentity.copy(packSha256 = hash("wrong-pack")),
            validIdentity.copy(registryFileSha256 = hash("wrong-registry")),
            validIdentity.copy(variant = T10Variant.DEBUG),
            validIdentity.copy(profileId = T10ProfileId.PROFILE_UNASSIGNED),
            validIdentity.copy(permissionId = T10PermissionId.PERMISSION_NONE),
        )
        invalid.forEach { identity ->
            var resolutions = 0
            val effects = CountingEffects(T10AuthorizedTarget(identity))
            val result = T10SessionEngine.withResolverForVerification {
                resolutions++
                T10FixedOperationCatalog.resolve(it)
            }.start(plan, handoff(identity), effects.runtime())
            assertBlocked(result, T10SessionResultCode.EXACT_IDENTITY_INVALID)
            assertEquals(0, resolutions)
            effects.assertZero()
        }
    }

    @Test
    fun `current candidate resolves every row then blocks before all runtime effects`() {
        val plan = currentPlan()
        val identity = identity(plan)
        var resolutions = 0
        val effects = CountingEffects(T10AuthorizedTarget(identity))
        val result = T10SessionEngine.withResolverForVerification { probe ->
            resolutions++
            T10FixedOperationCatalog.resolve(probe)
        }.start(plan, handoff(identity), effects.runtime())
        val blocked = assertBlocked(result, T10SessionResultCode.BINDING_BLOCKED)
        assertEquals(23, blocked.processCode)
        assertEquals(7, resolutions)
        assertEquals(plan.template.rows.take(7).map { it.rowId }, blocked.blockedReadBindings.map { it.rowId })
        assertTrue(blocked.blockedReadBindings.all {
            it.reason == BindingBlockReason.UNPROVEN_APP_REACHABILITY
        })
        assertThrows(UnsupportedOperationException::class.java) {
            (blocked.blockedReadBindings as MutableList<*>).clear()
        }
        effects.assertZero()
    }

    @Test
    fun `supported verification bindings still cannot freeze the current inert template`() {
        val plan = currentPlan()
        val identity = identity(plan)
        val effects = CountingEffects(T10AuthorizedTarget(identity))
        val supported = T10ReadBindingResolver { probe ->
            val operation = FixedReadOperation.forProbe(probe)
            FixedBinding.Supported(operation, operation.codec)
        }
        val result = T10SessionEngine.withResolverForVerification(supported)
            .start(plan, handoff(identity), effects.runtime())
        assertBlocked(result, T10SessionResultCode.INERT_TEMPLATE)
        effects.assertZero()
    }

    @Test
    fun `same session discoveries are immutable sanitized records only`() {
        val plan = currentPlan()
        val handoff = handoff(identity(plan))
        val quarantine = T10SameSessionQuarantine.afterHypotheticalStart(plan, handoff)
        val before = quarantine.snapshot()
        val planBytes = plan.toCanonicalBytes()
        val record = quarantine.recordDiscovery(
            T10RowId.DISCOVERY_LIST_PACKAGE_METADATA,
            T10ProbeId.LIST_PACKAGE_METADATA,
            LocalEvidenceId.fromContentSha256(hash("synthetic-evidence")),
        )
        assertEquals(T10DiscoveryDisposition.DISCOVERY_ONLY, record.disposition)
        assertEquals(before, quarantine.snapshot())
        assertArrayEquals(planBytes, plan.toCanonicalBytes())
        assertTrue(before.allowedMutationCandidateRevisionIds.isEmpty())
        assertEquals(
            mapOf(
                T10CatalogCandidateId.H8_PROPERTY_CONFIG_METADATA_R3 to T10CatalogCandidateState.READ_ONLY_READY,
                T10CatalogCandidateId.S11_SOURCE_DOMAIN_R1 to T10CatalogCandidateState.MUTATION_REVIEW,
                T10CatalogCandidateId.S12_REJECTED_SHAPE_R1 to T10CatalogCandidateState.REJECTED,
            ),
            before.candidateStates,
        )
        assertThrows(UnsupportedOperationException::class.java) {
            (quarantine.records() as MutableList<*>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (before.rowIds as MutableList<*>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (before.candidateStates as MutableMap<*, *>).clear()
        }
        assertThrows(IllegalArgumentException::class.java) {
            quarantine.recordDiscovery(
                T10RowId.D_M1_SURFACE,
                T10ProbeId.LIST_PACKAGE_METADATA,
                LocalEvidenceId.fromContentSha256(hash("wrong-row")),
            )
        }
    }

    @Test
    fun `synthetic happy path arms before mutate and restores in frozen order`() {
        val inverse = T10InverseOperationId.entries.toMutableList()
        val plan = T10SyntheticMutationPlan.create(inverse)
        inverse.reverse()
        val executor = T10RollbackExecutor()
        val port = RecordingRollbackPort(onMutate = { assertNotNull(executor.armedFrame()) })
        val result = executor.execute(plan, port, MonotonicClock { 0 }, MonotonicDeadline.at(100))
        assertEquals(T10RollbackOutcome.PASS, result.outcome)
        assertFalse(result.recoveryBlocked)
        assertNull(executor.armedFrame())
        assertEquals(
            listOf(
                "capture", "mutate", "read:VERIFY_MUTATION", "clear",
                "inverse:PREPARE_CAPTURED_VALUE", "inverse:APPLY_CAPTURED_VALUE",
                "read:VERIFY_RESTORE",
            ),
            port.calls,
        )
        assertEquals(T10InverseOperationId.entries, plan.inverseOperationIds)
        assertTrue(port.seenPriors.all { it == PRIOR })
        assertTrue(port.seenPriors.all { it !== PRIOR })
    }

    @Test
    fun `mutation exception is typed and still clears restores and verifies`() {
        val port = RecordingRollbackPort(mutationException = IllegalStateException("synthetic"))
        val result = T10RollbackExecutor().execute(
            T10SyntheticMutationPlan.create(),
            port,
            MonotonicClock { 0 },
            MonotonicDeadline.at(100),
        )
        assertEquals(T10RollbackOutcome.MUTATION_FAILED, result.outcome)
        assertEquals(
            T10RollbackFailure(T10RollbackStage.MUTATION, T10RollbackFailureKind.UNEXPECTED_EXCEPTION),
            result.failures.first(),
        )
        assertEquals(listOf("capture", "mutate", "clear"), port.calls.take(3))
        assertEquals("read:VERIFY_RESTORE", port.calls.last())
        assertFalse(result.recoveryBlocked)
    }

    @Test
    fun `readback failure and post mutation deadline both run cleanup`() {
        val readFailure = RecordingRollbackPort(readBack = mutableMapOf(
            T10ReadBackOperationId.VERIFY_MUTATION to T10OperationAttempt.FAIL,
        ))
        val failed = T10RollbackExecutor().execute(
            T10SyntheticMutationPlan.create(), readFailure,
            MonotonicClock { 0 }, MonotonicDeadline.at(100),
        )
        assertEquals(T10RollbackOutcome.READ_BACK_FAILED, failed.outcome)
        assertEquals("clear", readFailure.calls[3])
        assertEquals("read:VERIFY_RESTORE", readFailure.calls.last())

        var now = 0L
        val deadlinePort = RecordingRollbackPort(onMutate = { now = 50 })
        val expired = T10RollbackExecutor().execute(
            T10SyntheticMutationPlan.create(), deadlinePort,
            MonotonicClock { now }, MonotonicDeadline.at(50),
        )
        assertEquals(T10RollbackOutcome.DEADLINE_EXPIRED, expired.outcome)
        assertFalse(deadlinePort.calls.contains("read:VERIFY_MUTATION"))
        assertEquals("clear", deadlinePort.calls[2])
        assertEquals("read:VERIFY_RESTORE", deadlinePort.calls.last())
    }

    @Test
    fun `clear failure does not skip ordered restore and verification`() {
        val port = RecordingRollbackPort(clear = T10OperationAttempt.FAIL)
        val result = T10RollbackExecutor().execute(
            T10SyntheticMutationPlan.create(), port,
            MonotonicClock { 0 }, MonotonicDeadline.at(100),
        )
        assertEquals(T10RollbackOutcome.CLEAR_FAILED, result.outcome)
        assertEquals(
            listOf(
                "clear", "inverse:PREPARE_CAPTURED_VALUE",
                "inverse:APPLY_CAPTURED_VALUE", "read:VERIFY_RESTORE",
            ),
            port.calls.takeLast(4),
        )
        assertFalse(result.recoveryBlocked)
    }

    @Test
    fun `restore failure retains deep copied frame and blocks the next mutation`() {
        val inverse = T10InverseOperationId.entries.toMutableList()
        val plan = T10SyntheticMutationPlan.create(inverse)
        val port = RecordingRollbackPort(inverse = mutableMapOf(
            T10InverseOperationId.PREPARE_CAPTURED_VALUE to T10OperationAttempt.FAIL,
        ))
        val executor = T10RollbackExecutor()
        val failed = executor.execute(plan, port, MonotonicClock { 0 }, MonotonicDeadline.at(100))
        assertEquals(T10RollbackOutcome.RESTORE_FAILED, failed.outcome)
        val frame = requireNotNull(failed.armedFrame)
        assertTrue(failed.recoveryBlocked)
        assertSame(frame, executor.armedFrame())
        assertEquals(PRIOR, frame.prior)
        assertNotSame(PRIOR, frame.prior)
        inverse.clear()
        assertEquals(T10InverseOperationId.entries, frame.inverseOperationIds)
        assertThrows(UnsupportedOperationException::class.java) {
            (frame.inverseOperationIds as MutableList<*>).clear()
        }
        val callCount = port.calls.size
        val blocked = executor.execute(plan, port, MonotonicClock { 0 }, MonotonicDeadline.at(100))
        assertEquals(T10RollbackOutcome.BLOCKED_ARMED_FRAME, blocked.outcome)
        assertSame(frame, blocked.armedFrame)
        assertEquals(callCount, port.calls.size)
    }

    @Test
    fun `restore verification failure retains recovery block and one dimension is enforced`() {
        assertEquals(listOf(T10MutationDimension.SYNTHETIC_VALUE), T10MutationDimension.entries)
        assertThrows(IllegalArgumentException::class.java) {
            T10SyntheticMutationPlan.create(listOf(T10InverseOperationId.PREPARE_CAPTURED_VALUE))
        }
        assertThrows(IllegalArgumentException::class.java) {
            T10SyntheticMutationPlan.create(T10InverseOperationId.entries.reversed())
        }
        val port = RecordingRollbackPort(readBack = mutableMapOf(
            T10ReadBackOperationId.VERIFY_RESTORE to T10OperationAttempt.FAIL,
        ))
        val result = T10RollbackExecutor().execute(
            T10SyntheticMutationPlan.create(), port,
            MonotonicClock { 0 }, MonotonicDeadline.at(100),
        )
        assertEquals(T10RollbackOutcome.RESTORE_VERIFICATION_FAILED, result.outcome)
        assertTrue(result.recoveryBlocked)
        assertNotNull(result.armedFrame)
        assertThrows(UnsupportedOperationException::class.java) {
            (result.failures as MutableList<*>).clear()
        }
    }

    private class CountingEffects(private val target: T10AuthorizedTarget?) {
        private var targets = 0
        private var transports = 0
        private var ledgers = 0
        private var clocks = 0
        private var nonces = 0

        fun runtime() = T10SessionRuntime(
            targetAuthorizationLoader = T10TargetAuthorizationLoader { targets++; target },
            transportFactory = T10SessionTransportFactory { _, _ ->
                transports++
                VehicleTransport { _, _ -> DispatchOutcome.TimeoutBeforeDispatch }
            },
            ledgerSink = T10SessionLedgerSink { ledgers++ },
            clock = MonotonicClock { clocks++; 0 },
            nonceSource = T10NonceSource {
                nonces++
                T10Canonical.sha256("nonce".toByteArray())
            },
        )

        fun assertZero() {
            assertEquals(listOf(0, 0, 0, 0, 0), listOf(targets, transports, ledgers, clocks, nonces))
        }
    }

    private class RecordingRollbackPort(
        private val capture: T10PriorCapture = T10PriorCapture.Captured(PRIOR),
        private val mutation: T10OperationAttempt = T10OperationAttempt.PASS,
        private val readBack: MutableMap<T10ReadBackOperationId, T10OperationAttempt> = mutableMapOf(),
        private val clear: T10OperationAttempt = T10OperationAttempt.PASS,
        private val inverse: MutableMap<T10InverseOperationId, T10OperationAttempt> = mutableMapOf(),
        private val mutationException: Exception? = null,
        private val onMutate: () -> Unit = {},
    ) : T10RollbackPort {
        val calls = mutableListOf<String>()
        val seenPriors = mutableListOf<T10PriorState>()

        override fun capturePrior(operationId: T10PriorCaptureOperationId): T10PriorCapture {
            calls += "capture"
            return capture
        }

        override fun mutate(operationId: T10MutationOperationId): T10OperationAttempt {
            calls += "mutate"
            onMutate()
            mutationException?.let { throw it }
            return mutation
        }

        override fun readBack(
            operationId: T10ReadBackOperationId,
            prior: T10PriorState,
        ): T10OperationAttempt {
            calls += "read:${operationId.name}"
            seenPriors += prior
            return readBack[operationId] ?: T10OperationAttempt.PASS
        }

        override fun clear(operationId: T10ClearOperationId): T10OperationAttempt {
            calls += "clear"
            return clear
        }

        override fun restore(
            operationId: T10InverseOperationId,
            prior: T10PriorState,
        ): T10OperationAttempt {
            calls += "inverse:${operationId.name}"
            seenPriors += prior
            return inverse[operationId] ?: T10OperationAttempt.PASS
        }
    }

    private fun assertBlocked(
        result: T10SessionStartResult,
        code: T10SessionResultCode,
    ): T10SessionStartResult.Blocked {
        val blocked = result as T10SessionStartResult.Blocked
        assertEquals(code, blocked.code)
        assertEquals(code.processCode, blocked.processCode)
        return blocked
    }

    private fun currentPlan(): T10SessionPlan = T10SessionPlanLoader.load(
        Files.readAllBytes(projectRoot().resolve(PLAN_PATH)),
    )

    private fun identity(plan: T10SessionPlan): ExactIdentity = ExactIdentity(
        sourceSnapshotSha256 = hash("source"),
        diffFileSha256 = hash("diff"),
        apkFileSha256 = hash("apk"),
        signer = SignerIdentity.fromCertificateHashes(listOf(hash("cert-b"), hash("cert-a"))),
        registryFileSha256 = plan.template.registryFileSha256,
        packSha256 = plan.template.packSha256,
        candidateSetSha256 = hash("candidate-set"),
        variant = T10Variant.VEHICLE_TEST,
        senderId = T10SenderId.SENDER_CLUSTER_NAV,
        componentId = T10ComponentId.COMPONENT_PROBE_RECEIVER,
        permissionId = T10PermissionId.PERMISSION_VENDOR_CAR,
        profileId = T10ProfileId.PROFILE_SEAL_T10,
    )

    private fun handoff(identity: ExactIdentity): T10ArtifactHandoff = T10ArtifactHandoff.create(
        identity,
        listOf(T10BlockerId.UNPROVEN_APP_REACHABILITY),
    )

    private fun projectRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        repeat(6) {
            if (Files.isRegularFile(current.resolve(PLAN_PATH))) return current
            current = requireNotNull(current.parent)
        }
        error("cannot locate ClusterNav project root")
    }

    private fun hash(value: String): Sha256 = T10Canonical.sha256(value.toByteArray())

    companion object {
        private const val PLAN_PATH =
            "docs/diagnostics/hud-sign-re/expansion/vehicle-session-plan.json"
        private const val CATALOG_PATH =
            "core/src/main/kotlin/com/byd/clusternav/carexec/T10FixedOperationCatalog.kt"
        private val SOURCE_PATHS = listOf(
            CATALOG_PATH,
            "core/src/main/kotlin/com/byd/clusternav/carexec/T10SessionEngine.kt",
            "core/src/main/kotlin/com/byd/clusternav/carexec/T10RollbackExecutor.kt",
        )
        private val PRIOR = T10PriorState.of(17)
    }
}
