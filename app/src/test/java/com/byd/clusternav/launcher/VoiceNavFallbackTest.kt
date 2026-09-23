package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceLaunch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ [owner 2026-09-18] App dẫn đường MẶC ĐỊNH + KHÔNG fallback chéo — bài chạy THẬT trên [VoiceTargetDispatch] ══
 *
 * Owner: *"nói dẫn đường không nêu app thì lấy app mặc định; có tên thì app đó; KHÔNG fallback kiểu mở VietMap
 * không được thì lại mở GMaps — cái nào ra cái đó thôi."* Bài dựng dispatch thật (lớp thuần trên đường này) và
 * kiểm: geocode hỏng KHÔNG nhảy sang app khác, và câu không nêu app dùng đúng app mặc định.
 */
class VoiceNavFallbackTest {

    private val gmapsPkg = "com.google.android.apps.maps"
    private val vietmapPkg = "vn.vietmap.live"
    private val labels = mapOf("VietMap" to vietmapPkg, "Google Maps" to gmapsPkg)

    private fun dispatch(
        geocodeResult: VoiceAppIntents.Coords?,
        handoffs: MutableList<VoiceAppIntents.Handoff>,
        opened: MutableList<String> = ArrayList(),
        navDefault: String? = null,
    ) = VoiceTargetDispatch(
        state = { HomeUiState() },
        media = { error("bài này không chạm nhạc") },
        openApp = { pkg -> opened += pkg; true },
        confirm = { _, y, _ -> y() },   // đồng ý ngay khi có hộp đọc-lại
        say = {},
        sendToApp = { h -> handoffs += h; true },
        geocode = { geocodeResult },
        mediaPackage = { null },
        onUi = { it() },
        background = { it() },   // chạy đồng bộ để bài tất định
        navDefault = { navDefault },
    )

    @Test
    fun `VietMap dan bang CHU — giao handoff text toi VietMap, KHONG geocode, KHONG GMaps`() {
        val handoffs = ArrayList<VoiceAppIntents.Handoff>()
        val opened = ArrayList<String>()
        dispatch(geocodeResult = null, handoffs = handoffs, opened = opened)
            .runNav(VoiceIntent.Nav(query = "chợ bến thành", app = VoiceAppTargets.VIETMAP), labels)

        assertEquals(1, handoffs.size, "VietMap nay nhận chữ ⇒ giao thẳng, không cần geocode")
        assertEquals(vietmapPkg, handoffs[0].pkg, "giao đúng VietMap")
        assertTrue(
            (handoffs[0].launch as VoiceLaunch.Uri).template.startsWith("https://www.google.com/maps/"),
            "URL chữ chuẩn Google (VietMap tự geocode)",
        )
        assertTrue(handoffs.none { it.pkg == gmapsPkg }, "KHÔNG nhảy sang Google Maps — cái nào ra cái đó")
    }

    @Test
    fun `khong neu app thi dung app MAC DINH (gmaps) dan bang chu`() {
        val handoffs = ArrayList<VoiceAppIntents.Handoff>()
        dispatch(geocodeResult = null, handoffs = handoffs, navDefault = "gmaps")
            .runNav(VoiceIntent.Nav(query = "chợ bến thành", app = null), labels)

        assertEquals(1, handoffs.size, "không nêu app ⇒ dùng app mặc định (GMaps nhận chữ thẳng, không cần geocode)")
        assertEquals(gmapsPkg, handoffs[0].pkg)
        assertTrue((handoffs[0].launch as VoiceLaunch.Uri).template.startsWith("google.navigation:q="))
    }

    @Test
    fun `khong neu app + mac dinh VietMap ⇒ VietMap dan bang chu, KHONG GMaps`() {
        val handoffs = ArrayList<VoiceAppIntents.Handoff>()
        dispatch(geocodeResult = null, handoffs = handoffs, navDefault = "vietmap")
            .runNav(VoiceIntent.Nav(query = "chợ bến thành", app = null), labels)

        assertEquals(1, handoffs.size, "mặc định VietMap ⇒ giao chữ thẳng cho VietMap")
        assertEquals(vietmapPkg, handoffs[0].pkg)
        assertTrue(handoffs.none { it.pkg == gmapsPkg }, "KHÔNG nhảy GMaps")
    }
}
