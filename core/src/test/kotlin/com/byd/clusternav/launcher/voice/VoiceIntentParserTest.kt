package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.Strings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V1 · BÀI CANH BỘ PHÂN TÍCH Ý ĐỊNH ════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R7. Nguồn câu mẫu: **44 mẫu câu tiếng Việt [ĐO] nguyên văn** trong
 * `docs/diagnostics/kiki-car-RE-2026-09-14.md` §7(c) — chúng là chứng cứ *"người Việt thật sự nói thế nào với trợ
 * lý trên xe"*, đã qua kiểm chứng thị trường; ta chuyển thể sang tập đóng của Kachi.
 *
 * ## Vì sao nhiều ca kỳ vọng [VoiceIntent.Unknown] — và đó là ĐÚNG
 * Radio, truyền hình, tin tức, hỏi đáp, toán: Kachi **cố ý không làm** (phương án C, RE §8.2 — Kiki giữ phần từ
 * vựng mở, Kachi giữ phần xe + launcher chạy offline). Khoá chúng lại bằng test để lần sau không ai "tiện tay"
 * thêm một nhánh đoán mò: một câu trả lời sai còn tệ hơn một câu *"tôi không làm được việc này"*.
 */
class VoiceIntentParserTest {

    private val profiles = listOf("Mặc định", "Vợ")
    private val apps = listOf("VTV Go", "YouTube", "Zing MP3", "VietMap Live")

    private fun one(s: String): VoiceIntent = VoiceIntentParser.parseOne(s, profiles, apps)
    private fun all(s: String): List<VoiceIntent> = VoiceIntentParser.parse(s, profiles, apps)

    /** Bảng ca: câu → ý định mong đợi. Thông báo lỗi kèm nguyên văn câu để đọc là biết ca nào đỏ. */
    private fun expect(vararg cases: Pair<String, VoiceIntent>) =
        cases.forEach { (s, want) -> assertEquals(want, one(s), "câu: \"$s\"") }

    private fun unknown(s: String, reason: VoiceUnknownReason) =
        assertEquals(reason, (one(s) as? VoiceIntent.Unknown)?.reason, "câu: \"$s\" phải là Unknown($reason)")

    @AfterEach fun resetLang() { Strings.current = Lang.VI }

    // ══ A · DẪN ĐƯỜNG (Kiki §7c #1–#6) — điểm đến là từ vựng MỞ, giữ nguyên văn ════════════════════════

    @Test fun `dan duong giu nguyen van diem den`() = expect(
        "Dẫn đường đến chợ Bến Thành" to VoiceIntent.Nav("chợ Bến Thành"),
        "Chỉ đường đến số 72 Nguyễn Cơ Thạch" to VoiceIntent.Nav("số 72 Nguyễn Cơ Thạch"),
        "Chỉ đường đến trạm xăng gần nhất" to VoiceIntent.Nav("trạm xăng gần nhất"),
        "Chỉ đường đến Hà Nội" to VoiceIntent.Nav("Hà Nội"),
        "Dẫn đường tới sân bay Nội Bài" to VoiceIntent.Nav("sân bay Nội Bài"),
        "Navigate to Ben Thanh market" to VoiceIntent.Nav("Ben Thanh market"),
    )

    /**
     * *"trạm sạc"* chứa cụm *"sạc"* của xe ở nhiều biến thể — nếu đem điểm đến so với từ vựng xe thì một
     * câu dẫn đường sẽ biến thành lệnh sạc pin. Ca này khoá luật *"sau động từ NAV thì KHÔNG khớp từ vựng xe"*.
     */
    @Test fun `diem den KHONG bi khop nham vao tu vung xe`() = expect(
        "Chỉ đường đến trạm sạc gần nhất" to VoiceIntent.Nav("trạm sạc gần nhất"),
        "Dẫn đường đến chợ Gió" to VoiceIntent.Nav("chợ Gió"),
    )

    // ══ B · NHẠC (Kiki §7c #7–#13) — tên bài/ca sĩ/thể loại = từ vựng MỞ ══════════════════════════════

