package com.byd.clusternav.launcher.camera

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ Phép nắn fisheye — chứng minh BẰNG SỐ, off-car, không GPU ═════════════════════════════════════════════════
 *
 * Công thức tồn tại **hai bản** (Kotlin [CameraDewarp] + GLSL [CameraDewarpShader]) và bộ số mặc định là **[ĐOÁN]**
 * suy từ hình học (chưa có khung nào chụp từ xe — RE §7 Q1/Q6). Bài này khoá ba thứ:
 *  1. **Không trôi khỏi Electro**: `aspect == 1` ⇒ [CameraDewarp.mapDstToSrc] trùng khít bản viết lại **nguyên văn**
 *     ba dòng của shader E (RE §3.2) — nếu ai "cải tiến" công thức thì đỏ ngay.
 *  2. **Đúng quang học**: đường thẳng của thế giới, đi qua mô hình chụp đẳng khoảng rồi qua phép nắn, phải **thẳng
 *     lại**; đồng-θ phải **tròn theo pixel** ở cả 4:3 và 3:4 (chỗ Electro thiếu).
 *  3. **Hai bản không lệch nhau**: tập uniform của GLSL đọc từ chính văn bản shader, so với danh sách tay; các token
 *     `atan(`/`mix(`/`clamp(` và viền đen bị ghim chuỗi.
 */
class CameraDewarpTest {

    private companion object {
        /** Dải pano thật: `1280×960` (RE §2.1 — `5120×960 = 4 × 1280×960`). */
        const val ASPECT_43 = 1280f / 960f

        /** Cùng dải sau khi xoay ±90 ⇒ ô `960×1280`. */
        const val ASPECT_34 = 960f / 1280f

        val P = DewarpParams()

    }

    /** Nguyên văn ba dòng của Electro (RE §3.2) — KHÔNG aspect, tâm ghim `(0.5, 0.5)`. Dùng làm mốc so. */
    private fun electro(u: Float, v: Float, p: DewarpParams): Pair<Float, Float> {
        val px = (u - 0.5f) * 2f
        val py = (v - 0.5f) * 2f
        val pLen = hypot(px, py)
        val dirX = if (pLen > 0.0001f) px / pLen else 0f
        val dirY = if (pLen > 0.0001f) py / pLen else 0f
        val theta = atan2(pLen, p.focal)
        val r = theta * p.k
        val projX = 0.5f + dirX * r * 0.5f * p.scale
        val projY = 0.5f + dirY * r * 0.5f * p.scale
        val a = p.amount.coerceIn(0f, 1f)
        return (u + (projX - u) * a) to (v + (projY - v) * a)
    }

    // ── (a) amount = 0 ⇒ ĐÚNG đường 2.73, không lệch một bit ────────────────────────────────────────────────────

    @Test
    fun `amount 0 thi khong doi mot pixel nao`() {
        val off = P.copy(amount = 0f)
        for (i in 0..10) for (j in 0..10) {
            val u = i / 10f
            val v = j / 10f
            val (su, sv) = CameraDewarp.mapDstToSrc(u, v, off, ASPECT_43)
            assertEquals(u.toDouble(), su.toDouble(), 1e-6, "u tai ($u,$v)")
            assertEquals(v.toDouble(), sv.toDouble(), 1e-6, "v tai ($u,$v)")
        }
        assertFalse(off.enabled, "amount 0 phai tat han phep nan")
    }

    // ── (b) tâm về tâm, kể cả tâm NGOÀI ô (ca crop hẹp của Kachi) ───────────────────────────────────────────────

    @Test
    fun `tam quang anh xa ve chinh no`() {
        for (p in listOf(P, P.copy(centerX = 1.25f, centerY = 0.5f), P.copy(centerX = -0.3f, centerY = 0.2f))) {
            val (su, sv) = CameraDewarp.mapDstToSrc(p.centerX, p.centerY, p, ASPECT_43)
            assertEquals(p.centerX.toDouble(), su.toDouble(), 1e-6)
            assertEquals(p.centerY.toDouble(), sv.toDouble(), 1e-6)
        }
    }

