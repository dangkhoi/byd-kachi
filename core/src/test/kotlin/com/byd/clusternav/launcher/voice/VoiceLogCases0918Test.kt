package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.Strings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ BÀI CANH DỰNG TỪ **LOG XE THẬT 2026-09-18** ══════════════════════════════════════════════════════════════
 *
 * Nguồn: `docs/diagnostics/oncar-voice-cases-findings-2026-09-18.md` (§D1 · §D2 · §D3) +
 * `oncar-voice-music-vietmap-2026-09-18.md` (§BUG A) — **53 phiên voice thật** (owner + anh em) trên xe bản 1.76,
 * mỗi phiên có `.wav` + `.json` của `VoiceWavProbe`. Mỗi ca dưới đây là MỘT chuỗi người ta **nói thật** kèm hành
 * vi **đo được**, nay khoá về đúng.
 *
 * Tách khỏi [VoiceIntentParserTest] vì tệp đó đã 499/500 dòng (CLAUDE.md §4.1) — và tách theo VAI: tệp kia canh
 * *ngữ pháp* của bộ phân tích, tệp này canh *ba lỗi của một phiên log cụ thể*.
 *
 * ## Vì sao nhóm D1 là nhóm quan trọng nhất
 * Ba ca của nó không phải *"máy không hiểu"* — chúng là *"máy hiểu SAI và làm một việc khác"*, trong đó hai việc
 * chạm **thân xe** trên xe đang chạy (mở cốp · mở cửa sổ trời). Một câu không hiểu thì người ta nói lại; một
 * lệnh sai thì không lấy lại được.
 */
class VoiceLogCases0918Test {

    private fun one(s: String): VoiceIntent = VoiceIntentParser.parseOne(s)

    @AfterEach fun resetLang() { Strings.current = Lang.VI }

    // ══ D1 · CÂU HỎI KHÔNG BAO GIỜ ĐƯỢC THÀNH LỆNH GHI ════════════════════════════════════════════════

    /**
     * ⚠⚠ Ca NẶNG NHẤT của phiên log: *"tất cả cửa đang khóa hay đang mở"* → **`Control(sunroof, 1)`** = **mở
     * cửa sổ trời**.
     *
     * Hai thứ cộng lại mới ra được: bỏ dấu thì *"tất"* = *"tắt"* (một động từ), và câu không khớp datum nào nên
     * [VoicePhoneticMatch] "sửa" một âm tiết thành một cụm của xe rồi đọc lại. Bài này khoá **tính chất**, không
     * khoá một mã cụ thể: đúng chỗ này thì đòi *"không phải lệnh ghi"* mạnh hơn đòi *"phải ra Unknown"*, vì ngày
     * có datum đọc khóa cửa thì câu ấy **nên** thành một câu ĐỌC — và bài này vẫn phải xanh.
     */
    @Test fun `D1 · cau hoi lua chon KHONG BAO GIO ra lenh ghi`() {
        listOf(
            "tất cả cửa đang khóa hay đang mở",
            "cửa đang mở hay đang đóng",
            "xe đang khóa hay chưa",
            "kính đang mở hay đang đóng",
        ).forEach { s ->
            val got = one(s)
            assertFalse(
                VoiceQuestion.writesToCar(got),
                "«$s» là câu HỎI — tuyệt đối không được ra lệnh ghi, ra: $got",
            )
        }
    }

    /**
     * *"bật đèn khẩn cấp"* → **`Control(trunk, 1)`** = **mở cốp**.
     *
     * Gốc: bộ đăng ký không có nút đèn khẩn cấp (chỉ có datum ĐỌC `emergency_alarm`), nên câu rơi vào Unknown và
     * tầng chữa chính tả sửa *"cấp"* → *"cốp"* — đúng cặp lẫn owner đã nêu (*"cốp với cấp khác gì nhau đâu"*).
     */
    @Test fun `D1 · den khan cap KHONG duoc thanh mo cop`() {
        val got = one("bật đèn khẩn cấp")
        assertEquals(VoiceUnknownReason.FEATURE_GONE, (got as? VoiceIntent.Unknown)?.reason, "ra: $got")
        assertFalse(VoiceQuestion.writesToCar(got), "không được chạm thân xe")
    }

