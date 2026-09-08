package com.byd.clusternav.offcar

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

internal fun <T> immutableListSnapshot(values: Collection<T>): List<T> =
    java.util.Collections.unmodifiableList(java.util.ArrayList(values))

internal fun <K, V> immutableMapSnapshot(values: Map<K, V>): Map<K, V> =
    java.util.Collections.unmodifiableMap(java.util.LinkedHashMap(values))
object ExpansionPathFence {
    private fun validateTrustedRoot(trustedRoot: Path) {
        require(trustedRoot.isAbsolute && trustedRoot == trustedRoot.normalize()) {
            "trusted root must be normalized and absolute"
        }
        generateSequence(trustedRoot) { it.parent }.toList().asReversed().forEach { component ->
            val attributes = Files.readAttributes(component, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            require(!attributes.isSymbolicLink) { "symbolic link component is forbidden: $component" }
            require(attributes.isDirectory) { "trusted root ancestor must be a directory: $component" }
        }
    }

    fun validate(trustedRoot: Path, candidate: Path): Path {
        validateTrustedRoot(trustedRoot)
        require(candidate.isAbsolute && candidate == candidate.normalize() && candidate != trustedRoot &&
            candidate.startsWith(trustedRoot)) { "candidate must be a normalized descendant of the trusted root" }
        var component = candidate
        while (true) {
            try {
                require(!Files.readAttributes(
                    component, BasicFileAttributes::class.java, NOFOLLOW_LINKS,
                ).isSymbolicLink) { "symbolic link component is forbidden: $component" }
            } catch (_: NoSuchFileException) {
                // Missing components remain valid for future fenced output paths.
            }
            if (component == trustedRoot) return candidate
            component = requireNotNull(component.parent)
        }
    }

    fun requireRegularInput(trustedRoot: Path, candidate: Path): Path = validate(trustedRoot, candidate).also {
        require(Files.isRegularFile(it, NOFOLLOW_LINKS)) { "regular input file is required: $it" }
    }
}

enum class DiscoveryOperation { READ, LIST }
enum class DiscoveryMetadataTarget { PROPERTY_CONFIG, SERVICE_METADATA, PACKAGE_METADATA }

enum class DiscoveryProbe(
    val id: String,
    val operation: DiscoveryOperation,
    val target: DiscoveryMetadataTarget,
) {
    LIST_PACKAGE_METADATA("PROBE-LIST-PACKAGE-METADATA", DiscoveryOperation.LIST, DiscoveryMetadataTarget.PACKAGE_METADATA),
    LIST_PROPERTY_CONFIGS("PROBE-LIST-PROPERTY-CONFIGS", DiscoveryOperation.LIST, DiscoveryMetadataTarget.PROPERTY_CONFIG),
    LIST_SERVICE_METADATA("PROBE-LIST-SERVICE-METADATA", DiscoveryOperation.LIST, DiscoveryMetadataTarget.SERVICE_METADATA),
    READ_PACKAGE_METADATA("PROBE-READ-PACKAGE-METADATA", DiscoveryOperation.READ, DiscoveryMetadataTarget.PACKAGE_METADATA),
    READ_PROPERTY_CONFIG("PROBE-READ-PROPERTY-CONFIG", DiscoveryOperation.READ, DiscoveryMetadataTarget.PROPERTY_CONFIG),
    READ_SERVICE_METADATA("PROBE-READ-SERVICE-METADATA", DiscoveryOperation.READ, DiscoveryMetadataTarget.SERVICE_METADATA),
    ;

    init {
        ExpansionIds.probe(id)
        require(id.startsWith("PROBE-${operation.name}-")) { "probe operation and ID disagree" }
    }

    fun request(): DiscoveryRequest = when (operation) {
        DiscoveryOperation.READ -> DiscoveryRequest.Read(this)
        DiscoveryOperation.LIST -> DiscoveryRequest.ListMetadata(this)
    }
}

sealed interface DiscoveryRequest {
    val probe: DiscoveryProbe

    class Read internal constructor(override val probe: DiscoveryProbe) : DiscoveryRequest {
        init { require(probe.operation == DiscoveryOperation.READ) }
    }

