package com.byd.clusternav.vietmapwidget

/**
 * Identifies which component owns a VietMapWidgetBridge listening session.
 * The bridge stays listening as long as at least one owner has called start().
 */
enum class VietMapWidgetOwner {
    NAVIGATION,
    DIAGNOSTICS,
}
