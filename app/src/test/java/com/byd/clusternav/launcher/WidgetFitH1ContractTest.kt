package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ QA 04/10 + soát vòng 3 (2.87, làn H1) — chỗ NỐI của các bản vá khớp widget ═══════════════════════════════════
 *
 * Quyết định thuần đã khoá ở `:core` (`GridFitWindowWidgetTest`, `FitRulesRound3Test`, `MsDockLabelFitTest`). Dự án
 * không dùng Robolectric ⇒ bài này khoá rằng tầng vẽ ĐI QUA các quyết định ấy (CLAUDE.md §8: hàm mới có chỗ gọi thật):
 *  1. [QA P2] icon cỡ cố định chặn theo Ô ([FitRules.iconScale]) ở lượt áp của lưới, KHÔNG ở lượt đo dò;
 *  2. [QA P2] dạng nhãn DỰ PHÒNG (ngang 2 dòng) có trong danh sách đo dò và tới được [GridFit] với cờ `reserve`;
 *  3. [soát 3 P2] ô NHÓM trong ô nén đổ TẠI CHỖ — `refreshRead` không còn thay view nó mỗi nhịp; mọi nhánh của
 *     `WidgetViews.mini` hoặc có hàm đổ, hoặc tự lo, hoặc là nút (đường đọc-lại riêng);
 *  4. [soát 3 P2] lưới giữ cờ "đọc được" khi một ô con bị thay; dòng `WidgetFit` chỉ khi bố cục đổi thật;
 *  5. [QA P3] widget đồng hồ đổ lại theo nhịp đồng hồ 10 s có sẵn, không dựng lại ô.
 */
class WidgetFitH1ContractTest {

