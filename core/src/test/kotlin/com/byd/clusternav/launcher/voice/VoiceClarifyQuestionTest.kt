package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.TelemetryRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ D1 · CÂU HỎI ĐI QUA **LƯỢT HỎI LẠI** VẪN KHÔNG ĐƯỢC THÀNH LỆNH GHI ══════════════════════════════════════
 *
 * Nguồn: log xe THẬT 2026-09-18 (`logs/20260918`, 53 phiên `VoiceWavProbe` dạng `.zip`), bản trên xe 1.76.
 *
 * ## ⚠⚠ Vì sao phải có tệp này, khi đã có `VoiceLogCases0918Test`
 * Tệp kia canh **lượt phân tích ĐẦU**. Nhưng đọc lại chính tệp nhật ký của ca nặng nhất
 * (`20260917-200206-017.json`) thì thấy lượt đầu **không** bắn lệnh nào:
 *
 * ```json
 * { "heard": "tất cả cửa đang khóa hay đang mở",
 *   "decision": "Control(sunroof=1)", "replies": ["✗ Bật Cửa sổ trời — xe không nhận lệnh"],
 *   "clarify": true, "follow_up": true }
 * ```
 *
 * Hai cờ cuối là chỗ mấu chốt: lượt đầu ra `NO_OBJECT`, Kachi **hỏi lại** *"Cửa nào …?"*, người lái đọc một cái
 * tên, và **chính lượt trả lời ấy** mới thành lệnh **mở cửa sổ trời** trên xe đang chạy. Nghĩa là mọi cổng đặt ở
 * lượt đầu — kể cả [VoiceQuestion.isChoice] — đều không đóng được đường này: câu trả lời *"cửa sổ trời"* đứng một
 * mình là một câu ra lệnh hoàn toàn hợp lệ, và nó **không còn mang dấu hiệu nào** của câu hỏi ban đầu.
 *
 * Tệp này đi hết chuỗi THẬT — `parse` → [VoiceClarify.ask] → [VoiceClarify.combine] → `parse` lần hai — đúng
 * đường mà `:app` đang chạy (`VoiceSessionTurns.kt:188`).
 */
class VoiceClarifyQuestionTest {

    private val terms = VoiceGrammar.terms()

    private fun one(s: String): VoiceIntent = VoiceIntentParser.parseOne(s)

    private fun ask(s: String): VoiceClarify.Ask? =
        (one(s) as? VoiceIntent.Unknown)?.let { VoiceClarify.ask(it, 0, terms) }

    /** Câu hỏi lại **bắt buộc phải có** — không có thì bài đỏ ngay tại chỗ, kèm ý định thật để đọc. */
    private fun mustAsk(s: String): VoiceClarify.Ask =
        requireNotNull(ask(s)) { "«$s» phải có câu hỏi lại, ý định thật: ${one(s)}" }

    /** Lượt hai như `:app` làm: ghép ngữ cảnh mang theo với câu trả lời, rồi phân tích lại. */
    private fun answer(s: String, said: String): VoiceIntent =
        one(VoiceClarify.combine(mustAsk(s).carry, said))

    // ══ Cổng chính ════════════════════════════════════════════════════════════════════════════════════

    /**
     * ⚠⚠ CA CỦA NHẬT KÝ: hỏi *"tất cả cửa đang khóa hay đang mở"* rồi trả lời một cái tên ⇒ **không lệnh ghi nào**.
     *
     * Bốn câu trả lời dưới đây cố ý gồm cả ca xấu nhất: người lái đọc đúng cái tên mà bản 1.76 đã biến thành lệnh
     * (*"cửa sổ trời"*), và ca người lái tự thêm động từ hành động (*"mở cửa sổ trời"*). Cả hai vẫn phải nằm trong
     * đường ĐỌC — chốt là [VoiceClarify.READ_VERB] mà [VoiceClarify.Ask.carry] mang sang.
     */
    @Test fun `cau hoi lua chon di qua luot hoi lai VAN khong ra lenh ghi`() {
        val q = "tất cả cửa đang khóa hay đang mở"
        listOf("cửa sổ trời", "cửa trước trái", "mở cửa sổ trời", "mở hết kính").forEach { said ->
            val got = answer(q, said)
            assertFalse(
                VoiceQuestion.writesToCar(got),
                "hỏi «$q» rồi trả lời «$said» ⇒ TUYỆT ĐỐI không được ghi vào xe, ra: $got",
            )
        }
        // …và câu trả lời ĐỌC ĐƯỢC thì phải ra đúng datum cửa, không phải một lời từ chối.
        assertEquals(VoiceIntent.Read("door_lf"), answer(q, "cửa trước trái"))
    }

