package com.byd.clusternav.launcher

import java.util.concurrent.atomic.AtomicLong

/**
 * ═══ 3.1.6 R29 (làm lại) — BỘ GIẢI HỌ MỰC theo MẶT: mỗi bề mặt một họ mực, đổi CÙNG NHAU (`:core`, thuần) ═══════════════════
 *
 * Owner 15/10: *"cái làm tương phản chữ bằng cách vá 1 mớ text background nhìn rất dị hợm, xấu không tả được, phải làm đổi màu
 * chữ tự động, đồng đều, chứ không vá thế này được em"*. Spec `kachi-301-cast-steps-dialog-ia.html` §R29 (Pass 12) · §4.7.
 *
 * Bản 3.1.5 giải TỪNG phần tử và vá đệm nền dưới phần tử thiếu tương phản ⇒ một thẻ lốm đốm đệm, chữ cạnh nhau khác cực. Bản
 * này KHÔNG có lớp đỡ nào: tương phản chỉ đến từ MÀU MỰC, và màu mực chọn theo MẶT (thẻ · thanh trên · thanh nút · lớp phủ
 * 3D · ô nút), không theo phần tử:
 *
 *  • bên gọi đưa các HỌ mực theo thứ tự ưu tiên (`:app`: mực của chủ đề → cùng chủ đề nâng chữ phụ một bậc → bảng đối cực →
 *    bảng đối cực nâng chữ phụ). Mã màu không sống ở đây;
 *  • mỗi phần tử trên mặt là một [InkNeed]: vai (sàn WCAG) + nền TỆ NHẤT dưới nó + mực của nó dưới từng họ;
 *  • [score] = tỉ số tệ nhất / sàn, lấy MIN trên mọi phần tử ⇒ ≥ 1 nghĩa là MỌI phần tử của mặt đạt sàn dưới họ đó;
 *  • [choose] chọn MỘT họ cho cả mặt, có TRỄ (hysteresis) để trình chiếu ảnh sát ngưỡng không làm mặt nhấp nháy giữa hai họ:
 *     – họ đang hiện còn đạt ⇒ giữ, trừ khi một họ ƯU TIÊN hơn đạt với lề [HYSTERESIS] (về lại mực của chủ đề khi chắc chắn);
 *     – họ đang hiện trượt (hoặc mặt mới) ⇒ họ ĐẦU TIÊN đạt;
 *     – không họ nào đạt (ảnh rất rối) ⇒ họ để NHIỀU phần tử đạt sàn nhất ([passes], bên gọi đếm); hoà ⇒ điểm cao hơn; giữ
 *       họ đang hiện khi nó hoà số phần tử đạt với họ tốt nhất và kém điểm không quá [KEEP_BEST_WITHIN]. `ok = false` +
 *       [ContrastLedger.belowFloor] ⇒ `:app` ghi log. KHÔNG có nhánh im lặng dưới sàn, KHÔNG vá nền. [ĐO máy ảo 15/10] chọn theo
 *       điểm MIN thuần thì một chip trên mảng tối kéo cả thanh trên về mực sáng trên ảnh sáng (giờ 2,2:1) — số phần tử đạt là
 *       thước đúng cho "họ tốt nhất".
 *
 * ## Vì sao không nâng độ đục thẻ ở đây
 * Owner 04/10 (2.88, KDoc [ChromeOpacity]): *"tùy user chọn"* — độ đục thẻ và mức làm tối ảnh đều là lựa chọn của người dùng.
 * Lớp nhuộm cả thẻ khi không họ nào đạt là câu hỏi cho owner (spec §R29 OQ7'), chưa làm.
 *
 * Chi phí: mực đục ⇒ 2 phép đo/phần tử/họ; một mặt ~20 phần tử × 4 họ ⇒ ~160 phép đo, dưới 1 ms; chỉ chạy khi SỰ KIỆN đổi.
 */
enum class InkRole(val floor: Double) {
    /** Chữ thường (WCAG 1.4.3). */
    TEXT(4.5),

    /** Chữ phụ / chú thích — vẫn là chữ, cùng sàn 4.5 (bài học `GroupCellGrid` MUT 3.65–3.73:1, diagnostics §11). */
    SECONDARY_TEXT(4.5),

    /** Chữ lớn (≥ 18 pt hoặc 14 pt đậm, WCAG 1.4.3) — số chính của widget. */
    LARGE_TEXT(3.0),

    /** Nét icon / chấm trạng thái / thành phần đồ hoạ (WCAG 1.4.11). */
    ICON(3.0),

    /** Chữ màu nhấn (nhãn giá trị SELECT, chip đang bật). */
    ACCENT_TEXT(4.5),

    /** Icon màu nhấn (nút đang BẬT). */
    ACCENT_ICON(3.0),
}

/**
 * Một phần tử mang mực trên một mặt: vai [role], nền tệ nhất [ground] dưới nó, và mực của nó dưới TỪNG họ ([inks], cùng thứ tự
 * họ bên gọi đưa — ARGB; mực có alpha được đo đúng phép trộn).
 */
class InkNeed(val role: InkRole, val ground: ContrastBackdrop, val inks: IntArray)

/**
 * Kết quả chọn họ của một mặt. [family] = vị trí họ (0 = mực của chủ đề) · [score] = MIN (tỉ số tệ nhất / sàn) của mọi phần
 * tử dưới họ đó · [ok] = mọi phần tử đạt sàn · [switched] = đổi họ so với lần trước (để log/đếm).
 */
