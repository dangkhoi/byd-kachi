package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP3-v5 — khoá NEO hình xe: mọi neo trong [0,1] · trái/phải đối xứng · đầu trên đuôi dưới · không hai bộ phận
 * cùng CHỖ (chấm chồng nhau đọc thành một). Thuần `:core`, kiểm off-car.
 */
class CarLayoutTest {

    @Test
    fun `moi neo nam trong khung anh`() {
        (CarPart.values().map { CarLayout.part(it) } + TyreCorner.values().map { CarLayout.wheel(it) }).forEach {
            assertTrue(it.x in 0f..1f && it.y in 0f..1f, "neo ngoài khung: $it")
        }
    }

    @Test
    fun `trai phai doi xung quanh truc giua`() {
        fun mirror(a: CarAnchor, b: CarAnchor) {
            assertEquals(a.x, 1f - b.x, 1e-4f, "cặp trái/phải không đối xứng: $a / $b")
            assertEquals(a.y, b.y, 1e-4f, "cặp trái/phải phải cùng hàng: $a / $b")
        }
        mirror(CarLayout.part(CarPart.DOOR_LF), CarLayout.part(CarPart.DOOR_RF))
        mirror(CarLayout.part(CarPart.DOOR_LR), CarLayout.part(CarPart.DOOR_RR))
        mirror(CarLayout.wheel(TyreCorner.FRONT_LEFT), CarLayout.wheel(TyreCorner.FRONT_RIGHT))
        mirror(CarLayout.wheel(TyreCorner.REAR_LEFT), CarLayout.wheel(TyreCorner.REAR_RIGHT))
    }

    @Test
    fun `dau xe o tren, duoi xe o duoi`() {
        // Cửa trước cao hơn cửa sau; bánh trước cao hơn bánh sau; cốp ở dưới cùng.
        assertTrue(CarLayout.part(CarPart.DOOR_LF).y < CarLayout.part(CarPart.DOOR_LR).y)
        assertTrue(CarLayout.wheel(TyreCorner.FRONT_LEFT).y < CarLayout.wheel(TyreCorner.REAR_LEFT).y)
        assertTrue(CarLayout.part(CarPart.TAILGATE).y > CarLayout.part(CarPart.SUNROOF).y)
    }

    /**
     * OQ7 (2.76 · R11) — khối 4 thẻ lốp phải có tâm = tâm ảnh (0,50). Neo bánh 0,26/0,80 ⇒ tâm dải 0,53 ⇒ dịch −0,03.
     * Suy từ neo (không gõ số): đổi neo bánh là số này tự theo; test ghim cả CON SỐ để ai đổi neo phải chụp lại ảnh.
     */
    @Test
    fun `khoi the lop dich de tam khoi = tam anh - suy tu neo banh`() {
        val front = CarLayout.wheel(TyreCorner.FRONT_LEFT).y
        val rear = CarLayout.wheel(TyreCorner.REAR_LEFT).y
        assertEquals(0.53f, (front + rear) / 2f, 1e-4f, "tâm dải neo bánh (cái owner thấy là 'xe nhô lên')")
        assertEquals(-0.03f, CarLayout.tyreCardShift, 1e-4f)
        val top = CarLayout.tyreCardCenterY(TyreCorner.FRONT_LEFT, 100f, 400f)
        val bottom = CarLayout.tyreCardCenterY(TyreCorner.REAR_RIGHT, 100f, 400f)
        assertEquals(300f, (top + bottom) / 2f, 0.01f, "tâm khối thẻ = tâm ảnh (100 + 400/2)")
        assertEquals(100f + 0.23f * 400f, top, 0.01f)
        assertEquals(100f + 0.77f * 400f, bottom, 0.01f)
        // Không dịch NEO: chấm cảnh báo vẫn ở bánh thật, bảng cửa / xe mini không bị kéo theo.
        assertEquals(0.26f, front, 1e-6f)
        assertEquals(0.80f, rear, 1e-6f)
    }

    @Test
    fun `khong hai bo phan cung mot cho`() {
        val all = CarPart.values().map { CarLayout.part(it) } + TyreCorner.values().map { CarLayout.wheel(it) }
        // Khoảng cách tối thiểu giữa hai neo bất kỳ (Manhattan) — đủ để hai chấm không chồng lên nhau.
        for (i in all.indices) for (j in i + 1 until all.size) {
            val d = kotlin.math.abs(all[i].x - all[j].x) + kotlin.math.abs(all[i].y - all[j].y)
            assertTrue(d > 0.03f, "hai neo trùng chỗ: ${all[i]} / ${all[j]}")
        }
    }
}
