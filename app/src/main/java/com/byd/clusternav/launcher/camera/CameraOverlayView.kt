package com.byd.clusternav.launcher.camera

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.Surface
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
    private var surface: android.view.TextureView? = null
    private var container: android.view.View? = null

    private companion object {
        const val MATCH = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = android.view.ViewGroup.LayoutParams.WRAP_CONTENT

        /** Cạnh overlay VUÔNG = 50% CHIỀU CAO màn (owner 2026-09-25: to gấp 2 so với 26% trước). */
        const val SQUARE_RATIO = 0.50f

        /** Lề trên màn CHÍNH = 14% chiều cao ⇒ nằm hẳn DƯỚI thanh trên (trước bị đè header). */
        const val MAIN_TOP_RATIO = 0.14f

        /** Lề trên CỤM = 6% chiều cao (cụm không có thanh trên; trước để cao quá thấy 1/2). */
        const val CLUSTER_TOP_RATIO = 0.06f

        /** Lề bên = 3% bề rộng. */
        const val SIDE_MARGIN_RATIO = 0.03f
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
        crop: FloatArray? = null,
        onSurfaceReady: (Surface) -> Unit = {},
    ) {
        hide()
        runCatching {
            val ctx = appCtx
            // onCluster: dựng cửa sổ trên DISPLAY CỤM (createDisplayContext) — cùng cách SpeedBadgeOverlay. Không
            // có cụm (off-car / chưa chiếu) ⇒ rơi về màn chính, không crash (overlay vẫn hiện để verify).
            val w = (if (onCluster) clusterWm(ctx) else null) ?: wmOf(ctx) ?: return
            val radius = KachiSpace.dp(ctx, KachiSpace.RADIUS_XL).toFloat()
            // TextureView (KHÔNG SurfaceView): vẽ TRONG cây view ⇒ (1) outline bo góc ăn thật, (2) frame composite
            // trong cửa sổ có nền bo — hết đen/góc vuông. AVMCamera.addPreviewSurface nhận Surface từ SurfaceTexture.
            val tv = android.view.TextureView(ctx).apply {
                isOpaque = true
                surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w2: Int, h2: Int) {
                        applyCrop(this@apply, w2, h2, crop)
                        runCatching { onSurfaceReady(Surface(st)) }.onFailure { Log.w(PanoramaHal.TAG, "onSurfaceReady: ${it.message}") }
                    }
                    override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w2: Int, h2: Int) { applyCrop(this@apply, w2, h2, crop) }
                    override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture) = true
                    override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
                }
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
                addView(tv, android.widget.FrameLayout.LayoutParams(MATCH, MATCH))
                labelFor(ctx, side)?.let { tvl ->
                    addView(tvl, android.widget.FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START))
                }
            }
            w.addView(frame, layoutParams(ctx, corner, onCluster))
            wm = w; surface = tv; container = frame
            Log.i(PanoramaHal.TAG, "overlay show corner=$corner side=$side cluster=$onCluster")
        }.onFailure { Log.w(PanoramaHal.TAG, "overlay show failed: ${it.message}") }
    }

    fun hide() {
        runCatching { container?.let { wm?.removeView(it) } }
        surface = null; container = null; wm = null
    }

    /** Surface để [PanoramaHal]/LVDS đổ video vào (nếu ROM cho). null khi chưa hiện. */
    fun surfaceView(): android.view.TextureView? = surface

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

    /**
     * Crop vùng ảnh camera cho TextureView. [crop] = (x0,y0,x1,y1) chuẩn hoá 0..1 của ẢNH NGUỒN cần hiện; `null`
     * hoặc toàn khung ⇒ không transform. Cam gương = crop vùng trái/phải của fisheye 4-in-1 (RE kinex).
     *
     * TextureView mặc định căng SurfaceTexture lấp đầy view. Để chỉ hiện vùng [x0,x1]×[y0,y1]: phóng
     * `1/(x1-x0)` × `1/(y1-y0)` quanh gốc rồi dịch để vùng crop về (0,0). `setTransform` là ma trận trên toạ độ
     * VIEW (px), nên nhân theo `vw`/`vh`.
     */
    private fun applyCrop(tv: android.view.TextureView, vw: Int, vh: Int, crop: FloatArray?) {
        if (crop == null || crop.size < 4 || vw <= 0 || vh <= 0) return
        val (x0, y0, x1, y1) = crop
        val cw = (x1 - x0).coerceAtLeast(0.001f)
        val ch = (y1 - y0).coerceAtLeast(0.001f)
        if (cw >= 0.999f && ch >= 0.999f) return   // toàn khung ⇒ khỏi transform
        val m = android.graphics.Matrix()
        val sx = 1f / cw
        val sy = 1f / ch
        m.setScale(sx, sy)
        m.postTranslate(-x0 * sx * vw, -y0 * sy * vh)
        tv.setTransform(m)
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
    private fun layoutParams(ctx: Context, corner: String, onCluster: Boolean): WindowManager.LayoutParams {
        val dm = ctx.resources.displayMetrics
        // VUÔNG: cạnh = 26% CHIỀU CAO màn (owner: cam nên hình vuông, không ngang).
        val squareSide = (dm.heightPixels * SQUARE_RATIO).toInt()
        val atLeft = corner == CameraSignalPolicy.CORNER_TOP_LEFT
        // Lề trên theo % CHIỀU CAO màn (chắc ăn qua mọi density): màn CHÍNH 14% (dưới thanh trên, trong khung —
        // trước bị đè header); CỤM 6% (không có thanh trên; trước cao quá chỉ thấy 1/2).
        val topY = (dm.heightPixels * (if (onCluster) CLUSTER_TOP_RATIO else MAIN_TOP_RATIO)).toInt()
        val sideMargin = (dm.widthPixels * SIDE_MARGIN_RATIO).toInt()
        return WindowManager.LayoutParams(
            squareSide, squareSide,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or (if (atLeft) Gravity.START else Gravity.END)
            x = sideMargin
            y = topY
        }
    }
}
