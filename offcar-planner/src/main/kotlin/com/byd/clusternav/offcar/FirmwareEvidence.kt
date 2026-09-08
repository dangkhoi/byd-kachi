package com.byd.clusternav.offcar

enum class EvidenceLevel {
    SOURCE_CONSTANT,
    WRITE_INTENT_CONSTANT,
    STATUS_OR_OUTPUT_ONLY,
    CONTROL_OR_GATE,
    SERVICE_INTERFACE,
    CLIENT_WRAPPER,
    CONCRETE_SET_CALL_SITE,
    NATIVE_CONSUMER,
    RECORDED_FIELD,
    OWNER_OBSERVATION,
    FIELD_PROVEN,
    REMINDER_THRESHOLD,
    SOURCE_ARBITRATION,
    UNKNOWN,
    UNAVAILABLE,
}

enum class EvidenceState {
    CITED_CANDIDATE,
    CITED_NATIVE_CONSUMER,
    FIELD_PROVEN,
    UNKNOWN,
    UNAVAILABLE,
}

enum class CorpusVerdict {
    COMPLETE_FOR_AVAILABLE_JAVA_AND_REQUESTED_NATIVE_SYMBOL_SCOPE,
    NOT_EXHAUSTIVE,
}

enum class CapabilityState {
    UNKNOWN,
    UNAVAILABLE,
    FIELD_PROVEN,
    UNSUPPORTED,
}

data class ArtifactCitation(
    val artifactSha256: String,
    val path: String,
    val lineOrAddress: String,
) {
    init {
        require(artifactSha256.matches(Regex("[0-9a-f]{64}"))) { "artifact SHA-256 is required" }
        require(path.isNotBlank()) { "artifact path is required" }
        require(lineOrAddress.isNotBlank()) { "line or address is required" }
    }
}

data class FirmwareEvidence(
    val id: String,
    val level: EvidenceLevel,
    val state: EvidenceState,
    val claim: String,
    val hitCount: Int,
    val citations: List<ArtifactCitation>,
    val executable: Boolean = false,
)

data class EvidenceEdge(
    val id: String,
    val fromEvidenceIds: Set<String>,
    val toEvidenceIds: Set<String>,
    val state: String,
    val surface: String,
)

data class CorpusGap(val id: String, val state: EvidenceState = EvidenceState.UNAVAILABLE)

data class NaviInfoSchema(
    val declaredFieldCount: Int,
    val fields: List<String>,
    val startObjectLine: Int,
    val hasSpeedLimit: Boolean,
)

object FirmwareEvidenceCatalog {
    const val SOURCE_INDEX_SCHEMA = "clusternav.re-evidence-graph/v1"
    const val SOURCE_INDEX_SHA256 = "ac3fd27701e6b05c5037594b35d314b49ddadaeb0315d8a64c3a3da0bef980b9"
    const val AVAILABLE_SCOPE = "COMPLETE_FOR_AVAILABLE_JAVA_AND_REQUESTED_NATIVE_SYMBOL_SCOPE"
    val corpusVerdict = CorpusVerdict.NOT_EXHAUSTIVE
    const val truthState = "SOURCE_MINED_NOT_FIELD_PROVEN"
    const val offCarVisualPass = false

    private fun citation(hash: String, path: String, locator: Any) =
        ArtifactCitation(hash, path, locator.toString())

    private fun evidence(
        id: String,
        level: EvidenceLevel,
        claim: String,
        hits: Int,
        vararg citations: ArtifactCitation,
        state: EvidenceState = EvidenceState.CITED_CANDIDATE,
    ) = FirmwareEvidence(id, level, state, claim, hits, citations.toList())

