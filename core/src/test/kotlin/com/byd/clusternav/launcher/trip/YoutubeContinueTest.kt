package com.byd.clusternav.launcher.trip

import com.byd.clusternav.launcher.voice.VoiceAppTargets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.99 · R1 (spec `docs/specs/kachi-299-plan.html`) — KHOÁ lỗi owner 10/10 trên 2.98: *"auto youtube … hát đúng 1 bài xong nghỉ,
 * không hát tiếp auto sang bài khác như khi mở youtube bình thường"*.
 *
 * Gốc [ĐO máy ảo 10/10, fixture `diagnostics/youtube-continue-2026-10-10.txt` — `dumpsys media_session` nguyên văn]:
 *  1. Lượt phát tiếp 2.96–2.98 gửi K4-VIEW `watch?v=<id>` + `--ez force_fullscreen true` (R10). Extra đó mở chế độ MỘT VIDEO của
 *     YouTube: hết bài `state=1` đứng yên, không sang bài kế (fixture 0/3 khối `force_fullscreen*`; tổng phép đo 0/9).
 *  2. Bỏ extra vẫn chưa đủ: VIEW `watch?v=<id>` trơn từ ngoài app chỉ đôi khi có hàng "kế tiếp" (fixture `plain*` 2/3; tổng 6/16).
 *  3. `&list=RD<id>` (Mix YouTube tự dựng theo chính bài) ⇒ có "kế tiếp" ngay khi phát, hết bài sang bài kế (fixture 2/2; tổng 7/7),
 *     kể cả tua NGAY khi phát như `TripMusicResume.finish`.
 */
class YoutubeContinueTest {

    private val youtube = requireNotNull(VoiceAppTargets.byKey(VoiceAppTargets.YOUTUBE))
    private val ytMusic = requireNotNull(VoiceAppTargets.byKey(VoiceAppTargets.YT_MUSIC))
    private val id = "psZ1g9fMfeo"
    private val watch = "https://www.youtube.com/watch?v=$id"

    /** Một khối đo của fixture: nhãn + chuỗi tiêu đề phiên (theo thứ tự) SAU lệnh tua gần cuối. */
    private data class Run(val label: String, val titles: List<String>) {
        /** Hết bài mà phiên đổi sang bài khác = YouTube tự chuyển bài. */
        val continued: Boolean get() = titles.distinct().size > 1
    }

    private fun runs(): List<Run> {
        val text = requireNotNull(javaClass.getResourceAsStream("/diagnostics/youtube-continue-2026-10-10.txt")) { "thiếu fixture" }
            .bufferedReader().readText()
        val out = mutableListOf<Run>()
        var label: String? = null
        var afterSeek = false
        val titles = mutableListOf<String>()
        fun flush() { label?.let { out += Run(it, titles.toList()) }; titles.clear(); afterSeek = false }
        for (line in text.lines()) {
            val head = Regex("^# ([a-z_-]+r?\\d?):").find(line)
            when {
                head != null && line.contains("am start") -> { flush(); label = head.groupValues[1] }
                line.startsWith("# seekTo") -> afterSeek = true
                afterSeek && line.startsWith("metadata:") ->
                    titles += line.substringAfter("description=").substringBefore(", ")
            }
        }
        flush()
        return out
    }

    @Test
    fun `fixture that - force_fullscreen dung sau mot bai, Mix sang bai ke, VIEW tron khong chac`() {
        val all = runs()
        assertEquals(8, all.size, "đủ 8 khối đo: ${all.map { it.label }}")
        val fs = all.filter { it.label.startsWith("force_fullscreen") }
        val mix = all.filter { it.label.startsWith("mix") }
        val plain = all.filter { it.label.startsWith("plain") }
        assertEquals(3, fs.size); assertEquals(2, mix.size); assertEquals(3, plain.size)
        assertTrue(fs.none { it.continued }, "force_fullscreen = chế độ một video: ${fs.map { it.titles.distinct() }}")
        assertTrue(mix.all { it.continued }, "Mix: hết bài sang bài kế: ${mix.map { it.titles.distinct() }}")
        assertTrue(plain.any { !it.continued } && plain.any { it.continued }, "VIEW trơn: lúc có lúc không — không dựa vào nó được")
    }

