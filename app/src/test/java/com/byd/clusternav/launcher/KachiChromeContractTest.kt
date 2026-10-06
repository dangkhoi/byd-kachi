package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Calendar
import kotlin.streams.toList

/**
 * ═══ R-OP1/R-OP2 — BÀI CANH ĐỘ ĐỤC NỀN CHUNG: một chỗ ghi, đúng năm chỗ dựng, không lan ra ngoài màn chính ══════════
 *
 * Spec `docs/specs/kachi-287-look-and-keys.html` §3 R-OP2: *"hàm làm mờ chỉ được gọi ở 5 chỗ dựng của màn chính"*.
 * Mỗi bài một mắt xích: (1) [KachiChrome.apply] chỉ ở `ThemeHost` · (2) `fade` đúng năm chỗ dựng, KHÔNG ở Cài đặt/ngăn
 * kéo/lớp phủ giọng nói/bộ chọn thanh trên/bộ sửa bố cục · (3) không trên dòng mang thông tin (ô BẬT, WARN/ALERT, nền
 * widget bên thứ ba, đĩa ⇄, thẻ bánh cảnh báo) · (4) 0 % trong suốt là không-chạm · (5) đổi độ đục ⇒ `sync` báo đổi đúng
 * một lần · (6) thanh kéo ở Cài đặt (2.88) nối dây tới `deps.onColorChoice`, bước 5 % · (7) ba chỗ vẽ Canvas đọc hệ số
 * (kính trên ảnh, thẻ bánh thường, hai dải che hình nền — soát Pass 10) dùng đúng phép `drawnAlpha`.
 * Phần số (2.88: áp ĐÚNG số người chọn ở mọi nền) ở [ChromeOpacityExactContractTest].
 */
class KachiChromeContractTest {

    @AfterEach
    fun reset() {
        KachiTheme.applyTheme(ThemeMode.NIGHT, 12, ColorChoice.DEFAULT, null)
        KachiChrome.apply(ChromeOpacity.DEFAULT)
    }

    @Test
    fun `KachiChrome apply chi duoc goi tu ThemeHost`() {
        assertEquals(listOf("ThemeHost.kt"), callers("KachiChrome.apply("), "một chỗ ghi hệ số — cùng chủ với bảng màu")
        val host = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ThemeHost.kt")
        val sync = SourceRoots.body(host, "fun sync(")
        assertTrue("KachiChrome.apply(state.colorChoice.surfaceOpacity)" in sync, "đọc độ đục từ state (theo hồ sơ)")
        // 2.88 — độ đục không còn phụ thuộc ảnh nền (bỏ sàn đọc được) ⇒ tham số `hasArt` của 2.87 phải đi hẳn, không treo.
        assertFalse("hasArt" in host, "2.88: KachiChrome không còn nhận trạng thái ảnh nền")
        assertTrue(
            Regex("""KachiTheme\.applyTheme\([^\n]*\)\s+or\s+KachiChrome\.apply\(""").containsMatchIn(sync),
            "phải là `or` KHÔNG ngắn mạch — `||` nuốt mất lượt đổi độ đục khi bảng màu cũng đổi cùng nhịp",
        )
    }

