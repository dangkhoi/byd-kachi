package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.Wcag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * R29 (làm lại 15/10) — bộ giải HỌ MỰC theo mặt ([ContrastInk.choose]). Owner 15/10: *"phải làm đổi màu chữ tự động, đồng đều,
 * chứ không vá thế này"*. Tính chất: với MỌI nền (0..1, hai chủ đề, ảnh trần lẫn thẻ mọi độ đục, ảnh rực màu), một mặt có ĐỦ các
 * vai nhận MỘT họ; họ đó ĐẠT sàn cho mọi vai — kiểm lại bằng phép đo ĐỘC LẬP (`Wcag`, vét 401 điểm) — hoặc bộ giải báo DƯỚI SÀN
 * và phép đo độc lập xác nhận quả thật không họ nào đạt (bộ giải không bỏ sót họ đạt). Không có lớp đỡ nào để tính vào.
 */
class ContrastInkTest {

    /** Tỉ số tệ nhất đo độc lập: dựng lại nền rồi đo bằng `Wcag` trên chuỗi hex. */
    private fun verified(ink: Int, b: ContrastBackdrop): Double =
        (0..400).minOf { Wcag.ratio(ColorMath.hex(ink), ColorMath.hex(b.groundAt(it / 400.0))) }

    /** Họ [f] đạt cho mọi phần tử của mặt — theo phép đo độc lập. */
    private fun passes(needs: List<InkNeed>, f: Int) = needs.all { verified(it.inks[f], it.ground) >= it.role.floor - 1e-6 }

    private fun sweepBackdrops(theme: ThemeFixture): Sequence<Pair<String, ContrastBackdrop>> = sequence {
        for (dim in intArrayOf(0, 45, 90)) for (width in doubleArrayOf(0.0, 0.1, 0.3, 0.6, 1.0)) {
            var l = 0.0
            while (l <= 1.0 - width + 1e-9) {
                val base = ToneBox.gray(l, minOf(1.0, l + width))
                yield("ảnh trần dim=$dim $base" to ContrastBackdrop(base, listOf(theme.scrim(dim))))
                for (fade in doubleArrayOf(0.2, 0.7, 1.0)) for (top in booleanArrayOf(true, false)) {
                    // ảnh mờ của thẻ: khoảng hẹp hơn ảnh sắc (mờ san phẳng chi tiết) nhưng cùng tâm, ĐÃ làm tối như WallArt.
                    val dimmed = ContrastBackdrop(base, listOf(theme.scrim(dim))).ground()
                    val glass = ToneBox(dimmed.at(0.25), dimmed.at(0.75))
                    yield("thẻ o${(fade * 100).toInt()} ${if (top) "đỉnh" else "đáy"} dim=$dim $base" to
                        ContrastBackdrop(base, listOf(theme.scrim(dim)) + theme.card(glass, fade, top = top)))
                }
                l += 0.04
            }
        }
    }

    @Test
    fun `quet 0 toi 1 hai chu de - mot ho cho ca mat, dat san hoac bao duoi san that su`() {
        ContrastLedger.reset()
        var cases = 0; var below = 0; var swapped = 0
        for (theme in ThemeFixture.BOTH) for ((label, b) in sweepBackdrops(theme)) {
            val needs = theme.surface(b)
            val c = ContrastInk.choose(ContrastInk.scores(needs, 4), passes = ContrastInk.passes(needs, 4))
            if (c.ok) {
                assertTrue(passes(needs, c.family), "${theme.name} $label ${c.describe()}: đo độc lập trượt")
            } else {
                below++
                for (f in 0 until 4) assertFalse(passes(needs, f), "${theme.name} $label: họ#$f đạt mà bộ giải báo dưới sàn")
            }
            cases++; if (c.family != 0) swapped++
        }
        println("[ĐO] R29 họ mực quét: $cases mặt · khác họ chủ đề $swapped · dưới sàn $below")
        assertEquals(below.toLong(), ContrastLedger.belowFloor.get(), "mỗi lần dưới sàn phải được đếm (fail-loud)")
        assertTrue(cases > 3_000, "quét quá thưa: $cases")
        assertTrue(below < cases / 2, "phần lớn mặt phải có họ đạt: $below/$cases")
    }

