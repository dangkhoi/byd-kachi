package com.byd.clusternav.offcar

class CandidateScenarioGenerator {
    fun generate(): List<CommandPlan> = listOf(
        plan(
            id = "OFFCAR-H0-PHYSICAL-HUD", feature = CandidateFeature.NAV_HUD,
            property = KnownProperty.PHYSICAL_HUD_SWITCH, dimension = MutationDimension.PHYSICAL_HUD_GATE,
            value = MutationValue.KnownBoolean(true), risk = CandidateRisk.HIGH,
            disposition = PlanDisposition.BLOCKED,
            reason = "Physical HUD ownership requires separately consented future diagnostic handling.",
            access = PropertyAccess.UNKNOWN, type = PropertyValueType.BOOLEAN,
            provider = ProviderKind.HUD_SERVICE, providerVerified = false, configVerified = false,
            consumer = null, evidenceIds = listOf("H0", "H7"),
            surfaces = listOf(SurfaceKind.HUD_NAVIGATION),
        ),
        plan(
            id = "OFFCAR-H1-NAV-MAP", feature = CandidateFeature.NAV_HUD,
            property = KnownProperty.HUD_NAVIGATION_MAP, dimension = MutationDimension.NAV_HUD_GATE,
            value = MutationValue.KnownEnum("ON", 2, "H1"), risk = CandidateRisk.MEDIUM,
            disposition = PlanDisposition.UNKNOWN,
            reason = "Exact 2/1 call site exists; property access, type/provider config and prior remain unproven.",
            access = PropertyAccess.UNKNOWN, type = PropertyValueType.INTEGER,
            provider = ProviderKind.DICAR_PROPERTY, providerVerified = true, configVerified = false,
            consumer = null, evidenceIds = listOf("H1", "H4", "H7"),
            surfaces = listOf(SurfaceKind.CLUSTER_NAVIGATION, SurfaceKind.HUD_NAVIGATION),
        ),
        plan(
            id = "OFFCAR-H2-HUD-SERVICE", feature = CandidateFeature.NAV_HUD,
            property = KnownProperty.HUD_SERVICE_NAVIGATION_MAP, dimension = MutationDimension.HUD_SERVICE_GATE,
            value = MutationValue.KnownBoolean(true), risk = CandidateRisk.MEDIUM,
            disposition = PlanDisposition.UNKNOWN,
            reason = "Interface and client wrapper exist, but target service resolution and permission are unknown.",
            access = PropertyAccess.UNKNOWN, type = PropertyValueType.SERVICE_CALL,
            provider = ProviderKind.HUD_SERVICE, providerVerified = false, configVerified = false,
            consumer = null, evidenceIds = listOf("H2", "H4", "H7"),
            surfaces = listOf(SurfaceKind.CLUSTER_NAVIGATION, SurfaceKind.HUD_NAVIGATION),
        ),
        plan(
            id = "OFFCAR-H3-NAV-FUSION", feature = CandidateFeature.NAV_HUD,
            property = KnownProperty.NAVIGATION_FUSION, dimension = MutationDimension.NAV_FUSION_GATE,
            value = MutationValue.KnownBoolean(true), risk = CandidateRisk.MEDIUM,
            disposition = PlanDisposition.UNKNOWN,
            reason = "Fusion controls are source-backed gates without exact target config or inverse semantics.",
            access = PropertyAccess.UNKNOWN, type = PropertyValueType.BOOLEAN,
            provider = ProviderKind.DICAR_PROPERTY, providerVerified = false, configVerified = false,
            consumer = null, evidenceIds = listOf("H3", "H4", "H7"),
            surfaces = listOf(SurfaceKind.CLUSTER_NAVIGATION, SurfaceKind.HUD_NAVIGATION),
        ),
        plan(
            id = "OFFCAR-H4-AMAP-PROFILE", feature = CandidateFeature.NAV_HUD,
            property = KnownProperty.AMAP_NAVIGATION_PROFILE, dimension = MutationDimension.AMAP_PROFILE,
            value = MutationValue.KnownToken(CatalogToken.GUIDANCE_LEFT), risk = CandidateRisk.LOW,
            disposition = PlanDisposition.UNKNOWN,
            reason = "Canonical content shape is source-backed; HUD surface behavior is not field-proven.",
            access = PropertyAccess.WRITE, type = PropertyValueType.BINARY,
            provider = ProviderKind.AMAP_SERVICE, providerVerified = true, configVerified = true,
            consumer = "H4", evidenceIds = listOf("H4", "H7"),
            surfaces = listOf(SurfaceKind.CLUSTER_NAVIGATION, SurfaceKind.HUD_NAVIGATION),
        ),
        plan(
            id = "OFFCAR-H5-DIRECT-NAV", feature = CandidateFeature.NAV_HUD,
            property = KnownProperty.INSTRUMENT_NAVIGATION_PAYLOAD,
            dimension = MutationDimension.DIRECT_NAV_PAYLOAD,
            value = MutationValue.KnownInt(2), risk = CandidateRisk.HIGH,
            disposition = PlanDisposition.BLOCKED,
            reason = "Direct instrument fallback is held until canonical Amap plus map-gate candidates are exhausted.",
            access = PropertyAccess.WRITE, type = PropertyValueType.INTEGER,
            provider = ProviderKind.INSTRUMENT, providerVerified = true, configVerified = false,
            consumer = "H5", evidenceIds = listOf("H4", "H5", "H7"),
            surfaces = listOf(SurfaceKind.CLUSTER_NAVIGATION, SurfaceKind.HUD_NAVIGATION),
        ),
        plan(
            id = "OFFCAR-H6-HUD-ROAD", feature = CandidateFeature.HUD_ROAD,
            property = KnownProperty.AMAP_ROAD_NAME, dimension = MutationDimension.HUD_ROAD_PAYLOAD,
            value = MutationValue.KnownToken(CatalogToken.ROAD_B), risk = CandidateRisk.LOW,
            disposition = PlanDisposition.UNKNOWN,
            reason = "Road producer is source-backed; direct HUD road consumer remains unavailable.",
            access = PropertyAccess.WRITE, type = PropertyValueType.TEXT,
            provider = ProviderKind.AMAP_SERVICE, providerVerified = true, configVerified = true,
            consumer = null, evidenceIds = listOf("H4", "H6", "H7"),
            surfaces = listOf(SurfaceKind.CLUSTER_NAVIGATION, SurfaceKind.HUD_ROAD),
        ),
        speedPlan(
            id = "OFFCAR-S1-STATISTICS-SIGN", property = KnownProperty.STATISTICS_SPEED_LIMIT,
            dimension = MutationDimension.STATISTICS_SPEED_VALUE,
            value = MutationValue.IntSequence(listOf(50, 80), restorePrior = true),
            reason = "SET names exist, but property access, provider, exact type and clear encoding are unknown.",
            provider = ProviderKind.STATISTICS,
        ),
        speedPlan(
            id = "OFFCAR-S4-SPEED-GATE", property = KnownProperty.SPEED_ASSIST_GATE,
            dimension = MutationDimension.SPEED_ASSIST_GATE, value = MutationValue.Unspecified,
            reason = "Control call sites exist, but bounded enum/range and inverse semantics are unknown.",
            provider = ProviderKind.ADAS,
        ),
        speedPlan(
            id = "OFFCAR-S5-ISA-MAP-SIGN", property = KnownProperty.ISA_MAP_SPEED_LIMIT,
            dimension = MutationDimension.ISA_MAP_SPEED_VALUE,
            value = MutationValue.IntSequence(listOf(50, 80), restorePrior = true),
            reason = "Map-value SET family exists without property access/type/provider or consumer linkage.",
            provider = ProviderKind.DICAR_PROPERTY,
        ),
        plan(
            id = "OFFCAR-M4-HUD-SAFE-DRIVING", feature = CandidateFeature.HUD_SIGN,
            property = KnownProperty.HUD_SAFE_DRIVING, dimension = MutationDimension.HUD_SAFE_DRIVING_GATE,
            value = MutationValue.KnownBoolean(true), risk = CandidateRisk.MEDIUM,
            disposition = PlanDisposition.UNKNOWN,
            reason = "HUD safe-driving service gate is unproven on the target profile.",
            access = PropertyAccess.UNKNOWN, type = PropertyValueType.SERVICE_CALL,
            provider = ProviderKind.HUD_SERVICE, providerVerified = false, configVerified = false,
            consumer = null, evidenceIds = listOf("H2", "H4", "H7", "S0", "S1", "S6", "S8"),
            surfaces = listOf(SurfaceKind.CLUSTER_SPEED_SIGN, SurfaceKind.HUD_SPEED_SIGN),
        ),
        plan(
            id = "OFFCAR-M4-HUD-SIGN-FUSION", feature = CandidateFeature.HUD_SIGN,
            property = KnownProperty.HUD_SIGN_FUSION, dimension = MutationDimension.HUD_SIGN_FUSION_GATE,
            value = MutationValue.KnownBoolean(true), risk = CandidateRisk.MEDIUM,
            disposition = PlanDisposition.UNKNOWN,
            reason = "Image/navigation fusion gate lacks exact access, type, prior and HUD sign consumer proof.",
            access = PropertyAccess.UNKNOWN, type = PropertyValueType.BOOLEAN,
            provider = ProviderKind.DICAR_PROPERTY, providerVerified = false, configVerified = false,
            consumer = null, evidenceIds = listOf("H3", "H4", "H7", "S0", "S1", "S6", "S8"),
            surfaces = listOf(SurfaceKind.CLUSTER_SPEED_SIGN, SurfaceKind.HUD_SPEED_SIGN),
        ),
    )

