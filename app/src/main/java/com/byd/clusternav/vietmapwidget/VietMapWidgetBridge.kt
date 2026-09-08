package com.byd.clusternav.vietmapwidget

import com.byd.clusternav.navigation.NavApps
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutionException
// §7 — MỘT nguồn sự thật cho tên gói (sửa 08-23 vòng 2b). Trước đây mỗi file widget tự chép chuỗi
// "vn.vietmap.live"; `NavPackageRosterSyncTest` không canh tới đây nên bản chép này trôi im lặng.
private const val VIETMAP_PACKAGE = NavApps.VIETMAP_LIVE
/**
 * VietMap widget bridge — binding lifecycle, per-provider independence, generation-bound callbacks.
 *
 * ⛔ GIỮ — ĐỪNG XÓA (closing 2026-08-28). Đây là nguồn VietMap **DUY NHẤT còn lại** sau khi owner gỡ toàn bộ
 * đường dẫn-đường VietMap/Waze (a11y turn + screen-capture mũi tên) khỏi cụm/HUD. File này KHÁC HẲN đường đã
 * gỡ: nó đọc **AppWidgetHost RemoteViews** (KHÔNG qua accessibility, KHÔNG screen-capture) để lấy **tốc
 * độ + giới hạn tốc độ (hiện tại + sắp tới)** → nuôi `speedbadge/SpeedBadgeOverlay` trên cụm. Gỡ nhầm file này
 * = mất speed badge. Xem `docs/runbook-mod-vietmap-cluster.md` + `.kiro/steering/project-context.md`.
 *
 * Extraction logic is delegated to [VietMapWidgetExtraction].
 * Clear logic is handled by [VietMapWidgetClearStateMachine].
 *
 * Each provider (speed, alerts) maintains its own freshness, reason, and generation counter.
 * A stale alert update NEVER invalidates a fresh speed reading, and vice versa.
 */
