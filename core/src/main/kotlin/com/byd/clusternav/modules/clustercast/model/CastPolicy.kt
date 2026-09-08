package com.byd.clusternav.modules.clustercast.model

data class TargetEvidence(
    val projectionComponent: Boolean?,
    val connectedPhoneSession: Boolean?,
    val userProtected: Boolean,
)

object CastPolicy {
    /**
     * A projection-hinted package (CarPlay, Android Auto — matched by name the same way V1's
     * `PROJECTION_HINTS` in `ClusterCast.kt` did) is [TargetClass.PROJECTION_SINK] unless
     * `connectedPhoneSession` was *positively* read as `false`. An unmeasurable reading (`null`) no
     * longer falls through to [TargetClass.UNKNOWN_PROTECTED] the way an explicit `false` still does.
     *
     * 2026-07-28, on the vehicle: casting Apple CarPlay or Android Auto was permanently blocked.
     * `connectedPhoneSession` comes from `dumpsys activity services <pkg>`, whose parser looks for
     * fields like `connectedPhoneSession=`/`mPhoneConnected=` — which describe a THIRD-PARTY app that
     * is *currently being mirrored*, not the CarPlay/Android Auto host surface's own record of
     * itself. That query can never match anything for the host package, so it always reads `null` —
     * not `false` — and the old code (`projectionComponent && connectedPhoneSession == true`, else
     * `projectionComponent -> UNKNOWN_PROTECTED`) sent every `null` reading down the same path as a
     * confirmed `false`. `UNKNOWN_PROTECTED` targets are refused as a cast target outright by
     * `CastManualTargetSnapshot.eligibilityFor`, not merely protected from force-stop — so the two
     * flagship reasons this app exists could never be cast, in any vehicle state.
     *
     * A confirmed `false` is a stronger, different signal than "the check does not apply to this
     * package" and keeps failing closed to `UNKNOWN_PROTECTED` unchanged — see
     * `CastAndroidAutoSliceTest.projection evidence without connected truth fails closed`, which this
     * must keep passing exactly as written.
     *
     * 2026-07-28, branch-order fix: `evidence.projectionComponent -> UNKNOWN_PROTECTED` must be
     * checked *before* `evidence.userProtected -> KEEP_SESSION`, not after. By the time either branch
     * is reached, `projectionComponent == true` can only mean `connectedPhoneSession` was positively
     * confirmed `false` (the `null`/`true` cases were already routed to `PROJECTION_SINK` above), so
     * this is a confirmed-absent-session projection host — it must fail closed regardless of whether
     * the user separately marked it protected. Checking `userProtected` first let that positively
     * confirmed `false` reading fall into `KEEP_SESSION` instead, a wrong classification (though it
     * does not weaken force-stop safety, since neither class permits force-stop). See
     * `CastPolicyTest.a confirmed-absent projection session fails closed even when user-protected`.
     */
    fun classify(evidence: TargetEvidence): TargetClass = when {
        evidence.projectionComponent == null -> TargetClass.UNKNOWN_PROTECTED
        evidence.projectionComponent && evidence.connectedPhoneSession != false -> TargetClass.PROJECTION_SINK
        evidence.projectionComponent -> TargetClass.UNKNOWN_PROTECTED
        evidence.userProtected -> TargetClass.KEEP_SESSION
        else -> TargetClass.NORMAL
    }

    fun mayForceStop(targetClass: TargetClass): Boolean = targetClass == TargetClass.NORMAL
    fun preservesSession(targetClass: TargetClass): Boolean = targetClass != TargetClass.NORMAL
}
