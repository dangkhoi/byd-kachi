package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceIntentParser
import com.byd.clusternav.launcher.voice.VoiceLaunch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 3.0 R1 `NAV-MULTI-PROVIDER-ALL3` — câu nhiều app ⇒ MỖI app một deep-link CÓ điểm đến (dispatch thật) ═══════════════════════
 *
 * Spec `docs/specs/kachi-300-plan.html` R1. [ĐO xe 10/10]: VietMap chỉ nhận `MAIN/LAUNCHER` (không điểm đến). Bài chạy câu test của backlog
 * qua bộ phân tích THẬT rồi [VoiceTargetDispatch.runNav] thật (cùng giả lập của [VoiceNavFallbackTest]) và đòi: ba handoff, ba gói, mỗi
 * handoff là đường DẪN của chính app ấy (GMaps `google.navigation:q=` · VietMap `vietmaplive://…lat/lng` kèm toạ độ đã tra · Waze
 * `waze://?q=…&navigate=yes`), không app nào bị mở trơn.
 */
class VoiceNavMultiProviderDispatchTest {

    private val gmaps = "com.google.android.apps.maps"
    private val vietmap = "vn.vietmap.live"
    private val waze = "com.waze"
    private val labels = mapOf("Google Maps" to gmaps, "VietMap" to vietmap, "Waze" to waze)

    @Test
    fun `Ben Thanh bang Google Maps, VietMap, va Waze ⇒ ba handoff dan duong, khong mo tron`() {
        val handoffs = ArrayList<VoiceAppIntents.Handoff>()
        val opened = ArrayList<String>()
        val geocoded = ArrayList<String>()
        val coords = VoiceAppIntents.Coords(10.772, 106.698, "Chợ Bến Thành")   // toạ độ CÔNG CỘNG (chợ Bến Thành)
        val d = VoiceTargetDispatch(
            state = { HomeUiState() },
            media = { error("không chạm nhạc") },
            openApp = { opened += it; true },
            confirm = { _, y, _ -> y() },
            say = {},
            sendToApp = { handoffs += it; true },
            geocode = { q -> geocoded += q; coords },
            mediaPackage = { null },
            onUi = { it() },
            background = { it() },
        )
        val intents = VoiceIntentParser.parse(
            "dẫn đường đến chợ Bến Thành bằng Google Maps, VietMap, và Waze",
            apps = labels.keys.toList(),
        )
        intents.forEach { d.runNav(it as VoiceIntent.Nav, labels) }

        assertEquals(listOf(gmaps, vietmap, waze), handoffs.map { it.pkg }, "đúng ba app, đúng thứ tự nói")
        assertTrue(opened.isEmpty(), "không app nào bị mở trơn (MAIN/LAUNCHER)")
        assertEquals(listOf("chợ Bến Thành"), geocoded, "chỉ VietMap cần tra toạ độ, và tra ĐIỂM ĐẾN SẠCH (không dính 'bằng …')")
        val byPkg = handoffs.associateBy { it.pkg }
        assertTrue((byPkg.getValue(gmaps).launch as VoiceLaunch.Uri).template.startsWith("google.navigation:q="))
        assertTrue((byPkg.getValue(vietmap).launch as VoiceLaunch.Uri).template.startsWith("vietmaplive://companion/navigation?lat="))
        assertEquals(coords, byPkg.getValue(vietmap).coords)
        assertTrue((byPkg.getValue(waze).launch as VoiceLaunch.Uri).template.startsWith("waze://?q="))
        assertTrue(handoffs.all { it.query == "chợ Bến Thành" }, "mọi app nhận cùng điểm đến sạch")
    }
}
