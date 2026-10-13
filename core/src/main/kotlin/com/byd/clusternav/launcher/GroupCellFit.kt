package com.byd.clusternav.launcher

/**
 * ═══ 3.01 R20 · WIDGET-FIT-CELLS — ô con của ô NHÓM (Chuyến đi · Năng lượng · Khí hậu · Kính · Đèn …) chọn số cột + dạng
 * theo BỀ RỘNG ĐO ĐƯỢC và chỗ chữ THẬT cần (thuần, `:core`, đơn vị px) ═══════════════════════════════════════════════════
 *
 * Bệnh [ĐO uiautomator máy ảo kachi_play, 3.1 vehicleTest, tiếng Việt, 13/10 — `docs/diagnostics/widget-fit-301-2026-10-13.md`]:
 *  - *Chuyến đi* ở khung 2×3 (hộp nội dung 267 px): hàng số phụ chia CỐ ĐỊNH 3 cột ⇒ nhãn `Thời gian chuyến` còn **38 px**
 *    (vỡ thành `Th/ời …`), `Tiêu thụ 50km` 38 px;
 *  - *Đèn* 5 cột cố định ⇒ nhãn `Đèn sương mù trước` còn **2 px**; *Kính* 15 px; *Khí hậu* hàng thứ ba cao 25 px (số 13 px — bị
 *    cắt nửa).
 * Gốc [SUY mã]: `GroupTileView.gridRows(cells, perRow)` lấy số cột = TRẦN cố định (5 dải / 3 thẻ) bất kể bề rộng, ô con xếp
 * NGANG nhãn·số với nhãn `weight 1` ⇒ ô hẹp thì nhãn nhường tới 0 và bị cắt thành mảnh.
 *
 * Luật (một bộ cho MỌI nhóm — không theo mã nhóm, CLAUDE.md §7):
 *  1. **Số cột** = nhiều nhất (≤ trần cũ, để ô rộng KHÔNG đổi — R21) mà MỌI ô con đang hiện vừa trọn chữ ở một [Form];
 *  2. **Dạng** theo BẬC ([tiers]): (icon nếu được xin) → nhãn MỘT dòng (ngang nhãn·số, rồi dọc nhãn trên số dưới) → nhãn
 *     HAI dòng (ngang, rồi dọc). Mỗi bậc thử hết số cột (nhiều → ít) trước khi xuống bậc sau: thà một cột danh sách gọn
 *     `Thời gian chuyến … —` còn hơn ba cột nhãn vỡ dòng. Nhãn 2 dòng chỉ ngắt ở DẤU CÁCH ([twoLineWidth]) — không giữa từ;
 *  3. **Chiều cao** (khi chỗ gọi đưa trần — lượt nhường của ô nhóm): không vừa ở số mục hiện tại với mọi số cột ⇒ bỏ mục
 *     CUỐI (thứ tự khai trong [CapabilityGroups] = thứ tự trình bày = ưu tiên) rồi thử lại; không bao giờ dưới 1 mục;
 *  4. **Giá trị** không bao giờ bị cắt: bề rộng giá trị là đầu vào cứng của mọi phép "vừa" (chỗ gọi đo bằng `Paint` thật, đã
 *     chừa chữ số rộng nhất — [FitValues.headroom]).
 *
 * Mọi số đo đến từ `:app` (`Paint.measureText` + đo `TextView` thật, theo đúng ngôn ngữ đang hiện) ⇒ không có hằng dp nào để
 * đoán sai, đúng cho tiếng Việt (dài nhất trong 5 thứ tiếng ở các nhãn này) lẫn Thái/Mã Lai.
 */
object GroupCellFit {

    /** Cách xếp một ô con: [SIDE] = nhãn · số cùng hàng; [STACK] = (icon) / nhãn / số chồng dọc. */
    enum class Form { SIDE, STACK }

    /**
     * Một dạng thử: [form] + số dòng nhãn tối đa [lines] (1|2) + có hiện icon không.
     */
    data class Option(val form: Form, val lines: Int, val icon: Boolean)

    /**
     * Chỗ chữ THẬT một ô con cần (px, chỗ gọi đo):
     * @property label1 bề rộng nhãn trên MỘT dòng.
     * @property label2 bề rộng nhỏ nhất để nhãn nằm trong ≤ 2 dòng mà không ngắt giữa từ ([twoLineWidth]).
     * @property value bề rộng giá trị (đã chừa chữ số, làm tròn lên — [FitValues.needPx]).
     */
    data class Need(val label1: Int, val label2: Int, val value: Int)

    /**
     * Hằng hình học của lưới (px, chỗ gọi quy từ thang [KachiSpace] / đo `TextView`):
     * [labelLine]/[labelLine2] cao nhãn 1/2 dòng · [valueLine] cao giá trị · [icon] cạnh icon (0 = không có) · [padX]/[padY] lề
     * trong ô con · [inner] khe nhãn–số (ngang) hoặc icon–nhãn (dọc) · [margin] lề NGOÀI mỗi ô con (cả bốn phía).
     */
    data class Metrics(
        val labelLine: Int, val labelLine2: Int, val valueLine: Int, val icon: Int,
        val padX: Int, val padY: Int, val inner: Int, val margin: Int,
        /** Khe nhãn–số của dạng ngang (mặc định = [inner]). [ĐO] 13/10: khe 6 px ⇒ `Trong xe22 °C` đọc dính. */
        val sideGap: Int = inner,
    )

