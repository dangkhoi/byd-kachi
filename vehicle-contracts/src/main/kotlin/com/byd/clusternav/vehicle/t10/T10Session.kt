package com.byd.clusternav.vehicle.t10

import java.util.Collections

/** The only six probes admitted by the neutral T10 Session N contract. */
enum class T10ProbeId(val wireName: String) {
    LIST_PACKAGE_METADATA("PROBE-LIST-PACKAGE-METADATA"),
    LIST_PROPERTY_CONFIGS("PROBE-LIST-PROPERTY-CONFIGS"),
    LIST_SERVICE_METADATA("PROBE-LIST-SERVICE-METADATA"),
    READ_PACKAGE_METADATA("PROBE-READ-PACKAGE-METADATA"),
    READ_PROPERTY_CONFIG("PROBE-READ-PROPERTY-CONFIG"),
    READ_SERVICE_METADATA("PROBE-READ-SERVICE-METADATA");

    companion object {
        fun parse(value: String) = entries.singleOrNull { it.wireName == value }
            ?: throw IllegalArgumentException("unknown T10 probe ID")
    }
}

enum class FixedResultCodec {
    PACKAGE_METADATA_LIST, PROPERTY_CONFIG_LIST, SERVICE_METADATA_LIST,
    PACKAGE_METADATA, PROPERTY_CONFIG, SERVICE_METADATA,
}

enum class FixedReadOperation(
    val wireName: String,
    val probeId: T10ProbeId,
    val codec: FixedResultCodec,
) {
    LIST_PACKAGE_METADATA("OP-READ-LIST-PACKAGE-METADATA", T10ProbeId.LIST_PACKAGE_METADATA, FixedResultCodec.PACKAGE_METADATA_LIST),
    LIST_PROPERTY_CONFIGS("OP-READ-LIST-PROPERTY-CONFIGS", T10ProbeId.LIST_PROPERTY_CONFIGS, FixedResultCodec.PROPERTY_CONFIG_LIST),
    LIST_SERVICE_METADATA("OP-READ-LIST-SERVICE-METADATA", T10ProbeId.LIST_SERVICE_METADATA, FixedResultCodec.SERVICE_METADATA_LIST),
    READ_PACKAGE_METADATA("OP-READ-PACKAGE-METADATA", T10ProbeId.READ_PACKAGE_METADATA, FixedResultCodec.PACKAGE_METADATA),
    READ_PROPERTY_CONFIG("OP-READ-PROPERTY-CONFIG", T10ProbeId.READ_PROPERTY_CONFIG, FixedResultCodec.PROPERTY_CONFIG),
    READ_SERVICE_METADATA("OP-READ-SERVICE-METADATA", T10ProbeId.READ_SERVICE_METADATA, FixedResultCodec.SERVICE_METADATA);

    companion object {
        fun parse(value: String) = entries.singleOrNull { it.wireName == value }
            ?: throw IllegalArgumentException("unknown fixed read operation ID")
        fun forProbe(probeId: T10ProbeId) = entries.single { it.probeId == probeId }
    }
}

enum class BindingBlockReason {
    UNPROVEN_APP_REACHABILITY,
    UNPROVEN_PERMISSION_BINDING,
    UNSUPPORTED_EXACT_PROFILE,
    EXACT_IDENTITY_MISMATCH,
    MISSING_OPERATIONAL_AUTHORIZATION,
}

sealed interface FixedBinding {
    data class Supported(val operation: FixedReadOperation, val codec: FixedResultCodec) : FixedBinding {
        init { require(codec == operation.codec) { "operation codec mismatch" } }
    }
    data class Blocked(val reason: BindingBlockReason) : FixedBinding
}

enum class T10SessionRowKind { READ_ONLY, MUTATION, MILESTONE }
enum class T10RiskClass { LOW, MEDIUM, HIGH }
enum class T10RequiredSurface { HUD_NAV_MAP, HUD_ROAD_NAME, CLUSTER_SPEED_SIGN, HUD_SPEED_SIGN }

