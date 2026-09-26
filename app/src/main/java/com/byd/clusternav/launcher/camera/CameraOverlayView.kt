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
 * ## VÙNG cho phép theo TỈ LỆ màn (0.50 × chiều cao), CỬA SỔ theo tỉ lệ ẢNH (CAM-ROT-2 · owner 2026-09-26)
 * Tới 2.72 cửa sổ là ô **VUÔNG** cạnh 50 % chiều cao màn, và ảnh bị **căng không đẳng hướng** cho lấp kín ô đó.
 * Owner: *"không muốn có viền đen, nên làm overlay cho nó đúng với tỷ lệ camera, không fix bừa"* ⇒ ô vuông ấy nay
 * chỉ còn là **vùng cho phép**; cửa sổ thật là hình lớn nhất **đúng tỉ lệ vùng crop sau xoay** nằm trong vùng đó
 * ([CameraOverlayFrame.fit], thuần, có test bằng số), căn GIỮA vùng. Không dải đen (cửa sổ co lại bằng ảnh, không
 * phải ảnh co lại trong cửa sổ) và không méo (ma trận trở thành phép đẳng hướng — xem KDoc `CameraOverlayFrame`).
 * Tỉ lệ đó cần **cỡ ảnh nguồn**: đo bằng `AVMCamera.getPreviewWidth/Height` sau khi mở camera rồi gọi
 * [onStreamMeasured]; trước đó dùng gợi ý `CamView.hintW/hintH`, không có gợi ý ⇒ **giữ nguyên ô vuông 2.72**.
 *
 * ## Hai đường KẾT XUẤT (CLOSE-14 · CAM-LAG) — mặc định KHÔNG đổi
 * [CameraSignalPolicy.RENDER_TEXTURE] (mặc định, đang chạy hiện trường): `TextureView` vẽ TRONG cây view ⇒ (1)
 * outline bo góc ăn thật, (2) crop + xoay bằng `setTransform`.
 * [CameraSignalPolicy.RENDER_SURFACE] (chip Cài đặt, để ĐO L2): `SurfaceView` + `setZOrderMediaOverlay`
 * — layer riêng do SurfaceFlinger ghép, rẻ hơn một lượt GPU mỗi khung, nhưng **không có
 * `setTransform`** ⇒ crop phải làm bằng cách phóng-và-kéo-lệch lớp video ([CameraOverlayFrame.stretch]) và xoay
 * thì chỉ còn đường nhờ HAL (`AVMCamera.setDisplayOrientation`, chỗ gọi báo lại qua [onStreamMeasured]).
 * ⚠ [CHƯA BIẾT] ROM này có bo góc / có cắt layer con theo biên cửa sổ hay không — chính lý do `TextureView` được
 * chọn ở 2.3x. Vì vậy `SurfaceView` là lựa chọn phụ, `setZOrderMediaOverlay` chứ KHÔNG `setZOrderOnTop` ("on top"
 * đặt layer lên trên **toàn bộ** cửa sổ, tức bỏ luôn cơ hội được ghép cùng nền đã bo).
 *
 * ## Đường KHUNG HÌNH không được có việc nặng (CLOSE-14)
 * `onSurfaceTextureUpdated` để TRỐNG: không log, không cấp phát, không shell — mỗi khung 15 fps đi qua đó. Ma trận
 * chỉ dựng ở hai callback *đổi cỡ*, không phải mỗi khung ([applyTransform]); `isOpaque = true` để `TextureView`
 * khỏi phải blend alpha của chính nó (nền bo góc nằm ở view CHA, nên cạnh bo vẫn do `clipToOutline` cắt).
 *
 * Mọi op WindowManager trên main thread + `runCatching` (không ném).
 */
class CameraOverlayView(private val appCtx: Context) {

    private var wm: WindowManager? = null
    private var video: View? = null
    private var container: View? = null
    private var live: Live? = null

    /**
     * Thứ đang hiện — đủ để **dựng lại cỡ cửa sổ** khi cỡ ảnh nguồn được đo xong ([onStreamMeasured]).
     *
     * Là state của tầng vẽ, KHÔNG phải nguồn sự thật: mọi giá trị đều do chỗ gọi truyền vào ở [show] (đọc từ prefs
     * ở controller). Lớp này vẫn không đọc prefs — xem KDoc [show].
     */
    private class Live(
        val corner: String,
        val onCluster: Boolean,
        val crop: FloatArray?,
        val rotationDeg: Int,
        val render: String,
        var streamW: Int,
        var streamH: Int,
        var rotationEffective: Boolean,
    )

