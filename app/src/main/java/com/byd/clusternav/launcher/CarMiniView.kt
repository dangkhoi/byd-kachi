package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Sơ đồ xe mini cho widget *Trạng thái xe* — VISUAL-REFRESH P3: nay là **cùng chiếc xe** với bộ icon và hai bảng
 * lớn ([CarArtSource] mặt TRÊN, mức tả thực (1) qua [CarArtPainter]), thay cho bản `drawRoundRect` hệ 150×250 —
 * "chiếc xe thứ ba" mà kiểm kê U7 §1 đã gọi tên và AC1.4 cấm (`CarFramesSourceContractTest`).
 *
 * Trạng thái vẽ được: bốn cửa + cốp (đúng dữ liệu widget này có — `CarStatus.Body`), tone qua [CarPartStyle]
 * (`:core`); chưa đọc được ⇒ nét DIM, không bịa đóng.
 */
class CarMiniView(context: Context) : View(context) {

    private val painter = CarArtPainter()
    private val dst = RectF()
    private val laidOut = RectF()
    private val strokeFloorPx = Sp.dpf(context, Sp.STROKE)

    /** Cửa mở? (`null` = chưa đọc), thứ tự [CarPart.DOOR_LF]·RF·LR·RR; cốp riêng. Dựng map ở [set], không trong onDraw. */
    private var open: Map<String, Boolean?> = emptyMap()

    fun set(doors: List<Boolean?>, tailgate: Boolean?) {
        val ids = listOf(CarPart.DOOR_LF, CarPart.DOOR_RF, CarPart.DOOR_LR, CarPart.DOOR_RR).map(CarFrames::pieceIdOf)
        open = ids.mapIndexed { i, id -> id to doors.getOrNull(i) }.toMap() + (CarFrames.pieceIdOf(CarPart.TAILGATE) to tailgate)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val pad = minOf(w, h) * PAD_RATIO
        val strokePx = maxOf(minOf(w, h) * OUTLINE_RATIO, strokeFloorPx)
        dst.set(pad, pad, w - pad, h - pad)
        dst.inset(strokePx / 2f, strokePx / 2f)
        if (dst != laidOut) { painter.layout(CarFace.TOP, dst, openFrame = true, strokePx = strokePx); laidOut.set(dst) }
        painter.draw(
            canvas,
            toneOf = { p -> open[p.id]?.let { if (it) GroupTone.ALERT else GroupTone.NEUTRAL } },
            availableOf = { p -> !open.containsKey(p.id) || open[p.id] != null },
            // Widget này chỉ có dữ liệu cửa + cốp ⇒ không vẽ gương/ca-pô (không bịa bộ phận không đọc).
            include = { p -> p.role != CarPartRole.MIRROR && p.role != CarPartRole.PANEL || open.containsKey(p.id) },
        )
    }

    private companion object {
        const val PAD_RATIO = 0.04f
        const val OUTLINE_RATIO = 0.018f
    }
}
