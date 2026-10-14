package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraSignalPolicy.CamView
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.92 · kế hoạch một phiên theo kiểu hình — HÌNH HỌC THẬT của khung HAL (5120×960, 4 dải 1280×960) ═══════════
 *
 * Spec `docs/specs/kachi-292-camera-full-view.html` R2/R3/R4/R7 · §4.3. Thứ tự dải `sau · trái · phải · trước`
 * ([CameraPanoCrop.defaultStrip]: trái = 1, phải = 2), hai gương đối xứng gương (dấu dịch theo bên).
 */
class CameraViewPlanTest {

    private val w = CameraPanoCrop.PANO_W
    private val h = CameraPanoCrop.PANO_H
    private val pct = CameraSignalPolicy.CIRCLE_PCT_DEFAULT
    private val rotations = listOf(0, -90, 90, 180)
    private val seal = CameraViewPlan.Knobs(focalPct = 55, kPct = 100, scalePct = 130)

    private fun view(left: Boolean) = if (left) CamView.MIRROR_LEFT else CamView.MIRROR_RIGHT

    private fun crops(mode: String, left: Boolean, span: String, shape: String) =
        CameraViewPlan.crops(mode, view(left), left, CameraPanoCrop.defaultStrip(left), null, span, shape, pct)

    // ── R2 · Nắn thẳng = 2.91 từng trường ──────────────────────────────────────────────────────────────────────

    /** Bản dựng 2.91 (`Prefs.cameraGlUniforms` d320ece) — cùng đối số, KHÔNG có `fit`/`mode`/`kappaPct`. */
    private fun legacy(crop: FloatArray?, strip: Int, rot: Int, mirror: Boolean, left: Boolean, k: CameraViewPlan.Knobs,
                       tex: Boolean): CameraGlUniforms {
        val c = CameraGlUniforms.sourceCentre(crop, strip)
        return CameraGlUniforms.of(
            crop = crop, srcCentreX = c[0], srcCentreY = c[1], streamW = w, streamH = h, rotationDeg = rot, flipH = mirror,
            amountPct = k.amountPct, focalPct = k.focalPct, kPct = k.kPct, scalePct = k.scalePct,
            centerXPct = k.centerXPct, centerYPct = k.centerYPct, panXPct = k.panXPct, panYPct = k.panYPct,
            panXSign = CameraDewarpPrefs.panXSign(left), texMatrix = tex,
        )
    }

    @Test fun `Nan thang 100 phan tram bang het bo uniform 2 91 o moi to hop`() {
        var n = 0
        for (left in listOf(true, false)) for (span in CameraSignalPolicy.SPANS) for (shape in CameraSignalPolicy.SHAPES)
            for (rot in rotations) for (mirror in listOf(false, true))
                for (k in listOf(CameraViewPlan.Knobs(), seal, seal.copy(amountPct = 0, panXPct = -20, centerXPct = 3))) {
                    val strip = CameraPanoCrop.defaultStrip(left)
                    val c = crops(CameraViewMode.STRAIGHT, left, span, shape)
                    val today = CameraPanoCrop.cropFor(view(left), left, strip, span, shape, pct)
                    assertTrue(c.frame.contentEquals(today) && c.content.contentEquals(today), "vùng cắt = hôm nay")
                    assertEquals(shape, c.frameShape)
                    val now = CameraViewPlan.gl(CameraViewMode.STRAIGHT, 100, c, strip, w, h, rot, mirror, left, k, true)
                    assertEquals(legacy(today, strip, rot, mirror, left, k, true), now, "$left $span $shape $rot $mirror $k")
                    assertTrue(now.fit.contentEquals(CameraGlUniforms.NO_FIT), "shader đi nhánh cũ")
                    assertEquals(1f, now.dewarp.kappa)
                    assertNull(CameraViewPlan.tvScale(CameraViewMode.STRAIGHT, 100, c, w, h, rot), "TV: ma trận y hệt")
                    n++
                }
        assertEquals(2 * 2 * 3 * 4 * 2 * 3, n)
    }

