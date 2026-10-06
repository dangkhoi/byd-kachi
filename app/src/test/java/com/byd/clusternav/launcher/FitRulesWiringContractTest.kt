package com.byd.clusternav.launcher

import android.view.ViewGroup
import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * L5 WIDGET-FIT-ALL — soát vòng 1 (2.87): chỗ NỐI giữa tầng vẽ (`FitScale` · `FitProbe` · `FitGridLayout`) và các
 * quyết định thuần [FitRules] (`:core`, `FitRulesTest` khoá hành vi bằng mô hình `LinearLayout` r47).
 *
 * Dự án không dùng Robolectric nên cây view thật không dựng được trong JVM; bài này khoá rằng tầng vẽ ĐI QUA đúng
 * các quyết định đã test, và rằng các lỗi soát vòng 1 không quay lại:
 *  - [P1] dạng NGANG: `LayoutParams` + `weight` do [FitRules.lp] quyết (con MATCH chia hàng, không nuốt hàng); dạng
 *    không vẽ trọn được không thành ứng viên với hộp giả `w₀`;
 *  - [P2] chữ đổi tại chỗ báo cho lưới ([FitGridLayout.contentChanged]) từ CẢ hai đường đổ của `refreshRead`;
 *  - [P2] chữ tự do một dòng có ngân sách; con cỡ cố định tràn khung bị bắt;
 *  - [P3] kiểm cắt chữ dùng `getLineMax` (không khoảng trắng cuối); icon chọn biến thể/tint theo cỡ ĐÃ KHỚP.
 */
class FitRulesWiringContractTest {

