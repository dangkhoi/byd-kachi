package com.byd.clusternav.vietmapwidget

/**
 * Nhịp poll tươi của [VietMapWidgetBridge] — THUẦN (không Android), test off-device.
 *
 * BG-31 (`docs/diagnostics/perf-inventory-2026-09-25.md`): trước 2026-09-25 `freshnessTick` chạy 1 Hz suốt đời tiến
 * trình, mỗi nhịp ≈7 binder trên main (`installedProviders`×3 + `getAppWidgetInfo`×3 + `getPackageInfo`) — kể cả
 * khi VietMap không cài / badge tắt / chưa bind widget nào, tức là không ai dùng kết quả.
 *
 * Luật: **1 Hz chỉ khi CẢ BA điều kiện đủ** (gói cài · người dùng bật · có widget đã bind) — đó là hành vi hiện
 * trường, không đổi. Thiếu một điều kiện ⇒ ngủ [SLOW_MS] chỉ để kiểm lại điều kiện (và vẫn publish để hết-tươi /
 * mất-bind được phản ánh trong ≤ 10 s, khi vốn không có dữ liệu gì để tươi).
 */
object VietMapWidgetTickPolicy {
    /** Nhịp hiện trường khi có người dùng — GIỮ NGUYÊN 1 000 ms (freshness TTL tính theo giây). */
    const val FAST_MS = 1_000L

    /** Nhịp ngủ khi thiếu điều kiện: chỉ để kiểm lại điều kiện. */
    const val SLOW_MS = 10_000L

    /** Cache `installedProviders` + `getPackageInfo`: làm mới theo TTL này hoặc khi nhận `PACKAGE_ADDED/REMOVED/REPLACED`. */
    const val PROVIDER_CACHE_TTL_MS = 60_000L

    /**
     * @param installed gói VietMap có cài (cache PackageManager)
     * @param enabled   có người dùng kết quả (badge bật theo Prefs, hoặc màn chẩn đoán đang mở)
     * @param bound     có ít nhất một widget id đã bind/khôi phục
     */
    fun tickIntervalMs(installed: Boolean, enabled: Boolean, bound: Boolean): Long =
        if (installed && enabled && bound) FAST_MS else SLOW_MS

    /** `true` khi chưa nạp hoặc đã quá [PROVIDER_CACHE_TTL_MS]. */
    fun providerCacheStale(nowMs: Long, loadedAtMs: Long?): Boolean =
        loadedAtMs == null || nowMs - loadedAtMs >= PROVIDER_CACHE_TTL_MS
}
