package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá lớp ĐƠN VỊ do người dùng chọn (spec `kachi-unified-capability-tile.html` §4.6, R11–R13).
 *
 * Điểm quan trọng nhất là **R12**: ai không đổi gì thì KHÔNG thấy khác biệt nào. Test `mặc định …` dưới đây là cái
 * chặn việc "cải tiến" định dạng làm đổi thứ người dùng đang thấy.
 */
class UnitsTest {

    // ── R11: chọn theo LOẠI, một lựa chọn ăn cho mọi mục cùng loại ───────────────────────────────

    @Test
    fun `suy loai dai luong tu chuoi don vi da khai trong registry`() {
        assertEquals(Quantity.PRESSURE, Units.quantityOf("kPa"), "kPa phải là áp suất")
        assertEquals(Quantity.TEMPERATURE, Units.quantityOf("°C"), "°C phải là nhiệt độ")
        assertEquals(Quantity.DISTANCE, Units.quantityOf("km"), "km phải là khoảng cách")
        assertEquals(Quantity.SPEED, Units.quantityOf("km/h"), "km/h phải là tốc độ")
    }

    @Test
    fun `don vi KHONG co lua chon thay the thi khong co loai`() {
        // R13: không bày lựa chọn giả cho những thứ vô nghĩa khi đổi
        listOf("%", "°", "V", "rpm", "kWh", "kW", "h", "min", "µg/m³", "").forEach {
            assertNull(Units.quantityOf(it), "đơn vị '$it' KHÔNG được có loại (không có lựa chọn hợp lý)")
        }
    }

    @Test
    fun `doi mot lua chon thi MOI muc cung loai doi theo`() {
        val prefs = UnitPrefs().with(Quantity.PRESSURE, "psi")
        val ids = listOf("tyre_p_fl", "tyre_p_fr", "tyre_p_rl", "tyre_p_rr")
        val status = CarStatus(tyres = CarStatus.Tyres(pFlKpa = 240.0, pFrKpa = 240.0, pRlKpa = 240.0, pRrKpa = 240.0))
        ids.forEach { id ->
            val v = UnitFormat.apply(TelemetryReadout.of(id, status)!!, prefs)
            assertEquals("psi", v.unit, "$id phải theo lựa chọn psi")
        }
    }

    // ── R12: mặc định = y như cũ ─────────────────────────────────────────────────────────────────

    @Test
    fun `mac dinh KHONG doi gi ngoai ap suat sang bar`() {
        // Nhiệt độ / khoảng cách / tốc độ: mặc định TRÙNG đơn vị registry ⇒ view phải là CHÍNH object cũ
        val status = CarStatus(
            drivetrain = CarStatus.Drivetrain(speedKmh = 56),
            energy = CarStatus.Energy(evRangeKm = 418),
        )
        listOf("speed", "ev_range_km").forEach { id ->
            val before = TelemetryReadout.of(id, status)!!
            val after = UnitFormat.apply(before)
            assertEquals(before, after, "$id mặc định phải KHÔNG đổi gì")
        }
    }

    @Test
    fun `mac dinh KHONG dinh dang lai gia tri co san so le`() {
        // Bảo vệ chống hồi quy: định dạng lại theo bảng số-lẻ sẽ làm "28.5" thành "29" dù người dùng không chọn gì.
        val v = TelemetryView("x", "Nhiệt", "°C", WidgetShape.VALUE, EvidenceTier.PROVEN, "28.5")
        assertEquals("28.5", UnitFormat.apply(v).valueText, "mặc định phải giữ NGUYÊN chuỗi tầng đọc đã format")
    }

    @Test
    fun `ap suat mac dinh la bar de khop widget lop dang hien`() {
        val v = TelemetryReadout.of("tyre_p_fl", CarStatus(tyres = CarStatus.Tyres(pFlKpa = 240.0)))!!
        assertEquals("kPa", v.unit, "tầng ĐỌC phải giữ đơn vị gốc của xe")
        val shown = UnitFormat.apply(v)
        assertEquals("bar", shown.unit, "tầng TRÌNH BÀY mặc định phải là bar (khớp widget lốp hiện có)")
        assertEquals("2.4", shown.valueText, "240 kPa = 2.4 bar")
    }

    // ── R13: không bịa độ chính xác, không bịa số ────────────────────────────────────────────────

    @Test
    fun `quy doi dung so va dung so chu so thap phan`() {
        fun show(unit: String, raw: String, q: Quantity, to: String): String? =
            UnitFormat.apply(
                TelemetryView("x", "l", unit, WidgetShape.VALUE, EvidenceTier.PROVEN, raw),
                UnitPrefs().with(q, to),
            ).valueText

        assertEquals("34.8", show("kPa", "240", Quantity.PRESSURE, "psi"), "240 kPa ≈ 34.8 psi (1 số lẻ)")
        assertEquals("240", show("kPa", "240", Quantity.PRESSURE, "kPa"), "chọn đúng đơn vị gốc ⇒ giữ nguyên")
        assertEquals("77", show("°C", "25", Quantity.TEMPERATURE, "°F"), "25°C = 77°F (0 số lẻ)")
        assertEquals("62", show("km", "100", Quantity.DISTANCE, "mile"), "100 km ≈ 62 mile")
        assertEquals("62", show("km/h", "100", Quantity.SPEED, "mph"), "100 km/h ≈ 62 mph")
    }

    @Test
    fun `gia tri chua doc duoc thi van la mot gach ngang`() {
        val v = TelemetryView("x", "l", "kPa", WidgetShape.BOARD, EvidenceTier.PROVEN, null)
        val out = UnitFormat.apply(v, UnitPrefs().with(Quantity.PRESSURE, "psi"))
        assertNull(out.valueText, "chưa đọc được thì KHÔNG được bịa số")
        assertEquals("—", out.display, "hiện dấu gạch ngang")
        assertEquals("psi", out.unit, "vẫn nên cho biết đang tính theo đơn vị nào")
    }

