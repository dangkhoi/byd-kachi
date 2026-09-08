package com.byd.clusternav.modules.clustercast.model

data class CastManualTargetSnapshot(
    val evidence: TargetEvidence,
    val installed: Boolean,
    val hasLauncher: Boolean,
)
