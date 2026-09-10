package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CapabilityModelTest {

    @Test fun `Domain co du 8 domain telemetry`() {
        assertEquals(8, Domain.TELEMETRY.size)
        assertEquals(
            listOf(
                Domain.ENERGY, Domain.DRIVETRAIN, Domain.CLIMATE, Domain.TYRES,
                Domain.BODY, Domain.LIGHTS, Domain.SAFETY, Domain.IDENTITY,
            ),
            Domain.TELEMETRY,
        )
        // INFOTAINMENT ton tai (control-only) nhung KHONG nam trong telemetry domain.
        assertFalse(Domain.INFOTAINMENT in Domain.TELEMETRY)
    }

    @Test fun `WidgetShape phu cac hinh render can thiet`() {
        val kinds = WidgetShape.values().toSet()
        listOf(
            WidgetShape.RING, WidgetShape.CARD, WidgetShape.GAUGE, WidgetShape.BOARD,
            WidgetShape.STRIP, WidgetShape.DIAL, WidgetShape.MEDIA,
        ).forEach { assertTrue(it in kinds, "thieu WidgetShape.$it") }
    }

    @Test fun `EvidenceTier co dung 4 muc`() {
        assertEquals(4, EvidenceTier.values().size)
        assertEquals(
            setOf(EvidenceTier.PROVEN, EvidenceTier.OVERDRIVE, EvidenceTier.DASHCAST, EvidenceTier.NEEDS_CAR),
            EvidenceTier.values().toSet(),
        )
    }

    @Test fun `wired dung cho moi tier tru NEEDS_CAR`() {
        assertTrue(EvidenceTier.PROVEN.wired)
        assertTrue(EvidenceTier.OVERDRIVE.wired)
        assertTrue(EvidenceTier.DASHCAST.wired)
        assertFalse(EvidenceTier.NEEDS_CAR.wired)
    }

    @Test fun `needsBadge chi cho OVERDRIVE va DASHCAST`() {
        assertFalse(EvidenceTier.PROVEN.needsBadge)
        assertTrue(EvidenceTier.OVERDRIVE.needsBadge)
        assertTrue(EvidenceTier.DASHCAST.needsBadge)
        assertFalse(EvidenceTier.NEEDS_CAR.needsBadge)
    }
}