    @Test
    fun `fade dung nam cho dung cua man chinh`() {
        assertEquals(
            listOf("ControlDockView.kt", "ControlTileFactory.kt", "KachiTopStrip.kt", "ReadTile.kt", "WallGlass.kt"),
            callers("KachiChrome.fade("),
        )
        assertEquals(
            listOf("TyreBoardView.kt", "WallGlass.kt", "WallView.kt"), callers("KachiChrome.fraction"),
            "hệ số chỉ đọc thẳng ở ba chỗ vẽ Canvas: cửa kính trên ảnh · thẻ bánh thường · hai dải che hình nền",
        )
        assertEquals(listOf("ThemeHost.kt"), callers("KachiChrome.apply("), "một chỗ ghi")
        assertTrue("KachiChrome.fade(KachiTheme.surface(ctx, size.radius))" in code(file("ControlTileFactory.kt")))
        assertTrue("KachiChrome.fade(KachiTheme.surface(ctx, size.radius, domain = pick.domain))" in code(file("ReadTile.kt")))
        // Mỗi chỗ đúng số lượt gọi: thanh trên + thanh nút dựng nền ở init/build VÀ restyle — thiếu restyle là đổi chủ
        // đề/độ đục xong thanh rơi về 100 %.
        assertEquals(2, count("KachiTopStrip.kt", "KachiChrome.fade("), "thanh trên: build() + restyle()")
        assertEquals(2, count("ControlDockView.kt", "KachiChrome.fade("), "thanh nút: init + restyle()")
        val dock = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ControlDockView.kt")
        assertTrue("background = KachiChrome.fade(" in SourceRoots.body(dock, "init {") &&
            "background = KachiChrome.fade(" in SourceRoots.body(dock, "fun restyle("))
        val strip = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiTopStrip.kt")
        assertTrue("stripRow.background = KachiChrome.fade(KachiTheme.card(" in SourceRoots.body(strip, "fun restyle("),
            "restyle thanh trên phải dựng lại nền qua fade()")
        assertTrue("background = KachiChrome.fade(KachiTheme.card(context, Sp.RADIUS_L, KachiTheme.BAR_TOP))" in
            SourceRoots.body(strip, "private fun build("), "build thanh trên")
    }

    @Test
    fun `khong lan ra Cai dat, ngan keo, lop phu giong noi, bo chon, bo sua bo cuc`() {
        val leaks = chromeLeaks(sources().associate { it.fileName.toString() to code(it) })
        assertEquals(emptyList<String>(), leaks, "R-OP2: chỉ màn chính mờ — Cài đặt/hộp thoại/ngăn kéo giữ nguyên")
        // Bảng màu KHÔNG nhân alpha: KachiTheme/KachiPalette* không biết độ đục.
        listOf("KachiTheme.kt", "KachiThemeDrawables.kt", "KachiPalette.kt", "KachiPaletteDerive.kt").forEach {
            assertFalse("KachiChrome" in code(file(it)) || "surfaceOpacity" in code(file(it)), "$it không được biết độ đục nền")
        }
    }

    @Test
    fun `khong mo nen mang thong tin`() {
        val tiles = code(file("ControlTileFactory.kt"))
        val applyBg = SourceRoots.body(tiles, "private fun applyBg(")
        assertTrue("KachiChrome.fade(KachiTheme.surface(ctx, size.radius))" in applyBg, "nhánh TẮT mờ")
        assertTrue(Regex("""if \(active\) KachiTheme\.gradientSoft\(ctx, size\.radius\) else""").containsMatchIn(applyBg), "nhánh BẬT trần")
        assertFalse("KachiChrome.fade(KachiTheme.gradientSoft" in tiles, "ô BẬT mang trạng thái — không mờ")
        // Ô con nhóm WARN/ALERT: màu nền là thông tin.
        val group = code(file("GroupTileViews.kt"))
        val surfaceOf = SourceRoots.body(group, "fun surfaceOf(")
        assertFalse("KachiChrome" in surfaceOf, "WARN/ALERT không mờ")
        assertTrue(Regex("""GroupTone\.WARN, GroupTone\.ALERT ->\s*KachiGlass\.plain\(""").containsMatchIn(surfaceOf), "WARN/ALERT qua plain")
        assertTrue("GroupTone.ACTIVE -> KachiGlass.apply(view, radius, SurfaceTone.ACTIVE, domain)" in surfaceOf)
        // Nền widget bên thứ ba (chữ trắng của RemoteViews đo trên nó) + đĩa ⇄.
        assertFalse("KachiChrome" in code(file("WorkspaceViewCards.kt")), "WIDGET_BACKING không mờ")
        assertTrue(
            "KachiGlass.apply(d, Sp.SWAP_DISC / 2, SurfaceTone.NEUTRAL, fade = false)" in code(file("SlotSwapButton.kt")),
            "đĩa ⇄ là nút — không mờ",
        )
        // Tone BẬT/LÕM không bao giờ mờ, kể cả khi chỗ gọi quên cờ.
        assertTrue(KachiChrome.fades(SurfaceTone.NEUTRAL) && KachiChrome.fades(SurfaceTone.WELL))
        assertFalse(KachiChrome.fades(SurfaceTone.ACTIVE) || KachiChrome.fades(SurfaceTone.SUNKEN))
        val glass = code(file("WallGlass.kt"))
        assertTrue("fade && KachiChrome.fades(tone)" in SourceRoots.body(glass, "fun apply("), "apply gộp điều kiện tone")
        // Chữ/icon không bao giờ mờ: chỉ alpha của Drawable nền, không `View.alpha`.
        val chrome = code(file("KachiChrome.kt"))
        assertEquals(1, Regex("""\.alpha\s*=""").findAll(chrome).count(), "KachiChrome chỉ đặt alpha cho đúng một thứ: Drawable nền trong fade()")
        assertFalse(Regex("""\bView\b""").containsMatchIn(chrome), "KachiChrome không chạm View (chữ/icon không bao giờ mờ)")
    }

