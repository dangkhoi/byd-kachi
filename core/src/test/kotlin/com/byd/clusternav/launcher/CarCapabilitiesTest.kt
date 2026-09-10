package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CarCapabilitiesTest {

    @Test fun `derived tu registry - dung so luong`() {
        assertEquals(TelemetryRegistry.ALL.size, CarCapabilities.TELEMETRY.size)
        assertEquals(ControlRegistry.ALL.size, CarCapabilities.CONTROL.size)
        assertEquals(
            TelemetryRegistry.ALL.size + ControlRegistry.ALL.size,
            CarCapabilities.ALL.size,
            "id telemetry va control khong duoc trung nhau",
        )
    }

    @Test fun `PROVEN thi wired va khong badge`() {
        val soc = CarCapabilities.of("soc")!!
        assertEquals(EvidenceTier.PROVEN, soc.tier)
        assertTrue(soc.wired)
        assertFalse(soc.needsBadge)
        assertTrue(CarCapabilities.isWired("pm25_level"))
    }

    @Test fun `NEEDS_CAR thi khong wired`() {
        // tyre_t_fl = nhiet lop, chua doc duoc toi khi TPMS phat.
        assertFalse(CarCapabilities.isWired("tyre_t_fl"))
        assertEquals(EvidenceTier.NEEDS_CAR, CarCapabilities.tierOf("tyre_t_fl"))
    }

    @Test fun `OVERDRIVE va DASHCAST can badge`() {
        assertTrue(CarCapabilities.needsBadge("ev_range_km")) // OVERDRIVE
        assertTrue(CarCapabilities.needsBadge("cast"))        // DASHCAST (control)
    }

    @Test fun `id la tra ve null hoac false an toan`() {
        assertNull(CarCapabilities.of("khong-co"))
        assertNull(CarCapabilities.tierOf("khong-co"))
        assertFalse(CarCapabilities.isWired("khong-co"))
        assertFalse(CarCapabilities.needsBadge("khong-co"))
    }

    @Test fun `control proven cung wired`() {
        assertTrue(CarCapabilities.isWired("window"))
        assertEquals(EvidenceTier.PROVEN, CarCapabilities.tierOf("seatc"))
    }
}
