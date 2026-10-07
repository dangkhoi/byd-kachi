package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LayoutPreset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.96 R12 (owner 07/10) — câu phản hồi THUẬN MIỆNG. Khoá đúng các câu owner chỉ ra (*"Tắt kính lái"* → đóng kính;
 * *"Sưởi ghế phụ: Tắt"* → *"đã tắt sưởi ghế phụ"*) + các câu 2.95 đọc ra sai ngữ pháp (*"Đã gió: AUTO"*, *"Đã bố cục 2
 * cột"*, *"Đã camera sau"*). Bảng trước → sau: `docs/diagnostics/voice-phrasing-296.md`.
 */
class VoicePhrasing296Test {

    private val vi = Lang.VI
    private fun spoken(vararg lines: String) = VoiceFeedbackPhrase.merge(lines.toList(), vi)

    @Test fun `kinh = MO DONG, khong BAT TAT`() {
        assertEquals("Đóng kính lái", VoiceReply.preview(VoiceIntent.Control("win_lf", 0), vi))
        assertEquals("Mở kính phụ", VoiceReply.preview(VoiceIntent.Control("win_rf", 1), vi))
        assertEquals("Đóng cửa sổ trời", VoiceReply.preview(VoiceIntent.Control("sunroof", 0), vi))
        // Xe đã đọc lại khớp ⇒ ĐÃ đóng.
        assertEquals("✓ Đã đóng kính lái", VoiceReply.doneConfirmed(VoiceIntent.Control("win_lf", 0), vi))
        assertEquals("Đã đóng kính lái", spoken(VoiceReply.doneConfirmed(VoiceIntent.Control("win_lf", 0), vi)))
    }

    @Test fun `bo phan mo-to chua xac nhan = DANG, khong hua DA`() {
        val line = VoiceReply.done(VoiceIntent.Control("win_lf", 0), vi)
        assertTrue(line.startsWith("✓ Đang đóng kính lái"), line)
        assertTrue(spoken(line)!!.startsWith("Đang đóng kính lái"), spoken(line))
        assertFalse(spoken(line)!!.contains("Đã đang"), spoken(line))
    }

    @Test fun `ghe SELECT = cau bat tat, khong NHAN hai cham MUC`() {
        assertEquals("✓ Đã tắt sưởi ghế phụ", VoiceReply.doneConfirmed(VoiceIntent.Control("seath_r", 0), vi))
        assertEquals("✓ Đã bật sưởi ghế phụ mức 1", VoiceReply.doneConfirmed(VoiceIntent.Control("seath_r", 1), vi))
        assertEquals("✓ Đã bật mát ghế lái mức 2", VoiceReply.doneConfirmed(VoiceIntent.Control("seatc", 2), vi))
        assertEquals("Đã tắt sưởi ghế phụ", spoken("✓ Đã tắt sưởi ghế phụ"))
    }

    @Test fun `bac STEP khong con dau bang, thang ngan doc MUC`() {
        assertEquals("✓ Đã đặt gió mức 3", VoiceReply.doneConfirmed(VoiceIntent.Control("fan", 3), vi))
        assertEquals("✓ Đã đặt nhiệt độ 24", VoiceReply.doneConfirmed(VoiceIntent.Control("temp", 24), vi))
        assertEquals("✓ Đã tăng gió 2 nấc", VoiceReply.doneConfirmed(VoiceIntent.Control("fan", relative = 2), vi))
    }

    @Test fun `bat tat thuong - ha chu dau nhan`() {
        assertEquals("✓ Đã bật đèn đọc", VoiceReply.doneConfirmed(VoiceIntent.Control("readl", 1), vi))
        assertEquals("✓ Đã tắt sấy kính trước", VoiceReply.doneConfirmed(VoiceIntent.Control("defrost", 0), vi))
    }

    @Test fun `nut va goi co nhan da la cau lenh - khong chong BAM CHAY GOI`() {
        assertEquals("✓ Đã đóng tất cả kính", VoiceReply.doneConfirmed(VoiceIntent.Control("windows_close_all", 1), vi))
        assertTrue(VoiceReply.done(VoiceIntent.Macro("mac_win_open_all"), vi).startsWith("✓ Đã mở hết kính"))
    }

    @Test fun `app, bo cuc, dan duong, gio AUTO, camera - het cau DA + danh tu`() {
        assertEquals("✓ Đã mở YouTube", VoiceReply.done(VoiceIntent.OpenApp("YouTube"), vi))
        assertEquals("✓ Đã mở YouTube vào ô 2", VoiceReply.done(VoiceIntent.OpenApp("YouTube", slot = 2), vi))
        assertTrue(VoiceReply.done(VoiceIntent.Layout(LayoutPreset.TWO_COL), vi).startsWith("✓ Đã chuyển sang bố cục "))
        assertEquals("✓ Đã bắt đầu dẫn đường tới Bitexco", VoiceReply.done(VoiceIntent.Nav("Bitexco"), vi))
        assertEquals("Đã để gió ở AUTO", spoken(VoiceReply.autoLevel("fan", vi)))
        assertEquals("Đã phát nhạc", spoken(VoiceReply.done(VoiceIntent.Media(VoiceMediaOp.PLAY), vi)))
        assertEquals("Đã chuyển bài tiếp theo", spoken(VoiceReply.done(VoiceIntent.Media(VoiceMediaOp.NEXT), vi)))
    }

    @Test fun `nhieu viec - mot loi dan cho mot day, DANG tach rieng`() {
        val a = VoiceReply.doneConfirmed(VoiceIntent.Control("readl", 1), vi)
        val b = VoiceReply.doneConfirmed(VoiceIntent.Control("seath_r", 0), vi)
        assertEquals("Đã bật đèn đọc, tắt sưởi ghế phụ", spoken(a, b))
        assertEquals("Đã bật đèn đọc, đang đóng kính lái", spoken(a, "✓ Đang đóng kính lái"))
        // Dòng KHÔNG mang lời dẫn (ca một-phần, câu xem-trước) vẫn nhận "Đã" như 2.95.
        assertEquals("Đã bật đèn đọc, dẫn đường tới X", spoken(a, "✓ Dẫn đường tới X"))
    }

    @Test fun `ca mot-phan KHONG noi DA cho viec chua xay ra`() {
        val i = VoiceIntent.Media(VoiceMediaOp.PLAY)
        val t = VoiceAppTargets.ALL.first()
        assertTrue(VoiceReply.musicAppOpened(i, t, vi).startsWith("✓ Phát nhạc"), "giữ câu xem-trước — không 'Đã phát nhạc' khi chưa có phiên")
        assertTrue(VoiceReply.navOpenedNoHandover(VoiceIntent.Nav("X"), t, vi).startsWith("✓ Dẫn đường tới X"))
    }

    @Test fun `hong - cau Chua theo dong tu tu nhien`() {
        assertEquals(
            "Chưa đóng kính lái, xe không nhận lệnh",
            spoken(VoiceReply.failed(VoiceIntent.Control("win_lf", 0), lang = vi)),
        )
    }

    @Test fun `tam biet tu nhien`() {
        assertEquals("Tạm biệt, hẹn gặp lại", spoken(VoiceReply.bye(vi)))
        assertEquals("Bye, see you", VoiceReply.bye(Lang.EN))
    }

    /** Tiếng Anh giữ khuôn câu xem-trước + lời dẫn "Done: " (giọng EN) — chỉ đổi động từ kính + chữ thường nhãn. */
    @Test fun `tieng Anh giu khuon`() {
        val en = Lang.EN
        assertEquals("✓ Close driver window", VoiceReply.doneConfirmed(VoiceIntent.Control("win_lf", 0), en))
        assertEquals("Done: close driver window", VoiceFeedbackPhrase.merge(listOf("✓ Close driver window"), en))
        assertEquals("✓ Turn off passenger seat heating", VoiceReply.doneConfirmed(VoiceIntent.Control("seath_r", 0), en))
    }
}
