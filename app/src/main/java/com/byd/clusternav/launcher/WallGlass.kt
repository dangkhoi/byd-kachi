package com.byd.clusternav.launcher

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.byd.clusternav.R

/**
 * ═══ VISUAL-REFRESH P1b · §4.10 mục (2) — THẺ LÀ CỬA SỔ NHÌN XUỐNG ẢNH MỜ, KHÔNG PHẢI MIẾNG VÁ ══════════════════
 *
 * Lớp **đáy** (chỉ số 0) của nền một thẻ khi có ảnh nền: vẽ đúng **vùng ảnh mờ nằm dưới thẻ** (theo toạ độ của thẻ
 * trong cửa sổ), rồi một **lớp che** màu nền màn với độ đục chọn theo độ chói *đo được* của vùng đó ([GlassVeil]),
 * rồi một lớp **nhuộm rất nhẹ** màu trội của ảnh (§4.10 mục 4). Bề mặt chất liệu 80 % ([KachiTheme.surface] với
 * `overArtwork = true`) nằm lên trên — ba lớp ấy cộng lại là "kính".
 *
 * ## Không cấp phát trong [draw]
 * Mọi `Paint`/`RectF`/`Matrix` dựng một lần. [relocate] chạy khi thẻ **đổi chỗ** (layout / cuộn), không chạy mỗi
 * khung — nó chỉ đổi ma trận của shader + màu của lớp che. Trên xe nhịp trạng thái là 1 Hz, và thẻ không di chuyển.
 *
 * ## Vì sao cần biết vị trí thẻ
 * Một `Drawable` không biết view của nó ở đâu; và dưới HW-acceleration `canvas.matrix` của một view là ma trận
 * **cục bộ** (mỗi view một RenderNode), nên không suy được vị trí trong cửa sổ từ canvas. [KachiGlass] gắn listener
 * layout + cuộn để gọi [relocate] với `getLocationInWindow`.
 */
class WallWindowDrawable(
    private val art: WallArt,
    private val radius: Float,
    veil: Int,
    tint: Int?,
    private val surfaces: IntArray,
    private val inks: IntArray,
) : Drawable() {
    private val veilOpaque = ColorMath.withAlpha(veil, 255)
    private val shader = BitmapShader(art.blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val photo = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = this@WallWindowDrawable.shader }
    private val veilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorMath.withAlpha(veilOpaque, (GlassVeil.MIN * 255).toInt()) }
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint?.let { ColorMath.withAlpha(it, TINT_ALPHA) } ?: Color.TRANSPARENT }
    private val matrix = Matrix()
    private val rect = RectF()
    private val loc = IntArray(2)
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE
    private var lastW = -1
    private var lastH = -1

    /**
     * Đọc lại vị trí của [view] trong cửa sổ; chỉ tốn việc khi **vùng ảnh dưới thẻ** đổi.
     *
     * ⚠ [SOÁT P1b 2026-09-17] Cổng phải xét CẢ CỠ, không chỉ vị trí: [GlassVeil.alphaFor] đo độ chói của hình chữ
     * nhật `loc … loc + (width, height)`, nên một thẻ **đổi cỡ tại chỗ** (ô làm việc đổi bố cục · ô con của nhóm sau
     * một lượt đo lại) nhìn xuống vùng ảnh khác mà cổng cũ vẫn thoát sớm ⇒ lớp che giữ độ đục của vùng CŨ, im lặng.
     * Cùng bẫy ở lượt bind ĐẦU: `bind` gọi `relocate` trước lượt layout đầu tiên, lúc `width = 0` ⇒ đo một vùng
     * 1×1; nếu thẻ tình cờ nằm đúng chỗ đó sau layout (thẻ ở `0,0`) thì con số 1×1 ấy ở lại cả phiên.
     *
     * ⚠ [SOÁT P1b 2026-09-17] Độ đục là **biến cục bộ**, không phải một thuộc tính công khai: bản đầu phơi
     * `var veilAlpha` kèm KDoc *"để bảng đo/ảnh chụp đối chiếu"* nhưng [ĐO] **0 chỗ đọc** trong cả `app`, `core`,
     * `docs`, `scripts` ⇒ mã chết + một câu KDoc nói sai (cùng họ `LauncherRequirements.notice()` đã gỡ).
     */
    fun relocate(view: View) {
        view.getLocationInWindow(loc)
        val w = maxOf(1, view.width)
        val h = maxOf(1, view.height)
        if (loc[0] == lastX && loc[1] == lastY && w == lastW && h == lastH) return
        lastX = loc[0]; lastY = loc[1]; lastW = w; lastH = h
        matrix.setScale(art.scale.toFloat(), art.scale.toFloat())
        matrix.postTranslate(-loc[0].toFloat(), -loc[1].toFloat())
        shader.setLocalMatrix(matrix)
        val lum = art.luminanceOf(loc[0], loc[1], loc[0] + w, loc[1] + h)
        val alpha = GlassVeil.alphaFor(lum, veilOpaque, surfaces, inks)
        veilPaint.color = ColorMath.withAlpha(veilOpaque, (alpha * 255).toInt())
        invalidateSelf()
    }

    /**
     * Ba lớp của "kính": vùng ảnh mờ → lớp che → nhuộm màu trội.
     *
     * ⚠ [SOÁT P1b 2026-09-17] Bỏ lớp ảnh khi bitmap **đã bị thu hồi**: `KachiGlass.refresh` chỉ đi cây view dưới
     * `rootFrame`, nên một thẻ kính nằm ngoài cây đó (cửa sổ overlay dựng sau này) sẽ giữ `BitmapShader` trỏ vào ảnh
     * mờ vừa bị `WallpaperController` nhả ⇒ `drawRoundRect` **ném** và launcher mất màn chính. Đây là đúng lá chắn
     * `WallView.onDraw` đã có cho ảnh gốc (`!p.isRecycled`); thiếu nó thì hai đường ảnh cùng một dự án lại khác luật.
     * Hỏng thì thẻ rơi về lớp che + bề mặt — nhìn như thẻ thường, không sập.
     */
    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        if (!art.blurred.isRecycled) canvas.drawRoundRect(rect, radius, radius, photo)
        canvas.drawRoundRect(rect, radius, radius, veilPaint)
        if (tintPaint.color != Color.TRANSPARENT) canvas.drawRoundRect(rect, radius, radius, tintPaint)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        /** 6 % màu trội — đủ để "ăn theo ảnh", không đủ để đụng sàn mực (bảng đo P1b). */
        const val TINT_ALPHA = 0x0f
    }
}

