package com.byd.clusternav.modules.clustercast.simplified

/**
 * Pure placement math for the bubble long-press submenu (R5 / #7): put the card **beside** the
 * floating bubble (not centred on screen). Android-free so it is unit-testable without a device;
 * [BubbleSubmenuOverlay] measures the card + reads the bubble window position and calls this.
 *
 * Rule: card goes to the RIGHT of the bubble when the bubble sits on the left half of the screen,
 * otherwise to the LEFT; vertically it is centred on the bubble. Everything is then clamped inside
 * the screen with a [margin] so the card never renders off-screen.
 */
object BubbleSubmenuAnchor {
    /** @return (leftMargin, topMargin) in px for the card, relative to the top-left of the screen. */
    fun offset(
        screenW: Int,
        screenH: Int,
        anchorX: Int,
        anchorY: Int,
        anchorW: Int,
        anchorH: Int,
        cardW: Int,
        cardH: Int,
        gap: Int,
        margin: Int,
    ): Pair<Int, Int> {
        val bubbleCenterX = anchorX + anchorW / 2
        val placeRight = bubbleCenterX <= screenW / 2
        val rawLeft = if (placeRight) anchorX + anchorW + gap else anchorX - cardW - gap
        val rawTop = anchorY + anchorH / 2 - cardH / 2
        val maxLeft = (screenW - cardW - margin).coerceAtLeast(margin)
        val maxTop = (screenH - cardH - margin).coerceAtLeast(margin)
        return rawLeft.coerceIn(margin, maxLeft) to rawTop.coerceIn(margin, maxTop)
    }
}