enum class T10CandidateRevisionId(val wireName: String) {
    H8_PROPERTY_CONFIG_METADATA_R3("CAND-H-008-PROPERTY-CONFIG-METADATA@3");
    companion object {
        fun parse(value: String) = entries.singleOrNull { it.wireName == value }
            ?: throw IllegalArgumentException("unknown T10 candidate revision ID")
    }
}

enum class T10RowId(val wireName: String) {
    DISCOVERY_LIST_PACKAGE_METADATA("ROW-0001-DISCOVERY-LIST-PACKAGE-METADATA"),
    DISCOVERY_LIST_PROPERTY_CONFIGS("ROW-0002-DISCOVERY-LIST-PROPERTY-CONFIGS"),
    DISCOVERY_LIST_SERVICE_METADATA("ROW-0003-DISCOVERY-LIST-SERVICE-METADATA"),
    DISCOVERY_READ_PACKAGE_METADATA("ROW-0004-DISCOVERY-READ-PACKAGE-METADATA"),
    DISCOVERY_READ_PROPERTY_CONFIG("ROW-0005-DISCOVERY-READ-PROPERTY-CONFIG"),
    DISCOVERY_READ_SERVICE_METADATA("ROW-0006-DISCOVERY-READ-SERVICE-METADATA"),
    READ_H8_PROPERTY_CONFIG_METADATA("ROW-0007-READ-H-008-PROPERTY-CONFIG-METADATA"),
    D_M1_SURFACE("ROW-0008-D-M1-SURFACE"),
    D_M2_SURFACE("ROW-0009-D-M2-SURFACE"),
    D_M3_SURFACE("ROW-0010-D-M3-SURFACE"),
    D_M4_SURFACE("ROW-0011-D-M4-SURFACE");
    companion object {
        fun parse(value: String) = entries.singleOrNull { it.wireName == value }
            ?: throw IllegalArgumentException("unknown T10 row ID")
    }
}

enum class T10ObservationId(val wireName: String) {
    M1_SURFACE_RESULT("OBS-M1-SURFACE-RESULT"), M2_SURFACE_RESULT("OBS-M2-SURFACE-RESULT"),
    M3_SURFACE_RESULT("OBS-M3-SURFACE-RESULT"), M4_SURFACE_RESULT("OBS-M4-SURFACE-RESULT");
    companion object {
        fun parse(value: String) = entries.singleOrNull { it.wireName == value }
            ?: throw IllegalArgumentException("unknown T10 observation ID")
    }
}

enum class T10ResultIdentityId(val wireName: String) {
    D_M1("RESULT-D-M1-0001"), D_M2("RESULT-D-M2-0001"),
    D_M3("RESULT-D-M3-0001"), D_M4("RESULT-D-M4-0001");
    companion object {
        fun parse(value: String) = entries.singleOrNull { it.wireName == value }
            ?: throw IllegalArgumentException("unknown T10 result identity ID")
    }
}

class SessionId private constructor(val value: String) {
    override fun equals(other: Any?) = other is SessionId && value == other.value
    override fun hashCode() = value.hashCode()
    override fun toString() = value
    companion object {
        private val PATTERN = Regex("^SESSION-[0-9A-F]{16}$")
        fun parse(value: String): SessionId {
            require(PATTERN.matches(value)) { "invalid T10 session ID" }
            return SessionId(value)
        }
    }
}

