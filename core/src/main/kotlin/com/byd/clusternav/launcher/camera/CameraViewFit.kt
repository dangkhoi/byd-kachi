package com.byd.clusternav.launcher.camera

import kotlin.math.sqrt

/**
 * ═══ VỪA KHUNG — nội dung (trọn dải) vào cửa sổ (tỉ lệ của vùng cắt KHUNG), viền đen chứ KHÔNG cắt — THUẦN ════════
 *
 * Spec `docs/specs/kachi-292-camera-full-view.html` §4.3. Một phép, hai đầu ra: `uFit` cho shader ([CameraDewarp.fitLocal])
 * và tỉ lệ màn cho đường `TextureView` ([tvScale] → [CameraOverlayTransform.matrix]).
 *
 * ## Vì sao chỉ cần TỈ LỆ, không cần cỡ pixel cửa sổ
 * Cửa sổ LUÔN mang đúng tỉ lệ vùng cắt KHUNG sau xoay ([CameraOverlayFrame.fit] trên màn chính, `tall`/`fit` trên cụm —
 * và ở kiểu trọn dải [CameraClusterBand.place] đi `fitInside` nên lớp video = cửa sổ, không phóng). Theo hai trục của ô
 * (CHƯA xoay) cửa sổ tỉ lệ với `(Fw, Fh)` px nguồn của vùng cắt khung, nội dung với `(Cw, Ch)` — xoay ±90 chỉ đổi trục
 * MÀN, không đổi phép so này (shader xoay TRƯỚC khi vừa khung). Hai cỡ px nguồn là đủ.
 *
 * ## Công thức
 * ```
 *   chữ nhật: σ = min(Fw/Cw, Fh/Ch)               // khít một cạnh, cạnh kia viền
 *   tròn    : σ = 1/√((Cw/Fw)² + (Ch/Fh)²)        // bốn góc chữ nhật nội dung nằm TRÊN elip/đường tròn nội tiếp
 *   σ' = σ·zoom ;  uFit = (Fw/(Cw·σ'), Fh/(Ch·σ'))
 * ```
 * `uFit > 1` ⇒ cửa sổ phủ nhiều hơn một ô nội dung (viền đen); `< 1` ⇒ phóng vào. Đẳng hướng theo px:
 * `uFit.x/Fw = uFit.y/Fh` — ảnh không bao giờ bị kéo một chiều.
 */
object CameraViewFit {

    /** Cỡ px nguồn của một vùng cắt `(x0,y0,x1,y1)`; `null` = nguyên khung. Luồng chưa biết cỡ ⇒ `null` (xem [letterbox]). */
    fun cropPx(crop: FloatArray?, streamW: Int, streamH: Int): FloatArray? {
        if (streamW <= 0 || streamH <= 0) return null
        if (crop == null || crop.size < 4) return floatArrayOf(streamW.toFloat(), streamH.toFloat())
        val w = (crop[2] - crop[0]).coerceAtLeast(CameraOverlayTransform.MIN_SPAN) * streamW
        val h = (crop[3] - crop[1]).coerceAtLeast(CameraOverlayTransform.MIN_SPAN) * streamH
        return floatArrayOf(w, h)
    }

    /**
     * `uFit` vừa khung cho nội dung [content] (px nguồn) trong cửa sổ có tỉ lệ [frame] (px nguồn), có thu phóng.
     *
     * Chưa biết cỡ ([frame]/[content] `null` — luồng chưa đo, không gợi ý): coi nội dung CÙNG tỉ lệ khung — cửa sổ khi ấy
     * là ô vuông của vùng cho phép (`CameraOverlayFrame.fit` trả nguyên vùng), và một tỉ lệ đoán bừa còn tệ hơn.
     */
    fun letterbox(frame: FloatArray?, content: FloatArray?, round: Boolean, zoomPct: Int): FloatArray {
        val f = frame ?: content ?: UNIT
        val c = content ?: f
        val fw = f[0].toDouble().coerceAtLeast(EPS)
        val fh = f[1].toDouble().coerceAtLeast(EPS)
        val cw = c[0].toDouble().coerceAtLeast(EPS)
        val ch = c[1].toDouble().coerceAtLeast(EPS)
        val sigma = if (round) {
            1.0 / sqrt((cw / fw) * (cw / fw) + (ch / fh) * (ch / fh))
        } else {
            minOf(fw / cw, fh / ch)
        }
        val s = sigma * zoom(zoomPct)
        return floatArrayOf((fw / (cw * s)).toFloat(), (fh / (ch * s)).toFloat())
    }

    /**
     * `uFit` của *Nắn thẳng*: 100 % ⇒ [CameraGlUniforms.NO_FIT] (shader bỏ hẳn bước vừa khung — đường cũ từng bit);
     * khác ⇒ phóng ĐỀU quanh tâm khung (`1/zoom` mỗi trục).
     */
    fun zoomOnly(zoomPct: Int): FloatArray {
        val z = zoom(zoomPct)
        if (z == 1.0) return CameraGlUniforms.NO_FIT
        return floatArrayOf((1.0 / z).toFloat(), (1.0 / z).toFloat())
    }

    /**
     * Tỉ lệ MÀN (theo trục view) cho ma trận `TextureView`: nội dung chiếm `1/uFit` cửa sổ theo trục của ô, đổi trục khi
     * xoay bội lẻ của 90. `null` ⇒ không thêm phép nào (đường hôm nay — [CameraOverlayTransform.matrix] giữ ma trận cũ).
     */
    fun tvScale(fit: FloatArray, rotationDeg: Int): FloatArray? {
        if (fit.size < 2 || fit[0] <= 0f || fit[1] <= 0f) return null
        val fx = 1f / fit[0]
        val fy = 1f / fit[1]
        return if (CameraOverlayFrame.quarterTurn(rotationDeg)) floatArrayOf(fy, fx) else floatArrayOf(fx, fy)
    }

    /**
     * Bài kiểm của chính phép: góc `(±0.5, ±0.5)` của ô nội dung (toạ độ ô) rơi vào đâu trong cửa sổ chuẩn hoá — `true`
     * khi cả bốn nằm trong khung (chữ nhật: `[0,1]²`; tròn: elip nội tiếp). Dùng cho test hình học thật, không ở đường vẽ.
     */
    fun contentInside(fit: FloatArray, round: Boolean, slack: Float = 1e-4f): Boolean {
        if (fit.size < 2 || fit[0] <= 0f || fit[1] <= 0f) return true
        val hx = 0.5f / fit[0]
        val hy = 0.5f / fit[1]
        return if (round) (hx / 0.5f) * (hx / 0.5f) + (hy / 0.5f) * (hy / 0.5f) <= 1f + slack
        else hx <= 0.5f + slack && hy <= 0.5f + slack
    }

    private fun zoom(zoomPct: Int): Double =
        (if (CameraViewMode.isZoomPct(zoomPct)) zoomPct else CameraViewMode.ZOOM_DEFAULT) / 100.0

    private const val EPS = 1e-6
    private val UNIT = floatArrayOf(1f, 1f)
}
