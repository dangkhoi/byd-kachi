package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 VOICE-TAUGHT-ACCENT-FUZZY [P3] — luật dấu của tên GIỌNG một âm tiết giữ ở MỌI đường khớp tên app ═══════════════════
 *
 * Senior review wave 2A, ghi chú (a): luật [VoiceHomograph.spelledOk] (OQ2 — `VoiceTaughtAccentMatchTest`) chỉ chạy ở
 * [VoiceGrammar.matchAt]; ba đường khác so trên bản BỎ DẤU nên đi vòng nó. [ĐO off-car 07/10, bộ phân tích thật, trước bản vá]:
 *  • tiền tố ([VoiceAppPrefix.pick]) — dạy «thuỷ» ⇒ *"đưa thúy vào ô số hai"* ⇒ `OpenApp(…, 2)`;
 *  • khớp mờ ([VoiceLastResort] `appFuzzy`) — dạy «trường» ⇒ *"mở trượng vào ô hai"* ⇒ `OpenApp(…, 2)`;
 *  • sau cụm *"bằng"* (`VoiceTailClause.appAfterMarker`, nhánh tên đã dạy) — *"phát nhạc bằng thúy"* ⇒ nhạc trên app ấy.
 * Luật giữ nguyên chữ: chỉ tên GIỌNG một âm tiết bị ràng; chữ MANG dấu phải đúng cách viết đã dạy, không kể CHỖ đặt dấu thanh
 * (*"thủy"* ≡ *"thuỷ"*); chữ không dấu ⇒ không dữ liệu ⇒ hành vi cũ. `com.example.*` là gói thử; `com.spotify.music` là gói của
 * một app trong bảng đích ([VoiceAppTargets]) — cần cho đường *"bằng &lt;app&gt;"*.
 */
class VoiceTaughtAccentFuzzyTest {

    private val spotify = "com.spotify.music"
    private val chrome = "com.example.chrome"
    private val labels = listOf("Spotify" to spotify, "Chrome" to chrome)

    private fun aliases(vararg taught: TaughtName): List<VoiceAppAlias> =
        VoiceAppIndex.build(labels, labels.map { it.second }.toSet(), taught.toList()).aliases

    private fun parse(text: String, aliases: List<VoiceAppAlias>) =
        VoiceIntentParser.parse(text, apps = labels.map { it.first }, aliases = aliases)

    private fun opens(text: String, aliases: List<VoiceAppAlias>, app: String) =
        parse(text, aliases).any { it is VoiceIntent.OpenApp && it.appName == app }

    private fun musicOn(text: String, aliases: List<VoiceAppAlias>, key: String) =
        parse(text, aliases).any { it is VoiceIntent.Media && it.app == key }

    private val thuySpoken get() = aliases(TaughtName(spotify, TaughtSource.SPEECH, "thuỷ", "Spotify"))

    @Test
    fun `tien to - day thuy thi dua thuy SAC vao o so hai KHONG mo`() {
        val a = thuySpoken
        listOf("đưa thúy vào ô số hai", "thúy vào ô số hai", "THÚY VÀO Ô SỐ HAI").forEach { s ->
            assertFalse(opens(s, a, "Spotify"), "«thúy» (sắc) ≠ «thuỷ» (hỏi) ⇒ không phải tên đã dạy: ${parse(s, a)}")
        }
    }

    @Test
    fun `tien to - hai kieu dat dau thanh va chu khong dau van mo`() {
        val a = thuySpoken
        listOf("đưa thuỷ vào ô số hai", "đưa thủy vào ô số hai", "đưa thuy vào ô số hai").forEach { s ->
            assertEquals(listOf(VoiceIntent.OpenApp("Spotify", 2)), parse(s, a), "«$s»")
        }
    }

    @Test
    fun `khop mo - day truong thi mo truong NANG vao o hai KHONG mo`() {
        val a = aliases(TaughtName(chrome, TaughtSource.SPEECH, "trường", "Chrome"))
        assertFalse(opens("mở trượng vào ô hai", a, "Chrome"), "nặng ≠ huyền: ${parse("mở trượng vào ô hai", a)}")
        assertFalse(opens("mở trườngg vào ô hai", a, "Chrome"), "chữ MANG dấu khác cách viết ⇒ không khớp mờ")
        assertEquals(listOf(VoiceIntent.OpenApp("Chrome", 2)), parse("mở trường vào ô hai", a))
        assertTrue(opens("mở truongg vào ô hai", a, "Chrome"), "không dấu ⇒ không dữ liệu ⇒ khớp mờ như cũ")
    }

    @Test
    fun `sau cum bang - ten giong mot am tiet giu luat dau`() {
        val a = thuySpoken
        assertFalse(musicOn("phát nhạc bằng thúy", a, "spotify"), "${parse("phát nhạc bằng thúy", a)}")
        assertTrue(musicOn("phát nhạc bằng thủy", a, "spotify"), "khác CHỖ đặt dấu thanh = cùng chữ")
        assertTrue(musicOn("phát nhạc bằng thuy", a, "spotify"), "không dấu ⇒ như cũ")
    }

    @Test
    fun `ten GO khong bi rang - hanh vi cu`() {
        val typed = aliases(TaughtName(spotify, TaughtSource.TYPED, "thuỷ", "Spotify"))
        assertTrue(opens("đưa thúy vào ô số hai", typed, "Spotify"), "tên GÕ không bảo đảm dấu ⇒ không ràng (OQ2)")
        assertTrue(musicOn("phát nhạc bằng thúy", typed, "spotify"))
    }

    @Test
    fun `bang ung vien loc dung mot loai`() {
        val terms = VoiceGrammar.terms(apps = labels.map { it.first }, aliases = thuySpoken)
        fun labelsFor(first: String?) = VoiceLastResort.candidates(terms, first?.let { VoiceLexicon.tokenize(it).single() })
            .filter { (_, words) -> words == listOf("thuy") }
        assertEquals(1, labelsFor(null).size, "không nêu token ⇒ không lọc (chỗ gọi cũ)")
        assertEquals(1, labelsFor("thủy").size)
        assertEquals(1, labelsFor("thuy").size)
        assertEquals(0, labelsFor("thúy").size)
        assertTrue(VoiceLastResort.candidates(terms, VoiceLexicon.tokenize("thúy").single()).any { it.first.key == "spotify" },
            "cách gọi của bảng đích không mang ràng buộc dấu ⇒ còn nguyên")
    }
}
