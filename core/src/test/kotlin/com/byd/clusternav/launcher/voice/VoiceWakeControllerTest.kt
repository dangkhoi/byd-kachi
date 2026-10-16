package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Bộ não bộ nghe "Hey Kachi" — kiểm 4 tầng cắt CPU + cooldown + cầu chì false-accept (thuần, off-car). */
class VoiceWakeControllerTest {

    private fun controller() = VoiceWakeController(
        gate = VoiceWakeGate(),
        loadGuard = VoiceLoadGuard(suspendAbove = 6.0, resumeBelow = 4.0, resumeStableReads = 1),
        cooldownMs = 3_000L, maxWakesPerWindow = 6, wakeWindowMs = 60_000L,
    )

    @Test fun `he nong thi SUSPENDED (nha mic, thoi KWS)`() {
        val c = controller()
        assertEquals(VoiceWakeController.Frame.SUSPENDED, c.onFrame(rms = 800.0, load1 = 9.0, nowMs = 0),
            "load cao ⇒ dừng dù có giọng to")
    }

    @Test fun `im thi IDLE, co giong thi RUN_KWS`() {
        val c = controller()
        assertEquals(VoiceWakeController.Frame.IDLE, c.onFrame(rms = 80.0, load1 = 1.0, nowMs = 0), "im ⇒ không chạy KWS")
        assertEquals(VoiceWakeController.Frame.RUN_KWS, c.onFrame(rms = 800.0, load1 = 1.0, nowMs = 100), "có giọng ⇒ chạy KWS")
    }

    @Test fun `no wake xong thi cooldown IDLE roi moi chay lai`() {
        val c = controller()
        assertEquals(VoiceWakeController.Frame.RUN_KWS, c.onFrame(800.0, 1.0, 100))
        assertEquals(VoiceWakeController.Wake.FIRE, c.onKwsResult(matched = true, nowMs = 100))
        // trong cooldown (100..3100): dù có giọng to vẫn IDLE
        assertEquals(VoiceWakeController.Frame.IDLE, c.onFrame(800.0, 1.0, 1_000), "đang cooldown ⇒ không chạy KWS")
        // sau cooldown: chạy lại
        assertEquals(VoiceWakeController.Frame.RUN_KWS, c.onFrame(800.0, 1.0, 3_200))
    }

    @Test fun `KWS khong khop thi NONE, khong cooldown`() {
        val c = controller()
        assertEquals(VoiceWakeController.Wake.NONE, c.onKwsResult(matched = false, nowMs = 100))
        assertEquals(VoiceWakeController.Frame.RUN_KWS, c.onFrame(800.0, 1.0, 200), "không khớp ⇒ không nghỉ")
    }

    /**
     * ═══ TẦNG 4 — ngân sách CPU suy diễn (VOICE-WAKE-CPU-BUDGET) ═══════════════════════════════════════════════
     * Ca hỏng mà ba tầng đầu KHÔNG bịt: **nhạc trong cabin** = RMS nằm trên ngưỡng **liên tục** ⇒ `voiced()` đúng mọi
     * khung. [ĐO xe 15/10] trần cũ đếm khung để `:wake` chạy 105 % TB / 263 % đỉnh. Nay trần đếm **ms CPU đo được**.
     * Red-proof: trả `onFrame` về bản đếm khung (hoặc bỏ `budget.mayRun`) ⇒ bài này đỏ (không bao giờ ra OBSERVE).
     */
    @Test fun `nhac lien tuc - luot giai ma dat bi gian ra theo gia CPU that`() {
        val c = VoiceWakeController(
            gate = VoiceWakeGate(),
            loadGuard = VoiceLoadGuard(suspendAbove = 6.0, resumeBelow = 4.0, resumeStableReads = 1),
            budget = VoiceWakeCpuBudget(duty = 0.25, burstMs = 4_000.0),
        )
        // 60 s nhạc, khung 100 ms; mỗi khung thứ 5 được chạy là một lượt giải mã ~1 350 ms CPU (số xe 15/10).
        var runs = 0; var observes = 0; var cpu = 0L
        for (i in 0 until 600) {
            val now = i * 100L
            when (c.onFrame(800.0, 1.0, now)) {
                VoiceWakeController.Frame.RUN_KWS -> { runs++; val cost = if (runs % 5 == 0) 1_350L else 1L; cpu += cost; c.onInferenceCost(cost, now) }
                VoiceWakeController.Frame.OBSERVE -> { observes++; cpu += 1; c.onInferenceCost(1L, now) }
                else -> Unit
            }
        }
        assertTrue(observes > 0, "quá ngân sách ⇒ phải có khung OBSERVE (gom, không suy diễn)")
        // Chặn trên: burst + duty × 60 s + một lượt = 4 000 + 15 000 + 1 350.
        assertTrue(cpu <= 4_000 + 15_000 + 1_350, "CPU 60 s = $cpu ms vượt chặn trên")
        // Trước (trần khung 40/10 s): mọi lượt giải mã có thể đều chạy ⇒ 120 lượt × 1 350 = 162 s CPU / 60 s.
        assertTrue(cpu < 0.40 * 60_000, "CPU 60 s = $cpu ms — phải dưới 40 % một lõi")
        assertEquals(cpu, c.chargedCpuMs(), "controller phải tính đúng tổng đã báo")
    }