    /**
     * 0 % trong suốt (độ đục 100) = đường cũ TỪNG BYTE: không `setAlpha` nào được gọi, lớp kính trên ảnh giữ đúng ba alpha
     * cũ (bộ giải lớp che trên bề mặt 100 %, trần/sàn mặc định), `apply` không báo đổi khi bật/tắt ảnh hay đổi màu.
     */
    @Test
    fun `o 0 phan tram trong suot khong cham gi`() {
        KachiChrome.apply(100)
        assertEquals(1.0, KachiChrome.fraction, 0.0)
        val chrome = code(file("KachiChrome.kt"))
        assertTrue("if (fraction < 1.0) d.alpha = ChromeOpacity.alphaByte(fraction)" in SourceRoots.body(chrome, "fun fade("),
            "fade ở 100 % không gọi setAlpha")
        val glass = code(file("WallGlass.kt"))
        // Cửa sổ kính: hệ số đi vào hàm dựng; ảnh mờ chỉ đổi alpha dưới 100 %; lớp che + nhuộm qua drawnAlpha (f ≥ 1 ⇒ y nguyên).
        assertTrue("if (fade < 1.0) alpha = ChromeOpacity.alphaByte(fade)" in glass, "lớp ảnh mờ: 100 % không chạm alpha")
        assertTrue("ChromeOpacity.drawnAlpha((alpha * 255).toInt(), fade)" in SourceRoots.body(glass, "private fun veilColor("),
            "lớp che: đúng byte cũ `(alpha × 255).toInt()` rồi mới nhân hệ số")
        assertTrue("ChromeOpacity.drawnAlpha(TINT_ALPHA, fade)" in glass, "lớp nhuộm cùng luật")
        val relocate = SourceRoots.body(glass, "fun relocate(")
        assertTrue("veilPaint.color = veilColor(GlassVeil.alphaFor(lum, veilOpaque, surfaces, inks))" in relocate,
            "bộ giải lớp che chạy trên bề mặt 100 % với sàn/trần MẶC ĐỊNH — đúng đường 2.86; không còn sàn theo vùng ảnh")
        val paint = SourceRoots.body(glass, "private fun paint(")
        assertTrue("fade = if (spec.fade) KachiChrome.fraction else 1.0," in paint, "nút (fade = false) không mờ cả lớp cửa sổ")
        assertTrue("surfaces = KachiTheme.surfacePair(spec.tone, overArtwork = true)," in paint, "bề mặt đưa bộ giải = bề mặt 100 %")
        order(paint, "val window = WallWindowDrawable(", "if (spec.fade) KachiChrome.fade(top)", "LayerDrawable(arrayOf(window, top))")
        assertTrue("override fun setAlpha(alpha: Int) = Unit" in glass, "setAlpha của cửa sổ vẫn không làm gì — hệ số tường minh")
        // Bật/tắt ảnh hay đổi bảng màu ở 100 % KHÔNG làm apply báo đổi (không dựng lại ô mỗi lượt ảnh trình chiếu).
        assertFalse(KachiChrome.apply(100))
        assertFalse(KachiChrome.apply(100))
    }

