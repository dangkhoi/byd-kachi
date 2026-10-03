package com.byd.clusternav.launcher

import kotlin.math.roundToInt

/**
 * ═══ L5 WIDGET-FIT-ALL — các QUYẾT ĐỊNH thuần của tầng vẽ (`FitScale` · `FitProbe` · `FitGridLayout`) ═══════════════
 *
 * Soát vòng 1 (2.87): tầng vẽ chỉ có bài canh CHUỖI NGUỒN, nên lỗi lật ngang — con `MATCH_PARENT` của khối dọc giữ
 * nguyên bề rộng khi khối lật ngang ⇒ con đầu nuốt hết hàng, mọi con sau rộng 0 (`LinearLayout.java:1929-1936` +
 * `:1339-1375` r47) — lọt qua mọi bài. Phép quyết nào của tầng vẽ không cần `View` thật được kéo về đây để test thuần
 * (dự án không dùng Robolectric):
 *  - [lp]: `LayoutParams` đích của một view khi áp `k` + dạng;
 *  - [freeTextCut]: chữ MỘT dòng tự `…` (giá trị · tên bài) khi nào mới tính là bị cắt;
 *  - [spills]: con tràn khỏi khung cha (con cỡ cố định — nút nhạc, thanh tiến trình, vạch mức — hoặc hàng hết chỗ);
 *  - [usable]: dạng nào được đưa vào phép khớp;
 *  - [reprobe] + [settle]: khi nào đo dò lại một ô vì chữ đã đổi, và nhận số đo mới ra sao (không giật).
 *
 * Số dp KHÔNG sống ở đây (`SpacingScaleContractTest`): mọi đầu vào là px do tầng vẽ đo/đổi.
 */
object FitRules {

    /** = `ViewGroup.LayoutParams.MATCH_PARENT` — `FitRulesWiringContractTest` (:app) ghim bằng nhau. */
    const val MATCH = -1

    /** = `ViewGroup.LayoutParams.WRAP_CONTENT`. */
    const val WRAP = -2

    /** `LayoutParams` rút gọn: bề rộng/cao (px > 0, [MATCH], [WRAP], hoặc 0 khi chia `weight`) + `weight`. */
    data class Lp(val width: Int, val height: Int, val weight: Float = 0f)

    /**
     * `LayoutParams` đích của một view có [base] (giá trị của bộ dựng, thang 1) khi áp hệ số [k]. [rotated] = view là
     * con TRỰC TIẾP của khối chính và lưới đang ở dạng NGANG (khối dọc đã lật thành hàng).
     *  - cỡ cố định (px > 0) × k, tối thiểu 1px; [MATCH]/[WRAP]/0 giữ nguyên;
     *  - con dùng `weight` đổi trục khi lật (đang chia phần còn lại theo chiều dọc ⇒ chia theo chiều ngang);
     *  - con [MATCH] bề rộng (mặc định của `LinearLayout` dọc khi `addView` không kèm LP) ⇒ `0 + weight 1`: các con co
     *    giãn CHIA hàng ngang. Giữ [MATCH] là con đầu lấy hết, con sau rộng 0 (lỗi soát vòng 1). Không dùng [WRAP]:
     *    `TextView` bề rộng [WRAP] mà đổi chữ mỗi nhịp thì `checkForRelayout` rơi nhánh "bề rộng động" ⇒
     *    `requestLayout` MỖI NHỊP (`TextView.java:9641-9692` r47) — đúng cú đo lại cả màn mỗi giây owner báo 09-25;
     *    bề rộng 0 + weight là bề rộng TĨNH ⇒ đổi chữ chỉ dựng lại chữ, không đo lại;
     *  - không lật ⇒ trả đúng giá trị gốc × k (đổi dạng về DỌC là khôi phục trọn vẹn).
     */
    fun lp(base: Lp, k: Double, rotated: Boolean): Lp {
        fun sc(v: Int): Int = if (v > 0) (v * k).roundToInt().coerceAtLeast(1) else v
        val w = sc(base.width)
        val h = sc(base.height)
        return when {
            !rotated -> Lp(w, h, base.weight)
            base.weight > 0f -> Lp(h, w, base.weight)
            base.width == MATCH -> Lp(0, h, 1f)
            else -> Lp(w, h, base.weight)
        }
    }

    /**
     * Ngân sách của chữ TỰ DO một dòng (`maxLines = 1` + `ellipsize`, không phải nhãn) tính theo em của chính nó:
     * chữ ngắn hơn ngân sách (giá trị `2.3–2.5`, `418 km`, `µg · Tốt` ≈ 3–5 em) phải hiện TRỌN; chữ dài hơn (tên bài
     * hát 100 ký tự) được `…` sau [FREE_TEXT_EM] em. Không có trần này thì một chuỗi dài quyết cỡ CẢ lưới (hộp chung
     * = MAX các ô) và kéo mọi ô xuống sàn (soát vòng 1, P2). [ĐỀ XUẤT, owner chốt].
     */
    const val FREE_TEXT_EM = 6.0

