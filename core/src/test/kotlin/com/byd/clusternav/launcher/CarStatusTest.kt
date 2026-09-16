package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CarStatusTest {

    @Test fun `mac dinh moi field null (chua doc off-car)`() {
        val s = CarStatus()
        assertNull(s.energy.soc)
        assertNull(s.energy.evRangeKm)
        assertNull(s.drivetrain.speedKmh)
        assertNull(s.climate.pm25Level)
        assertNull(s.tyres.pFlKpa)
        assertNull(s.body.windowLfPct)
        assertNull(s.lights.lowBeam)
        assertNull(s.energy.volt12v)
        assertNull(s.identity.vin)
    }

    @Test fun `copy-based - cap nhat 1 domain khong dung domain khac`() {
        val s = CarStatus()
        val s2 = s.copy(energy = s.energy.copy(soc = 82, evRangeKm = 418))
        assertEquals(82, s2.energy.soc)
        assertEquals(418, s2.energy.evRangeKm)
        assertNull(s2.climate.pm25Level) // domain khac giu null
        assertNull(s.energy.soc)         // ban goc bat bien
    }

    // ⚠ Bài `radar zones la list nullable` đã gỡ 2026-09-16: cụm `CarStatus.Safety` và datum `radar_zones` không
    // còn tồn tại sau khi owner gỡ toàn bộ ADAS/an toàn; ba mục điện 12V/MCU đã dời sang [CarStatus.Energy].
    // ⚠ (V) FEATURE-FILTER 2026-09-17: `mcuStatus` đã xoá (owner chấm NO) ⇒ bài chỉ còn canh hai vai điện 12V.
    @Test fun `dien 12V nam o cum Energy`() {
        val s = CarStatus().copy(energy = CarStatus.Energy(volt12v = 12.6, volt12vLevel = 2))
        assertEquals(12.6, s.energy.volt12v)
        assertEquals(2, s.energy.volt12vLevel)
    }
}
