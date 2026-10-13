package com.byd.clusternav.launcher

import kotlin.math.hypot

/**
 * ═══ 3.01 R24 — BẢNG ICON TRÊN HÌNH XE (thuần `:core`) ═══════════════════════════════════════════════════════════════
 *
 * Owner 13/10: *"cái widget đèn cũng làm vậy luôn, có hình xe và chấm tròn hay icon đèn xung quanh, đúng vị trí đèn, nhấn
 * tắt, nhấn mở"*. Cùng ngôn ngữ bảng Kính (R22·C): hình xe là nền, mỗi vật một ĐIỂM (icon trong vòng) ĐÚNG chỗ của nó trên
 * ảnh, chạm điểm = nút của vật đó.
 *
 * ## Dữ liệu, không mã nhóm (CLAUDE.md §7)
 * Ô nào lên hình = ô của [GroupIconBoard] (nhóm khai [GroupLayout.ICONS]) mà ảnh đang vẽ có ĐIỂM cho icon của nó ([CarArt.spot]
 * — đo theo từng ảnh). Ô có NÚT mà ảnh không có điểm ⇒ KHÔNG dựng hình (không được mất nút) ⇒ lưới icon cũ. Ô chỉ-xem không
 * điểm (vd chế độ đèn pha) ⇒ bỏ khỏi hình (lưới icon — dạng lùi — vẫn có nó).
 *
 * ## Vừa chỗ — đo, không đoán
 * [fits]: mọi điểm (đĩa [discPx]) không chồng nhau, cách nhau ≥ [gapPx]; không vừa ⇒ `:app` lùi lưới icon → ô tóm tắt.
 *
 * ## Chạm — MỖI điểm sở hữu vùng của nó (3.01 TAPFIX)
 * [SpotTouch.owner]: cú chạm thuộc điểm có tâm gần nhất (trong tầm), KỂ CẢ điểm chỉ-xem — điểm chỉ-xem nuốt cú chạm,
 * không bao giờ chuyển cho đèn có nút bên cạnh. [SpotTouch.ownSquare]: ô vuông không chồng cho nút trợ năng. Bản R24 (`zones` — k-d chỉ
 * trên điểm CÓ NÚT) đã GỠ: nó giao cả khung cho 3 đèn có nút ⇒ chạm sương mù bật đèn ban ngày.
 */
object CarIconBoard {

    /** Một điểm trên hình: [at] chuẩn hoá theo khung ảnh ([CarAnchor], x 0 = trái xe, y 0 = đầu xe). */
    data class Spot(val tile: IconTile, val at: CarAnchor)

    /** Ít nhất chừng này điểm mới đáng một hình xe (một điểm thì lưới icon rõ hơn). */
    const val MIN_SPOTS = 2

    /** Điểm của [tiles] trên ảnh [art]; `null` = không dựng hình được (ô có nút thiếu điểm · quá ít điểm). */
    fun place(tiles: List<IconTile>, art: CarArt): List<Spot>? {
        val spots = ArrayList<Spot>()
        tiles.forEach { t ->
            val at = art.spot(t.icon)
            if (at == null) { if (t.action != null) return null } else spots += Spot(t, at)
        }
        return spots.takeIf { it.size >= MIN_SPOTS }
    }

    /** Tâm (px) của [s] trong khung ảnh `[left, top] + [w]×[h]`. */
    fun centerIn(s: Spot, left: Float, top: Float, w: Float, h: Float): Pair<Float, Float> = (left + s.at.x * w) to (top + s.at.y * h)

    /** Các đĩa [discPx] tại [points] không chồng nhau (khe ≥ [gapPx]) và nằm trọn trong `[0, boundW) × [0, boundH)`. */
    fun fits(points: List<Pair<Float, Float>>, discPx: Float, gapPx: Float, boundW: Float, boundH: Float): Boolean {
        val r = discPx / 2f
        if (points.any { (x, y) -> x - r < 0f || y - r < 0f || x + r > boundW || y + r > boundH }) return false
        for (i in points.indices) for (j in i + 1 until points.size) {
            val (ax, ay) = points[i]; val (bx, by) = points[j]
            if (hypot(ax - bx, ay - by) < discPx + gapPx) return false
        }
        return true
    }

    /**
     * Trạng thái điểm (cùng bốn kiểu [CarDots.Kind], ở đây chỉ ba): [cell] đọc được ⇒ sáng khi khác NEUTRAL (luật
     * [GroupIconBoard]); [sent] = bật/tắt Kachi vừa gửi (trong ân hạn sau cú chạm, hoặc nút không có số đọc mà đã từng gửi
     * BẬT) — thắng số đọc vì số còn trễ một nhịp poll. Không gì cả ⇒ [CarDots.Kind.UNKNOWN] (không giấu "chưa biết" thành "tắt").
     */
    fun kind(cell: GroupCell?, sent: Boolean?): CarDots.Kind = when {
        sent != null -> if (sent) CarDots.Kind.OPEN else CarDots.Kind.CLOSED
        cell != null && cell.available -> if (cell.tone != GroupTone.NEUTRAL) CarDots.Kind.OPEN else CarDots.Kind.CLOSED
        else -> CarDots.Kind.UNKNOWN
    }
}
