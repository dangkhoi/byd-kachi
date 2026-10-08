package com.byd.clusternav.launcher.trip

import com.byd.clusternav.launcher.voice.VoiceAppTargets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.97 · YT-SAVE-ON-CHANGE — anh em báo 08/10 (2.96): phát tiếp "không đúng bài". [SUY] nghe Mix, bài vừa chuyển chưa tới nhịp
 * 60 s đã tắt máy ⇒ mẫu cuối còn là bài trước. Bên lưu nghe sự kiện đổi bài của ĐÚNG gói app đích rồi lưu sau một nhịp ngắn.
 */
class YoutubeSaveOnChangeTest {

    @Test
    fun `chi nghe dung goi cua app dich phat tiep`() {
        val expected = YoutubeResume.watchedTargets().flatMap { it.packages }.toSet()
        assertEquals(expected, YoutubeResume.watchedPackages())
        assertTrue(VoiceAppTargets.byKey(VoiceAppTargets.YOUTUBE)!!.packages.all { it in YoutubeResume.watchedPackages() })
    }

    @Test
    fun `luu khi doi bai som hon han nhip dinh ky`() {
        assertTrue(YoutubeResume.CHANGE_SETTLE_MS in 500L..5_000L, "đủ để metadata bài mới ổn định, ngắn hơn nhiều so với 60 s")
        assertTrue(YoutubeResume.CHANGE_SETTLE_MS * 10 <= YoutubeResume.SAMPLE_EVERY_MS)
    }
}
