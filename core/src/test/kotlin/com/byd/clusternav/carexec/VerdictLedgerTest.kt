package com.byd.clusternav.carexec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VerdictLedgerTest {

    private fun entry(step: String, candidate: String, verdict: Verdict, at: String = "2026-07-27T13:00:00Z") =
        VerdictEntry(at, CarFeature.CLUSTER_CAST, step, candidate, verdict, VerdictSource.MEASURED, "car:5555", "")

    @Test
    fun `dong moi nhat quyet dinh trang thai hien tai`() {
        val ledger = VerdictLedger(
            listOf(
                entry("place", "place.freeform-only", Verdict.FAIL, "2026-07-27T10:00:00Z"),
                entry("place", "place.freeform-only", Verdict.OK, "2026-07-27T11:00:00Z"),
            ),
        )
        assertEquals(Verdict.OK, ledger.verdictFor("place.freeform-only"))
    }

    @Test
    fun `step OK khi co it nhat mot candidate OK`() {
        val ledger = VerdictLedger(
            listOf(
                entry("place", "place.freeform-then-resize", Verdict.FAIL),
                entry("place", "place.freeform-only", Verdict.OK),
            ),
        )
        assertEquals("place.freeform-only", ledger.okCandidateFor("place"))
    }

    @Test
    fun `e2e chi gom step da OK va noi ten step con thieu`() {
        val ledger = VerdictLedger(
            listOf(
                entry("observe", "observe.dumpsys", Verdict.OK),
                entry("place", "place.freeform-only", Verdict.OK),
            ),
        )
        val plan = ledger.e2eChain(CarFeature.CLUSTER_CAST)
        assertEquals(listOf("observe" to "observe.dumpsys", "place" to "place.freeform-only"), plan.ready)
        assertTrue(plan.blocked.containsAll(listOf("open-projection", "teardown", "restore")))
        assertFalse(plan.runnable, "chuỗi còn step chưa chứng minh thì không được coi là chạy được")
    }

    @Test
    fun `ghi va doc lai mot dong khong mat thong tin`() {
        val original = VerdictEntry(
            "2026-07-27T13:00:00Z", CarFeature.CLUSTER_CAST, "teardown", "teardown.18-then-0",
            Verdict.OK, VerdictSource.HUMAN, "192.0.2.10:5555", "owner xác nhận về đồng hồ",
        )
        assertEquals(original, VerdictEntry.fromRow(original.toRow()))
    }

    @Test
    fun `tab va newline trong ghi chu khong pha vo dinh dang`() {
        val messy = VerdictEntry(
            "2026-07-27T13:00:00Z", CarFeature.CLUSTER_CAST, "place", "place.movestack",
            Verdict.FAIL, VerdictSource.MEASURED, "car:5555", "dòng\tmột\ndòng hai",
        )
        assertEquals(8, messy.toRow().split('\t').size)
    }
}
