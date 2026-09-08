package com.byd.clusternav.navigation.screencapture

import com.byd.clusternav.navigation.screencapture.ForegroundWindowFilter.TYPE_ACCESSIBILITY_OVERLAY
import com.byd.clusternav.navigation.screencapture.ForegroundWindowFilter.TYPE_APPLICATION
import com.byd.clusternav.navigation.screencapture.ForegroundWindowFilter.TYPE_INPUT_METHOD
import com.byd.clusternav.navigation.screencapture.ForegroundWindowFilter.TYPE_MAGNIFICATION_OVERLAY
import com.byd.clusternav.navigation.screencapture.ForegroundWindowFilter.TYPE_SPLIT_SCREEN_DIVIDER
import com.byd.clusternav.navigation.screencapture.ForegroundWindowFilter.TYPE_SYSTEM
import com.byd.clusternav.navigation.screencapture.ForegroundWindowFilter.TYPE_UNKNOWN
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure off-car lock for the B3.7 foreground-window decision — a floating/overlay window (e.g. WazeMod's HUD)
 * must NOT override the real foreground app (e.g. VietMap). Window-info path + event-semantics degrade path.
 */
class ForegroundWindowFilterTest {

    private fun decide(
        windowType: Int = TYPE_UNKNOWN,
        isActive: Boolean = false,
        isFocused: Boolean = false,
        stateChange: Boolean = false,
        sameAsCurrent: Boolean = false,
    ) = ForegroundWindowFilter.shouldPublishForeground(windowType, isActive, isFocused, stateChange, sameAsCurrent)

    @Test
    fun `accessibility overlay window never wins even when active focused and state-change`() {
        assertFalse(
            decide(
                windowType = TYPE_ACCESSIBILITY_OVERLAY,
                isActive = true, isFocused = true, stateChange = true, sameAsCurrent = true,
            ),
        )
    }

    @Test
    fun `system IME divider magnifier windows never define foreground`() {
        for (t in listOf(TYPE_INPUT_METHOD, TYPE_SYSTEM, TYPE_SPLIT_SCREEN_DIVIDER, TYPE_MAGNIFICATION_OVERLAY)) {
            assertFalse(decide(windowType = t, isActive = true, isFocused = true), "type=$t must not win")
        }
    }

    @Test
    fun `application window counts only when active or focused`() {
        assertFalse(decide(windowType = TYPE_APPLICATION, isActive = false, isFocused = false))
        assertTrue(decide(windowType = TYPE_APPLICATION, isActive = true, isFocused = false))
        assertTrue(decide(windowType = TYPE_APPLICATION, isActive = false, isFocused = true))
    }

    @Test
    fun `application window drawn overlay but inactive is rejected (window-info path)`() {
        // WazeMod overlay reporting TYPE_APPLICATION but not the active/focused task.
        assertFalse(
            decide(windowType = TYPE_APPLICATION, isActive = false, isFocused = false, stateChange = false),
        )
    }

    @Test
    fun `unknown window degrades to a real foreground transition (state change)`() {
        assertTrue(decide(windowType = TYPE_UNKNOWN, stateChange = true, sameAsCurrent = false))
    }

    @Test
    fun `unknown window content-change of a DIFFERENT package does not hijack foreground`() {
        // The reported on-car bug with window info unavailable: WazeMod overlay content update while VietMap
        // is foreground → different pkg, not a state change → must NOT be published.
        assertFalse(decide(windowType = TYPE_UNKNOWN, stateChange = false, sameAsCurrent = false))
    }

    @Test
    fun `unknown window content-change of the SAME package refreshes (keep-alive)`() {
        assertTrue(decide(windowType = TYPE_UNKNOWN, stateChange = false, sameAsCurrent = true))
    }
}
