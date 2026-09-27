package com.byd.clusternav.launcher.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.widget.FrameLayout
import com.byd.clusternav.launcher.KachiSpace
import com.byd.clusternav.launcher.camera.CameraSignalPolicy.Side

/**
 * ═══ MẶT NẠ CỬA SỔ CAMERA — mép NGOÀI ôm kính cụm, mép TRONG mờ dần (2.77 + 2.78) ════════════════════════════════
 *
 * Owner 27/09 chiều, nhìn camera chiếu trên cụm: *"này nhìn OK, nhưng shape nó không theo cạnh trái cong của cụm"*;
 * 27/09 tối, sau khi mép trái đã ôm kính: *"phần cạnh bên phải thêm tý blur ra ngoài cho nó smooth, ko là 1 vạch
 * thẳng nhìn nó như sẹo, ngược lại cho bên phải cũng thế"*. Hai mép vì thế **khác hẳn nhau về cách kết thúc**: mép
 * NGOÀI cắt cứng theo kính (ngoài nó là viền đục, không ai thấy đường cắt), mép TRONG chuyển alpha dần về 0
 * ([glassFade], bề rộng do `:core` [CameraClusterBand.fadePx] quyết). Số đo đường cong + lý do nó chỉ đo được từ ảnh
 * chụp: KDoc [CameraClusterBand] và `docs/diagnostics/offcar-2026-09-27/camera-cluster-band.md` §11–§12.
 *
 * ## Vì sao KHÔNG dùng [android.view.ViewOutlineProvider] như bo góc 2.73
 * [ĐO source] AOSP `android-10.0.0_r47` `graphics/java/android/graphics/Outline.java`: `setConvexPath()` đặt
 * `mMode = MODE_CONVEX_PATH`, còn `canClip()` là đúng một dòng `return mMode != MODE_CONVEX_PATH;` ⇒ một outline
 * mang đường bất kỳ **không cắt được**, nó chỉ còn dùng để đổ bóng ([SUY] cho chặng HWUI: `View.setClipToOutline`
 * chỉ đẩy cờ xuống `RenderNode`, phép cắt thật nằm ở đó). Đường cong này không phải round-rect ⇒ outline không làm
 * được việc, phải cắt bằng [Canvas.clipPath] ngay trong [CameraGlassFrame.draw].
 *
 * ## Vì sao cắt ở view CHA, không ở lớp video
 * Hệt lý do của bo góc 2.73: `TextureView` (cả đường `TV` lẫn `GL`) được HWUI vẽ **trong** cây view nên clip của
 * cha ăn vào nó. `SurfaceView` là layer riêng do SurfaceFlinger ghép ⇒ [ĐOÁN] không ăn — y như cảnh báo đã ghi ở
 * `CameraOverlayView.roundOutline`, không phải một giới hạn mới.
 *
 * ## Hỏng thì hỏng về đâu
 * [glassMask] trả `null` khi hồ sơ cụm chưa đo đường cong ⇒ [CameraGlassFrame] vẽ y hệt một `FrameLayout` thường
 * ⇒ cửa sổ 2.76. Không có nhánh nào ném.
 */