    /** Ảnh RỰC màu: hộp góc tối/sáng lệch sắc (đỏ cạnh lam, xanh lá, cam…) — đúng chỗ mô hình "xám" cũ sai 29 %. */
    @Test
    fun `anh ruc mau - dat san doc lap hoac that su khong ho nao dat`() {
        val rnd = kotlin.random.Random(1014)
        var cases = 0
        repeat(800) {
            val a = IntArray(3) { rnd.nextInt(256) }; val z = IntArray(3) { rnd.nextInt(256) }
            val box = ToneBox(ColorMath.argb(255, minOf(a[0], z[0]), minOf(a[1], z[1]), minOf(a[2], z[2])),
                ColorMath.argb(255, maxOf(a[0], z[0]), maxOf(a[1], z[1]), maxOf(a[2], z[2])))
            for (theme in ThemeFixture.BOTH) {
                val dim = intArrayOf(0, 45, 90)[rnd.nextInt(3)]
                val b = ContrastBackdrop(box, listOf(theme.scrim(dim)) + theme.card(box, fade = rnd.nextDouble(0.0, 1.0), top = rnd.nextBoolean()))
                val needs = theme.surface(b)
                val c = ContrastInk.choose(ContrastInk.scores(needs, 4))
                if (c.ok) assertTrue(passes(needs, c.family), "${theme.name} $b ${c.describe()}")
                else assertTrue((0 until 4).none { passes(needs, it) }, "${theme.name} $b: bỏ sót họ đạt")
                cases++
            }
        }
        assertTrue(cases >= 1_600)
    }

    @Test
    fun `tre - ho dang hien con dat thi giu, chi ve ho uu tien khi du le`() {
        // Đang ở họ đối cực nâng (3), mực chủ đề vừa đủ sàn ⇒ GIỮ (không nhấp nháy ở nhịp trình chiếu kế).
        assertEquals(3, ContrastInk.choose(doubleArrayOf(1.05, 1.05, 0.7, 1.4), current = 3).family)
        // Mực chủ đề đạt với lề ≥ 10 % ⇒ về lại.
        assertEquals(0, ContrastInk.choose(doubleArrayOf(1.12, 1.12, 0.7, 1.4), current = 3).family)
        // Họ đang hiện trượt ⇒ họ ĐẦU TIÊN đạt (không chờ lề).
        assertEquals(2, ContrastInk.choose(doubleArrayOf(0.99, 0.99, 1.01, 1.3), current = 0).family)
        // Mặt mới (chưa có họ) ⇒ họ đầu tiên đạt.
        assertEquals(1, ContrastInk.choose(doubleArrayOf(0.9, 1.0, 1.5, 1.5)).family)
        // Không họ nào đạt: giữ họ đang hiện nếu kém tốt nhất ≤ 5 %, không thì sang họ tốt nhất.
        val keep = ContrastInk.choose(doubleArrayOf(0.80, 0.80, 0.78, 0.78), current = 2)
        assertEquals(2, keep.family); assertFalse(keep.ok)
        assertEquals(0, ContrastInk.choose(doubleArrayOf(0.80, 0.80, 0.70, 0.70), current = 2).family)
        // Có số phần tử đạt: họ để NHIỀU phần tử đạt nhất thắng dù điểm MIN thấp hơn (một chip trên mảng tối không kéo cả thanh).
        assertEquals(2, ContrastInk.choose(doubleArrayOf(0.33, 0.33, 0.24, 0.24), passes = intArrayOf(2, 2, 8, 8)).family)
        // Hoà số đạt ⇒ điểm cao hơn; họ đang hiện hoà số đạt và kém điểm ≤ 5 % ⇒ giữ.
        assertEquals(3, ContrastInk.choose(doubleArrayOf(0.5, 0.5, 0.6, 0.7), passes = intArrayOf(4, 4, 6, 6)).family)
        assertEquals(2, ContrastInk.choose(doubleArrayOf(0.5, 0.5, 0.68, 0.7), current = 2, passes = intArrayOf(4, 4, 6, 6)).family)
    }

    /** Trình chiếu dao động quanh ngưỡng: chuỗi điểm lên xuống ±3 % quanh 1,0 — số lần đổi họ phải ≤ 1. */
    @Test
    fun `tre - nen dao dong sat nguong khong lam mat nhap nhay`() {
        var cur = -1; var flips = 0
        for (i in 0 until 40) {
            val theme = 1.0 + if (i % 2 == 0) 0.03 else -0.03   // mực chủ đề lúc đạt lúc trượt sát sàn
            val c = ContrastInk.choose(doubleArrayOf(theme, theme, 1.6, 1.6), cur)
            if (cur != -1 && c.family != cur) flips++
            cur = c.family
        }
        assertTrue(flips <= 1, "đổi họ $flips lần trong 40 nhịp sát ngưỡng")
    }

