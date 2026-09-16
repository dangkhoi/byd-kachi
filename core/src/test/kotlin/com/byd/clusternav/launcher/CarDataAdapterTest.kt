package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** W1b: [CarDataAdapter] build [CarStatus] từ telemetry giả (named + feature), gộp 2 nhịp, 6 method cũ, off-car null. */
class CarDataAdapterTest {

    private fun adapter() = CarDataAdapter(
        HalBindingTable(
            FakeHalGateway(
                getters = mapOf(
                    "getElecPercentageValue" to "82",             // soc
                    "getElecDrivingRangeValue" to "418",          // ev_range_km
                    "getCurrentSpeed" to "56",                    // speed
                    "getWindowOpenPercent" to "98",               // window_* (per-index arg)
                    "getAutoVIN" to "LGXCE4CB0N0000001",          // vin
                    "getPM2p5Level" to "2",                       // pm25_level
                    "getOutCarTemperature" to "26",               // ext_temp
                    "getChargePercent" to "45",                   // charging_pct (Instrument, 2026-09-15)
                ),
                // Remediation 2026-09-15: áp lốp 4 góc qua MỘT getter `getTyrePressureValue(area)` (area 1..4), đèn cốt
                // qua `getLightStatus(LOW_BEAM=2)` — cùng getter với 5 đèn khác nên fake phải trả theo arg.
                gettersByArg = mapOf(
                    "getTyrePressureValue" to mapOf(1 to "240", 2 to "235", 3 to "230", 4 to "210"),   // kPa
                    "getLightStatus" to mapOf(2 to "1"),                                                   // light_low_beam
                ),
                features = mapOf(
                    1031798832 to "24",    // cabin_temp
                    339738656 to "120",    // motor_power (fast) — số của xe giả, tra qua TÊN ở dưới
                ),
                // V3 · R11 (1.66): `motor_power` nay bind theo **TÊN HẰNG** (`ENGINE_POWER`) vì số thật đổi theo
                // cấu hình xe ([ĐO nguồn fw-dl3]: 339738656 khi CanFD · 353370144 Toyota · 1033203762 còn lại).
                // Xe giả ở đây khai đúng một cấu hình; đường đọc phải đi qua phép tra tên rồi mới tới số.
                featureNames = mapOf("Engine.ENGINE_POWER" to 339738656),
            ),
        ),
    )

    @Test fun `readSlow builds CarStatus across domains`() {
        val s = adapter().readSlow(CarStatus())
        assertEquals(82, s.energy.soc)
        assertEquals(418, s.energy.evRangeKm)
        assertEquals(45, s.energy.chargingPct)
        assertEquals(24, s.climate.cabinTempC)
        assertEquals(240.0, s.tyres.pFlKpa)          // raw kPa in CarStatus
        assertEquals(98, s.body.windowLfPct)
        assertEquals(true, s.lights.lowBeam)
        assertEquals("LGXCE4CB0N0000001", s.identity.vin)
    }

    @Test fun `readFast builds drivetrain and fast fields`() {
        val s = adapter().readFast(CarStatus())
        assertEquals(56, s.drivetrain.speedKmh)
        assertEquals(120, s.energy.motorPowerKw)
    }

    @Test fun `slow preserves fast fields (copy-merge boundary)`() {
        val a = adapter()
        val s = a.readSlow(a.readFast(CarStatus()))
        assertEquals(56, s.drivetrain.speedKmh)          // from fast, kept
        assertEquals(120, s.energy.motorPowerKw)         // fast field survives slow energy.copy
        assertEquals(82, s.energy.soc)                   // from slow
    }

    @Test fun `legacy 6 methods map correctly (kPa to bar)`() {
        val a = adapter()
        assertEquals(82, a.batteryPercent())
        assertEquals(418, a.rangeKm())
        assertEquals(listOf(2.4, 2.35, 2.3, 2.1), a.tirePressuresBar())
        assertEquals(2, a.pm25Level())
        assertEquals(56, a.speedKmh())
        assertEquals(26, a.outsideTempC())
    }

    @Test fun `off-car returns all null`() {
        val a = CarDataAdapter(HalBindingTable(FakeHalGateway()))
        val s = a.readSlow(a.readFast(CarStatus()))
        assertNull(s.energy.soc)
        assertNull(s.drivetrain.speedKmh)
        assertNull(s.tyres.pFlKpa)
        assertNull(s.identity.vin)
        assertNull(a.batteryPercent())
        assertNull(a.tirePressuresBar())
    }
}