    /**
     * Kết quả: [shown] mục ĐẦU được hiện (phần còn lại ẩn), xếp theo [rowCounts] (chia đều như `GroupTileView.rowsOf`), mỗi ô
     * con ở [option]; [rowPx] = chiều cao MỘT hàng cần (kể cả lề ngoài); [fits] = `false` khi ngay một mục ở một cột vẫn không
     * vừa (chỗ gọi vẫn vẽ — dạng hẹp nhất — nhưng biết là tràn).
     */
    data class Plan(
        val shown: Int, val rowCounts: List<Int>, val option: Option, val rowPx: Int, val fits: Boolean,
        /** Hai cột dùng chung của dạng [Form.SIDE] ([sideColumns]); `null` ở dạng dọc. */
        val columns: Columns? = null,
    ) {
        val cols: Int get() = rowCounts.maxOrNull() ?: 0
        val heightPx: Int get() = rowCounts.size * rowPx
    }

    /**
     * Bề rộng nhỏ nhất (px) để một nhãn gồm các từ rộng [words] (px, cách nhau bởi dấu cách rộng [space]) nằm trong ≤ 2 dòng
     * khi `TextView` ngắt THAM LAM ở dấu cách (`breakStrategy = SIMPLE`, không gạch nối): `min` theo chỗ ngắt `k` của
     * `max(dòng 1, dòng 2)`. Ở bề rộng đó phép ngắt tham lam lấy ≥ `k` từ cho dòng 1 nên dòng 2 càng ngắn ⇒ vừa 2 dòng.
     * Luôn ≥ từ dài nhất ⇒ không bao giờ phải ngắt giữa từ (thứ bị cắt thành `Th/ời`). Rỗng ⇒ 0.
     */
    fun twoLineWidth(words: List<Int>, space: Int): Int {
        if (words.isEmpty()) return 0
        if (words.size == 1) return words[0]
        var best = Int.MAX_VALUE
        for (k in 1 until words.size) {
            val a = words.subList(0, k).sum() + space * (k - 1)
            val b = words.subList(k, words.size).sum() + space * (words.size - k - 1)
            best = minOf(best, maxOf(a, b))
        }
        return best
    }

    /**
     * Hai CỘT DÙNG CHUNG của dạng ngang: [label] (nhãn canh PHẢI, sát số) + [value] (số canh TRÁI) — cả cặp canh GIỮA ô con.
     *
     * Owner 13/10 (máy ảo, số giả): *"chỉ số bị lệch phải… nằm chơi vơi"* — [ĐO] bản trước nhãn `weight 1` canh giữa + số
     * `WRAP` dính mép phải ⇒ ô rộng 255 px: `Gió` ở giữa, `4` ở mép, khoảng trống ~150 px giữa nhãn và số; mỗi hàng một vị
     * trí số khác nhau. Nay MỌI ô con cùng lưới dùng cùng hai bề rộng ⇒ số của mọi hàng thẳng một trục, nhãn sát số.
     */
    data class Columns(val labelPx: Int, val valuePx: Int)

    /**
     * Hai cột của [use] ở dạng ngang [o] trong ô con rộng [cellW]; `null` = không vừa. Cột số = số DÀI NHẤT (không bao giờ
     * cắt số); cột nhãn = nhãn dài nhất (1 dòng nếu vừa phần còn lại, không thì bản 2 dòng ngắt ở dấu cách khi [o] cho 2).
     */
    fun sideColumns(use: List<Need>, o: Option, m: Metrics, cellW: Int): Columns? {
        if (use.isEmpty()) return null
        val value = use.maxOf { it.value }
        val room = cellW - 2 * m.padX - m.sideGap - value
        val label = use.maxOf { if (o.lines == 1 || it.label1 <= room) it.label1 else it.label2 }
        return if (label <= room) Columns(label, value) else null
    }

    /** Bề rộng ô con (không kể lề ngoài) mà [n] mục cần ở dạng [o]. */
    fun cellWidth(n: Need, o: Option, m: Metrics): Int {
        val label = if (o.lines == 1) n.label1 else n.label2
        return 2 * m.padX + when (o.form) {
            Form.SIDE -> label + m.sideGap + n.value
            Form.STACK -> maxOf(label, n.value, if (o.icon) m.icon else 0)
        }
    }

    /** Chiều cao ô con (không kể lề ngoài) — nhãn 1 dòng nếu vừa một dòng ở [cellW], không thì 2. */
    fun cellHeight(n: Need, o: Option, m: Metrics, cellW: Int): Int {
        val labelW = if (o.form == Form.SIDE) cellW - 2 * m.padX - m.sideGap - n.value else cellW - 2 * m.padX
        val labelH = if (o.lines == 1 || n.label1 <= labelW) m.labelLine else m.labelLine2
        return 2 * m.padY + when (o.form) {
            Form.SIDE -> maxOf(labelH, m.valueLine)
            Form.STACK -> (if (o.icon) m.icon + m.inner else 0) + labelH + m.valueLine
        }
    }

