package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ControlKind
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.CtlSafetyPolicy
import com.byd.clusternav.launcher.Lang
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 VOICE-BARE-NOUN-IMPLICIT-VERB (spec `kachi-293-voice.html` §10) — tên BỘ PHẬN CHUYỂN ĐỘNG nói trần không nhận động từ
 * ngầm "mở" ([VoiceBareCover]).
 *
 * [ĐO off-car 07/10, bộ phân tích thật] trước bản này: *"cốp"* ⇒ `Control(trunk, 1)` (mở cốp), *"tất cả kính"* ⇒ hạ cả bốn kính,
 * *"đóng kính lái và cửa sổ trời"* ⇒ đóng kính rồi **mở** nóc, và chuỗi mô hình in ở matrix *"ĐÓNG KÍNH TRƯỚC TRÁI VÀ KÍNH"*
 * ⇒ đóng rồi **mở** lại chính kính lái. Bài này khoá: (1) nói trần ⇒ hỏi lại; (2) nút bật/tắt điện–khí giữ nguyên động từ ngầm
 * (quyết định coordinator — thay đổi tối thiểu); (3) mọi câu CÓ động từ giữ nguyên; (4) câu ghép mượn động từ của vế trước;
 * (5) câu hỏi lại *"Mở hay đóng …?"* hội tụ sau MỘT câu trả lời.
 */
class VoiceBareCoverTest {

    private fun all(s: String) = VoiceIntentParser.parse(s)
    private fun one(s: String) = all(s).single()
    private fun noVerb(s: String) = VoiceIntent.Unknown(VoiceUnknownReason.NO_VERB, s)

    /** Tên bộ phận chuyển động nói trần ⇒ hỏi lại — ba ca coordinator nêu + dạng chữ HOA mô hình in + lời lịch sự. */
    @Test
    fun `ten bo phan chuyen dong tran KHONG thanh lenh`() {
        listOf(
            "kính trước trái", "cửa sổ trời", "cốp", "KÍNH TRƯỚC TRÁI", "CỬA SỔ TRỜI", "CỐP",
            "kính", "cửa sổ", "nóc xe", "cốp sau", "cửa hậu", "rèm che nắng", "bốn kính", "hết kính", "50% kính lái",
            "nửa kính lái", "cốp đi", "làm ơn cốp", "cửa sổ trời giúp tôi",
        ).forEach { s -> assertEquals(noVerb(s), one(s), "«$s»") }
    }

    /** Nút bật/tắt điện–khí (TOGGLE · SELECT · BUTTON không chuyển động) GIỮ động từ ngầm bật — kể cả nhãn có chữ "kính". */
    @Test
    fun `nut bat tat dien khi GIU nguyen dong tu ngam`() {
        listOf(
            "điều hòa" to VoiceIntent.Control("ac_auto", 1),
            "đèn đọc" to VoiceIntent.Control("readl", 1),
            "sưởi ghế" to VoiceIntent.Control("seath", 1),
            "khóa trẻ em" to VoiceIntent.Control("child_lock", 1),
            "sấy kính trước" to VoiceIntent.Control("defrost", 1),
            "lọc bụi" to VoiceIntent.Control("pm25", 1),
        ).forEach { (s, want) -> assertEquals(want, one(s), "«$s»") }
    }

    /**
     * Quét bằng máy MỌI cách gọi (nhãn · nhãn ngắn · đồng nghĩa) của MỌI bộ phận chuyển động — không bảng tay: thêm một cách nói ở
     * [VoiceSynonyms] là tự vào bài. Bỏ ra đúng cụm MỞ ĐẦU bằng một cụm động từ (*"mở nửa kính lái"* · *"đóng tất cả kính"* —
     * động từ thật, bài sau khoá; *"tat ca kinh"* không dấu — không dữ liệu để phân biệt, bài đồng hình khoá).
     */
    @Test
    fun `moi cach goi cua moi bo phan chuyen dong deu hoi lai`() {
        val names = VoiceGrammar.terms()
            .filter { it.kind == VoiceTermKind.CONTROL && VoiceBareCover.moves(it.id) }
            .filterNot { t -> VoiceGrammar.VERBS.any { (w, _) -> t.words.size > w.size && t.words.take(w.size) == w } }
            .map { it.words.joinToString(" ") }
            .distinct()
        assertTrue(names.size >= 100, "quét quá ít cách gọi (${names.size}) — bộ lọc hỏng?")
        names.forEach { s -> assertEquals(noVerb(s), one(s), "«$s»") }
    }

