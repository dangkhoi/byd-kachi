package com.byd.clusternav.vehicleprobe

import com.byd.clusternav.vehicle.t10.BindingBlockReason
import com.byd.clusternav.vehicle.t10.DispatchOutcome
import com.byd.clusternav.vehicle.t10.FixedReadOperation
import com.byd.clusternav.vehicle.t10.MonotonicClock
import com.byd.clusternav.vehicle.t10.MonotonicDeadline
import com.byd.clusternav.vehicle.t10.OperationOutcome
import com.byd.clusternav.vehicle.t10.OperationRejectionReason
import com.byd.clusternav.vehicle.t10.TransportFailureKind
import com.byd.clusternav.vehicle.t10.VehicleTransport
import dadb.AdbAuthException
import dadb.AdbConnectException
import dadb.AdbConnectionClosedException
import dadb.AdbException
import dadb.AdbProtocolException
import dadb.AdbTimeoutException
import dadb.Dadb
import dadb.adbserver.AdbServer
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal data class FixedDadbResponse(val stdout: String, val stderr: String, val exitCode: Int)

internal enum class VerificationFixedReadCommand(internal val shellText: String) {
    SYNTHETIC_READ("printf T10_SYNTHETIC_FIXED_READ"),
}

internal interface FixedDadbSession : AutoCloseable {
    fun executeFixed(command: VerificationFixedReadCommand): FixedDadbResponse
    override fun close()
}

internal fun interface FixedDadbConnector {
    fun connect(
        adbServerHost: String,
        adbServerPort: Int,
        deviceQuery: String,
        connectTimeoutMs: Int,
        socketTimeoutMs: Int,
    ): FixedDadbSession
}

private sealed interface FixedCommandDecision {
    data class Blocked(val reason: BindingBlockReason) : FixedCommandDecision
    class Supported private constructor(internal val command: VerificationFixedReadCommand) : FixedCommandDecision {
        companion object {
            val SYNTHETIC_READ = Supported(VerificationFixedReadCommand.SYNTHETIC_READ)
        }
    }
}

private fun interface FixedCommandRenderer {
    fun render(operation: FixedReadOperation): FixedCommandDecision
}

/**
 * Strict host dadb transport. Production command rendering currently admits zero commands; a session
 * can therefore be created only by the closed verification seam used with fake sessions.
 */
