package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.89 · B3 DOCK-SCALE — DÂY NỐI của cỡ thanh nút (`kachi-289-field-fixes` §B3) ═══════════════════════════════════════
 *
 * Phép số đã có test hành vi (`BarScaleTest` ở `:core`, `BarScaleBudgetContractTest`). Bài này khoá phần chỉ đọc mã mới
 * thấy được: (1) 100 % đi ĐÚNG đường cũ; (2) cả cây view của thanh dựng bằng MỘT `Context` co/giãn, không bộ đệm toàn
 * cục (K1); (3) thanh + vùng ô đặt lại qua đường đổi viền sẵn có khi cỡ đổi (một lượt — VdAppHost/WorkspaceView không
 * biết gì về cỡ); (4) dải mẫu ở Cài đặt TRƠ và kéo không ghi gì; (5) đích chạm ≥ 48 dp thật; (6) mọi hàm mới có call
 * site (CLAUDE.md §8); (7) khai báo khoá theo hồ sơ đủ (K3/K6). Thử ĐỎ: gỡ `scalePct` khỏi `layoutChanged`; đổi dải mẫu
 * sang `ControlTileState.shared`; cho `onPreview` gọi `deps.`; bỏ nhánh `isIdentity` của `wrap`.
 */
class BarScaleWiringContractTest {