    @Test
    fun `phat tiep YouTube - khong con extra toan man (che do mot video)`() {
        assertNull(youtube.watchFullscreenExtra, "force_fullscreen làm YouTube dừng sau MỘT bài [ĐO 0/9]")
        // Lệnh K4-VIEW của phát tiếp (đường `TripMusicRun.viewWhenReady` lấy extra từ bảng) không mang `--ez` nào.
        val url = requireNotNull(TripMusicPlan.continuousUrl(youtube, watch))
        assertFalse(TripMusicPlan.viewCmd(283, url, youtube.packages.first(), youtube.watchFullscreenExtra).contains("--ez"))
    }

    @Test
    fun `link YouTube di khuon lien tuc - Mix cua CHINH bai, cung video id`() {
        assertEquals("$watch&list=RD$id", TripMusicPlan.continuousUrl(youtube, watch))
        val cont = requireNotNull(TripMusicPlan.continuousUrl(youtube, watch))
        assertTrue(TripMusicPlan.safeWatchUrl(cont))
        assertEquals(id, TripMusicPlan.videoIdOf(cont), "bài đầu vẫn đúng bài đã lưu ⇒ chờ đúng bài + tua không đổi")
        // Đã liên tục thì giữ nguyên (đường hoãn R2c có thể gọi lại).
        assertEquals(cont, TripMusicPlan.continuousUrl(youtube, cont))
        // Lệnh K4-VIEW: `&` nằm TRONG cặp nháy đơn ⇒ shell không tách lệnh.
        assertEquals(
            "am start --display 283 -a android.intent.action.VIEW -d '$cont' -p com.google.android.youtube",
            TripMusicPlan.viewCmd(283, cont, "com.google.android.youtube"),
        )
    }

    @Test
    fun `app khong khai khuon lien tuc - link giu nguyen (YT Music tu phat tiep, chua do Mix)`() {
        val m = "https://music.youtube.com/watch?v=$id"
        assertNull(ytMusic.watchContinue)
        assertEquals(m, TripMusicPlan.continuousUrl(ytMusic, m))
        assertNull(TripMusicPlan.continuousUrl(youtube, null))
        // Link không qua rào ⇒ không dựng gì từ nó.
        assertEquals("intent://x", TripMusicPlan.continuousUrl(youtube, "intent://x"))
    }

    @Test
    fun `rao link - list chi duoc la Mix cua CUNG bai`() {
        assertTrue(TripMusicPlan.safeWatchUrl("$watch&list=RD$id"))
        assertFalse(TripMusicPlan.safeWatchUrl("$watch&list=RDaaaaaaaaaaa"), "Mix của bài khác")
        assertFalse(TripMusicPlan.safeWatchUrl("$watch&list=PL0123456789"), "danh sách tuỳ ý")
        assertFalse(TripMusicPlan.safeWatchUrl("$watch&list=RD$id'; reboot"))
        assertFalse(TripMusicPlan.safeWatchUrl("$watch&t=12s"))
    }

    /** [ĐO máy ảo 10/10] `playFromUri(watch?v=…&list=RD…)` BỎ hàng chờ (dừng sau bài, 1/1) ⇒ link có hàng chờ luôn đi K4-VIEW. */
    @Test
    fun `link co hang cho - khong giao qua playFromUri, di K4-VIEW`() {
        val cont = "$watch&list=RD$id"
        val uriSession = TripMusicPlan.Session("com.google.android.youtube", playing = false, acceptsUri = true)
        assertEquals(TripMusicPlan.Play.View(cont), TripMusicPlan.play(cont, uriSession))
        assertEquals(TripMusicPlan.Play.FromUri(watch), TripMusicPlan.play(watch, uriSession), "link trơn: đường cũ không đổi")
        assertEquals(TripMusicPlan.Play.View(cont), TripMusicPlan.play(cont, null))
    }
}
