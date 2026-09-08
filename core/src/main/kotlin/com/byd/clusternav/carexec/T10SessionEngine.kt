package com.byd.clusternav.vehicle.t10

import java.util.Collections
import java.util.LinkedHashMap

enum class T10SessionResultCode(val processCode: Int) {
    STARTED(0),
    STRICT_MODEL_INVALID(20),
    SESSION_N_SHAPE_INVALID(21),
    EXACT_IDENTITY_INVALID(22),
    BINDING_BLOCKED(23),
    TARGET_AUTHORIZATION_UNAVAILABLE(24),
    TARGET_IDENTITY_MISMATCH(25),
    INERT_TEMPLATE(26),
    RUNTIME_SETUP_FAILED(27),
    SESSION_START_FAILED(28);

    companion object {
        fun fromProcessCode(value: Int): T10SessionResultCode = entries.singleOrNull {
            it.processCode == value
        } ?: throw IllegalArgumentException("unknown T10 session process code")
    }
}

data class T10BlockedReadBinding(
    val rowId: T10RowId,
    val probeId: T10ProbeId,
    val reason: BindingBlockReason,
)

sealed interface T10SessionStartResult {
    val code: T10SessionResultCode
    val processCode: Int get() = code.processCode

    class Blocked internal constructor(
        override val code: T10SessionResultCode,
        blockedReadBindings: Collection<T10BlockedReadBinding> = emptyList(),
    ) : T10SessionStartResult {
        val blockedReadBindings: List<T10BlockedReadBinding> =
            Collections.unmodifiableList(ArrayList(blockedReadBindings))

        init {
            require(code != T10SessionResultCode.STARTED)
            require(
                code == T10SessionResultCode.BINDING_BLOCKED || blockedReadBindings.isEmpty(),
            )
        }
    }

    class Started internal constructor(val session: T10StartedSession) : T10SessionStartResult {
        override val code = T10SessionResultCode.STARTED
    }
}

data class T10AuthorizedTarget(val exactIdentity: ExactIdentity)

fun interface T10TargetAuthorizationLoader {
    fun load(expectedIdentity: ExactIdentity): T10AuthorizedTarget?
}

fun interface T10SessionTransportFactory {
    fun create(freeze: SessionPackFreeze, target: T10AuthorizedTarget): VehicleTransport
}

fun interface T10SessionLedgerSink {
    fun appendSessionStart(freeze: SessionPackFreeze)
}

fun interface T10NonceSource {
    fun nextNonceSha256(): Sha256
}

data class T10SessionRuntime(
    val targetAuthorizationLoader: T10TargetAuthorizationLoader,
    val transportFactory: T10SessionTransportFactory,
    val ledgerSink: T10SessionLedgerSink,
    val clock: MonotonicClock,
    val nonceSource: T10NonceSource,
)

class T10StartedSession internal constructor(
    val freeze: SessionPackFreeze,
    val target: T10AuthorizedTarget,
    val transport: VehicleTransport,
    readBindings: Map<T10RowId, FixedBinding.Supported>,
) {
    val readBindings: Map<T10RowId, FixedBinding.Supported> =
        Collections.unmodifiableMap(LinkedHashMap(readBindings))
}

