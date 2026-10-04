package com.byd.clusternav.launcher

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.88 · CHIP ÁP SUẤT LỐP — chữ · đoạn màu · câu trình đọc màn hình (spec `kachi-288-tyre-car-state` R3/R4/R9) ═══
 *
 * Mẫu owner 04/10: *"2.4 2.4 2.6 2.6 kiểu vậy, cho 4 bánh, cái nào cảnh báo thì vàng, đỏ, bình thường thì xanh lá như
 * màu range lái"*. Số đi qua lớp đơn vị của hồ sơ, KHÔNG in chữ đơn vị; TT TP · ST SP, khe giữa hai trục rộng gấp đôi.
 */
class TyreChipTest {

    @AfterEach fun resetLang() { Strings.current = Lang.VI }

    private val bar = UnitPrefs.DEFAULT
    private val kpa = UnitPrefs.DEFAULT.with(Quantity.PRESSURE, "kPa")
    private val psi = UnitPrefs.DEFAULT.with(Quantity.PRESSURE, "psi")

    /** 2.4 2.4 · 2.6 2.6 bar, cụm đồng hồ nói cả bốn bình thường (trắng). */
    private val allOk = CarStatus.Tyres(
        pFlKpa = 240.0, pFrKpa = 240.0, pRlKpa = 260.0, pRrKpa = 260.0,
        cFl = TyreJudge.COLOUR_WHITE, cFr = TyreJudge.COLOUR_WHITE, cRl = TyreJudge.COLOUR_WHITE, cRr = TyreJudge.COLOUR_WHITE,
    )

    private fun chip(t: CarStatus.Tyres, units: UnitPrefs = bar, labels: Boolean = true): ChipView =
        TopStripChips.render(TopStripConfig(listOf(TopStripConfig.TYRES), showLabels = labels), CarStatus(tyres = t), units).single()

    private fun ChipView.numbers() = runs.map { text.substring(it.start, it.end) }

    @Test
    fun `mau cua owner - bon so xanh, khe truc rong gap doi, khong chu don vi`() {
        val c = chip(allOk, labels = false)
        assertEquals("2.4 2.4  2.6 2.6", c.text)
        assertEquals(listOf("2.4", "2.4", "2.6", "2.6"), c.numbers(), "mỗi đoạn màu bọc ĐÚNG một con số")
        assertTrue(c.runs.all { it.tone == ChipTone.ENERGY }, "bình thường = xanh của chip pin (ChipTone.ENERGY)")
        assertEquals(ChipTone.ENERGY, c.tone, "cả bốn xanh ⇒ icon xanh")
        assertEquals("ic-group-tyres", c.icon)
        assertFalse(c.text.contains("bar"), "không in chữ đơn vị trên chip")
    }

    @Test
    fun `bat nhan thi them Lop va cham giua, cac doan mau van dung vi tri`() {
        val c = chip(allOk)
        assertEquals("Lốp · 2.4 2.4  2.6 2.6", c.text)
        assertEquals(listOf("2.4", "2.4", "2.6", "2.6"), c.numbers())
        Strings.current = Lang.EN
        assertEquals("Tyres · 2.4 2.4  2.6 2.6", chip(allOk).text)
    }

    @Test
    fun `don vi theo ho so - bar, kPa, psi`() {
        assertEquals("2.4 2.4  2.6 2.6", chip(allOk, bar, labels = false).text)
        assertEquals("240 240  260 260", chip(allOk, kpa, labels = false).text)
        // 240 kPa × 0.1450377377 = 34.81 ⇒ "34.8"; 260 ⇒ 37.71 ⇒ "37.7" (psi 1 chữ số — Units.OPTIONS).
        val p = chip(allOk, psi, labels = false)
        assertEquals("34.8 34.8  37.7 37.7", p.text)
        assertEquals(listOf("34.8", "34.8", "37.7", "37.7"), p.numbers())
    }

