package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.CapabilityCatalog
import com.byd.clusternav.launcher.CapabilityKind
import com.byd.clusternav.launcher.CapabilityPicker
import com.byd.clusternav.launcher.CarDataDemand
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.HomeUiState
import com.byd.clusternav.launcher.IconRepeat
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.SlotContent
import com.byd.clusternav.launcher.WorkspaceRenderPlan
import com.byd.clusternav.launcher.WorkspaceRenderPlanner
import com.byd.clusternav.launcher.WorkspaceState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 wave 2B · CAMERA-WIDGET-TILE (OQ3) + D4 — phần THUẦN của ô camera trong widget lưới ô giữa màn ═══════════════
 *
 * Ô camera là việc LAUNCHER: không đọc gì từ xe, trạng thái sáng nghe controller camera. Bốn tính chất mà tầng vẽ dựa vào:
 *  1. lưới KHÔNG dựng lại ô camera theo nhịp trạng thái xe 1 Hz (dựng lại = tháo/gắn giữa cú chạm ⇒ mất cú bấm, và gỡ/gắn
 *     lại người nghe) — `WorkspaceRenderPlanner.selfDriven` + `decide`;
 *  2. đặt ô camera vào lưới không làm cổng nhu cầu dữ liệu xe (H1) rơi về ĐỌC-HẾT;
 *  3. bốn camera cùng một bóng hình ⇒ lưới KHÔNG được bỏ nhãn (dạng chỉ-icon) — luật widget sẵn có `IconRepeat.ofIds`;
 *  4. D4: ô *Tắt camera* không còn trùng hình với nút Camera 360 của xe; ô lưu trong ô giữa màn qua được lượt dọn mã lạ.
 */
class CameraWidgetTileTest {

    private val cameraIds = CapabilityPicker.cameraPicks().map { it.id }

    @Test fun `tien de - nam o camera la viec launcher, bay o ca hai bo chon`() {
        assertEquals(LauncherActions.CAM_OFF, cameraIds.last())
        assertEquals(5, cameraIds.size)
        cameraIds.forEach { assertEquals(CapabilityKind.LAUNCHER, CapabilityCatalog.kindOf(it), it) }
    }

    @Test fun `luoi khong dung lai o camera theo nhip trang thai xe`() {
        cameraIds.forEach { assertTrue(WorkspaceRenderPlanner.selfDriven(it), "$it tự lo nội dung (nghe controller)") }
        val w = WorkspaceState(slots = List(WorkspaceState.SLOT_CAP) { if (it == 0) SlotContent.Widget(cameraIds) else SlotContent.Empty })
        val plan = WorkspaceRenderPlanner.decide(w, w, builtSlotCount = w.preset.slotCount, statusChanged = true)
        assertEquals(WorkspaceRenderPlan.PerSlot(emptyList()), plan, "trạng thái xe đổi ⇒ ô chỉ có camera KHÔNG bị dựng lại")
        // Ô TRỘN (camera + một datum) vẫn dựng lại phần đọc như cũ — luật cũ không đổi cho mục ĐỌC.
        val mixed = WorkspaceState(slots = List(WorkspaceState.SLOT_CAP) { if (it == 0) SlotContent.Widget(listOf(cameraIds[0], "soc")) else SlotContent.Empty })
        assertEquals(WorkspaceRenderPlan.PerSlot(listOf(0)), WorkspaceRenderPlanner.decide(mixed, mixed, mixed.preset.slotCount, statusChanged = true))
    }

    @Test fun `o camera khong lam cong nhu cau du lieu xe roi ve doc het`() {
        val base = HomeUiState()
        val withCams = base.copy(workspace = base.workspace.copy(slots = base.workspace.slots.mapIndexed { i, s ->
            if (i == 0) SlotContent.Widget(cameraIds) else s
        }))
        val empty = base.copy(workspace = base.workspace.copy(slots = base.workspace.slots.mapIndexed { i, s ->
            if (i == 0) SlotContent.Empty else s
        }))
        val d = CarDataDemand.of(withCams)
        assertNotNull(d, "mã launcher không được làm cổng H1 tắt im lặng (đọc-hết)")
        assertEquals(CarDataDemand.of(empty), d, "ô camera không bày datum nào của xe")
    }

    @Test fun `bon camera cung bong hinh - luoi khong duoc bo nhan`() {
        assertFalse(IconRepeat.ofIds(cameraIds.take(4)), "bỏ nhãn là bấm nhầm camera (cùng bóng xe, khác quạt)")
        assertFalse(IconRepeat.ofIds(cameraIds.take(2)), "hai camera cũng chặn — luật không-nhãn không có trần CAP")
        assertTrue(IconRepeat.ofIds(listOf(cameraIds[0], LauncherActions.CAM_OFF)), "Tắt camera có bóng riêng (D4)")
    }

    @Test fun `D4 - hinh Tat camera khac hinh Camera 360, o luu qua duoc luot don ma la`() {
        val offIcon = CapabilityCatalog.pick(LauncherActions.CAM_OFF)?.icon
        assertEquals("ic-cam-off", offIcon)
        assertNotEquals(ControlRegistry.byId("cam")?.icon, offIcon, "hai ô cạnh nhau trên thanh nút từng y hệt nhau")
        assertEquals(cameraIds.size, cameraIds.mapNotNull { CapabilityCatalog.pick(it)?.icon }.toSet().size, "năm hình khác nhau")
        val w = WorkspaceState(slots = List(WorkspaceState.SLOT_CAP) { if (it == 0) SlotContent.Widget(cameraIds) else SlotContent.Empty })
        assertEquals(w, w.sanitized(), "mã camera là mã có thật ⇒ không bị dọn khỏi ô")
        assertTrue(w.unknownWidgetIds().isEmpty())
    }
}
