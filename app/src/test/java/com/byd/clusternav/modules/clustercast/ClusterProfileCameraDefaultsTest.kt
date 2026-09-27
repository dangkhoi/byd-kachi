package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.launcher.camera.CameraClusterBand
import com.byd.clusternav.launcher.camera.CameraGlUniforms
import com.byd.clusternav.launcher.camera.CameraPanoCrop
import com.byd.clusternav.launcher.camera.CameraProfileDefaults
import com.byd.clusternav.launcher.camera.CameraSignalPolicy
import com.byd.clusternav.launcher.camera.ClusterBandSpec
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

    /** Đời khác (DL5 · generic · hồ sơ tự nhập) = TRUNG TÍNH = 2.75. */
    @Test fun `doi khac trung tinh`() {
        listOf(ClusterProfile.DL5, ClusterProfile.GENERIC_FALLBACK).forEach {
            assertEquals(CameraProfileDefaults.NEUTRAL, it.camera, "${it.id} phải trung tính — bộ Seal không được chép sang xe chưa đo")
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

    /**
     * ═══ 2.77 — ĐƯỜNG CONG KÍNH chỉ đi theo đời ĐÃ ĐO, không phải mặc định dùng chung ═════════════════════════════
     *
     * Khoá lại đúng lỗi điều phối bắt được lúc gộp hai làn: làn L2 nhét bảng `leftEdge` [ĐO trên kính Seal DL3] vào
     * chính `ClusterBandSpec.SEAL_DL3`, mà `ClusterProfile.band` lại lấy bộ ấy làm **mặc định cho mọi đời** (từ
     * 2.76, khi bộ ấy chỉ còn là 4 số ĐẶT cửa sổ). Từ 2.77 bảng ấy **CẮT** điểm ảnh (`CameraOverlayMask.glassMask`)
     * tới 122 px bên trái ⇒ một cụm DiLink5 (đường tới được thật: `detectSeed` trả [ClusterProfile.DL5] cho xe BYD
     * DL5) sẽ bị xén ảnh theo miếng kính của xe khác, âm thầm. CLAUDE.md §7: khác biệt đời xe nằm trong hồ sơ.
     *
     * Rơi về = mất đường cong (tường thẳng 2.76), KHÔNG mất camera — đó là điều kiện `leftEdge.isEmpty()` mà
     * `CameraClusterBand.leftEdge`/`glassMask` đã có sẵn.
     */
    @Test fun `duong cong kinh chi cho doi DA DO, doi khac tuong thang 2 76`() {
        assertEquals(ClusterBandSpec.SEAL_DL3, ClusterProfile.SEAL_DL3.band, "đời đã đo mang bảng mép cong")
        assertTrue(ClusterProfile.SEAL_DL3.band.leftEdge.isNotEmpty())
        assertTrue(ClusterProfile.SEAL_DL3.band.rightEdge.isNotEmpty(), "2.78: đời đã đo mang CẢ mép phải")
        listOf(ClusterProfile.DL5, ClusterProfile.GENERIC_FALLBACK).forEach {
            assertEquals(emptyList<Int>(), it.band.leftEdge, "${it.id} chưa đo kính ⇒ KHÔNG được xén theo kính Seal")
            assertEquals(emptyList<Int>(), it.band.rightEdge, "${it.id}: mép phải cũng vậy (2.78)")
            assertEquals(ClusterBandSpec.SEAL_DL3_NO_CURVE, it.band)
        }
        // Bốn số ĐẶT cửa sổ thì vẫn dùng chung (quyết định 2.76, không đổi): chỉ đường CẮT là theo đời.
        assertEquals(ClusterBandSpec.SEAL_DL3.left, ClusterBandSpec.SEAL_DL3_NO_CURVE.left)
        assertEquals(ClusterBandSpec.SEAL_DL3.right, ClusterBandSpec.SEAL_DL3_NO_CURVE.right)
        assertEquals(ClusterBandSpec.SEAL_DL3.top, ClusterBandSpec.SEAL_DL3_NO_CURVE.top)
        assertEquals(ClusterBandSpec.SEAL_DL3.bottom, ClusterBandSpec.SEAL_DL3_NO_CURVE.bottom)
        assertEquals(ClusterBandSpec.SEAL_DL3.radiusPx, ClusterBandSpec.SEAL_DL3_NO_CURVE.radiusPx)
        // id lạ dán từ chat + đời BYD DL5 thật: cả hai đều phải là tường thẳng.
        val custom = requireNotNull(ClusterProfile.parse("sl6_dl3;3;1600;600;16-35;;fission"))
        assertEquals(emptyList<Int>(), custom.band.leftEdge, "chuỗi owner dán không mang kính của Seal")
        assertEquals(emptyList<Int>(), ClusterProfile.detectSeed("BYD AUTO", "byd", "byd", "dilink5").band.leftEdge)
        assertEquals(ClusterBandSpec.SEAL_DL3.leftEdge, ClusterProfile.detectSeed("BYD AUTO", "byd", "byd", "dilink3").band.leftEdge)
        // Cửa duy nhất, cùng khuôn với `cameraFor`.
        assertEquals(ClusterBandSpec.SEAL_DL3, ClusterProfile.bandFor("seal_dl3"))
        assertEquals(ClusterBandSpec.SEAL_DL3_NO_CURVE, ClusterProfile.bandFor("dilink5"))
    }

    /** Tường thẳng 2.76 nghĩa là: cửa sổ ở đúng `band.x0`/`band.x1` và KHÔNG có gì để cắt ⇒ `glassMask` trả `null`. */
    @Test fun `tuong thang thi cua so o x0 va khong co mat na`() {
        val spec = ClusterProfile.DL5.band
        val band = CameraClusterBand.band(1920, 720, spec)
        val edge = CameraClusterBand.leftEdge(band, spec, 1920)
        val edgeRight = CameraClusterBand.rightEdge(band, spec, 1920)
        assertEquals(emptyList<Int>(), edge, "bảng rỗng ⇒ không nội suy gì")
        assertEquals(emptyList<Int>(), edgeRight, "bảng phải cũng rỗng (2.78)")
        val p = CameraClusterBand.place(
            band = band, atLeft = true, streamW = 5120, streamH = 960,
            crop = CameraPanoCrop.stripCrop(1), rotationDeg = 0, spec = spec, displayH = 720,
            leftEdge = edge, rightEdge = edgeRight,
        )
        assertEquals(band.x0, p.x, "đúng tường thẳng 140 của 2.76")
        assertEquals(emptyList<Int>(), p.leftEdge)
        assertEquals(band.x0, CameraClusterBand.maskLeftAt(p, p.y + p.h / 2), "mép có mực = tường thẳng")
        assertTrue(CameraClusterBand.insideBand(p), "bất biến [P1] 2.76 còn nguyên")
        val pr = CameraClusterBand.place(
            band = band, atLeft = false, streamW = 5120, streamH = 960,
            crop = CameraPanoCrop.stripCrop(2), rotationDeg = 0, spec = spec, displayH = 720,
            leftEdge = edge, rightEdge = edgeRight,
        )
        assertEquals(band.x1, pr.x + pr.w, "bên PHẢI cũng đúng tường thẳng 1780 của 2.77")
        assertEquals(emptyList<Int>(), pr.rightEdge)
        assertEquals(band.x1, CameraClusterBand.maskRightAt(pr, pr.y + pr.h / 2), "mép có mực = tường thẳng")
        assertTrue(CameraClusterBand.insideBand(pr))
    }
}
