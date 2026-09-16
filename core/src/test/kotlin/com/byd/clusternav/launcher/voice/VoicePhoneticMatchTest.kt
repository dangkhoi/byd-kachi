package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ H7 · CHỊU LỖI CHÍNH TẢ — khoá đúng những câu ĐÃ ĐO, và khoá cả những câu KHÔNG được nhận ═════════════════
 *
 * Owner 2026-09-16: *"cốp với cấp khác gì nhau đâu"*.
 *
 * ## Nguồn của mọi ca ở đây
 *  • **[ĐO giọng thật 2026-09-16]** — 5 bản thu (owner · con gái · ba giọng Nam) giải mã bằng đúng mô hình
 *    đang ship, lưu ở `docs/diagnostics/voice-rec-2026-09-16/segments-*-shipping.json`. Cột trái của bảng
 *    *"cặp thật"* là **chuỗi mô hình in ra**, không phải chuỗi bịa;
 *  • **[ĐO host]** `docs/diagnostics/voice-mishear-2026-09-16.md` §5 + `emulator-voice-e2e-2026-09-15.md` §3 L3.
 *
 * ## Bài quan trọng nhất ở đây KHÔNG phải bài "nhận đúng"
 * Một bộ chịu lỗi nhận mọi thứ thì **tệ hơn là không có**: nó biến mọi câu nói đời thường trong cabin thành
 * một lệnh cho xe. Nên `cau doi thuong KHONG duoc thanh lenh xe` mới là bài giữ cả tính năng, và
 * `nhap nhang thi HOI LAI` là bài giữ lời hứa *"không bao giờ đoán giữa hai lệnh"*.
 */
class VoicePhoneticMatchTest {

    private fun one(text: String): VoiceIntent = VoiceIntentParser.parseOne(text)

    private fun control(text: String): VoiceIntent.Control {
        val got = one(text)
        assertTrue(got is VoiceIntent.Control, "«$text» phải là Control, ra: $got")
        return got as VoiceIntent.Control
    }

    private fun read(text: String): VoiceIntent.Read {
        val got = one(text)
        assertTrue(got is VoiceIntent.Read, "«$text» phải là Read, ra: $got")
        return got as VoiceIntent.Read
    }

    // ── (1) Cặp ĐÃ ĐO trên giọng thật ────────────────────────────────────────────────────────────

    @Test
    fun `cap nghe nham tren giong THAT tro ve dung nut`() {
        // cột trái = chuỗi mô hình in ra · cột phải = mã phải trỏ tới · chú thích = tệp thu + số đoạn.
        listOf(
            "mở cấp sau" to "trunk",            // miennam-a #39 · miennam-b #26 — cốp → cấp (owner nêu tên)
            "ở cấp sau" to "trunk",             // miennam-c #31 — mất luôn cả phụ âm đầu của động từ
            "mở góc sau" to "trunk",            // owner #59
            "bằng ghế sưởi" to "seath",         // owner #58 — bật → bằng
            "bật ghế sửi" to "seath",           // host §5 — sưởi → sửi
            "mở kim trước trái" to "win_lf",    // bé #51 — kính → kim
            "mở kín trước trái" to "win_lf",    // host, e2e 09-15 §3 L3
            // ⚠ (V) FEATURE-FILTER 2026-09-17: hai cặp ĐÃ ĐO «chế độ đá/gái thể thao» (bé #38 · owner #45)
            // trỏ tới nút `drive_mode` — nút đó đã gỡ theo lệnh owner nên không còn đích để trỏ. Giữ lại ghi chép
            // ở đây (luật dự án: không xoá lịch sử đo) nhưng không assert nữa.
            "bật lọt bụi" to "pm25",            // quy luật phụ âm cuối t↔c, không phải cặp chép tay
            "bật đèn đang đọc" to "readl",      // host §5 — đèn đọc sách → đang đọc sách
        ).forEach { (text, id) -> assertEquals(id, control(text).id, "«$text» phải ra nút $id") }
    }

