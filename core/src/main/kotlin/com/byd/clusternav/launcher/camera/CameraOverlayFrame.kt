package com.byd.clusternav.launcher.camera

import kotlin.math.roundToInt

/**
 * ═══ CỠ CỬA SỔ overlay camera — ĐÚNG TỈ LỆ ảnh sau xoay, THUẦN, test off-car ══════════════════════════════════
 *
 * Owner 2026-09-26 (quyết định 7c, backlog CAM-ROT-2): *"không muốn có viền đen, nên làm overlay cho nó đúng với
 * tỷ lệ camera, không fix bừa"* ⇒ **cửa sổ** overlay phải mang đúng tỉ lệ của **vùng crop SAU khi xoay**, thay vì
 * ô VUÔNG cố định của 2.35–2.72 (ô vuông ⇒ hoặc dải đen, hoặc co giãn không đẳng hướng).
 *
 * ## Vì sao cỡ cửa sổ mới là chỗ chữa, không phải ma trận
 * [CameraOverlayTransform] đã **đúng** cho mọi khung: bước 1 căng vùng crop lấp khung, bước 3 bù tỉ lệ khi xoay
 * ±90 trong khung không vuông. Phép co giãn ấy chỉ **không đẳng hướng** khi tỉ lệ khung ≠ tỉ lệ ảnh. Cho khung
 * đúng tỉ lệ ⇒ cùng ma trận ấy trở thành phép **đẳng hướng**: không méo, và vùng crop vẫn phủ đủ khung nên
 * không có một pixel đen nào. Tức bản này **không sửa một dòng nào** của đường đã chạy hiện trường (CLAUDE.md §6)
 * — nó chỉ cấp cho đường đó một khung có tỉ lệ đúng.
 *
 * Chứng minh bằng số (bài `khong meo khi khung dung ti le`): dải gương 512×960 px, xoay −90, vùng cho phép 360×360
 * ⇒ khung **360×192**; một px nguồn thành 0,375 px khung ở CẢ hai trục.
 *
 * ## ⚠ Khung sau xoay ±90 của dải gương là khung NGANG, không phải khung dọc
 * Backlog CAM-ROT-2 viết "khung dọc thay khung vuông" — đó là **[SUY] của người ghi**, và hình học nói khác:
 * vùng crop gương là dải DỌC 512×960; xoay ±90 đổi vai hai trục ⇒ ảnh hiện ra **960×512 (1,875:1, NGANG)**. Đúng
 * chiều vật lý của một cam gương (trường nhìn rộng, thấp) và khớp RE kinex: blind-spot xoay 90 xuất **640×480
 * NGANG** ([ĐO] `jadx-kinex Y0/C0094o.java:343-344`). Ai muốn khung 4:3 như kinex thì phải **thu dải y của crop**
 * (`C0094o.java:330-336`) — đó là đổi crop, tức đổi đường đã chạy hiện trường, và backlog ghi rõ *"Không tự làm"*.
 *
 * ## Cỡ ảnh nguồn đến từ đâu (KHÔNG hardcode)
 * `:app` đo bằng `AVMCamera.getPreviewWidth/getPreviewHeight` ([ĐO RE] có thật trong lớp framework — firmware
 * `com/byd/dilink51_main/hardware/camera/DiLinkAVMCamera.java` gọi thẳng hai hàm đó) rồi gọi
 * `CameraOverlayView.onStreamMeasured`. Trước lượt đo đầu tiên thì dùng **gợi ý** của
 * [CameraSignalPolicy.CamView.hintW]/`hintH`; không có gợi ý ⇒ [fit] trả **đúng vùng vuông cũ**, tức hành vi
 * 2.72 không đổi cho tới khi có số thật.
 */
object CameraOverlayFrame {

    /**
     * Cỡ cửa sổ overlay (px) + cờ nói cỡ ảnh nguồn đã BIẾT chưa.
     *
     * [streamKnown] `false` = chưa có cỡ nguồn nào (không đo được, không gợi ý) ⇒ [w]×[h] chính là vùng cho phép,
     * y như trước bản này. Chỗ gọi dùng cờ này để ghi một dòng log trung thực thay vì khoe "đã đúng tỉ lệ".
     */
    data class Frame(val w: Int, val h: Int, val streamKnown: Boolean)

    /** Cỡ + độ lệch của lớp video khi KHÔNG có ma trận (đường `SurfaceView`) — xem [stretch]. */
    data class Stretch(val w: Int, val h: Int, val x: Int, val y: Int)