    class ListMetadata internal constructor(override val probe: DiscoveryProbe) : DiscoveryRequest {
        init { require(probe.operation == DiscoveryOperation.LIST) }
    }
}

object DiscoveryProbeCatalog {
    val all: List<DiscoveryProbe> = immutableListSnapshot(DiscoveryProbe.entries.sortedBy(DiscoveryProbe::id))
    val byId: Map<String, DiscoveryProbe> = immutableMapSnapshot(all.associateBy(DiscoveryProbe::id))

    init {
        require(byId.size == all.size) { "duplicate discovery probe ID" }
        require(all.map(DiscoveryProbe::id) == all.map(DiscoveryProbe::id).sorted())
        require(all.groupBy(DiscoveryProbe::target).values.all { probes ->
            probes.map(DiscoveryProbe::operation).toSet() == DiscoveryOperation.entries.toSet()
        }) { "each metadata target requires one fixed READ and LIST probe" }
    }

    fun requireById(id: String): DiscoveryProbe =
        requireNotNull(byId[id]) { "unknown fixed discovery probe ID: $id" }

    fun request(id: String): DiscoveryRequest = requireById(id).request()
}

enum class PromotionRequirement {
    SOURCE_EVIDENCE, SELECTOR, CONFIG, ACCESS, WRITE_ACCESS, JAVA_TYPE, PROVIDER, PERMISSION,
    TRANSPORT, BOUNDED_DOMAIN, CONSUMER, OWNERSHIP, RISK, READ_PROBE, NO_MUTATION_OPERATION,
    CLEAR_NOT_APPLICABLE, NO_CLEAR_OPERATION, NO_INVERSE_OPERATIONS, MUTATION_OPERATION,
    PRIOR_READ, READ_BACK, CLEAR_REQUIRED, CLEAR_OPERATION, INVERSE_OPERATION,
}

class PromotionDecision internal constructor(
    val state: CandidateState,
    val unmetRequirements: List<PromotionRequirement>,
    val absoluteRejects: List<AbsoluteReject>,
) {
    val frozenEligible: Boolean =
        state == CandidateState.READ_ONLY_READY || state == CandidateState.READY_FOR_FIELD
}

object CandidatePromotion {
    fun review(mode: CandidateMode, proof: PromotionProof): PromotionDecision {
        val unmet = buildList {
            fun missing(value: Any?, requirement: PromotionRequirement) { if (value == null) add(requirement) }
            if (proof.evidenceIds.isEmpty()) add(PromotionRequirement.SOURCE_EVIDENCE)
            missing(proof.selectorId, PromotionRequirement.SELECTOR)
            missing(proof.configId, PromotionRequirement.CONFIG)
            missing(proof.access, PromotionRequirement.ACCESS)
            missing(proof.javaType, PromotionRequirement.JAVA_TYPE)
            missing(proof.providerId, PromotionRequirement.PROVIDER)
            missing(proof.permissionId, PromotionRequirement.PERMISSION)
            missing(proof.transportId, PromotionRequirement.TRANSPORT)
            if (proof.boundedDomainValueIds.isEmpty()) add(PromotionRequirement.BOUNDED_DOMAIN)
            missing(proof.consumerId, PromotionRequirement.CONSUMER)
            missing(proof.ownership, PromotionRequirement.OWNERSHIP)
            missing(proof.risk, PromotionRequirement.RISK)
            when (mode) {
                CandidateMode.READ_ONLY -> {
                    missing(proof.readProbeId, PromotionRequirement.READ_PROBE)
                    if (proof.mutationOperationId != null) add(PromotionRequirement.NO_MUTATION_OPERATION)
                    if (proof.clearPolicy != ClearPolicy.NOT_APPLICABLE) add(PromotionRequirement.CLEAR_NOT_APPLICABLE)
                    if (proof.clearOperationId != null) add(PromotionRequirement.NO_CLEAR_OPERATION)
                    if (proof.inverseOperationIds.isNotEmpty()) add(PromotionRequirement.NO_INVERSE_OPERATIONS)
                }
                CandidateMode.MUTATION -> {
                    if (proof.access !in setOf(ConfigAccess.READ_WRITE, ConfigAccess.WRITE)) {
                        add(PromotionRequirement.WRITE_ACCESS)
                    }
                    missing(proof.mutationOperationId, PromotionRequirement.MUTATION_OPERATION)
                    missing(proof.priorReadOperationId, PromotionRequirement.PRIOR_READ)
                    missing(proof.readBackOperationId, PromotionRequirement.READ_BACK)
                    if (proof.clearPolicy != ClearPolicy.REQUIRED) add(PromotionRequirement.CLEAR_REQUIRED)
                    missing(proof.clearOperationId, PromotionRequirement.CLEAR_OPERATION)
                    if (proof.inverseOperationIds.isEmpty()) add(PromotionRequirement.INVERSE_OPERATION)
                }
            }
        }.distinct().sortedBy { it.ordinal }
        return PromotionDecision(proof.deriveState(mode), unmet, proof.absoluteRejects.toList())
    }

