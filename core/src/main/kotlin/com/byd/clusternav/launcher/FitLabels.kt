package com.byd.clusternav.launcher

/**
 * ═══ J1 (2.87, QA2 04/10) — CHỮ TÊN của từng ô trong lưới widget: nhãn đầy · nhãn ngắn · cắt đầu ════════════════════
 *
 * Bệnh [ĐO máy ảo QA2, `fit-2x1-dock-4lang.png`, `fit-3x1-th-ms.png` — bằng chứng phiên, ngoài repo]: khung 2×1 / 3×1 không bố cục nào đọc được
 * ⇒ lưới giữ sàn 10sp và nhãn bị `…` tới mức các nút hiện Y HỆT nhau — `Kính …` ×4, `กระจ…` ×4, `Kaca…` ×4, `全部…` ×2,
 * `Kaca blkg k…` ×2 — trong khi icon kính cũng không phân biệt được ([IconRepeat]) ⇒ người lái không biết nút nào là kính
 * nào.
 *
 * Quyết định điều phối (J1): mỗi ô nút/datum có sẵn NHÃN NGẮN đã dịch đủ 5 tiếng ([ControlDef.shortLabelIn] /
 * [TelemetrySpec.shortLabelIn] — đúng chữ của hàng nút nhóm, trần zh6/th14/ms14 ở `I18nPairs.SHORT_CAPS`). Nhãn đầy không hiện trọn
 * ⇒ ô đổi CHỮ sang nhãn ngắn (nhãn đầy vào mô tả trợ năng) TRƯỚC khi `…` hay chỉ-icon. Kèm luật PHÂN BIỆT: hai ô trong
 * cùng lưới mà hiện CÙNG một chữ thấy được ⇒ chọn cách hiện khác cho chúng (`Kaca BKr` / `Kaca BKn` khác nhau trong khi
 * `Kaca blkg k…` ×2 thì không).
 *
 * Bốn cách hiện ([Variant]) = hai chữ (đầy · ngắn) × hai chỗ cắt (cuối · đầu). Cắt ĐẦU chỉ cho chữ MỘT dòng
 * ([ĐO AOSP r47] `StaticLayout.java:1078-1103` — START chỉ áp khi `mMaximumVisibleLineCount == 1`) và CHỈ là đường phân
 * biệt (không bao giờ là cách hiện gốc): phần nói "nút nào" thường nằm ở CUỐI nhãn (kính lái/phụ/sau trái/sau phải,
 * 打开/关闭, คนขับ/ผู้โดยสาร) nên `Kính sau trái` / `Kính sau phải` cắt cuối đều ra `Kính…`, cắt đầu ra `…trái` / `…phải`.
 * Đó là [ĐỀ XUẤT] của làn J1 nối tiếp quyết định nhãn ngắn — với nhãn ngắn hiện có, khung 2×1 (ô 84×43px) vẫn cắt cả
 * nhãn ngắn của VI/TH/ZH (`Kính ST` ⇒ `Kính…`; nhãn ngắn TH/ZH = nhãn đầy) — owner đổi được.
 *
 * Tầng vẽ (`FitNames` ở :app) đo từng cách hiện của từng ô trên bố cục THẬT (cỡ ô thật, `k` thật) rồi hỏi [choose]; ở
 * đây chỉ có LUẬT, test thuần (`FitLabelsTest`).
 */
object FitLabels {

    /** Cách hiện chữ tên của một ô: [short] = nhãn ngắn thay nhãn đầy, [start] = `…` ở ĐẦU (chỉ chữ một dòng). */
    enum class Variant(val short: Boolean, val start: Boolean) {
        FULL(false, false),
        SHORT(true, false),
        FULL_START(false, true),
        SHORT_START(true, true),
        ;

        companion object {
            fun of(short: Boolean, start: Boolean): Variant = values().first { it.short == short && it.start == start }
        }
    }

    /** Một ô hiện gì dưới một cách hiện: [text] = chữ THẤY được (kể cả dấu `…`), [cut] = chữ có bị cắt không. */
    data class Shown(val text: String, val cut: Boolean)

    /** Một dòng đang hiện của khối chữ: `[start, end)` của chữ gốc; `[ellStart, ellStart + ellCount)` tính từ [start] bị `…`. */
    data class Line(val start: Int, val end: Int, val ellStart: Int = 0, val ellCount: Int = 0)

    /** Các cách hiện ô có: nhãn ngắn chỉ khi ô có nhãn ngắn KHÁC nhãn đầy; cắt đầu chỉ khi mọi chữ tên đang là MỘT dòng. */
    fun variants(hasShort: Boolean, oneLine: Boolean): List<Variant> =
        Variant.values().filter { (hasShort || !it.short) && (oneLine || !it.start) }

