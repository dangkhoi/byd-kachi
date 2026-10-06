package com.byd.clusternav.launcher.camera

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager

/**
 * Tham số cửa sổ của overlay camera — tách khỏi `CameraOverlayView` ngày 2026-09-28.
 *
 * Tách vì tệp kia đã **đúng 500 dòng**, tức kịch trần cứng của CLAUDE.md §4.1, nên không thêm được một dòng nào
 * cho tính năng vạch chuẩn. Chọn đúng hàm này để dời vì nó là chỗ **ít dính nhất**: một chỗ gọi duy nhất, và nó
 * chỉ cần bốn số của vùng cho phép chứ không cần lớp `Live`/`Geo` riêng tư nào.
 */

/**
 * 2.92 · CAMERA-FULL-VIEW — thứ LỚP VIDEO lấy mẫu khi khác cửa sổ: vùng NỘI DUNG ([crop], `null` = nguyên khung) + tỉ lệ
 * vừa khung của ma trận TV ([scale], `null` = không thêm phép nào) + [letterbox] (đặt cửa sổ VỪA trên cụm, không
 * phóng-cắt). Toán ở `:core` [CameraViewPlan]; vắng ở `CameraOverlayView.show` ⇒ hành vi 2.91 (lớp video lấy đúng vùng
 * cắt của cửa sổ). Đặt ở tệp này (không trong `CameraOverlayView`) vì tệp kia sát trần 500 dòng (CLAUDE.md §4.1).
 */
class CameraVideoContent(val crop: FloatArray?, val scale: FloatArray?, val letterbox: Boolean)

/**
 * Cửa sổ cỡ [f] ở góc TRÊN [corner], **căn giữa** vùng cho phép (`areaW`×`areaH` tại `x0`,`y0`).
 *
 * Cửa sổ của [WindowManager] không có lề (`margin`) — [WindowManager.LayoutParams.x]/`y` là **độ lệch kể từ góc
 * mà `gravity` chọn**, nên `y = lề trên + (vùng − khung)/2` chính là "nằm giữa vùng đã dành" mà R2 đòi; và vì `x`
 * cũng tính từ góc `gravity` nên công thức dùng chung cho cả `START` lẫn `END`.
 *
 * Góc lạ (không phải `"TL"`/`"TR"`) ⇒ coi như trên-phải; lượt đọc pref đã chặn ở `Prefs.cameraPos`, đây chỉ là
 * lưới an toàn cho chỗ gọi thứ hai sau này.
 */
internal fun overlayLayoutParams(
    areaW: Int,
    areaH: Int,
    x0: Int,
    y0: Int,
    f: CameraOverlayFrame.Frame,
    corner: String,
): WindowManager.LayoutParams {
    val atLeft = corner == CameraSignalPolicy.CORNER_TOP_LEFT
    return WindowManager.LayoutParams(
        f.w, f.h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or (if (atLeft) Gravity.START else Gravity.END)
        x = x0 + ((areaW - f.w) / 2).coerceAtLeast(0)
        y = y0 + ((areaH - f.h) / 2).coerceAtLeast(0)
    }
}
