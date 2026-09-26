package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraSignalPolicy.CamView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * ═══ R8-B — bộ uniform của đường kết xuất GL, kiểm BẰNG SỐ off-car ═════════════════════════════════════════════
 *
 * Toán của phép nắn đã có bài riêng (`CameraDewarpTest`, 23 ca). Bài này kiểm **phép HỢP**: từ (crop, cỡ luồng, góc
 * xoay, sáu núm) ra mười uniform — chỗ mà `:app` không test được off-car và là nơi bảy con số có thể lệch im lặng.
 *
 * Ba nhóm ca có giá trị nhất, tất cả đều bằng số:
 *  1. **Cùng hình học với đường đang chạy** — đường GL và đường `TextureView` phải đặt **cùng một pixel nguồn** vào
 *     **cùng một điểm khung**, trên 4 góc xoay × 2 bề rộng × 2 hình khung. Đây là bài đắt nhất và cũng là bài duy
 *     nhất chứng minh đổi chip *Kết xuất* không đổi thứ owner đang thấy (khi độ nắn = 0).
 *  2. **Đổi trục `t`** — `t = 1 − y`, KHÔNG phải `flipV`; hai công thức chỉ trùng nhau ở crop đối xứng.
 *  3. **Bộ suy ra khớp tài liệu** — `K` của vệt hẹp gấp đúng 2,5 lần `K` của trọn dải (`camera-dewarp-math.md` §3.1).
 */
class CameraGlUniformsTest {

    private val streamW = 5120
    private val streamH = 960

    private fun cropOf(span: String, shape: String, strip: Int = 1, left: Boolean = true): FloatArray? =
        CameraPanoCrop.cropFor(
            view = CamView.MIRROR_LEFT, left = left, strip = strip, span = span, shape = shape,
            circlePct = CameraSignalPolicy.CIRCLE_PCT_DEFAULT,
        )

    private fun uniformsFor(
        span: String,
        shape: String,
        rotationDeg: Int,
        amountPct: Int = CameraDewarpPrefs.AMOUNT_DEFAULT,
    ): CameraGlUniforms {
        val crop = cropOf(span, shape)
        val centre = CameraGlUniforms.sourceCentre(CamView.MIRROR_LEFT, strip = 1)
        return CameraGlUniforms.of(
            crop = crop, srcCentreX = centre[0], srcCentreY = centre[1],
            streamW = streamW, streamH = streamH, rotationDeg = rotationDeg, amountPct = amountPct,
        )
    }

    // ══ (1) HỢP ĐỒNG uniform — đủ tên, đúng tên ════════════════════════════════════════════════════════════

    /**
     * Mỗi `uniform` khai trong GLSL phải có **đúng một** nguồn giá trị: hoặc [CameraGlUniforms.VALUE_UNIFORMS], hoặc
     * sampler [CameraGlUniforms.SAMPLER_UNIFORM]. Thêm một uniform vào shader mà quên nối dây ⇒ nó nhận `0` và khung
     * ra sai **im lặng** (vd `uFocal = 0` ⇒ `enabled == false` ⇒ không nắn gì, trông y như "tính năng không chạy").
     *
     * Đọc từ **chính văn bản shader** (`declaredUniforms`), không từ bản tay `UNIFORMS` — nếu không thì hai bản tay
     * so với nhau và cả hai cùng thiếu.
     */
    @Test fun `bang gan uniform phu dung tap declaredUniforms cua shader`() {
        val declared = CameraDewarpShader.declaredUniforms()
        val covered = CameraGlUniforms.VALUE_UNIFORMS + CameraGlUniforms.SAMPLER_UNIFORM
        assertEquals(declared.toSet(), covered.toSet(), "uniform trong GLSL và nguồn giá trị phải khớp TỪNG tên")
        assertEquals(declared.size, covered.size, "không được có tên trùng ở bảng gán")
        assertEquals(
            CameraDewarpShader.UNIFORMS.toSet(), declared.toSet(),
            "bản tay UNIFORMS đã lệch khỏi văn bản shader — bài CameraDewarpTest cũng ghim, đây là lưới thứ hai",
        )
        assertFalse(
            CameraGlUniforms.SAMPLER_UNIFORM in CameraGlUniforms.VALUE_UNIFORMS,
            "uTex là texture unit, KHÔNG phải một giá trị hình học — trộn vào là mời gán nó bằng một `float`",
        )
    }

