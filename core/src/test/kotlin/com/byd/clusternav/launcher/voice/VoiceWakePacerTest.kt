package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WAKE-WEAK-UNIT (3.1.14) — khoá luật lùi theo tải máy của "Hey Kachi" (`VoiceWakePacer.kt`).
 * Gốc [ĐO log Seal 17/10]: nạp mô hình ~45 s ở 96–154 % CPU sau mỗi lần nổ máy, rồi ~12 phút ở trần 25 %, trong khi
 * [VoiceLoadGuard] không thấy tải của ai khác (`/proc/loadavg` bị chặn).
 * Red-proof: [VoiceWakeController.pace] không làm gì (= trước bản này) ⇒ bài 3, 4 đỏ; [VoiceLoadDefer.step] luôn CALM ⇒ bài 5 đỏ;
 * [VoiceWakeDuty.forDevice] luôn DUTY ⇒ bài 6 đỏ.
 */
class VoiceWakePacerTest {

    // Mẫu thật [ĐO máy ảo clusternav10 2 lõi 17/10, nice 19, lượt thức hẹn giờ, ms]: rảnh · 1 vòng bận · 2 vòng bận (đủ 2/2 lõi).
    private val idle = listOf(0.029, 0.781, 0.031, 0.024, 0.023)
    private val oneSpin = listOf(0.339, 0.150, 0.039, 0.025, 0.026)
    private val twoSpin = listOf(3.653, 3.352, 2.701, 3.692, 3.576)

    @Test fun `1 - bang mau that - ranh va con mot loi khong bi coi la ban, du loi thi ban`() {
        val b = VoiceWakeBackoff()
        (idle + oneSpin + idle).forEach { assertFalse(b.sample(it), "mẫu $it không được làm 'bận'") }
        twoSpin.forEach { b.sample(it) }
        assertTrue(b.busy, "đủ 2 lõi bận ⇒ phải bận")
        assertEquals(1, b.busySpells)
    }

    @Test fun `2 - ban nhanh, ranh cham, khong co so thi giu nguyen`() {
        val b = VoiceWakeBackoff()
        b.sample(3.0); assertFalse(b.busy, "một mẫu lẻ chưa đủ")
        b.sample(3.0); assertTrue(b.busy)
        repeat(10) { b.sample(null) }; assertTrue(b.busy, "không có số sạch ⇒ không tự kết luận đã rảnh")
        repeat(4) { b.sample(0.1) }; assertTrue(b.busy, "4 mẫu nguội chưa đủ")
        b.sample(1.0); repeat(4) { b.sample(0.1) }; assertTrue(b.busy, "mẫu giữa khe xoá chuỗi nguội")
        b.sample(0.1); assertFalse(b.busy, "5 mẫu nguội liên tiếp ⇒ rảnh")
    }

    /** Nhạc có lời liên tục: engine muốn giải mã mỗi 500 ms, mỗi lượt 1 350 ms CPU (giá xe [ĐO 15/10]). */
    private fun cpuOver(ctl: VoiceWakeController, durMs: Long, busyAt: (Long) -> Boolean, deviceDuty: Double): Long {
        var t = 0L; var cpu = 0L; var nextTick = 0L
        while (t < durMs) {
            if (t >= nextTick) { ctl.pace(deviceDuty, busyAt(t), t); nextTick += 1_000L }
            if (ctl.onFrame(800.0, 0.0, t) == VoiceWakeController.Frame.RUN_KWS && t % 500L == 0L) {
                ctl.onInferenceCost(1_350L, t); cpu += 1_350L
            }
            t += 100L
        }
        return cpu
    }

    @Test fun `3 - may ban thi tran CPU dai han xuong 5 phan tram va xo kep o mot luot`() {
        val calm = cpuOver(VoiceWakeController(), 120_000L, { false }, VoiceWakeCpuBudget.DUTY)
        val busy = cpuOver(VoiceWakeController(), 120_000L, { true }, VoiceWakeCpuBudget.DUTY)
        assertTrue(calm >= 30_000L - 1_350L, "rảnh: vẫn được dùng gần đủ 25 % ($calm)")
        // Bận ngay từ đầu: xô kẹp 1 500 ⇒ ≤ 1 500 + 5 % × 120 s + một lượt.
        assertTrue(busy <= 1_500L + 6_000L + 1_350L, "bận: CPU $busy ms vượt chặn trên")
        assertTrue(busy > 0L, "bận không được điếc hẳn (bài học F5 09-19)")
    }

    @Test fun `4 - vua ban thi khong tieu not xo day, het ban thi xo nap dan khong nhay`() {
        val ctl = VoiceWakeController()
        assertEquals(4_000.0, ctl.budgetCreditMs(), 1e-9)
        ctl.pace(VoiceWakeCpuBudget.DUTY, busy = true, nowMs = 0L)
        assertTrue(ctl.budgetCreditMs() <= VoiceWakeDuty.BUSY_BURST_MS, "bận ⇒ xô kẹp ngay")
        assertEquals(VoiceWakeDuty.BUSY_DUTY, ctl.budgetDuty())
        ctl.pace(VoiceWakeCpuBudget.DUTY, busy = false, nowMs = 1_000L)
        assertTrue(ctl.budgetCreditMs() <= VoiceWakeDuty.BUSY_BURST_MS + 1_000 * 0.05 + 1e-9, "nới lại không làm số dư nhảy lên")
        ctl.onFrame(800.0, 0.0, 20_000L)
        assertEquals(4_000.0, ctl.budgetCreditMs(), 1e-9, "19 s × 25 % ⇒ xô đầy lại tới trần cũ")
    }