    @Test
    fun `moi so mot mau theo xe - do vang xanh xam, icon lay mau nang nhat`() {
        val t = CarStatus.Tyres(
            pFlKpa = 180.0, pFrKpa = 230.0, pRlKpa = 260.0,
            cFl = TyreJudge.COLOUR_RED, cFr = TyreJudge.COLOUR_YELLOW, cRl = TyreJudge.COLOUR_WHITE,
        )
        val c = chip(t, labels = false)
        assertEquals("1.8 2.3  2.6 —", c.text, "bánh SP không số ⇒ '—'")
        assertEquals(
            listOf(ChipTone.ALERT, ChipTone.WARN, ChipTone.ENERGY, ChipTone.NEUTRAL), c.runs.map { it.tone },
        )
        assertEquals(ChipTone.ALERT, c.tone, "icon = màu nặng nhất")
        val yellowOnly = chip(t.copy(cFl = TyreJudge.COLOUR_WHITE), labels = false)
        assertEquals(ChipTone.WARN, yellowOnly.tone)
        val okButOneGrey = chip(allOk.copy(cRr = null), labels = false)
        assertEquals(ChipTone.NEUTRAL, okButOneGrey.tone, "chưa đủ bốn xanh ⇒ icon trung tính, không xanh")
    }

    @Test
    fun `khong so ma xe van to mau thi van in dau gach co mau`() {
        val t = CarStatus.Tyres(cFl = 1, cFr = 1, cRl = 1, cRr = 1)   // xe đo gián tiếp: không số, cụm vẫn có màu
        val c = chip(t, labels = false)
        assertEquals("— —  — —", c.text)
        assertTrue(c.runs.all { it.tone == ChipTone.ENERGY }, "màu xe phán vẫn hiện dù không có số")
    }

    @Test
    fun `off-car mot dau gach duy nhat, khong doan mau nao`() {
        val on = chip(CarStatus.Tyres())
        assertEquals("Lốp · —", on.text)
        assertTrue(on.runs.isEmpty())
        assertEquals(ChipTone.NEUTRAL, on.tone)
        assertEquals("—", chip(CarStatus.Tyres(), labels = false).text)
        assertEquals("Áp suất lốp: chưa đọc được", on.desc)
    }

    @Test
    fun `chip cu KHONG doi mot byte - runs rong mac dinh`() {
        val s = CarStatus(energy = CarStatus.Energy(soc = 82, evRangeKm = 418))
        val chips = TopStripChips.render(TopStripConfig.DEFAULT, s)
        assertTrue(chips.all { it.runs.isEmpty() }, "chỉ chip lốp mang đoạn màu")
        assertEquals("82% · 418 km", chips.single { it.icon == "ic-bolt" }.text)
    }

    @Test
    fun `cau trinh doc man hinh du bon banh, ca 5 ngon ngu`() {
        val t = allOk.copy(cRr = TyreJudge.COLOUR_RED, psRr = TyreJudge.PRESSURE_UNDER)
        Strings.current = Lang.VI
        assertEquals(
            "Áp suất lốp: Trước trái 2.4 bar, bình thường; Trước phải 2.4 bar, bình thường; " +
                "Sau trái 2.6 bar, bình thường; Sau phải 2.6 bar, cảnh báo: non",
            chip(t).desc,
        )
        Strings.current = Lang.EN
        assertEquals(
            "Tyre pressure: Front left 2.4 bar, normal; Front right 2.4 bar, normal; " +
                "Rear left 2.6 bar, normal; Rear right 2.6 bar, alert: low",
            chip(t).desc,
        )
        val han = Regex("\\p{IsHan}")
        val thai = Regex("\\p{IsThai}")
        val viMark = Regex("[ăâđêôơưàảãáạằẳẵắặầẩẫấậèẻẽéẹềểễếệìỉĩíịòỏõóọồổỗốộờởỡớợùủũúụừửữứựỳỷỹýỵ]", RegexOption.IGNORE_CASE)
        for (l in listOf(Lang.ZH, Lang.TH, Lang.MS)) {
            Strings.current = l
            val d = chip(t).desc
            assertFalse(viMark.containsMatchIn(d), "$l: câu trình đọc còn chữ Việt: $d")
            TyreCorner.values().forEach { assertTrue(d.contains(it.labelIn(l)), "$l: thiếu tên góc ${it.labelIn(l)}: $d") }
            when (l) {
                Lang.ZH -> assertTrue(han.containsMatchIn(d), d)
                Lang.TH -> assertTrue(thai.containsMatchIn(d), d)
                else -> assertFalse(d.contains("normal") || d.contains("alert"), "ms không được lùi về tiếng Anh: $d")
            }
        }
    }

