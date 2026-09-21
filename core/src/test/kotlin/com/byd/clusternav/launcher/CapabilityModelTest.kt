package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CapabilityModelTest {

    // 7 chứ không 8: `Domain.SAFETY` đã gỡ hẳn 2026-09-16 cùng toàn bộ ADAS/an toàn (owner).
    @Test fun `Domain co du 7 domain telemetry`() {
        assertEquals(7, Domain.TELEMETRY.size)
        assertEquals(
            listOf(
                Domain.ENERGY, Domain.DRIVETRAIN, Domain.CLIMATE, Domain.TYRES,
                Domain.BODY, Domain.LIGHTS, Domain.IDENTITY,
            ),
            Domain.TELEMETRY,
        )
        assertFalse(Domain.values().any { it.name == "SAFETY" }, "ADAS/an toàn không được mọc lại thành domain")
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

    /**
     * ⚠ 2026-09-21 · OWNER CHỐT BỎ HẲN CHẤM: [EvidenceTier.needsBadge] nay **luôn `false`** cho MỌI mức (kể cả
     * không-PROVEN). Chấm "chưa kiểm trên xe" đã gỡ khỏi mọi bề mặt UI; [EvidenceTier] chỉ còn là dữ liệu (thứ tự
     * tin cậy + [wired]). Bài này khoá đúng quyết định đó — nếu ai bật badge lại thì đỏ, buộc xem lại với owner.
     */
    @Test fun `needsBadge luon false sau khi owner bo cham 2026-09-21`() {
        EvidenceTier.values().forEach {
            assertFalse(it.needsBadge, "$it: chấm đã bỏ hẳn — không mức nào được mang badge nữa")
        }
    }
}