    /**
     * Ngữ cảnh mang theo của một câu HỎI là một động từ ĐỌC — và nó phải là động từ **có thật trong bảng**.
     *
     * Chép một chữ không có trong [VoiceGrammar.VERBS] vào [VoiceClarify.READ_VERB] sẽ bịt cổng D1 **mà mọi bài
     * canh hành vi vẫn xanh** (câu ghép rơi vào `NO_VERB` = "không hiểu", nghe như máy chỉ hơi ngớ ngẩn). Nên phép
     * canh ở đây là máy đọc bảng, không phải mắt người.
     */
    @Test fun `READ_VERB phai la mot dong tu DOC cua bang ngu phap`() {
        val hit = VoiceGrammar.VERBS.firstOrNull { it.first == listOf(VoiceClarify.READ_VERB) }
        assertNotNull(hit, "«${VoiceClarify.READ_VERB}» không có trong VoiceGrammar.VERBS")
        assertTrue(VoiceGrammar.isRead(hit!!.second), "«${VoiceClarify.READ_VERB}» phải là động từ ĐỌC, ra: ${hit.second}")
    }

    /** Ba ca của [VoiceClarify.Ask.carry] — câu HỎI · câu có động từ · câu một từ. */
    @Test fun `ngu canh mang theo dung ba ca`() {
        assertEquals(listOf(VoiceClarify.READ_VERB), ask("tất cả cửa đang khóa hay đang mở")?.carry)
        // Câu RA LỆNH mang chính động từ của nó — và mang **cả cụm** (*"kiểm tra"*, không phải *"kiểm"*).
        assertEquals(listOf("kiểm tra"), ask("kiểm tra áp suất")?.carry)
        assertEquals(listOf("bật"), ask("bật đèn")?.carry)
        // Cả câu chỉ có một từ ⇒ không có gì để mang (bài `loc mot minh…` của VoiceClarifyTest khoá cùng điều này).
        assertEquals(emptyList<String>(), ask("lọc")?.carry)
    }

    /**
     * Câu HỎI thì danh sách lựa chọn phải là thứ **ĐỌC được**.
     *
     * Hỏi *"cửa đang khóa hay mở"* mà đưa ra nút *"Cửa sổ trời"* / *"Hạ hết kính"* là mời người lái đọc tên một
     * cái nút — tức mời đúng cái lệnh ghi mà cả tệp này sinh ra để chặn.
     */
    @Test fun `cau hoi thi chi dua ra thu doc duoc`() {
        val a = mustAsk("tất cả cửa đang khóa hay đang mở")
        val labels = TelemetryRegistry.ALL.mapNotNull { it.displayLabel }.toSet()
        a.question.substringAfter("— ").removeSuffix("?")
            .split(", hay ", ", ", " hay ")
            .forEach { assertTrue(it.trim() in labels, "«${it.trim()}» không phải mục ĐỌC — hỏi: ${a.question}") }
    }

    // ══ Hỏi lại ĐÚNG HỌ — ba câu về lốp trong log ═════════════════════════════════════════════════════

