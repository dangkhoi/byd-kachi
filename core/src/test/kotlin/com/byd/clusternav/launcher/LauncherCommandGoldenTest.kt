package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * STAGE 0 SAFETY NET — byte-locks the EXACT window/display shell command strings that the pure :core launcher
 * builders ([FreeformLaunch], [ShellAppLauncher]) emit today. These strings are PROVEN on the car (they reuse
 * the cluster-cast recipe: `am task resize`, fullscreen return, resolve-activity). Every later refactor stage
 * MUST keep them byte-identical — this file is the tripwire.
 *
 * 2.93 · SLOT-DEAD-OPENSLOT (spec `kachi-293-wave2a.html` §4.4): the freeform OPEN path (`launchCmd` + `openInSlot` /
 * `moveToSlot` sequences) was removed — 0 product call sites — so its three golden locks went with it.
 *
 * Relationship to the existing suite (REUSE/EXTEND, not duplicate):
 *  - [FreeformLaunchTest] already covers behaviour + `contains` checks for launch/fullscreen/flags and exact
 *    resize + the parsers. THIS file adds exact-equality (`assertEquals`) golden locks for the command
 *    TEMPLATES that were NOT yet byte-pinned (launch, resolve, fullscreen, freeform flags).
 *  - [ShellAppLauncherTest] covers task-selection behaviour. THIS file pins the exact emitted-command SEQUENCE
 *    (byte-for-byte, in order) of the on-car adapter.
 *
 * Pure JVM — no android.*, no file IO. All representative inputs mirror on-car values (Seal cluster 1920×720,
 * slot [0,90,1920,630]).
 */
class LauncherCommandGoldenTest {

    private val slot = SlotRect(index = 0, left = 0, top = 90, right = 1920, bottom = 630)

    // ─────────────────────────── FreeformLaunch: exact command templates ───────────────────────────

    @Test
    fun `resolve-activity command is byte-exact`() {
        assertEquals(
            "cmd package resolve-activity --brief -a android.intent.action.MAIN" +
                " -c android.intent.category.LAUNCHER com.foo",
            FreeformLaunch.resolveCmd("com.foo"),
        )
    }

    @Test
    fun `fullscreen-return command is byte-exact on the main display`() {
        assertEquals(
            "am start --display 0 --windowingMode 1 -f 0x20000000" +
                " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n 'com.foo/.Main'",
            FreeformLaunch.fullscreenCmd("com.foo/.Main"),
        )
    }

    @Test
    fun `resize command is byte-exact (left top right bottom, not w h)`() {
        assertEquals("am task resize 42 0 90 1920 630", FreeformLaunch.resizeCmd(42, slot))
        assertEquals(
            "am task resize 7 10 20 300 400",
            FreeformLaunch.resizeCmd(7, SlotRect(index = 3, left = 10, top = 20, right = 300, bottom = 400)),
        )
    }

    @Test
    fun `freeform boot flags are byte-exact and in order`() {
        // Stage B3: the freeform-flag command strings live ONLY in the single sanctioned writer FreeformSeedPolicy
        // (the former FreeformLaunch.freeformFlagCmds constant was removed). Byte-lock stays here + FreeformSeedPolicyTest.
        assertEquals(
            listOf(
                "settings put global enable_freeform_support 1",
                "settings put global force_resizable_activities 1",
            ),
            com.byd.clusternav.system.FreeformSeedPolicy.SEED_CMDS,
        )
    }

    // ── launchOnDisplayCmd: byte-locks the strings VdAppHost/SlotAppHost used to build inline (routed here in B1) ──

    @Test
    fun `launchOnDisplayCmd with launcher category is byte-exact (VdAppHost path)`() {
        assertEquals(
            "am start --display 7 --windowingMode 1 -a android.intent.action.MAIN" +
                " -c android.intent.category.LAUNCHER -n 'com.foo/.Main'",
            FreeformLaunch.launchOnDisplayCmd("com.foo/.Main", 7, 1),
        )
    }

    @Test
    fun `launchOnDisplayCmd without launcher category is byte-exact (SlotAppHost path)`() {
        assertEquals(
            "am start --display 7 --windowingMode 1 -n 'com.foo/.Main'",
            FreeformLaunch.launchOnDisplayCmd("com.foo/.Main", 7, 1, withLauncherCategory = false),
        )
    }

    // ─────────────────────── ShellAppLauncher: exact emitted-command sequence ───────────────────────

    /** Records every shell command in order; scripts resolve / stack-list / resize replies by command. */
    private fun recording(
        calls: MutableList<String>,
        stack: String,
        component: String = "com.foo/.Main",
        resizeOut: String = "",
    ): (String) -> String = { cmd ->
        calls += cmd
        when {
            cmd.startsWith("cmd package resolve-activity") -> "priority=0\n$component"
            cmd == "am stack list" -> stack
            cmd.startsWith("am task resize") -> resizeOut
            else -> ""
        }
    }

    @Test
    fun `closeSlot emits the exact fullscreen-return sequence`() {
        val calls = mutableListOf<String>()
        ShellAppLauncher(recording(calls, stack = "")).closeSlot("com.foo")
        assertEquals(
            listOf(
                "cmd package resolve-activity --brief -a android.intent.action.MAIN" +
                    " -c android.intent.category.LAUNCHER com.foo",
                "am start --display 0 --windowingMode 1 -f 0x20000000" +
                    " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n 'com.foo/.Main'",
            ),
            calls,
        )
    }

    // 2.93 wave 2C · SLOT-DEAD-FREEFORM-REST (spec `kachi-293-wave2c.html` R2): `isFreeformAvailable` (`settings get global
    // enable_freeform_support`) removed with 0 product call sites — its golden lock went with it. The adapter's ONLY emitted
    // sequence left is `closeSlot` above (pinned byte-for-byte; `ShellAppLauncherTest` pins the contract shape).
}
