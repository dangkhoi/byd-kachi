package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraSignalPolicy.CamView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.tan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.92 · κ — họ phép chiếu khung ra (`θ = κ·atan(r, κ·F)`) — khoá bằng số ═════════════════════════════════════
 *
 * Spec `docs/specs/kachi-292-camera-full-view.html` R2/R4 · research `camera-rear-coverage-2026-10-06.md` §4.3/§9.
 * Bốn bài học được khoá ở đây:
 *  1. **κ = 1 trùng TỪNG BIT** công thức 2.74–2.91 (bản sao nguyên văn ở [legacyMapDstToSrc]) — *Nắn thẳng* không đổi
 *     một pixel (CLAUDE.md §6).
 *  2. κ = 2 là **stereographic** đóng (`r_dst = 2F·tan(θ/2)`), nghịch đảo khứ hồi ở κ ∈ {1; 1,5; 2}.
 *  3. Nguyên nhân lời phàn nàn 06/10: bộ Seal hôm nay dừng ở mép đuôi **x = 140,6 px ⇒ θ 76°** (@376 px/rad).
 *  4. Bộ *Thẳng rộng* mặc định (2.94 R1: phép TRỤ, [CameraDewarpCylinder]) tới **84–90°** phía đuôi ở hàng giữa, hàng
 *     giữa **không đen**, toàn khung đen **≤ 2 %**, ở CẢ hai gương (đối xứng gương: đuôi −x ở dải trái, +x ở dải phải).
 */
class CameraDewarpKappaTest {

    private val streamW = CameraPanoCrop.PANO_W
    private val streamH = CameraPanoCrop.PANO_H

    /** Bản sao NGUYÊN VĂN `CameraDewarp.mapDstToSrc` của 2.91 (`d320ece`, `CameraDewarp.kt:301-317`). */
    private fun legacyMapDstToSrc(u: Float, v: Float, p: DewarpParams, aspect: Float): Pair<Float, Float> {
        if (!p.enabled) return u to v
        val asp = if (aspect > 0f && !aspect.isNaN() && !aspect.isInfinite()) aspect else 1f
        val px = (u - p.centerX) * 2f
        val py = (v - p.centerY) * 2f / asp
        val rDst = hypot(px.toDouble(), py.toDouble()).toFloat()
        if (rDst <= 1e-6f) return u to v
        val dirX = px / rDst
        val dirY = py / rDst
        val theta = atan2(rDst.toDouble(), p.focal.toDouble())
        val rSrc = (p.gain.toDouble() * theta).toFloat()
        val projX = p.centerX + dirX * rSrc * 0.5f
        val projY = p.centerY + dirY * rSrc * 0.5f * asp
        val a = p.amount.coerceIn(0f, 1f)
        return (u + (projX - u) * a) to (v + (projY - v) * a)
    }

    private val sealStraight = CameraViewPlan.Knobs(focalPct = 55, kPct = 100, scalePct = 130)

    private fun seal(left: Boolean, mode: String, rot: Int = 0, shape: String = CameraSignalPolicy.SHAPE_RECT):
        Pair<CameraViewPlan.Crops, CameraGlUniforms> {
        val strip = CameraPanoCrop.defaultStrip(left)
        val view = if (left) CamView.MIRROR_LEFT else CamView.MIRROR_RIGHT
        val crops = CameraViewPlan.crops(mode, view, left, strip, null, CameraSignalPolicy.SPAN_STRIP, shape,
            CameraSignalPolicy.CIRCLE_PCT_DEFAULT)
        val gl = CameraViewPlan.gl(mode, CameraViewMode.ZOOM_DEFAULT, crops, strip, streamW, streamH, rot,
            mirror = false, left = left, knobs = sealStraight, texMatrix = true)
        return crops to gl
    }

