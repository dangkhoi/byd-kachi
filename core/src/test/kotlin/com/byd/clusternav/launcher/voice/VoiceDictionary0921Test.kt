package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * ═══ 1.91 · MỞ RỘNG DICTIONARY — NHIỀU CÁCH NÓI CHO CÙNG MỘT LỆNH ═════════════════════════════════════════════
 *
 * Owner 2026-09-21: *"nhiều câu tương tự nhau cho 1 command"* (kèm ví dụ ghế mát: *mát ghế · mát đít · mát mông ·
 * quạt ghế · thổi mát ghế*). Bài này khoá **kết quả** của lượt mở rộng — mỗi dòng là một cách nói mới phải ra đúng
 * nút/datum nó nhắm tới.
 *
 * ## Vì sao một tệp RIÊNG, không nhét vào `VoiceIntentParserTest`
 * Tệp kia đã **536 dòng** (vượt trần 500 của CLAUDE.md §4.1 từ trước lượt này) và nó trả lời một câu hỏi khác:
 * *"các LUẬT của bộ phân tích có đúng không"*. Tệp này chỉ trả lời *"bảng DỮ LIỆU cách nói có phủ đủ không"* —
 * tách theo VAI, đúng tiền lệ `VoiceWindowScopeTest` (lượt D) và `VoiceLogCases0918Test`.
 *
 * ⚠ Phạm vi kính/cửa sổ (*"mở hết cửa sổ"*) nằm ở [VoiceWindowScopeTest], không lặp ở đây.
 */
class VoiceDictionary0921Test {

    private fun one(s: String): VoiceIntent = VoiceIntentParser.parseOne(s)

    /** Bảng ca: câu → ý định mong đợi. Thông báo lỗi kèm nguyên văn câu để đọc là biết ca nào đỏ. */
    private fun expect(vararg cases: Pair<String, VoiceIntent>) =
        cases.forEach { (s, want) -> assertEquals(want, one(s), "câu: \"$s\"") }

    // ══ 1 · GHẾ MÁT / GHẾ SƯỞI — ví dụ owner đưa tận chữ ═══════════════════════════════════════════════

    /**
     * Ghế là nút SELECT có *"Tắt"* ở index 0, nên *"bật …"* = mức 1 và *"tắt …"* = 0 (`offOnSelect`, 1.84); nêu mức
     * thì [VoiceControlParse] khớp nhãn lựa chọn (*"mức 2"* → index 2).
     */
    @Test fun `ghe mat co nhieu cach noi`() = expect(
        "bật mát ghế" to VoiceIntent.Control("seatc", 1),
        "tắt mát ghế" to VoiceIntent.Control("seatc", 0),
        "bật mát đít" to VoiceIntent.Control("seatc", 1),
        "bật mát mông" to VoiceIntent.Control("seatc", 1),
        "bật thổi mát ghế" to VoiceIntent.Control("seatc", 1),
        "bật ghế lái mát" to VoiceIntent.Control("seatc", 1),
        "mát ghế mức 2" to VoiceIntent.Control("seatc", 2),
        // …và cách nói CŨ không được rụng (chống hồi quy của chính lượt thêm).
        "bật quạt ghế" to VoiceIntent.Control("seatc", 1),
        "bật làm mát ghế" to VoiceIntent.Control("seatc", 1),
    )

    @Test fun `ghe suoi co nhieu cach noi`() = expect(
        "bật ấm ghế" to VoiceIntent.Control("seath", 1),
        "bật làm ấm ghế" to VoiceIntent.Control("seath", 1),
        "bật sưởi đít" to VoiceIntent.Control("seath", 1),
        "bật sưởi mông" to VoiceIntent.Control("seath", 1),
        "tắt sưởi ghế" to VoiceIntent.Control("seath", 0),
        "ghế sưởi mức 1" to VoiceIntent.Control("seath", 1),
    )

    // ══ 2 · ĐIỀU HÒA · GIÓ · NHIỆT · SẤY · LỌC ════════════════════════════════════════════════════════