    /** `apply` báo đổi đúng khi độ đục đổi — `sync` chạy 1 Hz nên cùng đầu vào lần hai phải là `false`. */
    @Test
    fun `apply bao doi chi khi do duc doi`() {
        KachiChrome.apply(100)
        assertTrue(KachiChrome.apply(70))
        assertFalse(KachiChrome.apply(70), "cùng đầu vào ⇒ false (sync 1 Hz)")
        assertFalse(KachiChrome.apply(72), "72 kẹp về 70 ⇒ không đổi")
        assertTrue(KachiChrome.apply(0), "trong suốt 100 %")
        assertEquals(0.0, KachiChrome.fraction, 0.0)
        assertTrue(KachiChrome.apply(100), "về 0 % trong suốt")
    }

    @Test
    fun `doi do duc thi sync bao doi dung mot lan, bang mau giu nguyen`() {
        val night = ThemeMode.NIGHT
        val base = HomeUiState(themeMode = night)
        ThemeHost.sync(base, Calendar.getInstance(), null)
        assertTrue(!ThemeHost.sync(base, Calendar.getInstance(), null), "không đổi gì ⇒ false (sync chạy 1 Hz)")
        val palette = KachiTheme.palette
        val seventy = base.copy(colorChoice = ColorChoice(surfaceOpacity = 70))
        assertTrue(ThemeHost.sync(seventy, Calendar.getInstance(), null), "chỉ đổi độ đục ⇒ true (dựng lại nền tại chỗ)")
        assertEquals(0.7, KachiChrome.fraction, 1e-12)
        assertSame(palette, KachiTheme.palette, "độ đục không phải màu — bảng màu giữ CÙNG thực thể")
        assertTrue(!ThemeHost.sync(seventy, Calendar.getInstance(), null), "cùng giá trị lần hai ⇒ false")
        // Đổi màu VÀ độ đục cùng nhịp: cả hai đều được áp (không ngắn mạch).
        val both = base.copy(colorChoice = ColorChoice(AccentChoice.TEAL, surfaceOpacity = 40))
        assertTrue(ThemeHost.sync(both, Calendar.getInstance(), null))
        assertEquals(0.4, KachiChrome.fraction, 1e-12, "đổi bảng màu không được nuốt lượt đổi độ đục")
        // 2.88 — cuối thanh kéo: độ đục 0 tới tận KachiChrome (không bị kéo về bậc 40 % như 2.87).
        assertTrue(ThemeHost.sync(base.copy(colorChoice = ColorChoice(surfaceOpacity = 0)), Calendar.getInstance(), null))
        assertEquals(0.0, KachiChrome.fraction, 0.0)
        assertTrue(ThemeHost.sync(base, Calendar.getInstance(), null), "về mặc định ⇒ true")
        assertEquals(1.0, KachiChrome.fraction, 0.0)
        assertSame(KachiPalette.DARK, KachiTheme.palette)
    }

