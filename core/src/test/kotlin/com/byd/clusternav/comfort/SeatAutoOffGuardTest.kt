package com.byd.clusternav.comfort

import com.byd.clusternav.comfort.SeatAutoOffGuard.Reading
import com.byd.clusternav.comfort.SeatAutoOffGuard.Signature
import com.byd.clusternav.comfort.SeatAutoOffGuard.Verdict
import com.byd.clusternav.comfort.SeatComfort.SeatMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.6 · SEAT-AUTO-OFF-REAPPLY — khoá luật "xe tự tắt ghế ⇒ bật lại MỘT lần" (`docs/diagnostics/seat-vent-auto-off-2026-10-14.md`
 * §13). Dựng theo mẫu THẬT 15/10 (§12.1–§12.2): lên nguồn 07:42:09.7 (t = 0), `SeatComfort` ghi mát ghế lái = 3 ở +16,8 s, xe
 * tắt (`0x4fa00008 = 1`) ở +549,5 s (+9,2′), sưởi ghế lái = 1 suốt. Mỗi bài đỏ khi gỡ đúng một nhánh luật.
 */
class SeatAutoOffGuardTest {

    private val sec = 1_000L
    private val min = 60_000L
    private val on = Reading(heat = 1, vent = 3)
    private val off = Reading(heat = 1, vent = 1)

    /** Ghế lái, mát mức 3, đã thấy bật ở +60 s. */
    private fun armed(): SeatAutoOffGuard = SeatAutoOffGuard().apply {
        arm(seatIndex = 0, mode = SeatMode.COOL, state = 3, poweredAtMs = 0, armedAtMs = 17 * sec)
        assertEquals(Verdict.Keep, observe(0, 60 * sec, on, active = true, signature = Signature.UNOBSERVABLE))
    }

    private fun SeatAutoOffGuard.drop(at: Long, sig: Signature, reading: Reading = off, active: Boolean = true): List<Verdict> =
        listOf(observe(0, at, reading, active, sig), observe(0, at + 10 * sec, reading, active, sig))

    private fun List<Verdict>.reapplies() = filterIsInstance<Verdict.Reapply>()

    @Test
    fun `tat co dau 0x095 thi bat lai dung mot lan, cung che do va muc`() {
        val v = armed().drop(549 * sec, Signature.SEEN)
        assertEquals(Verdict.Keep, v[0], "lượt tắt đầu chỉ là nghi (chống đọc rách)")
        val r = v.reapplies().single()
        assertEquals(0, r.seatIndex)
        assertEquals(SeatMode.COOL, r.mode)
        assertEquals(3, r.state)
        assertEquals(549 * sec, r.dropAtMs, "mốc tắt = lượt đọc tắt ĐẦU TIÊN")
        assertEquals(Signature.SEEN, r.signature)
    }

    @Test
    fun `dau 0x095 co duong nghe ma khong thay thi khong bat lai`() {
        val g = armed()
        val v = g.drop(549 * sec, Signature.NOT_SEEN)
        assertTrue(v.reapplies().isEmpty())
        assertTrue(v[1] is Verdict.Disarm)
        assertTrue(g.armedSeats().isEmpty())
    }

    @Test
    fun `khong quan sat duoc dau thi theo cua so - tat o 9_2 phut bat lai`() {
        assertEquals(1, armed().drop(549 * sec, Signature.UNOBSERVABLE).reapplies().size)
    }

    @Test
    fun `lan tat thu hai khong bat lai nua`() {
        val g = armed()
        assertEquals(1, g.drop(549 * sec, Signature.SEEN).reapplies().size)
        // bật lại xong xe lên 3, rồi lại tắt lần hai
        val later = listOf(g.observe(0, 580 * sec, on, true, Signature.SEEN)) + g.drop(11 * min, Signature.SEEN)
        assertTrue(later.reapplies().isEmpty(), "một lần mỗi ghế mỗi lần lên nguồn")
        assertNull(g.nextDelayMs(11 * min), "hết ghế canh ⇒ luồng canh thoát")
    }

    @Test
    fun `chuc nang kia bat thi thoi canh, khong bat lai`() {
        val g = armed()
        val took = g.observe(0, 5 * min, Reading(heat = 2, vent = 1), true, Signature.SEEN)
        assertTrue(took is Verdict.Disarm)
        assertTrue(g.drop(549 * sec, Signature.SEEN).reapplies().isEmpty())
    }

    @Test
    fun `chuc nang kia khong doc duoc thi khong dam bat lai`() {
        val v = armed().drop(549 * sec, Signature.SEEN, reading = Reading(heat = null, vent = 1))
        assertTrue(v.reapplies().isEmpty())
    }

    @Test
    fun `Kachi ghi ghe do sau luc ap thi thoi canh`() {
        val g = armed()
        assertTrue(g.onKachiWrite(0, 7 * min) is Verdict.Disarm)
        assertTrue(g.drop(549 * sec, Signature.SEEN).reapplies().isEmpty())
    }

    @Test
    fun `lenh ghi truoc luc ap khong tinh`() {
        val g = armed()
        assertEquals(Verdict.Keep, g.onKachiWrite(0, 10 * sec))
        assertEquals(1, g.drop(549 * sec, Signature.SEEN).reapplies().size)
    }

    @Test
    fun `Kachi ghi ghe khac khong dung ghe nay`() {
        val g = armed()
        assertEquals(Verdict.Keep, g.onKachiWrite(1, 7 * min))
        assertEquals(1, g.drop(549 * sec, Signature.SEEN).reapplies().size)
    }

