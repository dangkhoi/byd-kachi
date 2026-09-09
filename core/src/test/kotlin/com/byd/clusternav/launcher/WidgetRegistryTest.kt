package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WidgetRegistryTest {

    @Test fun `co 8 widget id duy nhat`() {
        assertEquals(8, WidgetRegistry.ALL.size)
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
    }
}
