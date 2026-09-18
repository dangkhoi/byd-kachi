package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * ═══ "Hey Kachi" — LÕI AN TOÀN (thuần, off-car) ══════════════════════════════════════════════════════════════
 * Khoá đúng phần owner lo nhất: không hang CPU. Load-guard (lá chắn chính), cổng năng lượng (duty-cycle KWS),
 * một-mic-handoff (không hai mic), preset câu gọi.
 */
class VoiceWakeSafetyTest {

    // ── VoiceLoadGuard — dừng NHANH khi nóng, chạy lại CHẬM khi nguội đủ lâu ──────────────────────────────────
    @Test fun `load-guard dung ngay khi nong, chi chay lai sau khi nguoi lien tiep`() {
        val g = VoiceLoadGuard(suspendAbove = 6.0, resumeBelow = 4.0, resumeStableReads = 3)
        assertTrue(g.allow(2.0), "load thấp ⇒ chạy")
        assertFalse(g.allow(7.0), "vượt ngưỡng ⇒ DỪNG ngay 1 nhịp")
        assertFalse(g.allow(3.0), "nguội nhịp 1 — chưa đủ")
        assertFalse(g.allow(3.0), "nguội nhịp 2 — chưa đủ")
        assertTrue(g.allow(3.0), "nguội liên tiếp đủ 3 nhịp ⇒ chạy lại")
    }

    @Test fun `mot nhip nong xoa chuoi nguoi (chong rung)`() {
        val g = VoiceLoadGuard(suspendAbove = 6.0, resumeBelow = 4.0, resumeStableReads = 3)
        g.allow(9.0) // suspend
        assertFalse(g.allow(3.0), "nguội nhịp 1")
        assertFalse(g.allow(5.0), "5.0 ở GIỮA khe trễ (4..6) ⇒ vẫn dừng + xoá chuỗi")
        assertFalse(g.allow(3.0), "phải đếm LẠI từ đầu")
        assertFalse(g.allow(3.0))
        assertTrue(g.allow(3.0), "đủ 3 nhịp nguội liên tiếp mới chạy lại")
    }

    @Test fun `load giua khe tre khi dang chay thi van chay`() {
        val g = VoiceLoadGuard(suspendAbove = 6.0, resumeBelow = 4.0)
        assertTrue(g.allow(5.0), "5.0 < suspendAbove 6.0 ⇒ chưa dừng (chỉ dừng khi VƯỢT ngưỡng trên)")
    }

    // ── VoiceWakeGate — im thì không chạy KWS; giọng không tự kéo nền lên để bịt mình ─────────────────────────
    @Test fun `cong nang luong chan im, cho giong`() {
        val g = VoiceWakeGate()
        assertFalse(g.voiced(80.0), "dưới sàn ⇒ không chạy KWS")
        assertTrue(g.voiced(600.0), "vượt ngưỡng ⇒ chạy KWS")
    }

    @Test fun `giong lien tuc khong tu keo nen len bit minh`() {
        val g = VoiceWakeGate()
        repeat(20) { assertTrue(g.voiced(600.0), "giọng to liên tục vẫn phải qua cổng mọi lần") }
        assertEquals(VoiceWakeGate.DEFAULT_ABS_FLOOR, g.noiseFloor(), 1e-9, "nền KHÔNG đổi khi toàn giọng")
    }

    @Test fun `nen hoc theo im lang`() {
        val g = VoiceWakeGate()
        repeat(50) { g.voiced(90.0) } // dưới sàn nhiều lần ⇒ học nền xuống
        assertTrue(g.noiseFloor() < VoiceWakeGate.DEFAULT_ABS_FLOOR, "nền đi xuống theo mức im ${g.noiseFloor()}")
    }

    // ── VoiceWakePhrase — preset + validate ──────────────────────────────────────────────────────────────────
    @Test fun `preset mac dinh va tra ve default khi id la`() {
        assertEquals("hey_kachi", VoiceWakePhrase.DEFAULT.id)
        assertEquals(VoiceWakePhrase.DEFAULT, VoiceWakePhrase.byId("khong-ton-tai"))
        assertEquals("OK Kachi", VoiceWakePhrase.byId("ok_kachi").display)
    }

    @Test fun `validate cau goi tuy chon`() {
        assertTrue(VoiceWakePhrase.isValidCustom("hey kachi"))
        assertFalse(VoiceWakePhrase.isValidCustom("kachi"), "một từ ⇒ false-accept cao")
        assertFalse(VoiceWakePhrase.isValidCustom("a b"), "từ quá ngắn")
        assertFalse(VoiceWakePhrase.isValidCustom("một hai ba bốn năm"), "quá 4 từ")
        assertFalse(VoiceWakePhrase.isValidCustom(""))
    }

    // ── VoiceSingleFlight.handoff — MỘT mic, không khe hở ─────────────────────────────────────────────────────
    @BeforeEach fun resetFlight() = VoiceSingleFlight.reset()

    @Test fun `handoff wake sang command nguyen tu, tinh cau chi`() {
        assertEquals(VoiceSingleFlight.Grant.Ok, VoiceSingleFlight.acquire("wake", 1000L))
        assertEquals(1, VoiceSingleFlight.opensInWindow(1000L))
        assertEquals(VoiceSingleFlight.Grant.Ok, VoiceSingleFlight.handoff("wake", "chinh", 1001L))
        assertEquals(2, VoiceSingleFlight.opensInWindow(1001L), "wake→command tính là một lượt mở mới")
        // giờ chủ là "chinh": ai xin nữa bị chắn
        assertEquals(VoiceSingleFlight.Grant.Busy("chinh"), VoiceSingleFlight.acquire("khac", 1002L))
    }

    @Test fun `handoff khi chu khong dung thi khong cuop`() {
        VoiceSingleFlight.acquire("chinh", 2000L)
        assertEquals(VoiceSingleFlight.Grant.Busy("chinh"), VoiceSingleFlight.handoff("wake", "x", 2001L))
    }

    @Test fun `handoff cham cau chi thi giu nguyen chu wake`() {
        // 11 lượt mở-nhả để lấp cầu chì tới 11, rồi wake giữ mic thành lượt 12
        for (i in 0 until 11) { VoiceSingleFlight.acquire("x$i", 3000L + i); VoiceSingleFlight.release() }
        assertEquals(VoiceSingleFlight.Grant.Ok, VoiceSingleFlight.acquire("wake", 3100L))
        assertEquals(12, VoiceSingleFlight.opensInWindow(3100L))
        val g = VoiceSingleFlight.handoff("wake", "chinh", 3101L)
        assertTrue(g is VoiceSingleFlight.Grant.Fused, "chạm cầu chì ⇒ Fused")
        // chủ vẫn là wake (không tạo khe trống): ai xin đều thấy wake giữ
        assertEquals(VoiceSingleFlight.Grant.Busy("wake"), VoiceSingleFlight.acquire("khac", 3102L))
    }
}
