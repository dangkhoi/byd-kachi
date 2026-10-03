package com.byd.clusternav.launcher

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.87 · R-OP3 — ĐỌC ĐƯỢC Ở MỌI BẬC: độ chói 0..1 × hai chủ đề × mọi màu nhấn/tông × mọi bậc độ đục ════════════
 *
 * Cùng khuôn `WallGlassContractTest.lop che du cho moi do choi anh…`, nhưng đo **mọi chồng lớp màn chính thật sự vẽ
 * lên một nền mờ** — chữ trên thanh/khay/thẻ, ô TẮT, ô BẬT, ô ACTIVE, ô cảnh báo — chứ không chỉ chữ trên nền. Luật
 * nghiệm thu: **0 ca dưới 4.5:1 ở chỗ hôm nay (100 %) đạt**.
 *
 * ⚠ Mô hình VẼ ở đây viết ĐỘC LẬP với `ChromeRoles` (sản phẩm): lớp nào nằm trên lớp nào được dựng lại từ bảng màu,
 * còn từ sản phẩm chỉ lấy QUYẾT ĐỊNH (hệ số/alpha mà `KachiChrome` chọn). Thêm một thứ vẽ lên nền mờ mà quên khai
 * chồng của nó cho bộ giải ⇒ bài này đỏ. `Drawable.alpha` theo phép nhân `GradientDrawable.modulateAlpha` [ĐO AOSP
 * r47]: `màu × (a + (a >> 7)) >> 8`. Vùng ảnh = xám cùng độ chói (như `GlassVeil`); dải che của hình nền KHÔNG tính
 * (nó chỉ làm nền dễ đọc hơn ⇒ bỏ qua là thận trọng).
 */
class ChromeOpacityContrastContractTest {

    @AfterEach
    fun reset() {
        KachiTheme.applyTheme(ThemeMode.NIGHT, 12, ColorChoice.DEFAULT, null)
        KachiChrome.apply(ChromeOpacity.DEFAULT, false)
    }

    /** Vai màu đọc thẳng từ bảng (KHÔNG qua `ChromeRoles`). */
    private class Pal(val name: String, val p: KachiPalette) {
        fun c(s: String) = ColorMath.parse(s)
        val bg = c(p.bg)
        val inks = intArrayOf(c(p.mut), c(p.mut2), c(p.ink))
        val tileInks = inks + intArrayOf(c(p.ink2), c(p.icon))
        val onInk = intArrayOf(c(p.inkOnAccent))
        val grounds = listOf(bg, ColorMath.over(c(p.glow1), bg), ColorMath.over(c(p.glow2), bg))
        val surf = intArrayOf(c(p.surfFrom), c(p.surfTo))
        val well = intArrayOf(c(p.slot), c(p.slotTo))
        val tileOn = intArrayOf(c(p.tileOnFrom), c(p.tileOnTo))
        val surfOn = intArrayOf(c(p.surfOnFrom), c(p.surfOnTo))
        val bar = c(p.bar)
        val barTop = c(p.barTop)
        /** Ô con nhóm WARN/ALERT: nền = sắc ngữ nghĩa ở 0x59 (GroupTileView.SEMANTIC_ALPHA), chữ = chính sắc đó. */
        val warn = listOf(c(p.amber) to ColorMath.withAlpha(c(p.amber), 0x59), c(p.red) to ColorMath.withAlpha(c(p.red), 0x59))
        fun glassPair(tone: SurfaceTone) = when (tone) {                                       // = KachiTheme.surfacePair(tone, true)
            SurfaceTone.NEUTRAL -> intArrayOf(c(p.surfFromOverArt), c(p.surfToOverArt))
            else -> intArrayOf(ColorMath.scaleAlpha(c(p.slot), 0.8), ColorMath.scaleAlpha(c(p.slotTo), 0.8))
        }
    }

    /** Áp từng bảng qua ĐÚNG đường sản phẩm (`applyTheme`), vì `KachiChrome` tính sàn trên bảng đang áp. */
    private fun forEachPalette(block: (Pal) -> Unit) {
        listOf("TỐI" to ThemeMode.NIGHT, "SÁNG" to ThemeMode.DAY).forEach { (n, mode) ->
            AccentChoice.values().filter { it != AccentChoice.FROM_ART }.forEach { a ->
                CardTone.values().forEach { t ->
                    KachiTheme.applyTheme(mode, 12, ColorChoice(a, t), null)
                    block(Pal("$n·$a·$t", KachiTheme.palette))
                }
            }
        }
    }

