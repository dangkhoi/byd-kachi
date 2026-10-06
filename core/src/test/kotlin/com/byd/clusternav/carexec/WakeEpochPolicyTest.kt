package com.byd.clusternav.carexec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.91 · F3 — khoá lỗi "tắt-bật màn nhanh ngay sau khởi động thì lượt kiểm điều kiện VietMap bị bỏ" (ghi chú QA 2.89 Q3;
 * spec `kachi-291-small-fixes.html` §4.3). Mô phỏng ĐÚNG hai mắt xích ở `:app` (`KachiReadyLog.wake` + `readyChain`) bằng luật
 * thuần — thử ĐỎ: bỏ nhánh `offAt in cur..at` (mắt xích 1) hoặc cho `shouldRun` bỏ qua cổng (mắt xích 2).
 */
class WakeEpochPolicyTest {

    /** Bộ mô phỏng: đúng thứ tự gọi của `KachiReadyLog.wake` / `screenOff` và `EarlyShellChannel.readyChain`. */
    private class Sim {
        var epoch = -1L
        var offAt = -1L
        var lastRun = Long.MIN_VALUE
        var runs = 0
        fun on(at: Long) { if (WakeEpochPolicy.isNewWake(epoch, offAt, at)) epoch = at }
        fun off(at: Long) { offAt = at }
        fun chain(up: Boolean, interactive: Boolean?) {
            val e = epoch
            if (!WakeEpochPolicy.shouldRun(lastRun, e, up, interactive)) return
            lastRun = e
            runs++
        }
    }

    @Test
    fun `ca QA - tin hieu cu chay muon luc man da tat, lan thuc that 8 s sau van chay chuoi SAN`() {
        val s = Sim()
        s.on(1_000)                       // HOME hiện sau khởi động (tín hiệu "cũ")
        s.off(3_000)                      // màn tắt
        s.chain(up = true, interactive = false)   // lượt xếp hàng chạy muộn: màn tắt ⇒ cổng hỏng, KHÔNG tiêu mốc
        s.on(9_000)                       // lần thức thật, < 10 s sau mốc cũ
        assertEquals(9_000, s.epoch, "màn đã tắt giữa hai tín hiệu ⇒ lần thức MỚI")
        s.chain(up = true, interactive = true)
        assertEquals(1, s.runs, "lần thức thật phải chạy chuỗi SẴN (kiểm điều kiện VietMap)")
    }

    @Test
    fun `cung mot lan thuc - HOME, broadcast, kenh len chi chay MOT luot`() {
        val s = Sim()
        s.on(1_000); s.chain(up = true, interactive = true)
        s.on(1_400); s.chain(up = true, interactive = true)      // broadcast cùng lần thức
        s.on(2_100); s.chain(up = true, interactive = true)      // kênh lên lúc màn đang bật
        assertEquals(1_000, s.epoch)
        assertEquals(1, s.runs, "chống dồn: một lượt mỗi lần thức")
    }

    @Test
    fun `tung lan thuc khac nhau deu chay - debounce theo lan tat man, khong theo dong ho`() {
        val s = Sim()
        s.on(1_000); s.chain(true, true)
        s.off(2_000); s.on(2_500); s.chain(true, true)
        s.off(4_000); s.on(4_200); s.chain(true, true)
        assertEquals(3, s.runs)
        s.on(20_000); s.chain(true, true)
        assertEquals(4, s.runs, "≥ 10 s sau mốc cũ (không thấy tắt) ⇒ vẫn là lần thức mới như trước")
    }

    @Test
    fun `cong hong khong tieu moc - kenh len sau do trong cung lan thuc thi chay`() {
        val s = Sim()
        s.on(1_000)
        s.chain(up = false, interactive = true)
        s.chain(up = true, interactive = null)
        assertEquals(0, s.runs)
        s.chain(up = true, interactive = true)
        assertEquals(1, s.runs)
        s.chain(up = true, interactive = true)
        assertEquals(1, s.runs, "đã chạy thì không chạy lại cho cùng mốc")
    }

    @Test
    fun `bang isNewWake`() {
        assertTrue(WakeEpochPolicy.isNewWake(-1, -1, 5))
        assertFalse(WakeEpochPolicy.isNewWake(1_000, -1, 1_000))
        assertFalse(WakeEpochPolicy.isNewWake(1_000, -1, 10_999))
        assertTrue(WakeEpochPolicy.isNewWake(1_000, -1, 11_000))
        assertTrue(WakeEpochPolicy.isNewWake(1_000, -1, 500), "đồng hồ lùi (mốc lạ) ⇒ mới")
        assertFalse(WakeEpochPolicy.isNewWake(1_000, 900, 2_000), "tắt TRƯỚC mốc hiện tại không tính")
        assertTrue(WakeEpochPolicy.isNewWake(1_000, 1_000, 2_000))
        assertTrue(WakeEpochPolicy.isNewWake(1_000, 2_000, 2_000))
        assertFalse(WakeEpochPolicy.isNewWake(1_000, 3_000, 2_000), "tắt SAU tín hiệu (đọc lệch thứ tự) không mở lần thức")
        assertEquals(10_000L, WakeEpochPolicy.SAME_WAKE_MS)
    }
}