class SessionRowTemplate private constructor(
    val rowId: T10RowId,
    val kind: T10SessionRowKind,
    val candidateRevisionId: T10CandidateRevisionId?,
    val resultIdentityId: T10ResultIdentityId?,
    probeIds: List<T10ProbeId>,
    observations: List<T10ObservationId>,
    requiredSurfaces: List<T10RequiredSurface>,
    dependsOnRowIds: List<T10RowId>,
    val dependencyReady: Boolean,
    val dependencyUncertainty: Long,
    val estimatedTimeMs: Long,
    val evidenceStrength: Long,
    val informationGain: Long,
    val mutationRisk: Long,
    val phaseRank: Long,
    val reversibility: Long,
    val riskClass: T10RiskClass,
    val score: Long,
) {
    val probeIds = immutableList(probeIds)
    val observations = immutableList(observations)
    val requiredSurfaces = immutableList(requiredSurfaces)
    val dependsOnRowIds = immutableList(dependsOnRowIds)

    init {
        listOf(dependencyUncertainty, evidenceStrength, informationGain, mutationRisk, reversibility).forEach {
            require(it in 0..100)
        }
        require(estimatedTimeMs in 0..86_400_000 && phaseRank in 0..9_999)
        require(riskClass == riskClassFor(mutationRisk) && score == checkedScore(this))
        require(probeIds == probeIds.distinct() && observations == observations.distinct())
        require(requiredSurfaces == requiredSurfaces.distinct() && dependsOnRowIds == dependsOnRowIds.distinct())
        when (kind) {
            T10SessionRowKind.READ_ONLY -> require(
                probeIds.size == 1 && observations.isEmpty() && resultIdentityId == null &&
                    ((candidateRevisionId == null && requiredSurfaces.isEmpty()) ||
                        (candidateRevisionId != null && requiredSurfaces.isNotEmpty())),
            )
            T10SessionRowKind.MILESTONE -> require(
                candidateRevisionId == null && probeIds.isEmpty() && observations.size == 1 &&
                    requiredSurfaces.size == 1 && resultIdentityId != null && dependsOnRowIds.isEmpty(),
            )
            T10SessionRowKind.MUTATION -> throw IllegalArgumentException("neutral Session N does not admit mutation rows")
        }
    }

    internal fun canonicalValue(): T10JsonValue = T10Canonical.obj(
        "candidateRevisionId" to candidateRevisionId.jsonText(),
        "clearOperationId" to T10Canonical.nullValue(),
        "dependencyReady" to T10Canonical.boolean(dependencyReady),
        "dependencyUncertainty" to T10Canonical.integer(dependencyUncertainty),
        "dependsOnRowIds" to T10Canonical.array(dependsOnRowIds.map { T10Canonical.text(it.wireName) }),
        "estimatedTimeMs" to T10Canonical.integer(estimatedTimeMs),
        "evidenceStrength" to T10Canonical.integer(evidenceStrength),
        "informationGain" to T10Canonical.integer(informationGain),
        "invalidationTriggers" to T10Canonical.array(emptyList()),
        "inverseOperationIds" to T10Canonical.array(emptyList()),
        "kind" to T10Canonical.text(kind.name),
        "mutationOperationId" to T10Canonical.nullValue(),
        "mutationRisk" to T10Canonical.integer(mutationRisk),
        "observations" to T10Canonical.array(observations.map { T10Canonical.text(it.wireName) }),
        "phaseRank" to T10Canonical.integer(phaseRank),
        "probeIds" to T10Canonical.array(probeIds.map { T10Canonical.text(it.wireName) }),
        "requiredSurfaces" to T10Canonical.array(requiredSurfaces.map { T10Canonical.text(it.name) }),
        "restoreScope" to T10Canonical.array(emptyList()),
        "resultIdentityId" to resultIdentityId.jsonText(),
        "reversibility" to T10Canonical.integer(reversibility),
        "riskClass" to T10Canonical.text(riskClass.name),
        "rollbackStackIndex" to T10Canonical.nullValue(),
        "rowId" to T10Canonical.text(rowId.wireName),
        "score" to T10Canonical.integer(score),
    )

    override fun equals(other: Any?) = other is SessionRowTemplate && canonicalValue() == other.canonicalValue()
    override fun hashCode() = canonicalValue().hashCode()

    companion object {
        internal fun create(
            rowId: T10RowId, kind: T10SessionRowKind, candidateRevisionId: T10CandidateRevisionId?,
            resultIdentityId: T10ResultIdentityId?, probeIds: List<T10ProbeId>, observations: List<T10ObservationId>,
            requiredSurfaces: List<T10RequiredSurface>, dependsOnRowIds: List<T10RowId>, dependencyReady: Boolean,
            dependencyUncertainty: Long, estimatedTimeMs: Long, evidenceStrength: Long, informationGain: Long,
            mutationRisk: Long, phaseRank: Long, reversibility: Long, riskClass: T10RiskClass, score: Long,
        ) = SessionRowTemplate(rowId, kind, candidateRevisionId, resultIdentityId, probeIds, observations,
            requiredSurfaces, dependsOnRowIds, dependencyReady, dependencyUncertainty, estimatedTimeMs,
            evidenceStrength, informationGain, mutationRisk, phaseRank, reversibility, riskClass, score)
    }
}