    /**
     * Ba câu hỏi về lốp trong log đều từng hỏi lại sai họ, vì bản cũ lấy **từ khớp đầu tiên** làm đầu họ:
     *  • *"chỉ số áp suất lốp"* → *"**Số** nào — Odo tổng hay Số VIN?"* (chữ *"số"* của cụm dẫn *"chỉ số"* thắng);
     *  • *"kiểm tra áp suất"* → *"Áp nào — **Áp cell cao, Áp cell thấp**, hay Áp lốp trước-trái?"* (hai lựa chọn
     *    đầu là điện áp cell PIN, cho một câu hỏi về LỐP).
     * Nay đầu họ và các thành viên xếp theo **mức ủng hộ của cả câu** (`VoiceClarify.support`).
     */
    @Test fun `cau hoi ve lop thi hoi lai ve lop, khong ve cell pin`() {
        listOf("kiểm tra áp suất", "chỉ số áp suất lốp", "áp suất lốp bên trái là bao nhiêu").forEach { q ->
            val a = mustAsk(q)
            assertTrue(a.question.startsWith("Áp nào —"), "«$q» ra: ${a.question}")
            assertTrue(a.question.contains("lốp"), "«$q» phải nêu tên lốp, ra: ${a.question}")
            assertFalse(a.question.contains("cell"), "«$q» không được nêu cell pin, ra: ${a.question}")
        }
        // …và câu nào nói rõ BÊN thì bên ấy phải lên trước.
        val left = mustAsk("áp suất lốp bên trái là bao nhiêu").question
        assertTrue(
            left.indexOf("sau-trái") in 1 until left.indexOf("trước-phải"),
            "nói «bên trái» thì hai lốp TRÁI phải đứng trước lốp phải, ra: $left",
        )
    }

    /** …và lượt trả lời của cả ba câu ấy ra một datum lốp THẬT, không phải *"không hiểu"*. */
    @Test fun `tra loi cau hoi ve lop thi doc dung datum lop`() {
        assertEquals(VoiceIntent.Read("tyre_p_fl"), answer("kiểm tra áp suất", "áp lốp trước trái"))
        assertEquals(VoiceIntent.Read("tyre_p_rr"), answer("chỉ số áp suất lốp", "áp lốp sau phải"))
        assertEquals(VoiceIntent.Read("tyre_p_rl"), answer("áp suất lốp bên trái là bao nhiêu", "áp lốp sau trái"))
    }

    /**
     * Bộ khung câu hỏi bị **trừ ra** trước khi đo mức ủng hộ — xem KDoc [VoiceQuestion.FRAME_WORDS].
     *
     * [ĐO xe 2026-09-18, `20260918-121223-214.json`] *"kính lái đang mở bao nhiêu"* là câu người lái nói, và kết
     * quả CUỐI trên xe là `Read(window_lf)` — *"Kính trước-trái: 0 %"* (nhật ký cũng ghi `clarify: true`, tức nó
     * đi qua một lượt hỏi lại và **đi đúng**). Bài này khoá đúng chuỗi ấy. Không trừ bộ khung ra thì hai chữ *"bao
     * nhiêu"* trùng cách nói *"đang chạy bao nhiêu"* của datum `speed` và câu hỏi lại đổi thành *"Đang nào — Tốc
     * độ hay Đèn đọc?"* — một họ không liên quan.
     */
    @Test fun `cum hoi ve kinh cu the doc thang khong keo sang ho khac`() {
        // 1.94 (owner 2026-09-22): "kính lái" nay là kính CỤ THỂ (control `win_lf`, đồng bộ datum `window_lf`)
        // ⇒ "kính lái đang mở bao nhiêu" đọc THẲNG độ mở kính lái, KHÔNG hỏi lại "kính nào" và KHÔNG kéo sang
        // họ khác (nhiệt độ/đèn). Trước 1.94 "kính" mơ hồ nên phải hỏi; nay tên vị trí đã rõ.
        assertEquals(VoiceIntent.Read("window_lf"), one("kính lái đang mở bao nhiêu"))
    }

    // ══ KHÔNG siết quá tay: câu RA LỆNH vẫn ra lệnh ══════════════════════════════════════════════════

    /**
     * Cổng D1 chỉ áp cho câu HỎI. Một câu ra lệnh hỏi lại rồi được trả lời thì **phải** làm việc ấy — đây đúng là
     * điều owner yêu cầu ở vòng R8 (*"hỏi lại cho tới khi hiểu rồi làm"*), nên siết nó là làm hỏng tính năng.
     */
    @Test fun `cau ra lenh hoi lai roi tra loi thi VAN lam`() {
        assertEquals(VoiceIntent.Control("readl", 1), answer("bật", "đèn đọc"))
        assertEquals(VoiceIntent.Control("pm25", 1), answer("lọc", "lọc bụi"))
        assertEquals("Bật gì?", ask("bật")?.question)
    }

