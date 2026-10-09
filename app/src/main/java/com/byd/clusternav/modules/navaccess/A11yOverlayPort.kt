package com.byd.clusternav.modules.navaccess

import android.accessibilityservice.AccessibilityService
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 2.98 · R7 — cửa duy nhất tới dịch vụ trợ năng ĐANG NỐI để dựng cửa sổ `TYPE_ACCESSIBILITY_OVERLAY` (lớp 30, trên thanh
 * điều hướng 23 và thanh trạng thái 17 — [ĐO nguồn `WindowManagerPolicy.getWindowLayerFromTypeLw`], evidence
 * `oncar-freeform-waze-2026-10-09.md` §4). Loại cửa sổ này CHỈ thêm được bằng `WindowManager` của chính dịch vụ.
 *
 * Không bật trợ năng ở đâu cả (không ghi `settings`): dịch vụ chưa nối ⇒ [service] `null` ⇒ không có lớp che, phần đặt
 * freeform vẫn chạy (thanh hệ thống hiện). Dịch vụ rời ⇒ báo [onDetach] để bên dựng gỡ tham chiếu view (cửa sổ chết theo token
 * của dịch vụ). Luồng chính.
 */
object A11yOverlayPort {

    @Volatile var service: AccessibilityService? = null
        private set

    private val detachListeners = CopyOnWriteArrayList<() -> Unit>()
    private val attachListeners = CopyOnWriteArrayList<() -> Unit>()

    /** Dịch vụ (nối lại — `AccessibilityRebind` tắt/bật dịch vụ) ⇒ báo [onAttach] để bên dựng che lại (cửa sổ cũ đã chết theo token). */
    fun attach(s: AccessibilityService) {
        service = s
        attachListeners.forEach { runCatching { it() } }
    }

    fun detach(s: AccessibilityService) {
        if (service !== s) return
        service = null
        detachListeners.forEach { runCatching { it() } }
    }

    /** Đăng ký một lần (idempotent theo tham chiếu). */
    fun onDetach(listener: () -> Unit) { if (listener !in detachListeners) detachListeners += listener }

    /** Đăng ký một lần (idempotent theo tham chiếu). Chạy trên luồng của `onServiceConnected` (luồng chính). */
    fun onAttach(listener: () -> Unit) { if (listener !in attachListeners) attachListeners += listener }
}