    @Test fun `mo bai mo nhac ra Media QUERY`() = expect(
        "Mở bài Nồng nàn Hà Nội" to VoiceIntent.Media(VoiceMediaOp.QUERY, "Nồng nàn Hà Nội"),
        "Mở nhạc Trữ tình" to VoiceIntent.Media(VoiceMediaOp.QUERY, "Trữ tình"),
        "Mở nhạc Bolero" to VoiceIntent.Media(VoiceMediaOp.QUERY, "Bolero"),
        "Mở nhạc trẻ remix" to VoiceIntent.Media(VoiceMediaOp.QUERY, "trẻ remix"),
        "Phát bài Diễm xưa" to VoiceIntent.Media(VoiceMediaOp.QUERY, "Diễm xưa"),
    )

    @Test fun `lenh phat khong co ten bai`() = expect(
        "Phát nhạc" to VoiceIntent.Media(VoiceMediaOp.PLAY),
        "Mở nhạc" to VoiceIntent.Media(VoiceMediaOp.PLAY),
        "Tắt nhạc" to VoiceIntent.Media(VoiceMediaOp.PAUSE),
        "Dừng nhạc" to VoiceIntent.Media(VoiceMediaOp.PAUSE),
        "Chuyển bài tiếp theo" to VoiceIntent.Media(VoiceMediaOp.NEXT),
        "Bài trước" to VoiceIntent.Media(VoiceMediaOp.PREV),
        "Next track" to VoiceIntent.Media(VoiceMediaOp.NEXT),
    )

    // ══ C · ÂM LƯỢNG (Kiki §7c #14–#16) ═══════════════════════════════════════════════════════════════

    /** *"tối đa"* là ĐÍCH, không phải một nấc — xem KDoc `VoiceIntentParser.step`. `vol` có `max = 30`. */
    @Test fun `tang am luong toi da la lenh tuyet doi`() = expect(
        "Tăng âm lượng tối đa" to VoiceIntent.Control("vol", 30),
        "Giảm âm lượng" to VoiceIntent.Control("vol", null, -1),
        "Tăng âm lượng" to VoiceIntent.Control("vol", null, 1),
        "Đặt âm lượng 12" to VoiceIntent.Control("vol", 12),
    )

    // ══ D · NHỮNG THỨ KACHI CỐ Ý KHÔNG LÀM (Kiki §7c #18–#41, #44) ════════════════════════════════════

    @Test fun `radio truyen hinh tin tuc hoi dap deu KHONG doan mo`() {
        unknown("Mở radio", VoiceUnknownReason.NO_OBJECT)
        unknown("Mở kênh VOV Giao thông", VoiceUnknownReason.NO_OBJECT)
        unknown("Tin thời sự hôm nay", VoiceUnknownReason.NO_VERB)
        unknown("Quốc ca được sáng tác năm nào", VoiceUnknownReason.NO_VERB)
        unknown("Thời tiết hôm nay thế nào", VoiceUnknownReason.NO_OBJECT)
        unknown("12 x 15 bằng bao nhiêu", VoiceUnknownReason.NO_OBJECT)
        unknown("Kiki ơi", VoiceUnknownReason.NO_VERB)
        unknown("Tốc độ giới hạn ở đây là 50km/h", VoiceUnknownReason.NO_VERB)
        unknown("Tìm trạm xăng gần đây", VoiceUnknownReason.NO_VERB)
        unknown("", VoiceUnknownReason.EMPTY)
    }

    // ══ E · NÚT XE — 5 kiểu ControlKind ═══════════════════════════════════════════════════════════════

    @Test fun `TOGGLE bat tat`() = expect(
        "Bật đèn đọc" to VoiceIntent.Control("readl", 1),
        "Tắt đèn đọc" to VoiceIntent.Control("readl", 0),
        "Bật điều hoà" to VoiceIntent.Control("ac_auto", 1),
        "Tắt lọc bụi" to VoiceIntent.Control("pm25", 0),
        "Bật lấy gió trong" to VoiceIntent.Control("recirc", 1),
        "Turn on the reading light" to VoiceIntent.Control("readl", 1),
    )

