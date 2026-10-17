package com.byd.clusternav.launcher

import kotlin.math.max
import kotlin.math.min

/**
 * ═══ 3.01 R25 — CHẠM THẲNG VÀO BỘ PHẬN trên xe 3D (thuần `:core`) ═══════════════════════════════════════════════════
 *
 * Owner 13/10: *"cái nào chuyển động được trên 3D thì không cần làm chấm trạng thái"* ⇒ chạm vào chính bộ phận. Owner 14/10 đặt
 * lại CHẤM trên mọi điểm CÓ VIỆC ([Car3dHotspots.shows]) — chỉ là dấu nhìn ([dotPoint]; 3.1.12 chấm không dời — icon ghế nhường nó, [Car3dDeconflict]), vùng nhấn dưới đây không đổi. Mỗi đích = hộp THẾ GIỚI của bộ phận (hợp hộp `extras.bbox` của các nút
 * khớp neo, sau phép đặt [Car3dOrient.Placement]); đích còn chấm (đèn đọc sách…) = hộp suy biến tại điểm neo.
 *  • Hộp chiếu lên màn bằng CÙNG camera với Filament ([Car3dCamera.project] 8 góc ⇒ chữ nhật bao), nới tối thiểu [minPx]
 *    mỗi chiều quanh tâm (≥ 48 dp hiệu dụng — đèn mảnh, kính chiếu xiên vẫn chạm được).
 *  • Đích phía KHUẤT thân xe bỏ ([Car3dCamera.facing] với pháp tuyến elip [Car3dMath.outward] — cùng luật chấm cũ): chạm
 *    kính trái không bao giờ trúng kính phải sau lưng nó.
 *  • Nhiều đích chứa điểm chạm ⇒ đích có chữ nhật NHỎ NHẤT thắng (cụ thể nhất: kính nằm TRONG tấm cửa, đèn trong cản trước).
 *  • Không đích nào chứa ⇒ `null` (chạm vào khoảng trống / thân xe không làm gì — vuốt xoay vẫn là của khung).
 */
object Car3dPick {

    /** Đích: [id] điểm chạm · hộp thế giới [min]..[max] · [anchor] điểm neo (cho phép thấy/khuất). */
    data class Target(val id: String, val min: V3, val max: V3, val anchor: V3)

    data class Rect(val l: Float, val t: Float, val r: Float, val b: Float) {
        val area: Float get() = (r - l) * (b - t)
        fun contains(x: Float, y: Float) = x >= l && x <= r && y >= t && y <= b
        val cx: Float get() = (l + r) / 2
        val cy: Float get() = (t + b) / 2
    }

