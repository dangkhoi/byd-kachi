package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ControlRegistryTest {

    @Test fun `default dock = viền dưới + bộ nút mặc định`() {
        val d = ControlRegistry.defaultDock()
        assertEquals(DockEdge.BOTTOM, d.edge)
        assertEquals(ControlRegistry.defaultEnabledIds(), d.enabled)
        assertTrue("temp" in d.enabled && "fan" in d.enabled && "pm25" in d.enabled)
        assertFalse("cast" in d.enabled) // có trong kho nhưng mặc định tắt (⚠ WP8: mốc cũ `wiper` đã purge)
    }

    @Test fun `setEnabled them va xoa - id khong hop le thi bo qua`() {
        val d = ControlRegistry.defaultDock()
        val added = d.setEnabled("cast", true)
        assertTrue("cast" in added.enabled)
        val removed = added.setEnabled("cast", false)
        assertFalse("cast" in removed.enabled)
        assertEquals(d, d.setEnabled("khong-co", true))
    }

    @Test fun `RW0 - thanh nut nhan CA thong tin DOC chu khong chi nut`() {
        // ⚠ Đây là CỔNG CHẶN THẬT của yêu cầu "đặt được ở cả 3 vùng" (spec Đ3/R2): trước RW0 dòng này là
        // `if (ControlRegistry.byId(id) == null) return this` ⇒ mọi mã KHÔNG phải nút bị BỎ QUA IM LẶNG, nên áp
        // suất lốp / phần trăm pin không bao giờ vào được thanh.
        // [ĐO] senior review 2026-09-10: hoàn nguyên đúng một dòng đó ⇒ CẢ 2431 bài vẫn XANH ⇒ dòng quan trọng
        // nhất của gói này KHÔNG có bài nào canh. Bài này là bài canh nó.
        val d = ControlRegistry.defaultDock()
        listOf("tyre_p_fl", "soc").forEach { readId ->
            assertEquals(CapabilityKind.READ, CapabilityCatalog.kindOf(readId), "tiền đề: '$readId' là thông tin ĐỌC")
            val on = d.setEnabled(readId, true)
            assertTrue(readId in on.enabled, "mã ĐỌC '$readId' PHẢI vào được thanh nút (R2 chiều một)")
            assertFalse(readId in on.setEnabled(readId, false).enabled, "và bỏ ra được")
        }
        // Nới KHÔNG có nghĩa là nhận rác: mã không thuộc bộ đăng ký nào vẫn bị từ chối (giữ tính chất bản cũ).
        listOf("khong_ton_tai", "", "TYRE_P_FL").forEach {
            assertEquals(d, d.setEnabled(it, true), "mã lạ '$it' KHÔNG được vào cấu hình bền")
        }
    }

    @Test fun `withEdge + isVertical`() {
        assertTrue(ControlRegistry.defaultDock().withEdge(DockEdge.LEFT).isVertical())
        assertTrue(ControlRegistry.defaultDock().withEdge(DockEdge.RIGHT).isVertical())
        assertFalse(ControlRegistry.defaultDock().withEdge(DockEdge.TOP).isVertical())
        assertFalse(ControlRegistry.defaultDock().withEdge(DockEdge.BOTTOM).isVertical())
    }

    @Test fun `step clamp theo min max`() {
        val temp = ControlRegistry.byId("temp")!!
        assertEquals(33, temp.clamp(99))
        assertEquals(17, temp.clamp(-5))
        assertEquals(22, temp.clamp(22))
        val fan = ControlRegistry.byId("fan")!!
        assertEquals(7, fan.clamp(10)); assertEquals(0, fan.clamp(-1))
    }

    @Test fun `moi control id duy nhat`() {
        val ids = ControlRegistry.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