    /** Tập "chuyển động" đọc từ dữ liệu sẵn có của từng nút: COVER ∪ MOVES_SLOWLY, và nằm trọn trong tập cần xác nhận. */
    @Test
    fun `tap bo phan chuyen dong doc tu du lieu san co cua tung nut`() {
        val moving = ControlRegistry.ALL.filter { VoiceBareCover.moves(it.id) }.map { it.id }.toSet()
        ControlRegistry.ALL.forEach { d ->
            assertEquals(d.kind == ControlKind.COVER || CtlSafetyPolicy.movesSlowly(d.id), VoiceBareCover.moves(d.id), d.id)
        }
        assertTrue(ControlRegistry.ALL.filter { it.kind == ControlKind.COVER }.all { it.id in moving }, "COVER phải ⊆ tập")
        assertTrue(moving.all { CtlSafetyPolicy.needsConfirm(it) }, "tập chuyển động phải ⊆ CONFIRM_REQUIRED: $moving")
        assertTrue(moving.containsAll(listOf("trunk", "sunroof", "sunshade", "win_lf", "windows_all")), "$moving")
        listOf("child_lock", "readl", "ac_auto", "seath", "defrost", "pm25").forEach { assertFalse(VoiceBareCover.moves(it), it) }
    }

    /** Câu CÓ động từ (kể cả động từ nằm trong tên nút, chữ HOA, động từ hướng kính) KHÔNG đổi một ý định nào. */
    @Test
    fun `co dong tu thi khong doi`() {
        listOf(
            "mở cốp" to VoiceIntent.Control("trunk", 1),
            "MỞ CỐP" to VoiceIntent.Control("trunk", 1),
            "đóng cốp" to VoiceIntent.Control("trunk", 0),
            "hạ cốp" to VoiceIntent.Control("trunk", 0),
            "mở cửa sổ trời" to VoiceIntent.Control("sunroof", 1),
            "đóng cửa sổ trời" to VoiceIntent.Control("sunroof", 0),
            "mở kính trước trái" to VoiceIntent.Control("win_lf", 1),
            "đóng kính trước trái" to VoiceIntent.Control("win_lf", 0),
            "hạ kính lái" to VoiceIntent.Control("win_lf", 1),
            "mở rèm che nắng" to VoiceIntent.Control("sunshade", 1),
            "mở nửa kính lái" to VoiceIntent.Control("win_half_lf", 1),
            "mở 4 kính" to VoiceIntent.Control("windows_all", 1),
            "đóng tất cả kính" to VoiceIntent.Control("windows_close_all", null),
            "mở hết kính" to VoiceIntent.Macro("mac_win_open_all"),
            "đóng hết kính" to VoiceIntent.Macro("mac_win_close_all"),
        ).forEach { (s, want) -> assertEquals(want, one(s), "«$s»") }
    }

    /**
     * *"tất cả"* bỏ dấu = *"tắt cả"*: chữ CÓ DẤU là dữ liệu (luật [VoiceHomograph]) ⇒ *"tất cả kính"* là câu KHÔNG động từ. Chữ
     * không dấu thì không có gì để phân biệt ⇒ hành vi cũ, từng byte.
     */
    @Test
    fun `tat ca kinh - dau thanh la du lieu`() {
        listOf("tất cả kính", "TẤT CẢ KÍNH", "tất cả cửa sổ", "tất cả cửa kính", "tất cả kiếng", "tất cả kính lên").forEach { s ->
            assertEquals(noVerb(s), one(s), "«$s»")
        }
        assertEquals(VoiceIntent.Control("windows_all", 1), one("tat ca kinh"), "không dấu ⇒ y như trước")
        assertEquals(VoiceIntent.Control("windows_all", 1), one("mở tất cả kính"))
        assertEquals(VoiceIntent.Control("windows_close_all", null), one("ĐÓNG TẤT CẢ KÍNH"))
    }