internal class DadbVehicleTransport private constructor(
    private val approvedTarget: ApprovedDadbTarget,
    private val evidenceSink: LocalEvidenceSink,
    private val clock: MonotonicClock,
    private val connector: FixedDadbConnector,
    private val renderer: FixedCommandRenderer,
) : VehicleTransport, AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val activeCalls = ConcurrentHashMap.newKeySet<CallControl>()
    private val executor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(1),
        ThreadFactory { runnable -> Thread(runnable, WORKER_NAME).apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )

    internal constructor(
        approvedTarget: ApprovedDadbTarget,
        evidenceWriter: LocalEvidenceWriter,
        clock: MonotonicClock = SYSTEM_MONOTONIC_CLOCK,
    ) : this(approvedTarget, evidenceWriter, clock, PRODUCTION_CONNECTOR, PRODUCTION_RENDERER)

    override fun dispatch(operation: FixedReadOperation, deadline: MonotonicDeadline): DispatchOutcome {
        when (val command = renderer.render(operation)) {
            is FixedCommandDecision.Blocked -> return DispatchOutcome.Blocked(command.reason)
            is FixedCommandDecision.Supported -> return dispatchSupported(command.command, deadline)
        }
    }

    private fun dispatchSupported(
        command: VerificationFixedReadCommand,
        deadline: MonotonicDeadline,
    ): DispatchOutcome {
        if (closed.get()) return DispatchOutcome.TransportFailure(TransportFailureKind.IO_FAILURE)
        val now = clock.elapsedRealtimeMs()
        if (deadline.isExpiredAt(now)) return DispatchOutcome.TimeoutBeforeDispatch
        val waitMs = deadline.remainingAt(now).coerceAtMost(MAX_CALL_TIMEOUT_MS)
        if (waitMs <= 0) return DispatchOutcome.TimeoutBeforeDispatch

        val call = CallControl()
        activeCalls += call
        val future = try {
            executor.submit<FixedDadbResponse> { executeCall(call, command) }
        } catch (_: RejectedExecutionException) {
            activeCalls -= call
            return DispatchOutcome.TransportFailure(TransportFailureKind.IO_FAILURE)
        }
        call.future.set(future)
        if (call.cancelled.get()) future.cancel(true)

        val response = try {
            future.get(waitMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            cancelAndForget(call)
            return DispatchOutcome.TimeoutBeforeDispatch
        } catch (_: CancellationException) {
            cancelAndForget(call)
            return DispatchOutcome.TimeoutBeforeDispatch
        } catch (interrupted: InterruptedException) {
            cancelAndForget(call)
            Thread.currentThread().interrupt()
            return DispatchOutcome.TimeoutBeforeDispatch
        } catch (wrapped: ExecutionException) {
            return classifyExpectedFailure(requireNotNull(wrapped.cause))
        }

        if (!LocalEvidenceWriter.isWithinBounds(response.stdout, response.stderr)) {
            return DispatchOutcome.Completed(
                OperationOutcome.Rejected(OperationRejectionReason.CODEC_REJECTED),
            )
        }
        if (response.exitCode != 0) {
            return DispatchOutcome.Completed(
                OperationOutcome.Rejected(OperationRejectionReason.INVALID_TARGET_STATE),
            )
        }
        synchronized(call.publicationLock) {
            if (call.cancelled.get() || closed.get() || deadline.isExpiredAt(clock.elapsedRealtimeMs())) {
                return DispatchOutcome.TimeoutBeforeDispatch
            }
            val evidenceId = try {
                evidenceSink.write(response.stdout, response.stderr, response.exitCode) {
                    !call.cancelled.get() && !closed.get() &&
                        !deadline.isExpiredAt(clock.elapsedRealtimeMs())
                }
            } catch (_: EvidencePublicationCancelledException) {
                return DispatchOutcome.TimeoutBeforeDispatch
            } catch (_: EvidencePayloadTooLargeException) {
                return DispatchOutcome.Completed(
                    OperationOutcome.Rejected(OperationRejectionReason.CODEC_REJECTED),
                )
            } catch (_: IOException) {
                return DispatchOutcome.TransportFailure(TransportFailureKind.IO_FAILURE)
            }
            return DispatchOutcome.Completed(OperationOutcome.Success(evidenceId))
        }
    }

    private fun executeCall(
        call: CallControl,
        command: VerificationFixedReadCommand,
    ): FixedDadbResponse {
        try {
            if (call.cancelled.get() || closed.get()) throw CancellationException("T10 call cancelled")
            val session = connector.connect(
                approvedTarget.adbServerHost,
                approvedTarget.adbServerPort,
                approvedTarget.deviceQuery(),
                CONNECT_TIMEOUT_MS,
                SOCKET_TIMEOUT_MS,
            )
            if (!call.session.compareAndSet(null, session)) {
                session.close()
                throw CancellationException("T10 session was not admitted")
            }
            if (call.cancelled.get() || closed.get()) {
                closeActiveSession(call)
                throw CancellationException("T10 call cancelled after connect")
            }
            return session.executeFixed(command)
        } finally {
            closeActiveSession(call)
            activeCalls -= call
        }
    }

    private fun classifyExpectedFailure(cause: Throwable): DispatchOutcome = when (cause) {
        is AdbAuthException -> DispatchOutcome.TransportFailure(TransportFailureKind.TARGET_UNAUTHORIZED)
        is AdbTimeoutException, is SocketTimeoutException -> DispatchOutcome.TimeoutBeforeDispatch
        is AdbConnectException -> DispatchOutcome.TransportFailure(TransportFailureKind.CONNECTION_UNAVAILABLE)
        is AdbConnectionClosedException -> DispatchOutcome.TransportFailure(TransportFailureKind.TARGET_OFFLINE)
        is AdbProtocolException -> DispatchOutcome.TransportFailure(TransportFailureKind.PROTOCOL_FAILURE)
        is AdbException, is IOException -> DispatchOutcome.TransportFailure(TransportFailureKind.IO_FAILURE)
        is CancellationException -> DispatchOutcome.TimeoutBeforeDispatch
        is RuntimeException -> throw cause
        is Error -> throw cause
        else -> throw IllegalStateException("unexpected checked T10 transport failure", cause)
    }

    private fun cancelAndForget(call: CallControl) {
        try {
            cancelCall(call)
        } finally {
            activeCalls -= call
        }
    }

    private fun cancelCall(call: CallControl) {
        synchronized(call.publicationLock) {
            call.cancelled.set(true)
            call.future.get()?.cancel(true)
            closeActiveSession(call)
        }
    }

    private fun closeActiveSession(call: CallControl) {
        call.session.getAndSet(null)?.close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        var runtimeFailure: RuntimeException? = null
        var fatalFailure: Error? = null
        activeCalls.forEach { call ->
            try {
                cancelCall(call)
            } catch (failure: RuntimeException) {
                if (runtimeFailure == null) runtimeFailure = failure
                else requireNotNull(runtimeFailure).addSuppressed(failure)
            } catch (failure: Error) {
                if (fatalFailure == null) fatalFailure = failure
                else requireNotNull(fatalFailure).addSuppressed(failure)
            }
        }
        executor.shutdownNow()
        fatalFailure?.let { throw it }
        runtimeFailure?.let { throw it }
    }

    internal fun queuedCallCountForVerification(): Int = executor.queue.size

    companion object {
        internal const val CONNECT_TIMEOUT_MS = 1_500
        internal const val SOCKET_TIMEOUT_MS = 4_000
        internal const val MAX_CALL_TIMEOUT_MS = 10_000L
        private const val WORKER_NAME = "t10-dadb-worker"

        internal fun forVerification(
            approvedTarget: ApprovedDadbTarget,
            evidenceSink: LocalEvidenceSink,
            clock: MonotonicClock,
            connector: FixedDadbConnector,
            supportedOperation: FixedReadOperation,
        ): DadbVehicleTransport {
            val renderer = FixedCommandRenderer { operation ->
                if (operation == supportedOperation) FixedCommandDecision.Supported.SYNTHETIC_READ
                else FixedCommandDecision.Blocked(BindingBlockReason.UNPROVEN_APP_REACHABILITY)
            }
            return DadbVehicleTransport(approvedTarget, evidenceSink, clock, connector, renderer)
        }

        internal fun forProductionVerification(
            approvedTarget: ApprovedDadbTarget,
            evidenceSink: LocalEvidenceSink,
            clock: MonotonicClock,
            connector: FixedDadbConnector,
        ): DadbVehicleTransport = DadbVehicleTransport(
            approvedTarget,
            evidenceSink,
            clock,
            connector,
            PRODUCTION_RENDERER,
        )

        private val SYSTEM_MONOTONIC_CLOCK = MonotonicClock { System.nanoTime() / 1_000_000L }
        private val PRODUCTION_RENDERER = FixedCommandRenderer {
            FixedCommandDecision.Blocked(BindingBlockReason.UNPROVEN_APP_REACHABILITY)
        }
        private val PRODUCTION_CONNECTOR = FixedDadbConnector { host, port, query, connect, socket ->
            AdbServer.createDadb(host, port, query, connect, socket).asFixedSession()
        }
    }

    private class CallControl {
        val cancelled = AtomicBoolean(false)
        val session = AtomicReference<FixedDadbSession?>()
        val future = AtomicReference<Future<*>?>()
        val publicationLock = Any()
    }
}

private fun Dadb.asFixedSession(): FixedDadbSession = object : FixedDadbSession {
    override fun executeFixed(command: VerificationFixedReadCommand): FixedDadbResponse {
        val response = shell(command.shellText)
        return FixedDadbResponse(response.output, response.errorOutput, response.exitCode)
    }

    override fun close() {
        this@asFixedSession.close()
    }
}
