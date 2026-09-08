package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.modules.clustercast.ClusterNavLaneWidget.Decision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Decision + timing gates for the OEM "simple navigation" cluster widget (op 39).
 *
 * Contract (two-track): assert op 39 whenever nav is active AND Cast is OFF (nav-only mode). op 39
 * "simple navigation" (Giữa + ETA) is the ONLY nav-on-cluster mode (owner 2026-08-12) — the old
 * "small/top" display-mode gate is gone. When Cast is ON the Cast track owns the cluster and the
 * widget must not fight it.
 *
 * Timing (docs/specs/nav-cluster-op39-selfdiagnose.html · D1): a SUCCESS holds for [REASSERT_MS];
 * a not-yet-successful state retries but no faster than [RETRY_AFTER_FAIL_MS] so a recovered dadb
 * loopback re-asserts on the next nav frame instead of being blocked for the full 30s.
 */
class ClusterNavLaneWidgetTest {

    // ── back-compat predicate (unchanged callers) ────────────────────────────────────────────────

    @Test fun `asserts in nav-only mode (nav active, cast off)`() {
        assertTrue(ClusterNavLaneWidget.shouldAssert(navActive = true, castEnabled = false))
    }

    @Test fun `does not assert while casting is enabled`() {
        assertFalse(ClusterNavLaneWidget.shouldAssert(navActive = true, castEnabled = true))
    }

    @Test fun `does not assert when nav is idle`() {
        assertFalse(ClusterNavLaneWidget.shouldAssert(navActive = false, castEnabled = false))
        assertFalse(ClusterNavLaneWidget.shouldAssert(navActive = false, castEnabled = true))
    }

    @Test fun `opcode is 39 simple navigation`() {
        assertEquals(39, ClusterNavLaneWidget.OP_SIMPLE_NAV)
    }

    // ── decide(): split reasons for UI/log (D2) ──────────────────────────────────────────────────

    @Test fun `decide asserts in nav-only mode`() {
        assertEquals(Decision.ASSERT, ClusterNavLaneWidget.decide(navActive = true, castEnabled = false))
    }

    @Test fun `decide gates on cast — Cast owns the cluster`() {
        assertEquals(Decision.GATED_CAST, ClusterNavLaneWidget.decide(navActive = true, castEnabled = true))
    }

    @Test fun `decide skips when nav idle regardless of cast`() {
        assertEquals(Decision.SKIP_IDLE, ClusterNavLaneWidget.decide(navActive = false, castEnabled = false))
        assertEquals(Decision.SKIP_IDLE, ClusterNavLaneWidget.decide(navActive = false, castEnabled = true))
    }

    @Test fun `shouldAssert agrees with decide == ASSERT`() {
        for (nav in listOf(true, false)) for (cast in listOf(true, false)) {
            val expected = ClusterNavLaneWidget.decide(nav, cast) == Decision.ASSERT
            assertEquals(expected, ClusterNavLaneWidget.shouldAssert(nav, cast), "nav=$nav cast=$cast")
        }
    }

    // ── shouldIssueNow(): success-hold + failure-retry (D1) ──────────────────────────────────────

    @Test fun `fresh state issues immediately`() {
        // No prior success, no prior attempt → issue now.
        assertTrue(ClusterNavLaneWidget.shouldIssueNow(nowMs = 1_000L, lastOkAtMs = 0L, lastAttemptAtMs = 0L))
    }

    @Test fun `holds within reassert window after a success`() {
        val ok = 1_000L
        val now = ok + ClusterNavLaneWidget.REASSERT_MS - 1L
        assertFalse(ClusterNavLaneWidget.shouldIssueNow(nowMs = now, lastOkAtMs = ok, lastAttemptAtMs = ok))
    }

    @Test fun `re-asserts once the reassert window elapses after a success`() {
        val ok = 1_000L
        val now = ok + ClusterNavLaneWidget.REASSERT_MS
        assertTrue(ClusterNavLaneWidget.shouldIssueNow(nowMs = now, lastOkAtMs = ok, lastAttemptAtMs = ok))
    }

    @Test fun `after a failed attempt, backs off briefly (does not block for the full reassert window)`() {
        // Failure leaves lastOkAtMs untouched (0). Within the short backoff → wait.
        val attempt = 1_000L
        val soon = attempt + ClusterNavLaneWidget.RETRY_AFTER_FAIL_MS - 1L
        assertFalse(ClusterNavLaneWidget.shouldIssueNow(nowMs = soon, lastOkAtMs = 0L, lastAttemptAtMs = attempt))
        // …then retries well before REASSERT_MS would have elapsed — this is the fixed bug.
        val retry = attempt + ClusterNavLaneWidget.RETRY_AFTER_FAIL_MS
        assertTrue(ClusterNavLaneWidget.shouldIssueNow(nowMs = retry, lastOkAtMs = 0L, lastAttemptAtMs = attempt))
        assertTrue(retry - attempt < ClusterNavLaneWidget.REASSERT_MS, "retry must be far quicker than the success hold")
    }
}
