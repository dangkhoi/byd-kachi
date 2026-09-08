package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.modules.clustercast.simplified.AppType
import com.byd.clusternav.modules.clustercast.simplified.DisplayConfig
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState
import com.byd.clusternav.modules.clustercast.simplified.SlotState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Accessibility contract for the SINGLE-ICON floating bubble (R5 / #7 — replaces the old 3-zone
 * Trái/Phải/Full contract). Pure JVM: verifies the renderer's content-description strings and size
 * constants without an Android Context (actual view rendering is on-car visual).
 *
 * 1. The icon has ONE content description ("ClusterNav cast"); there are no per-zone labels.
 * 2. The content description tracks state (idle → cast, casting → return) so a screen-reader user
 *    hears what a tap will do.
 * 3. The icon touch target is ≥48dp (automotive guideline) — bigger than the old 38dp zones.
 */
class BubbleAccessibilityTest {

    // ─── 1. Single icon, one content description, no zone labels ─────────────

    @Test
    fun `idle content description is the single-icon label 'ClusterNav cast'`() {
        assertEquals("ClusterNav cast", BubbleRenderer.CONTENT_DESC_IDLE)
        assertEquals("ClusterNav cast", BubbleRenderer.contentDescriptionFor(SimpleCastState.Idle))
    }

    @Test
    fun `no content description uses the old zone words Trai Phai or Full`() {
        // The 3-zone contract is gone. None of the icon's descriptions may carry a zone label.
        val all = listOf(
            BubbleRenderer.CONTENT_DESC_IDLE,
            BubbleRenderer.CONTENT_DESC_CASTING,
            BubbleRenderer.CONTENT_DESC_BUSY,
        )
        all.forEach { desc ->
            listOf("Trái", "Phải", "Full", "chạm để chiếu", "không khả dụng").forEach { zoneWord ->
                assertFalse(desc.contains(zoneWord), "single icon must not use zone word '$zoneWord' — was: $desc")
            }
        }
    }

    // ─── 2. Content description tracks state (cast ↔ return) ─────────────────

    @Test
    fun `casting content description hints 'return' so a tap meaning is clear`() {
        val full = SimpleCastState.CastingFull("com.test", AppType.NORMAL, DisplayConfig.NORMAL_DEFAULT)
        val desc = BubbleRenderer.contentDescriptionFor(full)
        assertEquals(BubbleRenderer.CONTENT_DESC_CASTING, desc)
        assertTrue(desc.contains("trả về"), "casting icon must hint 'tap to return' — was: $desc")
        assertTrue(desc.startsWith("ClusterNav cast"), "still the same icon identity — was: $desc")
    }

    @Test
    fun `split state also reads as casting (a tap returns everything)`() {
        val split = SimpleCastState.CastingSplit(
            left = SlotState("com.left", DisplayConfig.NORMAL_DEFAULT), right = null,
        )
        assertEquals(BubbleRenderer.CONTENT_DESC_CASTING, BubbleRenderer.contentDescriptionFor(split))
    }

    @Test
    fun `transient states read as busy, not a dead label`() {
        listOf(
            SimpleCastState.Off,
            SimpleCastState.Opening,
            SimpleCastState.Stopping,
            SimpleCastState.Closing,
            SimpleCastState.Error("x"),
        ).forEach { state ->
            assertEquals(BubbleRenderer.CONTENT_DESC_BUSY, BubbleRenderer.contentDescriptionFor(state))
        }
    }

    @Test
    fun `all three descriptions share the ClusterNav cast identity and are non-blank`() {
        listOf(
            BubbleRenderer.CONTENT_DESC_IDLE,
            BubbleRenderer.CONTENT_DESC_CASTING,
            BubbleRenderer.CONTENT_DESC_BUSY,
        ).forEach {
            assertTrue(it.isNotBlank())
            assertTrue(it.startsWith("ClusterNav cast"), "must carry the icon identity — was: $it")
        }
    }

    // ─── 3. Touch target ≥48dp (automotive guideline) ────────────────────────

    @Test
    fun `icon is app-icon sized within the 48-56dp band the spec asks for`() {
        assertTrue(
            BubbleRenderer.ICON_SIZE_DP in 48..56,
            "icon size (${BubbleRenderer.ICON_SIZE_DP}dp) must be ~app-icon sized (48–56dp)",
        )
    }

    @Test
    fun `icon touch target honours the 48dp automotive minimum`() {
        assertTrue(BubbleRenderer.TOUCH_MIN_DP >= 48, "automotive touch target must be ≥48dp")
        assertTrue(
            BubbleRenderer.ICON_SIZE_DP >= BubbleRenderer.TOUCH_MIN_DP,
            "rendered icon (${BubbleRenderer.ICON_SIZE_DP}dp) must meet the ${BubbleRenderer.TOUCH_MIN_DP}dp floor",
        )
    }
}
