package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceLaunch
import com.byd.clusternav.launcher.voice.VoiceMediaOp
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    /**
     * ═══ [ĐO xe 2026-09-18] UA của resolver phải là UA **MÁY TÍNH** ══════════════════════════════════════
     *
     * Hai bài trên chứng minh *"giải được id thì mở watch"*, nhưng trên xe nhánh ấy **chưa bao giờ chạy**: mọi
     * lượt đều bắn `MEDIA_PLAY_FROM_SEARCH` (đường lùi) ⇒ resolver trả `null` ở mọi lượt. Gốc không nằm ở regex
     * mà ở **UA**: [ĐO off-car, mô phỏng đúng lớp đó] UA di động ⇒ YouTube trả trang consent/mobile JS-only, bắt
     * được **0** videoId; UA máy tính ⇒ **225**.
     *
     * ⇒ *"không có chữ Mobile"* là một tính chất phải khoá bằng bài canh, vì nó **im lặng**: đổi UA về di động thì
     * code vẫn chạy, test hành vi vẫn xanh, chỉ tính năng "phát luôn" chết. Quét trên `codeOf` (đã bỏ chú thích)
     * nên bảng so sánh trong KDoc của lớp ấy không làm bài này báo sai.
     * Bằng chứng: `docs/diagnostics/oncar-voice-music-vietmap-2026-09-18.md` §BUG B.
     */
    @Test
    fun `UA cua resolver la UA may tinh, khong phai di dong`() {
        val src = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceYoutubeResolver.kt")
        val ua = Regex("""BROWSER_UA\s*=\s*([\s\S]*?)\n\s*\n""").find(src)?.groupValues?.get(1)
            ?: error("không tìm thấy hằng BROWSER_UA — bài canh đang quét vùng không tồn tại")
        assertFalse(ua.contains("Mobile"), "UA di động ⇒ YouTube trả trang JS-only, 0 videoId: $ua")
        assertFalse(ua.contains("Android"), "UA di động ⇒ YouTube trả trang JS-only, 0 videoId: $ua")
        assertTrue(ua.contains("Windows NT"), "phải là UA máy tính — [ĐO] chỉ UA đó mới có `ytInitialData`: $ua")
    }

    /**
     * ═══ [ĐO 2026-09-18] UA đúng mà trần đọc quá NGẮN thì vẫn không phát được ════════════════════════════
     *
     * Bài canh trên chốt UA, nhưng UA chỉ là **một nửa**: đo trang thật (4 truy vấn) thì `"videoId"` đầu nằm ở
     * **763–787 K ký tự**, còn bản 1.75 đặt trần **600 K** kèm lý do *"id nằm sớm trong `ytInitialData`"* — một
     * giả định chưa ai đo. Với trần đó, resolver trả `null` ở mọi lượt **kể cả sau khi đã sửa UA**.
     *
     * Hai tính chất phải giữ cùng nhau, nên khoá cùng một chỗ:
     *  1. trần đọc **vượt** mốc đã đo (+ biên),
     *  2. lượt đọc đi **theo dòng chảy** — không gom cả trang vào một `StringBuilder` (1,3 M ký tự ≈ 2,6 MB, đúng
     *     loại áp lực RAM đang là gốc của `SEGV_MAPERR` trong Piper).
     */
    @Test
    fun `tran doc vuot moc da do va khong gom ca trang vao RAM`() {
        val src = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceYoutubeResolver.kt")
        val cap = Regex("""MAX_CHARS\s*=\s*([\d_]+)""").find(src)?.groupValues?.get(1)?.replace("_", "")?.toInt()
            ?: error("không tìm thấy hằng MAX_CHARS — bài canh đang quét vùng không tồn tại")
        assertTrue(
            cap >= 1_000_000,
            "trần đọc $cap ký tự ≤ mốc videoId đã đo trên trang thật (763–787 K) ⇒ luôn trả null, luôn lùi",
        )
        assertTrue(
            src.contains("YoutubeSearchParse.firstVideoId(it, MAX_CHARS)"),
            "phải quét theo dòng chảy ở `:core` (dừng ở khớp đầu), không tự gom trang ở đây",
        )
        assertFalse(
            src.contains("StringBuilder"),
            "gom cả trang vào RAM: trang 1,3 M ký tự ≈ 2,6 MB — thứ resolver không được phép giữ",
        )
    }
}
