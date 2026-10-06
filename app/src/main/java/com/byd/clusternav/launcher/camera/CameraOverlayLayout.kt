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
 * 2.93 · CAMERA-PER-CAM-CONFIG — vị trí kéo-thả ([place], `null` = góc mặc định) + cỡ [sizePct] RIÊNG của camera đang hiện.
 * Dựng qua [of]: chưa kéo + 100 % ⇒ `null` ⇒ `CameraOverlayView` đi đường đặt chỗ 2.73–2.92 NGUYÊN VĂN (CLAUDE.md §6).
 */
class CameraOverlayPlace private constructor(val place: CameraCamConfig.Place?, val sizePct: Int) {
    companion object {
        fun of(place: CameraCamConfig.Place?, sizePct: Int): CameraOverlayPlace? =
            if (CameraPlacement.custom(place, sizePct)) CameraOverlayPlace(place, sizePct) else null
    }
}

/** Kết quả đường đặt chỗ RIÊNG — khung, tham số cửa sổ tuyệt đối, bán kính (cụm; `null` = bán kính khung launcher), log. */
internal class CustomOverlayGeo(
    val f: CameraOverlayFrame.Frame,
    val lp: WindowManager.LayoutParams,
    val radiusPx: Int?,
    val note: String,
)

/**
 * Cửa sổ của camera đã KÉO hoặc đã ĐỔI CỠ — toán ở `:core` [CameraPlacement] (có test bằng số); đây chỉ dịch ra
 * [WindowManager.LayoutParams] toạ độ TUYỆT ĐỐI (`TOP|START`), cùng loại cửa sổ + cờ của 2.73 (không nhận chạm).
 *
 * Trên cụm: vùng = DẢI GIỮA theo hồ sơ xe; *Theo cụm* ở đường này vẽ như chữ nhật (không mặt nạ cong — KDoc lớp
 * [CameraPlacement]); bán kính bo theo hồ sơ như đường dải ([CameraClusterBand.radius]).
 */
internal fun customOverlayGeo(
    displayW: Int,
    displayH: Int,
    onCluster: Boolean,
    band: ClusterBandSpec,
    shape: String,
    corner: String,
    p: CameraOverlayPlace,
    stream: CameraPlacement.Stream,
): CustomOverlayGeo {
    val win = if (onCluster) {
        val b = CameraClusterBand.band(displayW, displayH, band)
        CameraPlacement.cluster(b, shape == CameraSignalPolicy.SHAPE_ROUND, corner, p.place, p.sizePct, stream)
    } else {
        CameraPlacement.main(displayW, displayH, corner, p.place, p.sizePct, stream)
    }
    val lp = WindowManager.LayoutParams(
        win.w, win.h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = win.x
        y = win.y
    }
    val radius = if (onCluster) CameraClusterBand.radius(band, displayH, win.w, win.h) else null
    // Nhật ký ASCII (tầng `launcher/` không mang chữ Việt cứng — `LauncherI18nContractTest`): đủ để buổi xe đọc lại chỗ đặt.
    val note = "percam place=${p.place?.encode() ?: corner} size=${p.sizePct}% at=${win.x},${win.y} display=${displayW}x$displayH"
    return CustomOverlayGeo(CameraOverlayFrame.Frame(win.w, win.h, win.streamKnown), lp, radius, note)
}

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