    @Test
    fun `tam ngoai o la ca THAT cua crop guong`() {
        // Dải 1 tâm x = 0.375 (kinex `Y0/C0094o.java:76`), crop gương trái x[0.25, 0.35] ⇒ centerX = 1.25.
        val (cx, cy) = CameraDewarp.centerInCrop(0.375f, 0.5f, floatArrayOf(0.25f, 0f, 0.35f, 1f))
        assertEquals(1.25, cx.toDouble(), 1e-4)
        assertEquals(0.5, cy.toDouble(), 1e-4)
        assertEquals(1.25f, DewarpParams(centerX = cx).clamped().centerX, 1e-4f, "kep KHONG duoc cat tam ra ngoai o")
    }

    // ── (c) bán kính đơn điệu ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `ban kinh nguon tang don dieu theo ban kinh dich`() {
        for (p in listOf(P, P.copy(amount = 0.4f), P.copy(focal = 2f, k = 0.9f))) {
            var prev = -1f
            var r = 0f
            while (r <= 5f) {
                val cur = CameraDewarp.effectiveSrcRadius(r, p)
                assertTrue(cur > prev, "khong don dieu tai r=$r ($cur <= $prev) voi $p")
                prev = cur
                r += 0.005f
            }
        }
    }

    @Test
    fun `amount 1 chi voi toi tia 90 do`() {
        assertEquals((P.gain * Math.PI / 2.0), P.reachableSrcRadius.toDouble(), 1e-5)
        // Vành ngoài của vòng ảnh (0,75 nửa-bề-ngang = 480 px) nằm SAU 90° ⇒ không điểm đích nào chiếu tới.
        assertTrue(P.reachableSrcRadius < 0.75f, "K*SCALE*pi/2 = ${P.reachableSrcRadius} phai nho hon 0,75")
        assertNull(CameraDewarp.forwardSrcToDst(0.5f + 0.75f / 2f, 0.5f, P, 1f), "ngoai tam voi ⇒ null")
        assertNotNull(CameraDewarp.forwardSrcToDst(0.5f + 0.6f / 2f, 0.5f, P, 1f))
    }

    // ── (d) khứ hồi ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `khu hoi dich to nguon roi ve dich`() {
        for (p in listOf(P, P.copy(amount = 0.5f), P.copy(centerX = 1.25f))) {
            for (aspect in listOf(1f, ASPECT_43, ASPECT_34)) {
                var i = 1
                while (i <= 19) {
                    var j = 1
                    while (j <= 19) {
                        val u = i / 20f
                        val v = j / 20f
                        val (su, sv) = CameraDewarp.mapDstToSrc(u, v, p, aspect)
                        val back = CameraDewarp.forwardSrcToDst(su, sv, p, aspect)
                        assertNotNull(back, "khong nghich dao duoc ($u,$v) aspect=$aspect $p")
                        assertEquals(u.toDouble(), back!!.first.toDouble(), 1e-3, "u ($u,$v) aspect=$aspect")
                        assertEquals(v.toDouble(), back.second.toDouble(), 1e-3, "v ($u,$v) aspect=$aspect")
                        j++
                    }
                    i++
                }
            }
        }
    }

    // ── (e) đường thẳng vẫn thẳng ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `duong thang van thang`() {
        val spec = CameraDewarpTestPattern.Spec()
        val p = CameraDewarpTestPattern.lensParams(spec)
        val aspect = CameraDewarpTestPattern.stripAspect(spec)
        for (sceneY in listOf(0f, 0.35f, -0.8f)) {
            val pts = (-10..10).map { n ->
                val sceneX = n / 10f
                val (su, sv) = CameraDewarp.idealEquidistantSource(sceneX, sceneY, p, aspect)
                CameraDewarp.forwardSrcToDst(su, sv, p, aspect)
                    ?: error("diem thuoc tam nhin ma khong nghich dao duoc: ($sceneX,$sceneY)")
            }
            assertTrue(maxResidual(pts) < 1e-3, "duong y=$sceneY bi cong: lech ${maxResidual(pts)}")
        }
    }

    /** Lệch vuông góc lớn nhất so với đường qua điểm đầu và điểm cuối. */
    private fun maxResidual(pts: List<Pair<Float, Float>>): Double {
        val (x0, y0) = pts.first()
        val (x1, y1) = pts.last()
        val dx = (x1 - x0).toDouble()
        val dy = (y1 - y0).toDouble()
        val len = hypot(dx, dy)
        return pts.maxOf { (x, y) -> abs(dx * (y - y0) - dy * (x - x0)) / len }
    }

    @Test
    fun `mo hinh ong kinh dang khoang thuan nghich`() {
        val spec = CameraDewarpTestPattern.Spec()
        val p = CameraDewarpTestPattern.lensParams(spec)
        val aspect = CameraDewarpTestPattern.stripAspect(spec)
        val halfFov = spec.lensFovDeg / 2f
        for (deg in listOf(0f, 15f, 45f, 70f, halfFov)) {
            val th = Math.toRadians(deg.toDouble()).toFloat()
            val r = CameraDewarp.equidistantSrcRadius(th, p)
            assertEquals(th.toDouble(), CameraDewarp.equidistantTheta(r, p).toDouble(), 1e-5, "theta $deg")
            // r = 1 nửa-bề-ngang ⇔ 640 px; tia 95° phải rơi ĐÚNG vành vòng ảnh (480 px).
            val px = r * spec.stripWidth / 2f
            val want = (deg / halfFov * spec.circleRadiusPx).toDouble()
            assertEquals(want, px.toDouble(), 0.02, "ban kinh px tai $deg do")
        }
        // idealEquidistantSource phải dùng ĐÚNG cùng hệ số: tan 45° = 1 ⇒ rơi đúng bán kính của tia 45°.
        val (su, sv) = CameraDewarp.idealEquidistantSource(1f, 0f, p, aspect)
        assertEquals(0.5, sv.toDouble(), 1e-6)
        assertEquals(45.0 / halfFov * spec.circleRadiusPx, (su - 0.5) * spec.stripWidth, 0.05)
    }

    // ── (f) tỉ lệ khung: đồng-θ TRÒN theo pixel, ở cả 4:3 và 3:4 ────────────────────────────────────────────────

    @Test
    fun `dong theta tron theo pixel o ca 1280x960 va 960x1280`() {
        for ((w, h) in listOf(1280f to 960f, 960f to 1280f)) {
            val aspect = DewarpParams.aspectOf(w, h)
            val p = DewarpParams.derive(rectWidthPx = w, rectHeightPx = h, imageCircleDiameterPx = minOf(w, h))
            for (tPx in listOf(40f, 120f, 240f)) {
                val alongX = CameraDewarp.mapDstToSrc(0.5f + tPx / w, 0.5f, p, aspect)
                val alongY = CameraDewarp.mapDstToSrc(0.5f, 0.5f + tPx / h, p, aspect)
                val srcPxX = (alongX.first - 0.5) * w
                val srcPxY = (alongY.second - 0.5) * h
                assertEquals(srcPxX, srcPxY, 0.05, "${w.toInt()}x${h.toInt()} tai $tPx px: x=$srcPxX y=$srcPxY")
            }
        }
    }

    @Test
    fun `aspect 1 trung khit nguyen van Electro`() {
        for (p in listOf(P, P.copy(amount = 0.3f), P.copy(focal = 0.8f, k = 0.6f, scale = 1.3f))) {
            for (i in 0..10) for (j in 0..10) {
                val u = i / 10f
                val v = j / 10f
                val mine = CameraDewarp.mapDstToSrc(u, v, p, 1f)
                val theirs = electro(u, v, p)
                assertEquals(theirs.first.toDouble(), mine.first.toDouble(), 1e-6, "u ($u,$v) $p")
                assertEquals(theirs.second.toDouble(), mine.second.toDouble(), 1e-6, "v ($u,$v) $p")
            }
        }
    }

    // ── bộ mặc định: suy từ hình học, không phải hằng bê từ Electro ─────────────────────────────────────────────

    @Test
    fun `mac dinh khop dung phep suy hinh hoc`() {
        val derived = DewarpParams.derive(rectWidthPx = 1280f, rectHeightPx = 960f, imageCircleDiameterPx = 960f)
        assertEquals(DewarpParams.DEFAULT_K.toDouble(), derived.k.toDouble(), 1e-5, "K mac dinh")
        assertEquals(DewarpParams.DEFAULT_FOCAL.toDouble(), derived.focal.toDouble(), 1e-5, "F mac dinh")
        assertEquals(1.0, derived.scale.toDouble(), 1e-6)
        // Độ phóng tại tâm = 1: gần tâm ảnh nắn và ảnh thô trùng nhau.
        val t = 0.002f
        val (su, _) = CameraDewarp.mapDstToSrc(0.5f + t, 0.5f, derived, 1f)
        assertEquals((0.5f + t).toDouble(), su.toDouble(), 2e-6, "do phong tai tam phai = 1")
        assertEquals(65.66, derived.outputHalfFovDeg.toDouble(), 0.05, "nua-FOV ngang cua khung ra")
    }

    @Test
    fun `K va F ti le nghich voi be ngang o`() {
        val full = DewarpParams.derive(rectWidthPx = 1280f, rectHeightPx = 960f, imageCircleDiameterPx = 960f)
        val half = DewarpParams.derive(rectWidthPx = 640f, rectHeightPx = 960f, imageCircleDiameterPx = 960f)
        assertEquals(2.0 * full.k, half.k.toDouble(), 1e-5)
        assertEquals(2.0 * full.focal, half.focal.toDouble(), 1e-5)
    }

    // ── (g) kẹp tham số ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `kep tham so ve mien dung duoc`() {
        val bad = DewarpParams(
            amount = 5f,
            focal = -3f,
            k = Float.NaN,
            scale = Float.POSITIVE_INFINITY,
            centerX = 99f,
            centerY = -99f,
        ).clamped()
        assertEquals(1f, bad.amount)
        assertEquals(DewarpParams.DEFAULT_K, bad.k, "NaN phai ve mac dinh, khong ve 0")
        assertTrue(bad.focal >= DewarpParams.MIN_FOCAL, "F am phai bi kep duong (RE §3.3 rang buoc 4)")
        assertEquals(DewarpParams.DEFAULT_SCALE, bad.scale, "vo cuc ve mac dinh, khong ve tran")
        assertEquals(DewarpParams.MAX_CENTER, bad.centerX)
        assertEquals(DewarpParams.MIN_CENTER, bad.centerY)
        assertEquals(DewarpParams.MAX_GAIN, DewarpParams(scale = 1e9f).clamped().scale, "so huu han thi kep ve tran")
        assertTrue(bad.enabled)
        assertFalse(DewarpParams(focal = 0f).enabled, "F = 0 phai tat phep nan")
        assertFalse(DewarpParams(k = 0f).enabled, "K = 0 phai tat phep nan")
        assertFalse(DewarpParams(amount = DewarpParams.AMOUNT_EPS).enabled, "cong 1e-4 giong Electro")
    }

    // ── xoay: cùng quy ước với CameraOverlayTransform ───────────────────────────────────────────────────────────

    @Test
    fun `xoay boi cua 90 khop CameraOverlayTransform`() {
        // 90° ⇒ (a,b) = (dy, 1−dx); 180° ⇒ (1−dx, 1−dy); 270° ⇒ (1−dy, dx).
        assertLocal(0.3f, 0.8f, CameraDewarp.rotateDstToLocal(0.2f, 0.3f, 90), "90")
        assertLocal(0.8f, 0.7f, CameraDewarp.rotateDstToLocal(0.2f, 0.3f, 180), "180")
        assertLocal(0.7f, 0.2f, CameraDewarp.rotateDstToLocal(0.2f, 0.3f, 270), "270")
        assertLocal(0.7f, 0.2f, CameraDewarp.rotateDstToLocal(0.2f, 0.3f, -90), "goc am phai chuan hoa")
        assertLocal(0.2f, 0.3f, CameraDewarp.rotateDstToLocal(0.2f, 0.3f, 0), "0")
        // Bốn góc ô đi đúng bốn góc ô ⇒ không lộ mép đen sau khi xoay.
        for (deg in listOf(0, 90, 180, 270)) {
            val corners = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)
                .map { (u, v) -> CameraDewarp.rotateDstToLocal(u, v, deg) }
            assertEquals(4, corners.toSet().size, "bon goc phai di bon cho khac nhau (deg=$deg)")
            assertTrue(corners.all { onEdge(it.first) && onEdge(it.second) }, "goc o phai ve goc o (deg=$deg)")
        }
    }

    private fun onEdge(t: Float): Boolean = abs(t) < 1e-6f || abs(t - 1f) < 1e-6f

    private fun assertLocal(a: Float, b: Float, actual: Pair<Float, Float>, what: String) {
        assertEquals(a.toDouble(), actual.first.toDouble(), 1e-5, "a cua $what")
        assertEquals(b.toDouble(), actual.second.toDouble(), 1e-5, "b cua $what")
    }

    @Test
    fun `xoay goc le thi xien o khong vuong`() {
        // Ghi lại đúng sự thật, không giả vờ hàm tổng quát: 45° trong toạ độ chuẩn hoá KHÔNG phải xoay theo pixel.
        val (a, b) = CameraDewarp.rotateDstToLocal(1f, 1f, 45)
        assertTrue(a > 1f, "goc le day diem ra NGOAI o (a=$a) ⇒ shader tra den, dung nhu thiet ke")
        assertEquals(0.5, b.toDouble(), 1e-5)
    }

    // ── uSrcRect + soi gương bằng bề rộng ÂM ───────────────────────────────────────────────────────────────────

    @Test
    fun `soi guong di bang be rong am cua uSrcRect`() {
        val crop = floatArrayOf(0.25f, 0f, 0.35f, 1f)
        val plain = CameraDewarp.srcRect(crop)
        assertEquals(0.25, plain[0].toDouble(), 1e-6)
        assertEquals(0.0, plain[1].toDouble(), 1e-6)
        assertEquals(0.10, plain[2].toDouble(), 1e-6)
        assertEquals(1.0, plain[3].toDouble(), 1e-6)
        val mirrored = CameraDewarp.srcRect(crop, flipH = true)
        assertEquals(0.35f, mirrored[0])
        assertTrue(mirrored[2] < 0f, "soi guong = be rong AM")
        // Hai đầu dải đổi chỗ, không mất một pixel nào.
        assertEquals(0.25, CameraDewarp.applySrcRect(0f, 0f, plain).first.toDouble(), 1e-6)
        assertEquals(0.35, CameraDewarp.applySrcRect(1f, 0f, plain).first.toDouble(), 1e-6)
        assertEquals(0.35, CameraDewarp.applySrcRect(0f, 0f, mirrored).first.toDouble(), 1e-6)
        assertEquals(0.25, CameraDewarp.applySrcRect(1f, 0f, mirrored).first.toDouble(), 1e-6)
        assertTrue(CameraDewarp.srcRect(null).contentEquals(floatArrayOf(0f, 0f, 1f, 1f)))
    }

    @Test
    fun `sample tra null khi nan day diem ra ngoai o`() {
        val rect = CameraDewarp.srcRect(floatArrayOf(0.25f, 0f, 0.35f, 1f))
        // SCALE lớn = với sâu hơn vào fisheye; quá 1,0 bán kính nguồn thì góc ô rơi RA NGOÀI ô ⇒ đen đặc.
        val zoomedOut = P.copy(scale = 3f)
        assertTrue(zoomedOut.gain * Math.PI / 2.0 > 1.0, "phai chon SCALE du lon de day duoc diem ra ngoai")
        assertNull(CameraDewarp.sample(1f, 1f, 0, zoomedOut, ASPECT_43, rect), "goc o ra ngoai ⇒ den")
        assertNotNull(CameraDewarp.sample(0.5f, 0.5f, 0, zoomedOut, ASPECT_43, rect), "tam o luon co du lieu")
        // Ngược lại: F rất nhỏ = FOV ra rất rộng ⇒ ảnh bị KÉO VÀO tâm, không bao giờ ra ngoài ô.
        assertNotNull(CameraDewarp.sample(1f, 1f, 0, P.copy(focal = 0.05f), ASPECT_43, rect))
    }

    // ── (h) ghim văn bản shader ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `nguon shader ghim dung cac token cua cong thuc`() {
        val (vs, fs) = CameraDewarpShader.program()
        assertEquals(CameraDewarpShader.VERTEX, vs)
        assertEquals(CameraDewarpShader.FRAGMENT, fs)
        assertTrue(fs.contains("#extension GL_OES_EGL_image_external : require"))
        assertTrue(fs.contains("uniform samplerExternalOES uTex;"))
        // 2.92: θ = κ·atan(r, κ·F) — κ = 1 là đúng `atan(pLen, uFocal)` của Electro (nhân 1.0 chính xác IEEE).
        assertTrue(fs.contains("float theta = uKappa * atan(pLen, uKappa * uFocal);"), "atan 2 doi so = atan2, ho kappa")
        // 2.92: bước vừa khung TÁCH khỏi phép dịch — nhánh cũ (uFit.x <= 0) đi đúng `local = local + uPan;` của 2.75.
        assertTrue(fs.contains("if (uFit.x > 0.0) {"), "vua khung phai co cong uFit.x > 0")
        assertTrue(fs.contains("local = vec2(0.5, 0.5) + ((local - vec2(0.5, 0.5)) * uFit);"))
        assertTrue(fs.contains("local = local + uPan;"), "dich cua so cua 2.75 giu nguyen van")
        assertTrue(fs.contains("mix(local, projected, amount)"), "phep TRON, RE §3.3")
        assertTrue(fs.contains("clamp(uAmount, 0.0, 1.0)"))
        assertTrue(fs.contains("gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);"), "ngoai o ⇒ den dac, khong clamp")
        assertTrue(fs.contains("uSrcRect.xy + (corrected * uSrcRect.zw)"))
        assertTrue(fs.contains("(uTexMatrix * vec4(raw, 0.0, 1.0)).xy"))
        assertTrue(vs.contains("vTexCoord = aTexCoord;"))
        // Loại trừ vét cạn như RE §3.3 đã làm với Electro: đúng MỘT atan, không pow/asin/sqrt, `tan(` chỉ trong atan.
        assertEquals(1, Regex(Regex.escape("atan(")).findAll(fs).count())
        assertEquals(1, Regex(Regex.escape("tan(")).findAll(fs).count())
        for (banned in listOf("pow(", "asin(", "sqrt(")) {
            assertFalse(fs.contains(banned), "$banned khong thuoc mo hinh dang khoang")
        }
        // Không còn chỗ chèn kiểu Electro (`<PARAM_F>`…): mọi số đi bằng uniform (RE §3.5, §6.3 canh bao cuoi).
        assertFalse(Regex("<[A-Z_]+>").containsMatchIn(fs), "khong duoc con cho chen kieu <PARAM_F> trong GLSL")
        assertEquals(CameraDewarp.FORMULA, CameraDewarpShader.FORMULA)
    }

    @Test
    fun `tap uniform va attribute cua shader khop danh sach tay`() {
        assertEquals(CameraDewarpShader.UNIFORMS, CameraDewarpShader.declaredUniforms())
        assertEquals(CameraDewarpShader.ATTRIBUTES, CameraDewarpShader.declaredAttributes())
        assertEquals(CameraDewarpShader.UNIFORMS.size, CameraDewarpShader.UNIFORMS.toSet().size)
        for (name in CameraDewarpShader.UNIFORMS) {
            assertTrue(CameraDewarpShader.FRAGMENT.contains(name), "$name khai ma khong dung")
        }
    }

    // ── (i) ảnh fisheye tổng hợp ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `anh pano 4 dai co dung co mau nhan va goc toi`() {
        val spec = CameraDewarpTestPattern.Spec(width = 5120, height = 960, strips = 4)
        val img = CameraDewarpTestPattern.pano(spec)
        assertEquals(5120 * 960, img.size)
        // Góc khung: r = hypot(640, 480) = 800 > 480 ⇒ ngoài vòng ảnh.
        assertEquals(CameraDewarpTestPattern.CORNER, img[0], "goc tren-trai phai la vung toi ngoai vong anh")
        assertEquals(CameraDewarpTestPattern.CORNER, img[5119], "goc tren-phai cung vay")
        for (s in 0 until 4) {
            assertEquals(
                CameraDewarpTestPattern.stripTint(s),
                dominantNear(img, spec, s),
                "nen dai $s phai la mau rieng cua no",
            )
            assertEquals(
                CameraDewarpTestPattern.labelPixelCount(s, spec),
                countInStrip(img, spec, s, CameraDewarpTestPattern.LABEL),
                "so pixel chu so nhan dai $s",
            )
        }
        // Nhãn "1" ít pixel hơn nhãn "0" ⇒ bốn dải phân biệt được cả bằng máy lẫn bằng mắt.
        assertTrue(
            CameraDewarpTestPattern.labelPixelCount(1, spec) < CameraDewarpTestPattern.labelPixelCount(0, spec),
        )
    }

    @Test
    fun `vong danh dau tron theo pixel va luoi dem duoc`() {
        val spec = CameraDewarpTestPattern.Spec()
        val img = CameraDewarpTestPattern.strip(spec)
        val cx = spec.stripWidth / 2
        val cy = spec.height / 2
        val ringX = (cx until spec.width).first { img[cy * spec.width + it] == CameraDewarpTestPattern.RING } - cx
        val ringY = (cy until spec.height).first { img[it * spec.width + cx] == CameraDewarpTestPattern.RING } - cy
        assertEquals(ringX.toDouble(), ringY.toDouble(), 1.5, "dong-theta phai TRON theo pixel")
        val expectedRing = spec.circleRadiusPx *
            (Math.toRadians(spec.ringThetaDeg.toDouble()) / Math.toRadians(spec.lensFovDeg.toDouble() / 2.0))
        assertEquals(expectedRing, ringX.toDouble(), 3.0)

        // Số vạch lưới DỌC trong nửa trong của hàng giữa — so với chính mô hình, không hardcode.
        val halfR = spec.circleRadiusPx / 2f
        val maxSceneX = kotlin.math.tan(CameraDewarpTestPattern.thetaAtRadius(halfR, spec).toDouble()).toFloat()
        val expected = CameraDewarpTestPattern.gridLinesWithin(maxSceneX, spec).size
        assertEquals(4, expected, "mo hinh phai cho 4 vach trong |x| < tan(47,5°) voi buoc 0,5 lech pha 0,5")
        var runs = 0
        var prev = false
        for (dx in -halfR.toInt() + 1 until halfR.toInt()) {
            val cur = img[cy * spec.width + (cx + dx)] == CameraDewarpTestPattern.GRID
            if (cur && !prev) runs++
            prev = cur
        }
        assertEquals(expected, runs, "so vach luoi doc dem tren anh phai khop mo hinh")
    }

    @Test
    fun `chan troi lech tam thi CONG, qua tam thi thang`() {
        val spec = CameraDewarpTestPattern.Spec()
        val img = CameraDewarpTestPattern.strip(spec)
        val cx = spec.stripWidth / 2
        val atCentre = horizonRow(img, spec, cx)
        val atSide = horizonRow(img, spec, cx + 250)
        assertTrue(atCentre > 0 && atSide > 0, "phai tim thay chan troi o ca hai cot")
        assertTrue(abs(atCentre - atSide) > 5, "chan troi lech tam phai CONG (lech ${abs(atCentre - atSide)} px)")

        val straight = CameraDewarpTestPattern.Spec(horizonY = 0f)
        val img2 = CameraDewarpTestPattern.strip(straight)
        assertEquals(
            horizonRow(img2, straight, cx).toDouble(),
            horizonRow(img2, straight, cx + 250).toDouble(),
            1.0,
            "duong qua truc quang thi van THANG",
        )
    }

    @Test
    fun `nan anh tong hop bang lensParams thi luoi thang lai`() {
        val spec = CameraDewarpTestPattern.Spec()
        val p = CameraDewarpTestPattern.lensParams(spec)
        val aspect = CameraDewarpTestPattern.stripAspect(spec)
        // Vạch lưới dọc x = 0,25: lấy các điểm của nó trên mặt phẳng, chiếu vào ảnh, rồi nắn ⇒ phải cùng MỘT cột.
        val col = (-8..8).map { n ->
            val (su, sv) = CameraDewarp.idealEquidistantSource(0.25f, n / 10f, p, aspect)
            CameraDewarp.forwardSrcToDst(su, sv, p, aspect) ?: error("khong nghich dao duoc n=$n")
        }
        val spread = col.maxOf { it.first } - col.minOf { it.first }
        assertTrue(spread < 1e-3, "vach doc sau khi nan phai thang dung: lech $spread")
    }

    /** Màu xuất hiện nhiều nhất trong vùng `r < 0.4·R` quanh tâm dải [strip] (nhãn/vành đều ngoài vùng này). */
    private fun dominantNear(img: IntArray, spec: CameraDewarpTestPattern.Spec, strip: Int): Int {
        val stripW = spec.stripWidth
        val cx = strip * stripW + stripW / 2
        val cy = spec.height / 2
        val rad = (spec.circleRadiusPx * 0.4f).toInt()
        val counts = HashMap<Int, Int>()
        for (y in (cy - rad)..(cy + rad)) for (x in (cx - rad)..(cx + rad)) {
            if (hypot((x - cx).toDouble(), (y - cy).toDouble()) > rad) continue
            val c = img[y * spec.width + x]
            counts[c] = (counts[c] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }!!.key
    }

    private fun countInStrip(img: IntArray, spec: CameraDewarpTestPattern.Spec, strip: Int, colour: Int): Int {
        val stripW = spec.stripWidth
        var n = 0
        for (y in 0 until spec.height) for (x in (strip * stripW) until (strip * stripW + stripW)) {
            if (img[y * spec.width + x] == colour) n++
        }
        return n
    }

    /** Hàng của pixel chân trời trên cột [x]; `-1` nếu không có. */
    private fun horizonRow(img: IntArray, spec: CameraDewarpTestPattern.Spec, x: Int): Int {
        for (y in 0 until spec.height) {
            if (img[y * spec.width + x] == CameraDewarpTestPattern.HORIZON) return y
        }
        return -1
    }
}
