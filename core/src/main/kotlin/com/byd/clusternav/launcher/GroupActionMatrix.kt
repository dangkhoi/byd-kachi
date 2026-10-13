package com.byd.clusternav.launcher

/**
 * ═══ 3.01 R22 — BẢNG **VẬT × VIỆC** của ô nhóm (owner 13/10: *"nhìn như này nó đâu có OK em, rối mà"*) ═══════════
 *
 * ## [ĐO] bệnh — ảnh owner, nhóm *Kính* ở ô 2×3
 * Một hàng đọc `Kính lái 0 % · Kính phụ 35 %` (nhãn xuống 2 dòng) rồi **11 nút phẳng cùng cỡ** xếp 3 cột, hàng cuối
 * hụt một ô: `Kính lái · Kính phụ · Kính ST · Kính SP · 4 kính · 50% lái · 50% phụ · 50% ST · 50% SP · 50% 4 kính ·
 * Đóng cả cụm`. Thật ra đó chỉ là **hai việc** (`100%` · `50%`) lặp lại trên **năm vật** + một nút lẻ. Lưới phẳng
 * bắt mắt đọc 11 nhãn để tìm ra cấu trúc mà dữ liệu đã có sẵn.
 *
 * ## Chữa — dựng cấu trúc từ DỮ LIỆU, không từ mã nhóm (CLAUDE.md §7)
 *  • **Vật** = [GroupActionCell.icon]: các nút điều khiển CÙNG một vật dùng chung một hình (`ic-car-top-window-lf`
 *    cho cả `win_lf` lẫn `win_half_lf`) — đó là quy ước có sẵn của [ControlRegistry], không phải luật mới.
 *  • **Việc** = [GroupActionCell.verb] ([ControlDef.verb] — dữ liệu của dòng nút). Nút không khai động từ ⇒ không vào bảng.
 *  • **Cột** = động từ có mặt ở ≥ [MIN_TARGETS] vật; cần ≥ [MIN_VERBS] cột mới gọi là bảng (một cột thì chỉ là danh sách).
 *  • **Số** của vật = ô XEM có mã trùng [GroupActionCell.readKey] của một nút trong hàng ⇒ hàng đọc và hàng nút GỘP
 *    làm một (hết cảnh `Kính lái` hiện hai lần: một lần là số, một lần là nút).
 *  • Nút còn lại (không động từ, động từ chỉ một vật có, trùng ô) ⇒ [ActionMatrix.footer], giữ thứ tự khai.
 *
 * Nhóm không có nút nào khai động từ ⇒ `null` ⇒ bộ vẽ đi đường cũ, **không đổi một pixel** (Đèn · Cửa & khoang hôm nay).
 */