    @Test
    fun `mat toi tren nen toi - giu muc chu de, khong doi ho`() {
        val dark = ContrastBackdrop(ToneBox.gray(0.01))
        assertEquals(0, ContrastInk.choose(ContrastInk.scores(ThemeFixture.DARK.surface(dark), 4)).family)
        // Sáng trên ảnh tối: mực tối của chủ đề chìm ⇒ CẢ MẶT sang họ đối cực (mực sáng), không riêng một chữ.
        val c = ContrastInk.choose(ContrastInk.scores(ThemeFixture.LIGHT.surface(dark), 4))
        assertTrue(c.ok && c.family == 2, c.describe())
        assertEquals(ThemeFixture.DARK.ink, ThemeFixture.LIGHT.inks(InkRole.TEXT)[c.family])
    }

    /**
     * Diagnostics 301 §11 [ĐO máy ảo 14/10]: Sáng · ảnh + làm tối 45 % + thẻ 70 % ⇒ mặt thẻ ≈ (186,189,197), MUT 3,65–3,73:1.
     * Họ nâng chữ phụ (CÙNG cực, `mut` → `ink2`) đứng trước đổi cực ⇒ cả thẻ giữ mực tối, chữ phụ đậm lên một bậc cho mọi nhãn.
     */
    @Test
    fun `ca that GroupCellGrid - chu phu nang mot bac cung cuc cho ca the`() {
        val face = ColorMath.argb(255, 186, 189, 197)
        val theme = ThemeFixture.LIGHT
        assertTrue(ColorMath.ratio(theme.mut, face) < 4.5, "tiền đề: MUT trên mặt thẻ phải dưới sàn")
        val b = ContrastBackdrop(ToneBox(face, face))
        val needs = listOf(InkRole.TEXT, InkRole.SECONDARY_TEXT, InkRole.ICON).map { InkNeed(it, b, theme.inks(it)) }   // vai của ô nhóm
        val c = ContrastInk.choose(ContrastInk.scores(needs, 4))
        assertTrue(c.ok && c.family == 1, c.describe())
        assertTrue(ColorMath.ratio(theme.ink2, face) >= 4.5 && theme.ink2 != theme.ink, "chữ phụ vẫn khác mực chính")
    }

    /**
     * Ảnh nhiều chi tiết: sọc 2 px đen/trắng (đúng ảnh làm phương án A của 2.86 chết). Trung bình nói "xám 0.5" ⇒ A chọn một mực
     * đạt trên xám 0.5 — nhưng mực ấy chìm trên nửa số điểm ảnh. Bộ giải đo KHOẢNG ⇒ không họ nào đạt ⇒ BÁO dưới sàn, không vá.
     */
    @Test
    fun `anh soc 2 px - trung binh lua, khoang khong lua, bao duoi san`() {
        ContrastLedger.reset()
        val w = 960; val h = 120
        val px = IntArray(w * h) { if (((it % w) / 2) % 2 == 0) -1 else ColorMath.parse("#000000") }
        val region = LumaField.of(px, w, h).range(300, 30, 420, 70)!!
        for (theme in ThemeFixture.BOTH) {
            val meanGray = ColorMath.grayOfLuminance(region.mean)
            val meanPick = theme.inks(InkRole.TEXT).firstOrNull { ColorMath.ratio(it, meanGray) >= 4.5 }
            requireNotNull(meanPick) { "tiền đề: theo trung bình (${Wcag.fmt(region.mean)}) phương án A tìm được mực 'đạt'" }
            val c = ContrastInk.choose(ContrastInk.scores(theme.surface(ContrastBackdrop(region, listOf(theme.scrim(0)))), 4))
            assertFalse(c.ok, "${theme.name}: ${c.describe()}")
            assertTrue(c.describe().contains("DƯỚI-SÀN"))
        }
        assertEquals(2L, ContrastLedger.belowFloor.get())
    }

