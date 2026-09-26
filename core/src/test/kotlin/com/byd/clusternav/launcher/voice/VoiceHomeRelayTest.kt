package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.LayoutPreset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá VOICE-WAKE-SLOT-LAYOUT (2.69): hai việc có tham số của `:wake` → Activity đi qua một bộ mã hoá duy nhất, giải
 * mã **từ chối** đầu vào lạ (intent tới HOME activity ai cũng gửi được), và phép so hạn là thứ giữ cho `:wake` và
 * Activity không nói hai điều khác nhau.
 */
class VoiceHomeRelayTest {

    @Test
    fun `hai action moi co id rieng, mang co awaitsResult, bon action cu khong`() {
        assertEquals(VoiceHomeAction.ASSIGN_APP_TO_SLOT, VoiceHomeAction.of("assign_app_to_slot"))
        assertEquals(VoiceHomeAction.SET_LAYOUT, VoiceHomeAction.of("set_layout"))
        assertEquals(
            setOf(VoiceHomeAction.ASSIGN_APP_TO_SLOT, VoiceHomeAction.SET_LAYOUT),
            VoiceHomeAction.values().filter { it.awaitsResult }.toSet(),
            "chỉ hai việc trả Boolean cho dispatcher mới cần chờ ack",
        )
        assertEquals(VoiceHomeAction.values().size, VoiceHomeAction.values().map { it.id }.toSet().size, "id phải duy nhất")
    }

    @Test
    fun `slot - round-trip 0-based va ten goi co dau cham, gach duoi, so`() {
        listOf(0 to "com.google.android.youtube", 3 to "vn.vietmap.live", 1 to "a.b_c.d9").forEach { (slot, pkg) ->
            assertEquals(VoiceHomeRelay.SlotAssign(slot, pkg), VoiceHomeRelay.decodeSlot(VoiceHomeRelay.encodeSlot(slot, pkg)))
        }
    }

    @Test
    fun `slot - dau vao la bi tu choi, khong doan`() {
        listOf(
            null, "", "youtube", ":com.a.b", "2:", "-1:com.a.b", "x:com.a.b", "2:noDot", "2:com..b", "2:1com.a", "2:com.a b",
            "2:com.a.b;rm -rf", "2:com/a/b",
        ).forEach { assertNull(VoiceHomeRelay.decodeSlot(it), "phải từ chối `$it`") }
    }

    @Test
    fun `layout - round-trip 5 preset, ten la tra null`() {
        LayoutPreset.values().forEach { p -> assertEquals(p, VoiceHomeRelay.decodeLayout(VoiceHomeRelay.encodeLayout(p))) }
        assertNull(VoiceHomeRelay.decodeLayout(null))
        assertNull(VoiceHomeRelay.decodeLayout("two_col"))
        assertNull(VoiceHomeRelay.decodeLayout("FIVE"))
    }

    @Test
    fun `han - qua han thi Activity khong lam, khong han (0) thi luon lam`() {
        assertFalse(VoiceHomeRelay.expired(deadlineMs = 0L, nowMs = 999_999L), "0 = fire-and-forget (bốn việc cũ)")
        assertFalse(VoiceHomeRelay.expired(deadlineMs = 1_000L, nowMs = 1_000L), "đúng mốc vẫn còn hạn")
        assertFalse(VoiceHomeRelay.expired(deadlineMs = 1_000L, nowMs = 999L))
        assertTrue(VoiceHomeRelay.expired(deadlineMs = 1_000L, nowMs = 1_001L))
        assertTrue(VoiceHomeRelay.ACK_MS in 500L..5_000L, "hạn chờ Activity phải cùng thang với hạn ack `:wake` (1,5 s sống / 4 s lạnh)")
    }

    /**
     * Khoá vá [SOÁT 2.69 · P1]: hạn chờ ack là một phép ĐO (tiến trình chính sống/chết), không phải hằng cố định —
     * 1,5 s cho một launcher đang sống là **từ chối oan** khi LMK vừa giết nó và `startActivity` phải dựng lạnh.
     * Trần cứng 5 s = `KEY_DISPATCHING_TIMEOUT_MS` của AOSP r47 (perform chặn luồng main của `:wake`).
     */
    @Test
    fun `han cho ack theo phep DO - 1,5 s khi tien trinh chinh song, 4 s khi phai dung lanh`() {
        assertEquals(VoiceHomeRelay.ACK_MS, VoiceHomeRelay.ackTimeoutMs(mainProcessAlive = true))
        assertEquals(VoiceEntryRoute.ACK_COLD_MS, VoiceHomeRelay.ackTimeoutMs(mainProcessAlive = false))
        assertTrue(
            VoiceHomeRelay.ackTimeoutMs(false) > VoiceHomeRelay.ackTimeoutMs(true),
            "dựng lạnh phải được chờ LÂU HƠN — không thì ca thường nhất (launcher bị LMK giết) luôn từ chối oan",
        )
        assertTrue(
            VoiceHomeRelay.ackTimeoutMs(false) < 5_000L,
            "perform chặn luồng MAIN của `:wake`, và tấm chữ là cửa sổ nhận chạm ⇒ phải dưới " +
                "KEY_DISPATCHING_TIMEOUT_MS = 5 * 1000 (AOSP android-10.0.0_r47 ActivityTaskManagerService)",
        )
    }
}
