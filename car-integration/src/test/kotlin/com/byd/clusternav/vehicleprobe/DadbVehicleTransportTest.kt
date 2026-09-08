package com.byd.clusternav.vehicleprobe

import com.byd.clusternav.vehicle.t10.BindingBlockReason
import com.byd.clusternav.vehicle.t10.DispatchOutcome
import com.byd.clusternav.vehicle.t10.DispatchResultState
import com.byd.clusternav.vehicle.t10.ExactIdentity
import com.byd.clusternav.vehicle.t10.FixedReadOperation
import com.byd.clusternav.vehicle.t10.LocalEvidenceId
import com.byd.clusternav.vehicle.t10.MonotonicClock
import com.byd.clusternav.vehicle.t10.MonotonicDeadline
import com.byd.clusternav.vehicle.t10.OperationOutcome
import com.byd.clusternav.vehicle.t10.OperationRejectionReason
import com.byd.clusternav.vehicle.t10.Sha256
import com.byd.clusternav.vehicle.t10.SignerIdentity
import com.byd.clusternav.vehicle.t10.T10Canonical
import com.byd.clusternav.vehicle.t10.T10ComponentId
import com.byd.clusternav.vehicle.t10.T10DispatchResultLoader
import com.byd.clusternav.vehicle.t10.T10PermissionId
import com.byd.clusternav.vehicle.t10.T10ProfileId
import com.byd.clusternav.vehicle.t10.T10SenderId
import com.byd.clusternav.vehicle.t10.T10SessionPlan
import com.byd.clusternav.vehicle.t10.T10SessionPlanLoader
import com.byd.clusternav.vehicle.t10.T10Variant
import com.byd.clusternav.vehicle.t10.TrackedDispatchResult
import com.byd.clusternav.vehicle.t10.TransportFailureKind
import dadb.AdbAuthException
import dadb.AdbConnectException
import dadb.AdbProtocolException
import dadb.AdbTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DadbVehicleTransportTest {
    private val clock = MonotonicClock { 0L }
    private val deadline = MonotonicDeadline.at(5_000L)

    @Test
    fun `production renderer blocks all six before connector and evidence`() {
        val connector = RecordingConnector { error("connector must remain unreachable") }
        val evidence = CountingEvidence()
        val transport = DadbVehicleTransport.forProductionVerification(testTarget(), evidence, clock, connector)
        FixedReadOperation.entries.forEach { operation ->
            val result = transport.dispatch(operation, deadline) as DispatchOutcome.Blocked
            assertEquals(BindingBlockReason.UNPROVEN_APP_REACHABILITY, result.reason)
        }
        assertEquals(0, connector.calls.size)
        assertEquals(0, evidence.writes.get())
        transport.close()
        transport.close()
    }

    @Test
    fun `supported verification operation connects lazily with exact transport query and fixed bounds`() {
        val session = FakeSession { FixedDadbResponse("synthetic", "", 0) }
        val connector = RecordingConnector { session }
        val evidence = CountingEvidence()
        val transport = verificationTransport(connector, evidence)
        assertTrue(connector.calls.isEmpty())

        val blocked = transport.dispatch(FixedReadOperation.READ_SERVICE_METADATA, deadline)
        assertTrue(blocked is DispatchOutcome.Blocked)
        assertTrue(connector.calls.isEmpty())

        val completed = transport.dispatch(FixedReadOperation.LIST_PACKAGE_METADATA, deadline)
            as DispatchOutcome.Completed
        assertTrue(completed.operationOutcome is OperationOutcome.Success)
        val call = connector.calls.single()
        assertEquals("127.0.0.1", call.host)
        assertEquals(5037, call.port)
        assertEquals("host:transport:SERIAL-TEST", call.query)
        assertEquals(DadbVehicleTransport.CONNECT_TIMEOUT_MS, call.connectTimeoutMs)
        assertEquals(DadbVehicleTransport.SOCKET_TIMEOUT_MS, call.socketTimeoutMs)
        assertEquals(listOf(VerificationFixedReadCommand.SYNTHETIC_READ), session.commands)
        assertEquals(1, session.closeCount.get())
        assertEquals(1, evidence.writes.get())

        val tracked = TrackedDispatchResult.from(FixedReadOperation.LIST_PACKAGE_METADATA, completed)
        val loaded = T10DispatchResultLoader.load(tracked.toCanonicalBytes())
        assertEquals(FixedReadOperation.LIST_PACKAGE_METADATA, loaded.operation)
        assertEquals(DispatchResultState.OPERATION_SUCCESS, loaded.state)
        assertEquals(tracked.localEvidenceId, loaded.localEvidenceId)
        assertEquals(tracked.selfSha256, loaded.selfSha256)
        transport.close()
    }

    @Test
    fun `one worker plus one queued call rejects saturation without opening a third session`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val connector = RecordingConnector {
            FakeSession {
                entered.countDown()
                release.await()
                FixedDadbResponse("ok", "", 0)
            }
        }
        val transport = verificationTransport(connector, CountingEvidence())
        val callers = Executors.newFixedThreadPool(2)
        try {
            val first = callers.submit<DispatchOutcome> {
                transport.dispatch(FixedReadOperation.LIST_PACKAGE_METADATA, deadline)
            }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val second = callers.submit<DispatchOutcome> {
                transport.dispatch(FixedReadOperation.LIST_PACKAGE_METADATA, deadline)
            }
            awaitTrue { transport.queuedCallCountForVerification() == 1 }
            val rejected = transport.dispatch(FixedReadOperation.LIST_PACKAGE_METADATA, deadline)
                as DispatchOutcome.TransportFailure
            assertEquals(TransportFailureKind.IO_FAILURE, rejected.kind)
            assertEquals(1, connector.calls.size)
            release.countDown()
            assertTrue(first.get(1, TimeUnit.SECONDS) is DispatchOutcome.Completed)
            assertTrue(second.get(1, TimeUnit.SECONDS) is DispatchOutcome.Completed)
            assertEquals(2, connector.calls.size)
        } finally {
            release.countDown()
            transport.close()
            callers.shutdownNow()
        }
    }

    @Test
    fun `call timeout closes active session and late shell completion cannot publish evidence`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val session = FakeSession {
            entered.countDown()
            while (release.count > 0) {
                try {
                    release.await(10, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    // Deliberately ignore interruption to model a socket call that completes late.
                }
            }
            finished.countDown()
            FixedDadbResponse("late", "late", 0)
        }
        val evidence = CountingEvidence()
        val transport = verificationTransport(RecordingConnector { session }, evidence)
        val result = transport.dispatch(
            FixedReadOperation.LIST_PACKAGE_METADATA,
            MonotonicDeadline.at(40),
        )
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        assertEquals(DispatchOutcome.TimeoutBeforeDispatch, result)
        assertEquals(1, session.closeCount.get())
        assertEquals(0, evidence.writes.get())
        release.countDown()
        assertTrue(finished.await(1, TimeUnit.SECONDS))
        Thread.sleep(20)
        assertEquals(0, evidence.writes.get())
        assertEquals(1, session.closeCount.get())
        transport.close()
    }

    @Test
    fun `close cancels active call idempotently and prevents late success`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val session = FakeSession {
            entered.countDown()
            while (release.count > 0) {
                try {
                    release.await(10, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    // The fake intentionally remains late after cancellation.
                }
            }
            FixedDadbResponse("late", "", 0)
        }
        val evidence = CountingEvidence()
        val transport = verificationTransport(RecordingConnector { session }, evidence)
        val caller = Executors.newSingleThreadExecutor()
        try {
            val future = caller.submit<DispatchOutcome> {
                transport.dispatch(FixedReadOperation.LIST_PACKAGE_METADATA, deadline)
            }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            transport.close()
            transport.close()
            assertEquals(DispatchOutcome.TimeoutBeforeDispatch, future.get(1, TimeUnit.SECONDS))
            assertEquals(1, session.closeCount.get())
            release.countDown()
            Thread.sleep(20)
            assertEquals(0, evidence.writes.get())
        } finally {
            release.countDown()
            caller.shutdownNow()
        }
    }

    @Test
    fun `shell rejection is distinct from connect auth protocol and timeout failures`() {
        val nonzero = dispatchWithSession { FixedDadbResponse("raw-out", "raw-err", 9) }
            as DispatchOutcome.Completed
        val rejected = nonzero.operationOutcome as OperationOutcome.Rejected
        assertEquals(OperationRejectionReason.INVALID_TARGET_STATE, rejected.reason)

        assertTransportFailure(TransportFailureKind.CONNECTION_UNAVAILABLE) {
            throw AdbConnectException("synthetic", null)
        }
        assertTransportFailure(TransportFailureKind.TARGET_UNAUTHORIZED) {
            throw AdbAuthException("synthetic", null)
        }
        assertEquals(DispatchOutcome.TimeoutBeforeDispatch, dispatchWithSession {
            throw AdbTimeoutException("synthetic", null)
        })
        val protocol = dispatchWithSession { throw AdbProtocolException("synthetic", null) }
            as DispatchOutcome.TransportFailure
        assertEquals(TransportFailureKind.PROTOCOL_FAILURE, protocol.kind)
    }

    @Test
    fun `deadline expiry during evidence staging prevents atomic publication and success`() {
        val now = AtomicLong(0)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val publications = AtomicInteger()
        val evidence = LocalEvidenceSink { stdout, stderr, exitCode, publicationAllowed ->
            entered.countDown()
            release.await()
            if (!publicationAllowed()) throw EvidencePublicationCancelledException()
            publications.incrementAndGet()
            LocalEvidenceId.fromContentSha256(testHash("$exitCode:$stdout:$stderr"))
        }
        val transport = DadbVehicleTransport.forVerification(
            approvedTarget = testTarget(),
            evidenceSink = evidence,
            clock = MonotonicClock(now::get),
            connector = RecordingConnector { FakeSession { FixedDadbResponse("ready", "", 0) } },
            supportedOperation = FixedReadOperation.LIST_PACKAGE_METADATA,
        )
        val caller = Executors.newSingleThreadExecutor()
        try {
            val future = caller.submit<DispatchOutcome> {
                transport.dispatch(FixedReadOperation.LIST_PACKAGE_METADATA, MonotonicDeadline.at(50))
            }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            now.set(50)
            release.countDown()
            assertEquals(DispatchOutcome.TimeoutBeforeDispatch, future.get(1, TimeUnit.SECONDS))
            assertEquals(0, publications.get())
        } finally {
            release.countDown()
            transport.close()
            caller.shutdownNow()
        }
    }

    @Test
    fun `unknown runtime propagates only after session cleanup`() {
        val session = FakeSession { throw IllegalStateException("synthetic unknown") }
        val transport = verificationTransport(RecordingConnector { session }, CountingEvidence())
        val failure = assertThrows(IllegalStateException::class.java) {
            transport.dispatch(FixedReadOperation.LIST_PACKAGE_METADATA, deadline)
        }
        assertEquals("synthetic unknown", failure.message)
        assertEquals(1, session.closeCount.get())
        transport.close()
    }

    @Test
    fun `oversized raw response is codec rejection and is never written`() {
        val evidence = CountingEvidence()
        val transport = verificationTransport(
            RecordingConnector {
                FakeSession {
                    FixedDadbResponse("x".repeat(LocalEvidenceWriter.MAX_STREAM_BYTES + 1), "", 0)
                }
            },
            evidence,
        )
        val completed = transport.dispatch(FixedReadOperation.LIST_PACKAGE_METADATA, deadline)
            as DispatchOutcome.Completed
        assertEquals(
            OperationRejectionReason.CODEC_REJECTED,
            (completed.operationOutcome as OperationOutcome.Rejected).reason,
        )
        assertEquals(0, evidence.writes.get())
        transport.close()
    }

    private fun verificationTransport(
        connector: FixedDadbConnector,
        evidence: LocalEvidenceSink,
    ): DadbVehicleTransport = DadbVehicleTransport.forVerification(
        approvedTarget = testTarget(),
        evidenceSink = evidence,
        clock = clock,
        connector = connector,
        supportedOperation = FixedReadOperation.LIST_PACKAGE_METADATA,
    )

    private fun dispatchWithSession(action: (VerificationFixedReadCommand) -> FixedDadbResponse): DispatchOutcome {
        val transport = verificationTransport(RecordingConnector { FakeSession(action) }, CountingEvidence())
        return try {
            transport.dispatch(FixedReadOperation.LIST_PACKAGE_METADATA, deadline)
        } finally {
            transport.close()
        }
    }

    private fun assertTransportFailure(kind: TransportFailureKind, connect: () -> FixedDadbSession) {
        val transport = verificationTransport(RecordingConnector(connect), CountingEvidence())
        try {
            val result = transport.dispatch(FixedReadOperation.LIST_PACKAGE_METADATA, deadline)
                as DispatchOutcome.TransportFailure
            assertEquals(kind, result.kind)
        } finally {
            transport.close()
        }
    }

    private fun awaitTrue(condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        while (!condition() && System.nanoTime() < end) Thread.sleep(5)
        assertTrue(condition())
    }
}

