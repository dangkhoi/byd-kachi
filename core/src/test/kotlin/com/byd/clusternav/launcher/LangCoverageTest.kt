package com.byd.clusternav.launcher

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ U5 · T2 — BÀI CANH NHÃN TIẾNG ANH ════════════════════════════════════════════════════════════════════════
 *
 * ## Bệnh nó chữa
 * Nhãn tiếng Anh là **dữ liệu gắn vào từng dòng registry** ([Strings] KDoc giải thích vì sao). Dữ liệu thì **quên
 * được**: thêm một datum mới mà không điền `labelEn` sẽ không làm gì đỏ, launcher vẫn chạy, và cái sai chỉ lộ ra
 * dưới dạng *"một ô nói tiếng Việt giữa màn tiếng Anh"* — mà **chỉ người dùng English gặp**, tức owner sẽ không
 * thấy. Bài này biến chuyện quên đó thành **đỏ off-car**.
 *
 * ## Vì sao ĐẾM TUYỆT ĐỐI, không chỉ `forEach { assertNotNull }`
 * Vòng lặp trên một danh sách chỉ chứng minh *"những gì đang có đều có nhãn"*. Nó **không** bắt được ca danh sách bị
 * co lại (ai đó bỏ một bộ đăng ký khỏi phép quét, hoặc [Localized] bị tháo khỏi một lớp) — lúc đó vòng lặp quét ít
 * hơn và vẫn xanh. Ghim số là cách duy nhất để *"quét thiếu"* cũng đỏ. Đây đúng bài học của dự án về **dấu xanh
 * giả**: thêm tệp MỚI thì đỏ đúng, còn thứ **mất đi** thì im lặng.
 *
 * ## Ba luật về CHẤT bản dịch (không chỉ "có hay không")
 *  1. **Không được trùng y nguyên nhãn Việt** — vì chép nguyên là cách "điền cho xong". Trừ [SAME_ON_PURPOSE]: ký
 *     hiệu ngành (VIN · ESP · PM2.5 · EV/HEV · LDW/LDP) thì dịch mới là sai (spec §6 OQ2).
 *  2. **Không được chứa dấu tiếng Việt** — bắt ca dịch nửa vời (*"Tyre pressure trước-trái"*).
 *  3. **Nhãn ngắn phải THẬT ngắn** — chip thanh trạng thái cao ~24dp; nhãn ngắn dài bằng nhãn đầy thì `shortEn` vô
 *     nghĩa và chữ bị cắt (đúng lỗi [ĐO] 2026-09-10: *"Áp lốp trước-t…"* × 2 không phân biệt được).
 *
 * ## ⚠ TỰ DỌN [Strings.current] — bắt buộc
 * [Strings.current] là `var` toàn cục. Bài nào đổi nó mà không dọn sẽ làm **bài chạy sau** đọc nhãn tiếng Anh trong
 * khi nó assert tiếng Việt ⇒ đỏ ở một tệp **không liên quan**, và chạy riêng lẻ thì lại xanh. Đó là loại lỗi rất tốn
 * thời gian để lần ra, nên [dọn] chạy sau MỌI bài ở đây (kể cả bài không đổi gì) và có một bài riêng chứng minh việc
 * đổi-rồi-trả-về không để lại vết.
 */
class LangCoverageTest {

    /** Chạy sau MỌI bài — kể cả bài không chạm [Strings.current], để không phải nhớ bài nào có chạm. */
    @AfterEach
    fun `dọn`() {
        Strings.current = Lang.VI
    }

    // ── 1 · ĐẾM TUYỆT ĐỐI: không mã nào thiếu nhãn EN ────────────────────────────────────────────

    @Test
    fun `moi datum co nhan EN, dung 123 dong`() {
        assertEquals(123, TelemetryRegistry.ALL.size, "số datum đổi ⇒ xem lại bản dịch trước khi ghim số mới")
        val missing = TelemetryRegistry.ALL.filter { it.labelEn.isNullOrBlank() }.map { it.id }
        assertTrue(missing.isEmpty(), "datum thiếu nhãn tiếng Anh: $missing")
    }

