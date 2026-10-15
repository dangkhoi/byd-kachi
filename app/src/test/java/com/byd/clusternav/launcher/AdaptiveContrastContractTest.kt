package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.ContrastBackdrop.Layer
import com.byd.clusternav.testsupport.Wcag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.6 R29 (làm lại 15/10) — HỌ mực theo mặt trên bảng màu SỐNG ([KachiPalette] + mọi bảng suy theo lựa chọn màu), chồng lớp
 * dựng bằng CHÍNH hàm mã vẽ dùng ([InkModel]). Owner 15/10: *"phải làm đổi màu chữ tự động, đồng đều, chứ không vá thế này"*.
 *
 * Tính chất: một MẶT mang ĐỦ mọi vai trên một nền ghép (ảnh trần · thẻ kính o20/o70/o100 · dải che thanh trên × làm tối 0/45/90 ×
 * độ chói 0..1 × độ rộng hộp) nhận MỘT họ ([InkModel.families] → [ContrastInk.choose]); họ đó ĐẠT sàn cho mọi phần tử — kiểm lại
 * độc lập bằng [Wcag] trên chuỗi hex — hoặc bộ giải báo DƯỚI SÀN và phép đo độc lập xác nhận không họ nào đạt (không bỏ sót).
 * Hợp đồng nguồn (không lớp đỡ nào tồn tại · nối dây) ở `AdaptiveInkSourceContractTest`.
 */
class AdaptiveContrastContractTest {

    private fun c(hex: String) = ColorMath.parse(hex)
    private fun drawn(color: Int, f: Double) = ColorMath.withAlpha(color, ChromeOpacity.drawnAlpha(ColorMath.alpha(color), f))

    /** Tỉ số tệ nhất của nét [ink] (có thể có alpha) trên nền [b], đo độc lập bằng [Wcag] trên chuỗi hex. */
    private fun worstOn(ink: Int, b: ContrastBackdrop): Double =
        (0..100).minOf { val g = ColorMath.hex(b.groundAt(it / 100.0)); Wcag.ratio(ColorMath.hex(ink), g) }

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

    /** Một mặt mang các vai [keys] (mỗi vai ở chữ thường · chữ lớn · nét) trên nền [b] — đúng phép `AdaptiveInk.decide` dựng. */
    private fun surface(p: KachiPalette, b: ContrastBackdrop, keys: List<InkKey> = InkKey.entries): List<InkNeed> {
        val fams = InkModel.families(p)
        return keys.flatMap { k ->
            listOf(false to false, false to true, true to false).map { (icon, large) ->
                InkNeed(InkModel.role(k, icon, large), b, IntArray(fams.size) { InkModel.ink(k, fams[it]) })
            }
        }
    }

    /** Họ [f] đạt cho mọi phần tử của mặt — theo phép đo ĐỘC LẬP. */
    private fun passes(needs: List<InkNeed>, f: Int) = needs.all { worstOn(it.inks[f], it.ground) >= it.role.floor - 1e-6 }

    /** Kiểm một lần chọn: đạt ⇒ đo độc lập đạt; dưới sàn ⇒ đo độc lập: không họ nào đạt. Trả `true` khi đạt. */
    private fun checked(label: String, needs: List<InkNeed>, c: FamilyChoice, families: Int): Boolean {
        if (c.ok) assertTrue(passes(needs, c.family), "$label ${c.describe()}: đo độc lập trượt")
        else for (f in 0 until families) assertFalse(passes(needs, f), "$label: họ#$f đạt mà bộ giải báo dưới sàn")
        return c.ok
    }

    @Test
    fun `bang mau song - mot ho cho ca mat, dat san doc lap hoac bao duoi san that su`() {
        ContrastLedger.reset()
        var cases = 0; var below = 0; var other = 0
        for (p in listOf(KachiPalette.DARK, KachiPalette.LIGHT)) for ((label, b) in backdrops(p, step = 0.2)) {
            val needs = surface(p, b)
            val ch = ContrastInk.choose(ContrastInk.scores(needs, 4))
            if (!checked("${p.bg} $label", needs, ch, 4)) below++
            cases++; if (ch.family != 0) other++
        }
        println("[ĐO] R29 họ mực bảng sống (mặt đủ 12 vai × 3 loại): $cases mặt · khác họ chủ đề $other · dưới sàn $below")
        assertEquals(below.toLong(), ContrastLedger.belowFloor.get(), "mỗi mặt dưới sàn phải được đếm (fail-loud)")
        assertTrue(cases > 300, "quét quá thưa: $cases")
    }

