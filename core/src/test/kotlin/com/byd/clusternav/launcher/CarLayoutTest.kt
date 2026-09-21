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
