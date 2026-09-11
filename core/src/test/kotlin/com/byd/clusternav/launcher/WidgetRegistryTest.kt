package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WidgetRegistryTest {

    @Test fun `co 9 widget id duy nhat`() {
        // Chốt số lượng để ai thêm widget phải nghĩ. 8 cái đầu = bộ prototype owner đã duyệt; cái thứ 9
        // (trình chiếu ảnh) thêm ở U4 theo yêu cầu owner — xem spec kachi-wallpaper.html.
        assertEquals(9, WidgetRegistry.ALL.size)
        val ids = WidgetRegistry.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun `phan loai LOCAL vs CAR vs BOARD`() {
        assertEquals(WidgetKind.LOCAL, WidgetRegistry.byId("w_clock")!!.kind)
        assertEquals(WidgetKind.LOCAL, WidgetRegistry.byId("w_media")!!.kind)
        assertEquals(WidgetKind.CAR, WidgetRegistry.byId("w_energy")!!.kind)
        assertEquals(WidgetKind.CAR, WidgetRegistry.byId("w_tire")!!.kind)
        assertEquals(WidgetKind.CAR, WidgetRegistry.byId("w_speed")!!.kind)
        assertEquals(WidgetKind.BOARD, WidgetRegistry.byId("w_board")!!.kind)
        // Trình chiếu ảnh đọc tệp trên máy, KHÔNG phải dữ liệu xe ⇒ off-car vẫn chạy đầy đủ.
        assertEquals(WidgetKind.LOCAL, WidgetRegistry.byId("w_photos")!!.kind)
    }
}
