package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Sơ đồ xe mini cho widget *Trạng thái xe* — **WP3-v5**: nay là **ẢNH bitmap** ([CarImageLayer]) + chấm trạng thái
 * cửa/cốp tại neo [CarLayout], thay hình vector cũ (owner bỏ vector car).
 *
 * Trạng thái vẽ được: bốn cửa + cốp (đúng dữ liệu widget này có — `CarStatus.Body`). Cửa mở ⇒ chấm ĐỎ tại vị trí
 * cửa đó; chưa đọc/đóng ⇒ không chấm (ảnh xe đã nói "xe kín"). KHÔNG viền, KHÔNG blur.
 */
class CarMiniView(context: Context) : View(context) {

    private val car = CarImageLayer(context) { invalidate() }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dst = RectF()
    private val content = RectF()

    /**
     * Cửa mở? (`null` = chưa đọc), thứ tự [CarPart.DOOR_LF]·RF·LR·RR.
     *
     * ⚠ 2026-09-25 — chấm CỐP đã gỡ cùng datum `tailgate_status` ([ĐO xe] `getHatchDoorStatus` rỗng với mọi arg:
     * cốp xe này không có cảm biến trạng thái). Giữ chấm mà không có đường đọc nào điền được thì nó vĩnh viễn im.
     */
    private var doors: List<Boolean?> = emptyList()

    fun set(doors: List<Boolean?>) {
        // (owner 2026-09-25 nháy hình xe): chỉ vẽ lại khi ĐỔI — trước invalidate mỗi nhịp dù trạng thái y hệt.
        val unchanged = this.doors == doors
        this.doors = doors
        if (!unchanged) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val pad = minOf(w, h) * PAD_RATIO
        dst.set(pad, pad, w - pad, h - pad)
        car.ensure(width, height)
        car.draw(canvas, dst)

        car.contentRect(dst, content)
        val r = minOf(content.width(), content.height()) * DOT_RATIO
        val open = Color.parseColor(KachiTheme.RED)
        // Cửa
        val parts = listOf(CarPart.DOOR_LF, CarPart.DOOR_RF, CarPart.DOOR_LR, CarPart.DOOR_RR)
        parts.forEachIndexed { i, part ->
            if (doors.getOrNull(i) == true) markAt(canvas, part, r, open)
        }
    }

    private fun markAt(canvas: Canvas, part: CarPart, r: Float, color: Int) {
        val a = CarLayout.part(part)
        dot.color = color
        canvas.drawCircle(content.left + a.x * content.width(), content.top + a.y * content.height(), r, dot)
    }

    private companion object {
        const val PAD_RATIO = 0.04f
        /** Bán kính chấm theo cạnh nhỏ của khung xe — đủ thấy mà không che thân. */
        const val DOT_RATIO = 0.07f
    }
}