private data class ConnectorCall(
    val host: String,
    val port: Int,
    val query: String,
    val connectTimeoutMs: Int,
    val socketTimeoutMs: Int,
)

private class RecordingConnector(
    private val session: () -> FixedDadbSession,
) : FixedDadbConnector {
    val calls = CopyOnWriteArrayList<ConnectorCall>()

    override fun connect(
        adbServerHost: String,
        adbServerPort: Int,
        deviceQuery: String,
        connectTimeoutMs: Int,
        socketTimeoutMs: Int,
    ): FixedDadbSession {
        calls += ConnectorCall(adbServerHost, adbServerPort, deviceQuery, connectTimeoutMs, socketTimeoutMs)
        return session()
    }
}

private class FakeSession(
    private val action: (VerificationFixedReadCommand) -> FixedDadbResponse,
) : FixedDadbSession {
    val commands = CopyOnWriteArrayList<VerificationFixedReadCommand>()
    val closeCount = AtomicInteger()

    override fun executeFixed(command: VerificationFixedReadCommand): FixedDadbResponse {
        commands += command
        return action(command)
    }

    override fun close() {
        closeCount.incrementAndGet()
    }
}

internal class CountingEvidence : LocalEvidenceSink {
    val writes = AtomicInteger()
    override fun write(
        stdout: String,
        stderr: String,
        exitCode: Int,
        publicationAllowed: () -> Boolean,
    ): LocalEvidenceId {
        if (!publicationAllowed()) throw EvidencePublicationCancelledException()
        writes.incrementAndGet()
        return LocalEvidenceId.fromContentSha256(testHash("$exitCode:$stdout:$stderr"))
    }
}