class SessionPackTemplate private constructor(
    val allowedCandidateRevisionIds: List<T10CandidateRevisionId>,
    val allowedMutationCandidateRevisionIds: List<T10CandidateRevisionId>,
    val allowedProbeIds: List<T10ProbeId>,
    val budgetMs: Long,
    val coverageFileSha256: Sha256,
    val evidenceMapFileSha256: Sha256,
    val identityRequirement: T10IdentityRequirement,
    val packSha256: Sha256,
    val registryFileSha256: Sha256,
    val revision: Long,
    rows: List<SessionRowTemplate>,
) {
    val rows: List<SessionRowTemplate> = immutableList(rows)

    init {
        require(revision > 0 && budgetMs in 0..86_400_000 && rows.isNotEmpty())
        require(allowedCandidateRevisionIds == allowedCandidateRevisionIds.distinct())
        require(allowedMutationCandidateRevisionIds == allowedMutationCandidateRevisionIds.distinct())
        require(allowedProbeIds == allowedProbeIds.distinct())
        require(rows.map { it.rowId }.distinct().size == rows.size)
    }

    fun withResolvedIdentity(identity: ExactIdentity): SessionPackTemplate {
        require(identity.packSha256 == packSha256 && identity.registryFileSha256 == registryFileSha256) {
            "resolved identity does not bind this logical pack and registry"
        }
        return create(allowedCandidateRevisionIds, allowedMutationCandidateRevisionIds, allowedProbeIds,
            budgetMs, coverageFileSha256, evidenceMapFileSha256, T10IdentityRequirement.Resolved(identity),
            packSha256, registryFileSha256, revision, rows)
    }

    internal fun canonicalValue(): T10JsonValue = T10Canonical.obj(
        "allowedCandidateRevisionIds" to T10Canonical.array(allowedCandidateRevisionIds.map { T10Canonical.text(it.wireName) }),
        "allowedMutationCandidateRevisionIds" to T10Canonical.array(allowedMutationCandidateRevisionIds.map { T10Canonical.text(it.wireName) }),
        "allowedProbeIds" to T10Canonical.array(allowedProbeIds.map { T10Canonical.text(it.wireName) }),
        "budgetMs" to T10Canonical.integer(budgetMs),
        "coverageFileSha256" to T10Canonical.text(coverageFileSha256.value),
        "evidenceMapFileSha256" to T10Canonical.text(evidenceMapFileSha256.value),
        "identityRequirement" to identityRequirement.canonicalValue(),
        "packSha256" to T10Canonical.text(packSha256.value),
        "registryFileSha256" to T10Canonical.text(registryFileSha256.value),
        "revision" to T10Canonical.integer(revision),
        "rows" to T10Canonical.array(rows.map { it.canonicalValue() }),
    )

    override fun equals(other: Any?) = other is SessionPackTemplate && canonicalValue() == other.canonicalValue()
    override fun hashCode() = canonicalValue().hashCode()

    companion object {
        internal fun create(
            candidates: List<T10CandidateRevisionId>, mutations: List<T10CandidateRevisionId>, probes: List<T10ProbeId>,
            budget: Long, coverage: Sha256, evidence: Sha256, identity: T10IdentityRequirement, pack: Sha256,
            registry: Sha256, revision: Long, rows: List<SessionRowTemplate>,
        ) = SessionPackTemplate(immutableList(candidates), immutableList(mutations), immutableList(probes), budget,
            coverage, evidence, identity, pack, registry, revision, immutableList(rows))
    }
}

class T10SessionPlan internal constructor(
    val selfSha256: Sha256,
    val fileSha256: Sha256,
    val template: SessionPackTemplate,
) {
    val schemaId = SCHEMA_ID
    fun toCanonicalBytes(): ByteArray = T10Canonical.render(canonicalValue())
    internal fun canonicalValue(): T10JsonValue = T10Canonical.obj(
        "schemaId" to T10Canonical.text(schemaId), "selfSha256" to T10Canonical.text(selfSha256.value),
        "template" to template.canonicalValue(),
    )
    companion object { const val SCHEMA_ID = "clusternav.expansion-vehicle-session-plan/v1" }
}