    @Test fun `dieu hoa va gio co nhieu cach noi`() = expect(
        "bật quạt tự động" to VoiceIntent.Control("ac_auto", 1),
        "bật làm mát xe" to VoiceIntent.Control("ac_auto", 1),
        "đặt mức quạt 3" to VoiceIntent.Control("fan", 3),
        "đặt tốc độ gió 2" to VoiceIntent.Control("fan", 2),
        "đặt gió điều hòa 4" to VoiceIntent.Control("fan", 4),
        // ⚠ cụm ĐÃ ĐO `BẬT MÁY LẠNH` phải sống — lượt này cố ý không thêm cụm nào bắt đầu bằng *"máy lạnh"*.
        "bật máy lạnh" to VoiceIntent.Control("ac_auto", 1),
    )

    /**
     * `nhiet do xe` khai ở CẢ HAI bảng (nút `temp` + datum `inside_temp`) — `VoiceIntentParser.choose` phân xử theo
     * loại động từ. Khai một bên thôi thì nửa kia thành MISMATCH, nên bài này canh **cả hai chiều**.
     */
    @Test fun `nhiet do co nhieu cach noi, doc va ghi tach nhau`() = expect(
        "đặt nhiệt độ xe 24" to VoiceIntent.Control("temp", 24),
        "xem nhiệt độ xe" to VoiceIntent.Read("inside_temp"),
        "đặt độ nóng 25" to VoiceIntent.Control("temp", 25),
        "đặt độ lạnh 20" to VoiceIntent.Control("temp", 20),
    )

    @Test fun `say kinh va lay gio co nhieu cach noi`() = expect(
        "bật tan sương" to VoiceIntent.Control("defrost", 1),
        "bật khử sương" to VoiceIntent.Control("defrost", 1),
        "bật sấy kính hậu" to VoiceIntent.Control("defrost_rear", 1),
        "bật tuần hoàn gió" to VoiceIntent.Control("recirc", 1),
        "bật tuần hoàn khí" to VoiceIntent.Control("recirc", 1),
    )

    @Test fun `loc bui co nhieu cach noi`() = expect(
        "bật khử bụi" to VoiceIntent.Control("pm25", 1),
        "bật lọc khí" to VoiceIntent.Control("pm25", 1),
        "tắt lọc khí" to VoiceIntent.Control("pm25", 0),
        // Nút BẤM: mọi động từ hành động đều là *"bấm"*, và cả câu là TÊN việc nên nói trần cũng chạy.
        "lọc gấp" to VoiceIntent.Control("pm25_clean_now", null),
    )

    // ══ 3 · THÂN XE · ĐÈN · TIỆN NGHI ═════════════════════════════════════════════════════════════════

    @Test fun `khoa cop cua co nhieu cach noi`() = expect(
        "mở cốp hậu" to VoiceIntent.Control("trunk", 1),
        "mở cửa cốp" to VoiceIntent.Control("trunk", 1),
        "mở khoang hành lý" to VoiceIntent.Control("trunk", 1),
        "đóng cốp hậu" to VoiceIntent.Control("trunk", 0),
        // ⚠ cụm ĐÃ ĐO `MỞ KHÓA CỬA` phải sống — lượt này cố ý không thêm cụm nào bắt đầu bằng *"khóa cửa"*.
    )

    @Test fun `den co nhieu cach noi`() = expect(
        "bật đèn cabin" to VoiceIntent.Control("readl", 1),
        "bật chiếu xa" to VoiceIntent.Control("headl", 1),
        "bật đèn lớn" to VoiceIntent.Control("headl", 1),
        "bật đèn chạy ban ngày" to VoiceIntent.Control("drl", 1),
        "tắt đèn chạy ban ngày" to VoiceIntent.Control("drl", 0),
    )