    /**
     * Cửa sổ lớn nhất có **tỉ lệ của vùng crop sau xoay** mà còn nằm trong vùng [areaW]×[areaH].
     *
     * - [streamW]×[streamH] = cỡ ảnh HAL đổ ra (px). `≤ 0` (chưa đo, không gợi ý) ⇒ trả nguyên vùng cho phép +
     *   `streamKnown=false`: **không đoán** tỉ lệ, giữ đúng ô vuông đã chạy trên xe.
     * - [crop] = `(x0,y0,x1,y1)` chuẩn hoá 0..1 **trong toạ độ ẢNH NGUỒN, áp TRƯỚC khi xoay** (đúng thứ tự của
     *   [CameraOverlayTransform.matrix]); `null` = cả khung.
     * - [rotationDeg] bội của 90 (âm được); 90/270 đổi vai hai trục.
     *
     * Không bao giờ vượt vùng cho phép, và **khi vùng cho phép hợp lệ** thì không bao giờ trả 0 (một cửa sổ 0 px là
     * một overlay vô hình mà không ai báo lỗi) — cạnh nhỏ nhất bị kẹp về 1. Vùng cho phép **tự nó** suy biến
     * ([areaW]/[areaH] ≤ 0, ca `displayMetrics` off-car) thì trả lại đúng vùng ấy: đó là sự thật của chỗ gọi, không
     * phải một con số ta nghĩ ra — và `WindowManager` từ chối nó ồn ào hơn là một cửa sổ 1×1 px trông như đã chạy.
     */
    fun fit(
        streamW: Int,
        streamH: Int,
        crop: FloatArray?,
        rotationDeg: Int,
        areaW: Int,
        areaH: Int,
    ): Frame {
        if (areaW <= 0 || areaH <= 0) return Frame(areaW, areaH, false)
        if (streamW <= 0 || streamH <= 0) return Frame(areaW, areaH, false)
        val srcW = cropSpan(crop, 0) * streamW
        val srcH = cropSpan(crop, 1) * streamH
        val swapAxes = quarterTurn(rotationDeg)
        val rotW = if (swapAxes) srcH else srcW
        val rotH = if (swapAxes) srcW else srcH
        // "Vừa khít": so hai tỉ lệ bằng phép nhân chéo (không chia ⇒ không có ca chia cho 0 nào lọt).
        return if (rotW * areaH >= rotH * areaW) {
            // Ảnh rộng hơn vùng ⇒ bề rộng quyết định.
            Frame(areaW, ((areaW * rotH) / rotW).roundToInt().coerceIn(1, areaH), true)
        } else {
            Frame(((areaH * rotW) / rotH).roundToInt().coerceIn(1, areaW), areaH, true)
        }
    }

    /**
     * Cỡ + độ lệch của lớp video để CẮT vùng [crop] mà **không** dùng ma trận — đường kết xuất `SurfaceView`
     * ([CameraSignalPolicy.RENDER_SURFACE]).
     *
     * `SurfaceView` là một **layer riêng** do SurfaceFlinger ghép: không có `setTransform`, nên cách duy nhất còn
     * lại để chỉ thấy 10 % bề ngang là **phóng lớp video lên `1/cw` lần rồi kéo lệch** cho đúng dải cần xem, và để
     * cửa sổ cắt phần thừa. Ví dụ khung 192×360 với crop gương trái `x[0.25..0.35]` ⇒ lớp video 1920×360 đặt ở
     * `x = -480`.
     *
     * ⚠ [ĐOÁN — chưa đo trên xe] ROM có cắt layer con theo biên cửa sổ hay không; cùng chỗ chưa biết đã ghi ở KDoc
     * `CameraOverlayView` về bo góc của layer video. Vì thế `SurfaceView` là **lựa chọn phụ để ĐO giật** (CLOSE-14
     * L2), không phải mặc định.
     */
    fun stretch(frameW: Int, frameH: Int, crop: FloatArray?): Stretch {
        if (frameW <= 0 || frameH <= 0) return Stretch(frameW, frameH, 0, 0)
        val cw = cropSpan(crop, 0)
        val ch = cropSpan(crop, 1)
        val w = (frameW / cw).roundToInt().coerceAtLeast(frameW)
        val h = (frameH / ch).roundToInt().coerceAtLeast(frameH)
        val full = crop != null && crop.size >= 4
        val x0 = if (full) crop!![0] else 0f
        val y0 = if (full) crop!![1] else 0f
        return Stretch(w, h, -(x0 * w).roundToInt(), -(y0 * h).roundToInt())
    }

    /** Góc [deg] có đổi vai hai trục (bội LẺ của 90) hay không — âm cũng đúng. */
    fun quarterTurn(deg: Int): Boolean {
        val d = ((deg % 360) + 360) % 360
        return d == 90 || d == 270
    }

    /**
     * Bề rộng ([axis] 0) hay bề cao ([axis] 1) của [crop], chuẩn hoá 0..1.
     *
     * Dùng chung ngưỡng [CameraOverlayTransform.MIN_SPAN] với tầng ma trận: hai bên phải coi *cùng một* dải suy
     * biến là cùng một con số, nếu không thì khung tính theo dải này mà ma trận căng theo dải kia.
     */
    private fun cropSpan(crop: FloatArray?, axis: Int): Float {
        if (crop == null || crop.size < 4) return 1f
        val span = crop[axis + 2] - crop[axis]
        return span.coerceAtLeast(CameraOverlayTransform.MIN_SPAN).coerceAtMost(1f)
    }
}
