package com.byd.clusternav.launcher

/**
 * ═══ 3.01 R25 — điểm chạm 3D → ô nút SẴN CÓ + chấm trạng thái (thuần `:core`) ═══════════════════════════════════
 *
 * Không đường ghi thứ hai: điểm chạm chọn các [GroupActionCell] của nhóm (cùng nguồn với bảng Kính/Đèn/Cửa 2D), chấm
 * dùng CÙNG ngôn ngữ [CarDots] (vòng đóng · quạt mở một phần · chấm đặc mở hẳn · vòng đứt chưa đọc), và cú chạm kính
 * dùng CÙNG vòng [WindowCycle] (đóng → 50 % → mở hẳn → đóng) với bảng Kính 2D.
 */
object Car3dHotspots {

    /** Ô nút điều khiển vật + ô XEM nói trạng thái của nó (`null` = chưa có đường đọc). */
    data class Resolved(val actions: List<GroupActionCell>, val cell: GroupCell?)

    sealed class Tap {
        /** Đặt TƯỜNG MINH ô [action] bật/tắt (vòng kính). */
        data class Set(val action: GroupActionCell, val on: Boolean) : Tap()
        /** Bấm ô [action] như chạm ô thật (công tắc cốp/đèn tự đảo theo trạng thái nó giữ). */
        data class Press(val action: GroupActionCell) : Tap()
        /** Điểm chỉ-xem (cửa) hoặc nhóm không có ô cho vật này. */
        object None : Tap()
    }

    /**
     * Ô nút của vật [h] trong model nhóm [m]: khớp MÃ nút, hoặc khớp khoá vật đọc từ icon ([GroupActionMatrix.placeOf] —
     * `ic-car-top-window-lf` ⇒ `lf`, `ic-car-top-trunk` ⇒ `trunk`). Ô xem = [Car3dHotspot.read] hoặc `readKey` nút đầu.
     */
    fun resolve(m: GroupBoardModel, h: Car3dHotspot): Resolved {
        val actions = if (h.target.isEmpty()) emptyList()
        else m.actions.filter { it.id == h.target || GroupActionMatrix.placeOf(it.icon)?.key == h.target }
        val cells = m.cells.associateBy { it.id }
        val key = h.read ?: actions.firstNotNullOfOrNull { a -> a.readKey.takeIf { it.isNotEmpty() } }
        return Resolved(actions, key?.let { cells[it] })
    }

    /**
     * Cú chạm làm gì. Vật có ô MỨC ([GroupActionCell.level]) ⇒ bước kế của [WindowCycle] từ mức đọc được [level] (%), hoặc
     * mở hẳn khi [double] (cú thứ hai của chạm đúp — [pending] = đích lạc quan cú trước đã gửi); không thì bấm ô đầu tiên.
     */
    fun tap(r: Resolved, level: Int?, double: Boolean = false, pending: Int? = null): Tap {
        if (r.actions.isEmpty()) return Tap.None
        if (r.actions.any { it.level != null }) {
            // Chạm đúp ⇒ mở hẳn ([WindowCycle.tapTarget]); đích vừa gửi đã là mở hẳn ⇒ không bước nào.
            val target = WindowCycle.tapTarget(level, double, pending) ?: return Tap.None
            return WindowCycle.plan(r.actions, target)?.let { Tap.Set(it.action, it.on) } ?: Tap.None
        }
        return Tap.Press(r.actions.first())
    }

    /** Chấm của vật: [fraction] = phân số `%` đọc được (`KachiMeter.fractionOf` ở `:app`), `null` nếu không phải `%`. */
    fun dot(r: Resolved, fraction: Float?): CarDots.Dot {
        val c = r.cell ?: return CarDots.ofCell(false, null, GroupTone.NEUTRAL)
        return CarDots.ofCell(c.available, fraction, c.tone)
    }

    /** Vật có đang "khác nghỉ" (cửa mở / đèn sáng) — cho [Car3dVariant]. Chưa đọc ⇒ coi như nghỉ (hiện bản đóng). */
    fun active(cell: GroupCell?): Boolean = cell != null && cell.available && cell.tone != GroupTone.NEUTRAL

    /**
     * Biến thể có ô đọc [read] (một hoặc nhiều mã nối `|`) đang "khác nghỉ": BẤT KỲ ô nào [active]. Rỗng ⇒ không bao giờ.
     * [cell] tra ô theo mã trong nhóm của biến thể. Mã `cmd:<nút>` ([Car3dVariant.CMD]) = lệnh cuối Kachi đã gửi cho nút đó
     * ([sent] — vật không cảm biến như cốp); không bao giờ coi là SỐ ĐỌC.
     */
    fun activeAny(read: String, sent: (String) -> Boolean = { false }, cell: (String) -> GroupCell?): Boolean =
        read.split('|').map { it.trim() }.any { r ->
            when {
                r.isEmpty() -> false
                r.startsWith(Car3dVariant.CMD) -> sent(r.removePrefix(Car3dVariant.CMD))
                else -> active(cell(r))
            }
        }

    /**
     * Điểm có VIỆC khi chạm (ghi được — có ô nút): CÙNG cờ mà [Car3dPick.pick] dùng để nhường cú chạm cho đích có việc.
     * Kính/cốp/đèn ghi được/đèn đọc ⇒ có; cửa (không đường ghi) ⇒ chỉ xem.
     */
    fun actionable(r: Resolved): Boolean = r.actions.isNotEmpty()

    /**
     * Điểm có CHẤM không — owner 14/10: *"mấy chỗ mà action được, thì cho 1 nút tròn như trước… chỉ để hiểu là nhấn được"* rồi
     * chỉnh *"cửa đâu có nhấn được nên ko cần chấm tròn, chỉ chấm tròn những chỗ action được thôi"* ⇒ chấm = ĐÚNG [actionable]
     * (không bảng tay). Chấm chỉ là dấu nhìn: vùng nhấn + luật chọn đích ([Car3dPick]) không đổi; điểm chỉ-xem (cửa) không chấm —
     * cửa mở đã thấy bằng chính hình 3D.
     */
    fun shows(r: Resolved): Boolean = actionable(r)
}