    @Test
    fun `moi nut co nhan EN, dung 64 nut`() {
        assertEquals(64, ControlRegistry.ALL.size, "số nút đổi ⇒ xem lại bản dịch trước khi ghim số mới")
        val missing = ControlRegistry.ALL.filter { it.labelEn.isNullOrBlank() }.map { it.id }
        assertTrue(missing.isEmpty(), "nút thiếu nhãn tiếng Anh: $missing")
    }

    @Test
    fun `moi nhom co nhan va dong phu EN, dung 12 nhom`() {
        assertEquals(12, CapabilityGroups.ALL.size)
        val missing = CapabilityGroups.ALL
            .filter { it.labelEn.isNullOrBlank() || it.subEn.isNullOrBlank() }.map { it.id }
        assertTrue(missing.isEmpty(), "nhóm thiếu labelEn/subEn: $missing")
    }

    @Test
    fun `moi widget co nhan EN, dung 9 widget`() {
        assertEquals(9, WidgetRegistry.ALL.size)
        val missing = WidgetRegistry.ALL.filter { it.labelEn.isNullOrBlank() }.map { it.id }
        assertTrue(missing.isEmpty(), "widget thiếu nhãn tiếng Anh: $missing")
    }

    @Test
    fun `moi goi lenh co nhan EN, dung 4 goi`() {
        assertEquals(4, ActionMacros.ALL.size)
        val missing = ActionMacros.ALL.filter { it.labelEn.isNullOrBlank() }.map { it.id }
        assertTrue(missing.isEmpty(), "gói lệnh thiếu nhãn tiếng Anh: $missing")
    }

    @Test
    fun `moi muc cai dat co nhan EN — 10 nhom va 56 muc`() {
        assertEquals(10, SettingsCatalog.GROUPS.size)
        // 56 = 20 (IA v1) + 36 mục dựng lại từ màn ClusterNav (IA v2 §4.3: nav 11 · cast 9 · keys 5 · car 5 thêm ·
        // system 6 thêm · about 1 thêm), trừ mục `clusternav_open` bị thay bằng `system_advanced_screen`.
        assertEquals(56, SettingsCatalog.ENTRIES.size)
        val badGroups = SettingsCatalog.GROUPS.filter { it.labelEn.isBlank() || it.subEn.isBlank() }.map { it.id }
        assertTrue(badGroups.isEmpty(), "nhóm cài đặt thiếu labelEn/subEn: $badGroups")
        val badEntries = SettingsCatalog.ENTRIES.filter { it.labelEn.isNullOrBlank() }.map { it.id }
        assertTrue(badEntries.isEmpty(), "mục cài đặt thiếu nhãn tiếng Anh: $badEntries")
    }

    @Test
    fun `cac enum mang nhan cung co ban EN`() {
        assertEquals(9, Domain.values().size)
        assertEquals(7, Quantity.values().size)
        assertEquals(4, TyreCorner.values().size)
        assertEquals(6, LauncherRequirements.ALL.size)
        Domain.values().forEach { assertTrue(it.labelEn.isNotBlank(), "Domain.${it.name} thiếu nhãn EN") }
        Quantity.values().forEach { assertTrue(it.labelEn.isNotBlank(), "Quantity.${it.name} thiếu nhãn EN") }
        TyreCorner.values().forEach {
            assertTrue(it.labelEn.isNotBlank(), "TyreCorner.${it.name} thiếu nhãn EN")
            assertTrue(it.shortLabelEn.isNotBlank(), "TyreCorner.${it.name} thiếu viết tắt EN")
        }
        LauncherRequirements.ALL.forEach {
            assertNotNull(it.labelEn, "điều kiện ${it.id} thiếu nhãn EN")
            assertNotNull(it.losesWhatIfMissingEn, "điều kiện ${it.id} thiếu câu 'mất gì' bằng EN")
            // Chỉ ca cần người dùng mới có việc-cần-làm; ca đó thì bản EN là bắt buộc.
            if (it.userAction != null) assertNotNull(it.userActionEn, "điều kiện ${it.id} thiếu việc-cần-làm EN")
        }
    }