    @Test
    fun `anh chia doi sang toi - vung vat ranh bao duoi san, vung mot ben ca mat doi ho`() {
        val w = 640; val h = 200
        val px = IntArray(w * h) { if (it % w < w / 2) -1 else ColorMath.parse("#101010") }
        val f = LumaField.of(px, w, h)
        val theme = ThemeFixture.LIGHT
        fun on(b: ContrastBackdrop) = ContrastInk.choose(ContrastInk.scores(listOf(InkNeed(InkRole.ICON, b, theme.inks(InkRole.ICON))), 4))
        assertFalse(on(ContrastBackdrop(f.range(280, 80, 360, 120)!!)).ok)
        val darkSide = on(ContrastBackdrop(f.range(420, 80, 600, 120)!!))
        assertTrue(darkSide.ok && darkSide.family == 2, darkSide.describe())
    }

    @Test
    fun `mot phan tu truot thi CA MAT doi ho - khong co phan tu nao tu chon rieng`() {
        // Chữ chính đạt dưới họ 0; MỘT chữ phụ nằm trên chỗ sáng hơn (L 0,07) nên `mut` trượt ⇒ CẢ MẶT sang họ nâng (mọi chữ phụ
        // của mặt cùng đậm lên một bậc), không riêng chữ phụ đó.
        val t = ThemeFixture.DARK
        val text = InkNeed(InkRole.TEXT, ContrastBackdrop(ToneBox.gray(0.0)), t.inks(InkRole.TEXT))
        val icon = InkNeed(InkRole.SECONDARY_TEXT, ContrastBackdrop(ToneBox.gray(0.07)), t.inks(InkRole.SECONDARY_TEXT))
        assertTrue(ContrastInk.margin(text, 0) >= 1.0 && ContrastInk.margin(icon, 0) < 1.0, "tiền đề")
        val c = ContrastInk.choose(ContrastInk.scores(listOf(text, icon), 4))
        assertTrue(c.ok && c.family == 1, c.describe())
        assertEquals(c.score, minOf(ContrastInk.margin(text, c.family), ContrastInk.margin(icon, c.family)), 1e-12)
    }

    @Test
    fun `bang mau hong - khong ho nao dat thi bao duoi san, khong im lang`() {
        ContrastLedger.reset()
        val gray = ColorMath.grayOfLuminance(0.2)
        val c = ContrastInk.choose(ContrastInk.scores(listOf(InkNeed(InkRole.TEXT, ContrastBackdrop(ToneBox.gray(0.2)), intArrayOf(gray))), 1))
        assertFalse(c.ok, c.describe())
        assertTrue(c.describe().contains("DƯỚI-SÀN"))
        assertEquals(1L, ContrastLedger.belowFloor.get())
        assertEquals(1L, ContrastLedger.resolved.get())
        assertThrows(IllegalArgumentException::class.java) { ContrastInk.choose(DoubleArray(0)) }
        assertTrue(ContrastInk.score(emptyList(), 0) == Double.MAX_VALUE, "mặt không phần tử ⇒ không ràng buộc")
    }

    @Test
    fun `so dem dem dung khac ho, doi ho, duoi san`() {
        ContrastLedger.reset()
        val t = ThemeFixture.DARK
        val c0 = ContrastInk.choose(ContrastInk.scores(t.surface(ContrastBackdrop(ToneBox.gray(0.0))), 4))           // giữ
        ContrastInk.choose(ContrastInk.scores(t.surface(ContrastBackdrop(ToneBox.gray(1.0))), 4), c0.family)      // đổi
        ContrastInk.choose(ContrastInk.scores(t.surface(ContrastBackdrop(ToneBox.gray(0.0, 1.0))), 4))            // dưới sàn
        assertEquals(3L, ContrastLedger.resolved.get())
        assertEquals(1L, ContrastLedger.switched.get())
        assertTrue(ContrastLedger.swapped.get() >= 1L)
        assertEquals(1L, ContrastLedger.belowFloor.get())
        assertTrue(ContrastLedger.snapshot().contains("dưới-sàn=1"))
    }

    @Test
    fun `san theo vai dung WCAG`() {
        assertEquals(4.5, InkRole.TEXT.floor); assertEquals(4.5, InkRole.SECONDARY_TEXT.floor); assertEquals(4.5, InkRole.ACCENT_TEXT.floor)
        assertEquals(3.0, InkRole.LARGE_TEXT.floor); assertEquals(3.0, InkRole.ICON.floor); assertEquals(3.0, InkRole.ACCENT_ICON.floor)
    }
}
