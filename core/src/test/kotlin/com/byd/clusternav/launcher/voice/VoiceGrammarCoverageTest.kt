package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ActionMacros
import com.byd.clusternav.launcher.ControlDef
import com.byd.clusternav.launcher.ControlKind
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.EvidenceTier
import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.Strings
import com.byd.clusternav.launcher.TelemetryRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V1 · ĐỘ PHỦ — BÀI CANH **SINH TỪ BỘ ĐĂNG KÝ** ════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R7.
 *
 * ## Vì sao phải SINH chứ không chép 194 câu vào một bảng
 * Bảng chép tay chỉ chứng minh *"194 câu này chạy"*. Thứ cần chứng minh là *"**mọi** khả năng của xe đều gọi được
 * bằng lời"* — và câu đó chỉ đúng nếu phép kiểm **đếm lại từ chính bộ đăng ký** mỗi lần chạy. Thêm một nút vào
 * `ControlRegistry` mà từ vựng không phủ ⇒ bài này **đỏ ngay**, không phải chờ ai đó nhớ ra để thêm ca test. Đây
 * là cùng cơ chế `LangCoverageTest` dùng cho bản dịch, và cùng lý do.
 *
 * Số ca sinh ra hôm nay: 65 nút + 123 datum + 4 gói lệnh + 2 hành động launcher = **194**, cộng phần tiếng Anh.
 */
class VoiceGrammarCoverageTest {

    /**
     * Câu mẫu cho một nút, dựng từ **nhãn của chính nó** + động từ hợp với [ControlKind].
     *
     * Nút BẤM không có động từ nào tự nhiên trong tiếng Việt (*"bấm Lọc ngay"* không ai nói) ⇒ dùng chính cái tên
     * làm câu lệnh, đúng luật *"cả câu là TÊN của việc"* mà `VoiceIntentParser.headMatch` cài.
     */
    private fun sentenceFor(def: ControlDef): String = when (def.kind) {
        ControlKind.TOGGLE -> "bật ${def.label}"
        ControlKind.COVER -> "mở ${def.label}"
        ControlKind.BUTTON -> def.label
        ControlKind.SELECT -> "đặt ${def.label} ${def.args.firstOrNull().orEmpty()}"
        ControlKind.STEP -> "đặt ${def.label} ${def.value}"
    }

    // ══ 1 · MỌI NÚT gọi được bằng lời ══════════════════════════════════════════════════════════════════

    @Test
    fun `moi nut trong ControlRegistry deu co it nhat mot cau nhan dung`() {
        val misses = ArrayList<String>()
        ControlRegistry.ALL.forEach { def ->
            val s = sentenceFor(def)
            val got = VoiceIntentParser.parseOne(s)
            // So theo **nhãn**, không theo mã: hai nút trùng nhãn (vd nhãn ngắn dùng chung) thì câu dựng từ nhãn
            // KHÔNG phân biệt nổi chúng — đó là giới hạn của chính cái nhãn, không phải lỗi của bộ phân tích.
            val okId = (got as? VoiceIntent.Control)?.id
            val okLabel = okId?.let { ControlRegistry.byId(it)?.label }
            if (okLabel != def.label) misses.add("${def.id} · \"$s\" → $got")
        }
        assertTrue(misses.isEmpty(), "nút KHÔNG gọi được bằng lời (${misses.size}/${ControlRegistry.ALL.size}):\n" +
            misses.joinToString("\n"))
    }

    @Test
    fun `moi nut deu co dung gia tri mong doi theo ControlKind`() {
        ControlRegistry.ALL.forEach { def ->
            val got = VoiceIntentParser.parseOne(sentenceFor(def)) as? VoiceIntent.Control
            assertNotNull(got, "${def.id}: không ra Control")
            val want = when (def.kind) {
                ControlKind.TOGGLE, ControlKind.COVER -> 1
                ControlKind.BUTTON -> null
                ControlKind.SELECT -> 0
                ControlKind.STEP -> def.clamp(def.value)
            }
            // Chỉ so khi câu khớp ĐÚNG nút đó (nhãn trùng thì bài trên đã nói rõ giới hạn).
            if (got!!.id == def.id) assertEquals(want, got.value, "${def.id} · \"${sentenceFor(def)}\"")
        }
    }