    @Test fun `5 - nap nen cho may ranh lien tuc, may ban mai thi van nap o tran cho`() {
        val d = VoiceLoadDefer()
        var t = 0L
        // Nổ máy: 30 s bận, rồi rảnh.
        while (t < 30_000L) { assertEquals(VoiceLoadDefer.Go.WAIT, d.step(t, true)); t += 500L }
        while (t < 30_000L + VoiceLoadDefer.QUIET_MS - 500L) { assertEquals(VoiceLoadDefer.Go.WAIT, d.step(t, false)); t += 500L }
        assertEquals(VoiceLoadDefer.Go.CALM, d.step(30_000L + VoiceLoadDefer.QUIET_MS, false))
        // Bận mãi: không điếc vĩnh viễn.
        val e = VoiceLoadDefer()
        assertEquals(VoiceLoadDefer.Go.WAIT, e.step(0L, true))
        assertEquals(VoiceLoadDefer.Go.WAIT, e.step(VoiceLoadDefer.MAX_WAIT_MS - 1, true))
        assertEquals(VoiceLoadDefer.Go.TIMEOUT, e.step(VoiceLoadDefer.MAX_WAIT_MS, true))
        // Chưa có số (null) không được tính là rảnh.
        val f = VoiceLoadDefer()
        assertEquals(VoiceLoadDefer.Go.WAIT, f.step(0L, null)); assertEquals(VoiceLoadDefer.Go.WAIT, f.step(VoiceLoadDefer.QUIET_MS, null))
    }

    @Test fun `6 - may yeu theo SO DO, khong theo ten doi xe`() {
        assertEquals(VoiceWakeCpuBudget.DUTY, VoiceWakeDuty.forDevice(cores = 8, ramMb = 3_700, lowRam = false))   // Seal [ĐO]: 8 lõi
        assertEquals(VoiceWakeDuty.LOW_END_DUTY, VoiceWakeDuty.forDevice(cores = 4, ramMb = 8_000, lowRam = false))
        assertEquals(VoiceWakeDuty.LOW_END_DUTY, VoiceWakeDuty.forDevice(cores = 8, ramMb = 2_900, lowRam = false))
        assertEquals(VoiceWakeDuty.LOW_END_DUTY, VoiceWakeDuty.forDevice(cores = 8, ramMb = 8_000, lowRam = true))
        assertEquals(VoiceWakeDuty.LOW_END_DUTY, VoiceWakeDuty.forDevice(8, 8_000, false, meanDecodeMs = 2_500))
        assertEquals(VoiceWakeCpuBudget.DUTY, VoiceWakeDuty.forDevice(8, 8_000, false, meanDecodeMs = 1_350))
        assertEquals(0.10, VoiceWakeDuty.LOW_END_DUTY)
        assertNull(VoiceWakeDuty.lowEndReason(8, 0, false, null), "RAM không đọc được (0) ≠ máy yếu")
    }

    /**
     * Mức harness: cabin YÊN, một câu gọi sau ≥ 16 s im — đúng điều kiện của bộ 57 câu gọi (`WakeAsrHarnessTextsTest` ≥ 49/57).
     * Trần máy yếu chỉ đổi tốc độ NẠP, không đổi xô ⇒ số lượt giải mã được chạy cho một câu gọi phải y hệt máy thường: 3 lượt
     * liền (trung bình đo 16/10 là 1,82 lượt/tệp) ở giá xe 1 350 ms.
     */
    @Test fun `7 - cabin yen - may yeu giai ma dung bang so luot may thuong`() {
        fun decodes(duty: Double): Int {
            val ctl = VoiceWakeController()
            ctl.pace(duty, busy = false, nowMs = 0L)
            var n = 0
            for (k in 0 until 3) { val t = 16_000L + k * 500L; if (ctl.onFrame(800.0, 0.0, t) == VoiceWakeController.Frame.RUN_KWS) { ctl.onInferenceCost(1_350L, t); n++ } }
            return n
        }
        assertEquals(decodes(VoiceWakeCpuBudget.DUTY), decodes(VoiceWakeDuty.LOW_END_DUTY))
        assertEquals(3, decodes(VoiceWakeDuty.LOW_END_DUTY), "xô 4 000 ms phủ 3 lượt giải mã của một câu gọi")
    }

    @Test fun `8 - schedstat - hieu hai moc, ban than suy dien thi bo mau`() {
        assertEquals(1_234L to 56L, VoiceSchedDelay.parse("999 1234 56\n"))
        assertNull(VoiceSchedDelay.parse("rác"))
        val m = VoiceSchedDelay()
        assertNull(m.sample(0L, 0L), "lần đầu chỉ ghi mốc")
        assertEquals(2.0, m.sample(20_000_000L, 10L)!!, 1e-9)
        assertNull(m.sample(90_000_000L, 20L, dirty = true), "giây có lượt giải mã của mình ⇒ không có số")
        assertEquals(0.5, m.sample(95_000_000L, 30L)!!, 1e-9, "mốc ghi lại sau mẫu bẩn")
        assertNull(m.sample(1L, 31L), "bộ đếm lùi ⇒ không có số")
        val o = VoiceOversleep()
        assertEquals(0.0, o.sample(500L, 540L)!!, 1e-9, "độ trễ hẹn giờ cố định bị trừ")
        assertEquals(12.0, o.sample(500L, 552L)!!, 1e-9)
    }
}