    // ── R3 · Gương cầu: trọn dải, vừa khung, không cắt ─────────────────────────────────────────────────────────

    @Test fun `Guong cau lay tron dai dung ben theo thu tu sau trai phai truoc`() {
        for (span in CameraSignalPolicy.SPANS) for (shape in CameraSignalPolicy.SHAPES) {
            val l = crops(CameraViewMode.FISHEYE, true, span, shape).content!!
            val r = crops(CameraViewMode.FISHEYE, false, span, shape).content!!
            assertArrayEquals(floatArrayOf(0.25f, 0f, 0.5f, 1f), l, 1e-6f, "gương trái = dải 1 (trái) trọn vẹn")
            assertArrayEquals(floatArrayOf(0.5f, 0f, 0.75f, 1f), r, 1e-6f, "gương phải = dải 2 (phải) trọn vẹn")
        }
    }

    @Test fun `Guong cau vua khung moi hinh moi goc xoay ca hai ben, bon goc dai nam trong khung`() {
        for (left in listOf(true, false)) for (span in CameraSignalPolicy.SPANS) for (shape in CameraSignalPolicy.SHAPES)
            for (rot in rotations) for (mirror in listOf(false, true)) {
                val strip = CameraPanoCrop.defaultStrip(left)
                val c = crops(CameraViewMode.FISHEYE, left, span, shape)
                val round = c.frameShape == CameraSignalPolicy.SHAPE_ROUND
                val gl = CameraViewPlan.gl(CameraViewMode.FISHEYE, 100, c, strip, w, h, rot, mirror, left, seal, true)
                assertEquals(0f, gl.dewarp.amount, "không nắn")
                assertEquals(0f, gl.dewarp.panX); assertEquals(0f, gl.dewarp.panY)
                assertTrue(CameraViewFit.contentInside(gl.fit, round), "$left $span $shape $rot: góc dải bị cắt ${gl.fit.toList()}")
                // Bốn góc của dải (toạ độ ô 0/1) phải tới được từ một điểm khung trong [0,1]² — đi đúng chuỗi shader.
                for ((cu, cv) in listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)) {
                    val du = 0.5f + (cu - 0.5f) / gl.fit[0]
                    val dv = 0.5f + (cv - 0.5f) / gl.fit[1]
                    assertTrue(du in -1e-4f..1.0001f && dv in -1e-4f..1.0001f, "góc ($cu,$cv) ra ngoài khung: ($du,$dv)")
                }
                if (!round) assertArrayEquals(floatArrayOf(1f, 1f), gl.fit, 1e-5f, "chữ nhật: khung khớp tỉ lệ dải, 0 viền")
            }
    }

    /** Hình TRÒN: chữ nhật dải 4:3 NỘI TIẾP đường tròn — 0,8 D × 0,6 D (§4.3), không mất một góc nào. */
    @Test fun `Guong cau hinh tron noi tiep dung 0 8 x 0 6`() {
        val c = crops(CameraViewMode.FISHEYE, true, CameraSignalPolicy.SPAN_STRIP, CameraSignalPolicy.SHAPE_ROUND)
        assertEquals(CameraSignalPolicy.SHAPE_ROUND, c.frameShape)
        val sq = CameraViewFit.cropPx(c.frame, w, h)!!
        assertEquals(sq[0], sq[1], 1e-3f, "khung tròn vẫn là ô vuông của hôm nay ⇒ cửa sổ không đổi chỗ, không đổi cỡ")
        val fit = CameraViewPlan.fit(CameraViewMode.FISHEYE, 100, c, w, h)
        assertArrayEquals(floatArrayOf(1.25f, 960f / 576f), fit, 1e-4f)
        assertArrayEquals(floatArrayOf(0.8f, 0.6f), CameraViewFit.tvScale(fit, 0), 1e-5f)
        assertArrayEquals(floatArrayOf(0.6f, 0.8f), CameraViewFit.tvScale(fit, -90), 1e-5f, "xoay ±90 đổi trục MÀN")
    }

    /** Thu phóng 120 % ở Gương cầu: phóng vào ⇒ góc dải bị cắt — đúng điều người lái chọn, không phải lỗi. */
    @Test fun `thu phong doi uFit deu hai truc`() {
        val c = crops(CameraViewMode.FISHEYE, true, CameraSignalPolicy.SPAN_STRIP, CameraSignalPolicy.SHAPE_RECT)
        val f120 = CameraViewPlan.fit(CameraViewMode.FISHEYE, 120, c, w, h)
        assertArrayEquals(floatArrayOf(1f / 1.2f, 1f / 1.2f), f120, 1e-5f)
        assertFalse(CameraViewFit.contentInside(f120, round = false))
        val f50 = CameraViewPlan.fit(CameraViewMode.FISHEYE, 50, c, w, h)
        assertArrayEquals(floatArrayOf(2f, 2f), f50, 1e-5f)
        assertArrayEquals(floatArrayOf(1f / 1.2f, 1f / 1.2f), CameraViewPlan.fit(CameraViewMode.STRAIGHT, 120, c, w, h), 1e-5f)
        assertArrayEquals(floatArrayOf(1.2f, 1.2f), CameraViewPlan.tvScale(CameraViewMode.STRAIGHT, 120, c, w, h, 0), 1e-5f)
        assertArrayEquals(CameraViewFit.zoomOnly(100), CameraGlUniforms.NO_FIT, "100 % ⇒ đúng NO_FIT")
    }

    // ── R4 · Thẳng rộng ────────────────────────────────────────────────────────────────────────────────────────

    /** ĐỔI GHIM (2.94 R1, có lý do): bộ mặc định theo phép TRỤ — F 100 → 122 %, dịch −20 → −15 %, `uCyl` bật. */
    @Test fun `Thang rong - tron dai, kappa 1 5, nan du, F 122, dich ve duoi theo ben, tru`() {
        for (left in listOf(true, false)) for (span in CameraSignalPolicy.SPANS) {
            val strip = CameraPanoCrop.defaultStrip(left)
            val c = crops(CameraViewMode.WIDE, left, span, CameraSignalPolicy.SHAPE_RECT)
            val gl = CameraViewPlan.gl(CameraViewMode.WIDE, 100, c, strip, w, h, 0, false, left, seal, true)
            assertEquals(1.5f, gl.dewarp.kappa)
            assertEquals(1f, gl.dewarp.amount)
            assertEquals(gl.dewarp.k * 1.22f, gl.dewarp.focal, 1e-6f, "F 122 % của bộ suy ra (≈ F 0,55 research)")
            assertTrue(gl.dewarp.cylinder, "Thẳng rộng = phép trụ (uCyl 1)")
            assertEquals(1.3f, gl.dewarp.scale, 1e-6f, "S của xe (mô hình ống kính) dùng chung")
            assertEquals(if (left) -0.15f else 0.15f, gl.dewarp.panX, 1e-6f, "đuôi: −x dải trái, +x dải phải")
            assertEquals(0f, gl.dewarp.panY)
            assertEquals(0.25f, kotlin.math.abs(gl.srcRect[2]), 1e-6f, "lấy TRỌN dải kể cả khi vùng gương là NARROW")
            assertArrayEquals(floatArrayOf(1f, 1f), gl.fit, 1e-5f)
            assertEquals(CameraViewMode.WIDE, gl.mode)
        }
        // Ba núm theo xe đi thẳng vào uniform.
        val c = crops(CameraViewMode.WIDE, true, CameraSignalPolicy.SPAN_STRIP, CameraSignalPolicy.SHAPE_ROUND)
        val k = seal.copy(wideKappaPct = 200, wideFocalPct = 140, widePanXPct = -30)
        val gl = CameraViewPlan.gl(CameraViewMode.WIDE, 100, c, 1, w, h, 0, false, true, k, true)
        assertEquals(2f, gl.dewarp.kappa); assertEquals(-0.3f, gl.dewarp.panX, 1e-6f)
        assertEquals(DewarpParams.DEFAULT_K * 1.4f, gl.dewarp.focal, 1e-5f)
        assertTrue(CameraViewFit.contentInside(gl.fit, round = true), "Thẳng rộng trong khung tròn cũng vừa khung")
    }

    /** Mọi kiểu hình × mọi hình khung: hình của phiên = ĐÚNG hình người lái chọn (không một phép ép nào ở tầng kế hoạch). */
    @Test fun `theo cum giu theo cum o moi kieu`() {
        for (mode in CameraViewMode.MODES) for (shape in CameraSignalPolicy.SHAPES) for (left in listOf(true, false)) {
            val c = crops(mode, left, CameraSignalPolicy.SPAN_STRIP, shape)
            assertEquals(shape, c.frameShape, "$mode $shape left=$left")
            if (CameraViewMode.fullView(mode) && shape != CameraSignalPolicy.SHAPE_ROUND) {
                assertTrue(c.frame.contentEquals(c.content), "kiểu trọn dải: khung = nội dung (không viền) — $mode $shape")
            }
        }
    }

    /**
     * ═══ CAMERA-STYLE-CLUSTER-RECT · hồi quy dựng từ LOG XE 14/10 (Seal, 3.1.4) ═══════════════════════════════════════
     *
     * Owner 14/10: *"camera xi nhan, chọn kiểu thể hiện theo cụm nó vẫn chữ nhật"*. [ĐO] mọi phiên camera trên xe từ 07/10
     * tới 14/10 ghi `kiểu=WIDE` (logcat 14/10 08:00:34 · 08:03:20 · 08:05:29 …): với kiểu ấy, phép ép R7 cũ
     * (`CameraViewMode.frameShape`) biến *Theo cụm* thành chữ nhật ⇒ chọn *Theo cụm* KHÔNG BAO GIỜ có tác dụng.
     *
     * Bộ số lấy nguyên văn từ dòng log: nguồn 5120×960 · `crop=0.25,0,0.5,1` rot −90 (trái) và `0.5,0,0.75,1` rot 90
     * (phải) · `chỗ=TL` cho CẢ HAI camera · `dải=140,132-1780,560` · display 1920×720 · `kết xuất=GL` (letterbox =
     * kiểu trọn dải). Chữ nhật phải ra đúng `cửa=321x428 tại=140,132 lớp=321x428` như log; *Theo cụm* phải NỚI ra kính
     * (mép cong, x < 140) và phóng-cắt (lớp cao hơn cửa) — đúng hình đã chạy trên xe 06/10 với *Nắn thẳng*
     * (`cửa=391x428 tại=19,132 lớp=391x521`). Bài này ĐỎ khi khôi phục phép ép (khung ra `RECT`).
     */
    @Test fun `log xe 14-10 - Thang rong + Theo cum tren cum ra theo cum, khong ra chu nhat`() {
        val spec = ClusterBandSpec.SEAL_DL3
        val band = CameraClusterBand.band(1920, 720, spec)
        assertEquals(listOf(140, 132, 1780, 560), listOf(band.x0, band.y0, band.x1, band.y1), "dải = log `dải=140,132-1780,560`")
        val leftEdge = CameraClusterBand.leftEdge(band, spec, 1920)
        val rightEdge = CameraClusterBand.rightEdge(band, spec, 1920)
        // (bên nguồn, dải, crop log, xoay log) — camera PHẢI đi nguồn MIRROR_LEFT dải 2 như log `→ MIRROR_LEFT camId=1`.
        val cams = listOf(
            Triple(true, 1, -90) to floatArrayOf(0.25f, 0f, 0.5f, 1f),
            Triple(false, 2, 90) to floatArrayOf(0.5f, 0f, 0.75f, 1f),
        )
        for ((cam, logCrop) in cams) {
            val (left, strip, rot) = cam
            for (mode in listOf(CameraViewMode.WIDE, CameraViewMode.FISHEYE)) {
                fun place(shape: String): CameraClusterBand.Placement {
                    val c = CameraViewPlan.crops(mode, CamView.MIRROR_LEFT, left, strip, strip,
                        CameraSignalPolicy.SPAN_STRIP, shape, pct)
                    assertArrayEquals(logCrop, c.content, 1e-6f, "nội dung = `nội-dung=` của log")
                    assertEquals(shape, c.frameShape, "hình của phiên = lựa chọn ($mode $shape)")
                    val eff = CameraClusterBand.effectiveShape(c.frameShape, onCluster = true)
                    // `chỗ=TL` ⇒ atLeft = true cho cả hai camera (CameraOverlayView: corner == TL).
                    return CameraClusterBand.place(band, true, w, h, c.frame, rot, spec, 720, eff,
                        leftEdge, rightEdge, fitInside = CameraViewMode.fullView(mode))
                }
                val rect = place(CameraSignalPolicy.SHAPE_RECT)
                assertEquals(listOf(140, 132, 321, 428, 321, 428),
                    listOf(rect.x, rect.y, rect.w, rect.h, rect.layerW, rect.layerH), "chữ nhật = log 14/10 ($mode left=$left)")
                val clus = place(CameraSignalPolicy.SHAPE_CLUSTER)
                assertEquals(CameraSignalPolicy.SHAPE_CLUSTER, clus.shape, "$mode left=$left")
                assertTrue(clus.leftEdge.isNotEmpty(), "mép trái mang bảng cong ⇒ mặt nạ bám kính ($mode left=$left)")
                assertTrue(clus.x < rect.x, "nới ra tới kính: x ${clus.x} < ${rect.x}")
                assertEquals(rect.h, clus.h, "vẫn cao trọn dải")
                assertTrue(clus.layerH > clus.h, "phóng-cắt (cover) để bám cong, không kéo giãn: lớp ${clus.layerH} > cửa ${clus.h}")
                assertTrue(CameraClusterBand.fadePx(clus) > 0, "mép trong mờ dần — chỉ hình theo cụm có")
            }
        }
    }

    // ── tầng vẽ: ma trận TV + đặt cửa sổ trên cụm ──────────────────────────────────────────────────────────────

    @Test fun `ma tran TV - khong ti le thi y het, co ti le thi noi dung chiem dung phan khung`() {
        val crop = floatArrayOf(0.25f, 0f, 0.5f, 1f)
        for (rot in rotations) {
            assertArrayEquals(
                CameraOverlayTransform.matrix(400, 400, crop, rot, mirror = true),
                CameraOverlayTransform.matrix(400, 400, crop, rot, mirror = true, scale = null),
            )
        }
        val m = CameraOverlayTransform.matrix(400, 400, crop, 0, scale = floatArrayOf(0.8f, 0.6f))
        val tl = CameraOverlayTransform.mapSource(m, 400, 400, 0.25f, 0f)
        val br = CameraOverlayTransform.mapSource(m, 400, 400, 0.5f, 1f)
        assertArrayEquals(floatArrayOf(40f, 80f), tl, 1e-3f, "góc trên-trái dải ở (0,1·W ; 0,2·H)")
        assertArrayEquals(floatArrayOf(360f, 320f), br, 1e-3f, "320×240 = đúng 4:3, nằm giữa")
    }

    @Test fun `dat tren cum - fitInside trung tall voi dai 4 3, khong phong cat voi nguyen khung`() {
        val band = CameraClusterBand.band(1920, 720, ClusterBandSpec.SEAL_DL3)
        val strip = floatArrayOf(0.25f, 0f, 0.5f, 1f)
        for (rot in rotations) {
            val a = CameraClusterBand.place(band, true, w, h, strip, rot, ClusterBandSpec.SEAL_DL3, 720,
                CameraSignalPolicy.SHAPE_RECT)
            val b = CameraClusterBand.place(band, true, w, h, strip, rot, ClusterBandSpec.SEAL_DL3, 720,
                CameraSignalPolicy.SHAPE_RECT, fitInside = true)
            assertEquals(a, b, "dải 4:3 / 3:4 ⇒ Seal không đổi chỗ đứng (rot $rot)")
        }
        val whole = CameraClusterBand.place(band, true, w, h, null, 0, ClusterBandSpec.SEAL_DL3, 720,
            CameraSignalPolicy.SHAPE_RECT, fitInside = true)
        // Nguyên khung 5,33:1 ⇒ lớp video = cửa sổ (sai số làm tròn ≤ 1 px), không phóng-cắt như nhánh `tall`.
        assertTrue(whole.layerW - whole.w <= 1 && whole.layerH - whole.h <= 1, "lớp ${whole.layerW}x${whole.layerH} cửa ${whole.w}x${whole.h}")
        assertTrue(whole.h < band.h, "hạ chiều cao thay vì cắt hai mép")
        assertTrue(CameraClusterBand.insideBand(whole))
    }

    // ── 2.93 · camera theo yêu cầu: dấu dịch-x theo CAMERA ─────────────────────────────────────────────────────

    /**
     * Mặc định `panXSign` = theo [left] ⇒ hai camera gương y hệt 2.92 từng trường; camera GIỮA (`0`) ⇒ hai núm dịch-x
     * theo bên (`camera_dewarp_pan_x` · `camera_wide_pan_x`) KHÔNG áp — không có *"phía đuôi theo bên"* cho camera sau/trước.
     */
    @Test fun `dau dich theo camera - guong y nhu cu, camera giua khong dich`() {
        val k = seal.copy(panXPct = -20, panYPct = 7, widePanXPct = -20)
        for (mode in CameraViewMode.MODES) for (left in listOf(true, false)) {
            val c = crops(mode, left, CameraSignalPolicy.SPAN_STRIP, CameraSignalPolicy.SHAPE_RECT)
            val strip = CameraPanoCrop.defaultStrip(left)
            val implicit = CameraViewPlan.gl(mode, 100, c, strip, w, h, 0, false, left, k, true)
            val explicit = CameraViewPlan.gl(mode, 100, c, strip, w, h, 0, false, left, k, true,
                panXSign = if (left) CameraWhich.LEFT.panXSign else CameraWhich.RIGHT.panXSign)
            assertEquals(implicit, explicit, "$mode $left: camera gương = 2.92")
        }
        for (mode in CameraViewMode.MODES) for (which in listOf(CameraWhich.REAR, CameraWhich.FRONT)) {
            val c = CameraViewPlan.crops(mode, CamView.MIRROR_LEFT, true, which.strip, null, CameraSignalPolicy.SPAN_STRIP,
                CameraSignalPolicy.SHAPE_RECT, pct)
            assertArrayEquals(CameraPanoCrop.stripCrop(which.strip), c.content, 1e-6f, "$which: trọn dải ${which.strip}")
            val u = CameraViewPlan.gl(mode, 100, c, which.strip, w, h, 0, false, true, k, true, panXSign = which.panXSign)
            assertEquals(0f, u.panX, "$mode $which: không dịch-x theo bên")
            val noKnobs = CameraViewPlan.gl(mode, 100, c, which.strip, w, h, 0, false, true, k.copy(panXPct = 0, widePanXPct = 0), true,
                panXSign = which.panXSign)
            assertEquals(noKnobs, u, "$mode $which: núm dịch-x theo bên không đổi gì cho camera giữa")
        }
    }
}