    /**
     * Phép quét GỘP: mọi thứ mang [Localized] phải có nhãn EN, và **tổng số phải khớp**.
     *
     * Đây là bài bắt ca *"một bộ đăng ký lặng lẽ rơi khỏi tầm quét"*: sáu bài trên mỗi bài canh một bộ, nên bỏ hẳn
     * `Localized` khỏi một lớp sẽ làm lớp đó không còn ở đây mà **không bài nào đỏ** nếu không ghim tổng.
     */
    @Test
    fun `tong so nhan co ban EN dung 304`() {
        val all: List<Localized> = TelemetryRegistry.ALL + ControlRegistry.ALL + CapabilityGroups.ALL +
            WidgetRegistry.ALL + ActionMacros.ALL + SettingsCatalog.GROUPS + SettingsCatalog.ENTRIES +
            Domain.values().toList() + Quantity.values().toList() + TyreCorner.values().toList() +
            LauncherRequirements.ALL
        // 304 = 265 + 3 nhóm mới (nav · cast · keys — `bars` bù cho `clusternav` bị bỏ) + 36 mục mới của IA v2.
        assertEquals(304, all.size, "số nhãn đổi — thêm mã mới thì phải dịch, rồi mới ghim số mới")
        val missing = all.filter { it.labelEn.isNullOrBlank() }.map { it.label }
        assertTrue(missing.isEmpty(), "còn nhãn chưa có bản EN: $missing")
    }

    // ── 2 · CHẤT bản dịch ───────────────────────────────────────────────────────────────────────

    @Test
    fun `nhan EN khong trung y nguyen nhan VI`() {
        val copied = localizedRows()
            .filter { it.labelEn == it.label && it.label !in SAME_ON_PURPOSE }
            .map { it.label }
        assertTrue(
            copied.isEmpty(),
            "nhãn EN chép y nguyên nhãn VI (điền cho xong?) — nếu là ký hiệu ngành thì khai vào SAME_ON_PURPOSE " +
                "kèm lý do: $copied",
        )
    }

    @Test
    fun `moi muc trong danh sach cho phep trung deu co ly do, va deu dung toi`() {
        SAME_ON_PURPOSE.forEach { (term, why) ->
            assertTrue(why.isNotBlank(), "'$term' được phép trùng thì phải nói LÝ DO, không thì đây là chỗ làm im bài")
        }
        // Danh sách cho phép mà không còn ai dùng = rác tích lại, và nó nới lỏng bài canh cho lần sau.
        val everyString = localizedRows().flatMap { listOf(it.label, it.labelEn ?: "") } +
            ControlRegistry.ALL.flatMap { it.args + it.argsEn } +
            TelemetryRegistry.ALL.flatMap { listOfNotNull(it.short, it.shortEn) }
        val unused = SAME_ON_PURPOSE.keys.filterNot { it in everyString }
        assertTrue(unused.isEmpty(), "mục cho phép trùng không còn ai dùng ⇒ xoá đi: $unused")
    }

    @Test
    fun `nhan EN khong chua dau tieng Viet`() {
        val dirty = ArrayList<String>()
        localizedRows().forEach { row ->
            row.labelEn?.let { if (hasVietnameseMark(it)) dirty.add("${row.label} → $it") }
        }
        CapabilityGroups.ALL.forEach { g ->
            g.subEn?.let { if (hasVietnameseMark(it)) dirty.add("${g.id}.subEn → $it") }
        }
        SettingsCatalog.GROUPS.forEach { g ->
            if (hasVietnameseMark(g.subEn)) dirty.add("${g.id}.subEn → ${g.subEn}")
        }
        TelemetryRegistry.ALL.forEach { s ->
            s.shortEn?.let { if (hasVietnameseMark(it)) dirty.add("${s.id}.shortEn → $it") }
        }
        ControlRegistry.ALL.forEach { c ->
            c.argsEn.filter { hasVietnameseMark(it) }.forEach { dirty.add("${c.id}.argsEn → $it") }
        }
        LauncherRequirements.ALL.forEach { r ->
            listOfNotNull(r.losesWhatIfMissingEn, r.userActionEn)
                .filter { hasVietnameseMark(it) }.forEach { dirty.add("${r.id} → $it") }
        }
        TyreCorner.values().forEach {
            if (hasVietnameseMark(it.labelEn) || hasVietnameseMark(it.shortLabelEn)) dirty.add("TyreCorner.${it.name}")
        }
        assertTrue(dirty.isEmpty(), "bản EN còn dấu tiếng Việt (dịch nửa vời): $dirty")
    }

