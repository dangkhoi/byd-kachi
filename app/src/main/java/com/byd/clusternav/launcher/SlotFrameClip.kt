package com.byd.clusternav.launcher

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider

/**
 * ═══ A5(b) · SLOT-CORNER-ROUND (2.89) — khung ô cắt nội dung theo góc bo bằng ĐƯỜNG VIỀN RIÊNG, không mượn nền ═══════════
 *
 * [ĐO ảnh xe 05/10] (`docs/diagnostics/oncar-2026-10-05-slot-cluster.md` §5): app trong ô KHÔNG bị cắt bo — góc trên trái vuông.
 *
 * ## Gốc lỗi — [ĐO nguồn android-10.0.0_r47]
 *  - `View.clipToOutline` dùng `ViewOutlineProvider.BACKGROUND` (`ViewOutlineProvider.java:33-44`) = `background.getOutline`.
 *  - Có ảnh nền, nền khung là `LayerDrawable(WallWindowDrawable, bề mặt)` (`KachiGlass.paint`). `LayerDrawable.getOutline`
 *    (`LayerDrawable.java:1277-1289`) lấy lớp ĐẦU có đường viền khác rỗng; `WallWindowDrawable` không ghi đè ⇒ `Drawable.getOutline`
 *    (`Drawable.java:1175-1178`) = HÌNH CHỮ NHẬT ⇒ khung chỉ cắt theo mép, không bo. Không ảnh nền ⇒ `GradientDrawable` (bo) ⇒ cắt bo
 *    đúng — bởi vậy lỗi chỉ lộ khi bật hình nền.
 *  - Mặt vẽ app (`SurfaceView`, nằm DƯỚI cửa sổ) chỉ hiện qua "lỗ" nó tự đục: `SurfaceView.draw/dispatchDraw` →
 *    `clearSurfaceViewPort` = `canvas.drawColor(0, CLEAR)` (`SurfaceView.java:398-428`) — một lệnh vẽ trong display list của cây
 *    view, nên chịu phép cắt của cha: `RenderNodeDrawable.cpp:324-327` → `clipOutline` (`:78-97`) = `clipRRect(…, kIntersect,
 *    true)` (khử răng cưa) khi đường viền là hình bo (`Outline.h:91-94`). Ngoài cung bo không bị đục ⇒ điểm ảnh cửa sổ ở đó vẫn
 *    là nền phía sau khung (`WallView`: ảnh nền sắc / nền mặc định, đục) ⇒ góc tròn, đúng "vùng hình nền sau góc".
 *
 * ## Vì sao KHÔNG vẽ mặt nạ góc (brief 2.89 gợi ý) / KHÔNG `TextureView`
 * Đường viền riêng chữa đúng gốc: 0 view mới, 0 lượt vẽ thêm, 0 cấp phát mỗi khung (đường viền chỉ dựng lại khi khung đổi cỡ),
 * góc tự đúng ở MỌI chế độ (ảnh nền / nền mặc định / thanh trong suốt 0–100 % / đổi chủ đề) vì thứ hiện ra ở góc CHÍNH LÀ nền
 * phía sau, không phải bản sao của nó. Mặt nạ là con của khung thì cũng bị chính phép cắt này cắt mất ở ca không ảnh nền. `TextureView`
 * = một lượt chép GPU mỗi khung (KDoc `VdAppHost`).
 *
 * Phạm vi: cả khung ô widget / widget app khác được cắt bo như ý định gốc của `makeSlot` ("overflow:hidden" của prototype) — hôm
 * nay chúng cũng chỉ cắt theo mép khi bật ảnh nền. `SlotAppHost` (ActivityView, ROM ký nền tảng) có bán kính riêng — không đổi.
 */
internal object SlotFrameClip {

    /** Cắt [frame] (và mọi con, kể cả lỗ của `SurfaceView`) theo hình bo bán kính [radiusPx] — thay cho `clipToOutline` trần. */
    fun apply(frame: View, radiusPx: Float) {
        frame.outlineProvider = Round(radiusPx)
        frame.clipToOutline = true
    }

    /** `View` gọi lại ở mỗi lần đổi cỡ (`rebuildOutline`) — không cấp phát gì ngoài lời gọi đặt hình. */
    private class Round(private val r: Float) : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val rad = SlotFrameShape.radius(view.width, view.height, r) ?: return outline.setEmpty()
            outline.setRoundRect(0, 0, view.width, view.height, rad)
        }
    }
}
