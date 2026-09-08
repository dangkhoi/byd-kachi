package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit test THUẦN cho ManeuverRegistry — INVARIANT: mỗi chữ ký PHẢI đúng 225 bit (lưới 15×15).
 * Lệch 1 bit → ManeuverSignature đóng gói lệch ô → khớp Hamming sai → mũi tên rẽ sai cho tài xế.
 */
class ManeuverRegistryTest {

    @Test fun `registry không rỗng`() {
        assertTrue(ManeuverRegistry.RAW.isNotEmpty())
    }

    @Test fun `mọi chữ ký đúng 225 bit, chỉ 0 hoặc 1`() {
        for ((bits, name) in ManeuverRegistry.RAW) {
            assertEquals(225, bits.length, "chữ ký '$name' phải 225 bit, thực tế ${bits.length}")
            assertTrue(bits.all { it == '0' || it == '1' }, "chữ ký '$name' có ký tự lạ (chỉ được 0/1)")
        }
    }

    @Test fun `mọi maneuver có tên`() {
        for ((_, name) in ManeuverRegistry.RAW) assertTrue(name.isNotBlank())
    }
}