    @Test
    fun `lua chon cua nut co ban EN dung so phan tu`() {
        val withArgs = ControlRegistry.ALL.filter { it.args.isNotEmpty() }
        // 14 = 5 nút COVER kính/rèm (mỗi cái "Đóng"/"Mở") + 9 nút SELECT. ⚠ [ĐO] con số tôi ĐOÁN lúc viết bài này là
        // 11 và bài đỏ ngay — đúng việc nó sinh ra để làm, và là lời nhắc rằng đếm bằng mắt qua một tệp 355 dòng thì
        // sai. Giữ số đo, không giữ số đoán.
        assertEquals(14, withArgs.size, "số nút có lựa chọn đổi ⇒ xem lại bản dịch")
        val bad = withArgs.filter { it.argsEn.size != it.args.size }.map { "${it.id}(${it.args.size}≠${it.argsEn.size})" }
        assertTrue(bad.isEmpty(), "lựa chọn EN thiếu/lệch số phần tử — sẽ lùi về CẢ danh sách tiếng Việt: $bad")
    }

    @Test
    fun `nhan ngan EN phai that ngan`() {
        val tooLong = ArrayList<String>()
        TelemetryRegistry.ALL.forEach { s ->
            // (a) `shortEn` đã khai thì phải ngắn.
            s.shortEn?.let { if (it.length > SHORT_CAP) tooLong.add("${s.id}.shortEn='$it'(${it.length})") }
            // (b) Datum nào cần viết tắt ở tiếng Việt thì bản EN **thực dùng** (kể cả khi lùi về `labelEn`) cũng phải
            //     ngắn — không thì chip tiếng Anh bị cắt đúng chỗ chip tiếng Việt vừa được chữa.
            if (s.short != null) {
                val en = s.shortLabelIn(Lang.EN)
                if (en.length > SHORT_CAP) tooLong.add("${s.id} EN short='$en'(${en.length})")
            }
        }
        TyreCorner.values().forEach {
            if (it.shortLabelEn.length > 3) tooLong.add("TyreCorner.${it.name}='${it.shortLabelEn}'")
        }
        assertTrue(tooLong.isEmpty(), "nhãn ngắn EN dài quá $SHORT_CAP ký tự (chip ~24dp sẽ cắt chữ): $tooLong")
    }

