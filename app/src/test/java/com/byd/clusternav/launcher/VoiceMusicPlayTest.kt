package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceLaunch
import com.byd.clusternav.launcher.voice.VoiceMediaOp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ [owner 2026-09-18] "phát bài hát LUÔN" — bài chạy THẬT trên [VoiceTargetDispatch] ════════════════════════
 *
 * Mục tiêu: search ra kết quả rồi PHÁT LUÔN. Cơ chế Kiki (giải video_id → mở `watch?v=<id>` = tự phát), làm
 * on-device. Bài kiểm: giải được id → mở URL watch; giải KHÔNG được → lùi `MEDIA_PLAY_FROM_SEARCH` với TÊN bài.
 */
class VoiceMusicPlayTest {

    private val ytPkg = "com.google.android.youtube"
    private val labels = mapOf("YouTube" to ytPkg)

    private fun dispatch(vid: String?, handoffs: MutableList<VoiceAppIntents.Handoff>) =
        VoiceTargetDispatch(
            state = { HomeUiState() },
            media = { error("QUERY không chạm transport") },
            openApp = { true },
            confirm = { _, y, _ -> y() },
            say = {},
            sendToApp = { h -> handoffs += h; true },
            geocode = { null },
            mediaPackage = { null },
            onUi = { it() },
            background = { it() },   // chạy đồng bộ để tất định
            navDefault = { null },
            resolveVideo = { vid },
        )

    @Test
    fun `giai duoc video_id thi mo URL watch (tu phat)`() {
        val handoffs = ArrayList<VoiceAppIntents.Handoff>()
        dispatch(vid = "dQw4w9WgXcQ", handoffs = handoffs)
            .runMedia(VoiceIntent.Media(VoiceMediaOp.QUERY, "diễm xưa", VoiceAppTargets.YOUTUBE), labels)

        assertEquals(1, handoffs.size)
        val h = handoffs[0]
        assertEquals(ytPkg, h.pkg)
        assertTrue((h.launch as VoiceLaunch.Uri).template.contains("watch?v="), "phải mở URL watch để TỰ PHÁT")
        assertEquals("dQw4w9WgXcQ", h.query, "query của watch = video_id đã giải, không phải tên bài")
    }

    @Test
    fun `giai khong duoc video_id thi lui MEDIA_PLAY_FROM_SEARCH voi TEN bai`() {
        val handoffs = ArrayList<VoiceAppIntents.Handoff>()
        dispatch(vid = null, handoffs = handoffs)
            .runMedia(VoiceIntent.Media(VoiceMediaOp.QUERY, "diễm xưa", VoiceAppTargets.YOUTUBE), labels)

        assertEquals(1, handoffs.size)
        val h = handoffs[0]
        assertTrue(
            (h.launch as VoiceLaunch.Action).action.contains("MEDIA_PLAY_FROM_SEARCH"),
            "giải hỏng ⇒ lùi search-play",
        )
        assertEquals("diễm xưa", h.query, "lùi dùng TÊN bài (không phải video_id)")
    }
}
