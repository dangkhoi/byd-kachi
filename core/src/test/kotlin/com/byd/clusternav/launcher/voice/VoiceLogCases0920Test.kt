package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.Strings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ BÀI CANH TỪ PHIÊN XE 1.84 (2026-09-20) — hai lỗi voice owner báo ═════════════════════════════════════════
 *
 * Nguồn: `docs/diagnostics/oncar-1.84-session-2026-09-20.md` §5. Hai việc của lượt 1.85 nằm ở `:core`:
 *  • **chọn app dẫn đường** — *"… bằng VietMap"* bị ASR in ra *"bằng **vietna**"* ⇒ mệnh đề chọn app không được
 *    cắt ⇒ hai chữ rác đi theo **điểm đến** và câu rơi sang app mặc định với một địa chỉ không tra được;
 *  • **câu trả lời nhạc** — vẫn nhắc *"bấm Play"* trong khi đường watch của 1.75 đã **tự phát**.
 *
 * Mỗi fix khoá cả HAI chiều: cái đáng cắt thì cắt, còn **tên địa điểm có chứa chính chữ đánh dấu** thì tuyệt đối
 * không được cắt (đó là cái giá mà một phép cắt vô điều kiện sẽ phải trả — xem `1.85-stage4.md`).
 */
class VoiceLogCases0920Test {

    private fun one(s: String): VoiceIntent = VoiceIntentParser.parseOne(s)

    @AfterEach fun resetLang() { Strings.current = Lang.VI }

    // ══ (1) CHỌN APP DẪN ĐƯỜNG — cụm bị ASR bóp méo vẫn phải cắt ra khỏi ĐIỂM ĐẾN ═══════════════════════

    /** Ca ĐO ĐƯỢC trên xe: *"vietna"* (thiếu `m`, thừa `a`, mất `p`) phải ra VietMap, và điểm đến phải SẠCH. */
    @Test fun `bang vietna van chon VietMap va diem den sach`() {
        assertEquals(
            VoiceIntent.Nav("chợ bến thành", VoiceAppTargets.VIETMAP),
            one("dẫn đường tới chợ bến thành bằng vietna"),
        )
        // Mô hình tách hai từ hay một từ đều phải ra cùng kết quả (phép so chạy trên chuỗi đã ghép liền).
        assertEquals(
            VoiceIntent.Nav("chợ bến thành", VoiceAppTargets.VIETMAP),
            one("dẫn đường tới chợ bến thành bằng việt na"),
        )
    }

    /** Hai tầng cũ KHÔNG được đổi hành vi: khớp chính xác và cụm rụng-âm-cuối vẫn như 1.84. */
    @Test fun `khop chinh xac va rung am cuoi van nhu cu`() {
        val want = VoiceIntent.Nav("chợ bến thành", VoiceAppTargets.VIETMAP)
        assertEquals(want, one("dẫn đường tới chợ bến thành bằng vietmap"))
        assertEquals(want, one("dẫn đường tới chợ bến thành bằng vietma"))
        assertEquals(want, one("dẫn đường tới chợ bến thành bằng việt máp"))
        assertEquals(
            VoiceIntent.Nav("chợ bến thành", VoiceAppTargets.GMAPS),
            one("dẫn đường tới chợ bến thành bằng google map"),
        )
    }