    // ── R7 — ngân sách đọc K1 ──────────────────────────────────────────────────────────────────────

    /** Gateway đếm lượt chạm HAL (getter + lượt tra tên feature) — bọc [FakeHalGateway]. */
    private class Counting(private val inner: HalGateway) : HalGateway by inner {
        val getters = ArrayList<String>()
        val names = ArrayList<String>()
        override fun getter(deviceFqn: String, method: String, arg: Int?): String? {
            getters += method
            return inner.getter(deviceFqn, method, arg)
        }
        override fun featureIdByName(constName: String): Int? {
            names += constName
            return inner.featureIdByName(constName)
        }
        fun stateReads() = getters.count { it in TPMS_STATE }
    }

    private companion object {
        val TPMS_STATE = setOf("getTyrePressureState", "getTyreAirLeakState", "getTyreSystemState")
        val COLOUR_NAMES = listOf("LF", "RF", "LB", "RB").map { "INSTRUMENT_2IN1_${it}_TYRE_COLOR" }
    }

    private val colourNames = COLOUR_NAMES

    private fun gateway(colour: String?): Counting = Counting(
        FakeHalGateway(
            getters = mapOf(
                "getTyrePressureValue" to "250", "getTyrePressureState" to "0",
                "getTyreAirLeakState" to "0", "getTyreSystemState" to "0",
            ),
            featureNames = if (colour == null) emptyMap() else colourNames.mapIndexed { i, n -> n to 500 + i }.toMap(),
            features = if (colour == null) emptyMap() else (0..3).associate { 500 + it to colour },
        ),
    )

    /** Màn CHỈ bày chip lốp (ô giữa trống, thanh nút trống) — để mọi lượt chạm HAL đếm được đều là của lốp. */
    private val chipDemand: () -> Set<String>? = {
        CarDataDemand.of(
            HomeUiState(
                workspace = WorkspaceState(slots = List(WorkspaceState.SLOT_CAP) { SlotContent.Empty }),
                dock = DockConfig(enabled = emptyList()),
                topStrip = TopStripConfig(ids = listOf(TopStripConfig.TYRES)),
            ),
        )
    }

    @Test
    fun `R7 cum noi trang thi KHONG doc ma TPMS - 8 luot moi nhip cham`() {
        val gw = gateway(colour = "int=1")
        val s = CarDataAdapter(HalBindingTable(gw), chipDemand, clock = { 0L }).readSlow(CarStatus())
        assertEquals(0, gw.stateReads(), "cụm đã phán bình thường ⇒ 0 lượt đọc PS/LK/SYS")
        assertEquals(4, gw.getters.count { it == "getTyrePressureValue" })
        assertEquals(4, gw.names.size, "màu cụm luôn đọc — đường phục hồi không gate bằng dữ liệu chính nó làm mới")
        assertTrue(TyreBoard.readings(s.tyres).all { it.severity == TyreSeverity.OK })
    }

