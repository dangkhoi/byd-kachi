package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.launcher.camera.CameraGlUniforms
import com.byd.clusternav.launcher.camera.CameraPanoCrop
import com.byd.clusternav.launcher.camera.CameraProfileDefaults
import com.byd.clusternav.launcher.camera.CameraSignalPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.76 · R2 — hồ sơ → mặc định camera → uniform: Seal DL3 = bộ [ĐO 27/09], đời khác = trung tính ═══════════════
 *
 * AC của spec: *"test profile → mặc định → uniform cho DL3; trung tính cho một đời giả"*. Số ở đây là số **owner đã
 * duyệt bằng mắt trên xe** (backlog `ONCAR-2026-09-27`: *"thẳng, tự nhiên, thấy 2 bánh"*), không phải phép suy.
 */
class ClusterProfileCameraDefaultsTest {

    private val P = CameraSignalPolicy

    @Test fun `Seal DL3 mang dung bo do tren xe 27 09`() {
        val c = ClusterProfile.SEAL_DL3.camera
        assertEquals(ClusterProfile.SEAL_DL3_CAMERA, c)
        assertEquals(P.SPAN_STRIP, c.span, "[ĐO] vệt hẹp 0,10 là rìa vòng fisheye — trọn dải mới thẳng")
        assertEquals(P.RENDER_GL, c.render, "[ĐO CAM-B1] Adreno 610 đủ trần texture ⇒ đường nắn chạy được")
        assertEquals(100, c.amountPct); assertEquals(55, c.focalPct); assertEquals(100, c.kPct); assertEquals(130, c.scalePct)
        assertEquals(0, c.centerXPct, "[ĐO] cx ≠ 0 làm cong — owner bác tại chỗ"); assertEquals(0, c.centerYPct)
        assertEquals(0, c.panXPct); assertEquals(0, c.panYPct)
        assertEquals(P.ROTATE_NONE, c.rotation(left = true), "research §6.1: 18/20 hệ hiện ĐỨNG, khung HAL vốn đứng")
        assertEquals(P.ROTATE_NONE, c.rotation(left = false))
        assertEquals(2, c.channel(left = true), "[ĐO 11:16] kênh 2 = gương TRÁI (cột E4)")
        assertEquals(3, c.channel(left = false), "[ĐO 11:16] kênh 3 = gương PHẢI (cột E3)")
        assertTrue(c.hasChannelMap)
        assertEquals(c, c.sane(), "bộ đo phải nằm trọn trong miền — không trường nào bị sane() sửa")
    }

    /** Bộ Seal đi vào phép hợp uniform: F = 0,55·base · K = base · S = 1,30·base, tâm/dịch không đổi. */
    @Test fun `Seal DL3 vao uniform ra F55 K100 S130`() {
        val c = ClusterProfile.SEAL_DL3.camera
        val crop = CameraPanoCrop.stripCrop(1)
        val base = CameraGlUniforms.of(
            crop = crop, srcCentreX = CameraPanoCrop.stripCentre(1).toFloat(), srcCentreY = 0.5f,
            streamW = 5120, streamH = 960, rotationDeg = P.rotationDegrees(c.rotation(true), left = true),
        )
        val seal = CameraGlUniforms.of(
            crop = crop, srcCentreX = CameraPanoCrop.stripCentre(1).toFloat(), srcCentreY = 0.5f,
            streamW = 5120, streamH = 960, rotationDeg = P.rotationDegrees(c.rotation(true), left = true),
            amountPct = c.amountPct, focalPct = c.focalPct, kPct = c.kPct, scalePct = c.scalePct,
            centerXPct = c.centerXPct, centerYPct = c.centerYPct, panXPct = c.panXPct, panYPct = c.panYPct,
        )
        assertEquals(0f, seal.rotationDeg, "Seal mặc định ĐỨNG")
        assertEquals(base.dewarp.focal * 0.55f, seal.dewarp.focal, 1e-5f)
        assertEquals(base.dewarp.k, seal.dewarp.k, 1e-6f)
        assertEquals(base.dewarp.scale * 1.30f, seal.dewarp.scale, 1e-5f)
        assertEquals(1f, seal.dewarp.amount, 1e-6f)
        assertEquals(base.centerX, seal.centerX, 1e-6f); assertEquals(base.centerY, seal.centerY, 1e-6f)
        assertEquals(0f, seal.dewarp.panX, 1e-6f); assertEquals(0f, seal.dewarp.panY, 1e-6f)
    }

    /** Đời khác (DL5 · generic · hồ sơ tự nhập) = TRUNG TÍNH = 2.75, và không có bản đồ kênh ⇒ hàng Nguồn ẩn. */
    @Test fun `doi khac trung tinh, khong ban do kenh`() {
        listOf(ClusterProfile.DL5, ClusterProfile.GENERIC_FALLBACK).forEach {
            assertEquals(CameraProfileDefaults.NEUTRAL, it.camera, "${it.id} phải trung tính — bộ Seal không được chép sang xe chưa đo")
            assertFalse(it.camera.hasChannelMap)
        }
        val custom = requireNotNull(ClusterProfile.parse("sl6_dl3;3;1600;600;16-35;;fission"))
        assertEquals(CameraProfileDefaults.NEUTRAL, custom.camera, "id lạ ⇒ trung tính")
        // Đời "giả" qua detectSeed: không phải BYD ⇒ generic ⇒ trung tính.
        assertEquals(CameraProfileDefaults.NEUTRAL, ClusterProfile.detectSeed("Pixel 6", "Google", "Google", "").camera)
        assertEquals(CameraProfileDefaults.NEUTRAL, ClusterProfile.detectSeed("BYD AUTO", "byd", "byd", "dilink5").camera)
        // Xe BYD DL3 ⇒ bộ Seal.
        assertEquals(ClusterProfile.SEAL_DL3_CAMERA, ClusterProfile.detectSeed("BYD AUTO", "byd", "byd", "dilink3").camera)
    }

    /** Camera KHÔNG nằm trong chuỗi share; parse gán lại theo id ⇒ round-trip seed vẫn giữ bộ đo. */
    @Test fun `camera khong vao chuoi export, round trip theo id`() {
        val export = ClusterProfile.SEAL_DL3.export()
        assertFalse("STRIP" in export || "GL" in export, "chuỗi share là 'chiếu cụm thế nào', không mang bộ camera")
        assertEquals(ClusterProfile.SEAL_DL3, ClusterProfile.parse(export))
        assertEquals(ClusterProfile.SEAL_DL3_CAMERA, ClusterProfile.cameraFor("seal_dl3"))
        assertEquals(CameraProfileDefaults.NEUTRAL, ClusterProfile.cameraFor("dilink5"))
    }
}
