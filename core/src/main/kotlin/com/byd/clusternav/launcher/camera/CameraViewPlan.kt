package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraSignalPolicy.CamView

/**
 * ═══ KẾ HOẠCH MỘT PHIÊN theo KIỂU HÌNH — vùng cắt khung/nội dung · bộ uniform · tỉ lệ TextureView — THUẦN ═════════
 *
 * Spec `docs/specs/kachi-292-camera-full-view.html` §4.1/§4.5. `CameraSignalController.openSession` (`:app`) đọc prefs rồi
 * gọi ĐÚNG ba hàm ở đây — không một phép quyết theo kiểu nào nằm ở `:app` (không test được off-car).
 *
 * ## Hai vùng cắt
 *  • **khung** — thứ quyết TỈ LỆ cửa sổ (`CameraOverlayView`), tức chỗ đứng + hình khung. *Nắn thẳng*: đúng vùng cắt
 *    hôm nay. Kiểu trọn dải: hình *Tròn* giữ ô vuông của hôm nay (cửa sổ vẫn tròn đúng chỗ), hình khác lấy chính vùng
 *    nội dung (khung khớp tỉ lệ dải ⇒ không viền).
 *  • **nội dung** — thứ shader/ma trận LẤY MẪU. *Nắn thẳng*: = khung. Kiểu trọn dải: TRỌN dải
 *    (`span STRIP`, hình chữ nhật của [CameraPanoCrop.cropFor]) — kể cả khi người lái để `NARROW` hay hình *Tròn*.
 *
 * ## Bộ uniform theo kiểu (bảng §4.5 của spec)
 * | | *Nắn thẳng* | *Thẳng rộng* | *Gương cầu* |
 * |---|---|---|---|
 * | độ nắn | pref | 100 % | 0 |
 * | F / κ | pref / 1 | `wide_focal` / `wide_kappa` | (bỏ) / 1 |
 * | K · S · tâm | pref (chung — mô hình ống kính của xe) | pref | pref (không dùng) |
 * | dịch | pref | `wide_pan_x` × dấu bên, 0 | 0, 0 |
 * | `uFit` | [CameraViewFit.zoomOnly] | [CameraViewFit.letterbox] | [CameraViewFit.letterbox] |
 */
object CameraViewPlan {

    /** Hai vùng cắt + hình khung thật sự dùng ([CameraViewMode.frameShape]). Mảng `null` = nguyên khung ảnh. */
    class Crops(val frame: FloatArray?, val content: FloatArray?, val frameShape: String)

    /**
     * Mười một núm đọc từ prefs — mặc định = đúng hằng `:core` (hồ sơ trung tính), để test và chỗ gọi cùng một nguồn.
     * Tám núm đầu là bộ *Nắn thẳng* hôm nay; ba núm cuối là bộ *Thẳng rộng* (theo XE, chỉnh qua `prefs_set`).
     */
    data class Knobs(
        val amountPct: Int = CameraDewarpPrefs.AMOUNT_DEFAULT,
        val focalPct: Int = CameraDewarpPrefs.PCT_DEFAULT,
        val kPct: Int = CameraDewarpPrefs.PCT_DEFAULT,
        val scalePct: Int = CameraDewarpPrefs.PCT_DEFAULT,
        val centerXPct: Int = CameraDewarpPrefs.CENTER_DEFAULT,
        val centerYPct: Int = CameraDewarpPrefs.CENTER_DEFAULT,
        val panXPct: Int = CameraDewarpPrefs.PAN_DEFAULT,
        val panYPct: Int = CameraDewarpPrefs.PAN_DEFAULT,
        val wideKappaPct: Int = CameraViewMode.WIDE_KAPPA_PCT_DEFAULT,
        val wideFocalPct: Int = CameraViewMode.WIDE_FOCAL_PCT_DEFAULT,
        val widePanXPct: Int = CameraViewMode.WIDE_PAN_X_PCT_DEFAULT,
    )

    /**
     * Vùng cắt khung + nội dung cho kiểu [mode] (đã qua [CameraViewMode.effective]). *Nắn thẳng* ⇒ hai vùng LÀ một, và
     * là đúng lượt [CameraPanoCrop.cropFor] của hôm nay (cùng đối số, cùng thứ tự) ⇒ không đổi một pixel.
     */
    fun crops(
        mode: String,
        view: CamView,
        left: Boolean,
        strip: Int,
        panoStrip: Int?,
        span: String,
        shape: String,
        circlePct: Int,
    ): Crops {
        val today = CameraPanoCrop.cropFor(
            view = view, left = left, strip = strip, span = span, shape = shape, circlePct = circlePct,
            panoStrip = panoStrip,
        )
        if (!CameraViewMode.fullView(mode)) return Crops(today, today, shape)
        val content = CameraPanoCrop.cropFor(
            view = view, left = left, strip = strip, span = CameraSignalPolicy.SPAN_STRIP,
            shape = CameraSignalPolicy.SHAPE_RECT, circlePct = circlePct, panoStrip = panoStrip,
        )
        val frameShape = CameraViewMode.frameShape(mode, shape)
        val frame = if (frameShape == CameraSignalPolicy.SHAPE_ROUND) today else content
        return Crops(frame, content, frameShape)
    }

    /** `uFit` của phiên — *Nắn thẳng* chỉ thu phóng; hai kiểu kia vừa khung rồi thu phóng ([CameraViewFit]). */
    fun fit(mode: String, zoomPct: Int, crops: Crops, streamW: Int, streamH: Int): FloatArray {
        if (!CameraViewMode.fullView(mode)) return CameraViewFit.zoomOnly(zoomPct)
        return CameraViewFit.letterbox(
            frame = CameraViewFit.cropPx(crops.frame, streamW, streamH),
            content = CameraViewFit.cropPx(crops.content, streamW, streamH),
            round = crops.frameShape == CameraSignalPolicy.SHAPE_ROUND,
            zoomPct = zoomPct,
        )
    }

