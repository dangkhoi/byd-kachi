package com.byd.clusternav.offcar

sealed interface ReadStep {
    data class EvidenceCheck(val evidenceId: String) : ReadStep
    data class PropertyConfig(val target: PropertySelector) : ReadStep
    data class PriorValue(val target: PropertySelector, val token: String = "PRIOR") : ReadStep
}

sealed interface Observation {
    data class ReadBack(val target: PropertySelector, val expected: String) : Observation
    data class Surface(
        val surface: SurfaceKind,
        val expected: String,
        val state: VisualObservationState = VisualObservationState.NOT_RUN_OFF_CAR,
    ) : Observation
    data class EvidenceOnly(val evidenceId: String, val expected: String) : Observation
}

sealed interface InverseStep {
    data class RestorePrior(val target: PropertySelector, val token: String = "PRIOR") : InverseStep
    data class VerifyRestored(val target: PropertySelector) : InverseStep
}

enum class SurfaceKind {
    CLUSTER_NAVIGATION,
    HUD_NAVIGATION,
    HUD_ROAD,
    CLUSTER_SPEED_SIGN,
    HUD_SPEED_SIGN,
    UNRELATED_WARNINGS,
}

enum class VisualObservationState {
    NOT_RUN_OFF_CAR,
}

enum class PlanDisposition {
    UNKNOWN,
    BLOCKED,
}

data class CommandPlan(
    val id: String,
    val candidate: VehicleCandidate,
    val disposition: PlanDisposition,
    val reason: String,
    val offCarVisualPass: Boolean = false,
) {
    init {
        require(id.isNotBlank())
        require(!offCarVisualPass) { "off-car plans cannot assert a visual PASS" }
    }
}

data class PropertyGatewayContract(
    val configMethod: String,
    val readMethod: String,
    val writeMethod: String,
    val deprecatedFallbackMethod: String,
    val modernFirst: Boolean,
    val fallbackOnlyWhenModernUnavailable: Boolean,
)

object ModernPropertyGatewayContract {
    val contract = PropertyGatewayContract(
        configMethod = "getPropertyConfigs",
        readMethod = "getCarProperty",
        writeMethod = "setCarProperty",
        deprecatedFallbackMethod = "setProperty",
        modernFirst = true,
        fallbackOnlyWhenModernUnavailable = true,
    )
}

data class CapabilityPromotionInput(
    val corpusComplete: Boolean,
    val requiredArtifactsAvailable: Boolean,
    val exactMatrixExhausted: Boolean,
    val negativeFieldRows: Int,
    val positiveFieldProof: Boolean,
)

object CapabilityPromoter {
    fun resolve(input: CapabilityPromotionInput): CapabilityState = when {
        input.positiveFieldProof -> CapabilityState.FIELD_PROVEN
        !input.requiredArtifactsAvailable -> CapabilityState.UNKNOWN
        !input.corpusComplete -> CapabilityState.UNKNOWN
        input.exactMatrixExhausted && input.negativeFieldRows > 0 -> CapabilityState.UNSUPPORTED
        else -> CapabilityState.UNKNOWN
    }

    fun unavailableBranch(): CapabilityState = CapabilityState.UNAVAILABLE
}
