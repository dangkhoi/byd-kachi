package com.byd.clusternav.launcher.camera

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * ═══ ẢNH FISHEYE TỔNG HỢP để kiểm phép nắn — THUẦN, không Android, không tệp ═══════════════════════════════════
 *
 * Sinh một khung **giống khung AVM thật**: lưới thẳng của thế giới bị bẻ thành đường cong, chân trời lệch tâm cũng
 * cong, một vòng tròn đánh dấu ở góc tia cố định, vành tối ngoài vòng ảnh. Dựng bằng **mô hình chụp** đẳng khoảng
 * ([CameraDewarp.idealEquidistantSource] cùng quy ước), tức đúng mô hình mà [CameraDewarpShader] giả định khi nắn
 * ngược lại ⇒ nắn một ảnh sinh ở đây với [lensParams] phải trả lại **đường thẳng thật thẳng**.
 *
 * ## Dùng để làm gì (CLAUDE.md §14 — tầng 1/tầng 4)
 *  • **Off-car, ngay bây giờ**: bài `CameraDewarpTest` kiểm bằng số mà không cần xe, không cần GPU.
 *  • **Máy ảo, agent kế tiếp**: `:app` đẩy `IntArray` này vào `Surface` của `SurfaceTexture` camera giả rồi so ảnh
 *    ra của shader với [CameraDewarp.sample] tính trên CPU ⇒ chứng minh GLSL và Kotlin **cùng một công thức** trên
 *    GPU thật, trước khi động tới xe. Kế hoạch ở `docs/diagnostics/offcar-2026-09-26/camera-dewarp-math.md`.
 *  • **Trên xe**: KHÔNG dùng. Ảnh này là **tổng hợp** — nó không nói gì về ống kính thật (equidistant hay
 *    equisolid, RE §3.3), nên đừng bao giờ dùng nó để "xác nhận" tham số. Tham số chốt bằng một khung 5120×960
 *    chụp từ xe (RE §7 Q1/Q6).
 *
 * ## Vì sao vẽ ngược (mỗi pixel nguồn → điểm thế giới) chứ không vẽ xuôi (đi dọc đường thẳng rồi chấm điểm)
 * Vẽ xuôi phải chọn bước đi: bước thưa thì đường **đứt nét** ở vùng bị nén, bước dày thì đắt và đè lên nhau ⇒ số
 * pixel của một nét không tính trước được, nên không test được bằng số. Vẽ ngược cho mỗi pixel đúng một câu trả lời,
 * và độ dày nét giữ **hằng theo pixel** nhờ nhân với gradient giải tích `|∇X|`, `|∇Y|` — nhờ đó bài test đếm được số
 * vạch và đếm được **đúng** số pixel của nhãn.
 */
object CameraDewarpTestPattern {

    /** Ngoài vòng ảnh — gần đen nhưng KHÁC 0 để phân biệt được với "đen đặc" của shader (`#000000`). */
    const val CORNER = 0xFF050505.toInt()

    /** Trong vòng ảnh nhưng ngoài tầm mặt phẳng thế giới (tia > [Spec.sceneHalfFovDeg]). */
    const val VIGNETTE = 0xFF1A1A1A.toInt()

    /** Vạch lưới (mặt phẳng thế giới, cách nhau [Spec.gridStep]). */
    const val GRID = 0xFFBFBFBF.toInt()

    /** Chân trời — một đường thẳng LỆCH tâm ⇒ cong trong ảnh fisheye (vạch lưới qua tâm thì vẫn thẳng). */
    const val HORIZON = 0xFFFFC24D.toInt()

    /** Vòng tròn đánh dấu ở [Spec.ringThetaDeg] — đồng-θ, nên phải TRÒN theo pixel. */
    const val RING = 0xFF35D0FF.toInt()

    /** Chữ số nhãn dải. */
    const val LABEL = 0xFFFFFFFF.toInt()

    /** Nền của từng dải 0..3 — đủ khác nhau để mắt và test đều nhận ra dải nào là dải nào. */
    val STRIP_TINTS: List<Int> = listOf(
        0xFF16304E.toInt(),
        0xFF14421F.toInt(),
        0xFF4E1A1A.toInt(),
        0xFF3A1A4E.toInt(),
    )

    /** Nền của dải [index] (quay vòng nếu nhiều hơn 4 dải). */
    fun stripTint(index: Int): Int = STRIP_TINTS[((index % STRIP_TINTS.size) + STRIP_TINTS.size) % STRIP_TINTS.size]

