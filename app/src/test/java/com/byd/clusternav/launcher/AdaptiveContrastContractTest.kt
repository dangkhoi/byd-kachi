package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.ContrastBackdrop.Layer
import com.byd.clusternav.testsupport.SourceRoots
import com.byd.clusternav.testsupport.Wcag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.5 R29 pha 2 (T29-8/T29-9) — mực thích ứng trên bảng màu SỐNG ([KachiPalette] + mọi bảng suy theo lựa chọn màu), chồng
 * lớp dựng bằng CHÍNH hàm mã vẽ dùng ([InkModel]), và hợp đồng nguồn: mọi chỗ đã nối (spec §5.1 T29-2…7) đi qua
 * [AdaptiveInk] ⇒ [ContrastInk.resolve].
 *
 * Owner 14/10: *"luôn luôn nhìn thấy được"*. Tính chất: trên MỌI nền ghép (ảnh trần · thẻ kính o20/o70/o100 · dải che thanh
 * trên × làm tối 0/45/90 × độ chói 0..1 × độ rộng hộp), mọi vai, cả hai chủ đề ⇒ mực + lớp đỡ trả về ĐẠT sàn, kiểm lại độc lập
 * bằng [Wcag] trên chuỗi hex (không tin số bộ giải tự khai).
 */
class AdaptiveContrastContractTest {

    private val dir = "src/main/java/com/byd/clusternav/launcher/"
    private fun c(hex: String) = ColorMath.parse(hex)
    private fun drawn(color: Int, f: Double) = ColorMath.withAlpha(color, ChromeOpacity.drawnAlpha(ColorMath.alpha(color), f))

    private fun verified(r: Resolution, b: ContrastBackdrop): Double {
        val full = if (r.reinforced) b.with(Layer.Flat(r.haloColor())) else b
        return (0..100).minOf { Wcag.ratio(ColorMath.hex(r.ink), ColorMath.hex(full.groundAt(it / 100.0))) }
    }

    /** Nền ghép tiêu biểu: ảnh trần · thẻ kính (lớp che do [GlassVeil] chọn như `WallWindowDrawable`) · dải che đỉnh màn. */
    private fun backdrops(p: KachiPalette, step: Double = 0.1): Sequence<Pair<String, ContrastBackdrop>> = sequence {
        val bg = ColorMath.withAlpha(c(p.bg), 255)
        for (dim in intArrayOf(0, 45, 90)) for (width in doubleArrayOf(0.0, 0.3, 1.0)) {
            var l = 0.0
            while (l <= 1.0 - width + 1e-9) {
                val base = ToneBox.gray(l, minOf(1.0, l + width))
                val wall = listOfNotNull(InkModel.scrim(p, dim))
                yield("ảnh trần dim=$dim $base" to ContrastStack.compose(base, listOf(listOf(wall))))
                // Dải che đỉnh màn (thanh trên) ở độ đục chung 0.2/1.0 — mặt thanh BAR_TOP × f lên trên.
                for (f in doubleArrayOf(0.2, 1.0)) {
                    val bands = InkModel.bandVariants(p, f, 8, 60, 1080, WallView.BAND_FRACTION)
                    val bar = ContrastStack.surfaceVariants(intArrayOf(drawn(c(p.barTop), f)))
                    yield("thanh trên o${(f * 100).toInt()} dim=$dim $base" to ContrastStack.compose(base, listOf(listOf(wall), bands, bar)))
                }
                val dimmed = ContrastBackdrop(base, wall).ground()
                val glass = ToneBox(dimmed.at(0.25), dimmed.at(0.75))
                for (f in doubleArrayOf(0.2, 0.7, 1.0)) {
                    val surfaces = intArrayOf(drawn(c(p.surfFromOverArt), f), drawn(c(p.surfToOverArt), f))
                    val veilA = GlassVeil.alphaFor(Luma.of(dimmed.at(0.5)), bg, intArrayOf(c(p.surfFromOverArt), c(p.surfToOverArt)),
                        intArrayOf(c(p.mut), c(p.mut2), c(p.ink)))
                    val veil = ColorMath.withAlpha(bg, ChromeOpacity.drawnAlpha((veilA * 255).toInt(), f))
                    val card = ContrastStack.glassVariants(glass, f, if (f < 1.0) ChromeOpacity.alphaByte(f) / 255.0 else 1.0, veil, 0, surfaces, 0)
                    yield("thẻ o${(f * 100).toInt()} dim=$dim $base" to ContrastStack.compose(base, listOf(listOf(wall), card)))
                }
                l += step
            }
        }
    }

    private fun livePalettes(): List<KachiPalette> = listOf(KachiPalette.DARK, KachiPalette.LIGHT).flatMap { base ->
        val dark = base === KachiPalette.DARK
        listOf(base) + AccentChoice.entries.map { base.derive(ColorChoice(accent = it), null, dark) }
    }