    /**
     * *"áp suất lốp bên trái là bao nhiêu"* → **`Read(soc)`**: máy trả **phần trăm pin** cho một câu hỏi về lốp.
     *
     * Gốc: tầng chữa chính tả sửa *"bên"* → *"pin"*. Câu đã nêu rõ họ **Áp …** nhưng không nói lốp nào, nên việc
     * đúng là **hỏi lại** ([VoiceClarify]) — không phải đoán sang một họ khác. Bài khoá cả hai nửa: không ra
     * `soc`, và có một câu hỏi lại nêu tên lốp.
     */
    @Test fun `D1 · cau hoi ve lop KHONG duoc tra loi bang pin, phai hoi lai`() {
        val got = one("áp suất lốp bên trái là bao nhiêu")
        assertTrue(got is VoiceIntent.Unknown, "phải hỏi lại, ra: $got")
        assertEquals(VoiceIntent.Read("soc"), VoiceIntent.Read("soc")) // mốc đọc: `soc` là thứ KHÔNG được ra
        assertTrue(got !is VoiceIntent.Read, "không được đọc một datum khác họ")
        val ask = VoiceClarify.ask(got as VoiceIntent.Unknown, 0)
        assertTrue(ask != null && ask.question.contains("lốp"), "câu hỏi lại phải nêu tên lốp, ra: ${ask?.question}")
    }

    /** …và cổng D1 KHÔNG được siết quá tay: mọi câu hỏi đang trả lời đúng vẫn phải y nguyên. */
    @Test fun `D1 · cong cau hoi khong lam cam cac cau dang chay dung`() {
        assertEquals(VoiceIntent.Read("soc"), one("pin còn bao nhiêu"))
        assertEquals(VoiceIntent.Read("inside_temp"), one("nhiệt độ đang bao nhiêu"))
        assertEquals(VoiceIntent.Read("inside_temp"), one("máy lạnh đang bao nhiêu độ"))
        assertEquals(VoiceIntent.Read("pm25_level"), one("chỉ số bụi mịn"))
        assertEquals(VoiceIntent.Read("op_mode"), one("xe đang ở chế độ lái nào"))
        assertEquals(VoiceIntent.Read("window_lf"), one("xem kính trước trái"))
        // Câu RA LỆNH vẫn ra lệnh — `hay` ở đầu câu là *"hãy"*, không phải *"hoặc"* (cổng 2 của `isChoice`).
        assertEquals(VoiceIntent.Control("readl", 1), one("hãy bật đèn đọc"))
        assertEquals(VoiceIntent.Control("window", 1), one("mở kính"))   // lượt D: cụm mơ hồ = kính LÁI
    }

    // ══ D2 · CÂU HỢP LỆ MÀ TỪ VỰNG CÒN THIẾU ══════════════════════════════════════════════════════════

    /** Nhiên liệu: cả hai câu đều ra Unknown trong log; `fuel_pct` (*"Mức xăng"*) là datum có thật. */
    @Test fun `D2 · cau hoi nhien lieu doc dung datum`() {
        assertEquals(VoiceIntent.Read("fuel_pct"), one("chỉ số xăng"))
        assertEquals(VoiceIntent.Read("fuel_pct"), one("xăng còn bao nhiêu"))
        assertEquals(VoiceIntent.Read("fuel_pct"), one("xem mức nhiên liệu"))
        // ⚠ *"mức nhiên liệu"* đứng trần vẫn là NO_VERB — đúng cổng [SOÁT 1.69 · P1] (*"một danh ngữ không có
        // động từ KHÔNG phải một lệnh"*). Cách nói mới không được mở lại cánh cửa đó.
        assertTrue(one("mức nhiên liệu") is VoiceIntent.Unknown, "danh ngữ trần không phải lệnh")
        // …mà KHÔNG cướp cụm dài hơn, và KHÔNG đụng điểm đến (đường NAV không tra từ vựng xe).
        assertEquals(VoiceIntent.Read("fuel_range_km"), one("xem tầm hoạt động xăng"))
        assertEquals(VoiceIntent.Nav("trạm xăng gần nhất"), one("Chỉ đường đến trạm xăng gần nhất"))
    }

