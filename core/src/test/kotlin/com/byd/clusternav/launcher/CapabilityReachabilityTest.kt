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

    /**
     * Đúng những gì màn chọn (ngăn kéo + màn Cài đặt) bày ra cho người dùng.
     *
     * G1 · T4: mục **Nhóm** ([CapabilityPicker.groupPicks]) được kể riêng vì hai màn chọn nay **lọc nhóm khỏi lĩnh
     * vực** ([CapabilityPicker.singlesOf]) — để cùng một mã không có hai ô (bảng tra `mã → view` sẽ bị ghi đè). Nếu
     * ở đây chỉ kể `byDomain()` thì phép lọc đó làm 12 nhóm trông như "không có đường tới", tức bài này sẽ đỏ ĐÚNG
     * nhưng vì lý do SAI. Kể cả hai nguồn = mô tả đúng thứ màn chọn thật sự bày.
     */
    private fun reachable(): Set<String> =
        CapabilityPicker.groupPicks().map { it.id }.toSet() +
            CapabilityCatalog.byDomain().flatMap { it.second }.map { it.id }.toSet() +
            WidgetRegistry.ALL.map { it.id }.toSet() +
            // S4 · R12: hành động của chính launcher khai `domain = null` ⇒ `byDomain()` KHÔNG bày chúng (cố ý).
            // Đường tới chúng là khối riêng của bộ chọn nút thanh xe — kể nguồn đó ra ở đây, đúng cùng lý do đã
            // phải kể `groupPicks()` khi G1 lọc nhóm khỏi lĩnh vực: nếu không, bài đỏ ĐÚNG nhưng vì lý do SAI.
            CapabilityPicker.launcherPicks().map { it.id }.toSet()

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
    fun `moi NHOM deu dat duoc - va dat duoc o MUC DAU`() {
        // G1 · T4. Nhóm cũng dễ tàng hình như gói lệnh, chỉ theo cách khác: nó vào màn chọn qua mục **Nhóm** riêng,
        // nên nếu ai đó bỏ mục đó đi (hoặc lọc nhóm khỏi lĩnh vực mà quên bày lại) thì 12 nhóm **im lặng biến mất**
        // khỏi CẢ ngăn kéo LẪN màn Cài đặt — đúng bài học "vẽ được ≠ đặt được": T3 vẽ được ô nhóm, nhưng vẽ được
        // không có nghĩa người dùng đặt được.
        val reach = reachable()
        val firstSection = CapabilityPicker.groupPicks().map { it.id }
        CapabilityGroups.ALL.forEach { g ->
            assertTrue(g.id in reach, "nhóm '${g.label}' (${g.id}) không có đường đặt vào ô")
            assertNotNull(CapabilityCatalog.pick(g.id), "nhóm phải tra ra được như một khả năng")
            assertTrue(g.id in firstSection, "nhóm '${g.label}' phải nằm ở MỤC ĐẦU của màn chọn (§4.2), không rải rác")
        }
        assertEquals(
            CapabilityGroups.ALL.size, firstSection.size,
            "mục đầu phải bày ĐÚNG các nhóm, không thêm không bớt",
        )
    }

    /**
     * S4 · R12 — hai hành động launcher phải đặt được, và đặt được ĐÚNG MỘT chỗ: khối Launcher của bộ chọn nút.
     *
     * Cùng bài học "vẽ được ≠ đặt được": `ControlDockView` nay dựng được ô loại [CapabilityKind.LAUNCHER], nhưng
     * nếu bộ chọn không bày khối đó thì người dùng không có nút nào để đưa chúng lên thanh.
     */
    @Test
    fun `hai hanh dong launcher deu dat duoc va chi o khoi Launcher`() {
        val reach = reachable()
        val section = CapabilityPicker.launcherPicks().map { it.id }
        LauncherActions.ALL.forEach { a ->
            assertTrue(a.id in reach, "hành động '${a.label}' (${a.id}) không có đường đặt vào thanh nút")
            assertTrue(a.id in section, "phải nằm trong khối Launcher, không rải vào lĩnh vực của xe")
            assertEquals(CapabilityKind.LAUNCHER, CapabilityCatalog.kindOf(a.id), "phải phân loại là LAUNCHER")
        }
        assertEquals(LauncherActions.ALL.size, section.size, "khối Launcher bày ĐÚNG các hành động đó, không thêm")
        // Và chúng KHÔNG được lọt vào lĩnh vực của xe — ở đó chúng sẽ có ô THỨ HAI (bảng `mã → view` bị ghi đè,
        // đúng lỗi RW0 mà `CapabilityPicker.singlesOf` đang chặn cho NHÓM).
        val inDomains = CapabilityCatalog.byDomain().flatMap { it.second }.map { it.id }
        assertTrue(
            LauncherActions.ALL.none { it.id in inDomains },
            "hành động launcher không thuộc lĩnh vực nào của xe ⇒ không được xuất hiện trong byDomain()",
        )
    }

    @Test
    fun `moi NUT deu dat duoc`() {
        val reach = reachable()
        // V3 · R12 (1.66): xem chú thích cùng ca ở `CapabilityPickerTest` — mã cố ý ẩn (`hood`: xe không có nắp
        // ca-pô điện) không có đường ĐẶT MỚI, nhưng ô ai đã đặt vẫn dựng được (`CapabilityCatalog.pick`).
        val hidden = CapabilityCatalog.HIDDEN_FROM_PICKER.keys
        val missing = ControlRegistry.ALL.map { it.id }.filter { it !in reach && it !in hidden }
        assertTrue(missing.isEmpty(), "nút không có đường đặt vào ô: $missing")
        // Vẫn phải tra ra được — ẩn khỏi màn CHỌN, không phải xoá khỏi danh mục.
        hidden.forEach { id -> assertTrue(CapabilityCatalog.pick(id) != null, "mã ẩn $id phải còn tra được") }
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
