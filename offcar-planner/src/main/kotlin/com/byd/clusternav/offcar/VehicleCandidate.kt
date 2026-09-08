package com.byd.clusternav.offcar

enum class CandidateFeature {
    NAV_HUD,
    HUD_ROAD,
    CLUSTER_SIGN,
    HUD_SIGN,
}

enum class CandidateRisk {
    LOW,
    MEDIUM,
    HIGH,
}

enum class MutationDimension {
    PHYSICAL_HUD_GATE,
    NAV_HUD_GATE,
    HUD_SERVICE_GATE,
    NAV_FUSION_GATE,
    AMAP_PROFILE,
    DIRECT_NAV_PAYLOAD,
    HUD_ROAD_PAYLOAD,
    STATISTICS_SPEED_VALUE,
    SPEED_ASSIST_GATE,
    ISA_MAP_SPEED_VALUE,
    HUD_SAFE_DRIVING_GATE,
    HUD_SIGN_FUSION_GATE,
}

enum class PropertyAccess {
    UNKNOWN,
    READ,
    WRITE,
    READ_WRITE,
    UNAVAILABLE,
}

enum class PropertyValueType {
    UNKNOWN,
    INTEGER,
    BOOLEAN,
    TEXT,
    BINARY,
    SERVICE_CALL,
}

enum class ProviderKind {
    UNKNOWN,
    DICAR_PROPERTY,
    AMAP_SERVICE,
    HUD_SERVICE,
    INSTRUMENT,
    STATISTICS,
    ADAS,
}

data class ProviderProof(val kind: ProviderKind, val verified: Boolean)

enum class KnownProperty(val evidenceId: String) {
    PHYSICAL_HUD_SWITCH("H0"),
    HUD_NAVIGATION_MAP("H1"),
    HUD_SERVICE_NAVIGATION_MAP("H2"),
    NAVIGATION_FUSION("H3"),
    AMAP_NAVIGATION_PROFILE("H4"),
    INSTRUMENT_NAVIGATION_PAYLOAD("H5"),
    AMAP_ROAD_NAME("H6"),
    STATISTICS_SPEED_LIMIT("S1"),
    SPEED_ASSIST_GATE("S4"),
    ISA_MAP_SPEED_LIMIT("S5"),
    HUD_SAFE_DRIVING("H2"),
    HUD_SIGN_FUSION("H3"),
}

sealed interface PropertySelector {
    data class Catalog(val property: KnownProperty) : PropertySelector
    data class FreeForm(val name: String) : PropertySelector
    data class RawId(val value: String) : PropertySelector
}

enum class MutationMode {
    SINGLE_DIMENSION,
    MASS,
}

enum class CatalogToken {
    GUIDANCE_LEFT,
    ROAD_B,
}

sealed interface MutationValue {
    data class KnownInt(val value: Int) : MutationValue
    data class KnownBoolean(val value: Boolean) : MutationValue
    data class KnownToken(val value: CatalogToken) : MutationValue
    data class IntSequence(val values: List<Int>, val restorePrior: Boolean) : MutationValue
    data class KnownEnum(val name: String, val encodedValue: Int, val evidenceId: String) : MutationValue
    data class GuessedEnum(val name: String, val encodedValue: Int) : MutationValue
    data object Unspecified : MutationValue
}

data class PropertyContract(
    val target: PropertySelector,
    val access: PropertyAccess,
    val valueType: PropertyValueType,
    val provider: ProviderProof,
    val propertyConfigEvidenceId: String?,
    val propertyConfigVerified: Boolean,
    val expectedConsumerEvidenceId: String?,
    val boundedRange: IntRange?,
    val artifactEvidenceIds: Set<String>,
)

sealed interface MutationStep {
    data class CatalogMutation(
        val dimension: MutationDimension,
        val contract: PropertyContract,
        val value: MutationValue,
        val mode: MutationMode = MutationMode.SINGLE_DIMENSION,
    ) : MutationStep
}

data class VehicleCandidate(
    val id: String,
    val feature: CandidateFeature,
    val preconditions: List<ReadStep>,
    val mutations: List<MutationStep>,
    val observations: List<Observation>,
    val inverse: List<InverseStep>,
    val risk: CandidateRisk,
    val evidenceIds: List<String>,
)

enum class CandidateValidationIssue {
    MISSING_ARTIFACT_EVIDENCE,
    RAW_ID,
    FREE_FORM_NAME,
    MASS_MODE,
    GUESSED_ENUM,
    UNBOUNDED_VALUE,
    MISSING_MUTATION,
    MULTI_DIMENSION_MUTATION,
    MISSING_PROPERTY_CONFIG,
    MISSING_WRITE_ACCESS,
    MISSING_VALUE_TYPE,
    MISSING_PROVIDER,
    MISSING_EXPECTED_CONSUMER,
    MISSING_PRIOR,
    MISSING_INVERSE,
    MISSING_OBSERVATION,
}