    /** Phép nhân của `GradientDrawable.modulateAlpha` (viết lại độc lập — không gọi `ChromeStack`). */
    private fun drawn(c: Int, a: Int): Int = ColorMath.withAlpha(c, (ColorMath.alpha(c) * (a + (a shr 7))) shr 8)
    private fun byteOf(t: Double) = Math.round(t * 255).toInt().coerceIn(0, 255)

    /** Mọi tổ hợp màu: [layers] (dưới → trên, mỗi lớp là các đầu chuyển sắc) phủ lên từng nền trong [grounds]. */
    private fun stack(grounds: List<Int>, vararg layers: IntArray): List<Int> =
        layers.fold(grounds) { gs, layer -> gs.flatMap { g -> layer.map { ColorMath.over(it, g) } } }

    private fun worst(inks: IntArray, grounds: List<Int>): Double = grounds.minOf { g -> inks.minOf { ColorMath.ratio(it, g) } }

    private val lums = (0..20).map { it * 0.05 }

    private class Sweep {
        val bad = mutableListOf<String>(); var n = 0
        private val kinds = sortedMapOf<String, Int>()
        fun check(what: String, today: Double, now: Double) {
            n++
            if (today >= 4.5 && now < 4.5) {
                bad += "$what: hôm nay ${"%.2f".format(today)} → ${"%.2f".format(now)}"
                // Nhóm theo (chủ đề, bậc, loại chồng) — bỏ màu/tông/độ chói — để thông điệp đỏ nói NGAY họ nào hỏng.
                val k = what.replace(Regex("""·\w+·\w+"""), "").replace(Regex("""l=[\d.]+ """), "")
                kinds[k] = (kinds[k] ?: 0) + 1
            }
        }
        fun report() = "${bad.size} ca · theo loại: $kinds · ví dụ: ${bad.take(8)}"
    }

    /** Không ảnh nền: nền dưới đã biết (nền màn + tâm hai vầng sáng THEO MÀU NHẤN), mọi lớp mờ chồng như màn thật. */
    @Test
    fun `khong anh nen moi chong lop van doc duoc`() {
        val s = Sweep()
        forEachPalette { pal ->
            ChromeOpacity.STEPS.forEach { pct ->
                KachiChrome.apply(pct, false)
                fun layers(today: Boolean): Map<String, Pair<IntArray, List<Int>>> {
                    val t = if (today) 1.0 else KachiChrome.surfaceFraction(onBar = false, hasArt = false)
                    val td = if (today) 1.0 else KachiChrome.surfaceFraction(onBar = true, hasArt = false)
                    fun fade(cs: IntArray, f: Double) = if (f >= 1.0) cs else IntArray(cs.size) { drawn(cs[it], byteOf(f)) }
                    val topA = if (today) 255 else KachiChrome.barAlpha(pal.barTop, null, tiles = false)
                    val barA = if (today) 255 else KachiChrome.barAlpha(pal.bar, null, tiles = true)
                    val top = stack(pal.grounds, intArrayOf(drawn(pal.barTop, topA)))
                    val bar = stack(pal.grounds, intArrayOf(drawn(pal.bar, barA)))
                    val well = stack(pal.grounds, fade(pal.well, t))
                    val out = linkedMapOf(
                        "chữ thanh trên" to (pal.inks to top),
                        "chữ thanh nút" to (pal.inks to bar),
                        "ô TẮT trên thanh nút" to (pal.tileInks to stack(bar, fade(pal.surf, td))),
                        "ô BẬT trên thanh nút" to (pal.onInk to stack(bar, pal.tileOn)),
                        "chữ khay ô" to (pal.inks to well),
                        "thẻ/ô TẮT trong khay" to (pal.tileInks to stack(well, fade(pal.surf, t))),
                        "ô BẬT trong khay" to (pal.onInk to stack(well, pal.tileOn)),
                        "ô ACTIVE trong khay" to (intArrayOf(ColorMath.parse(pal.p.ink)) to stack(well, pal.surfOn)),
                    )
                    pal.warn.forEachIndexed { i, (ink, fill) -> out["ô cảnh báo $i trong khay"] = intArrayOf(ink) to stack(well, intArrayOf(fill)) }
                    return out
                }
                val was = layers(true); val now = layers(false)
                was.forEach { (k, v) -> s.check("${pal.name} $pct% $k", worst(v.first, v.second), worst(now.getValue(k).first, now.getValue(k).second)) }
            }
        }
        assertTrue(s.n >= 2 * 24 * 5 * 10, "quét thiếu ca: ${s.n}")
        assertTrue(s.bad.isEmpty(), "nền mờ (không ảnh) làm hỏng chữ: ${s.report()}")
    }