    // ══ (2) ĐỔI TRỤC t — không phải soi gương ══════════════════════════════════════════════════════════════

    /** `textureT` thuận nghịch với chính nó ⇒ hợp được với `flipV` mà không phải nhớ thứ tự áp. */
    @Test fun `doi truc t thuan nghich`() {
        val r = floatArrayOf(0.25f, 0.2f, 0.10f, 0.6f)
        val back = CameraGlUniforms.textureT(CameraGlUniforms.textureT(r))
        r.indices.forEach { i -> assertEquals(r[i], back[i], 1e-6f, "phần tử $i") }
    }

    /**
     * ⚠ Bài khoá cái bẫy đắt nhất của R8-B: **`textureT` ≠ `flipV`**, và chúng chỉ trùng nhau khi `y0 + y1 == 1`.
     *
     * Mọi crop hôm nay đều đối xứng quanh `y = 0,5` nên dùng `flipV` sẽ **đúng hôm nay** và sai đúng vào lần đầu ai
     * đó thu dải y (backlog CAM-ROT-2 đã ghi việc ấy sẽ tới, kiểu kinex `Y0/C0094o.java:330-336`). Bài này là thứ sẽ
     * đỏ ở đúng lượt ấy thay vì để owner nhìn một khung lật dọc trên xe.
     */
    @Test fun `doi truc t KHAC flipV, chi trung o crop doi xung`() {
        val symmetric = floatArrayOf(0.25f, 0.2f, 0.35f, 0.8f)   // y0 + y1 = 1.0
        val skewed = floatArrayOf(0.25f, 0.1f, 0.35f, 0.6f)      // y0 + y1 = 0.7

        listOf(symmetric to true, skewed to false).forEach { (crop, shouldMatch) ->
            val byAxis = CameraGlUniforms.textureT(CameraDewarp.srcRect(crop))
            val byFlip = CameraDewarp.srcRect(crop, flipV = true)
            val same = abs(byAxis[1] - byFlip[1]) < 1e-6f && abs(byAxis[3] - byFlip[3]) < 1e-6f
            assertEquals(shouldMatch, same, "crop y[${crop[1]}..${crop[3]}]: trùng=$shouldMatch")
        }

        // Và đây là con số của ca lệch: `1 − y0 = 0,9` chứ không phải `y1 = 0,6`.
        val rect = CameraGlUniforms.textureT(CameraDewarp.srcRect(skewed))
        assertEquals(0.9f, rect[1], 1e-6f, "t của mép TRÊN crop = 1 − y0")
        assertEquals(-0.5f, rect[3], 1e-6f, "bề cao t = −(y1 − y0)")
    }

    /** Mặc định của hai view gương: `uSrcRect` mang đúng dải x của 2.73 và trục t đã đổi. */
    @Test fun `uSrcRect mac dinh mang dung vet 2 73 tren truc t`() {
        val u = uniformsFor(CameraSignalPolicy.SPAN_NARROW, CameraSignalPolicy.SHAPE_RECT, rotationDeg = -90)
        assertEquals(0.25f, u.srcRect[0], 1e-6f, "x0 = 0,25 (vệt hẹp của 2.73)")
        assertEquals(0.10f, u.srcRect[2], 1e-6f, "bề rộng = 0,10")
        assertEquals(1.0f, u.srcRect[1], 1e-6f, "t của mép TRÊN (y0 = 0) là 1,0")
        assertEquals(-1.0f, u.srcRect[3], 1e-6f, "trục t ngược trục y ⇒ bề cao ÂM")
    }

