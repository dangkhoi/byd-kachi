package com.byd.clusternav.launcher

/**
 * BỐ CỤC ĐỘNG trên lưới 12 cột × 6 dòng (P9) — phần MODEL, thuần Kotlin (`:core`, cấm `android.*`) ⇒ test off-car.
 *
 * Owner muốn: *"user tự vẽ từng khung theo lưới, gán app / widget / action, lưu thành hồ sơ riêng ⇒ mỗi người một
 * launcher"*.
 *
 * ## Bước này làm gì (và cố ý KHÔNG làm gì)
 * Đây là **bước nền**: chỉ model + kiểm tra + đổi sang khung pixel. **KHÔNG** chạm tầng vẽ, **KHÔNG** đổi định dạng
 * lưu, **KHÔNG** nới trần số ô. Lý do: tầng vẽ là chỗ phần chiếu-app-vào-ô đang sống, và nó vừa được sửa hai lỗi
 * khó (P-bug1, P-bug2). Đổi nền trước rồi đổi tầng vẽ sau, mỗi bước tự kiểm được.
 *
 * ## [ĐO] Bố cục hiện tại có phải trường hợp riêng của lưới không?
 * Câu này quyết định việc lưới **thay thế** hay **cộng thêm**. Đo được:
 *  • **4/5 biểu diễn ĐÚNG**: 1 ô = (0,0,12,6) · 2 cột = 6+6 · 2 hàng = 3+3 · 4 ô = 2×2 — mỗi cái phủ đúng 72/72 ô.
 *  • **Bố cục "3 ô" thì KHÔNG**: cột trái của nó rộng `1.55/2.55 = 0.6078` bề ngang, tức **7.294 cột** — không phải
 *    số nguyên. Ép về 7 cột lệch **2.45%** bề ngang, ép về 8 cột lệch **5.88%**.
 *
 * ⇒ **Kết luận**: lưới là nguồn bố cục **THÊM VÀO**, không thay thế. 5 bố cục sẵn có đã khớp pixel với prototype đã
 * duyệt và đã proven trên máy ảo — viết lại chúng bằng lưới sẽ **làm xấu đi** đúng cái đang đúng.
 */

/**
 * Một khung trên lưới, toạ độ **THEO Ô** (không phải pixel).
 *
 * @property col cột bắt đầu, 0-based.
 * @property row dòng bắt đầu, 0-based.
 * @property cols số cột khung chiếm.
 * @property rows số dòng khung chiếm.
 */
data class GridFrame(val col: Int, val row: Int, val cols: Int, val rows: Int) {
    val colEnd: Int get() = col + cols
    val rowEnd: Int get() = row + rows
    val cellCount: Int get() = cols * rows

    /** Hai khung có chồng nhau không (chồng = hỏng, xem [GridLayout.problems]). */
    fun overlaps(other: GridFrame): Boolean =
        col < other.colEnd && other.col < colEnd && row < other.rowEnd && other.row < rowEnd
}

/**
 * Một bố cục do người dùng vẽ: danh sách khung theo thứ tự (thứ tự = thứ tự ô, khớp cách sắp lại app khi đổi bố cục).
 */
data class GridLayout(val frames: List<GridFrame>) {

