package com.byd.clusternav.launcher

import java.util.concurrent.ConcurrentHashMap

/**
 * ═══ 3.01 POLISH2 — TRẠNG THÁI LẠC QUAN của đèn vừa chạm (thuần `:core`) ═════════════════════════════════════════════════
 *
 * Owner 13/10 (máy ảo): chạm đèn ban ngày ⇒ lệnh đi (`setDayTimeLightState [2]`) nhưng bảng Đèn/3D vẫn hiện SÁNG tới khi xe báo —
 * và nếu số đọc cũ tới trước thì bật lại. Như chấm kính ([ActionMatrixCompact] `pending`): giữ đích vừa gửi tới khi SỐ ĐỌC xác
 * nhận ĐÚNG đích đó, hoặc hết [HOLD_MS] (khi ấy số đọc thắng — xe không làm theo thì màn nói thật). Một bộ dùng chung cho bảng Đèn
 * 2D và xe 3D (cùng một chiếc xe — chạm ở chỗ này thì chỗ kia cũng thấy).
 *
 * 3.1.6 · R32 — cốp vào bộ này với hạn RIÊNG của dòng ([ControlDef.confirmMs], [holdMsOf]): số đọc trùng đích ⇒ thả sớm; hết hạn ⇒
 * số đọc thắng — người lái đóng cốp bằng nút vật lý thì màn về "đóng" ở lượt đọc kế (owner 15/10, ca xung đột).
 */
class LampHold(private val holdMs: Long = HOLD_MS) {

    private val held = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    /** Vừa gửi [on] cho nút [id] lúc [now] (ms, đồng hồ đơn điệu). Hạn = [holdMsOf] (cửa sổ xác nhận của dòng, nếu có). */
    fun put(id: String, on: Boolean, now: Long) { held[id] = on to now + holdMsOf(id) }

    /**
     * 3.1.6 · R32 — hạn giữ của nút [id]: [ControlDef.confirmMs] của dòng registry (cốp 10 s — [ĐO] đóng ~5,8 s mới đọc 0), không
     * khai ⇒ hạn của bộ này. DỮ LIỆU của dòng, không `if (id == "trunk")`.
     */
    fun holdMsOf(id: String): Long = ControlRegistry.byId(id)?.confirmMs ?: holdMs

    /** Bỏ đích đang giữ của [id] (lệnh bị cổng chặn / ghi hỏng ⇒ không có gì để "chờ xác nhận"). */
    fun drop(id: String) { held.remove(id) }

    /**
     * Giá trị lạc quan CÒN hiệu lực của [id] trước số đọc [read] (`null` = không có số đọc): số đọc trùng đích ⇒ thả (đã xác
     * nhận); quá hạn ⇒ thả. Trả `null` ⇒ người gọi dùng số đọc như thường.
     */
    fun held(id: String, read: Boolean?, now: Long): Boolean? {
        val h = held[id] ?: return null
        if (now >= h.second || read == h.first) { held.remove(id, h); return null }
        return h.first
    }

    /**
     * 3.1.6 · R32 — mức (0/1) mà ô HAI MỨC của nút [id] nên hiện trước số đọc [read] (0/1, `null` = chưa đọc được): đích đang giữ
     * (chưa xác nhận, chưa hết hạn) thắng; không thì đúng số đọc. Cùng luật [held] — ô cốp, xe 3D và bảng cửa nói một điều.
     */
    fun shown(id: String, read: Int?, now: Long): Int? = held(id, read?.let { it > 0 }, now)?.let { if (it) 1 else 0 } ?: read

    /** Hạn còn lại (ms) của lần giữ dài nhất — để người vẽ hẹn MỘT lần vẽ lại lúc hết hạn; `0` = không giữ gì. */
    fun remaining(now: Long): Long = held.values.maxOfOrNull { (it.second - now).coerceAtLeast(0) } ?: 0L

    companion object {
        /** ~3 s: đủ cho một nhịp đổ số trạng thái xe (đèn đổi gần như tức thì trên bus). */
        const val HOLD_MS = 3_000L
        val shared = LampHold()
    }
}
