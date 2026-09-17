package com.byd.clusternav.launcher

/**
 * Cổng **gửi lại** cho một thông điệp định kỳ mang cùng nội dung (K8, 1.70) — thuần, test bằng đồng hồ giả.
 *
 * [ĐO xe 2026-09-17] bong bóng VietMap nhận `VM_BUBBLE_POS x=… y=…` **mỗi 2 s** (nhịp làm tươi của
 * `FloatingBubbleService`) dù toạ độ không đổi từ lúc mở app — một broadcast + một dòng log mỗi 2 s cho một
 * việc đã xong. Luật: cùng nội dung thì chỉ gửi lại sau [minRepeatMs]; nội dung ĐỔI thì gửi ngay.
 */
class ResendGate(private val minRepeatMs: Long) {
    private var lastPayload: Any? = null
    private var lastAtMs = Long.MIN_VALUE

    /** `true` = nên gửi bây giờ (và ghi nhận đã gửi). */
    fun shouldSend(nowMs: Long, payload: Any): Boolean {
        val same = payload == lastPayload
        if (same && nowMs - lastAtMs < minRepeatMs) return false
        lastPayload = payload
        lastAtMs = nowMs
        return true
    }
}
