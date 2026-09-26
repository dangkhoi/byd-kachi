package com.byd.clusternav.vietmapwidget

import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.vietmapwidget.VietMapWidgetBridge.Companion.TAG
import com.byd.clusternav.vietmapwidget.VietMapWidgetBridge.Companion.unavailable
import com.byd.clusternav.vietmapwidget.VietMapWidgetBridge.ListenerEntry

/**
 * ═══ VAI "snapshot/publish" của [VietMapWidgetBridge]: gom độ tươi từng provider → ghép snapshot → phát cho listener ═══
 *
 * Tách khỏi `VietMapWidgetBridge.kt` (513 dòng → trần 500, CLAUDE.md §4.1) theo VAI, đúng khuôn `DEBT-500`
 * (`VoiceSession`→`VoiceSessionTurns`, `WorkspacePrefs`→`WorkspacePrefsLang`): là **hàm mở rộng của chính
 * [VietMapWidgetBridge]**, dùng lại ĐÚNG các trường của bridge (nay `internal`), nên bề mặt gọi trong bridge
 * (`publishSnapshot()` · `clearRuntimeValues()` · `setUnavailable(...)`) không đổi một ký tự và không có bản sao
 * trạng thái nào. Thân từng hàm chép NGUYÊN VĂN; thứ tự trong `stop()` (clear → publish → `listening=false` →
 * `host.stopListening()`) do `SpeedSignSourceLifecycleTest` canh vẫn nằm ở tệp bridge.
 *
 * Ba vai còn lại vẫn ở tệp bridge: lifecycle (`start`/`stop`), bind/host (`beginBinding`/`restoreBoundViews`/
 * `autoBindMissing`), và catalog đã tách từ trước ([VietMapProviderCatalog]).
 */
// --- Snapshot publishing (per-provider independent) ---
internal fun VietMapWidgetBridge.publishSnapshot() {
    val now = SystemClock.elapsedRealtime()
    // Compute per-provider freshness INDEPENDENTLY — no combined gate
    val (speedFresh, speedFreshReason) = VietMapWidgetTextParser.freshness(
        speedSnapshot.updatedAtElapsedMs, now, unavailableReasonForSlot(VietMapWidgetSlot.SPEED_LIMIT)
    )
    val (alertsFresh, alertsFreshReason) = VietMapWidgetTextParser.freshness(
        alertsSnapshot.updatedAtElapsedMs, now, unavailableReasonForSlot(VietMapWidgetSlot.ALERTS)
    )
    val (alertFullFresh, alertFullFreshReason) = VietMapWidgetTextParser.freshness(
        alertFullSnapshot.updatedAtElapsedMs, now, unavailableReasonForSlot(VietMapWidgetSlot.ALERT_FULL)
    )
    // Persist computed freshness back onto each provider snapshot
    speedSnapshot = speedSnapshot.copy(freshness = speedFresh, reason = speedFreshReason)
    alertsSnapshot = alertsSnapshot.copy(freshness = alertsFresh, reason = alertsFreshReason)
    alertFullSnapshot = alertFullSnapshot.copy(freshness = alertFullFresh, reason = alertFullFreshReason)
    // Delegate the (pure, tested) composition to :core — combined freshness stays speed+alerts only;
    // ALERT_FULL projects purely into the additive upcoming* fields under its own freshness.
    val composed = VietMapWidgetTextParser.composeSnapshot(
        speed = providerState(speedSnapshot),
        alerts = providerState(alertsSnapshot),
        alertFull = providerState(alertFullSnapshot),
        providerVersion = providerVersion(),
        nowElapsedMs = now,
    )
    val next = composed.snapshot
    if (next == published) return
    published = next
    dispatchToListeners(next)
}

internal fun VietMapWidgetBridge.providerState(snap: VietMapProviderSnapshot<VietMapWidgetRawValues>): VietMapProviderState =
    VietMapProviderState(snap.values, snap.freshness, snap.reason, snap.updatedAtElapsedMs)
/**
 * Dispatch snapshot to listeners, filtering out stale-generation entries.
 * Stale listeners are automatically pruned.
 */
internal fun VietMapWidgetBridge.dispatchToListeners(snapshot: VietMapWidgetSnapshot) {
    val stale = mutableListOf<ListenerEntry>()
    listeners.forEach { entry ->
        if (entry.generation != listenerGeneration) {
            stale += entry
            return@forEach
        }
        try {
            entry.callback(snapshot)
        } catch (error: RuntimeException) {
            Log.e(TAG, "widget snapshot listener failed", error)
        }
    }
    if (stale.isNotEmpty()) {
        listeners.removeAll(stale.toSet())
        Log.d(TAG, "pruned ${stale.size} stale listener(s)")
    }
}
// --- Per-slot unavailable reason (independent of other slot) ---
internal fun VietMapWidgetBridge.unavailableReasonForSlot(slot: VietMapWidgetSlot): VietMapWidgetUnavailableReason? = when {
    providerInfo(slot) == null -> VietMapWidgetUnavailableReason.PROVIDER_MISSING
    slot in unsupportedSlots -> VietMapWidgetUnavailableReason.UNSUPPORTED_SHAPE
    !isSlotBound(slot) -> VietMapWidgetUnavailableReason.NOT_BOUND
    else -> null
}
internal fun VietMapWidgetBridge.isSlotBound(slot: VietMapWidgetSlot): Boolean {
    val id = prefs.widgetId(slot) ?: return false
    return manager.getAppWidgetInfo(id)?.provider == slot.component
}

internal fun VietMapWidgetBridge.clearRuntimeValues() {
    views.clear()
    slotsById.clear()
    unsupportedSlots.clear()
    speedSnapshot = speedSnapshot.copy(
        values = null, updatedAtElapsedMs = null,
        freshness = VietMapWidgetFreshness.UNAVAILABLE,
        reason = VietMapWidgetUnavailableReason.NOT_BOUND,
    )
    alertsSnapshot = alertsSnapshot.copy(
        values = null, updatedAtElapsedMs = null,
        freshness = VietMapWidgetFreshness.UNAVAILABLE,
        reason = VietMapWidgetUnavailableReason.NOT_BOUND,
    )
    alertFullSnapshot = alertFullSnapshot.copy(
        values = null, updatedAtElapsedMs = null,
        freshness = VietMapWidgetFreshness.UNAVAILABLE,
        reason = VietMapWidgetUnavailableReason.NOT_BOUND,
    )
    extraction.releaseResources()
}
internal fun VietMapWidgetBridge.setUnavailable(reason: VietMapWidgetUnavailableReason) {
    published = unavailable(reason)
    dispatchToListeners(published)
}
