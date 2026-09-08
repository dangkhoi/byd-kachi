package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** ManeuverHold — hysteresis chống nháy HUD: giữ hướng rẽ hợp lệ gần nhất khi 1 frame lỡ không phân loại. */
class ManeuverHoldTest {

    @Test fun `fresh hợp lệ luôn thắng (kể cả có last)`() {
        assertEquals(5, ManeuverHold.resolve(fresh = 5, last = -1))
        assertEquals(2, ManeuverHold.resolve(fresh = 2, last = 8))   // hướng rẽ đổi thật → theo fresh, không dính last
        assertEquals(9, ManeuverHold.resolve(fresh = 9, last = 3))
    }

    @Test fun `frame lỗi đọc (fresh null) → GIỮ hướng rẽ trước, KHÔNG rớt straight`() {
        assertEquals(8, ManeuverHold.resolve(fresh = null, last = 8))    // đây là ca chống nháy chính
        assertEquals(11, ManeuverHold.resolve(fresh = null, last = 11))
        assertEquals(2, ManeuverHold.resolve(fresh = null, last = 2))
    }

    @Test fun `chưa có hướng nào (last -1) và frame lỗi → -1 (caller coi như chưa có maneuver)`() {
        assertEquals(-1, ManeuverHold.resolve(fresh = null, last = -1))
    }

    @Test fun `fresh ngoài dải 0-28 (phòng thủ) → giữ last hợp lệ`() {
        assertEquals(7, ManeuverHold.resolve(fresh = 99, last = 7))
        assertEquals(-1, ManeuverHold.resolve(fresh = 99, last = -1))
    }

    @Test fun `last ngoài dải bị bỏ qua`() {
        assertEquals(-1, ManeuverHold.resolve(fresh = null, last = 99))
        assertEquals(4, ManeuverHold.resolve(fresh = 4, last = 99))
    }
}