    fun review(candidate: CandidateRevision): PromotionDecision {
        val decision = review(candidate.input.mode, candidate.input.proof)
        require(decision.state == candidate.state) { "stored candidate state is not derived" }
        return decision
    }
}

object CandidateRevisionChains {
    fun validate(candidates: List<CandidateRevision>) {
        val byId = candidates.associateBy(CandidateRevision::candidateRevisionId)
        require(byId.size == candidates.size) { "candidate revision IDs must be unique" }
        val ordered = candidates.sortedWith(compareBy(ExpansionIds.candidateComparator) { it.candidateRevisionId })
        require(candidates == ordered) { "candidate revisions must be canonically sorted" }
        candidates.forEach { candidate ->
            CandidatePromotion.review(candidate)
            candidate.input.dependsOnRevisionIds.forEach { dependencyId ->
                val dependency = requireNotNull(byId[dependencyId]) { "unresolved candidate dependency: $dependencyId" }
                require(dependency.input.mutationDimension == candidate.input.mutationDimension) {
                    "candidate dependencies must stay in one mutation dimension"
                }
            }
        }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visit(candidateId: String) {
            if (candidateId in visited) return
            require(visiting.add(candidateId)) { "candidate dependency graph contains a cycle" }
            byId.getValue(candidateId).input.dependsOnRevisionIds.forEach(::visit)
            visiting.remove(candidateId)
            visited.add(candidateId)
        }
        candidates.forEach { visit(it.candidateRevisionId) }
        candidates.groupBy { ExpansionIds.candidate(it.candidateRevisionId).baseId }.values.forEach { revisions ->
            revisions.forEachIndexed { index, candidate ->
                require(candidate.revision == index + 1) { "candidate revision chain must be gapless" }
                require(candidate.input.predecessorCandidateSha256 == revisions.getOrNull(index - 1)?.revisionSha256) {
                    "candidate predecessor mismatch"
                }
            }
        }
    }
}

class FrozenEligibility private constructor(
    allowedProbeIds: List<String>,
    readOnlyCandidateRevisionIds: List<String>,
    mutationCandidateRevisionIds: List<String>,
) {
    val allowedProbeIds = immutableListSnapshot(allowedProbeIds)
    val readOnlyCandidateRevisionIds = immutableListSnapshot(readOnlyCandidateRevisionIds)
    val mutationCandidateRevisionIds = immutableListSnapshot(mutationCandidateRevisionIds)
    val allowedCandidateRevisionIds = immutableListSnapshot(
        (this.readOnlyCandidateRevisionIds + this.mutationCandidateRevisionIds)
            .sortedWith(ExpansionIds.candidateComparator),
    )

    fun isCandidateEligible(candidateRevisionId: String): Boolean {
        ExpansionIds.candidate(candidateRevisionId)
        return candidateRevisionId in allowedCandidateRevisionIds
    }

    companion object {
        fun freeze(
            candidates: List<CandidateRevision>,
            probes: List<DiscoveryProbe> = DiscoveryProbeCatalog.all,
        ): FrozenEligibility {
            CandidateRevisionChains.validate(candidates)
            require(probes.distinct().size == probes.size) { "frozen probes must be unique" }
            val latest = candidates.groupBy { ExpansionIds.candidate(it.candidateRevisionId).baseId }
                .values.map { it.maxBy(CandidateRevision::revision) }
            val readOnly = latest.filter {
                it.input.mode == CandidateMode.READ_ONLY && it.state == CandidateState.READ_ONLY_READY
            }.sortedWith(compareBy(ExpansionIds.candidateComparator) { it.candidateRevisionId })
            val mutations = latest.filter {
                it.input.mode == CandidateMode.MUTATION && it.state == CandidateState.READY_FOR_FIELD
            }.sortedWith(compareBy(ExpansionIds.candidateComparator) { it.candidateRevisionId })
            val probeIds = probes.map(DiscoveryProbe::id).sorted()
            readOnly.forEach {
                val readProbeId = requireNotNull(it.input.proof.readProbeId)
                DiscoveryProbeCatalog.requireById(readProbeId)
                require(readProbeId in probeIds) { "eligible read-only candidate probe is not frozen" }
            }
            return FrozenEligibility(
                probeIds,
                readOnly.map(CandidateRevision::candidateRevisionId),
                mutations.map(CandidateRevision::candidateRevisionId),
            )
        }
    }
}

class DiscoveryOnlyRecord private constructor(
    val probe: DiscoveryProbe,
    evidenceIds: List<String>,
) {
    val evidenceIds = immutableListSnapshot(evidenceIds)
    val eventType: LedgerEventType = LedgerEventType.DISCOVERY_ONLY

    companion object {
        fun create(probe: DiscoveryProbe, evidenceIds: List<String>): DiscoveryOnlyRecord {
            require(evidenceIds.isNotEmpty()) { "discovery evidence is required" }
            evidenceIds.forEach(ExpansionIds::evidence)
            require(evidenceIds == evidenceIds.distinct().sorted()) { "discovery evidence must be sorted and unique" }
            return DiscoveryOnlyRecord(probe, evidenceIds)
        }
    }
}

class SameSessionQuarantine private constructor(
    val frozenEligibility: FrozenEligibility,
    records: List<DiscoveryOnlyRecord>,
) {
    val records = immutableListSnapshot(records)

    fun record(probe: DiscoveryProbe, evidenceIds: List<String>): SameSessionQuarantine {
        require(probe.id in frozenEligibility.allowedProbeIds) { "probe is not in the frozen session" }
        return SameSessionQuarantine(frozenEligibility, records + DiscoveryOnlyRecord.create(probe, evidenceIds))
    }

    companion object {
        fun start(frozenEligibility: FrozenEligibility) = SameSessionQuarantine(frozenEligibility, emptyList())
    }
}

class ProposedTombstone private constructor(
    val targetCandidateRevisionId: String,
    val candidate: CandidateRevision,
    val invalidationReasonId: String,
    val frozenEligibility: FrozenEligibility,
) {
    companion object {
        fun create(
            target: CandidateRevision,
            proposed: CandidateRevision,
            invalidationReasonId: String,
            currentCandidates: List<CandidateRevision>,
            frozenEligibility: FrozenEligibility,
        ): ProposedTombstone {
            CandidateRevisionChains.validate(currentCandidates)
            ExpansionIds.reason(invalidationReasonId)
            require(currentCandidates.any {
                it.candidateRevisionId == target.candidateRevisionId && it.revisionSha256 == target.revisionSha256
            }) { "tombstone target is not in the current registry" }
            require(frozenEligibility.isCandidateEligible(target.candidateRevisionId)) {
                "tombstone target is not in current frozen eligibility"
            }
            require(invalidationReasonId in target.input.invalidatesOn) { "invalidation reason is not authorized" }
            val targetKey = ExpansionIds.candidate(target.candidateRevisionId)
            val proposedKey = ExpansionIds.candidate(proposed.candidateRevisionId)
            require(targetKey.baseId == proposedKey.baseId && targetKey.revision < Int.MAX_VALUE &&
                proposedKey.revision == targetKey.revision + 1) { "tombstone must be the exact next same-base revision" }
            require(proposed.input.predecessorCandidateSha256 == target.revisionSha256) {
                "tombstone predecessor must be the target revision hash"
            }
            require(proposed.state == CandidateState.REJECTED) { "tombstone must derive REJECTED" }
            require(currentCandidates.none { it.candidateRevisionId == proposed.candidateRevisionId }) {
                "proposed tombstone is already in the current registry"
            }
            require(!frozenEligibility.isCandidateEligible(proposed.candidateRevisionId)) {
                "proposed tombstone cannot enter current frozen eligibility"
            }
            return ProposedTombstone(target.candidateRevisionId, proposed, invalidationReasonId, frozenEligibility)
        }
    }
}

enum class ReviewedDerivationId {
    H8_DISCOVERED, H8_SOURCE_BACKED, H8_READ_ONLY_READY, S11_MUTATION_REVIEW,
    S12_REJECTED, H8_DUPLICATE, S11_OUT_OF_SCOPE,
}

enum class DerivationRule(val id: String) {
    SOURCE_METADATA_DUPLICATE("RULE-SOURCE-METADATA-DUPLICATE"),
    NON_SELECTOR_METADATA("RULE-NON-SELECTOR-METADATA"),
}

sealed interface ReviewedDerivationDisposition {
    val reviewId: ReviewedDerivationId

