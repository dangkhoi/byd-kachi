package com.byd.clusternav.launcher.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.widget.FrameLayout
import com.byd.clusternav.launcher.KachiSpace
import com.byd.clusternav.launcher.camera.CameraSignalPolicy.Side

/**
 * ═══ MẶT NẠ CỬA SỔ CAMERA — cắt theo ĐƯỜNG CONG của kính cụm (2.77) ══════════════════════════════════════════════
 *
 * Owner 27/09 chiều, nhìn camera chiếu trên cụm: *"này nhìn OK, nhưng shape nó không theo cạnh trái cong của cụm"*.
 * Số đo đường cong + lý do nó chỉ đo được từ ảnh chụp: KDoc [CameraClusterBand] và
 * `docs/diagnostics/offcar-2026-09-27/camera-cluster-band.md` §11.
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
internal class CameraGlassFrame(ctx: Context, private val mask: Path?) : FrameLayout(ctx) {

    override fun draw(canvas: Canvas) {
        val m = mask
        if (m == null) {
            super.draw(canvas)
            return
        }
        val save = canvas.save()
        canvas.clipPath(m)
        super.draw(canvas)
        canvas.restoreToCount(save)
    }
}

/**
 * Mặt nạ của cửa sổ [p] ở **toạ độ cục bộ của cửa sổ**: hình chữ nhật bo góc [radius] của 2.73 **giao** với nửa mặt
 * phẳng nằm bên phải đường cong đã đo ([CameraClusterBand.leftEdgeAt]).
 *
 * Giao (`Path.Op.INTERSECT`) thay vì tự vẽ một đường viền mới: bo góc vẫn là **đúng** bán kính của hồ sơ, hai góc
 * bên trong không đổi một px, và nếu bảng cong rỗng thì chỗ gọi đã trả `null` từ trước ⇒ không có bản sao thứ hai
 * của hình dạng cửa sổ.
 *
 * Đa giác cong lấy mẫu ở **đúng** các hàng của bảng và kéo dài thêm một mẫu ra ngoài hai đầu dải, để đường cắt phủ
 * trọn chiều cao cửa sổ kể cả khi cửa sổ thấp hơn dải (căn giữa ⇒ đỉnh cửa sổ nằm dưới `band.y0`).
 *
 * @return `null` khi không có gì để cắt (chưa đo đường cong, hoặc cửa sổ suy biến).
 */
internal fun glassMask(p: CameraClusterBand.Placement?, radius: Float): Path? {
    if (p == null || p.leftEdge.size < 2 || p.w < 1 || p.h < 1) return null
    val w = p.w.toFloat()
    val h = p.h.toFloat()
    val band = p.band
    val n = p.leftEdge.size
    val glass = Path()
    val yTop = (band.y0 - p.y).toFloat() - 1f
    glass.moveTo(w, yTop)
    glass.lineTo((p.leftEdge.first() - p.x).toFloat(), yTop)
    for (i in 0 until n) {
        val y = band.y0 + band.h * i / (n - 1)
        glass.lineTo((p.leftEdge[i] - p.x).toFloat(), (y - p.y).toFloat())
    }
    val yBot = (band.y1 - p.y).toFloat() + 1f
    glass.lineTo((p.leftEdge.last() - p.x).toFloat(), yBot)
    glass.lineTo(w, yBot)
    glass.close()
    val rounded = Path().apply { addRoundRect(0f, 0f, w, h, radius, radius, Path.Direction.CW) }
    rounded.op(glass, Path.Op.INTERSECT)
    return rounded
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