    /**
     * Lỗi khiến bố cục **không dùng được**. Rỗng = hợp lệ.
     *
     * Cố ý KHÔNG coi "còn ô trống" là lỗi: người dùng để chỗ trống là **quyền của họ** (nền vẫn hiện ra ở đó). Chỗ
     * trống được báo riêng qua [uncoveredCells] để trình vẽ hiển thị, không phải để chặn.
     */
    fun problems(): List<String> {
        val out = ArrayList<String>()
        if (frames.isEmpty()) out.add("bố cục rỗng: chưa vẽ khung nào")
        frames.forEachIndexed { i, f ->
            if (f.cols < WorkspaceGrid.MIN_COLS || f.rows < WorkspaceGrid.MIN_ROWS) {
                out.add("khung ${i + 1} nhỏ quá (${f.cols}×${f.rows}), tối thiểu " +
                    "${WorkspaceGrid.MIN_COLS}×${WorkspaceGrid.MIN_ROWS}")
            }
            if (f.col < 0 || f.row < 0 || f.colEnd > WorkspaceGrid.COLS || f.rowEnd > WorkspaceGrid.ROWS) {
                out.add("khung ${i + 1} ra ngoài lưới ${WorkspaceGrid.COLS}×${WorkspaceGrid.ROWS}")
            }
        }
        // Chồng nhau: báo từng cặp, vì trình vẽ cần biết ĐÍCH DANH hai khung nào để tô đỏ.
        for (i in frames.indices) {
            for (j in i + 1 until frames.size) {
                if (frames[i].overlaps(frames[j])) out.add("khung ${i + 1} và khung ${j + 1} đè lên nhau")
            }
        }
        return out
    }

    val valid: Boolean get() = problems().isEmpty()

    /** Số ô đã bị các khung phủ (đếm ô trùng một lần — dùng để tính chỗ trống). */
    fun coveredCells(): Int {
        val seen = HashSet<Int>()
        frames.forEach { f ->
            for (c in f.col until f.colEnd) for (r in f.row until f.rowEnd) {
                if (c in 0 until WorkspaceGrid.COLS && r in 0 until WorkspaceGrid.ROWS) seen.add(r * WorkspaceGrid.COLS + c)
            }
        }
        return seen.size
    }

    /** Số ô còn trống — thông tin cho trình vẽ, KHÔNG phải lỗi. */
    fun uncoveredCells(): Int = WorkspaceGrid.TOTAL_CELLS - coveredCells()

    /**
     * Đổi sang khung pixel.
     *
     * **Cùng ngữ nghĩa khe hở với [WorkspaceLayout]**: khe nằm GIỮA các ô, và khung sát mép phải/dưới ăn hết phần
     * làm tròn còn lại (nhờ vậy không bao giờ hở một vạch ở mép). Điều này quan trọng: có nó thì sau này tầng vẽ đổi
     * sang lưới mà bố cục cũ vẫn ra **đúng từng pixel** — đã có test chứng minh cho 4 bố cục biểu diễn được.
     */
    fun slots(width: Int, height: Int, gap: Int = 0): List<SlotRect> {
        require(width > 0 && height > 0) { "width/height must be > 0 (was $width x $height)" }
        val g = gap.coerceAtLeast(0)
        return frames.mapIndexed { i, f ->
            SlotRect(
                index = i,
                left = WorkspaceGrid.edge(f.col, WorkspaceGrid.COLS, width, g),
                top = WorkspaceGrid.edge(f.row, WorkspaceGrid.ROWS, height, g),
                right = WorkspaceGrid.farEdge(f.colEnd, WorkspaceGrid.COLS, width, g),
                bottom = WorkspaceGrid.farEdge(f.rowEnd, WorkspaceGrid.ROWS, height, g),
            )
        }
    }
}

/** Lưới + phép tính biên. */
object WorkspaceGrid {

    /** Owner chốt: 12 cột × 6 dòng. */
    const val COLS = 12
    const val ROWS = 6
    const val TOTAL_CELLS = COLS * ROWS

    /**
     * Khung tối thiểu. 2×1 vì một khung rộng 1 cột (1/12 bề ngang ≈ 160px trên màn 1920) thì không hiện nổi thứ gì
     * có nghĩa — không phải giới hạn kỹ thuật mà là giới hạn **dùng được**.
     */
    const val MIN_COLS = 2
    const val MIN_ROWS = 1

    /**
     * Biên GẦN (trái/trên) của ô thứ [i].
     *
     * ⚠ **CẮT (chia số nguyên), KHÔNG làm tròn** — phải khớp đúng cách [WorkspaceLayout] tính. [ĐO] 2026-09-11:
     * bản đầu tôi dùng làm-tròn và phép chứng minh tương đương **đỏ ngay** ở bố cục 2 cột, màn 1920 khe 1
     * (cũ 959 vs mới 960). Hình học thì giống nhau; chỉ quy tắc làm tròn khác — và lệch 1 pixel là đủ để bố cục
     * người dùng xê dịch khi đổi tầng vẽ.
     */
    fun edge(i: Int, count: Int, total: Int, gap: Int): Int {
        val inner = total - (count - 1) * gap
        return i * inner / count + i * gap
    }

