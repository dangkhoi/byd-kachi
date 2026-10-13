package com.byd.clusternav.launcher

import java.util.concurrent.ConcurrentHashMap

/**
 * ═══ 3.01 POLISH2 — TRẠNG THÁI LẠC QUAN của đèn vừa chạm (thuần `:core`) ═════════════════════════════════════════════════
 *
 * Owner 13/10 (máy ảo): chạm đèn ban ngày ⇒ lệnh đi (`setDayTimeLightState [2]`) nhưng bảng Đèn/3D vẫn hiện SÁNG tới khi xe báo —
 * và nếu số đọc cũ tới trước thì bật lại. Như chấm kính ([ActionMatrixCompact] `pending`): giữ đích vừa gửi tới khi SỐ ĐỌC xác
 * nhận ĐÚNG đích đó, hoặc hết [HOLD_MS] (khi ấy số đọc thắng — xe không làm theo thì màn nói thật). Một bộ dùng chung cho bảng Đèn
 * 2D và xe 3D (cùng một chiếc xe — chạm ở chỗ này thì chỗ kia cũng thấy).
 */
class LampHold(private val holdMs: Long = HOLD_MS) {

    private val held = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    /** Vừa gửi [on] cho nút [id] lúc [now] (ms, đồng hồ đơn điệu). */
    fun put(id: String, on: Boolean, now: Long) { held[id] = on to now + holdMs }

    /**
     * Giá trị lạc quan CÒN hiệu lực của [id] trước số đọc [read] (`null` = không có số đọc): số đọc trùng đích ⇒ thả (đã xác
     * nhận); quá hạn ⇒ thả. Trả `null` ⇒ người gọi dùng số đọc như thường.
     */
    fun held(id: String, read: Boolean?, now: Long): Boolean? {
        val h = held[id] ?: return null
        if (now >= h.second || read == h.first) { held.remove(id, h); return null }
        return h.first
    }

    /** Hạn còn lại (ms) của lần giữ dài nhất — để người vẽ hẹn MỘT lần vẽ lại lúc hết hạn; `0` = không giữ gì. */
    fun remaining(now: Long): Long = held.values.maxOfOrNull { (it.second - now).coerceAtLeast(0) } ?: 0L

    companion object {
        /** ~3 s: đủ cho một nhịp đổ số trạng thái xe (đèn đổi gần như tức thì trên bus). */
        const val HOLD_MS = 3_000L
        val shared = LampHold()
    }
}
