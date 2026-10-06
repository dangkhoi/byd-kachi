package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 2.93 VOICE-CLAUSE-VERB-ELLIPSIS (spec `kachi-293-voice.html` §10) — vế chỉ là TÊN một nút bật/tắt mượn động từ họ bật/tắt/
 * mở/đóng của vế đứng trước gần nhất ([VoiceClauseEllipsis]).
 *
 * [ĐO off-car 07/10, bộ phân tích thật] trước bản này: *"tắt điều hòa và đèn đọc"* ⇒ tắt điều hoà + **BẬT** đèn đọc (vế sau nhận
 * động từ ngầm bật). Luật điều phối: mượn khi động từ hợp với kiểu nút; vế trước là nhạc · đọc · app · tăng/giảm ⇒ giữ NGUYÊN hành
 * vi hôm nay (bật ngầm), KHÔNG hỏi lại. Bộ phận chuyển động (kính · cốp …) đi luật riêng — `VoiceBareCoverTest`.
 */
class VoiceClauseEllipsisTest {

    private fun all(s: String) = VoiceIntentParser.parse(s)
    private fun c(id: String, v: Int?) = VoiceIntent.Control(id, v)

    @Test
    fun `hai ca dieu phoi neu ten`() {
        assertEquals(listOf(c("ac_auto", 0), c("readl", 0)), all("tắt điều hòa và đèn đọc"))
        assertEquals(listOf(c("readl", 1), c("seath", 1)), all("bật đèn đọc và sưởi ghế"))
    }

    /** Mượn động từ họ bật/tắt/mở/đóng cho mọi kiểu nút nhận nó: TOGGLE · SELECT (ghế) — cả chuỗi, liên từ "rồi", chữ HOA. */
    @Test
    fun `nut bat tat muon dong tu cua ve truoc`() {
        listOf(
            "tắt đèn đọc và sưởi ghế" to listOf(c("readl", 0), c("seath", 0)),
            "tắt điều hòa và mát ghế" to listOf(c("ac_auto", 0), c("seatc", 0)),
            "tắt điều hòa và sưởi ghế phụ" to listOf(c("ac_auto", 0), c("seath_r", 0)),
            "tắt điều hòa và khóa trẻ em" to listOf(c("ac_auto", 0), c("child_lock", 0)),
            "tắt sấy kính trước và sấy kính sau" to listOf(c("defrost", 0), c("defrost_rear", 0)),
            "tắt đèn đọc và đèn pha" to listOf(c("readl", 0), c("headl", 0)),
            "tắt máy lạnh rồi đèn đọc" to listOf(c("ac_auto", 0), c("readl", 0)),
            "TẮT ĐIỀU HÒA VÀ ĐÈN ĐỌC" to listOf(c("ac_auto", 0), c("readl", 0)),
            "tắt điều hòa và đèn đọc và sưởi ghế" to listOf(c("ac_auto", 0), c("readl", 0), c("seath", 0)),
            "tắt đèn đọc và đèn ban ngày và lọc bụi" to listOf(c("readl", 0), c("drl", 0), c("pm25", 0)),
            "đóng cốp và đèn đọc" to listOf(c("trunk", 0), c("readl", 0)),
            // Động từ hành động GẦN NHẤT là động từ CUỐI của vế trước (vế mang hai lệnh không liên từ).
            "bật đèn pha tắt đèn đọc và sưởi ghế" to listOf(c("headl", 1), c("seath", 0)),
            // Họ bật/mở: giống hệt động từ ngầm hôm nay.
            "bật điều hòa và đèn đọc" to listOf(c("ac_auto", 1), c("readl", 1)),
            "bật đèn pha và đèn đọc" to listOf(c("headl", 1), c("readl", 1)),
        ).forEach { (s, want) -> assertEquals(want, all(s), "«$s»") }
    }

