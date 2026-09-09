package com.byd.clusternav.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the single sanctioned persistent-window-state writer.
 *
 * The invariants under test are the whole point of the class (the cluster-BRICK vector):
 *  • commit-before-mutate — the durable marker is written BEFORE any Settings.Global write;
 *  • FF_USER_REMOVED is terminal — ensureSeed never re-seeds after the user opts out;
 *  • resetDisplayAll is the ONLY clearer of the persisted geometry writes;
 *  • command strings are byte-identical to the proven on-car strings.
 *
 * A single [Recorder] captures marker commits AND shell commands into ONE ordered list, so ordering claims are
 * proven by the recorded sequence rather than asserted structurally.
 */
class FreeformSeedPolicyTest {

    private class Recorder(initial: FreeformSeedPolicy.SeedState = FreeformSeedPolicy.SeedState.NONE) {
        val events = mutableListOf<String>()
        val log = mutableListOf<String>()
        private var state = initial
        private val markers = object : FreeformSeedPolicy.MarkerStore {
            override fun read() = state
            override fun commit(state: FreeformSeedPolicy.SeedState) { this@Recorder.state = state; events += "commit:${state.code}" }
        }
        private val sh: (String) -> String = { events += "sh:$it"; "" }
        fun policy() = FreeformSeedPolicy(markers, sh) { log += it }
        fun state() = state
    }

    // ── byte-identical command strings ─────────────────────────────────────────────────────────────

    @Test
    fun `seed commands are byte-exact and in order`() {
        assertEquals(
            listOf(
                "settings put global enable_freeform_support 1",
                "settings put global force_resizable_activities 1",
            ),
            FreeformSeedPolicy.SEED_CMDS,
        )
    }

    @Test
    fun `unseed commands are byte-exact and in order`() {
        assertEquals(
            listOf(
                "settings delete global enable_freeform_support",
                "settings delete global force_resizable_activities",
            ),
            FreeformSeedPolicy.UNSEED_CMDS,
        )
    }

    @Test
    fun `wm size, density and reset command builders are byte-exact`() {
        assertEquals("wm size 1920x720 -d 1", FreeformSeedPolicy.sizeCmd(1, 1920, 720))
        assertEquals("wm density 200 -d 1", FreeformSeedPolicy.densityCmd(1, 200))
        assertEquals(listOf("wm size reset -d 1", "wm density reset -d 1"), FreeformSeedPolicy.resetCmds(1))
    }

    // ── commit-before-mutate ───────────────────────────────────────────────────────────────────────

    @Test
    fun `ensureSeed commits the marker BEFORE any settings write`() {
        val r = Recorder()
        assertTrue(r.policy().ensureSeed())
        assertEquals(
            listOf(
                "commit:1", // ★ marker SEEDED committed FIRST
                "sh:settings put global enable_freeform_support 1",
                "sh:settings put global force_resizable_activities 1",
            ),
            r.events,
        )
    }

    // ── FF_USER_REMOVED terminal ───────────────────────────────────────────────────────────────────

    @Test
    fun `ensureSeed is a no-op once the user removed freeform (terminal)`() {
        val r = Recorder(FreeformSeedPolicy.SeedState.USER_REMOVED)
        assertFalse(r.policy().ensureSeed())
        assertEquals(emptyList<String>(), r.events, "must not commit or write once USER_REMOVED")
        assertEquals(FreeformSeedPolicy.SeedState.USER_REMOVED, r.state())
    }

    @Test
    fun `ensureSeed seeds from NONE and SEEDED but never from USER_REMOVED`() {
        assertTrue(Recorder(FreeformSeedPolicy.SeedState.NONE).policy().ensureSeed())
        assertTrue(Recorder(FreeformSeedPolicy.SeedState.SEEDED).policy().ensureSeed())
        assertFalse(Recorder(FreeformSeedPolicy.SeedState.USER_REMOVED).policy().ensureSeed())
    }

    // ── unseed writes flags THEN marks terminal ────────────────────────────────────────────────────

    @Test
    fun `unseed deletes both flags then commits the terminal marker`() {
        val r = Recorder(FreeformSeedPolicy.SeedState.SEEDED)
        r.policy().unseed()
        assertEquals(
            listOf(
                "sh:settings delete global enable_freeform_support",
                "sh:settings delete global force_resizable_activities",
                "commit:2", // USER_REMOVED after the deletes
            ),
            r.events,
        )
        assertEquals(FreeformSeedPolicy.SeedState.USER_REMOVED, r.state())
    }

    // ── reset is the ONLY clearer ──────────────────────────────────────────────────────────────────

    @Test
    fun `value writes never emit a reset`() {
        val r = Recorder()
        val p = r.policy()
        assertEquals("wm size 1920x720 -d 1", p.writeDisplaySize(1, 1920, 720))
        assertEquals("wm density 200 -d 1", p.writeDisplayDensity(1, 200))
        assertEquals(listOf("sh:wm size 1920x720 -d 1", "sh:wm density 200 -d 1"), r.events)
        assertTrue(r.events.none { it.contains("reset") }, "value writes must never clear the persisted state")
    }

    @Test
    fun `resetDisplayAll is the only clearer and emits size then density reset`() {
        val r = Recorder()
        r.policy().resetDisplayAll(1)
        assertEquals(listOf("sh:wm size reset -d 1", "sh:wm density reset -d 1"), r.events)
    }

    // ── marker helpers + on-disk code compatibility ────────────────────────────────────────────────

    @Test
    fun `seedMarked is true only for SEEDED`() {
        assertFalse(FreeformSeedPolicy(store(FreeformSeedPolicy.SeedState.NONE), { "" }).seedMarked())
        assertTrue(FreeformSeedPolicy(store(FreeformSeedPolicy.SeedState.SEEDED), { "" }).seedMarked())
        assertFalse(FreeformSeedPolicy(store(FreeformSeedPolicy.SeedState.USER_REMOVED), { "" }).seedMarked())
    }

    @Test
    fun `SeedState codes match the on-disk cast marker ints`() {
        assertEquals(0, FreeformSeedPolicy.SeedState.NONE.code)
        assertEquals(1, FreeformSeedPolicy.SeedState.SEEDED.code)
        assertEquals(2, FreeformSeedPolicy.SeedState.USER_REMOVED.code)
        assertEquals(FreeformSeedPolicy.SeedState.SEEDED, FreeformSeedPolicy.SeedState.of(1))
        assertEquals(FreeformSeedPolicy.SeedState.NONE, FreeformSeedPolicy.SeedState.of(99)) // unknown -> NONE
    }

    private fun store(s: FreeformSeedPolicy.SeedState) = object : FreeformSeedPolicy.MarkerStore {
        override fun read() = s
        override fun commit(state: FreeformSeedPolicy.SeedState) {}
    }
}