    /**
     * Chữ tự do một dòng đang `…` có tính là BỊ CẮT không: chỉ khi chỗ dành cho nó ([availPx]) còn hẹp hơn ngân sách
     * [FREE_TEXT_EM] × cỡ chữ ([textPx]). Chữ ngắn hơn ngân sách mà bị `…` thì chỗ < bề rộng tự nhiên < ngân sách ⇒
     * vẫn tính là cắt. Đơn điệu theo [availPx] ⇒ phép tìm nhị phân bề rộng nhỏ nhất của `FitProbe` vẫn đúng.
     */
    fun freeTextCut(availPx: Float, textPx: Float): Boolean = availPx + 1f < FREE_TEXT_EM * textPx

    /**
     * Một khung cha có để con TRÀN ra ngoài không, trên MỘT trục: [parentPx] cỡ đo được của cha, [paddingPx] tổng lề
     * trong hai phía, [childPx] cỡ đo được + lề ngoài của từng con đang hiện. [stacked] = các con xếp NỐI TIẾP trên
     * trục này (`LinearLayout` cùng hướng) ⇒ cộng dồn; còn lại (trục chéo, `FrameLayout`, khung tự đặt) ⇒ từng con.
     * Sai số 1px (làm tròn). Con cỡ cố định vẫn được đo đúng cỡ của nó dù cha hẹp hơn (`ViewGroup.getChildMeasureSpec`
     * EXACTLY) và chỉ đơn giản tràn ra — phép kiểm cắt chữ không thấy được, phép này thấy.
     */
    fun spills(parentPx: Int, paddingPx: Int, childPx: List<Int>, stacked: Boolean): Boolean {
        if (childPx.isEmpty()) return false
        val need = if (stacked) childPx.sum() else childPx.max()
        return need + paddingPx > parentPx + 1
    }

    /**
     * Dạng được đưa vào phép khớp: trong [candidates] (chỉ số của `FitProbe.OPTIONS`), chỉ những dạng mà MỌI ô có bề
     * rộng nào đó vẽ trọn ([ok]). Dạng không ô nào vẽ trọn được ở bất kỳ bề rộng nào (vd hàng ngang mà một con không
     * bao giờ có chỗ) KHÔNG được làm ứng viên với hộp giả. Không dạng nào đạt ⇒ giữ nguyên danh sách (đường lùi cũ:
     * phép kiểm báo nhầm thì vẫn phải vẽ một cái gì đó, dạng gốc đứng đầu).
     */
    fun usable(candidates: List<Int>, ok: (Int) -> Boolean): List<Int> = candidates.filter(ok).ifEmpty { candidates }

    /** Chờ tối thiểu giữa hai lượt đo dò một ô khi chữ MỚI bị cắt (một nhịp trạng thái xe). */
    const val GROW_GAP_MS = 1_000L

    /**
     * Chờ tối thiểu giữa hai lượt đo dò một ô cho mọi trường hợp còn lại: chữ đổi mà KHÔNG cắt (hộp có thể đã nhỏ đi
     * ⇒ cho lưới giãn lại), lưới đang không đọc được (khung quá nhỏ — tự phục hồi khi chữ ngắn lại), hoặc lượt đo dò
     * trước không chữa được vết cắt ([reprobe] `stuck`). [ĐỀ XUẤT].
     */
    const val RECHECK_MS = 30_000L

    /**
     * Có đo dò lại ô mà chữ đã đổi (dấu nội dung khác lúc đo) không. [clipped] = ô đang cắt chữ/tràn; [legible] =
     * lưới hiện ở tầng đọc được; [stuck] = lượt đo dò trước của ô vẫn để lại vết cắt; [sinceProbeMs] = từ lượt đo dò
     * trước của ô. Cắt + đọc được + chưa kẹt ⇒ nhịp kế tiếp (chữ dài ra giữa chuyến); còn lại ⇒ thưa ([RECHECK_MS]) —
     * không bao giờ đo dò theo từng nhịp 1 Hz khi việc đo không chữa được gì (R-WF6).
     */
    fun reprobe(clipped: Boolean, legible: Boolean, stuck: Boolean, sinceProbeMs: Long): Boolean =
        sinceProbeMs >= if (clipped && legible && !stuck) GROW_GAP_MS else RECHECK_MS

    /** Hộp phải nhỏ đi hơn tỉ lệ này mới nhận ở lượt đo dò KHÔNG cắt — chống giật (99 ↔ 100 km/h không đổi cỡ lưới). */
    const val SHRINK_HYSTERESIS = 0.15

    /**
     * Nhận số đo mới [new] của một dạng thay cho số cũ [old] ra sao. [grow] = lượt đo dò vì chữ bị cắt ⇒ nhận [new]
     * (nội dung thật cần chỗ đó). Lượt đo dò thưa (không cắt) ⇒ chỉ nhận khi hộp NHỎ ĐI rõ rệt (không lớn hơn ở trục
     * nào và nhỏ hơn [SHRINK_HYSTERESIS] ở ít nhất một trục): chữ đang vừa thì không có lý do bóp cả lưới, và dao động
     * nhỏ không làm cỡ chữ cả lưới đổi theo.
     */
    fun settle(old: GridFit.Shape, new: GridFit.Shape, grow: Boolean): GridFit.Shape {
        if (grow) return new
        val notBigger = new.widthPx <= old.widthPx && new.heightPx <= old.heightPx
        val keep = 1.0 - SHRINK_HYSTERESIS
        val shrank = new.widthPx < old.widthPx * keep || new.heightPx < old.heightPx * keep
        return if (notBigger && shrank) new else old
    }
}