    /** Thẻ/khay KÍNH trên ảnh: bề mặt × f + sàn lớp che MIN × f, bộ giải chọn lớp che (đúng `KachiGlass.paint`). */
    @Test
    fun `kinh tren anh nen doc duoc o moi bac, ke ca thu nam tren khay`() {
        val s = Sweep()
        forEachPalette { pal ->
            ChromeOpacity.STEPS.forEach { pct ->
                KachiChrome.apply(pct, true)
                listOf(SurfaceTone.NEUTRAL, SurfaceTone.WELL).forEach { tone ->
                    val pair = pal.glassPair(tone)
                    /** Nền dưới chữ của thẻ ở hệ số [f] trên vùng ảnh độ chói [l] — đúng ba lớp của `WallWindowDrawable` + bề mặt. */
                    fun glass(f: Double, l: Double): List<Int> {
                        val shown = if (f < 1.0) IntArray(pair.size) { drawn(pair[it], byteOf(f)) } else pair
                        // QUYẾT ĐỊNH lớp che lấy từ sản phẩm, đúng công thức của `WallWindowDrawable.relocate`
                        // (bài `KachiChromeContractTest` ghim công thức đó): max(bộ giải chữ, sàn thứ nằm trên khay).
                        val a = maxOf(
                            GlassVeil.alphaFor(l, pal.bg, shown, pal.inks, min = GlassVeil.MIN * f, max = if (f < 1.0) 1.0 else GlassVeil.MAX),
                            if (f < 1.0 && tone == SurfaceTone.WELL) KachiChrome.wellOverlayFloor(l, pal.bg, pair, shown, pal.inks, GlassVeil.MIN * f) else 0.0,
                        )
                        val veiled = ColorMath.over(ColorMath.withAlpha(pal.bg, (a * 255).toInt()), ColorMath.grayOfLuminance(l))
                        return stack(listOf(veiled), shown)
                    }
                    lums.forEach { l ->
                        val f = if (pct == 100) 1.0 else KachiChrome.fraction
                        val today = glass(1.0, l); val now = glass(f, l)
                        val tag = "${pal.name} $tone $pct% l=${"%.2f".format(l)}"
                        s.check("$tag chữ", worst(pal.inks, today), worst(pal.inks, now))
                        if (tone == SurfaceTone.WELL) {
                            val t = KachiChrome.surfaceFraction(onBar = false, hasArt = true)
                            val off = if (t < 1.0) IntArray(pal.surf.size) { drawn(pal.surf[it], byteOf(t)) } else pal.surf
                            s.check("$tag ô TẮT trong khay", worst(pal.tileInks, stack(today, pal.surf)), worst(pal.tileInks, stack(now, off)))
                            s.check("$tag ô BẬT trong khay", worst(pal.onInk, stack(today, pal.tileOn)), worst(pal.onInk, stack(now, pal.tileOn)))
                            pal.warn.forEachIndexed { i, (ink, fill) ->
                                s.check("$tag ô cảnh báo $i", worst(intArrayOf(ink), stack(today, intArrayOf(fill))), worst(intArrayOf(ink), stack(now, intArrayOf(fill))))
                            }
                        }
                    }
                }
            }
        }
        assertTrue(s.n >= 2 * 24 * 5 * 21 * 6, "quét thiếu ca: ${s.n}")
        assertTrue(s.bad.isEmpty(), "kính mờ theo bậc làm hỏng chữ: ${s.report()}")
    }

