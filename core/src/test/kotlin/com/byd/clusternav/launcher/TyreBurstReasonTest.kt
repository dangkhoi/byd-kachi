package com.byd.clusternav.launcher

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 `TYRE-BURST-REASON` (spec `docs/specs/kachi-293-widget.html` R-W17) — áp suất lốp trả MÃ OEM 4092 (nổ lốp) / 4093
 * (bất thường): 2.88 đã tô màu theo màu cụm (M1) nhưng không in chữ lý do như launcher gốc.
 *
 * [ĐO source] L3 `TyreCardView.java` (jadx-l3-new): `:133-138` màu cụm KHÔNG phải 1/2/3 ⇒ "--" rồi dừng; `:140-158` màu
 * hợp lệ + 4092 ⇒ chữ nổ lốp (vùng ROW: "--" + icon nổ lốp), 4093 ⇒ chữ bất thường. Senior review 2.93 Pass 1 SIẾT cổng
 * thành màu CÙNG CHIỀU (KDoc `TyreJudge.burstUnder`): L3 đọc mã + màu từ CÙNG kênh cụm, Kachi đọc mã từ getter TPMS khác
 * ([SUY] cùng bộ mã) ⇒ cụm TRẮNG mà chữ "nổ lốp" là báo động giả cho người đang lái. Bài khoá cổng đó (đỏ ⇒ nổ lốp; vàng/đỏ ⇒
 * bất thường, sau chữ TPMS; trắng ⇒ im) và đường mã đi từ lượt đọc áp suất (cùng lượt HAL, không thêm) tới chữ của ô bánh.
 */
class TyreBurstReasonTest {

    @AfterEach
    fun lang() {
        Strings.current = Lang.VI
    }

    private fun j(c: Int?, pc: Int?, ps: Int? = null, lk: Int? = null, sys: Int? = null) = TyreJudge.judge(c, ps, lk, sys, pc)

    @Test
    fun `mau cum cung chieu - 4092 la NO LOP khi do, 4093 la BAT THUONG khi vang hoac do, mau van cua cum`() {
        assertEquals(TyreJudgement(TyreStatus.BURST, TyreSeverity.ALERT, TyreSource.CLUSTER), j(TyreJudge.COLOUR_RED, 4092))
        assertEquals(TyreJudgement(TyreStatus.ABNORMAL, TyreSeverity.WARN, TyreSource.CLUSTER), j(TyreJudge.COLOUR_YELLOW, 4093))
        assertEquals(TyreJudgement(TyreStatus.ABNORMAL, TyreSeverity.ALERT, TyreSource.CLUSTER), j(TyreJudge.COLOUR_RED, 4093))
        // Nổ lốp (mức ĐỎ) đứng TRƯỚC chữ TPMS khi cụm đỏ (nổ lốp nặng hơn "non").
        assertEquals(TyreStatus.BURST, j(TyreJudge.COLOUR_RED, 4092, ps = TyreJudge.PRESSURE_UNDER).status)
        // "Bất thường" chỉ thay chữ CHUNG — chữ TPMS cụ thể (mã [ĐO source] của SDK) không bị che.
        assertEquals(TyreStatus.UNDER, j(TyreJudge.COLOUR_YELLOW, 4093, ps = TyreJudge.PRESSURE_UNDER).status)
        assertEquals(TyreStatus.LEAK_FAST, j(TyreJudge.COLOUR_RED, 4093, lk = TyreJudge.LEAK_FAST).status)
    }

    /** Senior review 2.93 Pass 1 — chặn báo động giả: mã từ getter TPMS ([SUY] cùng bộ mã) không được nói NẶNG hơn màu cụm. */
    @Test
    fun `mau cum KHONG cung chieu - khong bao gio noi no lop`() {
        // Cụm TRẮNG = lời phán [ĐO] "bình thường" ⇒ không chữ nào, kể cả 4092/4093 (bản Pass 0 in "nổ lốp" màu thường).
        assertEquals(TyreJudgement(TyreStatus.OK, TyreSeverity.OK, TyreSource.CLUSTER), j(TyreJudge.COLOUR_WHITE, 4092))
        assertEquals(TyreJudgement(TyreStatus.OK, TyreSeverity.OK, TyreSource.CLUSTER), j(TyreJudge.COLOUR_WHITE, 4093))
        // Cụm VÀNG + 4092: L3 ghép VÀNG ↔ icon "bất thường", ĐỎ ↔ icon "nổ lốp" (`TyreCardView.java:210-216`) ⇒ không leo chữ.
        assertEquals(TyreStatus.CAR_WARN, j(TyreJudge.COLOUR_YELLOW, 4092).status)
        assertEquals(TyreStatus.UNDER, j(TyreJudge.COLOUR_YELLOW, 4092, ps = TyreJudge.PRESSURE_UNDER).status)
        // Kết luận một dòng (cũng là câu trả lời giọng nói "lốp ổn không") không bao giờ có "nổ lốp" khi cả 4 cụm TRẮNG.
        val white = CarStatus.Tyres(cFl = 1, cFr = 1, cRl = 1, cRr = 1, pcFl = 4092, pcFr = 4092, pcRl = 4093, pcRr = 4092)
        assertEquals("lốp ổn", TyreBoard.verdict(TyreBoard.readings(white)))
    }