    /**
     * *"ghế mát mức mấy"* → Unknown. Cụm hỏi *"mức mấy"* chưa có trong bảng — thêm ở [VoiceQuestion.ASK_EXTRA].
     *
     * ⚠ Chữ *"mấy"* đứng trần **không** được nhận: bỏ dấu xong nó trùng hệt *"máy"*, nên nó sẽ ép cả họ câu
     * *"bật **máy** lạnh"* thành câu hỏi. Dòng thứ hai khoá đúng ranh giới đó.
     */
    @Test fun `D2 · muc may la cum hoi, con may dung tran thi khong`() {
        assertEquals(VoiceIntent.Read("seat_vent_state"), one("ghế mát mức mấy"))
        assertEquals(VoiceIntent.Read("seat_heat_state"), one("ghế sưởi mức mấy"))
        assertEquals(VoiceIntent.Control("ac_auto", 1), one("bật máy lạnh"))
    }

    /**
     * ⚠ …và chuỗi mô hình THẬT SỰ in ra là **`ghế mất mấy`** — rụng hẳn chữ *"mức"*.
     *
     * [ĐO xe 2026-09-18] `heard: "ghế mất mấy"` → `intents: []`. Bỏ dấu thì *"mát"* = *"mất"* (`mat`) nên phần đối
     * tượng vẫn đúng; thứ duy nhất thiếu là **chữ hỏi**, và nó chỉ còn một tiếng. Cụm hai từ *"mức mấy"* của bài
     * trên không còn gì để khớp ⇒ câu rơi vào `NO_VERB` = *"không hiểu"*, dù người lái nói rất rõ.
     * Hình dạng thứ ba ([VoiceQuestion.bareAskBody]) chữa đúng ca này — ba cổng của nó ở KDoc, và cổng thứ tư là
     * *"phần thân phải ra một datum THẬT"* nên nó không bao giờ biến một câu thành lệnh ghi.
     */
    @Test fun `D2 · chu hoi rung con MOT tieng o cuoi cau van doc duoc`() {
        assertEquals(VoiceIntent.Read("seat_vent_state"), one("ghế mất mấy"))
        assertEquals(VoiceIntent.Read("seat_heat_state"), one("ghế sưởi mấy"))
        // Hai câu THẬT khác của cùng phiên log (`heard`, cùng người, 2 lượt) — *"quạt gió đang **mức mấy**"*.
        // `ac_wind` (*"Mức quạt gió"*) là datum có thật; nhãn nó cần chữ *"mức"* nên phải khai cách nói ở
        // [VoiceSynonyms.TELEMETRY], và cụm ấy trùng nút `fan` một cách **hợp lệ** (read/action tách qua `choose`).
        assertEquals(VoiceIntent.Read("ac_wind"), one("quạt gió đang mất máy"))
        assertEquals(VoiceIntent.Read("ac_wind"), one("quạt điều hòa đang mất máy"))
        // …mà KHÔNG cướp nút gió: câu RA LỆNH vẫn về `fan`.
        assertEquals(VoiceIntent.Control("fan", null, relative = 1), one("tăng quạt gió"))
    }