    private fun code(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")
    private val dock by lazy { code("ControlDockView.kt") }
    private val ctxSrc by lazy { code("DockScaleContext.kt") }
    private val section by lazy { code("SettingsBarScaleSection.kt") }

    @Test
    fun `thanh nut dung MOT Context co gian cho ca cay view, 100 phan tram la context goc`() {
        assertTrue("private var ui: Context = context" in dock, "mặc định (100 %) ui LÀ context")
        assertTrue("ControlTileFactory(ui, control = { control }, size = TileSize.DOCK, state = tileState)" in
            SourceRoots.body(dock, "private fun factory()"), "bộ dựng ô dựng trên ui")
        val set = SourceRoots.body(dock, "fun setConfig(cfg: DockConfig)")
        assertTrue("if (BarScale.snap(cfg.scalePct) != uiPct) rescale() else rebuild()" in set)
        val rescale = SourceRoots.body(dock, "private fun rescale()")
        listOf("ui = DockScaleContext.wrap(context, uiPct)", "tiles = factory()", "restyle()").forEach {
            assertTrue(it in rescale, "rescale thiếu '$it'")
        }
        assertTrue("cornerRadius = dpi(ui, Sp.RADIUS_XL)" in SourceRoots.body(dock, "fun restyle("), "bo góc theo cỡ")
        assertTrue("if (BarScale.isIdentity(uiPct)) sized(tile) else hitCell(tile, targets)" in
            SourceRoots.body(dock, "private fun place("), "100 % ⇒ sized() y như 2.88")
        val rebuild = SourceRoots.body(dock, "private fun rebuild()")
        assertFalse("sized(" in rebuild, "mọi ô phải qua place() — sized() trực tiếp là bỏ qua khung chạm")
        assertEquals(4, Regex("""addView\(place\(""").findAll(rebuild).count(), "nút · gói lệnh · ô đọc · ô Launcher")
        assertTrue("def.kind == ControlKind.STEP && !config.isVertical()) 2 else 1" in rebuild, "ô STEP ngang = 2 đích")
        assertTrue("setPadding(p, p, p, p)" in SourceRoots.body(dock, "private fun applyPad()"), "100 % giữ lề cũ")
        val strip = SourceRoots.body(dock, "private fun shortcutStrip()")
        assertTrue("fillAcross = fill" in strip && "val fill = !BarScale.isIdentity(uiPct)" in strip)
    }

    @Test
    fun `khung cham - san 48dp that, tran 100 phan tram do tren context goc, tron be day`() {
        val cell = SourceRoots.body(dock, "private fun hitCell(")
        listOf(
            "BarScale.cellAlongPx(", "DockScaleContext.touchFloorPx(ui)", "val len100 = dpi(context,",
            "DockHitCell(ui, tile, w, h)", "LayoutParams(MATCH, along)", "LayoutParams(along, MATCH)",
        ).forEach { assertTrue(it in cell, "hitCell thiếu '$it'") }
        val hit = code("DockHitCell.kt")
        assertTrue(Regex(""":\s*TouchDelegate\(""").containsMatchIn(hit), "nới VÙNG CHẠM bằng TouchDelegate của nền tảng (lối StepTouchTarget)")
        assertTrue("BarScale.clampInto(x - tile.left, tile.width)" in hit && "BarScale.clampInto(y - tile.top, tile.height)" in hit)
        assertTrue("getTouchDelegateInfo" in hit, "TalkBack phải biết vùng nới")
        assertTrue("MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> active = false" in hit, "cử chỉ dính tới hết")
        // −/+ của ô STEP: sàn 48 dp THẬT khi ô dựng trên Context co/giãn (ở 100 % hai số bằng nhau).
        assertTrue("val min = dpi(host.context, Sp.TOUCH).coerceAtLeast(DockScaleContext.touchFloorPx(host.context))" in
            code("StepTouchTarget.kt"))
        assertTrue("fun touchFloorPx(ctx: Context): Int = dpi(unscaled(ctx), Sp.TOUCH)" in ctxSrc)
        // Khối lối tắt: khe = max(52 dp co/giãn, 48 dp thật), cùng hàm cho khe và bề dài.
        val icons = code("ShortcutIconsView.kt")
        assertEquals(2, Regex("""shortcutSlotPx\((ctx|context)\)""").findAll(icons).count(), "cellPx + shortcutStripLength")
        assertTrue("!fillAcross -> LayoutParams(cellPx(), cellPx())" in SourceRoots.body(icons, "private fun cellLp()"),
            "100 % / lưới widget giữ khe vuông như 2.88")
        // K7 — thông báo của ô gói lệnh hiện cỡ THẬT (không co theo thanh 50 %).
        assertTrue("Toast.makeText(DockScaleContext.unscaled(ctx), notice" in code("ControlTileFactory.kt"))
    }

    @Test
    fun `Context co gian - 100 la base, chi ghi de densityDpi, khong bo dem toan cuc (K1)`() {
        assertTrue("if (BarScale.isIdentity(pct)) base else Scaled(base, BarScale.snap(pct))" in ctxSrc)
        // Pass 2 · vietmap-dock-r1-7: ghi đè mang theo NGÔN NGỮ của base (LangHost) — không thì chuỗi ở cỡ ≠ 100 % theo ngôn ngữ máy.
        val init = SourceRoots.body(ctxSrc, "init {")
        val at = listOf("applyOverrideConfiguration(Configuration().apply {", "densityDpi = BarScale.scaledDpi(baseDpi, pct)",
            "setLocales(base.resources.configuration.locales)").map { init.indexOf(it) }
        assertTrue(at.all { it >= 0 } && at == at.sorted(), "ghi đè = densityDpi + ngôn ngữ của base, đúng thứ tự: $init")
        assertFalse("fontScale" in ctxSrc, "owner: hình đúng số kéo — không bù chữ (không sàn 90 %)")
        assertTrue("ContextThemeWrapper(base, themeOf(base))" in ctxSrc && "getActivityInfo(" in ctxSrc,
            "theme = theme THẬT của Activity (0 ⇒ getTheme chọn theme mặc định đè lên)")
        listOf("HashMap", "mutableMapOf", "LruCache", "SparseArray", "mapOf(").forEach {
            assertFalse(it in ctxSrc, "K1: không bộ đệm toàn cục ('$it') — wrapper phải sống/chết cùng Activity")
        }
        // Wrapper được giữ trong TRƯỜNG của view (không ở object).
        assertTrue(Regex("""DockScaleContext\.wrap\(""").findAll(dock).count() == 1)
    }

    /** Pass 2 · vietmap-dock-r1-8 — lề icon lối tắt khi lấp trọn bề dày: chỉ DỌC trục (phép số ở `BarScaleBudgetContractTest` f). */
    @Test
    fun `loi tat lap tron be day - le icon chi doc truc`() {
        val cell = SourceRoots.body(code("ShortcutIconsView.kt"), "private fun cell(sc: AppShortcut): ImageView")
        listOf("!fillAcross -> setPadding(pad, pad, pad, pad)", "vertical -> setPadding(0, pad, 0, pad)",
            "else -> setPadding(pad, 0, pad, 0)").forEach { assertTrue(it in cell, "thiếu `$it`: $cell") }
    }

    @Test
    fun `doi co dat lai thanh va vung o qua duong doi vien - mot luot`() {
        val area = SourceRoots.body(code("DockAreaLayout.kt"), "fun apply(")
        assertTrue("val barDensity = BarScale.scaledDensity(density, cfg.scalePct)" in area)
        assertTrue("dp(Bars.DOCK_THICK, barDensity)" in area && "dp(Bars.DOCK_WIDE, barDensity)" in area)
        assertTrue("val gap = dp(Sp.SLOT_GAP)" in area, "khe KHÔNG co (phải bằng khe giữa ô ở bốn chỗ)")
        val render = SourceRoots.body(code("KachiHomeRender.kt"), "internal fun KachiHomeActivity.render(")
        assertEquals(2, Regex("""prev\.dock\.scalePct != state\.dock\.scalePct""").findAll(render).count(),
            "cả layoutChanged (DockAreaLayout.apply) lẫn windows.reflow()")
        // Vùng ô khớp lại bằng đường SẴN CÓ (onLayout ⇒ VdAppHost.resize khử trùng; FitGridLayout/ShortcutGridLayout
        // onMeasure) — chúng không được biết gì về cỡ thanh.
        listOf("WorkspaceView.kt", "VdAppHost.kt", "FitGridLayout.kt", "ShortcutGridLayout.kt").forEach {
            assertFalse("BarScale" in code(it) || "DockScale" in code(it), "$it không được biết cỡ thanh")
        }
    }

    @Test
    fun `Cai dat - keo chi ve dai mau tro, tha moi ghi qua intent san co`() {
        assertTrue("SettingsBarScaleSection(context, rows, deps).build(body)" in
            SourceRoots.body(code("SettingsSections.kt"), "private fun display(body: LinearLayout)"))
        assertTrue("positions = BarScale.POSITIONS" in section && "current = BarScale.position(saved.dock.scalePct)" in section)
        assertTrue("onPreview = { pos -> preview.show(BarScale.ofPosition(pos)) }," in section)
        assertTrue("{ pos -> deps.onDockConfig(deps.state().dock.withScale(BarScale.ofPosition(pos))) }" in section)
        val show = SourceRoots.body(section, "fun show(pct: Int)")
        assertFalse("deps" in show || "onDockConfig" in show, "kéo KHÔNG ghi gì")
        assertTrue("frame.removeCallbacks(apply)" in show && "frame.postOnAnimation(apply)" in show, "gộp theo khung hình")
        // Dải mẫu = CHÍNH ControlDockView (K4: một đường dựng), trơ: bảng trạng thái riêng, cổng xe mặc định NoCar, khung nuốt chạm.
        // Review 2.89 Pass 3 · vietmap-dock-r2-4 — ĐỔI GHIM có lý do: bảng lệnh-cuối RIÊNG (`ControlLastSent()`), không bảng dùng chung
        // mà phím Đảo đọc (`refresh` ⇒ `setOn`/`setSel` mỗi lượt kéo ghi ảnh chụp cũ vào đó).
        assertTrue("ControlDockView(ctx, ControlTileState(ControlLastSent()))" in section)
        assertFalse("ControlTileState()" in section || "ControlLastSent.shared" in section, "dải mẫu không chạm bảng dùng chung")
        assertFalse(Regex("""\.control\s*=""").containsMatchIn(section), "không nối cổng xe thật vào dải mẫu")
        assertFalse("Bars.DOCK_TILE" in section, "K4: không phép cỡ ô thứ hai ở Cài đặt")
        assertTrue("override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true" in section)
        assertTrue("IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS" in section)
        val slider = code("SettingsRowsSlider.kt")
        assertTrue("onPreview: (Int) -> Unit = {}," in slider, "hàng cũ (độ trong suốt) không truyền ⇒ y nguyên")
        assertTrue("if (fromUser) onPreview(progress)" in SourceRoots.body(slider, "override fun onProgressChanged("))
    }

    @Test
    fun `luu ben theo ho so - khai du cho (K3, K6)`() {
        assertTrue("dockScalePct()" in SourceRoots.body(code("WorkspacePrefs.kt"), "fun loadDock()"))
        assertTrue("putDockScale(it, c.scalePct)" in SourceRoots.body(code("WorkspacePrefs.kt"), "fun saveDock(c: DockConfig)"))
        assertEquals(ProfileScope.Scope.PROFILE, ProfileScope.scopeOf("dock_scale"))
        assertTrue("dock_scale" in ProfileScope.LAUNCHER_LAYOUT_SUFFIXES)
        assertEquals(PrefType.STRING, ProfileScopeLauncher.DECLARED_TYPES["dock_scale"])
        assertTrue(ProfileSharePolicy.SHAREABLE.containsKey("dock_scale"), "bản chia sẻ mang theo cỡ thanh")
        // K6 — `SettingsCoverageContractTest` chỉ quét tệp có `getSharedPreferences(` nên KHÔNG thấy tệp mở rộng này.
        val entry = SettingsCatalog.ENTRIES.single { it.prefKey == "dock_scale" }
        assertEquals("display_bar_scale", entry.id)
        assertEquals(SettingsGroup.DISPLAY, entry.group)
    }

    /** CLAUDE.md §8 — hàm mới phải có call site ngoài chính định nghĩa. */
    @Test
    fun `moi ham moi deu co call site`() {
        val sites = mapOf(
            "DockScaleContext.wrap(" to listOf("ControlDockView.kt"),
            "DockScaleContext.touchFloorPx(" to listOf("ControlDockView.kt", "ShortcutIconsView.kt", "StepTouchTarget.kt"),
            "DockScaleContext.unscaled(" to listOf("ControlTileFactory.kt"),
            "DockScaleContext.baseDpiOf(" to listOf("ControlDockView.kt"),
            "BarScale.scaledDensity(" to listOf("DockAreaLayout.kt"),
            "BarScale.cellAlongPx(" to listOf("ControlDockView.kt"),
            "DockHitCell(" to listOf("ControlDockView.kt"),
            "thicknessPx()" to listOf("SettingsBarScaleSection.kt"),
            "dockScalePct()" to listOf("WorkspacePrefs.kt"),
            "putDockScale(" to listOf("WorkspacePrefs.kt"),
            ".withScale(" to listOf("SettingsBarScaleSection.kt"),
            "onPreview = " to listOf("SettingsBarScaleSection.kt"),
        )
        val missing = sites.flatMap { (call, files) -> files.filterNot { call in code(it) }.map { "$it → $call" } }
        assertEquals(emptyList<String>(), missing)
    }
}