    /**
     * 2.88 — dây nối một chiều: thanh kéo ở Cài đặt → `deps.onColorChoice` (cùng intent màu) → … → ThemeHost (bài ở trên).
     * Khoá: hàng đứng NGAY sau Tông thẻ; là thanh kéo 0..[ChromeOpacity.POSITIONS] (bước [ChromeOpacity.STEP] = 5 %) đọc
     * vị trí từ độ đục đang lưu và ghi lại qua `ofPosition`; câu trợ năng mang phần trăm.
     */
    @Test
    fun `thanh keo do trong suot ngay sau tong the, buoc 5, noi toi onColorChoice`() {
        val sections = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt")
        val color = SourceRoots.body(sections, "private fun color(")
        val tone = color.indexOf("R.string.kachi_row_tone")
        val row = color.indexOf("R.string.kachi_row_bg_opacity")
        val note = color.indexOf("R.string.kachi_bg_opacity_note")
        assertTrue(tone in 0 until row, "hàng độ trong suốt đứng NGAY sau Tông thẻ (Hiển thị › Màu sắc)")
        assertTrue(row < note, "ghi chú đi sau hàng")
        val slider = color.substring(color.lastIndexOf("body.addView(", row), note)
        assertTrue("rows.sliderRow(" in slider, "2.88: thanh kéo, không còn năm chip")
        assertFalse("chipRow" in slider)
        assertTrue("positions = ChromeOpacity.POSITIONS" in slider, "số nấc = của :core, không con số thứ hai")
        assertTrue("current = ChromeOpacity.position(choice.surfaceOpacity)" in slider, "vị trí đọc từ độ đục ĐANG LƯU (theo hồ sơ)")
        assertTrue("choice.copy(surfaceOpacity = ChromeOpacity.ofPosition(pos))" in slider, "ghi lại qua phép nghịch của :core")
        assertTrue("deps.onColorChoice(choice)" in slider, "đi cùng intent màu ⇒ theo hồ sơ, lưu bền, ThemeHost")
        assertTrue("ChromeOpacity.transparencyPct(ChromeOpacity.ofPosition(pos))" in slider, "số hiện = độ TRONG SUỐT")
        assertTrue("R.string.kachi_bg_opacity_a11y" in slider, "trợ năng đọc phần trăm")
        // Bước 5 %: 21 vị trí, đầu = như cũ, cuối = trong hẳn — chính phép ánh xạ thanh kéo dùng.
        assertEquals((100 downTo 0 step 5).toList(), (0..ChromeOpacity.POSITIONS).map(ChromeOpacity::ofPosition))
        // Giữa nhãn Tông thẻ và hàng thanh kéo chỉ có ĐÚNG một `addView` — của chính hàng đó.
        assertEquals(1, Regex("""body\.addView\(""").findAll(color.substring(tone, row)).count(),
            "không chen hàng nào giữa Tông thẻ và Độ trong suốt nền")
        // Thanh kéo: áp khi THẢ (không theo từng nấc kéo — đường áp dựng lại thanh nút + ghi prefs), phím/trợ năng áp ngay.
        // Luật (kéo không áp · thả áp một lần · phím/trợ năng áp ngay · trùng chỗ cũ bỏ) có test HÀNH VI ở `:core`
        // `CommitOnReleaseTest`; ở đây ghim ba sự kiện của SeekBar đi đúng vào ba cửa của nó (soát Pass 10 [P3]).
        val rowSrc = code(file("SettingsRowsSlider.kt"))
        assertTrue("max = positions" in rowSrc && "keyProgressIncrement = 1" in rowSrc)
        assertTrue("val gate = CommitOnRelease(bar.progress)" in rowSrc, "mốc ban đầu = vị trí đang lưu")
        assertTrue("gate.onChange(progress, fromUser)?.let(onCommit)" in SourceRoots.body(rowSrc, "override fun onProgressChanged("))
        val start = SourceRoots.body(rowSrc, "override fun onStartTrackingTouch(")
        assertTrue("gate.onStart()" in start, "bắt đầu kéo phải báo cổng — thiếu nó là mỗi nấc kéo đều áp")
        assertFalse("onCommit" in start, "bắt đầu kéo chưa áp gì")
        assertTrue("gate.onStop(seekBar.progress)?.let(onCommit)" in SourceRoots.body(rowSrc, "override fun onStopTrackingTouch("), "thả tay ⇒ áp")
        assertEquals(3, Regex("""\bgate\.on""").findAll(rowSrc).count(), "không cửa nào khác gọi cổng")
        assertFalse(Regex("""\btracking\b""").containsMatchIn(rowSrc), "không giữ bản sao thứ hai của trạng thái kéo ở :app")
        // 2.93 SLIDER-RAIL-CONTRAST (đổi ghim có lý do): rãnh MUT ở disabledAlpha của theme ROM chỉ 1,6:1 / 1,81:1 [ĐO QA 04/10]
        // ⇒ drawable riêng, rãnh MUT2 ≥ 3:1 trên PANEL (Widget293ContrastTest đo trên bảng màu thật). Vẫn không FIELD_SUNKEN.
        assertTrue("progressDrawable = sliderRail(context)" in rowSrc && "floor = RAIL_FLOOR" in rowSrc,
            "rãnh riêng, sàn 3:1 (soát Pass 10 [P2]: FIELD_SUNKEN trên PANEL ≈ 1.03–1.11:1, ở vị trí 0 không thấy thanh kéo)")
        assertFalse("FIELD_SUNKEN" in SourceRoots.body(rowSrc, "internal fun sliderRail("), "rãnh không dùng FIELD_SUNKEN")
        assertTrue("bar.contentDescription = describe(text)" in rowSrc, "mô tả trợ năng đổi theo từng nấc")
        assertTrue("addView(bar, LinearLayout.LayoutParams(0, dpi(context, Sp.TOUCH), 1f))" in rowSrc, "đích chạm 48 dp")
    }

