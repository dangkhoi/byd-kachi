package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá HỢP ĐỒNG của [SurfaceRamp] (2026-09-17) — thang bề mặt derive từ recipe, thay 16 hex đặt tay.
 *
 * Bài này canh **tính chất** của thang (đơn điệu theo độ cao, nền = bậc 0, alpha áp đúng), không canh một mã màu
 * cụ thể — đúng lẽ IA: mood đổi thì mã đổi, nhưng tính chất "cao hơn = sáng hơn" phải giữ, nếu không thẻ hết nổi.
 */
class SurfaceRampTest {

    // Recipe TỐI thu nhỏ: base tối, lift sáng, sink tối hơn.
    private val ramp = SurfaceRamp.of(base = "#141b30", lift = "#c6d0ff", sink = "#080b16", step = 0.04)

    private fun lum(hex: String) = ColorMath.luminance(ColorMath.parse(hex))

    @Test fun `bac 0 la base`() {
        assertEquals("#141b30", ramp.at(0))
    }

    @Test fun `cao hon thi sang hon, thap hon thi toi hon`() {
        // Đây là tính chất mà mọi thẻ/ô lõm dựa vào để nổi/lõm; đảo là hỏng cả chiều sâu của màn.
        val ladder = listOf(-2, -1, 0, 1, 2, 3, 4, 5)
        val lums = ladder.map { lum(ramp.at(it)) }
        lums.zipWithNext().forEach { (lo, hi) ->
            assertTrue(hi > lo, "thang không đơn điệu: $lums")
        }
    }

    @Test fun `alpha ap dung ma khong doi sac`() {
        val opaque = ramp.at(1)                 // #rrggbb
        val translucent = ramp.at(1, 0xd9)      // #d9rrggbb
        assertTrue(translucent.startsWith("#d9"), "phải mang alpha d9: $translucent")
        assertEquals(opaque.removePrefix("#"), translucent.removePrefix("#d9"), "cùng RGB, chỉ khác alpha")
    }

    @Test fun `bac cao khong hoa trang tinh`() {
        // Kẹp ≤ 0.98 để bậc cao nhất còn chỗ cho viền/chữ — không được trùng lift.
        assertTrue(ramp.at(99) != "#c6d0ff", "bậc rất cao không được hoá đúng màu lift (mất chỗ cho viền/chữ)")
    }
}
