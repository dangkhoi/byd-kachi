package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraSignalPolicy.CamView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.94 R1 · *Thẳng rộng* = phép chiếu TRỤ (camera gương) · sau/trước = *Nắn thẳng* — khoá bằng số ═══════════════
 *
 * Spec `docs/specs/kachi-294-plan.html` §3 R1 · §4.1. Owner 07/10: *"cam trái phải thì theo hướng trụ, cam sau trước thì
 * lấy option 3"*. Bài này khoá:
 *  1. Kotlin ↔ GLSL: khối `if (uCyl > 0.5)` là bản dịch từng dòng của [CameraDewarpCylinder.map]; hằng chốt cực khớp.
 *  2. Số vàng từ bản port numpy `render_validated.py` (khớp ảnh chụp xe NCC 0,991) — research README §3.
 *  3. TÍNH CHẤT trụ: đường thế giới song song trục (thân xe) ⇒ φ không đổi ⇒ đường THẲNG trên khung (κ xuyên tâm: cong).
 *  4. Cờ trụ tắt ⇒ *Nắn thẳng* và *Gương cầu* không đổi một bit; chốt cực ⇒ đen, không NaN, không gập.
 *  5. Luật vai camera ([CameraViewMode.forCamera]) + bộ mặc định của hai gương (đen ~0,87 %, đuôi ~86,3°).
 */
class CameraDewarpCylinderTest {

    private val asp = 1280f / 960f
    private val k = 0.452335f
    private val cyl = DewarpParams(amount = 1f, focal = k * 1.22f, k = k, scale = 1.3f, kappa = 1.5f, cylinder = true)
        .clamped()

    /** local nguồn → tia camera theo ống kính f-θ (`θ = r/(K·S)`); x = local a, y = local b, z = trục quang. */
    private fun lensRay(a: Float, b: Float, p: DewarpParams): DoubleArray {
        val qx = (a - p.centerX) * 2.0
        val qy = (b - p.centerY) * 2.0 / asp
        val r = hypot(qx, qy)
        if (r < 1e-12) return doubleArrayOf(0.0, 0.0, 1.0)
        val t = r / p.gain
        return doubleArrayOf(sin(t) * qx / r, sin(t) * qy / r, cos(t))
    }

    /** Tia camera → local nguồn (phép THUẬN của ống kính — mô hình thế giới của bài, độc lập với mã sản phẩm). */
    private fun lensSrc(x: Double, y: Double, z: Double, p: DewarpParams): Pair<Double, Double> {
        val rho = hypot(x, y)
        val r = p.gain * atan2(rho, z)
        return (p.centerX + x / rho * r * 0.5) to (p.centerY + y / rho * r * 0.5 * asp)
    }

    // ── (1) Kotlin ↔ GLSL ───────────────────────────────────────────────────────────────────────────────────────

    @Test fun `khoi tru GLSL ghim tung dong voi Kotlin`() {
        val fs = CameraDewarpShader.FRAGMENT
        assertTrue("uCyl" in CameraDewarpShader.UNIFORMS && "uCyl" in CameraGlUniforms.VALUE_UNIFORMS)
        for (line in listOf(
            "uniform float uCyl;",
            "if (uCyl > 0.5) {",
            "float lam = uKappa * atan(p.x, uKappa * uFocal);",
            "float phi = p.y / uFocal;",
            "if (abs(lam) >= ${CameraDewarpCylinder.POLE_GUARD_GLSL} || " +
                "abs(phi) >= ${CameraDewarpCylinder.PHI_GUARD_GLSL}) {",
            "corrected = vec2(-1.0, -1.0);",
            "vec3 ray = vec3(sin(lam), cos(lam) * sin(phi), cos(lam) * cos(phi));",
            "float rho = length(ray.xy);",
            "if (rho > 0.000001) {",
            "float rSrcCyl = gain * atan(rho, ray.z);",
            "cylProjected = vec2(uCenter.x + (ray.x / rho * rSrcCyl * 0.5),",
            "uCenter.y + (ray.y / rho * rSrcCyl * 0.5 * uAspect));",
            "corrected = mix(local, cylProjected, amount);",
        )) assertTrue(line in fs, "GLSL thiếu: $line")
        assertEquals(CameraDewarpCylinder.POLE_GUARD_RAD, CameraDewarpCylinder.POLE_GUARD_GLSL.toDouble(), 1e-7)
        assertEquals(PI / 2 - 1e-3, CameraDewarpCylinder.POLE_GUARD_RAD, 0.0)
        assertEquals(CameraDewarpCylinder.PHI_GUARD_RAD, CameraDewarpCylinder.PHI_GUARD_GLSL.toDouble(), 1e-6)
        assertTrue("cyl:" in CameraDewarp.FORMULA && CameraDewarp.FORMULA == CameraDewarpShader.FORMULA)
        // Nhánh xuyên tâm nằm trong `else` của khối trụ — đúng dòng 2.92, không đổi một ký tự.
        assertTrue(fs.indexOf("if (uCyl > 0.5) {") < fs.indexOf("float theta = uKappa * atan(pLen, uKappa * uFocal);"))
    }