data class ActionMatrix(
    val verbs: List<String>,
    val rows: List<Row>,
    val footer: List<GroupActionCell>,
) {
    /**
     * Một hàng = một vật; [title] = nhãn ĐÃ theo ngôn ngữ (nhãn ô XEM, hoặc nhãn nút cột đầu). [slots] dài đúng bằng [verbs]; `null` = vật này không có việc đó (ô trống GIỮ chỗ để các cột
     * vẫn thẳng hàng dọc). [cell] = số của vật (nếu nhóm có ô XEM cho nó).
     */
    data class Row(
        val target: String, val title: String, val cell: GroupCell?, val slots: List<GroupActionCell?>,
        /** Vị trí THẬT của vật trên thân xe ([GroupActionMatrix.placeOf]); `null` = việc của cả xe (hàng "4 kính"). */
        val place: CarPart? = null,
    )

    /** Ô XEM đã gộp vào hàng — bộ vẽ không vẽ chúng lần thứ hai ở lưới đọc. */
    val mergedCellIds: Set<String> get() = rows.mapNotNull { it.cell?.id }.toSet()

    /** Hàng đặt được lên hình xe (mỗi vật một góc thân xe) — bảng vẽ BOARD; còn lại là việc của cả xe. */
    val placed: List<Row> get() = rows.filter { it.place != null }

    /** Hàng KHÔNG có vị trí (việc của cả xe) — thanh nút dưới hình xe, cùng [footer]. */
    val whole: List<Row> get() = rows.filter { it.place == null }

    /**
     * Có vẽ được lên hình xe không: ≥ 2 vật có vị trí, vị trí không trùng nhau (hai vật cùng một góc thì hình xe nói
     * sai), và mọi vị trí là CỬA (các thẻ số đặt hai bên thân xe, cạnh đúng cửa đó).
     */
    val boardable: Boolean
        get() = placed.size >= GroupActionMatrix.MIN_TARGETS &&
            placed.map { it.place }.distinct().size == placed.size && placed.all { it.place!!.isDoor }

    /** Bảng có cột số không (không hàng nào có ô XEM ⇒ không chừa cột số). */
    val hasValues: Boolean get() = rows.any { it.cell != null }

    /**
     * 3.01 R22·C (owner 13/10 *"thêm cả cốp vào đó luôn"*) — nút KHÔNG động từ mà vật của nó có chỗ ở ĐẦU/ĐUÔI hình xe
     * ([GroupActionMatrix.placeOf] + [GroupActionMatrix.atCarEnd]; hôm nay: cốp `ic-car-top-trunk`) ⇒ một chấm chạm được
     * trên hình xe gọn. Vẫn nằm trong [footer] cho danh sách gọn (dạng lùi) — không mất nút nào.
     */
    val onCar: List<Pair<CarPart, GroupActionCell>>
        get() = footer.mapNotNull { a -> GroupActionMatrix.placeOf(a.icon)?.takeIf { GroupActionMatrix.atCarEnd(it) }?.let { it to a } }

    /** Việc *"mở tất cả"* = việc của CẢ XE ([whole]) có mức cao nhất (> 0) — đọc [GroupActionCell.level], không tên nút. */
    val openAll: GroupActionCell?
        get() = whole.flatMap { it.slots }.filterNotNull().filter { (it.level ?: 0) > 0 }.maxByOrNull { it.level ?: 0 }

    /**
     * 3.01 POLISH (owner 13/10 *"phần kính thêm 1 action là 50% tất cả kính"*) — việc *"hé tất cả"* = việc của CẢ XE có mức
     * dương THẤP nhất, khác (thấp hơn) [openAll]. Đọc [GroupActionCell.level], không tên nút (hôm nay: `win_half_all`,
     * `setAllWindowState(4,4,4,4)`). Chỉ một mức dương ⇒ `null` (không có nấc giữa để hé).
     */
    val halfAll: GroupActionCell?
        get() {
            val top = openAll ?: return null
            return whole.flatMap { it.slots }.filterNotNull()
                .filter { (it.level ?: 0) in 1 until (top.level ?: 0) }.minByOrNull { it.level ?: 0 }
        }

    /** Việc *"đóng tất cả"* = việc của cả xe / nút lẻ có mức 0. */
    val closeAll: GroupActionCell?
        get() = (whole.flatMap { it.slots }.filterNotNull() + footer).firstOrNull { it.level == 0 }
}

object GroupActionMatrix {

    /** Một động từ phải lặp ở ít nhất chừng này vật mới thành CỘT — một vật thì nó là nút lẻ, xuống chân bảng. */
    const val MIN_TARGETS = 2

    /** Ít nhất chừng này cột mới dựng bảng; ít hơn thì lưới nút cũ đã đủ rõ (không có gì "lặp" để gom). */
    const val MIN_VERBS = 2

    /** Bảng vật × việc của [m], hoặc `null` nếu các nút của nhóm không có dạng đó. Thuần, không đọc gì ngoài [m]. */
    fun of(m: GroupBoardModel): ActionMatrix? {
        val withVerb = m.actions.filter { it.verb.isNotEmpty() }
        val byTarget = LinkedHashMap<String, MutableList<GroupActionCell>>()
        withVerb.forEach { byTarget.getOrPut(it.icon) { mutableListOf() }.add(it) }
        val verbs = withVerb.map { it.verb }.distinct()
            .filter { v -> byTarget.values.count { list -> list.any { it.verb == v } } >= MIN_TARGETS }
        if (verbs.size < MIN_VERBS) return null
        val cells = m.cells.associateBy { it.id }
        val placed = HashSet<String>()
        val rows = byTarget.mapNotNull { (target, list) ->
            val slots = verbs.map { v -> list.firstOrNull { it.verb == v } }
            val first = slots.firstOrNull { it != null } ?: return@mapNotNull null
            slots.forEach { it?.let { a -> placed += a.id } }
            val cell = list.firstNotNullOfOrNull { a -> a.readKey.takeIf { it.isNotEmpty() }?.let { cells[it] } }
            ActionMatrix.Row(target, cell?.label ?: first.label, cell, slots, placeOf(target))
        }
        if (rows.size < MIN_TARGETS) return null
        return ActionMatrix(verbs, rows, m.actions.filter { it.id !in placed })
    }

