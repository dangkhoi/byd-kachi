package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.ContrastBackdrop.Layer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * R29 pha 2 — phép ghép chồng lớp ([ContrastStack]). Khoá: một tổ hợp giữ chồng CHÍNH XÁC; nhiều tổ hợp ⇒ hộp HỢP chứa mọi
 * nền thật (mọi điểm giữa hai đầu chuyển sắc, mọi vị trí trong dải che); dải che lấy mẫu đúng vùng chạm.
 */
class ContrastStackTest {

    @Test
    fun `mot to hop giu chong chinh xac - nhieu to hop hop lai`() {
        val base = ToneBox.gray(0.2, 0.6)
        val one = ContrastStack.compose(base, listOf(listOf(listOf(Layer.Flat(0x80000000.toInt())))))
        assertEquals(base, one.base); assertEquals(1, one.layers.size)
        val two = ContrastStack.compose(base, listOf(listOf(listOf(Layer.Flat(0x80000000.toInt())), listOf(Layer.Flat(0x80ffffff.toInt())))))
        val u = ContrastBackdrop(base, listOf(Layer.Flat(0x80000000.toInt()))).ground()
            .union(ContrastBackdrop(base, listOf(Layer.Flat(0x80ffffff.toInt()))).ground())
        assertEquals(u.lo, two.base.lo); assertEquals(u.hi, two.base.hi); assertTrue(two.layers.isEmpty())
        assertEquals(base, ContrastStack.compose(base, listOf(emptyList())).base, "nhóm rỗng bị bỏ qua")
    }

    @Test
    fun `hop bao tho - moi diem chuyen sac giua hai dau va moi diem anh trong hop nam trong nen ghep`() {
        val rnd = kotlin.random.Random(29)
        repeat(400) {
            fun col(a: Int) = ColorMath.argb(a, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            val a = 40 + rnd.nextInt(200)
            val top = col(a); val bottom = col(a)
            val lo = IntArray(3) { rnd.nextInt(200) }
            val base = ToneBox(ColorMath.argb(255, lo[0], lo[1], lo[2]), ColorMath.argb(255, lo[0] + 50, lo[1] + 50, lo[2] + 50))
            val b = ContrastStack.compose(base, listOf(ContrastStack.surfaceVariants(intArrayOf(top, bottom))))
            val g = b.ground()
            repeat(20) {
                val t = rnd.nextDouble(); val s = rnd.nextDouble()
                fun m(x: Int, y: Int) = Math.round(x + (y - x) * s).toInt()
                val mid = ColorMath.argb(a, m(ColorMath.red(top), ColorMath.red(bottom)), m(ColorMath.green(top), ColorMath.green(bottom)), m(ColorMath.blue(top), ColorMath.blue(bottom)))
                val px = ColorMath.over(mid, base.at(t))
                for (sh in intArrayOf(16, 8, 0)) {
                    val v = (px ushr sh) and 0xff
                    assertTrue(v >= ((g.lo ushr sh) and 0xff) - 1 && v <= ((g.hi ushr sh) and 0xff) + 1, "điểm ảnh lọt ngoài hộp hợp")
                }
            }
        }
    }

    @Test
    fun `dai che - ngoai dai mot bien the rong, cham dai lay mau hai dau`() {
        val bar = 0x99141b30.toInt()
        assertEquals(listOf(emptyList<Layer>()), ContrastStack.bandVariants(bar, 400, 600, 1080, 0.14f))
        val inTop = ContrastStack.bandVariants(bar, 0, 60, 1080, 0.14f)
        assertTrue(inTop.first() == listOf(Layer.Flat(bar)), "mép màn = màu dải đầy")
        assertTrue(inTop.size >= 6 && inTop.none { it.isEmpty() }, "vùng nằm trọn trong dải đỉnh: ${inTop.size}")
        val across = ContrastStack.bandVariants(bar, 100, 900, 1080, 0.14f)
        assertTrue(across.any { it.isEmpty() }, "vùng vắt qua giữa màn có phần không dải")
        assertEquals(bar, ContrastStack.towardClear(bar, 0.0)); assertEquals(0, ContrastStack.towardClear(bar, 1.0))
        assertEquals(listOf(emptyList<Layer>()), ContrastStack.bandVariants(0, 0, 60, 1080, 0.14f), "dải trong suốt ⇒ không lớp")
    }

    @Test
    fun `the kinh - dung thu tu ve, do duc 0 chi con be mat`() {
        val glass = ToneBox.gray(0.3, 0.5)
        val v = ContrastStack.glassVariants(glass, 0.7, 0.7, 0x5a0a0d13, 0x0f336699, intArrayOf(0x8effffff.toInt(), 0x8ef0f0f0.toInt()), 0)
        assertEquals(2, v.size)
        assertTrue(v[0][0] is Layer.Photo && v[0][1] == Layer.Flat(0x5a0a0d13) && v[0][2] == Layer.Flat(0x0f336699))
        val off = ContrastStack.glassVariants(glass, 0.0, 0.0, 0x5a0a0d13, 0, intArrayOf(0), 0)
        assertEquals(listOf(listOf<Layer>(Layer.Flat(0))), off)
    }
}