    private fun code(file: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$file")

    private val layout by lazy { code("FitGridLayout.kt") }
    private val scale by lazy { code("FitScale.kt") }
    private val probe by lazy { code("FitProbe.kt") }
    private val widgets by lazy { code("WidgetViews.kt") }
    private val icons by lazy { code("KachiIcons.kt") }

    @Test
    fun `hang so LayoutParams cua core trung Android`() {
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, FitRules.MATCH)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, FitRules.WRAP)
    }

    @Test
    fun `P1 - bo ap dat LayoutParams va weight theo FitRules lp`() {
        val params = SourceRoots.body(scale, "private fun params(")
        assertTrue(params.contains("FitRules.lp(FitRules.Lp(b.lpW, b.lpH, b.weight), k, rot)"), "LP đích từ :core")
        // ĐỔI GHIM (soát vòng 2, P3): bản vòng 1 ghim `lp.weight = t.weight` — ghi weight = số lúc chụp ở MỌI lượt áp,
        // kéo thanh tiến trình nhạc (weight đổi mỗi nhịp) về vị trí lúc dựng. Nay weight đặt (và trả về gốc khi về dạng
        // DỌC) qua [FitRules.weight]: chỉ khi bộ áp sở hữu nó (phép lật) — `FitRulesRound2Test` khoá hành vi.
        assertTrue(params.contains("FitRules.weight(b.weight, t.weight, lp.weight, b.heldWeight)"), "weight do :core quyết")
        assertTrue(params.contains("b.heldWeight = t.weight.takeIf { it != b.weight }"), "nhớ weight mình đang ghi đè")
        assertFalse(params.contains("lp.weight = t.weight"), "ghi weight vô điều kiện = reset thanh tiến trình nhạc")
        // Bản lỗi: chỉ đổi trục con có weight, giữ MATCH_PARENT ⇒ con sau rộng 0.
        assertFalse(params.contains("if (rot && b.weight > 0f)"), "phép đổi trục tự viết ở :app đã thay bằng FitRules.lp")
    }

    @Test
    fun `P1 - dang khong ve tron duoc khong lam ung vien`() {
        val shape = SourceRoots.body(probe, "private fun shape(")
        assertTrue(shape.contains("val usable = grow < GROW_STEPS || ok(hi)"), "đo dò phải báo dạng có vẽ trọn được không")
        assertTrue(shape.contains("to usable"))
        val combine = SourceRoots.body(layout, "private fun combine(")
        assertTrue(combine.contains("FitRules.usable(cand)") && combine.contains("it.usable[i]"), "lọc dạng qua :core")
    }

    @Test
    fun `P2 - phep kiem cat chu bat con tran khung va cho chu tu do ngan sach`() {
        val clipped = SourceRoots.body(probe, "fun clipped(fs: FitScale)")
        assertTrue(clipped.contains("fs.groups()") && clipped.contains("spills(g)"), "con cỡ cố định tràn khung cha")
        assertTrue(clipped.contains("fs.freeLine(tv)"), "chữ tự do một dòng có ngân sách")
        val spills = SourceRoots.body(probe, "private fun spills(g: ViewGroup)")
        assertTrue(spills.contains("FitRules.spills("), "phép tràn từ :core")
        val text = SourceRoots.body(probe, "private fun clippedText(")
        // ĐỔI GHIM (soát vòng 2, P3): ngân sách đi qua `FitRules.cut` (gọi `freeTextCut` trong :core) — chỉ chữ tự do
        // được KHAI mới hưởng ([FitRules.freeText]).
        assertTrue(text.contains("FitRules.cut(dots, free, availW.toFloat(), tv.textSize)"), "ngân sách chữ tự do từ :core")
        // P3: getLineWidth tính cả khoảng trắng cuối dòng (Layout.java:1387-1401 r47) ⇒ báo cắt oan.
        assertTrue(text.contains("getLineMax("))
        assertFalse(text.contains("getLineWidth("))
        assertTrue(SourceRoots.body(scale, "fun freeLine(").contains(".free"))
    }

    @Test
    fun `P2 - do tai cho bao luoi khop, chi xin do khi den luot`() {
        val refresh = SourceRoots.body(widgets, "fun refreshRead(")
        val calls = Regex("FitGridLayout\\.contentChanged\\(v\\)").findAll(refresh).count()
        assertEquals(2, calls, "cả ô ĐỌC (refresh) lẫn ô HÀNH ĐỘNG (refreshAction) đều đổ chữ tại chỗ")
        assertTrue(refresh.contains("if (WidgetRefreshers.refreshAction(v, data.car)) FitGridLayout.contentChanged(v)"))
        // ĐỔI GHIM (soát vòng 2, P1): nhịp đo dò lại (stale/grow/stuck/probedAt) dời vào [FitRules.Cell] ở `:core` để
        // test thuần cả TRÌNH TỰ đổ tại chỗ → lượt đo → lượt khớp (`FitRulesRound2Test`); [FitRules.reprobe] nay được gọi
        // TRONG `Cell.check`. Bản vòng 1 ghim `if (due(child)) requestLayout()` + `FitRules.reprobe(` ở `due` + `it.grow`
        // /`it.stuck =` ở `refit` — đúng chỗ đã chốt "lượt thưa" khi bố cục chữ WRAP còn `null`.
        val hook = SourceRoots.body(layout, "private fun onContentChanged(")
        assertTrue(hook.contains("FitRules.Verdict.DUE -> requestLayout()"), "chỉ xin MỘT lượt đo khi đến lượt đo dò lại")
        val due = SourceRoots.body(layout, "private fun due(v: View, measured: Boolean)")
        assertTrue(due.contains("FitProbe.signature(fs) == need.sig") && due.contains("it.cell.check("))
        assertFalse(due.contains("requestLayout") || due.contains("measure("), "due chỉ ĐỌC, không đo")
        val refit = SourceRoots.body(layout, "private fun refit(w: Int, h: Int)")
        assertTrue(refit.contains("val raw = FitProbe.need(v, fs, floors)") && refit.contains("settled(it.need, raw, it.cell.grow)"),
            "nhận số đo mới qua settle, theo cờ của Cell")
        assertTrue(refit.contains("it.cell.fitted("), "lượt không chữa được vết cắt ⇒ lượt sau thưa (stuck trong Cell)")
        assertTrue(SourceRoots.body(layout, "private fun settled(").contains("FitRules.settle("))
    }

    @Test
    fun `P3 - icon chon bien the va tint theo co da khop`() {
        val params = SourceRoots.body(scale, "private fun params(")
        assertTrue(params.contains("KachiIcons.refit(b.v, minOf(t.width, t.height))"), "đổi cỡ icon ⇒ chọn lại biến thể")
        assertTrue(SourceRoots.body(icons, "fun byLevel(").contains("sized(img, sizeDp)"))
        assertTrue(SourceRoots.body(icons, "fun tint(").contains("sized(img, sizeDp)"))
        val refit = SourceRoots.body(icons, "fun refit(")
        assertTrue(refit.contains("byLevel(img, it, d.level, dp)") && refit.contains("tint(img, dp, d.selected, d.ink)"))
        // Bảng yếu không được giữ view trong giá trị (bài học WidgetRefreshers: giá trị trỏ về khoá ⇒ không dọn được).
        val drawn = icons.substringAfter("private class Drawn").substringBefore("private val drawnBy")
        assertFalse(drawn.contains("View") || drawn.contains("ImageView"))
    }

    @Test
    fun `ham moi co cho goi that va tep duoi 500 dong`() {
        val app = SourceRoots.moduleSourceRoots().filter { it.toString().contains("app") }
        // Bỏ chú thích bằng ĐÚNG bộ quét của `SourceRoots.codeOf`: tên hàm nhắc trong KDoc không được tính là chỗ gọi.
        fun strip(t: String) = KotlinSource.stripComments(t)
        fun uses(token: String, except: String): Boolean = app.any { root ->
            java.nio.file.Files.walk(root).use { s ->
                s.filter { it.toString().endsWith(".kt") && it.fileName.toString() != except }
                    .anyMatch { strip(it.toFile().readText()).contains(token) }
            }
        }
        // ĐỔI GHIM (soát vòng 2): `FitRules.reprobe(` và `FitRules.freeTextCut(` nay được gọi trong `:core`
        // (`Cell.check`, `cut`) — :app đi qua `FitRules.Cell(` / `FitRules.cut(`; `FitRulesRound2WiringContractTest`
        // khoá hai chỗ gọi trong :core.
        listOf("FitRules.lp(", "FitRules.spills(", "FitRules.cut(", "FitRules.usable(", "FitRules.Cell(",
            "FitRules.settle(", "FitRules.weight(", "FitRules.freeText(", "FitRules.splitsNumber(", "FitRules.sigStep(",
            "FitRules.known(",
        ).forEach { assertTrue(uses(it, "FitRules.kt"), "$it chưa có chỗ gọi ở :app") }
        assertTrue(uses("FitGridLayout.contentChanged(", "FitGridLayout.kt"))
        assertTrue(uses("KachiIcons.refit(", "KachiIcons.kt"))
        listOf(
            "src/main/kotlin/com/byd/clusternav/launcher/FitRules.kt",
            "src/main/java/com/byd/clusternav/launcher/KachiIcons.kt",
        ).forEach { rel ->
            val n = SourceRoots.text(rel).lines().size
            assertTrue(n <= 500, "$rel dài $n dòng — trần là 500 (CLAUDE.md §4.1)")
        }
    }
}
