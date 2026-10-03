package com.byd.clusternav.launcher

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
 * ═══ 2.87 · R-OP1/R-OP2 — BÀI CANH ĐỘ ĐỤC NỀN CHUNG: một chỗ ghi, đúng năm chỗ dựng, không lan ra ngoài màn chính ══
 *
 * Spec `docs/specs/kachi-287-look-and-keys.html` §3 R-OP2: *"hàm làm mờ chỉ được gọi ở 5 chỗ dựng của màn chính"*.
 * Mỗi bài một mắt xích: (1) [KachiChrome.apply] chỉ ở `ThemeHost` · (2) `fade`/`bar` đúng năm chỗ dựng, KHÔNG ở
 * Cài đặt/ngăn kéo/lớp phủ giọng nói/bộ chọn thanh trên/bộ sửa bố cục · (3) không trên dòng mang thông tin (ô BẬT,
 * WARN/ALERT, nền widget bên thứ ba, đĩa ⇄, dải che hình nền) · (4) 100 % là không-chạm · (5) đổi độ đục ⇒ `sync` báo
 * đổi đúng một lần · (6) hàng chọn ở Cài đặt nối dây tới `deps.onColorChoice`.
 * Phần số (đọc được trên mọi độ chói × mọi bậc) ở [ChromeOpacityContrastContractTest].
 */
class KachiChromeContractTest {

    @AfterEach
    fun reset() {
        KachiTheme.applyTheme(ThemeMode.NIGHT, 12, ColorChoice.DEFAULT, null)
        KachiChrome.apply(ChromeOpacity.DEFAULT, false)
    }

    @Test
    fun `KachiChrome apply chi duoc goi tu ThemeHost`() {
        assertEquals(listOf("ThemeHost.kt"), callers("KachiChrome.apply("), "một chỗ ghi hệ số — cùng chủ với bảng màu")
        val host = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ThemeHost.kt")
        val sync = SourceRoots.body(host, "fun sync(")
        assertTrue("KachiChrome.apply(state.colorChoice.surfaceOpacity, hasArt)" in sync, "đọc độ đục từ state (theo hồ sơ)")
        assertTrue("hasArt: Boolean = WallArtStore.current != null" in sync || "hasArt: Boolean = WallArtStore.current != null" in host,
            "bật/tắt ảnh nền phải tới KachiChrome (sàn có/không ảnh khác nhau)")
        assertTrue(
            Regex("""KachiTheme\.applyTheme\([^\n]*\)\s+or\s+KachiChrome\.apply\(""").containsMatchIn(sync),
            "phải là `or` KHÔNG ngắn mạch — `||` nuốt mất lượt đổi độ đục khi bảng màu cũng đổi cùng nhịp",
        )
    }

