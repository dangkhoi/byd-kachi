package com.byd.clusternav.comfort

import com.byd.clusternav.comfort.SeatAutoOffGuard.Evidence
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
 * 3.1.12 · SEAT-AUTOOFF-ANY-ON — khoá luật "xe tự tắt ghế (dấu `0x095`) ⇒ bật lại, MỌI ghế đang bật, ai bật cũng vậy"
 * (`docs/diagnostics/seat-vent-auto-off-2026-10-14.md` §14). Dựng từ chuỗi THẬT của chuyến 17/10 (logcat toàn hệ thống, mốc tính
 * bằng giây từ 11:05:00; không có dữ liệu cá nhân):
 *  - 11:05:14.8 tiến trình 3.1.11 vừa cài đè (`adb install -r`) ⇒ đọc xe: ghế lái mát = 3, ghế phụ mát = 1, sưởi = 1 cả hai;
 *  - 11:06:09.7 / 11:06:10.9 ghế phụ mát 2 → 3 bằng ô Kachi (`KachiCtl ctl #2/#3 id=seatc_r`);
 *  - 11:08:17.2 xe tắt cả hai ghế (`0x4fa00008`/`0x4fa00010` = 1), 11:08:20.23 dấu `0x0950000F`/`0x09500022` (+3,0 s);
 *  - 11:35:39.07 ghế phụ mát → 1, KHÔNG có dấu, không có lời ghi Kachi.
 * Bản 3.1.6 không bật lại lần 11:08 (luồng canh tự thôi ở 11:05:14, ghế phụ chưa từng được canh). Mỗi bài đỏ khi gỡ đúng một nhánh.
 */
class SeatAutoOffGuardTest {

    private fun t(sec: Double): Long = (sec * 1000).toLong()

    // Mốc chuyến 17/10 (giây từ 11:05:00).
    private val passengerOn2 = t(69.76)
    private val passengerOn3 = t(70.96)
    private val driverOff = t(197.206)
    private val passengerOff = t(197.232)
    private val signature = t(200.230)
    private val passengerOff2 = t(1839.067)

    private fun evidence(
        signatureAt: Long? = signature,
        live: Boolean = true,
        kachiAt: Long? = null,
        now: Reading = Reading(heat = 1, vent = 1),
        reapplied: List<Long> = emptyList(),
    ) = Evidence(signatureLive = live, signatureAtMs = signatureAt, kachiWriteAtMs = kachiAt, now = now, reappliedAtMs = reapplied)

    /** Đúng trạng thái 17/10 tới ngay trước 11:08:17: tiến trình mới đọc xe, rồi ghế phụ bật bằng ô. */
    private fun trip(): SeatAutoOffGuard = SeatAutoOffGuard().apply {
        seed(0, Reading(heat = 1, vent = 3))
        seed(1, Reading(heat = 1, vent = 1))
        assertEquals(Verdict.Keep, onEvent(1, SeatMode.COOL, 2, passengerOn2))
        assertEquals(Verdict.Keep, onEvent(1, SeatMode.COOL, 3, passengerOn3))
    }

    private fun SeatAutoOffGuard.off(seat: Int, at: Long, mode: SeatMode = SeatMode.COOL): Verdict.DecideAt =
        onEvent(seat, mode, 1, at) as Verdict.DecideAt

    @Test
    fun `17-10 - ghe nguoi dung bat va ghe doc tu xe sau cai de, xe tat kem dau 0x095 thi bat lai ca hai`() {
        val g = trip()
        val d0 = g.off(0, driverOff)
        val d1 = g.off(1, passengerOff)
        assertEquals(driverOff + SeatAutoOffGuard.SIGNATURE_LAG_MS, d0.atMs, "quyết +8 s sau lần tắt (dấu tới +3 s)")
        val r0 = g.decide(0, d0.atMs, evidence()) as Verdict.Reapply
        val r1 = g.decide(1, d1.atMs, evidence()) as Verdict.Reapply
        assertEquals(Triple(0, SeatMode.COOL, 3), Triple(r0.seatIndex, r0.mode, r0.state), "ghế lái: mức đọc từ xe lúc tiến trình bật")
        assertEquals(Triple(1, SeatMode.COOL, 3), Triple(r1.seatIndex, r1.mode, r1.state), "ghế phụ: bật bằng ô Kachi, vẫn được canh")
        assertEquals(3024L, r0.signatureLagMs)
    }