    /** Điểm khung `(u,v)` → px trong DẢI (0..1280 × 0..960) hoặc `null` = đen. */
    private fun stripPx(u: Float, v: Float, crops: CameraViewPlan.Crops, gl: CameraGlUniforms, rot: Int = 0): Pair<Float, Float>? {
        val s = CameraDewarp.sample(u, v, rot, gl.dewarp, gl.aspect, CameraDewarp.srcRect(crops.content), gl.fit)
            ?: return null
        val c = crops.content!!
        return ((s.first - c[0]) / (c[2] - c[0]) * 1280f) to ((s.second - c[1]) / (c[3] - c[1]) * 960f)
    }

    /** θ theo giả thuyết H1 (f-θ thật ≈ K·S = 376,3 px/rad, research §2.2) — [SUY], dùng làm thước đo chung. */
    private fun thetaDeg(xPx: Float, yPx: Float): Double {
        val fTheta = DewarpParams.DEFAULT_K * 1.3 * 640.0
        return Math.toDegrees(hypot(xPx - 640.0, yPx - 480.0) / fTheta)
    }

    // ── (1) κ = 1 trùng từng bit ────────────────────────────────────────────────────────────────────────────────

    @Test fun `kappa 1 trung tung bit voi cong thuc 2 91`() {
        val sets = listOf(
            DewarpParams(),
            DewarpParams(amount = 1f, focal = 0.2488f, k = 0.452335f, scale = 1.3f),
            DewarpParams(amount = 0.5f, focal = 0.3f, k = 1.130836f, scale = 1f, centerX = 1.25f),
            DewarpParams(amount = 1f, focal = 0.45f, k = 0.6f, scale = 1.1f, centerX = -0.25f, centerY = 0.6f),
        ).map { it.clamped() }
        var n = 0
        for (p in sets) for (aspect in listOf(1f, 1280f / 960f, 512f / 960f)) {
            for (i in 0..20) for (j in 0..20) {
                val u = -0.2f + i * 0.07f
                val v = -0.2f + j * 0.07f
                val now = CameraDewarp.mapDstToSrc(u, v, p, aspect)
                val old = legacyMapDstToSrc(u, v, p, aspect)
                assertEquals(old.first.toBits(), now.first.toBits(), "x tại ($u,$v) $p")
                assertEquals(old.second.toBits(), now.second.toBits(), "y tại ($u,$v) $p")
                n++
            }
        }
        assertEquals(4 * 3 * 21 * 21, n)
        assertEquals(1f, DewarpParams().kappa, "κ mặc định = 1")
    }

    /** `fit = null` và `fit = NO_FIT` đều là ĐÚNG đường cũ (không phép `0.5 + (u − 0.5)·1` nào chen vào). */
    @Test fun `sample khong vua khung la duong cu tung bit`() {
        val p = DewarpParams(amount = 1f, focal = 0.2488f, k = 0.452335f, scale = 1.3f, panX = -0.15f).clamped()
        val rect = CameraDewarp.srcRect(floatArrayOf(0.25f, 0f, 0.5f, 1f))
        for (rot in listOf(0, -90, 90, 180)) for (i in 0..10) for (j in 0..10) {
            val u = i / 10f
            val v = j / 10f
            val a = CameraDewarp.sample(u, v, rot, p, 1280f / 960f, rect)
            val b = CameraDewarp.sample(u, v, rot, p, 1280f / 960f, rect, CameraGlUniforms.NO_FIT)
            assertEquals(a?.first?.toBits(), b?.first?.toBits())
            assertEquals(a?.second?.toBits(), b?.second?.toBits())
        }
    }

    // ── (2) κ = 2 stereographic · khứ hồi ──────────────────────────────────────────────────────────────────────

    @Test fun `kappa 2 la stereographic dong`() {
        val p = DewarpParams(amount = 1f, focal = 0.5f, k = 0.45f, scale = 1.3f, kappa = 2f).clamped()
        for (deg in listOf(10, 45, 76, 90, 96, 120)) {
            val theta = Math.toRadians(deg.toDouble())
            val rDst = 2.0 * p.focal * tan(theta / 2.0)          // phép thuận stereographic
            val (a, _) = CameraDewarp.mapDstToSrc((p.centerX + rDst / 2.0).toFloat(), p.centerY, p, 1f)
            val rSrc = (a - p.centerX) * 2.0
            assertEquals(p.gain * theta, rSrc, 1e-5, "θ $deg° ⇒ r_src = K·S·θ")
        }
    }

