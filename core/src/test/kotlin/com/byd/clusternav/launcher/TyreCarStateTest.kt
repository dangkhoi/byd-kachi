package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.88 · LỐP THEO PHÁN XÉT CỦA XE — bảng chân trị M1–M7 + cổng đọc + sentinel (spec `kachi-288-tyre-car-state`) ═══
 *
 * Khoá lệnh owner 04/10 (nguyên văn): *"Cảnh báo nó theo tùy loại xe đó nha, không hardcode số đâu"* và *"cái nào
 * cảnh báo thì vàng, đỏ, bình thường thì xanh lá như màu range lái"*. Ai đưa lại một con số ngưỡng vào phép phán thì
 * bảng chân trị dưới đây (toàn MÃ, không một số áp suất nào) vẫn xanh — nhưng `TyreNoThresholdContractTest` ở `:app` đỏ.
 *
 * Mọi hằng tham chiếu [TyreJudge] (mã OEM, dẫn `file:line` ở đó) — bài này KHÔNG tự chép số nghĩa.
 */
class TyreCarStateTest {

    private val sentinels = listOf(-2147482648, -2147482647, -2147482646, -2147482645)

    private fun j(c: Int? = null, ps: Int? = null, lk: Int? = null, sys: Int? = null) = TyreJudge.judge(c, ps, lk, sys)