    /** Vùng cho phép (px) + góc của nó so với mép màn — cửa sổ thật nằm GIỮA vùng này. */
    private class Box(val areaW: Int, val areaH: Int, val x0: Int, val y0: Int)

    private companion object {
        const val MATCH = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = android.view.ViewGroup.LayoutParams.WRAP_CONTENT

        /** Cạnh VÙNG CHO PHÉP (vuông) = 50% CHIỀU CAO màn (owner 2026-09-25: to gấp 2 so với 26% trước). */
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
     *
     * [rotationDeg] (R7, owner 2026-09-26): góc xoay NỘI DUNG video quanh tâm view, độ, dương = cùng chiều kim
     * đồng hồ (↻). Chỗ gọi đã tính từ pref + bên xi-nhan (`CameraSignalPolicy.rotationDegrees`) — lớp này chỉ nhận
     * SỐ, không biết chế độ. `0` = giữ y hành vi trước R7.
     *
     * [render] = mã đường kết xuất ([CameraSignalPolicy.RENDERS]); [streamW]×[streamH] = **gợi ý** cỡ ảnh nguồn
     * (`0` = chưa biết ⇒ cửa sổ giữ nguyên vùng vuông tới khi [onStreamMeasured] có số thật).
     */
    fun show(
        corner: String,
        side: Side? = null,
        onCluster: Boolean = false,
        crop: FloatArray? = null,
        rotationDeg: Int = 0,
        render: String = CameraSignalPolicy.RENDER_TEXTURE,
        streamW: Int = 0,
        streamH: Int = 0,
        onSurfaceReady: (Surface) -> Unit = {},
    ) {
        hide()
        runCatching {
            val ctx = appCtx
            // onCluster: dựng cửa sổ trên DISPLAY CỤM (createDisplayContext) — cùng cách SpeedBadgeOverlay. Không
            // có cụm (off-car / chưa chiếu) ⇒ rơi về màn chính, không crash (overlay vẫn hiện để verify).
            val w = (if (onCluster) clusterWm(ctx) else null) ?: wmOf(ctx) ?: return
            val radius = KachiSpace.dp(ctx, KachiSpace.RADIUS_XL).toFloat()
            val byMatrix = CameraSignalPolicy.rotatesByMatrix(render)
            val st = Live(corner, onCluster, crop, rotationDeg, render, streamW, streamH, rotationEffective = byMatrix)
            val box = box(ctx, st)
            val f = frameOf(st, box)
            val child = if (byMatrix) textureVideo(ctx, crop, rotationDeg, onSurfaceReady) else surfaceVideo(ctx, onSurfaceReady)
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
                addView(child, videoLp(st, f))
                labelFor(ctx, side)?.let { tvl ->
                    addView(tvl, android.widget.FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START))
                }
            }
            w.addView(frame, layoutParams(box, f, corner))
            wm = w; video = child; container = frame; live = st
            Log.i(
                PanoramaHal.TAG,
                "overlay show corner=$corner side=$side cluster=$onCluster rot=$rotationDeg" +
                    " kết xuất=$render khung=${f.w}x${f.h} vùng=${box.areaW}x${box.areaH} nguồn-biết=${f.streamKnown}",
            )
        }.onFailure { Log.w(PanoramaHal.TAG, "overlay show failed: ${it.message}") }
    }

    fun hide() {
        runCatching { container?.let { wm?.removeView(it) } }
        video = null; container = null; wm = null; live = null
    }

    /**
     * Cỡ ảnh nguồn THẬT vừa đo được (và xoay có thật sự được áp hay không) ⇒ **dựng lại cỡ cửa sổ** cho đúng tỉ lệ.
     *
     * Chỗ gọi (controller) đo ngay sau khi mở camera: `AVMCamera.getPreviewWidth/getPreviewHeight`; `0` = HAL không
     * trả (ROM này [CHƯA BIẾT] có trả không) ⇒ giữ gợi ý đang dùng, KHÔNG về 0. [rotationEffective] `false` khi
     * đường kết xuất không xoay được bằng ma trận và HAL cũng từ chối `setDisplayOrientation` ⇒ cửa sổ phải lấy tỉ
     * lệ **chưa xoay**, nếu không ảnh sẽ nằm trong một khung sai tỉ lệ (méo) mà không ai báo.
     *
     * Gọi được từ luồng nào cũng được: không phải main thì tự đẩy về main qua [View.post] (op WindowManager).
     */
    fun onStreamMeasured(streamW: Int, streamH: Int, rotationEffective: Boolean) {
        val st = live ?: return
        val c = container ?: return
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            c.post { onStreamMeasured(streamW, streamH, rotationEffective) }
            return
        }
        val sw = if (streamW > 0) streamW else st.streamW
        val sh = if (streamH > 0) streamH else st.streamH
        if (sw == st.streamW && sh == st.streamH && rotationEffective == st.rotationEffective) return
        st.streamW = sw; st.streamH = sh; st.rotationEffective = rotationEffective
        runCatching {
            val box = box(appCtx, st)
            val f = frameOf(st, box)
            video?.let { v -> v.layoutParams = videoLp(st, f); v.requestLayout() }
            wm?.updateViewLayout(c, layoutParams(box, f, st.corner))
            Log.i(PanoramaHal.TAG, "overlay cỡ nguồn ${sw}x$sh xoay-thật=$rotationEffective ⇒ khung ${f.w}x${f.h}")
        }.onFailure { Log.w(PanoramaHal.TAG, "overlay resize failed: ${it.message}") }
    }

    /**
     * Lớp video của đường MẶC ĐỊNH: `TextureView` (crop + xoay bằng ma trận, bo góc ăn thật).
     *
     * `AVMCamera.addPreviewSurface` nhận `Surface` dựng từ `SurfaceTexture` của nó. `onSurfaceTextureUpdated` để
     * TRỐNG — xem ⚠ ở KDoc lớp về đường khung hình.
     */
    private fun textureVideo(
        ctx: Context,
        crop: FloatArray?,
        rotationDeg: Int,
        onSurfaceReady: (Surface) -> Unit,
    ): View = android.view.TextureView(ctx).apply {
        isOpaque = true
        surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w2: Int, h2: Int) {
                applyTransform(this@apply, w2, h2, crop, rotationDeg)
                runCatching { onSurfaceReady(Surface(st)) }.onFailure { Log.w(PanoramaHal.TAG, "onSurfaceReady: ${it.message}") }
            }
            override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w2: Int, h2: Int) { applyTransform(this@apply, w2, h2, crop, rotationDeg) }
            override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
        }
    }

    /**
     * Lớp video của đường PHỤ (đo L2): `SurfaceView` layer riêng.
     *
     * Không `setTransform` ⇒ crop làm bằng cỡ + lề âm của chính view này ([videoLp] → [CameraOverlayFrame.stretch]),
     * xoay thì chỗ gọi thử nhờ HAL. `setZOrderMediaOverlay(true)`: nằm trên nền bo góc của cửa sổ mà KHÔNG nhảy lên
     * trên toàn bộ cửa sổ (khác `setZOrderOnTop`) ⇒ nhãn *Camera trái/phải* vẫn đọc được.
     */
    private fun surfaceVideo(ctx: Context, onSurfaceReady: (Surface) -> Unit): View =
        android.view.SurfaceView(ctx).apply {
            setZOrderMediaOverlay(true)
            holder.addCallback(object : android.view.SurfaceHolder.Callback {
                override fun surfaceCreated(h: android.view.SurfaceHolder) {
                    runCatching { onSurfaceReady(h.surface) }.onFailure { Log.w(PanoramaHal.TAG, "onSurfaceReady: ${it.message}") }
                }
                override fun surfaceChanged(h: android.view.SurfaceHolder, format: Int, w2: Int, h2: Int) {}
                override fun surfaceDestroyed(h: android.view.SurfaceHolder) {}
            })
        }

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
     * Đặt ma trận **crop + xoay** cho TextureView. Phép toán nằm ở `:core` [CameraOverlayTransform] (thuần, có test
     * bằng số: `CameraOverlayTransformTest`); lớp này chỉ dịch 9 số ấy sang [android.graphics.Matrix] và giao cho
     * `setTransform`.
     *
     * [crop] = `(x0,y0,x1,y1)` chuẩn hoá 0..1 của ẢNH NGUỒN cần hiện (cam gương = vùng trái/phải của fisheye 4-in-1,
     * RE kinex); [rotationDeg] = góc xoay quanh tâm view (R7), dương = ↻ cùng chiều kim đồng hồ.
     * `null` trả về từ [CameraOverlayTransform.matrix] ⇒ **không đụng** `setTransform` (y hành vi trước R7).
     *
     * `setValues` (API 1) nhận đúng bố cục row-major mà `:core` dựng — xem KDoc [CameraOverlayTransform] về quy ước,
     * và `CameraRotationWiringContractTest` ghim bốn hằng chỉ số của SDK.
     *
     * ⚠ Chỉ chạy ở hai callback ĐỔI CỠ (available / size-changed), **không** mỗi khung ⇒ `Matrix` cấp phát ở đây là
     * vài lần một lượt xi-nhan, không phải 15 lần/giây (CLOSE-14).
     */
    private fun applyTransform(tv: android.view.TextureView, vw: Int, vh: Int, crop: FloatArray?, rotationDeg: Int) {
        val values = CameraOverlayTransform.matrix(vw, vh, crop, rotationDeg) ?: return
        tv.setTransform(android.graphics.Matrix().apply { setValues(values) })
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
     * VÙNG CHO PHÉP: ô vuông cạnh [SQUARE_RATIO] × chiều cao màn, ở góc trên, lùi xuống hết bề cao thanh trên.
     *
     * Đây đúng là cửa sổ của 2.35–2.72; từ CAM-ROT-2 nó chỉ còn là **trần**: cửa sổ thật ([frameOf]) không bao giờ
     * to hơn vùng này, nên bản đổi tỉ lệ KHÔNG thể lấn thêm chỗ làm việc hay đè thanh trên.
     */
    private fun box(ctx: Context, st: Live): Box {
        val dm = ctx.resources.displayMetrics
        val side = (dm.heightPixels * SQUARE_RATIO).toInt()
        // Lề trên theo % CHIỀU CAO màn (chắc ăn qua mọi density): màn CHÍNH 14% (dưới thanh trên, trong khung —
        // trước bị đè header); CỤM 6% (không có thanh trên; trước cao quá chỉ thấy 1/2).
        val topY = (dm.heightPixels * (if (st.onCluster) CLUSTER_TOP_RATIO else MAIN_TOP_RATIO)).toInt()
        val sideMargin = (dm.widthPixels * SIDE_MARGIN_RATIO).toInt()
        return Box(side, side, sideMargin, topY)
    }

    /** Cửa sổ đúng tỉ lệ ảnh sau xoay trong vùng [box] — toán ở `:core` [CameraOverlayFrame]. */
    private fun frameOf(st: Live, box: Box) = CameraOverlayFrame.fit(
        streamW = st.streamW,
        streamH = st.streamH,
        crop = st.crop,
        rotationDeg = if (st.rotationEffective) st.rotationDeg else 0,
        areaW = box.areaW,
        areaH = box.areaH,
    )

    /**
     * Cỡ + chỗ của lớp video trong cửa sổ.
     *
     * `TextureView` lấp kín cửa sổ (crop/xoay do ma trận lo). `SurfaceView` không có ma trận ⇒ phóng lớp video lên
     * `1/crop` lần rồi kéo lệch bằng **lề âm** ([CameraOverlayFrame.stretch]) để đúng dải gương lọt vào cửa sổ.
     */
    private fun videoLp(st: Live, f: CameraOverlayFrame.Frame): android.widget.FrameLayout.LayoutParams {
        if (CameraSignalPolicy.rotatesByMatrix(st.render)) {
            return android.widget.FrameLayout.LayoutParams(MATCH, MATCH)
        }
        val s = CameraOverlayFrame.stretch(f.w, f.h, st.crop)
        return android.widget.FrameLayout.LayoutParams(s.w, s.h, Gravity.TOP or Gravity.START).apply {
            leftMargin = s.x
            topMargin = s.y
        }
    }

    /**
     * Cửa sổ cỡ [f] ở góc TRÊN [corner], **căn giữa** vùng cho phép [box].
     *
     * Cửa sổ của [WindowManager] không có lề (`margin`) — [WindowManager.LayoutParams.x]/`y` là **độ lệch kể từ
     * góc mà `gravity` chọn**, nên `y = lề trên + (vùng − khung)/2` chính là "nằm giữa vùng đã dành" mà R2 đòi; và
     * vì `x` cũng tính từ góc `gravity` nên công thức dùng chung cho cả `START` lẫn `END`.
     * Góc lạ (không phải `"TL"`/`"TR"`) ⇒ coi như trên-phải; lượt đọc pref đã chặn ở `Prefs.cameraPos`, đây chỉ
     * là lưới an toàn cho chỗ gọi thứ hai sau này.
     */
    private fun layoutParams(box: Box, f: CameraOverlayFrame.Frame, corner: String): WindowManager.LayoutParams {
        val atLeft = corner == CameraSignalPolicy.CORNER_TOP_LEFT
        return WindowManager.LayoutParams(
            f.w, f.h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or (if (atLeft) Gravity.START else Gravity.END)
            x = box.x0 + ((box.areaW - f.w) / 2).coerceAtLeast(0)
            y = box.y0 + ((box.areaH - f.h) / 2).coerceAtLeast(0)
        }
    }
}