    // ── (2) số vàng từ render_validated.py (map_cyl, F = 0,452335·1,22, κ 1,5, K·S = 0,452335·1,3, aspect 4:3) ──

    @Test fun `so vang tu ban port numpy`() {
        val golden = listOf(
            floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f),
            floatArrayOf(0.1f, 0.5f, 0.16114256f, 0.5f),
            floatArrayOf(-0.15f, 0.5f, 0.05730046f, 0.5f),
            floatArrayOf(0.9f, 0.3f, 0.84836053f, 0.39320356f),
            floatArrayOf(0.3f, 0.05f, 0.25185334f, 0.11153230f),
            floatArrayOf(0.7f, 0.95f, 0.74814666f, 0.88846770f),
            floatArrayOf(0.2f, 0.8f, 0.20003783f, 0.71229733f),
        )
        for (g in golden) {
            val (sa, sb) = CameraDewarp.mapDstToSrc(g[0], g[1], cyl, asp)
            assertEquals(g[2], sa, 2e-5f, "a tại (${g[0]},${g[1]})")
            assertEquals(g[3], sb, 2e-5f, "b tại (${g[0]},${g[1]})")
        }
    }

    // ── (3) tính chất trụ ───────────────────────────────────────────────────────────────────────────────────────

    /**
     * Mô hình thế giới: đường `X ↦ (X, Y0, Z0)` song song trục a (thân xe). Chiếu qua ống kính ⇒ điểm nguồn thật; nghịch
     * đảo giải tích của phép trụ (viết lại trong bài) ⇒ điểm khung. Mã sản phẩm phải đưa khung về ĐÚNG điểm nguồn ấy, và
     * mọi điểm khung của đường phải cùng một `v` (φ không đổi ⇒ một đường thẳng ngang của ô chưa xoay).
     */
    @Test fun `duong song song than xe ra mot duong thang phi khong doi`() {
        val p = cyl
        for ((y0, z0) in listOf(0.4 to 1.0, -0.6 to 1.0, 1.2 to 0.5, 0.3 to -0.2)) {
            val vs = ArrayList<Double>()
            for (i in -30..30) {
                val x = i / 10.0
                val n = kotlin.math.sqrt(x * x + y0 * y0 + z0 * z0)
                val (dx, dy, dz) = Triple(x / n, y0 / n, z0 / n)
                val lam = atan2(dx, hypot(dy, dz))
                if (abs(lam) >= CameraDewarpCylinder.POLE_GUARD_RAD - 0.05) continue
                val phi = atan2(dy, dz)
                val u = p.centerX + p.kappa * p.focal * tan(lam / p.kappa) / 2.0
                val v = p.centerY + phi * p.focal * asp / 2.0
                vs += v
                val (wa, wb) = lensSrc(dx, dy, dz, p)
                val (sa, sb) = CameraDewarp.mapDstToSrc(u.toFloat(), v.toFloat(), p, asp)
                assertEquals(wa, sa.toDouble(), 5e-5, "a (Y0=$y0,Z0=$z0,X=$x)")
                assertEquals(wb, sb.toDouble(), 5e-5, "b (Y0=$y0,Z0=$z0,X=$x)")
            }
            assertTrue(vs.size > 20)
            assertEquals(0.0, vs.max() - vs.min(), 1e-12, "φ không đổi ⇒ cùng một hàng")
        }
    }

    /** Chiều ngược: một HÀNG khung (v cố định) lấy mẫu đúng các tia của MỘT mặt phẳng chứa trục — κ xuyên tâm thì không. */
    @Test fun `mot hang khung la mot mat phang chua truc, kappa xuyen tam thi cong`() {
        val radial = cyl.copy(cylinder = false)
        for (v in listOf(0.15f, 0.3f, 0.7f, 0.9f)) {
            fun spread(p: DewarpParams): Double {
                val phis = (0..40).map { i ->
                    val (sa, sb) = CameraDewarp.mapDstToSrc(-0.1f + i * 0.025f, v, p, asp)
                    val r = lensRay(sa, sb, p)
                    atan2(r[1], r[2])
                }
                return phis.max() - phis.min()
            }
            assertTrue(spread(cyl) < 2e-4, "trụ: hàng v=$v phải là một mặt phẳng chứa trục (${spread(cyl)})")
            assertTrue(spread(radial) > 0.05, "κ xuyên tâm: hàng v=$v phải cong (${spread(radial)})")
        }
    }

    // ── (4) cờ tắt = từng bit cũ · chốt cực ─────────────────────────────────────────────────────────────────────

    @Test fun `Nan thang va Guong cau khong bat tru, co tat la tung bit cu`() {
        assertFalse(DewarpParams().cylinder)
        val w = CameraPanoCrop.PANO_W
        val h = CameraPanoCrop.PANO_H
        for (left in listOf(true, false)) for (mode in listOf(CameraViewMode.STRAIGHT, CameraViewMode.FISHEYE)) {
            val strip = CameraPanoCrop.defaultStrip(left)
            val view = if (left) CamView.MIRROR_LEFT else CamView.MIRROR_RIGHT
            val c = CameraViewPlan.crops(mode, view, left, strip, null, CameraSignalPolicy.SPAN_STRIP,
                CameraSignalPolicy.SHAPE_RECT, CameraSignalPolicy.CIRCLE_PCT_DEFAULT)
            val gl = CameraViewPlan.gl(mode, 100, c, strip, w, h, -90, false, left, CameraViewPlan.Knobs(), true)
            assertFalse(gl.dewarp.cylinder, "$mode không được bật trụ")
        }
        // Cùng bộ số, cờ tắt ⇒ đúng nhánh xuyên tâm (bằng bit với DewarpParams không có cờ).
        val off = cyl.copy(cylinder = false)
        val plain = DewarpParams(amount = 1f, focal = k * 1.22f, k = k, scale = 1.3f, kappa = 1.5f).clamped()
        for (i in 0..10) for (j in 0..10) {
            val a = CameraDewarp.mapDstToSrc(i / 10f, j / 10f, off, asp)
            val b = CameraDewarp.mapDstToSrc(i / 10f, j / 10f, plain, asp)
            assertEquals(b.first.toBits(), a.first.toBits()); assertEquals(b.second.toBits(), a.second.toBits())
        }
        // Nắn tắt (amount 0) ⇒ đồng nhất, kể cả khi cờ trụ bật — cùng cổng [DewarpParams.enabled].
        assertEquals(0.3f to 0.7f, CameraDewarp.mapDstToSrc(0.3f, 0.7f, cyl.copy(amount = 0f), asp))
    }

    @Test fun `chot cuc tra den, khong NaN, ke ca thu phong 50 phan tram`() {
        // λ = κ·atan(p.x, κF) chạm π/2 − 1e-3 khi p.x = κF·tan((π/2 − 1e-3)/κ).
        val sPole = cyl.kappa * cyl.focal * tan(CameraDewarpCylinder.POLE_GUARD_RAD / cyl.kappa)
        val uPole = (cyl.centerX - sPole / 2.0).toFloat()
        val rect = CameraDewarp.srcRect(floatArrayOf(0.25f, 0f, 0.5f, 1f))
        assertNull(CameraDewarp.sample(uPole - 1e-3f, 0.5f, 0, cyl, asp, rect), "qua cực ⇒ đen")
        assertNull(CameraDewarp.sample(uPole - 5f, 0.5f, 0, cyl, asp, rect), "xa cực ⇒ đen, không gập lại")
        assertNotNull(CameraDewarp.sample(uPole + 0.01f, 0.5f, 0, cyl, asp, rect), "ngay trước cực vẫn có hình")
        for (fit in listOf(floatArrayOf(2f, 2f), floatArrayOf(1f, 1f), floatArrayOf(0.66f, 0.66f))) {
            for (i in -10..30) for (j in -10..30) {
                val got = CameraDewarp.sample(i / 20f, j / 20f, -90, cyl.copy(panX = -0.5f), asp, rect, fit) ?: continue
                assertTrue(got.first.isFinite() && got.second.isFinite())
            }
        }
    }

    /**
     * Soát Opus 07/10 [P3]: `sin/cos φ` tuần hoàn 2π ⇒ không chốt `|φ| ≥ π` thì F nhỏ (`camera_wide_focal` 25 %, ghi được
     * qua `prefs_set`) cho hàng khung `φ = 2π − 0,3` lấy lại đúng tia `φ = −0,3` — ảnh LẶP trong khung. Bỏ chốt ⇒ đỏ.
     */
    @Test fun `chot quanh truc - F nho khong lap anh`() {
        val small = cyl.copy(focal = k * 0.25f)
        val pyWrap = ((2 * PI - 0.3) * small.focal).toFloat()
        val v = small.centerY + pyWrap * asp / 2f
        assertTrue(v in 0f..1f, "hàng ấy nằm TRONG khung (v=$v) — đúng ca owner nhìn thấy")
        val u = small.centerX + 0.05f / 2f
        val (sa, sb) = CameraDewarp.mapDstToSrc(u, v, small, asp)
        assertFalse(sa in 0f..1f && sb in 0f..1f, "φ = 2π − 0,3 phải đen, không lấy lại tia φ = −0,3 ($sa, $sb)")
        // Ngay dưới π vẫn có số hữu hạn (tia sau 90° — vòng ảnh quyết đen hay không, không phải chốt).
        val (na, nb) = CameraDewarp.mapDstToSrc(u, small.centerY + (0.99 * PI * small.focal).toFloat() * asp / 2f, small, asp)
        assertTrue(na.isFinite() && nb.isFinite())
    }

    // ── (5) luật vai camera + bộ mặc định hai gương ─────────────────────────────────────────────────────────────

    @Test fun `sau va truoc chon Thang rong thi ve Nan thang, guong giu Thang rong`() {
        val tv = CameraSignalPolicy.RENDER_TEXTURE
        for (which in CameraWhich.ALL) {
            val want = if (which.side) CameraViewMode.WIDE else CameraViewMode.STRAIGHT
            assertEquals(want, CameraViewMode.forCamera(CameraViewMode.WIDE, which), "$which")
            assertEquals(CameraViewMode.STRAIGHT, CameraViewMode.forCamera(CameraViewMode.STRAIGHT, which))
            assertEquals(CameraViewMode.FISHEYE, CameraViewMode.forCamera(CameraViewMode.FISHEYE, which))
        }
        // Thứ tự quy: vai camera TRƯỚC đường vẽ ⇒ camera sau trên TV vẫn là Nắn thẳng (không rơi sang Gương cầu).
        assertEquals(CameraViewMode.STRAIGHT,
            CameraViewMode.effective(CameraViewMode.forCamera(CameraViewMode.WIDE, CameraWhich.REAR), tv))
        assertEquals(CameraViewMode.FISHEYE,
            CameraViewMode.effective(CameraViewMode.forCamera(CameraViewMode.WIDE, CameraWhich.LEFT), tv))
    }

    /** Bộ mặc định trên hai gương, xoay thật (trái −90, phải +90): đen ~0,87 % · đuôi ~86,3° (research, bản port). */
    @Test fun `bo mac dinh hai guong - den duoi 1 5 phan tram, duoi 84 den 90 do`() {
        val w = CameraPanoCrop.PANO_W
        val h = CameraPanoCrop.PANO_H
        for (left in listOf(true, false)) {
            val strip = CameraPanoCrop.defaultStrip(left)
            val view = if (left) CamView.MIRROR_LEFT else CamView.MIRROR_RIGHT
            val c = CameraViewPlan.crops(CameraViewMode.WIDE, view, left, strip, null, CameraSignalPolicy.SPAN_STRIP,
                CameraSignalPolicy.SHAPE_RECT, CameraSignalPolicy.CIRCLE_PCT_DEFAULT)
            val rot = if (left) -90 else 90
            val gl = CameraViewPlan.gl(CameraViewMode.WIDE, 100, c, strip, w, h, rot, false, left,
                CameraViewPlan.Knobs(scalePct = 130), true)
            val p = gl.dewarp
            assertTrue(p.cylinder)
            assertEquals(1.5f, p.kappa)
            assertEquals(k * 1.22f, p.focal, 1e-6f)
            assertEquals(if (left) -0.15f else 0.15f, p.panX, 1e-6f)
            val rect = CameraDewarp.srcRect(c.content)
            var black = 0
            for (i in 0 until 371) for (j in 0 until 495) {
                if (CameraDewarp.sample((i + 0.5f) / 371f, (j + 0.5f) / 495f, rot, p, gl.aspect, rect, gl.fit) == null) black++
            }
            val frac = black / (371.0 * 495.0)
            if (left) assertEquals(0.00867, frac, 0.001, "đen gương trái (research 0,867 %)")
            assertTrue(frac < 0.015, "đen ${frac * 100} %")
            val tailA = (if (left) 0f else 1f) + p.panX
            val (sa, sb) = CameraDewarp.mapDstToSrc(tailA, 0.5f, p, gl.aspect)
            val tailDeg = Math.toDegrees(hypot((sa - p.centerX) * 2.0, (sb - p.centerY) * 2.0 / gl.aspect) / p.gain)
            assertEquals(86.27, tailDeg, 0.1, "mép đuôi")
        }
    }
}
