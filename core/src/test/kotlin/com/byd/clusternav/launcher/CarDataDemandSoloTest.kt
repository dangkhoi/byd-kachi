package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * ═══ FIX286 · R-KC — đọc TƯƠI một datum khi màn chính khuất (cổng tốc độ của cốp qua phím) ════════════════════════
 *
 * Màn chính `onStop` ⇒ vòng poll dừng + nhu cầu `null` ⇒ ảnh chụp cũ. [CarDataDemand.Holder.withSoloIfIdle] cho đúng
 * MỘT lượt đọc chạm đúng datum cần, rồi trả mọi thứ về như cũ — kể cả khi lượt đọc ném.
 */
class CarDataDemandSoloTest {

    @Test
    fun `man khuat thi luot doc chi cham dung datum can, xong tra ve null`() {
        val h = CarDataDemand.Holder()
        val seen = h.withSoloIfIdle(setOf("speed")) { h.get() }
        assertEquals(setOf("speed"), seen, "lượt đọc phải chạm ĐÚNG datum cần — không phải 'đọc hết' (null)")
        assertNull(h.get(), "xong phải trả nhu cầu về null như lúc màn khuất")
    }

    @Test
    fun `man dang cong bo nhu cau thi khong chay, khong dung toi nhu cau`() {
        val h = CarDataDemand.Holder()
        h.set(setOf("soc"))
        var ran = false
        assertNull(h.withSoloIfIdle(setOf("speed")) { ran = true; 1 })
        assertEquals(false, ran, "màn đang poll ⇒ đường refreshForRead của màn lo, không đọc thêm")
        assertEquals(setOf("soc"), h.get())
    }

    @Test
    fun `luot doc nem thi van tra nhu cau ve null`() {
        val h = CarDataDemand.Holder()
        assertThrows<IllegalStateException> { h.withSoloIfIdle(setOf("speed")) { error("HAL hỏng") } }
        assertNull(h.get(), "ném mà không trả về null ⇒ vòng poll lần sau chỉ đọc một tập rỗng")
    }

    @Test
    fun `man kip cong bo nhu cau trong luc doc thi khong bi ghi de`() {
        val h = CarDataDemand.Holder()
        h.withSoloIfIdle(setOf("speed")) { h.set(setOf("soc", "speed")) }
        assertEquals(setOf("soc", "speed"), h.get(), "nhu cầu thật của màn vừa mở không được bị xoá về null")
    }
}
