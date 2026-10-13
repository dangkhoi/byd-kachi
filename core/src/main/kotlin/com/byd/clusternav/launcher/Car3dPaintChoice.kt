package com.byd.clusternav.launcher

import java.util.concurrent.CopyOnWriteArraySet

/**
 * 3.01 R25 — màu sơn xe 3D đang chọn (tên dòng `car_body_color.csv`, `null` = mặc định AVM). Hình chiếu LÚC VẼ của
 * `HomeUiState.colorChoice.carPaint` (khoá `color_choice` theo hồ sơ) — chỉ [ThemeHost.sync] ghi, chỉ widget 3D đọc để tô
 * (đổi MỘT tham số vật liệu, không dựng lại mô hình). Không phải trạng thái thứ hai: không ai đọc nó để quyết định logic,
 * cùng lẽ [KachiTheme.palette]. Đổi ⇒ báo các widget đang gắn ([listen]) — [ĐO máy ảo 13/10] chờ nhịp đổ số thì số giả đứng
 * yên không bao giờ tới, màu chọn trong Cài đặt không lên xe.
 */
object Car3dPaintChoice {
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    @Volatile var current: String? = null
        set(v) { if (field != v) { field = v; listeners.forEach { it() } } }

    /** 3.01 R28 — "Hiện thông số lốp" dưới từng bánh ([ColorChoice.carTyres], theo hồ sơ; mặc định BẬT). Cùng đường báo [listen]. */
    @Volatile var tyres: Boolean = true
        set(v) { if (field != v) { field = v; listeners.forEach { it() } } }

    fun listen(l: () -> Unit) { listeners += l }
    fun unlisten(l: () -> Unit) { listeners -= l }
}