internal class CameraGlassFrame(
    ctx: Context,
    private val mask: Path?,
    private val fade: GlassFade? = null,
) : FrameLayout(ctx) {

    /** `DST_IN` = giữ nguyên màu, chỉ nhân alpha ⇒ dải mờ không làm đổi một điểm màu nào của video. */
    private val fadePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private var shaderW = 0

    override fun draw(canvas: Canvas) {
        val m = mask
        val f = fade
        if (m == null && f == null) {
            super.draw(canvas)
            return
        }
        // Dải mờ cần một LỚP riêng: `DST_IN` phải ăn vào chính điểm ảnh của cửa sổ, không phải vào nền cụm phía sau.
        // Mặt nạ cắt cứng thì không cần lớp, nhưng dùng chung một đường cho cả hai để chỉ có MỘT thứ tự vẽ.
        val save = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        if (m != null) canvas.clipPath(m)
        super.draw(canvas)
        if (f != null && width > 0 && height > 0) {
            val px = f.px.coerceIn(1, width)
            if (shaderW != width) {
                val from = if (f.atRight) (width - px).toFloat() else px.toFloat()
                val to = if (f.atRight) width.toFloat() else 0f
                fadePaint.shader = LinearGradient(from, 0f, to, 0f, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                shaderW = width
            }
            val x0 = if (f.atRight) (width - px).toFloat() else 0f
            canvas.drawRect(x0, 0f, x0 + px, height.toFloat(), fadePaint)
        }
        canvas.restoreToCount(save)
    }
}

/** Dải mờ ở mép TRONG: rộng [px], nằm ở mép PHẢI của cửa sổ khi [atRight] (tức cửa sổ đứng đầu TRÁI dải). */
internal data class GlassFade(val px: Int, val atRight: Boolean)

/**
 * Dải mờ của cửa sổ [p], hoặc `null` khi không có gì để mờ. Mép mờ là mép TRONG: cửa sổ đứng đầu TRÁI ⇒ mờ ở bên
 * PHẢI, và ngược lại.
 *
 * ⚠ Từ 2.79 [p] khác `null` cho **cả ba** hình trên cụm (chữ nhật/tròn cũng đi qua `CameraClusterBand.place`), nên
 * phép lọc *"chỉ hình theo cụm mới mờ"* nằm ở [CameraClusterBand.fadePx] — nó trả `0` cho hai hình kia. `null` ở
 * đây giờ chỉ còn nghĩa: màn chính, hoặc cửa sổ hẹp tới mức mờ sẽ ăn mất ảnh.
 */
internal fun glassFade(p: CameraClusterBand.Placement?): GlassFade? {
    val px = if (p == null) 0 else CameraClusterBand.fadePx(p)
    return if (p != null && px > 0) GlassFade(px, atRight = p.atLeft) else null
}

/**
 * Mặt nạ của cửa sổ [p] ở **toạ độ cục bộ của cửa sổ**: hình chữ nhật bo góc [radius] của 2.73 **giao** với nửa mặt
 * phẳng nằm phía TRONG của mỗi đường cong đã đo ([CameraClusterBand.leftEdgeAt] / [CameraClusterBand.rightEdgeAt]).
 *
 * Giao (`Path.Op.INTERSECT`) thay vì tự vẽ một đường viền mới: bo góc vẫn là **đúng** bán kính của hồ sơ, hai góc
 * bên trong không đổi một px, và nếu bảng cong rỗng thì chỗ gọi đã trả `null` từ trước ⇒ không có bản sao thứ hai
 * của hình dạng cửa sổ.
 *
 * Cửa sổ chỉ mang **một** bảng (bảng của bên nó đứng, xem `CameraClusterBand.place`), nhưng hàm này cắt được cả hai
 * để `:app` không cần biết bên nào — 2.78 thêm mép PHẢI đúng như mép TRÁI của 2.77, không có nhánh `if` theo bên.
 *
 * @return `null` khi không có gì để cắt (chưa đo đường cong nào, hoặc cửa sổ suy biến).
 */
internal fun glassMask(p: CameraClusterBand.Placement?, radius: Float): Path? {
    if (p == null || p.w < 1 || p.h < 1) return null
    val left = if (p.leftEdge.size >= 2) halfPlane(p, p.leftEdge, atLeft = true) else null
    val right = if (p.rightEdge.size >= 2) halfPlane(p, p.rightEdge, atLeft = false) else null
    if (left == null && right == null) return null
    val rounded = Path().apply {
        addRoundRect(0f, 0f, p.w.toFloat(), p.h.toFloat(), radius, radius, Path.Direction.CW)
    }
    left?.let { rounded.op(it, Path.Op.INTERSECT) }
    right?.let { rounded.op(it, Path.Op.INTERSECT) }
    return rounded
}

/**
 * Nửa mặt phẳng "còn mực" của MỘT mép: đa giác nối cạnh TRONG của cửa sổ (`w` nếu mép này ở bên trái, `0` nếu ở bên
 * phải) với đường cong lấy mẫu ở **đúng** các hàng mà `:core` định nghĩa, kéo thêm một hàng ra ngoài hai đầu dải để
 * đường cắt phủ trọn chiều cao cửa sổ kể cả khi cửa sổ thấp hơn dải (căn giữa ⇒ đỉnh cửa sổ nằm dưới `band.y0`).
 */
private fun halfPlane(p: CameraClusterBand.Placement, edge: List<Int>, atLeft: Boolean): Path {
    val band = p.band
    val n = edge.size
    val inner = if (atLeft) p.w.toFloat() else 0f
    val yTop = (band.y0 - p.y).toFloat() - 1f
    val yBot = (band.y1 - p.y).toFloat() + 1f
    val glass = Path()
    glass.moveTo(inner, yTop)
    glass.lineTo((edge.first() - p.x).toFloat(), yTop)
    for (i in 0 until n) {
        val y = band.y0 + band.h * i / (n - 1)
        glass.lineTo((edge[i] - p.x).toFloat(), (y - p.y).toFloat())
    }
    glass.lineTo((edge.last() - p.x).toFloat(), yBot)
    glass.lineTo(inner, yBot)
    glass.close()
    return glass
}

/**
 * Nhãn ngắn *Camera trái/phải* ở góc, hoặc `null` khi chỗ gọi không nói bên nào. (Tách khỏi `CameraOverlayView` ở
 * 2.77 để tệp ấy ở dưới trần 500 dòng của CLAUDE.md §4.1 — không đổi một hành vi nào.)
 *
 * Chữ đi qua tài nguyên (`R.string.kachi_camera_left/right`) như mọi chữ của tầng `launcher/` —
 * `LauncherI18nContractTest` quét chính điều đó.
 */
internal fun labelFor(ctx: Context, side: Side?): android.widget.TextView? {
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
