package com.byd.clusternav.launcher.camera

/**
 * ═══ HÌNH HỌC transform của overlay camera — THUẦN, test off-car ═══════════════════════════════════════════════
 *
 * Xây **ma trận 3×3** mà `TextureView.setTransform` cần để (1) chỉ hiện một vùng CROP của ảnh nguồn, (2) XOAY nội
 * dung quanh tâm cửa sổ (spec `camera-turn-signal-hal-socket.html` R7).
 *
 * ## Vì sao ở `:core` chứ không nằm trong `CameraOverlayView`
 * Tới 2.67 phép toán này sống trong `:app` và **không có một bài test nào** — `android.graphics.Matrix` là stub trên
 * JVM (không Robolectric) nên mọi assert phải đi qua thiết bị. Kết quả: hai công thức dễ sai nhất của cả tính năng
 * (dịch sau crop, và bù tỉ lệ khi xoay ±90 trong khung KHÔNG vuông) chỉ được "kiểm" bằng mắt trên xe. Tách ra thành
 * hàm thuần cho phép chứng minh bằng số: xem `CameraOverlayTransformTest`. Cùng khuôn `AppScale` (CLAUDE.md §10:
 * parser/hình học là code thuần, test off-device).
 *
 * `:app` chỉ còn một việc: `Matrix().apply { setValues(...) }` rồi `setTransform`. Không có bản sao thứ hai của
 * công thức.
 *
 * ## Quy ước toạ độ (KHÔNG tự đặt — theo `TextureView`/`Matrix` của AOSP)
 *  • `TextureView` mặc định căng SurfaceTexture **lấp đầy view**: điểm chuẩn hoá `(u,v)` của ảnh nguồn nằm ở
 *    `(u·vw, v·vh)` px trong view. `setTransform` là ma trận **trên toạ độ VIEW (px)**, áp SAU phép căng đó.
 *  • Bố cục 9 phần tử = đúng thứ tự `android.graphics.Matrix.setValues` — **row-major**:
 *    `[MSCALE_X=0, MSKEW_X=1, MTRANS_X=2, MSKEW_Y=3, MSCALE_Y=4, MTRANS_Y=5, MPERSP_0=6, MPERSP_1=7, MPERSP_2=8]`,
 *    tức `x' = m[0]·x + m[1]·y + m[2]`, `y' = m[3]·x + m[4]·y + m[5]`. Bốn hằng chỉ số này được **ghim lại bằng
 *    assert** ở `CameraRotationWiringContractTest` (đọc trực tiếp từ `android.graphics.Matrix` của SDK) — nếu ai đó
 *    đổi quy ước thì bài đó đỏ, không phải màn hình xe.
 *  • Góc DƯƠNG = **cùng chiều kim đồng hồ** trên hệ toạ độ màn (trục y hướng XUỐNG) — đúng chiều dương của
 *    `Matrix.postRotate`, và đúng quy ước của [CameraSignalPolicy.rotationDegrees] (dương = ↻ sang phải).
 */
object CameraOverlayTransform {

    /** Bề rộng/cao crop nhỏ nhất được nhận (chống chia 0 khi pref/RE cho một dải suy biến). */
    const val MIN_SPAN = 0.001f

    /** Từ mức này coi như crop TOÀN khung ⇒ khỏi transform (giữ y hành vi trước R7). */
    const val FULL_SPAN = 0.999f