    @Test
    fun `bang mau song - moi vai moi nen ghep deu dat san hoac co lop do, kiem doc lap`() {
        var cases = 0; var halos = 0; var swaps = 0
        for (p in listOf(KachiPalette.DARK, KachiPalette.LIGHT)) for ((label, b) in backdrops(p)) for (key in InkKey.entries) {
            for ((icon, large) in listOf(false to false, false to true, true to false)) {
                val role = InkModel.role(key, icon, large)
                val r = ContrastInk.resolve(role, b, InkModel.candidates(key, p))
                val v = verified(r, b)
                assertTrue(r.ok && v >= role.floor - 1e-6, "${p.bg} $label $key ${role.name}: ${r.describe()} đo độc lập ${Wcag.fmt(v)}")
                cases++; if (r.reinforced) halos++; if (r.swapped) swaps++
            }
        }
        println("[ĐO] R29 pha 2 bảng sống: $cases ca · đổi mực $swaps · lớp đỡ $halos")
        assertTrue(cases > 10_000, "quét quá thưa: $cases")
    }

    @Test
    fun `bang suy theo mau nhan - vai nhan va muc chinh van dat`() {
        for (p in livePalettes()) for ((label, b) in backdrops(p, step = 0.25)) for (key in listOf(InkKey.ACCENT_INK, InkKey.INK, InkKey.MUT)) {
            val role = InkModel.role(key, icon = false, large = false)
            val r = ContrastInk.resolve(role, b, InkModel.candidates(key, p))
            assertTrue(r.ok && verified(r, b) >= role.floor - 1e-6, "${p.accentInk} $label $key ${r.describe()}")
        }
    }

    @Test
    fun `ca that GroupCellGrid - MUT tren the sang 186 189 197 khong con 3,7 - doi muc hoac lop do`() {
        val p = KachiPalette.LIGHT
        val b = ContrastBackdrop(ToneBox.span(ColorMath.argb(255, 186, 189, 197)))
        assertTrue(ColorMath.ratio(c(p.mut), b.groundAt(0.0)) < 4.5, "tiền đề: MUT trên (186,189,197) dưới sàn")
        val r = ContrastInk.resolve(InkRole.SECONDARY_TEXT, b, InkModel.candidates(InkKey.MUT, p))
        assertTrue(r.ok && (r.swapped || r.reinforced) && verified(r, b) >= 4.5, r.describe())
    }

    @Test
    fun `vai tu ma mau - moi vai cua ca hai bang tra ve dung loai chu`() {
        for (p in livePalettes()) for (k in InkKey.entries) {
            val back = InkKey.of(k.pick(p), p)
            assertTrue(back != null, "${k.name} không tra lại được")
            assertEquals(InkModel.role(k, false, false), InkModel.role(back!!, false, false), "${k.name} trùng mã với ${back.name} khác vai")
        }
        assertEquals(null, InkKey.of("#12345678", KachiPalette.DARK), "mã lạ không được gán vai")
    }

    @Test
    fun `ung vien - lop do duc, it nhat mot ung vien do duoc moi vai`() {
        for (p in livePalettes()) for (k in InkKey.entries) {
            val cs = InkModel.candidates(k, p)
            assertEquals(ColorMath.withAlpha(c(k.pick(p)), 255), cs.first().ink, "${k.name}: ứng viên đầu phải là mực của chủ đề")
            cs.forEach { assertEquals(255, ColorMath.alpha(it.halo)); assertEquals(255, ColorMath.alpha(it.ink)) }
            for (role in listOf(InkModel.role(k, false, false), InkModel.role(k, true, false)))
                assertTrue(cs.any { ColorMath.ratio(it.ink, it.halo) >= role.floor }, "${k.name}/${role.name}: không ứng viên nào đỡ được")
        }
    }

    /** T29-8 — hộp thoại: mặt ĐỤC tĩnh (`surfFrom`→`surfTo`) ⇒ mực của chủ đề đạt luôn, không đổi mực, không lớp đỡ. */
    @Test
    fun `hop thoai tinh - muc chu de dat tren hai dau mat hop, moi vai dung o day`() {
        for (p in livePalettes()) {
            val b = ContrastBackdrop(ToneBox.span(ColorMath.withAlpha(c(p.surfFrom), 255), ColorMath.withAlpha(c(p.surfTo), 255)))
            for (k in listOf(InkKey.INK, InkKey.INK2, InkKey.MUT, InkKey.ACCENT_INK)) for (icon in listOf(false, true)) {
                val r = ContrastInk.resolve(InkModel.role(k, icon, false), b, InkModel.candidates(k, p))
                assertTrue(r.ok && !r.swapped && !r.reinforced, "${p.bg} hộp thoại $k icon=$icon: ${r.describe()}")
            }
        }
    }