    /** Mặt thật thường gặp (thẻ: chữ · chữ phụ · nhấn): trên THẺ kính mọi độ đục, bảng sống và mọi bảng suy theo màu nhấn. */
    @Test
    fun `the kinh - chu, chu phu, nhan cung mot ho, moi bang mau`() {
        var cases = 0; var below = 0
        for (p in livePalettes()) for ((label, b) in backdrops(p, step = 0.25).filter { it.first.startsWith("thẻ") }) {
            val needs = surface(p, b, listOf(InkKey.INK, InkKey.MUT, InkKey.ACCENT_INK))
            if (!checked("${p.accentInk} $label", needs, ContrastInk.choose(ContrastInk.scores(needs, 4)), 4)) below++
            cases++
        }
        println("[ĐO] R29 thẻ kính chữ/chữ phụ/nhấn: $cases mặt · dưới sàn $below")
    }

    @Test
    fun `ca that GroupCellGrid - MUT tren the sang 186 189 197 - ca the nang chu phu cung cuc`() {
        val p = KachiPalette.LIGHT
        val b = ContrastBackdrop(ToneBox.span(ColorMath.argb(255, 186, 189, 197)))
        assertTrue(ColorMath.ratio(c(p.mut), b.groundAt(0.0)) < 4.5, "tiền đề: MUT trên (186,189,197) dưới sàn")
        val needs = surface(p, b, listOf(InkKey.INK, InkKey.MUT, InkKey.ICON))
        val ch = ContrastInk.choose(ContrastInk.scores(needs, 4))
        assertTrue(ch.ok && ch.family == 1 && passes(needs, 1), ch.describe())
        val fam = InkModel.families(p)[1]
        assertEquals(ColorMath.withAlpha(c(p.ink2), 255), InkModel.ink(InkKey.MUT, fam), "chữ phụ nâng = INK2 cùng bảng")
        assertEquals(ColorMath.withAlpha(c(p.ink), 255), InkModel.ink(InkKey.INK, fam), "mực chính không đổi")
    }