    // ══ 2 · MỌI DATUM đọc được bằng lời ════════════════════════════════════════════════════════════════

    @Test
    fun `moi datum trong TelemetryRegistry deu doc duoc bang mot cau xem`() {
        val misses = ArrayList<String>()
        TelemetryRegistry.ALL.forEach { spec ->
            val s = "xem ${spec.label}"
            val got = VoiceIntentParser.parseOne(s)
            val gotLabel = (got as? VoiceIntent.Read)?.datumId?.let { TelemetryRegistry.byId(it)?.label }
            if (gotLabel != spec.label) misses.add("${spec.id} · \"$s\" → $got")
        }
        assertTrue(misses.isEmpty(), "datum KHÔNG đọc được bằng lời (${misses.size}/${TelemetryRegistry.ALL.size}):\n" +
            misses.joinToString("\n"))
    }

    // ══ 3 · GÓI LỆNH + HÀNH ĐỘNG LAUNCHER ══════════════════════════════════════════════════════════════

    @Test
    fun `moi goi lenh goi duoc bang chinh ten no`() = ActionMacros.ALL.forEach { m ->
        assertEquals(VoiceIntent.Macro(m.id), VoiceIntentParser.parseOne(m.label), "gói \"${m.label}\"")
    }

    @Test
    fun `moi hanh dong launcher goi duoc bang loi`() = LauncherActions.ALL.forEach { a ->
        assertEquals(VoiceIntent.Launcher(a.id), VoiceIntentParser.parseOne("mở ${a.label}"), "\"mở ${a.label}\"")
    }

    // ══ 4 · TIẾNG ANH CƠ BẢN ═══════════════════════════════════════════════════════════════════════════

    /**
     * Nhãn Anh là **dữ liệu nằm cạnh** nhãn Việt (`Localized.labelEn`), nên từ vựng sinh ra đã có sẵn cả hai thứ
     * tiếng. Bài này chứng minh điều đó thật sự chảy tới bộ phân tích, không phải chỉ có mặt trong danh sách.
     */
    @Test
    fun `nut co nhan tieng Anh deu goi duoc bang cau tieng Anh`() {
        val misses = ArrayList<String>()
        ControlRegistry.ALL.filter { !it.labelEn.isNullOrBlank() }.forEach { def ->
            val s = when (def.kind) {
                ControlKind.TOGGLE -> "turn on ${def.labelEn}"
                ControlKind.COVER -> "open ${def.labelEn}"
                ControlKind.BUTTON -> def.labelEn!!
                ControlKind.SELECT -> "set ${def.labelEn} ${def.argsEn.firstOrNull() ?: def.args.firstOrNull().orEmpty()}"
                ControlKind.STEP -> "set ${def.labelEn} ${def.value}"
            }
            val got = VoiceIntentParser.parseOne(s)
            if (!hitsControl(got, def.labelEn) && !hitsMacroNamed(got, s)) misses.add("${def.id} · \"$s\" → $got")
        }
        assertTrue(misses.isEmpty(), "nhãn Anh không gọi được (${misses.size}):\n" + misses.joinToString("\n"))
    }

    private fun hitsControl(got: VoiceIntent, labelEn: String?): Boolean =
        ControlRegistry.byId((got as? VoiceIntent.Control)?.id ?: "")?.labelEn == labelEn

    /**
     * Ca hợp lệ DUY NHẤT mà câu dựng từ nhãn nút lại ra một GÓI LỆNH: nhãn Anh `"All windows"` (`windows_all`) đứng
     * sau `"open"` tạo thành đúng tên gói `"Open all"` — và gói đó **đúng hơn**: nó gồm 4 nút **đã chạy thật trên
     * xe**, còn `windows_all` ở mức CHƯA KIỂM (xem KDoc `ActionMacros`). Luật *"cả câu là TÊN của việc"* chọn cái
     * tên dài hơn, tức chọn cái chắc ăn hơn. Chấp nhận, nhưng chỉ khi tên gói thật sự nằm ở đầu câu.
     */
    private fun hitsMacroNamed(got: VoiceIntent, sentence: String): Boolean {
        val m = ActionMacros.byId((got as? VoiceIntent.Macro)?.id ?: "") ?: return false
        val head = VoiceLexicon.deaccent(sentence).startsWith(VoiceLexicon.deaccent(m.labelEn ?: m.label))
        return head
    }

