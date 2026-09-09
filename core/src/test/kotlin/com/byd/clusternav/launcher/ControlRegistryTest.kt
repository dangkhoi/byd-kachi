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
        assertFalse("wiper" in d.enabled) // có trong kho nhưng mặc định tắt
    }

    @Test fun `setEnabled them va xoa - id khong hop le thi bo qua`() {
        val d = ControlRegistry.defaultDock()
        val added = d.setEnabled("wiper", true)
        assertTrue("wiper" in added.enabled)
        val removed = added.setEnabled("wiper", false)
        assertFalse("wiper" in removed.enabled)
        assertEquals(d, d.setEnabled("khong-co", true))
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
