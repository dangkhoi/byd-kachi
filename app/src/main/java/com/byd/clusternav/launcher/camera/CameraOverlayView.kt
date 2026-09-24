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
    private var container: android.view.View? = null

    private companion object {
        const val MATCH = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
    }

    /** Hiện overlay ở [side]. [option] (runbook): "B"=setZOrderMediaOverlay thay setZOrderOnTop. */
    fun show(side: Side, onCluster: Boolean = false, option: String = "A") {
        hide()
        runCatching {
            val ctx = appCtx
            val w = wmOf(ctx) ?: return
            val sv = SurfaceView(ctx).apply {
                if (option.contains("B")) setZOrderMediaOverlay(true) else setZOrderOnTop(true)   // B: media overlay
                setBackgroundColor(Color.BLACK)
                holder.setFormat(PixelFormat.OPAQUE)
            }
            // Nhãn nổi trên SurfaceView: off-car (chưa có tín hiệu LVDS) vẫn NHÌN THẤY overlay hiện đúng bên/đúng
            // lúc ⇒ verify wiring E2E bằng mắt. Khi tín hiệu video thật đổ vào SurfaceView, nhãn nằm trên góc.
            val frame = android.widget.FrameLayout(ctx).apply {
                addView(sv, android.widget.FrameLayout.LayoutParams(MATCH, MATCH))
                addView(android.widget.TextView(ctx).apply {
                    text = ctx.getString(
                        if (side == Side.LEFT) com.byd.clusternav.R.string.kachi_camera_left
                        else com.byd.clusternav.R.string.kachi_camera_right,
                    )
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    setPadding(24, 16, 24, 16)
                }, android.widget.FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            }
            w.addView(frame, layoutParams(ctx, side))
            wm = w; surface = sv; container = frame
            Log.i(PanoramaHal.TAG, "overlay show side=$side cluster=$onCluster opt=$option")
        }.onFailure { Log.w(PanoramaHal.TAG, "overlay show failed: ${it.message}") }
    }

    fun hide() {
        runCatching { container?.let { wm?.removeView(it) } }
        surface = null; container = null; wm = null
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
