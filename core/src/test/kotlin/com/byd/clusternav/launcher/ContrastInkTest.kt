package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.Wcag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * R29 — bộ giải mực ([ContrastInk]). Tính chất chính (owner 14/10 *"luôn luôn nhìn thấy được"*): với MỌI độ chói nền 0..1,
 * cả hai chủ đề, mọi vai, ảnh đồng màu lẫn ảnh nhiều chi tiết, trên ảnh trần lẫn trên thẻ ở mọi độ đục — mực trả về ĐẠT sàn,
 * hoặc đi kèm lớp đỡ làm nó đạt; kiểm lại bằng phép đo ĐỘC LẬP (`Wcag`, vét 401 điểm), không tin con số bộ giải tự khai.
 */
class ContrastInkTest {

    /** Kiểm độc lập: dựng lại nền (kể cả lớp đỡ) rồi đo bằng `Wcag` trên chuỗi hex. */
    private fun verified(r: Resolution, b: ContrastBackdrop): Double {
        val full = if (r.reinforced) b.with(ContrastBackdrop.Layer.Flat(r.haloColor())) else b
        return (0..400).minOf { Wcag.ratio(ColorMath.hex(r.ink), ColorMath.hex(full.groundAt(it / 400.0))) }
    }

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
                l += 0.02
            }
        }
    }

    @Test
    fun `quet do choi 0 toi 1 hai chu de moi vai - luon dat san hoac co lop do lam dat`() {
        var cases = 0; var reinforced = 0; var swapped = 0
        for (theme in ThemeFixture.BOTH) for ((label, b) in sweepBackdrops(theme)) for (role in InkRole.entries) {
            val r = ContrastInk.resolve(role, b, theme.candidates(role))
            assertTrue(r.ok, "${theme.name} $label ${r.describe()}")
            val v = verified(r, b)
            assertTrue(v >= role.floor - 1e-6, "${theme.name} $label ${role.name}: đo độc lập ${Wcag.fmt(v)} < ${role.floor} (${r.describe()})")
            cases++; if (r.reinforced) reinforced++; if (r.swapped) swapped++
        }
        println("[ĐO] R29 quét: $cases ca · đổi mực $swapped · lớp đỡ $reinforced")
        assertTrue(cases > 20_000, "quét quá thưa: $cases")
    }

    /** Ảnh RỰC màu: hộp góc tối/sáng lệch sắc (đỏ cạnh lam, xanh lá, cam…) — đúng chỗ mô hình "xám" cũ sai 29 %. */
    @Test
    fun `anh ruc mau - hop lech sac van luon dat san hoac co lop do`() {
        val rnd = kotlin.random.Random(1014)
        var cases = 0
        repeat(1500) {
            val a = IntArray(3) { rnd.nextInt(256) }; val z = IntArray(3) { rnd.nextInt(256) }
            val box = ToneBox(ColorMath.argb(255, minOf(a[0], z[0]), minOf(a[1], z[1]), minOf(a[2], z[2])),
                ColorMath.argb(255, maxOf(a[0], z[0]), maxOf(a[1], z[1]), maxOf(a[2], z[2])))
            for (theme in ThemeFixture.BOTH) {
                val dim = intArrayOf(0, 45, 90)[rnd.nextInt(3)]
                val b = ContrastBackdrop(box, listOf(theme.scrim(dim)) + theme.card(box, fade = rnd.nextDouble(0.0, 1.0), top = rnd.nextBoolean()))
                for (role in InkRole.entries) {
                    val r = ContrastInk.resolve(role, b, theme.candidates(role))
                    assertTrue(r.ok && verified(r, b) >= role.floor - 1e-6, "${theme.name} $b ${r.describe()}")
                    cases++
                }
            }
        }
        assertTrue(cases >= 18_000)
    }

    @Test
    fun `lop do la toi thieu - bot mot bac thi truot, ung vien khac khong can it hon`() {
        for (theme in ThemeFixture.BOTH) for ((label, b) in sweepBackdrops(theme)) for (role in InkRole.entries) {
            val cands = theme.candidates(role)
            val r = ContrastInk.resolve(role, b, cands)
            if (!r.reinforced) continue
            for (c in cands) {
                if (ColorMath.ratio(c.ink, c.halo) < role.floor) continue
                val less = r.haloAlpha - ContrastInk.HALO_STEP
                if (less < ContrastInk.HALO_STEP / 2) continue
                val w = b.with(ContrastBackdrop.Layer.Flat(ColorMath.withAlpha(c.halo, ContrastInk.alphaByte(less)))).worst(c.ink)
                assertTrue(w < role.floor, "${theme.name} $label ${role.name}: ${ColorMath.hex(c.ink)} đạt ở alpha $less < ${r.haloAlpha}")
            }
        }
    }

    @Test
    fun `lop do hoa alpha thi giu thu tu uu tien`() {
        val t = ThemeFixture.LIGHT
        val c = t.candidates(InkRole.TEXT)[0]
        val r = ContrastInk.resolve(InkRole.TEXT, ContrastBackdrop(ToneBox.gray(0.0, 1.0)), listOf(c, c.copy()))
        assertTrue(r.reinforced, r.describe())
        assertEquals(0, r.index, "hai ứng viên cần cùng alpha ⇒ giữ ứng viên đứng trước")
    }

    @Test
    fun `khong doi muc khi muc mac dinh da dat - ket qua on dinh`() {
        // Tối trên nền tối đồng màu: mực chủ đề đủ ⇒ index 0, không đỡ.
        val dark = ContrastBackdrop(ToneBox.gray(0.01))
        for (role in InkRole.entries) {
            val r = ContrastInk.resolve(role, dark, ThemeFixture.DARK.candidates(role))
            assertEquals(0, r.index, r.describe()); assertFalse(r.reinforced, r.describe())
        }
        // Sáng trên ảnh tối: mực tối của chủ đề chìm ⇒ đổi sang mực sáng (không đỡ, vì nền đồng màu).
        val r = ContrastInk.resolve(InkRole.TEXT, dark, ThemeFixture.LIGHT.candidates(InkRole.TEXT))
        assertTrue(r.swapped && !r.reinforced, r.describe())
        assertEquals(ThemeFixture.DARK.ink, r.ink)
    }

    /** Diagnostics 301 §11 [ĐO máy ảo 14/10]: Sáng · ảnh + làm tối 45 % + thẻ 70 % ⇒ mặt thẻ ≈ (186,189,197), MUT 3,65–3,73:1. */
    @Test
    fun `ca that GroupCellGrid - MUT tren the sang 186 189 197 khong duoc giu nguyen`() {
        val face = ColorMath.argb(255, 186, 189, 197)
        val theme = ThemeFixture.LIGHT
        assertTrue(ColorMath.ratio(theme.mut, face) < 4.5, "tiền đề: MUT trên mặt thẻ phải dưới sàn")
        val r = ContrastInk.resolve(InkRole.SECONDARY_TEXT, ContrastBackdrop(ToneBox(face, face)), theme.candidates(InkRole.SECONDARY_TEXT))
        assertTrue(r.ok && r.ink != theme.mut, r.describe())
        assertTrue(ColorMath.ratio(r.ink, face) >= 4.5, r.describe())
    }

    /**
     * Ảnh nhiều chi tiết: sọc 2 px đen/trắng (đúng ảnh làm phương án A của 2.86 chết). Trung bình nói "xám 0.5" ⇒ A chọn một mực
     * đạt trên xám 0.5 — nhưng mực ấy chìm trên nửa số điểm ảnh. Bộ giải đo KHOẢNG ⇒ không mực trần nào đạt ⇒ xin lớp đỡ.
     */
    @Test
    fun `anh soc 2 px - trung binh lua, khoang khong lua, bo giai xin lop do`() {
        val w = 960; val h = 120
        val px = IntArray(w * h) { if (((it % w) / 2) % 2 == 0) -1 else ColorMath.parse("#000000") }
        val region = LumaField.of(px, w, h).range(300, 30, 420, 70)!!
        for (theme in ThemeFixture.BOTH) {
            val meanGray = ColorMath.grayOfLuminance(region.mean)
            val meanPick = theme.candidates(InkRole.TEXT).firstOrNull { ColorMath.ratio(it.ink, meanGray) >= 4.5 }
            requireNotNull(meanPick) { "tiền đề: theo trung bình (${Wcag.fmt(region.mean)}) phương án A tìm được mực 'đạt'" }
            val onPixels = minOf(ColorMath.ratio(meanPick.ink, -1), ColorMath.ratio(meanPick.ink, ColorMath.parse("#000000")))
            assertTrue(onPixels < 4.5, "tiền đề: một mực trần không thể đạt trên cả trắng lẫn đen (${Wcag.fmt(onPixels)})")
            val b = ContrastBackdrop(region, listOf(theme.scrim(0)))
            val r = ContrastInk.resolve(InkRole.TEXT, b, theme.candidates(InkRole.TEXT))
            assertTrue(r.ok && r.reinforced, "${theme.name}: ${r.describe()}")
            assertTrue(verified(r, b) >= 4.5 - 1e-6)
        }
    }

    @Test
    fun `anh chia doi sang toi - vung vat ranh xin lop do, vung mot ben chi doi muc`() {
        val w = 640; val h = 200
        val px = IntArray(w * h) { if (it % w < w / 2) -1 else ColorMath.parse("#101010") }
        val f = LumaField.of(px, w, h)
        val theme = ThemeFixture.LIGHT
        val across = ContrastInk.resolve(InkRole.ICON, ContrastBackdrop(f.range(280, 80, 360, 120)!!), theme.candidates(InkRole.ICON))
        assertTrue(across.reinforced, across.describe())
        val darkSide = ContrastInk.resolve(InkRole.ICON, ContrastBackdrop(f.range(420, 80, 600, 120)!!), theme.candidates(InkRole.ICON))
        assertTrue(!darkSide.reinforced && darkSide.swapped, darkSide.describe())
    }

    @Test
    fun `bang mau hong - khong ung vien nao do duoc thi bao duoi san, khong im lang`() {
        ContrastLedger.reset()
        val gray = ColorMath.grayOfLuminance(0.2)
        val bad = listOf(InkCandidate(gray, ColorMath.grayOfLuminance(0.22)))
        val r = ContrastInk.resolve(InkRole.TEXT, ContrastBackdrop(ToneBox.gray(0.2)), bad)
        assertFalse(r.ok, r.describe())
        assertTrue(r.describe().contains("DƯỚI-SÀN"))
        assertEquals(1L, ContrastLedger.belowFloor.get())
        assertEquals(1L, ContrastLedger.resolved.get())
        assertThrows(IllegalArgumentException::class.java) { ContrastInk.resolve(InkRole.TEXT, ContrastBackdrop(ToneBox.gray(0.2)), emptyList()) }
    }

    @Test
    fun `so dem dem dung doi muc va lop do`() {
        ContrastLedger.reset()
        val t = ThemeFixture.DARK
        ContrastInk.resolve(InkRole.TEXT, ContrastBackdrop(ToneBox.gray(0.0)), t.candidates(InkRole.TEXT))   // giữ
        ContrastInk.resolve(InkRole.TEXT, ContrastBackdrop(ToneBox.gray(1.0)), t.candidates(InkRole.TEXT))   // đổi
        ContrastInk.resolve(InkRole.TEXT, ContrastBackdrop(ToneBox.gray(0.0, 1.0)), t.candidates(InkRole.TEXT)) // đỡ
        assertEquals(3L, ContrastLedger.resolved.get())
        assertEquals(1L, ContrastLedger.reinforced.get())
        assertTrue(ContrastLedger.swapped.get() >= 1L)
        assertEquals(0L, ContrastLedger.belowFloor.get())
        assertTrue(ContrastLedger.snapshot().contains("dưới-sàn=0"))
    }

    @Test
    fun `san theo vai dung WCAG`() {
        assertEquals(4.5, InkRole.TEXT.floor); assertEquals(4.5, InkRole.SECONDARY_TEXT.floor); assertEquals(4.5, InkRole.ACCENT_TEXT.floor)
        assertEquals(3.0, InkRole.LARGE_TEXT.floor); assertEquals(3.0, InkRole.ICON.floor); assertEquals(3.0, InkRole.ACCENT_ICON.floor)
    }
}