    // ══ (3) BỘ SUY RA — khớp tài liệu, và tỉ lệ nghịch với bề ngang ô ══════════════════════════════════════

    /**
     * `K`/`F` **tỉ lệ nghịch với bề ngang ô**: vệt hẹp `512×960` cho `1,13084`, trọn dải `1280×960` cho `0,452335`,
     * tỉ số **đúng 2,5** (`1280/512`) — `camera-dewarp-math.md` §3 + §3.1.
     *
     * Đây là lý do sáu núm là **phần trăm của bộ suy ra** chứ không phải trị tuyệt đối: một `K` tuyệt đối vừa chỉnh
     * đúng cho vệt hẹp sẽ sai 2,5 lần ngay khi owner chạm chip *Vùng gương*.
     */
    @Test fun `bo suy ra khop tai lieu va ti le nghich voi be ngang o`() {
        val narrow = uniformsFor(CameraSignalPolicy.SPAN_NARROW, CameraSignalPolicy.SHAPE_RECT, 0)
        val strip = uniformsFor(CameraSignalPolicy.SPAN_STRIP, CameraSignalPolicy.SHAPE_RECT, 0)
        assertEquals(1.130836f, narrow.dewarp.k, 1e-5f, "vệt hẹp 512 px ⇒ K = (960/512)/rad(95)")
        assertEquals(0.452335f, strip.dewarp.k, 1e-5f, "trọn dải 1280 px ⇒ K = DewarpParams.DEFAULT_K")
        assertEquals(DewarpParams.DEFAULT_K, strip.dewarp.k, 1e-5f)
        assertEquals(2.5f, narrow.dewarp.k / strip.dewarp.k, 1e-4f, "1280/512 = 2,5")
        // `F = K·SCALE` ⇒ độ phóng tại tâm = 1 (ảnh nắn và ảnh thô trùng nhau ở giữa khung).
        listOf(narrow, strip).forEach { assertEquals(it.dewarp.k, it.dewarp.focal, 1e-6f) }
    }

    /** `uAspect` là tỉ lệ ô theo pixel NGUỒN — và hình TRÒN cho ô VUÔNG, tức thu về đúng Electro (`aspect == 1`). */
    @Test fun `uAspect theo pixel nguon, hinh TRON cho o vuong`() {
        assertEquals(
            512f / 960f, uniformsFor(CameraSignalPolicy.SPAN_NARROW, CameraSignalPolicy.SHAPE_RECT, 0).aspect, 1e-6f,
        )
        assertEquals(
            1280f / 960f, uniformsFor(CameraSignalPolicy.SPAN_STRIP, CameraSignalPolicy.SHAPE_RECT, 0).aspect, 1e-6f,
        )
        // squareCrop: cạnh = 0,1875 × 5120 = 960 px = bề cao ⇒ aspect = 1.
        assertEquals(
            1f, uniformsFor(CameraSignalPolicy.SPAN_NARROW, CameraSignalPolicy.SHAPE_ROUND, 0).aspect, 1e-6f,
            "hình TRÒN cắt ô vuông ⇒ aspect = 1 ⇒ công thức trùng khít Electro",
        )
    }

    /**
     * **Xoay KHÔNG đổi `uAspect`** — shader xoay **trước** khi nắn, nên p-space vẫn đo trên ô NGUỒN.
     *
     * Đảo hai trục khi xoay ±90 sẽ làm đồng-θ thành ellipse đúng ở ca xoay, tức ca **mặc định của cả hai bên gương**
     * (trái ↺ −90 / phải ↻ +90) — sai ở đúng chỗ không ai nhìn ca nào khác để so.
     */
    @Test fun `xoay khong doi uAspect`() {
        val base = uniformsFor(CameraSignalPolicy.SPAN_NARROW, CameraSignalPolicy.SHAPE_RECT, 0)
        listOf(-90, 90, 180, 270).forEach { deg ->
            val u = uniformsFor(CameraSignalPolicy.SPAN_NARROW, CameraSignalPolicy.SHAPE_RECT, deg)
            assertEquals(base.aspect, u.aspect, 1e-6f, "xoay $deg°")
            assertEquals(deg.toFloat(), u.rotationDeg, "góc phải đi thẳng vào uRotation, không quy đổi")
            assertEquals(base.dewarp, u.dewarp, "xoay không đổi một tham số nắn nào")
        }
    }

