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

    @Test fun `telemetry picks phu du lanh vuc dang co datum va tong bang registry`() {
        // H1 · T2 (2026-09-16): datum nay KHÔNG còn nằm gọn trong [Domain.TELEMETRY] — `media_vol` (âm lượng
        // Android) là mục ĐỌC đầu tiên của [Domain.INFOTAINMENT]. Bài này vì thế đo *"bày ra đúng những lĩnh vực
        // đang có datum"* thay vì ghim đúng bảy tên: ghim tên là bắt mọi lĩnh vực mới phải sửa bài kiểm trước khi
        // được tồn tại, mà cái nó thật sự canh là *"không lĩnh vực nào có datum mà bộ chọn bỏ quên"*.
        val groups = WidgetCatalog.telemetryByDomain()
        val domains = groups.map { it.first }.toSet()
        assertEquals(TelemetryRegistry.domains(), domains, "bộ chọn phải bày đúng các lĩnh vực đang có datum")
        assertTrue(Domain.TELEMETRY.all { it in domains }, "bảy lĩnh vực gốc của catalog §A vẫn phải còn đủ")
        assertEquals(TelemetryRegistry.ALL.size, groups.sumOf { it.second.size }, "tổng pick = số datum")
    }

    @Test fun `pick tra curated roi telemetry roi null`() {
        assertEquals("w_energy", WidgetCatalog.pick("w_energy")?.id)
        assertNotNull(WidgetCatalog.pick("soc"))                 // telemetry
        assertFalse(WidgetCatalog.pick("motor_power")!!.needsBadge)  // 2026-09-21 badge bỏ hẳn
        assertFalse(WidgetCatalog.pick("soc")!!.needsBadge)          // PROVEN
        assertNull(WidgetCatalog.pick("khong_co"))
    }

    @Test fun `control panels co drive HUD rieng va tong bang registry`() {
        val panels = ControlPanels.byDomain()
        val byDomain = panels.toMap()
        // ⚠ Panel SAFETY (ADAS) đã gỡ 2026-09-16 cùng cả `Domain.SAFETY` — owner gỡ toàn bộ ADAS/an toàn.
        assertTrue(Domain.values().none { it.name == "SAFETY" }, "không được có panel ADAS/an toàn nào mọc lại")
        // ⚠ (V) FEATURE-FILTER 2026-09-17: mốc cũ là `drive_mode` — nút đó đã gỡ (owner chấm NO). Nút DRIVETRAIN
        // còn sống lấy làm mốc: `powertrain_mode` (EV / HEV).
        assertTrue(byDomain[Domain.DRIVETRAIN]!!.any { it.id == "powertrain_mode" }, "EV/HEV panel DRIVETRAIN")
        // ⚠ WP8 2026-09-20: hai nút HUD (`hud_switch` · `hud_brightness`) purge theo triage owner (#62 · #63 —
        // HUD kính lái là cổng coding firmware của XE, ADR 0002, nên nút trong app là nút chết). Mốc INFOTAINMENT
        // nay là `cast` (chiếu cụm) — nút này vẫn sống, chỉ ẩn khỏi bộ chọn (HIDDEN_FROM_PICKER).
        assertTrue(byDomain[Domain.INFOTAINMENT]!!.any { it.id == "cast" }, "cast panel INFOTAINMENT")
        assertEquals(ControlRegistry.ALL.size, panels.sumOf { it.second.size }, "tổng nút = registry (không sót)")
    }

    @Test fun `select cycle vong va nhan dung`() {
        // ⚠ (V) 2026-09-17: mốc cũ `drive_mode` đã gỡ ⇒ dùng `headlight_mode` (SELECT 4 lựa chọn: Tắt/Auto/Đỗ/Cốt).
        val def = ControlRegistry.byId("headlight_mode")!!   // args: Tắt/Auto/Đỗ/Cốt
        assertEquals(1, ControlTileLogic.nextSelectIndex(0, def.args.size))
        assertEquals(0, ControlTileLogic.nextSelectIndex(def.args.size - 1, def.args.size))   // vòng lại
        assertEquals("Auto", ControlTileLogic.selectLabel(def, 1))
        assertEquals(def.label, ControlTileLogic.selectLabel(def, 99))   // ngoài phạm vi → nhãn nút
        assertEquals(0, ControlTileLogic.nextSelectIndex(0, 0))          // rỗng an toàn
    }

    @Test fun `needsBadge da bo hoan toan 2026-09-21`() {
        // Tier vẫn là dữ liệu (tra ControlDef.tier), nhưng ControlTileLogic.needsBadge nay luôn false.
        assertFalse(ControlTileLogic.needsBadge(ControlRegistry.byId("defrost")!!))      // OVERDRIVE
        assertFalse(ControlTileLogic.needsBadge(ControlRegistry.byId("pm25")!!))         // PROVEN
        assertFalse(ControlTileLogic.needsBadge(ControlRegistry.byId("cast")!!))         // DASHCAST
    }

    @Test fun `NoCar port off-car deu no-op false + du lieu null`() {
        assertFalse(NoCar.toggle("pm25", true))
        assertFalse(NoCar.step("fan", 3))
        assertFalse(NoCar.cover("win_lf", true))
        assertFalse(NoCar.select("headlight_mode", 1))
        assertFalse(NoCar.press("pm25_clean_now"))
        assertNull(NoCar.batteryPercent())
        assertNull(NoCar.pm25Level())
    }
}
