package com.byd.clusternav.modules.clustercast.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Locks the exact three-valued [TargetEvidence.connectedPhoneSession] contract.
 *
 * Measured on the vehicle 2026-07-28: tapping "CHIẾU Apple CarPlay LÊN CỤM" and "CHIẾU Android Auto
 * LÊN CỤM" did nothing but show a toast — `executeCast()` was never even invoked, because
 * `CastManualTargetSnapshot.eligibilityFor` blocks any `TargetClass.UNKNOWN_PROTECTED` target before
 * a cast attempt ever starts. `ProjectionSessionEvidenceParser` reads `dumpsys activity services
 * <pkg>` looking for fields (`connectedPhoneSession=`, `mPhoneConnected=`, `sessionState=`) that
 * describe a THIRD-PARTY app currently being phone-mirrored — never present in the CarPlay/Android
 * Auto host surface's own dump — so it always returns `null` for those two packages, never `true` or
 * `false`. The evidence-gathering bug (parser can't see the host's own session) is separate from and
 * upstream of this policy bug (the policy treated "couldn't measure" the same as "confirmed absent").
 */
class CastPolicyTest {

    @Test
    fun `a projection-hinted package with unmeasurable session evidence is a castable projection sink`() {
        // This is the exact real-world shape for the CarPlay/Android Auto host package: name-matched
        // as a projection component, but dumpsys never reports the host's own session state.
        val evidence = TargetEvidence(projectionComponent = true, connectedPhoneSession = null, userProtected = false)
        assertEquals(TargetClass.PROJECTION_SINK, CastPolicy.classify(evidence))
    }

    @Test
    fun `a projection-hinted package with confirmed connection is a castable projection sink`() {
        val evidence = TargetEvidence(projectionComponent = true, connectedPhoneSession = true, userProtected = false)
        assertEquals(TargetClass.PROJECTION_SINK, CastPolicy.classify(evidence))
    }

    /**
     * A positively-confirmed `false` reading is a stronger, different signal than "unmeasurable" and
     * must keep failing closed — this is the pre-existing, deliberate behavior asserted by
     * `CastAndroidAutoSliceTest.projection evidence without connected truth fails closed`, repeated
     * here at the policy level so a future edit cannot silently widen it back to unconditional.
     */
    @Test
    fun `a projection-hinted package with a confirmed absent session still fails closed`() {
        val evidence = TargetEvidence(projectionComponent = true, connectedPhoneSession = false, userProtected = false)
        assertEquals(TargetClass.UNKNOWN_PROTECTED, CastPolicy.classify(evidence))
    }

    /**
     * Branch-order regression: a projection-hinted package with a positively confirmed absent
     * session must fail closed to `UNKNOWN_PROTECTED` even when the user separately marked it
     * "protected" — `userProtected` must not be checked before this confirmed-`false` case. Before
     * the 2026-07-28 branch-order fix, checking `userProtected` first sent this exact evidence shape
     * to `KEEP_SESSION` instead.
     */
    @Test
    fun `a confirmed-absent projection session fails closed even when user-protected`() {
        val evidence = TargetEvidence(projectionComponent = true, connectedPhoneSession = false, userProtected = true)
        assertEquals(TargetClass.UNKNOWN_PROTECTED, CastPolicy.classify(evidence))
    }

    @Test
    fun `unknown projection identity always fails closed regardless of session evidence`() {
        listOf(true, false, null).forEach { session ->
            val evidence = TargetEvidence(projectionComponent = null, connectedPhoneSession = session, userProtected = false)
            assertEquals(TargetClass.UNKNOWN_PROTECTED, CastPolicy.classify(evidence), "session=$session")
        }
    }

    @Test
    fun `a non-projection package the user marked protected keeps its session instead of normal`() {
        val evidence = TargetEvidence(projectionComponent = false, connectedPhoneSession = null, userProtected = true)
        assertEquals(TargetClass.KEEP_SESSION, CastPolicy.classify(evidence))
    }

    @Test
    fun `an ordinary app with no projection hint and no user preference is normal`() {
        val evidence = TargetEvidence(projectionComponent = false, connectedPhoneSession = null, userProtected = false)
        assertEquals(TargetClass.NORMAL, CastPolicy.classify(evidence))
    }

    @Test
    fun `only normal targets may be force stopped, and every other class preserves its session`() {
        TargetClass.entries.forEach { targetClass ->
            assertEquals(targetClass == TargetClass.NORMAL, CastPolicy.mayForceStop(targetClass))
            assertEquals(targetClass != TargetClass.NORMAL, CastPolicy.preservesSession(targetClass))
        }
    }
}