    val all: List<FirmwareEvidence> = listOf(
        evidence("H0", EvidenceLevel.CONTROL_OR_GATE, "Physical HUD switch/config", 39,
            citation("ce6b16bead84b31ea5af4510a48840e8db462245508369a9d4c087e61b2fc8bf", "carsettings/com/byd/feature/instrument/Instrument.java", 528)),
        evidence("H1", EvidenceLevel.CONCRETE_SET_CALL_SITE, "HUD navigation-map gate", 28,
            citation("7a372b4b286ca7b3933d2bc0f3ba8f082afe70a5d5f9742e8aa581d2021da9bd", "carsettings/com/byd/ccs/impl/server/hud/Hud00600401300000.java", 69),
            citation("ce6b16bead84b31ea5af4510a48840e8db462245508369a9d4c087e61b2fc8bf", "carsettings/com/byd/feature/instrument/Instrument.java", 536),
            citation("ce6b16bead84b31ea5af4510a48840e8db462245508369a9d4c087e61b2fc8bf", "carsettings/com/byd/feature/instrument/Instrument.java", 538),
            citation("ce6b16bead84b31ea5af4510a48840e8db462245508369a9d4c087e61b2fc8bf", "carsettings/com/byd/feature/instrument/Instrument.java", 539)),
        evidence("H2", EvidenceLevel.SERVICE_INTERFACE, "Modern HUD service", 404,
            citation("861a29ae7862f89ed77a1bd8664d3e66abe1136a65afef1a12b9a1c279e19d72", "fw-new/car/j2.java", 133)),
        evidence("H3", EvidenceLevel.CONTROL_OR_GATE, "Fusion/request/map-format controls", 26,
            citation("ce6b16bead84b31ea5af4510a48840e8db462245508369a9d4c087e61b2fc8bf", "carsettings/com/byd/feature/instrument/Instrument.java", 534)),
        evidence("H4", EvidenceLevel.CONCRETE_SET_CALL_SITE, "Canonical Amap state/profile", 84,
            citation("12c31f0a7eef32cfbc7a6e7e8c991153a86da3de63e3e71f4f299d3df1571678", "amap/com/example/amapservice/AmapService.java", 49),
            citation("2d799ac87e9b59f41a8cfc6b2fbb5bec902c4c9cfafd4aed21ce3ff14f3fa9cd", "amap/byd/fbs/naviInfo/NaviInfo.java", 246)),
        evidence("H5", EvidenceLevel.CONCRETE_SET_CALL_SITE, "Direct instrument icon/distance", 46,
            citation("12c31f0a7eef32cfbc7a6e7e8c991153a86da3de63e3e71f4f299d3df1571678", "amap/com/example/amapservice/AmapService.java", 588)),
        evidence("H6", EvidenceLevel.SOURCE_CONSTANT, "Road side-channel", 85,
            citation("12c31f0a7eef32cfbc7a6e7e8c991153a86da3de63e3e71f4f299d3df1571678", "amap/com/example/amapservice/AmapService.java", 598),
            citation("2d799ac87e9b59f41a8cfc6b2fbb5bec902c4c9cfafd4aed21ce3ff14f3fa9cd", "amap/byd/fbs/naviInfo/NaviInfo.java", 37)),
        evidence("H7", EvidenceLevel.SOURCE_CONSTANT, "HUD topology/status", 369,
            citation("7a372b4b286ca7b3933d2bc0f3ba8f082afe70a5d5f9742e8aa581d2021da9bd", "carsettings/com/byd/ccs/impl/server/hud/Hud00600401300000.java", 62)),
        evidence("S0", EvidenceLevel.RECORDED_FIELD, "Waze/VietMap acquisition", 523,
            citation("7ac543ad4f12568b54378d4f3199fd551198ff8a1d5b60b1c3dd7868cf5c4b6a", "dashcast/com/byd/dashcast/hud/MapNotificationListenerService.java", 20)),
        evidence("S1", EvidenceLevel.WRITE_INTENT_CONSTANT, "Statistics ISA value/type/unit/sign", 12,
            citation("9826bbdf7b53d476046f83040403ec7180e74145f5a8205e13923454e0a4ce5c", "carsettings/com/byd/feature/statistics/Statistics.java", 44),
            citation("9826bbdf7b53d476046f83040403ec7180e74145f5a8205e13923454e0a4ce5c", "carsettings/com/byd/feature/statistics/Statistics.java", 45),
            citation("9826bbdf7b53d476046f83040403ec7180e74145f5a8205e13923454e0a4ce5c", "carsettings/com/byd/feature/statistics/Statistics.java", 50),
            citation("9826bbdf7b53d476046f83040403ec7180e74145f5a8205e13923454e0a4ce5c", "carsettings/com/byd/feature/statistics/Statistics.java", 55)),
        evidence("S2", EvidenceLevel.STATUS_OR_OUTPUT_ONLY, "Instrument traffic-sign family", 20,
            citation("ce6b16bead84b31ea5af4510a48840e8db462245508369a9d4c087e61b2fc8bf", "carsettings/com/byd/feature/instrument/Instrument.java", 792)),
        evidence("S3", EvidenceLevel.STATUS_OR_OUTPUT_ONLY, "ADAS SLA/SLR/RSI outputs", 9,
            citation("2c1ec324c822582e2294e9bc13acc0ea4ab2214e6367ec6affc333dd211000ca", "carsettings/com/byd/feature/adas/Adas.java", 944)),
        evidence("S4", EvidenceLevel.CONTROL_OR_GATE, "SLA/ISA/TSR controls", 35,
            citation("68ed497de475854180df661e384ab0ec9ff958d2df6ad8c254cd2a709d9a8b6c", "carsettings/com/byd/dipilot/view/safetyassistance/old/trafficsign/LimitRange.java", 110)),
        evidence("S5", EvidenceLevel.WRITE_INTENT_CONSTANT, "Setting ISA-map speed-limit family", 24,
            citation("f275740f0875fe2106b9ff91769da556e9dae3900bd52dccc7782c4d81ff5542", "carsettings/com/byd/feature/setting/Setting.java", 949)),
        evidence("S6", EvidenceLevel.CONCRETE_SET_CALL_SITE, "Modern string property transport", 49,
            citation("63030be578db9e525179a228a7baf7a873eee87814fd87979083f7787d1d0c4d", "fw-new/com/byd/car/property/ICarPropertyManager.java", 14),
            citation("63030be578db9e525179a228a7baf7a873eee87814fd87979083f7787d1d0c4d", "fw-new/com/byd/car/property/ICarPropertyManager.java", 31),
            citation("fd630a5ad0937df4245d774ce0df954aead9b4aed2ece8601be315d38d7333d0", "fw-new/car/s2.java", 225)),
        evidence("S7", EvidenceLevel.SERVICE_INTERFACE, "trafficmonitor/provider arbitration", 34,
            citation("49b1f4bb11801a3e520c0d2cd9b77979ddf699bc7a48cc39d7f654bff4b683e9", "carsettings/com/byd/dipilot/view/safetyassistance/old/trafficsign/TSRCellular.java", 56)),
        evidence("S8", EvidenceLevel.NATIVE_CONSUMER, "Native cluster sign consumers", 25,
            citation("9f8a0b269fbee37bad510e8dfbc239b857e00a87663fc9fb7ae39913f86017ca", "cluster-old-native", "0xd8ec4"),
            citation("3197abee462e1de4ae476b8643a5570a4e90b3d0623534e9451ea810d8ee8ae8", "cluster-new-native", "0xd9240"),
            state = EvidenceState.CITED_NATIVE_CONSUMER),
        evidence("S9", EvidenceLevel.REMINDER_THRESHOLD, "Speed reminder thresholds", 60,
            citation("f518f1b01e84dde1e152856375d5e1bc75d7064e0654a10a19155ca575d81b09", "carsettings/com/byd/carsettings/R.java", 9072)),
        evidence("S10", EvidenceLevel.SOURCE_CONSTANT, "Legacy ADAS prompt", 3,
            citation("2c1ec324c822582e2294e9bc13acc0ea4ab2214e6367ec6affc333dd211000ca", "carsettings/com/byd/feature/adas/Adas.java", 1074)),
    )