    @Test
    fun `cap nghe nham tren giong THAT tro ve dung thong tin doc`() {
        listOf(
            "xem bên" to "soc",                     // miennam-c #24
            "biên còn bao nhiêu" to "soc",          // owner #39 · #57
            "bin còn bao nhiêu" to "soc",           // bé #14
            "xem phim" to "soc",                    // owner #38
            "xem tin" to "soc",                     // host — e2e 09-15 §3 L3, và §5 «tin còn bao nhiêu» ×4
            "xe áp suất lốp trước trái" to "tyre_p_fl",  // bé #36 · #53 — xem → xe
            "sẽ áp suất lốp trước trái" to "tyre_p_fl",  // owner #40 — xem → sẽ
        ).forEach { (text, id) -> assertEquals(id, read(text).datumId, "«$text» phải đọc $id") }
    }

    @Test
    fun `dong tu nghe nham van ra dung viec`() {
        assertEquals(VoiceIntent.Media(VoiceMediaOp.PAUSE), one("dần nhạc"))  // miennam-c #34 — dừng → dần
        // [ĐO ×2] `w09` của bộ 25 WAV: *"dừng nhạc"* → *"rừng nhạc"* ở lượt máy ảo 09-15 (§3 L3) và **đo lại**
        // tối 09-16 sau khi VAD cắt đuôi làm lệch nhẹ tệp ấy. Chữ vẫn sai, nhưng lệnh của người lái phải chạy.
        assertEquals(VoiceIntent.Media(VoiceMediaOp.PAUSE), one("rừng nhạc"))
        // owner #51 «TÁM MẬT ĐỘ HAI MƯƠI HAI ĐỘ»: cả cụm tên nút nghe trượt, con số vẫn phải đi tới nơi.
        assertEquals(VoiceIntent.Control("temp", 22), one("mật độ hai mươi hai độ"))
    }

    // ── (2) Bài QUAN TRỌNG NHẤT: câu đời thường vẫn phải là "không hiểu" ─────────────────────────

    @Test
    fun `cau doi thuong KHONG duoc thanh lenh xe`() {
        // 5 câu đầu đã có trong `scripts/emulator/voice-cases.tsv` t63–t67 — giữ đúng chữ để hai tầng nói
        // cùng một chuyện. Phần sau là câu người ta nói trong cabin, chọn cố ý sao cho **gần** từ vựng của xe.
        listOf(
            "hôm nay trời đẹp quá", "kể cho tôi nghe một câu chuyện", "bật abcxyz", "kachi ơi", "xem xe",
            "gọi cho vợ", "nhắc tôi đổ xăng ngày mai", "mua giúp tôi ly cà phê", "đường này kẹt xe quá",
            "con gái tôi đi học chưa", "trời hôm nay mưa không", "thôi khỏi cần", "nay ăn gì",
            "xe này chạy êm ghê", "alo alo một hai ba", "chán quá đi mất", "đói bụng quá",
            "Quốc ca được sáng tác năm nào", "Thời tiết hôm nay thế nào", "Tìm trạm xăng gần đây",
        ).forEach {
            val got = one(it)
            assertTrue(got is VoiceIntent.Unknown, "«$it» KHÔNG phải lệnh — phải là Unknown, ra: $got")
        }
    }