    @Test fun `nghich dao khu hoi o kappa 1, 1 5, 2`() {
        for (kappa in listOf(1f, 1.5f, 2f)) {
            val p = DewarpParams(amount = 1f, focal = 0.45f, k = 0.452335f, scale = 1.3f, kappa = kappa).clamped()
            for (i in 1..9) {
                val rSrcWanted = 0.1f * i                         // tới 0,9 nửa-bề-ngang ≈ 88° ở 0,588
                val src = (0.5f + rSrcWanted / 2f) to 0.5f
                val dst = CameraDewarp.forwardSrcToDst(src.first, src.second, p, 1f)
                if (kappa == 1f && rSrcWanted >= p.reachableSrcRadius) { assertNull(dst); continue }
                assertNotNull(dst, "κ $kappa r_src $rSrcWanted phải với tới")
                val back = CameraDewarp.mapDstToSrc(dst!!.first, dst.second, p, 1f)
                assertEquals(src.first, back.first, 2e-5f, "κ $kappa khứ hồi x")
            }
            assertEquals((p.gain * kappa * PI / 2).toFloat(), p.reachableSrcRadius, 1e-6f, "tầm với = K·S·κ·π/2")
        }
    }

    @Test fun `kappa duoc kep vao 1 den 8`() {
        assertEquals(1f, DewarpParams(kappa = 0.2f).clamped().kappa)
        assertEquals(8f, DewarpParams(kappa = 50f).clamped().kappa)
        assertEquals(1f, DewarpParams(kappa = Float.NaN).clamped().kappa, "NaN ⇒ mặc định, không đen câm")
        assertEquals(1.5f, CameraDewarpPrefs.apply(DewarpParams(), 0.5f, 0.5f, kappaPct = 150).kappa)
        assertEquals(1f, CameraDewarpPrefs.apply(DewarpParams(), 0.5f, 0.5f, kappaPct = 90).kappa, "ngoài miền ⇒ 100 %")
    }

    // ── (3) gốc lời phàn nàn 06/10 ─────────────────────────────────────────────────────────────────────────────

    @Test fun `bo Seal Nan thang dung o mep duoi 76 do — goc loi cat hoi lo`() {
        val (crops, gl) = seal(left = true, mode = CameraViewMode.STRAIGHT)
        val tail = stripPx(0f, 0.5f, crops, gl)!!
        assertEquals(140.6f, tail.first, 0.5f, "mép đuôi khung hôm nay = x 140,6 px của dải (research K2)")
        assertEquals(76.0, thetaDeg(tail.first, tail.second), 0.2)
        assertEquals(1f, gl.dewarp.kappa)
        assertTrue(gl.fit.contentEquals(CameraGlUniforms.NO_FIT), "Nắn thẳng 100 % ⇒ không vừa khung")
    }

    // ── (4) bộ Thẳng rộng mặc định ─────────────────────────────────────────────────────────────────────────────