    @Test
    fun `R7 cum chua phan thi doc du ma TPMS, roi GIU so cu giua hai luot doc`() {
        val gw = gateway(colour = null)
        var now = 0L
        val adapter = CarDataAdapter(HalBindingTable(gw), chipDemand, clock = { now })
        var s = adapter.readSlow(CarStatus())
        assertEquals(9, gw.stateReads(), "nhịp đầu đọc NGAY: 4 PS + 4 LK + 1 SYS")
        assertTrue(TyreBoard.readings(s.tyres).all { it.severity == TyreSeverity.OK }, "M6 qua mã TPMS")
        // Soát 2.88 regress-2: mã TPMS còn cần thì đọc mỗi 3 nhịp chậm (Pass 2: giữ tối đa 25 s), GIỮ số cũ ở giữa (không nháy xám).
        gw.getters.clear()
        repeat(2) {
            now += 10_000; s = adapter.readSlow(s)
            assertTrue(TyreBoard.readings(s.tyres).all { it.severity == TyreSeverity.OK }, "nhịp giữa vẫn xanh")
        }
        assertEquals(0, gw.stateReads(), "hai nhịp giữa: giữ số cũ, 0 lượt đọc mã TPMS")
        assertEquals(8, gw.getters.count { it == "getTyrePressureValue" }, "áp suất vẫn đọc mỗi nhịp")
        now += 10_000; adapter.readSlow(s)
        assertEquals(9, gw.stateReads(), "nhịp thứ ba: đọc lại")
    }

    @Test
    fun `R7 cum da to mau ca bon banh thi KHONG doc SYS - M1 khong bao gio hoi no`() {
        val gw = Counting(
            FakeHalGateway(
                getters = mapOf("getTyrePressureValue" to "250", "getTyrePressureState" to "2", "getTyreAirLeakState" to "0",
                    "getTyreSystemState" to "0"),
                featureNames = colourNames.mapIndexed { i, n -> n to 500 + i }.toMap(),
                features = mapOf(500 to "int=1", 501 to "int=1", 502 to "int=1", 503 to "int=2"),
            ),
        )
        val s = CarDataAdapter(HalBindingTable(gw), chipDemand, clock = { 0L }).readSlow(CarStatus())
        assertEquals(listOf("getTyrePressureState", "getTyreAirLeakState"), gw.getters.filter { it in TPMS_STATE },
            "chỉ PS + LK của bánh SP (cụm vàng) — SYS không đọc vì cả bốn màu cụm hợp lệ")
        val rr = TyreBoard.readings(s.tyres).last()
        assertEquals(TyreSeverity.WARN to TyreStatus.UNDER, rr.severity to rr.status, "cụm vàng, chữ 'non' từ TPMS")
        assertEquals(null, s.tyres.sys)
    }

    @Test
    fun `R7 mot banh khong co mau cum thi doc SYS, banh vua roi trang thi doc ngay du chua toi nhip`() {
        val colours = mutableMapOf<Int, String?>(500 to "int=1", 501 to "int=1", 502 to "int=1", 503 to "int=1")
        val gw = Counting(
            FakeHalGateway(
                getters = mapOf("getTyrePressureValue" to "250", "getTyrePressureState" to "2", "getTyreAirLeakState" to "0",
                    "getTyreSystemState" to "0"),
                featureNames = colourNames.mapIndexed { i, n -> n to 500 + i }.toMap(),
                features = colours,
            ),
        )
        var now = 0L
        val adapter = CarDataAdapter(HalBindingTable(gw), chipDemand, clock = { now })
        var s = adapter.readSlow(CarStatus())
        assertEquals(0, gw.stateReads(), "cả bốn trắng ⇒ 0 lượt đọc mã TPMS")
        // Nhịp 2 (CHƯA tới nhịp đọc định kỳ): SP vừa đổi sang vàng ⇒ chưa có số cũ ⇒ đọc NGAY, chữ lý do không trễ.
        colours[503] = "int=2"; now += 10_000; s = adapter.readSlow(s)
        assertEquals(listOf("getTyrePressureState", "getTyreAirLeakState"), gw.getters.filter { it in TPMS_STATE })
        assertEquals(TyreStatus.UNDER, TyreBoard.readings(s.tyres).last().status)
        // Màu cụm của ST mất (0 = INVALID ⇒ null) ⇒ phép phán của ST lùi sang TPMS ⇒ SYS phải được đọc.
        gw.getters.clear(); colours[502] = "int=0"; now += 10_000; s = adapter.readSlow(s)
        assertTrue("getTyreSystemState" in gw.getters, "có bánh không có màu cụm ⇒ đọc SYS")
        assertEquals(TyreSeverity.ALERT to TyreStatus.UNDER, TyreBoard.readings(s.tyres)[2].let { it.severity to it.status })
    }

