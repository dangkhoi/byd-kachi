package com.byd.clusternav.launcher

/**
 * ═══ 2.93 `WF-MEDIA-SMALL` — widget NHẠC đơn: khung nhỏ BỎ phần phụ trước, giữ ba nút cỡ chạm (thuần, `:core`, px) ════════
 *
 * Bệnh [ĐO máy ảo QA3 04/10]: khung 4×1 có thanh nút ⇒ nút trước/sau rộng 0, nút phát 70×72 px; 2×1 chỉ còn ảnh bìa (tên
 * cao 6 px, 0 nút); 2×2 ảnh giãn 84×120 + thanh tiến trình, 0 nút; chỉ QUAD đạt. Gốc [ĐO mã]: cả khối dọc (ảnh · tên ·
 * nghệ sĩ · tiến trình · hàng nút) co/giãn CHUNG một hệ số `k` qua `FitScale`, mà nút 48 dp không được co ⇒ khung không đủ
 * cho cả khối thì bộ giải chọn cách "ít tệ nhất" — luôn mất NÚT, tức mất đúng thứ người lái cần trên xe đang chạy.
 *
 * Luật (spec `docs/specs/kachi-293-widget.html` R-W13 — chiều "bỏ ảnh + tên trước, giữ trước/phát/sau cỡ chạm" của spec 287
 * OQ-WF4, theo ĐO khung, không theo mã widget):
 *  1. phần nội dung xếp theo thứ tự GIỮ: nút trước/phát/sau › tên bài › tiến trình › nghệ sĩ › ảnh bìa ([LEVELS] bỏ dần từ cuối);
 *     tới cùng chỉ còn nút PHÁT (khung hẹp hơn ba nút);
 *  2. mỗi mức thử hai cách xếp: [Arrange.STACK] (dọc như 2.92: ảnh · tên · nghệ sĩ · tiến trình · hàng nút) và [Arrange.ROW]
 *     (ảnh │ cột chữ + tiến trình │ hàng nút — khung một hàng rộng); `k` = lớn nhất để cả khối vừa (≤ [MAX_SCALE]);
 *  3. chọn MỨC đầu tiên (nhiều nội dung nhất) có cách xếp đạt `k ≥ 1` (nút ≥ 48 dp — R-WF4); cùng mức ⇒ `k` lớn hơn, hoà ⇒
 *     STACK (dáng cũ);
 *  4. lề trong KHÔNG tính vào điều kiện vừa: nó chỉ nhận phần dư (≤ [Box.pad] mỗi phía) — khung một hàng 86–123 px vẫn
 *     giữ được nút 72 px;
 *  5. không mức nào đạt ⇒ chỉ nút phát ở `k = 1` ([Plan.fits] `false` — tràn, khung quá nhỏ cho một đích chạm).
 * Chữ tự do (tên bài, nghệ sĩ) cần tối thiểu [FitRules.FREE_TEXT_EM] em — cùng ngân sách của `FitRules.freeTextCut`.
 */
object MediaFit {

    enum class Part { ART, TITLE, ARTIST, PROGRESS, PREV_NEXT }

    enum class Arrange { STACK, ROW }

    /** Trần `k` — như ô đơn của lưới widget (`FitGridLayout.MAX_SCALE_SINGLE`). */
    const val MAX_SCALE = 1.5

    /**
     * Cỡ ở thang 1 (px — tầng vẽ đổi dp/sp ra px): lề trong [pad]; ảnh vuông [art] + khe dưới/cạnh [gap]; chiều cao một dòng
     * tên [titleH] / nghệ sĩ [artistH]; bề rộng tối thiểu cột chữ [textMinW]; thanh tiến trình [progW]×[progH] + khe trên
     * [progGap]; nút vuông [btn] + khe giữa hai nút [btnGap]; khe trên hàng nút [rowGap].
     */
    data class Box(
        val pad: Int, val art: Int, val gap: Int, val titleH: Int, val artistH: Int, val textMinW: Int,
        val progW: Int, val progH: Int, val progGap: Int, val btn: Int, val btnGap: Int, val rowGap: Int,
    )

    data class Plan(val parts: Set<Part>, val arrange: Arrange, val k: Double, val fits: Boolean)

    private val ALL = Part.values().toSet()

    /** Mức nội dung, nhiều → ít (luật 1). Mức cuối rỗng = chỉ nút phát. */
    val LEVELS: List<Set<Part>> = listOf(
        ALL,
        ALL - Part.ART,
        ALL - Part.ART - Part.ARTIST,
        ALL - Part.ART - Part.ARTIST - Part.PROGRESS,
        setOf(Part.PREV_NEXT),
        emptySet(),
    )

    /** (rộng, cao) nội dung ở thang 1 — KHÔNG gồm lề trong (luật 4). */
    fun natural(parts: Set<Part>, arrange: Arrange, b: Box): Pair<Double, Double> {
        val buttons = if (Part.PREV_NEXT in parts) 3.0 * b.btn + 2.0 * b.btnGap else b.btn.toDouble()
        val title = if (Part.TITLE in parts) b.titleH.toDouble() else 0.0
        val artist = if (Part.ARTIST in parts) b.artistH.toDouble() else 0.0
        val prog = if (Part.PROGRESS in parts) (b.progGap + b.progH).toDouble() else 0.0
        val text = title + artist + prog
        val hasText = text > 0.0
        val art = Part.ART in parts
        return when (arrange) {
            Arrange.STACK -> {
                val w = maxOf(if (art) b.art.toDouble() else 0.0, if (hasText) b.textMinW.toDouble() else 0.0,
                    if (Part.PROGRESS in parts) b.progW.toDouble() else 0.0, buttons)
                val above = (if (art) b.art + b.gap else 0) + text
                w to above + (if (above > 0.0) b.rowGap else 0) + b.btn
            }
            Arrange.ROW -> {
                val w = (if (art) b.art + b.gap else 0) + (if (hasText) b.textMinW + b.gap else 0) + buttons
                w to maxOf(if (art) b.art.toDouble() else 0.0, text, b.btn.toDouble())
            }
        }
    }

    /** `k` lớn nhất để [parts] xếp [arrange] vừa khung [w]×[h] (≤ [MAX_SCALE]). */
    fun scale(parts: Set<Part>, arrange: Arrange, w: Int, h: Int, b: Box): Double {
        val (nw, nh) = natural(parts, arrange, b)
        if (nw <= 0.0 || nh <= 0.0) return MAX_SCALE
        return minOf(w / nw, h / nh, MAX_SCALE)
    }

    /** Kế hoạch cho khung [w]×[h] (KDoc lớp, luật 1–5). */
    fun plan(w: Int, h: Int, b: Box): Plan {
        for (parts in LEVELS) {
            val ks = scale(parts, Arrange.STACK, w, h, b)
            val kr = if (parts.isEmpty() || parts == setOf(Part.PREV_NEXT)) ks else scale(parts, Arrange.ROW, w, h, b)
            val best = maxOf(ks, kr)
            if (best >= 1.0 - 1e-9) {
                val arrange = if (kr > ks + 1e-9) Arrange.ROW else Arrange.STACK
                return Plan(parts, arrange, best, fits = true)
            }
        }
        return Plan(emptySet(), Arrange.STACK, 1.0, fits = false)
    }
}
