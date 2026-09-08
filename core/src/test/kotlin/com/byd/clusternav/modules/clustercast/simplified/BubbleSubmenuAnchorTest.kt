package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Pure placement contract for the long-press submenu (R5 / #7): card sits BESIDE the bubble. */
class BubbleSubmenuAnchorTest {
    // 1920×720 IVI; bubble 156px; card 420×300px; gap 24; margin 24.
    @Test
    fun `bubble on the right half places the card to its left`() {
        val (left, _) = BubbleSubmenuAnchor.offset(1920, 720, 1700, 300, 156, 156, 420, 300, 24, 24)
        assertEquals(1700 - 420 - 24, left)
        assertTrue(left < 1700, "card is left of the bubble")
    }

    @Test
    fun `bubble on the left half places the card to its right`() {
        val (left, _) = BubbleSubmenuAnchor.offset(1920, 720, 60, 300, 156, 156, 420, 300, 24, 24)
        assertEquals(60 + 156 + 24, left)
    }

    @Test
    fun `card is vertically centred on the bubble`() {
        val (_, top) = BubbleSubmenuAnchor.offset(1920, 720, 60, 300, 156, 156, 420, 300, 24, 24)
        assertEquals(300 + 156 / 2 - 300 / 2, top)
    }

    @Test
    fun `placement is clamped inside the screen`() {
        val (left, top) = BubbleSubmenuAnchor.offset(1920, 720, 1910, 715, 40, 40, 420, 300, 24, 24)
        assertTrue(left in 24..(1920 - 420 - 24), "left in bounds: $left")
        assertTrue(top in 24..(720 - 300 - 24), "top in bounds: $top")
    }
}
