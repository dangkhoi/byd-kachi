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
        // Ô trên thanh nút biết mình ở thanh nút (sàn riêng khi có ảnh nền) — bằng cỡ ô, không bằng tên chỗ gọi.
        assertTrue("KachiChrome.fade(KachiTheme.surface(ctx, size.radius), size == TileSize.DOCK)" in code(file("ControlTileFactory.kt")))
        assertTrue("size == TileSize.DOCK)" in code(file("ReadTile.kt")))
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
        val forbidden = Regex("""^(Settings.*|AppDrawer.*|VoiceOverlay|TopStripPicker|LayoutEditorPanel|VoiceTextConsole|ShellAccessCard|ShellChannelGate|WallView)\.kt$""")
        val leaks = sources().filter { forbidden.matches(it.fileName.toString()) }
            .filter { f -> listOf("KachiChrome.", "KachiGlass.bar(").any { it in code(f) } }
            .map { it.fileName.toString() }
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
        assertTrue("KachiChrome.fade(KachiTheme.surface(ctx, size.radius), size == TileSize.DOCK)" in applyBg, "nhánh TẮT mờ")
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
        assertTrue("maxOf(GlassVeil.alphaFor(lum, veilOpaque, surfaces, inks, min = veilMin, max = veilMax), overlayFloor?.invoke(lum) ?: 0.0)" in relocate)
        assertTrue("veilMax = if (f < 1.0) 1.0 else GlassVeil.MAX," in paint, "100 % ⇒ trần cũ 90 %")
        assertTrue("overlayFloor = if (f < 1.0 && spec.tone == SurfaceTone.WELL) {" in paint)
        assertTrue("KachiChrome.wellOverlayFloor(l, veil, pair, shown, inks, GlassVeil.MIN * f)" in paint)
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

    // ── Hạ tầng ──────────────────────────────────────────────────────────────────────────────────

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

    private fun code(f: Path): String = f.toFile().readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        .lines().joinToString("\n") { it.substringBefore("//") }
}