    @Test fun `kinh tung cua, noc, rem, sac co nhieu cach noi`() = expect(
        "mở kính người lái" to VoiceIntent.Control("win_lf", 1),
        "mở cửa sổ tài xế" to VoiceIntent.Control("win_lf", 1),
        "mở kiếng bên lái" to VoiceIntent.Control("win_lf", 1),
        "mở kiếng bên phụ" to VoiceIntent.Control("win_rf", 1),
        "mở kính nóc" to VoiceIntent.Control("sunroof", 1),
        "mở rèm trời" to VoiceIntent.Control("sunshade", 1),
        "mở che nắng" to VoiceIntent.Control("sunshade", 1),
        "bật đế sạc" to VoiceIntent.Control("wireless_charge", 1),
        "bật camera toàn cảnh" to VoiceIntent.Control("cam", 1),
        // ⚠ cụm ĐÃ ĐO `MỞ KÍNH TRƯỚC TRÁI` phải sống.
        "mở kính trước trái" to VoiceIntent.Control("win_lf", 1),
    )

    // ══ 4 · THÔNG TIN ĐỌC ═════════════════════════════════════════════════════════════════════════════

    @Test fun `thong tin doc co nhieu cach noi`() = expect(
        "xem dung lượng pin" to VoiceIntent.Read("soc"),
        "xem quãng đường còn lại" to VoiceIntent.Read("ev_range_km"),
        "xem tốc độ xe" to VoiceIntent.Read("speed"),
        "xem nhiệt độ bên ngoài" to VoiceIntent.Read("ext_temp"),
        "xem mức bụi mịn" to VoiceIntent.Read("pm25_level"),
        "xem số km xe đã chạy" to VoiceIntent.Read("odometer"),
        "xem bình xăng" to VoiceIntent.Read("fuel_pct"),
        "xem lốp trước trái" to VoiceIntent.Read("tyre_p_fl"),
        "xem lốp trước phải" to VoiceIntent.Read("tyre_p_fr"),
        "xem lốp sau trái" to VoiceIntent.Read("tyre_p_rl"),
        "xem lốp sau phải" to VoiceIntent.Read("tyre_p_rr"),
        // ⚠ cụm ĐÃ ĐO `XEM PIN` phải sống — lượt này cố ý không thêm cụm nào bắt đầu bằng *"pin"*.
        "xem pin" to VoiceIntent.Read("soc"),
    )

    // ══ 5 · CÁC CỤM CỐ Ý **KHÔNG** NHẬN — chống "mở rộng" thành "đoán bừa" ════════════════════════════

    /**
     * ⚠⚠ Bài quan trọng nhất tệp này: bốn cụm owner có nêu mà lượt này **TỪ CHỐI**, mỗi cụm một lý do đã ghi tại
     * chỗ khai trong [VoiceSynonyms]. Không có bài này thì lần sau ai đó "làm cho đủ" sẽ thêm chúng vào, và cả bốn
     * cái sai đều **im lặng** (máy trả lời *"đã xong"* cho một việc khác).
     *
     *  • *"lấy gió ngoài"* = chiều NGƯỢC của `recirc` ⇒ trỏ vào đó là BẬT tuần hoàn trong khi người ta xin TẮT;
     *  • *"mở cửa"* (hai từ) khớp tại **vị trí 0** nên `headMatch` sẽ lấy nó và *"mở cửa sổ"* mất đường tới
     *    `window` — [ĐO off-car] đúng bẫy *"cụm ngắn đầu câu cướp cụm dài phía sau"*;
     *  • *"bật đèn ngay"* = *"bật đèn NGAY BÂY GIỜ"*, không phải đèn ban ngày (`den ngay` bỏ dấu trùng nhau);
     *  • *"sấy gương"* / *"lau kính"* nói về bộ phận mà registry **không còn nút** (gương gập, gạt mưa đã gỡ).
     */
    @Test fun `cum bi tu choi van phai la KHONG HIEU, khong duoc doan`() {
        listOf(
            "mở cửa",
            "bật đèn ngay",
            "sấy gương",
            "lau kính",
        ).forEach {
            val got = one(it)
            assertEquals(
                VoiceIntent.Unknown::class.java, got.javaClass,
                "câu \"$it\" phải là KHÔNG HIỂU (hỏi lại), ra: $got",
            )
        }
        // …và cái mà *"mở cửa"* KHÔNG được phép nuốt: câu dài hơn vẫn tới đúng nút kính lái.
        assertEquals(VoiceIntent.Control("win_lf", 1), one("mở cửa sổ"))
    }

