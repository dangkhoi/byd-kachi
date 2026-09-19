package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.Strings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * ═══ BÀI CANH TỪ LOG XE 1.79 (2026-09-19) — ba lỗi parse owner chốt fix hết ═══════════════════════════════════
 *
 * Nguồn: `docs/diagnostics/oncar-voice-log-1.79-findings-2026-09-19.md`. Owner 2026-09-19: *"người ta nói rõ
 * dẫn đường đến, tìm đường đến, tìm bài hát — không cần assume, không rõ hỏi lại thôi"* + *"vietmap → việt máp,
 * việt mép, việt mốp… tạo biến thể, đưa vào dictionary như tên app tiếng Anh khác"*.
 *
 * Ba fix, mỗi cái khoá cả HAI chiều (đúng thì ăn, sai thì KHÔNG được đoán):
 *  • (b) media-search: "tìm/tìm kiếm + TỪ-NHẠC" → query; "tìm <phi-nhạc>" giữ nguyên NO_VERB (hỏi lại);
 *  • (a) VietMap: biến thể phiên âm mở đúng app;
 *  • (c) "điều hòa X độ" → đặt nhiệt độ (nút `temp`), "bật/tắt điều hòa" (không số) vẫn ac_auto.
 */
class VoiceLogCases0919Test {

    private fun one(s: String): VoiceIntent = VoiceIntentParser.parseOne(s)

    @AfterEach fun resetLang() { Strings.current = Lang.VI }

    // ══ (b) TÌM BÀI HÁT — chỉ khi có TỪ-NHẠC ═══════════════════════════════════════════════════════════
    @Test fun `tim tu-nhac la media query`() {
        assertEquals(VoiceIntent.Media(VoiceMediaOp.QUERY, "ngày chưa giông bão"), one("tìm bài hát ngày chưa giông bão"))
        assertEquals(VoiceIntent.Media(VoiceMediaOp.QUERY, "sơn tùng"), one("tìm kiếm nhạc sơn tùng"))
        assertEquals(VoiceIntent.Media(VoiceMediaOp.QUERY, "diễm xưa"), one("tìm ca khúc diễm xưa"))
    }

    /** GUARD: "tìm <phi-nhạc>" KHÔNG có từ-nhạc ⇒ NO_VERB (hỏi lại) — tuyệt đối KHÔNG phát địa danh thành bài hát. */
    @Test fun `tim phi-nhac giu nguyen NO_VERB`() {
        assertEquals(VoiceUnknownReason.NO_VERB, (one("tìm trạm xăng gần đây") as? VoiceIntent.Unknown)?.reason)
        assertEquals(VoiceUnknownReason.NO_VERB, (one("tìm nhà hàng gần đây") as? VoiceIntent.Unknown)?.reason)
        // "tìm bài hát" trống (không tên bài) ⇒ cũng hỏi lại, không phát bừa.
        assertEquals(VoiceUnknownReason.NO_VERB, (one("tìm bài hát") as? VoiceIntent.Unknown)?.reason)
    }

    /** "tìm đường đến X" vẫn là DẪN ĐƯỜNG (động từ NAV dài hơn thắng, không rơi vào nhánh nhạc). */
    @Test fun `tim duong den van la nav`() {
        assertEquals(VoiceIntent.Nav("sân bay Nội Bài"), one("tìm đường đến sân bay Nội Bài"))
    }

    // ══ (a) VIETMAP — biến thể phiên âm mở đúng app ════════════════════════════════════════════════════
    @Test fun `mo viet map bien the phien am`() {
        val want = VoiceIntent.OpenApp("VietMap", appKey = VoiceAppTargets.VIETMAP)
        assertEquals(want, one("mở việt máp"))
        assertEquals(want, one("mở việt mép"))
        assertEquals(want, one("mở việt mốp"))
        assertEquals(want, one("mở việt mụp"))
    }

    // ══ (c) ĐIỀU HÒA X ĐỘ → đặt nhiệt độ ════════════════════════════════════════════════════════════════
    @Test fun `dieu hoa X do la setpoint nhiet`() {
        assertEquals(VoiceIntent.Control("temp", 25), one("mở điều hòa hai mươi lăm độ"))
        assertEquals(VoiceIntent.Control("temp", 22), one("chỉnh máy lạnh hai mươi hai độ"))
    }

    /** "bật/tắt điều hòa" (KHÔNG số/độ) vẫn là ac_auto — không đụng fix (c). */
    @Test fun `bat tat dieu hoa khong so van la ac_auto`() {
        assertEquals(VoiceIntent.Control("ac_auto", 1), one("bật điều hòa"))
        assertEquals(VoiceIntent.Control("ac_auto", 0), one("tắt điều hòa"))
    }
}