    @Test fun `COVER mo dong`() = expect(
        "Mở kính trước trái" to VoiceIntent.Control("win_lf", 1),
        "Đóng kính trước trái" to VoiceIntent.Control("win_lf", 0),
        "Đóng kính sau phải" to VoiceIntent.Control("win_rr", 0),
        "Mở rèm che nắng" to VoiceIntent.Control("sunshade", 1),
    )

    @Test fun `STEP tuyet doi va tuong doi`() = expect(
        "Đặt nhiệt độ 22" to VoiceIntent.Control("temp", 22),
        "Đặt nhiệt độ hai mươi hai" to VoiceIntent.Control("temp", 22),
        "Đặt nhiệt độ hai mươi tư" to VoiceIntent.Control("temp", 24),
        "Set temperature to 24" to VoiceIntent.Control("temp", 24),
        "Tăng gió" to VoiceIntent.Control("fan", null, 1),
        "Giảm gió 2 nấc" to VoiceIntent.Control("fan", null, -2),
        "Đặt gió tối đa" to VoiceIntent.Control("fan", 7),
    )

    /** Giá trị ngoài dải bị **kẹp bằng chính `ControlDef.clamp`** — hai bề mặt không được có hai luật kẹp. */
    @Test fun `STEP ngoai dai bi kep theo ControlDef`() = expect(
        "Đặt nhiệt độ 99" to VoiceIntent.Control("temp", 33),
        "Đặt nhiệt độ 5" to VoiceIntent.Control("temp", 17),
    )

    @Test fun `SELECT chon theo nhan lua chon`() = expect(
        "Chỉnh chế độ lái sang thể thao" to VoiceIntent.Control("drive_mode", 2),
        "Đặt chế độ lái eco" to VoiceIntent.Control("drive_mode", 1),
        "Chỉnh góc camera sang sau" to VoiceIntent.Control("camera_view", 1),
        "Đặt màu đèn viền xanh lá" to VoiceIntent.Control("ambient_color", 2),
    )

    @Test fun `BUTTON bam mot phat`() = expect(
        "Lọc ngay" to VoiceIntent.Control("pm25_clean_now", null),
        "Sạc ngay" to VoiceIntent.Control("start_charging", null),
        "Gập gương" to VoiceIntent.Control("mirror_fold_btn", null),
    )

    // ══ F · BA CẶP NHÃN LỒNG NHAU (backlog L-RE2) — luật "dãy dài nhất thắng" ══════════════════════════

    /**
     * [ĐO] `docs/PROJECT-BACKLOG.md` L-RE2: ba cặp nút dùng CHUNG feature-id nhưng nghĩa khác, và nhãn cái này
     * **chứa** nhãn cái kia. Nhận nhầm ở đây là bấm nhầm một nút ảnh hưởng tầm nhìn ban đêm.
     */
    @Test fun `khong nhan nham giua ba cap nhan long nhau`() = expect(
        "Bật camera 360" to VoiceIntent.Control("cam", 1),
        "Chỉnh góc camera sang trước" to VoiceIntent.Control("camera_view", 0),
        "Bật đèn pha" to VoiceIntent.Control("headl", 1),
        "Chỉnh chế độ đèn pha sang auto" to VoiceIntent.Control("headlight_mode", 1),
        "Đặt độ sáng màn 7" to VoiceIntent.Control("brightness_gear", 7),
        "Đặt độ sáng HUD 3" to VoiceIntent.Control("hud_brightness", 3),
    )

    // ══ G · NHÃN TRÙNG giữa ĐỌC và HÀNH ĐỘNG — loại động từ quyết định ════════════════════════════════

    /**
     * 18 nhãn trùng giữa mục ĐỌC và HÀNH ĐỘNG (`CapabilityCatalog.collidingLabels`). *"Kính trước-trái"* vừa là
     * datum (% mở) vừa là nút (đóng/mở); *"Gạt mưa"* vừa là badge trạng thái vừa là nút.
     */
    @Test fun `cung mot cum dong tu quyet dinh xem hay bam`() = expect(
        "Xem kính trước trái" to VoiceIntent.Read("window_lf"),
        "Mở kính trước trái" to VoiceIntent.Control("win_lf", 1),
        "Xem gạt mưa" to VoiceIntent.Read("wiper_state"),
        "Bật gạt mưa" to VoiceIntent.Control("wiper", 1),
        "Xem cửa sổ trời" to VoiceIntent.Read("sunroof_state"),
        "Mở cửa sổ trời" to VoiceIntent.Control("sunroof", 1),
    )