    /** Tâm quang của crop gương nằm **NGOÀI** ô (`1,25`) — kẹp về `[0,1]` là nắn quanh một điểm không phải quang tâm. */
    @Test fun `tam quang cua crop guong nam ngoai o`() {
        val narrow = uniformsFor(CameraSignalPolicy.SPAN_NARROW, CameraSignalPolicy.SHAPE_RECT, 0)
        assertEquals(1.25f, narrow.centerX, 1e-5f, "(0,375 − 0,25)/0,10 = 1,25 — dải 1, kinex Y0/C0094o.java:76")
        assertEquals(0.5f, narrow.centerY, 1e-5f)
        // Trọn dải thì tâm dải rơi đúng giữa ô; hình TRÒN cắt quanh tâm dải nên cũng vậy.
        assertEquals(0.5f, uniformsFor(CameraSignalPolicy.SPAN_STRIP, CameraSignalPolicy.SHAPE_RECT, 0).centerX, 1e-5f)
        assertEquals(0.5f, uniformsFor(CameraSignalPolicy.SPAN_NARROW, CameraSignalPolicy.SHAPE_ROUND, 0).centerX, 1e-5f)
        // Dải 2 (bên phải) — tâm 0,625, crop `x[0,65..0,75]` ⇒ tâm ở −0,25, tức ngoài ô về phía ÂM.
        val right = CameraGlUniforms.of(
            crop = CameraPanoCrop.narrowCrop(2, left = false),
            srcCentreX = CameraPanoCrop.stripCentre(2).toFloat(), srcCentreY = 0.5f,
            streamW = streamW, streamH = streamH, rotationDeg = 90,
        )
        assertEquals(-0.25f, right.centerX, 1e-5f, "(0,625 − 0,65)/0,10 = −0,25")
    }

    /** Chưa đo được cỡ luồng ⇒ `aspect = 1` (hành vi BIẾT TRƯỚC = Electro), không đoán một tỉ lệ nào. */
    @Test fun `chua biet co luong thi aspect ve 1`() {
        val centre = CameraGlUniforms.sourceCentre(CamView.MIRROR_LEFT, 1)
        val u = CameraGlUniforms.of(
            crop = CameraPanoCrop.narrowCrop(1, left = true),
            srcCentreX = centre[0], srcCentreY = centre[1],
            streamW = 0, streamH = 0, rotationDeg = 0,
        )
        assertEquals(1f, u.aspect, 1e-6f)
        assertTrue(u.enabled, "vẫn nắn được — chỉ là không có hiệu chỉnh tỉ lệ khung")
    }

    /** Lượt chụp THÔ: `amount = 0`, nguyên khung, không xoay — và `uTexMatrix` giữ lựa chọn của owner. */
    @Test fun `passthrough khong nan, nguyen khung, khong xoay`() {
        val p = CameraGlUniforms.passthrough(texMatrix = false)
        assertFalse(p.enabled, "amount = 0 ⇒ ba cổng của shader đóng")
        assertEquals(0f, p.dewarp.amount, 1e-6f)
        assertEquals(0f, p.rotationDeg)
        assertFalse(p.texMatrix)
        assertEquals(0f, p.srcRect[0], 1e-6f)
        assertEquals(1f, p.srcRect[1], 1e-6f)
        assertEquals(1f, p.srcRect[2], 1e-6f)
        assertEquals(-1f, p.srcRect[3], 1e-6f, "nguyên khung, trục t ngược ⇒ (0, 1, 1, −1)")
        assertTrue(CameraGlUniforms.passthrough().texMatrix, "mặc định vẫn BẬT (AOSP dặn áp ma trận)")
    }

