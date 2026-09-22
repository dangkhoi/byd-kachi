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

    @Test fun `2026-09-21 badge da bo - OVERDRIVE khong con dau`() {
        // Tier vẫn là dữ liệu, nhưng UI không vẽ chấm nữa (owner chốt).
        assertEquals(EvidenceTier.OVERDRIVE, CarCapabilities.tierOf("ev_range_km"))
        assertFalse(CarCapabilities.needsBadge("ev_range_km"))
        // ⚠⚠ 1.90 2026-09-21 — vế `DASHCAST` gỡ vì `cast` là mã **DUY NHẤT** mang tier đó, và owner đã xoá nút ấy.
        // [ĐO] grep: `EvidenceTier.DASHCAST` nay không còn chỗ dùng nào trong dữ liệu registry (chỉ còn trong KDoc
        // + enum). Giữ giá trị enum là có chủ ý — nó vẫn là một mức bằng chứng có nghĩa (*"đọc từ mã byd-dashcast"*)
        // và `ActionMacros.tier()` so theo THỨ TỰ KHAI nên bỏ một giá trị giữa dãy sẽ đổi phép so của gói lệnh.
        assertTrue(
            ControlRegistry.ALL.none { it.tier == EvidenceTier.DASHCAST } &&
                TelemetryRegistry.ALL.none { it.tier == EvidenceTier.DASHCAST },
            "nếu có mã DASHCAST mới thì thêm lại vế đo cho nó ở đây (badge vẫn phải là false)",
        )
    }

    @Test fun `id la tra ve null hoac false an toan`() {
        assertNull(CarCapabilities.of("khong-co"))
        assertNull(CarCapabilities.tierOf("khong-co"))
        assertFalse(CarCapabilities.isWired("khong-co"))
        assertFalse(CarCapabilities.needsBadge("khong-co"))
    }

    @Test fun `control proven cung wired`() {
        assertTrue(CarCapabilities.isWired("win_lf"))
        assertEquals(EvidenceTier.PROVEN, CarCapabilities.tierOf("seatc"))
    }
}