    /**
     * Vị trí trên thân xe của một vật, đọc từ HẬU TỐ khoá vật theo quy ước thân xe `-lf · -rf · -lr · -rr` (xem KDoc
     * [CarPart] — cùng quy ước với mã datum `door_lf`/`window_lf` và icon `ic-car-top-window-lf`). Không khớp ⇒ `null`
     * (vật của cả xe). Đây là phép đọc một quy ước ĐÃ CÓ, không phải bảng tra theo tên nhóm.
     */
    fun placeOf(target: String): CarPart? = CarPart.values().firstOrNull { target.endsWith("-" + it.key) }

    /**
     * Vật [p] nằm ở ĐẦU hoặc ĐUÔI thân xe (neo chung [CarLayout] ≤ [FRONT_END] hoặc ≥ [REAR_END]) — có chỗ cho một DẢI chạm
     * ngang trước/sau vùng bốn cửa. Cốp (0,90) có; nóc/rèm (giữa thân) không ⇒ không lên hình xe gọn (vùng chạm sẽ đè cửa).
     */
    fun atCarEnd(p: CarPart): Boolean = !p.isDoor && CarLayout.part(p).y.let { it <= FRONT_END || it >= REAR_END }

    /** Ranh "đầu xe" / "đuôi xe" theo hệ chuẩn hoá [CarLayout] (y 0 = đầu xe). */
    const val FRONT_END = 0.2f
    const val REAR_END = 0.8f

    // ── Co giãn theo chỗ (đo bằng px ở `:app`, quyết ở đây — cùng lối [GroupCellFit]) ─────────────────────────

    /**
     * Số đo thật của bảng (px, `:app` đo bằng `Paint`).
     *
     * @property labelPx nhãn vật dài nhất, MỘT dòng · [valuePx] số rộng nhất (0 nếu bảng không có cột số) ·
     *   [buttonPx] bề rộng một nút (đã ≥ đích chạm) · [footerPx] bề rộng cả hàng chân (0 nếu không có chân) ·
     *   [touchPx] cao tối thiểu một hàng = đích chạm · [gap] khe giữa các cột · [blockGap] khe giữa hai khối bảng.
     */
    data class Metrics(
        val labelPx: Int, val valuePx: Int, val labelLine: Int, val valueLine: Int,
        val buttonPx: Int, val footerPx: Int, val touchPx: Int, val gap: Int, val blockGap: Int,
    )

    /**
     * Kế hoạch: [stacked] = số nằm DƯỚI nhãn (hẹp) thay vì cạnh nhãn · [cols] số cột nút giữ lại (bỏ từ PHẢI — cột đầu
     * là việc chính) · [blocks] số khối bảng đặt cạnh nhau (ô rộng mà thấp) · [rowsShown] số hàng vật hiện (bỏ từ cuối
     * khi ô thấp; chân bảng luôn giữ) · [perBlock] hàng mỗi khối · [labelPx] bề rộng cột nhãn (có thể bị kẹp ⇒ "…").
     * [fits] = hiện đủ mọi hàng và mọi cột.
     */
    data class Plan(
        val stacked: Boolean, val cols: Int, val blocks: Int, val rowsShown: Int, val perBlock: Int,
        val rowPx: Int, val labelPx: Int, val fits: Boolean,
    )

    /** Bề rộng một hàng ở dạng [stacked] với [cols] cột nút. */
    fun rowWidth(m: Metrics, stacked: Boolean, cols: Int): Int {
        val left = if (stacked || m.valuePx == 0) maxOf(m.labelPx, m.valuePx) else m.labelPx + m.gap + m.valuePx
        return left + cols * (m.buttonPx + m.gap)
    }