    @Test
    fun `nhom bien the - mot to hop giu chong chinh xac, nhieu to hop hop lai bao tho`() {
        val base = ToneBox.gray(0.2, 0.6)
        val one = ContrastStack.compose(base, listOf(listOf(listOf(Layer.Flat(0x80000000.toInt())))))
        assertEquals(1, one.layers.size)
        val two = ContrastStack.compose(base, listOf(listOf(listOf(Layer.Flat(0x80000000.toInt())), listOf(Layer.Flat(0x80ffffff.toInt())))))
        val dark = ContrastBackdrop(base, listOf(Layer.Flat(0x80000000.toInt()))).ground()
        val light = ContrastBackdrop(base, listOf(Layer.Flat(0x80ffffff.toInt()))).ground()
        assertEquals(dark.union(light).lo, two.base.lo); assertEquals(dark.union(light).hi, two.base.hi)
        // Dải che: vùng nằm ngoài dải ⇒ đúng một biến thể rỗng; vùng chạm dải ⇒ có biến thể mang màu dải.
        assertEquals(listOf(emptyList<Layer>()), InkModel.bandVariants(KachiPalette.DARK, 1.0, 400, 600, 1080, WallView.BAND_FRACTION))
        assertTrue(InkModel.bandVariants(KachiPalette.DARK, 1.0, 0, 60, 1080, WallView.BAND_FRACTION).size > 1)
    }

    // ═══ Pha 2b — T29-6 lớp phủ xe 3D · nút đĩa (spec §5.1 T29-6, diagnostics `widget-visual-301` §12) ═══════════════════════

    /** Màu sơn tiêu biểu (trắng · đen · xám · đỏ · lam đậm — dải `car_body_color.csv`). */
    private val paints = listOf(0xffd1d1d1, 0xff141414, 0xff767e8a, 0xff8a1a1a, 0xff1f3f9a).map { it.toInt() }
    private val shadow = Car3dBackdropView.SHADOW_CORE

    /**
     * Nền dưới một lớp phủ 3D: thẻ phía sau (`backdrops`) × ảnh nền xe (không · tối · sáng · cả dải; phủ trọn hay không) × màu sơn
     * (không ⇒ vùng ngoài khung xe) × bóng đất — đúng phép `Car3dInk.groups` (= [InkModel.car3dVariants] ghép sau lớp của thẻ).
     */
    private fun car3d(p: KachiPalette): Sequence<Pair<String, ContrastBackdrop>> = sequence {
        for ((label, card) in backdrops(p, step = 0.5)) for (photo in listOf(null, ToneBox.gray(0.0, 0.15), ToneBox.gray(0.55, 1.0), ToneBox.gray(0.0, 1.0)))
            for (covers in listOf(true, false)) for (paint in listOf<Int?>(null) + paints) for (sh in intArrayOf(0, shadow)) {
                if (photo == null && !covers) continue
                val v = InkModel.car3dVariants(photo, covers, paint?.let(InkModel::paintSpan), sh)
                yield("$label ảnh=$photo phủ=$covers sơn=${paint?.let(ColorMath::hex)} bóng=${sh != 0}" to ContrastStack.compose(card.base, listOf(listOf(card.layers), v)))
            }
    }

    /** Tỉ số tệ nhất của nét [ink] (có thể có alpha — trộn trước) trên nền [b], kiểm độc lập bằng [Wcag] trên chuỗi hex. */
    private fun worstOn(ink: Int, b: ContrastBackdrop): Double =
        (0..100).minOf { val g = ColorMath.hex(b.groundAt(it / 100.0)); Wcag.ratio(ColorMath.hex(ink), g) }

    @Test
    fun `nen 3D bao tho - diem anh that ngau nhien luon nam trong hop ghep`() {
        val rnd = java.util.Random(29)
        fun ch() = rnd.nextInt(256)
        repeat(4000) {
            val card = ColorMath.argb(255, ch(), ch(), ch())
            val a = ColorMath.argb(255, ch(), ch(), ch()); val b = ColorMath.argb(255, ch(), ch(), ch())
            val photo = ToneBox.span(a, b)
            val paint = ColorMath.argb(255, ch(), ch(), ch())
            val covers = rnd.nextBoolean()
            val g = ContrastStack.compose(ToneBox.span(card), listOf(InkModel.car3dVariants(photo, covers, InkModel.paintSpan(paint), shadow))).ground()
            // Điểm ảnh thật: (ảnh trong hộp | thẻ khi ảnh không phủ) → bóng alpha bất kỳ ≤ lõi → (có thể) thân xe tô trong dải bóng đổ.
            fun inBox(lo: Int, hi: Int): Int {
                fun pick(sh: Int): Int { val l = (lo shr sh) and 0xff; val h = (hi shr sh) and 0xff; return minOf(l, h) + rnd.nextInt(kotlin.math.abs(h - l) + 1) }
                return ColorMath.argb(255, pick(16), pick(8), pick(0))
            }
            var px = if (covers || rnd.nextBoolean()) inBox(photo.lo, photo.hi) else card
            px = ColorMath.over(ColorMath.withAlpha(shadow, rnd.nextInt(ColorMath.alpha(shadow) + 1)), px)
            if (rnd.nextBoolean()) { val s = InkModel.paintSpan(paint); px = inBox(s.lo, s.hi) }
            val y = Luma.of(px)
            assertTrue(y >= Luma.of(g.lo) - 1e-3 && y <= Luma.of(g.hi) + 1e-3, "điểm ${ColorMath.hex(px)} ngoài hộp ${ColorMath.hex(g.lo)}…${ColorMath.hex(g.hi)}")
        }
        val s = InkModel.paintSpan(paints[0])
        assertTrue(Luma.of(s.lo) < Luma.of(paints[0]) && Luma.of(s.hi) > Luma.of(paints[0]), "dải sơn phải gồm cả mặt khuất lẫn vệt sáng")
    }

