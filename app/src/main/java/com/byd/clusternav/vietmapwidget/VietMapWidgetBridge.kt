package com.byd.clusternav.vietmapwidget

import com.byd.clusternav.Prefs
import com.byd.clusternav.system.PackageQueries
import com.byd.clusternav.navigation.NavApps
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet
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
    internal val manager = AppWidgetManager.getInstance(appContext)
    internal val prefs = VietMapWidgetPrefs(appContext)
    private val main = Handler(Looper.getMainLooper())
    internal val extraction = VietMapWidgetExtraction(appContext)
    private val host = VietMapAppWidgetHost(appContext, HOST_ID, { listenerGeneration }, ::onHostViewUpdated)
    private val owners = linkedSetOf<VietMapWidgetOwner>()
    internal val listeners = CopyOnWriteArraySet<ListenerEntry>()
    internal val views = mutableMapOf<VietMapWidgetSlot, AppWidgetHostView>()
    internal val slotsById = mutableMapOf<Int, VietMapWidgetSlot>()
    internal val unsupportedSlots = mutableSetOf<VietMapWidgetSlot>()
    /** Generation counters for listener callback binding — incremented on stop/restart. */
    @Volatile internal var listenerGeneration = 0L
    /** Per-provider snapshots — fully independent. */
    internal var speedSnapshot = VietMapProviderSnapshot<VietMapWidgetRawValues>(
        slot = VietMapWidgetSlot.SPEED_LIMIT, values = null,
        updatedAtElapsedMs = null, freshness = VietMapWidgetFreshness.UNAVAILABLE,
        reason = VietMapWidgetUnavailableReason.NOT_BOUND, generation = 0L,
    )
    internal var alertsSnapshot = VietMapProviderSnapshot<VietMapWidgetRawValues>(
        slot = VietMapWidgetSlot.ALERTS, values = null,
        updatedAtElapsedMs = null, freshness = VietMapWidgetFreshness.UNAVAILABLE,
        reason = VietMapWidgetUnavailableReason.NOT_BOUND, generation = 0L,
    )
    internal var alertFullSnapshot = VietMapProviderSnapshot<VietMapWidgetRawValues>(
        slot = VietMapWidgetSlot.ALERT_FULL, values = null,
        updatedAtElapsedMs = null, freshness = VietMapWidgetFreshness.UNAVAILABLE,
        reason = VietMapWidgetUnavailableReason.NOT_BOUND, generation = 0L,
    )
    private var listening = false
    @Volatile internal var published = unavailable(VietMapWidgetUnavailableReason.NOT_BOUND)
    private val publishDebounced = Runnable { publishSnapshot() }
    // BG-31 (2026-09-25): nhịp do [VietMapWidgetTickPolicy] quyết — 1 Hz chỉ khi (gói cài ∧ có người dùng ∧ có widget
    // bind); thiếu một điều kiện ⇒ 10 s. Trước đây 1 Hz vô điều kiện suốt đời tiến trình (~7 binder/s trên main).
    private val freshnessTick = object : Runnable {
        override fun run() {
            if (!listening) return
            publishSnapshot()
            main.postDelayed(this, tickIntervalMs())
        }
    }
    private fun tickIntervalMs(): Long = VietMapWidgetTickPolicy.tickIntervalMs(
        installed = providerVersion() != null,
        enabled = consumerEnabled(),
        bound = slotsById.isNotEmpty(),
    )
    /**
     * Có ai dùng snapshot không: màn chẩn đoán đang mở, hoặc badge tốc độ bật — đúng ba pref mà
     * `NavigationSpeedSignOwner.syncFromPrefs` nạp vào coordinator (master + ít nhất một cổng ra).
     */
    private fun consumerEnabled(): Boolean =
        VietMapWidgetOwner.DIAGNOSTICS in owners ||
            (Prefs.enabled(appContext) && (Prefs.lane(appContext) || Prefs.hud(appContext)))
    // BG-31: cache provider/version (TTL + receiver gói) — xem [VietMapProviderCatalog]. Chỉ chạm trên main.
    private val catalog = VietMapProviderCatalog(appContext, manager, VIETMAP_PACKAGE) { _ ->
        if (!listening) return@VietMapProviderCatalog
        publishSnapshot()
        main.removeCallbacks(freshnessTick)   // đánh giá lại nhịp ngay, không chờ hết 10 s
        main.post(freshnessTick)
    }
    // --- Lifecycle ---
    fun start(owner: VietMapWidgetOwner) = onMain {
        owners.add(owner)
        if (listening) return@onMain
        listenerGeneration++
        listening = true
        try {
            catalog.refresh(force = true)
            catalog.register()
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
        catalog.unregister()
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
    fun bindingStatuses(): List<VietMapWidgetBindingStatus> {
        catalog.refresh(force = true)   // người dùng đang hỏi (Diag/bind) — đọc tươi, không tin cache
        return VietMapWidgetSlot.entries.map { slot ->
            val id = prefs.widgetId(slot)
            val available = providerInfo(slot) != null
            val bound = id != null && manager.getAppWidgetInfo(id)?.provider == slot.component
            VietMapWidgetBindingStatus(slot, id, available, bound)
        }
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
    /**
     * ⚠ [callbackGeneration] đến TỪ [VietMapAppWidgetHost] (chụp trong `updateAppWidget`, trước lượt post).
     * Bản trước đọc `listenerGeneration` NGAY TRONG block này rồi so nó với chính nó ba dòng sau ⇒ điều kiện
     * `callbackGeneration != listenerGeneration` KHÔNG BAO GIỜ đúng (mã chết mang hình dạng lá chắn) ⇒ một
     * lượt RemoteViews của phiên nghe CŨ, đến sau `stop()`/`start()`, vẫn ghi đè snapshot của phiên mới.
     */
    private fun onHostViewUpdated(appWidgetId: Int, view: AppWidgetHostView, callbackGeneration: Long) = onMain {
        if (!listening) return@onMain
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
                    alertsSnapshot = alertsSnapshot.copy(
                        values = extracted,
                        updatedAtElapsedMs = now,
                        generation = callbackGeneration,
                    )
                    // Hash ở luồng `widget-hash`, kết quả tự về (KHÔNG dựng một Thread chờ cho MỖI lượt cập nhật —
                    // [SOÁT Pass 1 · 2026-09-25 · P2], xem KDoc `VietMapWidgetExtraction.hashAlerts`). Vẫn chỉ trộn
                    // vào snapshot khi thế hệ chưa đổi, và vẫn trộn trên main.
                    extraction.hashAlerts(view) { h1, h2 ->
                        main.post {
                            if (listenerGeneration != callbackGeneration) return@post
                            val current = alertsSnapshot.values ?: return@post
                            if (current.firstAlertImageHash == h1 && current.secondAlertImageHash == h2) return@post
                            alertsSnapshot = alertsSnapshot.copy(
                                values = current.copy(firstAlertImageHash = h1, secondAlertImageHash = h2),
                            )
                            schedulePublish()
                        }
                    }
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
    // --- Snapshot publishing / clear: xem VietMapWidgetBridgePublish.kt (hàm mở rộng, tách theo VAI — DEBT-500) ---
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
    // --- Utility ---
    internal fun providerInfo(slot: VietMapWidgetSlot): AppWidgetProviderInfo? = catalog.info(slot.component)
    // D3(a): rẽ nhánh API 33 + bắt NameNotFound nay nằm ở một cửa PackageQueries (trước đây tệp này tự rẽ — bản gốc
    // của khuôn đó). Gói không cài ⇒ null. BG-31: đọc qua cache (TTL / receiver gói).
    internal fun providerVersion(): String? = catalog.version()
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
    internal data class ListenerEntry(
        val callback: (VietMapWidgetSnapshot) -> Unit,
        val generation: Long,
    )
    companion object {
        internal const val TAG = "VietMapWidget"
        private const val HOST_ID = 0x564D
        private const val UPDATE_DEBOUNCE_MS = 120L
        @Volatile private var instance: VietMapWidgetBridge? = null
        fun get(context: Context): VietMapWidgetBridge = instance ?: synchronized(this) {
            instance ?: VietMapWidgetBridge(context).also { instance = it }
        }
        internal fun unavailable(reason: VietMapWidgetUnavailableReason) = VietMapWidgetSnapshot(
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