    data class CandidateDerivation(
        override val reviewId: ReviewedDerivationId,
        val candidate: CandidateRevision,
    ) : ReviewedDerivationDisposition

    data class DuplicateOf(
        override val reviewId: ReviewedDerivationId,
        val canonicalReviewId: ReviewedDerivationId,
    ) : ReviewedDerivationDisposition

    data class OutOfScope(
        override val reviewId: ReviewedDerivationId,
        val rule: DerivationRule,
    ) : ReviewedDerivationDisposition
}

class DerivationClosure private constructor(
    val reviewedIds: List<ReviewedDerivationId>,
    val dispositions: List<ReviewedDerivationDisposition>,
) {
    val byReviewId: Map<ReviewedDerivationId, ReviewedDerivationDisposition> =
        immutableMapSnapshot(dispositions.associateBy(ReviewedDerivationDisposition::reviewId))

    companion object {
        fun close(
            reviewedIds: List<ReviewedDerivationId>,
            dispositions: List<ReviewedDerivationDisposition>,
        ): DerivationClosure {
            require(reviewedIds == reviewedIds.distinct().sortedBy { it.ordinal }) { "review IDs must be ordered and unique" }
            require(dispositions.map { it.reviewId } == dispositions.map { it.reviewId }.distinct().sortedBy { it.ordinal }) {
                "each reviewed derivation must have exactly one ordered disposition"
            }
            require(dispositions.map { it.reviewId }.toSet() == reviewedIds.toSet()) { "derivation closure is incomplete" }
            val byId = dispositions.associateBy { it.reviewId }
            val candidates = dispositions.filterIsInstance<ReviewedDerivationDisposition.CandidateDerivation>()
            require(candidates.map { it.candidate.candidateRevisionId }.distinct().size == candidates.size) {
                "candidate derivations must be unique"
            }
            candidates.forEach { CandidatePromotion.review(it.candidate) }
            dispositions.filterIsInstance<ReviewedDerivationDisposition.DuplicateOf>().forEach { duplicate ->
                require(duplicate.reviewId != duplicate.canonicalReviewId) { "duplicate cannot reference itself" }
                require(byId[duplicate.canonicalReviewId] !is ReviewedDerivationDisposition.DuplicateOf &&
                    byId.containsKey(duplicate.canonicalReviewId)) { "duplicate must reference a canonical disposition" }
            }
            return DerivationClosure(immutableListSnapshot(reviewedIds), immutableListSnapshot(dispositions))
        }
    }
}

object SourceBackedExpansionCatalog {
    private const val INVALIDATION_REASON = "REASON-INVALIDATED-SOURCE-CONTRACT"
    private const val H8_EVIDENCE = "FACT-PARENT-H7"
    private const val S11_EVIDENCE = "FACT-PARENT-S6"
    private const val S12_EVIDENCE = "FACT-PARENT-S2"
    private val profile = PlanningProfile(100, 90, 80, 100, 10, 10, 1_000)