    /**
     * ĐỔI GHIM (2.94 R1, có lý do): *Thẳng rộng* của camera gương nay là phép TRỤ (owner 07/10). Mép đuôi ≥ 93° của κ
     * xuyên tâm 2.92 KHÔNG còn đạt được — điểm tụ của đường song song thân xe ở đúng 90°, phép chiếu giữ chúng thẳng
     * không vẽ được quá đó (research README §2) ⇒ cửa sổ mới 84–90° (research 86,3°).
     */
    @Test fun `Thang rong mac dinh toi sau xa, hang giua khong den, ca hai guong`() {
        for (left in listOf(true, false)) {
            val (crops, gl) = seal(left = left, mode = CameraViewMode.WIDE)
            assertEquals(1.5f, gl.dewarp.kappa)
            assertEquals(1f, gl.dewarp.amount)
            assertTrue(gl.dewarp.cylinder, "Thẳng rộng = phép trụ")
            val tailU = if (left) 0f else 1f                      // đuôi: −x dải trái, +x dải phải (research §2.1)
            val tail = stripPx(tailU, 0.5f, crops, gl)
            assertNotNull(tail, "mép đuôi không được đen")
            val tailDeg = thetaDeg(tail!!.first, tail.second)
            assertTrue(tailDeg in 84.0..90.0, "mép đuôi $tailDeg° ngoài 84–90°")
            for (i in 0..400) assertNotNull(stripPx(i / 400f, 0.5f, crops, gl), "hàng giữa đen tại u=${i / 400f}")
            var black = 0
            for (i in 0..120) for (j in 0..120) if (stripPx(i / 120f, j / 120f, crops, gl) == null) black++
            assertTrue(black <= 0.02 * 121 * 121, "đen toàn khung $black/${121 * 121}")
        }
    }

    /**
     * Ba tính chất điều phối đòi cho "Thẳng rộng": **tâm không méo** (độ phóng xuyên tâm = tiếp tuyến tại tâm quang),
     * **đơn điệu** (đi từ tâm ra mép đuôi thì bán kính nguồn tăng ngặt — không gập, không lặp hình), và tia sau xa vào
     * khung (bài dưới).
     */
    @Test fun `Thang rong - tam khong meo va don dieu tu tam ra mep duoi`() {
        val (_, gl) = seal(left = true, mode = CameraViewMode.WIDE)
        val p = gl.dewarp
        val asp = gl.aspect
        val h = 1e-3f
        fun srcOff(du: Float, dv: Float): Pair<Double, Double> {
            val (a, b) = CameraDewarp.mapDstToSrc(p.centerX + du, p.centerY + dv, p, asp)
            return ((a - p.centerX) * 2.0) to ((b - p.centerY) * 2.0 / asp)   // về p-space đẳng hướng
        }
        val radial = hypot(srcOff(h, 0f).first, srcOff(h, 0f).second) / (h * 2.0)
        val tangential = hypot(srcOff(0f, h * asp).first, srcOff(0f, h * asp).second) / (h * 2.0)
        assertEquals(radial, tangential, 1e-3 * radial, "tâm quang: phóng xuyên tâm = tiếp tuyến (bảo giác tại tâm)")
        var last = -1.0
        for (i in 0..200) {
            val du = -i / 200f * 0.7f                             // từ tâm ra phía đuôi (−x, gương trái)
            val r = hypot(srcOff(du, 0f).first, srcOff(du, 0f).second)
            assertTrue(r > last, "bán kính nguồn phải tăng ngặt (i=$i)")
            last = r
        }
    }

    /**
     * Hướng 90° phía đuôi (chân trời) rơi TRONG khung ra ở họ κ XUYÊN TÂM 1,5 — và NGOÀI khung ở Nắn thẳng.
     * ĐỔI GHIM (2.94 R1): *Thẳng rộng* sản phẩm nay là trụ (không vẽ được 90° — bài trên); bài này giữ tính chất của
     * nhánh xuyên tâm κ (vẫn trong [CameraDewarp.mapDstToSrc], cờ trụ tắt) với bộ số 2.93 (F 100 %, dịch −20 %).
     */
    @Test fun `tia 90 do phia duoi nam trong khung kappa xuyen tam, ngoai khung Nan thang`() {
        for (left in listOf(true, false)) {
            val dirX = if (left) -1f else 1f
            fun dstU(mode: String): Float? {
                val (_, gl) = seal(left = left, mode = mode)
                val p = if (mode == CameraViewMode.WIDE) {
                    gl.dewarp.copy(cylinder = false, focal = gl.dewarp.k, panX = if (left) -0.2f else 0.2f)
                } else {
                    gl.dewarp
                }
                val rSrc = (p.gain * PI / 2).toFloat()
                val local = CameraDewarp.forwardSrcToDst(p.centerX + dirX * rSrc / 2f, p.centerY, p, gl.aspect) ?: return null
                return local.first - p.panX                        // rot 0 · vừa khung (1,1) ⇒ bỏ dịch là ra toạ độ khung
            }
            // Phối cảnh thẳng: 90° là `tan` vô cực — hoặc không có nghiệm (null) hoặc rơi cách khung hàng triệu ô.
            val straight = dstU(CameraViewMode.STRAIGHT)
            assertTrue(straight == null || straight !in -1f..2f, "phối cảnh thẳng không vẽ được 90° (u=$straight)")
            val u = dstU(CameraViewMode.WIDE)!!
            assertTrue(u in 0f..1f, "90° phía đuôi tại u=$u")
            assertTrue(abs(u - 0.5f) > 0.3f, "90° nằm gần mép đuôi, không ở giữa (u=$u)")
        }
    }

