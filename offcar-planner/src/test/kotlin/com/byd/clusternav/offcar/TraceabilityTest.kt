package com.byd.clusternav.offcar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TraceabilityTest {
    @Test
    fun `all canonical requirement task gate and future IDs are referenced bidirectionally`() {
        assertTrue(TraceabilityCatalog.validate().isEmpty(), TraceabilityCatalog.validate().joinToString())
        assertEquals((1..32).map { "R$it" }.toSet(), TraceabilityCatalog.requirements)
        assertEquals((0..11).map { "T$it" }.toSet(), TraceabilityCatalog.tasks)
        assertEquals((1..27).map { "O$it" }.toSet(), TraceabilityCatalog.gates)
        TraceabilityCatalog.links.forEach { link ->
            assertTrue(link.tasks.all(TraceabilityCatalog.tasks::contains))
            assertTrue(link.gates.all(TraceabilityCatalog.gates::contains))
            assertTrue(link.futureIds.all(TraceabilityCatalog.futureIds::contains))
            assertFalse(link.artifact.equals("N/A", ignoreCase = true))
            assertTrue(link.artifact.split(" + ").all { it.contains('/') }, link.artifact)
        }
        val statuses = TraceabilityCatalog.links.associate { it.requirement to it.status }
        assertEquals(RequirementVerificationStatus.DEFERRED_T10_T11, statuses.getValue("R10"))
        assertEquals(RequirementVerificationStatus.DEFERRED_T10_T11, statuses.getValue("R11"))
        assertEquals(RequirementVerificationStatus.BLOCKED_BY_EXPLICIT_NO_ADB_INSTALL, statuses.getValue("R24"))
        assertEquals(RequirementVerificationStatus.DEFERRED_T10_T11, statuses.getValue("R28"))
        assertEquals(RequirementVerificationStatus.NOT_EXHAUSTIVE, statuses.getValue("R32"))
        assertTrue(statuses.filterKeys { it !in setOf("R10", "R11", "R24", "R28", "R32") }
            .values.all { it == RequirementVerificationStatus.VERIFIED_OFF_CAR })
    }

    @Test
    fun `milestone packs have separate canonical D and P sections and no visual pass`() {
        val renderer = CommandPlanRenderer()
        val plans = CandidateScenarioGenerator().generate()
        val definitions = listOf(
            arrayOf("M1", "D-M1-NAV-HUD", "P-M1-NAV-HUD", CandidateFeature.NAV_HUD.name),
            arrayOf("M2", "D-M2-HUD-ROAD", "P-M2-HUD-ROAD", CandidateFeature.HUD_ROAD.name),
            arrayOf("M3", "D-M3-CLUSTER-SIGN", "P-M3-CLUSTER-SIGN", CandidateFeature.CLUSTER_SIGN.name),
            arrayOf("M4", "D-M4-HUD-SIGN", "P-M4-HUD-SIGN", CandidateFeature.HUD_SIGN.name),
        )
        definitions.forEach { definition ->
            val feature = CandidateFeature.valueOf(definition[3])
            val rendered = renderer.renderMilestonePack(
                definition[0], feature, definition[1], definition[2], plans,
            )
            assertTrue(rendered.contains("\"${definition[1]}\": {"))
            assertTrue(rendered.contains("\"${definition[2]}\": {"))
            assertFalse(rendered.contains("\"visualPass\": true"))
            assertTrue(rendered.contains("\"capability\": \"UNKNOWN\""))
        }
    }

    @Test
    fun `traceability JSON contains explicit reverse maps without aliases`() {
        val rendered = CommandPlanRenderer().renderTraceability()
        assertTrue(rendered.contains("\"tasksToRequirements\""))
        assertTrue(rendered.contains("\"gatesToRequirements\""))
        assertTrue(rendered.contains("\"futureToRequirements\""))
        assertFalse(Regex("\"(all|N/A|T\\d+-T\\d+|O\\d+-O\\d+)\"", RegexOption.IGNORE_CASE)
            .containsMatchIn(rendered))
    }
}
