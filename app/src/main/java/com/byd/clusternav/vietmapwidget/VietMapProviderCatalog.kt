package com.byd.clusternav.vietmapwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.system.PackageQueries

/**
 * Cache "gói VietMap có cài / provider nào / phiên bản nào" cho [VietMapWidgetBridge] — BG-31.
 *
 * `AppWidgetManager.installedProviders` trả CẢ danh sách provider hệ thống qua binder, `getPackageInfo` thêm một
 * binder nữa; trước 2026-09-25 bridge hỏi 4 lần mỗi giây trên main. Ở đây hỏi lại chỉ khi (a) quá
 * [VietMapWidgetTickPolicy.PROVIDER_CACHE_TTL_MS], (b) nhận `PACKAGE_ADDED/REMOVED/REPLACED` của đúng gói
 * VietMap, hoặc (c) caller ép `force` (người dùng đang bind ở màn chẩn đoán — đọc tươi).
 *
 * Chỉ chạm từ main thread (cùng kỷ luật với mọi state của bridge).
 */
internal class VietMapProviderCatalog(
    private val appContext: Context,
    private val manager: AppWidgetManager,
    private val packageName: String,
    /** Gọi trên main sau khi cache được làm mới vì gói đổi (cài/gỡ/cập nhật). */
    private val onPackageChanged: (action: String?) -> Unit,
) {
    private var infos: Map<ComponentName, AppWidgetProviderInfo> = emptyMap()
    private var version: String? = null
    private var loadedAtMs: Long? = null
    private var registered = false

    fun info(component: ComponentName): AppWidgetProviderInfo? {
        refresh()
        return infos[component]
    }

    /** `null` = gói không cài. */
    fun version(): String? {
        refresh()
        return version
    }

    fun refresh(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && !VietMapWidgetTickPolicy.providerCacheStale(now, loadedAtMs)) return
        infos = manager.installedProviders
            .filter { it.provider.packageName == packageName }
            .associateBy { it.provider }
        version = PackageQueries.packageInfo(appContext.packageManager, packageName)?.versionName
        loadedAtMs = now
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.data?.schemeSpecificPart != packageName) return
            Log.i(TAG, "gói $packageName ${intent.action} → làm mới cache provider")
            refresh(force = true)
            onPackageChanged(intent.action)
        }
    }

    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        runCatching {
            // API 33+: cờ export bắt buộc khi targetSdk ≥ 34; broadcast gói là của hệ thống nên NOT_EXPORTED vẫn nhận.
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }
            registered = true
        }.onFailure { Log.w(TAG, "đăng ký receiver gói thất bại (cache chỉ làm mới theo TTL)", it) }
    }

    fun unregister() {
        if (!registered) return
        registered = false
        runCatching { appContext.unregisterReceiver(receiver) }
            .onFailure { Log.w(TAG, "huỷ receiver gói thất bại", it) }
    }

    private companion object {
        const val TAG = "VietMapWidget"
    }
}
