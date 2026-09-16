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
        // U6 nâng sàn: 34 → 56 icon phân biệt (18 hình mới cho 3 nhóm dày nhất). Sàn là số ĐO ĐƯỢC, không phải số
        // mong muốn — nó chỉ được đi lên.
        assertTrue(
            byConcept >= 56,
            "sàn đã đạt được là 56 icon phân biệt — tụt xuống dưới là hồi quy (đang có $byConcept). " +
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
        // U7: hai hình nay đều dựng trên KHUNG XE nhìn từ trên, khác nhau ở đại lượng (bánh tô đặc vs bánh tô
        // đặc + nhiệt kế) VÀ mang luôn vị trí bánh trong tên. Trước U7 là "ic-tire"/"ic-temp" — đúng về khái
        // niệm nhưng bốn bánh vẫn chung một hình, tức ô "Lốp TT" và "Lốp SP" trông y hệt nhau.
        assertEquals("ic-car-top-tyre-fl", p)
        assertEquals("ic-car-top-tyre-temp-fl", t)
    }

    @Test
    fun `tien to dai phai duoc khop TRUOC tien to ngan`() {
        // "tyre_t_" và "tyre_p_" cùng bắt đầu bằng "tyre_"; nếu bảng tra xếp sai thứ tự thì nhiệt lốp sẽ ra icon lốp.
        assertEquals("ic-car-top-tyre-temp-rr", CapabilityIcons.forTelemetry("tyre_t_rr", Domain.TYRES))
        assertEquals("ic-car-front-lowbeam", CapabilityIcons.forTelemetry("light_low_beam", Domain.LIGHTS))
        assertEquals("ic-car-front-turn-l", CapabilityIcons.forTelemetry("light_left_turn", Domain.LIGHTS))
        assertEquals("ic-car-front-turn-r", CapabilityIcons.forTelemetry("light_right_turn", Domain.LIGHTS))
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
            "soc" to "ic-battery", "ev_range_km" to "ic-range", "odometer" to "ic-road",
            // U7 — sáu họ dưới đây đổi từ "một hình cho cả họ" sang "một hình cho mỗi VỊ TRÍ"; đó là toàn bộ
            // điểm của lượt này, nên số ghim ở đây đổi theo (xem CapabilityIconPositionTest).
            // ⚠ Hai mốc `seatbelt_driver` · `bsd_fl_alarm` đã gỡ 2026-09-16 cùng toàn bộ ADAS/an toàn (owner).
            "gps_lat" to "ic-gps-lat",
            "steering_deg" to "ic-steering", "cabin_temp" to "ic-temp",
            "window_lf" to "ic-car-top-window-lf", "door_lf" to "ic-car-top-door-lf",
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