    val byId: Map<String, FirmwareEvidence> = all.associateBy(FirmwareEvidence::id)

    val edges = listOf(
        EvidenceEdge("E-NAV-AMAP", setOf("H4"), setOf("H4"), "SOURCE_BACKED", "cluster"),
        EvidenceEdge("E-NAV-HUD-GATE", setOf("H4"), setOf("H1"), "CANDIDATE_UNEXECUTED", "HUD"),
        EvidenceEdge("E-SIGN-PROPERTY", setOf("S0"), setOf("S1", "S2", "S3", "S6"), "TRANSPORT_SEMANTICS_UNPROVEN", "none-off-car"),
        EvidenceEdge("E-SIGN-CONSUMER", setOf("S1", "S2", "S3"), setOf("S8"), "NATIVE_CONSUMERS_SOURCE_BACKED_TRANSPORT_LINK_UNPROVEN", "cluster/HUD-unknown"),
        EvidenceEdge("E-PROVIDER-ARBITRATION", setOf("S7"), setOf("S7"), "PACKAGE_CANDIDATE_TARGET_INSTALL_UNKNOWN", "none-off-car"),
    )

    val unavailableCorpus = listOf(
        "C-BYDAUTO-PROVIDER-LIB", "C-ODM-PARTITION", "C-PROPERTY-REGISTRY",
        "C-PROVIDER-APK", "C-QML-RCC", "C-SERVICE-CONTEXT",
        "C-SYSTEM-EXT-PARTITION", "C-VENDOR-BOOT", "C-VENDOR-PARTITION",
    ).map(::CorpusGap)

    val naviInfo = NaviInfoSchema(
        declaredFieldCount = 18,
        fields = listOf(
            "naviState", "nextRouteName", "curToSegmentDist", "forwardState",
            "nextTurnIcon", "routeRemainTime", "routeRemainDist", "stringEtaArrivalTime",
            "exitNameInfo", "exitDirectionInfo", "routrRemainDisAuto", "routrRemainTimeAuto",
            "SegRemainDisAuto", "nextNextTurnIcon", "nextToSegmentDist", "nextNextRouteName",
            "roungAboutNum", "nextRoungAboutNum",
        ),
        startObjectLine = 246,
        hasSpeedLimit = false,
    )

    fun validate(): List<String> = buildList {
        val expected = (0..7).map { "H$it" }.toSet() + (0..10).map { "S$it" }
        if (byId.keys != expected) add("evidence IDs differ from H0-H7/S0-S10")
        all.forEach { row ->
            if (row.citations.isEmpty()) add("${row.id} has no artifact citation")
            if (row.hitCount < row.citations.size) add("${row.id} hit count is inconsistent")
            if (row.executable) add("${row.id} is prematurely executable")
        }
        edges.forEach { edge ->
            (edge.fromEvidenceIds + edge.toEvidenceIds).filterNot(byId::containsKey).forEach {
                add("${edge.id} references unknown evidence $it")
            }
        }
        if (corpusVerdict != CorpusVerdict.NOT_EXHAUSTIVE) add("missing corpus must remain NOT_EXHAUSTIVE")
        if (offCarVisualPass) add("off-car evidence cannot produce visual PASS")
    }
}
