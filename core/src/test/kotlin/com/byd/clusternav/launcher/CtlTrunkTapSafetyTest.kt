package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.CtlSafetyPolicy.Motion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 · owner 13/10 *"Chặn MỞ cốp khi xe chạy (Khuyên dùng)"* + *"làm theo luật mới hết đi"* — khoá phép DUY NHẤT
 * [CtlSafetyPolicy.blocked] (chạm · giọng nói · phím): chỉ MỞ cốp bị chặn; chỉ cho mở khi CHỨNG MINH được xe đứng (tốc độ = 0,
 * hoặc không tốc độ mà số = P); không biết gì ⇒ chặn (fail-safe).
 */
class CtlTrunkTapSafetyTest {

    private fun blocked(arg: Int, kmh: Int?, gear: String?, id: String = "trunk") =
        CtlSafetyPolicy.blocked(id, arg) { Motion(kmh, gear) }

    @Test fun `mo cop - bang quyet dinh day du`() {
        assertTrue(blocked(1, 112, "D"), "đang chạy ⇒ chặn")
        assertTrue(blocked(1, 5, "P"), "tốc độ > 0 thắng số P (số có thể đọc trễ)")
        assertFalse(blocked(1, 0, "D"), "tốc độ đọc được 0 ⇒ cho mở")
        assertFalse(blocked(1, 0, null), "tốc độ 0, không số ⇒ cho mở")
        assertFalse(blocked(1, null, "P"), "không tốc độ, số P ⇒ cho mở")
        assertFalse(blocked(1, null, "3"), "mã thô HAL GEAR_P = 3 ⇒ P")
        assertTrue(blocked(1, null, "D"), "không tốc độ, số D ⇒ chặn")
        assertTrue(blocked(1, null, "2"), "mã thô GEAR_D = 2 ⇒ chặn")
        assertTrue(blocked(1, null, null), "không biết gì ⇒ chặn (fail-safe)")
        assertTrue(blocked(1, null, "255"), "GEAR_INVALID ⇒ không biết ⇒ chặn")
    }

    @Test fun `dong cop va nut ngoai tap khong bao gio bi chan, khong ton luot doc`() {
        var reads = 0
        val m = { reads++; Motion(120, "D") }
        assertFalse(CtlSafetyPolicy.blocked("trunk", CtlSafetyPolicy.STATIONARY_SAFE_ARG, m), "ĐÓNG luôn được")
        assertFalse(CtlSafetyPolicy.blocked("win_lf", 1, m), "kính không gate")
        assertFalse(CtlSafetyPolicy.blocked("readl", 1, m), "đèn không gate")
        assertEquals(0, reads, "đọc chuyển động LƯỜI: chỉ khi MỞ một nút trong tập")
        assertTrue(CtlSafetyPolicy.blocked("trunk", 1, m))
        assertEquals(1, reads)
    }

    @Test fun `ma so - P R N D va ma tho`() {
        assertEquals(true, CtlSafetyPolicy.parked("p"))
        assertEquals(false, CtlSafetyPolicy.parked("R"))
        assertEquals(false, CtlSafetyPolicy.parked("0"))
        assertEquals(null, CtlSafetyPolicy.parked(""))
        assertEquals(null, CtlSafetyPolicy.parked("x"))
    }

    /** Tập bị chặn = đúng [CtlSafetyPolicy.REQUIRES_STATIONARY] (một nguồn cho cả ba đường), không danh sách riêng. */
    @Test fun `tap bi chan dung bang REQUIRES_STATIONARY`() {
        ControlRegistry.ALL.forEach { def ->
            assertEquals(CtlSafetyPolicy.requiresStationary(def.id), CtlSafetyPolicy.blocked(def.id, 1) { Motion(null, null) },
                "'${def.id}': chặn khi không biết ⇔ thuộc tập")
        }
    }
}