    private fun code(file: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$file")

    private val scale by lazy { code("FitScale.kt") }
    private val probe by lazy { code("FitProbe.kt") }
    private val layout by lazy { code("FitGridLayout.kt") }
    private val widgets by lazy { code("WidgetViews.kt") }
    private val tele by lazy { code("WidgetTelemetry.kt") }
    private val groups by lazy { code("GroupTiles.kt") }
    private val registry by lazy { code("WidgetRefreshers.kt") }
    private val home by lazy { code("KachiHomeActivity.kt") }

    @Test
    fun `1 - icon chan theo o o luot ap cua luoi, khong o luot do do`() {
        val apply = SourceRoots.body(scale, "fun apply(k: Double, f: Form, n: Int, cw: Int = Int.MAX_VALUE, ch: Int = Int.MAX_VALUE)")
        assertTrue(apply.contains("params(b, iconK(b, k), rot)"), "LayoutParams của icon áp hệ số đã chặn theo ô")
        val iconK = SourceRoots.body(scale, "private fun iconK(b: Base, k: Double)")
        // Soát vòng 6 — ĐỔI GHIM có lý do: lề của icon theo DẠNG (`room` — dạng ngang xoay lề "chỉ dọc" của con khối chính, như
        // padding/params); bản cũ tính một lần theo dạng dọc ⇒ icon ô nút kính 26px thay vì 29px (QA3; FitRulesRound6Test).
        assertTrue(iconK.contains("val own = room(b, form, k, cellW, cellH)"))
        assertTrue(SourceRoots.body(scale, "private fun room(b: Base, f: Form, k: Double, cw: Int, ch: Int)")
            .contains("FitRules.iconScale(k, b.lpW, b.lpH, cw - sc(x, k), ch - sc(y, k))"))
        // ĐỔI GHIM (soát vòng 4 P3): bản H1 nói "nút nhạc 48dp không bị chặn" mà chỉ kiểm `!is ImageView` — nút nhạc LÀ
        // ImageView (MediaWidgetView.mbtn) nên vẫn bị chặn dưới 48dp. Quyết theo R-WF4 ("nút nhạc 48dp không co dưới
        // 48dp"): nút BẤM cỡ cố định được miễn — chặn theo ô chỉ cho icon không bấm được.
        assertTrue(iconK.contains("b.v !is ImageView || b.v.isClickable"), "chỉ ICON không bấm được — nút nhạc 48dp không bị chặn theo ô")
        assertTrue(code("MediaWidgetView.kt").contains("fun mbtn(icon: String) = ImageView(ctx)"), "nút nhạc là ImageView ⇒ cần miễn tường minh")
        // Lưới truyền cỡ ô; đo dò KHÔNG (hộp tự nhiên ở thang 1 phải đo không chặn).
        assertTrue(SourceRoots.body(layout, "private fun applyAll(").contains("apply(f.scale, opt.form, opt.lines, f.cellW, f.cellH)"))
        assertTrue(SourceRoots.body(probe, "private fun shape(").contains("fs.apply(1.0, opt.form, opt.lines)"))
        assertTrue(SourceRoots.body(scale, "private fun insets()").contains("byView[cur]?.pad"), "lề trong mọi khung bọc")
    }

    @Test
    fun `2 - dang nhan du phong toi duoc GridFit`() {
        // ĐỔI GHIM (J1 + soát vòng 4 P3): giữa dự phòng và chỉ-icon nay có ba dạng nhãn NGẮN, và mọi dạng PHỤ (dự phòng ·
        // nhãn ngắn · chỉ-icon) đo LƯỜI ([FitProbe.more]); phép dùng lại số đo của ô không nhãn dời sang `twin`/`fill`
        // (bản H1 ghim chuỗi trong thân `need`). `WidgetFitJ1ContractTest` khoá phần mới.
        assertEquals(listOf(false, false, false, true, false, false, false, false), FitProbe.OPTIONS.map { it.reserve })
        assertEquals(FitProbe.OPTIONS.indexOfFirst { it.reserve } + 1, FitProbe.OPTIONS.indexOfFirst { it.short }, "dự phòng ngay trước nhãn ngắn")
        assertEquals(GridFit.Form.ICON_ONLY, FitProbe.OPTIONS.last().form, "chỉ-icon là dạng cuối")
        assertTrue(SourceRoots.body(layout, "private fun combine(").contains("reserve = FitProbe.OPTIONS[i].reserve"))
        assertTrue(SourceRoots.body(probe, "private fun shape(").contains("reserve = opt.reserve"))
        // Ô không nhãn: ngang-2 ≡ ngang-1 — dùng lại số đo (không thêm lượt đo dò), cờ dùng được theo ĐÚNG chỉ số.
        val twin = SourceRoots.body(probe, "private fun twin(i: Int, fs: FitScale)")
        assertTrue(twin.contains("opt.lines == 2 && opt.form == Form.HORIZONTAL -> at(opt.short, Form.HORIZONTAL, 1)"))
        assertTrue(SourceRoots.body(probe, "private fun fill(").contains("usable[i] = usable[at]"))
    }

    @Test
    fun `3 - o nhom trong o nen do tai cho, moi nhanh mini co duong khong thay view`() {
        val mini = SourceRoots.body(groups, "fun mini(ctx: Context, id: String, data: WidgetData)")
        assertTrue(mini.contains("return WidgetRefreshers.live(root, ::fillGroupMini)"), "ô nhóm nén đăng ký hàm đổ")
        val fill = SourceRoots.body(groups, "fun fillGroupMini(d: WidgetData)")
        assertTrue(fill.contains("GroupBoard.of(id, d.car, d.units)?.let(::paint)"))
        val paint = SourceRoots.body(groups, "fun paint(m: GroupBoardModel)")
        assertTrue(paint.contains("if (tone != shown)"), "sắc thái chỉ ghi khi đổi (KachiGlass dựng lại nền là việc đắt)")
        assertTrue(mini.contains("paint(first)"), "lượt dựng và lượt đổ đi qua CÙNG một cửa")
        listOf("ImageView(", "LinearLayout(", "TextView(", "addView(", "text(ctx").forEach {
            assertFalse(fill.contains(it) || paint.contains(it), "hàm đổ dựng view `$it` ⇒ chỉ dời cú giật vào trong")
        }
        assertTrue(SourceRoots.body(groups, "private fun fallback(").contains("WidgetRefreshers.live("), "mã lạ cũng không bị thay mỗi nhịp")
        // Mọi bộ dựng mà `mini()` gọi: phân loại tường minh ⇒ bộ dựng mới chưa phân loại là ĐỎ.
        val body = SourceRoots.body(widgets, "private fun mini(")
        // Bộ dựng = lời gọi ĐẦU nhánh (sau `->`, `else`, hoặc điều kiện `if (…)`), không phải trợ giúp trong lambda giá trị.
        val builders = Regex("""(?:->|else|\))\s+([A-Za-z_][\w.]*)\(ctx[,)]""").findAll(body).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf("miniCard", "tyreMini", "PhotoWidgetView", "ShortcutIconsView", "GroupTiles.mini", "actionTile", "telemetryMini"),
            builders, "nhánh mới của mini() — xếp nó vào một trong ba nhóm dưới đây",
        )
        // (a) có hàm đổ tại chỗ
        assertTrue(SourceRoots.body(tele, "internal fun miniCard(").contains("WidgetRefreshers.live("))
        assertTrue(SourceRoots.body(widgets, "private fun tyreMini(").contains("miniCard("))
        assertTrue(SourceRoots.body(tele, "internal fun telemetryMini(").contains("WidgetRefreshers.live("))
        // (b) tự lo nội dung — refreshRead giữ nguyên view của chúng
        assertTrue(WorkspaceRenderPlanner.selfDriven("w_photos") && WorkspaceRenderPlanner.selfDriven("w_apps"))
        // (c) nút: đường đọc-lại riêng, xét TRƯỚC mọi đường thay view
        val refresh = SourceRoots.body(widgets, "fun refreshRead(")
        assertTrue(refresh.indexOf("CapabilityCatalog.isWrite(tag.id)") in 0 until refresh.indexOf("removeViewAt"))
    }

