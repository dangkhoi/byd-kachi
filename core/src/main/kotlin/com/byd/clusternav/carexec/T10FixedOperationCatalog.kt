package com.byd.clusternav.vehicle.t10

import java.util.Collections

/** Candidate IDs understood by the T10 core policy. External text must be parsed before lookup. */
enum class T10CatalogCandidateId(val wireName: String) {
    H8_PROPERTY_CONFIG_METADATA_R3("CAND-H-008-PROPERTY-CONFIG-METADATA@3"),
    S11_SOURCE_DOMAIN_R1("CAND-S-011-SOURCE-DOMAIN@1"),
    S12_REJECTED_SHAPE_R1("CAND-S-012-REJECTED-SHAPE@1");

    companion object {
        fun parse(value: String): T10CatalogCandidateId = entries.singleOrNull { it.wireName == value }
            ?: throw IllegalArgumentException("unknown T10 catalog candidate ID")
    }
}

enum class T10CatalogCandidateState {
    READ_ONLY_READY,
    MUTATION_REVIEW,
    REJECTED,
}

/** Candidate closure is separate from transport binding failures. */
enum class CandidateBindingReason {
    CANDIDATE_NOT_READY,
    REJECTED_REVISION,
}

sealed interface CandidateBindingDecision {
    class ReadOnlyProbe internal constructor(
        val probeId: T10ProbeId,
        val binding: FixedBinding.Blocked,
    ) : CandidateBindingDecision {
        override fun equals(other: Any?): Boolean = other is ReadOnlyProbe &&
            probeId == other.probeId && binding == other.binding

        override fun hashCode(): Int = 31 * probeId.hashCode() + binding.hashCode()
    }

    data class Closed(val reason: CandidateBindingReason) : CandidateBindingDecision
}

class T10CandidateBindingPolicy internal constructor(
    val candidateId: T10CatalogCandidateId,
    val state: T10CatalogCandidateState,
    val decision: CandidateBindingDecision,
)

fun interface T10ReadBindingResolver {
    fun resolve(probeId: T10ProbeId): FixedBinding
}

/** No production operation is admitted until app-UID reachability is independently proven. */
object T10FixedOperationCatalog : T10ReadBindingResolver {
    private val blocked = FixedBinding.Blocked(BindingBlockReason.UNPROVEN_APP_REACHABILITY)
    private val readBindings: Map<T10ProbeId, FixedBinding.Blocked> = Collections.unmodifiableMap(
        T10ProbeId.entries.associateWith { blocked },
    )

    val productionReadBindings: Map<T10ProbeId, FixedBinding.Blocked> get() = readBindings
    val productionMutationOperations: Set<T10MutationOperationId> = emptySet()

    override fun resolve(probeId: T10ProbeId): FixedBinding.Blocked = readBindings.getValue(probeId)

    fun resolveCandidate(candidateId: T10CatalogCandidateId): T10CandidateBindingPolicy = when (candidateId) {
        T10CatalogCandidateId.H8_PROPERTY_CONFIG_METADATA_R3 -> T10CandidateBindingPolicy(
            candidateId = candidateId,
            state = T10CatalogCandidateState.READ_ONLY_READY,
            decision = CandidateBindingDecision.ReadOnlyProbe(
                probeId = T10ProbeId.READ_PROPERTY_CONFIG,
                binding = resolve(T10ProbeId.READ_PROPERTY_CONFIG),
            ),
        )

        T10CatalogCandidateId.S11_SOURCE_DOMAIN_R1 -> T10CandidateBindingPolicy(
            candidateId = candidateId,
            state = T10CatalogCandidateState.MUTATION_REVIEW,
            decision = CandidateBindingDecision.Closed(CandidateBindingReason.CANDIDATE_NOT_READY),
        )

        T10CatalogCandidateId.S12_REJECTED_SHAPE_R1 -> T10CandidateBindingPolicy(
            candidateId = candidateId,
            state = T10CatalogCandidateState.REJECTED,
            decision = CandidateBindingDecision.Closed(CandidateBindingReason.REJECTED_REVISION),
        )
    }
}
