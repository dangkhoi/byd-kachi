package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.74 · UX7 — MỌI widget tổng hợp đặt chữ theo CÙNG MỘT LUẬT ══════════════════════════════════════════════
 *
 * Owner 2026-09-27: *"ngoài widget lốp đã sửa, có sửa cùng các widget tổng hợp khác không? cho nó giống nhau, hết
 * lỗi chứ?"*. UX2 đã sửa ba lỗi hình học **chỉ trong bảng lốp**; bài này khoá luật đó cho **cả năm** ô vẽ Canvas +
 * bốn chỗ dựng bằng `TextView`, để lần sau ai thêm một bảng nữa thì bảng đó không thể quay lại hằng ma thuật.
 *
 * Số học nằm ở `:core` (`CellTextLayoutTest` — 20 ca, chạy off-car với mọi phông/mọi cỡ ô); bài này chỉ canh **DÂY
 * NỐI**: ô vẽ phải GỌI hình học chung, và không được có bản canh thứ hai ở `:app`. Lý do phải tách hai tầng: unit
 * test `:app` không có Robolectric (android.jar stub ném) nên không dựng được `Canvas`/`Paint` thật để đo pixel.
 */
class CompositeWidgetLayoutContractTest {

    private fun code(rel: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$rel")

    private val tyre by lazy { code("TyreBoardView.kt") }
    private val ring by lazy { code("RingView.kt") }
    private val photo by lazy { code("PhotoWidgetView.kt") }
    private val grid by lazy { code("GridEditorView.kt") }
    private val door by lazy { code("DoorBoardView.kt") }
    private val axis by lazy { code("AxisRow.kt") }
    private val readTile by lazy { code("ReadTile.kt") }
    private val telemetry by lazy { code("WidgetTelemetry.kt") }
    private val widgets by lazy { code("WidgetViews.kt") }
    private val groups by lazy { code("GroupTileViews.kt") }

    /** Mọi tệp `:app/launcher` (kể cả tệp chưa tồn tại lúc viết bài này) — để luật áp cho ô vẽ SAU này nữa. */
    private fun launcherFiles(): List<Pair<String, String>> {
        val dir = SourceRoots.path("src/main/java/com/byd/clusternav/launcher").toFile()
        return dir.listFiles()!!.filter { it.isFile && it.name.endsWith(".kt") }
            .sortedBy { it.name }
            .map { it.name to SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/${it.name}") }
    }

    // ══ 1 · baseline: KHÔNG hằng ma thuật ở bất kỳ `drawText` nào ═════════════════════════════════════════════

    /**
     * Bệnh gốc của cả ba lỗi UX2/UX7: baseline viết bằng `cy + textSize * 0.36f`. Bài này quét **mọi** `drawText`
     * của launcher và đòi tham số **y** không chứa một hằng số thực nào — tức nó phải đến từ một biến đã tính qua
     * [CellTextLayout] (hoặc từ một phép cộng các biến đó).
     *
     * Quét theo tham số THỨ BA (`drawText(text, x, y, paint)`) chứ không theo cả dòng: tham số **x** được phép có
     * hằng (`w / 2f` là tâm ô, không phải phỏng đoán về phông).
     *
     * ⚠ Quét **một tầng biến**: `drawText(..., base, ...)` với `val base = cy + textSize * 0.36f` phải đỏ. Bỏ tầng
     * này là bài canh mục ruỗng — [ĐO] đúng lỗ đó khi thử đột biến `RingView` về công thức 2.73: nó chỉ dời hằng
     * lên một dòng `val` là phép quét theo lời gọi không thấy gì nữa.
     */
    @Test
    fun `khong con baseline bang hang so - moi drawText cua launcher`() {
        val offenders = mutableListOf<String>()
        var scanned = 0
        val literal = Regex("""\d*\.\d+f?""")
        val ident = Regex("""[A-Za-z_]\w*""")
        for ((name, src) in launcherFiles()) {
            var at = src.indexOf("drawText(")
            while (at >= 0) {
                val args = splitArgs(src, src.indexOf('(', at))
                scanned++
                val y = args.getOrNull(2)?.trim() ?: ""
                val exprs = listOf(y) + ident.findAll(y).map { it.value }.toSet().mapNotNull { rhsOf(src, it) }
                exprs.filter { literal.containsMatchIn(it) }
                    .forEach { offenders += "$name: baseline của drawText tính bằng hằng — `${it.trim()}`" }
                at = src.indexOf("drawText(", at + 1)
            }
        }
        assertTrue(scanned >= 8, "chỉ đọc được $scanned lời gọi drawText — phép quét đã hụt, không phải mã sạch")
        assertEquals(
            emptyList<String>(), offenders,
            "baseline tính bằng hằng số ⇒ khối chữ lệch tâm với phông khác (đúng lỗi owner báo trên bảng lốp): $offenders",
        )
    }

    /** Năm ô vẽ Canvas đều lấy baseline từ hình học CHUNG — không tệp nào có phép canh riêng. */
    @Test
    fun `nam o ve Canvas dung CHUNG mot hinh hoc`() {
        val two = mapOf("TyreBoardView" to tyre, "RingView" to ring, "PhotoWidgetView" to photo)
        two.forEach { (name, src) ->
            assertTrue(
                src.contains("CellTextLayout.twoLineTopBaseline("),
                "$name: khối 2 dòng phải canh giữa bằng số đo phông ([CellTextLayout])",
            )
            assertTrue(
                src.contains("CellTextLayout.SUB_LINE_GAP"),
                "$name: nhịp 2 dòng phải là nhịp CHUNG, không phải hằng riêng của bảng",
            )
        }
        assertTrue(
            grid.contains("CellTextLayout.centeredBaseline("),
            "GridEditorView: số thứ tự khung (một dòng) cũng canh bằng số đo phông",
        )
        // Bảng cửa KHÔNG vẽ chữ (chỉ chấm) ⇒ không được có Paint chữ nào sống ở đó (đường chết của 2.73).
        assertFalse(door.contains("drawText("), "bảng cửa không vẽ chữ")
        assertFalse(door.contains("footerP"), "Paint chân bảng là ĐƯỜNG CHẾT — đã gỡ ở UX7, đừng dựng lại")
        assertFalse(door.contains("FOOTER_"), "hằng chân bảng cũng là di sản dòng kết luận đã gỡ")
    }

    /**
     * Mực của dòng SỐ đo trên **chữ mẫu cố định**, không trên chuỗi đang vẽ: bảng có nhiều ô (4 bánh) hoặc có giá
     * trị đổi theo nhịp (`"82%"` → `"—"`) mà đo theo chuỗi thì baseline **nhảy** giữa các ô / giữa các nhịp.
     */
    @Test
    fun `muc dong so do tren chu MAU, khong tren chuoi dang ve`() {
        listOf("TyreBoardView" to tyre, "RingView" to ring).forEach { (name, src) ->
            assertTrue(src.contains("""const val INK_REF = "0""""), "$name: phải khai chữ mẫu đo mực")
            assertTrue(src.contains("getTextBounds(INK_REF"), "$name: đo mực trên chữ mẫu, không trên giá trị")
        }
    }

    /** Chữ phải CO cho vừa ô — `drawText` không tự kẹp bề rộng, nên ô nào vẽ chữ tự do đều phải đi qua `fitScale`. */
    @Test
    fun `chu co cho vua o - khong de tran ra ngoai o`() {
        listOf("TyreBoardView" to tyre, "RingView" to ring, "PhotoWidgetView" to photo).forEach { (name, src) ->
            assertTrue(src.contains("CellTextLayout.fitScale("), "$name: chữ dài phải co cho vừa ô")
        }
        // Sàn cỡ chữ lấy từ thang bảng (cùng sàn DoorBoardView từng dùng) — không phát minh sàn riêng.
        listOf("RingView" to ring, "PhotoWidgetView" to photo).forEach { (name, src) ->
            assertTrue(
                src.contains("Sp.BOARD_VALUE_MIN") || src.contains("Sp.BOARD_LABEL_MIN"),
                "$name: sàn cỡ chữ phải lấy từ KachiSpace, không phải một số dp gõ tay",
            )
        }
    }

    // ══ 2 · một TRỤC: con số ở trục ô (ô dựng bằng TextView) ══════════════════════════════════════════════════

    /**
     * Bốn chỗ dựng `số + đơn vị` bằng `TextView` phải đi qua [AxisRow]. `LinearLayout(HORIZONTAL) + gravity =
     * CENTER` canh giữa CẢ CỤM ⇒ con số lệch trục so với nhãn/chú thích của chính ô đó.
     */
    @Test
    fun `bon cho dung so + don vi deu qua AxisRow, khong con hang canh giua ca cum`() {
        val sites = listOf(
            Triple("WidgetTelemetry.valueShape", telemetry, "private fun valueShape("),
            Triple("WidgetViews.speed", widgets, "private fun speed("),
            Triple("GroupTileViews.leadRow", groups, "private fun leadRow("),
            Triple("ReadTile.readTileOf", readTile, "internal fun readTileOf("),
        )
        sites.forEach { (name, src, signature) ->
            val body = SourceRoots.body(src, signature)
            assertTrue(body.contains("AxisRow("), "$name: hàng `số + đơn vị` phải là AxisRow")
            assertFalse(
                body.contains("HORIZONTAL"),
                "$name: còn một hàng ngang tự canh giữa ⇒ con số lại lệch trục",
            )
        }
    }

    /** [AxisRow] KHÔNG được tự tính trục — nó chỉ là vỏ `ViewGroup` quanh [CellTextLayout.lineStartX]. */
    @Test
    fun `AxisRow chi la vo quanh lineStartX, khong co phep canh thu hai`() {
        val layout = SourceRoots.body(axis, "override fun onLayout(")
        assertTrue(layout.contains("CellTextLayout.lineStartX("), "trục phải do :core tính")
        assertFalse(
            Regex("""lineWidth\s*/\s*2|\(\s*anchor\.measuredWidth\s*\+[^)]*\)\s*/\s*2""").containsMatchIn(layout),
            "canh giữa cả cụm ở đây là tái phát đúng lỗi (2)",
        )
        val measure = SourceRoots.body(axis, "override fun onMeasure(")
        // Đo ĐƠN VỊ trước rồi mới cho số phần còn lại (thà cắt số còn hơn mất đơn vị — luật của TyreBoardView).
        assertTrue(
            measure.indexOf("trail.measure(") in 1 until measure.indexOf("anchor.measure("),
            "phải đo đơn vị TRƯỚC số, không thì số dài sẽ đẩy đơn vị ra ngoài ô",
        )
    }

    // ══ 3 · thẻ đục không đè lên ảnh xe + hai bảng dùng ảnh xe cùng khung ═════════════════════════════════════

    /**
     * Bảng nào vẽ ảnh xe **và** vẽ thẻ/khối đục lên đó thì mép thẻ phải kẹp theo khung ảnh THẬT
     * ([CellTextLayout.cardSpanX]). Viết theo tính chất (không theo tên bảng) để bảng SAU cũng bị canh.
     */
    @Test
    fun `bang nao ve the len anh xe thi phai kep theo khung anh`() {
        var boards = 0
        for ((name, src) in launcherFiles()) {
            // "Bảng vẽ ảnh xe" = tệp **dùng** lớp ảnh (`CarImageLayer(context…`) hoặc đọc khung letterbox của nó.
            // KHÔNG tính chính `CarImageLayer.kt`: khối bo góc nó vẽ LÀ chỗ giữ ảnh (placeholder), không phải thẻ
            // đè lên ảnh.
            val drawsCar = src.contains("CarImageLayer(context") || src.contains("carView.contentRect(")
            if (!drawsCar) continue
            boards++
            if (src.contains("drawRoundRect(") || src.contains("drawRect(")) {
                assertTrue(
                    src.contains("CellTextLayout.cardSpanX("),
                    "$name: vẽ khối đục lên ảnh xe mà không kẹp theo khung ảnh ⇒ cắt mất gương/mép cửa",
                )
            }
        }
        assertTrue(boards >= 3, "chỉ thấy $boards ô vẽ ảnh xe — phép quét đã hụt (phải có lốp · cửa · xe mini)")
    }

    /** Hai bảng vẽ ảnh xe top-down dùng **cùng** mép dưới khung ảnh — không bảng nào còn chừa dải trống chết. */
    @Test
    fun `bang lop va bang cua dung cung mep duoi khung anh`() {
        val re = Regex("""const val CAR_BOTTOM = ([0-9.]+)f""")
        val t = re.find(tyre)?.groupValues?.get(1)
        val d = re.find(door)?.groupValues?.get(1)
        assertTrue(t != null && d != null, "cả hai bảng phải khai CAR_BOTTOM (lốp=$t cửa=$d)")
        assertEquals(t, d, "hai bảng dùng chung ảnh xe mà mép dưới khác nhau ⇒ hình xe hai cỡ")
    }

    // ══ helper ═══════════════════════════════════════════════════════════════════════════════════════════════

    /** Phần bên phải dấu `=` của khai báo `val <id>` ĐẦU TIÊN trong tệp (một tầng là đủ — xem KDoc bài quét). */
    private fun rhsOf(src: String, id: String): String? =
        Regex("""\bval $id\s*=\s*(.*)""").find(src)?.groupValues?.get(1)

    /** Cắt danh sách tham số của lời gọi mở ngoặc tại [open], tách theo dấu phẩy ở **mức ngoặc 0**. */
    private fun splitArgs(src: String, open: Int): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var start = open + 1
        var i = open
        var inStr = false
        while (i < src.length) {
            val ch = src[i]
            when {
                ch == '"' -> inStr = !inStr
                inStr -> Unit
                ch == '(' || ch == '[' -> depth++
                ch == ')' || ch == ']' -> {
                    depth--
                    if (depth == 0) { out += src.substring(start, i); return out }
                }
                ch == ',' && depth == 1 -> { out += src.substring(start, i); start = i + 1 }
            }
            i++
        }
        return out
    }
}