object PropertyCandidateValidator {
    fun validate(
        candidate: VehicleCandidate,
        evidence: Map<String, FirmwareEvidence> = FirmwareEvidenceCatalog.byId,
    ): Set<CandidateValidationIssue> = buildSet {
        val citedIds = candidate.evidenceIds.toSet()
        if (citedIds.isEmpty() || citedIds.any { evidence[it]?.citations.isNullOrEmpty() }) {
            add(CandidateValidationIssue.MISSING_ARTIFACT_EVIDENCE)
        }
        if (candidate.mutations.isEmpty()) add(CandidateValidationIssue.MISSING_MUTATION)
        if (candidate.mutations.size > 1) add(CandidateValidationIssue.MULTI_DIMENSION_MUTATION)
        if (candidate.observations.isEmpty()) add(CandidateValidationIssue.MISSING_OBSERVATION)

        candidate.mutations.filterIsInstance<MutationStep.CatalogMutation>().forEach { mutation ->
            val contract = mutation.contract
            when (contract.target) {
                is PropertySelector.RawId -> add(CandidateValidationIssue.RAW_ID)
                is PropertySelector.FreeForm -> add(CandidateValidationIssue.FREE_FORM_NAME)
                is PropertySelector.Catalog -> Unit
            }
            if (mutation.mode == MutationMode.MASS) add(CandidateValidationIssue.MASS_MODE)
            val boundedRange = contract.boundedRange
            when (val value = mutation.value) {
                is MutationValue.GuessedEnum -> add(CandidateValidationIssue.GUESSED_ENUM)
                MutationValue.Unspecified -> add(CandidateValidationIssue.UNBOUNDED_VALUE)
                is MutationValue.KnownInt -> if (boundedRange == null || value.value !in boundedRange) {
                    add(CandidateValidationIssue.UNBOUNDED_VALUE)
                }
                is MutationValue.IntSequence -> if (
                    value.values.isEmpty() || boundedRange == null || value.values.any { it !in boundedRange }
                ) {
                    add(CandidateValidationIssue.UNBOUNDED_VALUE)
                }
                is MutationValue.KnownEnum -> if (
                    value.evidenceId !in citedIds || evidence[value.evidenceId]?.citations.isNullOrEmpty()
                ) {
                    add(CandidateValidationIssue.MISSING_ARTIFACT_EVIDENCE)
                }
                else -> Unit
            }
            if (!contract.propertyConfigVerified || contract.propertyConfigEvidenceId == null) {
                add(CandidateValidationIssue.MISSING_PROPERTY_CONFIG)
            }
            if (contract.access !in setOf(PropertyAccess.WRITE, PropertyAccess.READ_WRITE)) {
                add(CandidateValidationIssue.MISSING_WRITE_ACCESS)
            }
            if (contract.valueType == PropertyValueType.UNKNOWN) {
                add(CandidateValidationIssue.MISSING_VALUE_TYPE)
            }
            if (!contract.provider.verified || contract.provider.kind == ProviderKind.UNKNOWN) {
                add(CandidateValidationIssue.MISSING_PROVIDER)
            }
            if (contract.expectedConsumerEvidenceId == null) {
                add(CandidateValidationIssue.MISSING_EXPECTED_CONSUMER)
            }
            val requiredEvidence = contract.artifactEvidenceIds +
                listOfNotNull(contract.propertyConfigEvidenceId, contract.expectedConsumerEvidenceId)
            if (requiredEvidence.any { evidence[it]?.citations.isNullOrEmpty() }) {
                add(CandidateValidationIssue.MISSING_ARTIFACT_EVIDENCE)
            }
            val hasPrior = candidate.preconditions.any {
                it is ReadStep.PriorValue && it.target == contract.target
            }
            val hasInverse = candidate.inverse.any {
                it is InverseStep.RestorePrior && it.target == contract.target
            }
            if (!hasPrior) add(CandidateValidationIssue.MISSING_PRIOR)
            if (!hasInverse) add(CandidateValidationIssue.MISSING_INVERSE)
        }
    }

    val prohibitedShapeIssues = setOf(
        CandidateValidationIssue.RAW_ID,
        CandidateValidationIssue.FREE_FORM_NAME,
        CandidateValidationIssue.MASS_MODE,
        CandidateValidationIssue.GUESSED_ENUM,
        CandidateValidationIssue.MISSING_PRIOR,
        CandidateValidationIssue.MISSING_INVERSE,
        CandidateValidationIssue.MULTI_DIMENSION_MUTATION,
    )
}
