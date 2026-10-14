package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.Wcag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * R29 — mô hình chồng lớp ([ContrastBackdrop]). Khoá: nền ghép đơn điệu theo `t`; MỌI điểm ảnh trong hộp cho nền ghép nằm
 * giữa hai góc (lý do "hai góc là đủ", không giả định xám); [ContrastBackdrop.worst] KHÔNG khai cao hơn phép vét dày;
 * thứ tự lớp đúng thứ tự vẽ.
 */
class ContrastBackdropTest {

    private val rnd = Random(1410)

    private fun randomColor(a: Int = 255) = ColorMath.argb(a, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))

    private fun randomBox(): ToneBox {
        val a = randomColor(); val b = randomColor()
        fun ch(f: (Int) -> Int, pick: (Int, Int) -> Int) = pick(f(a), f(b))
        return ToneBox(
            ColorMath.argb(255, ch(ColorMath::red, ::minOf), ch(ColorMath::green, ::minOf), ch(ColorMath::blue, ::minOf)),
            ColorMath.argb(255, ch(ColorMath::red, ::maxOf), ch(ColorMath::green, ::maxOf), ch(ColorMath::blue, ::maxOf)),
        )
    }

    private fun randomLayers(): List<ContrastBackdrop.Layer> = List(rnd.nextInt(0, 5)) {
        if (rnd.nextBoolean()) ContrastBackdrop.Layer.Flat(randomColor(rnd.nextInt(256)))
        else ContrastBackdrop.Layer.Photo(randomBox(), rnd.nextDouble())
    }

    private fun randomBackdrop() = ContrastBackdrop(randomBox(), randomLayers())

    /** Vét 2001 điểm theo `t` bằng phép đo ĐỘC LẬP (`Wcag`, chuỗi hex) — chuẩn so. */
    private fun brute(ink: Int, b: ContrastBackdrop): Double =
        (0..2000).minOf { Wcag.ratio(ColorMath.hex(ink), ColorMath.hex(b.groundAt(it / 2000.0))) }

    private fun inside(box: ToneBox): Int {
        fun pick(f: (Int) -> Int) = rnd.nextInt(f(box.lo), f(box.hi) + 1)
        return ColorMath.argb(255, pick(ColorMath::red), pick(ColorMath::green), pick(ColorMath::blue))
    }

    @Test
    fun `nen ghep don dieu khong giam theo t`() {
        repeat(400) {
            val b = randomBackdrop()
            var prev = -1.0
            for (i in 0..200) {
                val y = Luma.of(b.groundAt(i / 200.0))
                assertTrue(y >= prev - 1e-12, "$b t=${i / 200.0}: $y < $prev")
                prev = y
            }
        }
    }

    /** Cơ sở của cả bộ giải: không có "xám thay sắc" — điểm ảnh CÓ SẮC bất kỳ trong hộp, trộn thật từng lớp, vẫn nằm giữa hai góc. */
    @Test
    fun `moi diem anh trong hop cho nen ghep nam giua hai goc`() {
        repeat(3000) {
            val b = randomBackdrop()
            val lo = Luma.of(b.groundAt(0.0)); val hi = Luma.of(b.groundAt(1.0))
            repeat(20) {
                var g = inside(b.base)
                for (l in b.layers) g = when (l) {
                    is ContrastBackdrop.Layer.Flat -> l.over(g, 0.5)
                    is ContrastBackdrop.Layer.Photo -> ColorMath.over(ColorMath.withAlpha(inside(l.box), (l.alpha * 255).let { Math.round(it).toInt() }), g)
                }
                val y = Luma.of(g)
                assertTrue(y >= lo - 1e-12 && y <= hi + 1e-12, "$b: ${ColorMath.hex(g)} y=$y ngoài [$lo, $hi]")
            }
        }
    }

    @Test
    fun `muc duc - worst dung bang vet day, khong khai cao hon`() {
        repeat(600) {
            val b = randomBackdrop()
            val ink = randomColor()
            val w = b.worst(ink); val v = brute(ink, b)
            assertTrue(w <= v + 1e-9, "khai dư: worst=$w vét=$v ink=${ColorMath.hex(ink)} $b")
            if (w > 1.0) assertEquals(v, w, 1e-9, "ink=${ColorMath.hex(ink)} $b")
        }
    }

    @Test
    fun `muc co alpha - lay mau va do quanh day khong khai cao hon vet day qua 0,5 phan tram`() {
        repeat(800) {
            val b = randomBackdrop()
            val ink = randomColor(rnd.nextInt(40, 255))
            val w = b.worst(ink); val v = brute(ink, b)
            assertTrue(w <= v * 1.005 + 1e-9, "khai dư: worst=$w vét=$v ink=${ColorMath.hex(ink)} $b")
        }
    }

    @Test
    fun `muc nam trong khoang nen thi tan vao nen - 1 to 1`() {
        val b = ContrastBackdrop(ToneBox.gray(0.05, 0.9))
        assertEquals(1.0, b.worst(ColorMath.grayOfLuminance(0.4)), 0.0)
    }

    @Test
    fun `lop phang duc che het, lop alpha 0 khong doi gi, thu tu lop dung thu tu ve`() {
        val red = ColorMath.parse("#ff0000"); val blue = ColorMath.parse("#0000ff")
        val base = ToneBox.gray(0.0, 1.0)
        assertEquals(red, ContrastBackdrop(base, listOf(ContrastBackdrop.Layer.Flat(blue), ContrastBackdrop.Layer.Flat(red))).groundAt(0.3))
        assertEquals(blue, ContrastBackdrop(base, listOf(ContrastBackdrop.Layer.Flat(red), ContrastBackdrop.Layer.Flat(blue))).groundAt(0.3))
        val clear = ContrastBackdrop(base, listOf(ContrastBackdrop.Layer.Flat(ColorMath.withAlpha(red, 0)), ContrastBackdrop.Layer.Photo(base, 0.0)))
        assertEquals(ContrastBackdrop(base).groundAt(0.7), clear.groundAt(0.7))
    }

    @Test
    fun `hop xam lam tron ra ngoai - goc toi khong sang hon, goc sang khong toi hon`() {
        var y = 0.0
        while (y <= 1.0) {
            val b = ToneBox.gray(y)
            assertTrue(b.lumLo <= y + 1e-12 && b.lumHi >= y - 1e-12, "y=$y: [${b.lumLo}, ${b.lumHi}]")
            y += 0.001
        }
    }
}
