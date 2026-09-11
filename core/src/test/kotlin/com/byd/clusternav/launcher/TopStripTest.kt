package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RW0 **vùng thứ ba** — thanh trạng thái trên nay đọc từ không gian khả năng thay vì 3 chip viết cứng.
 *
 * Bài quan trọng nhất ở đây là bài ĐẦU TIÊN: **mặc định phải ra ĐÚNG chuỗi của bản viết cứng cũ**. Nới một bề mặt đã
 * được owner duyệt mà âm thầm đổi thứ hiện ra là loại lỗi dự án đã gặp (làm tròn bố cục 3 ô, chip bỏ qua đơn vị) —
 * "cho cấu hình được" không phải là giấy phép đổi mặc định.
 */
class TopStripTest {

    private val full = CarStatus(
        climate = CarStatus.Climate(pm25Level = 2, outsideTempC = 24),
        energy = CarStatus.Energy(soc = 82, evRangeKm = 418),
    )

    @Test
    fun `mac dinh ra DUNG ba chip cua ban viet cung cu`() {
        val chips = TopStripChips.render(TopStripConfig.DEFAULT, full)
        assertEquals(3, chips.size, "mặc định phải là đúng 3 chip như trước")
        assertEquals("PM2.5 · Tốt", chips[0].text)
        assertEquals("24°C ngoài", chips[1].text)
        assertEquals("82% · 418 km", chips[2].text)
        assertEquals(listOf("ic-leaf", null, "ic-bolt"), chips.map { it.icon }, "icon phải giữ như cũ")
        assertEquals(ChipTone.ENERGY, chips[2].tone, "chip pin vẫn là sắc thái riêng (xanh)")
    }

    @Test
    fun `off-car moi field null thi chip noi chua doc duoc chu khong bia so`() {
        val chips = TopStripChips.render(TopStripConfig.DEFAULT, CarStatus())
        assertEquals("PM2.5 · —", chips[0].text)
        assertEquals("—°C ngoài", chips[1].text)
        assertEquals("—% · — km", chips[2].text)
    }

    @Test
    fun `chip di qua lop don vi giong moi be mat khac`() {
        // [ĐO] lỗi thật ở gói 2: người dùng chọn °F mà chip vẫn ghi °C, vì bề mặt này tự dựng chuỗi từ CarStatus.
        val f = UnitPrefs.DEFAULT.with(Quantity.TEMPERATURE, "°F").with(Quantity.DISTANCE, "mile")
        val chips = TopStripChips.render(TopStripConfig.DEFAULT, full, f)
        assertTrue(chips[1].text.endsWith("°F ngoài"), "phải theo đơn vị đã chọn, thấy: ${chips[1].text}")
        assertTrue(chips[2].text.contains(" mile"), "tầm chạy phải theo đơn vị đã chọn, thấy: ${chips[2].text}")
        assertFalse(chips[1].text.contains("°C"), "không được còn đơn vị gốc")
    }

    @Test
    fun `mot datum bat ky dat duoc len thanh tren`() {
        val cfg = TopStripConfig(listOf(TopStripConfig.PM25)).setEnabled("tyre_p_fl", true)
        assertTrue(cfg.has("tyre_p_fl"))
        val chips = TopStripChips.render(cfg, CarStatus(tyres = CarStatus.Tyres(pFlKpa = 241.0)))
        assertEquals(2, chips.size)
        // Giá trị đi qua lớp đơn vị: 241 kPa ⇒ mặc định dự án là **bar** ⇒ "2.4 bar". Chip KHÔNG được hiện số thô.
        assertTrue(chips[1].text.endsWith("2.4 bar"), "chip datum phải hiện giá trị đã quy đổi, thấy: ${chips[1].text}")
        // Chip phải dùng nhãn NGẮN, không nhãn đầy: thanh trên chỉ rộng vài chục pixel, "Áp lốp trước-trái" sẽ đẩy
        // đồng hồ ra khỏi thanh. Đây là mục đích của `TelemetrySpec.short` (RW0 mục c).
        val spec = TelemetryRegistry.byId("tyre_p_fl")!!
        assertEquals("Lốp TT", spec.shortLabel, "datum bề-mặt-hẹp phải có nhãn ngắn")
        assertTrue(chips[1].text.startsWith(spec.shortLabel), "chip phải dùng nhãn ngắn, thấy: ${chips[1].text}")
        assertFalse(chips[1].text.startsWith(spec.label), "không được dùng nhãn đầy trên chip")
        assertTrue(chips[1].desc.isNotBlank(), "chip rất ngắn ⇒ phải có câu đọc cho trình đọc màn hình")
    }