    /**
     * ═══⚠⚠ GUARD QUAN TRỌNG NHẤT của lượt này — TÊN ĐỊA ĐIỂM CHỨA CHÍNH CHỮ ĐÁNH DẤU ═══════════════════
     *
     * [VoiceLexicon.BY_APP_MARKERS] gồm `bang · tren · voi · qua · dung` — **tất cả đều là từ tiếng Việt thật** và
     * đều xuất hiện GIỮA tên địa điểm (*"cầu Bằng Lăng"* · *"cảng Dung Quất"* · *"trên Nguyễn Huệ"* · *"qua Thủ
     * Đức"*). Một phép cắt **vô điều kiện** (cứ thấy chữ đánh dấu là cắt) sẽ nuốt đúng những câu này và dẫn người
     * lái tới *"cầu"* / *"cảng"*, hoặc tới một điểm đến rỗng — một lệnh dẫn đường SAI mà không có dấu hiệu nào.
     *
     * Vì vậy tầng thứ ba ([VoiceAppTargets.bySpokenFuzzy]) **neo tiền tố 4 ký tự + hai sàn độ dài** chứ không cắt
     * bừa. Bài này là phép chứng minh cái neo ấy còn nguyên.
     */
    @Test fun `ten dia diem chua chu danh dau KHONG bi cat`() {
        assertEquals(VoiceIntent.Nav("cầu bằng lăng"), one("dẫn đường tới cầu bằng lăng"))
        assertEquals(VoiceIntent.Nav("cảng dung quất"), one("dẫn đường tới cảng dung quất"))
        assertEquals(VoiceIntent.Nav("nhà hàng trên nguyễn huệ"), one("dẫn đường tới nhà hàng trên nguyễn huệ"))
        assertEquals(VoiceIntent.Nav("bến xe miền đông"), one("dẫn đường tới bến xe miền đông"))
    }

    // ══ Luật thuần của tầng khớp mờ — đo trực tiếp, không qua bộ phân tích ═════════════════════════════

    @Test fun `bySpokenFuzzy bat cum bi bop meo va cum bi cat cut`() {
        val vm = VoiceAppTargets.byKey(VoiceAppTargets.VIETMAP)
        assertEquals(vm, VoiceAppTargets.bySpokenFuzzy(listOf("vietna")), "vietna ⇒ VietMap (lệch 2 ký tự)")
        assertEquals(vm, VoiceAppTargets.bySpokenFuzzy(listOf("viet", "na")), "tách từ không đổi kết quả")
        assertEquals(vm, VoiceAppTargets.bySpokenFuzzy(listOf("vietm")), "cắt cụt 2 ký tự ⇒ vẫn ra VietMap")
        // *"youtub"* phải ra YouTube, KHÔNG ra YouTube Music — dù `youtubemusic` cũng bắt đầu bằng `youtub` và
        // YT Music đứng trước trong bảng. Đây là phép canh cho việc đã bỏ luật *"tiền tố thật sự"* (xem KDoc).
        assertEquals(
            VoiceAppTargets.byKey(VoiceAppTargets.YOUTUBE),
            VoiceAppTargets.bySpokenFuzzy(listOf("youtub"), VoiceAppKind.MUSIC),
        )
    }

    /** Hai chiều: mọi cụm dưới đây là **tên địa điểm / danh từ thường** đứng sau chữ đánh dấu ⇒ phải trả `null`. */
    @Test fun `bySpokenFuzzy KHONG bat ten dia diem hay danh tu thuong`() {
        listOf(
            listOf("lang"), listOf("quat"), listOf("thu", "duc"), listOf("nguyen", "hue"),
            listOf("xe", "may"), listOf("cau", "sai", "gon"), listOf("mien", "dong"), listOf("ben", "thanh"),
            listOf("duong", "lang"), listOf("tan", "binh"),
        ).forEach { assertNull(VoiceAppTargets.bySpokenFuzzy(it), "«${it.joinToString(" ")}» không phải tên app") }
    }

    /** Cụm quá ngắn (`yt` · `waze` · `quay` · `zing`) chỉ được khớp CHÍNH XÁC — khớp mờ phải im. */
    @Test fun `cum ngan chi khop chinh xac`() {
        listOf(listOf("yt"), listOf("waze"), listOf("quay"), listOf("zing")).forEach {
            assertNull(VoiceAppTargets.bySpokenFuzzy(it), "«${it.joinToString(" ")}» dưới sàn, không được khớp mờ")
            assertTrue(VoiceAppTargets.bySpoken(it) != null, "«${it.joinToString(" ")}» vẫn phải khớp chính xác")
        }
    }

