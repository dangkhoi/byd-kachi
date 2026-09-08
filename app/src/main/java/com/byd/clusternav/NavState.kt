package com.byd.clusternav

import android.graphics.Bitmap

/** Trạng thái dẫn đường hiện tại — bất biến, đẩy cho UI vẽ. */
data class NavState(
    val active: Boolean = false,
    val arrow: Bitmap? = null,   // mũi tên maneuver (bitmap lấy thẳng từ notification Google Maps)
    val arrowRes: Int = 0,       // hoặc drawable mũi tên của app (dùng cho demo / fallback); 0 = không dùng
    val distance: String = "",   // khoảng cách tới ngã rẽ, vd "250 m" / "1.2 km"
    val road: String = "",       // đường/hướng kế tiếp
    val maneuverText: String = "", // dòng lệnh rẽ THÔ từ notification ("Rẽ phải vào Nguyễn Huệ") — chỉ để phân loại NEW_ICON
    val maneuverIcon: Int = -1,    // mã AMAP NEW_ICON đọc từ TÊN small-icon (cách DashCast); -1 = không có
    val maneuver: com.byd.clusternav.navigation.Maneuver? = null,  // maneuver TRUNG LẬP (nguồn sự thật cho encoder HUD); maneuverIcon là bản mã-hoá AMAP cho làn cụm
    val eta: String = "",        // ETA + quãng còn lại, vd "10:32 · 5.2 km · 8 phút"
    val updatedAt: Long = 0L
)