class VietMapWidgetBridge private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val manager = AppWidgetManager.getInstance(appContext)
    private val prefs = VietMapWidgetPrefs(appContext)
    private val main = Handler(Looper.getMainLooper())
    private val extraction = VietMapWidgetExtraction(appContext)
    private val host = VietMapAppWidgetHost(appContext, HOST_ID, ::onHostViewUpdated)
    private val owners = linkedSetOf<VietMapWidgetOwner>()
    private val listeners = CopyOnWriteArraySet<ListenerEntry>()
    private val views = mutableMapOf<VietMapWidgetSlot, AppWidgetHostView>()
    private val slotsById = mutableMapOf<Int, VietMapWidgetSlot>()
    private val unsupportedSlots = mutableSetOf<VietMapWidgetSlot>()
    /** Generation counters for listener callback binding — incremented on stop/restart. */
    @Volatile private var listenerGeneration = 0L
    /** Per-provider snapshots — fully independent. */
    private var speedSnapshot = VietMapProviderSnapshot<VietMapWidgetRawValues>(
        slot = VietMapWidgetSlot.SPEED_LIMIT, values = null,
        updatedAtElapsedMs = null, freshness = VietMapWidgetFreshness.UNAVAILABLE,
        reason = VietMapWidgetUnavailableReason.NOT_BOUND, generation = 0L,
    )
    private var alertsSnapshot = VietMapProviderSnapshot<VietMapWidgetRawValues>(
        slot = VietMapWidgetSlot.ALERTS, values = null,
        updatedAtElapsedMs = null, freshness = VietMapWidgetFreshness.UNAVAILABLE,
        reason = VietMapWidgetUnavailableReason.NOT_BOUND, generation = 0L,
    )
    private var alertFullSnapshot = VietMapProviderSnapshot<VietMapWidgetRawValues>(
        slot = VietMapWidgetSlot.ALERT_FULL, values = null,
        updatedAtElapsedMs = null, freshness = VietMapWidgetFreshness.UNAVAILABLE,
        reason = VietMapWidgetUnavailableReason.NOT_BOUND, generation = 0L,
    )
    private var listening = false
    @Volatile private var published = unavailable(VietMapWidgetUnavailableReason.NOT_BOUND)
    private val publishDebounced = Runnable { publishSnapshot() }
    private val freshnessTick = object : Runnable {
        override fun run() {
            if (!listening) return
            publishSnapshot()
            main.postDelayed(this, FRESHNESS_TICK_MS)
        }
    }
    // --- Lifecycle ---
    fun start(owner: VietMapWidgetOwner) = onMain {
        owners.add(owner)
        if (listening) return@onMain
        listenerGeneration++
        listening = true
        try {
            host.startListening()
            restoreBoundViews()
            autoBindMissing()
            main.removeCallbacks(freshnessTick)
            main.post(freshnessTick)
            Log.i(TAG, "widget host listening (gen=$listenerGeneration)")
        } catch (error: RuntimeException) {
            Log.e(TAG, "widget host start failed", error)
            listening = false
            setUnavailable(VietMapWidgetUnavailableReason.HOST_ERROR)
        }
    }
    fun stop(owner: VietMapWidgetOwner) = onMain {
        if (!owners.remove(owner) || owners.isNotEmpty() || !listening) return@onMain
        main.removeCallbacks(freshnessTick)
        main.removeCallbacks(publishDebounced)
        clearRuntimeValues()
        publishSnapshot()
        listenerGeneration++
        listening = false
        try {
            host.stopListening()
        } catch (error: RuntimeException) {
            Log.e(TAG, "widget host stop failed", error)
        }
        Log.i(TAG, "widget host stopped (gen=$listenerGeneration)")
    }
    // --- Listener management with generation binding ---
    fun addListener(listener: (VietMapWidgetSnapshot) -> Unit) {
        val entry = ListenerEntry(listener, listenerGeneration)
        listeners += entry
        main.post { listener(published) }
    }
    fun removeListener(listener: (VietMapWidgetSnapshot) -> Unit) {
        listeners.removeIf { it.callback === listener }
    }
    fun snapshot(): VietMapWidgetSnapshot = published
    // --- Binding ---
    fun bindingStatuses(): List<VietMapWidgetBindingStatus> = VietMapWidgetSlot.entries.map { slot ->
        val id = prefs.widgetId(slot)
        val available = providerInfo(slot) != null
        val bound = id != null && manager.getAppWidgetInfo(id)?.provider == slot.component
        VietMapWidgetBindingStatus(slot, id, available, bound)
    }
    fun beginBinding(slot: VietMapWidgetSlot): VietMapWidgetBindResult {
        val provider = providerInfo(slot)
            ?: return VietMapWidgetBindResult.Failed(
                slot, VietMapWidgetUnavailableReason.PROVIDER_MISSING,
                "VietMap provider is not installed",
            )
        bindingStatuses().first { it.slot == slot }.takeIf { it.bound }?.let {
            return VietMapWidgetBindResult.Bound(slot)
        }
        val appWidgetId = try {
            host.allocateAppWidgetId()
        } catch (error: RuntimeException) {
            Log.e(TAG, "widget ID allocation failed", error)
            return VietMapWidgetBindResult.Failed(
                slot, VietMapWidgetUnavailableReason.HOST_ERROR,
                "Cannot allocate widget ID",
            )
        }
        val bound = try {
            manager.bindAppWidgetIdIfAllowed(appWidgetId, provider.provider)
        } catch (error: SecurityException) {
            Log.w(TAG, "direct widget bind not allowed")
            false
        } catch (error: IllegalArgumentException) {
            deleteAllocatedId(appWidgetId)
            return VietMapWidgetBindResult.Failed(
                slot, VietMapWidgetUnavailableReason.HOST_ERROR,
                "Widget provider rejected the binding",
            )
        }
        if (bound && completeBinding(slot, appWidgetId, granted = true)) {
            return VietMapWidgetBindResult.Bound(slot)
        }
        if (bound) {
            return VietMapWidgetBindResult.Failed(
                slot, VietMapWidgetUnavailableReason.HOST_ERROR,
                "Android did not retain the widget binding",
            )
        }
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
        }
        if (intent.resolveActivity(appContext.packageManager) == null) {
            deleteAllocatedId(appWidgetId)
            return VietMapWidgetBindResult.Failed(
                slot, VietMapWidgetUnavailableReason.BIND_UI_UNAVAILABLE,
                "Không có màn xác nhận bind widget. Chạy lệnh sau khi đỗ xe:\n" +
                    "adb shell appwidget grantbind --package ${com.byd.clusternav.BuildConfig.APPLICATION_ID} --user 0\n" +
                    "rồi thử lại.",
            )
        }
        return VietMapWidgetBindResult.ConsentRequired(slot, appWidgetId, intent)
    }
    fun completeBinding(slot: VietMapWidgetSlot, appWidgetId: Int, granted: Boolean): Boolean {
        val actuallyBound = manager.getAppWidgetInfo(appWidgetId)?.provider == slot.component
        if (!granted || !actuallyBound) {
            deleteAllocatedId(appWidgetId)
            publishSnapshot()
            return false
        }
        prefs.widgetId(slot)?.takeIf { it != appWidgetId }?.let(::deleteAllocatedId)
        prefs.saveWidgetId(slot, appWidgetId, providerVersion())
        if (listening) restoreBoundViews()
        return true
    }
    fun unbindAll() = onMain {
        VietMapWidgetSlot.entries.forEach { slot ->
            prefs.widgetId(slot)?.let(::deleteAllocatedId)
        }
        prefs.clearAll()
        clearRuntimeValues()
        publishSnapshot()
        Log.i(TAG, "widget bindings removed")
    }
    // --- Host callback (generation-bound) ---
    private fun onHostViewUpdated(appWidgetId: Int, view: AppWidgetHostView) = onMain {
        if (!listening) return@onMain
        val callbackGeneration = listenerGeneration
        val slot = slotsById[appWidgetId] ?: return@onMain
        if (views[slot] !== view) return@onMain
        // Generation check: discard callbacks from prior listening sessions
        if (callbackGeneration != listenerGeneration) {
            Log.d(TAG, "discarding stale callback gen=$callbackGeneration current=$listenerGeneration")
            return@onMain
        }
        val now = SystemClock.elapsedRealtime()
        when (slot) {
            VietMapWidgetSlot.SPEED_LIMIT -> {
                val extracted = extraction.extractSpeed(view)
                if (extracted == null) {
                    unsupportedSlots += slot
                } else {
                    unsupportedSlots -= slot
                    speedSnapshot = speedSnapshot.copy(
                        values = extracted,
                        updatedAtElapsedMs = now,
                        generation = callbackGeneration,
                    )
                }
            }
            VietMapWidgetSlot.ALERTS -> {
                val extracted = extraction.extractAlerts(view)
                if (extracted == null) {
                    unsupportedSlots += slot
                } else {
                    unsupportedSlots -= slot
                    // Launch background hash and update when ready
                    val hashFuture = extraction.hashAlertsAsync(view)
                    alertsSnapshot = alertsSnapshot.copy(
                        values = extracted,
                        updatedAtElapsedMs = now,
                        generation = callbackGeneration,
                    )
                    // Schedule hash result merge (non-blocking on main)
                    Thread {
                        try {
                            val (h1, h2) = hashFuture.get()
                            main.post {
                                // Only merge if generation hasn't changed
                                if (listenerGeneration == callbackGeneration) {
                                    val current = alertsSnapshot.values
                                    if (current != null) {
                                        alertsSnapshot = alertsSnapshot.copy(
                                            values = current.copy(
                                                firstAlertImageHash = h1,
                                                secondAlertImageHash = h2,
                                            )
                                        )
                                        schedulePublish()
                                    }
                                }
                            }
                        } catch (interrupted: InterruptedException) {
                            Thread.currentThread().interrupt()
                        } catch (cancelled: CancellationException) {
                            Log.d(TAG, "alert hash cancelled", cancelled)
                        } catch (failed: ExecutionException) {
                            Log.w(TAG, "alert hash failed", failed.cause ?: failed)
                        }
                    }.start()
                }
            }
            VietMapWidgetSlot.ALERT_FULL -> {
                val extracted = extraction.extractAlertFull(view)
                if (extracted == null) {
                    unsupportedSlots += slot
                } else {
                    unsupportedSlots -= slot
                    alertFullSnapshot = alertFullSnapshot.copy(
                        values = extracted,
                        updatedAtElapsedMs = now,
                        generation = callbackGeneration,
                    )
                }
            }
        }
        schedulePublish()
    }
    private fun schedulePublish() {
        main.removeCallbacks(publishDebounced)
        main.postDelayed(publishDebounced, UPDATE_DEBOUNCE_MS)
    }
    // --- Snapshot publishing (per-provider independent) ---
    private fun publishSnapshot() {
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

    private fun providerState(snap: VietMapProviderSnapshot<VietMapWidgetRawValues>): VietMapProviderState =
        VietMapProviderState(snap.values, snap.freshness, snap.reason, snap.updatedAtElapsedMs)
    /**
     * Dispatch snapshot to listeners, filtering out stale-generation entries.
     * Stale listeners are automatically pruned.
     */
    private fun dispatchToListeners(snapshot: VietMapWidgetSnapshot) {
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
    private fun unavailableReasonForSlot(slot: VietMapWidgetSlot): VietMapWidgetUnavailableReason? = when {
        providerInfo(slot) == null -> VietMapWidgetUnavailableReason.PROVIDER_MISSING
        slot in unsupportedSlots -> VietMapWidgetUnavailableReason.UNSUPPORTED_SHAPE
        !isSlotBound(slot) -> VietMapWidgetUnavailableReason.NOT_BOUND
        else -> null
    }
    private fun isSlotBound(slot: VietMapWidgetSlot): Boolean {
        val id = prefs.widgetId(slot) ?: return false
        return manager.getAppWidgetInfo(id)?.provider == slot.component
    }
    // --- Restore / Clear ---
    private fun restoreBoundViews() {
        clearRuntimeValues()
        extraction.reloadRemoteResources()
        VietMapWidgetSlot.entries.forEach { slot ->
            val id = prefs.widgetId(slot) ?: return@forEach
            val info = manager.getAppWidgetInfo(id)
            if (info == null) {
                // Info temporarily unavailable (system not ready, or app just installed).
                // Keep the saved ID — do NOT delete. Will retry on next start/update.
                Log.w(TAG, "getAppWidgetInfo($id) returned null for ${slot.name} — keeping saved ID")
                slotsById[id] = slot
                return@forEach
            }
            if (info.provider != slot.component) {
                // Provider genuinely changed — orphan ID, clean up
                Log.w(TAG, "widget $id provider mismatch: ${info.provider} != ${slot.component} — removing")
                deleteAllocatedId(id)
                prefs.clearWidgetId(slot)
                return@forEach
            }
            if (providerInfo(slot) == null) {
                // Provider uninstalled but ID still points to it — clean up
                Log.w(TAG, "provider for ${slot.name} no longer installed — removing widget $id")
                deleteAllocatedId(id)
                prefs.clearWidgetId(slot)
                return@forEach
            }
            slotsById[id] = slot
            try {
                views[slot] = host.createView(appContext, id, info)
            } catch (error: RuntimeException) {
                Log.e(TAG, "host view creation failed for ${slot.name}", error)
                unsupportedSlots += slot
            }
        }
        publishSnapshot()
    }
    /**
     * Auto-bind any missing slots silently. Called on every start() after restoreBoundViews().
     * Only binds if: provider is installed AND bindAppWidgetIdIfAllowed succeeds (grant already given).
     * If grant was never given, this is a no-op (no UI prompt, no crash).
     * User only needs to bind manually ONCE (via DiagActivity) if auto-bind is not allowed.
     */
    private fun autoBindMissing() {
        VietMapWidgetSlot.entries.forEach { slot ->
            if (prefs.widgetId(slot) != null) return@forEach  // already bound
            if (slot in unsupportedSlots) return@forEach
            val provider = providerInfo(slot) ?: return@forEach  // VietMap not installed
            val appWidgetId = try {
                host.allocateAppWidgetId()
            } catch (_: RuntimeException) { return@forEach }
            val bound = try {
                manager.bindAppWidgetIdIfAllowed(appWidgetId, provider.provider)
            } catch (_: SecurityException) { false }
            if (bound) {
                prefs.saveWidgetId(slot, appWidgetId, providerVersion())
                slotsById[appWidgetId] = slot
                try {
                    val info = manager.getAppWidgetInfo(appWidgetId)
                    if (info != null) views[slot] = host.createView(appContext, appWidgetId, info)
                } catch (_: RuntimeException) {}
                Log.i(TAG, "auto-bound ${slot.name} → id=$appWidgetId")
            } else {
                // Grant not given — clean up allocated ID, user must bind manually once
                try {
                    host.deleteAppWidgetId(appWidgetId)
                } catch (error: RuntimeException) {
                    Log.w(TAG, "allocated widget ID cleanup failed", error)
                }
            }
        }
    }
    private fun clearRuntimeValues() {
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
    private fun setUnavailable(reason: VietMapWidgetUnavailableReason) {
        published = unavailable(reason)
        dispatchToListeners(published)
    }
    // --- Utility ---
    private fun providerInfo(slot: VietMapWidgetSlot): AppWidgetProviderInfo? =
        manager.installedProviders.firstOrNull { it.provider == slot.component }
    private fun providerVersion(): String? = try {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            appContext.packageManager.getPackageInfo(VIETMAP_PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(VIETMAP_PACKAGE, 0)
        }
        info.versionName
    } catch (_: PackageManager.NameNotFoundException) { null }
    private fun deleteAllocatedId(appWidgetId: Int) {
        try {
            host.deleteAppWidgetId(appWidgetId)
        } catch (_: IllegalArgumentException) {
            Log.w(TAG, "widget ID was already removed")
        } catch (error: RuntimeException) {
            Log.e(TAG, "widget ID removal failed", error)
        }
        slotsById.remove(appWidgetId)
        views.entries.removeAll { it.value.appWidgetId == appWidgetId }
    }
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
    // --- Generation-bound listener entry ---
    private data class ListenerEntry(
        val callback: (VietMapWidgetSnapshot) -> Unit,
        val generation: Long,
    )
    companion object {
        private const val TAG = "VietMapWidget"
        private const val HOST_ID = 0x564D
        private const val UPDATE_DEBOUNCE_MS = 120L
        private const val FRESHNESS_TICK_MS = 1_000L
        @Volatile private var instance: VietMapWidgetBridge? = null
        fun get(context: Context): VietMapWidgetBridge = instance ?: synchronized(this) {
            instance ?: VietMapWidgetBridge(context).also { instance = it }
        }
        private fun unavailable(reason: VietMapWidgetUnavailableReason) = VietMapWidgetSnapshot(
            currentSpeedKph = null,
            speedLimitKph = null,
            alerts = emptyList(),
            providerVersion = null,
            updatedAtElapsedMs = null,
            freshness = VietMapWidgetFreshness.UNAVAILABLE,
            reason = reason,
        )
    }
}
