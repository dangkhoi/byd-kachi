package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.91 VOICE-APP-NAMES · C4 — bảng câu R6 (spec §4.6 *"Các dạng câu phải chạy"*): tên đã dạy là cụm `APP` hạng nhất,
 * `id` = nhãn thật, mọi luật đang chạy (dãy dài nhất · mệnh đề ô · `APP_CLOSE` · khớp mờ duy nhất) tự áp. Câu
 * *"nep leag"* = chuỗi mô hình in cho *"netflix"* trên xe 27/09 [SUY — log cũ]; gói `com.example.*` là bộ thử.
 */
class VoiceTaughtAliasParseTest {

    private val flix = "com.example.flix"
    private val spotify = VoiceAppTargets.byKey(VoiceAppTargets.SPOTIFY)!!.packages.first()
    private val labels = listOf("Netflix" to flix, "Spotify" to spotify, "Cài đặt" to "com.android.settings")
    private val taught = listOf(
        TaughtName(flix, TaughtSource.SPEECH, "nep leag", "Netflix"),
        TaughtName(spotify, TaughtSource.SPEECH, "xanh lá cây", "Spotify"),
    )
    private val idx = VoiceAppIndex.build(labels, labels.map { it.second }.toSet(), taught)

    private fun parse(text: String, aliases: List<VoiceAppAlias> = idx.aliases): List<VoiceIntent> =
        VoiceIntentParser.parse(text, apps = idx.keys.keys.toList(), aliases = aliases)

    private fun one(text: String) = parse(text).single()

    @Test
    fun `cac dang cau goi app bang ten da day`() {
        assertEquals(VoiceIntent.OpenApp("Netflix"), one("mở nep leag"))
        assertEquals(VoiceIntent.OpenApp("Netflix", 2), one("đưa nep leag vào ô số hai"))
        assertEquals(VoiceIntent.OpenApp("Netflix", 1), one("nep leag vào ô số một"))
        assertEquals(VoiceIntent.OpenApp("Netflix"), one("mở ứng dụng nep leag"))
        assertEquals(VoiceIntent.OpenApp("Netflix"), one("bật nep leag"))
    }

    @Test
    fun `dong ten da day ra APP_CLOSE khong mo app`() {
        val got = one("đóng nep leag")
        assertTrue(got is VoiceIntent.Unknown && got.reason == VoiceUnknownReason.APP_CLOSE, "$got")
    }

    @Test
    fun `phat nhac tren ten da day cua app dich giao dung ma dich`() {
        // Tên đã dạy cố ý XA mọi cách nói của bảng đích ⇒ chỉ đường tên-đã-dạy của `appAfterMarker` giải được.
        assertEquals(VoiceIntent.Media(VoiceMediaOp.PLAY, app = VoiceAppTargets.SPOTIFY), one("phát nhạc trên xanh lá cây"))
        val q = one("phát bài hoa nở bằng xanh lá cây")
        assertTrue(q is VoiceIntent.Media && q.app == VoiceAppTargets.SPOTIFY && q.query == "hoa nở", "$q")
        val before = parse("phát bài hoa nở bằng xanh lá cây", emptyList()).first()
        assertTrue(before is VoiceIntent.Media && before.app == null, "mốc gốc: không có tên đã dạy thì không cắt — $before")
    }

    @Test
    fun `ten da day cua app khong thuoc bang dich sau bang khong bi cat`() {
        val got = one("dẫn đường đến nep leag")
        assertEquals(VoiceIntent.Nav("nep leag"), got)
        val got2 = one("dẫn đường tới chợ bằng nep leag")
        assertEquals(VoiceIntent.Nav("chợ bằng nep leag"), got2)
    }

    @Test
    fun `khop mo tang bon chi khi duy nhat va duoi san thi khong mo`() {
        assertEquals("Netflix", (one("mở nep leog") as VoiceIntent.OpenApp).appName)
        val short = one("mở nep")
        assertTrue(short is VoiceIntent.Unknown, "$short")
    }

    @Test
    fun `hoi lai mo gi roi tra loi bang ten da day`() {
        val combined = VoiceClarify.combine(listOf("mở"), "nep leag")
        assertEquals(VoiceIntent.OpenApp("Netflix"), parse(combined).single())
    }

    @Test
    fun `cau lenh xe khong doi khi co ten da day`() {
        listOf("bật đèn đọc", "xem pin", "mở cốp", "tắt điều hòa", "dẫn đường đến nhà sách").forEach { s ->
            assertEquals(parse(s, emptyList()), parse(s), "«$s» phải không đổi khi có tên đã dạy")
        }
    }

    @Test
    fun `khong co ten da day thi khong mo duoc - moc goc`() {
        assertTrue(parse("mở nep leag", emptyList()).single() is VoiceIntent.Unknown)
    }
}