    // ── M1 — màu cụm đồng hồ ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `M1 mau cum quyet MAU, TPMS chi cho CHU`() {
        assertEquals(TyreSeverity.ALERT, j(c = TyreJudge.COLOUR_RED).severity)
        assertEquals(TyreStatus.CAR_ALERT, j(c = TyreJudge.COLOUR_RED).status, "đỏ không lý do ⇒ chữ chung 'báo đỏ'")
        assertEquals(TyreSeverity.WARN, j(c = TyreJudge.COLOUR_YELLOW).severity)
        assertEquals(TyreStatus.CAR_WARN, j(c = TyreJudge.COLOUR_YELLOW).status, "vàng không lý do ⇒ 'chú ý'")
        assertEquals(TyreSeverity.OK, j(c = TyreJudge.COLOUR_WHITE).severity, "trắng = cụm nói bình thường ⇒ XANH")
        assertEquals(TyreSource.CLUSTER, j(c = TyreJudge.COLOUR_WHITE).source)
        // Lý do lấy từ TPMS, nhưng MÀU vẫn của cụm (cụm vàng + TPMS non ⇒ VÀNG chữ "non").
        val yUnder = j(c = TyreJudge.COLOUR_YELLOW, ps = TyreJudge.PRESSURE_UNDER)
        assertEquals(TyreSeverity.WARN to TyreStatus.UNDER, yUnder.severity to yUnder.status)
        val rLeak = j(c = TyreJudge.COLOUR_RED, lk = TyreJudge.LEAK_FAST, ps = TyreJudge.PRESSURE_OVER)
        assertEquals(TyreStatus.LEAK_FAST, rLeak.status, "nhiều lý do ⇒ thứ nguy nhất trước (xì nhanh)")
        // Cụm trắng thắng mọi mã TPMS (M1 dừng ngay) — và vì thế adapter không cần đọc chúng (R7).
        assertEquals(TyreSeverity.OK, j(c = TyreJudge.COLOUR_WHITE, ps = TyreJudge.PRESSURE_UNDER, sys = 3).severity)
    }

    @Test
    fun `M1 C bang 0 la INVALID, khong phai mau - roi sang TPMS`() {
        assertEquals(TyreSeverity.NONE, j(c = TyreJudge.COLOUR_INVALID).severity, "0 = cụm chưa có màu ⇒ không phán")
        assertEquals(TyreStatus.UNKNOWN, j(c = TyreJudge.COLOUR_INVALID).status, "0 là mã ĐÃ BIẾT ⇒ không phải 'mã lạ'")
        val viaTpms = j(c = TyreJudge.COLOUR_INVALID, ps = 0, lk = 0, sys = 0)
        assertEquals(TyreSeverity.OK to TyreSource.TYRE, viaTpms.severity to viaTpms.source, "lùi sang mã TPMS")
    }

    // ── M2 / M3 — hệ thống TPMS ─────────────────────────────────────────────────────────────────────

    @Test
    fun `M2 tu kiem hoac bi che thi XAM, du TPMS dang bao non`() {
        for (sys in listOf(TyreJudge.SYS_SELF_CHECK, TyreJudge.SYS_MASKED)) {
            val r = j(ps = TyreJudge.PRESSURE_UNDER, lk = 0, sys = sys)
            assertEquals(TyreSeverity.NONE, r.severity, "sys=$sys: mã lúc tự kiểm chưa phải lời phán")
            assertEquals(TyreStatus.UNKNOWN, r.status)
        }
    }

    @Test
    fun `M3 tin hieu bat thuong hoac hong thi VANG loi cam bien`() {
        for (sys in listOf(TyreJudge.SYS_SIGNAL_ABNORMAL, TyreJudge.SYS_BREAKDOWN)) {
            val r = j(ps = 0, lk = 0, sys = sys)
            assertEquals(TyreSeverity.WARN to TyreStatus.SENSOR, r.severity to r.status, "sys=$sys")
        }
    }

    // ── M4 / M5 / M6 ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `M4 mot ma bat thuong HOP LE la du de bao DO, ke ca khi SYS doc hong`() {
        assertEquals(TyreStatus.LEAK_FAST, j(lk = TyreJudge.LEAK_FAST).status)
        assertEquals(TyreStatus.UNDER, j(ps = TyreJudge.PRESSURE_UNDER).status)
        assertEquals(TyreStatus.OVER, j(ps = TyreJudge.PRESSURE_OVER).status)
        // SYS đọc hỏng (null / sentinel) mà PS = 2 ⇒ vẫn ĐỎ.
        for (sys in listOf<Int?>(null) + sentinels) {
            val r = j(ps = TyreJudge.PRESSURE_UNDER, sys = sys)
            assertEquals(TyreSeverity.ALERT to TyreStatus.UNDER, r.severity to r.status, "sys=$sys")
        }
    }

    @Test
    fun `M5 xi cham la VANG`() {
        val r = j(ps = 0, lk = TyreJudge.LEAK_SLOW, sys = 0)
        assertEquals(TyreSeverity.WARN to TyreStatus.LEAK_SLOW, r.severity to r.status)
    }

    @Test
    fun `M6 XANH chi khi DU ba ma hop le va ca ba binh thuong`() {
        assertEquals(TyreSeverity.OK, j(ps = 0, lk = 0, sys = 0).severity)
        assertEquals(TyreSource.TYRE, j(ps = 0, lk = 0, sys = 0).source)
        // Thiếu MỘT mã ⇒ không xanh.
        assertEquals(TyreSeverity.NONE, j(ps = 0, lk = 0).severity, "thiếu SYS ⇒ chưa đủ để nói bình thường")
        assertEquals(TyreSeverity.NONE, j(ps = 0, sys = 0).severity, "thiếu LK")
        assertEquals(TyreSeverity.NONE, j(lk = 0, sys = 0).severity, "thiếu PS")
        sentinels.forEach { s ->
            assertEquals(TyreSeverity.NONE, j(ps = 0, lk = 0, sys = s).severity, "SYS = sentinel $s ⇒ không xanh")
            assertEquals(TyreSeverity.NONE, j(ps = s, lk = 0, sys = 0).severity, "PS = sentinel $s ⇒ không xanh")
        }
    }

    // ── M7 — không phán được ────────────────────────────────────────────────────────────────────────

    @Test
    fun `M7 sentinel KHONG thanh ma la, ma la thi noi ra con so`() {
        sentinels.forEach { s ->
            val r = j(c = s, ps = s, lk = s, sys = s)
            assertEquals(TyreStatus.UNKNOWN, r.status, "sentinel $s là mã HỎNG đã biết ⇒ không phải 'mã lạ'")
            assertNull(r.code)
        }
        val odd = j(ps = 7)
        assertEquals(TyreStatus.CODE to 7, odd.status to odd.code)
        assertEquals(TyreSeverity.NONE, odd.severity, "mã lạ ⇒ XÁM, không bịa màu")
        // Xét theo TỪNG bảng: 3 có nghĩa ở bảng SYS (hỏng) nhưng là mã LẠ của bảng áp suất.
        assertEquals(TyreStatus.CODE to 3, j(ps = 3).let { it.status to it.code })
        assertEquals(TyreStatus.CODE to 9, j(c = 9).let { it.status to it.code }, "màu cụm 9 là mã lạ")
        assertEquals(TyreStatus.CODE to 5, j(sys = 5).let { it.status to it.code })
    }

    @Test
    fun `off-car KHONG BAO GIO xanh - rong, toan sentinel, toan C bang 0, chi co so`() {
        val cases = listOf(
            CarStatus.Tyres(),
            CarStatus.Tyres(cFl = 0, cFr = 0, cRl = 0, cRr = 0),
            CarStatus.Tyres(pFlKpa = 262.0, pFrKpa = 260.0, pRlKpa = 280.0, pRrKpa = 280.0),
        ) + sentinels.map { s ->
            CarStatus.Tyres(
                cFl = s, cFr = s, cRl = s, cRr = s, psFl = s, psFr = s, psRl = s, psRr = s,
                lkFl = s, lkFr = s, lkRl = s, lkRr = s, sys = s,
            )
        }
        cases.forEach { t ->
            val r = TyreBoard.readings(t)
            assertTrue(r.none { it.severity == TyreSeverity.OK }, "không bánh nào được XANH: $t")
            assertFalse(TyreBoard.anyAlert(t), "và không kêu cảnh báo: $t")
            val chip = TopStripChips.render(TopStripConfig(listOf(TopStripConfig.TYRES)), CarStatus(tyres = t)).single()
            assertTrue(chip.runs.none { it.tone == ChipTone.ENERGY } && chip.tone != ChipTone.ENERGY, "chip không xanh: $t")
        }
    }

    // ── Áp suất: 4 MÃ OEM trong dải số (soát 2.88 truth-1) — KHÔNG phải ngưỡng ──────────────────────────────

    /**
     * Khoá soát truth-1: 4092 nổ · 4093 bất thường · 4094 chưa có · 4095 mặc định là MÃ của launcher gốc BYD (L3
     * `TyreCardView.java:34-37`), không phải ~41 bar. Lọc ở TẦNG ĐỌC (`HalReadTables.INVALID_VALUES`) ⇒ chip, bảng, ô
     * nhóm `g_tyres` (đi qua [TelemetryReadout]), ô nhỏ và câu hỏi bằng giọng cùng một luật. Bản trước chỉ bảng lọc
     * (0..4094) ⇒ 4092..4094 hiện "40.9" trên chip/bảng, còn 4095 hiện "—" ở bảng nhưng "4095" ở ô nhóm.
     */
    @Test
    fun `ma OEM 4092-4095 cua ap suat thanh khong co so o TANG DOC, moi be mat cung luat`() {
        val codes = listOf(TyreJudge.PRESSURE_BURST, TyreJudge.PRESSURE_ABNORMAL, TyreJudge.PRESSURE_NONE, TyreJudge.PRESSURE_DEFAULT)
        assertEquals(listOf(4092, 4093, 4094, 4095), codes, "chép đúng L3 TyreCardView.java:34-37")
        val gw = FakeHalGateway(
            gettersByArg = mapOf("getTyrePressureValue" to codes.withIndex().associate { (i, c) -> i + 1 to c.toString() }),
        )
        val table = HalBindingTable(gw)
        TyreIds.PRESSURE.forEach { assertNull(table.readDouble(it), "$it: mã OEM ⇒ null ở tầng đọc") }
        // Qua đúng đường nhịp chậm ⇒ mọi bề mặt thấy "—", không "40.9" / "4095".
        val s = CarDataAdapter(table, { TyreIds.PRESSURE.toSet() }, clock = { 0L }).readSlow(CarStatus())
        assertTrue(TyreBoard.readings(s.tyres).all { it.pressureKpa == null }, "bảng / ô nhỏ")
        val chip = TopStripChips.render(TopStripConfig(listOf(TopStripConfig.TYRES), showLabels = false), s).single()
        assertEquals(TelemetryView.PLACEHOLDER, chip.text, "chip")
        TyreIds.PRESSURE.forEach { id ->
            assertEquals(TelemetryView.PLACEHOLDER, TelemetryReadout.of(id, s)!!.display, "$id: ô nhóm / câu trả lời giọng")
        }
        val group = GroupBoard.of("g_tyres", s)!!
        assertTrue(group.cells.filter { it.id in TyreIds.PRESSURE }.all { it.number == TelemetryView.PLACEHOLDER }, "ô nhóm g_tyres")
        // Số thật vẫn chảy qua; 0 là biên dưới hợp lệ; số âm / không hữu hạn ⇒ lưới cuối ở bảng.
        val real = FakeHalGateway(gettersByArg = mapOf("getTyrePressureValue" to mapOf(1 to "262", 2 to "0", 3 to "4091")))
        assertEquals(listOf(262.0, 0.0, 4091.0), TyreIds.PRESSURE.take(3).map { HalBindingTable(real).readDouble(it) })
        val r = TyreBoard.readings(CarStatus.Tyres(pFlKpa = -1.0, pFrKpa = Double.NaN, pRlKpa = 0.0, pRrKpa = 262.0))
        assertEquals(listOf(null, null, 0.0, 262.0), r.map { it.pressureKpa })
    }

    // ── Sentinel ở tầng đọc (R8) ───────────────────────────────────────────────────────────────────

    @Test
    fun `isSentinelRc phu CA DAI FAILED BUSY TIMEOUT INVALID`() {
        sentinels.forEach { assertTrue(HalBindingTable.isSentinelRc(it.toLong()), "$it phải là sentinel") }
        assertFalse(HalBindingTable.isSentinelRc(-2147482649L), "ngay dưới dải ⇒ không")
        assertFalse(HalBindingTable.isSentinelRc(-2147482644L), "ngay trên dải ⇒ không")
        assertFalse(HalBindingTable.isSentinelRc(0L))
        assertFalse(HalBindingTable.isSentinelRc(null))
        assertTrue(HalBindingTable.rawIsSentinel("int=-2147482647 float=0.0"), "BUSY dạng EventValue cũng bị bắt")
    }

    /** Soát 2.88 regress-5: lời đáp `getid` không được biến BUSY/TIMEOUT tạm thời thành *"trim không có"* (§2). */
    @Test
    fun `nghia sentinel - chi FAILED moi la trim khong co, BUSY TIMEOUT la tam thoi`() {
        assertTrue(HalBindingTable.sentinelMeaning(-2147482648L)!!.contains("khong co tren trim"))
        listOf(-2147482647L, -2147482646L).forEach { rc ->
            val m = HalBindingTable.sentinelMeaning(rc)!!
            assertTrue(m.contains("tam thoi") && !m.contains("khong co tren trim"), "$rc: $m")
        }
        assertTrue(HalBindingTable.sentinelMeaning(-2147482645L)!!.contains("khong hop le"))
        assertNull(HalBindingTable.sentinelMeaning(0L))
        assertNull(HalBindingTable.sentinelMeaning(null))
        sentinels.forEach { assertNotNull(HalBindingTable.sentinelMeaning(it.toLong()), "đủ cả dải isSentinelRc: $it") }
    }

    @Test
    fun `mau cum bang 0 bi loc o tang doc, mau 1 thi chay qua`() {
        val names = mapOf("INSTRUMENT_2IN1_LF_TYRE_COLOR" to 77)
        fun read(v: String) = HalBindingTable(
            FakeHalGateway(features = mapOf(77 to v), featureNames = names),
        ).readInt("tyre_c_fl")
        assertNull(read("int=0"), "COLOR_INVALID ⇒ null (\"—\"; nhịp poll KHÔNG cho nguội — HalReadTables.INVALID_IS_PENDING)")
        assertEquals(1, read("int=1"))
        assertNull(read("int=-2147482646"), "TIMEOUT ⇒ null")
    }

    @Test
    fun `ma TPMS doc dung getter va dung area`() {
        val gw = FakeHalGateway(
            gettersByArg = mapOf(
                "getTyrePressureState" to mapOf(1 to "0", 2 to "1", 3 to "2", 4 to "0"),
                "getTyreAirLeakState" to mapOf(4 to "2"),
            ),
            getters = mapOf("getTyreSystemState" to "0"),
        )
        val t = HalBindingTable(gw)
        assertEquals(listOf(0, 1, 2, 0), TyreIds.PRESSURE_STATE.map { t.readInt(it) }, "area 1..4 = TT TP ST SP")
        assertEquals(2, t.readInt("tyre_lk_rr"))
        assertEquals(0, t.readInt("tyre_sys"))
        assertNull(gw.getterArgs["getTyreSystemState"], "getTyreSystemState() KHÔNG tham số")
    }

    // ── Bộ mã: một chỗ khai, đủ dây ─────────────────────────────────────────────────────────────────

    @Test
    fun `13 ma trang thai co trong bo dang ky, NEEDS_CAR, an khoi moi bo chon`() {
        assertEquals(13, TyreIds.RAW_STATES.size)
        TyreIds.RAW_STATES.forEach { id ->
            val spec = TelemetryRegistry.byId(id)
            assertNotNull(spec, "$id phải có dòng đăng ký")
            assertEquals(EvidenceTier.NEEDS_CAR, spec!!.tier, "$id: giá trị thật trên xe CHƯA đo ⇒ NEEDS_CAR")
            assertEquals(Domain.TYRES, spec.domain)
            assertTrue(id in CapabilityCatalog.HIDDEN_FROM_PICKER, "$id phải ẩn khỏi mọi bộ chọn")
            assertTrue(CapabilityCatalog.all().none { it.id == id })
            assertTrue(TopStripConfig.choices().none { it.id == id })
        }
        // Màu cụm bind theo TÊN hằng (không theo số — feature-id gán lúc khởi tạo theo cấu hình xe).
        TyreIds.COLOUR.forEach { id ->
            assertTrue(HalBindingTable.routeOf(TelemetryRegistry.byId(id)!!.bindingKey) is BindingRoute.FeatureName, id)
        }
        // LB = sau-trái, RB = sau-phải (L3 BydAutoTyrePressureInstrumentMonitor.java:144-158).
        assertEquals("BYDAutoFeatureIds.INSTRUMENT_2IN1_LB_TYRE_COLOR", TelemetryRegistry.byId("tyre_c_rl")!!.bindingKey)
        assertEquals("BYDAutoFeatureIds.INSTRUMENT_2IN1_RB_TYRE_COLOR", TelemetryRegistry.byId("tyre_c_rr")!!.bindingKey)
    }

    @Test
    fun `ap suat moi banh keo theo ma trang thai cua DUNG banh do`() {
        TyreIds.PRESSURE.forEachIndexed { i, p ->
            assertEquals(
                setOf(TyreIds.COLOUR[i], TyreIds.PRESSURE_STATE[i], TyreIds.AIR_LEAK[i], TyreIds.SYSTEM),
                CarDataDemand.COMPANION[p],
            )
        }
        // Một chỗ phủ mọi bề mặt bày lốp: chip, widget tổng hợp, nhóm.
        fun demandOf(chips: List<String> = emptyList(), widget: String? = null) = CarDataDemand.of(
            HomeUiState(
                workspace = WorkspaceState(
                    slots = listOfNotNull(widget?.let { SlotContent.Widget(listOf(it)) }) +
                        List(WorkspaceState.SLOT_CAP - (if (widget != null) 1 else 0)) { SlotContent.Empty },
                ),
                topStrip = TopStripConfig(ids = chips),
            ),
        )!!
        listOf(demandOf(chips = listOf(TopStripConfig.TYRES)), demandOf(widget = "w_tire"), demandOf(widget = "g_tyres"))
            .forEach { assertTrue(it.containsAll(TyreIds.RAW_STATES), "thiếu mã trạng thái: ${TyreIds.RAW_STATES - it}") }
    }

    @Test
    fun `dong log tho - doi thi doi dong, khong ma nao thi khong ghi`() {
        assertNull(TyreBoard.rawLine(CarStatus.Tyres()), "off-car / màn không bày lốp ⇒ không có gì để ghi")
        val t = CarStatus.Tyres(
            pFlKpa = 262.0, pFrKpa = 260.0, pRlKpa = 280.0, pRrKpa = 280.0,
            cFl = 1, cFr = 1, cRl = 1, cRr = 3, psRr = 2,
        )
        assertEquals("TYRE raw p=4/4 c=1,1,1,3|ps=-,-,-,2|lk=-,-,-,-|sys=- → G,G,G,R src=cluster", TyreBoard.rawLine(t))
        val tpms = CarStatus.Tyres(psFl = 0, lkFl = 0, sys = 0, psFr = 7)
        assertEquals("TYRE raw p=0/4 c=-,-,-,-|ps=0,7,-,-|lk=0,-,-,-|sys=0 → G,N,N,N src=tyre", TyreBoard.rawLine(tpms))
        // Không in áp suất (nhảy ±1 kPa mỗi nhịp) ⇒ số đổi mà phán xét không đổi thì dòng không đổi.
        assertEquals(TyreBoard.rawLine(t), TyreBoard.rawLine(t.copy(pFlKpa = 263.0)))
    }

    /**
     * Khoá quyết định §A của spec (không cần hỏi owner): chip lốp là TUỲ CHỌN — đặt được, KHÔNG tự mọc trên thanh
     * của người đang dùng máy (luật [TopStripConfig.DEFAULT_IDS]).
     */
    @Test
    fun `chip lop dung san nhung KHONG o mac dinh`() {
        assertTrue(TopStripConfig.TYRES in TopStripConfig.BUILT_IN)
        assertFalse(TopStripConfig.TYRES in TopStripConfig.DEFAULT_IDS)
        assertTrue(TopStripConfig.isChippable(TopStripConfig.TYRES))
        val pick = TopStripConfig.choices().single { it.id == TopStripConfig.TYRES }
        assertEquals("Áp suất lốp" to "Tyre pressure", pick.label to pick.labelEn)
        assertEquals("ic-group-tyres", pick.icon)
        assertEquals(Domain.TYRES, pick.domain)
    }

    /**
     * Soát 2.88 truth-3 / ui-1 / regress-4: câu kết luận (nhãn TalkBack của bảng lốp) xếp theo mức ĐÃ PHÁN, không theo
     * màu mặc định của chữ. Cụm VÀNG + TPMS "non" ⇒ UNDER/WARN; cụm ĐỎ không lý do ⇒ CAR_ALERT/ALERT. Bản trước đọc
     * *"1 bánh non · 1 bánh báo đỏ"* — bánh vàng trước bánh đỏ, ngược với KDoc *"nặng trước"* và ngược với màu trên bảng.
     */
    @Test
    fun `ket luan neu banh DO truoc banh VANG theo muc da phan, ke ca o nhanh M1`() {
        val t = CarStatus.Tyres(
            cFl = TyreJudge.COLOUR_YELLOW, psFl = TyreJudge.PRESSURE_UNDER, lkFl = 0,
            cFr = TyreJudge.COLOUR_RED, psFr = 0, lkFr = 0,
            cRl = TyreJudge.COLOUR_WHITE, cRr = TyreJudge.COLOUR_WHITE,
        )
        val r = TyreBoard.readings(t)
        assertEquals(TyreSeverity.WARN to TyreStatus.UNDER, r[0].severity to r[0].status)
        assertEquals(TyreSeverity.ALERT to TyreStatus.CAR_ALERT, r[1].severity to r[1].status)
        assertEquals("1 bánh báo đỏ · 1 bánh non", TyreBoard.verdict(r))
        // Cùng lý do ở hai mức ⇒ một nhóm, xếp theo mức nặng nhất của nhóm (đỏ) — trước "xì chậm" (vàng).
        val mixed = TyreBoard.readings(
            t.copy(cFr = TyreJudge.COLOUR_RED, psFr = TyreJudge.PRESSURE_UNDER, cRl = null, psRl = 0, lkRl = 2, sys = 0),
        )
        assertEquals("2 bánh non · 1 bánh xì chậm", TyreBoard.verdict(mixed))
        // Mẫu EN dạng liệt kê (soát ui-2): lý do 2.88 là danh từ.
        Strings.current = Lang.EN
        try {
            assertEquals("1 wheel: red alert · 1 wheel: low", TyreBoard.verdict(r))
            assertEquals("2 wheels: low · 1 wheel: slow leak", TyreBoard.verdict(mixed))
        } finally {
            Strings.current = Lang.VI
        }
    }
}