    @Test
    fun `17-10 11h35 - ghe phu tat khong co dau 0x095 thi KHONG bat lai`() {
        val g = trip()
        g.decide(1, g.off(1, passengerOff).atMs, evidence())                          // lần 11:08 đã bật lại ⇒ xe báo bật lại
        assertEquals(Verdict.Keep, g.onEvent(1, SeatMode.COOL, 3, passengerOff + t(1.0)))
        val d = g.off(1, passengerOff2)
        val v = g.decide(1, d.atMs, evidence(reapplied = listOf(passengerOff + 1)))   // dấu gần nhất là của 11:08:20
        assertTrue(v is Verdict.Skip && v.reason.contains("không có dấu"), "$v")
    }

    @Test
    fun `nguoi dung tat qua Kachi (dau ghi) thi khong bat lai du co dau`() {
        val g = trip()
        val d = g.off(0, driverOff)
        val v = g.decide(0, d.atMs, evidence(kachiAt = driverOff + 30))   // dấu ghi sau lúc ghi trả, ~cùng lúc sự kiện
        assertTrue(v is Verdict.Skip && v.reason.contains("Kachi"), "$v")
        val g2 = trip()
        val d2 = g2.off(1, passengerOff)
        assertTrue(g2.decide(1, d2.atMs, evidence(kachiAt = passengerOn3)) is Verdict.Reapply, "lời ghi BẬT 2 phút trước không phải lời tắt")
    }

    @Test
    fun `khoi dong lai tien trinh luc ghe dang bat - van canh, doc tu xe`() {
        val fresh = SeatAutoOffGuard()                  // tiến trình mới: RAM trắng
        fresh.seed(0, Reading(heat = 1, vent = 3))
        assertEquals(SeatMode.COOL to 3, fresh.onState(0))
        assertTrue(fresh.decide(0, fresh.off(0, driverOff).atMs, evidence()) is Verdict.Reapply)
        // không đọc lại xe thì không biết ghế bật ⇒ không có gì để bật lại (bằng chứng: bỏ seed là bài trên đỏ)
        assertEquals(Verdict.Keep, SeatAutoOffGuard().onEvent(0, SeatMode.COOL, 1, driverOff))
    }

    @Test
    fun `doc lai xe thay Tat thi quen muc cu, khong doc duoc thi giu`() {
        val g = trip()
        g.seed(0, Reading(heat = null, vent = null))
        assertEquals(SeatMode.COOL to 3, g.onState(0), "đọc hỏng ⇒ giữ")
        g.seed(0, Reading(heat = 1, vent = 1))
        assertNull(g.onState(0))
        assertEquals(Verdict.Keep, g.onEvent(0, SeatMode.COOL, 1, driverOff), "ghế đã tắt theo xe ⇒ sự kiện tắt không phải bị tắt")
        g.seed(0, Reading(heat = 2, vent = 1))
        assertEquals(SeatMode.HEAT to 2, g.onState(0))
    }

    @Test
    fun `khong quan sat duoc dau thi khong doan`() {
        val g = trip()
        val v = g.decide(0, g.off(0, driverOff).atMs, evidence(live = false))
        assertTrue(v is Verdict.Skip && v.reason.contains("không quan sát"), "$v")
    }

    @Test
    fun `chuc nang kia dang bat hoac ghe da bat lai thi khong bat lai`() {
        val g = trip()
        val v = g.decide(0, g.off(0, driverOff).atMs, evidence(now = Reading(heat = 2, vent = 1)))
        assertTrue(v is Verdict.Skip && v.reason.contains("chức năng kia"), "$v")
        val g2 = trip()
        val v2 = g2.decide(0, g2.off(0, driverOff).atMs, evidence(now = Reading(heat = 1, vent = 2)))
        assertTrue(v2 is Verdict.Skip && v2.reason.contains("đã bật lại"), "$v2")
    }

    @Test
    fun `bat lai truoc han quyet thi huy, tat chuc nang von khong bat thi khong canh`() {
        val g = trip()
        val d = g.off(0, driverOff)
        assertEquals(Verdict.Keep, g.onEvent(0, SeatMode.COOL, 2, driverOff + t(2.0)))
        assertEquals(Verdict.Keep, g.decide(0, d.atMs, evidence()))
        assertEquals(Verdict.Keep, g.onEvent(1, SeatMode.HEAT, 1, passengerOff), "sưởi ghế phụ vốn tắt")
    }

