package com.byd.clusternav.launcher.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.byd.clusternav.launcher.KachiSpace
import com.byd.clusternav.launcher.KachiTheme
import com.byd.clusternav.launcher.KachiType

/**
 * ═══ 2.93 · Ô KÉO-THẢ vị trí camera (bộ chỉnh *Từng camera*) — cùng khuôn bộ chỉnh bóng VietMap ════════════════════════
 *
 * Owner 06/10: *"cho chỉnh size và vị trí từng camera"* — vị trí kéo-thả trên ô xem trước, như [com.byd.clusternav
 * .VmBubblePlacementView]. Khác ở hai chỗ, đều có chủ ý:
 *  • **Không tự tính hình học**: cửa sổ vẽ ra là đúng thứ overlay sẽ dựng — [CameraPlacement.Model] (`:core`, có test bằng
 *    số) cho cỡ khung đúng tỉ lệ ảnh + vùng cho phép (dưới thanh trên / dải cụm) + phép kẹp; view chỉ đổi toạ độ display ↔
 *    toạ độ ô (letterbox giữ tỉ lệ display).
 *  • **Không chữ cứng**: nhãn đến từ tài nguyên qua chỗ dựng (tầng `launcher/` cấm chuỗi viết cứng — `LauncherI18nContractTest`).
 *
 * Chạm/kéo dời TÂM khung theo ngón tay (đã kẹp trong vùng); `ACTION_UP` báo vị trí lưu bền ([CameraCamConfig.Place], phần
 * nghìn của vùng) qua [onMoved]. Chưa có mô hình (góc nhìn lạ) ⇒ vẽ nền trống, không nhận chạm.
 */
class CameraPlacementView(
    context: Context,
    /** Nhãn trong khung camera (tên camera). */
    private val markerLabel: String,
    /** Chữ mờ ở đáy ô (*"Kéo để đặt chỗ"*). */
    private val hint: String,
    private val onMoved: (CameraCamConfig.Place) -> Unit,
) : View(context) {

    private var model: CameraPlacement.Model? = null
    private var win: CameraPlacement.Window? = null
    private var dragging = false

    private val bg = Paint().apply { color = KachiTheme.c(KachiTheme.FIELD_SUNKEN) }
    private val regionFill = Paint().apply { color = KachiTheme.c(KachiTheme.PANEL) }
    private val markFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = KachiTheme.c(KachiTheme.ACCENT); alpha = MARK_ALPHA }
    private val markText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = KachiTheme.c(KachiTheme.ON_ACCENT); textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, KachiType.CAPTION, context.resources.displayMetrics)
    }
    private val hintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = KachiTheme.c(KachiTheme.MUT); textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, KachiType.CAPTION, context.resources.displayMetrics)
    }
    private val tmp = RectF()

    init {
        // Trình đọc màn hình đọc đúng câu hướng dẫn (ô không có chữ View nào khác — chỉ vẽ Canvas).
        contentDescription = hint
    }

    /** Nạp mô hình + chỗ đã lưu ([place] `null` = góc mặc định) + cỡ — gọi lúc dựng và mỗi lần cỡ/góc đổi. */
    fun bind(model: CameraPlacement.Model?, place: CameraCamConfig.Place?, sizePct: Int) {
        this.model = model
        win = model?.window(place, sizePct)
        dragging = false
        invalidate()
    }

    // ── toạ độ: display ↔ ô (letterbox giữ tỉ lệ display) ───────────────────────────────────────────────────────
    private fun scale(m: CameraPlacement.Model): Float =
        if (width == 0 || height == 0) 1f else minOf(width.toFloat() / m.displayW, height.toFloat() / m.displayH)

    private fun left(m: CameraPlacement.Model) = (width - m.displayW * scale(m)) / 2f
    private fun top(m: CameraPlacement.Model) = (height - m.displayH * scale(m)) / 2f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val m = model ?: return
        if (width == 0 || height == 0 || m.displayW <= 0 || m.displayH <= 0) return
        val s = scale(m)
        val l = left(m)
        val t = top(m)
        canvas.drawRect(l, t, l + m.displayW * s, t + m.displayH * s, bg)
        tmp.set(l + m.region.x0 * s, t + m.region.y0 * s, l + m.region.x1 * s, t + m.region.y1 * s)
        canvas.drawRect(tmp, regionFill)
        canvas.drawText(hint, l + m.displayW * s / 2f, t + m.displayH * s - KachiSpace.dpf(context, KachiSpace.S), hintText)
        val w = win ?: return
        tmp.set(l + w.x * s, t + w.y * s, l + (w.x + w.w) * s, t + (w.y + w.h) * s)
        // Không nét viền (WP1 · zero borders — `ZeroBorderContractTest`): vùng cho phép tách bằng FILL, khung camera bằng màu nhấn.
        if (m.round) {
            canvas.drawOval(tmp, markFill)
        } else {
            val r = KachiSpace.dpf(context, KachiSpace.RADIUS_S)
            canvas.drawRoundRect(tmp, r, r, markFill)
        }
        val fm = markText.fontMetrics
        canvas.drawText(markerLabel, tmp.centerX(), tmp.centerY() - (fm.ascent + fm.descent) / 2f, markText)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Khoá giữa cú kéo ⇒ huỷ luôn cú kéo (cùng luật VmBubblePlacementView: cờ phải chết cùng cổng).
        if (!isEnabled) { dragging = false; return false }
        val m = model ?: return false
        val w = win ?: return false
        val s = scale(m)
        val cx = ((event.x - left(m)) / s).toInt()
        val cy = ((event.y - top(m)) / s).toInt()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (cx !in 0..m.displayW || cy !in 0..m.displayH) return false
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                win = m.dragTo(w, cx, cy); invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                win = m.dragTo(w, cx, cy); invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                win?.let { onMoved(m.placeOf(it)) }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private companion object {
        /** Khung camera hơi trong (~82 %, cùng mức khung bóng VietMap) — chữ trên nó vẫn đọc được, vẫn thấy vùng bên dưới. */
        const val MARK_ALPHA = 210
    }
}