    /**
     * Ma trận cho [vw]×[vh] px với vùng [crop] `(x0,y0,x1,y1)` chuẩn hoá 0..1 và góc xoay [rotationDeg] (độ).
     *
     * `null` = **không cần transform** (view suy biến, hoặc không crop và không xoay) ⇒ chỗ gọi đừng gọi
     * `setTransform`, y như trước R7.
     *
     * ## Ba bước, theo đúng thứ tự đã chạy hiện trường (CLAUDE.md §6 — đường mới xuống CUỐI)
     *  1. **Crop** (2.36, đã chạy trên xe): phóng `1/cw`, `1/ch` quanh gốc rồi dịch để góc `(x0,y0)` về `(0,0)` ⇒
     *     vùng crop lấp **đúng** khung view.
     *  2. **Xoay** (R7, thêm SAU): quay quanh tâm view. Vùng crop đang lấp khung nên nó quay tại chỗ, tâm giữ tâm.
     *  3. **Bù tỉ lệ** khi xoay ±90 và khung KHÔNG vuông: sau bước 2 hình có bề ngang `vh` trong khung rộng `vw`
     *     (và cao `vw` trong khung cao `vh`) ⇒ hở/tràn. `scale(vw/vh, vh/vw)` quanh tâm đưa nó về **đúng** `vw×vh`.
     *     Cửa sổ hiện VUÔNG ⇒ hệ số = 1 (no-op); công thức viết tổng quát để một lượt đổi tỉ lệ cửa sổ sau này
     *     không làm hở mép âm thầm. ⚠ Chỉ đúng cho góc là **bội của 90** — và [CameraSignalPolicy.rotationDegrees]
     *     chỉ sinh −90/0/90/180 (bài `chi sinh boi cua 90` ghim điều đó).
     *
     * ⚠ Bước 1 + 3 đều là phép co giãn **KHÔNG đẳng hướng**: dải gương 512×960 căng vào ô vuông vốn đã bị giãn
     * ngang ~1,88× từ 2.36 (owner đã nhận "cắt video ok"), xoay chỉ mang đúng lượng giãn ấy sang trục kia — KHÔNG
     * làm méo thêm. Cách xoay không méo của kinex (thu hẹp dải y theo tỉ lệ ra) ghi ở §Reviewer Log của spec.
     */
    fun matrix(vw: Int, vh: Int, crop: FloatArray?, rotationDeg: Int): FloatArray? {
        if (vw <= 0 || vh <= 0) return null
        var m = IDENTITY.copyOf()
        if (crop != null && crop.size >= 4) {
            val x0 = crop[0]
            val y0 = crop[1]
            val cw = (crop[2] - crop[0]).coerceAtLeast(MIN_SPAN)
            val ch = (crop[3] - crop[1]).coerceAtLeast(MIN_SPAN)
            if (!(cw >= FULL_SPAN && ch >= FULL_SPAN)) {
                val sx = 1f / cw
                val sy = 1f / ch
                m = floatArrayOf(sx, 0f, -x0 * sx * vw, 0f, sy, -y0 * sy * vh, 0f, 0f, 1f)
            }
        }
        val deg = ((rotationDeg % 360) + 360) % 360
        if (deg != 0) {
            val cx = vw / 2f
            val cy = vh / 2f
            m = mul(rotateAbout(deg, cx, cy), m)
            if (deg == 90 || deg == 270) {
                m = mul(scaleAbout(vw.toFloat() / vh, vh.toFloat() / vw, cx, cy), m)
            }
        }
        return if (m.contentEquals(IDENTITY)) null else m
    }

    /**
     * Ảnh của điểm ảnh-nguồn chuẩn hoá `(u,v)` trong toạ độ VIEW px, sau [matrix] — `floatArrayOf(x, y)`.
     *
     * Đây là phép mà `TextureView` thực sự làm: căng `(u,v)` → `(u·vw, v·vh)` rồi nhân ma trận. Bài test dùng hàm
     * này để chứng minh **vùng crop phủ ĐỦ view** (bốn góc crop đi đúng bốn góc view ⇒ không lộ mép đen).
     */
    fun mapSource(m: FloatArray?, vw: Int, vh: Int, u: Float, v: Float): FloatArray {
        val x = u * vw
        val y = v * vh
        if (m == null) return floatArrayOf(x, y)
        return floatArrayOf(m[0] * x + m[1] * y + m[2], m[3] * x + m[4] * y + m[5])
    }

    private val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    /** `a ∘ b` (áp `b` trước, rồi `a`) — tương đương `Matrix.postConcat(a)` trên `b`. */
    private fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(9)
        for (r in 0..2) for (c in 0..2) {
            out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
        }
        return out
    }

    /** Quay [deg] (bội của 90 ⇒ sin/cos chính xác tuyệt đối, không sai số) quanh `(cx,cy)`. */
    private fun rotateAbout(deg: Int, cx: Float, cy: Float): FloatArray {
        val (cos, sin) = when (deg) {
            90 -> 0f to 1f
            180 -> -1f to 0f
            270 -> 0f to -1f
            0 -> 1f to 0f
            else -> {
                val r = Math.toRadians(deg.toDouble())
                Math.cos(r).toFloat() to Math.sin(r).toFloat()
            }
        }
        // translate(cx,cy) ∘ rot ∘ translate(-cx,-cy)
        return floatArrayOf(
            cos, -sin, cx - cos * cx + sin * cy,
            sin, cos, cy - sin * cx - cos * cy,
            0f, 0f, 1f,
        )
    }

    /** Co giãn `(sx,sy)` quanh `(cx,cy)`. */
    private fun scaleAbout(sx: Float, sy: Float, cx: Float, cy: Float): FloatArray = floatArrayOf(
        sx, 0f, cx - sx * cx,
        0f, sy, cy - sy * cy,
        0f, 0f, 1f,
    )
}
