package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceWakeStandDown.Decision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * BG-20 (2026-09-25 · wake) — quyết định THUẦN đứng xuống của `:wake` sau phiên headless khi "Hey Kachi" TẮT.
 * Dời từ `:app` sang `:core` 2026-09-26 cùng object (CLOSE-3); phần dây `VoiceWakeService` ở
 * `app/.../VoiceWakeStandDownWiringContractTest`.
 */
class VoiceWakeStandDownTest {

    private val off = VoiceWakeMode.OFF

    // ── Quyết định thuần đứng xuống ─────────────────────────────────────────────────────────────────────────

    /** FIX286 · VK1: tham số `wakeEnabled` → [VoiceWakeMode]; WAKE giữ y nguyên hành vi `wakeEnabled = true` cũ. */
    @Test
    fun `wake BAT thi KEEP - vong doi thuong so huu service, du phien dang o pha nao`() {
        VoiceTurnPhase.entries.forEach { ph ->
            listOf(false, true).forEach { loading ->
                assertEquals(Decision.KEEP, VoiceWakeStandDown.decide(VoiceWakeMode.WAKE, sessionPhase = ph, waitedMs = 0, loading = loading))
                assertEquals(Decision.KEEP, VoiceWakeStandDown.decide(VoiceWakeMode.WAKE, sessionPhase = ph, waitedMs = 999_999, loading = loading))
            }
        }
    }

    /** FIX286 · VK2: HOLD (phím vô-lăng gán Kachi nghe, wake TẮT) giữ mô hình như WAKE — đó là toàn bộ ý nghĩa của nó. */
    @Test
    fun `HOLD thi KEEP o moi pha - giu mo hinh cho lan bam phim ke`() {
        VoiceTurnPhase.entries.forEach { ph ->
            assertEquals(Decision.KEEP, VoiceWakeStandDown.decide(VoiceWakeMode.HOLD, ph, waitedMs = 0, loading = false), ph.name)
            assertEquals(Decision.KEEP, VoiceWakeStandDown.decide(VoiceWakeMode.HOLD, ph, waitedMs = VoiceWakeStandDown.MAX_WAIT_MS, loading = true), ph.name)
        }
    }

    @Test
    fun `wake TAT va phien da IDLE thi STAND_DOWN ngay`() {
        assertEquals(Decision.STAND_DOWN, VoiceWakeStandDown.decide(off, VoiceTurnPhase.IDLE, waitedMs = 0, loading = false))
    }

    @Test
    fun `wake TAT va phien con chay thi WAIT - khong cat giua cau nguoi lai`() {
        listOf(VoiceTurnPhase.LISTENING, VoiceTurnPhase.DECODING, VoiceTurnPhase.EXECUTING).forEach { ph ->
            assertEquals(Decision.WAIT, VoiceWakeStandDown.decide(off, ph, waitedMs = 60_000, loading = false), ph.name)
        }
    }

    @Test
    fun `phien ket qua tran thi van dung xuong - FGS treo mai khong phai cach che loi`() {
        val max = VoiceWakeStandDown.MAX_WAIT_MS
        assertEquals(Decision.WAIT, VoiceWakeStandDown.decide(off, VoiceTurnPhase.LISTENING, waitedMs = max - 1, loading = false))
        assertEquals(Decision.STAND_DOWN, VoiceWakeStandDown.decide(off, VoiceTurnPhase.LISTENING, waitedMs = max, loading = false))
    }

    /**
     * FIX286 · VK3 — HỒI QUY "huỷ trong lúc nạp → bấm lại": phiên đã IDLE (người lái chạm huỷ khi còn "Getting
     * ready…") mà mô hình ĐANG NẠP ⇒ WAIT, không đứng xuống. 2.85 trả STAND_DOWN ở đây ⇒ luồng chính `:wake` gọi nhả,
     * chờ đúng khoá luồng nạp đang giữ (9–34 s) ⇒ lần bấm sau không hiện gì rồi nạp lại từ đầu. Đứng TRƯỚC trần chờ:
     * kể cả quá trần, đang nạp thì không nhả được gì — chỉ bỏ 74 MB lại trong một tiến trình không chủ.
     */
    @Test
    fun `dang nap thi WAIT ke ca khi phien da IDLE hoac da qua tran - nap xong moi dung xuong`() {
        VoiceTurnPhase.entries.forEach { ph ->
            assertEquals(Decision.WAIT, VoiceWakeStandDown.decide(off, ph, waitedMs = 0, loading = true), ph.name)
            assertEquals(Decision.WAIT, VoiceWakeStandDown.decide(off, ph, waitedMs = VoiceWakeStandDown.MAX_WAIT_MS * 2, loading = true), ph.name)
        }
        assertEquals(Decision.STAND_DOWN, VoiceWakeStandDown.decide(off, VoiceTurnPhase.IDLE, waitedMs = 0, loading = false), "nạp xong ⇒ nhịp sau đứng xuống")
    }
}
