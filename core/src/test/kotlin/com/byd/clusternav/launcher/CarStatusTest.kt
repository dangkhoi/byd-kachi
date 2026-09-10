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
        assertNull(s.safety.radarZones)
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

    @Test fun `radar zones la list nullable`() {
        val s = CarStatus().copy(safety = CarStatus.Safety(radarZones = listOf(0, 1, 2, 3, 4, 0, 0, 0)))
        assertEquals(8, s.safety.radarZones!!.size)
    }
}
