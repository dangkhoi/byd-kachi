package com.byd.clusternav.system

/**
 * Có bật `StrictMode` (chỉ `penaltyLog`) cho tiến trình này không — luật THUẦN, tách khỏi `KachiApplication` để
 * khoá off-device (hardening 2026-09-25 · spec R4(b)).
 *
 * Vì sao KHÔNG chỉ `BuildConfig.DEBUG`: `vehicleTest` = `initWith(release)` + `isDebuggable = true`
 * (`app/build.gradle.kts`), tức `DEBUG == true` mà **chạy trên xe thật**. `penaltyLog` không đổi hành vi, nhưng
 * mỗi vi phạm là ~14 dòng logcat đi thẳng vào tệp usage trên thẻ (`KachiLog`) — đúng thứ PERF 09-16 đang phải
 * giảm. Nên chỉ build type `debug` (máy ảo `clusternav10`) mới bật; muốn đo trên xe thì thêm công tắc ẩn trong
 * Prefs (audit §7), không gắn theo build type.
 */
object StrictModeGate {
    const val DEBUG_BUILD_TYPE = "debug"

    fun enabled(isDebuggable: Boolean, buildType: String): Boolean =
        isDebuggable && buildType == DEBUG_BUILD_TYPE
}
