package com.byd.clusternav.modules.navaccess

import com.byd.clusternav.modules.navaccess.KeyReadyPlan.KeyStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * READY-AT-HOME §4.8 — kiểm phím vô-lăng trong chuỗi chuẩn bị: hỏi binder trước (0 lệnh shell), chờ lớp 2 (2.83) kết
 * luận trước khi cấp/gắn ô, và không có đường chữa thứ hai. Mốc xe dùng trong ca "lớp 2 sắp xét": [ĐO c2 29/09]
 * `on_restart` 11:34:13.153 đi TRƯỚC `power_screen_state` 11:34:14.236 — màn chính có thể nhận kênh trước khi bộ thu
 * màn bật của lớp 2 kịp nghe.
 */
class KeyReadyPlanTest {

    @Test
    fun `phim-thoai tat thi khong lam gi, da gan thi khong mot lenh shell`() {
        assertEquals(KeyStep.OFF, KeyReadyPlan.step(false, null, layer2Pending = true, waitedLayer2 = false, bornFromOwnHeal = false))
        assertEquals(KeyStep.BOUND, KeyReadyPlan.step(true, true, layer2Pending = true, waitedLayer2 = false, bornFromOwnHeal = false))
    }

    @Test
    fun `chua gan ma lop 2 dang xet thi CHO truoc, roi moi cap`() {
        assertEquals(KeyStep.WAIT_LAYER2, KeyReadyPlan.step(true, false, layer2Pending = true, waitedLayer2 = false, bornFromOwnHeal = false))
        assertEquals(KeyStep.GRANT, KeyReadyPlan.step(true, false, layer2Pending = true, waitedLayer2 = true, bornFromOwnHeal = false),
            "đã chờ hết trần ⇒ fail-open, cấp như B1")
        assertEquals(KeyStep.GRANT, KeyReadyPlan.step(true, null, layer2Pending = false, waitedLayer2 = false, bornFromOwnHeal = false),
            "binder không trả lời ⇒ coi như chưa gắn (đúng grantOrSkip)")
        assertEquals(KeyStep.SKIP_OWN_HEAL, KeyReadyPlan.step(true, false, false, false, bornFromOwnHeal = true),
            "tiến trình sinh từ chính lượt chữa ⇒ không toggle chồng lên lượt chữa vừa bắn")
    }

    @Test
    fun `o chi cho khi lop 2 dang xet MA phim chua gan`() {
        assertTrue(KeyReadyPlan.holdTile(true, false, layer2Pending = true))
        assertTrue(KeyReadyPlan.holdTile(true, null, layer2Pending = true))
        assertFalse(KeyReadyPlan.holdTile(true, true, layer2Pending = true), "ca thường (đã gắn): 0 ms chờ")
        assertFalse(KeyReadyPlan.holdTile(true, false, layer2Pending = false))
        assertFalse(KeyReadyPlan.holdTile(false, false, layer2Pending = true))
    }

    @Test
    fun `lop 2 sap xet - tien trinh bat luc man tat, chua thay man bat, lan bat ke la mo xe`() {
        val tatMay = 1_000_000L
        val moXeTruoc = 500_000L
        val now = 1_060_000L
        assertTrue(KeyReadyPlan.layer2Expected(true, false, true, tatMay, moXeTruoc, now))
        assertFalse(KeyReadyPlan.layer2Expected(false, false, true, tatMay, moXeTruoc, now), "tiến trình bật lúc màn SÁNG ⇒ không có lớp 2")
        assertFalse(KeyReadyPlan.layer2Expected(true, true, true, tatMay, moXeTruoc, now), "đã thấy màn bật ⇒ cờ bận của lớp 2 lo")
        assertFalse(KeyReadyPlan.layer2Expected(true, false, false, tatMay, moXeTruoc, now))
        assertFalse(KeyReadyPlan.layer2Expected(true, false, true, tatMay, moXeAt(tatMay + 1), now), "đã claim mở xe sau tắt máy")
    }

    /**
     * [ĐO máy ảo 02/10 01:45:25.824] lượt cấp của chuỗi kiểm phím THUA single-flight ⇒ `keys=GRANT->NOT_BOUND`, lượt thắng
     * ghi `đã BOUND` lúc 27.523. Cửa sổ hỏi binder sau NOT_BOUND phải phủ trọn một lượt toggle của lượt thắng (settle 1,2 s
     * + toggle 0,8 s + 6 lượt đọc cách 1 s ≈ 8 s), và chỉ hỏi binder thưa (0 lệnh shell mỗi nhịp).
     */
    @Test
    fun `cua so xac nhan gan muon phu tron mot luot toggle`() {
        assertTrue(KeyReadyPlan.LATE_BIND_WATCH_MS >= 1_200L + 800L + 6 * 1_000L, "ngắn hơn lượt toggle ⇒ nhãn chốt NOT_BOUND oan")
        assertTrue(KeyReadyPlan.LATE_BIND_WATCH_MS <= 30_000L, "không dài hơn hạn của chính lượt cấp (GRANT_TIMEOUT_MS)")
        assertTrue(KeyReadyPlan.LATE_BIND_POLL_MS >= 500L, "binder hỏi thưa, không bận vòng")
    }

    private fun moXeAt(v: Long) = v
}