    private fun speedPlan(
        id: String,
        property: KnownProperty,
        dimension: MutationDimension,
        value: MutationValue,
        reason: String,
        provider: ProviderKind,
    ): CommandPlan = plan(
        id = id, feature = CandidateFeature.CLUSTER_SIGN, property = property,
        dimension = dimension, value = value, risk = CandidateRisk.HIGH,
        disposition = PlanDisposition.UNKNOWN, reason = reason,
        access = PropertyAccess.UNKNOWN, type = PropertyValueType.INTEGER,
        provider = provider, providerVerified = false, configVerified = false,
        consumer = "S8", evidenceIds = (0..10).map { "S$it" },
        surfaces = listOf(
            SurfaceKind.CLUSTER_SPEED_SIGN,
            SurfaceKind.HUD_SPEED_SIGN,
            SurfaceKind.UNRELATED_WARNINGS,
        ),
        range = 1..300,
    )

    private fun plan(
        id: String,
        feature: CandidateFeature,
        property: KnownProperty,
        dimension: MutationDimension,
        value: MutationValue,
        risk: CandidateRisk,
        disposition: PlanDisposition,
        reason: String,
        access: PropertyAccess,
        type: PropertyValueType,
        provider: ProviderKind,
        providerVerified: Boolean,
        configVerified: Boolean,
        consumer: String?,
        evidenceIds: List<String>,
        surfaces: List<SurfaceKind>,
        range: IntRange? = null,
    ): CommandPlan {
        val target = PropertySelector.Catalog(property)
        val candidate = VehicleCandidate(
            id = id,
            feature = feature,
            preconditions = evidenceIds.distinct().map(ReadStep::EvidenceCheck) +
                ReadStep.PropertyConfig(target) + ReadStep.PriorValue(target),
            mutations = listOf(
                MutationStep.CatalogMutation(
                    dimension = dimension,
                    contract = PropertyContract(
                        target = target,
                        access = access,
                        valueType = type,
                        provider = ProviderProof(provider, providerVerified),
                        propertyConfigEvidenceId = property.evidenceId,
                        propertyConfigVerified = configVerified,
                        expectedConsumerEvidenceId = consumer,
                        boundedRange = range,
                        artifactEvidenceIds = evidenceIds.toSet(),
                    ),
                    value = value,
                ),
            ),
            observations = listOf(Observation.ReadBack(target, "PRIOR_OR_TYPED_VALUE")) +
                surfaces.map { Observation.Surface(it, "INDEPENDENT_FUTURE_OBSERVATION") },
            inverse = listOf(InverseStep.RestorePrior(target), InverseStep.VerifyRestored(target)),
            risk = risk,
            evidenceIds = evidenceIds.distinct(),
        )
        return CommandPlan("$id-PLAN", candidate, disposition, reason)
    }
}