    @Test fun `lay gio ngoai → recirc TAT`() {
        assertEquals(VoiceIntent.Control("recirc", 0), one("lấy gió ngoài"))
        assertEquals(VoiceIntent.Control("recirc", 0), one("gió ngoài"))
    }

    /**
     * ⚠⚠ [SOÁT 2026-09-21 · P1] Chiều PHỦ ĐỊNH của cùng cụm đó.
     *
     * Luật *"gió ngoài ⇒ recirc TẮT"* bản đầu trả `Control(recirc, 0)` cho **mọi** câu chứa "gio"+"ngoai", nên
     * [ĐO] cả ba câu dưới đây **BẬT gió ngoài** trong khi người lái vừa xin THÔI lấy gió ngoài — lệnh chạy ngược
     * hẳn với lời nói. Nay luật đọc động từ (nó đứng SAU `verbHit`): OFF/CLOSE ⇒ `recirc` BẬT (nút hai trạng thái
     * nên chiều ngược suy ra được, không phải đoán).
     */
    @Test fun `tat hoac dong gio ngoai → recirc BAT, khong duoc chay nguoc`() = expect(
        "tắt gió ngoài" to VoiceIntent.Control("recirc", 1),
        "tắt lấy gió ngoài" to VoiceIntent.Control("recirc", 1),
        "đóng gió ngoài" to VoiceIntent.Control("recirc", 1),
        // Chiều thuận KHÔNG bị bản vá làm hỏng, và nút tuần hoàn nói thẳng thì vẫn đi đường cũ.
        "bật gió ngoài" to VoiceIntent.Control("recirc", 0),
        "bật tuần hoàn gió" to VoiceIntent.Control("recirc", 1),
    )

    @Test fun `dieu hoa X do khong verb → dat nhiet do (temp)`() {
        assertEquals(VoiceIntent.Control("temp", 25), one("điều hòa 25 độ"))
        assertEquals(VoiceIntent.Control("temp", 24), one("máy lạnh 24 độ"))
    }

    /**
     * ⚠⚠ [SOÁT 2026-09-21 · P1] Cổng verbless *"điều hòa &lt;số&gt; độ"* KHÔNG được biến một câu-không-phải-lệnh
     * thành một lệnh GHI.
     *
     * Bản đầu `return` thẳng `VoiceControlParse.control("ac_auto", SET, …)`. `ac_auto` là TOGGLE, nên khi
     * `degreesSetpoint` không khớp (số KHÔNG đứng ngay trước chữ *"độ"*) nhánh `TOGGLE + SET` trả
     * `Control(ac_auto, 1)` ⇒ [ĐO] *"điều hòa chế độ hai"* **BẬT điều hòa**. Đó chính là họ lỗi [P1] mà 1.83 đã vá
     * một lần cho *"bật điều hòa chế độ hai"* (bản vá ấy = `degreesSetpoint` đòi số ngay trước *"độ"*), và cổng mới
     * đi vòng qua nó — **cùng một lỗi, cửa khác**.
     *
     * Ca cuối là phần thứ hai của cùng bản vá: cổng nay đòi cụm HAI TỪ (*"điều hòa"* hoặc *"máy lạnh"*) thay vì
     * riêng chữ *"điều"*, nên *"điều chỉnh ghế 2 độ"* hết bị đọc thành **đặt nhiệt cabin = 17**.
     */
    @Test fun `khong phai setpoint thi phai HOI LAI, khong duoc bat dieu hoa`() {
        listOf(
            "điều hòa chế độ hai",
            "điều hòa chế độ 2",
            "điều hòa mức độ 3",
            "điều chỉnh ghế 2 độ",
        ).forEach {
            val got = one(it)
            assertEquals(
                VoiceIntent.Unknown::class.java, got.javaClass,
                "câu \"$it\" phải là KHÔNG HIỂU (hỏi lại) — không được thành lệnh ghi, ra: $got",
            )
        }
    }

}