    /**
     * Biên XA (phải/dưới) khi khung kết thúc **trước** ô thứ [i]: biên gần của ô đó, trừ đi khe.
     *
     * **Khung sát mép tự động chạm đúng [total]** — không cần nhánh đặc biệt, và đây là tính chất chứng minh được:
     * `edge(count) = count·inner/count + count·gap = inner + count·gap`, mà `inner = total − (count−1)·gap`, nên
     * `edge(count) − gap = total`. Chia số nguyên vẫn đúng vì `count·inner` chia hết cho `count`.
     *
     * [ĐO] 2026-09-11: bản đầu tôi viết thêm nhánh `if (i >= count) total`. Thử phá bỏ nhánh đó ⇒ **0 test đỏ**
     * ⇒ nhánh **dư thừa**. Đã bỏ và thay bằng test khoá chính tính chất (`bien xa cua o cuoi CHAM DUNG mep`) —
     * chứng minh tính chất tốt hơn là viết một ca đặc biệt để né nó.
     */
    fun farEdge(i: Int, count: Int, total: Int, gap: Int): Int = edge(i, count, total, gap) - gap

    /**
     * Bố cục sẵn có biểu diễn bằng lưới, hoặc `null` nếu **không biểu diễn đúng được**.
     *
     * [ĐO] "3 ô" trả `null`: cột trái của nó là 7.294/12 — không phải số nguyên (xem KDoc đầu file). Trả `null` thay
     * vì làm tròn là cố ý: làm tròn âm thầm sẽ đổi bố cục owner đã duyệt mà không ai biết.
     */
    fun fromPreset(preset: LayoutPreset): GridLayout? = when (preset) {
        LayoutPreset.ONE -> GridLayout(listOf(GridFrame(0, 0, COLS, ROWS)))
        LayoutPreset.TWO_COL -> GridLayout(
            listOf(GridFrame(0, 0, COLS / 2, ROWS), GridFrame(COLS / 2, 0, COLS / 2, ROWS)),
        )
        LayoutPreset.TWO_ROW -> GridLayout(
            listOf(GridFrame(0, 0, COLS, ROWS / 2), GridFrame(0, ROWS / 2, COLS, ROWS / 2)),
        )
        LayoutPreset.QUAD -> GridLayout(
            listOf(
                GridFrame(0, 0, COLS / 2, ROWS / 2),
                GridFrame(COLS / 2, 0, COLS / 2, ROWS / 2),
                GridFrame(0, ROWS / 2, COLS / 2, ROWS / 2),
                GridFrame(COLS / 2, ROWS / 2, COLS / 2, ROWS / 2),
            ),
        )
        LayoutPreset.THREE -> null
    }

    /** Bố cục sẵn có nào biểu diễn được bằng lưới — dùng cho tài liệu và test, không phải cho UI. */
    fun presetsRepresentable(): List<LayoutPreset> = LayoutPreset.values().filter { fromPreset(it) != null }

    /**
     * Chuỗi lưu bền một bố cục: `col,row,cols,rows` mỗi khung, ngăn bằng `;`.
     *
     * Cố ý dùng định dạng **tự đọc được** thay vì số gói lại: bố cục là thứ người dùng bỏ công vẽ, và khi cần cứu dữ
     * liệu bằng tay thì đọc được là quan trọng hơn tiết kiệm vài byte.
     */
    fun encode(layout: GridLayout): String =
        layout.frames.joinToString(";") { "${it.col},${it.row},${it.cols},${it.rows}" }

