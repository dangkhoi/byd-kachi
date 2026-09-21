package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TelemetryRegistryTest {

    @Test fun `phu du 8 domain telemetry catalog A`() {
        val covered = TelemetryRegistry.domains()
        Domain.TELEMETRY.forEach { d ->
            assertTrue(d in covered, "TelemetryRegistry thieu domain $d")
        }
    }

    @Test fun `co it nhat 100 datum (catalog ~112)`() {
        // ⚠ WP8 2026-09-20: sàn 100 → 70 sau khi owner purge 29 datum BỎ (registry còn 73). Sàn là chốt chống
        // "registry teo bất thường", không phải mục tiêu — hạ đúng bằng số đã mất.
        assertTrue(TelemetryRegistry.ALL.size >= 70, "chi co ${TelemetryRegistry.ALL.size} datum")
    }

    @Test fun `moi bindingKey khong rong`() {
        val empty = TelemetryRegistry.ALL.filter { it.bindingKey.isBlank() }
        assertTrue(empty.isEmpty(), "bindingKey rong: ${empty.map { it.id }}")
    }

    @Test fun `moi telemetry id duy nhat`() {
        val ids = TelemetryRegistry.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun `byId va byDomain hoat dong`() {
        val soc = TelemetryRegistry.byId("soc")
        assertNotNull(soc)
        assertEquals(Domain.ENERGY, soc!!.domain)
        assertEquals(EvidenceTier.PROVEN, soc.tier)
        assertNull(TelemetryRegistry.byId("khong-co"))
        assertTrue(TelemetryRegistry.byDomain(Domain.TYRES).all { it.domain == Domain.TYRES })
        assertTrue(TelemetryRegistry.byDomain(Domain.TYRES).isNotEmpty())
    }

    @Test fun `datum proven cluster nav duoc danh dau PROVEN`() {
        // Nhung datum da chay tren xe owner (SpeedProvider/PM2.5/tyre/SOC).
        listOf("soc", "speed", "pm25_level", "pm25_value", "tyre_p_fl").forEach { id ->
            assertEquals(EvidenceTier.PROVEN, TelemetryRegistry.byId(id)?.tier, "$id phai PROVEN")
        }
    }
}
