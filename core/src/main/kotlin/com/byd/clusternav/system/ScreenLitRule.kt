package com.byd.clusternav.system

/**
 * 2.98 · R6-C — "màn xe đang SÁNG?" gộp từ hai nguồn: `PowerManager.isInteractive` và trạng thái display 0 (`Display.getState()`).
 * [ĐO log xe 08/10] màn chính Kachi RESUMED suốt ~9,6 h đêm ⇒ [SUY] "tắt màn" của BYD có thể không lật `isInteractive` (soát Pass 4)
 * ⇒ thêm nguồn thứ hai. Một nguồn nói TẮT là tắt; không hỏi được (`null`) ⇒ coi như SÁNG (fail-open = hành vi cũ, đọc như trước).
 */
object ScreenLitRule {
    fun lit(interactive: Boolean?, displayOn: Boolean?): Boolean = interactive != false && displayOn != false
}