    // ── (5) không NaN/∞ trên toàn miền núm (soát Opus 06/10 [P3]) ─────────────────────────────────────────────────

    /**
     * Một NaN/∞ lọt vào toạ độ lấy mẫu là khung ĐEN CÂM trên xe — không ném, không log. Quét các góc của miền mà
     * `prefs_set` ghi được (κ 100–800 %, F/K/S 25–400 %, dịch ±50 %, thu phóng 50–150 %) × tròn/chữ nhật × xoay, kể cả
     * điểm khung NGOÀI `[0,1]²` và tia vượt `κ·90°`: [CameraDewarp.sample] phải trả `null` (đen trung thực) hoặc số hữu
     * hạn, và nghịch đảo [forwardSrcToDst] cũng vậy (quá tầm với ⇒ `null`, không bao giờ `tan` nổ ra ∞).
     */
    @Test fun `khong NaN khong vo cuc tren toan mien nut, ke ca tia qua 90 do va ngoai khung`() {
        var n = 0
        for (mode in listOf(CameraViewMode.STRAIGHT, CameraViewMode.WIDE)) for (kappa in listOf(100, 150, 800))
            for (f in listOf(25, 100, 400)) for (k in listOf(25, 400)) for (s in listOf(25, 400)) for (pan in listOf(-50, 50))
                for (zoom in listOf(50, 150)) for (round in listOf(false, true)) for (rot in listOf(0, 90)) {
                    val shape = if (round) CameraSignalPolicy.SHAPE_ROUND else CameraSignalPolicy.SHAPE_RECT
                    val crops = seal(left = true, mode = mode, shape = shape).first
                    val knobs = sealStraight.copy(focalPct = f, kPct = k, scalePct = s, panXPct = pan,
                        wideKappaPct = kappa, wideFocalPct = f, widePanXPct = pan)
                    val gl = CameraViewPlan.gl(mode, zoom, crops, 1, streamW, streamH, rot, false, true, knobs, true)
                    val rect = CameraDewarp.srcRect(crops.content)
                    for (i in 0..6) for (j in 0..6) {
                        val u = -0.25f + i * 0.25f
                        val v = -0.25f + j * 0.25f
                        val got = CameraDewarp.sample(u, v, rot, gl.dewarp, gl.aspect, rect, gl.fit)
                        if (got != null) {
                            assertTrue(got.first.isFinite() && got.second.isFinite(), "$mode ($u,$v) κ$kappa F$f K$k S$s → $got")
                        }
                        n++
                    }
                    val p = gl.dewarp
                    for (t in 0..12) {
                        val rSrc = p.reachableSrcRadius * t / 10f
                        val back = CameraDewarp.forwardSrcToDst(p.centerX + rSrc / 2f, p.centerY, p, gl.aspect)
                        if (back != null) assertTrue(back.first.isFinite() && back.second.isFinite(), "nghịch đảo r=$rSrc → $back")
                    }
                }
        assertEquals(2 * 3 * 3 * 2 * 2 * 2 * 2 * 2 * 2 * 49, n)
    }
}