    /** Cao một hàng: không dưới đích chạm; dạng xếp chồng cao bằng nhãn + số. */
    fun rowHeight(m: Metrics, stacked: Boolean): Int =
        maxOf(m.touchPx, if (stacked && m.valuePx > 0) m.labelLine + m.valueLine else maxOf(m.labelLine, m.valueLine))

    /**
     * Chọn dạng cho [rows] hàng vật + (nếu [footer]) một hàng chân, trong khung [widthPx] × [heightPx] (`null` = không trần).
     *
     * Thứ tự nhường — **giữ chức năng trước, giữ dáng sau**: (1) đủ cột, số cạnh nhãn → (2) đủ cột, số dưới nhãn →
     * (3) bớt cột từ phải → (4) kẹp nhãn bằng "…". Nhãn nút KHÔNG bao giờ xuống dòng (bề rộng nút đã đo đủ chữ). Với mỗi
     * dạng vừa bề ngang: thử 1, 2, … khối cạnh nhau (ô rộng mà thấp) tới khi đủ bề cao; thiếu bề cao cả khi đã chia khối
     * tối đa ⇒ bỏ hàng vật từ CUỐI (thứ tự khai = thứ tự ưu tiên), chân bảng giữ.
     */
    fun plan(rows: Int, verbs: Int, footer: Boolean, widthPx: Int, heightPx: Int?, m: Metrics): Plan {
        val slots = rows + if (footer) 1 else 0
        val forms = buildList {
            for (cols in verbs downTo 1) { add(false to cols); add(true to cols) }
        }
        var partial: Plan? = null
        for ((stacked, cols) in forms) {
            val rowW = rowWidth(m, stacked, cols)
            if (rowW > widthPx) continue
            val rowH = rowHeight(m, stacked)
            val maxBlocks = maxOf(1, (widthPx + m.blockGap) / (rowW + m.blockGap))
            for (k in 1..maxBlocks) {
                val blockW = (widthPx - (k - 1) * m.blockGap) / k
                if (footer && m.footerPx > blockW) break
                val per = (slots + k - 1) / k
                if (heightPx == null || per * rowH <= heightPx) {
                    return Plan(stacked, cols, k, rows, per, rowH, m.labelPx, cols == verbs)
                }
            }
            // Vừa bề ngang mà thiếu bề cao ⇒ ghi lại phương án BỎ HÀNG tốt nhất của dạng đầu tiên vừa bề ngang.
            if (partial == null && heightPx != null) {
                var k = maxBlocks
                while (k > 1 && footer && m.footerPx > (widthPx - (k - 1) * m.blockGap) / k) k--
                val per = maxOf(1, heightPx / rowH)
                val shown = (per * k - if (footer) 1 else 0).coerceIn(0, rows)
                val used = shown + if (footer) 1 else 0
                partial = Plan(stacked, cols, k, shown, (used + k - 1) / k, rowH, m.labelPx, false)
            }
        }
        partial?.let { return it }
        // Hẹp tới mức một cột nút + số dưới nhãn vẫn tràn ⇒ kẹp NHÃN (nút giữ nguyên bề rộng chạm được).
        val label = (widthPx - (m.buttonPx + m.gap)).coerceAtLeast(0)
        val rowH = rowHeight(m, true)
        val per = if (heightPx == null) slots else maxOf(1, heightPx / rowH)
        val shown = (per - if (footer) 1 else 0).coerceIn(0, rows)
        return Plan(true, 1, 1, shown, minOf(per, slots), rowH, label, false)
    }

    // ── 3.01 R22·B — BẢNG HÌNH XE GỌN cho ô nhỏ (owner: ô Kính 2×3 mất `Kính SP` + `4 kính` ở danh sách gọn) ─────────
    //
    // Ở ô nhỏ, đích chạm không còn là NÚT cạnh vật mà là CHÍNH VẬT trên hình xe: mỗi vật một VÙNG chạm (≥ đích chạm,
    // nở ra quanh hình vẽ — vùng là cả phần tư khung, không phải vệt kính 6 px), chạm = bước kế của vòng đóng → 50 % →
    // mở hẳn ([WindowCycle], R22·C — công tắc chế độ đã gỡ), chân = hai nút hình mở/đóng tất cả. Thứ tự lùi theo chỗ đo:
    // hình xe gọn ([compactForm]) → danh sách gọn ([plan], chỉ dưới ngưỡng hình xe) → ô tóm tắt (khung ngoài).
    // ⚠ Owner 13/10 bác MỌI lưới nút chữ lặp (`100%`/`50%` cạnh từng kính) ở mọi cỡ ⇒ hình xe "đầy đủ" có thẻ + nút cạnh
    // từng cửa (và phép `boardFits` của nó) đã GỠ; ô lớn dùng chính hình xe gọn, chỉ to hơn.

