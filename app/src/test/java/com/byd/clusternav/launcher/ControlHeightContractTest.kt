package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ BÀI CANH CHIỀU CAO ĐIỀU KHIỂN (IA v2 · R7) ═════════════════════════════════════════════════════════════
 *
 * Một luật, hai vế:
 *  • **Nút** (button phụ · pill bảng vẽ · "Xong" của bảng Cài đặt · nút −/+ · nút hành động của hàng danh sách)
 *    = [KachiSpace.TOUCH] (**48**).
 *  • **Chip** = [KachiSpace.ICON_XL] (**44**) — số **OWNER ĐÃ CHỐT** bằng ảnh (design system §10 OQ3), KHÔNG
 *    được "dọn cho đồng bộ" lên 48.
 *
 * ## ⚠⚠ Vì sao bài này tồn tại — bốn chiều cao cho ba vai
 * [ĐO] soát ảnh Settings 2026-09-12 trên **cùng một sản phẩm**: **35dp** (Xong/FAB/Apps/Settings) · **43dp**
 * (pill bảng vẽ bố cục) · **44dp** (chip) · **48dp** (nút của `SettingsRows`). Ba trong bốn con số đó **không ai
 * từng chọn** — chúng là *hệ quả* của đệm dọc cộng một dòng chữ, ở ba tệp khác nhau tự dựng nút bằng
 * `GradientDrawable` riêng. Đó là lý do luật phải được khoá bằng MÁY: không có bài canh thì bề mặt thứ tư mọc ra
 * lại lệch tiếp, và không ai thấy cho tới lượt soát ảnh sau.
 *
 * ## Giới hạn đã biết — nói ra, không giả vờ phủ hết
 * Bài này quét **mã nguồn**, nên nó khoá *lời khai* `minHeight = …` chứ không đo chiều cao THẬT lúc dựng view
 * (chiều cao thật còn phụ thuộc đệm, cỡ chữ, drawable nền). Cỡ thật vẫn phải kiểm bằng ảnh máy ảo — cùng cảnh
 * báo đã ghi ở `SpacingScaleContractTest.dich cham duoi muc toi thieu phai co ly do tai cho`. Cái bài này chặn
 * được là **sự trôi dạt**: một nút mới ra đời với 43dp, hoặc một nút cũ bị hạ xuống.
 */
class ControlHeightContractTest {

    /**
     * Bốn bề mặt dựng nút. Thêm bề mặt nào dựng nút thì thêm tệp đó vào đây.
     *
     * ⚠ [AppDrawer] vào danh sách từ 2026-09-13: [ĐO] soát ảnh Settings v2 đo nút **quyết định** của ngăn kéo
     * (*Áp dụng (N)* / *Đặt N widget*) cao **34.7dp** — thấp hơn cả nút PHỤ của Settings. Bài canh cũ chỉ nhìn ba
     * tệp trong `SettingsRows`-land nên bề mặt thứ tư trôi mà không ai đỏ, đúng điều KDoc lớp này cảnh báo.
     */
    private val surfaces = listOf("SettingsRows.kt", "SettingsPanel.kt", "LayoutEditorPanel.kt", "AppDrawer.kt")