    private fun emptyProof(evidenceIds: List<String> = emptyList()) = PromotionProof(
        absoluteRejects = emptyList(), selectorId = null, readProbeId = null, mutationOperationId = null,
        configId = null, access = null, javaType = null, providerId = null, permissionId = null,
        transportId = null, boundedDomainValueIds = emptyList(), priorReadOperationId = null,
        readBackOperationId = null, clearPolicy = ClearPolicy.NOT_APPLICABLE, clearOperationId = null,
        inverseOperationIds = emptyList(), consumerId = null, ownership = null, risk = null,
        evidenceIds = evidenceIds,
    )

    val h8ReadOnlyProof: PromotionProof = emptyProof(listOf(H8_EVIDENCE)).copy(
        selectorId = "SEL-H8-PROPERTY-CONFIG-METADATA", readProbeId = DiscoveryProbe.READ_PROPERTY_CONFIG.id,
        configId = "CONFIG-H8-PROPERTY-METADATA", access = ConfigAccess.READ_ONLY, javaType = JavaType.STRING,
        providerId = "PROVIDER-SOURCE-METADATA", permissionId = "PERMISSION-NONE",
        transportId = "TRANSPORT-READ-ONLY-METADATA", boundedDomainValueIds = listOf("VALUE-METADATA-AVAILABLE"),
        consumerId = "CONSUMER-EXPANSION-REVIEW", ownership = Ownership.DIAGNOSTIC_TEMP, risk = 0,
    )
    val s11MutationReviewProof: PromotionProof = emptyProof(listOf(S11_EVIDENCE)).copy(
        selectorId = "SEL-S11-SOURCE-DOMAIN", mutationOperationId = "OP-MUTATE-S11-SOURCE-DOMAIN",
        configId = "CONFIG-S11-SOURCE-DOMAIN", access = ConfigAccess.READ_WRITE, javaType = JavaType.STRING,
        providerId = "PROVIDER-SOURCE-METADATA", permissionId = "PERMISSION-VENDOR-CAR",
        transportId = "TRANSPORT-SOURCE-PROVEN", boundedDomainValueIds = emptyList(),
        consumerId = "CONSUMER-CLUSTER-NATIVE", ownership = Ownership.DIAGNOSTIC_TEMP, risk = 25,
    )
    private fun candidate(
        id: String, milestone: Milestone, mode: CandidateMode, proof: PromotionProof, observation: String,
        hypothesis: String, dimension: String, predecessor: String?, restore: List<RestoreScope> = emptyList(),
    ) = CandidateRevision.create(CandidateRevisionInput(
        candidateRevisionId = id, milestone = milestone, mode = mode, proof = proof,
        hypothesisId = hypothesis, mutationDimension = dimension,
        requiredSurfaces = when (milestone) {
            Milestone.M1 -> listOf(RequiredSurface.HUD_NAV_MAP)
            Milestone.M2 -> listOf(RequiredSurface.HUD_ROAD_NAME)
            Milestone.M3 -> listOf(RequiredSurface.CLUSTER_SPEED_SIGN)
            Milestone.M4 -> listOf(RequiredSurface.HUD_SPEED_SIGN)
        },
        requiredObservationIds = listOf(observation), restoreScope = restore,
        invalidationTriggers = if (mode == CandidateMode.MUTATION) listOf(INVALIDATION_REASON) else emptyList(), planningProfile = profile,
        dependsOnRevisionIds = emptyList(), pruneGroup = null, subsumes = emptyList(),
        invalidatesOn = listOf(INVALIDATION_REASON), predecessorCandidateSha256 = predecessor,
    ))