    @Test
    fun `giu nguyen LY DO khong hieu khi chua duoc gi`() {
        // Chữa chính tả chỉ được phép **thay** một câu không hiểu bằng một ý định CÓ NGHĨA. Không sửa được thì
        // câu báo cũ phải còn nguyên — kể cả lý do, vì tầng hỏi lại ([VoiceClarify]) rẽ nhánh theo đúng nó.
        listOf(
            "tắt hết đèn" to VoiceUnknownReason.NO_OBJECT,
            "về chỗ nào đó" to VoiceUnknownReason.NO_VERB,
            "bật cái đó" to VoiceUnknownReason.NO_OBJECT,
            // *"tăng đèn đọc"*: cụm *"đèn đọc"* khớp CHÍNH XÁC ⇒ vùng cấm ⇒ không ai được "sửa" nó thành
            // một câu bật đèn. Không có luật vùng cấm thì đây là ca máy **làm một việc** mà câu không bảo.
            "tăng đèn đọc" to VoiceUnknownReason.MISMATCH,
        ).forEach { (text, reason) ->
            val got = one(text)
            assertTrue(got is VoiceIntent.Unknown && got.reason == reason, "«$text» phải là $reason, ra: $got")
        }
    }

    // ── (3) Đường CHÍNH XÁC không được đụng tới ──────────────────────────────────────────────────

    @Test
    fun `cau nghe DUNG thi tang chiu loi khong duoc hoi toi`() {
        // `repair` trả `null` = *"không có gì để sửa"*. Đây là cách duy nhất chứng minh bằng máy rằng đường
        // chính xác vẫn đi trước và tầng này **không** chen vào (chỉ so kết quả cuối thì không phân biệt được
        // "không chen vào" với "chen vào rồi ra cùng kết quả").
        val terms = VoiceGrammar.terms()
        listOf("bật đèn đọc", "mở kính trước trái", "xem pin", "dừng nhạc", "mở hết kính", "khoá xe")
            .forEach {
                assertNull(
                    VoicePhoneticMatch.repair(VoiceLexicon.tokenize(it), terms),
                    "«$it» nghe đúng ⇒ không được sửa gì",
                )
            }
    }

    @Test
    fun `cau dang chay tot khong doi mot chu`() {
        assertEquals(VoiceIntent.Control("readl", 1), one("bật đèn đọc"))
        assertEquals(VoiceIntent.Control("win_lf", 1), one("mở kính trước trái"))
        assertEquals(VoiceIntent.Control("temp", 24), one("nhiệt độ hai mươi bốn độ"))
        assertEquals(VoiceIntent.Macro("mac_win_open_all"), one("mở hết kính"))
        assertEquals("soc", read("xem pin").datumId)
    }

    // ── (4) Nhập nhằng thì HỎI, không đoán ───────────────────────────────────────────────────────