    // ── R7 · K1 trên màn MẶC ĐỊNH (soát 2.88 regress-2) ──────────────────────────────────────────────────

    /**
     * Gateway trả lời MỌI lượt đọc (trần trên: không datum nào nguội vì *"xe không có"*), màu cụm theo ca; đếm lượt
     * đọc HAL đúng như `KachiPerf.Counter.HAL_READ` (getter · featureGet), cộng setting/local cho chắc.
     */
    private class AnswerAll(private val colour: String) : HalGateway by FakeHalGateway() {
        var reads = 0
        var stateReads = 0
        var colourReads = 0
        private val colourIds = (0..3).map { 700 + it }.toSet()
        override fun getter(deviceFqn: String, method: String, arg: Int?): String? {
            reads++
            if (method in TPMS_STATE) stateReads++
            return when (method) {
                "getTyrePressureValue" -> "250"
                in TPMS_STATE -> "0"
                else -> "1"
            }
        }
        override fun featureGet(deviceFqn: String, id: Int): String? {
            reads++
            if (id in colourIds) { colourReads++; return colour }
            return "int=1"
        }
        override fun settingGet(key: String): String? { reads++; return "1" }
        override fun localGet(target: String, method: String, arg: Int?): String? { reads++; return "1" }
        override fun featureIdByName(constName: String): Int? =
            COLOUR_NAMES.indexOf(constName).takeIf { it >= 0 }?.let { 700 + it } ?: (8000 + (constName.hashCode() and 0xFFFF))
    }

    /** Màn mặc định THẬT: bố cục `WorkspaceState.DEFAULT` (có `w_board` ⇒ có lốp) + chip mặc định; thanh nút đọc ở
     *  nhịp NHANH ([CarDataAdapter.readFast]) nên ngoài phép đo nhịp chậm này. */
    private val defaultDemand: () -> Set<String>? = {
        CarDataDemand.of(HomeUiState(workspace = WorkspaceState.DEFAULT, dock = DockConfig(enabled = emptyList())))
    }

    /** Lượt đọc / phút ở trạng thái ổn định: 1 phút làm nóng (cho cache nguội xong) rồi đo 10 phút. */
    private fun perMinute(colour: String): Triple<Int, Int, Int> {
        val gw = AnswerAll(colour)
        var now = 0L
        val adapter = CarDataAdapter(HalBindingTable(gw), defaultDemand, clock = { now })
        var s = CarStatus()
        repeat(6) { s = adapter.readSlow(s); now += 10_000 }
        gw.reads = 0; gw.stateReads = 0; gw.colourReads = 0
        repeat(60) { s = adapter.readSlow(s); now += 10_000 }
        return Triple(gw.reads / 10, gw.stateReads / 10, gw.colourReads / 10)
    }

    @Test
    fun `R7 K1 man mac dinh duoi 150 luot moi phut o moi ca mau cum`() {
        assertTrue(TyreIds.RAW_STATES.all { it in defaultDemand()!! }, "w_board mặc định kéo 13 mã thô vào nhu cầu")
        val cases = mapOf(
            "int=1" to "cụm nói cả bốn trắng",
            "int=0" to "cụm chưa có màu (0 = INVALID, getter CÓ trả lời ⇒ KHÔNG nguội — soát Pass 2)",
            "int=-2147482648" to "xe không có kênh màu cụm (FAILED ⇒ nguội)",
            "int=3" to "cụm tô đỏ cả bốn bánh (sáng lạnh / TPMS chưa hiệu chuẩn)",
            "int=9" to "mã màu lạ cả bốn bánh (không nguội, không phán được)",
        )
        val got = cases.mapValues { (c, _) -> perMinute(c) }
        got.forEach { (c, r) -> assertTrue(r.first < 150, "${cases[c]}: ${r.first} lượt/phút ≥ 150 (K1)") }
        // Phần của lốp, khoá đúng ba luật cắt của `readTyres` (mã TPMS mỗi 3 nhịp · SYS chỉ khi có bánh không màu cụm).
        assertEquals(0 to 24, got.getValue("int=1").let { it.second to it.third }, "trắng: 0 mã TPMS, 4 màu × 6")
        assertEquals(18 to 24, got.getValue("int=0").let { it.second to it.third }, "màu 0: (8 PS/LK + 1 SYS) × 2 + 4 màu × 6")
        assertEquals(18, got.getValue("int=-2147482648").second, "không kênh màu: (8 PS/LK + 1 SYS) × 2 lượt/phút")
        assertTrue(got.getValue("int=-2147482648").third <= 4, "không kênh màu: chỉ còn lượt thử lại của cache")
        assertEquals(16 to 24, got.getValue("int=3").let { it.second to it.third }, "đỏ: 8 PS/LK × 2, KHÔNG SYS")
        assertEquals(18 to 24, got.getValue("int=9").let { it.second to it.third }, "mã lạ: 9 × 2 + 4 màu × 6")
    }