    /**
     * Từ đầu câu **không phải động từ** thì không được đem đi hỏi *"&lt;nó&gt; gì?"*.
     *
     * [ĐO xe 2026-09-18] *"hev đi được bao nhiêu"* trước đây hỏi lại *"**Hev** gì?"*: máy lấy một danh từ làm động
     * từ. Bộ đăng ký **không có** datum nào cho tầm hoạt động chung của xe hybrid (chỉ có `ev_range_km` và
     * `fuel_range_km` riêng), nên câu đúng ở đây là một lời *"chưa rõ"* thật thà — không phải một câu đoán.
     */
    @Test fun `danh tu dau cau khong bi doi vai thanh dong tu`() {
        val a = mustAsk("hev đi được bao nhiêu")
        assertEquals(VoiceClarify.vague(), a.question)
        // ⚠ [SOÁT senior 2026-09-18 · P0] Trước đây bài này ghim `carry` = rỗng. Đổi thành [VoiceClarify.READ_VERB]
        // vì câu ấy **là** một câu hỏi (cụm *"bao nhiêu"*), và [ĐO off-car] chính dòng `Ask(vague(), emptyList())`
        // là chỗ *"cốp đã mở chưa"* lọt qua: carry rỗng ⇒ trả lời *"cửa sổ trời"* ra `Control(sunroof,1)`. Điều bài
        // này sinh ra để canh — *"không được hỏi «Hev gì?»"* — vẫn nguyên ở dòng trên; chỗ đổi là **chặt hơn**.
        assertEquals(listOf(VoiceClarify.READ_VERB), a.carry)
    }

    // ══ [SOÁT senior 2026-09-18 · P0] Hình dạng câu hỏi mà ba cổng đầu để HỞ ════════════════════════════

    /**
     * ⚠⚠ *"&lt;đối tượng&gt; đang &lt;trạng thái&gt; không/chưa"* — dạng hỏi có/không phổ biến nhất của tiếng Việt,
     * và [ĐO off-car lượt soát] nó **không** bị ba cổng đầu của [VoiceQuestion.isChoice] nhận ra (chỉ MỘT từ ngữ
     * cảnh ⇒ `context = 1 < 2`) ⇒ `Unknown(NO_VERB)` ⇒ hỏi lại với **carry rỗng** ⇒ câu trả lời đứng một mình:
     *
     * ```
     * «cốp đã mở chưa»           + «cửa sổ trời» ⇒ Control(sunroof,1)   ← mở cửa sổ trời trên xe đang chạy
     * «cửa sổ trời đang mở không» + «mở cốp»     ⇒ Control(trunk,1)
     * «xe đã khóa cửa chưa»      + «rời xe»      ⇒ Macro(mac_leave)
     * ```
     *
     * Tức **đúng tai nạn của nhật ký 1.76**, chỉ khác một hình dạng câu. Bài này khoá cả hai lượt.
     */
    @Test fun `cau hoi co-khong mot dau hieu KHONG duoc ra lenh ghi o ca hai luot`() {
        val asked = listOf(
            "cửa sổ trời đang mở không",
            "cốp đã mở chưa",
            "xe đã khóa cửa chưa",
            "điều hòa đang bật không",
            "kính hạ hết chưa",
            "hạ hết kính chưa",
        )
        val worstAnswers = listOf("cửa sổ trời", "mở cốp", "khóa xe", "rời xe", "mở hết kính", "sưởi ghế")
        asked.forEach { q ->
            assertFalse(VoiceQuestion.writesToCar(one(q)), "lượt 1 của «$q» ra: ${one(q)}")
            val u = one(q) as? VoiceIntent.Unknown ?: return@forEach
            val a = requireNotNull(VoiceClarify.ask(u, 0, terms)) { "«$q» phải có câu hỏi lại" }
            assertEquals(listOf(VoiceClarify.READ_VERB), a.carry, "«$q» phải mang động từ ĐỌC sang lượt hai")
            worstAnswers.forEach { said ->
                val got = one(VoiceClarify.combine(a.carry, said))
                assertFalse(VoiceQuestion.writesToCar(got), "hỏi «$q» + trả lời «$said» ⇒ $got")
            }
        }
    }

