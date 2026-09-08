package com.byd.clusternav.vietmapwidget

import android.content.Intent

/**
 * Result of a widget binding attempt.
 */
sealed interface VietMapWidgetBindResult {
    /** Binding is already active. */
    data class Bound(val slot: VietMapWidgetSlot) : VietMapWidgetBindResult

    /** User consent is needed to complete binding. */
    data class ConsentRequired(
        val slot: VietMapWidgetSlot,
        val appWidgetId: Int,
        val intent: Intent,
    ) : VietMapWidgetBindResult

    /** Binding failed. */
    data class Failed(
        val slot: VietMapWidgetSlot,
        val reason: VietMapWidgetUnavailableReason,
        val detail: String,
    ) : VietMapWidgetBindResult
}

/**
 * Status of a single widget slot's binding.
 */
data class VietMapWidgetBindingStatus(
    val slot: VietMapWidgetSlot,
    val appWidgetId: Int?,
    val providerAvailable: Boolean,
    val bound: Boolean,
)
