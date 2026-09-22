package com.byd.clusternav.launcher

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * ═══ THANH NÚT PHẢI TỰ RỤNG MÃ ĐÃ XOÁ ═══════════════════════════════════════════════════════════════════════
 *
 * Bug 2026-09-22 ("đặt 10 hiện 6"): owner gỡ `lock/door/steer_heat` + macro `mac_leave/mac_door_light` (1.94/1.95),
 * `window` (hợp nhất vào `win_lf`) — nhưng cấu hình thanh nút ĐÃ LƯU vẫn giữ mã đó, `ControlDockView.rebuild` bỏ
 * qua IM LẶNG ⇒ số "đang bật" (10) không khớp số nút hiện (6). Đây là **bổ sung còn thiếu** của quy trình gỡ mã:
 * ô có `WorkspaceState.sanitized`, chip có `TopStripConfig.decode` lọc, **thanh nút** thì trước đây KHÔNG.
 *
 * Bài này khoá [DockSelection.sanitize]: gỡ một control mà quên bịt đường thanh nút thì đây đỏ ngay.
 */
class DockSelectionTest {

    /** Sáu mã đã gỡ trong 1.94/1.95 — cấu hình cũ lưu chúng phải TỰ RỤNG khi nạp. */
    private val removed = listOf("lock", "door", "window", "steer_heat", "mac_leave", "mac_door_light")

    @Test fun `sanitize bo het ma da xoa 1_94-1_95`() {
        removed.forEach { id ->
            assertTrue(
                CapabilityCatalog.kindOf(id) == null && ActionMacros.byId(id) == null,
                "tiền đề: '$id' đã gỡ khỏi mọi bộ đăng ký",
            )
        }
        val old = listOf("lock", "window", "trunk", "readl", "fan", "door", "mac_door_light", "fuel_range_km", "defrost", "seath")
        val clean = DockSelection.sanitize(old)
        assertEquals(
            listOf("trunk", "readl", "fan", "fuel_range_km", "defrost", "seath"), clean,
            "chỉ giữ mã còn sống, thứ tự nguyên vẹn — số 'đang bật' phải khớp số nút hiện",
        )
    }

    @Test fun `sanitize giu nguyen ma con song`() {
        val live = listOf("trunk", "readl", "pm25", "seatc", "seath", "fan", "temp", "win_lf", "windows_close_all", "mac_win_close_all")
        assertEquals(live, DockSelection.sanitize(live), "mọi mã còn dùng được (control · datum · gói lệnh) phải giữ")
    }

    @Test fun `sanitize danh sach rong tra rong`() {
        assertEquals(emptyList<String>(), DockSelection.sanitize(emptyList()))
    }
}