    /**
     * Bản dịch KHÔNG được sinh ra cặp nhãn trùng MỚI.
     *
     * [ĐO] khi làm T2: ba nhãn `Đèn viền` (nhóm) / `Đèn viền cabin` (datum) / `Đèn viền cabin` (nút) — bản dịch đầu
     * của tôi cho **cả ba** thành `"Ambient lighting"`. Bản Việt chỉ trùng MỘT cặp (datum↔nút, và cặp đó đã có gợi ý
     * loại `· xem`/`· bấm`), nên tiếng Anh sinh ra một cặp trùng thứ hai **không ai gợi ý** — vì phép phát hiện trùng
     * chạy trên nhãn Việt (xem KDoc [CapabilityPick.displayLabel]). Hậu quả: hai ô chữ y hệt nhau, chỉ người dùng
     * English gặp.
     *
     * ## ⚠⚠ [ĐO] BÀI NÀY TỪNG KHÔNG BẮT ĐƯỢC CHÍNH CA NÓ SINH RA ĐỂ BẮT
     * Bản đầu hỏi *"trong nhóm trùng ở EN, có mục nào ĐÃ trùng ở VI không"* — nếu có thì tha cả nhóm. Dựng lại đúng
     * lỗi cũ (nhóm `g_ambient` mang nhãn của datum) thì bài **VẪN XANH**, vì hai mục kia đúng là đã trùng ở VI.
     * Phép đúng phải là **theo TỪNG CẶP**: trong một nhóm trùng ở EN thì **mọi** nhãn VI phải giống nhau — tức chúng
     * vốn đã là cùng một cặp trùng, không phải "có họ hàng với một cặp trùng". Đây là lời nhắc rằng thử-phá không chỉ
     * kiểm mã sản phẩm, nó kiểm cả bài canh.
     */
    @Test
    fun `ban dich khong sinh ra nhan trung MOI`() {
        val rows = CapabilityGroups.ALL.map { it.id to (it.label to it.labelEn) } +
            WidgetRegistry.ALL.map { it.id to (it.label to it.labelEn) } +
            TelemetryRegistry.ALL.map { it.id to (it.label to it.labelEn) } +
            ControlRegistry.ALL.map { it.id to (it.label to it.labelEn) } +
            ActionMacros.ALL.map { it.id to (it.label to it.labelEn) }
        val newlyColliding = rows
            .groupBy { it.second.second ?: it.second.first }         // gom theo nhãn EN thực dùng
            .filterValues { group -> group.size > 1 && group.map { it.second.first }.distinct().size > 1 }
            .map { (en, group) -> "$en ← ${group.map { it.first }} (VI: ${group.map { it.second.first }.distinct()})" }
        assertTrue(
            newlyColliding.isEmpty(),
            "bản dịch làm hai mục KHÁC NHAU thành cùng một chữ (tiếng Việt chúng khác nhau ⇒ không có gợi ý loại " +
                "nào phân biệt): $newlyColliding",
        )
    }

    // ── 3 · Cơ chế chọn ngôn ngữ ─────────────────────────────────────────────────────────────────

    @Test
    fun `t tra ve theo ngon ngu dang chon`() {
        Strings.current = Lang.VI
        assertEquals("xin chào", Strings.t("xin chào", "hello"))
        Strings.current = Lang.EN
        assertEquals("hello", Strings.t("xin chào", "hello"))
        // Dạng có tham số tường minh KHÔNG phụ thuộc trạng thái toàn cục (đây là dạng test nên dùng).
        assertEquals("xin chào", Strings.t("xin chào", "hello", Lang.VI))
    }

    @Test
    fun `pick tu lui ve tieng Viet khi chua dich`() {
        Strings.current = Lang.EN
        assertEquals("gốc", Strings.pick("gốc", null))
        assertEquals("gốc", Strings.pick("gốc", ""), "chuỗi rỗng = CHƯA dịch, không phải 'nhãn trống'")
        assertEquals("gốc", Strings.pick("gốc", "   "))
        assertEquals("root", Strings.pick("gốc", "root"))
        // Một dòng chưa dịch thì hiện tiếng Việt — KHÔNG ném, vì launcher trên xe không được sập vì một nhãn.
        val undone = TelemetrySpec("x", "Nhãn Việt", "", Domain.BODY, WidgetShape.VALUE, EvidenceTier.PROVEN, "k")
        assertEquals("Nhãn Việt", undone.displayLabel)
    }

    @Test
    fun `doi ngon ngu roi tra ve VI thi moi nhan y nhu cu`() {
        Strings.current = Lang.VI
        val before = snapshot()
        Strings.current = Lang.EN
        val english = snapshot()
        assertNotEquals(before, english)
        Strings.current = Lang.VI
        assertEquals(before, snapshot(), "đổi ngôn ngữ để lại vết ⇒ bài chạy sau sẽ đỏ ở tệp không liên quan")
    }

