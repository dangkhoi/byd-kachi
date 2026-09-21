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
        // ⚠⚠ 1.90 2026-09-21: `powertrain_mode` (mốc DRIVETRAIN cũ, sau `drive_mode`) đã xoá — xe thuần điện ⇒
        // lĩnh vực Động lực **không còn nút nào**, nên `byDomain` không có khoá đó. Đó là kết luận đúng; bài
        // `ControlRegistryExtendedTest.control gom nhieu domain` canh riêng điều này.
        assertTrue(byDomain[Domain.DRIVETRAIN].isNullOrEmpty(), "Động lực không còn nút nào (1.90)")
        // ⚠ WP8 2026-09-20: hai nút HUD (`hud_switch` · `hud_brightness`) purge theo triage owner (#62 · #63).
        // ⚠ 1.90: mốc INFOTAINMENT `cast` cũng xoá ⇒ dùng `cam` (Camera 360) — nút INFOTAINMENT còn sống.
        assertTrue(byDomain[Domain.INFOTAINMENT]!!.any { it.id == "cam" }, "cam panel INFOTAINMENT")
        assertEquals(ControlRegistry.ALL.size, panels.sumOf { it.second.size }, "tổng nút = registry (không sót)")
    }

    @Test fun `select cycle vong va nhan dung`() {
        // ⚠ (V) 2026-09-17: mốc cũ `drive_mode` đã gỡ ⇒ `headlight_mode`. ⚠ 1.90 2026-09-21: mốc đó cũng xoá ⇒
        // dùng `seatc` (SELECT 3 lựa chọn: Tắt/Mức 1/Mức 2) — vẫn phủ đúng phép vòng + tra nhãn theo chỉ số.
        val def = ControlRegistry.byId("seatc")!!   // args: Tắt/Mức 1/Mức 2
        assertEquals(1, ControlTileLogic.nextSelectIndex(0, def.args.size))
        assertEquals(0, ControlTileLogic.nextSelectIndex(def.args.size - 1, def.args.size))   // vòng lại
        assertEquals("Mức 1", ControlTileLogic.selectLabel(def, 1))
        assertEquals(def.label, ControlTileLogic.selectLabel(def, 99))   // ngoài phạm vi → nhãn nút
        assertEquals(0, ControlTileLogic.nextSelectIndex(0, 0))          // rỗng an toàn
    }

    @Test fun `needsBadge da bo hoan toan 2026-09-21`() {
        // Tier vẫn là dữ liệu (tra ControlDef.tier), nhưng ControlTileLogic.needsBadge nay luôn false.
        assertFalse(ControlTileLogic.needsBadge(ControlRegistry.byId("defrost")!!))      // OVERDRIVE
        assertFalse(ControlTileLogic.needsBadge(ControlRegistry.byId("pm25")!!))         // PROVEN
        // ⚠ 1.90: vế DASHCAST gỡ — `cast` là mã duy nhất mang tier ấy và owner đã xoá nút (xem CarCapabilitiesTest).
        assertFalse(ControlTileLogic.needsBadge(ControlRegistry.byId("headl")!!))        // NEEDS_CAR
    }

    @Test fun `NoCar port off-car deu no-op false + du lieu null`() {
        assertFalse(NoCar.toggle("pm25", true))
        assertFalse(NoCar.step("fan", 3))
        assertFalse(NoCar.cover("win_lf", true))
        assertFalse(NoCar.select("seatc", 1))   // ⚠ 1.90: mốc cũ `headlight_mode` đã xoá
        assertFalse(NoCar.press("pm25_clean_now"))
        assertNull(NoCar.batteryPercent())
        assertNull(NoCar.pm25Level())
    }
}
