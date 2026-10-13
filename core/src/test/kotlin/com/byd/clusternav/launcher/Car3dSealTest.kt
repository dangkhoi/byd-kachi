package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 R25 — khoá các SỰ THẬT đo trên lưới Seal thật (bộ chạy thử ngoài repo, 13/10 — repo không chứa byte BYD): trục mô hình
 * (X đầu xe, Y lên, Z sườn PHẢI — `doorlf_paint` tâm z = −0,85, `turnrightfront` z = +0,73) phải đặt ra khung thế giới chung
 * với sườn TRÁI là +X (không soi gương); widget lớn chỉ bày cho ô lớn; bảng nhu cầu đọc đủ ba nhóm của widget.
 */
class Car3dSealTest {

    @Test
    fun `truc luoi Seal dat dung khung the gioi - trai la trai, dau xe la +Z`() {
        // Hộp bao + tâm bộ phận ĐO bằng BydAvmMesh trên car.mesh thật (bộ chạy thử meshharness/eke-parts.txt).
        val pl = Car3dOrient.place(V3(0.02, 0.84, 0.0), V3(2.39, 0.62, 0.93), front = V3(2.10, 0.65, 0.0), rear = V3(-2.28, 0.51, 0.0))
        val doorLf = pl.apply(V3(0.37, 0.83, -0.85))
        val turnRf = pl.apply(V3(2.10, 0.65, 0.73))
        assertTrue(doorLf.x > 0.5, "cửa trước-TRÁI phải ở +X (sườn trái): $doorLf")
        assertTrue(turnRf.x < -0.5 && turnRf.z > 1.5, "xi-nhan trước-PHẢI ở −X, đầu xe +Z: $turnRf")
        assertEquals(0.84 - 0.84, pl.apply(V3(0.02, 0.84, 0.0)).y, 1e-9)
        // Camera mặc định ¾ trước-trái nhìn thấy cửa trái, không thấy cửa phải (chấm phía khuất bị ẩn).
        val cam = Car3dCamera(V3.ZERO, 8.0, Car3dCatalog.BYD_AVM.yawDeg, Car3dCatalog.BYD_AVM.pitchDeg, Car3dCatalog.BYD_AVM.fovDeg)
        val doorRf = pl.apply(V3(0.37, 0.83, 0.85))
        val b = pl.bounds
        assertTrue(cam.facing(doorLf, Car3dMath.outward(doorLf, b)))
        assertFalse(cam.facing(doorRf, Car3dMath.outward(doorRf, b)))
        // [ĐO máy ảo 13/10] lỗi lượt đầu: hướng ngang thuần ẩn cửa SAU-trái ở góc ¾ trước-trái; pháp tuyến elip thì thấy.
        val doorLr = pl.apply(V3(-0.69, 0.83, -0.78))
        assertFalse(cam.facing(doorLr, Car3dMath.outward(doorLr, V3.ZERO)), "khoá lại bằng chứng của lỗi cũ")
        assertTrue(cam.facing(doorLr, Car3dMath.outward(doorLr, b)))
        val trunk = pl.apply(V3(-1.85, 1.16, 0.0))
        assertFalse(cam.facing(trunk, Car3dMath.outward(trunk, b)), "cốp khuất khi nhìn từ trước")
        val roof = pl.apply(V3(-0.34, 1.49, 0.0))
        assertTrue(cam.facing(roof, Car3dMath.outward(roof, b)), "nóc thấy được khi camera nhìn xuống")
        // [ĐO máy ảo 13/10] lượt elip-3D: kính trước-PHẢI (cao) lọt "thấy", cửa sau-trái (thấp, ty 0,3) bị ẩn — khoá cả hai.
        val winRf = pl.apply(V3(0.27, 1.18, 0.74)); val doorLrLow = pl.apply(V3(-0.69, 0.60, -0.78))
        assertFalse(cam.facing(winRf, Car3dMath.outward(winRf, b)))
        assertTrue(cam.facing(doorLrLow, Car3dMath.outward(doorLrLow, b)))
        // Quay tới biên 200° (sau-PHẢI): sườn trái khuất, sườn phải hiện.
        val back = cam.copy(yawDeg = 200.0)
        val doorRfLow = pl.apply(V3(0.37, 0.60, 0.85))
        assertFalse(back.facing(doorLrLow, Car3dMath.outward(doorLrLow, b)), "cửa sau-trái phải khuất ở 200°")
        assertTrue(back.facing(doorRfLow, Car3dMath.outward(doorRfLow, b)), "cửa trước-phải phải hiện ở 200°")
    }

    @Test
    fun `widget xe 3D chi bay cho o lon, o chua do thi khong bay`() {
        val (w, h) = WidgetRegistry.LARGE_SLOT_DP
        assertTrue(WidgetRegistry.offered(w, h).any { it.id == "w_car3d" })
        assertFalse(WidgetRegistry.offered(w - 1, h).any { it.id == "w_car3d" })
        assertFalse(WidgetRegistry.offered(w, h - 1).any { it.id == "w_car3d" })
        assertFalse(WidgetRegistry.offered(null, null).any { it.id == "w_car3d" })
        // Widget không đòi cỡ vẫn bày ở mọi ô (kể cả chưa đo).
        assertEquals(WidgetRegistry.ALL.size - 1, WidgetRegistry.offered(null, null).size)
    }

    @Test
    fun `nhu cau du lieu cua xe 3D bang dung ba nhom no doc`() {
        val groups = Car3dCatalog.GROUPS.map { CapabilityGroups.byId(it)!! }
        // R28: + mức sưởi/mát hai ghế (icon ghế trên xe 3D); số lốp đi riêng theo công tắc ([CarDataDemand.CAR3D_TYRES]).
        val seats = Car3dHero.Seat.values().flatMap { listOf(it.heatDatum, it.ventDatum) }
        assertEquals(groups.flatMap { it.reads }.toSet() + seats, CarDataDemand.CURATED.getValue("w_car3d"))
    }
}
