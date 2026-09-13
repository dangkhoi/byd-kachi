package com.byd.clusternav.launcher

/** Nguồn dữ liệu widget: LOCAL (Android thuần: đồng hồ/nhạc) · CAR (BydHal → off-car "—") · BOARD (bảng gộp nhiều mini). */
enum class WidgetKind { LOCAL, CAR, BOARD }

data class WidgetDef(
    val id: String,
    override val label: String,
    val icon: String,
    val kind: WidgetKind,
    /** Nhãn tiếng Anh (U5 · T2) — tham số mặc định, xem KDoc [Strings]. */
    override val labelEn: String? = null,
) : Localized

/**
 * Widget dựng tay. 8 cái đầu là bộ đã duyệt ở prototype (docs/prototypes/kachi-workspace.html); cái thứ 9
 * (trình chiếu ảnh) thêm ở U4 theo yêu cầu của owner.
 */
object WidgetRegistry {
    val ALL: List<WidgetDef> = listOf(
        WidgetDef("w_energy", "Năng lượng",          "ic-bolt",  WidgetKind.CAR, "Energy"),
        // U7: thẻ này vẽ CẢ BỐN bánh (TyreBoardView) nên nó là một ô TỔNG HỢP — mang đúng hình mà nhóm Lốp
        // mang (khung xe + bốn bánh tô), không phải hình MỘT bánh. [ĐO] ảnh máy ảo 2026-09-13: để `ic-tire`
        // thì ô "Áp suất lốp" giữa màn nói "một bánh" trong khi nội dung nó bày ra là bốn bánh.
        // `ic-tire` vẫn sống: nó là hình lùi-về của lĩnh vực Lốp (`WidgetCatalog.iconFor`).
        WidgetDef("w_tire",   "Áp suất lốp",         "ic-group-tyres", WidgetKind.CAR, "Tyre pressure"),
        WidgetDef("w_pm25",   "Không khí",           "ic-leaf",  WidgetKind.CAR, "Air quality"),
        WidgetDef("w_clock",  "Đồng hồ + thời tiết", "ic-sun",   WidgetKind.LOCAL, "Clock + weather"),
        WidgetDef("w_media",  "Đang phát",           "ic-music", WidgetKind.LOCAL, "Now playing"),
        WidgetDef("w_car",    "Trạng thái xe",       "ic-car",   WidgetKind.CAR, "Car status"),
        WidgetDef("w_speed",  "Tốc độ",              "ic-speed", WidgetKind.CAR, "Speed"),
        WidgetDef("w_board",  "Bảng tổng hợp",       "ic-grid",  WidgetKind.BOARD, "Overview board"),
        // U4 phần (b): widget TRÌNH CHIẾU ảnh — owner nêu cả hình nền LẪN widget riêng. Đọc cùng thư mục ảnh với
        // hình nền, nhưng chạy ĐỘC LẬP: người dùng có thể muốn một khung ảnh trong ô mà KHÔNG đổi nền màn hình.
        // Kind LOCAL vì nguồn là tệp trên máy, không phải dữ liệu xe (nên off-car vẫn chạy đầy đủ).
        WidgetDef("w_photos", "Trình chiếu ảnh",     "ic-photo", WidgetKind.LOCAL, "Photo slideshow"),
    )

    fun byId(id: String): WidgetDef? = ALL.firstOrNull { it.id == id }
}