    // ── Soát 2.88 Pass 2 — tuổi của mã giữ lại · mã "chưa có số" không làm nguội getter ─────────────────────────

    /**
     * Khoá soát Pass 2 (a): bản đếm nhịp giữ PS/LK/SYS từ TRƯỚC lúc màn khuất (vòng poll dừng, bộ đếm + ảnh chụp cũ
     * còn) hoặc trước lúc lốp rời màn ⇒ xe không có kênh màu cụm mà lốp đã non trong lúc đó vẫn XANH (M6) thêm 2 nhịp.
     */
    @Test
    fun `R7 ma giu lai khong bao gio cu - man khuat lau hay lop roi man roi bay lai thi doc ngay`() {
        val tpms = mutableMapOf<String, String?>(
            "getTyrePressureValue" to "250", "getTyrePressureState" to "0", "getTyreAirLeakState" to "0",
            "getTyreSystemState" to "0",
        )
        val gw = Counting(FakeHalGateway(getters = tpms))   // không kênh màu cụm ⇒ phán bằng mã TPMS (M2–M6)
        var now = 0L
        var showTyres = true
        val adapter = CarDataAdapter(HalBindingTable(gw), { if (showTyres) chipDemand() else setOf("soc") }, clock = { now })
        var s = adapter.readSlow(CarStatus())
        assertTrue(TyreBoard.readings(s.tyres).all { it.severity == TyreSeverity.OK })
        // (1) Màn khuất 1 giờ (vòng poll dừng — không một nhịp nào), lốp non trong lúc đó; nhịp ĐẦU khi quay lại phải đọc.
        tpms["getTyrePressureState"] = "2"; now += 3_600_000L; s = adapter.readSlow(s)
        assertTrue(TyreBoard.readings(s.tyres).all { it.severity == TyreSeverity.ALERT }, "không được giữ XANH cũ 1 giờ")
        // (2) Lốp rời màn 5 phút (vòng poll vẫn chạy), lốp về bình thường lúc ấy; nhịp đầu bày lại phải đọc.
        showTyres = false
        repeat(30) { now += 10_000; s = adapter.readSlow(s) }
        tpms["getTyrePressureState"] = "0"; showTyres = true; now += 10_000; gw.getters.clear(); s = adapter.readSlow(s)
        assertEquals(9, gw.stateReads(), "bày lại ⇒ đọc NGAY cả 9 mã")
        assertTrue(TyreBoard.readings(s.tyres).all { it.severity == TyreSeverity.OK }, "không được giữ ĐỎ cũ")
        // (3) Đồng hồ lùi (đầu xe chỉnh giờ) ⇒ đọc, không khoá.
        gw.getters.clear(); now -= 60_000; adapter.readSlow(s)
        assertEquals(9, gw.stateReads())
        // (4) Soát Pass 3 — lốp rời màn NGẮN (20 s < TPMS_HOLD_MS) rồi bày lại ⇒ vẫn đọc NGAY. Khoá riêng nhánh "chưa bày
        // đủ 13 mã ⇒ xoá mốc" của `tpmsDue`: ca (2) rời màn 5 phút nên đồng hồ một mình đã đủ đến hạn, không phân biệt được.
        showTyres = false; now += 10_000; adapter.readSlow(s)
        showTyres = true; now += 10_000; gw.getters.clear(); adapter.readSlow(s)
        assertEquals(9, gw.stateReads(), "bày lại sau 20 s ⇒ đọc NGAY nhờ xoá mốc, không chờ hết 25 s")
    }