    // ══ H · ĐỌC THÔNG TIN ═════════════════════════════════════════════════════════════════════════════

    @Test fun `doc thong tin xe`() = expect(
        "Xem pin" to VoiceIntent.Read("soc"),
        "Pin còn bao nhiêu" to VoiceIntent.Read("soc"),
        "Đọc tốc độ" to VoiceIntent.Read("speed", aloud = true),
        "Kiểm tra áp suất lốp trước trái" to VoiceIntent.Read("tyre_p_fl"),
        "Xem nhiệt ngoài xe" to VoiceIntent.Read("ext_temp"),
        "Show battery" to VoiceIntent.Read("soc"),
        "Xem tầm hoạt động EV" to VoiceIntent.Read("ev_range_km"),
    )

    /** *"đọc"* ⇒ chờ NGHE, *"xem"* ⇒ chờ NHÌN. Hôm nay cả hai ra chữ (chưa có TTS — R8) nhưng ý định khác nhau. */
    @Test fun `doc to va xem la hai y dinh khac nhau`() {
        assertEquals(VoiceIntent.Read("soc", aloud = true), one("Đọc pin"))
        assertEquals(VoiceIntent.Read("soc", aloud = false), one("Xem pin"))
    }

    // ══ I · GÓI LỆNH · LAUNCHER · HỒ SƠ · APP ═════════════════════════════════════════════════════════

    /** Tên gói lệnh bắt đầu bằng một động từ ⇒ luật "cả câu là TÊN của việc" (xem `headMatch`). */
    @Test fun `goi lenh thang nut cung ten`() = expect(
        "Mở hết kính" to VoiceIntent.Macro("mac_win_open_all"),
        "Đóng hết kính" to VoiceIntent.Macro("mac_win_close_all"),
        "Rời xe" to VoiceIntent.Macro("mac_leave"),
        "Close all" to VoiceIntent.Macro("mac_win_close_all"),
    )

    @Test fun `mo khoa cua la nut BUTTON chu khong phai tat nut khoa`() = expect(
        "Mở khoá cửa" to VoiceIntent.Control("door", null),
        "Khoá xe" to VoiceIntent.Control("lock", 1),
        "Unlock" to VoiceIntent.Control("door", null),
    )

    @Test fun `launcher ho so va app`() = expect(
        "Mở Cài đặt" to VoiceIntent.Launcher("launcher_settings"),
        "Mở ứng dụng" to VoiceIntent.Launcher("launcher_apps"),
        "Mở ứng dụng VTV Go" to VoiceIntent.OpenApp("VTV Go"),
        "Mở YouTube" to VoiceIntent.OpenApp("YouTube"),
        "Chuyển sang hồ sơ Vợ" to VoiceIntent.Profile("Vợ"),
        "Đổi sang Mặc định" to VoiceIntent.Profile("Mặc định"),
    )

    // ══ J · CÂU GHÉP (Kiki §7c #42) ═══════════════════════════════════════════════════════════════════

    @Test fun `cau ghep tach theo thu tu noi`() {
        assertEquals(
            listOf(VoiceIntent.Nav("Bitexco"), VoiceIntent.Media(VoiceMediaOp.QUERY, "trẻ")),
            all("Chỉ đường đến Bitexco và mở nhạc trẻ"),
        )
        assertEquals(
            listOf(VoiceIntent.Macro("mac_win_close_all"), VoiceIntent.Control("lock", 1)),
            all("Đóng hết kính rồi khoá cửa"),
        )
        assertEquals(
            listOf(VoiceIntent.Control("readl", 1), VoiceIntent.Control("headl", 0)),
            all("Bật đèn đọc và tắt đèn pha"),
        )
    }

