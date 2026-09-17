package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceLaunch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ [ĐO xe 2026-09-17 · log owner] VietMap "đơ" + GMaps "chưa dẫn" — bài chạy THẬT trên [VoiceTargetDispatch] ══
 *
 * Owner báo trên xe: dẫn đường bằng VietMap thì đơ (geocode kẹt/mạng treo). Luật mới: geocode hỏng/timeout ⇒
 * **lùi về Google Maps dẫn bằng CHỮ** (`google.navigation:q=`, Google tự geocode) — người lái luôn nhận được
 * dẫn đường, không kẹt. Bài dựng dispatch thật (lớp thuần, không chạm `android.*` trên đường này) và bắt đúng ý
 * định `Nav(app=vietmap)`.
 */
class VoiceNavFallbackTest {

    private val gmapsPkg = "com.google.android.apps.maps"
    private val labels = mapOf("VietMap" to "vn.vietmap.live", "Google Maps" to gmapsPkg)

    private fun dispatch(geocodeResult: VoiceAppIntents.Coords?, handoffs: MutableList<VoiceAppIntents.Handoff>) =
        VoiceTargetDispatch(
            state = { HomeUiState() },
            media = { error("bài này không chạm nhạc") },
            openApp = { true },
            confirm = { _, y, _ -> y() },   // đồng ý ngay khi có hộp đọc-lại
            say = {},
            sendToApp = { h -> handoffs += h; true },
            geocode = { geocodeResult },
            mediaPackage = { null },
            onUi = { it() },
            background = { it() },   // chạy đồng bộ để bài tất định
        )

    @Test
    fun `VietMap geocode HONG thi lui ve Google Maps dan bang chu`() {
        val handoffs = ArrayList<VoiceAppIntents.Handoff>()
        dispatch(geocodeResult = null, handoffs = handoffs)
            .runNav(VoiceIntent.Nav(query = "chợ bến thành", app = VoiceAppTargets.VIETMAP), labels)

        assertEquals(1, handoffs.size, "geocode hỏng phải vẫn dẫn được (qua GMaps), không kẹt/đơ")
        val h = handoffs[0]
        assertEquals(gmapsPkg, h.pkg, "lùi về Google Maps")
        assertTrue(
            (h.launch as VoiceLaunch.Uri).template.startsWith("google.navigation:q="),
            "phải DẪN bằng chữ qua GMaps (Google tự geocode), không mở VietMap trơn",
        )
        assertEquals("chợ bến thành", h.query, "giữ nguyên điểm đến người lái nói")
    }

    @Test
    fun `VietMap geocode OK thi giao toa do cho VietMap`() {
        val handoffs = ArrayList<VoiceAppIntents.Handoff>()
        val coords = VoiceAppIntents.Coords(10.77, 106.70, "Chợ Bến Thành")
        dispatch(geocodeResult = coords, handoffs = handoffs)
            .runNav(VoiceIntent.Nav(query = "chợ bến thành", app = VoiceAppTargets.VIETMAP), labels)

        assertEquals(1, handoffs.size)
        val h = handoffs[0]
        assertEquals("vn.vietmap.live", h.pkg, "geocode được ⇒ giao thẳng cho VietMap")
        assertEquals(coords, h.coords, "kèm toạ độ đã giải")
    }
}
