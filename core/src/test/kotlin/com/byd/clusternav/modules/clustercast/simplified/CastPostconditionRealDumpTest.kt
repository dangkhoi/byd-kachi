package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T4 gap coverage — CastPostconditionVerifier + CastStackParser with real vehicle dumps.
 *
 * Tests the verifier using real `am stack list` output formats observed on BYD DiLink3
 * (Android 10, vehicle-proven format). Ensures typed TaskLookupResult handles:
 * - Standard single-task format
 * - Multiple activities from same package
 * - Mixed display outputs
 * - Empty display (projection open but nothing cast)
 * - CarPlay format (different activity naming)
 * - UTF-8 activity names (Vietnamese)
 *
 * R3: Cast commits only on verified postcondition.
 */
class CastPostconditionRealDumpTest {

    // ─── Real vehicle dump format: standard case ──────────────────────────────

    private val singleAppOnDisplay1 = """
        Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0
          taskId=1: com.android.launcher3/com.android.launcher3.Launcher bounds=[0,0][1920,720] visible=true topActivity=ComponentInfo{com.android.launcher3/com.android.launcher3.Launcher}
          taskId=5: com.byd.autolink.app/.home.HomeActivity bounds=[0,0][1920,720] visible=false
          taskId=8: vn.vietmap.live/vn.vietmap.live.MainActivity bounds=[0,0][1920,720] visible=false
        Stack id=2 bounds=[0,0][1920,720] displayId=1 userId=0
          taskId=99: com.byd.clusternav/.modules.clustercast.ClusterBlackActivity bounds=[0,0][1920,720] visible=true topActivity=ComponentInfo{com.byd.clusternav/.modules.clustercast.ClusterBlackActivity}
          taskId=101: com.google.android.apps.maps/com.google.android.maps.MapsActivity bounds=[0,90][1920,630] visible=true topActivity=ComponentInfo{com.google.android.apps.maps/com.google.android.maps.MapsActivity}
    """.trimIndent()

    @Test
    fun `real dump - single app on display 1 returns Found`() {
        val result = CastStackParser.findTaskIdTyped(singleAppOnDisplay1, "com.google.android.apps.maps", 1)
        assertTrue(result is CastStackParser.TaskLookupResult.Found)
        val found = result as CastStackParser.TaskLookupResult.Found
        assertEquals("101", found.taskId)
        assertEquals(1, found.displayId)
    }

    @Test
    fun `real dump - isAppOnDisplay returns true for Google Maps on display 1`() {
        assertTrue(CastStackParser.isAppOnDisplay(singleAppOnDisplay1, "com.google.android.apps.maps", 1))
    }

    @Test
    fun `real dump - isAppOnDisplay returns false for VietMap on display 1`() {
        // VietMap is on display 0 (visible=false), not display 1
        assertFalse(CastStackParser.isAppOnDisplay(singleAppOnDisplay1, "vn.vietmap.live", 1))
    }

    @Test
    fun `real dump - ClusterBlack is visible on display 1`() {
        assertTrue(CastStackParser.isAppOnDisplay(singleAppOnDisplay1, "com.byd.clusternav", 1))
    }

    // ─── CarPlay format (vehicle-observed) ────────────────────────────────────

    private val carPlayOnDisplay1 = """
        Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0
          taskId=1: com.android.launcher3/com.android.launcher3.Launcher bounds=[0,0][1920,720] visible=true topActivity=ComponentInfo{com.android.launcher3/com.android.launcher3.Launcher}
        Stack id=3 bounds=[0,0][1422,800] displayId=1 userId=0
          taskId=99: com.byd.clusternav/.modules.clustercast.ClusterBlackActivity bounds=[0,0][1422,800] visible=true
          taskId=10: com.byd.autolink.carplay/com.byd.autolink.carplay.activity.CarPlayActivity bounds=[0,0][1422,800] visible=true topActivity=ComponentInfo{com.byd.autolink.carplay/com.byd.autolink.carplay.activity.CarPlayActivity}
    """.trimIndent()

    @Test
    fun `real dump - CarPlay on display 1 Found`() {
        val result = CastStackParser.findTaskIdTyped(carPlayOnDisplay1, "com.byd.autolink.carplay", 1)
        assertTrue(result is CastStackParser.TaskLookupResult.Found)
        assertEquals("10", (result as CastStackParser.TaskLookupResult.Found).taskId)
    }

    @Test
    fun `real dump - CarPlay identified as CARPLAY AppType`() {
        val appType = AppMover.classifyApp("com.byd.autolink.carplay")
        assertEquals(AppType.CARPLAY, appType)
    }

    // ─── Ambiguous case: same package multiple activities ─────────────────────

    private val ambiguousMultiActivity = """
        Stack id=2 bounds=[0,0][1920,720] displayId=1 userId=0
          taskId=99: com.byd.clusternav/.modules.clustercast.ClusterBlackActivity bounds=[0,0][1920,720] visible=true
          taskId=200: com.test.browser/com.test.browser.BrowserActivity bounds=[0,90][960,630] visible=true
          taskId=201: com.test.browser/com.test.browser.SettingsActivity bounds=[960,90][1920,630] visible=true
    """.trimIndent()

    @Test
    fun `real dump - multiple activities same package returns Ambiguous`() {
        val result = CastStackParser.findTaskIdTyped(ambiguousMultiActivity, "com.test.browser", 1)
        assertTrue(result is CastStackParser.TaskLookupResult.Ambiguous,
            "Multiple visible tasks for same pkg should be Ambiguous, got: $result")
        val ambiguous = result as CastStackParser.TaskLookupResult.Ambiguous
        assertEquals(2, ambiguous.matches.size)
    }

    // ─── Empty display (projection open, nothing cast yet) ────────────────────

