package com.byd.clusternav.launcher

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/**
 * ═══ 3.01 TAPFIX — AI SỞ HỮU CÚ CHẠM trên hình xe (thuần `:core`, chung bảng Đèn + bảng Kính) ═════════════════════════════
 *
 * Owner 13/10 trên máy ảo: *"nhấn nút này nó xanh nút khác"* (bảng Đèn) và *"widget kính cũng bị vậy luôn đó, click lên xa
 * trên top… nó không đúng vùng, chứ ko chỉ là vùng chọn rộng"* (bảng Kính).
 *
 * Gốc chung [ĐO bằng bản chép thuật toán cũ, công cụ ngoài repo — bảng ở handoff `kachi-301-stage-7-TAPFIX-done.md`]: cả hai bảng chia KHUNG thành vùng chữ nhật
 * nở tới mép (Đèn: k-d trên 3 điểm có nút — `CarIconBoard.zones` R24; Kính: phần tư/dải theo neo CHUNG `CarLayout.part`,
 * `GroupActionMatrix.zones`) ⇒ vùng không gắn với chỗ CHẤM ĐƯỢC VẼ: chạm điểm chỉ-xem bật đèn bên cạnh; chạm sát mép trên ô
 * (xa mọi chấm) mở kính trước.
 *
 * Luật mới, một chỗ: chủ cú chạm = điểm có TÂM VẼ gần nhất ([owner], Voronoi theo tâm), và chỉ khi trong [reachPx] của tâm
 * đó — xa hơn ⇒ không ai, không lệnh. Tâm truyền vào phải là CHÍNH toạ độ bộ vẽ dùng (`:app` lấy từ cùng hàm ánh xạ với lớp vẽ)
 * ⇒ vùng không thể lệch khỏi chấm. Mọi điểm vẽ ra đều có mặt trong danh sách (cả điểm chỉ-xem) ⇒ không vùng nào nuốt tâm
 * của điểm khác.
 */
object SpotTouch {

    /**
     * Tầm chạm = chừng này × đích chạm (48 dp) tính từ TÂM chấm: 0,75 ⇒ bán kính 36 dp, đích rộng 72 dp — dễ trúng khi xe
     * chạy, nhưng chạm cách chấm 60 dp (vd sát mép trên ô) không còn là "chạm chấm đó".
     */
    const val REACH_OF_TOUCH = 0.75f

    /** Tầm chạm px cho đích chạm [touchPx]. */
    fun reachPx(touchPx: Int): Float = touchPx * REACH_OF_TOUCH

    /**
     * Chủ của cú chạm `([x], [y])` trong các tâm [points]: chỉ số tâm GẦN NHẤT nếu cách ≤ [reachPx]; xa mọi tâm hơn ⇒ `null`.
     * Hoà ⇒ chỉ số nhỏ hơn (tất định).
     */
    fun owner(points: List<Pair<Float, Float>>, x: Float, y: Float, reachPx: Float): Int? {
        var best = -1; var bestD = Float.MAX_VALUE
        points.forEachIndexed { i, (px, py) ->
            val dd = hypot(px - x, py - y)
            if (dd < bestD) { bestD = dd; best = i }
        }
        return best.takeIf { it >= 0 && bestD <= reachPx }
    }

    /**
     * Ô vuông RIÊNG của tâm [i] (nút trợ năng / dò bằng chạm của TalkBack): cạnh = min([maxPx], khoảng Chebyshev
     * `max(|dx|, |dy|)` tới tâm gần nhất) ⇒ hai ô bất kỳ KHÔNG chồng (tổng nửa cạnh ≤ khoảng cách trên trục xa hơn), kẹp trong
     * `[0, [boundW]) × [0, [boundH])`. Biên làm tròn VÀO trong (`ceil` trái/trên, `floor` phải/dưới).
     */
    fun ownSquare(points: List<Pair<Float, Float>>, i: Int, maxPx: Float, boundW: Int, boundH: Int): GroupActionMatrix.Zone {
        val (cx, cy) = points[i]
        var side = maxPx
        points.forEachIndexed { j, (x, y) -> if (j != i) side = minOf(side, maxOf(abs(x - cx), abs(y - cy))) }
        val h = side / 2f
        return GroupActionMatrix.Zone(
            ceil(cx - h).toInt().coerceIn(0, boundW), ceil(cy - h).toInt().coerceIn(0, boundH),
            floor(cx + h).toInt().coerceIn(0, boundW), floor(cy + h).toInt().coerceIn(0, boundH),
        )
    }

    /**
     * Khung ảnh đúng tỉ lệ [aspect] (rộng : cao) lớn nhất, canh GIỮA trong `[l, r) × [t, b)` — `[trái, trên, rộng, cao]`. MỘT
     * phép đặt khung cho lớp vẽ chấm (`CarLevelsLayer.fit`) và cho bài kiểm vùng chạm.
     */
    fun fitCenter(l: Float, t: Float, r: Float, b: Float, aspect: Float): FloatArray {
        val s = minOf((r - l) / aspect, b - t); val w = s * aspect
        val cx = (l + r) / 2f; val cy = (t + b) / 2f
        return floatArrayOf(cx - w / 2f, cy - s / 2f, w, s)
    }
}