enum class RequirementVerificationStatus {
    VERIFIED_OFF_CAR,
    DEFERRED_T10_T11,
    BLOCKED_BY_EXPLICIT_NO_ADB_INSTALL,
    NOT_EXHAUSTIVE,
}

data class TraceLink(
    val requirement: String,
    val tasks: Set<String>,
    val gates: Set<String>,
    val futureIds: Set<String>,
    val artifact: String,
    val status: RequirementVerificationStatus,
)

object TraceabilityCatalog {
    val requirements = (1..32).map { "R$it" }.toSet()
    val tasks = (0..11).map { "T$it" }.toSet()
    val gates = (1..27).map { "O$it" }.toSet()
    val futureIds = setOf(
        "D-H0-HUD-PHYSICAL-TEMP", "D-M1-NAV-HUD", "D-M2-HUD-ROAD",
        "D-M3-CLUSTER-SIGN", "D-M4-HUD-SIGN", "P-M1-NAV-HUD",
        "P-M2-HUD-ROAD", "P-M3-CLUSTER-SIGN", "P-M4-HUD-SIGN",
    )

    private fun ids(value: String): Set<String> =
        value.split(',').map(String::trim).filter(String::isNotEmpty).toSet()

    private val artifactPaths = mapOf(
        "traceability.json" to "docs/diagnostics/hud-sign-re/traceability.json",
        "DependencyBoundaryTest.kt" to "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/OffCarTransportFenceTest.kt + offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/DependencyBoundaryTest.kt",
        "AmapFrameBuilderContractTest.kt" to "app/src/test/java/com/byd/clusternav/AmapFrameBuilderContractTest.kt",
        "evidence-index.json" to "docs/diagnostics/hud-sign-re/evidence-index.json",
        "candidate-report.html" to "docs/diagnostics/hud-sign-re/candidate-report.html",
        "HudRoadCapabilityTest.kt" to "app/src/test/java/com/byd/clusternav/HudRoadCapabilityTest.kt",
        "SpeedSignSourceLifecycleTest.kt" to "app/src/test/java/com/byd/clusternav/SpeedSignSourceLifecycleTest.kt",
        "NaviInfoSchemaTest.kt" to "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/NaviInfoSchemaTest.kt",
        "SpeedLimitFrameTest.kt" to "vehicle-contracts/src/test/kotlin/com/byd/clusternav/contracts/SpeedLimitFrameTest.kt",
        "m3-cluster-sign-plan.json" to "docs/diagnostics/hud-sign-re/m3-cluster-sign-plan.json",
        "m4-hud-sign-plan.json" to "docs/diagnostics/hud-sign-re/m4-hud-sign-plan.json",
        "PropertyCandidateValidatorTest.kt" to "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/PropertyCandidateValidatorTest.kt",
        "ModernPropertyGatewayContractTest.kt" to "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ModernPropertyGatewayContractTest.kt",
        "native/libbydcluster-diff.json" to "docs/diagnostics/hud-sign-re/native/libbydcluster-diff.json",
        "FirmwareEvidenceGraphTest.kt" to "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/FirmwareEvidenceGraphTest.kt",
        "CandidateSafetyTest.kt" to "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/CandidateSafetyTest.kt",
        "ScenarioSnapshotTest.kt" to "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ScenarioSnapshotTest.kt",
        "OffCarTransportFenceTest.kt" to "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/OffCarTransportFenceTest.kt",
        "MainProbeSurfaceAbsenceTest.kt" to "app/src/test/java/com/byd/clusternav/MainProbeSurfaceAbsenceTest.kt",
        "NavigationOutputIsolationTest.kt" to "app/src/test/java/com/byd/clusternav/navigation/NavigationOutputIsolationTest.kt",
        "SpeedSignLifecycleCoordinatorTest.kt" to "core/src/test/kotlin/com/byd/clusternav/navigation/SpeedSignLifecycleCoordinatorTest.kt",
        "CapabilityPromotionTest.kt" to "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/CapabilityPromotionTest.kt",
        "FloatingBubbleFirstLaunchContractTest.kt" to "app/src/test/java/com/byd/clusternav/FloatingBubbleFirstLaunchContractTest.kt + app/src/test/java/com/byd/clusternav/modules/clustercast/CastUILifecycleSafetyTest.kt + docs/diagnostics/hud-sign-re/first-launch-emulator-result.json",
        "verify-seal-hud-sign-offcar.sh" to "scripts/verify-seal-hud-sign-offcar.sh",
        "tools/re/manifest.json" to "tools/re/manifest.json + scripts/verify-seal-hud-sign-offcar.sh",
        "test_report_sanitizer.py" to "scripts/re/tests/test_report_sanitizer.py",
        "future exact identities" to "docs/_handoff/hud-sign-vehicle-test-candidate.json + docs/_handoff/hud-sign-release-candidate.json",
        "m1-m4 plan packs" to "docs/diagnostics/hud-sign-re/m1-nav-hud-plan.json + docs/diagnostics/hud-sign-re/m2-hud-road-plan.json + docs/diagnostics/hud-sign-re/m3-cluster-sign-plan.json + docs/diagnostics/hud-sign-re/m4-hud-sign-plan.json",
        "PhysicalHudOwnershipTest.kt" to "app/src/test/java/com/byd/clusternav/PhysicalHudOwnershipTest.kt",
        "corpus-completeness.json" to "docs/diagnostics/hud-sign-re/corpus-completeness.json",
    )

