package com.byd.clusternav.launcher.camera

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import com.byd.clusternav.launcher.KachiSpace
import com.byd.clusternav.launcher.KachiBars
import com.byd.clusternav.launcher.camera.CameraSignalPolicy.Side

/**
 * ═══ OVERLAY CAMERA — cửa sổ NHỎ, BO GÓC, ở một góc TRÊN của khung launcher ══════════════════════════════════
 *
 * Owner 2026-09-25 (spec `docs/specs/camera-turn-signal-hal-socket.html` R2–R4): *"overlay video bo góc tròn
 * giống khung launcher, cỡ nhỏ ở góc TRÊN của khung chính, KHÔNG đè header bar; mỗi bên xi-nhan chọn được hiện ở
 * góc nào"*. Trước 2.35 overlay là **nửa màn** (42 % × 60 %) dính mép giữa — nó che mất chỗ làm việc và nằm sai
 * chỗ so với thứ owner mô tả.
 *
 * ## Ba con số đến từ đâu (không tự chọn)
 *  • **Bán kính** = [KachiSpace.RADIUS_XL] — cùng hằng mà `KachiTheme.surface` lấy làm mặc định cho *khung ô làm
 *    việc*. Đây chính là "bo góc giống khung launcher"; gõ một số riêng ở đây là dựng bản sao thứ hai của độ bo,
 *    và nó sẽ lệch ở đúng lần ai đó chỉnh thang bán kính.
 *  • **Lề trên** = [KachiBars.HEADER_H] — bề cao THẬT của thanh trên (42dp, viết dạng phép cộng ở chính hằng
 *    đó). Lấy hằng thay vì một số "khoảng chừng 64" ⇒ owner hạ chiều cao thanh thì overlay tự lên theo, và lời
 *    hứa *"không đè header"* không thể rữa âm thầm.
 *  • **Lề bên** = [KachiSpace.M] (12dp) — một bậc của thang khoảng cách, không phải số tự chọn.
 *
 * ## Cỡ theo TỈ LỆ màn (0.30 × 0.26)
 * Overlay phải nhỏ mà vẫn đọc được trên cả màn chính (1920×720 trên xe) lẫn màn cụm; px cứng sẽ đúng trên một
 * màn và sai trên màn kia. Tỉ lệ ⇒ một công thức cho cả hai.
 *
 * ## ⚠ [CHƯA BIẾT] — bo góc của LỚP VIDEO chỉ xe trả lời được
 * [clipToOutline] + [ViewOutlineProvider] bo **cây view**. [SurfaceView] thì có một **layer riêng** do
 * SurfaceFlinger ghép, và layer ấy nhận một khung cắt HÌNH CHỮ NHẬT — nên nếu ROM này không bo layer theo outline
 * của cha thì bốn góc video sẽ vẫn vuông trên nền đã bo. Ghi ra thay vì hứa: nếu on-car thấy góc vuông thì đổi
 * [SurfaceView] → `TextureView` (vẽ **trong** cây view ⇒ outline bo thật, và `avm.open(camId, surface)` không
 * đổi một chữ vì `TextureView` cũng cấp một [Surface]).
 * Vì lẽ đó z-order dùng [SurfaceView.setZOrderMediaOverlay] chứ KHÔNG phải `setZOrderOnTop`: "on top" đặt layer
 * lên trên **toàn bộ** cửa sổ, tức bỏ luôn cơ hội được ghép cùng nền đã bo.
 *
 * Mọi op WindowManager trên main thread + `runCatching` (không ném).
 */
class CameraOverlayView(private val appCtx: Context) {

    private var wm: WindowManager? = null
    private var surface: SurfaceView? = null
    private var container: android.view.View? = null

    private companion object {
        const val MATCH = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = android.view.ViewGroup.LayoutParams.WRAP_CONTENT

        /** Bề rộng overlay = 30 % bề rộng màn (R2 "cỡ nhỏ"). */
        const val W_RATIO = 0.30f

        /** Bề cao overlay = 26 % bề cao màn. */
        const val H_RATIO = 0.26f
    }