    @Test
    fun `khong mau cum thi ma ap suat KHONG noi gi - dung cong cua L3`() {
        // Màu cụm 0 (COLOR_INVALID) đã thành null ở tầng đọc ⇒ M2–M7 như 2.88, không chữ nổ lốp.
        assertEquals(TyreStatus.UNKNOWN, j(null, 4092).status)
        assertEquals(TyreStatus.UNDER, j(null, 4093, ps = TyreJudge.PRESSURE_UNDER).status)
        // Mã "chưa có số" không phải mã báo.
        assertEquals(TyreStatus.CAR_ALERT, j(TyreJudge.COLOUR_RED, TyreJudge.PRESSURE_NONE).status)
        assertEquals(TyreStatus.OK, j(TyreJudge.COLOUR_WHITE, TyreJudge.PRESSURE_DEFAULT).status)
        assertEquals(TyreStatus.OK, j(TyreJudge.COLOUR_WHITE, null).status, "2.88 không đổi khi không có mã")
    }

    @Test
    fun `o banh in dung chu - VI, EN, va ket luan dem dung`() {
        val t = CarStatus.Tyres(cFl = TyreJudge.COLOUR_RED, pcFl = 4092, cFr = TyreJudge.COLOUR_YELLOW, pcFr = 4093, cRl = 1, cRr = 1)
        val r = TyreBoard.readings(t)
        assertEquals(listOf("nổ lốp", "bất thường", null, null), r.map { it.reason })
        assertEquals("1 bánh nổ lốp · 1 bánh bất thường", TyreBoard.verdict(r))
        Strings.current = Lang.EN
        assertEquals(listOf("burst", "abnormal", null, null), TyreBoard.readings(t).map { it.reason })
        Strings.current = Lang.ZH
        assertEquals("爆胎", TyreBoard.readings(t)[0].reason, "bảng dịch zh có hàng")
    }

    @Test
    fun `dong log tho - pc chi in khi co ma, dong cu giu nguyen byte`() {
        val t = CarStatus.Tyres(cFl = 3, cFr = 1, cRl = 1, cRr = 1)
        val old = TyreBoard.rawLine(t)!!
        assertTrue(!old.contains("pc="), "không mã ⇒ dòng 2.88 y nguyên: $old")
        assertEquals("$old pc=4092,-,-,-", TyreBoard.rawLine(t.copy(pcFl = 4092)))
        assertEquals("TYRE raw p=0/4 c=-,-,-,-|ps=-,-,-,-|lk=-,-,-,-|sys=- → N,N,N,N src=none pc=-,-,4093,-",
            TyreBoard.rawLine(CarStatus.Tyres(pcRl = 4093)), "chỉ có mã báo vẫn là một dòng đáng ghi")
    }

    /** Đường thật: getter áp suất trả 4092, cụm ĐỎ ⇒ adapter giữ mã (cùng lượt đọc) ⇒ bảng in "nổ lốp". */
    @Test
    fun `adapter giu ma cua CHINH luot doc ap suat - khong luot HAL them`() {
        val names = listOf("LF", "RF", "LB", "RB").map { "INSTRUMENT_2IN1_${it}_TYRE_COLOR" }
        val colours = mutableMapOf<Int, String?>(500 to "int=3", 501 to "int=1", 502 to "int=1", 503 to "int=1")
        val values = mutableMapOf<String, String?>("getTyrePressureValue" to "4092")
        val gw = FakeHalGateway(getters = values, featureNames = names.mapIndexed { i, n -> n to 500 + i }.toMap(), features = colours)
        val want = (TyreIds.PRESSURE + TyreIds.RAW_STATES).toSet()
        var showing = true
        val adapter = CarDataAdapter(HalBindingTable(gw), { if (showing) want else setOf("soc") }, clock = { 0L })
        var s = adapter.readSlow(CarStatus())
        assertEquals(listOf(4092, 4092, 4092, 4092), listOf(s.tyres.pcFl, s.tyres.pcFr, s.tyres.pcRl, s.tyres.pcRr))
        assertTrue(listOf(s.tyres.pFlKpa, s.tyres.pFrKpa).all { it == null }, "áp suất vẫn '—' như 2.88")
        val r = TyreBoard.readings(s.tyres)
        assertEquals(TyreStatus.BURST, r[0].status)
        assertEquals(TyreSeverity.ALERT, r[0].severity, "màu của cụm (đỏ)")
        assertEquals(TyreStatus.OK, r[1].status, "cụm trắng ⇒ không chữ 'nổ lốp' dù getter trả 4092 (cổng cùng chiều — senior review 2.93 Pass 1)")
        assertEquals(TyreSeverity.OK, r[1].severity, "cụm trắng ⇒ màu thường")
        assertEquals(4092, s.tyres.pcFr, "mã vẫn được giữ (dòng TYRE raw … pc= — bằng chứng 🚗), chỉ không thành chữ")
        // Lốp rời màn ⇒ giữ mã cũ (cùng luật 'không hiện ⇒ prev'), không xoá về null giữa chừng.
        showing = false
        s = adapter.readSlow(s)
        assertEquals(4092, s.tyres.pcFl)
        // Số thật trở lại ⇒ mã biến mất ngay nhịp kế.
        showing = true; values["getTyrePressureValue"] = "262"
        s = adapter.readSlow(s)
        assertNull(s.tyres.pcFl)
        assertEquals(262.0, s.tyres.pFlKpa)
        assertEquals(TyreStatus.CAR_ALERT, TyreBoard.readings(s.tyres)[0].status, "hết mã ⇒ về chữ 2.88")
    }
}