    /**
     * (7) Soát Pass 10 (2.88) — hai chỗ vẽ Canvas không có `Drawable` để [KachiChrome.fade]:
     *  • hai dải che đỉnh/đáy của hình nền (`WallView`) — nền SAU thanh trên/thanh nút; owner 04/10 *"trong suốt lên 100%"*
     *    ⇒ mờ CÙNG hệ số với hai thanh, 100 % không vẽ dải;
     *  • bốn thẻ bánh THƯỜNG của bảng lốp (`TyreBoardView`) — nền trung tính như ô con nhóm thường; thẻ CẢNH BÁO giữ đục.
     * Cả hai nhân ĐÚNG phép `drawnAlpha` (f = 1 ⇒ trả y alpha cũ ⇒ y byte 2.87) — phần số ở [ChromeOpacityExactContractTest]
     * (vai `barTop`, `card2`).
     */
    @Test
    fun `dai che hinh nen va the banh lop thuong mo cung he so`() {
        val wall = code(file("WallView.kt"))
        val size = SourceRoots.body(wall, "override fun onSizeChanged(")
        order(size, "bandFade = KachiChrome.fraction",
            "val bar = ColorMath.withAlpha(raw, ChromeOpacity.drawnAlpha(ColorMath.alpha(raw), bandFade))",
            "bandTop.shader = LinearGradient(0f, 0f, 0f, bandH, bar, Color.TRANSPARENT",
            "bandBottom.shader = LinearGradient(0f, h - bandH, 0f, h.toFloat(), Color.TRANSPARENT, bar")
        assertTrue("val raw = Color.parseColor(KachiTheme.BAR_TOP)" in size, "đầu đậm của dải vẫn là vai BAR_TOP (theo chủ đề)")
        assertTrue("if (bandH <= 0f || bandFade <= 0.0) return" in SourceRoots.body(wall, "private fun drawBands("),
            "100 % trong suốt ⇒ không vẽ dải (không tốn hai lượt tô cả bề ngang màn)")
        assertFalse("KachiChrome" in SourceRoots.body(wall, "override fun onDraw("), "hệ số chỉ đọc lúc dựng shader, không mỗi khung")
        // Đổi độ đục ⇒ `applyThemeInPlace` gọi `wall.restyle()` ⇒ dựng lại shader với hệ số mới.
        assertTrue("onSizeChanged(width, height, width, height)" in SourceRoots.body(wall, "fun restyle("))
        val render = code(file("KachiHomeRender.kt"))
        assertTrue("wall.restyle()" in SourceRoots.body(render, "internal fun KachiHomeActivity.applyThemeInPlace("))

        val tyre = SourceRoots.body(code(file("TyreBoardView.kt")), "private fun drawCell(")
        assertTrue("if (st.alert) ColorMath.mix(card2, col, SEMANTIC_MIX)" in tyre, "thẻ cảnh báo: màu nền là thông tin — không mờ")
        assertTrue("else ColorMath.withAlpha(card2, ChromeOpacity.drawnAlpha(ColorMath.alpha(card2), KachiChrome.fraction))" in tyre,
            "thẻ bánh thường mờ cùng hệ số, cùng phép")
        assertEquals(1, Regex("""KachiChrome\.fraction""").findAll(tyre).count(), "chỉ nhánh thường đọc hệ số")
    }

