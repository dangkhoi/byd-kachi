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
 * ## BA đường KẾT XUẤT (CLOSE-14 · CAM-LAG · R8-B) — mặc định KHÔNG đổi
 * Lớp này chỉ **chọn** đường rồi giao cho [CameraVideoLayer] dựng (tệp riêng từ 2.74: *"cửa sổ"* và *"cái gì vẽ
 * khung"* là hai vai, và trần 500 dòng của CLAUDE.md §4.1 buộc tách đúng đường khớp ấy). Bảng ba đường + cái mất của
 * từng đường nằm ở KDoc [CameraVideoLayer]; ở đây chỉ hai điều cửa sổ phải biết:
 *  • **`SV` không có `setTransform`** ⇒ cắt vùng bằng cỡ + lề âm của lớp video ([CameraOverlayFrame.stretch], gọi từ
 *    [videoLp]) và xoay thì chỉ còn đường nhờ HAL ⇒ tỉ lệ cửa sổ phải chờ [onStreamMeasured] trả lời *"có xoay thật
 *    không"*. ⚠ [CHƯA BIẾT] ROM này có bo góc / có cắt layer con theo biên cửa sổ hay không — chính lý do
 *    `TextureView` được chọn ở 2.3x.
 *  • **`GL` xoay trong shader** ⇒ tỉ lệ cửa sổ lấy *đã xoay* ngay (không chờ ai), và `setTransform` **không** được
 *    gọi (⚠ KDoc [CameraVideoLayer]). Cửa sổ, bo góc, hình TRÒN, nhãn: **không đổi một dòng nào** so với 2.73 —
 *    outline oval nằm trên view CHA nên nó cắt cả `TextureView` của đường GL y như của đường `TV`.
 *
 * ## Đường KHUNG HÌNH không được có việc nặng (CLOSE-14)
 * Lời hứa ấy nay do [CameraVideoLayer] giữ (`onSurfaceTextureUpdated`/`surfaceChanged` để TRỐNG, `isOpaque = true`,
 * ma trận chỉ dựng ở hai callback *đổi cỡ*). Lớp này **không có** một hàm nào chạy mỗi khung.
 *
 * Mọi op WindowManager trên main thread + `runCatching` (không ném).
 */
class CameraOverlayView(private val appCtx: Context) {

    private var wm: WindowManager? = null

    /** Lớp video đang treo (vai *"cái gì vẽ khung"*) — xem [CameraVideoLayer]. */
    private var layer: CameraVideoLayer? = null
    private var container: View? = null
    private var live: Live? = null

    /** View của lớp video — `null` khi không hiện gì. Ba phép hỏi chỉ-đọc dưới đây đo trên chính nó. */
    private val video: View? get() = layer?.view

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
        val shape: String,
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
        shape: String = CameraSignalPolicy.SHAPE_RECT,
        streamW: Int = 0,
        streamH: Int = 0,
        gl: CameraGlUniforms? = null,
        synthOn: Boolean = false,
        synthFile: String = "",
        onSurfaceReady: (Surface) -> Unit = {},
    ) {
        hide()
        runCatching {
            val ctx = appCtx
            // onCluster: dựng cửa sổ trên DISPLAY CỤM (createDisplayContext) — cùng cách SpeedBadgeOverlay. Không
            // có cụm (off-car / chưa chiếu) ⇒ rơi về màn chính, không crash (overlay vẫn hiện để verify).
            val w = (if (onCluster) clusterWm(ctx) else null) ?: wmOf(ctx) ?: return
            val radius = KachiSpace.dp(ctx, KachiSpace.RADIUS_XL).toFloat()
            // Xoay: ma trận (`TV`) hay shader (`GL`)? Cả hai đều "có người làm" ⇒ cửa sổ lấy tỉ lệ ĐÃ xoay.
            // `SV` thì chưa biết (chờ HAL trả lời) — controller báo lại qua [onStreamMeasured].
            val rotDone = CameraSignalPolicy.rotatesByMatrix(render) || CameraSignalPolicy.rotatesInShader(render)
            // HÌNH KHUNG (R8-A, owner 2026-09-26): tròn = `setOval` trên ĐÚNG cái [ViewOutlineProvider] mà 2.73 đang
            // dùng để bo góc — không thêm một cơ chế cắt thứ hai. Mã lạ ⇒ chủ nhật (mặc định 2.73).
            val round = shape == CameraSignalPolicy.SHAPE_ROUND
            val st = Live(corner, onCluster, crop, rotationDeg, render, shape, streamW, streamH, rotationEffective = rotDone)
            val box = box(ctx, st)
            val f = frameOf(st, box)
            val vl = CameraVideoLayer.create(
                ctx = ctx, render = render, crop = crop, rotationDeg = rotationDeg,
                gl = gl, streamW = streamW, streamH = streamH, synthOn = synthOn, synthFile = synthFile,
                onSurfaceReady = onSurfaceReady,
            )
            val child = vl.view
            // Nhãn nhỏ ở góc: off-car (chưa có video) vẫn NHÌN THẤY overlay hiện đúng bên/đúng lúc ⇒ verify wiring
            // E2E bằng mắt. Chữ ngắn ("Camera trái") nên nó không ăn chỗ khi video thật đã đổ vào.
            val frame = android.widget.FrameLayout(ctx).apply {
                // Nền BO GÓC = thứ cho bốn góc một màu đục để mép video không lởm chởm nếu layer bị cắt vuông.
                background = GradientDrawable().apply {
                    this.shape = if (round) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
                    cornerRadius = radius
                    setColor(Color.BLACK)
                }
                roundOutline(radius, round)
                addView(child, videoLp(st, f))
                labelFor(ctx, side)?.let { tvl ->
                    addView(tvl, android.widget.FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START))
                }
            }
            w.addView(frame, layoutParams(box, f, corner))
            wm = w; layer = vl; container = frame; live = st
            // RE §7 Q13/Q17: hai con số GPU đi thẳng vào dòng này — buổi xe chỉ cần một ảnh `logcat`, không phải một
            // lệnh riêng (CLAUDE.md §11). Lượt GL ĐẦU TIÊN của phiên in `chưa đo` (ngữ cảnh dựng sau dòng này); lượt
            // sau có số thật. Nói "chưa đo" thay vì in `0` là đúng luật §2 — chưa biết ≠ GPU trả 0.
            Log.i(
                PanoramaHal.TAG,
                "overlay show corner=$corner side=$side cluster=$onCluster rot=$rotationDeg hình=$shape" +
                    " kết xuất=$render khung=${f.w}x${f.h} vùng=${box.areaW}x${box.areaH} nguồn-biết=${f.streamKnown}" +
                    " gl=${CameraGlInfo.summary()}" + (if (gl != null) " nắn=${gl.describe()}" else ""),
            )
        }.onFailure { Log.w(PanoramaHal.TAG, "overlay show failed: ${it.message}") }
    }

    /**
     * Dỡ overlay. **Lớp video trước, cửa sổ sau** — và thứ tự đó không đổi được ở đường GL: [CameraVideoLayer.release]
     * chờ luồng vẽ dọn xong `EGLSurface` đang trỏ vào `SurfaceTexture` của `TextureView`, còn `removeView` là thứ làm
     * nền tảng **huỷ** chính `SurfaceTexture` ấy. Gỡ view trước là mở đúng cửa sổ đua đó.
     *
     * (Đường `TV`/`SV` không có gì để dỡ ⇒ thứ tự này là no-op với chúng, tức đường đang chạy hiện trường không đổi
     * một hành vi nào — CLAUDE.md §6.)
     */
    fun hide() {
        runCatching { layer?.release() }
        runCatching { container?.let { wm?.removeView(it) } }
        layer = null; container = null; wm = null; live = null
    }

    /** `frames=N busySkip=M` của luồng vẽ GL, rỗng ở hai đường kia. Chỉ ĐỌC — cho lời đáp `camera_frame`. */
    fun glStats(): String = layer?.glStats().orEmpty()

    /** Cỡ ảnh tổng hợp đang bơm (`camera_synth`), rỗng khi không bật. Chỉ ĐỌC. */
    fun synthSize(): String = layer?.synthSize().orEmpty()

    /**
     * Chụp một khung **THÔ** (chưa nắn, nguyên khung) qua FBO — chỉ đường GL làm được.
     *
     * Vì sao cần, khi đã có [captureFrame]: trên đường GL thứ `getBitmap` trả về là khung **ĐÃ NẮN** (cửa ra là chính
     * `SurfaceTexture` của `TextureView`, và shader đã ghi vào đó) ⇒ không dùng được để **đo** bán kính/tâm vòng ảnh,
     * tức không dùng được để chốt tham số nắn. Xem KDoc [CameraGlRenderer.grabRaw].
     */
    fun grabRawFrame(w: Int, h: Int): IntArray? = layer?.grabRaw(w, h)

    /** Cửa sổ overlay đang treo trên [WindowManager] hay không — chỉ ĐỌC, cho cầu kiểm thử. */
    fun showing(): Boolean = container != null

    /**
     * Lớp video đang hiện có **chụp lại được** hay không — tức có phải `TextureView` không.
     *
     * ĐO chứ không tra pref (CLAUDE.md §7): pref `camera_render` nói *sẽ* dựng đường nào, còn cái đang treo trên
     * màn là thứ đã dựng ở lượt xi-nhan trước. Hai thứ đó khác nhau đúng trong khoảng giữa hai lượt (owner đổi chip
     * trong Cài đặt), và đúng khoảng đó là lúc lệnh chẩn đoán bị gọi.
     *
     * `false` (đường [CameraSignalPolicy.RENDER_SURFACE]) ⇒ [captureFrame] chắc chắn trả `null`: `SurfaceView` là
     * một layer riêng do SurfaceFlinger ghép, **không có** `getBitmap` nào tương đương — xem KDoc [captureFrame].
     */
    fun capturable(): Boolean = video is android.view.TextureView

    /** Mã đường kết xuất ĐANG treo ([CameraSignalPolicy.RENDERS]), rỗng khi không hiện gì. Chỉ ĐỌC. */
    fun renderPath(): String = live?.render ?: ""

    /**
     * [android.view.TextureView.isAvailable] của lớp video: `mSurface != null`, tức ĐÃ có `SurfaceTexture`
     * ([ĐO] AOSP `android-10.0.0_r47` `frameworks/base/core/java/android/view/TextureView.java:624-626`).
     * `false` ⇒ [captureFrame] chắc chắn trả `null`, và đó là câu trả lời đúng cho *"chưa có khung nào cả"*.
     *
     * Đường `SurfaceView` không có cờ này ⇒ luôn `false` (xem [capturable] để phân biệt hai lý do).
     */
    fun available(): Boolean = (video as? android.view.TextureView)?.isAvailable == true

    /**
     * Chụp MỘT khung của luồng video ra [android.graphics.Bitmap] cỡ [w] × [h] — **KHÔNG mang theo ma trận
     * crop+xoay** mà `CameraVideoLayer.applyTransform` đã đặt lên view (đường `TV`). Trên đường `GL` thì ngược lại:
     * shader ghi thẳng vào cửa ra nên ảnh chụp về là khung **ĐÃ NẮN** — xem [grabRawFrame] cho khung thô.
     *
     * ## Chỉ đường `TextureView` chụp được
     * Đường phụ [CameraSignalPolicy.RENDER_SURFACE] dựng một `SurfaceView`: layer của nó do SurfaceFlinger ghép
     * **ngoài** cây view, nên `TextureView.getBitmap` KHÔNG tồn tại ở đó và không có hàm nào thay thế trong tiến
     * trình app. ⇒ trả `null`, và chỗ gọi nói THẲNG lý do đó ra lời đáp ([capturable]) chứ không để người đang
     * ngồi trong xe đọc `capture_failed` rồi đi mò xi-nhan.
     *
     * ## Vì sao ảnh ra là khung GỐC, không phải ô vuông đang thấy trên màn — [ĐO] AOSP `android-10.0.0_r47`
     *  1. `TextureView.getBitmap(int,int)` (`core/java/android/view/TextureView.java:574-581`) dựng một bitmap
     *     `ARGB_8888` đúng `w × h` rồi gọi `getBitmap(Bitmap)`; `w`/`h` ≤ 0 hoặc `!isAvailable()` ⇒ trả `null`.
     *  2. `getBitmap(Bitmap)` (`TextureView.java:605-627`) gọi `mLayer.copyInto(bitmap)`.
     *  3. `Readback::copyLayerInto(DeferredLayerUpdater*, SkBitmap*)` (`libs/hwui/Readback.cpp:86-105`) đặt
     *     `dstRect` = **toàn bộ** bitmap (`Readback.cpp:95`) và `srcRect = nullptr` (`Readback.cpp:99`).
     *  4. `Readback::copyLayerInto(Layer*, …)` (`Readback.cpp:159`) gọi
     *     `LayerDrawable::DrawLayer(…, srcRect, dstRect, false)` — **`useLayerTransform = false`**
     *     (`Readback.cpp:184-186`).
     *  5. `LayerDrawable::DrawLayer` (`libs/hwui/pipeline/skia/LayerDrawable.cpp:53-85`): `useLayerTransform`
     *     `false` ⇒ `matrix = textureMatrix` **thay vì** `Concat(layerTransform, textureMatrix)`. `layerTransform`
     *     chính là thứ `TextureView.setTransform` ghi vào layer (`TextureView.java:493` + `:522`) ⇒ crop+xoay của
     *     [CameraOverlayTransform] **không** đi vào ảnh. `srcRect` rỗng ⇒ `MakeIWH(layerWidth, layerHeight)` rồi
     *     `matrixInv.mapRect` ⇒ vùng nguồn = **trọn ảnh gốc** (`LayerDrawable.cpp:104-117`), căng vào `dstRect`.
     *
     * ⇒ `w × h` = cỡ luồng thật (fisheye 4-in-1) thì ảnh ra là khung gốc 1:1; khác cỡ thì vẫn là khung gốc, chỉ bị
     * co/giãn đều. [SUY] bước JNI `TextureLayer.copyInto` → `RenderProxy::copyLayerInto` không mở source ra đọc
     * (chỉ có một chỗ nhận `DeferredLayerUpdater*` ở Readback nên đường đi là duy nhất).
     *
     * ⚠ PHẢI gọi trên main thread (`getBitmap` đồng bộ hoá với render thread; KDoc AOSP cấm gọi trong `onDraw`).
     * Hỏng (`OutOfMemoryError`, `IllegalStateException` khi không lấy được ngữ cảnh render) ⇒ `null`, không ném:
     * một lượt chụp hụt không được phép giết launcher đang lăn bánh.
     */
    fun captureFrame(w: Int, h: Int): android.graphics.Bitmap? {
        val tv = video as? android.view.TextureView ?: return null
        if (!tv.isAvailable || w < 1 || h < 1) return null
        return runCatching { tv.getBitmap(w, h) }
            .onFailure { Log.w(PanoramaHal.TAG, "captureFrame ${w}x$h failed: ${it.javaClass.simpleName}") }
            .getOrNull()
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
     * Bo cây view: [oval] = hình TRÒN/ELIP (`Outline.setOval`), ngược lại = chữ nhật bo góc bán kính [radius] — **cùng
     * một** [ViewOutlineProvider] + [View.setClipToOutline] của 2.73, không thêm cơ chế cắt nào khác.
     *
     * ⚠ Giới hạn đã biết (nguyên văn cảnh báo bo góc ở KDoc lớp): outline cắt được **cây view HWUI**, nên đường
     * `TextureView` ăn; lớp `SurfaceView` là layer riêng do SurfaceFlinger ghép nên [ĐOÁN] không ăn — nhãn chip nói
     * thẳng điều đó thay vì im lặng vẽ một hình vuông khi owner chọn "Tròn".
     */
    private fun View.roundOutline(radius: Float, oval: Boolean = false) {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                if (oval) outline.setOval(0, 0, v.width, v.height) else outline.setRoundRect(0, 0, v.width, v.height, radius)
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
        // `usesTextureView`, KHÔNG `rotatesByMatrix`: đường `GL` cũng là `TextureView` lấp kín cửa sổ (shader cắt
        // vùng), nhưng nó KHÔNG xoay bằng ma trận. Dùng lẫn hai phép hỏi ở đây là đẩy đường GL vào nhánh phóng-kéo-lệch
        // của `SurfaceView` ⇒ cắt HAI lần (một lần shader, một lần lề âm) và khung ra là một mảnh vụn của dải.
        if (CameraSignalPolicy.usesTextureView(st.render)) {
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