    @Test
    fun `ho muc - thu tu, cuc, chu phu giu quan he voi muc chinh, vai mang nghia giu nghia`() {
        for (p in livePalettes()) {
            val f = InkModel.families(p)
            assertEquals(4, f.size)
            assertTrue(f[0].palette === p && !f[0].raised && f[1].palette === p && f[1].raised)
            assertTrue(f[2].palette === InkModel.other(p) && !f[2].raised && f[3].palette === InkModel.other(p) && f[3].raised)
            assertTrue(f[0].lightInk != f[2].lightInk, "họ đối cực phải khác cực mực")
            for (fam in f) {
                // Chữ phụ không bao giờ ĐẬM hơn mực chính của cùng họ (giữ phân cấp).
                val ink = Luma.of(InkModel.ink(InkKey.INK, fam)); val mut = Luma.of(InkModel.ink(InkKey.MUT, fam))
                val bg = Luma.of(c(fam.palette.bg))
                assertTrue(kotlin.math.abs(mut - bg) <= kotlin.math.abs(ink - bg) + 1e-9, "${fam.palette.bg} raised=${fam.raised}: chữ phụ đậm hơn mực chính")
                for (k in InkKey.entries.filter { it.semantic }) assertEquals(ColorMath.withAlpha(c(k.pick(fam.palette)), 255), InkModel.ink(k, fam), "$k giữ mã của họ")
            }
        }
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

    /** T29-8 — hộp thoại: mặt ĐỤC tĩnh (`surfFrom`→`surfTo`) ⇒ họ của chủ đề (0) đạt luôn cho mọi vai dùng ở đây. */
    @Test
    fun `hop thoai tinh - ho chu de dat tren hai dau mat hop`() {
        for (p in livePalettes()) {
            val b = ContrastBackdrop(ToneBox.span(ColorMath.withAlpha(c(p.surfFrom), 255), ColorMath.withAlpha(c(p.surfTo), 255)))
            val needs = surface(p, b, listOf(InkKey.INK, InkKey.INK2, InkKey.MUT, InkKey.ACCENT_INK))
            val ch = ContrastInk.choose(ContrastInk.scores(needs, 4))
            assertTrue(ch.ok && ch.family == 0 && passes(needs, 0), "${p.bg} hộp thoại: ${ch.describe()}")
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

    // ═══ Lớp phủ xe 3D · nút đĩa · chấm (spec §5.1 T29-6) — bộ vẽ tự tô góp điểm, KHÔNG lớp đỡ ═══════════════════════════

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
    fun `nut dia - cong thuc theo ho cua mat, glyph dat san hoac bao duoi san, Toi tren nen toi giu byte`() {
        var n = 0; var below = 0
        for (p in livePalettes()) for ((label, b) in backdrops(p, step = 0.25) + car3d(p).filterIndexed { i, _ -> i % 5 == 0 }) {
            val fams = InkModel.families(p)
            // Kiểu đĩa chung của nhóm: kiểu để điểm cao nhất dưới từng họ (đúng phép `AdaptiveInk.decide`).
            val alt = IntArray(fams.size) { f -> (0..1).maxByOrNull { KachiButtonInks.score(KachiButtonInks.of(p, fams[f], it), b) }!! }
            val ch = ContrastInk.choose(DoubleArray(fams.size) { KachiButtonInks.score(KachiButtonInks.of(p, fams[it], alt[it]), b) })
            val k = KachiButtonInks.of(p, fams[ch.family], alt[ch.family])
            val rest = worstOn(c(k.glyph), b.with(Layer.Flat(k.rest))); val on = worstOn(c(k.glyphOn), b.with(Layer.Flat(k.on)))
            val ok = rest >= KachiButtonInks.GLYPH_FLOOR - 1e-6 && on >= KachiButtonInks.GLYPH_ON_FLOOR - 1e-6
            if (ch.ok) assertTrue(ok, "${p.bg} $label: glyph nghỉ ${Wcag.fmt(rest)} · bật ${Wcag.fmt(on)}") else below++
            n++
        }
        // Bảng Tối, họ chủ đề ⇒ đúng từng byte 3.1.4 (CLAUDE.md §6); bảng Sáng ⇒ đúng mặc định 3.1.x (kính tối) dưới MỌI họ.
        val d = KachiPalette.DARK; val l = KachiPalette.LIGHT
        assertEquals(KachiButtonInks.of(d), KachiButtonInks.of(d, InkModel.families(d)[0]))
        for (f in InkModel.families(l)) for (a in 0..1) assertEquals(KachiButtonInks.of(l), KachiButtonInks.of(l, f, a))
        assertTrue(KachiButtonInks.of(d, InkModel.families(d)[0], 1) != KachiButtonInks.of(d), "Tối, kiểu 1 = kính tối (khác tông mềm)")
        for (g in listOf(d.card, d.tile, d.bg)) assertTrue(KachiButtonInks.score(KachiButtonInks.of(d), ContrastBackdrop(ToneBox.span(c(g)))) >= 1.0, "Tối trên $g")
        println("[ĐO] R29 nút đĩa: $n nền · dưới sàn $below")
    }

    @Test
    fun `cham den nut tren xe 3D - bo mau theo ho dat 3 hoac bao duoi san, kiem doc lap theo dung thu tu ve`() {
        var cases = 0; var below = 0
        for (p in listOf(KachiPalette.DARK, KachiPalette.LIGHT)) {
            val fams = InkModel.families(p)
            val inks = fams.map { StateDotPainter.Inks.of(it.palette, it.lightInk) }
            fun ContrastBackdrop.over(x: Int) = with(Layer.Flat(x))
            for ((label, b) in car3d(p).filterIndexed { i, _ -> i % 2 == 0 }) for (shape in StateDotPainter.Inks.Shape.entries) {
                val ch = ContrastInk.choose(DoubleArray(fams.size) { inks[it].score(b, shape, c(fams[it].palette.amber)) })
                cases++
                if (!ch.ok) { below++; continue }
                val k = inks[ch.family]; val warn = c(fams[ch.family].palette.amber)
                val checks: List<Pair<Int, ContrastBackdrop>> = when (shape) {
                    StateDotPainter.Inks.Shape.DOT -> listOf(k.closed, k.unknown, k.accent, warn).map { it to b.over(k.backing) } +
                        listOf(k.accent to b.over(k.openGlow).over(k.backing),
                            warn to b.over(ColorMath.withAlpha(warn, StateDotPainter.OPEN_GLOW_ALPHA)).over(k.backing))
                    StateDotPainter.Inks.Shape.BUTTON -> listOf(k.closed, k.unknown).map { k.drawnGlyph(it) to b.over(k.shadow).over(k.tonal) } +
                        listOf(k.drawnGlyph(k.accent) to b.over(ColorMath.withAlpha(k.accent, StateDotPainter.GLOW_OUTER_ALPHA)).over(k.openGlow).over(k.shadow).over(k.tonal))
                    StateDotPainter.Inks.Shape.LAMP -> {
                        val glow = ColorMath.withAlpha(k.lampTone(null), StateDotPainter.LAMP_GLOW_ALPHA)
                        listOf(k.lampOff, k.lampUnknown).flatMap { i -> listOf(b, b.over(k.lampEdge)).map { k.drawnGlyph(i) to it } } +
                            listOf(b.over(glow), b.over(glow).over(k.lampEdge)).map { k.drawnGlyph(k.lampTone(null)) to it }
                    }
                }
                for ((ink, g) in checks) assertTrue(worstOn(ink, g) >= InkRole.ICON.floor - 1e-6, "${p.bg} $label ${shape.name} ${ColorMath.hex(ink)}: ${Wcag.fmt(worstOn(ink, g))}")
            }
        }
        println("[ĐO] R29 chấm/nút/đèn 3D theo họ: $cases phép chọn · dưới sàn $below")
        assertTrue(cases > 1000)
    }
}