    // ══ 5 · CHỐT AN TOÀN CỦA CHÍNH TỪ VỰNG ═════════════════════════════════════════════════════════════

    /**
     * ⚠⚠ Bài **quan trọng nhất** tệp này. Bỏ dấu xong thì tiếng Việt đụng nhau rất nhiều: `"cái"` = `"cài"`,
     * `"của"` = `"cửa"`, `"thể"` = `"thế"`. Một từ đệm trùng **tiền tố** của một cụm trong từ vựng sẽ làm cụm đó
     * **không bao giờ khớp được nữa** — im lặng, không ai đỏ. (Bản đầu của `VoiceLexicon.FILLERS` có đúng ba từ
     * như vậy và nó nuốt mất *"cài đặt"*, *"cửa sổ trời"*, *"thể thao"*.)
     */
    @Test
    fun `khong tu dem nao la tien to cua mot cum trong tu vung`() {
        val terms = VoiceGrammar.terms()
        val bad = VoiceLexicon.FILLERS.filter { f -> terms.any { it.words.firstOrNull() == f } }
        assertTrue(bad.isEmpty(), "từ đệm nuốt mất đầu một cụm thật: $bad — bỏ khỏi VoiceLexicon.FILLERS")
    }

    @Test
    fun `moi cum trong tu vung deu khong rong va da chuan hoa`() {
        VoiceGrammar.terms(listOf("Mặc định"), listOf("YouTube")).forEach { t ->
            assertTrue(t.words.isNotEmpty(), "cụm rỗng cho ${t.id}")
            t.words.forEach { w ->
                assertEquals(VoiceLexicon.deaccent(w), w, "cụm của ${t.id} chưa chuẩn hoá: \"$w\"")
            }
        }
    }

    /** Từ vựng phải **sinh ra**, không chép tay: bỏ một bộ đăng ký ra khỏi [VoiceGrammar.terms] là bài này đỏ. */
    @Test
    fun `tu vung phu du bon bo dang ky`() {
        val terms = VoiceGrammar.terms()
        listOf(
            VoiceTermKind.CONTROL to ControlRegistry.ALL.size,
            VoiceTermKind.TELEMETRY to TelemetryRegistry.ALL.size,
            VoiceTermKind.MACRO to ActionMacros.ALL.size,
            VoiceTermKind.LAUNCHER to LauncherActions.ALL.size,
        ).forEach { (kind, n) ->
            assertEquals(n, terms.filter { it.kind == kind }.map { it.id }.distinct().size, "thiếu mã loại $kind")
        }
    }

    // ══ 6 · SỐ BẰNG CHỮ ════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `doc so bang chu tieng Viet va tieng Anh`() {
        fun n(s: String): Int? = VoiceLexicon.readNumber(VoiceLexicon.tokenize(s), 0)?.value
        assertEquals(0, n("không"))
        assertEquals(7, n("bảy"))
        assertEquals(10, n("mười"))
        assertEquals(15, n("mười lăm"))
        assertEquals(21, n("hai mươi mốt"))
        assertEquals(22, n("hai mươi hai"))
        assertEquals(24, n("hai mươi tư"))
        assertEquals(30, n("ba mươi"))
        assertEquals(33, n("ba mươi ba"))
        assertEquals(22, n("22"))
        assertEquals(80, n("80%"))
        assertEquals(3, n("three"))
        assertEquals(22, n("twenty two"))
        assertEquals(22, n("twenty-two"))
        assertEquals(VoiceLexicon.MAX, n("tối đa"))
        assertEquals(VoiceLexicon.MAX, n("hết cỡ"))
        assertEquals(VoiceLexicon.MIN, n("tối thiểu"))
        assertEquals(null, n("mèo"))

