package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá **bản vá binding off-car 2026-09-15** (`docs/diagnostics/hal-binding-remediation-2026-09-15.md`) phía ĐỌC:
 * bindingKey + readArg + device + coerce đúng như doc (mỗi mục có file:line stub ở registry/HalBindingTable).
 *
 * Vì sao là test riêng: chuyến on-car 2026-09-15 chấm 121/187 KHÔNG, phần lớn vì **tên method sai / arg thiếu /
 * device sai** — toàn bộ chứng minh được off-car bằng stub, không cần xe. Mỗi bài dưới đây là một họ lỗi đã đo;
 * đổi registry mà đỏ ở đây tức là quay lại đúng lỗi cũ. KHÔNG chứng minh xe trả giá trị (phần đó = T-BRIDGE `hal get`).
 */
class BindingRemediationTest {

    private fun key(id: String) = requireNotNull(TelemetryRegistry.byId(id)) { "không có datum $id" }.bindingKey
    private fun named(id: String) = HalBindingTable.routeOf(key(id)) as BindingRoute.NamedMethod

    // ── Đèn ─────────────────────────────────────────────────────────────────────────────────────
    @Test fun `den cot pha doc cung getLightStatus voi type LOW=2 HIGH=3`() {
        assertEquals("BYDAutoLightDevice.getLightStatus", key("light_low_beam"))
        assertEquals("BYDAutoLightDevice.getLightStatus", key("light_high_beam"))
        assertEquals(2, HalBindingTable.readArg("light_low_beam"))
        assertEquals(3, HalBindingTable.readArg("light_high_beam"))
        // Siblings cũ không đổi (SIDE=1/L_TURN=4/R_TURN=5/F_FOG=6/R_FOG=7).
        assertEquals(1, HalBindingTable.readArg("light_side"))
        assertEquals(7, HalBindingTable.readArg("light_rear_fog"))
        // Dispatch thật: gateway giả nhận đúng arg.
        val gw = FakeHalGateway(getters = mapOf("getLightStatus" to "1"))
        assertEquals(1, HalBindingTable(gw).readInt("light_high_beam"))
        assertEquals(3, gw.getterArgs["getLightStatus"])
    }

    // ── Động lực ───────────────────────────────────────────────────────────────────────────────
    /**
     * ⚠ UX-OVERHAUL · WP8 2026-09-20 — ba mốc `slope_deg` · `steering_deg` · `wheel_speed` đã **purge** (nằm trong
     * 14 mã của khe #5-18) ⇒ rời bài.
     * ⚠⚠ 1.90 2026-09-21 — hai mốc `energy_mode` · `op_mode` cũng **xoá** (owner: xe thuần điện) ⇒ bài còn đúng
     * MỘT mốc là `gear`. Tính chất cần canh KHÔNG đổi: đường đọc phải là **named-method THẬT**, không phải một
     * feature-id đoán; và `gear` vẫn là mục #19 của nhóm CẦN, nên bài vẫn có việc.
     */
    @Test fun `dong luc doi sang getter that - gear`() {
        assertEquals("BYDAutoGearboxDevice.getCurrentGear", key("gear"))
        assertEquals("android.hardware.bydauto.gearbox.BYDAutoGearboxDevice", named("gear").fqn)
    }

    // ── Khí hậu ─────────────────────────────────────────────────────────────────────────────────
    @Test fun `khi hau ten method dung - ac_wind ac_cycle temp_unit anion`() {
        assertEquals("BYDAutoAcDevice.getAcWindLevel", key("ac_wind"))
        assertEquals("BYDAutoAcDevice.getAcCycleMode", key("ac_cycle"))
        assertEquals("BYDAutoAcDevice.getTemperatureUnit", key("temp_unit"))
        assertTrue(HalBindingTable.routeOf(key("temp_unit")) is BindingRoute.NamedMethod, "không còn route car-setting (luôn null)")
        assertEquals("BYDAutoPM2p5Device.getPM2p5AnionState", key("anion_state"))
        assertEquals("android.hardware.bydauto.pm2p5.BYDAutoPM2p5Device", named("anion_state").fqn)
    }