    /**
     * [ĐO] mẫu câu Kiki #10 — chữ *"và"* nằm TRONG tên bài hát. Tách vô điều kiện sẽ gửi đi nửa cái tên.
     * Luật *"mọi vế phải hiểu được, không thì trả nguyên câu"* tự xử ca này mà không cần biết bài hát nào có chữ "và".
     */
    @Test fun `va nam trong ten bai hat thi KHONG tach`() = assertEquals(
        listOf(VoiceIntent.Media(VoiceMediaOp.QUERY, "Cỏ dại và hoa dành dành")),
        all("Mở bài Cỏ dại và hoa dành dành"),
    )

    // ══ K · KHÔNG DẤU · HOA THƯỜNG · DẤU CÂU ══════════════════════════════════════════════════════════

    /**
     * Chuỗi vào tầng này có thể tới từ bàn phím xe (thường **không dấu**), từ kịch bản test, và mai kia từ ASR.
     * Không chịu được cả ba dạng thì đường thử bằng chữ vô dụng ngay từ đầu.
     */
    @Test fun `khong dau hoa thuong dau cau deu ra cung mot y dinh`() {
        val want = VoiceIntent.Control("readl", 1)
        listOf("Bật đèn đọc", "bat den doc", "BẬT ĐÈN ĐỌC", "  bật  đèn   đọc !! ", "Kachi, bật đèn đọc")
            .forEach { assertEquals(want, one(it), "câu: \"$it\"") }
    }

    // ══ L · CÂU PHẢN HỒI SONG NGỮ (R5) ════════════════════════════════════════════════════════════════

    // ══ M · CÂU ĐỜI THƯỜNG (soát 2026-09-14) ══════════════════════════════════════════════════════════
    //
    // Mười câu người ta nói hằng ngày, gõ thử trong lượt soát senior. Bốn câu ĐỎ và chúng đỏ theo bốn kiểu
    // khác nhau — mỗi kiểu nay có một dòng vá ở MỘT chỗ (VoiceSynonyms / VoiceGrammar.VERBS / askAt), không
    // chỗ nào là `if (id == "…")`. Giữ nguyên cả mười ở đây để lần sau sửa ngữ pháp còn biết mình phá cái gì.

    @Test fun `muoi cau doi thuong deu hieu duoc`() = expect(
        // Trước: MISMATCH (cụm *"sổ"* khớp nhãn "Số" của datum `gear`). Nay: nút kính GỘP — việc CONFIRM.
        "mở cửa sổ" to VoiceIntent.Control("windows_all", 1),
        "bật máy lạnh" to VoiceIntent.Control("ac_auto", 1),
        "xem pin" to VoiceIntent.Read("soc"),
        "đổi sang hồ sơ Mặc định" to VoiceIntent.Profile("Mặc định"),
        "phát nhạc" to VoiceIntent.Media(VoiceMediaOp.PLAY),
        // Trước: NO_VERB (*"bài"* là từ khoá NHẠC, mà `headMatch` không nhận loại đó).
        "bài tiếp" to VoiceIntent.Media(VoiceMediaOp.NEXT),
        // Trước: NO_OBJECT (từ vựng chỉ có *"chiếu cụm"*).
        "dừng chiếu" to VoiceIntent.Control("cast", 0),
        "mở VietMap Live" to VoiceIntent.OpenApp("VietMap Live"),
    )

    /** Hai câu còn lại trong mười câu: một câu KHÔNG hiểu được và **đúng ra là không nên** hiểu. */
    @Test fun `hai cau con lai noi thang la chua lam duoc`() {
        // *"tắt hết đèn"*: Kachi không có khả năng "mọi đèn" (không nút gộp, không gói lệnh) ⇒ nói thẳng còn hơn
        // tự chọn một cái đèn nào đó. Ngày có gói lệnh "tắt hết đèn", câu này tự hiểu được (từ vựng SINH từ registry).
        unknown("tắt hết đèn", VoiceUnknownReason.NO_OBJECT)
        // *"về bố cục 2 cột"*: bố cục **chưa** nằm trong tập đóng của giọng nói (spec §7 OQ1 họ hàng) — chờ owner.
        unknown("về bố cục 2 cột", VoiceUnknownReason.NO_VERB)
    }