internal fun testProjectRoot(): Path {
    var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    repeat(6) {
        if (Files.isRegularFile(current.resolve(TEST_PLAN_PATH))) return current
        current = requireNotNull(current.parent)
    }
    error("cannot locate ClusterNav project root")
}

internal fun testPlan(): T10SessionPlan = T10SessionPlanLoader.load(
    Files.readAllBytes(testProjectRoot().resolve(TEST_PLAN_PATH)),
)

internal fun testIdentity(plan: T10SessionPlan = testPlan()): ExactIdentity = ExactIdentity(
    sourceSnapshotSha256 = testHash("source"),
    diffFileSha256 = testHash("diff"),
    apkFileSha256 = testHash("apk"),
    signer = SignerIdentity.fromCertificateHashes(listOf(testHash("certificate"))),
    registryFileSha256 = plan.template.registryFileSha256,
    packSha256 = plan.template.packSha256,
    candidateSetSha256 = testHash("candidates"),
    variant = T10Variant.VEHICLE_TEST,
    senderId = T10SenderId.SENDER_CLUSTER_NAV,
    componentId = T10ComponentId.COMPONENT_PROBE_ACTIVITY,
    permissionId = T10PermissionId.PERMISSION_NONE,
    profileId = T10ProfileId.PROFILE_SEAL_T10,
)

internal fun testTarget(identity: ExactIdentity = testIdentity()): ApprovedDadbTarget =
    ApprovedDadbTarget.create("127.0.0.1", 5037, "SERIAL-TEST", identity)

internal fun testHash(value: String): Sha256 = T10Canonical.sha256(value.toByteArray())

private const val TEST_PLAN_PATH =
    "docs/diagnostics/hud-sign-re/expansion/vehicle-session-plan.json"