    /**
     * [soát 2.87 · P3] `KachiGlass.apply` MỜ theo bậc theo mặc định (`fade = true`) ⇒ một thẻ kính đặt vào Cài đặt/ngăn
     * kéo/hộp thoại sẽ mờ theo bậc mà bộ quét cũ (chỉ `KachiChrome.`) không thấy. Thử-phá bằng tệp giả.
     */
    @Test
    fun `bo quet ro ri bat KachiGlass apply mo mac dinh`() {
        val leak = mapOf("SettingsPreview.kt" to "fun f(v: View) { KachiGlass.apply(v, Sp.RADIUS_M, SurfaceTone.NEUTRAL, slotDomain(x)) }")
        assertEquals(listOf("SettingsPreview.kt"), chromeLeaks(leak), "thẻ kính mờ mặc định trong Cài đặt phải bị bắt")
        val ok = mapOf(
            "SettingsPreview.kt" to "fun f(v: View) { KachiGlass.apply(v, Sp.RADIUS_M, SurfaceTone.NEUTRAL, slotDomain(x), fade = false) }",
            "WidgetViews.kt" to "fun f(v: View) { KachiGlass.apply(v, Sp.RADIUS_M) }",   // màn chính — được mờ
        )
        assertEquals(emptyList<String>(), chromeLeaks(ok), "`fade = false` (nút, không phải nền) và màn chính thì được")
        assertEquals(listOf("AppDrawerX.kt"), chromeLeaks(mapOf("AppDrawerX.kt" to "val a = KachiChrome.fraction")))
    }

    // ── Hạ tầng ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Tệp NGOÀI màn chính (Cài đặt · ngăn kéo · lớp phủ giọng nói · bộ chọn · bộ sửa bố cục) chạm độ đục. `WallView` rời
     * danh sách ở 2.88 (soát Pass 10): hai dải che của nó là nền sau thanh trên/thanh nút ⇒ mờ cùng hệ số — bài (7).
     */
    private fun chromeLeaks(files: Map<String, String>): List<String> {
        val forbidden = Regex("""^(Settings.*|AppDrawer.*|VoiceOverlay|TopStripPicker|LayoutEditorPanel|VoiceTextConsole|ShellAccessCard|ShellChannelGate)\.kt$""")
        return files.filter { (name, src) ->
            forbidden.matches(name) && ("KachiChrome." in src || fadingGlass(src))
        }.keys.sorted()
    }

    /** Có lời gọi `KachiGlass.apply(` nào KHÔNG mang `fade = false` (đọc trọn đối số theo ngoặc, kể cả ngoặc lồng). */
    private fun fadingGlass(src: String): Boolean {
        var at = src.indexOf("KachiGlass.apply(")
        while (at >= 0) {
            var i = at + "KachiGlass.apply(".length
            var depth = 1
            while (i < src.length && depth > 0) { if (src[i] == '(') depth++ else if (src[i] == ')') depth--; i++ }
            if (!Regex("""\bfade\s*=\s*false\b""").containsMatchIn(src.substring(at, i))) return true
            at = src.indexOf("KachiGlass.apply(", i)
        }
        return false
    }

    private fun sources(): List<Path> = SourceRoots
        .moduleSourceRoots().first { it.toString().contains("app") }
        .let { root -> Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() } }
        .sortedBy { it.fileName.toString() }

    private fun file(name: String): Path = sources().first { it.fileName.toString() == name }

    /** Tệp (ngoài định nghĩa `KachiChrome.kt`) có [token] trong MÃ (đã bỏ chú thích). */
    private fun callers(token: String): List<String> = sources()
        .filter { it.fileName.toString() != "KachiChrome.kt" && token in code(it) }
        .map { it.fileName.toString() }.sorted()

    private fun count(name: String, token: String): Int = Regex(Regex.escape(token)).findAll(code(file(name))).count()

    private fun order(src: String, vararg parts: String) {
        var at = -1
        parts.forEach { p ->
            val i = src.indexOf(p, at + 1)
            assertTrue(i > at, "thứ tự sai / thiếu '$p' trong: ${src.take(800)}")
            at = i
        }
    }

    private fun code(f: Path): String = KotlinSource.stripComments(f.toFile().readText())
}