    /** Chiều cao ô con dạng ngang theo hai cột [c]: nhãn 1 dòng nếu vừa cột nhãn, không thì 2. */
    fun sideHeight(n: Need, m: Metrics, c: Columns): Int =
        2 * m.padY + maxOf(if (n.label1 <= c.labelPx) m.labelLine else m.labelLine2, m.valueLine)

    /**
     * Các BẬC dạng (luật 2 KDoc lớp), mỗi bậc thử hết số cột trước bậc sau; trong một bậc, ở cùng số cột, dạng đứng trước
     * thắng. [icons] = chỗ gọi xin icon (dải STRIP có icon phân biệt được) ⇒ hai bậc có icon đứng đầu.
     */
    fun tiers(icons: Boolean): List<List<Option>> {
        // 3.01 R22 (owner 13/10 "nhìn thô" → brief Kim §2.2): HÀNG `nhãn · số` là ngôn ngữ chính của thẻ — mọi dạng NGANG
        // (1 rồi 2 dòng nhãn) được thử ở MỌI số cột TRƯỚC dạng DỌC. Trước: cùng bậc ⇒ 2 cột DỌC thắng 1 cột NGANG, ra lưới
        // ô "nhãn trên / số dưới" lơ lửng và ăn gấp đôi chiều cao (Chuyến đi 2×3 mất mục Odo khi số chính to lên).
        val plain = listOf(
            listOf(Option(Form.SIDE, 1, false)), listOf(Option(Form.SIDE, 2, false)),
            listOf(Option(Form.STACK, 1, false)), listOf(Option(Form.STACK, 2, false)),
        )
        return if (icons) listOf(listOf(Option(Form.STACK, 1, true)), listOf(Option(Form.STACK, 2, true))) + plain else plain
    }

    /**
     * Chọn bố cục cho [needs] (theo thứ tự ưu tiên) trong bề rộng [widthPx] và trần cao [heightPx] (`null` = không trần — lượt
     * đo đầu của ô nhóm: lưới báo đúng chỗ nó CẦN để chỗ gọi thấy tràn mà cho phần khác nhường trước), tối đa [maxCols] cột.
     */
    fun plan(needs: List<Need>, widthPx: Int, heightPx: Int?, m: Metrics, maxCols: Int, icons: Boolean): Plan {
        val all = tiers(icons)
        if (needs.isEmpty() || maxCols <= 0) return Plan(0, emptyList(), all.first().first(), 0, true)
        val limit = heightPx ?: Int.MAX_VALUE
        for (shown in needs.size downTo 1) {
            val use = needs.subList(0, shown)
            // Bỏ icon / nhãn xuống 2 dòng TRƯỚC khi bỏ một mục (luật 3: thà mất icon còn hơn mất một con số).
            for (group in all) {
                for (cols in minOf(shown, maxCols) downTo 1) {
                    val counts = GridFit.rowCounts(shown, cols, GridFit.RowSplit.BALANCED)
                    val cellW = widthPx / counts.max() - 2 * m.margin
                    for (o in group) {
                        val c = if (o.form == Form.SIDE) sideColumns(use, o, m, cellW) ?: continue else null
                        if (c == null && use.any { cellWidth(it, o, m) > cellW }) continue
                        val row = use.maxOf { if (c != null) sideHeight(it, m, c) else cellHeight(it, o, m, cellW) } + 2 * m.margin
                        if (counts.size * row <= limit) return Plan(shown, counts, o, row, true, c)
                    }
                }
            }
        }
        // Một mục ở một cột vẫn không vừa: vẽ dạng hẹp nhất (dọc, 2 dòng) cho mục ĐẦU — tràn được chỗ gọi thấy ([Plan.fits]).
        val o = all.last().last()
        val cellW = widthPx - 2 * m.margin
        return Plan(1, listOf(1), o, cellHeight(needs[0], o, m, cellW) + 2 * m.margin, false)
    }

    /**
     * Số cột cho hàng NÚT của ô nhóm: nhiều nhất (≤ [maxCols], ≤ [n]) mà mỗi ô còn ≥ [minTilePx] (chỗ nhãn nút cần, đo ở
     * `:app`, không dưới đích chạm) sau khi trừ lề ngoài [marginPx] hai bên. Không cột nào vừa ⇒ 1.
     */
    fun actionCols(n: Int, widthPx: Int, minTilePx: Int, marginPx: Int, maxCols: Int): Int {
        if (n <= 0) return 0
        for (cols in minOf(n, maxCols) downTo 1) {
            val counts = GridFit.rowCounts(n, cols, GridFit.RowSplit.BALANCED)
            if (widthPx / counts.max() - 2 * marginPx >= minTilePx) return counts.max()
        }
        return 1
    }
}