    @Test
    fun `ngoai cua so - tat som truoc 6 phut thi khong bat lai`() {
        val g = armed()
        val v = g.drop(4 * min, Signature.UNOBSERVABLE)
        assertTrue(v.reapplies().isEmpty())
        assertTrue(v[1] is Verdict.Disarm)
        assertTrue(g.drop(9 * min, Signature.UNOBSERVABLE).reapplies().isEmpty(), "đã thôi canh")
    }

    @Test
    fun `qua 15 phut thi het han, khong bat lai`() {
        val g = armed()
        val v = g.drop(15 * min + sec, Signature.SEEN)
        assertTrue(v.reapplies().isEmpty())
        assertTrue(v[0] is Verdict.Disarm)
    }

    @Test
    fun `mot luot doc tat roi lai bat thi khong bat lai`() {
        val g = armed()
        assertEquals(Verdict.Keep, g.observe(0, 549 * sec, off, true, Signature.SEEN))
        assertEquals(Verdict.Keep, g.observe(0, 559 * sec, on, true, Signature.SEEN))
        assertEquals(Verdict.Keep, g.observe(0, 569 * sec, off, true, Signature.SEEN), "nghi lại từ đầu")
    }

    @Test
    fun `chua tung thay muc da ap thi tat khong phai bi tat`() {
        val g = SeatAutoOffGuard().apply { arm(0, SeatMode.COOL, 3, 0, 17 * sec) }
        assertTrue(g.drop(549 * sec, Signature.SEEN).reapplies().isEmpty())
    }

    @Test
    fun `man hoac xe tat thi khong bat lai`() {
        val v = armed().drop(549 * sec, Signature.SEEN, active = false)
        assertTrue(v.reapplies().isEmpty())
        assertTrue(v[0] is Verdict.Disarm)
    }

    @Test
    fun `nguoi dung doi muc khac thi thoi canh`() {
        val g = armed()
        assertTrue(g.observe(0, 3 * min, Reading(heat = 1, vent = 2), true, Signature.SEEN) is Verdict.Disarm)
        assertTrue(g.drop(549 * sec, Signature.SEEN).reapplies().isEmpty())
    }

    @Test
    fun `muc Tat khong duoc canh`() {
        val g = SeatAutoOffGuard().apply { arm(0, SeatMode.COOL, SeatComfort.STATE_OFF, 0, 17 * sec) }
        assertTrue(g.armedSeats().isEmpty())
        assertNull(g.nextDelayMs(sec))
    }

    @Test
    fun `suoi ghe phu dung chuc nang suoi de xet`() {
        val g = SeatAutoOffGuard().apply { arm(1, SeatMode.HEAT, 2, 0, 17 * sec) }
        g.observe(1, 60 * sec, Reading(heat = 2, vent = 1), true, Signature.UNOBSERVABLE)
        val v = listOf(
            g.observe(1, 9 * min, Reading(heat = 1, vent = 1), true, Signature.UNOBSERVABLE),
            g.observe(1, 9 * min + 10 * sec, Reading(heat = 1, vent = 1), true, Signature.UNOBSERVABLE),
        )
        assertEquals(SeatMode.HEAT, v.reapplies().single().mode)
    }

    @Test
    fun `nhip doc thua truoc cua so, day trong cua so, dung o 15 phut`() {
        val g = armed()
        assertEquals(SeatAutoOffGuard.COARSE_PERIOD_MS, g.nextDelayMs(2 * min))
        assertEquals(SeatAutoOffGuard.FINE_PERIOD_MS, g.nextDelayMs(6 * min))
        assertEquals(5 * sec, g.nextDelayMs(15 * min - 5 * sec), "không ngủ quá trần")
        assertNull(g.nextDelayMs(15 * min))
        assertNull(SeatAutoOffGuard().nextDelayMs(0))
    }

    @Test
    fun `reset quen ghe cua lan len nguon truoc`() {
        val g = armed()
        g.reset()
        assertTrue(g.armedSeats().isEmpty())
    }

    @Test
    fun `cong nguon, ma khung va moc len nguon`() {
        assertTrue(SeatAutoOffGuard.carOn(2))
        assertTrue(SeatAutoOffGuard.carOn(3))
        assertTrue(SeatAutoOffGuard.carOn(null), "không đọc được ⇒ để cổng màn quyết")
        assertFalse(SeatAutoOffGuard.carOn(0))
        assertFalse(SeatAutoOffGuard.carOn(1))
        assertFalse(SeatAutoOffGuard.carOn(4))
        assertEquals(3, SeatAutoOffGuard.raw(" 3 "))
        assertNull(SeatAutoOffGuard.raw("-2147482645"))
        assertNull(SeatAutoOffGuard.raw("null"))
        assertEquals(500L, SeatAutoOffGuard.poweredAt(500, 1_000))
        assertEquals(1_000L, SeatAutoOffGuard.poweredAt(-1, 1_000))
        assertEquals(1_000L, SeatAutoOffGuard.poweredAt(2_000, 1_000))
    }

    @Test
    fun `nut ghe suy tu registry - lai la 0, phu la 1, nut khac null`() {
        assertEquals(0, SeatControls.seatIndexOf("seatc"))
        assertEquals(0, SeatControls.seatIndexOf("seath"))
        assertEquals(1, SeatControls.seatIndexOf("seatc_r"))
        assertEquals(1, SeatControls.seatIndexOf("seath_r"))
        assertNull(SeatControls.seatIndexOf("fan"))
        assertNull(SeatControls.seatIndexOf("pm25"))
        assertEquals("seat_vent_state", SeatControls.datumOf(0, SeatMode.COOL))
        assertEquals("seat_heat_state_r", SeatControls.datumOf(1, SeatMode.HEAT))
        assertNull(SeatControls.datumOf(2, SeatMode.COOL), "Seal không có nút ghế sau")
    }
}