    @Test
    fun `nut dia do theo nen - glyph nghi 4,5 bat 3 moi nen, Toi tren nen toi giu nguyen byte`() {
        var n = 0; var other = 0; var deep = 0
        for (p in livePalettes()) for ((label, b) in backdrops(p, step = 0.25) + car3d(p).filterIndexed { i, _ -> i % 3 == 0 }) {
            val k = KachiButtonInks.of(p, b)
            val rest = worstOn(c(k.glyph), b.with(Layer.Flat(k.rest)))
            val on = worstOn(c(k.glyphOn), b.with(Layer.Flat(k.on)))
            assertTrue(rest >= KachiButtonInks.GLYPH_FLOOR - 1e-6 && on >= KachiButtonInks.GLYPH_ON_FLOOR - 1e-6,
                "${p.bg} $label: glyph nghỉ ${Wcag.fmt(rest)} · bật ${Wcag.fmt(on)} (đĩa ${ColorMath.hex(k.rest)})")
            n++; if (k != KachiButtonInks.of(p)) other++; if (ColorMath.alpha(k.rest) > KachiButtonInks.LIGHT_REST_ALPHA) deep++
        }
        // Bảng Tối trên chính mặt thẻ/ô bảng Tối (nền `WindowControlIconContractTest` đo) ⇒ đúng từng byte 3.1.4 (CLAUDE.md §6).
        val d = KachiPalette.DARK
        for (g in listOf(d.card, d.tile, d.bg)) assertEquals(KachiButtonInks.of(d), KachiButtonInks.of(d, ContrastBackdrop(ToneBox.span(c(g)))), "Tối trên $g")
        println("[ĐO] R29 T29-6 nút đĩa: $n nền · đổi công thức $other · đĩa đậm thêm $deep")
        assertTrue(other > 0, "quét không chạm ca nào cần đổi công thức — nền quá hẹp")
    }

    @Test
    fun `cham den nut tren xe 3D - net dat 3 tren nen do cong lop do, kiem doc lap theo dung thu tu ve`() {
        var cases = 0; var halos = 0
        for ((p, night) in listOf(KachiPalette.DARK to true, KachiPalette.LIGHT to false)) {
            val k = StateDotPainter.Inks.of(p, night)
            val warn = c(p.amber)
            fun ContrastBackdrop.over(x: Int) = with(Layer.Flat(x))
            fun ContrastBackdrop.halo(h: Int) = if (h != 0) over(h) else this
            for ((label, b) in car3d(p).filterIndexed { i, _ -> i % 2 == 0 }) for (shape in StateDotPainter.Inks.Shape.entries) {
                val h = k.halos(b, shape, warn, p) { line -> throw AssertionError("dưới sàn ($label): $line") }
                val checks: List<Triple<String, Int, List<ContrastBackdrop>>> = when (shape) {
                    StateDotPainter.Inks.Shape.DOT -> listOf(
                        Triple("vòng đóng", k.closed, listOf(b.over(k.backing).halo(h[CarDots.Kind.CLOSED.ordinal]))),
                        Triple("vòng chưa đọc", k.unknown, listOf(b.over(k.backing).halo(h[CarDots.Kind.UNKNOWN.ordinal]))),
                        Triple("quạt/vòng nhấn", k.accent, listOf(b.over(k.backing).halo(h[StateDotPainter.Inks.TONE]))),
                        Triple("chấm mở", k.accent, listOf(b.over(k.halo).over(k.backing).halo(h[CarDots.Kind.OPEN.ordinal]))),
                        Triple("vòng cảnh báo", warn, listOf(b.over(k.backing).halo(h[StateDotPainter.Inks.WARN]))),
                        Triple("chấm cảnh báo", warn, listOf(b.over(ColorMath.withAlpha(warn, StateDotPainter.HALO_ALPHA)).over(k.backing).halo(h[StateDotPainter.Inks.WARN_OPEN]))),
                    )
                    StateDotPainter.Inks.Shape.BUTTON -> listOf(
                        Triple("nút tắt", k.drawnGlyph(k.closed), listOf(b.over(k.shadow).over(k.tonal).halo(h[CarDots.Kind.CLOSED.ordinal]))),
                        Triple("nút chưa đọc", k.drawnGlyph(k.unknown), listOf(b.over(k.shadow).over(k.tonal).halo(h[CarDots.Kind.UNKNOWN.ordinal]))),
                        Triple("nút bật", k.drawnGlyph(k.accent), listOf(b.over(ColorMath.withAlpha(k.accent, StateDotPainter.GLOW_OUTER_ALPHA)).over(k.halo)
                            .over(k.shadow).over(k.tonal).halo(h[CarDots.Kind.OPEN.ordinal]))),
                    )
                    StateDotPainter.Inks.Shape.LAMP -> {
                        val glow = ColorMath.withAlpha(k.lampTone(null), StateDotPainter.LAMP_GLOW_ALPHA)
                        listOf(
                            Triple("đèn tắt", k.drawnGlyph(k.lampOff), listOf(b, b.over(k.lampEdge)).map { it.halo(h[CarDots.Kind.CLOSED.ordinal]) }),
                            Triple("đèn chưa đọc", k.drawnGlyph(k.lampUnknown), listOf(b, b.over(k.lampEdge)).map { it.halo(h[CarDots.Kind.UNKNOWN.ordinal]) }),
                            Triple("đèn bật", k.drawnGlyph(k.lampTone(null)), listOf(b.over(glow), b.over(glow).over(k.lampEdge)).map { it.halo(h[CarDots.Kind.OPEN.ordinal]) }),
                        )
                    }
                }
                for ((what, ink, grounds) in checks) for (g in grounds) {
                    val v = worstOn(ink, g)
                    assertTrue(v >= InkRole.ICON.floor - 1e-6, "${p.bg} $label ${shape.name} $what: ${Wcag.fmt(v)}")
                    cases++
                }
                halos += h.count { it != 0 }
            }
        }
        println("[ĐO] R29 T29-6 chấm/nút/đèn 3D: $cases phép kiểm · lớp đỡ $halos")
        assertTrue(halos > 0, "quét không chạm ca cần lớp đỡ — nền quá hẹp")
    }