object T10SessionPlanLoader {
    val STAGE_A_PACK_SHA256 = Sha256.parse("d99d602705f0da99dceb0ac43abe9c1a880b0ee817a03ef6aa7a19a2e10d68f3")
    val STAGE_A_SELF_SHA256 = Sha256.parse("4301e4c8401b6dd20f7baadc1273c247afea8bd8736a4987acb7572549db7e8e")
    val STAGE_A_FILE_SHA256 = Sha256.parse("51674404c6d93791c44198007d59f0fab05364cb6a0315bcb57d3de8446213b4")

    fun load(bytes: ByteArray): T10SessionPlan {
        val parsed = T10Canonical.parse(bytes)
        val root = parsed.objectFields("vehicle session plan")
        root.requireExactKeys("vehicle session plan", "schemaId", "selfSha256", "template")
        require(root.getValue("schemaId").stringValue("schemaId") == T10SessionPlan.SCHEMA_ID)
        val self = Sha256.parse(root.getValue("selfSha256").stringValue("selfSha256"))
        val withoutSelf = T10Canonical.obj(
            "schemaId" to root.getValue("schemaId"), "template" to root.getValue("template"),
        )
        require(T10Canonical.sha256(withoutSelf) == self) { "session plan self SHA-256 mismatch" }
        val templateFields = root.getValue("template").objectFields("template")
        val zeroTemplate = T10Canonical.obj(*templateFields.map { (key, value) ->
            key to if (key == "packSha256") T10Canonical.text("0".repeat(64)) else value
        }.toTypedArray())
        val packProjection = T10Canonical.obj(
            "schemaId" to root.getValue("schemaId"), "template" to zeroTemplate,
        )
        val template = parseTemplate(root.getValue("template"))
        require(T10Canonical.sha256(packProjection) == template.packSha256) { "logical pack SHA-256 mismatch" }
        val plan = T10SessionPlan(self, T10Canonical.sha256(bytes), template)
        validateStageATruth(plan)
        return plan
    }

    private fun validateStageATruth(plan: T10SessionPlan) {
        val template = plan.template
        require(plan.selfSha256 == STAGE_A_SELF_SHA256 && plan.fileSha256 == STAGE_A_FILE_SHA256)
        require(template.packSha256 == STAGE_A_PACK_SHA256 && template.revision == 4L && template.budgetMs == 3_600_000L)
        require(template.allowedCandidateRevisionIds == listOf(T10CandidateRevisionId.H8_PROPERTY_CONFIG_METADATA_R3))
        require(template.allowedMutationCandidateRevisionIds.isEmpty() && template.allowedProbeIds == T10ProbeId.entries)
        val inert = template.identityRequirement as? T10IdentityRequirement.Inert
            ?: throw IllegalArgumentException("Stage A identity must remain inert")
        require(inert.blockerIds == listOf(T10BlockerId.MISSING_AUTHORIZED_T10_HANDOFF))
        require(template.rows.map { it.rowId } == T10RowId.entries)
        require(template.rows.count { it.kind == T10SessionRowKind.READ_ONLY } == 7)
        require(template.rows.count { it.kind == T10SessionRowKind.MILESTONE } == 4)
        template.rows.take(6).zip(T10ProbeId.entries).forEach { (row, probe) -> require(row.probeIds == listOf(probe)) }
        val h8 = template.rows[6]
        require(h8.candidateRevisionId == T10CandidateRevisionId.H8_PROPERTY_CONFIG_METADATA_R3 &&
            h8.probeIds == listOf(T10ProbeId.READ_PROPERTY_CONFIG) &&
            h8.requiredSurfaces == listOf(T10RequiredSurface.HUD_NAV_MAP))
        val expectedMilestones = listOf(
            Triple(T10ResultIdentityId.D_M1, T10ObservationId.M1_SURFACE_RESULT, T10RequiredSurface.HUD_NAV_MAP),
            Triple(T10ResultIdentityId.D_M2, T10ObservationId.M2_SURFACE_RESULT, T10RequiredSurface.HUD_ROAD_NAME),
            Triple(T10ResultIdentityId.D_M3, T10ObservationId.M3_SURFACE_RESULT, T10RequiredSurface.CLUSTER_SPEED_SIGN),
            Triple(T10ResultIdentityId.D_M4, T10ObservationId.M4_SURFACE_RESULT, T10RequiredSurface.HUD_SPEED_SIGN),
        )
        template.rows.drop(7).zip(expectedMilestones).forEach { (row, expected) ->
            require(row.resultIdentityId == expected.first && row.observations == listOf(expected.second) &&
                row.requiredSurfaces == listOf(expected.third))
        }
    }
}

