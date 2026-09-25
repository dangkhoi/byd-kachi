package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View

/**
 * ẢNH XE — LAYER RIÊNG (owner 2026-09-25: "tách layer hình xe, không repaint khi số đổi").
 *
 * Trước đây [TyreBoardView]/[CarMiniView] vẽ hình xe + số trong CÙNG một `onDraw`, nên mỗi lần số đổi (dù chỉ số)
 * → `invalidate()` → re-composite CẢ hình xe ⇒ nháy. Nay hình xe là một View NỀN riêng: nó chỉ `invalidate` khi
 * **ảnh vừa nạp xong** (callback [CarImageLayer]), KHÔNG bao giờ theo nhịp số. Lớp số ([TyreBoardView] vẽ trong
 * suốt) nằm ĐÈ lên — số đổi chỉ vẽ lại lớp số, hình xe đứng yên tuyệt đối.
 *
 * Khung ảnh dùng ĐÚNG hằng bố cục của [TyreBoardView] ([carDstIn]) để lớp số bên trên tính cùng một `content`
 * rect (letterbox) và bám đúng bánh — hai lớp không lệch nhau.
 */
class CarImageView(context: Context) : View(context) {
    private val car = CarImageLayer(context) { invalidate() }
    private val carDst = RectF()

    /** Neo bố cục: cùng công thức [TyreBoardView] để hai lớp khớp. `null` = dùng mặc định cột giữa. */
    var layoutRect: ((w: Float, h: Float, out: RectF) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val rc = layoutRect
        if (rc != null) rc(w, h, carDst) else carDst.set(w * 0.30f, h * 0.02f, w * 0.70f, h * 0.88f)
        car.ensure(carDst.width().toInt(), carDst.height().toInt())
        car.draw(canvas, carDst)
    }

    /** Khung mà HÌNH XE thực sự chiếm (letterbox trong [carDst]) — lớp số dùng để bám bánh. */
    fun contentRect(w: Float, h: Float, out: RectF) {
        val rc = layoutRect
        if (rc != null) rc(w, h, carDst) else carDst.set(w * 0.30f, h * 0.02f, w * 0.70f, h * 0.88f)
        car.contentRect(carDst, out)
    }

    fun release() = car.release()

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        car.release()
    }
}