    @Test
    fun `do theo vi tri - o lop do dung phep chon muc cua draw`() {
        fun dot(k: CarDots.Kind, warn: Boolean = false) = CarDots.Dot(k, 0.5f, warn)
        assertEquals(CarDots.Kind.CLOSED.ordinal, StateDotPainter.Inks.dotSlot(dot(CarDots.Kind.CLOSED), lit = false))
        assertEquals(StateDotPainter.Inks.TONE, StateDotPainter.Inks.dotSlot(dot(CarDots.Kind.CLOSED), lit = true))
        assertEquals(StateDotPainter.Inks.WARN, StateDotPainter.Inks.dotSlot(dot(CarDots.Kind.PARTIAL, warn = true), lit = false))
        assertEquals(StateDotPainter.Inks.WARN_OPEN, StateDotPainter.Inks.dotSlot(dot(CarDots.Kind.OPEN, warn = true), lit = false))
        assertEquals(CarDots.Kind.OPEN.ordinal, StateDotPainter.Inks.dotSlot(dot(CarDots.Kind.OPEN), lit = true))
    }

    // ═══ Hợp đồng nguồn: chỗ đã nối gọi bộ giải (CLAUDE.md §8 — hàm mới phải có call site) ════════════════════════════════

    private fun code(f: String) = SourceRoots.codeOf(dir + f)

    @Test
    fun `bo giai - mot cua ContrastInk resolve, ung vien tu bang song, duoi san log qua LogLineThrottle`() {
        val src = code("AdaptiveInk.kt")
        val solve = SourceRoots.body(src, "private fun solve(")
        assertTrue(solve.contains("ContrastInk.resolve(") && solve.contains("InkModel.candidates(mark.key, KachiTheme.palette)"))
        assertTrue(solve.contains("if (!it.ok) logW("))
        assertTrue(SourceRoots.body(src, "private fun logW(").contains("throttle.suppressedBefore("))
        assertTrue(src.contains("private val throttle = LogLineThrottle()"))
        assertTrue(SourceRoots.body(src, "private fun backdropOf(").contains("art.backdropAt("))
        // Nhịp 1 Hz chỉ TRA kết quả đã lưu; onDraw không có ở đây.
        assertFalse(src.contains("onDraw"))
        val perf = code("KachiHomeActivity.kt")
        assertTrue(perf.contains("KachiPerf.dueLine(") && perf.contains("ContrastLedger.snapshot()"), "ContrastLedger phải in cùng dòng KachiPerf")
    }

