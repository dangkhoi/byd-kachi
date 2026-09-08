package com.byd.clusternav.vehicleprobe

import com.byd.clusternav.vehicle.t10.MonotonicClock
import com.byd.clusternav.vehicle.t10.RepoRelativePath
import com.byd.clusternav.vehicle.t10.T10ArtifactHandoff
import com.byd.clusternav.vehicle.t10.T10Canonical
import com.byd.clusternav.vehicle.t10.T10IdentityLoader
import com.byd.clusternav.vehicle.t10.T10NonceSource
import com.byd.clusternav.vehicle.t10.T10SessionEngine
import com.byd.clusternav.vehicle.t10.T10SessionPlan
import com.byd.clusternav.vehicle.t10.T10SessionPlanLoader
import com.byd.clusternav.vehicle.t10.T10SessionResultCode
import com.byd.clusternav.vehicle.t10.T10SessionRuntime
import com.byd.clusternav.vehicle.t10.T10SessionStartResult
import com.byd.clusternav.vehicle.t10.T10SessionTransportFactory
import java.io.IOException
import java.nio.file.Path
import java.security.SecureRandom

internal fun interface T10RuntimeFactory {
    fun create(plan: T10SessionPlan, handoff: T10ArtifactHandoff): T10SessionRuntime
}

/** Fixed-path host entry. It delegates all admission ordering to the core engine before local dadb I/O. */
internal class HudSignSessionRunner private constructor(
    private val repositoryRoot: Path,
    private val engine: T10SessionEngine,
    private val runtimeFactory: T10RuntimeFactory,
) {
    constructor() : this(Path.of("").toAbsolutePath().normalize())

    internal constructor(repositoryRoot: Path) : this(
        repositoryRoot.toAbsolutePath().normalize(),
        T10SessionEngine(),
        productionRuntimeFactory(repositoryRoot.toAbsolutePath().normalize()),
    )

    internal constructor(repositoryRoot: Path, runtimeFactory: T10RuntimeFactory) : this(
        repositoryRoot.toAbsolutePath().normalize(),
        T10SessionEngine(),
        runtimeFactory,
    )

    fun run(args: Array<String>): Int {
        if (args.isNotEmpty()) return T10SessionResultCode.STRICT_MODEL_INVALID.processCode
        val plan = try {
            T10SessionPlanLoader.load(PLAN_PATH.readBytesNoFollow(repositoryRoot))
        } catch (_: IOException) {
            return T10SessionResultCode.STRICT_MODEL_INVALID.processCode
        } catch (_: IllegalArgumentException) {
            return T10SessionResultCode.STRICT_MODEL_INVALID.processCode
        }
        val handoff = try {
            T10IdentityLoader.loadHandoff(HANDOFF_PATH.readBytesNoFollow(repositoryRoot))
        } catch (_: IOException) {
            return T10SessionResultCode.EXACT_IDENTITY_INVALID.processCode
        } catch (_: IllegalArgumentException) {
            return T10SessionResultCode.EXACT_IDENTITY_INVALID.processCode
        }
        val runtime = try {
            runtimeFactory.create(plan, handoff)
        } catch (_: IOException) {
            return T10SessionResultCode.RUNTIME_SETUP_FAILED.processCode
        } catch (_: IllegalArgumentException) {
            return T10SessionResultCode.RUNTIME_SETUP_FAILED.processCode
        }
        val result = engine.start(plan, handoff, runtime)
        if (result is T10SessionStartResult.Started) {
            (result.session.transport as? AutoCloseable)?.close()
        }
        return result.processCode
    }

    companion object {
        private val HANDOFF_PATH = RepoRelativePath.parse(
            "docs/_handoff/hud-sign-vehicle-test-candidate.json",
        )
        private val PLAN_PATH = RepoRelativePath.parse(
            "docs/diagnostics/hud-sign-re/expansion/vehicle-session-plan.json",
        )

        private fun productionRuntimeFactory(repositoryRoot: Path): T10RuntimeFactory =
            T10RuntimeFactory { plan, _ ->
                val monotonicClock = MonotonicClock { System.nanoTime() / 1_000_000L }
                val authorization = T10LocalAuthorization(repositoryRoot, plan.fileSha256)
                val resultStore = T10ResultStore.forLocalSession(repositoryRoot)
                T10SessionRuntime(
                    targetAuthorizationLoader = authorization,
                    transportFactory = T10SessionTransportFactory { freeze, target ->
                        DadbVehicleTransport(
                            approvedTarget = authorization.approvedFor(target),
                            evidenceWriter = LocalEvidenceWriter(repositoryRoot, freeze.sessionId),
                            clock = monotonicClock,
                        )
                    },
                    ledgerSink = resultStore,
                    clock = monotonicClock,
                    nonceSource = secureNonceSource(),
                )
            }

        private fun secureNonceSource(): T10NonceSource = T10NonceSource {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            T10Canonical.sha256(bytes)
        }
    }
}