    /** Giải mã; token rác bị BỎ (không làm sập). Rỗng/null ⇒ bố cục rỗng. */
    fun decode(s: String?): GridLayout {
        if (s.isNullOrBlank()) return GridLayout(emptyList())
        val frames = s.split(";").mapNotNull { part ->
            val n = part.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (n.size == 4) GridFrame(n[0], n[1], n[2], n[3]) else null
        }
        return GridLayout(frames)
    }
}

/**
 * BỐ CỤC ĐANG HIỆU LỰC — **nguồn duy nhất** trả lời hai câu: *bao nhiêu ô* và *ô nằm ở đâu*.
 *
 * ## Vì sao phải có chỗ này
 * [ĐO] trước khi có nó, **sáu chỗ** tự suy ra số ô và khung pixel từ bố cục sẵn: bộ dựng ô, đo cỡ, đặt chỗ (3 chỗ ở
 * màn chính) và bộ sắp lại cửa sổ app (3 chỗ nữa). Thêm bố cục tự vẽ mà bỏ sót một chỗ thì màn hình vẽ theo bố cục
 * mới nhưng **cửa sổ app lại đặt theo bố cục cũ** — app nằm lệch khỏi ô, đúng loại lỗi của P-bug2. Có test canh
 * (`GridSeamGuardTest`) chặn việc suy ra ở chỗ khác.
 *
 * ## Luật LÙI AN TOÀN (quan trọng)
 * Bố cục tự vẽ chỉ thắng khi nó **dùng được thật**: hợp lệ VÀ số khung nằm trong trần ô hiện tại. Ngược lại **lùi về
 * bố cục sẵn** thay vì hiện ra thứ hỏng. Ba ca lùi:
 *  - dữ liệu hỏng / giải mã ra rỗng ⇒ người dùng vẫn có màn hình dùng được, không phải màn trắng;
 *  - bố cục đè nhau (lẽ ra trình vẽ đã chặn lúc lưu, nhưng đây là **lưới an toàn**);
 *  - **nhiều khung hơn trần ô** — ca này có thật khi bản sau nới trần rồi người dùng hạ cấp bản: bố cục 6 khung lưu
 *    bởi bản mới sẽ lùi về bố cục sẵn ở bản cũ chứ không làm mất ô.
 */
object EffectiveLayout {

    /** Bố cục tự vẽ có dùng được với trần ô [cap] không. */
    fun usable(custom: GridLayout?, cap: Int = WorkspaceState.SLOT_CAP): Boolean =
        custom != null && custom.frames.isNotEmpty() && custom.frames.size <= cap && custom.valid

    /** Số ô đang hiện. */
    fun slotCount(preset: LayoutPreset, custom: GridLayout?, cap: Int = WorkspaceState.SLOT_CAP): Int =
        if (usable(custom, cap)) custom!!.frames.size else preset.slotCount

    /**
     * Khung pixel của từng ô. Cùng thứ tự với ô nội dung ⇒ đổi bố cục thì app trong ô đi theo đúng thứ tự (giữ đúng
     * cách sắp lại app đã có).
     */
    fun rects(
        preset: LayoutPreset,
        custom: GridLayout?,
        width: Int,
        height: Int,
        gap: Int = 0,
        cap: Int = WorkspaceState.SLOT_CAP,
    ): List<SlotRect> =
        if (usable(custom, cap)) custom!!.slots(width, height, gap)
        else WorkspaceLayout.slots(preset, width, height, gap)

    /** Lý do bố cục tự vẽ bị bỏ qua — để nói cho người dùng, không im lặng. `null` = đang dùng nó. */
    fun ignoredReason(custom: GridLayout?, cap: Int = WorkspaceState.SLOT_CAP): String? = when {
        custom == null || custom.frames.isEmpty() -> null          // chưa vẽ gì: không phải "bị bỏ qua"
        custom.frames.size > cap -> "bố cục tự vẽ có ${custom.frames.size} khung, bản này đỡ tối đa $cap"
        !custom.valid -> "bố cục tự vẽ đang lỗi: " + custom.problems().first()
        else -> null
    }
}