    // ── Lốp + thân xe ───────────────────────────────────────────────────────────────────────────
    @Test fun `ap lop 4 goc dung getTyrePressureValue(area) 1-4`() {
        listOf("tyre_p_fl" to 1, "tyre_p_fr" to 2, "tyre_p_rl" to 3, "tyre_p_rr" to 4).forEach { (id, area) ->
            assertEquals("BYDAutoTyreDevice.getTyrePressureValue", key(id), id)
            assertEquals(area, HalBindingTable.readArg(id), id)
        }
        val gw = FakeHalGateway(getters = mapOf("getTyrePressureValue" to "240"))
        assertEquals(240, HalBindingTable(gw).readInt("tyre_p_rl"))
        assertEquals(3, gw.getterArgs["getTyrePressureValue"])
    }

    @Test fun `cua 4 goc dung getDoorState(area) va bao dong getAlarmState`() {
        listOf("door_lf" to 1, "door_rf" to 2, "door_lr" to 3, "door_rr" to 4).forEach { (id, area) ->
            assertEquals("BYDAutoBodyworkDevice.getDoorState", key(id), id)
            assertEquals(area, HalBindingTable.readArg(id), id)
        }
        assertEquals("BYDAutoBodyworkDevice.getAlarmState", key("emergency_alarm"))
    }

    // ── Năng lượng ──────────────────────────────────────────────────────────────────────────────
    @Test fun `nang luong ten method dung theo device`() {
        assertEquals("BYDAutoStatisticDevice.getFuelDrivingRangeValue", key("fuel_range_km"))
        assertEquals("BYDAutoStatisticDevice.getFuelPercentageValue", key("fuel_pct"))
        assertEquals("BYDAutoStatisticDevice.getEVMileageValue", key("ev_mileage_km"))
        // ⚠ (V) FEATURE-FILTER 2026-09-17: năm dòng SẠC (`charge_power` · `charging_pct` · `charging_capacity_kwh`
        // · `charger_work_state` · `charging_state`) đã gỡ cùng datum — owner chấm NO cho cả cụm sạc.
        assertEquals("BYDAutoOtaDevice.getBatteryVoltage", key("volt_12v"), "Power không có getBatteryVoltage")
    }

    // ⚠ (V) FEATURE-FILTER 2026-09-17 — hai bài đã GỠ cùng chủ của chúng (owner chấm NO cho cả cụm sạc):
    //  • `dang sac = getChargerWorkState bang 2, khong phai lon hon 0` — khoá bảng `BOOL_WHEN_EQUALS` cho
    //    `is_charging`; cả datum lẫn bảng đều không còn.
    //  • `con gio con phut lay phan tu 0 va 1 cua getChargeRestTime` — khoá bảng `ARRAY_INDEX` cho
    //    `charging_eta_hour`/`charging_eta_min`; cả hai datum lẫn bảng đều không còn.
    // Hai cơ chế ấy nay không có chủ nào ⇒ đã xoá khỏi `HalReadTables`/`HalBindingTable`, không để bảng rỗng.

    @Test fun `tam dien sentinel 1000 1023 la khong hop le`() {
        fun read(v: String) = HalBindingTable(FakeHalGateway(getters = mapOf("getElecDrivingRangeValue" to v))).readInt("ev_range_km")
        assertEquals(312, read("312"))
        assertNull(read("1000"), "STATISTIC_ELEC_DRIVING_RANGE_INVALID")
        assertNull(read("1023"), "STATISTIC_ELEC_DRIVING_RANGE_DEFAULT")
    }

    /**
     * ⚠⚠ UX-OVERHAUL · WP8 2026-09-20 — bài này **đảo chiều**. Cả ba thành viên của nó đã rời registry:
     * `batt_range_bodywork` ở (V) 2026-09-17, rồi `cell_v_high`/`cell_v_low` ở WP8 (#5-18). Ba mã ấy sinh ra bài
     * này vì chúng bind vào một feature-id **của việc khác** (atom TẦM XĂNG), nên đường đọc phải là `None` để
     * không đọc ra số sai. Nay chúng bị xoá hẳn — đó là cách chữa MẠNH hơn, và bài canh phải nói đúng điều ấy:
     * ba mã KHÔNG được mọc lại (mọc lại là mang theo cả cái id sai).
     */
    @Test fun `ba ma bind vao id cua viec khac da bi XOA, khong duoc moc lai`() {
        listOf("cell_v_high", "cell_v_low", "batt_range_bodywork").forEach { id ->
            assertNull(
                TelemetryRegistry.byId(id),
                "$id đã xoá (id cũ = atom tầm xăng, đọc ra số sai) — mọc lại là mang theo cả id sai",
            )
        }
    }

