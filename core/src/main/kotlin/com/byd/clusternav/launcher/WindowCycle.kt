package com.byd.clusternav.launcher

/**
 * ═══ 3.01 R22·C — CHẠM CHẤM KÍNH = MỘT VÒNG đóng → 50 % → mở hẳn → đóng (thuần `:core`) ═══════════════════════════
 *
 * Owner 13/10: *"nhấn vô các hình tròn cho nó mở đóng luôn… khi click vô từng chấm tròn thì mở 50%, nhấn lần 2 mở hết,
 * nhấn lần 3 thì đóng hết"*. Công tắc chế độ `Mở hẳn | Hé` (R22·B) GỠ — một cú chạm tự biết bước kế.
 *
 * ## Bước kế quyết từ MỨC ĐANG CÓ, không đếm số lần chạm
 * Đếm lần chạm sẽ lệch ngay khi kính đổi bằng đường khác (công tắc cửa, giọng nói, app BYD). Nên bước kế đọc từ mức THẬT
 * (số đọc `window_*` %, hoặc đích lạc quan vừa gửi khi xe chưa kịp báo — xem `ActionMatrixCompact`):
 *  • chưa đọc được (`null`) hoặc đóng (≤ 0) ⇒ [HALF];
 *  • 0 < mức < [HALF] (vd hé 35 %) ⇒ [HALF] — lên nấc gần nhất, không nhảy cóc qua 50 %;
 *  • [HALF] ≤ mức < [FULL] ⇒ [FULL];
 *  • mở hẳn (≥ [FULL]) ⇒ 0 (đóng).
 *
 * ## Ghi bằng nút SẴN CÓ, theo DỮ LIỆU mức của nút
 * [plan] chọn trong các ô nút của MỘT vật ([ActionMatrix.Row.slots]) ô có [GroupActionCell.level] đúng bằng đích và BẬT nó
 * (`win_half_*` 50 · `win_*` 100); đích 0 ⇒ TẮT ô mức cao nhất (`win_*` — TOGGLE tắt = đóng, [ĐO xe 09-22]). Không tên
 * nút nào trong mã; nút đi đúng đường ghi của ô (`ControlTileFactory`), không đường HAL thứ hai.
 */
object WindowCycle {

    const val HALF = 50
    const val FULL = 100

    /** Đích (%) của cú chạm kế tiếp khi kính đang ở [level] (%; `null` = chưa đọc được). */
    fun next(level: Int?): Int = when {
        level == null || level <= 0 -> HALF
        level >= FULL -> 0
        level >= HALF -> FULL
        else -> HALF
    }

    /**
     * 3.1.5 — CHẠM ĐÚP mở hẳn (owner 14/10 *"double click thì mở all được ko"*). Cú chạm ĐƠN không bị hoãn (gửi ngay bước [next]);
     * cú chạm thứ hai lên CÙNG kính trong thời hạn chạm đúp ([isDouble]) ⇒ đích [FULL] thay cho bước kế của vòng. Đích lạc quan
     * [pending] (cú trước vừa gửi) đã là [FULL] ⇒ `null` = không gửi thêm gì (kính đã/đang mở hẳn). Kết quả cuối luôn là mở hẳn: từ
     * đóng ⇒ 50 rồi 100 (xe nhận thêm đúng một lệnh); từ 50 ⇒ 100 rồi thôi; từ mở hẳn ⇒ cú đầu gửi đóng, cú hai trả về mở hẳn.
     */
    fun tapTarget(level: Int?, double: Boolean, pending: Int?): Int? = when {
        !double -> next(level)
        pending == FULL -> null
        else -> FULL
    }

    /** Cú chạm lúc [now] (ms) có phải cú thứ hai của một chạm đúp (cú trước lúc [last], thời hạn [timeoutMs]). */
    fun isDouble(last: Long?, now: Long, timeoutMs: Long): Boolean = last != null && now >= last && now - last <= timeoutMs

    /** Một lệnh: bật/tắt ô [action]. */
    data class Step(val action: GroupActionCell, val on: Boolean)

    /** Lệnh đưa vật có các ô [slots] tới [target] %; `null` = vật không có ô cho mức đó (không bịa lệnh). */
    fun plan(slots: List<GroupActionCell?>, target: Int): Step? {
        val leveled = slots.filterNotNull().filter { it.level != null }
        if (target <= 0) return leveled.filter { it.level!! > 0 }.maxByOrNull { it.level!! }?.let { Step(it, false) }
        return leveled.firstOrNull { it.level == target }?.let { Step(it, true) }
    }

    /** Mức % từ ô XEM: chưa đọc / không phải phân số ⇒ `null`. [fraction] 0..1 (`KachiMeter.fractionOf` ở `:app`). */
    fun levelOf(available: Boolean, fraction: Float?): Int? =
        if (!available || fraction == null) null else Math.round(fraction.coerceIn(0f, 1f) * FULL)
}
