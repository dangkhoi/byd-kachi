package com.byd.clusternav.launcher

import android.app.Activity
import android.os.Build
import java.util.WeakHashMap

/**
 * Cầu predictive-back cho API 33+ (tách khỏi `KachiHomeActivity.kt` 2026-09-25, trần 500 dòng). Activity chỉ gọi
 * [attach]/[detach]; callback nền tảng (`android.window.*`) được giữ ở đây theo Activity và lớp `android.window.*`
 * chỉ được nạp trong nhánh `SDK_INT >= 33`; trên xe (API 29) không bao giờ chạm tới.
 *
 * ⚠ [SOÁT Pass 1 · 2026-09-25] `WeakHashMap` ở đây **không** tự dọn được: GIÁ TRỊ (callback) giữ `onBack`, mà
 * `onBack` của chỗ gọi là `{ onBackPressed() }` — tức một tham chiếu MẠNH ngược về Activity (khoá). Khoá còn được
 * với tới thì mục không bao giờ rụng. Vậy đường thu hồi DUY NHẤT là [detach] trong `onDestroy` (đang có, và
 * `onDestroy` luôn chạy trước khi Activity được bỏ) — `WeakHashMap` chỉ là lưới thứ hai cho ca chỗ gọi quên
 * `detach` **và** không tự trỏ về Activity. Đừng đọc nó thành "khỏi cần detach".
 *
 * Vì sao cần: từ API 33 (mặc định BẬT khi targetSdk ≥ 36) cử chỉ Back KHÔNG còn gọi `onBackPressed`; Activity kế
 * thừa `android.app.Activity` thuần nên không có `onBackPressedDispatcher` AndroidX ⇒ đăng ký thủ công, cùng đổ về
 * một đường `onBackPressed`.
 */
internal object PredictiveBack {
    private val callbacks = WeakHashMap<Activity, Any>()

    fun attach(a: Activity, onBack: () -> Unit) {
        if (Build.VERSION.SDK_INT < 33) return
        callbacks[a] = Api33.register(a, onBack)
    }

    fun detach(a: Activity) {
        if (Build.VERSION.SDK_INT < 33) return
        callbacks.remove(a)?.let { Api33.unregister(a, it) }
    }

    @androidx.annotation.RequiresApi(33)
    private object Api33 {
        fun register(a: Activity, onBack: () -> Unit): android.window.OnBackInvokedCallback {
            val cb = android.window.OnBackInvokedCallback { onBack() }
            a.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb,
            )
            return cb
        }

        fun unregister(a: Activity, cb: Any) {
            a.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(cb as android.window.OnBackInvokedCallback)
        }
    }
}