    /** Chữ THẤY được của [text] theo các dòng đang hiện [lines] (như `Layout.getLineStart/End/EllipsisStart/Count`). */
    fun visible(text: CharSequence, lines: List<Line>): String = buildString {
        for (l in lines) {
            val s = l.start.coerceIn(0, text.length)
            val e = l.end.coerceIn(s, text.length)
            if (l.ellCount <= 0) { append(text, s, e); continue }
            val a = (s + l.ellStart).coerceIn(s, e)
            val b = (a + l.ellCount).coerceIn(a, e)
            append(text, s, a).append('…').append(text, b, e)
        }
    }

    /** Cách hiện GỐC của một ô: chữ đầu tiên hiện TRỌN theo thứ tự đầy › ngắn; không chữ nào trọn ⇒ ngắn (có thì), không thì đầy. */
    fun base(tile: Map<Variant, Shown>): Variant =
        listOf(Variant.FULL, Variant.SHORT).firstOrNull { tile[it]?.cut == false }
            ?: if (Variant.SHORT in tile) Variant.SHORT else Variant.FULL

    /**
     * Chọn cách hiện cho MỌI ô của một lưới. [tiles] = với mỗi ô, chữ thấy được dưới từng cách hiện nó có (`null` = ô
     * không có chữ tên đang hiện — chỉ-icon, ô số…). Hai bước:
     *  1. mỗi ô lấy [base];
     *  2. luật PHÂN BIỆT: một nhóm ô cùng hiện một chữ ⇒ thử từng cách hiện (thứ tự [Variant]) cho CẢ nhóm (ô thiếu cách
     *     đó dùng cách gần nhất — [nearest]), chấm điểm = số ô của nhóm hiện chữ không trùng ô nào khác; điểm cao nhất
     *     (hoà ⇒ cách đứng trước) mà cao hơn hiện tại thì áp cho cả nhóm. Cả nhóm cùng một cách để hai nút cùng loại
     *     trông cùng kiểu (`…trái` / `…phải`, không phải `Kính…` / `…phải`). Lặp tới khi không nhóm nào đổi (có trần).
     */
    fun choose(tiles: List<Map<Variant, Shown>?>): List<Variant?> {
        val pick = tiles.map { t -> t?.let(::base) }.toMutableList()
        fun shown(i: Int, v: Variant): String? = tiles[i]?.let { t -> t[nearest(t, v)]?.text }
        repeat(tiles.size + 1) {
            val live = pick.indices.filter { pick[it] != null }
            val groups = live.groupBy { shown(it, pick[it]!!) }
                .filter { (text, g) -> !text.isNullOrBlank() && g.size > 1 }.values
            if (groups.isEmpty()) return pick
            var moved = false
            for (g in groups) {
                val others = live.filter { it !in g }.mapNotNull { shown(it, pick[it]!!) }
                fun score(ts: List<String?>): Int = ts.count { t -> t != null && t !in others && ts.count { it == t } == 1 }
                val now = score(g.map { shown(it, pick[it]!!) })
                val best = Variant.values().maxByOrNull { v -> score(g.map { shown(it, v) }) }!!
                if (score(g.map { shown(it, best) }) > now) {
                    g.forEach { pick[it] = nearest(tiles[it]!!, best) }
                    moved = true
                }
            }
            if (!moved) return pick
        }
        return pick
    }

    /**
     * Soát vòng 5 (P3) — mô tả trợ năng của MỘT chữ tên: đang hiện bản NGẮN ([short]) ⇒ bản ĐẦY [full] (TalkBack đọc tên đầy
     * đủ); bản đầy (kể cả `…` cuối/đầu — `TextView` báo cho trợ năng CHÍNH chữ của nó, không phải phần thấy được) ⇒ mô tả của
     * bộ dựng [builder] (thường `null`). Tầng vẽ ghi kết quả lên CHÍNH `TextView` tên, KHÔNG lên ô bấm bọc nó: mô tả của ô
     * bấm THAY cả cây con ⇒ TalkBack mất chữ chọn (`EV`/`AUTO`) của nút nhiều lựa chọn và con số của ô đọc (`23°`).
     */
    fun spoken(short: Boolean, full: CharSequence, builder: CharSequence?): CharSequence? = if (short) full else builder

    /** [v] nếu ô có; không thì cách gần nhất — bỏ nhãn ngắn trước, rồi bỏ cắt đầu (đầy-cắt-cuối ô nào cũng có). */
    fun nearest(tile: Map<Variant, Shown>, v: Variant): Variant = when {
        v in tile -> v
        Variant.of(false, v.start) in tile -> Variant.of(false, v.start)
        Variant.of(v.short, false) in tile -> Variant.of(v.short, false)
        else -> Variant.FULL
    }
}