    /**
     * Hình học của ảnh sinh ra. Mặc định = **một dải** `1280×960` của ảnh 4-in-1 (RE §2.1: pano `5120×960` =
     * `4 × 1280×960`), vòng ảnh ≈ bề cao khung, ống kính 190° — cùng giả định với [DewarpParams.DEFAULT_K].
     */
    data class Spec(
        val width: Int = 1280,
        val height: Int = 960,
        /** Số dải dọc bằng nhau; mỗi dải có một tâm quang riêng ở giữa nó. */
        val strips: Int = 1,
        val lensFovDeg: Float = DewarpParams.DEFAULT_LENS_FOV_DEG,
        /** Đường kính vòng ảnh, px. `<= 0` ⇒ bằng bề cao khung. */
        val imageCircleDiameterPx: Float = 0f,
        /** Nửa-FOV còn có mặt phẳng thế giới. Ngoài ngưỡng này là [VIGNETTE] — mặt phẳng không phủ được tia ≥ 90°. */
        val sceneHalfFovDeg: Float = 80f,
        /** Khoảng giữa hai vạch lưới, đơn vị mặt phẳng ở khoảng cách 1 (tức `tan` của góc tia). */
        val gridStep: Float = 0.5f,
        /**
         * Lệch pha của lưới: vạch nằm ở `(n + gridOffset)·gridStep`.
         *
         * Mặc định **0,5** — tức KHÔNG có vạch nào đi qua quang tâm, và đó là chủ ý: một vạch qua tâm sẽ phủ **trọn**
         * hàng/cột giữa (đường qua trục quang vẫn thẳng, nên nó *là* hàng giữa) làm không đếm được vạch dọc nào nữa.
         * Muốn có chữ thập qua tâm để nhìn thì đặt `0` và đặt [horizonY] `= 0`.
         */
        val gridOffset: Float = 0.5f,
        val gridThicknessPx: Float = 3f,
        /** Vị trí chân trời trên trục y của mặt phẳng; `0` = qua tâm (khi đó nó **thẳng**, không cong). */
        val horizonY: Float = 0.35f,
        val horizonThicknessPx: Float = 3f,
        val ringThetaDeg: Float = 45f,
        val ringThicknessPx: Float = 3f,
        /** Phóng của chữ số nhãn (font 3×5) — số pixel nhãn = `bits × scale²`, bài test đếm đúng con số ấy. */
        val labelScale: Int = 12,
        val drawLabels: Boolean = true,
    ) {
        val stripWidth: Int get() = if (strips > 0) width / strips else width
        val circleRadiusPx: Float get() = if (imageCircleDiameterPx > 0f) imageCircleDiameterPx / 2f else height / 2f
    }

    /** Một dải `1280×960` (mặc định) — ca dùng để test toán. */
    fun strip(spec: Spec = Spec()): IntArray = render(spec)

    /** Khung 4-in-1 `5120×960` với bốn dải khác màu, nhãn `0..3` — ca dùng để test chọn dải + đẩy vào máy ảo. */
    fun pano(spec: Spec = Spec(width = 5120, height = 960, strips = 4)): IntArray = render(spec)

    /**
     * Bộ tham số **khớp đúng ống kính** mà [render] đã dùng, cho một ô = trọn một dải.
     *
     * Nắn ảnh sinh ra bằng bộ này (với `amount = 1`) là phép nghịch **chính xác** của mô hình chụp ⇒ mọi đường thẳng
     * của thế giới trở lại thẳng. Ô hẹp hơn dải thì **phải** đổi `K`/`F` theo [DewarpParams.derive] và đổi tâm theo
     * [CameraDewarp.centerInCrop] — xem cảnh báo "tỉ lệ nghịch với bề ngang ô" ở đó.
     */
    fun lensParams(spec: Spec = Spec()): DewarpParams = DewarpParams.derive(
        rectWidthPx = spec.stripWidth.toFloat(),
        rectHeightPx = spec.height.toFloat(),
        imageCircleDiameterPx = spec.circleRadiusPx * 2f,
        lensFovDeg = spec.lensFovDeg,
    )

    /** Tỉ lệ ô của một dải — thứ phải truyền vào `uAspect`/`aspect`. */
    fun stripAspect(spec: Spec = Spec()): Float =
        DewarpParams.aspectOf(spec.stripWidth.toFloat(), spec.height.toFloat())

    /**
     * Vẽ ảnh, trả `IntArray` ARGB kích thước `width × height`, **hàng trước cột sau** (`out[y*width + x]`).
     *
     * Ưu tiên tô: nhãn > vòng đánh dấu > chân trời > lưới > nền dải > vành tối > ngoài vòng ảnh.
     */
    fun render(spec: Spec): IntArray {
        require(spec.width > 0 && spec.height > 0) { "co anh phai duong: ${spec.width}x${spec.height}" }
        val out = IntArray(spec.width * spec.height)
        val strips = if (spec.strips > 0) spec.strips else 1
        val stripW = spec.stripWidth
        val radius = spec.circleRadiusPx
        val thetaMax = Math.toRadians(spec.lensFovDeg.toDouble() / 2.0)
        val thetaScene = Math.toRadians(spec.sceneHalfFovDeg.toDouble().coerceAtMost(89.0))
        val ringRadiusPx = radius * (Math.toRadians(spec.ringThetaDeg.toDouble()) / thetaMax).toFloat()
        val cy = spec.height / 2.0

        for (s in 0 until strips) {
            val x0 = s * stripW
            val x1 = if (s == strips - 1) spec.width else x0 + stripW
            val cx = x0 + stripW / 2.0
            val tint = stripTint(s)
            for (y in 0 until spec.height) {
                val dy = y + 0.5 - cy
                var idx = y * spec.width + x0
                for (x in x0 until x1) {
                    val dx = x + 0.5 - cx
                    out[idx++] = pixel(dx, dy, radius, thetaMax, thetaScene, ringRadiusPx, tint, spec)
                }
            }
            if (spec.drawLabels) drawDigit(out, spec, s, cx, cy, radius)
        }
        return out
    }