    /** Động từ vế trước KHÔNG hợp (nhạc · đọc · app · tăng/giảm · hạ) hay nút không nhận nó ⇒ giữ NGUYÊN hành vi hôm nay. */
    @Test
    fun `dong tu khong hop thi giu nguyen - khong hoi lai`() {
        fun dropped(s: String) = VoiceIntent.Unknown(VoiceUnknownReason.DROPPED_CLAUSE, s)
        listOf(
            "phát nhạc và đèn đọc" to listOf(VoiceIntent.Media(VoiceMediaOp.PLAY), c("readl", 1)),
            "dừng nhạc và đèn đọc" to listOf(VoiceIntent.Media(VoiceMediaOp.PAUSE), c("readl", 1)),
            "xem pin và đèn đọc" to listOf(VoiceIntent.Read("soc"), c("readl", 1)),
            "tăng nhiệt độ và đèn đọc" to listOf(c("temp", null).copy(relative = 1), c("readl", 1)),
            "hạ kính lái và đèn đọc" to listOf(c("win_lf", 1), c("readl", 1)),
            "tắt điều hòa và phát nhạc và đèn đọc" to listOf(c("ac_auto", 0), VoiceIntent.Media(VoiceMediaOp.PLAY), c("readl", 1)),
            // BUTTON (bấm một phát) · STEP (tên trần không là lệnh) · camera (LAUNCHER) · gói lệnh: như hôm nay.
            "tắt điều hòa và lọc ngay" to listOf(c("ac_auto", 0), c("pm25_clean_now", null)),
            "tắt điều hòa và gió" to listOf(c("ac_auto", 0), dropped("gió")),
            "tắt điều hòa và camera" to listOf(c("ac_auto", 0), dropped("camera")),
            "tắt đèn đọc và rời xe" to listOf(c("readl", 0), dropped("rời xe")),
        ).forEach { (s, want) -> assertEquals(want, all(s), "«$s»") }
        val app = VoiceIntentParser.parse("mở youtube và đèn đọc", apps = listOf("YouTube"))
        assertEquals(c("readl", 1), app.last(), "vế trước mở app ⇒ đèn đọc vẫn bật ngầm: $app")
    }

    /**
     * Senior review wave 2 [P1] — động từ của mạch là động từ MỞ ĐẦU một lệnh mà bộ phân tích HIỂU được, không phải chữ hành động
     * cuối bất kỳ. [ĐO off-car 07/10] trước bản vá: chữ nằm TRONG tên / điểm đến thành động từ của vế sau — *"hạ"* của Hạ Long MỞ
     * kính lái, *"hà"* của Hà Nội đóng cốp, *"dong"* của *"tu dong"* (gõ không dấu) TẮT đèn đọc / ĐÓNG kính, *"nắng"* (= *"nâng"*)
     * của *"rèm che nắng"* làm rơi vế kính.
     */
    @Test
    fun `dong tu cua mach phai mo dau mot lenh hieu duoc`() {
        fun dropped(s: String) = VoiceIntent.Unknown(VoiceUnknownReason.DROPPED_CLAUSE, s)
        listOf(
            "dẫn đường đến hạ long và kính lái" to listOf(VoiceIntent.Nav("hạ long"), dropped("kính lái")),
            "dẫn đường đến hà nội và cốp" to listOf(VoiceIntent.Nav("hà nội"), dropped("cốp")),
            "bat dieu hoa tu dong va den doc" to listOf(c("ac_auto", 1), c("readl", 1)),
            "bat dieu hoa tu dong va kinh lai" to listOf(c("ac_auto", 1), c("win_lf", 1)),
            "bat dieu hoa tu dong va cua so troi" to listOf(c("ac_auto", 1), c("sunroof", 1)),
            "mở rèm che nắng và kính lái" to listOf(c("sunshade", 1), c("win_lf", 1)),
            "đóng rèm che nắng và kính lái" to listOf(c("sunshade", 0), c("win_lf", 0)),
            "mở kính lái và rèm che nắng và cốp" to listOf(c("win_lf", 1), c("sunshade", 1), c("trunk", 1)),
            // Vế mang hai lệnh không liên từ: lệnh CUỐI hiểu được vẫn quyết (khoá ở bài trên, giữ nguyên).
            "phát nhạc tắt đèn đọc và sưởi ghế" to listOf(VoiceIntent.Media(VoiceMediaOp.QUERY, "tắt đèn đọc"), c("seath", 0)),
        ).forEach { (s, want) -> assertEquals(want, all(s), "«$s»") }
    }

    /** Tên nút mở đầu bằng một động từ HÀNH ĐỘNG (*"lấy gió trong"* = nhãn `recirc`) đã tự mang động từ — không mượn. */
    @Test
    fun `ten nut tu mang dong tu thi khong muon`() {
        assertEquals(listOf(c("ac_auto", 0), c("recirc", 1)), all("tắt điều hòa và lấy gió trong"))
        assertEquals(listOf(c("defrost", 0), c("recirc", 1), c("sunroof", 1)), all("tắt sấy kính và lấy gió trong rồi mở nóc xe"))
    }
}