    /**
     * Hiện overlay ở góc [corner] (`"TL"`/`"TR"` — xem [CameraSignalPolicy.CORNER_TOP_LEFT]).
     *
     * [corner] đã được chỗ gọi tra từ pref `camera_pos_left`/`camera_pos_right` (`Prefs.cameraPos`) — lớp này
     * KHÔNG đọc prefs: nó là tầng vẽ, và một lượt đọc prefs ở đây sẽ thành cửa thứ hai vào cùng chỗ lưu.
     *
     * [side] chỉ quyết **nhãn** *Camera trái/phải*, KHÔNG quyết vị trí (đó là việc của [corner]) — hai vai tách
     * hẳn từ R4, vì xi-nhan trái được phép hiện ở góc trên-phải. `null` ⇒ không vẽ nhãn.
     */
    fun show(
        corner: String,
        side: Side? = null,
        onCluster: Boolean = false,
        onSurfaceReady: (Surface) -> Unit = {},
    ) {
        hide()
        runCatching {
            val ctx = appCtx
            // onCluster: dựng cửa sổ trên DISPLAY CỤM (createDisplayContext) — cùng cách SpeedBadgeOverlay. Không
            // có cụm (off-car / chưa chiếu) ⇒ rơi về màn chính, không crash (overlay vẫn hiện để verify).
            val w = (if (onCluster) clusterWm(ctx) else null) ?: wmOf(ctx) ?: return
            val radius = KachiSpace.dp(ctx, KachiSpace.RADIUS_XL).toFloat()
            val sv = SurfaceView(ctx).apply {
                setZOrderMediaOverlay(true)   // xem ⚠ ở KDoc lớp: "on top" bỏ mất cơ hội được bo cùng nền
                setBackgroundColor(Color.BLACK)
                holder.setFormat(PixelFormat.OPAQUE)
                roundOutline(radius)
                // Surface sẵn sàng ⇒ controller đổ camera (AVMCamera.addPreviewSurface) vào — RE kinex.
                holder.addCallback(object : android.view.SurfaceHolder.Callback {
                    override fun surfaceCreated(h: android.view.SurfaceHolder) {
                        runCatching { onSurfaceReady(h.surface) }.onFailure { Log.w(PanoramaHal.TAG, "onSurfaceReady: ${it.message}") }
                    }
                    override fun surfaceChanged(h: android.view.SurfaceHolder, f: Int, w2: Int, h2: Int) {}
                    override fun surfaceDestroyed(h: android.view.SurfaceHolder) {}
                })
            }
            // Nhãn nhỏ ở góc: off-car (chưa có video) vẫn NHÌN THẤY overlay hiện đúng bên/đúng lúc ⇒ verify wiring
            // E2E bằng mắt. Chữ ngắn ("Camera trái") nên nó không ăn chỗ khi video thật đã đổ vào.
            val frame = android.widget.FrameLayout(ctx).apply {
                // Nền BO GÓC = thứ cho bốn góc một màu đục để mép video không lởm chởm nếu layer bị cắt vuông.
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = radius
                    setColor(Color.BLACK)
                }
                roundOutline(radius)
                addView(sv, android.widget.FrameLayout.LayoutParams(MATCH, MATCH))
                labelFor(ctx, side)?.let { tv ->
                    addView(tv, android.widget.FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START))
                }
            }
            w.addView(frame, layoutParams(ctx, corner))
            wm = w; surface = sv; container = frame
            Log.i(PanoramaHal.TAG, "overlay show corner=$corner side=$side cluster=$onCluster")
        }.onFailure { Log.w(PanoramaHal.TAG, "overlay show failed: ${it.message}") }
    }

    fun hide() {
        runCatching { container?.let { wm?.removeView(it) } }
        surface = null; container = null; wm = null
    }

    /** Surface để [PanoramaHal]/LVDS đổ video vào (nếu ROM cho). null khi chưa hiện. */
    fun surfaceView(): SurfaceView? = surface

    /**
     * Nhãn ngắn *Camera trái/phải* ở góc, hoặc `null` khi chỗ gọi không nói bên nào.
     *
     * Chữ đi qua tài nguyên (`R.string.kachi_camera_left/right`) như mọi chữ của tầng `launcher/` —
     * `LauncherI18nContractTest` quét chính điều đó.
     */
    private fun labelFor(ctx: Context, side: Side?): android.widget.TextView? {
        val res = when (side) {
            Side.LEFT -> com.byd.clusternav.R.string.kachi_camera_left
            Side.RIGHT -> com.byd.clusternav.R.string.kachi_camera_right
            null -> return null
        }
        val pad = KachiSpace.dp(ctx, KachiSpace.S)
        return android.widget.TextView(ctx).apply {
            text = ctx.getString(res)
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(pad, pad, pad, pad)
        }
    }

    /** Bo góc cây view: outline tròn + [View.setClipToOutline]. Xem ⚠ ở KDoc lớp về giới hạn với lớp video. */
    private fun View.roundOutline(radius: Float) {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, radius)
            }
        }
        clipToOutline = true
    }

    private fun wmOf(ctx: Context): WindowManager? =
        ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    /** WindowManager của DISPLAY CỤM. Ưu tiên display PRESENTATION ≠ 0 (cụm DiLink3.0 = display 2, KHÔNG hardcode 1
     *  — regression X2). null nếu chưa có cụm (off-car ⇒ rơi màn chính). */
    private fun clusterWm(ctx: Context): WindowManager? = runCatching {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
            ?: return null
        val display = dm.getDisplays(android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != 0 }
            ?: dm.displays.firstOrNull { it.displayId != 0 }
            ?: return null
        ctx.createDisplayContext(display).getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    }.getOrNull()

    /**
     * Cửa sổ nhỏ ở góc TRÊN [corner], lùi xuống hết bề cao thanh trên.
     *
     * Cửa sổ của [WindowManager] không có lề (`margin`) — [WindowManager.LayoutParams.x]/`y` là **độ lệch kể từ
     * góc mà `gravity` chọn**, nên `y = HEADER_H` chính là "lề trên = bề cao header" mà R2 đòi.
     * Góc lạ (không phải `"TL"`/`"TR"`) ⇒ coi như trên-phải; lượt đọc pref đã chặn ở `Prefs.cameraPos`, đây chỉ
     * là lưới an toàn cho chỗ gọi thứ hai sau này.
     */
    private fun layoutParams(ctx: Context, corner: String): WindowManager.LayoutParams {
        val dm = ctx.resources.displayMetrics
        val w = (dm.widthPixels * W_RATIO).toInt()
        val h = (dm.heightPixels * H_RATIO).toInt()
        val atLeft = corner == CameraSignalPolicy.CORNER_TOP_LEFT
        return WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or (if (atLeft) Gravity.START else Gravity.END)
            x = KachiSpace.dp(ctx, KachiSpace.M)
            y = KachiSpace.dp(ctx, KachiBars.HEADER_H)
        }
    }
}
