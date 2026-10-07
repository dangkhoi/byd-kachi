package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraSignalPolicy.CamView
import kotlin.math.abs

/**
 * ═══ MỘT GIÁ TRỊ mang TRỌN bộ uniform của đường nắn GL — THUẦN, test off-car ══════════════════════════════════
 *
 * R8-B (2.74). Hợp đồng uniform nằm ở `docs/diagnostics/offcar-2026-09-26/camera-dewarp-math.md` §5 và KDoc
 * [CameraDewarpShader]; lớp này là **chỗ duy nhất** dựng ra bộ giá trị ấy, còn `:app` chỉ `glUniform*` theo TÊN.
 *
 * ## Vì sao không để `:app` tự tính từng uniform
 * Bảy trong mười uniform là **kết quả của một phép hợp**: `uSrcRect` từ crop + soi gương, `uCenter` từ tâm dải đổi
 * sang toạ độ ô, `uAspect` từ pixel NGUỒN (không phải pixel khung!), bốn tham số nắn từ bộ suy ra **nhân** phần trăm
 * của owner. Mỗi phép ấy đã sai ít nhất một lần trong lịch sử tính năng này — và `:app` **không test được off-car**
 * (không GPU, `android.graphics.Matrix` là stub). Dồn cả bộ vào một `data class` thuần thì bài `:core` so được từng
 * con số với [CameraOverlayTransform]/[CameraDewarp], và `:app` không còn chỗ nào để lệch.
 *
 * ## Bốn cái bẫy mà bảng dưới đây đóng lại
 *  1. **`uAspect` là tỉ lệ ô theo pixel NGUỒN**, không phải tỉ lệ cửa sổ. Shader nắn trong **không gian ô đã chuẩn
 *     hoá** (bước 1 xoay → bước 2 nắn → bước 4 mới vào texture), nên đơn vị của mọi bán kính là *nửa bề ngang ô
 *     nguồn*. Lấy tỉ lệ cửa sổ ở đây là đúng cái lỗi ellipse của Electro (RE §3.3 ràng buộc 3).
 *  2. **Xoay KHÔNG đổi `uAspect`.** Xoay xảy ra **trước** phép nắn trong shader ⇒ p-space vẫn đo trên ô nguồn.
 *     Đảo hai trục của `uAspect` khi xoay ±90 là làm đồng-θ thành ellipse đúng ở ca xoay — tức ca mặc định của cả
 *     hai bên gương. Bài `xoay khong doi uAspect` ghim điều đó.
 *  3. **`uCenter` được phép NGOÀI `[0,1]`.** Tâm quang của dải 1 (`x = 0,375`) không nằm trong crop `x[0,25..0,35]`
 *     ⇒ `centerX = 1,25`. Kẹp nó về `[0,1]` là nắn quanh một điểm không phải quang tâm ⇒ *một bên thẳng, bên kia
 *     còng* — và trông đủ giống "gần đúng" để không ai nghi ma trận.
 *  4. **`uPan` KHÔNG phải `uCenter`.** Dịch cửa sổ giữ nguyên trục quang ⇒ ảnh vẫn thẳng; dời `uCenter` là đổi
 *     chính trục ấy ⇒ *một bên thẳng, bên kia còng* ([ĐO] xe 27/09 bác `cx −10 %`). Hai núm trông giống nhau trên
 *     màn Cài đặt nên chỗ này phải nói rõ — xem KDoc [CameraDewarp.panLocal].
 *
 * ## ⚠ Trục y của ẢNH đi XUỐNG, trục t của TEXTURE đi LÊN — [textureT] là chỗ đổi, và nó KHÔNG phải soi gương
 * Mọi `crop` của dự án (`CameraPanoCrop`, `CameraOverlayTransform`, `setTransform`) đo y **từ trên xuống** — quy ước
 * của màn hình. `uSrcRect` thì nuôi vào `uTexMatrix`, và [ĐO] AOSP `android-10.0.0_r47`
 * `graphics/java/android/graphics/SurfaceTexture.java:38-42` nói rõ đầu vào của ma trận ấy là *"traditional 2D
 * OpenGL ES texture coordinate"*: **`t = 0` là ĐÁY ảnh** (*"sampling from the bottom left corner of the image can be
 * accomplished by transforming the column vector (0, 0, 0, 1)"*). Hai quy ước ngược nhau ⇒ phải đổi, và đổi đúng
 * bằng `t = 1 − y`, KHÔNG bằng cách bật [CameraDewarp.srcRect] `flipV`:
 * ```
 *   flipV       ⇒ (y, h) = (y1,   y0 − y1)      ← soi gương quanh tâm CROP
 *   textureT    ⇒ (y, h) = (1−y0, y0 − y1)      ← đổi trục, quanh tâm ẢNH
 * ```
 * Hai công thức chỉ **trùng nhau khi `y0 + y1 = 1`** (crop đối xứng quanh giữa ảnh). Mọi crop hôm nay đều đối xứng
 * như vậy (`stripCrop`/`narrowCrop` là `y[0..1]`, `squareCrop` là `y[0,5±h]`) ⇒ **nếu dùng `flipV` thì hôm nay đúng
 * và sai đúng vào lần đầu ai đó thu dải y** (mà backlog CAM-ROT-2 đã ghi là việc sẽ tới, kiểu kinex
 * `Y0/C0094o.java:330-336`). Vì vậy đổi trục là một phép RIÊNG, có tên, có bài canh, và **hợp được** với `flipV`.
 *
 * `uCenter` KHÔNG đổi trục: nó sống trong không gian **ô** (`local`) — nơi phép nắn làm việc — và bước đổi trục nằm
 * **sau** phép nắn (shader bước 4). Xem sơ đồ ba không gian ở `camera-dewarp-math.md` §1.
 */