    /** Câu ghép: vế chỉ là tên bộ phận mượn động từ HÀNH ĐỘNG của vế đứng trước gần nhất. */
    @Test
    fun `cau ghep muon dong tu cua ve truoc`() {
        fun c(id: String, v: Int) = VoiceIntent.Control(id, v)
        listOf(
            // Giữ nguyên (đã đúng trước bản này).
            "mở kính lái và cửa sổ trời" to listOf(c("win_lf", 1), c("sunroof", 1)),
            "bật điều hòa và cốp" to listOf(c("ac_auto", 1), c("trunk", 1)),
            "mở kính lái và kính phụ và cốp" to listOf(c("win_lf", 1), c("win_rf", 1), c("trunk", 1)),
            "mở kính lái và đèn đọc và cốp" to listOf(c("win_lf", 1), c("readl", 1), c("trunk", 1)),
            // *"tất"* mang dấu không phải động từ ⇒ vế ấy không cắt mạch mượn của vế sau.
            "mở kính lái và tất cả kính và cốp" to listOf(c("win_lf", 1), c("windows_all", 1), c("trunk", 1)),
            "mở nhạc và cửa sổ trời" to listOf(VoiceIntent.Media(VoiceMediaOp.PLAY), c("sunroof", 1)),
            // Đổi: trước đây vế sau bị MỞ dù người lái nói đóng/tắt/hạ.
            "đóng kính lái và cửa sổ trời" to listOf(c("win_lf", 0), c("sunroof", 0)),
            "tắt điều hòa và cửa sổ trời" to listOf(c("ac_auto", 0), c("sunroof", 0)),
            "đóng cốp rồi kính lái" to listOf(c("trunk", 0), c("win_lf", 0)),
            "đóng kính lái và kính phụ và cốp" to listOf(c("win_lf", 0), c("win_rf", 0), c("trunk", 0)),
            "hạ kính lái và cốp" to listOf(c("win_lf", 1), c("trunk", 0)),
            "tắt đèn đọc và TẤT CẢ KÍNH" to listOf(c("readl", 0), c("windows_all", 0)),
            // Động từ + *"hết kính"* thành TÊN gói lệnh — vẫn là lệnh lên bộ phận chuyển động (mở: tương đương nút gộp cũ).
            "mở kính lái và hết kính" to listOf(c("win_lf", 1), VoiceIntent.Macro("mac_win_open_all")),
            "đóng kính lái và hết kính" to listOf(c("win_lf", 0), VoiceIntent.Macro("mac_win_close_all")),
            // Chuỗi THẬT mô hình in ở matrix6 (tail-none, w05 «đóng kính trước trái» + đuôi im lặng): trước ⇒ đóng rồi MỞ lại.
            "ĐÓNG KÍNH TRƯỚC TRÁI VÀ KÍNH" to listOf(c("win_lf", 0), c("win_lf", 0)),
            // 2.93 VOICE-CLAUSE-VERB-ELLIPSIS: nút bật/tắt cũng mượn động từ họ bật/tắt (khoá chi tiết ở VoiceClauseEllipsisTest).
            "tắt điều hòa và đèn đọc" to listOf(c("ac_auto", 0), c("readl", 0)),
        ).forEach { (s, want) -> assertEquals(want, all(s), "«$s»") }
    }