/** Tham số dựng nền của một view kính — giữ trong tag để [KachiGlass.refresh] dựng lại đúng thứ đó khi ảnh đổi. */
internal class GlassSpec(val radius: Int, val tone: SurfaceTone, val domain: Domain?)

/**
 * Cửa DUY NHẤT gắn nền **kính** cho một view (thẻ nội dung, khay ô làm việc, ô con của nhóm).
 *
 * ## Hợp đồng
 *  • **Không có ảnh nền** ([WallArtStore.current] = `null`) ⇒ `view.background = KachiTheme.surface(...)` — đúng
 *    từng byte đường cũ. Người không dùng hình nền không thấy gì khác (bài `WallGlassContractTest`).
 *  • Có ảnh ⇒ `LayerDrawable(cửa sổ, bề mặt 80 %)`; view được ghi tag để [refresh] dựng lại khi ảnh đổi/tắt mà
 *    **không dựng lại màn** (đổi ảnh trình chiếu mỗi 15 s không được kéo theo một lượt `recreate`).
 *  • Ô LÕM không bao giờ là kính (lõm ≠ cửa sổ).
 */
object KachiGlass {

    fun apply(view: View, radius: Int = KachiSpace.RADIUS_XL, tone: SurfaceTone = SurfaceTone.NEUTRAL, domain: Domain? = null) {
        val spec = GlassSpec(radius, tone, domain)
        view.setTag(R.id.kachi_glass_spec, spec)
        paint(view, spec)
    }