class SessionRow internal constructor(val template: SessionRowTemplate, val exactIdentity: ExactIdentity) {
    val rowId get() = template.rowId
    val kind get() = template.kind
    val probeIds get() = template.probeIds
    val resultIdentityId get() = template.resultIdentityId
    internal fun canonicalValue(): T10JsonValue {
        val fields = template.canonicalValue().objectFields("row").map { it.key to it.value }.toMutableList()
        fields += "exactIdentity" to exactIdentity.canonicalValue()
        return T10Canonical.obj(*fields.toTypedArray())
    }
}

class SessionPackFreeze private constructor(
    val sessionId: SessionId, val sessionInstanceSha256: Sha256, val templateFileSha256: Sha256,
    val exactIdentity: ExactIdentity, val revision: Long, val allowedProbeIds: List<T10ProbeId>,
    val allowedCandidateRevisionIds: List<T10CandidateRevisionId>,
    val allowedMutationCandidateRevisionIds: List<T10CandidateRevisionId>, val sessionStartElapsedMs: Long,
    val budgetMs: Long, val deadlineElapsedMs: Long, val coverageFileSha256: Sha256,
    val evidenceMapFileSha256: Sha256, val registryFileSha256: Sha256, val packSha256: Sha256,
    rows: List<SessionRow>,
) {
    val rows: List<SessionRow> = immutableList(rows)
    fun toCanonicalBytes(): ByteArray = T10Canonical.render(canonicalValue(true))
    fun matchesTemplate(template: SessionPackTemplate): Boolean = revision == template.revision &&
        allowedProbeIds == template.allowedProbeIds && allowedCandidateRevisionIds == template.allowedCandidateRevisionIds &&
        allowedMutationCandidateRevisionIds == template.allowedMutationCandidateRevisionIds && budgetMs == template.budgetMs &&
        coverageFileSha256 == template.coverageFileSha256 && evidenceMapFileSha256 == template.evidenceMapFileSha256 &&
        registryFileSha256 == template.registryFileSha256 && packSha256 == template.packSha256 &&
        rows.map { it.template } == template.rows &&
        (template.identityRequirement as? T10IdentityRequirement.Resolved)?.exactIdentity == exactIdentity

    internal fun canonicalValue(includeInstance: Boolean): T10JsonValue = T10Canonical.obj(*buildList {
        add("allowedCandidateRevisionIds" to T10Canonical.array(allowedCandidateRevisionIds.map { T10Canonical.text(it.wireName) }))
        add("allowedMutationCandidateRevisionIds" to T10Canonical.array(allowedMutationCandidateRevisionIds.map { T10Canonical.text(it.wireName) }))
        add("allowedProbeIds" to T10Canonical.array(allowedProbeIds.map { T10Canonical.text(it.wireName) }))
        add("budgetMs" to T10Canonical.integer(budgetMs)); add("coverageFileSha256" to T10Canonical.text(coverageFileSha256.value))
        add("deadlineElapsedMs" to T10Canonical.integer(deadlineElapsedMs)); add("evidenceMapFileSha256" to T10Canonical.text(evidenceMapFileSha256.value))
        add("exactIdentity" to exactIdentity.canonicalValue()); add("packSha256" to T10Canonical.text(packSha256.value))
        add("registryFileSha256" to T10Canonical.text(registryFileSha256.value)); add("revision" to T10Canonical.integer(revision))
        add("rows" to T10Canonical.array(rows.map { it.canonicalValue() })); add("sessionId" to T10Canonical.text(sessionId.value))
        if (includeInstance) add("sessionInstanceSha256" to T10Canonical.text(sessionInstanceSha256.value))
        add("sessionStartElapsedMs" to T10Canonical.integer(sessionStartElapsedMs)); add("templateFileSha256" to T10Canonical.text(templateFileSha256.value))
    }.toTypedArray())

    companion object {
        internal fun create(
            id: SessionId, instance: Sha256, file: Sha256, identity: ExactIdentity, template: SessionPackTemplate,
            start: Long, deadline: Long,
        ) = SessionPackFreeze(id, instance, file, identity, template.revision, immutableList(template.allowedProbeIds),
            immutableList(template.allowedCandidateRevisionIds), immutableList(template.allowedMutationCandidateRevisionIds),
            start, template.budgetMs, deadline, template.coverageFileSha256, template.evidenceMapFileSha256,
            template.registryFileSha256, template.packSha256, template.rows.map { SessionRow(it, identity) })
    }
}

