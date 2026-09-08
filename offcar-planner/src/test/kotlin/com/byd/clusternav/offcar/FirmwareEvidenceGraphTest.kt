package com.byd.clusternav.offcar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirmwareEvidenceGraphTest {
    @Test
    fun `generated H and S evidence metadata is complete and exact`() {
        val expected = mapOf(
            "H0" to (EvidenceLevel.CONTROL_OR_GATE to 39),
            "H1" to (EvidenceLevel.CONCRETE_SET_CALL_SITE to 28),
            "H2" to (EvidenceLevel.SERVICE_INTERFACE to 404),
            "H3" to (EvidenceLevel.CONTROL_OR_GATE to 26),
            "H4" to (EvidenceLevel.CONCRETE_SET_CALL_SITE to 84),
            "H5" to (EvidenceLevel.CONCRETE_SET_CALL_SITE to 46),
            "H6" to (EvidenceLevel.SOURCE_CONSTANT to 85),
            "H7" to (EvidenceLevel.SOURCE_CONSTANT to 369),
            "S0" to (EvidenceLevel.RECORDED_FIELD to 523),
            "S1" to (EvidenceLevel.WRITE_INTENT_CONSTANT to 12),
            "S2" to (EvidenceLevel.STATUS_OR_OUTPUT_ONLY to 20),
            "S3" to (EvidenceLevel.STATUS_OR_OUTPUT_ONLY to 9),
            "S4" to (EvidenceLevel.CONTROL_OR_GATE to 35),
            "S5" to (EvidenceLevel.WRITE_INTENT_CONSTANT to 24),
            "S6" to (EvidenceLevel.CONCRETE_SET_CALL_SITE to 49),
            "S7" to (EvidenceLevel.SERVICE_INTERFACE to 34),
            "S8" to (EvidenceLevel.NATIVE_CONSUMER to 25),
            "S9" to (EvidenceLevel.REMINDER_THRESHOLD to 60),
            "S10" to (EvidenceLevel.SOURCE_CONSTANT to 3),
        )

        assertEquals(expected.keys, FirmwareEvidenceCatalog.byId.keys)
        expected.forEach { (id, expectedMetadata) ->
            val row = FirmwareEvidenceCatalog.byId.getValue(id)
            assertEquals(expectedMetadata.first, row.level, id)
            assertEquals(expectedMetadata.second, row.hitCount, id)
            assertTrue(row.citations.isNotEmpty(), id)
            assertFalse(row.executable, id)
        }
        assertEquals(EvidenceState.CITED_NATIVE_CONSUMER, FirmwareEvidenceCatalog.byId.getValue("S8").state)
    }

    @Test
    fun `evidence graph has no dangling or overpromoted claims`() {
        assertTrue(FirmwareEvidenceCatalog.validate().isEmpty(), FirmwareEvidenceCatalog.validate().joinToString())
        assertEquals(5, FirmwareEvidenceCatalog.edges.size)
        assertEquals(9, FirmwareEvidenceCatalog.unavailableCorpus.size)
        assertTrue(FirmwareEvidenceCatalog.unavailableCorpus.all { it.state == EvidenceState.UNAVAILABLE })
        assertEquals(CorpusVerdict.NOT_EXHAUSTIVE, FirmwareEvidenceCatalog.corpusVerdict)
        assertFalse(FirmwareEvidenceCatalog.offCarVisualPass)
    }

    @Test
    fun `taxonomy includes unknown unavailable and field-proof states without claiming them`() {
        val levels = EvidenceLevel.entries.toSet()
        assertTrue(levels.containsAll(setOf(
            EvidenceLevel.UNKNOWN,
            EvidenceLevel.UNAVAILABLE,
            EvidenceLevel.FIELD_PROVEN,
            EvidenceLevel.CLIENT_WRAPPER,
            EvidenceLevel.SOURCE_ARBITRATION,
        )))
        assertTrue(FirmwareEvidenceCatalog.all.none { it.state == EvidenceState.FIELD_PROVEN })
    }

    @Test
    fun `typed catalog is bound to the complete generated evidence index`() {
        val bytes = java.nio.file.Files.readAllBytes(
            testProjectRoot().resolve("docs/diagnostics/hud-sign-re/evidence-index.json"),
        )
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }

        assertEquals(FirmwareEvidenceCatalog.SOURCE_INDEX_SHA256, hash)
        assertEquals("clusternav.re-evidence-graph/v1", FirmwareEvidenceCatalog.SOURCE_INDEX_SCHEMA)
    }
}
