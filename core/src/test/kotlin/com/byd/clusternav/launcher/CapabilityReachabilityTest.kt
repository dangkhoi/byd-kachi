package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RW0 — **CHẠM TỚI ĐƯỢC**: mọi khả năng phải có ĐƯỜNG cho người dùng đặt vào ô, không chỉ "vẽ được".
 *
 * ⚠⚠ Vì sao có bài này (lỗi thật, tìm ra ở lượt soát 2026-09-11): `WidgetViews` **vẽ được** ô hành động và
 * [ActionMacros] **có** 4 gói lệnh, nhưng màn chọn của ngăn kéo chỉ liệt kê mục ĐỌC ⇒ người dùng **không có nút nào**
 * để đặt một hành động vào ô giữa màn. Số đo *"3 gói lệnh ở ô giữa màn"* của phiên trước đạt được bằng cách **gieo
 * cấu hình bằng tay**, nên nó KHÔNG chứng minh người dùng làm được. Cả một gói tính năng (W2) không giao được.
 *
 * Bài học đóng vào test: *"vẽ được" ≠ "đặt được"*. Một khả năng chỉ tính là xong khi có đường **đi từ tay người dùng**
 * tới nó. Nguồn của màn chọn = [CapabilityCatalog.byDomain] (mục có nhóm) + [WidgetRegistry.ALL] (widget dựng tay,
 * không thuộc nhóm nào ⇒ UI bày riêng ở đầu).
 */
class CapabilityReachabilityTest {

    /** Đúng những gì màn chọn (ngăn kéo + bảng Tuỳ biến) bày ra cho người dùng. */
    private fun reachable(): Set<String> =
        CapabilityCatalog.byDomain().flatMap { it.second }.map { it.id }.toSet() +
            WidgetRegistry.ALL.map { it.id }.toSet()

    @Test
    fun `moi kha nang deu co duong dat vao o`() {
        val hidden = CapabilityCatalog.all().map { it.id }.filter { it !in reachable() }
        assertTrue(
            hidden.isEmpty(),
            "có khả năng KHÔNG bày ở màn chọn nào ⇒ người dùng không đặt được, tính năng coi như không giao: $hidden",
        )
    }

    @Test
    fun `moi GOI LENH deu dat duoc`() {
        // Gói lệnh là thứ dễ tàng hình nhất: nó chỉ vào màn chọn qua `byDomain()`, mà hàm đó lọc theo nhóm ⇒ một gói
        // khai `domain = null` sẽ **im lặng biến mất** khỏi CẢ ngăn kéo LẪN bảng Tuỳ biến mà không test nào đỏ.
        val reach = reachable()
        ActionMacros.ALL.forEach { macro ->
            assertTrue(macro.id in reach, "gói lệnh '${macro.label}' (${macro.id}) không có đường đặt vào ô")
            assertNotNull(CapabilityCatalog.pick(macro.id), "gói lệnh phải tra ra được như một khả năng")
        }
    }

    @Test
    fun `moi NUT deu dat duoc`() {
        val reach = reachable()
        val missing = ControlRegistry.ALL.map { it.id }.filter { it !in reach }
        assertTrue(missing.isEmpty(), "nút không có đường đặt vào ô: $missing")
    }

    @Test
    fun `dat hanh dong vao o thi o giu nguyen ma do`() {
        // Đường ghi (`assignWidgets`) KHÔNG được lọc mã hành động: trước RW0 cổng chặn nằm ở `DockConfig.setEnabled`,
        // và đây là cổng tương ứng của ô giữa màn. Bỏ mã đi im lặng = người dùng bấm Đặt mà không có gì xảy ra.
        val state = WorkspaceState.DEFAULT.withSlot(0, SlotContent.Widget(listOf("mac_leave", "recirc", "tyre_p_fl")))
        val ids = (state.slots[0] as SlotContent.Widget).ids
        assertEquals(listOf("mac_leave", "recirc", "tyre_p_fl"), ids, "ô phải giữ ĐỦ cả gói lệnh, nút và mục đọc")
        assertTrue(CapabilityCatalog.isWrite("mac_leave"), "gói lệnh phải ra ô BẤM được")
        assertTrue(CapabilityCatalog.isWrite("recirc"), "nút phải ra ô BẤM được")
        assertTrue(!CapabilityCatalog.isWrite("tyre_p_fl"), "mục đọc phải ra ô XEM (không bấm)")
    }
}
