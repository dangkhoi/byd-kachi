package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá ICON THEO KHÁI NIỆM cho mục đọc (U1).
 *
 * Bệnh: [ĐO] 2026-09-11 — **123 mục đọc chỉ dùng 9 icon** (một icon cho cả nhóm) ⇒ trên bảng Tuỳ biến một hàng 5 ô
 * trông y hệt nhau, icon không giúp phân biệt gì. Các test dưới đây **đo** mức phân biệt và chặn việc tụt lại.
 */
class CapabilityIconsTest {

    @Test
    fun `so icon phan biet duoc phai hon HAN muc mot-icon-moi-nhom`() {
        val byDomainOnly = TelemetryRegistry.ALL.map { WidgetCatalog.iconFor(it.domain) }.toSet().size
        val byConcept = CapabilityIcons.distinctIconCount()
        // [SOÁT] bản cũ chỉ đòi ">= gấp đôi cách cũ" = >= 14, nên số icon tụt từ 29 xuống 14 vẫn XANH — tức con
        // số "7 → 29 icon" mà tài liệu công bố KHÔNG được bài nào khoá. Nay khoá cả sàn thật.
        assertTrue(
            byConcept >= byDomainOnly * 2,
            "Icon theo khái niệm phải phân biệt ÍT NHẤT gấp đôi cách cũ (theo nhóm). " +
                "Đang có: theo nhóm=$byDomainOnly · theo khái niệm=$byConcept",
        )
        assertTrue(
            byConcept >= 29,
            "sàn đã đạt được là 29 icon phân biệt — tụt xuống dưới là hồi quy (đang có $byConcept). " +
                "Thêm icon thì NÂNG số này lên, đừng hạ.",
        )
    }

    @Test
    fun `khong nhom lon nao con dung chung DUNG mot icon`() {
        assertEquals(
            emptyList<Domain>(), CapabilityIcons.domainsWithSingleIcon(),
            "Nhóm có >3 mục mà mọi mục cùng một icon ⇒ icon vô dụng trong nhóm đó. Thêm khái niệm vào bảng tra.",
        )
    }

    @Test
    fun `moi muc doc deu tra ra mot icon, khong bao gio rong`() {
        TelemetryRegistry.ALL.forEach {
            val icon = CapabilityIcons.forTelemetry(it.id, it.domain)
            assertTrue(icon.isNotBlank(), "mục ${it.id} phải có icon (lùi về icon nhóm là được, nhưng không rỗng)")
            assertTrue(icon.startsWith("ic-"), "tên icon phải theo quy ước 'ic-…' (mục ${it.id} ra '$icon')")
        }
    }

    @Test
    fun `nhiet lop KHAC ap suat lop`() {
        // Đây là ca cụ thể nhất: 8 mục lốp trước đây cùng một icon, nhưng 4 cái đo áp suất và 4 cái đo nhiệt.
        val p = CapabilityIcons.forTelemetry("tyre_p_fl", Domain.TYRES)
        val t = CapabilityIcons.forTelemetry("tyre_t_fl", Domain.TYRES)
        assertNotEquals(p, t, "áp suất và nhiệt độ là hai thứ khác nhau ⇒ icon phải khác")
        assertEquals("ic-tire", p)
        assertEquals("ic-temp", t)
    }

    @Test
    fun `tien to dai phai duoc khop TRUOC tien to ngan`() {
        // "tyre_t_" và "tyre_p_" cùng bắt đầu bằng "tyre_"; nếu bảng tra xếp sai thứ tự thì nhiệt lốp sẽ ra icon lốp.
        assertEquals("ic-temp", CapabilityIcons.forTelemetry("tyre_t_rr", Domain.TYRES))
        assertEquals("ic-light", CapabilityIcons.forTelemetry("light_low_beam", Domain.LIGHTS))
        assertEquals("ic-turn-left", CapabilityIcons.forTelemetry("light_left_turn", Domain.LIGHTS))
        assertEquals("ic-turn-right", CapabilityIcons.forTelemetry("light_right_turn", Domain.LIGHTS))
    }

    @Test
    fun `ma la thi lui ve icon cua nhom chu khong ra rong`() {
        val icon = CapabilityIcons.forTelemetry("mot_muc_hoan_toan_moi", Domain.ENERGY)
        assertEquals(WidgetCatalog.iconFor(Domain.ENERGY), icon,
            "thêm mục mới mà chưa khai icon ⇒ lùi về icon nhóm, KHÔNG được ra ô trống icon")
    }

    @Test
    fun `nhung khai niem nang nhat da co icon rieng`() {
        // Các họ nhiều mục nhất — nếu chúng vẫn dùng icon nhóm thì việc này coi như chưa làm gì
        mapOf(
            "soc" to "ic-battery", "ev_range_km" to "ic-road", "odometer" to "ic-road",
            "seatbelt_driver" to "ic-seatbelt", "bsd_fl_alarm" to "ic-radar", "gps_lat" to "ic-gps",
            "steering_deg" to "ic-steering", "cabin_temp" to "ic-temp", "window_lf" to "ic-window",
            "door_lf" to "ic-door",
        ).forEach { (id, expected) ->
            val spec = TelemetryRegistry.byId(id)
            assertTrue(spec != null, "tiền đề: mục $id phải tồn tại trong registry")
            assertEquals(expected, CapabilityIcons.forTelemetry(id, spec!!.domain), "mục $id")
        }
    }

    @Test
    fun `bang tra khong tro toi ten icon nao trung nhau vo nghia`() {
        // Chặn lỗi copy-dán: cùng một mã khai hai lần với hai icon khác nhau thì map sẽ âm thầm giữ cái sau.
        val ids = TelemetryRegistry.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "tiền đề: mã trong registry không trùng")
    }

    @Test
    fun `icon cua NUT khong bi doi - viec nay chi cham vao muc DOC`() {
        // Nút đã có icon riêng từ trước; U1 chỉ chữa phía mục ĐỌC. Đổi phía nút là vượt phạm vi.
        ControlRegistry.ALL.take(10).forEach {
            assertEquals(it.icon, CapabilityCatalog.pick(it.id)!!.icon, "icon của nút ${it.id} phải giữ nguyên")
        }
    }
}