    // ══ (4) CÙNG HÌNH HỌC với đường `TextureView` đang chạy ════════════════════════════════════════════════

    /**
     * ⚠ **Bài đắt nhất của R8-B**: với độ nắn `0`, đường GL và đường `TextureView` phải đặt **cùng một pixel nguồn**
     * vào **cùng một điểm khung** — trên **4 góc xoay × 2 bề rộng × 2 hình khung = 16 tổ hợp**, mỗi tổ hợp 5 điểm.
     *
     * Vì sao bài này quan trọng hơn mọi bài khác ở đây: nó là lời hứa *"đổi chip Kết xuất không đổi thứ owner đang
     * thấy"*. Nếu nó đỏ thì owner bật `GL` trên xe sẽ thấy hình **lật hoặc xoay sai** và sẽ đi chỉnh sáu núm nắn để
     * chữa một lỗi hình học — vòng chẩn đoán sai địa chỉ mà CLAUDE.md §2 nói tới.
     *
     * Ba quy ước bị ghim cùng lúc: chiều xoay (shader dùng ma trận **chuyển vị** của `postRotate`), trục `t`
     * ([CameraGlUniforms.textureT]), và quad `v = 0` ở **ĐỈNH** cửa sổ ([CameraGlRenderer] `POS`/`TEX`).
     */
    @Test fun `duong GL va duong TextureView cung mot hinh hoc khi khong nan`() {
        val area = 360
        var checked = 0
        for (span in CameraSignalPolicy.SPANS) for (shape in CameraSignalPolicy.SHAPES) {
            val crop = cropOf(span, shape)
            for (rot in listOf(0, -90, 90, 180)) {
                val frame = CameraOverlayFrame.fit(streamW, streamH, crop, rot, area, area)
                val m = CameraOverlayTransform.matrix(frame.w, frame.h, crop, rot)
                val u = uniformsFor(span, shape, rot, amountPct = 0)
                val tag = "$span/$shape/$rot°"
                listOf(
                    0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f, 0.5f to 0.5f,
                ).forEach { (uo, vo) ->
                    // Đường GL: toạ độ khung chuẩn hoá → toạ độ texture, rồi đổi trục t về trục y của ẢNH.
                    val t = CameraDewarp.sample(uo, vo, rot, u.dewarp, u.aspect, u.srcRect)
                    assertNotNull(t, "$tag ($uo,$vo): shader trả đen ở một điểm KHÔNG nắn — không thể đúng")
                    val glX = t!!.first
                    val glY = 1f - t.second
                    // Đường TextureView: nghịch đảo ma trận `setTransform` tại đúng điểm khung ấy (px).
                    val (tvX, tvY) = tvSource(m, frame.w, frame.h, uo * frame.w, vo * frame.h)
                    assertEquals(tvX.toDouble(), glX.toDouble(), 2e-3, "$tag ($uo,$vo) trục x")
                    assertEquals(tvY.toDouble(), glY.toDouble(), 2e-3, "$tag ($uo,$vo) trục y")
                    checked++
                }
            }
        }
        assertEquals(2 * 2 * 4 * 5, checked, "phải đi hết 2 bề rộng × 2 hình × 4 góc × 5 điểm")
    }