    /**
     * Cái **NEO TIỀN TỐ**, đo bằng một cặp dựng riêng (các tên địa điểm ở bài trên tình cờ đều lệch xa, nên chúng
     * không chứng minh được cái neo — bài này thì có).
     *
     * `dau tu` (*"Đầu Tư"*, có trong hàng loạt tên toà nhà/ngân hàng) ghép liền thành `dautu`, **lệch đúng 2 ký
     * tự** với `dutup` (cách đọc phiên âm của YouTube) ⇒ nếu bỏ neo thì nó khớp YouTube. Bốn ký tự đầu khác nhau
     * (`daut` vs `dutu`), nên neo phải chặn.
     */
    @Test fun `neo tien to chan cum lech tien to du gan ve ky tu`() {
        assertNull(VoiceAppTargets.bySpokenFuzzy(listOf("dau", "tu")), "khác tiền tố ⇒ không được khớp mờ")
        assertNull(VoiceAppTargets.bySpokenFuzzy(listOf("dautu")))
    }

    /** Khớp mờ phải tôn trọng [VoiceAppKind]: một app NHẠC không được nhận cho câu dẫn đường. */
    @Test fun `khop mo van ton trong loai app`() {
        assertNull(VoiceAppTargets.bySpokenFuzzy(listOf("youtub"), VoiceAppKind.NAV))
        assertNull(VoiceAppTargets.bySpokenFuzzy(listOf("vietna"), VoiceAppKind.MUSIC))
    }

    // ══ (2) CÂU TRẢ LỜI NHẠC — đường watch tự phát thì KHÔNG nhắc "bấm Play" ═══════════════════════════

    private val ytm = requireNotNull(VoiceAppTargets.byKey(VoiceAppTargets.YT_MUSIC))
    private val query = VoiceIntent.Media(VoiceMediaOp.QUERY, "diễm xưa", VoiceAppTargets.YT_MUSIC)

    @Test fun `duong watch noi dang phat, khong nhac bam Play`() {
        val said = VoiceReply.handedOver(query, ytm, autoplay = true)
        assertTrue(said.contains("đang phát"), "đường watch tự phát ⇒ phải nói đang phát; nhận: «$said»")
        assertFalse(said.contains("bấm Play"), "nhạc đã phát rồi thì không được nhắc bấm Play; nhận: «$said»")
        Strings.current = Lang.EN
        val en = VoiceReply.handedOver(query, ytm, autoplay = true)
        assertTrue(en.contains("playing now"), "bản EN cũng phải nói đang phát; nhận: «$en»")
        assertFalse(en.contains("press Play"), "bản EN không được nhắc press Play; nhận: «$en»")
    }

    /**
     * Chiều còn lại — đường `deliver` (`MEDIA_PLAY_FROM_SEARCH` mang TÊN bài) **vẫn** dừng ở nút Play
     * ([ĐO] máy ảo 2026-09-14), nên câu cũ phải còn nguyên. Bỏ hẳn câu ấy là nói sai cho Spotify/Zing, nơi
     * `watch == null` ⇒ `deliver` là đường DUY NHẤT.
     */
    @Test fun `duong deliver van nhac bam Play`() {
        val said = VoiceReply.handedOver(query, ytm)
        assertTrue(said.contains("bấm Play"), "đường tìm-kiếm vẫn dừng ở nút Play; nhận: «$said»")
        assertFalse(said.contains("đang phát"), "đường tìm-kiếm chưa phát, không được hứa đang phát")
    }

    /** Dẫn đường không có đuôi nào (kể cả khi `autoplay` mặc định) — một câu ✓ trơn, không đổi. */
    @Test fun `nav khong bi them duoi nhac`() {
        val gm = requireNotNull(VoiceAppTargets.byKey(VoiceAppTargets.GMAPS))
        val said = VoiceReply.handedOver(VoiceIntent.Nav("chợ bến thành", VoiceAppTargets.GMAPS), gm)
        assertFalse(said.contains("bấm Play"))
        assertFalse(said.contains("đang phát"))
    }
}