    @Test fun `ngan sach khong chan mot cau goi sau quang yen`() {
        val c = controller() // mặc định: 25 % · burst 4 000 ms
        // Câu gọi ≈ 1,5 s = 15 khung; 3 lượt giải mã đắt như xe (1 350 ms) — phải chạy hết, không khung nào bị OBSERVE.
        for (i in 0 until 15) {
            val now = i * 100L
            assertEquals(VoiceWakeController.Frame.RUN_KWS, c.onFrame(800.0, 1.0, now), "khung #${i + 1} của câu gọi")
            c.onInferenceCost(if (i % 5 == 4) 1_350L else 1L, now)
        }
    }

    @Test fun `reset tra lai tron ngan sach`() {
        val c = controller()
        c.onFrame(800.0, 1.0, 0L); c.onInferenceCost(50_000L, 0L)
        assertEquals(VoiceWakeController.Frame.OBSERVE, c.onFrame(800.0, 1.0, 100L), "nợ ⇒ OBSERVE")
        c.reset()
        assertEquals(VoiceWakeController.Frame.RUN_KWS, c.onFrame(800.0, 1.0, 200L), "bật lại công tắc ⇒ ngân sách đầy")
    }

    /**
     * ═══ ĐUÔI CÂU — VOICE-WAKE-TAIL-UNDECODED ═══════════════════════════════════════════════════════════════════
     * [ĐO máy ảo 15/10] cabin yên 61,4 %: khung im bị cắt trước engine ⇒ âm cuối câu gọi không bao giờ được giải mã.
     * Khoá: tiếng→im ⇒ [tailMs] khung OBSERVE rồi ĐÚNG MỘT FINISH; im tiếp ⇒ IDLE; đoạn tiếng mới ⇒ FINISH mới;
     * nợ ngân sách ⇒ không FINISH. Red-proof: cho `tail()` trả IDLE ⇒ bài đỏ.
     */
    @Test fun `tieng roi im - gom duoi roi dung MOT luot giai ma cuoi`() {
        val c = VoiceWakeController(
            loadGuard = VoiceLoadGuard(suspendAbove = 6.0, resumeBelow = 4.0, resumeStableReads = 1), tailMs = 600L,
        )
        val got = ArrayList<VoiceWakeController.Frame>()
        var t = 0L
        repeat(8) { c.onFrame(10_000.0, 1.0, t); t += 100 }            // câu gọi 0,8 s
        repeat(15) { got += c.onFrame(0.0, 1.0, t); t += 100 }          // 1,5 s im
        val obs = got.takeWhile { it == VoiceWakeController.Frame.OBSERVE }.size
        assertTrue(obs in 5..6, "≈ 600 ms đuôi gom vào engine (OBSERVE): $obs khung")
        assertEquals(VoiceWakeController.Frame.FINISH, got[obs], "hết đuôi ⇒ FINISH")
        assertEquals(1, got.count { it == VoiceWakeController.Frame.FINISH }, "đúng MỘT lượt cuối mỗi đoạn tiếng")
        assertTrue(got.drop(obs + 1).all { it == VoiceWakeController.Frame.IDLE }, "im tiếp ⇒ chỉ toán RMS")
        // đoạn tiếng thứ hai ⇒ một FINISH nữa
        repeat(5) { c.onFrame(10_000.0, 1.0, t); t += 100 }
        val second = (0 until 10).map { c.onFrame(0.0, 1.0, t).also { t += 100 } }
        assertEquals(1, second.count { it == VoiceWakeController.Frame.FINISH })
    }