    @Test
    fun `fade va bar dung nam cho dung cua man chinh`() {
        assertEquals(listOf("ControlTileFactory.kt", "ReadTile.kt", "WallGlass.kt"), callers("KachiChrome.fade("))
        assertEquals(listOf("ControlDockView.kt", "KachiTopStrip.kt"), callers("KachiGlass.bar("))
        assertEquals(listOf("WallGlass.kt"), callers("KachiChrome.barAlpha("), "sàn đọc được của thanh chỉ tính ở KachiGlass")
        assertEquals(listOf("WallGlass.kt"), callers("KachiChrome.fraction"), "hệ số chỉ đọc ở cửa kính")
        assertEquals(listOf("ThemeHost.kt"), callers("KachiChrome.apply("), "một chỗ ghi")
        // Ô trên thanh nút biết mình ở thanh nút (sàn riêng) — [soát 2.87 · P3] chỗ gọi NÓI THẲNG (`onBar`), không suy từ
        // cỡ ô nữa: ô hành động trong ô NÉN dùng cỡ DOCK mà nằm trong khay ⇒ từng nhận nhầm sàn thanh nút (40 tổ hợp lệch).
        assertTrue("KachiChrome.fade(KachiTheme.surface(ctx, size.radius), onBar)" in code(file("ControlTileFactory.kt")))
        assertTrue("domain = pick.domain), onBar)" in code(file("ReadTile.kt")))
        assertFalse("TileSize.DOCK)" in code(file("ReadTile.kt")) || "size == TileSize.DOCK)" in code(file("ControlTileFactory.kt")))
        assertTrue("size = TileSize.DOCK, onBar = true)" in code(file("ControlDockView.kt")), "thanh nút: trên thanh")
        assertTrue("size = size, onBar = false)" in code(file("WidgetViews.kt")), "ô hành động trong ô (kể cả ô nén cỡ DOCK): trong khay")
        assertTrue("size = TileSize.GROUP, onBar = false," in code(file("GroupTileParts.kt")), "hàng nút ô nhóm: trong khay")
        assertTrue("readTileOf(ctx, size, onBar, pick)" in code(file("ControlTileFactory.kt")))
        assertEquals(2, count("ControlDockView.kt", "KachiTheme.BAR, tiles = true)"), "thanh nút CHỞ ô ⇒ sàn tính cả ô")
        // Mỗi chỗ đúng số lượt gọi: thanh trên + thanh nút dựng nền ở init/build VÀ restyle — thiếu restyle là đổi chủ
        // đề xong thanh rơi về 100 %.
        assertEquals(2, count("KachiTopStrip.kt", "KachiGlass.bar("), "thanh trên: build() + restyle()")
        assertEquals(2, count("ControlDockView.kt", "KachiGlass.bar("), "thanh nút: init + restyle()")
        val dock = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ControlDockView.kt")
        assertTrue("KachiGlass.bar(" in SourceRoots.body(dock, "init {") && "KachiGlass.bar(" in SourceRoots.body(dock, "fun restyle("))
        val strip = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiTopStrip.kt")
        assertTrue("KachiGlass.bar(stripRow," in SourceRoots.body(strip, "fun restyle("), "restyle thanh trên phải qua bar()")
    }

    @Test
    fun `khong lan ra Cai dat, ngan keo, lop phu giong noi, bo chon, bo sua bo cuc`() {
        val leaks = chromeLeaks(sources().associate { it.fileName.toString() to code(it) })
        assertEquals(emptyList<String>(), leaks, "R-OP2: chỉ màn chính mờ — Cài đặt/hộp thoại/ngăn kéo/dải che giữ nguyên")
        // Bảng màu KHÔNG nhân alpha: KachiTheme/KachiPalette* không biết độ đục.
        listOf("KachiTheme.kt", "KachiThemeDrawables.kt", "KachiPalette.kt", "KachiPaletteDerive.kt").forEach {
            assertFalse("KachiChrome" in code(file(it)) || "surfaceOpacity" in code(file(it)), "$it không được biết độ đục nền")
        }
    }