    /** Chữ nhật màn của [t] (nới tới [minPx] mỗi chiều); `null` = có góc sau lưng camera. */
    fun rect(cam: Car3dCamera, t: Target, w: Int, h: Int, minPx: Float): Rect? {
        var l = Float.MAX_VALUE; var tp = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in 0 until CORNERS) {
            val p = V3(if (i and 1 == 0) t.min.x else t.max.x, if (i and 2 == 0) t.min.y else t.max.y, if (i and 4 == 0) t.min.z else t.max.z)
            val s = cam.project(p, w, h) ?: return null
            l = min(l, s.x); r = max(r, s.x); tp = min(tp, s.y); b = max(b, s.y)
        }
        val cx = (l + r) / 2; val cy = (tp + b) / 2
        val hw = max((r - l) / 2, minPx / 2); val hh = max((b - tp) / 2, minPx / 2)
        return Rect(cx - hw, cy - hh, cx + hw, cy + hh)
    }

    /** Đích có quay về camera không (cùng luật chấm: pháp tuyến elip ngang của hộp xe [bounds]). */
    fun visible(cam: Car3dCamera, t: Target, bounds: Car3dBounds): Boolean = cam.facing(t.anchor, Car3dMath.outward(t.anchor, bounds))

    /**
     * Đích dưới điểm chạm ([x],[y] px), hoặc `null`. Chữ nhật NHỎ NHẤT chứa điểm thắng (cụ thể nhất). POLISH2 (owner 13/10 *"nhấn
     * vào cốp… đang nhấn vào vùng đen"* — [ĐO máy ảo] chạm giữa nắp cốp ra `tap tail → chỉ xem`): đích chỉ-xem nhỏ nhất mà NẰM TRÊN
     * một đích có việc ([actionable]) — tâm chữ nhật của nó trong chữ nhật đích có việc, như dải đèn đuôi trên nắp — thì đích có
     * việc nhận cú chạm.
     *
     * [Soát 3.01 Pass 3 · P2] Bản POLISH2 cho đích có việc thắng MỌI đích chỉ-xem chỉ cần hai chữ nhật cùng chứa điểm chạm:
     * hộp nắp cốp (to hơn khi đã mở) hay hộp kính cabin của đèn đọc chồng lên một phần cửa ⇒ chạm cửa (chỉ xem) lại bấm ô CỐP /
     * đèn đọc — một lệnh ghi xe từ cú chạm không nhắm vào nó. Nay chỉ nhường khi đích chỉ-xem thật sự nằm trên đích có việc.
     */
    fun pick(
        cam: Car3dCamera, targets: List<Target>, bounds: Car3dBounds, w: Int, h: Int, x: Float, y: Float, minPx: Float,
        actionable: (String) -> Boolean = { true },
    ): String? {
        val hits = targets.asSequence().filter { visible(cam, it, bounds) }
            .mapNotNull { t -> rect(cam, t, w, h, minPx)?.takeIf { it.contains(x, y) }?.let { t.id to it } }
            .sortedBy { it.second.area }.toList()
        val best = hits.firstOrNull() ?: return null
        if (actionable(best.first)) return best.first
        return hits.firstOrNull { (id, r) -> actionable(id) && r.contains(best.second.cx, best.second.cy) }?.first ?: best.first
    }

    /**
     * Chỗ đặt CHẤM của điểm (owner 14/10 *"mấy chỗ action được thì cho 1 nút tròn như trước… diện tích nhấn thì như cũ"*): điểm
     * neo [anchor] giữ đúng tỉ lệ vị trí trong hộp NGHỈ [rest] của bộ phận, chuyển sang hộp ĐANG VẼ [now] (cánh cửa/nắp cốp quay
     * theo bản lề) ⇒ chấm đi theo bộ phận; rồi KẸP vào hộp đích chạm [hit] ⇒ chấm không bao giờ nằm ngoài vùng nhấn của chính nó.
     * Kính TRƯỢT không dời hộp đang vẽ (`:app` `Car3dRenderer.nodeBox` — phần kính đã tụt bị da cửa che) ⇒ chấm kính ở lại ô kính.
     * Trục suy biến (hộp dẹt) ⇒ giữ toạ độ tâm của [now].
     */
    fun dotPoint(anchor: V3, rest: Pair<V3, V3>, now: Pair<V3, V3>, hit: Target): V3 {
        fun axis(a: Double, r0: Double, r1: Double, n0: Double, n1: Double, h0: Double, h1: Double): Double {
            val f = if (r1 - r0 > EPS) ((a - r0) / (r1 - r0)).coerceIn(0.0, 1.0) else 0.5
            return (n0 + f * (n1 - n0)).coerceIn(min(h0, h1), max(h0, h1))
        }
        val (r0, r1) = rest; val (n0, n1) = now
        return V3(
            axis(anchor.x, r0.x, r1.x, n0.x, n1.x, hit.min.x, hit.max.x),
            axis(anchor.y, r0.y, r1.y, n0.y, n1.y, hit.min.y, hit.max.y),
            axis(anchor.z, r0.z, r1.z, n0.z, n1.z, hit.min.z, hit.max.z),
        )
    }

    private const val CORNERS = 8
    private const val EPS = 1e-9
}
