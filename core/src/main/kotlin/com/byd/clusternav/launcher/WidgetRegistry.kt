package com.byd.clusternav.launcher

/** Nguồn dữ liệu widget: LOCAL (Android thuần: đồng hồ/nhạc) · CAR (BydHal → off-car "—") · BOARD (bảng gộp nhiều mini). */
enum class WidgetKind { LOCAL, CAR, BOARD }

data class WidgetDef(val id: String, val label: String, val icon: String, val kind: WidgetKind)

/**
 * Widget dựng tay. 8 cái đầu là bộ đã duyệt ở prototype (docs/prototypes/kachi-workspace.html); cái thứ 9
 * (trình chiếu ảnh) thêm ở U4 theo yêu cầu của owner.
 */
object WidgetRegistry {
    val ALL: List<WidgetDef> = listOf(
        WidgetDef("w_energy", "Năng lượng",          "ic-bolt",  WidgetKind.CAR),
        WidgetDef("w_tire",   "Áp suất lốp",         "ic-tire",  WidgetKind.CAR),
        WidgetDef("w_pm25",   "Không khí",           "ic-leaf",  WidgetKind.CAR),
        WidgetDef("w_clock",  "Đồng hồ + thời tiết", "ic-sun",   WidgetKind.LOCAL),
        WidgetDef("w_media",  "Đang phát",           "ic-music", WidgetKind.LOCAL),
        WidgetDef("w_car",    "Trạng thái xe",       "ic-lock",  WidgetKind.CAR),
        WidgetDef("w_speed",  "Tốc độ",              "ic-speed", WidgetKind.CAR),
        WidgetDef("w_board",  "Bảng tổng hợp",       "ic-grid",  WidgetKind.BOARD),
        // U4 phần (b): widget TRÌNH CHIẾU ảnh — owner nêu cả hình nền LẪN widget riêng. Đọc cùng thư mục ảnh với
        // hình nền, nhưng chạy ĐỘC LẬP: người dùng có thể muốn một khung ảnh trong ô mà KHÔNG đổi nền màn hình.
        // Kind LOCAL vì nguồn là tệp trên máy, không phải dữ liệu xe (nên off-car vẫn chạy đầy đủ).
        WidgetDef("w_photos", "Trình chiếu ảnh",     "ic-sun",   WidgetKind.LOCAL),
    )

    fun byId(id: String): WidgetDef? = ALL.firstOrNull { it.id == id }
}