    @Test
    fun `nhan ngan tu lui theo bac shortEn roi labelEn`() {
        val full = TelemetryRegistry.byId("tyre_p_fl")!!
        assertEquals("Tyre FL", full.shortLabelIn(Lang.EN))
        assertEquals("Lốp TT", full.shortLabelIn(Lang.VI))
        // Có `labelEn` nhưng KHÔNG có `shortEn` ⇒ lùi về nhãn đầy tiếng Anh, chứ không rơi về tiếng Việt: một chip
        // hơi dài vẫn hơn một chip đột ngột đổi thứ tiếng.
        val noShortEn = TelemetryRegistry.byId("ev_range_km")!!
        assertEquals(null, noShortEn.shortEn)
        assertEquals("EV range", noShortEn.shortLabelIn(Lang.EN))
        // Không có gì tiếng Anh ⇒ lùi hẳn về nhãn ngắn tiếng Việt.
        val nothing = TelemetrySpec("x", "Nhãn", "", Domain.BODY, WidgetShape.VALUE, EvidenceTier.PROVEN, "k", short = "N")
        assertEquals("N", nothing.shortLabelIn(Lang.EN))
    }

    // ── 4 · Bề mặt THẬT đổi theo ngôn ngữ (không chỉ trường dữ liệu) ─────────────────────────────

    @Test
    fun `o nhom, chip, ket luan lop va goi lenh deu doi theo ngon ngu`() {
        val status = CarStatus(tyres = CarStatus.Tyres(pFlKpa = 150.0))

        Strings.current = Lang.VI
        assertEquals("Lốp", GroupBoard.of("g_tyres", status)!!.label)
        assertEquals("Lốp TT", GroupBoard.of("g_tyres", status)!!.cells.first().label)
        assertTrue(TyreBoard.verdict(TyreBoard.readings(status.tyres)).contains("bánh non"))
        assertEquals("Bật", TelemetryReadout.of("light_low_beam", CarStatus(lights = CarStatus.Lights(lowBeam = true)))!!.display)

        Strings.current = Lang.EN
        assertEquals("Tyres", GroupBoard.of("g_tyres", status)!!.label)
        assertEquals("Tyre FL", GroupBoard.of("g_tyres", status)!!.cells.first().label)
        assertTrue(TyreBoard.verdict(TyreBoard.readings(status.tyres)).contains("low"))
        assertEquals("On", TelemetryReadout.of("light_low_beam", CarStatus(lights = CarStatus.Lights(lowBeam = true)))!!.display)
        // Gói lệnh: câu báo cho người dùng gọi tên bước hỏng bằng nhãn EN.
        val res = MacroResult("mac_leave", listOf(MacroStepResult("win_lf", false)))
        assertEquals("Leaving the car: the car took no command", res.notice(ActionMacros.byId("mac_leave")!!.displayLabel))
        // Lựa chọn của nút SELECT cũng theo ngôn ngữ.
        assertEquals("Sport", ControlTileLogic.selectLabel(ControlRegistry.byId("drive_mode")!!, 2))
    }

    @Test
    fun `chip thanh tren doi theo ngon ngu`() {
        val status = CarStatus(climate = CarStatus.Climate(pm25Level = 1))
        val cfg = TopStripConfig(listOf(TopStripConfig.PM25, "tyre_p_fl"))

        Strings.current = Lang.VI
        val vi = TopStripChips.render(cfg, status)
        assertEquals("PM2.5 · Tốt", vi[0].text)
        assertTrue(vi[1].text.startsWith("Lốp TT · "))

        Strings.current = Lang.EN
        val en = TopStripChips.render(cfg, status)
        assertEquals("PM2.5 · Good", en[0].text)
        assertTrue(en[1].text.startsWith("Tyre FL · "), "chip phải dùng nhãn NGẮN tiếng Anh, thấy: ${en[1].text}")
    }

    @Test
    fun `dong phu cua nhom giu so dem tu du lieu o ca hai thu tieng`() {
        val g = CapabilityGroups.WINDOWS
        Strings.current = Lang.VI
        assertEquals("4 mục · 6 nút · phần trăm mở + mở/đóng từng kính", g.contentLine)
        Strings.current = Lang.EN
        assertEquals("4 items · 6 buttons · how far open, plus open/close each window", g.contentLine)
    }