    @Test
    fun `cho da noi - moi cho to chu icon trong pham vi T29 di qua AdaptiveInk`() {
        // T29-2/3 — WallArt nấu hai lưới + đệm + nền thật.
        val art = code("WallArt.kt")
        val build = SourceRoots.body(art, "fun build(cache: ArtCache")
        assertTrue(build.contains("sharpField(source, src)") && build.contains("LumaField.of(px, w, h, GLASS_COLS)"))
        assertTrue(SourceRoots.body(art, "private fun loadCached(").contains("LumaField.decode("))
        assertTrue(SourceRoots.body(art, "private fun saveCached(").contains("art.sharp.encode()"))
        assertTrue(code("WallDefault.kt").contains("LumaField.of(px, w, h)"))
        // T29-4 — kính.
        val glass = code("WallGlass.kt")
        assertTrue(SourceRoots.body(glass, "fun relocate(").contains("AdaptiveInk.update(view, force = true)"))
        assertTrue(SourceRoots.body(glass, "internal fun inkGroups(").contains("ContrastStack.glassVariants("))
        val paint = SourceRoots.body(glass, "private fun paint(")
        assertEquals(2, Regex("AdaptiveInk\\.surface\\(").findAll(paint).count(), "paint: cả hai nhánh (có/không ảnh) khai mặt")
        assertTrue(paint.contains("window.inkGroups("))
        assertTrue(SourceRoots.body(glass, "fun refresh(").contains("AdaptiveInk.onRefresh(root)"))
        assertTrue(SourceRoots.body(glass, "fun plain(").contains("AdaptiveInk.clear(view)"))
        // T29-5 — chữ/icon ô nhóm + bộ kiểu chữ widget (không còn setTextColor thô ở các hàm tô màu).
        val type = code("KachiWidgetType.kt")
        for (fn in listOf("fun pending(", "fun hero(", "fun unit(", "fun caption(", "fun title(")) {
            val b = SourceRoots.body(type, fn)
            assertTrue(b.contains("AdaptiveInk.text(tv, ") && !b.contains("setTextColor("), "KachiWidgetType.$fn")
        }
        val gtext = SourceRoots.body(code("GroupTileViews.kt"), "fun text(ctx: Context")
        assertTrue(gtext.contains("AdaptiveInk.text(this, color)") && !gtext.contains("setTextColor("))
        assertTrue(SourceRoots.body(code("GroupTileParts.kt"), "fun bind(cell: GroupCell)").contains("AdaptiveInk.icon("))
        // T29-7 — thanh trên + thanh nút + ô nút.
        val strip = code("KachiTopStrip.kt")
        val face = SourceRoots.body(strip, "private fun applyChipFace(")
        assertTrue(face.contains("AdaptiveInk.text(v, color, compound = true)") && face.contains("setTint(ink)") && !face.contains("setTextColor("))
        assertTrue(strip.contains("themed { AdaptiveInk.fill(strip) { intArrayOf(c(KachiTheme.BAR_TOP)) } }"))
        assertTrue(strip.contains("themed { AdaptiveInk.text(this, KachiTheme.INK) }") && strip.contains("themed { AdaptiveInk.text(this, chipInk(ChipTone.NEUTRAL)) }"))
        assertTrue(code("ControlDockView.kt").contains("AdaptiveInk.fill(this) { intArrayOf(c(KachiTheme.BAR)) }"))
        val tiles = code("ControlTileFactory.kt")
        assertTrue(SourceRoots.body(tiles, "private fun applyBg(").contains("AdaptiveInk.tile(v, active)"))
        val tint = SourceRoots.body(tiles, "private fun tint(")
        assertTrue(tint.contains("AdaptiveInk.text(label, ") && !tint.contains("label.setTextColor("))
    }