    @Test
    fun `gia tri khong phai so thi de nguyen`() {
        // vd trạng thái "Có"/"Không" — có đơn vị rỗng, nhưng chặn cả ca đơn vị hợp lệ mà giá trị là chữ
        val v = TelemetryView("x", "l", "kPa", WidgetShape.BADGE, EvidenceTier.PROVEN, "Có")
        assertEquals(v, UnitFormat.apply(v, UnitPrefs().with(Quantity.PRESSURE, "psi")), "chữ thì để nguyên")
    }

    @Test
    fun `so khong huu han thi khong duoc in ra chuoi rac`() {
        // `String.toDoubleOrNull()` CHẤP NHẬN "NaN"/"Infinity" (đúng đặc tả Double.valueOf). Nếu không chặn thì một
        // giá trị rác từ HAL sẽ được quy đổi rồi format thành chuỗi "NaN"/"Infinity" — tức bịa ra một con số (R13).
        assertNull(Units.fromBase(Quantity.PRESSURE, Double.NaN, "psi"), "NaN ⇒ không xác định")
        assertNull(Units.fromBase(Quantity.TEMPERATURE, Double.POSITIVE_INFINITY, "°F"), "vô cực ⇒ không xác định")
        assertNull(Units.fromBase(Quantity.LENGTH, Double.MAX_VALUE, "ft"), "tràn khi nhân ⇒ không xác định")
        listOf("NaN", "Infinity", "-Infinity").forEach { junk ->
            val v = TelemetryView("x", "l", "kPa", WidgetShape.VALUE, EvidenceTier.PROVEN, junk)
            assertEquals(
                v, UnitFormat.apply(v, UnitPrefs().with(Quantity.PRESSURE, "psi")),
                "'$junk' phải để NGUYÊN, không được nhận là đã quy đổi",
            )
        }
    }

    @Test
    fun `tieu thu nghich dao va khong bia vo cuc khi chia cho khong`() {
        assertEquals(5.0, Units.fromBase(Quantity.CONSUMPTION, 20.0, "km/kWh"), "20 kWh/100km = 5 km/kWh")
        assertNull(Units.fromBase(Quantity.CONSUMPTION, 0.0, "km/kWh"), "0 ⇒ không xác định, KHÔNG trả vô cực")
    }

    @Test
    fun `ma don vi la thi bo qua khong sap`() {
        assertNull(Units.fromBase(Quantity.PRESSURE, 1.0, "atm"), "mã lạ ⇒ null")
        val p = UnitPrefs().with(Quantity.PRESSURE, "atm")
        assertEquals("bar", p.unitFor(Quantity.PRESSURE), "mã lạ bị bỏ, giữ mặc định")
    }

    // ── Lưu bền ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `luu roi doc lai duoc nguyen ven`() {
        val p = UnitPrefs().with(Quantity.PRESSURE, "psi").with(Quantity.TEMPERATURE, "°F")
        val back = UnitPrefs.decode(p.encode())
        assertEquals("psi", back.unitFor(Quantity.PRESSURE))
        assertEquals("°F", back.unitFor(Quantity.TEMPERATURE))
        assertEquals("km", back.unitFor(Quantity.DISTANCE), "loại không đổi thì vẫn mặc định")
    }

    @Test
    fun `chuoi luu rac hoac rong thi ve mac dinh`() {
        listOf(null, "", "   ", "RAC", "=x", "PRESSURE", "PRESSURE=atm", "LOAI_LA=psi").forEach {
            val p = UnitPrefs.decode(it)
            assertEquals("bar", p.unitFor(Quantity.PRESSURE), "chuỗi '$it' phải suy giảm về mặc định")
        }
    }

    @Test
    fun `chi luu loai da doi de khong phai chuyen doi du lieu khi them loai moi`() {
        assertEquals("", UnitPrefs.DEFAULT.encode(), "mặc định = chuỗi rỗng")
        assertEquals("PRESSURE=psi", UnitPrefs().with(Quantity.PRESSURE, "psi").encode())
    }

    // ── Bảng lựa chọn phải khớp thực tế registry ──────────────────────────────────────────────────

    @Test
    fun `moi loai deu co it nhat hai lua chon va phan tu dau la mac dinh`() {
        Quantity.values().forEach { q ->
            val opts = Units.options(q)
            assertTrue(opts.size >= 2, "$q phải có ≥2 lựa chọn, nếu không thì đừng bày ra")
            assertEquals(opts.first().code, Units.defaultUnit(q), "$q: phần tử đầu phải là mặc định")
            assertNotNull(Units.BASE[q], "$q phải khai đơn vị gốc")
            assertTrue(opts.any { it.code == Units.BASE[q] }, "$q: đơn vị gốc phải nằm trong danh sách lựa chọn")
        }
    }

    @Test
    fun `chi bay loai thuc su co trong registry`() {
        val inUse = UnitFormat.quantitiesInUse()
        assertTrue(inUse.isNotEmpty(), "phải có loại đang dùng")
        inUse.forEach { q ->
            assertTrue(
                TelemetryRegistry.ALL.any { Units.quantityOf(it.unit) == q },
                "$q được bày nhưng KHÔNG có mục nào trong registry dùng nó",
            )
        }
        assertTrue(Quantity.PRESSURE in inUse, "áp suất phải đang được dùng (4 mục lốp)")
        assertTrue(Quantity.TEMPERATURE in inUse, "nhiệt độ phải đang được dùng")
    }
}