data class FamilyChoice(val family: Int, val score: Double, val ok: Boolean, val switched: Boolean) {
    fun describe(): String = "họ#$family điểm " + (if (score > 1e6) "∞" else "%.2f".format(score)) + (if (ok) "" else " DƯỚI-SÀN")
}

/** Mực đã tô cho một phần tử (theo họ của mặt) + tỉ số tệ nhất trên nền của nó — đi vào log và chỗ gọi `onInk`. */
data class Resolution(val role: InkRole, val ink: Int, val family: Int, val worst: Double, val ok: Boolean) {
    fun describe(): String =
        "${role.name} họ#$family ${ColorMath.hex(ink)} tệ-nhất %.2f/%.1f".format(worst, role.floor) + (if (ok) "" else " DƯỚI-SÀN")
}

object ContrastInk {

    /** Về lại một họ ƯU TIÊN hơn họ đang hiện chỉ khi nó đạt sàn với lề 10 % (4,5 ⇒ 4,95) — chống nhấp nháy khi trình chiếu. */
    const val HYSTERESIS = 0.10

    /** Không họ nào đạt: giữ họ đang hiện nếu điểm của nó kém họ tốt nhất không quá 5 %. */
    const val KEEP_BEST_WITHIN = 0.05

    private const val EPS = 1e-9

    /** Tỉ số tệ nhất / sàn của một phần tử dưới họ [family]. */
    fun margin(need: InkNeed, family: Int): Double = need.ground.worst(need.inks[family]) / need.role.floor

    /** Điểm của họ [family] trên cả mặt: MIN [margin] trên mọi phần tử; mặt không có phần tử ⇒ [Double.MAX_VALUE]. */
    fun score(needs: List<InkNeed>, family: Int): Double {
        var s = Double.MAX_VALUE
        for (n in needs) { val m = margin(n, family); if (m < s) s = m }
        return s
    }

    /** Điểm của mọi họ (0 until [families]). */
    fun scores(needs: List<InkNeed>, families: Int): DoubleArray = DoubleArray(families) { score(needs, it) }

    /** Số phần tử đạt sàn dưới từng họ — thước của [choose] khi không họ nào đạt cho cả mặt. */
    fun passes(needs: List<InkNeed>, families: Int): IntArray = IntArray(families) { f -> needs.count { margin(it, f) >= 1.0 - EPS } }

    /**
     * Chọn MỘT họ cho cả mặt từ [scores] (theo thứ tự ưu tiên), có trễ quanh họ đang hiện [current] (−1 = mặt mới) — xem KDoc tệp.
     * [passes] = số phần tử đạt dưới từng họ (`null` ⇒ chỉ theo điểm) — chỉ dùng khi không họ nào đạt cho cả mặt.
     */
    fun choose(scores: DoubleArray, current: Int = -1, passes: IntArray? = null): FamilyChoice {
        require(scores.isNotEmpty()) { "cần ít nhất một họ mực" }
        fun ok(f: Int) = scores[f] >= 1.0 - EPS
        val cur = current.takeIf { it in scores.indices }
        val pick = when {
            cur != null && ok(cur) -> (0 until cur).firstOrNull { scores[it] >= 1.0 + HYSTERESIS - EPS } ?: cur
            scores.indices.any(::ok) -> scores.indices.first(::ok)
            else -> {
                fun count(f: Int) = passes?.getOrNull(f) ?: 0
                val best = scores.indices.maxWithOrNull(compareBy<Int>({ count(it) }, { scores[it] }, { -it }))!!
                if (cur != null && count(cur) == count(best) && scores[cur] >= scores[best] * (1.0 - KEEP_BEST_WITHIN)) cur else best
            }
        }
        return ContrastLedger.note(FamilyChoice(pick, scores[pick], ok(pick), cur != null && pick != cur))
    }

    /** Alpha 0..1 → byte, làm tròn gần nhất (cùng luật [ChromeOpacity.alphaByte]). */
    fun alphaByte(a: Double): Int = Math.round(a.coerceIn(0.0, 1.0) * 255).toInt()
}

/**
 * Bộ đếm của bộ giải — đường ĐO (cùng tinh thần [KachiPerf]: `AtomicLong`, 0 cấp phát). `:app` in [snapshot] cùng dòng
 * hiệu năng định kỳ và ghi log mỗi lần [belowFloor] tăng (fail-loud).
 */
object ContrastLedger {
    /** Số lần chọn họ cho một mặt. */
    val resolved = AtomicLong()

    /** Số lần một mặt dùng họ KHÁC mực của chủ đề (họ ≠ 0). */
    val swapped = AtomicLong()

    /** Số lần một mặt ĐỔI họ so với lần trước (trễ giữ con số này thấp khi trình chiếu). */
    val switched = AtomicLong()

    /** Số lần không họ nào đạt sàn trên một mặt (đã chọn họ tốt nhất, log ở `:app`). */
    val belowFloor = AtomicLong()

    internal fun note(c: FamilyChoice): FamilyChoice {
        resolved.incrementAndGet()
        if (c.family != 0) swapped.incrementAndGet()
        if (c.switched) switched.incrementAndGet()
        if (!c.ok) belowFloor.incrementAndGet()
        return c
    }

    fun snapshot(): String =
        "tương phản: mặt=${resolved.get()} khác-họ=${swapped.get()} đổi-họ=${switched.get()} dưới-sàn=${belowFloor.get()}"

    fun reset() { resolved.set(0); swapped.set(0); switched.set(0); belowFloor.set(0) }
}
