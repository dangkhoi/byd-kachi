package com.byd.clusternav.launcher

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * ═══ 3.1.12 · CAR3D-SEAT-ICON-OVERLAP — icon ghế NHƯỜNG chỗ cho chấm có việc trên xe 3D (thuần `:core`) ═══════════════════
 *
 * Owner 17/10 (3.1.11, trên xe): *"2 icon sưởi, mát ghế nó đè lên kính trước, làm kính trước hơi khó nhấn"*.
 * [ĐO log xe 17/10] góc mặc định (yaw 40, pitch 16, khung 1192×882, 240 dpi): neo `win-lf` chiếu ra (656, 403) nhưng chấm ghi
 * (656, 367) — bị dời LÊN 36 px vì cụm ghế lái (điểm ghế chiếu (638, 403), đĩa 50 px) nằm ĐÚNG trên neo; lớp icon ghế nằm trên và
 * giành cú chạm ⇒ vùng nhấn kính trước bị hai đĩa ăn mất phần quanh chấm. Gốc: chấm né icon (luật cũ `freeY`) — ưu tiên NGƯỢC.
 *
 * Luật mới (mọi góc, mọi đời xe — đo hình chiếu, không theo tên bộ phận/góc):
 *  • Chấm có việc ĐỨNG YÊN ở hình chiếu bộ phận; quanh nó là vùng CỨNG (ô chạm 48 dp) — icon không được đè.
 *  • Chữ nhật vùng nhấn của bộ phận có việc (kính, đèn…) là vùng MỀM — đè được nhưng bị phạt theo phần đè.
 *  • Cụm icon dời tới tâm RẺ NHẤT trong bán kính [place] `reach`: chi phí = khoảng dời + [HARD_W]·phần đè cứng + [SOFT_W]·phần đè mềm
 *    (phần đè = diện tích chung ÷ cao cụm, đơn vị px như khoảng dời). Duyệt vòng vuông nở dần, dừng khi vòng kế không thể rẻ hơn ⇒ chỗ
 *    gốc đã trống thì 0 bước dư.
 *  • Hết chỗ (khung chật) ⇒ vẫn trả tâm rẻ nhất (`false` = còn đè cứng) — không ẩn icon; cú chạm trong vùng cứng thuộc CHẤM ([claimed]).
 *
 * Không cấp phát mỗi khung: [Zones] là bộ đệm tái dùng (chỉ nở khi vượt sức chứa), [place] ghi kết quả vào mảng của bên gọi.
 */
object Car3dDeconflict {

    /** Bộ đệm vùng (l, t, r, b liên tiếp): CỨNG = ô chạm của chấm có việc (+ cụm vừa đặt, tạm) · MỀM = vùng nhấn bộ phận có việc. */
    class Zones(capacity: Int = 16) {
        var hard = FloatArray(capacity * 4); private set
        var nHard = 0; private set
        var soft = FloatArray(capacity * 4); private set
        var nSoft = 0; private set

        fun clear() { nHard = 0; nSoft = 0 }
        fun addHard(l: Float, t: Float, r: Float, b: Float) { hard = put(hard, nHard++, l, t, r, b) }
        fun addSoft(l: Float, t: Float, r: Float, b: Float) { soft = put(soft, nSoft++, l, t, r, b) }
        /** Bỏ các vùng cứng thêm sau mốc [n] (cụm icon đặt tạm để cụm kế không đè nó). */
        fun truncateHard(n: Int) { nHard = n.coerceIn(0, nHard) }

        private fun put(a: FloatArray, i: Int, l: Float, t: Float, r: Float, b: Float): FloatArray {
            val o = if (i * 4 + 4 > a.size) a.copyOf(a.size * 2 + 4) else a
            o[i * 4] = l; o[i * 4 + 1] = t; o[i * 4 + 2] = r; o[i * 4 + 3] = b
            return o
        }
    }