    /**
     * Trọn bộ uniform cho đường `GL`. *Nắn thẳng* ở thu phóng 100 % ⇒ **đúng** [CameraGlUniforms.of] của 2.91 với cùng
     * đối số (mười uniform cũ bằng hệt; `uFit = (0,0)`, `uKappa = 1`) — bài `CameraViewPlanTest` ghim từng trường.
     *
     * @param strip chỉ số dải của phiên — quyết tâm quang ([CameraGlUniforms.sourceCentre]).
     * @param left bên xi-nhan — dấu của mọi phép dịch theo x ([CameraDewarpPrefs.panXSign]).
     * @param panXSign 2.93 — dấu dịch x của CAMERA đang xem ([CameraWhich.panXSign]); mặc định = theo [left] (hai camera
     *   gương, y như 2.92). Camera GIỮA (sau/trước) truyền `0` ⇒ hai núm dịch theo bên không áp (KDoc [CameraWhich]).
     */
    fun gl(
        mode: String,
        zoomPct: Int,
        crops: Crops,
        strip: Int,
        streamW: Int,
        streamH: Int,
        rotationDeg: Int,
        mirror: Boolean,
        left: Boolean,
        knobs: Knobs,
        texMatrix: Boolean,
        panXSign: Int = CameraDewarpPrefs.panXSign(left),
    ): CameraGlUniforms {
        val m = if (CameraViewMode.isMode(mode)) mode else CameraViewMode.defaultMode()
        val centre = CameraGlUniforms.sourceCentre(crops.content, strip)
        val fit = fit(m, zoomPct, crops, streamW, streamH)
        // 2.93 — camera GIỮA (`panXSign == 0`): hai núm dịch-x theo bên KHÔNG áp (pan 0, dấu +1 vô hại vì pan đã 0);
        // camera gương: đúng dấu + đúng pref như 2.92 (bài `CameraViewPlanTest` ghim từng trường).
        val centreCam = panXSign == 0
        val sign = if (centreCam) 1 else panXSign
        val widePanX = if (centreCam) CameraDewarpPrefs.PAN_DEFAULT else knobs.widePanXPct
        val panX = if (centreCam) CameraDewarpPrefs.PAN_DEFAULT else knobs.panXPct
        return when (m) {
            CameraViewMode.WIDE -> CameraGlUniforms.of(
                crop = crops.content, srcCentreX = centre[0], srcCentreY = centre[1],
                streamW = streamW, streamH = streamH, rotationDeg = rotationDeg, flipH = mirror,
                amountPct = CameraDewarpPrefs.AMOUNT_MAX, focalPct = knobs.wideFocalPct, kPct = knobs.kPct,
                scalePct = knobs.scalePct, centerXPct = knobs.centerXPct, centerYPct = knobs.centerYPct,
                panXPct = widePanX, panYPct = CameraDewarpPrefs.PAN_DEFAULT, panXSign = sign,
                texMatrix = texMatrix, kappaPct = knobs.wideKappaPct, fit = fit, mode = m,
            )
            CameraViewMode.FISHEYE -> CameraGlUniforms.of(
                crop = crops.content, srcCentreX = centre[0], srcCentreY = centre[1],
                streamW = streamW, streamH = streamH, rotationDeg = rotationDeg, flipH = mirror,
                amountPct = CameraDewarpPrefs.AMOUNT_MIN, focalPct = knobs.focalPct, kPct = knobs.kPct,
                scalePct = knobs.scalePct, centerXPct = knobs.centerXPct, centerYPct = knobs.centerYPct,
                panXPct = CameraDewarpPrefs.PAN_DEFAULT, panYPct = CameraDewarpPrefs.PAN_DEFAULT, panXSign = sign,
                texMatrix = texMatrix, fit = fit, mode = m,
            )
            else -> CameraGlUniforms.of(
                crop = crops.content, srcCentreX = centre[0], srcCentreY = centre[1],
                streamW = streamW, streamH = streamH, rotationDeg = rotationDeg, flipH = mirror,
                amountPct = knobs.amountPct, focalPct = knobs.focalPct, kPct = knobs.kPct, scalePct = knobs.scalePct,
                centerXPct = knobs.centerXPct, centerYPct = knobs.centerYPct, panXPct = panX,
                panYPct = knobs.panYPct, panXSign = sign, texMatrix = texMatrix, fit = fit, mode = m,
            )
        }
    }

    /**
     * Tỉ lệ MÀN thêm vào ma trận `TextureView` (đường `TV` và đường RƠI của `GL`): kiểu trọn dải ⇒ vừa khung kiểu
     * *Gương cầu* (TV không có κ — xem [CameraViewMode.effective]); *Nắn thẳng* ⇒ chỉ thu phóng. `null` ⇒ không thêm phép
     * nào: ma trận y hệt hôm nay.
     */
    fun tvScale(mode: String, zoomPct: Int, crops: Crops, streamW: Int, streamH: Int, rotationDeg: Int): FloatArray? {
        val fit = if (CameraViewMode.fullView(mode)) {
            fit(CameraViewMode.FISHEYE, zoomPct, crops, streamW, streamH)
        } else {
            CameraViewFit.zoomOnly(zoomPct)
        }
        return CameraViewFit.tvScale(fit, rotationDeg)
    }
}