    /**
     * WAKE-TAIL-STALE-FINISH (soát Pass 9 [P3]) — nói giữa chừng thì phiên lệnh xin micro (YIELD), listener nhả mic; mở lại sau
     * đó thì khung im ĐẦU không được phát FINISH cho đoạn tiếng của lượt TRƯỚC. Red-proof: gỡ `onMicOpened` ⇒ khung đầu = FINISH.
     */
    @Test fun `mo lai micro thi duoi cau cua luot truoc khong phat FINISH`() {
        val c = VoiceWakeController(
            loadGuard = VoiceLoadGuard(suspendAbove = 6.0, resumeBelow = 4.0, resumeStableReads = 1), tailMs = 600L,
        )
        var t = 0L
        repeat(5) { c.onFrame(10_000.0, 1.0, t); t += 100 }   // đang có tiếng thì bị nhường micro
        t += 5_000L                                            // micro nhả ~5 s (phiên lệnh)
        c.onMicOpened()
        val got = (0 until 10).map { c.onFrame(0.0, 1.0, t).also { t += 100 } }
        assertTrue(got.none { it == VoiceWakeController.Frame.FINISH }, "lượt nghe mới bắt đầu bằng im ⇒ không FINISH cũ: $got")
        // lượt mới vẫn có đuôi câu như thường
        repeat(5) { c.onFrame(10_000.0, 1.0, t); t += 100 }
        assertEquals(1, (0 until 10).count { c.onFrame(0.0, 1.0, t).also { t += 100 } == VoiceWakeController.Frame.FINISH })
    }

    @Test fun `khong co tieng truoc thi khong FINISH, no ngan sach thi khong FINISH`() {
        val c = VoiceWakeController(loadGuard = VoiceLoadGuard(suspendAbove = 6.0, resumeBelow = 4.0, resumeStableReads = 1))
        assertTrue((0 until 30).none { c.onFrame(0.0, 1.0, it * 100L) == VoiceWakeController.Frame.FINISH }, "im từ đầu ⇒ không lượt nào")
        val d = VoiceWakeController(
            loadGuard = VoiceLoadGuard(suspendAbove = 6.0, resumeBelow = 4.0, resumeStableReads = 1),
            budget = VoiceWakeCpuBudget(duty = 0.25, burstMs = 1_000.0),
        )
        var t = 0L
        repeat(5) { d.onFrame(10_000.0, 1.0, t); d.onInferenceCost(5_000L, t); t += 100 }   // tiêu quá ngân sách
        assertTrue((0 until 15).none { d.onFrame(0.0, 1.0, t).also { t += 100 } == VoiceWakeController.Frame.FINISH },
            "nợ ngân sách ⇒ lượt cuối cũng bị chặn (một trần cho mọi suy diễn)")
    }

    @Test fun `cau chi false-accept tu tat sau qua nhieu wake`() {
        val c = controller()
        // 6 wake đầu trong cửa sổ ⇒ FIRE; wake thứ 7 ⇒ FUSED
        for (i in 0 until 6) assertEquals(VoiceWakeController.Wake.FIRE, c.onKwsResult(true, i * 100L), "wake #${i + 1}")
        assertEquals(VoiceWakeController.Wake.FUSED, c.onKwsResult(true, 700L), "wake #7 vượt trần ⇒ tự tắt")
        assertTrue(c.isFused())
        assertEquals(VoiceWakeController.Frame.SUSPENDED, c.onFrame(800.0, 1.0, 800L), "đã cầu chì ⇒ ngừng hẳn")
        c.reset(); assertFalse(c.isFused(), "bật lại công tắc ⇒ reset")
    }

    @Test fun `cau chi TU tat sau cua so hoi phuc (khong can restart service)`() {
        // [ĐO xe 2026-09-21] fuse cũ latch vĩnh viễn ⇒ nổ 1 lần rồi thử nhiều lần (>6) là "kêu hoài không lên"
        // tới hết chuyến. Nay tự tắt sau DEFAULT_FUSE_RECOVERY_MS.
        val c = controller()
        for (i in 0 until 6) c.onKwsResult(true, i * 100L)
        assertEquals(VoiceWakeController.Wake.FUSED, c.onKwsResult(true, 700L))
        assertTrue(c.isFused())
        // Trong cửa sổ hồi phục ⇒ vẫn SUSPENDED
        val within = 700L + VoiceWakeController.DEFAULT_FUSE_RECOVERY_MS - 1
        assertEquals(VoiceWakeController.Frame.SUSPENDED, c.onFrame(800.0, 1.0, within))
        // Hết cửa sổ ⇒ fuse tự tắt (không cần reset); onFrame qua mốc sẽ tự xoá fuse
        val after = 700L + VoiceWakeController.DEFAULT_FUSE_RECOVERY_MS + 1
        c.onFrame(5000.0, 1.0, after)
        assertFalse(c.isFused(), "hết cửa sổ ⇒ fuse tự tắt, nghe lại được")
    }
}
