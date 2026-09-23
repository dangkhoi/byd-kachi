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
    fun `VietMap geocode HONG thi mo VietMap tron, KHONG nhay GMaps`() {
        val handoffs = ArrayList<VoiceAppIntents.Handoff>()
        val opened = ArrayList<String>()
        dispatch(geocodeResult = null, handoffs = handoffs, opened = opened)
            .runNav(VoiceIntent.Nav(query = "chợ bến thành", app = VoiceAppTargets.VIETMAP), labels)

        assertTrue(handoffs.none { it.pkg == gmapsPkg }, "geocode hỏng KHÔNG được nhảy sang Google Maps — cái nào ra cái đó")
        assertTrue(opened.contains(vietmapPkg), "mở CHÍNH VietMap (app đã chọn), không đổi app")
    }

    @Test
    fun `VietMap geocode OK thi giao toa do cho VietMap`() {
        val handoffs = ArrayList<VoiceAppIntents.Handoff>()
        val coords = VoiceAppIntents.Coords(10.77, 106.70, "Chợ Bến Thành")
        dispatch(geocodeResult = coords, handoffs = handoffs)
            .runNav(VoiceIntent.Nav(query = "chợ bến thành", app = VoiceAppTargets.VIETMAP), labels)

        assertEquals(1, handoffs.size)
        val h = handoffs[0]
        assertEquals(vietmapPkg, h.pkg, "geocode được ⇒ giao thẳng cho VietMap")
        assertEquals(coords, h.coords, "kèm toạ độ đã giải")
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
    fun `khong neu app + mac dinh VietMap + geocode HONG ⇒ mo VietMap tron, KHONG GMaps`() {
        val handoffs = ArrayList<VoiceAppIntents.Handoff>()
        val opened = ArrayList<String>()
        dispatch(geocodeResult = null, handoffs = handoffs, opened = opened, navDefault = "vietmap")
            .runNav(VoiceIntent.Nav(query = "chợ bến thành", app = null), labels)

        assertTrue(handoffs.none { it.pkg == gmapsPkg }, "mặc định VietMap + geocode hỏng KHÔNG được nhảy GMaps")
        assertTrue(opened.contains(vietmapPkg), "mở CHÍNH VietMap (app mặc định)")
    }
}
