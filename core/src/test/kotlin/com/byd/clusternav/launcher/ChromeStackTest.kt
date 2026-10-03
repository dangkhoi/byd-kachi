package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.ChromeStack.Layer
import com.byd.clusternav.launcher.ChromeStack.Stack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 2.87 · R-OP3 — số học của "chồng lớp" và sàn đọc được ([ChromeStack]). Màu là mẫu bảng tối, không phải bảng thật. */
class ChromeStackTest {

    private fun c(s: String) = ColorMath.parse(s)
    private val bg = c("#141b30")
    private val bar = c("#99141b30")
    private val inks = intArrayOf(c("#9daabe"), c("#eaf0f8"))
    private val text = Stack(emptyList(), inks)

    @Test
    fun `luoi do choi phu du 0 toi 1`() {
        assertEquals(21, ChromeStack.LUMS.size)
        assertEquals(0.0, ChromeStack.LUMS.first(), 0.0)
        assertEquals(1.0, ChromeStack.LUMS.last(), 1e-12)
    }

    @Test
    fun `alpha ve ra theo dung phep nhan modulateAlpha`() {
        assertEquals(0xcc, ChromeStack.drawnAlpha(0xcc, 1.0), "t = 1 ⇒ không đổi")
        assertEquals(255, ChromeStack.byteOf(1.0))
        assertEquals(102, ChromeStack.byteOf(0.4))
        // AOSP r47 GradientDrawable.modulateAlpha: alpha * (a + (a >> 7)) >> 8.
        assertEquals((0xcc * (102 + 0)) shr 8, ChromeStack.drawnAlpha(0xcc, 0.4))
        assertEquals((255 * (217 + 1)) shr 8, ChromeStack.drawnAlpha(255, 0.85))
        assertEquals(ColorMath.withAlpha(bar, ChromeStack.drawnAlpha(0x99, 0.7)), ChromeStack.faded(bar, 0.7))
    }

    @Test
    fun `worst xet moi to hop mau cua moi lop`() {
        val light = c("#eeeeee")
        val gradient = Layer(intArrayOf(bg, light))          // một đầu tối, một đầu sáng
        val w = ChromeStack.worst(Stack(listOf(gradient), inks), 1.0, bg)
        assertEquals(minOf(ColorMath.ratio(inks[0], light), ColorMath.ratio(inks[1], light)), w, 1e-12, "đầu tệ nhất quyết")
        // Lớp không mờ thì hệ số không đổi gì.
        assertEquals(w, ChromeStack.worst(Stack(listOf(gradient), inks), 0.4, bg), 1e-12)
    }

    @Test
    fun `floor tra f khi khong rang buoc, tang khi nen lo ra lam hong chu, khong bi keo boi cap hom nay da hut`() {
        // Thanh mờ trên nền tối: chữ sáng càng trong càng dễ đọc ⇒ không ràng buộc.
        val onBar = listOf(Stack(listOf(Layer(intArrayOf(bar), fades = true)), inks))
        assertEquals(0.4, ChromeStack.floor(0.4, onBar, listOf(bg)), 1e-12)
        assertEquals(1.0, ChromeStack.floor(1.0, onBar, listOf(bg)))
        // Nền xám vừa lộ ra ⇒ hôm nay (60 %) chữ còn đạt, ở 40 % thì hụt ⇒ phải giữ đục hơn 40 %.
        val brightGround = c("#555555")
        assertTrue(ChromeStack.worst(onBar[0], 1.0, brightGround) >= ChromeStack.FLOOR, "tiền đề: hôm nay đạt")
        assertTrue(ChromeStack.worst(onBar[0], 0.4, brightGround) < ChromeStack.FLOOR, "tiền đề: 40 % hụt")
        val t = ChromeStack.floor(0.4, onBar, listOf(brightGround))
        assertTrue(t > 0.4, "nền sáng lộ ra phải nâng sàn: $t")
        assertTrue(ChromeStack.worst(onBar[0], t, brightGround) >= ChromeStack.FLOOR || t == 1.0)
        // Cặp hôm nay đã hụt (mực tối trên nền tối) KHÔNG kéo sàn.
        val hopeless = Stack(listOf(Layer(intArrayOf(bar), fades = true)), intArrayOf(c("#202020")))
        assertEquals(0.4, ChromeStack.floor(0.4, listOf(hopeless), listOf(bg)), 1e-12)
    }

    /** Thanh trên ảnh: vùng TỆ NHẤT quyết; ô có chồng hôm nay đã hụt ⇒ giữ đúng [base]; rỗng ⇒ null. */
    @Test
    fun `can toi thieu cua thanh tren anh`() {
        val opaque = ColorMath.withAlpha(bar, 255)
        val base = 0x99 / 255.0
        assertNull(ChromeStack.neededOver(DoubleArray(0), opaque, base, listOf(text), 1.0))
        assertEquals(0.0, ChromeStack.neededOver(doubleArrayOf(0.0), opaque, base, listOf(text), 1.0)!!, 1e-9, "ảnh đen: được trong hẳn")
        val dim = ChromeStack.neededOver(doubleArrayOf(0.15), opaque, base, listOf(text), 1.0)!!
        assertTrue(dim > 0.0 && dim <= base, "ảnh tối vừa: cần một ít ($dim)")
        val mixed = ChromeStack.neededOver(doubleArrayOf(0.0, 0.15), opaque, base, listOf(text), 1.0)!!
        assertEquals(dim, mixed, 1e-12, "ô tệ nhất quyết")
        // Ảnh trắng: hôm nay (60 %) chữ đã hụt ⇒ giữ đúng base, không làm tệ thêm.
        assertTrue(ChromeStack.worst(text, 1.0, ColorMath.over(bar, c("#ffffff"))) < ChromeStack.FLOOR)
        assertEquals(base, ChromeStack.neededOver(doubleArrayOf(1.0), opaque, base, listOf(text), 1.0)!!, 1e-12)
        // Kết quả tìm được thật sự đạt sàn.
        val g = ColorMath.over(ColorMath.withAlpha(opaque, (dim * 255).toInt()), ColorMath.grayOfLuminance(0.15))
        assertTrue(ChromeStack.worst(text, 1.0, g) >= ChromeStack.FLOOR)
    }
}