    // ── GPS ─────────────────────────────────────────────────────────────────────────────────────
    // ⚠ Bài `day an toan va ghe phu doc tu SafetyBelt(1042)` đã gỡ 2026-09-16: bốn datum nó khoá
    // (`seatbelt_driver` · `seatbelt_passenger` · `oms_driver` · `oms_passenger`) không còn tồn tại sau khi
    // owner gỡ toàn bộ ADAS/an toàn khỏi launcher.
    /**
     * ⚠ UX-OVERHAUL · WP8 2026-09-20 — bốn datum GPS đã **XOÁ** (#53-56): `BYDAutoLocationDevice` chỉ có setter
     * (app ĐẨY toạ độ xuống xe), nên chưa bao giờ có đường đọc, và automation dẫn-đường-theo-lịch của 1.85 dùng
     * `LocationManager` của Android. Phần **an toàn** của bài thì GIỮ NGUYÊN và còn quan trọng hơn trước: xoá
     * datum không được hiểu thành "nay route sang LocationManager cũng được".
     */
    @Test fun `GPS da XOA - va LocationManager KHONG duoc thanh Local target`() {
        listOf("gps_lat", "gps_lon", "gps_elevation", "gps_heading").forEach { id ->
            assertNull(TelemetryRegistry.byId(id), "$id đã xoá ở WP8 — HAL không có đường đọc GPS")
        }
        assertEquals(BindingRoute.None, HalBindingTable.routeOf("LocationManager.lat"))
        assertTrue("LocationManager" !in HalBindingTable.LOCAL_TARGETS)
    }

    // ── §C: telemetry halDevice ghi đè device cho feature-read ────────────────────────────────────
    @Test fun `TelemetrySpec halDevice ghi de device theo domain cho feature-read`() {
        val byDomain = TelemetrySpec("x", "X", "", Domain.CLIMATE, WidgetShape.BADGE, EvidenceTier.OVERDRIVE, "123")
        assertEquals("android.hardware.bydauto.ac.BYDAutoAcDevice", HalBindingTable.featureDeviceFor(byDomain))
        val override = byDomain.copy(halDevice = "BYDAutoPM2p5Device")
        assertEquals("android.hardware.bydauto.pm2p5.BYDAutoPM2p5Device", HalBindingTable.featureDeviceFor(override))
        // Đường thật: ControlDef.halDevice vẫn được tôn trọng cho id vừa đọc vừa ghi (readl → SETTING).
        assertEquals("BYDAutoSettingDevice", ControlRegistry.byId("readl")!!.halDevice)
    }

    // ── §B: mảng từ gateway ─────────────────────────────────────────────────────────────────────
    @Test fun `coerceInt coerceDouble lay phan tu dau khi gateway tra mang`() {
        assertEquals(12, HalBindingTable.coerceInt("[12, 3, 0, 0]"), "PM2.5/wheel_speed int[]/byte[] → [0]")
        assertEquals(12.5, HalBindingTable.coerceDouble("[12.5, 3]"))
        assertNull(HalBindingTable.coerceInt("[]"))
        assertNull(HalBindingTable.coerceInt("[I@6f2b958e"), "toString mặc định của mảng vẫn là rác, không đoán")
        assertEquals(7, HalBindingTable.coerceInt("7"), "1 phần tử gateway đã trả số trần")
        // ⚠ (V) FEATURE-FILTER 2026-09-17: nửa sau của bài — `readIntList("charging_eta_hour")` trả `[2, 35]` —
        // đã gỡ cùng `readIntList`/`ARRAY_INDEX` (chủ duy nhất của chúng là hai ô thời-gian-sạc owner chấm NO).
        // Phần còn lại vẫn khoá đúng cái đang sống: gateway trả mảng ⇒ `coerceInt` lấy phần tử ĐẦU, rác vẫn ra null.
    }

    // ⚠ Bài `avh doi tu command-wrapper sang setAVHState` đã gỡ 2026-09-16 cùng nút `avh` (owner gỡ ADAS/an toàn).

    // ⚠ 1.90 2026-09-21: bài `headlight_mode feature-id di device INSTRUMENT` đã gỡ cùng nút (owner xoá
    // `headlight_mode`). Bất biến *"halDevice ghi đè route thô theo Domain"* vẫn được canh ở
    // `HalBindingTableTest.describeWrite feature-id gives hex label plus device by domain` (vế `readl`: LIGHTS → SETTING).
}