    /**
     * Đặt cụm [w]×[h] muốn ở tâm ([cx], [cy]) trong khung [fw]×[fh] (cụm luôn nằm TRỌN trong khung). Ghi tâm chọn vào [out]
     * (`out[0]` x, `out[1]` y). Ứng viên = lưới bước [step] quanh tâm muốn (đã kẹp vào khung), tới [reach] px. `true` = không đè
     * vùng cứng nào.
     */
    fun place(cx: Float, cy: Float, w: Float, h: Float, fw: Float, fh: Float, z: Zones, step: Float, reach: Float, out: FloatArray): Boolean {
        val x0 = clampCenter(cx, w, fw); val y0 = clampCenter(cy, h, fh)
        val s = max(step, 1f)
        val rings = ceil(max(reach, 0f) / s).toInt()
        var bx = x0; var by = y0; var best = Float.MAX_VALUE; var bestHard = Float.MAX_VALUE
        var k = 0
        while (k <= rings && k * s <= best) {   // mọi ứng viên vòng k cách tâm ≥ k·step ⇒ không rẻ hơn [best] thì dừng
            val n = if (k == 0) 1 else 8 * k
            for (t in 0 until n) {
                val i: Int; val j: Int
                when {
                    k == 0 -> { i = 0; j = 0 }
                    t <= 2 * k -> { i = -k + t; j = -k }                       // hàng trên (ưu tiên dời LÊN khi bằng giá)
                    t <= 4 * k + 1 -> { i = -k + (t - 2 * k - 1); j = k }       // hàng dưới
                    else -> { val u = t - 4 * k - 2; i = if (u % 2 == 0) -k else k; j = -k + 1 + u / 2 }   // hai cột bên
                }
                val x = x0 + i * s; val y = y0 + j * s
                if (x - w / 2 < -EPS || x + w / 2 > fw + EPS || y - h / 2 < -EPS || y + h / 2 > fh + EPS) continue
                val hard = overlap(z.hard, z.nHard, x, y, w, h) / max(h, 1f)
                val cost = hypot(x - x0, y - y0) + HARD_W * hard + SOFT_W * overlap(z.soft, z.nSoft, x, y, w, h) / max(h, 1f)
                if (cost < best) { best = cost; bestHard = hard; bx = x; by = y }
            }
            k++
        }
        out[0] = bx; out[1] = by
        return bestHard <= EPS
    }

    /** Cú chạm ([x], [y]) nằm trong ô chạm của một chấm có việc ⇒ chấm thắng mọi nút trang trí/phụ chồng lên (icon ghế). */
    fun claimed(x: Float, y: Float, z: Zones): Boolean {
        val a = z.hard
        for (i in 0 until z.nHard) if (x >= a[i * 4] && x <= a[i * 4 + 2] && y >= a[i * 4 + 1] && y <= a[i * 4 + 3]) return true
        return false
    }

    /** Tổng diện tích chung của cụm tâm ([x], [y]) cỡ [w]×[h] với [n] chữ nhật đầu của [a]. */
    fun overlap(a: FloatArray, n: Int, x: Float, y: Float, w: Float, h: Float): Float {
        val l = x - w / 2; val t = y - h / 2; val r = x + w / 2; val b = y + h / 2
        var sum = 0f
        for (i in 0 until n) {
            val ow = min(r, a[i * 4 + 2]) - max(l, a[i * 4]); if (ow <= 0f) continue
            val oh = min(b, a[i * 4 + 3]) - max(t, a[i * 4 + 1]); if (oh <= 0f) continue
            sum += ow * oh
        }
        return sum
    }

    private fun clampCenter(c: Float, size: Float, frame: Float): Float =
        if (size >= frame) frame / 2 else c.coerceIn(size / 2, frame - size / 2)

    /** Phạt đè vùng cứng: 1 px đè ≫ mọi khoảng dời trong tầm ⇒ còn chỗ trống là chọn chỗ trống. */
    const val HARD_W = 1000f
    /** Phạt đè vùng nhấn bộ phận có việc: ngang khoảng dời — chịu dời thêm chừng bề rộng phần đè để trả lại vùng nhấn. */
    const val SOFT_W = 1f
    private const val EPS = 1e-3f
}