    /**
     * Nền **KHÔNG-kính** cho một view có thể đã TỪNG là kính (ô con nhóm đổi sắc thái: NEUTRAL → WARN/ALERT, nơi
     * *màu nền chính là thông tin*).
     *
     * ⚠ [SOÁT P1b] Đặt thẳng `view.background = …` là chưa đủ: thẻ `kachi_glass_spec` của lượt kính trước **ở lại**
     * trên view, nên lượt [refresh] kế tiếp (trình chiếu đổi ảnh mỗi 15 s) sẽ đắp kính đè lên đúng cái nền đang mang
     * cảnh báo — lỗi im lặng, và là đúng bệnh mà KDoc `WorkspaceView.makeSlot` đã ghi cho ô TRỐNG. Gỡ thẻ + listener
     * trước rồi mới đặt nền.
     */
    fun plain(view: View, background: Drawable) {
        view.setTag(R.id.kachi_glass_spec, null)
        unbind(view)
        view.background = background
    }

    /** Dựng lại nền của mọi view kính dưới [root] theo ảnh hiện tại — gọi khi ảnh nền đổi / bật / tắt. */
    fun refresh(root: View) {
        (root.getTag(R.id.kachi_glass_spec) as? GlassSpec)?.let { paint(root, it) }
        if (root is ViewGroup) for (i in 0 until root.childCount) refresh(root.getChildAt(i))
    }

    private fun paint(view: View, spec: GlassSpec) {
        val art = WallArtStore.current
        val ctx = view.context
        if (art == null || spec.tone == SurfaceTone.SUNKEN) {
            unbind(view)
            view.background = KachiTheme.surface(ctx, spec.radius, spec.tone, spec.domain)
            return
        }
        val window = WallWindowDrawable(
            art = art,
            radius = KachiSpace.dpf(ctx, spec.radius),
            veil = KachiTheme.c(KachiTheme.BG),
            tint = art.dominant.firstOrNull(),
            surfaces = KachiTheme.surfacePair(spec.tone, overArtwork = true),
            inks = veilInks(spec.tone),
        )
        val top = KachiTheme.surface(ctx, spec.radius, spec.tone, spec.domain, overArtwork = true)
        view.background = LayerDrawable(arrayOf(window, top))
        bind(view, window)
    }

    /**
     * Mực mà lớp che phải giữ đọc được trên từng tone — **cùng hợp đồng** với bảng đo P1: thẻ thường/khay đỡ cả ba
     * mực (`ThemePaletteContractTest.textOn`), còn thẻ BẬT chỉ có hợp đồng với `ink`
     * (`SurfaceContrastContractTest.chu tren the dang bat…`) — [ĐO] đòi `mut` trên thẻ BẬT thì ngay bảng gốc (không
     * ảnh) đã không đạt, nên đòi nó ở lớp kính là đòi một điều bảng màu chưa bao giờ hứa.
     */
    internal fun veilInks(tone: SurfaceTone): IntArray = when (tone) {
        SurfaceTone.ACTIVE -> intArrayOf(KachiTheme.c(KachiTheme.INK))
        else -> intArrayOf(KachiTheme.c(KachiTheme.MUT), KachiTheme.c(KachiTheme.MUT2), KachiTheme.c(KachiTheme.INK))
    }

    /** Listener layout + cuộn — vị trí trong cửa sổ đổi thì cửa sổ nhìn xuống vùng khác của ảnh. */
    private class Binding(val view: View, val window: WallWindowDrawable) :
        View.OnLayoutChangeListener, View.OnAttachStateChangeListener, ViewTreeObserver.OnScrollChangedListener {
        override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) = window.relocate(view)
        override fun onScrollChanged() = window.relocate(view)
        override fun onViewAttachedToWindow(v: View) { v.viewTreeObserver.addOnScrollChangedListener(this); window.relocate(view) }
        override fun onViewDetachedFromWindow(v: View) { v.viewTreeObserver.removeOnScrollChangedListener(this) }
    }

    private fun bind(view: View, window: WallWindowDrawable) {
        unbind(view)
        val b = Binding(view, window)
        view.setTag(R.id.kachi_glass_binding, b)
        view.addOnLayoutChangeListener(b)
        view.addOnAttachStateChangeListener(b)
        if (view.isAttachedToWindow) { view.viewTreeObserver.addOnScrollChangedListener(b); window.relocate(view) }
    }

    private fun unbind(view: View) {
        val b = view.getTag(R.id.kachi_glass_binding) as? Binding ?: return
        view.removeOnLayoutChangeListener(b)
        view.removeOnAttachStateChangeListener(b)
        if (view.isAttachedToWindow) view.viewTreeObserver.removeOnScrollChangedListener(b)
        view.setTag(R.id.kachi_glass_binding, null)
    }
}