    private companion object {

        /** Trần ký tự cho nhãn NGẮN tiếng Anh — chip thanh trạng thái cao ~24dp. */
        const val SHORT_CAP = 14

        /**
         * Nhãn/lựa chọn **được phép** giống nhau ở hai thứ tiếng → lý do.
         *
         * Mỗi mục là một quyết định phải giải thích được (cùng khuôn [SettingsCatalog.NOT_SETTINGS]): nếu danh sách
         * này chỉ là một tập chuỗi thì nó sẽ thành chỗ nhét mã vào cho bài canh im.
         */
        val SAME_ON_PURPOSE: Map<String, String> = mapOf(
            "PM2.5" to "ký hiệu ngành cho bụi mịn 2.5µm — dịch thành câu dài là sai chuẩn (spec §6 OQ2)",
            "ESP" to "electronic stability program — viết tắt in trên chính táp-lô xe",
            "EV / HEV" to "hai chế độ hệ truyền động, viết tắt ngành; xe hiện đúng chữ này",
            "EV" to "electric vehicle — lựa chọn của nút EV/HEV, không dịch",
            "HEV" to "hybrid electric vehicle — lựa chọn của nút EV/HEV, không dịch",
            "Auto" to "từ quốc tế, dùng y nguyên trong cả hai thứ tiếng ở chế độ đèn pha",
            "Eco" to "tên chế độ lái do xe đặt, không dịch",
            "LDW" to "lane departure warning — viết tắt ngành, dịch ra sẽ dài hơn cả ô nút",
            "LDP" to "lane departure prevention — cùng lý do LDW",
        )

        /** Mọi dòng có nhãn, ở đúng một chỗ để các bài không lệch phạm vi quét. */
        fun localizedRows(): List<Localized> =
            TelemetryRegistry.ALL + ControlRegistry.ALL + CapabilityGroups.ALL + WidgetRegistry.ALL +
                ActionMacros.ALL + SettingsCatalog.GROUPS + SettingsCatalog.ENTRIES +
                Domain.values().toList() + Quantity.values().toList() + TyreCorner.values().toList() +
                LauncherRequirements.ALL

        /**
         * Dấu tiếng Việt — chữ có mặt trong tiếng Việt mà **không** có trong tiếng Anh.
         *
         * Liệt kê tường minh thay vì dùng phép chuẩn hoá Unicode: bảng này đọc được, và nó cũng chính là bảng để
         * người sau thêm chữ nếu phát hiện sót. Không gồm dấu `–`/`·`/`°` — chúng là dấu câu/ký hiệu, dùng chung.
         */
        const val VIETNAMESE_MARKS =
            "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ" +
                "ÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ"

        fun hasVietnameseMark(s: String): Boolean = s.any { it in VIETNAMESE_MARKS }

        /** Ảnh chụp MỌI nhãn hiện ra — dùng để chứng minh đổi ngôn ngữ không để lại vết. */
        fun snapshot(): List<String> =
            localizedRows().map { it.displayLabel } +
                TelemetryRegistry.ALL.map { it.displayShortLabel } +
                ControlRegistry.ALL.flatMap { it.displayArgs } +
                CapabilityGroups.ALL.map { it.contentLine } +
                listOf(
                    CapabilityPicker.GROUPS_TITLE, CapabilityPicker.GROUPS_NOTE,
                    CapabilityPicker.SINGLES_TITLE, CapabilityPicker.HINT_PREFIX,
                ) +
                ThemeMode.values().map { it.label() } +
                DockEdge.values().map { it.label } +
                LayoutPreset.values().map { it.label } +
                ImageFit.values().map { it.label } +
                TyreStatus.values().mapNotNull { it.reason }

        fun assertNotEquals(a: Any?, b: Any?) =
            assertFalse(a == b, "hai bên phải khác nhau — nếu giống thì phép đo này không chứng minh gì")
    }
}