    /**
     * Điểm nguồn mà đường `TextureView` đặt tại điểm khung `(xp, yp)` px — **nghịch đảo** của
     * [CameraOverlayTransform.matrix], trả về toạ độ ảnh nguồn chuẩn hoá (y **xuống**, như mọi `crop` của dự án).
     *
     * Ma trận là affine (`m[6] = m[7] = 0`, `m[8] = 1`) nên nghịch đảo đóng: `det = m0·m4 − m1·m3`. `null` (không cần
     * transform) ⇒ `TextureView` căng nguyên ảnh lấp khung.
     */
    private fun tvSource(m: FloatArray?, vw: Int, vh: Int, xp: Float, yp: Float): Pair<Float, Float> {
        if (m == null) return (xp / vw) to (yp / vh)
        val det = m[0] * m[4] - m[1] * m[3]
        val dx = xp - m[2]
        val dy = yp - m[5]
        val x = (m[4] * dx - m[1] * dy) / det
        val y = (-m[3] * dx + m[0] * dy) / det
        return (x / vw) to (y / vh)
    }

    /** Ba cổng bật/tắt của phép nắn khớp shader: `amount`, `F`, `K·SCALE`. */
    @Test fun `do nan 0 thi khong nan, 100 thi nan`() {
        assertFalse(uniformsFor(CameraSignalPolicy.SPAN_NARROW, CameraSignalPolicy.SHAPE_RECT, 0, amountPct = 0).enabled)
        assertTrue(uniformsFor(CameraSignalPolicy.SPAN_NARROW, CameraSignalPolicy.SHAPE_RECT, 0, amountPct = 100).enabled)
        assertEquals(
            1f,
            uniformsFor(CameraSignalPolicy.SPAN_NARROW, CameraSignalPolicy.SHAPE_RECT, 0).dewarp.amount, 1e-6f,
            "mặc định = nắn ĐỦ (cả tầng đã nằm sau chip Kết xuất TẮT sẵn)",
        )
    }

    /** `sourceCentre`: view có crop dải ⇒ tâm DẢI; view nguyên khung ⇒ tâm khung; dải lạ ⇒ mặc định. */
    @Test fun `sourceCentre theo dai, view nguyen khung thi giua khung`() {
        assertEquals(0.375f, CameraGlUniforms.sourceCentre(CamView.MIRROR_LEFT, 1)[0], 1e-6f)
        assertEquals(0.625f, CameraGlUniforms.sourceCentre(CamView.MIRROR_LEFT, 2)[0], 1e-6f)
        assertEquals(0.5f, CameraGlUniforms.sourceCentre(CamView.REAR_LEFT, 1)[0], 1e-6f, "view không crop ⇒ tâm khung")
        assertEquals(0.5f, CameraGlUniforms.sourceCentre(CamView.MIRROR_LEFT, 1)[1], 1e-6f, "bốn dải cao trọn khung")
        // Dải ngoài `0..3` (prefs sửa tay) ⇒ mặc định, không ném và không cho một tâm ngoài ảnh.
        assertEquals(0.375f, CameraGlUniforms.sourceCentre(CamView.MIRROR_LEFT, 9)[0], 1e-6f)
    }

    /** `equals`/`hashCode` phải so **nội dung** `FloatArray` — nếu không, hai bộ giống nhau vẫn báo khác. */
    @Test fun `equals so noi dung srcRect`() {
        val a = uniformsFor(CameraSignalPolicy.SPAN_NARROW, CameraSignalPolicy.SHAPE_RECT, -90)
        val b = uniformsFor(CameraSignalPolicy.SPAN_NARROW, CameraSignalPolicy.SHAPE_RECT, -90)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == uniformsFor(CameraSignalPolicy.SPAN_STRIP, CameraSignalPolicy.SHAPE_RECT, -90))
    }

    /** `describe()` có đủ mười con số để đọc bằng mắt trên `logcat` — nó là bề mặt chẩn đoán duy nhất trên xe. */
    @Test fun `describe noi du muoi con so`() {
        val d = uniformsFor(CameraSignalPolicy.SPAN_NARROW, CameraSignalPolicy.SHAPE_RECT, -90).describe()
        listOf("srcRect=", "rot=", "aspect=", "amount=", "F=", "K=", "S=", "tâm=", "texMatrix=").forEach {
            assertTrue(it in d, "thiếu `$it` trong: $d")
        }
    }
}