    /**
     * ⚠⚠ Ba cổng của [VoiceQuestion.bareAskBody] — mỗi dòng dưới đây là một câu **phải KHÔNG đổi**.
     *
     * Chữ `may` là dấu hiệu nguy hiểm nhất có thể nhận: bỏ dấu xong nó trùng hệt *"máy"*, từ nằm giữa cả họ câu
     * lệnh máy lạnh. Nên bài này quan trọng hơn bài ở trên: nó chứng minh cái giá phải trả là **0**.
     */
    @Test fun `D2 · chu hoi mot tieng KHONG duoc cuop cau lenh nao`() {
        // (1) `may` phải là từ CUỐI — *"bật **máy** lạnh"* có nó ở giữa.
        assertEquals(VoiceIntent.Control("ac_auto", 1), one("bật máy lạnh"))
        assertEquals(VoiceIntent.Control("ac_auto", 0), one("tắt máy lạnh"))
        // (2) phải còn ≥ 2 từ phía trước. Cổng này gác đúng một họ câu THẬT: danh ngữ tiếng Việt kết bằng *"máy"*
        //     mà từ đứng trước lại là một datum — *"số máy"* (số điện thoại) trỏ `gear` (*"Số"*), *"pin máy"* trỏ
        //     `soc`. Không có cổng này thì *"cho anh số máy"* trả về **vị trí cần số** của xe.
        //     ⚠ [ĐO thử phá] ba câu *"tắt máy"* / *"mở máy"* / *"nổ máy"* KHÔNG chứng minh được cổng này — chúng đã
        //     bị cổng (3) hoặc (4) chặn trước, nên hạ trần xuống 2 mà bộ bài canh vẫn xanh. Hai câu dưới mới là ca
        //     mà **chỉ** cổng (2) đứng chắn.
        listOf("số máy", "pin máy").forEach {
            assertTrue(one(it) !is VoiceIntent.Read, "«$it» là danh ngữ, không phải câu hỏi mức — ra: ${one(it)}")
        }
        listOf("tắt máy", "mở máy", "nổ máy").forEach {
            assertTrue(one(it) !is VoiceIntent.Read, "«$it» không phải câu hỏi mức, ra: ${one(it)}")
        }
        // …mà câu hỏi mức THẬT (≥ 2 từ đối tượng) vẫn đi lọt.
        assertEquals(VoiceIntent.Read("speed"), one("tốc độ mấy"))
        // (4) phần thân phải ra một datum THẬT — *"kiểm tra máy"* thì không, nên câu đi tiếp y như trước.
        assertTrue(one("kiểm tra máy") !is VoiceIntent.Read, "ra: ${one("kiểm tra máy")}")
    }

    /**
     * *"chỉ số áp suất lốp"* / *"kiểm tra áp suất"* — [ĐO xe] cả hai ra `intents: []`.
     *
     * Không thể đoán lốp nào (bốn lốp, câu không nói), nên việc đúng là **hỏi lại** — và hỏi lại **về lốp**. Bản
     * 1.76 hỏi *"Số nào — Odo tổng hay Số VIN?"* và *"Áp nào — Áp cell cao, Áp cell thấp, …?"*; chuỗi hỏi-lại đầy
     * đủ khoá ở `VoiceClarifyQuestionTest`. Ở đây chỉ khoá phần bộ phân tích: **không** ra một datum khác họ.
     *
     * ⚠ Dòng cuối là chuỗi mô hình THẬT in ra (`chỉ số áp suất **lớp**`) — bỏ dấu thì *"lớp"* = *"lốp"* nên nó tự
     * đi cùng đường, và bài này ghim điều đó lại để không ai "chữa" bằng một bảng lẫn âm thứ hai.
     */
    @Test fun `D2 · cau hoi ap suat khong doan lop nao, va khong nhay ho khac`() {
        listOf("kiểm tra áp suất", "chỉ số áp suất lốp", "chỉ số áp suất lớp").forEach { s ->
            val got = one(s)
            assertTrue(got is VoiceIntent.Unknown, "«$s» phải hỏi lại chứ không đoán, ra: $got")
            assertFalse(VoiceQuestion.writesToCar(got), "«$s» là câu ĐỌC — không được ghi vào xe")
        }
        // Nói rõ lốp nào thì đọc thẳng, không hỏi lại.
        assertEquals(VoiceIntent.Read("tyre_p_fr"), one("áp suất lốp trước phải là bao nhiêu"))
    }