    @Test
    fun `cho da noi pha 2b - lop phu 3D, nut dia, nut thanh tren, nut doi, icon o nut, doan mau, o canh bao, nut DIM`() {
        // T29-6 — mặt khung 3D + ba sự kiện; không gì trong đường MỖI KHUNG (`project`, `onDraw`).
        val w = code("Car3dWidgetView.kt")
        assertTrue(w.contains("Car3dInk.declare(this, backdrop)") && w.contains("backdrop.paint = p.argb()"))
        val frame = SourceRoots.body(w, "private fun frame(")
        assertTrue(Regex("""if \(burst > 0\) \{[^\n]*Car3dInk\.settle\(this\) }""").containsMatchIn(frame), "giải lại khi xoay DỪNG (cuối đợt khung)")
        assertEquals(1, Regex("Car3dInk\\.").findAll(frame).count(), "frame chỉ gọi Car3dInk ở nhánh dừng")
        assertTrue(SourceRoots.text(dir + "Car3dWidgetView.kt").lines().size <= 500, "Car3dWidgetView trần 500 dòng")
        val hero = code("Car3dHeroLayer.kt")
        assertFalse(SourceRoots.body(hero, "fun project(").let { it.contains("AdaptiveInk") || it.contains("Car3dInk") }, "project chạy MỖI KHUNG — không giải ở đó")
        assertTrue(SourceRoots.body(hero, "fun reproject(").contains("Car3dInk::settle"))
        assertTrue(hero.contains("inkRegion(\"car3d-tyre\")"))
        val fill = SourceRoots.body(hero, "fun fill(d: WidgetData)")
        assertTrue(fill.contains("AdaptiveInk.text(p, ") && fill.contains("AdaptiveInk.text(t, KachiTheme.MUT)") && !fill.contains("setTextColor("))
        val seat = SourceRoots.body(hero, "private fun draw(id: String)")
        assertTrue(seat.indexOf("AdaptiveInk.fill(v, fades = false") in 0 until seat.indexOf("KachiIcons.tint("), "mặt đĩa khai TRƯỚC khi tô icon")
        val dots = code("Car3dDotsLayer.kt")
        val onDraw = SourceRoots.body(dots, "override fun onDraw(")
        assertFalse(onDraw.contains("adapt(") || onDraw.contains("backdropAt(") || onDraw.contains("halos("), "onDraw chỉ tra ô lớp đỡ")
        val adapt = SourceRoots.body(dots, "fun adapt(")
        assertTrue(adapt.contains("AdaptiveInk.backdropAt(this, box)") && adapt.contains("inks.halos(under,"))
        // [ĐO máy ảo 14/10] giải cả 15 điểm mỗi lần dừng xoay: đỉnh 15 ms luồng chính ⇒ chỉ điểm đang hiện, nền đổi mới giải.
        assertTrue(adapt.contains("if (!s.shown) continue") && adapt.contains("AdaptiveInk.sameBackdrop(under, s.under)"))
        val bd = code("Car3dBackdropView.kt")
        assertTrue(SourceRoots.body(bd, "private fun reload(").contains("bmp?.let { fieldOf(it) }"), "lưới dựng trên luồng giải mã sẵn có")
        assertFalse(SourceRoots.body(bd, "fun shadow(").contains("changed()"), "bóng đổi MỖI KHUNG khi xoay — không giải ở đó")
        val ink3d = code("Car3dInk.kt")
        assertTrue(SourceRoots.body(ink3d, "fun groups(").contains("InkModel.car3dVariants("))
        assertFalse(ink3d.contains("Thread(") || ink3d.contains("postDelayed") || code("AdaptiveInk.kt").contains("Thread("), "0 luồng · 0 nhịp")
        val foot = code("Car3dFootRow.kt")
        assertTrue(foot.contains("inkRegion(\"car3d-foot\")") && foot.contains("AdaptiveInk.text(this, KachiTheme.INK)"))
        assertTrue(SourceRoots.body(foot, "private fun disc(").contains("KachiButtonInks.adapt(this, child)"))
        assertTrue(SourceRoots.body(foot, "private fun drawRecirc(").contains("KachiButtonInks.at("))
        // KachiButtonInks: nhánh cố định chỉ còn là mặc định CHƯA ĐO; ba chỗ dựng nút đều nối mặt dò.
        assertTrue(SourceRoots.body(code("KachiButtonInks.kt"), "fun adapt(").contains("onUnder = { under ->"))
        for ((f, fn) in mapOf("ActionMatrixCompact.kt" to "private fun barButton(", "Car3dTaps.kt" to "private fun button("))
            assertTrue(SourceRoots.body(code(f), fn).contains("KachiButtonInks.adapt(this, getChildAt(0))"), "$f: $fn")
        // StateDotPainter: ba hình vẽ lớp đỡ đo được dưới nét.
        val painter = code("StateDotPainter.kt")
        for (fn in listOf("fun draw(canvas", "private fun drawButton(", "fun drawLamp("))
            assertTrue(SourceRoots.body(painter, fn).contains("if (halo != 0) { fill.color = halo;"), "StateDotPainter.$fn")
        // Thanh trên: hai nút pill (ứng dụng · mic · cài đặt) = mặt + icon thích ứng.
        val pill = SourceRoots.body(code("KachiTopStrip.kt"), "private fun pill(")
        assertTrue(pill.contains("AdaptiveInk.fill(this, fades = false, variant = fill)") && pill.contains("AdaptiveInk.icon(this, ") && !pill.contains("setColorFilter("))
        // Nút ⇄.
        val swap = code("SlotSwapButton.kt")
        assertTrue(SourceRoots.body(swap, "fun build(").contains("AdaptiveInk.icon(this, KachiTheme.MUT)") && SourceRoots.body(swap, "fun centered(").contains("AdaptiveInk.ride(visual, d)"))
        // Icon ô nút: nhánh tô mực qua bộ giải; độ mờ TẮT tính trên mực + nền ĐO.
        val icons = code("KachiIcons.kt")
        val tint = SourceRoots.body(icons, "fun tint(img: ImageView")
        assertTrue(tint.contains("AdaptiveInk.icon(img, inkHex)") && !tint.contains("setColorFilter(") && tint.contains("AdaptiveInk.release(img)"))
        assertTrue(SourceRoots.body(icons, "fun fadeOff(img: ImageView").contains("AdaptiveInk.onInk(img)"))
        // Đoạn màu trong chuỗi.
        assertTrue(SourceRoots.body(code("KachiWidgetType.kt"), "fun withUnit(").contains("AdaptiveInk.InkSpan(KachiTheme.MUT)"))
        val chip = code("TopStripChipInk.kt")
        assertFalse(chip.contains("ForegroundColorSpan") || code("KachiWidgetType.kt").contains("ForegroundColorSpan"), "không còn đoạn màu đóng băng")
        assertTrue(SourceRoots.body(chip, "internal fun applyChipText(").contains("AdaptiveInk.spans(v)"))
        // Ô con WARN/ALERT 35 % = mặt; nút −/+ DIM = mặt + chữ thích ứng.
        assertTrue(SourceRoots.body(code("GroupTileViews.kt"), "fun surfaceOf(").contains("AdaptiveInk.fill(view, fades = false, variant = t)"))
        val step = SourceRoots.body(code("ControlTileFactory.kt"), "private fun stepBtn(")
        assertTrue(step.contains("AdaptiveInk.fill(this, fades = false, variant = \"dim\")") && step.contains("AdaptiveInk.text(this, KachiTheme.INK)") && !step.contains("setTextColor("))
        // Lớp đỡ đặt được cả khi phần tử đã có nền (không còn nhánh "không đặt được").
        val ai = code("AdaptiveInk.kt")
        assertTrue(SourceRoots.body(ai, "private fun placeHaloUnsafe(").contains("HaloLayer(bg, haloLike(v, bg)"))
        assertFalse(ai.contains("không đặt được lớp đỡ (đã có nền)"))
    }

