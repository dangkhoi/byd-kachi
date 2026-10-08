package com.byd.clusternav.launcher.voice

import java.text.Normalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 VOICE-ROI-CONNECTOR — liên từ đồng hình sau khi bỏ dấu ═══════════════════════════════════════════════════
 *
 * Khoá lỗi [ĐO off-car 06/10] (spec `kachi-290-voice-app-names.html` §9 F4, backlog `VOICE-ROI-CONNECTOR`): *"phát bài mưa
 * rơi bằng spotify"* ra `[Media(QUERY "mưa"), OpenApp(Spotify)]` — *"rơi"* bỏ dấu = `roi` = *"rồi"* (liên từ) tách câu,
 * rồi vế *"bằng spotify"* được phép chữa chính tả đọc thành *"bật spotify"*. Luật mới ([VoiceHomograph]): chữ MANG dấu chỉ
 * là liên từ khi viết đúng *"rồi"* hay *"và"*; chữ KHÔNG dấu giữ hành vi cũ (không dữ liệu thì không đoán).
 */
class VoiceHomographTest {

    private val spotify = VoiceAppTargets.SPOTIFY

    @Test
    fun `ten bai co chu roi khong bi tach thanh hai lenh`() {
        val want = listOf(VoiceIntent.Media(VoiceMediaOp.QUERY, "mưa rơi", spotify))
        assertEquals(want, VoiceIntentParser.parse("phát bài mưa rơi bằng spotify"))
        // Nhãn app MÁY cũng dính (backlog) — có nhãn "Spotify" trên máy vẫn phải là MỘT lệnh nhạc.
        assertEquals(want, VoiceIntentParser.parse("phát bài mưa rơi bằng spotify", apps = listOf("Spotify")))
        // Mô hình nghe in HOA có dấu (tầng nghe hạ chữ thường) — cùng kết quả.
        assertEquals(want, VoiceIntentParser.parse("PHÁT BÀI MƯA RƠI BẰNG SPOTIFY".lowercase()))
    }

    @Test
    fun `lien tu that van tach cau ghep nhu cu`() {
        val want = listOf(VoiceIntent.Macro("mac_win_close_all"), VoiceIntent.Control("readl", 1))
        assertEquals(want, VoiceIntentParser.parse("Đóng hết kính rồi bật đèn đọc"))
        assertEquals(want, VoiceIntentParser.parse("ĐÓNG HẾT KÍNH RỒI BẬT ĐÈN ĐỌC".lowercase()))
        assertEquals(
            listOf(VoiceIntent.Control("readl", 1), VoiceIntent.Control("headl", 0)),
            VoiceIntentParser.parse("Bật đèn đọc và tắt đèn pha"),
        )
    }

    /** Gõ KHÔNG dấu (bàn phím xe, kịch bản test): không có dữ liệu để tách đồng hình ⇒ hành vi cũ từng byte. */
    @Test
    fun `go khong dau giu hanh vi cu - roi van la lien tu`() {
        assertEquals(
            listOf(VoiceIntent.Macro("mac_win_close_all"), VoiceIntent.Control("readl", 1)),
            VoiceIntentParser.parse("dong het kinh roi bat den doc"),
        )
        assertEquals(2, VoiceIntentParser.parse("bat den doc va tat den pha").size)
    }

    /**
     * Tên bài có *"rơi"* không còn bị cắt đôi ở chữ ấy — và một câu nhạc KHÔNG liên từ thì đi đúng luật chung của mọi tên
     * bài (cả câu là từ vựng mở, KDoc [VoiceIntentParser.parse]): *"phát bài lá rơi bật đèn đọc"* giờ ra y như *"phát bài
     * hạ trắng bật đèn đọc"*. Trước bản vá, *"rơi"* = liên từ nên câu ấy may mắn tách được — nhưng tên bài mất nửa.
     */
    @Test
    fun `cau nhac co chu roi di dung luat chung cua ten bai`() {
        assertEquals(
            VoiceIntentParser.parse("phát bài hạ trắng bật đèn đọc").map { it::class },
            VoiceIntentParser.parse("phát bài lá rơi bật đèn đọc").map { it::class },
        )
        assertEquals(listOf(VoiceIntent.Media(VoiceMediaOp.QUERY, "lá rơi")), VoiceIntentParser.parse("phát bài lá rơi"))
        // Liên từ THẬT giữa hai vế vẫn tách.
        assertEquals(
            listOf(VoiceIntent.Media(VoiceMediaOp.QUERY, "lá rơi"), VoiceIntent.Control("readl", 1)),
            VoiceIntentParser.parse("phát bài lá rơi rồi bật đèn đọc"),
        )
    }

    /** Câu MIX không liên từ ([VoiceControlParse.multiVerbSplit]): chữ nối THẬT ở đuôi vế được cắt, chữ đồng hình thì không. */
    @Test
    fun `cau MIX chi cat chu noi that o duoi ve`() {
        fun segs(s: String) = VoiceControlParse.multiVerbSplit(VoiceLexicon.tokenize(s))!!.map { seg -> seg.joinToString(" ") { it.raw } }
        assertEquals(listOf("bật đèn đọc", "tắt máy lạnh"), segs("bật đèn đọc xong tắt máy lạnh"))
        assertEquals(listOf("bật đèn đọc", "tắt máy lạnh"), segs("bật đèn đọc rồi tắt máy lạnh"))
        assertEquals(listOf("bat den doc", "tat may lanh"), segs("bat den doc roi tat may lanh"), "không dấu ⇒ như cũ")
        assertEquals(listOf("bật đèn đọc rơi", "tắt máy lạnh"), segs("bật đèn đọc rơi tắt máy lạnh"), "«rơi» không phải chữ nối")
        assertEquals(
            listOf(VoiceIntent.Control("readl", 1), VoiceIntent.Control("ac_auto", 0)).map { it::class },
            VoiceIntentParser.parse("bật đèn đọc xong tắt máy lạnh").map { it::class },
        )
    }

    @Test
    fun `Words khop dung cach viet khi chu mang dau`() {
        val w = VoiceHomograph.Words("và", "rồi", "and")
        fun m(raw: String) = w.matches(VoiceLexicon.Token(raw, VoiceLexicon.deaccent(raw)))
        listOf("rồi", "RỒI", "Rồi", "roi", "ROI", "và", "va", "and", "AND").forEach { assertTrue(m(it), "«$it» là liên từ") }
        listOf("rơi", "rời", "rối", "rọi", "vá", "vả", "vạ", "đi").forEach { assertFalse(m(it), "«$it» KHÔNG phải liên từ") }
        assertTrue(m(Normalizer.normalize("rồi", Normalizer.Form.NFD)), "NFD «rồi» vẫn là liên từ")
        assertEquals(setOf("va", "roi", "and"), w.norms)
    }

    /** Bộ nghe được khai đủ liên từ ở dạng bỏ dấu (hợp đồng `VoicePhrases`) — 2.98 R2 thêm "xong". */
    @Test
    fun `tap lien tu bo dau khong doi`() {
        assertEquals(setOf("va", "roi", "xong", "and", "then"), VoiceIntentParser.CONNECTORS)   // 2.98 R2 thêm "xong"
    }
}
