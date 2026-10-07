package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.87 R-SI1 (spec `docs/specs/kachi-287-look-and-keys.html` §4.4) → 2.92 (spec `kachi-292-shortcut-widget.html` R1/R3) —
 * bài canh TĨNH nối dây lưới lối tắt. Hình học (cỡ lớn nhất, khe cố định, hàng cuối căn giữa, kẹp, cuộn) khoá bằng test
 * thuần `ShortcutGridFitTest` + `ShortcutGridScrollTest` ở `:core`; bài này khoá chỗ NỐI: lưới widget (ô to + ô nén) đặt
 * icon bằng `ShortcutGridFit` với khe `KachiBars.SHORTCUT_GRID_GAP`, cuộn khi phép khớp nói cuộn, còn khối thanh nút KHÔNG
 * đổi một dòng hành vi (owner 03/10: khối thanh nút đã đúng — dài theo số app).
 */
class ShortcutGridFitWiringContractTest {

    private fun code(file: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$file")

    private val view by lazy { code("ShortcutIconsView.kt") }
    private val layout by lazy { code("ShortcutGridLayout.kt") }

    @Test
    fun `luoi widget dat icon bang ShortcutGridFit qua ShortcutGridLayout`() {
        // 2.93 · R1 — ĐỔI GHIM có lý do (spec `kachi-293-slot.html`, SHORTCUT-GRID-SCROLL-KEEP): khung mới nhận vị trí cuộn người
        // lái đã chọn ở khung cũ (`keep`) — lượt dựng lại không còn đưa lưới về đầu.
        val grid = SourceRoots.body(view, "private fun buildGrid(items: List<AppShortcut>, keep: ShortcutScrollKeep.Wanted)")
        // 2.93 wave 2A · SHORTCUT-SCROLL-REBUILD — ĐỔI GHIM có lý do (spec `kachi-293-wave2a.html` §4.3): khung báo thêm mỗi cú cuộn
        // của người lái để NHỚ theo ô (view mới của ô sau restyle/dựng lại tiếp tục từ đó — `ShortcutScrollRebuildWiringContractTest`).
        assertTrue(grid.contains("ShortcutGridLayout(context, keep, { w -> scrollKey?.let { ShortcutScrollMemory.remember(it, w) } }) { px ->") &&
            grid.contains("if (gen == generation) fitIcons(px)"),
            "lưới dựng MỘT khung khớp, báo cỡ của lượt cũ bị bỏ, mang vị trí cuộn của khung cũ")
        assertTrue(grid.contains("items.forEach { box.addView(cell(it)) }"))
        assertTrue(grid.contains("LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)"), "khung lấp ô widget")
        listOf("LinearLayout(", "cellLp()", "chunked(", "gridCols").forEach {
            assertFalse(grid.contains(it), "lưới còn '$it' ⇒ quay lại ô cố định/LinearLayout lồng (trước R-SI1)")
        }
        // Khung khớp: tính ở lượt ĐO theo (rộng, cao, số icon), đặt theo vị trí của `:core`.
        val refit = SourceRoots.body(layout, "private fun refit(w: Int, h: Int, gap: Int, minIcon: Int)")
        assertTrue(refit.contains("ShortcutGridFit.fit("), "phép khớp đi qua :core")
        // 2.92 — ĐỔI GHIM có lý do: khe tỉ lệ `GAP_RATIO` (0,3 × icon) đã gỡ (owner 06/10: icon bé, lề hai bên to) ⇒
        // khe CỐ ĐỊNH đọc từ thang (`Bars.SHORTCUT_GRID_GAP`, đo ở onMeasure) truyền thẳng vào phép khớp.
        assertTrue(refit.contains("ShortcutGridFit.fit(childCount, w, h, gap, minIcon, dpi(context, Bars.SHORTCUT_GRID_MAX_ICON))"))
        assertTrue(refit.contains("if (it.widthPx == w && it.heightPx == h && it.count == childCount) return it"),
            "khớp lại CHỈ khi khung hoặc số icon đổi — không thrash")
        assertTrue(refit.contains("post { onIconPx(f.iconPx) }"), "đổi drawable SAU lượt bố trí, không giữa lượt đo")
        val measure = SourceRoots.body(layout, "override fun onMeasure(")
        assertTrue(measure.contains("Bars.SHORTCUT_GRID_MIN_ICON") && measure.contains("refit("))
        assertTrue(measure.contains("val gap = dpi(context, Bars.SHORTCUT_GRID_GAP)"), "khe cố định từ thang, không tỉ lệ")
        assertTrue(measure.contains("MeasureSpec.makeMeasureSpec(f.iconPx + 2 * padX, MeasureSpec.EXACTLY)"), "mọi icon CÙNG cỡ")
        val place = SourceRoots.body(layout, "override fun onLayout(")
        assertTrue(place.contains("f.left(i)") && place.contains("f.top(i)"), "vị trí (kể cả hàng cuối căn giữa) từ :core")
    }

    @Test
    fun `hinh chung cua app da go nap lai dung co khop`() {
        val fit = SourceRoots.body(view, "private fun fitIcons(iconPx: Int)")
        assertTrue(fit.contains("cells.forEach { if (it.generic) genericIcon(it.view) }"))
        assertTrue(SourceRoots.body(view, "private fun genericIcon(v: ImageView)").contains("KachiIcons.res(GENERIC_ICON, iconSizeDp())"))
        assertTrue(SourceRoots.body(view, "private fun load(gen: Int)").contains("cell.generic = icon == null"))
        assertTrue(SourceRoots.body(view, "private fun rebuild()").contains("fittedDp = 0"), "lượt dựng mới không mang cỡ khớp cũ")
    }

    @Test
    fun `khoi thanh nut khong dung phep khop - khe co dinh nhu 2_86`() {
        val rebuild = SourceRoots.body(view, "private fun rebuild()")
        // 2.93 · R1 — ĐỔI GHIM có lý do: lưới nhận thêm vị trí cuộn của khung cũ; nhánh khối thanh nút KHÔNG đổi.
        assertTrue(rebuild.contains("if (grid) buildGrid(items, keep) else items.forEach { addView(cell(it), cellLp()) }"))
        // 2.89 · B3 — đổi chân có chủ ý: khe đi qua `shortcutSlotPx` (= `SHORTCUT_CELL`, sàn 48 dp THẬT khi thanh co) —
        // CÙNG hàm với `shortcutStripLength`; vẫn KHÔNG qua lưới khớp (vế dưới).
        assertTrue(SourceRoots.body(view, "private fun cellPx()").contains("shortcutSlotPx(context)"))
        assertTrue(SourceRoots.body(view, "private fun baseIconDp()")
            .contains("if (grid && !compact) Bars.SHORTCUT_GRID_ICON else Bars.SHORTCUT_ICON"))
        val cell = SourceRoots.body(view, "private fun cell(sc: AppShortcut)")
        // 2.96 DOCK-ICON-HALF-GAP — ĐỔI GHIM có lý do (owner 07/10 "chỉ cần chừa 1/2 khoảng trống hiện tại" rồi "hơi sát quá, giảm lại chút"): icon thanh nút = 
        // `dockIconPx()` (SHORTCUT_DOCK_ICON kẹp trong khe − XS mỗi bên, không dưới cỡ cũ); khe vẫn cố định, vẫn không qua lưới khớp.
        assertTrue(cell.contains("if (!grid) {") && cell.contains("val pad = (cellPx() - dockIconPx()) / 2"),
            "khối thanh nút: lề khe cố định quanh icon thanh nút")
        val dockIcon = SourceRoots.body(view, "private fun dockIconPx()")
        assertTrue(dockIcon.contains("Bars.SHORTCUT_DOCK_ICON") && dockIcon.contains("cellPx() - 2 * dpi(context, Sp.XS)") &&
            dockIcon.contains("coerceAtLeast(dpi(context, iconSizeDp()))"), dockIcon)
        assertEquals(60, KachiBars.SHORTCUT_DOCK_ICON, "44 + (93 − 44)/3 — owner: nửa thì sát, lùi về ~2/3 phần chừa cũ")
        listOf("ShortcutGridFit", "ShortcutGridLayout").forEach {
            assertFalse(SourceRoots.body(view, "internal fun shortcutStripLength(ctx: Context, n: Int)").contains(it))
            assertFalse(SourceRoots.body(code("ControlDockView.kt"), "private fun shortcutStrip()").contains(it),
                "khối thanh nút không được đi qua lưới khớp ($it)")
        }
        // Cỡ khớp chỉ đến từ khung lưới — khối thanh nút không có khung đó nên `fittedDp` luôn 0 ⇒ luôn cỡ gốc.
        assertEquals(1, Regex("""fitIcons\(""").findAll(view).count() - 1, "fitIcons chỉ một chỗ gọi (khung lưới)")
    }

    /**
     * 2.92 — ĐỔI GHIM có lý do (spec 292 R1.2): sàn icon 28 dp (R-SI1) → 40 dp = 48 − khe 8 ⇒ ô chạm (icon + khe) ≥ 48 dp
     * ở chế độ khớp; khung không xếp nổi ở sàn ⇒ CUỘN thay vì để khối 28 dp tràn khung. Khe 8 dp = khe giữa hai icon của
     * khối thanh nút (52 − 44) — một nhịp cho hai bề mặt của cùng danh sách.
     */
    @Test
    fun `san 40 tran 120 dp, khe 8 dp cung nhip thanh nut, dich cham cua luoi gom ca khe`() {
        assertEquals(8, KachiBars.SHORTCUT_GRID_GAP)
        assertEquals(KachiBars.SHORTCUT_CELL - KachiBars.SHORTCUT_ICON, KachiBars.SHORTCUT_GRID_GAP, "cùng khe khối thanh nút")
        assertEquals(40, KachiBars.SHORTCUT_GRID_MIN_ICON)
        assertEquals(KachiSpace.TOUCH, KachiBars.SHORTCUT_GRID_MIN_ICON + KachiBars.SHORTCUT_GRID_GAP, "ô chạm = sàn + khe")
        assertEquals(120, KachiBars.SHORTCUT_GRID_MAX_ICON, "spec §4.4: icon tối đa 120 dp")
        // Vùng chạm = icon + nửa khe mỗi bên: lề trong của con = nửa khe (khung đặt), không phải lề cố định.
        val refit = SourceRoots.body(layout, "private fun refit(w: Int, h: Int, gap: Int, minIcon: Int)")
        assertTrue(refit.contains("getChildAt(i).setPadding(padX, padY, padX, padY)"))
        assertFalse(SourceRoots.text("src/main/java/com/byd/clusternav/launcher/KachiSpaceBars.kt").contains("SHORTCUT_GRID_CELL"),
            "khe cố định 64 dp của lưới đã gỡ (R-SI1) — còn nó là còn chỗ để ai đó dùng lại")
    }

    /**
     * 2.92 · R3 — nhiều app ⇒ CUỘN theo trục dài. Hình học (trục, số dòng, quãng cuộn) ở `:core` (`ShortcutGridScrollTest`);
     * bài này khoá ĐƯỜNG NỐI của tầng vẽ: kéo bị chặn sau ngưỡng chạm (icon đang nhấn nhận CANCEL — không mở nhầm app),
     * DOWN vào lề KHÔNG bị nuốt (ô vẫn nhận chạm/nhấn giữ như trước), vị trí cuộn kẹp lại mỗi lượt khớp, trôi bằng
     * `OverScroller`, quãng cuộn thật cho `canScroll*` + mép mờ, trợ năng cuộn, nhật ký `WidgetFit` một dòng/lượt đổi.
     */
    @Test
    fun `cuon khi nhieu app - chan keo, khong nuot cham le, kep vi tri, troi, tro nang, nhat ky`() {
        val intercept = SourceRoots.body(layout, "override fun onInterceptTouchEvent(ev: MotionEvent)")
        assertTrue(intercept.contains("if (axis == Scroll.NONE) return false"), "không cuộn ⇒ chạm y như 2.91")
        assertTrue(intercept.contains("abs(along(ev, i) - lastAlong) > touchSlop"), "chặn kéo SAU ngưỡng chạm")
        assertTrue(intercept.contains("parent?.requestDisallowInterceptTouchEvent(true)"))
        assertTrue(intercept.contains("return dragging"))
        val touch = SourceRoots.body(layout, "override fun onTouchEvent(ev: MotionEvent)")
        assertTrue(touch.contains("MotionEvent.ACTION_DOWN -> return dragging"), "DOWN vào lề không bị nuốt ⇒ ô nhận chạm")
        // Review 292 Pass 1 [P2] — ĐỔI GHIM có lý do: bản đầu `scrollAlongTo(position() + (lastAlong - a).roundToInt())` rồi
        // `lastAlong = a` vứt phần lẻ mỗi khung ⇒ kéo chậm (< 0,5 px/khung, toạ độ chạm lẻ) không dời nội dung. Nay dời phần
        // NGUYÊN và dồn phần lẻ (tổng dời = tổng ngón đi, lệch < 1 px — bất biến của `ScrollView` với toạ độ int).
        assertTrue(touch.contains("val d = (lastAlong - a).toInt()") && touch.contains("scrollAlongTo(position() + d)") &&
            touch.contains("lastAlong -= d"), "kéo dồn phần lẻ, không làm tròn rồi vứt mỗi lượt")
        assertFalse(touch.contains("roundToInt"), "không làm tròn delta kéo từng lượt (vứt phần lẻ)")
        assertTrue(touch.contains("if (dragging) fling()"))
        val refit = SourceRoots.body(layout, "private fun refit(w: Int, h: Int, gap: Int, minIcon: Int)")
        assertTrue(refit.contains("settleScroll(f)") && refit.contains("report(f)"))
        val settle = SourceRoots.body(layout, "private fun settleScroll(f: ShortcutGridFit.Fit)")
        // 2.93 · R2 — ĐỔI GHIM có lý do (SHORTCUT-SCROLL-DOCK-RELAYOUT, QA 2.92 264 → 43): kẹp vị trí NGƯỜI LÁI CHỌN theo quãng
        // mới (`ShortcutScrollKeep.applied` — kẹp `f.maxScrollPx`, bài `ShortcutScrollKeepTest`), không kẹp chính vị trí đang áp.
        assertTrue(settle.contains("ShortcutScrollKeep.applied(wanted, f)") && settle.contains("scrollTo(x, y)"),
            "vị trí cuộn = lựa chọn của người lái kẹp vào quãng mới")
        assertFalse(settle.contains("scrollX.coerceIn") || settle.contains("scrollY.coerceIn"),
            "kẹp chính vị trí đang áp ⇒ một lượt đo ở khung lạ xoá vĩnh viễn lựa chọn của người lái")
        assertTrue(SourceRoots.body(layout, "private fun scrollAlongTo(p: Int)").contains("p.coerceIn(0, fit?.maxScrollPx ?: 0)"))
        assertTrue(SourceRoots.body(layout, "private fun fling()").contains("scroller.fling("))
        assertTrue(SourceRoots.body(layout, "override fun computeScroll()").contains("scroller.computeScrollOffset()"))
        assertTrue(SourceRoots.body(layout, "override fun computeVerticalScrollRange()").contains("fit?.contentHeightPx"))
        assertTrue(SourceRoots.body(layout, "override fun computeHorizontalScrollRange()").contains("fit?.contentWidthPx"))
        // Review 292 Pass 1 [P2] — mép mờ chỉ vẽ trong `View.draw(Canvas)`; ViewGroup mặc định WILL_NOT_DRAW ⇒ bỏ qua hàm đó
        // [ĐO decompile framework.jar máy ảo A10] ⇒ thiếu dòng này thì "mép mờ báo còn nữa" (R3) không bao giờ hiện.
        val init = SourceRoots.body(layout, "init {")
        assertTrue(init.contains("setWillNotDraw(false)") && init.contains("setFadingEdgeLength("),
            "khung cuộn phải tự vẽ (như ScrollView.initScrollView) để mép mờ hiện")
        assertTrue(settle.contains("isVerticalFadingEdgeEnabled = f.scroll == Scroll.VERTICAL") &&
            settle.contains("isHorizontalFadingEdgeEnabled = f.scroll == Scroll.HORIZONTAL"), "mép mờ đúng trục cuộn")
        // Review 292 Pass 1 [P3] — khung không bấm/không focus ở AUTO không "quan trọng" với trợ năng [ĐO decompile A10
        // `View.isImportantForAccessibility`] ⇒ cuộn được thì phải YES, nếu không nút cuộn (R3) không tới dịch vụ trợ năng.
        assertTrue(Regex("""importantForAccessibility =\s*if \(f\.scroll == Scroll\.NONE\) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_YES""")
            .containsMatchIn(settle), "khung cuộn phải là nút trợ năng quan trọng; không cuộn giữ AUTO như 2.91")
        val a11y = SourceRoots.body(layout, "override fun performAccessibilityAction(action: Int, arguments: Bundle?)")
        assertTrue(a11y.contains("ACTION_SCROLL_FORWARD") && a11y.contains("ACTION_SCROLL_BACKWARD"))
        assertTrue(SourceRoots.body(layout, "private fun report(f: ShortcutGridFit.Fit)").contains("if (f == shown) return"),
            "nhật ký chỉ khi bố cục ĐỔI, không theo lượt đo")
        assertTrue(layout.contains("const val TAG = \"WidgetFit\""), "cùng thẻ logcat với FitGridLayout")
        // Hàm mới có chỗ gọi thật (CLAUDE.md §8).
        listOf("settleScroll(", "scrollAlongTo(", "fling(", "secondaryUp(", "endDrag(", "track(").forEach {
            assertTrue(Regex(Regex.escape(it)).findAll(layout).count() >= 2, "'$it' khai mà không gọi")
        }
    }

    /**
     * 2.93 · SHORTCUT-GRID-SCROLL-KEEP + SHORTCUT-SCROLL-DOCK-RELAYOUT (spec `kachi-293-slot.html` R1/R2) — đường NỐI của luật
     * `ShortcutScrollKeep` (luật thuần khoá ở `ShortcutScrollKeepTest`, gồm đúng số QA 264/43):
     *  - phát tin cài/gỡ/đổi gói: gói ngoài danh sách không chạm lưới; gói trong danh sách chỉ nạp lại icon TẠI CHỖ;
     *  - gắn lại view cùng danh sách ⇒ chỉ làm mới; danh sách đổi ⇒ dựng lại và đem vị trí cuộn sang khung mới;
     *  - vị trí người lái chọn chỉ có MỘT chỗ ghi (cuộn do người lái), lượt khớp chỉ đọc nó;
     *  - nhật ký `WidgetFit` mang `pos=áp/chọn` + `view=` để chốt lượt đo lạ từ đâu (CLAUDE.md §11).
     */
    @Test
    fun `vi tri cuon song qua phat tin goi, gan lai, dung lai va luot do khung la`() {
        val packages = SourceRoots.body(view, "private val onPackages = object : BroadcastReceiver()")
        assertTrue(packages.contains("ShortcutScrollKeep.touches(pkg, cells.map { it.sc.pkg })") && packages.contains("refresh()"))
        assertFalse(packages.contains("rebuild()"), "phát tin gói KHÔNG dựng lại cả lưới (2.92: mọi phát tin ⇒ cuộn về 0)")
        assertTrue(SourceRoots.body(view, "override fun onAttachedToWindow()")
            .contains("if (ShortcutScrollKeep.needsRebuild(built, ShortcutHub.items())) rebuild() else refresh()"))
        assertTrue(view.contains("ShortcutScrollKeep.needsRebuild(built, ShortcutHub.items())) rebuild() }"), "bên nghe danh sách")
        val rebuild = SourceRoots.body(view, "private fun rebuild()")
        // wave 2A — ĐỔI GHIM có lý do: không có khung cũ (view MỚI của ô) ⇒ bản nhớ theo ô trước khi về ORIGIN.
        assertTrue(rebuild.contains("(getChildAt(0) as? ShortcutGridLayout)?.keep ?: scrollKey?.let(ShortcutScrollMemory::recall) ?: " +
            "ShortcutScrollKeep.Wanted.ORIGIN") && rebuild.contains("built = items"), "lượt dựng lại lấy vị trí của khung CŨ trước khi tháo nó")
        val refresh = SourceRoots.body(view, "private fun refresh()")
        assertTrue(refresh.contains("load(generation)") && refresh.contains("paintDim()"))
        assertFalse(refresh.contains("removeAllViews") || refresh.contains("rebuild"), "làm mới không đổi cây view")
        // Một chỗ GHI vị trí người lái chọn (khai báo là `wanted: Wanted = start`, không khớp mẫu): cuộn do người lái.
        assertEquals(1, Regex("""\bwanted = """).findAll(layout).count(), "chỉ cuộn do người lái được ghi 'wanted'")
        assertTrue(SourceRoots.body(layout, "private fun scrollAlongTo(p: Int)")
            .contains("wanted = ShortcutScrollKeep.userScrolled(axis, c)"))
        val report = SourceRoots.body(layout, "private fun report(f: ShortcutGridFit.Fit)")
        assertTrue(report.contains("pos=%d/%d view=%x") && report.contains("System.identityHashCode(this)"))
        assertTrue(report.contains("frame=%dx%d"), "dạng `frame=W×H` của OC-292-1 giữ nguyên")
    }
}
