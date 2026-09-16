package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** W1c/W1e: catalog cho màn Tuỳ biến (widget picks + control panels theo domain + tile logic). Thuần. */
class LauncherCatalogTest {

    @Test fun `curated widget picks khop WidgetRegistry`() {
        assertEquals(WidgetRegistry.ALL.map { it.id }, WidgetCatalog.CURATED.map { it.id })
        assertTrue(WidgetCatalog.CURATED.all { it.tier == EvidenceTier.PROVEN })
    }

    @Test fun `telemetry picks phu 8 domain va tong bang registry`() {
        val groups = WidgetCatalog.telemetryByDomain()
        val domains = groups.map { it.first }.toSet()
        assertEquals(Domain.TELEMETRY.toSet(), domains, "telemetry phải phủ đúng 8 domain")
        assertEquals(TelemetryRegistry.ALL.size, groups.sumOf { it.second.size }, "tổng pick = số datum")
    }

    @Test fun `pick tra curated roi telemetry roi null`() {
        assertEquals("w_energy", WidgetCatalog.pick("w_energy")?.id)
        assertNotNull(WidgetCatalog.pick("soc"))                 // telemetry
        assertTrue(WidgetCatalog.pick("motor_power")!!.needsBadge)   // OVERDRIVE
        assertFalse(WidgetCatalog.pick("soc")!!.needsBadge)          // PROVEN
        assertNull(WidgetCatalog.pick("khong_co"))
    }

    @Test fun `control panels co drive HUD rieng va tong bang registry`() {
        val panels = ControlPanels.byDomain()
        val byDomain = panels.toMap()
        // ⚠ Panel SAFETY (ADAS) đã gỡ 2026-09-16 cùng cả `Domain.SAFETY` — owner gỡ toàn bộ ADAS/an toàn.
        assertTrue(Domain.values().none { it.name == "SAFETY" }, "không được có panel ADAS/an toàn nào mọc lại")
        assertTrue(byDomain[Domain.DRIVETRAIN]!!.any { it.id == "drive_mode" }, "chế độ lái panel DRIVETRAIN")
        assertTrue(byDomain[Domain.INFOTAINMENT]!!.any { it.id == "hud_switch" }, "HUD panel INFOTAINMENT")
        assertEquals(ControlRegistry.ALL.size, panels.sumOf { it.second.size }, "tổng nút = registry (không sót)")
    }

    @Test fun `select cycle vong va nhan dung`() {
        val def = ControlRegistry.byId("drive_mode")!!   // args: Thường/Eco/Thể thao/Tuyết
        assertEquals(1, ControlTileLogic.nextSelectIndex(0, def.args.size))
        assertEquals(0, ControlTileLogic.nextSelectIndex(def.args.size - 1, def.args.size))   // vòng lại
        assertEquals("Eco", ControlTileLogic.selectLabel(def, 1))
        assertEquals(def.label, ControlTileLogic.selectLabel(def, 99))   // ngoài phạm vi → nhãn nút
        assertEquals(0, ControlTileLogic.nextSelectIndex(0, 0))          // rỗng an toàn
    }

    @Test fun `needsBadge theo tier`() {
        assertTrue(ControlTileLogic.needsBadge(ControlRegistry.byId("defrost")!!))       // OVERDRIVE
        assertFalse(ControlTileLogic.needsBadge(ControlRegistry.byId("pm25")!!))         // PROVEN
        assertTrue(ControlTileLogic.needsBadge(ControlRegistry.byId("cast")!!))          // DASHCAST
    }

    @Test fun `NoCar port off-car deu no-op false + du lieu null`() {
        assertFalse(NoCar.toggle("pm25", true))
        assertFalse(NoCar.step("fan", 3))
        assertFalse(NoCar.cover("win_lf", true))
        assertFalse(NoCar.select("drive_mode", 1))
        assertFalse(NoCar.press("pm25_clean_now"))
        assertNull(NoCar.batteryPercent())
        assertNull(NoCar.pm25Level())
    }
}