    @Test
    fun `nhap nhang thi HOI LAI, tuyet doi khong doan giua hai lenh`() {
        // Từ vựng DỰNG RIÊNG cho bài này, không lấy từ bộ đăng ký: cần đúng hai cụm **cách đều** chuỗi nghe
        // được, mà danh mục thật thì đổi theo từng dòng registry ⇒ dựng riêng mới khoá được đúng cái luật.
        // `cốp` cách `cấp` và `góc` đúng một cặp ĐÃ ĐO ⇒ hoà ⇒ không được chọn bừa.
        val terms = listOf(
            VoiceTerm(listOf("cap"), VoiceTermKind.CONTROL, "x"),
            VoiceTerm(listOf("goc"), VoiceTermKind.CONTROL, "y"),
        )
        assertNull(
            VoicePhoneticMatch.repair(VoiceLexicon.tokenize("mở cốp"), terms),
            "hai cụm ngang giá ⇒ phải trả null (để câu giữ nguyên Unknown và tầng hỏi lại vào việc)",
        )
        // …và tầng hỏi lại THẬT SỰ có câu để hỏi, chứ không phải im lặng.
        val ask = VoiceClarify.ask(VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, "mở cốp"), round = 0)
        assertNotNull(ask, "câu không hiểu phải còn cửa hỏi lại")
    }

    @Test
    fun `mot cum ngang gia duy nhat thi van duoc chon`() {
        // Đối chứng của bài trên: bỏ cụm thứ hai đi thì chính chuỗi ấy được sửa. Không có bài đối chứng này
        // thì `repair` trả `null` vì **bất kỳ** lý do gì cũng làm bài trên xanh — tức một bài mù.
        val terms = listOf(VoiceTerm(listOf("cap"), VoiceTermKind.CONTROL, "x"))
        val fixed = VoicePhoneticMatch.repair(VoiceLexicon.tokenize("mở cốp"), terms)
        assertEquals(listOf("mo", "cap"), fixed?.map { it.norm }, "một ứng viên duy nhất ⇒ phải sửa")
    }

    // ── (5) Bảng giá là HẰNG, không phải số bay trên trời ────────────────────────────────────────

    @Test
    fun `bang gia giu dung con so da soat`() {
        assertEquals(0.30, VoicePhoneticConfusions.SUB_CONFUSED)
        assertEquals(0.15, VoicePhoneticConfusions.SUB_TONE)
        assertEquals(1.00, VoicePhoneticConfusions.SUB_OTHER)
        assertEquals(0.80, VoicePhoneticConfusions.GAP)
        assertEquals(0.40, VoicePhoneticConfusions.GAP_FILLER)
        assertEquals(0.35, VoicePhoneticMatch.ACCEPT)
        assertEquals(0.10, VoicePhoneticMatch.MARGIN)
    }

    @Test
    fun `gia mot phep thay dung theo bang`() {
        fun cost(a: String, b: String) = VoicePhoneticConfusions.cost(
            VoicePhoneticConfusions.syl(a), VoicePhoneticConfusions.syl(b),
        )
        // Trùng hệt sau khi bỏ dấu **và** cùng thanh ⇒ 0. Đây là chỗ *"đọc"*/*"độc"* rơi vào: bỏ dấu xong
        // chúng bằng nhau, nên ca ấy vốn đã chạy từ V1 mà không cần tầng nào cả.
        assertEquals(0.0, cost("đọc", "độc"))
        // Cùng chữ, khác thanh — hỏi ↔ ngã (giọng Nam gộp hai thanh này).
        assertEquals(VoicePhoneticConfusions.SUB_TONE, cost("mở", "mỡ"))
        // Cặp ĐÃ ĐO (owner nêu tên): `ô`↔`â` sau khi bỏ dấu là `o`↔`a`, quy luật cố ý KHÔNG nhận — nó đi
        // đường "đã quan sát" nên vẫn có giá rẻ.
        assertEquals(VoicePhoneticConfusions.SUB_CONFUSED, cost("cốp", "cấp"))
        // Quy luật: phụ âm cuối `t`↔`c` · phụ âm đầu `tr`↔`ch` · vần `ươ`↔`ơ`.
        assertEquals(VoicePhoneticConfusions.SUB_CONFUSED, cost("lọt", "lọc"))
        assertEquals(VoicePhoneticConfusions.SUB_CONFUSED, cost("chái", "trái"))
        assertEquals(VoicePhoneticConfusions.SUB_CONFUSED, cost("cơp", "cươp"))
        // ⚠ KHÔNG nhận: hai thành phần cùng lệch, hoặc một nguyên âm đơn khác hẳn. Nếu bài này đỏ thì
        // *"bật cái đó"* sẽ thành *"bật Cài đặt"* — đã đo trên nguyên mẫu host.
        assertEquals(VoicePhoneticConfusions.SUB_OTHER, cost("đó", "đặt"))
        assertEquals(VoicePhoneticConfusions.SUB_OTHER, cost("phim", "kính"))
        // `d`↔`r` vào bảng 2026-09-16 sau khi đo được HAI lần (ca `w09`). Cụm MỘT âm tiết vẫn phải đi qua
        // [nearMiss], nên cặp `dừng`/`rừng` còn phải có tên trong [OBSERVED] — bài dưới khoá đúng chỗ đó.
        assertEquals(VoicePhoneticConfusions.SUB_CONFUSED, cost("rừng", "dừng"))
        assertTrue(
            VoicePhoneticConfusions.nearMiss(
                VoicePhoneticConfusions.syl("rừng"), VoicePhoneticConfusions.syl("dừng"),
            ),
            "cụm một âm tiết `dừng` chỉ qua được cổng nếu cặp này nằm trong bảng ĐÃ ĐO",
        )
    }

    @Test
    fun `cum MOT am tiet chi nhan cap DA DO, khong nhan quy luat`() {
        fun near(a: String, b: String) = VoicePhoneticConfusions.nearMiss(
            VoicePhoneticConfusions.syl(a), VoicePhoneticConfusions.syl(b),
        )
        fun cost(a: String, b: String) = VoicePhoneticConfusions.cost(
            VoicePhoneticConfusions.syl(a), VoicePhoneticConfusions.syl(b),
        )
        // ⚠ *"vợ"* và *"gió"* lệch đúng một phụ âm đầu **có trong bảng** (`v`↔`gi`, giọng Nam gộp) nên giá của
        // chúng vẫn là 0,30 — quy luật không sai. Thứ chặn ca này là cổng [nearMiss]: từ vựng có hàng chục cụm
        // MỘT âm tiết (*"gió"*, *"pin"*, *"kính"*…), cho quy luật chạy tự do ở đó thì mọi tiếng đời thường đều
        // cướp được một nút. [ĐO nguyên mẫu host] *"gọi cho vợ"* → `Bật Gió`.
        assertEquals(VoicePhoneticConfusions.SUB_CONFUSED, cost("vợ", "gió"))
        assertTrue(!near("vợ", "gió"), "cụm một âm tiết KHÔNG được nhận theo quy luật")
        assertTrue(!near("đó", "gió"), "cụm một âm tiết KHÔNG được nhận theo quy luật")
        // …còn cặp ĐÃ ĐO thì vẫn qua, vì nó là phép đo chứ không phải suy luận.
        assertTrue(near("bên", "pin"))
        assertTrue(near("cấp", "cốp"))
    }

    @Test
    fun `tach am tiet dung dau va cuoi`() {
        // `gi` phải thắng `g`, `ngh` phải thắng `ng` — sai thứ tự là mọi quy luật phụ âm đầu lệch theo.
        assertEquals(VoicePhoneticConfusions.Parts("gi", "o", ""), VoicePhoneticConfusions.parts("gio"))
        assertEquals(VoicePhoneticConfusions.Parts("ngh", "i", ""), VoicePhoneticConfusions.parts("nghi"))
        assertEquals(VoicePhoneticConfusions.Parts("tr", "a", "ng"), VoicePhoneticConfusions.parts("trang"))
        assertEquals(VoicePhoneticConfusions.Parts("k", "i", "nh"), VoicePhoneticConfusions.parts("kinh"))
        // Vần không bao giờ được rỗng: `"ba"` là `b`+`a`, không phải `b`+``+`a`.
        assertEquals(VoicePhoneticConfusions.Parts("b", "a", ""), VoicePhoneticConfusions.parts("ba"))
    }

    @Test
    fun `moi cap da quan sat deu ghi ro cho da nhin thay`() {
        // Bảng [OBSERVED] là **bằng chứng**, không phải danh sách ý kiến (CLAUDE.md §2). Dòng nào không nói
        // được nó đến từ đâu thì lần sau không ai dám xoá, cũng không ai dám tin.
        VoicePhoneticConfusions.OBSERVED.forEach {
            assertTrue(it.note.length >= 5, "cặp ${it.right}/${it.heard} thiếu chỗ quan sát")
        }
        assertTrue(
            VoicePhoneticConfusions.OBSERVED.count { it.seen == VoicePhoneticConfusions.Seen.REC } >= 15,
            "phần lớn bảng phải đến từ BẢN THU THẬT, không phải corpus tổng hợp",
        )
    }
}