    /**
     * Cụm hỏi đứng GIỮA câu cũng là câu hỏi — xem KDoc `VoiceIntentParser.askAt`.
     *
     * Trước bản vá chỉ nhận cụm hỏi ở CUỐI, nên *"pin còn bao nhiêu"* hiểu được còn *"còn bao nhiêu pin"* thì câm.
     */
    @Test fun `cum hoi dung o giua cau van la cau hoi`() = expect(
        "pin còn bao nhiêu" to VoiceIntent.Read("soc"),
        "còn bao nhiêu pin" to VoiceIntent.Read("soc"),
        "bao nhiêu phần trăm pin" to VoiceIntent.Read("soc"),
    )

    /** …nhưng vẫn KHÔNG được biến câu hỏi ngoài tập đóng thành một câu trả lời bịa. */
    @Test fun `cum hoi o giua cau khong keo theo cau hoi ngoai tap dong`() {
        unknown("12 x 15 bằng bao nhiêu", VoiceUnknownReason.NO_OBJECT)
        unknown("Thời tiết hôm nay thế nào", VoiceUnknownReason.NO_OBJECT)
    }

    /**
     * ⚠ Số nói RÚT GỌN — bệnh nặng nhất lượt soát này: đọc hụt một chữ thì `ControlDef.clamp` kéo về `min`, tức
     * máy **làm sai** mà vẫn báo "✓". *"đặt nhiệt độ hai lăm"* từng ra **17 °C** (lạnh nhất) thay vì 25.
     */
    @Test fun `so noi rut gon doc dung, khong roi ve min`() = expect(
        "đặt nhiệt độ hai lăm" to VoiceIntent.Control("temp", 25),
        "đặt nhiệt độ hăm bốn" to VoiceIntent.Control("temp", 24),
        "đặt nhiệt độ hăm lăm" to VoiceIntent.Control("temp", 25),
        "đặt nhiệt độ hai tư" to VoiceIntent.Control("temp", 24),
        "đặt âm lượng hai mốt" to VoiceIntent.Control("vol", 21),
        // Bản đầy đủ vẫn phải y nguyên.
        "đặt nhiệt độ hai mươi lăm" to VoiceIntent.Control("temp", 25),
        "đặt âm lượng năm" to VoiceIntent.Control("vol", 5),
    )

    /** Kính: cụm dài vẫn thắng cụm ngắn mới thêm — một kính cụ thể KHÔNG được rơi vào nút gộp. */
    @Test fun `cum kinh ngan khong nuot cum kinh dai`() = expect(
        "mở kính" to VoiceIntent.Control("windows_all", 1),
        "đóng kính" to VoiceIntent.Control("windows_all", 0),
        "mở kính trước trái" to VoiceIntent.Control("win_lf", 1),
        "xem kính trước trái" to VoiceIntent.Read("window_lf"),
        "mở hết kính" to VoiceIntent.Macro("mac_win_open_all"),
        "đóng hết kính" to VoiceIntent.Macro("mac_win_close_all"),
    )

    /** …và cụm *"chiếu"* một từ không được nuốt nhãn nào có chứa nó. */
    @Test fun `cum chieu mot tu khong nuot nhan dai hon`() = expect(
        "dừng chiếu" to VoiceIntent.Control("cast", 0),
        "bật chiếu cụm" to VoiceIntent.Control("cast", 1),
        "bật đèn chiếu xa" to VoiceIntent.Control("headl", 1),
    )

    @Test fun `cau phan hoi goi ten nut bang nhan cua bo dang ky`() {
        Strings.current = Lang.VI
        assertEquals("Bật Đèn đọc", VoiceReply.preview(VoiceIntent.Control("readl", 1)))
        assertEquals("Đặt Nhiệt độ = 22", VoiceReply.preview(VoiceIntent.Control("temp", 22)))
        Strings.current = Lang.EN
        assertEquals("Turn on Reading light", VoiceReply.preview(VoiceIntent.Control("readl", 1)))
        assertTrue(VoiceReply.unknown(VoiceIntent.Unknown(VoiceUnknownReason.NO_VERB, "abc")).contains("abc"))
    }
}