    /**
     * P0 14/10 [ĐO máy ảo, agent kính]: `GradientDrawable.getCornerRadii()` ném NPE (`float[].clone()` trên mảng null) khi nền chỉ
     * đặt `cornerRadius` ⇒ lỗi trong lớp đỡ lọt lên `Car3dHeroLayer.fill` ⇒ widget 3D lùi 2D. Khoá: đọc bán kính an toàn, và MỌI
     * cửa công khai của mực thích ứng là ranh giới fail-soft (log qua LogLineThrottle, không ném vào chỗ gọi).
     */
    @Test
    fun `muc thich ung khong bao gio nem vao cho goi - cornerRadii an toan, moi cua cong khai fail-soft`() {
        val ai = code("AdaptiveInk.kt")
        val like = SourceRoots.body(ai, "private fun haloLike(")
        assertTrue(like.contains("runCatching { g.cornerRadii }.getOrNull()"), "getCornerRadii phải đọc an toàn")
        assertFalse(Regex("""=\s*g\.cornerRadii\b""").containsMatchIn(like), "không đọc thẳng getCornerRadii")
        for (fn in listOf("private fun ink(", "fun update(view: View", "private fun placeHalo(", "fun spans(", "fun reapply("))
            assertTrue(SourceRoots.body(ai, fn).contains("catch (e: RuntimeException)"), "$fn phải là ranh giới fail-soft")
        // Nhánh lỗi ghi log qua bộ chặn lặp (fail-loud không spam), không nuốt im lặng.
        assertTrue(SourceRoots.body(ai, "private fun logW(").contains("throttle.suppressedBefore("))
        assertTrue(Regex("""catch \(e: RuntimeException\) \{\s*logW\(""").findAll(ai).count() >= 4)
    }

    @Test
    fun `ham moi cua R29 deu co call site ngoai dinh nghia`() {
        val main = SourceRoots.moduleSourceRoots().filter { it.toString().contains("app") }.flatMap { root ->
            root.toFile().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }.associate { it.name to com.byd.clusternav.testsupport.KotlinSource.stripComments(it.readText()) }
        fun callers(token: String, except: String) = main.filter { (n, s) -> n != except && s.contains(token) }.keys
        for (t in listOf("AdaptiveInk.text(", "AdaptiveInk.icon(", "AdaptiveInk.fill(", "AdaptiveInk.tile(", "AdaptiveInk.surface(",
            "AdaptiveInk.update(", "AdaptiveInk.reapply(", "AdaptiveInk.onRefresh(", "AdaptiveInk.clear(",
            "AdaptiveInk.backdropAt(", "AdaptiveInk.ride(", "AdaptiveInk.onInk(", "AdaptiveInk.release(", "AdaptiveInk.base(",
            "AdaptiveInk.spans(", "AdaptiveInk.InkSpan(")) {
            assertTrue(callers(t, "AdaptiveInk.kt").isNotEmpty(), "$t không có call site")
        }
        for (t in listOf("InkModel.candidates(", "ContrastStack.compose(", "InkModel.bandVariants(", "ContrastStack.glassVariants(",
            "ContrastStack.surfaceVariants(", "InkModel.plainWall(", "InkModel.scrim(", "InkModel.role(", "InkKey.of(",
            "InkModel.car3dVariants(", "InkModel::paintSpan", "InkModel.overlayHalo(")) {
            assertTrue(callers(t, "AdaptiveInkModel.kt").isNotEmpty(), "$t không có call site trong :app")
        }
        for ((t, home) in listOf("Car3dInk.declare(" to "Car3dInk.kt", "Car3dInk::changed" to "Car3dInk.kt", "Car3dInk.settle(" to "Car3dInk.kt",
            "inkRegion(" to "Car3dInk.kt", "KachiButtonInks.adapt(" to "KachiButtonInks.kt", "KachiButtonInks.at(" to "KachiButtonInks.kt",
            ".halos(" to "StateDotPainter.kt", "Inks.dotSlot(" to "StateDotPainter.kt", "painter.inks()" to "StateDotPainter.kt",
            ".boxUnder(" to "Car3dBackdropView.kt", ".shadowHits(" to "Car3dBackdropView.kt", "?.adapt()" to "Car3dDotsLayer.kt")) {
            assertTrue(callers(t, home).isNotEmpty(), "$t không có call site ngoài $home")
        }
        assertTrue(callers(".backdropAt(", "WallArt.kt").isNotEmpty() && callers(".glassAt(", "WallArt.kt").isNotEmpty())
        assertTrue(callers(".inkGroups(", "__").isNotEmpty())
    }
}