    @Test
    fun `4 - luoi giu co doc duoc khi o con bi thay, nhat ky chi khi bo cuc doi`() {
        val due = SourceRoots.body(layout, "private fun due(v: View, measured: Boolean)")
        assertTrue(due.contains("it.cell.check(same, legible, now)"), "đọc cờ giữ lại, không đọc kết quả khớp vừa bị xoá")
        assertFalse(due.contains("fit?.legible"), "fit bị xoá khi ô con đổi ⇒ 'không đọc được' giả ⇒ nhịp 30 s")
        val refit = SourceRoots.body(layout, "private fun refit(w: Int, h: Int)")
        assertTrue(refit.contains("fit = f; shown = f; legible = f.legible;"))
        assertTrue(refit.contains("val same = f == shown && keyW == w && keyH == h && keyN == kids.size"))
        listOf("override fun onViewAdded(", "override fun onViewRemoved(").forEach { sig ->
            val b = SourceRoots.body(layout, sig)
            assertFalse(b.contains("legible") || b.contains("shown"), "$sig không được xoá cờ giữ lại")
        }
    }

    @Test
    fun `5 - dong ho do lai theo nhip dong ho, khong dung lai o`() {
        val tick = home.substringAfter("private val tick = object : Runnable").substringBefore("override fun attachBaseContext")
        assertTrue(tick.contains("WidgetRefreshers.tickAll(workspace)"), "dùng LẠI nhịp 10 s của đồng hồ thanh trên")
        assertTrue(tick.contains("handler.postDelayed(this, 10_000)"))
        val clock = SourceRoots.body(widgets, "private fun clock(")
        assertTrue(clock.contains("WidgetRefreshers.liveTick(root) { fillClock(last) }"))
        assertTrue(widgets.contains("\"w_clock\"  -> miniCard(ctx, data, \"ic-sun\", KachiTheme.INK, ticks = true)"))
        assertTrue(SourceRoots.body(tele, "internal fun miniCard(").contains("if (ticks) WidgetRefreshers.liveTick(card.root) { fillCard(last) }"))
        val all = SourceRoots.body(registry, "fun tickAll(root: View)")
        assertTrue(all.contains("FitGridLayout.contentChanged(root)"), "giờ dài ra (9:59 → 10:00) ⇒ báo lưới khớp")
        listOf("addView(", "removeView", "inflate(").forEach { assertFalse(all.contains(it), "tickAll không dựng view") }
        assertTrue(SourceRoots.text("src/main/res/values/ids.xml").contains("kachi_widget_tick_fill"))
    }

    @Test
    fun `tep cham toi duoi tran 500 dong`() {
        listOf("GroupTiles.kt", "WidgetRefreshers.kt", "KachiHomeActivity.kt", "FitScale.kt", "FitProbe.kt", "FitGridLayout.kt")
            .forEach { name ->
                val n = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/$name").lines().size
                assertTrue(n <= 500, "$name dài $n dòng — trần là 500")
            }
    }
}