    private fun code(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")

    /**
     * Dòng mã đã **bỏ chú thích nhưng GIỮ số dòng** — bắt buộc: KDoc của chính các hàm này nhắc tên `minHeight`
     * và cả hai hằng, nên quét thô sẽ báo sai hàng loạt rồi bị tắt đi (thà không có).
     */
    private fun codeLines(name: String): List<Pair<Int, String>> =
        SourceRoots.text("src/main/java/com/byd/clusternav/launcher/$name")
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)) { m ->
                m.value.replace(Regex("[^\n]"), " ")
            }
            .lines().mapIndexed { i, l -> (i + 1) to l.substringBefore("//") }

    /** Cả `minHeight` (thuộc tính Kotlin) lẫn `minimumHeight` (API View) — hai chính tả của cùng một việc. */
    private val declaresMinHeight = Regex("""\bmin(?:imum)?Height\s*=\s*(.+)""")

    // ── Vế 1 · mọi lời khai chiều cao là TOUCH, trừ chip ──────────────────────────────────────────

    @Test
    fun `moi chieu cao dieu khien khai Sp TOUCH - rieng chip giu Sp ICON_XL`() {
        val chipBody = SourceRoots.body(code("SettingsRows.kt"), "private fun addChip(")
        val offenders = mutableListOf<String>()
        var sites = 0
        surfaces.forEach { f ->
            codeLines(f).forEach { (no, line) ->
                val rhs = declaresMinHeight.find(line)?.groupValues?.get(1)?.trim() ?: return@forEach
                sites++
                // Chip là ngoại lệ DUY NHẤT và nó phải nằm đúng trong `addChip` — không ai được khai 44 chỗ khác.
                val isChip = chipBody.contains(line.trim())
                val want = if (isChip) "Sp.ICON_XL" else "Sp.TOUCH"
                if (!rhs.contains(want)) {
                    offenders += "$f:$no  minHeight = $rhs  (vai ${if (isChip) "CHIP" else "NÚT"} ⇒ phải là $want)"
                }
            }
        }
        assertTrue(sites >= 5, "phải thấy ít nhất 5 chỗ khai chiều cao điều khiển, thấy $sites")
        assertEquals(
            emptyList<String>(), offenders,
            "R7: nút = Sp.TOUCH (48) · chip = Sp.ICON_XL (44, owner chốt bằng ảnh). Số trần hay một hằng khác " +
                "là quay lại 'bốn chiều cao cho ba vai':\n" + offenders.joinToString("\n"),
        )
    }

    // ── Vế 2 · neo DƯƠNG: xoá lời khai đi cũng phải đỏ ────────────────────────────────────────────

    /**
     * Vế 1 một mình **không đủ**: xoá sạch mọi dòng `minHeight` thì nó xanh (không còn gì để soi) trong khi bệnh
     * lại đúng là *"nút không khai đích chạm"*. Bài này neo từng bề mặt một, kèm `gravity` — hai thứ phải đi cùng
     * nhau vì `minHeight` chỉ **nới ô** chứ không **căn chữ**: có minHeight mà không có gravity thì nút cao 48dp
     * với chữ dính mép trên, mắt đọc ra "lệch" dù số đo đã đạt.
     */
    @Test
    fun `ba be mat deu neo dich cham vao Sp TOUCH kem can giua`() {
        listOf(
            // tệp · mốc hàm · lời khai phải thấy (chính tả `dpi(context, …)` hay `dp(…)` tuỳ helper của tệp)
            Triple("SettingsRows.kt", "private fun paintButton(", "minHeight = dpi(context, Sp.TOUCH)"),
            Triple("SettingsPanel.kt", "private fun head()", "minHeight = dpi(context, Sp.TOUCH)"),
            Triple("LayoutEditorPanel.kt", "private fun pill(", "minHeight = dp(Sp.TOUCH)"),
            Triple("AppDrawer.kt", "private fun placeBar()", "minHeight = dpi(context, Sp.TOUCH)"),
        ).forEach { (file, marker, decl) ->
            val body = SourceRoots.body(code(file), marker)
            assertTrue(body.contains(decl), "$file · `$marker` phải khai `$decl` (R7: nút = 48dp)")
            assertTrue(
                body.contains("Gravity.CENTER"),
                "$file · `$marker` khai minHeight thì phải căn giữa (`Gravity.CENTER`) — minHeight nới ô chứ " +
                    "không căn chữ, thiếu nó thì chữ dính mép trên",
            )
        }
    }

    /**
     * Mọi nút của [SettingsRows] đi qua **một** hàm sơn ([SettingsRows.paintButton]) — không tệp nào được dựng
     * nút thứ hai bằng `GradientDrawable` riêng (design system R5). Đây là chỗ luật "48" thật sự sống: sửa một
     * dòng là ba nút cùng đổi.
     */
    @Test
    fun `moi nut cua SettingsRows di qua mot ham son duy nhat`() {
        val src = code("SettingsRows.kt")
        listOf("fun button(", "fun listRow(", "private fun squareButton(").forEach { marker ->
            assertTrue(
                SourceRoots.body(src, marker).contains("paintButton("),
                "`$marker…` phải lấy hình dạng nút từ paintButton() — dựng nền riêng là mở lại cửa lệch chiều cao",
            )
        }
    }

    // ── Vế 3 · quyết định của OWNER ───────────────────────────────────────────────────────────────

    /**
     * Chip **giữ 44**, và bài này ghim lý do tại chỗ.
     *
     * [ĐO] design system §10 [P2] đề xuất nâng chip 44 → 48 cho "đồng bộ đích chạm"; owner chốt 2026-09-12:
     * **GIỮ 44** vì đó là số đã verify bằng ảnh. Không có bài canh thì lần dọn dẹp sau sẽ "sửa" nó một cách thiện
     * chí và không ai nhớ vì sao nó từng là 44 — đúng họ lỗi mà `EXEMPT_LINES` của [TypeScaleContractTest] chặn.
     */
    @Test
    fun `chip giu chieu cao 44 - quyet dinh cua owner`() {
        val chipBody = SourceRoots.body(code("SettingsRows.kt"), "private fun addChip(")
        assertTrue(
            chipBody.contains("minHeight = dpi(context, Sp.ICON_XL)"),
            "chip phải giữ Sp.ICON_XL (44) — owner đã chốt bằng ảnh, không nâng lên Sp.TOUCH",
        )
        // Bề rộng tối thiểu của chip suy từ CHIỀU CAO (1.5×) chứ không mượn đích chạm — xem KDoc CHIP_MIN_W.
        assertTrue(
            chipBody.contains("minWidth = dpi(context, Sp.CHIP_MIN_W)"),
            "chip phải lấy bề rộng tối thiểu từ Sp.CHIP_MIN_W (66 = 1.5 × 44) — Sp.TOUCH (48) cho tỉ lệ 1.09 " +
                "và chip 1–2 ký tự đọc ra HÌNH TRÒN",
        )
        val space = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/KachiSpace.kt")
        assertTrue(
            Regex("""const val CHIP_MIN_W = 66\b""").containsMatchIn(space),
            "KachiSpace phải khai `const val CHIP_MIN_W = 66` (1.5 × chiều cao chip 44)",
        )
    }
}