    private fun pixel(
        dx: Double,
        dy: Double,
        radius: Float,
        thetaMax: Double,
        thetaScene: Double,
        ringRadiusPx: Float,
        tint: Int,
        spec: Spec,
    ): Int {
        val r = hypot(dx, dy)
        if (r > radius) return CORNER
        if (abs(r - ringRadiusPx) < spec.ringThicknessPx / 2.0) return RING
        val theta = (r / radius) * thetaMax
        if (theta > thetaScene) return VIGNETTE
        if (r < 0.5) return tint

        val rho = tan(theta)
        val phi = atan2(dy, dx)
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val sceneX = rho * cosPhi
        val sceneY = rho * sinPhi

        // Gradient giải tích: bán kính đổi `rhoDr` mỗi px, tiếp tuyến đổi `rho/r` mỗi px ⇒ nét dày HẰNG theo pixel.
        val cosT = cos(theta)
        val rhoDr = (thetaMax / radius) / (cosT * cosT)
        val tangential = rho / r
        val gradX = hypot(rhoDr * cosPhi, tangential * sinPhi)
        val gradY = hypot(rhoDr * sinPhi, tangential * cosPhi)

        if (abs(sceneY - spec.horizonY) < spec.horizonThicknessPx / 2.0 * gradY) return HORIZON
        val step = spec.gridStep.toDouble()
        if (step > 0.0) {
            val off = spec.gridOffset.toDouble()
            if (abs(sceneX - gridLineNear(sceneX, step, off)) < spec.gridThicknessPx / 2.0 * gradX) return GRID
            if (abs(sceneY - gridLineNear(sceneY, step, off)) < spec.gridThicknessPx / 2.0 * gradY) return GRID
        }
        return tint
    }

    /** Vạch lưới gần [value] nhất — vạch nằm ở `(n + offset)·step`. */
    private fun gridLineNear(value: Double, step: Double, offset: Double): Double =
        (Math.round(value / step - offset) + offset) * step

    /** Vị trí (đơn vị mặt phẳng) của các vạch lưới trong khoảng `(-limit, limit)` — bài test đếm bằng nó. */
    fun gridLinesWithin(limit: Float, spec: Spec = Spec()): List<Float> {
        val step = spec.gridStep
        if (step <= 0f) return emptyList()
        val out = ArrayList<Float>()
        var n = -512
        while (n <= 512) {
            val m = (n + spec.gridOffset) * step
            if (abs(m) < limit) out.add(m)
            n++
        }
        return out.sorted()
    }

    /** Góc tia (rad) của một pixel cách quang tâm [rPx] — mô hình đẳng khoảng của ảnh sinh ra. */
    fun thetaAtRadius(rPx: Float, spec: Spec = Spec()): Float {
        val radius = spec.circleRadiusPx
        if (radius <= 0f) return 0f
        return (rPx / radius * Math.toRadians(spec.lensFovDeg.toDouble() / 2.0)).toFloat()
    }

    /** Font 3×5 cho `0..9`; mỗi `Int` là 15 bit, bit cao nhất = pixel trên-trái, đọc theo hàng. */
    private val DIGITS = intArrayOf(
        0b111_101_101_101_111, // 0
        0b010_010_010_010_010, // 1
        0b111_001_111_100_111, // 2
        0b111_001_111_001_111, // 3
        0b101_101_111_001_001, // 4
        0b111_100_111_001_111, // 5
        0b111_100_111_101_111, // 6
        0b111_001_001_001_001, // 7
        0b111_101_111_101_111, // 8
        0b111_101_111_001_111, // 9
    )

    /** Số pixel bật của chữ số [digit] khi vẽ với [Spec.labelScale] — bài test đối chiếu đúng con số này. */
    fun labelPixelCount(digit: Int, spec: Spec = Spec()): Int {
        val bits = Integer.bitCount(DIGITS[((digit % 10) + 10) % 10])
        return bits * spec.labelScale * spec.labelScale
    }

    private fun drawDigit(out: IntArray, spec: Spec, stripIndex: Int, cx: Double, cy: Double, radius: Float) {
        val glyph = DIGITS[((stripIndex % 10) + 10) % 10]
        val sc = spec.labelScale.coerceAtLeast(1)
        val left = (cx - 3 * sc / 2.0).roundToInt()
        val top = (cy - radius * 0.85).roundToInt()
        for (row in 0 until 5) for (col in 0 until 3) {
            if ((glyph shr (14 - (row * 3 + col))) and 1 == 0) continue
            for (py in 0 until sc) for (px in 0 until sc) {
                val x = left + col * sc + px
                val y = top + row * sc + py
                if (x in 0 until spec.width && y in 0 until spec.height) out[y * spec.width + x] = LABEL
            }
        }
    }
}