    /** Không mượn được động từ mở/đóng ⇒ vế bộ phận thành dòng "đã bỏ qua", các vế kia vẫn chạy; toàn tên trần ⇒ hỏi lại. */
    @Test
    fun `cau ghep khong muon duoc thi bo ve bo phan`() {
        fun dropped(s: String) = VoiceIntent.Unknown(VoiceUnknownReason.DROPPED_CLAUSE, s)
        listOf(
            "phát nhạc và cốp" to listOf(VoiceIntent.Media(VoiceMediaOp.PLAY), dropped("cốp")),
            "xem pin và cốp" to listOf(VoiceIntent.Read("soc"), dropped("cốp")),
            "dừng nhạc và cửa sổ trời" to listOf(VoiceIntent.Media(VoiceMediaOp.PAUSE), dropped("cửa sổ trời")),
            "tăng nhiệt độ và cửa sổ trời" to listOf(VoiceIntent.Control("temp", null, 1), dropped("cửa sổ trời")),
            "lấy gió ngoài và cốp" to listOf(VoiceIntent.Control("recirc", 0), dropped("cốp")),
            "đèn đọc và cốp" to listOf(VoiceIntent.Control("readl", 1), dropped("cốp")),
            "cốp và kính lái" to listOf(noVerb("cốp và kính lái")),
        ).forEach { (s, want) -> assertEquals(want, all(s), "«$s»") }
    }

    /** Câu hỏi lại *"Mở hay đóng <nhãn>?"* + câu trả lời một chữ ⇒ ĐÚNG lệnh của đúng bộ phận, sau MỘT lượt. */
    @Test
    fun `hoi lai Mo hay dong roi tra loi mot chu`() {
        fun ask(s: String, lang: Lang = Lang.VI) = VoiceClarify.ask(one(s) as VoiceIntent.Unknown, 0, lang = lang)
        val trunk = requireNotNull(ask("cốp")) { "«cốp» phải có câu hỏi lại" }
        assertEquals("Mở hay đóng Cốp sau?", trunk.question)
        assertEquals(listOf("cốp"), trunk.carry)
        assertEquals("Open or close Tailgate?", ask("cốp", Lang.EN)!!.question)
        assertEquals(listOf("cốp"), ask("cốp đi")!!.carry, "lời lịch sự không vào phần mang theo")
        val win = ask("KÍNH TRƯỚC TRÁI")!!
        assertEquals("Mở hay đóng Kính lái?", win.question)
        fun answer(a: VoiceClarify.Ask, said: String) = all(VoiceClarify.combine(a.carry, said))
        listOf(
            "mở" to VoiceIntent.Control("win_lf", 1),
            "MỞ" to VoiceIntent.Control("win_lf", 1),
            "đóng" to VoiceIntent.Control("win_lf", 0),
            "đóng lại" to VoiceIntent.Control("win_lf", 0),
            "mở hết" to VoiceIntent.Control("win_lf", 1),   // KHÔNG thành gói mở cả bốn kính
            "hạ" to VoiceIntent.Control("win_lf", 1),
        ).forEach { (said, want) -> assertEquals(listOf(want), answer(win, said), "«$said»") }
        assertEquals(listOf(VoiceIntent.Control("trunk", 0)), answer(trunk, "đóng"))
        val all4 = ask("tất cả kính")!!
        assertEquals("Mở hay đóng 4 kính?", all4.question)
        assertEquals(listOf(VoiceIntent.Control("windows_close_all", null)), answer(all4, "đóng"))
        assertEquals(listOf(VoiceIntent.Control("windows_all", 1)), answer(all4, "mở"))
    }

