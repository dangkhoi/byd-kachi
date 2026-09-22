package com.byd.clusternav.launcher.camera

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.SurfaceView
import android.view.WindowManager
import com.byd.clusternav.launcher.camera.CameraSignalPolicy.Side

/**
 * ═══ OVERLAY CAMERA — SurfaceView nổi trái/phải màn ═════════════════════════════════════════════════════════
 *
 * Owner 2026-09-22: overlay hiện video camera bên trái (xi-nhan trái) / phải (xi-nhan phải), chọn vị trí được.
 *
 * RE `docs/diagnostics/camera-panorama-RE-2026-09-22.md`: kinex dựng một **SurfaceView** đặt tại rect + bật
 * panorama output → tín hiệu LVDS phần cứng **compose vào layer của SurfaceView đó**. Nên overlay này = một cửa
 * sổ `TYPE_APPLICATION_OVERLAY` chứa SurfaceView; [PanoramaHal] bật view tương ứng.
 *
 * ⚠ **CHỖ CHỈ XE TRẢ LỜI**: tín hiệu LVDS có đổ vào SurfaceView của untrusted_app không (runbook option A–J).
 * Off-car: cửa sổ dựng được, SurfaceView hiện (nền đen), không crash — đó là tất cả những gì đo được ở đây.
 *
 * Mọi op WindowManager trên main thread + `runCatching` (không ném). Cỡ/vị trí lấy tỉ lệ màn (nửa chiều rộng).
 */
class CameraOverlayView(private val appCtx: Context) {

    private var wm: WindowManager? = null
    private var surface: SurfaceView? = null

    /** Hiện overlay ở [side] (trái/phải). setZOrderOnTop để layer camera nổi (runbook thử cả 2 z-order). */
    fun show(side: Side, onCluster: Boolean = false) {
        hide()
        runCatching {
            val ctx = appCtx
            val w = wmOf(ctx) ?: return
            val sv = SurfaceView(ctx).apply {
                setZOrderOnTop(true)          // option G: layer nổi (thử setZOrderMediaOverlay nếu không đổ tín hiệu)
                setBackgroundColor(Color.BLACK)
                holder.setFormat(PixelFormat.OPAQUE)
            }
            w.addView(sv, layoutParams(ctx, side))
            wm = w; surface = sv
            Log.i(PanoramaHal.TAG, "overlay show side=$side cluster=$onCluster")
        }.onFailure { Log.w(PanoramaHal.TAG, "overlay show failed: ${it.message}") }
    }

    fun hide() {
        runCatching { surface?.let { wm?.removeView(it) } }
        surface = null; wm = null
    }

    /** Surface để [PanoramaHal]/LVDS đổ video vào (nếu ROM cho). null khi chưa hiện. */
    fun surfaceView(): SurfaceView? = surface

    private fun wmOf(ctx: Context): WindowManager? =
        ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    /** Nửa màn bên [side], cao ~60% màn, dính mép. Cỡ tỉ lệ ⇒ không hardcode px. */
    private fun layoutParams(ctx: Context, side: Side): WindowManager.LayoutParams {
        val dm = ctx.resources.displayMetrics
        val w = (dm.widthPixels * 0.42f).toInt()
        val h = (dm.heightPixels * 0.6f).toInt()
        return WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or (if (side == Side.LEFT) Gravity.START else Gravity.END)
        }
    }
}
