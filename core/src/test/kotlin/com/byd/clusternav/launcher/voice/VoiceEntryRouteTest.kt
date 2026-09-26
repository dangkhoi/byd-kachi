package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceEntryRoute.Route
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá CLOSE-3 (2026-09-26): wake BẬT ⇒ mọi lối vào đi `:wake` (một mô hình cho cả máy); wake TẮT ⇒ in-process như
 * V3 R4. "Service sống" chỉ đổi HẠN CHỜ ack, không đổi đường — xem KDoc [VoiceEntryRoute].
 */
class VoiceEntryRouteTest {

    // ── decide: 4 tổ hợp ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `wake TAT - in-process, khong cho ack (ca service song lan chet)`() {
        assertEquals(VoiceEntryRoute.Plan(Route.IN_PROCESS, 0L), VoiceEntryRoute.decide(wakeEnabled = false, wakeProcessAlive = true))
        assertEquals(VoiceEntryRoute.Plan(Route.IN_PROCESS, 0L), VoiceEntryRoute.decide(wakeEnabled = false, wakeProcessAlive = false))
    }

    @Test
    fun `wake BAT + wake song - di wake, cho ack ngan`() {
        assertEquals(
            VoiceEntryRoute.Plan(Route.WAKE_PROCESS, VoiceEntryRoute.ACK_WARM_MS),
            VoiceEntryRoute.decide(wakeEnabled = true, wakeProcessAlive = true),
        )
    }

    /** Wake bật mà `:wake` chết ⇒ VẪN đi wake (startForegroundService dựng lại) — in-process ở ca này là nạp bản mô hình thứ hai. */
    @Test
    fun `wake BAT + wake chet - van di wake, cho ack dai hon (dung lanh)`() {
        val plan = VoiceEntryRoute.decide(wakeEnabled = true, wakeProcessAlive = false)
        assertEquals(Route.WAKE_PROCESS, plan.route)
        assertEquals(VoiceEntryRoute.ACK_COLD_MS, plan.ackTimeoutMs)
        assertTrue(plan.ackTimeoutMs > VoiceEntryRoute.ACK_WARM_MS, "dựng lạnh phải được chờ lâu hơn tiến trình đang sống")
    }

    // ── afterDispatch: đường lùi ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `gui hong hoac khong ack thi lui in-process - gui duoc va ack thi o lai wake`() {
        assertEquals(Route.IN_PROCESS, VoiceEntryRoute.afterDispatch(dispatched = false, acked = false))
        assertEquals(Route.IN_PROCESS, VoiceEntryRoute.afterDispatch(dispatched = true, acked = false))
        assertEquals(Route.IN_PROCESS, VoiceEntryRoute.afterDispatch(dispatched = false, acked = true))
        assertEquals(Route.WAKE_PROCESS, VoiceEntryRoute.afterDispatch(dispatched = true, acked = true))
    }

    // ── VoiceHomeAction: id ↔ enum, id lạ bị bỏ ─────────────────────────────────────────────────────────────

    @Test
    fun `home action - moi id di ve dung enum, id la hoac null tra null`() {
        VoiceHomeAction.values().forEach { a -> assertEquals(a, VoiceHomeAction.of(a.id)) }
        assertNull(VoiceHomeAction.of(null))
        assertNull(VoiceHomeAction.of("start_voice"))
        assertEquals(VoiceHomeAction.values().size, VoiceHomeAction.values().map { it.id }.toSet().size, "id phải duy nhất")
    }
}