    /** Cách xếp hình xe gọn: [STACK] = xe trên · chân dưới (ô cao/vuông); [ROW] = xe trái · chân phải (ô rộng-thấp). */
    enum class CompactForm { STACK, ROW }

    /**
     * Số đo thật (px) của hình xe gọn. 3.01 R22·C (owner 13/10 *"nhấn vô các hình tròn cho nó mở đóng luôn"*): công tắc chế
     * độ `Mở hẳn | Hé` đã GỠ — chạm chấm tự đi vòng đóng → 50 % → mở hẳn → đóng ([WindowCycle]) ⇒ không còn số đo công tắc.
     * [minCarPx] = cao tối thiểu của hình xe · [bars]/[barPx] = số nút chân và bề rộng một nút · [ends] = số DẢI chạm đầu/
     * đuôi xe (cốp trên hình) — mỗi dải cần thêm một đích chạm theo bề cao · [touchPx] = đích chạm · [gap] = khe.
     */
    data class CompactMetrics(
        val touchPx: Int, val gap: Int, val minCarPx: Int, val bars: Int, val barPx: Int, val ends: Int = 0,
    )

    /** Vùng xe (khung chạm của các vật) theo dạng [f]: trả `(rộng, cao)`; âm/0 = không còn chỗ. */
    fun compactCarArea(f: CompactForm, widthPx: Int, heightPx: Int, m: CompactMetrics): Pair<Int, Int> = when (f) {
        CompactForm.STACK -> widthPx to heightPx - if (m.bars != 0) m.touchPx + m.gap else 0
        CompactForm.ROW -> widthPx - (if (m.bars != 0) m.bars * m.barPx + (m.bars - 1) * m.gap + m.gap else 0) to heightPx
    }

    /**
     * Dạng hình xe gọn VỪA khung [widthPx] × [heightPx], hoặc `null` (lùi danh sách gọn). Điều kiện — mọi thứ phải
     * CHẠM được và THẤY được, không chỉ "vẽ lọt": vùng xe chia đôi bề ngang vẫn ≥ đích chạm, bề cao đủ hai hàng cửa + mỗi
     * dải đầu/đuôi ([CompactMetrics.ends]) một đích chạm · hình xe cao ≥ [CompactMetrics.minCarPx] và còn ≥ nửa chừng đó
     * bề ngang sau khi chừa khe hai bên · chân lọt bề ngang (STACK) / bề cao (ROW). Thử [CompactForm.STACK] trước.
     */
    fun compactForm(widthPx: Int, heightPx: Int, m: CompactMetrics): CompactForm? {
        val barsW = m.bars * m.barPx + (m.bars - 1).coerceAtLeast(0) * m.gap
        for (f in CompactForm.values()) {
            val (w, h) = compactCarArea(f, widthPx, heightPx, m)
            val carW = w - 2 * m.gap
            if (w < 2 * m.touchPx || h < (2 + m.ends) * m.touchPx || h < m.minCarPx || carW < m.minCarPx / 2) continue
            val ok = when (f) {
                CompactForm.STACK -> barsW <= widthPx
                CompactForm.ROW -> m.bars == 0 || m.touchPx <= heightPx
            }
            if (ok) return f
        }
        return null
    }

    /** Một vùng chạm (px, nửa mở `[left, right) × [top, bottom)`). Thuần — `:app` đổi sang `Rect`. */
    data class Zone(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    // 3.01 TAPFIX — `zones`/`band` (phần tư + dải đầu/đuôi theo neo CHUNG, nở tới mép ô) đã GỠ: vùng không gắn với chỗ chấm
    // VẼ ⇒ owner 13/10 *"click lên xa trên top… không đúng vùng"*. Chủ cú chạm nay = [SpotTouch.owner] trên tâm vẽ thật.
}