    /**
     * …và giá trị NHÌN THẤY ĐƯỢC của cổng (4): lượt 1 nay **trả lời** thay vì nói *"không hiểu"*.
     *
     * Ghim bằng `assertEquals` chứ không bằng *"không phải lệnh ghi"*: hai khẳng định kia vẫn xanh nếu cổng (4)
     * bị gỡ (câu rơi về `Unknown`, mà `Unknown` cũng không ghi gì) ⇒ chúng **không** canh được chính cổng ấy.
     * [ĐO lượt soát] đúng thế: phép thử phá gỡ cổng (4) lần đầu KHÔNG làm đỏ bài nào.
     */
    @Test fun `cong thu tu tra loi ngay o luot mot`() {
        assertEquals(VoiceIntent.Read("sunroof_state"), one("cửa sổ trời đang mở không"))
        assertEquals(VoiceIntent.Read("ac_on"), one("điều hòa đang bật không"))
        assertEquals(VoiceIntent.Read("sunroof_state"), one("đã mở cửa sổ trời chưa"))
    }

    /**
     * Dạng *"A hay B"* — [VoiceQuestion.isQuestion] **không** nhận (chỉ một từ ngữ cảnh), nên chỗ duy nhất đỡ nó
     * là [VoiceQuestion.looksAsked] ở lượt trả lời.
     *
     * ⚠ Bài này cũng canh một chi tiết đã cắn một lần: phép đo phải chạy trên bản **CHƯA lọc tiếng đệm**. Chữ
     * `hay` nằm trong [VoiceLexicon.FILLERS] (vai *"hãy"*), nên đo trên bản đã lọc thì dấu hiệu bị cắt trước khi
     * nhìn tới — [ĐO lượt soát] bản vá đầu của chính lượt này mắc đúng lỗi đó và carry ra rỗng.
     */
    @Test fun `dang A hay B mang dong tu DOC sang luot tra loi`() {
        listOf("kính hay cửa", "cửa hay kính", "áp lốp hay nhiệt lốp", "kính trước hay kính sau").forEach { q ->
            val u = one(q) as? VoiceIntent.Unknown ?: error("«$q» phải là Unknown, ra: ${one(q)}")
            val a = requireNotNull(VoiceClarify.ask(u, 0, terms)) { "«$q» phải có câu hỏi lại" }
            assertEquals(listOf(VoiceClarify.READ_VERB), a.carry, "«$q» phải mang động từ ĐỌC")
            listOf("cửa sổ trời", "mở cốp", "rời xe").forEach { said ->
                val got = one(VoiceClarify.combine(a.carry, said))
                assertFalse(VoiceQuestion.writesToCar(got), "hỏi «$q» + trả lời «$said» ⇒ $got")
            }
        }
    }

    /**
     * …và cổng (4) **không** được siết sang câu ra lệnh: một câu mở đầu bằng động từ HÀNH ĐỘNG vẫn là lệnh.
     *
     * Đây chính là ca mà cổng (3) của [VoiceQuestion.isChoice] sinh ra để giữ (*"bật đèn đọc không"*). Nới điều
     * kiện *"không có động từ hành động ở vị trí 0"* là làm câm những câu này.
     */
    @Test fun `cong thu tu khong duoc lam cam cau ra lenh`() {
        assertEquals(VoiceIntent.Control("readl", 1), one("bật đèn đọc không"))
        assertEquals(VoiceIntent.Control("readl", 1), one("bật đèn đọc"))
        assertEquals(VoiceIntent.Control("win_lf", 1), one("mở kính"))   // lượt D: cụm mơ hồ = kính LÁI
        // ⚠ RỦI RO CÒN LẠI, ghim để nó không tự đổi: câu mở đầu bằng động từ HÀNH ĐỘNG mà kết bằng *"chưa"* vẫn
        // là một LỆNH. Tiếng Việt thì *"mở cửa sổ trời chưa?"* nghiêng về câu hỏi, nhưng [ĐO log xe] mô hình có
        // thêm/rụng từ ở đuôi câu, nên coi nó là câu hỏi sẽ làm CÂM một lệnh nói đúng. Đổi chiều là quyết định
        // của owner — khi đổi thì sửa dòng này kèm lý do, đừng đổi lặng lẽ (`voice-review-done.md` [P2]).
        assertEquals(VoiceIntent.Control("sunroof", 1), one("mở cửa sổ trời chưa"))
    }
}
