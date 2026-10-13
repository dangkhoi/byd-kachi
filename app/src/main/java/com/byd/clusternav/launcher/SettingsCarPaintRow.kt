package com.byd.clusternav.launcher

import android.content.Context
import android.view.View
import com.byd.clusternav.R

/**
 * ═══ 3.01 R25 — hàng "Màu xe 3D" (Cài đặt › Giao diện › khối HÌNH XE) ═══════════════════════════════════════════════
 *
 * Owner 13/10: *"có mấy màu xe vậy?"* — 8 màu của chính app AVM (`car_body_color.csv`, đọc LÚC CHẠY từ `com.byd.avc`; bản
 * vehicleTest đọc bản đóng gói — repo không chứa byte BYD). Ô màu tròn, KHÔNG chữ (tên màu chỉ cho trợ năng); lưu trong
 * `color_choice` ([ColorChoice.carPaint]) ⇒ theo hồ sơ + xuất/nhập. Áp tại chỗ lên vật liệu sơn của xe 3D (ảnh xe 2D của OEM
 * không đổi). Xe không có mô hình 3D ⇒ không có hàng (không nút chết).
 */
internal object SettingsCarPaintRow {

    /** `null` = đời xe này không có màu sơn AVM. [current] = tên đang chọn (`null` = mặc định AVM). */
    fun paintRow(context: Context, rows: SettingsRows, current: String?, onPick: (String) -> Unit): View? {
        val presets = Car3dModelSource.paints(context)
        if (presets.isEmpty()) return null
        val chosen = Car3dPaint.pick(presets, current)?.name ?: return null
        val swatches = presets.map { Swatch(it.name, it.argb(), title(context, it.name)) }
        return rows.swatchRow(context.getString(R.string.kcar3d_paint_row), swatches, chosen, withCaption = false, onPick = onPick)
    }

    private fun title(context: Context, name: String): String =
        NAMES[name]?.let(context::getString) ?: context.getString(R.string.kcar3d_paint_other, name)

    /**
     * Tên dòng → nhãn trợ năng: 8 dòng của Seal + tên phổ biến ở đời khác [ĐO 37 tệp `car_body_color.csv`; tên tiếp thị theo
     * hằng `CarBodyColorController` của AVM — vd `grey` = TIME_GREY, `blue` = INK_STONE_BLUE]. Dòng lạ ⇒ "Màu <tên>".
     */
    private val NAMES = mapOf(
        "white" to R.string.kcar3d_paint_white, "black" to R.string.kcar3d_paint_black, "grey" to R.string.kcar3d_paint_grey,
        "darkblue" to R.string.kcar3d_paint_darkblue, "purple" to R.string.kcar3d_paint_purple, "red" to R.string.kcar3d_paint_red,
        "smartblue" to R.string.kcar3d_paint_smartblue, "green" to R.string.kcar3d_paint_green,
        "blue" to R.string.kcar3d_paint_blue, "orange" to R.string.kcar3d_paint_orange, "pink" to R.string.kcar3d_paint_pink,
        "silvery" to R.string.kcar3d_paint_silvery, "mountain_grey" to R.string.kcar3d_paint_mountain_grey,
        "tianshan_white" to R.string.kcar3d_paint_tianshan_white,
        Car3dCatalog.SEAL_STEEL to R.string.kcar3d_paint_steel,
    )

    /**
     * 3.01 R25 — hàng "Nền xe 3D": chọn một ảnh trong thư mục hình nền SẴN CÓ ([WallpaperStore]) hoặc không ảnh; nút căn bật chế
     * độ căn trên chính widget ([Car3dBackdropChoice.aligning] — kéo/chụm, bấm Xong lưu theo hồ sơ). Xe không có mô hình 3D ⇒ không hàng.
     */
    fun backdrop(context: Context, rows: SettingsRows, current: Car3dBackdrop?, onPick: (Car3dBackdrop?) -> Unit): List<View> {
        if (Car3dModelSource.paints(context).isEmpty()) return emptyList()
        val files = WallpaperStore.images(context).map { java.io.File(it).name }.filter { Car3dBackdrop.validName(it) }
        if (files.isEmpty()) return listOf(rows.note(context.getString(R.string.kcar3d_bg_empty, WallpaperStore.folderHint(context))))
        val options = listOf(NONE to context.getString(R.string.kcar3d_bg_none)) + files.map { it to it.substringBeforeLast('.') }
        val chosen = current?.file?.takeIf { it in files } ?: NONE
        return listOf(
            rows.chipRow(context.getString(R.string.kcar3d_bg_row), options, chosen, wrap = true) { f ->
                onPick(if (f == NONE) null else Car3dBackdrop(f))
            },
            rows.button(context.getString(R.string.kcar3d_bg_align)) { if (Car3dBackdropChoice.current != null) Car3dBackdropChoice.aligning = true },
            rows.note(context.getString(R.string.kcar3d_bg_align_note)),
        )
    }

    /** 3.01 R28 — "Hiện thông số lốp" (áp suất + nhiệt dưới từng bánh của xe 3D), theo hồ sơ; xe không có mô hình 3D ⇒ không hàng. */
    fun tyres(context: Context, rows: SettingsRows, on: Boolean, onChange: (Boolean) -> Unit): View? =
        if (Car3dModelSource.paints(context).isEmpty()) null
        else rows.checkRow(on, context.getString(R.string.kcar3d_tyres_row), context.getString(R.string.kcar3d_tyres_sub), onChange)

    private const val NONE = ""
}
