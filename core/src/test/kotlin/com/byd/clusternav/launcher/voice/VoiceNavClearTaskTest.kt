package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * ═══ 2.83 · `CLEAR_TASK` khi BẮT ĐẦU DẪN là DỮ LIỆU của đích, và hôm nay CHỈ Google Maps có ═══════════════════
 *
 * [ĐO xe 29/09] Google Maps đang dẫn + ý-định chỉ `NEW_TASK` ⇒ GMaps hỏi *"Thoát chế độ đi theo chỉ dẫn?"* và khoá
 * nút *"Có"* — người lái kẹt giữa hai điểm đến. Thêm `FLAG_ACTIVITY_CLEAR_TASK` ⇒ phiên cũ bị dọn, dẫn thẳng đích
 * mới (owner xác nhận).
 *
 * Bài này khoá hai điều ở `:core`:
 *  1. Google Maps mang cờ — gỡ nó là lỗi hiện trường quay lại.
 *  2. MỌI đích khác KHÔNG mang cờ — VietMap (`singleTask`) và Waze chưa đo ca này; bật cho chúng là đổi một đường
 *     đang chạy tốt mà chưa đo (CLAUDE.md §6/§14).
 * Phần `:app` (cờ thật sự vào `Intent`, và mọi đường dẫn đi qua cùng một cửa) khoá ở `VoiceNavClearTaskFlagTest`.
 */
class VoiceNavClearTaskTest {

    @Test
    fun `Google Maps mang co CLEAR_TASK khi bat dau dan`() {
        val gmaps = VoiceAppTargets.byKey(VoiceAppTargets.GMAPS)!!
        assertTrue(gmaps.clearTaskOnNav, "[ĐO xe 29/09] thiếu cờ ⇒ GMaps hỏi 'Thoát chế độ đi theo chỉ dẫn?' và khoá nút")
    }

    @Test
    fun `chi DUNG Google Maps mang co - VietMap, Waze va app nhac giu nguyen`() {
        val flagged = VoiceAppTargets.ALL.filter { it.clearTaskOnNav }.map { it.key }
        assertEquals(listOf(VoiceAppTargets.GMAPS), flagged, "đích chưa đo mà mang cờ ⇒ đổi đường đang chạy tốt (§6/§14)")
        listOf(VoiceAppTargets.VIETMAP, VoiceAppTargets.WAZE).forEach { key ->
            assertTrue(!VoiceAppTargets.byKey(key)!!.clearTaskOnNav, "$key chưa đo ca CLEAR_TASK — phải giữ nguyên")
        }
    }

    @Test
    fun `mac dinh la false`() {
        val t = VoiceAppTarget("x", "X", VoiceAppKind.NAV, listOf("a.b"), VoiceLaunch.OpenOnly)
        assertTrue(!t.clearTaskOnNav, "một dòng mới trong bảng không được tự mang cờ dọn task")
    }

    /** Cờ nói về *"bắt đầu DẪN"* — một app nhạc mang nó là dữ liệu sai, phải nổ ngay lúc dựng bảng. */
    @Test
    fun `app nhac khong duoc mang co`() {
        assertThrows<IllegalArgumentException> {
            VoiceAppTarget(
                "m", "M", VoiceAppKind.MUSIC, listOf("a.b"),
                VoiceLaunch.Action(VoiceLaunch.ACTION_MEDIA_PLAY_FROM_SEARCH), clearTaskOnNav = true,
            )
        }
    }
}
