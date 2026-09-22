package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LayoutPreset
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

    /** *"tối đa"* là ĐÍCH, không phải một nấc. ⚠ 1.90: đo trên `fan` (`max = 7`, `min = 0`) thay `vol` (đã xoá). */
    @Test fun `tang gio toi da la lenh tuyet doi`() = expect(
        "Tăng gió tối đa" to VoiceIntent.Control("fan", 7),
        "Giảm gió" to VoiceIntent.Control("fan", null, -1),
        "Tăng gió" to VoiceIntent.Control("fan", null, 1),
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

    // ⚠ (V) FEATURE-FILTER 2026-09-17: hai ca `chế độ lái` (nút `drive_mode`) đã gỡ cùng nút — owner chấm NO.
    // ⚠ UX-OVERHAUL · WP8 2026-09-20: ca `màu đèn viền` (`ambient_color`) gỡ cùng nút. Hai ca còn lại vẫn khoá
    // đúng luật "chọn theo NHÃN lựa chọn, không theo số thứ tự" — và cả hai đều là nhãn **nhiều từ**, tức vẫn phủ
    // ca khó nhất của luật ấy.
    // ⚠⚠ 1.90: cả HAI ca (`headlight_mode` · `camera_view`) gỡ cùng nút ⇒ mốc nay là `seatc` (*"Mức 1/2"*, nhãn
    // lựa chọn nhiều từ) — vẫn phủ ca khó nhất: parser đọc theo chỉ số thì *"mức 2"* ra 1.
    @Test fun `SELECT chon theo nhan lua chon`() = expect(
        "ghế mát mức 2" to VoiceIntent.Control("seatc", 2),
        "ghế mát mức 1" to VoiceIntent.Control("seatc", 1),
    )

    // ⚠ (V) FEATURE-FILTER 2026-09-17: hai ca `Sạc ngay` (`start_charging`) và `Gập gương` (`mirror_fold_btn`)
    // đã gỡ cùng hai nút — owner chấm NO.
    @Test fun `BUTTON bam mot phat`() = expect(
        "Lọc ngay" to VoiceIntent.Control("pm25_clean_now", null),
    )

    // ══ F · BA CẶP NHÃN LỒNG NHAU (backlog L-RE2) — luật "dãy dài nhất thắng" ══════════════════════════

    /**
     * [ĐO] `docs/PROJECT-BACKLOG.md` L-RE2: ba cặp nút dùng CHUNG feature-id nhưng nghĩa khác, và nhãn cái này
     * **chứa** nhãn cái kia. Nhận nhầm ở đây là bấm nhầm một nút ảnh hưởng tầm nhìn ban đêm.
     */
    @Test fun `khong nhan nham giua ba cap nhan long nhau`() = expect(
        "Bật camera 360" to VoiceIntent.Control("cam", 1),
        "Bật đèn pha" to VoiceIntent.Control("headl", 1),
        // ⚠⚠ 1.90 — CẢ BA CẶP đã tan (lời giải cuối cho L-RE2): `camera_view` · `headlight_mode` ·
        // `brightness_gear` đều xoá (`hud_brightness` purge ở WP8). Hai câu còn lại vẫn phải trỏ ĐÚNG nút.
        // ⚠⚠ UX-OVERHAUL · WP8 2026-09-20 — cặp thứ BA (*"độ sáng màn"* vs *"độ sáng HUD"*) **hết tồn tại**:
        // `hud_brightness` purge theo triage owner (#63), và đó cũng là lời giải cho chính bug L-RE2 mà bài này
        // sinh ra để canh — hai nút ấy dùng CHUNG feature-id `1276174360`, nên chỉ một trong hai từng nói thật.
        // `brightness_gear` (độ sáng màn chính) là cái ĐÚNG và nó ở lại; ca *"độ sáng HUD"* nay phải KHÔNG hiểu
        // được, và bài `khong nhan nham` vẫn còn hai cặp lồng nhau để canh.
    )

    /** Nhãn của một nút đã purge thì phải trở về KHÔNG HIỂU — không được rơi sang nút gần giống nào khác. */
    @Test fun `nhan cua nut da purge o WP8 khong duoc roi sang nut khac`() {
        listOf("Đặt độ sáng HUD 3", "Bật HUD kính lái", "Bật gạt mưa", "Đặt màu đèn viền xanh lá", "Đặt mức tái tạo cao")
            .forEach { line ->
                val got = one(line)
                assertTrue(
                    got !is VoiceIntent.Control && got !is VoiceIntent.Read,
                    "\"$line\" nói về một nút đã purge ở WP8 ⇒ không được thành lệnh xe, ra: $got",
                )
            }
    }

    /**
     * ═══ WP8 · ĐIỀU KIỆN KẾT NẠP của [VoiceFeatureGone.HARD_BLOCK] — canh bằng MÁY, không bằng lời ═══════════
     *
     * Bảng chặn cứng được hỏi **trước mọi phép khớp**, nên một từ lọt vào đó sẽ giết **mọi** câu chứa nó — kể cả
     * câu của một tính năng đang chạy tốt, và giết **im lặng** (người lái chỉ thấy *"đã bỏ"*). KDoc của bảng đặt ra
     * đúng một điều kiện cho việc thêm từ: *không nhãn/từ-đồng-nghĩa nào còn chứa nó*. Điều kiện ấy tới nay chỉ là
     * một câu văn kèm *"[ĐO grep sau purge]"* — tức nó đúng ở thời điểm viết và không có gì giữ cho nó còn đúng.
     *
     * Bài này biến nó thành phép kiểm: mỗi từ chặn cứng phải **không** xuất hiện trong từ vựng SINH từ bộ đăng ký.
     * Hệ quả thực dụng: hôm nào ai đó thêm lại một nút có chữ `hud` trong nhãn, bài này đỏ **trước** khi nút đó ra
     * xe và chết không hiểu vì sao.
     *
     * ⚠ Chỉ phủ được từ vựng TĨNH (nút · datum · nhóm · gói lệnh). Nhãn app do máy cài sinh ra là động ⇒ một app
     * tên *"HUD …"* vẫn lọt; đó là giới hạn đã biết, không phải chỗ quên.
     */
    @Test fun `moi tu chan cung KHONG duoc nam trong tu vung dang song`() {
        val vocab = VoiceGrammar.terms(profiles, apps)
        VoiceFeatureGone.HARD_BLOCK.forEach { blocked ->
            val clash = vocab.filter { blocked in it.words }
            assertTrue(
                clash.isEmpty(),
                "từ chặn cứng \"$blocked\" còn nằm trong từ vựng đang sống ⇒ nó sẽ giết chính tính năng đó: " +
                    clash.joinToString { "${it.kind}/${it.id}=${it.words}" },
            )
        }
    }

    /**
     * Canary đi kèm bài trên: cụm *"kính lái"* là từ đồng nghĩa của `window` từ 1.66 và nó **vẫn phải sống**. Đây
     * là nửa thứ hai của bug WP8 — chặn *"HUD kính lái"* mà chặn luôn *"kính lái"* thì đổi một lệnh sai thành một
     * tính năng mất.
     */
    @Test fun `chan cung HUD khong giet lenh kinh lai`() = expect(
        "Mở kính lái" to VoiceIntent.Control("win_lf", 1),
        "Đóng kính lái" to VoiceIntent.Control("win_lf", 0),
    )

    // ══ G · NHÃN TRÙNG giữa ĐỌC và HÀNH ĐỘNG — loại động từ quyết định ════════════════════════════════

    /**
     * 18 nhãn trùng giữa mục ĐỌC và HÀNH ĐỘNG (`CapabilityCatalog.collidingLabels`). *"Kính trước-trái"* vừa là
     * datum (% mở) vừa là nút (đóng/mở); *"Gạt mưa"* vừa là badge trạng thái vừa là nút.
     */
    @Test fun `cung mot cum dong tu quyet dinh xem hay bam`() = expect(
        "Xem kính trước trái" to VoiceIntent.Read("window_lf"),
        "Mở kính trước trái" to VoiceIntent.Control("win_lf", 1),
        // ⚠ WP8: cặp *"Gạt mưa"* (datum `wiper_state` + nút `wiper`) đã purge cả hai ⇒ bỏ khỏi bài. Hai cặp còn
        // lại vẫn phủ đúng luật *"loại động từ quyết định xem hay bấm"* trên nhãn TRÙNG.
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

    @Test fun `cac cau doi thuong deu hieu duoc`() = expect(
        // Trước: MISMATCH (cụm *"sổ"* khớp nhãn "Số" của datum `gear`). Nay: **kính LÁI** — lượt D 2026-09-19 dời
        // cụm mơ hồ này khỏi nút GỘP (owner: *"mở kính"* hạ cả 4 là sai), xem KDoc `VoiceSynonyms.CONTROL`.
        "mở cửa sổ" to VoiceIntent.Control("win_lf", 1),
        "bật máy lạnh" to VoiceIntent.Control("ac_auto", 1),
        "xem pin" to VoiceIntent.Read("soc"),
        "đổi sang hồ sơ Mặc định" to VoiceIntent.Profile("Mặc định"),
        "phát nhạc" to VoiceIntent.Media(VoiceMediaOp.PLAY),
        // Trước: NO_VERB (*"bài"* là từ khoá NHẠC, mà `headMatch` không nhận loại đó).
        "bài tiếp" to VoiceIntent.Media(VoiceMediaOp.NEXT),
        // ⚠ 1.90: ca *"dừng chiếu"* (→ `cast`) gỡ cùng nút. Chiếu cụm nay bật/tắt bằng nút nổi + Cài đặt.
        "mở VietMap Live" to VoiceIntent.OpenApp("VietMap Live"),
    )

    /** Câu còn lại: KHÔNG hiểu được và **đúng ra là không nên** hiểu. */
    @Test fun `cau con lai noi thang la chua lam duoc`() {
        // *"tắt hết đèn"*: Kachi không có khả năng "mọi đèn" (không nút gộp, không gói lệnh) ⇒ nói thẳng còn hơn
        // tự chọn một cái đèn nào đó. Ngày có gói lệnh "tắt hết đèn", câu này tự hiểu được (từ vựng SINH từ registry).
        unknown("tắt hết đèn", VoiceUnknownReason.NO_OBJECT)
    }

    /**
     * ⚠⚠ **ĐỔI KỲ VỌNG CÓ CHỦ Ý (L7, owner duyệt 2026-09-16)** — *"về bố cục 2 cột"*.
     *
     * Từ 1.64 câu này được giữ ở [VoiceUnknownReason.NO_VERB] vì bố cục **chưa** nằm trong tập đóng của giọng
     * nói. Nay nó nằm rồi ([VoiceLayouts]), nên kỳ vọng đổi sang [VoiceIntent.Layout] — đây là *"tính năng mới
     * phủ lên một ca đang để ngỏ"*, không phải *"sửa test cho hết đỏ"*.
     *
     * Thứ bài canh này **luôn** canh thì KHÔNG đổi: `về` vẫn không được thành động từ dẫn đường vô điều kiện
     * ([VoiceGrammar.VERBS] không có nó; xem chú thích ⚠⚠ ở đó). Ba câu dưới khoá đúng ranh giới ấy — chỉ đuôi
     * *"bố cục …"* mới thành [VoiceIntent.Layout], mọi đuôi khác vẫn `NO_VERB`, tuyệt đối không thành `Nav`.
     */
    @Test fun `ve bo cuc la LAYOUT, con ve mot chuoi la thi van NO_VERB`() {
        assertEquals(VoiceIntent.Layout(LayoutPreset.TWO_COL), one("về bố cục 2 cột"))
        unknown("về Bitexco", VoiceUnknownReason.NO_VERB)
        unknown("về chỗ nào đó", VoiceUnknownReason.NO_VERB)
    }

    /**
     * L7 — bảng cách nói bố cục, gồm cả số bằng CHỮ (mô hình nghe trả chữ, không trả chữ số).
     *
     * *"bố cục hai ô"* ra [LayoutPreset.TWO_COL], không phải `TWO_ROW` — quyết định ghi ở KDoc [VoiceLayouts].
     */
    @Test fun `bo cuc bang giong noi`() = expect(
        "bố cục 1 ô" to VoiceIntent.Layout(LayoutPreset.ONE),
        "bố cục một ô" to VoiceIntent.Layout(LayoutPreset.ONE),
        "bố cục 2 cột" to VoiceIntent.Layout(LayoutPreset.TWO_COL),
        "bố cục hai cột" to VoiceIntent.Layout(LayoutPreset.TWO_COL),
        "bố cục hai ô" to VoiceIntent.Layout(LayoutPreset.TWO_COL),
        "bố cục 2 hàng" to VoiceIntent.Layout(LayoutPreset.TWO_ROW),
        "bố cục hai hàng" to VoiceIntent.Layout(LayoutPreset.TWO_ROW),
        "bố cục 3 ô" to VoiceIntent.Layout(LayoutPreset.THREE),
        "bố cục bốn ô" to VoiceIntent.Layout(LayoutPreset.QUAD),
        "đổi sang bố cục 2 cột" to VoiceIntent.Layout(LayoutPreset.TWO_COL),
        "chuyển bố cục 4 ô" to VoiceIntent.Layout(LayoutPreset.QUAD),
        "đổi bố cục 4 ô" to VoiceIntent.Layout(LayoutPreset.QUAD),
        "bố cục 4" to VoiceIntent.Layout(LayoutPreset.QUAD),
    )

    /** Cụm đánh dấu phải có, phần đuôi phải khớp TRỌN — không thì im lặng đi tiếp, không đoán. */
    @Test fun `cau khong phai bo cuc thi VoiceLayouts khong dung vao`() {
        unknown("hai cột", VoiceUnknownReason.NO_VERB)
        unknown("bố cục mười hai ô", VoiceUnknownReason.NO_VERB)
        unknown("bố cục 2 cột màu xanh", VoiceUnknownReason.NO_VERB)
        // Hai chữ *"bố cục"* nằm giữa một câu KHÁC (ở đây là tên bài hát) ⇒ [VoiceLayouts.LEAD_WORDS] chặn:
        // *"mở bài …"* vẫn là một câu nhạc, không bị cướp thành lệnh đổi bố cục.
        assertEquals(
            VoiceIntent.Media(VoiceMediaOp.QUERY, "bố cục hai cột"),
            one("mở bài bố cục hai cột"),
        )
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
        // ⚠ 1.90: hai ca *"đặt âm lượng…"* gỡ cùng `vol`. Bản đầy đủ vẫn phải y nguyên.
        "đặt nhiệt độ hai mươi lăm" to VoiceIntent.Control("temp", 25),
    )

    // ⚠ Hai bài về PHẠM VI câu nói về kính (cụm mơ hồ vs tường minh · mức Nửa) đã sang
    // `VoiceWindowScopeTest` ở lượt D 2026-09-19 — tệp này đứng sát trần 500 dòng, tách theo CHỦ ĐỀ.

    /**
     * …và cụm MỘT TỪ không được nuốt nhãn nào có chứa nó.
     * ⚠ 1.90: *"chiếu"* (của `cast`) đã gỡ; vế CÒN giá trị: *"bật đèn chiếu xa"* phải trỏ `headl`.
     */
    @Test fun `cum mot tu khong nuot nhan dai hon`() = expect(
        "bật đèn chiếu xa" to VoiceIntent.Control("headl", 1),
    )

    /**
     * ═══ [SOÁT 1.69 · P1] Câu KHÔNG có động từ mà chỉ *bắt đầu* bằng một cái tên ⇒ **không phải một lệnh** ═══
     *
     * Bệnh: nhánh (b') của [VoiceIntentParser] gắn một **động từ ngầm** cho cụm khớp tại vị trí 0, còn phần
     * đuôi thì các nhánh TOGGLE/COVER/BUTTON/MACRO của `build` **không hề đọc**. Cộng với 29 cụm MỘT từ trong
     * bộ đăng ký trùng tiếng Việt đời thường sau khi bỏ dấu (`cop` · `kinh` · `gio` · `chieu` · `tieng`…),
     * [ĐO off-car 2026-09-17] ba câu dưới đây từng ra **hành động thân xe**:
     *  • *"chiều nay mấy giờ về"* ⇒ `Control(cast, 1)`
     *  • *"cốp xe bẩn quá"* ⇒ `Control(trunk, 1)` — mở cốp trên xe đang chạy
     *  • *"kính bẩn quá"* ⇒ `Control(windows_all, 1)` — hạ hết kính (sau lượt D: `Control(window, 1)`)
     *
     * Bài này khoá đúng điều đó. Gỡ dòng `verbHit != null || after.isEmpty() || readsTail(head)` ở nhánh (b')
     * thì ba dòng đầu đỏ ngay — đã thử, đúng ba giá trị ghi trên.
     */
    @Test fun `danh tu dau cau khong co dong tu KHONG duoc thanh hanh dong`() {
        listOf("chiều nay mấy giờ về", "cốp xe bẩn quá", "kính bẩn quá", "tiếng gì lạ vậy").forEach {
            val got = VoiceIntentParser.parseOne(it)
            assertTrue(got is VoiceIntent.Unknown, "«$it» KHÔNG phải lệnh — phải là Unknown, ra: $got")
        }
    }

    /**
     * …và cổng trên KHÔNG được siết quá tay: ba họ câu dưới vẫn phải chạy y như trước.
     *
     * Cột chia: có động từ ⇒ luôn qua · tên chiếm cả câu ⇒ qua · cụm **có đọc đuôi** (STEP tra số, SELECT tra
     * nhãn, LAUNCHER tra tên app) ⇒ qua, vì lúc ấy phần đuôi đã là đối số thật.
     */
    @Test fun `cong danh tu dau cau khong sieu qua tay`() = expect(
        // ⚠ 1.90: ca *"chiếu cụm"* gỡ cùng nút `cast`; *"cốp"* vẫn phủ vế *"tên chiếm cả câu"*.
        "cốp" to VoiceIntent.Control("trunk", 1),               // tên chiếm cả câu
        "mở hết kính ra" to VoiceIntent.Macro("mac_win_open_all"), // có động từ ⇒ đuôi thừa không đổi ý định
        "nhiệt độ hai mươi bốn độ" to VoiceIntent.Control("temp", 24), // STEP đọc đuôi
        "gió mức ba" to VoiceIntent.Control("fan", 3),          // STEP đọc đuôi
    )

    @Test fun `cau phan hoi goi ten nut bang nhan cua bo dang ky`() {
        Strings.current = Lang.VI
        assertEquals("Bật Đèn đọc", VoiceReply.preview(VoiceIntent.Control("readl", 1)))
        assertEquals("Đặt Nhiệt độ = 22", VoiceReply.preview(VoiceIntent.Control("temp", 22)))
        Strings.current = Lang.EN
        assertEquals("Turn on Reading light", VoiceReply.preview(VoiceIntent.Control("readl", 1)))
        assertTrue(VoiceReply.unknown(VoiceIntent.Unknown(VoiceUnknownReason.NO_VERB, "abc")).contains("abc"))
    }

    // ══ LOG XE 2026-09-17 — số & câu hỏi (nguồn: /tmp/kvlog, 81 lượt thật) ══════════════════════════════
    //
    // Mỗi ca dưới đây là MỘT chuỗi owner/bạn bè NÓI THẬT trên xe + hành vi SAI đo được, nay khoá về đúng.

    /** «tăng/giảm nhiệt độ HAI MƯƠI BỐN độ» = ĐẶT 24, KHÔNG phải ±24 (số trong dải 17..33 = setpoint). */
    @Test fun `log xe · so trong dai nhiet do la SETPOINT tuyet doi`() = expect(
        "tăng nhiệt độ hai mươi bốn độ" to VoiceIntent.Control("temp", 24),   // was: temp +24
        "giảm nhiệt độ hai mươi hai độ" to VoiceIntent.Control("temp", 22),   // was: temp -22
        "giảm nhiệt độ hai mươi bốn độ" to VoiceIntent.Control("temp", 24),   // was: temp -24
        "tăng nhiệt độ hai mươi hai" to VoiceIntent.Control("temp", 22),      // was: temp +22
    )

    /** …nhưng số NGOÀI dải + không số ⇒ vẫn TƯƠNG ĐỐI (không phá hành vi bước đang đúng trong log). */
    @Test fun `log xe · nhiet do ngoai dai va gio-am-luong van tuong doi`() = expect(
        "giảm nhiệt độ năm độ" to VoiceIntent.Control("temp", null, relative = -5),   // 5 < 17 ⇒ bước
        "giảm nhiệt độ bốn độ" to VoiceIntent.Control("temp", null, relative = -4),
        "tăng nhiệt độ" to VoiceIntent.Control("temp", null, relative = 1),
        "giảm nhiệt độ" to VoiceIntent.Control("temp", null, relative = -1),
        "tăng quạt gió" to VoiceIntent.Control("fan", null, relative = 1),            // fan min 0 ⇒ luôn tương đối
        // ⚠ 1.90: ca *"giảm âm lượng hai"* gỡ cùng `vol`; vế *"min 0 ⇒ số trần vẫn TƯƠNG ĐỐI"* nay đo bằng `fan`.
        "giảm gió hai" to VoiceIntent.Control("fan", null, relative = -2),            // fan min 0 ⇒ giữ −2
    )

    /**
     * Log xe (build cũ) từng BẮN NHẦM điều khiển cho câu về feature ĐÃ GỠ / không phải control / xe không có.
     * 1.73 hiện trả Unknown (→ hỏi lại) — KHÓA lại để không tái phát thành bắn nhầm nguy hiểm (đèn pha, cửa sổ trời).
     * ⚠ [ĐO xe 2026-09-18] `chỉ số xăng` **rời khỏi danh sách này**: nay ra `Read(fuel_pct)`, một datum có thật và
     * đúng thứ câu ấy hỏi (`VoiceLogCases0918Test`). Thứ bài này canh — không bắn nhầm một **LỆNH GHI** — vẫn nguyên.
     */
    @Test fun `log xe · cau feature-da-go KHONG ban nham control`() {
        listOf(
            "chuyển chế độ lái",                     // was: Control(headl) — chế độ lái đã gỡ, KHÔNG được bật đèn pha
            "mở xi nhan trái", "mở xi nhan phải",     // was: Control(drl) — xi nhan không phải control
            "tất cả cửa đang khóa hay đang mở",       // was: Control(sunroof=1) — câu HỎI, KHÔNG được mở cửa sổ trời
        ).forEach { s ->
            assertTrue(one(s) is VoiceIntent.Unknown, "«$s» phải Unknown (hỏi lại), KHÔNG bắn nhầm — ra: ${one(s)}")
        }
    }

    /** Câu HỎI: datum DÀI NHẤT thắng + bỏ cụm dẫn «chỉ số» ⇒ hết «số»→gear, hết ø. */
    @Test fun `log xe · cau hoi map dung datum`() = expect(
        "chỉ số bụi mịn là bao nhiêu" to VoiceIntent.Read("pm25_level"),  // was: Read(gear)
        "chỉ số bụi mịn hiện nay" to VoiceIntent.Read("pm25_level"),      // was: Read(odometer)
        "nhiệt độ đang bao nhiêu" to VoiceIntent.Read("inside_temp"),     // was: rỗng (không datum)
        "máy lạnh đang bao nhiêu độ" to VoiceIntent.Read("inside_temp"),  // was: Read(media_vol)
        "bin còn bao nhiêu" to VoiceIntent.Read("soc"),                   // giữ đúng (chống hồi quy)
    )

    /** «tắt bụi mịn» = tắt máy lọc (pm25 control), KHÔNG mở app; câu HỎI vẫn về telemetry (read/action tách). */
    @Test fun `log xe · tat bui min dieu khien may loc khong mo app`() {
        assertEquals(VoiceIntent.Control("pm25", 0), one("tắt bụi mịn"))
        assertEquals(VoiceIntent.Read("pm25_level"), one("bụi mịn bao nhiêu"))
    }
}