    // ══ D3 · TÍNH NĂNG ĐÃ BỎ / KHÔNG CÓ NÚT — trả lời lịch sự, đúng tên ═══════════════════════════════

    @Test fun `D3 · cau ve tinh nang da bo tra loi dung ten`() {
        listOf(
            "kiểm tra dây an toàn",
            "gập gương chiếu hậu",
            "xe đang sạc pin hay không",
        ).forEach { s ->
            val got = one(s)
            assertEquals(VoiceUnknownReason.FEATURE_GONE, (got as? VoiceIntent.Unknown)?.reason, "câu: «$s» ra $got")
            val say = VoiceReply.unknown(got as VoiceIntent.Unknown)
            assertTrue(say.contains("đã bỏ"), "phải nói là đã bỏ, ra: $say")
        }
    }

    @Test fun `D3 · tinh nang chua bao gio co nut thi noi la chua dieu khien duoc`() {
        val got = one("bật đèn khẩn cấp") as VoiceIntent.Unknown
        val say = VoiceReply.unknown(got)
        assertTrue(say.contains("chưa điều khiển được"), "ra: $say")
        assertTrue(say.contains("đèn khẩn cấp"), "phải gọi đúng tên tính năng, ra: $say")
        Strings.current = Lang.EN
        assertTrue(VoiceReply.unknown(got).contains("hazard"), "câu tiếng Anh phải nêu tên, ra: ${VoiceReply.unknown(got)}")
    }

    /**
     * …và bảng [VoiceFeatureGone] **không được** giết đường ĐỌC còn sống.
     *
     * `mirror_fold` · `light_left_turn` · `op_mode` vẫn là datum có thật: bảng chỉ nói về **nút**, và nó chỉ được
     * hỏi khi câu đã không hiểu được.
     */
    @Test fun `D3 · bang tinh nang da bo khong giet duong DOC con song`() {
        assertEquals(VoiceIntent.Read("mirror_fold"), one("xem gương chiếu hậu"))
        assertEquals(VoiceIntent.Read("light_left_turn"), one("xem xi nhan trái"))
        assertEquals(VoiceIntent.Read("op_mode"), one("xem chế độ lái"))
        // Không có gì để hỏi lại khi tính năng đã bỏ — nói lại cũng ra đúng câu ấy.
        val gone = one("kiểm tra dây an toàn") as VoiceIntent.Unknown
        assertEquals(null, VoiceClarify.ask(gone, 0), "tính năng đã bỏ thì KHÔNG hỏi lại")
    }

    // ══ ⑤ · CHỌN APP DẪN ĐƯỜNG khi tên app bị rụng âm cuối ════════════════════════════════════════════

    /**
     * [ĐO xe 2026-09-18 · §BUG A] Một dòng log, **ba** lỗi: owner nói *"…bằng VietMap"*, mô hình in ra
     * *"bằng vietma"*, Kachi bắn `google.navigation:q=chợ bến thành **bằng vietma**` tới **Google Maps**.
     *
     * Sau vá: tách đúng app **và** địa chỉ sạch rác.
     */
    @Test fun `chon app dan duong chiu duoc ten rung am cuoi`() {
        val want = VoiceIntent.Nav("chợ bến thành", VoiceAppTargets.VIETMAP)
        listOf(
            "dẫn đường tới chợ bến thành bằng vietmap",
            "dẫn đường tới chợ bến thành bằng vietma",
            "dẫn đường tới chợ bến thành bằng việt map",
        ).forEach { assertEquals(want, one(it), "câu: «$it»") }
        assertEquals(VoiceIntent.Nav("sân bay", VoiceAppTargets.WAZE), one("dẫn đường đến sân bay bằng waze"))
    }

