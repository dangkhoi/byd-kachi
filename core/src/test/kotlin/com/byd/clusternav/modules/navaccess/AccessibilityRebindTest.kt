package com.byd.clusternav.modules.navaccess

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * DEVICE-FREE unit test for the accessibility FORCE-REBIND decision/string logic ([AccessibilityRebind]).
 *
 * The bug (docs/diagnostics/oncar-handoff-voicekey-2026-08-14.md §8): after a reboot the service is ENABLED
 * in enabled_accessibility_services but NOT BOUND (absent from `dumpsys accessibility` "Bound services"), so
 * onKeyEvent (mic-hold voice key) and the screen-read booster are dead. The proven live fix toggles the
 * service OUT then IN to force a rebind. These are the parts that can be decided without a device; the dadb
 * I/O, pause and never-leave-removed recovery are locked by an app-side wiring test on `NavConnect`.
 */
class AccessibilityRebindTest {

    private val acc = AccessibilityRebind.ACC_COMP
    // The two OEM accessibility services that MUST never be clobbered (from §8's proven live fix).
    private val vr = "com.byd.vrassistant.xf/com.iflytek.autofly.access.service.AccessibilityServices"
    private val sysui = "com.android.systemui/com.android.systemui.custom.StatusBarAccessibilityService"

    /** The value inside the quotes of a `settings put secure <key> "VALUE"` command. */
    private fun quotedValue(cmd: String): String {
        val first = cmd.indexOf('"')
        val last = cmd.lastIndexOf('"')
        require(first in 0 until last) { "no quoted value in: $cmd" }
        return cmd.substring(first + 1, last)
    }

    // ── accessibilityRebindWrites ────────────────────────────────────────────
    @Test
    fun `already bound produces no writes (no flicker)`() {
        assertTrue(AccessibilityRebind.accessibilityRebindWrites("$vr:$sysui:$acc", true).isEmpty())
        assertTrue(AccessibilityRebind.accessibilityRebindWrites(null, true).isEmpty())
    }

    @Test
    fun `enabled but not bound toggles remove then re-add then accessibility_enabled`() {
        val w = AccessibilityRebind.accessibilityRebindWrites("$vr:$sysui:$acc", false)
        assertEquals(3, w.size)
        // [0] remove ClusterNav — OEM services preserved, clusternav gone
        val removed = quotedValue(w[0])
        assertTrue(w[0].startsWith("settings put secure enabled_accessibility_services"), "remove writes the enabled list")
        assertTrue(removed.contains(vr) && removed.contains(sysui), "OEM services preserved on remove")
        assertFalse(removed.contains(acc), "clusternav stripped on remove")
        // [1] re-add ClusterNav — appended LAST, OEM preserved
        val readd = quotedValue(w[1])
        assertTrue(w[1].startsWith("settings put secure enabled_accessibility_services"), "re-add writes the enabled list")
        assertTrue(readd.endsWith(acc), "clusternav re-appended last")
        assertEquals("$vr:$sysui:$acc", readd)
        // [2] re-enable accessibility
        assertEquals("settings put secure accessibility_enabled 1", w[2])
    }

    @Test
    fun `oem services are never clobbered and keep their relative order`() {
        // clusternav in the MIDDLE — the two OEM services must survive with their original strings + order.
        val w = AccessibilityRebind.accessibilityRebindWrites("$vr:$acc:$sysui", false)
        assertEquals("$vr:$sysui", quotedValue(w[0]))
        assertEquals("$vr:$sysui:$acc", quotedValue(w[1]))
    }

    @Test
    fun `empty or null or literal-null current handled`() {
        for (cur in listOf(null, "", "null", "   ")) {
            val w = AccessibilityRebind.accessibilityRebindWrites(cur, false)
            assertEquals(3, w.size, "cur=$cur")
            assertEquals("", quotedValue(w[0]), "remove is an empty quoted value for cur=$cur")
            assertEquals(acc, quotedValue(w[1]), "re-add is just clusternav for cur=$cur")
            assertEquals("settings put secure accessibility_enabled 1", w[2], "cur=$cur")
        }
    }