    /**
     * Soát Pass 3 — khoá PHẠM VI của [HalReadTables.INVALID_IS_PENDING]: chỉ màu cụm + áp lốp. Nới ra (vd mọi khoá của
     * [HalReadTables.INVALID_VALUES]) thì cửa trả 255 / tầm điện trả 1023 không bao giờ nguội ⇒ đọc mỗi nhịp, vượt K1 —
     * mà không bài nào đỏ, vì bài hiển thị chỉ xem giá trị in ra.
     */
    @Test
    fun `chi mau cum va ap lop duoc coi ma khong hop le la chua co so`() {
        assertEquals((TyreIds.COLOUR + TyreIds.PRESSURE).toSet(), HalReadTables.INVALID_IS_PENDING)
    }

    /**
     * Khoá soát Pass 2 (b): màu cụm 0 và áp suất 4092..4095 là mã *"chưa có số"* (getter CÓ trả lời) — lúc vừa nổ máy,
     * cảm biến TPMS chưa phát. Bản trước cho [HalAbsentCache] nguội sau 3 nhịp (thử lại 60 s → … → 10 phút) ⇒ số/màu
     * thật tới mà chip vẫn "—"/XÁM thêm vài phút. Getter VẮNG thật (FAILED) vẫn nguội.
     */
    @Test
    fun `ma chua co so khong lam nguoi getter - so va mau that den la nhip ke thay ngay`() {
        val colours = mutableMapOf<Int, String?>(500 to "int=0", 501 to "int=0", 502 to "int=0", 503 to "int=0")
        val values = mutableMapOf<String, String?>("getTyrePressureValue" to "4094")
        val gw = Counting(
            FakeHalGateway(getters = values, featureNames = colourNames.mapIndexed { i, n -> n to 500 + i }.toMap(), features = colours),
        )
        var now = 0L
        val adapter = CarDataAdapter(HalBindingTable(gw), chipDemand, clock = { now })
        var s = CarStatus()
        repeat(4) { s = adapter.readSlow(s); now += 10_000 }   // 40 s "chưa có số" — quá 3 lượt trượt của cache
        assertTrue(TyreBoard.readings(s.tyres).all { it.pressureKpa == null && it.severity == TyreSeverity.NONE })
        colours.keys.forEach { colours[it] = "int=1" }; values["getTyrePressureValue"] = "262"
        s = adapter.readSlow(s)
        assertEquals(List(4) { 262.0 }, TyreBoard.readings(s.tyres).map { it.pressureKpa }, "số tới ⇒ hiện ngay")
        assertTrue(TyreBoard.readings(s.tyres).all { it.severity == TyreSeverity.OK }, "màu cụm tới ⇒ XANH ngay")
        // Getter vắng thật (FAILED) vẫn nguội: sau 3 lượt trượt, 0 lượt đọc màu cho tới mốc thử lại.
        colours.keys.forEach { colours[it] = "int=-2147482648" }
        gw.names.clear()
        repeat(3) { now += 10_000; s = adapter.readSlow(s) }
        gw.names.clear()
        repeat(3) { now += 10_000; s = adapter.readSlow(s) }
        assertEquals(0, gw.names.size, "FAILED ⇒ nguội như cũ")
    }

    @Test
    fun `R7 man khong bay lop thi khong mot luot doc lop nao`() {
        val gw = gateway(colour = "int=2")
        CarDataAdapter(HalBindingTable(gw), { setOf("soc") }, clock = { 0L }).readSlow(CarStatus())
        assertTrue(gw.getters.none { it.startsWith("getTyre") } && gw.names.isEmpty())
    }
}