    /** Thanh trên/thanh nút trên ảnh: alpha do [KachiChrome.barAlpha] (vùng tệ nhất); ô TẮT/ô BẬT nằm trên thanh nút. */
    @Test
    fun `thanh tren anh nen giu san doc duoc o moi bac`() {
        val s = Sweep()
        forEachPalette { pal ->
            ChromeOpacity.STEPS.forEach { pct ->
                KachiChrome.apply(pct, true)
                val td = KachiChrome.surfaceFraction(onBar = true, hasArt = true)
                val off = if (td < 1.0) IntArray(pal.surf.size) { drawn(pal.surf[it], byteOf(td)) } else pal.surf
                lums.forEach { l ->
                    val gray = listOf(ColorMath.grayOfLuminance(l))
                    val tag = "${pal.name} $pct% l=${"%.2f".format(l)}"
                    val topToday = stack(gray, intArrayOf(pal.barTop))
                    val topNow = stack(gray, intArrayOf(drawn(pal.barTop, KachiChrome.barAlpha(pal.barTop, doubleArrayOf(l), tiles = false))))
                    s.check("$tag chữ thanh trên", worst(pal.inks, topToday), worst(pal.inks, topNow))
                    val barToday = stack(gray, intArrayOf(pal.bar))
                    val barNow = stack(gray, intArrayOf(drawn(pal.bar, KachiChrome.barAlpha(pal.bar, doubleArrayOf(l), tiles = true))))
                    s.check("$tag chữ thanh nút", worst(pal.inks, barToday), worst(pal.inks, barNow))
                    s.check("$tag ô TẮT", worst(pal.tileInks, stack(barToday, pal.surf)), worst(pal.tileInks, stack(barNow, off)))
                    s.check("$tag ô BẬT", worst(pal.onInk, stack(barToday, pal.tileOn)), worst(pal.onInk, stack(barNow, pal.tileOn)))
                }
            }
        }
        assertTrue(s.n >= 2 * 24 * 5 * 21 * 4, "quét thiếu ca: ${s.n}")
        assertTrue(s.bad.isEmpty(), "thanh mờ theo bậc làm hỏng chữ: ${s.report()}")
    }

    /**
     * Không phải nút chết (OQ3): bậc thấp THẬT SỰ làm trong hơn ở chỗ nền cho phép, và giữ đúng hôm nay ở chỗ không cho
     * phép. Số [SUY — bản sao ColorMath, chưa đo pixel] ghi lại để owner biết "chỉnh không ăn" là ở đâu.
     */
    @Test
    fun `bac thap thuc su lam trong hon o cho nen cho phep`() {
        KachiTheme.applyTheme(ThemeMode.NIGHT, 12, ColorChoice.DEFAULT, null)
        val dark = Pal("TỐI", KachiTheme.palette)
        KachiChrome.apply(40, true)
        val overDark = KachiChrome.barAlpha(dark.barTop, doubleArrayOf(0.02), tiles = false)
        val overWhite = KachiChrome.barAlpha(dark.barTop, doubleArrayOf(1.0), tiles = false)
        val mixed = KachiChrome.barAlpha(dark.barTop, doubleArrayOf(0.02, 1.0), tiles = false)
        assertTrue(overDark < 255, "ảnh tối: thanh trên phải trong hơn hôm nay ở 40 % (được $overDark/255)")
        assertEquals(255, overWhite, "ảnh trắng ở bảng tối: giữ đúng hôm nay (chữ hôm nay đã hụt — không làm tệ thêm)")
        assertEquals(255, mixed, "một vùng sáng dưới thanh là đủ giữ cả thanh — vùng TỆ NHẤT quyết")
        assertTrue(KachiChrome.barAlpha(dark.bar, doubleArrayOf(0.02), tiles = true) < 255, "thanh nút trên ảnh tối cũng trong hơn")
        // Không ảnh, bảng mặc định: sàn không ràng buộc ⇒ đúng bậc người chọn ở mọi chỗ.
        KachiChrome.apply(40, false)
        assertEquals(0.4, KachiChrome.surfaceFraction(onBar = false, hasArt = false), 1e-12)
        assertEquals(byteOf(0.4), KachiChrome.barAlpha(dark.barTop, null, tiles = false), "không ảnh: đúng 40 %")
        // Sàn không-ảnh theo VÙNG: ở bảng sáng · Lục ngọc · ấm, chữ trên KHAY sát 4.5 trên tâm vầng sáng ⇒ khay giữ
        // 100 %, nhưng thanh trên vẫn được mờ — một hệ số chung thì cả màn đứng yên vì khay.
        KachiTheme.applyTheme(ThemeMode.DAY, 12, ColorChoice(AccentChoice.TEAL, CardTone.WARM), null)
        KachiChrome.apply(70, false)
        assertEquals(1.0, KachiChrome.surfaceFraction(onBar = false, hasArt = false), 0.0, "khay giữ nguyên")
        assertTrue(KachiChrome.barAlpha(ColorMath.parse(KachiTheme.palette.barTop), null, tiles = false) < 255, "thanh trên vẫn mờ được")
    }
}