    @Test
    fun `no dangling leading trailing or double colons`() {
        val messy = ":$vr::$acc:$sysui:"
        val w = AccessibilityRebind.accessibilityRebindWrites(messy, false)
        for (v in listOf(quotedValue(w[0]), quotedValue(w[1]))) {
            assertFalse(v.contains("::"), "no double colon in [$v]")
            assertFalse(v.startsWith(":"), "no leading colon in [$v]")
            assertFalse(v.endsWith(":"), "no trailing colon in [$v]")
        }
        assertEquals("$vr:$sysui", quotedValue(w[0]))
        assertEquals("$vr:$sysui:$acc", quotedValue(w[1]))
    }

    @Test
    fun `duplicate clusternav entries collapse to one on re-add`() {
        val w = AccessibilityRebind.accessibilityRebindWrites("$acc:$sysui:$acc", false)
        assertEquals(sysui, quotedValue(w[0]))
        assertEquals("$sysui:$acc", quotedValue(w[1]))
    }

    @Test
    fun `only clusternav enabled removes to empty then re-adds just clusternav`() {
        // Edge: ClusterNav is the ONLY enabled service. The remove phase writes an EMPTY quoted value
        // (settings put ... "") and the re-add restores just ClusterNav. This is the case that exercises the
        // empty-string write on-device, so lock it explicitly.
        val w = AccessibilityRebind.accessibilityRebindWrites(acc, false)
        assertEquals(3, w.size)
        assertEquals("", quotedValue(w[0]), "remove is an empty quoted value when clusternav was the only entry")
        assertEquals("settings put secure enabled_accessibility_services \"\"", w[0], "remove is a well-formed empty write")
        assertEquals(acc, quotedValue(w[1]), "re-add is just clusternav")
        assertEquals("settings put secure accessibility_enabled 1", w[2])
    }

    @Test
    fun `produced commands match the §8 proven live fix exactly (boundary lock)`() {
        // BOUNDARY: the OEM accessibility manager expects component format `pkg/cls` with a `:` separator, and
        // the proven live fix (docs/diagnostics/oncar-handoff-voicekey-2026-08-14.md §8) issued exactly these
        // three quoted `settings put secure ...` commands with OTHERS = vr:sysui. Lock the full command
        // strings so any drift in key name, quoting, separator or ordering is caught off-car.
        val others = "$vr:$sysui"
        val w = AccessibilityRebind.accessibilityRebindWrites("$others:$acc", false)
        assertEquals(
            listOf(
                "settings put secure enabled_accessibility_services \"$others\"",
                "settings put secure enabled_accessibility_services \"$others:$acc\"",
                "settings put secure accessibility_enabled 1",
            ),
            w,
        )
    }

    // ── isClusterNavBound ────────────────────────────────────────────────────
    @Test
    fun `null or blank dump fails safe as bound (no toggle)`() {
        assertTrue(AccessibilityRebind.isClusterNavBound(null))
        assertTrue(AccessibilityRebind.isClusterNavBound(""))
        assertTrue(AccessibilityRebind.isClusterNavBound("   "))
    }

    @Test
    fun `missing Bound services header fails safe as bound`() {
        assertTrue(AccessibilityRebind.isClusterNavBound("Accessibility manager state:\n    Enabled services:{{$acc}}\n"))
    }

    @Test
    fun `enabled-not-bound dump is detected as NOT bound (clusternav only under Enabled)`() {
        val dump = """
            Accessibility manager state:
                User state[attributes:{id=0, currentUser=true}]
                       Bound services:{Service[label=StatusBar, capabilities=1, componentName=ComponentInfo{$sysui}]}
                       Enabled services:{{$vr}, {$sysui}, {$acc}}
                       Binding services:{}
        """.trimIndent()
        assertFalse(AccessibilityRebind.isClusterNavBound(dump), "clusternav is Enabled but NOT in Bound → not bound")
    }

    @Test
    fun `bound dump is detected as bound (clusternav inside Bound section, nested braces)`() {
        val dump = """
            Accessibility manager state:
                       Bound services:{Service[label=StatusBar, componentName=ComponentInfo{$sysui}], Service[label=ClusterNav booster, capabilities=9, componentName=ComponentInfo{$acc}]}
                       Enabled services:{{$sysui}, {$acc}}
        """.trimIndent()
        assertTrue(AccessibilityRebind.isClusterNavBound(dump))
    }

    @Test
    fun `bound detection is scoped so a later Enabled entry does not count as bound`() {
        val dump = "Bound services:{Service[componentName=ComponentInfo{$sysui}]}\nEnabled services:{{$acc}}"
        assertFalse(AccessibilityRebind.isClusterNavBound(dump))
    }
}