    @Test
    fun `khong mo nen mang thong tin`() {
        val tiles = code(file("ControlTileFactory.kt"))
        val applyBg = SourceRoots.body(tiles, "private fun applyBg(")
        assertTrue("KachiChrome.fade(KachiTheme.surface(ctx, size.radius), onBar)" in applyBg, "nhánh TẮT mờ")
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

    @Test
    fun `o 100 phan tram khong cham gi`() {
        listOf(false, true).forEach { art ->
            KachiChrome.apply(100, art)
            assertEquals(1.0, KachiChrome.fraction, 0.0)
            listOf(false, true).forEach { onBar ->
                listOf(false, true).forEach { a -> assertEquals(1.0, KachiChrome.surfaceFraction(onBar, a), 0.0) }
            }
            val bar = ColorMath.parse(KachiTheme.palette.barTop)
            assertEquals(255, KachiChrome.barAlpha(bar, doubleArrayOf(0.0, 1.0), tiles = false))
            assertEquals(255, KachiChrome.barAlpha(bar, null, tiles = true))
        }
        val chrome = code(file("KachiChrome.kt"))
        assertTrue("if (t < 1.0) d.alpha = ChromeStack.byteOf(t)" in SourceRoots.body(chrome, "fun fade("), "fade ở 100 % không gọi setAlpha")
        assertTrue("if (f >= 1.0) return 255" in SourceRoots.body(chrome, "internal fun barAlpha("))
        // Kính: f = 1 ⇒ không đổi bề mặt, sàn lớp che đúng MIN (đường cũ từng byte).
        val paint = SourceRoots.body(code(file("WallGlass.kt")), "private fun paint(")
        assertTrue("if (f < 1.0) IntArray(pair.size) { ChromeStack.faded(pair[it], f) } else pair" in paint)
        assertTrue("veilMin = GlassVeil.MIN * f," in paint)
        // Công thức lớp che mà bài quét ChromeOpacityContrastContractTest dựng lại: max(bộ giải chữ, sàn thứ trên khay);
        // sàn thêm chỉ cho KHAY và chỉ dưới 100 % (null ⇒ maxOf(…, 0.0) = đường cũ).
        val relocate = SourceRoots.body(code(file("WallGlass.kt")), "fun relocate(")
        // Soát vòng 2 [P3] — ĐỔI GHIM có lý do: bề mặt đưa bộ giải là bề mặt ĐANG VẼ của vùng (`surfaceAt` — thẻ mà lớp che 100 %
        // không cứu được thì giữ bề mặt đục hơn); không có `surfaceAt` (100 %, thẻ không khai gì) ⇒ `shown = surfaces` = đường cũ.
        order(relocate, "val s = surfaceAt?.invoke(lum)", "val shown = if (s == null) surfaces else IntArray(pair.size) { ChromeStack.faded(pair[it], s) }",
            "maxOf(GlassVeil.alphaFor(lum, veilOpaque, shown, inks, min = veilMin, max = veilMax), overlayFloor?.invoke(lum, shown) ?: 0.0)",
            "if (s != null) onSurface?.invoke(s)")
        assertTrue("veilMax = if (f < 1.0) 1.0 else GlassVeil.MAX," in paint, "100 % ⇒ trần cũ 90 %")
        // [soát 2.87 · P2] sàn thêm nay cho KHAY và cho thẻ có mực màu KHAI (`extraInks`) — vẫn chỉ dưới 100 %.
        assertTrue("val fits = f < 1.0 && (spec.tone == SurfaceTone.WELL || extra.isNotEmpty())" in paint)
        assertTrue("overlayFloor = if (fits) {" in paint && "surfaceAt = if (fits) {" in paint, "cả hai chỉ dưới 100 %, cùng điều kiện")
        assertTrue("KachiChrome.glassFloor(l, veil, pair, s, inks, extra, well, GlassVeil.MIN * f)" in paint)
        assertTrue("KachiChrome.glassSurface(l, veil, pair, inks, extra, well, GlassVeil.MIN * f)" in paint)
        assertTrue("onSurface = { s -> ChromeStack.byteOf(s).let { if (top.alpha != it) top.alpha = it } }" in paint,
            "hệ số bề mặt đưa bộ giải = alpha của lớp bề mặt THẬT")
        order(paint, "if (spec.fade) KachiChrome.fade(top)", "val window = WallWindowDrawable(", "LayerDrawable(arrayOf(window, top))")
        // Lớp che bộ giải bề mặt thử = CÙNG công thức relocate (max(bộ giải chữ trần 1.0, sàn thứ trên thẻ)).
        val veilFn = SourceRoots.body(chrome, "internal fun glassVeil(")
        assertTrue("GlassVeil.alphaFor(l, veil, shown, inks, min = veilMin, max = 1.0)" in veilFn &&
            "glassFloor(l, veil, pair, shown, inks, extraInks, well, veilMin)" in veilFn, veilFn)
        assertTrue("if (fraction >= 1.0) return 1.0" in SourceRoots.body(chrome, "internal fun glassSurface("), "100 % ⇒ không đổi một byte")
        assertTrue("val extra = spec.extraInks.filterNot { it in inks }.toIntArray()" in paint, "mực trung tính không khai hai lần")
        assertEquals(GlassVeil.MIN, GlassVeil.MIN * 1.0, 0.0)
        // Bật/tắt ảnh hay đổi bảng màu ở 100 % KHÔNG làm apply báo đổi (không dựng lại ô mỗi lượt ảnh trình chiếu).
        assertFalse(KachiChrome.apply(100, false))
        assertFalse(KachiChrome.apply(100, true))
    }

    /** Ở bậc < 100 %, bật/tắt ảnh nền phải báo đổi (sàn có/không ảnh khác nhau) — và cùng đầu vào lần hai ⇒ false. */
    @Test
    fun `bat tat anh nen o bac thap thi bao doi`() {
        KachiChrome.apply(100, false)
        assertTrue(KachiChrome.apply(70, false))
        assertFalse(KachiChrome.apply(70, false), "cùng đầu vào ⇒ false (sync 1 Hz)")
        assertTrue(KachiChrome.apply(70, true), "bật ảnh ⇒ ô phải dựng lại theo sàn có-ảnh")
        assertFalse(KachiChrome.apply(70, true))
        assertTrue(KachiChrome.apply(70, false), "tắt ảnh ⇒ dựng lại theo sàn không-ảnh")
        assertTrue(KachiChrome.apply(100, false), "về 100 %")
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
        assertTrue(!ThemeHost.sync(seventy, Calendar.getInstance(), null), "cùng bậc lần hai ⇒ false")
        // Đổi màu VÀ độ đục cùng nhịp: cả hai đều được áp (không ngắn mạch).
        val both = base.copy(colorChoice = ColorChoice(AccentChoice.TEAL, surfaceOpacity = 40))
        assertTrue(ThemeHost.sync(both, Calendar.getInstance(), null))
        assertEquals(0.4, KachiChrome.fraction, 1e-12, "đổi bảng màu không được nuốt lượt đổi độ đục")
        assertTrue(ThemeHost.sync(base, Calendar.getInstance(), null), "về mặc định ⇒ true")
        assertEquals(1.0, KachiChrome.fraction, 0.0)
        assertSame(KachiPalette.DARK, KachiTheme.palette)
    }

    /** Dây nối một chiều: chip ở Cài đặt → `deps.onColorChoice` (cùng intent màu) → … → ThemeHost (bài ở trên). */
    @Test
    fun `hang chon do duc ngay sau tong the, noi toi onColorChoice`() {
        val sections = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt")
        val color = SourceRoots.body(sections, "private fun color(")
        val tone = color.indexOf("R.string.kachi_row_tone")
        val row = color.indexOf("R.string.kachi_row_bg_opacity")
        val note = color.indexOf("R.string.kachi_bg_opacity_note")
        assertTrue(tone in 0 until row, "hàng độ đục đứng NGAY sau Tông thẻ (Hiển thị › Màu sắc)")
        assertTrue(row < note, "ghi chú đi sau hàng")
        val chip = color.substring(row, note)
        assertTrue("ChromeOpacity.STEPS" in chip, "chip = đúng năm bậc của :core, không danh sách thứ hai")
        assertTrue("choice.surfaceOpacity" in chip && "copy(surfaceOpacity = " in chip, "đọc + ghi trường độ đục")
        assertTrue("deps.onColorChoice(choice)" in chip, "đi cùng intent màu ⇒ theo hồ sơ, lưu bền, ThemeHost")
        // Giữa nhãn Tông thẻ và nhãn Độ đục nền chỉ có ĐÚNG một `addView` — của chính hàng độ đục.
        assertEquals(1, Regex("""body\.addView\(""").findAll(color.substring(tone, row)).count(),
            "không chen hàng nào giữa Tông thẻ và Độ đục nền")
    }

    /**
     * [soát 2.87 · P2] Thẻ ô nén/ô bảng vẽ số to bằng MÀU (w_board: ACCENT thuần) lên kính mờ — bộ giải lớp che chỉ biết
     * mut/mut2/ink ⇒ ACCENT tụt 4.5 → 3.86:1. Thẻ phải KHAI màu nó vẽ (lúc dựng + khi màu đổi theo nhịp), và danh sách
     * khai cho bài quét ([ChromeInks.CARD]) phải phủ MỌI màu mà chỗ dựng truyền vào.
     */
    @Test
    fun `the o nen khai mau chu cho lop che, danh sach khai phu moi mau`() {
        val tel = code(file("WidgetTelemetry.kt"))
        val mini = SourceRoots.body(tel, "internal class MiniCard(")
        assertTrue("KachiGlass.apply(this, Sp.RADIUS_M, domain = domain, extraInks = intArrayOf(c(color)))" in mini, "ô nén khai màu chữ")
        assertTrue("v.color?.let { KachiGlass.addInk(root, c(it)) }" in SourceRoots.body(mini, "fun set("), "màu đổi theo nhịp cũng khai")
        val cell = SourceRoots.body(tel, "internal class BoardCell(")
        assertTrue("KachiGlass.apply(this, Sp.RADIUS_M, extraInks = intArrayOf(c(color)))" in cell, "ô bảng khai màu chữ")
        assertTrue("v.color?.let { KachiGlass.addInk(root, c(it)) }" in SourceRoots.body(cell, "fun set("))
        val neutral = setOf("INK", "MUT", "MUT2")
        val used = listOf("WidgetViews.kt", "WidgetTelemetry.kt").flatMap { f ->
            code(file(f)).lines().filter { l -> listOf("miniCard(", "BoardCell(", "MiniValue(", "val tone =").any { it in l } }
                .flatMap { l -> Regex("""KachiTheme\.([A-Z_0-9]+)""").findAll(l).map { it.groupValues[1] } }
        }.toSet()
        assertTrue("ACCENT" in used && "GREEN" in used, "bộ quét nguồn hỏng? thấy $used")
        assertEquals(emptySet<String>(), used - neutral - ChromeInks.CARD.keys, "màu chữ thẻ chưa khai trong ChromeInks.CARD")
        // Chữ màu vẽ THẲNG lên khay (widget to, không qua thẻ khai mực): phải nằm trong ChromeInks.WELL — bài quét đo đúng chúng.
        val onWell = code(file("WidgetViews.kt")).lines().filter { l -> "tv(ctx," in l || "setTextColor(" in l }
            .flatMap { l -> Regex("""KachiTheme\.([A-Z_0-9]+)""").findAll(l).map { it.groupValues[1] } }.toSet() - neutral
        assertTrue("GREEN" in onWell, "bộ quét nguồn hỏng? thấy $onWell")
        assertEquals(emptySet<String>(), onWell - ChromeInks.WELL, "chữ màu thẳng trên khay chưa khai trong ChromeInks.WELL")
        assertTrue(ChromeInks.CARD.keys.containsAll(ChromeInks.WELL))
    }

    /**
     * [soát 2.87 · P3] `KachiGlass.apply` MỜ theo bậc theo mặc định (`fade = true`) ⇒ một thẻ kính đặt vào Cài đặt/ngăn
     * kéo/hộp thoại sẽ mờ theo bậc mà bộ quét cũ (chỉ `KachiChrome.` + `KachiGlass.bar(`) không thấy. Thử-phá bằng tệp giả.
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

    /** Tệp NGOÀI màn chính (Cài đặt · ngăn kéo · lớp phủ giọng nói · bộ chọn · bộ sửa bố cục · dải che) chạm độ đục. */
    private fun chromeLeaks(files: Map<String, String>): List<String> {
        val forbidden = Regex("""^(Settings.*|AppDrawer.*|VoiceOverlay|TopStripPicker|LayoutEditorPanel|VoiceTextConsole|ShellAccessCard|ShellChannelGate|WallView)\.kt$""")
        return files.filter { (name, src) ->
            forbidden.matches(name) && (listOf("KachiChrome.", "KachiGlass.bar(").any { it in src } || fadingGlass(src))
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

    private fun code(f: Path): String = f.toFile().readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        .lines().joinToString("\n") { it.substringBefore("//") }
}