    /**
     * Senior review wave 2 [P1] — lời KHÔNG phải câu trả lời (*"hả?"* = chưa nghe rõ) không bao giờ thành lệnh. [ĐO off-car 07/10]
     * trước bản vá: bỏ dấu thì *"hả"* = *"hạ"* (từ mở đầu lệnh hướng kính) ⇒ ghép *"hả kính lái"* = MỞ kính lái, *"hả tất cả kính"*
     * = hạ cả bốn kính, *"hả cốp"* = đóng cốp — xe không hỏi xác nhận mặc định cho kính/cốp (`VoiceRiskTable`).
     */
    @Test
    fun `tra loi ha khong thanh lenh`() {
        fun ask(s: String) = requireNotNull(VoiceClarify.ask(one(s) as VoiceIntent.Unknown, 0, lang = Lang.VI)) { "«$s»" }
        listOf("kính lái", "tất cả kính", "cốp", "cửa sổ trời").forEach { name ->
            val a = ask(name)
            listOf("hả", "HẢ", "hà", "há", "nắng").forEach { said ->
                val combined = VoiceClarify.combine(a.carry, said)
                assertEquals(said, combined, "«$name» + «$said» không được dán tên bộ phận")
                assertTrue(all(combined).none { it is VoiceIntent.Control || it is VoiceIntent.Macro }, "«$name» + «$said»")
            }
        }
        // Đúng chữ thì vẫn là động từ; chữ không dấu ⇒ không dữ liệu ⇒ như cũ.
        assertEquals(listOf(VoiceIntent.Control("win_lf", 1)), all(VoiceClarify.combine(ask("kính lái").carry, "hạ")))
        assertEquals(listOf(VoiceIntent.Control("win_lf", 1)), all(VoiceClarify.combine(ask("kính lái").carry, "ha")))
    }

    /** Câu trả lời là một câu của riêng nó / lời thôi ⇒ KHÔNG dán tên bộ phận vào (không thành *"mở cốp …"*). */
    @Test
    fun `tra loi la cau rieng thi giu nguyen`() {
        val carry = listOf("cốp")
        assertEquals("bật điều hòa", VoiceClarify.combine(carry, "bật điều hòa"))
        assertEquals("mở youtube", VoiceClarify.combine(carry, "mở youtube"))
        assertEquals("mở cốp", VoiceClarify.combine(carry, "mở cốp"))
        assertEquals(listOf(VoiceIntent.EndSession), all(VoiceClarify.combine(carry, "thôi")))
        assertFalse(all(VoiceClarify.combine(carry, "bật điều hòa")).any { it is VoiceIntent.Control && it.id == "trunk" })
    }

    /** Mọi carry KHÁC (động từ · động từ ĐỌC của cổng D1 · vế hồ sơ) ghép đúng như trước. */
    @Test
    fun `carry khac ghep nhu cu`() {
        assertEquals("mở kính lái", VoiceClarify.combine(listOf("mở"), "kính lái"))
        assertEquals("xem cửa sổ trời", VoiceClarify.combine(listOf(VoiceClarify.READ_VERB), "cửa sổ trời"))
        assertEquals("xem mở cốp", VoiceClarify.combine(listOf(VoiceClarify.READ_VERB), "mở cốp"))
        assertEquals("chuyển sang hồ sơ Vợ", VoiceClarify.combine(listOf("chuyển", "sang", "hồ", "sơ"), "Vợ"))
    }

    /** Câu hỏi / câu có đuôi / lý do khác KHÔNG đi đường *"Mở hay đóng"* — câu hỏi lại chung giữ nguyên. */
    @Test
    fun `chi cau ten tran moi hoi Mo hay dong`() {
        listOf("cốp xe bẩn quá", "kính bẩn quá", "cốp đã mở chưa", "cốp và kính lái").forEach { s ->
            val u = VoiceIntentParser.parse(s).single() as? VoiceIntent.Unknown ?: return@forEach
            val q = VoiceClarify.ask(u, 0, lang = Lang.VI)?.question.orEmpty()
            assertFalse(q.startsWith("Mở hay đóng"), "«$s» ⇒ «$q»")
        }
        assertEquals(null, VoiceBareCover.ask("mở cốp", VoiceGrammar.terms(), Lang.VI), "có động từ ⇒ không phải ca này")
        assertEquals(null, VoiceBareCover.ask("đèn đọc", VoiceGrammar.terms(), Lang.VI), "nút bật/tắt ⇒ không phải ca này")
    }
}