    private val verificationStatuses = requirements.associateWith {
        RequirementVerificationStatus.VERIFIED_OFF_CAR
    } + mapOf(
        "R10" to RequirementVerificationStatus.DEFERRED_T10_T11,
        "R11" to RequirementVerificationStatus.DEFERRED_T10_T11,
        "R24" to RequirementVerificationStatus.BLOCKED_BY_EXPLICIT_NO_ADB_INSTALL,
        "R28" to RequirementVerificationStatus.DEFERRED_T10_T11,
        "R32" to RequirementVerificationStatus.NOT_EXHAUSTIVE,
    )

    private fun link(r: String, t: String, o: String, f: String, artifact: String) =
        TraceLink(
            requirement = r,
            tasks = ids(t),
            gates = ids(o),
            futureIds = ids(f),
            artifact = artifactPaths.getValue(artifact),
            status = verificationStatuses.getValue(r),
        )

    val links = listOf(
        link("R1", "T0,T4,T5,T8,T9", "O9,O26,O27", "D-M1-NAV-HUD,D-M2-HUD-ROAD,D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN,P-M1-NAV-HUD,P-M2-HUD-ROAD,P-M3-CLUSTER-SIGN,P-M4-HUD-SIGN", "traceability.json"),
        link("R2", "T0,T5,T6,T8,T9", "O13,O15,O26", "", "DependencyBoundaryTest.kt"),
        link("R3", "T2,T5,T7,T10,T11", "O6,O19", "D-M1-NAV-HUD,P-M1-NAV-HUD", "AmapFrameBuilderContractTest.kt"),
        link("R4", "T2,T4,T5,T9,T10,T11", "O5,O10,O12", "D-M1-NAV-HUD,P-M1-NAV-HUD", "evidence-index.json"),
        link("R5", "T2,T4,T5,T9,T10,T11", "O5,O9,O10", "D-M1-NAV-HUD,D-M2-HUD-ROAD,P-M1-NAV-HUD,P-M2-HUD-ROAD", "candidate-report.html"),
        link("R6", "T4,T5,T7,T10,T11", "O12,O17", "D-M2-HUD-ROAD,P-M2-HUD-ROAD", "HudRoadCapabilityTest.kt"),
        link("R7", "T5,T7,T8,T10,T11", "O17,O18", "D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN,P-M3-CLUSTER-SIGN,P-M4-HUD-SIGN", "SpeedSignSourceLifecycleTest.kt"),
        link("R8", "T2,T4,T5,T10,T11", "O6,O17", "D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN", "NaviInfoSchemaTest.kt"),
        link("R9", "T5,T7,T10,T11", "O17", "D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN,P-M3-CLUSTER-SIGN,P-M4-HUD-SIGN", "SpeedLimitFrameTest.kt"),
        link("R10", "T4,T5,T9,T10,T11", "O10,O11,O12,O27", "D-M3-CLUSTER-SIGN,P-M3-CLUSTER-SIGN", "m3-cluster-sign-plan.json"),
        link("R11", "T4,T5,T9,T10,T11", "O10,O11,O12,O19,O27", "D-M4-HUD-SIGN,P-M4-HUD-SIGN", "m4-hud-sign-plan.json"),
        link("R12", "T2,T4,T6,T10,T11", "O5,O11,O16", "D-M1-NAV-HUD,D-M2-HUD-ROAD,D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN", "PropertyCandidateValidatorTest.kt"),
        link("R13", "T2,T6,T8,T10,T11", "O16", "D-M1-NAV-HUD,D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN", "ModernPropertyGatewayContractTest.kt"),
        link("R14", "T3,T8,T10,T11", "O7,O8", "D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN", "native/libbydcluster-diff.json"),
        link("R15", "T2,T3,T4,T5,T9", "O9", "D-M1-NAV-HUD,D-M2-HUD-ROAD,D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN", "FirmwareEvidenceGraphTest.kt"),
        link("R16", "T4,T5,T6,T8,T10", "O10,O11,O12,O13,O14,O15", "D-M1-NAV-HUD,D-M2-HUD-ROAD,D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN", "CandidateSafetyTest.kt"),
        link("R17", "T5,T8,T9", "O12,O14,O27", "D-M1-NAV-HUD,D-M2-HUD-ROAD,D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN,P-M1-NAV-HUD,P-M2-HUD-ROAD,P-M3-CLUSTER-SIGN,P-M4-HUD-SIGN", "ScenarioSnapshotTest.kt"),
        link("R18", "T5,T6,T8,T9", "O13", "", "OffCarTransportFenceTest.kt"),
        link("R19", "T0,T8,T10", "O15", "D-M1-NAV-HUD,D-M2-HUD-ROAD,D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN", "MainProbeSurfaceAbsenceTest.kt"),
        link("R20", "T4,T5,T9,T10,T11", "O9,O27", "D-M1-NAV-HUD,D-M2-HUD-ROAD,D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN,P-M1-NAV-HUD,P-M2-HUD-ROAD,P-M3-CLUSTER-SIGN,P-M4-HUD-SIGN", "FirmwareEvidenceGraphTest.kt"),
        link("R21", "T5,T7,T8,T10,T11", "O19", "D-M1-NAV-HUD,D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN,P-M1-NAV-HUD,P-M3-CLUSTER-SIGN,P-M4-HUD-SIGN", "NavigationOutputIsolationTest.kt"),
        link("R22", "T5,T7,T8,T10,T11", "O18,O19", "D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN,P-M3-CLUSTER-SIGN,P-M4-HUD-SIGN", "SpeedSignLifecycleCoordinatorTest.kt"),
        link("R23", "T4,T5,T9,T10,T11", "O4,O9,O10", "D-M1-NAV-HUD,D-M2-HUD-ROAD,D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN", "CapabilityPromotionTest.kt"),
        link("R24", "T0,T7,T8,T11", "O21,O22", "P-M1-NAV-HUD", "FloatingBubbleFirstLaunchContractTest.kt"),
        link("R25", "T0,T4,T6,T8,T9,T10,T11", "O23,O24", "", "verify-seal-hud-sign-offcar.sh"),
        link("R26", "T1,T8", "O2,O3", "", "tools/re/manifest.json"),
        link("R27", "T0,T1,T2,T3,T8,T9", "O25", "", "test_report_sanitizer.py"),
        link("R28", "T0,T9,T10,T11", "O1,O27", "D-H0-HUD-PHYSICAL-TEMP,D-M1-NAV-HUD,D-M2-HUD-ROAD,D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN,P-M1-NAV-HUD,P-M2-HUD-ROAD,P-M3-CLUSTER-SIGN,P-M4-HUD-SIGN", "future exact identities"),
        link("R29", "T9,T10,T11", "O27", "D-M1-NAV-HUD,D-M2-HUD-ROAD,D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN,P-M1-NAV-HUD,P-M2-HUD-ROAD,P-M3-CLUSTER-SIGN,P-M4-HUD-SIGN", "m1-m4 plan packs"),
        link("R30", "T9,T10,T11", "O13,O26", "D-H0-HUD-PHYSICAL-TEMP,D-M1-NAV-HUD,D-M2-HUD-ROAD,D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN,P-M1-NAV-HUD,P-M2-HUD-ROAD,P-M3-CLUSTER-SIGN,P-M4-HUD-SIGN", "OffCarTransportFenceTest.kt"),
        link("R31", "T4,T7,T9,T10,T11", "O20", "D-H0-HUD-PHYSICAL-TEMP,D-M1-NAV-HUD,P-M1-NAV-HUD", "PhysicalHudOwnershipTest.kt"),
        link("R32", "T0,T2,T3,T8,T10,T11", "O4,O8", "D-M1-NAV-HUD,D-M2-HUD-ROAD,D-M3-CLUSTER-SIGN,D-M4-HUD-SIGN", "corpus-completeness.json"),
    )

    fun validate(): List<String> = buildList {
        if (links.map(TraceLink::requirement).toSet() != requirements) add("requirements are incomplete")
        if (links.flatMap { it.tasks }.toSet() != tasks) add("task references are incomplete")
        if (links.flatMap { it.gates }.toSet() != gates) add("gate references are incomplete")
        if (links.flatMap { it.futureIds }.toSet() != futureIds) add("future references are incomplete")
        val forbidden = Regex("(^|[-,])(all|N/A|T\\d+-T\\d+|O\\d+-O\\d+)([-,]|$)", RegexOption.IGNORE_CASE)
        links.forEach { link ->
            (link.tasks + link.gates + link.futureIds).filter { forbidden.containsMatchIn(it) }.forEach {
                add("${link.requirement} contains alias $it")
            }
        }
    }
}