    /**
     * …và phép khớp mờ KHÔNG được cắt một điểm đến có chữ *"bằng"* trong tên.
     *
     * Ba cổng của [VoiceAppTargets.bySpokenLoose] + ba cổng của [VoiceTailClause.appAfterMarker] cùng giữ điều
     * này; câu *"cầu Bằng Lăng"* là ca thật mà KDoc `withTarget` đã nêu từ V1.1.
     */
    @Test fun `khop mo ten app khong cat mat diem den`() {
        assertEquals(VoiceIntent.Nav("cầu Bằng Lăng"), one("dẫn đường tới cầu Bằng Lăng"))
        assertEquals(VoiceIntent.Nav("chợ Bằng"), one("dẫn đường tới chợ Bằng"))
        // Rụng âm cuối chỉ nhận khi cụm đủ dài: `bySpokenLoose` từ chối cụm < 5 ký tự.
        assertEquals(null, VoiceAppTargets.bySpokenLoose(listOf("y")))
        assertEquals(null, VoiceAppTargets.bySpokenLoose(listOf("map")))
        assertEquals(VoiceAppTargets.VIETMAP, VoiceAppTargets.bySpokenLoose(listOf("vietma"))?.key)
        // …và chỉ rụng ĐÚNG một ký tự cuối, không phải mọi cụm gần giống.
        assertEquals(null, VoiceAppTargets.bySpokenLoose(listOf("vietm")))
        assertEquals(null, VoiceAppTargets.bySpokenLoose(listOf("vietmax")))
    }

    /**
     * ⑤ · Ba cụm đánh dấu *"bằng / qua / dùng &lt;app&gt;"* — owner nói cả ba, nên cả ba phải tách được app.
     *
     * *"dùng"* là cụm mới (2026-09-18). Bỏ dấu thì `dung` trùng **ba** từ: *"dừng"* (động từ PAUSE), *"đừng"*,
     * *"đúng"* — xem KDoc [VoiceLexicon.BY_APP_MARKERS] về ba cổng cho phép nó vào. Dòng *"vietmáp"* là ca **khớp
     * CHÍNH XÁC** (bỏ dấu ra đúng `vietmap`), cố ý đặt cạnh *"vietma"* để thấy hai đường khác nhau: một cái không
     * cần phép khớp mờ, một cái cần.
     */
    @Test fun `chon app dan duong nhan ca ba cum danh dau`() {
        val want = VoiceIntent.Nav("chợ bến thành", VoiceAppTargets.VIETMAP)
        listOf(
            "dẫn đường tới chợ bến thành bằng vietmap",
            "dẫn đường tới chợ bến thành qua vietmap",
            "dẫn đường tới chợ bến thành dùng vietmap",
            "dẫn đường tới chợ bến thành bằng vietmáp",
            "dẫn đường tới chợ bến thành dùng vietma",
        ).forEach { assertEquals(want, one(it), "câu: «$it»") }
    }

    /**
     * ⚠ …và cụm *"dùng"* KHÔNG được đổi một câu nào đang chạy.
     *
     * Ba câu dưới đây mang đúng chuỗi `dung` sau khi bỏ dấu nhưng **không** có hình dạng `… dùng <tên app>` kết
     * thúc câu, nên cổng (a)+(b) của [VoiceTailClause.appAfterMarker] loại chúng ngay.
     */
    @Test fun `cum danh dau dung KHONG cuop cau dung nhac nao`() {
        assertEquals(VoiceIntent.Media(VoiceMediaOp.PAUSE), one("dừng nhạc"))
        assertEquals(VoiceIntent.Media(VoiceMediaOp.PAUSE), one("tạm dừng"))
        assertEquals(VoiceIntent.Media(VoiceMediaOp.PLAY, app = VoiceAppTargets.YOUTUBE), one("phát nhạc trên youtube"))
        assertTrue(one("đừng mở youtube") !is VoiceIntent.OpenApp, "ra: ${one("đừng mở youtube")}")
    }
}