    val h8Discovered = candidate(
        "CAND-H-008-PROPERTY-CONFIG-METADATA@1", Milestone.M1, CandidateMode.READ_ONLY, emptyProof(),
        "OBS-M1-H8-METADATA", "HYP-H8-METADATA", "DIMENSION-H8-METADATA", null,
    )
    val h8SourceBacked = candidate(
        "CAND-H-008-PROPERTY-CONFIG-METADATA@2", Milestone.M1, CandidateMode.READ_ONLY, emptyProof(listOf(H8_EVIDENCE)),
        "OBS-M1-H8-METADATA", "HYP-H8-METADATA", "DIMENSION-H8-METADATA", h8Discovered.revisionSha256,
    )
    val h8ReadOnlyReady = candidate(
        "CAND-H-008-PROPERTY-CONFIG-METADATA@3", Milestone.M1, CandidateMode.READ_ONLY, h8ReadOnlyProof,
        "OBS-M1-H8-METADATA", "HYP-H8-METADATA", "DIMENSION-H8-METADATA", h8SourceBacked.revisionSha256,
    )
    val s11MutationReview = candidate(
        "CAND-S-011-SOURCE-DOMAIN@1", Milestone.M3, CandidateMode.MUTATION, s11MutationReviewProof,
        "OBS-M3-S11-SOURCE-DOMAIN", "HYP-S11-SOURCE-DOMAIN", "DIMENSION-S11-SOURCE-DOMAIN", null,
        listOf(RestoreScope.CURRENT_PROPERTY),
    )
    val s12Rejected = candidate(
        "CAND-S-012-REJECTED-SHAPE@1", Milestone.M4, CandidateMode.MUTATION,
        s11MutationReviewProof.copy(absoluteRejects = listOf(AbsoluteReject.RAW_SELECTOR), evidenceIds = listOf(S12_EVIDENCE)),
        "OBS-M4-S12-REJECTED", "HYP-S12-REJECTED", "DIMENSION-S12-REJECTED", null,
        listOf(RestoreScope.CURRENT_PROPERTY),
    )