    @Test
    fun `KHONG nhan nut hay goi lenh - day la quyet dinh co chu y`() {
        // Lý do ở KDoc TopStripConfig: chip ~24dp là quá nhỏ cho một đích chạm, và một cú chạm lệch có thể bắn lệnh
        // xe không hoàn lại được (vd mở khoá cửa). Đây là bài khoá **ý định**, không phải khoá hiện trạng.
        val cfg = TopStripConfig(listOf(TopStripConfig.PM25))
        assertEquals(cfg, cfg.setEnabled("recirc", true), "nút KHÔNG được lên thanh trên")
        assertEquals(cfg, cfg.setEnabled("mac_leave", true), "gói lệnh KHÔNG được lên thanh trên")
        assertEquals(cfg, cfg.setEnabled("khong_co_ma_nay", true), "mã lạ vẫn bị từ chối như DockConfig")
        assertTrue(TopStripConfig.choices().none { it.kind == CapabilityKind.WRITE }, "màn chọn cũng không được bày nút")
    }

    @Test
    fun `day tran thi bo qua chu khong day mot chip khac ra`() {
        var cfg = TopStripConfig(emptyList())
        val ids = TelemetryRegistry.ALL.take(TopStripConfig.CAP + 2).map { it.id }
        ids.forEach { cfg = cfg.setEnabled(it, true) }
        assertEquals(TopStripConfig.CAP, cfg.ids.size, "không được vượt trần")
        assertEquals(ids.take(TopStripConfig.CAP), cfg.ids, "phải giữ những cái vào TRƯỚC, không đẩy ra")
    }

    @Test
    fun `chuoi luu doc lai nguyen ven va tu chua du lieu hong`() {
        val cfg = TopStripConfig(listOf(TopStripConfig.ENERGY, "tyre_p_fl"))
        assertEquals(cfg, TopStripConfig.decode(TopStripConfig.encode(cfg)), "lưu rồi đọc phải ra y hệt")
        assertEquals(TopStripConfig.DEFAULT, TopStripConfig.decode(null), "chưa có gì ⇒ mặc định")
        assertEquals(TopStripConfig.DEFAULT, TopStripConfig.decode("   "), "rỗng ⇒ mặc định, không để thanh trên trắng")
        assertEquals(TopStripConfig.DEFAULT, TopStripConfig.decode("ma_da_bi_xoa,ma_rac"), "toàn mã lạ ⇒ mặc định")
        // Một mã lạ lẫn giữa mã tốt: bỏ MỤC đó, KHÔNG bỏ cả dòng (mất luôn cấu hình người dùng vì một mã rữa là quá tay).
        assertEquals(
            listOf(TopStripConfig.PM25, "tyre_p_fl"),
            TopStripConfig.decode(TopStripConfig.PM25 + ",ma_rac,tyre_p_fl").ids,
        )
    }

    @Test
    fun `nhan ngan tu lui ve nhan day khi chua khai`() {
        val spec = TelemetryRegistry.ALL.first { it.short == null }
        assertEquals(spec.label, spec.shortLabel, "chưa khai nhãn ngắn ⇒ phải lùi về nhãn đầy, không rỗng")
    }
}
