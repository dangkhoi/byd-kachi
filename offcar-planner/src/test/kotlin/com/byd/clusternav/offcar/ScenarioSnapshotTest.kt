package com.byd.clusternav.offcar

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ScenarioSnapshotTest {
    @TempDir
    lateinit var temp: Path

    private val expectedHashes = mapOf(
        "candidate-report.html" to "af3c3db29e29b5c4cd4ebd0a0d4ef863de5a7a2d7f60c527bbe989596ce3eb36",
        "traceability.json" to "332ae311ed642441c7ec8640fc4c389e1e7865813eafef8b1442645ff3791e60",
        "m1-nav-hud-plan.json" to "c8eff7210e093fa1b2e32328abeddf37fa51d919d524df851e4ecd634d289bf9",
        "m2-hud-road-plan.json" to "dffb04d3e26beadd9fd7f813870fca6712d5f213f4145787280ddb4718437faf",
        "m3-cluster-sign-plan.json" to "ac2de2631de73ab2ddd2ad4efa2f33e805fae2d8007091e2ae3497c64625fb7e",
        "m4-hud-sign-plan.json" to "51fc71db1050baa816e9df2e1250a196c1d6e608a48df228e5318d6077c910d1",
    )

    @Test
    fun `generated files match deterministic inert snapshots`() {
        val first = temp.resolve("first")
        val second = temp.resolve("second")
        val firstResult = OffCarPlannerMain.writeReports(first)
        val secondResult = OffCarPlannerMain.writeReports(second)

        assertEquals(12, firstResult.candidateCount)
        assertEquals(expectedHashes.keys, firstResult.files.toSet())
        expectedHashes.forEach { (name, expectedHash) ->
            val firstBytes = Files.readAllBytes(first.resolve(name))
            val secondBytes = Files.readAllBytes(second.resolve(name))
            assertTrue(firstBytes.contentEquals(secondBytes), name)
            assertEquals(expectedHash, sha256(firstBytes), name)
            val text = firstBytes.decodeToString()
            assertFalse(text.contains("\"visualPass\": true"), name)
            assertFalse(text.contains("\"offCarVisualPass\": true"), name)
        }
    }

    @Test
    fun `plain text rendering is deterministic and explicitly inert`() {
        val renderer = CommandPlanRenderer()
        val plans = CandidateScenarioGenerator().generate()
        val first = renderer.renderText(plans)
        val second = renderer.renderText(plans)

        assertEquals(first, second)
        assertTrue(first.startsWith("CLUSTERNAV OFF-CAR INERT PLAN DATA"))
        assertTrue(first.contains("candidateCount=12"))
        assertTrue(first.contains("visualPass=false"))
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
