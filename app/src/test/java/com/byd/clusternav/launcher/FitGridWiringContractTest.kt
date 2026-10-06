package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * L5 WIDGET-FIT-ALL (2.87) — bài canh TĨNH chỗ NỐI của phép khớp lưới widget.
 *
 * Hình học (cột/hàng, cỡ lớn nhất đọc được, khe đều, sàn chữ, đích chạm 48dp, ca ảnh 03/10) khoá bằng test thuần
 * `GridFitTest` ở `:core` với oracle độc lập; dự án không dùng Robolectric nên tầng view không dựng được trong JVM —
 * bài này khoá phần NỐI DÂY mà off-car chứng minh được, và phần đo thật để QA làm trên máy ảo (`logcat -s WidgetFit`,
 * `uiautomator dump`).
 *
 * Khoá cái gì:
 *  - ô widget (1 mục và 2..8 mục) đi qua [FitGridLayout], không còn hàng chia theo SỐ MỤC (bệnh ảnh 03/10);
 *  - khớp ở lượt ĐO, chỉ khi khoá (rộng, cao, số ô) đổi, không dựng lại view (không mất cú bấm), không `post`;
 *  - bộ áp chỉ ĐỔI view có sẵn, ghi khi giá trị thật sự đổi;
 *  - vá B5 (autosize + ẩn icon trong `MiniCard`) đã gỡ — hai cơ chế cùng chỉnh cỡ sẽ đánh nhau;
 *  - mọi hàm mới có chỗ gọi thật (CLAUDE.md §8) và mọi tệp ≤ 500 dòng.
 */
class FitGridWiringContractTest {