    @Test
    fun `doi mat sang suoi - su kien suoi bat truoc thi su kien mat tat khong tinh`() {
        val g = trip()
        assertEquals(Verdict.Keep, g.onEvent(0, SeatMode.HEAT, 2, driverOff))
        assertEquals(Verdict.Keep, g.onEvent(0, SeatMode.COOL, 1, driverOff + 20))
    }

    @Test
    fun `quyet som thi hen lai, su kien tat lap khong hen them`() {
        val g = trip()
        val d = g.off(0, driverOff)
        assertEquals(Verdict.Keep, g.onEvent(0, SeatMode.COOL, 1, driverOff + 5), "postEvent lặp")
        val early = g.decide(0, driverOff + t(1.0), evidence())
        assertTrue(early is Verdict.DecideAt && early.atMs == d.atMs, "$early")
        assertTrue(g.decide(0, d.atMs, evidence()) is Verdict.Reapply)
        assertEquals(Verdict.Keep, g.decide(0, d.atMs + 1, evidence()), "đã quyết ⇒ không bật lại lần nữa")
    }

    @Test
    fun `ngan sach - toi da 2 lan moi ghe trong 60 phut`() {
        val g = trip()
        val d = g.off(0, driverOff)
        val v = g.decide(0, d.atMs, evidence(reapplied = listOf(d.atMs - t(600.0), d.atMs - t(1200.0))))
        assertTrue(v is Verdict.Skip && v.reason.contains("đã bật lại 2 lần"), "$v")
        val g2 = trip()
        val d2 = g2.off(0, driverOff)
        assertTrue(g2.decide(0, d2.atMs, evidence(reapplied = listOf(d2.atMs - t(600.0), d2.atMs - t(3700.0)))) is Verdict.Reapply, "lần cũ hơn 60′ không tính")
    }

    @Test
    fun `dau van tay - cua so -2 s toi +8 s quanh lan tat`() {
        assertEquals(Signature.UNOBSERVABLE, SeatAutoOffGuard.signatureOf(false, driverOff, signature))
        assertEquals(Signature.SEEN, SeatAutoOffGuard.signatureOf(true, driverOff, signature))
        assertEquals(Signature.SEEN, SeatAutoOffGuard.signatureOf(true, driverOff, driverOff - t(1.5)))
        assertEquals(Signature.NOT_SEEN, SeatAutoOffGuard.signatureOf(true, driverOff, driverOff - t(3.0)))
        assertEquals(Signature.NOT_SEEN, SeatAutoOffGuard.signatureOf(true, driverOff, driverOff + t(9.0)))
        assertEquals(Signature.NOT_SEEN, SeatAutoOffGuard.signatureOf(true, passengerOff2, signature), "dấu 27 phút trước")
        assertEquals(Signature.NOT_SEEN, SeatAutoOffGuard.signatureOf(true, driverOff, null))
    }

    @Test
    fun `cong nguon va ma khung`() {
        assertTrue(SeatAutoOffGuard.carOn(2))
        assertTrue(SeatAutoOffGuard.carOn(3))
        assertTrue(SeatAutoOffGuard.carOn(null), "không đọc được ⇒ để cổng màn quyết")
        assertFalse(SeatAutoOffGuard.carOn(0))
        assertFalse(SeatAutoOffGuard.carOn(1))
        assertFalse(SeatAutoOffGuard.carOn(4))
        assertEquals(3, SeatAutoOffGuard.raw(" 3 "))
        assertNull(SeatAutoOffGuard.raw("-2147482645"))
        assertNull(SeatAutoOffGuard.raw("null"))
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
        assertEquals(setOf("seat_vent_state", "seat_vent_state_r", "seat_heat_state", "seat_heat_state_r"), SeatControls.DATUMS)
        assertEquals(listOf(0, 1), SeatControls.SEAT_INDICES)
        assertEquals(1, SeatControls.bindOfDatum("seat_vent_state_r")?.seatIndex)
        assertEquals(SeatMode.HEAT, SeatControls.bindOfDatum("seat_heat_state")?.mode)
        assertNull(SeatControls.bindOfDatum("fan"))
    }
}