        // Lối nói RÚT GỌN (soát 2026-09-14): bỏ chữ "mươi". Thiếu bảng này thì "hai lăm" đọc ra 2, rồi `clamp`
        // kéo về `min` ⇒ máy làm SAI một việc và vẫn báo "✓" (xem KDoc `VoiceLexicon.VI_TENS_SHORT`).
        assertEquals(25, n("hai lăm"))
        assertEquals(24, n("hai tư"))
        assertEquals(31, n("ba mốt"))
        assertEquals(24, n("hăm bốn"))
        assertEquals(25, n("hăm lăm"))
        assertEquals(21, n("hăm mốt"))
        // "băm" (= ba mươi) cố ý KHÔNG nhận: bỏ dấu xong nó là "bam", trùng hệt **"bấm"** — một động từ ra lệnh.
        assertEquals(null, n("băm mốt"))
        // "hai linh" không phải một con số (nó là nửa của "hai linh năm") ⇒ chỉ đọc được "hai".
        assertEquals(2, n("hai linh"))
    }

    // ══ 7 · BẢNG AN TOÀN (R4) ══════════════════════════════════════════════════════════════════════════

    /**
     * ═══ V3 · R7 — MẶC ĐỊNH **KHÔNG HỎI GÌ CẢ** (owner chốt 2026-09-16) ══════════════════════════════
     *
     * Đây là bài canh của một **đổi hành vi**, không phải một bài canh bảng: tới 1.65 bốn dòng
     * [VoiceRiskTable.CONTROL_RULES] + gói kính + đổi hồ sơ + điểm đến mở **luôn** hỏi lại. [ĐO xe 2026-09-16]
     * cái giá thật: *"mở kính lái"* → nút gộp → hộp *"Hạ hết 4 kính?"* → 5,4 s chờ → người lái nói *"ừ"* → bị
     * bỏ → **huỷ, không nói gì**. Owner: *"cái nào nguy hiểm lái xe mới hỏi, chứ mở cửa hỏi làm gì"*.
     *
     * Thử làm nó ĐỎ: bỏ tham số `confirmIds` ở `VoiceRiskTable.of` (quay lại bảng cứng) ⇒ nửa đầu bài này đỏ.
     */
    @Test
    fun `mac dinh KHONG hoi gi ca — tap rong thi moi viec la NORMAL`() {
        listOf(
            VoiceIntent.Control("door", null),
            VoiceIntent.Control("lock", 0),
            VoiceIntent.Control("windows_all", 1),
            VoiceIntent.Control("cast", 0),
            VoiceIntent.Control("trunk", 1),
            VoiceIntent.Control("sunroof", 1),
            VoiceIntent.Macro("mac_win_open_all"),
            VoiceIntent.Profile("Vợ"),
            VoiceIntent.Nav("Bitexco"),
            VoiceIntent.Media(VoiceMediaOp.QUERY, "Diễm Xưa"),
        ).forEach { i ->
            assertEquals(VoiceRisk.NORMAL, VoiceRiskTable.of(i), "mặc định phải CHẠY LUÔN: $i")
        }
        // Đọc vẫn là SAFE (không đổi gì ngoài màn hình) — cổng an toàn không liên quan tới nó.
        assertEquals(VoiceRisk.SAFE, VoiceRiskTable.of(VoiceIntent.Read("soc")))
    }

    @Test
    fun `bat mot ma thi DUNG ma do hoi lai, cac ma khac khong`() {
        val only = setOf(VoiceRiskTable.PREFIX_CONTROL + "door")
        assertEquals(VoiceRisk.CONFIRM, VoiceRiskTable.of(VoiceIntent.Control("door", null), only))
        assertEquals(VoiceRisk.NORMAL, VoiceRiskTable.of(VoiceIntent.Control("lock", 0), only))
        assertEquals(VoiceRisk.NORMAL, VoiceRiskTable.of(VoiceIntent.Profile("Vợ"), only))
        // Giá trị KHÔNG vào mã: tích "Khoá xe" là tích cả hai chiều (xem KDoc [VoiceRiskTable.confirmId]).
        val lock = setOf(VoiceRiskTable.PREFIX_CONTROL + "lock")
        assertEquals(VoiceRisk.CONFIRM, VoiceRiskTable.of(VoiceIntent.Control("lock", 0), lock))
        assertEquals(VoiceRisk.CONFIRM, VoiceRiskTable.of(VoiceIntent.Control("lock", 1), lock))
        // Nút KHÔNG nằm trong bảng lý do thì không có mã ⇒ không bao giờ hỏi được, kể cả khi ai đó nhét mã lạ.
        assertEquals(null, VoiceRiskTable.confirmId(VoiceIntent.Control("readl", 1)))
        assertEquals(VoiceRisk.NORMAL, VoiceRiskTable.of(VoiceIntent.Control("readl", 1), setOf("control:readl")))
        // Sổ địa chỉ: tập ĐÓNG người dùng tự gõ ⇒ không có mã, không bật được (spec `kachi-voice-addresses` §4.2).
        assertEquals(null, VoiceRiskTable.confirmId(VoiceIntent.NavigateSaved("Nhà")))
    }

    /** Mỗi mã bày ra trong Cài đặt phải có NHÃN + LÝ DO đọc được — một ô tích trống nghĩa là một ô không ai tích. */
    @Test
    fun `moi ma hoi-duoc deu co nhan va ly do`() {
        val ids = VoiceRiskTable.askableIds()
        assertTrue(ids.isNotEmpty(), "tiền đề: danh sách việc hỏi-được không rỗng")
        assertEquals(ids.size, ids.distinct().size, "mã trùng ⇒ hai ô tích ghi đè nhau")
        ids.forEach { id ->
            val pair = VoiceRiskTable.askableLabel(id)
            assertTrue(pair != null, "thiếu nhãn cho mã $id")
            assertTrue(pair!!.first.isNotBlank() && pair.second.isNotBlank(), "nhãn/lý do rỗng cho $id")
        }
    }

    /** Mọi việc phải hỏi lại đều phải nói được **vì sao** — hộp xác nhận không được là một cú chạm trống nghĩa. */
    @Test
    fun `moi viec CONFIRM deu co ly do doc duoc`() {
        listOf(
            VoiceIntent.Control("door", null),
            VoiceIntent.Control("lock", 0),
            VoiceIntent.Control("windows_all", 1),
            VoiceIntent.Control("cast", 0),
            VoiceIntent.Macro("mac_win_open_all"),
            VoiceIntent.Profile("Vợ"),
        ).forEach { i ->
            assertTrue(!VoiceRiskTable.reason(i).isNullOrBlank(), "thiếu lý do cho $i")
        }
    }

    // ══ 8 · TỪ VỰNG DỰNG MỘT LẦN (soát 2026-09-14) ════════════════════════════════════════════════════

    /**
     * Phần tĩnh của từ vựng nay `by lazy` (không dựng lại ~600 cụm mỗi câu). Cái giá phải canh của mọi bộ nhớ đệm
     * là **rò rỉ giữa hai lần gọi**: danh sách hồ sơ/app là thứ THAY ĐỔI (đổi hồ sơ, cài/gỡ app), nên một lần gọi
     * cũ mà còn dính lại thì người dùng gọi được một app đã gỡ — hoặc tệ hơn, một hồ sơ đã xoá.
     */
    @Test
    fun `tu vung dung mot lan nhung phan dong khong ro ri giua hai lan goi`() {
        val a = VoiceGrammar.terms(profiles = listOf("Vợ"), apps = listOf("VTV Go"))
        val b = VoiceGrammar.terms(profiles = listOf("Bố"), apps = listOf("YouTube"))
        val c = VoiceGrammar.terms()

        fun ids(t: List<VoiceTerm>, k: VoiceTermKind) = t.filter { it.kind == k }.map { it.id }.toSet()
        assertEquals(setOf("Vợ"), ids(a, VoiceTermKind.PROFILE))
        assertEquals(setOf("Bố"), ids(b, VoiceTermKind.PROFILE))
        assertEquals(setOf("VTV Go"), ids(a, VoiceTermKind.APP))
        assertEquals(setOf("YouTube"), ids(b, VoiceTermKind.APP))
        assertTrue(ids(c, VoiceTermKind.PROFILE).isEmpty() && ids(c, VoiceTermKind.APP).isEmpty(),
            "gọi không tham số phải KHÔNG còn hồ sơ/app của lần gọi trước")

        // Phần tĩnh thì phải y hệt nhau ở cả ba lần — nếu không, cùng một câu sẽ hiểu khác nhau tuỳ lúc gọi.
        val static = { t: List<VoiceTerm> ->
            t.filter { it.kind != VoiceTermKind.PROFILE && it.kind != VoiceTermKind.APP }
        }
        assertEquals(static(c), static(a))
        assertEquals(static(c), static(b))
        assertTrue(c.zipWithNext().all { (x, y) -> x.words.size >= y.words.size }, "phải xếp DÀI trước NGẮN")
    }

    // ══ 9 · CÂU TRẢ LỜI (R5) ══════════════════════════════════════════════════════════════════════════

    /**
     * ⚠ 2026-09-21 · OWNER BỎ HẲN CHẤM + ĐUÔI "chưa kiểm trên xe". `CarCapabilities.needsBadge`/`ActionMacro.needsBadge`
     * nay luôn false ⇒ `VoiceReply.unverified` trả rỗng. Câu trả lời giọng nói KHÔNG còn nói "chưa kiểm" cho MỌI mục.
     */
    @Test
    fun `cau tra loi khong con noi chua kiem tren xe 2026-09-21`() {
        Strings.current = Lang.VI
        val unproven = ControlRegistry.ALL.first { it.tier != EvidenceTier.PROVEN }
        val proven = ControlRegistry.ALL.first { it.tier == EvidenceTier.PROVEN }
        assertTrue(!VoiceReply.done(VoiceIntent.Control(unproven.id, 1)).contains("chưa kiểm"), unproven.id)
        assertTrue(!VoiceReply.done(VoiceIntent.Control(proven.id, 1)).contains("chưa kiểm"), proven.id)
        assertTrue(!VoiceReply.confirmQuestion(VoiceIntent.Control("windows_all", 1)).contains("chưa kiểm"))
        assertTrue(!VoiceReply.done(VoiceIntent.Read("soc")).contains("chưa kiểm"))
        Strings.current = Lang.EN
        assertTrue(!VoiceReply.done(VoiceIntent.Control(unproven.id, 1)).contains("not yet checked"))
        Strings.current = Lang.VI
    }

    /** Huỷ ở hộp hỏi lại phải nói ra còn mấy vế không chạy — im lặng là để người ta tưởng nửa sau đã chạy. */
    @Test
    fun `cau huy noi ro con may viec khong chay`() {
        Strings.current = Lang.VI
        val i = VoiceIntent.Control("door", null)
        assertTrue(VoiceReply.cancelled(i, 0).contains("đã huỷ"))
        assertTrue(!VoiceReply.cancelled(i, 0).contains("không chạy"), "không có vế sau thì đừng doạ")
        assertTrue(VoiceReply.cancelled(i, 2).contains("2"))
        Strings.current = Lang.EN
        assertTrue(VoiceReply.cancelled(i, 2).contains("cancelled"))
        Strings.current = Lang.VI
    }

    /** Mọi mã nút/gói trong bảng an toàn phải TỒN TẠI — chống mục rữa khi ai đó đổi/xoá mã. */
    @Test
    fun `bang an toan khong tro toi ma da chet`() {
        VoiceRiskTable.CONTROL_RULES.forEach {
            assertNotNull(ControlRegistry.byId(it.controlId), "mã nút lạ trong bảng an toàn: ${it.controlId}")
        }
        VoiceRiskTable.MACRO_IDS.forEach {
            assertNotNull(ActionMacros.byId(it), "mã gói lạ trong bảng an toàn: $it")
        }
    }
}