object SessionFreezeFactory {
    fun freeze(plan: T10SessionPlan, nonceSha256: Sha256, startElapsedMs: Long): SessionPackFreeze =
        freeze(plan.template, plan.fileSha256, nonceSha256, startElapsedMs)

    fun freeze(template: SessionPackTemplate, templateFileSha256: Sha256, nonceSha256: Sha256, startElapsedMs: Long): SessionPackFreeze {
        require(startElapsedMs >= 0) { "session start must be non-negative monotonic time" }
        val identity = (template.identityRequirement as? T10IdentityRequirement.Resolved)?.exactIdentity
            ?: throw IllegalStateException("inert identity cannot produce an executable freeze")
        require(identity.variant == T10Variant.VEHICLE_TEST && identity.profileId != T10ProfileId.PROFILE_UNASSIGNED)
        require(identity.packSha256 == template.packSha256 && identity.registryFileSha256 == template.registryFileSha256)
        val deadline = Math.addExact(startElapsedMs, template.budgetMs)
        val sessionHash = T10Canonical.sha256(T10Canonical.obj(
            "packSha256" to T10Canonical.text(template.packSha256.value),
            "sessionNonceSha256" to T10Canonical.text(nonceSha256.value),
            "sessionStartElapsedMs" to T10Canonical.integer(startElapsedMs),
        ))
        val sessionId = SessionId.parse("SESSION-${sessionHash.value.take(16).uppercase()}")
        val draft = SessionPackFreeze.create(sessionId, SESSION_ZERO_SHA256, templateFileSha256, identity, template, startElapsedMs, deadline)
        val instance = T10Canonical.sha256(draft.canonicalValue(false))
        return SessionPackFreeze.create(sessionId, instance, templateFileSha256, identity, template, startElapsedMs, deadline)
            .also { require(it.matchesTemplate(template)) }
    }
}

private fun parseTemplate(value: T10JsonValue): SessionPackTemplate {
    val fields = value.objectFields("template")
    fields.requireExactKeys("template", "allowedCandidateRevisionIds", "allowedMutationCandidateRevisionIds",
        "allowedProbeIds", "budgetMs", "coverageFileSha256", "evidenceMapFileSha256", "identityRequirement",
        "packSha256", "registryFileSha256", "revision", "rows")
    fun strings(name: String) = fields.getValue(name).arrayValues(name).map { it.stringValue(name) }
    val candidates = strings("allowedCandidateRevisionIds").map(T10CandidateRevisionId::parse)
    val mutations = strings("allowedMutationCandidateRevisionIds").map(T10CandidateRevisionId::parse)
    val probes = strings("allowedProbeIds").map(T10ProbeId::parse)
    require(candidates == candidates.distinct() && mutations == mutations.distinct() && probes == probes.distinct())
    return SessionPackTemplate.create(candidates, mutations, probes, fields.getValue("budgetMs").longValue("budgetMs"),
        Sha256.parse(fields.getValue("coverageFileSha256").stringValue("coverageFileSha256")),
        Sha256.parse(fields.getValue("evidenceMapFileSha256").stringValue("evidenceMapFileSha256")),
        parseIdentityRequirement(fields.getValue("identityRequirement")),
        Sha256.parse(fields.getValue("packSha256").stringValue("packSha256")),
        Sha256.parse(fields.getValue("registryFileSha256").stringValue("registryFileSha256")),
        fields.getValue("revision").longValue("revision"), fields.getValue("rows").arrayValues("rows").map(::parseRow))
}