data class CameraGlUniforms(
    /**
     * `uSrcRect` = `(x, y, w, h)` chuẩn hoá — **giá trị uniform cuối cùng**, đã đổi sang trục `t` của texture
     * ([textureT]) nên `h` bình thường là **ÂM**. Soi gương thì ĐẢO dấu ấy lại — xem ⚠ ở KDoc lớp: dấu một mình
     * không còn đọc được thành *"có mirror hay không"*, nên đừng suy ngược từ nó.
     */
    val srcRect: FloatArray,
    /** `uRotation` (độ), **dương = cùng chiều kim đồng hồ trên màn** — đúng [CameraSignalPolicy.rotationDegrees]. */
    val rotationDeg: Float,
    /** `uAspect` = bề ngang/bề cao ô theo **pixel NGUỒN**. Xem bẫy (1)/(2) ở KDoc lớp. */
    val aspect: Float,
    /** `uAmount` · `uFocal` · `uK` · `uScale` · `uCenter` · `uPan` — đã [DewarpParams.clamped]. */
    val dewarp: DewarpParams,
    /** `false` ⇒ `:app` truyền **ma trận đơn vị** cho `uTexMatrix` (RE §7 Q17 — xem [CameraDewarpPrefs]). */
    val texMatrix: Boolean,
    /**
     * `uFit` (2.92) — vừa khung ô ra ↔ ô nội dung ([CameraDewarp.fitLocal], hệ số từ [CameraViewFit]). [NO_FIT]
     * `(0, 0)` ⇒ shader **bỏ hẳn** bước này: *Nắn thẳng* ở thu phóng 100 % đi đúng đường 2.74–2.91.
     */
    val fit: FloatArray = NO_FIT,
    /** Kiểu hình đang vẽ ([CameraViewMode.MODES]) — chỉ để ghi nhật ký ([describe]); shader chỉ biết số. */
    val mode: String = CameraViewMode.STRAIGHT,
) {

    /** `uCenter.x`. */
    val centerX: Float get() = dewarp.centerX

    /** `uCenter.y`. */
    val centerY: Float get() = dewarp.centerY

    /** `uPan.x` — dịch cửa sổ theo x của ô CHƯA XOAY ([CameraDewarp.panLocal]). */
    val panX: Float get() = dewarp.panX

    /** `uPan.y`. */
    val panY: Float get() = dewarp.panY

    /** Phép nắn có thật sự chạy hay không — **cùng ba cổng với shader** ([DewarpParams.enabled]). */
    val enabled: Boolean get() = dewarp.enabled

    /**
     * 2.76 L7 — đang **LẬT GƯƠNG** (pref `camera_mirror_*`, research §6.2) hay không, đọc từ dấu của `uSrcRect.z`.
     *
     * Khác với `h` (⚠ KDoc lớp: [textureT] đảo dấu `h` nên dấu ấy không còn nói được *"có flipV không"*), trục **x**
     * không đi qua phép đổi trục nào — `w < 0` ⇔ `flipH` của [CameraDewarp.srcRect], tức đúng một nghĩa. Lật ở
     * KHÔNG GIAN NGUỒN (bước 4 của shader), tức *"ảnh HAL bị lật tay"* được sửa **trước** khi xoay/dịch/nắn nhìn vào
     * nó ⇒ hợp với xoay ±90 đúng như một camera thật lắp ngược: `rot(mirror(src))`, không phải `mirror(rot(src))`.
     * ⚠ Lời hứa ấy chỉ đúng vì **tâm quang cũng lật** ([CameraDewarp.centerInCrop] nhận `flipH` — [P1] soát 27/09:
     * bản đầu của 2.76 lật `uSrcRect` mà giữ `uCenter`, tức nắn quanh một trục lệch tới 1,5 bề ngang ô ở crop hẹp).
     * Đường `TV` làm cùng phép ấy ở [CameraOverlayTransform.matrix] (lật SAU crop, TRƯỚC xoay) — bài
     * `CameraMirrorTest` ghim hai đường cho cùng một điểm.
     */
    val mirror: Boolean get() = srcRect[2] < 0f

    /**
     * Một dòng nhật ký đọc được bằng mắt trên xe (`logcat -s KachiCamera`). Không gọi trong đường khung hình —
     * nó cấp phát chuỗi; chỗ gọi duy nhất là lượt dựng overlay.
     */
    fun describe(): String = ("kiểu=%s srcRect=[%.4f,%.4f,%.4f,%.4f] rot=%.0f lật=%b aspect=%.4f amount=%.3f F=%.4f K=%.4f" +
        " S=%.3f κ=%.2f trụ=%b tâm=(%.4f,%.4f) dịch=(%.3f,%.3f) vừa=(%.3f,%.3f) texMatrix=%b")
        .format(mode, srcRect[0], srcRect[1], srcRect[2], srcRect[3], rotationDeg, mirror, aspect,
            dewarp.amount, dewarp.focal, dewarp.k, dewarp.scale, dewarp.kappa, dewarp.cylinder, centerX, centerY, panX, panY,
            fit[0], fit[1], texMatrix)

    /** `data class` với hai `FloatArray` ⇒ phải tự so nội dung, nếu không hai bộ giống nhau vẫn báo khác. */
    override fun equals(other: Any?): Boolean = this === other || (other is CameraGlUniforms &&
        srcRect.contentEquals(other.srcRect) && rotationDeg == other.rotationDeg && aspect == other.aspect &&
        dewarp == other.dewarp && texMatrix == other.texMatrix && fit.contentEquals(other.fit) && mode == other.mode)

    override fun hashCode(): Int = (srcRect.contentHashCode() * 31 + dewarp.hashCode()) * 31 + fit.contentHashCode()

    companion object {

        /**
         * Uniform nào được **giá trị** ở đây cấp — bài test so với [CameraDewarpShader.declaredUniforms] để một
         * uniform mới thêm vào GLSL mà quên nối dây là **đỏ**, thay vì im lặng nhận `0` và cho ra khung đen.
         *
         * `uTex` KHÔNG nằm đây: nó là sampler, gán bằng **số hiệu texture unit** ở tầng GL (một hằng `0`), không
         * phải một giá trị hình học. Bài test trừ đúng tên đó ra, tường minh.
         */
        val VALUE_UNIFORMS: List<String> = listOf(
            "uTexMatrix", "uSrcRect", "uRotation", "uAmount", "uFocal", "uK", "uScale", "uAspect", "uCenter",
            "uPan", "uFit", "uKappa", "uCyl",
        )

        /** `uFit = (0, 0)` ⇒ shader bỏ hẳn bước vừa khung (KDoc [fit]). Mảng dùng chung — KHÔNG được ghi vào. */
        val NO_FIT: FloatArray = floatArrayOf(0f, 0f)

        /** Sampler của texture OES — gán bằng texture unit, không bằng một giá trị hình học (xem [VALUE_UNIFORMS]). */
        const val SAMPLER_UNIFORM = "uTex"

        /**
         * Đổi một `(x, y, w, h)` từ **trục y của ẢNH (xuống)** sang **trục t của TEXTURE (lên)**: `t = 1 − y`.
         *
         * Xem ⚠ ở KDoc lớp về vì sao đây là một phép riêng chứ không phải `flipV`. Thuận nghịch với chính nó
         * (`textureT(textureT(r)) == r`) — bài `doi truc t thuan nghich` ghim điều đó, và nhờ vậy nó **hợp được** với
         * soi gương: `textureT(srcRect(crop, flipV = true))` vừa mirror vừa đúng trục.
         */
        fun textureT(rect: FloatArray): FloatArray =
            floatArrayOf(rect[0], 1f - rect[1], rect[2], -rect[3])

        /**
         * Tâm quang của ô, trong toạ độ **ẢNH NGUỒN** chuẩn hoá.
         *
         * View có crop dải pano ⇒ tâm là **tâm DẢI** ([CameraPanoCrop.stripCentre]; dải 1 ⇒ `0,375`, khớp kinex
         * `Y0/C0094o.java:76`). View nguyên khung ⇒ tâm khung. Trục y luôn `0,5`: bốn dải cao trọn khung (RE §2.1).
         *
         * ⚠ **[SUY]**, không phải [ĐO]: *"tâm vòng ảnh nằm giữa dải"* mới là giả định. Khung `5120×960` chụp từ xe
         * chốt lại (`camera-dewarp-math.md` §3.2 D1) — tới lúc đó núm `camera_dewarp_cx/cy` là đường sửa của owner.
         */
        fun sourceCentre(crop: FloatArray?, strip: Int): FloatArray {
            // ⚠ 2026-09-28: cổng này TRƯỚC ĐÂY hỏi `view.crop` — tức rect DỰNG SẴN của góc, không phải vùng cắt
            // ĐANG dùng. Khi [CameraPanoCrop.cropFor] bắt đầu cắt dải cho các góc không-Gương, tâm quang vẫn ở
            // giữa KHUNG ⇒ `centerInCrop` đẩy trục quang ra đúng MÉP NGOÀI của dải (0.5−0.25)/0.25 = 1.0 ⇒ hình
            // cong lệch hẳn. Nay bám vào chính vùng cắt đang dùng nên hai thứ không thể lệch pha nhau nữa.
            if (crop == null) return floatArrayOf(0.5f, 0.5f)
            val s = if (CameraPanoCrop.isStrip(strip)) strip else CameraPanoCrop.defaultStrip(left = true)
            return floatArrayOf(CameraPanoCrop.stripCentre(s).toFloat(), 0.5f)
        }

        /**
         * Dựng trọn bộ uniform.
         *
         * @param crop `(x0,y0,x1,y1)` chuẩn hoá của ẢNH NGUỒN ([CameraPanoCrop.cropFor]); `null` = nguyên khung.
         * @param srcCentreX,srcCentreY tâm quang trong toạ độ ảnh nguồn ([sourceCentre]).
         * @param streamW,streamH cỡ ảnh nguồn (px). `<= 0` ⇒ coi ô là vuông (`aspect = 1`) và vòng ảnh bằng bề cao ô:
         *   đó là ca *"chưa đo được cỡ luồng"*, và một `aspect` đoán bừa còn tệ hơn `1` — với `aspect == 1` công thức
         *   thu về **đúng** Electro (`camera-dewarp-math.md` §2), tức một hành vi biết trước.
         * @param rotationDeg góc xoay nội dung ([CameraSignalPolicy.rotationDegrees]); shader xoay, **không** ma trận.
         * @param flipH,flipV soi gương — đi bằng bề rộng/cao **ÂM** của `uSrcRect`, không bằng uniform mới (RE §6.2).
         * @param panXSign dấu của [panXPct] theo BÊN đang xem ([CameraDewarpPrefs.panXSign]) — `−1` lật trục x để
         *   một giá trị pref mang **cùng một nghĩa vật lý** ở cả hai gương. Trị đã nhân dấu là thứ [describe] in ra.
         * @param kappaPct κ họ phép chiếu, `%` tuyệt đối ([CameraDewarpPrefs.isKappaPct]); `100` = phối cảnh thẳng.
         * @param fit `uFit` đã tính ([CameraViewFit]); [NO_FIT] = bỏ bước vừa khung (đường cũ).
         * @param mode kiểu hình để ghi nhật ký — giá trị uniform KHÔNG phụ thuộc trường này ([CameraViewPlan] dựng số).
         * @param cylinder 2.94 R1 — `uCyl`: phép chiếu TRỤ ([DewarpParams.cylinder]); chỉ *Thẳng rộng* bật.
         */
        fun of(
            crop: FloatArray?,
            srcCentreX: Float,
            srcCentreY: Float,
            streamW: Int,
            streamH: Int,
            rotationDeg: Int,
            flipH: Boolean = false,
            flipV: Boolean = false,
            amountPct: Int = CameraDewarpPrefs.AMOUNT_DEFAULT,
            focalPct: Int = CameraDewarpPrefs.PCT_DEFAULT,
            kPct: Int = CameraDewarpPrefs.PCT_DEFAULT,
            scalePct: Int = CameraDewarpPrefs.PCT_DEFAULT,
            centerXPct: Int = CameraDewarpPrefs.CENTER_DEFAULT,
            centerYPct: Int = CameraDewarpPrefs.CENTER_DEFAULT,
            panXPct: Int = CameraDewarpPrefs.PAN_DEFAULT,
            panYPct: Int = CameraDewarpPrefs.PAN_DEFAULT,
            panXSign: Int = 1,
            texMatrix: Boolean = CameraDewarpPrefs.TEX_MATRIX_DEFAULT,
            kappaPct: Int = CameraDewarpPrefs.KAPPA_DEFAULT,
            fit: FloatArray = NO_FIT,
            mode: String = CameraViewMode.STRAIGHT,
            cylinder: Boolean = false,
        ): CameraGlUniforms {
            // Trục t của texture đi LÊN ⇒ đổi trục SAU khi dựng rect theo trục y của ảnh. Xem ⚠ ở KDoc lớp.
            val rect = textureT(CameraDewarp.srcRect(crop, flipH, flipV))
            // Cỡ ô theo pixel NGUỒN. Trị tuyệt đối vì soi gương cho `w`/`h` âm — `uAspect` là một tỉ lệ hình học,
            // không mang dấu (hợp đồng §5: *"dùng trị tuyệt đối khi soi gương"*).
            val known = streamW > 0 && streamH > 0
            val cellW = if (known) abs(rect[2]) * streamW else 1f
            val cellH = if (known) abs(rect[3]) * streamH else 1f
            val base = CameraDewarpPrefs.base(
                cellWidthPx = cellW,
                cellHeightPx = cellH,
                // Giả định [ĐOÁN] của bộ mặc định: đường kính vòng ảnh ≈ bề cao ẢNH (không phải bề cao ô — ô có thể
                // đã bị cắt trên/dưới ở hình TRÒN). Chưa biết cỡ luồng ⇒ bề cao ô, tức thu về ca `aspect = 1`.
                imageCircleDiameterPx = if (known) streamH.toFloat() else cellH,
            )
            // Lật ⇒ tâm quang cũng lật: trục quang nằm ở local `1 − c` khi `uSrcRect.z < 0` (⚠ KDoc [CameraDewarp.centerInCrop]).
            val (cx, cy) = CameraDewarp.centerInCrop(srcCentreX, srcCentreY, crop, flipH, flipV)
            return CameraGlUniforms(
                srcRect = rect,
                rotationDeg = rotationDeg.toFloat(),
                aspect = DewarpParams.aspectOf(cellW, cellH),
                dewarp = CameraDewarpPrefs.apply(
                    base = base, centerX = cx, centerY = cy,
                    amountPct = amountPct, focalPct = focalPct, kPct = kPct, scalePct = scalePct,
                    centerXPct = centerXPct, centerYPct = centerYPct,
                    // MỘT núm, MỘT nghĩa vật lý ở cả hai gương: hai camera gương là ảnh soi gương của nhau nên
                    // trục `+x` của ô trỏ ngược chiều ở hai bên — xem KDoc [CameraDewarpPrefs.panXSign].
                    panXPct = if (panXSign < 0) -panXPct else panXPct,
                    panYPct = panYPct,
                    kappaPct = kappaPct,
                ).copy(cylinder = cylinder),
                texMatrix = texMatrix,
                fit = fit,
                mode = mode,
            )
        }

        /**
         * Bộ uniform của lượt chụp **THÔ** (`camera_frame --es name raw`): không nắn, nguyên khung, không xoay,
         * không soi gương, `uTexMatrix` **giữ nguyên lựa chọn của owner**.
         *
         * Vì sao vẫn giữ `texMatrix`: lượt chụp thô tồn tại để trả lời *"khung HAL đổ ra thật sự trông thế nào"*, và
         * `getTransformMatrix` là **một phần của khung đó** (AOSP dặn phải áp — xem KDoc [CameraDewarpPrefs]). Tắt
         * nó ở đây sẽ cho ra một ảnh không phải thứ HAL đang đưa, tức làm bẩn đúng phép đo mình đang lấy.
         */
        fun passthrough(texMatrix: Boolean = CameraDewarpPrefs.TEX_MATRIX_DEFAULT): CameraGlUniforms =
            CameraGlUniforms(
                srcRect = textureT(CameraDewarp.srcRect(null)),
                rotationDeg = 0f,
                aspect = 1f,
                dewarp = DewarpParams(amount = 0f).clamped(),
                texMatrix = texMatrix,
            )
    }
}