    private fun code(file: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$file")

    private val widgets by lazy { code("WidgetViews.kt") }
    private val layout by lazy { code("FitGridLayout.kt") }
    private val scale by lazy { code("FitScale.kt") }
    private val probe by lazy { code("FitProbe.kt") }

    @Test
    fun `o widget di qua FitGridLayout - khong con chia hang theo so muc`() {
        val grid = SourceRoots.body(widgets, "fun buildGrid(")
        // ĐỔI GHIM 2.93 wave 2A (SHORTCUT-SCROLL-REBUILD): build nhận thêm `scrollKey` — cùng đường co/giãn theo khung.
        assertTrue(grid.contains("FitGridLayout.single(ctx, build(ctx, list[0], data, scrollKey)"), "một mục cũng co/giãn theo khung")
        assertTrue(grid.contains("FitGridLayout.grid(ctx, IconRepeat.ofIds(list))"), "lưới 2..8 mục + luật chỉ-icon từ :core")
        assertTrue(grid.contains("addView(mini(ctx, id, data, scrollKey).also { it.tag = WidgetTag(id, compact = true) })"))  // ĐỔI GHIM 2.93 wave 2A: scrollKey
        listOf("LinearLayout(", "topN", "n / 2", "subList(", "setMargins(").forEach {
            assertFalse(grid.contains(it), "buildGrid còn '$it' ⇒ quay lại hàng chia theo số mục / ô cố định (ảnh 03/10)")
        }
    }

    @Test
    fun `khop o luot DO, chi khi khoa doi, khong dung lai view`() {
        val measure = SourceRoots.body(layout, "override fun onMeasure(")
        assertTrue(measure.contains("keyW != iw || keyH != ih || keyN != childCount"), "khớp lại CHỈ khi khung/số ô đổi")
        assertTrue(measure.contains("if (grew()) refit(iw, ih)"), "chữ dài ra mà bị cắt ⇒ khớp lại đúng ô đó")
        val refit = SourceRoots.body(layout, "private fun refit(w: Int, h: Int)")
        assertTrue(refit.contains("GridFit.fit(kids.size, w, h, shapes, spec)"), "phép khớp đi qua :core")
        assertTrue(refit.contains("FitProbe.need(v, fs, floors)"), "hộp tự nhiên ĐO thật, không gõ số")
        assertTrue(refit.contains("it.need == null"), "đo dò chỉ ô chưa có số đo (cache theo ô)")
        assertTrue(refit.contains("FitProbe.clipped(it)") && refit.contains("VERIFY_STEPS"), "kiểm lại bằng bố cục thật")
        val measurePath = listOf(
            "override fun onMeasure(", "private fun refit(", "private fun combine(", "private fun applyAll(",
            "private fun measureAll(", "private fun grew()",
        ).joinToString("\n") { SourceRoots.body(layout, it) }
        listOf("requestLayout(", "post {", "post(", "addView(", "removeView", "removeAllViews").forEach {
            assertFalse(measurePath.contains(it), "đường đo có '$it' ⇒ dựng lại/đẩy việc ra ngoài lượt đo (giật, mất cú bấm)")
        }
        assertFalse(layout.contains("onSizeChanged"), "khớp trong onSizeChanged = đổi view giữa lượt LAYOUT")
        // Lượt cache (nhịp 1 Hz) không đo dò lại theo từng nhịp. ĐỔI GHIM (soát vòng 1, P2): bản trước ghim chặn trần
        // `if (fit?.legible != true) return false` — chặn ấy làm lưới đã rơi xuống sàn (một tên bài dài) KHÔNG BAO GIỜ
        // phục hồi tới lần dựng lại. Nay mọi lượt đo dò lại đi qua nhịp của [FitRules.reprobe] (cắt + đọc được ⇒ nhịp
        // kế; còn lại ⇒ thưa 30 s) — `FitRulesTest` khoá nhịp, `FitRulesWiringContractTest` khoá chỗ nối.
        assertTrue(SourceRoots.body(layout, "private fun grew()").contains("due(v, measured = true) == FitRules.Verdict.DUE"))
        // ĐỔI GHIM (soát vòng 2, P1): `due` quyết qua [FitRules.Cell.check] (gọi [FitRules.reprobe] bên trong, `:core`)
        // thay vì gọi thẳng `FitRules.reprobe(` — để trạng thái cắt CHƯA BIẾT không bị chốt thành lượt thưa.
        assertTrue(SourceRoots.body(layout, "private fun due(v: View, measured: Boolean)").contains("it.cell.check("))
        // Đặt đúng vị trí :core trả (khe đều, hàng thiếu căn giữa).
        val place = SourceRoots.body(layout, "override fun onLayout(")
        assertTrue(place.contains("f.left(i)") && place.contains("f.top(i)"))
    }

    @Test
    fun `lich thuoc - khe, dich cham, san chu, tran deu qua thang`() {
        val spec = SourceRoots.body(layout, "private fun spec(kids: List<View>)")
        assertTrue(spec.contains("dpi(context, Sp.S)"), "khe lưới = Sp.S (8dp) — đúng khe 2.86, không số trần")
        assertTrue(spec.contains("dpi(context, Sp.TOUCH)"), "đích chạm 48dp khi lưới có ô bấm được")
        assertTrue(spec.contains("MAX_SCALE_SINGLE") && spec.contains("MAX_SCALE_GRID") && spec.contains("QUANTUM"))
        assertEquals(10, KachiBars.FIT_TEXT_MIN, "sàn chữ 10sp = nhãn nhỏ nhất đang ship trong một ô (SELECT ở DOCK)")
        assertEquals(2.0, FitGridLayout.MAX_SCALE_GRID)
        assertEquals(1.5, FitGridLayout.MAX_SCALE_SINGLE)
        assertTrue(probe.contains("Bars.FIT_TEXT_MIN") && probe.contains("Sp.ICON_XS") && probe.contains("Sp.TOUCH"))
    }

    @Test
    fun `bo ap chi DOI view co san, ghi khi doi that`() {
        listOf("addView(", "removeView", "removeAllViews", "inflate(").forEach {
            assertFalse(scale.contains(it), "FitScale có '$it' ⇒ dựng lại view thay vì đổi tại chỗ")
        }
        // ĐỔI GHIM (QA 04/10, làn H1): `apply` nhận thêm cỡ ô (cw, ch) để chặn icon theo ô ([FitRules.iconScale]) —
        // khoá no-op nay là bộ NĂM (k, dạng, số dòng, cỡ ô); đo dò gọi bản không cỡ ô (mặc định = không chặn).
        // ĐỔI GHIM lần nữa (QA3, 04/10): bộ khoá thêm trần icon CHUNG của lưới (`iconCapPx` — icon cùng lưới cùng cỡ,
        // FitRules.iconCap): đổi trần mà trả no-op thì icon giữ cỡ cũ.
        val apply = SourceRoots.body(scale, "fun apply(k: Double, f: Form, n: Int, cw: Int = Int.MAX_VALUE, ch: Int = Int.MAX_VALUE)")
        assertTrue(
            apply.contains("if (k == scale && f == form && n == lines && cw == cellW && ch == cellH && iconCapPx == appliedCap) return false"),
            "áp lại cùng bộ = no-op",
        )
        // `setTextSize` là no-op khi autosize bật (TextView.java:4271-4275 r47) ⇒ ô STEP "AUTO" phải co dải autosize.
        val text = SourceRoots.body(scale, "private fun text(")
        assertTrue(text.contains("setAutoSizeTextTypeUniformWithConfiguration(") && text.contains("TypedValue.COMPLEX_UNIT_PX"))
        assertTrue(text.contains("if (abs(tv.textSize - px) > 0.01f)"), "chỉ đặt cỡ chữ khi đổi thật")
        // Chỉ-icon: nhãn vào mô tả trợ năng (uiautomator/TalkBack vẫn đọc được).
        assertTrue(apply.contains("descHost.contentDescription = desc"))
        // Đo dò ép đo lại (bộ đệm đo View.java:24519-24552 làm TextView.getLayout() cũ).
        assertTrue(SourceRoots.body(probe, "private fun shape(").contains("fs.forceAll()"))
    }

    @Test
    fun `o tu ve theo khung khong bi co hai lan`() {
        val self = SourceRoots.body(layout, "fun selfFitting(v: View)")
        listOf("RingView", "TyreBoardView", "PhotoWidgetView", "CarMiniView", "DoorBoardView", "ShortcutIconsView", "GroupTileView")
            .forEach { assertTrue(self.contains("is $it"), "ô tự vẽ $it phải được để nguyên (FILL), không co bằng FitScale") }
        assertTrue(SourceRoots.body(layout, "fun single(ctx: Context, child: View)").contains("if (selfFitting(child)) child"),
            "ô đơn tự vẽ trả NGUYÊN view như 2.86")
    }

    @Test
    fun `va B5 cua MiniCard da go - mot co che chinh co duy nhat`() {
        val tele = code("WidgetTelemetry.kt")
        val card = tele.substringAfter("internal class MiniCard(").substringBefore("internal fun miniCard(")
        assertFalse(card.contains("setAutoSizeTextTypeUniformWithConfiguration"), "autosize WRAP chỉ co, không giãn")
        assertFalse(card.contains("addOnLayoutChangeListener"), "ẩn icon theo bố cục đánh nhau với dạng NGANG của lưới")
    }

    @Test
    fun `ham moi co cho goi that - CLAUDE md 8`() {
        val app = SourceRoots.moduleSourceRoots().filter { it.toString().contains("app") }
        val core = SourceRoots.moduleSourceRoots().filter { it.toString().contains("core") }
        fun uses(roots: List<java.nio.file.Path>, token: String, except: String): Boolean = roots.any { root ->
            java.nio.file.Files.walk(root).use { s ->
                s.filter { it.toString().endsWith(".kt") && it.fileName.toString() != except }
                    .anyMatch { it.toFile().readText().contains(token) }
            }
        }
        assertTrue(uses(app, "FitGridLayout.grid(", "FitGridLayout.kt"))
        assertTrue(uses(app, "FitGridLayout.single(", "FitGridLayout.kt"))
        assertTrue(uses(app, "FitProbe.need(", "FitProbe.kt"))
        assertTrue(uses(app, "FitScale(", "FitScale.kt"))
        assertTrue(uses(app, "GridFit.fit(", "GridFit.kt"))
        assertTrue(uses(app, "GridFit.capacity(", "GridFit.kt"), "sức chứa có chỗ dùng thật (nhật ký QA)")
        assertTrue(uses(app, "IconRepeat.ofIds(", "IconRepeat.kt"))
        assertTrue(uses(core, "IconRepeat.distinguishable(", "IconRepeat.kt"), "ô nhóm dùng CÙNG luật icon")
        assertTrue(uses(core, "GridFit.fit(", "GridFit.kt"), "lưới lối tắt (ShortcutGridFit) là một cấu hình của GridFit")
    }

    @Test
    fun `moi tep cua L5 duoi tran 500 dong`() {
        listOf(
            "src/main/java/com/byd/clusternav/launcher/FitGridLayout.kt",
            "src/main/java/com/byd/clusternav/launcher/FitScale.kt",
            "src/main/java/com/byd/clusternav/launcher/FitProbe.kt",
            "src/main/java/com/byd/clusternav/launcher/WidgetViews.kt",
            "src/main/java/com/byd/clusternav/launcher/WidgetTelemetry.kt",
            "src/main/java/com/byd/clusternav/launcher/KachiSpaceBars.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/GridFit.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/ShortcutGridFit.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/IconRepeat.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/GroupBoardModel.kt",
        ).forEach { rel ->
            val n = SourceRoots.text(rel).lines().size
            assertTrue(n <= 500, "$rel dài $n dòng — trần là 500 (CLAUDE.md §4.1)")
        }
    }
}
