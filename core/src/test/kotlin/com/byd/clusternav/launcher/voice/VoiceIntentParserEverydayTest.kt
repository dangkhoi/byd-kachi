package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LayoutPreset
import com.byd.clusternav.launcher.Strings
import com.byd.clusternav.launcher.voice.VoiceIntentParserHarness.all
import com.byd.clusternav.launcher.voice.VoiceIntentParserHarness.apps
import com.byd.clusternav.launcher.voice.VoiceIntentParserHarness.expect
import com.byd.clusternav.launcher.voice.VoiceIntentParserHarness.one
import com.byd.clusternav.launcher.voice.VoiceIntentParserHarness.profiles
import com.byd.clusternav.launcher.voice.VoiceIntentParserHarness.unknown
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Nửa sau của [VoiceIntentParserTest] — câu ĐỜI THƯỜNG (soát 2026-09-14) · bố cục bằng giọng nói (L7) · cụm hỏi giữa câu ·
 * số nói rút gọn · danh từ đầu câu · câu phản hồi · LOG XE 2026-09-17. Tách THUẦN theo CHỦ ĐỀ (568 dòng → trần 500, L6-debt
 * 2026-09-27); thân từng bài giữ nguyên byte, trợ giúp dùng chung ở [VoiceIntentParserHarness].
 */
class VoiceIntentParserEverydayTest {

    @AfterEach fun resetLang() { Strings.current = Lang.VI }

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
        // ⚠ 1.90: ca *"chiếu cụm"* gỡ cùng nút `cast`. ⚠ 2.93 VOICE-BARE-NOUN-IMPLICIT-VERB: *"cốp"* trần nay HỎI LẠI (bộ phận
        // chuyển động không nhận động từ ngầm — `VoiceBareCoverTest`) ⇒ vế *"tên chiếm cả câu"* phủ bằng một nút bật/tắt.
        "đèn đọc" to VoiceIntent.Control("readl", 1),           // tên chiếm cả câu
        "mở hết kính ra" to VoiceIntent.Macro("mac_win_open_all"), // có động từ ⇒ đuôi thừa không đổi ý định
        "nhiệt độ hai mươi bốn độ" to VoiceIntent.Control("temp", 24), // STEP đọc đuôi
        "gió mức ba" to VoiceIntent.Control("fan", 3),          // STEP đọc đuôi
    )

    @Test fun `cau phan hoi goi ten nut bang nhan cua bo dang ky`() {
        Strings.current = Lang.VI
        assertEquals("Bật đèn đọc", VoiceReply.preview(VoiceIntent.Control("readl", 1)))
        assertEquals("Đặt nhiệt độ 22", VoiceReply.preview(VoiceIntent.Control("temp", 22)))
        Strings.current = Lang.EN
        assertEquals("Turn on reading light", VoiceReply.preview(VoiceIntent.Control("readl", 1)))
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