private fun parseRow(value: T10JsonValue): SessionRowTemplate {
    val f = value.objectFields("row")
    f.requireExactKeys("row", "candidateRevisionId", "clearOperationId", "dependencyReady", "dependencyUncertainty",
        "dependsOnRowIds", "estimatedTimeMs", "evidenceStrength", "informationGain", "invalidationTriggers",
        "inverseOperationIds", "kind", "mutationOperationId", "mutationRisk", "observations", "phaseRank", "probeIds",
        "requiredSurfaces", "restoreScope", "resultIdentityId", "reversibility", "riskClass", "rollbackStackIndex", "rowId", "score")
    fun strings(name: String) = f.getValue(name).arrayValues(name).map { it.stringValue(name) }
    fun nullable(name: String): String? = f.getValue(name).let { if (it.isNull()) null else it.stringValue(name) }
    require(f.getValue("clearOperationId").isNull() && f.getValue("mutationOperationId").isNull() &&
        f.getValue("rollbackStackIndex").isNull() && strings("invalidationTriggers").isEmpty() &&
        strings("inverseOperationIds").isEmpty() && strings("restoreScope").isEmpty()) { "Stage A row contains mutation material" }
    return SessionRowTemplate.create(T10RowId.parse(f.getValue("rowId").stringValue("rowId")),
        enumValue(f.getValue("kind").stringValue("kind"), "row kind"), nullable("candidateRevisionId")?.let(T10CandidateRevisionId::parse),
        nullable("resultIdentityId")?.let(T10ResultIdentityId::parse), strings("probeIds").map(T10ProbeId::parse),
        strings("observations").map(T10ObservationId::parse), strings("requiredSurfaces").map { enumValue(it, "surface") },
        strings("dependsOnRowIds").map(T10RowId::parse), f.getValue("dependencyReady").booleanValue("dependencyReady"),
        f.getValue("dependencyUncertainty").longValue("dependencyUncertainty"), f.getValue("estimatedTimeMs").longValue("estimatedTimeMs"),
        f.getValue("evidenceStrength").longValue("evidenceStrength"), f.getValue("informationGain").longValue("informationGain"),
        f.getValue("mutationRisk").longValue("mutationRisk"), f.getValue("phaseRank").longValue("phaseRank"),
        f.getValue("reversibility").longValue("reversibility"), enumValue(f.getValue("riskClass").stringValue("riskClass"), "risk class"),
        f.getValue("score").longValue("score"))
}

private fun checkedScore(row: SessionRowTemplate): Long = Math.subtractExact(
    Math.addExact(Math.addExact(Math.multiplyExact(row.evidenceStrength, 100), Math.multiplyExact(row.informationGain, 40)),
        Math.multiplyExact(row.reversibility, 30)),
    Math.addExact(Math.addExact(Math.multiplyExact(row.mutationRisk, 50), Math.multiplyExact(row.estimatedTimeMs / 1000, 10)),
        Math.multiplyExact(row.dependencyUncertainty, 25)),
)
private fun riskClassFor(risk: Long) = when (risk) { in 0..33 -> T10RiskClass.LOW; in 34..66 -> T10RiskClass.MEDIUM; else -> T10RiskClass.HIGH }
private fun T10CandidateRevisionId?.jsonText() = this?.let { T10Canonical.text(it.wireName) } ?: T10Canonical.nullValue()
private fun T10ResultIdentityId?.jsonText() = this?.let { T10Canonical.text(it.wireName) } ?: T10Canonical.nullValue()
private val SESSION_ZERO_SHA256 = Sha256.parse("0".repeat(64))
