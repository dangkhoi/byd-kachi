package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1 · R26 — DÂY NỐI phía `:app`: dải −/+ của Cài đặt › Chiếu cụm lấy trần từ kích VẬT LÝ đã đo của cụm, không từ `wmSize` cũ.
 * [ĐO xe 13/10 `docs/diagnostics/oncar-2026-10-13/10-recast-waze-2.txt`]: `wmSize` lưu cho app = 1920×520 ⇒ trần mép dưới 520 (+R7
 * 780) — bánh cóc không thoát. Phép chọn kích là thuần ở `:core` (`ClusterDisplayFacts.editFrameSize`, `CastFreeformGeometryTest`);
 * bài này khoá chỗ gọi. Hàng "Dịch nội dung" ẩn trên cụm freeform đi qua `contentShiftSupported` (khoá ở
 * `SettingsCastShiftWiringContractTest` + `CastFreeformGeometryTest`).
 */
class CastFreeformBandWiringContractTest {

    private val bridge by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeGeometry.kt") }

    @Test
    fun `khung dai -+ = kich vat ly da do, lui wmSize khi chua do`() {
        val frame = SourceRoots.body(bridge, "fun ClusterNavBridge.geometryFrame(): Pair<Int, Int>")
        assertTrue(frame.contains("coordinator.clusterPhysicalSize"), frame)
        assertTrue(frame.contains("ClusterDisplayFacts.editFrameSize(physical, wmSize)"), frame)
        assertFalse(frame.contains("split(\"x\")"), "không tự tách wmSize (phép chọn ở :core): $frame")
    }

    @Test
    fun `edit frame va phep kep deu di qua geometryFrame`() {
        val edit = SourceRoots.body(bridge, "private fun ClusterNavBridge.editFrame(target: CastGeometryTarget): CastBounds")
        assertTrue(edit.contains("geometryFrame()"), edit)
        val clamp = SourceRoots.body(bridge, "private fun ClusterNavBridge.clampToBand(target: CastGeometryTarget, bounds: CastBounds): CastBounds")
        assertTrue(clamp.contains("geometryFrame()") && clamp.contains("CastGeometryGuard.overflowMax(height)"), clamp)
    }
}
