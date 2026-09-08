package com.byd.clusternav.offcar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CandidateSafetyTest {
    private val plans = CandidateScenarioGenerator().generate()

    @Test
    fun `generator emits exact feature and disposition counts`() {
        assertEquals(12, plans.size)
        assertEquals(2, plans.count { it.disposition == PlanDisposition.BLOCKED })
        assertEquals(10, plans.count { it.disposition == PlanDisposition.UNKNOWN })
        assertEquals(
            mapOf(
                CandidateFeature.NAV_HUD to 6,
                CandidateFeature.HUD_ROAD to 1,
                CandidateFeature.CLUSTER_SIGN to 3,
                CandidateFeature.HUD_SIGN to 2,
            ),
            plans.groupingBy { it.candidate.feature }.eachCount(),
        )
    }

    @Test
    fun `all generated plans are structurally safe and inert`() {
        plans.forEach { plan ->
            val issues = PropertyCandidateValidator.validate(plan.candidate)
            assertTrue((issues intersect PropertyCandidateValidator.prohibitedShapeIssues).isEmpty(), "$plan: $issues")
            assertEquals(1, plan.candidate.mutations.size)
            assertTrue(plan.candidate.preconditions.any { it is ReadStep.PriorValue })
            assertTrue(plan.candidate.inverse.any { it is InverseStep.RestorePrior })
            assertFalse(plan.offCarVisualPass)
            assertTrue(plan.disposition in setOf(PlanDisposition.UNKNOWN, PlanDisposition.BLOCKED))
        }
    }

    @Test
    fun `candidate context covers all generated evidence families without unsupported promotion`() {
        val covered = plans.flatMap { it.candidate.evidenceIds }.toSet()
        assertEquals(FirmwareEvidenceCatalog.byId.keys, covered)
        assertTrue(plans.none { it.reason.contains("unsupported", ignoreCase = true) })
    }

    @Test
    fun `fake transport records lifecycle without a mutation executor`() {
        val records = FakeVehicleTransport().capture(plans.first())
        assertTrue(records.any { it.phase == "READ" })
        assertEquals(1, records.count { it.phase == "PROPOSE" })
        assertTrue(records.any { it.phase == "OBSERVE" })
        assertTrue(records.any { it.phase == "INVERSE" })
    }
}