class T10SessionEngine private constructor(
    private val bindingResolver: T10ReadBindingResolver,
    private val forceShapeRejectionForVerification: Boolean,
) {
    constructor() : this(T10FixedOperationCatalog, false)

    companion object {
        internal fun withResolverForVerification(resolver: T10ReadBindingResolver): T10SessionEngine =
            T10SessionEngine(resolver, false)

        internal fun withShapeRejectionForVerification(
            resolver: T10ReadBindingResolver,
        ): T10SessionEngine = T10SessionEngine(resolver, true)
    }

    fun start(
        plan: T10SessionPlan,
        handoff: T10ArtifactHandoff,
        runtime: T10SessionRuntime,
    ): T10SessionStartResult {
        val strictPlan = strictPlan(plan)
            ?: return blocked(T10SessionResultCode.STRICT_MODEL_INVALID)
        val strictHandoff = strictHandoff(handoff)
            ?: return blocked(T10SessionResultCode.EXACT_IDENTITY_INVALID)

        if (forceShapeRejectionForVerification || !isExactSessionN(strictPlan)) {
            return blocked(T10SessionResultCode.SESSION_N_SHAPE_INVALID)
        }
        if (!isCrossIdentityEligible(strictPlan.template, strictHandoff)) {
            return blocked(T10SessionResultCode.EXACT_IDENTITY_INVALID)
        }

        val resolvedRows = resolveEveryReadRow(strictPlan)
        val blockedReads = resolvedRows.mapNotNull { (row, binding) ->
            (binding as? FixedBinding.Blocked)?.let {
                T10BlockedReadBinding(row.rowId, row.probeIds.single(), it.reason)
            }
        }
        if (blockedReads.isNotEmpty()) {
            return T10SessionStartResult.Blocked(T10SessionResultCode.BINDING_BLOCKED, blockedReads)
        }

        val resolvedIdentity = strictPlan.template.identityRequirement as? T10IdentityRequirement.Resolved
            ?: return blocked(T10SessionResultCode.INERT_TEMPLATE)
        if (resolvedIdentity.exactIdentity != strictHandoff.exactIdentity) {
            return blocked(T10SessionResultCode.EXACT_IDENTITY_INVALID)
        }

        val target = try {
            runtime.targetAuthorizationLoader.load(strictHandoff.exactIdentity)
        } catch (_: Exception) {
            null
        } ?: return blocked(T10SessionResultCode.TARGET_AUTHORIZATION_UNAVAILABLE)
        if (target.exactIdentity != strictHandoff.exactIdentity) {
            return blocked(T10SessionResultCode.TARGET_IDENTITY_MISMATCH)
        }

        val freeze = prepareFreeze(strictPlan, runtime)
            ?: return blocked(T10SessionResultCode.RUNTIME_SETUP_FAILED)
        val transport = try {
            runtime.transportFactory.create(freeze, target)
        } catch (_: Exception) {
            return blocked(T10SessionResultCode.RUNTIME_SETUP_FAILED)
        }
        try {
            runtime.ledgerSink.appendSessionStart(freeze)
        } catch (_: Exception) {
            return blocked(T10SessionResultCode.SESSION_START_FAILED)
        }

        val bindings = resolvedRows.associateTo(LinkedHashMap()) { (row, binding) ->
            row.rowId to (binding as FixedBinding.Supported)
        }
        return T10SessionStartResult.Started(T10StartedSession(freeze, target, transport, bindings))
    }

    private fun strictPlan(plan: T10SessionPlan): T10SessionPlan? = try {
        val loaded = T10SessionPlanLoader.load(plan.toCanonicalBytes())
        loaded.takeIf {
            it.fileSha256 == plan.fileSha256 && it.selfSha256 == plan.selfSha256 &&
                it.template == plan.template && it.toCanonicalBytes().contentEquals(plan.toCanonicalBytes())
        }
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun strictHandoff(handoff: T10ArtifactHandoff): T10ArtifactHandoff? = try {
        val loaded = T10IdentityLoader.loadHandoff(handoff.toCanonicalBytes())
        loaded.takeIf {
            it.selfSha256 == handoff.selfSha256 && it.exactIdentity == handoff.exactIdentity &&
                it.blockerIds == handoff.blockerIds
        }
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun resolveEveryReadRow(
        plan: T10SessionPlan,
    ): List<Pair<SessionRowTemplate, FixedBinding>> = plan.template.rows
        .filter { it.kind == T10SessionRowKind.READ_ONLY }
        .map { row ->
            val probeId = row.probeIds.single()
            val binding = try {
                bindingResolver.resolve(probeId)
            } catch (_: Exception) {
                FixedBinding.Blocked(BindingBlockReason.UNPROVEN_APP_REACHABILITY)
            }
            row to normalizeBinding(probeId, binding)
        }

    private fun normalizeBinding(probeId: T10ProbeId, binding: FixedBinding): FixedBinding = when (binding) {
        is FixedBinding.Blocked -> binding
        is FixedBinding.Supported -> if (binding.operation.probeId == probeId) {
            binding
        } else {
            FixedBinding.Blocked(BindingBlockReason.EXACT_IDENTITY_MISMATCH)
        }
    }

    private fun prepareFreeze(plan: T10SessionPlan, runtime: T10SessionRuntime): SessionPackFreeze? = try {
        val startElapsedMs = runtime.clock.elapsedRealtimeMs()
        if (startElapsedMs < 0) return null
        val nonceSha256 = runtime.nonceSource.nextNonceSha256()
        SessionFreezeFactory.freeze(plan, nonceSha256, startElapsedMs)
    } catch (_: Exception) {
        null
    }

    private fun isCrossIdentityEligible(
        template: SessionPackTemplate,
        handoff: T10ArtifactHandoff,
    ): Boolean {
        val identity = handoff.exactIdentity
        val permissionEligible = when (identity.componentId) {
            T10ComponentId.COMPONENT_PROBE_RECEIVER ->
                identity.permissionId == T10PermissionId.PERMISSION_VENDOR_CAR
            T10ComponentId.COMPONENT_PROBE_ACTIVITY ->
                identity.permissionId == T10PermissionId.PERMISSION_NONE
        }
        val resolvedMatches = (template.identityRequirement as? T10IdentityRequirement.Resolved)
            ?.exactIdentity?.let { it == identity } ?: true
        return identity.packSha256 == template.packSha256 &&
            identity.registryFileSha256 == template.registryFileSha256 &&
            identity.variant == T10Variant.VEHICLE_TEST &&
            identity.profileId == T10ProfileId.PROFILE_SEAL_T10 &&
            permissionEligible && resolvedMatches
    }

    private fun isExactSessionN(plan: T10SessionPlan): Boolean {
        val template = plan.template
        if (template.rows.size != 11 || template.rows.map { it.rowId } != T10RowId.entries ||
            template.rows.count { it.kind == T10SessionRowKind.READ_ONLY } != 7 ||
            template.rows.count { it.kind == T10SessionRowKind.MILESTONE } != 4 ||
            template.rows.any { it.kind == T10SessionRowKind.MUTATION } ||
            template.allowedMutationCandidateRevisionIds.isNotEmpty() ||
            template.allowedProbeIds != T10ProbeId.entries ||
            template.allowedCandidateRevisionIds != listOf(T10CandidateRevisionId.H8_PROPERTY_CONFIG_METADATA_R3)
        ) return false

        val discoveryRows = template.rows.take(6)
        if (discoveryRows.map { it.probeIds.singleOrNull() } != T10ProbeId.entries) return false
        if (discoveryRows.any {
                it.kind != T10SessionRowKind.READ_ONLY || it.candidateRevisionId != null ||
                    it.resultIdentityId != null || it.observations.isNotEmpty() ||
                    it.requiredSurfaces.isNotEmpty() || it.dependsOnRowIds.isNotEmpty()
            }
        ) return false

        val h8 = template.rows[6]
        if (h8.kind != T10SessionRowKind.READ_ONLY ||
            h8.candidateRevisionId != T10CandidateRevisionId.H8_PROPERTY_CONFIG_METADATA_R3 ||
            h8.probeIds != listOf(T10ProbeId.READ_PROPERTY_CONFIG) || h8.resultIdentityId != null ||
            h8.observations.isNotEmpty() ||
            h8.requiredSurfaces != listOf(T10RequiredSurface.HUD_NAV_MAP) ||
            h8.dependsOnRowIds.isNotEmpty()
        ) return false

        val milestones = listOf(
            Triple(T10ResultIdentityId.D_M1, T10ObservationId.M1_SURFACE_RESULT, T10RequiredSurface.HUD_NAV_MAP),
            Triple(T10ResultIdentityId.D_M2, T10ObservationId.M2_SURFACE_RESULT, T10RequiredSurface.HUD_ROAD_NAME),
            Triple(T10ResultIdentityId.D_M3, T10ObservationId.M3_SURFACE_RESULT, T10RequiredSurface.CLUSTER_SPEED_SIGN),
            Triple(T10ResultIdentityId.D_M4, T10ObservationId.M4_SURFACE_RESULT, T10RequiredSurface.HUD_SPEED_SIGN),
        )
        return template.rows.drop(7).zip(milestones).all { (row, expected) ->
            row.kind == T10SessionRowKind.MILESTONE && row.candidateRevisionId == null &&
                row.resultIdentityId == expected.first && row.probeIds.isEmpty() &&
                row.observations == listOf(expected.second) &&
                row.requiredSurfaces == listOf(expected.third) && row.dependsOnRowIds.isEmpty()
        }
    }

    private fun blocked(code: T10SessionResultCode): T10SessionStartResult.Blocked =
        T10SessionStartResult.Blocked(code)
}

enum class T10SameSessionState { FROZEN_DISCOVERY_ONLY }
enum class T10DiscoveryDisposition { DISCOVERY_ONLY }

data class T10DiscoveryRecord(
    val rowId: T10RowId,
    val probeId: T10ProbeId,
    val evidenceId: LocalEvidenceId,
    val disposition: T10DiscoveryDisposition = T10DiscoveryDisposition.DISCOVERY_ONLY,
)

class T10SessionInvariantSnapshot internal constructor(
    val templateFileSha256: Sha256,
    val exactIdentitySha256: Sha256,
    rowIds: Collection<T10RowId>,
    allowedProbeIds: Collection<T10ProbeId>,
    allowedCandidateRevisionIds: Collection<T10CandidateRevisionId>,
    allowedMutationCandidateRevisionIds: Collection<T10CandidateRevisionId>,
    candidateStates: Map<T10CatalogCandidateId, T10CatalogCandidateState>,
) {
    val rowIds: List<T10RowId> = Collections.unmodifiableList(ArrayList(rowIds))
    val allowedProbeIds: List<T10ProbeId> = Collections.unmodifiableList(ArrayList(allowedProbeIds))
    val allowedCandidateRevisionIds: List<T10CandidateRevisionId> =
        Collections.unmodifiableList(ArrayList(allowedCandidateRevisionIds))
    val allowedMutationCandidateRevisionIds: List<T10CandidateRevisionId> =
        Collections.unmodifiableList(ArrayList(allowedMutationCandidateRevisionIds))
    val candidateStates: Map<T10CatalogCandidateId, T10CatalogCandidateState> =
        Collections.unmodifiableMap(LinkedHashMap(candidateStates))
    val state = T10SameSessionState.FROZEN_DISCOVERY_ONLY

    override fun equals(other: Any?): Boolean = other is T10SessionInvariantSnapshot &&
        templateFileSha256 == other.templateFileSha256 && exactIdentitySha256 == other.exactIdentitySha256 &&
        rowIds == other.rowIds && allowedProbeIds == other.allowedProbeIds &&
        allowedCandidateRevisionIds == other.allowedCandidateRevisionIds &&
        allowedMutationCandidateRevisionIds == other.allowedMutationCandidateRevisionIds &&
        candidateStates == other.candidateStates && state == other.state

    override fun hashCode(): Int = listOf(
        templateFileSha256,
        exactIdentitySha256,
        rowIds,
        allowedProbeIds,
        allowedCandidateRevisionIds,
        allowedMutationCandidateRevisionIds,
        candidateStates,
        state,
    ).hashCode()
}

class T10SameSessionQuarantine private constructor(
    private val rows: List<SessionRowTemplate>,
    private val invariant: T10SessionInvariantSnapshot,
) {
    private val records = mutableListOf<T10DiscoveryRecord>()

    @Synchronized
    fun recordDiscovery(
        rowId: T10RowId,
        probeId: T10ProbeId,
        evidenceId: LocalEvidenceId,
    ): T10DiscoveryRecord {
        val row = rows.singleOrNull { it.rowId == rowId }
        require(row?.kind == T10SessionRowKind.READ_ONLY && row.probeIds == listOf(probeId)) {
            "discovery must remain bound to its frozen read row"
        }
        return T10DiscoveryRecord(rowId, probeId, evidenceId).also(records::add)
    }

    @Synchronized
    fun records(): List<T10DiscoveryRecord> = Collections.unmodifiableList(ArrayList(records))

    fun snapshot(): T10SessionInvariantSnapshot = invariant

    companion object {
        fun afterStart(session: T10StartedSession): T10SameSessionQuarantine = from(
            templateFileSha256 = session.freeze.templateFileSha256,
            exactIdentity = session.freeze.exactIdentity,
            rows = session.freeze.rows.map { it.template },
            allowedProbeIds = session.freeze.allowedProbeIds,
            allowedCandidateRevisionIds = session.freeze.allowedCandidateRevisionIds,
            allowedMutationCandidateRevisionIds = session.freeze.allowedMutationCandidateRevisionIds,
        )

        internal fun afterHypotheticalStart(
            plan: T10SessionPlan,
            handoff: T10ArtifactHandoff,
        ): T10SameSessionQuarantine = from(
            templateFileSha256 = plan.fileSha256,
            exactIdentity = handoff.exactIdentity,
            rows = plan.template.rows,
            allowedProbeIds = plan.template.allowedProbeIds,
            allowedCandidateRevisionIds = plan.template.allowedCandidateRevisionIds,
            allowedMutationCandidateRevisionIds = plan.template.allowedMutationCandidateRevisionIds,
        )

        private fun from(
            templateFileSha256: Sha256,
            exactIdentity: ExactIdentity,
            rows: Collection<SessionRowTemplate>,
            allowedProbeIds: Collection<T10ProbeId>,
            allowedCandidateRevisionIds: Collection<T10CandidateRevisionId>,
            allowedMutationCandidateRevisionIds: Collection<T10CandidateRevisionId>,
        ): T10SameSessionQuarantine {
            val copiedRows = Collections.unmodifiableList(ArrayList(rows))
            val states = T10CatalogCandidateId.entries.associateWith {
                T10FixedOperationCatalog.resolveCandidate(it).state
            }
            val snapshot = T10SessionInvariantSnapshot(
                templateFileSha256,
                exactIdentity.canonicalSha256(),
                copiedRows.map { it.rowId },
                allowedProbeIds,
                allowedCandidateRevisionIds,
                allowedMutationCandidateRevisionIds,
                states,
            )
            return T10SameSessionQuarantine(copiedRows, snapshot)
        }
    }
}
