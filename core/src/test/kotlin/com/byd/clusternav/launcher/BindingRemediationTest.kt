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
    @Test fun `dong luc doi sang getter that - gear energy op_mode slope steering`() {
        assertEquals("BYDAutoGearboxDevice.getCurrentGear", key("gear"))
        assertEquals("BYDAutoEnergyDevice.getEnergyMode", key("energy_mode"))
        assertEquals("BYDAutoEnergyDevice.getOperationMode", key("op_mode"))
        assertEquals("BYDAutoSensorDevice.getSlope", key("slope_deg"))
        assertEquals("android.hardware.bydauto.sensor.BYDAutoSensorDevice", named("slope_deg").fqn)
        assertEquals(1, HalBindingTable.readArg("steering_deg"), "getSteeringWheelValue(BODYWORK_CMD_STEERING_WHEEL_ANGEL=1)")
        assertNull(HalBindingTable.readArg("wheel_speed"), "getWheelSpeed() là 0-arg (SpecialDevice.java:59)")
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
        assertEquals("BYDAutoInstrumentDevice.getChargePower", key("charge_power"))
        assertEquals("BYDAutoInstrumentDevice.getChargePercent", key("charging_pct"))
        assertEquals("BYDAutoChargingDevice.getChargingCapacity", key("charging_capacity_kwh"))
        assertEquals("BYDAutoChargingDevice.getChargerWorkState", key("charger_work_state"))
        assertEquals("BYDAutoChargingDevice.getChargerWorkState", key("charging_state"), "getChargeState KHÔNG tồn tại")
        assertEquals("BYDAutoOtaDevice.getBatteryVoltage", key("volt_12v"), "Power không có getBatteryVoltage")
    }

    @Test fun `dang sac = getChargerWorkState bang 2, khong phai lon hon 0`() {
        assertEquals("BYDAutoChargingDevice.getChargerWorkState", key("is_charging"))
        fun read(v: String) = HalBindingTable(FakeHalGateway(getters = mapOf("getChargerWorkState" to v))).readBool("is_charging")
        assertEquals(true, read("2"), "START=2 → đang sạc")
        assertEquals(false, read("1"), "READY=1 → chưa sạc (coerceBool cũ sẽ nói true)")
        assertEquals(false, read("3"), "FINISH=3 → không sạc")
        assertEquals(false, read("4"), "TERMINATE=4 → không sạc")
        assertNull(read("rác"))
    }

    @Test fun `con gio con phut lay phan tu 0 va 1 cua getChargeRestTime`() {
        assertEquals("BYDAutoInstrumentDevice.getChargeRestTime", key("charging_eta_hour"))
        assertEquals("BYDAutoInstrumentDevice.getChargeRestTime", key("charging_eta_min"))
        val table = HalBindingTable(FakeHalGateway(getters = mapOf("getChargeRestTime" to "[2, 35]")))   // BydHal.arrayToStr
        assertEquals(2, table.readInt("charging_eta_hour"))
        assertEquals(35, table.readInt("charging_eta_min"))
        assertNull(HalBindingTable(FakeHalGateway(getters = mapOf("getChargeRestTime" to "[I@1a2b3c"))).readInt("charging_eta_min"))
    }

    @Test fun `tam dien sentinel 1000 1023 la khong hop le`() {
        fun read(v: String) = HalBindingTable(FakeHalGateway(getters = mapOf("getElecDrivingRangeValue" to v))).readInt("ev_range_km")
        assertEquals(312, read("312"))
        assertNull(read("1000"), "STATISTIC_ELEC_DRIVING_RANGE_INVALID")
        assertNull(read("1023"), "STATISTIC_ELEC_DRIVING_RANGE_DEFAULT")
    }

    @Test fun `feature-id gan nham nghia bi go - cell_v va batt_range khong con doc ra so sai`() {
        listOf("cell_v_high", "cell_v_low", "batt_range_bodywork").forEach { id ->
            assertTrue(key(id).isNotBlank(), "$id: bindingKey không rỗng (guard registry)")
            assertEquals(BindingRoute.None, HalBindingTable.routeOf(key(id)), "$id: id cũ = tầm xăng / tốc độ vô-lăng ⇒ None")
            assertEquals(EvidenceTier.NEEDS_CAR, TelemetryRegistry.byId(id)!!.tier, id)
        }
    }

    // ── An toàn / GPS ───────────────────────────────────────────────────────────────────────────
    @Test fun `day an toan va ghe phu doc tu SafetyBelt(1042) khong phai ADAS`() {
        assertEquals("BYDAutoSafetyBeltDevice.getSafetyBeltStatus", key("seatbelt_driver"))
        assertEquals("BYDAutoSafetyBeltDevice.getSafetyBeltStatus", key("seatbelt_passenger"))
        assertEquals(1, HalBindingTable.readArg("seatbelt_driver"), "SAFETY_BELT_AREA_MAIN=1")
        assertEquals(2, HalBindingTable.readArg("seatbelt_passenger"), "SAFETY_BELT_AREA_DEPUTY=2")
        assertEquals("android.hardware.bydauto.safetybelt.BYDAutoSafetyBeltDevice", named("seatbelt_driver").fqn)
        assertEquals("BYDAutoSafetyBeltDevice.getPassengerStatus", key("oms_passenger"))
        assertEquals(1, HalBindingTable.readArg("oms_passenger"), "SAFETY_BELT_PASSENGER_DEPUTY=1")
        // oms_driver: enum PASSENGER không có ghế lái ⇒ NEEDS-ONCAR, giữ nguyên feature-id (không bịa arg).
        assertTrue(HalBindingTable.routeOf(key("oms_driver")) is BindingRoute.Feature)
    }

    @Test fun `GPS van None - BLOCKED-BY-DESIGN, khong duoc route sang LocationManager`() {
        // Quyền location đã retire (DeadReckonRetirementTest ở :app ghim manifest) sau sự cố ghim GPS toàn xe.
        // Mở lại = quyết định owner; tới lúc đó 4 mục này phải là None, và `LocationManager` KHÔNG được là Local target.
        listOf("gps_lat", "gps_lon", "gps_elevation", "gps_heading").forEach { id ->
            assertEquals(BindingRoute.None, HalBindingTable.routeOf(key(id)), id)
        }
        assertEquals(BindingRoute.None, HalBindingTable.routeOf("LocationManager.lat"))
        assertTrue("LocationManager" !in HalBindingTable.LOCAL_TARGETS)
        assertNull(HalBindingTable(FakeHalGateway()).readDouble("gps_lat"))
    }

    // ── §C: telemetry halDevice ghi đè device cho feature-read ────────────────────────────────────
    @Test fun `TelemetrySpec halDevice ghi de device theo domain cho feature-read`() {
        val byDomain = TelemetrySpec("x", "X", "", Domain.SAFETY, WidgetShape.BADGE, EvidenceTier.OVERDRIVE, "123")
        assertEquals("android.hardware.bydauto.adas.BYDAutoADASDevice", HalBindingTable.featureDeviceFor(byDomain))
        val override = byDomain.copy(halDevice = "BYDAutoSafetyBeltDevice")
        assertEquals("android.hardware.bydauto.safetybelt.BYDAutoSafetyBeltDevice", HalBindingTable.featureDeviceFor(override))
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
        // radar 8 vùng vẫn đọc CẢ mảng.
        val table = HalBindingTable(FakeHalGateway(getters = mapOf("getAllRadarProbeStates" to "[0, 1, 2, 3, 0, 0, 0, 0]")))
        assertEquals(listOf(0, 1, 2, 3, 0, 0, 0, 0), table.readIntList("radar_zones"))
    }

    // ── Ghi: avh có đường thật, headl/lock đúng trạng thái NEEDS-ONCAR ─────────────────────────
    @Test fun `avh doi tu command-wrapper sang setAVHState`() {
        val def = ControlRegistry.byId("avh")!!
        assertEquals("named:setAVHState" to "android.hardware.bydauto.adas.BYDAutoADASDevice", HalBindingTable.describeWrite(def))
        assertEquals(EvidenceTier.NEEDS_CAR, def.tier, "enum on/off chưa có nguồn ⇒ vẫn cần xe")
    }

    @Test fun `headlight_mode feature-id di device INSTRUMENT`() {
        val (route, device) = HalBindingTable.describeWrite(ControlRegistry.byId("headlight_mode")!!)
        assertEquals("feature:0x4c109038", route)
        assertEquals("android.hardware.bydauto.instrument.BYDAutoInstrumentDevice", device)
    }
}