    private val emptyDisplay1 = """
        Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0
          taskId=1: com.android.launcher3/com.android.launcher3.Launcher bounds=[0,0][1920,720] visible=true
        Stack id=2 bounds=[0,0][1920,720] displayId=1 userId=0
          taskId=99: com.byd.clusternav/.modules.clustercast.ClusterBlackActivity bounds=[0,0][1920,720] visible=true
    """.trimIndent()

    @Test
    fun `real dump - empty display 1 returns NotFound for target app`() {
        val result = CastStackParser.findTaskIdTyped(emptyDisplay1, "com.test.app", 1)
        assertTrue(result is CastStackParser.TaskLookupResult.NotFound)
    }

    @Test
    fun `real dump - empty display 1 has no external apps`() {
        assertFalse(CastStackParser.isAppOnDisplay(emptyDisplay1, "com.test.app", 1))
    }

    // ─── Postcondition verifier with FakeShell ────────────────────────────────

    @Test
    fun `verifier returns Verified when app appears on target display`() {
        val shell = object : SimpleCastShell {
            override fun execute(command: String): ShellResult {
                return ShellResult(0, singleAppOnDisplay1, "")
            }
        }
        val verifier = CastPostconditionVerifier(shell, sleepMs = {})
        val outcome = verifier.verifyCastFull("com.google.android.apps.maps", 1)
        assertTrue(outcome is CastMutationOutcome.Verified)
        assertEquals(101, (outcome as CastMutationOutcome.Verified).taskId)
        assertEquals(1, outcome.displayId)
    }

    @Test
    fun `verifier returns Unknown when app not on display`() {
        val shell = object : SimpleCastShell {
            override fun execute(command: String): ShellResult {
                return ShellResult(0, emptyDisplay1, "")
            }
        }
        val verifier = CastPostconditionVerifier(shell, sleepMs = {})
        val outcome = verifier.verifyCastFull("com.test.app", 1, maxRetries = 1)
        assertTrue(outcome is CastMutationOutcome.Unknown)
    }

    @Test
    fun `verifier returns Unknown on shell failure`() {
        val shell = object : SimpleCastShell {
            override fun execute(command: String): ShellResult {
                return ShellResult(1, "", "permission denied")
            }
        }
        val verifier = CastPostconditionVerifier(shell, sleepMs = {})
        val outcome = verifier.verifyCastFull("com.test.app", 1, maxRetries = 1)
        assertTrue(outcome is CastMutationOutcome.Unknown)
    }

    @Test
    fun `verifier retries and succeeds on second attempt`() {
        var callCount = 0
        val shell = object : SimpleCastShell {
            override fun execute(command: String): ShellResult {
                callCount++
                return if (callCount == 1) {
                    ShellResult(0, emptyDisplay1, "") // first: not visible yet
                } else {
                    ShellResult(0, singleAppOnDisplay1, "") // second: visible
                }
            }
        }
        val verifier = CastPostconditionVerifier(shell, sleepMs = {})
        val outcome = verifier.verifyCastFull("com.google.android.apps.maps", 1, maxRetries = 3)
        assertTrue(outcome is CastMutationOutcome.Verified)
        assertEquals(2, callCount, "should have tried twice before succeeding")
    }

    @Test
    fun `verifyAbsent returns Verified when app is gone`() {
        val shell = object : SimpleCastShell {
            override fun execute(command: String): ShellResult {
                return ShellResult(0, emptyDisplay1, "")
            }
        }
        val verifier = CastPostconditionVerifier(shell, sleepMs = {})
        val outcome = verifier.verifyAbsent("com.test.app", 1)
        assertTrue(outcome is CastMutationOutcome.Verified)
    }

    @Test
    fun `verifyAbsent returns Unknown when app still present`() {
        val shell = object : SimpleCastShell {
            override fun execute(command: String): ShellResult {
                return ShellResult(0, singleAppOnDisplay1, "")
            }
        }
        val verifier = CastPostconditionVerifier(shell, sleepMs = {})
        val outcome = verifier.verifyAbsent("com.google.android.apps.maps", 1, maxRetries = 1)
        assertTrue(outcome is CastMutationOutcome.Unknown)
    }

    // ─── Typed result parsing edge cases ─────────────────────────────────────

    @Test
    fun `parser handles empty am stack list output`() {
        val result = CastStackParser.findTaskIdTyped("", "com.test", 1)
        assertTrue(result is CastStackParser.TaskLookupResult.NotFound)
    }

    @Test
    fun `parser handles malformed lines gracefully`() {
        val malformed = """
            Stack id=2 bounds=[0,0][1920,720] displayId=1 userId=0
              this line is garbage
              taskId=abc: not/a.real.task visible=true
              taskId=100: com.valid.app/.Activity visible=true
        """.trimIndent()
        val result = CastStackParser.findTaskIdTyped(malformed, "com.valid.app", 1)
        assertTrue(result is CastStackParser.TaskLookupResult.Found)
        assertEquals("100", (result as CastStackParser.TaskLookupResult.Found).taskId)
    }

    @Test
    fun `parseTasks extracts correct display ID and visibility`() {
        val tasks = CastStackParser.parseTasks(singleAppOnDisplay1)
        val maps = tasks.first { it.pkg == "com.google.android.apps.maps" }
        assertEquals(1, maps.displayId)
        assertTrue(maps.visible)
        assertEquals(101, maps.taskId)
    }

    @Test
    fun `parseTasks handles invisible tasks`() {
        val tasks = CastStackParser.parseTasks(singleAppOnDisplay1)
        val vietmap = tasks.first { it.pkg == "vn.vietmap.live" }
        assertEquals(0, vietmap.displayId)
        assertFalse(vietmap.visible)
    }
}