    val publishedCandidates = immutableListSnapshot(listOf(
        h8Discovered, h8SourceBacked, h8ReadOnlyReady, s11MutationReview, s12Rejected,
    )).also(CandidateRevisionChains::validate)
    val publishedRegistryHistory: List<RegistryRevision> = run {
        val firstCandidates = listOf(h8Discovered, s11MutationReview, s12Rejected)
        val first = RegistryRevision.create(1, null, firstCandidates)
        val secondCandidates = listOf(h8Discovered, h8SourceBacked, s11MutationReview, s12Rejected)
        val second = RegistryRevision.create(2, first.registryRevisionSha256, secondCandidates)
        val third = RegistryRevision.create(3, second.registryRevisionSha256, publishedCandidates)
        val fourth = RegistryRevision.create(4, third.registryRevisionSha256, publishedCandidates)
        immutableListSnapshot(listOf(first, second, third, fourth)).also(RegistryHistory::validate)
    }

    val reviewedDispositions: List<ReviewedDerivationDisposition> = immutableListSnapshot(listOf(
        ReviewedDerivationDisposition.CandidateDerivation(ReviewedDerivationId.H8_DISCOVERED, h8Discovered),
        ReviewedDerivationDisposition.CandidateDerivation(ReviewedDerivationId.H8_SOURCE_BACKED, h8SourceBacked),
        ReviewedDerivationDisposition.CandidateDerivation(ReviewedDerivationId.H8_READ_ONLY_READY, h8ReadOnlyReady),
        ReviewedDerivationDisposition.CandidateDerivation(ReviewedDerivationId.S11_MUTATION_REVIEW, s11MutationReview),
        ReviewedDerivationDisposition.CandidateDerivation(ReviewedDerivationId.S12_REJECTED, s12Rejected),
        ReviewedDerivationDisposition.DuplicateOf(ReviewedDerivationId.H8_DUPLICATE, ReviewedDerivationId.H8_SOURCE_BACKED),
        ReviewedDerivationDisposition.OutOfScope(ReviewedDerivationId.S11_OUT_OF_SCOPE, DerivationRule.NON_SELECTOR_METADATA),
    ))
    val closure: DerivationClosure = DerivationClosure.close(ReviewedDerivationId.entries.toList(), reviewedDispositions)
}
